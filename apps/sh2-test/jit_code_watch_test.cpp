// ARM64 regression/benchmark for the JIT's code-write watch. No BIOS or game needed.
#include "../../libs/gbsat-core/src/ymir/hw/sh2/jit/sh2_jit.hpp"
#include <chrono>
#include <cstdio>
#include <cstdlib>

using ymir::sh2::jit::Cache;
static uint16 Fetch(void *, uint32) { return 0x0009; }
static void Check(bool ok) { if (!ok) { std::fprintf(stderr, "code-watch regression\n"); std::exit(1); } }

int main() {
    Check(Cache::Supported());
    Cache cache;
    cache.compileEnabled = false; // Negative cache entries are watched too.
    for (uint32 base : {0x00200000u, 0x06000000u}) {
        for (uint32 offset = 0; offset < 256; ++offset) {
            cache.Flush();
            const uint32 address = base + offset;
            const uint32 affected = (address & ~1u) + 2;
            cache.Get(affected, Fetch, nullptr, true);
            cache.Get(base + 0x1000, Fetch, nullptr, true);
            auto epoch = *cache.Epoch();
            auto invalidated = cache.stats.invalidated;
            cache.CodeWritten(base + 0x8000);
            Check(*cache.Epoch() == epoch);
            cache.CodeWritten(address);
            Check(*cache.Epoch() == epoch + 1);
            Check(cache.stats.invalidated == invalidated + 1);
            cache.CodeWritten(address);
            Check(*cache.Epoch() == epoch + 1);
            cache.CodeWritten(base + 0x1000);
            Check(*cache.Epoch() == epoch + 2);
        }
    }
    for (uint32 alias : {0x06100000u, 0x26000000u, 0xa6000000u}) {
        cache.Flush();
        cache.Get(0x06000000, Fetch, nullptr, true);
        auto epoch = *cache.Epoch();
        cache.CodeWritten(alias);
        Check(*cache.Epoch() == epoch + 1);
    }
    cache.compileEnabled = true;
    for (uint32 address : {0x0020007eu, 0x0600007eu, 0x00200ffeu, 0x06000ffeu}) {
        cache.Flush();
        const auto *block = cache.Get(address, Fetch, nullptr, true);
        Check(block != nullptr);
        auto epoch = *cache.Epoch();
        cache.CodeWritten(address + 0x8000);
        Check(*cache.Epoch() == epoch);
        Check(cache.Get(address, Fetch, nullptr, false) == block);
        cache.CodeWritten(address + 2);
        Check(*cache.Epoch() > epoch);
        Check(cache.Get(address, Fetch, nullptr, false) == nullptr);
    }
    cache.Flush();
    const auto start = std::chrono::steady_clock::now();
    for (uint32 i = 0; i < 10000000; ++i) cache.CodeWritten(0x06000000 | ((i * 4) & 0xfffff));
    const auto ns = std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now() - start).count();
    std::printf("PASS: 512 adjacency/boundary cases, 3 aliases, 4 native blocks; empty write %.2f ns\n", ns / 10000000.0);
}
