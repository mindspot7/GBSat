#pragma once

#include <array>

/**
@file
@brief VDP1 and VDP2 renderer implementation using Vulkan compute shaders.

Port of the Direct3D 12 renderer. Requires Vulkan 1.1 with the `shaderInt64` feature.
*/

#include <ymir/hw/vdp/renderer/vdp_renderer_hw_base.hpp>

#include "vdp_renderer_hw_vulkan_callbacks.hpp"

#include <ymir/util/result.hpp>

#include <memory>
#include <string>

namespace ymir::vdp {

/// @brief VDP renderer implementation using Vulkan.
class VulkanVDPRenderer : public HardwareVDPRendererBase {
    VulkanVDPRenderer(VDPState &state, const config::VDP2DebugRender &vdp2DebugRenderOptions,
                      const config::VDP2AccessPatternsConfig &vdp2AccessPatternsConfig);

    util::VoidResult<> Initialize(const VulkanRendererContext &context);

public:
    ~VulkanVDPRenderer();

    static util::ObjectResult<VulkanVDPRenderer>
    Create(VDPState &state, const config::VDP2DebugRender &vdp2DebugRenderOptions,
           const config::VDP2AccessPatternsConfig &vdp2AccessPatternsConfig, const VulkanRendererContext &context);

    // -------------------------------------------------------------------------
    // Configuration

    VulkanRendererCallbacks HwCallbacks;

protected:
    void UpdateEnhancements() override;

public:
    // -------------------------------------------------------------------------
    // Basics

    bool IsValid() const override;

    void Reset(bool hard) override;

    // -------------------------------------------------------------------------
    // Save states

    void PreSaveStateSync() override;
    void PostLoadStateSync() override;

    void SaveState(savestate::VDPSaveState::VDPRendererSaveState &state) override;
    bool ValidateState(const savestate::VDPSaveState::VDPRendererSaveState &state) const override;
    void LoadState(const savestate::VDPSaveState::VDPRendererSaveState &state) override;

    // -------------------------------------------------------------------------
    // VDP1 memory and register writes

    void VDP1WriteVRAM(uint32 address, uint8 value) override;
    void VDP1WriteVRAM(uint32 address, uint16 value) override;
    void VDP1SyncFB() override;
    void VDP1DebugSyncFB() override;
    void VDP1WriteFB(uint32 address, uint8 value) override;
    void VDP1WriteFB(uint32 address, uint16 value) override;
    void VDP1WriteReg(uint32 address, uint16 value) override;

    // -------------------------------------------------------------------------
    // VDP2 memory and register writes

    void VDP2WriteVRAM(uint32 address, uint8 value) override;
    void VDP2WriteVRAM(uint32 address, uint16 value) override;
    void VDP2WriteCRAM(uint32 address, uint8 value) override;
    void VDP2WriteCRAM(uint32 address, uint16 value) override;
    void VDP2WriteReg(uint32 address, uint16 value) override;

    // -------------------------------------------------------------------------
    // Debugger

    void UpdateEnabledLayers() override;

    // -------------------------------------------------------------------------
    // Utilities

    void DumpExtraVDP1Framebuffers(std::ostream &out) const override;

    // -------------------------------------------------------------------------
    // Diagnostics

    /// @brief Describes the GPU and the features in use.
    std::string DebugDeviceInfo() const;

    /// @brief Reads back the intermediate layer data of the most recently rendered frame at the given output pixel.
    std::string DebugDescribePixel(uint32 x, uint32 y);

    // -------------------------------------------------------------------------
    // Rendering process

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

    /// @brief Internal resolution scale of the frame passed to the frame ready callback (call from the callback).
    uint32 DeliveredFrameScale() const;

    /// @brief VDP2 layer render batches of the last frame and what triggered them (diagnostics).
    struct FrameStats {
        uint32 batches = 0, vram = 0, cram = 0, layer = 0, compose = 0;
        // Changed layer parameters: NBG0-3 x (base, scroll, pages, line/cell scroll), RBGs, windows, line/back/SFC
        std::array<uint32, 19> changed{};
        uint32 syncs = 0;    // mid-frame submit-and-wait (e.g. CPU reads of the VDP1 framebuffer)
        uint32 waitUs = 0;   // time spent waiting for the GPU
        uint32 vdp1Cmds = 0; // VDP1 commands executed
    };
    FrameStats LastFrameStats() const;

private:
    struct Impl;
    std::unique_ptr<Impl> m_impl;
};

} // namespace ymir::vdp
