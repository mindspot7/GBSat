package app.ymir.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.ymir.android.Emulator
import app.ymir.android.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenSettings: () -> Unit, onRequestAllFiles: () -> Unit, launch: (String) -> Unit) {
    var games by remember { mutableStateOf<List<Storage.Game>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val hasAccess = Storage.hasAllFilesAccess()

    LifecycleResumeEffect(Unit) { refreshKey++; onPauseOrDispose { } }
    LaunchedEffect(Emulator.gamesDir, refreshKey, hasAccess) {
        if (Emulator.gamesDir.isBlank() || !hasAccess) { games = emptyList(); return@LaunchedEffect }
        loading = true
        games = withContext(Dispatchers.IO) { Storage.scan(Emulator.gamesDir) }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("GBSat", style = MaterialTheme.typography.headlineSmall) },
            actions = {
                IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, "Rescan") }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        when {
            !Emulator.iplLoaded -> EmptyState(
                icon = Icons.Default.Memory, title = "Add a Saturn BIOS",
                body = "GBSat needs your own Saturn IPL ROM (512 KiB) to boot games.",
                action = "Open settings", onAction = onOpenSettings,
            )
            !hasAccess -> EmptyState(
                icon = Icons.Default.FolderOff, title = "Allow file access",
                body = "Disc images (CUE/BIN, CHD, ISO, MDS, CCD) are read directly from storage.",
                action = "Grant access", onAction = onRequestAllFiles,
            )
            Emulator.gamesDir.isBlank() -> EmptyState(
                icon = Icons.Default.Folder, title = "Choose your games folder",
                body = "Pick the folder that holds your Saturn disc images.",
                action = "Open settings", onAction = onOpenSettings,
            )
            else -> {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search ${games.size} games") },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                val shown = games.filter { it.title.contains(query, true) }
                if (shown.isEmpty() && !loading) {
                    EmptyState(Icons.Default.SearchOff, "No games found", "Nothing matched in ${Emulator.gamesDir}", null, {})
                } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.path }) { g -> GameRow(g) { launch(g.path) } }
                }
            }
        }
    }
}

@Composable
private fun GameRow(g: Storage.Game, onClick: () -> Unit) {
    val ext = File(g.path).extension.uppercase()
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(ext, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f))
        if (action != null) { Spacer(Modifier.height(24.dp)); Button(onClick = onAction) { Text(action) } }
    }
}
