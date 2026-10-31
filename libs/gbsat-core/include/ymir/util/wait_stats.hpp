#pragma once

// Diagnostics: time the emulation thread spends waiting for other threads (sound, VDP1 and VDP2 render threads).
// Read and reset by the frontend (performance log).

#include <atomic>
#include <chrono>
#include <cstddef>
#include <cstdint>

namespace util::waitstats {

enum GlesUploadTarget : size_t { GlesUploadOther, GlesUploadVDP1VRAM, GlesUploadVDP1FBRAMWrites, GlesUploadVDP2VRAM, GlesUploadTargetCount };

enum Kind { SCSP, VDP1, VDP2, Count };

inline std::atomic<uint64_t> g_waitNs[Count]{};
// Parts of the VDP2 wait: for the render thread to finish the previous frame (deferred delivery) and to take its copy of
// the VDP1 framebuffer (before a VDP1 erase or swap). The rest of VDP2 is erase/swap without the copy.
inline std::atomic<uint64_t> g_vdp2FrameWaitNs{0};
inline std::atomic<uint64_t> g_vdp2CopyWaitNs{0};
// Vulkan presentation diagnostics: host time blocked on the submitted frame fence and time spent in the frontend's
// synchronous frame callback (thumbnail/skip analysis and presentation handoff).
inline std::atomic<uint64_t> g_vulkanFrameFenceWaitNs{0};
inline std::atomic<uint64_t> g_vulkanFrameCallbackNs{0};
// GLES VDP renderer diagnostics (read once per second by the Android performance log).
inline std::atomic<uint64_t> g_glesDispatchTicks{0};
inline std::atomic<uint64_t> g_glesDispatchCount{0};
inline std::atomic<uint64_t> g_glesUploadTicks{0};
inline std::atomic<uint64_t> g_glesUploadCount{0};
inline std::atomic<uint64_t> g_glesPersistentUploadTicks{0};
inline std::atomic<uint64_t> g_glesPersistentUploadCount{0};
inline std::atomic<uint64_t> g_glesPersistentUploadBytes{0};
inline std::atomic<uint64_t> g_glesStagedCopyTicks{0};
inline std::atomic<uint64_t> g_glesStagedCopyCount{0};
inline std::atomic<uint64_t> g_glesStagedCopyBytes{0};
inline std::atomic<uint64_t> g_glesSubDataUploadTicks{0};
inline std::atomic<uint64_t> g_glesSubDataUploadCount{0};
inline std::atomic<uint64_t> g_glesSubDataUploadBytes{0};
inline std::atomic<uint64_t> g_glesUploadTargetTicks[GlesUploadTargetCount]{};
inline std::atomic<uint64_t> g_glesUploadTargetCount[GlesUploadTargetCount]{};
inline std::atomic<uint64_t> g_glesUploadTargetBytes[GlesUploadTargetCount]{};
inline std::atomic<uint64_t> g_glesFenceWaitTicks{0};
inline std::atomic<uint64_t> g_glesPresentCopyTicks{0};
inline std::atomic<uint64_t> g_glesReadbackTicks{0};
inline std::atomic<uint64_t> g_glesVDP1SubmitTicks{0};
inline std::atomic<uint64_t> g_glesVDP2EndTicks{0};

struct Timer {
    Kind kind;
    std::chrono::steady_clock::time_point start = std::chrono::steady_clock::now();
    explicit Timer(Kind k) : kind(k) {}
    ~Timer() {
        g_waitNs[kind].fetch_add(
            std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count(),
            std::memory_order_relaxed);
    }
};

struct SplitTimer {
    std::atomic<uint64_t> &total;
    std::chrono::steady_clock::time_point start = std::chrono::steady_clock::now();
    explicit SplitTimer(std::atomic<uint64_t> &t) : total(t) {}
    ~SplitTimer() {
        total.fetch_add(
            std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count(),
            std::memory_order_relaxed);
    }
};

// Where the emulation thread spends a frame (master SH-2, slave SH-2, SCU, VDP, the scheduler: sound and CD when they
// run on this thread). Timed only on frames the frontend marks with g_profile (a sample, to keep the cost negligible).
enum Part { PartMSH2, PartSSH2, PartSCU, PartVDP, PartSched, PartCount };

inline std::atomic<bool> g_profile{false};
// "Hi-res: one field" option: the second field of interlaced screens isn't rendered, so the software renderer copies each
// line of the first field onto the line below it (otherwise those lines keep an old picture)
inline std::atomic<bool> g_oneField{false};
// SH-2 chained interpreter (SH2::RunChained); off only for tests that compare it with the plain interpreter
inline std::atomic<bool> g_sh2Chained{true};
// Threaded VDP2 with two framebuffers (SoftwareVDPRenderer::m_framebuffers); off only for diagnostics (A/B tests)
inline std::atomic<bool> g_vdp2TwoFB{true};
// Threaded VDP: VDP1 draw commands and VDP2 lines sent to the render threads in batches; off only for diagnostics
inline std::atomic<bool> g_vdpBatch{true};
// GPU time of the Vulkan renderer's command buffers (timestamp queries), for the overlay's GPU usage
inline std::atomic<uint64_t> g_gpuBusyNs{0};
// Skip Engine (frontend runs at maximum speed through loads, transitions and save waits): activity counters read once
// per frame by the frontend. Data sectors the CD drive read, drive ticks spent seeking on a data track, and accesses to
// the backup memory (internal or cartridge).
inline std::atomic<uint32_t> g_cdDataSectors{0};
inline std::atomic<uint32_t> g_cdDataSeekTicks{0};
inline std::atomic<uint32_t> g_bupAccesses{0};
inline std::atomic<bool> g_gpuTimed{false}; // the Vulkan renderer measures it (timestamps supported)
inline std::atomic<uint64_t> g_partTicks[PartCount]{};
inline std::atomic<uint32_t> g_profiledFrames{0};

// The scheduler part split by event (profiled frames): VDP phases (line drawing hand-off to the VDP2 thread, waits for
// the render threads at the end of a frame), sound sample ticks, CD block/drive, and the rest (SCU timer 1, SMPC)
enum SchedKind { SchedVDP, SchedSCSP, SchedCD, SchedOther, SchedCount };
inline uint64_t g_schedTicks[SchedCount]{}; // only touched by the emulation thread; read and reset after the frame

// Tight loops of the SH-2s (index 0 master, 1 slave), on the profiled frames: cycles interpreted, cycles spent in loops
// that jump back over at most 4 instructions (polling a register or a delay loop: what idle-loop skipping would save)
// and the hottest loop heads (direct-mapped by address). Only touched by the emulation thread.
inline uint64_t g_shExec[2]{};
inline uint64_t g_shLoop[2]{};
inline uint32_t g_hotPc[2][64]{};
inline uint64_t g_hotCyc[2][64]{};
// Cycles skipped by idle-loop skipping (profiled frames)
inline uint64_t g_shSkip[2]{};

// VDP1 commands processed (all frames), a measure of how much the game draws with sprites/polygons
inline std::atomic<uint32_t> g_vdp1Commands{0};

// Cheap timestamp: the ARM generic timer on arm64 (no syscall, no vDSO), else steady_clock nanoseconds.
inline uint64_t Ticks() {
#if defined(__aarch64__)
    uint64_t v;
    asm volatile("mrs %0, cntvct_el0" : "=r"(v));
    return v;
#else
    return std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
#endif
}

inline double TicksPerSecond() {
#if defined(__aarch64__)
    uint64_t f;
    asm volatile("mrs %0, cntfrq_el0" : "=r"(f));
    return f ? static_cast<double>(f) : 1e9;
#else
    return 1e9;
#endif
}

struct TickTimer {
    std::atomic<uint64_t> &total;
    uint64_t start = Ticks();
    explicit TickTimer(std::atomic<uint64_t> &value) : total(value) {}
    ~TickTimer() { total.fetch_add(Ticks() - start, std::memory_order_relaxed); }
};

} // namespace util::waitstats
