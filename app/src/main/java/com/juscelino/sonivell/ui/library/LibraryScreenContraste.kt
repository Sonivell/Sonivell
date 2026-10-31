package com.juscelino.sonivell.ui.library

import android.content.res.Resources
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.LibraryState
import com.juscelino.sonivell.LibraryTab
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Album
import com.juscelino.sonivell.data.ForYouState
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.Search
import com.juscelino.sonivell.data.SmartPlaylist
import com.juscelino.sonivell.data.SmartPlaylists
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.Chip
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.SectionTitle
import com.juscelino.sonivell.ui.components.glassTone
import com.juscelino.sonivell.ui.components.TrackRow
import com.juscelino.sonivell.ui.sonivell.SvMiniWave
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.util.Calendar
import com.juscelino.sonivell.ui.theme.cardSurface
import com.juscelino.sonivell.ui.theme.glassSurface

/** Rótulos já traduzidos, usados como "Tocando de …" ao iniciar a reprodução. */
private data class ContrasteLabels(val search: String, val library: String, val liked: String, val forYou: String)

private enum class ContrasteSymbol { Smart, Auto, Folder }

/** Capa de um item da biblioteca: a de uma faixa, o coração das Curtidas ou um ícone. */
private sealed interface ContrasteArt {
    data class Of(val track: Track?) : ContrasteArt
    data object Liked : ContrasteArt
    data class Symbol(val kind: ContrasteSymbol) : ContrasteArt
}

/** Linha (ou bloco da grade) da Biblioteca e da Busca 2.0: capa, título e "Tipo · detalhe". */
private data class ContrasteItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val art: ContrasteArt,
    val round: Boolean = false,
    val pinned: Boolean = false,
    val onClick: () -> Unit,
)

/** Roxo das Curtidas, como no mockup 2.0. */
private val LikedTone = Color(0xFF3A2F7A)

