# Experimental GLES VDP renderer A/B

Status: the experimental Ymir GLES VDP backend is selectable on branch `codex/gles-vdp-ab` and rendered Virtua Fighter 2 on the RG Rotate. The first A/B showed a large performance regression against Vulkan and the software VDP, so the mode remains opt-in and is not suitable for the default or mainline yet.

## Goal

Add an opt-in Ymir hardware VDP renderer using OpenGL ES compute, with internal scales 1x through 4x, and compare it to the existing Vulkan hardware renderer on the RG Rotate. Keep the software renderer and Vulkan as-is and leave the current default unchanged until repeated game tests justify a change.

## Current architecture

- `SoftwareVDPRenderer` composes the Saturn frame on the CPU at the emulated output resolution. The Android OpenGL ES presenter displays that completed frame; scaling its texture would not increase Saturn rendering detail.
- `VulkanVDPRenderer` runs Ymir VDP1/VDP2 compute shaders on the GPU. Its HLSL sources are compiled to SPIR-V, and its hardware renderer supports internal scaling.
- The shared GBFrame presenter already displays Vulkan output through `AHardwareBuffer`/EGLImage, with a CPU readback fallback. An A/B backend must preserve GPU-to-GPU delivery so readback does not distort the result.
- GBFrame commit `1274a71` adds a shared GLES render context, GPU-writable AHardwareBuffer targets, and shared GL-sync handoff to its presenter. Its EGL config prefers window+pbuffer support and falls back to the old window-only config, leaving the GLES VDP path unavailable on devices without a pbuffer.
- Local `:app:externalNativeBuildDebug` passed with that GBFrame commit integrated. This validates the current interface compiles in the Android app; it does not yet exercise the shared context on-device.

## Implementation route

1. Device capability check on RG Rotate: Android 12, ARM Mali-G52, OpenGL ES 3.2 (`ro.opengles.version=196610`; SurfaceFlinger reports driver `v1.r34p0`). ES 3.2 includes compute shaders.
2. Compiled all 20 Ymir VDP compute-shader variants with DXC using GL-compatible SPIR-V layout, translated them to GLSL ES 3.1 with SPIRV-Cross, then compiled and linked each on the RG Rotate's GLES 3.2 driver. Translation exposed two ES image-format gaps: VDP2's `r8ui` and `r16ui` outputs are represented by GLES-only `r32ui` variants. The existing Vulkan shader formats remain unchanged. Generated GLSL is checked in under `res/shaders/gles/vdp/`.
3. A clean local Gradle native build passed: `:app:externalNativeBuildDebug` / `BUILD SUCCESSFUL`. An earlier incremental CMake link against a stale mixed-PGO cache failed and was not used as validation.
4. Add an Android-only Ymir renderer type and resource/synchronization layer. Reuse the API-independent VDP command and parameter logic where practical; do not change the existing software or Vulkan paths.
5. Deliver completed GLES frames to the existing presenter without CPU readback. Preserve instrumentation and frame scale metadata.
6. Expose the experimental backend and 1x–4x scale in the shared GBFrame settings/quick menu only after the renderer initializes and produces correct frames; keep Vulkan and software selections available.
7. Build and validate locally, then run paired tests on the RG Rotate from the same savestate and scene at 1x, 2x, 3x, and 4x. Compare emulation FPS, displayed FPS, frame time, GPU time, and output correctness. Use NiGHTS, Daytona, Virtua Fighter 2, Guardian Heroes, and Burning Rangers; record each run and leave defaults untouched.

## Current implementation checkpoint

