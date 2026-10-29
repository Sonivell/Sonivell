package com.juscelino.sonivell.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.juscelino.sonivell.ui.components.FastScroller
import com.juscelino.sonivell.data.sortKey
import com.juscelino.sonivell.data.indexLetter
import com.juscelino.sonivell.SortOrder
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.LibraryState
import com.juscelino.sonivell.LibraryTab
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Album
import com.juscelino.sonivell.data.Artist
import com.juscelino.sonivell.data.ForYouState
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.Search
import com.juscelino.sonivell.data.SmartPlaylist
import com.juscelino.sonivell.data.SmartPlaylists
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.PlaySquircle
import com.juscelino.sonivell.ui.components.TrackRow
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.util.Calendar

/*
 * Layout Essencial: as mesmas três telas (Para você, Buscar, Biblioteca) com o mínimo de
 * elementos. A capa é o centro visual; o lima fica só no que toca e no que está selecionado.
 */

private const val SIDE = 20

/** Chave de ordenação que ignora símbolos no começo ("!Tchau radar!", "#…", "(…"). */
private enum class EssAlbumMode { AZ, Added }

private enum class EssResultFilter { All, Songs, Albums, Artists }

@Composable
fun LibraryScreenEssencial(
    mode: LibraryMode,
    state: LibraryState,
    favorites: Set<Long>,
    currentId: Long?,
    contentPadding: PaddingValues,
    actions: LibraryActions,
    forYou: ForYouState = ForYouState(),
    playlists: List<Playlist> = emptyList(),
    smartPlaylists: List<SmartPlaylist> = emptyList(),
    semanticResults: List<Track> = emptyList(),
    recentSearches: List<String> = emptyList(),
    tagBannerCount: Int = 0,
) {
    val c = SonivellTheme.colors
    val labels = EssLabels(
        search = stringResource(R.string.source_search),
        library = stringResource(R.string.nav_library),
        liked = stringResource(R.string.source_liked),
        forYou = stringResource(R.string.nav_home),
    )
    val focus = remember { FocusRequester() }
    LaunchedEffect(mode) { if (mode == LibraryMode.Buscar) runCatching { focus.requestFocus() } }
    // Trocar o texto da busca volta o filtro dos resultados para "Tudo".
    LaunchedEffect(state.query) { EssSearchFilter.value = EssResultFilter.All }
    // A Biblioteca Essencial não tem "Tudo": abre direto nos álbuns.
    LaunchedEffect(mode, state.tab) { if (mode == LibraryMode.Biblioteca && state.tab == LibraryTab.Tudo) actions.onTab(LibraryTab.Albuns) }

    val listState = rememberLazyListState()
    // Letras do índice A–Z: uma por linha do fim da lista (as grades contam uma por fileira de 3).
    val albumMode = EssAlbumSort.value
    val scrollLetters = remember(mode, state.tab, state.tracks, state.sort, state.albums, state.artists, state.folders, albumMode) {
        if (mode != LibraryMode.Biblioteca) emptyList()
        else when (state.tab) {
            LibraryTab.Albuns, LibraryTab.Tudo ->
                if (albumMode == EssAlbumMode.AZ) essSortAlbums(state.albums, albumMode).chunked(3).map { indexLetter(it.first().title) } else emptyList()
            LibraryTab.Artistas -> state.artists.sortedBy { sortKey(it.name) }.chunked(3).map { indexLetter(it.first().name) }
            LibraryTab.Pastas -> state.folders.map { indexLetter(it.name) }
            LibraryTab.Musicas -> when (state.sort) {
                SortOrder.Titulo -> state.tracks.map { indexLetter(it.title) }
                SortOrder.Artista -> state.tracks.map { indexLetter(it.artist) }
                SortOrder.Album -> state.tracks.map { indexLetter(it.album) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
    // Na grade, 40 fileiras já são 120 capas.
    val withIndex = scrollLetters.size >= 40

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            end = if (withIndex) 14.dp else 0.dp,
        ),
    ) {
        item(key = "header") { EssHeader(mode, actions) }
        if (mode == LibraryMode.Buscar) {
            item(key = "field") { EssSearchPill(state.query, actions.onQuery, Modifier.padding(horizontal = SIDE.dp).padding(bottom = 22.dp).focusRequester(focus)) }
        }
        if (state.loading && state.allTracks.isEmpty()) {
            item(key = "loading") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = SIDE.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.5.dp)
                    Text(stringResource(R.string.browse_loading_title), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                }
            }
            return@LazyColumn
        }
        if (state.allTracks.isEmpty()) {
            item(key = "empty") { EssMuted(stringResource(R.string.library_empty), Modifier.padding(horizontal = SIDE.dp)) }
            return@LazyColumn
        }
        when (mode) {
            LibraryMode.Inicio -> essHome(state, favorites, forYou, labels, actions)
            LibraryMode.Buscar -> {
                if (Search.parse(state.query).isEmpty) {
                    if (recentSearches.isNotEmpty()) {
                        item(key = "recent") {
                            RecentSearches(recentSearches, actions.onQuery, actions.onRemoveRecentSearch, Modifier.padding(horizontal = SIDE.dp).padding(bottom = 26.dp))
                        }
                    }
                    essBrowse(state, actions)
                }
                else essResults(state, currentId, labels, actions, playlists, semanticResults)
            }
            LibraryMode.Biblioteca -> {
                if (tagBannerCount > 0) {
                    item(key = "tags-banner") {
                        TagsBanner(tagBannerCount, actions.onOpenOrganize, actions.onDismissTagBanner, Modifier.padding(horizontal = SIDE.dp).padding(bottom = 16.dp))
                    }
                }
                essLibrary(state, favorites, currentId, labels, actions, playlists, smartPlaylists)
            }
        }
    }
    FastScroller(
        listState,
        scrollLetters,
        Modifier
            .align(Alignment.CenterEnd)
            .padding(top = contentPadding.calculateTopPadding() + 150.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp, end = 4.dp),
    )
    }
}

