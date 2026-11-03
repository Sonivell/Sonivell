package com.juscelino.sonivell.playback

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Espectro do que o Sonivell está tocando, para as barras sobre a capa (o "visualizador").
 * O motor de áudio entrega as amostras já mixadas; aqui sai uma FFT de 1024 pontos a cada ~50 ms,
 * só enquanto alguém está ouvindo. Nada é gravado.
 */
object Spectrum {
    private const val SIZE = 1024
    private const val EVERY_MS = 50L

    private val listeners = CopyOnWriteArrayList<(FloatArray) -> Unit>()
    private val window = FloatArray(SIZE)
    private var filled = 0
    private var lastMs = 0L

    val active: Boolean get() = listeners.isNotEmpty()

    fun listen(l: (FloatArray) -> Unit) { listeners.add(l) }
    fun unlisten(l: (FloatArray) -> Unit) { listeners.remove(l) }

    /** [mono]: amostras de -1 a 1. Chamado pela thread de áudio. */
    fun feed(mono: FloatArray, count: Int) {
        for (i in 0 until count) {
            if (filled < SIZE) window[filled++] = mono[i]
        }
        val now = System.currentTimeMillis()
        if (filled < SIZE || now - lastMs < EVERY_MS) {
            if (filled >= SIZE) filled = 0
            return
        }
        lastMs = now
        filled = 0
        val magnitudes = fft(window)
        listeners.forEach { runCatching { it(magnitudes) } }
    }

    /** FFT real com janela de Hann; devolve SIZE/2 magnitudes de 0 a 1. */
    private fun fft(input: FloatArray): FloatArray {
        val n = input.size
        val re = DoubleArray(n) { i -> input[i] * (0.5 - 0.5 * cos(2 * PI * i / (n - 1))) }
        val im = DoubleArray(n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar
                    im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar
                    im[i + k] += ai
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
        return FloatArray(n / 2) { k -> (hypot(re[k], im[k]) / (n / 8.0)).toFloat().coerceIn(0f, 1f) }
    }
}