- Added the Android-only `OpenGLESVDPRenderer` Ymir type and the callback/context contract, without changing the selected renderer or app settings.
- Added move-only GLES RAII wrappers for compute programs, buffers, and 2D/array images. Shader programs load from the packaged `ymir-core-gles-shaders` resource filesystem and report compile/link logs with shader labels.
- Added a synchronous `gles::Buffer::Readback` path that maps only at explicit readback/debug/save synchronization points; it inserts shader/buffer visibility barriers and avoids any CPU copy on normal frame submission. The Android arm64 native build passed after this addition. It is not wired into a Ymir renderer yet.
- Added `gles::BufferFillKernel`, a small compute shader that fills aligned SSBO ranges without making a large CPU fill vector. On the RG Rotate's Mali-G52 GLES 3.2 driver, a native probe filled and mapped 1M words, then overwrote a subrange and verified all surrounding words were preserved. `:app:externalNativeBuildDebug` passed after this addition. Resource allocation and renderer dispatch still need to call the helper.
- Added checked `gles::Buffer::CopyTo` using `glCopyBufferSubData`, including source/destination bounds and same-buffer overlap checks. The RG Rotate native probe copied a 1M-word GPU buffer into a separate buffer and verified the copied range by mapped readback. This is the transfer primitive needed for VRAM uploads and VDP1 framebuffer synchronization in the GLES port; Ymir call sites are not wired yet.
- Added `Image::ReadbackRGBA8` for the GLES CPU-presentation fallback. It keeps its read framebuffer with the image, restores the prior read-FBO binding, and synchronizes shader writes before `glReadPixels`. This path is synchronous and intended only when the shared AHardwareBuffer target cannot be acquired.
- Extended the small SPIR-V reflection helper to retain resource names, allowing GLES to assign translated combined-sampler uniforms to their original Ymir binding numbers. The GLES layer now maps reflected SRV/UAV bindings, buffer/image resources, compute dispatch, and memory barriers.
- Added an Android frame-target wrapper that imports GBFrame's `AHardwareBuffer` as an EGLImage, copies the completed GLES image into it, and emits the shared-context GLsync fence expected by GBFrame.
- On 2026-10-30, an RG Rotate GLES 3.2 probe compiled/linked all 20 translated shaders and resolved every combined sampler uniform from the matching SPIR-V binding names (5 combined samplers total). This validates shader program setup on the Mali-G52, not Ymir rendering.
- `:app:externalNativeBuildDebug` passed for `arm64-v8a` after the resource, dispatch, and frame-target layer changes. No Saturn rendering is wired to the new path yet.
- Ymir's renderer resource allocation, API methods, Android frame-target callback, and menu selection are wired. On-device shader initialization completed on the RG Rotate, with `gles: ready` in logcat. Game image correctness still needs a deliberate visual comparison, and the first performance data shows that the current synchronization path needs profiling before this backend is useful.

## First on-device comparison (RG Rotate, build 422)

On 2026-10-30, build 422 was installed locally and Virtua Fighter 2 was launched from Slot 1 (`Virtua Fighter 2_1.state`, dated 27/10/2026 22:56). The renderer was switched in the same session and Slot 1 was reloaded for each mode. Settings were 1x for the CPU and Vulkan reference runs; the GLES path was measured at 1x, 2x, and 4x. The displayed values below are single HUD snapshots after allowing each run to settle, so they are preliminary rather than a statistically complete benchmark.

| Renderer / scale | Emulation FPS | Speed | Frame time | CPU / GPU shown by app |
| --- | ---: | ---: | ---: | ---: |
| CPU VDP + OpenGL ES presenter, 1x | 59.8–59.9 | 99.6–99.9% | 9.9–10.0 ms | 19.8–20.4% / 30.1–30.3% |
| Vulkan VDP, 1x (first pass) | 56.4 | 93.9% | 27.1 ms | 10.1% / 24.3% |
| Vulkan VDP, 1x (repeat) | 33.9 | 56.5% | 25.6 ms | 8.8% / 55.5% |
| Vulkan VDP, 1x (build 423 repeat) | 57.1 | 95.2% | 17.4 ms | 10.6% / 14.6% |
| GLES VDP, 1x (pass 1) | 16.6 | 27.7% | 59.0 ms | 11.8% / 4.9% |
| GLES VDP, 1x (pass 2) | 21.3 | 35.6% | 44.5 ms | 14.2% / 0.0% |
| GLES VDP, 2x | 16.3 | 27.2% | 59.3 ms | 12.3% / 3.9% |
| GLES VDP, 4x | 17.4 | 29.0% | 50.5 ms | 12.8% / 4.9% |

The current GLES implementation is substantially slower than the CPU renderer in this scene and below the fresh Vulkan repeat. Increasing internal resolution from 1x to 4x did not cause a clear additional FPS drop, which suggests the large loss is in the backend's command/dispatch path rather than pixel throughput; this is an inference, not a profiler result. The HUD's GPU utilization is not reliable for diagnosing GLES here: it reported 0–5% while the renderer produced frames at 16–21 FPS.

The build 422 Vulkan samples logged `AHardwareBuffer import failed; using CPU readback presentation`; build 423's repeat did too. This is not a GPU-to-GPU presentation-path comparison. For GLES, gralloc logged allocation of three 704x448 RGBA AHardwareBuffers for the process, and no fallback stage appeared; that supports (but does not prove) that the shared target path was used. The GLES initialization log reached `gles: ready`, and no GLES pipeline creation failure appeared after correcting the embedded resource path to include `vdp/`.

