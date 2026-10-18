package app.gbsat.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.gbsat.android.Emulator
import kotlinx.coroutines.launch

private enum class Screen { Library, Settings }

@Composable
fun GBSatRoot(onRequestAllFiles: () -> Unit, onImmersive: (Boolean) -> Unit, onQuitApp: () -> Unit) {
    GBSatTheme {
        var screen by remember { mutableStateOf(Screen.Library) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val inGame = Emulator.running

        LaunchedEffect(inGame) { onImmersive(inGame) }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (inGame) {
                GameScreen(onQuitApp)
            } else {
                BackHandler(screen == Screen.Settings) { screen = Screen.Library }
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (screen) {
                            Screen.Library -> LibraryScreen(
                                onOpenSettings = { screen = Screen.Settings },
                                onRequestAllFiles = onRequestAllFiles,
                                launch = { path ->
                                    scope.launch {
                                        Emulator.launch(path)?.let { snackbar.showSnackbar(it) }
                                    }
                                },
                            )
                            Screen.Settings -> SettingsScreen(
                                onBack = { screen = Screen.Library },
                                onRequestAllFiles = onRequestAllFiles,
                                toast = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                            )
                        }
                    }
                }
            }
        }
    }
}
