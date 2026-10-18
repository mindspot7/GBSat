// Vulkan context for GBSat: instance, Android surface, device, swapchain and a simple frame-presentation path.
// This is the foundation for the Vulkan VDP renderer; it also lets the software renderer present through Vulkan.
#pragma once

#include <android/native_window.h>
#include <cstdint>
#include <string>

namespace vkctx {

struct DeviceInfo {
    bool available = false;
    bool shaderInt64 = false;
    uint32_t apiVersion = 0;
    std::string name;
    std::string error;
};

// Probes the device without creating a surface. Safe to call from any thread.
DeviceInfo Probe();

// Creates (or recreates) the presentation context on a window. Returns false and fills error on failure.
bool Init(ANativeWindow *window, std::string &error);
void Shutdown();
bool IsReady();

// Uploads an RGBA8 image (w*h*4 bytes, row-major) and presents it scaled to the swapchain with the given filter.
// `linear` selects bilinear vs nearest filtering.
bool PresentRGBA(const uint32_t *pixels, uint32_t w, uint32_t h, bool linear);

} // namespace vkctx
