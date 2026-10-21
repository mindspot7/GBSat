# Changelog

## Unreleased
- Vulkan renderer: finished frames are read back from cached GPU memory in a single pass (faster frame delivery on Mali GPUs).
- Vulkan renderer: games that change the scroll of the NBG2/NBG3 backgrounds every scanline (e.g. DonPachi) render each frame in one GPU batch instead of one per line.
- Vulkan renderer: games that rewrite video registers every scanline with unchanged values (e.g. DonPachi) no longer split each frame into hundreds of GPU batches.
- Faster emulation: native code is now optimized with a profile recorded while playing (PGO). Training: "PGO training build" workflow and tools/pgo-pull.bat.
- Performance: the sound thread no longer burns CPU in the kernel while waiting, and the video render threads get their names (so they are placed on the right cores).
- Profiling support: GBSat can be sampled by the on-device profiler (profileable); "Profiling build" workflow and tools/profile.bat.
- Play time, save data export/import, the Resume view and the controller diagram also come from the shared UI kit.
- Setup wizard, box art and RetroAchievements now come from the shared UI kit.
- The interface building blocks (theme, settings rows, dialogs, menu icons, monitoring overlay) now come from the shared UI kit (github.com/mindspot7/UI_GB, git submodule libs/ui_gb).
- Edge-to-edge: removed the deprecated status/navigation bar color and cutout settings (Android 15+).

## 1.3.1
- In-game menu: Exit goes straight back to the library (Quit GBSat removed); the game picture no longer takes the controller's key focus, so the menu works with a controller. Exit dialog in the app colors.
- Controller: A confirms the Quit dialog right after start or after closing a game; the in-game menu takes the focus on its first item, so the controller works as soon as it opens.

## 1.3.0
- No more "All files access" permission (required for Google Play): the games folder is chosen with the system folder picker and games are read from it directly (CHD, ISO, CUE/BIN, CCD, MDS). After updating, pick the games folder again once.
- Data is kept in the app's storage, with Export data (saves and save states) / Import data (Settings → System) for backups and for bringing back data from a custom folder used by older versions.
- KOF95 / Ultraman ROM cartridges are added in Settings → System → Cartridge ROMs.
- Google Play readiness: targets Android 16 (API 36), 16 KB memory page support, App Bundle (.aab) built alongside the APK. Header subtitle "Saturn emulator".

## 1.2.0
- Monitoring overlay header only when all monitoring overlays are on. Code cleanup.
- Auto frame skip skips more frames on hi-res/interlaced screens (menus such as Daytona USA's), which cost up to 4x the rendering work.
- Threaded VDP can be switched again during a game (applies immediately).
- Fixed lasting slowdown after changing video/performance options in game: threads created by the emulation thread (GPU driver, recreated render threads) no longer stay on its CPU core.

## 1.1.0
- Threaded VDP changes take effect when a game starts (switching mid-game slowed the game down).
- In-game menu: Performance page with Frame skip, Threaded VDP and Threaded audio.
- Threaded audio (on by default): the SCSP sound chip and its 68000 run on their own thread, so the emulation thread only runs the SH-2s; that thread is also placed on a fast core.
- Fixed a frozen picture with the Vulkan GPU renderer and automatic frame skip.
- The screen no longer turns off while a game is open.
- CPU placement ranks cores by scheduler capacity: the emulation thread gets the fastest core to itself, VDP1 the next big core(s), so chips with close big/LITTLE clocks (Unisoc T618) are handled.
- Game database (adapted from Mednafen): the right cartridge per game (1 MiB RAM where 4 MiB breaks the game; KOF95/Ultraman ROM carts from GBSat/carts/kof95.bin and ultraman.bin; 4 MiB otherwise). SH-2 cache is forced only for the games that need it, so the High preset no longer turns it on for every game. Long game names stay on one line. RetroAchievements shows total points.
- High preset uses the Native resolution. Fast-forward button next to the menu button; overlay shows the Android version.
- Vulkan GPU: "Native" internal resolution, matching the screen (up to 6x; lowered automatically if the GPU can't handle it). Merged upstream Ymir 0.4.2 dependency updates. CI actions updated to Node 24.
- Performance overlay redesigned (top right, colored): app version, FPS/speed, CPU name and usage, GPU name and load, renderer, resolution, frame time; "Show all monitoring" option. Recent view shows the latest save state of each game. RetroAchievements: hardcore and softcore points; unlocks from the current session endpoint.
- RetroAchievements: user picture, name and points at the top of the menu; Game options → Achievements shows any game's achievements and your unlocks. "Cover image" label. No highlight box around the selected cover. Select works right after start and after closing a game.

## 1.0.0 — Public release
- Save state folders are named after the game (existing folders are renamed automatically).
- Landscape: the settings menu is docked on the left and the library works beside it (X or Left/Right switch between them). The button help bar is shown only when a controller is connected.
- Performance: automatic frame skip (skips frames only while below full speed; also in the in-game Video menu), emulation and VDP threads pinned to the fast CPU cores with an Android performance hint session (Android 13+), zero-copy frame presentation through AHardwareBuffer (no texture upload; falls back automatically).
- Quit dialog: centered, A confirms. Back from a settings page returns to the open menu on that entry. 20dp cover spacing; the shelf keeps the selected cover in the second slot so the last one is never cut off.
- "Confirm Quit?" dialog with A/B buttons, half-width. No selection outline on covers, 16dp spacing, last cover no longer cut off.
- Presets: Low uses the Software renderer, Mid has no shader. Shaders keep their size in hi-res/interlaced screens (menus and title screens of games such as Daytona USA).
- Small screens (e.g. 720x720): the interface scales down to fit. Menu opens over the button bar. Wider spacing between covers, shelf bar replaced by a soft cover reflection. Select hint removed.

## 0.9.0
- Illustrated multicolor menu icons; narrower dropdown menu with a solid background.
- Library: cleaner shelf (selected game details above the box art, save state and achievement badges, bigger selected cover), fixed button help bar with Nintendo-style colored buttons; blurred background removed.
- Setup wizard: preset is the last step, gradient background, colorful icons. High preset uses the Scanlines shader.
- Library: menu drops down from the GBSat logo bar (tap anywhere on the bar); closer box art.
- In-game menu: new Overlays submenu (FPS, speed, renderer, resolution, fast-forward button, touch controls); on-screen fast-forward button.
- Library: covers with even width (proportions kept in a narrow range), game details at the top, light violet selection outline, Start opens the game options, B asks before quitting.
- Y opens the game options; the top bar is touch-only (the controller stays in the menu and the shelf); square covers keep their proportions.
- Game options: Continue (latest save state), Open (no save state), choose cover. Better box art matching (disc markers, unbalanced tags, word overlap, Japanese titles). Glossy menu icons.
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
