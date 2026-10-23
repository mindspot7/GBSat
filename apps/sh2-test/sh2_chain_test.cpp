// Checks that the chained SH-2 interpreter (SH2::RunChained) gives exactly the same results as the plain one
// (InterpretNext): random code in High Work RAM runs for a few frames with each, and the CPU registers, the cycle count
// and both work RAMs must match. No BIOS needed (a minimal IPL whose reset vector points to the random code).
#include <ymir/ymir.hpp>
#include <ymir/util/wait_stats.hpp>

#include <cstdio>
#include <cstring>
#include <memory>
#include <random>
#include <vector>

using namespace ymir;

struct State {
    std::array<uint32, 16> r{};
    uint32 pc = 0, pr = 0;
    uint64 mac = 0;
    uint64 hashH = 0, hashL = 0;
    bool operator==(const State &) const = default;
};

static uint64 Hash(Saturn &s, uint32 base, uint32 size) {
    uint64 h = 1469598103934665603ull;
    for (uint32 a = 0; a < size; a += 4) {
        h = (h ^ s.mainBus.Peek<uint32>(base + a)) * 1099511628211ull;
    }
    return h;
}

static State Run(uint32 seed, bool chained, uint32 frames) {
    util::waitstats::g_sh2Chained.store(chained);
    auto saturn = std::make_unique<Saturn>();
    std::vector<uint8> ipl(sys::kIPLSize, 0);
    const uint8 boot[] = {0x06, 0x00, 0x40, 0x00, 0x06, 0x0F, 0xFF, 0xF0}; // PC = 0x06004000, SP = 0x060FFFF0
    std::memcpy(ipl.data(), boot, sizeof(boot));
    saturn->LoadIPL(std::span<uint8, sys::kIPLSize>(ipl.data(), sys::kIPLSize));
    saturn->configuration.swRenderer.threadedVDP1 = false;
    saturn->configuration.swRenderer.threadedVDP2 = false;
    saturn->configuration.audio.threadedSCSP = false;
    saturn->Reset(true);

    std::mt19937 rng(seed);
    // Random code, biased towards common instructions, short backward branches (loops, idle-loop detection) and delay
    // slots; data words in between. Exception vectors (VBR = 0, in the IPL: 0) send exceptions to address 0, which
    // runs zeros there: deterministic either way.
    auto word = [&]() -> uint16 {
        const uint32 k = rng() % 16;
        const uint16 n = (rng() % 16) << 8, m = (rng() % 16) << 4;
        switch (k) {
        case 0: return 0x6003 | n | m;                          // mov Rm,Rn
        case 1: return 0x300C | n | m;                          // add Rm,Rn
        case 2: return 0x7000 | n | (rng() & 0xFF);             // add #imm,Rn
        case 3: return 0xE000 | n | (rng() & 0xFF);             // mov #imm,Rn
        case 4: return 0x2008 | n | m;                          // tst Rm,Rn
        case 5: return 0x8900 | (0x100 - 1 - rng() % 8);        // bt short backward
        case 6: return 0x8B00 | (0x100 - 1 - rng() % 8);        // bf short backward
        case 7: return 0x4010 | n;                              // dt Rn
        case 8: return 0x6002 | n | m;                          // mov.l @Rm,Rn
        case 9: return 0x2002 | n | m;                          // mov.l Rm,@Rn
        case 10: return 0x0009;                                 // nop
        case 11: return 0xA000 | (rng() % 64);                  // bra forward
        case 12: return 0x3000 | n | m | (rng() % 8);           // cmp/x
        default: return (uint16)rng();                          // anything
        }
    };
    for (uint32 a = 0; a < 0x8000; a += 2) {
        saturn->mainBus.Poke<uint16>(0x06004000 + a, word());
    }
    // Registers that point into work RAM, so loads and stores hit RAM most of the time
    auto &p = saturn->masterSH2.GetProbe();
    for (int i = 0; i < 15; ++i) {
        p.R(i) = 0x06010000 + (rng() % 0x10000) * 4;
    }
    for (uint32 f = 0; f < frames; ++f) {
        saturn->RunFrame();
    }
    State st;
    st.r = p.R();
    st.pc = p.PC();
    st.pr = p.PR();
    st.mac = p.MAC().u64;
    st.hashH = Hash(*saturn, 0x06000000, 0x100000);
    st.hashL = Hash(*saturn, 0x00200000, 0x100000);
    return st;
}

int main(int argc, char **argv) {
    const uint32 count = argc > 1 ? (uint32)std::atoi(argv[1]) : 200;
    const uint32 frames = 4;
    uint32 failures = 0;
    for (uint32 seed = 1; seed <= count; ++seed) {
        const State a = Run(seed, false, frames);
        const State b = Run(seed, true, frames);
        if (!(a == b)) {
            std::printf("seed %u: MISMATCH (PC %08X vs %08X)\n", seed, a.pc, b.pc);
            ++failures;
        }
    }
    std::printf("%u programs, %u mismatches\nRESULT: %s\n", count, failures, failures ? "MISMATCH" : "MATCH");
    return failures ? 1 : 0;
}
