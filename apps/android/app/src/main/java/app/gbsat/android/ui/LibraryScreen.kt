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
 * Home screen: top bar (app icon, label, clock, battery), the game library in the theme chosen in Settings → Extras
 * (gb.frame.HomeLibrary: Showcase by default, Grid, List or Continue) and the settings menu, which drops down over the
 * library (the Grid theme shows it as an icon rail instead). Works with touch and with a controller: the D-pad moves the
 * selection, A opens, B goes back, X opens the menu, Y (or a long press) opens the game options, Select shows the recent
 * save states.
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
    var recentMode by MenuState.recent
    var menuOpen by MenuState.open
    val style by HomeTheme.style
    val rail = style == HomeStyle.Grid // the menu is the icon rail beside the games
    val menuFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val hasPad = rememberHasGamepad()
    var railFocused by remember { mutableStateOf(false) }
    // Rail: coming back from a settings page focuses the entry that was opened
    LaunchedEffect(Unit) { if (rail && MenuState.selected != null) { delay(150); runCatching { menuFocus.requestFocus() } } }
    LaunchedEffect(menuOpen) { if (menuOpen && !rail) { delay(80); runCatching { menuFocus.requestFocus() } } }
    var confirmQuit by MenuState.quitOpen
    MenuState.onQuit = onQuit
    var headerPx by remember { mutableIntStateOf(0) }

    // ---- Data: games, save states, play time ----
    var games by remember { mutableStateOf<List<gb.frame.Game>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
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

    // B / Back: close the menu, leave the rail or Recent, otherwise ask before quitting
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
            menuOpen && !rail -> menuOpen = false
            rail && railFocused -> focusManager.moveFocus(FocusDirection.Right)
            recentMode -> recentMode = false
            else -> confirmQuit = true
        }
    }

    val openPage: (SettingsPage) -> Unit = { MenuState.selected = it; onOpenSettings(it) }
    val actions = HomeActions(
        play = { MenuState.selected = null; launch(it.key) },
        fresh = { MenuState.selected = null; open(it.key) },
        options = { gameMenu = it },
        resume = { e -> states.firstOrNull { it.gamePath + "#" + it.slot == e.key }?.let { MenuState.selected = null; launchState(it) } },
        openMenu = { if (rail) runCatching { menuFocus.requestFocus() } else menuOpen = true },
        showRecent = { recentMode = it },
    )
    val railItems = SettingsEntries.map { (icon, label, page) ->
        HomeMenuItem(icon, label, selected = MenuState.selected == page) { openPage(page) }
    } + HomeMenuItem(MenuIcon.Quit, "Quit") { onQuit() }
    val toSystem = { openPage(SettingsPage.System) }
    val emptyKind = when {
        !Emulator.iplLoaded -> EmptyKind.Bios
        Emulator.gamesDir.isBlank() -> EmptyKind.Folder
        games.isEmpty() && !recentMode -> if (loading) EmptyKind.Loading else EmptyKind.NoGames
        recentMode && entries.isEmpty() -> EmptyKind.NoStates
        else -> null
    }
    val empty: (@Composable () -> Unit)? = if (emptyKind == null) null else ({ EmptyContent(emptyKind, toSystem) })

    Box(Modifier.fillMaxSize().background(AppBackground).onPreviewKeyEvent { e ->
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
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
            Key.ButtonX -> { // X: menu (rail: switch between the rail and the games)
                if (rail) { if (railFocused) focusManager.moveFocus(FocusDirection.Right) else runCatching { menuFocus.requestFocus() } }
                else menuOpen = !menuOpen
                true
            }
            else -> false
        }
    }) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The top bar opens the menu, which drops down right below it
            HomeTopBar(painterResource(R.mipmap.ic_launcher), "Saturn emulator",
                onClick = { if (rail) runCatching { menuFocus.requestFocus() } else menuOpen = !menuOpen },
                modifier = Modifier.onSizeChanged { headerPx = it.height })
            Box(Modifier.fillMaxWidth().weight(1f)) {
                HomeLibrary(style, homeGames, entries, recentMode, actions, railItems, menuFocus, coverVersion, empty,
                    onMenuFocus = { railFocused = it })
                achievementsFor?.let { path ->
                    // Drawn over the library (same window), so the controller mapping of the main window applies
                    Box(Modifier.zIndex(10f).fillMaxSize()) {
                        AchievementsScreen(onBack = { achievementsFor = null }, load = { RetroAchievements.gameAchievements(path) })
                    }
                }
            }
            if (hasPad) HelpBar(recentMode)
        }
        // Menu and scrim drop down below the top bar, over the library and the button help bar
        val headerDp = with(LocalDensity.current) { headerPx.toDp() }
        if (!rail) Box(Modifier.fillMaxSize().safeDrawingPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp + headerDp + 8.dp)) {
            if (menuOpen) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = .35f))
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { menuOpen = false })
            }
            androidx.compose.animation.AnimatedVisibility(
                menuOpen,
                enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.TopStart).padding(bottom = 4.dp),
            ) {
                Box(Modifier.width(240.dp)) {
                    // The menu stays open while a settings page is shown, so Back returns to it with that entry selected
                    SideMenu(onOpenSettings = openPage, onQuit = onQuit, focus = menuFocus, recentMode = recentMode,
                        onToggleRecent = { recentMode = !recentMode; menuOpen = false })
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
    var onQuit: (() -> Unit)? = null
    /** Library shows recent save states instead of all games (Select; toggled in MainActivity so it works without
     *  focus). Remembered between sessions by the kit's LibraryView. */
    val recent = gb.frame.LibraryView.recent
    var selected by mutableStateOf<SettingsPage?>(null)
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

private val AppBackground = Brush.verticalGradient(listOf(Color(0xFF1D1636), Color(0xFF2B2152), Color(0xFF161126)))
private val PanelColor = Color(0xFF1B1830).copy(alpha = 0.88f)
private val PanelBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
private val Gold = Color(0xFFE8C26A)

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

@Composable
private fun SideMenu(
    onOpenSettings: (SettingsPage) -> Unit, onQuit: () -> Unit, focus: FocusRequester, recentMode: Boolean, onToggleRecent: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xF21C1830), shadowElevation = 12.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            RaUserHeader()
            // Touch way to the recent save states (Select on a controller)
            IconEntry(if (recentMode) Icons.Default.GridView else Icons.Default.History, if (recentMode) "All games" else "Recent save states", onToggleRecent)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Color.White.copy(alpha = .12f))
            Text("SETTINGS", style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            SettingsEntries.forEach { (icon, label, page) ->
                val sel = (MenuState.selected ?: SettingsPage.System) == page
                MenuEntry(icon, label, if (sel) Modifier.focusRequester(focus) else Modifier) { onOpenSettings(page) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White.copy(alpha = .12f))
            MenuEntry(MenuIcon.Quit, "Quit", onClick = onQuit)
        }
    }
}

@Composable
private fun MenuEntry(icon: MenuIcon, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color.White.copy(alpha = .14f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuIconView(icon)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun IconEntry(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color.White.copy(alpha = .14f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Gold, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
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
        ButtonHint("X", Color(0xFF3B7BE5), "Settings")
        ButtonHint("B", Color(0xFFF2C230), if (recentMode) "Back" else "Quit")
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
