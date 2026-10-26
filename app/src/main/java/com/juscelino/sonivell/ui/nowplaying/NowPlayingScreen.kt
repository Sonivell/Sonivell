package com.juscelino.sonivell.ui.nowplaying

import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.juscelino.sonivell.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.AutoAwesome
import com.juscelino.sonivell.ShuffleMode
import com.juscelino.sonivell.SleepTimer
import android.media.AudioManager
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.playback.PlayerState
import com.juscelino.sonivell.playback.RepeatMode
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.theme.SonivellTheme

data class NowPlayingActions(
    val onClose: () -> Unit,
    val onMore: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onFavorite: () -> Unit,
    val onLyrics: () -> Unit,
    val onQueue: () -> Unit,
    val onSleepTimer: () -> Unit = {},
)

@Composable
fun NowPlayingScreen(
    track: Track,
    state: PlayerState,
    shuffleMode: ShuffleMode,
    smartAvailable: Boolean,
    isFavorite: Boolean,
    sourceLabel: String,
    actions: NowPlayingActions,
    sleepTimer: SleepTimer = SleepTimer(),
) {
    val c = SonivellTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Topo
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.cd_close_player), actions.onClose, background = c.surface)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.playing_from).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = c.textSecondary)
                Text(sourceLabel, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sleepTimer.active) SleepChip(sleepTimer, actions.onSleepTimer)
            }
            IconBtn(Icons.Rounded.MoreHoriz, stringResource(R.string.cd_more_options), actions.onMore, background = c.surface)
        }

        // Capa
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AlbumArt(
                track,
                Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f),
                corner = 28.dp,
                detailed = true,
            )
        }

        // Título + curtir
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconBtn(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                stringResource(if (isFavorite) R.string.cd_unlike else R.string.cd_like),
                actions.onFavorite,
                tint = if (isFavorite) c.accent else c.textSecondary,
                iconSize = 24.dp,
            )
        }

        // Progresso
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs.takeIf { it > 0 } ?: track.durationMs,
                onSeek = actions.onSeek,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeText(formatDuration(state.positionMs))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    track.formatLabel?.let { fmt ->
                        Text(
                            fmt,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textTertiary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.surface)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                TimeText(formatDuration(state.durationMs.takeIf { it > 0 } ?: track.durationMs))
            }
        }

        // Controles
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShuffleButton(shuffleMode, smartAvailable, actions.onShuffle)
            IconBtn(Icons.Rounded.SkipPrevious, stringResource(R.string.cd_previous), actions.onPrevious, size = 56.dp, iconSize = 34.dp)
            IconBtn(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                actions.onTogglePlay,
                size = 76.dp,
                iconSize = 36.dp,
                background = c.accent,
                tint = c.onAccent,
            )
            IconBtn(Icons.Rounded.SkipNext, stringResource(R.string.cd_next), actions.onNext, size = 56.dp, iconSize = 34.dp)
            IconBtn(
                if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                stringResource(
                    when (state.repeat) {
                        RepeatMode.OFF -> R.string.cd_repeat_off
                        RepeatMode.ALL -> R.string.cd_repeat_all
                        RepeatMode.ONE -> R.string.cd_repeat_one
                    }
                ),
                actions.onRepeat,
                tint = if (state.repeat == RepeatMode.OFF) c.textSecondary else c.accent,
            )
        }

        VolumeRow()

        // Barra inferior
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(c.surfaceLow)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(44.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(18.dp), tint = c.textTertiary)
                Text(stringResource(R.string.this_phone), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.textTertiary)
            }
            IconBtn(Icons.Rounded.Lyrics, stringResource(R.string.cd_lyrics), actions.onLyrics, tint = c.textTertiary, iconSize = 20.dp)
            Spacer(Modifier.size(4.dp))
            val openQueue = stringResource(R.string.cd_open_queue)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surfaceHigh)
                    .clickable(onClickLabel = openQueue, onClick = actions.onQueue)
                    .semantics { contentDescription = openQueue },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(20.dp), tint = c.textPrimary)
            }
        }
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = SonivellTheme.colors.textSecondary)
}

/** Barra fina do mockup (4 dp) com bolinha; aceita toque e arrasto. */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val c = SonivellTheme.colors
    val seekLabel = stringResource(R.string.cd_seek)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
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
        contentAlignment = Alignment.CenterStart,
    ) {
        val width = maxWidth
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.track))
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.textPrimary))
        val knob = if (dragFraction != null) 18.dp else 14.dp
        Box(
            Modifier
                .offset(x = (width * fraction) - knob / 2)
                .size(knob)
                .clip(CircleShape)
                .background(c.textPrimary)
        )
    }
}

/** Aleatório em três estados: desligado, comum e SMART (ícone de brilho + selo). */
@Composable
private fun ShuffleButton(mode: ShuffleMode, smartAvailable: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val label = stringResource(
        when (mode) {
            ShuffleMode.OFF -> R.string.cd_shuffle_on
            ShuffleMode.ON -> if (smartAvailable) R.string.cd_shuffle_smart else R.string.cd_shuffle_off
            ShuffleMode.SMART -> R.string.cd_smart_off
        }
    )
    Box(contentAlignment = Alignment.Center) {
        IconBtn(
            if (mode == ShuffleMode.SMART) Icons.Rounded.AutoAwesome else Icons.Rounded.Shuffle,
            label,
            onClick,
            tint = if (mode == ShuffleMode.OFF) c.textSecondary else c.accent,
        )
        if (mode == ShuffleMode.SMART) {
            Text(
                stringResource(R.string.source_smart),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.6.sp),
                color = c.onAccent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.accent)
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

/** Contagem regressiva do temporizador (toque para mudar ou cancelar). */
@Composable
private fun SleepChip(timer: SleepTimer, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val label = when {
        timer.endOfTrack -> stringResource(R.string.timer_end_of_track_short)
        timer.endsAtMs != null -> formatDuration((timer.endsAtMs - now).coerceAtLeast(0))
        else -> ""
    }
    Row(
        Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface)
            .clickable(onClickLabel = stringResource(R.string.sleep_timer), onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.Bedtime, null, Modifier.size(12.dp), tint = c.accent)
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.accent)
    }
}

/**
 * Volume da música do aparelho (o mesmo dos botões físicos). Acompanha mudanças feitas
 * pelos botões enquanto a tela está aberta.
 */
@Composable
private fun VolumeRow() {
    val c = SonivellTheme.colors
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val volumeLabel = stringResource(R.string.cd_volume)
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    LaunchedEffect(Unit) {
        while (true) {
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            delay(1_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, Modifier.size(20.dp), tint = c.textSecondary)
        Slider(
            value = volume.toFloat(),
            onValueChange = {
                volume = it.roundToInt()
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) }
            },
            valueRange = 0f..max.toFloat(),
            modifier = Modifier.weight(1f).semantics { contentDescription = volumeLabel },
            colors = SliderDefaults.colors(
                thumbColor = c.textPrimary,
                activeTrackColor = c.textPrimary,
                inactiveTrackColor = c.track,
            ),
        )
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(20.dp), tint = c.textSecondary)
    }
}
