package app.gbsat.android

import gb.frame.HotkeyAction
import gb.frame.Hotkeys
import gb.frame.SaveData
import gb.frame.RetroAchievements
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
import gb.frame.DataFile as File

/** Process-wide emulator controller: owns persistent settings and merges all input sources into one pad. */
object Emulator {
    var initialized by mutableStateOf(false)
    var initializationError by mutableStateOf<String?>(null)
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
    var cpuSync by mutableStateOf(1) // SH-2 sync: 0 = Accurate (every 32 cycles), 1 = Fast (every 128, default)
    // Not user options (performance logs: turning them off was never faster). One field only acts with the CPU renderers
    // (the native side ignores it with Vulkan). Idle-loop skipping and threaded VDP stay on unless a game's recommended
    // profile turns them off ("idleSkip": false / "threadedVdp": false in assets/game_profiles.json): the way to fix a
    // game that misbehaves with them without bringing the options back.
    var idleSkip = true; private set    // SH-2 idle-loop skipping
    val oneField = true                 // hi-res interlaced screens: render one field of each pair
    var threadedVdp = true; private set // VDP1/VDP2 rendering on their own threads
    var scspMode by mutableStateOf(0)        // threaded audio: 0 = Auto (per game, tuned while playing), 1 = On, 2 = Off
    var preloadDisc by mutableStateOf(false)
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
    // Renderer: 1 = CPU (OpenGL): VDPs emulated on the CPU, shown through OpenGL ES; 4 = GPU (Vulkan).
    // 0 (CPU, plain window output) is no longer offered: it is only used internally when OpenGL ES can't start.
    var renderer by mutableStateOf(1)
    /** OpenGL ES could not start on this device: CPU frames go to the plain window output instead. */
    var glFallback by mutableStateOf(false)
    /** Renderer id actually used for presentation (see [glFallback]). */
    val displayRenderer get() = if (renderer == 1 && glFallback) 0 else renderer
    val rendererLabel get() = when (displayRenderer) { 4 -> "GPU (Vulkan)"; 1 -> "CPU (OpenGL)"; else -> "CPU (plain)" }

    /** Use the built-in recommended settings per game (assets/game_profiles.json). */
    // Per-game settings while a game runs: renderer, frame skip and threaded audio start from the user's own choice for
    // that game, else the recommended profile, else the global settings. The global values are kept aside meanwhile.
    private data class GameOpts(val renderer: Int, val frameSkip: Boolean, val scspMode: Int)
    private var sessionGlobals: GameOpts? = null
    private var sessionStart: GameOpts? = null
    var useGameProfiles by mutableStateOf(true) // recommended per-game settings (assets/game_profiles.json)
    var frameSkip by mutableStateOf(false) // automatic frame skip (only while below full speed)
    var volume by mutableStateOf(100)      // audio volume, percent
    var autoSave by mutableStateOf(false)  // save state when leaving a game
    var autoLoad by mutableStateOf(false)  // load that state when starting the game
    var ffAudio by mutableStateOf(0)       // fast-forward audio: 0 default, 1 speed up, 2 mute

