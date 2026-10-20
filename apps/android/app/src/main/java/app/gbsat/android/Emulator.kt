package app.gbsat.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.KeyEvent
import java.io.File

/** Process-wide emulator controller: owns persistent settings and merges all input sources into one pad. */
object Emulator {
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    var iplLoaded by mutableStateOf(false); private set
    /** Report of the crash that ended the previous session (shown once), or null. */
    var lastCrash by mutableStateOf<String?>(null)
    var running by mutableStateOf(false); private set
    var paused by mutableStateOf(true); private set
    var fastForward by mutableStateOf(false); private set
    var currentGame by mutableStateOf<String?>(null); private set
    var trayOpen by mutableStateOf(false); private set

    // Settings (persisted)
    var gamesDir by mutableStateOf("")
    var analogPad by mutableStateOf(false)
    var sh2Cache by mutableStateOf(false)
    var threadedVdp by mutableStateOf(true)
    var threadedScsp by mutableStateOf(true) // sound (SCSP + 68000) on its own thread
    var preloadDisc by mutableStateOf(false)
    var cartridge by mutableStateOf(2) // 4 MiB RAM expansion (fixed; works with 1 MiB games too)
    var showTouchControls by mutableStateOf(true)
    var aspect by mutableStateOf(0) // 0 = 4:3, 1 = fill, 2 = 16:9
    var showFps by mutableStateOf(false)
    var showSpeed by mutableStateOf(false)
    var showFfButton by mutableStateOf(false)
    var showRenderer by mutableStateOf(false)
    var showResolution by mutableStateOf(false)
    var showCpu by mutableStateOf(false)
    var showGpu by mutableStateOf(false)
    var showFrameTime by mutableStateOf(false)
    /** All performance monitoring overlays at once. */
    var showAllStats: Boolean
        get() = showFps && showSpeed && showRenderer && showResolution && showCpu && showGpu && showFrameTime
        set(v) { showFps = v; showSpeed = v; showRenderer = v; showResolution = v; showCpu = v; showGpu = v; showFrameTime = v }
    var fastBoot by mutableStateOf(true)
    var renderer by mutableStateOf(0) // 0 = software, 1 = OpenGL ES, 4 = Vulkan GPU (2 = Vulkan present and 3 = ANGLE were removed)
    var frameSkip by mutableStateOf(false) // render every other frame
    var volume by mutableStateOf(100)      // audio volume, percent
    var autoSave by mutableStateOf(false)  // save state when leaving a game
    var autoLoad by mutableStateOf(false)  // load that state when starting the game
    var ffAudio by mutableStateOf(0)       // fast-forward audio: 0 default, 1 speed up, 2 mute

