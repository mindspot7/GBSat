// Generated: function-pointer table so the renderer can run on either the system EGL/GLES driver or ANGLE.
#pragma once
#include <EGL/egl.h>
#include <GLES3/gl3.h>

namespace gld {
extern decltype(&::eglChooseConfig) p_eglChooseConfig;
extern decltype(&::eglCreateContext) p_eglCreateContext;
extern decltype(&::eglCreateWindowSurface) p_eglCreateWindowSurface;
extern decltype(&::eglDestroyContext) p_eglDestroyContext;
extern decltype(&::eglDestroySurface) p_eglDestroySurface;
extern decltype(&::eglGetDisplay) p_eglGetDisplay;
extern decltype(&::eglInitialize) p_eglInitialize;
extern decltype(&::eglMakeCurrent) p_eglMakeCurrent;
extern decltype(&::eglQuerySurface) p_eglQuerySurface;
extern decltype(&::eglSwapBuffers) p_eglSwapBuffers;
extern decltype(&::eglSwapInterval) p_eglSwapInterval;
extern decltype(&::eglTerminate) p_eglTerminate;
extern decltype(&::glActiveTexture) p_glActiveTexture;
extern decltype(&::glAttachShader) p_glAttachShader;
extern decltype(&::glBindTexture) p_glBindTexture;
extern decltype(&::glBindVertexArray) p_glBindVertexArray;
extern decltype(&::glClear) p_glClear;
extern decltype(&::glClearColor) p_glClearColor;
extern decltype(&::glCompileShader) p_glCompileShader;
extern decltype(&::glCreateProgram) p_glCreateProgram;
extern decltype(&::glCreateShader) p_glCreateShader;
extern decltype(&::glDrawArrays) p_glDrawArrays;
extern decltype(&::glGenTextures) p_glGenTextures;
extern decltype(&::glGenVertexArrays) p_glGenVertexArrays;
extern decltype(&::glGetShaderInfoLog) p_glGetShaderInfoLog;
extern decltype(&::glGetShaderiv) p_glGetShaderiv;
extern decltype(&::glGetUniformLocation) p_glGetUniformLocation;
extern decltype(&::glLinkProgram) p_glLinkProgram;
extern decltype(&::glPixelStorei) p_glPixelStorei;
extern decltype(&::glShaderSource) p_glShaderSource;
extern decltype(&::glTexImage2D) p_glTexImage2D;
extern decltype(&::glTexParameteri) p_glTexParameteri;
extern decltype(&::glTexSubImage2D) p_glTexSubImage2D;
extern decltype(&::glUniform1i) p_glUniform1i;
extern decltype(&::glUniform2f) p_glUniform2f;
extern decltype(&::glUseProgram) p_glUseProgram;
extern decltype(&::glViewport) p_glViewport;

// Loads the driver: ANGLE (libEGL_angle.so / libGLESv2_angle.so) or the system one. Returns false on failure.
bool Load(bool angle);
// Creates the EGL display (ANGLE Vulkan platform when loaded with angle=true).
EGLDisplay GetDisplay();
bool IsAngle();
} // namespace gld

#define eglChooseConfig gld::p_eglChooseConfig
#define eglCreateContext gld::p_eglCreateContext
#define eglCreateWindowSurface gld::p_eglCreateWindowSurface
#define eglDestroyContext gld::p_eglDestroyContext
#define eglDestroySurface gld::p_eglDestroySurface
#define eglGetDisplay gld::p_eglGetDisplay
#define eglInitialize gld::p_eglInitialize
#define eglMakeCurrent gld::p_eglMakeCurrent
#define eglQuerySurface gld::p_eglQuerySurface
#define eglSwapBuffers gld::p_eglSwapBuffers
#define eglSwapInterval gld::p_eglSwapInterval
#define eglTerminate gld::p_eglTerminate
#define glActiveTexture gld::p_glActiveTexture
#define glAttachShader gld::p_glAttachShader
#define glBindTexture gld::p_glBindTexture
#define glBindVertexArray gld::p_glBindVertexArray
#define glClear gld::p_glClear
#define glClearColor gld::p_glClearColor
#define glCompileShader gld::p_glCompileShader
#define glCreateProgram gld::p_glCreateProgram
#define glCreateShader gld::p_glCreateShader
#define glDrawArrays gld::p_glDrawArrays
#define glGenTextures gld::p_glGenTextures
#define glGenVertexArrays gld::p_glGenVertexArrays
#define glGetShaderInfoLog gld::p_glGetShaderInfoLog
#define glGetShaderiv gld::p_glGetShaderiv
#define glGetUniformLocation gld::p_glGetUniformLocation
#define glLinkProgram gld::p_glLinkProgram
#define glPixelStorei gld::p_glPixelStorei
#define glShaderSource gld::p_glShaderSource
#define glTexImage2D gld::p_glTexImage2D
#define glTexParameteri gld::p_glTexParameteri
#define glTexSubImage2D gld::p_glTexSubImage2D
#define glUniform1i gld::p_glUniform1i
#define glUniform2f gld::p_glUniform2f
#define glUseProgram gld::p_glUseProgram
#define glViewport gld::p_glViewport
