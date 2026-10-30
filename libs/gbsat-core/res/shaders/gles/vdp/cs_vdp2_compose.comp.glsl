#version 310 es
#extension GL_EXT_texture_buffer : require
#if defined(GL_EXT_control_flow_attributes)
#extension GL_EXT_control_flow_attributes : require
#define SPIRV_CROSS_FLATTEN [[flatten]]
#define SPIRV_CROSS_BRANCH [[dont_flatten]]
#define SPIRV_CROSS_UNROLL [[unroll]]
#define SPIRV_CROSS_LOOP [[dont_unroll]]
#else
#define SPIRV_CROSS_FLATTEN
#define SPIRV_CROSS_BRANCH
#define SPIRV_CROSS_UNROLL
#define SPIRV_CROSS_LOOP
#endif
layout(local_size_x = 32, local_size_y = 1, local_size_z = 1) in;

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

struct ComposeParams
{
    uint colorCalcEnable;
    uint colorOffsetEnable;
    uint colorOffsetSelect;
    uint lineColorEnable;
    ivec3 colorOffsetA;
    ivec3 colorOffsetB;
    uint bgColorCalcRatios[5];
    uint backLineColorCalcRatios[2];
};

bool _92;
uvec3 _93;

layout(binding = 0, std140) uniform type_CommonRenderParams
{
    CommonRenderParams g_commonParams;
} CommonRenderParams_1;

layout(binding = 1, std430) readonly buffer type_StructuredBuffer_ComposeParams
{
    ComposeParams _m0[];
} g_composeParams;

layout(binding = 3) uniform highp usamplerBuffer g_lnclBackIn;
layout(binding = 17, rgba8) uniform writeonly highp image2D g_compositeOut;
uniform highp usampler2DArray SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler;
uniform highp usampler2DArray SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler;
uniform highp usampler2D SPIRV_Cross_Combinedg_colorCalcWindowInSPIRV_Cross_DummySampler;
uniform highp usampler2DArray SPIRV_Cross_Combinedg_rbgLineColorInSPIRV_Cross_DummySampler;

