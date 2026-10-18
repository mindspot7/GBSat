# GBSat

**GBSat** is a Sega Saturn emulator for Android phones and handhelds (arm64, Android 9+), with a touch- and
gamepad-friendly interface built in Jetpack Compose.

## Features

- **Game library** with cover art (downloaded automatically); CUE/BIN, CHD, ISO, MDS and CCD images
- **Save states** with slots plus quick save/load
- **Gamepad support**: full button remapping (including L3/R3), analog pad mode
- **Hotkeys** for quick save, quick load, fast-forward (hold or toggle) and the menu, bound to a single button or a
  button combination
- **Quick menu** in game: save/load state, fast-forward, reset, disc tray, cheats, display mode, shaders, close game,
  quit
- **Fast boot**: skips the BIOS language and clock setup screens
- **Cheats**: Action Replay and raw address codes, import from `.cht` files
- **Renderers**
  - Software (CPU)
  - OpenGL ES and ANGLE presentation with texture filtering (nearest, bilinear) and shaders
    (scanlines, CRT, LCD3x, dots)
  - **Vulkan GPU**: Saturn VDP1/VDP2 graphics rendered with Vulkan compute shaders
- **Display**: 4:3, Fullscreen or 16:9 (with built-in widescreen hacks for supported games), optional FPS counter
- **RetroAchievements**: login, hardcore mode, unlock notifications and achievement list
- Touch controls (can be hidden)
- Crash reports shown on the next launch, with automatic fallback to the Software renderer

## Requirements

- Android 9 or newer, 64-bit ARM (arm64-v8a)
- A Sega Saturn BIOS image (512 KiB). None is included.
- Your own game disc images

## Getting started

1. Install the APK.
2. Open **Settings** and select your BIOS file.
3. Grant file access and pick the folder that holds your games.
4. Choose a game in the library. The back button (or the menu button at the top left) opens the quick menu.

## Downloads and versions

APKs are built by GitHub Actions on every push. Open the **Actions** tab, select the latest **Android APK** run and
download the `GBSat-v<version>-build<N>` artifact.

The release version is kept in `apps/android/VERSION` and every build adds its build number. The version is shown at
the bottom of the Settings screen. Changes are listed in [CHANGELOG.md](CHANGELOG.md).

## Building

| Path | Contents |
| --- | --- |
| `apps/android` | Android app: Kotlin/Compose UI and the JNI bridge |
| `libs/gbsat-core` | Emulator core, including the Vulkan compute renderer |
| `vendor/` | Third-party libraries (git submodules) |
| `apps/vk-test` | Host test comparing the software and Vulkan renderers |
| `.github/workflows` | APK build, Vulkan shader/renderer checks, ANGLE build |

Local build: Android Studio with SDK 35, NDK 27.2.12479018 and CMake 3.31.6, plus the DirectX Shader Compiler
(`dxc`, with SPIR-V support) in your `PATH`. Clone with `--recurse-submodules`, open `apps/android` and build.

## License

Widescreen codes: SegaXtreme "Saturn Widescreen Codes & Patches" collection. Cheat database: libretro-database (CC BY-SA 4.0).

GPLv3. See [LICENSE](LICENSE) for the full text and the credits of the original authors.
