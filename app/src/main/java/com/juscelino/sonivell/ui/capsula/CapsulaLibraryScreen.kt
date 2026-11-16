package com.juscelino.sonivell.ui.capsula

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.ALBUM_SORTS
import com.juscelino.sonivell.ARTIST_SORTS
import com.juscelino.sonivell.FOLDER_SORTS
import com.juscelino.sonivell.LibraryState
import com.juscelino.sonivell.R
import com.juscelino.sonivell.SortOrder
import com.juscelino.sonivell.SortTarget
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.Ranked
import com.juscelino.sonivell.data.SmartPlaylist
import com.juscelino.sonivell.data.SmartPlaylists
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.data.realGenreCount
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.CoverCollage
import com.juscelino.sonivell.ui.components.SonivellSheet
import com.juscelino.sonivell.ui.library.GenreMosaic
import com.juscelino.sonivell.ui.library.GenreTags
import com.juscelino.sonivell.ui.library.LibraryActions
import com.juscelino.sonivell.ui.library.genreBrowse
import com.juscelino.sonivell.ui.sonivell.SvSortButton
import com.juscelino.sonivell.ui.sonivell.SvSortHeader
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlinx.coroutines.delay

/** Filtros da Biblioteca do Cápsula, na ordem dos chips. */
enum class CapsulaLibTab(val label: Int) {
    Tudo(R.string.sv_all),
    Playlists(R.string.cap_short_playlists),
    Albuns(R.string.sv_albums),
    Artistas(R.string.sv_artists),
    Musicas(R.string.tab_songs),
    Generos(R.string.sv_genres),
    Pastas(R.string.tab_folders),
}

/**
 * Biblioteca do Cápsula: chips de filtro, três atalhos em vidro (Curtidas · Pastas · Recentes), os
 * gêneros no Mosaico (o gênero que mais aparece em destaque, os outros pelo tamanho) e as playlists.
 * Remover uma playlist pede confirmação e ainda dá alguns segundos para desfazer.
 */
