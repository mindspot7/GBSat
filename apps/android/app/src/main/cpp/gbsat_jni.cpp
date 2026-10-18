// Ymir Android bridge: runs ymir-core on a dedicated emulation thread, presents frames to an ANativeWindow,
// and streams SCSP audio through AAudio. All emulator state is only touched from the emulation thread;
// the UI talks to it through a command queue (see Post/RunSync).
#include <jni.h>

#include <aaudio/AAudio.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>
#include <unwind.h>
#include <android/log.h>
#include <android/native_window_jni.h>

#include <ymir/ymir.hpp>

#include "gbsat_ra.hpp"
#include "gl_renderer.hpp"

extern "C" {
#include <md5.h>
}
#include "vk_context.hpp"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <deque>
#include <filesystem>
#include <array>
#include <fstream>
#include <iterator>
#include <functional>
#include <future>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#define LOG_TAG "GBSat"
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
    std::string discHeader; // first 256 bytes of the disc's system area (IP.BIN header), printable ASCII

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
    uint32_t resScale = 1; // Vulkan GPU renderer internal resolution scale (1..4)
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

    // Renderer comparison (emulation thread only): captured frames
    struct Captured {
        std::vector<uint32_t> px, prev;
        uint32_t w = 0, h = 0;
        int count = 0;
    };
    Captured *capture = nullptr;
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
// Crash diagnostics: a fatal signal writes a small report (signal, current stage, backtrace) to a file that the app
// shows on the next launch. Everything in the handler is async-signal-safe except dladdr, which is fine in practice.

char g_crashPath[512] = {};
char g_stage[192] = "starting";
std::atomic<int> g_crashRenderer{0};

void SetStage(const char *stage) {
    std::strncpy(g_stage, stage, sizeof(g_stage) - 1);
    g_stage[sizeof(g_stage) - 1] = 0;
}

void WriteStr(int fd, const char *s) {
    if (s) write(fd, s, std::strlen(s));
}

void WriteHex(int fd, uintptr_t v) {
    char buf[2 + 16 + 1];
    buf[0] = '0';
    buf[1] = 'x';
    for (int i = 0; i < 16; ++i) {
        const int nib = (v >> ((15 - i) * 4)) & 0xF;
        buf[2 + i] = "0123456789abcdef"[nib];
    }
    buf[18] = 0;
    WriteStr(fd, buf);
}

void WriteDec(int fd, long v) {
    char buf[24];
    int n = 0;
    bool neg = v < 0;
    unsigned long u = neg ? -v : v;
    do { buf[n++] = '0' + (u % 10); u /= 10; } while (u && n < 20);
    if (neg) buf[n++] = '-';
    for (int i = n - 1; i >= 0; --i) write(fd, &buf[i], 1);
}

struct BacktraceState {
    uintptr_t pcs[32];
    int count = 0;
};

_Unwind_Reason_Code UnwindCb(struct _Unwind_Context *ctx, void *arg) {
    auto *st = static_cast<BacktraceState *>(arg);
    const uintptr_t pc = _Unwind_GetIP(ctx);
    if (pc != 0 && st->count < 32) {
        st->pcs[st->count++] = pc;
    }
    return st->count < 32 ? _URC_NO_REASON : _URC_END_OF_STACK;
}

void CrashHandler(int sig, siginfo_t *info, void *) {
    int fd = open(g_crashPath, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd >= 0) {
        WriteStr(fd, "signal=");
        WriteDec(fd, sig);
        WriteStr(fd, " (");
        WriteStr(fd, sig == SIGSEGV ? "SIGSEGV" : sig == SIGABRT ? "SIGABRT" : sig == SIGBUS ? "SIGBUS" : sig == SIGILL ? "SIGILL" : sig == SIGFPE ? "SIGFPE" : "?");
        WriteStr(fd, ") addr=");
        WriteHex(fd, info ? (uintptr_t)info->si_addr : 0);
        WriteStr(fd, "\nrenderer=");
        WriteDec(fd, g_crashRenderer.load());
        WriteStr(fd, "\nstage=");
        WriteStr(fd, g_stage);
        WriteStr(fd, "\nbacktrace:\n");
        BacktraceState st;
        _Unwind_Backtrace(UnwindCb, &st);
        for (int i = 0; i < st.count; ++i) {
            Dl_info dli{};
            WriteStr(fd, "  #");
            WriteDec(fd, i);
            WriteStr(fd, " ");
            if (dladdr((void *)st.pcs[i], &dli) && dli.dli_fname) {
                const char *base = std::strrchr(dli.dli_fname, '/');
                WriteStr(fd, base ? base + 1 : dli.dli_fname);
                WriteStr(fd, "+");
                WriteHex(fd, st.pcs[i] - (uintptr_t)dli.dli_fbase);
                if (dli.dli_sname) {
                    WriteStr(fd, " ");
                    WriteStr(fd, dli.dli_sname);
                }
            } else {
                WriteHex(fd, st.pcs[i]);
            }
            WriteStr(fd, "\n");
        }
        close(fd);
    }
    signal(sig, SIG_DFL);
    raise(sig);
}

