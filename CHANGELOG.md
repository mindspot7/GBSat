# Changelog

## Unreleased
- Library: cleaner shelf (selected game details above the box art, save state and achievement badges, bigger selected cover), fixed button help bar with Nintendo-style colored buttons; blurred background removed.
- Controller: X opens the menu, Select switches between all games and recent save states.
- Auto save / auto load: the game state is saved when closing a game and restored when starting it (System → Save states).
- Overlays: show FPS, speed, renderer and resolution.
- Audio options: volume and fast-forward audio (Default, Speed up, Mute), in the Audio page and the in-game menu.
- Redesigned option pages, controller diagram for button mapping, in-game menu as a left panel with submenus, game shelf in the library.

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
