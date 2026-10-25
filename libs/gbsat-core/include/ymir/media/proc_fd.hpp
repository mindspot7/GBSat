#pragma once

// Reading disc files the Android app stages as symlinks to /proc/self/fd/N: through the descriptor, never by reopening
// the link by path (refused on some devices, e.g. the Anbernic RG DS). The helpers live in the GBFrame kit
// (native/gb_native.cmake: gb_native_core) so every console core of the family shares them; built without the kit
// (desktop, tests) only plain files are opened.

#include <filesystem>
#include <fstream>

#if __has_include(<gb_proc_fd.hpp>)
    #include <gb_proc_fd.hpp>

namespace ymir::media {

using gb::InputFile;
using gb::ProcFdOf;

} // namespace ymir::media
#else
namespace ymir::media {

inline int ProcFdOf(const std::filesystem::path &) {
    return -1;
}

// Same interface as the kit's InputFile: an `std::ifstream{path, std::ios::binary}`
class InputFile final : public std::ifstream {
public:
    explicit InputFile(const std::filesystem::path &path) : std::ifstream(path, std::ios::binary) {}
};

} // namespace ymir::media
#endif
