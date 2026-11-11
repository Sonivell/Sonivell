package com.juscelino.sonivell.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.LocalInDock
import com.juscelino.sonivell.ui.theme.glassSurface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Alturas (0..1) da onda de uma faixa. É um desenho estável por faixa (a mesma música tem
 * sempre a mesma onda), não a amplitude real do áudio.
 */
fun waveShape(seed: Long, bars: Int): FloatArray {
    val a = (seed % 997).toFloat() * 0.37f
    val b = (seed % 389).toFloat() * 0.11f
    return FloatArray(bars) { i ->
        abs(sin(i * 1.7f + a) * cos(i * 0.31f + b)) * 0.85f + 0.15f
    }
}

/** Onda em barras: a parte já tocada na cor de destaque, o resto apagado. */
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

/** Barra de progresso do player Ondas: a onda da faixa, com toque e arrasto para buscar. */
@Composable
fun WaveSeekBar(
    seed: Long,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seekLabel = stringResource(R.string.cd_seek)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics { contentDescription = seekLabel }
            .pointerInput(durationMs) {
                detectTapGestures { o ->
                    if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragFraction = (o.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Waveform(seed, fraction, Modifier.fillMaxWidth().height(40.dp), bars = 56)
    }
}

/** Botão de tocar do Ondas: quadrado arredondado na cor de destaque. */
@Composable
fun PlaySquircle(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    @Suppress("UNUSED_PARAMETER") corner: Dp = 14.dp,
    iconSize: Dp = 18.dp,
    contentDescription: String? = null,
    background: Color = SonivellTheme.colors.accent,
    tint: Color = SonivellTheme.colors.onAccent,
) {
    val icons = SonivellTheme.icons
    val label = contentDescription ?: stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)
    Box(
        modifier
            .size(size)
            // Play sempre circular, em todo o app (proposta visual); [corner] ficou para os chamadores.
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { this.contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (isPlaying) icons.pause else icons.play, null, Modifier.size(iconSize), tint = tint)
    }
}

/**
 * Mini player: capa, título, artista e play circular. Com [wave] (estilo Ondas) a onda de 16 dp é o
 * progresso; sem ela (Sonivell, Essencial), uma linha de 3 dp na base do cartão. Tocar abre o player.
 */
@Composable
fun MiniPlayerOndas(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    wave: Boolean = true,
) {
    val c = SonivellTheme.colors
    val openPlayer = stringResource(R.string.cd_open_player)
    val shape = RoundedCornerShape(20.dp)
    // No dock a superfície é do próprio dock (mini player e abas num bloco só).
    val docked = LocalInDock.current
    Column(
        modifier
            .padding(horizontal = if (docked) 0.dp else 12.dp)
            .fillMaxWidth()
            // Vidro: o mini player também em vidro.
            .then(
                when {
                    docked -> Modifier
                    SonivellTheme.glass -> Modifier.glassSurface(shape, c.surface, c.outline, elevation = 12.dp)
                    else -> Modifier.clip(shape).background(c.surface)
                },
            )
            .clickable(onClickLabel = openPlayer, onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlbumArt(track, Modifier.size(44.dp), corner = 12.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (wave) {
                    Waveform(track.id, progress, Modifier.fillMaxWidth().height(16.dp), bars = 36, barWidth = 2.dp)
                } else {
                    Text(track.artist, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // No dock o play é neutro e menor: a cor de destaque fica para a ação principal de cada tela.
            if (docked) PlaySquircle(isPlaying, onTogglePlay, size = 40.dp, iconSize = 16.dp, background = c.textPrimary, tint = c.background)
            else PlaySquircle(isPlaying, onTogglePlay)
        }
        if (!wave) {
            Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.track)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(c.accent))
            }
            Box(Modifier.height(6.dp))
        }
    }
}
