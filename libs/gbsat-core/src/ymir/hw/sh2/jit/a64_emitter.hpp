#pragma once

// Minimal AArch64 instruction encoder for the SH-2 JIT: only the 32-bit (W register) data-processing, load/store and
// control instructions the compiler uses. Each function appends one instruction.

#include <ymir/core/types.hpp>

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
};

} // namespace ymir::sh2::jit::a64
