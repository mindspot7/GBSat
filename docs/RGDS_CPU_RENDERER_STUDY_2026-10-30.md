# RG DS CPU renderer performance study — 2026-10-30

## Scope and method

Device: Anbernic RG DS, RK3568 (4x Cortex-A55), Entry tier. GBSat local build 420, CPU (OpenGL) renderer (Ymir's software VDP renderer), Low preset, 1x, threaded VDP, adaptive frame skip. Each gameplay capture relaunched the exact ROM through the supported ROM intent and loaded its existing savestate. No ROM or savestate was copied into the repository. Profiling used `simpleperf cpu-clock` at 1 kHz; profile overhead is present, so profile FPS is descriptive. Where useful, A/B FPS results are additionally grouped by VDP1 command count to reduce scene variation; repeated SCSP on/off runs are summarized separately.

The measurement method follows `GBFrame/docs/LESSONS.md`: profile per thread/function, compare the same scene and workload buckets, and measure renderer wait time as well as CPU share. Profile outputs and CSVs remain in the local ignored workspace under `C:\.android\gbsat_vdp1clip_ab` and `C:\.android\gbsat_cpu_profile`.

## Tested changes — discarded

### VDP1 textured-line clipping fast path

Hypothesis: skip per-pixel clipping tests for fully in-bounds textured lines without user clipping.

- Burning Rangers: base A1/A2 median 32.7/32.6 FPS; candidate B1/B2 32.7/32.9. No repeatable gain.
- Virtua Fighter 2: base 57.6/57.8 FPS; candidate 57.2/57.3. Slightly slower.

The change was removed.

### VDP2 priority insertion

Hypothesis: replace insertion loops for the top-three layer priorities with explicit comparisons.

- VF2: base runs around 57.6–57.8 FPS; candidate 57.9/58.3, but the small difference did not repeat consistently in command-count buckets.
- NiGHTS: base 38.2/37.1; candidate 37.7/38.4. No consistent direction.

The change was removed.

### VDP2 normal-scroll fast path extended to horizontal zoom

The existing fast path is already enabled by default. A local extension allowed it for horizontally zoomed normal-scroll backgrounds. On Guardian Heroes the extension reduced the VDP2 CPU metric (median 45% to 40%) and the `VDP2DrawNormalScrollBG` self-sample share (11.38% to 9.28%). Overall emulation FPS did not improve (55.2 to 54.65), and main-thread time stayed flat. The extension did not pass a pixel-equivalence test and was removed.

### VDP1 memory-source branch hoisted out of the texel loop

Hypothesis: select the threaded/non-threaded `VDP1Memory` reference once per textured scanline instead of rechecking it for each texel read. Reads retained the same `ReadVRAM` mapping and endian handling.

VF2 A/B, same savestate, simpleperf on; performance-log medians grouped by command count:

| VDP1 commands/frame | Base FPS / VDP1 CPU | Candidate FPS / VDP1 CPU |
|---|---:|---:|
| 240–279 | 56.6 / 31% | 54.5 / 37% |
| 280–319 | 55.9 / 31% | 55.3 / 34% |
| 360+ | 50.4 / 40% | 48.7 / 45% |

The candidate raised VDP1 CPU use and did not improve FPS. It was removed.

## What the profiles show

- Burning Rangers profile: emulation thread 39.87% of process samples, SCSP 22.24%, VDP2 17.14%, VDP1 11.95%; `VDP1Cmd_Handle` was 9.40% of process samples. Main-thread waits were about 3.25 ms for sound, 0.47 ms for VDP1 and 0.01 ms for VDP2 in that capture.
- VF2 profile: emulation thread 27.41%, SCSP 24.20%, VDP2 23.83%, VDP1 16.74%; `VDP1Cmd_Handle` self was 12.22%. VDP1 address samples mapped to the textured-line path used by distorted sprites: texel fetch/decode, texture-stepper advancement, per-pixel clipping and pixel plotting. The performance logger showed median VDP1 wait of 0.96 ms/frame and VDP2 wait of 0.00 ms/frame.
- A high renderer-thread CPU share alone does not show that the renderer limits emulation FPS. Track emulation throughput and per-thread wait time; the tested VDP1/VDP2 micro-optimizations mostly changed worker cost without moving the frame-rate limit.
- SCSP and the SH-2 emulation thread remain substantial costs. The threaded-audio A/B below measures their interaction; any audio-thread test must also track underruns and latency.

## Threaded SCSP A/B — follow-up

A temporary `debug.gbsat.force_scsp` switch forced the SCSP worker on or off without changing saved game preferences. Runs used the same VF2 and Burning Rangers savestates and profile build, in an on/off/off/on sequence; each simpleperf capture was 20 seconds at 1 kHz. All runs confirmed the title and `state loaded` in logcat. The diagnostic switch was removed after measurement.

| Game | Threaded SCSP | Median emu FPS | Frame time | Sound wait | Emu-thread CPU | SCSP-thread CPU | Audio underruns/s |
|---|---|---:|---:|---:|---:|---:|---:|
| VF2 | On | 53.7 | 17.96 ms | 4.88 ms | 62% | 53% | 17 |
| VF2 | Off | 40.45 | 24.22 ms | 0 ms | 92% | 0% | 51 |
| Burning Rangers | On | 30.9 | 33.19 ms | 4.55 ms | 77% | 39% | 50 |
| Burning Rangers | Off | 25.6 | 38.40 ms | 0 ms | 94% | 0% | 54 |

The renderer remained CPU (OpenGL), with threaded VDP on and adaptive frame skip. `audio_xruns` stayed at zero in all four groups. Turning off the worker removes `wait_sound_ms` but shifts SCSP work onto the SH-2 emulation thread; FPS falls and audio underruns rise. Keep automatic/per-game threaded audio behavior on this device. Do not treat `wait_sound_ms` by itself as evidence that disabling the SCSP thread will improve speed.

Filtering the same simpleperf profiles by CPU confirms the existing affinity policy: on VF2 with SCSP on, 94.6% of the emulation thread's CPU samples were on CPU 3 while SCSP, VDP1 and VDP2 shared CPUs 0–2. With SCSP off, 99.3% of its samples were on CPU 3 while render workers remained on CPUs 0–2. This supports keeping the current placement; it describes where the thread ran, not continuous saturation of CPU 3.

## CPU frequency, thermal state and hardware counters

With the same VF2 gameplay state loaded, 25 one-second samples of `scaling_cur_freq` reported 1,992,000 kHz on all four CPUs. The RK3568 exposes one `cpufreq-dt` policy covering CPUs 0–3, with the `performance` governor and a 1,992,000 kHz maximum. Thermal status was 0; the final CPU/SoC reading was 60.0 °C, below the reported 70 °C hot-throttling threshold. This capture shows no evidence that clock scaling or thermal throttling limited this workload. The frequency value is the kernel driver's reported rate, not an independent clock probe.

A separate 12-second `simpleperf stat --per-thread` capture on the normal signed build counted cycles, instructions, generic cache misses and branch misses. The dominant emulation worker (Android name `Thread-15`) retired 6.92 billion instructions in 15.71 billion cycles (0.44 IPC); VDP2 retired 18.50 billion in 16.07 billion cycles (1.15 IPC). Generic cache misses were 0.37% of emulation-thread instructions and 0.20% of VDP2 instructions. These counters do not expose DRAM latency or bandwidth and the generic cache event does not identify a specific cache level, so they cannot rule out memory stalls. They provide no positive evidence for a cache-thrashing bottleneck or a justified memory-access rewrite; they also cannot distinguish dependency stalls from other causes of low IPC.

## Decision and status

No renderer optimization or diagnostic override from these experiments remains in source. The normal, signed build 420 was restored on the RG DS and at the local Gradle release APK path. No GitHub build was triggered for these measurements.

Raw VDP1/VDP2 captures, SCSP A/B CSVs, frequency samples, hardware-counter output, `simpleperf` reports, address maps and run logs are preserved locally under `C:\.android\gbsat_vdp1clip_ab` and `C:\.android\gbsat_cpu_profile`. The latest frequency check is in `vf2-cpu-frequency-20261030.csv`, `vf2-thermal-20261030.txt` and `vf2-frequency-perf-log.csv`; the hardware-counter run is `vf2-hw-counters-20261030.txt`. The app and core source trees contain no temporary profile flags or experimental renderer changes.
