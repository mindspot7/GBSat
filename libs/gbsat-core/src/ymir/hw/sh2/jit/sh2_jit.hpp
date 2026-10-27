#pragma once

// SH-2 JIT for ARM64 (docs/SH2_JIT.md). Compiles straight-line runs of SH-2 instructions into host code that gives
// exactly the interpreter's results: registers, T flag and cycle counts. SH2::Advance runs the blocks and interprets
// everything else. One cache serves both SH-2s (blocks only touch the registers they are given).

#include <ymir/core/types.hpp>

#include <memory>
#include <unordered_map>
#include <cstdlib>
#include <vector>

namespace ymir::sh2::jit {

// A compiled block: code(R, &SR) runs `count` instructions starting at `startPC`.
struct Block {
    void (*code)(uint32 *R, uint32 *SR);
    uint32 startPC;
    uint32 lastPC;      // address of the last instruction
    uint32 count;       // instructions (each takes 1 cycle)
    uint32 firstRegs;   // registers the first instruction reads or writes (bit n = Rn): load-use stall check
    bool setsFetch;     // an instruction at a 4-byte aligned address refilled the fetch pipeline...
    uint32 fetched;     // ...with this pair of opcodes
    uint32 pageFirst, pageLast; // 4 KiB pages of code it was compiled from (bus addresses >> 12)
};

class Cache {
public:
    // fetch(ctx, address): side-effect-free instruction read of the SH-2 asking for the block
    using Fetch = uint16 (*)(void *ctx, uint32 address);

    Cache();
    ~Cache();

    static bool Supported();

    // The block starting at pc, compiled on first use; nullptr when nothing can be compiled there (the first
    // instruction isn't supported or the address isn't plain work RAM / BIOS code)
    const Block *Get(uint32 pc, Fetch fetch, void *ctx);

    // Diagnostics: with compiling off no block is made, so the JIT path only interprets (one instruction at a time)
    bool compileEnabled = true;

    // Drops every block (reset, save state load)
    void Flush();

    // Bus write to work RAM: drops the blocks compiled from that 4 KiB page
    void CodeWritten(uint32 busAddress);

    struct Stats {
        uint64 blocksRun = 0, instrsRun = 0, interpreted = 0, compiled = 0, invalidated = 0, flushes = 0;
    } stats;

private:
    const Block *Compile(uint32 pc, Fetch fetch, void *ctx);

    uint8 *m_code = nullptr;
    size_t m_codeSize = 0;
    size_t m_codeUsed = 0;
    std::vector<std::unique_ptr<Block>> m_blocks;
    // Code a block was compiled from: [first, last] halfwords (normalized bus addresses)
    struct Range {
        uint32 pc, first, last;
    };
    void MarkCode(uint32 first, uint32 last);

    // Block starting at each halfword of the BIOS ROM, WRAM-L and WRAM-H (nullptr: not compiled yet, kNoBlock: nothing
    // compilable there). Checked on every instruction the chain dispatches, so a flat table
    Block **m_slots = nullptr;
    Block **Slot(uint32 pc);
    std::unordered_map<uint32, std::vector<Range>> m_pages; // 4 KiB page -> blocks compiled from it
    std::vector<uint64> m_codeBits = std::vector<uint64>(0x100000 / 64); // WRAM-L + WRAM-H halfwords holding code
};

} // namespace ymir::sh2::jit
