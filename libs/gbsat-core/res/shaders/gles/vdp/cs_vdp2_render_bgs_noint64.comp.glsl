#version 310 es
#extension GL_EXT_texture_buffer : require
layout(local_size_x = 32, local_size_y = 1, local_size_z = 7) in;

struct CommonRenderParams
{
    uint startY;
    uint displayParams;
    uint layerParams;
    uint rotParams;
    uint spriteParams;
    uvec2 spritePriosRatios;
    uint vcellScroll;
    uint windows;
    uint enhancements;
};

struct LayerWindowParams
{
    uint windowLogicAnd;
    uint window0Enable;
    uint window0Invert;
    uint window1Enable;
    uint window1Invert;
};

struct LayerWindowParamsS
{
    LayerWindowParams base;
    uint spriteWindowEnable;
    uint spriteWindowInvert;
};

struct BaseBGParams
{
    uint enabled;
    uint enableTransparency;
    uint bitmap;
    uint priorityNumber;
    uint priorityMode;
    uint specialFunctionSelect;
    uint cellSizeShift;
    uint colorFormat;
    uint cramOffset;
    uint supplScrollCharNum;
    uint supplPalNum;
    uint supplSpecialColorCalc;
    uint supplSpecialPriority;
    uint mosaicEnable;
    uint colorCalcEnable;
    uint extChar;
    uint twoWordChar;
    uint patNameAccess;
    uint charPatAccess;
    uint charPatDelay;
    uint vramDataOffset;
    uint specialColorCalcMode;
    uvec2 pageShift;
    uvec2 bitmapSize;
    uint bitmapBaseAddress;
    LayerWindowParamsS windowParams;
};

struct NBGParams
{
    BaseBGParams base;
    uvec2 scrollAmount;
    uvec2 scrollInc;
    uint pageBaseAddresses[4];
    uint vcellScrollEnable;
    uint lineScrollXEnable;
    uint lineScrollYEnable;
    uint lineZoomEnable;
    uint lineScrollInterval;
    uint lineScrollTableAddress;
    uint vcellScrollOffset;
    uint vcellScrollDelay;
    uint vcellScrollRepeat;
    uint scrollYIncBase;
};

struct RBGParams
{
    BaseBGParams base;
    uint screenOverProcess;
    uint screenOverPatternName;
    uint pageBaseAddresses[2][16];
};

struct GlobalWindowParams
{
    uvec2 start;
    uvec2 end;
    uint lineWindowTableAddress;
    uint lineWindowTableEnable;
};

struct LineBackScreenParams
{
    uint baseAddress;
    uint perLine;
};

struct LayerRenderParams
{
    NBGParams nbg[4];
    RBGParams rbg[2];
    GlobalWindowParams windows[2];
    LayerWindowParams rotWindows;
    LineBackScreenParams lineScreenParams;
    LineBackScreenParams backScreenParams;
    uint specialFunctionCodes;
};

struct RotParamBase
{
    uint tableAddress;
    int Xst;
    int Yst;
    uint KA;
};

uint _178;
bool _179;
const uint _180[4] = uint[](320u, 352u, 640u, 704u);
const uint _181[4] = uint[](224u, 240u, 256u, 256u);
const uint _184[2][2] = uint[][](uint[](13u, 14u), uint[](11u, 12u));

layout(binding = 0, std140) uniform type_CommonRenderParamsBuffer
{
    CommonRenderParams g_commonParams;
} CommonRenderParamsBuffer;

layout(binding = 1, std430) readonly buffer type_StructuredBuffer_LayerRenderParams
{
    LayerRenderParams _m0[];
} g_layerRenderParams;

layout(binding = 2, std430) readonly buffer type_ByteAddressBuffer
{
    uint _m0[];
} g_vram;

layout(binding = 4, std430) readonly buffer g_cramRotCoeff
{
    uint _m0[];
} g_cramRotCoeff_1;

layout(binding = 5, std430) readonly buffer type_StructuredBuffer_RotParamBase
{
    RotParamBase _m0[];
} g_rotParamBases;

layout(binding = 7, std430) readonly buffer type_StructuredBuffer_v4uint
{
    uvec4 _m0[];
} g_nbgLineScroll;

layout(binding = 3) uniform highp usamplerBuffer g_cramColor;
layout(binding = 17, rgba8ui) uniform writeonly highp uimage2DArray g_layerOut;
layout(binding = 18, rgba8ui) uniform writeonly highp uimage2DArray g_rbgLineColorOut;
layout(binding = 19, r32ui) uniform writeonly highp uimage2D g_colorCalcWindowOut;
uniform highp usampler2DArray SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler;

