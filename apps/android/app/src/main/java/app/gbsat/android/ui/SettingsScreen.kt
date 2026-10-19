package app.gbsat.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.gbsat.android.Emulator
import app.gbsat.android.NativeBridge
import app.gbsat.android.SaturnButton
import app.gbsat.android.HotkeyAction
import app.gbsat.android.keyName
import app.gbsat.android.Storage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onRequestAllFiles: () -> Unit, toast: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

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

    fun <T> change(set: (T) -> Unit): (T) -> Unit = { v -> set(v); Emulator.savePrefs(); Emulator.applyOptions() }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("System")
            ListItem(
                headlineContent = { Text("Saturn BIOS (IPL ROM)") },
                supportingContent = { Text(if (Emulator.iplLoaded) "Loaded" else "Not loaded — required") },
                trailingContent = { FilledTonalButton(onClick = { biosPicker.launch(arrayOf("*/*")) }) { Text(if (Emulator.iplLoaded) "Replace" else "Choose") } },
            )
            ListItem(
                headlineContent = { Text("Games folder") },
                supportingContent = { Text(Emulator.gamesDir.ifBlank { "Not set" }) },
                trailingContent = { FilledTonalButton(onClick = { folderPicker.launch(null) }) { Text("Choose") } },
            )
            if (!Storage.hasAllFilesAccess()) {
                ListItem(
                    headlineContent = { Text("File access") },
                    supportingContent = { Text("Required to read disc images by path") },
                    trailingContent = { FilledTonalButton(onClick = onRequestAllFiles) { Text("Grant") } },
                )
            }
            ListItem(
                headlineContent = { Text("Emulator data location") },
                supportingContent = { Text((if (Emulator.dataDir == Emulator.defaultDataDir) "Internal app storage" else Emulator.dataDir.path) +
                    "\nBIOS, saves, save states, cheats and covers") },
                trailingContent = {
                    Row {
                        if (Emulator.dataDir != Emulator.defaultDataDir) TextButton(onClick = { scope.launch {
                            dataMsg = Emulator.changeDataDir(Emulator.defaultDataDir) ?: "Data copied to internal storage. Restart GBSat to use it."
                        } }) { Text("Internal") }
                        FilledTonalButton(onClick = { if (Storage.hasAllFilesAccess()) dataPicker.launch(null) else onRequestAllFiles() }) { Text("Change") }
                    }
                },
            )
            Toggle("Fast boot", "Skip the BIOS clock/language setup screens after the first run", Emulator.fastBoot, change<Boolean> { Emulator.fastBoot = it })
            Choice("Video standard", listOf("NTSC", "PAL"), if (Emulator.pal) 1 else 0, change<Int> { Emulator.pal = it == 1 })

            Section("Performance")
            Toggle("Threaded VDP", "Render VDP1/VDP2 on separate threads", Emulator.threadedVdp, change<Boolean> { Emulator.threadedVdp = it })
            Toggle("SH-2 cache emulation", "More accurate, slightly slower", Emulator.sh2Cache, change<Boolean> { Emulator.sh2Cache = it })
            Toggle("Frame skip", "Renders every other frame to keep full speed on slower devices", Emulator.frameSkip, change<Boolean> { Emulator.frameSkip = it })
            val cdSpeeds = listOf(2, 4, 8, 16)
            Choice("CD read speed", listOf("Original (2x)", "4x", "8x", "16x"), cdSpeeds.indexOf(Emulator.cdSpeed).coerceAtLeast(0),
                change<Int> { Emulator.cdSpeed = cdSpeeds[it] })
            Text(
                "Faster disc reads shorten loading screens. Some games may misbehave (e.g. videos or music out of sync); use Original if a game has problems.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
            )
            Toggle("Preload disc into RAM", "Faster loading, uses memory", Emulator.preloadDisc, change<Boolean> { Emulator.preloadDisc = it })

            Section("Controls")
            Choice("Controller", listOf("Control Pad", "3D Control Pad"), if (Emulator.analogPad) 1 else 0, change<Int> { Emulator.analogPad = it == 1 })
            Toggle("On-screen controls", "Show touch pad while playing", Emulator.showTouchControls, change<Boolean> { Emulator.showTouchControls = it })

            Section("Gamepad mapping")
            Text("Tap Remap, then press the controller button you want to use.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            SaturnButton.entries.forEach { b ->
                val keys = Emulator.keysFor(b).joinToString(", ") { android.view.KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") }
                ListItem(
                    headlineContent = { Text(b.name) },
                    supportingContent = { Text(keys.ifEmpty { "Unassigned" }) },
                    trailingContent = { FilledTonalButton(onClick = { Emulator.remapTarget = b }) { Text("Remap") } },
                )
            }
            TextButton(onClick = { Emulator.resetKeyMap() }) { Text("Reset mapping to defaults") }

            Section("Display")
            // Renderer modes shown (internal ids): Software 0, OpenGL ES 1, ANGLE 3, Vulkan GPU 4
            val rendererIds = listOf(0, 1, 3, 4)
            Choice("Renderer", listOf("Software", "OpenGL ES", "ANGLE", "Vulkan GPU"),
                rendererIds.indexOf(Emulator.renderer).coerceAtLeast(0), change<Int> { Emulator.renderer = rendererIds[it] })
            Text(
                "Software, OpenGL ES and ANGLE emulate the Saturn graphics on the CPU; OpenGL ES, ANGLE and Vulkan GPU add the filters and shaders below. " +
                    "Vulkan GPU renders the graphics on the GPU with Vulkan; " +
                    "it falls back to software rendering if the GPU cannot run it.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (Emulator.renderer == 1 || Emulator.renderer == 3 || Emulator.renderer == 4) {
                Choice("Texture filtering", listOf("Nearest", "Bilinear"), Emulator.filter.coerceAtMost(1), change<Int> { Emulator.filter = it })
                // Shown alphabetically; internal ids: 0 none, 1 scanlines, 2 CRT, 3 LCD3x, 4 dots
                val shaderIds = listOf(0, 2, 4, 3, 1)
                Choice("Shader", listOf("None", "CRT", "Dots", "LCD3x", "Scanlines"), shaderIds.indexOf(Emulator.shader).coerceAtLeast(0),
                    change<Int> { Emulator.shader = shaderIds[it] })
            }
            if (Emulator.renderer == 4) {
                val gpuErr = remember { runCatching { NativeBridge.nativeGetGpuError().orEmpty() }.getOrDefault("") }
                if (gpuErr.isNotEmpty()) Text("Last GPU renderer error: $gpuErr",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (Emulator.renderer == 4) {
                Choice("Internal resolution", listOf("1x (native)", "2x", "3x", "4x"), Emulator.resScale - 1,
                    change<Int> { Emulator.resScale = it + 1 })
                Text(
                    "Renders the Saturn sprites and polygons at a higher resolution for sharper 3D graphics. " +
                        "Applies to standard resolution games; high-resolution and interlaced screens stay at native resolution. " +
                        "Higher values need a faster GPU.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
                )
                val probe = remember { runCatching { NativeBridge.nativeProbeVulkan() }.getOrDefault("error|probe failed") }
                Text(
                    if (probe.startsWith("ok|")) "Vulkan device: " + probe.split("|").drop(1).joinToString(", ") else "Vulkan unavailable: " + probe.removePrefix("error|"),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Choice("Display", listOf("4:3", "Fullscreen", "16:9"), Emulator.aspect, change<Int> { Emulator.aspect = it })
            Text(
                "16:9 also turns on the built-in widescreen hacks for supported games (about 20, e.g. Panzer Dragoon, " +
                    "Sega Rally, Virtual-On, Last Bronx, Quake). Other games are stretched. Not used in RetroAchievements hardcore mode.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
            )
            Toggle("Show FPS", "Overlay with the emulation speed during games", Emulator.showFps) { Emulator.showFps = it; Emulator.savePrefs() }

            Section("RetroAchievements")
            Toggle("Enable RetroAchievements", "Earn achievements from retroachievements.org (needs an account and internet)",
                app.gbsat.android.RetroAchievements.enabled) { app.gbsat.android.RetroAchievements.changeEnabled(it) }
            if (app.gbsat.android.RetroAchievements.enabled) {
                val ra = app.gbsat.android.RetroAchievements
                if (ra.user != null) {
                    ListItem(
                        headlineContent = { Text("Logged in as ${ra.user}") },
                        supportingContent = { Text("${ra.score} points" + (ra.gameTitle?.let { " · playing $it" } ?: "")) },
                        trailingContent = { TextButton(onClick = { ra.logout() }) { Text("Log out") } },
                    )
                } else {
                    var raUser by remember { mutableStateOf("") }
                    var raPass by remember { mutableStateOf("") }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        OutlinedTextField(raUser, { raUser = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
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
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Toggle("Hardcore mode", "More points, but save state loading and cheats are disabled. Enabling it during a game resets the game.",
                    ra.hardcore) { ra.changeHardcore(it) }
            }

            Section("Hotkeys")
            Text("Bind a single button or hold several buttons together (a combination) and release. L3/R3 are supported.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            HotkeyAction.entries.forEach { a ->
                val keys = Emulator.hotkeys[a].orEmpty()
                ListItem(
                    headlineContent = { Text(a.label) },
                    supportingContent = { Text(if (keys.isEmpty()) "Unassigned" else keys.joinToString(" + ") { keyName(it) } + if (keys.size > 1) "  (combination)" else "") },
                    trailingContent = {
                        Row {
                            if (keys.isNotEmpty()) TextButton(onClick = { Emulator.bindHotkey(a, emptySet()) }) { Text("Clear") }
                            FilledTonalButton(onClick = { Emulator.hotkeyCaptureKeys = emptySet(); Emulator.hotkeyCapture = a }) { Text("Set") }
                        }
                    },
                )
            }
            Choice("Fast-forward hotkey", listOf("Hold", "Toggle"), if (Emulator.ffToggle) 1 else 0, change<Int> { Emulator.ffToggle = it == 1 })

            Spacer(Modifier.height(24.dp))
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val versionName = remember {
                runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            Text("GBSat v$versionName", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("GBSat is based on Ymir, GPLv3 software by StrikerX3 and contributors. This Android port is a derivative work and is released under the same license. " +
                "Cheat database: libretro-database (CC BY-SA 4.0).",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(32.dp))
        }
    }
    // Capture prompts are drawn in this window (not a Dialog) so physical button events reach MainActivity.
    val remap = Emulator.remapTarget
    val hk = Emulator.hotkeyCapture
    if (remap != null || hk != null) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f)), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 420.dp).padding(16.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (remap != null) {
                        Text("Remap ${remap.name}", style = MaterialTheme.typography.titleLarge)
                        Text("Press a button on your controller…")
                        TextButton(onClick = { Emulator.remapTarget = null }) { Text("Cancel") }
                    } else if (hk != null) {
                        Text("Set ${hk.label}", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (Emulator.hotkeyCaptureKeys.isEmpty()) "Press a button, or hold several buttons together, then release."
                            else Emulator.hotkeyCaptureKeys.joinToString(" + ") { keyName(it) },
                        )
                        TextButton(onClick = { Emulator.hotkeyCapture = null }) { Text("Cancel") }
                    }
                }
            }
        }
    }
    }
}

@Composable private fun Section(t: String) =
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) = ListItem(
    headlineContent = { Text(title) }, supportingContent = { Text(sub) },
    trailingContent = { Switch(checked = value, onCheckedChange = onChange) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(selected = i == selected, onClick = { onSelect(i) }, shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label) }
            }
        }
    }
}
