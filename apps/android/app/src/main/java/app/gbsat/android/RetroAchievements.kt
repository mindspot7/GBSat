package app.gbsat.android

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** RetroAchievements: login, hardcore mode, unlock notifications and the achievement list (rcheevos in native code). */
object RetroAchievements {
    data class Notification(val kind: String, val title: String, val text: String, val points: Int, val badgeUrl: String)

    data class Achievement(
        val id: Long, val title: String, val description: String, val points: Int, val unlocked: Boolean,
        val badgeUrl: String, val lockedBadgeUrl: String, val progress: String, val bucket: String,
    )

    data class GameSummary(
        val title: String, val unlocked: Int, val total: Int, val pointsUnlocked: Int, val pointsTotal: Int,
        val badgeUrl: String, val richPresence: String, val hardcore: Boolean,
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var cacheDir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val http = Executors.newFixedThreadPool(2)

    var enabled by mutableStateOf(false); private set
    var hardcore by mutableStateOf(false); private set
    var user by mutableStateOf<String?>(null); private set
    var score by mutableStateOf(0); private set
    var loginPending by mutableStateOf(false); private set
    var loginError by mutableStateOf<String?>(null); private set
    var gameTitle by mutableStateOf<String?>(null); private set

    /** Pending on-screen notifications (unlocks, game info, errors). The UI shows and removes them. */
    var notifications by mutableStateOf<List<Notification>>(emptyList())

    fun init(context: Context, version: String) {
        prefs = context.getSharedPreferences("retroachievements", Context.MODE_PRIVATE)
        cacheDir = File(context.cacheDir, "ra_badges").apply { mkdirs() }
        enabled = prefs.getBoolean("enabled", false)
        hardcore = prefs.getBoolean("hardcore", false)
        NativeBridge.nativeRaInit("GBSat/$version (Android ${Build.VERSION.RELEASE})")
        NativeBridge.nativeRaSetHardcore(enabled && hardcore)
        val savedUser = prefs.getString("user", null)
        val token = prefs.getString("token", null)
        if (enabled && savedUser != null && token != null) {
            loginPending = true
            NativeBridge.nativeRaLogin(savedUser, token, true)
        }
        scope.launch {
            while (true) {
                poll()
                delay(250)
            }
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean("enabled", on).apply()
        NativeBridge.nativeRaSetHardcore(on && hardcore)
        if (!on) {
            NativeBridge.nativeRaLogout()
            user = null
            gameTitle = null
        } else {
            val savedUser = prefs.getString("user", null)
            val token = prefs.getString("token", null)
            if (savedUser != null && token != null) {
                loginPending = true
                NativeBridge.nativeRaLogin(savedUser, token, true)
            }
        }
    }

    fun setHardcore(on: Boolean) {
        hardcore = on
        prefs.edit().putBoolean("hardcore", on).apply()
        NativeBridge.nativeRaSetHardcore(enabled && on)
    }

    fun login(username: String, password: String) {
        loginError = null
        loginPending = true
        prefs.edit().putString("user", username.trim()).remove("token").apply()
        NativeBridge.nativeRaLogin(username.trim(), password, false)
    }

    fun logout() {
        NativeBridge.nativeRaLogout()
        prefs.edit().remove("token").apply()
        user = null
        score = 0
        gameTitle = null
    }

    /** True when the emulator must block save state loading and cheats. */
    val hardcoreActive get() = enabled && hardcore && user != null

    private fun poll() {
        val text = NativeBridge.nativeRaPollEvents().toString(Charsets.UTF_8)
        if (text.isEmpty()) return
        val add = mutableListOf<Notification>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val p = line.split('\t')
            val kind = p.getOrElse(0) { "" }
            val a = p.getOrElse(1) { "" }
            val b = p.getOrElse(2) { "" }
            val n = p.getOrElse(3) { "0" }.toIntOrNull() ?: 0
            val url = p.getOrElse(4) { "" }
            when (kind) {
                "login" -> {
                    user = a; score = n; loginPending = false; loginError = null
                    prefs.edit().putString("token", b).apply()
                    add += Notification("info", "RetroAchievements", "Logged in as $a ($n points)", 0, url)
                }
                "loginfail" -> { loginPending = false; loginError = a; user = null }
                "game" -> { gameTitle = a; add += Notification("game", a, b, 0, url) }
                "gamefail" -> { gameTitle = null; add += Notification("info", "RetroAchievements", a, 0, "") }
                "unlocked" -> add += Notification("unlocked", a, b, n, url)
                "completed" -> add += Notification("completed", a, "All achievements earned!", 0, url)
                "challenge", "progress" -> add += Notification(kind, a, b, 0, url)
                "lbstart" -> add += Notification("leaderboard", "Leaderboard started", a, 0, "")
                "lbfail" -> add += Notification("leaderboard", "Leaderboard failed", a, 0, "")
                "lbsubmit" -> add += Notification("leaderboard", "Leaderboard submitted", "$a: $b", 0, "")
                "error" -> add += Notification("info", "RetroAchievements error", "$a: $b", 0, "")
                "info" -> add += Notification("info", a, b, 0, "")
                "reset" -> Emulator.reset(true)
            }
        }
        if (add.isNotEmpty()) notifications = notifications + add
    }

    fun gameUnloaded() { gameTitle = null }

    /** Achievement list for the running game (blocks briefly on the emulation thread). */
    suspend fun achievements(): Pair<GameSummary?, List<Achievement>> = withContext(Dispatchers.IO) {
        val text = NativeBridge.nativeRaAchievements().toString(Charsets.UTF_8)
        var summary: GameSummary? = null
        val list = mutableListOf<Achievement>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val p = line.split('\t')
            if (p[0] == "#") {
                summary = GameSummary(
                    p.getOrElse(1) { "" }, p.getOrElse(2) { "0" }.toInt(), p.getOrElse(3) { "0" }.toInt(),
                    p.getOrElse(4) { "0" }.toInt(), p.getOrElse(5) { "0" }.toInt(), p.getOrElse(6) { "" },
                    p.getOrElse(7) { "" }, p.getOrElse(8) { "0" } == "1",
                )
            } else if (p.size >= 9) {
                list += Achievement(p[0].toLongOrNull() ?: 0, p[1], p[2], p[3].toIntOrNull() ?: 0, p[4] == "1", p[5], p[6], p[7], p[8])
            }
        }
        summary to list
    }

    // ---- Badge images (cached on disk) ----
    private val memCache = HashMap<String, ImageBitmap>()

    suspend fun image(url: String): ImageBitmap? {
        if (url.isBlank()) return null
        memCache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            val name = MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val f = File(cacheDir, "$name.img")
            val bytes = if (f.exists()) f.readBytes() else runCatching {
                (URL(url).openConnection() as HttpURLConnection).run {
                    connectTimeout = 10000; readTimeout = 15000
                    setRequestProperty("User-Agent", "GBSat")
                    inputStream.use { it.readBytes() }
                }
            }.getOrNull()?.also { f.writeBytes(it) }
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }?.also { memCache[url] = it }
    }

    // ---- HTTP for rcheevos (called from native code) ----
    fun httpRequest(id: Int, url: String, post: String?, contentType: String?, userAgent: String) {
        http.execute {
            var status = -1
            var body = ByteArray(0)
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", userAgent)
                if (post != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", contentType ?: "application/x-www-form-urlencoded")
                    conn.outputStream.use { it.write(post.toByteArray()) }
                }
                status = conn.responseCode
                body = (if (status >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
            } catch (_: Exception) {
                status = -1
            }
            NativeBridge.nativeRaHttpResponse(id, status, body)
        }
    }
}
