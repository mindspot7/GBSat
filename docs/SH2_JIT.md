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

Same picture on a Cortex-A55 (the RG DS's RK3568 core; measured on the T618's A55 cores at 2.0 GHz with taskset):
VF2 interpreter run 4.4% of samples in `RunChained`, JIT run 3.9% + 0.6% (`RunBlocks`) + 1.9% generated code = 6.4%;
Burning Rangers 5.5% against ~11% (recompiling included). The in-order core doesn't change the balance.

## Approximate mode (October 2026, experimental, off)

`Cache::approximate` (JNI `nativeSetSH2Jit(2)`; `adb shell setprop debug.gbsat.jit 0|1|2` overrides, for tests; the app
passes 0): blocks have no cycle bound and run to their end even past the slice end (the overshoot is taken from the next
slice, as the interpreter's last instruction already is), and the slice is only checked between blocks, so slices end at
block boundaries. Not identical to the interpreter.

Measured on the RG DS (RK3568), Burning Rangers from a BIOS boot, 20 s window, perf log: the emulation thread is the limit
(94% CPU) and the master SH-2 is most of it.

| | emu fps | msh2 ms/frame |
|---|---|---|
| Interpreter | 30.1 | 23.7 |
| JIT exact | ~22 | ~40 |
| JIT approximate | 19.8 | 45.0 |

Per second the JIT ran ~390k blocks but still interpreted ~1.7-2.2M instructions (the chain between blocks: blocks
start only at jump targets, so whatever follows a block that stopped early runs interpreted up to the next jump), and
each block entry costs ~100 host instructions. The JIT makes the master SH-2 about twice as slow; removing the slice
checks (approximate mode) does not change that. Whole-process profile of the same scene: SCSP thread 34%, emulation
thread 33%, VDP2 renderer 16%.

Then, in approximate mode only: blocks are compiled and entered at any instruction (not only at jump targets: slices
end at block boundaries, so the chain no longer interprets the rest of a basic block), blocks of only interpreter calls
are kept (they link), links reach the cache-through WRAM-H area too, and BT/S, BF/S run their slot in the block (target
= next instruction when not taken). Interpreted instructions drop to ~0; RG DS, Virtua Fighter 2 attract mode at 35 s:
emulation 42.9 -> 47.8 fps, master SH-2 16.7 -> 14.6 ms per frame. Burning Rangers (32-cycle slices) runs fine. Blocks
are still entered from C++ once per slice (~380k/s): cheaper entry/exit is the next step.

Memory accesses outside the direct WRAM-H path call small helpers (`Context::memRead/memWrite`, 1 cycle, no bus wait)
with only the caller-saved host registers saved, instead of a full interpreter call. Measured twice each, interleaved,
VF2 attract (RG DS, 25 s windows): interpreter 43.1 fps / master SH-2 17.2 ms; approximate JIT 45.7 fps / 15.0 ms
(+6% fps). Profile of the emulation thread with the JIT: generated code 19% of the process samples (the SH-2 work
itself), Advance 3.7%, InterpretNext 2% (instructions without host code), RunBlocks 1.9%, CodeWritten 1.3% (the
code-write watch on every bus write). Next: find what the generated code spends its time on (a perf map of the blocks),
host code for the remaining interpreted instructions, a cheaper code-write watch.

Block map (`Cache::mapFile`, `adb shell setprop debug.gbsat.jitmap 1` before starting a game with the JIT on: writes
`Android/media/<app>/jitmap.txt`, host address of each block part and SH-2 instruction) matched with simpleperf samples
(`--sort comm,dso,vaddr_in_file`, the `unknown` dso): of the generated code's time, plain register instructions 37%,
memory accesses 20%, memory helpers (slow path) 11%, interpreter calls 8% (mostly STS.L PR / LDS.L PR / STC SR), exits
8%, branches 9%, prologue 5%. Dropping the load-use stall tracking and adding the cycles of plain instructions once per
run (approximate mode) changed nothing measurable (master SH-2 15.1 ms): the generated code is no longer the limit.
What is left in the master SH-2's time is mostly the system work its accesses trigger (VDP2 VRAM writes, render queue
pushes) and the per-slice dispatch (Advance, RunBlocks, about 6% of the process).

Before those changes, what would be needed for a gain: blocks entered at any instruction (no interpreted tail), all common instructions in
host code including memory accesses outside WRAM-H through direct helper calls, cheaper entry/exit (registers loaded
lazily) and blocks that follow branches, so that whole slices stay in host code. That is a redesign of the compiler,
not a mode of the current one.

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
