# SH-2 ARM64 JIT: design

Status: October 2026, phase 2 started. Phase 1 (host code for ALU, MOV loads/stores with a direct WRAM-H path,
MAC/MUL/STS/LDS, branches and delay slots; interpreter calls for the rest) plus: a fixed register mapping (R0-R15 in
host registers in every block), blocks jumping to the next one directly (pipeline refill, end-of-slot interrupt
check, idle-loop back jumps and the slice checked inline), a static cycle bound per block (no per-instruction slice
check; a block starts only when the slice has more than its bound left) and block lookups only where the flow jumps.
Burning Rangers, Daytona USA, Virtua Fighter 2 and NiGHTS stay identical to the interpreter for 300 frames.

Not faster yet. Profile of sh2cmp (VF2, 200 frames, RG Rotate): the interpreter run spends 4.5% of the samples in
`RunChained`; the JIT run 3.5% in `RunChained` (instructions still interpreted), 0.5% in `RunBlocks` and 1.6% in
generated code, about twice the chain's cost per instruction. Reasons: each block entered from C++ pays ~100 host
instructions (callee-saved registers, all 16 SH-2 registers in and out) for ~7 SH-2 instructions (VF2's basic blocks
are short); with 128-cycle slices most blocks start from C++ (slices start mid-block, blocks that don't fit the rest
of a slice are skipped), so linking helps little. Ymir's chained interpreter costs ~15-20 host instructions per SH-2
instruction, a high bar. To pay off the JIT needs to stay in host code for whole slices: entries at any instruction
(slices starting mid-block), exits at any instruction (the slice end) without leaving the block structure, cheaper
entry/exit (registers loaded lazily), and longer blocks (following branches). sh2cmp's time per frame is not a good
measure here: it renders VDP and runs the SCSP on its single thread; use simpleperf on it (SH-2 symbols).

## Why, and what it can gain

Burning Rangers on the RG Rotate (T618), CPU renderer, Fast CPU sync (128 cycles): the SH-2 interpreter
(`SH2::RunChained` and the instruction handlers) is ~65% of the emulation thread. The rest (SCU DMA, VDP,
scheduler, kernel, frame pacing) does not change with a JIT. If compiled code runs SH-2 instructions 2-3x faster,
the emulation thread does ~35-45% less work: Burning Rangers 14.6 -> ~8.5-10 ms per frame. Games bound by
another thread (NiGHTS waits on the VDP2 renderer, Virtua Fighter 2 on sound) gain CPU headroom, not frames.

YabaSanshiro's DynarecSh2 gave nothing over this interpreter (registers kept in memory, a register copy on every
entry). Lessons applied here: SH-2 registers live in host registers inside a block; compiled code works on the
SH2 object in place (no copies); slices are long enough to amortize entering compiled code (Fast sync, 128 cycles).

## Goal: identical results to the interpreter

The JIT must produce exactly what `RunChained` produces: same registers, memory, cycle counts, interrupt
acceptance points and slice ends. That keeps every game that works with the interpreter working, and makes
validation mechanical (see below). Ymir's timing makes this possible because it is deterministic per instruction:

- Base cycles per instruction are fixed (branch taken/not taken known at run time).
- Memory access cycles (`AccessCycles`): cached area = 1 with cache emulation off; cache-through and I/O come
  from the bus page tables. Computed statically for the cached area, at run time otherwise.
- Load-use stall (`WritebackCycles`, `m_wbReg`): +1 when an instruction uses the register the previous one loaded.
  Known statically inside a block; for the first instruction it is checked against `m_wbReg` at run time, and the
  block stores `m_wbReg` on exit.
- Bus wait (`IsBusWait`): the instruction does not execute and is retried. Slow-path accesses check it and leave
  the block at that instruction with its cycles added.

## Structure

- `libs/gbsat-core/src/ymir/hw/sh2/jit/`: emitter, compiler, code cache. `SH2` keeps owning interrupts, on-chip
  modules, DMA, idle-loop skipping and save states; Advance calls the JIT instead of `RunChained` when enabled.