    // ---- Presets: groups of video/performance options ----
    enum class Preset(val label: String) { Low("Low"), Mid("Mid"), High("High") }
    private data class PresetValues(val renderer: Int, val resScale: Int, val shader: Int, val filter: Int,
                                    val frameSkip: Boolean, val sh2Cache: Boolean)
    private fun presetValues(p: Preset) = when (p) {
        // Powerful devices: GPU rendering at the screen's native resolution with scanlines
        Preset.High -> PresetValues(renderer = 4, resScale = 0, shader = 1, filter = 1, frameSkip = false, sh2Cache = false)
        // Mid-range: CPU rendering (OpenGL output) with scanlines
        Preset.Mid -> PresetValues(renderer = 1, resScale = 1, shader = 1, filter = 1, frameSkip = false, sh2Cache = false)
        // Modest devices: CPU rendering (OpenGL output), no shader, frame skip
        Preset.Low -> PresetValues(renderer = 1, resScale = 1, shader = 0, filter = 1, frameSkip = true, sh2Cache = false)
    }
    /** The preset matching the current options, or null ("Custom") when any of them was changed. */
    /** The options in use, for the performance log (key=value pairs separated by ';'). */
    fun logSettings(): String {
        fun on(b: Boolean) = if (b) "on" else "off"
        return listOf(
            "preset=${currentPreset?.label ?: "custom"}",
            "renderer=$rendererLabel",
            "scale=$resScaleLabel",
            "filter=${if (filter == 0) "nearest" else "bilinear"}",
            "shader=${gb.frame.Shaders.name(shader)}",
            "aspect=${listOf("4:3", "fill", "16:9").getOrElse(aspect) { "$aspect" }}",
            "widescreen=${widescreenHack ?: "off"}",
            "frameskip=${on(frameSkip)}",
            "sh2_cache=${on(sh2Cache)}",
            "cpu_sync=${if (cpuSync == 1) "fast" else "accurate"}",
            "idle_skip=${on(idleSkip)}",
            "one_field=${on(oneField)}",
            "threaded_vdp=${on(threadedVdp)}",
            "threaded_audio=${listOf("auto", "on", "off").getOrElse(scspMode) { "$scspMode" }}",
            "cd_speed=${cdSpeed}x",
            "skip_loads=${on(skipLoads)}",
            "skip_transitions=${on(skipTransitions)}",
            "skip_saves=${on(skipSaves)}",
            "preload_disc=${on(preloadDisc)}",
            "fast_boot=${on(fastBoot)}",
            "game_profiles=${on(useGameProfiles)}",
            "ff_audio=${listOf("default", "speed up", "mute").getOrElse(ffAudio) { "$ffAudio" }}",
            "volume=$volume",
            "analog_pad=${on(analogPad)}",
        ).joinToString(";") { it.replace(",", " ") }
    }

    val currentPreset: Preset?
        get() = Preset.entries.firstOrNull { presetValues(it) == PresetValues(renderer, resScale, shader, filter, frameSkip, sh2Cache) }
    fun applyPreset(p: Preset) {
        val v = presetValues(p)
        renderer = v.renderer; resScale = v.resScale; shader = v.shader; filter = v.filter
        frameSkip = v.frameSkip; sh2Cache = v.sh2Cache
        savePrefs(); applyOptions()
    }
    var setupDone by mutableStateOf(true)
    fun finishSetup() { setupDone = true; prefs.edit().putBoolean("setupDone", true).apply() }
    var cdSpeed by mutableStateOf(8) // CD read speed factor (2 = original); 8x by default since streams switch back to 2x
    // Skip Engine: maximum speed (sound muted) through CD loads behind a still silent screen, silent black transitions and
    // backup memory (save) waits
    var skipLoads by mutableStateOf(false)
    var skipTransitions by mutableStateOf(false)
    var skipSaves by mutableStateOf(false)
    var resScale by mutableStateOf(1) // GPU (Vulkan) internal resolution scale (1x..4x); 0 = Native (matches the screen)
    /**
     * Scale used for "Native": the screen's short side divided by the Saturn's ~224 lines, rounded, at most what the GPU
     * handles (4x on Adreno 730+, 3x on Adreno 640-7xx / Mali-G710+, 1x on weaker GPUs).
     */
    val nativeScale: Int
        get() {
            val m = appContext.resources.displayMetrics
            return Math.round(minOf(m.widthPixels, m.heightPixels) / 224f).coerceIn(1, device.gpuMaxScale.coerceIn(1, 4))
        }
    val effectiveScale get() = if (resScale == 0) nativeScale else resScale
    val resScaleLabel get() = if (resScale == 0) "Native (${nativeScale}x)" else "${resScale}x"
    var filter by mutableStateOf(1)   // 0 nearest, 1 bilinear
    var shader by mutableStateOf(gb.frame.Shaders.NONE) // presenter shader id (gb.frame.Shaders)
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
        // Never apply cheats while RetroAchievements hardcore is active, including cheats enabled earlier.
        val w = (if (hardcore) emptyList() else cheats.filter { it.enabled }.flatMap { CheatParser.parse(it.code).orEmpty() }) +
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
    const val STATE_SLOTS = SaveData.MANUAL_SAVE_STATE_SLOTS
    const val QUICK_SLOT = 99
    const val AUTO_SLOT = 98
    private val statesDir get() = File(dataDir, gb.frame.DataLayout.SAVESTATES)
    private lateinit var stateFiles: gb.frame.SaveStateFiles
    private fun stateLabel(slot: Int) = when (slot) {
        QUICK_SLOT -> "quick"
        AUTO_SLOT -> "auto"
        else -> { require(slot in 0 until STATE_SLOTS); (slot + 1).toString() }
    }
    private fun stateSlot(label: String) = when (label) { "quick" -> QUICK_SLOT; "auto" -> AUTO_SLOT; else -> label.toInt() - 1 }
    private fun migrateStateDirs() = stateFiles.migrateLegacyNumberedStates()
    fun registerStateGames(games: List<gb.frame.Game>) = stateFiles.rememberExistingGames(games)

