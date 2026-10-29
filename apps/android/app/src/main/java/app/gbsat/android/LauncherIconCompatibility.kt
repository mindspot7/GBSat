package app.gbsat.android

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The RG DS launcher puts a white plate behind transparent legacy icons. Select an opaque,
 * black-backed launcher alias only on that device; every other Android device keeps the
 * transparent launcher resource declared as the app's default.
 */
internal object LauncherIconCompatibility {
    private const val DEFAULT_LAUNCHER = "app.gbsat.emulator.LauncherActivity"
    private const val RG_DS_LAUNCHER = "app.gbsat.emulator.RgDsLauncherActivity"

    fun apply(context: Context, manufacturer: String, model: String) {
        val useRgDsIcon = shouldUseRgDsIcon(manufacturer, model)
        setAliasEnabled(context, RG_DS_LAUNCHER, enabled = useRgDsIcon, manifestDefault = false)
        setAliasEnabled(context, DEFAULT_LAUNCHER, enabled = !useRgDsIcon, manifestDefault = true)
    }

    internal fun shouldUseRgDsIcon(manufacturer: String, model: String): Boolean =
        manufacturer.trim().equals("Anbernic", ignoreCase = true) &&
            model.trim().equals("RG DS", ignoreCase = true)

    private fun setAliasEnabled(context: Context, name: String, enabled: Boolean, manifestDefault: Boolean) {
        val component = ComponentName(context.packageName, name)
        val packageManager = context.packageManager
        val componentState = packageManager.getComponentEnabledSetting(component)
        val currentlyEnabled = when (componentState) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> manifestDefault
        }
        if (currentlyEnabled != enabled) {
            packageManager.setComponentEnabledSetting(
                component,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
