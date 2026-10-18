package app.ymir.android

import android.app.Application

class GBSatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Emulator.init(this)
        Covers.init(this)
    }
}
