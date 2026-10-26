package com.juscelino.sonivell.ui.library

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.juscelino.sonivell.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import com.juscelino.sonivell.data.ForYouState
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Category
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.smart.SmartProgress
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.LibraryState
import com.juscelino.sonivell.LibraryTab
import com.juscelino.sonivell.data.Album
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.Chip
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.SectionLabel
import com.juscelino.sonivell.ui.components.TrackRow
import com.juscelino.sonivell.ui.theme.SonivellTheme

enum class LibraryMode { Inicio, Buscar, Biblioteca }

/** Rótulos já traduzidos, usados como "Tocando de …" ao iniciar a reprodução. */
private data class LibraryLabels(val search: String, val library: String, val liked: String, val forYou: String)

data class LibraryActions(
    val onQuery: (String) -> Unit,
    val onTab: (LibraryTab) -> Unit,
    val onSort: () -> Unit,
    val onRefresh: () -> Unit,
    val onPlay: (tracks: List<Track>, index: Int, source: String) -> Unit,
    val onShuffleAll: () -> Unit,
    val onPlayMix: (Track) -> Unit = {},
    val onTrackMenu: (Track) -> Unit = {},
    val onOpenAlbum: (Long) -> Unit = {},
    val onOpenArtist: (String) -> Unit = {},
    val onOpenGenre: (String) -> Unit = {},
    val onOpenFolder: (String) -> Unit = {},
    val onOpenPlaylist: (Long) -> Unit = {},
    val onNewPlaylist: () -> Unit = {},
    val onImportPlaylist: () -> Unit = {},
)

