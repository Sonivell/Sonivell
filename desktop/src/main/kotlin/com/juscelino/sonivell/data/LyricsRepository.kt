package com.juscelino.sonivell.data

import androidx.compose.runtime.Immutable
import com.juscelino.sonivell.compat.Context
import com.juscelino.sonivell.compat.SharedPreferences
import com.juscelino.sonivell.compat.Uri
import com.juscelino.sonivell.desktop.Scanner
import com.juscelino.sonivell.desktop.withDeadline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Trecho de letra encontrado na busca; [timeMs] só existe em letra sincronizada. */
@Immutable
data class LyricHit(val track: Track, val line: String, val timeMs: Long?)

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
 * Letras das músicas, sem internet: primeiro um arquivo .lrc com o mesmo nome da música (ao lado
 * dela ou na pasta de letras escolhida), depois a letra embutida no próprio arquivo de áudio.
 */
class LyricsRepository(@Suppress("unused") private val context: Context, private val prefs: SharedPreferences) {

    private val cache = HashMap<Long, Lyrics?>()
    private val lock = Mutex()
    private var lrcIndex: Map<String, File>? = null
    /** Texto das letras da pasta, lido uma vez para a busca: nome do arquivo → letra. */
    private var textIndex: Map<String, Lyrics>? = null

    val lrcFolder: Uri? get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)

    fun setLrcFolder(uri: Uri?) {
        prefs.edit().apply { if (uri == null) remove(KEY_FOLDER) else putString(KEY_FOLDER, uri.toString()) }.apply()
        lrcIndex = null
        textIndex = null
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
            runCatching { besideTrack(track) ?: fromLrcFolder(track) ?: embedded(track) }
                .onFailure { System.err.println("Falha ao ler a letra de ${track.title}: $it") }
                .getOrNull()
        }
        cache[track.id] = result
        result
    }

    // ---- Letra embutida no arquivo (lida pelo ffprobe) ----

    private fun embedded(track: Track): Lyrics? {
        // A varredura já sabe se o arquivo tem letra: sem ela, nem abre o ffprobe.
        if (Scanner.extras[track.path]?.hasLyrics == false) return null
        val p = ProcessBuilder(
            "ffprobe", "-v", "error", "-show_entries", "format_tags:stream_tags", "-of", "json", track.path,
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val json = withDeadline(p, 15) { p.inputStream.bufferedReader(Charsets.UTF_8).readText() }
        p.waitFor(5, TimeUnit.SECONDS)
        val key = Regex(""""(lyrics[^"]*|unsyncedlyrics|uslt|©lyr)"\s*:\s*"((?:[^"\\]|\\.)*)"""", RegexOption.IGNORE_CASE)
        val raw = key.find(json)?.groupValues?.get(2) ?: return null
        return parse(unjson(raw), fromFile = false)
    }

    private fun unjson(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                    'u' -> if (i + 5 < s.length) { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 } else sb.append(n)
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    // ---- Arquivo .lrc ao lado da música (comum no PC) ----

    private fun besideTrack(track: Track): Lyrics? {
        val audio = File(track.path)
        val stem = audio.nameWithoutExtension
        val file = listOf("$stem.lrc", "$stem.LRC", "$stem.txt").map { File(audio.parentFile, it) }.firstOrNull { it.isFile } ?: return null
        return readFile(file)?.let { parse(it, fromFile = true) }
    }

    private fun readFile(file: File): String? = runCatching {
        file.inputStream().use { BoundedRead.bytes(it, BoundedRead.LYRICS_MAX)?.decodeToString() }
    }.getOrNull()

    // ---- Arquivo .lrc numa pasta autorizada ----

    private fun fromLrcFolder(track: Track): Lyrics? {
        val index = lrcIndex ?: buildIndex().also { lrcIndex = it }
        if (index.isEmpty()) return null
        val stem = track.fileName?.substringBeforeLast('.')?.lowercase()
        val uri = stem?.let(index::get)
            ?: index["${track.artist} - ${track.title}".lowercase()]
            ?: index[track.title.lowercase()]
            ?: return null
        val text = readFile(uri) ?: return null
        return parse(text, fromFile = true)
    }

    /**
     * Busca um trecho nas letras: as da pasta escolhida e as embutidas que já foram abertas.
     * Ignora maiúsculas e acentos. Cada faixa aparece uma vez, com a primeira linha que bate.
     */
    suspend fun search(query: String, tracks: List<Track>, limit: Int = 30): List<LyricHit> = withContext(Dispatchers.IO) {
        val q = fold(query.trim())
        if (q.length < 3) return@withContext emptyList()
        val texts = lock.withLock {
            textIndex ?: run {
                val index = lrcIndex ?: buildIndex().also { lrcIndex = it }
                index.mapNotNull { (stem, uri) ->
                    runCatching {
                        readFile(uri)?.let { parse(it, fromFile = true) }?.let { stem to it }
                    }.getOrNull()
                }.toMap().also { textIndex = it }
            }
        }
        val embedded = lock.withLock { cache.filterValues { it != null && !it.fromFile }.mapValues { it.value!! } }
        val out = ArrayList<LyricHit>()
        for (t in tracks) {
            val lyrics = embedded[t.id]
                ?: t.fileName?.substringBeforeLast('.')?.lowercase()?.let(texts::get)
                ?: texts["${t.artist} - ${t.title}".lowercase()]
                ?: texts[t.title.lowercase()]
                ?: continue
            val line = lyrics.lines.firstOrNull { fold(it.text).contains(q) } ?: continue
            out += LyricHit(t, line.text, if (lyrics.synced) line.timeMs else null)
            if (out.size >= limit) break
        }
        out
    }

    private fun fold(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(DIACRITICS, "")

    /** Lista os .lrc da pasta (e subpastas, até 4 níveis): nome sem extensão → arquivo. */
    private fun buildIndex(): Map<String, File> {
        val root = lrcFolder?.let { File(it.path) }?.takeIf { it.isDirectory } ?: return emptyMap()
        val out = HashMap<String, File>()
        runCatching {
            root.walkTopDown().maxDepth(5).filter { it.isFile }.forEach { f ->
                if (f.name.endsWith(".lrc", ignoreCase = true) || f.name.endsWith(".txt", ignoreCase = true)) {
                    out[f.name.substringBeforeLast('.').lowercase()] = f
                }
            }
        }
        return out
    }

    companion object {
        private val DIACRITICS = Regex("\\p{Mn}+")
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
