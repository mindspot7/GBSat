package app.gbsat.android

import android.app.Application

class GBSatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Emulator.init(this)
        // Box art from libretro-thumbnails; Western titles of games listed under their Japanese name
        gb.ui.Covers.init(Emulator.dataDir, "Sega - Saturn", mapOf(
            "sakura wars" to "sakura taisen", "sakura wars 2" to "sakura taisen 2", "dragon force 2" to "dragon force ii",
            "langrisser 3" to "langrisser iii",
        ))
    }
}
