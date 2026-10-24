package app.gbsat.android.ui

import gb.frame.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.gbsat.android.Emulator
import app.gbsat.android.R
import app.gbsat.android.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen: top bar (app icon, name, clock, battery), the game library in the theme chosen in Settings → Extras
 * (gb.frame.HomeLibrary: Showcase by default, Grid or Continue) and the main menu (gb.frame.HomeMenuPanel: library view,
 * settings, quit), which slides in from the left. Works with touch and with a controller: the D-pad moves the
 * selection, A opens, B goes back, X opens the menu, and Y (or a long press) opens the game options.
 */
@Composable
fun LibraryScreen(
    onOpenSettings: (SettingsPage?) -> Unit,
    launch: (String) -> Unit,
    launchState: (Emulator.RecentState) -> Unit,
    onQuit: () -> Unit,
    open: (String) -> Unit = launch,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by MenuState.open
    var recentMode by MenuState.recent
    val style by HomeTheme.style
    val fullscreen by HomeTheme.fullscreen
    val menuFocus = remember { FocusRequester() }
    val hasPad = rememberHasGamepad()
    // Menu opened: with a controller its first entry (or the page just closed) is selected, even when it was opened by a
    // tap (leave touch mode first: Compose won't focus clickable items there); closed: the focus goes back to the library
    val composeView = LocalView.current
    suspend fun focusMenu() {
        repeat(5) {
            if (MenuState.menuHasFocus) return
            composeView.requestFocusFromTouch()
            runCatching { menuFocus.requestFocus() }
            delay(60)
        }
    }
    LaunchedEffect(menuOpen, hasPad) {
        if (menuOpen) { if (hasPad) { delay(120); focusMenu() } } else if (hasPad) MenuState.refocus.intValue++
    }
    LaunchedEffect(MenuState.menuRefocus.intValue) { if (menuOpen && hasPad) focusMenu() }
    // MainActivity handles X (menu) and gives the focus back to the library when a key comes while nothing has it
    DisposableEffect(Unit) { MenuState.libraryShown = true; onDispose { MenuState.libraryShown = false; MenuState.hasFocus = false } }
    var confirmQuit by MenuState.quitOpen
    MenuState.onQuit = onQuit

    // ---- Data: games, save states, play time ----
    var games by remember { mutableStateOf<List<gb.frame.Game>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var openingGame by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var states by remember { mutableStateOf<List<Emulator.RecentState>>(emptyList()) }
    LifecycleResumeEffect(Unit) { refreshKey++; onPauseOrDispose { } }
    LaunchedEffect(Emulator.gamesDir, refreshKey) {
        if (Emulator.gamesDir.isBlank()) { games = emptyList(); return@LaunchedEffect }
        loading = games.isEmpty()
        games = withContext(Dispatchers.IO) { Storage.scan(Emulator.gamesDir) }
        loading = false
    }
    LaunchedEffect(refreshKey) { states = withContext(Dispatchers.IO) { Emulator.recentStates() } }
    val entries = remember(states) {
        states.map { s ->
            ResumeEntry(s.gamePath + "#" + s.slot, s.gamePath.substringAfterLast('/').substringBeforeLast('.'),
                when (s.slot) { Emulator.QUICK_SLOT -> "Quick save"; Emulator.AUTO_SLOT -> "Auto save"; else -> "Slot ${s.slot + 1}" },
                s.time, s.thumb, game = s.gamePath)
        }
    }
    val homeGames = remember(games, Emulator.achievementGames, refreshKey) {
        games.map { HomeGame(it.path, it.title, Emulator.playTime(it.path), it.path in Emulator.achievementGames) }
    }

    // ---- Game options, achievements, custom cover ----
    var gameMenu by remember { mutableStateOf<HomeGame?>(null) }
    var achievementsFor by remember { mutableStateOf<String?>(null) }
    var coverVersion by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val g = gameMenu
        if (uri != null && g != null) scope.launch { Covers.setCustom(ctx, g.title, uri); coverVersion++ }
        gameMenu = null
    }

    // B / Back: close the menu, leave Recent, otherwise ask before quitting
    // Some controllers deliver B twice (as BUTTON_B mapped to Back and as a system Back): one press must act only once,
    // otherwise the quit dialog opens and immediately closes again
    var lastBack by remember { mutableLongStateOf(0L) }
    androidx.activity.compose.BackHandler {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBack < 300) return@BackHandler
        lastBack = now
        when {
            confirmQuit -> confirmQuit = false
            achievementsFor != null -> achievementsFor = null
            menuOpen -> menuOpen = false
            else -> confirmQuit = true
        }
    }

    fun showLaunchLoading(action: () -> Unit) {
        if (openingGame) return
        openingGame = true
        action()
        scope.launch {
            // The launch callback starts the emulator coroutine. Keep the input-blocking overlay until the
            // emulator reports that it is running; the timeout also lets the library recover after a failed launch.
            repeat(900) {
                if (Emulator.running) {
                    delay(350)
                    openingGame = false
                    return@launch
                }
                delay(100)
            }
            openingGame = false
        }
    }
    val openPage: (SettingsPage) -> Unit = { MenuState.selected = it; onOpenSettings(it) }
    val actions = HomeActions(
        play = { MenuState.selected = null; showLaunchLoading { launch(it.key) } },
        fresh = { MenuState.selected = null; showLaunchLoading { open(it.key) } },
        options = { if (!openingGame) gameMenu = it },
        resume = { e -> states.firstOrNull { it.gamePath + "#" + it.slot == e.key }?.let { MenuState.selected = null; showLaunchLoading { launchState(it) } } },
        openMenu = { menuOpen = true },
    )
    val toSystem = { openPage(SettingsPage.System) }
    val emptyKind = when {
        !Emulator.iplLoaded -> EmptyKind.Bios
        Emulator.gamesDir.isBlank() -> EmptyKind.Folder
        games.isEmpty() -> if (loading) EmptyKind.Loading else EmptyKind.NoGames
        else -> null
    }
    val empty: (@Composable () -> Unit)? = if (emptyKind == null) null else ({ EmptyContent(emptyKind, toSystem) })

    Box(Modifier.fillMaxSize().background(AppBackground).onFocusChanged { MenuState.hasFocus = it.hasFocus }.onPreviewKeyEvent { e ->
        if (openingGame) return@onPreviewKeyEvent true
        // Quit dialog open: it owns the controller. A confirms, B/Back cancels; acting on key release and consuming the
        // press too, so the same press can't also reach the library or the system Back
        if (confirmQuit) {
            val up = e.type == KeyEventType.KeyUp
            when (e.key) {
                Key.ButtonA, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (up) { confirmQuit = false; onQuit() } }
                Key.ButtonB, Key.Back, Key.Escape -> { if (up) confirmQuit = false }
                else -> {}
            }
            return@onPreviewKeyEvent true
        }
        false
    }) {
        Column(Modifier.fillMaxSize().then(if (fullscreen) Modifier.padding(horizontal = 10.dp, vertical = 8.dp) else Modifier.safeDrawingPadding().padding(12.dp)),
            verticalArrangement = Arrangement.spacedBy(if (fullscreen) 0.dp else 12.dp)) {
            if (!fullscreen) HomeTopBar(painterResource(R.mipmap.ic_launcher), "GBSat", onClick = {
                if (!menuOpen) MenuState.selected = null
                menuOpen = !menuOpen
            }, menuOpen = menuOpen)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                HomeLibrary(style, homeGames, entries, recentMode, actions, coverVersion, empty,
                    refocus = MenuState.refocus.intValue, fullscreen = fullscreen)
                achievementsFor?.let { path ->
                    // Drawn over the library (same window), so the controller mapping of the main window applies
                    Box(Modifier.zIndex(10f).fillMaxSize()) {
                        AchievementsScreen(onBack = { achievementsFor = null }, load = { RetroAchievements.gameAchievements(path) })
                    }
                }
            }
            if (!fullscreen && hasPad && HomeTheme.buttonHelp.value) HelpBar(recentMode)

        }
        if (fullscreen && !menuOpen && !openingGame) {
            Box(Modifier.align(Alignment.CenterStart).zIndex(5f).padding(start = 2.dp)
                .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                .background(Color(0xCC171329)).clickable {
                    if (!menuOpen) MenuState.selected = null
                    menuOpen = !menuOpen
                }.padding(horizontal = 5.dp, vertical = 12.dp)) {
                Icon(Icons.Default.Menu, contentDescription = "Open menu", tint = Gold, modifier = Modifier.size(18.dp))
            }
        }
        // Main menu: slides in from the left over everything (X, or a tap on the header/handle). It stays open while a settings
        // page is shown, so Back returns to it with that entry selected
        HomeMenuPanel(
            open = menuOpen, onDismiss = { menuOpen = false }, focus = menuFocus,
            appIcon = painterResource(R.mipmap.ic_launcher), appName = "GBSat",
            recent = recentMode, gamesCount = games.size, recentCount = entries.distinctBy { it.game }.size,
            onShowRecent = { recentMode = it; menuOpen = false },
            items = SettingsEntries.filter { it.third != SettingsPage.About }.map { (icon, label, page) ->
                HomeMenuItem(icon, label, selected = MenuState.selected == page) { openPage(page) }
            },
            extras = listOf(HomeMenuItem(MenuIcon.About, "About", selected = MenuState.selected == SettingsPage.About) { openPage(SettingsPage.About) }),
            onQuit = onQuit, header = { RaUserHeader() }, onFocusChanged = { MenuState.menuHasFocus = it },
        )
        if (openingGame) {
            Box(
                Modifier.fillMaxSize().zIndex(100f)
                    .background(Color(0xE6171425))
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { }
                    .onPreviewKeyEvent { true },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    CircularProgressIndicator(color = Gold, strokeWidth = 4.dp, modifier = Modifier.size(54.dp))
                    Text("Opening game…", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Preparing the disc image. The first launch may take a little longer.", color = Color.White.copy(alpha = .78f),
                        style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp).padding(horizontal = 20.dp))
                }
            }
        }
        gameMenu?.let { g -> GameOptions(g, entries.firstOrNull { it.game == g.key },
            onDismiss = { gameMenu = null },
            onContinue = { e -> gameMenu = null; actions.resume(e) },
            onOpen = { gameMenu = null; actions.fresh(g) },
            onAchievements = { gameMenu = null; achievementsFor = g.key },
            onCover = { picker.launch(arrayOf("image/*")) }) }
        if (confirmQuit) QuitDialog(onConfirm = { confirmQuit = false; onQuit() }, onCancel = { confirmQuit = false })
    }
}

