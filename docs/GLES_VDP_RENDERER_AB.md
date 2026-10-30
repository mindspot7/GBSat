# Experimental GLES VDP renderer A/B

Status: shader translation and the GBFrame shared-context/AHardwareBuffer contract are complete on branch `codex/gles-vdp-ab`. The Ymir GLES VDP backend is under implementation; it is not selectable and does not render Saturn frames yet.

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
- Extended the small SPIR-V reflection helper to retain resource names, allowing GLES to assign translated combined-sampler uniforms to their original Ymir binding numbers. The GLES layer now maps reflected SRV/UAV bindings, buffer/image resources, compute dispatch, and memory barriers.
- The mapping check over the packaged artifacts found names for all 5 SPIRV-Cross combined samplers among the 20 translated shaders.
- `:app:externalNativeBuildDebug --rerun-tasks` passed for `arm64-v8a` after the resource and dispatch layer changes. This verifies native compilation/linking only; no Saturn rendering is wired to it yet.
- The remaining work is Ymir's renderer resource allocation and synchronization, frame delivery to GBFrame's shared GLES target, wiring the API methods, and runtime correctness/performance validation.

## Existing-renderer baseline (RG Rotate)

On 2026-10-30, the installed build 420 ran Virtua Fighter 2's existing Slot 1 state in both current modes. Each mode produced 12 one-second samples at 1x, 704x448, with frameskip enabled and nearly identical median VDP1 command counts (310 CPU / 312 Vulkan).

| Existing mode | Median emulation FPS | Median shown FPS | Median emulation ms/frame |
| --- | ---: | ---: | ---: |
| CPU renderer + OpenGL presenter | 60.0 | 29.9 | 9.28 |
| Vulkan VDP renderer + OpenGL presenter | 56.7 | 30.8 | 17.94 |

This is a useful device baseline, not the intended GPU-to-GPU comparison: “CPU (OpenGL)” means Ymir's software VDP renderer presented through OpenGL ES. During the Vulkan run, Ymir logged `AHardwareBuffer import failed; using CPU readback presentation`, so Vulkan's presentation path also fell back to CPU readback. The CPU-rendered route was modestly faster for this sampled scene; the result does not establish that an OpenGL compute VDP renderer would be faster than Vulkan. Raw performance log: `C:\.android\perf-log-rgrotate-before-gles.csv` (CPU window 18:02:54–18:03:05; Vulkan window 18:04:26–18:04:37). The device was returned to sleep after the run.

## Risks and acceptance

- A GLES backend is a second GPU implementation, not a simple API switch. Shader translation, binding/resource mapping, barriers, context ownership, and driver behavior can all differ.
- The existing Vulkan-to-GBFrame path already uses shared Android hardware buffers, so eliminating readback is not an automatic GLES advantage.
- 4x in each dimension processes up to 16 times as many pixels as 1x. Higher scale is an image-quality choice and may reduce throughput.
- The experiment is successful only if it renders representative games correctly and provides repeatable A/B measurements. Performance parity or improvement is not assumed.