    private fun stateFile(slot: Int): File? {
        val game = currentGame ?: return null
        migrateStateDirs()
        val name = SaveData.gameFolderName(game)
        val old = mutableListOf(File(statesDir, name), File(dataDir, "states/$name"))
        NativeBridge.nativeGetDiscHash()?.let { old += File(statesDir, it); old += File(dataDir, "states/$it") }
        stateFiles.migrateLegacyGame(game, old)
        return stateFiles.file(game, stateLabel(slot))
    }

    /** Last-modified time per slot, or null if empty. */
    fun slotTimes(): List<Long?> = (0 until STATE_SLOTS).map { stateFile(it)?.takeIf { f -> f.exists() }?.lastModified() }

    suspend fun saveState(slot: Int): String? = withContext(Dispatchers.IO) {
        val f = stateFile(slot) ?: return@withContext "No game loaded"
        f.parentFile?.mkdirs()
        val err = NativeBridge.nativeSaveState(f.absolutePath)
        if (err == null) {
            runCatching { saveThumbnail(thumbFile(f)) }
            // No achievement progress: the empty side file isn't kept
            File(f.path + ".ra").takeIf { it.length() == 0L }?.delete()
            currentGame?.let { stateFiles.remember(it) }
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
        stateFiles.games().forEach { game ->
            stateFiles.files(game).forEach { (label, f) ->
                out += RecentState(game, stateSlot(label), f.lastModified(), thumbFile(f).takeIf { it.exists() })
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
        stateFile(slot)?.let { thumbFile(it) }?.takeIf { it.exists() }?.let { it.inputStream().use { stream -> android.graphics.BitmapFactory.decodeStream(stream) } }
    suspend fun loadState(slot: Int): String? = withContext(Dispatchers.IO) {
        val f = stateFile(slot) ?: return@withContext "No game loaded"
        NativeBridge.nativeLoadState(f.absolutePath)
    }

    val biosFile get() = File(dataDir, "bios/ipl.bin")

    // ---- Emulator data location (BIOS, saves, states, cheats, covers) ----
    /** Folder holding the emulator data. Defaults to the app's private storage. */
    lateinit var dataDir: File; private set
    lateinit var dataFolder: gb.frame.AppDataFolder; private set
    private val dataSubdirs = listOf("bios", "saves", "savestates", "cheats", "covers")
    /** Folder used by an older version for the data, when it couldn't be read after the update (shown in Settings). */
    var legacyDataDir by mutableStateOf<String?>(null); private set

    /** Stores a ROM cartridge dump picked by the user (KOF95 or Ultraman), used automatically by the games that need it. */
    suspend fun importCartRom(uri: android.net.Uri, fileName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dst = File(dataDir, "carts").apply { mkdirs() }.resolve(fileName)
            val staged = File.createTempFile("cart-", ".tmp", dst.parentFile)
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input -> staged.outputStream().use { input.copyTo(it) } }
                    ?: error("Can't read the file")
                check(staged.renameTo(dst)) { "Could not replace cartridge ROM" }
            } finally { staged.delete() }
        }.exceptionOrNull()?.let { "Copy failed: ${it.message}" }
    }
    fun hasCartRom(fileName: String) = File(dataDir, "carts/$fileName").exists()

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
    /** Seconds played (shared play time tracker of the UI kit). */
    fun playTime(path: String): Long = gb.frame.PlayTime.of(path)
    private fun accountPlayTime() = gb.frame.PlayTime.update(currentGame, running && !paused)

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("gbsat", Context.MODE_PRIVATE)
        Storage.init(appContext)
        Thread { gb.frame.DeviceTier.detect() }.start() // GPU name from a throwaway OpenGL ES context, off the main thread
        // Data lives in the app's storage. Older versions could keep it in a folder of the user's choice, which needed
        // the all-files permission; select that folder in System → Data folder to restore it through SAF.
        val oldDir = prefs.getString("dataDir", null)
        dataDir = File(appContext.filesDir.path)
        if (oldDir != null) {
            val f = File(oldDir)
            if (f.canRead()) runCatching { dataSubdirs.forEach { n -> File(f, n).takeIf { it.exists() }?.copyRecursively(File(dataDir, n), overwrite = false) } }
            else legacyDataDir = oldDir
            prefs.edit().remove("dataDir").apply()
        }
        gb.frame.DataLayout.move(File(dataDir, "states"), File(dataDir, "savestates"))
        dataFolder = gb.frame.AppDataFolder(appContext, "GBSat", (dataSubdirs + "carts").associateWith { File(dataDir, it) })
        System.loadLibrary("gbsat")
        dataFolder.openAtStartup()
        stateFiles = gb.frame.SaveStateFiles(dataDir, prefs)
        stateFiles.migrateLegacyNumberedStatesOnce(dataFolder.folder ?: "app")
        // Games folder paths from older versions are no longer readable: the folder must be picked again
        if (prefs.getString("gamesDir", "")?.startsWith("/") == true) prefs.edit().putString("gamesDir", "").apply()
        gb.frame.PlayTime.init(prefs)
        gb.frame.LibraryView.init(prefs)
        gb.frame.HomeTheme.init(prefs)
        gb.frame.GameProfiles.init(context)
        Hotkeys.configure(bindings = { hotkeys }, run = { a, down -> runHotkey(a, down) })
        achievementGames = prefs.getStringSet("achievementGames", emptySet())?.toSet() ?: emptySet()
        setupDone = prefs.getBoolean("setupDone", false) || File(dataDir, "bios/ipl.bin").exists()
        gamesDir = prefs.getString("gamesDir", "") ?: ""
        analogPad = prefs.getBoolean("analogPad", false)
        sh2Cache = prefs.getBoolean("sh2Cache", false)
        // "sh2Sync": Fast became the default before the option shipped; "cpuSync" only held values from test builds
        prefs.edit().remove("cpuSync").apply()
        cpuSync = prefs.getInt("sh2Sync", 1).coerceIn(0, 1)
        prefs.edit().remove("idleSkip").remove("oneField").remove("threadedVdp").apply() // no longer options (always on)
        scspMode = prefs.getInt("scspMode", 0).coerceIn(0, 2)
        prefs.edit().remove("threadedScsp").remove("threadedScspDevice").apply() // replaced by scspMode + per-game memory
        // Threaded audio choices the Auto tuner remembered per game ("scsp:<game>") were measured before the VDP2 render
        // thread stopped blocking the emulation at the end of each frame, which changed which mode is faster (Burning
        // Rangers on the RG Rotate stayed on "off" at ~55 fps against ~59 with threaded audio), and again after the sound
        // thread stopped polling while waiting and Auto started threaded on 2 fast cores: start over ("scsp3:")
        prefs.all.keys.filter { it.startsWith("scsp:") || it.startsWith("scsp2:") }.takeIf { it.isNotEmpty() }?.let { old ->
            prefs.edit().apply { old.forEach { remove(it) } }.apply()
        }
        preloadDisc = prefs.getBoolean("preloadDisc", false)
        showTouchControls = prefs.getBoolean("touch", true)
        aspect = prefs.getInt("aspect", 0).coerceIn(0, 2)
        showFps = prefs.getBoolean("showFps", false)
        showSpeed = prefs.getBoolean("showSpeed", false)
        showFfButton = prefs.getBoolean("showFfButton", false)
        showRenderer = prefs.getBoolean("showRenderer", false)
        showResolution = prefs.getBoolean("showResolution", false)
        showCpu = prefs.getBoolean("showCpu", false)
        showGpu = prefs.getBoolean("showGpu", false)
        showFrameTime = prefs.getBoolean("showFrameTime", false)
        fastBoot = prefs.getBoolean("fastBoot", true)
        useGameProfiles = prefs.getBoolean("gameProfiles", true)
        // Older renderer ids: 0 (Software) became CPU (OpenGL), 2 (Vulkan present) GPU (Vulkan), 3 (ANGLE) CPU (OpenGL)
        renderer = prefs.getInt("renderer", 1).let { if (it == 4 || it == 2) 4 else 1 }
        filter = prefs.getInt("filter", 1).coerceIn(0, 1) // "Sharp bilinear" (2) was removed
        shader = gb.frame.Shaders.sanitize(prefs.getInt("shader", 0)) // the removed dot matrix becomes none
        resScale = prefs.getInt("resScale", 1).coerceIn(0, 4)
        cdSpeed = prefs.getInt("cdSpeed", 8).let { if (it in setOf(2, 4, 8, 16)) it else 8 }
        // Once: 4x (the usual choice before videos and streamed music switched back to the original speed) becomes 8x
        if (!prefs.getBoolean("cdSpeed8x", false)) {
            if (cdSpeed == 4) cdSpeed = 8
            prefs.edit().putBoolean("cdSpeed8x", true).putInt("cdSpeed", cdSpeed).apply()
        }
        skipLoads = prefs.getBoolean("skipLoads", false)
        skipTransitions = prefs.getBoolean("skipTransitions", false)
        skipSaves = prefs.getBoolean("skipSaves", false)
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

        // Crash report from the previous session. If it happened with the GPU (Vulkan) renderer, switch to CPU (OpenGL).
        val crashFile = File(appContext.filesDir, "crash.txt")
        if (crashFile.exists()) {
            val text = runCatching { crashFile.readText() }.getOrDefault("")
            crashFile.delete()
            if (text.isNotBlank()) {
                val gpu = text.contains("renderer=4")
                if (gpu) { renderer = 1; prefs.edit().putInt("renderer", 1).apply() }
                lastCrash = (if (gpu) "GBSat closed while using the GPU (Vulkan) renderer, so the renderer was switched to CPU (OpenGL).\n\n" else "") +
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
        NativeBridge.nativeInit(bup.absolutePath, File(dataDir, "saves/smpc.bin").absolutePath)?.let { error(it) }
        applyOptions()
        RetroAchievements.init(appContext, "GBSat", appVersion().substringBefore(" "), GBSatRa)
        if (biosFile.exists()) iplLoaded = NativeBridge.nativeLoadIpl(biosFile.absolutePath) == null
    }

    fun appVersion(): String =
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull() ?: "?"

    fun applyOptions() {
        NativeBridge.nativeSetPadType(analogPad)
        NativeBridge.nativeSetSH2Cache(sh2Cache)
        NativeBridge.nativeSetSH2SyncStep(if (cpuSync == 1) 128 else 32)
        NativeBridge.nativeSetSH2Jit(0)
        NativeBridge.nativeSetIdleSkip(idleSkip)
        NativeBridge.nativeSetOneField(oneField)
        NativeBridge.nativeSetThreadedVdp(threadedVdp)
        NativeBridge.nativeSetScspMode(scspMode)
        NativeBridge.nativeSetResolutionScale(effectiveScale)
        NativeBridge.nativeSetCdReadSpeed(cdSpeed)
        NativeBridge.nativeSetAutoSkip(skipLoads, skipTransitions, skipSaves)
        NativeBridge.nativeSetFrameSkip(frameSkip)
        NativeBridge.nativeSetAudio(volume, ffAudio)
        NativeBridge.nativeSetRenderer(displayRenderer)
        NativeBridge.nativeSetDisplayOptions(filter, shader)
        if (running) applyCheats()
        NativeBridge.nativeSetFastBoot(fastBoot)
    }

    fun savePrefs() {
        // While a game runs, changes to the per-game options are remembered for that game; the globals stay as they were
        val g = sessionGlobals
        val game = currentGame
        val start = sessionStart
        if (g != null && game != null && start != null) {
            val e = prefs.edit()
            if (renderer != start.renderer) e.putInt("game:$game:renderer", renderer)
            if (frameSkip != start.frameSkip) e.putBoolean("game:$game:frameSkip", frameSkip)
            if (scspMode != start.scspMode) e.putInt("game:$game:scspMode", scspMode)
            e.apply()
            sessionStart = GameOpts(renderer, frameSkip, scspMode)
        }
        savePrefsEditor(g).apply()
    }

    private fun savePrefsEditor(globals: GameOpts?) = prefs.edit()
        .putString("gamesDir", gamesDir).putBoolean("analogPad", analogPad).putBoolean("sh2Cache", sh2Cache).putInt("sh2Sync", cpuSync)
        .putInt("scspMode", globals?.scspMode ?: scspMode).putBoolean("gameProfiles", useGameProfiles).putBoolean("preloadDisc", preloadDisc)
        .putBoolean("touch", showTouchControls).putInt("aspect", aspect).putBoolean("showFps", showFps).putBoolean("showSpeed", showSpeed).putBoolean("showFfButton", showFfButton).putBoolean("showRenderer", showRenderer).putBoolean("showResolution", showResolution).putBoolean("showCpu", showCpu).putBoolean("showGpu", showGpu).putBoolean("showFrameTime", showFrameTime)
        .putBoolean("fastBoot", fastBoot).putInt("renderer", globals?.renderer ?: renderer).putInt("filter", filter).putInt("shader", shader).putInt("resScale", resScale).putInt("cdSpeed", cdSpeed).putBoolean("skipLoads", skipLoads).putBoolean("skipTransitions", skipTransitions).putBoolean("skipSaves", skipSaves).putBoolean("frameSkip", globals?.frameSkip ?: frameSkip).putInt("volume", volume).putBoolean("autoSave", autoSave).putBoolean("autoLoad", autoLoad).putInt("ffAudio", ffAudio)
        .putBoolean("ffToggle", ffToggle)

    /** Validate a staged BIOS in the active data folder before replacing the installed one. */
    suspend fun importBios(context: Context, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        val destination = biosFile
        destination.parentFile?.mkdirs()
        val staged = File.createTempFile("bios-", ".tmp", destination.parentFile)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> staged.outputStream().use { input.copyTo(it) } }
                ?: return@withContext "Cannot read file"
            if (staged.length() != 512L * 1024) return@withContext "Saturn BIOS must be 512 KiB"
            NativeBridge.nativeLoadIpl(staged.absolutePath)?.let { return@withContext it }
            if (!staged.renameTo(destination)) {
                if (destination.exists()) NativeBridge.nativeLoadIpl(destination.absolutePath)
                return@withContext "Could not replace BIOS"
            }
            iplLoaded = true
            null
        } catch (failure: Exception) { failure.message ?: "Cannot import BIOS" }
        finally { staged.delete() }
    }

