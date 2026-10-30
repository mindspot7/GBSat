# Experimental GLES VDP renderer A/B

Status: investigation and implementation branch `codex/gles-vdp-ab`; no renderer or UI changes have been made yet.

## Goal

Add an opt-in Ymir hardware VDP renderer using OpenGL ES compute, with internal scales 1x through 4x, and compare it to the existing Vulkan hardware renderer on the RG Rotate. Keep the software renderer and Vulkan as-is and leave the current default unchanged until repeated game tests justify a change.

## Current architecture

- `SoftwareVDPRenderer` composes the Saturn frame on the CPU at the emulated output resolution. The Android OpenGL ES presenter displays that completed frame; scaling its texture would not increase Saturn rendering detail.
- `VulkanVDPRenderer` runs Ymir VDP1/VDP2 compute shaders on the GPU. Its HLSL sources are compiled to SPIR-V, and its hardware renderer supports internal scaling.
- The shared GBFrame presenter already displays Vulkan output through `AHardwareBuffer`/EGLImage, with a CPU readback fallback. An A/B backend must preserve GPU-to-GPU delivery so readback does not distort the result.
- The current GBFrame EGL context requests OpenGL ES 3. A GLES compute backend needs ES 3.1; the context and context-sharing/lifetime contract need to be designed before enabling the renderer.

## Implementation route

1. Validate RG Rotate GLES version, compute support, relevant limits, and EGL/AHardwareBuffer interop while the display is awake; restore sleep after device work.
2. Prototype conversion of one existing VDP HLSL compute shader to GLSL ES 3.1 and validate its bindings, buffer layout, atomics, and output. Evaluate the existing DXC/SPIR-V build and SPIRV-Cross route before choosing generated or runtime-translated GLSL.
3. Add an Android-only Ymir renderer type and resource/synchronization layer. Reuse the API-independent VDP command and parameter logic where practical; do not change the existing software or Vulkan paths.
4. Deliver completed GLES frames to the existing presenter without CPU readback. Preserve instrumentation and frame scale metadata.
5. Expose the experimental backend and 1x–4x scale in the shared GBFrame settings/quick menu only after the renderer initializes and produces correct frames; keep Vulkan and software selections available.
6. Build and validate locally, then run paired tests on the RG Rotate from the same savestate and scene at 1x, 2x, 3x, and 4x. Compare emulation FPS, displayed FPS, frame time, GPU time, and output correctness. Use NiGHTS, Daytona, Virtua Fighter 2, Guardian Heroes, and Burning Rangers; record each run and leave defaults untouched.

## Risks and acceptance

- A GLES backend is a second GPU implementation, not a simple API switch. Shader translation, binding/resource mapping, barriers, context ownership, and driver behavior can all differ.
- The existing Vulkan-to-GBFrame path already uses shared Android hardware buffers, so eliminating readback is not an automatic GLES advantage.
- 4x in each dimension processes up to 16 times as many pixels as 1x. Higher scale is an image-quality choice and may reduce throughput.
- The experiment is successful only if it renders representative games correctly and provides repeatable A/B measurements. Performance parity or improvement is not assumed.