/** Layout Contraste (2.0). */
@Composable
fun LibraryScreenContraste(
    mode: LibraryMode,
    state: LibraryState,
    favorites: Set<Long>,
    currentId: Long?,
    contentPadding: PaddingValues,
    actions: LibraryActions,
    smartProgress: SmartProgress = SmartProgress(),
    forYou: ForYouState = ForYouState(),
    playlists: List<Playlist> = emptyList(),
    smartPlaylists: List<SmartPlaylist> = emptyList(),
    semanticResults: List<Track> = emptyList(),
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val res = LocalContext.current.resources
    val decades = remember(state.allTracks) { SmartPlaylists.decades(state.allTracks) }
    val focus = remember { FocusRequester() }
    var grid by rememberSaveable { mutableStateOf(false) }
    var showAllSongs by rememberSaveable(state.query) { mutableStateOf(false) }
    val labels = ContrasteLabels(
        search = stringResource(R.string.source_search),
        library = stringResource(R.string.nav_library),
        liked = stringResource(R.string.source_liked),
        forYou = stringResource(R.string.nav_home),
    )
    LaunchedEffect(mode) { if (mode == LibraryMode.Buscar) runCatching { focus.requestFocus() } }

    // Itens da Biblioteca já montados (com textos traduzidos) para a aba atual.
    val libItems = remember(mode, state, playlists, smartPlaylists, favorites, decades, res) {
        if (mode != LibraryMode.Biblioteca) emptyList()
        else buildLibraryItems(res, state, playlists, smartPlaylists, favorites, decades, actions)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Cabeçalho: cada aba tem o seu (a engrenagem fica só no Para você).
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Column(Modifier.weight(1f)) {
                    if (mode == LibraryMode.Inicio) {
                        Text(stringResource(greeting()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                    }
                    Text(
                        stringResource(
                            when (mode) {
                                LibraryMode.Inicio -> R.string.nav_home
                                LibraryMode.Buscar -> R.string.nav_search
                                LibraryMode.Biblioteca -> R.string.nav_library
                            }
                        ),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                        color = c.textPrimary,
                    )
                }
                when (mode) {
                    LibraryMode.Inicio -> {
                        if (state.allTracks.isNotEmpty()) {
                            IconBtn(icons.shuffle, stringResource(R.string.cd_shuffle_all), actions.onShuffleAll)
                        }
                        IconBtn(icons.settings, stringResource(R.string.cd_settings), actions.onOpenSettings)
                    }
                    LibraryMode.Biblioteca -> {
                        IconBtn(icons.search, stringResource(R.string.cd_search_library), actions.onOpenSearch)
                        IconBtn(icons.add, stringResource(R.string.cd_new_playlist), actions.onNewPlaylist, iconSize = 24.dp)
                    }
                    LibraryMode.Buscar -> Unit
                }
            }
        }

        if (mode == LibraryMode.Buscar) {
            item(key = "search") {
                SearchField(state.query, actions.onQuery, Modifier.focusRequester(focus).padding(bottom = 12.dp))
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
            LibraryMode.Inicio -> homeContent(state, favorites, labels, actions, smartProgress, forYou, decades)
            LibraryMode.Buscar -> searchContent(state, currentId, labels, actions, playlists, semanticResults, decades, showAllSongs) { showAllSongs = true }
            LibraryMode.Biblioteca -> {
                item(key = "chips") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            LibraryTab.Tudo, LibraryTab.Playlists, LibraryTab.Albuns, LibraryTab.Artistas,
                            LibraryTab.Musicas, LibraryTab.Generos, LibraryTab.Pastas,
                        ).forEach { t ->
                            Chip(stringResource(t.label), selected = state.tab == t, onClick = { actions.onTab(t) })
                        }
                    }
                }
                if (state.tab == LibraryTab.Musicas) {
                    item(key = "sort") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            SortButton(stringResource(state.sort.label), actions.onSort)
                            Spacer(Modifier.weight(1f))
                            Text(pluralStringResource(R.plurals.songs_count, state.tracks.size, state.tracks.size), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        }
                    }
                    itemsIndexed(state.tracks, key = { _, t -> t.id }) { i, t ->
                        TrackRow(t, onClick = { actions.onPlay(state.tracks, i, labels.library) }, highlighted = t.id == currentId, onMore = { actions.onTrackMenu(t) })
                    }
                } else {
                    item(key = "view") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.tab == LibraryTab.Playlists) {
                                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip(stringResource(R.string.new_smart_playlist), selected = false, onClick = actions.onNewSmartPlaylist)
                                    Chip(stringResource(R.string.import_playlist), selected = false, onClick = actions.onImportPlaylist)
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            IconBtn(
                                if (grid) icons.list else icons.grid,
                                stringResource(if (grid) R.string.cd_view_list else R.string.cd_view_grid),
                                { grid = !grid },
                                tint = c.textSecondary,
                                iconSize = 20.dp,
                            )
                        }
                    }
                    if (state.tab == LibraryTab.Generos && state.genres.isEmpty()) {
                        item(key = "g-empty") { EmptyText(stringResource(R.string.genres_empty)) }
                    }
                    if (state.tab == LibraryTab.Playlists && playlists.isEmpty()) {
                        item(key = "pl-empty") { EmptyText(stringResource(R.string.playlists_empty)) }
                    }
                    if (grid) {
                        libItems.chunked(3).forEachIndexed { i, row ->
                            item(key = "grid-$i-${row.first().key}") { ItemGrid(row, Modifier.padding(bottom = 14.dp)) }
                        }
                    } else {
                        items(libItems, key = { it.key }) { ItemRow(it) }
                    }
                }
            }
        }
    }
}

@StringRes
private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}

private fun decadeText(res: Resources, start: Int): String =
    res.getString(R.string.decade_label, if (start in 1900..1999) (start % 100).toString() else start.toString())

private fun songs(res: Resources, n: Int): String = res.getQuantityString(R.plurals.songs_count, n, n)