private enum class EmptyKind { Bios, Folder, Loading, NoGames, NoStates }

/** What the library shows while there are no games (or save states) to show. */
@Composable
private fun EmptyContent(kind: EmptyKind, toSystem: () -> Unit) = Panel(Modifier.fillMaxSize()) {
    when (kind) {
        EmptyKind.Bios -> EmptyState(Icons.Default.Memory, "Add a Saturn BIOS", "GBSat needs your own Saturn IPL ROM (512 KiB) to boot games.", "Open System options", toSystem)
        EmptyKind.Folder -> EmptyState(Icons.Default.Folder, "Choose your games folder", "Pick the folder that holds your Saturn disc images.", "Open System options", toSystem)
        EmptyKind.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        EmptyKind.NoGames -> EmptyState(Icons.Default.SearchOff, "No games found", "Nothing found in ${Storage.folderLabel(Emulator.gamesDir)}", null, {})
        EmptyKind.NoStates -> EmptyState(Icons.Default.History, "No save states yet", "Save states you create in games show up here, newest first.", null, {})
    }
}

/** Library menu state that survives opening a settings page (the library screen is rebuilt on return). */
internal object MenuState {
    val open = mutableStateOf(false)
    /** "Confirm Quit?" is open; A confirms from MainActivity, which works even when nothing has focus yet. */
    val quitOpen = mutableStateOf(false)
    /** Remember whether the library shows recent save states or all games. */
    val recent = gb.frame.LibraryView.recent
    var onQuit: (() -> Unit)? = null
    var selected by mutableStateOf<SettingsPage?>(null)
    /** The library is on screen (not a settings page or a game): MainActivity handles X and lost focus there. */
    @Volatile var libraryShown = false
    /** Something inside the library screen has the focus (a tap or a closed menu can leave nothing focused). */
    @Volatile var hasFocus = false
    /** Bumped to put the focus back on the library. */
    val refocus = mutableIntStateOf(0)
    /** Something inside the open main menu has the focus (opened with a tap, the focus can stay on the library behind). */
    @Volatile var menuHasFocus = false
    /** Bumped to put the focus back on the open menu. */
    val menuRefocus = mutableIntStateOf(0)
}

