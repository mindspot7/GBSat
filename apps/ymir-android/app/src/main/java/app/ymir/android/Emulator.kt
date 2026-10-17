package app.ymir.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.view.KeyEvent
import java.io.File

/** Process-wide emulator controller: owns persistent settings and merges all input sources into one pad. */
object Emulator {
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    var iplLoaded by mutableStateOf(false); private set
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
    var aspect by mutableStateOf(0) // 0 = 4:3, 1 = stretch, 2 = native pixels

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
    private val statesDir get() = File(appContext.filesDir, "states")
    private fun stateFile(slot: Int): File? =
        NativeBridge.nativeGetDiscHash()?.let { File(statesDir, "$it/$slot.savestate") }

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

    val biosFile get() = File(appContext.filesDir, "bios/ipl.bin")

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("ymir", Context.MODE_PRIVATE)
        gamesDir = prefs.getString("gamesDir", "") ?: ""
        analogPad = prefs.getBoolean("analogPad", false)
        sh2Cache = prefs.getBoolean("sh2Cache", false)
        pal = prefs.getBoolean("pal", false)
        threadedVdp = prefs.getBoolean("threadedVdp", true)
        preloadDisc = prefs.getBoolean("preloadDisc", false)
        cartridge = prefs.getInt("cartridge", 0)
        showTouchControls = prefs.getBoolean("touch", true)
        aspect = prefs.getInt("aspect", 0)
        prefs.getString("keyMap", null)?.let { str ->
            runCatching { keyMap = str.split(",").filter { it.isNotBlank() }.associate { e -> e.substringBefore(':').toInt() to e.substringAfter(':').toInt() } }
        }

        val bup = File(appContext.filesDir, "saves").apply { mkdirs() }.resolve("internal_backup.bin")
        NativeBridge.nativeInit(bup.absolutePath)
        applyOptions()
        if (biosFile.exists()) iplLoaded = NativeBridge.nativeLoadIpl(biosFile.absolutePath) == null
    }

    fun applyOptions() {
        NativeBridge.nativeSetPadType(analogPad)
        NativeBridge.nativeSetSH2Cache(sh2Cache)
        NativeBridge.nativeSetVideoStandard(pal)
        NativeBridge.nativeSetThreadedVdp(threadedVdp)
        NativeBridge.nativeSetCartridge(cartridge)
    }

    fun savePrefs() = prefs.edit()
        .putString("gamesDir", gamesDir).putBoolean("analogPad", analogPad).putBoolean("sh2Cache", sh2Cache)
        .putBoolean("pal", pal).putBoolean("threadedVdp", threadedVdp).putBoolean("preloadDisc", preloadDisc)
        .putInt("cartridge", cartridge).putBoolean("touch", showTouchControls).putInt("aspect", aspect).apply()

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
            currentGame = path; running = true; pause(false)
        }
        err
    }

    fun pause(p: Boolean) { paused = p; NativeBridge.nativeSetPaused(p) }
    fun toggleFastForward() { fastForward = !fastForward; NativeBridge.nativeSetFastForward(fastForward) }
    fun reset(hard: Boolean) = NativeBridge.nativeReset(hard)
    fun toggleTray() { trayOpen = !trayOpen; NativeBridge.nativeSetTrayOpen(trayOpen) }
    fun quitGame() {
        pause(true); running = false; currentGame = null
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
