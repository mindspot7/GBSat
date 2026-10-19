package app.gbsat.android.ui

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import app.gbsat.android.Covers
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
    onRequestAllFiles: () -> Unit,
    launch: (String) -> Unit,
    launchState: (Emulator.RecentState) -> Unit,
    onQuit: () -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var recentMode by remember { mutableStateOf(false) }
    var background by remember { mutableStateOf<ImageBitmap?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(AppBackground)) {
        val wide = maxWidth >= 720.dp
        val menu: @Composable () -> Unit = {
            SideMenu(onOpenSettings = { scope.launch { drawer.close() }; onOpenSettings(it) }, onQuit = onQuit)
        }
        val content: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header(recentMode, onToggleRecent = { recentMode = !recentMode }, onMenu = { if (!wide) scope.launch { drawer.open() } })
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (wide) Box(Modifier.width(250.dp).fillMaxHeight()) { menu() }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (recentMode) {
                            RecentStates(launchState = launchState, onSelected = { background = it })
                        } else {
                            Library(onOpenSettings = { onOpenSettings(SettingsPage.System) }, onRequestAllFiles = onRequestAllFiles,
                                launch = launch, onSelected = { background = it })
                        }
                    }
                }
                HelpBar(recentMode)
            }
        }
        if (wide) content() else ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = { Box(Modifier.width(280.dp).fillMaxHeight().safeDrawingPadding().padding(12.dp)) { menu() } },
            content = content,
        )
    }
}

private val AppBackground = Brush.verticalGradient(listOf(Color(0xFF1D1636), Color(0xFF2B2152), Color(0xFF161126)))
private val PanelColor = Color(0xFF1B1830).copy(alpha = 0.88f)
private val PanelBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
private val Gold = Color(0xFFE8C26A)

@Composable
private fun Panel(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(24.dp), content: @Composable () -> Unit) =
    Surface(modifier, shape = shape, color = PanelColor, border = PanelBorder, content = content)

