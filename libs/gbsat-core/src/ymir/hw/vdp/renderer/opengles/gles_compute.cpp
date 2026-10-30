#include "gles_compute.hpp"

#if defined(__ANDROID__)

#include <fmt/format.h>

#include <algorithm>
#include <cstring>
#include <limits>
#include <utility>
#include <vector>

#include <ymir/hw/vdp/vdp2_defs.hpp>
#include "../common_hw/common_hw_defs.hpp"

CMRC_DECLARE(ymir_core_gles_shaders);

namespace ymir::vdp::gles {
namespace {

cmrc::embedded_filesystem g_glesShaderFS = cmrc::ymir_core_gles_shaders::get_filesystem();

using GlesTexBufferProc = void (*)(GLenum target, GLenum internalFormat, GLuint buffer);

GlesTexBufferProc GetTexBufferProc() {
    static const auto proc = reinterpret_cast<GlesTexBufferProc>(eglGetProcAddress("glTexBufferEXT"));
    return proc;
}

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
    , m_target(std::exchange(other.m_target, 0))
    , m_textureView(std::exchange(other.m_textureView, 0)) {}

Buffer &Buffer::operator=(Buffer &&other) noexcept {
    if (this != &other) {
        Reset();
        m_handle = std::exchange(other.m_handle, 0);
        m_size = std::exchange(other.m_size, 0);
        m_target = std::exchange(other.m_target, 0);
        m_textureView = std::exchange(other.m_textureView, 0);
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

bool Buffer::CreateTextureView(GLenum internalFormat, std::string &error) {
    if (m_handle == 0) {
        error = "Cannot create a texture view for an uninitialized GLES buffer";
        return false;
    }
    const GlesTexBufferProc texBuffer = GetTexBufferProc();
    if (texBuffer == nullptr) {
        error = "GL_EXT_texture_buffer is unavailable";
        return false;
    }
    if (m_textureView != 0) {
        glDeleteTextures(1, &m_textureView);
        m_textureView = 0;
    }
    glGenTextures(1, &m_textureView);
    glBindTexture(GL_TEXTURE_BUFFER, m_textureView);
    texBuffer(GL_TEXTURE_BUFFER, internalFormat, m_handle);
    if (!CheckGl("texture buffer view creation", error)) {
        glDeleteTextures(1, &m_textureView);
        m_textureView = 0;
        return false;
    }
    return true;
}

void Buffer::Reset() {
    if (m_textureView != 0) {
        glDeleteTextures(1, &m_textureView);
    }
    if (m_handle != 0) {
        glDeleteBuffers(1, &m_handle);
    }
    m_handle = 0;
    m_size = 0;
    m_target = 0;
    m_textureView = 0;
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

AndroidFrameTarget::AndroidFrameTarget(AndroidFrameTarget &&other) noexcept
    : m_display(std::exchange(other.m_display, EGL_NO_DISPLAY))
    , m_image(std::exchange(other.m_image, EGL_NO_IMAGE_KHR))
    , m_texture(std::exchange(other.m_texture, 0))
    , m_framebuffer(std::exchange(other.m_framebuffer, 0))
    , m_readFramebuffer(std::exchange(other.m_readFramebuffer, 0))
    , m_token(std::exchange(other.m_token, 0))
    , m_width(std::exchange(other.m_width, 0))
    , m_height(std::exchange(other.m_height, 0)) {}

AndroidFrameTarget &AndroidFrameTarget::operator=(AndroidFrameTarget &&other) noexcept {
    if (this != &other) {
        Reset();
        m_display = std::exchange(other.m_display, EGL_NO_DISPLAY);
        m_image = std::exchange(other.m_image, EGL_NO_IMAGE_KHR);
        m_texture = std::exchange(other.m_texture, 0);
        m_framebuffer = std::exchange(other.m_framebuffer, 0);
        m_readFramebuffer = std::exchange(other.m_readFramebuffer, 0);
        m_token = std::exchange(other.m_token, 0);
        m_width = std::exchange(other.m_width, 0);
        m_height = std::exchange(other.m_height, 0);
    }
    return *this;
}

AndroidFrameTarget::~AndroidFrameTarget() {
    Reset();
}

bool AndroidFrameTarget::Import(EGLDisplay display, AHardwareBuffer *buffer, uint64_t token, std::string &error) {
    if (display == EGL_NO_DISPLAY || buffer == nullptr) {
        error = "Cannot import an empty Android frame target";
        return false;
    }
    if (m_display == display && m_token == token && m_image != EGL_NO_IMAGE_KHR) {
        return true;
    }
    Reset();

    AHardwareBuffer_Desc desc{};
    AHardwareBuffer_describe(buffer, &desc);
    if (desc.width == 0 || desc.height == 0 || desc.layers != 1 ||
        desc.format != AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM) {
        error = fmt::format("Unsupported Android frame target ({}x{}, layers {}, format {})", desc.width, desc.height,
                            desc.layers, desc.format);
        return false;
    }

    const auto getNativeClientBuffer =
        reinterpret_cast<PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC>(eglGetProcAddress("eglGetNativeClientBufferANDROID"));
    const auto createImage = reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(eglGetProcAddress("eglCreateImageKHR"));
    const auto destroyImage = reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(eglGetProcAddress("eglDestroyImageKHR"));
    const auto imageTargetTexture = reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
        eglGetProcAddress("glEGLImageTargetTexture2DOES"));
    if (getNativeClientBuffer == nullptr || createImage == nullptr || destroyImage == nullptr ||
        imageTargetTexture == nullptr) {
        error = "EGL_ANDROID_get_native_client_buffer or EGL_KHR_image_base is unavailable";
        return false;
    }

    const EGLint attrs[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
    const EGLClientBuffer clientBuffer = getNativeClientBuffer(buffer);
    if (clientBuffer == nullptr) {
        error = fmt::format("eglGetNativeClientBufferANDROID failed (EGL error 0x{:04X})", eglGetError());
        return false;
    }
    m_image = createImage(display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer, attrs);
    if (m_image == EGL_NO_IMAGE_KHR) {
        error = fmt::format("eglCreateImageKHR for AHardwareBuffer failed (EGL error 0x{:04X})", eglGetError());
        return false;
    }

    m_display = display;
    m_token = token;
    m_width = desc.width;
    m_height = desc.height;
    glGenTextures(1, &m_texture);
    glBindTexture(GL_TEXTURE_2D, m_texture);
    imageTargetTexture(GL_TEXTURE_2D, m_image);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glGenFramebuffers(1, &m_framebuffer);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, m_framebuffer);
    glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, m_texture, 0);
    const GLenum status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE || !CheckGl("AHardwareBuffer EGLImage framebuffer", error)) {
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            error = fmt::format("AHardwareBuffer framebuffer is incomplete (0x{:04X})", status);
        }
        Reset();
        return false;
    }
    glGenFramebuffers(1, &m_readFramebuffer);
    if (!CheckGl("AHardwareBuffer EGLImage import", error)) {
        Reset();
        return false;
    }
    return true;
}

bool AndroidFrameTarget::CopyFrom(const Image &source, uint32_t width, uint32_t height, std::string &error) {
    if (m_framebuffer == 0 || m_readFramebuffer == 0 || source.Handle() == 0 || source.Target() != GL_TEXTURE_2D ||
        width == 0 || height == 0 || width > source.Width() || height > source.Height() || width > m_width ||
        height > m_height) {
        error = "Invalid GLES frame copy source or target dimensions";
        return false;
    }
    glBindFramebuffer(GL_READ_FRAMEBUFFER, m_readFramebuffer);
    glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, source.Handle(), 0);
    if (glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        error = "GLES VDP source image is not framebuffer complete";
        return false;
    }
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, m_framebuffer);
    glBlitFramebuffer(0, 0, static_cast<GLint>(width), static_cast<GLint>(height), 0, 0,
                      static_cast<GLint>(width), static_cast<GLint>(height), GL_COLOR_BUFFER_BIT, GL_NEAREST);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    return CheckGl("GLES VDP frame copy to AHardwareBuffer", error);
}