void InstallCrashHandler() {
    struct sigaction sa {};
    sa.sa_sigaction = CrashHandler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);
    for (int sig : {SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE}) {
        sigaction(sig, &sa, nullptr);
    }
}

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
    if (g->capture != nullptr) {
        auto &c = *g->capture;
        c.prev.swap(c.px);
        c.px.assign(fb, fb + (size_t)width * height);
        c.w = width;
        c.h = height;
        ++c.count;
    }
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
            lk.unlock();
            ra::Idle();
            nextFrame = steady_clock::now();
            fpsWindowStart = nextFrame;
            fpsFrames = 0;
            continue;
        }

        g->saturn->RunFrame();
        bool skipNext = false;
        const bool cheatsAllowed = !ra::IsHardcore();
        for (const auto &c : g->cheats) {
            if (!cheatsAllowed) {
                break;
            }
            if (skipNext) {
                skipNext = false;
                continue;
            }
            switch (c.size) {
            case -2: // 16-bit "if equal": run the next code only when memory matches
                skipNext = g->saturn->mainBus.Peek<uint16_t>(c.addr) != (uint16_t)c.value;
                break;
            case 1: g->saturn->mainBus.Poke<uint8_t>(c.addr, (uint8_t)c.value); break;
            case 4: g->saturn->mainBus.Poke<uint32_t>(c.addr, c.value); break;
            default: g->saturn->mainBus.Poke<uint16_t>(c.addr, (uint16_t)c.value); break;
            }
        }
        ra::DoFrame();
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
// JNI exports — class app.gbsat.android.NativeBridge

#define JNI_FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_app_gbsat_android_NativeBridge_##name

JNI_FN(void, nativeSetCrashFile)(JNIEnv *env, jclass, jstring path) {
    const std::string p = JStr(env, path);
    std::strncpy(g_crashPath, p.c_str(), sizeof(g_crashPath) - 1);
    InstallCrashHandler();
}

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
    // RetroAchievements hash: MD5 of the first 512 bytes of sector 0 of track 1 (volume + ROM headers)
    std::string raHash;
    if (!disc->sessions.empty()) {
        const auto &session = disc->sessions.front();
        const auto &track = session.tracks[session.firstTrackIndex];
        std::array<uint8_t, 2048> sector{};
        if (track.ReadSectorUserData(track.startFrameAddress, sector)) {
            md5_state_t md5;
            md5_byte_t digest[16];
            md5_init(&md5);
            md5_append(&md5, sector.data(), 512);
            md5_finish(&md5, digest);
            char hex[33];
            for (int i = 0; i < 16; ++i) snprintf(hex + i * 2, 3, "%02x", digest[i]);
            raHash = hex;
            std::string hdr(reinterpret_cast<const char *>(sector.data()), 256);
            for (char &c : hdr) {
                if (c < 0x20 || c > 0x7E) c = ' ';
            }
            std::lock_guard lk(g->winMutex);
            g->discHeader = hdr;
        }
    }
    RunSync([disc, raHash]() -> int {
        g->saturn->LoadDisc(std::move(*disc));
        g->saturn->AutodetectRegion();
        g->saturn->Reset(true);
        g->ring.Clear();
        ra::LoadGame(raHash);
        return 0;
    });
    return nullptr;
}

