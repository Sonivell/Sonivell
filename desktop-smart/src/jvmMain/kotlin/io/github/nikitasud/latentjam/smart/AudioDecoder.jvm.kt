package io.github.nikitasud.latentjam.smart

import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Resultado da decodificação de uma janela, tipado até o motor avaliar todos os recortes.
 * [InvalidAudio]: fato determinístico sobre o arquivo. [Unavailable]: falha que pode passar depois.
 */
internal sealed interface AudioDecodeResult {
    class Success(val waveform: FloatArray, val validSamples: Int) : AudioDecodeResult
    data class InvalidAudio(val detail: String) : AudioDecodeResult
    data class Unavailable(val detail: String) : AudioDecodeResult
}

/** Decodifica um trecho de uma faixa para mono float via ffmpeg (já reamostrado para [targetSampleRate]). */
internal class FfmpegAudioDecoder {

    fun decodeWindowMono(
        audioUri: String,
        startMs: Long,
        targetSampleRate: Int,
        targetSamples: Int,
        isCancelled: () -> Boolean,
    ): AudioDecodeResult {
        val path = pathOf(audioUri)
        if (!File(path).isFile) return AudioDecodeResult.Unavailable("arquivo não encontrado: $path")
        val seconds = targetSamples.toDouble() / targetSampleRate
        val cmd = listOf(
            "ffmpeg", "-nostdin", "-loglevel", "error",
            "-ss", "%.3f".format(java.util.Locale.ROOT, startMs / 1000.0),
            "-i", path, "-vn", "-sn", "-dn",
            "-t", "%.3f".format(java.util.Locale.ROOT, seconds),
            "-ac", "1", "-ar", targetSampleRate.toString(), "-f", "f32le", "-",
        )
        val process = try {
            ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (e: Exception) {
            return AudioDecodeResult.Unavailable("ffmpeg indisponível: ${e.message}")
        }
        return try {
            val bytes = ByteArray(targetSamples * 4)
            var filled = 0
            val input = process.inputStream
            while (filled < bytes.size) {
                if (isCancelled()) return AudioDecodeResult.Unavailable("cancelado")
                val n = input.read(bytes, filled, bytes.size - filled)
                if (n < 0) break
                filled += n
            }
            val samples = filled / 4
            if (samples <= 0) {
                return AudioDecodeResult.InvalidAudio("o ffmpeg não produziu áudio em ${startMs}ms")
            }
            val wave = FloatArray(targetSamples)
            val buffer = java.nio.ByteBuffer.wrap(bytes, 0, samples * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until samples) {
                val v = buffer.getFloat()
                wave[i] = if (v.isFinite()) v.coerceIn(-1f, 1f) else 0f
            }
            AudioDecodeResult.Success(wave, samples)
        } catch (e: Exception) {
            AudioDecodeResult.Unavailable("falha ao ler o áudio: ${e.message}")
        } finally {
            process.destroy()
            runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        }
    }

    private fun pathOf(audioUri: String): String =
        if (audioUri.startsWith("file:")) runCatching { File(URI(audioUri)).path }.getOrDefault(audioUri) else audioUri
}
