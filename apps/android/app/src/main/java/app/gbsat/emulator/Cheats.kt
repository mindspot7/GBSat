package app.gbsat.emulator

import android.content.Context

/** A single cheat: one or more memory writes, stored per game. */
data class Cheat(val name: String, val code: String, val enabled: Boolean = false, val fromDatabase: Boolean = false)

/**
 * One cheat operation. [size] is 1, 2 or 4 for a constant write of that many bytes, or [SIZE_IF16] for a 16-bit
 * comparison: the next operation only runs if the 16-bit value at [addr] equals [value].
 */
data class MemWrite(val addr: Long, val value: Long, val size: Int) {
    companion object { const val SIZE_IF16 = -2 }
}

object CheatParser {
    /**
     * Parses Action Replay style codes, as used by the libretro cheat database and most Saturn cheat lists:
     * `1AAAAAAA VVVV` 16-bit write, `3AAAAAAA 00VV` 8-bit write, `DAAAAAAA VVVV` "if equal" condition for the next
     * code; master codes (`F6...`/`B6...`) are ignored. Raw `0AAAAAAA VALUE` writes pick their size from the value
     * length. Pairs may be separated by spaces, '+', ';' or new lines, or written together as 12 hex digits.
     * Returns null if the code cannot be used (unknown code type or `??` placeholders), or an empty list if it only
     * contains master codes.
     */
    fun parse(code: String): List<MemWrite>? {
        val raw = code.split(Regex("[\\s+:;,]+")).filter { it.isNotEmpty() }
        val tokens = raw.flatMap { if (it.length == 12) listOf(it.substring(0, 8), it.substring(8)) else listOf(it) }
        if (tokens.isEmpty() || tokens.size % 2 != 0) return null
        val out = mutableListOf<MemWrite>()
        for (i in tokens.indices step 2) {
            val a = tokens[i]
            val v = tokens[i + 1]
            if (a.length != 8) return null
            val addr = a.toLongOrNull(16) ?: return null
            val value = v.toLongOrNull(16) ?: return null
            val top = (addr ushr 24).toInt()
            if (top == 0xF6 || top == 0xB6) continue // master/enable codes: not needed by an emulator
            val target = addr and 0x0FFFFFFFL
            when ((addr ushr 28).toInt()) {
                0x1 -> out += MemWrite(target, value and 0xFFFF, 2)
                0x3 -> out += MemWrite(target, value and 0xFF, 1)
                0xD -> out += MemWrite(target, value and 0xFFFF, MemWrite.SIZE_IF16)
                0x0 -> out += MemWrite(addr, value, when (v.length) { in 1..2 -> 1; in 3..4 -> 2; else -> 4 })
                else -> return null
            }
        }
        return out
    }

    /** True if the code parses and actually writes memory. */
    fun usable(code: String): Boolean = parse(code)?.any { it.size > 0 } == true

    /** Parses a libretro `.cht` file (cheatN_desc / cheatN_code), keeping only usable cheats. */
    fun parseCht(text: String, fromDatabase: Boolean = false): List<Cheat> {
        val descs = HashMap<Int, String>()
        val codes = HashMap<Int, String>()
        val re = Regex("""cheat(\d+)_(desc|code)\s*=\s*"(.*)"""")
        text.lineSequence().forEach { l ->
            re.find(l.trim())?.let { m ->
                val i = m.groupValues[1].toInt()
                if (m.groupValues[2] == "desc") descs[i] = m.groupValues[3] else codes[i] = m.groupValues[3]
            }
        }
        return codes.keys.sorted().mapNotNull { i ->
            val c = codes[i] ?: return@mapNotNull null
            if (!usable(c)) null else Cheat(descs[i]?.trim()?.ifEmpty { null } ?: "Cheat $i", c, false, fromDatabase)
        }
    }
}

/** Built-in cheat database (libretro-database, Sega - Saturn), bundled in the APK assets. */
object CheatDatabase {
    private var names: List<String>? = null

    private fun norm(t: String) = t.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ").lowercase()
        .replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun regionTags(t: String) = Regex("\\(([^)]*)\\)").findAll(t).map { it.groupValues[1].lowercase() }.toSet()

    private fun index(context: Context): List<String> = names ?: (context.assets.list("cheats")?.filter { it.endsWith(".cht") }
        ?.map { it.removeSuffix(".cht") }.orEmpty()).also { names = it }

    /** Finds the database entry for a game file name (e.g. "Three Dirty Dwarves (USA).cue"). */
    fun find(context: Context, gameFileName: String): String? {
        val title = gameFileName.substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("\\s*\\((Disc|Disk|Track)\\s*\\d+[^)]*\\)", RegexOption.IGNORE_CASE), "").trim()
        val all = index(context)
        all.firstOrNull { it.equals(title, ignoreCase = true) }?.let { return it }
        val key = norm(title)
        if (key.isEmpty()) return null
        val candidates = all.filter { norm(it) == key }
        if (candidates.isNotEmpty()) {
            val tags = regionTags(title)
            return candidates.maxByOrNull { c -> regionTags(c).count { it in tags } }
        }
        // Looser match: database title starts with the game title or vice versa (subtitles, "The" etc.)
        return all.filter { val n = norm(it); n.startsWith(key) || key.startsWith(n) }.minByOrNull { it.length }
    }

    fun load(context: Context, gameFileName: String): List<Cheat> {
        val entry = find(context, gameFileName) ?: return emptyList()
        val text = runCatching { context.assets.open("cheats/$entry.cht").bufferedReader().readText() }.getOrNull() ?: return emptyList()
        return CheatParser.parseCht(text, fromDatabase = true)
    }
}
