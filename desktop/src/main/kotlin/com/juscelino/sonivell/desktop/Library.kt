package com.juscelino.sonivell.desktop

import com.juscelino.sonivell.R
import com.juscelino.sonivell.compat.Texts
import com.juscelino.sonivell.compat.Uri
import com.juscelino.sonivell.data.TextFix
import com.juscelino.sonivell.data.Track
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Prazo do ffprobe por arquivo: um arquivo que o trave é pulado, não prende a varredura. */
private const val PROBE_TIMEOUT_S = 20L

private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "wav", "wma", "alac", "ape", "wv", "aiff", "aif")

/**
 * O que a varredura lê de cada arquivo além do que vira [Track]: o ganho ReplayGain (para normalizar
 * o volume) e se há letra embutida. Fica num mapa à parte para o [Track] ser igual ao do Android.
 */
data class TrackExtras(val gainDb: Float?, val hasLyrics: Boolean)

/** Lê as etiquetas dos arquivos com o ffprobe (o mesmo pacote ffmpeg usado para tocar). */
object Scanner {
    fun toolsAvailable(): Boolean = runCatching {
        ProcessBuilder("ffprobe", "-version").redirectErrorStream(true).start().let { p ->
            p.inputStream.readBytes(); p.waitFor() == 0
        }
    }.getOrDefault(false)

    /** Extras por caminho, preenchidos na varredura. */
    val extras = ConcurrentHashMap<String, TrackExtras>()

    // v2: formato do Track do app (disco, artista do álbum, ReplayGain, letra embutida).
    private val cacheFile get() = File(Dirs.cache, "library-v2.tsv")

    private fun clean(s: Any?) = (s ?: "").toString().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private class Probed(val track: Track, val extras: TrackExtras)

    private fun load(): Map<String, Pair<Long, Probed>> {
        if (!cacheFile.exists()) return emptyMap()
        val out = HashMap<String, Pair<Long, Probed>>()
        runCatching {
            cacheFile.forEachLine { line ->
                val f = line.split('\t')
                if (f.size < 18) return@forEachLine
                val path = f[0]
                fun s(i: Int) = f[i].ifEmpty { null }
                val probed = build(
                    path = path, title = s(2), artist = s(3), album = s(4), albumArtist = s(5), genre = s(6),
                    year = f[7].toIntOrNull(), trackNo = f[8].toIntOrNull(), disc = f[9].toIntOrNull(),
                    durationMs = f[10].toLongOrNull() ?: 0L, mime = s(11), size = f[12].toLongOrNull(),
                    modifiedMs = f[1].toLongOrNull() ?: 0L, addedMs = f[13].toLongOrNull() ?: 0L,
                    gainDb = f[14].toFloatOrNull(), hasLyrics = f[15] == "1",
                )
                out[path] = (f[1].toLongOrNull() ?: 0L) to probed
            }
        }
        return out
    }

    private fun save(items: List<Pair<File, Probed>>) {
        runCatching {
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.bufferedWriter().use { w ->
                for ((file, p) in items) {
                    val t = p.track
                    w.write(
                        listOf(
                            t.path, file.lastModified(), t.rawTitle, t.rawArtist, t.rawAlbum, t.albumArtist, t.genre,
                            t.year ?: "", t.trackNumber ?: "", t.discNumber ?: "", t.durationMs, t.mimeType, t.sizeBytes ?: "",
                            t.dateAdded * 1000, p.extras.gainDb ?: "", if (p.extras.hasLyrics) 1 else 0, "", "",
                        ).joinToString("\t") { clean(it) },
                    )
                    w.newLine()
                }
            }
            tmp.renameTo(cacheFile)
        }
    }

    /** Varre as pastas e devolve as faixas. [onProgress] recebe (feitas, total) enquanto lê arquivos novos. */
    fun scan(roots: List<File>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Track> {
        val files = ArrayList<File>()
        val seen = HashSet<String>()
        for (root in roots) {
            if (!root.isDirectory) continue
            runCatching {
                Files.walk(root.toPath()).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.fileName.toString().substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
                        .forEach { p -> val f = p.toFile(); if (seen.add(f.path)) files.add(f) }
                }
            }
        }
        val cache = load()
        val result = ConcurrentHashMap<String, Probed>()
        val pending = ArrayList<File>()
        for (f in files) {
            val hit = cache[f.path]
            if (hit != null && hit.first == f.lastModified()) result[f.path] = hit.second else pending.add(f)
        }
        if (pending.isNotEmpty()) {
            val pool = Executors.newFixedThreadPool(minOf(6, Runtime.getRuntime().availableProcessors().coerceAtLeast(2)))
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            for (f in pending) pool.execute {
                probe(f)?.let { result[f.path] = it }
                onProgress(done.incrementAndGet(), pending.size)
            }
            pool.shutdown()
            pool.awaitTermination(1, TimeUnit.HOURS)
        }
        val items = files.mapNotNull { f -> result[f.path]?.let { f to it } }
        save(items)
        extras.clear()
        items.forEach { (f, p) -> extras[f.path] = p.extras }
        return items.map { it.second.track }
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) { 'n' -> sb.append('\n'); 't' -> sb.append(' '); else -> sb.append(n) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    /** Ganho em dB: REPLAYGAIN_TRACK_GAIN ("-6.50 dB"), ou R128_TRACK_GAIN do Opus (Q7.8, −23 LUFS → +5 dB). */
    private fun gainOf(tags: Map<String, String>): Float? {
        tags["replaygain_track_gain"]?.let { v -> Regex("""[-+]?\d+(\.\d+)?""").find(v)?.value?.toFloatOrNull()?.let { return it } }
        tags["r128_track_gain"]?.toIntOrNull()?.let { return it / 256f + 5f }
        return null
    }

    private fun mimeOf(codec: String, ext: String): String = when (codec) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "opus" -> "audio/opus"
        "vorbis" -> "audio/ogg"
        "aac" -> "audio/aac"
        "alac" -> "audio/alac"
        "wmav1", "wmav2" -> "audio/x-ms-wma"
        else -> if (codec.startsWith("pcm")) "audio/wav" else "audio/$ext"
    }

