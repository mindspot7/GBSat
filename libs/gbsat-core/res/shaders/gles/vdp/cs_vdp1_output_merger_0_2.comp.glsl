#version 310 es
layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

struct CommonRenderParams
{
    uint displayParams;
    uint enhancements;
};

struct OITFragment
{
    uint data;
    uint next;
};

layout(binding = 0, std140) uniform type_RenderParamsBuffer
{
    CommonRenderParams g_commonParams;
} RenderParamsBuffer;

layout(binding = 1, std430) readonly buffer type_StructuredBuffer_OITFragment
{
    OITFragment _m0[];
} g_fragments;

layout(binding = 17, std430) buffer type_RWByteAddressBuffer
{
    uint _m0[];
} g_fbramOut;

layout(binding = 18, std430) buffer type_RWStructuredBuffer_uint
{
    uint _m0[];
} g_listHeads;

void main()
{
    uint _76 = max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uvec2 _78 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(_76);
    uint _84 = _76 * _76;
    if (((RenderParamsBuffer.g_commonParams.displayParams >> 2u) & 1u) != 0u)
    {
    }
    else
    {
        do
        {
            uint _97 = _78.x;
            uint _99 = (gl_GlobalInvocationID.x * 2u) + (gl_GlobalInvocationID.y * _97);
            uint _103 = _99 + ((gl_GlobalInvocationID.z * _97) * _78.y);
            uint _105 = g_listHeads._m0[_103];
            uint _106 = _103 + 1u;
            uint _108 = g_listHeads._m0[_106];
            if (all(equal(uvec2(_105, _108), uvec2(4294967295u))))
            {
                break;
            }
            g_listHeads._m0[_103] = 4294967295u;
            g_listHeads._m0[_106] = 4294967295u;
            uint _119 = (((_99 * 2u) + (((RenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _84))) + (gl_GlobalInvocationID.z * (_84 * 524288u))) >> 2u;
            uint _122 = g_fbramOut._m0[_119] >> 16u;
            uint _127 = ((_122 >> 8u) & 255u) | ((_122 << 8u) & 65280u);
            uint _236;
            do
            {
                if (_108 == 4294967295u)
                {
                    _236 = _127;
                    break;
                }
                uint _134;
                _134 = 0u;
                OITFragment _63[32];
                uint _137 = _108;
                for (;;)
                {
                    bool _145;
                    if (_137 != 4294967295u)
                    {
                        _145 = _134 < 32u;
                    }
                    else
                    {
                        _145 = false;
                    }
                    if (_145)
                    {
                        _63[_134] = OITFragment(g_fragments._m0[_137].data, g_fragments._m0[_137].next);
                        _134++;
                        _137 = _63[_134].next;
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
                for (uint _155 = 1u; _155 < _134; _155++)
                {
                    OITFragment _162 = _63[_155];
                    int _167;
                    _167 = int(_155 - 1u);
                    for (;;)
                    {
                        bool _182;
                        if (_167 >= 0)
                        {
                            _182 = ((_63[_167].data >> 16u) & 65535u) < ((_162.data >> 16u) & 65535u);
                        }
                        else
                        {
                            _182 = false;
                        }
                        if (_182)
                        {
                            _63[_167 + 1] = _63[_167];
                            _167--;
                            continue;
                        }
                        else
                        {
                            break;
                        }
                    }
                    _63[_167 + 1] = _162;
                }
                uvec4 _198;
                _198 = uvec4(_127 & 31u, (_127 >> 5u) & 31u, (_127 >> 10u) & 31u, (_127 >> 15u) & 1u);
                uvec4 _199;
                for (uint _201 = 0u; _201 < _134; _198 = _199, _201++)
                {
                    uvec4 _215 = uvec4(_63[_201].data & 31u, (_63[_201].data >> 5u) & 31u, (_63[_201].data >> 10u) & 31u, (_63[_201].data >> 15u) & 1u);
                    if (_198.w != 0u)
                    {
                        uvec3 _224 = (_198.xyz + _215.xyz) >> uvec3(1u);
                        _199 = uvec4(_224.x, _224.y, _224.z, _198.w);
                    }
                    else
                    {
                        _199 = _215;
                    }
                }
                _236 = ((_198.x | (_198.y << 5u)) | (_198.z << 10u)) | (_198.w << 15u);
                break;
            } while(false);
            uint _247 = ((g_fbramOut._m0[_119] >> 8u) & 255u) | ((g_fbramOut._m0[_119] << 8u) & 65280u);
            uint _356;
            do
            {
                if (_105 == 4294967295u)
                {
                    _356 = _247;
                    break;
                }
                uint _254;
                _254 = 0u;
                OITFragment _62[32];
                uint _257 = _105;
                for (;;)
                {
                    bool _265;
                    if (_257 != 4294967295u)
                    {
                        _265 = _254 < 32u;
                    }
                    else
                    {
                        _265 = false;
                    }
                    if (_265)
                    {
                        _62[_254] = OITFragment(g_fragments._m0[_257].data, g_fragments._m0[_257].next);
                        _254++;
                        _257 = _62[_254].next;
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
                for (uint _275 = 1u; _275 < _254; _275++)
                {
                    OITFragment _282 = _62[_275];
                    int _287;
                    _287 = int(_275 - 1u);
                    for (;;)
                    {
                        bool _302;
                        if (_287 >= 0)
                        {
                            _302 = ((_62[_287].data >> 16u) & 65535u) < ((_282.data >> 16u) & 65535u);
                        }
                        else
                        {
                            _302 = false;
                        }
                        if (_302)
                        {
                            _62[_287 + 1] = _62[_287];
                            _287--;
                            continue;
                        }
                        else
                        {
                            break;
                        }
                    }
                    _62[_287 + 1] = _282;
                }
                uvec4 _318;
                _318 = uvec4(_247 & 31u, (_247 >> 5u) & 31u, (_247 >> 10u) & 31u, (_247 >> 15u) & 1u);
                uvec4 _319;
                for (uint _321 = 0u; _321 < _254; _318 = _319, _321++)
                {
                    uvec4 _335 = uvec4(_62[_321].data & 31u, (_62[_321].data >> 5u) & 31u, (_62[_321].data >> 10u) & 31u, (_62[_321].data >> 15u) & 1u);
                    if (_318.w != 0u)
                    {
                        uvec3 _344 = (_318.xyz + _335.xyz) >> uvec3(1u);
                        _319 = uvec4(_344.x, _344.y, _344.z, _318.w);
                    }
                    else
                    {
                        _319 = _335;
                    }
                }
                _356 = ((_318.x | (_318.y << 5u)) | (_318.z << 10u)) | (_318.w << 15u);
                break;
            } while(false);
            g_fbramOut._m0[_119] = ((((_236 >> 8u) & 255u) | ((_236 << 8u) & 65280u)) << 16u) | (((_356 >> 8u) & 255u) | ((_356 << 8u) & 65280u));
            break;
        } while(false);
    }
}

