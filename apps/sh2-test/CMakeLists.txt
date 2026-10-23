# Host test: the chained SH-2 interpreter must give exactly the same results as the plain one (no BIOS, no Vulkan).
# Built and run by .github/workflows/sh2-test.yml.
cmake_minimum_required(VERSION 3.20)
project(gbsat_sh2_test LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_SCAN_FOR_MODULES OFF)
set(Ymir_DEV_BUILD OFF CACHE BOOL "" FORCE)
set(Ymir_ENABLE_DEVLOG OFF CACHE BOOL "" FORCE)
set(Ymir_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(Ymir_ENABLE_IPO OFF CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/../.." ymir-build)
add_executable(sh2_chain_test sh2_chain_test.cpp)
target_link_libraries(sh2_chain_test PRIVATE ymir::ymir-core)