@Composable
fun CapsulaLibraryScreen(
    state: LibraryState,
    favorites: Set<Long>,
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylist>,
    genreOfMonth: Ranked<String>?,
    contentPadding: PaddingValues,
    actions: LibraryActions,
    tab: CapsulaLibTab,
    onTab: (CapsulaLibTab) -> Unit,
    playlistTracks: (Playlist) -> List<Track>,
    onDeletePlaylist: (Playlist) -> Unit,
    onRestorePlaylist: (Playlist, Int) -> Unit,
    tagBannerCount: Int = 0,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val resources = LocalContext.current.resources
    var confirming by remember { mutableStateOf<Playlist?>(null) }
    var removed by remember { mutableStateOf<Pair<Playlist, Int>?>(null) }
    val decades = remember(state.allTracks) { SmartPlaylists.decades(state.allTracks) }

    // O "Desfazer" some sozinho depois de alguns segundos.
    LaunchedEffect(removed) {
        if (removed != null) {
            delay(UNDO_MS)
            removed = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
                start = 20.dp,
                end = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                CapsulaHeader(stringResource(R.string.sv_library_title)) {
                    GlassCircle(icons.search, stringResource(R.string.cd_search_library), actions.onOpenSearch)
                    GlassCircle(icons.add, stringResource(R.string.action_playlist), actions.onNewPlaylist)
                }
            }
            if (tagBannerCount > 0) {
                item(key = "tags-banner") {
                    com.juscelino.sonivell.ui.library.TagsBanner(tagBannerCount, actions.onOpenOrganize, actions.onDismissTagBanner)
                }
            }
            item(key = "chips") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CapsulaLibTab.entries.forEach { t ->
                        FilterChipPill(stringResource(t.label), t == tab) { onTab(t) }
                    }
                }
            }

            when (tab) {
                CapsulaLibTab.Tudo -> {
                    item(key = "tiles") {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickTile(icons.liked, stringResource(R.string.source_liked), pluralStringResource(R.plurals.songs_count, favorites.size, favorites.size), c.accent, Modifier.weight(1f)) {
                                actions.onOpenAuto("curtidas")
                            }
                            QuickTile(icons.folder, stringResource(R.string.tab_folders), pluralStringResource(R.plurals.folders_count, state.folders.size, state.folders.size), c.textTertiary, Modifier.weight(1f)) {
                                onTab(CapsulaLibTab.Pastas)
                            }
                            QuickTile(icons.recent, stringResource(R.string.cap_recent), stringResource(R.string.cap_last_30_days), c.textTertiary, Modifier.weight(1f)) {
                                actions.onOpenAuto("recentes")
                            }
                        }
                    }
                    // Gêneros no Mosaico (sem a grade colorida): o maior em destaque, os outros pelo tamanho.
                    item(key = "genres") {
                        val good = remember(state.genres) {
                            state.genres.filterNot { GenreTags.isGeneric(it.name) }.sortedByDescending { it.tracks.size }
                        }
                        if (good.isNotEmpty()) {
                            Column(Modifier.padding(top = 8.dp)) {
                                GenreMosaic(good, total = realGenreCount(state.genres), actions = actions)
                            }
                        }
                    }
                    playlistSection(this, playlists, smartPlaylists, actions, playlistTracks, onAsk = { confirming = it }, withTitle = true)
                }
                CapsulaLibTab.Playlists -> playlistSection(this, playlists, smartPlaylists, actions, playlistTracks, onAsk = { confirming = it }, withTitle = false)
                CapsulaLibTab.Albuns -> {
                    item(key = "al-sort") {
                        SvSortHeader(pluralStringResource(R.plurals.albums_count, state.albums.size, state.albums.size)) {
                            SvSortButton(
                                ALBUM_SORTS, state.sorts.albums, { it.label }, SortTarget.Albuns in state.sorts.reversed,
                                { actions.onSetGroupSort(SortTarget.Albuns, it) }, { actions.onToggleSortReversed(SortTarget.Albuns) },
                            )
                        }
                    }
                    items(state.albums, key = { "al" + it.id }) { album ->
                        ListRow(album.title, album.artist, { actions.onOpenAlbum(album.id) }) { AlbumArt(album.tracks.firstOrNull(), Modifier.size(52.dp), corner = 14.dp) }
                    }
                }
                CapsulaLibTab.Artistas -> {
                    item(key = "ar-sort") {
                        SvSortHeader(pluralStringResource(R.plurals.artists_count, state.artists.size, state.artists.size)) {
                            SvSortButton(
                                ARTIST_SORTS, state.sorts.artists, { it.label }, SortTarget.Artistas in state.sorts.reversed,
                                { actions.onSetGroupSort(SortTarget.Artistas, it) }, { actions.onToggleSortReversed(SortTarget.Artistas) },
                            )
                        }
                    }
                    items(state.artists, key = { "ar" + it.name }) { artist ->
                        ListRow(artist.name, pluralStringResource(R.plurals.songs_count, artist.tracks.size, artist.tracks.size), { actions.onOpenArtist(artist.name) }) {
                            AlbumArt(artist.tracks.firstOrNull(), Modifier.size(52.dp), corner = 26.dp)
                        }
                    }
                }
                CapsulaLibTab.Musicas -> {
                    val list = state.tracks.ifEmpty { state.allTracks }
                    val source = resources.getString(R.string.tab_songs)
                    item(key = "mu-sort") {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(pluralStringResource(R.plurals.songs_count, list.size, list.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.textSecondary, modifier = Modifier.weight(1f))
                            if (state.query.isBlank()) SvSortButton(
                                SortOrder.entries, state.sort, { it.label }, SortTarget.Musicas in state.sorts.reversed,
                                actions.onSetSort, { actions.onToggleSortReversed(SortTarget.Musicas) },
                            )
                            PillButton(stringResource(R.string.mode_shuffle), icons.shuffle, actions.onShuffleAll, filled = true)
                        }
                    }
                    itemsIndexed(list, key = { _, t -> "mu" + t.id }) { i, t ->
                        ListRow(t.title, t.artist + " · " + formatDuration(t.durationMs), { actions.onPlay(list, i, source) }, onMore = { actions.onTrackMenu(t) }) {
                            AlbumArt(t, Modifier.size(52.dp), corner = 14.dp)
                        }
                    }
                }
                CapsulaLibTab.Generos -> genreBrowse(state, decades, genreOfMonth, actions)
                CapsulaLibTab.Pastas -> {
                    item(key = "fo-sort") {
                        SvSortHeader(pluralStringResource(R.plurals.folders_count, state.folders.size, state.folders.size)) {
                            SvSortButton(
                                FOLDER_SORTS, state.sorts.folders, { it.label }, SortTarget.Pastas in state.sorts.reversed,
                                { actions.onSetGroupSort(SortTarget.Pastas, it) }, { actions.onToggleSortReversed(SortTarget.Pastas) },
                            )
                        }
                    }
                    items(state.folders, key = { "fo" + it.path }) { folder ->
                        ListRow(folder.name, pluralStringResource(R.plurals.songs_count, folder.tracks.size, folder.tracks.size), { actions.onOpenFolder(folder.path) }) {
                            IconBubble(icons.folder, tint = c.accent, background = c.accent.copy(alpha = 0.14f), size = 52.dp)
                        }
                    }
                }
            }
        }

        removed?.let { (p, index) ->
            UndoPill(
                stringResource(R.string.cap_playlist_removed, p.name),
                onUndo = {
                    onRestorePlaylist(p, index)
                    removed = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 12.dp),
            )
        }
    }

    confirming?.let { p ->
        ConfirmRemoveSheet(
            name = p.name,
            onCancel = { confirming = null },
            onConfirm = {
                val index = playlists.indexOfFirst { it.id == p.id }.coerceAtLeast(0)
                onDeletePlaylist(p)
                removed = p to index
                confirming = null
            },
        )
    }
}

