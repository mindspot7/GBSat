cmake_minimum_required(VERSION 3.28)
set(CMAKE_CXX_SCAN_FOR_MODULES OFF) # avoid host clang-scan-deps vs NDK clang PCH mismatch
project(ymir_android LANGUAGES C CXX)

# <repo>/apps/ymir-android/app/src/main/cpp -> <repo>
get_filename_component(YMIR_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../.." ABSOLUTE)

# Build ymir-core as a library (not the SDL3 desktop app). No Vulkan/DXC on Android: the software
# renderer is used, exactly like the default desktop configuration.
set(Ymir_LIBRARY_ONLY ON CACHE BOOL "" FORCE)
set(Ymir_DEV_BUILD OFF CACHE BOOL "" FORCE)
set(Ymir_ENABLE_DEVLOG OFF CACHE BOOL "" FORCE)
set(Ymir_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(CMAKE_DISABLE_FIND_PACKAGE_Vulkan TRUE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_subdirectory("${YMIR_ROOT}" ymir-build)

# cereal (header-only) for save state serialization; the serializers are shared with the desktop app.
include(FetchContent)
set(JUST_INSTALL_CEREAL ON CACHE BOOL "" FORCE)
FetchContent_Declare(cereal GIT_REPOSITORY https://github.com/USCiLab/cereal.git GIT_TAG v1.3.2 GIT_SHALLOW TRUE)
FetchContent_MakeAvailable(cereal)

add_library(ymir_android SHARED ymir_jni.cpp savestate_io.cpp gl_renderer.cpp gl_dyn.cpp vk_context.cpp)
target_include_directories(ymir_android PRIVATE "${CMAKE_CURRENT_LIST_DIR}")
target_compile_features(ymir_android PRIVATE cxx_std_20)
target_link_libraries(ymir_android PRIVATE ymir::ymir-core cereal::cereal android log nativewindow aaudio EGL GLESv3 vulkan)
set_target_properties(ymir_android PROPERTIES INTERPROCEDURAL_OPTIMIZATION ON)
