package app.ymir.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ymir.android.Emulator
import app.ymir.android.SaturnButton
import app.ymir.android.HotkeyAction
import app.ymir.android.keyName
import app.ymir.android.Storage
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

    Emulator.remapTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { Emulator.remapTarget = null },
            title = { Text("Remap ${target.name}") },
            text = { Text("Press a button on your controller…") },
            confirmButton = { TextButton(onClick = { Emulator.remapTarget = null }) { Text("Cancel") } },
        )
    }

    Emulator.hotkeyCapture?.let { action ->
        AlertDialog(
            onDismissRequest = { Emulator.hotkeyCapture = null },
            title = { Text("Set ${action.label}") },
            text = {
                Text(
                    if (Emulator.hotkeyCaptureKeys.isEmpty()) "Press a button, or hold several buttons together, then release."
                    else Emulator.hotkeyCaptureKeys.joinToString(" + ") { keyName(it) },
                )
            },
            confirmButton = { TextButton(onClick = { Emulator.hotkeyCapture = null }) { Text("Cancel") } },
        )
    }

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
            Choice("Renderer", listOf("Software", "OpenGL ES"), Emulator.renderer, change<Int> { Emulator.renderer = it })
            Text(
                "The Saturn VDP is always emulated in software (Ymir has no GPU renderer yet). OpenGL ES presents the image on the GPU " +
                    "and enables the filters and shaders below. Vulkan, enhanced resolution and widescreen hacks are not available.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (Emulator.renderer == 1) {
                Choice("Texture filtering", listOf("Nearest", "Bilinear", "Sharp bilinear"), Emulator.filter, change<Int> { Emulator.filter = it })
                Choice("Shader", listOf("None", "Scanlines", "CRT", "LCD3x", "Dots"), Emulator.shader, change<Int> { Emulator.shader = it })
            }
            Choice("Aspect ratio", listOf("4:3", "Stretch", "Pixel-perfect"), Emulator.aspect, change<Int> { Emulator.aspect = it })

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
            Text("GBSat is based on Ymir, GPLv3 software by StrikerX3 and contributors. This Android port is a derivative work and is released under the same license.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(32.dp))
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
