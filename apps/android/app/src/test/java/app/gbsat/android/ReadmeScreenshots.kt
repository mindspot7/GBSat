package app.gbsat.android

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
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
abstract class ScreenTest {
    @get:Rule val rule = createComposeRule()
    protected open val out: String = System.getProperty("screenshotDir") ?: "build/screenshots"

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
        gb.frame.Covers.init(data, "Sega - Saturn")
        Storage.init(ctx)
        val games = File(ctx.filesDir, "games").apply { mkdirs() }
        File(javaClass.getResource("/covers")!!.toURI()).listFiles()!!.sorted().forEach { png ->
            val title = png.nameWithoutExtension
            File(games, "$title.chd").writeText("x")
            png.copyTo(File(data, "covers/custom/$title.img"), overwrite = true)
        }
        Emulator.gamesDir = games.path
        setState("iplLoaded", true)
        Emulator.setupDone = true
        MenuState.open.value = false; MenuState.selected = null
        Emulator.renderer = 4; Emulator.resScale = 4; Emulator.shader = 1; Emulator.filter = 1; Emulator.sh2Cache = true
    }

    protected fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit, action: (() -> Unit)? = null) {
        rule.setContent { GBSatTheme { content() } }
        rule.waitForIdle(); Thread.sleep(300); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        action?.let { it(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle() }
        rule.onRoot().captureRoboImage("$out/$name.png")
    }
}

/** README screenshots (docs/screenshots). */
class ReadmeScreenshots : ScreenTest() {
    @Test fun library() = shot("library", { LibraryScreen({}, {}, {}, {}) })
    @Test fun menu() = shot("menu", { LibraryScreen({}, {}, {}, {}) }) { rule.onNodeWithText("GBSat").performClick() }
    @Test fun wizard() = shot("setup", { SetupWizard({}) }) {
        // Through to the last step (the preset picker)
        while (rule.onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Next").performClick(); rule.mainClock.advanceTimeBy(300); rule.waitForIdle()
        }
    }
    @Test fun video() = shot("settings-video", { SettingsScreen({}, {}, SettingsPage.Video) })
    @Test fun controls() = shot("settings-controls", { SettingsScreen({}, {}, SettingsPage.Controls) })
    @Test fun performance() = shot("settings-performance", { SettingsScreen({}, {}, SettingsPage.Performance) })
    @Test fun extras() = shot("settings-extras", { SettingsScreen({}, {}, SettingsPage.Extras) })
}

/**
 * Layout check on the screens of the most common handhelds (not committed: uploaded as the "device-screens" artifact).
 * Densities are estimates; 640x480 is checked at mdpi (also 1280x960 at xhdpi) and hdpi.
 */
class DeviceScreens : ScreenTest() {
    override val out = "build/device-screens"

    private fun library(d: String) = shot("$d-library", { LibraryScreen({}, {}, {}, {}) })
    private fun menu(d: String) = shot("$d-menu", { LibraryScreen({}, {}, {}, {}) }) { rule.onNodeWithText("GBSat").performClick() }
    private fun video(d: String) = shot("$d-video", { SettingsScreen({}, {}, SettingsPage.Video) })
    private fun setup(d: String) = shot("$d-setup", { SetupWizard({}) }) {
        while (rule.onAllNodesWithText("Next").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Next").performClick(); rule.mainClock.advanceTimeBy(300); rule.waitForIdle()
        }
    }

    // Rockchip RK3566 handhelds: 640x480 (mdpi and hdpi)
    @Test @Config(qualifiers = "w640dp-h480dp-land-mdpi") fun s640mLibrary() = library("640x480-mdpi")
    @Test @Config(qualifiers = "w640dp-h480dp-land-mdpi") fun s640mMenu() = menu("640x480-mdpi")
    @Test @Config(qualifiers = "w427dp-h320dp-land-hdpi") fun s640hLibrary() = library("640x480-hdpi")
    @Test @Config(qualifiers = "w427dp-h320dp-land-hdpi") fun s640hMenu() = menu("640x480-hdpi")
    @Test @Config(qualifiers = "w427dp-h320dp-land-hdpi") fun s640hVideo() = video("640x480-hdpi")
    @Test @Config(qualifiers = "w427dp-h320dp-land-hdpi") fun s640hSetup() = setup("640x480-hdpi")
    // 3:4 portrait 720x960 (Unisoc T820 handhelds)
    @Test @Config(qualifiers = "w360dp-h480dp-port-xhdpi") fun p34Library() = library("720x960")
    @Test @Config(qualifiers = "w360dp-h480dp-port-xhdpi") fun p34Menu() = menu("720x960")
    @Test @Config(qualifiers = "w360dp-h480dp-port-xhdpi") fun p34Video() = video("720x960")
    @Test @Config(qualifiers = "w360dp-h480dp-port-xhdpi") fun p34Setup() = setup("720x960")
    // Square 720x720: 3.95" (hdpi) and 3.5" (xhdpi)
    @Test @Config(qualifiers = "w480dp-h480dp-land-hdpi") fun sq480Library() = library("720x720-hdpi")
    @Test @Config(qualifiers = "w480dp-h480dp-land-hdpi") fun sq480Menu() = menu("720x720-hdpi")
    @Test @Config(qualifiers = "w360dp-h360dp-land-xhdpi") fun sq360Library() = library("720x720-xhdpi")
    @Test @Config(qualifiers = "w360dp-h360dp-land-xhdpi") fun sq360Menu() = menu("720x720-xhdpi")
    @Test @Config(qualifiers = "w360dp-h360dp-land-xhdpi") fun sq360Setup() = setup("720x720-xhdpi")
    // Near-square 1240x1080 and 1920x1080
    @Test @Config(qualifiers = "w472dp-h411dp-land-xxhdpi") fun sqLibrary() = library("1240x1080")
    @Test @Config(qualifiers = "w472dp-h411dp-land-xxhdpi") fun sqMenu() = menu("1240x1080")
    @Test @Config(qualifiers = "w731dp-h411dp-land-420dpi") fun fhdLibrary() = library("1920x1080")
    @Test @Config(qualifiers = "w731dp-h411dp-land-420dpi") fun fhdMenu() = menu("1920x1080")
}
