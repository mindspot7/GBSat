# GBSat (Ymir for Android)

Android frontend for the [Ymir](https://github.com/StrikerX3/Ymir) Sega Saturn emulator. It builds the unmodified
`ymir-core` library (same software renderer, NEON-optimized VDP paths, threaded VDP, SH-2/SCU/SCSP/CD block emulation)
and wraps it in a Kotlin/Jetpack Compose app.

* Native bridge: `app/src/main/cpp/ymir_jni.cpp` (emulation thread, ANativeWindow video, AAudio audio, input)
* UI: `app/src/main/java/app/ymir/android/ui` (library, settings, in-game menu, touch controls)
* arm64-v8a only. Android 9+ (API 28).

## Build

CI: push to your fork; `.github/workflows/android.yml` produces the APK as a build artifact.

Local: install Android Studio (SDK 35, NDK 27.2.12479018, CMake 3.31.6), clone with `--recurse-submodules`, open
`apps/ymir-android`, and run. From a shell: `gradle assembleRelease` (generate a wrapper with `gradle wrapper`).

## Use

1. Settings → choose your Saturn BIOS (IPL ROM, 512 KiB). Ymir does not ship one.
2. Settings → grant file access and choose the folder with your disc images (CUE/BIN, CHD, ISO, MDS, CCD).
3. Pick a game. Back button opens the pause menu. Physical gamepads work automatically.

## Not yet ported from the desktop app

Save states / rewind, debugger windows, per-game profiles, MIDI, CD Block LLE toggle, backup-RAM manager,
GPU renderers (Direct3D/Vulkan/Metal are not used by the default desktop path either), custom input mapping.

License: GPLv3, same as Ymir.