private val SettingsEntries = listOf(
    Triple(MenuIcon.System, "System", SettingsPage.System), Triple(MenuIcon.Video, "Video", SettingsPage.Video),
    Triple(MenuIcon.Audio, "Audio", SettingsPage.Audio), Triple(MenuIcon.Performance, "Performance", SettingsPage.Performance),
    Triple(MenuIcon.Controls, "Controls", SettingsPage.Controls), Triple(MenuIcon.Extras, "Extras", SettingsPage.Extras),
    Triple(MenuIcon.About, "About", SettingsPage.About),
)

/** "Confirm Quit?" drawn in the app window (not a separate dialog window), so the A/B controller mapping applies. */
@Composable
private fun QuitDialog(onConfirm: () -> Unit, onCancel: () -> Unit) =
    ConfirmDialog(title = "Exit", message = "Are you sure you want to exit?", confirm = "Exit", onConfirm = onConfirm, onCancel = onCancel)

private val AppBackground = Brush.verticalGradient(listOf(Color(0xFF11131A), Color(0xFF202431), Color(0xFF0B0D12)))
private val PanelColor = Color(0xFF1A1D27).copy(alpha = 0.96f)
private val PanelBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.17f))
private val Gold = Color(0xFFE5C56A)

@Composable
private fun Panel(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(24.dp), content: @Composable () -> Unit) =
    Surface(modifier, shape = shape, color = PanelColor, border = PanelBorder, content = content)

