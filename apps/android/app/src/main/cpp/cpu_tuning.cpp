#include "cpu_tuning.hpp"

#include <android/log.h>
#include <dirent.h>
#include <dlfcn.h>
#include <sched.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "GBSat", __VA_ARGS__)

namespace cputune {
namespace {

std::mutex m;
bool maskReady = false;
cpu_set_t bigMask;
bool haveBigMask = false;

// Fast cores: those whose max frequency is at least 80% of the fastest core. On big.LITTLE chips that leaves out the
// small cores; on chips where all cores are equal no pinning is done.
void BuildMask() {
    maskReady = true;
    const long n = sysconf(_SC_NPROCESSORS_CONF);
    std::vector<long> freq(n > 0 ? n : 0, 0);
    long maxF = 0, minF = 0;
    for (long i = 0; i < n; i++) {
        char path[96];
        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%ld/cpufreq/cpuinfo_max_freq", i);
        if (FILE *f = fopen(path, "r")) {
            if (fscanf(f, "%ld", &freq[i]) != 1) freq[i] = 0;
            fclose(f);
        }
        if (freq[i] > maxF) maxF = freq[i];
        if (freq[i] > 0 && (minF == 0 || freq[i] < minF)) minF = freq[i];
    }
    if (maxF == 0 || minF == maxF) return;
    CPU_ZERO(&bigMask);
    int count = 0;
    for (long i = 0; i < n; i++) {
        if (freq[i] >= maxF * 8 / 10) { CPU_SET(i, &bigMask); count++; }
    }
    // A single prime core cannot host the emulation and the render threads: widen to the next tier
    if (count < 2) {
        for (long i = 0; i < n; i++) {
            if (freq[i] >= maxF * 6 / 10) { CPU_SET(i, &bigMask); }
        }
    }
    haveBigMask = true;
    LOGI("CPU tuning: emulation pinned to fast cores (max %ld kHz)", maxF);
}

// ---- Performance hint session (APerformanceHint, Android 13+), loaded at runtime since minSdk is 28 ----
struct APerformanceHintManager;
struct APerformanceHintSession;
using GetManagerFn = APerformanceHintManager *(*)();
using CreateFn = APerformanceHintSession *(*)(APerformanceHintManager *, const int32_t *, size_t, int64_t);
using ReportFn = int (*)(APerformanceHintSession *, int64_t);
using CloseFn = void (*)(APerformanceHintSession *);
GetManagerFn getManager;
CreateFn createSession;
ReportFn report;
CloseFn closeSession;
APerformanceHintSession *session = nullptr;
std::vector<int32_t> sessionTids;

void UpdateSession(std::vector<int32_t> tids) {
    static bool loaded = false;
    if (!loaded) {
        loaded = true;
        if (void *lib = dlopen("libandroid.so", RTLD_NOW)) {
            getManager = (GetManagerFn)dlsym(lib, "APerformanceHint_getManager");
            createSession = (CreateFn)dlsym(lib, "APerformanceHint_createSession");
            report = (ReportFn)dlsym(lib, "APerformanceHint_reportActualWorkDuration");
            closeSession = (CloseFn)dlsym(lib, "APerformanceHint_closeSession");
        }
    }
    if (!getManager || !createSession || !report || !closeSession) return;
    if (session && tids == sessionTids) return;
    if (session) { closeSession(session); session = nullptr; }
    APerformanceHintManager *mgr = getManager();
    if (!mgr) return;
    session = createSession(mgr, tids.data(), tids.size(), 16'666'666); // one 60 Hz frame
    sessionTids = std::move(tids);
    if (session) LOGI("CPU tuning: performance hint session with %zu threads", sessionTids.size());
}

} // namespace

void PinEmulationThreads() {
    std::lock_guard lk(m);
    if (!maskReady) BuildMask();
    const pid_t self = (pid_t)syscall(SYS_gettid);
    std::vector<int32_t> tids{self};
    if (haveBigMask) sched_setaffinity(self, sizeof(bigMask), &bigMask);
    // VDP render threads ("VDP1 render th", "VDP2 render th", "VDP deinterlac")
    if (DIR *d = opendir("/proc/self/task")) {
        while (dirent *e = readdir(d)) {
            if (e->d_name[0] == '.') continue;
            char path[64], name[32] = {};
            snprintf(path, sizeof(path), "/proc/self/task/%s/comm", e->d_name);
            if (FILE *f = fopen(path, "r")) {
                if (!fgets(name, sizeof(name), f)) name[0] = 0;
                fclose(f);
            }
            if (strncmp(name, "VDP", 3) == 0) {
                const pid_t tid = (pid_t)atoi(e->d_name);
                if (haveBigMask) sched_setaffinity(tid, sizeof(bigMask), &bigMask);
                tids.push_back(tid);
            }
        }
        closedir(d);
    }
    UpdateSession(std::move(tids));
}

void ReportFrameWork(int64_t ns) {
    std::lock_guard lk(m);
    if (session && report && ns > 0) report(session, ns);
}

} // namespace cputune