JNI_FN(void, nativeEjectDisc)(JNIEnv *, jclass) {
    if (g) {
        Post([] {
            ra::UnloadGame();
            g->saturn->EjectDisc();
        });
    }
}
JNI_FN(void, nativeSetTrayOpen)(JNIEnv *, jclass, jboolean open) {
    if (g) {
        Post([open] { open ? g->saturn->OpenTray() : g->saturn->CloseTray(); });
    }
}
JNI_FN(void, nativeReset)(JNIEnv *, jclass, jboolean hard) {
    if (g) {
        Post([hard] {
            g->saturn->Reset(hard);
            ra::Reset();
        });
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

// Disc header (IP.BIN): product number at 0x20, area symbols at 0x40, title at 0x60.
JNI_FN(jstring, nativeGetDiscHeader)(JNIEnv *env, jclass) {
    if (!g) {
        return nullptr;
    }
    std::lock_guard lk(g->winMutex);
    return NewJStr(env, g->discHeader);
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
    return NewJStr(env, RunSync([p] {
        auto err = savestate_io::Save(*g->saturn, p);
        if (err.empty()) {
            // RetroAchievements progress (achievement hit counts) next to the state
            const auto progress = ra::SerializeProgress();
            std::ofstream out{p + ".ra", std::ios::binary | std::ios::trunc};
            out.write(reinterpret_cast<const char *>(progress.data()), (std::streamsize)progress.size());
        }
        return err;
    }));
}

JNI_FN(jstring, nativeLoadState)(JNIEnv *env, jclass, jstring path) {
    if (!g) {
        return NewJStr(env, "not initialized");
    }
    const std::string p = JStr(env, path);
    return NewJStr(env, RunSync([p]() -> std::string {
                       if (ra::IsHardcore()) {
                           return "Loading states is disabled in RetroAchievements hardcore mode";
                       }
                       auto err = savestate_io::Load(*g->saturn, p);
                       if (err.empty()) {
                           std::ifstream in{p + ".ra", std::ios::binary};
                           std::vector<uint8_t> progress{std::istreambuf_iterator<char>(in), {}};
                           ra::DeserializeProgress(progress);
                       }
                       g->ring.Clear();
                       return err;
                   }));
}

// Display: renderer 0 = native window (CPU copy), 1 = OpenGL ES. Set before the surface is created.
// Switches the VDP emulation between the software renderer and the Vulkan compute (GPU) renderer.
// Runs on the emulation thread. If the GPU renderer cannot be created the software renderer stays active.
void ApplyVdpRenderer(bool gpu, bool force = false) {
    auto &vdp = g->saturn->VDP;
    const bool isGpu = vdp.GetRendererAs<ymir::vdp::VDPRendererType::Vulkan>() != nullptr;
    if (gpu == isGpu && !force) {
        return;
    }
    if (gpu) {
        if (isGpu) {
            // Recreating the GPU renderer (e.g. new resolution scale): release the current one first
            vdp.UseSoftwareRenderer();
        }
        SetStage("vk: switching to the Vulkan GPU renderer");
        ymir::vdp::VulkanRendererContext ctx{};
        ctx.resolutionScale = g->resScale;
        ctx.progress = [](const char *stage) {
            SetStage(stage);
            __android_log_print(ANDROID_LOG_INFO, "GBSat", "%s", stage);
        };
        auto result = vdp.UseVulkanRenderer(ctx);
        SetStage(result ? "running (Vulkan GPU renderer)" : "running (software renderer, GPU init failed)");
        std::lock_guard lk(g->winMutex);
        if (!result) {
            g->gpuError = result.Error().message;
            __android_log_print(ANDROID_LOG_WARN, "GBSat", "Vulkan VDP renderer failed: %s", g->gpuError.c_str());
        } else {
            g->gpuError.clear();
        }
    } else {
        SetStage("switching to the software renderer");
        vdp.UseSoftwareRenderer();
        SetStage("running (software renderer)");
    }
}

JNI_FN(void, nativeSetRenderer)(JNIEnv *, jclass, jint mode) {
    if (g) {
        g->glMode = (mode == 1 || mode == 3);
        g->rendererMode = mode;
        g_crashRenderer = mode;
        if (mode == 1 || mode == 3) {
            glr::SetBackend(mode == 3); // 3 = OpenGL ES through ANGLE (Vulkan)
        }
        const bool gpu = mode == 4;
        Post([gpu] { ApplyVdpRenderer(gpu); });
    }
}

// Internal resolution scale of the Vulkan GPU renderer (1..4). Recreates the renderer when it is active.
JNI_FN(void, nativeSetResolutionScale)(JNIEnv *, jclass, jint scale) {
    if (!g) {
        return;
    }
    const uint32_t s = static_cast<uint32_t>(std::clamp<jint>(scale, 1, 4));
    Post([s] {
        if (s == g->resScale) {
            return;
        }
        g->resScale = s;
        if (g->saturn->VDP.GetRendererAs<ymir::vdp::VDPRendererType::Vulkan>() != nullptr) {
            ApplyVdpRenderer(true, true);
        }
    });
}

// Diagnostics: renders the next frames with the software and the Vulkan renderer from the same state and reports the
// differences, plus the Vulkan renderer's intermediate layer data at some differing pixels.
JNI_FN(jstring, nativeCompareRenderers)(JNIEnv *env, jclass) {
    if (!g) {
        return NewJStr(env, "not initialized");
    }
    const std::string report = RunSync([]() -> std::string {
        auto &sat = *g->saturn;
        auto &vdp = sat.VDP;
        const bool wasGpu = vdp.GetRendererAs<ymir::vdp::VDPRendererType::Vulkan>() != nullptr;
        auto state = std::make_unique<ymir::savestate::SaveState>();
        sat.SaveState(*state);
        const bool t1 = sat.configuration.swRenderer.threadedVDP1, t2 = sat.configuration.swRenderer.threadedVDP2,
                   t3 = sat.configuration.swRenderer.threadedDeinterlacer;
        sat.configuration.swRenderer.threadedVDP1 = false;
        sat.configuration.swRenderer.threadedVDP2 = false;
        sat.configuration.swRenderer.threadedDeinterlacer = false;

        Context::Captured sw, vk;
        std::string out;
        // Software
        vdp.UseSoftwareRenderer();
        sat.LoadState(*state);
        g->capture = &sw;
        for (int i = 0; i < 4; ++i) sat.RunFrame();
        // Vulkan
        auto r = vdp.UseVulkanRenderer(ymir::vdp::VulkanRendererContext{});
        if (!r) {
            out = "Vulkan renderer failed: " + r.Error().message;
        } else {
            sat.LoadState(*state);
            g->capture = &vk;
            for (int i = 0; i < 4; ++i) sat.RunFrame();
        }
        g->capture = nullptr;
        if (out.empty()) {
            auto *vkr = vdp.GetRendererAs<ymir::vdp::VDPRendererType::Vulkan>();
            out += "GPU: " + vkr->DebugDeviceInfo() + "\n";
            const auto &ref = sw.prev.empty() ? sw.px : sw.prev; // Vulkan delivers one frame late
            char buf[256];
            snprintf(buf, sizeof(buf), "software %ux%u (%d frames), vulkan %ux%u (%d frames)\n", sw.w, sw.h, sw.count,
                     vk.w, vk.h, vk.count);
            out += buf;
            if (sw.w == vk.w && sw.h == vk.h && !ref.empty() && !vk.px.empty()) {
                size_t diff = 0;
                std::vector<std::pair<uint32_t, uint32_t>> samples;
                uint32_t minY = UINT32_MAX, maxY = 0, minX = UINT32_MAX, maxX = 0;
                for (uint32_t y = 0; y < sw.h; ++y) {
                    for (uint32_t x = 0; x < sw.w; ++x) {
                        const uint32_t a = ref[y * sw.w + x] & 0xFFFFFF, b = vk.px[y * sw.w + x] & 0xFFFFFF;
                        if (a != b) {
                            ++diff;
                            minY = std::min(minY, y); maxY = std::max(maxY, y);
                            minX = std::min(minX, x); maxX = std::max(maxX, x);
                            if (samples.size() < 6 && (diff % 97) == 1) samples.push_back({x, y});
                        }
                    }
                }
                snprintf(buf, sizeof(buf), "differing pixels: %zu, area x %u-%u y %u-%u\n", diff, minX, maxX, minY, maxY);
                out += diff ? buf : "differing pixels: 0\n";
                for (auto [x, y] : samples) {
                    snprintf(buf, sizeof(buf), "sw=%06X vk=%06X ", ref[y * sw.w + x] & 0xFFFFFF, vk.px[y * sw.w + x] & 0xFFFFFF);
                    out += buf + vkr->DebugDescribePixel(x, y) + "\n";
                }
            }
        }
        // Restore
        sat.configuration.swRenderer.threadedVDP1 = t1;
        sat.configuration.swRenderer.threadedVDP2 = t2;
        sat.configuration.swRenderer.threadedDeinterlacer = t3;
        if (!wasGpu) {
            vdp.UseSoftwareRenderer();
        }
        sat.LoadState(*state);
        return out;
    });
    return NewJStr(env, report);
}

// RetroAchievements: initialization and achievement list (see gbsat_ra.cpp for the rest)
JNI_FN(void, nativeRaInit)(JNIEnv *env, jclass, jstring userAgent) {
    if (!g) {
        return;
    }
    ra::Init([](std::function<void()> fn) { Post(std::move(fn)); },
             [](uint32_t addr) -> uint8_t { return g->saturn->mainBus.Peek<uint8_t>(addr); }, JStr(env, userAgent));
}

// Returned as UTF-8 bytes: titles may contain characters that JNI's modified UTF-8 strings cannot carry.
JNI_FN(jbyteArray, nativeRaAchievements)(JNIEnv *env, jclass) {
    const std::string s = g ? RunSync([] { return ra::AchievementList(); }) : std::string{};
    jbyteArray arr = env->NewByteArray((jsize)s.size());
    env->SetByteArrayRegion(arr, 0, (jsize)s.size(), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
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
