#version 310 es
layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;

struct CommonRenderParams
{
    uint displayParams;
    uint enhancements;
};

struct FBRAMWriteParams
{
    uint writeCount;
};

struct FBRAMWrite
{
    uint address;
    uint andMask;
    uint orMask;
};

layout(binding = 0, std140) uniform type_CommonRenderParamsBuffer
{
    CommonRenderParams g_commonParams;
    FBRAMWriteParams g_writeParams;
} CommonRenderParamsBuffer;

layout(binding = 1, std430) readonly buffer type_StructuredBuffer_FBRAMWrite
{
    FBRAMWrite _m0[];
} g_fbramWrites;

layout(binding = 17, std430) buffer type_RWByteAddressBuffer
{
    uint _m0[];
} g_fbramOut;

void main()
{
    uint _55 = max(((CommonRenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uint _56 = _55 * _55;
    uint _58 = ((CommonRenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _56);
    bool _63 = ((CommonRenderParamsBuffer.g_commonParams.enhancements >> 1u) & 1u) != 0u;
    do
    {
        if (gl_GlobalInvocationID.x >= CommonRenderParamsBuffer.g_writeParams.writeCount)
        {
            break;
        }
        uint _74 = g_fbramWrites._m0[gl_GlobalInvocationID.x].address;
        uint _76 = g_fbramWrites._m0[gl_GlobalInvocationID.x].andMask;
        uint _78 = g_fbramWrites._m0[gl_GlobalInvocationID.x].orMask;
        if (_55 > 1u)
        {
            uint _83 = 512u << (CommonRenderParamsBuffer.g_commonParams.displayParams & 1u);
            uint _84 = _74 >> 1u;
            uint _85 = _84 % _83;
            uint _86 = _84 / _83;
            uint _87 = _83 * _55;
            for (uint _89 = 0u; _89 < _55; _89++)
            {
                uint _96 = (_86 * _55) + _89;
                for (uint _98 = 0u; _98 < _55; _98++)
                {
                    uint _105 = _98 * 2u;
                    uint _109 = ((_105 / _55) * 16u) & 31u;
                    uint _117 = (((_105 + 1u) / _55) * 16u) & 31u;
                    uint _121 = ((_76 >> _109) & 65535u) | (((_76 >> _117) & 65535u) << 16u);
                    uint _125 = ((_78 >> _109) & 65535u) | (((_78 >> _117) & 65535u) << 16u);
                    uint _129 = _58 + (((_96 * _87) + ((_85 * _55) + _105)) * 2u);
                    uint _138;
                    if (_121 != 0u)
                    {
                        _138 = _125 | (g_fbramOut._m0[_129 >> 2u] & _121);
                    }
                    else
                    {
                        _138 = _125;
                    }
                    g_fbramOut._m0[_129 >> 2u] = _138;
                }
            }
            if (_63)
            {
                uint _144 = _58 + (_56 * 1048576u);
                for (uint _146 = 0u; _146 < _55; _146++)
                {
                    uint _153 = (_86 * _55) + _146;
                    for (uint _155 = 0u; _155 < _55; _155++)
                    {
                        uint _162 = _155 * 2u;
                        uint _176 = ((_76 >> (((_162 / _55) * 16u) & 31u)) & 65535u) | (((_76 >> ((((_162 + 1u) / _55) * 16u) & 31u)) & 65535u) << 16u);
                        uint _180 = _144 + (((_153 * _87) + ((_85 * _55) + _162)) * 2u);
                        uint _188;
                        if (_176 != 0u)
                        {
                            _188 = g_fbramOut._m0[_180 >> 2u] & _176;
                        }
                        else
                        {
                            _188 = 0u;
                        }
                        g_fbramOut._m0[_180 >> 2u] = _188;
                    }
                }
            }
            break;
        }
        uint _191 = _74 + _58;
        uint _192 = _191 >> 2u;
        uint _194 = g_fbramOut._m0[_192];
        uint _196 = (_194 & _76) | _78;
        g_fbramOut._m0[_192] = _196;
        if (_63)
        {
            uint _201 = (_191 + (_56 * 1048576u)) >> 2u;
            g_fbramOut._m0[_201] &= _76;
        }
        if (((CommonRenderParamsBuffer.g_commonParams.enhancements & 1u) != 0u) ? (((CommonRenderParamsBuffer.g_commonParams.displayParams >> 3u) & 1u) != 0u) : false)
        {
            g_fbramOut._m0[(_191 + (_56 * 524288u)) >> 2u] = _196;
            if (_63)
            {
                uint _216 = (_191 + (_56 * 1572864u)) >> 2u;
                g_fbramOut._m0[_216] &= _76;
            }
        }
        break;
    } while(false);
}