### Barrier batching check (build 423)

The first profile candidate was a redundant global memory barrier after every compute dispatch: the renderer also issued its own barrier before the next dispatch. That extra `glMemoryBarrier(GL_ALL_BARRIER_BITS)` was removed so Ymir's existing pending-barrier path handles resource visibility. Build 423 installed and initialized successfully, but Virtua Fighter 2 Slot 1 at GLES 1x still measured 16.2 FPS (27.0%, 61.0 ms), against 57.1 FPS (95.2%, 17.4 ms) for Vulkan in the same session and 59.8 FPS (99.6%, 9.9 ms) for CPU VDP. The change did not materially improve performance; redundant barriers were not the primary bottleneck.

Next diagnostic: instrument per-frame CPU time spent issuing VDP1/VDP2 dispatches, GL buffer uploads, frame-target copy, and `WaitFrame()` to isolate the stall. Keep GLES experimental and leave the device default on CPU/OpenGL until a profiled change yields correct frames and repeatable performance.

## Existing-renderer baseline (RG Rotate)

On 2026-10-30, the installed build 420 ran Virtua Fighter 2's existing Slot 1 state in both current modes. Each mode produced 12 one-second samples at 1x, 704x448, with frameskip enabled and nearly identical median VDP1 command counts (310 CPU / 312 Vulkan).

| Existing mode | Median emulation FPS | Median shown FPS | Median emulation ms/frame |
| --- | ---: | ---: | ---: |
| CPU renderer + OpenGL presenter | 60.0 | 29.9 | 9.28 |
| Vulkan VDP renderer + OpenGL presenter | 56.7 | 30.8 | 17.94 |

This is a useful device baseline, not the intended GPU-to-GPU comparison: “CPU (OpenGL)” means Ymir's software VDP renderer presented through OpenGL ES. During the Vulkan run, Ymir logged `AHardwareBuffer import failed; using CPU readback presentation`, so Vulkan's presentation path also fell back to CPU readback. The CPU-rendered route was modestly faster for this sampled scene; the result does not establish that an OpenGL compute VDP renderer would be faster than Vulkan. Raw performance log: `C:\.android\perf-log-rgrotate-before-gles.csv` (CPU window 18:02:54–18:03:05; Vulkan window 18:04:26–18:04:37). The device was returned to sleep after the run.

### Repeat check on the current installed build

On 2026-10-30, Virtua Fighter 2 Slot 1 was relaunched from its saved state for two runs in each existing renderer. Order was Vulkan → CPU/OpenGL → Vulkan → CPU/OpenGL. Each run collected 15–20 one-second samples at 1x and 704×448. The app reported `frameskip=on`, `CPU sync=fast`, `SH-2 JIT=on`, `threaded VDP=on`, and `CRT Royale`; both Vulkan launches logged the AHardwareBuffer import failure and used CPU readback.

| Renderer | Run 1, median FPS / frame time | Run 2, median FPS / frame time | Last 10 samples, median FPS / frame time |
| --- | ---: | ---: | ---: |
| CPU VDP + OpenGL ES presenter | 60.3 / 9.9 ms | 60.5 / 9.8 ms | 60.4–60.5 / 9.7–9.8 ms |
| Vulkan VDP + OpenGL ES presenter | 50.8 / 19.8 ms | 41.5 / 23.9 ms | 52.8 / 20.1 ms; 41.5 / 25.7 ms |

The CPU route repeated consistently near the 60 FPS target. Vulkan varied more and remained slower in both runs; its GPU fence wait was a median 8.7 ms and 12.8 ms in each run's last 10 samples. This is a comparison of the available CPU and Vulkan VDP paths, not an OpenGL compute VDP A/B. The result is also specific to this VF2 state and scene. Raw logs: `C:\.android\renderer-ab-cpu.csv`, `renderer-ab-cpu-2.csv`, `renderer-ab-vulkan.csv`, and `renderer-ab-vulkan-2.csv`.

### Controlled renderer pass and upload strategy (builds 426–427)

