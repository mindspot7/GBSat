package app.gbsat.android.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gbsat.android.Emulator
import app.gbsat.android.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GameScreen(onQuitApp: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var fps by remember { mutableFloatStateOf(0f) }
    var stats by remember { mutableStateOf(longArrayOf(0, 320, 224, 0, 1)) }

    BackHandler(enabled = !menu) { menu = true; Emulator.pause(true) }
    LaunchedEffect(Unit) {
        while (true) { stats = NativeBridge.nativeGetStats(); fps = stats[0] / 100f; delay(500) }
    }
    LaunchedEffect(Emulator.menuRequest) {
        if (Emulator.menuRequest) { menu = true; Emulator.pause(true); Emulator.menuRequest = false }
    }
    // Report if the Vulkan GPU renderer could not start (the software renderer keeps running).
    LaunchedEffect(Emulator.renderer) {
        if (Emulator.renderer == 4) {
            delay(1500)
            val err = NativeBridge.nativeGetGpuError().orEmpty()
            if (err.isNotEmpty()) Emulator.showToast("Vulkan GPU unavailable, using software: $err")
        }
    }
    // Resume when returning from background.
    LaunchedEffect(Unit) { if (Emulator.paused && !menu) Emulator.pause(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screenW = maxWidth
        val landscape = maxWidth > maxHeight
        val ratio = if (Emulator.aspect == 2) 16f / 9f else 4f / 3f
        val videoMod = if (Emulator.aspect == 1) Modifier.fillMaxSize() else Modifier.aspectRatio(ratio)

        if (landscape) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VideoSurface(videoMod.fillMaxHeight())
            }
            if (Emulator.showTouchControls) TouchControls(Modifier.fillMaxSize().safeDrawingPadding(), landscape = true)
        } else {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                // Fullscreen fills the whole area above the touch controls (or the whole screen when they are hidden)
                val full = Emulator.aspect == 1
                Box(Modifier.fillMaxWidth().weight(1f, fill = full), contentAlignment = Alignment.TopCenter) {
                    VideoSurface(if (full) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(ratio))
                }
                if (Emulator.showTouchControls) TouchControls(Modifier.fillMaxWidth().weight(1f), landscape = false)
            }
        }

        // Unobtrusive top-left menu handle + optional stats
        Row(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { menu = true; Emulator.pause(true) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = .45f), contentColor = Color.White),
            ) { Icon(Icons.Default.Menu, "Menu") }
        }

        // Performance overlay, top right
        PerfOverlay(fps, stats, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(10.dp))

        if (Emulator.showFfButton) {
            FilledTonalIconButton(
                onClick = { Emulator.toggleFastForward() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (Emulator.fastForward) Color(0xFFE8C26A).copy(alpha = .85f) else Color.Black.copy(alpha = .45f),
                    contentColor = if (Emulator.fastForward) Color.Black else Color.White),
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
            ) { Icon(Icons.Default.FastForward, "Fast-forward") }
        }

        RaNotifications(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 56.dp))

        Emulator.toast?.let { msg ->
            Surface(
                shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = .7f),
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 12.dp),
            ) { Text(msg, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        }

        if (menu) GameMenu(
            onQuitApp = onQuitApp,
            onDismiss = { menu = false; Emulator.pause(false) },
        )
    }
}

@Composable
private fun VideoSurface(modifier: Modifier) {
    // A SurfaceView can only be fed by one kind of producer, so switching renderer recreates it.
    key(Emulator.renderer) { VideoSurfaceInner(modifier) }
}

@Composable
private fun VideoSurfaceInner(modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) = NativeBridge.nativeSetSurface(h.surface)
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) = NativeBridge.nativeSetSurface(null)
                })
            }
        },
    )
}

private enum class MenuPage(val title: String) { Root("Paused"), Save("Save state"), Load("Load state"), Video("Video"), Audio("Audio"), Overlays("Overlays"), Game("Game"), Exit("Exit") }

private val MenuGold = Color(0xFFE8C26A)
private val MenuDim = Color.White.copy(alpha = .65f)

