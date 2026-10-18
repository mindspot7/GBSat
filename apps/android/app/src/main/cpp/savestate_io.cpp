// Save state (de)serialization. Kept in its own translation unit because the cereal serializers are heavy.
// Uses the same on-disk format as the desktop frontend (cereal PortableBinaryArchive), so states are interchangeable.
#include <ymir/ymir.hpp>

#include <serdes/cereal_savestate.hpp>

#include <cereal/archives/portable_binary.hpp>

#include <filesystem>
#include <fstream>
#include <memory>
#include <string>

namespace savestate_io {

std::string Save(ymir::Saturn &saturn, const std::filesystem::path &path) {
    try {
        auto state = std::make_unique<ymir::savestate::SaveState>();
        saturn.SaveState(*state);
        std::error_code ec;
        std::filesystem::create_directories(path.parent_path(), ec);
        const auto tmp = path.string() + ".tmp";
        {
            std::ofstream out{tmp, std::ios::binary};
            if (!out) {
                return "Cannot write save state file";
            }
            cereal::PortableBinaryOutputArchive archive{out};
            archive(*state);
        }
        std::filesystem::rename(tmp, path, ec);
        return ec ? "Cannot finalize save state file: " + ec.message() : std::string{};
    } catch (const std::exception &e) {
        return std::string("Save failed: ") + e.what();
    }
}

std::string Load(ymir::Saturn &saturn, const std::filesystem::path &path) {
    try {
        std::ifstream in{path, std::ios::binary};
        if (!in) {
            return "No save state in this slot";
        }
        auto state = std::make_unique<ymir::savestate::SaveState>();
        {
            cereal::PortableBinaryInputArchive archive{in};
            archive(*state);
        }
        return saturn.LoadState(*state) ? std::string{} : "Save state is incompatible (different BIOS or disc?)";
    } catch (const std::exception &e) {
        return std::string("Load failed: ") + e.what();
    }
}

} // namespace savestate_io
