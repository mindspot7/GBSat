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
import gb.frame.WizardStatus
import gb.frame.WizardStep
import kotlinx.coroutines.launch

/** First-run setup (shared wizard from the UI kit): welcome, data folder, BIOS, games folder, performance preset. */
@Composable
fun SetupWizard(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { message = Emulator.importBios(ctx, it) ?: "BIOS loaded" } }
    }
    val gamesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { Storage.takeFolder(it); Emulator.gamesDir = it.toString(); Emulator.savePrefs(); message = null }
    }
    // The video/performance preset is picked for the detected device (Settings → Performance shows which)
    LaunchedEffect(Unit) { if (!Emulator.setupDone) Emulator.applyPreset(Emulator.recommendedPreset) }
    gb.frame.SetupWizard(
        steps = listOf(
            WizardStep(Icons.Default.SportsEsports, "Welcome to GBSat", listOf(Color(0xFF8B7CFF), Color(0xFFE85CC4)),
                "A Saturn emulator for Android. A few steps and you're ready to play."),
            gb.frame.dataFolderWizardStep(Emulator.dataFolder),
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
            gb.frame.performanceWizardStep("GBSat", Emulator.recommendedPreset.label, Emulator.device.summary,
                when (Emulator.recommendedPreset) {
                    Emulator.Preset.High -> "GPU (Vulkan) rendering at the screen's resolution, scanlines"
                    Emulator.Preset.Mid -> "CPU rendering, scanlines"
                    Emulator.Preset.Low -> "CPU rendering, no shader, automatic frame skip"
                }),
        ),
        onFinish = onFinish, message = message, onStepChange = { message = null },
    )
}
