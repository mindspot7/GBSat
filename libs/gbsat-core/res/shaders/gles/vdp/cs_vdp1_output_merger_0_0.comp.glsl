#version 310 es
layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

struct CommonRenderParams
{
    uint displayParams;
    uint enhancements;
};

layout(binding = 0, std140) uniform type_RenderParamsBuffer
{
    CommonRenderParams g_commonParams;
} RenderParamsBuffer;

layout(binding = 17, std430) buffer type_RWByteAddressBuffer
{
    uint _m0[];
} g_fbramOut;

layout(binding = 18, std430) buffer type_RWStructuredBuffer_uint
{
    uint _m0[];
} g_internalSpriteOut;

void main()
{
    uint _61 = max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uvec2 _63 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(_61);
    uint _69 = _61 * _61;
    uint _71 = ((RenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _69);
    if (((RenderParamsBuffer.g_commonParams.displayParams >> 2u) & 1u) != 0u)
    {
        do
        {
            uint _82 = _63.x;
            uint _84 = (gl_GlobalInvocationID.x * 4u) + (gl_GlobalInvocationID.y * _82);
            uint _88 = _84 + ((gl_GlobalInvocationID.z * _82) * _63.y);
            uint _90 = g_internalSpriteOut._m0[_88];
            uint _91 = _88 + 1u;
            uint _93 = g_internalSpriteOut._m0[_91];
            uint _94 = _88 + 2u;
            uint _96 = g_internalSpriteOut._m0[_94];
            uint _97 = _88 + 3u;
            uint _99 = g_internalSpriteOut._m0[_97];
            bool _108 = ((_90 >> 16u) & 65535u) != 0u;
            bool _109 = ((_93 >> 16u) & 65535u) != 0u;
            bool _110 = ((_96 >> 16u) & 65535u) != 0u;
            bool _111 = ((_99 >> 16u) & 65535u) != 0u;
            bool _116;
            if (!_108)
            {
                _116 = !_109;
            }
            else
            {
                _116 = false;
            }
            bool _120;
            if (_116)
            {
                _120 = !_110;
            }
            else
            {
                _120 = false;
            }
            bool _124;
            if (_120)
            {
                _124 = !_111;
            }
            else
            {
                _124 = false;
            }
            if (_124)
            {
                break;
            }
            g_internalSpriteOut._m0[_88] = 0u;
            g_internalSpriteOut._m0[_91] = 0u;
            g_internalSpriteOut._m0[_94] = 0u;
            g_internalSpriteOut._m0[_97] = 0u;
            uint _131 = ((_84 + (gl_GlobalInvocationID.z * (_69 * 524288u))) + _71) >> 2u;
            uint _139;
            if (_108)
            {
                _139 = (g_fbramOut._m0[_131] & 4294967040u) | (_90 & 255u);
            }
            else
            {
                _139 = g_fbramOut._m0[_131];
            }
            uint _146;
            if (_109)
            {
                _146 = (_139 & 4294902015u) | ((_93 & 255u) << 8u);
            }
            else
            {
                _146 = _139;
            }
            uint _153;
            if (_110)
            {
                _153 = (_146 & 4278255615u) | ((_96 & 255u) << 16u);
            }
            else
            {
                _153 = _146;
            }
            uint _160;
            if (_111)
            {
                _160 = (_153 & 16777215u) | ((_99 & 255u) << 24u);
            }
            else
            {
                _160 = _153;
            }
            g_fbramOut._m0[_131] = _160;
            break;
        } while(false);
    }
    else
    {
        do
        {
            uint _167 = _63.x;
            uint _169 = (gl_GlobalInvocationID.x * 2u) + (gl_GlobalInvocationID.y * _167);
            uint _173 = _169 + ((gl_GlobalInvocationID.z * _167) * _63.y);
            uint _175 = g_internalSpriteOut._m0[_173];
            uint _176 = _173 + 1u;
            uint _178 = g_internalSpriteOut._m0[_176];
            bool _183 = ((_175 >> 16u) & 65535u) != 0u;
            bool _184 = ((_178 >> 16u) & 65535u) != 0u;
            bool _189;
            if (!_183)
            {
                _189 = !_184;
            }
            else
            {
                _189 = false;
            }
            if (_189)
            {
                break;
            }
            g_internalSpriteOut._m0[_173] = 0u;
            g_internalSpriteOut._m0[_176] = 0u;
            uint _197 = (((_169 * 2u) + (gl_GlobalInvocationID.z * (_69 * 524288u))) + _71) >> 2u;
            uint _209;
            if (_183)
            {
                _209 = (g_fbramOut._m0[_197] & 4294901760u) | (((_175 >> 8u) & 255u) | ((_175 << 8u) & 65280u));
            }
            else
            {
                _209 = g_fbramOut._m0[_197];
            }
            uint _220;
            if (_184)
            {
                _220 = (_209 & 65535u) | ((((_178 >> 8u) & 255u) | ((_178 << 8u) & 65280u)) << 16u);
            }
            else
            {
                _220 = _209;
            }
            g_fbramOut._m0[_197] = _220;
            break;
        } while(false);
    }
}

