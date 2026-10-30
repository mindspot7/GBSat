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

uint _70;
bool _71;
ivec2 _82;
uint _83;

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

layout(binding = 17, std430) buffer type_RWByteAddressBuffer
{
    uint _m0[];
} g_fbramOut;

layout(binding = 2) uniform highp usamplerBuffer g_spanPrefixSums;

void main()
{
    uint _96 = max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uvec2 _98 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), _83) * uvec2(_96);
    bool _101 = ((RenderParamsBuffer.g_commonParams.displayParams >> 2u) & 1u) != 0u;
    bool _104 = ((RenderParamsBuffer.g_commonParams.displayParams >> 3u) & 1u) != 0u;
    bool _107 = ((RenderParamsBuffer.g_commonParams.displayParams >> 4u) & 1u) != 0u;
    uint _109 = (RenderParamsBuffer.g_commonParams.displayParams >> 5u) & 1u;
    bool _111 = (RenderParamsBuffer.g_commonParams.enhancements & 1u) != 0u;
    do
    {
        uint _152;
        do
        {
            if (gl_GlobalInvocationID.x >= texelFetch(g_spanPrefixSums, int(RenderParamsBuffer.g_polyDrawParams.numSpans)).x)
            {
                _152 = 4294967295u;
                break;
            }
            uint _130;
            _130 = 0u;
            uint _128;
            uint _131;
            uint _148;
            bool _149;
            uint _127 = RenderParamsBuffer.g_polyDrawParams.numSpans;
            for (;;)
            {
                if (_130 != _127)
                {
                    uint _136 = (_130 + _127) >> 1u;
                    uvec4 _138 = texelFetch(g_spanPrefixSums, int(_136));
                    uint _139 = _138.x;
                    if (gl_GlobalInvocationID.x == _139)
                    {
                        _148 = _136;
                        _149 = true;
                        break;
                    }
                    bool _143 = gl_GlobalInvocationID.x > _139;
                    if (_143)
                    {
                        _131 = _136 + 1u;
                    }
                    else
                    {
                        _131 = _130;
                    }
                    _128 = _143 ? _127 : _136;
                    _127 = _128;
                    _130 = _131;
                    continue;
                }
                else
                {
                    _148 = _70;
                    _149 = false;
                    break;
                }
            }
            if (_149)
            {
                _152 = _148;
                break;
            }
            _152 = _130 - 1u;
            break;
        } while(false);
        if (_152 == 4294967295u)
        {
            break;
        }
        uint _161 = g_spanParams._m0[_152].skip_cmdIndex;
        uint _163 = g_spanParams._m0[_152].attrs;
        uint _169 = (gl_GlobalInvocationID.x - texelFetch(g_spanPrefixSums, int(_152)).x) + (_161 & 65535u);
        uint _171 = (_161 >> 16u) & 65535u;
        bool _177 = (_163 & 1u) != 0u;
        ivec2 _181 = g_spanParams._m0[_152].coord1 - g_spanParams._m0[_152].coord0;
        ivec2 _182 = abs(_181);
        int _183 = _182.x;
        int _184 = _182.y;
        uint _186 = uint(max(_183, _184));
        bool _187 = _183 >= _184;
        ivec2 _209;
        ivec2 _210;
        ivec2 _211;
        ivec2 _212;
        if (_187)
        {
            _209 = ivec2(0, (_181.y >= 0) ? 1 : (-1));
            _210 = ivec2((_181.x >= 0) ? 1 : (-1), 0);
            _211 = _181;
            _212 = _182;
        }
        else
        {
            _209 = ivec2((_181.x >= 0) ? 1 : (-1), 0);
            _210 = ivec2(0, (_181.y >= 0) ? 1 : (-1));
            _211 = _181.yx;
            _212 = _182.yx;
        }
        int _214 = _212.y << 1;
        int _216 = _212.x << 1;
        bool _223;
        if (!_177)
        {
            _223 = _211.x < 0;
        }
        else
        {
            _223 = false;
        }
        int _225 = (_212.x + 1) + _214;
        ivec2 _226 = g_spanParams._m0[_152].coord0 - _210;
        ivec2 _270;
        int _271;
        int _272;
        if (_177)
        {
            int _230 = 0;
            int _231 = -1;
            bool _239 = (g_spanParams._m0[_152].coord0.x > g_spanParams._m0[_152].coord1.x) == (g_spanParams._m0[_152].coord0.y > g_spanParams._m0[_152].coord1.y);
            ivec2 _269;
            if (_187)
            {
                int _261;
                if (_239)
                {
                    _261 = 0;
                }
                else
                {
                    _261 = -_210.x;
                }
                int _267;
                if (_239)
                {
                    _267 = -_209.y;
                }
                else
                {
                    _267 = 0;
                }
                _269 = ivec2(_261, _267);
            }
            else
            {
                int _248;
                if (_239)
                {
                    _248 = 0;
                }
                else
                {
                    _248 = -_209.x;
                }
                int _254;
                if (_239)
                {
                    _254 = -_210.y;
                }
                else
                {
                    _254 = 0;
                }
                _269 = ivec2(_248, _254);
            }
            _270 = _269;
            _271 = _223 ? 0 : (-1);
            _272 = _225 - 1;
        }
        else
        {
            _270 = _82;
            _271 = int(_223);
            _272 = _225;
        }
        int _299;
        uint _300;
        ivec2 _301;
        do
        {
            uint _276 = min(_169, _186) + 1u;
            int _277 = int(_276);
            if (_277 == 0)
            {
                _299 = _272;
                _300 = 0u;
                _301 = _226;
                break;
            }
            ivec2 _283 = _226 + (_210 * ivec2(_277));
            int _285 = _272 - (_214 * _277);
            int _297;
            ivec2 _298;
            if (_216 != 0)
            {
                int _291 = ((_271 - _285) + _216) / _216;
                _297 = _285 + (_216 * _291);
                _298 = _283 + (_209 * ivec2(_291));
            }
            else
            {
                _297 = _285;
                _298 = _283;
            }
            _299 = _297;
            _300 = _276;
            _301 = _298;
            break;
        } while(false);
        if (((_163 >> 1u) & 1u) != 0u)
        {
            uint _308 = max((((g_commandParams._m0[_171].cmdsizesrca >> 8u) & 63u) << 3u), 1u);
            bool _311 = ((_163 >> 2u) & 1u) != 0u;
            bool _318 = ((g_commandParams._m0[_171].cmdpmodcolr >> 7u) & 1u) == 0u;
            bool _326;
            if (((g_commandParams._m0[_171].cmdpmodcolr >> 12u) & 1u) != 0u)
            {
                _326 = _186 < (_308 - 1u);
            }
            else
            {
                _326 = false;
            }
            int _331 = int(_308 - 1u);
            int _332 = _311 ? 0 : _331;
            int _333 = _311 ? _331 : 0;
            uint _334 = _186 + 1u;
            int _340;
            int _341;
            if (_326)
            {
                _340 = _333 >> 1;
                _341 = _332 >> 1;
            }
            else
            {
                _340 = _333;
                _341 = _332;
            }
            int _342 = _341 - _340;
            int _343 = abs(_342);
            uint _344 = uint(_343);
            bool _345 = _342 >= 0;
            int _354;
            int _355;
            if (_326)
            {
                int _351 = 2;
                int _352 = -2;
                _354 = (_340 << 1) | int(((RenderParamsBuffer.g_commonParams.displayParams >> 6u) & 1u) != 0u);
                _355 = _345 ? 2 : (-2);
            }
            else
            {
                _354 = _340;
                _355 = _345 ? 1 : (-1);
            }
            int _356 = int(_334);
            int _378;
            int _379;
            int _380;
            if (_334 <= _344)
            {
                int _373 = int(_344 - (_334 << 1u));
                int _377;
                if (_345)
                {
                    _377 = _373 + 1;
                }
                else
                {
                    _377 = _373;
                }
                _378 = _377;
                _379 = _356;
                _380 = _343 + 1;
            }
            else
            {
                int _364 = int(_334 - (_334 << 1u));
                int _369;
                if (_342 < 0)
                {
                    _369 = _364 + 1;
                }
                else
                {
                    _369 = _364;
                }
                _378 = _369;
                _379 = _356 - 1;
                _380 = _343;
            }
            int _387 = int(uint(_378) + (uint(_380 << 1) * _169));
            int _395;
            if (_387 >= 0)
            {
                _395 = _354 + (_355 * ((_387 / (_379 << 1)) + 1));
            }
            else
            {
                _395 = _354;
            }
            bool _399;
            if (_318)
            {
                _399 = !_326;
            }
            else
            {
                _399 = false;
            }
            uint _406;
            bool _407;
            if (_399)
            {
                uint _404 = (_163 >> 13u) & 1023u;
                _406 = _404;
                _407 = _404 < _308;
            }
            else
            {
                _406 = _70;
                _407 = false;
            }
            uint _408 = uint(_395);
            bool _417;
            if (_407)
            {
                bool _416;
                if (_311)
                {
                    _416 = _408 <= _406;
                }
                else
                {
                    _416 = _408 >= _406;
                }
                _417 = _416;
            }
            else
            {
                _417 = false;
            }
            if (_417)
            {
                break;
            }
            uint _424 = ((g_commandParams._m0[_171].cmdsizesrca >> 16u) & 65535u) << 3u;
            uint _426 = _408 + (((_163 >> 3u) & 1023u) * _308);
            bool _529;
            bool _530;
            switch ((g_commandParams._m0[_171].cmdpmodcolr >> 3u) & 7u)
            {
                case 0u:
                {
                    uint _511 = _424 + (_426 >> 1u);
                    uint _526 = (((g_vram._m0[(_511 & 524284u) >> 2u] >> (((_511 & 3u) * 8u) & 31u)) & 255u) >> ((((~_408) & 1u) * 4u) & 31u)) & 15u;
                    _529 = _526 == 0u;
                    _530 = _526 == 15u;
                    break;
                }
                case 1u:
                {
                    uint _492 = _424 + (_426 >> 1u);
                    uint _507 = (((g_vram._m0[(_492 & 524284u) >> 2u] >> (((_492 & 3u) * 8u) & 31u)) & 255u) >> ((((~_408) & 1u) * 4u) & 31u)) & 15u;
                    _529 = _507 == 0u;
                    _530 = _507 == 15u;
                    break;
                }
                case 2u:
                {
                    uint _479 = _424 + _426;
                    uint _488 = (g_vram._m0[(_479 & 524284u) >> 2u] >> (((_479 & 3u) * 8u) & 31u)) & 255u;
                    _529 = _488 == 0u;
                    _530 = _488 == 255u;
                    break;
                }
                case 3u:
                {
                    uint _467 = _424 + _426;
                    uint _476 = (g_vram._m0[(_467 & 524284u) >> 2u] >> (((_467 & 3u) * 8u) & 31u)) & 255u;
                    _529 = _476 == 0u;
                    _530 = _476 == 255u;
                    break;
                }
                case 4u:
                {
                    uint _455 = _424 + _426;
                    uint _464 = (g_vram._m0[(_455 & 524284u) >> 2u] >> (((_455 & 3u) * 8u) & 31u)) & 255u;
                    _529 = _464 == 0u;
                    _530 = _464 == 255u;
                    break;
                }
                case 5u:
                {
                    uint _436 = (_424 & 4294967280u) + (_426 * 2u);
                    uint _445 = (g_vram._m0[(_436 & 524284u) >> 2u] >> (((_436 & 2u) * 8u) & 31u)) & 65535u;
                    uint _450 = ((_445 >> 8u) & 255u) | ((_445 << 8u) & 65280u);
                    _529 = ((_450 >> 15u) & 1u) == 0u;
                    _530 = _450 == 32767u;
                    break;
                }
                default:
                {
                    _529 = _71;
                    _530 = _71;
                    break;
                }
            }
            bool _539;
            if (!(_530 ? _318 : false))
            {
                bool _538;
                if (_529)
                {
                    _538 = ((g_commandParams._m0[_171].cmdpmodcolr >> 6u) & 1u) == 0u;
                }
                else
                {
                    _538 = false;
                }
                _539 = _538;
            }
            else
            {
                _539 = true;
            }
            if (_539)
            {
                break;
            }
        }
        else
        {
        }
        do
        {
            bool _563;
            if (!any(lessThan(_301, ivec2(0))))
            {
                _563 = any(greaterThan(uvec2(_301), uvec2(g_commandParams._m0[_171].sysClip & 65535u, (g_commandParams._m0[_171].sysClip >> 16u) & 65535u)));
            }
            else
            {
                _563 = true;
            }
            if (_563)
            {
                break;
            }
            if (((g_commandParams._m0[_171].cmdpmodcolr >> 10u) & 1u) != 0u)
            {
                uvec2 _582 = uvec2(_301);
                bool _590;
                if (!any(lessThan(_582, uvec2(g_commandParams._m0[_171].userClip0 & 65535u, (g_commandParams._m0[_171].userClip0 >> 16u) & 65535u))))
                {
                    _590 = any(greaterThan(_582, uvec2(g_commandParams._m0[_171].userClip1 & 65535u, (g_commandParams._m0[_171].userClip1 >> 16u) & 65535u)));
                }
                else
                {
                    _590 = true;
                }
                if (_590 != (((g_commandParams._m0[_171].cmdpmodcolr >> 9u) & 1u) != 0u))
                {
                    break;
                }
            }
            bool _608;
            if (((g_commandParams._m0[_171].cmdpmodcolr >> 8u) & 1u) != 0u)
            {
                _608 = (((uint(_301.x) / _96) ^ (uint(_301.y) / _96)) & 1u) != 0u;
            }
            else
            {
                _608 = false;
            }
            if (_608)
            {
                break;
            }
            uint _613 = _96 * _96;
            uint _615 = ((RenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _613);
            bool _625;
            if (((!_111) ? _104 : false) ? _107 : false)
            {
                _625 = uint(_301.y & 1) != _109;
            }
            else
            {
                _625 = false;
            }
            if (_625)
            {
                break;
            }
            bool _628 = _111 ? _104 : false;
            bool _634;
            if (_628)
            {
                _634 = (_301.y & 1) != 0;
            }
            else
            {
                _634 = false;
            }
            uint _639;
            if (_634)
            {
                _639 = _615 + (_613 * 524288u);
            }
            else
            {
                _639 = _615;
            }
            ivec2 _647;
            if ((!_628) ? _107 : true)
            {
                ivec2 _646 = _301;
                _646.y = _301.y >> 1;
                _647 = _646;
            }
            else
            {
                _647 = _301;
            }
            uint _654 = (uint(_647.y) * _98.x) + uint(_647.x);
            uint _660;
            if (_101)
            {
                _660 = _654 & 4294967294u;
            }
            else
            {
                _660 = _654 << 1u;
            }
            uint _661 = _660 + _639;
            uint _669 = atomicOr(g_fbramOut._m0[(_661 & 4294967292u) >> 2u], 128u << (((_661 & 2u) * 8u) & 31u));
            break;
        } while(false);
        bool _679;
        if (_177)
        {
            bool _678;
            if (_300 > 1u)
            {
                _678 = ((_299 - _216) + _214) > _271;
            }
            else
            {
                _678 = false;
            }
            _679 = _678;
        }
        else
        {
            _679 = false;
        }
        if (_679)
        {
            ivec2 _682 = _301 + _270;
            do
            {
                bool _701;
                if (!any(lessThan(_682, ivec2(0))))
                {
                    _701 = any(greaterThan(uvec2(_682), uvec2(g_commandParams._m0[_171].sysClip & 65535u, (g_commandParams._m0[_171].sysClip >> 16u) & 65535u)));
                }
                else
                {
                    _701 = true;
                }
                if (_701)
                {
                    break;
                }
                if (((g_commandParams._m0[_171].cmdpmodcolr >> 10u) & 1u) != 0u)
                {
                    uvec2 _720 = uvec2(_682);
                    bool _728;
                    if (!any(lessThan(_720, uvec2(g_commandParams._m0[_171].userClip0 & 65535u, (g_commandParams._m0[_171].userClip0 >> 16u) & 65535u))))
                    {
                        _728 = any(greaterThan(_720, uvec2(g_commandParams._m0[_171].userClip1 & 65535u, (g_commandParams._m0[_171].userClip1 >> 16u) & 65535u)));
                    }
                    else
                    {
                        _728 = true;
                    }
                    if (_728 != (((g_commandParams._m0[_171].cmdpmodcolr >> 9u) & 1u) != 0u))
                    {
                        break;
                    }
                }
                bool _746;
                if (((g_commandParams._m0[_171].cmdpmodcolr >> 8u) & 1u) != 0u)
                {
                    _746 = (((uint(_682.x) / _96) ^ (uint(_682.y) / _96)) & 1u) != 0u;
                }
                else
                {
                    _746 = false;
                }
                if (_746)
                {
                    break;
                }
                uint _751 = _96 * _96;
                uint _753 = ((RenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _751);
                bool _763;
                if (((!_111) ? _104 : false) ? _107 : false)
                {
                    _763 = uint(_682.y & 1) != _109;
                }
                else
                {
                    _763 = false;
                }
                if (_763)
                {
                    break;
                }
                bool _766 = _111 ? _104 : false;
                bool _772;
                if (_766)
                {
                    _772 = (_682.y & 1) != 0;
                }
                else
                {
                    _772 = false;
                }
                uint _777;
                if (_772)
                {
                    _777 = _753 + (_751 * 524288u);
                }
                else
                {
                    _777 = _753;
                }
                ivec2 _785;
                if ((!_766) ? _107 : true)
                {
                    ivec2 _784 = _682;
                    _784.y = _682.y >> 1;
                    _785 = _784;
                }
                else
                {
                    _785 = _682;
                }
                uint _792 = (uint(_785.y) * _98.x) + uint(_785.x);
                uint _798;
                if (_101)
                {
                    _798 = _792 & 4294967294u;
                }
                else
                {
                    _798 = _792 << 1u;
                }
                uint _799 = _798 + _777;
                uint _807 = atomicOr(g_fbramOut._m0[(_799 & 4294967292u) >> 2u], 128u << (((_799 & 2u) * 8u) & 31u));
                break;
            } while(false);
        }
        break;
    } while(false);
}

