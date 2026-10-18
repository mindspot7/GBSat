// Ymir Android bridge: runs ymir-core on a dedicated emulation thread, presents frames to an ANativeWindow,
// and streams SCSP audio through AAudio. All emulator state is only touched from the emulation thread;
// the UI talks to it through a command queue (see Post/RunSync).
#include <jni.h>

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <android/native_window_jni.h>

#include <ymir/ymir.hpp>

#include "gl_renderer.hpp"
#include "vk_context.hpp"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <deque>
#include <filesystem>
#include <fstream>
#include <functional>
#include <future>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#define LOG_TAG "YmirAndroid"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace savestate_io {
std::string Save(ymir::Saturn &saturn, const std::filesystem::path &path);
std::string Load(ymir::Saturn &saturn, const std::filesystem::path &path);
} // namespace savestate_io

namespace {

using namespace std::chrono;
namespace pr = ymir::peripheral;

constexpr int32_t kSampleRate = 44100;
constexpr uint32_t kRingFrames = 16384; // power of two
constexpr uint32_t kTargetBufferedFrames = 2048;

// ---------------------------------------------------------------------------------------------------------------------
// Audio ring buffer (single producer: emu thread, single consumer: AAudio callback)

struct AudioRing {
    std::vector<int16_t> data = std::vector<int16_t>(kRingFrames * 2);
    std::atomic<uint32_t> head{0}; // write index (frames)
    std::atomic<uint32_t> tail{0}; // read index (frames)

    uint32_t Fill() const {
        return head.load(std::memory_order_acquire) - tail.load(std::memory_order_acquire);
    }
    void Push(int16_t l, int16_t r, bool dropIfFull) {
        const uint32_t h = head.load(std::memory_order_relaxed);
        if (h - tail.load(std::memory_order_acquire) >= kRingFrames) {
            if (dropIfFull) {
                return;
            }
            return; // never block the emulator on audio
        }
        data[(h & (kRingFrames - 1)) * 2] = l;
        data[(h & (kRingFrames - 1)) * 2 + 1] = r;
        head.store(h + 1, std::memory_order_release);
    }
    void Pop(int16_t *out, int32_t frames) {
        uint32_t t = tail.load(std::memory_order_relaxed);
        const uint32_t avail = head.load(std::memory_order_acquire) - t;
        const uint32_t n = std::min<uint32_t>(avail, frames);
        for (uint32_t i = 0; i < n; ++i, ++t) {
            out[i * 2] = data[(t & (kRingFrames - 1)) * 2];
            out[i * 2 + 1] = data[(t & (kRingFrames - 1)) * 2 + 1];
        }
        if (n < (uint32_t)frames) {
            std::memset(out + n * 2, 0, (frames - n) * 2 * sizeof(int16_t));
        }
        tail.store(t, std::memory_order_release);
    }
    void Clear() {
        tail.store(head.load(std::memory_order_acquire), std::memory_order_release);
    }
};

// ---------------------------------------------------------------------------------------------------------------------
// Global emulator context

struct Context {
    std::unique_ptr<ymir::Saturn> saturn;

    // Threading
    std::thread emuThread;
    std::atomic<bool> running{false};
    std::atomic<bool> paused{true};
    std::atomic<bool> fastForward{false};
    std::mutex cmdMutex;
    std::condition_variable cmdCv;
    std::deque<std::function<void()>> commands;

    // Input
    std::atomic<uint16_t> pressed{0};  // pr::Button bits, 1 = pressed
    std::atomic<uint8_t> stickX{0x80}, stickY{0x80}, trigL{0}, trigR{0};
    std::atomic<bool> analogPad{false};

    // Video
    std::mutex winMutex;
    ANativeWindow *window = nullptr;
    int32_t curW = 0, curH = 0;
    std::atomic<uint32_t> videoW{320}, videoH{224};
    std::atomic<uint64_t> frameCounter{0};

    // Audio
    AudioRing ring;
    AAudioStream *stream = nullptr;
    std::mutex audioMutex;
    std::atomic<bool> audioOk{false};

