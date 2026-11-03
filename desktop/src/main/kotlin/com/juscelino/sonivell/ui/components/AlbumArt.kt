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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import com.juscelino.sonivell.data.ArtProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.theme.ArtTones
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.data.UiStyle
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

/** Tons foscos das capas vazias do Sonivell, um por artista. */
private val FrostedTones = listOf(
    Color(0xFF2F4B45), Color(0xFF4A3F2A), Color(0xFF3A3350), Color(0xFF4A2A2E), Color(0xFF2A3A4E),
)

/** Até duas iniciais do nome ("Lariss Neo-Soul" → "LN"), ignorando símbolos e números soltos. */
internal fun initialsOf(name: String): String =
    name.split(Regex("""[\s\-_/·]+"""))
        .mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
        .take(2)
        .joinToString("")

/**
 * Capa de quem não tem arte: um bloco com as iniciais, sem repetir o título (que já aparece embaixo
 * do cartão). No Sonivell, tons foscos escolhidos pelo artista; nos outros estilos e no modo claro,
 * cinza neutro. Pequena demais, fica só o bloco.
 */
@Composable
fun InitialsArt(name: String, toneKey: String, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val frosted = SonivellTheme.style == UiStyle.SONIVELL && c.isDark
    val bg = if (frosted) FrostedTones[OndaSeed.cor(toneKey)] else c.surfaceHigh
    val fg = if (frosted) Color(0xFFF3F4F6).copy(alpha = 0.92f) else c.textSecondary
    BoxWithConstraints(modifier.background(bg), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val text = initialsOf(name)
        if (side >= 28.dp && text.isNotEmpty()) {
            Text(
                text,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = (side.value * 0.32f).sp, letterSpacing = 0.sp),
                fontWeight = FontWeight.ExtraBold,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

/**
 * Capa real do álbum; sem ela, a capa vazia do estilo: a onda no Ondas (sem texto por cima, que
 * repetiria o título) e as iniciais nos outros ([InitialsArt]).
 */
@Composable
fun AlbumArt(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    @Suppress("UNUSED_PARAMETER") detailed: Boolean = false,
) {
    val shape = RoundedCornerShape(corner)
    val ondas = SonivellTheme.style == UiStyle.ONDAS
    // Capas grandes (player, cabeçalhos) pedem a imagem em alta; nas listas basta a pequena.
    var large by remember { mutableStateOf(false) }
    Box(modifier.clip(shape).onSizeChanged { if (it.width > LARGE_ART_PX) large = true }) {
        if (track == null) {
            if (ondas) OndaArt("", "", title = null, subtitle = null, modifier = Modifier.fillMaxSize(), corner = corner)
            else InitialsArt("", "", Modifier.fillMaxSize())
        } else {
            val album = track.rawAlbum?.takeIf { it.isNotBlank() } ?: track.title
            val artista = track.albumArtist?.takeIf { it.isNotBlank() } ?: track.artist
            when (val art = rememberArtImage(track, large).value) {
                // Só o fundo enquanto carrega, para não piscar a capa vazia antes da capa real.
                ArtLoading ->
                    if (ondas) OndaArt(album, artista, title = null, subtitle = null, modifier = Modifier.fillMaxSize(), corner = corner, placeholder = true)
                    else Box(Modifier.fillMaxSize().background(SonivellTheme.colors.surfaceHigh))
                is ImageBitmap -> Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else ->
                    if (ondas) OndaArt(album, artista, title = null, subtitle = null, modifier = Modifier.fillMaxSize(), corner = corner)
                    else InitialsArt(album, artista, Modifier.fillMaxSize())
            }
        }
    }
}

/** Marca de "capa ainda carregando" em [rememberArtImage]. */
object ArtLoading

/**
 * Capa de [track] carregada fora da thread da tela: [ArtLoading] enquanto lê, a imagem, ou null
 * quando a faixa não tem capa. Volta a carregar quando a capa própria do álbum muda.
 */
@Composable
fun rememberArtImage(track: Track, large: Boolean = false): State<Any?> {
    val key = ArtProvider.keyFor(track, large)
    // Enquanto a grande carrega, mostra a pequena (se já houver): a capa não pisca ao abrir o player.
    val initial = when {
        ArtProvider.isCached(track, large) -> ArtProvider.cached(track, large)
        large && ArtProvider.isCached(track, false) -> ArtProvider.cached(track, false) ?: ArtLoading
        else -> ArtLoading
    }
    return produceState(initial, key) {
        if (!ArtProvider.isCached(track, large)) value = withContext(Dispatchers.IO) { ArtProvider.load(track, large) }
    }
}

/** Acima desta largura em pixels a capa usa a versão grande. */
private const val LARGE_ART_PX = 260

/**
 * Colagem 2×2 das capas de [tracks] (álbuns diferentes), usada nos mixes do Sonivell. Com menos de
 * quatro álbuns, a capa da primeira faixa ocupa tudo.
 */
@Composable
fun CoverCollage(tracks: List<Track>, modifier: Modifier = Modifier, corner: Dp = 20.dp) {
    val covers = tracks.distinctBy { it.albumId }.take(4)
    Box(modifier.clip(RoundedCornerShape(corner))) {
        if (covers.size < 4) {
            AlbumArt(covers.firstOrNull(), Modifier.fillMaxSize(), corner = 0.dp)
        } else {
            Column(Modifier.fillMaxSize()) {
                listOf(covers.take(2), covers.drop(2)).forEach { row ->
                    androidx.compose.foundation.layout.Row(Modifier.weight(1f).fillMaxWidth()) {
                        row.forEach { AlbumArt(it, Modifier.weight(1f).fillMaxSize(), corner = 0.dp) }
                    }
                }
            }
        }
    }
}
