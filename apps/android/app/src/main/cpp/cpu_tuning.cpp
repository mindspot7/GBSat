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
#include <algorithm>
#include <vector>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "GBSat", __VA_ARGS__)

namespace cputune {
namespace {

std::mutex m;
bool maskReady = false;
bool havePinning = false;
cpu_set_t mainMask;  // emulation thread (SH-2s, SCU, SCSP...): the single fastest core, alone
cpu_set_t vdp1Mask;  // sound (SCSP) and VDP2 render threads: the next fast core(s)
cpu_set_t otherMask; // VDP1/deinterlace threads: every core except the emulation thread's

long ReadLong(const char *fmt, long cpu) {
    char path[96];
    snprintf(path, sizeof(path), fmt, cpu);
    long v = 0;
    if (FILE *f = fopen(path, "r")) {
        if (fscanf(f, "%ld", &v) != 1) v = 0;
        fclose(f);
    }
    return v;
}

// Cores are ranked by cpu_capacity (the scheduler's own performance rating, which tells big from LITTLE cores even
// when their clocks are close, e.g. Cortex-A75 2.0 GHz vs A55 1.8 GHz on the Unisoc T618), falling back to max clock.
void BuildMask() {
    maskReady = true;
    const long n = sysconf(_SC_NPROCESSORS_CONF);
    if (n < 2) return;
    std::vector<std::pair<long, long>> rank; // (score, cpu)
    for (long i = 0; i < n; i++) {
        long cap = ReadLong("/sys/devices/system/cpu/cpu%ld/cpu_capacity", i);
        long freq = ReadLong("/sys/devices/system/cpu/cpu%ld/cpufreq/cpuinfo_max_freq", i);
        rank.emplace_back(cap > 0 ? cap * 10'000'000L + freq / 1000 : freq, i);
    }
    std::sort(rank.begin(), rank.end(), [](auto &a, auto &b) { return a.first > b.first || (a.first == b.first && a.second > b.second); });
    if (rank[0].first == rank.back().first) return; // all cores equal: leave scheduling to Android
    const long top = rank[0].second, second = rank[1].second;
    CPU_ZERO(&mainMask);
    CPU_SET(top, &mainMask);
    CPU_ZERO(&vdp1Mask);
    CPU_SET(second, &vdp1Mask);
    // Other big cores (same score tier as the second) also take VDP1 work
    for (size_t i = 2; i < rank.size() && rank[i].first == rank[1].first; i++) CPU_SET(rank[i].second, &vdp1Mask);
    CPU_ZERO(&otherMask);
    for (long i = 0; i < n; i++) if (i != top) CPU_SET(i, &otherMask);
    havePinning = true;
    LOGI("CPU tuning: emulation on cpu%ld, sound on cpu%ld+", top, second);
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
    if (havePinning) sched_setaffinity(self, sizeof(mainMask), &mainMask);
    // VDP render threads ("VDP1 render th", "VDP2 render th", "VDP deinterlac") and the sound thread ("SCSP thread")
    if (DIR *d = opendir("/proc/self/task")) {
        while (dirent *e = readdir(d)) {
            if (e->d_name[0] == '.') continue;
            char path[64], name[32] = {};
            snprintf(path, sizeof(path), "/proc/self/task/%s/comm", e->d_name);
            if (FILE *f = fopen(path, "r")) {
                if (!fgets(name, sizeof(name), f)) name[0] = 0;
                fclose(f);
            }
            const pid_t tid = (pid_t)atoi(e->d_name);
            // Threads created by the emulation thread inherit its single-core affinity: GPU driver threads (Vulkan
            // device), recreated render/sound threads, etc. Left there, they share the emulation core forever, even
            // after going back to another renderer or game, so any such thread is moved off it.
            if (havePinning && tid != self && strncmp(name, "VDP", 3) != 0 && strncmp(name, "SCSP", 4) != 0) {
                cpu_set_t cur;
                CPU_ZERO(&cur);
                if (sched_getaffinity(tid, sizeof(cur), &cur) == 0 && CPU_EQUAL(&cur, &mainMask)) {
                    sched_setaffinity(tid, sizeof(otherMask), &otherMask);
                }
            }
            if (strncmp(name, "VDP", 3) == 0 || strncmp(name, "SCSP", 4) == 0) {
                if (havePinning) {
                    // The sound thread is synchronized with the emulation thread many times per frame, so it gets the
                    // next big core(s); the render threads may use every other core and are balanced by Android
                    // VDP2 (the heaviest render thread, especially on hi-res screens) shares the next big core(s)
                    // with the sound thread instead of running on little cores
                    const bool big = strncmp(name, "SCSP", 4) == 0 || strncmp(name, "VDP2", 4) == 0;
                    const cpu_set_t *mask = big ? &vdp1Mask : &otherMask;
                    sched_setaffinity(tid, sizeof(cpu_set_t), mask);
                }
                tids.push_back(tid);
            }
        }
        closedir(d);
    }
    // The hint session covers only the emulation thread, which lives as long as the app: render/sound threads are
    // recreated when options change, and replacing the session each time left the CPU boost in a worse state until the
    // app was restarted.
    UpdateSession({self});
}

void ReportFrameWork(int64_t ns) {
    std::lock_guard lk(m);
    if (session && report && ns > 0) report(session, ns);
}

} // namespace cputune
