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

uint _69;
bool _70;
ivec2 _78;

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
    uvec2 _96 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u));
    bool _99 = ((RenderParamsBuffer.g_commonParams.displayParams >> 3u) & 1u) != 0u;
    bool _102 = ((RenderParamsBuffer.g_commonParams.displayParams >> 4u) & 1u) != 0u;
    uint _104 = (RenderParamsBuffer.g_commonParams.displayParams >> 5u) & 1u;
    bool _106 = (RenderParamsBuffer.g_commonParams.enhancements & 1u) != 0u;
    do
    {
        uint _147;
        do
        {
            if (gl_GlobalInvocationID.x >= texelFetch(g_spanPrefixSums, int(RenderParamsBuffer.g_polyDrawParams.numSpans)).x)
            {
                _147 = 4294967295u;
                break;
            }
            uint _125;
            _125 = 0u;
            uint _123;
            uint _126;
            uint _143;
            bool _144;
            uint _122 = RenderParamsBuffer.g_polyDrawParams.numSpans;
            for (;;)
            {
                if (_125 != _122)
                {
                    uint _131 = (_125 + _122) >> 1u;
                    uvec4 _133 = texelFetch(g_spanPrefixSums, int(_131));
                    uint _134 = _133.x;
                    if (gl_GlobalInvocationID.x == _134)
                    {
                        _143 = _131;
                        _144 = true;
                        break;
                    }
                    bool _138 = gl_GlobalInvocationID.x > _134;
                    if (_138)
                    {
                        _126 = _131 + 1u;
                    }
                    else
                    {
                        _126 = _125;
                    }
                    _123 = _138 ? _122 : _131;
                    _122 = _123;
                    _125 = _126;
                    continue;
                }
                else
                {
                    _143 = _69;
                    _144 = false;
                    break;
                }
            }
            if (_144)
            {
                _147 = _143;
                break;
            }
            _147 = _125 - 1u;
            break;
        } while(false);
        if (_147 == 4294967295u)
        {
            break;
        }
        uint _156 = g_spanParams._m0[_147].skip_cmdIndex;
        uint _158 = g_spanParams._m0[_147].attrs;
        uint _164 = (gl_GlobalInvocationID.x - texelFetch(g_spanPrefixSums, int(_147)).x) + (_156 & 65535u);
        uint _166 = (_156 >> 16u) & 65535u;
        bool _172 = (_158 & 1u) != 0u;
        ivec2 _176 = g_spanParams._m0[_147].coord1 - g_spanParams._m0[_147].coord0;
        ivec2 _177 = abs(_176);
        int _178 = _177.x;
        int _179 = _177.y;
        uint _181 = uint(max(_178, _179));
        bool _182 = _178 >= _179;
        ivec2 _204;
        ivec2 _205;
        ivec2 _206;
        ivec2 _207;
        if (_182)
        {
            _204 = ivec2(0, (_176.y >= 0) ? 1 : (-1));
            _205 = ivec2((_176.x >= 0) ? 1 : (-1), 0);
            _206 = _176;
            _207 = _177;
        }
        else
        {
            _204 = ivec2((_176.x >= 0) ? 1 : (-1), 0);
            _205 = ivec2(0, (_176.y >= 0) ? 1 : (-1));
            _206 = _176.yx;
            _207 = _177.yx;
        }
        int _209 = _207.y << 1;
        int _211 = _207.x << 1;
        bool _218;
        if (!_172)
        {
            _218 = _206.x < 0;
        }
        else
        {
            _218 = false;
        }
        int _220 = (_207.x + 1) + _209;
        ivec2 _221 = g_spanParams._m0[_147].coord0 - _205;
        ivec2 _265;
        int _266;
        int _267;
        if (_172)
        {
            int _225 = 0;
            int _226 = -1;
            bool _234 = (g_spanParams._m0[_147].coord0.x > g_spanParams._m0[_147].coord1.x) == (g_spanParams._m0[_147].coord0.y > g_spanParams._m0[_147].coord1.y);
            ivec2 _264;
            if (_182)
            {
                int _243;
                if (_234)
                {
                    _243 = 0;
                }
                else
                {
                    _243 = -_205.x;
                }
                int _249;
                if (_234)
                {
                    _249 = -_204.y;
                }
                else
                {
                    _249 = 0;
                }
                _264 = ivec2(_243, _249);
            }
            else
            {
                int _256;
                if (_234)
                {
                    _256 = 0;
                }
                else
                {
                    _256 = -_204.x;
                }
                int _262;
                if (_234)
                {
                    _262 = -_205.y;
                }
                else
                {
                    _262 = 0;
                }
                _264 = ivec2(_256, _262);
            }
            _265 = _264;
            _266 = _218 ? 0 : (-1);
            _267 = _220 - 1;
        }
        else
        {
            _265 = _78;
            _266 = int(_218);
            _267 = _220;
        }
        int _294;
        uint _295;
        ivec2 _296;
        do
        {
            uint _271 = min(_164, _181) + 1u;
            int _272 = int(_271);
            if (_272 == 0)
            {
                _294 = _267;
                _295 = 0u;
                _296 = _221;
                break;
            }
            ivec2 _278 = _221 + (_205 * ivec2(_272));
            int _280 = _267 - (_209 * _272);
            int _292;
            ivec2 _293;
            if (_211 != 0)
            {
                int _286 = ((_266 - _280) + _211) / _211;
                _292 = _280 + (_211 * _286);
                _293 = _278 + (_204 * ivec2(_286));
            }
            else
            {
                _292 = _280;
                _293 = _278;
            }
            _294 = _292;
            _295 = _271;
            _296 = _293;
            break;
        } while(false);
        if (((_158 >> 1u) & 1u) != 0u)
        {
            uint _303 = max((((g_commandParams._m0[_166].cmdsizesrca >> 8u) & 63u) << 3u), 1u);
            bool _306 = ((_158 >> 2u) & 1u) != 0u;
            bool _313 = ((g_commandParams._m0[_166].cmdpmodcolr >> 7u) & 1u) == 0u;
            bool _321;
            if (((g_commandParams._m0[_166].cmdpmodcolr >> 12u) & 1u) != 0u)
            {
                _321 = _181 < (_303 - 1u);
            }
            else
            {
                _321 = false;
            }
            int _326 = int(_303 - 1u);
            int _327 = _306 ? 0 : _326;
            int _328 = _306 ? _326 : 0;
            uint _329 = _181 + 1u;
            int _335;
            int _336;
            if (_321)
            {
                _335 = _328 >> 1;
                _336 = _327 >> 1;
            }
            else
            {
                _335 = _328;
                _336 = _327;
            }
            int _337 = _336 - _335;
            int _338 = abs(_337);
            uint _339 = uint(_338);
            bool _340 = _337 >= 0;
            int _349;
            int _350;
            if (_321)
            {
                int _346 = 2;
                int _347 = -2;
                _349 = (_335 << 1) | int(((RenderParamsBuffer.g_commonParams.displayParams >> 6u) & 1u) != 0u);
                _350 = _340 ? 2 : (-2);
            }
            else
            {
                _349 = _335;
                _350 = _340 ? 1 : (-1);
            }
            int _351 = int(_329);
            int _373;
            int _374;
            int _375;
            if (_329 <= _339)
            {
                int _359 = int(_339 - (_329 << 1u));
                int _363;
                if (_340)
                {
                    _363 = _359 + 1;
                }
                else
                {
                    _363 = _359;
                }
                _373 = _363;
                _374 = _351;
                _375 = _338 + 1;
            }
            else
            {
                int _367 = int(_329 - (_329 << 1u));
                int _372;
                if (_337 < 0)
                {
                    _372 = _367 + 1;
                }
                else
                {
                    _372 = _367;
                }
                _373 = _372;
                _374 = _351 - 1;
                _375 = _338;
            }
            int _382 = int(uint(_373) + (uint(_375 << 1) * _164));
            int _390;
            if (_382 >= 0)
            {
                _390 = _349 + (_350 * ((_382 / (_374 << 1)) + 1));
            }
            else
            {
                _390 = _349;
            }
            bool _394;
            if (_313)
            {
                _394 = !_321;
            }
            else
            {
                _394 = false;
            }
            uint _401;
            bool _402;
            if (_394)
            {
                uint _399 = (_158 >> 13u) & 1023u;
                _401 = _399;
                _402 = _399 < _303;
            }
            else
            {
                _401 = _69;
                _402 = false;
            }
            uint _403 = uint(_390);
            bool _412;
            if (_402)
            {
                bool _411;
                if (_306)
                {
                    _411 = _403 <= _401;
                }
                else
                {
                    _411 = _403 >= _401;
                }
                _412 = _411;
            }
            else
            {
                _412 = false;
            }
            if (_412)
            {
                break;
            }
            uint _419 = ((g_commandParams._m0[_166].cmdsizesrca >> 16u) & 65535u) << 3u;
            uint _421 = _403 + (((_158 >> 3u) & 1023u) * _303);
            bool _524;
            bool _525;
            switch ((g_commandParams._m0[_166].cmdpmodcolr >> 3u) & 7u)
            {
                case 0u:
                {
                    uint _430 = _419 + (_421 >> 1u);
                    uint _445 = (((g_vram._m0[(_430 & 524284u) >> 2u] >> (((_430 & 3u) * 8u) & 31u)) & 255u) >> ((((~_403) & 1u) * 4u) & 31u)) & 15u;
                    _524 = _445 == 0u;
                    _525 = _445 == 15u;
                    break;
                }
                case 1u:
                {
                    uint _449 = _419 + (_421 >> 1u);
                    uint _464 = (((g_vram._m0[(_449 & 524284u) >> 2u] >> (((_449 & 3u) * 8u) & 31u)) & 255u) >> ((((~_403) & 1u) * 4u) & 31u)) & 15u;
                    _524 = _464 == 0u;
                    _525 = _464 == 15u;
                    break;
                }
                case 2u:
                {
                    uint _467 = _419 + _421;
                    uint _476 = (g_vram._m0[(_467 & 524284u) >> 2u] >> (((_467 & 3u) * 8u) & 31u)) & 255u;
                    _524 = _476 == 0u;
                    _525 = _476 == 255u;
                    break;
                }
                case 3u:
                {
                    uint _479 = _419 + _421;
                    uint _488 = (g_vram._m0[(_479 & 524284u) >> 2u] >> (((_479 & 3u) * 8u) & 31u)) & 255u;
                    _524 = _488 == 0u;
                    _525 = _488 == 255u;
                    break;
                }
                case 4u:
                {
                    uint _491 = _419 + _421;
                    uint _500 = (g_vram._m0[(_491 & 524284u) >> 2u] >> (((_491 & 3u) * 8u) & 31u)) & 255u;
                    _524 = _500 == 0u;
                    _525 = _500 == 255u;
                    break;
                }
                case 5u:
                {
                    uint _505 = (_419 & 4294967280u) + (_421 * 2u);
                    uint _514 = (g_vram._m0[(_505 & 524284u) >> 2u] >> (((_505 & 2u) * 8u) & 31u)) & 65535u;
                    uint _519 = ((_514 >> 8u) & 255u) | ((_514 << 8u) & 65280u);
                    _524 = ((_519 >> 15u) & 1u) == 0u;
                    _525 = _519 == 32767u;
                    break;
                }
                default:
                {
                    _524 = _70;
                    _525 = _70;
                    break;
                }
            }
            bool _534;
            if (!(_525 ? _313 : false))
            {
                bool _533;
                if (_524)
                {
                    _533 = ((g_commandParams._m0[_166].cmdpmodcolr >> 6u) & 1u) == 0u;
                }
                else
                {
                    _533 = false;
                }
                _534 = _533;
            }
            else
            {
                _534 = true;
            }
            if (_534)
            {
                break;
            }
        }
        else
        {
        }
        do
        {
            bool _558;
            if (!any(lessThan(_296, ivec2(0))))
            {
                _558 = any(greaterThan(uvec2(_296), uvec2(g_commandParams._m0[_166].sysClip & 65535u, (g_commandParams._m0[_166].sysClip >> 16u) & 65535u)));
            }
            else
            {
                _558 = true;
            }
            if (_558)
            {
                break;
            }
            if (((g_commandParams._m0[_166].cmdpmodcolr >> 10u) & 1u) != 0u)
            {
                uvec2 _577 = uvec2(_296);
                bool _585;
                if (!any(lessThan(_577, uvec2(g_commandParams._m0[_166].userClip0 & 65535u, (g_commandParams._m0[_166].userClip0 >> 16u) & 65535u))))
                {
                    _585 = any(greaterThan(_577, uvec2(g_commandParams._m0[_166].userClip1 & 65535u, (g_commandParams._m0[_166].userClip1 >> 16u) & 65535u)));
                }
                else
                {
                    _585 = true;
                }
                if (_585 != (((g_commandParams._m0[_166].cmdpmodcolr >> 9u) & 1u) != 0u))
                {
                    break;
                }
            }
            bool _601;
            if (((!_106) ? _99 : false) ? _102 : false)
            {
                _601 = uint(_296.y & 1) != _104;
            }
            else
            {
                _601 = false;
            }
            if (_601)
            {
                break;
            }
            bool _604 = _106 ? _99 : false;
            bool _610;
            if (_604)
            {
                _610 = (_296.y & 1) != 0;
            }
            else
            {
                _610 = false;
            }
            uint _616;
            if (_610)
            {
                _616 = _96.x * _96.y;
            }
            else
            {
                _616 = 0u;
            }
            ivec2 _624;
            if ((!_604) ? _102 : true)
            {
                ivec2 _623 = _296;
                _623.y = _296.y >> 1;
                _624 = _623;
            }
            else
            {
                _624 = _296;
            }
            uint _627 = _96.x;
            uint _632 = _616 + ((uint(_624.y) * _627) + uint(_624.x));
            uint _639;
            if (((g_commandParams._m0[_166].cmdpmodcolr >> 8u) & 1u) != 0u)
            {
                _639 = _632 + ((_627 * _96.y) * 2u);
            }
            else
            {
                _639 = _632;
            }
            uint _641 = atomicAdd(g_internalSpriteOut._m0[_639], 1u);
            break;
        } while(false);
        bool _651;
        if (_172)
        {
            bool _650;
            if (_295 > 1u)
            {
                _650 = ((_294 - _211) + _209) > _266;
            }
            else
            {
                _650 = false;
            }
            _651 = _650;
        }
        else
        {
            _651 = false;
        }
        if (_651)
        {
            ivec2 _654 = _296 + _265;
            do
            {
                bool _673;
                if (!any(lessThan(_654, ivec2(0))))
                {
                    _673 = any(greaterThan(uvec2(_654), uvec2(g_commandParams._m0[_166].sysClip & 65535u, (g_commandParams._m0[_166].sysClip >> 16u) & 65535u)));
                }
                else
                {
                    _673 = true;
                }
                if (_673)
                {
                    break;
                }
                if (((g_commandParams._m0[_166].cmdpmodcolr >> 10u) & 1u) != 0u)
                {
                    uvec2 _692 = uvec2(_654);
                    bool _700;
                    if (!any(lessThan(_692, uvec2(g_commandParams._m0[_166].userClip0 & 65535u, (g_commandParams._m0[_166].userClip0 >> 16u) & 65535u))))
                    {
                        _700 = any(greaterThan(_692, uvec2(g_commandParams._m0[_166].userClip1 & 65535u, (g_commandParams._m0[_166].userClip1 >> 16u) & 65535u)));
                    }
                    else
                    {
                        _700 = true;
                    }
                    if (_700 != (((g_commandParams._m0[_166].cmdpmodcolr >> 9u) & 1u) != 0u))
                    {
                        break;
                    }
                }
                bool _716;
                if (((!_106) ? _99 : false) ? _102 : false)
                {
                    _716 = uint(_654.y & 1) != _104;
                }
                else
                {
                    _716 = false;
                }
                if (_716)
                {
                    break;
                }
                bool _719 = _106 ? _99 : false;
                bool _725;
                if (_719)
                {
                    _725 = (_654.y & 1) != 0;
                }
                else
                {
                    _725 = false;
                }
                uint _731;
                if (_725)
                {
                    _731 = _96.x * _96.y;
                }
                else
                {
                    _731 = 0u;
                }
                ivec2 _739;
                if ((!_719) ? _102 : true)
                {
                    ivec2 _738 = _654;
                    _738.y = _654.y >> 1;
                    _739 = _738;
                }
                else
                {
                    _739 = _654;
                }
                uint _742 = _96.x;
                uint _747 = _731 + ((uint(_739.y) * _742) + uint(_739.x));
                uint _754;
                if (((g_commandParams._m0[_166].cmdpmodcolr >> 8u) & 1u) != 0u)
                {
                    _754 = _747 + ((_742 * _96.y) * 2u);
                }
                else
                {
                    _754 = _747;
                }
                uint _756 = atomicAdd(g_internalSpriteOut._m0[_754], 1u);
                break;
            } while(false);
        }
        break;
    } while(false);
}

