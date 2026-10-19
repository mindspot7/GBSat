package app.gbsat.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.gbsat.android.Emulator
import app.gbsat.android.Storage
import kotlinx.coroutines.launch

/** First-run setup: BIOS, file access, games folder and data location. */
@Composable
fun SetupWizard(onRequestAllFiles: () -> Unit, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var hasAccess by remember { mutableStateOf(Storage.hasAllFilesAccess()) }
    LifecycleResumeEffect(Unit) { hasAccess = Storage.hasAllFilesAccess(); onPauseOrDispose { } }

    val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { scope.launch { message = Emulator.importBios(ctx, it) ?: "BIOS loaded" } }
    }
    val gamesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val path = Storage.treeUriToPath(it)
            if (path == null) message = "Unsupported folder" else { Emulator.gamesDir = path; Emulator.savePrefs(); message = null }
        }
    }
    val dataPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            val path = Storage.treeUriToPath(it)
            if (path == null) message = "Unsupported folder" else scope.launch {
                message = Emulator.changeDataDir(java.io.File(path, "GBSat"))
                    ?: "Data will be stored in $path/GBSat after GBSat restarts."
            }
        }
    }

    val steps = 6
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color(0xFF1D1636), Color(0xFF2B2152), Color(0xFF161126)))).safeDrawingPadding().padding(24.dp)) {
        LinearProgressIndicator(progress = { (step + 1f) / steps }, modifier = Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            when (step) {
                0 -> Page(Icons.Default.SportsEsports, "Welcome to GBSat", listOf(Color(0xFF8B7CFF), Color(0xFFE85CC4)),
                    "A Sega Saturn emulator for Android. A few steps and you're ready to play.")
                1 -> {
                    Page(Icons.Default.Memory, "Saturn BIOS", listOf(Color(0xFFFFB347), Color(0xFFFF6B6B)),
                        "GBSat needs your own Saturn BIOS (IPL ROM, 512 KiB). None is included.")
                    Button(onClick = { biosPicker.launch(arrayOf("*/*")) }) { Text(if (Emulator.iplLoaded) "Replace BIOS" else "Choose BIOS file") }
                    if (Emulator.iplLoaded) Status("BIOS loaded")
                }
                2 -> {
                    Page(Icons.Default.Lock, "File access", listOf(Color(0xFF4FC3F7), Color(0xFF5C6BF0)),
                        "GBSat reads disc images (CUE/BIN, CHD, ISO, MDS, CCD) directly from your storage.")
                    if (hasAccess) Status("Access granted") else Button(onClick = onRequestAllFiles) { Text("Grant access") }
                }
                3 -> {
                    Page(Icons.Default.VideoLibrary, "Games folder", listOf(Color(0xFF66E3A4), Color(0xFF1FA2A8)), "Pick the folder that holds your Saturn games.")
                    Button(onClick = { if (hasAccess) gamesPicker.launch(null) else onRequestAllFiles() }) { Text("Choose folder") }
                    if (Emulator.gamesDir.isNotBlank()) Status(Emulator.gamesDir)
                }
                4 -> {
                    Page(Icons.Default.Cloud, "Emulator data", listOf(Color(0xFF7FD7FF), Color(0xFF7A5CFF)),
                        "BIOS, saves, save states, cheats and covers are kept in the app's internal storage. " +
                            "You can store them in a folder of your choice instead (easier to back up).")
                    OutlinedButton(onClick = { if (hasAccess) dataPicker.launch(null) else onRequestAllFiles() }) { Text("Choose folder (optional)") }
                    Status(if (Emulator.dataDir == Emulator.defaultDataDir) "Internal app storage" else Emulator.dataDir.path)
                }
                5 -> {
                    Page(Icons.Default.AutoAwesome, "Preset", listOf(Color(0xFFFFD25A), Color(0xFFE8A13A)),
                        "Choose how GBSat balances image quality and speed. You can change it later in Settings or in the in-game menu.")
                    val descriptions = mapOf(
                        Emulator.Preset.High to "Vulkan GPU at 4x resolution, Scanlines shader. For powerful devices.",
                        Emulator.Preset.Mid to "OpenGL ES renderer, no shader. For mid-range devices.",
                        Emulator.Preset.Low to "Software renderer with frame skip. For modest devices.",
                    )
                    listOf(Emulator.Preset.High, Emulator.Preset.Mid, Emulator.Preset.Low).forEach { p ->
                        val selected = Emulator.currentPreset == p
                        OutlinedCard(onClick = { Emulator.applyPreset(p) }, modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = { Emulator.applyPreset(p) })
                                Column {
                                    Text(p.label, style = MaterialTheme.typography.titleMedium)
                                    Text(descriptions.getValue(p), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) TextButton(onClick = { step--; message = null }) { Text("Back") } else TextButton(onClick = onFinish) { Text("Skip") }
            Button(onClick = { if (step < steps - 1) { step++; message = null } else onFinish() }) {
                Text(if (step < steps - 1) "Next" else "Start")
            }
        }
    }
}

@Composable
private fun Page(icon: ImageVector, title: String, colors: List<Color>, body: String) {
    // Rounded gradient badge with a soft glow
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(132.dp).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(colors[0].copy(alpha = .35f), Color.Transparent)), CircleShape))
        Box(Modifier.size(96.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(colors), RoundedCornerShape(30.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(52.dp), tint = Color.White)
        }
    }
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Color.White.copy(alpha = .75f))
}

@Composable
private fun Status(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
