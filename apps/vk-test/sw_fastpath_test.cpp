// Checks that the software renderer's scroll background fast path (g_swScrollFastPath) draws exactly the same frames as
// the per-dot path. Random VDP2 VRAM/CRAM contents and random NBG0-3 cell-mode configurations (character size, colors,
// pattern name format, plane size, maps, scroll, zoom, mosaic, line/cell scroll, windows, resolutions) are rendered with
// both paths and compared pixel by pixel. No BIOS needed.
#include <ymir/ymir.hpp>
#include <ymir/hw/vdp/renderer/vdp_renderer_sw.hpp>

#include <cstdio>
#include <cstring>
#include <memory>
#include <random>
#include <vector>

using namespace ymir;

constexpr uint32 kVDP2VRAM = 0x25E00000, kVDP2CRAM = 0x25F00000, kVDP2Regs = 0x25F80000;

struct Frame {
    std::vector<uint32> px;
    uint32 w = 0, h = 0;
};

static void Setup(Saturn &s, uint32 seed) {
    std::mt19937 rng(seed);
    auto r16 = [&] { return (uint16)rng(); };
    auto P = [&](uint32 a, uint16 v) { s.mainBus.Poke<uint16>(a, v); };
    for (uint32 a = 0; a < 0x80000; a += 2) P(kVDP2VRAM + a, r16());
    for (uint32 a = 0; a < 0x1000; a += 2) P(kVDP2CRAM + a, r16());
    // TVMD: display on, random horizontal resolution (incl. hi-res), 224/240 lines, non-interlaced
    P(kVDP2Regs + 0x000, 0x8000 | (rng() % 4) | ((rng() % 2) << 4));
    P(kVDP2Regs + 0x00E, (uint16)(rng() % 2 ? 0x0000 : 0x1000) | (uint16)(rng() & 0x0300)); // RAMCTL: CRAM mode, banks
    // Cycle patterns: mostly permissive (pattern names and character data for every NBG), sometimes random
    const bool randomCycles = rng() % 4 == 0;
    const uint16 cyc[8] = {0x0123, 0x4567, 0x0123, 0x4567, 0x0123, 0x4567, 0x0123, 0x4567};
    for (int i = 0; i < 8; ++i) P(kVDP2Regs + 0x010 + i * 2, randomCycles ? r16() : cyc[i]);
    P(kVDP2Regs + 0x020, 0x000F | (uint16)(rng() & 0x0F00)); // BGON: NBG0-3 (+ random transparency disables)
    P(kVDP2Regs + 0x022, (uint16)(rng() & 0x7F7F));          // MZCTL: mosaic sometimes
    P(kVDP2Regs + 0x028, (uint16)(rng() & 0x3F31));          // CHCTLA: cell mode (bitmap bits clear), random sizes/colors
    P(kVDP2Regs + 0x02A, (uint16)(rng() & 0x3311));          // CHCTLB
    for (int i = 0; i < 4; ++i) P(kVDP2Regs + 0x030 + i * 2, r16()); // PNCN0-3: 1/2 words, supplementary data
    P(kVDP2Regs + 0x03A, r16() & 0x00FF);                     // PLSZ
    P(kVDP2Regs + 0x03C, r16() & 0x7777);                     // MPOFN
    for (uint32 r = 0x040; r <= 0x04E; r += 2) P(kVDP2Regs + r, r16() & 0x3F3F); // MPABN0..MPCDN3
    // Scroll: NBG0/1 with fractional parts and zoom 1.0 most of the time (fast path), NBG2/3 integer
    for (int i = 0; i < 2; ++i) {
        const uint32 base = 0x070 + i * 0x10;
        P(kVDP2Regs + base + 0x0, r16() & 0x07FF);
        P(kVDP2Regs + base + 0x2, r16() & 0xFF00);
        P(kVDP2Regs + base + 0x4, r16() & 0x07FF);
        P(kVDP2Regs + base + 0x6, r16() & 0xFF00);
        const bool zoom = rng() % 4 == 0;
        P(kVDP2Regs + base + 0x8, zoom ? (r16() & 0x0007) : 0x0001);
        P(kVDP2Regs + base + 0xA, zoom ? (r16() & 0xFF00) : 0x0000);
        P(kVDP2Regs + base + 0xC, 0x0001);
        P(kVDP2Regs + base + 0xE, 0x0000);
    }
    for (int i = 0; i < 2; ++i) {
        P(kVDP2Regs + 0x090 + i * 4, r16() & 0x07FF);
        P(kVDP2Regs + 0x092 + i * 4, r16() & 0x07FF);
    }
    P(kVDP2Regs + 0x09A, (uint16)(rng() % 3 == 0 ? (r16() & 0x3F3F) : 0)); // SCRCTL: line/cell scroll sometimes
    P(kVDP2Regs + 0x09C, r16() & 0x0003);                                     // VCSTA
    P(kVDP2Regs + 0x09E, r16());
    for (uint32 r = 0x0A0; r <= 0x0A6; r += 2) P(kVDP2Regs + r, r16() & 0x0007); // LSTA0/1
    if (rng() % 3 == 0) {                                                        // windows sometimes
        P(kVDP2Regs + 0x0C0, r16() & 0x03FF);
        P(kVDP2Regs + 0x0C2, r16() & 0x00FF);
        P(kVDP2Regs + 0x0C4, r16() & 0x03FF);
        P(kVDP2Regs + 0x0C6, r16() & 0x00FF);
        P(kVDP2Regs + 0x0D0, r16() & 0x8F8F);
        P(kVDP2Regs + 0x0D2, r16() & 0x8F8F);
    }
    P(kVDP2Regs + 0x0E8, r16() & 0x3F3F); // SFSEL/SFCODE-ish area
    P(kVDP2Regs + 0x0EC, r16() & 0x3333); // CCCTL etc (random color calc)
    P(kVDP2Regs + 0x0F8, 0x0706);         // PRINA: NBG0 7, NBG1 6
    P(kVDP2Regs + 0x0FA, 0x0504);         // PRINB: NBG2 5, NBG3 4
    P(kVDP2Regs + 0x0AC, 0x0003);
    P(kVDP2Regs + 0x0AE, 0xFFFE);
}

