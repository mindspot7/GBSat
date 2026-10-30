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
    uint _70 = ((RenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _68);
    if (((RenderParamsBuffer.g_commonParams.displayParams >> 2u) & 1u) != 0u)
    {
    }
    else
    {
        uint _81;
        uint _83;
        uint _85;
        do
        {
            _81 = _62.x;
            _83 = (gl_GlobalInvocationID.x * 2u) + (gl_GlobalInvocationID.y * _81);
            _85 = _62.y;
            uint _87 = _83 + ((gl_GlobalInvocationID.z * _81) * _85);
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
            uint _107 = (((_83 * 2u) + (gl_GlobalInvocationID.z * (_68 * 524288u))) + _70) >> 2u;
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
        uint _194 = gl_GlobalInvocationID.z + 2u;
        do
        {
            uint _199 = _83 + ((_194 * _81) * _85);
            uint _201 = g_internalSpriteOut._m0[_199];
            uint _202 = min(_201, 5u);
            uint _203 = _199 + 1u;
            uint _205 = g_internalSpriteOut._m0[_203];
            uint _206 = min(_205, 5u);
            bool _211;
            if (_202 == 0u)
            {
                _211 = _206 == 0u;
            }
            else
            {
                _211 = false;
            }
            if (_211)
            {
                break;
            }
            g_internalSpriteOut._m0[_199] = 0u;
            g_internalSpriteOut._m0[_203] = 0u;
            uint _219 = (((_83 * 2u) + (_194 * (_68 * 524288u))) + _70) >> 2u;
            uint _262;
            if (_202 != 0u)
            {
                uint _229 = ((g_fbramOut._m0[_219] >> 8u) & 255u) | ((g_fbramOut._m0[_219] << 8u) & 65280u);
                uint _236 = (_229 >> 15u) & 1u;
                uint _261;
                if (_236 != 0u)
                {
                    uvec3 _244 = uvec4(_229 & 31u, (_229 >> 5u) & 31u, (_229 >> 10u) & 31u, _236).xyz >> (uvec3(_202) & uvec3(31u));
                    uint _254 = ((_244.x | (_244.y << 5u)) | (_244.z << 10u)) | (_236 << 15u);
                    _261 = (g_fbramOut._m0[_219] & 4294901760u) | (((_254 >> 8u) & 255u) | ((_254 << 8u) & 65280u));
                }
                else
                {
                    _261 = g_fbramOut._m0[_219];
                }
                _262 = _261;
            }
            else
            {
                _262 = g_fbramOut._m0[_219];
            }
            uint _305;
            if (_206 != 0u)
            {
                uint _266 = _262 >> 16u;
                uint _271 = ((_266 >> 8u) & 255u) | ((_266 << 8u) & 65280u);
                uint _278 = (_271 >> 15u) & 1u;
                uint _304;
                if (_278 != 0u)
                {
                    uvec3 _286 = uvec4(_271 & 31u, (_271 >> 5u) & 31u, (_271 >> 10u) & 31u, _278).xyz >> (uvec3(_206) & uvec3(31u));
                    uint _296 = ((_286.x | (_286.y << 5u)) | (_286.z << 10u)) | (_278 << 15u);
                    _304 = (_262 & 65535u) | ((((_296 >> 8u) & 255u) | ((_296 << 8u) & 65280u)) << 16u);
                }
                else
                {
                    _304 = _262;
                }
                _305 = _304;
            }
            else
            {
                _305 = _262;
            }
            g_fbramOut._m0[_219] = _305;
            break;
        } while(false);
    }
}

