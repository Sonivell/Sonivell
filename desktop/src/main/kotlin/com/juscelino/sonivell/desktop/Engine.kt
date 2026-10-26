package com.juscelino.sonivell.desktop

import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * Motor de áudio do PC: o ffmpeg decodifica qualquer formato para PCM e o Java Sound toca.
 * Assim FLAC, MP3, OPUS, M4A etc. funcionam do mesmo jeito, sem bibliotecas nativas extras.
 */
class AudioEngine(
    private val onEnded: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val format = AudioFormat(44100f, 16, 2, true, false)
    private val lock = Any()
    private var line: SourceDataLine? = null
    private var process: Process? = null

    @Volatile private var generation = 0
    @Volatile private var paused = false
    @Volatile private var gain = 0.8f
    @Volatile private var startMs = 0L
    @Volatile private var baseFrames = 0L
    @Volatile private var active = false

    val isActive: Boolean get() = active

    var volume: Float
        get() = gain
        set(value) { gain = value.coerceIn(0f, 1f) }

    fun positionMs(): Long {
        val l = line ?: return startMs
        if (!active) return startMs
        val frames = (l.longFramePosition - baseFrames).coerceAtLeast(0)
        return startMs + frames * 1000L / 44100L
    }

    private fun ensureLine(): SourceDataLine? {
        synchronized(lock) {
            line?.let { return it }
            return try {
                val info = DataLine.Info(SourceDataLine::class.java, format)
                val opened = AudioSystem.getLine(info) as SourceDataLine
                opened.open(format, 44100 * 4 / 2)
                line = opened
                opened
            } catch (e: Exception) {
                onError(e.message ?: e.javaClass.simpleName)
                null
            }
        }
    }

    fun play(path: String, fromMs: Long) {
        val l = ensureLine() ?: return
        val gen = synchronized(lock) {
            generation++
            process?.destroy()
            l.stop()
            l.flush()
            paused = false
            startMs = fromMs
            baseFrames = l.longFramePosition
            active = true
            l.start()
            generation
        }
        val worker = Thread({ pump(gen, path, fromMs, l) }, "sonivell-audio")
        worker.isDaemon = true
        worker.start()
    }

    private fun pump(gen: Int, path: String, fromMs: Long, l: SourceDataLine) {
        val cmd = listOf(
            "ffmpeg", "-nostdin", "-loglevel", "error", "-ss", "%.3f".format(java.util.Locale.ROOT, fromMs / 1000.0),
            "-i", path, "-vn", "-f", "s16le", "-ac", "2", "-ar", "44100", "-",
        )
        val proc = try {
            ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (e: Exception) {
            onError("ffmpeg: " + (e.message ?: "")); return
        }
        synchronized(lock) { if (gen == generation) process = proc else { proc.destroy(); return } }
        val input = proc.inputStream
        val buf = ByteArray(16384)
        var natural = false
        try {
            while (gen == generation) {
                val n = input.read(buf)
                if (n < 0) { natural = true; break }
                if (n == 0) continue
                val g = gain * gain
                if (g < 0.999f) {
                    var i = 0
                    while (i + 1 < n) {
                        val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort().toInt()
                        val v = (s * g).toInt().coerceIn(-32768, 32767)
                        buf[i] = (v and 0xff).toByte()
                        buf[i + 1] = ((v shr 8) and 0xff).toByte()
                        i += 2
                    }
                }
                while (paused && gen == generation) Thread.sleep(40)
                if (gen != generation) break
                var off = 0
                while (off < n && gen == generation) off += l.write(buf, off, n - off)
            }
            if (natural && gen == generation) {
                l.drain()
                if (gen == generation) { active = false; onEnded() }
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            if (gen == generation) onError(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { proc.destroy() }
        }
    }

    fun pause() {
        paused = true
        line?.stop()
    }

    fun resume() {
        paused = false
        line?.start()
    }

    fun stop() {
        synchronized(lock) {
            generation++
            process?.destroy()
            line?.stop()
            line?.flush()
            active = false
            paused = false
        }
    }

    fun close() {
        stop()
        synchronized(lock) { line?.close(); line = null }
    }

    companion object {
        fun available(): Boolean = runCatching {
            ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start().let { p ->
                p.inputStream.readBytes(); p.waitFor() == 0
            }
        }.getOrDefault(false)
    }
}

/** Capas: tenta a arte embutida (via ffmpeg) ou uma imagem "cover/folder" na pasta; guarda em cache de disco. */
object ArtLoader {
    private val dir get() = File(Dirs.cache, "art").also { it.mkdirs() }

    fun bytesFor(track: Track): ByteArray? {
        val key = fold(track.albumKey).hashCode().toString(16) + "-" + (if (track.album.isBlank()) track.id.toString(16) else "")
        val cached = File(dir, "$key.jpg")
        val none = File(dir, "$key.none")
        if (cached.exists()) return cached.readBytes()
        if (none.exists()) return null
        val folderImage = File(track.folder).listFiles { f ->
            f.isFile && f.nameWithoutExtension.lowercase() in setOf("cover", "folder", "front", "album", "artwork") &&
                f.extension.lowercase() in setOf("jpg", "jpeg", "png")
        }?.firstOrNull()
        val bytes = folderImage?.readBytes() ?: runCatching {
            val p = ProcessBuilder(
                "ffmpeg", "-nostdin", "-loglevel", "error", "-i", track.path, "-an", "-map", "0:v:0", "-frames:v", "1",
                "-vf", "scale=512:-2", "-f", "image2", "-c:v", "mjpeg", "-",
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val data = p.inputStream.readBytes()
            p.waitFor()
            data.takeIf { it.size > 100 }
        }.getOrNull()
        if (bytes == null) runCatching { none.createNewFile() } else runCatching { cached.writeBytes(bytes) }
        return bytes
    }
}
