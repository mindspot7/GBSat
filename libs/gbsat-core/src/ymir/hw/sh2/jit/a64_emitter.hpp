#pragma once

// Minimal AArch64 instruction encoder for the SH-2 JIT: only the 32-bit (W register) data-processing, load/store and
// control instructions the compiler uses. Each function appends one instruction.

#include <ymir/core/types.hpp>

#include <cstdint>
#include <utility>
#include <vector>

namespace ymir::sh2::jit::a64 {

// Condition codes
enum Cond : uint32 { EQ = 0, NE = 1, HS = 2, LO = 3, MI = 4, PL = 5, HI = 8, LS = 9, GE = 10, LT = 11, GT = 12, LE = 13 };

constexpr uint32 WZR = 31;

class Emitter {
public:
    std::vector<uint32> code;

    void Emit(uint32 insn) {
        code.push_back(insn);
        if (m_backPatch != nullptr) {
            Patch(*m_backPatch, code.size() - 1);
            m_backPatch = nullptr;
        }
    }

    // Loads and stores (32-bit, unsigned offset scaled by 4)
    void LdrW(uint32 rt, uint32 xn, uint32 offset) {
        Emit(0xB9400000 | ((offset / 4) << 10) | (xn << 5) | rt);
    }
    void StrW(uint32 rt, uint32 xn, uint32 offset) {
        Emit(0xB9000000 | ((offset / 4) << 10) | (xn << 5) | rt);
    }

    // Moves
    void Mov(uint32 rd, uint32 rm) {
        Emit(0x2A0003E0 | (rm << 16) | rd); // orr wd, wzr, wm
    }
    void MovImm(uint32 rd, uint32 imm) {
        Emit(0x52800000 | ((imm & 0xFFFF) << 5) | rd); // movz wd, #lo
        if (imm >> 16) {
            Emit(0x72A00000 | ((imm >> 16) << 5) | rd); // movk wd, #hi, lsl #16
        }
    }

