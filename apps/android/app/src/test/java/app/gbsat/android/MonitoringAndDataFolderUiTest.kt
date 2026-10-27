package app.gbsat.android

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import gb.frame.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MonitoringAndDataFolderUiTest {
    @get:Rule val compose = createComposeRule()
    private class Settings : MonitoringSettings {
        override var options by mutableStateOf(PerfOptions())
        override var showFfButton by mutableStateOf(false)
        override fun update(options: PerfOptions, showFfButton: Boolean) { this.options = options; this.showFfButton = showFfButton }
    }
    @Test fun fastForwardActionDisappearsWhenOverlayButtonIsEnabled() {
        val settings = Settings()
        compose.setContent {
            val page = remember { QuickMenuPage("test", "Paused") {
                MonitoringQuickMenuRows(settings)
                FastForwardQuickMenuAction(settings, false) {}
            } }
            QuickMenuHost(page, "Game", {})
        }
        compose.onNodeWithText("Fast-forward", useUnmergedTree = true).assertExists()
        compose.runOnIdle { settings.set("fastForwardButton", true) }
        compose.onNodeWithText("Fast-forward", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Show fast-forward button", useUnmergedTree = true).assertExists()
    }
    @Test fun sharedWizardIncludesOptionalDataFolderAndChooseAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = AppDataFolder(context, "Test", mapOf("saves" to java.io.File(context.filesDir, "folder-ui-test")))
        compose.setContent { SetupWizard(listOf(dataFolderWizardStep(folder)), {}) }
        compose.onAllNodesWithText("Data folder", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("Choose", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Start", useUnmergedTree = true).assertExists()
    }
}
