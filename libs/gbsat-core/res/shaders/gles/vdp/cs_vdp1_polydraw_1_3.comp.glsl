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

uint _71;
bool _72;
ivec2 _83;

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
    uint _99 = max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uvec2 _101 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(_99);
    bool _104 = ((RenderParamsBuffer.g_commonParams.displayParams >> 2u) & 1u) != 0u;
    bool _107 = ((RenderParamsBuffer.g_commonParams.displayParams >> 3u) & 1u) != 0u;
    bool _110 = ((RenderParamsBuffer.g_commonParams.displayParams >> 4u) & 1u) != 0u;
    uint _112 = (RenderParamsBuffer.g_commonParams.displayParams >> 5u) & 1u;
    bool _114 = (RenderParamsBuffer.g_commonParams.enhancements & 1u) != 0u;
    do
    {
        uint _155;
        do
        {
            if (gl_GlobalInvocationID.x >= texelFetch(g_spanPrefixSums, int(RenderParamsBuffer.g_polyDrawParams.numSpans)).x)
            {
                _155 = 4294967295u;
                break;
            }
            uint _133;
            _133 = 0u;
            uint _131;
            uint _134;
            uint _151;
            bool _152;
            uint _130 = RenderParamsBuffer.g_polyDrawParams.numSpans;
            for (;;)
            {
                if (_133 != _130)
                {
                    uint _139 = (_133 + _130) >> 1u;
                    uvec4 _141 = texelFetch(g_spanPrefixSums, int(_139));
                    uint _142 = _141.x;
                    if (gl_GlobalInvocationID.x == _142)
                    {
                        _151 = _139;
                        _152 = true;
                        break;
                    }
                    bool _146 = gl_GlobalInvocationID.x > _142;
                    if (_146)
                    {
                        _134 = _139 + 1u;
                    }
                    else
                    {
                        _134 = _133;
                    }
                    _131 = _146 ? _130 : _139;
                    _130 = _131;
                    _133 = _134;
                    continue;
                }
                else
                {
                    _151 = _71;
                    _152 = false;
                    break;
                }
            }
            if (_152)
            {
                _155 = _151;
                break;
            }
            _155 = _133 - 1u;
            break;
        } while(false);
        if (_155 == 4294967295u)
        {
            break;
        }
        uint _164 = g_spanParams._m0[_155].skip_cmdIndex;
        uint _166 = g_spanParams._m0[_155].attrs;
        uint _172 = (gl_GlobalInvocationID.x - texelFetch(g_spanPrefixSums, int(_155)).x) + (_164 & 65535u);
        uint _174 = (_164 >> 16u) & 65535u;
        bool _180 = (_166 & 1u) != 0u;
        ivec2 _184 = g_spanParams._m0[_155].coord1 - g_spanParams._m0[_155].coord0;
        ivec2 _185 = abs(_184);
        int _186 = _185.x;
        int _187 = _185.y;
        uint _189 = uint(max(_186, _187));
        bool _190 = _186 >= _187;
        ivec2 _212;
        ivec2 _213;
        ivec2 _214;
        ivec2 _215;
        if (_190)
        {
            _212 = ivec2(0, (_184.y >= 0) ? 1 : (-1));
            _213 = ivec2((_184.x >= 0) ? 1 : (-1), 0);
            _214 = _184;
            _215 = _185;
        }
        else
        {
            _212 = ivec2((_184.x >= 0) ? 1 : (-1), 0);
            _213 = ivec2(0, (_184.y >= 0) ? 1 : (-1));
            _214 = _184.yx;
            _215 = _185.yx;
        }
        int _217 = _215.y << 1;
        int _219 = _215.x << 1;
        bool _226;
        if (!_180)
        {
            _226 = _214.x < 0;
        }
        else
        {
            _226 = false;
        }
        int _228 = (_215.x + 1) + _217;
        ivec2 _229 = g_spanParams._m0[_155].coord0 - _213;
        ivec2 _273;
        int _274;
        int _275;
        if (_180)
        {
            int _233 = 0;
            int _234 = -1;
            bool _242 = (g_spanParams._m0[_155].coord0.x > g_spanParams._m0[_155].coord1.x) == (g_spanParams._m0[_155].coord0.y > g_spanParams._m0[_155].coord1.y);
            ivec2 _272;
            if (_190)
            {
                int _251;
                if (_242)
                {
                    _251 = 0;
                }
                else
                {
                    _251 = -_213.x;
                }
                int _257;
                if (_242)
                {
                    _257 = -_212.y;
                }
                else
                {
                    _257 = 0;
                }
                _272 = ivec2(_251, _257);
            }
            else
            {
                int _264;
                if (_242)
                {
                    _264 = 0;
                }
                else
                {
                    _264 = -_212.x;
                }
                int _270;
                if (_242)
                {
                    _270 = -_213.y;
                }
                else
                {
                    _270 = 0;
                }
                _272 = ivec2(_264, _270);
            }
            _273 = _272;
            _274 = _226 ? 0 : (-1);
            _275 = _228 - 1;
        }
        else
        {
            _273 = _83;
            _274 = int(_226);
            _275 = _228;
        }
        int _302;
        uint _303;
        ivec2 _304;
        do
        {
            uint _279 = min(_172, _189) + 1u;
            int _280 = int(_279);
            if (_280 == 0)
            {
                _302 = _275;
                _303 = 0u;
                _304 = _229;
                break;
            }
            ivec2 _286 = _229 + (_213 * ivec2(_280));
            int _288 = _275 - (_217 * _280);
            int _300;
            ivec2 _301;
            if (_219 != 0)
            {
                int _294 = ((_274 - _288) + _219) / _219;
                _300 = _288 + (_219 * _294);
                _301 = _286 + (_212 * ivec2(_294));
            }
            else
            {
                _300 = _288;
                _301 = _286;
            }
            _302 = _300;
            _303 = _279;
            _304 = _301;
            break;
        } while(false);
        if (((_166 >> 1u) & 1u) != 0u)
        {
            uint _311 = max((((g_commandParams._m0[_174].cmdsizesrca >> 8u) & 63u) << 3u), 1u);
            bool _314 = ((_166 >> 2u) & 1u) != 0u;
            bool _321 = ((g_commandParams._m0[_174].cmdpmodcolr >> 7u) & 1u) == 0u;
            bool _329;
            if (((g_commandParams._m0[_174].cmdpmodcolr >> 12u) & 1u) != 0u)
            {
                _329 = _189 < (_311 - 1u);
            }
            else
            {
                _329 = false;
            }
            int _334 = int(_311 - 1u);
            int _335 = _314 ? 0 : _334;
            int _336 = _314 ? _334 : 0;
            uint _337 = _189 + 1u;
            int _343;
            int _344;
            if (_329)
            {
                _343 = _336 >> 1;
                _344 = _335 >> 1;
            }
            else
            {
                _343 = _336;
                _344 = _335;
            }
            int _345 = _344 - _343;
            int _346 = abs(_345);
            uint _347 = uint(_346);
            bool _348 = _345 >= 0;
            int _357;
            int _358;
            if (_329)
            {
                int _354 = 2;
                int _355 = -2;
                _357 = (_343 << 1) | int(((RenderParamsBuffer.g_commonParams.displayParams >> 6u) & 1u) != 0u);
                _358 = _348 ? 2 : (-2);
            }
            else
            {
                _357 = _343;
                _358 = _348 ? 1 : (-1);
            }
            int _359 = int(_337);
            int _381;
            int _382;
            int _383;
            if (_337 <= _347)
            {
                int _367 = int(_347 - (_337 << 1u));
                int _371;
                if (_348)
                {
                    _371 = _367 + 1;
                }
                else
                {
                    _371 = _367;
                }
                _381 = _371;
                _382 = _359;
                _383 = _346 + 1;
            }
            else
            {
                int _375 = int(_337 - (_337 << 1u));
                int _380;
                if (_345 < 0)
                {
                    _380 = _375 + 1;
                }
                else
                {
                    _380 = _375;
                }
                _381 = _380;
                _382 = _359 - 1;
                _383 = _346;
            }
            int _390 = int(uint(_381) + (uint(_383 << 1) * _172));
            int _398;
            if (_390 >= 0)
            {
                _398 = _357 + (_358 * ((_390 / (_382 << 1)) + 1));
            }
            else
            {
                _398 = _357;
            }
            bool _402;
            if (_321)
            {
                _402 = !_329;
            }
            else
            {
                _402 = false;
            }
            uint _409;
            bool _410;
            if (_402)
            {
                uint _407 = (_166 >> 13u) & 1023u;
                _409 = _407;
                _410 = _407 < _311;
            }
            else
            {
                _409 = _71;
                _410 = false;
            }
            uint _411 = uint(_398);
            bool _420;
            if (_410)
            {
                bool _419;
                if (_314)
                {
                    _419 = _411 <= _409;
                }
                else
                {
                    _419 = _411 >= _409;
                }
                _420 = _419;
            }
            else
            {
                _420 = false;
            }
            if (_420)
            {
                break;
            }
            uint _427 = ((g_commandParams._m0[_174].cmdsizesrca >> 16u) & 65535u) << 3u;
            uint _429 = _411 + (((_166 >> 3u) & 1023u) * _311);
            bool _532;
            bool _533;
            switch ((g_commandParams._m0[_174].cmdpmodcolr >> 3u) & 7u)
            {
                case 0u:
                {
                    uint _438 = _427 + (_429 >> 1u);
                    uint _453 = (((g_vram._m0[(_438 & 524284u) >> 2u] >> (((_438 & 3u) * 8u) & 31u)) & 255u) >> ((((~_411) & 1u) * 4u) & 31u)) & 15u;
                    _532 = _453 == 0u;
                    _533 = _453 == 15u;
                    break;
                }
                case 1u:
                {
                    uint _457 = _427 + (_429 >> 1u);
                    uint _472 = (((g_vram._m0[(_457 & 524284u) >> 2u] >> (((_457 & 3u) * 8u) & 31u)) & 255u) >> ((((~_411) & 1u) * 4u) & 31u)) & 15u;
                    _532 = _472 == 0u;
                    _533 = _472 == 15u;
                    break;
                }
                case 2u:
                {
                    uint _475 = _427 + _429;
                    uint _484 = (g_vram._m0[(_475 & 524284u) >> 2u] >> (((_475 & 3u) * 8u) & 31u)) & 255u;
                    _532 = _484 == 0u;
                    _533 = _484 == 255u;
                    break;
                }
                case 3u:
                {
                    uint _487 = _427 + _429;
                    uint _496 = (g_vram._m0[(_487 & 524284u) >> 2u] >> (((_487 & 3u) * 8u) & 31u)) & 255u;
                    _532 = _496 == 0u;
                    _533 = _496 == 255u;
                    break;
                }
                case 4u:
                {
                    uint _499 = _427 + _429;
                    uint _508 = (g_vram._m0[(_499 & 524284u) >> 2u] >> (((_499 & 3u) * 8u) & 31u)) & 255u;
                    _532 = _508 == 0u;
                    _533 = _508 == 255u;
                    break;
                }
                case 5u:
                {
                    uint _513 = (_427 & 4294967280u) + (_429 * 2u);
                    uint _522 = (g_vram._m0[(_513 & 524284u) >> 2u] >> (((_513 & 2u) * 8u) & 31u)) & 65535u;
                    uint _527 = ((_522 >> 8u) & 255u) | ((_522 << 8u) & 65280u);
                    _532 = ((_527 >> 15u) & 1u) == 0u;
                    _533 = _527 == 32767u;
                    break;
                }
                default:
                {
                    _532 = _72;
                    _533 = _72;
                    break;
                }
            }
            bool _542;
            if (!(_533 ? _321 : false))
            {
                bool _541;
                if (_532)
                {
                    _541 = ((g_commandParams._m0[_174].cmdpmodcolr >> 6u) & 1u) == 0u;
                }
                else
                {
                    _541 = false;
                }
                _542 = _541;
            }
            else
            {
                _542 = true;
            }
            if (_542)
            {
                break;
            }
        }
        else
        {
        }
        do
        {
            bool _566;
            if (!any(lessThan(_304, ivec2(0))))
            {
                _566 = any(greaterThan(uvec2(_304), uvec2(g_commandParams._m0[_174].sysClip & 65535u, (g_commandParams._m0[_174].sysClip >> 16u) & 65535u)));
            }
            else
            {
                _566 = true;
            }
            if (_566)
            {
                break;
            }
            if (((g_commandParams._m0[_174].cmdpmodcolr >> 10u) & 1u) != 0u)
            {
                uvec2 _585 = uvec2(_304);
                bool _593;
                if (!any(lessThan(_585, uvec2(g_commandParams._m0[_174].userClip0 & 65535u, (g_commandParams._m0[_174].userClip0 >> 16u) & 65535u))))
                {
                    _593 = any(greaterThan(_585, uvec2(g_commandParams._m0[_174].userClip1 & 65535u, (g_commandParams._m0[_174].userClip1 >> 16u) & 65535u)));
                }
                else
                {
                    _593 = true;
                }
                if (_593 != (((g_commandParams._m0[_174].cmdpmodcolr >> 9u) & 1u) != 0u))
                {
                    break;
                }
            }
            uint _602 = _99 * _99;
            uint _604 = ((RenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _602);
            bool _614;
            if (((!_114) ? _107 : false) ? _110 : false)
            {
                _614 = uint(_304.y & 1) != _112;
            }
            else
            {
                _614 = false;
            }
            if (_614)
            {
                break;
            }
            bool _617 = _114 ? _107 : false;
            bool _623;
            if (_617)
            {
                _623 = (_304.y & 1) != 0;
            }
            else
            {
                _623 = false;
            }
            uint _628;
            if (_623)
            {
                _628 = _604 + (_602 * 524288u);
            }
            else
            {
                _628 = _604;
            }
            ivec2 _636;
            if ((!_617) ? _110 : true)
            {
                ivec2 _635 = _304;
                _635.y = _304.y >> 1;
                _636 = _635;
            }
            else
            {
                _636 = _304;
            }
            uint _639 = _101.x;
            uint _643 = (uint(_636.y) * _639) + uint(_636.x);
            uint _650;
            if (((g_commandParams._m0[_174].cmdpmodcolr >> 8u) & 1u) != 0u)
            {
                _650 = _643 + ((_639 * _101.y) * 2u);
            }
            else
            {
                _650 = _643;
            }
            uint _656;
            if (_104)
            {
                _656 = _650 & 4294967294u;
            }
            else
            {
                _656 = _650 << 1u;
            }
            uint _657 = _656 + _628;
            uint _665 = atomicOr(g_fbramOut._m0[(_657 & 4294967292u) >> 2u], 128u << (((_657 & 2u) * 8u) & 31u));
            break;
        } while(false);
        bool _675;
        if (_180)
        {
            bool _674;
            if (_303 > 1u)
            {
                _674 = ((_302 - _219) + _217) > _274;
            }
            else
            {
                _674 = false;
            }
            _675 = _674;
        }
        else
        {
            _675 = false;
        }
        if (_675)
        {
            ivec2 _678 = _304 + _273;
            do
            {
                bool _697;
                if (!any(lessThan(_678, ivec2(0))))
                {
                    _697 = any(greaterThan(uvec2(_678), uvec2(g_commandParams._m0[_174].sysClip & 65535u, (g_commandParams._m0[_174].sysClip >> 16u) & 65535u)));
                }
                else
                {
                    _697 = true;
                }
                if (_697)
                {
                    break;
                }
                if (((g_commandParams._m0[_174].cmdpmodcolr >> 10u) & 1u) != 0u)
                {
                    uvec2 _716 = uvec2(_678);
                    bool _724;
                    if (!any(lessThan(_716, uvec2(g_commandParams._m0[_174].userClip0 & 65535u, (g_commandParams._m0[_174].userClip0 >> 16u) & 65535u))))
                    {
                        _724 = any(greaterThan(_716, uvec2(g_commandParams._m0[_174].userClip1 & 65535u, (g_commandParams._m0[_174].userClip1 >> 16u) & 65535u)));
                    }
                    else
                    {
                        _724 = true;
                    }
                    if (_724 != (((g_commandParams._m0[_174].cmdpmodcolr >> 9u) & 1u) != 0u))
                    {
                        break;
                    }
                }
                uint _733 = _99 * _99;
                uint _735 = ((RenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _733);
                bool _745;
                if (((!_114) ? _107 : false) ? _110 : false)
                {
                    _745 = uint(_678.y & 1) != _112;
                }
                else
                {
                    _745 = false;
                }
                if (_745)
                {
                    break;
                }
                bool _748 = _114 ? _107 : false;
                bool _754;
                if (_748)
                {
                    _754 = (_678.y & 1) != 0;
                }
                else
                {
                    _754 = false;
                }
                uint _759;
                if (_754)
                {
                    _759 = _735 + (_733 * 524288u);
                }
                else
                {
                    _759 = _735;
                }
                ivec2 _767;
                if ((!_748) ? _110 : true)
                {
                    ivec2 _766 = _678;
                    _766.y = _678.y >> 1;
                    _767 = _766;
                }
                else
                {
                    _767 = _678;
                }
                uint _770 = _101.x;
                uint _774 = (uint(_767.y) * _770) + uint(_767.x);
                uint _781;
                if (((g_commandParams._m0[_174].cmdpmodcolr >> 8u) & 1u) != 0u)
                {
                    _781 = _774 + ((_770 * _101.y) * 2u);
                }
                else
                {
                    _781 = _774;
                }
                uint _787;
                if (_104)
                {
                    _787 = _781 & 4294967294u;
                }
                else
                {
                    _787 = _781 << 1u;
                }
                uint _788 = _787 + _759;
                uint _796 = atomicOr(g_fbramOut._m0[(_788 & 4294967292u) >> 2u], 128u << (((_788 & 2u) * 8u) & 31u));
                break;
            } while(false);
        }
        break;
    } while(false);
}