    // ---- Presets: groups of video/performance options ----
    enum class Preset(val label: String) { Low("Low"), Mid("Mid"), High("High") }
    private data class PresetValues(val renderer: Int, val resScale: Int, val shader: Int, val filter: Int,
                                    val frameSkip: Boolean, val sh2Cache: Boolean, val threadedVdp: Boolean)
    private fun presetValues(p: Preset) = when (p) {
        // Powerful devices: GPU rendering at the screen's native resolution with scanlines, accurate SH-2 cache
        Preset.High -> PresetValues(renderer = 4, resScale = 0, shader = 1, filter = 1, frameSkip = false, sh2Cache = false, threadedVdp = true)
        // Mid-range: CPU rendering through OpenGL ES, no shader
        Preset.Mid -> PresetValues(renderer = 1, resScale = 1, shader = 0, filter = 1, frameSkip = false, sh2Cache = false, threadedVdp = true)
        // Modest devices: Software renderer, frame skip
        Preset.Low -> PresetValues(renderer = 0, resScale = 1, shader = 0, filter = 1, frameSkip = true, sh2Cache = false, threadedVdp = true)
    }
    /** The preset matching the current options, or null ("Custom") when any of them was changed. */
    val currentPreset: Preset?
        get() = Preset.entries.firstOrNull { presetValues(it) == PresetValues(renderer, resScale, shader, filter, frameSkip, sh2Cache, threadedVdp) }
    fun applyPreset(p: Preset) {
        val v = presetValues(p)
        renderer = v.renderer; resScale = v.resScale; shader = v.shader; filter = v.filter
        frameSkip = v.frameSkip; sh2Cache = v.sh2Cache; threadedVdp = v.threadedVdp
        savePrefs(); applyOptions()
    }
    var setupDone by mutableStateOf(true)
    fun finishSetup() { setupDone = true; prefs.edit().putBoolean("setupDone", true).apply() }
    var cdSpeed by mutableStateOf(2) // CD read speed factor (2 = original)
    var resScale by mutableStateOf(1) // Vulkan GPU internal resolution scale (1x..4x); 0 = Native (matches the screen)
    /** Scale used for "Native": the screen's short side divided by the Saturn's ~224 lines, rounded (1x..6x). */
    val nativeScale: Int
        get() {
            val m = appContext.resources.displayMetrics
            return Math.round(minOf(m.widthPixels, m.heightPixels) / 224f).coerceIn(1, 6)
        }
    val effectiveScale get() = if (resScale == 0) nativeScale else resScale
    val resScaleLabel get() = if (resScale == 0) "Native (${nativeScale}x)" else "${resScale}x"
    var filter by mutableStateOf(1)   // 0 nearest, 1 bilinear, 2 sharp bilinear
    var shader by mutableStateOf(0)   // 0 none, 1 scanlines, 2 CRT, 3 LCD3x, 4 dot matrix
    var ffToggle by mutableStateOf(false) // fast-forward hotkey: false = hold, true = toggle

