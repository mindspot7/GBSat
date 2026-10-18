#ifndef YMIR_VDP_VDP1_COMMON_PARAMS_HLSLI
#define YMIR_VDP_VDP1_COMMON_PARAMS_HLSLI

// See C++ code for documentation on the fields

struct CommonRenderParams {
    uint displayParams;
    uint enhancements;
};

// Internal resolution scale factor (enhancements bits 2-4; 0 means 1x)
#define kResScale (max(BitExtract(g_commonParams.enhancements, 2, 3), 1u))
#define kResScaleSq (kResScale * kResScale)

#endif
