package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Text
import com.juscelino.sonivell.desktop.ArtLoader
import com.juscelino.sonivell.desktop.Track
import com.juscelino.sonivell.desktop.str
import com.juscelino.sonivell.desktop.waveShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Clique com realce ao passar o mouse e o cursor de mãozinha. */
@Composable
fun Modifier.hoverClick(
    shape: Shape = RoundedCornerShape(12.dp),
    hover: Color = SonivellTheme.colors.surface,
    base: Color = Color.Transparent,
    label: String? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return this
        .clip(shape)
        .background(if (hovered) hover else base)
        .hoverable(source)
        .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)
        .let { if (label != null) it.semantics { contentDescription = label } else it }
}

object ArtCache {
    private val memory = object : LinkedHashMap<String, ImageBitmap?>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > 300
    }
    private val permits = Semaphore(3)

    suspend fun get(track: Track): ImageBitmap? {
        val key = withContext(Dispatchers.IO) { ArtLoader.key(track) }
        synchronized(memory) { if (memory.containsKey(key)) return memory[key] }
        val bmp = permits.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    ArtLoader.bytesFor(track)?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }
                }.getOrNull()
            }
        }
        synchronized(memory) { memory[key] = bmp }
        return bmp
    }
}

private val artBackgrounds = listOf(Color(0xFF1B2A26), Color(0xFF1D2433), Color(0xFF2E221A), Color(0xFF2A1D30))
private val artCores = listOf(Color(0xFFC8F04A), Color(0xFF7CC4FF), Color(0xFFFF9F5A), Color(0xFFE8A6FF))

/** Abaixo desta largura a capa gerada não mostra o painel de vidro com texto. */
private val GlassTextMinWidth = 110.dp

/**
 * Capa da faixa: arte embutida, imagem da pasta ou, quando não há, a capa Onda ([OndaArt]).
 * Com [legacyArt], a reserva é a capa gerada anterior ([GlassArt]): o player continua com ela.
 */
@Composable
fun AlbumArt(track: Track?, size: Dp, corner: Dp = 14.dp, modifier: Modifier = Modifier, legacyArt: Boolean = false) {
    val bmp by produceState<ImageBitmap?>(null, track?.id) {
        value = if (track == null) null else ArtCache.get(track)
    }
    val seed = ((track?.albumKey?.hashCode() ?: 0) and 0x7fffffff) % 4
    Box(modifier.size(size).clip(RoundedCornerShape(corner)), contentAlignment = Alignment.Center) {
        val image = bmp
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (!legacyArt) {
            val album = track?.album?.ifBlank { track.title }.orEmpty()
            val artista = track?.albumArtist?.ifBlank { track.artist }.orEmpty()
            OndaArt(album, artista, title = album.takeIf { track != null }, subtitle = artista, size = size, corner = corner)
        } else {
            // Painel com texto só em capas grandes e não redondas (artistas).
            val showText = track != null && size >= GlassTextMinWidth && corner * 2 < size * 0.9f
            GlassArt(
                seed,
                size,
                title = if (showText) track!!.album.ifBlank { track.title } else null,
                subtitle = track?.albumArtist?.ifBlank { track.artist },
            )
        }
    }
}

/**
 * Capa gerada: sol com brilho e anéis atrás de um painel de vidro translúcido, com sombra,
 * trazendo [title] e [subtitle] em branco. Sem [title] fica só o sol.
 */
