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

} // namespace util::waitstats