private data class EssLabels(val search: String, val library: String, val liked: String, val forYou: String)

// ---------------------------------------------------------------- peças comuns

@Composable
private fun EssHeader(mode: LibraryMode, actions: LibraryActions) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val title = when (mode) {
        LibraryMode.Inicio -> stringResource(
            when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 5..11 -> R.string.greeting_morning
                in 12..17 -> R.string.greeting_afternoon
                else -> R.string.greeting_evening
            }
        )
        LibraryMode.Buscar -> stringResource(R.string.nav_search)
        LibraryMode.Biblioteca -> stringResource(R.string.nav_library)
    }
    Row(
        Modifier.fillMaxWidth().padding(start = SIDE.dp, end = 8.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, letterSpacing = (-0.8).sp),
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        when (mode) {
            LibraryMode.Inicio -> EssGhostIcon(icons.settings, stringResource(R.string.cd_settings), actions.onOpenSettings)
            LibraryMode.Biblioteca -> {
                EssGhostIcon(icons.search, stringResource(R.string.cd_search_library), actions.onOpenSearch)
                EssGhostIcon(icons.add, stringResource(R.string.cd_new_playlist), actions.onNewPlaylist)
            }
            LibraryMode.Buscar -> Unit
        }
    }
}

@Composable
private fun EssGhostIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(22.dp), tint = SonivellTheme.colors.textTertiary) }
}

@Composable
private fun EssSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, letterSpacing = (-0.2).sp),
        fontWeight = FontWeight.ExtraBold,
        color = SonivellTheme.colors.textPrimary,
        modifier = modifier.padding(horizontal = SIDE.dp).padding(bottom = 12.dp),
    )
}

@Composable
private fun EssMuted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = SonivellTheme.colors.textSecondary, modifier = modifier.padding(vertical = 12.dp))
}

@Composable
private fun EssChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .height(36.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(c.accent) else Modifier.border(1.dp, c.outline, shape))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, color = if (selected) c.onAccent else c.textTertiary)
    }
}

/** Até quatro capas diferentes em mosaico 2×2; com menos de quatro, uma capa só. */
@Composable
private fun EssCollage(tracks: List<Track>, modifier: Modifier, corner: Dp) {
    val covers = remember(tracks) { tracks.distinctBy { it.albumId }.take(4) }
    val shape = RoundedCornerShape(corner)
    if (covers.size < 4) {
        AlbumArt(covers.firstOrNull(), modifier, corner = corner)
        return
    }
    Column(modifier.clip(shape).background(SonivellTheme.colors.background), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        covers.chunked(2).forEach { pair ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                pair.forEach { AlbumArt(it, Modifier.weight(1f).fillMaxSize(), corner = 0.dp) }
            }
        }
    }
}

