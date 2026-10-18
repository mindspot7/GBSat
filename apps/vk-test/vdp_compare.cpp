// Renders a synthetic scene (VDP2 bitmap background + a set of VDP1 polygons, lines and sprites) with the software
// renderer and with the Vulkan renderer, then compares the resulting frames. No BIOS needed: the SH-2 spins in a loop
// while the VDPs are programmed through side-effect-free bus writes.
#include <ymir/ymir.hpp>

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <cstring>
#include <string>
#include <vector>

using namespace ymir;

struct Frame {
    std::vector<uint32> px;
    std::vector<uint32> prev; // previous frame (the Vulkan renderer delivers frames one frame late)
    uint32 w = 0, h = 0;
    int count = 0;
};

static void Poke16(Saturn &s, uint32 addr, uint16 v) {
    s.mainBus.Poke<uint16>(addr, v);
}

constexpr uint32 kVDP1VRAM = 0x25C00000, kVDP1Regs = 0x25D00000;
constexpr uint32 kVDP2VRAM = 0x25E00000, kVDP2CRAM = 0x25F00000, kVDP2Regs = 0x25F80000;

static void Cmd(Saturn &s, int index, std::initializer_list<std::pair<int, uint16>> fields) {
    const uint32 base = kVDP1VRAM + index * 0x20;
    for (int off = 0; off < 0x20; off += 2) Poke16(s, base + off, 0);
    for (auto [off, v] : fields) Poke16(s, base + off, v);
}

