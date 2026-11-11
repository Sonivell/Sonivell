package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.itemsIndexed
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.ui.components.OndaArt
import com.juscelino.sonivell.ui.theme.ArtTones
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.SmartPlaylist
import com.juscelino.sonivell.ui.library.LibraryActions
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.glassSurface

/**
 * Biblioteca do tema Sonivell: filtros em cápsula, coleções e uma fileira de artistas. Não é mais uma aba:
 * abre pelos cartões do Início já no filtro do cartão ([tab]: 0 Todas, 1 Músicas, 2 Álbuns, 3 Artistas,
 * 4 Gêneros, 5 Pastas) e tem o botão de voltar.
 */
@Composable
fun SvLibraryScreen(
    state: LibraryState,
    favorites: Set<Long>,
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylist>,
    contentPadding: PaddingValues,
    actions: LibraryActions,
    tab: Int,
    onTab: (Int) -> Unit,
    onBack: (() -> Unit)? = null,
    tagBannerCount: Int = 0,
) {
    val nc = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val tabs = listOf(R.string.sv_all, R.string.tab_songs, R.string.sv_albums, R.string.sv_artists, R.string.sv_genres, R.string.tab_folders)
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val songs = { n: Int -> resources.getString(R.string.sv_songs_count, n) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 20.dp, end = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                if (onBack != null) SvSquare(icons.back, stringResource(R.string.cd_back), onBack)
                SvTitle(stringResource(R.string.sv_library_title), modifier = Modifier.weight(1f))
                SvSquare(icons.search, stringResource(R.string.cd_search_library), actions.onOpenSearch)
                // Nova playlist: botão neutro, como a busca; a cor de destaque fica para tocar.
                SvSquare(icons.add, stringResource(R.string.action_playlist), actions.onNewPlaylist)
            }
        }
        if (tagBannerCount > 0) {
            item(key = "tags-banner") {
                com.juscelino.sonivell.ui.library.TagsBanner(tagBannerCount, actions.onOpenOrganize, actions.onDismissTagBanner)
            }
        }
        item {
            // Redesign: chips soltos e roláveis no lugar do segmentado; o ativo invertido (claro no escuro).
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEachIndexed { i, label ->
                    val on = tab == i
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) nc.textPrimary else nc.surface)
                            .clickable(role = Role.Tab) { onTab(i) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (on) nc.background else nc.textPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        when (tab) {
            0 -> {
                item {
                    SvSection(stringResource(R.string.sv_collections), Modifier.padding(top = 10.dp))
                }
                // Cada coleção com o seu ícone e a sua cor (antes todas tinham a mesma onda).
                item { collection(stringResource(R.string.source_liked), songs(favorites.size), icons.liked, nc.accent) { actions.onOpenAuto("curtidas") } }
                item { collection(stringResource(R.string.auto_most), stringResource(R.string.sv_most_sub), icons.trendUp, collectionAmber()) { actions.onOpenAuto("mais-ouvidas") } }
                item { collection(stringResource(R.string.auto_recent_added), "", icons.recent, collectionBlue()) { actions.onOpenAuto("recentes") } }
                items(smartPlaylists, key = { "s${it.id}" }) { sp ->
                    collection(sp.name, stringResource(R.string.smart_playlists), icons.smart, collectionPurple()) { actions.onOpenSmartPlaylist(sp.id) }
                }
                items(playlists, key = { "p${it.id}" }) { p ->
                    collection(p.name, songs(p.trackIds.size), icons.musicNote, nc.textSecondary) { actions.onOpenPlaylist(p.id) }
                }
                if (state.artists.isNotEmpty()) {
                    item {
                        SvSection(stringResource(R.string.sv_artists), Modifier.padding(top = 14.dp))
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(state.artists.take(30), key = { it.name }) { artist ->
                                Column(
                                    Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { actions.onOpenArtist(artist.name) }.padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    // Artista em círculo, como de costume em apps de música.
                                    com.juscelino.sonivell.ui.components.AlbumArt(artist.tracks.firstOrNull(), Modifier.size(76.dp), corner = 38.dp)
                                    Text(artist.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = nc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.size(width = 84.dp, height = 16.dp))
                                }
                            }
                        }
                    }
                }
                // Álbuns tocados há pouco, em grade de capas: a tela não termina num vazio.
                if (state.recentAlbums.isNotEmpty()) {
                    item {
                        SvSection(stringResource(R.string.sv_albums), Modifier.padding(top = 14.dp))
                    }
                    items(state.recentAlbums.take(6).chunked(3), key = { row -> "ra-" + row.first().id }) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { album ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { actions.onOpenAlbum(album.id) },
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    com.juscelino.sonivell.ui.components.AlbumArt(album.tracks.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp)
                                    Text(album.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(album.artist, style = MaterialTheme.typography.labelMedium, color = nc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            1 -> {
                val list = state.tracks.ifEmpty { state.allTracks }
                val source = resources.getString(R.string.tab_songs)
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(songs(list.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = nc.textSecondary, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(nc.accent)
                                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_shuffle_all), onClick = actions.onShuffleAll)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(R.string.mode_shuffle), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = nc.onAccent) }
                    }
                }
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { actions.onPlay(list, i, source) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SvCircleArt(t, 48.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(formatDuration(t.durationMs), style = MaterialTheme.typography.labelMedium, color = nc.textSecondary)
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_more_options)) { actions.onTrackMenu(t) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(icons.more, null, Modifier.size(20.dp), tint = nc.textSecondary) }
                    }
                }
            }
            2 -> items(state.albums.take(300), key = { it.id }) { album ->
                SvRow(album.title, album.artist, { actions.onOpenAlbum(album.id) }, art = { SvCircleArt(album.tracks.firstOrNull(), 48.dp) }, elevation = 0.dp)
            }
            3 -> items(state.artists.take(300), key = { it.name }) { artist ->
                SvRow(artist.name, songs(artist.tracks.size), { actions.onOpenArtist(artist.name) }, art = { SvCircleArt(artist.tracks.firstOrNull(), 48.dp) }, elevation = 0.dp)
            }
            4 -> {
                // Cartões de dois por linha, em superfície neutra, com a onda curta (a cor fica para ação e estado).
                val rows = state.genres.chunked(2)
                itemsIndexed(rows, key = { _, r -> "g-" + r.first().name }) { r, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEachIndexed { col, genre ->
                            // Redesign: cada gênero com a sua cor (a mesma do Explorar), sem a onda decorativa.
                            val genreShape = RoundedCornerShape(18.dp)
                            Box(
                                Modifier.weight(1f).height(96.dp)
                                    .clip(genreShape)
                                    .background(com.juscelino.sonivell.ui.library.genreTone(genre.name))
                                    .clickable(role = Role.Button) { actions.onOpenGenre(genre.name) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(genre.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(songs(genre.tracks.size), style = MaterialTheme.typography.labelMedium, color = nc.textPrimary.copy(alpha = 0.82f), modifier = Modifier.align(Alignment.BottomStart))
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            else -> items(state.folders, key = { it.path }) { folder ->
                SvRow(
                    folder.name, folder.path, { actions.onOpenFolder(folder.path) },
                    art = { SvIconTile(icons.folder) },
                    trailing = { Text(folder.tracks.size.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = nc.textSecondary) },
                    elevation = 0.dp,
                )
            }
        }
    }
}

/** Coleção: ícone próprio num quadrado tingido com a cor dela, o nome e a meta. */
@Composable
private fun collection(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    SvRow(
        title, subtitle, onClick,
        art = {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = tint)
            }
        },
        elevation = 0.dp,
    )
}

/** Tons das coleções: mais escuros no tema claro, para o ícone seguir legível. */
@Composable
private fun collectionAmber(): Color = if (SonivellTheme.colors.isDark) Color(0xFFF5B841) else Color(0xFF8A5A00)

@Composable
private fun collectionBlue(): Color = if (SonivellTheme.colors.isDark) Color(0xFF7DB4FF) else Color(0xFF0B5FAA)

@Composable
private fun collectionPurple(): Color = if (SonivellTheme.colors.isDark) Color(0xFFD7A6F0) else Color(0xFF7A3A9A)