    // Display
    std::atomic<bool> glMode{false};
    std::atomic<int> rendererMode{0}; // 0 = CPU window, 1 = GLES, 2 = Vulkan, 3 = ANGLE, 4 = Vulkan GPU VDP
    std::string gpuError; // last Vulkan VDP renderer error (guarded by winMutex)
    bool vkFailed = false;
    bool vkLinear = false;
    std::string vkError;

    // Cheats (emulation thread only): constant memory writes applied after every frame
    struct Cheat { uint32_t addr; uint32_t value; int size; };
    std::vector<Cheat> cheats;

    // Misc
    std::string smpcPath;
    std::string lastError;
    std::atomic<float> fps{0.0f};
};

Context *g = nullptr;

// ---------------------------------------------------------------------------------------------------------------------
// Command queue helpers

void Post(std::function<void()> fn) {
    {
        std::lock_guard lk(g->cmdMutex);
        g->commands.push_back(std::move(fn));
    }
    g->cmdCv.notify_all();
}

template <typename F>
auto RunSync(F &&fn) -> decltype(fn()) {
    using R = decltype(fn());
    auto task = std::make_shared<std::packaged_task<R()>>(std::forward<F>(fn));
    auto fut = task->get_future();
    Post([task] { (*task)(); });
    return fut.get();
}

void DrainCommands() {
    for (;;) {
        std::function<void()> fn;
        {
            std::lock_guard lk(g->cmdMutex);
            if (g->commands.empty()) {
                return;
            }
            fn = std::move(g->commands.front());
            g->commands.pop_front();
        }
        fn();
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Video

void OnFrameComplete(uint32_t *fb, uint32_t width, uint32_t height) {
    g->videoW.store(width, std::memory_order_relaxed);
    g->videoH.store(height, std::memory_order_relaxed);
    g->frameCounter.fetch_add(1, std::memory_order_relaxed);

    if (g->glMode.load(std::memory_order_relaxed)) {
        glr::Submit(fb, width, height);
        return;
    }

    std::lock_guard lk(g->winMutex);
    if (!g->window) {
        return;
    }
    const int presentMode = g->rendererMode.load(std::memory_order_relaxed);
    if ((presentMode == 2 || presentMode == 4) && !g->vkFailed) {
        if (!vkctx::IsReady() && !vkctx::Init(g->window, g->vkError)) {
            g->vkFailed = true; // fall back to CPU presentation
        }
        if (vkctx::IsReady()) {
            vkctx::PresentRGBA(fb, width, height, g->vkLinear);
            return;
        }
    }
    if ((int32_t)width != g->curW || (int32_t)height != g->curH) {
        ANativeWindow_setBuffersGeometry(g->window, width, height, WINDOW_FORMAT_RGBA_8888);
        g->curW = width;
        g->curH = height;
    }
    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(g->window, &buf, nullptr) < 0) {
        return;
    }
    auto *dst = static_cast<uint8_t *>(buf.bits);
    const uint32_t rows = std::min<uint32_t>(height, buf.height);
    const uint32_t cols = std::min<uint32_t>(width, buf.width);
    for (uint32_t y = 0; y < rows; ++y) {
        std::memcpy(dst + (size_t)y * buf.stride * 4, fb + (size_t)y * width, cols * 4);
    }
    ANativeWindow_unlockAndPost(g->window);
}

// ---------------------------------------------------------------------------------------------------------------------
// Audio (AAudio)

aaudio_data_callback_result_t AudioDataCb(AAudioStream *, void *, void *audioData, int32_t numFrames) {
    g->ring.Pop(static_cast<int16_t *>(audioData), numFrames);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

bool OpenAudio();

void AudioErrorCb(AAudioStream *, void *, aaudio_result_t error) {
    if (error == AAUDIO_ERROR_DISCONNECTED) {
        g->audioOk = false;
        std::thread([] { OpenAudio(); }).detach(); // reopen on the new default device
    }
}

void CloseAudio() {
    std::lock_guard lk(g->audioMutex);
    if (g->stream) {
        AAudioStream_requestStop(g->stream);
        AAudioStream_close(g->stream);
        g->stream = nullptr;
    }
    g->audioOk = false;
}

bool OpenAudio() {
    std::lock_guard lk(g->audioMutex);
    if (g->stream) {
        AAudioStream_close(g->stream);
        g->stream = nullptr;
    }
    AAudioStreamBuilder *b = nullptr;
    if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) {
        return false;
    }
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(b, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setSampleRate(b, kSampleRate);
    AAudioStreamBuilder_setChannelCount(b, 2);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_GAME);
    AAudioStreamBuilder_setDataCallback(b, AudioDataCb, nullptr);
    AAudioStreamBuilder_setErrorCallback(b, AudioErrorCb, nullptr);
    AAudioStream *s = nullptr;
    aaudio_result_t r = AAudioStreamBuilder_openStream(b, &s);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK) {
        LOGE("AAudio open failed: %s", AAudio_convertResultToText(r));
        return false;
    }
    // Rate conversion is handled by AAudio if the device native rate differs.
    AAudioStream_setBufferSizeInFrames(s, AAudioStream_getFramesPerBurst(s) * 2);
    if (AAudioStream_requestStart(s) != AAUDIO_OK) {
        AAudioStream_close(s);
        return false;
    }
    g->stream = s;
    g->audioOk = true;
    return true;
}

// ---------------------------------------------------------------------------------------------------------------------
// Peripheral input

void OnPeripheralReport(pr::PeripheralReport &report, void *) {
    const auto pressed = g->pressed.load(std::memory_order_relaxed);
    const auto released = static_cast<pr::Button>(static_cast<uint16_t>(~pressed) & static_cast<uint16_t>(pr::Button::All));
    switch (report.type) {
    case pr::PeripheralType::ControlPad: report.report.controlPad.buttons = released; break;
    case pr::PeripheralType::AnalogPad: {
        auto &r = report.report.analogPad;
        r.buttons = released;
        r.analog = true;
        r.x = g->stickX.load(std::memory_order_relaxed);
        r.y = g->stickY.load(std::memory_order_relaxed);
        r.l = g->trigL.load(std::memory_order_relaxed);
        r.r = g->trigR.load(std::memory_order_relaxed);
        break;
    }
    default: break;
    }
}

void ConnectPad(bool analog) {
    auto &port = g->saturn->SMPC.GetPeripheralPort1();
    port.SetPeripheralReportCallback({nullptr, OnPeripheralReport});
    if (analog) {
        port.ConnectAnalogPad();
    } else {
        port.ConnectControlPad();
    }
    g->saturn->SMPC.GetPeripheralPort2().DisconnectPeripherals();
}

// ---------------------------------------------------------------------------------------------------------------------
// SMPC persistence (clock / language settings): without this the BIOS asks for them on every launch.

void WriteSmpc(const ymir::smpc::PersistentSMPCData &d) {
    if (g->smpcPath.empty()) {
        return;
    }
    std::ofstream out(g->smpcPath, std::ios::binary | std::ios::trunc);
    out.write(reinterpret_cast<const char *>(d.SMEM.data()), 4);
    const uint8_t ste = d.STE ? 1 : 0;
    out.write(reinterpret_cast<const char *>(&ste), 1);
    out.write(reinterpret_cast<const char *>(&d.rtc.offset), sizeof(d.rtc.offset));
    out.write(reinterpret_cast<const char *>(&d.rtc.timestamp), sizeof(d.rtc.timestamp));
}

bool ReadSmpc(ymir::smpc::PersistentSMPCData &d) {
    std::ifstream in(g->smpcPath, std::ios::binary);
    if (!in) {
        return false;
    }
    uint8_t ste = 0;
    in.read(reinterpret_cast<char *>(d.SMEM.data()), 4);
    in.read(reinterpret_cast<char *>(&ste), 1);
    in.read(reinterpret_cast<char *>(&d.rtc.offset), sizeof(d.rtc.offset));
    in.read(reinterpret_cast<char *>(&d.rtc.timestamp), sizeof(d.rtc.timestamp));
    d.STE = ste != 0;
    return static_cast<bool>(in);
}

// ---------------------------------------------------------------------------------------------------------------------
// Emulation thread

void EmuLoop() {
    auto nextFrame = steady_clock::now();
    auto fpsWindowStart = steady_clock::now();
    int fpsFrames = 0;

    while (g->running.load()) {
        DrainCommands();

        if (g->paused.load()) {
            std::unique_lock lk(g->cmdMutex);
            g->cmdCv.wait_for(lk, milliseconds(20), [] { return !g->commands.empty() || !g->paused.load() || !g->running.load(); });
            nextFrame = steady_clock::now();
            fpsWindowStart = nextFrame;
            fpsFrames = 0;
            continue;
        }

        g->saturn->RunFrame();
        for (const auto &c : g->cheats) {
            switch (c.size) {
            case 1: g->saturn->mainBus.Poke<uint8_t>(c.addr, (uint8_t)c.value); break;
            case 4: g->saturn->mainBus.Poke<uint32_t>(c.addr, c.value); break;
            default: g->saturn->mainBus.Poke<uint16_t>(c.addr, (uint16_t)c.value); break;
            }
        }
        ++fpsFrames;

        const auto now = steady_clock::now();
        if (now - fpsWindowStart >= seconds(1)) {
            g->fps = fpsFrames / duration<float>(now - fpsWindowStart).count();
            fpsFrames = 0;
            fpsWindowStart = now;
        }

        if (g->fastForward.load()) {
            g->ring.Clear();
            nextFrame = steady_clock::now();
            continue;
        }

        if (g->audioOk.load()) {
            // Audio-driven pacing: keep ~46 ms buffered.
            while (g->running.load() && !g->paused.load() && g->ring.Fill() > kTargetBufferedFrames) {
                std::this_thread::sleep_for(microseconds(500));
            }
        } else {
            const bool pal = g->saturn->GetVideoStandard() == ymir::core::config::sys::VideoStandard::PAL;
            nextFrame += nanoseconds((int64_t)(1e9 / (pal ? 50.0 : 59.82)));
            const auto t = steady_clock::now();
            if (nextFrame > t) {
                std::this_thread::sleep_until(nextFrame);
            } else if (t - nextFrame > milliseconds(100)) {
                nextFrame = t;
            }
        }
    }
}

std::string JStr(JNIEnv *env, jstring s) {
    if (!s) {
        return {};
    }
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

jstring NewJStr(JNIEnv *env, const std::string &s) {
    return s.empty() ? nullptr : env->NewStringUTF(s.c_str());
}

} // namespace

// =====================================================================================================================
// JNI exports — class app.ymir.android.NativeBridge

#define JNI_FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_app_ymir_android_NativeBridge_##name

JNI_FN(jstring, nativeInit)(JNIEnv *env, jclass, jstring backupRamPath, jstring smpcPath) {
    if (g) {
        return nullptr;
    }
    g = new Context();
    g->saturn = std::make_unique<ymir::Saturn>();

    g->saturn->SCSP.SetSampleCallback({nullptr, [](int16_t l, int16_t r, void *) {
                                           g->ring.Push(l, r, g->fastForward.load());
                                       }});
    g->saturn->VDP.SetSoftwareRenderCallback({nullptr, [](uint32_t *fb, uint32_t w, uint32_t h, void *) {
                                                  OnFrameComplete(fb, w, h);
                                              }});
    g->saturn->VDP.SetVulkanFrameReadyCallback({nullptr, [](const uint32_t *fb, uint32_t w, uint32_t h, void *) {
                                                    OnFrameComplete(const_cast<uint32_t *>(fb), w, h);
                                                }});
    ConnectPad(false);

    g->smpcPath = JStr(env, smpcPath);
    {
        ymir::smpc::PersistentSMPCData data;
        if (ReadSmpc(data)) {
            g->saturn->SMPC.LoadPersistentData(data);
        }
    }
    g->saturn->SMPC.SetPersistDataCallback(
        {nullptr, [](const ymir::smpc::PersistentSMPCData &d, void *) { WriteSmpc(d); }});

    std::string err;
    const std::string bup = JStr(env, backupRamPath);
    if (!bup.empty()) {
        std::error_code ec;
        g->saturn->LoadInternalBackupMemoryImage(bup, false, ec);
        if (ec) {
            err = "Failed to load internal backup memory: " + ec.message();
        }
    }

    OpenAudio();
    g->running = true;
    g->emuThread = std::thread(EmuLoop);
    return NewJStr(env, err);
}

JNI_FN(void, nativeShutdown)(JNIEnv *, jclass) {
    if (!g) {
        return;
    }
    g->running = false;
    g->cmdCv.notify_all();
    if (g->emuThread.joinable()) {
        g->emuThread.join();
    }
    CloseAudio();
    glr::Shutdown();
    {
        std::lock_guard lk(g->winMutex);
        vkctx::Shutdown();
        if (g->window) {
            ANativeWindow_release(g->window);
            g->window = nullptr;
        }
    }
    g->saturn.reset(); // flushes backup memory mapping
    delete g;
    g = nullptr;
}

JNI_FN(void, nativeSetSurface)(JNIEnv *env, jclass, jobject surface) {
    if (!g) {
        return;
    }
    if (g->glMode.load()) {
        glr::SetWindow(surface ? ANativeWindow_fromSurface(env, surface) : nullptr);
        return;
    }
    std::lock_guard lk(g->winMutex);
    if (g->window) {
        ANativeWindow_release(g->window);
        g->window = nullptr;
    }
    g->curW = g->curH = 0;
    vkctx::Shutdown();
    g->vkFailed = false;
    if (surface) {
        g->window = ANativeWindow_fromSurface(env, surface);
    }
}

// Returns an error string, or null on success. `path` is the IPL ROM (BIOS) file.
JNI_FN(jstring, nativeLoadIpl)(JNIEnv *env, jclass, jstring path) {
    if (!g) {
        return NewJStr(env, "not initialized");
    }
    const std::string p = JStr(env, path);
    const std::string err = RunSync([p]() -> std::string {
        std::vector<uint8_t> rom(ymir::sys::kIPLSize);
        FILE *f = fopen(p.c_str(), "rb");
        if (!f) {
            return "Cannot open IPL ROM";
        }
        fseek(f, 0, SEEK_END);
        const long sz = ftell(f);
        fseek(f, 0, SEEK_SET);
        if (sz != (long)ymir::sys::kIPLSize) {
            fclose(f);
            return "IPL ROM size mismatch: expected " + std::to_string(ymir::sys::kIPLSize) + " bytes, got " +
                   std::to_string(sz);
        }
        const size_t n = fread(rom.data(), 1, rom.size(), f);
        fclose(f);
        if (n != rom.size()) {
            return "Failed to read IPL ROM";
        }
        g->saturn->LoadIPL(std::span<uint8_t, ymir::sys::kIPLSize>(rom));
        return {};
    });
    return NewJStr(env, err);
}

JNI_FN(jstring, nativeLoadDisc)(JNIEnv *env, jclass, jstring path, jboolean preload) {
    if (!g) {
        return NewJStr(env, "not initialized");
    }
    const std::string p = JStr(env, path);
    const bool pre = preload;
    // Parse the disc image outside the emulation thread so the UI can show progress without stalling emulation.
    auto disc = std::make_shared<ymir::media::Disc>();
    std::string log;
    const bool ok = ymir::media::LoadDisc(p, *disc, pre, [&log](ymir::media::MessageType, std::string msg) {
        log += msg + "\n";
    });
    if (!ok) {
        return NewJStr(env, log.empty() ? "Failed to load disc image" : log);
    }
    RunSync([disc]() -> int {
        g->saturn->LoadDisc(std::move(*disc));
        g->saturn->AutodetectRegion();
        g->saturn->Reset(true);
        g->ring.Clear();
        return 0;
    });
    return nullptr;
}

JNI_FN(void, nativeEjectDisc)(JNIEnv *, jclass) {
    if (g) {
        Post([] { g->saturn->EjectDisc(); });
    }
}
JNI_FN(void, nativeSetTrayOpen)(JNIEnv *, jclass, jboolean open) {
    if (g) {
        Post([open] { open ? g->saturn->OpenTray() : g->saturn->CloseTray(); });
    }
}
JNI_FN(void, nativeReset)(JNIEnv *, jclass, jboolean hard) {
    if (g) {
        Post([hard] { g->saturn->Reset(hard); });
    }
}
JNI_FN(void, nativeSetPaused)(JNIEnv *, jclass, jboolean paused) {
    if (g) {
        g->paused = paused;
        if (paused) {
            g->ring.Clear();
        }
        g->cmdCv.notify_all();
    }
}
JNI_FN(void, nativeSetFastForward)(JNIEnv *, jclass, jboolean ff) {
    if (g) {
        g->fastForward = ff;
    }
}

// Input: `mask` uses ymir::peripheral::Button bit positions (see Kotlin SaturnButton).
JNI_FN(void, nativeSetButtons)(JNIEnv *, jclass, jint mask) {
    if (g) {
        g->pressed.store((uint16_t)mask, std::memory_order_relaxed);
    }
}
JNI_FN(void, nativeSetAnalog)(JNIEnv *, jclass, jint x, jint y, jint l, jint r) {
    if (g) {
        g->stickX = (uint8_t)x;
        g->stickY = (uint8_t)y;
        g->trigL = (uint8_t)l;
        g->trigR = (uint8_t)r;
    }
}
JNI_FN(void, nativeSetPadType)(JNIEnv *, jclass, jboolean analog) {
    if (g) {
        Post([analog] {
            g->analogPad = analog;
            ConnectPad(analog);
        });
    }
}

// Options
JNI_FN(void, nativeSetSH2Cache)(JNIEnv *, jclass, jboolean on) {
    if (g) {
        Post([on] { g->saturn->EnableSH2CacheEmulation(on); });
    }
}
JNI_FN(void, nativeSetVideoStandard)(JNIEnv *, jclass, jboolean pal) {
    if (g) {
        Post([pal] {
            g->saturn->SetVideoStandard(pal ? ymir::core::config::sys::VideoStandard::PAL
                                            : ymir::core::config::sys::VideoStandard::NTSC);
        });
    }
}
JNI_FN(void, nativeSetThreadedVdp)(JNIEnv *, jclass, jboolean on) {
    if (g) {
        Post([on] {
            g->saturn->configuration.swRenderer.threadedVDP1 = on;
            g->saturn->configuration.swRenderer.threadedVDP2 = on;
            g->saturn->configuration.swRenderer.threadedDeinterlacer = on;
        });
    }
}
// 0 = none, 1 = 1 MiB DRAM cart, 2 = 4 MiB DRAM cart
JNI_FN(void, nativeSetCartridge)(JNIEnv *, jclass, jint type) {
    if (g) {
        Post([type] {
            static int current = -1;
            if (type == current) {
                return; // unchanged: don't reset the running game
            }
            current = type;
            switch (type) {
            case 1: g->saturn->InsertCartridge<ymir::cart::DRAM8MbitCartridge>(); break;
            case 2: g->saturn->InsertCartridge<ymir::cart::DRAM32MbitCartridge>(); break;
            default: g->saturn->RemoveCartridge(); break;
            }
            g->saturn->Reset(true);
        });
    }
}

// Stats: [fps*100, videoW, videoH]
JNI_FN(jlongArray, nativeGetStats)(JNIEnv *env, jclass) {
    jlong v[3] = {0, 320, 224};
    if (g) {
        v[0] = (jlong)(g->fps.load() * 100.0f);
        v[1] = g->videoW.load();
        v[2] = g->videoH.load();
    }
    jlongArray arr = env->NewLongArray(3);
    env->SetLongArrayRegion(arr, 0, 3, v);
    return arr;
}

// Save states: files live in <dir>/<disc hash>/<slot>.savestate (same layout as the desktop app).
JNI_FN(jstring, nativeGetDiscHash)(JNIEnv *env, jclass) {
    if (!g) {
        return nullptr;
    }
    const std::string h = RunSync([] { return ymir::ToString(g->saturn->GetDiscHash()); });
    return NewJStr(env, h);
}

JNI_FN(jstring, nativeSaveState)(JNIEnv *env, jclass, jstring path) {
    if (!g) {
        return NewJStr(env, "not initialized");
    }
    const std::string p = JStr(env, path);
    return NewJStr(env, RunSync([p] { return savestate_io::Save(*g->saturn, p); }));
}

JNI_FN(jstring, nativeLoadState)(JNIEnv *env, jclass, jstring path) {
    if (!g) {
        return NewJStr(env, "not initialized");
    }
    const std::string p = JStr(env, path);
    return NewJStr(env, RunSync([p] {
                       auto err = savestate_io::Load(*g->saturn, p);
                       g->ring.Clear();
                       return err;
                   }));
}

// Display: renderer 0 = native window (CPU copy), 1 = OpenGL ES. Set before the surface is created.
// Switches the VDP emulation between the software renderer and the Vulkan compute (GPU) renderer.
// Runs on the emulation thread. If the GPU renderer cannot be created the software renderer stays active.
void ApplyVdpRenderer(bool gpu) {
    auto &vdp = g->saturn->VDP;
    const bool isGpu = vdp.GetRendererAs<ymir::vdp::VDPRendererType::Vulkan>() != nullptr;
    if (gpu == isGpu) {
        return;
    }
    if (gpu) {
        auto result = vdp.UseVulkanRenderer(ymir::vdp::VulkanRendererContext{});
        std::lock_guard lk(g->winMutex);
        if (!result) {
            g->gpuError = result.Error().message;
            __android_log_print(ANDROID_LOG_WARN, "GBSat", "Vulkan VDP renderer failed: %s", g->gpuError.c_str());
        } else {
            g->gpuError.clear();
        }
    } else {
        vdp.UseSoftwareRenderer();
    }
}

JNI_FN(void, nativeSetRenderer)(JNIEnv *, jclass, jint mode) {
    if (g) {
        g->glMode = (mode == 1 || mode == 3);
        g->rendererMode = mode;
        if (mode == 1 || mode == 3) {
            glr::SetBackend(mode == 3); // 3 = OpenGL ES through ANGLE (Vulkan)
        }
        const bool gpu = mode == 4;
        Post([gpu] { ApplyVdpRenderer(gpu); });
    }
}

// Last Vulkan GPU (VDP) renderer error, empty if none.
JNI_FN(jstring, nativeGetGpuError)(JNIEnv *env, jclass) {
    if (!g) {
        return NewJStr(env, "");
    }
    std::lock_guard lk(g->winMutex);
    return NewJStr(env, g->gpuError);
}

// Returns "ok|<device>|int64" if Vulkan is usable, otherwise an error text starting with "error|".
JNI_FN(jstring, nativeProbeVulkan)(JNIEnv *env, jclass) {
    const auto info = vkctx::Probe();
    if (!info.available) {
        return NewJStr(env, "error|" + info.error);
    }
    return NewJStr(env, "ok|" + info.name + "|" + (info.shaderInt64 ? "int64" : "noint64"));
}

// Last Vulkan init error (empty if none).
JNI_FN(jstring, nativeGetVulkanError)(JNIEnv *env, jclass) {
    if (!g) {
        return NewJStr(env, "");
    }
    std::lock_guard lk(g->winMutex);
    return NewJStr(env, g->vkFailed ? g->vkError : "");
}
JNI_FN(void, nativeSetDisplayOptions)(JNIEnv *, jclass, jint filter, jint shader) {
    glr::SetOptions(filter, shader);
    if (g) {
        std::lock_guard lk(g->winMutex);
        g->vkLinear = filter != 0;
    }
}

// Cheats: parallel arrays of address / value / size (1, 2 or 4 bytes).
JNI_FN(void, nativeSetCheats)(JNIEnv *env, jclass, jlongArray addrs, jlongArray values, jintArray sizes) {
    if (!g) {
        return;
    }
    const jsize n = env->GetArrayLength(addrs);
    std::vector<Context::Cheat> list(n);
    std::vector<jlong> a(n), v(n);
    std::vector<jint> sz(n);
    if (n > 0) {
        env->GetLongArrayRegion(addrs, 0, n, a.data());
        env->GetLongArrayRegion(values, 0, n, v.data());
        env->GetIntArrayRegion(sizes, 0, n, sz.data());
    }
    for (jsize i = 0; i < n; ++i) {
        list[i] = {(uint32_t)a[i], (uint32_t)v[i], sz[i]};
    }
    Post([list = std::move(list)]() mutable { g->cheats = std::move(list); });
}

// Fast boot: mark the SMPC clock/language setup as done so the BIOS skips its first-run screens.
JNI_FN(void, nativeSetFastBoot)(JNIEnv *, jclass, jboolean on) {
    if (g && on) {
        Post([] {
            ymir::smpc::PersistentSMPCData d;
            g->saturn->SMPC.SavePersistentData(d);
            if (!d.STE) {
                d.STE = true;
                g->saturn->SMPC.LoadPersistentData(d);
                WriteSmpc(d);
            }
        });
    }
}
