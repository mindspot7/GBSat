package app.gbsat.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gbsat.android.Emulator
import app.gbsat.android.HotkeyAction
import app.gbsat.android.NativeBridge
import app.gbsat.android.R
import app.gbsat.android.RetroAchievements
import app.gbsat.android.SaturnButton
import app.gbsat.android.Storage
import app.gbsat.android.keyName
import kotlinx.coroutines.launch

/** Option pages opened from the side menu. */
enum class SettingsPage(val label: String, val icon: ImageVector) {
    System("System", Icons.Default.Tune),
    Video("Video", Icons.Default.Tv),
    Audio("Audio", Icons.Default.VolumeUp),
    Performance("Performance", Icons.Default.Speed),
    Controls("Controls", Icons.Default.SportsEsports),
    Extras("Extras", Icons.Default.EmojiEvents),
    About("About", Icons.Default.Info),
}

private val Gold = Color(0xFFE8C26A)
private val CardColor = Color(0xFF1B1830).copy(alpha = 0.9f)
private val CardBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
private val Dim = Color.White.copy(alpha = 0.65f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onRequestAllFiles: () -> Unit, toast: (String) -> Unit, page: SettingsPage? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = page ?: SettingsPage.System

    val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { toast(Emulator.importBios(ctx, it) ?: "BIOS loaded") } }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val path = Storage.treeUriToPath(it)
            if (path == null) toast("Unsupported folder") else { Emulator.gamesDir = path; Emulator.savePrefs() }
        }
    }
    var dataMsg by remember { mutableStateOf<String?>(null) }
    val dataPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val path = Storage.treeUriToPath(it)
            if (path == null) toast("Unsupported folder") else scope.launch {
                val err = Emulator.changeDataDir(java.io.File(path, "GBSat"))
                dataMsg = err ?: "Data copied to $path/GBSat. Restart GBSat to use the new location."
            }
        }
    }
    dataMsg?.let { m -> AlertDialog(onDismissRequest = { dataMsg = null }, title = { Text("Data location") }, text = { Text(m) },
        confirmButton = { TextButton(onClick = { dataMsg = null }) { Text("OK") } }) }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1D1636), Color(0xFF2B2152), Color(0xFF161126))))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(current.icon, null, tint = Gold)
                        Spacer(Modifier.width(12.dp))
                        Text(current.label, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (current) {
                        SettingsPage.System -> SystemPage(onRequestAllFiles, { biosPicker.launch(arrayOf("*/*")) }, { folderPicker.launch(null) },
                            onDataFolder = { if (Storage.hasAllFilesAccess()) dataPicker.launch(null) else onRequestAllFiles() },
                            onDataInternal = { scope.launch { dataMsg = Emulator.changeDataDir(Emulator.defaultDataDir) ?: "Data copied to internal storage. Restart GBSat to use it." } })
                        SettingsPage.Video -> VideoPage()
                        SettingsPage.Audio -> AudioPage()
                        SettingsPage.Performance -> PerformancePage()
                        SettingsPage.Controls -> ControlsPage()
                        SettingsPage.Extras -> ExtrasPage()
                        SettingsPage.About -> AboutPage()
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        CapturePrompts()
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Pages

/** Wraps a setter so that the change is saved and applied. */
private fun <T> ch(set: (T) -> Unit): (T) -> Unit = { v -> set(v); Emulator.savePrefs(); Emulator.applyOptions() }

@Composable
private fun SystemPage(
    onRequestAllFiles: () -> Unit, onBios: () -> Unit, onGamesFolder: () -> Unit,
    onDataFolder: () -> Unit, onDataInternal: () -> Unit,
) {
    Group("Getting started", Icons.Default.PlayCircle) {
        ActionRow(Icons.Default.Memory, "Saturn BIOS", if (Emulator.iplLoaded) "Loaded" else "Required: choose your IPL ROM (512 KiB)",
            if (Emulator.iplLoaded) "Replace" else "Choose", onBios, warn = !Emulator.iplLoaded)
        if (!Storage.hasAllFilesAccess()) {
            Divider()
            ActionRow(Icons.Default.FolderOff, "File access", "Required to read disc images", "Grant", onRequestAllFiles, warn = true)
        }
        Divider()
        ActionRow(Icons.Default.Folder, "Games folder", Emulator.gamesDir.ifBlank { "Not set" }, "Choose", onGamesFolder, warn = Emulator.gamesDir.isBlank())
    }
    Group("Data", Icons.Default.Storage) {
        val internal = Emulator.dataDir == Emulator.defaultDataDir
        ActionRow(Icons.Default.SdStorage, "Emulator data location",
            (if (internal) "Internal app storage" else Emulator.dataDir.path) + " · BIOS, saves, save states, cheats, covers",
            "Change", onDataFolder)
        if (!internal) {
            Divider()
            ActionRow(Icons.Default.PhoneAndroid, "Move back to internal storage", "Copies the data and uses it after a restart", "Move", onDataInternal)
        }
    }
    Group("Save states", Icons.Default.Save) {
        ToggleRow(Icons.Default.Save, "Auto save", "Save the game's state when you close it", Emulator.autoSave, ch { Emulator.autoSave = it })
        Divider()
        ToggleRow(Icons.Default.Restore, "Auto load", "Continue from the auto save when you start the game", Emulator.autoLoad, ch { Emulator.autoLoad = it })
    }
    Group("Startup", Icons.Default.RocketLaunch) {
        ToggleRow(Icons.Default.FastForward, "Fast boot", "Skip the BIOS clock and language screens", Emulator.fastBoot, ch { Emulator.fastBoot = it })
    }
}

@Composable
private fun VideoPage() {
    val preset = Emulator.currentPreset
    Group("Preset", Icons.Default.AutoAwesome) {
        ChoiceRow("Quality preset",
            "High: Vulkan GPU 4x + Scanlines · Mid: OpenGL ES · Low: Software + frame skip. Changing an option below sets Custom.",
            Emulator.Preset.entries.map { it.label } + if (preset == null) listOf("Custom") else emptyList(),
            preset?.ordinal ?: Emulator.Preset.entries.size,
        ) { i -> Emulator.Preset.entries.getOrNull(i)?.let { Emulator.applyPreset(it) } }
    }
    Group("Renderer", Icons.Default.Memory) {
        val rendererIds = listOf(0, 1, 4)
        ChoiceRow("Renderer",
            when (Emulator.renderer) {
                0 -> "CPU emulation, shown without filters or shaders"
                1 -> "CPU emulation, shown with OpenGL ES (filters and shaders)"
                else -> "Graphics emulated on the GPU with Vulkan (falls back to Software if unsupported)"
            },
            listOf("Software", "OpenGL ES", "Vulkan GPU"), rendererIds.indexOf(Emulator.renderer).coerceAtLeast(0),
            ch { Emulator.renderer = rendererIds[it] })
        if (Emulator.renderer == 4) {
            Divider()
            ChoiceRow("Internal resolution", "Sharper 3D sprites and polygons; higher values need a faster GPU. Native matches the screen (${Emulator.nativeScale}x here)",
                listOf("1x", "2x", "3x", "4x", "Native"), if (Emulator.resScale == 0) 4 else Emulator.resScale - 1,
                ch { Emulator.resScale = if (it == 4) 0 else it + 1 })
            val gpuErr = remember { runCatching { NativeBridge.nativeGetGpuError().orEmpty() }.getOrDefault("") }
            if (gpuErr.isNotEmpty()) Text("Last GPU error: $gpuErr", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
    Group("Picture", Icons.Default.AspectRatio) {
        ChoiceRow("Display mode", "16:9 turns on built-in widescreen hacks for about 20 games; others are stretched",
            listOf("4:3", "Fullscreen", "16:9"), Emulator.aspect, ch { Emulator.aspect = it })
        if (Emulator.renderer != 0) {
            Divider()
            ChoiceRow("Texture filtering", null, listOf("Nearest", "Bilinear"), Emulator.filter.coerceAtMost(1), ch { Emulator.filter = it })
            Divider()
            val shaderIds = listOf(0, 2, 4, 3, 1) // internal ids: 0 none, 1 scanlines, 2 CRT, 3 LCD3x, 4 dots
            ChoiceRow("Shader", null, listOf("None", "CRT", "Dots", "LCD3x", "Scanlines"),
                shaderIds.indexOf(Emulator.shader).coerceAtLeast(0), ch { Emulator.shader = shaderIds[it] })
        }
    }
    Group("Overlay", Icons.Default.Layers) {
        ToggleRow(Icons.Default.Insights, "Show all monitoring", "FPS, speed, CPU, GPU, renderer, resolution and frame time at the top right",
            Emulator.showAllStats) { Emulator.showAllStats = it; Emulator.savePrefs() }
        Divider()
        ToggleRow(Icons.Default.Speed, "Show FPS", "Frames per second", Emulator.showFps) { Emulator.showFps = it; Emulator.savePrefs() }
        Divider()
        ToggleRow(Icons.Default.Percent, "Show speed", "Emulation speed in percent (100% = full speed)", Emulator.showSpeed) { Emulator.showSpeed = it; Emulator.savePrefs() }
        Divider()
        ToggleRow(Icons.Default.Memory, "Show renderer", "Software, OpenGL ES or Vulkan GPU", Emulator.showRenderer) { Emulator.showRenderer = it; Emulator.savePrefs() }
        Divider()
        ToggleRow(Icons.Default.PhotoSizeSelectLarge, "Show resolution", "Output resolution (including the internal resolution scale)",
            Emulator.showResolution) { Emulator.showResolution = it; Emulator.savePrefs() }
        Divider()
        ToggleRow(Icons.Default.DeveloperBoard, "Show CPU", "Chip name and GBSat's CPU usage", Emulator.showCpu) { Emulator.showCpu = it; Emulator.savePrefs() }
        Divider()
        ToggleRow(Icons.Default.Videocam, "Show GPU", "GPU name and load (when the device reports it)", Emulator.showGpu) { Emulator.showGpu = it; Emulator.savePrefs() }
        Divider()
        ToggleRow(Icons.Default.Timer, "Show frame time", "Time spent emulating each frame (16.7 ms or less = full speed)", Emulator.showFrameTime) { Emulator.showFrameTime = it; Emulator.savePrefs() }
        Divider()
        ToggleRow(Icons.Default.FastForward, "Show fast-forward button", "On-screen button to turn fast-forward on and off",
            Emulator.showFfButton) { Emulator.showFfButton = it; Emulator.savePrefs() }
    }
}

@Composable
private fun AudioPage() {
    Group("Output", Icons.Default.VolumeUp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(if (Emulator.volume == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp)
                Spacer(Modifier.width(14.dp))
                Text("Volume", style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
                Text("${Emulator.volume}%", color = Gold, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = Emulator.volume.toFloat(), valueRange = 0f..100f, steps = 19,
                onValueChange = { Emulator.volume = it.toInt(); NativeBridge.nativeSetAudio(Emulator.volume, Emulator.ffAudio) },
                onValueChangeFinished = { Emulator.savePrefs() },
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold),
            )
        }
    }
    Group("Fast-forward", Icons.Default.FastForward) {
        ChoiceRow("Fast-forward audio",
            when (Emulator.ffAudio) {
                1 -> "Plays all the audio faster (higher pitch), following the game speed"
                2 -> "No sound while fast-forwarding"
                else -> "Plays short pieces of audio at normal pitch"
            },
            listOf("Default", "Speed up", "Mute"), Emulator.ffAudio, ch { Emulator.ffAudio = it })
    }
}

@Composable
private fun PerformancePage() {
    Group("Speed", Icons.Default.Speed) {
        ToggleRow(Icons.Default.SkipNext, "Frame skip (auto)", "Skips rendering frames only while the game runs below full speed",
            Emulator.frameSkip, ch { Emulator.frameSkip = it })
        Divider()
        val cdSpeeds = listOf(2, 4, 8, 16)
        ChoiceRow("CD read speed", "Shorter loading screens; use Original if a game's videos or music go out of sync",
            listOf("Original", "4x", "8x", "16x"), cdSpeeds.indexOf(Emulator.cdSpeed).coerceAtLeast(0), ch<Int> { Emulator.cdSpeed = cdSpeeds[it] })
    }
    Group("Emulation", Icons.Default.Memory) {
        ToggleRow(Icons.Default.CallSplit, "Threaded VDP", "Render the VDP1/VDP2 graphics on separate CPU threads",
            Emulator.threadedVdp, ch { Emulator.threadedVdp = it })
        Divider()
        ToggleRow(Icons.Default.Verified, "SH-2 cache emulation", "More accurate, slightly slower", Emulator.sh2Cache, ch { Emulator.sh2Cache = it })
    }
    Group("Loading", Icons.Default.Album) {
        ToggleRow(Icons.Default.Download, "Preload disc into RAM", "Faster loading, uses more memory", Emulator.preloadDisc, ch { Emulator.preloadDisc = it })
    }
}

@Composable
private fun ControlsPage() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Controller" to Icons.Default.SportsEsports, "Hotkeys" to Icons.Default.Keyboard, "Touch" to Icons.Default.TouchApp)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardColor).border(CardBorder, RoundedCornerShape(20.dp)).padding(4.dp)) {
        tabs.forEachIndexed { i, (label, icon) ->
            val sel = i == tab
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (sel) Gold.copy(alpha = .22f) else Color.Transparent)
                    .clickable { tab = i }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (sel) Gold else Dim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (sel) Color.White else Dim, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    when (tab) {
        0 -> {
            Group("Saturn controller", Icons.Default.SportsEsports) {
                ChoiceRow("Controller type", "The 3D Control Pad adds the analog stick and analog L/R used by some games",
                    listOf("Control Pad", "3D Control Pad"), if (Emulator.analogPad) 1 else 0, ch<Int> { Emulator.analogPad = it == 1 })
            }
            Group("Button mapping", Icons.Default.Gamepad) {
                Text("Tap a button on the pad or in the list, then press the button on your controller.",
                    style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                SaturnPadDiagram(highlight = Emulator.remapTarget, onSelect = { Emulator.remapTarget = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(2.1f))
                Spacer(Modifier.height(8.dp))
                val order = listOf(SaturnButton.Up, SaturnButton.Down, SaturnButton.Left, SaturnButton.Right, SaturnButton.A, SaturnButton.B,
                    SaturnButton.C, SaturnButton.X, SaturnButton.Y, SaturnButton.Z, SaturnButton.L, SaturnButton.R, SaturnButton.Start)
                order.forEachIndexed { i, b ->
                    if (i > 0) Divider()
                    val keys = Emulator.keysFor(b).joinToString(", ") { keyName(it) }
                    MappingRow(b, keys.ifEmpty { "Unassigned" }, selected = Emulator.remapTarget == b) { Emulator.remapTarget = b }
                }
                Divider()
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { Emulator.resetKeyMap() }) { Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text("Reset to defaults") }
                }
            }
        }
        1 -> {
            Group("Hotkeys", Icons.Default.Keyboard) {
                Text("Bind one button, or hold several together (a combination) and release. Buttons in a combination are not sent to the game.",
                    style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                HotkeyAction.entries.forEachIndexed { i, a ->
                    if (i > 0) Divider()
                    val keys = Emulator.hotkeys[a].orEmpty()
                    HotkeyRow(a, keys)
                }
            }
            Group("Fast-forward", Icons.Default.FastForward) {
                ChoiceRow("Fast-forward hotkey", "Hold: fast while held · Toggle: press once to switch on or off",
                    listOf("Hold", "Toggle"), if (Emulator.ffToggle) 1 else 0, ch<Int> { Emulator.ffToggle = it == 1 })
            }
        }
        else -> {
            Group("On-screen controls", Icons.Default.TouchApp) {
                ToggleRow(Icons.Default.TouchApp, "Show touch controls", "Hide them when playing with a controller or a handheld",
                    Emulator.showTouchControls, ch { Emulator.showTouchControls = it })
            }
        }
    }
}

@Composable
private fun ExtrasPage() {
    val ra = RetroAchievements
    Group("RetroAchievements", Icons.Default.EmojiEvents) {
        ToggleRow(Icons.Default.EmojiEvents, "Enable RetroAchievements", "Earn achievements from retroachievements.org (account and internet needed)",
            ra.enabled) { ra.changeEnabled(it) }
        if (ra.enabled) {
            Divider()
            if (ra.user != null) {
                ActionRow(Icons.Default.AccountCircle, "Logged in as ${ra.user}", "${ra.score} hardcore · ${ra.scoreSoftcore} softcore points" + (ra.gameTitle?.let { " · playing $it" } ?: ""),
                    "Log out", { ra.logout() })
            } else {
                var raUser by remember { mutableStateOf("") }
                var raPass by remember { mutableStateOf("") }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(raUser, { raUser = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(raPass, { raPass = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { ra.login(raUser, raPass); raPass = "" }, enabled = !ra.loginPending && raUser.isNotBlank() && raPass.isNotBlank()) {
                            Text(if (ra.loginPending) "Logging in…" else "Log in")
                        }
                        ra.loginError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 12.dp)) }
                    }
                    Text("Your password is only sent to retroachievements.org; GBSat keeps the login token, not the password.",
                        style = MaterialTheme.typography.bodySmall, color = Dim)
                }
            }
            Divider()
            ToggleRow(Icons.Default.LocalFireDepartment, "Hardcore mode",
                "More points; save state loading and cheats are disabled. Turning it on during a game resets the game.",
                ra.hardcore) { ra.changeHardcore(it) }
        }
    }
}

@Composable
private fun AboutPage() {
    val ctx = LocalContext.current
    val versionName = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    Group(null, null) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.height(12.dp))
            Text("GBSat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Sega Saturn emulation for Android", color = Dim)
            Spacer(Modifier.height(4.dp))
            Text("Version $versionName", color = Gold, style = MaterialTheme.typography.labelLarge)
        }
    }
    Group("Credits", Icons.Default.Favorite) {
        InfoRow("Ymir", "Saturn emulator core by StrikerX3 and contributors (GPLv3)")
        Divider()
        InfoRow("rcheevos", "RetroAchievements client library")
        Divider()
        InfoRow("libretro-database", "Cheat database (CC BY-SA 4.0) and cover art (libretro-thumbnails)")
        Divider()
        InfoRow("SegaXtreme community", "Saturn widescreen codes")
    }
    Group("License", Icons.Default.Gavel) {
        Text("GBSat is a derivative work of Ymir and is released under the GNU General Public License v3. " +
            "Sega Saturn is a trademark of Sega; GBSat is not affiliated with Sega.",
            style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(16.dp))
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Building blocks

@Composable
private fun Group(title: String?, icon: ImageVector?, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) Row(Modifier.padding(start = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Gold, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Gold, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = CardColor, border = CardBorder, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = .08f))

@Composable
private fun RowIcon(icon: ImageVector, warn: Boolean = false) =
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background((if (warn) Color(0xFFFF8A65) else Color(0xFFB9A6FF)).copy(alpha = .16f)),
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (warn) Color(0xFFFF8A65) else Color(0xFFB9A6FF), modifier = Modifier.size(22.dp)) }