static void SetupScene(Saturn &s, int scene) {
    // ---- VDP2 ----
    // TVMD: display on, 320 or 352 wide (scene 1), 224 lines, double-density interlace (scene 4)
    Poke16(s, kVDP2Regs + 0x000, 0x8000 | (scene == 1 ? 0x0001 : 0) | (scene == 4 ? 0x00C0 : 0));
    Poke16(s, kVDP2Regs + 0x00E, 0x0000);                             // RAMCTL: CRAM mode 0
    for (uint32 r = 0x010; r <= 0x01E; r += 2) Poke16(s, kVDP2Regs + r, 0x4444); // cycle patterns: NBG0 char data
    Poke16(s, kVDP2Regs + 0x020, 0x0001);  // BGON: NBG0
    Poke16(s, kVDP2Regs + 0x028, 0x0012);  // CHCTLA: NBG0 bitmap 512x256, 256 colors
    Poke16(s, kVDP2Regs + 0x02C, 0x0000);  // BMPNA
    Poke16(s, kVDP2Regs + 0x03C, 0x0000);  // MPOFN: bitmap at 0
    Poke16(s, kVDP2Regs + 0x070, 0x0010);  // SCXIN0 = 16
    Poke16(s, kVDP2Regs + 0x074, 0x0008);  // SCYIN0 = 8
    Poke16(s, kVDP2Regs + 0x078, 0x0001);  // ZMXIN0 = 1.0
    Poke16(s, kVDP2Regs + 0x07C, 0x0001);  // ZMYIN0 = 1.0
    Poke16(s, kVDP2Regs + 0x0AC, 0x0003);  // BKTAU
    Poke16(s, kVDP2Regs + 0x0AE, 0xFFFF);  // BKTAL -> back screen color at 0x7FFFE
    Poke16(s, kVDP2Regs + 0x0E0, 0x0020);  // SPCTL: mixed palette/RGB sprites, type 0
    Poke16(s, kVDP2Regs + 0x0F0, 0x0005);  // PRISA: sprite reg 0 priority 5
    Poke16(s, kVDP2Regs + 0x0F8, 0x0003);  // PRINA: NBG0 priority 3
    if (scene == 2) {
        Poke16(s, kVDP2Regs + 0x0EC, 0x0001); // CCCTL: color calculation on NBG0
        Poke16(s, kVDP2Regs + 0x108, 0x000F); // CCRNA: NBG0 ratio
        Poke16(s, kVDP2Regs + 0x110, 0x0001); // CLOFEN: color offset on NBG0
        Poke16(s, kVDP2Regs + 0x114, 0x0040); // COAR
        Poke16(s, kVDP2Regs + 0x116, 0x01E0); // COAG (negative)
    }
    if (scene == 3) {
        // NBG1 cell-based tilemap (16 colors, 1-word pattern names) on top of the bitmap NBG0
        Poke16(s, kVDP2Regs + 0x010, 0x415F); // CYCA0L: NBG0 CG, NBG1 PN, NBG1 CG
        Poke16(s, kVDP2Regs + 0x014, 0x415F);
        Poke16(s, kVDP2Regs + 0x018, 0x415F);
        Poke16(s, kVDP2Regs + 0x01C, 0x415F);
        Poke16(s, kVDP2Regs + 0x020, 0x0003); // BGON: NBG0 + NBG1
        Poke16(s, kVDP2Regs + 0x028, 0x0012); // CHCTLA: NBG0 bitmap 256 colors, NBG1 cells 16 colors
        Poke16(s, kVDP2Regs + 0x032, 0x8004); // PNCN1: 1-word pattern names, supplementary char bits = 4 (0x1000)
        Poke16(s, kVDP2Regs + 0x03A, 0x0000); // PLSZ
        Poke16(s, kVDP2Regs + 0x044, 0x1414); // MPABN1: page 20 (0x28000)
        Poke16(s, kVDP2Regs + 0x046, 0x1414); // MPCDN1
        Poke16(s, kVDP2Regs + 0x080, 0x0005); // SCXIN1
        Poke16(s, kVDP2Regs + 0x084, 0x0003); // SCYIN1
        Poke16(s, kVDP2Regs + 0x088, 0x0001); // ZMXIN1
        Poke16(s, kVDP2Regs + 0x08C, 0x0001); // ZMYIN1
        Poke16(s, kVDP2Regs + 0x0F8, 0x0403); // PRINA: NBG0 = 3, NBG1 = 4
        // 16 characters (8x8, 4bpp) at 0x20000, character number = address / 0x20 = 0x1000 + n
        for (int ch = 0; ch < 16; ++ch)
            for (int row = 0; row < 8; ++row)
                for (int col = 0; col < 8; col += 4) {
                    uint16 v = 0;
                    for (int k = 0; k < 4; ++k) {
                        const int x = col + k;
                        const int c = ((x + row + ch) % 5 == 0) ? 0 : ((x * row + ch) & 15);
                        v = (v << 4) | c;
                    }
                    Poke16(s, kVDP2VRAM + 0x20000 + ch * 0x20 + row * 4 + col / 2, v);
                }
        for (int y = 0; y < 64; ++y)
            for (int x = 0; x < 64; ++x) {
                const uint16 chr = 0x1000 + ((x * 3 + y) & 15);
                const uint16 pal = ((x + y) & 7) << 12;
                const uint16 flip = (x & 4) ? 0x0400 : 0;
                Poke16(s, kVDP2VRAM + 0x28000 + (y * 64 + x) * 2, pal | flip | (chr & 0x3FF));
            }
    }
    if (scene == 5) {
        // RBG0: rotated 256-color bitmap (rotation parameter A, 20 degrees around the screen center)
        Poke16(s, kVDP2Regs + 0x00E, 0x000F); // RAMCTL: VRAM-A0/A1 hold RBG character (bitmap) data
        Poke16(s, kVDP2Regs + 0x020, 0x0010); // BGON: RBG0
        Poke16(s, kVDP2Regs + 0x02A, 0x1200); // CHCTLB: RBG0 bitmap 512x256, 256 colors
        Poke16(s, kVDP2Regs + 0x03E, 0x0000); // MPOFR
        Poke16(s, kVDP2Regs + 0x0B0, 0x0000); // RPMD: parameter A
        Poke16(s, kVDP2Regs + 0x0B2, 0x0000); // RPRCTL
        Poke16(s, kVDP2Regs + 0x0B4, 0x0000); // KTCTL: no coefficient table
        Poke16(s, kVDP2Regs + 0x0BC, 0x0003); // RPTAU
        Poke16(s, kVDP2Regs + 0x0BE, 0xF800); // RPTAL -> table at 0x7F000
        Poke16(s, kVDP2Regs + 0x0FC, 0x0003); // PRIR: RBG0 priority 3
        auto w32 = [&](uint32 off, uint32 v) {
            Poke16(s, kVDP2VRAM + 0x7F000 + off, v >> 16);
            Poke16(s, kVDP2VRAM + 0x7F000 + off + 2, v & 0xFFFF);
        };
        auto fx = [](int v) { return (uint32)(v << 6); };
        for (uint32 off = 0; off < 0x60; off += 4) w32(off, 0);
        w32(0x00, fx(-40 << 10)); // Xst
        w32(0x04, fx(-30 << 10)); // Yst
        w32(0x08, fx(5 << 10));   // Zst
        w32(0x10, fx(1024));      // deltaYst = 1.0
        w32(0x14, fx(1024));      // deltaX = 1.0
        w32(0x1C, fx(962));       // A = cos
        w32(0x20, fx(-350));      // B = -sin
        w32(0x24, fx(37));        // C
        w32(0x28, fx(350));       // D = sin
        w32(0x2C, fx(962));       // E = cos
        w32(0x30, fx(-21));       // F
        Poke16(s, kVDP2VRAM + 0x7F000 + 0x34, 20);   // Px
        Poke16(s, kVDP2VRAM + 0x7F000 + 0x36, 10);   // Py
        Poke16(s, kVDP2VRAM + 0x7F000 + 0x38, 300);  // Pz
        Poke16(s, kVDP2VRAM + 0x7F000 + 0x3C, 160);  // Cx
        Poke16(s, kVDP2VRAM + 0x7F000 + 0x3E, 112);  // Cy
        Poke16(s, kVDP2VRAM + 0x7F000 + 0x40, 0);    // Cz
        w32(0x44, fx(7 << 10));    // Mx
        w32(0x48, fx(-3 << 10));   // My
        w32(0x4C, 0x00012000);     // kx = 1.125
        w32(0x50, 0x0000E000);     // ky = 0.875
    }
    Poke16(s, kVDP2VRAM + 0x7FFFE, 0x5000); // back screen: dark blue
    for (int i = 0; i < 256; ++i) {
        const uint16 r = i & 31, g = (i >> 3) & 31, b = (255 - i) >> 3;
        Poke16(s, kVDP2CRAM + i * 2, (b << 10) | (g << 5) | r);
    }
    for (uint32 y = 0; y < 256; ++y) {
        for (uint32 x = 0; x < 512; x += 2) {
            auto pix = [&](uint32 xx) -> uint8 { return ((xx / 4) ^ (y / 4)) & 0x3F ? (uint8)((xx + y * 3) & 0xFF) : 0; };
            Poke16(s, kVDP2VRAM + y * 512 + x, (pix(x) << 8) | pix(x + 1));
        }
    }

    // ---- VDP1 ----
    // Texture: 16x16 RGB at 0x2000; gouraud table at 0x1000
    for (int y = 0; y < 16; ++y)
        for (int x = 0; x < 16; ++x)
            Poke16(s, kVDP1VRAM + 0x2000 + (y * 16 + x) * 2,
                   (x == 0 || y == 0) ? 0x0000 : 0x8000 | ((x * 2) << 10) | ((y * 2) << 5) | 31);
    const uint16 grd[4] = {0x801F, 0x83E0, 0xFC00, 0xFFFF};
    for (int i = 0; i < 4; ++i) Poke16(s, kVDP1VRAM + 0x1000 + i * 2, grd[i]);

    int c = 0;
    Cmd(s, c++, {{0x00, 0x0009}, {0x14, 319}, {0x16, 223}});                                     // system clip
    Cmd(s, c++, {{0x00, 0x000A}, {0x0C, 0}, {0x0E, 0}});                                         // local coords
    Cmd(s, c++, {{0x00, 0x0004}, {0x04, 0x00C0}, {0x06, 0x801F}, {0x0C, 20}, {0x0E, 20}, {0x10, 140}, {0x12, 20},
                 {0x14, 140}, {0x16, 120}, {0x18, 20}, {0x1A, 120}});                             // red polygon
    Cmd(s, c++, {{0x00, 0x0004}, {0x04, 0x00C4}, {0x06, 0x83E0}, {0x0C, 60}, {0x0E, 60}, {0x10, 200}, {0x12, 40},
                 {0x14, 220}, {0x16, 160}, {0x18, 80}, {0x1A, 180}, {0x1C, 0x1000 >> 3}});        // gouraud polygon
    Cmd(s, c++, {{0x00, 0x0004}, {0x04, 0x00C3}, {0x06, 0xFC00}, {0x0C, 100}, {0x0E, 100}, {0x10, 260}, {0x12, 100},
                 {0x14, 260}, {0x16, 200}, {0x18, 100}, {0x1A, 200}});                            // half-transparent
    Cmd(s, c++, {{0x00, 0x0004}, {0x04, 0x01C0}, {0x06, 0x83FF}, {0x0C, 240}, {0x0E, 20}, {0x10, 300}, {0x12, 20},
                 {0x14, 300}, {0x16, 80}, {0x18, 240}, {0x1A, 80}});                              // mesh polygon
    Cmd(s, c++, {{0x00, 0x0000}, {0x04, 0x00E8}, {0x08, 0x2000 >> 3}, {0x0A, 0x0210}, {0x0C, 30}, {0x0E, 150}}); // sprite
    Cmd(s, c++, {{0x00, 0x0001}, {0x04, 0x00E8}, {0x08, 0x2000 >> 3}, {0x0A, 0x0210}, {0x0C, 180}, {0x0E, 170},
                 {0x14, 240}, {0x16, 215}});                                                      // scaled sprite
    Cmd(s, c++, {{0x00, 0x0002}, {0x04, 0x00E8}, {0x08, 0x2000 >> 3}, {0x0A, 0x0210}, {0x0C, 250}, {0x0E, 120},
                 {0x10, 310}, {0x12, 110}, {0x14, 300}, {0x16, 200}, {0x18, 240}, {0x1A, 190}});  // distorted sprite
    Cmd(s, c++, {{0x00, 0x0006}, {0x04, 0x00C0}, {0x06, 0xFFE0}, {0x0C, 0}, {0x0E, 223}, {0x10, 319}, {0x12, 0}}); // line
    Cmd(s, c++, {{0x00, 0x0005}, {0x04, 0x00C0}, {0x06, 0x83FF}, {0x0C, 10}, {0x0E, 10}, {0x10, 310}, {0x12, 10},
                 {0x14, 310}, {0x16, 213}, {0x18, 10}, {0x1A, 213}});                             // polyline
    Cmd(s, c++, {{0x00, 0x0004}, {0x04, 0x00C1}, {0x06, 0x8000}, {0x0C, 20}, {0x0E, 140}, {0x10, 70}, {0x12, 140},
                 {0x14, 70}, {0x16, 200}, {0x18, 20}, {0x1A, 200}});                              // shadow
    Cmd(s, c++, {{0x00, 0x8000}});                                                               // end

    if (scene == 8) {
        // Palette-coded sprites/polygons (no MSB): VDP2 sprite type 0 decodes priority/color calc/palette
        Poke16(s, kVDP2Regs + 0x0E0, 0x0000); // SPCTL: type 0, palette only
        Poke16(s, kVDP2Regs + 0x0F0, 0x0505); // PRISA: sprite regs 0/1 priority 5
        Poke16(s, kVDP2Regs + 0x0F2, 0x0606);
        Poke16(s, kVDP2Regs + 0x0F4, 0x0202); // PRISC: regs 4/5 behind NBG0
        Poke16(s, kVDP2Regs + 0x0F6, 0x0707);
        const uint32 cmd2 = kVDP1VRAM + 2 * 0x20, cmd4 = kVDP1VRAM + 4 * 0x20, cmd5 = kVDP1VRAM + 5 * 0x20;
        Poke16(s, cmd2 + 0x06, 0x0040);          // palette 0x40, priority reg 0
        Poke16(s, cmd4 + 0x06, 0x8000 | 0x0080); // reg 4 (behind the background), palette 0x80 -- bit 15 is priority bit
        Poke16(s, cmd5 + 0x06, 0x4000 | 0x00C0); // reg 2
        // 4bpp palette texture for the sprites (color mode 0 = 16-color bank)
        for (int y = 0; y < 16; ++y)
            for (int x = 0; x < 16; x += 4)
                Poke16(s, kVDP1VRAM + 0x3000 + y * 8 + x / 2,
                       (uint16)((((x + y) & 15) << 12) | (((x + 1 + y) & 15) << 8) | (((x + 2 + y) & 15) << 4) | ((x + 3 + y) & 15)));
        for (int c : {6, 7, 8}) {
            const uint32 cmd = kVDP1VRAM + c * 0x20;
            Poke16(s, cmd + 0x04, 0x00C0);           // color mode 0 (16-color bank)
            Poke16(s, cmd + 0x06, 0x0110);           // color bank
            Poke16(s, cmd + 0x08, 0x3000 >> 3);
            Poke16(s, cmd + 0x0A, 0x0210);
        }
    }
    Poke16(s, kVDP1Regs + 0x00, scene == 7 ? 0x0008 : 0x0000); // TVMR (scene 7: VBlank erase)
    Poke16(s, kVDP1Regs + 0x02, scene == 7 ? 0x0003 : 0x0000); // FBCR
    Poke16(s, kVDP1Regs + 0x06, 0x0000);              // EWDR
    Poke16(s, kVDP1Regs + 0x08, 0x0000);              // EWLR
    Poke16(s, kVDP1Regs + 0x0A, (40 << 9) | 223);     // EWRR
    Poke16(s, kVDP1Regs + 0x04, 0x0002);              // PTMR: draw automatically every frame
}

