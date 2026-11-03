package com.juscelino.sonivell.ui.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private const val BANDS = 28

/**
 * Barras de espectro desenhadas sobre a capa. No PC o próprio motor de áudio calcula o espectro
 * do que está tocando (sem microfone nem permissão): é o som que sai do Sonivell, nada é gravado.
 */
@Composable
fun AudioBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val levels = remember { mutableStateOf(FloatArray(BANDS)) }
    DisposableEffect(playing) {
        val listener: (FloatArray) -> Unit = { fft -> levels.value = bandsOf(fft, levels.value) }
        if (playing) com.juscelino.sonivell.playback.Spectrum.listen(listener)
        onDispose {
            com.juscelino.sonivell.playback.Spectrum.unlisten(listener)
            levels.value = FloatArray(BANDS)
        }
    }

    Canvas(modifier) {
        val values = levels.value
        val gap = size.width * 0.012f
        val barW = (size.width - gap * (BANDS + 1)) / BANDS
        values.forEachIndexed { i, v ->
            val h = max(size.height * 0.03f, v * size.height)
            val x = gap + i * (barW + gap)
            drawRoundRect(
                color = color.copy(alpha = 0.85f),
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2, barW / 2),
            )
        }
    }
}

/** Magnitudes da FFT (0..1, do grave ao agudo) -> [BANDS] faixas em escala quadrática, suavizadas com o quadro anterior. */
private fun bandsOf(magnitudes: FloatArray, previous: FloatArray): FloatArray {
    val bins = magnitudes.size
    val out = FloatArray(BANDS)
    for (b in 0 until BANDS) {
        val from = max(1, (bins * Math.pow(b.toDouble() / BANDS, 2.0)).toInt())
        val to = max(from + 1, (bins * Math.pow((b + 1).toDouble() / BANDS, 2.0)).toInt()).coerceAtMost(bins)
        var peak = 0f
        for (k in from until to) peak = max(peak, magnitudes[k])
        val db = (ln(1f + peak * 127f) / ln(128f)).coerceIn(0f, 1f)
        out[b] = max(db, previous.getOrElse(b) { 0f } * 0.8f)
    }
    return out
}
