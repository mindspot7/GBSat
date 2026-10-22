#pragma once

// Diagnostics: time the emulation thread spends waiting for other threads (sound, VDP1 and VDP2 render threads).
// Read and reset by the frontend (performance log).

#include <atomic>
#include <chrono>
#include <cstdint>

namespace util::waitstats {

enum Kind { SCSP, VDP1, VDP2, Count };

inline std::atomic<uint64_t> g_waitNs[Count]{};

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

// Where the emulation thread spends a frame (master SH-2, slave SH-2, SCU, VDP, the scheduler: sound and CD when they
// run on this thread). Timed only on frames the frontend marks with g_profile (a sample, to keep the cost negligible).
enum Part { PartMSH2, PartSSH2, PartSCU, PartVDP, PartSched, PartCount };

inline std::atomic<bool> g_profile{false};
inline std::atomic<uint64_t> g_partTicks[PartCount]{};
inline std::atomic<uint32_t> g_profiledFrames{0};

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

} // namespace util::waitstats
