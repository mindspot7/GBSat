package app.ymir.android

/** A single user cheat: one or more memory writes, stored per game. */
data class Cheat(val name: String, val code: String, val enabled: Boolean = false)

data class MemWrite(val addr: Long, val value: Long, val size: Int)

object CheatParser {
    /**
     * Accepts Action Replay style lines: `16AAAAAA VVVV` (16-bit write to 06AAAAAA) and `36AAAAAA VV` (8-bit write),
     * or raw `ADDRESS VALUE` where the value length selects 1/2/4 bytes (e.g. `06012345 00FF`).
     * Multiple lines may be separated by newlines or '+'. Returns null if any line cannot be parsed.
     */
    fun parse(code: String): List<MemWrite>? {
        val out = mutableListOf<MemWrite>()
        for (raw in code.split('\n', '+', ';')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("[\\s:]+")).filter { it.isNotEmpty() }
            if (parts.size != 2) return null
            val addr = parts[0].toLongOrNull(16) ?: return null
            val value = parts[1].toLongOrNull(16) ?: return null
            if (parts[0].length != 8) return null
            when (addr ushr 24) {
                0x16L -> out += MemWrite(0x06000000L or (addr and 0xFFFFFFL), value and 0xFFFF, 2)
                0x36L -> out += MemWrite(0x06000000L or (addr and 0xFFFFFFL), value and 0xFF, 1)
                else -> {
                    val size = when (parts[1].length) { in 1..2 -> 1; in 3..4 -> 2; else -> 4 }
                    out += MemWrite(addr, value, size)
                }
            }
        }
        return out.ifEmpty { null }
    }

    /** Parses a libretro `.cht` file (cheatN_desc / cheatN_code). */
    fun parseCht(text: String): List<Cheat> {
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
            if (parse(c) == null) null else Cheat(descs[i] ?: "Cheat $i", c)
        }
    }
}