@Composable
fun LibraryScreen(
    mode: LibraryMode,
    state: LibraryState,
    favorites: Set<Long>,
    currentId: Long?,
    contentPadding: PaddingValues,
    actions: LibraryActions,
    smartProgress: SmartProgress = SmartProgress(),
    forYou: ForYouState = ForYouState(),
    playlists: List<Playlist> = emptyList(),
) {
    val c = SonivellTheme.colors
    val trackById = remember(state.allTracks) { state.allTracks.associateBy { it.id } }
    val focus = remember { FocusRequester() }
    val labels = LibraryLabels(
        search = stringResource(R.string.source_search),
        library = stringResource(R.string.nav_library),
        liked = stringResource(R.string.source_liked),
        forYou = stringResource(R.string.nav_home),
    )
    LaunchedEffect(mode) { if (mode == LibraryMode.Buscar) runCatching { focus.requestFocus() } }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Cabeçalho
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    stringResource(
                        when (mode) {
                            LibraryMode.Inicio -> R.string.nav_home
                            LibraryMode.Buscar -> R.string.nav_search
                            LibraryMode.Biblioteca -> R.string.nav_library
                        }
                    ),
                    style = MaterialTheme.typography.displaySmall,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconBtn(Icons.Rounded.Refresh, stringResource(R.string.cd_refresh), actions.onRefresh, background = c.surface, iconSize = 20.dp)
            }
        }

        if (mode != LibraryMode.Inicio) {
            item(key = "search") {
                SearchField(state.query, actions.onQuery, Modifier.focusRequester(focus).padding(bottom = 16.dp))
            }
        }

        if (state.loading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.accent)
                }
            }
            return@LazyColumn
        }

        if (state.allTracks.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.library_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
            return@LazyColumn
        }

        when (mode) {
            LibraryMode.Inicio -> homeContent(state, favorites, currentId, labels, actions, smartProgress, forYou)
            LibraryMode.Buscar -> {
                val q = state.query.trim()
                val matching = if (q.isEmpty()) emptyList() else playlists.filter { it.name.contains(q, ignoreCase = true) }
                if (matching.isNotEmpty()) {
                    item(key = "pl-found-label") { SectionLabel(stringResource(R.string.tab_playlists), Modifier.padding(bottom = 4.dp)) }
                    items(matching, key = { "pl-found-" + it.id }) { p -> PlaylistRow(p, trackById, actions) }
                    item(key = "pl-found-gap") { Box(Modifier.height(12.dp)) }
                }
                trackList(state.tracks, currentId, labels.search, q.isNotEmpty(), actions)
            }
            LibraryMode.Biblioteca -> {
                item(key = "chips") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibraryTab.entries.forEach { t ->
                            Chip(stringResource(t.label), selected = state.tab == t, onClick = { actions.onTab(t) })
                        }
                    }
                }
                when (state.tab) {
                    LibraryTab.Musicas -> {
                        if (state.query.isBlank() && state.recentAlbums.isNotEmpty()) {
                            item(key = "recent-label") { SectionLabel(stringResource(R.string.recent), Modifier.padding(bottom = 12.dp)) }
                            item(key = "recent") { AlbumGrid(state.recentAlbums.take(3), actions, Modifier.padding(bottom = 20.dp)) }
                        }
                        item(key = "all-label") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionLabel(stringResource(R.string.all_songs, state.tracks.size), Modifier.weight(1f))
                                Text(stringResource(state.sort.label), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                                IconBtn(Icons.Rounded.SwapVert, stringResource(R.string.cd_sort_by, stringResource(state.sort.label)), actions.onSort, tint = c.textSecondary, iconSize = 18.dp)
                            }
                        }
                        trackList(state.tracks, currentId, labels.library, false, actions)
                    }
                    LibraryTab.Albuns -> {
                        item(key = "albums-label") { SectionLabel(pluralStringResource(R.plurals.albums_count, state.albums.size, state.albums.size), Modifier.padding(bottom = 12.dp)) }
                        state.albums.chunked(3).forEachIndexed { i, row ->
                            item(key = "albums-$i") { AlbumGrid(row, actions, Modifier.padding(bottom = 12.dp)) }
                        }
                    }
                    LibraryTab.Playlists -> {
                        item(key = "pl-actions") {
                            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip(stringResource(R.string.new_playlist), selected = true, onClick = actions.onNewPlaylist)
                                Chip(stringResource(R.string.import_playlist), selected = false, onClick = actions.onImportPlaylist)
                            }
                        }
                        if (playlists.isEmpty()) {
                            item(key = "pl-empty") {
                                Text(
                                    stringResource(R.string.playlists_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = c.textSecondary,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }
                        }
                        items(playlists, key = { "pl-" + it.id }) { p -> PlaylistRow(p, trackById, actions) }
                    }
                    LibraryTab.Artistas -> {
                        items(state.artists, key = { "ar-" + it.name }) { a ->
                            GroupRow(Icons.Rounded.Person, a.name, pluralStringResource(R.plurals.albums_count, a.albumCount, a.albumCount) + " · " + pluralStringResource(R.plurals.songs_count, a.tracks.size, a.tracks.size)) {
                                actions.onOpenArtist(a.name)
                            }
                        }
                    }
                    LibraryTab.Generos -> {
                        if (state.genres.isEmpty()) {
                            item(key = "g-empty") {
                                Text(stringResource(R.string.genres_empty), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
                            }
                        }
                        items(state.genres, key = { "g-" + it.name.lowercase() }) { g ->
                            GroupRow(Icons.Rounded.Category, g.name, pluralStringResource(R.plurals.songs_count, g.tracks.size, g.tracks.size)) {
                                actions.onOpenGenre(g.name)
                            }
                        }
                    }
                    LibraryTab.Pastas -> {
                        items(state.folders, key = { "f-" + it.path }) { f ->
                            GroupRow(Icons.Rounded.Folder, f.name, pluralStringResource(R.plurals.songs_count, f.tracks.size, f.tracks.size) + " · " + f.path) {
                                actions.onOpenFolder(f.path)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trackList(
    tracks: List<Track>,
    currentId: Long?,
    source: String,
    showCount: Boolean,
    actions: LibraryActions,
) {
    if (showCount) item(key = "count") {
        SectionLabel(pluralStringResource(R.plurals.results_count, tracks.size, tracks.size), Modifier.padding(bottom = 8.dp))
    }
    itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
        TrackRow(t, onClick = { actions.onPlay(tracks, i, source) }, highlighted = t.id == currentId, onMore = { actions.onTrackMenu(t) })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeContent(
    state: LibraryState,
    favorites: Set<Long>,
    currentId: Long?,
    labels: LibraryLabels,
    actions: LibraryActions,
    smartProgress: SmartProgress,
    forYou: ForYouState,
) {
    if (smartProgress.running && smartProgress.total > 0) {
        item(key = "smart-progress") { SmartAnalysisCard(smartProgress) }
    }
    item(key = "shuffle-all") {
        val c = SonivellTheme.colors
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(c.accent)
                .clickable(onClick = actions.onShuffleAll)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.Shuffle, null, Modifier.size(28.dp), tint = c.onAccent)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.shuffle_all), style = MaterialTheme.typography.titleMedium, color = c.onAccent)
                Text(pluralStringResource(R.plurals.songs_count, state.allTracks.size, state.allTracks.size), style = MaterialTheme.typography.bodyMedium, color = c.onAccent)
            }
        }
    }
    forYou.pick?.let { pick ->
        item(key = "fy-pick") {
            PickCard(pick, forYou.pickNeverPlayed) { actions.onPlay(listOf(pick), 0, labels.forYou) }
        }
    }
    if (forYou.mixSeeds.isNotEmpty()) {
        item(key = "fy-mixes") {
            MixRow(stringResource(R.string.fy_mixes), forYou.mixSeeds, actions.onPlayMix)
        }
    }
    trackRow("fy-unfinished", R.string.fy_unfinished, forYou.unfinished, labels.forYou, actions)
    trackRow("fy-hour", R.string.fy_this_hour, forYou.thisHour, labels.forYou, actions)
    trackRow("fy-forgotten", R.string.fy_forgotten, forYou.forgottenFavorites, labels.forYou, actions)
    if (forYou.isEmpty) {
        item(key = "fy-hint") {
            Text(
                stringResource(R.string.fy_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = SonivellTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
    if (state.recentAlbums.isNotEmpty()) {
        item(key = "home-recent-label") { SectionLabel(stringResource(R.string.recently_added), Modifier.padding(bottom = 12.dp)) }
        state.recentAlbums.chunked(3).forEachIndexed { i, row ->
            item(key = "home-recent-$i") { AlbumGrid(row, actions, Modifier.padding(bottom = 12.dp)) }
        }
    }
    val favs = state.allTracks.filter { it.id in favorites }
    if (favs.isNotEmpty()) {
        item(key = "fav-label") { SectionLabel(stringResource(R.string.liked_count, favs.size), Modifier.padding(top = 12.dp, bottom = 4.dp)) }
        itemsIndexed(favs, key = { _, t -> "fav-${t.id}" }) { i, t ->
            TrackRow(t, onClick = { actions.onPlay(favs, i, labels.liked) }, highlighted = t.id == currentId, onMore = { actions.onTrackMenu(t) })
        }
    }
}

/** Uma linha horizontal de faixas, com título; some quando está vazia. */
private fun androidx.compose.foundation.lazy.LazyListScope.trackRow(
    key: String,
    @StringRes title: Int,
    tracks: List<Track>,
    source: String,
    actions: LibraryActions,
) {
    if (tracks.isEmpty()) return
    item(key = key) {
        Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(title))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                    TrackCard(t, subtitle = t.artist) { actions.onPlay(tracks, i, source) }
                }
            }
        }
    }
}

@Composable
private fun TrackCard(track: Track, subtitle: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Column(
        Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AlbumArt(track, Modifier.size(128.dp), corner = 14.dp)
        Column {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Mixes pelo som (edição SMART): cada cartão começa uma fila SMART a partir de uma faixa. */
@Composable
private fun MixRow(title: String, seeds: List<Track>, onPlayMix: (Track) -> Unit) {
    val c = SonivellTheme.colors
    Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), tint = c.accent)
            SectionLabel(title)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(seeds, key = { "mix-" + it.id }) { seed ->
                Column(
                    Modifier
                        .width(148.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .clickable { onPlayMix(seed) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AlbumArt(seed, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp, detailed = true)
                    Text(
                        stringResource(R.string.fy_mix_of, seed.artist),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.fy_mix_from, seed.title),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Destaque do dia: uma faixa para redescobrir. */
@Composable
private fun PickCard(track: Track, neverPlayed: Boolean, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumArt(track, Modifier.size(84.dp), corner = 14.dp, detailed = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel(stringResource(R.string.fy_pick))
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (neverPlayed) R.string.fy_pick_never else R.string.fy_pick_long),
                style = MaterialTheme.typography.labelMedium,
                color = c.accent,
            )
        }
        IconBtn(Icons.Rounded.PlayArrow, stringResource(R.string.cd_play), onPlay, background = c.accent, tint = c.onAccent)
    }
}

/** Cartão discreto com o progresso da análise do som (edição smart). */
@Composable
private fun SmartAnalysisCard(progress: SmartProgress) {
    val c = SonivellTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = c.accent)
            Text(
                stringResource(R.string.smart_analysing),
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.smart_progress, progress.analysed, progress.total),
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
            )
        }
        val fraction = if (progress.total > 0) progress.analysed.toFloat() / progress.total else 0f
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.track)) {
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(c.accent))
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val searchLabel = stringResource(R.string.cd_search)
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = c.textSecondary)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel },
            )
        }
        if (value.isNotEmpty()) {
            IconBtn(Icons.Rounded.Close, stringResource(R.string.cd_clear_search), { onChange("") }, tint = c.textSecondary, iconSize = 18.dp)
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, actions: LibraryActions, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        albums.forEach { album ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { actions.onOpenAlbum(album.id) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AlbumArt(album.tracks.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp)
                Text(
                    album.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SonivellTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        repeat(3 - albums.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Linha de playlist: capa (primeira faixa), nome e total. */
@Composable
private fun PlaylistRow(playlist: Playlist, trackById: Map<Long, Track>, actions: LibraryActions) {
    val c = SonivellTheme.colors
    val tracks = playlist.trackIds.mapNotNull(trackById::get)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { actions.onOpenPlaylist(playlist.id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(tracks.firstOrNull(), Modifier.size(52.dp), corner = 12.dp, detailed = true)
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.songs_count, tracks.size, tracks.size) + " · " + formatDuration(tracks.sumOf { it.durationMs }),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun GroupRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(c.surface),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.textSecondary) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
