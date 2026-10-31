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

### Ymir upstream review (2026-10-30)

Fetched `upstream/main` and reviewed its 89 commits after the shared 2026-10-18 base (`8cdf0a5`, Ymir 0.4.2 development line). The post-base changes are concentrated in Ymir's SDL3 desktop Vulkan frontend, its generic Vulkan resource/synchronization layer, and build workflows. Recent examples include Vulkan queue allocation and dispatcher fixes (`45b68d958`, `9a1ac5369`) and Vulkan SDK compatibility (`fe439e98a`). The changed upstream core files do not include SH-2, SCU, SCSP, or Saturn VDP emulation/performance code. GBSat uses its Android-specific VDP renderers rather than those SDL3 frontend resources, so there is no safe wholesale update or direct performance cherry-pick identified in this range. Revisit individual Vulkan lifecycle fixes only if a matching defect appears in GBSat's own renderer, with Android device validation.

### Vulkan presentation clarification

The CPU-readback fallback recorded in the build 420/422/423 sections above is historical evidence for those specific runs, not a claim that the current source always presents Vulkan frames through CPU memory. Current Vulkan code first imports GBFrame's `AHardwareBuffer` and records a GPU-to-GPU copy; it falls back to `vkCmdCopyImageToBuffer` only when that import path is unavailable or fails. Separately, `OnVulkanHardwareFrame` locks the buffer for CPU frame metadata (skip analysis and a reduced last-frame copy) before submitting the shared target. That CPU access is distinct from the presentation fallback and may still synchronize. The build 440 Daytona Vulkan window ran at 60.05 FPS, but its runtime log did not capture either the AHardwareBuffer-success or fallback message, so the active presentation route remains unconfirmed for that window.

## Risks and acceptance

- A GLES backend is a second GPU implementation, not a simple API switch. Shader translation, binding/resource mapping, barriers, context ownership, and driver behavior can all differ.
- The existing Vulkan-to-GBFrame path already uses shared Android hardware buffers, so eliminating readback is not an automatic GLES advantage.
- 4x in each dimension processes up to 16 times as many pixels as 1x. Higher scale is an image-quality choice and may reduce throughput.
- The experiment is successful only if it renders representative games correctly and provides repeatable A/B measurements. Performance parity or improvement is not assumed.
