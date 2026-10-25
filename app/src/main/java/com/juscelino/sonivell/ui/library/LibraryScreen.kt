package com.juscelino.sonivell.ui.library

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
import androidx.compose.material.icons.rounded.Close
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

data class LibraryActions(
    val onQuery: (String) -> Unit,
    val onTab: (LibraryTab) -> Unit,
    val onSort: () -> Unit,
    val onRefresh: () -> Unit,
    val onPlay: (tracks: List<Track>, index: Int, source: String) -> Unit,
    val onShuffleAll: () -> Unit,
)

@Composable
fun LibraryScreen(
    mode: LibraryMode,
    state: LibraryState,
    favorites: Set<Long>,
    currentId: Long?,
    contentPadding: PaddingValues,
    actions: LibraryActions,
) {
    val c = SonivellTheme.colors
    val focus = remember { FocusRequester() }
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
                    when (mode) {
                        LibraryMode.Inicio -> "Início"
                        LibraryMode.Buscar -> "Buscar"
                        LibraryMode.Biblioteca -> "Biblioteca"
                    },
                    style = MaterialTheme.typography.displaySmall,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconBtn(Icons.Rounded.Refresh, "Atualizar biblioteca", actions.onRefresh, background = c.surface, iconSize = 20.dp)
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
                    "Nenhuma música encontrada no aparelho. Copie arquivos de áudio para o celular e toque em atualizar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
            return@LazyColumn
        }

        when (mode) {
            LibraryMode.Inicio -> homeContent(state, favorites, currentId, actions)
            LibraryMode.Buscar -> trackList(state.tracks, currentId, "Busca", state.query.isNotBlank(), actions)
            LibraryMode.Biblioteca -> {
                item(key = "chips") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LibraryTab.entries.forEach { t ->
                            Chip(t.label, selected = state.tab == t, onClick = { actions.onTab(t) })
                        }
                    }
                }
                when (state.tab) {
                    LibraryTab.Musicas -> {
                        if (state.query.isBlank() && state.recentAlbums.isNotEmpty()) {
                            item(key = "recent-label") { SectionLabel("Recentes", Modifier.padding(bottom = 12.dp)) }
                            item(key = "recent") { AlbumGrid(state.recentAlbums.take(3), actions, Modifier.padding(bottom = 20.dp)) }
                        }
                        item(key = "all-label") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionLabel("Todas as músicas · ${state.tracks.size}", Modifier.weight(1f))
                                Text(state.sort.label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                                IconBtn(Icons.Rounded.SwapVert, "Ordenar por: ${state.sort.label}", actions.onSort, tint = c.textSecondary, iconSize = 18.dp)
                            }
                        }
                        trackList(state.tracks, currentId, "Biblioteca", false, actions)
                    }
                    LibraryTab.Albuns -> {
                        item(key = "albums-label") { SectionLabel("${state.albums.size} álbuns", Modifier.padding(bottom = 12.dp)) }
                        state.albums.chunked(3).forEachIndexed { i, row ->
                            item(key = "albums-$i") { AlbumGrid(row, actions, Modifier.padding(bottom = 12.dp)) }
                        }
                    }
                    LibraryTab.Artistas -> {
                        items(state.artists, key = { "ar-" + it.name }) { a ->
                            GroupRow(Icons.Rounded.Person, a.name, "${a.albumCount} álbuns · ${a.tracks.size} músicas") {
                                actions.onPlay(a.tracks, 0, a.name)
                            }
                        }
                    }
                    LibraryTab.Pastas -> {
                        items(state.folders, key = { "f-" + it.path }) { f ->
                            GroupRow(Icons.Rounded.Folder, f.name, "${f.tracks.size} músicas · ${f.path}") {
                                actions.onPlay(f.tracks, 0, f.name)
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
        SectionLabel("${tracks.size} resultados", Modifier.padding(bottom = 8.dp))
    }
    itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
        TrackRow(t, onClick = { actions.onPlay(tracks, i, source) }, highlighted = t.id == currentId)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.homeContent(
    state: LibraryState,
    favorites: Set<Long>,
    currentId: Long?,
    actions: LibraryActions,
) {
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
                Text("Tocar tudo no aleatório", style = MaterialTheme.typography.titleMedium, color = c.onAccent)
                Text("${state.allTracks.size} músicas", style = MaterialTheme.typography.bodyMedium, color = c.onAccent)
            }
        }
    }
    if (state.recentAlbums.isNotEmpty()) {
        item(key = "home-recent-label") { SectionLabel("Adicionadas recentemente", Modifier.padding(bottom = 12.dp)) }
        state.recentAlbums.chunked(3).forEachIndexed { i, row ->
            item(key = "home-recent-$i") { AlbumGrid(row, actions, Modifier.padding(bottom = 12.dp)) }
        }
    }
    val favs = state.allTracks.filter { it.id in favorites }
    if (favs.isNotEmpty()) {
        item(key = "fav-label") { SectionLabel("Curtidas · ${favs.size}", Modifier.padding(top = 12.dp, bottom = 4.dp)) }
        itemsIndexed(favs, key = { _, t -> "fav-${t.id}" }) { i, t ->
            TrackRow(t, onClick = { actions.onPlay(favs, i, "Curtidas") }, highlighted = t.id == currentId)
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
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
                Text("Músicas, artistas, álbuns", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Buscar na biblioteca" },
            )
        }
        if (value.isNotEmpty()) {
            IconBtn(Icons.Rounded.Close, "Limpar busca", { onChange("") }, tint = c.textSecondary, iconSize = 18.dp)
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
                    .clickable { actions.onPlay(album.tracks, 0, album.title) },
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
