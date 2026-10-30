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
    uint _60 = max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uvec2 _62 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(_60);
    uint _68 = _60 * _60;
    if (((RenderParamsBuffer.g_commonParams.displayParams >> 2u) & 1u) != 0u)
    {
    }
    else
    {
        do
        {
            uint _81 = _62.x;
            uint _83 = (gl_GlobalInvocationID.x * 2u) + (gl_GlobalInvocationID.y * _81);
            uint _87 = _83 + ((gl_GlobalInvocationID.z * _81) * _62.y);
            uint _89 = g_internalSpriteOut._m0[_87];
            uint _90 = min(_89, 5u);
            uint _91 = _87 + 1u;
            uint _93 = g_internalSpriteOut._m0[_91];
            uint _94 = min(_93, 5u);
            bool _99;
            if (_90 == 0u)
            {
                _99 = _94 == 0u;
            }
            else
            {
                _99 = false;
            }
            if (_99)
            {
                break;
            }
            g_internalSpriteOut._m0[_87] = 0u;
            g_internalSpriteOut._m0[_91] = 0u;
            uint _107 = (((_83 * 2u) + (gl_GlobalInvocationID.z * (_68 * 524288u))) + (((RenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _68))) >> 2u;
            uint _150;
            if (_90 != 0u)
            {
                uint _117 = ((g_fbramOut._m0[_107] >> 8u) & 255u) | ((g_fbramOut._m0[_107] << 8u) & 65280u);
                uint _124 = (_117 >> 15u) & 1u;
                uint _149;
                if (_124 != 0u)
                {
                    uvec3 _132 = uvec4(_117 & 31u, (_117 >> 5u) & 31u, (_117 >> 10u) & 31u, _124).xyz >> (uvec3(_90) & uvec3(31u));
                    uint _142 = ((_132.x | (_132.y << 5u)) | (_132.z << 10u)) | (_124 << 15u);
                    _149 = (g_fbramOut._m0[_107] & 4294901760u) | (((_142 >> 8u) & 255u) | ((_142 << 8u) & 65280u));
                }
                else
                {
                    _149 = g_fbramOut._m0[_107];
                }
                _150 = _149;
            }
            else
            {
                _150 = g_fbramOut._m0[_107];
            }
            uint _193;
            if (_94 != 0u)
            {
                uint _154 = _150 >> 16u;
                uint _159 = ((_154 >> 8u) & 255u) | ((_154 << 8u) & 65280u);
                uint _166 = (_159 >> 15u) & 1u;
                uint _192;
                if (_166 != 0u)
                {
                    uvec3 _174 = uvec4(_159 & 31u, (_159 >> 5u) & 31u, (_159 >> 10u) & 31u, _166).xyz >> (uvec3(_94) & uvec3(31u));
                    uint _184 = ((_174.x | (_174.y << 5u)) | (_174.z << 10u)) | (_166 << 15u);
                    _192 = (_150 & 65535u) | ((((_184 >> 8u) & 255u) | ((_184 << 8u) & 65280u)) << 16u);
                }
                else
                {
                    _192 = _150;
                }
                _193 = _192;
            }
            else
            {
                _193 = _150;
            }
            g_fbramOut._m0[_107] = _193;
            break;
        } while(false);
    }
}

