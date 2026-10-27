// sh2cmp: frame-by-frame comparison of two Saturn runs from the same save state, for the SH-2 JIT (docs/SH2_JIT.md).
// Both runs use the same BIOS, disc and state; run B can use different options (later: the JIT). After every frame the
// save state of each run is hashed per component; the first frame that differs is reported with the components and
// SH-2 registers that differ. No UI, no threads: runs on the device through adb.
//
//   sh2cmp --bios ipl.bin --disc game.chd --state 0.savestate [--frames 600] [--sync 128] [--sync-b 128] [--jit-b]
//
// Exit code: 0 identical, 1 different, 2 setup error.

#include <ymir/ymir.hpp>
#include <ymir/util/wait_stats.hpp>

#include "../../../../../../../libs/gbsat-core/src/ymir/hw/sh2/jit/sh2_jit.hpp"

#include <serdes/cereal_savestate.hpp>

#include <cereal/archives/portable_binary.hpp>

#include <xxh3.h>

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

namespace {

struct Options {
    std::string bios, disc, state;
    int frames = 600;
    int sync = 128;
    int syncB = -1; // same as sync
    bool jitB = false;
    bool jitNoCompile = false;
};

std::unique_ptr<ymir::Saturn> MakeRun(const Options &o, int sync, const std::vector<uint8_t> &ipl, std::string &err) {
    auto saturn = std::make_unique<ymir::Saturn>();
    auto &cfg = saturn->configuration;
    // Single-threaded and deterministic
    cfg.swRenderer.threadedVDP1 = false;
    cfg.swRenderer.threadedVDP2 = false;
    cfg.swRenderer.threadedDeinterlacer = false;
    cfg.audio.threadedSCSP = false;
    // Emulated clock from a fixed time: with the host clock, games that read the RTC (Daytona USA) differ between runs
    cfg.rtc.mode = ymir::core::config::rtc::Mode::Virtual;
    cfg.rtc.virtHardResetStrategy = ymir::core::config::rtc::HardResetStrategy::ResetToFixedTime;
    saturn->LoadIPL(std::span<uint8_t, ymir::sys::kIPLSize>(const_cast<uint8_t *>(ipl.data()), ymir::sys::kIPLSize));

    ymir::media::Disc disc;
    std::string log;
    if (!ymir::media::LoadDisc(o.disc, disc, false, [&log](ymir::media::MessageType, std::string m) { log += m + "\n"; })) {
        err = "cannot load disc: " + log;
        return nullptr;
    }
    // Same cartridge as the app for most games (save states hold its contents): a 4 MiB backup memory cartridge,
    // in a scratch file per run
    {
        static int runIndex = 0;
        const std::string path = "sh2cmp_cart" + std::to_string(runIndex++) + ".bin";
        std::remove(path.c_str());
        ymir::bup::BackupMemory bm;
        std::error_code ec;
        bm.CreateFrom(path, false, ec, ymir::bup::BackupMemorySize::_32Mbit);
        if (ec) {
            err = "cannot create the backup cartridge file: " + ec.message();
            return nullptr;
        }
        saturn->InsertCartridge<ymir::cart::BackupMemoryCartridge>(std::move(bm));
    }
    saturn->LoadDisc(std::move(disc));
    saturn->AutodetectRegion();
    saturn->Reset(true);

    auto state = std::make_unique<ymir::savestate::SaveState>();
    try {
        std::ifstream in(o.state, std::ios::binary);
        if (!in) {
            err = "cannot open state";
            return nullptr;
        }
        cereal::PortableBinaryInputArchive archive{in};
        archive(*state);
    } catch (const std::exception &e) {
        err = std::string("cannot read state: ") + e.what();
        return nullptr;
    }
    if (!saturn->LoadState(*state)) {
        err = "state rejected (different BIOS or disc?)";
        return nullptr;
    }
    saturn->SetSH2SyncStep(sync);
    return saturn;
}

// Hash of a save state component through its serializer. A component whose serializer refuses the state (the SCU's
// checks the cartridge type) is left out and reported once.
template <typename T>
uint64_t Hash(const T &part, const char *name) {
    try {
        std::ostringstream os(std::ios::binary);
        {
            cereal::PortableBinaryOutputArchive archive{os};
            archive(const_cast<T &>(part));
        }
        const std::string s = os.str();
        return XXH3_64bits(s.data(), s.size());
    } catch (const std::exception &e) {
        static std::string reported;
        if (reported.find(name) == std::string::npos) {
            std::printf("note: %s not compared (%s)\n", name, e.what());
            reported += name;
        }
        return 0;
    }
}

struct Hashes {
    uint64_t v[9];
};
const char *const kParts[] = {"MSH2", "SSH2", "WRAM-L", "WRAM-H", "system", "SCU", "VDP", "sound RAM", "SMPC"};

Hashes HashState(const ymir::savestate::SaveState &s) {
    Hashes h{};
    h.v[0] = Hash(s.msh2, "MSH2");
    h.v[1] = Hash(s.ssh2, "SSH2");
    h.v[2] = XXH3_64bits(s.system.WRAMLow.data(), s.system.WRAMLow.size());
    h.v[3] = XXH3_64bits(s.system.WRAMHigh.data(), s.system.WRAMHigh.size());
    h.v[4] = Hash(s.system, "system");
    h.v[5] = Hash(s.scu, "SCU");
    h.v[6] = Hash(s.vdp, "VDP");
    h.v[7] = XXH3_64bits(s.scsp.WRAM.data(), s.scsp.WRAM.size()); // sound RAM (the SCSP serializer checks its CDDA buffer)
    h.v[8] = Hash(s.smpc, "SMPC");
    return h;
}

void PrintSH2(const char *name, const ymir::savestate::SH2SaveState &a, const ymir::savestate::SH2SaveState &b) {
    std::printf("  %s  %-8s %-8s\n", name, "run A", "run B");
    auto row = [](const char *reg, uint32_t x, uint32_t y) {
        std::printf("  %-5s %08X %08X%s\n", reg, x, y, x != y ? "  <" : "");
    };
    for (int i = 0; i < 16; ++i) {
        char reg[8];
        std::snprintf(reg, sizeof reg, "R%d", i);
        row(reg, a.R[i], b.R[i]);
    }
    row("PC", a.PC, b.PC);
    row("PR", a.PR, b.PR);
    row("SR", a.SR, b.SR);
    row("GBR", a.GBR, b.GBR);
    row("VBR", a.VBR, b.VBR);
    row("MACH", a.MACH, b.MACH);
    row("MACL", a.MACL, b.MACL);
}

void PrintFirstDiff(const char *name, const uint8_t *a, const uint8_t *b, size_t size, uint32_t base) {
    for (size_t i = 0; i < size; ++i) {
        if (a[i] != b[i]) {
            size_t n = 0;
            for (size_t j = i; j < size; ++j) {
                n += a[j] != b[j];
            }
            std::printf("  %s: %zu bytes differ, first at %08X (A %02X, B %02X)\n", name, n, base + (uint32_t)i, a[i],
                        b[i]);
            return;
        }
    }
}

} // namespace