On 2026-10-30, RG Rotate build 426 was installed with per-frame GLES instrumentation. Virtua Fighter 2 was repeatedly reloaded from Slot 2 (30/10/2026 18:00). To remove adaptive skipping and audio-thread auto-selection from this pass, the in-game settings were Adaptive graphics Off, Threaded audio Off, frameskip Off, CPU sync Fast, SH-2 recompiler On, 1x scale, Nearest filter, and GTU shader. Samples cover the title/ranking sequence; the active Saturn output mode changed between 352x240 and 320x224 while the save progressed, so treat these as paired scene windows rather than frame-identical replay.

| Renderer / upload route | Resolution in samples | Samples | Median emulation FPS | Median shown FPS | Median emulation ms/frame | Median GLES buffer-upload CPU time |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| CPU VDP + GL presenter | 352x240 | 37 | 59.9 | 59.9 | 8.75 ms | — |
| Vulkan VDP | 352x240 | 20 | 52.2 | 52.6 | 19.6 ms | — |
| GLES VDP, direct `glBufferSubData` | 352x240 | 9 | 2.9 | 0.0 | 313 ms | 177 ms/frame |
| GLES VDP, per-frame staging + `glCopyBufferSubData` | 320x224 | 52 | 12.8 | 0.0 | 73 ms | 48 ms/frame |

The direct-upload path regressed sharply on this VDP workload. Returning to the pre-existing staging path improved the GLES rate about 4.4x in these short windows, but the picture remained black and the presenter reported no displayed frames. The staged run spent about 48 ms/frame in buffer uploads, with roughly 13 uploads per frame; the direct run issued roughly 186 uploads per frame and spent about 177 ms/frame there. Since the game changed output mode between those two runs, the direct-versus-staging ratio is directional rather than a clean same-resolution A/B. Neither GLES run is usable for image-quality or 2x/4x testing until it presents correct frames.

During the earlier build 425 run, Mali's GLES driver aborted in `glReadPixels` after GBFrame could no longer import a GLES AHardwareBuffer as an EGLImage. `llvm-addr2line` resolved the application frame to `gles::Image::ReadbackRGBA8()` called from `OpenGLESVDPRenderer::VDP2EndFrame()`. GBFrame now has a CPU-readable AHardwareBuffer presenter fallback that honors the buffer stride, intended to avoid this driver readback path. Builds 426–427 compiled and ran, but the EGLImage import failure did not recur in those runs, so the new CPU-lock fallback itself remains unexercised on-device.

The actionable bottleneck in the controlled GLES run is CPU time spent updating Ymir buffers, and the direct route made that worse for this scene. The renderer remains experimental and its resolution-scaling comparison is deferred. Raw logs: `C:\.android\perf-log-rgrotate-build426.csv` and `C:\.android\perf-log-rgrotate-build427-staging.csv`. Both were local validation builds; no GitHub build was started.

### RG Rotate follow-up (builds 428–432)

The next pass used the Virtua Fighter 2 ROM intent and its automatically loaded gameplay state. It found two distinct correctness issues:

1. GBFrame tracked the GLES producer's in-flight AHardwareBuffer with one global `writingSlot`. Ymir can acquire another target before delivering an earlier frame, so a later acquire could replace that reservation and invalidate the earlier submission. GBFrame now tracks the writer on each slot. Build 430 confirmed that target submissions reached the presenter after this fix.
2. Ymir's final GLES composite remained black. In build 432, one-time probes found nonzero pixels in VDP2 layer outputs (L0: 5/25, L1: 5/25, L4: 5/25, L6: 1/25), while the completed composite was zero in all 25 samples. The remaining image failure is in Ymir's GLES composition path, before GBFrame presentation. The temporary probes were removed after the measurement.

Build 434 ruled out a GLES resource-binding limit: the RG Rotate reports 70 SSBO bindings and 21 image units, enough for the current binding indices, and the first compute dispatch reported no GL error. The OpenGL ES `u1` binding at image unit 17 is therefore valid on this device; the composition failure still needs shader/data-path investigation.

Build 430–432 profiles were collected on the RG Rotate (T618/Mali-G52), Virtua Fighter 2, 1x, CPU sync Fast, SH-2 JIT on, threaded VDP on, threaded audio off, frameskip off, nearest filter, and GTU shader. The game changed its active output mode during the run (GLES 320x224; CPU/Vulkan 352x240), so these same-save-state samples are directional rather than frame-identical.

