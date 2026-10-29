package app.gbsat.emulator.ui

import app.gbsat.emulator.Cheat
import app.gbsat.emulator.CheatParser
import app.gbsat.emulator.Emulator
import gb.frame.CheatActionResult
import gb.frame.CheatUiItem
import gb.frame.CheatsBackend
import gb.frame.RetroAchievements

/**
 * GBSat adapter for the shared GBFrame cheats screen. Saturn-specific Action Replay parsing,
 * the per-game database, persistence and native application remain in the emulator app.
 */
object GBSatCheatsBackend : CheatsBackend {
    override val cheats: List<CheatUiItem>
        get() = Emulator.cheats.mapIndexed { index, cheat ->
            CheatUiItem(
                id = index.toString(),
                name = cheat.name,
                code = cheat.code,
                enabled = cheat.enabled,
                sourceLabel = if (cheat.fromDatabase) "Database" else null,
            )
        }

    override val databaseStatus: String
        get() = Emulator.databaseEntry()?.let { "Cheat database: $it" }
            ?: "No entry in the cheat database for this game"

    override val canAddFromDatabase: Boolean
        get() = Emulator.databaseEntry() != null

    override val blockedMessage: String?
        get() = if (RetroAchievements.hardcoreActive) {
            "Cheats are disabled while RetroAchievements hardcore mode is on."
        } else null

    override val codeHint: String = "Code (e.g. 16012345 00FF, one per line)"

    override fun add(name: String, code: String): CheatActionResult {
        blockedMessage?.let { return CheatActionResult.failure(it) }
        if (!CheatParser.usable(code)) return CheatActionResult.failure("Invalid code")
        Emulator.updateCheats(Emulator.cheats + Cheat(name.ifBlank { "Cheat" }, code.trim(), true))
        return CheatActionResult.success("Cheat added.")
    }

    override fun setEnabled(id: String, enabled: Boolean) {
        if (blockedMessage != null) return
        val index = id.toIntOrNull() ?: return
        val current = Emulator.cheats.getOrNull(index) ?: return
        Emulator.updateCheats(Emulator.cheats.toMutableList().also { it[index] = current.copy(enabled = enabled) })
    }

    override fun delete(id: String) {
        if (blockedMessage != null) return
        val index = id.toIntOrNull() ?: return
        if (Emulator.cheats.getOrNull(index) == null) return
        Emulator.updateCheats(Emulator.cheats.toMutableList().also { it.removeAt(index) })
    }

    override fun importFile(contents: String): CheatActionResult {
        blockedMessage?.let { return CheatActionResult.failure(it) }
        val imported = CheatParser.parseCht(contents)
        if (imported.isEmpty()) return CheatActionResult.failure("No supported cheats found in that file")
        Emulator.updateCheats(Emulator.cheats + imported)
        return CheatActionResult.success("Imported ${imported.size} cheat(s).")
    }

    override fun addFromDatabase(): CheatActionResult {
        blockedMessage?.let { return CheatActionResult.failure(it) }
        if (!canAddFromDatabase) {
            return CheatActionResult.failure("No cheat database entry was found for this game.")
        }
        val added = Emulator.addDatabaseCheats()
        return if (added == 0) {
            CheatActionResult.success("All database cheats are already in the list.")
        } else {
            CheatActionResult.success("Added $added database cheat(s).")
        }
    }
}
