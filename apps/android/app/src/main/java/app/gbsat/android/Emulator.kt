package app.gbsat.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    var pal by mutableStateOf(false)
    var threadedVdp by mutableStateOf(true)
    var preloadDisc by mutableStateOf(false)
    var cartridge by mutableStateOf(0)
    var showTouchControls by mutableStateOf(true)
    var aspect by mutableStateOf(0) // 0 = 4:3, 1 = fill, 2 = 16:9
    var showFps by mutableStateOf(false)
    var fastBoot by mutableStateOf(true)
    var renderer by mutableStateOf(0) // 0 = software, 1 = OpenGL ES, 2 = Vulkan (present), 3 = ANGLE, 4 = Vulkan GPU (VDP on GPU)
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
    private fun cheatFile() = NativeBridge.nativeGetDiscHash()?.let { File(appContext.filesDir, "cheats/$it.txt") }

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
    private fun applyCheats() {
        val w = cheats.filter { it.enabled }.flatMap { CheatParser.parse(it.code).orEmpty() }
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
    )
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
    private val statesDir get() = File(appContext.filesDir, "states")
    private fun stateFile(slot: Int): File? =
        NativeBridge.nativeGetDiscHash()?.let { File(statesDir, "$it/${if (slot == QUICK_SLOT) "quick" else slot.toString()}.savestate") }

    /** Last-modified time per slot, or null if empty. */
    fun slotTimes(): List<Long?> = (0 until STATE_SLOTS).map { stateFile(it)?.takeIf { f -> f.exists() }?.lastModified() }

    suspend fun saveState(slot: Int): String? = withContext(Dispatchers.IO) {
        val f = stateFile(slot) ?: return@withContext "No game loaded"
        NativeBridge.nativeSaveState(f.absolutePath)
    }
    suspend fun loadState(slot: Int): String? = withContext(Dispatchers.IO) {
        val f = stateFile(slot) ?: return@withContext "No game loaded"
        NativeBridge.nativeLoadState(f.absolutePath)
    }

    /**
     * Saves the current state to Download/GBSat so it can be shared for debugging (e.g. renderer issues).
     * Returns the file path or an error message prefixed with "!".
     */
    suspend fun exportDebugState(): String = withContext(Dispatchers.IO) {
        val game = currentGame ?: return@withContext "!No game loaded"
        val name = game.substringAfterLast('/').substringBeforeLast('.').replace(Regex("[^A-Za-z0-9()_ -]"), "_")
        val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "GBSat")
        dir.mkdirs()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val f = File(dir, "$name-$stamp.state")
        val err = NativeBridge.nativeSaveState(f.absolutePath)
        if (err != null) "!$err" else f.absolutePath
    }

    val biosFile get() = File(appContext.filesDir, "bios/ipl.bin")

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("gbsat", Context.MODE_PRIVATE)
        gamesDir = prefs.getString("gamesDir", "") ?: ""
        analogPad = prefs.getBoolean("analogPad", false)
        sh2Cache = prefs.getBoolean("sh2Cache", false)
        pal = prefs.getBoolean("pal", false)
        threadedVdp = prefs.getBoolean("threadedVdp", true)
        preloadDisc = prefs.getBoolean("preloadDisc", false)
        cartridge = prefs.getInt("cartridge", 0)
        showTouchControls = prefs.getBoolean("touch", true)
        aspect = prefs.getInt("aspect", 0)
        showFps = prefs.getBoolean("showFps", false)
        fastBoot = prefs.getBoolean("fastBoot", true)
        // "Vulkan" (present-only, id 2) was replaced by Vulkan GPU, which also presents through Vulkan
        renderer = prefs.getInt("renderer", 0).let { if (it == 2) 4 else it }
        filter = prefs.getInt("filter", 1).coerceAtMost(1) // "Sharp bilinear" (2) was removed
        shader = prefs.getInt("shader", 0)
        ffToggle = prefs.getBoolean("ffToggle", false)
        prefs.getString("hotkeys", null)?.let { str ->
            runCatching {
                hotkeys = str.split(";").filter { it.contains("=") }.associate { e ->
                    HotkeyAction.valueOf(e.substringBefore('=')) to e.substringAfter('=').split("+").map { it.toInt() }.toSet()
                }
            }
        }
        prefs.getString("keyMap", null)?.let { str ->
            runCatching { keyMap = str.split(",").filter { it.isNotBlank() }.associate { e -> e.substringBefore(':').toInt() to e.substringAfter(':').toInt() } }
        }

        // Crash report from the previous session. If it happened with the Vulkan GPU renderer, fall back to software.
        val crashFile = File(appContext.filesDir, "crash.txt")
        if (crashFile.exists()) {
            val text = runCatching { crashFile.readText() }.getOrDefault("")
            crashFile.delete()
            if (text.isNotBlank()) {
                val gpu = text.contains("renderer=4")
                if (gpu) { renderer = 0; prefs.edit().putInt("renderer", 0).apply() }
                lastCrash = (if (gpu) "GBSat closed while using the Vulkan GPU (beta) renderer, so the renderer was switched back to Software.\n\n" else "") +
                    "GBSat ${appVersion()} crashed last time. Please send this report:\n\n" + text
            }
        }
        NativeBridge.nativeSetCrashFile(crashFile.absolutePath)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { crashFile.writeText("renderer=$renderer\nkotlin exception in ${t.name}:\n" + android.util.Log.getStackTraceString(e)) }
            previous?.uncaughtException(t, e)
        }

        val bup = File(appContext.filesDir, "saves").apply { mkdirs() }.resolve("internal_backup.bin")
        NativeBridge.nativeInit(bup.absolutePath, File(appContext.filesDir, "saves/smpc.bin").absolutePath)
        applyOptions()
        RetroAchievements.init(appContext, appVersion().substringBefore(" "))
        if (biosFile.exists()) iplLoaded = NativeBridge.nativeLoadIpl(biosFile.absolutePath) == null
    }

    fun appVersion(): String =
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull() ?: "?"

    fun applyOptions() {
        NativeBridge.nativeSetPadType(analogPad)
        NativeBridge.nativeSetSH2Cache(sh2Cache)
        NativeBridge.nativeSetVideoStandard(pal)
        NativeBridge.nativeSetThreadedVdp(threadedVdp)
        NativeBridge.nativeSetCartridge(cartridge)
        NativeBridge.nativeSetRenderer(renderer)
        NativeBridge.nativeSetDisplayOptions(filter, shader)
        if (fastBoot) NativeBridge.nativeSetFastBoot(true)
    }

    fun savePrefs() = prefs.edit()
        .putString("gamesDir", gamesDir).putBoolean("analogPad", analogPad).putBoolean("sh2Cache", sh2Cache)
        .putBoolean("pal", pal).putBoolean("threadedVdp", threadedVdp).putBoolean("preloadDisc", preloadDisc)
        .putInt("cartridge", cartridge).putBoolean("touch", showTouchControls).putInt("aspect", aspect).putBoolean("showFps", showFps)
        .putBoolean("fastBoot", fastBoot).putInt("renderer", renderer).putInt("filter", filter).putInt("shader", shader)
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

    suspend fun launch(path: String): String? = withContext(Dispatchers.IO) {
        if (!iplLoaded) return@withContext "Load a Saturn BIOS (IPL ROM) in Settings first."
        val err = NativeBridge.nativeLoadDisc(path, preloadDisc)
        if (err == null) {
            currentGame = path; running = true; loadCheats(); pause(false)
        }
        err
    }

    fun pause(p: Boolean) { paused = p; NativeBridge.nativeSetPaused(p) }
    fun toggleFastForward() { fastForward = !fastForward; NativeBridge.nativeSetFastForward(fastForward) }
    fun reset(hard: Boolean) = NativeBridge.nativeReset(hard)
    fun toggleTray() { trayOpen = !trayOpen; NativeBridge.nativeSetTrayOpen(trayOpen) }
    fun quitGame() {
        pause(true); running = false; currentGame = null; Hotkeys.reset(); cheats = emptyList()
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
