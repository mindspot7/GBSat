#include "vk_context.hpp"

#include <android/log.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <algorithm>
#include <cstring>
#include <vector>

#define LOGV(...) __android_log_print(ANDROID_LOG_INFO, "GBSat-VK", __VA_ARGS__)

namespace vkctx {

namespace {

struct Ctx {
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice phys = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    uint32_t queueFamily = 0;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkFormat format = VK_FORMAT_R8G8B8A8_UNORM;
    VkExtent2D extent{};
    std::vector<VkImage> images;
    VkCommandPool pool = VK_NULL_HANDLE;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    VkSemaphore imageAvail = VK_NULL_HANDLE;
    VkSemaphore renderDone = VK_NULL_HANDLE;
    VkFence fence = VK_NULL_HANDLE;
    // Staging + source image
    VkBuffer staging = VK_NULL_HANDLE;
    VkDeviceMemory stagingMem = VK_NULL_HANDLE;
    void *stagingMap = nullptr;
    VkDeviceSize stagingSize = 0;
    VkImage src = VK_NULL_HANDLE;
    VkDeviceMemory srcMem = VK_NULL_HANDLE;
    uint32_t srcW = 0, srcH = 0;
    bool srcInitialized = false;
    bool ready = false;
} g;

bool FindMemType(uint32_t bits, VkMemoryPropertyFlags want, uint32_t &out) {
    VkPhysicalDeviceMemoryProperties mp;
    vkGetPhysicalDeviceMemoryProperties(g.phys, &mp);
    for (uint32_t i = 0; i < mp.memoryTypeCount; ++i) {
        if ((bits & (1u << i)) && (mp.memoryTypes[i].propertyFlags & want) == want) {
            out = i;
            return true;
        }
    }
    return false;
}

void DestroySrc() {
    if (g.src) vkDestroyImage(g.device, g.src, nullptr);
    if (g.srcMem) vkFreeMemory(g.device, g.srcMem, nullptr);
    g.src = VK_NULL_HANDLE;
    g.srcMem = VK_NULL_HANDLE;
    g.srcW = g.srcH = 0;
    g.srcInitialized = false;
}

void DestroyStaging() {
    if (g.stagingMap) vkUnmapMemory(g.device, g.stagingMem);
    if (g.staging) vkDestroyBuffer(g.device, g.staging, nullptr);
    if (g.stagingMem) vkFreeMemory(g.device, g.stagingMem, nullptr);
    g.stagingMap = nullptr;
    g.staging = VK_NULL_HANDLE;
    g.stagingMem = VK_NULL_HANDLE;
    g.stagingSize = 0;
}

bool EnsureSrc(uint32_t w, uint32_t h) {
    if (g.src && g.srcW == w && g.srcH == h) return true;
    DestroySrc();

    VkImageCreateInfo ici{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
    ici.imageType = VK_IMAGE_TYPE_2D;
    ici.format = VK_FORMAT_R8G8B8A8_UNORM;
    ici.extent = {w, h, 1};
    ici.mipLevels = 1;
    ici.arrayLayers = 1;
    ici.samples = VK_SAMPLE_COUNT_1_BIT;
    ici.tiling = VK_IMAGE_TILING_OPTIMAL;
    ici.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    ici.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    if (vkCreateImage(g.device, &ici, nullptr, &g.src) != VK_SUCCESS) return false;
    VkMemoryRequirements mr;
    vkGetImageMemoryRequirements(g.device, g.src, &mr);
    uint32_t mt;
    if (!FindMemType(mr.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, mt)) return false;
    VkMemoryAllocateInfo mai{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    mai.allocationSize = mr.size;
    mai.memoryTypeIndex = mt;
    if (vkAllocateMemory(g.device, &mai, nullptr, &g.srcMem) != VK_SUCCESS) return false;
    vkBindImageMemory(g.device, g.src, g.srcMem, 0);
    g.srcW = w;
    g.srcH = h;
    g.srcInitialized = false;

    VkDeviceSize need = VkDeviceSize(w) * h * 4;
    if (need > g.stagingSize) {
        DestroyStaging();
        VkBufferCreateInfo bci{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        bci.size = need;
        bci.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
        bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vkCreateBuffer(g.device, &bci, nullptr, &g.staging) != VK_SUCCESS) return false;
        vkGetBufferMemoryRequirements(g.device, g.staging, &mr);
        if (!FindMemType(mr.memoryTypeBits,
                         VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT, mt))
            return false;
        mai.allocationSize = mr.size;
        mai.memoryTypeIndex = mt;
        if (vkAllocateMemory(g.device, &mai, nullptr, &g.stagingMem) != VK_SUCCESS) return false;
        vkBindBufferMemory(g.device, g.staging, g.stagingMem, 0);
        if (vkMapMemory(g.device, g.stagingMem, 0, VK_WHOLE_SIZE, 0, &g.stagingMap) != VK_SUCCESS) return false;
        g.stagingSize = need;
    }
    return true;
}

void Barrier(VkCommandBuffer cb, VkImage img, VkImageLayout from, VkImageLayout to, VkAccessFlags srcA,
             VkAccessFlags dstA, VkPipelineStageFlags srcS, VkPipelineStageFlags dstS) {
    VkImageMemoryBarrier b{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    b.oldLayout = from;
    b.newLayout = to;
    b.srcAccessMask = srcA;
    b.dstAccessMask = dstA;
    b.srcQueueFamilyIndex = b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    b.image = img;
    b.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    vkCmdPipelineBarrier(cb, srcS, dstS, 0, 0, nullptr, 0, nullptr, 1, &b);
}

bool CreateInstance(std::string &err, bool withSurface) {
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "GBSat";
    app.apiVersion = VK_API_VERSION_1_1;
    std::vector<const char *> exts;
    if (withSurface) {
        exts.push_back("VK_KHR_surface");
        exts.push_back("VK_KHR_android_surface");
    }
    VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ici.pApplicationInfo = &app;
    ici.enabledExtensionCount = uint32_t(exts.size());
    ici.ppEnabledExtensionNames = exts.data();
    VkResult r = vkCreateInstance(&ici, nullptr, &g.instance);
    if (r != VK_SUCCESS) {
        err = "vkCreateInstance falhou (" + std::to_string(int(r)) + ")";
        return false;
    }
    return true;
}

bool PickDevice(std::string &err, bool needPresent) {
    uint32_t n = 0;
    vkEnumeratePhysicalDevices(g.instance, &n, nullptr);
    if (!n) {
        err = "Nenhum dispositivo Vulkan";
        return false;
    }
    std::vector<VkPhysicalDevice> devs(n);
    vkEnumeratePhysicalDevices(g.instance, &n, devs.data());
    g.phys = devs[0];
    uint32_t qn = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(g.phys, &qn, nullptr);
    std::vector<VkQueueFamilyProperties> qs(qn);
    vkGetPhysicalDeviceQueueFamilyProperties(g.phys, &qn, qs.data());
    for (uint32_t i = 0; i < qn; ++i) {
        if (!(qs[i].queueFlags & (VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT))) continue;
        VkBool32 present = VK_TRUE;
        if (needPresent) vkGetPhysicalDeviceSurfaceSupportKHR(g.phys, i, g.surface, &present);
        if (present) {
            g.queueFamily = i;
            return true;
        }
    }
    err = "Nenhuma fila Vulkan adequada";
    return false;
}

bool CreateSwapchain(std::string &err) {
    VkSurfaceCapabilitiesKHR caps;
    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(g.phys, g.surface, &caps);
    uint32_t fn = 0;
    vkGetPhysicalDeviceSurfaceFormatsKHR(g.phys, g.surface, &fn, nullptr);
    std::vector<VkSurfaceFormatKHR> fmts(fn);
    vkGetPhysicalDeviceSurfaceFormatsKHR(g.phys, g.surface, &fn, fmts.data());
    VkSurfaceFormatKHR chosen = fmts.empty() ? VkSurfaceFormatKHR{VK_FORMAT_R8G8B8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR} : fmts[0];
    for (auto &f : fmts) {
        if (f.format == VK_FORMAT_R8G8B8A8_UNORM || f.format == VK_FORMAT_B8G8R8A8_UNORM) {
            chosen = f;
            break;
        }
    }
    g.format = chosen.format;
    g.extent = caps.currentExtent;
    if (g.extent.width == 0xFFFFFFFFu) g.extent = {640, 480};
    uint32_t count = std::max(caps.minImageCount, 2u);
    if (caps.maxImageCount && count > caps.maxImageCount) count = caps.maxImageCount;
    VkSwapchainCreateInfoKHR sci{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
    sci.surface = g.surface;
    sci.minImageCount = count;
    sci.imageFormat = chosen.format;
    sci.imageColorSpace = chosen.colorSpace;
    sci.imageExtent = g.extent;
    sci.imageArrayLayers = 1;
    sci.imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    sci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    sci.preTransform = caps.currentTransform;
    sci.compositeAlpha = (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                             ? VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
                             : VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
    sci.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    sci.clipped = VK_TRUE;
    if (vkCreateSwapchainKHR(g.device, &sci, nullptr, &g.swapchain) != VK_SUCCESS) {
        err = "vkCreateSwapchainKHR falhou";
        return false;
    }
    uint32_t in = 0;
    vkGetSwapchainImagesKHR(g.device, g.swapchain, &in, nullptr);
    g.images.resize(in);
    vkGetSwapchainImagesKHR(g.device, g.swapchain, &in, g.images.data());
    return true;
}

} // namespace

DeviceInfo Probe() {
    DeviceInfo info;
    Ctx saved = g;
    g = Ctx{};
    std::string err;
    if (!CreateInstance(err, false) || !PickDevice(err, false)) {
        info.error = err;
    } else {
        VkPhysicalDeviceProperties props;
        vkGetPhysicalDeviceProperties(g.phys, &props);
        VkPhysicalDeviceFeatures feats;
        vkGetPhysicalDeviceFeatures(g.phys, &feats);
        info.available = true;
        info.shaderInt64 = feats.shaderInt64 != 0;
        info.apiVersion = props.apiVersion;
        info.name = props.deviceName;
    }
    if (g.instance) vkDestroyInstance(g.instance, nullptr);
    g = saved;
    return info;
}

bool Init(ANativeWindow *window, std::string &error) {
    Shutdown();
    if (!CreateInstance(error, true)) return false;

    VkAndroidSurfaceCreateInfoKHR sci{VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR};
    sci.window = window;
    if (vkCreateAndroidSurfaceKHR(g.instance, &sci, nullptr, &g.surface) != VK_SUCCESS) {
        error = "vkCreateAndroidSurfaceKHR falhou";
        Shutdown();
        return false;
    }
    if (!PickDevice(error, true)) {
        Shutdown();
        return false;
    }

    float prio = 1.0f;
    VkDeviceQueueCreateInfo qci{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    qci.queueFamilyIndex = g.queueFamily;
    qci.queueCount = 1;
    qci.pQueuePriorities = &prio;
    const char *devExts[] = {"VK_KHR_swapchain"};
    VkDeviceCreateInfo dci{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    dci.queueCreateInfoCount = 1;
    dci.pQueueCreateInfos = &qci;
    dci.enabledExtensionCount = 1;
    dci.ppEnabledExtensionNames = devExts;
    if (vkCreateDevice(g.phys, &dci, nullptr, &g.device) != VK_SUCCESS) {
        error = "vkCreateDevice falhou";
        Shutdown();
        return false;
    }
    vkGetDeviceQueue(g.device, g.queueFamily, 0, &g.queue);

    if (!CreateSwapchain(error)) {
        Shutdown();
        return false;
    }

    VkCommandPoolCreateInfo pci{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    pci.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    pci.queueFamilyIndex = g.queueFamily;
    vkCreateCommandPool(g.device, &pci, nullptr, &g.pool);
    VkCommandBufferAllocateInfo cai{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    cai.commandPool = g.pool;
    cai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cai.commandBufferCount = 1;
    vkAllocateCommandBuffers(g.device, &cai, &g.cmd);
    VkSemaphoreCreateInfo sem{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    vkCreateSemaphore(g.device, &sem, nullptr, &g.imageAvail);
    vkCreateSemaphore(g.device, &sem, nullptr, &g.renderDone);
    VkFenceCreateInfo fci{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    fci.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    vkCreateFence(g.device, &fci, nullptr, &g.fence);

    g.ready = true;
    LOGV("Vulkan pronto: %ux%u", g.extent.width, g.extent.height);
    return true;
}

void Shutdown() {
    if (g.device) {
        vkDeviceWaitIdle(g.device);
        DestroySrc();
        DestroyStaging();
        if (g.fence) vkDestroyFence(g.device, g.fence, nullptr);
        if (g.imageAvail) vkDestroySemaphore(g.device, g.imageAvail, nullptr);
        if (g.renderDone) vkDestroySemaphore(g.device, g.renderDone, nullptr);
        if (g.pool) vkDestroyCommandPool(g.device, g.pool, nullptr);
        if (g.swapchain) vkDestroySwapchainKHR(g.device, g.swapchain, nullptr);
        vkDestroyDevice(g.device, nullptr);
    }
    if (g.instance) {
        if (g.surface) vkDestroySurfaceKHR(g.instance, g.surface, nullptr);
        vkDestroyInstance(g.instance, nullptr);
    }
    g = Ctx{};
}

bool IsReady() { return g.ready; }

bool PresentRGBA(const uint32_t *pixels, uint32_t w, uint32_t h, bool linear) {
    if (!g.ready || !w || !h) return false;
    if (!EnsureSrc(w, h)) return false;

    vkWaitForFences(g.device, 1, &g.fence, VK_TRUE, UINT64_MAX);

    uint32_t idx = 0;
    VkResult r = vkAcquireNextImageKHR(g.device, g.swapchain, UINT64_MAX, g.imageAvail, VK_NULL_HANDLE, &idx);
    if (r == VK_ERROR_OUT_OF_DATE_KHR || r == VK_SUBOPTIMAL_KHR) return false;
    if (r != VK_SUCCESS) return false;
    vkResetFences(g.device, 1, &g.fence);

    std::memcpy(g.stagingMap, pixels, size_t(w) * h * 4);

    vkResetCommandBuffer(g.cmd, 0);
    VkCommandBufferBeginInfo bi{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    bi.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(g.cmd, &bi);

    Barrier(g.cmd, g.src, g.srcInitialized ? VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 0, VK_ACCESS_TRANSFER_WRITE_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
    VkBufferImageCopy bic{};
    bic.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    bic.imageExtent = {w, h, 1};
    vkCmdCopyBufferToImage(g.cmd, g.staging, g.src, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &bic);
    Barrier(g.cmd, g.src, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT);
    g.srcInitialized = true;

    VkImage dst = g.images[idx];
    Barrier(g.cmd, dst, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 0,
            VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

    // Clear to black, then blit the frame letterboxed (keeping 4:3-ish aspect of the source).
    VkClearColorValue black{};
    VkImageSubresourceRange range{VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    vkCmdClearColorImage(g.cmd, dst, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &black, 1, &range);

    float sx = float(g.extent.width) / float(w);
    float sy = float(g.extent.height) / float(h);
    float s = std::min(sx, sy);
    int32_t dw = int32_t(float(w) * s), dh = int32_t(float(h) * s);
    int32_t ox = (int32_t(g.extent.width) - dw) / 2, oy = (int32_t(g.extent.height) - dh) / 2;
    VkImageBlit blit{};
    blit.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    blit.srcOffsets[1] = {int32_t(w), int32_t(h), 1};
    blit.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    blit.dstOffsets[0] = {ox, oy, 0};
    blit.dstOffsets[1] = {ox + dw, oy + dh, 1};
    vkCmdBlitImage(g.cmd, g.src, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, dst, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1,
                   &blit, linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);

    Barrier(g.cmd, dst, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            VK_ACCESS_TRANSFER_WRITE_BIT, 0, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);
    vkEndCommandBuffer(g.cmd);

    VkPipelineStageFlags wait = VK_PIPELINE_STAGE_TRANSFER_BIT;
    VkSubmitInfo si{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    si.waitSemaphoreCount = 1;
    si.pWaitSemaphores = &g.imageAvail;
    si.pWaitDstStageMask = &wait;
    si.commandBufferCount = 1;
    si.pCommandBuffers = &g.cmd;
    si.signalSemaphoreCount = 1;
    si.pSignalSemaphores = &g.renderDone;
    if (vkQueueSubmit(g.queue, 1, &si, g.fence) != VK_SUCCESS) return false;

    VkPresentInfoKHR pi{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
    pi.waitSemaphoreCount = 1;
    pi.pWaitSemaphores = &g.renderDone;
    pi.swapchainCount = 1;
    pi.pSwapchains = &g.swapchain;
    pi.pImageIndices = &idx;
    vkQueuePresentKHR(g.queue, &pi);
    return true;
}

} // namespace vkctx
