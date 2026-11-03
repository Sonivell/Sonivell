package com.juscelino.sonivell.desktop

import com.juscelino.sonivell.playback.Spectrum
import java.io.InputStream
import java.util.Locale
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Efeitos aplicados pelo ffmpeg ao decodificar: equalizador (níveis em milibéis por banda),
 * reforço de graves (0–1000, como o BassBoost do Android), velocidade e cortar silêncio.
 */
data class AudioFilters(
    val eqEnabled: Boolean = false,
    val bandFrequencies: List<Int> = emptyList(),
    val levels: List<Int> = emptyList(),
    val bass: Int = 0,
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
) {
    /** A cadeia do -af do ffmpeg, ou null sem efeito nenhum. */
    fun chain(): String? {
        val parts = ArrayList<String>()
        if (eqEnabled) {
            bandFrequencies.forEachIndexed { i, f ->
                val db = (levels.getOrNull(i) ?: 0) / 100.0
                if (db != 0.0) parts += String.format(Locale.ROOT, "equalizer=f=%d:t=o:w=1.6:g=%.2f", f, db)
            }
            if (bass > 0) parts += String.format(Locale.ROOT, "bass=g=%.2f:f=110:w=0.6", bass / 1000.0 * 12.0)
            if (parts.isNotEmpty()) parts += "alimiter=limit=0.95:level=false"
        }
        if (skipSilence) parts += "silenceremove=start_periods=1:start_threshold=-50dB:stop_periods=-1:stop_duration=0.6:stop_threshold=-50dB"
        if (kotlin.math.abs(speed - 1f) > 0.01f) parts += String.format(Locale.ROOT, "atempo=%.3f", speed.coerceIn(0.5f, 2f))
        return parts.takeIf { it.isNotEmpty() }?.joinToString(",")
    }
}

/**
 * Motor de áudio do PC: o ffmpeg decodifica qualquer formato para PCM (já com os efeitos) e uma
 * thread só mistura e toca pelo Java Sound. Assim:
 *  - a passagem automática para a próxima faixa não tem pausa (a nova entra logo atrás no buffer);
 *  - o crossfade toca as duas faixas juntas, com curvas de mesma potência;
 *  - o ganho do ReplayGain e o volume são aplicados aqui, sem reiniciar a decodificação.
 */
