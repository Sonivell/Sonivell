package com.juscelino.sonivell.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.theme.ArtTones
import kotlin.math.abs

/**
 * Capa geométrica do mockup (círculos concêntricos + onda).
 * Usada quando a faixa não tem arte; o [seed] escolhe a combinação de cores.
 */
@Composable
fun GeneratedArt(seed: Long, modifier: Modifier = Modifier, detailed: Boolean = true) {
    val i = (abs(seed) % ArtTones.backgrounds.size).toInt()
    val bg = ArtTones.backgrounds[i]
    val ring = ArtTones.rings[i]
    val core = ArtTones.cores[i]
    Canvas(modifier.background(bg)) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h * 0.61f)
        val stroke = Stroke(width = (w * 0.006f).coerceAtLeast(1.5f))
        if (detailed) {
            drawCircle(ring.copy(alpha = 0.55f), radius = w * 0.44f, center = center, style = stroke)
            drawCircle(ring.copy(alpha = 0.75f), radius = w * 0.34f, center = center, style = stroke)
        }
        drawCircle(ring, radius = w * 0.235f, center = center, style = stroke)
        drawCircle(core, radius = w * 0.13f, center = center)
        if (detailed) {
            val top = h * 0.73f
            drawRect(bg.copy(alpha = 0.6f), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(w, h - top))
            val wave = Path().apply {
                moveTo(0f, top)
                val seg = w / 4f
                for (k in 0 until 4) {
                    val x0 = k * seg
                    quadraticTo(x0 + seg / 2f, top + if (k % 2 == 0) -h * 0.05f else h * 0.05f, x0 + seg, top)
                }
            }
            drawPath(wave, core, style = stroke)
        }
    }
}

/** Capa real do álbum, com a capa geométrica como reserva. */
@Composable
fun AlbumArt(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    detailed: Boolean = false,
) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape)) {
        if (track == null) {
            GeneratedArt(seed = 0, modifier = Modifier.fillMaxSize(), detailed = detailed)
        } else {
            val context = LocalContext.current
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.artUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { GeneratedArt(track.albumId, Modifier.fillMaxSize(), detailed) },
                error = { GeneratedArt(track.albumId, Modifier.fillMaxSize(), detailed) },
            )
        }
    }
}
