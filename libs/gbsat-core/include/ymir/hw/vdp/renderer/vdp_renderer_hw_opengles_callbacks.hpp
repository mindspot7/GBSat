#pragma once

#include <ymir/util/callback.hpp>
#include <ymir/core/types.hpp>

namespace ymir::vdp {

/// EGL objects owned by the Android frontend. The context is GLES 3.1+ and shares objects with its presenter.
struct OpenGLESRendererContext {
    void *display = nullptr; // EGLDisplay
    void *context = nullptr; // EGLContext
    void *surface = nullptr; // 1x1 EGLSurface
    void (*progress)(const char *stage) = nullptr;
    uint32 resolutionScale = 1; // internal VDP scale; 1..4
};

using CBOpenGLESFrameTargetAcquireCallback = util::OptionalCallback<void *(uint32 width, uint32 height, uint64 *token)>;
using CBOpenGLESFrameTargetReadyCallback =
    util::OptionalCallback<void(void *buffer, uint64 token, uint32 width, uint32 height, uint32 scale, void *fence)>;
using CBOpenGLESFrameTargetCancelCallback = util::OptionalCallback<void(uint64 token)>;
using CBOpenGLESFrameReadyCallback = util::OptionalCallback<void(const uint32 *pixels, uint32 width, uint32 height)>;

struct OpenGLESRendererCallbacks {
    CBOpenGLESFrameReadyCallback FrameReady;
    CBOpenGLESFrameTargetAcquireCallback FrameTargetAcquire;
    CBOpenGLESFrameTargetReadyCallback FrameTargetReady;
    CBOpenGLESFrameTargetCancelCallback FrameTargetCancel;
};

} // namespace ymir::vdp
