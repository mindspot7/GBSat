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
    uint _63 = max(((RenderParamsBuffer.g_commonParams.enhancements >> 2u) & 7u), 1u);
    uvec2 _65 = uvec2(512u << (RenderParamsBuffer.g_commonParams.displayParams & 1u), 256u << ((RenderParamsBuffer.g_commonParams.displayParams >> 1u) & 1u)) * uvec2(_63);
    uint _71 = _63 * _63;
    uint _73 = ((RenderParamsBuffer.g_commonParams.displayParams >> 7u) & 1u) * (262144u * _71);
    if (((RenderParamsBuffer.g_commonParams.displayParams >> 2u) & 1u) != 0u)
    {
        do
        {
            uint _84 = _65.x;
            uint _86 = (gl_GlobalInvocationID.x * 4u) + (gl_GlobalInvocationID.y * _84);
            uint _88 = _65.y;
            uint _90 = _86 + ((gl_GlobalInvocationID.z * _84) * _88);
            uint _92 = g_internalSpriteOut._m0[_90];
            uint _93 = _90 + 1u;
            uint _95 = g_internalSpriteOut._m0[_93];
            uint _96 = _90 + 2u;
            uint _98 = g_internalSpriteOut._m0[_96];
            uint _99 = _90 + 3u;
            uint _101 = g_internalSpriteOut._m0[_99];
            uint _103 = (_92 >> 16u) & 65535u;
            uint _105 = (_95 >> 16u) & 65535u;
            uint _107 = (_98 >> 16u) & 65535u;
            uint _109 = (_101 >> 16u) & 65535u;
            bool _110 = _103 != 0u;
            bool _111 = _105 != 0u;
            bool _112 = _107 != 0u;
            bool _113 = _109 != 0u;
            uint _116 = _90 + ((_84 * _88) * 2u);
            uint _118 = g_internalSpriteOut._m0[_116];
            uint _119 = _116 + 1u;
            uint _121 = g_internalSpriteOut._m0[_119];
            uint _122 = _116 + 2u;
            uint _124 = g_internalSpriteOut._m0[_122];
            uint _125 = _116 + 3u;
            uint _127 = g_internalSpriteOut._m0[_125];
            uint _129 = (_118 >> 16u) & 65535u;
            uint _131 = (_121 >> 16u) & 65535u;
            uint _133 = (_124 >> 16u) & 65535u;
            uint _135 = (_127 >> 16u) & 65535u;
            bool _140;
            if (!_110)
            {
                _140 = _129 != 0u;
            }
            else
            {
                _140 = true;
            }
            bool _145;
            if (!_111)
            {
                _145 = _131 != 0u;
            }
            else
            {
                _145 = true;
            }
            bool _150;
            if (!_112)
            {
                _150 = _133 != 0u;
            }
            else
            {
                _150 = true;
            }
            bool _155;
            if (!_113)
            {
                _155 = _135 != 0u;
            }
            else
            {
                _155 = true;
            }
            bool _160;
            if (!_140)
            {
                _160 = !_145;
            }
            else
            {
                _160 = false;
            }
            bool _164;
            if (_160)
            {
                _164 = !_150;
            }
            else
            {
                _164 = false;
            }
            bool _168;
            if (_164)
            {
                _168 = !_155;
            }
            else
            {
                _168 = false;
            }
            if (_168)
            {
                break;
            }
            g_internalSpriteOut._m0[_90] = 0u;
            g_internalSpriteOut._m0[_93] = 0u;
            g_internalSpriteOut._m0[_96] = 0u;
            g_internalSpriteOut._m0[_99] = 0u;
            g_internalSpriteOut._m0[_116] = 0u;
            g_internalSpriteOut._m0[_119] = 0u;
            g_internalSpriteOut._m0[_122] = 0u;
            g_internalSpriteOut._m0[_125] = 0u;
            uint _173 = _86 + (gl_GlobalInvocationID.z * (_71 * 524288u));
            uint _175 = (_173 + _73) >> 2u;
            uint _183;
            if (_110)
            {
                _183 = (g_fbramOut._m0[_175] & 4294967040u) | (_92 & 255u);
            }
            else
            {
                _183 = g_fbramOut._m0[_175];
            }
            uint _190;
            if (_111)
            {
                _190 = (_183 & 4294902015u) | ((_95 & 255u) << 8u);
            }
            else
            {
                _190 = _183;
            }
            uint _197;
            if (_112)
            {
                _197 = (_190 & 4278255615u) | ((_98 & 255u) << 16u);
            }
            else
            {
                _197 = _190;
            }
            uint _204;
            if (_113)
            {
                _204 = (_197 & 16777215u) | ((_101 & 255u) << 24u);
            }
            else
            {
                _204 = _197;
            }
            g_fbramOut._m0[_175] = _204;
            uint _208 = ((_173 + (_71 * 1048576u)) + _73) >> 2u;
            uint _220;
            if (_140)
            {
                uint _213 = g_fbramOut._m0[_208] & 4294967040u;
                uint _219;
                if (_129 > _103)
                {
                    _219 = _213 | (_118 & 255u);
                }
                else
                {
                    _219 = _213;
                }
                _220 = _219;
            }
            else
            {
                _220 = g_fbramOut._m0[_208];
            }
            uint _231;
            if (_145)
            {
                uint _223 = _220 & 4294902015u;
                uint _230;
                if (_131 > _105)
                {
                    _230 = _223 | ((_121 & 255u) << 8u);
                }
                else
                {
                    _230 = _223;
                }
                _231 = _230;
            }
            else
            {
                _231 = _220;
            }
            uint _242;
            if (_150)
            {
                uint _234 = _231 & 4278255615u;
                uint _241;
                if (_133 > _107)
                {
                    _241 = _234 | ((_124 & 255u) << 16u);
                }
                else
                {
                    _241 = _234;
                }
                _242 = _241;
            }
            else
            {
                _242 = _231;
            }
            uint _253;
            if (_155)
            {
                uint _245 = _242 & 16777215u;
                uint _252;
                if (_135 > _109)
                {
                    _252 = _245 | ((_127 & 255u) << 24u);
                }
                else
                {
                    _252 = _245;
                }
                _253 = _252;
            }
            else
            {
                _253 = _242;
            }
            g_fbramOut._m0[_208] = _253;
            break;
        } while(false);
    }
    else
    {
        do
        {
            uint _260 = _65.x;
            uint _262 = (gl_GlobalInvocationID.x * 2u) + (gl_GlobalInvocationID.y * _260);
            uint _264 = _65.y;
            uint _266 = _262 + ((gl_GlobalInvocationID.z * _260) * _264);
            uint _268 = g_internalSpriteOut._m0[_266];
            uint _269 = _266 + 1u;
            uint _271 = g_internalSpriteOut._m0[_269];
            uint _273 = (_268 >> 16u) & 65535u;
            uint _275 = (_271 >> 16u) & 65535u;
            bool _276 = _273 != 0u;
            bool _277 = _275 != 0u;
            uint _280 = _266 + ((_260 * _264) * 2u);
            uint _282 = g_internalSpriteOut._m0[_280];
            uint _283 = _280 + 1u;
            uint _285 = g_internalSpriteOut._m0[_283];
            uint _287 = (_282 >> 16u) & 65535u;
            uint _289 = (_285 >> 16u) & 65535u;
            bool _294;
            if (!_276)
            {
                _294 = _287 != 0u;
            }
            else
            {
                _294 = true;
            }
            bool _299;
            if (!_277)
            {
                _299 = _289 != 0u;
            }
            else
            {
                _299 = true;
            }
            bool _304;
            if (!_294)
            {
                _304 = !_299;
            }
            else
            {
                _304 = false;
            }
            if (_304)
            {
                break;
            }
            g_internalSpriteOut._m0[_266] = 0u;
            g_internalSpriteOut._m0[_269] = 0u;
            g_internalSpriteOut._m0[_280] = 0u;
            g_internalSpriteOut._m0[_283] = 0u;
            uint _310 = (_262 * 2u) + (gl_GlobalInvocationID.z * (_71 * 524288u));
            uint _312 = (_310 + _73) >> 2u;
            uint _324;
            if (_276)
            {
                _324 = (g_fbramOut._m0[_312] & 4294901760u) | (((_268 >> 8u) & 255u) | ((_268 << 8u) & 65280u));
            }
            else
            {
                _324 = g_fbramOut._m0[_312];
            }
            uint _335;
            if (_277)
            {
                _335 = (_324 & 65535u) | ((((_271 >> 8u) & 255u) | ((_271 << 8u) & 65280u)) << 16u);
            }
            else
            {
                _335 = _324;
            }
            g_fbramOut._m0[_312] = _335;
            uint _339 = ((_310 + (_71 * 1048576u)) + _73) >> 2u;
            uint _355;
            if (_294)
            {
                uint _344 = g_fbramOut._m0[_339] & 4294901760u;
                uint _354;
                if (_287 > _273)
                {
                    _354 = _344 | (((_282 >> 8u) & 255u) | ((_282 << 8u) & 65280u));
                }
                else
                {
                    _354 = _344;
                }
                _355 = _354;
            }
            else
            {
                _355 = g_fbramOut._m0[_339];
            }
            uint _370;
            if (_299)
            {
                uint _358 = _355 & 65535u;
                uint _369;
                if (_289 > _275)
                {
                    _369 = _358 | ((((_285 >> 8u) & 255u) | ((_285 << 8u) & 65280u)) << 16u);
                }
                else
                {
                    _369 = _358;
                }
                _370 = _369;
            }
            else
            {
                _370 = _355;
            }
            g_fbramOut._m0[_339] = _370;
            break;
        } while(false);
    }
}

