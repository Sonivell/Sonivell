package com.juscelino.sonivell.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.theme.ArtTones
import kotlin.math.abs

/** Abaixo desta largura a capa gerada não mostra o painel de vidro com texto. */
private val GlassTextMinWidth = 110.dp

/**
 * Capa gerada para faixas sem arte: sol com brilho e anéis atrás de um painel de vidro
 * translúcido, com sombra, trazendo [title] e [subtitle] em branco.
 * O [seed] escolhe a combinação de cores; sem [title], em tamanhos pequenos ou em capas
 * redondas ([corner] perto da metade da largura) fica só o sol.
 */
@Composable
fun GeneratedArt(
    seed: Long,
    modifier: Modifier = Modifier,
    detailed: Boolean = true,
    title: String? = null,
    subtitle: String? = null,
    corner: Dp = 0.dp,
) {
    val i = (abs(seed) % ArtTones.backgrounds.size).toInt()
    val bg = ArtTones.backgrounds[i]
    val core = ArtTones.cores[i]
    BoxWithConstraints(modifier) {
        val round = corner * 2 >= maxWidth * 0.9f
        val showText = !title.isNullOrBlank() && maxWidth >= GlassTextMinWidth && !round
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.linearGradient(listOf(lerp(bg, Color.White, 0.06f), bg), Offset.Zero, Offset(w * 0.4f, h)))
            drawRect(
                Brush.radialGradient(listOf(core.copy(alpha = 0.30f), Color.Transparent), Offset(w * 0.85f, 0f), w * 0.9f),
            )
            val center = Offset(w / 2f, if (showText) h * 0.40f else h / 2f)
            val r = w * if (showText) 0.26f else 0.25f
            drawCircle(
                Brush.radialGradient(listOf(core.copy(alpha = 0.45f), Color.Transparent), center, r * 2.2f),
                radius = r * 2.2f,
                center = center,
            )
            val stroke = Stroke(width = (w * 0.006f).coerceAtLeast(1.5f))
            drawCircle(Color.White.copy(alpha = 0.11f), radius = w * 0.44f, center = center, style = stroke)
            if (detailed) drawCircle(Color.White.copy(alpha = 0.15f), radius = w * 0.34f, center = center, style = stroke)
            drawCircle(
                Brush.radialGradient(
                    0f to lerp(core, Color.White, 0.55f),
                    0.45f to core,
                    1f to lerp(core, Color.Black, 0.4f),
                    center = center + Offset(-r * 0.3f, -r * 0.4f),
                    radius = r * 1.4f,
                ),
                radius = r,
                center = center,
            )
        }
        if (showText) {
            val unit = maxWidth.value
            val paneCorner = (unit * 0.09f).dp
            val glass = RoundedCornerShape(paneCorner)
            val shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, unit * 0.012f), unit * 0.04f)
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding((unit * 0.08f).dp)
                    .fillMaxWidth()
                    .drawBehind {
                        // Sombra suave do painel: camadas crescentes de preto bem transparente.
                        val cr = paneCorner.toPx()
                        for (k in 1..6) {
                            val grow = k * unit * 0.012f * density
                            drawRoundRect(
                                Color.Black.copy(alpha = 0.07f),
                                topLeft = Offset(-grow * 0.5f, grow * 0.9f),
                                size = Size(size.width + grow, size.height),
                                cornerRadius = CornerRadius(cr + grow),
                            )
                        }
                    }
                    .clip(glass)
                    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.07f))))
                    .background(bg.copy(alpha = 0.25f))
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.08f))),
                        glass,
                    )
                    .padding(horizontal = (unit * 0.08f).dp, vertical = (unit * 0.07f).dp),
                verticalArrangement = Arrangement.spacedBy((unit * 0.012f).dp),
            ) {
                Text(
                    title!!,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = (unit * 0.087f).sp,
                        lineHeight = (unit * 0.105f).sp,
                        letterSpacing = (-0.1).sp,
                        shadow = shadow,
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = (unit * 0.07f).sp, shadow = shadow),
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Capa real do álbum; sem ela, a capa Onda ([OndaArt]). [detailed] ficou da capa gerada anterior
 * e não muda mais nada: a capa Onda escolhe a versão pelo tamanho.
 */
@Composable
fun AlbumArt(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    @Suppress("UNUSED_PARAMETER") detailed: Boolean = false,
) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape)) {
        if (track == null) {
            OndaArt("", "", title = null, subtitle = null, modifier = Modifier.fillMaxSize(), corner = corner)
        } else {
            val context = LocalContext.current
            val album = track.rawAlbum?.takeIf { it.isNotBlank() } ?: track.title
            val artista = track.albumArtist?.takeIf { it.isNotBlank() } ?: track.artist
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.artUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                // Só o fundo enquanto carrega, para não piscar a onda antes da capa real.
                loading = {
                    OndaArt(album, artista, title = null, subtitle = null, modifier = Modifier.fillMaxSize(), corner = corner, placeholder = true)
                },
                error = {
                    OndaArt(album, artista, title = album, subtitle = artista, modifier = Modifier.fillMaxSize(), corner = corner)
                },
            )
        }
    }
}
