#ifndef YMIR_VDP_VDP1_SCALED_FB_HLSLI
#define YMIR_VDP_VDP1_SCALED_FB_HLSLI

// Applies a masked 32-bit write addressed in native (1x) framebuffer coordinates to a framebuffer stored at the
// internal resolution scale. Only used with 16-bit pixels.
// - fbBase: byte offset of the framebuffer
// - strideWords: native row stride in 16-bit words
// - nativePos: native pixel coordinates of the first of the two pixels covered by the 32-bit unit (x is even)
// - andMask/orMask: native 32-bit masks; half 0 (low 16 bits) is the first pixel
// - replace: true to compute (old & andMask) | orMask, false to store orMask directly (andMask must be 0)
void ScaledFBWrite(RWByteAddressBuffer buf, uint fbBase, uint strideWords, uint2 nativePos, uint andMask, uint orMask) {
    const uint S = kResScale;
    const uint scaledStride = strideWords * S;
    for (uint r = 0; r < S; ++r) {
        const uint row = nativePos.y * S + r;
        for (uint k = 0; k < S; ++k) {
            const uint col = nativePos.x * S + k * 2u;
            uint a = 0;
            uint o = 0;
            [unroll]
            for (uint h = 0; h < 2; ++h) {
                const uint nh = (k * 2u + h) / S; // native half (0 or 1)
                a |= ((andMask >> (nh * 16u)) & 0xFFFFu) << (h * 16u);
                o |= ((orMask >> (nh * 16u)) & 0xFFFFu) << (h * 16u);
            }
            const uint address = fbBase + (row * scaledStride + col) * 2u;
            uint value = o;
            if (a != 0) {
                value |= buf.Load(address) & a;
            }
            buf.Store(address, value);
        }
    }
}

#endif
