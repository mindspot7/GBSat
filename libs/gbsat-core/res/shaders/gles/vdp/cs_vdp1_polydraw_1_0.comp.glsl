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
    uvec2 _103 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u));
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
                int _259;
                if (_250)
                {
                    _259 = 0;
                }
                else
                {
                    _259 = -_221.x;
                }
                int _265;
                if (_250)
                {
                    _265 = -_220.y;
                }
                else
                {
                    _265 = 0;
                }
                _280 = ivec2(_259, _265);
            }
            else
            {
                int _272;
                if (_250)
                {
                    _272 = 0;
                }
                else
                {
                    _272 = -_220.x;
                }
                int _278;
                if (_250)
                {
                    _278 = -_221.y;
                }
                else
                {
                    _278 = 0;
                }
                _280 = ivec2(_272, _278);
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
            uint _319 = max((((g_commandParams._m0[_180].cmdsizesrca >> 8u) & 63u) << 3u), 1u);
            bool _322 = ((_168 >> 2u) & 1u) != 0u;
            bool _329 = ((g_commandParams._m0[_180].cmdpmodcolr >> 7u) & 1u) == 0u;
            bool _337;
            if (((g_commandParams._m0[_180].cmdpmodcolr >> 12u) & 1u) != 0u)
            {
                _337 = _197 < (_319 - 1u);
            }
            else
            {
                _337 = false;
            }
            int _342 = int(_319 - 1u);
            int _343 = _322 ? 0 : _342;
            int _344 = _322 ? _342 : 0;
            uint _345 = _197 + 1u;
            int _351;
            int _352;
            if (_337)
            {
                _351 = _344 >> 1;
                _352 = _343 >> 1;
            }
            else
            {
                _351 = _344;
                _352 = _343;
            }
            int _353 = _352 - _351;
            int _354 = abs(_353);
            uint _355 = uint(_354);
            bool _356 = _353 >= 0;
            int _365;
            int _366;
            if (_337)
            {
                int _362 = 2;
                int _363 = -2;
                _365 = (_351 << 1) | int(((RenderParamsBuffer.g_commonParams.displayParams >> 6u) & 1u) != 0u);
                _366 = _356 ? 2 : (-2);
            }
            else
            {
                _365 = _351;
                _366 = _356 ? 1 : (-1);
            }
            int _367 = int(_345);
            int _389;
            int _390;
            int _391;
            if (_345 <= _355)
            {
                int _375 = int(_355 - (_345 << 1u));
                int _379;
                if (_356)
                {
                    _379 = _375 + 1;
                }
                else
                {
                    _379 = _375;
                }
                _389 = _379;
                _390 = _367;
                _391 = _354 + 1;
            }
            else
            {
                int _383 = int(_345 - (_345 << 1u));
                int _388;
                if (_353 < 0)
                {
                    _388 = _383 + 1;
                }
                else
                {
                    _388 = _383;
                }
                _389 = _388;
                _390 = _367 - 1;
                _391 = _354;
            }
            int _398 = int(uint(_389) + (uint(_391 << 1) * _178));
            int _406;
            if (_398 >= 0)
            {
                _406 = _365 + (_366 * ((_398 / (_390 << 1)) + 1));
            }
            else
            {
                _406 = _365;
            }
            bool _410;
            if (_329)
            {
                _410 = !_337;
            }
            else
            {
                _410 = false;
            }
            uint _417;
            bool _418;
            if (_410)
            {
                uint _415 = (_168 >> 13u) & 1023u;
                _417 = _415;
                _418 = _415 < _319;
            }
            else
            {
                _417 = _76;
                _418 = false;
            }
            uint _419 = uint(_406);
            bool _428;
            if (_418)
            {
                bool _427;
                if (_322)
                {
                    _427 = _419 <= _417;
                }
                else
                {
                    _427 = _419 >= _417;
                }
                _428 = _427;
            }
            else
            {
                _428 = false;
            }
            if (_428)
            {
                break;
            }
            uint _435 = ((g_commandParams._m0[_180].cmdsizesrca >> 16u) & 65535u) << 3u;
            uint _437 = _419 + (((_168 >> 3u) & 1023u) * _319);
            uint _567;
            bool _568;
            bool _569;
            switch ((g_commandParams._m0[_180].cmdpmodcolr >> 3u) & 7u)
            {
                case 0u:
                {
                    uint _446 = _435 + (_437 >> 1u);
                    uint _461 = (((g_vram._m0[(_446 & 524284u) >> 2u] >> (((_446 & 3u) * 8u) & 31u)) & 255u) >> ((((~_419) & 1u) * 4u) & 31u)) & 15u;
                    _567 = _461 | (_190 & 65520u);
                    _568 = _461 == 0u;
                    _569 = _461 == 15u;
                    break;
                }
                case 1u:
                {
                    uint _467 = _435 + (_437 >> 1u);
                    uint _482 = (((g_vram._m0[(_467 & 524284u) >> 2u] >> (((_467 & 3u) * 8u) & 31u)) & 255u) >> ((((~_419) & 1u) * 4u) & 31u)) & 15u;
                    uint _487 = (_482 * 2u) + (_191 * 8u);
                    uint _496 = (g_vram._m0[(_487 & 524284u) >> 2u] >> (((_487 & 2u) * 8u) & 31u)) & 65535u;
                    _567 = ((_496 >> 8u) & 255u) | ((_496 << 8u) & 65280u);
                    _568 = _482 == 0u;
                    _569 = _482 == 15u;
                    break;
                }
                case 2u:
                {
                    uint _502 = _435 + _437;
                    uint _510 = g_vram._m0[(_502 & 524284u) >> 2u] >> (((_502 & 3u) * 8u) & 31u);
                    uint _511 = _510 & 255u;
                    _567 = (_510 & 63u) | (_190 & 65472u);
                    _568 = _511 == 0u;
                    _569 = _511 == 255u;
                    break;
                }
                case 3u:
                {
                    uint _517 = _435 + _437;
                    uint _525 = g_vram._m0[(_517 & 524284u) >> 2u] >> (((_517 & 3u) * 8u) & 31u);
                    uint _526 = _525 & 255u;
                    _567 = (_525 & 127u) | (_190 & 65408u);
                    _568 = _526 == 0u;
                    _569 = _526 == 255u;
                    break;
                }
                case 4u:
                {
                    uint _532 = _435 + _437;
                    uint _541 = (g_vram._m0[(_532 & 524284u) >> 2u] >> (((_532 & 3u) * 8u) & 31u)) & 255u;
                    _567 = _541 | (_190 & 65280u);
                    _568 = _541 == 0u;
                    _569 = _541 == 255u;
                    break;
                }
                case 5u:
                {
                    uint _548 = (_435 & 4294967280u) + (_437 * 2u);
                    uint _557 = (g_vram._m0[(_548 & 524284u) >> 2u] >> (((_548 & 2u) * 8u) & 31u)) & 65535u;
                    uint _562 = ((_557 >> 8u) & 255u) | ((_557 << 8u) & 65280u);
                    _567 = _562;
                    _568 = ((_562 >> 15u) & 1u) == 0u;
                    _569 = _562 == 32767u;
                    break;
                }
                default:
                {
                    _567 = _76;
                    _568 = _77;
                    _569 = _77;
                    break;
                }
            }
            bool _578;
            if (!(_569 ? _329 : false))
            {
                bool _577;
                if (_568)
                {
                    _577 = ((g_commandParams._m0[_180].cmdpmodcolr >> 6u) & 1u) == 0u;
                }
                else
                {
                    _577 = false;
                }
                _578 = _577;
            }
            else
            {
                _578 = true;
            }
            if (_578)
            {
                break;
            }
            _585 = _567;
        }
        else
        {
            uint _584;
            if (_106)
            {
                _584 = _190 & 255u;
            }
            else
            {
                _584 = _191;
            }
            _585 = _584;
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
                    int _629 = int(_618 - (_611 << 1u));
                    int _633;
                    if (_619)
                    {
                        _633 = _629 + 1;
                    }
                    else
                    {
                        _633 = _629;
                    }
                    _641 = _633;
                    _642 = _621;
                    _643 = _617 + 1;
                }
                else
                {
                    int _640;
                    if (_616 < 0)
                    {
                        _640 = 1 - _621;
                    }
                    else
                    {
                        _640 = -_621;
                    }
                    _641 = _640;
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
                    int _686 = int(_676 - (_611 << 1u));
                    int _690;
                    if (_677)
                    {
                        _690 = _686 + 1;
                    }
                    else
                    {
                        _690 = _686;
                    }
                    _698 = _690;
                    _699 = _621;
                    _700 = _675 + 1;
                }
                else
                {
                    int _697;
                    if (_674 < 0)
                    {
                        _697 = 1 - _621;
                    }
                    else
                    {
                        _697 = -_621;
                    }
                    _698 = _697;
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
                    int _743 = int(_733 - (_611 << 1u));
                    int _747;
                    if (_734)
                    {
                        _747 = _743 + 1;
                    }
                    else
                    {
                        _747 = _743;
                    }
                    _755 = _747;
                    _756 = _621;
                    _757 = _732 + 1;
                }
                else
                {
                    int _754;
                    if (_731 < 0)
                    {
                        _754 = 1 - _621;
                    }
                    else
                    {
                        _754 = -_621;
                    }
                    _755 = _754;
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
            bool _944;
            if (((!_116) ? _109 : false) ? _112 : false)
            {
                _944 = uint(_312.y & 1) != _114;
            }
            else
            {
                _944 = false;
            }
            if (_944)
            {
                break;
            }
            bool _947 = _116 ? _109 : false;
            bool _953;
            if (_947)
            {
                _953 = (_312.y & 1) != 0;
            }
            else
            {
                _953 = false;
            }
            uint _959;
            if (_953)
            {
                _959 = _103.x * _103.y;
            }
            else
            {
                _959 = 0u;
            }
            ivec2 _967;
            if ((!_947) ? _112 : true)
            {
                ivec2 _966 = _312;
                _966.y = _312.y >> 1;
                _967 = _966;
            }
            else
            {
                _967 = _312;
            }
            uint _970 = _103.x;
            uint _975 = _959 + ((uint(_967.y) * _970) + uint(_967.x));
            uint _982;
            if (((g_commandParams._m0[_180].cmdpmodcolr >> 8u) & 1u) != 0u)
            {
                _982 = _975 + ((_970 * _103.y) * 2u);
            }
            else
            {
                _982 = _975;
            }
            uint _984 = atomicMax(g_internalSpriteOut._m0[_982], _879);
            break;
        } while(false);
        bool _994;
        if (_186)
        {
            bool _993;
            if (_311 > 1u)
            {
                _993 = ((_310 - _227) + _225) > _282;
            }
            else
            {
                _993 = false;
            }
            _994 = _993;
        }
        else
        {
            _994 = false;
        }
        if (_994)
        {
            ivec2 _997 = _312 + _281;
            do
            {
                bool _1016;
                if (!any(lessThan(_997, ivec2(0))))
                {
                    _1016 = any(greaterThan(uvec2(_997), uvec2(g_commandParams._m0[_180].sysClip & 65535u, (g_commandParams._m0[_180].sysClip >> 16u) & 65535u)));
                }
                else
                {
                    _1016 = true;
                }
                if (_1016)
                {
                    break;
                }
                if (((g_commandParams._m0[_180].cmdpmodcolr >> 10u) & 1u) != 0u)
                {
                    uvec2 _1035 = uvec2(_997);
                    bool _1043;
                    if (!any(lessThan(_1035, uvec2(g_commandParams._m0[_180].userClip0 & 65535u, (g_commandParams._m0[_180].userClip0 >> 16u) & 65535u))))
                    {
                        _1043 = any(greaterThan(_1035, uvec2(g_commandParams._m0[_180].userClip1 & 65535u, (g_commandParams._m0[_180].userClip1 >> 16u) & 65535u)));
                    }
                    else
                    {
                        _1043 = true;
                    }
                    if (_1043 != (((g_commandParams._m0[_180].cmdpmodcolr >> 9u) & 1u) != 0u))
                    {
                        break;
                    }
                }
                bool _1059;
                if (((!_116) ? _109 : false) ? _112 : false)
                {
                    _1059 = uint(_997.y & 1) != _114;
                }
                else
                {
                    _1059 = false;
                }
                if (_1059)
                {
                    break;
                }
                bool _1062 = _116 ? _109 : false;
                bool _1068;
                if (_1062)
                {
                    _1068 = (_997.y & 1) != 0;
                }
                else
                {
                    _1068 = false;
                }
                uint _1074;
                if (_1068)
                {
                    _1074 = _103.x * _103.y;
                }
                else
                {
                    _1074 = 0u;
                }
                ivec2 _1082;
                if ((!_1062) ? _112 : true)
                {
                    ivec2 _1081 = _997;
                    _1081.y = _997.y >> 1;
                    _1082 = _1081;
                }
                else
                {
                    _1082 = _997;
                }
                uint _1085 = _103.x;
                uint _1090 = _1074 + ((uint(_1082.y) * _1085) + uint(_1082.x));
                uint _1097;
                if (((g_commandParams._m0[_180].cmdpmodcolr >> 8u) & 1u) != 0u)
                {
                    _1097 = _1090 + ((_1085 * _103.y) * 2u);
                }
                else
                {
                    _1097 = _1090;
                }
                uint _1099 = atomicMax(g_internalSpriteOut._m0[_1097], _879);
                break;
            } while(false);
        }
        break;
    } while(false);
}