/** Monta a lista da aba atual da Biblioteca (fora da composição: textos via [Resources]). */
private fun buildLibraryItems(
    res: Resources,
    state: LibraryState,
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylist>,
    favorites: Set<Long>,
    decades: List<Int>,
    actions: LibraryActions,
): List<ContrasteItem> {
    val byId = state.allTracks.associateBy { it.id }
    val liked = state.allTracks.count { it.id in favorites }
    val likedItem = ContrasteItem(
        key = "liked",
        title = res.getString(R.string.source_liked),
        subtitle = res.getString(R.string.kind_playlist, songs(res, liked)),
        art = ContrasteArt.Liked,
        pinned = true,
        onClick = { actions.onOpenAuto("curtidas") },
    )
    val smart = smartPlaylists.map { sp ->
        ContrasteItem("sp-${sp.id}", sp.name, res.getString(R.string.kind_smart), ContrasteArt.Symbol(ContrasteSymbol.Smart), onClick = { actions.onOpenSmartPlaylist(sp.id) })
    }
    val own = playlists.map { p ->
        val first = p.trackIds.firstNotNullOfOrNull(byId::get)
        ContrasteItem("pl-${p.id}", p.name, res.getString(R.string.kind_playlist, songs(res, p.trackIds.count { it in byId })), ContrasteArt.Of(first), onClick = { actions.onOpenPlaylist(p.id) })
    }
    val albums = state.albums.map { a -> albumItem(res, a, actions) }
    val artists = state.artists.map { a ->
        ContrasteItem("ar-${a.name}", a.name, res.getString(R.string.kind_artist), ContrasteArt.Of(a.tracks.firstOrNull()), round = true, onClick = { actions.onOpenArtist(a.name) })
    }
    return when (state.tab) {
        LibraryTab.Tudo -> listOf(likedItem) + smart + own + albums + artists
        LibraryTab.Playlists -> {
            val autos = listOf(
                "mais-ouvidas" to R.string.auto_most,
                "menos-ouvidas" to R.string.auto_least,
                "nunca-tocadas" to R.string.auto_never,
                "recentes" to R.string.auto_recent_added,
                "ouvidas-recentemente" to R.string.auto_recent_played,
                "bem-avaliadas" to R.string.auto_top_rated,
            ).map { (key, label) ->
                ContrasteItem("auto-$key", res.getString(label), res.getString(R.string.kind_auto), ContrasteArt.Symbol(ContrasteSymbol.Auto), onClick = { actions.onOpenAuto(key) })
            } + decades.map { d ->
                ContrasteItem("auto-decada-$d", decadeText(res, d), res.getString(R.string.kind_auto), ContrasteArt.Symbol(ContrasteSymbol.Auto), onClick = { actions.onOpenAuto("decada-$d") })
            }
            listOf(likedItem) + own + smart + autos
        }
        LibraryTab.Albuns -> albums
        LibraryTab.Artistas -> artists
        LibraryTab.Generos -> state.genres.map { g ->
            ContrasteItem("g-${g.name.lowercase()}", g.name, res.getString(R.string.kind_genre, songs(res, g.tracks.size)), ContrasteArt.Of(g.tracks.firstOrNull()), onClick = { actions.onOpenGenre(g.name) })
        }
        LibraryTab.Pastas -> state.folders.map { f ->
            ContrasteItem("f-${f.path}", f.name, res.getString(R.string.kind_folder, songs(res, f.tracks.size)), ContrasteArt.Symbol(ContrasteSymbol.Folder), onClick = { actions.onOpenFolder(f.path) })
        }
        LibraryTab.Musicas -> emptyList()
    }
}

private fun albumItem(res: Resources, a: Album, actions: LibraryActions) =
    ContrasteItem("al-${a.id}", a.title, res.getString(R.string.kind_album, a.artist), ContrasteArt.Of(a.tracks.firstOrNull()), onClick = { actions.onOpenAlbum(a.id) })

