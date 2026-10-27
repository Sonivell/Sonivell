package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.desktop.Controller
import com.juscelino.sonivell.desktop.Track
import com.juscelino.sonivell.desktop.formatTime
import com.juscelino.sonivell.desktop.str

private enum class SortBy { Title, Artist, Album, Duration, Recent, Year }

private fun sorted(tracks: List<Track>, by: SortBy): List<Track> = when (by) {
    SortBy.Title -> tracks.sortedBy { it.title.lowercase() }
    SortBy.Artist -> tracks.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNo }))
    SortBy.Album -> tracks.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNo }))
    SortBy.Duration -> tracks.sortedByDescending { it.durationMs }
    SortBy.Recent -> tracks.sortedByDescending { it.addedMs }
    SortBy.Year -> tracks.sortedByDescending { it.year }
}

@Composable
fun LibraryScreen(ctl: Controller, nav: Nav, actions: Actions) {
    val c = SonivellTheme.colors
    val lib by ctl.library.collectAsState()
    val library = lib.library
    var sort by remember { mutableStateOf(SortBy.Title) }
    var sortMenu by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(false) }

    if (library.tracks.isEmpty()) { EmptyLibrary(ctl); return }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(str("desktop_library_summary", library.tracks.size, library.albums.size, library.artists.size), 14, FontWeight.SemiBold, c.textSecondary)
            Txt(str("nav_library"), 36, FontWeight.ExtraBold, letterSpacing = -1f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(str("tab_all"), nav.tab == LibTab.All, { nav.tab = LibTab.All })
            Pill(str("tab_albums"), nav.tab == LibTab.Albums, { nav.tab = LibTab.Albums })
            Pill(str("tab_artists"), nav.tab == LibTab.Artists, { nav.tab = LibTab.Artists })
            Pill(str("tab_genres"), nav.tab == LibTab.Genres, { nav.tab = LibTab.Genres })
            Pill(str("tab_folders"), nav.tab == LibTab.Folders, { nav.tab = LibTab.Folders })
            Box(Modifier.weight(1f))
            if (nav.tab == LibTab.All) {
                Box {
                    Row(
                        Modifier.height(38.dp).hoverClick(RoundedCornerShape(13.dp), hover = c.surface, onClick = { sortMenu = true }).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Traco.sort, str("cd_sort_by"), Modifier.size(18.dp), tint = c.textTertiary)
                        Txt(
                            str(when (sort) { SortBy.Title -> "sort_title"; SortBy.Artist -> "sort_artist"; SortBy.Album -> "sort_album"; SortBy.Duration -> "sort_duration"; SortBy.Recent -> "sort_recent"; SortBy.Year -> "sort_year" }),
                            13, FontWeight.Bold, c.textTertiary,
                        )
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortBy.values().forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(str(when (opt) { SortBy.Title -> "sort_title"; SortBy.Artist -> "sort_artist"; SortBy.Album -> "sort_album"; SortBy.Duration -> "sort_duration"; SortBy.Recent -> "sort_recent"; SortBy.Year -> "sort_year" })) },
                                onClick = { sort = opt; sortMenu = false },
                            )
                        }
                    }
                }
            }
            if (nav.tab == LibTab.Albums) {
                IconBtn(Traco.list, str("cd_view_list"), { grid = false }, size = 38.dp, outlined = true, active = !grid)
                IconBtn(Traco.grid, str("cd_view_grid"), { grid = true }, size = 38.dp, outlined = true, active = grid)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (nav.tab) {
                LibTab.All -> TrackTable(sorted(library.tracks, sort), ctl, actions)
                LibTab.Albums -> if (grid) AlbumGrid(library.albums, actions) else NamedList(
                    library.albums.map { Triple(it.title, it.artist + " · " + str("desktop_tracks_count", it.tracks.size), it.tracks.first()) }
                ) { i -> actions.openAlbum(library.albums[i]) }
                LibTab.Artists -> NamedList(library.artists.map { (n, l) -> Triple(n, str("desktop_tracks_count", l.size), l.first()) }) { i -> actions.openArtist(library.artists[i].first) }
                LibTab.Genres -> NamedList(library.genres.map { (n, l) -> Triple(n, str("desktop_tracks_count", l.size), l.first()) }) { i ->
                    val (n, l) = library.genres[i]; nav.detail = Detail(n, str("desktop_tracks_count", l.size), l, l.first(), str("kind_genre", n))
                }
                LibTab.Folders -> NamedList(library.folders.map { (n, l) -> Triple(n.substringAfterLast('/'), n + " · " + str("desktop_tracks_count", l.size), l.first()) }) { i ->
                    val (n, l) = library.folders[i]; nav.detail = Detail(n.substringAfterLast('/'), n, l, l.first(), str("kind_folder", n.substringAfterLast('/')))
                }
            }
        }
    }
}

