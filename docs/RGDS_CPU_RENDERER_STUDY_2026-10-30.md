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

The same RG DS measurements justify `threadedAudio=true` for Burning Rangers on Entry devices: its existing low-tier profile already recommends threaded SCSP, but that recommendation was skipped for four-A55 Entry devices just like NiGHTS. The measured results were 30.9 FPS On vs 25.6 Off, with fewer audio underruns (50 vs 54/s). The Entry profile now carries this tested setting too.

After the Entry profile update, local signed build 420 passed the app/GBFrame unit suites and vital lint, then was installed over the existing app. A fresh Burning Rangers intent launch confirmed the savestate loaded and, with the UI set to Auto, perf-log rows reported `threaded_scsp=1`. The 20-row confirmation capture had 34.9 median emu FPS, 32.88 ms/frame and 47 median audio underruns/s. This verifies profile selection; it is not a new A/B comparison. No GitHub build was run.

## VF2, Daytona and Guardian Heroes — Entry SCSP A/B

Each run relaunched the exact ROM intent and confirmed the same existing gameplay savestate loaded. Only threaded SCSP changed, in A/B/B/A order. Results are grouped by 200–399 VDP1 commands/frame to compare similar rendering load. VF2's 1 kHz simpleperf captures include sampling overhead; Daytona and Guardian Heroes used the ordinary performance logger.

| Game | Off samples | On samples | Median FPS Off -> On | Sound scheduler ms Off -> On | Audio underruns/s Off -> On |
|---|---:|---:|---:|---:|---:|
| Virtua Fighter 2 | 52 | 54 | 41.9 -> 55.3 | 11.06 -> 0.49 | 50 -> 10 |
| Daytona USA CCE | 20 | 14 | 44.5 -> 57.7 | 7.88 -> 0.79 | 38 -> 1 |
| Guardian Heroes | 40 | 40 | 47.3 -> 57.4 | 8.85 -> 0.62 | 45 -> 5 |

All three improved consistently with threaded SCSP. The measured Entry profiles now recommend it for these games; manual per-game On/Off remains honored. After adding the entries, local signed build 420 passed the app/GBFrame unit suites and vital lint. Intent launches of all three games loaded their savestates and confirmed `threaded_audio=auto` with `threaded_scsp=1` in the perf log. No GitHub build was run.

Filtering the same simpleperf profiles by CPU confirms the existing affinity policy: on VF2 with SCSP on, 94.6% of the emulation thread's CPU samples were on CPU 3 while SCSP, VDP1 and VDP2 shared CPUs 0–2. With SCSP off, 99.3% of its samples were on CPU 3 while render workers remained on CPUs 0–2. This supports keeping the current placement; it describes where the thread ran, not continuous saturation of CPU 3.

## NiGHTS on the RG DS — SCSP A/B and profile fix

NiGHTS was relaunched through its ROM intent and the existing gameplay savestate was confirmed loaded on every run. CPU/OpenGL, Low, 1x, threaded VDP, adaptive frame skip, Fast 128-cycle sync and the SH-2 JIT stayed fixed. A/B/B/A runs changed only threaded SCSP; each run started from the same saved state. To reduce scene variation, results below use the 250–349 VDP1-command bucket:

| Run | SCSP | Samples | Median emu FPS | Frame ms | Sound scheduler ms | Emu CPU % | SCSP CPU % | Audio underruns/s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| A1 | Off | 16 | 36.8 | 27.56 | 11.29 | 97 | 0 | 44 |
| B1 | On | 17 | 49.2 | 19.98 | 0.69 | 74 | 51 | 29 |
| B2 | On | 12 | 48.6 | 20.01 | 0.79 | 72 | 50 | 32 |
| A2 | Off | 10 | 36.8 | 26.35 | 11.30 | 97 | 0 | 44 |

Combined medians were 36.8 -> 48.7 emu FPS (+32%), 26.56 -> 19.99 ms/frame, 11.29 -> 0.74 ms in the sound scheduler, and 44 -> 31 audio underruns/s. SCSP wait when threaded on was about 4.2 ms, while VDP1/VDP2 waits stayed below 0.2 ms. The CPU emulation thread was the bottleneck; its work was reduced by moving SCSP execution off it. The result repeated across both On runs and both Off runs.

The Auto path had kept an old per-game Off result, and GBSat applied `threadedAudio` profiles only to devices with at most two fast cores. The RG DS is Entry (four Cortex-A55 cores), so NiGHTS's existing `low.threadedAudio=true` recommendation was skipped. GBSat now accepts a measured Entry-tier SCSP recommendation and lets it supersede a cached Auto result; an explicit per-game On/Off choice still wins. NiGHTS has `entry.threadedAudio=true` in `game_profiles.json`.

Local release build 420 passed the app and GBFrame unit suites and vital lint, then was installed over the existing app without clearing data. A fresh intent launch again loaded the savestate; with the UI still set to Auto, perf logs confirmed `threaded_scsp=1`. The 33-row post-install capture measured 50.3 median emu FPS, 20.27 ms/frame, 0.63 ms sound scheduler time and 28 median audio underruns/s. This validates the profile selection and matches the A/B direction. No GitHub build was run.

## Adaptive graphics, CPU sync and SH-2 recompiler — five game saves

Tests ran on the RG DS CPU/OpenGL renderer at Low/1x. Each run used the exact ROM intent and the existing gameplay savestate, with A/B/B/A ordering (15-second performance windows; the first Guardian Heroes Auto baseline window was 20 seconds). Results pool rows from shared 50-command VDP1 buckets with at least two samples per condition, reducing scene-load differences. For CPU sync, the recompiler was forced off in both conditions. For the recompiler test, Fast sync (128 cycles) stayed fixed. Adaptive graphics compares Auto with Off; other core settings stayed fixed. Perf logs and per-run logcat files are preserved locally under `C:\.android\gbsat_config_ab_2026-10-30`.

