package app.gbsat.android.ui

import gb.frame.*
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
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import gb.frame.HotkeyAction
import gb.frame.keyName
import app.gbsat.android.NativeBridge
import app.gbsat.android.R
import app.gbsat.android.SaturnButton
import app.gbsat.android.Storage
import kotlinx.coroutines.launch

/** Option pages opened from the side menu. */
enum class SettingsPage(val label: String, val icon: ImageVector) {
    System("System", Icons.Default.Tune),
    Video("Video", Icons.Default.Tv),
    Audio("Audio", Icons.AutoMirrored.Filled.VolumeUp),
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
fun SettingsScreen(onBack: () -> Unit, toast: (String) -> Unit, page: SettingsPage? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = page ?: SettingsPage.System

    val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { toast(Emulator.importBios(ctx, it) ?: "BIOS loaded") } }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { Storage.takeFolder(it); Emulator.gamesDir = it.toString(); Emulator.savePrefs() }
    }
    var dataMsg by remember { mutableStateOf<String?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { scope.launch { dataMsg = Emulator.exportData(it) ?: "Data copied to the \"GBSat backup\" folder." } }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { scope.launch { dataMsg = Emulator.importData(it) ?: "Data imported. Restart GBSat to load it." } }
    }
    var cartFile by remember { mutableStateOf("") }
    val cartPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { toast(Emulator.importCartRom(it, cartFile) ?: "Cartridge ROM saved") } }
    }
    dataMsg?.let { m -> AlertDialog(onDismissRequest = { dataMsg = null }, title = { Text("Data") }, text = { Text(m) },
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
                        SettingsPage.System -> SystemPage({ biosPicker.launch(arrayOf("*/*")) }, { folderPicker.launch(null) },
                            onExport = { exportPicker.launch(null) }, onImport = { importPicker.launch(null) },
                            onCart = { cartFile = it; cartPicker.launch(arrayOf("*/*")) })
                        SettingsPage.Video -> VideoPage()
                        SettingsPage.Audio -> AudioPage()
                        SettingsPage.Performance -> PerformancePage()
                        SettingsPage.Controls -> ControlsPage()
                        SettingsPage.Extras -> ExtrasPage(onLayoutChanged = { MenuState.open.value = false; onBack() })
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
    onBios: () -> Unit, onGamesFolder: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onCart: (String) -> Unit,
) {
    SettingsGroup("Getting started", Icons.Default.PlayCircle) {
        ActionRow(Icons.Default.Memory, "Saturn BIOS", if (Emulator.iplLoaded) "Loaded" else "Required: choose your IPL ROM (512 KiB)",
            if (Emulator.iplLoaded) "Replace" else "Choose", onBios, warn = !Emulator.iplLoaded)
        SettingsDivider()
        ActionRow(Icons.Default.Folder, "Games folder", if (Emulator.gamesDir.isBlank()) "Not set" else Storage.folderLabel(Emulator.gamesDir),
            "Choose", onGamesFolder, warn = Emulator.gamesDir.isBlank())
    }
    SettingsGroup("Data", Icons.Default.Storage) {
        Emulator.legacyDataDir?.let { old ->
            ActionRow(Icons.Default.Warning, "Data from the previous version",
                "Your saves were in $old. Import that folder to bring them back.", "Import", onImport, warn = true)
            SettingsDivider()
        }
        ActionRow(Icons.Default.Upload, "Export data", "Copy saves and save states to a folder (backup)", "Export", onExport)
        SettingsDivider()
        ActionRow(Icons.Default.Download, "Import data", "Restore a GBSat backup or an older GBSat data folder", "Import", onImport)
    }
    SettingsGroup("Cartridge ROMs", Icons.Default.SdCard) {
        ActionRow(Icons.Default.SdCard, "King of Fighters '95", if (Emulator.hasCartRom("kof95.bin")) "Installed" else "ROM cartridge dump (needed by this game)",
            "Choose", { onCart("kof95.bin") })
        SettingsDivider()
        ActionRow(Icons.Default.SdCard, "Ultraman", if (Emulator.hasCartRom("ultraman.bin")) "Installed" else "ROM cartridge dump (needed by this game)",
            "Choose", { onCart("ultraman.bin") })
    }
    SettingsGroup("Save states", Icons.Default.Save) {
        ToggleRow(Icons.Default.Save, "Auto save", "Save the game's state when you close it", Emulator.autoSave, ch { Emulator.autoSave = it })
        SettingsDivider()
        ToggleRow(Icons.Default.Restore, "Auto load", "Continue from the game's latest save state (auto, quick or slot) when you start it", Emulator.autoLoad, ch { Emulator.autoLoad = it })
    }
    SettingsGroup("Startup", Icons.Default.RocketLaunch) {
        ToggleRow(Icons.Default.FastForward, "Fast boot", "Skip the BIOS clock and language screens", Emulator.fastBoot, ch { Emulator.fastBoot = it })
    }
}

@Composable
private fun VideoPage() {
    SettingsGroup("Renderer", Icons.Default.Memory) {
        ChoiceRow("Renderer",
            if (Emulator.renderer == 4) "Saturn graphics emulated on the GPU with Vulkan; best on strong GPUs (falls back to the CPU if unsupported)"
            else "Saturn graphics emulated on the CPU, shown with OpenGL ES (filters and shaders); best on most devices",
            listOf("CPU (OpenGL)", "GPU (Vulkan)"), if (Emulator.renderer == 4) 1 else 0,
            ch { Emulator.renderer = if (it == 1) 4 else 1 })
        SettingsDivider()
        ToggleRow(Icons.Default.Tune, "Recommended settings per game", "Heavy games start with the renderer, frame skip and threaded audio that tested best on similar devices. Changes you make during a game are kept for that game",
            Emulator.useGameProfiles, ch { Emulator.useGameProfiles = it })
        if (Emulator.renderer == 4) {
            SettingsDivider()
            ChoiceRow("Internal resolution", "Sharper 3D sprites and polygons; higher values need a faster GPU. Native matches the screen (${Emulator.nativeScale}x here)",
                listOf("1x", "2x", "3x", "4x", "Native"), if (Emulator.resScale == 0) 4 else Emulator.resScale - 1,
                ch { Emulator.resScale = if (it == 4) 0 else it + 1 })
            val gpuErr = remember { runCatching { NativeBridge.nativeGetGpuError().orEmpty() }.getOrDefault("") }
            if (gpuErr.isNotEmpty()) Text("Last GPU error: $gpuErr", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
    SettingsGroup("Picture", Icons.Default.AspectRatio) {
        ChoiceRow("Display mode", "16:9 turns on built-in widescreen hacks for about 20 games; others are stretched",
            listOf("4:3", "Fullscreen", "16:9"), Emulator.aspect, ch { Emulator.aspect = it })
        if (Emulator.renderer != 0) {
            SettingsDivider()
            ChoiceRow("Texture filtering", null, listOf("Nearest", "Bilinear"), Emulator.filter.coerceAtMost(1), ch { Emulator.filter = it })
            SettingsDivider()
            val shaderIds = listOf(0, 2, 4, 3, 1) // internal ids: 0 none, 1 scanlines, 2 CRT, 3 LCD3x, 4 dots
            ChoiceRow("Shader", null, listOf("None", "CRT", "Dots", "LCD3x", "Scanlines"),
                shaderIds.indexOf(Emulator.shader).coerceAtLeast(0), ch { Emulator.shader = shaderIds[it] })
        }
    }
    SettingsGroup("Overlay", Icons.Default.Layers) {
        ToggleRow(Icons.Default.Insights, "Show all monitoring", "FPS, speed, CPU, GPU, renderer, resolution and frame time at the top right",
            Emulator.showAllStats) { Emulator.showAllStats = it; Emulator.savePrefs() }
        SettingsDivider()
        ToggleRow(Icons.Default.Speed, "Show FPS", "Frames per second", Emulator.showFps) { Emulator.showFps = it; Emulator.savePrefs() }
        SettingsDivider()
        ToggleRow(Icons.Default.Percent, "Show speed", "Emulation speed in percent (100% = full speed)", Emulator.showSpeed) { Emulator.showSpeed = it; Emulator.savePrefs() }
        SettingsDivider()
        ToggleRow(Icons.Default.Memory, "Show renderer", "CPU (OpenGL) or GPU (Vulkan)", Emulator.showRenderer) { Emulator.showRenderer = it; Emulator.savePrefs() }
        SettingsDivider()
        ToggleRow(Icons.Default.PhotoSizeSelectLarge, "Show resolution", "Output resolution (including the internal resolution scale)",
            Emulator.showResolution) { Emulator.showResolution = it; Emulator.savePrefs() }
        SettingsDivider()
        ToggleRow(Icons.Default.DeveloperBoard, "Show CPU", "Chip name and GBSat's CPU usage", Emulator.showCpu) { Emulator.showCpu = it; Emulator.savePrefs() }
        SettingsDivider()
        ToggleRow(Icons.Default.Videocam, "Show GPU", "GPU name and load (when the device reports it)", Emulator.showGpu) { Emulator.showGpu = it; Emulator.savePrefs() }
        SettingsDivider()
        ToggleRow(Icons.Default.Timer, "Show frame time", "Time spent emulating each frame (16.7 ms or less = full speed)", Emulator.showFrameTime) { Emulator.showFrameTime = it; Emulator.savePrefs() }
        SettingsDivider()
        ToggleRow(Icons.Default.FastForward, "Show fast-forward button", "On-screen button to turn fast-forward on and off",
            Emulator.showFfButton) { Emulator.showFfButton = it; Emulator.savePrefs() }
    }
}

@Composable
private fun AudioPage() {
    SettingsGroup("Output", Icons.AutoMirrored.Filled.VolumeUp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(if (Emulator.volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp)
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
    SettingsGroup("Fast-forward", Icons.Default.FastForward) {
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
    SettingsGroup("This device", Icons.Default.PhoneAndroid) {
        InfoRow("Device class", Emulator.device.summary)
        SettingsDivider()
        InfoRow("Recommended preset", "${Emulator.recommendedPreset.label}, applied on first run")
    }
    SettingsGroup("Speed", Icons.Default.Speed) {
        ToggleRow(Icons.Default.SkipNext, "Frame skip (auto)", "Skips rendering frames only while the game runs below full speed",
            Emulator.frameSkip, ch { Emulator.frameSkip = it })
        SettingsDivider()
        val cdSpeeds = listOf(2, 4, 8, 16)
        ChoiceRow("CD read speed", "Shorter loading screens; use Original if a game's videos or music go out of sync",
            listOf("Original", "4x", "8x", "16x"), cdSpeeds.indexOf(Emulator.cdSpeed).coerceAtLeast(0), ch<Int> { Emulator.cdSpeed = cdSpeeds[it] })
    }
    SettingsGroup("Emulation", Icons.Default.Memory) {
        ToggleRow(Icons.AutoMirrored.Filled.CallSplit, "Threaded VDP", "Render the VDP1/VDP2 graphics on separate CPU threads",
            Emulator.threadedVdp, ch { Emulator.threadedVdp = it })
        SettingsDivider()
        ChoiceRow("Threaded audio", "Sound chip and its 68000 on a separate thread. Auto tries both while a game runs slow and remembers the faster one per game",
            listOf("Auto", "On", "Off"), Emulator.scspMode, ch { Emulator.scspMode = it })
        SettingsDivider()
        ToggleRow(Icons.Default.Verified, "SH-2 cache emulation", "More accurate, slightly slower", Emulator.sh2Cache, ch { Emulator.sh2Cache = it })
        SettingsDivider()
        ToggleRow(Icons.Default.Speed, "Skip idle loops", "The SH-2 CPUs skip loops that only wait for a value to change. Much faster, same result; turn off if a game misbehaves",
            Emulator.idleSkip, ch { Emulator.idleSkip = it })
    }
    SettingsGroup("Loading", Icons.Default.Album) {
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
            SettingsGroup("Saturn controller", Icons.Default.SportsEsports) {
                ChoiceRow("Controller type", "The 3D Control Pad adds the analog stick and analog L/R used by some games",
                    listOf("Control Pad", "3D Control Pad"), if (Emulator.analogPad) 1 else 0, ch<Int> { Emulator.analogPad = it == 1 })
            }
            SettingsGroup("Button mapping", Icons.Default.Gamepad) {
                Text("Tap a button on the pad or in the list, then press the button on your controller.",
                    style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                SaturnPadDiagram(highlight = Emulator.remapTarget, onSelect = { Emulator.remapTarget = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(2.1f))
                Spacer(Modifier.height(8.dp))
                val order = listOf(SaturnButton.Up, SaturnButton.Down, SaturnButton.Left, SaturnButton.Right, SaturnButton.A, SaturnButton.B,
                    SaturnButton.C, SaturnButton.X, SaturnButton.Y, SaturnButton.Z, SaturnButton.L, SaturnButton.R, SaturnButton.Start)
                order.forEachIndexed { i, b ->
                    if (i > 0) SettingsDivider()
                    val keys = Emulator.keysFor(b).joinToString(", ") { keyName(it) }
                    MappingRow(b, keys.ifEmpty { "Unassigned" }, selected = Emulator.remapTarget == b) { Emulator.remapTarget = b }
                }
                SettingsDivider()
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { Emulator.resetKeyMap() }) { Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text("Reset to defaults") }
                }
            }
        }
        1 -> {
            SettingsGroup("Hotkeys", Icons.Default.Keyboard) {
                Text("Bind one button, or hold several together (a combination) and release. Buttons in a combination are not sent to the game.",
                    style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                HotkeyAction.entries.forEachIndexed { i, a ->
                    if (i > 0) SettingsDivider()
                    val keys = Emulator.hotkeys[a].orEmpty()
                    HotkeyRow(a, keys)
                }
            }
            SettingsGroup("Fast-forward", Icons.Default.FastForward) {
                ChoiceRow("Fast-forward hotkey", "Hold: fast while held · Toggle: press once to switch on or off",
                    listOf("Hold", "Toggle"), if (Emulator.ffToggle) 1 else 0, ch<Int> { Emulator.ffToggle = it == 1 })
            }
        }
        else -> {
            SettingsGroup("On-screen controls", Icons.Default.TouchApp) {
                ToggleRow(Icons.Default.TouchApp, "Show touch controls", "Hidden automatically while a controller is connected (handhelds included)",
                    Emulator.showTouchControls, ch { Emulator.showTouchControls = it })
            }
        }
    }
}

@Composable
private fun ExtrasPage(onLayoutChanged: () -> Unit) {
    HomeThemeSettings(onLayoutChanged)
    RetroAchievementsSettings("GBSat")
}

@Composable
private fun AboutPage() {
    val ctx = LocalContext.current
    val versionName = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
    SettingsGroup(null, null) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.height(12.dp))
            Text("GBSat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Sega Saturn emulation for Android", color = Dim)
            Spacer(Modifier.height(4.dp))
            Text("Version $versionName", color = Gold, style = MaterialTheme.typography.labelLarge)
        }
    }
    SettingsGroup("Credits", Icons.Default.Favorite) {
        InfoRow("Ymir", "Saturn emulator core by StrikerX3 and contributors (GPLv3)")
        SettingsDivider()
        InfoRow("rcheevos", "RetroAchievements client library")
        SettingsDivider()
        InfoRow("libretro-database", "Cheat database (CC BY-SA 4.0) and cover art (libretro-thumbnails)")
        SettingsDivider()
        InfoRow("SegaXtreme community", "Saturn widescreen codes")
    }
    SettingsGroup("License", Icons.Default.Gavel) {
        Text("GBSat is a derivative work of Ymir and is released under the GNU General Public License v3. " +
            "Sega Saturn is a trademark of Sega; GBSat is not affiliated with Sega.",
            style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(16.dp))
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Building blocks

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
        SaturnButton.Left -> null to Icons.AutoMirrored.Filled.KeyboardArrowLeft
        SaturnButton.Right -> null to Icons.AutoMirrored.Filled.KeyboardArrowRight
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
private fun SaturnPadDiagram(highlight: SaturnButton?, onSelect: (SaturnButton) -> Unit, modifier: Modifier) =
    PadDiagram(SaturnPad, highlight?.name, { id -> onSelect(SaturnButton.valueOf(id)) }, modifier)

/** Saturn control pad for the shared controller diagram: D-pad, Start, L/R and the A B C / X Y Z face buttons. */
private val SaturnPad = PadLayout(listOf(
    PadButton("L", "L", x = .23f, y = .14f, w = .20f, h = .13f, wOfWidth = true, shape = padRect(10), color = faceColor(SaturnButton.L)),
    PadButton("R", "R", x = .77f, y = .14f, w = .20f, h = .13f, wOfWidth = true, shape = padRect(10), color = faceColor(SaturnButton.R)),
    PadButton("Up", icon = Icons.Default.KeyboardArrowUp, x = .23f, y = .33f, w = .17f, h = .17f, shape = padRect(), color = faceColor(SaturnButton.Up)),
    PadButton("Down", icon = Icons.Default.KeyboardArrowDown, x = .23f, y = .67f, w = .17f, h = .17f, shape = padRect(), color = faceColor(SaturnButton.Down)),
    PadButton("Left", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, x = .15f, y = .50f, w = .17f, h = .17f, shape = padRect(), color = faceColor(SaturnButton.Left)),
    PadButton("Right", icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, x = .31f, y = .50f, w = .17f, h = .17f, shape = padRect(), color = faceColor(SaturnButton.Right)),
    PadButton("Start", "START", x = .50f, y = .62f, w = .11f, h = .10f, wOfWidth = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = faceColor(SaturnButton.Start)),
    PadButton("X", "X", x = .66f, y = .38f, w = .18f, h = .18f, color = faceColor(SaturnButton.X)),
    PadButton("Y", "Y", x = .75f, y = .33f, w = .18f, h = .18f, color = faceColor(SaturnButton.Y)),
    PadButton("Z", "Z", x = .84f, y = .38f, w = .18f, h = .18f, color = faceColor(SaturnButton.Z)),
    PadButton("A", "A", x = .64f, y = .62f, w = .18f, h = .18f, color = faceColor(SaturnButton.A)),
    PadButton("B", "B", x = .74f, y = .58f, w = .18f, h = .18f, color = faceColor(SaturnButton.B)),
    PadButton("C", "C", x = .84f, y = .62f, w = .18f, h = .18f, color = faceColor(SaturnButton.C)),
))

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
