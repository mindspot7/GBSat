// Profile-guided optimization support. In the training build (-PgbsatPgoGen) the code is instrumented and the
// counters are written every 30 s to the app's external files folder (Android/data/app.gbsat.android/pgo, readable over USB), since
// Android apps are usually killed instead of exiting normally. One file per process (%p).
#ifdef GBSAT_PGO_GEN
#include <chrono>
#include <sys/stat.h>
#include <thread>

extern "C" void __llvm_profile_set_filename(const char *);
extern "C" int __llvm_profile_write_file(void);

namespace {
struct PgoWriter {
    PgoWriter() {
        static constexpr const char *kDir = "/sdcard/Android/media/app.gbsat.android/pgo";
        mkdir(kDir, 0770);
        __llvm_profile_set_filename("/sdcard/Android/media/app.gbsat.android/pgo/gbsat-%p.profraw");
        std::thread([] {
            while (true) {
                std::this_thread::sleep_for(std::chrono::seconds(30));
                __llvm_profile_write_file();
            }
        }).detach();
    }
} pgoWriter;
} // namespace
#endif