GLsync AndroidFrameTarget::CreateFence() {
    GLsync fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    glFlush();
    return fence;
}

void AndroidFrameTarget::Reset() {
    if (m_readFramebuffer != 0) {
        glDeleteFramebuffers(1, &m_readFramebuffer);
    }
    if (m_framebuffer != 0) {
        glDeleteFramebuffers(1, &m_framebuffer);
    }
    if (m_texture != 0) {
        glDeleteTextures(1, &m_texture);
    }
    if (m_image != EGL_NO_IMAGE_KHR) {
        const auto destroyImage =
            reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(eglGetProcAddress("eglDestroyImageKHR"));
        if (destroyImage != nullptr && m_display != EGL_NO_DISPLAY) {
            destroyImage(m_display, m_image);
        }
    }
    m_display = EGL_NO_DISPLAY;
    m_image = EGL_NO_IMAGE_KHR;
    m_texture = 0;
    m_framebuffer = 0;
    m_readFramebuffer = 0;
    m_token = 0;
    m_width = 0;
    m_height = 0;
}

bool CreateDescriptorSet(const std::vector<vulkan::SpvBinding> &bindings, std::initializer_list<ResourceRef> srvs,
                         std::initializer_list<ResourceRef> uavs, DescriptorSet &out, std::string &error) {
    out.srvs.assign(srvs.begin(), srvs.end());
    out.uavs.assign(uavs.begin(), uavs.end());
    for (const vulkan::SpvBinding &binding : bindings) {
        if (binding.set != 0) {
            error = fmt::format("GLES VDP currently supports descriptor set 0 only (shader uses set {})", binding.set);
            return false;
        }
        if (binding.binding == 0) {
            continue; // Constants are supplied per dispatch through the dynamic uniform buffer.
        }
        const ResourceRef *resource = nullptr;
        if (binding.binding >= 17) {
            const size_t index = binding.binding - 17;
            if (index < out.uavs.size()) {
                resource = &out.uavs[index];
            }
        } else {
            const size_t index = binding.binding - 1;
            if (index < out.srvs.size()) {
                resource = &out.srvs[index];
            }
        }
        if (resource == nullptr) {
            error = fmt::format("No resource supplied for GLES descriptor binding {}", binding.binding);
            return false;
        }
        const bool needsBuffer = binding.kind == vulkan::SpvDescriptorKind::StorageBuffer ||
                                 binding.kind == vulkan::SpvDescriptorKind::UniformBuffer ||
                                 binding.kind == vulkan::SpvDescriptorKind::UniformTexelBuffer ||
                                 binding.kind == vulkan::SpvDescriptorKind::StorageTexelBuffer;
        const bool needsImage = binding.kind == vulkan::SpvDescriptorKind::SampledImage ||
                                binding.kind == vulkan::SpvDescriptorKind::StorageImage;
        if (needsBuffer && (resource->buffer == nullptr || resource->buffer->Handle() == 0)) {
            error = fmt::format("GLES descriptor binding {} requires a buffer resource", binding.binding);
            return false;
        }
        if (needsImage && (resource->image == nullptr || resource->image->Handle() == 0)) {
            error = fmt::format("GLES descriptor binding {} requires an image resource", binding.binding);
            return false;
        }
        if ((binding.kind == vulkan::SpvDescriptorKind::UniformTexelBuffer ||
             binding.kind == vulkan::SpvDescriptorKind::StorageTexelBuffer) &&
            resource->buffer->TextureView() == 0) {
            error = fmt::format("GLES descriptor binding {} requires a texture-buffer view", binding.binding);
            return false;
        }
    }
    return true;
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

bool ComputeProgram::Dispatch(const DescriptorSet &set, const Buffer &constantsBuffer, GLintptr constantsOffset,
                              GLsizeiptr constantsSize, uint32_t groupsX, uint32_t groupsY, uint32_t groupsZ,
                              std::string &error) const {
    if (m_handle == 0) {
        error = "Cannot dispatch an uninitialized GLES compute program";
        return false;
    }
    if (groupsX == 0 || groupsY == 0 || groupsZ == 0) {
        return true;
    }
    if (constantsBuffer.Handle() == 0 || constantsSize <= 0 || constantsOffset < 0 ||
        constantsOffset > constantsBuffer.Size() || constantsSize > constantsBuffer.Size() - constantsOffset) {
        error = "Invalid GLES compute constant buffer range";
        return false;
    }

    glUseProgram(m_handle);
    glBindBufferRange(GL_UNIFORM_BUFFER, 0, constantsBuffer.Handle(), constantsOffset, constantsSize);
    for (const vulkan::SpvBinding &binding : m_bindings) {
        if (binding.binding == 0) {
            continue;
        }
        const ResourceRef *resource = nullptr;
        if (binding.binding >= 17) {
            const size_t index = binding.binding - 17;
            if (index < set.uavs.size()) {
                resource = &set.uavs[index];
            }
        } else {
            const size_t index = binding.binding - 1;
            if (index < set.srvs.size()) {
                resource = &set.srvs[index];
            }
        }
        if (resource == nullptr) {
            error = fmt::format("No GLES resource supplied for shader binding {}", binding.binding);
            return false;
        }

        switch (binding.kind) {
        case vulkan::SpvDescriptorKind::StorageBuffer:
            if (resource->buffer == nullptr || resource->buffer->Handle() == 0) {
                error = fmt::format("GLES shader binding {} requires a storage buffer", binding.binding);
                return false;
            }
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding.binding, resource->buffer->Handle());
            break;
        case vulkan::SpvDescriptorKind::UniformBuffer:
            if (resource->buffer == nullptr || resource->buffer->Handle() == 0) {
                error = fmt::format("GLES shader binding {} requires a uniform buffer", binding.binding);
                return false;
            }
            glBindBufferBase(GL_UNIFORM_BUFFER, binding.binding, resource->buffer->Handle());
            break;
        case vulkan::SpvDescriptorKind::UniformTexelBuffer:
            if (resource->buffer == nullptr || resource->buffer->TextureView() == 0) {
                error = fmt::format("GLES shader binding {} requires a texture-buffer view", binding.binding);
                return false;
            }
            glActiveTexture(GL_TEXTURE0 + binding.binding);
            glBindTexture(GL_TEXTURE_BUFFER, resource->buffer->TextureView());
            break;
        case vulkan::SpvDescriptorKind::StorageTexelBuffer:
            if (resource->buffer == nullptr || resource->buffer->TextureView() == 0) {
                error = fmt::format("GLES shader binding {} requires a storage texture-buffer view", binding.binding);
                return false;
            }
            glBindImageTexture(binding.binding, resource->buffer->TextureView(), 0, GL_FALSE, 0, GL_WRITE_ONLY,
                               GL_R32UI);
            break;
        case vulkan::SpvDescriptorKind::SampledImage:
            if (resource->image == nullptr || resource->image->Handle() == 0) {
                error = fmt::format("GLES shader binding {} requires a sampled image", binding.binding);
                return false;
            }
            glActiveTexture(GL_TEXTURE0 + binding.binding);
            glBindTexture(resource->image->Target(), resource->image->Handle());
            break;
        case vulkan::SpvDescriptorKind::StorageImage:
            if (resource->image == nullptr || resource->image->Handle() == 0) {
                error = fmt::format("GLES shader binding {} requires a storage image", binding.binding);
                return false;
            }
            glBindImageTexture(binding.binding, resource->image->Handle(), 0,
                               resource->image->Target() == GL_TEXTURE_2D_ARRAY ? GL_TRUE : GL_FALSE, 0,
                               GL_WRITE_ONLY, resource->image->InternalFormat());
            break;
        }
    }
    glDispatchCompute(groupsX, groupsY, groupsZ);
    glMemoryBarrier(GL_ALL_BARRIER_BITS);
    return CheckGl("compute dispatch", error);
}

