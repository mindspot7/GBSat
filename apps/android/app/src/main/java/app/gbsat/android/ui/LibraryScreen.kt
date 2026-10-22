package app.gbsat.android.ui

import gb.frame.*
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.gbsat.android.Emulator
import app.gbsat.android.R
import app.gbsat.android.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen: header, side menu (permanent on wide screens, a drawer otherwise) and the game library, which can
 * switch to "Recent" (save states with their screenshots, newest first). Works with touch and with a controller:
 * the D-pad moves the selection, A opens, B goes back, Y (or a long press) opens the game options.
 */
@Composable
fun LibraryScreen(
    onOpenSettings: (SettingsPage?) -> Unit,
    launch: (String) -> Unit,
    launchState: (Emulator.RecentState) -> Unit,
    onQuit: () -> Unit,
    open: (String) -> Unit = launch,
) {
    val scope = rememberCoroutineScope()
    var recentMode by MenuState.recent
    var menuOpen by MenuState.open
    val menuFocus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // Landscape: the menu is docked on the left, always visible, and the library works beside it ("open" = menu focused)
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val docked = cfg.screenWidthDp > cfg.screenHeightDp * 1.15f
    val hasPad = rememberHasGamepad()
    // Docked menu: coming back from a settings page focuses the entry that was opened
    LaunchedEffect(Unit) { if (docked && MenuState.selected != null) { kotlinx.coroutines.delay(120); runCatching { menuFocus.requestFocus() } } }
    LaunchedEffect(menuOpen, docked) { if (menuOpen) { kotlinx.coroutines.delay(80); runCatching { menuFocus.requestFocus() } } }
    var confirmQuit by MenuState.quitOpen
    MenuState.onQuit = onQuit
    var headerPx by remember { mutableIntStateOf(0) }
    // B / Back: close the menu, leave Recent, otherwise ask before quitting
    // Some controllers deliver B twice (as BUTTON_B mapped to Back and as a system Back): one press must act only once,
    // otherwise the quit dialog opens and immediately closes again
    var lastBack by remember { mutableLongStateOf(0L) }
    androidx.activity.compose.BackHandler {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBack < 300) return@BackHandler
        lastBack = now
        when { confirmQuit -> confirmQuit = false; menuOpen && !docked -> menuOpen = false; menuOpen && docked -> focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right); recentMode -> recentMode = false; else -> confirmQuit = true }
    }

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
            Key.ButtonX -> { // X: menu (docked: switch between the menu and the library)
                if (docked && menuOpen) focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right) else menuOpen = !menuOpen
                true
            }
            else -> false
        }
    }) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The whole logo bar opens the menu, which drops down right below it
            Header(recentMode, Modifier.onSizeChanged { headerPx = it.height }, onToggleRecent = { recentMode = !recentMode },
                onMenu = { if (docked) runCatching { menuFocus.requestFocus() } else menuOpen = !menuOpen }, menuOpen = menuOpen && !docked)
            Row(Modifier.fillMaxWidth().weight(1f)) {
            if (docked) {
                Box(Modifier.width(220.dp).fillMaxHeight().onFocusChanged { menuOpen = it.hasFocus }) {
                    SideMenu(onOpenSettings = { MenuState.selected = it; onOpenSettings(it) }, onQuit = onQuit, focus = menuFocus, fill = true)
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (recentMode) {
                    RecentStates(launchState = launchState, onSelected = {})
                } else {
                    Library(edgePad = if (docked) 4.dp else 20.dp, onOpenSettings = { onOpenSettings(SettingsPage.System) },
                        launch = { MenuState.selected = null; launch(it) }, onSelected = {}, open = { MenuState.selected = null; open(it) },
                        launchState = { MenuState.selected = null; launchState(it) })
                }
            }
            }
            if (hasPad) HelpBar(recentMode)
        }
        // Menu and scrim drop down below the logo bar, over the shelf and the button help bar
        val headerDp = with(androidx.compose.ui.platform.LocalDensity.current) { headerPx.toDp() }
        if (!docked) Box(Modifier.fillMaxSize().safeDrawingPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp + headerDp + 8.dp)) {
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
                Box(Modifier.width(220.dp)) {
                    // The menu stays open while a settings page is shown, so Back returns to it with that entry selected
                    SideMenu(onOpenSettings = { MenuState.selected = it; onOpenSettings(it) }, onQuit = onQuit, focus = menuFocus)
                }
            }
        }
        if (confirmQuit) QuitDialog(onConfirm = { confirmQuit = false; onQuit() }, onCancel = { confirmQuit = false })
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

