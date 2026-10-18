// Loads a GBSat/Ymir save state into a Saturn without the original BIOS or disc (for renderer debugging).
#include <ymir/ymir.hpp>

#include <serdes/cereal_savestate.hpp>

#include <cereal/archives/portable_binary.hpp>

#include <fstream>
#include <memory>
#include <string>

std::string LoadDebugState(ymir::Saturn &saturn, const std::string &path) {
    try {
        std::ifstream in{path, std::ios::binary};
        if (!in) {
            return "cannot open " + path;
        }
        auto state = std::make_unique<ymir::savestate::SaveState>();
        {
            cereal::PortableBinaryInputArchive archive{in};
            archive(*state);
        }
        state->discHash = saturn.GetDiscHash(); // no disc here
        return saturn.LoadState(*state, true) ? std::string{} : std::string{"state rejected"};
    } catch (const std::exception &e) {
        return std::string("load failed: ") + e.what();
    }
}