class AudioEngine(
    /** A faixa atual acabou (o fim já está no buffer de saída): o player escolhe a próxima. */
    private val onEnded: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val format = AudioFormat(RATE.toFloat(), 16, 2, true, false)
    private val lock = Object()
    private var line: SourceDataLine? = null

    private class Decoder(val path: String, val startMs: Long, val speed: Float, var gain: Float, filters: String?) {
        val process: Process
        val input: InputStream
        var eof = false

        init {
            val cmd = arrayListOf(
                "ffmpeg", "-nostdin", "-loglevel", "error",
                "-ss", String.format(Locale.ROOT, "%.3f", startMs / 1000.0),
                "-i", path, "-vn",
            )
            if (filters != null) cmd += listOf("-af", filters)
            cmd += listOf("-f", "s16le", "-ac", "2", "-ar", RATE.toString(), "-")
            process = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            input = process.inputStream
        }

        /** Lê até encher [buf] (ou acabar). Devolve os bytes lidos. */
        fun fill(buf: ByteArray): Int {
            var off = 0
            while (off < buf.size && !eof) {
                val n = input.read(buf, off, buf.size - off)
                if (n < 0) eof = true else off += n
            }
            return off - (off % 4)
        }

        fun close() { runCatching { process.destroy() } }
    }

    // ---- Estado, sempre sob [lock] ----
    private var main: Decoder? = null
    /** Quadro (contado em [written]) em que o primeiro som de [main] entrou no buffer de saída. */
    private var mainBase = 0L
    private var tail: Decoder? = null
    private var fadeFrames = 0L
    private var fadeDone = 0L
    private var paused = false
    /** Quadros escritos na saída menos os descartados: acompanha [SourceDataLine.getLongFramePosition]. */
    private var written = 0L
    private var generation = 0
    private var filters = AudioFilters()
    private var worker: Thread? = null

    @Volatile var volume: Float = 0.8f
        set(value) { field = value.coerceIn(0f, 1f) }

    /** Duas casas: o caminho e a posição pedida, para reiniciar a decodificação ao mudar um efeito. */
    val isActive: Boolean get() = synchronized(lock) { main != null }

    private fun ensureLine(): SourceDataLine? {
        line?.let { return it }
        return try {
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val opened = AudioSystem.getLine(info) as SourceDataLine
            opened.open(format, RATE * 4 / 4) // 250 ms de buffer
            opened.start()
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

    private fun startWorker() {
        if (worker?.isAlive == true) return
        worker = Thread(::loop, "sonivell-audio").apply { isDaemon = true; start() }
    }

    private fun newDecoder(path: String, fromMs: Long, gain: Float): Decoder? = try {
        Decoder(path, fromMs, filters.speed, gain, filters.chain())
    } catch (e: Exception) {
        onError("ffmpeg: " + (e.message ?: ""))
        null
    }

    /** Posição da faixa atual, em ms (o que já saiu pelo alto-falante). */
    fun positionMs(): Long = synchronized(lock) {
        val d = main ?: return 0L
        val l = line ?: return d.startMs
        val played = (l.longFramePosition - mainBase).coerceAtLeast(0)
        d.startMs + (played * 1000L * d.speed / RATE).toLong()
    }

    /**
     * Toca [path] a partir de [fromMs]. [immediate] descarta o que está no buffer (o usuário
     * escolheu outra faixa); sem ele, a faixa entra logo depois da atual (passagem sem pausa).
     */
    fun play(path: String, fromMs: Long, gain: Float = 1f, immediate: Boolean = true) {
        val l = ensureLine() ?: return
        val d = newDecoder(path, fromMs, gain) ?: return
        synchronized(lock) {
            generation++
            main?.close()
            tail?.close()
            tail = null
            if (immediate) {
                l.flush()
                written = l.longFramePosition
            }
            main = d
            mainBase = written
            paused = false
            l.start()
            lock.notifyAll()
        }
        startWorker()
    }

    /**
     * Crossfade: [path] entra agora, crescendo, enquanto a faixa atual continua tocando e some
     * em [fadeMs] (curvas de mesma potência).
     */
    fun crossfadeTo(path: String, fromMs: Long, gain: Float, fadeMs: Long) {
        ensureLine() ?: return
        val d = newDecoder(path, fromMs, gain) ?: return
        synchronized(lock) {
            // Sem mudar a geração: o pedaço da faixa que sai, já lido, ainda toca.
            tail?.close()
            tail = main
            main = d
            mainBase = written
            fadeFrames = (fadeMs * RATE / 1000).coerceAtLeast(1)
            fadeDone = 0
            lock.notifyAll()
        }
        startWorker()
    }

    /** Ganho da faixa atual (normalização pelo ReplayGain); 1 = sem mudança. */
    fun setTrackGain(gain: Float) = synchronized(lock) { main?.gain = gain }

    /** Troca os efeitos; com algo tocando, a decodificação recomeça da posição atual. */
    fun setFilters(next: AudioFilters) {
        val restart = synchronized(lock) {
            if (next == filters) return
            filters = next
            main
        } ?: return
        val pos = positionMs()
        play(restart.path, pos, restart.gain, immediate = true)
        if (synchronized(lock) { paused }) pause()
    }

    fun pause() = synchronized(lock) {
        paused = true
        line?.stop()
    }

    fun resume() = synchronized(lock) {
        paused = false
        line?.start()
        lock.notifyAll()
    }

    fun stop() = synchronized(lock) {
        generation++
        main?.close(); main = null
        tail?.close(); tail = null
        line?.let { it.flush(); written = it.longFramePosition }
        paused = false
    }

    fun close() {
        stop()
        synchronized(lock) { line?.close(); line = null }
    }

    private fun loop() {
        val mbuf = ByteArray(CHUNK)
        val tbuf = ByteArray(CHUNK)
        val out = ByteArray(CHUNK)
        val mono = FloatArray(CHUNK / 4)
        while (true) {
            var gen = 0
            var mm: Decoder? = null
            var tt: Decoder? = null
            var ll: SourceDataLine? = null
            synchronized(lock) {
                // Sem nada tocando (ou pausado): o resto do buffer sai e a thread espera.
                while ((main == null || paused) && line != null) lock.wait(200)
                ll = line
                mm = main
                tt = tail
                gen = generation
            }
            val l = ll ?: return
            val m = mm ?: continue
            val t = tt
            val n = runCatching { m.fill(mbuf) }.getOrElse { m.eof = true; 0 }
            val frames = n / 4
            var fadeFrom = 0L
            var fadeTotal = 1L
            var tn = 0
            if (t != null) {
                tn = runCatching { t.fill(tbuf) }.getOrElse { t.eof = true; 0 }
                synchronized(lock) { fadeFrom = fadeDone; fadeTotal = fadeFrames }
            }
            val master = volume * volume
            var i = 0
            while (i < frames) {
                val o = i * 4
                var l0 = ((mbuf[o + 1].toInt() shl 8) or (mbuf[o].toInt() and 0xff)).toShort() * m.gain
                var r0 = ((mbuf[o + 3].toInt() shl 8) or (mbuf[o + 2].toInt() and 0xff)).toShort() * m.gain
                if (t != null) {
                    val p = ((fadeFrom + i).toFloat() / fadeTotal).coerceIn(0f, 1f)
                    val angle = p * (PI / 2).toFloat()
                    val fin = sin(angle)
                    val fout = cos(angle)
                    l0 *= fin; r0 *= fin
                    if (o + 3 < tn) {
                        l0 += ((tbuf[o + 1].toInt() shl 8) or (tbuf[o].toInt() and 0xff)).toShort() * t.gain * fout
                        r0 += ((tbuf[o + 3].toInt() shl 8) or (tbuf[o + 2].toInt() and 0xff)).toShort() * t.gain * fout
                    }
                }
                val lv = (l0 * master).toInt().coerceIn(-32768, 32767)
                val rv = (r0 * master).toInt().coerceIn(-32768, 32767)
                out[o] = (lv and 0xff).toByte(); out[o + 1] = ((lv shr 8) and 0xff).toByte()
                out[o + 2] = (rv and 0xff).toByte(); out[o + 3] = ((rv shr 8) and 0xff).toByte()
                mono[i] = (lv + rv) / 65536f
                i++
            }
            if (frames > 0 && Spectrum.active) Spectrum.feed(mono, frames)
            var off = 0
            while (off < frames * 4) {
                if (synchronized(lock) { generation != gen && main !== m }) break
                off += l.write(out, off, frames * 4 - off)
            }
            var ended = false
            synchronized(lock) {
                if (generation == gen || main === m) {
                    written += off / 4
                    if (t != null) {
                        fadeDone += frames
                        if (fadeDone >= fadeFrames || t.eof) { t.close(); if (tail === t) tail = null }
                    }
                    if (m.eof && main === m) {
                        main = null
                        m.close()
                        ended = true
                    }
                } else {
                    // Outra faixa entrou enquanto este pedaço era escrito: ele não deve tocar.
                    l.flush()
                    written = l.longFramePosition
                }
            }
            if (ended) {
                runCatching { onEnded() }
                // Nada entrou no lugar: espera o buffer esvaziar (o fim da faixa toca inteiro).
                if (synchronized(lock) { main == null }) runCatching { l.drain() }
            }
        }
    }

    companion object {
        const val RATE = 44100
        private const val CHUNK = 4096

        fun available(): Boolean = runCatching {
            ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start().let { p ->
                p.inputStream.readBytes(); p.waitFor() == 0
            }
        }.getOrDefault(false)
    }
}
