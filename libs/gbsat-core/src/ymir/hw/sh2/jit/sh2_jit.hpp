#pragma once

// Dynamic recompiler for the SH-2 (YabaSanshiro's DynarecSh2, by devMiyax): runs compiled blocks of SH-2 code instead
// of interpreting one instruction at a time. sh2.cpp owns the time slices, interrupts and on-chip modules; this side
// only runs code. Built on ARM64 only (GBSAT_SH2_JIT); elsewhere Supported() is false and nothing else is called.

#include <ymir/core/types.hpp>

namespace ymir::sh2::jit {

// Registers exchanged with the interpreter at the edges of each time slice
struct Regs {
    uint32 R[16];
    uint32 SR, GBR, VBR;
    uint32 MACH, MACL, PR, PC;
};

// Memory of the SH-2 that is running (implemented in sh2.cpp). Reads and writes add their bus wait states to *cycles.
struct Memory {
    void *ctx;
    uint8 (*read8)(void *ctx, uint32 address, uint32 &cycles);
    uint16 (*read16)(void *ctx, uint32 address, uint32 &cycles);
    uint32 (*read32)(void *ctx, uint32 address, uint32 &cycles);
    void (*write8)(void *ctx, uint32 address, uint8 value, uint32 &cycles);
    void (*write16)(void *ctx, uint32 address, uint16 value, uint32 &cycles);
    void (*write32)(void *ctx, uint32 address, uint32 value, uint32 &cycles);
    uint16 (*fetch16)(void *ctx, uint32 address); // instruction words for the compiler (no side effects)
    uint32 (*peek32)(void *ctx, uint32 address);

    // Work RAMs, accessed directly (no call into the SH-2) while SH-2 cache emulation is off
    uint8 *wramLow;  // 00200000-002FFFFF
    uint8 *wramHigh; // 06000000-07FFFFFF (1 MiB, mirrored)
    uint32 wramLowCycles, wramHighCycles; // bus cycles of a 32-bit access
    const bool *emulateCache;
};

bool Supported();

class Cpu {
public:
    Cpu(bool slave, const Memory &memory, uint8 *wramHigh);
    ~Cpu();

    void Load(const Regs &regs);
    void Store(Regs &regs) const;

    // Runs compiled blocks for about `budget` cycles (the last block may run over). Returns early, between blocks,
    // when an interrupt of a level above SR's mask is pending (*pendingLevel, read after every block). Returns the
    // cycles used; `looped` tells that the slice ended in a loop that only repeats itself (polling, SLEEP).
    uint64 Run(uint64 budget, const uint8 *pendingLevel, bool &looped);

private:
    void *m_impl;
    Memory m_memory;
};

// Drops every compiled block (memory replaced behind the CPUs' back: reset, save state load, disc change)
void Flush();

// Code memory written (WRAM-L or WRAM-H, bus address): compiled blocks covering it are dropped
void CodeWritten(uint32 address, uint32 size);

} // namespace ymir::sh2::jit