@Composable
private fun ToggleRow(icon: ImageVector, title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Dim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String?, action: String, onAction: () -> Unit, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon, warn)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (warn) Color(0xFFFFB199) else Dim,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(title: String, sub: String?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Dim)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(selected = i == selected, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = Gold.copy(alpha = .25f), activeContentColor = Color.White,
                        inactiveContainerColor = Color.Transparent, inactiveContentColor = Dim, activeBorderColor = Gold.copy(alpha = .6f),
                        inactiveBorderColor = Color.White.copy(alpha = .2f)),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = Dim)
    }
}

@Composable
private fun MappingRow(b: SaturnButton, keys: String, selected: Boolean, onRemap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) Gold.copy(alpha = .12f) else Color.Transparent).clickable(onClick = onRemap)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonBadge(b)
        Spacer(Modifier.width(14.dp))
        Text(saturnLabel(b), style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
        Text(keys, style = MaterialTheme.typography.bodyMedium, color = if (keys == "Unassigned") Color(0xFFFFB199) else Gold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Edit, "Remap", tint = Dim, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HotkeyRow(a: HotkeyAction, keys: Set<Int>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(when (a) {
            HotkeyAction.QuickSave -> Icons.Default.Save
            HotkeyAction.QuickLoad -> Icons.Default.Restore
            HotkeyAction.FastForward -> Icons.Default.FastForward
            HotkeyAction.Menu -> Icons.Default.Menu
        })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
            if (keys.isEmpty()) Text("Not set", style = MaterialTheme.typography.bodySmall, color = Dim)
            else Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                keys.forEachIndexed { i, k ->
                    if (i > 0) Text("+", color = Dim)
                    Text(keyName(k), color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.background(Color.White.copy(alpha = .12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        if (keys.isNotEmpty()) TextButton(onClick = { Emulator.bindHotkey(a, emptySet()) }) { Text("Clear") }
        FilledTonalButton(onClick = { Emulator.hotkeyCaptureKeys = emptySet(); Emulator.hotkeyCapture = a }) { Text("Set") }
    }
}

private fun saturnLabel(b: SaturnButton) = when (b) {
    SaturnButton.Up -> "D-pad up"; SaturnButton.Down -> "D-pad down"; SaturnButton.Left -> "D-pad left"; SaturnButton.Right -> "D-pad right"
    SaturnButton.L -> "L shoulder"; SaturnButton.R -> "R shoulder"
    else -> "${b.name} button"
}

@Composable
private fun ButtonBadge(b: SaturnButton) {
    val (text, icon) = when (b) {
        SaturnButton.Up -> null to Icons.Default.KeyboardArrowUp
        SaturnButton.Down -> null to Icons.Default.KeyboardArrowDown
        SaturnButton.Left -> null to Icons.Default.KeyboardArrowLeft
        SaturnButton.Right -> null to Icons.Default.KeyboardArrowRight
        SaturnButton.Start -> "ST" to null
        else -> b.name to null
    }
    Box(Modifier.size(34.dp).clip(CircleShape).background(faceColor(b)), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, tint = Color.White) else Text(text!!, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

private fun faceColor(b: SaturnButton) = when (b) {
    SaturnButton.A, SaturnButton.B, SaturnButton.C -> Color(0xFF5E5A78)
    SaturnButton.X, SaturnButton.Y, SaturnButton.Z -> Color(0xFF47435F)
    SaturnButton.L, SaturnButton.R -> Color(0xFF3A3652)
    SaturnButton.Start -> Color(0xFF6E58D8)
    else -> Color(0xFF2E2A45)
}

/**
 * Schematic Saturn-style pad: D-pad on the left, A/B/C (bottom) and X/Y/Z (top) on the right, L/R shoulders and Start.
 * Tapping a button selects it for remapping; the selected button is highlighted.
 */
@Composable
private fun SaturnPadDiagram(highlight: SaturnButton?, onSelect: (SaturnButton) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val body = Path().apply {
                val cw = size.width; val ch = size.height
                moveTo(cw * .20f, ch * .18f)
                lineTo(cw * .80f, ch * .18f)
                cubicTo(cw * .98f, ch * .18f, cw * 1.0f, ch * .70f, cw * .90f, ch * .90f)
                cubicTo(cw * .84f, ch * 1.0f, cw * .70f, ch * .96f, cw * .64f, ch * .80f)
                lineTo(cw * .36f, ch * .80f)
                cubicTo(cw * .30f, ch * .96f, cw * .16f, ch * 1.0f, cw * .10f, ch * .90f)
                cubicTo(cw * 0f, ch * .70f, cw * .02f, ch * .18f, cw * .20f, ch * .18f)
                close()
            }
            // Shoulders
            drawRoundRect(Color(0xFF3A3652), Offset(size.width * .12f, size.height * .06f), Size(size.width * .22f, size.height * .16f), CornerRadius(18f))
            drawRoundRect(Color(0xFF3A3652), Offset(size.width * .66f, size.height * .06f), Size(size.width * .22f, size.height * .16f), CornerRadius(18f))
            drawPath(body, Color(0xFF26223D))
            drawPath(body, Color.White.copy(alpha = .18f), style = Stroke(width = 3f))
            // D-pad well
            drawCircle(Color(0xFF1C1930), radius = size.height * .26f, center = Offset(size.width * .23f, size.height * .50f))
        }
        @Composable
        fun Btn(b: SaturnButton, x: Float, y: Float, bw: Dp, bh: Dp, label: String?, icon: ImageVector? = null, shape: RoundedCornerShape = CircleShape) {
            val sel = highlight == b
            Box(
                Modifier.offset(w * x - bw / 2, h * y - bh / 2).size(bw, bh).clip(shape)
                    .background(if (sel) Gold else faceColor(b))
                    .border(if (sel) 2.dp else 1.dp, if (sel) Color.White else Color.White.copy(alpha = .25f), shape)
                    .clickable { onSelect(b) },
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) Icon(icon, b.name, tint = if (sel) Color.Black else Color.White, modifier = Modifier.size(bh * .8f))
                else if (label != null) Text(label, color = if (sel) Color.Black else Color.White, fontWeight = FontWeight.Bold,
                    fontSize = (bh.value * .42f).sp, textAlign = TextAlign.Center)
            }
        }
        val unit = h * 0.17f
        // Shoulders
        Btn(SaturnButton.L, .23f, .14f, w * .20f, h * .13f, "L", shape = RoundedCornerShape(10.dp))
        Btn(SaturnButton.R, .77f, .14f, w * .20f, h * .13f, "R", shape = RoundedCornerShape(10.dp))
        // D-pad
        Btn(SaturnButton.Up, .23f, .33f, unit, unit, null, Icons.Default.KeyboardArrowUp, RoundedCornerShape(6.dp))
        Btn(SaturnButton.Down, .23f, .67f, unit, unit, null, Icons.Default.KeyboardArrowDown, RoundedCornerShape(6.dp))
        Btn(SaturnButton.Left, .23f - (unit / w), .50f, unit, unit, null, Icons.Default.KeyboardArrowLeft, RoundedCornerShape(6.dp))
        Btn(SaturnButton.Right, .23f + (unit / w), .50f, unit, unit, null, Icons.Default.KeyboardArrowRight, RoundedCornerShape(6.dp))
        // Start
        Btn(SaturnButton.Start, .50f, .62f, w * .11f, h * .10f, "START", shape = RoundedCornerShape(50))
        // Face buttons: X Y Z (top row), A B C (bottom row)
        val face = h * 0.18f
        Btn(SaturnButton.X, .66f, .38f, face, face, "X")
        Btn(SaturnButton.Y, .75f, .33f, face, face, "Y")
        Btn(SaturnButton.Z, .84f, .38f, face, face, "Z")
        Btn(SaturnButton.A, .64f, .62f, face, face, "A")
        Btn(SaturnButton.B, .74f, .58f, face, face, "B")
        Btn(SaturnButton.C, .84f, .62f, face, face, "C")
    }
}

/** Remap / hotkey capture prompts (drawn in this window, not a Dialog, so controller events reach MainActivity). */
@Composable
private fun CapturePrompts() {
    val remap = Emulator.remapTarget
    val hk = Emulator.hotkeyCapture
    if (remap == null && hk == null) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 460.dp).padding(16.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (remap != null) {
                    Text("Remap ${saturnLabel(remap)}", style = MaterialTheme.typography.titleLarge)
                    SaturnPadDiagram(highlight = remap, onSelect = { Emulator.remapTarget = it }, modifier = Modifier.fillMaxWidth().aspectRatio(2.1f))
                    Text("Press the button on your controller you want to use for it…", color = Dim, textAlign = TextAlign.Center)
                    TextButton(onClick = { Emulator.remapTarget = null }) { Text("Cancel") }
                } else if (hk != null) {
                    Text("Set ${hk.label}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (Emulator.hotkeyCaptureKeys.isEmpty()) "Press a button, or hold several buttons together, then release."
                        else Emulator.hotkeyCaptureKeys.joinToString(" + ") { keyName(it) },
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { Emulator.hotkeyCapture = null }) { Text("Cancel") }
                }
            }
        }
    }
}
