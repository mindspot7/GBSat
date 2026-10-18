package app.gbsat.android

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/** Helpers for turning SAF tree URIs into real paths (the core's loaders need file paths, e.g. for CUE+BIN sets). */
object Storage {
    fun hasAllFilesAccess() =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    fun treeUriToPath(uri: Uri): String? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val (volume, rel) = docId.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val root = if (volume.equals("primary", true)) "/storage/emulated/0" else "/storage/$volume"
        return if (rel.isEmpty()) root else "$root/$rel"
    }

    private val discExt = setOf("cue", "chd", "iso", "mds", "ccd")

    data class Game(val title: String, val path: String)

    fun scan(dir: String, maxDepth: Int = 3): List<Game> {
        val out = mutableListOf<Game>()
        fun walk(f: File, depth: Int) {
            val children = f.listFiles() ?: return
            for (c in children) {
                if (c.isDirectory && depth < maxDepth) walk(c, depth + 1)
                else if (c.isFile && c.extension.lowercase() in discExt) out += Game(c.nameWithoutExtension, c.absolutePath)
            }
        }
        walk(File(dir), 0)
        return out.sortedBy { it.title.lowercase() }
    }
}