std::string LoadDebugState(Saturn &saturn, const std::string &path);

static bool Run(bool vulkan, int scene, Frame &out, std::string &err, const std::string &statePath = {}) {
    auto saturn = std::make_unique<Saturn>();
    std::vector<uint8> ipl(sys::kIPLSize, 0);
    const uint8 boot[] = {0x00, 0x00, 0x01, 0x00, 0x06, 0x00, 0x40, 0x00}; // PC=0x100, SP=0x06004000
    std::memcpy(ipl.data(), boot, sizeof(boot));
    ipl[0x100] = 0xAF; // bra $
    ipl[0x101] = 0xFE;
    ipl[0x102] = 0x00; // nop
    ipl[0x103] = 0x09;
    saturn->LoadIPL(std::span<uint8, sys::kIPLSize>(ipl.data(), sys::kIPLSize));
    saturn->configuration.swRenderer.threadedVDP1 = false;
    saturn->configuration.swRenderer.threadedVDP2 = false;
    saturn->configuration.swRenderer.threadedDeinterlacer = false;
    saturn->Reset(true);

    auto onFrame = [](const uint32 *fb, uint32 w, uint32 h, void *ctx) {
        auto &f = *static_cast<Frame *>(ctx);
        f.prev.swap(f.px);
        f.px.assign(fb, fb + (size_t)w * h);
        f.w = w;
        f.h = h;
        ++f.count;
    };
    saturn->VDP.SetSoftwareRenderCallback({&out, [](uint32 *fb, uint32 w, uint32 h, void *ctx) {
                                               auto &f = *static_cast<Frame *>(ctx);
                                               f.prev.swap(f.px);
                                               f.px.assign(fb, fb + (size_t)w * h);
                                               f.w = w;
                                               f.h = h;
                                               ++f.count;
                                           }});
    saturn->VDP.SetVulkanFrameReadyCallback({&out, onFrame});
    if (vulkan) {
        auto r = saturn->VDP.UseVulkanRenderer(vdp::VulkanRendererContext{});
        if (!r) {
            err = r.Error().message;
            return false;
        }
    } else {
        saturn->VDP.UseSoftwareRenderer();
    }
    if (!statePath.empty()) {
        err = LoadDebugState(*saturn, statePath);
        if (!err.empty()) {
            return false;
        }
        for (int i = 0; i < 4; ++i) saturn->RunFrame();
        if (!vulkan) {
            std::printf("VDP2 registers:");
            for (uint32 r = 0; r <= 0x11E; r += 2) {
                if (r % 32 == 0) std::printf("\n  %03X:", r);
                std::printf(" %04X", saturn->mainBus.Peek<uint16>(kVDP2Regs + r));
            }
            std::printf("\nVDP1 TVMR/FBCR/PTMR: %04X %04X %04X\n", saturn->mainBus.Peek<uint16>(kVDP1Regs + 0),
                        saturn->mainBus.Peek<uint16>(kVDP1Regs + 2), saturn->mainBus.Peek<uint16>(kVDP1Regs + 4));
        }
        return true;
    }
    SetupScene(*saturn, scene);
    for (int i = 0; i < 12; ++i) {
        if (scene >= 6) {
            // Moving objects: shift the red polygon and the normal sprite every frame (exposes erase/swap bugs)
            const uint16 dx = i * 7;
            const uint32 cmd2 = kVDP1VRAM + 2 * 0x20, cmd6 = kVDP1VRAM + 6 * 0x20;
            Poke16(*saturn, cmd2 + 0x0C, 20 + dx);
            Poke16(*saturn, cmd2 + 0x10, 140 + dx);
            Poke16(*saturn, cmd2 + 0x14, 140 + dx);
            Poke16(*saturn, cmd2 + 0x18, 20 + dx);
            Poke16(*saturn, cmd6 + 0x0C, 30 + dx * 2);
            if (scene == 7) {
                Poke16(*saturn, kVDP1Regs + 0x02, 0x0003); // FBCR: manual erase + swap at the next frame
            }
        }
        saturn->RunFrame();
    }
    return true;
}

