package com.juscelino.sonivell.data

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.exoplayer.MetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
data class LyricLine(val timeMs: Long, val text: String)

@Immutable
data class Lyrics(
    val lines: List<LyricLine>,
    /** true quando as linhas têm tempo (LRC); false para letra corrida. */
    val synced: Boolean,
    val fromFile: Boolean,
) {
    /** Índice da linha que está tocando em [positionMs] (já com o ajuste de sincronia). */
    fun lineAt(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }
}

/**
 * Letras das músicas, sem internet: primeiro um arquivo .lrc com o mesmo nome da música
 * (numa pasta que o usuário autoriza), depois a letra embutida no próprio arquivo de áudio.
 */
@OptIn(UnstableApi::class)
class LyricsRepository(private val context: Context, private val prefs: SharedPreferences) {

    private val cache = HashMap<Long, Lyrics?>()
    private val lock = Mutex()
    private var lrcIndex: Map<String, Uri>? = null

    val lrcFolder: Uri? get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)

    fun setLrcFolder(uri: Uri?) {
        prefs.edit().apply { if (uri == null) remove(KEY_FOLDER) else putString(KEY_FOLDER, uri.toString()) }.apply()
        lrcIndex = null
        cache.clear()
    }

    /** Ajuste de sincronia por música, em ms (positivo = letra aparece mais tarde). */
    fun offsetFor(trackId: Long): Long = prefs.getLong("$KEY_OFFSET$trackId", 0L)

    fun setOffset(trackId: Long, offsetMs: Long) {
        prefs.edit().putLong("$KEY_OFFSET$trackId", offsetMs).apply()
    }

    suspend fun load(track: Track): Lyrics? = lock.withLock {
        if (cache.containsKey(track.id)) return@withLock cache[track.id]
        val result = withContext(Dispatchers.IO) {
            runCatching { fromLrcFolder(track) ?: embedded(track) }
                .onFailure { Log.w(TAG, "Falha ao ler a letra de ${track.title}", it) }
                .getOrNull()
        }
        cache[track.id] = result
        result
    }

    // ---- Letra embutida no arquivo ----

    private suspend fun embedded(track: Track): Lyrics? {
        val groups = MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri(track.uri)).await()
        val metadata = mutableListOf<Metadata>()
        for (g in 0 until groups.length) {
            val group = groups[g]
            for (f in 0 until group.length) group.getFormat(f).metadata?.let(metadata::add)
        }
        return TagReader.lyrics(metadata)?.let { parse(it, fromFile = false) }
    }

    // ---- Arquivo .lrc numa pasta autorizada ----

    private fun fromLrcFolder(track: Track): Lyrics? {
        val index = lrcIndex ?: buildIndex().also { lrcIndex = it }
        if (index.isEmpty()) return null
        val stem = track.fileName?.substringBeforeLast('.')?.lowercase()
        val uri = stem?.let(index::get)
            ?: index["${track.artist} - ${track.title}".lowercase()]
            ?: index[track.title.lowercase()]
            ?: return null
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: return null
        return parse(text, fromFile = true)
    }

    /** Lista os .lrc da pasta (e subpastas, até 4 níveis): nome sem extensão → arquivo. */
    private fun buildIndex(): Map<String, Uri> {
        val tree = lrcFolder ?: return emptyMap()
        val out = HashMap<String, Uri>()
        val resolver = context.contentResolver
        fun walk(documentId: String, depth: Int) {
            if (depth > 4) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            runCatching {
                resolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        val name = c.getString(1).orEmpty()
                        val mime = c.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) walk(id, depth + 1)
                        else if (name.endsWith(".lrc", ignoreCase = true)) {
                            out[name.substringBeforeLast('.').lowercase()] =
                                DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        }
                    }
                }
            }.onFailure { Log.w(TAG, "Pasta de letras inacessível", it) }
        }
        runCatching { walk(DocumentsContract.getTreeDocumentId(tree), 0) }
        return out
    }

    companion object {
        private const val TAG = "SonivellLyrics"
        private const val KEY_FOLDER = "lyrics_folder"
        private const val KEY_OFFSET = "lyrics_offset_"

        private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
        private val TAG_LINE = Regex("""^\[[a-zA-Z]+:.*]$""")

        /** Lê LRC (com tempos) ou texto corrido. Várias marcas de tempo numa linha viram várias linhas. */
        fun parse(text: String, fromFile: Boolean): Lyrics? {
            val clean = text.replace("\r\n", "\n").replace('\r', '\n')
            val fileOffset = OFFSET.find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val timed = mutableListOf<LyricLine>()
            clean.lineSequence().forEach { raw ->
                val line = raw.trim()
                val stamps = TIME.findAll(line).toList()
                if (stamps.isEmpty()) return@forEach
                val words = line.substring(stamps.last().range.last + 1).trim()
                stamps.forEach { m ->
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3]
                    val ms = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    // No LRC, [offset] positivo adianta a letra.
                    timed += LyricLine((min * 60_000 + sec * 1_000 + ms - fileOffset).coerceAtLeast(0), words)
                }
            }
            if (timed.isNotEmpty()) {
                return Lyrics(timed.sortedBy { it.timeMs }, synced = true, fromFile = fromFile)
            }
            val plain = clean.lines()
                .filterNot { TAG_LINE.matches(it.trim()) }
                .joinToString("\n")
                .trim()
            if (plain.isEmpty()) return null
            return Lyrics(plain.lines().map { LyricLine(0, it) }, synced = false, fromFile = fromFile)
        }
    }
}
