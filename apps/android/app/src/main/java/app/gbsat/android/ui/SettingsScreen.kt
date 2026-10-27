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
import app.gbsat.emulator.R
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
    var cartFile by remember { mutableStateOf("") }
    val cartPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { toast(Emulator.importCartRom(it, cartFile) ?: "Cartridge ROM saved") } }
    }

    Box(Modifier.fillMaxSize().background(palette.backgroundBrush)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(current.icon, null, tint = palette.highlight)
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
                            onCart = { cartFile = it; cartPicker.launch(arrayOf("*/*")) })
                        SettingsPage.Video -> VideoPage()
                        SettingsPage.Audio -> AudioPage()
                        SettingsPage.Performance -> PerformancePage()
                        // The kit's standard Controls page; GBSat only adds the Saturn pad type and supplies the pad (SaturnControls)
                        SettingsPage.Controls -> ControlsPage(SaturnControls) {
                            ChoiceRow("Controller type", "The 3D Control Pad adds the analog stick and analog L/R used by some games",
                                listOf("Control Pad", "3D Control Pad"), if (Emulator.analogPad) 1 else 0, ch<Int> { Emulator.analogPad = it == 1 })
                        }
                        SettingsPage.Extras -> ExtrasPage()
                        // The kit's standard About page: "Sega Saturn Emulator for Android", version and Application id
                        SettingsPage.About -> AboutPage(
                            appName = "GBSat", manufacturer = "Sega", console = "Saturn",
                            icon = rememberAppIconPainter(R.drawable.ic_launcher_foreground),
                            credits = listOf(
                                AboutCredit("Ymir", "Saturn emulator core by StrikerX3 and contributors (GPLv3)"),
                                AboutCredit("rcheevos", "RetroAchievements client library"),
                                AboutCredit("libretro-database", "Cheat database (CC BY-SA 4.0) and cover art (libretro-thumbnails)"),
                                AboutCredit("SegaXtreme community", "Saturn widescreen codes"),
                            ),
                            license = "GBSat is a derivative work of Ymir and is released under the GNU General Public License v3. " +
                                "Sega Saturn is a trademark of Sega; GBSat is not affiliated with Sega.",
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        ControlsCapturePrompts(SaturnControls)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Pages

/** Wraps a setter so that the change is saved and applied. */
private fun <T> ch(set: (T) -> Unit): (T) -> Unit = { v -> set(v); Emulator.savePrefs(); Emulator.applyOptions() }

@Composable
private fun SystemPage(
    onBios: () -> Unit, onGamesFolder: () -> Unit, onCart: (String) -> Unit,
) {
    SettingsGroup("Getting started", Icons.Default.PlayCircle) {
        ActionRow(Icons.Default.Memory, "Saturn BIOS", if (Emulator.iplLoaded) "Loaded" else "Required: choose your IPL ROM (512 KiB)",
            if (Emulator.iplLoaded) "Replace" else "Choose", onBios, warn = !Emulator.iplLoaded)
        SettingsDivider()
        ActionRow(Icons.Default.Folder, "Games folder", if (Emulator.gamesDir.isBlank()) "Not set" else Storage.folderLabel(Emulator.gamesDir),
            "Choose", onGamesFolder, warn = Emulator.gamesDir.isBlank())
    }
    SettingsGroup("Data", Icons.Default.Storage) {
        gb.frame.DataFolderControls(Emulator.dataFolder)
        Emulator.legacyDataDir?.let { old ->
            InfoRow("Data from the previous version", "Choose $old as the Data folder to restore its files after reopening.")
        }
    }
    SettingsGroup("Cartridge ROMs", Icons.Default.SdCard) {
        ActionRow(Icons.Default.SdCard, "King of Fighters '95", if (Emulator.hasCartRom("kof95.bin")) "Installed" else "ROM cartridge dump (needed by this game)",
            "Choose", { onCart("kof95.bin") })
        SettingsDivider()
        ActionRow(Icons.Default.SdCard, "Ultraman", if (Emulator.hasCartRom("ultraman.bin")) "Installed" else "ROM cartridge dump (needed by this game)",
            "Choose", { onCart("ultraman.bin") })
    }
    SaveStateSettings(Emulator.autoSave, Emulator.autoLoad, ch { Emulator.autoSave = it }, ch { Emulator.autoLoad = it })
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
            ChoiceRow("Enhanced resolution", "Sharper 3D sprites and polygons; higher values need a faster GPU. Native matches the screen (${Emulator.nativeScale}x here)",
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
            val shaderIds = gb.frame.Shaders.menuOrder
            ChoiceRow("Shader", null, shaderIds.map { gb.frame.Shaders.name(it) },
                shaderIds.indexOf(Emulator.shader).coerceAtLeast(0), ch { Emulator.shader = shaderIds[it] })
            SettingsDivider()
        }
    }
    SettingsGroup("Overlay", Icons.Default.Layers) {
        gb.frame.MonitoringSettingsRows(GBSatMonitoring)
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
                Text("${Emulator.volume}%", color = palette.highlight, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = Emulator.volume.toFloat(), valueRange = 0f..100f, steps = 19,
                onValueChange = { Emulator.volume = it.toInt(); NativeBridge.nativeSetAudio(Emulator.volume, Emulator.ffAudio) },
                onValueChangeFinished = { Emulator.savePrefs() },
                colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
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
        ChoiceRow("CD read speed", "Shorter loading screens. Videos and music streamed from the CD switch back to the original speed; use Original if one still goes out of sync",
            listOf("Original", "4x", "8x", "16x"), cdSpeeds.indexOf(Emulator.cdSpeed).coerceAtLeast(0), ch<Int> { Emulator.cdSpeed = cdSpeeds[it] })
        SettingsDivider()
        // Skip Engine: maximum speed (sound muted) only while one of these is detected
        ToggleRow(Icons.Default.FastForward, "Skip loading screens", "Maximum speed while the CD loads behind a still, silent screen",
            Emulator.skipLoads, ch { Emulator.skipLoads = it })
        SettingsDivider()
        ToggleRow(Icons.Default.FastForward, "Skip transitions", "Maximum speed through silent black screens between scenes",
            Emulator.skipTransitions, ch { Emulator.skipTransitions = it })
        SettingsDivider()
        ToggleRow(Icons.Default.FastForward, "Skip save waits", "Maximum speed while the game reads or writes its saves",
            Emulator.skipSaves, ch { Emulator.skipSaves = it })
    }
    SettingsGroup("Emulation", Icons.Default.Memory) {
        ChoiceRow("Threaded audio", "Sound chip and its 68000 on a separate thread. Auto tries both while a game runs slow and remembers the faster one per game",
            listOf("Auto", "On", "Off"), Emulator.scspMode, ch { Emulator.scspMode = it })
        SettingsDivider()
        ToggleRow(Icons.Default.Verified, "SH-2 cache emulation", "More accurate, slightly slower", Emulator.sh2Cache, ch { Emulator.sh2Cache = it })
    }
    SettingsGroup("Loading", Icons.Default.Album) {
        ToggleRow(Icons.Default.Download, "Preload disc into RAM", "Faster loading, uses more memory", Emulator.preloadDisc, ch { Emulator.preloadDisc = it })
    }
}

@Composable
private fun ExtrasPage() {
    RetroAchievementsSettings("GBSat")
}

// ---------------------------------------------------------------------------------------------------------------------
// Building blocks
