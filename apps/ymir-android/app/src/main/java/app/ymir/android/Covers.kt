package app.ymir.android

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Box art for the library. Order: user-chosen image -> cached download -> libretro-thumbnails
 * (thumbnails.libretro.com, matched by the disc image's file name) -> placeholder.
 */
object Covers {
    private lateinit var dir: File
    private val memory = LruCache<String, ImageBitmap>(64)

    fun init(context: Context) {
        dir = File(context.filesDir, "covers").apply { mkdirs(); File(this, "custom").mkdirs() }
    }

    private fun safe(title: String) = title.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
    private fun custom(title: String) = File(dir, "custom/${safe(title)}.img")
    private fun cached(title: String) = File(dir, "${safe(title)}.png")
    private fun miss(title: String) = File(dir, "${safe(title)}.miss")

    private fun decode(f: File): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 600) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }

    private fun download(name: String, to: File): Boolean = runCatching {
        val url = URL("https://thumbnails.libretro.com/Sega%20-%20Saturn/Named_Boxarts/" + Uri.encode(safe(name) + ".png"))
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        if (c.responseCode != 200) return@runCatching false
        val tmp = File(to.path + ".tmp")
        c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        tmp.renameTo(to)
    }.getOrDefault(false)

    private const val BASE = "https://thumbnails.libretro.com/Sega%20-%20Saturn/Named_Boxarts/"
    @Volatile private var index: List<String>? = null

    private fun norm(t: String) = t.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ").lowercase()
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    // Lists the thumbnail server's file names once, so titles can be matched even when the disc image name differs.
    private fun loadIndex(): List<String> {
        index?.let { return it }
        val list = runCatching {
            val c = URL(BASE).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 20000
            if (c.responseCode != 200) return@runCatching emptyList<String>()
            val html = c.inputStream.bufferedReader().readText()
            Regex("href=\"([^\"]+\\.png)\"").findAll(html).map { Uri.decode(it.groupValues[1]).removeSuffix(".png") }.toList()
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) index = list
        return list
    }

    private fun fuzzy(title: String): String? {
        val want = norm(title)
        if (want.isEmpty()) return null
        val cands = loadIndex().filter { norm(it) == want }
        if (cands.isEmpty()) return null
        // Prefer USA, then Europe, then anything else.
        return cands.firstOrNull { "(USA" in it } ?: cands.firstOrNull { "(Europe" in it } ?: cands.first()
    }

    suspend fun load(title: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memory.get(title)?.let { return@withContext it }
        val img = custom(title).takeIf { it.exists() }?.let(::decode)
            ?: cached(title).takeIf { it.exists() }?.let(::decode)
            ?: run {
                if (miss(title).exists()) return@run null
                val stripped = title.replace(Regex("\\s*\\((Disc|Disk)\\s*\\d+\\)"), "").trim()
                val ok = download(title, cached(title)) || (stripped != title && download(stripped, cached(title))) ||
                    (fuzzy(title)?.let { download(it, cached(title)) } ?: false)
                if (ok) decode(cached(title)) else { miss(title).writeText("1"); null }
            }
        if (img != null) memory.put(title, img)
        img
    }

    suspend fun setCustom(context: Context, title: String, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { i -> custom(title).outputStream().use { o -> i.copyTo(o) } }
        memory.remove(title)
    }

    suspend fun clearCustom(title: String) = withContext(Dispatchers.IO) {
        custom(title).delete(); cached(title).delete(); miss(title).delete(); memory.remove(title)
    }
}
