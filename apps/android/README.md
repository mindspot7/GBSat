# GBSat for Android

Sega Saturn emulator for Android, written in Kotlin/Jetpack Compose on top of a native emulator core.

* Native bridge: `app/src/main/cpp/gbsat_jni.cpp` (emulation thread, video, AAudio audio, input)
* UI: `app/src/main/java/app/gbsat/android/ui` (library, settings, in-game menu, touch controls)
* arm64-v8a only. Android 9+ (API 28).
* Version: `VERSION` (release number); CI appends the build number.

## Build

CI: push to the repository; `.github/workflows/android.yml` produces `GBSat-v<version>-build<N>.apk` as a build artifact.

Local: install Android Studio (SDK 36, NDK 27.2.12479018, CMake 3.31.6), clone with `--recurse-submodules`, open
`apps/android`, and run. From a shell: `gradle assembleRelease` (generate a wrapper with `gradle wrapper`).

## Use

1. Settings → choose your Saturn BIOS (IPL ROM, 512 KiB). None is included.
2. Settings → System → choose the folder with your disc images (CUE/BIN, CHD, ISO, MDS, CCD).
3. Pick a game. In the library, X opens the main menu, Start cycles the theme (Showcase, Grid, All in One), B exits and Select toggles fullscreen. In a game, B/Back closes the current menu level or opens the in-game menu; the controller's menu hotkey also opens it. Physical gamepads work automatically.

License: GPLv3 (see `LICENSE` at the repository root).
