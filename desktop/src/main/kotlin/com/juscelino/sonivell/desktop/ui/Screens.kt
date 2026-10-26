package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.desktop.Album
import com.juscelino.sonivell.desktop.Controller
import com.juscelino.sonivell.desktop.Track
import com.juscelino.sonivell.desktop.fold
import com.juscelino.sonivell.desktop.formatTime
import com.juscelino.sonivell.desktop.str
import java.util.Calendar

private fun greetingKey(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "greeting_morning"
    in 12..17 -> "greeting_afternoon"
    else -> "greeting_evening"
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, focus: FocusRequester? = null) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(44.dp).border(1.dp, c.outline, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Traco.search, str("cd_search"), Modifier.size(18.dp), tint = c.textSecondary)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Txt(str("search_hint"), 14, FontWeight.Medium, c.textSecondary)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(fontFamily = Manrope, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().let { if (focus != null) it.focusRequester(focus) else it },
            )
        }
        if (value.isNotEmpty()) {
            IconBtn(Traco.close, str("cd_clear_search"), { onChange("") }, size = 28.dp, iconSize = 16.dp, tint = c.textSecondary)
        }
    }
}

/** Linha de faixa: capa, título/artista, tempo, curtir e menu. Usada em listas e resultados. */
@Composable
fun TrackRow(
    track: Track,
    ctl: Controller,
    onPlay: () -> Unit,
    liked: Boolean,
    isCurrent: Boolean,
    onOpenAlbum: (Track) -> Unit,
    onOpenArtist: (Track) -> Unit,
    subtitle: String = track.artist,
) {
    val c = SonivellTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(60.dp).hoverClick(RoundedCornerShape(14.dp), hover = c.surface, base = if (isCurrent) c.surface else Color.Transparent, onClick = onPlay).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumArt(track, 44.dp, 12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(track.title, 15, FontWeight.ExtraBold, if (isCurrent) c.accent else c.textPrimary)
            Txt(subtitle, 13, FontWeight.Medium, c.textSecondary)
        }
        Txt(formatTime(track.durationMs), 13, FontWeight.Medium, c.textSecondary)
        IconBtn(if (liked) Traco.liked else Traco.like, str(if (liked) "cd_unlike" else "cd_like"), { ctl.toggleFavorite(track) }, size = 36.dp, active = liked)
        Box {
            IconBtn(Traco.more, str("cd_more_options"), { menu = true }, size = 36.dp, tint = c.textSecondary)
            TrackMenu(menu, { menu = false }, track, ctl, onOpenAlbum, onOpenArtist)
        }
    }
}

@Composable
fun TrackMenu(open: Boolean, onDismiss: () -> Unit, track: Track, ctl: Controller, onOpenAlbum: (Track) -> Unit, onOpenArtist: (Track) -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(str("play_next")) }, onClick = { ctl.playNext(track); onDismiss() })
        DropdownMenuItem(text = { Text(str("add_to_queue")) }, onClick = { ctl.addToQueue(track); onDismiss() })
        if (track.album.isNotBlank()) DropdownMenuItem(text = { Text(str("go_to_album")) }, onClick = { onOpenAlbum(track); onDismiss() })
        if (track.artist.isNotBlank()) DropdownMenuItem(text = { Text(str("go_to_artist")) }, onClick = { onOpenArtist(track); onDismiss() })
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Column(Modifier.width(176.dp).hoverClick(RoundedCornerShape(18.dp), hover = c.surface, onClick = onClick).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AlbumArt(album.tracks.first(), 160.dp, 16.dp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Txt(album.title, 14, FontWeight.ExtraBold)
            Txt(album.artist, 12, FontWeight.Medium, c.textSecondary)
        }
    }
}

/** Atalhos usados por várias telas para abrir álbum/artista e tocar listas. */
class Actions(val ctl: Controller, val nav: Nav) {
    fun openAlbum(track: Track) {
        val lib = ctl.library.value.library
        val album = lib.albums.firstOrNull { it.key == track.albumKey } ?: return
        openAlbum(album)
    }

    fun openAlbum(album: Album) {
        nav.detail = Detail(album.title, album.artist + if (album.year > 0) " · ${album.year}" else "", album.tracks, album.tracks.first(), str("kind_album", album.title))
    }

    fun openArtist(track: Track) = openArtist(track.artist)

    fun openArtist(name: String) {
        val tracks = ctl.library.value.library.artists.firstOrNull { it.first == name }?.second ?: return
        nav.detail = Detail(name, str("kind_artist"), tracks.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNo })), tracks.first(), str("kind_artist"))
    }
}