- **Emitter**: a small ARM64 encoder of our own (the few dozen instructions needed); no third-party code.
- **Registers**: R0-R15 in host registers for the whole block (x19-x28 callee-saved, the rest caller-saved and
  saved around helper calls), T in a host register, the cycle counter in a register, x29 = SH2 object.
  Loaded at block entry, stored at every exit.
- **Blocks**: from an entry PC to the first branch (with its delay slot), up to a size limit. Ends early before
  any instruction not compiled yet; the dispatcher then interprets that one instruction (`InterpretNext`), so the
  JIT can be built up incrementally and always works.
- **Exits** (back to Advance, state stored): end of block; slice budget reached; an instruction that may make an
  interrupt pending (SR writes, slow-path memory accesses) when one became acceptable; bus wait; short backward
  jump (as `RunChained` does, so Advance's idle-loop analysis keeps working).
- **Budget**: at block entry, if the remaining slice is shorter than the block's static cost, the rest of the slice
  is interpreted (exact slice end); otherwise the block runs whole, checking only after dynamic-cost instructions.
- **Memory**: inline fast paths for WRAM-H/WRAM-L in the cached and cache-through areas (big-endian arrays, `rev`);
  everything else calls the interpreter's `MemRead`/`MemWrite` helpers. SH-2 cache emulation on = interpreter only.
- **Code cache**: one RWX area (16-32 MiB), flushed when full. Lookup tables per region (WRAM-H, WRAM-L, BIOS).
  Invalidation: a code-write watch on the bus for WRAM pages (every writer: both SH-2s, SCU/SH-2 DMA, CD block),
  plus a full flush on reset, save state load and disc change.
- **Block linking** (phase 2): direct jumps between compiled blocks when the target is known and the budget allows.

## Validation

1. **Frame-exact comparison**: from the same save state, run N frames with the interpreter and with the JIT and
   compare a hash of the SH-2 registers, WRAM-L/WRAM-H and VDP RAM after every frame. Any difference stops at the
   first frame that diverges; a trace mode then compares slice by slice. Runs on the device (adb), no UI.
   Tool: `apps/android/app/src/main/cpp/tools/sh2cmp.cpp` (target `gbsat-sh2cmp`, configured with
   `-DGBSAT_TOOLS=ON`; same NDK/DXC setup as the app). Push it with `libc++_shared.so` to `/data/local/tmp` and run
   `LD_LIBRARY_PATH=. ./sh2cmp --bios <ipl.bin> --disc <game.chd> --state <0.savestate> --frames 600 [--sync 128]
   [--sync-b N]`. It compares MSH2, SSH2, WRAM-L, WRAM-H, system, VDP, sound RAM and SMPC (the SCU and SCSP
   serializers refuse to save some states, so those are left out). Checked: two interpreter runs of the Burning
   Rangers state stay identical for 300 frames; sync 32 against 128 differs in the first frames. The tool runs the RTC
   in virtual mode from a fixed time: with the host clock Daytona USA differs between two interpreter runs.
   `--jit-b` runs B with the JIT, `--jit-b-nocompile` with the JIT path but no blocks (same as the interpreter).
2. **Per-block check** (debug builds): after each compiled block, re-run the same instructions with the interpreter
   on a copy of the state and compare.
3. Game tests: the owner's save states (Burning Rangers, Daytona USA, Virtua Fighter 2, NiGHTS) and the perf log.

## Phases

1. Infrastructure: emitter, code cache, dispatcher with interpreter fallback, register load/store, ALU and
   register-move instructions, branches with delay slots, exact cycle counting; memory through helpers.
   Frame-exact comparison tool. Expected: works, modest gain.
2. Inline WRAM fast paths, block linking, multiply/MAC/DIV. Expected: most of the gain.
3. Remaining instructions (TRAPA, RTE, SLEEP stay interpreted if rare), option in the UI (CPU core: Interpreter /
   Recompiler), game tests on several devices.
