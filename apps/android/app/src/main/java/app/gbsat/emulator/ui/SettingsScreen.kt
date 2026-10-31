package app.gbsat.emulator.ui

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
import app.gbsat.emulator.Emulator
import gb.frame.HotkeyAction
import gb.frame.keyName
import app.gbsat.emulator.NativeBridge
import app.gbsat.emulator.R
import app.gbsat.emulator.SaturnButton
import app.gbsat.emulator.Storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.documentfile.provider.DocumentFile

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

    var importingBios by remember { mutableStateOf(false) }
    var biosCandidates by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    fun importBios(uri: Uri) {
        if (!importingBios) scope.launch {
            importingBios = true
            try { toast(Emulator.importBios(ctx, uri) ?: "BIOS loaded") }
            finally { importingBios = false }
        }
    }
    val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(::importBios) }
    val biosFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null && !importingBios) scope.launch {
            importingBios = true
            try {
                val files = withContext(Dispatchers.IO) {
                    val root = requireNotNull(DocumentFile.fromTreeUri(ctx, uri)) { "Could not open BIOS folder" }
                    root.listFiles().filter { it.isFile && it.length() == 512L * 1024 }
                }
                when (files.size) {
                    0 -> toast("No 512 KiB Saturn BIOS found in that folder")
                    1 -> toast(Emulator.importBios(ctx, files.single().uri) ?: "BIOS loaded")
                    else -> biosCandidates = files
                }
            } catch (error: Exception) { toast(error.message ?: "Could not import BIOS") }
            finally { importingBios = false }
        }
    }
    if (biosCandidates.isNotEmpty()) AlertDialog(
        onDismissRequest = { biosCandidates = emptyList() }, title = { Text("Choose Saturn BIOS") },
        text = { Column { biosCandidates.forEach { file -> TextButton(onClick = {
            biosCandidates = emptyList(); importBios(file.uri)
        }) { Text(file.name ?: "BIOS") } } } },
        confirmButton = { TextButton(onClick = { biosCandidates = emptyList() }) { Text("Cancel") } },
    )
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
                        SettingsPage.System -> SystemPage({ biosPicker.launch(arrayOf("*/*")) }, { biosFolderPicker.launch(null) }, importingBios, { folderPicker.launch(null) },
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
    onBios: () -> Unit, onBiosFolder: () -> Unit, importingBios: Boolean,
    onGamesFolder: () -> Unit, onCart: (String) -> Unit,
) {
    SystemSettings(
        dataFolder = Emulator.dataFolder,
        gamesFolder = SystemFolderSetting(Emulator.gamesDir.takeIf { it.isNotBlank() }?.let(Storage::folderLabel),
            "Choose the folder containing your Saturn disc images.", onGamesFolder),
        biosFolder = SystemBiosFolderSetting(
            path = if (Emulator.dataFolder.folder != null || Emulator.biosFile.exists()) Emulator.dataFolder.displayPath(Emulator.biosFile.parentFile!!) else null,
            description = "Choose a folder containing your Saturn BIOS (IPL ROM, 512 KiB).",
            files = listOf(SystemBiosFile(Emulator.biosFile.name, Emulator.iplLoaded && Emulator.biosFile.exists())),
            onChooseFolder = onBiosFolder, onChooseFiles = onBios, busy = importingBios,
        ),
        autoSave = Emulator.autoSave, autoLoad = Emulator.autoLoad,
        onAutoSave = ch { Emulator.autoSave = it }, onAutoLoad = ch { Emulator.autoLoad = it },
        startup = SystemStartupOptions(
            fastBoot = SystemToggleSetting(Emulator.fastBoot, ch { Emulator.fastBoot = it }, "Skip the BIOS intro and its clock and language screens"),
            skipLoadingScreens = SystemToggleSetting(Emulator.skipLoads, ch { Emulator.skipLoads = it }, "Maximum speed while the CD loads behind a still, silent screen"),
            skipTransitions = SystemToggleSetting(Emulator.skipTransitions, ch { Emulator.skipTransitions = it }, "Maximum speed through silent black screens between scenes"),
            skipSaveWaits = SystemToggleSetting(Emulator.skipSaves, ch { Emulator.skipSaves = it }, "Maximum speed while the game reads or writes its saves"),
        ),
        emulatorFiles = {
            SettingsGroup("Cartridge ROMs", Icons.Default.SdCard) {
                ActionRow(Icons.Default.SdCard, "King of Fighters '95", if (Emulator.hasCartRom("kof95.bin")) "Installed" else "ROM cartridge dump (needed by this game)",
                    "Choose", { onCart("kof95.bin") })
                SettingsDivider()
                ActionRow(Icons.Default.SdCard, "Ultraman", if (Emulator.hasCartRom("ultraman.bin")) "Installed" else "ROM cartridge dump (needed by this game)",
                    "Choose", { onCart("ultraman.bin") })
            }
        },
    )
}

