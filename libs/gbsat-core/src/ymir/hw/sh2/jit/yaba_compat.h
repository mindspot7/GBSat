// GBSat: stand-ins for the YabaSanshiro definitions DynarecSh2 was written against (core.h, sh2core.h, memory.h,
// threads.h, yabause.h). Memory goes to the Ymir SH-2 that is running (sh2_jit.cpp); nothing else of Yabause is used.
#pragma once

#include <cstdint>
#include <cstring>

#if defined(__ANDROID__) || defined(__linux__)
    #ifndef ARCH_IS_LINUX
        #define ARCH_IS_LINUX
    #endif
#endif
#if defined(__aarch64__) && !defined(AARCH64)
    #define AARCH64
#endif

typedef uint8_t u8;
typedef int8_t s8;
typedef uint16_t u16;
typedef int16_t s16;
typedef uint32_t u32;
typedef int32_t s32;
typedef uint64_t u64;
typedef int64_t s64;

#define FASTCALL
#define LOG(...) ((void)0)

// One emulation thread runs both SH-2s: no locking needed
typedef int YabMutex;
inline YabMutex *YabThreadCreateMutex() {
    return nullptr;
}
inline void YabThreadFreeMutex(YabMutex *) {}
inline void YabThreadLock(YabMutex *) {}
inline void YabThreadUnLock(YabMutex *) {}

// Yabause options the compiler checks: no SH-2 cache emulation in compiled code, no HLE BIOS
struct YabSysCompat {
    int use_sh2_cache = 0;
    int emulatebios = 0;
    int extend_backup = 0;
};
extern YabSysCompat yabsys;

// Bus accesses of the SH-2 that is running (instruction fetches at compile time, exception entry)
u16 MappedMemoryReadInst(u32 addr, u32 *cycles);
u32 MappedMemoryReadLong(u32 addr, u32 *cycles);
u32 MappedMemoryReadLongNocache(u32 addr, u32 *cycles);
void MappedMemoryWriteLong(u32 addr, u32 value, u32 *cycles);
