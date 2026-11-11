package com.juscelino.sonivell.desktop

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Prazo do ffprobe por arquivo: um arquivo que o trave é pulado, não prende a varredura. */
private const val PROBE_TIMEOUT_S = 20L

private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "aac", "wav", "wma", "alac", "ape", "wv", "aiff", "aif")

/** Lê as etiquetas dos arquivos com o ffprobe (o mesmo pacote ffmpeg usado para tocar). */
object Scanner {
    fun toolsAvailable(): Boolean = runCatching {
        ProcessBuilder("ffprobe", "-version").redirectErrorStream(true).start().let { p ->
            p.inputStream.readBytes(); p.waitFor() == 0
        }
    }.getOrDefault(false)

    private val cacheFile get() = File(Dirs.cache, "library-v1.tsv")

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun load(): Map<String, Pair<Long, Track>> {
        if (!cacheFile.exists()) return emptyMap()
        val out = HashMap<String, Pair<Long, Track>>()
        runCatching {
            cacheFile.forEachLine { line ->
                val f = line.split('\t')
                if (f.size < 14) return@forEachLine
                val path = f[0]
                out[path] = f[1].toLong() to Track(
                    id = trackIdOf(path), path = path, title = repairEncoding(f[3]), artist = repairEncoding(f[4]), albumArtist = repairEncoding(f[5]), album = repairEncoding(f[6]),
                    genre = repairEncoding(f[7]), year = f[8].toInt(), trackNo = f[9].toInt(), durationMs = f[10].toLong(),
                    format = f[11], bitrateKbps = f[12].toInt(), addedMs = f[13].toLong(),
                )
            }
        }
        return out
    }

    private fun save(tracks: List<Track>, mtimes: Map<String, Long>) {
        runCatching {
            cacheFile.bufferedWriter().use { w ->
                for (t in tracks) {
                    w.write(listOf(t.path, mtimes[t.path] ?: 0L, 0, t.title, t.artist, t.albumArtist, t.album, t.genre, t.year, t.trackNo, t.durationMs, t.format, t.bitrateKbps, t.addedMs).joinToString("\t") { clean(it.toString()) })
                    w.newLine()
                }
            }
        }
    }