bool ComputeProgram::Load(std::string_view glesResourcePath, std::string_view spirvResourcePath,
                          std::string_view label, std::string &error) {
    Reset();
    const std::string glesPath(glesResourcePath);
    const std::string spirvPath(spirvResourcePath);
    if (!g_glesShaderFS.is_file(glesPath)) {
        error = fmt::format("Embedded GLES shader not found: {}", glesResourcePath);
        return false;
    }
    if (!g_fsShaders.is_file(spirvPath)) {
        error = fmt::format("Embedded SPIR-V reflection shader not found: {}", spirvResourcePath);
        return false;
    }

    const auto file = g_glesShaderFS.open(glesPath);
    const std::string source(file.begin(), file.end());
    if (source.empty()) {
        error = fmt::format("Embedded GLES shader is empty: {}", glesResourcePath);
        return false;
    }

    const auto spirvFile = g_fsShaders.open(spirvPath);
    const size_t spirvBytes = static_cast<size_t>(spirvFile.size());
    if (spirvBytes == 0 || spirvBytes % sizeof(uint32_t) != 0) {
        error = fmt::format("Embedded SPIR-V shader has an invalid size: {}", spirvResourcePath);
        return false;
    }
    std::vector<uint32_t> spirv(spirvBytes / sizeof(uint32_t));
    std::memcpy(spirv.data(), spirvFile.begin(), spirvBytes);
    auto reflection = vulkan::ReflectSpirv(spirv.data(), spirv.size());
    if (!reflection.valid) {
        error = fmt::format("Could not reflect GLES shader bindings from {}", spirvResourcePath);
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

    glUseProgram(m_handle);
    for (const vulkan::SpvBinding &binding : reflection.bindings) {
        if (binding.kind != vulkan::SpvDescriptorKind::SampledImage &&
            binding.kind != vulkan::SpvDescriptorKind::UniformTexelBuffer) {
            continue;
        }
        GLint location = -1;
        if (binding.kind == vulkan::SpvDescriptorKind::SampledImage) {
            const std::string combinedName = "SPIRV_Cross_Combined" + binding.name + "SPIRV_Cross_DummySampler";
            location = glGetUniformLocation(m_handle, combinedName.c_str());
        }
        if (location < 0 && !binding.name.empty()) {
            location = glGetUniformLocation(m_handle, binding.name.c_str());
        }
        if (location >= 0) {
            glUniform1i(location, static_cast<GLint>(binding.binding));
        } else if (binding.kind == vulkan::SpvDescriptorKind::SampledImage) {
            error = fmt::format("GLES sampler for SPIR-V binding {} ('{}') was not found in '{}'", binding.binding,
                                binding.name, label);
            Reset();
            return false;
        }
    }
    glUseProgram(0);
    m_bindings = std::move(reflection.bindings);
    return true;
}

void ComputeProgram::Reset() {
    if (m_handle != 0) {
        glDeleteProgram(m_handle);
    }
    m_handle = 0;
    m_bindings.clear();
}

void MemoryBarrier(GLbitfield barriers) {
    glMemoryBarrier(barriers);
}

} // namespace ymir::vdp::gles

#endif // defined(__ANDROID__)
