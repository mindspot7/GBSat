package app.ymir.android.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import app.ymir.android.Emulator
import app.ymir.android.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GameScreen(onQuitApp: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var fps by remember { mutableFloatStateOf(0f) }

    BackHandler { menu = true; Emulator.pause(true) }
    LaunchedEffect(Unit) {
        while (true) { fps = NativeBridge.nativeGetStats()[0] / 100f; delay(500) }
    }
    LaunchedEffect(Emulator.menuRequest) {
        if (Emulator.menuRequest) { menu = true; Emulator.pause(true); Emulator.menuRequest = false }
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
                Box(Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.TopCenter) {
                    VideoSurface(if (Emulator.aspect == 1) Modifier.fillMaxWidth().height(screenW * 0.75f) else Modifier.fillMaxWidth().aspectRatio(ratio))
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
            if (Emulator.showFps) Text("%.1f fps".format(fps), color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }

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

@Composable
private fun GameMenu(onQuitApp: () -> Unit, onDismiss: () -> Unit) {
    var cheatsOpen by remember { mutableStateOf(false) }
    if (cheatsOpen) { CheatsScreen(onBack = { cheatsOpen = false }); return }
    var stateMode by remember { mutableStateOf<String?>(null) } // "Save" / "Load"
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (stateMode != null) {
        val mode = stateMode!!
        var times by remember { mutableStateOf<List<Long?>>(emptyList()) }
        LaunchedEffect(mode) { times = withContext(Dispatchers.IO) { Emulator.slotTimes() } }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 420.dp).padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$mode state", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                    for (slot in 0 until Emulator.STATE_SLOTS) {
                        val t = times.getOrNull(slot)
                        val label = "Slot ${slot + 1} — " + (t?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it)) } ?: "empty")
                        TextButton(
                            enabled = mode == "Save" || t != null, modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    val err = if (mode == "Save") Emulator.saveState(slot) else Emulator.loadState(slot)
                                    if (err == null) { stateMode = null; onDismiss() } else message = err
                                }
                            },
                        ) { Text(label, Modifier.weight(1f)) }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
                    TextButton(onClick = { stateMode = null; message = null }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                }
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
        val maxMenuHeight = maxHeight
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 420.dp).heightIn(max = maxMenuHeight).padding(16.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Paused", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                MenuItem(Icons.Default.PlayArrow, "Resume", onDismiss)
                MenuItem(Icons.Default.Save, "Save state") { stateMode = "Save" }
                MenuItem(Icons.Default.Restore, "Load state") { stateMode = "Load" }
                MenuItem(Icons.Default.FastForward, if (Emulator.fastForward) "Fast-forward: on" else "Fast-forward: off") { Emulator.toggleFastForward() }
                MenuItem(Icons.Default.RestartAlt, "Soft reset") { Emulator.reset(false); onDismiss() }
                MenuItem(Icons.Default.PowerSettingsNew, "Hard reset") { Emulator.reset(true); onDismiss() }
                MenuItem(Icons.Default.Eject, if (Emulator.trayOpen) "Close disc tray" else "Open disc tray") { Emulator.toggleTray() }
                MenuItem(Icons.Default.Speed, if (Emulator.showFps) "Hide FPS" else "Show FPS") { Emulator.showFps = !Emulator.showFps; Emulator.savePrefs() }
                MenuItem(Icons.Default.AspectRatio, "Display: " + listOf("4:3", "Fill", "16:9")[Emulator.aspect.coerceIn(0, 2)]) {
                    Emulator.aspect = (Emulator.aspect + 1) % 3; Emulator.savePrefs()
                }
                MenuItem(Icons.Default.Gamepad, if (Emulator.showTouchControls) "Hide touch controls" else "Show touch controls") {
                    Emulator.showTouchControls = !Emulator.showTouchControls; Emulator.savePrefs()
                }
                MenuItem(Icons.Default.Tune, "Cheats") { cheatsOpen = true }
                if (Emulator.renderer == 1 || Emulator.renderer == 3) {
                    val shaders = listOf("None", "Scanlines", "CRT", "LCD3x", "Dots")
                    val filters = listOf("Nearest", "Bilinear", "Sharp bilinear")
                    MenuItem(Icons.Default.Tv, "Shader: ${shaders[Emulator.shader]}") {
                        Emulator.shader = (Emulator.shader + 1) % shaders.size; Emulator.savePrefs(); Emulator.applyOptions()
                    }
                    MenuItem(Icons.Default.FilterAlt, "Filter: ${filters[Emulator.filter]}") {
                        Emulator.filter = (Emulator.filter + 1) % filters.size; Emulator.savePrefs(); Emulator.applyOptions()
                    }
                }
                MenuItem(Icons.Default.Close, "Close game (back to library)") { Emulator.quitGame() }
                MenuItem(Icons.Default.ExitToApp, "Quit GBSat") { onQuitApp() }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}
