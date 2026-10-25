# GBSat – notes for Claude

GBSat is the Sega Saturn emulator for Android of the GB family (core: the Ymir fork in `libs/gbsat-core`; UI kit: the
GBFrame submodule in `libs/gbframe`). Read `libs/gbframe/CLAUDE.md` too: it holds the kit's rules and what belongs to the
kit and what to the app. Talk to the owner in Portuguese; code, comments, commits and CHANGELOG entries are in English.

## Repositories and branches
- Work on **`android-port`** (the Android app); commit and push there. Never force-push. Releases (the `VERSION` file, release
  notes, tags) are the owner's: do not touch them.
- `libs/gbframe` is a separate repo (`mindspot7/GBFrame`, branch `main`). A change that is generic (every app built on the kit
  would want it) goes into the kit first: `git -C libs/gbframe switch main`, commit, push, then commit the new submodule
  pointer here. App-specific code stays here.
- Clone with `git clone --recurse-submodules`. New entries for users go under `## Unreleased` in `CHANGELOG.md`.

## Identity (do not let these drift)
- `applicationId` = Gradle `namespace` = **`app.gbsat.emulator`** (`apps/android/app/build.gradle.kts`). The Kotlin source
  package stays `app.gbsat.android`, so the launcher activity is `app.gbsat.android.MainActivity`; use the fully qualified
  class (`am start -n app.gbsat.emulator/app.gbsat.android.MainActivity`), never `.MainActivity`.
- `APP_ID` in `apps/android/app/src/main/cpp/CMakeLists.txt` must be the applicationId: PGO data and the perf log live in
  `/sdcard/Android/media/<applicationId>/`, the only folder the app may write on shared storage. Native code uses `GB_APP_ID`,
  never a typed package name.
- `bash tools/check-app-id.sh` verifies all of it (also run by the `App ID check` workflow). Run it after touching any of them.

## Build and CI
- Builds run in GitHub Actions (`android.yml`, about 4 minutes); there is no Gradle wrapper and local builds are signed with the
  debug key, which cannot update the CI-signed app on the device. Do not uninstall the app to work around that: it deletes
  the user's app data. Install the CI build instead.
- After a push, watch the run (`gh run list/view/watch`). On failure read the annotations
  (`gh api repos/mindspot7/GBSat/check-runs/<job id>/annotations`); fix errors and warnings, then push again. The `profile.yml`,
  `pgo-train.yml`, `screenshots.yml` (commits README images), `vulkan-dev.yml` and `sh2-test.yml` workflows are run by hand.

## Testing on the device (`tools/gbsat.ps1`)
Non-interactive; run as `powershell -NoProfile -ExecutionPolicy Bypass -File tools\gbsat.ps1 <Command> [options]`. Results go to
`test-output\<timestamp>-<command>[-label]\` (git-ignored) and the last line printed is `OUT: <folder>`: read the files there.
Always pass `-Label "<game name>"` when a game is involved.

| Command | Does |
|---|---|
| `Check` | device model, Android, SoC, display, installed app version, running pid |
| `Install [-Run id] [-Profiling]` | newest green CI build (or the profiling build) installed over the current one, then launched |
| `Launch` / `Stop` / `Wake` | start, force-stop, wake and unlock |
| `Logcat -Seconds n [-Clear]` | raw log plus `logcat-app.txt` (app tags, pids), `logcat-errors.txt` and the crash buffer |
| `Crash` | crash buffer and dropbox crash/native-crash/ANR entries |
| `Screenshot` | `screen.png` |
| `PerfLog -Seconds n` | clears and pulls `perf-log.csv` (one row per second) with a `summary.txt` |
| `Scroll -Seconds n` | gfxinfo of the library scroll (jank, percentiles) |
| `Profile -Seconds n` | simpleperf per thread/function (profiling build installed, game running) |
| `PgoPull` | zips the PGO training data |
| `Diag -Key k -Value 0/1` | A/B switches (`sh2_plain`, `vdp2_one_fb`, `vdp_nobatch`, `all`) |

Drive the game with `adb shell input` (keyevents for the pad: DPAD_*, BUTTON_A/B/X/Y/START...). BIOS and games stay on the
device; never copy them into the repo, and never commit `test-output`.

### Reading the results
- Log tags: `GBSat` (native core), `GBSat-SAF` (storage), `GBSat-Disc` / `GBFrame-Disc` (disc loading and the copy fallback),
  `GBSat-RA` (RetroAchievements), `GBNative*` (kit native), plus `AndroidRuntime` (Java crash), `DEBUG` / `libc` (native crash).
- A disc that fails with "file not found" means the descriptor read failed and the app is falling back to a cached copy
  (`GBFrame-Disc: Descriptor read failed`); see `docs/LESSONS.md` in the kit.
- Perf log: compare `shown_fps` / `emu_fps` with the game's target, then `worst_frame_ms`, `slow_frames`, `audio_underruns`,
  `thermal_status`, the per-component `*_ms` / `*_cpu_pct` columns and `settings` (what was on during the run). Change one
  setting at a time and keep the scene the same.
- Destructive device commands (`adb uninstall`, `pm clear`, `rm`) are denied in `.claude/settings.json`; ask the owner.