/** Tabela de músicas: número, título, artista, álbum, gênero, formato, duração e curtir. */
@Composable
private fun TrackTable(tracks: List<Track>, ctl: Controller, actions: Actions) {
    val c = SonivellTheme.colors
    val favorites by ctl.favorites.collectAsState()
    val player by ctl.player.collectAsState()
    val state = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(34.dp).padding(start = 14.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(44.dp)) { Txt(str("desktop_col_number"), 11, FontWeight.ExtraBold, c.textSecondary.copy(alpha = 0.8f), letterSpacing = 1f) }
            Box(Modifier.weight(2.8f).padding(end = 12.dp)) { Txt(str("sort_title").uppercase(), 11, FontWeight.ExtraBold, c.textSecondary, letterSpacing = 1f) }
            Box(Modifier.weight(1.5f).padding(end = 12.dp)) { Txt(str("sort_artist").uppercase(), 11, FontWeight.ExtraBold, c.textSecondary, letterSpacing = 1f) }
            Box(Modifier.weight(1.5f).padding(end = 12.dp)) { Txt(str("sort_album").uppercase(), 11, FontWeight.ExtraBold, c.textSecondary, letterSpacing = 1f) }
            Box(Modifier.weight(1.1f).padding(end = 12.dp)) { Txt(str("rule_genre").uppercase(), 11, FontWeight.ExtraBold, c.textSecondary, letterSpacing = 1f) }
            Box(Modifier.width(76.dp)) { Txt(str("rule_format").uppercase(), 11, FontWeight.ExtraBold, c.textSecondary, letterSpacing = 1f) }
            Box(Modifier.width(64.dp)) { Txt(str("sort_duration").uppercase(), 11, FontWeight.ExtraBold, c.textSecondary, letterSpacing = 1f) }
            Box(Modifier.width(76.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                    TableRow(i, t, tracks, ctl, actions, t.id in favorites, player.current?.id == t.id, player.playing)
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun TableRow(index: Int, t: Track, all: List<Track>, ctl: Controller, actions: Actions, liked: Boolean, current: Boolean, playing: Boolean) {
    val c = SonivellTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(56.dp).hoverClick(RoundedCornerShape(14.dp), hover = c.surface, base = if (current) c.surface else Color.Transparent, onClick = { ctl.playFrom(all, index, str("nav_library")) }).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp)) {
            if (current) Icon(Traco.nowPlaying, null, Modifier.size(16.dp), tint = c.accent)
            else Txt((index + 1).toString(), 13, FontWeight.Bold, c.textSecondary.copy(alpha = 0.8f))
        }
        Row(Modifier.weight(2.8f).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlbumArt(t, 40.dp, 11.dp)
            Txt(t.title, 15, FontWeight.ExtraBold, if (current) c.accent else c.textPrimary)
        }
        Box(Modifier.weight(1.5f).padding(end = 12.dp)) { Txt(t.artist, 14, FontWeight.SemiBold, c.textTertiary) }
        Box(Modifier.weight(1.5f).padding(end = 12.dp)) { Txt(t.album, 14, FontWeight.SemiBold, c.textTertiary) }
        Box(Modifier.weight(1.1f).padding(end = 12.dp)) { Txt(t.genre, 14, FontWeight.Medium, c.textSecondary) }
        Box(Modifier.width(76.dp)) { Txt(t.format, 11, FontWeight.ExtraBold, c.textSecondary, letterSpacing = 0.8f) }
        Box(Modifier.width(64.dp)) { Txt(formatTime(t.durationMs), 14, FontWeight.Medium, c.textSecondary) }
        IconBtn(if (liked) Traco.liked else Traco.like, str(if (liked) "cd_unlike" else "cd_like"), { ctl.toggleFavorite(t) }, size = 36.dp, active = liked)
        Box {
            IconBtn(Traco.more, str("cd_more_options"), { menu = true }, size = 36.dp, tint = c.textSecondary)
            TrackMenu(menu, { menu = false }, t, ctl, actions::openAlbum, actions::openArtist)
        }
    }
}

@Composable
private fun NamedList(items: List<Triple<String, String, Track>>, onClick: (Int) -> Unit) {
    val c = SonivellTheme.colors
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(items) { i, (title, subtitle, art) ->
                Row(
                    Modifier.fillMaxWidth().height(64.dp).hoverClick(RoundedCornerShape(14.dp), hover = c.surface, onClick = { onClick(i) }).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AlbumArt(art, 48.dp, 13.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(title, 15, FontWeight.ExtraBold)
                        Txt(subtitle, 13, FontWeight.Medium, c.textSecondary)
                    }
                    Icon(Traco.chevron, null, Modifier.size(18.dp), tint = c.textSecondary)
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun AlbumGrid(albums: List<com.juscelino.sonivell.desktop.Album>, actions: Actions) {
    val c = SonivellTheme.colors
    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(176.dp), state = state, modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItemsIndexed(albums) { _, a ->
                Column(Modifier.hoverClick(RoundedCornerShape(18.dp), hover = c.surface, onClick = { actions.openAlbum(a) }).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlbumArt(a.tracks.first(), 160.dp, 16.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Txt(a.title, 14, FontWeight.ExtraBold)
                        Txt(a.artist, 12, FontWeight.Medium, c.textSecondary)
                    }
                }
            }
        }
        VerticalScrollbar(androidx.compose.foundation.rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
