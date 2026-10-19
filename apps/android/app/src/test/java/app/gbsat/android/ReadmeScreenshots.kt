package app.gbsat.android

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.gbsat.android.ui.*
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the app screens used in the README (docs/screenshots). Run with:
 *   gradle :app:testDebugUnitTest --tests '*ReadmeScreenshots*'
 * The library uses sample games with original placeholder covers (no real box art).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = android.app.Application::class, qualifiers = "w853dp-h480dp-land-xhdpi")
class ReadmeScreenshots {
    @get:Rule val rule = createComposeRule()
    private val out = System.getProperty("screenshotDir") ?: "build/screenshots"

    private fun setField(name: String, value: Any?) {
        val f = Emulator::class.java.getDeclaredField(name); f.isAccessible = true; f.set(Emulator, value)
    }
    @Suppress("UNCHECKED_CAST")
    private fun setState(name: String, value: Any?) {
        val f = Emulator::class.java.getDeclaredField("$name\$delegate"); f.isAccessible = true
        (f.get(Emulator) as MutableState<Any?>).value = value
    }

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        setField("appContext", ctx)
        setField("prefs", ctx.getSharedPreferences("gbsat", Context.MODE_PRIVATE))
        val data = File(ctx.filesDir, "data").apply { mkdirs() }
        setField("dataDir", data)
        Covers.init(data)
        val games = File(ctx.filesDir, "games").apply { mkdirs() }
        File(javaClass.getResource("/covers")!!.toURI()).listFiles()!!.sorted().forEach { png ->
            val title = png.nameWithoutExtension
            File(games, "$title.chd").writeText("x")
            png.copyTo(File(data, "covers/custom/$title.img"), overwrite = true)
        }
        Emulator.gamesDir = games.path
        setState("iplLoaded", true)
        Emulator.setupDone = true
        Emulator.renderer = 4; Emulator.resScale = 4; Emulator.shader = 1; Emulator.filter = 1; Emulator.sh2Cache = true
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit, action: (() -> Unit)? = null) {
        rule.setContent { GBSatTheme { content() } }
        rule.waitForIdle(); Thread.sleep(300); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        action?.let { it(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle() }
        rule.onRoot().captureRoboImage("$out/$name.png")
    }

    @Test fun library() = shot("library", { LibraryScreen({}, {}, {}, {}, {}) })
    @Test fun menu() = shot("menu", { LibraryScreen({}, {}, {}, {}, {}) }) { rule.onNodeWithText("GBSat").performClick() }
    @Test fun wizard() = shot("setup", { SetupWizard({}, {}) }) {
        repeat(5) { rule.onNodeWithText("Next").performClick(); rule.mainClock.advanceTimeBy(300) }
    }
    @Test fun video() = shot("settings-video", { SettingsScreen({}, {}, {}, SettingsPage.Video) })
    @Test fun controls() = shot("settings-controls", { SettingsScreen({}, {}, {}, SettingsPage.Controls) })
    @Test fun performance() = shot("settings-performance", { SettingsScreen({}, {}, {}, SettingsPage.Performance) })
    @Test fun extras() = shot("settings-extras", { SettingsScreen({}, {}, {}, SettingsPage.Extras) })
}