    private fun probe(file: File): Probed? = runCatching {
        val p = ProcessBuilder(
            "ffprobe", "-v", "error", "-show_entries",
            "format=duration:format_tags:stream=codec_type,codec_name:stream_tags",
            "-of", "flat", file.path,
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        // Com prazo e sem ler o stderr depois do stdout (que podiam travar um esperando o outro).
        val text = withDeadline(p, PROBE_TIMEOUT_S) { p.inputStream.bufferedReader(Charsets.UTF_8).readText() }
        if (!p.waitFor(PROBE_TIMEOUT_S, TimeUnit.SECONDS)) p.destroyForcibly()
        val tags = HashMap<String, String>()
        var duration = 0.0
        val codecs = HashMap<String, String>()
        val types = HashMap<String, String>()
        val line = Regex("""^([^=]+)=(?:"(.*)"|(.*))$""")
        for (raw in text.lines()) {
            val m = line.find(raw) ?: continue
            val key = m.groupValues[1]
            val value = unescape(m.groupValues[2].ifEmpty { m.groupValues[3] })
            when {
                key == "format.duration" -> duration = value.toDoubleOrNull() ?: 0.0
                key.startsWith("format.tags.") -> tags[key.removePrefix("format.tags.").lowercase()] = value.trim()
                // Etiquetas do fluxo (Ogg/Opus guardam os comentários Vorbis no fluxo de áudio).
                key.startsWith("streams.stream.") && ".tags." in key ->
                    tags.putIfAbsent(key.substringAfter(".tags.").lowercase(), value.trim())
                key.endsWith(".codec_name") -> codecs[key.removeSuffix(".codec_name")] = value
                key.endsWith(".codec_type") -> types[key.removeSuffix(".codec_type")] = value
            }
        }
        val audioStream = types.entries.firstOrNull { it.value == "audio" }?.key
        val codec = (audioStream?.let { codecs[it] } ?: file.extension).lowercase()
        fun tag(vararg names: String) = names.firstNotNullOfOrNull { n -> tags[n]?.takeIf { it.isNotBlank() } }
        val number = Regex("""\d+""")
        val hasLyrics = tags.keys.any { it == "lyrics" || it == "unsyncedlyrics" || it.startsWith("lyrics-") || it == "uslt" || it == "©lyr" }
        build(
            path = file.path,
            title = tag("title"),
            artist = tag("artist"),
            album = tag("album"),
            albumArtist = tag("album_artist", "albumartist", "album artist"),
            genre = tag("genre"),
            year = Regex("""\d{4}""").find(tag("date", "year", "originaldate").orEmpty())?.value?.toInt(),
            trackNo = number.find(tag("track", "tracknumber").orEmpty())?.value?.toIntOrNull(),
            disc = number.find(tag("disc", "discnumber").orEmpty())?.value?.toIntOrNull(),
            durationMs = (duration * 1000).toLong(),
            mime = mimeOf(codec, file.extension.lowercase()),
            size = file.length(),
            modifiedMs = file.lastModified(),
            addedMs = file.lastModified(),
            gainDb = gainOf(tags),
            hasLyrics = hasLyrics,
        )
    }.getOrNull()

    /** Monta o [Track] com as mesmas regras do MusicRepository do Android (tags vazias, codificação errada). */
    private fun build(
        path: String,
        title: String?,
        artist: String?,
        album: String?,
        albumArtist: String?,
        genre: String?,
        year: Int?,
        trackNo: Int?,
        disc: Int?,
        durationMs: Long,
        mime: String?,
        size: Long?,
        modifiedMs: Long,
        addedMs: Long,
        gainDb: Float?,
        hasLyrics: Boolean,
    ): Probed {
        val file = File(path)
        val rawTitle = TextFix.repair(title)
        val rawArtist = TextFix.repair(artist)
        val rawAlbum = TextFix.repair(album)
        val fixedAlbumArtist = TextFix.repair(albumArtist)
        val folder = file.parent.orEmpty()
        val albumName = rawAlbum ?: Texts.get(R.string.unknown_album)
        // Um álbum = mesmo nome e mesmo artista do álbum (ou a mesma pasta, nas coletâneas sem a tag).
        val albumKey = albumName.lowercase() + "\u0001" + (fixedAlbumArtist?.lowercase() ?: folder)
        return Probed(
            Track(
                id = trackIdOf(path),
                uri = Uri.fromFile(file),
                title = rawTitle ?: file.nameWithoutExtension,
                artist = rawArtist ?: fixedAlbumArtist ?: Texts.get(R.string.unknown_artist),
                album = albumName,
                albumId = trackIdOf(albumKey),
                durationMs = durationMs,
                mimeType = mime,
                folder = folder,
                dateAdded = addedMs / 1000,
                rawTitle = rawTitle,
                rawArtist = rawArtist,
                rawAlbum = rawAlbum,
                albumArtist = fixedAlbumArtist,
                genre = TextFix.repair(genre),
                year = year?.takeIf { it > 0 },
                trackNumber = trackNo?.takeIf { it > 0 },
                discNumber = disc?.takeIf { it > 0 },
                fileName = file.name,
                sizeBytes = size,
                dateModified = modifiedMs / 1000,
            ),
            TrackExtras(gainDb, hasLyrics),
        )
    }
}
