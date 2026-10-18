package app.gbsat.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.gbsat.android.Cheat
import app.gbsat.android.CheatParser
import app.gbsat.android.Emulator

/** Per-game cheat list: toggle, add (name + code), delete, and import libretro `.cht` files. */
@Composable
fun CheatsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val dbEntry = remember { Emulator.databaseEntry() }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull().orEmpty()
            val parsed = CheatParser.parseCht(text)
            if (parsed.isEmpty()) error = "No supported cheats found in that file"
            else { Emulator.updateCheats(Emulator.cheats + parsed); error = null }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .75f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.widthIn(max = 520.dp).heightIn(max = 560.dp).padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                    Text("Cheats", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (dbEntry != null) IconButton(onClick = {
                        val n = Emulator.addDatabaseCheats()
                        error = if (n == 0) "All database cheats are already in the list" else null
                    }) { Icon(Icons.Default.LibraryAdd, "Add cheats from the database") }
                    IconButton(onClick = { importer.launch(arrayOf("*/*")) }) { Icon(Icons.Default.FileOpen, "Import .cht") }
                    IconButton(onClick = { adding = !adding }) { Icon(Icons.Default.Add, "Add") }
                }
                if (adding) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        code, { code = it }, label = { Text("Code (e.g. 16012345 00FF, one per line)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = {
                            if (!CheatParser.usable(code)) error = "Invalid code"
                            else {
                                Emulator.updateCheats(Emulator.cheats + Cheat(name.ifBlank { "Cheat" }, code.trim(), true))
                                name = ""; code = ""; adding = false; error = null
                            }
                        }) { Text("Add") }
                    }
                }
                Text(
                    if (dbEntry != null) "Cheat database: $dbEntry" else "No entry in the cheat database for this game",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp)) }
                if (Emulator.cheats.isEmpty() && !adding) {
                    Text(
                        "No cheats for this game yet. Add a code (Action Replay 16/36 format or ADDRESS VALUE) or import a .cht file.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn {
                    itemsIndexed(Emulator.cheats) { i, c ->
                        ListItem(
                            headlineContent = { Text(c.name) },
                            supportingContent = {
                                Text((if (c.fromDatabase) "Database · " else "") + c.code.lines().first().take(40))
                            },
                            leadingContent = {
                                Switch(checked = c.enabled, onCheckedChange = { on ->
                                    Emulator.updateCheats(Emulator.cheats.toMutableList().also { it[i] = c.copy(enabled = on) })
                                })
                            },
                            trailingContent = {
                                IconButton(onClick = { Emulator.updateCheats(Emulator.cheats.toMutableList().also { it.removeAt(i) }) }) {
                                    Icon(Icons.Default.Delete, "Delete")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
