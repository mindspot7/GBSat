# Changelog

## 0.5.0
- Built-in cheat database (libretro-database, 190+ Saturn games) shown in the quick menu Cheats screen; supports conditional (D) codes and the libretro `.cht` format.
- Quick menu: single "Reset" option (hard reset).
- Vulkan GPU: VDP1 erase works on GPUs limited to fewer than 1024 threads per work group.
- Vulkan GPU (beta) renderer: VDP1/VDP2 rendered with Vulkan compute shaders, with fallback to software.
- Versioned APKs (`GBSat-v<version>-build<N>.apk`), version shown in Settings.
- Changing shader/filter from the quick menu no longer resets the game.
- App id is now `app.gbsat.android` (installs as a new app).
- Crash reports: after a crash the app shows a report on the next launch; a crash with the Vulkan GPU renderer switches back to Software.
