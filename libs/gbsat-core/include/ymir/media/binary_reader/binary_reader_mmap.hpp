#pragma once

#include "binary_reader.hpp"

#include <ymir/media/proc_fd.hpp>

#include <mio/mmap.hpp>

#include <filesystem>
#include <span>

namespace ymir::media {

// Implementation of IBinaryReader backed by a memory-mapped file.
class MemoryMappedBinaryReader final : public IBinaryReader {
public:
    // Initializes a file content pointing to no file.
    MemoryMappedBinaryReader() = default;

    // Initializes a file content pointing to the specified file.
    // If any errors occur while reading the file, initializes an empty file content and returns the error in the
    // provided std::error_code object.
    MemoryMappedBinaryReader(std::filesystem::path path, std::error_code &error) {
        error.clear();
        // A /proc/self/fd/N link (how the Android app exposes documents) is mapped through its descriptor: reopening the
        // link by path is refused on some devices. The mapping outlives the descriptor, which mio does not close.
        if (const int procFd = ProcFdOf(path); procFd >= 0) {
            m_in = mio::make_mmap_source(procFd, error);
            if (!error) {
                return;
            }
            error.clear();
        }
        m_in = mio::make_mmap_source(path.native(), error);
    }

    MemoryMappedBinaryReader(const MemoryMappedBinaryReader &) = delete;
    MemoryMappedBinaryReader(MemoryMappedBinaryReader &&) = default;

    MemoryMappedBinaryReader &operator=(const MemoryMappedBinaryReader &) = delete;
    MemoryMappedBinaryReader &operator=(MemoryMappedBinaryReader &&) = default;

    uintmax_t Size() const final {
        return m_in.size();
    }

    uintmax_t Read(uintmax_t offset, uintmax_t size, std::span<uint8> output) const final {
        if (!m_in.is_mapped()) {
            return 0;
        }
        if (offset >= m_in.size()) {
            return 0;
        }

        // Limit size to the smallest of the requested size, the output buffer size and the amount of bytes available in
        // the file starting from offset
        size = std::min(size, m_in.size() - offset);
        size = std::min(size, output.size());
        std::copy_n(m_in.begin() + offset, size, output.begin());
        return size;
    }

private:
    mio::mmap_source m_in;
};

} // namespace ymir::media
