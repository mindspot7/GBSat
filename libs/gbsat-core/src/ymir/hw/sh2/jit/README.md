# SH-2 dynamic recompiler

`DynarecSh2.cpp`, `DynarecSh2.h`, `opcodes.h` and `dynalib_arm64.s` come from YabaSanshiro's
`yabause/src/sh2_dynarec_devmiyax` (devMiyax, GPLv2 or later), taken from the tico fork
(github.com/ticohq/tico-yabasanshiro, commit 56f8fd45, October 2026). Changes are marked `GBSat:`:

- Yabause headers replaced by `yaba_compat.h`; no HLE BIOS hooks; no DMA penalty bookkeeping.
- `dynalib_arm64.s`: the inline WRAM-H reads use Ymir's big-endian layout (Yabause swaps 16-bit words).
  Every macro keeps its instruction count, because the opcode templates' sizes and patch offsets are fixed.
- `CompileBlocks::Init` resets only the blocks used since the last call (it used to clear the whole 64 MiB area).

`sh2_jit.cpp` replaces `DynarecSh2CInterface.cpp`: memory goes to the running Ymir SH-2 (`sh2.cpp`), which
also owns time slices, interrupts, SLEEP and the on-chip modules. Writes to the work RAMs reach `CodeWritten`
through the bus's code watch (`SH2Bus::SetCodeWatch`), whoever makes them.