@Composable
private fun Header(recentMode: Boolean, modifier: Modifier, onToggleRecent: () -> Unit, onMenu: () -> Unit, menuOpen: Boolean) {
    val ctx = LocalContext.current
    var time by remember { mutableStateOf("") }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val fmt = android.text.format.DateFormat.getTimeFormat(ctx)
        while (true) {
            time = fmt.format(java.util.Date())
            ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { i ->
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                battery = if (level >= 0 && scale > 0) level * 100 / scale else -1
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
            delay(20_000)
        }
    }
    Surface(shape = RoundedCornerShape(32.dp), color = PanelColor, modifier =
        modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .focusProperties { canFocus = false } // touch only: the controller never moves to the top bar
            .clickable(onClick = onMenu),
    ) {
      BoxWithConstraints {
        val compact = maxWidth < 420.dp // narrow portrait screens: drop the subtitle, battery text and clock
        Row(Modifier.padding(horizontal = if (compact) 10.dp else 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GBSat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Icon(if (menuOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Menu", tint = Gold)
                }
                Text(if (recentMode) "Recent · save states" else "Saturn emulator", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = .7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Library / Recent switch
            FocusableIcon(if (recentMode) Icons.Default.GridView else Icons.Default.History,
                if (recentMode) "Show all games" else "Show recent save states", Gold, onToggleRecent)
            Spacer(Modifier.width(8.dp))
            if (!compact) Text(time, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = .9f))
            if (battery >= 0) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    when {
                        charging -> Icons.Default.BatteryChargingFull
                        battery > 80 -> Icons.Default.BatteryFull
                        battery > 50 -> Icons.Default.Battery5Bar
                        battery > 20 -> Icons.Default.Battery3Bar
                        else -> Icons.Default.Battery1Bar
                    }, null, tint = if (battery <= 20 && !charging) Color(0xFFFF6B6B) else Color(0xFF6BE58A),
                )
                if (!compact) Text("$battery%", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = .9f))
            }
        }
      }
    }
}

/** Icon button with a visible highlight when selected with the controller. */
@Composable
private fun FocusableIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White.copy(alpha = .18f) else Color.Transparent)
            .focusProperties { canFocus = false }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint) }
}

