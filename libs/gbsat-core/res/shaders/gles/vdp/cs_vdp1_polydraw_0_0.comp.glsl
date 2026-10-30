#version 310 es
#extension GL_EXT_texture_buffer : require
layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;

struct CommonRenderParams
{
    uint displayParams;
    uint enhancements;
};

struct PolyDrawParams
{
    uint numSpans;
};

struct PolySpan
{
    ivec2 coord0;
    ivec2 coord1;
    uint skip_cmdIndex;
    uint attrs;
    uvec3 gouraud0;
    uvec3 gouraud1;
};

struct CommandParams
{
    uint sysClip;
    uint userClip0;
    uint userClip1;
    uint cmdpmodcolr;
    uint cmdsizesrca;
};

uint _76;
bool _77;
ivec2 _85;

layout(binding = 0, std140) uniform type_RenderParamsBuffer
{
    CommonRenderParams g_commonParams;
    PolyDrawParams g_polyDrawParams;
} RenderParamsBuffer;

layout(binding = 1, std430) readonly buffer type_StructuredBuffer_PolySpan
{
    PolySpan _m0[];
} g_spanParams;

layout(binding = 3, std430) readonly buffer type_StructuredBuffer_CommandParams
{
    CommandParams _m0[];
} g_commandParams;

layout(binding = 4, std430) readonly buffer type_ByteAddressBuffer
{
    uint _m0[];
} g_vram;

layout(binding = 17, std430) buffer type_RWStructuredBuffer_uint
{
    uint _m0[];
} g_internalSpriteOut;

layout(binding = 2) uniform highp usamplerBuffer g_spanPrefixSums;

