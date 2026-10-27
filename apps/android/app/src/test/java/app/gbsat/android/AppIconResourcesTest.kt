package app.gbsat.android

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
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
        val launcher = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_controller_transparent)
        try { assertEquals(0, Color.alpha(launcher.getPixel(0, 0))) } finally { launcher.recycle() }
        for (resource in listOf(R.mipmap.ic_launcher, R.mipmap.ic_gbsat_controller)) {
            val icon = context.getDrawable(resource) as AdaptiveIconDrawable
            assertEquals(0, Color.alpha((icon.background as ColorDrawable).color))
        }
        val fallback = context.getDrawable(R.mipmap.ic_launcher_opaque) as AdaptiveIconDrawable
        assertEquals(Color.BLACK, (fallback.background as ColorDrawable).color)
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
}
