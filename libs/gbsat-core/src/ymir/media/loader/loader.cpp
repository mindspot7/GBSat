#include <ymir/media/loader/loader.hpp>

#include <ymir/media/loader/loader_bin_cue.hpp>
#include <ymir/media/loader/loader_chd.hpp>
#include <ymir/media/loader/loader_img_ccd_sub.hpp>
#include <ymir/media/loader/loader_iso.hpp>
#include <ymir/media/loader/loader_mdf_mds.hpp>

namespace ymir::media {

bool LoadDisc(std::filesystem::path path, Disc &disc, CbLoaderMessage cbMsg) {
    // Sanity check: check that the file exists
    if (!std::filesystem::is_regular_file(path)) {
        cbMsg(MessageType::Error, "File not found");
        disc.Invalidate();
        return false;
    }

    auto fail = [&] {
        cbMsg(MessageType::NotValid,
              "Not a valid disc image format. Supported files are .CCD, .CHD, .CUE, .MDS and .ISO");
        return false;
    };

    // Abuse short-circuiting to pick the first matching loader with less verbosity
    return loader::chd::Load(path, disc, cbMsg) ||    //
           loader::bincue::Load(path, disc, cbMsg) || //
           loader::mdfmds::Load(path, disc, cbMsg) || //
           loader::ccd::Load(path, disc, cbMsg) ||    //
           loader::iso::Load(path, disc, cbMsg) ||    //
           fail();
}

} // namespace ymir::media