static char Shade(uint32 p) {
    const uint32 r = p & 0xFF, g = (p >> 8) & 0xFF, b = (p >> 16) & 0xFF;
    const uint32 l = (r * 3 + g * 6 + b) / 10;
    static const char ramp[] = " .:-=+*#%@";
    return ramp[l * 9 / 255];
}

static std::string Ascii(const Frame &f, int cols = 64, int rows = 24) {
    std::string s;
    for (int r = 0; r < rows; ++r) {
        for (int c = 0; c < cols; ++c) {
            const uint32 x = c * f.w / cols, y = r * f.h / rows;
            s += f.px.empty() ? '?' : Shade(f.px[y * f.w + x]);
        }
        s += '\n';
    }
    return s;
}

static int CompareState(const std::string &path) {
    Frame sw, vk;
    std::string err;
    if (!Run(false, 0, sw, err, path)) {
        std::printf("software: %s\n", err.c_str());
        return 2;
    }
    if (!Run(true, 0, vk, err, path)) {
        std::printf("vulkan: %s\n", err.c_str());
        return 2;
    }
    if (!sw.prev.empty()) sw.px = sw.prev;
    std::printf("STATE %s: software %ux%u (%d frames), vulkan %ux%u (%d frames)\n", path.c_str(), sw.w, sw.h, sw.count,
                vk.w, vk.h, vk.count);
    if (sw.w != vk.w || sw.h != vk.h || sw.px.empty() || vk.px.empty()) {
        std::printf("size mismatch\n");
        return 1;
    }
    const int cols = 80, rows = 30;
    std::vector<int> cell(cols * rows, 0);
    size_t big = 0;
    int shown = 0;
    for (uint32 y = 0; y < sw.h; ++y)
        for (uint32 x = 0; x < sw.w; ++x) {
            const uint32 a = sw.px[y * sw.w + x] & 0xFFFFFF, b = vk.px[y * sw.w + x] & 0xFFFFFF;
            int maxd = 0;
            for (int k = 0; k < 24; k += 8) maxd = std::max(maxd, std::abs(int((a >> k) & 0xFF) - int((b >> k) & 0xFF)));
            if (maxd > 8) {
                ++big;
                cell[(y * rows / sw.h) * cols + x * cols / sw.w]++;
                if (shown < 12 && (x % 7 == 0)) {
                    std::printf("  px(%u,%u) sw=%06X vk=%06X\n", x, y, a, b);
                    ++shown;
                }
            }
        }
    std::printf("differing pixels: %zu of %u\n", big, sw.w * sw.h);
    std::printf("software:\n%s", Ascii(sw, cols, rows).c_str());
    std::printf("vulkan:\n%s", Ascii(vk, cols, rows).c_str());
    std::printf("diff map:\n");
    for (int r = 0; r < rows; ++r) {
        for (int c = 0; c < cols; ++c) std::putchar(cell[r * cols + c] ? 'X' : '.');
        std::putchar('\n');
    }
    return big ? 1 : 0;
}

