#include "sh2_jit.hpp"

#include <ymir/hw/sh2/sh2_decode.hpp>

#include <algorithm>
#include <bit>
#include <cstring>

#if defined(__aarch64__) && (defined(__ANDROID__) || defined(__linux__))
    #define GBSAT_SH2_JIT 1
    #include "a64_emitter.hpp"
    #include <sys/mman.h>
#endif

namespace ymir::sh2::jit {

namespace {

constexpr size_t kCodeSize = 32u << 20; // RWX area; flushed when full
constexpr uint32 kMaxInstrs = 64;      // instructions per block

// Only plain memory holds compiled code: BIOS ROM, WRAM-L, WRAM-H (cached and cache-through areas)
bool CompilableAddress(uint32 pc) {
    const uint32 partition = pc >> 29;
    if (partition != 0b000 && partition != 0b001 && partition != 0b101) {
        return false;
    }
    const uint32 bus = pc & 0x7FFFFFF;
    return bus < 0x0100000 || (bus >= 0x0200000 && bus < 0x0300000) || bus >= 0x6000000;
}

// Bus address with the WRAM-H mirrors (06000000-07FFFFFF, every MiB) folded onto 06000000-060FFFFF
uint32 Normalize(uint32 address) {
    const uint32 bus = address & 0x7FFFFFF;
    return bus >= 0x6000000 ? 0x6000000 | (bus & 0xFFFFF) : bus;
}

uint32 Page(uint32 pc) {
    return Normalize(pc) >> 12;
}

// Slot table index of a halfword (normalized address): BIOS ROM, WRAM-L, WRAM-H
constexpr size_t kSlots = 0x180000;
size_t SlotIndex(uint32 norm) {
    if (norm < 0x100000) {
        return norm >> 1;
    }
    if (norm < 0x300000) {
        return 0x80000 + ((norm - 0x200000) >> 1);
    }
    return 0x100000 + ((norm & 0xFFFFF) >> 1);
}

Block *const kNoBlock = reinterpret_cast<Block *>(uintptr_t{1});

// Bit index of a work RAM halfword (normalized address) in the code bitmap; -1 for the BIOS ROM (never written)
sint32 CodeIndex(uint32 norm) {
    if (norm >= 0x200000 && norm < 0x300000) {
        return (norm - 0x200000) >> 1;
    }
    if (norm >= 0x6000000) {
        return 0x80000 + ((norm & 0xFFFFF) >> 1);
    }
    return -1;
}

} // namespace

#if GBSAT_SH2_JIT

namespace {

using namespace a64;

// Registers the first instruction of a block uses, for the interpreter's load-use stall (WritebackCycles)
struct Instr {
    OpcodeType op;
    uint16 opcode;
    uint32 pc;
};

uint32 Rn(uint16 op) {
    return (op >> 8) & 15;
}
uint32 Rm(uint16 op) {
    return (op >> 4) & 15;
}
uint32 SImm8(uint16 op) {
    return static_cast<uint32>(static_cast<sint32>(static_cast<sint8>(op & 0xFF)));
}
uint32 UImm8(uint16 op) {
    return op & 0xFF;
}

enum Uses : uint8 { kNone, kN, kNM, kR0 };

// Supported instructions and the registers they read/write (in the interpreter's WritebackCycles call)
bool IsSupported(OpcodeType op, Uses &uses, bool &usesT) {
    usesT = false;
    switch (op) {
    case OpcodeType::NOP: uses = kNone; return true;
    case OpcodeType::CLRT:
    case OpcodeType::SETT: uses = kNone; usesT = true; return true;
    case OpcodeType::MOV_R:
    case OpcodeType::EXTUB:
    case OpcodeType::EXTUW:
    case OpcodeType::EXTSB:
    case OpcodeType::EXTSW:
    case OpcodeType::SWAPB:
    case OpcodeType::SWAPW:
    case OpcodeType::XTRCT:
    case OpcodeType::ADD:
    case OpcodeType::SUB:
    case OpcodeType::AND_R:
    case OpcodeType::OR_R:
    case OpcodeType::XOR_R:
    case OpcodeType::NOT:
    case OpcodeType::NEG: uses = kNM; return true;
    case OpcodeType::ADDC:
    case OpcodeType::SUBC:
    case OpcodeType::NEGC:
    case OpcodeType::CMP_EQ_R:
    case OpcodeType::CMP_GE:
    case OpcodeType::CMP_GT:
    case OpcodeType::CMP_HI:
    case OpcodeType::CMP_HS:
    case OpcodeType::CMP_STR:
    case OpcodeType::TST_R: uses = kNM; usesT = true; return true;
    case OpcodeType::MOV_I:
    case OpcodeType::ADD_I:
    case OpcodeType::SHLL2:
    case OpcodeType::SHLL8:
    case OpcodeType::SHLL16:
    case OpcodeType::SHLR2:
    case OpcodeType::SHLR8:
    case OpcodeType::SHLR16: uses = kN; return true;
    case OpcodeType::MOVT:
    case OpcodeType::ROTL:
    case OpcodeType::ROTR:
    case OpcodeType::ROTCL:
    case OpcodeType::ROTCR:
    case OpcodeType::SHAL:
    case OpcodeType::SHAR:
    case OpcodeType::SHLL:
    case OpcodeType::SHLR:
    case OpcodeType::DT:
    case OpcodeType::CMP_PL:
    case OpcodeType::CMP_PZ: uses = kN; usesT = true; return true;
    case OpcodeType::MOVA:
    case OpcodeType::AND_I:
    case OpcodeType::OR_I:
    case OpcodeType::XOR_I: uses = kR0; return true;
    case OpcodeType::CMP_EQ_I:
    case OpcodeType::TST_I: uses = kR0; usesT = true; return true;
    default: return false;
    }
}

uint32 UsedRegs(Uses uses, uint16 opcode) {
    switch (uses) {
    case kN: return 1u << Rn(opcode);
    case kNM: return (1u << Rn(opcode)) | (1u << Rm(opcode));
    case kR0: return 1u;
    default: return 0;
    }
}

// MOV.B/W/L loads and stores compiled with a direct WRAM-H path (cached area); other addresses call the interpreter.
// Mirrors the interpreter's handlers: address, value, the registers its WritebackCycles checks (and how many times),
// post-increment / pre-decrement, and which register a load leaves pending.
struct MemOp {
    uint32 size;       // 1, 2, 4
    bool store;
    enum Base { kRn, kRm, kRmR0, kRnR0, kRmDisp, kRnDisp, kGBR, kPC } base;
    uint32 disp;       // added to the base (scaled)
    uint32 target;     // load: destination register
    uint32 value;      // store: source register
    uint32 baseReg;    // kRn/kRm.. : the register holding the address
    bool postInc;      // load @Rm+ (when Rn != Rm)
    bool preDec;       // store @-Rn
    uint32 wbRegs;     // registers checked for the load-use stall
    uint32 wbTimes;    // 1, or 2 for MOV.W @(disp,Rm),R0
    uint32 regs;       // every register the instruction names
};

bool DescribeMem(OpcodeType op, uint16 opcode, uint32 pc, MemOp &m) {
    const uint32 n = Rn(opcode), mm = Rm(opcode);
    const uint32 d4 = opcode & 15, d8 = opcode & 0xFF;
    m = {};
    m.wbTimes = 1;
    auto load = [&](uint32 size, MemOp::Base base, uint32 baseReg, uint32 disp, uint32 target, uint32 wb) {
        m.size = size;
        m.store = false;
        m.base = base;
        m.baseReg = baseReg;
        m.disp = disp;
        m.target = target;
        m.wbRegs = wb;
    };
    auto store = [&](uint32 size, MemOp::Base base, uint32 baseReg, uint32 disp, uint32 value, uint32 wb) {
        m.size = size;
        m.store = true;
        m.base = base;
        m.baseReg = baseReg;
        m.disp = disp;
        m.value = value;
        m.wbRegs = wb;
    };
    const uint32 bN = 1u << n, bM = 1u << mm, b0 = 1u;
    switch (op) {
    case OpcodeType::MOVB_L: load(1, MemOp::kRm, mm, 0, n, bM); break;
    case OpcodeType::MOVW_L: load(2, MemOp::kRm, mm, 0, n, bM); break;
    case OpcodeType::MOVL_L: load(4, MemOp::kRm, mm, 0, n, bM); break;
    case OpcodeType::MOVB_P: load(1, MemOp::kRm, mm, 0, n, bM); m.postInc = n != mm; break;
    case OpcodeType::MOVW_P: load(2, MemOp::kRm, mm, 0, n, bM); m.postInc = n != mm; break;
    case OpcodeType::MOVL_P: load(4, MemOp::kRm, mm, 0, n, bM); m.postInc = n != mm; break;
    case OpcodeType::MOVB_L0: load(1, MemOp::kRmR0, mm, 0, n, bM | b0); break;
    case OpcodeType::MOVW_L0: load(2, MemOp::kRmR0, mm, 0, n, bM | b0); break;
    case OpcodeType::MOVL_L0: load(4, MemOp::kRmR0, mm, 0, n, bM | b0); break;
    case OpcodeType::MOVB_L4: load(1, MemOp::kRmDisp, mm, d4, 0, 0); break;
    case OpcodeType::MOVW_L4: load(2, MemOp::kRmDisp, mm, d4 << 1, 0, bM); m.wbTimes = 2; break;
    case OpcodeType::MOVL_L4: load(4, MemOp::kRmDisp, mm, d4 << 2, n, bM); break;
    case OpcodeType::MOVB_LG: load(1, MemOp::kGBR, 0, d8, 0, 0); break;
    case OpcodeType::MOVW_LG: load(2, MemOp::kGBR, 0, d8 << 1, 0, 0); break;
    case OpcodeType::MOVL_LG: load(4, MemOp::kGBR, 0, d8 << 2, 0, 0); break;
    case OpcodeType::MOVW_I: load(2, MemOp::kPC, 0, pc + (d8 << 1) + 4, n, 0); break;
    case OpcodeType::MOVL_I: load(4, MemOp::kPC, 0, (pc & ~3u) + (d8 << 2) + 4, n, 0); break;
    case OpcodeType::MOVB_S: store(1, MemOp::kRn, n, 0, mm, bM | bN); break;
    case OpcodeType::MOVW_S: store(2, MemOp::kRn, n, 0, mm, bM | bN); break;
    case OpcodeType::MOVL_S: store(4, MemOp::kRn, n, 0, mm, bM | bN); break;
    case OpcodeType::MOVB_M: store(1, MemOp::kRn, n, 0, mm, bM | bN); m.preDec = true; break;
    case OpcodeType::MOVW_M: store(2, MemOp::kRn, n, 0, mm, bM | bN); m.preDec = true; break;
    case OpcodeType::MOVL_M: store(4, MemOp::kRn, n, 0, mm, bM | bN); m.preDec = true; break;
    case OpcodeType::MOVB_S0: store(1, MemOp::kRnR0, n, 0, mm, bN | b0); break;
    case OpcodeType::MOVW_S0: store(2, MemOp::kRnR0, n, 0, mm, bN | b0); break;
    case OpcodeType::MOVL_S0: store(4, MemOp::kRnR0, n, 0, mm, bN | b0); break;
    case OpcodeType::MOVB_S4: store(1, MemOp::kRnDisp, mm, d4, 0, bM | b0); break; // nd4: Rn in bits 4-7
    case OpcodeType::MOVW_S4: store(2, MemOp::kRnDisp, mm, d4 << 1, 0, bM | b0); break;
    case OpcodeType::MOVL_S4: store(4, MemOp::kRnDisp, n, d4 << 2, mm, bM | bN); break;
    case OpcodeType::MOVB_SG: store(1, MemOp::kGBR, 0, d8, 0, b0); break;
    case OpcodeType::MOVW_SG: store(2, MemOp::kGBR, 0, d8 << 1, 0, b0); break;
    case OpcodeType::MOVL_SG: store(4, MemOp::kGBR, 0, d8 << 2, 0, b0); break;
    default: return false;
    }
    uint32 regs = 0;
    if (m.base != MemOp::kGBR && m.base != MemOp::kPC) {
        regs |= 1u << m.baseReg;
    }
    if (m.base == MemOp::kRmR0 || m.base == MemOp::kRnR0) {
        regs |= b0;
    }
    if (m.store) {
        regs |= 1u << m.value;
    } else {
        regs |= 1u << m.target;
    }
    m.regs = regs | m.wbRegs;
    return true;
}

// MAC, MUL, CLRMAC, STS/LDS with registers: host code. Mirrors the interpreter's cycles, load-use checks (bit 16:
// PR), the register left pending and the interrupt block of LDS/STS.
struct Special {
    uint32 regs;      // SH-2 registers named
    uint32 wbMask;    // registers checked for the load-use stall
    sint32 wbAfter;   // m_wbReg after (0xFF none)
    uint32 cycles;
    bool allowFalse;  // blocks interrupts on the next instruction
};
constexpr uint32 kWBPR = 0x10;
bool DescribeSpecial(OpcodeType op, uint16 opcode, Special &x) {
    const uint32 n = Rn(opcode), m = Rn(opcode); // m-format: register in bits 8-11 too
    const uint32 rm4 = Rm(opcode);
    const uint32 bPR = 1u << kWBPR;
    switch (op) {
    case OpcodeType::CLRMAC: x = {0, 0, 0xFF, 1, false}; return true;
    case OpcodeType::STS_MACH_R:
    case OpcodeType::STS_MACL_R: x = {1u << n, 0, static_cast<sint32>(n), 1, true}; return true;
    case OpcodeType::STS_PR_R: x = {1u << n, (1u << n) | bPR, 0xFF, 1, true}; return true;
    case OpcodeType::LDS_MACH_R:
    case OpcodeType::LDS_MACL_R: x = {1u << m, 1u << m, 0xFF, 1, true}; return true;
    case OpcodeType::LDS_PR_R: x = {1u << m, (1u << m) | bPR, 0xFF, 1, true}; return true;
    case OpcodeType::MUL:
    case OpcodeType::DMULS:
    case OpcodeType::DMULU: x = {(1u << n) | (1u << rm4), (1u << n) | (1u << rm4), 0xFF, 3, false}; return true;
    case OpcodeType::MULS:
    case OpcodeType::MULU: x = {(1u << n) | (1u << rm4), (1u << n) | (1u << rm4), 0xFF, 1, false}; return true;
    default: return false;
    }
}

// Branches compiled in host code (the block ends with them)
enum class BranchKind { None, BT, BF, BTS, BFS, BRA, BSR, BRAF, BSRF, JMP, JSR, RTS };
BranchKind BranchOf(OpcodeType op) {
    switch (op) {
    case OpcodeType::BT: return BranchKind::BT;
    case OpcodeType::BF: return BranchKind::BF;
    case OpcodeType::BTS: return BranchKind::BTS;
    case OpcodeType::BFS: return BranchKind::BFS;
    case OpcodeType::BRA: return BranchKind::BRA;
    case OpcodeType::BSR: return BranchKind::BSR;
    case OpcodeType::BRAF: return BranchKind::BRAF;
    case OpcodeType::BSRF: return BranchKind::BSRF;
    case OpcodeType::JMP: return BranchKind::JMP;
    case OpcodeType::JSR: return BranchKind::JSR;
    case OpcodeType::RTS: return BranchKind::RTS;
    default: return BranchKind::None;
    }
}

// Host registers: x19 = Context, x20 = cycles executed, x21 = slice end, w22 = SR, w0-w8 temporaries (w3/w4 in the
// native instructions), the SH-2 registers the block uses in w9-w17 and w23-w28 (reloaded after every interpreter
// call, so caller-saved ones are fine)
constexpr uint32 kCtx = 19, kCycles = 20, kLimit = 21, kSR = 22, kT0 = 3, kT1 = 4;
constexpr uint32 kTarget = 29; // delayed branch target (x29 is saved by the prologue)
constexpr uint32 kHostList[] = {9, 10, 11, 12, 13, 14, 15, 16, 17, 23, 24, 25, 26, 27, 28};
constexpr uint32 kHostRegs = 15;

// Instructions after which a block ends (the chain re-checks for idle loops, delay slots, exceptions)
enum class Flow { None, Branch, DelayedBranch, CondDelayed };
Flow FlowOf(OpcodeType op) {
    switch (op) {
    case OpcodeType::BF:
    case OpcodeType::BT:
    case OpcodeType::TRAPA:
    case OpcodeType::SLEEP:
    case OpcodeType::Illegal: return Flow::Branch;
    case OpcodeType::BFS:
    case OpcodeType::BTS: return Flow::CondDelayed;
    case OpcodeType::BRA:
    case OpcodeType::BRAF:
    case OpcodeType::BSR:
    case OpcodeType::BSRF:
    case OpcodeType::JMP:
    case OpcodeType::JSR:
    case OpcodeType::RTS:
    case OpcodeType::RTE: return Flow::DelayedBranch;
    default: return Flow::None;
    }
}

class Compiler {
public:
    Emitter e;
    uint32 host[16];

