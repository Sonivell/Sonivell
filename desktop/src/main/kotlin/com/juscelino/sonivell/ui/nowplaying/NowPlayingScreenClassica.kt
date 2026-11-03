package com.juscelino.sonivell.ui.nowplaying

import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.compat.stringResource
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
import com.juscelino.sonivell.ShuffleMode
import com.juscelino.sonivell.SleepTimer
import com.juscelino.sonivell.compat.AudioManager
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import com.juscelino.sonivell.compat.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
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
import com.juscelino.sonivell.ui.theme.cardSurface


/**
 * Player do estilo Clássico: sóbrio, com a estrutura comum a todos os estilos. Linha de progresso
 * com botão (o traço do Clássico), "MP3" como texto entre os tempos, play no lima único e sem anel,
 * Smart como interruptor, volume com uma trilha e um botão e o rodapé Este telefone · Letra · Fila.
 */
@Composable
fun NowPlayingScreenClassica(
    track: Track,
    state: PlayerState,
    shuffleMode: ShuffleMode,
    smartAvailable: Boolean,
    isFavorite: Boolean,
    sourceLabel: String,
    actions: NowPlayingActions,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val durationMs = state.durationMs.takeIf { it > 0 } ?: track.durationMs
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Topo
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(icons.collapse, stringResource(R.string.cd_close_player), actions.onClose, background = c.surface)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.playing_from).uppercase(), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Text(sourceLabel, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(icons.moreHoriz, stringResource(R.string.cd_more_options), actions.onMore, background = c.surface)
        }

        // Capa
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AlbumArt(
                track,
                Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f),
                corner = 16.dp,
                detailed = true,
            )
        }

        // Título (até duas linhas) + curtir
        Row(verticalAlignment = Alignment.Bottom) {
            PlayerTitle(track, Modifier.weight(1f), titleSize = 22.sp)
            IconBtn(
                if (isFavorite) icons.liked else icons.like,
                stringResource(if (isFavorite) R.string.cd_unlike else R.string.cd_like),
                actions.onFavorite,
                tint = if (isFavorite) c.accent else c.textSecondary,
                iconSize = 24.dp,
            )
        }

        // Progresso: linha de 4 dp com botão, preenchida no lima; "MP3" como texto; tempo restante.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LineSeekBar(state.positionMs, durationMs, actions.onSeek, fill = c.accent)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeText(formatDuration(state.positionMs))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { FormatText(track) }
                TimeText(remainingLabel(state.positionMs, durationMs))
            }
        }

        // Controles padrão: embaralhar · anterior · pausa · próxima · repetir
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerModeToggle(icons.shuffle, stringResource(R.string.mode_shuffle), shuffleMode == ShuffleMode.ON, onClick = {
                actions.onSetShuffle(if (shuffleMode == ShuffleMode.ON) ShuffleMode.OFF else ShuffleMode.ON)
            })
            IconBtn(icons.skipPrevious, stringResource(R.string.cd_previous), actions.onPrevious, size = 56.dp, iconSize = 34.dp)
            PlayCircle(state.isPlaying, actions.onTogglePlay, size = 72.dp, iconSize = 32.dp)
            IconBtn(icons.skipNext, stringResource(R.string.cd_next), actions.onNext, size = 56.dp, iconSize = 34.dp)
            PlayerModeToggle(
                if (state.repeat == RepeatMode.ONE) icons.repeatOne else icons.repeat,
                stringResource(
                    when (state.repeat) {
                        RepeatMode.OFF -> R.string.cd_repeat_off
                        RepeatMode.ALL -> R.string.cd_repeat_all
                        RepeatMode.ONE -> R.string.cd_repeat_one
                    }
                ),
                state.repeat != RepeatMode.OFF,
                onClick = actions.onRepeat,
            )
        }

        if (smartAvailable) {
            SmartQueueSwitch(
                on = shuffleMode == ShuffleMode.SMART,
                onToggle = { on -> actions.onSetShuffle(if (on) ShuffleMode.SMART else ShuffleMode.OFF) },
            )
        }

        VolumeRow()

        // Rodapé com rótulos: Este telefone · Letra · Fila
        PlayerFooter(
            onLyrics = actions.onLyrics,
            onQueue = actions.onQueue,
            modifier = Modifier.cardSurface(RoundedCornerShape(16.dp), c.surfaceLow).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = SonivellTheme.colors.textSecondary)
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
        Icon(SonivellTheme.icons.volumeDown, null, Modifier.size(20.dp), tint = c.textSecondary)
        // Uma trilha e um botão; preenche em branco, para o lima ficar só no que toca.
        LineSeekBar(
            positionMs = volume.toLong(),
            durationMs = max.toLong(),
            onSeek = { v ->
                volume = v.toInt().coerceIn(0, max)
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) }
            },
            modifier = Modifier.weight(1f),
            label = volumeLabel,
        )
        Icon(SonivellTheme.icons.volumeUp, null, Modifier.size(20.dp), tint = c.textSecondary)
    }
}
