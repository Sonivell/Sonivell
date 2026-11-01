package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.desktop.Controller
import com.juscelino.sonivell.desktop.Repeat
import com.juscelino.sonivell.desktop.formatTime
import com.juscelino.sonivell.desktop.str

/** Player completo: capa grande, onda para buscar, modos e a fila ao lado. */
@Composable
fun NowPlayingScreen(ctl: Controller, nav: Nav, actions: Actions) {
    val c = SonivellTheme.colors
    val p by ctl.player.collectAsState()
    val favorites by ctl.favorites.collectAsState()
    val t = p.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().cardSurface(RoundedCornerShape(22.dp), c.surfaceLow, elevation = 10.dp)) {
        // a capa encolhe quando a janela é baixa, para os controles nunca ficarem cobertos
        val artSize = (maxHeight - 470.dp).coerceIn(150.dp, 340.dp)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Traco.collapse, str("cd_close_player"), { nav.go(Route.ForYou) }, size = 44.dp, outlined = true)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(str("playing_from"), 11, FontWeight.Bold, c.textSecondary)
                    Txt(p.sourceLabel.ifBlank { str("nav_library") }, 13, FontWeight.ExtraBold)
                }
                Box(Modifier.size(44.dp))
            }
            AlbumArt(t, artSize, 34.dp)
            Row(Modifier.width(420.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Txt(t?.title ?: "", 28, FontWeight.ExtraBold, letterSpacing = -0.8f)
                    Txt(listOfNotNull(t?.artist?.ifBlank { null }, t?.album?.ifBlank { null }).joinToString(" · "), 15, FontWeight.Medium, c.textSecondary)
                }
                if (t != null) {
                    val liked = t.id in favorites
                    IconBtn(if (liked) Traco.liked else Traco.like, str(if (liked) "cd_unlike" else "cd_like"), { ctl.toggleFavorite(t) }, size = 48.dp, iconSize = 22.dp, active = liked)
                }
            }
            Column(Modifier.width(420.dp)) {
                if (t != null) WaveSeekBar(t.id, p.positionMs, t.durationMs, { ctl.seek(it) }, Modifier.fillMaxWidth(), bars = 64, height = 40.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt(formatTime(p.positionMs), 12, FontWeight.Bold, c.textSecondary)
                    if (t != null && t.format.isNotBlank()) Badge(t.format, false)
                    Txt(formatTime(t?.durationMs ?: 0L), 12, FontWeight.Bold, c.textSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Traco.skipPrevious, str("cd_previous"), { ctl.previous() }, size = 52.dp, iconSize = 28.dp)
                PlaySquircle(p.playing, { ctl.togglePlay() }, size = 76.dp, corner = 26.dp, iconSize = 30.dp)
                IconBtn(Traco.skipNext, str("cd_next"), { ctl.next() }, size = 52.dp, iconSize = 28.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ctl.smart.available) Pill(str("desktop_smart_label"), p.smart, { ctl.toggleSmart() }, Traco.shuffle)
                Pill(str("mode_shuffle"), p.shuffle, { ctl.toggleShuffle() }, Traco.shuffle)
                Pill(
                    str(if (p.repeat == Repeat.One) "mode_repeat_one" else "mode_repeat"), p.repeat != Repeat.Off, { ctl.cycleRepeat() },
                    if (p.repeat == Repeat.One) Traco.repeatOne else Traco.repeat,
                )
            }
        }
        }
        QueuePanel(ctl, Modifier.width(380.dp))
    }
}
