package app.gbsat.android

import android.app.Application

class GBSatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        @Suppress("DEPRECATION") externalMediaDirs // creates Android/media/<app> (PGO training build output)
        Thread({
            try {
                Emulator.init(this)
                // Saturn owns its artwork ratio and aliases; GBFrame remains console-neutral.
                gb.frame.Covers.init(Emulator.dataDir, "Sega - Saturn", mapOf(
                    "sakura wars" to "sakura taisen", "sakura wars 2" to "sakura taisen 2", "dragon force 2" to "dragon force ii",
                    "langrisser 3" to "langrisser iii",
                ), frameAspectRatio = 0.66f)
                Emulator.initialized = true
            } catch (failure: Exception) {
                Emulator.initializationError = failure.message ?: "Could not initialize app data"
            }
        }, "gbsat-startup").start()
    }
}
