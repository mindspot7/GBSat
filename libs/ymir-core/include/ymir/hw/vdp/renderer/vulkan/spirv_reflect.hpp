#pragma once

/**
@file
@brief Minimal SPIR-V reflection: extracts descriptor bindings from a compute shader module so the Vulkan VDP renderer
can build descriptor set layouts directly from the compiled shaders instead of duplicating the D3D12 root signatures.
Header-only, no dependencies beyond the standard library and the Vulkan descriptor type enum values.
*/

#include <algorithm>
#include <cstdint>
#include <unordered_map>
#include <vector>

namespace ymir::vdp::vulkan {

/// Values match VkDescriptorType.
enum class SpvDescriptorKind : uint32_t {
    SampledImage = 2,
    StorageImage = 3,
    UniformTexelBuffer = 4,
    StorageTexelBuffer = 5,
    UniformBuffer = 6,
    StorageBuffer = 7,
};

struct SpvBinding {
    uint32_t set = 0;
    uint32_t binding = 0;
    SpvDescriptorKind kind = SpvDescriptorKind::StorageBuffer;
};

struct SpvReflection {
    std::vector<SpvBinding> bindings; ///< sorted by (set, binding)
    uint32_t localSize[3] = {1, 1, 1};
    bool valid = false;
};

inline SpvReflection ReflectSpirv(const uint32_t *code, size_t wordCount) {
    SpvReflection out{};
    if (wordCount < 5 || code[0] != 0x07230203u) {
        return out;
    }

    struct TypeInfo {
        uint32_t op = 0;
        uint32_t a = 0;       // pointee / element / dim
        uint32_t b = 0;       // sampled (images)
        bool block = false;   // struct decorated Block
        bool bufBlock = false;
    };
    std::unordered_map<uint32_t, TypeInfo> types;
    std::unordered_map<uint32_t, uint32_t> setOf, bindingOf;
    struct Var {
        uint32_t id, type, storage;
    };
    std::vector<Var> vars;

    size_t i = 5;
    while (i < wordCount) {
        const uint32_t w = code[i];
        const uint32_t len = w >> 16;
        const uint32_t op = w & 0xFFFF;
        if (len == 0 || i + len > wordCount) {
            return out;
        }
        const uint32_t *p = &code[i + 1];
        switch (op) {
        case 16: // OpExecutionMode: entry, mode, ...
            if (len >= 6 && p[1] == 17) { // LocalSize
                out.localSize[0] = p[2];
                out.localSize[1] = p[3];
                out.localSize[2] = p[4];
            }
            break;
        case 71: // OpDecorate: target, decoration, [value]
            if (len >= 3) {
                if (p[1] == 2) types[p[0]].block = true;
                else if (p[1] == 3) types[p[0]].bufBlock = true;
            }
            if (len >= 4) {
                if (p[1] == 34) setOf[p[0]] = p[2];
                else if (p[1] == 33) bindingOf[p[0]] = p[2];
            }
            break;
        case 25: // OpTypeImage: result, sampled type, dim, depth, arrayed, ms, sampled, format
            if (len >= 9) {
                auto &t = types[p[0]];
                t.op = op;
                t.a = p[2];
                t.b = p[6];
            }
            break;
        case 27: // OpTypeSampledImage: result, image type
            types[p[0]].op = op;
            types[p[0]].a = p[1];
            break;
        case 28: // OpTypeArray: result, element, length
        case 29: // OpTypeRuntimeArray: result, element
            types[p[0]].op = op;
            types[p[0]].a = p[1];
            break;
        case 30: // OpTypeStruct
            types[p[0]].op = op;
            break;
        case 32: // OpTypePointer: result, storage, type
            types[p[0]].op = op;
            types[p[0]].a = p[2];
            break;
        case 59: // OpVariable: type, result, storage
            vars.push_back({p[1], p[0], p[2]});
            break;
        default:
            break;
        }
        i += len;
    }

    for (const Var &v : vars) {
        if (v.storage != 0 && v.storage != 2 && v.storage != 12) {
            continue; // UniformConstant, Uniform, StorageBuffer only
        }
        if (!setOf.count(v.id) && !bindingOf.count(v.id)) {
            continue;
        }
        uint32_t t = types[v.type].a; // pointee
        while (types[t].op == 28 || types[t].op == 29) {
            t = types[t].a;
        }
        const TypeInfo &ti = types[t];
        SpvBinding b{};
        b.set = setOf[v.id];
        b.binding = bindingOf[v.id];
        if (ti.op == 25) {
            const bool storage = ti.b == 2;
            const bool buffer = ti.a == 5; // Dim::Buffer
            if (buffer) {
                b.kind = storage ? SpvDescriptorKind::StorageTexelBuffer : SpvDescriptorKind::UniformTexelBuffer;
            } else {
                b.kind = storage ? SpvDescriptorKind::StorageImage : SpvDescriptorKind::SampledImage;
            }
        } else if (ti.op == 30) {
            b.kind = (v.storage == 2 && !ti.bufBlock) ? SpvDescriptorKind::UniformBuffer
                                                       : SpvDescriptorKind::StorageBuffer;
            if (v.storage == 12) b.kind = SpvDescriptorKind::StorageBuffer;
        } else {
            continue;
        }
        out.bindings.push_back(b);
    }
    std::sort(out.bindings.begin(), out.bindings.end(), [](const SpvBinding &a, const SpvBinding &b) {
        return a.set != b.set ? a.set < b.set : a.binding < b.binding;
    });
    out.valid = true;
    return out;
}

} // namespace ymir::vdp::vulkan