int main(int argc, char **argv) {
    if (argc > 1) {
        int rc = 0;
        for (int i = 1; i < argc; ++i) rc |= CompareState(argv[i]);
        return rc;
    }
    int failures = 0;
    for (int test = 0; test < 10; ++test) {
        const int scene = test < 6 ? test : test == 6 ? 5 : test - 1;
        const bool noInt64 = test == 6;
        if (test == 7) unsetenv("YMIR_VK_NO_INT64");
        if (noInt64) setenv("YMIR_VK_NO_INT64", "1", 1);
        Frame sw, vk;
        std::string err;
        Run(false, scene, sw, err);
        if (!sw.prev.empty()) sw.px = sw.prev; // compare against the same frame the Vulkan renderer delivered last
        if (!Run(true, scene, vk, err)) {
            std::printf("SCENE %d: Vulkan renderer failed to start: %s\n", scene, err.c_str());
            return 2;
        }
        std::printf("SCENE %d%s: software %ux%u (%d frames), vulkan %ux%u (%d frames)\n", scene, noInt64 ? " (no int64)" : "", sw.w, sw.h, sw.count,
                    vk.w, vk.h, vk.count);
        if (sw.w != vk.w || sw.h != vk.h || sw.px.empty()) {
            std::printf("  size mismatch\n");
            ++failures;
            continue;
        }
        size_t diff = 0, big = 0;
        std::string map;
        const int cols = 64, rows = 24;
        std::vector<int> cell(cols * rows, 0);
        for (uint32 y = 0; y < sw.h; ++y) {
            for (uint32 x = 0; x < sw.w; ++x) {
                const uint32 a = sw.px[y * sw.w + x] & 0xFFFFFF, b = vk.px[y * sw.w + x] & 0xFFFFFF;
                if (a != b) {
                    ++diff;
                    int maxd = 0;
                    for (int k = 0; k < 24; k += 8) maxd = std::max(maxd, std::abs(int((a >> k) & 0xFF) - int((b >> k) & 0xFF)));
                    if (maxd > 8) {
                        ++big;
                        cell[(y * rows / sw.h) * cols + x * cols / sw.w]++;
                    }
                }
            }
        }
        std::printf("  differing pixels: %zu (%zu with a channel difference > 8) of %u\n", diff, big, sw.w * sw.h);
        if (big > 0) {
            std::printf("  software:\n%s  vulkan:\n%s", Ascii(sw).c_str(), Ascii(vk).c_str());
            std::printf("  diff map:\n");
            for (int r = 0; r < rows; ++r) {
                for (int c = 0; c < cols; ++c) std::putchar(cell[r * cols + c] ? 'X' : '.');
                std::putchar('\n');
            }
            ++failures;
        }

        // Sample pixels for debugging
        if (big > 0) for (auto [x, y] : std::initializer_list<std::pair<uint32, uint32>>{{5, 5}, {50, 50}, {150, 150}, {270, 50}, {40, 160}}) {
            if (x < sw.w && y < sw.h)
                std::printf("  px(%u,%u) sw=%06X vk=%06X\n", x, y, sw.px[y * sw.w + x] & 0xFFFFFF, vk.px[y * sw.w + x] & 0xFFFFFF);
        }
    }
    std::printf("RESULT: %s\n", failures ? "MISMATCH" : "MATCH");
    return failures ? 1 : 0;
}