| Renderer | Samples | Median emulation FPS | Median shown FPS | Median emulation frame time | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| CPU VDP + OpenGL presenter | 14 | 60.0 | 30.4 | 8.59 ms | Correct image |
| Vulkan VDP | 15 | 51.3 | 51.5 | 19.65 ms | Correct image |
| Experimental GLES VDP | 13 | 12.2 | 0.0 | 81.09 ms | Black output; not a valid performance comparison |

The GLES sample spent a median 59.25 ms/frame uploading buffers, with 14 uploads/frame; dispatch submission cost was 8.72 ms/frame. Since the output is black, this rate cannot be used to judge the renderer's real performance, and no 2x–4x result can be interpreted. Keep GLES opt-in and experimental until the composition shader produces the expected image and shown FPS.

Raw logs: `C:\.android\gles-buffer-probe-build430.csv`, `vf2-cpu-build432.csv`, and `vf2-vulkan-build432.csv`. Builds 428–432 were local; no GitHub build was started.

## Daytona USA 1x threshold pass (RG Rotate, build 440)

On 2026-10-30, Daytona USA: Championship Circuit Edition was measured on the RG Rotate (T618/Mali-G52) from Slot 1 (27/10/2026 22:56). The ROM was launched with a file URI; Slot 1 was reloaded before each renderer window. All runs used 1x, 352x224, CPU sync Fast, SH-2 JIT on, threaded VDP on, frameskip off, Nearest filtering, and GTU shader. Each window contains 18 one-second samples.

| Renderer | Median emulation FPS | Median shown FPS | Median emulation ms/frame | Other timing |
| --- | ---: | ---: | ---: | --- |
| CPU (OpenGL) | 60.05 | 60.05 | 9.13 ms | — |
| Vulkan | 60.05 | 60.05 | 15.53 ms | 7.78 ms fence wait/frame; 0.49 ms frame callback/frame |
| Experimental GLES | 20.80 | 20.80 | 47.19 ms | 31.88 ms dispatch CPU/frame; 12 dispatches/frame; 4.87 ms upload CPU/frame; 2.31 ms fence wait/frame |

The GLES 1x result did not clear the 30 FPS threshold, so 2x was not measured. The GLES image path reported no CPU readback time. Source inspection confirms that this build's buffer upload implementation uses `glBufferSubData`; the CSV's `gles_upload_strategy` label was stale and incorrectly said `staging+glCopyBufferSubData`. The label is corrected in source for subsequent builds. CPU/GLES raw log: `C:\.android\daytona-build440.csv`; Vulkan raw log: `C:\.android\daytona-build440-vulkan.csv`.

### Dispatch-check candidate (build 441, reverted)

To isolate the cost of checking `glGetError()` after every dispatch, a local Release candidate retained per-dispatch checks in Debug and skipped them in Release. On the same game and device, its samples fell to 1.0 emulation FPS with roughly 900 GLES dispatches/frame and about 1.6–1.8 seconds of measured dispatch CPU per frame. The candidate was rejected and build 440 was restored on the device; the result is a severe regression signal, not an accepted optimization. Raw log: `C:\.android\daytona-build441.csv`.

### Redundant GLES state-bind cache (build 443)

Build 443 added per-context caching for the active program, indexed buffer bindings, texture units, and image units, invalidating the cache when resources or imported frame targets change. Repeating the same Daytona Slot 1 / 1x window produced 20.90 median FPS over 18 samples, versus 20.80 FPS in build 440. Dispatch CPU time moved from 31.88 to 30.47 ms/frame, while total frame time remained about 47.6 ms and the result stayed below the 30 FPS threshold. This is too small a throughput change to count as a useful optimization; keep it experimental and investigate constant-buffer upload/dispatch cost next. Raw log: `C:\.android\daytona-build443-gles.csv`.

### GPU overlay and constant-upload probe (build 444)

The RG Rotate overlay showed 71.2% GPU while Daytona ran at 60 FPS in CPU (OpenGL) mode. This is GBFrame's own timed OpenGL presentation work, not Android-wide GPU utilization: the timer wraps the presenter's `glClear` and full-screen `glDrawArrays`, and CPU mode's Saturn VDP is software-rendered. It does not include Ymir GLES compute dispatches, so the overlay cannot be used to estimate total GPU load for the experimental GLES renderer. On GLES mode the overlay showed 3.5% during one sample, which is only the presenter timer and is not evidence that the GLES VDP compute is cheap.