@Composable
private fun VideoPage() {
    SettingsGroup("Renderer", Icons.Default.Memory) {
        ChoiceRow("Renderer",
            when (Emulator.renderer) {
                4 -> "Saturn graphics emulated on the GPU with Vulkan; best on strong GPUs (falls back to the CPU if unsupported)"
                else -> "Saturn graphics emulated on the CPU, shown with OpenGL ES (filters and shaders); best on most devices"
            },
            listOf("CPU (OpenGL)", "GPU (Vulkan)"),
            if (Emulator.renderer == 4) 1 else 0,
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
        ChoiceRow("Display mode", "16:9 expands the view; built-in game hacks can be toggled below",
            listOf("4:3", "Fullscreen", "16:9"), Emulator.aspect) {
            Emulator.aspect = it
            Emulator.savePrefs()
            Emulator.applyOptions()
            Emulator.refreshCheats()
        }
        SettingsDivider()
        gb.frame.WidescreenGameHacksRow(Emulator.widescreenGameHacks) {
            Emulator.widescreenGameHacks = it
            Emulator.savePrefs()
            Emulator.refreshCheats()
        }
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
    // The kit's standard options; GBSat has no audio options of its own here (threaded audio is in Performance)
    gb.frame.AudioSettingsGroups(Emulator.audio)
}

@Composable
private fun PerformancePage() {
    SettingsGroup("This device", Icons.Default.PhoneAndroid) {
        InfoRow("Device class", Emulator.device.summary)
        SettingsDivider()
        InfoRow("Recommended preset", "${Emulator.recommendedPreset.label}, applied on first run")
    }
    SettingsGroup("Speed", Icons.Default.Speed) {
        AdaptiveGraphicsSettingsRow(Emulator.frameSkip, ch { Emulator.frameSkip = it })
        SettingsDivider()
        val cdSpeeds = listOf(2, 4, 8, 16)
        ChoiceRow("CD read speed", "Shorter loading screens. Videos and music streamed from the CD switch back to the original speed; use Original if one still goes out of sync",
            listOf("Original", "4x", "8x", "16x"), cdSpeeds.indexOf(Emulator.cdSpeed).coerceAtLeast(0), ch<Int> { Emulator.cdSpeed = cdSpeeds[it] })

    }
    SettingsGroup("Emulation", Icons.Default.Memory) {
        ChoiceRow("Threaded audio", "Sound chip and its 68000 on a separate thread. Auto tries both while a game runs slow and remembers the faster one per game",
            listOf("Auto", "On", "Off"), Emulator.scspMode, ch { Emulator.scspMode = it })
        SettingsDivider()
        ToggleRow(Icons.Default.Verified, "SH-2 cache emulation", "More accurate, slightly slower", Emulator.sh2Cache, ch { Emulator.sh2Cache = it })
        SettingsDivider()
        ChoiceRow("CPU sync", "How often the two SH-2 CPUs are kept in step. Fast (default) syncs less often for about 10% less work; use Accurate if a game misbehaves",
            listOf("Accurate", "Fast"), Emulator.cpuSync, ch<Int> { Emulator.cpuSync = it })
        SettingsDivider()
        ToggleRow(Icons.Default.Speed, "CPU recompiler", "On by default: faster on weak devices (Daytona USA +24% on an RG DS) and less CPU use on the others, slightly less accurate. Works with Fast CPU sync; games that need tighter sync (Burning Rangers) keep the interpreter. Turn off if a game misbehaves",
            Emulator.sh2Recompiler, ch { Emulator.sh2Recompiler = it })
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