@Composable
fun ForYouScreen(ctl: Controller, nav: Nav, actions: Actions) {
    val c = SonivellTheme.colors
    val lib by ctl.library.collectAsState()
    val favorites by ctl.favorites.collectAsState()
    val player by ctl.player.collectAsState()
    val resume by ctl.resume.collectAsState()
    val library = lib.library
    val state = rememberLazyListState()

    if (library.tracks.isEmpty()) {
        EmptyLibrary(ctl)
        return
    }
    val liked = remember(library, favorites) { library.tracks.filter { it.id in favorites } }
    val recent = remember(library) { library.recentlyAdded }
    val mostPlayed = remember(library, player.index) {
        library.tracks.map { it to ctl.user.playCount(it.id) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
    val rediscover = remember(library) {
        val now = System.currentTimeMillis()
        library.tracks.filter { ctl.user.lastPlayed(it.id).let { last -> last == 0L || now - last > 30L * 86_400_000L } }.shuffled().take(6)
    }
    val forgotten = remember(library, favorites) {
        val now = System.currentTimeMillis()
        liked.filter { now - ctl.user.lastPlayed(it.id) > 30L * 86_400_000L }.shuffled().take(5)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(str(greetingKey()), 14, FontWeight.SemiBold, c.textSecondary)
                        Txt(str("nav_home"), 36, FontWeight.ExtraBold, letterSpacing = -1f)
                    }
                    SearchBox(nav.query, { nav.query = it; nav.go(Route.Search) }, Modifier.width(360.dp))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(str("source_liked"), str("desktop_tracks_count", liked.size), Modifier.weight(1f)) {
                            if (liked.isNotEmpty()) nav.detail = Detail(str("source_liked"), str("desktop_tracks_count", liked.size), liked, liked.first(), str("source_liked"))
                        }
                        QuickCard(str("shuffle_all"), str("desktop_tracks_count", library.tracks.size), Modifier.weight(1f)) { ctl.playShuffled(library.tracks, str("source_shuffle")) }
                        QuickCard(str("recently_added"), str("auto_recent_added"), Modifier.weight(1f)) {
                            val list = recent.take(100); nav.detail = Detail(str("recently_added"), str("desktop_tracks_count", list.size), list, list.firstOrNull(), str("recently_added"))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickCard(str("auto_most"), str("desktop_tracks_count", mostPlayed.size), Modifier.weight(1f)) {
                            if (mostPlayed.isNotEmpty()) nav.detail = Detail(str("auto_most"), str("desktop_tracks_count", mostPlayed.size), mostPlayed.take(100), mostPlayed.first(), str("auto_most"))
                        }
                        QuickCard(str("fy_pick"), str("auto_never"), Modifier.weight(1f)) {
                            if (rediscover.isNotEmpty()) ctl.playFrom(rediscover, 0, str("fy_pick"))
                        }
                        QuickCard(str("nav_library"), str("desktop_library_summary", library.tracks.size, library.albums.size, library.artists.size), Modifier.weight(1f)) { nav.go(Route.Library) }
                    }
                }
            }
            val r = resume
            if (r != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(str("fy_continue"))
                    val (t, pos) = r
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AlbumArt(t, 72.dp, 18.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Txt(t.title, 17, FontWeight.ExtraBold)
                            Txt(str("fy_time_left", t.artist, formatTime(t.durationMs - pos)), 13, FontWeight.Medium, c.textSecondary)
                        }
                        Waveform(t.id, if (t.durationMs > 0) pos.toFloat() / t.durationMs else 0f, Modifier.width(360.dp).height(34.dp), bars = 70)
                        PlaySquircle(false, { ctl.playFrom(listOf(t), 0, "", pos) }, size = 52.dp, corner = 17.dp)
                    }
                }
            }
            if (recent.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(str("recently_added"))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(library.albums.sortedByDescending { a -> a.tracks.maxOf { it.addedMs } }.take(14)) { a -> AlbumCard(a) { actions.openAlbum(a) } }
                    }
                }
            }
            if (forgotten.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(str("fy_forgotten"))
                    forgotten.forEach { t -> TrackRow(t, ctl, { ctl.playFrom(forgotten, forgotten.indexOf(t), str("fy_forgotten")) }, t.id in favorites, player.current?.id == t.id, actions::openAlbum, actions::openArtist) }
                }
            }
            if (rediscover.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(str("fy_pick"))
                    rediscover.forEach { t -> TrackRow(t, ctl, { ctl.playFrom(rediscover, rediscover.indexOf(t), str("fy_pick")) }, t.id in favorites, player.current?.id == t.id, actions::openAlbum, actions::openArtist) }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        modifier.height(64.dp).hoverClick(RoundedCornerShape(16.dp), hover = c.surfaceHigh, base = c.surface, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 14, FontWeight.ExtraBold)
            Txt(subtitle, 12, FontWeight.SemiBold, c.textSecondary)
        }
    }
}

