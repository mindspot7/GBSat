cmake_minimum_required(VERSION 3.28)
set(CMAKE_CXX_SCAN_FOR_MODULES OFF) # avoid host clang-scan-deps vs NDK clang PCH mismatch
project(gbsat LANGUAGES C CXX)

# <repo>/apps/android/app/src/main/cpp -> <repo>
get_filename_component(YMIR_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../.." ABSOLUTE)

# Build the emulator core as a library (not the SDL3 desktop app). The software renderer is the default; the Vulkan
# renderer is compiled in too (requires DXC on the build machine to precompile the HLSL shaders to SPIR-V).
set(Ymir_LIBRARY_ONLY ON CACHE BOOL "" FORCE)
set(Ymir_DEV_BUILD OFF CACHE BOOL "" FORCE)
set(Ymir_ENABLE_DEVLOG OFF CACHE BOOL "" FORCE)
set(Ymir_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_subdirectory("${YMIR_ROOT}" ymir-build)

# cereal (header-only) for save state serialization; the serializers are shared with the desktop app.
include(FetchContent)
set(JUST_INSTALL_CEREAL ON CACHE BOOL "" FORCE)
FetchContent_Declare(cereal GIT_REPOSITORY https://github.com/USCiLab/cereal.git GIT_TAG v1.3.2 GIT_SHALLOW TRUE)
FetchContent_MakeAvailable(cereal)

# RetroAchievements client library (rcheevos)
set(RCHEEVOS_DIR "${YMIR_ROOT}/vendor/rcheevos")
file(GLOB RCHEEVOS_SOURCES
    "${RCHEEVOS_DIR}/src/*.c"
    "${RCHEEVOS_DIR}/src/rapi/*.c"
    "${RCHEEVOS_DIR}/src/rcheevos/*.c"
    "${RCHEEVOS_DIR}/src/rhash/*.c")
list(FILTER RCHEEVOS_SOURCES EXCLUDE REGEX "rc_libretro\\.c$|rc_client_raintegration\\.c$")
add_library(rcheevos STATIC ${RCHEEVOS_SOURCES})
target_include_directories(rcheevos PUBLIC "${RCHEEVOS_DIR}/include" "${RCHEEVOS_DIR}/src/rhash")
target_compile_definitions(rcheevos PUBLIC RC_DISABLE_LUA RC_CLIENT_SUPPORTS_HASH)
set_target_properties(rcheevos PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(gbsat SHARED gbsat_jni.cpp gbsat_ra.cpp savestate_io.cpp gl_renderer.cpp)
target_include_directories(gbsat PRIVATE "${CMAKE_CURRENT_LIST_DIR}")
target_compile_features(gbsat PRIVATE cxx_std_20)
target_link_libraries(gbsat PRIVATE ymir::ymir-core rcheevos cereal::cereal android log nativewindow aaudio EGL GLESv3 vulkan)
set_target_properties(gbsat PROPERTIES INTERPROCEDURAL_OPTIMIZATION ON)
