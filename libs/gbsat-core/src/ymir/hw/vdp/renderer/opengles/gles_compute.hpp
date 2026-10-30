#pragma once

#if defined(__ANDROID__)

#include <EGL/egl.h>
#include <GLES3/gl31.h>
#include <cmrc/cmrc.hpp>

#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>

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
    bool Upload(GLintptr offset, GLsizeiptr size, const void *data, std::string &error);
    void Reset();

    GLuint Handle() const { return m_handle; }
    GLsizeiptr Size() const { return m_size; }
    GLenum Target() const { return m_target; }

private:
    GLuint m_handle = 0;
    GLsizeiptr m_size = 0;
    GLenum m_target = 0;
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

class ComputeProgram {
public:
    ComputeProgram() = default;
    ComputeProgram(const ComputeProgram &) = delete;
    ComputeProgram &operator=(const ComputeProgram &) = delete;
    ComputeProgram(ComputeProgram &&other) noexcept;
    ComputeProgram &operator=(ComputeProgram &&other) noexcept;
    ~ComputeProgram();

    bool Load(std::string_view resourcePath, std::string_view label, std::string &error);
    void Reset();
    void Use() const { glUseProgram(m_handle); }
    GLuint Handle() const { return m_handle; }

private:
    GLuint m_handle = 0;
};

void MemoryBarrier(GLbitfield barriers = GL_ALL_BARRIER_BITS);

} // namespace ymir::vdp::gles

#endif // defined(__ANDROID__)
