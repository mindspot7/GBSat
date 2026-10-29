package app.gbsat.emulator

/**
 * Built-in widescreen (16:9 anamorphic) hacks, applied automatically while the 16:9 display mode is selected.
 *
 * The codes are Action Replay codes from the "Saturn Widescreen Codes & Patches" collection on SegaXtreme
 * (https://segaxtreme.net/threads/saturn-widescreen-codes-patches.34544/). Master codes (F6/B6) are not needed.
 * Games are identified by the disc header title (or the file name) and region, since the codes only work on the
 * exact release they were made for.
 */
object WidescreenHacks {
    enum class Region { J, U, E }

    private class Hack(
        val name: String,
        /** Any of these alternatives must match; each alternative is a set of words that must all be present. */
        val match: List<List<String>>,
        val exclude: List<String> = emptyList(),
        val codes: Map<Region, String>,
    )

    private val hacks = listOf(
        Hack("Cyber Troopers Virtual-On", listOf(listOf("VIRTUAL", "ON")), listOf("NETLINK", "SEGANET", "ORATORIO"), mapOf(
            Region.J to "16018630 0147\n160D58E0 0147\n160D5B08 0147",
            Region.U to "1601863C 0147\n160D58E0 0147\n160D5B08 0147",
            Region.E to "1601868C 0147\n160D58E0 0147\n160D5B08 0147",
        )),
        Hack("D-Xhird", listOf(listOf("XHIRD")), codes = mapOf(
            Region.J to "D60FFD00 1B1A\n36028A2D 0001\n36028A2E 0032\n360FFC81 0008",
        )),
        Hack("Duke Nukem 3D", listOf(listOf("DUKE", "NUKEM")), codes = mapOf(
            Region.E to "D6009C38 FFFF\n3600A041 0000\n3600A042 00C0\n36072B79 0000\n36072B7A 00C0\n360BA852 0056",
        )),
        Hack("Dungeon Master Nexus", listOf(listOf("DUNGEON", "MASTER", "NEXUS")), codes = mapOf(
            Region.J to "160970EE 00C0",
        )),
        Hack("Elan Doree", listOf(listOf("ELAN", "DOREE")), codes = mapOf(
            Region.J to "06004F84 2762\n06004F86 D57B\n06004F8C 1458\n06005176 C000",
        )),
        Hack("Exhumed / Powerslave / Seireki 1999", listOf(listOf("EXHUMED"), listOf("POWERSLAVE"), listOf("SEIREKI", "1999")), codes = mapOf(
            Region.J to "D6086908 0001\n36086909 0000\n3608690A 00C0",
            Region.U to "D6085E40 0001\n36085E41 0000\n36085E42 00C0",
            Region.E to "D608503C 0001\n3608503D 0000\n3608503E 00C0",
        )),
        Hack("Formula Karts: Special Edition", listOf(listOf("FORMULA", "KARTS")), codes = mapOf(
            Region.E to "160206C6 01AA",
        )),
        Hack("Jung Rhythm", listOf(listOf("JUNG", "RHYTHM")), codes = mapOf(
            Region.J to "16013E78 0000\n16013E7A BF00",
        )),
        Hack("Last Bronx", listOf(listOf("LAST", "BRONX")), codes = mapOf(
            Region.J to "360155C2 007A\n3601B925 0000\n3601B926 0002",
            Region.U to "360158EE 007A\n3601C511 0000\n3601C512 0002",
            Region.E to "36015902 007A\n3601C58D 0000\n3601C58E 0002",
        )),
        Hack("Pandemonium! / Magical Hoppers", listOf(listOf("PANDEMONIUM"), listOf("MAGICAL", "HOPPERS")), listOf("2"), mapOf(
            Region.J to "1604B2AC 0C00",
            Region.U to "1604B0BC 0C00",
            Region.E to "1604B28C 0C00",
        )),
        Hack("Panzer Dragoon", listOf(listOf("PANZER", "DRAGOON")), listOf("ZWEI", "SAGA", "AZEL", "II"), mapOf(
            Region.J to "160266B4 0083",
            Region.U to "16026974 0083",
            Region.E to "16004E62 76DE\n16004ED4 006E\n160267C6 0083",
        )),
        Hack("Quake", listOf(listOf("QUAKE")), codes = mapOf(
            Region.U to "36006D65 0000\n36006D66 00C0",
            Region.E to "D6006D5C 0001\n36006D5D 0000\n36006D5E 00C0",
        )),
        Hack("Sega Rally Championship", listOf(listOf("SEGA", "RALLY")), listOf("PLUS", "NETLINK"), mapOf(
            Region.J to "360102C9 0000\n360102CA 00CC",
            Region.U to "3601013D 0000\n3601013E 00CC",
            Region.E to "36010359 0000\n3601035A 00CC",
        )),
        Hack("Sega Touring Car Championship", listOf(listOf("TOURING", "CAR")), codes = mapOf(
            Region.J to "16026720 006C",
            Region.U to "16026720 006C",
            Region.E to "16026420 006C",
        )),
        Hack("Virtua Fighter 2", listOf(listOf("VIRTUA", "FIGHTER", "2")), listOf("KIDS", "REMIX", "PORTRAIT", "CG"), mapOf(
            Region.U to "160288E2 2CA0",
        )),
        Hack("Virtua Fighter Kids", listOf(listOf("VIRTUA", "FIGHTER", "KIDS")), codes = mapOf(
            Region.J to "3600F1FE 00E0\n3600F24D 0002\n3600F24E 0000\n3600F256 0082\n3600F26D 0078",
            Region.U to "36074366 00E0\n360743B5 0002\n360743B6 0000\n360743BE 0082\n360743D5 0078",
            Region.E to "36074626 00E0\n36074675 0002\n36074676 0000\n3607467E 0082\n36074695 0078",
        )),
        Hack("Wipeout XL / 2097", listOf(listOf("WIPEOUT", "XL"), listOf("WIPEOUT", "2097")), codes = mapOf(
            Region.J to "3607B59D 0000\n3607B59E 00C0",
            Region.E to "3607B7A5 0000\n3607B7A6 00C0",
        )),
        Hack("Zero Divide: The Final Conflict", listOf(listOf("ZERO", "DIVIDE", "FINAL"), listOf("ZERO", "DIVIDE", "CONFLICT")), codes = mapOf(
            Region.J to "3602E17E 0021\n360381BE 0021",
        )),
    )

