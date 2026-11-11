package com.juscelino.sonivell.ui.nowplaying

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.juscelino.sonivell.playback.PlaybackService
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private const val BANDS = 28

/**
 * Barras de espectro desenhadas sobre a capa. Usa o Visualizer do Android ligado na sessão de
 * áudio do player; sem a permissão de microfone (ou se o aparelho recusar), não desenha nada.
 * Só lê o som que o próprio Sonivell está tocando, nada é gravado.
 */
@Composable
fun AudioBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val levels = remember { mutableStateOf(FloatArray(BANDS)) }
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val session = PlaybackService.currentAudioSession

    DisposableEffect(granted, session, playing) {
        var viz: Visualizer? = null
        if (granted && session != 0 && playing) {
            viz = runCatching {
                Visualizer(session).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                                if (fft != null) levels.value = bands(fft, levels.value)
                            }
                        },
                        min(Visualizer.getMaxCaptureRate(), 20_000),
                        false,
                        true,
                    )
                    enabled = true
                }
            }.getOrNull()
        }
        onDispose {
            runCatching { viz?.enabled = false }
            viz?.release()
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

/** FFT -> [BANDS] faixas em escala logarítmica, suavizadas com o quadro anterior. */
private fun bands(fft: ByteArray, previous: FloatArray): FloatArray {
    val bins = fft.size / 2
    val out = FloatArray(BANDS)
    for (b in 0 until BANDS) {
        val from = max(1, (bins * Math.pow(b.toDouble() / BANDS, 2.0)).toInt())
        val to = max(from + 1, (bins * Math.pow((b + 1).toDouble() / BANDS, 2.0)).toInt()).coerceAtMost(bins)
        var peak = 0f
        for (k in from until to) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            peak = max(peak, hypot(re, im))
        }
        val db = (ln(1f + peak) / ln(128f)).coerceIn(0f, 1f)
        out[b] = max(db, previous.getOrElse(b) { 0f } * 0.8f)
    }
    return out
}
