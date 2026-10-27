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