/** O ícone vem do estilo escolhido (Fino ou Arredondado). */
@Composable
private fun symbolIcon(kind: ContrasteSymbol): ImageVector {
    val icons = SonivellTheme.icons
    return when (kind) {
        ContrasteSymbol.Smart -> icons.smart
        ContrasteSymbol.Auto -> icons.musicNote
        ContrasteSymbol.Folder -> icons.folder
    }
}

@Composable
private fun ContrasteArtBox(art: ContrasteArt, size: Dp?, round: Boolean, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
    val sized = (if (size != null) modifier.size(size) else modifier.fillMaxWidth().aspectRatio(1f)).clip(shape)
    when (art) {
        is ContrasteArt.Of -> AlbumArt(art.track, sized, corner = if (round) 999.dp else 8.dp)
        ContrasteArt.Liked -> Box(sized.background(LikedTone), contentAlignment = Alignment.Center) {
            Icon(SonivellTheme.icons.liked, null, Modifier.size(if (size != null) size * 0.4f else 36.dp), tint = Color.White)
        }
        is ContrasteArt.Symbol -> Box(sized.background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(symbolIcon(art.kind), null, Modifier.size(if (size != null) size * 0.38f else 32.dp), tint = c.textSecondary)
        }
    }
}

@Composable
private fun ItemRow(item: ContrasteItem) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = item.onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ContrasteArtBox(item.art, 64.dp, item.round)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = if (item.pinned) c.accent else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ItemGrid(row: List<ContrasteItem>, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.forEach { item ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = item.onClick),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = if (item.round) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                ContrasteArtBox(item.art, null, item.round)
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = if (item.pinned) c.accent else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun SortButton(label: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = stringResource(R.string.cd_sort_by, label), onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SonivellTheme.icons.sort, null, Modifier.size(16.dp), tint = c.textPrimary)
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = SonivellTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
}

// ---------------------------------------------------------------- Para você

