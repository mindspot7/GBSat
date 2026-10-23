# Changelog

## Unreleased
- Smoother library scrolling on modest GPUs: cover reflections are drawn in one pass and covers are kept at screen size.
- Threaded audio (Auto) now starts on for every device (before: only with 3+ fast cores, so not on e.g. the RG Rotate): with the lighter sound thread it was faster in every game profiled; per-game choices remembered by Auto are measured again.
- The library opens on the game (or save state) selected last: leaving a game, closing the menu or a settings page and restarting the app no longer jump back to the first game.
- Threaded audio uses less CPU: the sound thread sleeps directly while waiting for work instead of polling for up to ~64 µs each time (about a tenth of a core on the RG Rotate, shared with the video threads).
- Shorter loading with a faster CD read speed (4x and up): seeks on data tracks also run at the faster drive rate (each file read used to spend ~175 ms seeking at the idle rate). No change at the original speed.
- "Hi-res: one field": on 704x448 interlaced screens only one of the two fields is rendered and its lines are repeated, roughly halving the VDP2 work (Virtua Fighter 2). Logged as `one_field` in the performance log.
- Threaded audio (Auto): the per-game choices remembered by earlier versions are cleared, since the VDP2 changes made them out of date (Burning Rangers on the RG Rotate kept threaded audio off at ~55 fps; with it, ~59 fps).
- Faster with threaded VDP2 rendering: the VDP2 render thread takes its own copy of the VDP1 display framebuffer when it starts a frame, so the VDP1 framebuffer erase and swap at the end of a frame no longer wait for the whole frame to be drawn (~4 ms of every drawn frame on Virtua Fighter 2, RG Rotate).
- Faster with threaded VDP2 rendering (on by default): at the end of a drawn frame the emulation no longer waits for the render thread; the frame is shown as soon as it is finished while the next frame is emulated. With "Hi-res: one field" this removes the ~4 ms wait of every drawn frame (Virtua Fighter 2 on the RG Rotate: drawn frames took ~22 ms against a 16.7 ms budget).
- Recommended settings per game can turn off idle-loop skipping and threaded VDP for a game that misbehaves with them (`idleSkip` / `threadedVdp` in game_profiles.json).
- "Hi-res: one field", "Threaded VDP" and "Skip idle loops" are no longer options (removed from Settings and the in-game menu): they are always on, since turning them off was never faster in the performance logs. One field only acts with the CPU renderers: with GPU (Vulkan) it froze the picture and made the game slower (Virtua Fighter 2: ~38 fps instead of ~45), so it is never used there.
- Fixed: with "Hi-res: one field" the skipped field's lines kept an old picture (darker scanline-like rows on hi-res screens and frozen images when a game started); the rendered field's lines now fill them whichever field is rendered.
- Performance log: the time per component is now measured on one frame in 7 (one in 8 always fell on the same field of interlaced screens, so with "Hi-res: one field" it showed only skipped or only rendered frames). The scheduler column (`sched_ms`, formerly `sound_cd_ms`) is split into VDP phases, sound, CD and the rest, and new columns show the average time of rendered and skipped frames.
- Recommended settings: Virtua Fighter 2 and Panzer Dragoon Saga now use threaded audio on devices like the RG Rotate (measured faster: VF2 58.8 vs 53-54 fps, Panzer 56-59 vs 54).
- Fixed: the performance log restarted every second (its header got longer than the version check read).
- Fixed: on some interlaced hi-res screens (e.g. Batsugun at 640x480) the picture could freeze after fast forward or a skipped frame.
- Faster: the SH-2 CPUs skip idle loops (loops that only wait for a value to change), with the same result as running them; the performance log showed most games spending 60-100% of the SH-2 time in such loops. Idle loops that count while waiting, delay loops and the slave CPU waiting for the master's signal are skipped too, by whole iterations with exact cycle counts. When both CPUs wait at the same time, they skip together to the next event. Idle loops that call a short function (e.g. Panzer Dragoon Saga) are recognized too. A CPU waiting in an idle loop skips whole time slices while the memory it waits on doesn't change, even when the other CPU is busy.
- Audio: no more crackling when a game runs just below full speed: the sound now plays up to 5% slower while the buffer is low (dynamic rate control) instead of running dry.
- Automatic frame skip skips only as often as needed: at least 2 frames are shown between skips at first, and the spacing adapts every second (before, it showed only every other frame, ~30 fps on screen, even when the game was close to full speed).
- Performance log: share of each SH-2's time spent in tight loops (polling and delay loops) and the hottest loop of each.
- Performance log (perf-log.csv): new columns with the time per frame of each part of the emulation (master and slave SH-2, SCU, VDP, sound and CD, the rest, measured on one frame in 8), the slowest frame and frames over budget, VDP1 commands per frame, the game's resolution, audio underruns, CPU use per thread with the emulation core and its clock, the thermal status, the device, and all the Video/Performance options in use.

