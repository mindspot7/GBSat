// Version: apps/android/VERSION holds the release version (x.y.z); CI adds the build number (GitHub run number).
val gbsatVersion = rootProject.file("VERSION").readText().trim()
val gbsatBuild = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Keep the source namespace independent from the install/package identity.
    namespace = "app.gbsat.android"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "app.gbsat.emulator"
        minSdk = 28 // iconv in libc, AAudio, ANativeWindow
        targetSdk = 36
        versionCode = gbsatBuild
        versionName = "$gbsatVersion (build $gbsatBuild)"

        // The emulator core's hot paths are NEON-optimized; 32-bit ARM and x86 are not supported.
        ndk { abiFilters += "arm64-v8a" }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_CXX_SCAN_FOR_MODULES=OFF",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DYmir_EXTRA_INLINING=ON",
                    "-DYmir_ENABLE_IPO=ON",
                    // 16 KB memory pages (required by Google Play for native code on Android 15+)
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                )
                // PGO training build (-PgbsatPgoGen): instrumented code that records which paths run most
                if (project.hasProperty("gbsatPgoGen")) arguments += "-DGBSAT_PGO_GEN=ON"
                // Profiling build: line tables too, so simpleperf can split time by source line (--sort srcline)
                if (project.hasProperty("gbsatProfile")) arguments += "-DGBSAT_LINE_TABLES=ON"
                // CI: reuse compiled objects between builds (ccache), so a small change recompiles only what it touches
                if (project.hasProperty("gbsatCcache")) {
                    arguments += listOf("-DCMAKE_C_COMPILER_LAUNCHER=ccache", "-DCMAKE_CXX_COMPILER_LAUNCHER=ccache")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    signingConfigs {
        // Permanent release key: CI decodes the GBSAT_KEYSTORE_BASE64 secret to GBSAT_KEYSTORE_FILE. Every APK signed
        // with it installs over the previous one. Without it (forks, local builds) the debug keystore is used.
        val releaseKey = System.getenv("GBSAT_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
        create("sideload") {
            if (releaseKey != null) {
                storeFile = releaseKey
                storePassword = System.getenv("GBSAT_KEYSTORE_PASSWORD")
                keyAlias = "gbsat"
                keyPassword = System.getenv("GBSAT_KEYSTORE_PASSWORD")
            } else {
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        jniLibs {
            useLegacyPackaging = false
            // Profiling build (-PgbsatProfile): keep the native symbols so simpleperf reports function names
            if (project.hasProperty("gbsatProfile")) keepDebugSymbols += "**/*.so"
        }
    }
    // README screenshots (Robolectric + Roborazzi), see ReadmeScreenshots.kt
    testOptions.unitTests {
        isIncludeAndroidResources = true
        all {
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("screenshotDir", rootProject.file("../../docs/screenshots").absolutePath)
            it.maxHeapSize = "3g"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")
    // Installs the baseline profiles (Compose's own and src/main/baseline-prof.txt) on sideloaded APKs, so the UI runs
    // compiled instead of interpreted right after an install (the library scrolled at ~33 fps on the RG Rotate: Nterp)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation(project(":gbframe"))

    testImplementation(composeBom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.espresso:espresso-core:3.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
