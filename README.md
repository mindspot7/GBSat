# GBSat

Sega Saturn emulator for Android.

| Path | What |
| --- | --- |
| `apps/android` | Android app (Kotlin/Compose UI + JNI bridge). |
| `libs/ymir-core` | Emulator core. |
| `vendor/`, `cmake/`, `CMakeLists.txt` | Core dependencies (git submodules) and build scripts. |
| `.github/workflows/android.yml` | GitHub Actions workflow that builds the APK. |

## Versions

The release version lives in `apps/android/VERSION`. Every CI build adds its build number, so the APK is named
`GBSat-v<version>-build<N>.apk` and shows `v<version> (build N)` in Settings. See `CHANGELOG.md`.

## Getting the APK

Push to the repository (or run the **Android APK** workflow manually from the Actions tab), then download the
`GBSat-v…` artifact from the finished run and install the `.apk` on an Android 9+ arm64 device.

## License

GPLv3. See `LICENSE`.