## 1.3.4
- Showcase: the first and last covers (and screenshots in Recent) line up with the game title instead of starting further in.
- The selection highlight on the home screen and in the main menu only appears when a physical controller is detected; with a controller, opening the menu by touch selects System at once.
- Settings → Extras → Layout: new "Button help" switch to show or hide the controller button bar on the home screen.
- Changing the home layout now returns straight to the library.
- Fixed: after opening the main menu with a tap, the first D-pad press now selects System (Android stayed in touch mode, where the menu entries can't take the focus).
- Grid: covers are drawn as 3D boxes (spine and front, with a light gloss).
- Showcase: the blurred backdrop behind the selected game was removed for a cleaner look.
- Fixed: the first D-pad press after a touch moves the focus (no more two presses), in the library and in the main menu.
- Holding left/right on the Showcase and All in One shelves now glides through the covers smoothly.
- Grid: no All games / Recent buttons (use the menu or Select), the selected cover grows again (no frame), more room between rows and at the top and bottom.
- Main menu: opens on System; a tap on the top bar opens it at once, and the next tap works right away (no double tap).
- Showcase: covers sized to the room under the game details (the selected cover no longer touches the buttons on square screens), fixed reflection, and holding left/right moves through the shelf at a steady pace without stutter.
- Grid: no names under the covers.
- Fixed: with the main menu opened by touch, the D-pad didn't move in it.
- Fixed: X (menu) and the D-pad stopped working in the library after a tap or after closing a menu.
- New main menu: a panel that slides in from the left (X on a controller, or a tap on the top bar) with the library view (All games / Recent, with counts), the settings as large icon tiles and Quit.
- The Quality preset row was removed from Settings → Video; the preset for the device is still applied on first run and the individual options stay.
- New home screen (Showcase): the selected game shown large over its blurred cover, with play time, save state and achievements and Continue / Start fresh / Options buttons, and the box art shelf below. The top bar shows the app icon and name, the clock and the battery.
- Home screen layouts in Settings → Extras → Layout: Showcase (default), Grid (box art grid) and All in One (latest save states first, then all games). In Showcase, long game names stay on one line and scroll, and Recent shows the save-state screenshots in the same layout.
- The setup wizard no longer asks for a preset: GBSat picks it for the detected device (Settings → Performance shows the device class and the recommended preset). The Preset item was removed from the in-game Video menu (presets stay in Settings → Video).
- The game picture sits at the top of the screen when the screen is narrower than the picture (e.g. 5:4 screens, 16:9 mode on 4:3): the black bars go below it.
- Touch controls hide automatically while a controller is connected (built-in controls of handhelds included).
- Bigger covers on square and 5:4 screens.
- Small screens: the setup wizard is compact on short screens (e.g. 640x480); on portrait screens the game shelf sits higher.
- Better first-run defaults per device class (CPU core types and GPU): Rockchip RK3566/RK3568 and other chips with only little cores get the Low preset (it was High), as do Snapdragon 662 (older big cores) and Snapdragon G1 Gen 2; Unisoc T820 and other fast CPUs with a modest GPU get Mid; Snapdragon 865 and newer and Dimensity 8300 (Mali-G615 MC6) get High.
- Native internal resolution is limited to what the GPU handles (4x on Adreno 730+, 3x on Adreno 640-729, 2x on Mali-G615 MC6-class GPUs).
- Chips with 4+ equal cores (e.g. RK3566) now also keep the emulation thread alone on its own core.
- Threaded audio Auto: the mode found for a game is also remembered when GBSat is closed during the game.
- Removed leftovers of the old "Allow file access" screen and of the experimental relaxed frame sync.

## 1.3.3
- Renderers simplified to CPU (OpenGL) and GPU (Vulkan): the former Software option is gone, since CPU (OpenGL) renders the same and adds filters and shaders (existing Software users move to CPU (OpenGL); a plain output is used automatically if OpenGL ES can't start).
- Recommended settings per game: heavy games start with the renderer, frame skip and threaded audio that tested best on similar devices (Virtua Fighter 2, Panzer Dragoon Saga, Burning Rangers and NiGHTS on low-end chips). Changes made during a game are remembered for that game. Can be turned off in Settings.
- Threaded audio Auto mode (default): when a game runs below full speed, GBSat tries the other mode, keeps the faster one and remembers it per game.
- Faster CPU renderer: quicker background drawing (same output) and the background render thread on a fast core; Virtua Fighter 2, Panzer Dragoon Saga, Burning Rangers and NiGHTS run much faster on low-end chips such as the Unisoc T618.
- Frame skip: gentler and steadier (only when clearly below full speed, never more than 1 frame in a row), and no more trails on interlaced hi-res screens (e.g. Virtua Fighter 2).
- Fast forward no longer slows down heavy games.
- Offline RetroAchievements (casual mode): after a game was started online once, it can be played without a connection; unlocks are sent with their real time when the connection is back.
- Auto load continues from the game's latest save state in any slot (also with Auto save off), and Auto save is also written when you leave GBSat during a game.
- The library reopens in the layout you used last (all games or recent save states).
- Presets: the setup wizard recommends one for your device; Mid is CPU (OpenGL) with Scanlines, Low is CPU (OpenGL) with frame skip.
- Performance log for device tests (Android/media/app.gbsat.android/perf-log.csv, tools/perf-log.bat).
- The shared framework is now GBFrame (github.com/mindspot7/GBFrame); hotkeys, games folder access (now also GDI track lists), the OpenGL ES presenter, CPU tuning and test tools moved there.

## 1.3.2
- Faster emulation: native code optimized with a profile recorded while playing (PGO); lower CPU use and less heat on every renderer.
- Performance: the sound thread no longer wastes CPU while waiting, and the video render threads are placed on the right cores.
- Vulkan renderer: much faster in games with per-scanline background effects (e.g. DonPachi: from 30 to 60 fps on Mali GPUs), and faster frame delivery on Mali GPUs.
- Fast forward renders about 60 frames per second and skips the rest, so heavy games actually run faster.
- Automatic frame skip pauses itself when skipping does not help the game catch up (smoother picture in very heavy scenes).
- Games that do not need a RAM cartridge now get a 4 MiB save (backup memory) cartridge, stored with the saves (e.g. Dragon Force no longer reports a missing save cartridge).
- The interface now comes from the shared UI kit (github.com/mindspot7/UI_GB); edge-to-edge display without the deprecated bar color settings (Android 15+).

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
