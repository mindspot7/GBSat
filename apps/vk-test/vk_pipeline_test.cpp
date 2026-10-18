#include <cstring>
#include <string>
#include <algorithm>
// CI test: creates a Vulkan compute pipeline (with a layout derived by reflection) for every .spv in a directory.
// Runs on a software Vulkan driver (lavapipe). Usage: vk_pipeline_test <dir-with-spv>
#include <ymir/hw/vdp/renderer/vulkan/spirv_reflect.hpp>

#include <vulkan/vulkan.h>

#include <cstdio>
#include <filesystem>
#include <fstream>
#include <map>
#include <vector>

using namespace ymir::vdp::vulkan;
namespace fs = std::filesystem;

#define CHK(x)                                                                                                         \
    do {                                                                                                               \
        VkResult r_ = (x);                                                                                             \
        if (r_ != VK_SUCCESS) {                                                                                        \
            std::printf("FAIL %s -> %d\n", #x, (int)r_);                                                               \
            return 2;                                                                                                  \
        }                                                                                                              \
    } while (0)

int main(int argc, char **argv) {
    if (argc < 2) return 1;
    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.apiVersion = VK_API_VERSION_1_1;
    VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ici.pApplicationInfo = &app;
    VkInstance inst;
    CHK(vkCreateInstance(&ici, nullptr, &inst));
    uint32_t n = 0;
    vkEnumeratePhysicalDevices(inst, &n, nullptr);
    if (!n) { std::printf("no Vulkan device\n"); return 3; }
    std::vector<VkPhysicalDevice> pds(n);
    vkEnumeratePhysicalDevices(inst, &n, pds.data());
    VkPhysicalDevice pd = pds[0];
    VkPhysicalDeviceFeatures feats{};
    vkGetPhysicalDeviceFeatures(pd, &feats);
    std::printf("shaderInt64=%d\n", (int)feats.shaderInt64);
    VkPhysicalDeviceFeatures enable{};
    enable.shaderInt64 = feats.shaderInt64;
    float prio = 1;
    VkDeviceQueueCreateInfo qci{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    qci.queueCount = 1;
    qci.pQueuePriorities = &prio;
    VkDeviceCreateInfo dci{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    dci.queueCreateInfoCount = 1;
    dci.pQueueCreateInfos = &qci;
    dci.pEnabledFeatures = &enable;
    VkDevice dev;
    CHK(vkCreateDevice(pd, &dci, nullptr, &dev));

    int ok = 0, bad = 0;
    std::vector<fs::path> files;
    for (auto &e : fs::recursive_directory_iterator(argv[1]))
        if (e.path().extension() == ".spv") files.push_back(e.path());
    std::sort(files.begin(), files.end());
    for (auto &f : files) {
        std::ifstream in(f, std::ios::binary);
        std::vector<char> raw((std::istreambuf_iterator<char>(in)), {});
        std::vector<uint32_t> code(raw.size() / 4);
        std::memcpy(code.data(), raw.data(), code.size() * 4);
        SpvReflection rf = ReflectSpirv(code.data(), code.size());
        std::vector<VkDescriptorSetLayoutBinding> bs;
        std::string desc;
        for (auto &b : rf.bindings) {
            VkDescriptorSetLayoutBinding lb{};
            lb.binding = b.binding;
            lb.descriptorType = (VkDescriptorType)b.kind;
            lb.descriptorCount = 1;
            lb.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
            bs.push_back(lb);
            desc += std::to_string(b.binding) + ":" + std::to_string((int)b.kind) + " ";
        }
        VkDescriptorSetLayoutCreateInfo dl{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        dl.bindingCount = (uint32_t)bs.size();
        dl.pBindings = bs.data();
        VkDescriptorSetLayout dsl;
        VkPipelineLayout pl;
        VkShaderModule sm;
        VkPipeline pipe;
        VkPipelineLayoutCreateInfo plci{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        VkShaderModuleCreateInfo smci{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
        smci.codeSize = raw.size();
        smci.pCode = code.data();
        VkComputePipelineCreateInfo cpci{VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
        bool good = rf.valid && vkCreateDescriptorSetLayout(dev, &dl, nullptr, &dsl) == VK_SUCCESS;
        if (good) {
            plci.setLayoutCount = 1;
            plci.pSetLayouts = &dsl;
            good = vkCreatePipelineLayout(dev, &plci, nullptr, &pl) == VK_SUCCESS &&
                   vkCreateShaderModule(dev, &smci, nullptr, &sm) == VK_SUCCESS;
        }
        if (good) {
            cpci.stage = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
            cpci.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
            cpci.stage.module = sm;
            cpci.stage.pName = "CSMain";
            cpci.layout = pl;
            good = vkCreateComputePipelines(dev, VK_NULL_HANDLE, 1, &cpci, nullptr, &pipe) == VK_SUCCESS;
        }
        std::printf("%s %s local=%u,%u,%u bindings=[%s]\n", good ? "OK  " : "FAIL", f.filename().c_str(),
                    rf.localSize[0], rf.localSize[1], rf.localSize[2], desc.c_str());
        good ? ++ok : ++bad;
    }
    std::printf("pipelines ok=%d failed=%d\n", ok, bad);
    return bad ? 4 : 0;
}
