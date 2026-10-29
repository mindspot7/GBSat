package app.gbsat.android.ui

import gb.frame.MenuState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.gbsat.android.Emulator
import app.gbsat.android.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { Library, Settings }

@Composable
fun GBSatRoot(onImmersive: (Boolean) -> Unit, onQuitApp: () -> Unit) {
    GBSatTheme {
        if (!Emulator.initialized && Emulator.initializationError == null) {
            gb.frame.AppStartScreen(app.gbsat.emulator.R.drawable.ic_launcher_foreground)
            return@GBSatTheme
        }
        if (!Emulator.initialized) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (Emulator.initializationError == null) CircularProgressIndicator()
                    Text(Emulator.initializationError ?: "Opening data folder…")
                    if (Emulator.initializationError != null) gb.frame.DataFolderControls(Emulator.dataFolder)
                }
            }
            return@GBSatTheme
        }
        gb.frame.DataFolderWarning(Emulator.dataFolder)
        var screen by remember { mutableStateOf(Screen.Library) }
        var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val inGame = Emulator.running
        // Game requested by an intent (frontends, `am start`): matched to the library by file name, then started like a
        // tap on its cover (auto load included)
        val requested = gb.frame.LaunchIntent.pending.value
        LaunchedEffect(requested, Emulator.setupDone) {
            if (requested == null || !Emulator.setupDone) return@LaunchedEffect
            gb.frame.LaunchIntent.done() // changes this effect's key: the work runs in the root scope so it isn't cancelled
            scope.launch {
                val games = withContext(Dispatchers.IO) {
                    Storage.cachedScan(Emulator.gamesDir).ifEmpty { Storage.scan(Emulator.gamesDir) }
                }
                val game = gb.frame.LaunchIntent.resolve(requested, games)
                // Another game running: close it the way Exit does (auto save, back to the global options) first
                if (game != null && Emulator.running) Emulator.endGame()
                val err = if (game == null) gb.frame.LaunchIntent.notFound(requested) else Emulator.launch(game.path)
                err?.let { if (Emulator.running) Emulator.showToast(it) else snackbar.showSnackbar(it) }
            }
        }

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