Build 444 added timing around the GLES per-dispatch constant-buffer `glBufferSubData` call. In the last 18 one-second Daytona rows, median emulation FPS was 24.25, dispatch CPU time 28.45 ms/frame, and the now-broader upload CPU counter 26.56 ms/frame across 21 uploads/frame. The matching build 443 18-row window was 20.90 FPS, 30.46 ms dispatch CPU/frame, and 4.82 ms upload CPU/frame, but these windows were not scene-paired: Daytona's VDP1 command count varied during play. The FPS difference is not evidence of a speedup. The combined upload counter is now much higher with constant uploads included; without a dedicated counter, it does not isolate their contribution. A repeated same-scene A/B is also needed to quantify throughput changes. Raw log: `C:\.android\daytona-build444-gles.csv`.

The GPU percentage is computed from asynchronous timer-query results divided by wall-clock time between overlay polls. It excludes unrelated applications and may omit draws when the query ring is still busy. Treat it as the measured duration of this app's timed GPU work, not a whole-device utilization meter.

### GLES 1x threshold and 2x scale check (build 458)

Build 458 was installed on the RG Rotate (T618/Mali-G52). Daytona USA: Championship Circuit Edition Slot 1 was launched by ROM intent and the saved state was confirmed in logcat before each capture. The fixed settings were 1x or 2x, CPU sync Fast, SH-2 JIT on, threaded VDP on, frameskip off, Nearest filter, and shader None. The video path for GLES uses the experimental hardware VDP renderer. Captures were 17–21 one-second samples each; the game advances without deterministic input replay, so these are same-save-state windows, not frame-matched playback.

| Scale / renderer | Samples | Median emulation FPS | Median shown FPS | Median emulation ms/frame | Samples >= 48 FPS | Saturn output mode |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| GLES 1x, first window | 17 | 57.6 | 57.6 | 17.34 ms | 14/17 | 352x224 |
| GLES 1x, repeat | 20 | 59.6 | 59.6 | 14.81 ms | 20/20 | 352x224 |
| Vulkan 1x | 21 | 59.9 | 59.9 | 11.83 ms | 20/21 | 352x224 |
| GLES 2x | 21 | 38.6 | 38.6 | 26.45 ms | 1/21 | 704x448 |
| Vulkan 2x | 20 | 50.2 | 50.2 | 19.72 ms | 17/20 | 704x448 |

Both 1x GLES windows cleared the 80% target using the 60 FPS Saturn rate, and the Vulkan 1x window remained near full speed. The Vulkan and GLES results are close in aggregate but the command counts and scene progression differ, so the small frame-time difference is not a controlled renderer speed ranking. At 2x both paths produced coherent images at 704x448; GLES was slower, with one sample at or above 80% speed and a median of 64.3% of target. Vulkan's 2x median was 83.7%. This supports retaining GLES at 1x for further work and treating 2x as a quality/performance tradeoff on this device. It does not justify changing any default.

Build 458 also removed explicit memory-barrier requests after CPU-originated `glBufferSubData` updates, narrowed compute-write barriers to the shader storage/image, texture fetch/update, buffer update, and atomic-counter domains used by Ymir, and added an optional coherent persistent map for the per-frame constants upload ring when `GL_EXT_buffer_storage` and its entry point are available. Other GLES buffers continue to use dynamic uploads. The renderer keeps the existing dynamic allocation/upload fallback if the extension or mapping is unavailable. The 1x output remained coherent and dispatch CPU time in the repeat window was around 1.3 ms/frame; because several changes landed together and the VDP1 command stream varies, the capture does not isolate each change's contribution. The CSV `gles_upload_strategy` field still reports `glBufferSubData` for the renderer as a whole and does not distinguish the persistently mapped constants ring.

Raw logs: `C:\.android\daytona458-gles1x.csv`, `daytona458-gles-repeat1x.csv`, `daytona458-vulkan1x.csv`, `daytona458-gles2x.csv`, and `daytona458-vulkan2x.csv`. These were local build 458 captures; no GitHub build was started. After the comparison, the device was restored to experimental GLES 1x, Nearest, and shader None.

### 2x throughput work on the RG Rotate (builds 463–465)

