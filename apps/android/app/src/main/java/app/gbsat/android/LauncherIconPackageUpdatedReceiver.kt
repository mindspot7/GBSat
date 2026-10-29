package app.gbsat.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Select the device-specific icon after an app update, before the user launches the activity. */
class LauncherIconPackageUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        LauncherIconCompatibility.apply(context, Build.MANUFACTURER, Build.MODEL)
    }
}