/** Seção de playlists: as inteligentes e as do usuário, com o menu de remover nas do usuário. */
private fun playlistSection(
    scope: LazyListScope,
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylist>,
    actions: LibraryActions,
    playlistTracks: (Playlist) -> List<Track>,
    onAsk: (Playlist) -> Unit,
    withTitle: Boolean,
) = with(scope) {
    if (withTitle) {
        item(key = "pl-title") {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CapTitle(stringResource(R.string.cap_short_playlists), Modifier.weight(1f))
                PillButton(stringResource(R.string.action_playlist), SonivellTheme.icons.add, actions.onNewPlaylist, Modifier.heightIn(max = 40.dp))
            }
        }
    }
    items(smartPlaylists, key = { "sp" + it.id }) { sp ->
        ListRow(sp.name, stringResource(R.string.smart_playlists), { actions.onOpenSmartPlaylist(sp.id) }) {
            IconBubble(SonivellTheme.icons.smart, tint = SonivellTheme.colors.accent, background = SonivellTheme.colors.accent.copy(alpha = 0.14f), size = 52.dp)
        }
    }
    items(playlists, key = { "p" + it.id }) { p ->
        val tracks = playlistTracks(p)
        ListRow(
            p.name,
            pluralStringResource(R.plurals.songs_count, p.trackIds.size, p.trackIds.size),
            { actions.onOpenPlaylist(p.id) },
            onMore = { onAsk(p) },
            moreLabel = stringResource(R.string.cap_playlist_options, p.name),
        ) {
            val covers = remember(tracks) { tracks.distinctBy { it.albumId }.take(4) }
            if (covers.isNotEmpty()) CoverCollage(covers, Modifier.size(52.dp), corner = 14.dp)
            else IconBubble(SonivellTheme.icons.musicNote, size = 52.dp)
        }
    }
    if (playlists.isEmpty() && smartPlaylists.isEmpty()) {
        item(key = "pl-empty") {
            Text(
                stringResource(R.string.cap_no_playlists),
                style = MaterialTheme.typography.bodyLarge,
                color = SonivellTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, title: String, subtitle: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Column(
        modifier
            .capsulaGlass(RoundedCornerShape(20.dp), elevation = 4.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconBubble(icon, tint = tint, background = tint.copy(alpha = 0.16f), size = 34.dp)
        Spacer(Modifier.size(2.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Linha de lista: arte, título, apoio e, se houver, o botão de opções. */
@Composable
internal fun ListRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    moreLabel: String? = null,
    art: @Composable () -> Unit,
) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        art()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onMore != null) {
            val label = moreLabel ?: stringResource(R.string.cd_more_options)
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onMore),
                contentAlignment = Alignment.Center,
            ) { Icon(SonivellTheme.icons.more, label, Modifier.size(20.dp), tint = c.textSecondary) }
        }
    }
}

/** Confirmação de remover em folha de vidro: ícone de lixeira, o nome e Cancelar · Remover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmRemoveSheet(name: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = SonivellTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    SonivellSheet(onDismissRequest = onCancel, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                IconBubble(SonivellTheme.icons.delete, tint = Color(0xFFF87171), background = Color(0xFFF87171).copy(alpha = 0.16f), size = 46.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.cap_remove_title, name), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
                    Text(stringResource(R.string.cap_remove_message), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.cancel), null, onCancel, Modifier.weight(1f))
                PillButton(stringResource(R.string.cap_remove), null, onConfirm, Modifier.weight(1f), danger = true)
            }
        }
    }
}

private const val UNDO_MS = 5_000L
