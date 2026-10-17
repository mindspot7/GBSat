package app.ymir.android

import android.app.Application

class YmirApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Emulator.init(this)
    }
}