void main()
{
    uint _205 = (CommonRenderParamsBuffer.g_commonParams.displayParams >> 2u) & 3u;
    uint _207 = (CommonRenderParamsBuffer.g_commonParams.displayParams >> 4u) & 1u;
    bool _210 = ((CommonRenderParamsBuffer.g_commonParams.displayParams >> 5u) & 1u) != 0u;
    bool _213 = ((CommonRenderParamsBuffer.g_commonParams.displayParams >> 8u) & 1u) != 0u;
    uint _230;
    if (_210)
    {
        _230 = 480u;
    }
    else
    {
        _230 = _181[(CommonRenderParamsBuffer.g_commonParams.displayParams >> 13u) & ((1u << (uint((((CommonRenderParamsBuffer.g_commonParams.displayParams >> 9u) & 1u) != 0u) ? 2 : 1) & 31u)) - 1u)];
    }
    bool _259;
    bool _260;
    bool _261;
    bool _234 = (CommonRenderParamsBuffer.g_commonParams.rotParams & 1u) != 0u;
    uint _236 = (CommonRenderParamsBuffer.g_commonParams.rotParams >> 1u) & 15u;
    bool _239 = ((CommonRenderParamsBuffer.g_commonParams.rotParams >> 5u) & 1u) != 0u;
    bool _243 = (CommonRenderParamsBuffer.g_commonParams.enhancements & 1u) != 0u;
    uint _248 = uint((((CommonRenderParamsBuffer.g_commonParams.displayParams >> 6u) & 3u) == 1u) ? 2047 : 1023);
    uint _255 = gl_GlobalInvocationID.y + CommonRenderParamsBuffer.g_commonParams.startY;
    uvec2 _256 = uvec2(gl_GlobalInvocationID.x, _255);
    uint _271;
    do
    {
        _259 = _205 >= 2u;
        _260 = !_243;
        _261 = _260 ? _259 : false;
        bool _265;
        if (_261)
        {
            _265 = !_210;
        }
        else
        {
            _265 = false;
        }
        if (_265)
        {
            _271 = (_255 << 1u) | _207;
            break;
        }
        else
        {
            _271 = _255;
            break;
        }
        break; // unreachable workaround
    } while(false);
    uvec3 _274 = uvec3(gl_GlobalInvocationID.x, _271, gl_GlobalInvocationID.z);
    if (gl_GlobalInvocationID.z <= 3u)
    {
        uvec4 _6471;
        do
        {
            uvec2 _4794 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].scrollInc;
            bool _4815 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.enableTransparency != 0u;
            bool _4817 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.mosaicEnable != 0u;
            bool _4818 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorCalcEnable != 0u;
            bool _4819 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.extChar != 0u;
            bool _4820 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.twoWordChar != 0u;
            bool _4821 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.windowParams.base.windowLogicAnd != 0u;
            bool _4822 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.windowParams.base.window0Enable != 0u;
            bool _4824 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.windowParams.base.window1Enable != 0u;
            bool _4826 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.windowParams.spriteWindowEnable != 0u;
            bool _4829 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].lineScrollXEnable != 0u;
            bool _4830 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].lineScrollYEnable != 0u;
            bool _4831 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].lineZoomEnable != 0u;
            if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.enabled == 0u)
            {
                _6471 = uvec4(0u);
                break;
            }
            uint _4848;
            do
            {
                bool _4842;
                if (_260 ? (_205 == 3u) : false)
                {
                    _4842 = !_210;
                }
                else
                {
                    _4842 = false;
                }
                if (_4842)
                {
                    _4848 = (_255 << 1u) | _207;
                    break;
                }
                else
                {
                    _4848 = _255;
                    break;
                }
                break; // unreachable workaround
            } while(false);
            uvec2 _4849 = _256;
            _4849.y = _4848;
            bool _4853;
            if (_243)
            {
                _4853 = _205 == 2u;
            }
            else
            {
                _4853 = false;
            }
            uvec2 _4858;
            if (_4853)
            {
                uvec2 _4857 = _4849;
                _4857.y = _4848 >> 1u;
                _4858 = _4857;
            }
            else
            {
                _4858 = _4849;
            }
            bool _4864;
            if (gl_GlobalInvocationID.z >= 2u)
            {
                _4864 = (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].scrollYIncBase & 2147483648u) != 0u;
            }
            else
            {
                _4864 = false;
            }
            uvec2 _4887;
            uint _4888;
            if (_4864)
            {
                uint _4868 = _4858.y * 2u;
                uint _4871 = _4868 + 1u;
                bool _4874 = gl_GlobalInvocationID.z == 2u;
                uvec2 _4880;
                if (_4874)
                {
                    _4880 = g_nbgLineScroll._m0[_4868].xy;
                }
                else
                {
                    _4880 = g_nbgLineScroll._m0[_4868].zw;
                }
                uint _4886;
                if (_4874)
                {
                    _4886 = g_nbgLineScroll._m0[_4871].x;
                }
                else
                {
                    _4886 = g_nbgLineScroll._m0[_4871].y;
                }
                _4887 = _4880;
                _4888 = _4886;
            }
            else
            {
                _4887 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].scrollAmount;
                _4888 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].scrollYIncBase;
            }
            bool _5146;
            do
            {
                bool _4895;
                if (!_4822)
                {
                    _4895 = !_4824;
                }
                else
                {
                    _4895 = false;
                }
                bool _4899;
                if (_4895)
                {
                    _4899 = !_4826;
                }
                else
                {
                    _4899 = false;
                }
                if (_4899)
                {
                    _5146 = false;
                    break;
                }
                bool _5013;
                if (_4822)
                {
                    ivec2 _4913 = ivec2(g_layerRenderParams._m0[0u].windows[0].start);
                    ivec2 _4914 = ivec2(g_layerRenderParams._m0[0u].windows[0].end);
                    ivec2 _4953;
                    ivec2 _4954;
                    if (g_layerRenderParams._m0[0u].windows[0].lineWindowTableEnable != 0u)
                    {
                        uint _4919 = g_layerRenderParams._m0[0u].windows[0].lineWindowTableAddress + (_4858.y * 4u);
                        uint _4928 = (g_vram._m0[(_4919 & 524284u) >> 2u] >> (((_4919 & 2u) * 8u) & 31u)) & 65535u;
                        ivec2 _4935 = _4913;
                        _4935.x = int(((_4928 >> 8u) & 255u) | ((_4928 << 8u) & 65280u));
                        uint _4936 = _4919 + 2u;
                        uint _4945 = (g_vram._m0[(_4936 & 524284u) >> 2u] >> (((_4936 & 2u) * 8u) & 31u)) & 65535u;
                        ivec2 _4952 = _4914;
                        _4952.x = int(((_4945 >> 8u) & 255u) | ((_4945 << 8u) & 65280u));
                        _4953 = _4952;
                        _4954 = _4935;
                    }
                    else
                    {
                        _4953 = _4914;
                        _4954 = _4913;
                    }
                    int _4957 = (_4954.x << 16) >> 16;
                    int _4960 = (_4953.x << 16) >> 16;
                    ivec2 _4964 = ivec2(_4957, (_4954.y << 16) >> 16);
                    ivec2 _4968 = ivec2(_4960, (_4953.y << 16) >> 16);
                    ivec2 _4973;
                    if (_4957 < 0)
                    {
                        ivec2 _4972 = _4964;
                        _4972.x = 0;
                        _4973 = _4972;
                    }
                    else
                    {
                        _4973 = _4964;
                    }
                    ivec2 _4984;
                    ivec2 _4985;
                    if (_4960 < 0)
                    {
                        ivec2 _4982;
                        if (_4973.x >= _4960)
                        {
                            ivec2 _4981 = _4973;
                            _4981.x = 1023;
                            _4982 = _4981;
                        }
                        else
                        {
                            _4982 = _4973;
                        }
                        ivec2 _4983 = _4968;
                        _4983.x = 0;
                        _4984 = _4983;
                        _4985 = _4982;
                    }
                    else
                    {
                        _4984 = _4968;
                        _4985 = _4973;
                    }
                    ivec2 _4995;
                    ivec2 _4996;
                    if (!_213)
                    {
                        ivec2 _4991 = _4985;
                        _4991.x = _4985.x >> 1;
                        ivec2 _4994 = _4984;
                        _4994.x = _4984.x >> 1;
                        _4995 = _4994;
                        _4996 = _4991;
                    }
                    else
                    {
                        _4995 = _4984;
                        _4996 = _4985;
                    }
                    ivec2 _4997 = ivec2(_4858);
                    bool _5004;
                    if (all(greaterThanEqual(_4997, _4996)))
                    {
                        _5004 = all(lessThanEqual(_4997, _4995));
                    }
                    else
                    {
                        _5004 = false;
                    }
                    bool _5005 = _5004 != (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.windowParams.base.window0Invert != 0u);
                    bool _5012;
                    if (_4821)
                    {
                        _5012 = _4821 ? _5005 : false;
                    }
                    else
                    {
                        _5012 = (!_4821) ? _5005 : true;
                    }
                    _5013 = _5012;
                }
                else
                {
                    _5013 = _4821;
                }
                bool _5125;
                if (_4824)
                {
                    ivec2 _5025 = ivec2(g_layerRenderParams._m0[0u].windows[1].start);
                    ivec2 _5026 = ivec2(g_layerRenderParams._m0[0u].windows[1].end);
                    ivec2 _5065;
                    ivec2 _5066;
                    if (g_layerRenderParams._m0[0u].windows[1].lineWindowTableEnable != 0u)
                    {
                        uint _5031 = g_layerRenderParams._m0[0u].windows[1].lineWindowTableAddress + (_4858.y * 4u);
                        uint _5040 = (g_vram._m0[(_5031 & 524284u) >> 2u] >> (((_5031 & 2u) * 8u) & 31u)) & 65535u;
                        ivec2 _5047 = _5025;
                        _5047.x = int(((_5040 >> 8u) & 255u) | ((_5040 << 8u) & 65280u));
                        uint _5048 = _5031 + 2u;
                        uint _5057 = (g_vram._m0[(_5048 & 524284u) >> 2u] >> (((_5048 & 2u) * 8u) & 31u)) & 65535u;
                        ivec2 _5064 = _5026;
                        _5064.x = int(((_5057 >> 8u) & 255u) | ((_5057 << 8u) & 65280u));
                        _5065 = _5064;
                        _5066 = _5047;
                    }
                    else
                    {
                        _5065 = _5026;
                        _5066 = _5025;
                    }
                    int _5069 = (_5066.x << 16) >> 16;
                    int _5072 = (_5065.x << 16) >> 16;
                    ivec2 _5076 = ivec2(_5069, (_5066.y << 16) >> 16);
                    ivec2 _5080 = ivec2(_5072, (_5065.y << 16) >> 16);
                    ivec2 _5085;
                    if (_5069 < 0)
                    {
                        ivec2 _5084 = _5076;
                        _5084.x = 0;
                        _5085 = _5084;
                    }
                    else
                    {
                        _5085 = _5076;
                    }
                    ivec2 _5096;
                    ivec2 _5097;
                    if (_5072 < 0)
                    {
                        ivec2 _5094;
                        if (_5085.x >= _5072)
                        {
                            ivec2 _5093 = _5085;
                            _5093.x = 1023;
                            _5094 = _5093;
                        }
                        else
                        {
                            _5094 = _5085;
                        }
                        ivec2 _5095 = _5080;
                        _5095.x = 0;
                        _5096 = _5095;
                        _5097 = _5094;
                    }
                    else
                    {
                        _5096 = _5080;
                        _5097 = _5085;
                    }
                    ivec2 _5107;
                    ivec2 _5108;
                    if (!_213)
                    {
                        ivec2 _5103 = _5097;
                        _5103.x = _5097.x >> 1;
                        ivec2 _5106 = _5096;
                        _5106.x = _5096.x >> 1;
                        _5107 = _5106;
                        _5108 = _5103;
                    }
                    else
                    {
                        _5107 = _5096;
                        _5108 = _5097;
                    }
                    ivec2 _5109 = ivec2(_4858);
                    bool _5116;
                    if (all(greaterThanEqual(_5109, _5108)))
                    {
                        _5116 = all(lessThanEqual(_5109, _5107));
                    }
                    else
                    {
                        _5116 = false;
                    }
                    bool _5117 = _5116 != (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.windowParams.base.window1Invert != 0u);
                    bool _5124;
                    if (_4821)
                    {
                        _5124 = _5013 ? _5117 : false;
                    }
                    else
                    {
                        _5124 = (!_5013) ? _5117 : true;
                    }
                    _5125 = _5124;
                }
                else
                {
                    _5125 = _5013;
                }
                bool _5145;
                if (_4826)
                {
                    bool _5137 = (((texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(_4858, 0u)), int(0u)).x >> 6u) & 1u) != 0u) != (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.windowParams.spriteWindowInvert != 0u);
                    bool _5144;
                    if (_4821)
                    {
                        _5144 = _5125 ? _5137 : false;
                    }
                    else
                    {
                        _5144 = (!_5125) ? _5137 : true;
                    }
                    _5145 = _5144;
                }
                else
                {
                    _5145 = _5125;
                }
                _5146 = _5145;
                break;
            } while(false);
            if (_5146)
            {
                _6471 = uvec4(0u);
                break;
            }
            bool _5156;
            if (gl_GlobalInvocationID.z <= 1u)
            {
                _5156 = (!((!_4829) ? _4830 : true)) ? _4831 : true;
            }
            else
            {
                _5156 = false;
            }
            uvec2 _5247;
            uvec2 _5248;
            uvec2 _5249;
            if (_5156)
            {
                uint _5159 = _4829 ? 4u : 0u;
                uint _5165;
                if (_4830)
                {
                    uint _5162 = 8u;
                    uint _5163 = 4u;
                    _5165 = _4829 ? 8u : 4u;
                }
                else
                {
                    _5165 = _5159;
                }
                uint _5170;
                if (_4831)
                {
                    _5170 = _5165 + 4u;
                }
                else
                {
                    _5170 = _5165;
                }
                uint _5173 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].lineScrollInterval & 31u;
                uint _5176 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].lineScrollTableAddress + ((_4858.y >> _5173) * _5170);
                uvec2 _5197;
                if (_4829)
                {
                    uint _5180 = (_5176 & 524284u) >> 2u;
                    uvec2 _5196 = uvec2(0u);
                    _5196.x = ((((((g_vram._m0[_5180] >> 24u) & 255u) | ((g_vram._m0[_5180] >> 8u) & 65280u)) | ((g_vram._m0[_5180] << 8u) & 16711680u)) | ((g_vram._m0[_5180] << 24u) & 4278190080u)) >> 8u) & 524287u;
                    _5197 = _5196;
                }
                else
                {
                    _5197 = uvec2(0u);
                }
                uvec2 _5223;
                uvec2 _5224;
                if (_4830)
                {
                    uint _5202 = ((_5176 + (_4830 ? _5159 : 0u)) & 524284u) >> 2u;
                    uvec2 _5218 = _5197;
                    _5218.y = ((((((g_vram._m0[_5202] >> 24u) & 255u) | ((g_vram._m0[_5202] >> 8u) & 65280u)) | ((g_vram._m0[_5202] << 8u) & 16711680u)) | ((g_vram._m0[_5202] << 24u) & 4278190080u)) >> 8u) & 524287u;
                    uvec2 _5222 = _4858;
                    _5222.y = _4858.y & ((1u << _5173) - 1u);
                    _5223 = _5222;
                    _5224 = _5218;
                }
                else
                {
                    _5223 = _4858;
                    _5224 = _5197;
                }
                uvec2 _5246;
                if (_4831)
                {
                    uint _5229 = ((_5176 + (_4831 ? _5165 : 0u)) & 524284u) >> 2u;
                    uvec2 _5245 = _4794;
                    _5245.x = ((((((g_vram._m0[_5229] >> 24u) & 255u) | ((g_vram._m0[_5229] >> 8u) & 65280u)) | ((g_vram._m0[_5229] << 8u) & 16711680u)) | ((g_vram._m0[_5229] << 24u) & 4278190080u)) >> 8u) & 2047u;
                    _5246 = _5245;
                }
                else
                {
                    _5246 = _4794;
                }
                _5247 = _5246;
                _5248 = _5223;
                _5249 = _5224;
            }
            else
            {
                _5247 = _4794;
                _5248 = _4858;
                _5249 = uvec2(0u);
            }
            bool _5253;
            if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].vcellScrollEnable != 0u)
            {
                _5253 = !_4817;
            }
            else
            {
                _5253 = false;
            }
            uvec2 _5307;
            if (_5253)
            {
                int _5264 = int((_5248.x + ((_5249.x >> 8u) & 7u)) >> 3u);
                bool _5268;
                if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].vcellScrollRepeat != 0u)
                {
                    _5268 = _5264 > 0;
                }
                else
                {
                    _5268 = false;
                }
                int _5272;
                if (_5268)
                {
                    _5272 = _5264 - 1;
                }
                else
                {
                    _5272 = _5264;
                }
                int _5276;
                if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].vcellScrollDelay != 0u)
                {
                    _5276 = _5272 - 1;
                }
                else
                {
                    _5276 = _5272;
                }
                uint _5288 = ((((CommonRenderParamsBuffer.g_commonParams.vcellScroll & 524287u) + (uint(_5276) * (((CommonRenderParamsBuffer.g_commonParams.vcellScroll >> 19u) & 7u) << 2u))) + g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].vcellScrollOffset) & 524284u) >> 2u;
                uvec2 _5306 = _5249;
                _5306.y = _5249.y + (((((((g_vram._m0[_5288] >> 24u) & 255u) | ((g_vram._m0[_5288] >> 8u) & 65280u)) | ((g_vram._m0[_5288] << 8u) & 16711680u)) | ((g_vram._m0[_5288] << 24u) & 4278190080u)) >> 8u) & 524287u);
                _5307 = _5306;
            }
            else
            {
                _5307 = _5249;
            }
            uvec2 _5313 = ((_5307 + _4887) + (_5247 * (_5248 - uvec2(0u, _4888)))) >> uvec2(8u);
            uvec2 _5327;
            if (_4817)
            {
                _5327 = _5313 - (_5313 % uvec2(((CommonRenderParamsBuffer.g_commonParams.layerParams >> 14u) & 15u) + 1u, ((CommonRenderParamsBuffer.g_commonParams.layerParams >> 18u) & 15u) + 1u));
            }
            else
            {
                _5327 = _5313;
            }
            if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.bitmap != 0u)
            {
                uvec2 _6042 = _5327 & (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.bitmapSize - uvec2(1u));
                bool _6043 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.supplSpecialColorCalc != 0u;
                uvec4 _6470;
                do
                {
                    uint _6047 = _6042.x;
                    uint _6050 = _6047 + (_6042.y * g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.bitmapSize.x);
                    uvec4 _6421;
                    bool _6422;
                    uint _6423;
                    bool _6424;
                    if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 0u)
                    {
                        uint _6355 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.bitmapBaseAddress + (_6050 >> 1u);
                        uint _6357 = (_6355 >> 17u) & 3u;
                        uint _6381;
                        if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatAccess >> _6357) & 1u) != 0u)
                        {
                            uint _6367 = _6355 + (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.vramDataOffset >> _6357) & 1u) << 3u);
                            _6381 = (g_vram._m0[(_6367 & 524284u) >> 2u] >> ((((_6367 & 3u) * 8u) + (((~_6047) & 1u) * 4u)) & 31u)) & 15u;
                        }
                        else
                        {
                            _6381 = 0u;
                        }
                        uint _6384 = (_6381 >> 1u) & 7u;
                        uvec4 _6388 = texelFetch(g_cramColor, int((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cramOffset + (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.supplPalNum | _6381)) & _248));
                        bool _6392;
                        if (_4815)
                        {
                            _6392 = _6381 == 0u;
                        }
                        else
                        {
                            _6392 = false;
                        }
                        bool _6420;
                        do
                        {
                            if (!_4818)
                            {
                                _6420 = false;
                                break;
                            }
                            bool _6417;
                            bool _6418;
                            switch (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialColorCalcMode)
                            {
                                case 0u:
                                {
                                    _6417 = _4818;
                                    _6418 = true;
                                    break;
                                }
                                case 1u:
                                {
                                    _6417 = _6043;
                                    _6418 = true;
                                    break;
                                }
                                case 2u:
                                {
                                    bool _6416;
                                    if (_6043)
                                    {
                                        _6416 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + _6384) & 31u)) & 1u) != 0u;
                                    }
                                    else
                                    {
                                        _6416 = false;
                                    }
                                    _6417 = _6416;
                                    _6418 = true;
                                    break;
                                }
                                case 3u:
                                {
                                    _6417 = (_6388.w & 1u) != 0u;
                                    _6418 = true;
                                    break;
                                }
                                default:
                                {
                                    _6417 = _179;
                                    _6418 = false;
                                    break;
                                }
                            }
                            if (_6418)
                            {
                                _6420 = _6417;
                                break;
                            }
                            _6420 = false;
                            break;
                        } while(false);
                        _6421 = _6388;
                        _6422 = _6420;
                        _6423 = _6384;
                        _6424 = _6392;
                    }
                    else
                    {
                        uvec4 _6350;
                        bool _6351;
                        uint _6352;
                        bool _6353;
                        if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 1u)
                        {
                            uint _6287 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.bitmapBaseAddress + _6050;
                            uint _6289 = (_6287 >> 17u) & 3u;
                            uint _6309;
                            if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatAccess >> _6289) & 1u) != 0u)
                            {
                                uint _6299 = _6287 + (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.vramDataOffset >> _6289) & 1u) << 3u);
                                _6309 = (g_vram._m0[(_6299 & 524284u) >> 2u] >> (((_6299 & 3u) * 8u) & 31u)) & 255u;
                            }
                            else
                            {
                                _6309 = 0u;
                            }
                            uint _6313 = (_6309 >> 1u) & 7u;
                            uvec4 _6317 = texelFetch(g_cramColor, int((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cramOffset + ((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.supplPalNum & 1792u) | _6309)) & _248));
                            bool _6321;
                            if (_4815)
                            {
                                _6321 = _6309 == 0u;
                            }
                            else
                            {
                                _6321 = false;
                            }
                            bool _6349;
                            do
                            {
                                if (!_4818)
                                {
                                    _6349 = false;
                                    break;
                                }
                                bool _6346;
                                bool _6347;
                                switch (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialColorCalcMode)
                                {
                                    case 0u:
                                    {
                                        _6346 = _4818;
                                        _6347 = true;
                                        break;
                                    }
                                    case 1u:
                                    {
                                        _6346 = _6043;
                                        _6347 = true;
                                        break;
                                    }
                                    case 2u:
                                    {
                                        bool _6345;
                                        if (_6043)
                                        {
                                            _6345 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + _6313) & 31u)) & 1u) != 0u;
                                        }
                                        else
                                        {
                                            _6345 = false;
                                        }
                                        _6346 = _6345;
                                        _6347 = true;
                                        break;
                                    }
                                    case 3u:
                                    {
                                        _6346 = (_6317.w & 1u) != 0u;
                                        _6347 = true;
                                        break;
                                    }
                                    default:
                                    {
                                        _6346 = _179;
                                        _6347 = false;
                                        break;
                                    }
                                }
                                if (_6347)
                                {
                                    _6349 = _6346;
                                    break;
                                }
                                _6349 = false;
                                break;
                            } while(false);
                            _6350 = _6317;
                            _6351 = _6349;
                            _6352 = _6313;
                            _6353 = _6321;
                        }
                        else
                        {
                            uvec4 _6283;
                            bool _6284;
                            uint _6285;
                            bool _6286;
                            if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 2u)
                            {
                                uint _6216 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.bitmapBaseAddress + (_6050 << 1u);
                                uint _6218 = (_6216 >> 17u) & 3u;
                                uint _6243;
                                if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatAccess >> _6218) & 1u) != 0u)
                                {
                                    uint _6228 = _6216 + (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.vramDataOffset >> _6218) & 1u) << 3u);
                                    uint _6237 = (g_vram._m0[(_6228 & 524284u) >> 2u] >> (((_6228 & 2u) * 8u) & 31u)) & 65535u;
                                    _6243 = ((_6237 >> 8u) & 255u) | ((_6237 << 8u) & 65280u);
                                }
                                else
                                {
                                    _6243 = 0u;
                                }
                                uint _6244 = _6243 & 2047u;
                                uint _6246 = (_6243 >> 1u) & 7u;
                                uvec4 _6250 = texelFetch(g_cramColor, int((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cramOffset + _6244) & _248));
                                bool _6254;
                                if (_4815)
                                {
                                    _6254 = _6244 == 0u;
                                }
                                else
                                {
                                    _6254 = false;
                                }
                                bool _6282;
                                do
                                {
                                    if (!_4818)
                                    {
                                        _6282 = false;
                                        break;
                                    }
                                    bool _6279;
                                    bool _6280;
                                    switch (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialColorCalcMode)
                                    {
                                        case 0u:
                                        {
                                            _6279 = _4818;
                                            _6280 = true;
                                            break;
                                        }
                                        case 1u:
                                        {
                                            _6279 = _6043;
                                            _6280 = true;
                                            break;
                                        }
                                        case 2u:
                                        {
                                            bool _6278;
                                            if (_6043)
                                            {
                                                _6278 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + _6246) & 31u)) & 1u) != 0u;
                                            }
                                            else
                                            {
                                                _6278 = false;
                                            }
                                            _6279 = _6278;
                                            _6280 = true;
                                            break;
                                        }
                                        case 3u:
                                        {
                                            _6279 = (_6250.w & 1u) != 0u;
                                            _6280 = true;
                                            break;
                                        }
                                        default:
                                        {
                                            _6279 = _179;
                                            _6280 = false;
                                            break;
                                        }
                                    }
                                    if (_6280)
                                    {
                                        _6282 = _6279;
                                        break;
                                    }
                                    _6282 = false;
                                    break;
                                } while(false);
                                _6283 = _6250;
                                _6284 = _6282;
                                _6285 = _6246;
                                _6286 = _6254;
                            }
                            else
                            {
                                uvec4 _6211;
                                bool _6212;
                                uint _6213;
                                bool _6214;
                                if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 3u)
                                {
                                    uint _6143 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.bitmapBaseAddress + (_6050 << 1u);
                                    uint _6145 = (_6143 >> 17u) & 3u;
                                    uint _6170;
                                    if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatAccess >> _6145) & 1u) != 0u)
                                    {
                                        uint _6155 = _6143 + (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.vramDataOffset >> _6145) & 1u) << 3u);
                                        uint _6164 = (g_vram._m0[(_6155 & 524284u) >> 2u] >> (((_6155 & 2u) * 8u) & 31u)) & 65535u;
                                        _6170 = ((_6164 >> 8u) & 255u) | ((_6164 << 8u) & 65280u);
                                    }
                                    else
                                    {
                                        _6170 = 0u;
                                    }
                                    uint _6180 = (_6170 >> 15u) & 1u;
                                    bool _6185;
                                    if (_4815)
                                    {
                                        _6185 = _6180 == 0u;
                                    }
                                    else
                                    {
                                        _6185 = false;
                                    }
                                    bool _6210;
                                    do
                                    {
                                        if (!_4818)
                                        {
                                            _6210 = false;
                                            break;
                                        }
                                        bool _6207;
                                        bool _6208;
                                        switch (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialColorCalcMode)
                                        {
                                            case 0u:
                                            {
                                                _6207 = _4818;
                                                _6208 = true;
                                                break;
                                            }
                                            case 1u:
                                            {
                                                _6207 = _6043;
                                                _6208 = true;
                                                break;
                                            }
                                            case 2u:
                                            {
                                                bool _6206;
                                                if (_6043)
                                                {
                                                    _6206 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + 7u) & 31u)) & 1u) != 0u;
                                                }
                                                else
                                                {
                                                    _6206 = false;
                                                }
                                                _6207 = _6206;
                                                _6208 = true;
                                                break;
                                            }
                                            case 3u:
                                            {
                                                _6207 = true;
                                                _6208 = true;
                                                break;
                                            }
                                            default:
                                            {
                                                _6207 = _179;
                                                _6208 = false;
                                                break;
                                            }
                                        }
                                        if (_6208)
                                        {
                                            _6210 = _6207;
                                            break;
                                        }
                                        _6210 = false;
                                        break;
                                    } while(false);
                                    _6211 = uvec4((_6170 & 31u) << 3u, ((_6170 >> 5u) & 31u) << 3u, ((_6170 >> 10u) & 31u) << 3u, _6180);
                                    _6212 = _6210;
                                    _6213 = _178;
                                    _6214 = _6185;
                                }
                                else
                                {
                                    bool _6067 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 4u;
                                    uvec4 _6138;
                                    bool _6139;
                                    bool _6140;
                                    if (_6067)
                                    {
                                        uint _6072 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.bitmapBaseAddress + (_6050 << 2u);
                                        uint _6074 = (_6072 >> 17u) & 3u;
                                        uint _6100;
                                        if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatAccess >> _6074) & 1u) != 0u)
                                        {
                                            uint _6086 = ((_6072 + (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.vramDataOffset >> _6074) & 1u) << 3u)) & 524284u) >> 2u;
                                            _6100 = ((((g_vram._m0[_6086] >> 24u) & 255u) | ((g_vram._m0[_6086] >> 8u) & 65280u)) | ((g_vram._m0[_6086] << 8u) & 16711680u)) | ((g_vram._m0[_6086] << 24u) & 4278190080u);
                                        }
                                        else
                                        {
                                            _6100 = 0u;
                                        }
                                        uint _6107 = (_6100 >> 31u) & 1u;
                                        bool _6112;
                                        if (_4815)
                                        {
                                            _6112 = _6107 == 0u;
                                        }
                                        else
                                        {
                                            _6112 = false;
                                        }
                                        bool _6137;
                                        do
                                        {
                                            if (!_4818)
                                            {
                                                _6137 = false;
                                                break;
                                            }
                                            bool _6134;
                                            bool _6135;
                                            switch (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialColorCalcMode)
                                            {
                                                case 0u:
                                                {
                                                    _6134 = _4818;
                                                    _6135 = true;
                                                    break;
                                                }
                                                case 1u:
                                                {
                                                    _6134 = _6043;
                                                    _6135 = true;
                                                    break;
                                                }
                                                case 2u:
                                                {
                                                    bool _6133;
                                                    if (_6043)
                                                    {
                                                        _6133 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + 7u) & 31u)) & 1u) != 0u;
                                                    }
                                                    else
                                                    {
                                                        _6133 = false;
                                                    }
                                                    _6134 = _6133;
                                                    _6135 = true;
                                                    break;
                                                }
                                                case 3u:
                                                {
                                                    _6134 = true;
                                                    _6135 = true;
                                                    break;
                                                }
                                                default:
                                                {
                                                    _6134 = _179;
                                                    _6135 = false;
                                                    break;
                                                }
                                            }
                                            if (_6135)
                                            {
                                                _6137 = _6134;
                                                break;
                                            }
                                            _6137 = false;
                                            break;
                                        } while(false);
                                        _6138 = uvec4(_6100 & 255u, (_6100 >> 8u) & 255u, (_6100 >> 16u) & 255u, _6107);
                                        _6139 = _6137;
                                        _6140 = _6112;
                                    }
                                    else
                                    {
                                        _6138 = uvec4(0u);
                                        _6139 = false;
                                        _6140 = true;
                                    }
                                    _6211 = _6138;
                                    _6212 = _6139;
                                    _6213 = _6067 ? _178 : 0u;
                                    _6214 = _6140;
                                }
                                _6283 = _6211;
                                _6284 = _6212;
                                _6285 = _6213;
                                _6286 = _6214;
                            }
                            _6350 = _6283;
                            _6351 = _6284;
                            _6352 = _6285;
                            _6353 = _6286;
                        }
                        _6421 = _6350;
                        _6422 = _6351;
                        _6423 = _6352;
                        _6424 = _6353;
                    }
                    if (_6424)
                    {
                        _6470 = uvec4(0u);
                        break;
                    }
                    uint _6456;
                    if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.priorityMode == 1u)
                    {
                        _6456 = (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.priorityNumber & 4294967294u) | g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.supplSpecialPriority;
                    }
                    else
                    {
                        uint _6453;
                        if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.priorityMode == 2u)
                        {
                            uint _6434 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.priorityNumber & 4294967294u;
                            bool _6439;
                            if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.supplSpecialPriority != 0u)
                            {
                                _6439 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat < 3u;
                            }
                            else
                            {
                                _6439 = false;
                            }
                            uint _6452;
                            if (_6439)
                            {
                                _6452 = _6434 | uint(((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + _6423) & 31u)) & 1u) != 0u);
                            }
                            else
                            {
                                _6452 = _6434;
                            }
                            _6453 = _6452;
                        }
                        else
                        {
                            _6453 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.priorityNumber;
                        }
                        _6456 = _6453;
                    }
                    bool _6457 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat <= 2u;
                    uint _6459 = 64u;
                    uint _6460 = 0u;
                    _6470 = uvec4(_6421.xyz, ((_6457 ? 64u : 0u) | uint(_6422 ? 128 : 0)) | _6456);
                    break;
                } while(false);
                _6471 = _6470;
                break;
            }
            else
            {
                uvec2 _5332 = (uvec2(9u) + g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.pageShift) & uvec2(31u);
                uvec2 _5334 = (_5327 >> _5332) & uvec2(1u);
                uvec2 _5377;
                if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatDelay >> ((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].pageBaseAddresses[_5334.x | (_5334.y << 1u)] >> 17u) & 3u)) & 1u) != 0u)
                {
                    uvec2 _5376;
                    if (_5248.x >= (8u - (((_5307.x + _4887.x) >> 8u) & 7u)))
                    {
                        uvec2 _5375 = _5327;
                        _5375.x = _5327.x - 8u;
                        _5376 = _5375;
                    }
                    else
                    {
                        uint _5360 = _5327.x + _180[(CommonRenderParamsBuffer.g_commonParams.displayParams >> 10u) & 3u];
                        uvec2 _5372;
                        if (_5248.y >= 8u)
                        {
                            _5372 = uvec2(_5360, _5327.y - 8u);
                        }
                        else
                        {
                            _5372 = uvec2(_5360, _5327.y + _230);
                        }
                        _5376 = _5372;
                    }
                    _5377 = _5376;
                }
                else
                {
                    _5377 = _5327;
                }
                uint _200[16];
                for (int _5379 = 0; _5379 < 4; )
                {
                    _200[_5379] = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].pageBaseAddresses[_5379];
                    _5379++;
                    continue;
                }
                uint _201[16] = _200;
                uvec2 _5392 = (_5377 >> _5332) & uvec2(1u);
                uint _5396 = _5392.x | (_5392.y << 1u);
                uvec2 _5409;
                if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.vramDataOffset >> ((_201[_5396] >> 17u) & 3u)) & 1u) != 0u)
                {
                    uvec2 _5408 = _5377;
                    _5408.x = _5377.x + 8u;
                    _5409 = _5408;
                }
                else
                {
                    _5409 = _5377;
                }
                uvec2 _5411 = (_5409 >> uvec2(9u)) & g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.pageShift;
                uint _5418 = _201[_5396] + ((_5411.x + (_5411.y << 1u)) << (_184[g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cellSizeShift][int(_4820)] & 31u));
                uvec2 _5419 = _5409 >> uvec2(3u);
                uvec2 _5420 = _5419 & uvec2(63u);
                uvec2 _5426;
                if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cellSizeShift != 0u)
                {
                    _5426 = _5420 >> uvec2(1u);
                }
                else
                {
                    _5426 = _5420;
                }
                uint _5432 = _5426.x + (_5426.y << ((6u - g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cellSizeShift) & 31u));
                uvec2 _5434 = _5419 & uvec2(g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cellSizeShift);
                uint _5438 = _5434.x + (_5434.y << 1u);
                uvec2 _5439 = _5409 & uvec2(7u);
                uint _5572;
                uint _5573;
                bool _5574;
                bool _5575;
                bool _5576;
                bool _5577;
                if (_4820)
                {
                    uint _5566;
                    uint _5567;
                    bool _5568;
                    bool _5569;
                    bool _5570;
                    bool _5571;
                    do
                    {
                        uint _5527 = _5418 + (_5432 * 4u);
                        if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.patNameAccess >> ((_5527 >> 17u) & 3u)) & 1u) == 0u)
                        {
                            _5566 = 0u;
                            _5567 = 0u;
                            _5568 = false;
                            _5569 = false;
                            _5570 = false;
                            _5571 = false;
                            break;
                        }
                        uint _5536 = (_5527 & 524284u) >> 2u;
                        uint _5549 = ((((g_vram._m0[_5536] >> 24u) & 255u) | ((g_vram._m0[_5536] >> 8u) & 65280u)) | ((g_vram._m0[_5536] << 8u) & 16711680u)) | ((g_vram._m0[_5536] << 24u) & 4278190080u);
                        _5566 = _5549 & 32767u;
                        _5567 = ((_5549 >> 16u) & 127u) << 4u;
                        _5568 = ((_5549 >> 28u) & 1u) != 0u;
                        _5569 = ((_5549 >> 29u) & 1u) != 0u;
                        _5570 = ((_5549 >> 30u) & 1u) != 0u;
                        _5571 = ((_5549 >> 31u) & 1u) != 0u;
                        break;
                    } while(false);
                    _5572 = _5566;
                    _5573 = _5567;
                    _5574 = _5568;
                    _5575 = _5569;
                    _5576 = _5570;
                    _5577 = _5571;
                }
                else
                {
                    uint _5518;
                    uint _5519;
                    bool _5520;
                    bool _5521;
                    bool _5522;
                    bool _5523;
                    do
                    {
                        uint _5446 = _5418 + (_5432 * 2u);
                        if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.patNameAccess >> ((_5446 >> 17u) & 3u)) & 1u) == 0u)
                        {
                            _5518 = 0u;
                            _5519 = 0u;
                            _5520 = false;
                            _5521 = false;
                            _5522 = false;
                            _5523 = false;
                            break;
                        }
                        uint _5462 = (g_vram._m0[(_5446 & 524284u) >> 2u] >> (((_5446 & 2u) * 8u) & 31u)) & 65535u;
                        uint _5467 = ((_5462 >> 8u) & 255u) | ((_5462 << 8u) & 65280u);
                        uint _5470 = 2u * g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cellSizeShift;
                        uint _5473 = _5470 + uint(_4819 ? 2 : 0);
                        uint _5474 = _5473 & 31u;
                        uint _5484 = ((_5467 & uint(_4819 ? 4095 : 1023)) << (_5470 & 31u)) | (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.supplScrollCharNum >> _5474) & (31u >> _5474)) << ((10u + _5473) & 31u));
                        uint _5490;
                        if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cellSizeShift > 0u)
                        {
                            _5490 = _5484 | (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.supplScrollCharNum & 3u);
                        }
                        else
                        {
                            _5490 = _5484;
                        }
                        uint _5502;
                        if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat != 0u)
                        {
                            _5502 = ((_5467 >> 12u) & 7u) << 8u;
                        }
                        else
                        {
                            _5502 = (((_5467 >> 12u) & 15u) | g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.supplPalNum) << 4u;
                        }
                        bool _5505 = !_4819;
                        bool _5511;
                        if (_5505)
                        {
                            _5511 = ((_5467 >> 10u) & 1u) != 0u;
                        }
                        else
                        {
                            _5511 = false;
                        }
                        bool _5517;
                        if (_5505)
                        {
                            _5517 = ((_5467 >> 11u) & 1u) != 0u;
                        }
                        else
                        {
                            _5517 = false;
                        }
                        _5518 = _5490;
                        _5519 = _5502;
                        _5520 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.supplSpecialColorCalc != 0u;
                        _5521 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.supplSpecialPriority != 0u;
                        _5522 = _5511;
                        _5523 = _5517;
                        break;
                    } while(false);
                    _5572 = _5518;
                    _5573 = _5519;
                    _5574 = _5520;
                    _5575 = _5521;
                    _5576 = _5522;
                    _5577 = _5523;
                }
                bool _5588;
                if (_4820 ? (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatDelay >> (((_5572 << 5u) >> 17u) & 3u)) & 1u) != 0u) : false)
                {
                    _5588 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cellSizeShift > 0u;
                }
                else
                {
                    _5588 = false;
                }
                uint _5592;
                if (_5588)
                {
                    _5592 = _5438 ^ 1u;
                }
                else
                {
                    _5592 = _5438;
                }
                uvec2 _5603;
                uint _5604;
                if (_5576)
                {
                    uvec2 _5597 = _5439;
                    _5597.x = _5439.x ^ 7u;
                    uint _5602;
                    if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cellSizeShift > 0u)
                    {
                        _5602 = _5592 ^ 1u;
                    }
                    else
                    {
                        _5602 = _5592;
                    }
                    _5603 = _5597;
                    _5604 = _5602;
                }
                else
                {
                    _5603 = _5439;
                    _5604 = _5592;
                }
                uvec2 _5615;
                uint _5616;
                if (_5577)
                {
                    uvec2 _5609 = _5603;
                    _5609.y = _5603.y ^ 7u;
                    uint _5614;
                    if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cellSizeShift > 0u)
                    {
                        _5614 = _5604 ^ 2u;
                    }
                    else
                    {
                        _5614 = _5604;
                    }
                    _5615 = _5609;
                    _5616 = _5614;
                }
                else
                {
                    _5615 = _5603;
                    _5616 = _5604;
                }
                bool _5617 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 4u;
                uint _5633;
                if (_5617)
                {
                    _5633 = _5616 << 3u;
                }
                else
                {
                    uint _5631;
                    if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 3u)
                    {
                        _5631 = _5616 << 2u;
                    }
                    else
                    {
                        uint _5629;
                        if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat != 0u)
                        {
                            _5629 = _5616 << 1u;
                        }
                        else
                        {
                            _5629 = _5616;
                        }
                        _5631 = _5629;
                    }
                    _5633 = _5631;
                }
                uint _5635 = (_5572 + _5633) << 5u;
                uvec4 _6040;
                do
                {
                    uint _5642 = _5615.x + (_5615.y * 8u);
                    uvec4 _5992;
                    bool _5993;
                    uint _5994;
                    bool _5995;
                    if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 0u)
                    {
                        uint _5930 = _5635 + (_5642 >> 1u);
                        uint _5952;
                        if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatAccess >> ((_5930 >> 17u) & 3u)) & 1u) != 0u)
                        {
                            _5952 = (g_vram._m0[(_5930 & 524284u) >> 2u] >> ((((_5930 & 3u) * 8u) + (((~_5615.x) & 1u) * 4u)) & 31u)) & 15u;
                        }
                        else
                        {
                            _5952 = 0u;
                        }
                        uint _5955 = (_5952 >> 1u) & 7u;
                        uvec4 _5959 = texelFetch(g_cramColor, int((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cramOffset + (_5573 | _5952)) & _248));
                        bool _5963;
                        if (_4815)
                        {
                            _5963 = _5952 == 0u;
                        }
                        else
                        {
                            _5963 = false;
                        }
                        bool _5991;
                        do
                        {
                            if (!_4818)
                            {
                                _5991 = false;
                                break;
                            }
                            bool _5988;
                            bool _5989;
                            switch (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialColorCalcMode)
                            {
                                case 0u:
                                {
                                    _5988 = _4818;
                                    _5989 = true;
                                    break;
                                }
                                case 1u:
                                {
                                    _5988 = _5574;
                                    _5989 = true;
                                    break;
                                }
                                case 2u:
                                {
                                    bool _5987;
                                    if (_5574)
                                    {
                                        _5987 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + _5955) & 31u)) & 1u) != 0u;
                                    }
                                    else
                                    {
                                        _5987 = false;
                                    }
                                    _5988 = _5987;
                                    _5989 = true;
                                    break;
                                }
                                case 3u:
                                {
                                    _5988 = (_5959.w & 1u) != 0u;
                                    _5989 = true;
                                    break;
                                }
                                default:
                                {
                                    _5988 = _179;
                                    _5989 = false;
                                    break;
                                }
                            }
                            if (_5989)
                            {
                                _5991 = _5988;
                                break;
                            }
                            _5991 = false;
                            break;
                        } while(false);
                        _5992 = _5959;
                        _5993 = _5991;
                        _5994 = _5955;
                        _5995 = _5963;
                    }
                    else
                    {
                        uvec4 _5925;
                        bool _5926;
                        uint _5927;
                        bool _5928;
                        if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 1u)
                        {
                            uint _5866 = _5635 + _5642;
                            uint _5884;
                            if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatAccess >> ((_5866 >> 17u) & 3u)) & 1u) != 0u)
                            {
                                _5884 = (g_vram._m0[(_5866 & 524284u) >> 2u] >> (((_5866 & 3u) * 8u) & 31u)) & 255u;
                            }
                            else
                            {
                                _5884 = 0u;
                            }
                            uint _5888 = (_5884 >> 1u) & 7u;
                            uvec4 _5892 = texelFetch(g_cramColor, int((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cramOffset + ((_5573 & 1792u) | _5884)) & _248));
                            bool _5896;
                            if (_4815)
                            {
                                _5896 = _5884 == 0u;
                            }
                            else
                            {
                                _5896 = false;
                            }
                            bool _5924;
                            do
                            {
                                if (!_4818)
                                {
                                    _5924 = false;
                                    break;
                                }
                                bool _5921;
                                bool _5922;
                                switch (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialColorCalcMode)
                                {
                                    case 0u:
                                    {
                                        _5921 = _4818;
                                        _5922 = true;
                                        break;
                                    }
                                    case 1u:
                                    {
                                        _5921 = _5574;
                                        _5922 = true;
                                        break;
                                    }
                                    case 2u:
                                    {
                                        bool _5920;
                                        if (_5574)
                                        {
                                            _5920 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + _5888) & 31u)) & 1u) != 0u;
                                        }
                                        else
                                        {
                                            _5920 = false;
                                        }
                                        _5921 = _5920;
                                        _5922 = true;
                                        break;
                                    }
                                    case 3u:
                                    {
                                        _5921 = (_5892.w & 1u) != 0u;
                                        _5922 = true;
                                        break;
                                    }
                                    default:
                                    {
                                        _5921 = _179;
                                        _5922 = false;
                                        break;
                                    }
                                }
                                if (_5922)
                                {
                                    _5924 = _5921;
                                    break;
                                }
                                _5924 = false;
                                break;
                            } while(false);
                            _5925 = _5892;
                            _5926 = _5924;
                            _5927 = _5888;
                            _5928 = _5896;
                        }
                        else
                        {
                            uvec4 _5862;
                            bool _5863;
                            uint _5864;
                            bool _5865;
                            if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 2u)
                            {
                                uint _5799 = _5635 + (_5642 << 1u);
                                uint _5822;
                                if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatAccess >> ((_5799 >> 17u) & 3u)) & 1u) != 0u)
                                {
                                    uint _5816 = (g_vram._m0[(_5799 & 524284u) >> 2u] >> (((_5799 & 2u) * 8u) & 31u)) & 65535u;
                                    _5822 = ((_5816 >> 8u) & 255u) | ((_5816 << 8u) & 65280u);
                                }
                                else
                                {
                                    _5822 = 0u;
                                }
                                uint _5823 = _5822 & 2047u;
                                uint _5825 = (_5822 >> 1u) & 7u;
                                uvec4 _5829 = texelFetch(g_cramColor, int((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.cramOffset + _5823) & _248));
                                bool _5833;
                                if (_4815)
                                {
                                    _5833 = _5823 == 0u;
                                }
                                else
                                {
                                    _5833 = false;
                                }
                                bool _5861;
                                do
                                {
                                    if (!_4818)
                                    {
                                        _5861 = false;
                                        break;
                                    }
                                    bool _5858;
                                    bool _5859;
                                    switch (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialColorCalcMode)
                                    {
                                        case 0u:
                                        {
                                            _5858 = _4818;
                                            _5859 = true;
                                            break;
                                        }
                                        case 1u:
                                        {
                                            _5858 = _5574;
                                            _5859 = true;
                                            break;
                                        }
                                        case 2u:
                                        {
                                            bool _5857;
                                            if (_5574)
                                            {
                                                _5857 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + _5825) & 31u)) & 1u) != 0u;
                                            }
                                            else
                                            {
                                                _5857 = false;
                                            }
                                            _5858 = _5857;
                                            _5859 = true;
                                            break;
                                        }
                                        case 3u:
                                        {
                                            _5858 = (_5829.w & 1u) != 0u;
                                            _5859 = true;
                                            break;
                                        }
                                        default:
                                        {
                                            _5858 = _179;
                                            _5859 = false;
                                            break;
                                        }
                                    }
                                    if (_5859)
                                    {
                                        _5861 = _5858;
                                        break;
                                    }
                                    _5861 = false;
                                    break;
                                } while(false);
                                _5862 = _5829;
                                _5863 = _5861;
                                _5864 = _5825;
                                _5865 = _5833;
                            }
                            else
                            {
                                uvec4 _5794;
                                bool _5795;
                                uint _5796;
                                bool _5797;
                                if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat == 3u)
                                {
                                    uint _5730 = _5635 + (_5642 << 1u);
                                    uint _5753;
                                    if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatAccess >> ((_5730 >> 17u) & 3u)) & 1u) != 0u)
                                    {
                                        uint _5747 = (g_vram._m0[(_5730 & 524284u) >> 2u] >> (((_5730 & 2u) * 8u) & 31u)) & 65535u;
                                        _5753 = ((_5747 >> 8u) & 255u) | ((_5747 << 8u) & 65280u);
                                    }
                                    else
                                    {
                                        _5753 = 0u;
                                    }
                                    uint _5763 = (_5753 >> 15u) & 1u;
                                    bool _5768;
                                    if (_4815)
                                    {
                                        _5768 = _5763 == 0u;
                                    }
                                    else
                                    {
                                        _5768 = false;
                                    }
                                    bool _5793;
                                    do
                                    {
                                        if (!_4818)
                                        {
                                            _5793 = false;
                                            break;
                                        }
                                        bool _5790;
                                        bool _5791;
                                        switch (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialColorCalcMode)
                                        {
                                            case 0u:
                                            {
                                                _5790 = _4818;
                                                _5791 = true;
                                                break;
                                            }
                                            case 1u:
                                            {
                                                _5790 = _5574;
                                                _5791 = true;
                                                break;
                                            }
                                            case 2u:
                                            {
                                                bool _5789;
                                                if (_5574)
                                                {
                                                    _5789 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + 7u) & 31u)) & 1u) != 0u;
                                                }
                                                else
                                                {
                                                    _5789 = false;
                                                }
                                                _5790 = _5789;
                                                _5791 = true;
                                                break;
                                            }
                                            case 3u:
                                            {
                                                _5790 = true;
                                                _5791 = true;
                                                break;
                                            }
                                            default:
                                            {
                                                _5790 = _179;
                                                _5791 = false;
                                                break;
                                            }
                                        }
                                        if (_5791)
                                        {
                                            _5793 = _5790;
                                            break;
                                        }
                                        _5793 = false;
                                        break;
                                    } while(false);
                                    _5794 = uvec4((_5753 & 31u) << 3u, ((_5753 >> 5u) & 31u) << 3u, ((_5753 >> 10u) & 31u) << 3u, _5763);
                                    _5795 = _5793;
                                    _5796 = _178;
                                    _5797 = _5768;
                                }
                                else
                                {
                                    uvec4 _5725;
                                    bool _5726;
                                    bool _5727;
                                    if (_5617)
                                    {
                                        uint _5663 = _5635 + (_5642 << 2u);
                                        uint _5687;
                                        if (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.charPatAccess >> ((_5663 >> 17u) & 3u)) & 1u) != 0u)
                                        {
                                            uint _5673 = (_5663 & 524284u) >> 2u;
                                            _5687 = ((((g_vram._m0[_5673] >> 24u) & 255u) | ((g_vram._m0[_5673] >> 8u) & 65280u)) | ((g_vram._m0[_5673] << 8u) & 16711680u)) | ((g_vram._m0[_5673] << 24u) & 4278190080u);
                                        }
                                        else
                                        {
                                            _5687 = 0u;
                                        }
                                        uint _5694 = (_5687 >> 31u) & 1u;
                                        bool _5699;
                                        if (_4815)
                                        {
                                            _5699 = _5694 == 0u;
                                        }
                                        else
                                        {
                                            _5699 = false;
                                        }
                                        bool _5724;
                                        do
                                        {
                                            if (!_4818)
                                            {
                                                _5724 = false;
                                                break;
                                            }
                                            bool _5721;
                                            bool _5722;
                                            switch (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialColorCalcMode)
                                            {
                                                case 0u:
                                                {
                                                    _5721 = _4818;
                                                    _5722 = true;
                                                    break;
                                                }
                                                case 1u:
                                                {
                                                    _5721 = _5574;
                                                    _5722 = true;
                                                    break;
                                                }
                                                case 2u:
                                                {
                                                    bool _5720;
                                                    if (_5574)
                                                    {
                                                        _5720 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + 7u) & 31u)) & 1u) != 0u;
                                                    }
                                                    else
                                                    {
                                                        _5720 = false;
                                                    }
                                                    _5721 = _5720;
                                                    _5722 = true;
                                                    break;
                                                }
                                                case 3u:
                                                {
                                                    _5721 = true;
                                                    _5722 = true;
                                                    break;
                                                }
                                                default:
                                                {
                                                    _5721 = _179;
                                                    _5722 = false;
                                                    break;
                                                }
                                            }
                                            if (_5722)
                                            {
                                                _5724 = _5721;
                                                break;
                                            }
                                            _5724 = false;
                                            break;
                                        } while(false);
                                        _5725 = uvec4(_5687 & 255u, (_5687 >> 8u) & 255u, (_5687 >> 16u) & 255u, _5694);
                                        _5726 = _5724;
                                        _5727 = _5699;
                                    }
                                    else
                                    {
                                        _5725 = uvec4(0u);
                                        _5726 = false;
                                        _5727 = true;
                                    }
                                    _5794 = _5725;
                                    _5795 = _5726;
                                    _5796 = _5617 ? _178 : 0u;
                                    _5797 = _5727;
                                }
                                _5862 = _5794;
                                _5863 = _5795;
                                _5864 = _5796;
                                _5865 = _5797;
                            }
                            _5925 = _5862;
                            _5926 = _5863;
                            _5927 = _5864;
                            _5928 = _5865;
                        }
                        _5992 = _5925;
                        _5993 = _5926;
                        _5994 = _5927;
                        _5995 = _5928;
                    }
                    if (_5995)
                    {
                        _6040 = uvec4(0u);
                        break;
                    }
                    uint _6026;
                    if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.priorityMode == 1u)
                    {
                        _6026 = (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.priorityNumber & 4294967294u) | uint(_5575);
                    }
                    else
                    {
                        uint _6023;
                        if (g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.priorityMode == 2u)
                        {
                            uint _6005 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.priorityNumber & 4294967294u;
                            bool _6009;
                            if (_5575)
                            {
                                _6009 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat < 3u;
                            }
                            else
                            {
                                _6009 = false;
                            }
                            uint _6022;
                            if (_6009)
                            {
                                _6022 = _6005 | uint(((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.specialFunctionSelect * 8u) + _5994) & 31u)) & 1u) != 0u);
                            }
                            else
                            {
                                _6022 = _6005;
                            }
                            _6023 = _6022;
                        }
                        else
                        {
                            _6023 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.priorityNumber;
                        }
                        _6026 = _6023;
                    }
                    bool _6027 = g_layerRenderParams._m0[0u].nbg[gl_GlobalInvocationID.z].base.colorFormat <= 2u;
                    uint _6029 = 64u;
                    uint _6030 = 0u;
                    _6040 = uvec4(_5992.xyz, ((_6027 ? 64u : 0u) | uint(_5993 ? 128 : 0)) | _6026);
                    break;
                } while(false);
                _6471 = _6040;
                break;
            }
            break; // unreachable workaround
        } while(false);
        imageStore(g_layerOut, ivec3(_274), _6471);
    }
    else
    {
        if (gl_GlobalInvocationID.z <= 5u)
        {
            uint _579 = gl_GlobalInvocationID.z - 4u;
            uvec4 _4723;
            do
            {
                bool _601 = g_layerRenderParams._m0[0u].rbg[_579].base.windowParams.base.windowLogicAnd != 0u;
                bool _602 = g_layerRenderParams._m0[0u].rbg[_579].base.windowParams.base.window0Enable != 0u;
                bool _604 = g_layerRenderParams._m0[0u].rbg[_579].base.windowParams.base.window1Enable != 0u;
                bool _606 = g_layerRenderParams._m0[0u].rbg[_579].base.windowParams.spriteWindowEnable != 0u;
                if (g_layerRenderParams._m0[0u].rbg[_579].base.enabled == 0u)
                {
                    _4723 = uvec4(0u);
                    break;
                }
                uint _622;
                do
                {
                    bool _616;
                    if (_261)
                    {
                        _616 = !_210;
                    }
                    else
                    {
                        _616 = false;
                    }
                    if (_616)
                    {
                        _622 = (_255 << 1u) | _207;
                        break;
                    }
                    else
                    {
                        _622 = _255;
                        break;
                    }
                    break; // unreachable workaround
                } while(false);
                uvec2 _623 = uvec2(gl_GlobalInvocationID.x, _622);
                bool _877;
                do
                {
                    bool _630;
                    if (!_602)
                    {
                        _630 = !_604;
                    }
                    else
                    {
                        _630 = false;
                    }
                    bool _634;
                    if (_630)
                    {
                        _634 = !_606;
                    }
                    else
                    {
                        _634 = false;
                    }
                    if (_634)
                    {
                        _877 = false;
                        break;
                    }
                    bool _747;
                    if (_602)
                    {
                        ivec2 _648 = ivec2(g_layerRenderParams._m0[0u].windows[0].start);
                        ivec2 _649 = ivec2(g_layerRenderParams._m0[0u].windows[0].end);
                        ivec2 _687;
                        ivec2 _688;
                        if (g_layerRenderParams._m0[0u].windows[0].lineWindowTableEnable != 0u)
                        {
                            uint _653 = g_layerRenderParams._m0[0u].windows[0].lineWindowTableAddress + (_622 * 4u);
                            uint _662 = (g_vram._m0[(_653 & 524284u) >> 2u] >> (((_653 & 2u) * 8u) & 31u)) & 65535u;
                            ivec2 _669 = _648;
                            _669.x = int(((_662 >> 8u) & 255u) | ((_662 << 8u) & 65280u));
                            uint _670 = _653 + 2u;
                            uint _679 = (g_vram._m0[(_670 & 524284u) >> 2u] >> (((_670 & 2u) * 8u) & 31u)) & 65535u;
                            ivec2 _686 = _649;
                            _686.x = int(((_679 >> 8u) & 255u) | ((_679 << 8u) & 65280u));
                            _687 = _686;
                            _688 = _669;
                        }
                        else
                        {
                            _687 = _649;
                            _688 = _648;
                        }
                        int _691 = (_688.x << 16) >> 16;
                        int _694 = (_687.x << 16) >> 16;
                        ivec2 _698 = ivec2(_691, (_688.y << 16) >> 16);
                        ivec2 _702 = ivec2(_694, (_687.y << 16) >> 16);
                        ivec2 _707;
                        if (_691 < 0)
                        {
                            ivec2 _706 = _698;
                            _706.x = 0;
                            _707 = _706;
                        }
                        else
                        {
                            _707 = _698;
                        }
                        ivec2 _718;
                        ivec2 _719;
                        if (_694 < 0)
                        {
                            ivec2 _716;
                            if (_707.x >= _694)
                            {
                                ivec2 _715 = _707;
                                _715.x = 1023;
                                _716 = _715;
                            }
                            else
                            {
                                _716 = _707;
                            }
                            ivec2 _717 = _702;
                            _717.x = 0;
                            _718 = _717;
                            _719 = _716;
                        }
                        else
                        {
                            _718 = _702;
                            _719 = _707;
                        }
                        ivec2 _729;
                        ivec2 _730;
                        if (!_213)
                        {
                            ivec2 _725 = _719;
                            _725.x = _719.x >> 1;
                            ivec2 _728 = _718;
                            _728.x = _718.x >> 1;
                            _729 = _728;
                            _730 = _725;
                        }
                        else
                        {
                            _729 = _718;
                            _730 = _719;
                        }
                        ivec2 _731 = ivec2(_623);
                        bool _738;
                        if (all(greaterThanEqual(_731, _730)))
                        {
                            _738 = all(lessThanEqual(_731, _729));
                        }
                        else
                        {
                            _738 = false;
                        }
                        bool _739 = _738 != (g_layerRenderParams._m0[0u].rbg[_579].base.windowParams.base.window0Invert != 0u);
                        bool _746;
                        if (_601)
                        {
                            _746 = _601 ? _739 : false;
                        }
                        else
                        {
                            _746 = (!_601) ? _739 : true;
                        }
                        _747 = _746;
                    }
                    else
                    {
                        _747 = _601;
                    }
                    bool _858;
                    if (_604)
                    {
                        ivec2 _759 = ivec2(g_layerRenderParams._m0[0u].windows[1].start);
                        ivec2 _760 = ivec2(g_layerRenderParams._m0[0u].windows[1].end);
                        ivec2 _798;
                        ivec2 _799;
                        if (g_layerRenderParams._m0[0u].windows[1].lineWindowTableEnable != 0u)
                        {
                            uint _764 = g_layerRenderParams._m0[0u].windows[1].lineWindowTableAddress + (_622 * 4u);
                            uint _773 = (g_vram._m0[(_764 & 524284u) >> 2u] >> (((_764 & 2u) * 8u) & 31u)) & 65535u;
                            ivec2 _780 = _759;
                            _780.x = int(((_773 >> 8u) & 255u) | ((_773 << 8u) & 65280u));
                            uint _781 = _764 + 2u;
                            uint _790 = (g_vram._m0[(_781 & 524284u) >> 2u] >> (((_781 & 2u) * 8u) & 31u)) & 65535u;
                            ivec2 _797 = _760;
                            _797.x = int(((_790 >> 8u) & 255u) | ((_790 << 8u) & 65280u));
                            _798 = _797;
                            _799 = _780;
                        }
                        else
                        {
                            _798 = _760;
                            _799 = _759;
                        }
                        int _802 = (_799.x << 16) >> 16;
                        int _805 = (_798.x << 16) >> 16;
                        ivec2 _809 = ivec2(_802, (_799.y << 16) >> 16);
                        ivec2 _813 = ivec2(_805, (_798.y << 16) >> 16);
                        ivec2 _818;
                        if (_802 < 0)
                        {
                            ivec2 _817 = _809;
                            _817.x = 0;
                            _818 = _817;
                        }
                        else
                        {
                            _818 = _809;
                        }
                        ivec2 _829;
                        ivec2 _830;
                        if (_805 < 0)
                        {
                            ivec2 _827;
                            if (_818.x >= _805)
                            {
                                ivec2 _826 = _818;
                                _826.x = 1023;
                                _827 = _826;
                            }
                            else
                            {
                                _827 = _818;
                            }
                            ivec2 _828 = _813;
                            _828.x = 0;
                            _829 = _828;
                            _830 = _827;
                        }
                        else
                        {
                            _829 = _813;
                            _830 = _818;
                        }
                        ivec2 _840;
                        ivec2 _841;
                        if (!_213)
                        {
                            ivec2 _836 = _830;
                            _836.x = _830.x >> 1;
                            ivec2 _839 = _829;
                            _839.x = _829.x >> 1;
                            _840 = _839;
                            _841 = _836;
                        }
                        else
                        {
                            _840 = _829;
                            _841 = _830;
                        }
                        ivec2 _842 = ivec2(_623);
                        bool _849;
                        if (all(greaterThanEqual(_842, _841)))
                        {
                            _849 = all(lessThanEqual(_842, _840));
                        }
                        else
                        {
                            _849 = false;
                        }
                        bool _850 = _849 != (g_layerRenderParams._m0[0u].rbg[_579].base.windowParams.base.window1Invert != 0u);
                        bool _857;
                        if (_601)
                        {
                            _857 = _747 ? _850 : false;
                        }
                        else
                        {
                            _857 = (!_747) ? _850 : true;
                        }
                        _858 = _857;
                    }
                    else
                    {
                        _858 = _747;
                    }
                    bool _876;
                    if (_606)
                    {
                        bool _868 = (((texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(gl_GlobalInvocationID.x, _622, 0u)), int(0u)).x >> 6u) & 1u) != 0u) != (g_layerRenderParams._m0[0u].rbg[_579].base.windowParams.spriteWindowInvert != 0u);
                        bool _875;
                        if (_601)
                        {
                            _875 = _858 ? _868 : false;
                        }
                        else
                        {
                            _875 = (!_858) ? _868 : true;
                        }
                        _876 = _875;
                    }
                    else
                    {
                        _876 = _858;
                    }
                    _877 = _876;
                    break;
                } while(false);
                if (_877)
                {
                    _4723 = uvec4(0u);
                    break;
                }
                uvec2 _884;
                if (_213)
                {
                    uvec2 _883 = _256;
                    _883.x = gl_GlobalInvocationID.x >> 1u;
                    _884 = _883;
                }
                else
                {
                    _884 = _256;
                }
                uvec2 _891;
                if (_243 ? _259 : false)
                {
                    uvec2 _890 = _884;
                    _890.y = _884.y >> 1u;
                    _891 = _890;
                }
                else
                {
                    _891 = _884;
                }
                uint _1321;
                if (_579 == 0u)
                {
                    uint _1320;
                    do
                    {
                        uint _1317;
                        bool _1318;
                        switch ((CommonRenderParamsBuffer.g_commonParams.layerParams >> 22u) & 3u)
                        {
                            case 0u:
                            {
                                _1317 = 0u;
                                _1318 = true;
                                break;
                            }
                            case 1u:
                            {
                                _1317 = 1u;
                                _1318 = true;
                                break;
                            }
                            case 2u:
                            {
                                uint _1171 = (CommonRenderParamsBuffer.g_commonParams.rotParams >> 6u) & 1u;
                                if (_1171 == 0u)
                                {
                                    _1317 = 0u;
                                    _1318 = true;
                                    break;
                                }
                                bool _1315;
                                if (_1171 != 0u)
                                {
                                    uint _1186 = ((g_rotParamBases._m0[_891.y].tableAddress + 92u) & 524284u) >> 2u;
                                    uint _1210;
                                    if (_239)
                                    {
                                        _1210 = _891.x * uint((int((((((g_vram._m0[_1186] >> 24u) & 255u) | ((g_vram._m0[_1186] >> 8u) & 65280u)) | ((g_vram._m0[_1186] << 8u) & 16711680u)) | ((g_vram._m0[_1186] << 24u) & 4278190080u)) >> 6u) << 12) >> 12);
                                    }
                                    else
                                    {
                                        _1210 = 0u;
                                    }
                                    uint _1213 = (CommonRenderParamsBuffer.g_commonParams.rotParams >> 7u) & 1u;
                                    bool _1314;
                                    do
                                    {
                                        uint _1216 = (g_rotParamBases._m0[_891.y].KA + _1210) >> 10u;
                                        bool _1231;
                                        do
                                        {
                                            if (_234)
                                            {
                                                _1231 = true;
                                                break;
                                            }
                                            if (!_239)
                                            {
                                                _1231 = true;
                                                break;
                                            }
                                            _1231 = ((_236 >> ((((_1216 * 4u) >> _1213) >> 17u) & 3u)) & 1u) != 0u;
                                            break;
                                        } while(false);
                                        if (!_1231)
                                        {
                                            _1314 = true;
                                            break;
                                        }
                                        bool _1313;
                                        if (_1213 == 1u)
                                        {
                                            uint _1277 = _1216 * 2u;
                                            uint _1309;
                                            if (_234)
                                            {
                                                uint _1303 = (g_cramRotCoeff_1._m0[(_1277 & 2044u) >> 2u] >> (((_1277 & 2u) * 8u) & 31u)) & 65535u;
                                                _1309 = ((_1303 >> 8u) & 255u) | ((_1303 << 8u) & 65280u);
                                            }
                                            else
                                            {
                                                uint _1289 = (g_vram._m0[(_1277 & 524284u) >> 2u] >> (((_1277 & 2u) * 8u) & 31u)) & 65535u;
                                                _1309 = ((_1289 >> 8u) & 255u) | ((_1289 << 8u) & 65280u);
                                            }
                                            _1313 = ((_1309 >> 15u) & 1u) != 0u;
                                        }
                                        else
                                        {
                                            uint _1239 = _1216 * 4u;
                                            uint _1273;
                                            if (_234)
                                            {
                                                uint _1259 = (_1239 & 2044u) >> 2u;
                                                _1273 = ((((g_cramRotCoeff_1._m0[_1259] >> 24u) & 255u) | ((g_cramRotCoeff_1._m0[_1259] >> 8u) & 65280u)) | ((g_cramRotCoeff_1._m0[_1259] << 8u) & 16711680u)) | ((g_cramRotCoeff_1._m0[_1259] << 24u) & 4278190080u);
                                            }
                                            else
                                            {
                                                uint _1244 = (_1239 & 524284u) >> 2u;
                                                _1273 = ((((g_vram._m0[_1244] >> 24u) & 255u) | ((g_vram._m0[_1244] >> 8u) & 65280u)) | ((g_vram._m0[_1244] << 8u) & 16711680u)) | ((g_vram._m0[_1244] << 24u) & 4278190080u);
                                            }
                                            _1313 = ((_1273 >> 31u) & 1u) != 0u;
                                        }
                                        _1314 = _1313;
                                        break;
                                    } while(false);
                                    _1315 = _1314;
                                }
                                else
                                {
                                    _1315 = true;
                                }
                                _1317 = uint(_1315);
                                _1318 = true;
                                break;
                            }
                            case 3u:
                            {
                                bool _917 = g_layerRenderParams._m0[0u].rotWindows.windowLogicAnd != 0u;
                                bool _918 = g_layerRenderParams._m0[0u].rotWindows.window0Enable != 0u;
                                bool _920 = g_layerRenderParams._m0[0u].rotWindows.window1Enable != 0u;
                                uint _935;
                                do
                                {
                                    bool _929;
                                    if (_261)
                                    {
                                        _929 = !_210;
                                    }
                                    else
                                    {
                                        _929 = false;
                                    }
                                    if (_929)
                                    {
                                        _935 = (_891.y << 1u) | _207;
                                        break;
                                    }
                                    else
                                    {
                                        _935 = _891.y;
                                        break;
                                    }
                                    break; // unreachable workaround
                                } while(false);
                                uvec2 _936 = uvec2(_891.x, _935);
                                bool _1168;
                                do
                                {
                                    bool _943;
                                    if (!_918)
                                    {
                                        _943 = !_920;
                                    }
                                    else
                                    {
                                        _943 = false;
                                    }
                                    if (_943)
                                    {
                                        _1168 = false;
                                        break;
                                    }
                                    bool _1056;
                                    if (_918)
                                    {
                                        ivec2 _957 = ivec2(g_layerRenderParams._m0[0u].windows[0].start);
                                        ivec2 _958 = ivec2(g_layerRenderParams._m0[0u].windows[0].end);
                                        ivec2 _996;
                                        ivec2 _997;
                                        if (g_layerRenderParams._m0[0u].windows[0].lineWindowTableEnable != 0u)
                                        {
                                            uint _962 = g_layerRenderParams._m0[0u].windows[0].lineWindowTableAddress + (_935 * 4u);
                                            uint _971 = (g_vram._m0[(_962 & 524284u) >> 2u] >> (((_962 & 2u) * 8u) & 31u)) & 65535u;
                                            ivec2 _978 = _957;
                                            _978.x = int(((_971 >> 8u) & 255u) | ((_971 << 8u) & 65280u));
                                            uint _979 = _962 + 2u;
                                            uint _988 = (g_vram._m0[(_979 & 524284u) >> 2u] >> (((_979 & 2u) * 8u) & 31u)) & 65535u;
                                            ivec2 _995 = _958;
                                            _995.x = int(((_988 >> 8u) & 255u) | ((_988 << 8u) & 65280u));
                                            _996 = _995;
                                            _997 = _978;
                                        }
                                        else
                                        {
                                            _996 = _958;
                                            _997 = _957;
                                        }
                                        int _1000 = (_997.x << 16) >> 16;
                                        int _1003 = (_996.x << 16) >> 16;
                                        ivec2 _1007 = ivec2(_1000, (_997.y << 16) >> 16);
                                        ivec2 _1011 = ivec2(_1003, (_996.y << 16) >> 16);
                                        ivec2 _1016;
                                        if (_1000 < 0)
                                        {
                                            ivec2 _1015 = _1007;
                                            _1015.x = 0;
                                            _1016 = _1015;
                                        }
                                        else
                                        {
                                            _1016 = _1007;
                                        }
                                        ivec2 _1027;
                                        ivec2 _1028;
                                        if (_1003 < 0)
                                        {
                                            ivec2 _1025;
                                            if (_1016.x >= _1003)
                                            {
                                                ivec2 _1024 = _1016;
                                                _1024.x = 1023;
                                                _1025 = _1024;
                                            }
                                            else
                                            {
                                                _1025 = _1016;
                                            }
                                            ivec2 _1026 = _1011;
                                            _1026.x = 0;
                                            _1027 = _1026;
                                            _1028 = _1025;
                                        }
                                        else
                                        {
                                            _1027 = _1011;
                                            _1028 = _1016;
                                        }
                                        ivec2 _1038;
                                        ivec2 _1039;
                                        if (!_213)
                                        {
                                            ivec2 _1034 = _1028;
                                            _1034.x = _1028.x >> 1;
                                            ivec2 _1037 = _1027;
                                            _1037.x = _1027.x >> 1;
                                            _1038 = _1037;
                                            _1039 = _1034;
                                        }
                                        else
                                        {
                                            _1038 = _1027;
                                            _1039 = _1028;
                                        }
                                        ivec2 _1040 = ivec2(_936);
                                        bool _1047;
                                        if (all(greaterThanEqual(_1040, _1039)))
                                        {
                                            _1047 = all(lessThanEqual(_1040, _1038));
                                        }
                                        else
                                        {
                                            _1047 = false;
                                        }
                                        bool _1048 = _1047 != (g_layerRenderParams._m0[0u].rotWindows.window0Invert != 0u);
                                        bool _1055;
                                        if (_917)
                                        {
                                            _1055 = _917 ? _1048 : false;
                                        }
                                        else
                                        {
                                            _1055 = (!_917) ? _1048 : true;
                                        }
                                        _1056 = _1055;
                                    }
                                    else
                                    {
                                        _1056 = _917;
                                    }
                                    bool _1167;
                                    if (_920)
                                    {
                                        ivec2 _1068 = ivec2(g_layerRenderParams._m0[0u].windows[1].start);
                                        ivec2 _1069 = ivec2(g_layerRenderParams._m0[0u].windows[1].end);
                                        ivec2 _1107;
                                        ivec2 _1108;
                                        if (g_layerRenderParams._m0[0u].windows[1].lineWindowTableEnable != 0u)
                                        {
                                            uint _1073 = g_layerRenderParams._m0[0u].windows[1].lineWindowTableAddress + (_935 * 4u);
                                            uint _1082 = (g_vram._m0[(_1073 & 524284u) >> 2u] >> (((_1073 & 2u) * 8u) & 31u)) & 65535u;
                                            ivec2 _1089 = _1068;
                                            _1089.x = int(((_1082 >> 8u) & 255u) | ((_1082 << 8u) & 65280u));
                                            uint _1090 = _1073 + 2u;
                                            uint _1099 = (g_vram._m0[(_1090 & 524284u) >> 2u] >> (((_1090 & 2u) * 8u) & 31u)) & 65535u;
                                            ivec2 _1106 = _1069;
                                            _1106.x = int(((_1099 >> 8u) & 255u) | ((_1099 << 8u) & 65280u));
                                            _1107 = _1106;
                                            _1108 = _1089;
                                        }
                                        else
                                        {
                                            _1107 = _1069;
                                            _1108 = _1068;
                                        }
                                        int _1111 = (_1108.x << 16) >> 16;
                                        int _1114 = (_1107.x << 16) >> 16;
                                        ivec2 _1118 = ivec2(_1111, (_1108.y << 16) >> 16);
                                        ivec2 _1122 = ivec2(_1114, (_1107.y << 16) >> 16);
                                        ivec2 _1127;
                                        if (_1111 < 0)
                                        {
                                            ivec2 _1126 = _1118;
                                            _1126.x = 0;
                                            _1127 = _1126;
                                        }
                                        else
                                        {
                                            _1127 = _1118;
                                        }
                                        ivec2 _1138;
                                        ivec2 _1139;
                                        if (_1114 < 0)
                                        {
                                            ivec2 _1136;
                                            if (_1127.x >= _1114)
                                            {
                                                ivec2 _1135 = _1127;
                                                _1135.x = 1023;
                                                _1136 = _1135;
                                            }
                                            else
                                            {
                                                _1136 = _1127;
                                            }
                                            ivec2 _1137 = _1122;
                                            _1137.x = 0;
                                            _1138 = _1137;
                                            _1139 = _1136;
                                        }
                                        else
                                        {
                                            _1138 = _1122;
                                            _1139 = _1127;
                                        }
                                        ivec2 _1149;
                                        ivec2 _1150;
                                        if (!_213)
                                        {
                                            ivec2 _1145 = _1139;
                                            _1145.x = _1139.x >> 1;
                                            ivec2 _1148 = _1138;
                                            _1148.x = _1138.x >> 1;
                                            _1149 = _1148;
                                            _1150 = _1145;
                                        }
                                        else
                                        {
                                            _1149 = _1138;
                                            _1150 = _1139;
                                        }
                                        ivec2 _1151 = ivec2(_936);
                                        bool _1158;
                                        if (all(greaterThanEqual(_1151, _1150)))
                                        {
                                            _1158 = all(lessThanEqual(_1151, _1149));
                                        }
                                        else
                                        {
                                            _1158 = false;
                                        }
                                        bool _1159 = _1158 != (g_layerRenderParams._m0[0u].rotWindows.window1Invert != 0u);
                                        bool _1166;
                                        if (_917)
                                        {
                                            _1166 = _1056 ? _1159 : false;
                                        }
                                        else
                                        {
                                            _1166 = (!_1056) ? _1159 : true;
                                        }
                                        _1167 = _1166;
                                    }
                                    else
                                    {
                                        _1167 = _1056;
                                    }
                                    _1168 = _1167;
                                    break;
                                } while(false);
                                _1317 = uint(_1168);
                                _1318 = true;
                                break;
                            }
                            default:
                            {
                                _1317 = _178;
                                _1318 = false;
                                break;
                            }
                        }
                        if (_1318)
                        {
                            _1320 = _1317;
                            break;
                        }
                        _1320 = 0u;
                        break;
                    } while(false);
                    _1321 = _1320;
                }
                else
                {
                    _1321 = 1u;
                }
                uint _1324 = (_1321 * 256u) + _891.y;
                uint _1329 = _1321 * 5u;
                bool _1334 = ((CommonRenderParamsBuffer.g_commonParams.rotParams >> ((6u + _1329) & 31u)) & 1u) != 0u;
                bool _1471;
                if (_1334)
                {
                    uint _1340 = ((g_rotParamBases._m0[_1324].tableAddress + 92u) & 524284u) >> 2u;
                    uint _1364;
                    if (_239)
                    {
                        _1364 = _891.x * uint((int((((((g_vram._m0[_1340] >> 24u) & 255u) | ((g_vram._m0[_1340] >> 8u) & 65280u)) | ((g_vram._m0[_1340] << 8u) & 16711680u)) | ((g_vram._m0[_1340] << 24u) & 4278190080u)) >> 6u) << 12) >> 12);
                    }
                    else
                    {
                        _1364 = 0u;
                    }
                    uint _1369 = (CommonRenderParamsBuffer.g_commonParams.rotParams >> ((_1329 + 7u) & 31u)) & 1u;
                    bool _1470;
                    do
                    {
                        uint _1372 = (g_rotParamBases._m0[_1324].KA + _1364) >> 10u;
                        bool _1387;
                        do
                        {
                            if (_234)
                            {
                                _1387 = true;
                                break;
                            }
                            if (!_239)
                            {
                                _1387 = true;
                                break;
                            }
                            _1387 = ((_236 >> ((((_1372 * 4u) >> _1369) >> 17u) & 3u)) & 1u) != 0u;
                            break;
                        } while(false);
                        if (!_1387)
                        {
                            _1470 = true;
                            break;
                        }
                        bool _1469;
                        if (_1369 == 1u)
                        {
                            uint _1433 = _1372 * 2u;
                            uint _1465;
                            if (_234)
                            {
                                uint _1459 = (g_cramRotCoeff_1._m0[(_1433 & 2044u) >> 2u] >> (((_1433 & 2u) * 8u) & 31u)) & 65535u;
                                _1465 = ((_1459 >> 8u) & 255u) | ((_1459 << 8u) & 65280u);
                            }
                            else
                            {
                                uint _1445 = (g_vram._m0[(_1433 & 524284u) >> 2u] >> (((_1433 & 2u) * 8u) & 31u)) & 65535u;
                                _1465 = ((_1445 >> 8u) & 255u) | ((_1445 << 8u) & 65280u);
                            }
                            _1469 = ((_1465 >> 15u) & 1u) != 0u;
                        }
                        else
                        {
                            uint _1395 = _1372 * 4u;
                            uint _1429;
                            if (_234)
                            {
                                uint _1415 = (_1395 & 2044u) >> 2u;
                                _1429 = ((((g_cramRotCoeff_1._m0[_1415] >> 24u) & 255u) | ((g_cramRotCoeff_1._m0[_1415] >> 8u) & 65280u)) | ((g_cramRotCoeff_1._m0[_1415] << 8u) & 16711680u)) | ((g_cramRotCoeff_1._m0[_1415] << 24u) & 4278190080u);
                            }
                            else
                            {
                                uint _1400 = (_1395 & 524284u) >> 2u;
                                _1429 = ((((g_vram._m0[_1400] >> 24u) & 255u) | ((g_vram._m0[_1400] >> 8u) & 65280u)) | ((g_vram._m0[_1400] << 8u) & 16711680u)) | ((g_vram._m0[_1400] << 24u) & 4278190080u);
                            }
                            _1469 = ((_1429 >> 31u) & 1u) != 0u;
                        }
                        _1470 = _1469;
                        break;
                    } while(false);
                    _1471 = _1470;
                }
                else
                {
                    _1471 = true;
                }
                if (_1334 ? _1471 : false)
                {
                    _4723 = uvec4(0u);
                    break;
                }
                uint _1484 = (CommonRenderParamsBuffer.g_commonParams.rotParams >> ((_1329 + 7u) & 31u)) & 1u;
                uint _1488 = (CommonRenderParamsBuffer.g_commonParams.rotParams >> ((_1329 + 8u) & 31u)) & 3u;
                uint _1491 = ((g_rotParamBases._m0[_1324].tableAddress + 8u) & 524284u) >> 2u;
                uint _1511 = ((g_rotParamBases._m0[_1324].tableAddress + 20u) & 524284u) >> 2u;
                int _1528 = (int((((((g_vram._m0[_1511] >> 24u) & 255u) | ((g_vram._m0[_1511] >> 8u) & 65280u)) | ((g_vram._m0[_1511] << 8u) & 16711680u)) | ((g_vram._m0[_1511] << 24u) & 4278190080u)) >> 6u) << 19) >> 19;
                uint _1531 = ((g_rotParamBases._m0[_1324].tableAddress + 24u) & 524284u) >> 2u;
                int _1548 = (int((((((g_vram._m0[_1531] >> 24u) & 255u) | ((g_vram._m0[_1531] >> 8u) & 65280u)) | ((g_vram._m0[_1531] << 8u) & 16711680u)) | ((g_vram._m0[_1531] << 24u) & 4278190080u)) >> 6u) << 19) >> 19;
                uint _1551 = ((g_rotParamBases._m0[_1324].tableAddress + 28u) & 524284u) >> 2u;
                int _1568 = (int((((((g_vram._m0[_1551] >> 24u) & 255u) | ((g_vram._m0[_1551] >> 8u) & 65280u)) | ((g_vram._m0[_1551] << 8u) & 16711680u)) | ((g_vram._m0[_1551] << 24u) & 4278190080u)) >> 6u) << 18) >> 18;
                uint _1571 = ((g_rotParamBases._m0[_1324].tableAddress + 32u) & 524284u) >> 2u;
                int _1588 = (int((((((g_vram._m0[_1571] >> 24u) & 255u) | ((g_vram._m0[_1571] >> 8u) & 65280u)) | ((g_vram._m0[_1571] << 8u) & 16711680u)) | ((g_vram._m0[_1571] << 24u) & 4278190080u)) >> 6u) << 18) >> 18;
                uint _1591 = ((g_rotParamBases._m0[_1324].tableAddress + 36u) & 524284u) >> 2u;
                int _1608 = (int((((((g_vram._m0[_1591] >> 24u) & 255u) | ((g_vram._m0[_1591] >> 8u) & 65280u)) | ((g_vram._m0[_1591] << 8u) & 16711680u)) | ((g_vram._m0[_1591] << 24u) & 4278190080u)) >> 6u) << 18) >> 18;
                uint _1611 = ((g_rotParamBases._m0[_1324].tableAddress + 40u) & 524284u) >> 2u;
                int _1628 = (int((((((g_vram._m0[_1611] >> 24u) & 255u) | ((g_vram._m0[_1611] >> 8u) & 65280u)) | ((g_vram._m0[_1611] << 8u) & 16711680u)) | ((g_vram._m0[_1611] << 24u) & 4278190080u)) >> 6u) << 18) >> 18;
                uint _1631 = ((g_rotParamBases._m0[_1324].tableAddress + 44u) & 524284u) >> 2u;
                int _1648 = (int((((((g_vram._m0[_1631] >> 24u) & 255u) | ((g_vram._m0[_1631] >> 8u) & 65280u)) | ((g_vram._m0[_1631] << 8u) & 16711680u)) | ((g_vram._m0[_1631] << 24u) & 4278190080u)) >> 6u) << 18) >> 18;
                uint _1651 = ((g_rotParamBases._m0[_1324].tableAddress + 48u) & 524284u) >> 2u;
                int _1668 = (int((((((g_vram._m0[_1651] >> 24u) & 255u) | ((g_vram._m0[_1651] >> 8u) & 65280u)) | ((g_vram._m0[_1651] << 8u) & 16711680u)) | ((g_vram._m0[_1651] << 24u) & 4278190080u)) >> 6u) << 18) >> 18;
                uint _1676 = ((g_rotParamBases._m0[_1324].tableAddress & 2u) * 8u) & 31u;
                uint _1678 = (g_vram._m0[((g_rotParamBases._m0[_1324].tableAddress + 52u) & 524284u) >> 2u] >> _1676) & 65535u;
                int _1686 = (int(((_1678 >> 8u) & 255u) | ((_1678 << 8u) & 65280u)) << 18) >> 18;
                uint _1687 = g_rotParamBases._m0[_1324].tableAddress + 54u;
                uint _1696 = (g_vram._m0[(_1687 & 524284u) >> 2u] >> (((_1687 & 2u) * 8u) & 31u)) & 65535u;
                int _1704 = (int(((_1696 >> 8u) & 255u) | ((_1696 << 8u) & 65280u)) << 18) >> 18;
                uint _1711 = (g_vram._m0[((g_rotParamBases._m0[_1324].tableAddress + 56u) & 524284u) >> 2u] >> _1676) & 65535u;
                int _1719 = (int(((_1711 >> 8u) & 255u) | ((_1711 << 8u) & 65280u)) << 18) >> 18;
                uint _1726 = (g_vram._m0[((g_rotParamBases._m0[_1324].tableAddress + 60u) & 524284u) >> 2u] >> _1676) & 65535u;
                int _1734 = (int(((_1726 >> 8u) & 255u) | ((_1726 << 8u) & 65280u)) << 18) >> 18;
                uint _1735 = g_rotParamBases._m0[_1324].tableAddress + 62u;
                uint _1744 = (g_vram._m0[(_1735 & 524284u) >> 2u] >> (((_1735 & 2u) * 8u) & 31u)) & 65535u;
                int _1752 = (int(((_1744 >> 8u) & 255u) | ((_1744 << 8u) & 65280u)) << 18) >> 18;
                uint _1759 = (g_vram._m0[((g_rotParamBases._m0[_1324].tableAddress + 64u) & 524284u) >> 2u] >> _1676) & 65535u;
                uint _1770 = ((g_rotParamBases._m0[_1324].tableAddress + 68u) & 524284u) >> 2u;
                uint _1790 = ((g_rotParamBases._m0[_1324].tableAddress + 72u) & 524284u) >> 2u;
                uint _1810 = ((g_rotParamBases._m0[_1324].tableAddress + 76u) & 524284u) >> 2u;
                int _1826 = (int(((((g_vram._m0[_1810] >> 24u) & 255u) | ((g_vram._m0[_1810] >> 8u) & 65280u)) | ((g_vram._m0[_1810] << 8u) & 16711680u)) | ((g_vram._m0[_1810] << 24u) & 4278190080u)) << 8) >> 8;
                uint _1829 = ((g_rotParamBases._m0[_1324].tableAddress + 80u) & 524284u) >> 2u;
                int _1845 = (int(((((g_vram._m0[_1829] >> 24u) & 255u) | ((g_vram._m0[_1829] >> 8u) & 65280u)) | ((g_vram._m0[_1829] << 8u) & 16711680u)) | ((g_vram._m0[_1829] << 24u) & 4278190080u)) << 8) >> 8;
                uint _1848 = ((g_rotParamBases._m0[_1324].tableAddress + 92u) & 524284u) >> 2u;
                int _1867 = g_rotParamBases._m0[_1324].Xst - (_1686 << 10);
                int _1869 = g_rotParamBases._m0[_1324].Yst - (_1704 << 10);
                int _1871 = ((int((((((g_vram._m0[_1491] >> 24u) & 255u) | ((g_vram._m0[_1491] >> 8u) & 65280u)) | ((g_vram._m0[_1491] << 8u) & 16711680u)) | ((g_vram._m0[_1491] << 24u) & 4278190080u)) >> 6u) << 9) >> 9) - (_1719 << 10);
                uint _1873 = uint(abs(_1568));
                uint _1875 = uint(abs(_1867));
                uint _1876 = _1873 & 65535u;
                uint _1877 = _1873 >> 16u;
                uint _1878 = _1875 & 65535u;
                uint _1879 = _1875 >> 16u;
                uint _1880 = _1876 * _1878;
                uint _1881 = _1876 * _1879;
                uint _1882 = _1877 * _1878;
                uint _1888 = ((_1880 >> 16u) + (_1881 & 65535u)) + (_1882 & 65535u);
                uint _1891 = (_1880 & 65535u) | (_1888 << 16u);
                uint _1897 = (((_1877 * _1879) + (_1881 >> 16u)) + (_1882 >> 16u)) + (_1888 >> 16u);
                bool _1900 = _1867 < 0;
                uvec2 _1912;
                if ((_1568 < 0) != _1900)
                {
                    uint _1906 = (~_1891) + 1u;
                    _1912 = uvec2(_1906, (~_1897) + uint(_1906 == 0u));
                }
                else
                {
                    _1912 = uvec2(_1891, _1897);
                }
                uint _1914 = uint(abs(_1588));
                uint _1916 = uint(abs(_1869));
                uint _1917 = _1914 & 65535u;
                uint _1918 = _1914 >> 16u;
                uint _1919 = _1916 & 65535u;
                uint _1920 = _1916 >> 16u;
                uint _1921 = _1917 * _1919;
                uint _1922 = _1917 * _1920;
                uint _1923 = _1918 * _1919;
                uint _1929 = ((_1921 >> 16u) + (_1922 & 65535u)) + (_1923 & 65535u);
                uint _1932 = (_1921 & 65535u) | (_1929 << 16u);
                uint _1938 = (((_1918 * _1920) + (_1922 >> 16u)) + (_1923 >> 16u)) + (_1929 >> 16u);
                bool _1941 = _1869 < 0;
                uvec2 _1953;
                if ((_1588 < 0) != _1941)
                {
                    uint _1947 = (~_1932) + 1u;
                    _1953 = uvec2(_1947, (~_1938) + uint(_1947 == 0u));
                }
                else
                {
                    _1953 = uvec2(_1932, _1938);
                }
                uint _1956 = _1912.x + _1953.x;
                uint _1964 = uint(abs(_1608));
                uint _1966 = uint(abs(_1871));
                uint _1967 = _1964 & 65535u;
                uint _1968 = _1964 >> 16u;
                uint _1969 = _1966 & 65535u;
                uint _1970 = _1966 >> 16u;
                uint _1971 = _1967 * _1969;
                uint _1972 = _1967 * _1970;
                uint _1973 = _1968 * _1969;
                uint _1979 = ((_1971 >> 16u) + (_1972 & 65535u)) + (_1973 & 65535u);
                uint _1982 = (_1971 & 65535u) | (_1979 << 16u);
                uint _1988 = (((_1968 * _1970) + (_1972 >> 16u)) + (_1973 >> 16u)) + (_1979 >> 16u);
                bool _1991 = _1871 < 0;
                uvec2 _2003;
                if ((_1608 < 0) != _1991)
                {
                    uint _1997 = (~_1982) + 1u;
                    _2003 = uvec2(_1997, (~_1988) + uint(_1997 == 0u));
                }
                else
                {
                    _2003 = uvec2(_1982, _1988);
                }
                uint _2005 = _1956 + _2003.x;
                uint _2015 = uint(abs(_1628));
                uint _2016 = _2015 & 65535u;
                uint _2017 = _2015 >> 16u;
                uint _2018 = _2016 * _1878;
                uint _2019 = _2016 * _1879;
                uint _2020 = _2017 * _1878;
                uint _2026 = ((_2018 >> 16u) + (_2019 & 65535u)) + (_2020 & 65535u);
                uint _2029 = (_2018 & 65535u) | (_2026 << 16u);
                uint _2035 = (((_2017 * _1879) + (_2019 >> 16u)) + (_2020 >> 16u)) + (_2026 >> 16u);
                uvec2 _2049;
                if ((_1628 < 0) != _1900)
                {
                    uint _2043 = (~_2029) + 1u;
                    _2049 = uvec2(_2043, (~_2035) + uint(_2043 == 0u));
                }
                else
                {
                    _2049 = uvec2(_2029, _2035);
                }
                uint _2051 = uint(abs(_1648));
                uint _2052 = _2051 & 65535u;
                uint _2053 = _2051 >> 16u;
                uint _2054 = _2052 * _1919;
                uint _2055 = _2052 * _1920;
                uint _2056 = _2053 * _1919;
                uint _2062 = ((_2054 >> 16u) + (_2055 & 65535u)) + (_2056 & 65535u);
                uint _2065 = (_2054 & 65535u) | (_2062 << 16u);
                uint _2071 = (((_2053 * _1920) + (_2055 >> 16u)) + (_2056 >> 16u)) + (_2062 >> 16u);
                uvec2 _2085;
                if ((_1648 < 0) != _1941)
                {
                    uint _2079 = (~_2065) + 1u;
                    _2085 = uvec2(_2079, (~_2071) + uint(_2079 == 0u));
                }
                else
                {
                    _2085 = uvec2(_2065, _2071);
                }
                uint _2088 = _2049.x + _2085.x;
                uint _2096 = uint(abs(_1668));
                uint _2097 = _2096 & 65535u;
                uint _2098 = _2096 >> 16u;
                uint _2099 = _2097 * _1969;
                uint _2100 = _2097 * _1970;
                uint _2101 = _2098 * _1969;
                uint _2107 = ((_2099 >> 16u) + (_2100 & 65535u)) + (_2101 & 65535u);
                uint _2110 = (_2099 & 65535u) | (_2107 << 16u);
                uint _2116 = (((_2098 * _1970) + (_2100 >> 16u)) + (_2101 >> 16u)) + (_2107 >> 16u);
                uvec2 _2130;
                if ((_1668 < 0) != _1991)
                {
                    uint _2124 = (~_2110) + 1u;
                    _2130 = uvec2(_2124, (~_2116) + uint(_2124 == 0u));
                }
                else
                {
                    _2130 = uvec2(_2110, _2116);
                }
                uint _2132 = _2088 + _2130.x;
                int _2141 = _1686 - _1734;
                int _2142 = _1704 - _1752;
                int _2143 = _1719 - ((int(((_1759 >> 8u) & 255u) | ((_1759 << 8u) & 65280u)) << 18) >> 18);
                int _2151 = ((((_1568 * _2141) + (_1588 * _2142)) + (_1608 * _2143)) + (_1734 << 10)) + ((int((((((g_vram._m0[_1770] >> 24u) & 255u) | ((g_vram._m0[_1770] >> 8u) & 65280u)) | ((g_vram._m0[_1770] << 8u) & 16711680u)) | ((g_vram._m0[_1770] << 24u) & 4278190080u)) >> 6u) << 8) >> 8);
                int _2159 = ((((_1628 * _2141) + (_1648 * _2142)) + (_1668 * _2143)) + (_1752 << 10)) + ((int((((((g_vram._m0[_1790] >> 24u) & 255u) | ((g_vram._m0[_1790] >> 8u) & 65280u)) | ((g_vram._m0[_1790] << 8u) & 16711680u)) | ((g_vram._m0[_1790] << 24u) & 4278190080u)) >> 6u) << 8) >> 8);
                int _2300;
                int _2301;
                int _2302;
                if (_1334)
                {
                    uint _2176;
                    if (_239)
                    {
                        _2176 = _891.x * uint((int((((((g_vram._m0[_1848] >> 24u) & 255u) | ((g_vram._m0[_1848] >> 8u) & 65280u)) | ((g_vram._m0[_1848] << 8u) & 16711680u)) | ((g_vram._m0[_1848] << 24u) & 4278190080u)) >> 6u) << 12) >> 12);
                    }
                    else
                    {
                        _2176 = 0u;
                    }
                    int _2290;
                    do
                    {
                        uint _2180 = (g_rotParamBases._m0[_1324].KA + _2176) >> 10u;
                        bool _2195;
                        do
                        {
                            if (_234)
                            {
                                _2195 = true;
                                break;
                            }
                            if (!_239)
                            {
                                _2195 = true;
                                break;
                            }
                            _2195 = ((_236 >> ((((_2180 * 4u) >> _1484) >> 17u) & 3u)) & 1u) != 0u;
                            break;
                        } while(false);
                        if (!_2195)
                        {
                            _2290 = 0;
                            break;
                        }
                        int _2289;
                        if (_1484 == 1u)
                        {
                            uint _2246 = _2180 * 2u;
                            uint _2278;
                            if (_234)
                            {
                                uint _2272 = (g_cramRotCoeff_1._m0[(_2246 & 2044u) >> 2u] >> (((_2246 & 2u) * 8u) & 31u)) & 65535u;
                                _2278 = ((_2272 >> 8u) & 255u) | ((_2272 << 8u) & 65280u);
                            }
                            else
                            {
                                uint _2258 = (g_vram._m0[(_2246 & 524284u) >> 2u] >> (((_2246 & 2u) * 8u) & 31u)) & 65535u;
                                _2278 = ((_2258 >> 8u) & 255u) | ((_2258 << 8u) & 65280u);
                            }
                            int _2281 = (int(_2278) << 17) >> 17;
                            int _2288;
                            if (_1488 == 3u)
                            {
                                _2288 = _2281 << 14;
                            }
                            else
                            {
                                _2288 = _2281 << 6;
                            }
                            _2289 = _2288;
                        }
                        else
                        {
                            uint _2203 = _2180 * 4u;
                            uint _2237;
                            if (_234)
                            {
                                uint _2223 = (_2203 & 2044u) >> 2u;
                                _2237 = ((((g_cramRotCoeff_1._m0[_2223] >> 24u) & 255u) | ((g_cramRotCoeff_1._m0[_2223] >> 8u) & 65280u)) | ((g_cramRotCoeff_1._m0[_2223] << 8u) & 16711680u)) | ((g_cramRotCoeff_1._m0[_2223] << 24u) & 4278190080u);
                            }
                            else
                            {
                                uint _2208 = (_2203 & 524284u) >> 2u;
                                _2237 = ((((g_vram._m0[_2208] >> 24u) & 255u) | ((g_vram._m0[_2208] >> 8u) & 65280u)) | ((g_vram._m0[_2208] << 8u) & 16711680u)) | ((g_vram._m0[_2208] << 24u) & 4278190080u);
                            }
                            int _2240 = (int(_2237) << 8) >> 8;
                            int _2245;
                            if (_1488 == 3u)
                            {
                                _2245 = _2240 << 8;
                            }
                            else
                            {
                                _2245 = _2240;
                            }
                            _2289 = _2245;
                        }
                        _2290 = _2289;
                        break;
                    } while(false);
                    int _2297;
                    int _2298;
                    int _2299;
                    switch (_1488)
                    {
                        case 0u:
                        {
                            _2297 = _2290;
                            _2298 = _2151;
                            _2299 = _2290;
                            break;
                        }
                        case 1u:
                        {
                            _2297 = _1845;
                            _2298 = _2151;
                            _2299 = _2290;
                            break;
                        }
                        case 2u:
                        {
                            _2297 = _2290;
                            _2298 = _2151;
                            _2299 = _1826;
                            break;
                        }
                        case 3u:
                        {
                            _2297 = _1845;
                            _2298 = _2290 << 2;
                            _2299 = _1826;
                            break;
                        }
                        default:
                        {
                            _2297 = _1845;
                            _2298 = _2151;
                            _2299 = _1826;
                            break;
                        }
                    }
                    _2300 = _2297;
                    _2301 = _2298;
                    _2302 = _2299;
                }
                else
                {
                    _2300 = _1845;
                    _2301 = _2151;
                    _2302 = _1826;
                }
                int _2307 = int(((_2005 >> 10u) | (((((_1912.y + _1953.y) + uint(_1956 < _1912.x)) + _2003.y) + uint(_2005 < _1956)) << 22u)) + (_891.x * uint(((_1568 * _1528) + (_1588 * _1548)) >> 10)));
                int _2311 = int(((_2132 >> 10u) | (((((_2049.y + _2085.y) + uint(_2088 < _2049.x)) + _2130.y) + uint(_2132 < _2088)) << 22u)) + (_891.x * uint(((_1628 * _1528) + (_1648 * _1548)) >> 10)));
                uint _2313 = uint(abs(_2302));
                uint _2315 = uint(abs(_2307));
                uint _2316 = _2313 & 65535u;
                uint _2317 = _2313 >> 16u;
                uint _2318 = _2315 & 65535u;
                uint _2319 = _2315 >> 16u;
                uint _2320 = _2316 * _2318;
                uint _2321 = _2316 * _2319;
                uint _2322 = _2317 * _2318;
                uint _2328 = ((_2320 >> 16u) + (_2321 & 65535u)) + (_2322 & 65535u);
                uint _2331 = (_2320 & 65535u) | (_2328 << 16u);
                uint _2337 = (((_2317 * _2319) + (_2321 >> 16u)) + (_2322 >> 16u)) + (_2328 >> 16u);
                uvec2 _2352;
                if ((_2302 < 0) != (_2307 < 0))
                {
                    uint _2346 = (~_2331) + 1u;
                    _2352 = uvec2(_2346, (~_2337) + uint(_2346 == 0u));
                }
                else
                {
                    _2352 = uvec2(_2331, _2337);
                }
                uint _2357 = (_2352.x >> 16u) | (_2352.y << 16u);
                uint _2364 = _2357 + uint(_2301);
                uint _2371 = (_2364 >> 10u) | (((uint(int(_2352.y) >> 16) + ((_2301 < 0) ? 4294967295u : 0u)) + uint(_2364 < _2357)) << 22u);
                uint _2373 = uint(abs(_2300));
                uint _2375 = uint(abs(_2311));
                uint _2376 = _2373 & 65535u;
                uint _2377 = _2373 >> 16u;
                uint _2378 = _2375 & 65535u;
                uint _2379 = _2375 >> 16u;
                uint _2380 = _2376 * _2378;
                uint _2381 = _2376 * _2379;
                uint _2382 = _2377 * _2378;
                uint _2388 = ((_2380 >> 16u) + (_2381 & 65535u)) + (_2382 & 65535u);
                uint _2391 = (_2380 & 65535u) | (_2388 << 16u);
                uint _2397 = (((_2377 * _2379) + (_2381 >> 16u)) + (_2382 >> 16u)) + (_2388 >> 16u);
                uvec2 _2412;
                if ((_2300 < 0) != (_2311 < 0))
                {
                    uint _2406 = (~_2391) + 1u;
                    _2412 = uvec2(_2406, (~_2397) + uint(_2406 == 0u));
                }
                else
                {
                    _2412 = uvec2(_2391, _2397);
                }
                uint _2417 = (_2412.x >> 16u) | (_2412.y << 16u);
                uint _2424 = _2417 + uint(_2159);
                uvec2 _2432 = uvec2(_2371, (_2424 >> 10u) | (((uint(int(_2412.y) >> 16) + ((_2159 < 0) ? 4294967295u : 0u)) + uint(_2424 < _2417)) << 22u));
                uvec4 _4722;
                if (g_layerRenderParams._m0[0u].rbg[_579].base.bitmap != 0u)
                {
                    uvec4 _4721;
                    do
                    {
                        uint _4046 = g_layerRenderParams._m0[0u].rbg[_579].base.enableTransparency;
                        uint _4048 = g_layerRenderParams._m0[0u].rbg[_579].base.priorityNumber;
                        uint _4050 = g_layerRenderParams._m0[0u].rbg[_579].base.priorityMode;
                        uint _4052 = g_layerRenderParams._m0[0u].rbg[_579].base.specialFunctionSelect;
                        uint _4054 = g_layerRenderParams._m0[0u].rbg[_579].base.colorFormat;
                        uint _4056 = g_layerRenderParams._m0[0u].rbg[_579].base.cramOffset;
                        uint _4058 = g_layerRenderParams._m0[0u].rbg[_579].base.supplPalNum;
                        uint _4060 = g_layerRenderParams._m0[0u].rbg[_579].base.supplSpecialColorCalc;
                        uint _4062 = g_layerRenderParams._m0[0u].rbg[_579].base.supplSpecialPriority;
                        uint _4066 = g_layerRenderParams._m0[0u].rbg[_579].base.colorCalcEnable;
                        uint _4068 = g_layerRenderParams._m0[0u].rbg[_579].base.charPatAccess;
                        uint _4070 = g_layerRenderParams._m0[0u].rbg[_579].base.vramDataOffset;
                        uint _4072 = g_layerRenderParams._m0[0u].rbg[_579].base.specialColorCalcMode;
                        uvec2 _4074 = g_layerRenderParams._m0[0u].rbg[_579].base.bitmapSize;
                        bool _4077 = _4046 != 0u;
                        bool _4079 = _4066 != 0u;
                        uvec2 _4090;
                        if (g_layerRenderParams._m0[0u].rbg[_579].base.mosaicEnable != 0u)
                        {
                            uvec2 _4089 = _891;
                            _4089.x = _891.x - (_891.x % (((CommonRenderParamsBuffer.g_commonParams.layerParams >> 14u) & 15u) + 1u));
                            _4090 = _4089;
                        }
                        else
                        {
                            _4090 = _891;
                        }
                        uvec2 _4100;
                        if (g_layerRenderParams._m0[0u].rbg[_579].screenOverProcess == 3u)
                        {
                            _4100 = uvec2(512u);
                        }
                        else
                        {
                            _4100 = uvec2(2048u) << (g_layerRenderParams._m0[0u].rbg[_1321].base.pageShift & uvec2(31u));
                        }
                        if ((!all(lessThan(_2432, _4100))) ? (g_layerRenderParams._m0[0u].rbg[_579].screenOverProcess == 0u) : true)
                        {
                            do
                            {
                                if (((CommonRenderParamsBuffer.g_commonParams.layerParams >> ((gl_GlobalInvocationID.z + 8u) & 31u)) & 1u) == 0u)
                                {
                                    break;
                                }
                                bool _4122 = ((CommonRenderParamsBuffer.g_commonParams.layerParams >> 13u) & 1u) != 0u;
                                uint _4132;
                                bool _4133;
                                switch ((CommonRenderParamsBuffer.g_commonParams.layerParams >> 22u) & 3u)
                                {
                                    case 0u:
                                    {
                                        _4132 = 0u;
                                        _4133 = _1321 == 0u;
                                        break;
                                    }
                                    case 1u:
                                    {
                                        _4132 = _4122 ? 0u : 1u;
                                        _4133 = _1321 == 1u;
                                        break;
                                    }
                                    case 2u:
                                    {
                                        _4132 = 0u;
                                        _4133 = true;
                                        break;
                                    }
                                    case 3u:
                                    {
                                        _4132 = _4122 ? 0u : _1321;
                                        _4133 = true;
                                        break;
                                    }
                                    default:
                                    {
                                        _4132 = _178;
                                        _4133 = false;
                                        break;
                                    }
                                }
                                uint _4141 = g_layerRenderParams._m0[0u].lineScreenParams.baseAddress + (((g_layerRenderParams._m0[0u].lineScreenParams.perLine != 0u) ? _891.y : 0u) * 2u);
                                uint _4150 = (g_vram._m0[(_4141 & 524284u) >> 2u] >> (((_4141 & 2u) * 8u) & 31u)) & 65535u;
                                uint _4155 = ((_4150 >> 8u) & 255u) | ((_4150 << 8u) & 65280u);
                                uint _4284;
                                if (_4133)
                                {
                                    uint _4158 = _4132 * 5u;
                                    bool _4163 = ((CommonRenderParamsBuffer.g_commonParams.rotParams >> ((6u + _4158) & 31u)) & 1u) != 0u;
                                    uint _4283;
                                    if (_4163 ? (((CommonRenderParamsBuffer.g_commonParams.rotParams >> ((_4158 + 10u) & 31u)) & 1u) != 0u) : false)
                                    {
                                        uint _4176 = (_4132 * 256u) + _4090.y;
                                        uint _4280;
                                        if (_4163)
                                        {
                                            uint _4186 = ((g_rotParamBases._m0[_4176].tableAddress + 92u) & 524284u) >> 2u;
                                            uint _4210;
                                            if (_239)
                                            {
                                                _4210 = _4090.x * uint((int((((((g_vram._m0[_4186] >> 24u) & 255u) | ((g_vram._m0[_4186] >> 8u) & 65280u)) | ((g_vram._m0[_4186] << 8u) & 16711680u)) | ((g_vram._m0[_4186] << 24u) & 4278190080u)) >> 6u) << 12) >> 12);
                                            }
                                            else
                                            {
                                                _4210 = 0u;
                                            }
                                            uint _4215 = (CommonRenderParamsBuffer.g_commonParams.rotParams >> ((_4158 + 7u) & 31u)) & 1u;
                                            uint _4279;
                                            do
                                            {
                                                uint _4218 = (g_rotParamBases._m0[_4176].KA + _4210) >> 10u;
                                                bool _4233;
                                                do
                                                {
                                                    if (_234)
                                                    {
                                                        _4233 = true;
                                                        break;
                                                    }
                                                    if (!_239)
                                                    {
                                                        _4233 = true;
                                                        break;
                                                    }
                                                    _4233 = ((_236 >> ((((_4218 * 4u) >> _4215) >> 17u) & 3u)) & 1u) != 0u;
                                                    break;
                                                } while(false);
                                                if (!_4233)
                                                {
                                                    _4279 = 0u;
                                                    break;
                                                }
                                                uint _4278;
                                                if (_4215 == 1u)
                                                {
                                                    _4278 = 0u;
                                                }
                                                else
                                                {
                                                    uint _4241 = _4218 * 4u;
                                                    uint _4275;
                                                    if (_234)
                                                    {
                                                        uint _4261 = (_4241 & 2044u) >> 2u;
                                                        _4275 = ((((g_cramRotCoeff_1._m0[_4261] >> 24u) & 255u) | ((g_cramRotCoeff_1._m0[_4261] >> 8u) & 65280u)) | ((g_cramRotCoeff_1._m0[_4261] << 8u) & 16711680u)) | ((g_cramRotCoeff_1._m0[_4261] << 24u) & 4278190080u);
                                                    }
                                                    else
                                                    {
                                                        uint _4246 = (_4241 & 524284u) >> 2u;
                                                        _4275 = ((((g_vram._m0[_4246] >> 24u) & 255u) | ((g_vram._m0[_4246] >> 8u) & 65280u)) | ((g_vram._m0[_4246] << 8u) & 16711680u)) | ((g_vram._m0[_4246] << 24u) & 4278190080u);
                                                    }
                                                    _4278 = (_4275 >> 24u) & 127u;
                                                }
                                                _4279 = _4278;
                                                break;
                                            } while(false);
                                            _4280 = _4279;
                                        }
                                        else
                                        {
                                            _4280 = 0u;
                                        }
                                        _4283 = (((_4155 >> 7u) & 15u) << 7u) | _4280;
                                    }
                                    else
                                    {
                                        _4283 = _4155;
                                    }
                                    _4284 = _4283;
                                }
                                else
                                {
                                    _4284 = _4155;
                                }
                                imageStore(g_rbgLineColorOut, ivec3(uvec3(_891, _579)), texelFetch(g_cramColor, int(_4284)));
                                break;
                            } while(false);
                            uvec2 _4292 = _2432 & (_4074 - uvec2(1u));
                            bool _4293 = _4060 != 0u;
                            uvec4 _4720;
                            do
                            {
                                uint _4297 = _4292.x;
                                uint _4300 = _4297 + (_4292.y * _4074.x);
                                uvec4 _4671;
                                bool _4672;
                                uint _4673;
                                bool _4674;
                                if (_4054 == 0u)
                                {
                                    uint _4605 = g_layerRenderParams._m0[0u].rbg[_1321].base.bitmapBaseAddress + (_4300 >> 1u);
                                    uint _4607 = (_4605 >> 17u) & 3u;
                                    uint _4631;
                                    if (((_4068 >> _4607) & 1u) != 0u)
                                    {
                                        uint _4617 = _4605 + (((_4070 >> _4607) & 1u) << 3u);
                                        _4631 = (g_vram._m0[(_4617 & 524284u) >> 2u] >> ((((_4617 & 3u) * 8u) + (((~_4297) & 1u) * 4u)) & 31u)) & 15u;
                                    }
                                    else
                                    {
                                        _4631 = 0u;
                                    }
                                    uint _4634 = (_4631 >> 1u) & 7u;
                                    uvec4 _4638 = texelFetch(g_cramColor, int((_4056 + (_4058 | _4631)) & _248));
                                    bool _4642;
                                    if (_4077)
                                    {
                                        _4642 = _4631 == 0u;
                                    }
                                    else
                                    {
                                        _4642 = false;
                                    }
                                    bool _4670;
                                    do
                                    {
                                        if (!_4079)
                                        {
                                            _4670 = false;
                                            break;
                                        }
                                        bool _4667;
                                        bool _4668;
                                        switch (_4072)
                                        {
                                            case 0u:
                                            {
                                                _4667 = _4079;
                                                _4668 = true;
                                                break;
                                            }
                                            case 1u:
                                            {
                                                _4667 = _4293;
                                                _4668 = true;
                                                break;
                                            }
                                            case 2u:
                                            {
                                                bool _4666;
                                                if (_4293)
                                                {
                                                    _4666 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_4052 * 8u) + _4634) & 31u)) & 1u) != 0u;
                                                }
                                                else
                                                {
                                                    _4666 = false;
                                                }
                                                _4667 = _4666;
                                                _4668 = true;
                                                break;
                                            }
                                            case 3u:
                                            {
                                                _4667 = (_4638.w & 1u) != 0u;
                                                _4668 = true;
                                                break;
                                            }
                                            default:
                                            {
                                                _4667 = _179;
                                                _4668 = false;
                                                break;
                                            }
                                        }
                                        if (_4668)
                                        {
                                            _4670 = _4667;
                                            break;
                                        }
                                        _4670 = false;
                                        break;
                                    } while(false);
                                    _4671 = _4638;
                                    _4672 = _4670;
                                    _4673 = _4634;
                                    _4674 = _4642;
                                }
                                else
                                {
                                    uvec4 _4600;
                                    bool _4601;
                                    uint _4602;
                                    bool _4603;
                                    if (_4054 == 1u)
                                    {
                                        uint _4537 = g_layerRenderParams._m0[0u].rbg[_1321].base.bitmapBaseAddress + _4300;
                                        uint _4539 = (_4537 >> 17u) & 3u;
                                        uint _4559;
                                        if (((_4068 >> _4539) & 1u) != 0u)
                                        {
                                            uint _4549 = _4537 + (((_4070 >> _4539) & 1u) << 3u);
                                            _4559 = (g_vram._m0[(_4549 & 524284u) >> 2u] >> (((_4549 & 3u) * 8u) & 31u)) & 255u;
                                        }
                                        else
                                        {
                                            _4559 = 0u;
                                        }
                                        uint _4563 = (_4559 >> 1u) & 7u;
                                        uvec4 _4567 = texelFetch(g_cramColor, int((_4056 + ((_4058 & 1792u) | _4559)) & _248));
                                        bool _4571;
                                        if (_4077)
                                        {
                                            _4571 = _4559 == 0u;
                                        }
                                        else
                                        {
                                            _4571 = false;
                                        }
                                        bool _4599;
                                        do
                                        {
                                            if (!_4079)
                                            {
                                                _4599 = false;
                                                break;
                                            }
                                            bool _4596;
                                            bool _4597;
                                            switch (_4072)
                                            {
                                                case 0u:
                                                {
                                                    _4596 = _4079;
                                                    _4597 = true;
                                                    break;
                                                }
                                                case 1u:
                                                {
                                                    _4596 = _4293;
                                                    _4597 = true;
                                                    break;
                                                }
                                                case 2u:
                                                {
                                                    bool _4595;
                                                    if (_4293)
                                                    {
                                                        _4595 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_4052 * 8u) + _4563) & 31u)) & 1u) != 0u;
                                                    }
                                                    else
                                                    {
                                                        _4595 = false;
                                                    }
                                                    _4596 = _4595;
                                                    _4597 = true;
                                                    break;
                                                }
                                                case 3u:
                                                {
                                                    _4596 = (_4567.w & 1u) != 0u;
                                                    _4597 = true;
                                                    break;
                                                }
                                                default:
                                                {
                                                    _4596 = _179;
                                                    _4597 = false;
                                                    break;
                                                }
                                            }
                                            if (_4597)
                                            {
                                                _4599 = _4596;
                                                break;
                                            }
                                            _4599 = false;
                                            break;
                                        } while(false);
                                        _4600 = _4567;
                                        _4601 = _4599;
                                        _4602 = _4563;
                                        _4603 = _4571;
                                    }
                                    else
                                    {
                                        uvec4 _4533;
                                        bool _4534;
                                        uint _4535;
                                        bool _4536;
                                        if (_4054 == 2u)
                                        {
                                            uint _4466 = g_layerRenderParams._m0[0u].rbg[_1321].base.bitmapBaseAddress + (_4300 << 1u);
                                            uint _4468 = (_4466 >> 17u) & 3u;
                                            uint _4493;
                                            if (((_4068 >> _4468) & 1u) != 0u)
                                            {
                                                uint _4478 = _4466 + (((_4070 >> _4468) & 1u) << 3u);
                                                uint _4487 = (g_vram._m0[(_4478 & 524284u) >> 2u] >> (((_4478 & 2u) * 8u) & 31u)) & 65535u;
                                                _4493 = ((_4487 >> 8u) & 255u) | ((_4487 << 8u) & 65280u);
                                            }
                                            else
                                            {
                                                _4493 = 0u;
                                            }
                                            uint _4494 = _4493 & 2047u;
                                            uint _4496 = (_4493 >> 1u) & 7u;
                                            uvec4 _4500 = texelFetch(g_cramColor, int((_4056 + _4494) & _248));
                                            bool _4504;
                                            if (_4077)
                                            {
                                                _4504 = _4494 == 0u;
                                            }
                                            else
                                            {
                                                _4504 = false;
                                            }
                                            bool _4532;
                                            do
                                            {
                                                if (!_4079)
                                                {
                                                    _4532 = false;
                                                    break;
                                                }
                                                bool _4529;
                                                bool _4530;
                                                switch (_4072)
                                                {
                                                    case 0u:
                                                    {
                                                        _4529 = _4079;
                                                        _4530 = true;
                                                        break;
                                                    }
                                                    case 1u:
                                                    {
                                                        _4529 = _4293;
                                                        _4530 = true;
                                                        break;
                                                    }
                                                    case 2u:
                                                    {
                                                        bool _4528;
                                                        if (_4293)
                                                        {
                                                            _4528 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_4052 * 8u) + _4496) & 31u)) & 1u) != 0u;
                                                        }
                                                        else
                                                        {
                                                            _4528 = false;
                                                        }
                                                        _4529 = _4528;
                                                        _4530 = true;
                                                        break;
                                                    }
                                                    case 3u:
                                                    {
                                                        _4529 = (_4500.w & 1u) != 0u;
                                                        _4530 = true;
                                                        break;
                                                    }
                                                    default:
                                                    {
                                                        _4529 = _179;
                                                        _4530 = false;
                                                        break;
                                                    }
                                                }
                                                if (_4530)
                                                {
                                                    _4532 = _4529;
                                                    break;
                                                }
                                                _4532 = false;
                                                break;
                                            } while(false);
                                            _4533 = _4500;
                                            _4534 = _4532;
                                            _4535 = _4496;
                                            _4536 = _4504;
                                        }
                                        else
                                        {
                                            uvec4 _4461;
                                            bool _4462;
                                            uint _4463;
                                            bool _4464;
                                            if (_4054 == 3u)
                                            {
                                                uint _4393 = g_layerRenderParams._m0[0u].rbg[_1321].base.bitmapBaseAddress + (_4300 << 1u);
                                                uint _4395 = (_4393 >> 17u) & 3u;
                                                uint _4420;
                                                if (((_4068 >> _4395) & 1u) != 0u)
                                                {
                                                    uint _4405 = _4393 + (((_4070 >> _4395) & 1u) << 3u);
                                                    uint _4414 = (g_vram._m0[(_4405 & 524284u) >> 2u] >> (((_4405 & 2u) * 8u) & 31u)) & 65535u;
                                                    _4420 = ((_4414 >> 8u) & 255u) | ((_4414 << 8u) & 65280u);
                                                }
                                                else
                                                {
                                                    _4420 = 0u;
                                                }
                                                uint _4430 = (_4420 >> 15u) & 1u;
                                                bool _4435;
                                                if (_4077)
                                                {
                                                    _4435 = _4430 == 0u;
                                                }
                                                else
                                                {
                                                    _4435 = false;
                                                }
                                                bool _4460;
                                                do
                                                {
                                                    if (!_4079)
                                                    {
                                                        _4460 = false;
                                                        break;
                                                    }
                                                    bool _4457;
                                                    bool _4458;
                                                    switch (_4072)
                                                    {
                                                        case 0u:
                                                        {
                                                            _4457 = _4079;
                                                            _4458 = true;
                                                            break;
                                                        }
                                                        case 1u:
                                                        {
                                                            _4457 = _4293;
                                                            _4458 = true;
                                                            break;
                                                        }
                                                        case 2u:
                                                        {
                                                            bool _4456;
                                                            if (_4293)
                                                            {
                                                                _4456 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_4052 * 8u) + 7u) & 31u)) & 1u) != 0u;
                                                            }
                                                            else
                                                            {
                                                                _4456 = false;
                                                            }
                                                            _4457 = _4456;
                                                            _4458 = true;
                                                            break;
                                                        }
                                                        case 3u:
                                                        {
                                                            _4457 = true;
                                                            _4458 = true;
                                                            break;
                                                        }
                                                        default:
                                                        {
                                                            _4457 = _179;
                                                            _4458 = false;
                                                            break;
                                                        }
                                                    }
                                                    if (_4458)
                                                    {
                                                        _4460 = _4457;
                                                        break;
                                                    }
                                                    _4460 = false;
                                                    break;
                                                } while(false);
                                                _4461 = uvec4((_4420 & 31u) << 3u, ((_4420 >> 5u) & 31u) << 3u, ((_4420 >> 10u) & 31u) << 3u, _4430);
                                                _4462 = _4460;
                                                _4463 = _178;
                                                _4464 = _4435;
                                            }
                                            else
                                            {
                                                bool _4317 = _4054 == 4u;
                                                uvec4 _4388;
                                                bool _4389;
                                                bool _4390;
                                                if (_4317)
                                                {
                                                    uint _4322 = g_layerRenderParams._m0[0u].rbg[_1321].base.bitmapBaseAddress + (_4300 << 2u);
                                                    uint _4324 = (_4322 >> 17u) & 3u;
                                                    uint _4350;
                                                    if (((_4068 >> _4324) & 1u) != 0u)
                                                    {
                                                        uint _4336 = ((_4322 + (((_4070 >> _4324) & 1u) << 3u)) & 524284u) >> 2u;
                                                        _4350 = ((((g_vram._m0[_4336] >> 24u) & 255u) | ((g_vram._m0[_4336] >> 8u) & 65280u)) | ((g_vram._m0[_4336] << 8u) & 16711680u)) | ((g_vram._m0[_4336] << 24u) & 4278190080u);
                                                    }
                                                    else
                                                    {
                                                        _4350 = 0u;
                                                    }
                                                    uint _4357 = (_4350 >> 31u) & 1u;
                                                    bool _4362;
                                                    if (_4077)
                                                    {
                                                        _4362 = _4357 == 0u;
                                                    }
                                                    else
                                                    {
                                                        _4362 = false;
                                                    }
                                                    bool _4387;
                                                    do
                                                    {
                                                        if (!_4079)
                                                        {
                                                            _4387 = false;
                                                            break;
                                                        }
                                                        bool _4384;
                                                        bool _4385;
                                                        switch (_4072)
                                                        {
                                                            case 0u:
                                                            {
                                                                _4384 = _4079;
                                                                _4385 = true;
                                                                break;
                                                            }
                                                            case 1u:
                                                            {
                                                                _4384 = _4293;
                                                                _4385 = true;
                                                                break;
                                                            }
                                                            case 2u:
                                                            {
                                                                bool _4383;
                                                                if (_4293)
                                                                {
                                                                    _4383 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_4052 * 8u) + 7u) & 31u)) & 1u) != 0u;
                                                                }
                                                                else
                                                                {
                                                                    _4383 = false;
                                                                }
                                                                _4384 = _4383;
                                                                _4385 = true;
                                                                break;
                                                            }
                                                            case 3u:
                                                            {
                                                                _4384 = true;
                                                                _4385 = true;
                                                                break;
                                                            }
                                                            default:
                                                            {
                                                                _4384 = _179;
                                                                _4385 = false;
                                                                break;
                                                            }
                                                        }
                                                        if (_4385)
                                                        {
                                                            _4387 = _4384;
                                                            break;
                                                        }
                                                        _4387 = false;
                                                        break;
                                                    } while(false);
                                                    _4388 = uvec4(_4350 & 255u, (_4350 >> 8u) & 255u, (_4350 >> 16u) & 255u, _4357);
                                                    _4389 = _4387;
                                                    _4390 = _4362;
                                                }
                                                else
                                                {
                                                    _4388 = uvec4(0u);
                                                    _4389 = false;
                                                    _4390 = true;
                                                }
                                                _4461 = _4388;
                                                _4462 = _4389;
                                                _4463 = _4317 ? _178 : 0u;
                                                _4464 = _4390;
                                            }
                                            _4533 = _4461;
                                            _4534 = _4462;
                                            _4535 = _4463;
                                            _4536 = _4464;
                                        }
                                        _4600 = _4533;
                                        _4601 = _4534;
                                        _4602 = _4535;
                                        _4603 = _4536;
                                    }
                                    _4671 = _4600;
                                    _4672 = _4601;
                                    _4673 = _4602;
                                    _4674 = _4603;
                                }
                                if (_4674)
                                {
                                    _4720 = uvec4(0u);
                                    break;
                                }
                                uint _4706;
                                if (_4050 == 1u)
                                {
                                    _4706 = (_4048 & 4294967294u) | _4062;
                                }
                                else
                                {
                                    uint _4703;
                                    if (_4050 == 2u)
                                    {
                                        uint _4684 = _4048 & 4294967294u;
                                        bool _4689;
                                        if (_4062 != 0u)
                                        {
                                            _4689 = _4054 < 3u;
                                        }
                                        else
                                        {
                                            _4689 = false;
                                        }
                                        uint _4702;
                                        if (_4689)
                                        {
                                            _4702 = _4684 | uint(((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_4052 * 8u) + _4673) & 31u)) & 1u) != 0u);
                                        }
                                        else
                                        {
                                            _4702 = _4684;
                                        }
                                        _4703 = _4702;
                                    }
                                    else
                                    {
                                        _4703 = _4048;
                                    }
                                    _4706 = _4703;
                                }
                                bool _4707 = _4054 <= 2u;
                                uint _4709 = 64u;
                                uint _4710 = 0u;
                                _4720 = uvec4(_4671.xyz, ((_4707 ? 64u : 0u) | uint(_4672 ? 128 : 0)) | _4706);
                                break;
                            } while(false);
                            _4721 = _4720;
                            break;
                        }
                        _4721 = uvec4(0u);
                        break;
                    } while(false);
                    _4722 = _4721;
                }
                else
                {
                    uvec4 _4042;
                    do
                    {
                        uint _2439 = g_layerRenderParams._m0[0u].rbg[_579].base.enableTransparency;
                        uint _2441 = g_layerRenderParams._m0[0u].rbg[_579].base.priorityNumber;
                        uint _2443 = g_layerRenderParams._m0[0u].rbg[_579].base.priorityMode;
                        uint _2445 = g_layerRenderParams._m0[0u].rbg[_579].base.specialFunctionSelect;
                        uint _2447 = g_layerRenderParams._m0[0u].rbg[_579].base.cellSizeShift;
                        uint _2449 = g_layerRenderParams._m0[0u].rbg[_579].base.colorFormat;
                        uint _2451 = g_layerRenderParams._m0[0u].rbg[_579].base.cramOffset;
                        uint _2453 = g_layerRenderParams._m0[0u].rbg[_579].base.supplScrollCharNum;
                        uint _2455 = g_layerRenderParams._m0[0u].rbg[_579].base.supplPalNum;
                        uint _2457 = g_layerRenderParams._m0[0u].rbg[_579].base.supplSpecialColorCalc;
                        uint _2459 = g_layerRenderParams._m0[0u].rbg[_579].base.supplSpecialPriority;
                        uint _2463 = g_layerRenderParams._m0[0u].rbg[_579].base.colorCalcEnable;
                        uint _2465 = g_layerRenderParams._m0[0u].rbg[_579].base.extChar;
                        uint _2467 = g_layerRenderParams._m0[0u].rbg[_579].base.twoWordChar;
                        uint _2469 = g_layerRenderParams._m0[0u].rbg[_579].base.patNameAccess;
                        uint _2471 = g_layerRenderParams._m0[0u].rbg[_579].base.charPatAccess;
                        uint _2473 = g_layerRenderParams._m0[0u].rbg[_579].base.charPatDelay;
                        uint _2475 = g_layerRenderParams._m0[0u].rbg[_579].base.vramDataOffset;
                        uint _2477 = g_layerRenderParams._m0[0u].rbg[_579].base.specialColorCalcMode;
                        bool _2478 = _2439 != 0u;
                        bool _2480 = _2463 != 0u;
                        bool _2481 = _2465 != 0u;
                        bool _2482 = _2467 != 0u;
                        uvec2 _2493;
                        if (g_layerRenderParams._m0[0u].rbg[_579].base.mosaicEnable != 0u)
                        {
                            uvec2 _2492 = _891;
                            _2492.x = _891.x - (_891.x % (((CommonRenderParamsBuffer.g_commonParams.layerParams >> 14u) & 15u) + 1u));
                            _2493 = _2492;
                        }
                        else
                        {
                            _2493 = _891;
                        }
                        uvec2 _2495 = g_layerRenderParams._m0[0u].rbg[_1321].base.pageShift;
                        uint _2497 = g_layerRenderParams._m0[0u].rbg[_1321].screenOverProcess;
                        uint _2499 = g_layerRenderParams._m0[0u].rbg[_1321].screenOverPatternName;
                        uvec2 _2507;
                        if (_2497 == 3u)
                        {
                            _2507 = uvec2(512u);
                        }
                        else
                        {
                            _2507 = uvec2(2048u) << (_2495 & uvec2(31u));
                        }
                        if ((!all(lessThan(_2432, _2507))) ? (_2497 == 0u) : true)
                        {
                            do
                            {
                                if (((CommonRenderParamsBuffer.g_commonParams.layerParams >> ((gl_GlobalInvocationID.z + 8u) & 31u)) & 1u) == 0u)
                                {
                                    break;
                                }
                                bool _2529 = ((CommonRenderParamsBuffer.g_commonParams.layerParams >> 13u) & 1u) != 0u;
                                uint _2539;
                                bool _2540;
                                switch ((CommonRenderParamsBuffer.g_commonParams.layerParams >> 22u) & 3u)
                                {
                                    case 0u:
                                    {
                                        _2539 = 0u;
                                        _2540 = _1321 == 0u;
                                        break;
                                    }
                                    case 1u:
                                    {
                                        _2539 = _2529 ? 0u : 1u;
                                        _2540 = _1321 == 1u;
                                        break;
                                    }
                                    case 2u:
                                    {
                                        _2539 = 0u;
                                        _2540 = true;
                                        break;
                                    }
                                    case 3u:
                                    {
                                        _2539 = _2529 ? 0u : _1321;
                                        _2540 = true;
                                        break;
                                    }
                                    default:
                                    {
                                        _2539 = _178;
                                        _2540 = false;
                                        break;
                                    }
                                }
                                uint _2548 = g_layerRenderParams._m0[0u].lineScreenParams.baseAddress + (((g_layerRenderParams._m0[0u].lineScreenParams.perLine != 0u) ? _891.y : 0u) * 2u);
                                uint _2557 = (g_vram._m0[(_2548 & 524284u) >> 2u] >> (((_2548 & 2u) * 8u) & 31u)) & 65535u;
                                uint _2562 = ((_2557 >> 8u) & 255u) | ((_2557 << 8u) & 65280u);
                                uint _2691;
                                if (_2540)
                                {
                                    uint _2565 = _2539 * 5u;
                                    bool _2570 = ((CommonRenderParamsBuffer.g_commonParams.rotParams >> ((6u + _2565) & 31u)) & 1u) != 0u;
                                    uint _2690;
                                    if (_2570 ? (((CommonRenderParamsBuffer.g_commonParams.rotParams >> ((_2565 + 10u) & 31u)) & 1u) != 0u) : false)
                                    {
                                        uint _2583 = (_2539 * 256u) + _2493.y;
                                        uint _2687;
                                        if (_2570)
                                        {
                                            uint _2593 = ((g_rotParamBases._m0[_2583].tableAddress + 92u) & 524284u) >> 2u;
                                            uint _2617;
                                            if (_239)
                                            {
                                                _2617 = _2493.x * uint((int((((((g_vram._m0[_2593] >> 24u) & 255u) | ((g_vram._m0[_2593] >> 8u) & 65280u)) | ((g_vram._m0[_2593] << 8u) & 16711680u)) | ((g_vram._m0[_2593] << 24u) & 4278190080u)) >> 6u) << 12) >> 12);
                                            }
                                            else
                                            {
                                                _2617 = 0u;
                                            }
                                            uint _2622 = (CommonRenderParamsBuffer.g_commonParams.rotParams >> ((_2565 + 7u) & 31u)) & 1u;
                                            uint _2686;
                                            do
                                            {
                                                uint _2625 = (g_rotParamBases._m0[_2583].KA + _2617) >> 10u;
                                                bool _2640;
                                                do
                                                {
                                                    if (_234)
                                                    {
                                                        _2640 = true;
                                                        break;
                                                    }
                                                    if (!_239)
                                                    {
                                                        _2640 = true;
                                                        break;
                                                    }
                                                    _2640 = ((_236 >> ((((_2625 * 4u) >> _2622) >> 17u) & 3u)) & 1u) != 0u;
                                                    break;
                                                } while(false);
                                                if (!_2640)
                                                {
                                                    _2686 = 0u;
                                                    break;
                                                }
                                                uint _2685;
                                                if (_2622 == 1u)
                                                {
                                                    _2685 = 0u;
                                                }
                                                else
                                                {
                                                    uint _2648 = _2625 * 4u;
                                                    uint _2682;
                                                    if (_234)
                                                    {
                                                        uint _2668 = (_2648 & 2044u) >> 2u;
                                                        _2682 = ((((g_cramRotCoeff_1._m0[_2668] >> 24u) & 255u) | ((g_cramRotCoeff_1._m0[_2668] >> 8u) & 65280u)) | ((g_cramRotCoeff_1._m0[_2668] << 8u) & 16711680u)) | ((g_cramRotCoeff_1._m0[_2668] << 24u) & 4278190080u);
                                                    }
                                                    else
                                                    {
                                                        uint _2653 = (_2648 & 524284u) >> 2u;
                                                        _2682 = ((((g_vram._m0[_2653] >> 24u) & 255u) | ((g_vram._m0[_2653] >> 8u) & 65280u)) | ((g_vram._m0[_2653] << 8u) & 16711680u)) | ((g_vram._m0[_2653] << 24u) & 4278190080u);
                                                    }
                                                    _2685 = (_2682 >> 24u) & 127u;
                                                }
                                                _2686 = _2685;
                                                break;
                                            } while(false);
                                            _2687 = _2686;
                                        }
                                        else
                                        {
                                            _2687 = 0u;
                                        }
                                        _2690 = (((_2562 >> 7u) & 15u) << 7u) | _2687;
                                    }
                                    else
                                    {
                                        _2690 = _2562;
                                    }
                                    _2691 = _2690;
                                }
                                else
                                {
                                    _2691 = _2562;
                                }
                                imageStore(g_rbgLineColorOut, ivec3(uvec3(_891, _579)), texelFetch(g_cramColor, int(_2691)));
                                break;
                            } while(false);
                            uvec2 _2702 = (_2432 >> ((_2495 + uvec2(9u)) & uvec2(31u))) & uvec2(3u);
                            uint _2706 = _2702.x | (_2702.y << 2u);
                            uvec2 _2718;
                            if (((_2475 >> ((g_layerRenderParams._m0[0u].rbg[_579].pageBaseAddresses[_1321][_2706] >> 17u) & 3u)) & 1u) != 0u)
                            {
                                uvec2 _2717 = _2432;
                                _2717.x = _2371 + 8u;
                                _2718 = _2717;
                            }
                            else
                            {
                                _2718 = _2432;
                            }
                            uvec2 _2720 = (_2718 >> uvec2(9u)) & _2495;
                            uint _2727 = g_layerRenderParams._m0[0u].rbg[_579].pageBaseAddresses[_1321][_2706] + ((_2720.x + (_2720.y << 1u)) << (_184[_2447][int(_2482)] & 31u));
                            uvec2 _2728 = _2718 >> uvec2(3u);
                            uvec2 _2729 = _2728 & uvec2(63u);
                            uvec2 _2735;
                            if (_2447 != 0u)
                            {
                                _2735 = _2729 >> uvec2(1u);
                            }
                            else
                            {
                                _2735 = _2729;
                            }
                            uint _2741 = _2735.x + (_2735.y << ((6u - _2447) & 31u));
                            uvec2 _2743 = _2728 & uvec2(_2447);
                            uint _2747 = _2743.x + (_2743.y << 1u);
                            uvec2 _2748 = _2718 & uvec2(7u);
                            uint _2881;
                            uint _2882;
                            bool _2883;
                            bool _2884;
                            bool _2885;
                            bool _2886;
                            if (_2482)
                            {
                                uint _2875;
                                uint _2876;
                                bool _2877;
                                bool _2878;
                                bool _2879;
                                bool _2880;
                                do
                                {
                                    uint _2836 = _2727 + (_2741 * 4u);
                                    if (((_2469 >> ((_2836 >> 17u) & 3u)) & 1u) == 0u)
                                    {
                                        _2875 = 0u;
                                        _2876 = 0u;
                                        _2877 = false;
                                        _2878 = false;
                                        _2879 = false;
                                        _2880 = false;
                                        break;
                                    }
                                    uint _2845 = (_2836 & 524284u) >> 2u;
                                    uint _2858 = ((((g_vram._m0[_2845] >> 24u) & 255u) | ((g_vram._m0[_2845] >> 8u) & 65280u)) | ((g_vram._m0[_2845] << 8u) & 16711680u)) | ((g_vram._m0[_2845] << 24u) & 4278190080u);
                                    _2875 = _2858 & 32767u;
                                    _2876 = ((_2858 >> 16u) & 127u) << 4u;
                                    _2877 = ((_2858 >> 28u) & 1u) != 0u;
                                    _2878 = ((_2858 >> 29u) & 1u) != 0u;
                                    _2879 = ((_2858 >> 30u) & 1u) != 0u;
                                    _2880 = ((_2858 >> 31u) & 1u) != 0u;
                                    break;
                                } while(false);
                                _2881 = _2875;
                                _2882 = _2876;
                                _2883 = _2877;
                                _2884 = _2878;
                                _2885 = _2879;
                                _2886 = _2880;
                            }
                            else
                            {
                                uint _2827;
                                uint _2828;
                                bool _2829;
                                bool _2830;
                                bool _2831;
                                bool _2832;
                                do
                                {
                                    uint _2755 = _2727 + (_2741 * 2u);
                                    if (((_2469 >> ((_2755 >> 17u) & 3u)) & 1u) == 0u)
                                    {
                                        _2827 = 0u;
                                        _2828 = 0u;
                                        _2829 = false;
                                        _2830 = false;
                                        _2831 = false;
                                        _2832 = false;
                                        break;
                                    }
                                    uint _2771 = (g_vram._m0[(_2755 & 524284u) >> 2u] >> (((_2755 & 2u) * 8u) & 31u)) & 65535u;
                                    uint _2776 = ((_2771 >> 8u) & 255u) | ((_2771 << 8u) & 65280u);
                                    uint _2779 = 2u * _2447;
                                    uint _2782 = _2779 + uint(_2481 ? 2 : 0);
                                    uint _2783 = _2782 & 31u;
                                    uint _2793 = ((_2776 & uint(_2481 ? 4095 : 1023)) << (_2779 & 31u)) | (((_2453 >> _2783) & (31u >> _2783)) << ((10u + _2782) & 31u));
                                    uint _2799;
                                    if (_2447 > 0u)
                                    {
                                        _2799 = _2793 | (_2453 & 3u);
                                    }
                                    else
                                    {
                                        _2799 = _2793;
                                    }
                                    uint _2811;
                                    if (_2449 != 0u)
                                    {
                                        _2811 = ((_2776 >> 12u) & 7u) << 8u;
                                    }
                                    else
                                    {
                                        _2811 = (((_2776 >> 12u) & 15u) | _2455) << 4u;
                                    }
                                    bool _2814 = !_2481;
                                    bool _2820;
                                    if (_2814)
                                    {
                                        _2820 = ((_2776 >> 10u) & 1u) != 0u;
                                    }
                                    else
                                    {
                                        _2820 = false;
                                    }
                                    bool _2826;
                                    if (_2814)
                                    {
                                        _2826 = ((_2776 >> 11u) & 1u) != 0u;
                                    }
                                    else
                                    {
                                        _2826 = false;
                                    }
                                    _2827 = _2799;
                                    _2828 = _2811;
                                    _2829 = _2457 != 0u;
                                    _2830 = _2459 != 0u;
                                    _2831 = _2820;
                                    _2832 = _2826;
                                    break;
                                } while(false);
                                _2881 = _2827;
                                _2882 = _2828;
                                _2883 = _2829;
                                _2884 = _2830;
                                _2885 = _2831;
                                _2886 = _2832;
                            }
                            bool _2897;
                            if (_2482 ? (((_2473 >> (((_2881 << 5u) >> 17u) & 3u)) & 1u) != 0u) : false)
                            {
                                _2897 = _2447 > 0u;
                            }
                            else
                            {
                                _2897 = false;
                            }
                            uint _2901;
                            if (_2897)
                            {
                                _2901 = _2747 ^ 1u;
                            }
                            else
                            {
                                _2901 = _2747;
                            }
                            uvec2 _2912;
                            uint _2913;
                            if (_2885)
                            {
                                uvec2 _2906 = _2748;
                                _2906.x = _2748.x ^ 7u;
                                uint _2911;
                                if (_2447 > 0u)
                                {
                                    _2911 = _2901 ^ 1u;
                                }
                                else
                                {
                                    _2911 = _2901;
                                }
                                _2912 = _2906;
                                _2913 = _2911;
                            }
                            else
                            {
                                _2912 = _2748;
                                _2913 = _2901;
                            }
                            uvec2 _2924;
                            uint _2925;
                            if (_2886)
                            {
                                uvec2 _2918 = _2912;
                                _2918.y = _2912.y ^ 7u;
                                uint _2923;
                                if (_2447 > 0u)
                                {
                                    _2923 = _2913 ^ 2u;
                                }
                                else
                                {
                                    _2923 = _2913;
                                }
                                _2924 = _2918;
                                _2925 = _2923;
                            }
                            else
                            {
                                _2924 = _2912;
                                _2925 = _2913;
                            }
                            bool _2926 = _2449 == 4u;
                            uint _2942;
                            if (_2926)
                            {
                                _2942 = _2925 << 3u;
                            }
                            else
                            {
                                uint _2940;
                                if (_2449 == 3u)
                                {
                                    _2940 = _2925 << 2u;
                                }
                                else
                                {
                                    uint _2938;
                                    if (_2449 != 0u)
                                    {
                                        _2938 = _2925 << 1u;
                                    }
                                    else
                                    {
                                        _2938 = _2925;
                                    }
                                    _2940 = _2938;
                                }
                                _2942 = _2940;
                            }
                            uint _2944 = (_2881 + _2942) << 5u;
                            uvec4 _3349;
                            do
                            {
                                uint _2951 = _2924.x + (_2924.y * 8u);
                                uvec4 _3301;
                                bool _3302;
                                uint _3303;
                                bool _3304;
                                if (_2449 == 0u)
                                {
                                    uint _3239 = _2944 + (_2951 >> 1u);
                                    uint _3261;
                                    if (((_2471 >> ((_3239 >> 17u) & 3u)) & 1u) != 0u)
                                    {
                                        _3261 = (g_vram._m0[(_3239 & 524284u) >> 2u] >> ((((_3239 & 3u) * 8u) + (((~_2924.x) & 1u) * 4u)) & 31u)) & 15u;
                                    }
                                    else
                                    {
                                        _3261 = 0u;
                                    }
                                    uint _3264 = (_3261 >> 1u) & 7u;
                                    uvec4 _3268 = texelFetch(g_cramColor, int((_2451 + (_2882 | _3261)) & _248));
                                    bool _3272;
                                    if (_2478)
                                    {
                                        _3272 = _3261 == 0u;
                                    }
                                    else
                                    {
                                        _3272 = false;
                                    }
                                    bool _3300;
                                    do
                                    {
                                        if (!_2480)
                                        {
                                            _3300 = false;
                                            break;
                                        }
                                        bool _3297;
                                        bool _3298;
                                        switch (_2477)
                                        {
                                            case 0u:
                                            {
                                                _3297 = _2480;
                                                _3298 = true;
                                                break;
                                            }
                                            case 1u:
                                            {
                                                _3297 = _2883;
                                                _3298 = true;
                                                break;
                                            }
                                            case 2u:
                                            {
                                                bool _3296;
                                                if (_2883)
                                                {
                                                    _3296 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + _3264) & 31u)) & 1u) != 0u;
                                                }
                                                else
                                                {
                                                    _3296 = false;
                                                }
                                                _3297 = _3296;
                                                _3298 = true;
                                                break;
                                            }
                                            case 3u:
                                            {
                                                _3297 = (_3268.w & 1u) != 0u;
                                                _3298 = true;
                                                break;
                                            }
                                            default:
                                            {
                                                _3297 = _179;
                                                _3298 = false;
                                                break;
                                            }
                                        }
                                        if (_3298)
                                        {
                                            _3300 = _3297;
                                            break;
                                        }
                                        _3300 = false;
                                        break;
                                    } while(false);
                                    _3301 = _3268;
                                    _3302 = _3300;
                                    _3303 = _3264;
                                    _3304 = _3272;
                                }
                                else
                                {
                                    uvec4 _3234;
                                    bool _3235;
                                    uint _3236;
                                    bool _3237;
                                    if (_2449 == 1u)
                                    {
                                        uint _3175 = _2944 + _2951;
                                        uint _3193;
                                        if (((_2471 >> ((_3175 >> 17u) & 3u)) & 1u) != 0u)
                                        {
                                            _3193 = (g_vram._m0[(_3175 & 524284u) >> 2u] >> (((_3175 & 3u) * 8u) & 31u)) & 255u;
                                        }
                                        else
                                        {
                                            _3193 = 0u;
                                        }
                                        uint _3197 = (_3193 >> 1u) & 7u;
                                        uvec4 _3201 = texelFetch(g_cramColor, int((_2451 + ((_2882 & 1792u) | _3193)) & _248));
                                        bool _3205;
                                        if (_2478)
                                        {
                                            _3205 = _3193 == 0u;
                                        }
                                        else
                                        {
                                            _3205 = false;
                                        }
                                        bool _3233;
                                        do
                                        {
                                            if (!_2480)
                                            {
                                                _3233 = false;
                                                break;
                                            }
                                            bool _3230;
                                            bool _3231;
                                            switch (_2477)
                                            {
                                                case 0u:
                                                {
                                                    _3230 = _2480;
                                                    _3231 = true;
                                                    break;
                                                }
                                                case 1u:
                                                {
                                                    _3230 = _2883;
                                                    _3231 = true;
                                                    break;
                                                }
                                                case 2u:
                                                {
                                                    bool _3229;
                                                    if (_2883)
                                                    {
                                                        _3229 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + _3197) & 31u)) & 1u) != 0u;
                                                    }
                                                    else
                                                    {
                                                        _3229 = false;
                                                    }
                                                    _3230 = _3229;
                                                    _3231 = true;
                                                    break;
                                                }
                                                case 3u:
                                                {
                                                    _3230 = (_3201.w & 1u) != 0u;
                                                    _3231 = true;
                                                    break;
                                                }
                                                default:
                                                {
                                                    _3230 = _179;
                                                    _3231 = false;
                                                    break;
                                                }
                                            }
                                            if (_3231)
                                            {
                                                _3233 = _3230;
                                                break;
                                            }
                                            _3233 = false;
                                            break;
                                        } while(false);
                                        _3234 = _3201;
                                        _3235 = _3233;
                                        _3236 = _3197;
                                        _3237 = _3205;
                                    }
                                    else
                                    {
                                        uvec4 _3171;
                                        bool _3172;
                                        uint _3173;
                                        bool _3174;
                                        if (_2449 == 2u)
                                        {
                                            uint _3108 = _2944 + (_2951 << 1u);
                                            uint _3131;
                                            if (((_2471 >> ((_3108 >> 17u) & 3u)) & 1u) != 0u)
                                            {
                                                uint _3125 = (g_vram._m0[(_3108 & 524284u) >> 2u] >> (((_3108 & 2u) * 8u) & 31u)) & 65535u;
                                                _3131 = ((_3125 >> 8u) & 255u) | ((_3125 << 8u) & 65280u);
                                            }
                                            else
                                            {
                                                _3131 = 0u;
                                            }
                                            uint _3132 = _3131 & 2047u;
                                            uint _3134 = (_3131 >> 1u) & 7u;
                                            uvec4 _3138 = texelFetch(g_cramColor, int((_2451 + _3132) & _248));
                                            bool _3142;
                                            if (_2478)
                                            {
                                                _3142 = _3132 == 0u;
                                            }
                                            else
                                            {
                                                _3142 = false;
                                            }
                                            bool _3170;
                                            do
                                            {
                                                if (!_2480)
                                                {
                                                    _3170 = false;
                                                    break;
                                                }
                                                bool _3167;
                                                bool _3168;
                                                switch (_2477)
                                                {
                                                    case 0u:
                                                    {
                                                        _3167 = _2480;
                                                        _3168 = true;
                                                        break;
                                                    }
                                                    case 1u:
                                                    {
                                                        _3167 = _2883;
                                                        _3168 = true;
                                                        break;
                                                    }
                                                    case 2u:
                                                    {
                                                        bool _3166;
                                                        if (_2883)
                                                        {
                                                            _3166 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + _3134) & 31u)) & 1u) != 0u;
                                                        }
                                                        else
                                                        {
                                                            _3166 = false;
                                                        }
                                                        _3167 = _3166;
                                                        _3168 = true;
                                                        break;
                                                    }
                                                    case 3u:
                                                    {
                                                        _3167 = (_3138.w & 1u) != 0u;
                                                        _3168 = true;
                                                        break;
                                                    }
                                                    default:
                                                    {
                                                        _3167 = _179;
                                                        _3168 = false;
                                                        break;
                                                    }
                                                }
                                                if (_3168)
                                                {
                                                    _3170 = _3167;
                                                    break;
                                                }
                                                _3170 = false;
                                                break;
                                            } while(false);
                                            _3171 = _3138;
                                            _3172 = _3170;
                                            _3173 = _3134;
                                            _3174 = _3142;
                                        }
                                        else
                                        {
                                            uvec4 _3103;
                                            bool _3104;
                                            uint _3105;
                                            bool _3106;
                                            if (_2449 == 3u)
                                            {
                                                uint _3039 = _2944 + (_2951 << 1u);
                                                uint _3062;
                                                if (((_2471 >> ((_3039 >> 17u) & 3u)) & 1u) != 0u)
                                                {
                                                    uint _3056 = (g_vram._m0[(_3039 & 524284u) >> 2u] >> (((_3039 & 2u) * 8u) & 31u)) & 65535u;
                                                    _3062 = ((_3056 >> 8u) & 255u) | ((_3056 << 8u) & 65280u);
                                                }
                                                else
                                                {
                                                    _3062 = 0u;
                                                }
                                                uint _3072 = (_3062 >> 15u) & 1u;
                                                bool _3077;
                                                if (_2478)
                                                {
                                                    _3077 = _3072 == 0u;
                                                }
                                                else
                                                {
                                                    _3077 = false;
                                                }
                                                bool _3102;
                                                do
                                                {
                                                    if (!_2480)
                                                    {
                                                        _3102 = false;
                                                        break;
                                                    }
                                                    bool _3099;
                                                    bool _3100;
                                                    switch (_2477)
                                                    {
                                                        case 0u:
                                                        {
                                                            _3099 = _2480;
                                                            _3100 = true;
                                                            break;
                                                        }
                                                        case 1u:
                                                        {
                                                            _3099 = _2883;
                                                            _3100 = true;
                                                            break;
                                                        }
                                                        case 2u:
                                                        {
                                                            bool _3098;
                                                            if (_2883)
                                                            {
                                                                _3098 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + 7u) & 31u)) & 1u) != 0u;
                                                            }
                                                            else
                                                            {
                                                                _3098 = false;
                                                            }
                                                            _3099 = _3098;
                                                            _3100 = true;
                                                            break;
                                                        }
                                                        case 3u:
                                                        {
                                                            _3099 = true;
                                                            _3100 = true;
                                                            break;
                                                        }
                                                        default:
                                                        {
                                                            _3099 = _179;
                                                            _3100 = false;
                                                            break;
                                                        }
                                                    }
                                                    if (_3100)
                                                    {
                                                        _3102 = _3099;
                                                        break;
                                                    }
                                                    _3102 = false;
                                                    break;
                                                } while(false);
                                                _3103 = uvec4((_3062 & 31u) << 3u, ((_3062 >> 5u) & 31u) << 3u, ((_3062 >> 10u) & 31u) << 3u, _3072);
                                                _3104 = _3102;
                                                _3105 = _178;
                                                _3106 = _3077;
                                            }
                                            else
                                            {
                                                uvec4 _3034;
                                                bool _3035;
                                                bool _3036;
                                                if (_2926)
                                                {
                                                    uint _2972 = _2944 + (_2951 << 2u);
                                                    uint _2996;
                                                    if (((_2471 >> ((_2972 >> 17u) & 3u)) & 1u) != 0u)
                                                    {
                                                        uint _2982 = (_2972 & 524284u) >> 2u;
                                                        _2996 = ((((g_vram._m0[_2982] >> 24u) & 255u) | ((g_vram._m0[_2982] >> 8u) & 65280u)) | ((g_vram._m0[_2982] << 8u) & 16711680u)) | ((g_vram._m0[_2982] << 24u) & 4278190080u);
                                                    }
                                                    else
                                                    {
                                                        _2996 = 0u;
                                                    }
                                                    uint _3003 = (_2996 >> 31u) & 1u;
                                                    bool _3008;
                                                    if (_2478)
                                                    {
                                                        _3008 = _3003 == 0u;
                                                    }
                                                    else
                                                    {
                                                        _3008 = false;
                                                    }
                                                    bool _3033;
                                                    do
                                                    {
                                                        if (!_2480)
                                                        {
                                                            _3033 = false;
                                                            break;
                                                        }
                                                        bool _3030;
                                                        bool _3031;
                                                        switch (_2477)
                                                        {
                                                            case 0u:
                                                            {
                                                                _3030 = _2480;
                                                                _3031 = true;
                                                                break;
                                                            }
                                                            case 1u:
                                                            {
                                                                _3030 = _2883;
                                                                _3031 = true;
                                                                break;
                                                            }
                                                            case 2u:
                                                            {
                                                                bool _3029;
                                                                if (_2883)
                                                                {
                                                                    _3029 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + 7u) & 31u)) & 1u) != 0u;
                                                                }
                                                                else
                                                                {
                                                                    _3029 = false;
                                                                }
                                                                _3030 = _3029;
                                                                _3031 = true;
                                                                break;
                                                            }
                                                            case 3u:
                                                            {
                                                                _3030 = true;
                                                                _3031 = true;
                                                                break;
                                                            }
                                                            default:
                                                            {
                                                                _3030 = _179;
                                                                _3031 = false;
                                                                break;
                                                            }
                                                        }
                                                        if (_3031)
                                                        {
                                                            _3033 = _3030;
                                                            break;
                                                        }
                                                        _3033 = false;
                                                        break;
                                                    } while(false);
                                                    _3034 = uvec4(_2996 & 255u, (_2996 >> 8u) & 255u, (_2996 >> 16u) & 255u, _3003);
                                                    _3035 = _3033;
                                                    _3036 = _3008;
                                                }
                                                else
                                                {
                                                    _3034 = uvec4(0u);
                                                    _3035 = false;
                                                    _3036 = true;
                                                }
                                                _3103 = _3034;
                                                _3104 = _3035;
                                                _3105 = _2926 ? _178 : 0u;
                                                _3106 = _3036;
                                            }
                                            _3171 = _3103;
                                            _3172 = _3104;
                                            _3173 = _3105;
                                            _3174 = _3106;
                                        }
                                        _3234 = _3171;
                                        _3235 = _3172;
                                        _3236 = _3173;
                                        _3237 = _3174;
                                    }
                                    _3301 = _3234;
                                    _3302 = _3235;
                                    _3303 = _3236;
                                    _3304 = _3237;
                                }
                                if (_3304)
                                {
                                    _3349 = uvec4(0u);
                                    break;
                                }
                                uint _3335;
                                if (_2443 == 1u)
                                {
                                    _3335 = (_2441 & 4294967294u) | uint(_2884);
                                }
                                else
                                {
                                    uint _3332;
                                    if (_2443 == 2u)
                                    {
                                        uint _3314 = _2441 & 4294967294u;
                                        bool _3318;
                                        if (_2884)
                                        {
                                            _3318 = _2449 < 3u;
                                        }
                                        else
                                        {
                                            _3318 = false;
                                        }
                                        uint _3331;
                                        if (_3318)
                                        {
                                            _3331 = _3314 | uint(((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + _3303) & 31u)) & 1u) != 0u);
                                        }
                                        else
                                        {
                                            _3331 = _3314;
                                        }
                                        _3332 = _3331;
                                    }
                                    else
                                    {
                                        _3332 = _2441;
                                    }
                                    _3335 = _3332;
                                }
                                bool _3336 = _2449 <= 2u;
                                uint _3338 = 64u;
                                uint _3339 = 0u;
                                _3349 = uvec4(_3301.xyz, ((_3336 ? 64u : 0u) | uint(_3302 ? 128 : 0)) | _3335);
                                break;
                            } while(false);
                            _4042 = _3349;
                            break;
                        }
                        if (_2497 == 1u)
                        {
                            do
                            {
                                if (((CommonRenderParamsBuffer.g_commonParams.layerParams >> ((gl_GlobalInvocationID.z + 8u) & 31u)) & 1u) == 0u)
                                {
                                    break;
                                }
                                bool _3368 = ((CommonRenderParamsBuffer.g_commonParams.layerParams >> 13u) & 1u) != 0u;
                                uint _3378;
                                bool _3379;
                                switch ((CommonRenderParamsBuffer.g_commonParams.layerParams >> 22u) & 3u)
                                {
                                    case 0u:
                                    {
                                        _3378 = 0u;
                                        _3379 = _1321 == 0u;
                                        break;
                                    }
                                    case 1u:
                                    {
                                        _3378 = _3368 ? 0u : 1u;
                                        _3379 = _1321 == 1u;
                                        break;
                                    }
                                    case 2u:
                                    {
                                        _3378 = 0u;
                                        _3379 = true;
                                        break;
                                    }
                                    case 3u:
                                    {
                                        _3378 = _3368 ? 0u : _1321;
                                        _3379 = true;
                                        break;
                                    }
                                    default:
                                    {
                                        _3378 = _178;
                                        _3379 = false;
                                        break;
                                    }
                                }
                                uint _3387 = g_layerRenderParams._m0[0u].lineScreenParams.baseAddress + (((g_layerRenderParams._m0[0u].lineScreenParams.perLine != 0u) ? _891.y : 0u) * 2u);
                                uint _3396 = (g_vram._m0[(_3387 & 524284u) >> 2u] >> (((_3387 & 2u) * 8u) & 31u)) & 65535u;
                                uint _3401 = ((_3396 >> 8u) & 255u) | ((_3396 << 8u) & 65280u);
                                uint _3530;
                                if (_3379)
                                {
                                    uint _3404 = _3378 * 5u;
                                    bool _3409 = ((CommonRenderParamsBuffer.g_commonParams.rotParams >> ((6u + _3404) & 31u)) & 1u) != 0u;
                                    uint _3529;
                                    if (_3409 ? (((CommonRenderParamsBuffer.g_commonParams.rotParams >> ((_3404 + 10u) & 31u)) & 1u) != 0u) : false)
                                    {
                                        uint _3422 = (_3378 * 256u) + _2493.y;
                                        uint _3526;
                                        if (_3409)
                                        {
                                            uint _3432 = ((g_rotParamBases._m0[_3422].tableAddress + 92u) & 524284u) >> 2u;
                                            uint _3456;
                                            if (_239)
                                            {
                                                _3456 = _2493.x * uint((int((((((g_vram._m0[_3432] >> 24u) & 255u) | ((g_vram._m0[_3432] >> 8u) & 65280u)) | ((g_vram._m0[_3432] << 8u) & 16711680u)) | ((g_vram._m0[_3432] << 24u) & 4278190080u)) >> 6u) << 12) >> 12);
                                            }
                                            else
                                            {
                                                _3456 = 0u;
                                            }
                                            uint _3461 = (CommonRenderParamsBuffer.g_commonParams.rotParams >> ((_3404 + 7u) & 31u)) & 1u;
                                            uint _3525;
                                            do
                                            {
                                                uint _3464 = (g_rotParamBases._m0[_3422].KA + _3456) >> 10u;
                                                bool _3479;
                                                do
                                                {
                                                    if (_234)
                                                    {
                                                        _3479 = true;
                                                        break;
                                                    }
                                                    if (!_239)
                                                    {
                                                        _3479 = true;
                                                        break;
                                                    }
                                                    _3479 = ((_236 >> ((((_3464 * 4u) >> _3461) >> 17u) & 3u)) & 1u) != 0u;
                                                    break;
                                                } while(false);
                                                if (!_3479)
                                                {
                                                    _3525 = 0u;
                                                    break;
                                                }
                                                uint _3524;
                                                if (_3461 == 1u)
                                                {
                                                    _3524 = 0u;
                                                }
                                                else
                                                {
                                                    uint _3487 = _3464 * 4u;
                                                    uint _3521;
                                                    if (_234)
                                                    {
                                                        uint _3507 = (_3487 & 2044u) >> 2u;
                                                        _3521 = ((((g_cramRotCoeff_1._m0[_3507] >> 24u) & 255u) | ((g_cramRotCoeff_1._m0[_3507] >> 8u) & 65280u)) | ((g_cramRotCoeff_1._m0[_3507] << 8u) & 16711680u)) | ((g_cramRotCoeff_1._m0[_3507] << 24u) & 4278190080u);
                                                    }
                                                    else
                                                    {
                                                        uint _3492 = (_3487 & 524284u) >> 2u;
                                                        _3521 = ((((g_vram._m0[_3492] >> 24u) & 255u) | ((g_vram._m0[_3492] >> 8u) & 65280u)) | ((g_vram._m0[_3492] << 8u) & 16711680u)) | ((g_vram._m0[_3492] << 24u) & 4278190080u);
                                                    }
                                                    _3524 = (_3521 >> 24u) & 127u;
                                                }
                                                _3525 = _3524;
                                                break;
                                            } while(false);
                                            _3526 = _3525;
                                        }
                                        else
                                        {
                                            _3526 = 0u;
                                        }
                                        _3529 = (((_3401 >> 7u) & 15u) << 7u) | _3526;
                                    }
                                    else
                                    {
                                        _3529 = _3401;
                                    }
                                    _3530 = _3529;
                                }
                                else
                                {
                                    _3530 = _3401;
                                }
                                imageStore(g_rbgLineColorOut, ivec3(uvec3(_891, _579)), texelFetch(g_cramColor, int(_3530)));
                                break;
                            } while(false);
                            uvec2 _3535 = _2432 & uvec2(7u);
                            uint _3538 = 2u * _2447;
                            uint _3541 = _3538 + uint(_2481 ? 2 : 0);
                            uint _3542 = _3541 & 31u;
                            uint _3552 = ((_2499 & uint(_2481 ? 4095 : 1023)) << (_3538 & 31u)) | (((_2453 >> _3542) & (31u >> _3542)) << ((10u + _3541) & 31u));
                            bool _3553 = _2447 > 0u;
                            uint _3558;
                            if (_3553)
                            {
                                _3558 = _3552 | (_2453 & 3u);
                            }
                            else
                            {
                                _3558 = _3552;
                            }
                            bool _3559 = _2449 != 0u;
                            uint _3570;
                            if (_3559)
                            {
                                _3570 = ((_2499 >> 12u) & 7u) << 8u;
                            }
                            else
                            {
                                _3570 = (((_2499 >> 12u) & 15u) | _2455) << 4u;
                            }
                            bool _3571 = _2457 != 0u;
                            bool _3572 = _2459 != 0u;
                            bool _3573 = !_2481;
                            bool _3579;
                            if (_3573)
                            {
                                _3579 = ((_2499 >> 10u) & 1u) != 0u;
                            }
                            else
                            {
                                _3579 = false;
                            }
                            bool _3585;
                            if (_3573)
                            {
                                _3585 = ((_2499 >> 11u) & 1u) != 0u;
                            }
                            else
                            {
                                _3585 = false;
                            }
                            bool _3593 = (_2482 ? (((_2473 >> (((_3558 << 5u) >> 17u) & 3u)) & 1u) != 0u) : false) ? _3553 : false;
                            uint _3594 = uint(_3593);
                            uvec2 _3606;
                            uint _3607;
                            if (_3579)
                            {
                                uvec2 _3599 = _3535;
                                _3599.x = _3535.x ^ 7u;
                                uint _3605;
                                if (_3553)
                                {
                                    uint _3602 = 0u;
                                    uint _3603 = 1u;
                                    _3605 = _3593 ? 0u : 1u;
                                }
                                else
                                {
                                    _3605 = _3594;
                                }
                                _3606 = _3599;
                                _3607 = _3605;
                            }
                            else
                            {
                                _3606 = _3535;
                                _3607 = _3594;
                            }
                            uvec2 _3617;
                            uint _3618;
                            if (_3585)
                            {
                                uvec2 _3612 = _3606;
                                _3612.y = _3606.y ^ 7u;
                                uint _3616;
                                if (_3553)
                                {
                                    _3616 = _3607 ^ 2u;
                                }
                                else
                                {
                                    _3616 = _3607;
                                }
                                _3617 = _3612;
                                _3618 = _3616;
                            }
                            else
                            {
                                _3617 = _3606;
                                _3618 = _3607;
                            }
                            bool _3619 = _2449 == 4u;
                            uint _3634;
                            if (_3619)
                            {
                                _3634 = _3618 << 3u;
                            }
                            else
                            {
                                uint _3632;
                                if (_2449 == 3u)
                                {
                                    _3632 = _3618 << 2u;
                                }
                                else
                                {
                                    uint _3630;
                                    if (_3559)
                                    {
                                        _3630 = _3618 << 1u;
                                    }
                                    else
                                    {
                                        _3630 = _3618;
                                    }
                                    _3632 = _3630;
                                }
                                _3634 = _3632;
                            }
                            uint _3636 = (_3558 + _3634) << 5u;
                            uvec4 _4041;
                            do
                            {
                                uint _3643 = _3617.x + (_3617.y * 8u);
                                uvec4 _3993;
                                bool _3994;
                                uint _3995;
                                bool _3996;
                                if (_2449 == 0u)
                                {
                                    uint _3931 = _3636 + (_3643 >> 1u);
                                    uint _3953;
                                    if (((_2471 >> ((_3931 >> 17u) & 3u)) & 1u) != 0u)
                                    {
                                        _3953 = (g_vram._m0[(_3931 & 524284u) >> 2u] >> ((((_3931 & 3u) * 8u) + (((~_3617.x) & 1u) * 4u)) & 31u)) & 15u;
                                    }
                                    else
                                    {
                                        _3953 = 0u;
                                    }
                                    uint _3956 = (_3953 >> 1u) & 7u;
                                    uvec4 _3960 = texelFetch(g_cramColor, int((_2451 + (_3570 | _3953)) & _248));
                                    bool _3964;
                                    if (_2478)
                                    {
                                        _3964 = _3953 == 0u;
                                    }
                                    else
                                    {
                                        _3964 = false;
                                    }
                                    bool _3992;
                                    do
                                    {
                                        if (!_2480)
                                        {
                                            _3992 = false;
                                            break;
                                        }
                                        bool _3989;
                                        bool _3990;
                                        switch (_2477)
                                        {
                                            case 0u:
                                            {
                                                _3989 = _2480;
                                                _3990 = true;
                                                break;
                                            }
                                            case 1u:
                                            {
                                                _3989 = _3571;
                                                _3990 = true;
                                                break;
                                            }
                                            case 2u:
                                            {
                                                bool _3988;
                                                if (_3571)
                                                {
                                                    _3988 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + _3956) & 31u)) & 1u) != 0u;
                                                }
                                                else
                                                {
                                                    _3988 = false;
                                                }
                                                _3989 = _3988;
                                                _3990 = true;
                                                break;
                                            }
                                            case 3u:
                                            {
                                                _3989 = (_3960.w & 1u) != 0u;
                                                _3990 = true;
                                                break;
                                            }
                                            default:
                                            {
                                                _3989 = _179;
                                                _3990 = false;
                                                break;
                                            }
                                        }
                                        if (_3990)
                                        {
                                            _3992 = _3989;
                                            break;
                                        }
                                        _3992 = false;
                                        break;
                                    } while(false);
                                    _3993 = _3960;
                                    _3994 = _3992;
                                    _3995 = _3956;
                                    _3996 = _3964;
                                }
                                else
                                {
                                    uvec4 _3926;
                                    bool _3927;
                                    uint _3928;
                                    bool _3929;
                                    if (_2449 == 1u)
                                    {
                                        uint _3867 = _3636 + _3643;
                                        uint _3885;
                                        if (((_2471 >> ((_3867 >> 17u) & 3u)) & 1u) != 0u)
                                        {
                                            _3885 = (g_vram._m0[(_3867 & 524284u) >> 2u] >> (((_3867 & 3u) * 8u) & 31u)) & 255u;
                                        }
                                        else
                                        {
                                            _3885 = 0u;
                                        }
                                        uint _3889 = (_3885 >> 1u) & 7u;
                                        uvec4 _3893 = texelFetch(g_cramColor, int((_2451 + ((_3570 & 1792u) | _3885)) & _248));
                                        bool _3897;
                                        if (_2478)
                                        {
                                            _3897 = _3885 == 0u;
                                        }
                                        else
                                        {
                                            _3897 = false;
                                        }
                                        bool _3925;
                                        do
                                        {
                                            if (!_2480)
                                            {
                                                _3925 = false;
                                                break;
                                            }
                                            bool _3922;
                                            bool _3923;
                                            switch (_2477)
                                            {
                                                case 0u:
                                                {
                                                    _3922 = _2480;
                                                    _3923 = true;
                                                    break;
                                                }
                                                case 1u:
                                                {
                                                    _3922 = _3571;
                                                    _3923 = true;
                                                    break;
                                                }
                                                case 2u:
                                                {
                                                    bool _3921;
                                                    if (_3571)
                                                    {
                                                        _3921 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + _3889) & 31u)) & 1u) != 0u;
                                                    }
                                                    else
                                                    {
                                                        _3921 = false;
                                                    }
                                                    _3922 = _3921;
                                                    _3923 = true;
                                                    break;
                                                }
                                                case 3u:
                                                {
                                                    _3922 = (_3893.w & 1u) != 0u;
                                                    _3923 = true;
                                                    break;
                                                }
                                                default:
                                                {
                                                    _3922 = _179;
                                                    _3923 = false;
                                                    break;
                                                }
                                            }
                                            if (_3923)
                                            {
                                                _3925 = _3922;
                                                break;
                                            }
                                            _3925 = false;
                                            break;
                                        } while(false);
                                        _3926 = _3893;
                                        _3927 = _3925;
                                        _3928 = _3889;
                                        _3929 = _3897;
                                    }
                                    else
                                    {
                                        uvec4 _3863;
                                        bool _3864;
                                        uint _3865;
                                        bool _3866;
                                        if (_2449 == 2u)
                                        {
                                            uint _3800 = _3636 + (_3643 << 1u);
                                            uint _3823;
                                            if (((_2471 >> ((_3800 >> 17u) & 3u)) & 1u) != 0u)
                                            {
                                                uint _3817 = (g_vram._m0[(_3800 & 524284u) >> 2u] >> (((_3800 & 2u) * 8u) & 31u)) & 65535u;
                                                _3823 = ((_3817 >> 8u) & 255u) | ((_3817 << 8u) & 65280u);
                                            }
                                            else
                                            {
                                                _3823 = 0u;
                                            }
                                            uint _3824 = _3823 & 2047u;
                                            uint _3826 = (_3823 >> 1u) & 7u;
                                            uvec4 _3830 = texelFetch(g_cramColor, int((_2451 + _3824) & _248));
                                            bool _3834;
                                            if (_2478)
                                            {
                                                _3834 = _3824 == 0u;
                                            }
                                            else
                                            {
                                                _3834 = false;
                                            }
                                            bool _3862;
                                            do
                                            {
                                                if (!_2480)
                                                {
                                                    _3862 = false;
                                                    break;
                                                }
                                                bool _3859;
                                                bool _3860;
                                                switch (_2477)
                                                {
                                                    case 0u:
                                                    {
                                                        _3859 = _2480;
                                                        _3860 = true;
                                                        break;
                                                    }
                                                    case 1u:
                                                    {
                                                        _3859 = _3571;
                                                        _3860 = true;
                                                        break;
                                                    }
                                                    case 2u:
                                                    {
                                                        bool _3858;
                                                        if (_3571)
                                                        {
                                                            _3858 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + _3826) & 31u)) & 1u) != 0u;
                                                        }
                                                        else
                                                        {
                                                            _3858 = false;
                                                        }
                                                        _3859 = _3858;
                                                        _3860 = true;
                                                        break;
                                                    }
                                                    case 3u:
                                                    {
                                                        _3859 = (_3830.w & 1u) != 0u;
                                                        _3860 = true;
                                                        break;
                                                    }
                                                    default:
                                                    {
                                                        _3859 = _179;
                                                        _3860 = false;
                                                        break;
                                                    }
                                                }
                                                if (_3860)
                                                {
                                                    _3862 = _3859;
                                                    break;
                                                }
                                                _3862 = false;
                                                break;
                                            } while(false);
                                            _3863 = _3830;
                                            _3864 = _3862;
                                            _3865 = _3826;
                                            _3866 = _3834;
                                        }
                                        else
                                        {
                                            uvec4 _3795;
                                            bool _3796;
                                            uint _3797;
                                            bool _3798;
                                            if (_2449 == 3u)
                                            {
                                                uint _3731 = _3636 + (_3643 << 1u);
                                                uint _3754;
                                                if (((_2471 >> ((_3731 >> 17u) & 3u)) & 1u) != 0u)
                                                {
                                                    uint _3748 = (g_vram._m0[(_3731 & 524284u) >> 2u] >> (((_3731 & 2u) * 8u) & 31u)) & 65535u;
                                                    _3754 = ((_3748 >> 8u) & 255u) | ((_3748 << 8u) & 65280u);
                                                }
                                                else
                                                {
                                                    _3754 = 0u;
                                                }
                                                uint _3764 = (_3754 >> 15u) & 1u;
                                                bool _3769;
                                                if (_2478)
                                                {
                                                    _3769 = _3764 == 0u;
                                                }
                                                else
                                                {
                                                    _3769 = false;
                                                }
                                                bool _3794;
                                                do
                                                {
                                                    if (!_2480)
                                                    {
                                                        _3794 = false;
                                                        break;
                                                    }
                                                    bool _3791;
                                                    bool _3792;
                                                    switch (_2477)
                                                    {
                                                        case 0u:
                                                        {
                                                            _3791 = _2480;
                                                            _3792 = true;
                                                            break;
                                                        }
                                                        case 1u:
                                                        {
                                                            _3791 = _3571;
                                                            _3792 = true;
                                                            break;
                                                        }
                                                        case 2u:
                                                        {
                                                            bool _3790;
                                                            if (_3571)
                                                            {
                                                                _3790 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + 7u) & 31u)) & 1u) != 0u;
                                                            }
                                                            else
                                                            {
                                                                _3790 = false;
                                                            }
                                                            _3791 = _3790;
                                                            _3792 = true;
                                                            break;
                                                        }
                                                        case 3u:
                                                        {
                                                            _3791 = true;
                                                            _3792 = true;
                                                            break;
                                                        }
                                                        default:
                                                        {
                                                            _3791 = _179;
                                                            _3792 = false;
                                                            break;
                                                        }
                                                    }
                                                    if (_3792)
                                                    {
                                                        _3794 = _3791;
                                                        break;
                                                    }
                                                    _3794 = false;
                                                    break;
                                                } while(false);
                                                _3795 = uvec4((_3754 & 31u) << 3u, ((_3754 >> 5u) & 31u) << 3u, ((_3754 >> 10u) & 31u) << 3u, _3764);
                                                _3796 = _3794;
                                                _3797 = _178;
                                                _3798 = _3769;
                                            }
                                            else
                                            {
                                                uvec4 _3726;
                                                bool _3727;
                                                bool _3728;
                                                if (_3619)
                                                {
                                                    uint _3664 = _3636 + (_3643 << 2u);
                                                    uint _3688;
                                                    if (((_2471 >> ((_3664 >> 17u) & 3u)) & 1u) != 0u)
                                                    {
                                                        uint _3674 = (_3664 & 524284u) >> 2u;
                                                        _3688 = ((((g_vram._m0[_3674] >> 24u) & 255u) | ((g_vram._m0[_3674] >> 8u) & 65280u)) | ((g_vram._m0[_3674] << 8u) & 16711680u)) | ((g_vram._m0[_3674] << 24u) & 4278190080u);
                                                    }
                                                    else
                                                    {
                                                        _3688 = 0u;
                                                    }
                                                    uint _3695 = (_3688 >> 31u) & 1u;
                                                    bool _3700;
                                                    if (_2478)
                                                    {
                                                        _3700 = _3695 == 0u;
                                                    }
                                                    else
                                                    {
                                                        _3700 = false;
                                                    }
                                                    bool _3725;
                                                    do
                                                    {
                                                        if (!_2480)
                                                        {
                                                            _3725 = false;
                                                            break;
                                                        }
                                                        bool _3722;
                                                        bool _3723;
                                                        switch (_2477)
                                                        {
                                                            case 0u:
                                                            {
                                                                _3722 = _2480;
                                                                _3723 = true;
                                                                break;
                                                            }
                                                            case 1u:
                                                            {
                                                                _3722 = _3571;
                                                                _3723 = true;
                                                                break;
                                                            }
                                                            case 2u:
                                                            {
                                                                bool _3721;
                                                                if (_3571)
                                                                {
                                                                    _3721 = ((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + 7u) & 31u)) & 1u) != 0u;
                                                                }
                                                                else
                                                                {
                                                                    _3721 = false;
                                                                }
                                                                _3722 = _3721;
                                                                _3723 = true;
                                                                break;
                                                            }
                                                            case 3u:
                                                            {
                                                                _3722 = true;
                                                                _3723 = true;
                                                                break;
                                                            }
                                                            default:
                                                            {
                                                                _3722 = _179;
                                                                _3723 = false;
                                                                break;
                                                            }
                                                        }
                                                        if (_3723)
                                                        {
                                                            _3725 = _3722;
                                                            break;
                                                        }
                                                        _3725 = false;
                                                        break;
                                                    } while(false);
                                                    _3726 = uvec4(_3688 & 255u, (_3688 >> 8u) & 255u, (_3688 >> 16u) & 255u, _3695);
                                                    _3727 = _3725;
                                                    _3728 = _3700;
                                                }
                                                else
                                                {
                                                    _3726 = uvec4(0u);
                                                    _3727 = false;
                                                    _3728 = true;
                                                }
                                                _3795 = _3726;
                                                _3796 = _3727;
                                                _3797 = _3619 ? _178 : 0u;
                                                _3798 = _3728;
                                            }
                                            _3863 = _3795;
                                            _3864 = _3796;
                                            _3865 = _3797;
                                            _3866 = _3798;
                                        }
                                        _3926 = _3863;
                                        _3927 = _3864;
                                        _3928 = _3865;
                                        _3929 = _3866;
                                    }
                                    _3993 = _3926;
                                    _3994 = _3927;
                                    _3995 = _3928;
                                    _3996 = _3929;
                                }
                                if (_3996)
                                {
                                    _4041 = uvec4(0u);
                                    break;
                                }
                                uint _4027;
                                if (_2443 == 1u)
                                {
                                    _4027 = (_2441 & 4294967294u) | uint(_3572);
                                }
                                else
                                {
                                    uint _4024;
                                    if (_2443 == 2u)
                                    {
                                        uint _4006 = _2441 & 4294967294u;
                                        bool _4010;
                                        if (_3572)
                                        {
                                            _4010 = _2449 < 3u;
                                        }
                                        else
                                        {
                                            _4010 = false;
                                        }
                                        uint _4023;
                                        if (_4010)
                                        {
                                            _4023 = _4006 | uint(((g_layerRenderParams._m0[0u].specialFunctionCodes >> (((_2445 * 8u) + _3995) & 31u)) & 1u) != 0u);
                                        }
                                        else
                                        {
                                            _4023 = _4006;
                                        }
                                        _4024 = _4023;
                                    }
                                    else
                                    {
                                        _4024 = _2441;
                                    }
                                    _4027 = _4024;
                                }
                                bool _4028 = _2449 <= 2u;
                                uint _4030 = 64u;
                                uint _4031 = 0u;
                                _4041 = uvec4(_3993.xyz, ((_4028 ? 64u : 0u) | uint(_3994 ? 128 : 0)) | _4027);
                                break;
                            } while(false);
                            _4042 = _4041;
                            break;
                        }
                        _4042 = uvec4(0u);
                        break;
                    } while(false);
                    _4722 = _4042;
                }
                _4723 = _4722;
                break;
            } while(false);
            imageStore(g_layerOut, ivec3(_274), _4723);
        }
        else
        {
            if (gl_GlobalInvocationID.z == 6u)
            {
                bool _290 = ((CommonRenderParamsBuffer.g_commonParams.windows >> 5u) & 1u) != 0u;
                bool _293 = ((CommonRenderParamsBuffer.g_commonParams.windows >> 6u) & 1u) != 0u;
                bool _299 = ((CommonRenderParamsBuffer.g_commonParams.windows >> 8u) & 1u) != 0u;
                bool _305 = ((CommonRenderParamsBuffer.g_commonParams.windows >> 10u) & 1u) != 0u;
                uint _320;
                do
                {
                    bool _314;
                    if (_261)
                    {
                        _314 = !_210;
                    }
                    else
                    {
                        _314 = false;
                    }
                    if (_314)
                    {
                        _320 = (_255 << 1u) | _207;
                        break;
                    }
                    else
                    {
                        _320 = _255;
                        break;
                    }
                    break; // unreachable workaround
                } while(false);
                uvec2 _321 = _256;
                _321.y = _320;
                bool _575;
                do
                {
                    bool _328;
                    if (!_293)
                    {
                        _328 = !_299;
                    }
                    else
                    {
                        _328 = false;
                    }
                    bool _332;
                    if (_328)
                    {
                        _332 = !_305;
                    }
                    else
                    {
                        _332 = false;
                    }
                    if (_332)
                    {
                        _575 = false;
                        break;
                    }
                    bool _445;
                    if (_293)
                    {
                        ivec2 _346 = ivec2(g_layerRenderParams._m0[0u].windows[0].start);
                        ivec2 _347 = ivec2(g_layerRenderParams._m0[0u].windows[0].end);
                        ivec2 _385;
                        ivec2 _386;
                        if (g_layerRenderParams._m0[0u].windows[0].lineWindowTableEnable != 0u)
                        {
                            uint _351 = g_layerRenderParams._m0[0u].windows[0].lineWindowTableAddress + (_320 * 4u);
                            uint _360 = (g_vram._m0[(_351 & 524284u) >> 2u] >> (((_351 & 2u) * 8u) & 31u)) & 65535u;
                            ivec2 _367 = _346;
                            _367.x = int(((_360 >> 8u) & 255u) | ((_360 << 8u) & 65280u));
                            uint _368 = _351 + 2u;
                            uint _377 = (g_vram._m0[(_368 & 524284u) >> 2u] >> (((_368 & 2u) * 8u) & 31u)) & 65535u;
                            ivec2 _384 = _347;
                            _384.x = int(((_377 >> 8u) & 255u) | ((_377 << 8u) & 65280u));
                            _385 = _384;
                            _386 = _367;
                        }
                        else
                        {
                            _385 = _347;
                            _386 = _346;
                        }
                        int _389 = (_386.x << 16) >> 16;
                        int _392 = (_385.x << 16) >> 16;
                        ivec2 _396 = ivec2(_389, (_386.y << 16) >> 16);
                        ivec2 _400 = ivec2(_392, (_385.y << 16) >> 16);
                        ivec2 _405;
                        if (_389 < 0)
                        {
                            ivec2 _404 = _396;
                            _404.x = 0;
                            _405 = _404;
                        }
                        else
                        {
                            _405 = _396;
                        }
                        ivec2 _416;
                        ivec2 _417;
                        if (_392 < 0)
                        {
                            ivec2 _414;
                            if (_405.x >= _392)
                            {
                                ivec2 _413 = _405;
                                _413.x = 1023;
                                _414 = _413;
                            }
                            else
                            {
                                _414 = _405;
                            }
                            ivec2 _415 = _400;
                            _415.x = 0;
                            _416 = _415;
                            _417 = _414;
                        }
                        else
                        {
                            _416 = _400;
                            _417 = _405;
                        }
                        ivec2 _427;
                        ivec2 _428;
                        if (!_213)
                        {
                            ivec2 _423 = _417;
                            _423.x = _417.x >> 1;
                            ivec2 _426 = _416;
                            _426.x = _416.x >> 1;
                            _427 = _426;
                            _428 = _423;
                        }
                        else
                        {
                            _427 = _416;
                            _428 = _417;
                        }
                        ivec2 _429 = ivec2(_321);
                        bool _436;
                        if (all(greaterThanEqual(_429, _428)))
                        {
                            _436 = all(lessThanEqual(_429, _427));
                        }
                        else
                        {
                            _436 = false;
                        }
                        bool _437 = _436 != (((CommonRenderParamsBuffer.g_commonParams.windows >> 7u) & 1u) != 0u);
                        bool _444;
                        if (_290)
                        {
                            _444 = _290 ? _437 : false;
                        }
                        else
                        {
                            _444 = (!_290) ? _437 : true;
                        }
                        _445 = _444;
                    }
                    else
                    {
                        _445 = _290;
                    }
                    bool _556;
                    if (_299)
                    {
                        ivec2 _457 = ivec2(g_layerRenderParams._m0[0u].windows[1].start);
                        ivec2 _458 = ivec2(g_layerRenderParams._m0[0u].windows[1].end);
                        ivec2 _496;
                        ivec2 _497;
                        if (g_layerRenderParams._m0[0u].windows[1].lineWindowTableEnable != 0u)
                        {
                            uint _462 = g_layerRenderParams._m0[0u].windows[1].lineWindowTableAddress + (_320 * 4u);
                            uint _471 = (g_vram._m0[(_462 & 524284u) >> 2u] >> (((_462 & 2u) * 8u) & 31u)) & 65535u;
                            ivec2 _478 = _457;
                            _478.x = int(((_471 >> 8u) & 255u) | ((_471 << 8u) & 65280u));
                            uint _479 = _462 + 2u;
                            uint _488 = (g_vram._m0[(_479 & 524284u) >> 2u] >> (((_479 & 2u) * 8u) & 31u)) & 65535u;
                            ivec2 _495 = _458;
                            _495.x = int(((_488 >> 8u) & 255u) | ((_488 << 8u) & 65280u));
                            _496 = _495;
                            _497 = _478;
                        }
                        else
                        {
                            _496 = _458;
                            _497 = _457;
                        }
                        int _500 = (_497.x << 16) >> 16;
                        int _503 = (_496.x << 16) >> 16;
                        ivec2 _507 = ivec2(_500, (_497.y << 16) >> 16);
                        ivec2 _511 = ivec2(_503, (_496.y << 16) >> 16);
                        ivec2 _516;
                        if (_500 < 0)
                        {
                            ivec2 _515 = _507;
                            _515.x = 0;
                            _516 = _515;
                        }
                        else
                        {
                            _516 = _507;
                        }
                        ivec2 _527;
                        ivec2 _528;
                        if (_503 < 0)
                        {
                            ivec2 _525;
                            if (_516.x >= _503)
                            {
                                ivec2 _524 = _516;
                                _524.x = 1023;
                                _525 = _524;
                            }
                            else
                            {
                                _525 = _516;
                            }
                            ivec2 _526 = _511;
                            _526.x = 0;
                            _527 = _526;
                            _528 = _525;
                        }
                        else
                        {
                            _527 = _511;
                            _528 = _516;
                        }
                        ivec2 _538;
                        ivec2 _539;
                        if (!_213)
                        {
                            ivec2 _534 = _528;
                            _534.x = _528.x >> 1;
                            ivec2 _537 = _527;
                            _537.x = _527.x >> 1;
                            _538 = _537;
                            _539 = _534;
                        }
                        else
                        {
                            _538 = _527;
                            _539 = _528;
                        }
                        ivec2 _540 = ivec2(_321);
                        bool _547;
                        if (all(greaterThanEqual(_540, _539)))
                        {
                            _547 = all(lessThanEqual(_540, _538));
                        }
                        else
                        {
                            _547 = false;
                        }
                        bool _548 = _547 != (((CommonRenderParamsBuffer.g_commonParams.windows >> 9u) & 1u) != 0u);
                        bool _555;
                        if (_290)
                        {
                            _555 = _445 ? _548 : false;
                        }
                        else
                        {
                            _555 = (!_445) ? _548 : true;
                        }
                        _556 = _555;
                    }
                    else
                    {
                        _556 = _445;
                    }
                    bool _574;
                    if (_305)
                    {
                        bool _566 = (((texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(gl_GlobalInvocationID.x, _320, 0u)), int(0u)).x >> 6u) & 1u) != 0u) != (((CommonRenderParamsBuffer.g_commonParams.windows >> 11u) & 1u) != 0u);
                        bool _573;
                        if (_290)
                        {
                            _573 = _556 ? _566 : false;
                        }
                        else
                        {
                            _573 = (!_556) ? _566 : true;
                        }
                        _574 = _573;
                    }
                    else
                    {
                        _574 = _556;
                    }
                    _575 = _574;
                    break;
                } while(false);
                imageStore(g_colorCalcWindowOut, ivec2(_274.xy), uvec4(uint(_575)));
            }
        }
    }
}