private fun LazyListScope.homeContent(
    state: LibraryState,
    favorites: Set<Long>,
    labels: ContrasteLabels,
    actions: LibraryActions,
    smartProgress: SmartProgress,
    forYou: ForYouState,
    decades: List<Int>,
) {
    if (smartProgress.running && smartProgress.total > 0) {
        item(key = "smart-progress") { SmartAnalysisCard(smartProgress) }
    }
    item(key = "quick") { QuickGrid(state, favorites, forYou, decades, actions) }

    forYou.unfinished.firstOrNull()?.let { t ->
        item(key = "fy-continue") {
            Column(Modifier.padding(top = 18.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.fy_continue))
                ContinueCard(t, forYou.resumeAtMs[t.id] ?: 0L) { pos -> actions.onResume(t, pos) }
            }
        }
    }
    if (forYou.mixSeeds.isNotEmpty()) {
        item(key = "fy-mixes") { MixRow(forYou.mixSeeds, actions.onPlayMix) }
    }
    forYou.pick?.let { pick ->
        item(key = "fy-pick") {
            PickCard(pick, forYou.pickNeverPlayed) { actions.onPlay(listOf(pick), 0, labels.forYou) }
        }
    }
    trackRow("fy-hour", R.string.fy_this_hour, forYou.thisHour, labels.forYou, actions)
    trackRow("fy-unfinished", R.string.fy_unfinished, forYou.unfinished.drop(1), labels.forYou, actions)
    trackRow("fy-forgotten", R.string.fy_forgotten, forYou.forgottenFavorites, labels.forYou, actions)
    if (state.recentAlbums.isNotEmpty()) {
        item(key = "home-recent") {
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.recently_added))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recentAlbums, key = { "ra-" + it.id }) { a ->
                        Card(a.tracks.firstOrNull(), a.title, a.artist) { actions.onOpenAlbum(a.id) }
                    }
                }
            }
        }
    }
    if (forYou.isEmpty && forYou.mixSeeds.isEmpty()) {
        item(key = "fy-hint") {
            Text(
                stringResource(R.string.fy_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = SonivellTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

private data class ContrasteQuick(val key: String, val label: String, val art: ContrasteArt, val onClick: () -> Unit)

/** Atalhos 2×3: Curtidas, um mix, a década que mais aparece, nunca tocadas, recentes e um álbum. */
@Composable
private fun QuickGrid(state: LibraryState, favorites: Set<Long>, forYou: ForYouState, decades: List<Int>, actions: LibraryActions) {
    val c = SonivellTheme.colors
    val res = LocalContext.current.resources
    val tiles = remember(state.allTracks, state.recentAlbums, favorites, forYou.mixSeeds, decades, res) {
        val byDecade = state.allTracks.groupBy { t -> t.year?.takeIf { it in 1900..2100 }?.let { it / 10 * 10 } }
        val topDecade = decades.maxByOrNull { byDecade[it]?.size ?: 0 }
        buildList {
            add(ContrasteQuick("liked", res.getString(R.string.source_liked), ContrasteArt.Liked) { actions.onOpenAuto("curtidas") })
            forYou.mixSeeds.firstOrNull()?.let { seed ->
                add(ContrasteQuick("mix", res.getString(R.string.fy_mix_of, seed.artist), ContrasteArt.Of(seed)) { actions.onPlayMix(seed) })
            } ?: add(ContrasteQuick("most", res.getString(R.string.auto_most), ContrasteArt.Symbol(ContrasteSymbol.Auto)) { actions.onOpenAuto("mais-ouvidas") })
            topDecade?.let { d ->
                add(ContrasteQuick("dec", decadeText(res, d), ContrasteArt.Of(byDecade[d]?.firstOrNull())) { actions.onOpenAuto("decada-$d") })
            }
            add(ContrasteQuick("never", res.getString(R.string.auto_never), ContrasteArt.Symbol(ContrasteSymbol.Auto)) { actions.onOpenAuto("nunca-tocadas") })
            add(ContrasteQuick("played", res.getString(R.string.auto_recent_played), ContrasteArt.Symbol(ContrasteSymbol.Auto)) { actions.onOpenAuto("ouvidas-recentemente") })
            state.recentAlbums.firstOrNull()?.let { a ->
                add(ContrasteQuick("album", a.title, ContrasteArt.Of(a.tracks.firstOrNull())) { actions.onOpenAlbum(a.id) })
            } ?: add(ContrasteQuick("added", res.getString(R.string.auto_recent_added), ContrasteArt.Symbol(ContrasteSymbol.Auto)) { actions.onOpenAuto("recentes") })
            if (size < 6) add(ContrasteQuick("least", res.getString(R.string.auto_least), ContrasteArt.Symbol(ContrasteSymbol.Auto)) { actions.onOpenAuto("menos-ouvidas") })
        }.take(6)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { col, q ->
                    val tone = glassTone(row * 2 + col, c)
                    Row(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .glassSurface(RoundedCornerShape(10.dp), c.surface, c.outline, tone = tone, elevation = 6.dp)
                            .clickable(onClick = q.onClick),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickArt(q.art, tone)
                        Text(
                            q.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickArt(art: ContrasteArt, tone: Color) {
    val m = Modifier.size(56.dp)
    when (art) {
        is ContrasteArt.Of -> AlbumArt(art.track, m, corner = 0.dp)
        // Ícones sobre um véu translúcido na cor do bloco, no estilo de vidro do tema Sonivell.
        ContrasteArt.Liked -> Box(m.background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(SonivellTheme.icons.liked, null, Modifier.size(22.dp), tint = tone)
        }
        is ContrasteArt.Symbol -> Box(m.background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(symbolIcon(art.kind), null, Modifier.size(22.dp), tint = tone)
        }
    }
}

@Composable
private fun ContinueCard(track: Track, resumeAtMs: Long, onPlay: (Long) -> Unit) {
    val c = SonivellTheme.colors
    val duration = track.durationMs.coerceAtLeast(1L)
    val fraction = (resumeAtMs.toFloat() / duration).coerceIn(0f, 1f)
    Row(
        Modifier
            .fillMaxWidth()
            .cardSurface(RoundedCornerShape(16.dp))
            .clickable { onPlay(resumeAtMs) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AlbumArt(track, Modifier.size(64.dp), corner = 10.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.fy_time_left, track.artist, formatDuration((duration - resumeAtMs).coerceAtLeast(0L))),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.track)) {
                Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(c.accent))
            }
        }
        IconBtn(SonivellTheme.icons.play, stringResource(R.string.cd_continue, track.title), { onPlay(resumeAtMs) }, background = c.accent, tint = c.onAccent, iconSize = 20.dp)
    }
}

/** Uma linha horizontal de faixas, com título; some quando está vazia. */
private fun LazyListScope.trackRow(
    key: String,
    @StringRes title: Int,
    tracks: List<Track>,
    source: String,
    actions: LibraryActions,
) {
    if (tracks.isEmpty()) return
    item(key = key) {
        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(title))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                    Card(t, t.title, t.artist) { actions.onPlay(tracks, i, source) }
                }
            }
        }
    }
}

@Composable
private fun Card(track: Track?, title: String, subtitle: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Column(
        Modifier.width(140.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AlbumArt(track, Modifier.size(140.dp), corner = 12.dp, detailed = true)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Mixes pelo som (edição SMART): cada cartão começa uma fila SMART a partir de uma faixa. */
@Composable
private fun MixRow(seeds: List<Track>, onPlayMix: (Track) -> Unit) {
    val c = SonivellTheme.colors
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.fy_mixes)) {
            Text(stringResource(R.string.source_smart), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textSecondary)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(seeds, key = { "mix-" + it.id }) { seed ->
                Card(seed, stringResource(R.string.fy_mix_of, seed.artist), stringResource(R.string.fy_mix_from, seed.title)) { onPlayMix(seed) }
            }
        }
    }
}

/** Destaque do dia: uma faixa para redescobrir. */
@Composable
private fun PickCard(track: Track, neverPlayed: Boolean, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.fy_pick))
        Row(
            Modifier
                .fillMaxWidth()
                .cardSurface(RoundedCornerShape(16.dp))
                .clickable(onClick = onPlay)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AlbumArt(track, Modifier.size(64.dp), corner = 10.dp, detailed = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (neverPlayed) R.string.fy_pick_never else R.string.fy_pick_long),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                )
            }
            IconBtn(SonivellTheme.icons.play, stringResource(R.string.cd_play), onPlay, background = c.accent, tint = c.onAccent, iconSize = 20.dp)
        }
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
            .cardSurface(RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(SonivellTheme.icons.smart, null, Modifier.size(18.dp), tint = c.accent)
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

// ---------------------------------------------------------------- Busca

private fun LazyListScope.searchContent(
    state: LibraryState,
    currentId: Long?,
    labels: ContrasteLabels,
    actions: LibraryActions,
    playlists: List<Playlist>,
    semanticResults: List<Track>,
    decades: List<Int>,
    showAllSongs: Boolean,
    onShowAll: () -> Unit,
) {
    val parsed = Search.parse(state.query)
    if (parsed.isEmpty) {
        browseContent(state, decades, actions)
        return
    }
    val hasWords = parsed.words.isNotEmpty()
    val artists = if (!hasWords) emptyList() else state.artists.filter { Search.matchesText(parsed, it.name) }.take(5)
    val albums = if (!hasWords) emptyList() else state.albums.filter { Search.matchesText(parsed, it.title) }.take(6)
    val foundPlaylists = if (!hasWords) emptyList() else playlists.filter { Search.matchesText(parsed, it.name) }
    val genre = state.genres.firstOrNull { g -> parsed.words.any { w -> Search.matchesText(Search.Query(listOf(w), null), g.name) } }

    // Como a busca foi entendida: período e gênero reconhecidos.
    if (parsed.years != null || genre != null) {
        item(key = "s-interp") {
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                parsed.years?.let { InterpChip(stringResource(R.string.search_period, it.first, it.last)) }
                genre?.let { InterpChip(stringResource(R.string.search_genre, it.name)) }
            }
        }
    }

    val tracks = state.tracks
    val bestArtist = artists.firstOrNull()
    val bestAlbum = albums.firstOrNull()
    val bestTrack = tracks.firstOrNull()
    if (bestArtist != null || bestAlbum != null || bestTrack != null) {
        item(key = "s-best") {
            val res = LocalContext.current.resources
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(stringResource(R.string.search_best))
                when {
                    bestArtist != null -> BestCard(
                        title = bestArtist.name,
                        subtitle = res.getString(R.string.kind_artist) + " · " + songs(res, bestArtist.tracks.size),
                        track = bestArtist.tracks.firstOrNull(),
                        round = true,
                        onOpen = { actions.onOpenArtist(bestArtist.name) },
                        onPlay = { actions.onPlay(bestArtist.tracks, 0, bestArtist.name) },
                    )
                    bestAlbum != null -> BestCard(
                        title = bestAlbum.title,
                        subtitle = res.getString(R.string.kind_album, bestAlbum.artist),
                        track = bestAlbum.tracks.firstOrNull(),
                        round = false,
                        onOpen = { actions.onOpenAlbum(bestAlbum.id) },
                        onPlay = { actions.onPlay(bestAlbum.tracks, 0, bestAlbum.title) },
                    )
                    bestTrack != null -> BestCard(
                        title = bestTrack.title,
                        subtitle = bestTrack.artist,
                        track = bestTrack,
                        round = false,
                        onOpen = { actions.onPlay(tracks, 0, labels.search) },
                        onPlay = { actions.onPlay(tracks, 0, labels.search) },
                    )
                }
            }
        }
    }

    if (tracks.isNotEmpty()) {
        item(key = "s-songs-title") { SectionTitle(stringResource(R.string.tab_songs), Modifier.padding(bottom = 4.dp)) }
        val shown = if (showAllSongs) tracks else tracks.take(SONGS_PREVIEW)
        itemsIndexed(shown, key = { _, t -> "s-t-${t.id}" }) { i, t ->
            TrackRow(t, onClick = { actions.onPlay(tracks, i, labels.search) }, highlighted = t.id == currentId, onMore = { actions.onTrackMenu(t) })
        }
        if (!showAllSongs && tracks.size > SONGS_PREVIEW) {
            item(key = "s-songs-all") {
                Text(
                    stringResource(R.string.search_show_all, tracks.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SonivellTheme.colors.textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onShowAll)
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
    if (artists.isNotEmpty()) {
        item(key = "s-artists") {
            SearchGroup(stringResource(R.string.tab_artists), artists.map { a ->
                ContrasteItem("s-ar-${a.name}", a.name, stringResource(R.string.kind_artist), ContrasteArt.Of(a.tracks.firstOrNull()), round = true, onClick = { actions.onOpenArtist(a.name) })
            })
        }
    }
    if (albums.isNotEmpty()) {
        item(key = "s-albums") {
            val r = LocalContext.current.resources
            SearchGroup(stringResource(R.string.tab_albums), albums.map { albumItem(r, it, actions) })
        }
    }
    if (foundPlaylists.isNotEmpty()) {
        item(key = "s-playlists") {
            val byId = remember(state.allTracks) { state.allTracks.associateBy { it.id } }
            val r = LocalContext.current.resources
            SearchGroup(stringResource(R.string.tab_playlists), foundPlaylists.map { p ->
                ContrasteItem("s-pl-${p.id}", p.name, r.getString(R.string.kind_playlist, songs(r, p.trackIds.count { it in byId })), ContrasteArt.Of(p.trackIds.firstNotNullOfOrNull(byId::get)), onClick = { actions.onOpenPlaylist(p.id) })
            })
        }
    }
    if (tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && semanticResults.isEmpty()) {
        item(key = "s-none") { EmptyText(stringResource(R.string.search_none)) }
    }
    // Busca pelo sentido (edição SMART): o que combina com a descrição, além das palavras.
    val extra = semanticResults.filter { r -> tracks.none { it.id == r.id } }.take(15)
    if (extra.isNotEmpty()) {
        item(key = "s-sem-label") {
            Row(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(SonivellTheme.icons.smart, null, Modifier.size(18.dp), tint = SonivellTheme.colors.accent)
                SectionTitle(stringResource(R.string.search_match_style))
            }
        }
        itemsIndexed(extra, key = { _, t -> "sem-${t.id}" }) { i, t ->
            TrackRow(t, onClick = { actions.onPlay(extra, i, labels.search) }, highlighted = t.id == currentId, onMore = { actions.onTrackMenu(t) })
        }
    }
}

private const val SONGS_PREVIEW = 4

/** Busca vazia: blocos coloridos de gênero (e de década) para explorar a biblioteca. */
private fun LazyListScope.browseContent(state: LibraryState, decades: List<Int>, actions: LibraryActions) {
    item(key = "browse") {
        val c = SonivellTheme.colors
        val res = LocalContext.current.resources
        val tiles = remember(state.genres, decades, res) {
            state.genres.sortedByDescending { it.tracks.size }.take(12).map { g -> g.name to { actions.onOpenGenre(g.name) } } +
                decades.map { d -> decadeText(res, d) to { actions.onOpenAuto("decada-$d") } }
        }
        if (tiles.isEmpty()) return@item
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.search_browse), Modifier.padding(bottom = 2.dp))
            tiles.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { col, (label, onClick) ->
                        val tone = glassTone(row * 2 + col, c)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(92.dp)
                                .glassSurface(RoundedCornerShape(16.dp), c.surface, c.outline, tone = tone, elevation = 6.dp)
                                .clickable(onClick = onClick)
                                .padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = c.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Box(Modifier.size(8.dp).clip(CircleShape).background(tone))
                            }
                            SvMiniWave(label, tone, Modifier.align(Alignment.BottomStart).size(width = 44.dp, height = 14.dp))
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InterpChip(text: String) {
    val c = SonivellTheme.colors
    Box(
        Modifier.height(30.dp).cardSurface(RoundedCornerShape(15.dp)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
    }
}

@Composable
private fun SearchGroup(title: String, items: List<ContrasteItem>) {
    Column(Modifier.padding(top = 16.dp)) {
        SectionTitle(title, Modifier.padding(bottom = 4.dp))
        items.forEach { ItemRow(it) }
    }
}

@Composable
private fun BestCard(title: String, subtitle: String, track: Track?, round: Boolean, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .cardSurface(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ContrasteArtBox(ContrasteArt.Of(track), 72.dp, round)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconBtn(SonivellTheme.icons.play, stringResource(R.string.cd_play_item, title), onPlay, size = 48.dp, background = c.accent, tint = c.onAccent)
    }
}

/** Campo de busca 2.0: cores invertidas (claro no tema escuro), como no mockup. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val bg = c.textPrimary
    val fg = c.background
    val searchLabel = stringResource(R.string.cd_search)
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SonivellTheme.icons.search, null, Modifier.size(20.dp), tint = fg)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = fg.copy(alpha = 0.6f), maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel },
            )
        }
        if (value.isNotEmpty()) {
            IconBtn(SonivellTheme.icons.close, stringResource(R.string.cd_clear_search), { onChange("") }, tint = fg, iconSize = 18.dp)
        }
    }
}
