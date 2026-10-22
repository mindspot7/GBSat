package app.gbsat.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.gbsat.android.Emulator
import app.gbsat.android.Storage
import gb.ui.PresetOption
import gb.ui.PresetPicker
import gb.ui.WizardStatus
import gb.ui.WizardStep
import kotlinx.coroutines.launch

/** First-run setup (shared wizard from the UI kit): welcome, BIOS, games folder, preset. */
@Composable
fun SetupWizard(onRequestAllFiles: () -> Unit, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { message = Emulator.importBios(ctx, it) ?: "BIOS loaded" } }
    }
    val gamesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { Storage.takeFolder(it); Emulator.gamesDir = it.toString(); Emulator.savePrefs(); message = null }
    }
    val presets = listOf(Emulator.Preset.High, Emulator.Preset.Mid, Emulator.Preset.Low)
    val descriptions = mapOf(
        Emulator.Preset.High to "Vulkan GPU at the screen's native resolution, Scanlines shader. For powerful devices.",
        Emulator.Preset.Mid to "CPU renderer (OpenGL) with Scanlines shader. For mid-range devices.",
        Emulator.Preset.Low to "CPU renderer (OpenGL), no shader, frame skip. For modest devices.",
    )
    gb.ui.SetupWizard(
        steps = listOf(
            WizardStep(Icons.Default.SportsEsports, "Welcome to GBSat", listOf(Color(0xFF8B7CFF), Color(0xFFE85CC4)),
                "A Saturn emulator for Android. A few steps and you're ready to play."),
            WizardStep(Icons.Default.Memory, "Saturn BIOS", listOf(Color(0xFFFFB347), Color(0xFFFF6B6B)),
                "GBSat needs your own Saturn BIOS (IPL ROM, 512 KiB). None is included.") {
                Button(onClick = { biosPicker.launch(arrayOf("*/*")) }) { Text(if (Emulator.iplLoaded) "Replace BIOS" else "Choose BIOS file") }
                if (Emulator.iplLoaded) WizardStatus("BIOS loaded")
            },
            WizardStep(Icons.Default.VideoLibrary, "Games folder", listOf(Color(0xFF66E3A4), Color(0xFF1FA2A8)),
                "Pick the folder that holds your Saturn disc images (CUE/BIN, CHD, ISO, MDS, CCD). GBSat only reads that folder.") {
                Button(onClick = { gamesPicker.launch(null) }) { Text("Choose folder") }
                if (Emulator.gamesDir.isNotBlank()) WizardStatus(Storage.folderLabel(Emulator.gamesDir))
            },
            WizardStep(Icons.Default.AutoAwesome, "Preset", listOf(Color(0xFFFFD25A), Color(0xFFE8A13A)),
                "Choose how GBSat balances image quality and speed. You can change it later in Settings or in the in-game menu.") {
                PresetPicker(presets.map { PresetOption(it.label, descriptions.getValue(it)) }, presets.indexOf(Emulator.currentPreset)) {
                    Emulator.applyPreset(presets[it])
                }
            },
        ),
        onFinish = onFinish, message = message, onStepChange = { message = null },
    )
}
