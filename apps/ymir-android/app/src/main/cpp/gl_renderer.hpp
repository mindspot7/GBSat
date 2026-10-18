#pragma once

#include <android/native_window_jni.h>
#include <cstdint>

// GPU presentation path: takes the software-rendered Saturn frame, uploads it as a texture and draws it with a
// selectable scaling filter and post-process shader on a dedicated EGL/OpenGL ES 3 thread.
namespace glr {

// Takes ownership of one reference to `window` (releases it when replaced). Pass nullptr to detach.
void SetWindow(ANativeWindow *window);
// Latest-frame-wins; copies the pixels (RGBA8888).
void Submit(const uint32_t *fb, uint32_t width, uint32_t height);
// filter: 0 nearest, 1 bilinear, 2 sharp bilinear.
// shader: 0 none, 1 scanlines, 2 CRT, 3 LCD3x, 4 dot matrix.
void SetOptions(int filter, int shader);
// Selects the driver for the next start: false = system EGL/GLES, true = ANGLE (OpenGL ES on Vulkan). Restarts the GL thread.
void SetBackend(bool angle);
void Shutdown();

} // namespace glr