@Composable
private fun Header(recentMode: Boolean, onToggleRecent: () -> Unit, onMenu: () -> Unit) {
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
    Panel(Modifier.fillMaxWidth(), RoundedCornerShape(32.dp)) {
      BoxWithConstraints {
        val compact = maxWidth < 420.dp // narrow portrait screens: drop the subtitle, battery text and clock
        Row(Modifier.padding(horizontal = if (compact) 10.dp else 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("GBSat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (recentMode) "Recent · save states" else "Sega Saturn Emulation", style = MaterialTheme.typography.bodySmall,
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
            Spacer(Modifier.width(4.dp))
            FocusableIcon(Icons.Default.MoreVert, "Menu", Gold, onMenu)
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
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint) }
}

@Composable
private fun SideMenu(onOpenSettings: (SettingsPage?) -> Unit, onQuit: () -> Unit) {
    Panel(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Text("OPTIONS", style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            MenuEntry(Icons.Default.Tune, "System", Color(0xFFCFD3E6)) { onOpenSettings(SettingsPage.System) }
            MenuEntry(Icons.Default.Tv, "Video", Color(0xFF7FD7C4)) { onOpenSettings(SettingsPage.Video) }
            MenuEntry(Icons.Default.VolumeUp, "Audio", Color(0xFF9BE37B)) { onOpenSettings(SettingsPage.Audio) }
            MenuEntry(Icons.Default.Speed, "Performance", Color(0xFFFFB86B)) { onOpenSettings(SettingsPage.Performance) }
            MenuEntry(Icons.Default.SportsEsports, "Controls", Color(0xFFFF7A90)) { onOpenSettings(SettingsPage.Controls) }
            MenuEntry(Icons.Default.EmojiEvents, "Extras", Gold) { onOpenSettings(SettingsPage.Extras) }
            MenuEntry(Icons.Default.Info, "About", Color(0xFF6FA8FF)) { onOpenSettings(SettingsPage.About) }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White.copy(alpha = .12f))
            MenuEntry(Icons.Default.ExitToApp, "Quit", Color(0xFFFF8A65), onQuit)
        }
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color.White.copy(alpha = .14f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(30.dp), tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun Library(onOpenSettings: () -> Unit, onRequestAllFiles: () -> Unit, launch: (String) -> Unit, onSelected: (ImageBitmap?) -> Unit) {
    var games by remember { mutableStateOf<List<Storage.Game>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val hasAccess = Storage.hasAllFilesAccess()

    LifecycleResumeEffect(Unit) { refreshKey++; onPauseOrDispose { } }
    LaunchedEffect(Emulator.gamesDir, refreshKey, hasAccess) {
        if (Emulator.gamesDir.isBlank() || !hasAccess) { games = emptyList(); return@LaunchedEffect }
        loading = games.isEmpty()
        games = withContext(Dispatchers.IO) { Storage.scan(Emulator.gamesDir) }
        loading = false
    }

    when {
        !Emulator.iplLoaded -> Panel(Modifier.fillMaxSize()) {
            EmptyState(Icons.Default.Memory, "Add a Saturn BIOS", "GBSat needs your own Saturn IPL ROM (512 KiB) to boot games.", "Open System options", onOpenSettings)
        }
        !hasAccess -> Panel(Modifier.fillMaxSize()) {
            EmptyState(Icons.Default.FolderOff, "Allow file access", "Disc images (CUE/BIN, CHD, ISO, MDS, CCD) are read directly from storage.", "Grant access", onRequestAllFiles)
        }
        Emulator.gamesDir.isBlank() -> Panel(Modifier.fillMaxSize()) {
            EmptyState(Icons.Default.Folder, "Choose your games folder", "Pick the folder that holds your Saturn disc images.", "Open System options", onOpenSettings)
        }
        games.isEmpty() -> Panel(Modifier.fillMaxSize()) {
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else EmptyState(Icons.Default.SearchOff, "No games found", "Nothing found in ${Emulator.gamesDir}", null, {})
        }
        else -> {
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            var gameMenu by remember { mutableStateOf<Storage.Game?>(null) }
            var coverVersion by remember { mutableIntStateOf(0) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val g = gameMenu
                if (uri != null && g != null) scope.launch { Covers.setCustom(ctx, g.title, uri); coverVersion++ }
                gameMenu = null
            }
            gameMenu?.let { g ->
                AlertDialog(
                    onDismissRequest = { gameMenu = null },
                    title = { Text(g.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    text = {
                        Column {
                            val secs = Emulator.playTime(g.path)
                            Text(if (secs >= 60) "Played ${formatPlayTime(secs)}" else "Not played yet", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                                Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Choose cover image")
                            }
                            TextButton(onClick = { scope.launch { Covers.clearCustom(g.title); coverVersion++ }; gameMenu = null }) {
                                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Reset / re-download cover")
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { gameMenu = null; launch(g.path) }) { Text("Play") } },
                    dismissButton = { TextButton(onClick = { gameMenu = null }) { Text("Close") } },
                )
            }

            val first = remember { FocusRequester() }
            LaunchedEffect(games.firstOrNull()?.path) { runCatching { first.requestFocus() } }
            var selected by remember { mutableStateOf<Storage.Game?>(null) }
            var withStates by remember { mutableStateOf<Set<String>>(emptySet()) }
            LaunchedEffect(games) { withStates = withContext(Dispatchers.IO) { Emulator.gamesWithStates() } }
            // Game shelf: details of the selected game on top, one horizontal row of box art below
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val portrait = maxWidth < maxHeight * 1.2f
                val cardH = if (portrait) (maxHeight * 0.40f).coerceIn(130.dp, 240.dp) else (maxHeight * 0.58f).coerceIn(140.dp, 340.dp)
                val cardW = cardH * 0.72f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                    // Selected game: name, play time and badges (only while a game is selected)
                    androidx.compose.animation.AnimatedVisibility(selected != null,
                        enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                        val g = selected
                        if (g != null) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            Text(g.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = cardH * 0.12f, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(games.size, key = { games[it].path }) { i ->
                            val g = games[i]
                            GameCard(
                                g, coverVersion, cardW, cardH,
                                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                                onClick = { launch(g.path) }, onOptions = { gameMenu = g },
                                onSelected = { selected = g },
                            )
                        }
                    }
                    // Shelf board
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(Brush.verticalGradient(listOf(Color(0xFF6E5BC4), Color(0xFF2A2150)))),
                    )
                    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(14.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent))))
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
        if (!recentMode) ButtonHint("Y", Color(0xFF3BB273), "Options")
        ButtonHint("B", Color(0xFFF2C230), "Back")
    }
}

@Composable
private fun ButtonHint(button: String, color: Color, label: String) {
    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(button, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(
    g: Storage.Game, version: Int, cardW: androidx.compose.ui.unit.Dp, cardH: androidx.compose.ui.unit.Dp, modifier: Modifier,
    onClick: () -> Unit, onOptions: () -> Unit, onSelected: (ImageBitmap?) -> Unit,
) {
    var cover by remember(g.title, version) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(g.title, version) { cover = Covers.load(g.title) }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, label = "scale")
    Box(
        modifier.zIndex(if (focused) 1f else 0f).size(cardW, cardH)
            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelected(cover) }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonY || e.key == Key.ButtonX || e.key == Key.Menu)) { onOptions(); true } else false
            }
            .combinedClickable(onClick = onClick, onLongClick = onOptions),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val bmp = cover
        if (bmp != null) {
            // Box art keeps its own proportions inside the slot and stands on the shelf
            Image(
                bmp, g.title,
                Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height, matchHeightConstraintsFirst = bmp.height > bmp.width * 1.39f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(if (focused) 3.dp else 1.dp, if (focused) Gold else Color.White.copy(alpha = .18f), RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(0xFF241F3D))
                    .border(if (focused) 3.dp else 1.dp, if (focused) Gold else Color.White.copy(alpha = .16f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.Album, null, Modifier.size(44.dp), tint = Color(0xFFB9A6FF).copy(alpha = .7f))
                Spacer(Modifier.height(8.dp))
                Text(g.title, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    // Keep the background in sync when the cover finishes loading while selected
    LaunchedEffect(cover) { if (focused) onSelected(cover) }
}

/** Save states of all games with their screenshots, newest first. */
@Composable
private fun RecentStates(launchState: (Emulator.RecentState) -> Unit, onSelected: (ImageBitmap?) -> Unit) {
    var states by remember { mutableStateOf<List<Emulator.RecentState>?>(null) }
    LaunchedEffect(Unit) { states = withContext(Dispatchers.IO) { Emulator.recentStates() } }
    val list = states
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> Panel(Modifier.fillMaxSize()) {
            EmptyState(Icons.Default.History, "No save states yet", "Save states you create in games show up here, newest first.", null, {})
        }
        else -> {
            val first = remember { FocusRequester() }
            LaunchedEffect(list.first()) { runCatching { first.requestFocus() } }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp), contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.gamePath + "#" + it.slot }) { s ->
                    StateCard(s, if (s == list.first()) Modifier.focusRequester(first) else Modifier, onClick = { launchState(s) }, onSelected = onSelected)
                }
            }
        }
    }
}

@Composable
private fun StateCard(s: Emulator.RecentState, modifier: Modifier, onClick: () -> Unit, onSelected: (ImageBitmap?) -> Unit) {
    var shot by remember(s) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(s) {
        shot = withContext(Dispatchers.IO) { s.thumb?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() } }
    }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "scale")
    val title = s.gamePath.substringAfterLast('/').substringBeforeLast('.')
    val slot = if (s.slot == Emulator.QUICK_SLOT) "Quick save" else "Slot ${s.slot + 1}"
    val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(s.time))
    Column(modifier.scale(scale)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(14.dp)).background(Color.Black)
                .border(if (focused) 3.dp else 1.dp, if (focused) Gold else Color.White.copy(alpha = .16f), RoundedCornerShape(14.dp))
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelected(shot) }
                .clickable(onClick = onClick),
        ) {
            shot?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                ?: Icon(Icons.Default.Image, null, Modifier.align(Alignment.Center).size(40.dp), tint = Color.White.copy(alpha = .4f))
            Text(slot, Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(alpha = .6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, color = if (focused) Gold else Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(date, color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
    }
    LaunchedEffect(shot) { if (focused) onSelected(shot) }
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

/** Play time as "1h 05m" or "12m". */
fun formatPlayTime(secs: Long): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "${h}h ${"%02d".format(m)}m" else "${m}m"
}
