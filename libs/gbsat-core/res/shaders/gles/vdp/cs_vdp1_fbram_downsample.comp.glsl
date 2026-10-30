#version 310 es
layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;

struct CommonRenderParams
{
    uint displayParams;
    uint enhancements;
};

layout(binding = 0, std140) uniform type_CommonRenderParamsBuffer
{
    CommonRenderParams g_commonParams;
} CommonRenderParamsBuffer;

layout(binding = 17, std430) buffer type_RWByteAddressBuffer
{
    uint _m0[];
} g_fbramIn;

layout(binding = 18, std430) buffer g_nativeOut
{
    uint _m0[];
} g_nativeOut_1;

void main()
{
    do
    {
        if (gl_GlobalInvocationID.x >= 131072u)
        {
            break;
        }
        uint _45 = (gl_GlobalInvocationID.x % 65536u) * 2u;
        uint _46 = _45 % 512u;
        uint _52 = max(((CommonRenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
        uint _55 = (gl_GlobalInvocationID.x / 65536u) * (262144u * (_52 * _52));
        uint _59 = ((_45 / 512u) * _52) * (512u * _52);
        uint _62 = _55 + ((_59 + (_46 * _52)) * 2u);
        uint _64 = (_62 & 4294967292u) >> 2u;
        uint _74;
        if ((_62 & 2u) != 0u)
        {
            _74 = g_fbramIn._m0[_64] >> 16u;
        }
        else
        {
            _74 = g_fbramIn._m0[_64] & 65535u;
        }
        uint _79 = _55 + ((_59 + ((_46 + 1u) * _52)) * 2u);
        uint _81 = (_79 & 4294967292u) >> 2u;
        uint _91;
        if ((_79 & 2u) != 0u)
        {
            _91 = g_fbramIn._m0[_81] >> 16u;
        }
        else
        {
            _91 = g_fbramIn._m0[_81] & 65535u;
        }
        g_nativeOut_1._m0[(gl_GlobalInvocationID.x * 4u) >> 2u] = _74 | (_91 << 16u);
        break;
    } while(false);
}

