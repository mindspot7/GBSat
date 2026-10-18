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
};

/// @brief Invoked when the Vulkan renderer finishes a frame. `pixels` points to `width * height` RGBA8 pixels (rows
/// tightly packed) and is only valid during the call.
using CBVulkanFrameReadyCallback = util::OptionalCallback<void(const uint32 *pixels, uint32 width, uint32 height)>;

/// @brief Callbacks specific to the Vulkan VDP renderer.
struct VulkanRendererCallbacks {
    CBVulkanFrameReadyCallback FrameReady;
};

} // namespace ymir::vdp
