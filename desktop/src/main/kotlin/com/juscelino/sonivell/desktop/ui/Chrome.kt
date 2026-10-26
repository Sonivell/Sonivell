package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.desktop.Controller
import com.juscelino.sonivell.desktop.PlayerState
import com.juscelino.sonivell.desktop.Repeat
import com.juscelino.sonivell.desktop.Track
import com.juscelino.sonivell.desktop.formatTime
import com.juscelino.sonivell.desktop.str

enum class Route { ForYou, Search, Library, Settings, NowPlaying }
enum class LibTab { All, Albums, Artists, Genres, Folders }

data class Detail(val title: String, val subtitle: String, val tracks: List<Track>, val artTrack: Track?, val kind: String)

/** Estado de navegação da janela. */
class Nav {
    var route by mutableStateOf(Route.ForYou)
    var detail by mutableStateOf<Detail?>(null)
    var tab by mutableStateOf(LibTab.All)
    var showQueue by mutableStateOf(true)
    var query by mutableStateOf("")

    fun go(r: Route) { route = r; detail = null }
}

@Composable
fun Sidebar(nav: Nav, ctl: Controller, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val lib by ctl.library.collectAsState()
    Column(
        modifier.width(236.dp).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(c.surfaceLow).padding(horizontal = 10.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 2.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(Traco.forYouActive, null, Modifier.size(18.dp), tint = c.onAccent)
            }
            Txt(str("app_name"), 19, FontWeight.ExtraBold)
        }
        NavItem(Traco.forYou, Traco.forYouActive, str("nav_home"), nav.route == Route.ForYou) { nav.go(Route.ForYou) }
        NavItem(Traco.search, Traco.searchActive, str("nav_search"), nav.route == Route.Search) { nav.go(Route.Search) }
        NavItem(Traco.library, Traco.libraryActive, str("nav_library"), nav.route == Route.Library) { nav.go(Route.Library) }
        Spacer(Modifier.weight(1f))
        if (lib.scanning) {
            val p = lib.progress
            Txt(
                if (p != null) str("desktop_scanning", p.first, p.second) else str("desktop_scanning_plain"),
                12, FontWeight.SemiBold, c.textSecondary, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        NavItem(Traco.settings, Traco.settings, str("settings"), nav.route == Route.Settings) { nav.go(Route.Settings) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, active: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier.fillMaxWidth().height(42.dp).hoverClick(RoundedCornerShape(13.dp), hover = c.surfaceHigh, base = if (selected) c.surfaceHigh else androidx.compose.ui.graphics.Color.Transparent, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (selected) active else icon, null, Modifier.size(20.dp), tint = if (selected) c.accent else c.textTertiary)
        Txt(label, 14, if (selected) FontWeight.ExtraBold else FontWeight.Bold, if (selected) c.accent else c.textTertiary)
    }
}

@Composable
fun VolumeSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    Box(
        modifier.height(20.dp).pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.track))
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
    }
}

@Composable
fun PlayerBar(ctl: Controller, nav: Nav, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val p by ctl.player.collectAsState()
    val favorites by ctl.favorites.collectAsState()
    val t = p.current
    Row(
        modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(20.dp)).background(c.surface).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.width(320.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.hoverClick(RoundedCornerShape(16.dp), label = str("desktop_open_player"), onClick = { nav.route = Route.NowPlaying; nav.detail = null })) {
                AlbumArt(t, 60.dp, 16.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(t?.title ?: "", 15, FontWeight.ExtraBold)
                Txt(t?.artist ?: "", 13, FontWeight.Medium, c.textSecondary)
            }
            if (t != null) {
                val liked = t.id in favorites
                IconBtn(if (liked) Traco.liked else Traco.like, str(if (liked) "cd_unlike" else "cd_like"), { ctl.toggleFavorite(t) }, active = liked, iconSize = 20.dp)
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBtn(Traco.shuffle, str(if (p.shuffle) "cd_shuffle_on" else "cd_shuffle_off"), { ctl.toggleShuffle() }, active = p.shuffle, outlined = false)
                IconBtn(Traco.skipPrevious, str("cd_previous"), { ctl.previous() }, iconSize = 24.dp)
                PlaySquircle(p.playing, { ctl.togglePlay() })
                IconBtn(Traco.skipNext, str("cd_next"), { ctl.next() }, iconSize = 24.dp)
                IconBtn(
                    if (p.repeat == Repeat.One) Traco.repeatOne else Traco.repeat,
                    str(when (p.repeat) { Repeat.Off -> "cd_repeat_off"; Repeat.All -> "cd_repeat_all"; Repeat.One -> "cd_repeat_one" }),
                    { ctl.cycleRepeat() }, active = p.repeat != Repeat.Off,
                )
            }
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(formatTime(p.positionMs), 12, FontWeight.Bold, c.textSecondary)
                if (t != null) WaveSeekBar(t.id, p.positionMs, t.durationMs, { ctl.seek(it) }, Modifier.weight(1f), bars = 100, height = 24.dp)
                else Spacer(Modifier.weight(1f))
                Txt(formatTime(t?.durationMs ?: 0L), 12, FontWeight.Bold, c.textSecondary)
            }
        }
        Row(Modifier.width(330.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            if (t != null && t.format.isNotBlank()) Badge(t.format)
            Icon(Traco.volumeUp, str("cd_volume"), Modifier.size(20.dp), tint = c.textTertiary)
            VolumeSlider(p.volume, { ctl.setVolume(it) }, Modifier.width(90.dp))
            IconBtn(Traco.queue, str("desktop_show_queue"), { nav.showQueue = !nav.showQueue }, active = nav.showQueue)
        }
    }
}

/** Painel da fila: a faixa atual e as próximas. */
@Composable
fun QueuePanel(ctl: Controller, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val p by ctl.player.collectAsState()
    val favorites by ctl.favorites.collectAsState()
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(c.surfaceLow).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt(str("tab_up_next"), 15, FontWeight.ExtraBold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        if (p.current == null) {
            Txt(str("queue_empty"), 13, FontWeight.Medium, c.textSecondary, maxLines = 4, lineHeight = 19, modifier = Modifier.padding(4.dp))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(p.queue.drop(maxOf(p.index, 0))) { i, track ->
                val position = maxOf(p.index, 0) + i
                QueueRow(track, current = i == 0, liked = track.id in favorites, onClick = { if (i != 0) ctl.playQueueAt(position) }, onRemove = if (i == 0) null else ({ ctl.removeFromQueue(position) }))
            }
        }
    }
}

@Composable
private fun QueueRow(track: Track, current: Boolean, liked: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    val c = SonivellTheme.colors
    Row(
        Modifier.fillMaxWidth().height(56.dp).hoverClick(RoundedCornerShape(14.dp), hover = c.surfaceHigh, base = if (current) c.surface else androidx.compose.ui.graphics.Color.Transparent, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(track, 40.dp, 11.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Txt(track.title, 14, FontWeight.ExtraBold, if (current) c.accent else c.textPrimary)
            Txt(if (current) str("desktop_playing_now") else track.artist, 12, FontWeight.Medium, c.textSecondary)
        }
        if (onRemove != null) IconBtn(Traco.close, str("remove_from_queue"), onRemove, size = 32.dp, iconSize = 16.dp, tint = c.textSecondary)
        else Txt(formatTime(track.durationMs), 12, FontWeight.Medium, c.textSecondary)
    }
}