    // Hotkeys: action -> set of keycodes (1 key = button, 2+ = combination)
    var hotkeys by mutableStateOf<Map<HotkeyAction, Set<Int>>>(emptyMap()); private set
    var hotkeyCapture by mutableStateOf<HotkeyAction?>(null)
    var hotkeyCaptureKeys by mutableStateOf<Set<Int>>(emptySet())
    var menuRequest by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null); private set
    private val scope = CoroutineScope(Dispatchers.Main)
    private var toastJob: kotlinx.coroutines.Job? = null

    fun showToast(msg: String) {
        toast = msg
        toastJob?.cancel()
        toastJob = scope.launch { delay(2000); toast = null }
    }

    fun bindHotkey(a: HotkeyAction, keys: Set<Int>) {
        hotkeys = hotkeys.toMutableMap().also { if (keys.isEmpty()) it.remove(a) else it[a] = keys }
        prefs.edit().putString("hotkeys", hotkeys.entries.joinToString(";") { "${it.key.name}=${it.value.joinToString("+")}" }).apply()
    }

    fun runHotkey(a: HotkeyAction, down: Boolean) {
        if (!running) return
        when (a) {
            HotkeyAction.QuickSave -> if (down) scope.launch { showToast(saveState(QUICK_SLOT) ?: "Quick saved") }
            HotkeyAction.QuickLoad -> if (down) scope.launch { showToast(loadState(QUICK_SLOT) ?: "Quick loaded") }
            HotkeyAction.Menu -> if (down) menuRequest = true
            HotkeyAction.FastForward ->
                if (ffToggle) { if (down) toggleFastForward() }
                else { fastForward = down; NativeBridge.nativeSetFastForward(down) }
        }
    }

    // ---- Cheats (per game) ----
    var cheats by mutableStateOf<List<Cheat>>(emptyList()); private set
    private fun cheatFile() = NativeBridge.nativeGetDiscHash()?.let { File(dataDir, "cheats/$it.txt") }

    fun loadCheats() {
        val saved = cheatFile()?.takeIf { it.exists() }?.readLines()?.mapNotNull { l ->
            val p = l.split("\t")
            if (p.size >= 3) Cheat(p[1], p[2].replace("\\n", "\n"), p[0] == "1", p.getOrNull(3) == "db") else null
        }
        // First time with this game: start from the built-in database (all disabled)
        cheats = saved ?: currentGame?.let { CheatDatabase.load(appContext, it) }.orEmpty()
        applyCheats()
    }

    /** Adds database cheats for the current game that are not in the list yet. Returns how many were added. */
    fun addDatabaseCheats(): Int {
        val game = currentGame ?: return 0
        val known = cheats.map { it.code.trim() }.toSet()
        val extra = CheatDatabase.load(appContext, game).filter { it.code.trim() !in known }
        if (extra.isNotEmpty()) updateCheats(cheats + extra)
        return extra.size
    }

    fun databaseEntry(): String? = currentGame?.let { CheatDatabase.find(appContext, it) }
    fun updateCheats(list: List<Cheat>) {
        cheats = list
        cheatFile()?.let { f ->
            f.parentFile?.mkdirs()
            f.writeText(list.joinToString("\n") {
                "${if (it.enabled) 1 else 0}\t${it.name.replace('\t', ' ')}\t${it.code.replace("\n", "\\n")}\t${if (it.fromDatabase) "db" else "user"}"
            })
        }
        applyCheats()
    }
    /** Name of the built-in widescreen hack active for the current game (null if none). */
    var widescreenHack by mutableStateOf<String?>(null); private set

    /** Re-applies cheats and the widescreen hack (call after changing the display mode). */
    fun refreshCheats() = applyCheats()

    private fun applyCheats() {
        val hardcore = RetroAchievements.hardcoreActive
        val ws = if (aspect == 2 && running && !hardcore) WidescreenHacks.find(NativeBridge.nativeGetDiscHeader(), currentGame) else null
        widescreenHack = ws?.first
        val w = cheats.filter { it.enabled }.flatMap { CheatParser.parse(it.code).orEmpty() } +
            (ws?.let { CheatParser.parse(it.second).orEmpty() } ?: emptyList())
        NativeBridge.nativeSetCheats(w.map { it.addr }.toLongArray(), w.map { it.value }.toLongArray(), w.map { it.size }.toIntArray())
    }


    // ---- Gamepad mapping: Android keycode -> SaturnButton bit (persisted) ----
    private fun defaultKeyMap(): Map<Int, Int> = mapOf(
        KeyEvent.KEYCODE_DPAD_UP to SaturnButton.Up.bit, KeyEvent.KEYCODE_DPAD_DOWN to SaturnButton.Down.bit,
        KeyEvent.KEYCODE_DPAD_LEFT to SaturnButton.Left.bit, KeyEvent.KEYCODE_DPAD_RIGHT to SaturnButton.Right.bit,
        KeyEvent.KEYCODE_BUTTON_A to SaturnButton.B.bit, KeyEvent.KEYCODE_BUTTON_B to SaturnButton.A.bit,
        KeyEvent.KEYCODE_BUTTON_X to SaturnButton.Y.bit, KeyEvent.KEYCODE_BUTTON_Y to SaturnButton.X.bit,
        KeyEvent.KEYCODE_BUTTON_L1 to SaturnButton.L.bit, KeyEvent.KEYCODE_BUTTON_R1 to SaturnButton.R.bit,
        KeyEvent.KEYCODE_BUTTON_L2 to SaturnButton.Z.bit, KeyEvent.KEYCODE_BUTTON_R2 to SaturnButton.C.bit,
        KeyEvent.KEYCODE_BUTTON_START to SaturnButton.Start.bit,
        // Six-button pads (Saturn-style) report C and Z directly
        KeyEvent.KEYCODE_BUTTON_C to SaturnButton.C.bit, KeyEvent.KEYCODE_BUTTON_Z to SaturnButton.Z.bit,
    )

    /** Adds the default binding of every button that has no key yet (when that key is also unused). */
    private fun withDefaults(m: Map<Int, Int>): Map<Int, Int> {
        val out = m.toMutableMap()
        defaultKeyMap().forEach { (key, bit) ->
            if (key !in out && bit !in out.values) out[key] = bit
        }
        return out
    }
    var keyMap by mutableStateOf<Map<Int, Int>>(defaultKeyMap()); private set
    /** Button currently waiting for a physical key press in the remap dialog (null = not remapping). */
    var remapTarget by mutableStateOf<SaturnButton?>(null)

    fun keysFor(b: SaturnButton): List<Int> = keyMap.filterValues { it == b.bit }.keys.toList()

    fun bindKey(b: SaturnButton, keyCode: Int) {
        val m = keyMap.toMutableMap()
        m.entries.removeAll { it.value == b.bit || it.key == keyCode }
        m[keyCode] = b.bit
        keyMap = m; saveKeyMap()
    }
    fun resetKeyMap() { keyMap = defaultKeyMap(); saveKeyMap() }
    private fun saveKeyMap() = prefs.edit().putString("keyMap", keyMap.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()

    // ---- Save states ----
    const val STATE_SLOTS = 5
    const val QUICK_SLOT = 99
    const val AUTO_SLOT = 98
    private val statesDir get() = File(dataDir, "states")
    /** Folder name for a game's save states: the disc image file name, without characters that folders can't hold. */
    private fun stateDirName(gamePath: String) =
        File(gamePath).nameWithoutExtension.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "game" }

    /** Older versions named the folders after the disc hash; they are renamed to the game name (once, when found). */
    private var statesMigrated = false
    private fun migrateStateDirs() {
        if (statesMigrated) return
        statesMigrated = true
        statesDir.listFiles()?.forEach { dir ->
            if (!dir.isDirectory || !dir.name.matches(Regex("[0-9a-fA-F]{16,}"))) return@forEach
            val game = runCatching { File(dir, "game.txt").readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val target = File(statesDir, stateDirName(game))
            if (!target.exists()) dir.renameTo(target)
        }
    }

    private fun stateFile(slot: Int): File? {
        val game = currentGame ?: return null
        migrateStateDirs()
        val dir = File(statesDir, stateDirName(game))
        // States of this disc saved before the game was known by name (no game.txt yet)
        NativeBridge.nativeGetDiscHash()?.let { h -> File(statesDir, h).takeIf { it.isDirectory && !dir.exists() }?.renameTo(dir) }
        return File(dir, "${when (slot) { QUICK_SLOT -> "quick"; AUTO_SLOT -> "auto"; else -> slot.toString() }}.savestate")
    }

    /** Last-modified time per slot, or null if empty. */
    fun slotTimes(): List<Long?> = (0 until STATE_SLOTS).map { stateFile(it)?.takeIf { f -> f.exists() }?.lastModified() }

    suspend fun saveState(slot: Int): String? = withContext(Dispatchers.IO) {
        val f = stateFile(slot) ?: return@withContext "No game loaded"
        f.parentFile?.mkdirs()
        val err = NativeBridge.nativeSaveState(f.absolutePath)
        if (err == null) {
            runCatching { saveThumbnail(thumbFile(f)) }
            currentGame?.let { g -> runCatching { File(f.parentFile, "game.txt").writeText(g) } }
        }
        err
    }

    private fun thumbFile(state: File) = File(state.path + ".png")

    private fun saveThumbnail(out: File) {
        val d = NativeBridge.nativeGetLastFrame() ?: return
        val w = d[0]; val h = d[1]
        if (w <= 0 || h <= 0 || d.size < w * h + 2) return
        val bmp = android.graphics.Bitmap.createBitmap(d, 2, w, w, h, android.graphics.Bitmap.Config.ARGB_8888)
        out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    /** A save state with its screenshot, for the "Recent" view of the library. */
    data class RecentState(val gamePath: String, val slot: Int, val time: Long, val thumb: File?)

    /** All save states of all games (whose game is known), newest first. */
    fun recentStates(): List<RecentState> {
        val out = mutableListOf<RecentState>()
        migrateStateDirs()
        statesDir.listFiles()?.forEach { dir ->
            val game = runCatching { File(dir, "game.txt").readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@forEach
            dir.listFiles { f -> f.name.endsWith(".savestate") }?.forEach { f ->
                val name = f.name.removeSuffix(".savestate")
                val slot = when (name) { "quick" -> QUICK_SLOT; "auto" -> AUTO_SLOT; else -> name.toIntOrNull() ?: return@forEach }
                out += RecentState(game, slot, f.lastModified(), thumbFile(f).takeIf { it.exists() })
            }
        }
        return out.sortedByDescending { it.time }
    }

    /** Starts a game and loads one of its save states. Returns an error message or null. */
    suspend fun launchState(s: RecentState): String? {
        launch(s.gamePath, useAutoLoad = false)?.let { return it }
        return loadState(s.slot)
    }

    /** Screenshot taken when the slot was saved, or null. */
    fun slotThumbnail(slot: Int): android.graphics.Bitmap? =
        stateFile(slot)?.let { thumbFile(it) }?.takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
    suspend fun loadState(slot: Int): String? = withContext(Dispatchers.IO) {
        val f = stateFile(slot) ?: return@withContext "No game loaded"
        NativeBridge.nativeLoadState(f.absolutePath)
    }

    val biosFile get() = File(dataDir, "bios/ipl.bin")

    // ---- Emulator data location (BIOS, saves, states, cheats, covers) ----
    /** Folder holding the emulator data. Defaults to the app's private storage. */
    lateinit var dataDir: File; private set
    private val dataSubdirs = listOf("bios", "saves", "states", "cheats", "covers")
    val defaultDataDir: File get() = appContext.filesDir

    /**
     * Moves the emulator data to [target] (copying the existing files) and uses it from the next launch.
     * Returns an error message or null.
     */
    suspend fun changeDataDir(target: File): String? = withContext(Dispatchers.IO) {
        if (target.absolutePath == dataDir.absolutePath) return@withContext null
        if (!target.exists() && !target.mkdirs()) return@withContext "Cannot create ${target.path}"
        if (!target.canWrite()) return@withContext "Cannot write to ${target.path}"
        runCatching {
            dataSubdirs.forEach { name ->
                val src = File(dataDir, name)
                if (src.exists()) src.copyRecursively(File(target, name), overwrite = true)
            }
        }.exceptionOrNull()?.let { return@withContext "Copy failed: ${it.message}" }
        prefs.edit().putString("dataDir", if (target.absolutePath == defaultDataDir.absolutePath) null else target.absolutePath).commit()
        null
    }

    // ---- Library badges: games with save states / RetroAchievements ----
    var achievementGames by mutableStateOf<Set<String>>(emptySet()); private set
    /** Called when RetroAchievements recognizes the running game. */
    fun markAchievementGame() {
        val g = currentGame ?: return
        if (g !in achievementGames) {
            achievementGames = achievementGames + g
            prefs.edit().putStringSet("achievementGames", achievementGames).apply()
        }
    }
    /** Paths of the games that have at least one save state. */
    fun gamesWithStates(): Set<String> = recentStates().map { it.gamePath }.toSet()

    // ---- Play time per game (seconds, keyed by file name) ----
    private val playTimes = mutableStateMapOf<String, Long>()
    private var playStart = 0L
    private fun playKey(path: String) = path.substringAfterLast('/')
    fun playTime(path: String): Long = playTimes[playKey(path)] ?: 0L
    private fun loadPlayTimes() {
        prefs.getString("playTimes", null)?.split("\n")?.forEach { l ->
            val i = l.lastIndexOf('\t')
            if (i > 0) l.substring(i + 1).toLongOrNull()?.let { playTimes[l.substring(0, i)] = it }
        }
    }
    /** Adds the time played since the last call (while a game runs unpaused). */
    private fun accountPlayTime() {
        val now = android.os.SystemClock.elapsedRealtime()
        val game = currentGame
        if (playStart != 0L && game != null) {
            val k = playKey(game)
            playTimes[k] = (playTimes[k] ?: 0L) + (now - playStart) / 1000
            prefs.edit().putString("playTimes", playTimes.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
        }
        playStart = if (game != null && running && !paused) now else 0L
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("gbsat", Context.MODE_PRIVATE)
        dataDir = prefs.getString("dataDir", null)?.let { File(it) }?.takeIf { (it.isDirectory || it.mkdirs()) && it.canWrite() }
            ?: appContext.filesDir
        loadPlayTimes()
        achievementGames = prefs.getStringSet("achievementGames", emptySet())?.toSet() ?: emptySet()
        setupDone = prefs.getBoolean("setupDone", false) || File(dataDir, "bios/ipl.bin").exists()
        gamesDir = prefs.getString("gamesDir", "") ?: ""
        analogPad = prefs.getBoolean("analogPad", false)
        sh2Cache = prefs.getBoolean("sh2Cache", false)
        threadedVdp = prefs.getBoolean("threadedVdp", true)
        threadedScsp = prefs.getBoolean("threadedScsp", true)
        preloadDisc = prefs.getBoolean("preloadDisc", false)
        showTouchControls = prefs.getBoolean("touch", true)
        aspect = prefs.getInt("aspect", 0)
        showFps = prefs.getBoolean("showFps", false)
        showSpeed = prefs.getBoolean("showSpeed", false)
        showFfButton = prefs.getBoolean("showFfButton", false)
        showRenderer = prefs.getBoolean("showRenderer", false)
        showResolution = prefs.getBoolean("showResolution", false)
        showCpu = prefs.getBoolean("showCpu", false)
        showGpu = prefs.getBoolean("showGpu", false)
        showFrameTime = prefs.getBoolean("showFrameTime", false)
        fastBoot = prefs.getBoolean("fastBoot", true)
        // "Vulkan" (present-only, id 2) was replaced by Vulkan GPU, which also presents through Vulkan
        renderer = prefs.getInt("renderer", 0).let { when (it) { 2 -> 4; 3 -> 1; else -> it } } // ANGLE (3) was removed
        filter = prefs.getInt("filter", 1).coerceAtMost(1) // "Sharp bilinear" (2) was removed
        shader = prefs.getInt("shader", 0)
        resScale = prefs.getInt("resScale", 1).coerceIn(0, 4)
        cdSpeed = prefs.getInt("cdSpeed", 2).coerceIn(2, 200)
        frameSkip = prefs.getBoolean("frameSkip", false)
        volume = prefs.getInt("volume", 100).coerceIn(0, 100)
        autoSave = prefs.getBoolean("autoSave", false)
        autoLoad = prefs.getBoolean("autoLoad", false)
        ffAudio = prefs.getInt("ffAudio", 0).coerceIn(0, 2)
        ffToggle = prefs.getBoolean("ffToggle", false)
        prefs.getString("hotkeys", null)?.let { str ->
            runCatching {
                hotkeys = str.split(";").filter { it.contains("=") }.associate { e ->
                    HotkeyAction.valueOf(e.substringBefore('=')) to e.substringAfter('=').split("+").map { it.toInt() }.toSet()
                }
            }
        }
        prefs.getString("keyMap", null)?.let { str ->
            runCatching { keyMap = withDefaults(str.split(",").filter { it.isNotBlank() }.associate { e -> e.substringBefore(':').toInt() to e.substringAfter(':').toInt() }) }
        }

        // Crash report from the previous session. If it happened with the Vulkan GPU renderer, fall back to software.
        val crashFile = File(appContext.filesDir, "crash.txt")
        if (crashFile.exists()) {
            val text = runCatching { crashFile.readText() }.getOrDefault("")
            crashFile.delete()
            if (text.isNotBlank()) {
                val gpu = text.contains("renderer=4")
                if (gpu) { renderer = 0; prefs.edit().putInt("renderer", 0).apply() }
                lastCrash = (if (gpu) "GBSat closed while using the Vulkan GPU renderer, so the renderer was switched back to Software.\n\n" else "") +
                    "GBSat ${appVersion()} crashed last time. Please send this report:\n\n" + text
            }
        }
        NativeBridge.nativeSetCrashFile(crashFile.absolutePath)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { crashFile.writeText("renderer=$renderer\nkotlin exception in ${t.name}:\n" + android.util.Log.getStackTraceString(e)) }
            previous?.uncaughtException(t, e)
        }

        val bup = File(dataDir, "saves").apply { mkdirs() }.resolve("internal_backup.bin")
        NativeBridge.nativeInit(bup.absolutePath, File(dataDir, "saves/smpc.bin").absolutePath)
        applyOptions()
        RetroAchievements.init(appContext, appVersion().substringBefore(" "))
        if (biosFile.exists()) iplLoaded = NativeBridge.nativeLoadIpl(biosFile.absolutePath) == null
    }

    fun appVersion(): String =
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull() ?: "?"

    fun applyOptions() {
        NativeBridge.nativeSetPadType(analogPad)
        NativeBridge.nativeSetSH2Cache(sh2Cache)
        // Switching the render threads in the middle of a game slows it down a lot until it is restarted, so the
        // setting takes effect when a game starts
        if (!running) NativeBridge.nativeSetThreadedVdp(threadedVdp)
        NativeBridge.nativeSetThreadedScsp(threadedScsp)
        NativeBridge.nativeSetResolutionScale(effectiveScale)
        NativeBridge.nativeSetCdReadSpeed(cdSpeed)
        NativeBridge.nativeSetFrameSkip(frameSkip)
        NativeBridge.nativeSetAudio(volume, ffAudio)
        NativeBridge.nativeSetRenderer(renderer)
        NativeBridge.nativeSetDisplayOptions(filter, shader)
        if (running) applyCheats()
        if (fastBoot) NativeBridge.nativeSetFastBoot(true)
    }

    fun savePrefs() = prefs.edit()
        .putString("gamesDir", gamesDir).putBoolean("analogPad", analogPad).putBoolean("sh2Cache", sh2Cache)
        .putBoolean("threadedVdp", threadedVdp).putBoolean("threadedScsp", threadedScsp).putBoolean("preloadDisc", preloadDisc)
        .putBoolean("touch", showTouchControls).putInt("aspect", aspect).putBoolean("showFps", showFps).putBoolean("showSpeed", showSpeed).putBoolean("showFfButton", showFfButton).putBoolean("showRenderer", showRenderer).putBoolean("showResolution", showResolution).putBoolean("showCpu", showCpu).putBoolean("showGpu", showGpu).putBoolean("showFrameTime", showFrameTime)
        .putBoolean("fastBoot", fastBoot).putInt("renderer", renderer).putInt("filter", filter).putInt("shader", shader).putInt("resScale", resScale).putInt("cdSpeed", cdSpeed).putBoolean("frameSkip", frameSkip).putInt("volume", volume).putBoolean("autoSave", autoSave).putBoolean("autoLoad", autoLoad).putInt("ffAudio", ffAudio)
        .putBoolean("ffToggle", ffToggle).apply()

    /** Copies the picked BIOS into app storage and loads it. Returns an error message or null. */
    suspend fun importBios(context: Context, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        biosFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { i -> biosFile.outputStream().use { o -> i.copyTo(o) } }
            ?: return@withContext "Cannot read file"
        val err = NativeBridge.nativeLoadIpl(biosFile.absolutePath)
        iplLoaded = err == null
        if (err != null) biosFile.delete()
        err
    }

    /** Most recent save state of a game (auto, quick or slot), or null. */
    fun latestState(path: String): RecentState? = recentStates().firstOrNull { it.gamePath == path }

    suspend fun launch(path: String, useAutoLoad: Boolean = true): String? = withContext(Dispatchers.IO) {
        if (!iplLoaded) return@withContext "Load a Saturn BIOS (IPL ROM) in Settings first."
        NativeBridge.nativeSetThreadedVdp(threadedVdp)
        val err = NativeBridge.nativeLoadDisc(path, preloadDisc)
        if (err == null) {
            currentGame = path; running = true; loadCheats()
            if (useAutoLoad && autoLoad && stateFile(AUTO_SLOT)?.exists() == true) loadState(AUTO_SLOT)?.let { showToast(it) }
            pause(false)
            widescreenHack?.let { showToast("Widescreen hack enabled: $it") }
            NativeBridge.nativeTakeCartNotice()?.let { showToast(it) }
        }
        err
    }

    fun pause(p: Boolean) { accountPlayTime(); paused = p; NativeBridge.nativeSetPaused(p); accountPlayTime() }
    fun toggleFastForward() { fastForward = !fastForward; NativeBridge.nativeSetFastForward(fastForward) }
    fun reset(hard: Boolean) = NativeBridge.nativeReset(hard)
    fun toggleTray() { trayOpen = !trayOpen; NativeBridge.nativeSetTrayOpen(trayOpen) }
    /** Closes the game, saving the auto save state first when enabled. */
    fun quitGame() {
        if (autoSave && running && currentGame != null) {
            pause(true)
            scope.launch { saveState(AUTO_SLOT); closeGame() }
        } else closeGame()
    }

    /** Saves the auto save state synchronously (used when quitting the app from a game). */
    fun autoSaveBlocking() {
        if (autoSave && running && currentGame != null) kotlinx.coroutines.runBlocking { saveState(AUTO_SLOT) }
    }

    private fun closeGame() {
        pause(true); accountPlayTime(); running = false; currentGame = null; Hotkeys.reset(); cheats = emptyList()
        RetroAchievements.gameUnloaded()
        if (trayOpen) { trayOpen = false }
        NativeBridge.nativeEjectDisc()
    }

    // ---- Input merging: touch + gamepad each own a mask; native sees the OR. ----
    private var touchMask = 0
    private var padMask = 0
    private var touchStick = intArrayOf(0x80, 0x80)
    private var padStick = intArrayOf(0x80, 0x80)
    private var trig = intArrayOf(0, 0)

    private fun pushButtons() = NativeBridge.nativeSetButtons(touchMask or padMask)
    fun setTouchMask(m: Int) { touchMask = m; pushButtons() }
    fun setPadMask(m: Int) { padMask = m; pushButtons(); pushAnalog() }
    fun setPadStick(x: Int, y: Int) { padStick = intArrayOf(x, y); pushAnalog() }
    fun setTouchStick(x: Int, y: Int) { touchStick = intArrayOf(x, y); pushAnalog() }
    fun setTriggers(l: Int, r: Int) { trig = intArrayOf(l, r); pushAnalog() }
    private fun pushAnalog() {
        val x = if (padStick[0] != 0x80) padStick[0] else touchStick[0]
        val y = if (padStick[1] != 0x80) padStick[1] else touchStick[1]
        val m = touchMask or padMask
        val l = maxOf(trig[0], if (m and SaturnButton.L.bit != 0) 255 else 0)
        val r = maxOf(trig[1], if (m and SaturnButton.R.bit != 0) 255 else 0)
        NativeBridge.nativeSetAnalog(x, y, l, r)
    }
}
