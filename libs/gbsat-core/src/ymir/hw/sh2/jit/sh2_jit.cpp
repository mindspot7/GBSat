// Glue between Ymir's SH-2 (sh2.cpp) and YabaSanshiro's DynarecSh2: replaces DynarecSh2CInterface.cpp and the
// Yabause memory map. Compiled code calls the memGet*/memSet* functions below for every access that isn't an inline
// WRAM-H read; they go to the SH-2 that is running, through Ymir's bus and on-chip modules.

#include "sh2_jit.hpp"

#if defined(GBSAT_SH2_JIT)

    #include "DynarecSh2.h"

YabSysCompat yabsys;

namespace {

const ymir::sh2::jit::Memory *g_mem = nullptr; // memory of the SH-2 that is running
bool g_blocksCreated = false;                    // the block cache (64 MiB code area) exists

inline void AddCycles(uint32 cycles) {
    // The compiled code already counts one cycle per instruction; add the wait states beyond that
    if (cycles > 1 && DynarecSh2::CurrentContext != nullptr) {
        DynarecSh2::CurrentContext->memcycle_ += cycles - 1;
    }
}

// Writes to the cache data array (C0000000-C0000FFF, mirrored) can replace code compiled from there
inline void CacheArrayWritten(uint32 addr) {
    if ((addr & 0xE0000000) == 0xC0000000) {
        CompileBlocks::getInstance()->LookupTableC[(addr & 0x000FFFFF) >> 1] = nullptr;
    }
}

} // namespace

extern "C" {

void DebugLog(const char *, ...) {}

int EachClock() {
    return 0;
}
int DelayEachClock() {
    return 0;
}
int DebugEachClock() {
    return 0;
}
int DebugDelayClock() {
    return 0;
}

u8 memGetByteNoCache(u32 addr) {
    uint32 cycles = 0;
    const u8 value = g_mem->read8(g_mem->ctx, addr, cycles);
    AddCycles(cycles);
    return value;
}
u16 memGetWordNoCache(u32 addr) {
    uint32 cycles = 0;
    const u16 value = g_mem->read16(g_mem->ctx, addr, cycles);
    AddCycles(cycles);
    return value;
}
u32 memGetLongNoCache(u32 addr) {
    uint32 cycles = 0;
    const u32 value = g_mem->read32(g_mem->ctx, addr, cycles);
    AddCycles(cycles);
    return value;
}
void memSetByteNoCache(u32 addr, u8 data) {
    uint32 cycles = 0;
    CacheArrayWritten(addr);
    g_mem->write8(g_mem->ctx, addr, data, cycles);
    AddCycles(cycles);
}
void memSetWordNoCache(u32 addr, u16 data) {
    uint32 cycles = 0;
    CacheArrayWritten(addr);
    g_mem->write16(g_mem->ctx, addr, data, cycles);
    AddCycles(cycles);
}
void memSetLongNoCache(u32 addr, u32 data) {
    uint32 cycles = 0;
    CacheArrayWritten(addr);
    g_mem->write32(g_mem->ctx, addr, data, cycles);
    AddCycles(cycles);
}

// Ymir's MemRead/MemWrite already honor the cache option, so the "cached" entry points are the same functions
u8 memGetByte(u32 addr) {
    return memGetByteNoCache(addr);
}
u16 memGetWord(u32 addr) {
    return memGetWordNoCache(addr);
}
u32 memGetLong(u32 addr) {
    return memGetLongNoCache(addr);
}
void memSetByte(u32 addr, u8 data) {
    memSetByteNoCache(addr, data);
}
void memSetWord(u32 addr, u16 data) {
    memSetWordNoCache(addr, data);
}
void memSetLong(u32 addr, u32 data) {
    memSetLongNoCache(addr, data);
}

} // extern "C"

u16 MappedMemoryReadInst(u32 addr, u32 *) {
    return g_mem->fetch16(g_mem->ctx, addr);
}
u32 MappedMemoryReadLong(u32 addr, u32 *cycles) {
    if (cycles == nullptr) {
        return g_mem->peek32(g_mem->ctx, addr);
    }
    uint32 c = 0;
    const u32 value = g_mem->read32(g_mem->ctx, addr, c);
    *cycles = c;
    return value;
}
u32 MappedMemoryReadLongNocache(u32 addr, u32 *cycles) {
    return MappedMemoryReadLong(addr, cycles);
}
void MappedMemoryWriteLong(u32 addr, u32 value, u32 *cycles) {
    uint32 c = 0;
    g_mem->write32(g_mem->ctx, addr, value, c);
    if (cycles != nullptr) {
        *cycles = c;
    }
}

