package app.ymir.android.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay

@Composable
fun GameScreen() {
    var menu by remember { mutableStateOf(false) }
    var fps by remember { mutableFloatStateOf(0f) }
    var showStats by remember { mutableStateOf(false) }

    BackHandler { menu = true; Emulator.setPaused(true) }
    LaunchedEffect(Unit) {
        while (true) { fps = NativeBridge.nativeGetStats()[0] / 100f; delay(500) }
    }
    // Resume when returning from background.
    LaunchedEffect(Unit) { if (Emulator.paused && !menu) Emulator.setPaused(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        val videoMod = when (Emulator.aspect) {
            1 -> Modifier.fillMaxSize()
            else -> Modifier.aspectRatio(4f / 3f)
        }

        if (landscape) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VideoSurface(videoMod.fillMaxHeight())
            }
            if (Emulator.showTouchControls) TouchControls(Modifier.fillMaxSize().safeDrawingPadding(), landscape = true)
        } else {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Box(Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.TopCenter) {
                    VideoSurface(if (Emulator.aspect == 1) Modifier.fillMaxWidth().height(maxWidth * 0.75f) else Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                }
                if (Emulator.showTouchControls) TouchControls(Modifier.fillMaxWidth().weight(1f), landscape = false)
            }
        }

        // Unobtrusive top-left menu handle + optional stats
        Row(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { menu = true; Emulator.setPaused(true) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = .45f), contentColor = Color.White),
            ) { Icon(Icons.Default.Menu, "Menu") }
            if (showStats) Text("%.1f fps".format(fps), color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }

        if (menu) GameMenu(
            showStats = showStats, onToggleStats = { showStats = !showStats },
            onDismiss = { menu = false; Emulator.setPaused(false) },
        )
    }
}

@Composable
private fun VideoSurface(modifier: Modifier) {
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
private fun GameMenu(showStats: Boolean, onToggleStats: () -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 420.dp).padding(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Paused", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                MenuItem(Icons.Default.PlayArrow, "Resume", onDismiss)
                MenuItem(Icons.Default.FastForward, if (Emulator.fastForward) "Fast-forward: on" else "Fast-forward: off") { Emulator.toggleFastForward() }
                MenuItem(Icons.Default.RestartAlt, "Soft reset") { Emulator.reset(false); onDismiss() }
                MenuItem(Icons.Default.PowerSettingsNew, "Hard reset") { Emulator.reset(true); onDismiss() }
                MenuItem(Icons.Default.Eject, if (Emulator.trayOpen) "Close disc tray" else "Open disc tray") { Emulator.toggleTray() }
                MenuItem(Icons.Default.Speed, if (showStats) "Hide FPS" else "Show FPS", onToggleStats)
                MenuItem(Icons.Default.Gamepad, if (Emulator.showTouchControls) "Hide touch controls" else "Show touch controls") {
                    Emulator.showTouchControls = !Emulator.showTouchControls; Emulator.savePrefs()
                }
                MenuItem(Icons.Default.ExitToApp, "Quit game") { Emulator.quitGame() }
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
