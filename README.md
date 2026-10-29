<div align="center">

# GBSat

**Sega Saturn emulation for Android phones and handhelds**

Vulkan GPU rendering up to 4x · Full controller navigation · Save states · RetroAchievements

[Download the latest APK](../../releases/latest) · [Changelog](CHANGELOG.md)

<img src="docs/screenshots/library.png" width="760" alt="Game library">

</div>

## Highlights

| | |
| --- | --- |
| 🚀 **Vulkan GPU renderer** | Saturn VDP1/VDP2 graphics on the GPU with Vulkan compute shaders, internal resolution up to **4x**. CPU (OpenGL) renderer for every other device. |
| 🎮 **Built for handhelds** | The whole app works with a controller: D-pad, A/B/X/Y, Select for library fullscreen, full remapping, hotkeys and an analog 3D Control Pad mode. Touch controls too. |
| 💾 **Save states** | Slots with screenshot previews, quick save/load, **auto save and auto load**; recent save states are shown within the All in One library layout. |
| 🏆 **RetroAchievements** | Login, hardcore mode, unlock notifications, achievement list and offline play in casual mode. |
| 🖼️ **Game library** | Box art downloaded automatically (with fuzzy title matching), custom covers, play time per game. |
| 📺 **Display** | 4:3, Fullscreen or 16:9 with **built-in widescreen hacks** for ~20 games; CRT, CRT Royale, GTU, LCD3x and Scanlines shaders, bsnes gamma color correction. |
| ⚡ **Quick presets** | High / Mid / Low tune the renderer, resolution, shader and frame skip in one tap. |

## Screenshots

<table>
<tr>
<td><img src="docs/screenshots/menu.png" alt="Settings menu"><br><sub>Settings menu with controller navigation</sub></td>
<td><img src="docs/screenshots/settings-video.png" alt="Video settings"><br><sub>Video: presets, Vulkan GPU, resolution, shaders</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/settings-controls.png" alt="Controls"><br><sub>Button mapping on a Saturn pad diagram</sub></td>
<td><img src="docs/screenshots/settings-performance.png" alt="Performance"><br><sub>Performance: frame skip, CD speed, emulation options</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/setup.png" alt="Setup wizard"><br><sub>First-run setup wizard</sub></td>
<td><img src="docs/screenshots/settings-extras.png" alt="Extras"><br><sub>RetroAchievements</sub></td>
</tr>
</table>

<sub>Screenshots show fictional sample games with original covers drawn for GBSat (`tools/make_sample_covers.py`). They are rendered from the app's UI code by
`ReadmeScreenshots.kt` (run locally).</sub>

## All features

**Emulation and video**
- Renderers: CPU (OpenGL ES presentation) and GPU (Vulkan compute shaders, 1x–4x internal resolution)
- Filters (Nearest, Bilinear), shaders (CRT, CRT Royale, GTU, LCD3x, Scanlines) and color correction (bsnes gamma ramp)
- 4:3, Fullscreen and 16:9 display modes; widescreen hacks applied automatically
- Automatic frame skip (only while below full speed), threaded audio Auto/On/Off (Auto measures and remembers the faster mode per game), CD read speed up to 16x (8x by default; videos and music streamed from the CD keep the original speed), Skip Engine (maximum speed through loading screens, transitions and save waits), fast boot (skips the BIOS setup screens), 4 MiB RAM cartridge, NTSC/PAL
- Overlays: FPS and speed (green/yellow/red), CPU and GPU usage, renderer, resolution, frame time, on-screen fast-forward button

**Audio**
- Low-latency AAudio output, volume control, fast-forward audio (Default, Speed up, Mute)

**Games and saves**
- CUE/BIN, CHD, ISO, MDS and CCD images
- Save states with slots and previews, quick slot, auto save/load; recent states appear in the All in One library layout
- Built-in cheat database (libretro-database), Action Replay and raw codes, `.cht` import
- Game options: Continue from the latest state, Open fresh, choose a custom cover
- Recommended per-game settings for slower devices; renderer, frame skip and threaded audio changed in a game are remembered for that game

