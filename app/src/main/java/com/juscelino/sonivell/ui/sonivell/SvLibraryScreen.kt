package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.LibraryState
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.SmartPlaylist
import com.juscelino.sonivell.ui.library.LibraryActions
import com.juscelino.sonivell.ui.theme.SonivellTheme

/** Biblioteca do tema Sonivell: filtros em cápsula, coleções e uma fileira de artistas. */
@Composable
fun SvLibraryScreen(
    state: LibraryState,
    favorites: Set<Long>,
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylist>,
    contentPadding: PaddingValues,
    actions: LibraryActions,
) {
    val nc = SonivellTheme.colors
    val icons = SonivellTheme.icons
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.sv_all, R.string.sv_albums, R.string.sv_artists, R.string.sv_genres)
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val songs = { n: Int -> resources.getString(R.string.sv_songs_count, n) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 24.dp, end = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sv_library_title), style = MaterialTheme.typography.headlineSmall, color = nc.textPrimary, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_search), onClick = actions.onOpenSearch),
                    contentAlignment = Alignment.Center,
                ) { Icon(icons.search, null, Modifier.size(22.dp), tint = nc.textPrimary) }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tabs.size) { i ->
                    val on = tab == i
                    Box(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (on) nc.accent else nc.surface)
                            .clickable(role = Role.Tab) { tab = i }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(tabs[i]), style = MaterialTheme.typography.labelLarge, color = if (on) nc.onAccent else nc.textSecondary)
                    }
                }
            }
        }
        when (tab) {
            0 -> {
                item {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sv_collections), style = MaterialTheme.typography.titleLarge, color = nc.textPrimary, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = actions.onNewPlaylist),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icons.add, null, Modifier.size(22.dp), tint = nc.textPrimary) }
                    }
                }
                item { collection(stringResource(R.string.source_liked), songs(favorites.size), Color(0xFFA86A3A), Color(0xFF2A1A10)) { actions.onOpenAuto("curtidas") } }
                item { collection(stringResource(R.string.auto_most), "", Color(0xFF6A7A8A), Color(0xFF1A1F26)) { actions.onOpenAuto("mais-ouvidas") } }
                item { collection(stringResource(R.string.auto_recent_added), "", Color(0xFF8A6A4A), Color(0xFF221810)) { actions.onOpenAuto("recentes") } }
                items(smartPlaylists, key = { "s${it.id}" }) { sp ->
                    collection(sp.name, stringResource(R.string.smart_playlists), Color(0xFF7A5A8A), Color(0xFF1C1226)) { actions.onOpenSmartPlaylist(sp.id) }
                }
                items(playlists, key = { "p${it.id}" }) { p ->
                    collection(p.name, songs(p.trackIds.size), Color(0xFF9A8A6A), Color(0xFF1E1A12)) { actions.onOpenPlaylist(p.id) }
                }
                if (state.artists.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.sv_artists), style = MaterialTheme.typography.titleLarge, color = nc.textPrimary, modifier = Modifier.padding(top = 14.dp))
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(state.artists.take(30), key = { it.name }) { artist ->
                                Column(
                                    Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { actions.onOpenArtist(artist.name) }.padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    SvCircleArt(artist.tracks.firstOrNull(), 64.dp)
                                    Text(artist.name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = nc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.size(width = 72.dp, height = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
            1 -> items(state.albums.take(300), key = { it.id }) { album ->
                SvRow(album.title, album.artist, { actions.onOpenAlbum(album.id) }, art = { SvCircleArt(album.tracks.firstOrNull(), 48.dp) })
            }
            2 -> items(state.artists.take(300), key = { it.name }) { artist ->
                SvRow(artist.name, songs(artist.tracks.size), { actions.onOpenArtist(artist.name) }, art = { SvCircleArt(artist.tracks.firstOrNull(), 48.dp) })
            }
            else -> items(state.genres, key = { it.name }) { genre ->
                SvRow(genre.name, songs(genre.tracks.size), { actions.onOpenGenre(genre.name) }, art = { SvCircleArt(genre.tracks.firstOrNull(), 48.dp) })
            }
        }
    }
}

@Composable
private fun collection(title: String, subtitle: String, top: Color, bottom: Color, onClick: () -> Unit) {
    SvRow(title, subtitle, onClick, art = { SvOrb(top, bottom, Modifier.size(48.dp), seed = title.length) })
}
