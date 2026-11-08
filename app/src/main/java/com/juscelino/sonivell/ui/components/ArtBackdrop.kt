package com.juscelino.sonivell.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.theme.LocalGlassMaterial
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlin.math.roundToInt

/**
 * Fundo das telas comuns: a capa que está tocando, desfocada, sob um véu no fundo do tema
 * ([com.juscelino.sonivell.ui.theme.GlassMaterial.scrim]). Sem faixa, ou com o fundo da capa
 * desligado, é só o fundo liso do tema.
 *
 * Barato de propósito: a capa é pedida ao Coil já pequena e desfocada uma única vez, num bitmap de
 * [BackdropBlur.WIDTH] pixels de largura, e o cache guarda o resultado. Na tela é só uma imagem pronta
 * esticada (o próprio esticamento suaviza o resto); nada é desfocado a cada quadro. Ao trocar de
 * faixa as duas capas se cruzam devagar.
 */
@Composable
fun ArtBackdrop(track: Track?, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val material = LocalGlassMaterial.current
    Box(modifier.fillMaxSize().background(c.background)) {
        if (material.backdrop) {
            val context = LocalContext.current
            val dark = c.isDark
            Crossfade(targetState = track?.artUri, animationSpec = tween(CROSSFADE_MS), label = "fundoCapa") { uri: Uri? ->
                if (uri != null) {
                    val request = remember(uri, dark) {
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(SOURCE_PX)
                            .transformations(BackdropBlur(dark))
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(Modifier.fillMaxSize().background(c.background.copy(alpha = material.scrim)))
        }
    }
}

private const val CROSSFADE_MS = 700
private const val SOURCE_PX = 128

/**
 * Reduz a capa para [WIDTH] pixels, desfoca com três passadas de média móvel (perto de um desfoque
 * gaussiano) e acerta o brilho para o tema: no escuro a capa é escurecida e no claro misturada com
 * branco, para que o véu por cima garanta o contraste do texto em qualquer capa.
 */
private class BackdropBlur(private val dark: Boolean) : Transformation {

    override val cacheKey: String = "sonivell-fundo-capa:v1:$dark"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val software = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val height = (software.height * WIDTH / software.width.toFloat()).roundToInt().coerceAtLeast(4)
        val small = Bitmap.createScaledBitmap(software, WIDTH, height, true)
        val px = IntArray(WIDTH * height)
        small.getPixels(px, 0, WIDTH, 0, 0, WIDTH, height)
        repeat(PASSES) {
            blurLines(px, WIDTH, height, horizontal = true)
            blurLines(px, WIDTH, height, horizontal = false)
        }
        for (i in px.indices) px[i] = tone(px[i])
        return Bitmap.createBitmap(px, WIDTH, height, Bitmap.Config.ARGB_8888)
    }

    private fun tone(p: Int): Int {
        fun ch(v: Int) = if (dark) (v * DARKEN).roundToInt() else (v + (255 - v) * LIGHTEN).roundToInt()
        return (0xFF shl 24) or (ch(p shr 16 and 0xFF) shl 16) or (ch(p shr 8 and 0xFF) shl 8) or ch(p and 0xFF)
    }

    /** Média móvel de largura 2·[RADIUS]+1 ao longo de cada linha (ou coluna), com as bordas repetidas. */
    private fun blurLines(px: IntArray, w: Int, h: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = RADIUS * 2 + 1
        val buf = IntArray(len)
        for (line in 0 until lines) {
            fun index(i: Int) = if (horizontal) line * w + i else i * w + line
            for (i in 0 until len) buf[i] = px[index(i)]
            var r = 0
            var g = 0
            var b = 0
            for (k in -RADIUS..RADIUS) {
                val p = buf[k.coerceIn(0, len - 1)]
                r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF
            }
            for (i in 0 until len) {
                px[index(i)] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val leaving = buf[(i - RADIUS).coerceIn(0, len - 1)]
                val entering = buf[(i + RADIUS + 1).coerceIn(0, len - 1)]
                r += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
                g += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
                b += (entering and 0xFF) - (leaving and 0xFF)
            }
        }
    }

    override fun equals(other: Any?) = other is BackdropBlur && other.dark == dark
    override fun hashCode() = dark.hashCode()

    companion object {
        const val WIDTH = 48
        private const val RADIUS = 3
        private const val PASSES = 3
        private const val DARKEN = 0.75f
        private const val LIGHTEN = 0.40f
    }
}