static Frame Render(uint32 seed, bool fast) {
    vdp::g_swScrollFastPath = fast;
    auto saturn = std::make_unique<Saturn>();
    std::vector<uint8> ipl(sys::kIPLSize, 0);
    const uint8 boot[] = {0x00, 0x00, 0x01, 0x00, 0x06, 0x00, 0x40, 0x00};
    std::memcpy(ipl.data(), boot, sizeof(boot));
    ipl[0x100] = 0xAF;
    ipl[0x101] = 0xFE;
    ipl[0x102] = 0x00;
    ipl[0x103] = 0x09;
    saturn->LoadIPL(std::span<uint8, sys::kIPLSize>(ipl.data(), sys::kIPLSize));
    saturn->configuration.swRenderer.threadedVDP1 = false;
    saturn->configuration.swRenderer.threadedVDP2 = false;
    saturn->configuration.swRenderer.threadedDeinterlacer = false;
    saturn->Reset(true);
    Frame f;
    saturn->VDP.SetSoftwareRenderCallback({&f, [](uint32 *fb, uint32 w, uint32 h, void *ctx) {
                                               auto &fr = *static_cast<Frame *>(ctx);
                                               fr.px.assign(fb, fb + (size_t)w * h);
                                               fr.w = w;
                                               fr.h = h;
                                           }});
    saturn->VDP.UseSoftwareRenderer();
    Setup(*saturn, seed);
    for (int i = 0; i < 3; ++i) saturn->RunFrame();
    return f;
}

int main(int argc, char **argv) {
    const uint32 count = argc > 1 ? (uint32)std::atoi(argv[1]) : 300;
    uint32 failures = 0, nonEmpty = 0;
    for (uint32 seed = 1; seed <= count; ++seed) {
        const Frame a = Render(seed, false);
        const Frame b = Render(seed, true);
        if (a.w != b.w || a.h != b.h || a.px != b.px) {
            size_t diff = 0;
            for (size_t i = 0; i < std::min(a.px.size(), b.px.size()); ++i) diff += a.px[i] != b.px[i];
            std::printf("seed %u: MISMATCH (%ux%u vs %ux%u, %zu pixels differ)\n", seed, a.w, a.h, b.w, b.h, diff);
            ++failures;
        }
        bool any = false;
        for (size_t i = 1; i < a.px.size() && !any; ++i) any = a.px[i] != a.px[0];
        nonEmpty += any;
    }
    std::printf("%u scenes, %u with visible content, %u mismatches\nRESULT: %s\n", count, nonEmpty, failures,
                failures ? "MISMATCH" : "MATCH");
    return failures ? 1 : 0;
}
