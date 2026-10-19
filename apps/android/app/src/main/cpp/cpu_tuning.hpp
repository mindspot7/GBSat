#pragma once
#include <cstdint>

// CPU placement and Android performance hints for the emulation threads.
namespace cputune {
// Pins the calling thread (emulation) and the VDP render threads to the fastest cores and keeps an Android performance
// hint session (Android 13+) for them. Cheap; call it again from time to time to pick up recreated render threads.
void PinEmulationThreads();
// Reports the time spent emulating the last frame to the performance hint session.
void ReportFrameWork(int64_t ns);
} // namespace cputune
