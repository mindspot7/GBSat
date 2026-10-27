# Local validation: signed builds, data folder and shared overlays

- Standalone Gradle 8.14.3 wrapper and `tools/build-local.ps1` build an official-key
  release APK locally. Credentials stay in the external signing folder. APK
  signature verification and device updates succeeded without uninstalling.
- Build 380 was installed on RG Rotate (`ROTATE05716608`). System retained
  `Documents/GBSatData` after reopening and the Saturn BIOS remained loaded.
  Its `bios`, `saves`, `states`, `cheats`, `covers`, `carts` mounts use GBFrame's
  SAF persistence and synchronized local working copy. Games remain separate;
  account tokens/preferences are not exported. Texture packs are not supported by
  the current Saturn core and no nonfunctional Textures option is exposed.
- The real provider copied BIOS/backup data/covers and retained read/write grants.
  A native Dracula X (BR) save in empty manual slot 5 also synchronized its
  `4.savestate`, thumbnail, RA sidecar and game metadata into the selected tree.
  Renaming the tree and invoking Sync now displayed the shared unavailable-storage
  warning while preserving the local working copy. The original folder was restored.
- Export/Import data UI was replaced by persistent Data folder controls. Existing
  backups can be chosen at the level containing saves/states, then restored on reopen.
- The shared model and rows now own monitoring toggles and the rule hiding the
  duplicate quick-menu fast-forward action when the on-screen button is enabled.
- Local release regression tests (9), shared data/monitoring tests (10), Compose UI
  tests for wizard/fast-forward visibility (2), and application-ID consistency pass.
  Screenshot suites are excluded from the release build command; Compose tests run
  in debug, where their test Activity is declared. No cloud APK build was requested.
- Repository pushes are independent of APK generation; cloud Android builds now
  require manual dispatch or a dedicated trigger-file commit. Manual version_code
  can be set to at least the latest local build to preserve update compatibility.

## Launcher correction - build 381
- Added adaptive override matching the manifest icon resource; transparent background, original foreground and legacy PNGs preserved.
- Local signed build and icon/startup/pause plus shared regression tests passed; installed with adb install -r.
- RG Rotate: current app.gbsat.emulator icon has no white backing on the second launcher page, alongside GBCast. First-page white icon belongs to legacy app.gbsat.android build 329; confirmed by launching each. Legacy installation/data preserved.
- Adopted GBFrame 4d392ec launcher resource rule. No GitHub APK build triggered.


## Direct authoritative data storage - build 382

- Adopted shared GBFrame SAF Kotlin/native adapters. Selected Documents/GBSatData is the working storage for BIOS, saves, states, screenshots, cheats, covers and cartridge ROMs, with no active local mirror.
- Migration preserves previous offline writes, verifies destinations and retires matching obsolete local files once. Ordinary startup checks roots/access only, without full content hashes or background synchronization.
- Native internal/cartridge backup memory maps the provider's actual file descriptor. SMPC, BIOS/cartridge reads, savestate serialization and RetroAchievements state sidecars use the common native data adapter instead of libc++ reopening local paths.
- RG Rotate: existing Dracula X (BR) save was available, saved to previously empty Slot 4, showed the screenshot and loaded that slot directly. BIOS remained Loaded, and covers read from the selected folder.
- Temporarily renamed the selected folder with the app stopped: startup blocked core initialization, displayed the storage error and shared chooser. Restored the folder without uninstalling or clearing app data.
- Local release regression tests: 9 app tests plus 12 shared tests; debug shared wizard/monitoring UI checks also passed (2). App-ID check passed. Installed with the official release signing key.
- BIOS/cartridge imports stage in the selected folder before replacement, including when users select the already-installed file; no directory mirror is created.
- No all-files permission was added. Uses the official Android folder picker and persistent document grants. This storage approach does not itself require Play's restricted MANAGE_EXTERNAL_STORAGE permission.
- A pre-migration device-data backup is preserved on the PC in ignored test-output/direct-data-before.
- Final device BIOS-import check selected the already-installed Documents/GBSatData/bios/ipl.bin itself through the Android picker. The app reported BIOS loaded, and SHA-256 before/after was identical, confirming safe direct replacement without truncating the source.


## Shared System layout - build 383

