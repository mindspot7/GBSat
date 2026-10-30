#version 310 es
#extension GL_EXT_texture_buffer : require
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

uint _107;
bool _108;

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

layout(binding = 4, std430) readonly buffer type_StructuredBuffer_RotParamBase
{
    RotParamBase _m0[];
} g_rotParamBases;

layout(binding = 5, std430) readonly buffer g_spriteFB
{
    uint _m0[];
} g_spriteFB_1;

layout(binding = 3) uniform highp usamplerBuffer g_cramColor;
layout(binding = 17, rgba8ui) uniform writeonly highp uimage2DArray g_layerOut;
layout(binding = 18, r32ui) uniform writeonly highp uimage2DArray g_spriteAttrsOut;

void main()
{
    uint _130 = (CommonRenderParamsBuffer.g_commonParams.displayParams >> 2u) & 3u;
    bool _137 = ((CommonRenderParamsBuffer.g_commonParams.displayParams >> 8u) & 1u) != 0u;
    uint _146 = (CommonRenderParamsBuffer.g_commonParams.spriteParams >> 2u) & 15u;
    uint _151 = max(((CommonRenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uint _154 = max(((CommonRenderParamsBuffer.g_commonParams.enhancements >> 5u) & 7u), 1u);
    uvec2 _162 = uvec2(_154);
    uvec2 _163 = uvec2(512u << ((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 6u) & 1u), 256u << ((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 7u) & 1u)) * _162;
    bool _175 = ((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 20u) & 1u) != 0u;
    bool _179 = (CommonRenderParamsBuffer.g_commonParams.enhancements & 1u) != 0u;
    uint _186 = (262144u * _154) * _154;
    uint _187 = ((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 23u) & 1u) * _186;
    uint _188 = _186 * 2u;
    uint _189 = _186 * 4u;
    uvec2 _198 = uvec2(gl_GlobalInvocationID.x, gl_GlobalInvocationID.y + (CommonRenderParamsBuffer.g_commonParams.startY * _151));
    uvec2 _199 = uvec2(_151);
    uvec2 _200 = _198 / _199;
    uint _201 = _200.x;
    uint _202 = _200.y;
    uint _219;
    do
    {
        bool _209;
        if (!_179)
        {
            _209 = _130 >= 2u;
        }
        else
        {
            _209 = false;
        }
        bool _213;
        if (_209)
        {
            _213 = ((CommonRenderParamsBuffer.g_commonParams.displayParams >> 5u) & 1u) == 0u;
        }
        else
        {
            _213 = false;
        }
        if (_213)
        {
            _219 = (_202 << 1u) | ((CommonRenderParamsBuffer.g_commonParams.displayParams >> 4u) & 1u);
            break;
        }
        else
        {
            _219 = _202;
            break;
        }
        break; // unreachable workaround
    } while(false);
    uvec2 _220 = uvec2(_201, _219);
    uvec2 _223 = mix(_220, _198, bvec2(_151 > 1u));
    uvec4 _1216;
    uint _1217;
    bool _1218;
    bool _1219;
    uint _1220;
    do
    {
        bool _228 = gl_GlobalInvocationID.z == 1u;
        uvec2 _421;
        uint _422;
        if ((CommonRenderParamsBuffer.g_commonParams.spriteParams & 1u) != 0u)
        {
            uint _277 = (g_rotParamBases._m0[_202].tableAddress & 524284u) >> 2u;
            uint _297 = ((g_rotParamBases._m0[_202].tableAddress + 4u) & 524284u) >> 2u;
            uint _317 = ((g_rotParamBases._m0[_202].tableAddress + 12u) & 524284u) >> 2u;
            uint _337 = ((g_rotParamBases._m0[_202].tableAddress + 16u) & 524284u) >> 2u;
            uint _357 = ((g_rotParamBases._m0[_202].tableAddress + 20u) & 524284u) >> 2u;
            uint _377 = ((g_rotParamBases._m0[_202].tableAddress + 24u) & 524284u) >> 2u;
            uvec2 _412 = uvec2(((uint((int((((((g_vram._m0[_277] >> 24u) & 255u) | ((g_vram._m0[_277] >> 8u) & 65280u)) | ((g_vram._m0[_277] << 8u) & 16711680u)) | ((g_vram._m0[_277] << 24u) & 4278190080u)) >> 6u) << 9) >> 9) + (_202 * uint((int((((((g_vram._m0[_317] >> 24u) & 255u) | ((g_vram._m0[_317] >> 8u) & 65280u)) | ((g_vram._m0[_317] << 8u) & 16711680u)) | ((g_vram._m0[_317] << 24u) & 4278190080u)) >> 6u) << 19) >> 19))) + (_201 * uint((int((((((g_vram._m0[_357] >> 24u) & 255u) | ((g_vram._m0[_357] >> 8u) & 65280u)) | ((g_vram._m0[_357] << 8u) & 16711680u)) | ((g_vram._m0[_357] << 24u) & 4278190080u)) >> 6u) << 19) >> 19))) >> 10u, ((uint((int((((((g_vram._m0[_297] >> 24u) & 255u) | ((g_vram._m0[_297] >> 8u) & 65280u)) | ((g_vram._m0[_297] << 8u) & 16711680u)) | ((g_vram._m0[_297] << 24u) & 4278190080u)) >> 6u) << 9) >> 9) + (_202 * uint((int((((((g_vram._m0[_337] >> 24u) & 255u) | ((g_vram._m0[_337] >> 8u) & 65280u)) | ((g_vram._m0[_337] << 8u) & 16711680u)) | ((g_vram._m0[_337] << 24u) & 4278190080u)) >> 6u) << 19) >> 19))) + (_201 * uint((int((((((g_vram._m0[_377] >> 24u) & 255u) | ((g_vram._m0[_377] >> 8u) & 65280u)) | ((g_vram._m0[_377] << 8u) & 16711680u)) | ((g_vram._m0[_377] << 24u) & 4278190080u)) >> 6u) << 19) >> 19))) >> 10u) * _162;
            uvec2 _416 = _412 + (((_198 % _199) * _162) / _199);
            if (any(greaterThanEqual(_416, _163)))
            {
                _1216 = uvec4(0u);
                _1217 = 0u;
                _1218 = false;
                _1219 = false;
                _1220 = 0u;
                break;
            }
            _421 = _416;
            _422 = 0u;
        }
        else
        {
            bool _236;
            if (_151 <= 1u)
            {
                _236 = _154 > 1u;
            }
            else
            {
                _236 = true;
            }
            uvec2 _272;
            uint _273;
            if (_236)
            {
                _272 = (_198 * _162) / _199;
                _273 = 0u;
            }
            else
            {
                uvec2 _250;
                if (((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 8u) & 1u) != 0u)
                {
                    uvec2 _249 = _200;
                    _249.x = _201 << 1u;
                    _250 = _249;
                }
                else
                {
                    uvec2 _247;
                    if (((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 9u) & 1u) != 0u)
                    {
                        uvec2 _246 = _200;
                        _246.x = _201 >> 1u;
                        _247 = _246;
                    }
                    else
                    {
                        _247 = _200;
                    }
                    _250 = _247;
                }
                bool _254;
                if (_179)
                {
                    _254 = _130 >= 2u;
                }
                else
                {
                    _254 = false;
                }
                uvec2 _268;
                uint _269;
                if (_254)
                {
                    bool _263;
                    if (_130 == 3u)
                    {
                        _263 = (_250.y & 1u) != 0u;
                    }
                    else
                    {
                        _263 = false;
                    }
                    uvec2 _267 = _250;
                    _267.y = _250.y >> 1u;
                    _268 = _267;
                    _269 = uint(_263);
                }
                else
                {
                    _268 = _250;
                    _269 = 0u;
                }
                _272 = _268;
                _273 = _269;
            }
            _421 = _272;
            _422 = _273;
        }
        uint _427 = _421.x + (_421.y * _163.x);
        bool _675;
        do
        {
            bool _433 = (CommonRenderParamsBuffer.g_commonParams.windows & 1u) != 0u;
            bool _436 = ((CommonRenderParamsBuffer.g_commonParams.windows >> 1u) & 1u) != 0u;
            bool _442 = ((CommonRenderParamsBuffer.g_commonParams.windows >> 3u) & 1u) != 0u;
            bool _450;
            if (!_436)
            {
                _450 = !_442;
            }
            else
            {
                _450 = false;
            }
            if (_450)
            {
                _675 = false;
                break;
            }
            bool _563;
            if (_436)
            {
                ivec2 _464 = ivec2(g_layerRenderParams._m0[0u].windows[0].start);
                ivec2 _465 = ivec2(g_layerRenderParams._m0[0u].windows[0].end);
                ivec2 _503;
                ivec2 _504;
                if (g_layerRenderParams._m0[0u].windows[0].lineWindowTableEnable != 0u)
                {
                    uint _469 = g_layerRenderParams._m0[0u].windows[0].lineWindowTableAddress + (_219 * 4u);
                    uint _478 = (g_vram._m0[(_469 & 524284u) >> 2u] >> (((_469 & 2u) * 8u) & 31u)) & 65535u;
                    ivec2 _485 = _464;
                    _485.x = int(((_478 >> 8u) & 255u) | ((_478 << 8u) & 65280u));
                    uint _486 = _469 + 2u;
                    uint _495 = (g_vram._m0[(_486 & 524284u) >> 2u] >> (((_486 & 2u) * 8u) & 31u)) & 65535u;
                    ivec2 _502 = _465;
                    _502.x = int(((_495 >> 8u) & 255u) | ((_495 << 8u) & 65280u));
                    _503 = _502;
                    _504 = _485;
                }
                else
                {
                    _503 = _465;
                    _504 = _464;
                }
                int _507 = (_504.x << 16) >> 16;
                int _510 = (_503.x << 16) >> 16;
                ivec2 _514 = ivec2(_507, (_504.y << 16) >> 16);
                ivec2 _518 = ivec2(_510, (_503.y << 16) >> 16);
                ivec2 _523;
                if (_507 < 0)
                {
                    ivec2 _522 = _514;
                    _522.x = 0;
                    _523 = _522;
                }
                else
                {
                    _523 = _514;
                }
                ivec2 _534;
                ivec2 _535;
                if (_510 < 0)
                {
                    ivec2 _532;
                    if (_523.x >= _510)
                    {
                        ivec2 _531 = _523;
                        _531.x = 1023;
                        _532 = _531;
                    }
                    else
                    {
                        _532 = _523;
                    }
                    ivec2 _533 = _518;
                    _533.x = 0;
                    _534 = _533;
                    _535 = _532;
                }
                else
                {
                    _534 = _518;
                    _535 = _523;
                }
                ivec2 _545;
                ivec2 _546;
                if (!_137)
                {
                    ivec2 _541 = _535;
                    _541.x = _535.x >> 1;
                    ivec2 _544 = _534;
                    _544.x = _534.x >> 1;
                    _545 = _544;
                    _546 = _541;
                }
                else
                {
                    _545 = _534;
                    _546 = _535;
                }
                ivec2 _547 = ivec2(_220);
                bool _554;
                if (all(greaterThanEqual(_547, _546)))
                {
                    _554 = all(lessThanEqual(_547, _545));
                }
                else
                {
                    _554 = false;
                }
                bool _555 = _554 != (((CommonRenderParamsBuffer.g_commonParams.windows >> 2u) & 1u) != 0u);
                bool _562;
                if (_433)
                {
                    _562 = _433 ? _555 : false;
                }
                else
                {
                    _562 = (!_433) ? _555 : true;
                }
                _563 = _562;
            }
            else
            {
                _563 = _433;
            }
            bool _674;
            if (_442)
            {
                ivec2 _575 = ivec2(g_layerRenderParams._m0[0u].windows[1].start);
                ivec2 _576 = ivec2(g_layerRenderParams._m0[0u].windows[1].end);
                ivec2 _614;
                ivec2 _615;
                if (g_layerRenderParams._m0[0u].windows[1].lineWindowTableEnable != 0u)
                {
                    uint _580 = g_layerRenderParams._m0[0u].windows[1].lineWindowTableAddress + (_219 * 4u);
                    uint _589 = (g_vram._m0[(_580 & 524284u) >> 2u] >> (((_580 & 2u) * 8u) & 31u)) & 65535u;
                    ivec2 _596 = _575;
                    _596.x = int(((_589 >> 8u) & 255u) | ((_589 << 8u) & 65280u));
                    uint _597 = _580 + 2u;
                    uint _606 = (g_vram._m0[(_597 & 524284u) >> 2u] >> (((_597 & 2u) * 8u) & 31u)) & 65535u;
                    ivec2 _613 = _576;
                    _613.x = int(((_606 >> 8u) & 255u) | ((_606 << 8u) & 65280u));
                    _614 = _613;
                    _615 = _596;
                }
                else
                {
                    _614 = _576;
                    _615 = _575;
                }
                int _618 = (_615.x << 16) >> 16;
                int _621 = (_614.x << 16) >> 16;
                ivec2 _625 = ivec2(_618, (_615.y << 16) >> 16);
                ivec2 _629 = ivec2(_621, (_614.y << 16) >> 16);
                ivec2 _634;
                if (_618 < 0)
                {
                    ivec2 _633 = _625;
                    _633.x = 0;
                    _634 = _633;
                }
                else
                {
                    _634 = _625;
                }
                ivec2 _645;
                ivec2 _646;
                if (_621 < 0)
                {
                    ivec2 _643;
                    if (_634.x >= _621)
                    {
                        ivec2 _642 = _634;
                        _642.x = 1023;
                        _643 = _642;
                    }
                    else
                    {
                        _643 = _634;
                    }
                    ivec2 _644 = _629;
                    _644.x = 0;
                    _645 = _644;
                    _646 = _643;
                }
                else
                {
                    _645 = _629;
                    _646 = _634;
                }
                ivec2 _656;
                ivec2 _657;
                if (!_137)
                {
                    ivec2 _652 = _646;
                    _652.x = _646.x >> 1;
                    ivec2 _655 = _645;
                    _655.x = _645.x >> 1;
                    _656 = _655;
                    _657 = _652;
                }
                else
                {
                    _656 = _645;
                    _657 = _646;
                }
                ivec2 _658 = ivec2(_220);
                bool _665;
                if (all(greaterThanEqual(_658, _657)))
                {
                    _665 = all(lessThanEqual(_658, _656));
                }
                else
                {
                    _665 = false;
                }
                bool _666 = _665 != (((CommonRenderParamsBuffer.g_commonParams.windows >> 4u) & 1u) != 0u);
                bool _673;
                if (_433)
                {
                    _673 = _563 ? _666 : false;
                }
                else
                {
                    _673 = (!_563) ? _666 : true;
                }
                _674 = _673;
            }
            else
            {
                _674 = _563;
            }
            _675 = _674;
            break;
        } while(false);
        if (_675)
        {
            _1216 = uvec4(0u);
            _1217 = 0u;
            _1218 = false;
            _1219 = false;
            _1220 = 0u;
            break;
        }
        if (((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 10u) & 1u) != 0u)
        {
            uint _718;
            if (_228)
            {
                uint _704 = (_189 + (_427 << 1u)) + (_187 + (_422 * _188));
                uint _712 = g_spriteFB_1._m0[(_704 & 4294967292u) >> 2u] >> (((_704 & 2u) * 8u) & 31u);
                _718 = ((_712 >> 8u) & 255u) | ((_712 << 8u) & 65280u);
            }
            else
            {
                uint _686 = (_427 << 1u) + (_187 + (_422 * _188));
                uint _694 = g_spriteFB_1._m0[(_686 & 4294967292u) >> 2u] >> (((_686 & 2u) * 8u) & 31u);
                _718 = ((_694 >> 8u) & 255u) | ((_694 << 8u) & 65280u);
            }
            if (((_718 >> 15u) & 1u) != 0u)
            {
                if (_146 >= 8u)
                {
                    if ((_718 & 255u) == 0u)
                    {
                        _1216 = uvec4(0u);
                        _1217 = 0u;
                        _1218 = false;
                        _1219 = false;
                        _1220 = 0u;
                        break;
                    }
                }
                else
                {
                    if (_146 >= 2u)
                    {
                        bool _735;
                        if (_175)
                        {
                            _735 = (_718 & 32767u) == 0u;
                        }
                        else
                        {
                            _735 = false;
                        }
                        if (_735)
                        {
                            _1216 = uvec4(0u);
                            _1217 = 0u;
                            _1218 = false;
                            _1219 = false;
                            _1220 = 0u;
                            break;
                        }
                    }
                }
                _1216 = uvec4((_718 & 31u) << 3u, ((_718 >> 5u) & 31u) << 3u, ((_718 >> 10u) & 31u) << 3u, CommonRenderParamsBuffer.g_commonParams.spritePriosRatios.x & 7u);
                _1217 = (CommonRenderParamsBuffer.g_commonParams.spritePriosRatios.x >> 3u) & 31u;
                _1218 = true;
                _1219 = false;
                _1220 = 0u;
                break;
            }
        }
        uint _841;
        if (((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 1u) & 1u) != 0u)
        {
            uint _828;
            if (_228)
            {
                uint _818 = (_189 + _427) + (_187 + (_422 * _188));
                _828 = (g_spriteFB_1._m0[(_818 & 4294967292u) >> 2u] >> (((_818 & 3u) * 8u) & 31u)) & 255u;
            }
            else
            {
                uint _805 = _427 + (_187 + (_422 * _188));
                _828 = (g_spriteFB_1._m0[(_805 & 4294967292u) >> 2u] >> (((_805 & 3u) * 8u) & 31u)) & 255u;
            }
            bool _836;
            if (_146 < 8u)
            {
                bool _835;
                if (_228)
                {
                    _835 = _828 != 0u;
                }
                else
                {
                    _835 = true;
                }
                _836 = _835;
            }
            else
            {
                _836 = false;
            }
            uint _840;
            if (_836)
            {
                _840 = _828 | 65280u;
            }
            else
            {
                _840 = _828;
            }
            _841 = _840;
        }
        else
        {
            uint _762 = _427 << 1u;
            uint _799;
            if (_228)
            {
                uint _785 = (_189 + _762) + (_187 + (_422 * _188));
                uint _793 = g_spriteFB_1._m0[(_785 & 4294967292u) >> 2u] >> (((_785 & 2u) * 8u) & 31u);
                _799 = ((_793 >> 8u) & 255u) | ((_793 << 8u) & 65280u);
            }
            else
            {
                uint _768 = _762 + (_187 + (_422 * _188));
                uint _776 = g_spriteFB_1._m0[(_768 & 4294967292u) >> 2u] >> (((_768 & 2u) * 8u) & 31u);
                _799 = ((_776 >> 8u) & 255u) | ((_776 << 8u) & 65280u);
            }
            _841 = _799;
        }
        uint _1137;
        uint _1138;
        uint _1139;
        bool _1140;
        uint _1141;
        switch (_146)
        {
            case 0u:
            {
                uint _1120 = _841 & 2047u;
                uint _1136;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _1136 = 2u;
                        break;
                    }
                    else
                    {
                        if (_1120 == 2046u)
                        {
                            _1136 = 1u;
                            break;
                        }
                        else
                        {
                            _1136 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _1120;
                _1138 = (_841 >> 11u) & 7u;
                _1139 = (_841 >> 14u) & 3u;
                _1140 = false;
                _1141 = _1136;
                break;
            }
            case 1u:
            {
                uint _1103 = _841 & 2047u;
                uint _1119;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _1119 = 2u;
                        break;
                    }
                    else
                    {
                        if (_1103 == 2046u)
                        {
                            _1119 = 1u;
                            break;
                        }
                        else
                        {
                            _1119 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _1103;
                _1138 = (_841 >> 11u) & 3u;
                _1139 = (_841 >> 13u) & 7u;
                _1140 = false;
                _1141 = _1119;
                break;
            }
            case 2u:
            {
                uint _1083 = _841 & 2047u;
                uint _1102;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _1102 = 2u;
                        break;
                    }
                    else
                    {
                        if (_1083 == 2046u)
                        {
                            _1102 = 1u;
                            break;
                        }
                        else
                        {
                            _1102 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _1083;
                _1138 = (_841 >> 11u) & 7u;
                _1139 = (_841 >> 14u) & 1u;
                _1140 = ((_841 >> 15u) & 1u) != 0u;
                _1141 = _1102;
                break;
            }
            case 3u:
            {
                uint _1063 = _841 & 2047u;
                uint _1082;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _1082 = 2u;
                        break;
                    }
                    else
                    {
                        if (_1063 == 2046u)
                        {
                            _1082 = 1u;
                            break;
                        }
                        else
                        {
                            _1082 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _1063;
                _1138 = (_841 >> 11u) & 3u;
                _1139 = (_841 >> 13u) & 3u;
                _1140 = ((_841 >> 15u) & 1u) != 0u;
                _1141 = _1082;
                break;
            }
            case 4u:
            {
                uint _1043 = _841 & 1023u;
                uint _1062;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _1062 = 2u;
                        break;
                    }
                    else
                    {
                        if (_1043 == 1022u)
                        {
                            _1062 = 1u;
                            break;
                        }
                        else
                        {
                            _1062 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _1043;
                _1138 = (_841 >> 10u) & 7u;
                _1139 = (_841 >> 13u) & 3u;
                _1140 = ((_841 >> 15u) & 1u) != 0u;
                _1141 = _1062;
                break;
            }
            case 5u:
            {
                uint _1023 = _841 & 2047u;
                uint _1042;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _1042 = 2u;
                        break;
                    }
                    else
                    {
                        if (_1023 == 2046u)
                        {
                            _1042 = 1u;
                            break;
                        }
                        else
                        {
                            _1042 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _1023;
                _1138 = (_841 >> 11u) & 1u;
                _1139 = (_841 >> 12u) & 7u;
                _1140 = ((_841 >> 15u) & 1u) != 0u;
                _1141 = _1042;
                break;
            }
            case 6u:
            {
                uint _1003 = _841 & 1023u;
                uint _1022;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _1022 = 2u;
                        break;
                    }
                    else
                    {
                        if (_1003 == 1022u)
                        {
                            _1022 = 1u;
                            break;
                        }
                        else
                        {
                            _1022 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _1003;
                _1138 = (_841 >> 10u) & 3u;
                _1139 = (_841 >> 12u) & 7u;
                _1140 = ((_841 >> 15u) & 1u) != 0u;
                _1141 = _1022;
                break;
            }
            case 7u:
            {
                uint _983 = _841 & 511u;
                uint _1002;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _1002 = 2u;
                        break;
                    }
                    else
                    {
                        if (_983 == 510u)
                        {
                            _1002 = 1u;
                            break;
                        }
                        else
                        {
                            _1002 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _983;
                _1138 = (_841 >> 9u) & 7u;
                _1139 = (_841 >> 12u) & 7u;
                _1140 = ((_841 >> 15u) & 1u) != 0u;
                _1141 = _1002;
                break;
            }
            case 8u:
            {
                uint _968 = _841 & 127u;
                uint _982;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _982 = 2u;
                        break;
                    }
                    else
                    {
                        if (_968 == 126u)
                        {
                            _982 = 1u;
                            break;
                        }
                        else
                        {
                            _982 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _968;
                _1138 = 0u;
                _1139 = (_841 >> 7u) & 1u;
                _1140 = false;
                _1141 = _982;
                break;
            }
            case 9u:
            {
                uint _951 = _841 & 63u;
                uint _967;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _967 = 2u;
                        break;
                    }
                    else
                    {
                        if (_951 == 62u)
                        {
                            _967 = 1u;
                            break;
                        }
                        else
                        {
                            _967 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _951;
                _1138 = (_841 >> 6u) & 1u;
                _1139 = (_841 >> 7u) & 1u;
                _1140 = false;
                _1141 = _967;
                break;
            }
            case 10u:
            {
                uint _936 = _841 & 63u;
                uint _950;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _950 = 2u;
                        break;
                    }
                    else
                    {
                        if (_936 == 62u)
                        {
                            _950 = 1u;
                            break;
                        }
                        else
                        {
                            _950 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _936;
                _1138 = 0u;
                _1139 = (_841 >> 6u) & 3u;
                _1140 = false;
                _1141 = _950;
                break;
            }
            case 11u:
            {
                uint _921 = _841 & 63u;
                uint _935;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _935 = 2u;
                        break;
                    }
                    else
                    {
                        if (_921 == 62u)
                        {
                            _935 = 1u;
                            break;
                        }
                        else
                        {
                            _935 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _921;
                _1138 = (_841 >> 6u) & 3u;
                _1139 = 0u;
                _1140 = false;
                _1141 = _935;
                break;
            }
            case 12u:
            {
                uint _906 = _841 & 255u;
                uint _920;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _920 = 2u;
                        break;
                    }
                    else
                    {
                        if (_906 == 254u)
                        {
                            _920 = 1u;
                            break;
                        }
                        else
                        {
                            _920 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _906;
                _1138 = 0u;
                _1139 = (_841 >> 7u) & 1u;
                _1140 = false;
                _1141 = _920;
                break;
            }
            case 13u:
            {
                uint _889 = _841 & 255u;
                uint _905;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _905 = 2u;
                        break;
                    }
                    else
                    {
                        if (_889 == 254u)
                        {
                            _905 = 1u;
                            break;
                        }
                        else
                        {
                            _905 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _889;
                _1138 = (_841 >> 6u) & 1u;
                _1139 = (_841 >> 7u) & 1u;
                _1140 = false;
                _1141 = _905;
                break;
            }
            case 14u:
            {
                uint _874 = _841 & 255u;
                uint _888;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _888 = 2u;
                        break;
                    }
                    else
                    {
                        if (_874 == 254u)
                        {
                            _888 = 1u;
                            break;
                        }
                        else
                        {
                            _888 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _874;
                _1138 = 0u;
                _1139 = (_841 >> 6u) & 3u;
                _1140 = false;
                _1141 = _888;
                break;
            }
            case 15u:
            {
                uint _859 = _841 & 255u;
                uint _873;
                do
                {
                    if ((_841 & 32767u) == 0u)
                    {
                        _873 = 2u;
                        break;
                    }
                    else
                    {
                        if (_859 == 254u)
                        {
                            _873 = 1u;
                            break;
                        }
                        else
                        {
                            _873 = 0u;
                            break;
                        }
                        break; // unreachable workaround
                    }
                    break; // unreachable workaround
                } while(false);
                _1137 = _859;
                _1138 = (_841 >> 6u) & 3u;
                _1139 = 0u;
                _1140 = false;
                _1141 = _873;
                break;
            }
            default:
            {
                _1137 = _107;
                _1138 = _107;
                _1139 = _107;
                _1140 = _108;
                _1141 = _107;
                break;
            }
        }
        bool _1152;
        if (_175 ? (((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 21u) & 1u) != 0u) : false)
        {
            _1152 = _1140 != (((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 22u) & 1u) != 0u);
        }
        else
        {
            _1152 = false;
        }
        if (_1152)
        {
            _1216 = uvec4(0u);
            _1217 = 0u;
            _1218 = false;
            _1219 = true;
            _1220 = 0u;
            break;
        }
        uvec4 _1161 = texelFetch(g_cramColor, int(((((CommonRenderParamsBuffer.g_commonParams.spriteParams >> 17u) & 7u) << 8u) + _1137) & uint((((CommonRenderParamsBuffer.g_commonParams.displayParams >> 6u) & 3u) == 1u) ? 2047 : 1023)));
        bool _1166;
        if (_1141 == 2u)
        {
            _1166 = !_1140;
        }
        else
        {
            _1166 = false;
        }
        uint _1188;
        if (_1166)
        {
            _1188 = 0u;
        }
        else
        {
            uint _1172 = _1139 * 8u;
            uint _1187;
            do
            {
                if (_1172 < 32u)
                {
                    _1187 = (CommonRenderParamsBuffer.g_commonParams.spritePriosRatios.x >> (_1172 & 31u)) & 7u;
                    break;
                }
                else
                {
                    _1187 = (CommonRenderParamsBuffer.g_commonParams.spritePriosRatios.y >> (_1172 & 31u)) & 7u;
                    break;
                }
                break; // unreachable workaround
            } while(false);
            _1188 = _1187;
        }
        uint _1196 = _1138 * 8u;
        uint _1197 = _1196 + 3u;
        uint _1213;
        do
        {
            if (_1197 < 32u)
            {
                _1213 = (CommonRenderParamsBuffer.g_commonParams.spritePriosRatios.x >> (_1197 & 31u)) & 31u;
                break;
            }
            else
            {
                _1213 = (CommonRenderParamsBuffer.g_commonParams.spritePriosRatios.y >> ((_1196 + 4294967267u) & 31u)) & 31u;
                break;
            }
            break; // unreachable workaround
        } while(false);
        _1216 = uvec4(_1161.xyz, _1188 | 128u);
        _1217 = _1213;
        _1218 = _1161.w != 0u;
        _1219 = _1140;
        _1220 = _1141;
        break;
    } while(false);
    uint _1222 = _223.x;
    imageStore(g_layerOut, ivec3(uvec3(_1222, _223.y, gl_GlobalInvocationID.z + 6u)), _1216);
    uint _1227 = 32u;
    uint _1228 = 0u;
    uint _1232 = 64u;
    uint _1233 = 0u;
    imageStore(g_spriteAttrsOut, ivec3(uvec3(_1222, _223.y, gl_GlobalInvocationID.z)), uvec4(((_1217 | (_1218 ? 32u : 0u)) | (_1219 ? 64u : 0u)) | (_1220 << 7u)));
}

