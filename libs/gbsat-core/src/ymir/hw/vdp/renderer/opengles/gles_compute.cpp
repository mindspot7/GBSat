#include "gles_compute.hpp"

#if defined(__ANDROID__)

#include <fmt/format.h>

#include <algorithm>
#include <limits>
#include <utility>
#include <vector>

CMRC_DECLARE(ymir_core_gles_shaders);

namespace ymir::vdp::gles {
namespace {

cmrc::embedded_filesystem g_glesShaderFS = cmrc::ymir_core_gles_shaders::get_filesystem();

std::string GetShaderLog(GLuint shader) {
    GLint length = 0;
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
    if (length <= 1) {
        return {};
    }
    std::string log(static_cast<size_t>(length), '\0');
    GLsizei written = 0;
    glGetShaderInfoLog(shader, length, &written, log.data());
    log.resize(static_cast<size_t>(std::max<GLsizei>(written, 0)));
    return log;
}

std::string GetProgramLog(GLuint program) {
    GLint length = 0;
    glGetProgramiv(program, GL_INFO_LOG_LENGTH, &length);
    if (length <= 1) {
        return {};
    }
    std::string log(static_cast<size_t>(length), '\0');
    GLsizei written = 0;
    glGetProgramInfoLog(program, length, &written, log.data());
    log.resize(static_cast<size_t>(std::max<GLsizei>(written, 0)));
    return log;
}

bool CheckGl(std::string_view operation, std::string &error) {
    const GLenum result = glGetError();
    if (result == GL_NO_ERROR) {
        return true;
    }
    error = fmt::format("{} failed with GLES error 0x{:04X}", operation, result);
    while (glGetError() != GL_NO_ERROR) {
    }
    return false;
}

} // namespace

Buffer::Buffer(Buffer &&other) noexcept
    : m_handle(std::exchange(other.m_handle, 0))
    , m_size(std::exchange(other.m_size, 0))
    , m_target(std::exchange(other.m_target, 0)) {}

Buffer &Buffer::operator=(Buffer &&other) noexcept {
    if (this != &other) {
        Reset();
        m_handle = std::exchange(other.m_handle, 0);
        m_size = std::exchange(other.m_size, 0);
        m_target = std::exchange(other.m_target, 0);
    }
    return *this;
}

Buffer::~Buffer() {
    Reset();
}

bool Buffer::Create(GLenum target, GLsizeiptr size, const void *data, GLenum usage, std::string &error) {
    Reset();
    if (size <= 0) {
        error = "Cannot create an empty GLES buffer";
        return false;
    }
    glGenBuffers(1, &m_handle);
    glBindBuffer(target, m_handle);
    glBufferData(target, size, data, usage);
    if (!CheckGl("buffer allocation", error)) {
        Reset();
        return false;
    }
    m_size = size;
    m_target = target;
    return true;
}

bool Buffer::Upload(GLintptr offset, GLsizeiptr size, const void *data, std::string &error) {
    if (m_handle == 0 || offset < 0 || size < 0 || offset > m_size || size > m_size - offset) {
        error = "GLES buffer upload is outside the allocated range";
        return false;
    }
    if (size == 0) {
        return true;
    }
    glBindBuffer(m_target, m_handle);
    glBufferSubData(m_target, offset, size, data);
    return CheckGl("buffer upload", error);
}

void Buffer::Reset() {
    if (m_handle != 0) {
        glDeleteBuffers(1, &m_handle);
    }
    m_handle = 0;
    m_size = 0;
    m_target = 0;
}

Image::Image(Image &&other) noexcept
    : m_handle(std::exchange(other.m_handle, 0))
    , m_target(std::exchange(other.m_target, 0))
    , m_internalFormat(std::exchange(other.m_internalFormat, 0))
    , m_width(std::exchange(other.m_width, 0))
    , m_height(std::exchange(other.m_height, 0))
    , m_layers(std::exchange(other.m_layers, 0)) {}

Image &Image::operator=(Image &&other) noexcept {
    if (this != &other) {
        Reset();
        m_handle = std::exchange(other.m_handle, 0);
        m_target = std::exchange(other.m_target, 0);
        m_internalFormat = std::exchange(other.m_internalFormat, 0);
        m_width = std::exchange(other.m_width, 0);
        m_height = std::exchange(other.m_height, 0);
        m_layers = std::exchange(other.m_layers, 0);
    }
    return *this;
}

Image::~Image() {
    Reset();
}

bool Image::Create2D(GLenum internalFormat, uint32_t width, uint32_t height, std::string &error) {
    return Create(GL_TEXTURE_2D, internalFormat, width, height, 1, error);
}

bool Image::Create2DArray(GLenum internalFormat, uint32_t width, uint32_t height, uint32_t layers,
                          std::string &error) {
    return Create(GL_TEXTURE_2D_ARRAY, internalFormat, width, height, layers, error);
}

bool Image::Create(GLenum target, GLenum internalFormat, uint32_t width, uint32_t height, uint32_t layers,
                   std::string &error) {
    Reset();
    if (width == 0 || height == 0 || layers == 0 || width > static_cast<uint32_t>(std::numeric_limits<GLsizei>::max()) ||
        height > static_cast<uint32_t>(std::numeric_limits<GLsizei>::max()) ||
        layers > static_cast<uint32_t>(std::numeric_limits<GLsizei>::max())) {
        error = "Invalid GLES image dimensions";
        return false;
    }
    glGenTextures(1, &m_handle);
    glBindTexture(target, m_handle);
    if (target == GL_TEXTURE_2D_ARRAY) {
        glTexStorage3D(target, 1, internalFormat, static_cast<GLsizei>(width), static_cast<GLsizei>(height),
                       static_cast<GLsizei>(layers));
    } else {
        glTexStorage2D(target, 1, internalFormat, static_cast<GLsizei>(width), static_cast<GLsizei>(height));
    }
    if (!CheckGl("image allocation", error)) {
        Reset();
        return false;
    }
    m_target = target;
    m_internalFormat = internalFormat;
    m_width = width;
    m_height = height;
    m_layers = layers;
    return true;
}

void Image::Reset() {
    if (m_handle != 0) {
        glDeleteTextures(1, &m_handle);
    }
    m_handle = 0;
    m_target = 0;
    m_internalFormat = 0;
    m_width = 0;
    m_height = 0;
    m_layers = 0;
}

ComputeProgram::ComputeProgram(ComputeProgram &&other) noexcept
    : m_handle(std::exchange(other.m_handle, 0)) {}

ComputeProgram &ComputeProgram::operator=(ComputeProgram &&other) noexcept {
    if (this != &other) {
        Reset();
        m_handle = std::exchange(other.m_handle, 0);
    }
    return *this;
}

ComputeProgram::~ComputeProgram() {
    Reset();
}

bool ComputeProgram::Load(std::string_view resourcePath, std::string_view label, std::string &error) {
    Reset();
    const std::string path(resourcePath);
    if (!g_glesShaderFS.is_file(path)) {
        error = fmt::format("Embedded GLES shader not found: {}", resourcePath);
        return false;
    }

    const auto file = g_glesShaderFS.open(path);
    const std::string source(file.begin(), file.end());
    if (source.empty()) {
        error = fmt::format("Embedded GLES shader is empty: {}", resourcePath);
        return false;
    }

    const GLuint shader = glCreateShader(GL_COMPUTE_SHADER);
    const GLchar *sourcePtr = source.c_str();
    const GLint sourceLength = static_cast<GLint>(source.size());
    glShaderSource(shader, 1, &sourcePtr, &sourceLength);
    glCompileShader(shader);

    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    const std::string compileLog = GetShaderLog(shader);
    if (compiled != GL_TRUE) {
        error = fmt::format("GLES compute shader '{}' failed to compile: {}", label, compileLog);
        glDeleteShader(shader);
        return false;
    }

    m_handle = glCreateProgram();
    glAttachShader(m_handle, shader);
    glLinkProgram(m_handle);
    glDeleteShader(shader);

    GLint linked = GL_FALSE;
    glGetProgramiv(m_handle, GL_LINK_STATUS, &linked);
    if (linked != GL_TRUE) {
        error = fmt::format("GLES compute shader '{}' failed to link: {}", label, GetProgramLog(m_handle));
        Reset();
        return false;
    }
    return true;
}

void ComputeProgram::Reset() {
    if (m_handle != 0) {
        glDeleteProgram(m_handle);
    }
    m_handle = 0;
}

void MemoryBarrier(GLbitfield barriers) {
    glMemoryBarrier(barriers);
}

} // namespace ymir::vdp::gles

#endif // defined(__ANDROID__)