/** Capa com título e subtítulo embaixo: álbuns, mixes, artistas. */
@Composable
private fun EssCoverCard(
    title: String,
    subtitle: String?,
    modifier: Modifier,
    round: Boolean = false,
    corner: Dp = 18.dp,
    onClick: () -> Unit,
    art: @Composable (Modifier) -> Unit,
) {
    val c = SonivellTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(corner)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        art(Modifier.fillMaxWidth().aspectRatio(1f).then(if (round) Modifier.clip(CircleShape) else Modifier))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Grade de 3 colunas dentro de uma LazyColumn (uma linha por item). */
private fun <T> LazyListScope.essCoverGrid(keyPrefix: String, items: List<T>, key: (T) -> Any, cell: @Composable (T, Modifier) -> Unit) {
    items.chunked(3).forEachIndexed { i, row ->
        item(key = "$keyPrefix-$i-${key(row.first())}") {
            Row(Modifier.fillMaxWidth().padding(horizontal = SIDE.dp).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Para você

private fun LazyListScope.essHome(state: LibraryState, favorites: Set<Long>, forYou: ForYouState, labels: EssLabels, actions: LibraryActions) {
    val cont = forYou.unfinished.firstOrNull()
    val pick = forYou.pick
    when {
        cont != null -> item(key = "hero") {
            val pos = forYou.resumeAtMs[cont.id] ?: 0L
            EssHero(
                track = cont,
                tag = stringResource(R.string.ess_continue),
                progress = (pos.toFloat() / cont.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f),
                meta = stringResource(R.string.ess_remaining, formatDuration((cont.durationMs - pos).coerceAtLeast(0L))),
                onPlay = { actions.onResume(cont, pos) },
            )
        }
        pick != null -> item(key = "hero") {
            EssHero(track = pick, tag = stringResource(R.string.fy_pick), progress = null, meta = null, onPlay = { actions.onPlay(listOf(pick), 0, labels.forYou) })
        }
    }

    item(key = "mixes") {
        val res = LocalContext.current.resources
        val favs = remember(state.allTracks, favorites) { state.allTracks.filter { it.id in favorites } }
        val mixes = remember(forYou, favs, res) {
            buildList {
                if (forYou.thisHour.isNotEmpty()) {
                    add(EssMix("now", res.getString(R.string.ess_mix_now), res.getString(R.string.ess_mix_now_sub), forYou.thisHour) {
                        actions.onPlay(forYou.thisHour, 0, res.getString(R.string.ess_mix_now))
                    })
                }
                forYou.mixSeeds.forEach { seed ->
                    add(EssMix("mix-${seed.id}", res.getString(R.string.fy_mix_of, seed.artist), res.getString(R.string.fy_sounds_like, seed.title), listOf(seed)) { actions.onPlayMix(seed) })
                }
                if (favs.isNotEmpty()) {
                    add(EssMix("liked", labels.liked, res.getQuantityString(R.plurals.songs_count, favs.size, favs.size), favs) { actions.onOpenAuto("curtidas") })
                }
            }
        }
        if (mixes.isNotEmpty()) {
            Column(Modifier.padding(top = 30.dp)) {
                EssSectionTitle(stringResource(R.string.ess_mixes))
                LazyRow(contentPadding = PaddingValues(horizontal = SIDE.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(mixes, key = { it.key }) { m ->
                        EssCoverCard(m.title, m.subtitle, Modifier.width(164.dp), corner = 22.dp, onClick = m.onClick) { mod -> EssCollage(m.tracks, mod, 22.dp) }
                    }
                }
            }
        }
    }

    if (forYou.forgottenFavorites.isNotEmpty()) {
        item(key = "again") {
            Column(Modifier.padding(top = 30.dp)) {
                EssSectionTitle(stringResource(R.string.ess_listen_again))
                LazyRow(contentPadding = PaddingValues(horizontal = SIDE.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(forYou.forgottenFavorites, key = { _, t -> "ag-${t.id}" }) { i, t ->
                        EssCoverCard(t.title, t.artist, Modifier.width(128.dp), onClick = { actions.onPlay(forYou.forgottenFavorites, i, labels.forYou) }) { mod -> AlbumArt(t, mod, corner = 18.dp) }
                    }
                }
            }
        }
    }

    if (state.recentAlbums.isNotEmpty()) {
        item(key = "added") {
            Column(Modifier.padding(top = 30.dp)) {
                EssSectionTitle(stringResource(R.string.recently_added))
                LazyRow(contentPadding = PaddingValues(horizontal = SIDE.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(state.recentAlbums, key = { "ra-${it.id}" }) { a ->
                        EssCoverCard(a.title, a.artist, Modifier.width(128.dp), onClick = { actions.onOpenAlbum(a.id) }) { mod -> AlbumArt(a.tracks.firstOrNull(), mod, corner = 18.dp) }
                    }
                }
            }
        }
    }
}

private class EssMix(val key: String, val title: String, val subtitle: String, val tracks: List<Track>, val onClick: () -> Unit)

/** A capa grande do topo: continuar de onde parou (ou uma sugestão). */
@Composable
private fun EssHero(track: Track, tag: String, progress: Float?, meta: String?, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    Column(
        Modifier
            .padding(horizontal = SIDE.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(c.surface)
            .clickable(onClick = onPlay),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f)) {
            AlbumArt(track, Modifier.fillMaxSize(), corner = 0.dp, detailed = true)
            Box(
                Modifier.padding(16.dp).height(28.dp).clip(RoundedCornerShape(999.dp)).background(c.background).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tag, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlaySquircle(false, onPlay, size = 56.dp, corner = 28.dp, iconSize = 22.dp, contentDescription = stringResource(R.string.cd_play_item, track.title))
            }
            if (progress != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.track)) {
                        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(c.accent))
                    }
                    if (meta != null) Text(meta, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Buscar

@Composable
private fun EssSearchPill(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val ink = c.background
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(999.dp)).background(c.textPrimary).padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(SonivellTheme.icons.search, null, Modifier.size(20.dp), tint = ink.copy(alpha = 0.75f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(stringResource(R.string.ess_search_hint), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = 0.6f))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = ink, fontWeight = FontWeight.Bold)),
                cursorBrush = SolidColor(ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            val clear = stringResource(R.string.cd_clear_search)
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = clear) { onChange("") }.semantics { contentDescription = clear },
                contentAlignment = Alignment.Center,
            ) { Icon(SonivellTheme.icons.close, null, Modifier.size(16.dp), tint = ink) }
        }
    }
}

/** Busca vazia: gêneros com as capas de cada um e as décadas como botões simples. */
private fun LazyListScope.essBrowse(state: LibraryState, actions: LibraryActions) {
    item(key = "genres") {
        val genres = remember(state.genres) { state.genres.filterNot { GenreTags.isGeneric(it.name) }.sortedByDescending { it.tracks.size } }
        if (genres.isEmpty()) return@item
        var all by rememberSaveable { mutableStateOf(false) }
        val shown = if (all) genres else genres.take(6)
        Column {
            EssSectionTitle(stringResource(R.string.browse_genres))
            Column(Modifier.padding(horizontal = SIDE.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                shown.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { g -> EssGenreCard(g.name, g.tracks, Modifier.weight(1f)) { actions.onOpenGenre(g.name) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (genres.size > 6) {
                    Text(
                        if (all) stringResource(R.string.browse_show_less) else stringResource(R.string.browse_show_all, genres.size),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = SonivellTheme.colors.accent,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { all = !all }.heightIn(min = 44.dp).padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
    item(key = "decades") {
        val decades = remember(state.allTracks) { SmartPlaylists.decades(state.allTracks).sorted() }
        if (decades.isEmpty()) return@item
        val res = LocalContext.current.resources
        Column(Modifier.padding(top = 26.dp)) {
            EssSectionTitle(stringResource(R.string.browse_decades))
            EssDecadeChips(decades, res, actions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EssDecadeChips(decades: List<Int>, res: android.content.res.Resources, actions: LibraryActions) {
    FlowRow(Modifier.padding(horizontal = SIDE.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        decades.forEach { d ->
            val name = res.getString(R.string.decade_label, if (d in 1900..1999) (d % 100).toString() else d.toString())
            EssChip(name, selected = false) { actions.onOpenAuto("decada-$d") }
        }
    }
}

/** Três capas lado a lado, com o nome e a quantidade embaixo. */
@Composable
private fun EssGenreCard(name: String, tracks: List<Track>, modifier: Modifier, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val covers = remember(tracks) { tracks.distinctBy { it.albumId }.take(3) }
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(18.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (covers.size < 3) AlbumArt(covers.firstOrNull(), Modifier.fillMaxSize(), corner = 0.dp)
            else covers.forEach { AlbumArt(it, Modifier.weight(1f).fillMaxSize(), corner = 0.dp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(tracks.size.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
        }
    }
}

private fun LazyListScope.essResults(
    state: LibraryState,
    currentId: Long?,
    labels: EssLabels,
    actions: LibraryActions,
    playlists: List<Playlist>,
    semanticResults: List<Track>,
) {
    val parsed = Search.parse(state.query)
    val hasWords = parsed.words.isNotEmpty()
    val artists = if (!hasWords) emptyList() else state.artists.filter { Search.matchesText(parsed, it.name) }.take(10)
    val albums = if (!hasWords) emptyList() else state.albums.filter { Search.matchesText(parsed, it.title) }.take(12)
    val foundPlaylists = if (!hasWords) emptyList() else playlists.filter { Search.matchesText(parsed, it.name) }
    val tracks = state.tracks

    item(key = "r-filters") {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = SIDE.dp).padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EssResultFilter.entries.forEach { f ->
                val label = stringResource(
                    when (f) {
                        EssResultFilter.All -> R.string.tab_all
                        EssResultFilter.Songs -> R.string.tab_songs
                        EssResultFilter.Albums -> R.string.tab_albums
                        EssResultFilter.Artists -> R.string.tab_artists
                    }
                )
                EssChip(label, selected = EssSearchFilter.value == f) { EssSearchFilter.value = f }
            }
        }
    }
    item(key = "r-body") {
        val filter = EssSearchFilter.value
        Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            if (filter == EssResultFilter.All) {
                val res = LocalContext.current.resources
                val a = artists.firstOrNull()
                val al = albums.firstOrNull()
                val t = tracks.firstOrNull()
                when {
                    a != null -> EssBestResult(a.name, res.getString(R.string.kind_artist_albums, res.getQuantityString(R.plurals.albums_count, a.albumCount, a.albumCount)), a.tracks.firstOrNull(), true,
                        onOpen = { actions.onOpenArtist(a.name) }, onPlay = { actions.onPlay(a.tracks, 0, a.name) })
                    al != null -> EssBestResult(al.title, res.getString(R.string.kind_album, al.artist), al.tracks.firstOrNull(), false,
                        onOpen = { actions.onOpenAlbum(al.id) }, onPlay = { actions.onPlay(al.tracks, 0, al.title) })
                    t != null -> EssBestResult(t.title, t.artist, t, false,
                        onOpen = { actions.onPlay(tracks, 0, labels.search) }, onPlay = { actions.onPlay(tracks, 0, labels.search) })
                }
            }
            if ((filter == EssResultFilter.All || filter == EssResultFilter.Songs) && tracks.isNotEmpty()) {
                val shown = if (filter == EssResultFilter.Songs) tracks.take(200) else tracks.take(4)
                Column {
                    EssSectionTitle(stringResource(R.string.tab_songs))
                    Column(Modifier.padding(horizontal = SIDE.dp - 8.dp)) {
                        shown.forEachIndexed { i, tr ->
                            TrackRow(tr, onClick = { actions.onPlay(tracks, i, labels.search) }, highlighted = tr.id == currentId, onMore = { actions.onTrackMenu(tr) })
                        }
                    }
                    if (filter == EssResultFilter.All && tracks.size > 4) {
                        Text(
                            stringResource(R.string.search_show_all, tracks.size),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = SonivellTheme.colors.accent,
                            modifier = Modifier.padding(horizontal = SIDE.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { EssSearchFilter.value = EssResultFilter.Songs }.heightIn(min = 44.dp).padding(vertical = 12.dp),
                        )
                    }
                }
            }
            if ((filter == EssResultFilter.All || filter == EssResultFilter.Albums) && albums.isNotEmpty()) {
                EssCoverStrip(stringResource(R.string.tab_albums), albums, filter == EssResultFilter.Albums, round = false,
                    title = { it.title }, subtitle = { it.artist }, cover = { it.tracks.firstOrNull() }, onClick = { actions.onOpenAlbum(it.id) })
            }
            if ((filter == EssResultFilter.All || filter == EssResultFilter.Artists) && artists.isNotEmpty()) {
                val kind = stringResource(R.string.kind_artist)
                EssCoverStrip(stringResource(R.string.tab_artists), artists, filter == EssResultFilter.Artists, round = true,
                    title = { it.name }, subtitle = { kind }, cover = { it.tracks.firstOrNull() }, onClick = { actions.onOpenArtist(it.name) })
            }
            if (filter == EssResultFilter.All && foundPlaylists.isNotEmpty()) {
                val byId = remember(state.allTracks) { state.allTracks.associateBy { it.id } }
                EssCoverStrip(stringResource(R.string.tab_playlists), foundPlaylists, false, round = false,
                    title = { it.name }, subtitle = { null }, cover = { p -> p.trackIds.firstNotNullOfOrNull(byId::get) }, onClick = { actions.onOpenPlaylist(it.id) })
            }
            if (filter == EssResultFilter.All && semanticResults.isNotEmpty()) {
                Column {
                    EssSectionTitle(stringResource(R.string.search_sounds_like))
                    Column(Modifier.padding(horizontal = SIDE.dp - 8.dp)) {
                        semanticResults.forEachIndexed { i, tr ->
                            TrackRow(tr, onClick = { actions.onPlay(semanticResults, i, labels.search) }, highlighted = tr.id == currentId, onMore = { actions.onTrackMenu(tr) })
                        }
                    }
                }
            }
            if (tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && semanticResults.isEmpty()) {
                EssMuted(stringResource(R.string.search_none), Modifier.padding(horizontal = SIDE.dp))
            }
        }
    }
}

/** Filtro dos resultados (Tudo, Músicas…): vive enquanto a tela existe. */
private object EssSearchFilter {
    var value by mutableStateOf(EssResultFilter.All)
}

@Composable
private fun EssBestResult(title: String, subtitle: String, track: Track?, round: Boolean, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    Column {
        EssSectionTitle(stringResource(R.string.search_best))
        Column(
            Modifier.padding(horizontal = SIDE.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surface).clickable(onClick = onOpen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AlbumArt(track, Modifier.size(112.dp).then(if (round) Modifier.clip(CircleShape) else Modifier), corner = if (round) 56.dp else 18.dp)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlaySquircle(false, onPlay, size = 56.dp, corner = 28.dp, iconSize = 22.dp, contentDescription = stringResource(R.string.cd_play_item, title))
            }
        }
    }
}

/** Uma fileira de capas; [asGrid] mostra tudo em grade (quando o filtro é só esse tipo). */
@Composable
private fun <T> EssCoverStrip(
    heading: String,
    list: List<T>,
    asGrid: Boolean,
    round: Boolean,
    title: (T) -> String,
    subtitle: (T) -> String?,
    cover: (T) -> Track?,
    onClick: (T) -> Unit,
) {
    Column {
        EssSectionTitle(heading)
        if (asGrid) {
            Column(Modifier.padding(horizontal = SIDE.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                list.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { x -> EssCoverCard(title(x), subtitle(x), Modifier.weight(1f), round = round, onClick = { onClick(x) }) { mod -> AlbumArt(cover(x), mod, corner = if (round) 999.dp else 16.dp) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = SIDE.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(list) { x ->
                    EssCoverCard(title(x), subtitle(x), Modifier.width(128.dp), round = round, onClick = { onClick(x) }) { mod -> AlbumArt(cover(x), mod, corner = if (round) 999.dp else 18.dp) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Biblioteca

private fun LazyListScope.essLibrary(
    state: LibraryState,
    favorites: Set<Long>,
    currentId: Long?,
    labels: EssLabels,
    actions: LibraryActions,
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylist>,
) {
    item(key = "l-tabs") {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = SIDE.dp).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(LibraryTab.Playlists, LibraryTab.Albuns, LibraryTab.Artistas, LibraryTab.Musicas, LibraryTab.Generos, LibraryTab.Pastas).forEach { t ->
                EssChip(stringResource(t.label), selected = state.tab == t) { actions.onTab(t) }
            }
        }
    }
    item(key = "l-pins") {
        val favs = remember(state.allTracks, favorites) { state.allTracks.filter { it.id in favorites } }
        val recent = remember(state.allTracks) { state.allTracks.sortedByDescending { it.dateAdded }.take(8) }
        Row(Modifier.padding(horizontal = SIDE.dp).padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EssPin(labels.liked, pluralStringResource(R.plurals.songs_count, favs.size, favs.size), Modifier.weight(1f), onClick = { actions.onOpenAuto("curtidas") }) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(SonivellTheme.colors.accent), contentAlignment = Alignment.Center) {
                    Icon(SonivellTheme.icons.liked, null, Modifier.size(20.dp), tint = SonivellTheme.colors.onAccent)
                }
            }
            EssPin(stringResource(R.string.ess_recent), stringResource(R.string.ess_recent_sub), Modifier.weight(1f), onClick = { actions.onOpenAuto("recentes") }) {
                EssCollage(recent, Modifier.size(48.dp), 12.dp)
            }
        }
    }

    when (state.tab) {
        LibraryTab.Albuns, LibraryTab.Tudo -> {
            item(key = "l-sort") { EssAlbumSortRow(state.albums.size) }
            val sorted = essSortAlbums(state.albums, EssAlbumSort.value)
            essCoverGrid("al", sorted, { it.id }) { a, mod ->
                EssCoverCard(a.title, a.artist, mod, corner = 14.dp, onClick = { actions.onOpenAlbum(a.id) }) { m -> AlbumArt(a.tracks.firstOrNull(), m, corner = 14.dp) }
            }
        }
        LibraryTab.Artistas -> {
            val sorted = state.artists.sortedBy { sortKey(it.name) }
            essCoverGrid("ar", sorted, { it.name }) { a, mod ->
                EssCoverCard(a.name, null, mod, round = true, onClick = { actions.onOpenArtist(a.name) }) { m -> AlbumArt(a.tracks.firstOrNull(), m, corner = 999.dp) }
            }
        }
        LibraryTab.Generos -> {
            if (state.genres.isEmpty()) item(key = "g-empty") { EssMuted(stringResource(R.string.genres_empty), Modifier.padding(horizontal = SIDE.dp)) }
            essCoverGrid("ge", state.genres.sortedByDescending { it.tracks.size }, { it.name }) { g, mod ->
                val res = LocalContext.current.resources
                EssCoverCard(g.name, res.getQuantityString(R.plurals.songs_count, g.tracks.size, g.tracks.size), mod, corner = 14.dp, onClick = { actions.onOpenGenre(g.name) }) { m -> EssCollage(g.tracks, m, 14.dp) }
            }
        }
        LibraryTab.Playlists -> essPlaylistRows(state, playlists, smartPlaylists, actions)
        LibraryTab.Pastas -> {
            items(state.folders, key = { "f-${it.path}" }) { f ->
                val res = LocalContext.current.resources
                EssListRow(f.name, res.getQuantityString(R.plurals.songs_count, f.tracks.size, f.tracks.size), onClick = { actions.onOpenFolder(f.path) }) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(SonivellTheme.colors.surface), contentAlignment = Alignment.Center) {
                        Icon(SonivellTheme.icons.folder, null, Modifier.size(22.dp), tint = SonivellTheme.colors.textSecondary)
                    }
                }
            }
        }
        LibraryTab.Musicas -> {
            item(key = "m-sort") {
                Row(Modifier.padding(horizontal = SIDE.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    EssSortLabel(stringResource(state.sort.label), actions.onSort)
                    Spacer(Modifier.weight(1f))
                    Text(pluralStringResource(R.plurals.songs_count, state.tracks.size, state.tracks.size), style = MaterialTheme.typography.labelMedium, color = SonivellTheme.colors.textSecondary)
                }
            }
            itemsIndexed(state.tracks, key = { _, t -> "m-${t.id}" }) { i, t ->
                Box(Modifier.padding(horizontal = SIDE.dp - 8.dp)) {
                    TrackRow(t, onClick = { actions.onPlay(state.tracks, i, labels.library) }, highlighted = t.id == currentId, onMore = { actions.onTrackMenu(t) })
                }
            }
        }
    }
}

private object EssAlbumSort {
    var value by mutableStateOf(EssAlbumMode.AZ)
}

private fun essSortAlbums(albums: List<Album>, sort: EssAlbumMode): List<Album> = when (sort) {
    EssAlbumMode.AZ -> albums.sortedBy { sortKey(it.title) }
    EssAlbumMode.Added -> albums.sortedByDescending { a -> a.tracks.maxOfOrNull { it.dateAdded } ?: 0L }
}

@Composable
private fun EssAlbumSortRow(count: Int) {
    val label = stringResource(if (EssAlbumSort.value == EssAlbumMode.AZ) R.string.ess_sort_az else R.string.ess_sort_added)
    Row(Modifier.padding(horizontal = SIDE.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        EssSortLabel(label) { EssAlbumSort.value = if (EssAlbumSort.value == EssAlbumMode.AZ) EssAlbumMode.Added else EssAlbumMode.AZ }
        Spacer(Modifier.weight(1f))
        Text(pluralStringResource(R.plurals.albums_count, count, count), style = MaterialTheme.typography.labelMedium, color = SonivellTheme.colors.textSecondary)
    }
}

@Composable
private fun EssSortLabel(label: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SonivellTheme.icons.sort, null, Modifier.size(16.dp), tint = c.textTertiary)
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.textTertiary)
    }
}

@Composable
private fun EssPin(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit, art: @Composable () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        modifier.height(64.dp).clip(RoundedCornerShape(18.dp)).background(c.surface).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        art()
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EssListRow(title: String, subtitle: String, onClick: () -> Unit, art: @Composable () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = SIDE.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        art()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun LazyListScope.essPlaylistRows(state: LibraryState, playlists: List<Playlist>, smartPlaylists: List<SmartPlaylist>, actions: LibraryActions) {
    item(key = "pl-actions") {
        Row(Modifier.padding(horizontal = SIDE.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EssChip(stringResource(R.string.new_smart_playlist), selected = false, onClick = actions.onNewSmartPlaylist)
            EssChip(stringResource(R.string.import_playlist), selected = false, onClick = actions.onImportPlaylist)
        }
    }
    if (playlists.isEmpty() && smartPlaylists.isEmpty()) {
        item(key = "pl-empty") { EssMuted(stringResource(R.string.playlists_empty), Modifier.padding(horizontal = SIDE.dp)) }
    }
    items(playlists, key = { "pl-${it.id}" }) { p ->
        val byId = remember(state.allTracks) { state.allTracks.associateBy { it.id } }
        val tracks = remember(p, byId) { p.trackIds.mapNotNull(byId::get) }
        val res = LocalContext.current.resources
        EssListRow(p.name, res.getString(R.string.kind_playlist, res.getQuantityString(R.plurals.songs_count, tracks.size, tracks.size)), onClick = { actions.onOpenPlaylist(p.id) }) {
            EssCollage(tracks, Modifier.size(52.dp), 12.dp)
        }
    }
    items(smartPlaylists, key = { "sp-${it.id}" }) { sp ->
        EssListRow(sp.name, stringResource(R.string.kind_smart), onClick = { actions.onOpenSmartPlaylist(sp.id) }) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(SonivellTheme.colors.surface), contentAlignment = Alignment.Center) {
                Icon(SonivellTheme.icons.smart, null, Modifier.size(22.dp), tint = SonivellTheme.colors.accent)
            }
        }
    }
    val autos = listOf(
        "mais-ouvidas" to R.string.auto_most,
        "nunca-tocadas" to R.string.auto_never,
        "ouvidas-recentemente" to R.string.auto_recent_played,
        "bem-avaliadas" to R.string.auto_top_rated,
        "menos-ouvidas" to R.string.auto_least,
    )
    items(autos, key = { "auto-${it.first}" }) { (key, label) ->
        EssListRow(stringResource(label), stringResource(R.string.kind_auto), onClick = { actions.onOpenAuto(key) }) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(SonivellTheme.colors.surface), contentAlignment = Alignment.Center) {
                Icon(SonivellTheme.icons.musicNote, null, Modifier.size(22.dp), tint = SonivellTheme.colors.textSecondary)
            }
        }
    }
}
