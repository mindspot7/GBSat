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
    uint _110 = max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uvec2 _112 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(_110);
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
                int _281;
                if (_259)
                {
                    _281 = 0;
                }
                else
                {
                    _281 = -_230.x;
                }
                int _287;
                if (_259)
                {
                    _287 = -_229.y;
                }
                else
                {
                    _287 = 0;
                }
                _289 = ivec2(_281, _287);
            }
            else
            {
                int _268;
                if (_259)
                {
                    _268 = 0;
                }
                else
                {
                    _268 = -_229.x;
                }
                int _274;
                if (_259)
                {
                    _274 = -_230.y;
                }
                else
                {
                    _274 = 0;
                }
                _289 = ivec2(_268, _274);
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
            uint _332 = max((((g_commandParams._m0[_189].cmdsizesrca >> 8u) & 63u) << 3u), 1u);
            bool _335 = ((_177 >> 2u) & 1u) != 0u;
            bool _342 = ((g_commandParams._m0[_189].cmdpmodcolr >> 7u) & 1u) == 0u;
            bool _350;
            if (((g_commandParams._m0[_189].cmdpmodcolr >> 12u) & 1u) != 0u)
            {
                _350 = _206 < (_332 - 1u);
            }
            else
            {
                _350 = false;
            }
            int _355 = int(_332 - 1u);
            int _356 = _335 ? 0 : _355;
            int _357 = _335 ? _355 : 0;
            uint _358 = _206 + 1u;
            int _364;
            int _365;
            if (_350)
            {
                _364 = _357 >> 1;
                _365 = _356 >> 1;
            }
            else
            {
                _364 = _357;
                _365 = _356;
            }
            int _366 = _365 - _364;
            int _367 = abs(_366);
            uint _368 = uint(_367);
            bool _369 = _366 >= 0;
            int _378;
            int _379;
            if (_350)
            {
                int _375 = 2;
                int _376 = -2;
                _378 = (_364 << 1) | int(((RenderParamsBuffer.g_commonParams.displayParams >> 6u) & 1u) != 0u);
                _379 = _369 ? 2 : (-2);
            }
            else
            {
                _378 = _364;
                _379 = _369 ? 1 : (-1);
            }
            int _380 = int(_358);
            int _402;
            int _403;
            int _404;
            if (_358 <= _368)
            {
                int _397 = int(_368 - (_358 << 1u));
                int _401;
                if (_369)
                {
                    _401 = _397 + 1;
                }
                else
                {
                    _401 = _397;
                }
                _402 = _401;
                _403 = _380;
                _404 = _367 + 1;
            }
            else
            {
                int _388 = int(_358 - (_358 << 1u));
                int _393;
                if (_366 < 0)
                {
                    _393 = _388 + 1;
                }
                else
                {
                    _393 = _388;
                }
                _402 = _393;
                _403 = _380 - 1;
                _404 = _367;
            }
            int _411 = int(uint(_402) + (uint(_404 << 1) * _187));
            int _419;
            if (_411 >= 0)
            {
                _419 = _378 + (_379 * ((_411 / (_403 << 1)) + 1));
            }
            else
            {
                _419 = _378;
            }
            bool _423;
            if (_342)
            {
                _423 = !_350;
            }
            else
            {
                _423 = false;
            }
            uint _430;
            bool _431;
            if (_423)
            {
                uint _428 = (_177 >> 13u) & 1023u;
                _430 = _428;
                _431 = _428 < _332;
            }
            else
            {
                _430 = _85;
                _431 = false;
            }
            uint _432 = uint(_419);
            bool _441;
            if (_431)
            {
                bool _440;
                if (_335)
                {
                    _440 = _432 <= _430;
                }
                else
                {
                    _440 = _432 >= _430;
                }
                _441 = _440;
            }
            else
            {
                _441 = false;
            }
            if (_441)
            {
                break;
            }
            uint _448 = ((g_commandParams._m0[_189].cmdsizesrca >> 16u) & 65535u) << 3u;
            uint _450 = _432 + (((_177 >> 3u) & 1023u) * _332);
            uint _580;
            bool _581;
            bool _582;
            switch ((g_commandParams._m0[_189].cmdpmodcolr >> 3u) & 7u)
            {
                case 0u:
                {
                    uint _560 = _448 + (_450 >> 1u);
                    uint _575 = (((g_vram._m0[(_560 & 524284u) >> 2u] >> (((_560 & 3u) * 8u) & 31u)) & 255u) >> ((((~_432) & 1u) * 4u) & 31u)) & 15u;
                    _580 = _575 | (_199 & 65520u);
                    _581 = _575 == 0u;
                    _582 = _575 == 15u;
                    break;
                }
                case 1u:
                {
                    uint _524 = _448 + (_450 >> 1u);
                    uint _539 = (((g_vram._m0[(_524 & 524284u) >> 2u] >> (((_524 & 3u) * 8u) & 31u)) & 255u) >> ((((~_432) & 1u) * 4u) & 31u)) & 15u;
                    uint _544 = (_539 * 2u) + (_200 * 8u);
                    uint _553 = (g_vram._m0[(_544 & 524284u) >> 2u] >> (((_544 & 2u) * 8u) & 31u)) & 65535u;
                    _580 = ((_553 >> 8u) & 255u) | ((_553 << 8u) & 65280u);
                    _581 = _539 == 0u;
                    _582 = _539 == 15u;
                    break;
                }
                case 2u:
                {
                    uint _508 = _448 + _450;
                    uint _516 = g_vram._m0[(_508 & 524284u) >> 2u] >> (((_508 & 3u) * 8u) & 31u);
                    uint _517 = _516 & 255u;
                    _580 = (_516 & 63u) | (_199 & 65472u);
                    _581 = _517 == 0u;
                    _582 = _517 == 255u;
                    break;
                }
                case 3u:
                {
                    uint _493 = _448 + _450;
                    uint _501 = g_vram._m0[(_493 & 524284u) >> 2u] >> (((_493 & 3u) * 8u) & 31u);
                    uint _502 = _501 & 255u;
                    _580 = (_501 & 127u) | (_199 & 65408u);
                    _581 = _502 == 0u;
                    _582 = _502 == 255u;
                    break;
                }
                case 4u:
                {
                    uint _479 = _448 + _450;
                    uint _488 = (g_vram._m0[(_479 & 524284u) >> 2u] >> (((_479 & 3u) * 8u) & 31u)) & 255u;
                    _580 = _488 | (_199 & 65280u);
                    _581 = _488 == 0u;
                    _582 = _488 == 255u;
                    break;
                }
                case 5u:
                {
                    uint _460 = (_448 & 4294967280u) + (_450 * 2u);
                    uint _469 = (g_vram._m0[(_460 & 524284u) >> 2u] >> (((_460 & 2u) * 8u) & 31u)) & 65535u;
                    uint _474 = ((_469 >> 8u) & 255u) | ((_469 << 8u) & 65280u);
                    _580 = _474;
                    _581 = ((_474 >> 15u) & 1u) == 0u;
                    _582 = _474 == 32767u;
                    break;
                }
                default:
                {
                    _580 = _85;
                    _581 = _86;
                    _582 = _86;
                    break;
                }
            }
            bool _591;
            if (!(_582 ? _342 : false))
            {
                bool _590;
                if (_581)
                {
                    _590 = ((g_commandParams._m0[_189].cmdpmodcolr >> 6u) & 1u) == 0u;
                }
                else
                {
                    _590 = false;
                }
                _591 = _590;
            }
            else
            {
                _591 = true;
            }
            if (_591)
            {
                break;
            }
            _594 = _580;
        }
        else
        {
            uint _328;
            if (_115)
            {
                _328 = _199 & 255u;
            }
            else
            {
                _328 = _200;
            }
            _594 = _328;
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
                    int _645 = int(_627 - (_620 << 1u));
                    int _649;
                    if (_628)
                    {
                        _649 = _645 + 1;
                    }
                    else
                    {
                        _649 = _645;
                    }
                    _650 = _649;
                    _651 = _630;
                    _652 = _626 + 1;
                }
                else
                {
                    int _641;
                    if (_625 < 0)
                    {
                        _641 = 1 - _630;
                    }
                    else
                    {
                        _641 = -_630;
                    }
                    _650 = _641;
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
                    int _702 = int(_685 - (_620 << 1u));
                    int _706;
                    if (_686)
                    {
                        _706 = _702 + 1;
                    }
                    else
                    {
                        _706 = _702;
                    }
                    _707 = _706;
                    _708 = _630;
                    _709 = _684 + 1;
                }
                else
                {
                    int _698;
                    if (_683 < 0)
                    {
                        _698 = 1 - _630;
                    }
                    else
                    {
                        _698 = -_630;
                    }
                    _707 = _698;
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
                    int _759 = int(_742 - (_620 << 1u));
                    int _763;
                    if (_743)
                    {
                        _763 = _759 + 1;
                    }
                    else
                    {
                        _763 = _759;
                    }
                    _764 = _763;
                    _765 = _630;
                    _766 = _741 + 1;
                }
                else
                {
                    int _755;
                    if (_740 < 0)
                    {
                        _755 = 1 - _630;
                    }
                    else
                    {
                        _755 = -_630;
                    }
                    _764 = _755;
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
            bool _955;
            if (((g_commandParams._m0[_189].cmdpmodcolr >> 8u) & 1u) != 0u)
            {
                _955 = (((uint(_321.x) / _110) ^ (uint(_321.y) / _110)) & 1u) != 0u;
            }
            else
            {
                _955 = false;
            }
            if (_955)
            {
                break;
            }
            bool _967;
            if (((!_125) ? _118 : false) ? _121 : false)
            {
                _967 = uint(_321.y & 1) != _123;
            }
            else
            {
                _967 = false;
            }
            if (_967)
            {
                break;
            }
            bool _970 = _125 ? _118 : false;
            bool _976;
            if (_970)
            {
                _976 = (_321.y & 1) != 0;
            }
            else
            {
                _976 = false;
            }
            uint _982;
            if (_976)
            {
                _982 = _112.x * _112.y;
            }
            else
            {
                _982 = 0u;
            }
            ivec2 _990;
            if ((!_970) ? _121 : true)
            {
                ivec2 _989 = _321;
                _989.y = _321.y >> 1;
                _990 = _989;
            }
            else
            {
                _990 = _321;
            }
            uint _1000 = atomicAdd(g_counter._m0[0u], 1u);
            uint _1002 = atomicExchange(g_listHeads._m0[_982 + ((uint(_990.y) * _112.x) + uint(_990.x))], _1000);
            g_fragments._m0[_1000] = OITFragment(_888, _1002);
            break;
        } while(false);
        bool _1014;
        if (_195)
        {
            bool _1013;
            if (_320 > 1u)
            {
                _1013 = ((_319 - _236) + _234) > _291;
            }
            else
            {
                _1013 = false;
            }
            _1014 = _1013;
        }
        else
        {
            _1014 = false;
        }
        if (_1014)
        {
            ivec2 _1017 = _321 + _290;
            do
            {
                bool _1036;
                if (!any(lessThan(_1017, ivec2(0))))
                {
                    _1036 = any(greaterThan(uvec2(_1017), uvec2(g_commandParams._m0[_189].sysClip & 65535u, (g_commandParams._m0[_189].sysClip >> 16u) & 65535u)));
                }
                else
                {
                    _1036 = true;
                }
                if (_1036)
                {
                    break;
                }
                if (((g_commandParams._m0[_189].cmdpmodcolr >> 10u) & 1u) != 0u)
                {
                    uvec2 _1055 = uvec2(_1017);
                    bool _1063;
                    if (!any(lessThan(_1055, uvec2(g_commandParams._m0[_189].userClip0 & 65535u, (g_commandParams._m0[_189].userClip0 >> 16u) & 65535u))))
                    {
                        _1063 = any(greaterThan(_1055, uvec2(g_commandParams._m0[_189].userClip1 & 65535u, (g_commandParams._m0[_189].userClip1 >> 16u) & 65535u)));
                    }
                    else
                    {
                        _1063 = true;
                    }
                    if (_1063 != (((g_commandParams._m0[_189].cmdpmodcolr >> 9u) & 1u) != 0u))
                    {
                        break;
                    }
                }
                bool _1081;
                if (((g_commandParams._m0[_189].cmdpmodcolr >> 8u) & 1u) != 0u)
                {
                    _1081 = (((uint(_1017.x) / _110) ^ (uint(_1017.y) / _110)) & 1u) != 0u;
                }
                else
                {
                    _1081 = false;
                }
                if (_1081)
                {
                    break;
                }
                bool _1093;
                if (((!_125) ? _118 : false) ? _121 : false)
                {
                    _1093 = uint(_1017.y & 1) != _123;
                }
                else
                {
                    _1093 = false;
                }
                if (_1093)
                {
                    break;
                }
                bool _1096 = _125 ? _118 : false;
                bool _1102;
                if (_1096)
                {
                    _1102 = (_1017.y & 1) != 0;
                }
                else
                {
                    _1102 = false;
                }
                uint _1108;
                if (_1102)
                {
                    _1108 = _112.x * _112.y;
                }
                else
                {
                    _1108 = 0u;
                }
                ivec2 _1116;
                if ((!_1096) ? _121 : true)
                {
                    ivec2 _1115 = _1017;
                    _1115.y = _1017.y >> 1;
                    _1116 = _1115;
                }
                else
                {
                    _1116 = _1017;
                }
                uint _1126 = atomicAdd(g_counter._m0[0u], 1u);
                uint _1128 = atomicExchange(g_listHeads._m0[_1108 + ((uint(_1116.y) * _112.x) + uint(_1116.x))], _1126);
                g_fragments._m0[_1126] = OITFragment(_888, _1128);
                break;
            } while(false);
        }
        break;
    } while(false);
}

