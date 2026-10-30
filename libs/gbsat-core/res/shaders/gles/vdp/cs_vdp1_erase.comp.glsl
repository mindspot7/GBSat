#version 310 es
#if defined(GL_EXT_control_flow_attributes)
#extension GL_EXT_control_flow_attributes : require
#define SPIRV_CROSS_FLATTEN [[flatten]]
#define SPIRV_CROSS_BRANCH [[dont_flatten]]
#define SPIRV_CROSS_UNROLL [[unroll]]
#define SPIRV_CROSS_LOOP [[dont_unroll]]
#else
#define SPIRV_CROSS_FLATTEN
#define SPIRV_CROSS_BRANCH
#define SPIRV_CROSS_UNROLL
#define SPIRV_CROSS_LOOP
#endif
layout(local_size_x = 32, local_size_y = 8, local_size_z = 1) in;

struct CommonRenderParams
{
    uint displayParams;
    uint enhancements;
};

struct EraseParams
{
    uint coords;
    uint erase;
    uint vblank;
};

layout(binding = 0, std140) uniform type_CommonRenderParamsBuffer
{
    CommonRenderParams g_commonParams;
    EraseParams g_eraseParams;
} CommonRenderParamsBuffer;

layout(binding = 17, std430) buffer type_RWByteAddressBuffer
{
    uint _m0[];
} g_fbramOut;

void main()
{
    bool _57 = ((CommonRenderParamsBuffer.g_commonParams.displayParams >> 3u) & 1u) != 0u;
    uint _65 = max(((CommonRenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uint _66 = _65 * _65;
    uint _68 = (((CommonRenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) ^ 1u) * (262144u * _66);
    bool _70 = (CommonRenderParamsBuffer.g_commonParams.enhancements & 1u) != 0u;
    bool _73 = ((CommonRenderParamsBuffer.g_commonParams.enhancements >> 1u) & 1u) != 0u;
    bool _77 = (CommonRenderParamsBuffer.g_eraseParams.vblank & 1u) != 0u;
    uint _79 = (CommonRenderParamsBuffer.g_eraseParams.vblank >> 1u) & 511u;
    uint _81 = (CommonRenderParamsBuffer.g_eraseParams.vblank >> 10u) & 1023u;
    uint _86 = ((CommonRenderParamsBuffer.g_eraseParams.erase >> 16u) & 1u) + 8u;
    uint _91 = (CommonRenderParamsBuffer.g_eraseParams.coords & 63u) << 3u;
    uint _94 = (CommonRenderParamsBuffer.g_eraseParams.coords >> 31u) & 1u;
    uint _95 = ((CommonRenderParamsBuffer.g_eraseParams.coords >> 6u) & 511u) << _94;
    uint _98 = ((CommonRenderParamsBuffer.g_eraseParams.coords >> 15u) & 127u) << 3u;
    uint _101 = ((CommonRenderParamsBuffer.g_eraseParams.coords >> 22u) & 511u) << _94;
    uint _107 = (gl_WorkGroupID.x * 32u) + gl_LocalInvocationID.x;
    uint _113 = (gl_WorkGroupID.y * 32u) + gl_LocalInvocationID.y;
    SPIRV_CROSS_UNROLL
    for (uint _115 = 0u; _115 < 4u; _115++)
    {
        do
        {
            uint _126 = (_107 * 2u) + _91;
            uint _127 = (_113 + (_115 * 8u)) + _95;
            bool _134;
            if (_126 < (_98 + 1u))
            {
                _134 = _127 > (_101 + 1u);
            }
            else
            {
                _134 = true;
            }
            if (_134)
            {
                break;
            }
            bool _154;
            if (_77)
            {
                if (_127 > _79)
                {
                    break;
                }
                bool _142 = _127 == _79;
                bool _146;
                if (_142)
                {
                    _146 = _126 > _81;
                }
                else
                {
                    _146 = false;
                }
                if (_146)
                {
                    break;
                }
                bool _153;
                if (_142)
                {
                    _153 = (_126 + 1u) > _81;
                }
                else
                {
                    _153 = false;
                }
                _154 = _153;
            }
            else
            {
                _154 = false;
            }
            uint _155 = CommonRenderParamsBuffer.g_eraseParams.erase & 65535u;
            if (_65 > 1u)
            {
                uint _161 = _154 ? 4294901760u : 0u;
                uint _167;
                if (_154)
                {
                    _167 = _155;
                }
                else
                {
                    _167 = (_155 << 16u) | _155;
                }
                uint _168 = (1u << (_86 & 31u)) * _65;
                for (uint _170 = 0u; _170 < _65; _170++)
                {
                    uint _177 = (_127 * _65) + _170;
                    for (uint _179 = 0u; _179 < _65; _179++)
                    {
                        uint _186 = _179 * 2u;
                        uint _190 = ((_186 / _65) * 16u) & 31u;
                        uint _198 = (((_186 + 1u) / _65) * 16u) & 31u;
                        uint _202 = ((_161 >> _190) & 65535u) | (((_161 >> _198) & 65535u) << 16u);
                        uint _206 = ((_167 >> _190) & 65535u) | (((_167 >> _198) & 65535u) << 16u);
                        uint _210 = _68 + (((_177 * _168) + ((_126 * _65) + _186)) * 2u);
                        uint _219;
                        if (_202 != 0u)
                        {
                            _219 = _206 | (g_fbramOut._m0[_210 >> 2u] & _202);
                        }
                        else
                        {
                            _219 = _206;
                        }
                        g_fbramOut._m0[_210 >> 2u] = _219;
                    }
                }
                if (_73)
                {
                    uint _225 = _68 + (_66 * 1048576u);
                    for (uint _227 = 0u; _227 < _65; _227++)
                    {
                        uint _234 = (_127 * _65) + _227;
                        for (uint _236 = 0u; _236 < _65; )
                        {
                            g_fbramOut._m0[(_225 + (((_234 * _168) + ((_126 * _65) + (_236 * 2u))) * 2u)) >> 2u] = 0u;
                            _236++;
                            continue;
                        }
                    }
                }
                break;
            }
            uint _254 = _68 + (((_127 << (_86 & 31u)) + _126) * 2u);
            uint _265;
            if (_154)
            {
                _265 = (g_fbramOut._m0[_254 >> 2u] & 4294901760u) | _155;
            }
            else
            {
                _265 = (_155 << 16u) | _155;
            }
            g_fbramOut._m0[_254 >> 2u] = _265;
            if (_73)
            {
                g_fbramOut._m0[(_254 + (_66 * 1048576u)) >> 2u] = 0u;
            }
            if (_70 ? _57 : false)
            {
                g_fbramOut._m0[(_254 + (_66 * 524288u)) >> 2u] = _265;
                if (_73)
                {
                    g_fbramOut._m0[(_254 + (_66 * 1572864u)) >> 2u] = 0u;
                }
            }
            break;
        } while(false);
    }
}