int main(int argc, char **argv) {
    Options o;
    for (int i = 1; i < argc; ++i) {
        const std::string a = argv[i];
        auto next = [&]() -> std::string { return i + 1 < argc ? argv[++i] : std::string{}; };
        if (a == "--bios") o.bios = next();
        else if (a == "--disc") o.disc = next();
        else if (a == "--state") o.state = next();
        else if (a == "--frames") o.frames = std::atoi(next().c_str());
        else if (a == "--sync") o.sync = std::atoi(next().c_str());
        else if (a == "--sync-b") o.syncB = std::atoi(next().c_str());
        else if (a == "--jit-b") o.jitB = true;
        else if (a == "--jit-b-nocompile") o.jitB = o.jitNoCompile = true; // the JIT path, interpreting only
        else {
            std::fprintf(stderr, "unknown option %s\n", a.c_str());
            return 2;
        }
    }
    if (o.bios.empty() || o.disc.empty() || o.state.empty()) {
        std::fprintf(stderr, "usage: sh2cmp --bios ipl.bin --disc game.chd --state 0.savestate [--frames N] [--sync N] "
                             "[--sync-b N]\n");
        return 2;
    }
    if (o.syncB < 0) o.syncB = o.sync;

    std::vector<uint8_t> ipl(ymir::sys::kIPLSize);
    {
        std::ifstream in(o.bios, std::ios::binary);
        if (!in.read(reinterpret_cast<char *>(ipl.data()), ipl.size())) {
            std::fprintf(stderr, "cannot read BIOS %s\n", o.bios.c_str());
            return 2;
        }
    }
    util::waitstats::g_sh2Chained.store(true); // as in the app

    std::string err;
    auto runA = MakeRun(o, o.sync, ipl, err);
    if (!runA) {
        std::fprintf(stderr, "run A: %s\n", err.c_str());
        return 2;
    }
    auto runB = MakeRun(o, o.syncB, ipl, err);
    if (!runB) {
        std::fprintf(stderr, "run B: %s\n", err.c_str());
        return 2;
    }
    if (o.jitB && !runB->EnableSH2Jit(true)) {
        std::fprintf(stderr, "the SH-2 JIT is not supported here\n");
        return 2;
    }
    if (o.jitNoCompile) {
        runB->GetSH2Jit()->compileEnabled = false;
    }
    std::printf("A: sync %d   B: sync %d%s   %d frames\n", o.sync, o.syncB, o.jitB ? " JIT" : "", o.frames);
    const auto jitStats = [&] {
        if (const auto *jit = runB->GetSH2Jit()) {
            const auto &s = jit->stats;
            const double total = double(s.instrsRun + s.interpreted);
            std::printf("JIT: %llu blocks run (%.1f instr avg), %llu instr compiled = %.1f%%, %llu interpreted; "
                        "%llu blocks compiled, %llu invalidated, %llu flushes\n",
                        (unsigned long long)s.blocksRun, s.blocksRun ? double(s.instrsRun) / s.blocksRun : 0.0,
                        (unsigned long long)s.instrsRun, total > 0 ? 100.0 * s.instrsRun / total : 0.0,
                        (unsigned long long)s.interpreted, (unsigned long long)s.compiled,
                        (unsigned long long)s.invalidated, (unsigned long long)s.flushes);
        }
    };

    auto stA = std::make_unique<ymir::savestate::SaveState>();
    auto stB = std::make_unique<ymir::savestate::SaveState>();
    double msA = 0, msB = 0;
    for (int frame = 1; frame <= o.frames; ++frame) {
        auto t0 = std::chrono::steady_clock::now();
        runA->RunFrame();
        auto t1 = std::chrono::steady_clock::now();
        runB->RunFrame();
        auto t2 = std::chrono::steady_clock::now();
        msA += std::chrono::duration<double, std::milli>(t1 - t0).count();
        msB += std::chrono::duration<double, std::milli>(t2 - t1).count();

        runA->SaveState(*stA);
        runB->SaveState(*stB);
        const Hashes ha = HashState(*stA);
        const Hashes hb = HashState(*stB);
        bool same = true;
        for (int i = 0; i < 9; ++i) {
            same &= ha.v[i] == hb.v[i];
        }
        if (!same) {
            std::printf("DIFFERENT at frame %d:", frame);
            for (int i = 0; i < 9; ++i) {
                if (ha.v[i] != hb.v[i]) std::printf(" %s", kParts[i]);
            }
            std::printf("\n");
            if (ha.v[0] != hb.v[0]) PrintSH2("MSH2", stA->msh2, stB->msh2);
            if (ha.v[1] != hb.v[1]) PrintSH2("SSH2", stA->ssh2, stB->ssh2);
            PrintFirstDiff("WRAM-L", stA->system.WRAMLow.data(), stB->system.WRAMLow.data(), stA->system.WRAMLow.size(),
                           0x00200000);
            PrintFirstDiff("WRAM-H", stA->system.WRAMHigh.data(), stB->system.WRAMHigh.data(),
                           stA->system.WRAMHigh.size(), 0x06000000);
            std::printf("time per frame: A %.2f ms, B %.2f ms\n", msA / frame, msB / frame);
            jitStats();
            return 1;
        }
        if (frame % 60 == 0) {
            std::printf("frame %d identical (A %.2f ms, B %.2f ms per frame)\n", frame, msA / frame, msB / frame);
            std::fflush(stdout);
        }
    }
    std::printf("IDENTICAL for %d frames. Time per frame: A %.2f ms, B %.2f ms\n", o.frames, msA / o.frames,
                msB / o.frames);
    jitStats();
    return 0;
}
