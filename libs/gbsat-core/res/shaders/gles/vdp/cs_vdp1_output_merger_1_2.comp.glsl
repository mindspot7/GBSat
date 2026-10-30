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
    uint _78 = max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uvec2 _80 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(_78);
    uint _86 = _78 * _78;
    uint _88 = ((RenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _86);
    if (((RenderParamsBuffer.g_commonParams.displayParams >> 2u) & 1u) != 0u)
    {
    }
    else
    {
        uint _99;
        uint _101;
        uint _103;
        do
        {
            _99 = _80.x;
            _101 = (gl_GlobalInvocationID.x * 2u) + (gl_GlobalInvocationID.y * _99);
            _103 = _80.y;
            uint _105 = _101 + ((gl_GlobalInvocationID.z * _99) * _103);
            uint _107 = g_listHeads._m0[_105];
            uint _108 = _105 + 1u;
            uint _110 = g_listHeads._m0[_108];
            if (all(equal(uvec2(_107, _110), uvec2(4294967295u))))
            {
                break;
            }
            g_listHeads._m0[_105] = 4294967295u;
            g_listHeads._m0[_108] = 4294967295u;
            uint _121 = (((_101 * 2u) + _88) + (gl_GlobalInvocationID.z * (_86 * 524288u))) >> 2u;
            uint _124 = g_fbramOut._m0[_121] >> 16u;
            uint _129 = ((_124 >> 8u) & 255u) | ((_124 << 8u) & 65280u);
            uint _238;
            do
            {
                if (_110 == 4294967295u)
                {
                    _238 = _129;
                    break;
                }
                uint _136;
                _136 = 0u;
                OITFragment _65[32];
                uint _139 = _110;
                for (;;)
                {
                    bool _147;
                    if (_139 != 4294967295u)
                    {
                        _147 = _136 < 32u;
                    }
                    else
                    {
                        _147 = false;
                    }
                    if (_147)
                    {
                        _65[_136] = OITFragment(g_fragments._m0[_139].data, g_fragments._m0[_139].next);
                        _136++;
                        _139 = _65[_136].next;
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
                for (uint _157 = 1u; _157 < _136; _157++)
                {
                    OITFragment _164 = _65[_157];
                    int _169;
                    _169 = int(_157 - 1u);
                    for (;;)
                    {
                        bool _184;
                        if (_169 >= 0)
                        {
                            _184 = ((_65[_169].data >> 16u) & 65535u) < ((_164.data >> 16u) & 65535u);
                        }
                        else
                        {
                            _184 = false;
                        }
                        if (_184)
                        {
                            _65[_169 + 1] = _65[_169];
                            _169--;
                            continue;
                        }
                        else
                        {
                            break;
                        }
                    }
                    _65[_169 + 1] = _164;
                }
                uvec4 _200;
                _200 = uvec4(_129 & 31u, (_129 >> 5u) & 31u, (_129 >> 10u) & 31u, (_129 >> 15u) & 1u);
                uvec4 _201;
                for (uint _203 = 0u; _203 < _136; _200 = _201, _203++)
                {
                    uvec4 _217 = uvec4(_65[_203].data & 31u, (_65[_203].data >> 5u) & 31u, (_65[_203].data >> 10u) & 31u, (_65[_203].data >> 15u) & 1u);
                    if (_200.w != 0u)
                    {
                        uvec3 _226 = (_200.xyz + _217.xyz) >> uvec3(1u);
                        _201 = uvec4(_226.x, _226.y, _226.z, _200.w);
                    }
                    else
                    {
                        _201 = _217;
                    }
                }
                _238 = ((_200.x | (_200.y << 5u)) | (_200.z << 10u)) | (_200.w << 15u);
                break;
            } while(false);
            uint _249 = ((g_fbramOut._m0[_121] >> 8u) & 255u) | ((g_fbramOut._m0[_121] << 8u) & 65280u);
            uint _358;
            do
            {
                if (_107 == 4294967295u)
                {
                    _358 = _249;
                    break;
                }
                uint _256;
                _256 = 0u;
                OITFragment _64[32];
                uint _259 = _107;
                for (;;)
                {
                    bool _267;
                    if (_259 != 4294967295u)
                    {
                        _267 = _256 < 32u;
                    }
                    else
                    {
                        _267 = false;
                    }
                    if (_267)
                    {
                        _64[_256] = OITFragment(g_fragments._m0[_259].data, g_fragments._m0[_259].next);
                        _256++;
                        _259 = _64[_256].next;
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
                for (uint _277 = 1u; _277 < _256; _277++)
                {
                    OITFragment _284 = _64[_277];
                    int _289;
                    _289 = int(_277 - 1u);
                    for (;;)
                    {
                        bool _304;
                        if (_289 >= 0)
                        {
                            _304 = ((_64[_289].data >> 16u) & 65535u) < ((_284.data >> 16u) & 65535u);
                        }
                        else
                        {
                            _304 = false;
                        }
                        if (_304)
                        {
                            _64[_289 + 1] = _64[_289];
                            _289--;
                            continue;
                        }
                        else
                        {
                            break;
                        }
                    }
                    _64[_289 + 1] = _284;
                }
                uvec4 _320;
                _320 = uvec4(_249 & 31u, (_249 >> 5u) & 31u, (_249 >> 10u) & 31u, (_249 >> 15u) & 1u);
                uvec4 _321;
                for (uint _323 = 0u; _323 < _256; _320 = _321, _323++)
                {
                    uvec4 _337 = uvec4(_64[_323].data & 31u, (_64[_323].data >> 5u) & 31u, (_64[_323].data >> 10u) & 31u, (_64[_323].data >> 15u) & 1u);
                    if (_320.w != 0u)
                    {
                        uvec3 _346 = (_320.xyz + _337.xyz) >> uvec3(1u);
                        _321 = uvec4(_346.x, _346.y, _346.z, _320.w);
                    }
                    else
                    {
                        _321 = _337;
                    }
                }
                _358 = ((_320.x | (_320.y << 5u)) | (_320.z << 10u)) | (_320.w << 15u);
                break;
            } while(false);
            g_fbramOut._m0[_121] = ((((_238 >> 8u) & 255u) | ((_238 << 8u) & 65280u)) << 16u) | (((_358 >> 8u) & 255u) | ((_358 << 8u) & 65280u));
            break;
        } while(false);
        uint _365 = gl_GlobalInvocationID.z + 2u;
        do
        {
            uint _370 = _101 + ((_365 * _99) * _103);
            uint _372 = g_listHeads._m0[_370];
            uint _373 = _370 + 1u;
            uint _375 = g_listHeads._m0[_373];
            if (all(equal(uvec2(_372, _375), uvec2(4294967295u))))
            {
                break;
            }
            g_listHeads._m0[_370] = 4294967295u;
            g_listHeads._m0[_373] = 4294967295u;
            uint _386 = (((_101 * 2u) + _88) + (_365 * (_86 * 524288u))) >> 2u;
            uint _389 = g_fbramOut._m0[_386] >> 16u;
            uint _394 = ((_389 >> 8u) & 255u) | ((_389 << 8u) & 65280u);
            uint _503;
            do
            {
                if (_375 == 4294967295u)
                {
                    _503 = _394;
                    break;
                }
                uint _401;
                _401 = 0u;
                OITFragment _63[32];
                uint _404 = _375;
                for (;;)
                {
                    bool _412;
                    if (_404 != 4294967295u)
                    {
                        _412 = _401 < 32u;
                    }
                    else
                    {
                        _412 = false;
                    }
                    if (_412)
                    {
                        _63[_401] = OITFragment(g_fragments._m0[_404].data, g_fragments._m0[_404].next);
                        _401++;
                        _404 = _63[_401].next;
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
                for (uint _422 = 1u; _422 < _401; _422++)
                {
                    OITFragment _429 = _63[_422];
                    int _434;
                    _434 = int(_422 - 1u);
                    for (;;)
                    {
                        bool _449;
                        if (_434 >= 0)
                        {
                            _449 = ((_63[_434].data >> 16u) & 65535u) < ((_429.data >> 16u) & 65535u);
                        }
                        else
                        {
                            _449 = false;
                        }
                        if (_449)
                        {
                            _63[_434 + 1] = _63[_434];
                            _434--;
                            continue;
                        }
                        else
                        {
                            break;
                        }
                    }
                    _63[_434 + 1] = _429;
                }
                uvec4 _465;
                _465 = uvec4(_394 & 31u, (_394 >> 5u) & 31u, (_394 >> 10u) & 31u, (_394 >> 15u) & 1u);
                uvec4 _466;
                for (uint _468 = 0u; _468 < _401; _465 = _466, _468++)
                {
                    uvec4 _482 = uvec4(_63[_468].data & 31u, (_63[_468].data >> 5u) & 31u, (_63[_468].data >> 10u) & 31u, (_63[_468].data >> 15u) & 1u);
                    if (_465.w != 0u)
                    {
                        uvec3 _491 = (_465.xyz + _482.xyz) >> uvec3(1u);
                        _466 = uvec4(_491.x, _491.y, _491.z, _465.w);
                    }
                    else
                    {
                        _466 = _482;
                    }
                }
                _503 = ((_465.x | (_465.y << 5u)) | (_465.z << 10u)) | (_465.w << 15u);
                break;
            } while(false);
            uint _514 = ((g_fbramOut._m0[_386] >> 8u) & 255u) | ((g_fbramOut._m0[_386] << 8u) & 65280u);
            uint _623;
            do
            {
                if (_372 == 4294967295u)
                {
                    _623 = _514;
                    break;
                }
                uint _521;
                _521 = 0u;
                OITFragment _62[32];
                uint _524 = _372;
                for (;;)
                {
                    bool _532;
                    if (_524 != 4294967295u)
                    {
                        _532 = _521 < 32u;
                    }
                    else
                    {
                        _532 = false;
                    }
                    if (_532)
                    {
                        _62[_521] = OITFragment(g_fragments._m0[_524].data, g_fragments._m0[_524].next);
                        _521++;
                        _524 = _62[_521].next;
                        continue;
                    }
                    else
                    {
                        break;
                    }
                }
                for (uint _542 = 1u; _542 < _521; _542++)
                {
                    OITFragment _549 = _62[_542];
                    int _554;
                    _554 = int(_542 - 1u);
                    for (;;)
                    {
                        bool _569;
                        if (_554 >= 0)
                        {
                            _569 = ((_62[_554].data >> 16u) & 65535u) < ((_549.data >> 16u) & 65535u);
                        }
                        else
                        {
                            _569 = false;
                        }
                        if (_569)
                        {
                            _62[_554 + 1] = _62[_554];
                            _554--;
                            continue;
                        }
                        else
                        {
                            break;
                        }
                    }
                    _62[_554 + 1] = _549;
                }
                uvec4 _585;
                _585 = uvec4(_514 & 31u, (_514 >> 5u) & 31u, (_514 >> 10u) & 31u, (_514 >> 15u) & 1u);
                uvec4 _586;
                for (uint _588 = 0u; _588 < _521; _585 = _586, _588++)
                {
                    uvec4 _602 = uvec4(_62[_588].data & 31u, (_62[_588].data >> 5u) & 31u, (_62[_588].data >> 10u) & 31u, (_62[_588].data >> 15u) & 1u);
                    if (_585.w != 0u)
                    {
                        uvec3 _611 = (_585.xyz + _602.xyz) >> uvec3(1u);
                        _586 = uvec4(_611.x, _611.y, _611.z, _585.w);
                    }
                    else
                    {
                        _586 = _602;
                    }
                }
                _623 = ((_585.x | (_585.y << 5u)) | (_585.z << 10u)) | (_585.w << 15u);
                break;
            } while(false);
            g_fbramOut._m0[_386] = ((((_503 >> 8u) & 255u) | ((_503 << 8u) & 65280u)) << 16u) | (((_623 >> 8u) & 255u) | ((_623 << 8u) & 65280u));
            break;
        } while(false);
    }
}

