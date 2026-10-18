# Host test: renders the same synthetic Saturn scene with the software and the Vulkan VDP renderers and compares them.
# Built and run by .github/workflows/vulkan-dev.yml on a software Vulkan driver (lavapipe).
cmake_minimum_required(VERSION 3.28)
project(ymir_vk_test LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_SCAN_FOR_MODULES OFF)
set(Ymir_DEV_BUILD OFF CACHE BOOL "" FORCE)
set(Ymir_ENABLE_DEVLOG OFF CACHE BOOL "" FORCE)
set(Ymir_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(Ymir_ENABLE_IPO OFF CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/../.." ymir-build)
add_executable(vdp_compare vdp_compare.cpp)
target_link_libraries(vdp_compare PRIVATE ymir::ymir-core)