@Composable
private fun GlassArt(seed: Int, size: Dp, title: String?, subtitle: String?) {
    val bg = artBackgrounds[seed]
    val core = artCores[seed]
    val showText = !title.isNullOrBlank()
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            drawRect(Brush.linearGradient(listOf(lerp(bg, Color.White, 0.06f), bg), Offset.Zero, Offset(w * 0.4f, h)))
            drawRect(Brush.radialGradient(listOf(core.copy(alpha = 0.30f), Color.Transparent), Offset(w * 0.85f, 0f), w * 0.9f))
            val c = Offset(w / 2f, if (showText) h * 0.40f else h / 2f)
            val r = w * if (showText) 0.26f else 0.25f
            drawCircle(Brush.radialGradient(listOf(core.copy(alpha = 0.45f), Color.Transparent), c, r * 2.2f), radius = r * 2.2f, center = c)
            val stroke = Stroke(width = (w * 0.006f).coerceAtLeast(1.5f))
            drawCircle(Color.White.copy(alpha = 0.11f), radius = w * 0.44f, center = c, style = stroke)
            drawCircle(Color.White.copy(alpha = 0.15f), radius = w * 0.34f, center = c, style = stroke)
            drawCircle(
                Brush.radialGradient(
                    0f to lerp(core, Color.White, 0.55f),
                    0.45f to core,
                    1f to lerp(core, Color.Black, 0.4f),
                    center = c + Offset(-r * 0.3f, -r * 0.4f),
                    radius = r * 1.4f,
                ),
                radius = r,
                center = c,
            )
        }
        if (showText) {
            val unit = size.value
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
                                size = Size(this.size.width + grow, this.size.height),
                                cornerRadius = CornerRadius(cr + grow),
                            )
                        }
                    }
                    .clip(glass)
                    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.07f))))
                    .background(bg.copy(alpha = 0.25f))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.08f))), glass)
                    .padding(horizontal = (unit * 0.08f).dp, vertical = (unit * 0.07f).dp),
                verticalArrangement = Arrangement.spacedBy((unit * 0.012f).dp),
            ) {
                Text(
                    title!!,
                    style = TextStyle(fontSize = (unit * 0.087f).sp, lineHeight = (unit * 0.105f).sp, letterSpacing = (-0.1).sp, shadow = shadow),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = TextStyle(fontSize = (unit * 0.07f).sp, shadow = shadow),
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

@Composable
fun Waveform(
    seed: Long,
    progress: Float,
    modifier: Modifier = Modifier,
    bars: Int = 44,
    barWidth: Dp = 3.dp,
    played: Color = SonivellTheme.colors.accent,
    rest: Color = SonivellTheme.colors.textSecondary.copy(alpha = 0.35f),
) {
    val shape = remember(seed, bars) { waveShape(seed, bars) }
    Canvas(modifier) {
        val w = barWidth.toPx()
        val step = if (bars > 1) (size.width - w) / (bars - 1) else 0f
        val p = progress.coerceIn(0f, 1f)
        for (i in 0 until bars) {
            val h = size.height * shape[i]
            drawRoundRect(
                color = if ((i + 0.5f) / bars <= p) played else rest,
                topLeft = Offset(i * step, (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
        }
    }
}

/** Barra de progresso em forma de onda: toque e arraste para buscar. */
@Composable
fun WaveSeekBar(seed: Long, positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, bars: Int = 96, height: Dp = 30.dp) {
    val label = str("cd_seek")
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .height(height + 10.dp)
            .semantics { contentDescription = label }
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(durationMs) {
                detectTapGestures { o -> if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong()) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Waveform(seed, fraction, Modifier.fillMaxWidth().height(height), bars = bars)
    }
}

@Composable
fun IconBtn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = SonivellTheme.colors.textPrimary,
    outlined: Boolean = false,
    active: Boolean = false,
) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier
            .size(size)
            .let { if (outlined && !active) it.border(1.dp, c.outline, shape) else it }
            .hoverClick(shape, hover = if (active) c.accent else c.surfaceHigh, base = if (active) c.accent else Color.Transparent, label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(iconSize), tint = if (active) c.onAccent else tint)
    }
}

/** Botão de tocar do Ondas: quadrado arredondado na cor de destaque. */
@Composable
fun PlaySquircle(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp, corner: Dp = 16.dp, iconSize: Dp = 22.dp) {
    val c = SonivellTheme.colors
    val label = str(if (playing) "cd_pause" else "cd_play")
    Box(
        modifier.size(size).hoverClick(RoundedCornerShape(corner), hover = c.accent.copy(alpha = 0.85f), base = c.accent, label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (playing) Traco.pause else Traco.play, null, Modifier.size(iconSize), tint = c.onAccent)
    }
}

@Composable
fun Badge(text: String, on: Boolean = true) {
    val c = SonivellTheme.colors
    val color = if (on) c.accent else c.textSecondary
    Box(Modifier.height(22.dp).border(1.dp, color, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Txt(text, 10, FontWeight.ExtraBold, color, letterSpacing = 0.8f)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier.height(38.dp)
            .border(1.dp, if (selected) c.accent else c.outline, shape)
            .hoverClick(shape, hover = if (selected) c.accent else c.surface, base = if (selected) c.accent else Color.Transparent, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = if (selected) c.onAccent else c.textTertiary)
        Txt(text, 13, FontWeight.ExtraBold, if (selected) c.onAccent else c.textTertiary)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt(text, 21, FontWeight.ExtraBold)
        trailing()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Txt(text, 15, FontWeight.Medium, SonivellTheme.colors.textSecondary, maxLines = 4, lineHeight = 22)
    }
}

val CircleBadgeShape = CircleShape

/**
 * Escolha estável da capa Onda: a cor sai do artista e o formato da onda do álbum. Mesma conta do
 * app Android (app/.../ui/components/OndaArt.kt): FNV-1a de 32 bits sobre o texto em UTF-8.
 */
object OndaSeed {
    const val CORES = 5
    const val ONDAS = 4

    fun fnv1a(texto: String): Long {
        var h = 0x811c9dc5L
        for (b in texto.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xff)
            h = (h * 0x01000193L) and 0xffffffffL
        }
        return h
    }

    fun normalizar(texto: String): String = texto.trim().lowercase(java.util.Locale.ROOT)

    fun cor(artista: String): Int = (fnv1a(normalizar(artista)) % CORES).toInt()

    fun onda(album: String): Int = (fnv1a(normalizar(album)) % ONDAS).toInt()
}

/** Limão, Turquesa, Coral, Lavanda e Âmbar: uma por artista. */
private val OndaCores = listOf(
    Color(0xFFD4EF55), Color(0xFF4FE0C8), Color(0xFFFF8A6B), Color(0xFFB79BFF), Color(0xFFFFC24D),
)

/** Os quatro formatos (A, B, C, D) num quadro de 200×200: início, depois duas cúbicas de 3 pontos. */
private val OndaFormas = listOf(
    floatArrayOf(26f, 106f, 46f, 50f, 78f, 50f, 100f, 100f, 122f, 150f, 154f, 150f, 174f, 94f),
    floatArrayOf(20f, 100f, 44f, 66f, 76f, 66f, 100f, 100f, 124f, 134f, 156f, 134f, 180f, 100f),
    floatArrayOf(34f, 112f, 50f, 30f, 80f, 30f, 100f, 100f, 120f, 170f, 150f, 170f, 166f, 88f),
    floatArrayOf(26f, 94f, 46f, 150f, 78f, 150f, 100f, 100f, 122f, 50f, 154f, 50f, 174f, 106f),
)

/**
 * Capa "Onda" para faixas sem arte, igual à do app Android: Simples (< 110dp ou redonda), Com nome
 * (≥ 110dp com [title]) e Completo (≥ 160dp sem título, dois cartões de vidro). No PC não há
 * desfoque: o vidro é só a translucidez.
 */
@Composable
fun OndaArt(albumKey: String, artistKey: String, title: String?, subtitle: String?, size: Dp, corner: Dp = 0.dp) {
    val dark = SonivellTheme.colors.isDark
    val tom = OndaCores[OndaSeed.cor(artistKey)]
    val forma = OndaFormas[OndaSeed.onda(albumKey)]
    val onda = if (dark) tom else lerp(tom, Color.Black, 0.35f)
    val redonda = corner * 2 >= size * 0.9f
    val simples = size < 110.dp || redonda
    val comNome = !simples && !title.isNullOrBlank()
    val completo = !simples && !comNome && size >= 160.dp
    val vidro = Color.White.copy(alpha = if (dark) 0.10f else 0.45f)
    // Fora do DrawScope: lá dentro, "size" é o tamanho em pixels da área de desenho.
    val raioFaixa = size * 0.086f
    val raioFrente = size * 0.148f
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawOndaFundo(dark, tom, if (completo) 0.18f else 0.30f) }
        when {
            comNome -> {
                Canvas(Modifier.fillMaxSize()) {
                    drawOnda(forma, Offset(this.size.width / 2f, this.size.height * 0.38f), this.size.minDimension * 0.51f, onda)
                }
                val margem = size * 0.055f
                val faixa = RoundedCornerShape(size * 0.086f)
                val texto = if (dark) Color.White else Color(0xFF1A1E16)
                Box(Modifier.fillMaxSize().padding(start = margem, end = margem, bottom = margem)) {
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .size(width = size - margem * 2, height = size * 0.30f)
                            .drawBehind { sombraSuave(raioFaixa.toPx()) }
                            .clip(faixa)
                            .background(vidro)
                            .drawBehind { brilhoTopo() }
                            .border(1.dp, Color.White.copy(alpha = if (dark) 0.28f else 0.80f), faixa)
                            .padding(horizontal = size * 0.06f),
                        verticalArrangement = Arrangement.spacedBy(size * 0.008f, Alignment.CenterVertically),
                    ) {
                        Text(
                            title!!,
                            style = TextStyle(fontFamily = Manrope, fontSize = (size.value * 0.09f).sp, lineHeight = (size.value * 0.11f).sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = texto,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                subtitle,
                                style = TextStyle(fontFamily = Manrope, fontSize = (size.value * 0.063f).sp, lineHeight = (size.value * 0.08f).sp),
                                fontWeight = FontWeight.Medium,
                                color = texto.copy(alpha = if (dark) 0.68f else 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            completo -> {
                val atrasForma = RoundedCornerShape(size * 0.125f)
                val frenteForma = RoundedCornerShape(size * 0.148f)
                Box(
                    Modifier
                        .offset(size * 0.27f, size * 0.1875f)
                        .size(size * 0.46f, size * 0.51f)
                        .clip(atrasForma)
                        .background(tom.copy(alpha = 0.70f))
                        .border(1.dp, Color.White.copy(alpha = 0.40f), atrasForma),
                )
                Box(
                    Modifier
                        .offset(size * 0.219f, size * 0.25f)
                        .size(size * 0.5625f)
                        .drawBehind { sombraSuave(raioFrente.toPx()) }
                        .clip(frenteForma)
                        .background(vidro)
                        .drawBehind { brilhoTopo() }
                        .border(1.dp, Color.White.copy(alpha = if (dark) 0.32f else 0.80f), frenteForma),
                ) {
                    Canvas(Modifier.fillMaxSize()) { drawOnda(forma, center, this.size.minDimension * 0.78f, onda) }
                }
            }
            else -> Canvas(Modifier.fillMaxSize()) { drawOnda(forma, center, this.size.minDimension * 0.78f, onda) }
        }
    }
}

/** Fundo em diagonal (160°) com brilho radial da cor do artista no canto superior esquerdo. */
private fun DrawScope.drawOndaFundo(dark: Boolean, tom: Color, brilho: Float) {
    val cores = if (dark) {
        listOf(Color(0xFF22262A), Color(0xFF15181A), Color(0xFF0B0D0E))
    } else {
        listOf(Color(0xFFF4F6EE), Color(0xFFEBEFE4), Color(0xFFE3E8DA))
    }
    val dx = size.width * 0.17f
    val dy = size.height * 0.47f
    drawRect(Brush.linearGradient(cores, start = Offset(center.x - dx, center.y - dy), end = Offset(center.x + dx, center.y + dy)))
    drawRect(
        Brush.radialGradient(
            listOf(tom.copy(alpha = brilho), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.10f),
            radius = maxOf(size.width, size.height) * 0.80f,
        ),
    )
}

/** Onda de um dos quatro formatos, num quadro de lado [quadro] centrado em [centro]. */
private fun DrawScope.drawOnda(forma: FloatArray, centro: Offset, quadro: Float, cor: Color) {
    val s = quadro / 200f
    val x0 = centro.x - quadro / 2f
    val y0 = centro.y - quadro / 2f
    fun x(i: Int) = x0 + forma[i] * s
    fun y(i: Int) = y0 + forma[i + 1] * s
    val path = Path().apply {
        moveTo(x(0), y(0))
        cubicTo(x(2), y(2), x(4), y(4), x(6), y(6))
        cubicTo(x(8), y(8), x(10), y(10), x(12), y(12))
    }
    drawPath(path, cor, style = Stroke(width = 26f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Sombra suave por fora do vidro: camadas crescentes de preto bem transparente. */
private fun DrawScope.sombraSuave(raio: Float) {
    val passo = size.minDimension * 0.012f
    for (k in 1..6) {
        val g = k * passo
        drawRoundRect(
            Color.Black.copy(alpha = 0.05f),
            topLeft = Offset(-g * 0.5f, g * 0.9f),
            size = Size(size.width + g, size.height),
            cornerRadius = CornerRadius(raio + g),
        )
    }
}

/** Reflexo de 2dp no topo do vidro. */
private fun DrawScope.brilhoTopo() {
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), endY = 2.dp.toPx()),
        size = Size(size.width, 2.dp.toPx()),
    )
}