/** Game options: continue from the latest save state, start fresh, achievements, cover image. */
@Composable
private fun GameOptions(
    g: HomeGame, latest: ResumeEntry?, onDismiss: () -> Unit, onContinue: (ResumeEntry) -> Unit, onOpen: () -> Unit,
    onAchievements: () -> Unit, onCover: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(g.title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(if (g.playSeconds >= 60) "Played ${formatPlayTime(g.playSeconds)}" else "Not played yet", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                TextButton(enabled = latest != null, onClick = { latest?.let(onContinue) }) {
                    Icon(Icons.Default.PlayCircle, null); Spacer(Modifier.width(8.dp))
                    Text(if (latest != null) "Continue" else "Continue (no save state)")
                }
                TextButton(onClick = onOpen) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Open") }
                if (RetroAchievements.enabled && RetroAchievements.user != null) {
                    TextButton(onClick = onAchievements) { Icon(Icons.Default.EmojiEvents, null); Spacer(Modifier.width(8.dp)); Text("Achievements") }
                }
                TextButton(onClick = onCover) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Cover image") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Fixed bottom line with the controller buttons, Nintendo-style colored glyphs. */
@Composable
private fun HelpBar(recentMode: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .28f)).padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonHint("A", Color(0xFFE5483B), "Play")
        if (!recentMode) ButtonHint("Y", Color(0xFF3BB273), "Game options")
        ButtonHint("X", Color(0xFF3B7BE5), "Menu")
        ButtonHint("B", Color(0xFFF2C230), if (recentMode) "Back" else "Quit")
    }
}


@Composable
private fun ButtonHint(letter: String, tint: Color, label: String) {
    Row(
        Modifier.padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Text(letter, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
        }
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .7f))
        if (action != null) { Spacer(Modifier.height(24.dp)); Button(onClick = onAction) { Text(action) } }
    }
}