    uint32 R(uint32 n) const {
        return host[n];
    }

    // T = bit 0 of `bit`
    void SetT(uint32 bit) {
        e.InsertBit0(kSR, bit);
    }
    void SetTCond(Cond c) {
        e.Cset(kT0, c);
        SetT(kT0);
    }

    void Emit(const Instr &in) {
        const uint16 op = in.opcode;
        const uint32 n = R(Rn(op)), m = R(Rm(op));
        switch (in.op) {
        case OpcodeType::NOP: break;
        case OpcodeType::CLRT: SetT(WZR); break;
        case OpcodeType::SETT:
            e.MovImm(kT0, 1);
            SetT(kT0);
            break;
        case OpcodeType::MOV_R: e.Mov(n, m); break;
        case OpcodeType::MOV_I: e.MovImm(n, SImm8(op)); break;
        case OpcodeType::MOVA: e.MovImm(R(0), (in.pc & ~3u) + (UImm8(op) << 2) + 4u); break;
        case OpcodeType::MOVT: e.Bit0(n, kSR); break;
        case OpcodeType::EXTUB: e.Uxtb(n, m); break;
        case OpcodeType::EXTUW: e.Uxth(n, m); break;
        case OpcodeType::EXTSB: e.Sxtb(n, m); break;
        case OpcodeType::EXTSW: e.Sxth(n, m); break;
        case OpcodeType::SWAPB:
            e.Rev16(kT0, m);
            e.Mov(n, m);
            e.InsertLow16(n, kT0);
            break;
        case OpcodeType::SWAPW: e.Ror(n, m, 16); break;
        case OpcodeType::XTRCT: e.Extr(n, m, n, 16); break;
        case OpcodeType::ADD: e.Add(n, n, m); break;
        case OpcodeType::ADD_I: {
            const sint32 imm = static_cast<sint32>(SImm8(op));
            if (imm >= 0) {
                e.AddImm(n, n, imm);
            } else {
                e.SubImm(n, n, -imm);
            }
            break;
        }
        case OpcodeType::ADDC:
            e.Bit0(kT0, kSR);
            e.CmpImm(kT0, 1); // C = T
            e.Adcs(n, n, m);
            SetTCond(HS);
            break;
        case OpcodeType::SUB: e.Sub(n, n, m); break;
        case OpcodeType::SUBC:
            e.Bit0(kT0, kSR);
            e.Subs(WZR, WZR, kT0); // C = !T (no borrow)
            e.Sbcs(n, n, m);
            SetTCond(LO);
            break;
        case OpcodeType::NEGC:
            e.Bit0(kT0, kSR);
            e.Subs(WZR, WZR, kT0);
            e.Sbcs(n, WZR, m);
            SetTCond(LO);
            break;
        case OpcodeType::AND_R: e.And(n, n, m); break;
        case OpcodeType::OR_R: e.Orr(n, n, m); break;
        case OpcodeType::XOR_R: e.Eor(n, n, m); break;
        case OpcodeType::AND_I:
            e.MovImm(kT0, UImm8(op));
            e.And(R(0), R(0), kT0);
            break;
        case OpcodeType::OR_I:
            e.MovImm(kT0, UImm8(op));
            e.Orr(R(0), R(0), kT0);
            break;
        case OpcodeType::XOR_I:
            e.MovImm(kT0, UImm8(op));
            e.Eor(R(0), R(0), kT0);
            break;
        case OpcodeType::NOT: e.Mvn(n, m); break;
        case OpcodeType::NEG: e.Neg(n, m); break;
        case OpcodeType::ROTL:
            e.Lsr(kT0, n, 31);
            e.Ror(n, n, 31);
            SetT(kT0);
            break;
        case OpcodeType::ROTR:
            e.Bit0(kT0, n);
            e.Ror(n, n, 1);
            SetT(kT0);
            break;
        case OpcodeType::ROTCL:
            e.Lsr(kT0, n, 31);
            e.Bit0(kT1, kSR);
            e.Lsl(n, n, 1);
            e.Orr(n, n, kT1);
            SetT(kT0);
            break;
        case OpcodeType::ROTCR:
            e.Bit0(kT0, n);
            e.Bit0(kT1, kSR);
            e.Lsl(kT1, kT1, 31);
            e.Lsr(n, n, 1);
            e.Orr(n, n, kT1);
            SetT(kT0);
            break;
        case OpcodeType::SHAL:
        case OpcodeType::SHLL:
            e.Lsr(kT0, n, 31);
            e.Lsl(n, n, 1);
            SetT(kT0);
            break;
        case OpcodeType::SHAR:
            e.Bit0(kT0, n);
            e.Asr(n, n, 1);
            SetT(kT0);
            break;
        case OpcodeType::SHLR:
            e.Bit0(kT0, n);
            e.Lsr(n, n, 1);
            SetT(kT0);
            break;
        case OpcodeType::SHLL2: e.Lsl(n, n, 2); break;
        case OpcodeType::SHLL8: e.Lsl(n, n, 8); break;
        case OpcodeType::SHLL16: e.Lsl(n, n, 16); break;
        case OpcodeType::SHLR2: e.Lsr(n, n, 2); break;
        case OpcodeType::SHLR8: e.Lsr(n, n, 8); break;
        case OpcodeType::SHLR16: e.Lsr(n, n, 16); break;
        case OpcodeType::DT:
            e.SubImm(n, n, 1);
            e.CmpImm(n, 0);
            SetTCond(EQ);
            break;
        case OpcodeType::CMP_EQ_I:
            e.MovImm(kT0, SImm8(op));
            e.Cmp(R(0), kT0);
            SetTCond(EQ);
            break;
        case OpcodeType::CMP_EQ_R:
            e.Cmp(n, m);
            SetTCond(EQ);
            break;
        case OpcodeType::CMP_GE:
            e.Cmp(n, m);
            SetTCond(GE);
            break;
        case OpcodeType::CMP_GT:
            e.Cmp(n, m);
            SetTCond(GT);
            break;
        case OpcodeType::CMP_HI:
            e.Cmp(n, m);
            SetTCond(HI);
            break;
        case OpcodeType::CMP_HS:
            e.Cmp(n, m);
            SetTCond(HS);
            break;
        case OpcodeType::CMP_PL:
            e.CmpImm(n, 0);
            SetTCond(GT);
            break;
        case OpcodeType::CMP_PZ:
            e.CmpImm(n, 0);
            SetTCond(GE);
            break;
        case OpcodeType::CMP_STR:
            // T = some byte of Rn ^ Rm is zero: (v - 0x01010101) & ~v & 0x80808080 != 0
            e.Eor(kT0, n, m);
            e.MovImm(kT1, 0x01010101);
            e.Sub(kT1, kT0, kT1);
            e.Bic(kT1, kT1, kT0);
            e.MovImm(kT0, 0x80808080);
            e.Tst(kT1, kT0);
            SetTCond(NE);
            break;
        case OpcodeType::TST_R:
            e.Tst(n, m);
            SetTCond(EQ);
            break;
        case OpcodeType::TST_I:
            e.MovImm(kT0, UImm8(op));
            e.Tst(R(0), kT0);
            SetTCond(EQ);
            break;
        default: break;
        }
    }
};

} // namespace

bool Cache::Supported() {
    return true;
}

Cache::Cache() {
    void *p = mmap(nullptr, kCodeSize, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (p != MAP_FAILED) {
        m_code = static_cast<uint8 *>(p);
        m_codeSize = kCodeSize;
    }
    m_slots = static_cast<Block **>(std::calloc(kSlots, sizeof(Block *))); // zero pages until used
}

Cache::~Cache() {
    if (m_code != nullptr) {
        munmap(m_code, m_codeSize);
    }
    std::free(m_slots);
}

Block **Cache::Slot(uint32 pc) {
    return &m_slots[SlotIndex(Normalize(pc))];
}

void Cache::Flush() {
    m_blocks.clear();
    if (m_slots != nullptr) {
        std::memset(m_slots, 0, kSlots * sizeof(Block *));
    }
    m_pages.clear();
    std::fill(m_codeBits.begin(), m_codeBits.end(), 0);
    m_codeUsed = 0;
    ++stats.flushes;
}

void Cache::MarkCode(uint32 first, uint32 last) {
    for (uint32 a = first; a <= last; a += 2) {
        const sint32 i = CodeIndex(a);
        if (i >= 0) {
            m_codeBits[i >> 6] |= 1ull << (i & 63);
        }
    }
}

// A write to work RAM (any size, at busAddress): the halfwords it can touch are checked against the code bitmap
void Cache::CodeWritten(uint32 busAddress) {
    const uint32 norm = Normalize(busAddress & ~1u);
    for (uint32 h = norm; h <= norm + 2; h += 2) {
        const sint32 i = CodeIndex(h);
        if (i < 0 || !((m_codeBits[i >> 6] >> (i & 63)) & 1)) {
            continue;
        }
        m_codeBits[i >> 6] &= ~(1ull << (i & 63));
        ++m_epoch; // a running block that called the interpreter for this write stops after it
        // Drop every block (or "not compilable here" entry) covering h; the Block objects stay allocated until the
        // next flush (one may be running)
        const auto it = m_pages.find(h >> 12);
        if (it == m_pages.end()) {
            continue;
        }
        std::vector<Range> &ranges = it->second;
        for (size_t k = 0; k < ranges.size();) {
            const Range r = ranges[k];
            if (r.first <= h && h <= r.last) {
                Block **slot = Slot(r.pc);
                if (*slot == kNoBlock || (*slot != nullptr && (*slot)->startPC == r.pc)) {
                    *slot = nullptr;
                }
                ++stats.invalidated;
                // also unregister it from the other page it may span
                for (uint32 page = r.first >> 12; page <= (r.last >> 12); ++page) {
                    if (page == (h >> 12)) {
                        continue;
                    }
                    auto other = m_pages.find(page);
                    if (other != m_pages.end()) {
                        std::erase_if(other->second, [&](const Range &x) { return x.pc == r.pc; });
                    }
                }
                ranges[k] = ranges.back();
                ranges.pop_back();
            } else {
                ++k;
            }
        }
    }
}

const Block *Cache::Get(uint32 pc, Fetch fetch, void *ctx, bool compile) {
    if (m_slots == nullptr || !CompilableAddress(pc)) {
        return nullptr;
    }
    const Block *block = *Slot(pc);
    if (block == kNoBlock) {
        return nullptr;
    }
    // A block compiled through another mirror of the same memory (cached/cache-through) may embed its own PC (MOVA)
    if (block == nullptr || block->startPC != pc) {
        if (!compile) {
            return nullptr;
        }
        block = Compile(pc, fetch, ctx);
    }
    return block == kNoBlock ? nullptr : block;
}

const Block *Cache::Compile(uint32 pc, Fetch fetch, void *ctx) {
    // lastCovered: the last halfword the block depends on (its instructions and prefetched opcode pairs)
    auto remember = [&](Block *b, uint32 lastCovered) -> Block * {
        *Slot(pc) = b;
        const Range r{pc, Normalize(pc), Normalize(lastCovered)};
        for (uint32 page = r.first >> 12; page <= (r.last >> 12); ++page) {
            m_pages[page].push_back(r);
        }
        MarkCode(r.first, r.last);
        return b;
    };
    if (m_code == nullptr || !compileEnabled) {
        return remember(kNoBlock, pc);
    }

    // Collect the block: native (host code) or called (interpreter) instructions, up to the end of its control flow
    struct Item {
        Instr in;
        bool native;   // ALU in host code
        bool mem;      // MOV memory access: direct WRAM-H path, interpreter otherwise
        MemOp m;
        uint32 regs;   // registers used (native/mem); for ALU also the load-use stall check
        bool usesT;    // writes T
        Flow flow;
        bool special = false; // MUL/MAC registers/CLRMAC/STS/LDS
        Special sp{};
        bool mac = false;     // MAC.L / MAC.W: direct WRAM-H path, interpreter otherwise
        uint32 macSize = 0;
        BranchKind branch = BranchKind::None;
        bool readsT = false;
        bool isSlot = false;  // delay slot of the native branch before it
    };
    std::vector<Item> items;
    uint32 usedRegs = 0;
    bool usesT = false;
    uint32 natives = 0;
    for (uint32 addr = pc; items.size() < kMaxInstrs; addr += 2) {
        if (!CompilableAddress(addr) || Normalize(addr) - Normalize(pc) != addr - pc || Page(addr) > Page(pc) + 1) {
            break;
        }
        const uint16 opcode = fetch(ctx, addr);
        const OpcodeType op = DecodeTable::s_instance.opcodes[0][opcode];
        Item item{{op, opcode, addr}, false, false, {}, 0, false, FlowOf(op)};
        Uses uses;
        bool t;
        if (IsSupported(op, uses, t)) {
            item.native = true;
            item.regs = UsedRegs(uses, opcode);
            item.usesT = t;
        } else if (DescribeMem(op, opcode, addr, item.m)) {
            item.mem = true;
            item.regs = item.m.regs;
        } else if (nativeSpecials && DescribeSpecial(op, opcode, item.sp)) {
            item.special = true;
            item.regs = item.sp.regs;
        } else if (nativeMac && (op == OpcodeType::MACL || op == OpcodeType::MACW)) {
            item.mac = true;
            item.macSize = op == OpcodeType::MACL ? 4 : 2;
            item.regs = (1u << Rn(opcode)) | (1u << Rm(opcode));
            item.readsT = true; // SR.S
        } else if (nativeBranches && BranchOf(op) != BranchKind::None) {
            item.branch = BranchOf(op);
            const BranchKind b = item.branch;
            if (b == BranchKind::BRAF || b == BranchKind::BSRF || b == BranchKind::JMP || b == BranchKind::JSR) {
                item.regs = 1u << Rn(opcode);
            }
            item.readsT = b == BranchKind::BT || b == BranchKind::BF || b == BranchKind::BTS || b == BranchKind::BFS;
        }
        if (item.native || item.mem || item.special || item.mac || item.branch != BranchKind::None) {
            if (std::popcount(usedRegs | item.regs) > (int)kHostRegs) {
                break;
            }
            usedRegs |= item.regs;
            usesT |= item.usesT || item.readsT;
            ++natives;
        }
        items.push_back(item);
        if (item.branch != BranchKind::None && item.flow == Flow::DelayedBranch) {
            // Native delayed branch: its slot in host code when it is a register instruction, else an interpreter call
            if (CompilableAddress(addr + 2) && Page(addr + 2) <= Page(pc) + 1) {
                const uint16 slot = fetch(ctx, addr + 2);
                const OpcodeType op1 = DecodeTable::s_instance.opcodes[1][slot];
                const OpcodeType op0 = DecodeTable::s_instance.opcodes[0][slot];
                Item si{{op1, slot, addr + 2}, false, false, {}, 0, false, Flow::Branch};
                si.isSlot = true;
                Uses su;
                bool st;
                if (!nativeSlots) {
                    // slot through the interpreter
                } else if (op1 != OpcodeType::IllegalSlot && op0 != OpcodeType::MOVA && IsSupported(op0, su, st) &&
                    std::popcount(usedRegs | UsedRegs(su, slot)) <= (int)kHostRegs) {
                    si.in.op = op0;
                    si.native = true;
                    si.regs = UsedRegs(su, slot);
                    si.usesT = st;
                } else if (op1 != OpcodeType::IllegalSlot && DescribeSpecial(op0, slot, si.sp) &&
                           std::popcount(usedRegs | si.sp.regs) <= (int)kHostRegs) {
                    si.in.op = op0;
                    si.special = true;
                    si.regs = si.sp.regs;
                }
                usedRegs |= si.regs;
                usesT |= si.usesT;
                items.push_back(si);
            }
            break;
        }
        if (item.flow == Flow::DelayedBranch) {
            // the delay slot instruction, run by the interpreter in its delay-slot form
            if (CompilableAddress(addr + 2) && Page(addr + 2) <= Page(pc) + 1) {
                const uint16 slot = fetch(ctx, addr + 2);
                items.push_back({{DecodeTable::s_instance.opcodes[1][slot], slot, addr + 2}, false, false, {}, 0, false,
                                 Flow::Branch});
            }
            break;
        }
        if (item.flow != Flow::None) {
            break;
        }
    }
    // A block of only interpreter calls gains nothing over the chain
    if (natives == 0) {
        return remember(kNoBlock, pc);
    }

    Compiler c;
    Emitter &e = c.e;
    {
        uint32 next = 0;
        for (uint32 r = 0; r < 16; ++r) {
            c.host[r] = (usedRegs >> r) & 1 ? kHostList[next++] : WZR;
        }
    }
    auto loadRegs = [&] {
        e.LdrX(0, kCtx, offsetof(Context, R));
        for (uint32 r = 0; r < 16; ++r) {
            if ((usedRegs >> r) & 1) {
                e.LdrW(c.host[r], 0, r * 4);
            }
        }
        if (usesT) {
            e.LdrX(0, kCtx, offsetof(Context, SR));
            e.LdrW(kSR, 0, 0);
        }
    };
    // Registers changed in host code since the SH2 object was last updated (stored before interpreter calls)
    uint32 dirty = 0;
    bool dirtyT = false;
    auto storeRegsMask = [&](uint32 mask, bool t) {
        if (mask != 0) {
            e.LdrX(0, kCtx, offsetof(Context, R));
            for (uint32 r = 0; r < 16; ++r) {
                if ((mask >> r) & 1) {
                    e.StrW(c.host[r], 0, r * 4);
                }
            }
        }
        if (t) {
            e.LdrX(0, kCtx, offsetof(Context, SR));
            e.StrW(kSR, 0, 0);
        }
    };
    auto storeRegs = [&] { storeRegsMask(usedRegs, usesT); };
    auto storeConst32 = [&](size_t ptrField, uint32 value) {
        e.MovImm(1, value);
        e.LdrX(0, kCtx, ptrField);
        e.StrW(1, 0, 0);
    };
    auto storeWb = [&](uint32 value) {
        e.MovImm(1, value);
        e.LdrX(0, kCtx, offsetof(Context, wbReg));
        e.Strb(1, 0, 0);
    };

    // Prologue: save x19-x30, load the state
    e.StpPre(29, 30, 31, -96);
    e.Stp(19, 20, 31, 16);
    e.Stp(21, 22, 31, 32);
    e.Stp(23, 24, 31, 48);
    e.Stp(25, 26, 31, 64);
    e.Stp(27, 28, 31, 80);
    e.MovX(kCtx, 0);
    e.LdrX(0, kCtx, offsetof(Context, cycles));
    e.LdrX(kCycles, 0, 0);
    e.LdrX(kLimit, kCtx, offsetof(Context, limit));
    loadRegs();

    // Compile-time state that the SH2 object only gets at calls and exits
    constexpr sint32 kWbUnknown = -1;    // m_wbReg: in the SH2 object (after a call or at the start)
    sint32 wb = kWbUnknown;              // otherwise the value it has now (0xFF: none)
    bool afterCall = false;              // the previous instruction was an interpreter call
    bool fetchPending = false;           // the pipeline holds this prefetched pair (not stored yet)
    uint32 fetchValue = 0;
    uint32 lastCovered = pc;
    uint32 calls = 0;

    // Exits. A native exit stores the host state; a call exit only the cycles (the interpreter keeps the rest in the
    // SH2 object). Both record the last instruction run and return through the common epilogue.
    bool allowFalse = false; // the last instruction was LDS/STS (interrupts blocked on the next one)
    struct Exit {
        Emitter::Label label;
        bool native;
        uint32 lastPC, nextPC;
        bool storeFetch;
        uint32 fetched;
        sint32 wb;
        bool allowFalse = false;
        bool pcFromTarget = false; // PC = kTarget (end of a delay slot)
        bool delay = false;        // stopped between a branch and its slot: m_delaySlot, target = kTarget
        uint8 post = 0;
    };
    std::vector<std::unique_ptr<Exit>> exits;
    auto nativeExit = [&](uint32 lastPC) -> Exit & {
        exits.push_back(std::unique_ptr<Exit>(
            new Exit{{}, true, lastPC, lastPC + 2, fetchPending, fetchValue, wb, allowFalse}));
        return *exits.back();
    };
    auto callExit = [&](uint32 lastPC) -> Emitter::Label & {
        exits.push_back(std::unique_ptr<Exit>(new Exit{{}, false, lastPC, 0, false, 0, kWbUnknown}));
        return exits.back()->label;
    };
    // Out-of-line interpreter calls for memory accesses outside the direct path (emitted after the exits)
    struct SlowPath {
        Emitter::Label label, resume;
        uint32 addr;
        bool fetchPending;
        uint32 fetchValue;
        sint32 wb;
        uint32 dirty; // registers to store (w5 holds a computed address; the base register may be pre-decremented)
        bool dirtyT;
    };
    std::vector<std::unique_ptr<SlowPath>> slowPaths;
    struct HitPath {
        Emitter::Label label;
        Emitter::Label *exit;
    };
    std::vector<std::unique_ptr<HitPath>> hitPaths;
    auto macOffset = [&](uint32 dst, uint32 addrReg, uint32 size) {
        e.AndRun(dst, addrReg, size == 4 ? 2 : 1, size == 4 ? 18 : 19);
    };

    // Load-use stall of the registers in `mask` (counted `times` times)
    auto stall = [&](uint32 mask, uint32 times) {
        if (mask == 0) {
            return;
        }
        if (wb == kWbUnknown) {
            e.LdrX(0, kCtx, offsetof(Context, wbReg));
            e.Ldrb(1, 0, 0);
            e.MovImm(2, mask);
            e.Lsrv(2, 2, 1);
            e.And1(2, 2);
            if (times == 2) {
                e.Lsl(2, 2, 1);
            }
            e.AddX(kCycles, kCycles, 2);
        } else if (wb < 32 && ((mask >> wb) & 1)) {
            e.AddXImm(kCycles, kCycles, times);
        }
    };
    // Everything an interpreter call needs in the SH2 object
    auto beforeCall = [&](uint32 addr, bool setPC, bool reload) {
        storeRegsMask(dirty, dirtyT);
        if (fetchPending) {
            storeConst32(offsetof(Context, fetched), fetchValue);
        }
        if (wb != kWbUnknown) {
            storeWb(static_cast<uint32>(wb));
        }
        if (setPC) {
            storeConst32(offsetof(Context, PC), addr);
        }
        e.LdrX(0, kCtx, offsetof(Context, cycles));
        e.StrX(kCycles, 0, 0);
        e.LdrX(0, kCtx, offsetof(Context, sh2));
        e.LdrX(8, kCtx, offsetof(Context, interpret));
        e.Blr(8);
        e.AddX(kCycles, kCycles, 0);
        if (reload) {
            loadRegs();
        }
        dirty = 0;
        dirtyT = false;
    };
    // After a call that may have accessed memory: stop where the chain would
    auto afterCallChecks = [&](uint32 addr, Emitter::Label &x) {
        // bus wait: the instruction didn't run and will be retried
        e.LdrX(0, kCtx, offsetof(Context, PC));
        e.LdrW(1, 0, 0);
        e.MovImm(2, addr + 2);
        e.Cmp(1, 2);
        e.BCond(NE, x);
        // an interrupt due now (pending and allowed)
        e.LdrX(0, kCtx, offsetof(Context, intr));
        e.Ldrh(1, 0, 0);
        e.MovImm(2, 0x0101);
        e.Cmp(1, 2);
        e.BCond(EQ, x);
        // a write that dropped compiled code (possibly this block's)
        e.LdrX(0, kCtx, offsetof(Context, epoch));
        e.LdrW(1, 0, 0);
        e.LdrW(2, kCtx, offsetof(Context, startEpoch));
        e.Cmp(1, 2);
        e.BCond(NE, x);
    };

    for (size_t k = 0; k < items.size(); ++k) {
        const Item &it = items[k];
        const uint32 addr = it.in.pc;
        lastCovered = std::max(lastCovered, addr);
        allowFalse = false; // only the instruction right after LDS/STS has interrupts blocked
        const bool host = it.native || it.mem || it.special || it.mac || it.branch != BranchKind::None;
        if (host) {
            if (afterCall) {
                // the chain allows interrupts again before every instruction
                e.LdrX(0, kCtx, offsetof(Context, intr));
                e.MovImm(1, 1);
                e.Strb(1, 0, 1);
            }
            if ((addr & 2) == 0) {
                fetchPending = true;
                fetchValue = (static_cast<uint32>(fetch(ctx, addr)) << 16) | fetch(ctx, addr + 2);
                lastCovered = std::max(lastCovered, addr + 2);
            }
        }

        if (it.native) {
            stall(it.regs, 1);
            c.Emit(it.in);
            e.AddXImm(kCycles, kCycles, 1);
            wb = 0xFF;
            allowFalse = false;
            dirty |= it.regs;
            dirtyT |= it.usesT;
        } else if (it.special) {
            const Special &x = it.sp;
            const uint16 op = it.in.opcode;
            stall(x.wbMask, 1);
            const uint32 n = c.R(Rn(op)), m4 = c.R(Rm(op));
            switch (it.in.op) {
            case OpcodeType::CLRMAC:
                e.LdrX(0, kCtx, offsetof(Context, MAC));
                e.StrXZero(0);
                break;
            case OpcodeType::STS_MACH_R:
                e.LdrX(0, kCtx, offsetof(Context, MAC));
                e.LdrW(n, 0, 4);
                break;
            case OpcodeType::STS_MACL_R:
                e.LdrX(0, kCtx, offsetof(Context, MAC));
                e.LdrW(n, 0, 0);
                break;
            case OpcodeType::STS_PR_R:
                e.LdrX(0, kCtx, offsetof(Context, PR));
                e.LdrW(n, 0, 0);
                break;
            case OpcodeType::LDS_MACH_R:
                e.LdrX(0, kCtx, offsetof(Context, MAC));
                e.StrW(n, 0, 4);
                break;
            case OpcodeType::LDS_MACL_R:
                e.LdrX(0, kCtx, offsetof(Context, MAC));
                e.StrW(n, 0, 0);
                break;
            case OpcodeType::LDS_PR_R:
                e.LdrX(0, kCtx, offsetof(Context, PR));
                e.StrW(n, 0, 0);
                break;
            case OpcodeType::MUL:
                e.Mul(1, m4, n);
                e.LdrX(0, kCtx, offsetof(Context, MAC));
                e.StrW(1, 0, 0);
                break;
            case OpcodeType::MULS:
                e.Sxth(1, m4);
                e.Sxth(2, n);
                e.Mul(1, 1, 2);
                e.LdrX(0, kCtx, offsetof(Context, MAC));
                e.StrW(1, 0, 0);
                break;
            case OpcodeType::MULU:
                e.Uxth(1, m4);
                e.Uxth(2, n);
                e.Mul(1, 1, 2);
                e.LdrX(0, kCtx, offsetof(Context, MAC));
                e.StrW(1, 0, 0);
                break;
            case OpcodeType::DMULS:
                e.Smull(1, m4, n);
                e.LdrX(0, kCtx, offsetof(Context, MAC));
                e.StrX(1, 0, 0);
                break;
            case OpcodeType::DMULU:
                e.Umull(1, m4, n);
                e.LdrX(0, kCtx, offsetof(Context, MAC));
                e.StrX(1, 0, 0);
                break;
            default: break;
            }
            e.AddXImm(kCycles, kCycles, x.cycles);
            wb = x.wbAfter;
            allowFalse = x.allowFalse;
            dirty |= it.regs;
        } else if (it.mac) {
            // MAC.L / MAC.W @Rm+,@Rn+: both operands in WRAM-H's cached area and no saturation (SR.S = 0); otherwise
            // the interpreter (its slow path)
            const uint16 op = it.in.opcode;
            const uint32 rn = Rn(op), rm = Rm(op), size = it.macSize;
            auto slow = std::unique_ptr<SlowPath>(
                new SlowPath{{}, {}, addr, fetchPending, fetchValue, wb, dirty, dirtyT});
            e.Mov(5, c.R(rn)); // operand 2 address (read first)
            if (rm == rn) {
                e.AddImm(6, c.R(rn), size);
            } else {
                e.Mov(6, c.R(rm));
            }
            e.Lsr(1, 5, 25);
            e.CmpImm(1, 3);
            e.BCond(NE, slow->label);
            e.Lsr(1, 6, 25);
            e.CmpImm(1, 3);
            e.BCond(NE, slow->label);
            e.Ubfm(1, kSR, 1, 1); // SR.S
            e.Cbnz(1, slow->label);
            stall((1u << rn) | (1u << rm), 1);
            e.LdrX(2, kCtx, offsetof(Context, wramHigh));
            macOffset(1, 5, size);
            macOffset(4, 6, size);
            if (size == 4) {
                e.LdrWReg(3, 2, 1);
                e.Rev(3, 3);
                e.LdrWReg(7, 2, 4);
                e.Rev(7, 7);
                e.Smull(1, 7, 3);
            } else {
                e.LdrhReg(3, 2, 1);
                e.Rev16(3, 3);
                e.Sxth(3, 3);
                e.LdrhReg(7, 2, 4);
                e.Rev16(7, 7);
                e.Sxth(7, 7);
                e.Mul(1, 7, 3);
                e.Sxtw(1, 1);
            }
            e.LdrX(0, kCtx, offsetof(Context, MAC));
            e.LdrX(4, 0, 0);
            e.AddX(4, 4, 1);
            e.StrX(4, 0, 0);
            e.AddImm(c.R(rn), c.R(rn), size);
            e.AddImm(c.R(rm), c.R(rm), size);
            e.AddXImm(kCycles, kCycles, 3);
            wb = 0xFF;
            allowFalse = false;
            dirty |= it.regs;
            e.Bind(slow->resume);
            slowPaths.push_back(std::move(slow));
        } else if (it.branch != BranchKind::None) {
            const uint16 op = it.in.opcode;
            const BranchKind b = it.branch;
            const uint32 dispS8 = static_cast<uint32>(static_cast<sint32>(static_cast<sint8>(op & 0xFF)) * 2);
            sint32 d12 = op & 0xFFF;
            if (d12 & 0x800) {
                d12 -= 0x1000;
            }
            const uint32 dispS12 = static_cast<uint32>(d12 * 2);
            const uint32 bPR = 1u << kWBPR;
            allowFalse = false;
            if (b == BranchKind::BT || b == BranchKind::BF) {
                // taken: 3 cycles, PC = target, pipeline refilled; not taken: 1 cycle
                wb = 0xFF;
                Exit &taken = nativeExit(addr);
                taken.nextPC = addr + 4 + dispS8;
                taken.post = 1;
                e.Bit0(1, kSR);
                if (b == BranchKind::BF) {
                    e.MovImm(2, 1);
                    e.Eor(1, 1, 2);
                }
                Emitter::Label notTaken;
                e.CbzW(1, notTaken);
                e.AddXImm(kCycles, kCycles, 3);
                e.B(taken.label);
                e.Bind(notTaken);
                e.AddXImm(kCycles, kCycles, 1);
                e.B(nativeExit(addr).label);
                continue;
            }
            if (b == BranchKind::BTS || b == BranchKind::BFS) {
                // taken: 2 cycles, stop before the slot (the chain runs it); not taken: 1 cycle
                wb = 0xFF;
                Exit &taken = nativeExit(addr);
                taken.delay = true;
                e.Bit0(1, kSR);
                if (b == BranchKind::BFS) {
                    e.MovImm(2, 1);
                    e.Eor(1, 1, 2);
                }
                Emitter::Label notTaken;
                e.CbzW(1, notTaken);
                e.AddXImm(kCycles, kCycles, 2);
                e.MovImm(kTarget, addr + 4 + dispS8);
                e.B(taken.label);
                e.Bind(notTaken);
                e.AddXImm(kCycles, kCycles, 1);
                e.B(nativeExit(addr).label);
                continue;
            }
            // Unconditional delayed branches: target in kTarget, PR for calls, 2 cycles plus the load-use checks
            const uint32 rm = Rn(op);
            switch (b) {
            case BranchKind::BRA: break;
            case BranchKind::BSR: stall(bPR, 1); break;
            case BranchKind::BRAF:
            case BranchKind::JMP: stall(1u << rm, 1); break;
            case BranchKind::BSRF:
            case BranchKind::JSR: stall((1u << rm) | bPR, 1); break;
            case BranchKind::RTS: stall(bPR, 1); break;
            default: break;
            }
            switch (b) {
            case BranchKind::BRA:
            case BranchKind::BSR: e.MovImm(kTarget, addr + 4 + dispS12); break;
            case BranchKind::BRAF:
            case BranchKind::BSRF:
                e.MovImm(kTarget, addr + 4);
                e.Add(kTarget, kTarget, c.R(rm));
                break;
            case BranchKind::JMP:
            case BranchKind::JSR: e.Mov(kTarget, c.R(rm)); break;
            case BranchKind::RTS:
                e.LdrX(0, kCtx, offsetof(Context, PR));
                e.LdrW(kTarget, 0, 0);
                break;
            default: break;
            }
            if (b == BranchKind::BSR || b == BranchKind::BSRF || b == BranchKind::JSR) {
                e.MovImm(1, addr + 4);
                e.LdrX(0, kCtx, offsetof(Context, PR));
                e.StrW(1, 0, 0);
            }
            e.AddXImm(kCycles, kCycles, 2);
            wb = 0xFF;
            // the slice may end before the slot: leave with the delay slot pending
            Exit &x = nativeExit(addr);
            x.delay = true;
            e.CmpX(kCycles, kLimit);
            e.BCond(HS, x.label);
            if (k + 1 == items.size()) {
                e.B(nativeExit(addr).label); // no slot compiled: same as stopping before it
                exits.back()->delay = true;
                continue;
            }
            continue;
        } else if (it.isSlot && (it.native || it.special)) {
            // unreachable: handled with the host-code kinds above
        } else if (it.mem) {
            const MemOp &m = it.m;
            const uint32 rb = m.base == MemOp::kGBR || m.base == MemOp::kPC ? WZR : c.R(m.baseReg);
            // address -> w5 (kept for the code-write check)
            switch (m.base) {
            case MemOp::kRm:
            case MemOp::kRn: e.Mov(5, rb); break;
            case MemOp::kRmR0:
            case MemOp::kRnR0: e.Add(5, rb, c.R(0)); break;
            case MemOp::kRmDisp:
            case MemOp::kRnDisp: e.AddImm(5, rb, m.disp); break;
            case MemOp::kGBR:
                e.LdrX(0, kCtx, offsetof(Context, GBR));
                e.LdrW(5, 0, 0);
                e.AddImm(5, 5, m.disp);
                break;
            case MemOp::kPC: e.MovImm(5, m.disp); break;
            }
            if (m.preDec) {
                e.SubImm(5, 5, m.size);
            }
            auto slow = std::unique_ptr<SlowPath>(
                new SlowPath{{}, {}, addr, fetchPending, fetchValue, wb, dirty | it.regs, dirtyT});
            // previous fetch state for the call: this instruction's fetch is the interpreter's (FetchInstruction)
            // direct path: cached area (partition 000) of WRAM-H, 06000000-07FFFFFF
            e.Lsr(1, 5, 25);
            e.CmpImm(1, 3);
            e.BCond(NE, slow->label);
            stall(m.wbRegs, m.wbTimes); // uses x0-x2: before the offset and base
            e.AndRun(1, 5, m.size == 4 ? 2 : m.size == 2 ? 1 : 0, m.size == 4 ? 18 : m.size == 2 ? 19 : 20);
            e.LdrX(2, kCtx, offsetof(Context, wramHigh));
            if (!m.store) {
                const uint32 t = c.R(m.target);
                if (m.size == 4) {
                    e.LdrWReg(3, 2, 1);
                    e.Rev(t, 3);
                } else if (m.size == 2) {
                    e.LdrhReg(3, 2, 1);
                    e.Rev16(3, 3);
                    e.Sxth(t, 3);
                } else {
                    e.LdrbReg(3, 2, 1);
                    e.Sxtb(t, 3);
                }
                if (m.postInc) {
                    e.AddImm(c.R(m.baseReg), c.R(m.baseReg), m.size);
                }
            } else {
                const uint32 v = c.R(m.value);
                if (m.size == 4) {
                    e.Rev(3, v);
                    e.StrWReg(3, 2, 1);
                } else if (m.size == 2) {
                    e.Rev16(3, v);
                    e.StrhReg(3, 2, 1);
                } else {
                    e.StrbReg(v, 2, 1);
                }
                if (m.preDec) {
                    e.Mov(c.R(m.baseReg), 5);
                }
            }
            e.AddXImm(kCycles, kCycles, 1);
            const sint32 wbAfter = m.store ? 0xFF : static_cast<sint32>(m.target);
            wb = wbAfter;
            dirty |= it.regs;
            if (m.store) {
                // compiled code written? (code bitmap bits of the written halfwords; WRAM-H starts at bit 0x80000)
                auto hit = std::make_unique<HitPath>();
                e.Lsr(4, 1, 1);                  // halfword index in WRAM-H
                e.LdrX(6, kCtx, offsetof(Context, codeBits));
                e.AddXImmLsl12(6, 6, 0x10);      // + 0x80000 bits = 0x10000 bytes
                e.Lsr(7, 4, 6);
                e.LdrXRegScaled(7, 6, 7);
                e.LsrvX(7, 7, 4);
                e.AndXLow(7, 7, m.size == 4 ? 2 : 1);
                e.CbnzX(7, hit->label);
                hit->exit = &nativeExit(addr).label;
                hitPaths.push_back(std::move(hit));
            }
            e.Bind(slow->resume);
            slowPaths.push_back(std::move(slow));
        } else {
            // Interpreter call: state into the SH2 object, PC at this instruction (the delay slot's PC is already there)
            ++calls;
            ++stats.calledOps[static_cast<uint16>(it.in.op) & 511];
            const bool slot = k > 0 && items[k - 1].flow == Flow::DelayedBranch;
            const bool ends = it.flow == Flow::Branch || it.flow == Flow::CondDelayed || slot || k + 1 == items.size();
            if (it.isSlot) {
                // after a native branch: m_delaySlot and its target (no interrupt pending: SetupDelaySlot), then the
                // slot runs in its delay-slot form
                e.LdrX(0, kCtx, offsetof(Context, intr));
                e.Strb(WZR, 0, 0);
                e.LdrX(0, kCtx, offsetof(Context, delaySlot));
                e.MovImm(1, 1);
                e.Strb(1, 0, 0);
                e.LdrX(0, kCtx, offsetof(Context, delaySlotTarget));
                e.StrW(kTarget, 0, 0);
            }
            beforeCall(addr, !slot || it.isSlot, !ends);
            fetchPending = false;
            wb = kWbUnknown;
            afterCall = true;
            Emitter::Label &x = callExit(addr);
            if (ends) {
                e.B(x); // end of the block's control flow
                continue;
            }
            if (it.flow == Flow::DelayedBranch) {
                // the delay slot follows: no interrupt can be pending (SetupDelaySlot) and no memory was written
                e.LdrX(0, kCtx, offsetof(Context, PC));
                e.LdrW(1, 0, 0);
                e.MovImm(2, addr + 2);
                e.Cmp(1, 2);
                e.BCond(NE, x);
                e.CmpX(kCycles, kLimit);
                e.BCond(HS, x);
                continue;
            }
            afterCallChecks(addr, x);
            e.CmpX(kCycles, kLimit);
            e.BCond(HS, x);
            continue;
        }

        if (it.isSlot) {
            // end of the delay slot: PC = target, the rest of AdvancePC<.., true> in RunBlocks
            Exit &x = nativeExit(addr);
            x.pcFromTarget = true;
            x.post = 2;
            e.B(x.label);
            continue;
        }
        // after a native or memory instruction
        Emitter::Label &x = nativeExit(addr).label;
        if (afterCall) {
            // an interrupt raised by the call (allowed again now) stops the chain after this instruction
            e.LdrX(0, kCtx, offsetof(Context, intr));
            e.Ldrb(1, 0, 0);
            e.Cbnz(1, x);
        }
        e.CmpX(kCycles, kLimit);
        e.BCond(HS, x);
        afterCall = false;
    }
    // Falling off the end (a native or memory instruction was last, or the length limit)
    {
        const Item &last = items.back();
        Emitter::Label &x = last.native || last.mem || last.special || last.mac ? nativeExit(last.in.pc).label
                                                                                   : callExit(last.in.pc);
        e.B(x);
    }

    // Slow paths: the memory access through the interpreter, then back (or out where the chain would stop)
    for (auto &sp : slowPaths) {
        SlowPath &p = *sp;
        e.Bind(p.label);
        fetchPending = p.fetchPending;
        fetchValue = p.fetchValue;
        wb = p.wb;
        dirty = p.dirty;
        dirtyT = p.dirtyT;
        beforeCall(p.addr, true, true);
        Emitter::Label &x = callExit(p.addr);
        afterCallChecks(p.addr, x);
        e.B(p.resume);
    }
    // Writes to compiled code: drop it (the address is in w5), then leave after the instruction
    for (auto &hp : hitPaths) {
        e.Bind(hp->label);
        storeRegs();
        e.LdrX(0, kCtx, offsetof(Context, cache));
        e.Mov(1, 5);
        e.LdrX(8, kCtx, offsetof(Context, codeWritten));
        e.Blr(8);
        loadRegs();
        e.B(*hp->exit);
    }

    // Native exits: w1 = next PC, w2 = last PC, w3 = fetched pair, w4 = 1 to store it, w6 = m_wbReg (0x100: keep)
    Emitter::Label epilogue, nativeTail;
    for (auto &xp : exits) {
        Exit &x = *xp;
        e.Bind(x.label);
        if (x.native) {
            if (x.allowFalse) {
                e.LdrX(0, kCtx, offsetof(Context, intr));
                e.Strb(WZR, 0, 1);
            }
            if (x.delay) {
                // SetupDelaySlot: no interrupt pending until the slot ends
                e.LdrX(0, kCtx, offsetof(Context, intr));
                e.Strb(WZR, 0, 0);
                e.LdrX(0, kCtx, offsetof(Context, delaySlot));
                e.MovImm(7, 1);
                e.Strb(7, 0, 0);
                e.LdrX(0, kCtx, offsetof(Context, delaySlotTarget));
                e.StrW(kTarget, 0, 0);
            }
            if (x.post != 0) {
                e.MovImm(7, x.post);
                e.Strb(7, kCtx, offsetof(Context, post));
            }
            if (x.pcFromTarget) {
                // the interpreter keeps the last delayed branch's target (it is in the save state)
                e.LdrX(0, kCtx, offsetof(Context, delaySlotTarget));
                e.StrW(kTarget, 0, 0);
                e.Mov(1, kTarget);
            } else {
                e.MovImm(1, x.nextPC);
            }
            e.MovImm(2, x.lastPC);
            e.MovImm(4, x.storeFetch ? 1 : 0);
            if (x.storeFetch) {
                e.MovImm(3, x.fetched);
            }
            e.MovImm(6, x.wb == kWbUnknown ? 0x100 : static_cast<uint32>(x.wb));
            e.B(nativeTail);
        } else {
            e.MovImm(1, x.lastPC);
            e.StrW(1, kCtx, offsetof(Context, lastPC));
            e.B(epilogue);
        }
    }
    e.Bind(nativeTail);
    e.StrW(2, kCtx, offsetof(Context, lastPC));
    e.LdrX(0, kCtx, offsetof(Context, PC));
    e.StrW(1, 0, 0);
    {
        Emitter::Label noFetch, keepWb;
        e.CmpImm(4, 0);
        e.BCond(EQ, noFetch);
        e.LdrX(0, kCtx, offsetof(Context, fetched));
        e.StrW(3, 0, 0);
        e.Bind(noFetch);
        e.Lsr(7, 6, 8);
        e.Cbnz(7, keepWb);
        e.LdrX(0, kCtx, offsetof(Context, wbReg));
        e.Strb(6, 0, 0);
        e.Bind(keepWb);
    }
    storeRegs();
    e.Bind(epilogue);
    e.LdrX(0, kCtx, offsetof(Context, cycles));
    e.StrX(kCycles, 0, 0);
    e.Ldp(19, 20, 31, 16);
    e.Ldp(21, 22, 31, 32);
    e.Ldp(23, 24, 31, 48);
    e.Ldp(25, 26, 31, 64);
    e.Ldp(27, 28, 31, 80);
    e.LdpPost(29, 30, 31, 96);
    e.Ret();

    const size_t bytes = e.code.size() * 4;
    if (m_codeUsed + bytes > m_codeSize) {
        Flush();
    }
    uint8 *dst = m_code + m_codeUsed;
    std::memcpy(dst, e.code.data(), bytes);
    __builtin___clear_cache(reinterpret_cast<char *>(dst), reinterpret_cast<char *>(dst + bytes));
    m_codeUsed += (bytes + 15) & ~size_t{15};

    auto block = std::make_unique<Block>();
    block->code = reinterpret_cast<void (*)(Context *)>(dst);
    block->startPC = pc;
    block->instrs = static_cast<uint32>(items.size());
    Block *raw = block.get();
    m_blocks.push_back(std::move(block));
    ++stats.compiled;
    stats.nativeInstrs += natives;
    stats.calledInstrs += calls;
    return remember(raw, std::max(lastCovered, items.back().in.pc));
}

#else // no JIT on this platform

bool Cache::Supported() {
    return false;
}
Cache::Cache() = default;
Cache::~Cache() = default;
void Cache::Flush() {}
void Cache::CodeWritten(uint32) {}
const Block *Cache::Get(uint32, Fetch, void *, bool) {
    return nullptr;
}
const Block *Cache::Compile(uint32, Fetch, void *) {
    return nullptr;
}

#endif

} // namespace ymir::sh2::jit
