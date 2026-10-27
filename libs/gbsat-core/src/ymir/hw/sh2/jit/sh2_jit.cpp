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

constexpr size_t kCodeSize = 8u << 20; // RWX area; flushed when full
constexpr uint32 kMaxInstrs = 32;      // instructions per block

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

// Host registers: x0 = &R[0], x1 = &SR, w2 = SR, w3/w4 temporaries, w5..w17 hold the SH-2 registers the block uses
constexpr uint32 kSR = 2, kT0 = 3, kT1 = 4, kFirstHost = 5, kHostRegs = 13;

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

const Block *Cache::Get(uint32 pc, Fetch fetch, void *ctx) {
    if (m_slots == nullptr || !CompilableAddress(pc)) {
        return nullptr;
    }
    const Block *block = *Slot(pc);
    if (block == kNoBlock) {
        return nullptr;
    }
    // A block compiled through another mirror of the same memory (cached/cache-through) may embed its own PC (MOVA)
    if (block == nullptr || block->startPC != pc) {
        block = Compile(pc, fetch, ctx);
    }
    return block == kNoBlock ? nullptr : block;
}

const Block *Cache::Compile(uint32 pc, Fetch fetch, void *ctx) {
    // lastCovered: the last halfword the block depends on (its last instruction, or the opcode pair it prefetched)
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

    // Collect the run of supported instructions
    std::vector<Instr> instrs;
    uint32 usedRegs = 0;
    uint32 writtenRegs = 0;
    bool usesT = false;
    uint32 firstRegs = 0;
    for (uint32 addr = pc; instrs.size() < kMaxInstrs; addr += 2) {
        if (!CompilableAddress(addr) || Normalize(addr) - Normalize(pc) != addr - pc || Page(addr) > Page(pc) + 1) {
            break;
        }
        const uint16 opcode = fetch(ctx, addr);
        const OpcodeType op = DecodeTable::s_instance.opcodes[0][opcode];
        Uses uses;
        bool t;
        if (!IsSupported(op, uses, t)) {
            break;
        }
        const uint32 regs = UsedRegs(uses, opcode);
        if (std::popcount(usedRegs | regs) > (int)kHostRegs) {
            break;
        }
        if (instrs.empty()) {
            firstRegs = regs;
        }
        usedRegs |= regs;
        writtenRegs |= regs; // conservative: every register an instruction names is stored back
        usesT |= t;
        instrs.push_back({op, opcode, addr});
    }
    if (instrs.empty()) {
        return remember(kNoBlock, pc);
    }

    Compiler c;
    uint32 next = kFirstHost;
    for (uint32 r = 0; r < 16; ++r) {
        c.host[r] = (usedRegs >> r) & 1 ? next++ : WZR;
    }
    for (uint32 r = 0; r < 16; ++r) {
        if ((usedRegs >> r) & 1) {
            c.e.LdrW(c.host[r], 0, r * 4);
        }
    }
    if (usesT) {
        c.e.LdrW(kSR, 1, 0);
    }
    for (const Instr &in : instrs) {
        c.Emit(in);
    }
    for (uint32 r = 0; r < 16; ++r) {
        if ((writtenRegs >> r) & 1) {
            c.e.StrW(c.host[r], 0, r * 4);
        }
    }
    if (usesT) {
        c.e.StrW(kSR, 1, 0);
    }
    c.e.Ret();

    const size_t bytes = c.e.code.size() * 4;
    if (m_codeUsed + bytes > m_codeSize) {
        Flush();
    }
    uint8 *dst = m_code + m_codeUsed;
    std::memcpy(dst, c.e.code.data(), bytes);
    __builtin___clear_cache(reinterpret_cast<char *>(dst), reinterpret_cast<char *>(dst + bytes));
    m_codeUsed += (bytes + 15) & ~size_t{15};

    auto block = std::make_unique<Block>();
    block->code = reinterpret_cast<void (*)(uint32 *, uint32 *)>(dst);
    block->startPC = pc;
    block->lastPC = instrs.back().pc;
    block->count = static_cast<uint32>(instrs.size());
    block->firstRegs = firstRegs;
    block->setsFetch = false;
    uint32 lastCovered = block->lastPC;
    for (const Instr &in : instrs) {
        if ((in.pc & 2) == 0) {
            block->setsFetch = true;
            block->fetched = (static_cast<uint32>(fetch(ctx, in.pc)) << 16) | fetch(ctx, in.pc + 2);
            lastCovered = std::max(lastCovered, in.pc + 2);
        }
    }
    block->pageFirst = Page(pc);
    block->pageLast = Page(block->lastPC);
    Block *raw = block.get();
    m_blocks.push_back(std::move(block));
    ++stats.compiled;
    return remember(raw, lastCovered);
}

#else // no JIT on this platform

bool Cache::Supported() {
    return false;
}
Cache::Cache() = default;
Cache::~Cache() = default;
void Cache::Flush() {}
void Cache::CodeWritten(uint32) {}
const Block *Cache::Get(uint32, Fetch, void *) {
    return nullptr;
}
const Block *Cache::Compile(uint32, Fetch, void *) {
    return nullptr;
}

#endif

} // namespace ymir::sh2::jit
