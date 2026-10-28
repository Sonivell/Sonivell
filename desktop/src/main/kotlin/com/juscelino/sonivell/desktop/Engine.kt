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
            } catch (e: IllegalArgumentException) {
                // sem placa de som / servidor de áudio (PulseAudio, PipeWire) acessível
                onError("nenhuma saída de áudio encontrada. Verifique o som do sistema.")
                null
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

/**
 * O Java Sound toca pela saída "default" do ALSA. Em sistemas com PipeWire ou PulseAudio, ela só
 * chega ao servidor de som (e daí ao fone Bluetooth, à saída escolhida no painel de volume etc.)
 * se a ponte ALSA estiver instalada: pipewire-alsa ou libasound2-plugins. Sem ela, o som vai
 * direto para a placa (muitas vezes a HDMI) e o app "toca" em silêncio.
 */
object AudioRoute {
    enum class Server { PipeWire, PulseAudio }

    /** O servidor de som rodando sem a ponte ALSA, ou null se está tudo certo (ou não há servidor). */
    fun missingBridge(): Server? = runCatching {
        val runtime = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() } ?: return null
        val server = when {
            File(runtime, "pipewire-0").exists() -> Server.PipeWire
            File(runtime, "pulse/native").exists() -> Server.PulseAudio
            else -> return null // ALSA puro: tocar direto na placa é o certo
        }
        if (bridged()) null else server
    }.getOrNull()

    private fun mentionsServer(text: String) = text.contains("pipewire", true) || text.contains("pulse", true)

    private fun bridged(): Boolean {
        val dirs = listOf("/usr/share/alsa/alsa.conf.d", "/etc/alsa/conf.d")
        val confs = dirs.flatMap { File(it).listFiles()?.toList().orEmpty() }
            .filter { it.name.endsWith(".conf") && mentionsServer(it.name) }
        if (confs.isNotEmpty()) return true
        val home = System.getProperty("user.home")
        return listOf(File("/etc/asound.conf"), File(home, ".asoundrc")).any { f ->
            f.isFile && runCatching { mentionsServer(f.readText()) }.getOrDefault(false)
        }
    }
}

/**
 * Capas POR FAIXA: a arte embutida no arquivo (via ffmpeg) e, se não houver, uma imagem
 * "cover/folder" na pasta. Cache de disco pela faixa (caminho + data de modificação + tamanho):
 * antes a chave era o nome do álbum, e álbuns diferentes com o mesmo nome (ou faixas sem tag)
 * dividiam a mesma capa — muitas vezes errada.
 */
object ArtLoader {
    private val dir get() = File(Dirs.cache, "art-v2").also { it.mkdirs() }

    fun key(track: Track): String {
        val f = File(track.path)
        return trackIdOf(track.path + "\u0001" + f.lastModified() + "\u0001" + f.length()).toULong().toString(16)
    }

    fun bytesFor(track: Track): ByteArray? {
        val key = key(track)
        val cached = File(dir, "$key.jpg")
        val none = File(dir, "$key.none")
        if (cached.exists()) return cached.readBytes()
        if (none.exists()) return null
        val embedded = runCatching {
            val p = ProcessBuilder(
                "ffmpeg", "-nostdin", "-loglevel", "error", "-i", track.path, "-an", "-map", "0:v:0", "-frames:v", "1",
                "-vf", "scale='min(1024,iw)':-2", "-f", "image2", "-c:v", "mjpeg", "-",
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val data = p.inputStream.readBytes()
            p.waitFor()
            data.takeIf { it.size > 100 }
        }.getOrNull()
        val bytes = embedded ?: File(track.folder).listFiles { f ->
            f.isFile && f.nameWithoutExtension.lowercase() in setOf("cover", "folder", "front", "album", "artwork") &&
                f.extension.lowercase() in setOf("jpg", "jpeg", "png")
        }?.minByOrNull { it.name.lowercase() }?.readBytes()
        if (bytes == null) runCatching { none.createNewFile() } else runCatching { cached.writeBytes(bytes) }
        return bytes
    }
}
