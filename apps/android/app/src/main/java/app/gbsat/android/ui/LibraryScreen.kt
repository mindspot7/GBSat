package app.gbsat.android.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.gbsat.android.RetroAchievements
import app.gbsat.android.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Collection(val label: String, val icon: ImageVector) {
    All("All games", Icons.Default.GridView), Recent("Recent", Icons.Default.History), Favorites("Favorites", Icons.Default.Star)
}

/** Home screen: header, side menu (permanent on wide screens, a drawer otherwise), collections and the game library. */
@Composable
fun LibraryScreen(
    onOpenSettings: (SettingsPage?) -> Unit,
    onRequestAllFiles: () -> Unit,
    launch: (String) -> Unit,
    onQuit: () -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().background(AppBackground)) {
        val wide = maxWidth >= 720.dp
        val menu: @Composable () -> Unit = {
            SideMenu(onOpenSettings = { scope.launch { drawer.close() }; onOpenSettings(it) }, onQuit = onQuit)
        }
        val content: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header(onMenu = { if (!wide) scope.launch { drawer.open() } })
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (wide) Box(Modifier.width(260.dp).fillMaxHeight()) { menu() }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Library(onOpenSettings = { onOpenSettings(null) }, onRequestAllFiles = onRequestAllFiles, launch = launch)
                    }
                }
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
private val PanelColor = Color(0xFF1B1830).copy(alpha = 0.92f)
private val PanelBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))

@Composable
private fun Panel(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(24.dp), content: @Composable () -> Unit) =
    Surface(modifier, shape = shape, color = PanelColor, border = PanelBorder, content = content)

@Composable
private fun Header(onMenu: () -> Unit) {
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
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("GBSat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Sega Saturn Emulation", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .7f), maxLines = 1)
            }
            Text(time, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = .9f))
            if (battery >= 0) {
                Spacer(Modifier.width(14.dp))
                Icon(
                    when {
                        charging -> Icons.Default.BatteryChargingFull
                        battery > 80 -> Icons.Default.BatteryFull
                        battery > 50 -> Icons.Default.Battery5Bar
                        battery > 20 -> Icons.Default.Battery3Bar
                        else -> Icons.Default.Battery1Bar
                    }, null, tint = if (battery <= 20 && !charging) Color(0xFFFF6B6B) else Color(0xFF6BE58A),
                )
                Text("$battery%", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = .9f))
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, "Menu", tint = Color(0xFFE8C26A)) }
        }
    }
}

@Composable
private fun SideMenu(onOpenSettings: (SettingsPage?) -> Unit, onQuit: () -> Unit) {
    Panel(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            MenuEntry(Icons.Default.EmojiEvents, "RetroAchievements", Color(0xFFE8C26A)) { onOpenSettings(SettingsPage.RetroAchievements) }
            MenuEntry(Icons.Default.Settings, "Settings", Color(0xFFB9A6FF)) { onOpenSettings(null) }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White.copy(alpha = .12f))
            Text("OPTIONS", style = MaterialTheme.typography.labelLarge, color = Color(0xFFE8C26A),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            MenuEntry(Icons.Default.Tune, "System", Color(0xFFCFD3E6)) { onOpenSettings(SettingsPage.System) }
            MenuEntry(Icons.Default.Tv, "Video", Color(0xFF7FD7C4)) { onOpenSettings(SettingsPage.Video) }
            MenuEntry(Icons.Default.Speed, "Performance", Color(0xFFFFB86B)) { onOpenSettings(SettingsPage.Performance) }
            MenuEntry(Icons.Default.SportsEsports, "Controls", Color(0xFFFF7A90)) { onOpenSettings(SettingsPage.Controls) }
            MenuEntry(Icons.Default.Info, "About", Color(0xFF6FA8FF)) { onOpenSettings(SettingsPage.About) }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White.copy(alpha = .12f))
            MenuEntry(Icons.Default.ExitToApp, "Quit", Color(0xFFFF8A65), onQuit)
        }
    }
}

