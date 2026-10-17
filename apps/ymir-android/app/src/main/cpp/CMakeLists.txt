cmake_minimum_required(VERSION 3.28)
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

add_library(ymir_android SHARED ymir_jni.cpp)
target_compile_features(ymir_android PRIVATE cxx_std_20)
target_link_libraries(ymir_android PRIVATE ymir::ymir-core android log nativewindow aaudio)
set_target_properties(ymir_android PROPERTIES INTERPROCEDURAL_OPTIMIZATION ON)