| Game | Adaptive Auto -> Off: emu FPS | Shown FPS | Audio underruns/s | CPU sync Fast -> Accurate: emu FPS | Recompiler On -> Off: emu FPS |
|---|---:|---:|---:|---:|---:|
| NiGHTS | 43.4 -> 31.7 | 22.7 -> 31.7 | 38 -> 51.5 | 38.0 -> 34.7 | 46.7 -> 37.6 |
| Virtua Fighter 2 | 52.2 -> 43.2 | 13.8 -> 21.7 | 18 -> 44 | 45.7 -> 42.8 | 56.5 -> 46.8 |
| Daytona USA CCE | 53.6 -> 41.3 | 27.2 -> 41.4 | 14 -> 42.5 | 42.8 -> 40.4 | 55.7 -> 42.6 |
| Guardian Heroes | 57.7 -> 51.8 | 31.6 -> 51.9 | 3 -> 29 | 55.2 -> 52.9 | 57.3 -> 54.9 |
| Burning Rangers | 25.4 -> 25.7 | 16.7 -> 25.6 | 54 -> 54 | 25.9 -> 26.6 | 27.1 -> 28.5 |

Adaptive Auto improved emulation throughput and reduced audio underruns in NiGHTS, VF2, Daytona and Guardian Heroes, while lowering displayed frame rate because it skipped rendering. In Burning Rangers, throughput and underruns were effectively unchanged, but Off displayed more frames. The measured Entry profile now disables Adaptive graphics for Burning Rangers; other tested games keep Auto through the Low preset.

Fast sync beat Accurate in the four games where the core could use 128-cycle slices. Burning Rangers remained at 32 cycles in both conditions because its `PreciseSH2Sync` game-database flag clamps the requested setting. The approximate recompiler improved all four eligible games; Burning Rangers also remained on the interpreter because that same 32-cycle rule disables the approximate JIT. Keep Fast sync and the recompiler enabled as the device defaults, preserving the core's Burning Rangers exception.

## CPU frequency, thermal state and hardware counters

With the same VF2 gameplay state loaded, 25 one-second samples of `scaling_cur_freq` reported 1,992,000 kHz on all four CPUs. The RK3568 exposes one `cpufreq-dt` policy covering CPUs 0–3, with the `performance` governor and a 1,992,000 kHz maximum. Thermal status was 0; the final CPU/SoC reading was 60.0 °C, below the reported 70 °C hot-throttling threshold. This capture shows no evidence that clock scaling or thermal throttling limited this workload. The frequency value is the kernel driver's reported rate, not an independent clock probe.

A separate 12-second `simpleperf stat --per-thread` capture on the normal signed build counted cycles, instructions, generic cache misses and branch misses. The dominant emulation worker (Android name `Thread-15`) retired 6.92 billion instructions in 15.71 billion cycles (0.44 IPC); VDP2 retired 18.50 billion in 16.07 billion cycles (1.15 IPC). Generic cache misses were 0.37% of emulation-thread instructions and 0.20% of VDP2 instructions. These counters do not expose DRAM latency or bandwidth and the generic cache event does not identify a specific cache level, so they cannot rule out memory stalls. They provide no positive evidence for a cache-thrashing bottleneck or a justified memory-access rewrite; they also cannot distinguish dependency stalls from other causes of low IPC.

## Decision and status

No experimental renderer code or diagnostic override from these experiments remains in source. The measured NiGHTS/VF2/Daytona/Guardian Heroes Entry-tier SCSP recommendations and the Burning Rangers Entry-tier Adaptive graphics opt-out are retained. Local signed build 421 passed the app/GBFrame unit suites and vital lint, was installed on the RG DS and is at the Gradle release APK path. A fresh Burning Rangers intent launch loaded its savestate and reported `frameskip=off`, Fast sync requested, and the core's effective 32-cycle/JIT-off exception. The device was returned to sleep after validation. No GitHub build was triggered.

Raw VDP1/VDP2 captures, SCSP A/B CSVs, frequency samples, hardware-counter output, `simpleperf` reports, address maps and run logs are preserved locally under `C:\.android\gbsat_vdp1clip_ab` and `C:\.android\gbsat_cpu_profile`. The Adaptive graphics, CPU sync and recompiler A/B captures and per-run logs are under `C:\.android\gbsat_config_ab_2026-10-30`; `guardian-adaptive-off-B1-fixed.csv` is the clean replacement for the first mixed Guardian Heroes capture. NiGHTS SCSP captures are `nights-scsp-off-A1-20261030.csv`, `nights-scsp-on-B1-20261030.csv`, `nights-scsp-on-B2-20261030.csv` and `nights-scsp-off-A2-20261030.csv`; its installed-profile validation is `nights-entry-profile-validate-20261030.csv`. The VF2, Daytona and Guardian Heroes captures are named `vf2-scsp-*`, `daytona-scsp-*-20261030.csv` and `guardian-scsp-*-20261030.csv`; Auto-profile validations are `vf2-entry-profile-validate-20261030.csv`, `daytona-entry-profile-validate-20261030.csv` and `guardian-entry-profile-validate-20261030.csv`. The latest frequency check is in `vf2-cpu-frequency-20261030.csv`, `vf2-thermal-20261030.txt` and `vf2-frequency-perf-log.csv`; the hardware-counter run is `vf2-hw-counters-20261030.txt`. The app and core source trees contain no temporary profile flags or experimental renderer changes.