@Composable
fun EmptyLibrary(ctl: Controller) {
    val lib by ctl.library.collectAsState()
    val c = SonivellTheme.colors
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        val message = when {
            lib.toolsMissing -> str("desktop_no_tools")
            lib.scanning -> lib.progress?.let { str("desktop_scanning", it.first, it.second) } ?: str("desktop_scanning_plain")
            else -> str("desktop_empty")
        }
        Txt(message, 15, FontWeight.Medium, c.textSecondary, maxLines = 5, lineHeight = 22)
    }
}

@Composable
fun SearchScreen(ctl: Controller, nav: Nav, actions: Actions) {
    val c = SonivellTheme.colors
    val lib by ctl.library.collectAsState()
    val favorites by ctl.favorites.collectAsState()
    val player by ctl.player.collectAsState()
    val focus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = nav.query
    val tracks = remember(q, lib.library) { lib.library.search(q) }
    val albums = remember(q, lib.library) { lib.library.searchAlbums(q).take(12) }
    val artists = remember(q, lib.library) { lib.library.searchArtists(q).take(10) }
    val state = rememberLazyListState()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(str("nav_search"), 36, FontWeight.ExtraBold, letterSpacing = -1f)
        }
        SearchBox(q, { nav.query = it }, Modifier.widthIn(max = 640.dp).fillMaxWidth(), focus)
        if (q.isBlank()) {
            if (lib.library.genres.isNotEmpty()) {
                SectionTitle(str("search_browse"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lib.library.genres.take(20)) { (name, list) ->
                        Pill(name + " · " + list.size, false, { nav.detail = Detail(name, str("desktop_tracks_count", list.size), list, list.first(), str("kind_genre", name)) })
                    }
                }
            }
        } else if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            EmptyState(str("search_none"))
        } else {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (artists.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionTitle(str("desktop_section_artists"))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(artists) { (name, list) -> Pill(name, false, { actions.openArtist(name) }, Traco.artist) }
                            }
                        }
                    }
                    if (albums.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            SectionTitle(str("desktop_section_albums"))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(albums) { a -> AlbumCard(a) { actions.openAlbum(a) } } }
                        }
                    }
                    if (tracks.isNotEmpty()) {
                        item { SectionTitle(str("desktop_section_songs"), Modifier.padding(vertical = 4.dp)) }
                        itemsIndexed(tracks.take(200)) { i, t ->
                            TrackRow(t, ctl, { ctl.playFrom(tracks, i, str("search_hint")) }, t.id in favorites, player.current?.id == t.id, actions::openAlbum, actions::openArtist)
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

/** Lista de faixas de um álbum, artista, gênero, pasta ou lista automática. */
@Composable
fun DetailScreen(detail: Detail, ctl: Controller, nav: Nav, actions: Actions) {
    val c = SonivellTheme.colors
    val favorites by ctl.favorites.collectAsState()
    val player by ctl.player.collectAsState()
    val state = rememberLazyListState()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            IconBtn(Traco.back, str("desktop_back"), { nav.detail = null }, size = 44.dp, outlined = true)
            AlbumArt(detail.artTrack, 120.dp, 24.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Txt(detail.kind, 12, FontWeight.ExtraBold, c.accent, letterSpacing = 1.2f)
                Txt(detail.title, 32, FontWeight.ExtraBold, letterSpacing = -1f)
                Txt(detail.subtitle, 14, FontWeight.Medium, c.textSecondary)
            }
            PlaySquircle(false, { ctl.playFrom(detail.tracks, 0, detail.title) }, size = 56.dp, corner = 18.dp, iconSize = 24.dp)
            IconBtn(Traco.shuffle, str("shuffle_all"), { ctl.playShuffled(detail.tracks, detail.title) }, size = 44.dp, outlined = true)
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(detail.tracks) { i, t ->
                    TrackRow(t, ctl, { ctl.playFrom(detail.tracks, i, detail.title) }, t.id in favorites, player.current?.id == t.id, actions::openAlbum, actions::openArtist)
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}