@Composable
private fun MenuEntry(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(30.dp), tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun Library(onOpenSettings: () -> Unit, onRequestAllFiles: () -> Unit, launch: (String) -> Unit) {
    var games by remember { mutableStateOf<List<Storage.Game>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var collection by remember { mutableStateOf(Collection.All) }
    val hasAccess = Storage.hasAllFilesAccess()

    LifecycleResumeEffect(Unit) { refreshKey++; onPauseOrDispose { } }
    LaunchedEffect(Emulator.gamesDir, refreshKey, hasAccess) {
        if (Emulator.gamesDir.isBlank() || !hasAccess) { games = emptyList(); return@LaunchedEffect }
        loading = true
        games = withContext(Dispatchers.IO) { Storage.scan(Emulator.gamesDir) }
        loading = false
    }

    when {
        !Emulator.iplLoaded -> Panel(Modifier.fillMaxSize()) {
            EmptyState(Icons.Default.Memory, "Add a Saturn BIOS", "GBSat needs your own Saturn IPL ROM (512 KiB) to boot games.", "Open settings", onOpenSettings)
        }
        !hasAccess -> Panel(Modifier.fillMaxSize()) {
            EmptyState(Icons.Default.FolderOff, "Allow file access", "Disc images (CUE/BIN, CHD, ISO, MDS, CCD) are read directly from storage.", "Grant access", onRequestAllFiles)
        }
        Emulator.gamesDir.isBlank() -> Panel(Modifier.fillMaxSize()) {
            EmptyState(Icons.Default.Folder, "Choose your games folder", "Pick the folder that holds your Saturn disc images.", "Open settings", onOpenSettings)
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
                val fav = g.path in Emulator.favorites
                AlertDialog(
                    onDismissRequest = { gameMenu = null },
                    title = { Text(g.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    text = {
                        Column {
                            val secs = Emulator.playTime(g.path)
                            Text(if (secs >= 60) "Played ${formatPlayTime(secs)}" else "Not played yet", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { Emulator.toggleFavorite(g.path); gameMenu = null }) {
                                Icon(if (fav) Icons.Default.StarBorder else Icons.Default.Star, null); Spacer(Modifier.width(8.dp))
                                Text(if (fav) "Remove from favorites" else "Add to favorites")
                            }
                            TextButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                                Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Choose cover image")
                            }
                            TextButton(onClick = { scope.launch { Covers.clearCustom(g.title); coverVersion++ }; gameMenu = null }) {
                                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Reset / re-download cover")
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { launch(g.path) }) { Text("Play") } },
                    dismissButton = { TextButton(onClick = { gameMenu = null }) { Text("Close") } },
                )
            }

            val byPath = games.associateBy { it.path }
            val inCollection = when (collection) {
                Collection.All -> games
                Collection.Recent -> Emulator.recent.mapNotNull { byPath[it] }
                Collection.Favorites -> games.filter { it.path in Emulator.favorites }
            }
            val shown = inCollection.filter { it.title.contains(query, true) }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SearchField(query, { query = it }, onRefresh = { refreshKey++ })
                        Text("Collections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(Collection.entries.toList()) { c ->
                                val count = when (c) {
                                    Collection.All -> games.size
                                    Collection.Recent -> Emulator.recent.count { it in byPath }
                                    Collection.Favorites -> games.count { it.path in Emulator.favorites }
                                }
                                CollectionTile(c, count, selected = c == collection) { collection = c }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${collection.label} (${shown.size})", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                if (shown.isEmpty() && !loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            when (collection) {
                                Collection.Favorites -> "No favorites yet. Long-press a game to add it."
                                Collection.Recent -> "Games you play will show up here."
                                Collection.All -> "No games found in ${Emulator.gamesDir}"
                            },
                            color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(shown, key = { it.path }) { g ->
                    GameCard(g, coverVersion, favorite = g.path in Emulator.favorites, onClick = { launch(g.path) }, onLongClick = { gameMenu = g })
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onRefresh: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = .7f))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (query.isEmpty()) Text("Search Saturn games…", color = Color.White.copy(alpha = .5f))
                BasicTextField(query, onChange, singleLine = true, cursorBrush = SolidColor(Color.White),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), modifier = Modifier.fillMaxWidth())
            }
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Clear", tint = Color.White.copy(alpha = .7f)) }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Rescan", tint = Color.White.copy(alpha = .7f)) }
        }
    }
}

@Composable
private fun CollectionTile(c: Collection, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = when (c) {
        Collection.All -> listOf(Color(0xFF6E58D8), Color(0xFF4A3A9E))
        Collection.Recent -> listOf(Color(0xFF3E8E5A), Color(0xFF2A6340))
        Collection.Favorites -> listOf(Color(0xFFC08A2E), Color(0xFF8A5F1C))
    }
    Box(
        Modifier.size(width = 120.dp, height = 96.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(colors))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = .2f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(10.dp),
    ) {
        Icon(c.icon, null, Modifier.align(Alignment.Center).padding(bottom = 18.dp).size(34.dp), tint = Color.White)
        Text("$count", Modifier.align(Alignment.TopEnd), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium)
        Text(c.label, Modifier.align(Alignment.BottomCenter), color = Color.White, style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(g: Storage.Game, version: Int, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    var cover by remember(g.title, version) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(g.title, version) { cover = Covers.load(g.title) }
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF241F3D))
                .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            cover?.let { Image(it, g.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Album, null, Modifier.size(48.dp), tint = Color(0xFFB9A6FF).copy(alpha = .7f))
                    Spacer(Modifier.height(8.dp))
                    Text(g.title, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            if (favorite) Icon(Icons.Default.Star, null, Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp), tint = Color(0xFFFFD25A))
        }
        Spacer(Modifier.height(4.dp))
        Text(g.title, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val secs = Emulator.playTime(g.path)
        if (secs >= 60) Text(formatPlayTime(secs), color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.labelSmall)
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

/** Play time as "1h 05m" or "12m". */
fun formatPlayTime(secs: Long): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "${h}h ${"%02d".format(m)}m" else "${m}m"
}
