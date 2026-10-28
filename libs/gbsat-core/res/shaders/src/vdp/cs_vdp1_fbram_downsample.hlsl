#include "vdp1_defs.hlsli"
#include "vdp1_common_params.hlsli"

#include "util/bit_ops.hlsli"

cbuffer CommonRenderParamsBuffer : register(b0) {
    CommonRenderParams g_commonParams;
}

// Scaled framebuffers (kResScale > 1, 16-bit pixels, 512 pixels wide) -> native FBRAM (both framebuffers, 256 KiB each),
// for the CPU to read: games that read the framebuffer back (Burning Rangers copies it for its transitions) otherwise
// read whatever the native copy held when scaling started
RWByteAddressBuffer g_fbramIn : register(u1);
RWByteAddressBuffer g_nativeOut : register(u2);

static const uint kNativeFBSize = 256 * 1024;
static const uint kWidth = 512;

// One thread per 32-bit unit (two pixels) of the native framebuffers
[numthreads(64, 1, 1)]
void CSMain(uint3 id : SV_DispatchThreadID) {
    const uint unit = id.x;
    if (unit >= kNativeFBSize * 2 / 4) {
        return;
    }
    const uint fb = unit / (kNativeFBSize / 4);
    const uint pixel = (unit % (kNativeFBSize / 4)) * 2; // first of the two pixels
    const uint x = pixel % kWidth;
    const uint y = pixel / kWidth;
    const uint S = kResScale;
    const uint base = fb * kVDP1FBSize;
    // The top-left sample of each pixel's block (pixels are 16-bit, stored in 32-bit words)
    uint value = 0;
    [unroll]
    for (uint h = 0; h < 2; ++h) {
        const uint col = (x + h) * S;
        const uint address = base + ((y * S) * (kWidth * S) + col) * 2u;
        const uint word = g_fbramIn.Load(address & ~3u);
        const uint half = (address & 2u) ? (word >> 16u) : (word & 0xFFFFu);
        value |= half << (h * 16u);
    }
    g_nativeOut.Store(unit * 4u, value);
}