**Controls and interface**
- Home screen layouts: Showcase (default), Grid and All in One. The main menu's Theme tile cycles layouts, Fullscreen toggles the library display, and Select provides the same fullscreen shortcut.
- Controller navigation everywhere, including the in-game quick menu (save/load, video, audio, overlays, cheats, disc tray, reset)
- Button remapping (including L3/R3), hotkeys for quick save/load, fast-forward (hold or toggle) and the menu
- First-run setup wizard, configurable data folder, crash reports with automatic fallback to the CPU renderer

## Requirements

- Android 9 or newer, 64-bit ARM (arm64-v8a). Vulkan GPU renderer: a Vulkan 1.1 GPU (Adreno, Mali, Xclipse…)
- Your own Sega Saturn BIOS image (512 KiB). **None is included.**
- Your own game disc images. **No games are included or downloaded.**

## Getting started

1. Install the APK from the [Releases](../../releases/latest) page.
2. Follow the setup wizard: select your BIOS, pick your games folder and a preset. No storage permission is needed: GBSat only reads the folder you pick.
3. Choose a game in the library. In game, Back (or the on-screen menu button) opens the quick menu.

Frontends (ES-DE, Daijishou, Pegasus) and scripts can start a game directly. The game must be in GBSat's games folder; it is found by its file name:

```
am start -n app.gbsat.emulator/app.gbsat.android.MainActivity -a android.intent.action.VIEW -d <content:// or file:// URI of the disc>
am start -n app.gbsat.emulator/app.gbsat.android.MainActivity --es ROM "/storage/emulated/0/ROMS/SATURN/Virtua Fighter 2.chd"
```

## Downloads and versions

A release (`GBSat-v<version>.apk`) is published automatically when the version in `apps/android/VERSION` changes; its
notes come from [CHANGELOG.md](CHANGELOG.md). Development builds of every push are in the **Actions** tab (artifact
`GBSat-v<version>-build<N>`). APKs built after the package-ID change use both the Gradle `applicationId` and `namespace` `app.gbsat.emulator`, and are signed with the same key, so subsequent builds update that installation. The Kotlin/JNI source package remains `app.gbsat.android`. They are installed separately from older builds whose **application ID** was `app.gbsat.android`; Android does not automatically migrate the old app's private data. Export important saves before switching, then import them into the new installation if needed. The two installations can coexist.

## Building

| Path | Contents |
| --- | --- |
| `apps/android` | Android app: Kotlin/Compose UI and the JNI bridge |
| `libs/gbsat-core` | Emulator core, including the Vulkan compute renderer |
| `vendor/` | Third-party libraries (git submodules) |
| `libs/gbframe` | Shared framework ([GBFrame](https://github.com/mindspot7/GBFrame), git submodule) |
| `apps/vk-test` | Host test comparing the software and Vulkan renderers |
| `.github/workflows` | Single Build GBSat workflow; tests run locally |

Local build: Android Studio with SDK 36, NDK 27.2.12479018 and CMake 3.31.6, plus the DirectX Shader Compiler
(`dxc`, with SPIR-V support) in your `PATH`. Clone with `--recurse-submodules`, open `apps/android` and build.

For a locally signed Windows APK, use `tools/build-local.ps1 -BuildNumber <N> -Install` with credentials in an external folder configured through `GBSAT_SIGNING_DIR`. The [Android build guide](apps/android/README.md#locally-signed-apk-on-windows) documents setup, version numbering and the included Gradle wrapper. This path builds and signs locally without GitHub Actions.

## Privacy

GBSat collects no personal data; see the [privacy policy](PRIVACY.md).

## License

GBSat is based on [Ymir](https://github.com/StrikerX3/Ymir) by StrikerX3.
Sega Saturn is a trademark of SEGA. GBSat is not affiliated with or endorsed by SEGA.


Widescreen codes: SegaXtreme "Saturn Widescreen Codes & Patches" collection. Cheat database: libretro-database (CC BY-SA 4.0).

GPLv3. See [LICENSE](LICENSE) for the full text and the credits of the original authors.
