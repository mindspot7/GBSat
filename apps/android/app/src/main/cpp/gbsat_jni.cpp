#include "../../../../../../libs/gbsat-core/src/ymir/hw/sh2/jit/sh2_jit.hpp"
#include <gb_data_storage.hpp>
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
#include <sys/system_properties.h>
#include <android/native_window_jni.h>

#include <ymir/ymir.hpp>
#include <ymir/hw/vdp/renderer/vdp_renderer_sw.hpp>
#include <ymir/util/wait_stats.hpp>
#include <ymir/util/data_ops.hpp>
#include <ymir/db/game_db.hpp>
#include <ymir/db/ipl_db.hpp>
#include <ymir/hw/cart/cart.hpp>

#include "gbsat_ra.hpp"
#include "gl_renderer.hpp"
#include "cpu_tuning.hpp"
#include "perf_probe.hpp"
#include "auto_skip.hpp"
#include "diag_props.hpp"

extern "C" {
#include <md5.h>
}

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <deque>
#include <filesystem>
#include <array>
#include <bitset>
#include <cstdlib>
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
    std::atomic<uint32_t> underruns{0}; // callbacks that found too few samples while the game ran (performance log)
    std::atomic<uint32_t> minFill{UINT32_MAX}; // lowest buffer level seen by a callback (frames, performance log)
    std::atomic<uint32_t> burst{0};            // frames asked for by the last callback (performance log)
    int16_t lastL = 0, lastR = 0; // last samples played (consumer side), used to fade out on underruns

    void Push(int16_t l, int16_t r) {
        const uint32_t h = head.load(std::memory_order_relaxed);
        if (h - tail.load(std::memory_order_acquire) >= kRingFrames) {
            return; // full: drop (never block the emulator on audio)
        }
        data[(h & (kRingFrames - 1)) * 2] = l;
        data[(h & (kRingFrames - 1)) * 2 + 1] = r;
        head.store(h + 1, std::memory_order_release);
    }
    // Consumer side (AAudio callback only): read position between two samples and the current playback rate
    double pos = 0.0, step = 1.0;

    void Pop(int16_t *out, int32_t frames, uint32_t target, bool running) {
        uint32_t t = tail.load(std::memory_order_relaxed);
        const uint32_t avail = head.load(std::memory_order_acquire) - t;
        if (running) {
            burst.store((uint32_t)frames, std::memory_order_relaxed);
            if (avail < minFill.load(std::memory_order_relaxed)) minFill.store(avail, std::memory_order_relaxed);
        }
        // Dynamic rate control: when the buffer runs low (the game runs slightly slower than real time) the audio plays
        // up to 5% slower, interpolating between samples, instead of running dry: the pitch change is inaudible, the
        // clicks of underruns are not (the performance log showed ~30 underruns per second at 59.8 fps)
        const double fillRatio = target ? (double)avail / target : 1.0;
        const double want = std::clamp(1.0 + 0.05 * (fillRatio - 1.0), 0.95, 1.005);
        step += (want - step) * 0.1;
        uint32_t n = 0;
        for (; n < (uint32_t)frames; ++n) {
            const uint32_t k = (uint32_t)pos;
            if (k + 1 >= avail) {
                break;
            }
            const double f = pos - k;
            const uint32_t a = ((t + k) & (kRingFrames - 1)) * 2, b = ((t + k + 1) & (kRingFrames - 1)) * 2;
            out[n * 2] = (int16_t)(data[a] + (data[b] - data[a]) * f);
            out[n * 2 + 1] = (int16_t)(data[a + 1] + (data[b + 1] - data[a + 1]) * f);
            pos += step;
        }
        const uint32_t used = std::min<uint32_t>((uint32_t)pos, avail);
        pos -= used;
        t += used;
        if (n > 0) {
            lastL = out[(n - 1) * 2];
            lastR = out[(n - 1) * 2 + 1];
        }
        if (n < (uint32_t)frames) {
            if (running) underruns.fetch_add(1, std::memory_order_relaxed);
            // Underrun (emulation behind): ramp from the last sample down to silence instead of cutting to zero,
            // which avoids audible clicks
            const uint32_t missing = frames - n;
            const uint32_t ramp = std::min<uint32_t>(missing, 64);
            for (uint32_t i = 0; i < missing; ++i) {
                const int32_t k = i < ramp ? (int32_t)(ramp - i) : 0;
                out[(n + i) * 2] = (int16_t)(lastL * k / (int32_t)(ramp ? ramp : 1));
                out[(n + i) * 2 + 1] = (int16_t)(lastR * k / (int32_t)(ramp ? ramp : 1));
            }
            lastL = lastR = 0;
        }
        tail.store(t, std::memory_order_release);
    }
    void Clear() {
        tail.store(head.load(std::memory_order_acquire), std::memory_order_release);
    }
};

// ---------------------------------------------------------------------------------------------------------------------
// Global emulator context

struct FrameSkipSample {
    uint64_t renderedNs = 0, skippedNs = 0;
    uint32_t renderedFrames = 0, skippedFrames = 0;
};

struct Context {
    std::unique_ptr<ymir::Saturn> saturn;
    // Last displayed frame (downscaled to at most ~352 px wide), for save state thumbnails. Guarded by lastFrameMutex.
    std::mutex lastFrameMutex;
    std::vector<uint32_t> lastFrame;
    uint32_t lastFrameW = 0, lastFrameH = 0;
    std::string dataDir;      // GBSat data folder (carts/ holds the ROM cartridge dumps)
    int cartType = -1;        // cartridge in the slot: 0 none, 1/2/3 = 1/4/6 MiB RAM, 4 = backup memory, 10 = ROM
    std::string cartNotice;   // message about the cartridge picked for the last loaded disc (read once by the app)
    std::string discHeader; // first 256 bytes of the disc's system area (IP.BIN header), printable ASCII

    // Threading
    std::thread emuThread;
    std::atomic<bool> running{false};
    std::atomic<bool> paused{true};
    std::atomic<bool> fastForward{false};
    // Skip Engine (GBFrame gb::AutoSkip): maximum speed (sound muted) through CD loads behind a still, silent screen,
    // silent black transitions and backup memory waits; also says when the faster CD read speed is safe (UpdateAutoSkip)
    gb::AutoSkip skip;
    uint32_t lastCdSectors = 0, lastCdSeek = 0, lastBup = 0; // core activity counters at the last frame
    // Fast boot (GBFrame rule): the BIOS intro runs on the Skip Engine's fast path until the disc's 1st read program runs
    std::atomic<bool> fastBoot{false};
    std::atomic<bool> booting{false};
    int bootFrames = 0, bootGameFrames = 0, bootRegion = -1; // emulation thread only
    std::bitset<256> biosPages; // 4 KiB pages of WRAM-H where the BIOS ran before IP.BIN was loaded
    std::chrono::steady_clock::time_point bootStart;
    // CD read speed: the user's choice, and the one applied (the original 2x while the game streams from the CD)
    uint8_t cdSpeedUser = 2, cdSpeedApplied = 2;
    std::atomic<float> volume{1.0f};   // output volume 0..1
    std::atomic<int> ffAudio{0};       // fast-forward audio: 0 = default (normal pitch, skips), 1 = speed up, 2 = mute
    float ffDecimAcc = 0.0f;           // sample decimation accumulator for "speed up" (SCSP thread)
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
    std::atomic<bool> palMode{false};
    std::atomic<uint32_t> lastScale{1};
    std::atomic<bool> frameSkip{false}; // automatic frame skip: skip rendering only while running below full speed
    bool behind = false;                // last frame finished late (emulation thread only)
    int skipRun = 0;                    // consecutive skipped frames
    bool skipActive = false; // automatic frame skip engaged (emulation thread only)
    bool pairSkip = false;   // skip decision for the current field pair
    std::atomic<bool> oneField{false}; // hi-res interlaced screens: render only the first field of each pair
    bool deinterlaceAllowed = false;   // device tier mid and above (set by the app); used with the Vulkan renderer only
    bool lastField = false;      // field bit seen on the previous frame
    bool prevFirstField = false; // the previous frame was the first field of an interlaced pair
    int notBehindRun = 0;    // consecutive frames on time
    int renderRun = 0;       // consecutive rendered frames
    int minRenderRun = 2;    // frames shown between skips (adapted every second while skipping: 1..4)
    // Does skipping help? Skipped frames save the VDP's work; when the emulation thread is bound by the SH-2s (rendering
    // runs on other threads) a skipped frame takes as long as a rendered one, and skipping only drops frames from the
    // screen (RG DS, Virtua Fighter 2: 1 of 2 frames skipped, both ~21 ms). Compare rolling windows from similar scenes.
    int64_t fsRenderedNs = 0, fsSkippedNs = 0;
    int fsRendered = 0, fsSkipped = 0;
    bool fsUseless = false; // skipping did not help the current scene: retry after fsHold (scene may change)
    int fsHold = 0;         // seconds
    std::array<FrameSkipSample, 3> fsHistory{};
    size_t fsHistoryCount = 0, fsHistoryNext = 0;
    uint32_t fsPoorWindows = 0;
    float fsSceneVdp1Commands = -1.0f;
    uint32_t fsSceneWidth = 0, fsSceneHeight = 0;
    int fsSceneRendererMode = -1;
    std::chrono::steady_clock::time_point lastRendered{}; // fast forward: last frame that was rendered
    bool frameParity = false;
    std::atomic<bool> skipPresent{false};
    uint32_t resScale = 1; // Vulkan GPU renderer internal resolution scale (1..4)
    std::atomic<uint64_t> frameCounter{0};