    /** Varre as pastas e devolve as faixas. [onProgress] recebe (feitas, total) enquanto lê arquivos novos. */
    fun scan(roots: List<File>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Track> {
        val files = ArrayList<File>()
        for (root in roots) {
            if (!root.isDirectory) continue
            runCatching {
                Files.walk(root.toPath()).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.fileName.toString().substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
                        .forEach { files.add(it.toFile()) }
                }
            }
        }
        val cache = load()
        val mtimes = HashMap<String, Long>()
        val result = java.util.concurrent.ConcurrentHashMap<String, Track>()
        val pending = ArrayList<File>()
        for (f in files) {
            val m = f.lastModified()
            mtimes[f.path] = m
            val hit = cache[f.path]
            if (hit != null && hit.first == m) result[f.path] = hit.second else pending.add(f)
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
        val tracks = files.mapNotNull { result[it.path] }
        save(tracks, mtimes)
        return tracks
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) { 'n' -> sb.append(' '); 't' -> sb.append(' '); else -> sb.append(n) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun probe(file: File): Track? = runCatching {
        val p = ProcessBuilder(
            "ffprobe", "-v", "error", "-show_entries",
            "format=duration,bit_rate:format_tags:stream=codec_type,codec_name",
            "-of", "flat", file.path,
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        // Com prazo e sem ler o stderr depois do stdout (que podiam travar um esperando o outro).
        val text = withDeadline(p, PROBE_TIMEOUT_S) { p.inputStream.bufferedReader(Charsets.UTF_8).readText() }
        if (!p.waitFor(PROBE_TIMEOUT_S, TimeUnit.SECONDS)) p.destroyForcibly()
        val tags = HashMap<String, String>()
        var duration = 0.0
        var bitrate = 0
        val codecs = HashMap<String, String>()
        val types = HashMap<String, String>()
        val line = Regex("""^([^=]+)=(?:"(.*)"|(.*))$""")
        for (raw in text.lines()) {
            val m = line.find(raw) ?: continue
            val key = m.groupValues[1]
            val value = unescape(m.groupValues[2].ifEmpty { m.groupValues[3] })
            when {
                key == "format.duration" -> duration = value.toDoubleOrNull() ?: 0.0
                key == "format.bit_rate" -> bitrate = ((value.toLongOrNull() ?: 0L) / 1000).toInt()
                key.startsWith("format.tags.") -> tags[key.removePrefix("format.tags.").lowercase()] = repairEncoding(value.trim())
                key.endsWith(".codec_name") -> codecs[key.removeSuffix(".codec_name")] = value
                key.endsWith(".codec_type") -> types[key.removeSuffix(".codec_type")] = value
            }
        }
        val audioStream = types.entries.firstOrNull { it.value == "audio" }?.key
        val codec = (audioStream?.let { codecs[it] } ?: file.extension).lowercase()
        val format = when (codec) { "mp3" -> "MP3"; "flac" -> "FLAC"; "opus" -> "OPUS"; "vorbis" -> "OGG"; "aac" -> "AAC"; "alac" -> "ALAC"; "wmav2", "wmav1" -> "WMA"; else -> if (codec.startsWith("pcm")) "WAV" else codec.uppercase() }
        val artist = tags["artist"].orEmpty()
        val albumArtist = (tags["album_artist"] ?: tags["albumartist"]).orEmpty()
        val title = tags["title"]?.ifBlank { null } ?: file.nameWithoutExtension
        Track(
            id = trackIdOf(file.path), path = file.path, title = title,
            artist = artist.ifBlank { albumArtist }, albumArtist = albumArtist, album = tags["album"].orEmpty(),
            genre = tags["genre"].orEmpty(),
            year = Regex("""\d{4}""").find(tags["date"] ?: tags["year"] ?: "")?.value?.toInt() ?: 0,
            trackNo = Regex("""\d+""").find(tags["track"] ?: "")?.value?.toInt() ?: 0,
            durationMs = (duration * 1000).toLong(), format = format, bitrateKbps = bitrate, addedMs = file.lastModified(),
        )
    }.getOrNull()
}

/** A biblioteca já organizada: álbuns, artistas, gêneros e pastas, mais a busca. */
class Library(val tracks: List<Track>) {
    val byId: Map<Long, Track> = tracks.associateBy { it.id }

    val albums: List<Album> = tracks.filter { it.album.isNotBlank() }.groupBy { it.albumKey }.map { (key, list) ->
        val sorted = list.sortedWith(compareBy({ it.trackNo }, { it.title.lowercase() }))
        Album(key, sorted.first().album, sorted.first().albumArtist.ifBlank { sorted.first().artist }, sorted.maxOf { it.year }, sorted)
    }.sortedBy { sortKey(it.title) }

    val artists: List<Pair<String, List<Track>>> = tracks.filter { it.artist.isNotBlank() }.groupBy { it.artist }
        .toList().sortedBy { sortKey(it.first) }

    val genres: List<Pair<String, List<Track>>> = tracks.filter { it.genre.isNotBlank() }.groupBy { it.genre }
        .toList().sortedByDescending { it.second.size }

    val folders: List<Pair<String, List<Track>>> = tracks.groupBy { it.folder }.toList().sortedBy { sortKey(it.first.substringAfterLast('/')) }

    val recentlyAdded: List<Track> get() = tracks.sortedByDescending { it.addedMs }

    private val haystacks: Map<Long, String> = tracks.associate { it.id to fold("${it.title} ${it.artist} ${it.album} ${it.genre}") }

    fun search(query: String): List<Track> {
        val tokens = fold(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return tracks.filter { t -> val h = haystacks[t.id].orEmpty(); tokens.all { it in h } }
            .sortedWith(compareBy({ !fold(it.title).startsWith(tokens.first()) }, { it.title.lowercase() }))
    }

    fun searchAlbums(query: String): List<Album> {
        val tokens = fold(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return albums.filter { a -> val h = fold(a.title + " " + a.artist); tokens.all { it in h } }
    }

    fun searchArtists(query: String): List<Pair<String, List<Track>>> {
        val tokens = fold(query).split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return artists.filter { (name, _) -> val h = fold(name); tokens.all { it in h } }
    }
}