void main()
{
    uint _101 = max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uvec2 _103 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(_101);
    bool _106 = ((RenderParamsBuffer.g_commonParams.displayParams >> 2u) & 1u) != 0u;
    bool _109 = ((RenderParamsBuffer.g_commonParams.displayParams >> 3u) & 1u) != 0u;
    bool _112 = ((RenderParamsBuffer.g_commonParams.displayParams >> 4u) & 1u) != 0u;
    uint _114 = (RenderParamsBuffer.g_commonParams.displayParams >> 5u) & 1u;
    bool _116 = (RenderParamsBuffer.g_commonParams.enhancements & 1u) != 0u;
    do
    {
        uint _157;
        do
        {
            if (gl_GlobalInvocationID.x >= texelFetch(g_spanPrefixSums, int(RenderParamsBuffer.g_polyDrawParams.numSpans)).x)
            {
                _157 = 4294967295u;
                break;
            }
            uint _135;
            _135 = 0u;
            uint _133;
            uint _136;
            uint _153;
            bool _154;
            uint _132 = RenderParamsBuffer.g_polyDrawParams.numSpans;
            for (;;)
            {
                if (_135 != _132)
                {
                    uint _141 = (_135 + _132) >> 1u;
                    uvec4 _143 = texelFetch(g_spanPrefixSums, int(_141));
                    uint _144 = _143.x;
                    if (gl_GlobalInvocationID.x == _144)
                    {
                        _153 = _141;
                        _154 = true;
                        break;
                    }
                    bool _148 = gl_GlobalInvocationID.x > _144;
                    if (_148)
                    {
                        _136 = _141 + 1u;
                    }
                    else
                    {
                        _136 = _135;
                    }
                    _133 = _148 ? _132 : _141;
                    _132 = _133;
                    _135 = _136;
                    continue;
                }
                else
                {
                    _153 = _76;
                    _154 = false;
                    break;
                }
            }
            if (_154)
            {
                _157 = _153;
                break;
            }
            _157 = _135 - 1u;
            break;
        } while(false);
        if (_157 == 4294967295u)
        {
            break;
        }
        uint _166 = g_spanParams._m0[_157].skip_cmdIndex;
        uint _168 = g_spanParams._m0[_157].attrs;
        uint _178 = (gl_GlobalInvocationID.x - texelFetch(g_spanPrefixSums, int(_157)).x) + (_166 & 65535u);
        uint _180 = (_166 >> 16u) & 65535u;
        bool _186 = (_168 & 1u) != 0u;
        uint _190 = g_commandParams._m0[_180].cmdpmodcolr >> 16u;
        uint _191 = _190 & 65535u;
        ivec2 _192 = g_spanParams._m0[_157].coord1 - g_spanParams._m0[_157].coord0;
        ivec2 _193 = abs(_192);
        int _194 = _193.x;
        int _195 = _193.y;
        uint _197 = uint(max(_194, _195));
        bool _198 = _194 >= _195;
        ivec2 _220;
        ivec2 _221;
        ivec2 _222;
        ivec2 _223;
        if (_198)
        {
            _220 = ivec2(0, (_192.y >= 0) ? 1 : (-1));
            _221 = ivec2((_192.x >= 0) ? 1 : (-1), 0);
            _222 = _192;
            _223 = _193;
        }
        else
        {
            _220 = ivec2((_192.x >= 0) ? 1 : (-1), 0);
            _221 = ivec2(0, (_192.y >= 0) ? 1 : (-1));
            _222 = _192.yx;
            _223 = _193.yx;
        }
        int _225 = _223.y << 1;
        int _227 = _223.x << 1;
        bool _234;
        if (!_186)
        {
            _234 = _222.x < 0;
        }
        else
        {
            _234 = false;
        }
        int _236 = (_223.x + 1) + _225;
        ivec2 _237 = g_spanParams._m0[_157].coord0 - _221;
        ivec2 _281;
        int _282;
        int _283;
        if (_186)
        {
            int _241 = 0;
            int _242 = -1;
            bool _250 = (g_spanParams._m0[_157].coord0.x > g_spanParams._m0[_157].coord1.x) == (g_spanParams._m0[_157].coord0.y > g_spanParams._m0[_157].coord1.y);
            ivec2 _280;
            if (_198)
            {
                int _272;
                if (_250)
                {
                    _272 = 0;
                }
                else
                {
                    _272 = -_221.x;
                }
                int _278;
                if (_250)
                {
                    _278 = -_220.y;
                }
                else
                {
                    _278 = 0;
                }
                _280 = ivec2(_272, _278);
            }
            else
            {
                int _259;
                if (_250)
                {
                    _259 = 0;
                }
                else
                {
                    _259 = -_220.x;
                }
                int _265;
                if (_250)
                {
                    _265 = -_221.y;
                }
                else
                {
                    _265 = 0;
                }
                _280 = ivec2(_259, _265);
            }
            _281 = _280;
            _282 = _234 ? 0 : (-1);
            _283 = _236 - 1;
        }
        else
        {
            _281 = _85;
            _282 = int(_234);
            _283 = _236;
        }
        int _310;
        uint _311;
        ivec2 _312;
        do
        {
            uint _287 = min(_178, _197) + 1u;
            int _288 = int(_287);
            if (_288 == 0)
            {
                _310 = _283;
                _311 = 0u;
                _312 = _237;
                break;
            }
            ivec2 _294 = _237 + (_221 * ivec2(_288));
            int _296 = _283 - (_225 * _288);
            int _308;
            ivec2 _309;
            if (_227 != 0)
            {
                int _302 = ((_282 - _296) + _227) / _227;
                _308 = _296 + (_227 * _302);
                _309 = _294 + (_220 * ivec2(_302));
            }
            else
            {
                _308 = _296;
                _309 = _294;
            }
            _310 = _308;
            _311 = _287;
            _312 = _309;
            break;
        } while(false);
        uint _585;
        if (((_168 >> 1u) & 1u) != 0u)
        {
            uint _323 = max((((g_commandParams._m0[_180].cmdsizesrca >> 8u) & 63u) << 3u), 1u);
            bool _326 = ((_168 >> 2u) & 1u) != 0u;
            bool _333 = ((g_commandParams._m0[_180].cmdpmodcolr >> 7u) & 1u) == 0u;
            bool _341;
            if (((g_commandParams._m0[_180].cmdpmodcolr >> 12u) & 1u) != 0u)
            {
                _341 = _197 < (_323 - 1u);
            }
            else
            {
                _341 = false;
            }
            int _346 = int(_323 - 1u);
            int _347 = _326 ? 0 : _346;
            int _348 = _326 ? _346 : 0;
            uint _349 = _197 + 1u;
            int _355;
            int _356;
            if (_341)
            {
                _355 = _348 >> 1;
                _356 = _347 >> 1;
            }
            else
            {
                _355 = _348;
                _356 = _347;
            }
            int _357 = _356 - _355;
            int _358 = abs(_357);
            uint _359 = uint(_358);
            bool _360 = _357 >= 0;
            int _369;
            int _370;
            if (_341)
            {
                int _366 = 2;
                int _367 = -2;
                _369 = (_355 << 1) | int(((RenderParamsBuffer.g_commonParams.displayParams >> 6u) & 1u) != 0u);
                _370 = _360 ? 2 : (-2);
            }
            else
            {
                _369 = _355;
                _370 = _360 ? 1 : (-1);
            }
            int _371 = int(_349);
            int _393;
            int _394;
            int _395;
            if (_349 <= _359)
            {
                int _388 = int(_359 - (_349 << 1u));
                int _392;
                if (_360)
                {
                    _392 = _388 + 1;
                }
                else
                {
                    _392 = _388;
                }
                _393 = _392;
                _394 = _371;
                _395 = _358 + 1;
            }
            else
            {
                int _379 = int(_349 - (_349 << 1u));
                int _384;
                if (_357 < 0)
                {
                    _384 = _379 + 1;
                }
                else
                {
                    _384 = _379;
                }
                _393 = _384;
                _394 = _371 - 1;
                _395 = _358;
            }
            int _402 = int(uint(_393) + (uint(_395 << 1) * _178));
            int _410;
            if (_402 >= 0)
            {
                _410 = _369 + (_370 * ((_402 / (_394 << 1)) + 1));
            }
            else
            {
                _410 = _369;
            }
            bool _414;
            if (_333)
            {
                _414 = !_341;
            }
            else
            {
                _414 = false;
            }
            uint _421;
            bool _422;
            if (_414)
            {
                uint _419 = (_168 >> 13u) & 1023u;
                _421 = _419;
                _422 = _419 < _323;
            }
            else
            {
                _421 = _76;
                _422 = false;
            }
            uint _423 = uint(_410);
            bool _432;
            if (_422)
            {
                bool _431;
                if (_326)
                {
                    _431 = _423 <= _421;
                }
                else
                {
                    _431 = _423 >= _421;
                }
                _432 = _431;
            }
            else
            {
                _432 = false;
            }
            if (_432)
            {
                break;
            }
            uint _439 = ((g_commandParams._m0[_180].cmdsizesrca >> 16u) & 65535u) << 3u;
            uint _441 = _423 + (((_168 >> 3u) & 1023u) * _323);
            uint _571;
            bool _572;
            bool _573;
            switch ((g_commandParams._m0[_180].cmdpmodcolr >> 3u) & 7u)
            {
                case 0u:
                {
                    uint _551 = _439 + (_441 >> 1u);
                    uint _566 = (((g_vram._m0[(_551 & 524284u) >> 2u] >> (((_551 & 3u) * 8u) & 31u)) & 255u) >> ((((~_423) & 1u) * 4u) & 31u)) & 15u;
                    _571 = _566 | (_190 & 65520u);
                    _572 = _566 == 0u;
                    _573 = _566 == 15u;
                    break;
                }
                case 1u:
                {
                    uint _515 = _439 + (_441 >> 1u);
                    uint _530 = (((g_vram._m0[(_515 & 524284u) >> 2u] >> (((_515 & 3u) * 8u) & 31u)) & 255u) >> ((((~_423) & 1u) * 4u) & 31u)) & 15u;
                    uint _535 = (_530 * 2u) + (_191 * 8u);
                    uint _544 = (g_vram._m0[(_535 & 524284u) >> 2u] >> (((_535 & 2u) * 8u) & 31u)) & 65535u;
                    _571 = ((_544 >> 8u) & 255u) | ((_544 << 8u) & 65280u);
                    _572 = _530 == 0u;
                    _573 = _530 == 15u;
                    break;
                }
                case 2u:
                {
                    uint _499 = _439 + _441;
                    uint _507 = g_vram._m0[(_499 & 524284u) >> 2u] >> (((_499 & 3u) * 8u) & 31u);
                    uint _508 = _507 & 255u;
                    _571 = (_507 & 63u) | (_190 & 65472u);
                    _572 = _508 == 0u;
                    _573 = _508 == 255u;
                    break;
                }
                case 3u:
                {
                    uint _484 = _439 + _441;
                    uint _492 = g_vram._m0[(_484 & 524284u) >> 2u] >> (((_484 & 3u) * 8u) & 31u);
                    uint _493 = _492 & 255u;
                    _571 = (_492 & 127u) | (_190 & 65408u);
                    _572 = _493 == 0u;
                    _573 = _493 == 255u;
                    break;
                }
                case 4u:
                {
                    uint _470 = _439 + _441;
                    uint _479 = (g_vram._m0[(_470 & 524284u) >> 2u] >> (((_470 & 3u) * 8u) & 31u)) & 255u;
                    _571 = _479 | (_190 & 65280u);
                    _572 = _479 == 0u;
                    _573 = _479 == 255u;
                    break;
                }
                case 5u:
                {
                    uint _451 = (_439 & 4294967280u) + (_441 * 2u);
                    uint _460 = (g_vram._m0[(_451 & 524284u) >> 2u] >> (((_451 & 2u) * 8u) & 31u)) & 65535u;
                    uint _465 = ((_460 >> 8u) & 255u) | ((_460 << 8u) & 65280u);
                    _571 = _465;
                    _572 = ((_465 >> 15u) & 1u) == 0u;
                    _573 = _465 == 32767u;
                    break;
                }
                default:
                {
                    _571 = _76;
                    _572 = _77;
                    _573 = _77;
                    break;
                }
            }
            bool _582;
            if (!(_573 ? _333 : false))
            {
                bool _581;
                if (_572)
                {
                    _581 = ((g_commandParams._m0[_180].cmdpmodcolr >> 6u) & 1u) == 0u;
                }
                else
                {
                    _581 = false;
                }
                _582 = _581;
            }
            else
            {
                _582 = true;
            }
            if (_582)
            {
                break;
            }
            _585 = _571;
        }
        else
        {
            uint _319;
            if (_106)
            {
                _319 = _190 & 255u;
            }
            else
            {
                _319 = _191;
            }
            _585 = _319;
        }
        uint _586 = g_commandParams._m0[_180].cmdpmodcolr & 3u;
        bool _589 = ((g_commandParams._m0[_180].cmdpmodcolr >> 2u) & 1u) != 0u;
        bool _598;
        if (!_106)
        {
            bool _597;
            if (!_589)
            {
                _597 = _586 == 2u;
            }
            else
            {
                _597 = true;
            }
            _598 = _597;
        }
        else
        {
            _598 = false;
        }
        uint _876;
        if (_598)
        {
            uint _601 = _585 & 31u;
            uint _603 = (_585 >> 5u) & 31u;
            uint _605 = (_585 >> 10u) & 31u;
            uint _607 = (_585 >> 15u) & 1u;
            uvec4 _852;
            if (_589)
            {
                uint _611 = _197 + 1u;
                int _613 = int(g_spanParams._m0[_157].gouraud0.x);
                int _616 = int(g_spanParams._m0[_157].gouraud1.x) - _613;
                int _617 = abs(_616);
                uint _618 = uint(_617);
                bool _619 = _616 >= 0;
                int _620 = _619 ? 1 : (-1);
                int _621 = int(_611);
                int _641;
                int _642;
                int _643;
                if (_611 <= _618)
                {
                    int _636 = int(_618 - (_611 << 1u));
                    int _640;
                    if (_619)
                    {
                        _640 = _636 + 1;
                    }
                    else
                    {
                        _640 = _636;
                    }
                    _641 = _640;
                    _642 = _621;
                    _643 = _617 + 1;
                }
                else
                {
                    int _632;
                    if (_616 < 0)
                    {
                        _632 = 1 - _621;
                    }
                    else
                    {
                        _632 = -_621;
                    }
                    _641 = _632;
                    _642 = _621 - 1;
                    _643 = _617;
                }
                int _644 = _643 << 1;
                int _645 = _642 << 1;
                bool _646 = _645 != 0;
                int _665;
                int _666;
                int _667;
                int _668;
                if (_646)
                {
                    int _650;
                    int _653;
                    _650 = _613;
                    _653 = _641;
                    for (; _653 >= 0; )
                    {
                        _650 += _620;
                        _653 -= _645;
                        continue;
                    }
                    int _658;
                    int _661;
                    _658 = 0;
                    _661 = _644;
                    for (; _661 >= _645; )
                    {
                        _658 += _620;
                        _661 -= _645;
                        continue;
                    }
                    _665 = _661;
                    _666 = _650;
                    _667 = _658;
                    _668 = _653;
                }
                else
                {
                    _665 = _644;
                    _666 = _613;
                    _667 = 0;
                    _668 = _641;
                }
                int _671 = int(g_spanParams._m0[_157].gouraud0.y);
                int _674 = int(g_spanParams._m0[_157].gouraud1.y) - _671;
                int _675 = abs(_674);
                uint _676 = uint(_675);
                bool _677 = _674 >= 0;
                int _678 = _677 ? 1 : (-1);
                int _698;
                int _699;
                int _700;
                if (_611 <= _676)
                {
                    int _693 = int(_676 - (_611 << 1u));
                    int _697;
                    if (_677)
                    {
                        _697 = _693 + 1;
                    }
                    else
                    {
                        _697 = _693;
                    }
                    _698 = _697;
                    _699 = _621;
                    _700 = _675 + 1;
                }
                else
                {
                    int _689;
                    if (_674 < 0)
                    {
                        _689 = 1 - _621;
                    }
                    else
                    {
                        _689 = -_621;
                    }
                    _698 = _689;
                    _699 = _621 - 1;
                    _700 = _675;
                }
                int _701 = _700 << 1;
                int _702 = _699 << 1;
                bool _703 = _702 != 0;
                int _722;
                int _723;
                int _724;
                int _725;
                if (_703)
                {
                    int _707;
                    int _710;
                    _707 = _671;
                    _710 = _698;
                    for (; _710 >= 0; )
                    {
                        _707 += _678;
                        _710 -= _702;
                        continue;
                    }
                    int _715;
                    int _718;
                    _715 = 0;
                    _718 = _701;
                    for (; _718 >= _702; )
                    {
                        _715 += _678;
                        _718 -= _702;
                        continue;
                    }
                    _722 = _718;
                    _723 = _707;
                    _724 = _715;
                    _725 = _710;
                }
                else
                {
                    _722 = _701;
                    _723 = _671;
                    _724 = 0;
                    _725 = _698;
                }
                int _728 = int(g_spanParams._m0[_157].gouraud0.z);
                int _731 = int(g_spanParams._m0[_157].gouraud1.z) - _728;
                int _732 = abs(_731);
                uint _733 = uint(_732);
                bool _734 = _731 >= 0;
                int _735 = _734 ? 1 : (-1);
                int _755;
                int _756;
                int _757;
                if (_611 <= _733)
                {
                    int _750 = int(_733 - (_611 << 1u));
                    int _754;
                    if (_734)
                    {
                        _754 = _750 + 1;
                    }
                    else
                    {
                        _754 = _750;
                    }
                    _755 = _754;
                    _756 = _621;
                    _757 = _732 + 1;
                }
                else
                {
                    int _746;
                    if (_731 < 0)
                    {
                        _746 = 1 - _621;
                    }
                    else
                    {
                        _746 = -_621;
                    }
                    _755 = _746;
                    _756 = _621 - 1;
                    _757 = _732;
                }
                int _758 = _757 << 1;
                int _759 = _756 << 1;
                bool _760 = _759 != 0;
                int _779;
                int _780;
                int _781;
                int _782;
                if (_760)
                {
                    int _764;
                    int _767;
                    _764 = _728;
                    _767 = _755;
                    for (; _767 >= 0; )
                    {
                        _764 += _735;
                        _767 -= _759;
                        continue;
                    }
                    int _772;
                    int _775;
                    _772 = 0;
                    _775 = _758;
                    for (; _775 >= _759; )
                    {
                        _772 += _735;
                        _775 -= _759;
                        continue;
                    }
                    _779 = _775;
                    _780 = _764;
                    _781 = _772;
                    _782 = _767;
                }
                else
                {
                    _779 = _758;
                    _780 = _728;
                    _781 = 0;
                    _782 = _755;
                }
                int _784 = int(_178);
                int _833;
                int _834;
                int _835;
                if (_784 > 0)
                {
                    int _789 = _666 + (_667 * _784);
                    int _791 = (~_668) - (_665 * _784);
                    int _802;
                    if (_646)
                    {
                        int _795;
                        _795 = _789;
                        for (int _798 = _791; _798 < 0; )
                        {
                            _795 += _620;
                            _798 += _645;
                            continue;
                        }
                        _802 = _795;
                    }
                    else
                    {
                        _802 = _789;
                    }
                    int _804 = _723 + (_724 * _784);
                    int _806 = (~_725) - (_722 * _784);
                    int _817;
                    if (_703)
                    {
                        int _810;
                        _810 = _804;
                        for (int _813 = _806; _813 < 0; )
                        {
                            _810 += _678;
                            _813 += _702;
                            continue;
                        }
                        _817 = _810;
                    }
                    else
                    {
                        _817 = _804;
                    }
                    int _819 = _780 + (_781 * _784);
                    int _821 = (~_782) - (_779 * _784);
                    int _832;
                    if (_760)
                    {
                        int _825;
                        _825 = _819;
                        for (int _828 = _821; _828 < 0; )
                        {
                            _825 += _735;
                            _828 += _759;
                            continue;
                        }
                        _832 = _825;
                    }
                    else
                    {
                        _832 = _819;
                    }
                    _833 = _832;
                    _834 = _817;
                    _835 = _802;
                }
                else
                {
                    _833 = _780;
                    _834 = _723;
                    _835 = _666;
                }
                _852 = uvec4(uint(clamp((_835 + int(_601)) - 16, 0, 31)), uint(clamp((_834 + int(_603)) - 16, 0, 31)), uint(clamp((_833 + int(_605)) - 16, 0, 31)), _607);
            }
            else
            {
                _852 = uvec4(_601, _603, _605, _607);
            }
            uvec4 _865;
            if (_586 == 2u)
            {
                uvec4 _858 = _852;
                _858.x = _852.x >> 1u;
                _858.y = _852.y >> 1u;
                _858.z = _852.z >> 1u;
                _865 = _858;
            }
            else
            {
                _865 = _852;
            }
            _876 = ((_865.x | (_865.y << 5u)) | (_865.z << 10u)) | (_865.w << 15u);
        }
        else
        {
            _876 = _585;
        }
        uint _879 = _876 | ((_157 + 1u) << 16u);
        do
        {
            bool _901;
            if (!any(lessThan(_312, ivec2(0))))
            {
                _901 = any(greaterThan(uvec2(_312), uvec2(g_commandParams._m0[_180].sysClip & 65535u, (g_commandParams._m0[_180].sysClip >> 16u) & 65535u)));
            }
            else
            {
                _901 = true;
            }
            if (_901)
            {
                break;
            }
            if (((g_commandParams._m0[_180].cmdpmodcolr >> 10u) & 1u) != 0u)
            {
                uvec2 _920 = uvec2(_312);
                bool _928;
                if (!any(lessThan(_920, uvec2(g_commandParams._m0[_180].userClip0 & 65535u, (g_commandParams._m0[_180].userClip0 >> 16u) & 65535u))))
                {
                    _928 = any(greaterThan(_920, uvec2(g_commandParams._m0[_180].userClip1 & 65535u, (g_commandParams._m0[_180].userClip1 >> 16u) & 65535u)));
                }
                else
                {
                    _928 = true;
                }
                if (_928 != (((g_commandParams._m0[_180].cmdpmodcolr >> 9u) & 1u) != 0u))
                {
                    break;
                }
            }
            bool _946;
            if (((g_commandParams._m0[_180].cmdpmodcolr >> 8u) & 1u) != 0u)
            {
                _946 = (((uint(_312.x) / _101) ^ (uint(_312.y) / _101)) & 1u) != 0u;
            }
            else
            {
                _946 = false;
            }
            if (_946)
            {
                break;
            }
            bool _958;
            if (((!_116) ? _109 : false) ? _112 : false)
            {
                _958 = uint(_312.y & 1) != _114;
            }
            else
            {
                _958 = false;
            }
            if (_958)
            {
                break;
            }
            bool _961 = _116 ? _109 : false;
            bool _967;
            if (_961)
            {
                _967 = (_312.y & 1) != 0;
            }
            else
            {
                _967 = false;
            }
            uint _973;
            if (_967)
            {
                _973 = _103.x * _103.y;
            }
            else
            {
                _973 = 0u;
            }
            ivec2 _981;
            if ((!_961) ? _112 : true)
            {
                ivec2 _980 = _312;
                _980.y = _312.y >> 1;
                _981 = _980;
            }
            else
            {
                _981 = _312;
            }
            uint _991 = atomicMax(g_internalSpriteOut._m0[_973 + ((uint(_981.y) * _103.x) + uint(_981.x))], _879);
            break;
        } while(false);
        bool _1001;
        if (_186)
        {
            bool _1000;
            if (_311 > 1u)
            {
                _1000 = ((_310 - _227) + _225) > _282;
            }
            else
            {
                _1000 = false;
            }
            _1001 = _1000;
        }
        else
        {
            _1001 = false;
        }
        if (_1001)
        {
            ivec2 _1004 = _312 + _281;
            do
            {
                bool _1023;
                if (!any(lessThan(_1004, ivec2(0))))
                {
                    _1023 = any(greaterThan(uvec2(_1004), uvec2(g_commandParams._m0[_180].sysClip & 65535u, (g_commandParams._m0[_180].sysClip >> 16u) & 65535u)));
                }
                else
                {
                    _1023 = true;
                }
                if (_1023)
                {
                    break;
                }
                if (((g_commandParams._m0[_180].cmdpmodcolr >> 10u) & 1u) != 0u)
                {
                    uvec2 _1042 = uvec2(_1004);
                    bool _1050;
                    if (!any(lessThan(_1042, uvec2(g_commandParams._m0[_180].userClip0 & 65535u, (g_commandParams._m0[_180].userClip0 >> 16u) & 65535u))))
                    {
                        _1050 = any(greaterThan(_1042, uvec2(g_commandParams._m0[_180].userClip1 & 65535u, (g_commandParams._m0[_180].userClip1 >> 16u) & 65535u)));
                    }
                    else
                    {
                        _1050 = true;
                    }
                    if (_1050 != (((g_commandParams._m0[_180].cmdpmodcolr >> 9u) & 1u) != 0u))
                    {
                        break;
                    }
                }
                bool _1068;
                if (((g_commandParams._m0[_180].cmdpmodcolr >> 8u) & 1u) != 0u)
                {
                    _1068 = (((uint(_1004.x) / _101) ^ (uint(_1004.y) / _101)) & 1u) != 0u;
                }
                else
                {
                    _1068 = false;
                }
                if (_1068)
                {
                    break;
                }
                bool _1080;
                if (((!_116) ? _109 : false) ? _112 : false)
                {
                    _1080 = uint(_1004.y & 1) != _114;
                }
                else
                {
                    _1080 = false;
                }
                if (_1080)
                {
                    break;
                }
                bool _1083 = _116 ? _109 : false;
                bool _1089;
                if (_1083)
                {
                    _1089 = (_1004.y & 1) != 0;
                }
                else
                {
                    _1089 = false;
                }
                uint _1095;
                if (_1089)
                {
                    _1095 = _103.x * _103.y;
                }
                else
                {
                    _1095 = 0u;
                }
                ivec2 _1103;
                if ((!_1083) ? _112 : true)
                {
                    ivec2 _1102 = _1004;
                    _1102.y = _1004.y >> 1;
                    _1103 = _1102;
                }
                else
                {
                    _1103 = _1004;
                }
                uint _1113 = atomicMax(g_internalSpriteOut._m0[_1095 + ((uint(_1103.y) * _103.x) + uint(_1103.x))], _879);
                break;
            } while(false);
        }
        break;
    } while(false);
}

