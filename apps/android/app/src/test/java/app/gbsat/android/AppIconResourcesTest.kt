package app.gbsat.android

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import app.gbsat.emulator.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AppIconResourcesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun launcherDefaultsToTransparencyAndKeepsBlackOnlyAsExplicitFallback() {
        assertEquals(R.mipmap.ic_controller_transparent, context.applicationInfo.icon)
        assertTrue(context.getDrawable(R.mipmap.ic_controller_transparent) is BitmapDrawable)
        for (resource in listOf(R.mipmap.ic_launcher, R.mipmap.ic_gbsat_controller, R.mipmap.ic_controller_adaptive)) {
            val icon = context.getDrawable(resource) as AdaptiveIconDrawable
            assertEquals(0, Color.alpha((icon.background as ColorDrawable).color))
        }
        val fallback = context.getDrawable(R.mipmap.ic_launcher_opaque) as AdaptiveIconDrawable
        assertEquals(Color.BLACK, (fallback.background as ColorDrawable).color)
    }

    @Test fun adaptiveLauncherAliasUsesConsumerActivityAndTransparentResource() {
        val alias = context.packageManager.getActivityInfo(
            ComponentName(context.packageName, "app.gbsat.android.AdaptiveLauncherActivity"),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertEquals("app.gbsat.android.MainActivity", alias.targetActivity)
        assertEquals(R.mipmap.ic_controller_adaptive, alias.icon)
    }

    @Test fun inAppArtworkHasTransparentCanvasInsteadOfLauncherBadge() {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
        try {
            for ((x, y) in listOf(0 to 0, bitmap.width / 2 to 0, 0 to bitmap.height / 2,
                bitmap.width - 1 to bitmap.height / 2, bitmap.width / 2 to bitmap.height - 1)) {
                assertEquals("Unexpected background at $x,$y", 0, Color.alpha(bitmap.getPixel(x, y)))
            }
            assertTrue(Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 0)
        } finally { bitmap.recycle() }
    }

    @Test fun standaloneLauncherKeepsUserApprovedLegacySizeAndProportions() {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val launcher = BitmapFactory.decodeFile("src/main/res/mipmap-xxxhdpi/ic_controller_transparent.png", options)
        val foreground = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground, options)
        fun bounds(bitmap: android.graphics.Bitmap): Pair<Int, Int> {
            var left = bitmap.width; var right = -1; var top = bitmap.height; var bottom = -1
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 8) {
                    left = minOf(left, x); right = maxOf(right, x)
                    top = minOf(top, y); bottom = maxOf(bottom, y)
                }
            }
            return (right - left + 1) to (bottom - top + 1)
        }
        try {
            val (width, height) = bounds(launcher)
            val (sourceWidth, sourceHeight) = bounds(foreground)
            assertTrue("Approved legacy icon must retain its larger composition", width.toFloat() / launcher.width in .90f..1f)
            assertEquals("Controller must retain its proportions", sourceWidth.toFloat() / sourceHeight, width.toFloat() / height, .08f)
            assertEquals(0, Color.alpha(launcher.getPixel(0, 0)))
        } finally { launcher.recycle(); foreground.recycle() }
    }
}
