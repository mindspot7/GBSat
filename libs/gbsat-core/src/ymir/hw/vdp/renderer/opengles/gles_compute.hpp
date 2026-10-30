#pragma once

#if defined(__ANDROID__)

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl31.h>
#include <GLES2/gl2ext.h>
#include <android/hardware_buffer.h>
#include <cmrc/cmrc.hpp>
#include <ymir/hw/vdp/renderer/vulkan/spirv_reflect.hpp>

#include <cstddef>
#include <cstdint>
#include <initializer_list>
#include <string>
#include <string_view>
#include <vector>

#ifndef GL_TEXTURE_BUFFER
#define GL_TEXTURE_BUFFER 0x8C2A
#endif

namespace ymir::vdp::gles {

// Small RAII wrappers for the OpenGL ES compute backend. These objects are created and destroyed
// while the renderer's shared EGL context is current.
class Buffer {
public:
    Buffer() = default;
    Buffer(const Buffer &) = delete;
    Buffer &operator=(const Buffer &) = delete;
    Buffer(Buffer &&other) noexcept;
    Buffer &operator=(Buffer &&other) noexcept;
    ~Buffer();

    bool Create(GLenum target, GLsizeiptr size, const void *data, GLenum usage, std::string &error);
    bool CreateTextureView(GLenum internalFormat, std::string &error);
    bool Upload(GLintptr offset, GLsizeiptr size, const void *data, std::string &error);
    bool Readback(GLintptr offset, GLsizeiptr size, void *data, std::string &error);
    void Reset();

    GLuint Handle() const { return m_handle; }
    GLsizeiptr Size() const { return m_size; }
    GLenum Target() const { return m_target; }
    GLuint TextureView() const { return m_textureView; }

private:
    GLuint m_handle = 0;
    GLsizeiptr m_size = 0;
    GLenum m_target = 0;
    GLuint m_textureView = 0;
};

class Image {
public:
    Image() = default;
    Image(const Image &) = delete;
    Image &operator=(const Image &) = delete;
    Image(Image &&other) noexcept;
    Image &operator=(Image &&other) noexcept;
    ~Image();

    bool Create2D(GLenum internalFormat, uint32_t width, uint32_t height, std::string &error);
    bool Create2DArray(GLenum internalFormat, uint32_t width, uint32_t height, uint32_t layers, std::string &error);
    void Reset();

    GLuint Handle() const { return m_handle; }
    GLenum Target() const { return m_target; }
    GLenum InternalFormat() const { return m_internalFormat; }
    uint32_t Width() const { return m_width; }
    uint32_t Height() const { return m_height; }
    uint32_t Layers() const { return m_layers; }

private:
    bool Create(GLenum target, GLenum internalFormat, uint32_t width, uint32_t height, uint32_t layers,
                std::string &error);

    GLuint m_handle = 0;
    GLenum m_target = 0;
    GLenum m_internalFormat = 0;
    uint32_t m_width = 0;
    uint32_t m_height = 0;
    uint32_t m_layers = 0;
};

class AndroidFrameTarget {
public:
    AndroidFrameTarget() = default;
    AndroidFrameTarget(const AndroidFrameTarget &) = delete;
    AndroidFrameTarget &operator=(const AndroidFrameTarget &) = delete;
    AndroidFrameTarget(AndroidFrameTarget &&other) noexcept;
    AndroidFrameTarget &operator=(AndroidFrameTarget &&other) noexcept;
    ~AndroidFrameTarget();

    bool Import(EGLDisplay display, AHardwareBuffer *buffer, uint64_t token, std::string &error);
    bool CopyFrom(const Image &source, uint32_t width, uint32_t height, std::string &error);
    GLsync CreateFence();
    void Reset();

    uint64_t Token() const { return m_token; }
    GLuint Texture() const { return m_texture; }
    GLuint Framebuffer() const { return m_framebuffer; }

private:
    EGLDisplay m_display = EGL_NO_DISPLAY;
    EGLImageKHR m_image = EGL_NO_IMAGE_KHR;
    GLuint m_texture = 0;
    GLuint m_framebuffer = 0;
    GLuint m_readFramebuffer = 0;
    uint64_t m_token = 0;
    uint32_t m_width = 0;
    uint32_t m_height = 0;
};

struct ResourceRef {
    const Buffer *buffer = nullptr;
    const Image *image = nullptr;

    ResourceRef(const Buffer &resource)
        : buffer(&resource) {}
    ResourceRef(const Image &resource)
        : image(&resource) {}
};

struct DescriptorSet {
    std::vector<ResourceRef> srvs;
    std::vector<ResourceRef> uavs;
};

bool CreateDescriptorSet(const std::vector<vulkan::SpvBinding> &bindings, std::initializer_list<ResourceRef> srvs,
                         std::initializer_list<ResourceRef> uavs, DescriptorSet &out, std::string &error);

class ComputeProgram {
public:
    ComputeProgram() = default;
    ComputeProgram(const ComputeProgram &) = delete;
    ComputeProgram &operator=(const ComputeProgram &) = delete;
    ComputeProgram(ComputeProgram &&other) noexcept;
    ComputeProgram &operator=(ComputeProgram &&other) noexcept;
    ~ComputeProgram();

    bool Load(std::string_view glesResourcePath, std::string_view spirvResourcePath, std::string_view label,
              std::string &error);
    void Reset();
    void Use() const { glUseProgram(m_handle); }
    GLuint Handle() const { return m_handle; }
    const std::vector<vulkan::SpvBinding> &Bindings() const { return m_bindings; }
    bool Dispatch(const DescriptorSet &set, const Buffer &constantsBuffer, GLintptr constantsOffset,
                  GLsizeiptr constantsSize, uint32_t groupsX, uint32_t groupsY, uint32_t groupsZ,
                  std::string &error) const;

private:
    GLuint m_handle = 0;
    std::vector<vulkan::SpvBinding> m_bindings;
};

/// Small compute helper for clearing storage-buffer ranges without constructing a CPU-side fill buffer.
class BufferFillKernel {
public:
    BufferFillKernel() = default;
    BufferFillKernel(const BufferFillKernel &) = delete;
    BufferFillKernel &operator=(const BufferFillKernel &) = delete;
    BufferFillKernel(BufferFillKernel &&other) noexcept;
    BufferFillKernel &operator=(BufferFillKernel &&other) noexcept;
    ~BufferFillKernel();

    bool Initialize(std::string &error);
    bool Fill(const Buffer &buffer, GLintptr offset, GLsizeiptr size, uint32_t value, std::string &error) const;
    void Reset();

private:
    GLuint m_program = 0;
    GLint m_wordOffsetLocation = -1;
    GLint m_wordCountLocation = -1;
    GLint m_valueLocation = -1;
};

void MemoryBarrier(GLbitfield barriers = GL_ALL_BARRIER_BITS);

} // namespace ymir::vdp::gles

#endif // defined(__ANDROID__)