    /** Device class (CPU cores and GPU): recommended preset, game profiles, Native resolution cap. */
    val device get() = gb.frame.DeviceTier.detect()

    /**
     * Preset for this device, applied on first run: High needs a strong GPU (e.g. Snapdragon 865), Mid a fast CPU (e.g.
     * Unisoc T820), Low for the rest (e.g. Unisoc T618, Rockchip RK3568). A GPU that isn't recognized never gets High.
     */
    val recommendedPreset get() = when (device.tier) {
        gb.frame.DeviceTier.Tier.High -> Preset.High
        gb.frame.DeviceTier.Tier.Mid -> Preset.Mid
        else -> Preset.Low
    }

    /** Most recent save state of a game (auto, quick or slot), or null. */
    fun latestState(path: String): RecentState? {
        migrateStateDirs()
        return stateFiles.files(path).map { (label, file) ->
            RecentState(path, stateSlot(label), file.lastModified(), thumbFile(file).takeIf { it.exists() })
        }.maxByOrNull { it.time }
    }

    suspend fun launch(path: String, useAutoLoad: Boolean = true): String? = withContext(Dispatchers.IO) {
        if (!iplLoaded) return@withContext "Load a Saturn BIOS (IPL ROM) in Settings first."
        rememberScspMode() // a game was still running
        openDisc?.close()
        android.util.Log.i("GBSat-Disc", "launch requested path=" + path + "; name=" + Storage.fileName(path))
        // The kit opens the disc and retries from a copy when the core can't read it through the descriptor (SafGames)
        val loaded = Storage.openAndLoad(path) { disc ->
            android.util.Log.i("GBSat-Disc", "calling nativeLoadDisc path=" + disc.path + "; preload=" + preloadDisc)
            NativeBridge.nativeLoadDisc(disc.path, preloadDisc)
                .also { android.util.Log.i("GBSat-Disc", "nativeLoadDisc result=" + (it ?: "success")) }
        }
        val err = loaded.error
        loaded.disc?.let { openDisc = it }
        if (err == null) {
            currentGame = path; running = true; loadCheats()
            gb.frame.LibraryView.gameOpened(path)
            applyGameOptions(path)
            // Auto load: continue from the game's most recent save state (auto, quick or a numbered slot)
            if (useAutoLoad && autoLoad) latestState(path)?.let { st -> loadState(st.slot)?.let { showToast(it) } }
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

    /** Saves the auto save state without blocking (used when the app goes to the background during a game). */
    fun autoSaveInBackground() {
        rememberScspMode()
        if (autoSave && running && currentGame != null) scope.launch { saveState(AUTO_SLOT) }
    }

    /** Saves the auto save state synchronously (used when quitting the app from a game). */
    fun autoSaveBlocking() {
        rememberScspMode()
        if (autoSave && running && currentGame != null) kotlinx.coroutines.runBlocking { saveState(AUTO_SLOT) }
    }

    /** Auto threaded audio: remembers the mode the tuner kept for this game. */
    fun rememberScspMode() {
        val game = currentGame ?: return
        if (scspMode == 0 && running) prefs.edit().putBoolean("scsp3:$game", NativeBridge.nativeGetThreadedScsp()).apply()
    }

    /**
     * Per-game renderer, frame skip and threaded audio: the user's choice for this game, else the recommended profile
     * for this device class, else the global settings.
     */
    private fun applyGameOptions(path: String) {
        val globals = sessionGlobals ?: GameOpts(renderer, frameSkip, scspMode)
        sessionGlobals = globals
        val header = NativeBridge.nativeGetDiscHeader().orEmpty()
        val title = header.takeIf { it.length > 0x60 }?.substring(0x60, minOf(header.length, 0xD0))?.trim()
        val id = header.takeIf { it.length >= 0x2A }?.substring(0x20, 0x2A)?.trim()
        val profile = if (useGameProfiles) gb.frame.GameProfiles.lookup(title, id, device.tier) else emptyMap()
        var r = globals.renderer; var fs = globals.frameSkip; var sm = globals.scspMode
        // Auto threaded audio starts on, on every device. On the Unisoc T618 (2 fast cores) it used to cost the background
        // renderer too much, but with the sound thread no longer polling while it waits it was faster in every game
        // profiled (Daytona USA: emulation thread ~60% of a core instead of ~76%); the tuner still tries the other mode
        // when a game runs slow
        var startThreaded = true
        var audioKnown = false
        (profile["renderer"] as? String)?.let { r = if (it == "gpu") 4 else 1 }
        (profile["frameSkip"] as? Boolean)?.let { fs = it }
        // Escape hatches for games that misbehave (no user option): applied even when the user chose other settings
        idleSkip = profile["idleSkip"] as? Boolean ?: true
        threadedVdp = profile["threadedVdp"] as? Boolean ?: true
        // The threaded audio choices were measured on chips with 2 fast cores, where sound and the background renderer compete
        // for the second one; with 3+ (e.g. Snapdragon 662 in the Low class) both get a core and Auto decides
        if (device.fastCores <= 2) (profile["threadedAudio"] as? Boolean)?.let { on -> if (sm == 0) { startThreaded = on; audioKnown = true } else sm = if (on) 1 else 2 }
        // Auto threaded audio: what the tuner measured for this game on this device beats the profile
        if (prefs.contains("scsp3:$path")) { startThreaded = prefs.getBoolean("scsp3:$path", startThreaded); audioKnown = true }
        // The user's own choices for this game win
        var userChoice = false
        if (prefs.contains("game:$path:renderer")) { val saved = prefs.getInt("game:$path:renderer", r); r = if (saved == 4 || saved == 2) 4 else 1; userChoice = true }
        if (prefs.contains("game:$path:frameSkip")) { fs = prefs.getBoolean("game:$path:frameSkip", fs); userChoice = true }
        if (prefs.contains("game:$path:scspMode")) { sm = prefs.getInt("game:$path:scspMode", sm).coerceIn(0, 2); userChoice = true }
        renderer = r; frameSkip = fs; scspMode = sm
        sessionStart = GameOpts(r, fs, sm)
        applyOptions()
        if (sm == 0) {
            NativeBridge.nativeSetThreadedScsp(startThreaded)
            if (audioKnown) NativeBridge.nativeScspModeKnown()
        }
        if (profile.isNotEmpty() && !userChoice) showToast("Recommended settings for this game: $rendererLabel")
    }

    private fun closeGame() {
        rememberScspMode()
        // Back to the global renderer, frame skip and threaded audio
        sessionGlobals?.let { renderer = it.renderer; frameSkip = it.frameSkip; scspMode = it.scspMode }
        idleSkip = true; threadedVdp = true
        sessionGlobals = null; sessionStart = null
        applyOptions()
        pause(true); accountPlayTime(); running = false; currentGame = null; Hotkeys.reset(); cheats = emptyList()
        RetroAchievements.gameUnloaded()
        if (trayOpen) { trayOpen = false }
        NativeBridge.nativeEjectDisc()
        openDisc?.close(); openDisc = null
    }
    /** Files of the running disc (kept open while the game runs). */
    private var openDisc: gb.frame.OpenDisc? = null

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
