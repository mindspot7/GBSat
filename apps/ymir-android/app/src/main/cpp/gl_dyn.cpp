// Implemented before the macro redirection (so the real prototypes are visible).
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <dlfcn.h>

namespace gld {
decltype(&::eglChooseConfig) p_eglChooseConfig = nullptr;
decltype(&::eglCreateContext) p_eglCreateContext = nullptr;
decltype(&::eglCreateWindowSurface) p_eglCreateWindowSurface = nullptr;
decltype(&::eglDestroyContext) p_eglDestroyContext = nullptr;
decltype(&::eglDestroySurface) p_eglDestroySurface = nullptr;
decltype(&::eglGetProcAddress) p_eglGetProcAddress = nullptr;
decltype(&::eglGetDisplay) p_eglGetDisplay = nullptr;
decltype(&::eglInitialize) p_eglInitialize = nullptr;
decltype(&::eglMakeCurrent) p_eglMakeCurrent = nullptr;
decltype(&::eglQuerySurface) p_eglQuerySurface = nullptr;
decltype(&::eglSwapBuffers) p_eglSwapBuffers = nullptr;
decltype(&::eglSwapInterval) p_eglSwapInterval = nullptr;
decltype(&::eglTerminate) p_eglTerminate = nullptr;
decltype(&::glActiveTexture) p_glActiveTexture = nullptr;
decltype(&::glAttachShader) p_glAttachShader = nullptr;
decltype(&::glBindTexture) p_glBindTexture = nullptr;
decltype(&::glBindVertexArray) p_glBindVertexArray = nullptr;
decltype(&::glClear) p_glClear = nullptr;
decltype(&::glClearColor) p_glClearColor = nullptr;
decltype(&::glCompileShader) p_glCompileShader = nullptr;
decltype(&::glCreateProgram) p_glCreateProgram = nullptr;
decltype(&::glCreateShader) p_glCreateShader = nullptr;
decltype(&::glDrawArrays) p_glDrawArrays = nullptr;
decltype(&::glGenTextures) p_glGenTextures = nullptr;
decltype(&::glGenVertexArrays) p_glGenVertexArrays = nullptr;
decltype(&::glGetShaderInfoLog) p_glGetShaderInfoLog = nullptr;
decltype(&::glGetShaderiv) p_glGetShaderiv = nullptr;
decltype(&::glGetUniformLocation) p_glGetUniformLocation = nullptr;
decltype(&::glLinkProgram) p_glLinkProgram = nullptr;
decltype(&::glPixelStorei) p_glPixelStorei = nullptr;
decltype(&::glShaderSource) p_glShaderSource = nullptr;
decltype(&::glTexImage2D) p_glTexImage2D = nullptr;
decltype(&::glTexParameteri) p_glTexParameteri = nullptr;
decltype(&::glTexSubImage2D) p_glTexSubImage2D = nullptr;
decltype(&::glUniform1i) p_glUniform1i = nullptr;
decltype(&::glUniform2f) p_glUniform2f = nullptr;
decltype(&::glUseProgram) p_glUseProgram = nullptr;
decltype(&::glViewport) p_glViewport = nullptr;

namespace {
bool g_angle = false;
void *g_egl = nullptr;
void *g_gles = nullptr;

template <typename T> bool Sym(void *lib, const char *name, T &out) {
    out = reinterpret_cast<T>(dlsym(lib, name));
    return out != nullptr;
}
} // namespace

bool IsAngle() { return g_angle; }

bool Load(bool angle) {
    if (g_egl) {
        dlclose(g_egl);
        g_egl = nullptr;
    }
    if (g_gles) {
        dlclose(g_gles);
        g_gles = nullptr;
    }
    g_angle = false;
    if (angle) {
        g_egl = dlopen("libEGL_angle.so", RTLD_NOW | RTLD_LOCAL);
        g_gles = dlopen("libGLESv2_angle.so", RTLD_NOW | RTLD_LOCAL);
        if (g_egl && g_gles) {
            g_angle = true;
        } else {
            __android_log_print(ANDROID_LOG_WARN, "GBSat-GL", "ANGLE libraries not found (%s); using system GLES",
                                dlerror());
            if (g_egl) dlclose(g_egl);
            if (g_gles) dlclose(g_gles);
            g_egl = g_gles = nullptr;
        }
    }
    if (!g_angle) {
        g_egl = dlopen("libEGL.so", RTLD_NOW | RTLD_LOCAL);
        g_gles = dlopen("libGLESv3.so", RTLD_NOW | RTLD_LOCAL);
        if (!g_gles) g_gles = dlopen("libGLESv2.so", RTLD_NOW | RTLD_LOCAL);
    }
    if (!g_egl || !g_gles) return false;
    bool ok = true;
    ok &= Sym(g_egl, "eglChooseConfig", p_eglChooseConfig);
    ok &= Sym(g_egl, "eglCreateContext", p_eglCreateContext);
    ok &= Sym(g_egl, "eglCreateWindowSurface", p_eglCreateWindowSurface);
    ok &= Sym(g_egl, "eglDestroyContext", p_eglDestroyContext);
    ok &= Sym(g_egl, "eglDestroySurface", p_eglDestroySurface);
    ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress);
    ok &= Sym(g_egl, "eglGetDisplay", p_eglGetDisplay);
    ok &= Sym(g_egl, "eglInitialize", p_eglInitialize);
    ok &= Sym(g_egl, "eglMakeCurrent", p_eglMakeCurrent);
    ok &= Sym(g_egl, "eglQuerySurface", p_eglQuerySurface);
    ok &= Sym(g_egl, "eglSwapBuffers", p_eglSwapBuffers);
    ok &= Sym(g_egl, "eglSwapInterval", p_eglSwapInterval);
    ok &= Sym(g_egl, "eglTerminate", p_eglTerminate);
    if (!Sym(g_gles, "glActiveTexture", p_glActiveTexture)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glActiveTexture = reinterpret_cast<decltype(p_glActiveTexture)>(p_eglGetProcAddress("glActiveTexture"))) != nullptr);
    if (!Sym(g_gles, "glAttachShader", p_glAttachShader)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glAttachShader = reinterpret_cast<decltype(p_glAttachShader)>(p_eglGetProcAddress("glAttachShader"))) != nullptr);
    if (!Sym(g_gles, "glBindTexture", p_glBindTexture)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glBindTexture = reinterpret_cast<decltype(p_glBindTexture)>(p_eglGetProcAddress("glBindTexture"))) != nullptr);
    if (!Sym(g_gles, "glBindVertexArray", p_glBindVertexArray)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glBindVertexArray = reinterpret_cast<decltype(p_glBindVertexArray)>(p_eglGetProcAddress("glBindVertexArray"))) != nullptr);
    if (!Sym(g_gles, "glClear", p_glClear)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glClear = reinterpret_cast<decltype(p_glClear)>(p_eglGetProcAddress("glClear"))) != nullptr);
    if (!Sym(g_gles, "glClearColor", p_glClearColor)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glClearColor = reinterpret_cast<decltype(p_glClearColor)>(p_eglGetProcAddress("glClearColor"))) != nullptr);
    if (!Sym(g_gles, "glCompileShader", p_glCompileShader)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glCompileShader = reinterpret_cast<decltype(p_glCompileShader)>(p_eglGetProcAddress("glCompileShader"))) != nullptr);
    if (!Sym(g_gles, "glCreateProgram", p_glCreateProgram)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glCreateProgram = reinterpret_cast<decltype(p_glCreateProgram)>(p_eglGetProcAddress("glCreateProgram"))) != nullptr);
    if (!Sym(g_gles, "glCreateShader", p_glCreateShader)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glCreateShader = reinterpret_cast<decltype(p_glCreateShader)>(p_eglGetProcAddress("glCreateShader"))) != nullptr);
    if (!Sym(g_gles, "glDrawArrays", p_glDrawArrays)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glDrawArrays = reinterpret_cast<decltype(p_glDrawArrays)>(p_eglGetProcAddress("glDrawArrays"))) != nullptr);
    if (!Sym(g_gles, "glGenTextures", p_glGenTextures)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glGenTextures = reinterpret_cast<decltype(p_glGenTextures)>(p_eglGetProcAddress("glGenTextures"))) != nullptr);
    if (!Sym(g_gles, "glGenVertexArrays", p_glGenVertexArrays)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glGenVertexArrays = reinterpret_cast<decltype(p_glGenVertexArrays)>(p_eglGetProcAddress("glGenVertexArrays"))) != nullptr);
    if (!Sym(g_gles, "glGetShaderInfoLog", p_glGetShaderInfoLog)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glGetShaderInfoLog = reinterpret_cast<decltype(p_glGetShaderInfoLog)>(p_eglGetProcAddress("glGetShaderInfoLog"))) != nullptr);
    if (!Sym(g_gles, "glGetShaderiv", p_glGetShaderiv)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glGetShaderiv = reinterpret_cast<decltype(p_glGetShaderiv)>(p_eglGetProcAddress("glGetShaderiv"))) != nullptr);
    if (!Sym(g_gles, "glGetUniformLocation", p_glGetUniformLocation)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glGetUniformLocation = reinterpret_cast<decltype(p_glGetUniformLocation)>(p_eglGetProcAddress("glGetUniformLocation"))) != nullptr);
    if (!Sym(g_gles, "glLinkProgram", p_glLinkProgram)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glLinkProgram = reinterpret_cast<decltype(p_glLinkProgram)>(p_eglGetProcAddress("glLinkProgram"))) != nullptr);
    if (!Sym(g_gles, "glPixelStorei", p_glPixelStorei)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glPixelStorei = reinterpret_cast<decltype(p_glPixelStorei)>(p_eglGetProcAddress("glPixelStorei"))) != nullptr);
    if (!Sym(g_gles, "glShaderSource", p_glShaderSource)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glShaderSource = reinterpret_cast<decltype(p_glShaderSource)>(p_eglGetProcAddress("glShaderSource"))) != nullptr);
    if (!Sym(g_gles, "glTexImage2D", p_glTexImage2D)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glTexImage2D = reinterpret_cast<decltype(p_glTexImage2D)>(p_eglGetProcAddress("glTexImage2D"))) != nullptr);
    if (!Sym(g_gles, "glTexParameteri", p_glTexParameteri)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glTexParameteri = reinterpret_cast<decltype(p_glTexParameteri)>(p_eglGetProcAddress("glTexParameteri"))) != nullptr);
    if (!Sym(g_gles, "glTexSubImage2D", p_glTexSubImage2D)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glTexSubImage2D = reinterpret_cast<decltype(p_glTexSubImage2D)>(p_eglGetProcAddress("glTexSubImage2D"))) != nullptr);
    if (!Sym(g_gles, "glUniform1i", p_glUniform1i)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glUniform1i = reinterpret_cast<decltype(p_glUniform1i)>(p_eglGetProcAddress("glUniform1i"))) != nullptr);
    if (!Sym(g_gles, "glUniform2f", p_glUniform2f)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glUniform2f = reinterpret_cast<decltype(p_glUniform2f)>(p_eglGetProcAddress("glUniform2f"))) != nullptr);
    if (!Sym(g_gles, "glUseProgram", p_glUseProgram)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glUseProgram = reinterpret_cast<decltype(p_glUseProgram)>(p_eglGetProcAddress("glUseProgram"))) != nullptr);
    if (!Sym(g_gles, "glViewport", p_glViewport)) ok &= Sym(g_egl, "eglGetProcAddress", p_eglGetProcAddress) && ((p_glViewport = reinterpret_cast<decltype(p_glViewport)>(p_eglGetProcAddress("glViewport"))) != nullptr);
    return ok;
}

EGLDisplay GetDisplay() {
    if (g_angle) {
        using GetPlatformDisplayFn = EGLDisplay (*)(EGLenum, void *, const EGLint *);
        auto getPlatformDisplay =
            reinterpret_cast<GetPlatformDisplayFn>(p_eglGetProcAddress("eglGetPlatformDisplayEXT"));
        if (getPlatformDisplay) {
            const EGLint attrs[] = {0x3203 /*EGL_PLATFORM_ANGLE_TYPE_ANGLE*/, 0x3450 /*..._VULKAN_ANGLE*/, EGL_NONE};
            EGLDisplay d = getPlatformDisplay(0x3202 /*EGL_PLATFORM_ANGLE_ANGLE*/, EGL_DEFAULT_DISPLAY, attrs);
            if (d != EGL_NO_DISPLAY) return d;
        }
        __android_log_print(ANDROID_LOG_WARN, "GBSat-GL", "ANGLE display creation failed");
    }
    return p_eglGetDisplay(EGL_DEFAULT_DISPLAY);
}

} // namespace gld