    private fun words(s: String): Set<String> =
        s.uppercase().replace(Regex("[^A-Z0-9]+"), " ").trim().split(" ").filter { it.isNotEmpty() }.toSet()

    private fun matches(h: Hack, w: Set<String>): Boolean =
        h.match.any { alt -> w.containsAll(alt) } && h.exclude.none { it in w }

    /** Region from the header's area symbols, or from the file name tags when the disc lists several areas. */
    private fun region(header: String, fileName: String): Region? {
        val area = if (header.length >= 0x4A) header.substring(0x40, 0x4A) else ""
        val set = buildSet {
            if ('J' in area) add(Region.J)
            if ('U' in area) add(Region.U)
            if ('E' in area) add(Region.E)
        }
        if (set.size == 1) return set.first()
        val f = fileName.uppercase()
        return when {
            "(USA" in f || "(US)" in f || "(U)" in f -> Region.U
            "(EUROPE" in f || "(E)" in f || "(PAL" in f -> Region.E
            "(JAPAN" in f || "(J)" in f -> Region.J
            else -> null
        }
    }

    /** Finds the widescreen hack for the loaded disc. Returns the game name and its codes, or null. */
    fun find(header: String?, filePath: String?): Pair<String, String>? {
        val hdr = header.orEmpty()
        val fileName = filePath.orEmpty().substringAfterLast('/').substringBeforeLast('.')
        val title = if (hdr.length >= 0x100) hdr.substring(0x60, 0x100) else ""
        val titleWords = words(title)
        val fileWords = words(fileName.replace(Regex("\\(.*?\\)|\\[.*?]"), " "))
        val hack = hacks.firstOrNull { matches(it, titleWords) } ?: hacks.firstOrNull { matches(it, fileWords) } ?: return null
        val r = region(hdr, fileName) ?: return null
        val codes = hack.codes[r] ?: return null
        return hack.name to codes
    }
}
