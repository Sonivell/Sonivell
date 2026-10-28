package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.desktop.Controller
import com.juscelino.sonivell.desktop.Repeat
import com.juscelino.sonivell.desktop.formatTime
import com.juscelino.sonivell.desktop.str

/**
 * Modo compacto (estilo "janela pequena" do Windows Media Player): só a capa, a onda de
 * progresso e os controles. Entra pelo botão da barra do player ou Ctrl+M; sai pelo botão
 * de expandir sobre a capa, Ctrl+M ou maximizando a janela.
 */
@Composable
fun CompactPlayer(ctl: Controller) {
    val c = SonivellTheme.colors
    val p by ctl.player.collectAsState()
    val favorites by ctl.favorites.collectAsState()
    val t = p.current
    var lastVolume by remember { mutableStateOf(if (p.volume > 0f) p.volume else 0.8f) }

    Column(Modifier.fillMaxSize().background(c.background)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))) {
            // A capa preenche a área toda (corte central), como no player do Windows 7.
            AlbumArt(t, maxOf(maxWidth, maxHeight), 0.dp, Modifier.align(Alignment.Center))
            Box(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(c.background.copy(alpha = 0.92f), Color.Transparent)))
                    .padding(start = 18.dp, end = 60.dp, top = 16.dp, bottom = 44.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Txt(t?.title ?: str("app_name"), 18, FontWeight.ExtraBold, maxLines = 2, lineHeight = 22)
                    Txt(t?.artist ?: str("queue_empty"), 13, FontWeight.SemiBold, c.textTertiary, maxLines = 2)
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(12.dp)).background(c.background.copy(alpha = 0.7f))) {
                IconBtn(Traco.expand, str("desktop_exit_compact"), { ctl.setCompact(false) }, size = 38.dp, iconSize = 18.dp, tint = c.accent)
            }
            if (p.smart) {
                Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).clip(RoundedCornerShape(8.dp)).background(c.background.copy(alpha = 0.8f))) {
                    Badge("SMART")
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (t != null) WaveSeekBar(t.id, p.positionMs, t.durationMs, { ctl.seek(it) }, Modifier.fillMaxWidth(), bars = 60, height = 26.dp)
            else Waveform(0L, 0f, Modifier.fillMaxWidth().padding(vertical = 5.dp), bars = 60)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(formatTime(p.positionMs), 11, FontWeight.Bold, c.textSecondary)
                Txt(formatTime(t?.durationMs ?: 0L), 11, FontWeight.Bold, c.textSecondary)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val liked = t != null && t.id in favorites
            IconBtn(if (liked) Traco.liked else Traco.like, str(if (liked) "cd_unlike" else "cd_like"), { t?.let { ctl.toggleFavorite(it) } }, active = liked, tint = c.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconBtn(Traco.shuffle, str(if (p.shuffle) "cd_shuffle_on" else "cd_shuffle_off"), { ctl.toggleShuffle() }, active = p.shuffle, tint = c.textSecondary)
                IconBtn(Traco.skipPrevious, str("cd_previous"), { ctl.previous() }, iconSize = 24.dp)
                PlaySquircle(p.playing, { ctl.togglePlay() }, size = 56.dp, corner = 18.dp, iconSize = 24.dp)
                IconBtn(Traco.skipNext, str("cd_next"), { ctl.next() }, iconSize = 24.dp)
                IconBtn(
                    if (p.repeat == Repeat.One) Traco.repeatOne else Traco.repeat,
                    str(when (p.repeat) { Repeat.Off -> "cd_repeat_off"; Repeat.All -> "cd_repeat_all"; Repeat.One -> "cd_repeat_one" }),
                    { ctl.cycleRepeat() }, active = p.repeat != Repeat.Off, tint = c.textSecondary,
                )
            }
            val muted = p.volume <= 0f
            IconBtn(
                if (muted) Traco.volumeOff else Traco.volumeUp,
                str(if (muted) "desktop_unmute" else "desktop_mute"),
                {
                    if (muted) ctl.setVolume(lastVolume) else { lastVolume = p.volume; ctl.setVolume(0f) }
                },
                tint = c.textSecondary,
            )
        }
    }
}
