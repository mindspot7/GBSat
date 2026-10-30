#pragma once

#include <ymir/hw/vdp/renderer/vdp_renderer_hw_base.hpp>
#include <ymir/hw/vdp/renderer/vdp_renderer_hw_opengles_callbacks.hpp>
#include <ymir/util/result.hpp>

#include <memory>
#include <string>

namespace ymir::vdp {

/// Android OpenGL ES compute VDP renderer. It uses the frontend's shared EGL context and AHardwareBuffer presenter.
class OpenGLESVDPRenderer : public HardwareVDPRendererBase {
    OpenGLESVDPRenderer(VDPState &state, const config::VDP2DebugRender &vdp2DebugRenderOptions,
                        const config::VDP2AccessPatternsConfig &vdp2AccessPatternsConfig);

    util::VoidResult<> Initialize(const OpenGLESRendererContext &context);

public:
    ~OpenGLESVDPRenderer();

    static util::ObjectResult<OpenGLESVDPRenderer>
    Create(VDPState &state, const config::VDP2DebugRender &vdp2DebugRenderOptions,
           const config::VDP2AccessPatternsConfig &vdp2AccessPatternsConfig, const OpenGLESRendererContext &context);

    OpenGLESRendererCallbacks HwCallbacks;

protected:
    void UpdateEnhancements() override;

public:
    bool IsValid() const override;
    void Reset(bool hard) override;
    void PreSaveStateSync() override;
    void PostLoadStateSync() override;
    void SaveState(savestate::VDPSaveState::VDPRendererSaveState &state) override;
    bool ValidateState(const savestate::VDPSaveState::VDPRendererSaveState &state) const override;
    void LoadState(const savestate::VDPSaveState::VDPRendererSaveState &state) override;

    void VDP1WriteVRAM(uint32 address, uint8 value) override;
    void VDP1WriteVRAM(uint32 address, uint16 value) override;
    void VDP1SyncFB() override;
    void VDP1DebugSyncFB() override;
    void VDP1WriteFB(uint32 address, uint8 value) override;
    void VDP1WriteFB(uint32 address, uint16 value) override;
    void VDP1WriteReg(uint32 address, uint16 value) override;

    void VDP2WriteVRAM(uint32 address, uint8 value) override;
    void VDP2WriteVRAM(uint32 address, uint16 value) override;
    void VDP2WriteCRAM(uint32 address, uint8 value) override;
    void VDP2WriteCRAM(uint32 address, uint16 value) override;
    void VDP2WriteReg(uint32 address, uint16 value) override;

    void UpdateEnabledLayers() override;
    void DumpExtraVDP1Framebuffers(std::ostream &out) const override;
    std::string DebugDeviceInfo() const;
    std::string DebugDescribePixel(uint32 x, uint32 y);

    void VDP1EraseFramebuffer(uint64 cycles) override;
    void VDP1SwapFramebuffer() override;
    void VDP1BeginFrame() override;
    void VDP1ExecuteCommand(uint32 cmdAddress, VDP1Command::Control control) override;
    void VDP1EndFrame() override;
    void VDP2SetResolution(uint32 h, uint32 v, bool exclusive) override;
    void VDP2SetField(bool odd) override;
    void VDP2LatchTVMD() override;
    void VDP2BeginFrame() override;
    void VDP2RenderLine(uint32 y) override;
    void VDP2EndFrame() override;
    void VDP2SetSkipFrame(bool skip) override;

    uint32 DeliveredFrameScale() const;

private:
    struct Impl;
    std::unique_ptr<Impl> m_impl;
};

} // namespace ymir::vdp