@Composable
private fun SideMenu(onOpenSettings: (SettingsPage?) -> Unit, onQuit: () -> Unit, focus: FocusRequester, fill: Boolean = false) {
    Surface(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xF21C1830), shadowElevation = 12.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            RaUserHeader()
            Text("SETTINGS", style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            listOf(
                Triple(MenuIcon.System, "System", SettingsPage.System), Triple(MenuIcon.Video, "Video", SettingsPage.Video),
                Triple(MenuIcon.Audio, "Audio", SettingsPage.Audio), Triple(MenuIcon.Performance, "Performance", SettingsPage.Performance),
                Triple(MenuIcon.Controls, "Controls", SettingsPage.Controls), Triple(MenuIcon.Extras, "Extras", SettingsPage.Extras),
                Triple(MenuIcon.About, "About", SettingsPage.About),
            ).forEach { (icon, label, page) ->
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
private fun Library(
    edgePad: androidx.compose.ui.unit.Dp,
    onOpenSettings: () -> Unit, launch: (String) -> Unit, onSelected: (ImageBitmap?) -> Unit,
    open: (String) -> Unit, launchState: (Emulator.RecentState) -> Unit,
) {
    var games by remember { mutableStateOf<List<gb.frame.Game>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) { refreshKey++; onPauseOrDispose { } }
    LaunchedEffect(Emulator.gamesDir, refreshKey) {
        if (Emulator.gamesDir.isBlank()) { games = emptyList(); return@LaunchedEffect }
        loading = games.isEmpty()
        games = withContext(Dispatchers.IO) { Storage.scan(Emulator.gamesDir) }
        loading = false
    }

    when {
        !Emulator.iplLoaded -> Panel(Modifier.fillMaxSize()) {
            EmptyState(Icons.Default.Memory, "Add a Saturn BIOS", "GBSat needs your own Saturn IPL ROM (512 KiB) to boot games.", "Open System options", onOpenSettings)
        }
        Emulator.gamesDir.isBlank() -> Panel(Modifier.fillMaxSize()) {
            EmptyState(Icons.Default.Folder, "Choose your games folder", "Pick the folder that holds your Saturn disc images.", "Open System options", onOpenSettings)
        }
        games.isEmpty() -> Panel(Modifier.fillMaxSize()) {
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else EmptyState(Icons.Default.SearchOff, "No games found", "Nothing found in ${Storage.folderLabel(Emulator.gamesDir)}", null, {})
        }
        else -> {
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            var gameMenu by remember { mutableStateOf<gb.frame.Game?>(null) }
            var achievementsFor by remember { mutableStateOf<String?>(null) }
            achievementsFor?.let { path ->
                androidx.activity.compose.BackHandler { achievementsFor = null }
                // Drawn over the shelf (same parent box), so the controller mapping of the main window applies
                Box(Modifier.zIndex(10f).fillMaxSize()) {
                    AchievementsScreen(onBack = { achievementsFor = null }, load = { RetroAchievements.gameAchievements(path) })
                }
            }
            var coverVersion by remember { mutableIntStateOf(0) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val g = gameMenu
                if (uri != null && g != null) scope.launch { Covers.setCustom(ctx, g.title, uri); coverVersion++ }
                gameMenu = null
            }
            gameMenu?.let { g ->
                var latest by remember(g) { mutableStateOf<Emulator.RecentState?>(null) }
                LaunchedEffect(g) { latest = withContext(Dispatchers.IO) { Emulator.latestState(g.path) } }
                AlertDialog(
                    onDismissRequest = { gameMenu = null },
                    title = { Text(g.title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    text = {
                        Column {
                            val secs = Emulator.playTime(g.path)
                            Text(if (secs >= 60) "Played ${formatPlayTime(secs)}" else "Not played yet", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            val st = latest
                            TextButton(enabled = st != null, onClick = { gameMenu = null; st?.let(launchState) }) {
                                Icon(Icons.Default.PlayCircle, null); Spacer(Modifier.width(8.dp))
                                Text(if (st != null) "Continue" else "Continue (no save state)")
                            }
                            TextButton(onClick = { gameMenu = null; open(g.path) }) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Open")
                            }
                            if (RetroAchievements.enabled && RetroAchievements.user != null) {
                                TextButton(onClick = { gameMenu = null; achievementsFor = g.path }) {
                                    Icon(Icons.Default.EmojiEvents, null); Spacer(Modifier.width(8.dp)); Text("Achievements")
                                }
                            }
                            TextButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                                Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Cover image")
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { gameMenu = null }) { Text("Close") } },
                )
            }

            val first = remember { FocusRequester() }
            LaunchedEffect(games.firstOrNull()?.path) { runCatching { first.requestFocus() } }
            var selected by remember { mutableStateOf<gb.frame.Game?>(null) }
            var withStates by remember { mutableStateOf<Set<String>>(emptySet()) }
            LaunchedEffect(games) { withStates = withContext(Dispatchers.IO) { Emulator.gamesWithStates() } }
            // Game shelf: details of the selected game on top, one horizontal row of box art below
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Screen orientation, not the shelf area: the docked menu makes the shelf narrower in landscape
                val scr = androidx.compose.ui.platform.LocalConfiguration.current
                val portrait = scr.screenWidthDp < scr.screenHeightDp * 1.2f
                val cardH = if (portrait) (maxHeight * 0.40f).coerceIn(130.dp, 240.dp) else (maxHeight * 0.58f).coerceIn(140.dp, 340.dp)
                val cardW = cardH * 0.72f
                Column(Modifier.fillMaxSize()) {
                    // Selected game: name, play time and badges (only while a game is selected)
                    androidx.compose.animation.AnimatedVisibility(selected != null,
                        enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                        val g = selected
                        if (g != null) Column(Modifier.fillMaxWidth().padding(start = edgePad + 8.dp, end = 20.dp, top = 8.dp)) {
                            // Long names stay on one line
                            Text(g.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                val secs = Emulator.playTime(g.path)
                                Icon(Icons.Default.Schedule, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (secs >= 60) formatPlayTime(secs) else "Not played", style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = .75f))
                                if (g.path in withStates) {
                                    Spacer(Modifier.width(14.dp))
                                    Icon(Icons.Default.Save, "Has save states", tint = Color(0xFF7FD7C4), modifier = Modifier.size(18.dp))
                                }
                                if (g.path in Emulator.achievementGames) {
                                    Spacer(Modifier.width(10.dp))
                                    Icon(Icons.Default.EmojiEvents, "Has achievements", tint = Gold, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // The selected cover is kept in the second slot; at the ends the list stops with its full padding, so the
                    // first and last covers are never cut off (focus scrolling alone ignores the padding)
                    val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
                    var focusIndex by remember { mutableIntStateOf(-1) }
                    val slotPx = with(androidx.compose.ui.platform.LocalDensity.current) { (cardW + 20.dp).roundToPx() }
                    LaunchedEffect(focusIndex) { if (focusIndex >= 0) rowState.animateScrollToItem(focusIndex, -slotPx) }
                    androidx.compose.foundation.lazy.LazyRow(
                        state = rowState,
                        // Side padding leaves room for the enlarged (focused) first and last covers
                        contentPadding = PaddingValues(start = edgePad + cardW * 0.1f, end = 20.dp + cardW * 0.1f, top = cardH * 0.06f, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(games.size, key = { games[it].path }) { i ->
                            val g = games[i]
                            GameCard(
                                g, coverVersion, cardW, cardH,
                                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                                onClick = { launch(g.path) }, onOptions = { gameMenu = g },
                                onSelected = { selected = g; focusIndex = i },
                            )
                        }
                    }
                    // Portrait (e.g. 3:4 handhelds): the shelf sits lower-middle instead of on the bottom edge
                    if (portrait) Spacer(Modifier.weight(0.6f))
                }
            }
        }
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(
    g: gb.frame.Game, version: Int, cardW: androidx.compose.ui.unit.Dp, cardH: androidx.compose.ui.unit.Dp, modifier: Modifier,
    onClick: () -> Unit, onOptions: () -> Unit, onSelected: (ImageBitmap?) -> Unit,
) {
    var cover by remember(g.title, version) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(g.title, version) { cover = Covers.load(g.title) }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, label = "scale")
    // Cover and reflection scale together around the cover's bottom edge, so the reflection always starts below it
    val reflH = cardH * 0.16f
    // The cover area is tall enough for the tallest covers (ratio 0.66), so no cover is ever cut; covers sit on its bottom
    val boxH = cardW / 0.66f
    Column(modifier.zIndex(if (focused) 1f else 0f).width(cardW).padding(top = 0.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, boxH / (boxH + reflH)) }) {
    Box(
        Modifier.size(cardW, boxH)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelected(cover) }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.ButtonY) { onOptions(); true } else false
            }
            // No focus/press highlight box: the enlarged cover is the selection
            .combinedClickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
                onClick = onClick, onLongClick = onOptions),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val bmp = cover
        if (bmp != null) {
            // Every cover has the same width; square covers keep their proportions (shorter), only very tall ones are
            // widened slightly so the shelf looks even
            val ratio = (bmp.width.toFloat() / bmp.height).coerceIn(0.66f, 1.25f)
            Image(
                bmp, g.title,
                Modifier.fillMaxWidth().aspectRatio(ratio)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(10.dp)),
                contentScale = ContentScale.FillBounds,
            )
        } else {
            Column(
                Modifier.size(cardW, cardH).clip(RoundedCornerShape(10.dp)).background(Color(0xFF241F3D))
                    .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.Album, null, Modifier.size(44.dp), tint = Color(0xFFB9A6FF).copy(alpha = .7f))
                Spacer(Modifier.height(8.dp))
                Text(g.title, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    // Soft mirror reflection of the cover below it, fading out
    val refl = cover
    if (refl != null) {
        val ratio = (refl.width.toFloat() / refl.height).coerceIn(0.66f, 1.25f)
        Box(
            Modifier.fillMaxWidth().height(reflH).clipToBounds()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .30f), Color.Transparent)), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                },
        ) {
            Image(refl, null, Modifier.fillMaxWidth().aspectRatio(ratio).wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { scaleY = -1f }.clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.FillBounds)
        }
    } else Spacer(Modifier.height(reflH))
    }
    // Keep the background in sync when the cover finishes loading while selected
    LaunchedEffect(cover) { if (focused) onSelected(cover) }
}

/** Save states of all games with their screenshots, newest first. */
@Composable
private fun RecentStates(launchState: (Emulator.RecentState) -> Unit, onSelected: (ImageBitmap?) -> Unit) {
    var states by remember { mutableStateOf<List<Emulator.RecentState>?>(null) }
    // Latest save state of each game
    LaunchedEffect(Unit) { states = withContext(Dispatchers.IO) { Emulator.recentStates() } }
    val list = states
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> Panel(Modifier.fillMaxSize()) {
            EmptyState(Icons.Default.History, "No save states yet", "Save states you create in games show up here, newest first.", null, {})
        }
        else -> ResumeGrid(
            list.map { s ->
                ResumeEntry(s.gamePath + "#" + s.slot, s.gamePath.substringAfterLast('/').substringBeforeLast('.'),
                    when (s.slot) { Emulator.QUICK_SLOT -> "Quick save"; Emulator.AUTO_SLOT -> "Auto save"; else -> "Slot ${s.slot + 1}" },
                    s.time, s.thumb, game = s.gamePath)
            },
            onResume = { e -> list.firstOrNull { it.gamePath + "#" + it.slot == e.key }?.let(launchState) },
        )
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