The current goal is at least 51 FPS (85% of Saturn's 60 FPS rate) at GLES 2x, with Daytona CCE Slot 1, frameskip off, CPU sync Fast, SH-2 JIT on, Nearest, and shader None. Each run used a clean app process and ROM intent, confirmed the saved state loaded, and checked `704x448` output and zero GLES CPU readback. These remain advancing gameplay windows from the same save, not frame-identical replays.

| Build | GLES change | Samples | Median emulation FPS | Samples >= 51 FPS | Median fence wait | Median VDP2 end |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 463 | Split VDP2 workgroups (sprite 64, composition 128) plus narrowed barriers | 20 | 46.5 | 8/20 | 4.22 ms | 5.49 ms |
| 464 | Composition workgroup 256 | 20 | 49.9 | 7/20 | 6.47 ms | 7.45 ms |
| 465 | Three frame contexts, composition restored to 128 | 20 | 49.15 | 8/20 | 7.74 ms | 8.56 ms |

Neither candidate reached the 51 FPS target. The 256-thread composition group moved the overall median upward in this window, but its fence wait and VDP2-end measurements also rose, while the scene and VDP1 command counts were not frame-matched; it is not a defensible win and was reverted. Three frame contexts did not reduce the measured fence wait and add a queued frame of presentation latency, so that change was also reverted. GLES 2x remains below target; no 3x or 4x work was performed for this stage.

Builds 464 and 465 were local arm64 release builds installed on the RG Rotate. Raw logs: `C:\.android\daytona464-gles2x.csv` and `C:\.android\daytona465-gles2x.csv`. The build 465 three-frame experiment is rejected; source has been restored to two frame contexts.

### VDP2 composition reuse experiments (builds 467–469)

Builds 467–469 tested whether 2x composition could reuse native-resolution background samples shared by horizontal subpixels. Daytona CCE Slot 1 was started by ROM intent and its auto-loaded state was confirmed. Each build used GLES 2x, 704x448, CPU sync Fast, SH-2 JIT on, frameskip off, Nearest, shader None, and zero CPU readback. The game advanced during each window, so VDP1 command counts and the scene were not frame-matched.

| Build / method | Cache off or reference | Cache enabled | Result |
| --- | --- | --- | --- |
| 467: four serial Compose calls per base pixel, passing a cache struct | 45 rows, median 54.6 FPS; VDP2 end 9.03 ms; fence 7.86 ms | 45 rows, median 14.9 FPS; VDP2 end 56.66 ms; fence 53.25 ms | Rejected. Serializing the four subpixels removed useful GPU parallelism and the large by-value cache added severe cost. |
| 468: four serial calls using invocation-local cache storage | — | 10 settled rows, median 22.1 FPS; VDP2 end 29.72 ms; fence 28.19 ms | Rejected. Avoiding the struct copy did not offset the serial execution. |
| 469: parallel output pixels with workgroup-shared horizontal samples | — | 8 settled rows, median 34.5 FPS; VDP2 end 19.56 ms; fence 18.79 ms | Rejected. This remained well below the target and added a workgroup barrier plus unconditional background loads. |

Build 467's cache-off median exceeded 51 FPS (85% of target) across its two A windows, but this is not a sustained pass: individual rows varied with the changing scene and VDP1 command count. The experiment did not establish that caching improves throughput. The compose cache and its diagnostic property were removed from source after build 469. No default was changed. Raw logs: `C:\.android\daytona467-glescompose-ab.csv`, `C:\.android\daytona468-globalcache-smoke.csv`, `C:\.android\daytona469-groupshared-smoke.csv`, and `C:\.android\daytona469-groupshared-off.csv`.

Build 470 rebuilt the clean compose shader and renderer without the cache experiment. On the RG Rotate, 21 settled samples from the same ROM intent and auto-loaded Slot 1 measured 49.6 FPS median (47.5 average), with 8/21 samples at or above 51 FPS; median VDP2 end was 8.42 ms and GLES fence wait 7.55 ms. The window does not meet the 85% target. Settings remained at 2x/704x448, Fast CPU sync, SH-2 JIT on, frameskip off, Nearest and shader None, with zero CPU readback. Raw log: `C:\.android\daytona470-gles2x-clean.csv`. The device reported no thermal throttling during this short capture. Source still has the experimental renderer's earlier barrier and workgroup changes; this result validates the cache-free path, not a new speedup from those changes.

### Ymir upstream review (2026-10-30)

Fetched `upstream/main` and reviewed its 89 commits after the shared 2026-10-18 base (`8cdf0a5`, Ymir 0.4.2 development line). The post-base changes are concentrated in Ymir's SDL3 desktop Vulkan frontend, its generic Vulkan resource/synchronization layer, and build workflows. Recent examples include Vulkan queue allocation and dispatcher fixes (`45b68d958`, `9a1ac5369`) and Vulkan SDK compatibility (`fe439e98a`). The changed upstream core files do not include SH-2, SCU, SCSP, or Saturn VDP emulation/performance code. GBSat uses its Android-specific VDP renderers rather than those SDL3 frontend resources, so there is no safe wholesale update or direct performance cherry-pick identified in this range. Revisit individual Vulkan lifecycle fixes only if a matching defect appears in GBSat's own renderer, with Android device validation.

### Constant-block cache candidate (build 448, rejected)

Build 448 added a per-frame-context shadow of each 256-byte constant block and skipped `glBufferSubData` only when the same buffer offset was reused with byte-identical contents. The candidate was installed on the RG Rotate and Daytona Slot 1 was launched by ROM intent. Across 35 one-second samples, including 18 settled samples, the cache recorded **zero hits**. In the settled window, median emulation speed was 22.8 FPS, dispatch submission cost 30.16 ms/frame, constant upload cost 25.90 ms/frame, and 11 constant uploads/frame. Since it never skipped an upload, this cache cannot improve this workload; its compare and shadow-copy overhead is unjustified. The cache and temporary per-constant counters were removed after the measurement. Raw log: `C:\.android\daytona448-cache.csv`.

### Vulkan presentation clarification

The CPU-readback fallback recorded in the build 420/422/423 sections above is historical evidence for those specific runs, not a claim that the current source always presents Vulkan frames through CPU memory. Current Vulkan code first imports GBFrame's `AHardwareBuffer` and records a GPU-to-GPU copy; it falls back to `vkCmdCopyImageToBuffer` only when that import path is unavailable or fails. Separately, `OnVulkanHardwareFrame` locks the buffer for CPU frame metadata (skip analysis and a reduced last-frame copy) before submitting the shared target. That CPU access is distinct from the presentation fallback and may still synchronize. The build 440 Daytona Vulkan window ran at 60.05 FPS, but its runtime log did not capture either the AHardwareBuffer-success or fallback message, so the active presentation route remains unconfirmed for that window.

### Daytona CCE 2x composition and upload-path visual corrections (builds 481–489)

All runs used the Rotate, Daytona CCE Slot 1 via ROM intent, GLES 2x at 704x448, Fast CPU sync, SH-2 JIT on, frameskip off, Nearest, shader None, and zero GLES CPU readback. The gameplay advanced from the same save, so these are not frame-identical comparisons.

The output-merger bounding-box candidate was rejected. Across 57 settled rows from build 485, median speed was 50.8 FPS (48.0 average; 28/57 rows at or above 51 FPS). The preceding build 484 window had a 54.2 FPS median (50.8 average; 40/58 rows at or above 51 FPS) with a higher median VDP1 command count (290 versus 161). Candidate VDP1 submission CPU was 0.81 ms/frame versus 0.67 ms/frame in the earlier window. Scene variation prevents attributing the FPS difference to the code, and the candidate showed no reliable gain; it was removed. Raw logs: `C:\.android\daytona484-baseline.csv` and `C:\.android\daytona485-bounds.csv`.

The black image seen in builds 481–483 came from the VDP2 compose-height limit being packed at word 10, the `enhancements` field. The shader's `composeHeight` is at packed word 11 (byte 44); build 484 corrected the offset and restored the image. Restoring conservative buffer/texture barriers in build 487 did not remove the visible artifacts. Reverting VDP2 workgroups to Ymir's original 32x1 layout in build 488 also did not remove them.

The matching build 458 and 459 screenshots show the first obvious missing scoreboard rows after the persistent-mapped upload change. Build 489 disables persistent-mapped and staged-copy uploads in this renderer, returning resource and constant updates to the direct `glBufferSubData` path. A visual check of the same Daytona CCE save at 2x shows the missing scoreboard spans and block corruption are gone. This was a visual-only validation; no FPS capture or renderer comparison was made, as requested. Build 489 is installed locally on the Rotate. No GitHub build was run.

## Risks and acceptance

- A GLES backend is a second GPU implementation, not a simple API switch. Shader translation, binding/resource mapping, barriers, context ownership, and driver behavior can all differ.
- The existing Vulkan-to-GBFrame path already uses shared Android hardware buffers, so eliminating readback is not an automatic GLES advantage.
- 4x in each dimension processes up to 16 times as many pixels as 1x. Higher scale is an image-quality choice and may reduce throughput.
- The experiment is successful only if it renders representative games correctly and provides repeatable A/B measurements. Performance parity or improvement is not assumed.
