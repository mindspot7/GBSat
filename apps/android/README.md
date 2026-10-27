# GBSat for Android

Sega Saturn emulator for Android, written in Kotlin/Jetpack Compose on top of a native emulator core.

* Native bridge: `app/src/main/cpp/gbsat_jni.cpp` (emulation thread, video, AAudio audio, input)
* UI: `app/src/main/java/app/gbsat/android/ui` (library, settings, in-game menu, touch controls)
* arm64-v8a only. Android 9+ (API 28).
* Version: `VERSION` (release number); CI appends the build number.

## Build

CI: manually dispatch `Build GBSat` with the Android version code; `.github/workflows/build.yml` produces `GBSat-v<version>-build<N>.apk` as a build artifact.

Local: install Android Studio (SDK 36, NDK 27.2.12479018, CMake 3.31.6), clone with `--recurse-submodules`, open
`apps/android`, and run. The repository includes a Gradle 8.14.3 wrapper, matching CI; no global Gradle or sibling GBCast checkout is needed.

### Locally signed APK on Windows

Keep `gbsat-release.jks` and `GBSAT_KEYSTORE_PASSWORD.txt` in an external signing folder. Configure only its path as the Windows user variable `GBSAT_SIGNING_DIR`, or pass `-SigningDirectory` explicitly. Credentials stay outside the repository; the helper reads the password without printing it or placing it in command-line arguments.

From the repository root:

```powershell
./tools/build-local.ps1 -BuildNumber 381 -Install
```

Choose an appropriate version code for every build; it must not be lower than the installed version. The helper uses the local release key, builds/tests the release variant, verifies the APK signature, and optionally updates the device without uninstalling. It finds the Android SDK through `ANDROID_HOME` (or the standard Windows SDK location), and DXC through `PATH` or a sibling `Toolchains/dxc*/bin/x64` installation. Java 17, SDK 36, NDK 27.2.12479018 and CMake 3.31.6 are required. The first wrapper/dependency download needs internet; it does not run a GitHub build.

The APK is saved to `test-output/local-build<N>/GBSat-v<version>-build<N>.apk` (git-ignored). Gradle also accepts `-PgbsatVersionCode=<N>` directly. Manual cloud builds accept `version_code` so their version stays compatible with newer local installations; otherwise CI uses its run number. Pushes do not generate APKs unless the dedicated build trigger changes. Plain debug builds keep their separate development signature and do not replace an official installation.

### Data folder

The initial wizard and System offer the shared GBFrame **Data folder** picker.
Choose a dedicated writable folder for BIOS, saves, states/screenshots, cheats,
covers and cartridge ROMs. Games keep their separate read-only folder. Selection
uses Android's SAF permissions and requires reopening before restoring existing
files; native emulation uses a synchronized local working copy. System includes
**Sync now** and reports unavailable storage without deleting local saves.
To restore an old backup, select the actual folder containing `saves`/`states`
(for example `GBSat backup`), then close and reopen. The old Export/Import buttons
are replaced by this persistent folder workflow. Account tokens and preferences
remain private; GBSat does not currently support texture replacement packs.

## Use

1. Settings → choose your Saturn BIOS (IPL ROM, 512 KiB). None is included.
2. Settings → System → choose the folder with your disc images (CUE/BIN, CHD, ISO, MDS, CCD).
3. Pick a game. In the library, X opens the main menu, Start cycles the theme (Showcase, Grid, All in One), B exits and Select toggles fullscreen. In a game, B/Back closes the current menu level or opens the in-game menu; the controller's menu hotkey also opens it. Physical gamepads work automatically.

License: GPLv3 (see `LICENSE` at the repository root).

Tests, Android Lint, screenshots and profiling run locally. See the shared
[build and validation standard](../../libs/gbframe/docs/BUILD_VALIDATION_STANDARD.md).
