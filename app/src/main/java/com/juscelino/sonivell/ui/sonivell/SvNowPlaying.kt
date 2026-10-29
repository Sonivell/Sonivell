package com.juscelino.sonivell.ui.sonivell

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ShuffleMode
import com.juscelino.sonivell.SleepTimer
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.playback.PlayerState
import com.juscelino.sonivell.playback.RepeatMode
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.nowplaying.AudioBars
import com.juscelino.sonivell.ui.nowplaying.NowPlayingActions
import com.juscelino.sonivell.ui.nowplaying.PlayReason
import com.juscelino.sonivell.ui.theme.NoraColors
import com.juscelino.sonivell.ui.theme.SonivellTheme

/** Player do tema Sonivell: capa circular com brilho, progresso em fio, controles em creme e o porquê da escolha. */
@Composable
fun NowPlayingScreenSonivell(
    track: Track,
    state: PlayerState,
    shuffleMode: ShuffleMode,
    smartAvailable: Boolean,
    isFavorite: Boolean,
    sourceLabel: String,
    actions: NowPlayingActions,
    sleepTimer: SleepTimer = SleepTimer(),
    reason: PlayReason = PlayReason.Chosen,
    visualizer: Boolean = false,
) {
    val icons = SonivellTheme.icons
    val durationMs = state.durationMs.takeIf { it > 0 } ?: track.durationMs
    Column(
        Modifier
            .fillMaxSize()
            .background(NoraColors.Bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SvIcon(icons.collapse, stringResource(R.string.cd_close_player), actions.onClose)
            Text(
                sourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = NoraColors.Muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            SvIcon(icons.moreHoriz, stringResource(R.string.cd_more_options), actions.onMore)
        }

        // Capa circular com um brilho dourado atrás.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(listOf(NoraColors.Gold.copy(alpha = 0.28f), Color.Transparent), radius = size.minDimension * 0.7f),
                            radius = size.minDimension * 0.7f,
                        )
                    },
            ) {
                AlbumArt(track, Modifier.fillMaxSize().border(1.dp, Color(0x30FFFFFF), CircleShape), corner = 500.dp, detailed = true)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = NoraColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NoraColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SvIcon(
                if (isFavorite) icons.liked else icons.like,
                stringResource(if (isFavorite) R.string.cd_unlike else R.string.cd_like),
                actions.onFavorite,
                tint = if (isFavorite) NoraColors.Cream else NoraColors.Text,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SvSeek(state.positionMs, durationMs, actions.onSeek)
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(state.positionMs), style = MaterialTheme.typography.labelMedium, color = NoraColors.Muted)
                Box(Modifier.weight(1f))
                Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = NoraColors.Muted)
            }
        }

        // Controles: aleatório, anterior, tocar (creme), próxima, repetir.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            val shuffleOn = shuffleMode != ShuffleMode.OFF
            SvIcon(
                if (shuffleMode == ShuffleMode.SMART && smartAvailable) icons.smart else icons.shuffle,
                stringResource(R.string.mode_shuffle),
                { actions.onSetShuffle(if (shuffleOn) ShuffleMode.OFF else if (smartAvailable) ShuffleMode.SMART else ShuffleMode.ON) },
                tint = if (shuffleOn) NoraColors.Cream else NoraColors.Muted,
            )
            SvIcon(icons.skipPrevious, stringResource(R.string.cd_previous), actions.onPrevious, size = 52.dp, iconSize = 28.dp)
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(NoraColors.Cream)
                    .clickable(role = Role.Button, onClick = actions.onTogglePlay),
                contentAlignment = Alignment.Center,
            ) { Icon(if (state.isPlaying) icons.pause else icons.play, stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play), Modifier.size(30.dp), tint = Color(0xFF1A1612)) }
            SvIcon(icons.skipNext, stringResource(R.string.cd_next), actions.onNext, size = 52.dp, iconSize = 28.dp)
            SvIcon(
                if (state.repeat == RepeatMode.ONE) icons.repeatOne else icons.repeat,
                stringResource(R.string.mode_repeat),
                actions.onRepeat,
                tint = if (state.repeat != RepeatMode.OFF) NoraColors.Cream else NoraColors.Muted,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SvIcon(icons.lyrics, stringResource(R.string.cd_lyrics), actions.onLyrics, tint = NoraColors.Muted)
            SvIcon(icons.queue, stringResource(R.string.queue_title), actions.onQueue, tint = NoraColors.Muted)
            SvIcon(
                if (sleepTimer.active) icons.timer else icons.sleep,
                stringResource(R.string.sleep_timer),
                actions.onSleepTimer,
                tint = if (sleepTimer.active) NoraColors.Cream else NoraColors.Muted,
            )
        }

        // "Por que tocou": o motivo da escolha, no cartão do modelo.
        val why = when (reason) {
            is PlayReason.Smart -> stringResource(R.string.why_smart, reason.seed.title, reason.seed.artist)
            PlayReason.MixStart -> stringResource(R.string.why_seed)
            PlayReason.Shuffle -> stringResource(R.string.why_shuffle, sourceLabel)
            PlayReason.Chosen -> stringResource(R.string.why_source, sourceLabel)
        }
        SvCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.sv_why_title), style = MaterialTheme.typography.titleSmall, color = NoraColors.Text)
                    Text(why, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = NoraColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (visualizer) {
                    AudioBars(playing = state.isPlaying, color = NoraColors.Gold, modifier = Modifier.size(width = 64.dp, height = 28.dp))
                }
            }
        }
    }
}

@Composable
private fun SvIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = NoraColors.Text,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(iconSize), tint = tint) }
}

/** Progresso em fio: toque ou arraste para buscar. */
@Composable
private fun SvSeek(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val seekLabel = stringResource(R.string.cd_seek)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { contentDescription = seekLabel }
            .pointerInput(durationMs) {
                detectTapGestures { o -> if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong().coerceIn(0L, durationMs)) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                )
            },
    ) {
        val y = size.height / 2f
        val w = size.width
        drawLine(Color(0x25FFFFFF), Offset(0f, y), Offset(w, y), strokeWidth = 2.dp.toPx())
        drawLine(NoraColors.Cream, Offset(0f, y), Offset(w * fraction, y), strokeWidth = 3.dp.toPx())
        drawCircle(NoraColors.Cream, radius = 5.dp.toPx(), center = Offset(w * fraction, y))
    }
}
