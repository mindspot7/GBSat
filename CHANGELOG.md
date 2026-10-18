# Changelog

## 0.5.0
- Vulkan GPU: sprite shadows now respect the per-layer shadow enable (fixed darkened backgrounds, e.g. Three Dirty Dwarves on Mali).
- Vulkan GPU: internal resolution up to 4x (Settings → Display → Internal resolution). VDP1 sprites and polygons are rasterized at the higher resolution; backgrounds stay native. Applies to non-interlaced, standard-resolution screens.
- RetroAchievements (rcheevos): login, hardcore mode, unlock notifications with badges, achievement list in the quick menu, progress saved with save states.
- Vulkan GPU: fixed sprites going behind backgrounds of equal priority on Adreno GPUs (Three Dirty Dwarves).
- Quick menu: "Compare renderers (debug)" reports differences between Software and Vulkan GPU on the device.
- Renderer list: the presentation-only "Vulkan" option was removed (Vulkan GPU replaces it); "Sharp bilinear" filter removed; display mode "Fill" renamed to "Fullscreen".
- Quick menu: "Export debug state" saves the current state to Download/GBSat for bug reports.
- Built-in cheat database (libretro-database, 190+ Saturn games) shown in the quick menu Cheats screen; supports conditional (D) codes and the libretro `.cht` format.
- Quick menu: single "Reset" option (hard reset).
- Vulkan GPU: VDP1 erase works on GPUs limited to fewer than 1024 threads per work group.
- Vulkan GPU (beta) renderer: VDP1/VDP2 rendered with Vulkan compute shaders, with fallback to software.
- Versioned APKs (`GBSat-v<version>-build<N>.apk`), version shown in Settings.
- Changing shader/filter from the quick menu no longer resets the game.
- App id is now `app.gbsat.android` (installs as a new app).
- Crash reports: after a crash the app shows a report on the next launch; a crash with the Vulkan GPU renderer switches back to Software.
