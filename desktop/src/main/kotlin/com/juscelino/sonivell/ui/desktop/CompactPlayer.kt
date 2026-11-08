package com.juscelino.sonivell.ui.desktop

import com.juscelino.sonivell.ui.components.livePositionMs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.MainViewModel
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ShuffleMode
import com.juscelino.sonivell.compat.stringResource
import com.juscelino.sonivell.data.UiStyle
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.playback.PlayerVolume
import com.juscelino.sonivell.playback.RepeatMode
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.WaveSeekBar
import com.juscelino.sonivell.ui.nowplaying.LineSeekBar
import com.juscelino.sonivell.ui.nowplaying.PlayCircle
import com.juscelino.sonivell.ui.nowplaying.remainingLabel
import com.juscelino.sonivell.ui.theme.SonivellTheme

/**
 * Modo compacto (estilo "janela pequena" do Windows Media Player): só a capa, o progresso e os
 * controles. Entra pelo botão da barra do player ou Ctrl+M; sai pelo botão sobre a capa, Ctrl+M
 * ou maximizando a janela. O progresso segue o estilo (onda no Sonivell e no Ondas, linha nos outros).
 */
@Composable
fun CompactPlayer(vm: MainViewModel) {
    val c = SonivellTheme.colors
    val p by vm.playerState.collectAsState()
    val t by vm.currentTrack.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val shuffleMode by vm.shuffleMode.collectAsState()
    val volume by PlayerVolume.flow.collectAsState()
    var lastVolume by remember { mutableFloatStateOf(if (volume > 0f) volume else 0.8f) }
    val track = t

    Column(Modifier.fillMaxSize().background(c.background)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))) {
            // A capa preenche a área toda (corte central), como no player do Windows 7.
            AlbumArt(track, Modifier.size(maxOf(maxWidth, maxHeight)).align(Alignment.Center), corner = 0.dp)
            Box(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(c.background.copy(alpha = 0.92f), Color.Transparent)))
                    .padding(start = 18.dp, end = 60.dp, top = 16.dp, bottom = 44.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(track?.title ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track?.artist ?: stringResource(R.string.queue_empty), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(12.dp)).background(c.background.copy(alpha = 0.7f))) {
                IconBtn(SonivellTheme.icons.chevron, stringResource(R.string.desktop_exit_compact), { DesktopWindow.setCompact(false) }, size = 38.dp, iconSize = 18.dp, tint = c.accent)
            }
        }

        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (track != null) {
                WaveSeekBar(track.id, p.livePositionMs, track.durationMs, vm.player::seekTo)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(p.livePositionMs), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Text(if (track != null) remainingLabel(p.livePositionMs, track.durationMs) else "", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val liked = track != null && track.id in favorites
            IconBtn(if (liked) SonivellTheme.icons.liked else SonivellTheme.icons.like, stringResource(if (liked) R.string.cd_unlike else R.string.cd_like), { track?.let { vm.toggleFavorite(it.id) } }, tint = if (liked) c.accent else c.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val shuffleOn = shuffleMode == ShuffleMode.ON
                IconBtn(SonivellTheme.icons.shuffle, stringResource(if (shuffleOn) R.string.cd_shuffle_on else R.string.cd_shuffle_off), { vm.setShuffleMode(if (shuffleOn) ShuffleMode.OFF else ShuffleMode.ON) }, tint = if (shuffleOn) c.accent else c.textSecondary)
                IconBtn(SonivellTheme.icons.skipPrevious, stringResource(R.string.cd_previous), { vm.player.previous() }, iconSize = 24.dp)
                PlayCircle(p.isPlaying, vm.player::togglePlay, size = 56.dp, background = c.accent, tint = c.onAccent)
                IconBtn(SonivellTheme.icons.skipNext, stringResource(R.string.cd_next), { vm.player.next() }, iconSize = 24.dp)
                IconBtn(
                    if (p.repeat == RepeatMode.ONE) SonivellTheme.icons.repeatOne else SonivellTheme.icons.repeat,
                    stringResource(when (p.repeat) { RepeatMode.OFF -> R.string.cd_repeat_off; RepeatMode.ALL -> R.string.cd_repeat_all; RepeatMode.ONE -> R.string.cd_repeat_one }),
                    vm.player::cycleRepeat,
                    tint = if (p.repeat != RepeatMode.OFF) c.accent else c.textSecondary,
                )
            }
            val muted = volume <= 0f
            IconBtn(
                SonivellTheme.icons.volumeUp,
                stringResource(if (muted) R.string.desktop_unmute else R.string.desktop_mute),
                { if (muted) PlayerVolume.set(lastVolume) else { lastVolume = volume; PlayerVolume.set(0f) } },
                tint = if (muted) c.textSecondary.copy(alpha = 0.5f) else c.textSecondary,
            )
        }
    }
}
