package com.juscelino.sonivell.ui.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.theme.ArtTones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Tom dominante da capa (brilho do player), escurecido por padrão.
 * Sem capa (ou enquanto carrega), usa o tom da capa gerada da mesma faixa. [adjust] troca o
 * escurecimento por outro ajuste (o brilho do vidro usa um tom vivo).
 */
@Composable
internal fun artTint(track: Track, adjust: (Color) -> Color = ::darken): Color {
    val fallback = remember(track.albumId) {
        adjust(ArtTones.rings[(abs(track.albumId) % ArtTones.rings.size).toInt()])
    }
    val key = com.juscelino.sonivell.data.ArtProvider.keyFor(track)
    var tint by remember(key) { mutableStateOf(tintCache[key]?.let(adjust) ?: fallback) }
    LaunchedEffect(key) {
        val color = tintCache[key] ?: withContext(Dispatchers.IO) {
            com.juscelino.sonivell.data.ArtProvider.bytesFor(track)?.let { bytes ->
                runCatching { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))?.let(::dominant) }.getOrNull()
            }
        }?.also { tintCache[key] = it }
        if (color != null) tint = adjust(color)
    }
    return tint
}

/** Cor dominante de cada capa já vista (a conta lê a imagem do disco). */
private val tintCache = java.util.concurrent.ConcurrentHashMap<String, Color>()

private fun hsvOf(rgb: Int, out: FloatArray) {
    java.awt.Color.RGBtoHSB((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, out)
}

/** Média dos pixels (amostra de até 48×48) com peso maior para os mais saturados e claros (evita um cinza lavado). */
private fun dominant(image: java.awt.image.BufferedImage): Color {
    val hsv = FloatArray(3)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var total = 0.0
    val stepX = (image.width / 48).coerceAtLeast(1)
    val stepY = (image.height / 48).coerceAtLeast(1)
    var y = 0
    while (y < image.height) {
        var x = 0
        while (x < image.width) {
            val p = image.getRGB(x, y)
            x += stepX
            if ((p ushr 24) < 128) continue
            hsvOf(p, hsv)
            val w = 0.05 + hsv[1] * hsv[2]
            r += ((p shr 16) and 0xFF) * w
            g += ((p shr 8) and 0xFF) * w
            b += (p and 0xFF) * w
            total += w
        }
        y += stepY
    }
    if (total == 0.0) return Color(0xFF2A2626)
    return Color((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
}

private fun fromHsv(hsv: FloatArray): Color = Color(java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) or (0xFF shl 24))

private fun toHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    java.awt.Color.RGBtoHSB((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt(), hsv)
    return hsv
}

/** O tom da capa vivo e médio, para brilhar sobre o fundo escuro sem estourar (brilho do vidro). */
internal fun glowTone(color: Color): Color {
    val hsv = toHsv(color)
    hsv[1] = hsv[1].coerceIn(0.35f, 0.8f)
    hsv[2] = hsv[2].coerceIn(0.6f, 0.85f)
    return fromHsv(hsv)
}

private fun darken(color: Color): Color {
    val hsv = toHsv(color)
    hsv[1] = hsv[1].coerceAtMost(0.62f)
    hsv[2] = hsv[2].coerceIn(0.16f, 0.36f)
    return fromHsv(hsv)
}

