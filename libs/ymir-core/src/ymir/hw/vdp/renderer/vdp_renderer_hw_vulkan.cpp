#include <ymir/hw/vdp/renderer/vdp_renderer_hw_vulkan.hpp>

#include <ymir/util/dev_log.hpp>

#include <vulkan/vulkan.h>

#include <atomic>
#include <vector>

namespace ymir::vdp {

// ---------------------------------------------------------------------------------------------------------------------
// Vulkan port of the Direct3D 12 renderer (vdp_renderer_hw_d3d12.cpp).
//
// Porting status (see docs in the Android port):
//   [x] renderer type plumbing, context handoff, frame callback
//   [ ] Vulkan helper layer (buffers, barriers, descriptors, pipelines, upload ring)
//   [ ] VDP1 passes (erase, FBRAM write, polygon draw, output merger)
//   [ ] VDP2 passes (BGs, sprites, compose)
//   [ ] enhancements (resolution scale, deinterlace, transparent meshes)
//
// Until the passes are implemented, IsValid() returns false so the frontend keeps using the software renderer.
// ---------------------------------------------------------------------------------------------------------------------

struct VulkanVDPRenderer::Impl {
    Impl(const VulkanRendererCallbacks &hwCallbacks, VDPState &state,
         const config::VDP2AccessPatternsConfig &vdp2AccessPatternsConfig,
         const config::VDP2DebugRender &vdp2DebugRenderOptions, const config::Enhancements &enhancements)
        : vdpState(state)
        , enhancements(enhancements)
        , hwCallbacks(hwCallbacks)
        , vdp2AccessPatterns(vdp2AccessPatternsConfig)
        , vdp2DebugRender(vdp2DebugRenderOptions) {}

    VDPState &vdpState;
    const config::Enhancements &enhancements;
    const VulkanRendererCallbacks &hwCallbacks;
    const config::VDP2AccessPatternsConfig &vdp2AccessPatterns;
    const config::VDP2DebugRender &vdp2DebugRender;

    VulkanRendererContext ctx{};
    bool implemented = false;

    util::VoidResult<> Initialize(const VulkanRendererContext &context) {
        ctx = context;
        if (ctx.device == nullptr || ctx.queue == nullptr) {
            return util::ErrorMessage{"Incomplete Vulkan context"};
        }
        return {};
    }

    void Shutdown() {}
};

// ---------------------------------------------------------------------------------------------------------------------

VulkanVDPRenderer::VulkanVDPRenderer(VDPState &state, const config::VDP2DebugRender &vdp2DebugRenderOptions,
                                     const config::VDP2AccessPatternsConfig &vdp2AccessPatternsConfig)
    : HardwareVDPRendererBase(VDPRendererType::Vulkan)
    , m_impl(std::make_unique<Impl>(HwCallbacks, state, vdp2AccessPatternsConfig, vdp2DebugRenderOptions,
                                    m_enhancements)) {}

VulkanVDPRenderer::~VulkanVDPRenderer() {
    m_impl->Shutdown();
}

util::VoidResult<> VulkanVDPRenderer::Initialize(const VulkanRendererContext &context) {
    return m_impl->Initialize(context);
}

util::ObjectResult<VulkanVDPRenderer>
VulkanVDPRenderer::Create(VDPState &state, const config::VDP2DebugRender &vdp2DebugRenderOptions,
                          const config::VDP2AccessPatternsConfig &vdp2AccessPatternsConfig,
                          const VulkanRendererContext &context) {
    if (context.device == nullptr) {
        return util::ErrorMessage{"No Vulkan device provided"};
    }
    std::unique_ptr<VulkanVDPRenderer> renderer{new VulkanVDPRenderer(state, vdp2DebugRenderOptions, vdp2AccessPatternsConfig)};
    util::VoidResult<> result = renderer->Initialize(context);
    if (!result) {
        return result.Error();
    }
    return renderer;
}

void VulkanVDPRenderer::UpdateEnhancements() {}

bool VulkanVDPRenderer::IsValid() const {
    return m_impl->implemented;
}

void VulkanVDPRenderer::Reset(bool hard) {}

void VulkanVDPRenderer::PreSaveStateSync() {}
void VulkanVDPRenderer::PostLoadStateSync() {}
void VulkanVDPRenderer::SaveState(savestate::VDPSaveState::VDPRendererSaveState &state) {}
bool VulkanVDPRenderer::ValidateState(const savestate::VDPSaveState::VDPRendererSaveState &state) const {
    return true;
}
void VulkanVDPRenderer::LoadState(const savestate::VDPSaveState::VDPRendererSaveState &state) {}

void VulkanVDPRenderer::VDP1WriteVRAM(uint32 address, uint8 value) {}
void VulkanVDPRenderer::VDP1WriteVRAM(uint32 address, uint16 value) {}
void VulkanVDPRenderer::VDP1SyncFB() {}
void VulkanVDPRenderer::VDP1DebugSyncFB() {}
void VulkanVDPRenderer::VDP1WriteFB(uint32 address, uint8 value) {}
void VulkanVDPRenderer::VDP1WriteFB(uint32 address, uint16 value) {}
void VulkanVDPRenderer::VDP1WriteReg(uint32 address, uint16 value) {}

void VulkanVDPRenderer::VDP2WriteVRAM(uint32 address, uint8 value) {}
void VulkanVDPRenderer::VDP2WriteVRAM(uint32 address, uint16 value) {}
void VulkanVDPRenderer::VDP2WriteCRAM(uint32 address, uint8 value) {}
void VulkanVDPRenderer::VDP2WriteCRAM(uint32 address, uint16 value) {}
void VulkanVDPRenderer::VDP2WriteReg(uint32 address, uint16 value) {}

void VulkanVDPRenderer::UpdateEnabledLayers() {}

void VulkanVDPRenderer::DumpExtraVDP1Framebuffers(std::ostream &out) const {}

void VulkanVDPRenderer::VDP1EraseFramebuffer(uint64 cycles) {}
void VulkanVDPRenderer::VDP1SwapFramebuffer() {
    Callbacks.VDP1FramebufferSwap();
}
void VulkanVDPRenderer::VDP1BeginFrame() {}
void VulkanVDPRenderer::VDP1ExecuteCommand(uint32 cmdAddress, VDP1Command::Control control) {}
void VulkanVDPRenderer::VDP1EndFrame() {
    Callbacks.VDP1DrawFinished();
}

void VulkanVDPRenderer::VDP2SetResolution(uint32 h, uint32 v, bool exclusive) {}
void VulkanVDPRenderer::VDP2SetField(bool odd) {}
void VulkanVDPRenderer::VDP2LatchTVMD() {}
void VulkanVDPRenderer::VDP2BeginFrame() {}
void VulkanVDPRenderer::VDP2RenderLine(uint32 y) {}
void VulkanVDPRenderer::VDP2EndFrame() {
    Callbacks.VDP2DrawFinished();
}

} // namespace ymir::vdp