/**
 * In-game menu: a panel on the left side with submenus. Tapping outside the panel (or Back / B) closes it;
 * inside a submenu, Back returns to the main list.
 */
@Composable
private fun GameMenu(onQuitApp: () -> Unit, onDismiss: () -> Unit) {
    var cheatsOpen by remember { mutableStateOf(false) }
    var achievementsOpen by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(MenuPage.Root) }
    BackHandler { when { cheatsOpen -> cheatsOpen = false; achievementsOpen -> achievementsOpen = false; page != MenuPage.Root -> page = MenuPage.Root; else -> onDismiss() } }
    if (cheatsOpen) { CheatsScreen(onBack = { cheatsOpen = false }); return }
    if (achievementsOpen) { AchievementsScreen(onBack = { achievementsOpen = false }); return }

    val visible = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    Box(Modifier.fillMaxSize()) {
        // Scrim: tapping anywhere outside the panel closes the menu
        androidx.compose.animation.AnimatedVisibility(visible, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f))
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onDismiss() })
        }
        androidx.compose.animation.AnimatedVisibility(
            visible, enter = androidx.compose.animation.slideInHorizontally { -it }, exit = androidx.compose.animation.slideOutHorizontally { -it },
        ) {
            Surface(
                color = Color(0xFF1B1830).copy(alpha = .97f),
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
                modifier = Modifier.fillMaxHeight().widthIn(max = 340.dp).fillMaxWidth(0.85f),
            ) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 12.dp)) {
                    // Header: title, or back arrow + submenu title
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (page != MenuPage.Root) {
                            IconButton(onClick = { page = MenuPage.Root }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MenuGold) }
                        } else {
                            Icon(Icons.Default.Pause, null, tint = MenuGold, modifier = Modifier.padding(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(page.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                            if (page == MenuPage.Root) Emulator.currentGame?.let {
                                Text(it.substringAfterLast('/').substringBeforeLast('.'), style = MaterialTheme.typography.bodySmall,
                                    color = MenuDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = MenuDim) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Color.White.copy(alpha = .1f))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                        when (page) {
                            MenuPage.Root -> {
                                MenuItem(Icons.Default.PlayArrow, "Resume", onClick = onDismiss)
                                MenuItem(Icons.Default.Save, "Save state", sub = true) { page = MenuPage.Save }
                                MenuItem(Icons.Default.Restore, "Load state", sub = true) { page = MenuPage.Load }
                                MenuItem(Icons.Default.FastForward, "Fast-forward", value = if (Emulator.fastForward) "On" else "Off") { Emulator.toggleFastForward() }
                                MenuItem(Icons.Default.Tv, "Video", sub = true) { page = MenuPage.Video }
                                MenuItem(Icons.Default.VolumeUp, "Audio", sub = true) { page = MenuPage.Audio }
                                MenuItem(Icons.Default.Layers, "Overlays", sub = true) { page = MenuPage.Overlays }
                                MenuItem(Icons.Default.SportsEsports, "Game", sub = true) { page = MenuPage.Game }
                                MenuItem(Icons.Default.ExitToApp, "Exit", sub = true) { page = MenuPage.Exit }
                            }
                            MenuPage.Save, MenuPage.Load -> StateSlots(save = page == MenuPage.Save, onDone = onDismiss)
                            MenuPage.Video -> {
                                MenuItem(Icons.Default.AutoAwesome, "Preset", value = Emulator.currentPreset?.label ?: "Custom") {
                                    val all = Emulator.Preset.entries
                                    Emulator.applyPreset(all[((Emulator.currentPreset?.ordinal ?: -1) + 1) % all.size])
                                }
                                MenuItem(Icons.Default.AspectRatio, "Display", value = listOf("4:3", "Fullscreen", "16:9")[Emulator.aspect.coerceIn(0, 2)]) {
                                    Emulator.aspect = (Emulator.aspect + 1) % 3; Emulator.savePrefs(); Emulator.refreshCheats()
                                    Emulator.widescreenHack?.let { Emulator.showToast("Widescreen hack enabled: $it") }
                                }
                                val rendererIds = listOf(0, 1, 4)
                                val rendererNames = listOf("Software", "OpenGL ES", "Vulkan GPU")
                                MenuItem(Icons.Default.Memory, "Renderer", value = rendererNames[rendererIds.indexOf(Emulator.renderer).coerceAtLeast(0)]) {
                                    Emulator.renderer = rendererIds[(rendererIds.indexOf(Emulator.renderer) + 1) % rendererIds.size]
                                    Emulator.savePrefs(); Emulator.applyOptions()
                                }
                                MenuItem(Icons.Default.SkipNext, "Frame skip", value = if (Emulator.frameSkip) "Auto" else "Off") {
                                    Emulator.frameSkip = !Emulator.frameSkip; Emulator.savePrefs(); Emulator.applyOptions()
                                }
                                if (Emulator.renderer == 4) {
                                    MenuItem(Icons.Default.HighQuality, "Internal resolution", value = Emulator.resScaleLabel) {
                                        // 1x → 2x → 3x → 4x → Native → 1x
                                        Emulator.resScale = when (Emulator.resScale) { 0 -> 1; 4 -> 0; else -> Emulator.resScale + 1 }; Emulator.savePrefs(); Emulator.applyOptions()
                                    }
                                }
                                if (Emulator.renderer == 1 || Emulator.renderer == 4) {
                                    val shaders = listOf("None", "Scanlines", "CRT", "LCD3x", "Dots") // by internal id
                                    val shaderOrder = listOf(0, 2, 4, 3, 1) // cycles alphabetically
                                    MenuItem(Icons.Default.Gradient, "Shader", value = shaders[Emulator.shader]) {
                                        Emulator.shader = shaderOrder[(shaderOrder.indexOf(Emulator.shader) + 1) % shaderOrder.size]
                                        Emulator.savePrefs(); NativeBridge.nativeSetDisplayOptions(Emulator.filter, Emulator.shader)
                                    }
                                    MenuItem(Icons.Default.FilterAlt, "Filter", value = listOf("Nearest", "Bilinear")[Emulator.filter.coerceIn(0, 1)]) {
                                        Emulator.filter = (Emulator.filter + 1) % 2; Emulator.savePrefs(); NativeBridge.nativeSetDisplayOptions(Emulator.filter, Emulator.shader)
                                    }
                                }
                            }
                            MenuPage.Audio -> {
                                MenuItem(Icons.Default.VolumeDown, "Volume down", value = "${Emulator.volume}%") {
                                    Emulator.volume = (Emulator.volume - 10).coerceAtLeast(0); Emulator.savePrefs(); Emulator.applyOptions()
                                }
                                MenuItem(Icons.Default.VolumeUp, "Volume up", value = "${Emulator.volume}%") {
                                    Emulator.volume = (Emulator.volume + 10).coerceAtMost(100); Emulator.savePrefs(); Emulator.applyOptions()
                                }
                                MenuItem(Icons.Default.FastForward, "Fast-forward audio", value = listOf("Default", "Speed up", "Mute")[Emulator.ffAudio]) {
                                    Emulator.ffAudio = (Emulator.ffAudio + 1) % 3; Emulator.savePrefs(); Emulator.applyOptions()
                                }
                            }
                            MenuPage.Overlays -> {
                                fun onOff(b: Boolean) = if (b) "On" else "Off"
                                MenuItem(Icons.Default.Insights, "Show all monitoring", value = onOff(Emulator.showAllStats)) { Emulator.showAllStats = !Emulator.showAllStats; Emulator.savePrefs() }
                                MenuItem(Icons.Default.Speed, "Show FPS", value = onOff(Emulator.showFps)) { Emulator.showFps = !Emulator.showFps; Emulator.savePrefs() }
                                MenuItem(Icons.Default.Percent, "Show speed", value = onOff(Emulator.showSpeed)) { Emulator.showSpeed = !Emulator.showSpeed; Emulator.savePrefs() }
                                MenuItem(Icons.Default.Memory, "Show renderer", value = onOff(Emulator.showRenderer)) { Emulator.showRenderer = !Emulator.showRenderer; Emulator.savePrefs() }
                                MenuItem(Icons.Default.PhotoSizeSelectLarge, "Show resolution", value = onOff(Emulator.showResolution)) { Emulator.showResolution = !Emulator.showResolution; Emulator.savePrefs() }
                                MenuItem(Icons.Default.DeveloperBoard, "Show CPU", value = onOff(Emulator.showCpu)) { Emulator.showCpu = !Emulator.showCpu; Emulator.savePrefs() }
                                MenuItem(Icons.Default.Videocam, "Show GPU", value = onOff(Emulator.showGpu)) { Emulator.showGpu = !Emulator.showGpu; Emulator.savePrefs() }
                                MenuItem(Icons.Default.Timer, "Show frame time", value = onOff(Emulator.showFrameTime)) { Emulator.showFrameTime = !Emulator.showFrameTime; Emulator.savePrefs() }
                                MenuItem(Icons.Default.FastForward, "Show fast-forward button", value = onOff(Emulator.showFfButton)) { Emulator.showFfButton = !Emulator.showFfButton; Emulator.savePrefs() }
                                MenuItem(Icons.Default.TouchApp, "Touch controls", value = if (Emulator.showTouchControls) "Shown" else "Hidden") {
                                    Emulator.showTouchControls = !Emulator.showTouchControls; Emulator.savePrefs()
                                }
                            }
                            MenuPage.Game -> {
                                MenuItem(Icons.Default.RestartAlt, "Reset") { Emulator.reset(true); onDismiss() }
                                MenuItem(Icons.Default.Eject, "Disc tray", value = if (Emulator.trayOpen) "Open" else "Closed") { Emulator.toggleTray() }
                                MenuItem(Icons.Default.Tune, "Cheats", sub = true) { cheatsOpen = true }
                                if (app.gbsat.android.RetroAchievements.enabled) {
                                    MenuItem(Icons.Default.EmojiEvents, "Achievements", sub = true) { achievementsOpen = true }
                                }
                            }
                            MenuPage.Exit -> {
                                MenuItem(Icons.Default.Close, "Close game") { Emulator.quitGame() }
                                MenuItem(Icons.Default.PowerSettingsNew, "Quit GBSat") { onQuitApp() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Save state slots with screenshots (save or load). */
@Composable
private fun StateSlots(save: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var times by remember { mutableStateOf<List<Long?>>(emptyList()) }
    var thumbs by remember { mutableStateOf<List<android.graphics.Bitmap?>>(emptyList()) }
    LaunchedEffect(save) {
        times = withContext(Dispatchers.IO) { Emulator.slotTimes() }
        thumbs = withContext(Dispatchers.IO) { (0 until Emulator.STATE_SLOTS).map { runCatching { Emulator.slotThumbnail(it) }.getOrNull() } }
    }
    for (slot in 0 until Emulator.STATE_SLOTS) {
        val t = times.getOrNull(slot)
        val enabled = save || t != null
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                .clickable(enabled = enabled) {
                    scope.launch {
                        val err = if (save) Emulator.saveState(slot) else Emulator.loadState(slot)
                        if (err == null) onDone() else message = err
                    }
                }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumb = thumbs.getOrNull(slot)
            Box(Modifier.size(width = 88.dp, height = 66.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                if (thumb != null) androidx.compose.foundation.Image(thumb.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
                else Icon(Icons.Default.Image, null, tint = Color.White.copy(alpha = .3f), modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Slot ${slot + 1}", color = if (enabled) Color.White else MenuDim, fontWeight = FontWeight.SemiBold)
                Text(t?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it)) } ?: "Empty",
                    style = MaterialTheme.typography.bodySmall, color = MenuDim)
            }
        }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
}

@Composable
private fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String,
    value: String? = null, sub: Boolean = false, onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White.copy(alpha = .12f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color(0xFFB9A6FF))
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Color.White)
        if (value != null) Text(value, color = MenuGold, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (sub) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MenuDim)
    }
}
