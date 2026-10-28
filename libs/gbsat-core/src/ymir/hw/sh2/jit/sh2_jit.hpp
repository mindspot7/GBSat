#pragma once

// SH-2 JIT for ARM64 (docs/SH2_JIT.md). Blocks run inside the chained interpreter (SH2::RunChained<.., true>) and give
// exactly its results: simple register/ALU/compare instructions are compiled to host code; every other instruction is
// a call to the interpreter's one-instruction step (InterpretNext), so memory accesses, branches and their delay slots
// keep the interpreter's semantics. A block stops where the chain would stop or re-check: after a branch (and its delay
// slot), at the end of the time slice, when an interrupt becomes due, on a bus wait or after a write to compiled code.
// One cache serves both SH-2s.

#include <ymir/core/types.hpp>

#include <cstddef>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <unordered_map>
#include <vector>

namespace ymir::sh2::jit {

struct Block;

// The SH-2 state a block works on (pointers into the SH2 object, set once) and the per-run values. Standard layout:
// the generated code uses the offsets.
struct Context {
    uint32 *R;                       // R0-R15
    uint32 *SR;
    uint32 *PC;
    uint8 *wbReg;                    // register loaded by the last instruction (load-use stall)
    uint8 *intr;                     // [0] interrupt pending, [1] allowed
    uint32 *fetched;                 // fetched opcode pair (pipeline)
    uint32 *GBR;
    uint64 *MAC;                     // MAC.u64 (MACL low, MACH high)
    uint32 *PR;
    bool *delaySlot;
    uint32 *delaySlotTarget;
    uint8 *wramHigh;                 // WRAM-H array (big-endian), accessed directly for the cached area
    const uint64 *codeBits;          // the cache's code bitmap (writes to compiled code)
    void *cache;
    void (*codeWritten)(void *cache, uint32 address);
    uint64 *cycles;                  // cycles executed in this Advance
    void *sh2;
    uint64 (*interpret)(void *sh2);  // runs the instruction at PC, returns its cycles
    const uint32 *epoch;             // cache epoch: changes when a write drops compiled code
    uint64 limit;                    // end of the time slice
    uint32 startEpoch;               // epoch when the block started
    uint32 lastPC;                   // out: last instruction run (or attempted)
    uint8 post;                      // out: 1 refill the pipeline (taken branch), 2 a delay slot ended (see RunBlocks)
    Block *const *slots;             // the cache's block table (blocks jump to the next one directly)
    const uint8 *intcLevel;          // INTC.pending.level (end of a delay slot)
    // Approximate mode: memory accesses outside the direct path ([0] byte, [1] word, [2] long; loads return the raw value)
    uint32 (*memRead[3])(void *sh2, uint32 address);
    void (*memWrite[3])(void *sh2, uint32 address, uint32 value);
};

// A compiled block: code(&context) runs from startPC.
struct Block {
    void (*code)(Context *ctx);
    uint32 startPC;
    uint32 instrs; // instructions it runs to the end (each takes at least one cycle)
    void *linked;  // entry from another block: registers, SR and cycles already in host registers
    uint32 maxCycles; // most cycles it can take without interpreter calls: it starts only when the slice has more
};

class Cache {
public:
    // fetch(ctx, address): side-effect-free instruction read of the SH-2 asking for the block
    using Fetch = uint16 (*)(void *ctx, uint32 address);

    Cache();
    ~Cache();

    static bool Supported();

    // The block starting at pc, compiled on first use; nullptr when nothing can be compiled there (not plain work
    // RAM / BIOS code, or nothing worth a block)
    const Block *Get(uint32 pc, Fetch fetch, void *ctx, bool compile);

    // Diagnostics: with compiling off no block is made, so the JIT path only interprets
    bool compileEnabled = true;
    // Diagnostics: host code for these kinds (off: interpreter calls)
    bool nativeBranches = true, nativeSpecials = true, nativeMac = true, nativeSlots = true;
    // Approximate timing (option for weak devices): blocks always run whole, so the time slice can end up to one block
    // late. Faster, but not identical to the interpreter. Set before compiling (Flush when changed)
    bool approximate = false;
    // Diagnostics: when set, each compiled block is described here (host address of each part and SH-2 instruction)
    std::FILE *mapFile = nullptr;

    // Drops every block (reset, save state load)
    void Flush();

    // Bus write to work RAM: drops the blocks compiled from the written halfwords
    void CodeWritten(uint32 busAddress);

    const uint32 *Epoch() const {
        return &m_epoch;
    }
    Block *const *Slots() const {
        return m_slots;
    }
    const uint64 *CodeBits() const {
        return m_codeBits.data();
    }
    static void CodeWrittenThunk(void *cache, uint32 address) {
        static_cast<Cache *>(cache)->CodeWritten(address);
    }

    struct Stats {
        uint64 blocksRun = 0, instrsRun = 0, interpreted = 0, compiled = 0, invalidated = 0, flushes = 0;
        uint64 nativeInstrs = 0, calledInstrs = 0; // compiled instructions: host code / interpreter calls
        uint64 shortSlice = 0;                     // blocks not run: too little of the slice left
        uint32 calledOps[512] = {};                // compiled interpreter calls by OpcodeType (diagnostics)
    } stats;

private:
    const Block *Compile(uint32 pc, Fetch fetch, void *ctx);

    // Code a block was compiled from: [first, last] halfwords (normalized bus addresses)
    struct Range {
        uint32 pc, first, last;
    };
    void MarkCode(uint32 first, uint32 last);

    uint8 *m_code = nullptr;
    size_t m_codeSize = 0;
    size_t m_codeUsed = 0;
    uint32 m_epoch = 0;
    std::vector<std::unique_ptr<Block>> m_blocks;
    // Block starting at each halfword of the BIOS ROM, WRAM-L and WRAM-H (nullptr: not compiled yet, kNoBlock: nothing
    // compilable there). Checked on every instruction the chain dispatches, so a flat table
    Block **m_slots = nullptr;
    Block **Slot(uint32 pc);
    std::unordered_map<uint32, std::vector<Range>> m_pages; // 4 KiB page -> blocks compiled from it
    std::vector<uint64> m_codeBits = std::vector<uint64>(0x100000 / 64); // WRAM-L + WRAM-H halfwords holding code
};

} // namespace ymir::sh2::jit