namespace ymir::sh2::jit {

bool Supported() {
    return true;
}

Cpu::Cpu(bool slave, const Memory &memory, uint8 *wramHigh)
    : m_memory(memory) {
    auto *cpu = new DynarecSh2(); // creates the shared block cache the first time
    g_blocksCreated = true;
    cpu->SetSlave(slave);
    tagSH2 *sh = cpu->getDynaSh();
    sh->getmembyte = (uintptr_t)memGetByteNoCache;
    sh->getmemword = (uintptr_t)memGetWordNoCache;
    sh->getmemlong = (uintptr_t)memGetLongNoCache;
    sh->setmembyte = (uintptr_t)memSetByteNoCache;
    sh->setmemword = (uintptr_t)memSetWordNoCache;
    sh->setmemlong = (uintptr_t)memSetLongNoCache;
    if (wramHigh != nullptr) { // otherwise the constructor's zeroed stand-in stays
        sh->highwram = (uintptr_t)wramHigh;
    }
    m_impl = cpu;
}

Cpu::~Cpu() {
    auto *cpu = static_cast<DynarecSh2 *>(m_impl);
    if (DynarecSh2::CurrentContext == cpu) {
        DynarecSh2::CurrentContext = nullptr;
    }
    delete cpu;
}

void Cpu::Load(const Regs &regs) {
    auto *cpu = static_cast<DynarecSh2 *>(m_impl);
    std::memcpy(cpu->GetGenRegPtr(), regs.R, sizeof(regs.R));
    cpu->SET_SR(regs.SR);
    cpu->SET_GBR(regs.GBR);
    cpu->SET_VBR(regs.VBR);
    cpu->SET_MACH(regs.MACH);
    cpu->SET_MACL(regs.MACL);
    cpu->SET_PR(regs.PR);
    cpu->SET_PC(regs.PC);
    cpu->SET_ICOUNT(0); // interrupts are Ymir's (INTC), never queued here
}

void Cpu::Store(Regs &regs) const {
    auto *cpu = static_cast<DynarecSh2 *>(m_impl);
    std::memcpy(regs.R, cpu->GetGenRegPtr(), sizeof(regs.R));
    regs.SR = cpu->GET_SR();
    regs.GBR = cpu->GET_GBR();
    regs.VBR = cpu->GET_VBR();
    regs.MACH = cpu->GET_MACH();
    regs.MACL = cpu->GET_MACL();
    regs.PR = cpu->GET_PR();
    regs.PC = cpu->GET_PC();
}

uint64 Cpu::Run(uint64 budget, const uint8 *pendingLevel, bool &looped) {
    auto *cpu = static_cast<DynarecSh2 *>(m_impl);
    tagSH2 *sh = cpu->getDynaSh();
    const Memory *prevMem = g_mem;
    DynarecSh2 *prevCpu = DynarecSh2::CurrentContext;
    g_mem = &m_memory;
    cpu->SetCurrentContext();

    const uint32 target = budget > 0x7FFFFFFF ? 0x7FFFFFFF : (uint32)budget;
    sh->SysReg[4] = 0;
    sh->exitcount = target;
    cpu->memcycle_ = 0;
    while (sh->SysReg[4] < target) {
        if (cpu->Execute() == IN_INFINITY_LOOP) {
            // Polling loop (or SLEEP): the rest of the slice would repeat it
            sh->SysReg[4] = target;
            looped = true;
        }
        sh->SysReg[4] += cpu->memcycle_;
        cpu->memcycle_ = 0;
        if (*pendingLevel > ((sh->CtrlReg[0] >> 4) & 0xF)) {
            break;
        }
    }
    const uint64 used = sh->SysReg[4];

    g_mem = prevMem;
    DynarecSh2::CurrentContext = prevCpu;
    return used;
}

void Flush() {
    if (g_blocksCreated) {
        CompileBlocks::getInstance()->Init();
    }
}

void CodeWritten(uint32 address, uint32 size) {
    if (!g_blocksCreated) {
        return;
    }
    CompileBlocks *blocks = CompileBlocks::getInstance();
    const uint32 offset = address & 0xFFFFF;
    if ((address & 0x7F00000) == 0x0200000) {
        // WRAM-L: only blocks starting at the written halfwords (as in YabaSanshiro)
        for (uint32 i = 0; i < size; i += 2) {
            blocks->LookupTableLow[((offset + i) & 0xFFFFF) >> 1] = nullptr;
        }
    } else {
        // WRAM-H (06000000-07FFFFFF, mirrored every MiB): every block that includes them
        for (uint32 i = 0; i < size; i += 2) {
            blocks->setDirty(0x06000000 | ((offset + i) & 0xFFFFF));
        }
    }
}

} // namespace ymir::sh2::jit

#else

namespace ymir::sh2::jit {

bool Supported() {
    return false;
}
Cpu::Cpu(bool, const Memory &memory, uint8 *)
    : m_impl(nullptr)
    , m_memory(memory) {}
Cpu::~Cpu() = default;
void Cpu::Load(const Regs &) {}
void Cpu::Store(Regs &) const {}
uint64 Cpu::Run(uint64 budget, const uint8 *, bool &) {
    return budget;
}
void Flush() {}
void CodeWritten(uint32, uint32) {}

} // namespace ymir::sh2::jit

#endif