- Uses GBFrame SystemSettings: three Data folder rows, cartridge ROMs, automatic save states, then Loading Times. Getting started is removed from System.
- Bios folder displays Documents/GBSatData/bios and ipl.bin - Loaded. The shared chooser supports folder import and individual files; multiple 512 KiB candidates require an explicit selection.
- Fast boot and the three existing Skip Engine toggles are grouped in Loading Times; the three skip options were removed from the main Performance page. Existing settings and native callbacks are preserved.
- RG Rotate: verified Data row order/paths, BIOS status, Cartridge ROMs placement, Save states before Loading Times, all four Loading Times options, and scrolling on 720x720.
- Changed Skip loading screens, allowed preferences to flush, force-stopped/reopened and verified the changed value persisted. Restored its original enabled value afterwards.
- Selected the active bios folder through the new folder action: app reported BIOS loaded and the installed BIOS SHA-256 stayed unchanged.
- Local release tests: 9 app tests and 14 shared tests. Debug Compose UI tests: 4 passed, including section order, unconfigured folder descriptions, selected paths and unsupported-feature omission. App-ID check passed.
- No extra storage permissions; imports keep using the official document picker and active SAF storage adapter.

## Shared Skip Engine hardening - local build 384

- Updates GBFrame's existing detector: synchronized renderer/emulation observations, atomic audio peak maximum and diagnostics, immediate cancellation on audio/gameplay or disabled options, and conservative save-wait detection that does not accelerate moving-picture background saves.
- Keeps existing System > Loading Times controls and preferences. Uses the same native detector as GBCast; console-specific signals and acceleration remain in each consumer.
- Shared standalone ARM64 native tests: 17 passed on RG Rotate, including concurrent callbacks and all three skip reasons. Local release build and selected app tests (9) plus shared tests (14) passed.
- Installed the locally signed build 384 as an update, retaining app data and signature. Dracula X (BR) booted from the selected data/game folders at normal speed (60.4 FPS / 100.6% observed). App-ID and whitespace checks passed.
- Existing screenshot changes were preserved and excluded from this update. No GitHub build was requested.

## Vulkan verification with automatic Skips - build 384

- RG Rotate / Android 12, installed local build 384: selected GPU (Vulkan), confirmed all three existing Skip preferences on, and booted DoDonPachi and Dracula X (BR) from scratch.
- Performance CSV confirms renderer=GPU (Vulkan), manual fast_forward=0 and automatic acceleration in both games. DoDonPachi reached 102.7 emulated FPS during a detected wait, then returned to approximately 60 FPS with auto_skip_pct=0. Dracula X reached 105.4 emulated FPS during a wait and continued through its intro.
- No reproduction of GBCast's Vulkan presentation stalls/resource recreation. GBSat's Vulkan VDP callbacks feed the same detector; presentation uses the OpenGL ES presenter and automatic Skip does not recreate a Vulkan swapchain. Only one Vulkan initialization occurred before the two game sessions.
- Dracula X's Konami intro was slower (roughly 37-47 FPS in sampled intervals). Repeated the boot with all three Skips off, confirmed skip_loads/transitions/saves=off and auto_skip_pct=0 in the CSV: the same intro also slowed to roughly 37-48 FPS. This is a remaining Vulkan performance limitation in this tested game/device, not evidence of the GBCast Skip presentation regression. Later static intro screens returned to about 60 FPS.
- Restored original CPU (OpenGL) renderer and all three Skip preferences on. No app rebuild, save-slot replacement or code fix was necessary for this verification.
- Local evidence (ignored): test-output/vulkan-skip-check/perf-log.csv and screenshots. Existing documentation screenshot edits remain untouched. These are startup/intro tests on two games, not a compatibility guarantee for every game or every save-wait path.

## Lean manual packaging workflow

- Keeps one workflow: Build GBSat, in .github/workflows/build.yml. Manual dispatch only; no push/PR-triggered tests or builds. Android version code is supplied explicitly to preserve update ordering when workflow numbering changes.
- Removes separate test/lint/app-ID/diagnostic workflows. Unit/device/native tests and local diagnostics remain in the repository; cloud packaging does not execute tests, build a test APK, boot an Android emulator or generate screenshots/profiling reports.
- Retains signing, compiler/dependency caches, release APK packaging and the native Android 16 KB package validator. Local install helpers now use build.yml.
- All three workflow files validated locally with actionlint 1.7.12. Local device-tool PowerShell syntax and app-ID checks passed. GBCast's release-only Gradle task graph was checked locally with no unit/instrumentation/debug or Lint tasks.
