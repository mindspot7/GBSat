# Changelog

## 0.8.0 — first release

First public release of GBSat, a Sega Saturn emulator for Android based on Ymir.

**Emulation and video**
- Renderers: Software, OpenGL ES and Vulkan GPU (Saturn VDP1/VDP2 graphics on the GPU with Vulkan compute shaders).
- Vulkan GPU internal resolution up to 4x (sharper 3D sprites and polygons).
- Presets: High (Vulkan GPU 4x, LCD3x), Mid (OpenGL ES, Dots), Low (OpenGL ES, frame skip).
- Shaders (CRT, Dots, LCD3x, Scanlines) and Nearest/Bilinear filtering.
- Display modes 4:3, Fullscreen and 16:9, with built-in widescreen hacks for about 20 games.
- Frame skip, CD read speed up to 16x, 4 MiB RAM expansion cartridge always inserted, NTSC/PAL from the disc.

**Games and saves**
- Library with cover art, blurred cover background and play time per game.
- Save states with screenshot previews, plus a "Recent" view of all save states (opens the game at that state).
- Built-in cheat database (libretro-database) and Action Replay codes.
- RetroAchievements: login, hardcore mode, notifications and achievement list.

**Controls and interface**
- Full controller navigation (D-pad, A, B, Y) in the menus and in the in-game menu.
- Gamepad remapping with automatic defaults, hotkeys (single buttons or combinations), on-screen touch controls.
- First-run setup wizard, configurable data folder, full-screen interface with clock and battery.
