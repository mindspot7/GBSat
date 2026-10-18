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
            Toggle("Fast boot", "Skip the BIOS clock/language setup screens after the first run", Emulator.fastBoot, change<Boolean> { Emulator.fastBoot = it })
            Choice("Video standard", listOf("NTSC", "PAL"), if (Emulator.pal) 1 else 0, change<Int> { Emulator.pal = it == 1 })
            Choice("Cartridge", listOf("None", "1 MiB RAM", "4 MiB RAM"), Emulator.cartridge, change<Int> { Emulator.cartridge = it })

            Section("Performance")
            Toggle("Threaded VDP", "Render VDP1/VDP2 on separate threads", Emulator.threadedVdp, change<Boolean> { Emulator.threadedVdp = it })
            Toggle("SH-2 cache emulation", "More accurate, slightly slower", Emulator.sh2Cache, change<Boolean> { Emulator.sh2Cache = it })
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
            Choice("Renderer", listOf("Software", "OpenGL ES", "ANGLE", "Vulkan GPU (beta)"),
                rendererIds.indexOf(Emulator.renderer).coerceAtLeast(0), change<Int> { Emulator.renderer = rendererIds[it] })
            Text(
                "Software, OpenGL ES and ANGLE emulate the Saturn graphics on the CPU; OpenGL ES and ANGLE add the filters and shaders below. " +
                    "Vulkan GPU (beta) renders the graphics on the GPU with Vulkan and shows them through Vulkan; " +
                    "it falls back to software rendering if the GPU cannot run it.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (Emulator.renderer == 1 || Emulator.renderer == 3) {
                Choice("Texture filtering", listOf("Nearest", "Bilinear"), Emulator.filter.coerceAtMost(1), change<Int> { Emulator.filter = it })
                Choice("Shader", listOf("None", "Scanlines", "CRT", "LCD3x", "Dots"), Emulator.shader, change<Int> { Emulator.shader = it })
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
                Choice("Texture filtering", listOf("Nearest", "Bilinear"), minOf(Emulator.filter, 1), change<Int> { Emulator.filter = it })
                val probe = remember { runCatching { NativeBridge.nativeProbeVulkan() }.getOrDefault("error|probe failed") }
                Text(
                    if (probe.startsWith("ok|")) "Vulkan device: " + probe.split("|").drop(1).joinToString(", ") else "Vulkan unavailable: " + probe.removePrefix("error|"),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Choice("Display", listOf("4:3", "Fullscreen", "16:9"), Emulator.aspect, change<Int> { Emulator.aspect = it })
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