void main()
{
    bool _150;
    uint _104 = (CommonRenderParams_1.g_commonParams.displayParams >> 2u) & 3u;
    uint _106 = (CommonRenderParams_1.g_commonParams.displayParams >> 4u) & 1u;
    bool _109 = ((CommonRenderParams_1.g_commonParams.displayParams >> 5u) & 1u) != 0u;
    uint _112 = CommonRenderParams_1.g_commonParams.displayParams >> 10u;
    bool _114 = (_112 & 2u) != 0u;
    bool _116 = (_112 & 7u) < 2u;
    bool _128 = (CommonRenderParams_1.g_commonParams.enhancements & 1u) != 0u;
    bool _131 = ((CommonRenderParams_1.g_commonParams.enhancements >> 1u) & 1u) != 0u;
    uint _134 = max(((CommonRenderParams_1.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uvec2 _143 = uvec2(gl_GlobalInvocationID.x, gl_GlobalInvocationID.y + (CommonRenderParams_1.g_commonParams.startY * _134));
    uvec2 _145 = _143 / uvec2(_134);
    uint _146 = _145.x;
    uint _147 = _145.y;
    uint _164;
    do
    {
        _150 = !_128;
        bool _158;
        if (_150)
        {
            bool _157;
            if (_104 >= 2u)
            {
                _157 = !_109;
            }
            else
            {
                _157 = false;
            }
            _158 = _157;
        }
        else
        {
            _158 = false;
        }
        if (_158)
        {
            _164 = (_147 << 1u) | _106;
            break;
        }
        else
        {
            _164 = _147;
            break;
        }
        break; // unreachable workaround
    } while(false);
    uvec2 _168 = mix(uvec2(_146, _164), _143, bvec2(_134 > 1u));
    uvec3 _1854;
    do
    {
        uint _186;
        do
        {
            bool _180;
            if (_150)
            {
                bool _179;
                if (_104 >= 2u)
                {
                    _179 = !_109;
                }
                else
                {
                    _179 = false;
                }
                _180 = _179;
            }
            else
            {
                _180 = false;
            }
            if (_180)
            {
                _186 = (_147 << 1u) | _106;
                break;
            }
            else
            {
                _186 = _147;
                break;
            }
            break; // unreachable workaround
        } while(false);
        uvec2 _187 = uvec2(_146, _186);
        if ((CommonRenderParams_1.g_commonParams.displayParams & 1u) == 0u)
        {
            if (((CommonRenderParams_1.g_commonParams.displayParams >> 1u) & 1u) != 0u)
            {
                uint _216;
                do
                {
                    bool _202;
                    if (_128)
                    {
                        _202 = _104 == 2u;
                    }
                    else
                    {
                        _202 = false;
                    }
                    bool _207;
                    if (!_202)
                    {
                        _207 = _104 == 3u;
                    }
                    else
                    {
                        _207 = true;
                    }
                    bool _211;
                    if (_207)
                    {
                        _211 = !_109;
                    }
                    else
                    {
                        _211 = false;
                    }
                    if (_211)
                    {
                        _216 = _186 >> 1u;
                        break;
                    }
                    else
                    {
                        _216 = _186;
                        break;
                    }
                    break; // unreachable workaround
                } while(false);
                _1854 = texelFetch(g_lnclBackIn, int(_216 + 256u)).xyz;
                break;
            }
            _1854 = uvec3(0u);
            break;
        }
        uint _222;
        uint _225;
        uint _227;
        uint _229;
        uint _231;
        uint _233;
        _222 = 0u;
        _225 = 0u;
        _227 = 0u;
        _229 = 6u;
        _231 = 6u;
        _233 = 6u;
        uint _223;
        uint _226;
        uint _228;
        uint _230;
        uint _232;
        uint _234;
        SPIRV_CROSS_UNROLL
        for (uint _235 = 0u; _235 < 6u; _222 = _223, _225 = _226, _227 = _228, _229 = _230, _231 = _232, _233 = _234, _235++)
        {
            if (((CommonRenderParams_1.g_commonParams.layerParams >> (_235 & 31u)) & 1u) == 0u)
            {
                _223 = _222;
                _226 = _225;
                _228 = _227;
                _230 = _229;
                _232 = _231;
                _234 = _233;
                continue;
            }
            uvec4 _338;
            switch (_235)
            {
                case 0u:
                {
                }
                case 8u:
                {
                    uint _332;
                    switch (_235)
                    {
                        case 0u:
                        {
                            _332 = 6u;
                            break;
                        }
                        case 8u:
                        {
                            _332 = 7u;
                            break;
                        }
                        case 1u:
                        {
                            _332 = 4u;
                            break;
                        }
                        case 2u:
                        {
                            _332 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                            break;
                        }
                        case 3u:
                        {
                            _332 = 1u;
                            break;
                        }
                        case 4u:
                        {
                            _332 = 2u;
                            break;
                        }
                        case 5u:
                        {
                            _332 = 3u;
                            break;
                        }
                        default:
                        {
                            _332 = 8u;
                            break;
                        }
                    }
                    _338 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_168, _332)), int(0u));
                    break;
                }
                case 1u:
                {
                }
                case 2u:
                {
                }
                case 3u:
                {
                }
                case 4u:
                {
                }
                case 5u:
                {
                    uint _315;
                    switch (_235)
                    {
                        case 0u:
                        {
                            _315 = 6u;
                            break;
                        }
                        case 8u:
                        {
                            _315 = 7u;
                            break;
                        }
                        case 1u:
                        {
                            _315 = 4u;
                            break;
                        }
                        case 2u:
                        {
                            _315 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                            break;
                        }
                        case 3u:
                        {
                            _315 = 1u;
                            break;
                        }
                        case 4u:
                        {
                            _315 = 2u;
                            break;
                        }
                        case 5u:
                        {
                            _315 = 3u;
                            break;
                        }
                        default:
                        {
                            _315 = 8u;
                            break;
                        }
                    }
                    _338 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_146, _186, _315)), int(0u));
                    break;
                }
                case 6u:
                {
                    uint _298;
                    do
                    {
                        bool _284;
                        if (_128)
                        {
                            _284 = _104 == 2u;
                        }
                        else
                        {
                            _284 = false;
                        }
                        bool _289;
                        if (!_284)
                        {
                            _289 = _104 == 3u;
                        }
                        else
                        {
                            _289 = true;
                        }
                        bool _293;
                        if (_289)
                        {
                            _293 = !_109;
                        }
                        else
                        {
                            _293 = false;
                        }
                        if (_293)
                        {
                            _298 = _186 >> 1u;
                            break;
                        }
                        else
                        {
                            _298 = _186;
                            break;
                        }
                        break; // unreachable workaround
                    } while(false);
                    _338 = texelFetch(g_lnclBackIn, int(_298 + 256u));
                    break;
                }
                case 7u:
                {
                    uint _276;
                    do
                    {
                        bool _262;
                        if (_128)
                        {
                            _262 = _104 == 2u;
                        }
                        else
                        {
                            _262 = false;
                        }
                        bool _267;
                        if (!_262)
                        {
                            _267 = _104 == 3u;
                        }
                        else
                        {
                            _267 = true;
                        }
                        bool _271;
                        if (_267)
                        {
                            _271 = !_109;
                        }
                        else
                        {
                            _271 = false;
                        }
                        if (_271)
                        {
                            _276 = _186 >> 1u;
                            break;
                        }
                        else
                        {
                            _276 = _186;
                            break;
                        }
                        break; // unreachable workaround
                    } while(false);
                    _338 = texelFetch(g_lnclBackIn, int(_276));
                    break;
                }
                default:
                {
                    _338 = uvec4(0u);
                    break;
                }
            }
            uint _340 = _338.w & 7u;
            if (_340 == 0u)
            {
                _223 = _222;
                _226 = _225;
                _228 = _227;
                _230 = _229;
                _232 = _231;
                _234 = _233;
                continue;
            }
            if (_235 == 0u)
            {
                if (((texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(_168, 0u)), int(0u)).x >> 7u) & 3u) != 0u)
                {
                    _223 = _222;
                    _226 = _225;
                    _228 = _227;
                    _230 = _229;
                    _232 = _231;
                    _234 = _233;
                    continue;
                }
            }
            bool _366;
            if (_340 <= _227)
            {
                bool _365;
                if (_340 == _227)
                {
                    _365 = _235 < _233;
                }
                else
                {
                    _365 = false;
                }
                _366 = _365;
            }
            else
            {
                _366 = true;
            }
            uint _397;
            uint _398;
            uint _399;
            uint _400;
            if (_366)
            {
                _397 = _225;
                _398 = _227;
                _399 = _231;
                _400 = _233;
            }
            else
            {
                bool _378;
                if (_340 <= _225)
                {
                    bool _377;
                    if (_340 == _225)
                    {
                        _377 = _235 < _231;
                    }
                    else
                    {
                        _377 = false;
                    }
                    _378 = _377;
                }
                else
                {
                    _378 = true;
                }
                uint _393;
                uint _394;
                if (_378)
                {
                    _393 = _225;
                    _394 = _231;
                }
                else
                {
                    bool _390;
                    if (_340 <= _222)
                    {
                        bool _389;
                        if (_340 == _222)
                        {
                            _389 = _235 < _229;
                        }
                        else
                        {
                            _389 = false;
                        }
                        _390 = _389;
                    }
                    else
                    {
                        _390 = true;
                    }
                    _393 = _390 ? _340 : _222;
                    _394 = _390 ? _235 : _229;
                }
                _397 = _393;
                _398 = _378 ? _340 : _225;
                _399 = _394;
                _400 = _378 ? _235 : _231;
            }
            _223 = _397;
            _226 = _398;
            _228 = _366 ? _340 : _227;
            _230 = _399;
            _232 = _400;
            _234 = _366 ? _235 : _233;
        }
        uint _100[3] = uint[](_227, _225, _222);
        bool _408;
        if (_131)
        {
            _408 = (CommonRenderParams_1.g_commonParams.layerParams & 1u) != 0u;
        }
        else
        {
            _408 = false;
        }
        uvec3 _446;
        uint _447;
        if (_408)
        {
            uint _411 = _168.x;
            uvec4 _415 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_411, _168.y, 7u)), int(0u));
            uint _418 = _415.w & 7u;
            bool _429;
            if (_418 > 0u)
            {
                _429 = ((texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(_411, _168.y, 1u)), int(0u)).x >> 7u) & 3u) != 1u;
            }
            else
            {
                _429 = false;
            }
            uint _445;
            if (_429)
            {
                uint _444;
                uint _433 = 0u;
                for (;;)
                {
                    if (_433 < 3u)
                    {
                        if (_418 >= _100[_433])
                        {
                            _444 = _433;
                            break;
                        }
                        _433++;
                        continue;
                    }
                    else
                    {
                        _444 = 255u;
                        break;
                    }
                }
                _445 = _444;
            }
            else
            {
                _445 = 255u;
            }
            _446 = _415.xyz;
            _447 = _445;
        }
        else
        {
            _446 = _93;
            _447 = 255u;
        }
        bool _451;
        if (_116)
        {
            _451 = ((CommonRenderParams_1.g_commonParams.displayParams >> 6u) & 3u) == 0u;
        }
        else
        {
            _451 = false;
        }
        bool _452 = _451 ? (((CommonRenderParams_1.g_commonParams.layerParams >> 28u) & 1u) != 0u) : false;
        bool _466;
        if (!_452)
        {
            bool _465;
            if (_233 < 16u)
            {
                _465 = ((g_composeParams._m0[0u].lineColorEnable >> (_233 & 31u)) & 1u) != 0u;
            }
            else
            {
                _465 = false;
            }
            _466 = _465;
        }
        else
        {
            _466 = false;
        }
        uvec4 _562;
        switch (_233)
        {
            case 0u:
            {
            }
            case 8u:
            {
                uint _556;
                switch (_233)
                {
                    case 0u:
                    {
                        _556 = 6u;
                        break;
                    }
                    case 8u:
                    {
                        _556 = 7u;
                        break;
                    }
                    case 1u:
                    {
                        _556 = 4u;
                        break;
                    }
                    case 2u:
                    {
                        _556 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                        break;
                    }
                    case 3u:
                    {
                        _556 = 1u;
                        break;
                    }
                    case 4u:
                    {
                        _556 = 2u;
                        break;
                    }
                    case 5u:
                    {
                        _556 = 3u;
                        break;
                    }
                    default:
                    {
                        _556 = 8u;
                        break;
                    }
                }
                _562 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_168, _556)), int(0u));
                break;
            }
            case 1u:
            {
            }
            case 2u:
            {
            }
            case 3u:
            {
            }
            case 4u:
            {
            }
            case 5u:
            {
                uint _539;
                switch (_233)
                {
                    case 0u:
                    {
                        _539 = 6u;
                        break;
                    }
                    case 8u:
                    {
                        _539 = 7u;
                        break;
                    }
                    case 1u:
                    {
                        _539 = 4u;
                        break;
                    }
                    case 2u:
                    {
                        _539 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                        break;
                    }
                    case 3u:
                    {
                        _539 = 1u;
                        break;
                    }
                    case 4u:
                    {
                        _539 = 2u;
                        break;
                    }
                    case 5u:
                    {
                        _539 = 3u;
                        break;
                    }
                    default:
                    {
                        _539 = 8u;
                        break;
                    }
                }
                _562 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_146, _186, _539)), int(0u));
                break;
            }
            case 6u:
            {
                uint _522;
                do
                {
                    bool _508;
                    if (_128)
                    {
                        _508 = _104 == 2u;
                    }
                    else
                    {
                        _508 = false;
                    }
                    bool _513;
                    if (!_508)
                    {
                        _513 = _104 == 3u;
                    }
                    else
                    {
                        _513 = true;
                    }
                    bool _517;
                    if (_513)
                    {
                        _517 = !_109;
                    }
                    else
                    {
                        _517 = false;
                    }
                    if (_517)
                    {
                        _522 = _186 >> 1u;
                        break;
                    }
                    else
                    {
                        _522 = _186;
                        break;
                    }
                    break; // unreachable workaround
                } while(false);
                _562 = texelFetch(g_lnclBackIn, int(_522 + 256u));
                break;
            }
            case 7u:
            {
                uint _500;
                do
                {
                    bool _486;
                    if (_128)
                    {
                        _486 = _104 == 2u;
                    }
                    else
                    {
                        _486 = false;
                    }
                    bool _491;
                    if (!_486)
                    {
                        _491 = _104 == 3u;
                    }
                    else
                    {
                        _491 = true;
                    }
                    bool _495;
                    if (_491)
                    {
                        _495 = !_109;
                    }
                    else
                    {
                        _495 = false;
                    }
                    if (_495)
                    {
                        _500 = _186 >> 1u;
                        break;
                    }
                    else
                    {
                        _500 = _186;
                        break;
                    }
                    break; // unreachable workaround
                } while(false);
                _562 = texelFetch(g_lnclBackIn, int(_500));
                break;
            }
            default:
            {
                _562 = uvec4(0u);
                break;
            }
        }
        uint _568;
        bool _658;
        do
        {
            _568 = _233 & 31u;
            bool _571 = ((g_composeParams._m0[0u].colorCalcEnable >> _568) & 1u) != 0u;
            if (_233 >= 6u)
            {
                _658 = _571;
                break;
            }
            if (!_571)
            {
                _658 = false;
                break;
            }
            if (texelFetch(SPIRV_Cross_Combinedg_colorCalcWindowInSPIRV_Cross_DummySampler, ivec2(_187), int(0u)).x != 0u)
            {
                _658 = false;
                break;
            }
            bool _586 = ((CommonRenderParams_1.g_commonParams.layerParams >> 24u) & 1u) != 0u;
            if (_233 == 0u)
            {
                uint _590 = _168.x;
                uvec4 _594 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_590, _168.y, 6u)), int(0u));
                uint _595 = _594.w;
                bool _601;
                if (_586)
                {
                    _601 = ((_595 >> 7u) & 1u) != 0u;
                }
                else
                {
                    _601 = false;
                }
                if (_601)
                {
                    _658 = false;
                    break;
                }
                uint _608 = _595 & 7u;
                uint _612 = (CommonRenderParams_1.g_commonParams.spriteParams >> 12u) & 7u;
                bool _626;
                bool _627;
                switch ((CommonRenderParams_1.g_commonParams.spriteParams >> 15u) & 3u)
                {
                    case 0u:
                    {
                        _626 = _608 <= _612;
                        _627 = true;
                        break;
                    }
                    case 1u:
                    {
                        _626 = _608 == _612;
                        _627 = true;
                        break;
                    }
                    case 2u:
                    {
                        _626 = _608 >= _612;
                        _627 = true;
                        break;
                    }
                    case 3u:
                    {
                        _626 = ((texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(_590, _168.y, 0u)), int(0u)).x >> 5u) & 1u) != 0u;
                        _627 = true;
                        break;
                    }
                    default:
                    {
                        _626 = _92;
                        _627 = false;
                        break;
                    }
                }
                if (_627)
                {
                    _658 = _626;
                    break;
                }
                _658 = false;
                break;
            }
            uint _642;
            switch (_233)
            {
                case 0u:
                {
                    _642 = 6u;
                    break;
                }
                case 8u:
                {
                    _642 = 7u;
                    break;
                }
                case 1u:
                {
                    _642 = 4u;
                    break;
                }
                case 2u:
                {
                    _642 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                    break;
                }
                case 3u:
                {
                    _642 = 1u;
                    break;
                }
                case 4u:
                {
                    _642 = 2u;
                    break;
                }
                case 5u:
                {
                    _642 = 3u;
                    break;
                }
                default:
                {
                    _642 = 8u;
                    break;
                }
            }
            uvec4 _645 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_146, _186, _642)), int(0u));
            uint _646 = _645.w;
            bool _652;
            if (_586)
            {
                _652 = ((_646 >> 6u) & 1u) != 0u;
            }
            else
            {
                _652 = false;
            }
            if (_652)
            {
                _658 = false;
                break;
            }
            _658 = ((_646 >> 7u) & 1u) != 0u;
            break;
        } while(false);
        uint _1587;
        uvec3 _1588;
        if (_658)
        {
            uvec4 _754;
            switch (_231)
            {
                case 0u:
                {
                }
                case 8u:
                {
                    uint _748;
                    switch (_231)
                    {
                        case 0u:
                        {
                            _748 = 6u;
                            break;
                        }
                        case 8u:
                        {
                            _748 = 7u;
                            break;
                        }
                        case 1u:
                        {
                            _748 = 4u;
                            break;
                        }
                        case 2u:
                        {
                            _748 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                            break;
                        }
                        case 3u:
                        {
                            _748 = 1u;
                            break;
                        }
                        case 4u:
                        {
                            _748 = 2u;
                            break;
                        }
                        case 5u:
                        {
                            _748 = 3u;
                            break;
                        }
                        default:
                        {
                            _748 = 8u;
                            break;
                        }
                    }
                    _754 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_168, _748)), int(0u));
                    break;
                }
                case 1u:
                {
                }
                case 2u:
                {
                }
                case 3u:
                {
                }
                case 4u:
                {
                }
                case 5u:
                {
                    uint _731;
                    switch (_231)
                    {
                        case 0u:
                        {
                            _731 = 6u;
                            break;
                        }
                        case 8u:
                        {
                            _731 = 7u;
                            break;
                        }
                        case 1u:
                        {
                            _731 = 4u;
                            break;
                        }
                        case 2u:
                        {
                            _731 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                            break;
                        }
                        case 3u:
                        {
                            _731 = 1u;
                            break;
                        }
                        case 4u:
                        {
                            _731 = 2u;
                            break;
                        }
                        case 5u:
                        {
                            _731 = 3u;
                            break;
                        }
                        default:
                        {
                            _731 = 8u;
                            break;
                        }
                    }
                    _754 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_146, _186, _731)), int(0u));
                    break;
                }
                case 6u:
                {
                    uint _714;
                    do
                    {
                        bool _700;
                        if (_128)
                        {
                            _700 = _104 == 2u;
                        }
                        else
                        {
                            _700 = false;
                        }
                        bool _705;
                        if (!_700)
                        {
                            _705 = _104 == 3u;
                        }
                        else
                        {
                            _705 = true;
                        }
                        bool _709;
                        if (_705)
                        {
                            _709 = !_109;
                        }
                        else
                        {
                            _709 = false;
                        }
                        if (_709)
                        {
                            _714 = _186 >> 1u;
                            break;
                        }
                        else
                        {
                            _714 = _186;
                            break;
                        }
                        break; // unreachable workaround
                    } while(false);
                    _754 = texelFetch(g_lnclBackIn, int(_714 + 256u));
                    break;
                }
                case 7u:
                {
                    uint _692;
                    do
                    {
                        bool _678;
                        if (_128)
                        {
                            _678 = _104 == 2u;
                        }
                        else
                        {
                            _678 = false;
                        }
                        bool _683;
                        if (!_678)
                        {
                            _683 = _104 == 3u;
                        }
                        else
                        {
                            _683 = true;
                        }
                        bool _687;
                        if (_683)
                        {
                            _687 = !_109;
                        }
                        else
                        {
                            _687 = false;
                        }
                        if (_687)
                        {
                            _692 = _186 >> 1u;
                            break;
                        }
                        else
                        {
                            _692 = _186;
                            break;
                        }
                        break; // unreachable workaround
                    } while(false);
                    _754 = texelFetch(g_lnclBackIn, int(_692));
                    break;
                }
                default:
                {
                    _754 = uvec4(0u);
                    break;
                }
            }
            uint _1421;
            uvec3 _1422;
            if (_452)
            {
                uint _1118;
                switch ((CommonRenderParams_1.g_commonParams.layerParams >> 29u) & 7u)
                {
                    case 2u:
                    {
                        _1118 = 2u;
                        break;
                    }
                    case 4u:
                    {
                        _1118 = 3u;
                        break;
                    }
                    case 5u:
                    {
                        _1118 = 4u;
                        break;
                    }
                    case 6u:
                    {
                        _1118 = 5u;
                        break;
                    }
                    case 1u:
                    {
                        _1118 = 1u;
                        break;
                    }
                    case 0u:
                    {
                        _1118 = 0u;
                        break;
                    }
                    default:
                    {
                        _1118 = 6u;
                        break;
                    }
                }
                bool _1123;
                if (_233 != _1118)
                {
                    _1123 = _231 == _1118;
                }
                else
                {
                    _1123 = true;
                }
                uvec3 _1419;
                if (_1123)
                {
                    int _1126 = int(_146);
                    uvec4 _1222;
                    switch (_1118)
                    {
                        case 0u:
                        {
                        }
                        case 8u:
                        {
                            uint _1216;
                            switch (_1118)
                            {
                                case 0u:
                                {
                                    _1216 = 6u;
                                    break;
                                }
                                case 8u:
                                {
                                    _1216 = 7u;
                                    break;
                                }
                                case 1u:
                                {
                                    _1216 = 4u;
                                    break;
                                }
                                case 2u:
                                {
                                    _1216 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                    break;
                                }
                                case 3u:
                                {
                                    _1216 = 1u;
                                    break;
                                }
                                case 4u:
                                {
                                    _1216 = 2u;
                                    break;
                                }
                                case 5u:
                                {
                                    _1216 = 3u;
                                    break;
                                }
                                default:
                                {
                                    _1216 = 8u;
                                    break;
                                }
                            }
                            _1222 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_168, _1216)), int(0u));
                            break;
                        }
                        case 1u:
                        {
                        }
                        case 2u:
                        {
                        }
                        case 3u:
                        {
                        }
                        case 4u:
                        {
                        }
                        case 5u:
                        {
                            uint _1199;
                            switch (_1118)
                            {
                                case 0u:
                                {
                                    _1199 = 6u;
                                    break;
                                }
                                case 8u:
                                {
                                    _1199 = 7u;
                                    break;
                                }
                                case 1u:
                                {
                                    _1199 = 4u;
                                    break;
                                }
                                case 2u:
                                {
                                    _1199 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                    break;
                                }
                                case 3u:
                                {
                                    _1199 = 1u;
                                    break;
                                }
                                case 4u:
                                {
                                    _1199 = 2u;
                                    break;
                                }
                                case 5u:
                                {
                                    _1199 = 3u;
                                    break;
                                }
                                default:
                                {
                                    _1199 = 8u;
                                    break;
                                }
                            }
                            _1222 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(uint(max((_1126 - 2), 0)), _186, _1199)), int(0u));
                            break;
                        }
                        case 6u:
                        {
                            uint _1182;
                            do
                            {
                                bool _1168;
                                if (_128)
                                {
                                    _1168 = _104 == 2u;
                                }
                                else
                                {
                                    _1168 = false;
                                }
                                bool _1173;
                                if (!_1168)
                                {
                                    _1173 = _104 == 3u;
                                }
                                else
                                {
                                    _1173 = true;
                                }
                                bool _1177;
                                if (_1173)
                                {
                                    _1177 = !_109;
                                }
                                else
                                {
                                    _1177 = false;
                                }
                                if (_1177)
                                {
                                    _1182 = _186 >> 1u;
                                    break;
                                }
                                else
                                {
                                    _1182 = _186;
                                    break;
                                }
                                break; // unreachable workaround
                            } while(false);
                            _1222 = texelFetch(g_lnclBackIn, int(_1182 + 256u));
                            break;
                        }
                        case 7u:
                        {
                            uint _1160;
                            do
                            {
                                bool _1146;
                                if (_128)
                                {
                                    _1146 = _104 == 2u;
                                }
                                else
                                {
                                    _1146 = false;
                                }
                                bool _1151;
                                if (!_1146)
                                {
                                    _1151 = _104 == 3u;
                                }
                                else
                                {
                                    _1151 = true;
                                }
                                bool _1155;
                                if (_1151)
                                {
                                    _1155 = !_109;
                                }
                                else
                                {
                                    _1155 = false;
                                }
                                if (_1155)
                                {
                                    _1160 = _186 >> 1u;
                                    break;
                                }
                                else
                                {
                                    _1160 = _186;
                                    break;
                                }
                                break; // unreachable workaround
                            } while(false);
                            _1222 = texelFetch(g_lnclBackIn, int(_1160));
                            break;
                        }
                        default:
                        {
                            _1222 = uvec4(0u);
                            break;
                        }
                    }
                    uvec4 _1319;
                    switch (_1118)
                    {
                        case 0u:
                        {
                        }
                        case 8u:
                        {
                            uint _1313;
                            switch (_1118)
                            {
                                case 0u:
                                {
                                    _1313 = 6u;
                                    break;
                                }
                                case 8u:
                                {
                                    _1313 = 7u;
                                    break;
                                }
                                case 1u:
                                {
                                    _1313 = 4u;
                                    break;
                                }
                                case 2u:
                                {
                                    _1313 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                    break;
                                }
                                case 3u:
                                {
                                    _1313 = 1u;
                                    break;
                                }
                                case 4u:
                                {
                                    _1313 = 2u;
                                    break;
                                }
                                case 5u:
                                {
                                    _1313 = 3u;
                                    break;
                                }
                                default:
                                {
                                    _1313 = 8u;
                                    break;
                                }
                            }
                            _1319 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_168, _1313)), int(0u));
                            break;
                        }
                        case 1u:
                        {
                        }
                        case 2u:
                        {
                        }
                        case 3u:
                        {
                        }
                        case 4u:
                        {
                        }
                        case 5u:
                        {
                            uint _1296;
                            switch (_1118)
                            {
                                case 0u:
                                {
                                    _1296 = 6u;
                                    break;
                                }
                                case 8u:
                                {
                                    _1296 = 7u;
                                    break;
                                }
                                case 1u:
                                {
                                    _1296 = 4u;
                                    break;
                                }
                                case 2u:
                                {
                                    _1296 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                    break;
                                }
                                case 3u:
                                {
                                    _1296 = 1u;
                                    break;
                                }
                                case 4u:
                                {
                                    _1296 = 2u;
                                    break;
                                }
                                case 5u:
                                {
                                    _1296 = 3u;
                                    break;
                                }
                                default:
                                {
                                    _1296 = 8u;
                                    break;
                                }
                            }
                            _1319 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(uint(max((_1126 - 1), 0)), _186, _1296)), int(0u));
                            break;
                        }
                        case 6u:
                        {
                            uint _1279;
                            do
                            {
                                bool _1265;
                                if (_128)
                                {
                                    _1265 = _104 == 2u;
                                }
                                else
                                {
                                    _1265 = false;
                                }
                                bool _1270;
                                if (!_1265)
                                {
                                    _1270 = _104 == 3u;
                                }
                                else
                                {
                                    _1270 = true;
                                }
                                bool _1274;
                                if (_1270)
                                {
                                    _1274 = !_109;
                                }
                                else
                                {
                                    _1274 = false;
                                }
                                if (_1274)
                                {
                                    _1279 = _186 >> 1u;
                                    break;
                                }
                                else
                                {
                                    _1279 = _186;
                                    break;
                                }
                                break; // unreachable workaround
                            } while(false);
                            _1319 = texelFetch(g_lnclBackIn, int(_1279 + 256u));
                            break;
                        }
                        case 7u:
                        {
                            uint _1257;
                            do
                            {
                                bool _1243;
                                if (_128)
                                {
                                    _1243 = _104 == 2u;
                                }
                                else
                                {
                                    _1243 = false;
                                }
                                bool _1248;
                                if (!_1243)
                                {
                                    _1248 = _104 == 3u;
                                }
                                else
                                {
                                    _1248 = true;
                                }
                                bool _1252;
                                if (_1248)
                                {
                                    _1252 = !_109;
                                }
                                else
                                {
                                    _1252 = false;
                                }
                                if (_1252)
                                {
                                    _1257 = _186 >> 1u;
                                    break;
                                }
                                else
                                {
                                    _1257 = _186;
                                    break;
                                }
                                break; // unreachable workaround
                            } while(false);
                            _1319 = texelFetch(g_lnclBackIn, int(_1257));
                            break;
                        }
                        default:
                        {
                            _1319 = uvec4(0u);
                            break;
                        }
                    }
                    uvec4 _1413;
                    switch (_1118)
                    {
                        case 0u:
                        {
                        }
                        case 8u:
                        {
                            uint _1407;
                            switch (_1118)
                            {
                                case 0u:
                                {
                                    _1407 = 6u;
                                    break;
                                }
                                case 8u:
                                {
                                    _1407 = 7u;
                                    break;
                                }
                                case 1u:
                                {
                                    _1407 = 4u;
                                    break;
                                }
                                case 2u:
                                {
                                    _1407 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                    break;
                                }
                                case 3u:
                                {
                                    _1407 = 1u;
                                    break;
                                }
                                case 4u:
                                {
                                    _1407 = 2u;
                                    break;
                                }
                                case 5u:
                                {
                                    _1407 = 3u;
                                    break;
                                }
                                default:
                                {
                                    _1407 = 8u;
                                    break;
                                }
                            }
                            _1413 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_168, _1407)), int(0u));
                            break;
                        }
                        case 1u:
                        {
                        }
                        case 2u:
                        {
                        }
                        case 3u:
                        {
                        }
                        case 4u:
                        {
                        }
                        case 5u:
                        {
                            uint _1390;
                            switch (_1118)
                            {
                                case 0u:
                                {
                                    _1390 = 6u;
                                    break;
                                }
                                case 8u:
                                {
                                    _1390 = 7u;
                                    break;
                                }
                                case 1u:
                                {
                                    _1390 = 4u;
                                    break;
                                }
                                case 2u:
                                {
                                    _1390 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                    break;
                                }
                                case 3u:
                                {
                                    _1390 = 1u;
                                    break;
                                }
                                case 4u:
                                {
                                    _1390 = 2u;
                                    break;
                                }
                                case 5u:
                                {
                                    _1390 = 3u;
                                    break;
                                }
                                default:
                                {
                                    _1390 = 8u;
                                    break;
                                }
                            }
                            _1413 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_146, _186, _1390)), int(0u));
                            break;
                        }
                        case 6u:
                        {
                            uint _1373;
                            do
                            {
                                bool _1359;
                                if (_128)
                                {
                                    _1359 = _104 == 2u;
                                }
                                else
                                {
                                    _1359 = false;
                                }
                                bool _1364;
                                if (!_1359)
                                {
                                    _1364 = _104 == 3u;
                                }
                                else
                                {
                                    _1364 = true;
                                }
                                bool _1368;
                                if (_1364)
                                {
                                    _1368 = !_109;
                                }
                                else
                                {
                                    _1368 = false;
                                }
                                if (_1368)
                                {
                                    _1373 = _186 >> 1u;
                                    break;
                                }
                                else
                                {
                                    _1373 = _186;
                                    break;
                                }
                                break; // unreachable workaround
                            } while(false);
                            _1413 = texelFetch(g_lnclBackIn, int(_1373 + 256u));
                            break;
                        }
                        case 7u:
                        {
                            uint _1351;
                            do
                            {
                                bool _1337;
                                if (_128)
                                {
                                    _1337 = _104 == 2u;
                                }
                                else
                                {
                                    _1337 = false;
                                }
                                bool _1342;
                                if (!_1337)
                                {
                                    _1342 = _104 == 3u;
                                }
                                else
                                {
                                    _1342 = true;
                                }
                                bool _1346;
                                if (_1342)
                                {
                                    _1346 = !_109;
                                }
                                else
                                {
                                    _1346 = false;
                                }
                                if (_1346)
                                {
                                    _1351 = _186 >> 1u;
                                    break;
                                }
                                else
                                {
                                    _1351 = _186;
                                    break;
                                }
                                break; // unreachable workaround
                            } while(false);
                            _1413 = texelFetch(g_lnclBackIn, int(_1351));
                            break;
                        }
                        default:
                        {
                            _1413 = uvec4(0u);
                            break;
                        }
                    }
                    _1419 = (((_1222.xyz + _1319.xyz) >> uvec3(1u)) + _1413.xyz) >> uvec3(1u);
                }
                else
                {
                    _1419 = _754.xyz;
                }
                _1421 = _1123 ? _1118 : _231;
                _1422 = _1419;
            }
            else
            {
                uvec3 _1107;
                if (_116 ? (((CommonRenderParams_1.g_commonParams.layerParams >> 25u) & 1u) != 0u) : false)
                {
                    bool _922;
                    do
                    {
                        bool _835 = ((g_composeParams._m0[0u].colorCalcEnable >> (_231 & 31u)) & 1u) != 0u;
                        if (_231 >= 6u)
                        {
                            _922 = _835;
                            break;
                        }
                        if (!_835)
                        {
                            _922 = false;
                            break;
                        }
                        if (texelFetch(SPIRV_Cross_Combinedg_colorCalcWindowInSPIRV_Cross_DummySampler, ivec2(_187), int(0u)).x != 0u)
                        {
                            _922 = false;
                            break;
                        }
                        bool _850 = ((CommonRenderParams_1.g_commonParams.layerParams >> 24u) & 1u) != 0u;
                        if (_231 == 0u)
                        {
                            uint _854 = _168.x;
                            uvec4 _858 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_854, _168.y, 6u)), int(0u));
                            uint _859 = _858.w;
                            bool _865;
                            if (_850)
                            {
                                _865 = ((_859 >> 7u) & 1u) != 0u;
                            }
                            else
                            {
                                _865 = false;
                            }
                            if (_865)
                            {
                                _922 = false;
                                break;
                            }
                            uint _872 = _859 & 7u;
                            uint _876 = (CommonRenderParams_1.g_commonParams.spriteParams >> 12u) & 7u;
                            bool _890;
                            bool _891;
                            switch ((CommonRenderParams_1.g_commonParams.spriteParams >> 15u) & 3u)
                            {
                                case 0u:
                                {
                                    _890 = _872 <= _876;
                                    _891 = true;
                                    break;
                                }
                                case 1u:
                                {
                                    _890 = _872 == _876;
                                    _891 = true;
                                    break;
                                }
                                case 2u:
                                {
                                    _890 = _872 >= _876;
                                    _891 = true;
                                    break;
                                }
                                case 3u:
                                {
                                    _890 = ((texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(_854, _168.y, 0u)), int(0u)).x >> 5u) & 1u) != 0u;
                                    _891 = true;
                                    break;
                                }
                                default:
                                {
                                    _890 = _92;
                                    _891 = false;
                                    break;
                                }
                            }
                            if (_891)
                            {
                                _922 = _890;
                                break;
                            }
                            _922 = false;
                            break;
                        }
                        uint _906;
                        switch (_231)
                        {
                            case 0u:
                            {
                                _906 = 6u;
                                break;
                            }
                            case 8u:
                            {
                                _906 = 7u;
                                break;
                            }
                            case 1u:
                            {
                                _906 = 4u;
                                break;
                            }
                            case 2u:
                            {
                                _906 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                break;
                            }
                            case 3u:
                            {
                                _906 = 1u;
                                break;
                            }
                            case 4u:
                            {
                                _906 = 2u;
                                break;
                            }
                            case 5u:
                            {
                                _906 = 3u;
                                break;
                            }
                            default:
                            {
                                _906 = 8u;
                                break;
                            }
                        }
                        uvec4 _909 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_146, _186, _906)), int(0u));
                        uint _910 = _909.w;
                        bool _916;
                        if (_850)
                        {
                            _916 = ((_910 >> 6u) & 1u) != 0u;
                        }
                        else
                        {
                            _916 = false;
                        }
                        if (_916)
                        {
                            _922 = false;
                            break;
                        }
                        _922 = ((_910 >> 7u) & 1u) != 0u;
                        break;
                    } while(false);
                    uvec3 _1030;
                    if (_922)
                    {
                        uvec4 _1017;
                        switch (_229)
                        {
                            case 0u:
                            {
                            }
                            case 8u:
                            {
                                uint _1011;
                                switch (_229)
                                {
                                    case 0u:
                                    {
                                        _1011 = 6u;
                                        break;
                                    }
                                    case 8u:
                                    {
                                        _1011 = 7u;
                                        break;
                                    }
                                    case 1u:
                                    {
                                        _1011 = 4u;
                                        break;
                                    }
                                    case 2u:
                                    {
                                        _1011 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                        break;
                                    }
                                    case 3u:
                                    {
                                        _1011 = 1u;
                                        break;
                                    }
                                    case 4u:
                                    {
                                        _1011 = 2u;
                                        break;
                                    }
                                    case 5u:
                                    {
                                        _1011 = 3u;
                                        break;
                                    }
                                    default:
                                    {
                                        _1011 = 8u;
                                        break;
                                    }
                                }
                                _1017 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_168, _1011)), int(0u));
                                break;
                            }
                            case 1u:
                            {
                            }
                            case 2u:
                            {
                            }
                            case 3u:
                            {
                            }
                            case 4u:
                            {
                            }
                            case 5u:
                            {
                                uint _994;
                                switch (_229)
                                {
                                    case 0u:
                                    {
                                        _994 = 6u;
                                        break;
                                    }
                                    case 8u:
                                    {
                                        _994 = 7u;
                                        break;
                                    }
                                    case 1u:
                                    {
                                        _994 = 4u;
                                        break;
                                    }
                                    case 2u:
                                    {
                                        _994 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                        break;
                                    }
                                    case 3u:
                                    {
                                        _994 = 1u;
                                        break;
                                    }
                                    case 4u:
                                    {
                                        _994 = 2u;
                                        break;
                                    }
                                    case 5u:
                                    {
                                        _994 = 3u;
                                        break;
                                    }
                                    default:
                                    {
                                        _994 = 8u;
                                        break;
                                    }
                                }
                                _1017 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_146, _186, _994)), int(0u));
                                break;
                            }
                            case 6u:
                            {
                                uint _977;
                                do
                                {
                                    bool _963;
                                    if (_128)
                                    {
                                        _963 = _104 == 2u;
                                    }
                                    else
                                    {
                                        _963 = false;
                                    }
                                    bool _968;
                                    if (!_963)
                                    {
                                        _968 = _104 == 3u;
                                    }
                                    else
                                    {
                                        _968 = true;
                                    }
                                    bool _972;
                                    if (_968)
                                    {
                                        _972 = !_109;
                                    }
                                    else
                                    {
                                        _972 = false;
                                    }
                                    if (_972)
                                    {
                                        _977 = _186 >> 1u;
                                        break;
                                    }
                                    else
                                    {
                                        _977 = _186;
                                        break;
                                    }
                                    break; // unreachable workaround
                                } while(false);
                                _1017 = texelFetch(g_lnclBackIn, int(_977 + 256u));
                                break;
                            }
                            case 7u:
                            {
                                uint _955;
                                do
                                {
                                    bool _941;
                                    if (_128)
                                    {
                                        _941 = _104 == 2u;
                                    }
                                    else
                                    {
                                        _941 = false;
                                    }
                                    bool _946;
                                    if (!_941)
                                    {
                                        _946 = _104 == 3u;
                                    }
                                    else
                                    {
                                        _946 = true;
                                    }
                                    bool _950;
                                    if (_946)
                                    {
                                        _950 = !_109;
                                    }
                                    else
                                    {
                                        _950 = false;
                                    }
                                    if (_950)
                                    {
                                        _955 = _186 >> 1u;
                                        break;
                                    }
                                    else
                                    {
                                        _955 = _186;
                                        break;
                                    }
                                    break; // unreachable workaround
                                } while(false);
                                _1017 = texelFetch(g_lnclBackIn, int(_955));
                                break;
                            }
                            default:
                            {
                                _1017 = uvec4(0u);
                                break;
                            }
                        }
                        bool _1022;
                        if (_131)
                        {
                            _1022 = _447 == 2u;
                        }
                        else
                        {
                            _1022 = false;
                        }
                        uvec3 _1027;
                        if (_1022)
                        {
                            _1027 = (_1017.xyz + _446) >> uvec3(1u);
                        }
                        else
                        {
                            _1027 = _1017.xyz;
                        }
                        _1030 = (_754.xyz + _1027) >> uvec3(1u);
                    }
                    else
                    {
                        _1030 = _754.xyz;
                    }
                    uvec3 _1106;
                    if (_466)
                    {
                        uvec3 _1095;
                        do
                        {
                            bool _1045;
                            if (_233 != 1u)
                            {
                                bool _1044;
                                if (_233 == 2u)
                                {
                                    _1044 = ((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u;
                                }
                                else
                                {
                                    _1044 = false;
                                }
                                _1045 = _1044;
                            }
                            else
                            {
                                _1045 = true;
                            }
                            if (_1045)
                            {
                                bool _1055;
                                if (_128)
                                {
                                    bool _1054;
                                    if (_104 >= 2u)
                                    {
                                        _1054 = !_109;
                                    }
                                    else
                                    {
                                        _1054 = false;
                                    }
                                    _1055 = _1054;
                                }
                                else
                                {
                                    _1055 = false;
                                }
                                uint _1059;
                                if (_1055)
                                {
                                    _1059 = _147 >> 1u;
                                }
                                else
                                {
                                    _1059 = _147;
                                }
                                uint _1066;
                                do
                                {
                                    if (_114)
                                    {
                                        _1066 = _146 >> 1u;
                                        break;
                                    }
                                    else
                                    {
                                        _1066 = _146;
                                        break;
                                    }
                                    break; // unreachable workaround
                                } while(false);
                                _1095 = texelFetch(SPIRV_Cross_Combinedg_rbgLineColorInSPIRV_Cross_DummySampler, ivec3(uvec3(_1066, _1059, _233 - 1u)), int(0u)).xyz;
                                break;
                            }
                            uint _1091;
                            do
                            {
                                bool _1077;
                                if (_128)
                                {
                                    _1077 = _104 == 2u;
                                }
                                else
                                {
                                    _1077 = false;
                                }
                                bool _1082;
                                if (!_1077)
                                {
                                    _1082 = _104 == 3u;
                                }
                                else
                                {
                                    _1082 = true;
                                }
                                bool _1086;
                                if (_1082)
                                {
                                    _1086 = !_109;
                                }
                                else
                                {
                                    _1086 = false;
                                }
                                if (_1086)
                                {
                                    _1091 = _147 >> 1u;
                                    break;
                                }
                                else
                                {
                                    _1091 = _147;
                                    break;
                                }
                                break; // unreachable workaround
                            } while(false);
                            _1095 = texelFetch(g_lnclBackIn, int(_1091)).xyz;
                            break;
                        } while(false);
                        uvec3 _1105;
                        if (((g_composeParams._m0[0u].colorCalcEnable >> 7u) & 1u) != 0u)
                        {
                            _1105 = (_1030 + _1095) >> uvec3(1u);
                        }
                        else
                        {
                            _1105 = _1095;
                        }
                        _1106 = _1105;
                    }
                    else
                    {
                        _1106 = _1030;
                    }
                    _1107 = _1106;
                }
                else
                {
                    uvec3 _828;
                    if (_466)
                    {
                        uvec3 _827;
                        do
                        {
                            bool _777;
                            if (_233 != 1u)
                            {
                                bool _776;
                                if (_233 == 2u)
                                {
                                    _776 = ((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u;
                                }
                                else
                                {
                                    _776 = false;
                                }
                                _777 = _776;
                            }
                            else
                            {
                                _777 = true;
                            }
                            if (_777)
                            {
                                bool _787;
                                if (_128)
                                {
                                    bool _786;
                                    if (_104 >= 2u)
                                    {
                                        _786 = !_109;
                                    }
                                    else
                                    {
                                        _786 = false;
                                    }
                                    _787 = _786;
                                }
                                else
                                {
                                    _787 = false;
                                }
                                uint _791;
                                if (_787)
                                {
                                    _791 = _147 >> 1u;
                                }
                                else
                                {
                                    _791 = _147;
                                }
                                uint _798;
                                do
                                {
                                    if (_114)
                                    {
                                        _798 = _146 >> 1u;
                                        break;
                                    }
                                    else
                                    {
                                        _798 = _146;
                                        break;
                                    }
                                    break; // unreachable workaround
                                } while(false);
                                _827 = texelFetch(SPIRV_Cross_Combinedg_rbgLineColorInSPIRV_Cross_DummySampler, ivec3(uvec3(_798, _791, _233 - 1u)), int(0u)).xyz;
                                break;
                            }
                            uint _823;
                            do
                            {
                                bool _809;
                                if (_128)
                                {
                                    _809 = _104 == 2u;
                                }
                                else
                                {
                                    _809 = false;
                                }
                                bool _814;
                                if (!_809)
                                {
                                    _814 = _104 == 3u;
                                }
                                else
                                {
                                    _814 = true;
                                }
                                bool _818;
                                if (_814)
                                {
                                    _818 = !_109;
                                }
                                else
                                {
                                    _818 = false;
                                }
                                if (_818)
                                {
                                    _823 = _147 >> 1u;
                                    break;
                                }
                                else
                                {
                                    _823 = _147;
                                    break;
                                }
                                break; // unreachable workaround
                            } while(false);
                            _827 = texelFetch(g_lnclBackIn, int(_823)).xyz;
                            break;
                        } while(false);
                        _828 = _827;
                    }
                    else
                    {
                        _828 = _754.xyz;
                    }
                    _1107 = _828;
                }
                _1421 = _231;
                _1422 = _1107;
            }
            bool _1426;
            if (_131)
            {
                _1426 = _447 == 1u;
            }
            else
            {
                _1426 = false;
            }
            uvec3 _1431;
            if (_1426)
            {
                _1431 = (_1422 + _446) >> uvec3(1u);
            }
            else
            {
                _1431 = _1422;
            }
            uvec3 _1586;
            if (((CommonRenderParams_1.g_commonParams.layerParams >> 26u) & 1u) != 0u)
            {
                _1586 = min((_562.xyz + _1431), uvec3(255u));
            }
            else
            {
                bool _1440 = ((CommonRenderParams_1.g_commonParams.layerParams >> 27u) & 1u) != 0u;
                int _1575;
                if (_1440 ? _466 : false)
                {
                    _1575 = int(g_composeParams._m0[0u].backLineColorCalcRatios[1]);
                }
                else
                {
                    uint _1445 = _1440 ? _1421 : _233;
                    int _1571;
                    switch (_1445)
                    {
                        case 0u:
                        {
                            _1571 = int(texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(_168, 0u)), int(0u)).x & 31u);
                            break;
                        }
                        case 1u:
                        {
                        }
                        case 2u:
                        {
                        }
                        case 3u:
                        {
                        }
                        case 4u:
                        {
                        }
                        case 5u:
                        {
                            _1571 = int(g_composeParams._m0[0u].bgColorCalcRatios[_1445 - 1u]);
                            break;
                        }
                        case 6u:
                        {
                        }
                        case 7u:
                        {
                            bool _1549;
                            do
                            {
                                bool _1462 = ((g_composeParams._m0[0u].colorCalcEnable >> (_1445 & 31u)) & 1u) != 0u;
                                if (_1445 >= 6u)
                                {
                                    _1549 = _1462;
                                    break;
                                }
                                if (!_1462)
                                {
                                    _1549 = false;
                                    break;
                                }
                                if (texelFetch(SPIRV_Cross_Combinedg_colorCalcWindowInSPIRV_Cross_DummySampler, ivec2(_187), int(0u)).x != 0u)
                                {
                                    _1549 = false;
                                    break;
                                }
                                bool _1477 = ((CommonRenderParams_1.g_commonParams.layerParams >> 24u) & 1u) != 0u;
                                if (_1445 == 0u)
                                {
                                    uint _1481 = _168.x;
                                    uvec4 _1485 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_1481, _168.y, 6u)), int(0u));
                                    uint _1486 = _1485.w;
                                    bool _1492;
                                    if (_1477)
                                    {
                                        _1492 = ((_1486 >> 7u) & 1u) != 0u;
                                    }
                                    else
                                    {
                                        _1492 = false;
                                    }
                                    if (_1492)
                                    {
                                        _1549 = false;
                                        break;
                                    }
                                    uint _1499 = _1486 & 7u;
                                    uint _1503 = (CommonRenderParams_1.g_commonParams.spriteParams >> 12u) & 7u;
                                    bool _1517;
                                    bool _1518;
                                    switch ((CommonRenderParams_1.g_commonParams.spriteParams >> 15u) & 3u)
                                    {
                                        case 0u:
                                        {
                                            _1517 = _1499 <= _1503;
                                            _1518 = true;
                                            break;
                                        }
                                        case 1u:
                                        {
                                            _1517 = _1499 == _1503;
                                            _1518 = true;
                                            break;
                                        }
                                        case 2u:
                                        {
                                            _1517 = _1499 >= _1503;
                                            _1518 = true;
                                            break;
                                        }
                                        case 3u:
                                        {
                                            _1517 = ((texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(_1481, _168.y, 0u)), int(0u)).x >> 5u) & 1u) != 0u;
                                            _1518 = true;
                                            break;
                                        }
                                        default:
                                        {
                                            _1517 = _92;
                                            _1518 = false;
                                            break;
                                        }
                                    }
                                    if (_1518)
                                    {
                                        _1549 = _1517;
                                        break;
                                    }
                                    _1549 = false;
                                    break;
                                }
                                uint _1533;
                                switch (_1445)
                                {
                                    case 0u:
                                    {
                                        _1533 = 6u;
                                        break;
                                    }
                                    case 8u:
                                    {
                                        _1533 = 7u;
                                        break;
                                    }
                                    case 1u:
                                    {
                                        _1533 = 4u;
                                        break;
                                    }
                                    case 2u:
                                    {
                                        _1533 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                        break;
                                    }
                                    case 3u:
                                    {
                                        _1533 = 1u;
                                        break;
                                    }
                                    case 4u:
                                    {
                                        _1533 = 2u;
                                        break;
                                    }
                                    case 5u:
                                    {
                                        _1533 = 3u;
                                        break;
                                    }
                                    default:
                                    {
                                        _1533 = 8u;
                                        break;
                                    }
                                }
                                uvec4 _1536 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_146, _186, _1533)), int(0u));
                                uint _1537 = _1536.w;
                                bool _1543;
                                if (_1477)
                                {
                                    _1543 = ((_1537 >> 6u) & 1u) != 0u;
                                }
                                else
                                {
                                    _1543 = false;
                                }
                                if (_1543)
                                {
                                    _1549 = false;
                                    break;
                                }
                                _1549 = ((_1537 >> 7u) & 1u) != 0u;
                                break;
                            } while(false);
                            if (_1549)
                            {
                                _1571 = int(g_composeParams._m0[0u].backLineColorCalcRatios[1]);
                                break;
                            }
                            else
                            {
                                _1571 = int(g_composeParams._m0[0u].backLineColorCalcRatios[0]);
                                break;
                            }
                            break; // unreachable workaround
                        }
                        default:
                        {
                            _1571 = 31;
                            break;
                        }
                    }
                    _1575 = _1571;
                }
                ivec3 _1576 = ivec3(_1431);
                _1586 = uvec3(_1576 + (((ivec3(_562.xyz) - _1576) * ivec3(_1575)) >> ivec3(5)));
            }
            _1587 = _1421;
            _1588 = _1586;
        }
        else
        {
            _1587 = _231;
            _1588 = _562.xyz;
        }
        uint _1589 = _168.x;
        uvec3 _1591 = uvec3(_1589, _168.y, 6u);
        uvec3 _1638;
        if ((texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(_1591), int(0u)).w & 7u) >= _227)
        {
            uvec4 _1601 = texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(_1589, _168.y, 0u)), int(0u));
            uint _1602 = _1601.x;
            bool _1615;
            if (((CommonRenderParams_1.g_commonParams.spriteParams >> 20u) & 1u) == 0u)
            {
                _1615 = ((_1602 >> 6u) & 1u) != 0u;
            }
            else
            {
                _1615 = false;
            }
            bool _1630;
            if (_233 == 0u)
            {
                _1630 = ((_1602 >> 6u) & 1u) != 0u;
            }
            else
            {
                _1630 = ((g_composeParams._m0[0u].lineColorEnable >> ((16u + _233) & 31u)) & 1u) != 0u;
            }
            uvec3 _1637;
            if (((((_1602 >> 7u) & 3u) != 1u) ? _1615 : true) ? _1630 : false)
            {
                _1637 = _1588 >> uvec3(1u);
            }
            else
            {
                _1637 = _1588;
            }
            _1638 = _1637;
        }
        else
        {
            _1638 = _1588;
        }
        uvec3 _1663;
        if (((g_composeParams._m0[0u].colorOffsetEnable >> _568) & 1u) != 0u)
        {
            ivec3 _1658;
            if (((g_composeParams._m0[0u].colorOffsetSelect >> _568) & 1u) != 0u)
            {
                _1658 = g_composeParams._m0[0u].colorOffsetB;
            }
            else
            {
                _1658 = g_composeParams._m0[0u].colorOffsetA;
            }
            _1663 = uvec3(clamp(ivec3(_1638) + _1658, ivec3(0), ivec3(255)));
        }
        else
        {
            _1663 = _1638;
        }
        bool _1667;
        if (_131)
        {
            _1667 = _447 == 0u;
        }
        else
        {
            _1667 = false;
        }
        uvec3 _1853;
        if (_1667)
        {
            uvec3 _1826;
            if (((g_composeParams._m0[0u].colorCalcEnable >> 8u) & 1u) != 0u)
            {
                uvec3 _1825;
                if (((CommonRenderParams_1.g_commonParams.layerParams >> 26u) & 1u) != 0u)
                {
                    _1825 = min((_446 + _1663), uvec3(255u));
                }
                else
                {
                    bool _1684 = ((CommonRenderParams_1.g_commonParams.layerParams >> 27u) & 1u) != 0u;
                    int _1814;
                    if (_1684 ? _466 : false)
                    {
                        _1814 = int(g_composeParams._m0[0u].backLineColorCalcRatios[1]);
                    }
                    else
                    {
                        uint _1689 = _1684 ? _1587 : _233;
                        int _1810;
                        switch (_1689)
                        {
                            case 0u:
                            {
                                _1810 = int(texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(_1589, _168.y, 0u)), int(0u)).x & 31u);
                                break;
                            }
                            case 1u:
                            {
                            }
                            case 2u:
                            {
                            }
                            case 3u:
                            {
                            }
                            case 4u:
                            {
                            }
                            case 5u:
                            {
                                _1810 = int(g_composeParams._m0[0u].bgColorCalcRatios[_1689 - 1u]);
                                break;
                            }
                            case 6u:
                            {
                            }
                            case 7u:
                            {
                                bool _1790;
                                do
                                {
                                    bool _1706 = ((g_composeParams._m0[0u].colorCalcEnable >> (_1689 & 31u)) & 1u) != 0u;
                                    if (_1689 >= 6u)
                                    {
                                        _1790 = _1706;
                                        break;
                                    }
                                    if (!_1706)
                                    {
                                        _1790 = false;
                                        break;
                                    }
                                    if (texelFetch(SPIRV_Cross_Combinedg_colorCalcWindowInSPIRV_Cross_DummySampler, ivec2(_187), int(0u)).x != 0u)
                                    {
                                        _1790 = false;
                                        break;
                                    }
                                    bool _1721 = ((CommonRenderParams_1.g_commonParams.layerParams >> 24u) & 1u) != 0u;
                                    if (_1689 == 0u)
                                    {
                                        uvec4 _1726 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(_1591), int(0u));
                                        uint _1727 = _1726.w;
                                        bool _1733;
                                        if (_1721)
                                        {
                                            _1733 = ((_1727 >> 7u) & 1u) != 0u;
                                        }
                                        else
                                        {
                                            _1733 = false;
                                        }
                                        if (_1733)
                                        {
                                            _1790 = false;
                                            break;
                                        }
                                        uint _1740 = _1727 & 7u;
                                        uint _1744 = (CommonRenderParams_1.g_commonParams.spriteParams >> 12u) & 7u;
                                        bool _1758;
                                        bool _1759;
                                        switch ((CommonRenderParams_1.g_commonParams.spriteParams >> 15u) & 3u)
                                        {
                                            case 0u:
                                            {
                                                _1758 = _1740 <= _1744;
                                                _1759 = true;
                                                break;
                                            }
                                            case 1u:
                                            {
                                                _1758 = _1740 == _1744;
                                                _1759 = true;
                                                break;
                                            }
                                            case 2u:
                                            {
                                                _1758 = _1740 >= _1744;
                                                _1759 = true;
                                                break;
                                            }
                                            case 3u:
                                            {
                                                _1758 = ((texelFetch(SPIRV_Cross_Combinedg_spriteAttrsInSPIRV_Cross_DummySampler, ivec3(uvec3(_1589, _168.y, 0u)), int(0u)).x >> 5u) & 1u) != 0u;
                                                _1759 = true;
                                                break;
                                            }
                                            default:
                                            {
                                                _1758 = _92;
                                                _1759 = false;
                                                break;
                                            }
                                        }
                                        if (_1759)
                                        {
                                            _1790 = _1758;
                                            break;
                                        }
                                        _1790 = false;
                                        break;
                                    }
                                    uint _1774;
                                    switch (_1689)
                                    {
                                        case 0u:
                                        {
                                            _1774 = 6u;
                                            break;
                                        }
                                        case 8u:
                                        {
                                            _1774 = 7u;
                                            break;
                                        }
                                        case 1u:
                                        {
                                            _1774 = 4u;
                                            break;
                                        }
                                        case 2u:
                                        {
                                            _1774 = (((CommonRenderParams_1.g_commonParams.layerParams >> 11u) & 1u) != 0u) ? 5u : 0u;
                                            break;
                                        }
                                        case 3u:
                                        {
                                            _1774 = 1u;
                                            break;
                                        }
                                        case 4u:
                                        {
                                            _1774 = 2u;
                                            break;
                                        }
                                        case 5u:
                                        {
                                            _1774 = 3u;
                                            break;
                                        }
                                        default:
                                        {
                                            _1774 = 8u;
                                            break;
                                        }
                                    }
                                    uvec4 _1777 = texelFetch(SPIRV_Cross_Combinedg_layerInSPIRV_Cross_DummySampler, ivec3(uvec3(_146, _186, _1774)), int(0u));
                                    uint _1778 = _1777.w;
                                    bool _1784;
                                    if (_1721)
                                    {
                                        _1784 = ((_1778 >> 6u) & 1u) != 0u;
                                    }
                                    else
                                    {
                                        _1784 = false;
                                    }
                                    if (_1784)
                                    {
                                        _1790 = false;
                                        break;
                                    }
                                    _1790 = ((_1778 >> 7u) & 1u) != 0u;
                                    break;
                                } while(false);
                                if (_1790)
                                {
                                    _1810 = int(g_composeParams._m0[0u].backLineColorCalcRatios[1]);
                                    break;
                                }
                                else
                                {
                                    _1810 = int(g_composeParams._m0[0u].backLineColorCalcRatios[0]);
                                    break;
                                }
                                break; // unreachable workaround
                            }
                            default:
                            {
                                _1810 = 31;
                                break;
                            }
                        }
                        _1814 = _1810;
                    }
                    ivec3 _1815 = ivec3(_1663);
                    _1825 = uvec3(_1815 + (((ivec3(_446) - _1815) * ivec3(_1814)) >> ivec3(5)));
                }
                _1826 = _1825;
            }
            else
            {
                _1826 = _446;
            }
            uvec3 _1850;
            if (((g_composeParams._m0[0u].colorOffsetEnable >> _568) & 1u) != 0u)
            {
                ivec3 _1845;
                if (((g_composeParams._m0[0u].colorOffsetSelect >> _568) & 1u) != 0u)
                {
                    _1845 = g_composeParams._m0[0u].colorOffsetB;
                }
                else
                {
                    _1845 = g_composeParams._m0[0u].colorOffsetA;
                }
                _1850 = uvec3(clamp(ivec3(_1826) + _1845, ivec3(0), ivec3(255)));
            }
            else
            {
                _1850 = _1826;
            }
            _1853 = (_1663 + _1850) >> uvec3(1u);
        }
        else
        {
            _1853 = _1663;
        }
        _1854 = _1853;
        break;
    } while(false);
    vec4 _1860 = vec4(vec3(_1854) * vec3(0.0039215688593685626983642578125), 1.0);
    imageStore(g_compositeOut, ivec2(_168), _1860);
    bool _1870;
    if ((((CommonRenderParams_1.g_commonParams.displayParams >> 17u) & 1u) != 0u) ? _150 : false)
    {
        bool _1869;
        if (_104 >= 2u)
        {
            _1869 = !_109;
        }
        else
        {
            _1869 = false;
        }
        _1870 = _1869;
    }
    else
    {
        _1870 = false;
    }
    if (_1870)
    {
        imageStore(g_compositeOut, ivec2(uvec2(_168.x, _168.y ^ 1u)), _1860);
    }
}

