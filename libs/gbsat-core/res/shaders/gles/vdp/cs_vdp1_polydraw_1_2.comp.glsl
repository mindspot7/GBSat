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

struct OITFragment
{
    uint data;
    uint next;
};

uint _85;
bool _86;
ivec2 _94;

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
} g_listHeads;

layout(binding = 18, std430) buffer type_RWStructuredBuffer_OITFragment
{
    OITFragment _m0[];
} g_fragments;

layout(binding = 19, std430) buffer type_RWByteAddressBuffer
{
    uint _m0[];
} g_counter;

layout(binding = 2) uniform highp usamplerBuffer g_spanPrefixSums;

void main()
{
    uvec2 _112 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u));
    bool _115 = ((RenderParamsBuffer.g_commonParams.displayParams >> 2u) & 1u) != 0u;
    bool _118 = ((RenderParamsBuffer.g_commonParams.displayParams >> 3u) & 1u) != 0u;
    bool _121 = ((RenderParamsBuffer.g_commonParams.displayParams >> 4u) & 1u) != 0u;
    uint _123 = (RenderParamsBuffer.g_commonParams.displayParams >> 5u) & 1u;
    bool _125 = (RenderParamsBuffer.g_commonParams.enhancements & 1u) != 0u;
    do
    {
        uint _166;
        do
        {
            if (gl_GlobalInvocationID.x >= texelFetch(g_spanPrefixSums, int(RenderParamsBuffer.g_polyDrawParams.numSpans)).x)
            {
                _166 = 4294967295u;
                break;
            }
            uint _144;
            _144 = 0u;
            uint _142;
            uint _145;
            uint _162;
            bool _163;
            uint _141 = RenderParamsBuffer.g_polyDrawParams.numSpans;
            for (;;)
            {
                if (_144 != _141)
                {
                    uint _150 = (_144 + _141) >> 1u;
                    uvec4 _152 = texelFetch(g_spanPrefixSums, int(_150));
                    uint _153 = _152.x;
                    if (gl_GlobalInvocationID.x == _153)
                    {
                        _162 = _150;
                        _163 = true;
                        break;
                    }
                    bool _157 = gl_GlobalInvocationID.x > _153;
                    if (_157)
                    {
                        _145 = _150 + 1u;
                    }
                    else
                    {
                        _145 = _144;
                    }
                    _142 = _157 ? _141 : _150;
                    _141 = _142;
                    _144 = _145;
                    continue;
                }
                else
                {
                    _162 = _85;
                    _163 = false;
                    break;
                }
            }
            if (_163)
            {
                _166 = _162;
                break;
            }
            _166 = _144 - 1u;
            break;
        } while(false);
        if (_166 == 4294967295u)
        {
            break;
        }
        uint _175 = g_spanParams._m0[_166].skip_cmdIndex;
        uint _177 = g_spanParams._m0[_166].attrs;
        uint _187 = (gl_GlobalInvocationID.x - texelFetch(g_spanPrefixSums, int(_166)).x) + (_175 & 65535u);
        uint _189 = (_175 >> 16u) & 65535u;
        bool _195 = (_177 & 1u) != 0u;
        uint _199 = g_commandParams._m0[_189].cmdpmodcolr >> 16u;
        uint _200 = _199 & 65535u;
        ivec2 _201 = g_spanParams._m0[_166].coord1 - g_spanParams._m0[_166].coord0;
        ivec2 _202 = abs(_201);
        int _203 = _202.x;
        int _204 = _202.y;
        uint _206 = uint(max(_203, _204));
        bool _207 = _203 >= _204;
        ivec2 _229;
        ivec2 _230;
        ivec2 _231;
        ivec2 _232;
        if (_207)
        {
            _229 = ivec2(0, (_201.y >= 0) ? 1 : (-1));
            _230 = ivec2((_201.x >= 0) ? 1 : (-1), 0);
            _231 = _201;
            _232 = _202;
        }
        else
        {
            _229 = ivec2((_201.x >= 0) ? 1 : (-1), 0);
            _230 = ivec2(0, (_201.y >= 0) ? 1 : (-1));
            _231 = _201.yx;
            _232 = _202.yx;
        }
        int _234 = _232.y << 1;
        int _236 = _232.x << 1;
        bool _243;
        if (!_195)
        {
            _243 = _231.x < 0;
        }
        else
        {
            _243 = false;
        }
        int _245 = (_232.x + 1) + _234;
        ivec2 _246 = g_spanParams._m0[_166].coord0 - _230;
        ivec2 _290;
        int _291;
        int _292;
        if (_195)
        {
            int _250 = 0;
            int _251 = -1;
            bool _259 = (g_spanParams._m0[_166].coord0.x > g_spanParams._m0[_166].coord1.x) == (g_spanParams._m0[_166].coord0.y > g_spanParams._m0[_166].coord1.y);
            ivec2 _289;
            if (_207)
            {
                int _268;
                if (_259)
                {
                    _268 = 0;
                }
                else
                {
                    _268 = -_230.x;
                }
                int _274;
                if (_259)
                {
                    _274 = -_229.y;
                }
                else
                {
                    _274 = 0;
                }
                _289 = ivec2(_268, _274);
            }
            else
            {
                int _281;
                if (_259)
                {
                    _281 = 0;
                }
                else
                {
                    _281 = -_229.x;
                }
                int _287;
                if (_259)
                {
                    _287 = -_230.y;
                }
                else
                {
                    _287 = 0;
                }
                _289 = ivec2(_281, _287);
            }
            _290 = _289;
            _291 = _243 ? 0 : (-1);
            _292 = _245 - 1;
        }
        else
        {
            _290 = _94;
            _291 = int(_243);
            _292 = _245;
        }
        int _319;
        uint _320;
        ivec2 _321;
        do
        {
            uint _296 = min(_187, _206) + 1u;
            int _297 = int(_296);
            if (_297 == 0)
            {
                _319 = _292;
                _320 = 0u;
                _321 = _246;
                break;
            }
            ivec2 _303 = _246 + (_230 * ivec2(_297));
            int _305 = _292 - (_234 * _297);
            int _317;
            ivec2 _318;
            if (_236 != 0)
            {
                int _311 = ((_291 - _305) + _236) / _236;
                _317 = _305 + (_236 * _311);
                _318 = _303 + (_229 * ivec2(_311));
            }
            else
            {
                _317 = _305;
                _318 = _303;
            }
            _319 = _317;
            _320 = _296;
            _321 = _318;
            break;
        } while(false);
        uint _594;
        if (((_177 >> 1u) & 1u) != 0u)
        {
            uint _328 = max((((g_commandParams._m0[_189].cmdsizesrca >> 8u) & 63u) << 3u), 1u);
            bool _331 = ((_177 >> 2u) & 1u) != 0u;
            bool _338 = ((g_commandParams._m0[_189].cmdpmodcolr >> 7u) & 1u) == 0u;
            bool _346;
            if (((g_commandParams._m0[_189].cmdpmodcolr >> 12u) & 1u) != 0u)
            {
                _346 = _206 < (_328 - 1u);
            }
            else
            {
                _346 = false;
            }
            int _351 = int(_328 - 1u);
            int _352 = _331 ? 0 : _351;
            int _353 = _331 ? _351 : 0;
            uint _354 = _206 + 1u;
            int _360;
            int _361;
            if (_346)
            {
                _360 = _353 >> 1;
                _361 = _352 >> 1;
            }
            else
            {
                _360 = _353;
                _361 = _352;
            }
            int _362 = _361 - _360;
            int _363 = abs(_362);
            uint _364 = uint(_363);
            bool _365 = _362 >= 0;
            int _374;
            int _375;
            if (_346)
            {
                int _371 = 2;
                int _372 = -2;
                _374 = (_360 << 1) | int(((RenderParamsBuffer.g_commonParams.displayParams >> 6u) & 1u) != 0u);
                _375 = _365 ? 2 : (-2);
            }
            else
            {
                _374 = _360;
                _375 = _365 ? 1 : (-1);
            }
            int _376 = int(_354);
            int _398;
            int _399;
            int _400;
            if (_354 <= _364)
            {
                int _384 = int(_364 - (_354 << 1u));
                int _388;
                if (_365)
                {
                    _388 = _384 + 1;
                }
                else
                {
                    _388 = _384;
                }
                _398 = _388;
                _399 = _376;
                _400 = _363 + 1;
            }
            else
            {
                int _392 = int(_354 - (_354 << 1u));
                int _397;
                if (_362 < 0)
                {
                    _397 = _392 + 1;
                }
                else
                {
                    _397 = _392;
                }
                _398 = _397;
                _399 = _376 - 1;
                _400 = _363;
            }
            int _407 = int(uint(_398) + (uint(_400 << 1) * _187));
            int _415;
            if (_407 >= 0)
            {
                _415 = _374 + (_375 * ((_407 / (_399 << 1)) + 1));
            }
            else
            {
                _415 = _374;
            }
            bool _419;
            if (_338)
            {
                _419 = !_346;
            }
            else
            {
                _419 = false;
            }
            uint _426;
            bool _427;
            if (_419)
            {
                uint _424 = (_177 >> 13u) & 1023u;
                _426 = _424;
                _427 = _424 < _328;
            }
            else
            {
                _426 = _85;
                _427 = false;
            }
            uint _428 = uint(_415);
            bool _437;
            if (_427)
            {
                bool _436;
                if (_331)
                {
                    _436 = _428 <= _426;
                }
                else
                {
                    _436 = _428 >= _426;
                }
                _437 = _436;
            }
            else
            {
                _437 = false;
            }
            if (_437)
            {
                break;
            }
            uint _444 = ((g_commandParams._m0[_189].cmdsizesrca >> 16u) & 65535u) << 3u;
            uint _446 = _428 + (((_177 >> 3u) & 1023u) * _328);
            uint _576;
            bool _577;
            bool _578;
            switch ((g_commandParams._m0[_189].cmdpmodcolr >> 3u) & 7u)
            {
                case 0u:
                {
                    uint _455 = _444 + (_446 >> 1u);
                    uint _470 = (((g_vram._m0[(_455 & 524284u) >> 2u] >> (((_455 & 3u) * 8u) & 31u)) & 255u) >> ((((~_428) & 1u) * 4u) & 31u)) & 15u;
                    _576 = _470 | (_199 & 65520u);
                    _577 = _470 == 0u;
                    _578 = _470 == 15u;
                    break;
                }
                case 1u:
                {
                    uint _476 = _444 + (_446 >> 1u);
                    uint _491 = (((g_vram._m0[(_476 & 524284u) >> 2u] >> (((_476 & 3u) * 8u) & 31u)) & 255u) >> ((((~_428) & 1u) * 4u) & 31u)) & 15u;
                    uint _496 = (_491 * 2u) + (_200 * 8u);
                    uint _505 = (g_vram._m0[(_496 & 524284u) >> 2u] >> (((_496 & 2u) * 8u) & 31u)) & 65535u;
                    _576 = ((_505 >> 8u) & 255u) | ((_505 << 8u) & 65280u);
                    _577 = _491 == 0u;
                    _578 = _491 == 15u;
                    break;
                }
                case 2u:
                {
                    uint _511 = _444 + _446;
                    uint _519 = g_vram._m0[(_511 & 524284u) >> 2u] >> (((_511 & 3u) * 8u) & 31u);
                    uint _520 = _519 & 255u;
                    _576 = (_519 & 63u) | (_199 & 65472u);
                    _577 = _520 == 0u;
                    _578 = _520 == 255u;
                    break;
                }
                case 3u:
                {
                    uint _526 = _444 + _446;
                    uint _534 = g_vram._m0[(_526 & 524284u) >> 2u] >> (((_526 & 3u) * 8u) & 31u);
                    uint _535 = _534 & 255u;
                    _576 = (_534 & 127u) | (_199 & 65408u);
                    _577 = _535 == 0u;
                    _578 = _535 == 255u;
                    break;
                }
                case 4u:
                {
                    uint _541 = _444 + _446;
                    uint _550 = (g_vram._m0[(_541 & 524284u) >> 2u] >> (((_541 & 3u) * 8u) & 31u)) & 255u;
                    _576 = _550 | (_199 & 65280u);
                    _577 = _550 == 0u;
                    _578 = _550 == 255u;
                    break;
                }
                case 5u:
                {
                    uint _557 = (_444 & 4294967280u) + (_446 * 2u);
                    uint _566 = (g_vram._m0[(_557 & 524284u) >> 2u] >> (((_557 & 2u) * 8u) & 31u)) & 65535u;
                    uint _571 = ((_566 >> 8u) & 255u) | ((_566 << 8u) & 65280u);
                    _576 = _571;
                    _577 = ((_571 >> 15u) & 1u) == 0u;
                    _578 = _571 == 32767u;
                    break;
                }
                default:
                {
                    _576 = _85;
                    _577 = _86;
                    _578 = _86;
                    break;
                }
            }
            bool _587;
            if (!(_578 ? _338 : false))
            {
                bool _586;
                if (_577)
                {
                    _586 = ((g_commandParams._m0[_189].cmdpmodcolr >> 6u) & 1u) == 0u;
                }
                else
                {
                    _586 = false;
                }
                _587 = _586;
            }
            else
            {
                _587 = true;
            }
            if (_587)
            {
                break;
            }
            _594 = _576;
        }
        else
        {
            uint _593;
            if (_115)
            {
                _593 = _199 & 255u;
            }
            else
            {
                _593 = _200;
            }
            _594 = _593;
        }
        uint _595 = g_commandParams._m0[_189].cmdpmodcolr & 3u;
        bool _598 = ((g_commandParams._m0[_189].cmdpmodcolr >> 2u) & 1u) != 0u;
        bool _607;
        if (!_115)
        {
            bool _606;
            if (!_598)
            {
                _606 = _595 == 2u;
            }
            else
            {
                _606 = true;
            }
            _607 = _606;
        }
        else
        {
            _607 = false;
        }
        uint _885;
        if (_607)
        {
            uint _610 = _594 & 31u;
            uint _612 = (_594 >> 5u) & 31u;
            uint _614 = (_594 >> 10u) & 31u;
            uint _616 = (_594 >> 15u) & 1u;
            uvec4 _861;
            if (_598)
            {
                uint _620 = _206 + 1u;
                int _622 = int(g_spanParams._m0[_166].gouraud0.x);
                int _625 = int(g_spanParams._m0[_166].gouraud1.x) - _622;
                int _626 = abs(_625);
                uint _627 = uint(_626);
                bool _628 = _625 >= 0;
                int _629 = _628 ? 1 : (-1);
                int _630 = int(_620);
                int _650;
                int _651;
                int _652;
                if (_620 <= _627)
                {
                    int _638 = int(_627 - (_620 << 1u));
                    int _642;
                    if (_628)
                    {
                        _642 = _638 + 1;
                    }
                    else
                    {
                        _642 = _638;
                    }
                    _650 = _642;
                    _651 = _630;
                    _652 = _626 + 1;
                }
                else
                {
                    int _649;
                    if (_625 < 0)
                    {
                        _649 = 1 - _630;
                    }
                    else
                    {
                        _649 = -_630;
                    }
                    _650 = _649;
                    _651 = _630 - 1;
                    _652 = _626;
                }
                int _653 = _652 << 1;
                int _654 = _651 << 1;
                bool _655 = _654 != 0;
                int _674;
                int _675;
                int _676;
                int _677;
                if (_655)
                {
                    int _659;
                    int _662;
                    _659 = _622;
                    _662 = _650;
                    for (; _662 >= 0; )
                    {
                        _659 += _629;
                        _662 -= _654;
                        continue;
                    }
                    int _667;
                    int _670;
                    _667 = 0;
                    _670 = _653;
                    for (; _670 >= _654; )
                    {
                        _667 += _629;
                        _670 -= _654;
                        continue;
                    }
                    _674 = _670;
                    _675 = _659;
                    _676 = _667;
                    _677 = _662;
                }
                else
                {
                    _674 = _653;
                    _675 = _622;
                    _676 = 0;
                    _677 = _650;
                }
                int _680 = int(g_spanParams._m0[_166].gouraud0.y);
                int _683 = int(g_spanParams._m0[_166].gouraud1.y) - _680;
                int _684 = abs(_683);
                uint _685 = uint(_684);
                bool _686 = _683 >= 0;
                int _687 = _686 ? 1 : (-1);
                int _707;
                int _708;
                int _709;
                if (_620 <= _685)
                {
                    int _695 = int(_685 - (_620 << 1u));
                    int _699;
                    if (_686)
                    {
                        _699 = _695 + 1;
                    }
                    else
                    {
                        _699 = _695;
                    }
                    _707 = _699;
                    _708 = _630;
                    _709 = _684 + 1;
                }
                else
                {
                    int _706;
                    if (_683 < 0)
                    {
                        _706 = 1 - _630;
                    }
                    else
                    {
                        _706 = -_630;
                    }
                    _707 = _706;
                    _708 = _630 - 1;
                    _709 = _684;
                }
                int _710 = _709 << 1;
                int _711 = _708 << 1;
                bool _712 = _711 != 0;
                int _731;
                int _732;
                int _733;
                int _734;
                if (_712)
                {
                    int _716;
                    int _719;
                    _716 = _680;
                    _719 = _707;
                    for (; _719 >= 0; )
                    {
                        _716 += _687;
                        _719 -= _711;
                        continue;
                    }
                    int _724;
                    int _727;
                    _724 = 0;
                    _727 = _710;
                    for (; _727 >= _711; )
                    {
                        _724 += _687;
                        _727 -= _711;
                        continue;
                    }
                    _731 = _727;
                    _732 = _716;
                    _733 = _724;
                    _734 = _719;
                }
                else
                {
                    _731 = _710;
                    _732 = _680;
                    _733 = 0;
                    _734 = _707;
                }
                int _737 = int(g_spanParams._m0[_166].gouraud0.z);
                int _740 = int(g_spanParams._m0[_166].gouraud1.z) - _737;
                int _741 = abs(_740);
                uint _742 = uint(_741);
                bool _743 = _740 >= 0;
                int _744 = _743 ? 1 : (-1);
                int _764;
                int _765;
                int _766;
                if (_620 <= _742)
                {
                    int _752 = int(_742 - (_620 << 1u));
                    int _756;
                    if (_743)
                    {
                        _756 = _752 + 1;
                    }
                    else
                    {
                        _756 = _752;
                    }
                    _764 = _756;
                    _765 = _630;
                    _766 = _741 + 1;
                }
                else
                {
                    int _763;
                    if (_740 < 0)
                    {
                        _763 = 1 - _630;
                    }
                    else
                    {
                        _763 = -_630;
                    }
                    _764 = _763;
                    _765 = _630 - 1;
                    _766 = _741;
                }
                int _767 = _766 << 1;
                int _768 = _765 << 1;
                bool _769 = _768 != 0;
                int _788;
                int _789;
                int _790;
                int _791;
                if (_769)
                {
                    int _773;
                    int _776;
                    _773 = _737;
                    _776 = _764;
                    for (; _776 >= 0; )
                    {
                        _773 += _744;
                        _776 -= _768;
                        continue;
                    }
                    int _781;
                    int _784;
                    _781 = 0;
                    _784 = _767;
                    for (; _784 >= _768; )
                    {
                        _781 += _744;
                        _784 -= _768;
                        continue;
                    }
                    _788 = _784;
                    _789 = _773;
                    _790 = _781;
                    _791 = _776;
                }
                else
                {
                    _788 = _767;
                    _789 = _737;
                    _790 = 0;
                    _791 = _764;
                }
                int _793 = int(_187);
                int _842;
                int _843;
                int _844;
                if (_793 > 0)
                {
                    int _798 = _675 + (_676 * _793);
                    int _800 = (~_677) - (_674 * _793);
                    int _811;
                    if (_655)
                    {
                        int _804;
                        _804 = _798;
                        for (int _807 = _800; _807 < 0; )
                        {
                            _804 += _629;
                            _807 += _654;
                            continue;
                        }
                        _811 = _804;
                    }
                    else
                    {
                        _811 = _798;
                    }
                    int _813 = _732 + (_733 * _793);
                    int _815 = (~_734) - (_731 * _793);
                    int _826;
                    if (_712)
                    {
                        int _819;
                        _819 = _813;
                        for (int _822 = _815; _822 < 0; )
                        {
                            _819 += _687;
                            _822 += _711;
                            continue;
                        }
                        _826 = _819;
                    }
                    else
                    {
                        _826 = _813;
                    }
                    int _828 = _789 + (_790 * _793);
                    int _830 = (~_791) - (_788 * _793);
                    int _841;
                    if (_769)
                    {
                        int _834;
                        _834 = _828;
                        for (int _837 = _830; _837 < 0; )
                        {
                            _834 += _744;
                            _837 += _768;
                            continue;
                        }
                        _841 = _834;
                    }
                    else
                    {
                        _841 = _828;
                    }
                    _842 = _841;
                    _843 = _826;
                    _844 = _811;
                }
                else
                {
                    _842 = _789;
                    _843 = _732;
                    _844 = _675;
                }
                _861 = uvec4(uint(clamp((_844 + int(_610)) - 16, 0, 31)), uint(clamp((_843 + int(_612)) - 16, 0, 31)), uint(clamp((_842 + int(_614)) - 16, 0, 31)), _616);
            }
            else
            {
                _861 = uvec4(_610, _612, _614, _616);
            }
            uvec4 _874;
            if (_595 == 2u)
            {
                uvec4 _867 = _861;
                _867.x = _861.x >> 1u;
                _867.y = _861.y >> 1u;
                _867.z = _861.z >> 1u;
                _874 = _867;
            }
            else
            {
                _874 = _861;
            }
            _885 = ((_874.x | (_874.y << 5u)) | (_874.z << 10u)) | (_874.w << 15u);
        }
        else
        {
            _885 = _594;
        }
        uint _888 = _885 | ((_166 + 1u) << 16u);
        do
        {
            bool _910;
            if (!any(lessThan(_321, ivec2(0))))
            {
                _910 = any(greaterThan(uvec2(_321), uvec2(g_commandParams._m0[_189].sysClip & 65535u, (g_commandParams._m0[_189].sysClip >> 16u) & 65535u)));
            }
            else
            {
                _910 = true;
            }
            if (_910)
            {
                break;
            }
            if (((g_commandParams._m0[_189].cmdpmodcolr >> 10u) & 1u) != 0u)
            {
                uvec2 _929 = uvec2(_321);
                bool _937;
                if (!any(lessThan(_929, uvec2(g_commandParams._m0[_189].userClip0 & 65535u, (g_commandParams._m0[_189].userClip0 >> 16u) & 65535u))))
                {
                    _937 = any(greaterThan(_929, uvec2(g_commandParams._m0[_189].userClip1 & 65535u, (g_commandParams._m0[_189].userClip1 >> 16u) & 65535u)));
                }
                else
                {
                    _937 = true;
                }
                if (_937 != (((g_commandParams._m0[_189].cmdpmodcolr >> 9u) & 1u) != 0u))
                {
                    break;
                }
            }
            bool _953;
            if (((!_125) ? _118 : false) ? _121 : false)
            {
                _953 = uint(_321.y & 1) != _123;
            }
            else
            {
                _953 = false;
            }
            if (_953)
            {
                break;
            }
            bool _956 = _125 ? _118 : false;
            bool _962;
            if (_956)
            {
                _962 = (_321.y & 1) != 0;
            }
            else
            {
                _962 = false;
            }
            uint _968;
            if (_962)
            {
                _968 = _112.x * _112.y;
            }
            else
            {
                _968 = 0u;
            }
            ivec2 _976;
            if ((!_956) ? _121 : true)
            {
                ivec2 _975 = _321;
                _975.y = _321.y >> 1;
                _976 = _975;
            }
            else
            {
                _976 = _321;
            }
            uint _979 = _112.x;
            uint _984 = _968 + ((uint(_976.y) * _979) + uint(_976.x));
            uint _991;
            if (((g_commandParams._m0[_189].cmdpmodcolr >> 8u) & 1u) != 0u)
            {
                _991 = _984 + ((_979 * _112.y) * 2u);
            }
            else
            {
                _991 = _984;
            }
            uint _993 = atomicAdd(g_counter._m0[0u], 1u);
            uint _995 = atomicExchange(g_listHeads._m0[_991], _993);
            g_fragments._m0[_993] = OITFragment(_888, _995);
            break;
        } while(false);
        bool _1007;
        if (_195)
        {
            bool _1006;
            if (_320 > 1u)
            {
                _1006 = ((_319 - _236) + _234) > _291;
            }
            else
            {
                _1006 = false;
            }
            _1007 = _1006;
        }
        else
        {
            _1007 = false;
        }
        if (_1007)
        {
            ivec2 _1010 = _321 + _290;
            do
            {
                bool _1029;
                if (!any(lessThan(_1010, ivec2(0))))
                {
                    _1029 = any(greaterThan(uvec2(_1010), uvec2(g_commandParams._m0[_189].sysClip & 65535u, (g_commandParams._m0[_189].sysClip >> 16u) & 65535u)));
                }
                else
                {
                    _1029 = true;
                }
                if (_1029)
                {
                    break;
                }
                if (((g_commandParams._m0[_189].cmdpmodcolr >> 10u) & 1u) != 0u)
                {
                    uvec2 _1048 = uvec2(_1010);
                    bool _1056;
                    if (!any(lessThan(_1048, uvec2(g_commandParams._m0[_189].userClip0 & 65535u, (g_commandParams._m0[_189].userClip0 >> 16u) & 65535u))))
                    {
                        _1056 = any(greaterThan(_1048, uvec2(g_commandParams._m0[_189].userClip1 & 65535u, (g_commandParams._m0[_189].userClip1 >> 16u) & 65535u)));
                    }
                    else
                    {
                        _1056 = true;
                    }
                    if (_1056 != (((g_commandParams._m0[_189].cmdpmodcolr >> 9u) & 1u) != 0u))
                    {
                        break;
                    }
                }
                bool _1072;
                if (((!_125) ? _118 : false) ? _121 : false)
                {
                    _1072 = uint(_1010.y & 1) != _123;
                }
                else
                {
                    _1072 = false;
                }
                if (_1072)
                {
                    break;
                }
                bool _1075 = _125 ? _118 : false;
                bool _1081;
                if (_1075)
                {
                    _1081 = (_1010.y & 1) != 0;
                }
                else
                {
                    _1081 = false;
                }
                uint _1087;
                if (_1081)
                {
                    _1087 = _112.x * _112.y;
                }
                else
                {
                    _1087 = 0u;
                }
                ivec2 _1095;
                if ((!_1075) ? _121 : true)
                {
                    ivec2 _1094 = _1010;
                    _1094.y = _1010.y >> 1;
                    _1095 = _1094;
                }
                else
                {
                    _1095 = _1010;
                }
                uint _1098 = _112.x;
                uint _1103 = _1087 + ((uint(_1095.y) * _1098) + uint(_1095.x));
                uint _1110;
                if (((g_commandParams._m0[_189].cmdpmodcolr >> 8u) & 1u) != 0u)
                {
                    _1110 = _1103 + ((_1098 * _112.y) * 2u);
                }
                else
                {
                    _1110 = _1103;
                }
                uint _1112 = atomicAdd(g_counter._m0[0u], 1u);
                uint _1114 = atomicExchange(g_listHeads._m0[_1110], _1112);
                g_fragments._m0[_1112] = OITFragment(_888, _1114);
                break;
            } while(false);
        }
        break;
    } while(false);
}

