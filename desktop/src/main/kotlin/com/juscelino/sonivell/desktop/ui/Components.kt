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
import androidx.compose.ui.graphics.Shape
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
        val key = if (track.album.isBlank()) track.id.toString() else track.albumKey
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
private val artRings = listOf(Color(0xFF3B6457), Color(0xFF3A4A6B), Color(0xFF6B4A33), Color(0xFF5A3D66))
private val artCores = listOf(Color(0xFFC8F04A), Color(0xFF7CC4FF), Color(0xFFFF9F5A), Color(0xFFE8A6FF))

/** Capa da faixa: arte embutida, imagem da pasta ou uma capa gerada (círculos) quando não há. */
@Composable
fun AlbumArt(track: Track?, size: Dp, corner: Dp = 14.dp, modifier: Modifier = Modifier) {
    val bmp by produceState<ImageBitmap?>(null, track?.id, track?.albumKey) {
        value = if (track == null) null else ArtCache.get(track)
    }
    val seed = ((track?.albumKey?.hashCode() ?: 0) and 0x7fffffff) % 4
    Box(modifier.size(size).clip(RoundedCornerShape(corner)), contentAlignment = Alignment.Center) {
        val image = bmp
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(artBackgrounds[seed])
                val c = Offset(this.size.width / 2, this.size.height / 2)
                val m = this.size.minDimension
                drawCircle(artRings[seed], radius = m * 0.36f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = m * 0.03f))
                drawCircle(artRings[seed], radius = m * 0.22f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = m * 0.03f))
                drawCircle(artCores[seed], radius = m * 0.09f, center = c)
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
