package app.gbsat.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.gbsat.android.Emulator
import kotlinx.coroutines.launch

private enum class Screen { Library, Settings }

@Composable
fun GBSatRoot(onImmersive: (Boolean) -> Unit, onQuitApp: () -> Unit) {
    GBSatTheme {
        var screen by remember { mutableStateOf(Screen.Library) }
        var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val inGame = Emulator.running

        LaunchedEffect(inGame) { onImmersive(true) } // the whole app runs full screen

        Emulator.lastCrash?.let { report ->
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            AlertDialog(
                onDismissRequest = { Emulator.lastCrash = null },
                title = { Text("Crash report") },
                text = {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(report, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                    }
                },
                confirmButton = { TextButton(onClick = { Emulator.lastCrash = null }) { Text("OK") } },
                dismissButton = {
                    TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(report)) }) { Text("Copy") }
                },
            )
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (inGame) {
                GameScreen(onQuitApp)
            } else if (!Emulator.setupDone) {
                SetupWizard(onFinish = { Emulator.finishSetup() })
            } else {
                BackHandler(screen == Screen.Settings) {
                    screen = Screen.Library
                    if (MenuState.open.value) {
                        MenuState.menuHasFocus = false
                        MenuState.menuRefocus.intValue++
                    }
                }
                Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (screen) {
                            Screen.Library -> LibraryScreen(
                                onOpenSettings = { settingsPage = it; screen = Screen.Settings },
                                onQuit = onQuitApp,
                                launch = { path ->
                                    scope.launch {
                                        Emulator.launch(path)?.let { snackbar.showSnackbar(it) }
                                    }
                                },
                                launchState = { st ->
                                    scope.launch { Emulator.launchState(st)?.let { snackbar.showSnackbar(it) } }
                                },
                                open = { path -> scope.launch { Emulator.launch(path, useAutoLoad = false)?.let { snackbar.showSnackbar(it) } } },
                            )
                            Screen.Settings -> SettingsScreen(
                                onBack = {
                                    screen = Screen.Library
                                    // LibraryScreen is rebuilt; force the selected settings tile to reclaim focus.
                                    if (MenuState.open.value) {
                                        MenuState.menuHasFocus = false
                                        MenuState.menuRefocus.intValue++
                                    }
                                },
                                toast = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                                page = settingsPage,
                            )
                        }
                    }
                }
            }
        }
    }
}