    // Three-register arithmetic and logic
    void Add(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x0B000000 | (rm << 16) | (rn << 5) | rd);
    }
    void Sub(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x4B000000 | (rm << 16) | (rn << 5) | rd);
    }
    void Subs(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x6B000000 | (rm << 16) | (rn << 5) | rd);
    }
    void Adcs(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x3A000000 | (rm << 16) | (rn << 5) | rd);
    }
    void Sbcs(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x7A000000 | (rm << 16) | (rn << 5) | rd);
    }
    void And(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x0A000000 | (rm << 16) | (rn << 5) | rd);
    }
    void Ands(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x6A000000 | (rm << 16) | (rn << 5) | rd);
    }
    void Orr(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x2A000000 | (rm << 16) | (rn << 5) | rd);
    }
    void Eor(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x4A000000 | (rm << 16) | (rn << 5) | rd);
    }
    void Bic(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x0A200000 | (rm << 16) | (rn << 5) | rd);
    }
    void Mvn(uint32 rd, uint32 rm) {
        Emit(0x2A2003E0 | (rm << 16) | rd); // orn wd, wzr, wm
    }
    void Neg(uint32 rd, uint32 rm) {
        Sub(rd, WZR, rm);
    }
    void Cmp(uint32 rn, uint32 rm) {
        Subs(WZR, rn, rm);
    }
    void Tst(uint32 rn, uint32 rm) {
        Ands(WZR, rn, rm);
    }

    // Immediate add/subtract (0..4095)
    void AddImm(uint32 rd, uint32 rn, uint32 imm12) {
        Emit(0x11000000 | (imm12 << 10) | (rn << 5) | rd);
    }
    void SubImm(uint32 rd, uint32 rn, uint32 imm12) {
        Emit(0x51000000 | (imm12 << 10) | (rn << 5) | rd);
    }
    void CmpImm(uint32 rn, uint32 imm12) {
        Emit(0x7100001F | (imm12 << 10) | (rn << 5)); // subs wzr, wn, #imm
    }

    // Bitfield moves
    void Ubfm(uint32 rd, uint32 rn, uint32 immr, uint32 imms) {
        Emit(0x53000000 | (immr << 16) | (imms << 10) | (rn << 5) | rd);
    }
    void Sbfm(uint32 rd, uint32 rn, uint32 immr, uint32 imms) {
        Emit(0x13000000 | (immr << 16) | (imms << 10) | (rn << 5) | rd);
    }
    void Bfm(uint32 rd, uint32 rn, uint32 immr, uint32 imms) {
        Emit(0x33000000 | (immr << 16) | (imms << 10) | (rn << 5) | rd);
    }
    void Lsl(uint32 rd, uint32 rn, uint32 s) {
        Ubfm(rd, rn, (32 - s) & 31, 31 - s);
    }
    void Lsr(uint32 rd, uint32 rn, uint32 s) {
        Ubfm(rd, rn, s, 31);
    }
    void Asr(uint32 rd, uint32 rn, uint32 s) {
        Sbfm(rd, rn, s, 31);
    }
    void Uxtb(uint32 rd, uint32 rn) {
        Ubfm(rd, rn, 0, 7);
    }
    void Uxth(uint32 rd, uint32 rn) {
        Ubfm(rd, rn, 0, 15);
    }
    void Sxtb(uint32 rd, uint32 rn) {
        Sbfm(rd, rn, 0, 7);
    }
    void Sxth(uint32 rd, uint32 rn) {
        Sbfm(rd, rn, 0, 15);
    }
    // rd bit 0 = rn bit 0 (T flag)
    void InsertBit0(uint32 rd, uint32 rn) {
        Bfm(rd, rn, 0, 0);
    }
    // rd bits 15..0 = rn bits 15..0
    void InsertLow16(uint32 rd, uint32 rn) {
        Bfm(rd, rn, 0, 15);
    }
    // rd = bit 0 of rn
    void Bit0(uint32 rd, uint32 rn) {
        Ubfm(rd, rn, 0, 0);
    }
    void Ror(uint32 rd, uint32 rn, uint32 s) {
        Extr(rd, rn, rn, s);
    }
    // rd = (hi:lo) >> lsb (low 32 bits)
    void Extr(uint32 rd, uint32 hi, uint32 lo, uint32 lsb) {
        Emit(0x13800000 | (lo << 16) | (lsb << 10) | (hi << 5) | rd);
    }
    void Rev16(uint32 rd, uint32 rn) {
        Emit(0x5AC00400 | (rn << 5) | rd);
    }

    // rd = cond ? 1 : 0
    void Cset(uint32 rd, Cond cond) {
        Emit(0x1A9F07E0 | ((cond ^ 1u) << 12) | rd); // csinc wd, wzr, wzr, !cond
    }

    void Ret() {
        Emit(0xD65F03C0);
    }

    // 64-bit (X register) helpers
    void LdrX(uint32 rt, uint32 xn, uint32 offset) {
        Emit(0xF9400000 | ((offset / 8) << 10) | (xn << 5) | rt);
    }
    void StrX(uint32 rt, uint32 xn, uint32 offset) {
        Emit(0xF9000000 | ((offset / 8) << 10) | (xn << 5) | rt);
    }
    void Ldrb(uint32 rt, uint32 xn, uint32 offset) {
        Emit(0x39400000 | (offset << 10) | (xn << 5) | rt);
    }
    void Strb(uint32 rt, uint32 xn, uint32 offset) {
        Emit(0x39000000 | (offset << 10) | (xn << 5) | rt);
    }
    void Ldrh(uint32 rt, uint32 xn, uint32 offset) {
        Emit(0x79400000 | ((offset / 2) << 10) | (xn << 5) | rt);
    }
    void MovX(uint32 rd, uint32 rm) {
        Emit(0xAA0003E0 | (rm << 16) | rd);
    }
    void AddX(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x8B000000 | (rm << 16) | (rn << 5) | rd);
    }
    void AddXImm(uint32 rd, uint32 rn, uint32 imm12) {
        Emit(0x91000000 | (imm12 << 10) | (rn << 5) | rd);
    }
    void CmpX(uint32 rn, uint32 rm) {
        Emit(0xEB00001F | (rm << 16) | (rn << 5));
    }
    // Register pairs: stp/ldp with a signed offset (multiple of 8), pre-indexed store / post-indexed load
    void StpPre(uint32 rt, uint32 rt2, uint32 xn, sint32 offset) {
        Emit(0xA9800000 | ((static_cast<uint32>(offset / 8) & 0x7F) << 15) | (rt2 << 10) | (xn << 5) | rt);
    }
    void Stp(uint32 rt, uint32 rt2, uint32 xn, sint32 offset) {
        Emit(0xA9000000 | ((static_cast<uint32>(offset / 8) & 0x7F) << 15) | (rt2 << 10) | (xn << 5) | rt);
    }
    void Ldp(uint32 rt, uint32 rt2, uint32 xn, sint32 offset) {
        Emit(0xA9400000 | ((static_cast<uint32>(offset / 8) & 0x7F) << 15) | (rt2 << 10) | (xn << 5) | rt);
    }
    void LdpPost(uint32 rt, uint32 rt2, uint32 xn, sint32 offset) {
        Emit(0xA8C00000 | ((static_cast<uint32>(offset / 8) & 0x7F) << 15) | (rt2 << 10) | (xn << 5) | rt);
    }
    void Blr(uint32 xn) {
        Emit(0xD63F0000 | (xn << 5));
    }
    void Lsrv(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x1AC02400 | (rm << 16) | (rn << 5) | rd);
    }
    void And1(uint32 rd, uint32 rn) {
        Emit(0x12000000 | (rn << 5) | rd); // and wd, wn, #1
    }

    // Register-offset loads and stores: [xn + xm]
    void LdrWReg(uint32 rt, uint32 xn, uint32 xm) {
        Emit(0xB8606800 | (xm << 16) | (xn << 5) | rt);
    }
    void LdrhReg(uint32 rt, uint32 xn, uint32 xm) {
        Emit(0x78606800 | (xm << 16) | (xn << 5) | rt);
    }
    void LdrbReg(uint32 rt, uint32 xn, uint32 xm) {
        Emit(0x38606800 | (xm << 16) | (xn << 5) | rt);
    }
    void StrWReg(uint32 rt, uint32 xn, uint32 xm) {
        Emit(0xB8206800 | (xm << 16) | (xn << 5) | rt);
    }
    void StrhReg(uint32 rt, uint32 xn, uint32 xm) {
        Emit(0x78206800 | (xm << 16) | (xn << 5) | rt);
    }
    void StrbReg(uint32 rt, uint32 xn, uint32 xm) {
        Emit(0x38206800 | (xm << 16) | (xn << 5) | rt);
    }
    // [xn + xm * 8]
    void LdrXRegScaled(uint32 rt, uint32 xn, uint32 xm) {
        Emit(0xF8607800 | (xm << 16) | (xn << 5) | rt);
    }
    void Rev(uint32 rd, uint32 rn) {
        Emit(0x5AC00800 | (rn << 5) | rd);
    }
    // wd = wn & (len ones from bit lsb)
    void AndRun(uint32 rd, uint32 rn, uint32 lsb, uint32 len) {
        Emit(0x12000000 | (((32 - lsb) & 31) << 16) | ((len - 1) << 10) | (rn << 5) | rd);
    }
    void LsrvX(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x9AC02400 | (rm << 16) | (rn << 5) | rd);
    }
    // xd = xn & ((1 << len) - 1), len 1..63
    void AndXLow(uint32 rd, uint32 rn, uint32 len) {
        Emit(0x92400000 | ((len - 1) << 10) | (rn << 5) | rd);
    }
    void Mul(uint32 rd, uint32 rn, uint32 rm) {
        Emit(0x1B007C00 | (rm << 16) | (rn << 5) | rd); // madd wd, wn, wm, wzr
    }
    void Smull(uint32 xd, uint32 wn, uint32 wm) {
        Emit(0x9B207C00 | (wm << 16) | (wn << 5) | xd);
    }
    void Umull(uint32 xd, uint32 wn, uint32 wm) {
        Emit(0x9BA07C00 | (wm << 16) | (wn << 5) | xd);
    }
    void Sxtw(uint32 xd, uint32 wn) {
        Emit(0x93407C00 | (wn << 5) | xd);
    }
    void StrXZero(uint32 xn) {
        Emit(0xF900001F | (xn << 5)); // str xzr, [xn]
    }
    void AddXImmLsl12(uint32 rd, uint32 rn, uint32 imm12) {
        Emit(0x91400000 | (imm12 << 10) | (rn << 5) | rd);
    }
    // Branches to labels (patched when the label is placed)
    struct Label {
        size_t pos = SIZE_MAX;
        std::vector<std::pair<size_t, bool>> uses; // instruction index, conditional
    };
    void BCond(Cond c, Label &l) {
        Use(l, true);
        Emit(0x54000000 | c);
    }
    void B(Label &l) {
        Use(l, false);
        Emit(0x14000000);
    }
    void Cbnz(uint32 rt, Label &l) {
        Use(l, true);
        Emit(0x35000000 | rt);
    }
    void CbzW(uint32 rt, Label &l) {
        Use(l, true);
        Emit(0x34000000 | rt);
    }
    void CbnzX(uint32 rt, Label &l) {
        Use(l, true);
        Emit(0xB5000000 | rt);
    }
    // The branch about to be emitted targets l: patched at Bind, or right after emission when l is already placed
    void Use(Label &l, bool cond) {
        l.uses.push_back({code.size(), cond});
        if (l.pos != SIZE_MAX) {
            m_backPatch = &l;
        }
    }
    void Bind(Label &l) {
        l.pos = code.size();
        for (const auto &[at, cond] : l.uses) {
            Patch(l, at);
        }
    }

private:
    struct Label *m_backPatch = nullptr;
    void Patch(Label &l, size_t at) {
        bool cond = true;
        for (const auto &u : l.uses) {
            if (u.first == at) {
                cond = u.second;
            }
        }
        const sint32 off = static_cast<sint32>(l.pos) - static_cast<sint32>(at);
        if (cond) {
            code[at] |= (static_cast<uint32>(off) & 0x7FFFF) << 5;
        } else {
            code[at] |= static_cast<uint32>(off) & 0x3FFFFFF;
        }
    }
};

} // namespace ymir::sh2::jit::a64
