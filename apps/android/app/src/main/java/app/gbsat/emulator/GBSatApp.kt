package app.gbsat.emulator

import android.app.Application

class GBSatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        gb.frame.LauncherIconCompatibility.applyFromMetadata(this)
        @Suppress("DEPRECATION") externalMediaDirs // creates Android/media/<app> (PGO training build output)
        Thread({
            try {
                Emulator.init(this)
                // Saturn owns its artwork ratio and aliases; GBFrame remains console-neutral.
                gb.frame.Covers.init(Emulator.dataDir, "Sega - Saturn", mapOf(
                    "sakura wars" to "sakura taisen", "sakura wars 2" to "sakura taisen 2", "dragon force 2" to "dragon force ii",
                    "langrisser 3" to "langrisser iii",
                ), frameAspectRatio = 0.66f)
                // What the library's first frames need, ready before it shows (no decoding while it animates)
                gb.frame.preloadAppIcon(resources, app.gbsat.emulator.R.drawable.ic_launcher_foreground)
                gb.frame.Covers.preloadCached(Storage.cachedScan(Emulator.gamesDir).take(40).map { it.title })
                Emulator.initialized = true
            } catch (failure: Exception) {
                Emulator.initializationError = failure.message ?: "Could not initialize app data"
            }
        }, "gbsat-startup").start()
    }
}
