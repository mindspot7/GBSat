# GBSat

Sega Saturn emulator for Android, based on [Ymir](https://github.com/StrikerX3/Ymir) by StrikerX3 and contributors.

This repository is a trimmed fork that contains only what is needed to build the Android app:

| Path | What |
| --- | --- |
| `apps/ymir-android` | Android app (Kotlin/Compose UI + JNI bridge). See its README for usage. |
| `libs/ymir-core` | Unmodified Ymir emulator core. |
| `vendor/`, `cmake/`, `CMakeLists.txt` | Core dependencies (git submodules) and build scripts. |
| `.github/workflows/android.yml` | GitHub Actions workflow that builds the APK. |

## Getting the APK

Push to the repository (or run the **Android APK** workflow manually from the Actions tab), then download the
`gbsat-arm64` artifact from the finished run and install the `.apk` on an Android 9+ arm64 device.

## License

GPLv3, same as Ymir. See `LICENSE`.
