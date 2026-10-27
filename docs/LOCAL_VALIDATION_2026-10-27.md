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
