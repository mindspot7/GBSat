#pragma once

#include <ymir/util/callback.hpp>

#include <ymir/core/types.hpp>

namespace ymir::vdp {

/// @brief Vulkan objects the Vulkan VDP renderer uses. Created and owned by the frontend; they must outlive the
/// renderer. Handles are passed as opaque pointers so this header does not depend on the Vulkan headers.
struct VulkanRendererContext {
    void *instance = nullptr;       ///< VkInstance
    void *physicalDevice = nullptr; ///< VkPhysicalDevice
    void *device = nullptr;         ///< VkDevice (must have shaderInt64 enabled)
    void *queue = nullptr;          ///< VkQueue with compute support
    uint32 queueFamily = 0;         ///< queue family index of `queue`
    /// Optional: receives a short description of each initialization step (for crash diagnostics).
    void (*progress)(const char *stage) = nullptr;
    /// Internal resolution scale factor (1 = native, up to 4). VDP1 graphics are rasterized at this scale and the
    /// output frame is delivered at the scaled size. Only applied in non-interlaced, normal horizontal resolution
    /// modes with 16-bit VDP1 framebuffers; other modes render at native resolution.
    uint32 resolutionScale = 1;
};

/// @brief Invoked when the Vulkan renderer finishes a frame. `pixels` points to `width * height` RGBA8 pixels (rows
/// tightly packed) and is only valid during the call.
using CBVulkanFrameReadyCallback = util::OptionalCallback<void(const uint32 *pixels, uint32 width, uint32 height)>;

/// @brief Optional frontend-owned AHardwareBuffer output. The acquire callback reserves a buffer slot and returns its
/// opaque AHardwareBuffer pointer plus a token; the renderer copies the completed frame into it using Vulkan. The ready
/// callback runs after the Vulkan fence has completed, so the frontend may safely inspect or import the buffer.
using CBVulkanFrameTargetAcquireCallback =
    util::OptionalCallback<void *(uint32 width, uint32 height, uint64 *token)>;
using CBVulkanFrameTargetReadyCallback =
    util::OptionalCallback<void(void *buffer, uint64 token, uint32 width, uint32 height, uint32 scale)>;
using CBVulkanFrameTargetCancelCallback = util::OptionalCallback<void(uint64 token)>;

/// @brief Callbacks specific to the Vulkan VDP renderer.
struct VulkanRendererCallbacks {
    CBVulkanFrameReadyCallback FrameReady;
    CBVulkanFrameTargetAcquireCallback FrameTargetAcquire;
    CBVulkanFrameTargetReadyCallback FrameTargetReady;
    CBVulkanFrameTargetCancelCallback FrameTargetCancel;
};

} // namespace ymir::vdp