    // Audio
    AudioRing ring;
    std::atomic<uint32_t> targetFill{kTargetBufferedFrames};
    AAudioStream *stream = nullptr;
    std::mutex audioMutex;
    std::atomic<bool> audioOk{false};

    // Display
    std::atomic<bool> glMode{false};
    std::atomic<int> rendererMode{0}; // 0 = software (CPU window), 1 = OpenGL ES, 4 = Vulkan GPU (presented through OpenGL ES)
    std::string gpuError; // last Vulkan VDP renderer error (guarded by winMutex)


    // Cheats (emulation thread only): constant memory writes applied after every frame
    struct Cheat { uint32_t addr; uint32_t value; int size; };
    std::vector<Cheat> cheats;

    // Misc
    std::string smpcPath;
    std::string lastError;
    std::atomic<float> fps{0.0f};
    std::atomic<uint32_t> presented{0}; // frames sent to the screen (performance log)
    bool scspAuto = true;               // automatic threaded audio (emulation thread)
    int pinInFrames = 0;                // re-pin threads after this many frames (thread layout changed)
    struct {
        int phase = 0, secs = 0;
        float sum = 0, baseFps = 0;
        std::chrono::steady_clock::time_point cooldownUntil{};
    } scspTune;
    std::atomic<int64_t> frameTimeUs{0}; // emulation time per frame (smoothed)
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

// A/B diagnostics, switched with adb while a game runs (gbsat.bat, option 6), read once per second:
//   debug.gbsat.sh2_plain=1   SH-2 plain interpreter instead of the chained one (SH2::RunChained)
//   debug.gbsat.vdp2_one_fb=1 threaded VDP2 with one framebuffer (waits for the previous frame, as before build254)
//   debug.gbsat.vdp_nobatch=1 VDP1 commands and VDP2 lines sent to the render threads one by one (as before build264)
// The performance log's settings column shows them (diag=...).

static void ReadDiagnosticProps() {
    util::waitstats::g_sh2Chained.store(!gb::DiagOn("gbsat", "sh2_plain"), std::memory_order_relaxed);
    util::waitstats::g_vdp2TwoFB.store(!gb::DiagOn("gbsat", "vdp2_one_fb"), std::memory_order_relaxed);
    util::waitstats::g_vdpBatch.store(!gb::DiagOn("gbsat", "vdp_nobatch"), std::memory_order_relaxed);
}

void OnFrameComplete(uint32_t *fb, uint32_t width, uint32_t height) {
    // Not gated by the frame skip flag: the software renderer no longer delivers skipped frames, and with threaded VDP2
    // it delivers a drawn frame later (while the next frame runs, which may be a skipped one); the Vulkan renderer
    // delivers its frames one frame late too.
    g->presented.fetch_add(1, std::memory_order_relaxed);
    {
        g->skip.OnFrame(fb, width, height); // Skip Engine: black / still picture
    }
    {
        const uint32_t step = std::max<uint32_t>(1, width / 352);
        const uint32_t tw = width / step, th = height / step;
        std::lock_guard lk(g->lastFrameMutex);
        g->lastFrame.resize((size_t)tw * th);
        for (uint32_t y = 0; y < th; ++y) {
            const uint32_t *src = fb + (size_t)(y * step) * width;
            uint32_t *dst = g->lastFrame.data() + (size_t)y * tw;
            for (uint32_t x = 0; x < tw; ++x) dst[x] = src[x * step] | 0xFF000000u;
        }
        g->lastFrameW = tw;
        g->lastFrameH = th;
    }
    g->videoW.store(width, std::memory_order_relaxed);
    g->videoH.store(height, std::memory_order_relaxed);
    g->frameCounter.fetch_add(1, std::memory_order_relaxed);

    if (g->glMode.load(std::memory_order_relaxed)) {
        uint32_t scale = 1;
        if (auto *vkr = g->saturn->VDP.GetRendererAs<ymir::vdp::VDPRendererType::Vulkan>()) {
            scale = vkr->DeliveredFrameScale();
        }
        g->lastScale.store(scale, std::memory_order_relaxed);
        glr::Submit(fb, width, height, scale);
        return;
    }

    g->lastScale.store(1, std::memory_order_relaxed);
    std::lock_guard lk(g->winMutex);
    if (!g->window) {
        return;
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
    auto *out = static_cast<int16_t *>(audioData);
    g->ring.Pop(out, numFrames, g->targetFill.load(std::memory_order_relaxed),
                g->running.load(std::memory_order_relaxed) && !g->paused.load(std::memory_order_relaxed));
    const float vol = g->volume.load(std::memory_order_relaxed);
    if (vol < 0.999f) {
        const int32_t v = (int32_t)(vol * 256.0f);
        for (int32_t i = 0; i < numFrames * 2; ++i) out[i] = (int16_t)((out[i] * v) >> 8);
    }
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
    const int32_t burst = AAudioStream_getFramesPerBurst(s);
    AAudioStream_setBufferSizeInFrames(s, burst * 2);
    // Keep enough audio queued to cover the device's callback size plus one emulated frame (~735 samples), so devices
    // with large bursts don't underrun between frames
    g->targetFill = std::max<uint32_t>(kTargetBufferedFrames, (uint32_t)burst * 2 + 1024);
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
    gb::DataOutputFile out(g->smpcPath, std::ios::binary | std::ios::trunc);
    out.write(reinterpret_cast<const char *>(d.SMEM.data()), 4);
    const uint8_t ste = d.STE ? 1 : 0;
    out.write(reinterpret_cast<const char *>(&ste), 1);
    out.write(reinterpret_cast<const char *>(&d.rtc.offset), sizeof(d.rtc.offset));
    out.write(reinterpret_cast<const char *>(&d.rtc.timestamp), sizeof(d.rtc.timestamp));
}

bool ReadSmpc(ymir::smpc::PersistentSMPCData &d) {
    gb::DataInputFile in(g->smpcPath, std::ios::binary);
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

// Maximum speed, muted, with dropped presentation: fast-forward, the Skip Engine or the fast BIOS intro
bool FastPath() {
    return g->fastForward.load() || g->skip.Active() || g->booting.load(std::memory_order_relaxed);
}

void EndBoot(const char *reason) {
    if (g->booting.exchange(false)) {
        const auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - g->bootStart).count();
        LOGI("Fast boot off (%s) after %d frames, %lld ms", reason, g->bootFrames, (long long)ms);
    }
}

// After a reset with a disc in the drive (emulation thread)
void StartBoot() {
    {
        std::lock_guard lk(g->winMutex);
        if (!g->fastBoot.load() || g->discHeader.empty()) {
            return;
        }
    }
    g->bootFrames = g->bootGameFrames = 0;
    g->bootRegion = -1;
    g->biosPages.reset();
    g->bootStart = std::chrono::steady_clock::now();
    g->booting = true;
    LOGI("Fast boot on");
}

// Once per emulated frame while booting (emulation thread). The BIOS loads the disc's IP.BIN to 06002000 and finally
// jumps to the 1st read program (address in the header at offset F0). Its own intro code also runs from WRAM-H, even
// above the 1st read address (06010000+ while the game starts at 06004000), so the address alone can't tell them
// apart: the pages the BIOS ran in before IP.BIN was loaded are remembered, and the game is running once the CPU stays
// in other WRAM-H pages after the header appeared.
void BootFrame() {
    if (!g->booting.load(std::memory_order_relaxed)) {
        return;
    }
    ++g->bootFrames;
    const auto &wram = g->saturn->mem.WRAMHigh;
    const uint32_t pc = g->saturn->masterSH2.GetProbe().PC() & 0x07FFFFFF;
    const bool header = std::memcmp(&wram[0x2000], "SEGA SEGASATURN ", 16) == 0;
    const uint32_t firstRead = header ? util::ReadBE<uint32_t>(&wram[0x20F0]) & 0x07FFFFFF : 0;
    const bool inWramH = pc >= 0x06000000 && pc < 0x06100000;
    const size_t page = (pc >> 12) & 0xFF;
    if (inWramH && !header) {
        g->biosPages.set(page);
    }
    const bool inGame = header && inWramH && !g->biosPages.test(page);
    // Where the master SH-2 runs, logged when it changes: 0 BIOS ROM, 1 WRAM-H (BIOS), 2 WRAM-H (game), 3 other
    const int region = pc < 0x00100000 ? 0 : inGame ? 2 : inWramH ? 1 : 3;
    if (region != g->bootRegion) {
        g->bootRegion = region;
        LOGI("Fast boot frame %d pc %08x region %d header %d 1st read %08x bios pages %zu", g->bootFrames, pc, region,
             header, firstRead, g->biosPages.count());
    }
    g->bootGameFrames = inGame ? g->bootGameFrames + 1 : 0;
    if (g->bootGameFrames >= 3) {
        EndBoot("disc running");
    } else if (g->fastForward.load()) {
        EndBoot("fast-forward");
    } else if (g->bootFrames > 30 * 60) {
        EndBoot("timeout");
    }
}

// Skip Engine and CD read speed, once per emulated frame (emulation thread): reports the core's CD and backup memory
// activity to gb::AutoSkip (GBFrame, which decides) and applies the CD read speed it allows.
void UpdateAutoSkip() {
    namespace ws = util::waitstats;
    const uint32_t sectors = ws::g_cdDataSectors.load(std::memory_order_relaxed);
    const uint32_t seek = ws::g_cdDataSeekTicks.load(std::memory_order_relaxed);
    const uint32_t bup = ws::g_bupAccesses.load(std::memory_order_relaxed);
    const bool cdActive = sectors != g->lastCdSectors || seek != g->lastCdSeek;
    const uint32_t bupDelta = bup - g->lastBup;
    g->lastCdSectors = sectors;
    g->lastCdSeek = seek;
    g->lastBup = bup;
    g->skip.Update(cdActive, bupDelta);

    // Faster CD reads only while the game isn't streaming video or music from the CD (fast reads during streams made
    // them go out of sync in some games)
    if (g->cdSpeedUser > 2) {
        const uint8_t speed = g->skip.FastDiscOk() ? g->cdSpeedUser : 2;
        if (speed != g->cdSpeedApplied) {
            g->cdSpeedApplied = speed;
            g->saturn->configuration.cdblock.readSpeedFactor = speed;
        }
    }
}

// Automatic threaded audio: whether the sound chip runs faster on its own thread depends on the game and the device
// (on the T618 some games need the second fast core for the background renderer, others for the sound). When the game
// stays below full speed, the other mode is tried for a few seconds and kept if it is clearly faster. Called once per
// second by the emulation thread.
void ScspAutoTune(float fps) {
    auto &t = g->scspTune;
    if (!g->scspAuto || FastPath()) {
        t = {};
        return;
    }
    auto &threaded = g->saturn->configuration.audio.threadedSCSP;
    const float target = g->palMode.load(std::memory_order_relaxed) ? 50.0f : 60.0f;
    const auto now = std::chrono::steady_clock::now();
    switch (t.phase) {
    case 0: // watching
        if (now < t.cooldownUntil) {
            return;
        }
        if (fps < target * 0.95f) {
            t.sum += fps;
            if (++t.secs >= 3) {
                t.baseFps = t.sum / t.secs;
                threaded = !threaded.Get();
                g->pinInFrames = 3;
                t.phase = 1;
                t.secs = 0;
                t.sum = 0;
                LOGI("Auto threaded audio: %.1f fps, trying threaded audio %s", t.baseFps, threaded.Get() ? "on" : "off");
            }
        } else {
            t.secs = 0;
            t.sum = 0;
        }
        break;
    case 1: // settling after the switch (threads created and pinned)
        if (++t.secs >= 2) {
            t.phase = 2;
            t.secs = 0;
        }
        break;
    case 2: // measuring
        t.sum += fps;
        if (++t.secs >= 3) {
            const float trial = t.sum / t.secs;
            const bool better = trial >= t.baseFps + 1.5f;
            if (!better) {
                threaded = !threaded.Get(); // not better: back to the previous mode
                g->pinInFrames = 3;
            }
            LOGI("Auto threaded audio: %.1f fps vs %.1f, keeping threaded audio %s", trial, t.baseFps,
                 threaded.Get() ? "on" : "off");
            t = {};
            // The current mode proved the better one: don't try again for a long while (each trial costs a few slow
            // seconds). After a switch, check again sooner in case a later scene prefers the other mode.
            t.cooldownUntil = now + std::chrono::seconds(better ? 60 : 600);
        }
        break;
    }
}

// Total RunFrame time of the profiled frames (the parts are added up by the core)
std::atomic<uint64_t> g_profileFrameTicks{0};
// Per second, for the performance log: the slowest frame, frames over the time budget, the emulation thread's id
std::atomic<int64_t> g_worstFrameUs{0};
std::atomic<uint32_t> g_slowFrames{0};
// Emulation time of rendered and skipped frames, apart (emulation thread only): with "Hi-res: one field" or frame skip
// the two alternate and the average hides that the rendered ones are over budget
uint64_t g_renderedNs = 0, g_skippedNs = 0;
uint32_t g_renderedFrames = 0, g_skippedFrames = 0;
std::atomic<pid_t> g_emuTid{0};
// The app's options in use (set by the app, key=value;...), for the performance log
std::mutex g_logSettingsMutex;
std::string g_logSettings;

// Performance log for device tests: one line per second while a game runs, with the settings that affect speed.
// Android/media/<applicationId>/perf-log.csv (readable over USB; tools/gbsat.ps1 PerfLog), restarted when over 2 MB.
// GB_APP_ID comes from gb_native_add(... APP_ID ...) in CMakeLists.txt, so the path follows the applicationId.
#ifndef GB_APP_ID
#error "GB_APP_ID is defined by gb_native_add(gbsat APP_ID ...)"
#endif
void WritePerfLog(float fps, float presentedFps, float vdp1CommandsPerFrame) {
    if (auto *jit = g->saturn->GetSH2Jit(); jit != nullptr && jit->mapFile != nullptr) {
        std::fflush(jit->mapFile);
    }
    static const char *kPath = "/sdcard/Android/media/" GB_APP_ID "/perf-log.csv";
    std::string title;
    {
        std::lock_guard lk(g->winMutex);
        if (g->discHeader.size() >= 0x60 + 16) {
            title = g->discHeader.substr(0x60, std::min<size_t>(64, g->discHeader.size() - 0x60));
        }
    }
    title.erase(title.find_last_not_of(" \0") + 1);
    for (char &c : title) {
        if (c == ',' || c == '"' || (unsigned char)c < 32) c = ' ';
    }
    std::error_code ec;
    const auto size = std::filesystem::file_size(kPath, ec);
    bool fresh = ec || size > 2u * 1024 * 1024;
    if (!fresh) { // a log from an older version (fewer columns) starts over
        char head[4096] = {}; // the header is long (it was cut at 512 and the log restarted every second)
        if (FILE *h = fopen(kPath, "r")) {
            fgets(head, sizeof(head), h);
            fclose(h);
        }
        fresh = strstr(head, "cd_speed_now") == nullptr;
    }
    FILE *f = fopen(kPath, fresh ? "w" : "a");
    if (!f) {
        return;
    }
    if (fresh) {
        fprintf(f, "time,game,renderer,scale,threaded_vdp,threaded_scsp,frameskip,fast_forward,emu_fps,shown_fps,"
                   "emu_ms_per_frame,wait_sound_ms,wait_vdp1_ms,wait_vdp2_ms,"
                   "msh2_ms,ssh2_ms,scu_ms,vdp_ms,sched_ms,other_ms,"
                   "worst_frame_ms,slow_frames,vdp1_cmds_per_frame,resolution,sh2_cache,audio_underruns,audio_xruns,"
                   "emu_core,emu_core_mhz,emu_cpu_pct,vdp1_cpu_pct,vdp2_cpu_pct,scsp_cpu_pct,other_cpu_pct,busiest_other,"
                   "thermal_status,thermal_headroom,device,"
                   "msh2_loop_pct,ssh2_loop_pct,msh2_hot_loop,ssh2_hot_loop,msh2_skip_pct,ssh2_skip_pct,"
                   "audio_burst,audio_target,audio_min_fill,msh2_hot_ops,ssh2_hot_ops,msh2_idle_fail,ssh2_idle_fail,"
                   "sched_vdp_ms,sched_sound_ms,sched_cd_ms,sched_other_ms,rendered_frame_ms,skipped_frame_ms,"
                   "wait_vdp2_frame_ms,wait_vdp2_copy_ms,auto_skip_pct,cd_speed_now,settings\n");
    }
    const int mode = g->rendererMode;
    const char *renderer = mode == 4 ? "GPU (Vulkan)" : mode == 1 ? "CPU (OpenGL)" : "CPU (plain)";
    const auto &cfg = g->saturn->configuration;
    char ts[32];
    const time_t now = time(nullptr);
    strftime(ts, sizeof(ts), "%Y-%m-%d %H:%M:%S", localtime(&now));
        // Time the emulation thread waited per frame for the sound, VDP1 and VDP2 threads
    double waitMs[util::waitstats::Count];
    for (int i = 0; i < util::waitstats::Count; ++i) {
        waitMs[i] = util::waitstats::g_waitNs[i].exchange(0, std::memory_order_relaxed) / 1e6 / std::max(1.0f, fps);
    }
    // Where the emulation thread spent a frame (average of the profiled frames): master and slave SH-2, SCU, VDP (with
    // VDP2 drawing when it is not threaded), the scheduler (sound and CD when they run on this thread) and the rest
    namespace ws = util::waitstats;
    const uint32_t profFrames = ws::g_profiledFrames.exchange(0, std::memory_order_relaxed);
    const double tickMs = 1000.0 / ws::TicksPerSecond() / std::max<uint32_t>(1, profFrames);
    double partMs[ws::PartCount];
    double partSum = 0;
    for (int i = 0; i < ws::PartCount; ++i) {
        partMs[i] = ws::g_partTicks[i].exchange(0, std::memory_order_relaxed) * tickMs;
        partSum += partMs[i];
    }
    const double frameMs = g_profileFrameTicks.exchange(0, std::memory_order_relaxed) * tickMs;
    const double otherMs = profFrames ? std::max(0.0, frameMs - partSum) : 0.0;
    // The scheduler part split by event kind (VDP phases, sound, CD, the rest)
    double schedMs[ws::SchedCount];
    for (int i = 0; i < ws::SchedCount; ++i) {
        schedMs[i] = ws::g_schedTicks[i] * tickMs;
        ws::g_schedTicks[i] = 0;
    }
    // Average emulation time of rendered and skipped frames over the last second (0 when there were none)
    const double renderedMs = g_renderedFrames ? g_renderedNs / 1e6 / g_renderedFrames : 0.0;
    const double skippedMs = g_skippedFrames ? g_skippedNs / 1e6 / g_skippedFrames : 0.0;
    fprintf(f, "%s,%s,%s,%u,%d,%d,%d,%d,%.1f,%.1f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f", ts, title.c_str(), renderer,
            mode == 4 ? g->resScale : 1u, cfg.swRenderer.threadedVDP1.Get() ? 1 : 0,
            cfg.audio.threadedSCSP.Get() ? 1 : 0, g->frameSkip.load() ? 1 : 0, g->fastForward.load() ? 1 : 0, fps,
            presentedFps, g->frameTimeUs.load() / 1000.0,
            waitMs[util::waitstats::SCSP], waitMs[util::waitstats::VDP1], waitMs[util::waitstats::VDP2],
            partMs[ws::PartMSH2], partMs[ws::PartSSH2], partMs[ws::PartSCU], partMs[ws::PartVDP], partMs[ws::PartSched], otherMs);

    // CPU use per thread (percent of one core) and where the emulation thread runs: the emulation thread, the VDP1/VDP2
    // render threads, the sound thread, everything else together and the busiest other thread (e.g. the GPU driver)
    const pid_t emuTid = g_emuTid.load(std::memory_order_relaxed);
    double emuPct = 0, vdp1Pct = 0, vdp2Pct = 0, scspPct = 0, otherPct = 0, busiestPct = 0;
    int emuCore = -1;
    std::string busiest;
    const perfprobe::Snapshot probe = perfprobe::Latest(); // sampled by a background thread
    for (const auto &t : probe.threads) {
        if (t.tid == emuTid) {
            emuPct = t.cpuPct;
            emuCore = t.core;
        } else if (t.name.rfind("VDP1", 0) == 0) {
            vdp1Pct += t.cpuPct;
        } else if (t.name.rfind("VDP2", 0) == 0 || t.name.rfind("VDP deinterlace", 0) == 0) {
            vdp2Pct += t.cpuPct;
        } else if (t.name.rfind("SCSP", 0) == 0) {
            scspPct += t.cpuPct;
        } else {
            otherPct += t.cpuPct;
            if (t.cpuPct > busiestPct) {
                busiestPct = t.cpuPct;
                busiest = t.name;
            }
        }
    }
    for (char &c : busiest) {
        if (c == ',' || c == '"' || (unsigned char)c < 32) c = ' ';
    }
    int32_t xruns = -1;
    {
        std::lock_guard lk(g->audioMutex);
        if (g->stream) xruns = AAudioStream_getXRunCount(g->stream);
    }
    static int32_t lastXruns = 0;
    const int32_t xrunDelta = xruns >= 0 ? std::max(0, xruns - lastXruns) : -1;
    if (xruns >= 0) lastXruns = xruns;
    static const std::string device = [] {
        std::string d = perfprobe::DeviceName();
        for (char &c : d) {
            if (c == ',' || c == '"') c = ' ';
        }
        return d;
    }();
    // Share of each SH-2's interpreted time spent in tight loops (polling or delay loops), and its hottest loop as
    // address:percent (the candidates for idle-loop skipping)
    double loopPct[2], skipPct[2];
    std::string hotOps[2];
    char hot[2][32];
    for (int c = 0; c < 2; ++c) {
        const uint64_t exec = ws::g_shExec[c];
        // Share of the CPU's emulated cycles that idle-loop skipping didn't have to interpret
        skipPct[c] = exec + ws::g_shSkip[c] ? 100.0 * ws::g_shSkip[c] / (exec + ws::g_shSkip[c]) : 0.0;
        ws::g_shSkip[c] = 0;
        loopPct[c] = exec ? 100.0 * ws::g_shLoop[c] / exec : 0.0;
        int best = -1;
        for (int i = 0; i < 64; ++i) {
            if (ws::g_hotCyc[c][i] && (best < 0 || ws::g_hotCyc[c][i] > ws::g_hotCyc[c][best])) best = i;
        }
        hotOps[c].clear();
        if (best >= 0 && exec) {
            snprintf(hot[c], sizeof(hot[c]), "%08X:%.0f", ws::g_hotPc[c][best], 100.0 * ws::g_hotCyc[c][best] / exec);
            // The loop's first 6 instructions, to see why it isn't skipped (stores, counters, memory with side effects)
            for (uint32_t i = 0; i < 6; ++i) {
                char op[8];
                snprintf(op, sizeof(op), "%s%04X", i ? " " : "", g->saturn->mainBus.Peek<uint16_t>(ws::g_hotPc[c][best] + i * 2));
                hotOps[c] += op;
            }
        } else {
            snprintf(hot[c], sizeof(hot[c]), "-");
        }
        ws::g_shExec[c] = ws::g_shLoop[c] = 0;
        for (int i = 0; i < 64; ++i) {
            ws::g_hotPc[c][i] = 0;
            ws::g_hotCyc[c][i] = 0;
        }
    }
    // Audio buffer: frames per callback, the level the pacing keeps, the lowest level seen this second (-1: none)
    const uint32_t minFillRaw = g->ring.minFill.exchange(UINT32_MAX, std::memory_order_relaxed);
    const int minFill = minFillRaw == UINT32_MAX ? -1 : (int)minFillRaw;
    // Why each CPU's last analyzed loop wasn't idle: address:instruction[@memory read]
    char fail[2][48];
    for (int c = 0; c < 2; ++c) {
        const auto &cpu = c == 0 ? g->saturn->masterSH2 : g->saturn->slaveSH2;
        if (cpu.IdleFailAddr() && cpu.IdleFailOp() != 0xFFFE) {
            snprintf(fail[c], sizeof(fail[c]), "%08X:%04X@%08X", cpu.IdleFailPc(), cpu.IdleFailOp(), cpu.IdleFailAddr());
        } else if (cpu.IdleFailOp() == 0xFFFE) {
            snprintf(fail[c], sizeof(fail[c]), "%08X:carried R%04X", cpu.IdleFailPc(), cpu.IdleFailAddr());
        } else {
            snprintf(fail[c], sizeof(fail[c]), "%08X:%04X", cpu.IdleFailPc(), cpu.IdleFailOp());
        }
    }
    std::string settings;
    {
        std::lock_guard lk(g_logSettingsMutex);
        settings = g_logSettings;
    }
    if (!util::waitstats::g_sh2Chained.load(std::memory_order_relaxed) ||
        !util::waitstats::g_vdp2TwoFB.load(std::memory_order_relaxed) ||
        !util::waitstats::g_vdpBatch.load(std::memory_order_relaxed)) {
        settings += ";diag=";
        settings += util::waitstats::g_sh2Chained.load(std::memory_order_relaxed) ? "" : "sh2_plain ";
        settings += util::waitstats::g_vdp2TwoFB.load(std::memory_order_relaxed) ? "" : "vdp2_one_fb ";
        settings += util::waitstats::g_vdpBatch.load(std::memory_order_relaxed) ? "" : "vdp_nobatch";
    }
    settings += ";sync_step=" + std::to_string(g->saturn->GetSH2SyncStep()); // in use (game database may limit it)
    if (g->fsUseless) {
        settings += ";frameskip_paused=useless"; // frame skip on, but held off: skipped frames weren't faster
    }
    settings += ";frameskip_state=";
    settings += !g->frameSkip.load(std::memory_order_relaxed) ? "off" :
                g->fsUseless ? "paused" : g->skipActive ? "active" : "waiting";
    settings += ";frameskip_history=" + std::to_string(g->fsHistoryCount);
    settings += ";frameskip_cadence=" + std::to_string(g->minRenderRun);
    settings += ";frameskip_vdp1_cmds=" + std::to_string((uint32_t)std::max(0.0f, vdp1CommandsPerFrame));
    fprintf(f, ",%.2f,%u,%.0f,%ux%u,%d,%u,%d,%d,%d,%.0f,%.0f,%.0f,%.0f,%.0f,%s %.0f,%d,%.2f,%s,%.0f,%.0f,%s,%s,%.0f,%.0f,%u,%u,%d,%s,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.0f,%u,%s\n",
            g_worstFrameUs.exchange(0, std::memory_order_relaxed) / 1000.0, g_slowFrames.exchange(0, std::memory_order_relaxed),
            vdp1CommandsPerFrame,
            g->videoW.load(std::memory_order_relaxed), g->videoH.load(std::memory_order_relaxed),
            cfg.system.emulateSH2Cache.Get() ? 1 : 0, g->ring.underruns.exchange(0, std::memory_order_relaxed), xrunDelta,
            emuCore, emuCore >= 0 && emuCore < (int)probe.coreMHz.size() ? probe.coreMHz[emuCore] : -1, emuPct, vdp1Pct, vdp2Pct, scspPct, otherPct, busiest.c_str(), busiestPct,
            probe.thermal, probe.headroom, device.c_str(), loopPct[0], loopPct[1], hot[0], hot[1], skipPct[0], skipPct[1], g->ring.burst.load(std::memory_order_relaxed),
            g->targetFill.load(std::memory_order_relaxed), minFill, hotOps[0].c_str(), hotOps[1].c_str(), fail[0], fail[1],
            schedMs[ws::SchedVDP], schedMs[ws::SchedSCSP], schedMs[ws::SchedCD], schedMs[ws::SchedOther], renderedMs, skippedMs,
            ws::g_vdp2FrameWaitNs.exchange(0, std::memory_order_relaxed) / 1e6 / std::max(1.0f, fps),
            ws::g_vdp2CopyWaitNs.exchange(0, std::memory_order_relaxed) / 1e6 / std::max(1.0f, fps),
            g->skip.TakeActiveFrames() * 100.0 / std::max(1.0f, fps), (unsigned)g->cdSpeedApplied, settings.c_str());
    g_renderedNs = g_skippedNs = 0;
    g_renderedFrames = g_skippedFrames = 0;
    fclose(f);
}

void EmuLoop() {
    g_emuTid.store(gettid(), std::memory_order_relaxed);
    cputune::PinEmulationThreads();
    auto lastPin = steady_clock::now();
    auto nextFrame = steady_clock::now();
    auto fpsWindowStart = steady_clock::now();
    int fpsFrames = 0;
    const auto resetFrameSkipHistory = [] {
        g->fsHistory = {};
        g->fsHistoryCount = g->fsHistoryNext = 0;
        g->fsPoorWindows = 0;
    };

    while (g->running.load()) {
        DrainCommands();

        if (g->paused.load()) {
            // The last frame drawn may still be waiting for the render thread: show it before stopping
            if (auto *sw = g->saturn->VDP.GetRendererAs<ymir::vdp::VDPRendererType::Software>()) {
                sw->FlushPendingFrame();
            }
            std::unique_lock lk(g->cmdMutex);
            g->cmdCv.wait_for(lk, milliseconds(20), [] { return !g->commands.empty() || !g->paused.load() || !g->running.load(); });
            lk.unlock();
            ra::Idle();
            nextFrame = steady_clock::now();
            fpsWindowStart = nextFrame;
            fpsFrames = 0;
            continue;
        }

        {
            // Automatic frame skip: when the previous frame finished late, skip rendering this one (at most 2 in a row,
            // so the picture keeps updating); at full speed every frame is rendered
            // Hi-res/interlaced screens (704x480, used by many menus and title screens) cost up to 4x the rendering
            // work: there up to 4 frames in a row may be skipped, since such screens are mostly static
            const bool hires = g->videoW.load(std::memory_order_relaxed) >= 560 || g->videoH.load(std::memory_order_relaxed) >= 400;
            const bool softwareRenderer =
                g->saturn->VDP.GetRendererAs<ymir::vdp::VDPRendererType::Software>() != nullptr;
            const auto t = steady_clock::now();
            bool skip;
            if (FastPath()) {
                // Fast forward: render about 60 frames per second of real time and skip the rest, so heavy games
                // spend the time emulating instead of drawing frames nobody can see
                // (at most ~30 per second, and at least every other frame is skipped even when the game can't reach
                // full speed: rendering every frame made fast forward slower than normal play with frame skip)
                // Fast forward: show ~20 frames per second and never more than 1 of every 3 frames, so heavy (and hi-res)
                // games spend the time emulating
                skip = (t - g->lastRendered < milliseconds(50) || g->skipRun < 2) && g->skipRun < 12;
            } else {
                // On the software renderer, start earlier (below 96%) because skipped frames save CPU work. GPU
                // rendering keeps the 92% threshold; skipping its frames does not reduce SH-2 emulation work.
                // Skipping 2 of 3 frames to
                // recover a few percent made the picture much choppier than running slightly slow. One frame at a time
                // (two on hi-res screens), with minRenderRun frames shown in between: skipping whenever behind showed
                // only every other frame (~30 fps on screen while emulating 55-58, performance log); the spacing adapts
                // every second so only as many frames as needed are skipped.
                const float target = g->palMode.load(std::memory_order_relaxed) ? 50.0f : 60.0f;
                const float activationRatio = softwareRenderer ? 0.96f : 0.92f;
                const bool cpuRendererBehind = softwareRenderer && g->fps.load(std::memory_order_relaxed) < target * activationRatio;
                // Hysteresis: skipping starts when clearly behind and stays on until the game keeps up without help
                // (not behind for a whole second); deciding from the last second's fps alone made it switch on and
                // off every second (60 fps while skipping -> stop -> 33 fps -> skip again).
                if (g->fps.load(std::memory_order_relaxed) < target * activationRatio && (g->behind || cpuRendererBehind)) {
                    g->skipActive = true;
                }
                g->notBehindRun = (g->behind || cpuRendererBehind) ? 0 : g->notBehindRun + 1;
                if (g->notBehindRun >= 60) {
                    g->skipActive = false;
                }
                const int unit = hires ? 2 : 1;
                skip = g->frameSkip.load() && g->skipActive && !g->fsUseless && (g->behind || cpuRendererBehind) &&
                       (g->skipRun > 0 ? g->skipRun < unit : g->renderRun >= g->minRenderRun * unit);
            }
            if (!skip) {
                g->lastRendered = t;
            }
            // Interlaced (double-density) screens draw one field per frame: skipping single frames left the other
            // field stale, so moving objects showed a trail. Skip or render both fields of a pair together.
            // The pair follows the real field order (TVSTAT.ODD), so both fields of a rendered frame are fresh.
            // Only when the field really alternates: with the bit stuck (Batsugun at 640x480) "the second field follows
            // the first" kept a skip forever and the picture froze.
            if (g->videoH.load(std::memory_order_relaxed) >= 400) {
                const bool secondField = (g->saturn->mainBus.Peek<uint16_t>(0x25F80004) >> 1) & 1;
                if (secondField && g->prevFirstField) {
                    skip = g->pairSkip;
                }
                g->pairSkip = skip;
                g->prevFirstField = !secondField;
                // Option "Hi-res: one field": the second field is not rendered (the picture keeps the first one), halving the
                // VDP2 work of 704x448 screens. Only when the field bit alternates (a stuck bit would freeze the picture).
                // The bus reads TVSTAT.ODD inverted in interlaced modes, so "secondField" here is the even field inside the
                // core; the renderer copies the rendered field's lines onto the skipped field's rows whichever it is.
                const bool alternating = secondField != g->lastField;
                g->lastField = secondField;
                // Software renderer only: the Vulkan renderer draws the whole frame on the GPU (no VDP2 CPU work to
                // save) and delivers frames one late, so skipping fields there froze the picture and the skipped frames
                // took ~40 ms (VF2 on the RG Rotate, performance log)
                if (g->oneField.load(std::memory_order_relaxed) && alternating && secondField &&
                    g->saturn->VDP.GetRendererAs<ymir::vdp::VDPRendererType::Software>()) {
                    skip = true;
                }
            }
            g->skipRun = skip ? g->skipRun + 1 : 0;
            g->renderRun = skip ? 0 : g->renderRun + 1;
            g->saturn->VDP.SetSkipFrameRender(skip);
            g->skipPresent = skip;
        }
        const auto workStart = steady_clock::now();
        // Frame profile for the performance log: one frame in 7 is timed per component. An odd stride: one in 8 always
        // landed on the same field of interlaced screens, so with "Hi-res: one field" the profile showed only skipped or
        // only rendered frames (and switched between them after a resolution change)
        static uint32_t profileTick = 0;
        const bool profiled = (++profileTick % 7) == 0;
        const bool skippedFrame = g->skipPresent.load(std::memory_order_relaxed);
        uint64_t profStart = 0;
        if (profiled) {
            util::waitstats::g_profile.store(true, std::memory_order_relaxed);
            profStart = util::waitstats::Ticks();
        }
        g->saturn->RunFrame();
        if (profiled) {
            util::waitstats::g_profile.store(false, std::memory_order_relaxed);
            g_profileFrameTicks.fetch_add(util::waitstats::Ticks() - profStart, std::memory_order_relaxed);
            util::waitstats::g_profiledFrames.fetch_add(1, std::memory_order_relaxed);
        }
        g->palMode.store(g->saturn->GetVideoStandard() == ymir::core::config::sys::VideoStandard::PAL, std::memory_order_relaxed);
        UpdateAutoSkip();
        BootFrame();
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
        {
            const int64_t ns = duration_cast<nanoseconds>(now - workStart).count();
            cputune::ReportFrameWork(ns);
            // Slowest frame and frames over the time budget (the emulation alone can't keep full speed there)
            if (ns / 1000 > g_worstFrameUs.load(std::memory_order_relaxed)) g_worstFrameUs.store(ns / 1000, std::memory_order_relaxed);
            if (ns > (g->palMode.load(std::memory_order_relaxed) ? 20000000 : 16717000)) g_slowFrames.fetch_add(1, std::memory_order_relaxed);
            if (skippedFrame) {
                g_skippedNs += ns;
                ++g_skippedFrames;
                g->fsSkippedNs += ns;
                ++g->fsSkipped;
            } else {
                g_renderedNs += ns;
                ++g_renderedFrames;
                g->fsRenderedNs += ns;
                ++g->fsRendered;
            }
            const int64_t prev = g->frameTimeUs.load(std::memory_order_relaxed);
            g->frameTimeUs.store(prev == 0 ? ns / 1000 : (prev * 7 + ns / 1000) / 8, std::memory_order_relaxed);
        }
        // Threads (re)created by option changes inherit the emulation thread's single-core affinity and halve the speed
        // until they are moved: pin a few frames after such a change instead of waiting for the periodic pass
        if (g->pinInFrames > 0 && --g->pinInFrames == 0) {
            lastPin = now;
            cputune::PinEmulationThreads();
        }
        if (now - lastPin >= seconds(3)) { // render threads are recreated when the renderer changes
            lastPin = now;
            cputune::PinEmulationThreads();
        }
        if (now - fpsWindowStart >= seconds(1)) {
            ReadDiagnosticProps();
            const float secs = duration<float>(now - fpsWindowStart).count();
            g->fps = fpsFrames / secs;
            const uint32_t vdp1Commands = util::waitstats::g_vdp1Commands.exchange(0, std::memory_order_relaxed);
            const float vdp1CommandsPerFrame = vdp1Commands / std::max(1.0f, g->fps.load(std::memory_order_relaxed));
            WritePerfLog(g->fps, g->presented.exchange(0, std::memory_order_relaxed) / secs, vdp1CommandsPerFrame);

            const bool softwareRenderer =
                g->saturn->VDP.GetRendererAs<ymir::vdp::VDPRendererType::Software>() != nullptr;
            const int rendererMode = g->rendererMode.load(std::memory_order_relaxed);
            const uint32_t sceneWidth = g->videoW.load(std::memory_order_relaxed);
            const uint32_t sceneHeight = g->videoH.load(std::memory_order_relaxed);
            bool sceneChanged = g->fsSceneWidth != 0 &&
                                (sceneWidth != g->fsSceneWidth || sceneHeight != g->fsSceneHeight);
            sceneChanged |= g->fsSceneRendererMode >= 0 && rendererMode != g->fsSceneRendererMode;
            const float previousCommands = g->fsSceneVdp1Commands;
            if (previousCommands >= 0.0f) {
                const float lower = std::min(previousCommands, vdp1CommandsPerFrame);
                const float upper = std::max(previousCommands, vdp1CommandsPerFrame);
                sceneChanged |= (lower >= 12.0f && upper > lower * 1.4f) || (lower < 4.0f && upper >= 20.0f);
            }
            if (sceneChanged) {
                resetFrameSkipHistory();
                g->fsUseless = false;
                g->fsHold = 0;
                LOGI("Frame skip retry: scene load changed (VDP1 %.0f -> %.0f commands/frame)", previousCommands,
                     vdp1CommandsPerFrame);
            }
            g->fsSceneVdp1Commands = vdp1CommandsPerFrame;
            g->fsSceneWidth = sceneWidth;
            g->fsSceneHeight = sceneHeight;
            g->fsSceneRendererMode = rendererMode;

            if (!g->frameSkip.load(std::memory_order_relaxed)) {
                resetFrameSkipHistory();
                g->fsUseless = false;
                g->fsHold = 0;
            }

            // A single second can straddle a scene transition. Use three one-second samples from a similar VDP load,
            // then require two consecutive poor rolling windows before suppressing skips.
            if (g->fsHold > 0) {
                if (--g->fsHold == 0) {
                    g->fsUseless = false;
                    resetFrameSkipHistory();
                }
            } else if (g->frameSkip.load(std::memory_order_relaxed) && g->skipActive && !sceneChanged &&
                       g->fsSkipped >= 5 && g->fsRendered >= 5) {
                g->fsHistory[g->fsHistoryNext] = {static_cast<uint64_t>(g->fsRenderedNs),
                                                  static_cast<uint64_t>(g->fsSkippedNs),
                                                  static_cast<uint32_t>(g->fsRendered),
                                                  static_cast<uint32_t>(g->fsSkipped)};
                g->fsHistoryNext = (g->fsHistoryNext + 1) % g->fsHistory.size();
                g->fsHistoryCount = std::min(g->fsHistoryCount + 1, g->fsHistory.size());

                uint64_t renderedNs = 0, skippedNs = 0;
                uint32_t renderedFrames = 0, skippedFrames = 0;
                for (const auto &sample : g->fsHistory) {
                    renderedNs += sample.renderedNs;
                    skippedNs += sample.skippedNs;
                    renderedFrames += sample.renderedFrames;
                    skippedFrames += sample.skippedFrames;
                }
                if (g->fsHistoryCount == g->fsHistory.size()) {
                    const double skippedAvg = double(skippedNs) / std::max(1u, skippedFrames);
                    const double renderedAvg = double(renderedNs) / std::max(1u, renderedFrames);
                    // CPU rendering only needs to show a repeatable non-regression; the GPU path still needs a 10% win.
                    const double requiredRatio = softwareRenderer ? 1.0 : 0.9;
                    if (skippedAvg > renderedAvg * requiredRatio) {
                        ++g->fsPoorWindows;
                    } else {
                        g->fsPoorWindows = 0;
                        if (softwareRenderer && g->fps <
                                (g->palMode.load(std::memory_order_relaxed) ? 50.0f : 60.0f) * 0.985f &&
                            skippedAvg < renderedAvg * 0.98) {
                            g->minRenderRun = 1;
                        }
                    }
                }
                if (g->fsHistoryCount == g->fsHistory.size() && g->fsPoorWindows >= 2) {
                    g->fsUseless = true;
                    g->fsHold = softwareRenderer ? 5 : 20;
                    LOGI("Frame skip off for %d s after similar-scene samples", g->fsHold);
                    resetFrameSkipHistory();
                }
            }
            g->fsRenderedNs = g->fsSkippedNs = 0;
            g->fsRendered = g->fsSkipped = 0;
            // Frame skip spacing: skip more often while still clearly slow, less often once at full speed
            if (g->skipActive) {
                const float target = g->palMode.load(std::memory_order_relaxed) ? 50.0f : 60.0f;
                if (g->fps < target * 0.95f) {
                    g->minRenderRun = std::max(1, g->minRenderRun - 1);
                } else if (g->fps >= target * 0.985f) {
                    g->minRenderRun = std::min(4, g->minRenderRun + 1);
                }
            } else {
                g->minRenderRun = 2;
            }
            ScspAutoTune(g->fps);
            fpsFrames = 0;
            fpsWindowStart = now;
        }

        if (FastPath()) {
            // Default and mute: drop the excess so audio stays in sync when fast-forward ends.
            // Speed up: the samples were already decimated; keep the buffer short so it follows the game.
            // Skip Engine: muted (nothing reaches the buffer)
            if (g->ffAudio.load() != 1 || g->skip.Active() || g->booting.load() ||
                g->ring.Fill() > g->targetFill.load() * 2) {
                g->ring.Clear();
            }
            nextFrame = steady_clock::now();
            continue;
        }

        if (g->audioOk.load()) {
            // Behind when the audio buffer drains below half of its target (the game can't keep up)
            g->behind = g->ring.Fill() < g->targetFill.load() / 2;
            // Audio-driven pacing: keep ~46 ms buffered.
            while (g->running.load() && !g->paused.load() && g->ring.Fill() > g->targetFill.load()) {
                std::this_thread::sleep_for(microseconds(500));
            }
        } else {
            const bool pal = g->saturn->GetVideoStandard() == ymir::core::config::sys::VideoStandard::PAL;
            nextFrame += nanoseconds((int64_t)(1e9 / (pal ? 50.0 : 59.82)));
            const auto t = steady_clock::now();
            g->behind = t > nextFrame + milliseconds(2);
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
    g->dataDir = std::filesystem::path(JStr(env, backupRamPath)).parent_path().parent_path().string();

    g->saturn->SCSP.SetSampleCallback({nullptr, [](int16_t l, int16_t r, void *) {
                                           // Skip Engine: how loud the game is (a load or transition is silent); muted
                                           // while it runs at maximum speed
                                           g->skip.OnSample(l, r);
                                           if ((g->skip.Active() || g->booting.load(std::memory_order_relaxed)) &&
                                               !g->fastForward.load(std::memory_order_relaxed)) {
                                               return;
                                           }
                                           if (g->fastForward.load(std::memory_order_relaxed)) {
                const int mode = g->ffAudio.load(std::memory_order_relaxed);
                if (mode == 2) {
                    return; // mute
                }
                if (mode == 1) {
                    // Speed up: keep 1 of every <speed> samples, so the audio plays faster (and higher) like the game
                    const float speed = std::max(1.0f, g->fps.load(std::memory_order_relaxed) / 60.0f);
                    g->ffDecimAcc += 1.0f / speed;
                    if (g->ffDecimAcc < 1.0f) {
                        return;
                    }
                    g->ffDecimAcc -= 1.0f;
                }
            }
            g->ring.Push(l, r);
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

    // Helper threads: the sound thread and the VDP2 render thread share the next fast core(s); the other VDP threads
    // (VDP1, deinterlacer) may use every core except the emulation thread's
    cputune::SetThreadRoles({"SCSP", "VDP2"}, {"VDP"});
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
        // Which BIOS (behavior can differ between versions and regions; the log tells devices apart)
        const auto hash = g->saturn->GetIPLHash();
        if (const auto *info = ymir::db::GetIPLROMInfo(hash)) {
            LOGI("BIOS: version %s (%04u-%02u-%02u), region %d", info->version, (unsigned)info->year,
                 (unsigned)info->month, (unsigned)info->day, (int)info->region);
        } else {
            char hex[33];
            for (int i = 0; i < 16; ++i) {
                snprintf(hex + i * 2, 3, "%02x", hash[i]);
            }
            LOGI("BIOS: unknown (hash %s)", hex);
        }
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
            {
                std::lock_guard lk(g->winMutex);
                g->discHeader = hdr;
            }
            // The loaded game's title in the log: tools/profile.bat names its reports after it (reading the performance
            // log from the shell could return a stale copy, so the previous game's name)
            std::string title = hdr.substr(0x60, 112);
            title.erase(title.find_last_not_of(' ') + 1);
            std::string code = hdr.substr(0x20, 10);
            code.erase(code.find_last_not_of(' ') + 1);
            LOGI("Game: %s (%s)", title.c_str(), code.c_str());
        }
    }
    // Cartridge from the game database (adapted from Mednafen): the RAM cartridge for the games that need one (1, 4 or
    // 6 MiB), the KOF95/Ultraman ROM carts when their dumps are in <data>/carts/, otherwise a 4 MiB backup memory
    // cartridge (saves/cartridge_backup.bin) for games that save to (or ask for) a save cartridge.
    std::string productCode;
    {
        std::lock_guard lk(g->winMutex);
        if (g->discHeader.size() >= 0x2A) {
            productCode = g->discHeader.substr(0x20, 10);
            productCode.erase(productCode.find_last_not_of(' ') + 1);
        }
    }
    RunSync([disc, raHash, productCode]() -> int {
        using ymir::db::Cartridge;
        const auto *info = ymir::db::GetGameInfo(productCode, ymir::XXH128Hash{});
        const Cartridge need = info ? info->GetCartridge() : Cartridge::None;
        int want = 4;
        std::string notice;
        if (need == Cartridge::DRAM32Mbit) {
            want = 2;
        } else if (need == Cartridge::DRAM8Mbit) {
            want = 1;
            notice = "1 MiB RAM cartridge inserted for this game";
        } else if (need == Cartridge::DRAM48Mbit) {
            want = 3;
        } else if (need == Cartridge::ROM_KOF95 || need == Cartridge::ROM_Ultraman) {
            const bool kof = need == Cartridge::ROM_KOF95;
            const auto path = std::filesystem::path(g->dataDir) / "carts" / (kof ? "kof95.bin" : "ultraman.bin");
            gb::DataInputFile in(path, std::ios::binary);
            std::vector<uint8_t> rom((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
            if (!rom.empty()) {
                g->saturn->InsertCartridge<ymir::cart::ROMCartridge>()->LoadROM(rom);
                g->cartType = 10;
                want = 10;
                notice = std::string(kof ? "KOF95" : "Ultraman") + " ROM cartridge inserted";
            } else {
                notice = std::string("This game needs the ") + (kof ? "King of Fighters '95" : "Ultraman") +
                         " ROM cartridge: add its dump in Settings → System → Cartridge ROMs";
            }
        }
        // Always reinserted: loading a save state may have swapped the cartridge
        if (want != 10) {
            switch (want) {
            case 1: g->saturn->InsertCartridge<ymir::cart::DRAM8MbitCartridge>(); break;
            case 3: g->saturn->InsertCartridge<ymir::cart::DRAM48MbitCartridge>(); break;
            case 4: {
                const auto path = std::filesystem::path(g->dataDir) / "saves" / "cartridge_backup.bin";
                ymir::bup::BackupMemory bm;
                std::error_code ec;
                bool ok = false;
                struct stat backupInfo;
                if (gb::DataStat(path.c_str(), &backupInfo) == 0) {
                    ok = bm.LoadFrom(path, false, ec) == ymir::bup::BackupMemoryImageLoadResult::Success && !ec;
                } else {
                    bm.CreateFrom(path, false, ec, ymir::bup::BackupMemorySize::_32Mbit);
                    ok = !ec;
                }
                if (ok) {
                    g->saturn->InsertCartridge<ymir::cart::BackupMemoryCartridge>(std::move(bm));
                } else {
                    LOGE("Backup memory cartridge unavailable (%s), using the 4 MiB RAM cartridge", ec.message().c_str());
                    g->saturn->InsertCartridge<ymir::cart::DRAM32MbitCartridge>();
                }
                break;
            }
            default: g->saturn->InsertCartridge<ymir::cart::DRAM32MbitCartridge>(); break;
            }
            g->cartType = want;
        }
        g->cartNotice = notice;
        g->scspTune = {};
        g->saturn->LoadDisc(std::move(*disc));
        g->saturn->AutodetectRegion();
        g->saturn->Reset(true);
        StartBoot();
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
            StartBoot();
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
// Skip Engine options (see UpdateAutoSkip)
JNI_FN(void, nativeSetAutoSkip)(JNIEnv *, jclass, jboolean loads, jboolean transitions, jboolean saves) {
    if (g) {
        g->skip.skipLoads = loads;
        g->skip.skipTransitions = transitions;
        g->skip.skipSaves = saves; // the emulation thread stops a running skip within a few frames
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
// SH-2 sync step (CPU sync option). A/B on the device: setprop debug.gbsat.sync_step N overrides it
JNI_FN(void, nativeSetSH2SyncStep)(JNIEnv *, jclass, jint cycles) {
    if (g) {
        char v[PROP_VALUE_MAX] = {};
        const int prop = __system_property_get("debug.gbsat.sync_step", v) > 0 ? atoi(v) : 0;
        const int step = prop > 0 ? prop : cycles;
        Post([step] { g->saturn->SetSH2SyncStep(step); });
    }
}
// SH-2 recompiler: 0 off (interpreter), 2 approximate timing, used only with 128-cycle sync (Saturn::SetSH2JitApproximate);
// 1 exact, for tests. debug.gbsat.jit overrides
JNI_FN(void, nativeSetSH2Jit)(JNIEnv *, jclass, jint mode) {
    if (g) {
        char v[PROP_VALUE_MAX] = {};
        const int m = __system_property_get("debug.gbsat.jit", v) > 0 && v[0] >= '0' && v[0] <= '2' ? v[0] - '0' : mode;
        Post([m] {
            g->saturn->SetSH2JitApproximate(m == 2);
            if (m == 1) {
                g->saturn->EnableSH2Jit(true);
            } else if (m == 0) {
                g->saturn->EnableSH2Jit(false);
            }
            // Diagnostics: debug.gbsat.jitmap=1 describes the compiled blocks (with a profile, tells what host code runs)
            if (auto *jit = g->saturn->GetSH2Jit(); jit != nullptr && jit->mapFile == nullptr) {
                char mv[PROP_VALUE_MAX] = {};
                if (__system_property_get("debug.gbsat.jitmap", mv) > 0 && mv[0] == '1') {
                    jit->mapFile = std::fopen("/sdcard/Android/media/" GB_APP_ID "/jitmap.txt", "w");
                    jit->Flush();
                }
            }
            __android_log_print(ANDROID_LOG_INFO, "GBSat", "SH-2 JIT mode %d (%s)", m, g->saturn->GetSH2Jit() ? "on" : "off");
        });
    }
}
// The data folder changed without a restart (first folder picked): reopen the backup memory file at the same path,
// which now leads into the picked folder
JNI_FN(void, nativeReopenBackup)(JNIEnv *env, jclass, jstring backupRamPath) {
    if (g) {
        const std::string bup = JStr(env, backupRamPath);
        Post([bup] {
            std::error_code ec;
            g->saturn->LoadInternalBackupMemoryImage(bup, false, ec);
            if (ec) __android_log_print(ANDROID_LOG_ERROR, "GBSat", "Reopen backup memory: %s", ec.message().c_str());
        });
    }
}
JNI_FN(void, nativeSetIdleSkip)(JNIEnv *, jclass, jboolean on) {
    if (g) {
        Post([on] {
            g->saturn->masterSH2.SetIdleLoopSkip(on);
            g->saturn->slaveSH2.SetIdleLoopSkip(on);
        });
    }
}
JNI_FN(void, nativeSetOneField)(JNIEnv *, jclass, jboolean on) {
    if (g) {
        g->oneField.store(on, std::memory_order_relaxed);
        util::waitstats::g_oneField.store(on, std::memory_order_relaxed);
    }
}
JNI_FN(void, nativeSetThreadedVdp)(JNIEnv *, jclass, jboolean on) {
    if (g) {
        Post([on] {
            g->saturn->configuration.swRenderer.threadedVDP1 = on;
            g->pinInFrames = 3;
            g->saturn->configuration.swRenderer.threadedVDP2 = on;
            g->saturn->configuration.swRenderer.threadedDeinterlacer = on;
        });
    }
}
// SCSP (sound) and its MC68EC000 CPU on their own thread, off the emulation thread that runs the two SH-2s
JNI_FN(void, nativeSetThreadedScsp)(JNIEnv *, jclass, jboolean on) {
    if (g) {
        Post([on] {
            g->saturn->configuration.audio.threadedSCSP = on;
            g->pinInFrames = 3;
        });
    }
}

// Threaded audio mode: 0 = automatic (the emulation thread tries both and keeps the faster one, see ScspAutoTune),
// 1 = on, 2 = off. In automatic mode the current state is kept (the app sets the starting state per game).
JNI_FN(void, nativeSetScspMode)(JNIEnv *, jclass, jint mode) {
    if (g) {
        Post([mode] {
            g->scspAuto = mode == 0;
            if (mode != 0) {
                g->saturn->configuration.audio.threadedSCSP = mode == 1;
                g->pinInFrames = 3;
            }
        });
    }
}

// Auto threaded audio: the starting mode is the one remembered for this game, so skip trials for a while
JNI_FN(void, nativeScspModeKnown)(JNIEnv *, jclass) {
    if (g) {
        Post([] { g->scspTune.cooldownUntil = std::chrono::steady_clock::now() + std::chrono::seconds(600); });
    }
}

JNI_FN(jboolean, nativeGlFailed)(JNIEnv *, jclass) {
    return glr::Failed();
}

JNI_FN(jboolean, nativeGetThreadedScsp)(JNIEnv *, jclass) {
    return g && g->saturn->configuration.audio.threadedSCSP.Get();
}

// The app's options in use, written in each performance log line (key=value pairs separated by ';')
JNI_FN(void, nativeSetLogSettings)(JNIEnv *env, jclass, jstring settings) {
    const char *s = env->GetStringUTFChars(settings, nullptr);
    std::string v = s ? s : "";
    if (s) env->ReleaseStringUTFChars(settings, s);
    for (char &c : v) {
        if (c == ',' || c == '"' || (unsigned char)c < 32) c = ' ';
    }
    std::lock_guard lk(g_logSettingsMutex);
    g_logSettings = std::move(v);
}

// Stats: [fps*100, videoW, videoH]
JNI_FN(jlongArray, nativeGetStats)(JNIEnv *env, jclass) {
    // [fps*100, output width, output height, PAL (0/1), internal resolution scale, emulation µs per frame,
    //  Skip Engine running at maximum speed (0/1)]
    jlong v[7] = {0, 320, 224, 0, 1, 0, 0};
    if (g) {
        v[0] = (jlong)(g->fps.load() * 100.0f);
        v[1] = g->videoW.load();
        v[2] = g->videoH.load();
        v[3] = g->palMode.load() ? 1 : 0;
        v[4] = g->lastScale.load();
        v[5] = g->frameTimeUs.load();
        v[6] = g->skip.Active() ? 1 : 0;
    }
    jlongArray arr = env->NewLongArray(7);
    env->SetLongArrayRegion(arr, 0, 7, v);
    return arr;
}

// Last frame as [width, height, ARGB pixels...] for save state thumbnails (null if no frame yet).
JNI_FN(jintArray, nativeGetLastFrame)(JNIEnv *env, jclass) {
    if (!g) {
        return nullptr;
    }
    std::lock_guard lk(g->lastFrameMutex);
    if (g->lastFrame.empty()) {
        return nullptr;
    }
    const size_t n = g->lastFrame.size();
    std::vector<jint> out(n + 2);
    out[0] = (jint)g->lastFrameW;
    out[1] = (jint)g->lastFrameH;
    for (size_t i = 0; i < n; ++i) {
        const uint32_t p = g->lastFrame[i]; // R, G, B in bytes 0..2
        out[i + 2] = (jint)(0xFF000000u | ((p & 0xFFu) << 16) | (p & 0xFF00u) | ((p >> 16) & 0xFFu));
    }
    jintArray arr = env->NewIntArray((jsize)out.size());
    env->SetIntArrayRegion(arr, 0, (jsize)out.size(), out.data());
    return arr;
}

// Automatic frame skipping (skips rendering only while running below full speed).
JNI_FN(void, nativeSetFrameSkip)(JNIEnv *, jclass, jboolean on) {
    if (g) {
        g->frameSkip = on;
        if (!on) {
            Post([] { g->saturn->VDP.SetSkipFrameRender(false); g->skipPresent = false; });
        }
    }
}

// Audio: volume 0..100 and fast-forward audio mode (0 default, 1 speed up, 2 mute).
JNI_FN(void, nativeSetAudio)(JNIEnv *, jclass, jint volume, jint ffMode) {
    if (g) {
        g->volume = std::clamp<jint>(volume, 0, 100) / 100.0f;
        g->ffAudio = std::clamp<jint>(ffMode, 0, 2);
    }
}

// CD read speed factor (2 = original drive speed, up to 200).
JNI_FN(void, nativeSetCdReadSpeed)(JNIEnv *, jclass, jint factor) {
    if (g) {
        const uint8_t f = static_cast<uint8_t>(std::clamp<jint>(factor, 2, 200));
        Post([f] {
            g->cdSpeedUser = g->cdSpeedApplied = f;
            g->saturn->configuration.cdblock.readSpeedFactor = f;
        });
    }
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
            gb::DataOutputFile out{p + ".ra", std::ios::binary | std::ios::trunc};
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
                       EndBoot("state loaded");
                       if (err.empty()) {
                           gb::DataInputFile in{p + ".ra", std::ios::binary};
                           std::vector<uint8_t> progress{std::istreambuf_iterator<char>(in), {}};
                           ra::DeserializeProgress(progress);
                       }
                       g->ring.Clear();
                       return err;
                   }));
}

// Deinterlacing of double-density interlaced screens (VF2, Sega Rally hi-res): without it the core weaves the
// fields, and moving objects show combs of two fields 1/60 s apart. Vulkan only: the CPU renderers show one field of
// each pair ("Hi-res: one field"), which has no combs, and deinterlacing there would double their VDP2 work.
// Runs on the emulation thread.
void ApplyEnhancements() {
    auto &vdp = g->saturn->VDP;
    const bool gpu = vdp.GetRendererAs<ymir::vdp::VDPRendererType::Vulkan>() != nullptr;
    const bool deinterlace = gpu && g->deinterlaceAllowed;
    vdp.ModifyEnhancements([deinterlace](ymir::vdp::config::Enhancements &e) { e.deinterlace = deinterlace; });
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
    ApplyEnhancements();
}

JNI_FN(void, nativeSetRenderer)(JNIEnv *, jclass, jint mode) {
    if (g) {
        // Vulkan GPU (4) emulates the VDPs on the GPU and shows the frames through the OpenGL ES presenter, which
        // provides the filters and shaders
        const bool newGl = (mode == 1 || mode == 4);
        const bool oldGl = g->glMode.load();
        if (oldGl && !newGl) {
            glr::Shutdown(); // switching to CPU presentation in game: release the GL surface
        } else if (!oldGl && newGl) {
            std::lock_guard lk(g->winMutex);
            if (g->window) {
                ANativeWindow_release(g->window);
                g->window = nullptr;
            }
            g->curW = g->curH = 0;
        }
        g->glMode = newGl;
        g->rendererMode = mode;
        g_crashRenderer = mode;
        const bool gpu = mode == 4;
        Post([gpu] { ApplyVdpRenderer(gpu); });
    }
}

// Internal resolution scale of the Vulkan GPU renderer (1..4). Recreates the renderer when it is active.
JNI_FN(void, nativeSetResolutionScale)(JNIEnv *, jclass, jint scale) {
    if (!g) {
        return;
    }
    const uint32_t s = static_cast<uint32_t>(std::clamp<jint>(scale, 1, 6));
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

// Deinterlacing of interlaced hi-res screens with the Vulkan renderer (the app allows it from device tier mid up)
JNI_FN(void, nativeSetDeinterlace)(JNIEnv *, jclass, jboolean allowed) {
    if (g) {
        Post([allowed] {
            g->deinterlaceAllowed = allowed;
            ApplyEnhancements();
        });
    }
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
// Message about the cartridge chosen for the last loaded game (empty if nothing to say); cleared when read.
JNI_FN(jstring, nativeTakeCartNotice)(JNIEnv *env, jclass) {
    std::string n;
    if (g) {
        RunSync([&n]() -> int { n.swap(g->cartNotice); return 0; });
    }
    return n.empty() ? nullptr : env->NewStringUTF(n.c_str());
}

JNI_FN(jstring, nativeGetGpuName)(JNIEnv *env, jclass) {
    return env->NewStringUTF(glr::GpuName().c_str());
}

// GPU usage for the overlay, in percent since the last call: the GPU time of this app's work (Vulkan renderer command
// buffers + OpenGL presenter draws, measured with timestamp/timer queries) over the elapsed time, like DuckStation and
// AetherSX2. -1 when nothing is measured (plain CPU output, or the driver has no timer queries).
JNI_FN(jfloat, nativeGetGpuLoad)(JNIEnv *, jclass) {
    static auto last = std::chrono::steady_clock::now();
    const auto now = std::chrono::steady_clock::now();
    const double elapsedNs = std::chrono::duration<double, std::nano>(now - last).count();
    last = now;
    const uint64_t ns = glr::TakeGpuNs() + util::waitstats::g_gpuBusyNs.exchange(0, std::memory_order_relaxed);
    if (!glr::GpuTimed() && !util::waitstats::g_gpuTimed.load(std::memory_order_relaxed)) {
        return -1.0f;
    }
    if (elapsedNs <= 0.0) {
        return 0.0f;
    }
    return (jfloat)std::min(100.0, ns / elapsedNs * 100.0);
}

// RetroAchievements hash of a disc image that is not running (same rule as nativeLoadDisc), for the game's achievement list.
JNI_FN(jstring, nativeRaHashFile)(JNIEnv *env, jclass, jstring path) {
    auto disc = std::make_shared<ymir::media::Disc>();
    if (!ymir::media::LoadDisc(JStr(env, path), *disc, false, [](ymir::media::MessageType, std::string) {}) || disc->sessions.empty()) {
        return nullptr;
    }
    const auto &session = disc->sessions.front();
    const auto &track = session.tracks[session.firstTrackIndex];
    std::array<uint8_t, 2048> sector{};
    if (!track.ReadSectorUserData(track.startFrameAddress, sector)) {
        return nullptr;
    }
    md5_state_t md5;
    md5_byte_t digest[16];
    md5_init(&md5);
    md5_append(&md5, sector.data(), 512);
    md5_finish(&md5, digest);
    char hex[33];
    for (int i = 0; i < 16; ++i) snprintf(hex + i * 2, 3, "%02x", digest[i]);
    return env->NewStringUTF(hex);
}

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

JNI_FN(void, nativeSetDisplayOptions)(JNIEnv *, jclass, jint filter, jint shader) {
    glr::SetOptions(filter, shader);
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
    if (g) {
        g->fastBoot = on;
    }
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
