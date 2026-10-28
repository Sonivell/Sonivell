package com.juscelino.sonivell.ui.library

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
import com.juscelino.sonivell.data.Ranked
import com.juscelino.sonivell.data.Search
import com.juscelino.sonivell.data.SmartPlaylist
import com.juscelino.sonivell.data.SmartPlaylists
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.PlaySquircle
import com.juscelino.sonivell.ui.components.TrackRow
import com.juscelino.sonivell.ui.components.Waveform
import com.juscelino.sonivell.ui.theme.ArtTones
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/** Rótulos já traduzidos, usados como "Tocando de …" ao iniciar a reprodução. */
private data class OndasLabels(val search: String, val library: String, val liked: String, val forYou: String)

private enum class OndasSymbol { Smart, Auto, Folder, Liked }

/** Capa de um item: a de uma faixa ou um símbolo. */
private sealed interface OndasArt {
    data class Of(val track: Track?) : OndasArt
    data class Symbol(val kind: OndasSymbol) : OndasArt
}

/** Linha (ou bloco da grade) da Biblioteca e da Busca: capa, título e "Tipo · detalhe". */
private data class OndasItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val art: OndasArt,
    val round: Boolean = false,
    val onClick: () -> Unit,
)

private data class OndasQuick(val key: String, val label: String, val meta: String, val art: OndasArt, val liked: Boolean = false, val onClick: () -> Unit)

/** Layout Ondas (3.0, padrão) das telas Para você, Busca e Biblioteca. */
@Composable
fun LibraryScreenOndas(
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
    genreOfMonth: Ranked<String>? = null,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val res = LocalContext.current.resources
    val decades = remember(state.allTracks) { SmartPlaylists.decades(state.allTracks) }
    val focus = remember { FocusRequester() }
    var grid by rememberSaveable { mutableStateOf(false) }
    var showAllSongs by rememberSaveable(state.query) { mutableStateOf(false) }
    val labels = OndasLabels(
        search = stringResource(R.string.source_search),
        library = stringResource(R.string.nav_library),
        liked = stringResource(R.string.source_liked),
        forYou = stringResource(R.string.nav_home),
    )
    LaunchedEffect(mode) { if (mode == LibraryMode.Buscar) runCatching { focus.requestFocus() } }

    val libItems = remember(mode, state, playlists, smartPlaylists, decades, res) {
        if (mode != LibraryMode.Biblioteca) emptyList()
        else buildItems(res, state, playlists, smartPlaylists, decades, actions)
    }
    val favs = remember(state.allTracks, favorites) { state.allTracks.filter { it.id in favorites } }

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
        item(key = "header") {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    if (mode == LibraryMode.Inicio) {
                        Text(stringResource(greetingRes()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
                    }
                    Text(
                        stringResource(
                            when (mode) {
                                LibraryMode.Inicio -> R.string.nav_home
                                LibraryMode.Buscar -> R.string.nav_search
                                LibraryMode.Biblioteca -> R.string.nav_library
                            }
                        ),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, letterSpacing = (-0.9).sp),
                        color = c.textPrimary,
                    )
                }
                when (mode) {
                    LibraryMode.Inicio -> {
                        if (state.allTracks.isNotEmpty()) OutlinedSquare(icons.shuffle, stringResource(R.string.cd_shuffle_all), actions.onShuffleAll)
                        OutlinedSquare(icons.settings, stringResource(R.string.cd_settings), actions.onOpenSettings)
                    }
                    LibraryMode.Biblioteca -> {
                        OutlinedSquare(icons.search, stringResource(R.string.cd_search_library), actions.onOpenSearch)
                        FilledSquare(icons.add, stringResource(R.string.cd_new_playlist), actions.onNewPlaylist)
                    }
                    LibraryMode.Buscar -> Unit
                }
            }
        }

        if (mode == LibraryMode.Buscar) {
            item(key = "search") {
                OndasSearchField(state.query, actions.onQuery, Modifier.focusRequester(focus).padding(bottom = 12.dp))
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
            item(key = "empty") { Hint(stringResource(R.string.library_empty)) }
            return@LazyColumn
        }

        when (mode) {
            LibraryMode.Inicio -> home(state, favorites, labels, actions, smartProgress, forYou, decades)
            LibraryMode.Buscar -> search(state, currentId, labels, actions, playlists, semanticResults, decades, genreOfMonth, showAllSongs) { showAllSongs = true }
            LibraryMode.Biblioteca -> {
                item(key = "segments") {
                    Segmented(
                        listOf(LibraryTab.Tudo, LibraryTab.Playlists, LibraryTab.Albuns, LibraryTab.Artistas, LibraryTab.Musicas, LibraryTab.Generos, LibraryTab.Pastas),
                        state.tab,
                        actions.onTab,
                        Modifier.padding(bottom = 12.dp),
                    )
                }
                if (state.tab == LibraryTab.Tudo || state.tab == LibraryTab.Playlists) {
                    item(key = "liked") {
                        LikedHero(favs, Modifier.padding(bottom = 8.dp), onOpen = { actions.onOpenAuto("curtidas") }) {
                            if (favs.isNotEmpty()) actions.onPlay(favs, 0, labels.liked)
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
                                    SmallOutlined(stringResource(R.string.new_smart_playlist), actions.onNewSmartPlaylist)
                                    SmallOutlined(stringResource(R.string.import_playlist), actions.onImportPlaylist)
                                }
                            } else {
                                Text(
                                    pluralStringResource(R.plurals.results_count, libItems.size, libItems.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button, onClickLabel = stringResource(if (grid) R.string.cd_view_list else R.string.cd_view_grid)) { grid = !grid },
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(if (grid) icons.list else icons.grid, stringResource(if (grid) R.string.cd_view_list else R.string.cd_view_grid), Modifier.size(20.dp), tint = c.textSecondary)
                            }
                        }
                    }
                    if (state.tab == LibraryTab.Generos && state.genres.isEmpty()) {
                        item(key = "g-empty") { Hint(stringResource(R.string.genres_empty)) }
                    }
                    if (state.tab == LibraryTab.Playlists && playlists.isEmpty()) {
                        item(key = "pl-empty") { Hint(stringResource(R.string.playlists_empty)) }
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
private fun greetingRes(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}

private fun decadeName(res: Resources, start: Int): String =
    res.getString(R.string.decade_label, if (start in 1900..1999) (start % 100).toString() else start.toString())

private fun songsText(res: Resources, n: Int): String = res.getQuantityString(R.plurals.songs_count, n, n)

private fun buildItems(
    res: Resources,
    state: LibraryState,
    playlists: List<Playlist>,
    smartPlaylists: List<SmartPlaylist>,
    decades: List<Int>,
    actions: LibraryActions,
): List<OndasItem> {
    val byId = state.allTracks.associateBy { it.id }
    val smart = smartPlaylists.map { sp ->
        OndasItem("sp-${sp.id}", sp.name, res.getString(R.string.kind_smart), OndasArt.Symbol(OndasSymbol.Smart), onClick = { actions.onOpenSmartPlaylist(sp.id) })
    }
    val own = playlists.map { p ->
        OndasItem(
            "pl-${p.id}", p.name,
            res.getString(R.string.kind_playlist, songsText(res, p.trackIds.count { it in byId })),
            OndasArt.Of(p.trackIds.firstNotNullOfOrNull(byId::get)),
            onClick = { actions.onOpenPlaylist(p.id) },
        )
    }
    val albums = state.albums.map { albumItem(res, it, actions) }
    val artists = state.artists.map { a ->
        OndasItem("ar-${a.name}", a.name, res.getString(R.string.kind_artist_albums, res.getQuantityString(R.plurals.albums_count, a.albumCount, a.albumCount)), OndasArt.Of(a.tracks.firstOrNull()), round = true, onClick = { actions.onOpenArtist(a.name) })
    }
    return when (state.tab) {
        LibraryTab.Tudo -> smart + own + albums + artists
        LibraryTab.Playlists -> {
            val autos = listOf(
                "mais-ouvidas" to R.string.auto_most,
                "menos-ouvidas" to R.string.auto_least,
                "nunca-tocadas" to R.string.auto_never,
                "recentes" to R.string.auto_recent_added,
                "ouvidas-recentemente" to R.string.auto_recent_played,
            ).map { (key, label) ->
                OndasItem("auto-$key", res.getString(label), res.getString(R.string.kind_auto), OndasArt.Symbol(OndasSymbol.Auto), onClick = { actions.onOpenAuto(key) })
            } + decades.map { d ->
                OndasItem("auto-decada-$d", decadeName(res, d), res.getString(R.string.kind_auto), OndasArt.Symbol(OndasSymbol.Auto), onClick = { actions.onOpenAuto("decada-$d") })
            }
            own + smart + autos
        }
        LibraryTab.Albuns -> albums
        LibraryTab.Artistas -> artists
        LibraryTab.Generos -> state.genres.map { g ->
            OndasItem("g-${g.name.lowercase()}", g.name, res.getString(R.string.kind_genre, songsText(res, g.tracks.size)), OndasArt.Of(g.tracks.firstOrNull()), onClick = { actions.onOpenGenre(g.name) })
        }
        LibraryTab.Pastas -> state.folders.map { f ->
            OndasItem("f-${f.path}", f.name, res.getString(R.string.kind_folder, songsText(res, f.tracks.size)), OndasArt.Symbol(OndasSymbol.Folder), onClick = { actions.onOpenFolder(f.path) })
        }
        LibraryTab.Musicas -> emptyList()
    }
}

private fun albumItem(res: Resources, a: Album, actions: LibraryActions) =
    OndasItem("al-${a.id}", a.title, res.getString(R.string.kind_album, a.artist), OndasArt.Of(a.tracks.firstOrNull()), onClick = { actions.onOpenAlbum(a.id) })

// ---------------------------------------------------------------- peças

@Composable
private fun OutlinedSquare(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(44.dp).clip(shape).border(1.dp, c.outline, shape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = c.textPrimary) }
}

@Composable
private fun FilledSquare(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.accent).clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = c.onAccent) }
}

@Composable
private fun SmallOutlined(label: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.height(36.dp).clip(shape).border(1.dp, c.outline, shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textTertiary) }
}

/** Seletor segmentado (uma faixa só, a opção ativa destacada); rola de lado quando não cabe. */
@Composable
private fun Segmented(tabs: List<LibraryTab>, selected: LibraryTab, onSelect: (LibraryTab) -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { t ->
            val on = t == selected
            Box(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.surfaceHigh else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(t) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(t.label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (on) c.textPrimary else c.textSecondary,
                )
            }
        }
    }
}

/** Curtidas em destaque, na cor de destaque, com o próprio botão de tocar. */
@Composable
private fun LikedHero(favs: List<Track>, modifier: Modifier = Modifier, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.accent)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(SonivellTheme.icons.liked, null, Modifier.size(28.dp), tint = c.onAccent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(R.string.source_liked), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.ExtraBold, color = c.onAccent)
            Text(
                pluralStringResource(R.plurals.songs_count, favs.size, favs.size) + " · " + formatDuration(favs.sumOf { it.durationMs }),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = c.onAccent.copy(alpha = 0.72f),
            )
        }
        if (favs.isNotEmpty()) {
            PlaySquircle(
                isPlaying = false,
                onClick = onPlay,
                size = 48.dp,
                corner = 16.dp,
                iconSize = 20.dp,
                contentDescription = stringResource(R.string.cd_play_item, stringResource(R.string.source_liked)),
                background = c.onAccent,
                tint = c.accent,
            )
        }
    }
}

@Composable
private fun ArtBox(art: OndasArt, size: Dp?, round: Boolean, corner: Dp = 16.dp) {
    val c = SonivellTheme.colors
    val shape = if (round) CircleShape else RoundedCornerShape(corner)
    val m = (if (size != null) Modifier.size(size) else Modifier.fillMaxWidth().aspectRatio(1f)).clip(shape)
    when (art) {
        is OndasArt.Of -> AlbumArt(art.track, m, corner = if (round) 999.dp else corner)
        is OndasArt.Symbol -> Box(m.background(c.surface), contentAlignment = Alignment.Center) {
            val icons = SonivellTheme.icons
            Icon(
                when (art.kind) {
                    OndasSymbol.Smart -> icons.smart
                    OndasSymbol.Auto -> icons.musicNote
                    OndasSymbol.Folder -> icons.folder
                    OndasSymbol.Liked -> icons.liked
                },
                null,
                Modifier.size(if (size != null) size * 0.42f else 32.dp),
                tint = c.accent,
            )
        }
    }
}

@Composable
private fun ItemRow(item: OndasItem) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = item.onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtBox(item.art, 56.dp, item.round)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ItemGrid(row: List<OndasItem>, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        row.forEach { item ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = item.onClick),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = if (item.round) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                ArtBox(item.art, null, item.round, corner = 20.dp)
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = stringResource(R.string.cd_sort_by, label), onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SonivellTheme.icons.sort, null, Modifier.size(16.dp), tint = c.textTertiary)
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = c.textTertiary)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = SonivellTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun Title(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, letterSpacing = (-0.2).sp),
            fontWeight = FontWeight.ExtraBold,
            color = SonivellTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Selo "SMART" com contorno na cor de destaque. */
@Composable
private fun SmartTag() {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Box(Modifier.height(22.dp).clip(shape).border(1.dp, c.accent, shape).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.source_smart), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.8.sp), fontWeight = FontWeight.ExtraBold, color = c.accent)
    }
}

/** Duas ondas sobre o tom do mix: a capa dos mixes e dos blocos de gênero. */
@Composable
private fun WaveTile(index: Int, modifier: Modifier = Modifier) {
    val bg = ArtTones.backgrounds[index % ArtTones.backgrounds.size]
    val wave = ArtTones.cores[index % ArtTones.cores.size]
    Canvas(modifier.background(bg)) {
        val stroke = 2.5.dp.toPx()
        fun line(yFrac: Float, amp: Float, alpha: Float, width: Float) {
            val path = Path()
            val y0 = size.height * yFrac
            val left = size.width * 0.1f
            val right = size.width * 0.9f
            var x = left
            path.moveTo(x, y0)
            while (x <= right) {
                val t = (x - left) / (right - left)
                path.lineTo(x, y0 + sin(t * 4 * PI).toFloat() * amp)
                x += 2f
            }
            drawPath(path, wave.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round))
        }
        line(0.74f, size.height * 0.08f, 1f, stroke)
        line(0.82f, size.height * 0.05f, 0.45f, stroke * 0.8f)
    }
}

// ---------------------------------------------------------------- Para você

private fun LazyListScope.home(
    state: LibraryState,
    favorites: Set<Long>,
    labels: OndasLabels,
    actions: LibraryActions,
    smartProgress: SmartProgress,
    forYou: ForYouState,
    decades: List<Int>,
) {
    if (smartProgress.running && smartProgress.total > 0) {
        item(key = "smart-progress") { AnalysisCard(smartProgress) }
    }
    item(key = "quick") { QuickGrid(state, favorites, forYou, decades, actions) }

    forYou.unfinished.firstOrNull()?.let { t ->
        item(key = "fy-continue") {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Title(stringResource(R.string.fy_continue))
                ContinueCard(t, forYou.resumeAtMs[t.id] ?: 0L) { pos -> actions.onResume(t, pos) }
            }
        }
    }
    if (forYou.mixSeeds.isNotEmpty()) {
        item(key = "fy-mixes") {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Title(stringResource(R.string.fy_mixes)) { SmartTag() }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(forYou.mixSeeds, key = { _, s -> "mix-" + s.id }) { i, seed ->
                        Column(
                            Modifier.width(136.dp).clip(RoundedCornerShape(20.dp)).clickable { actions.onPlayMix(seed) },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WaveTile(i, Modifier.size(width = 136.dp, height = 120.dp).clip(RoundedCornerShape(20.dp)))
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(stringResource(R.string.fy_mix_of, seed.artist), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = SonivellTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.fy_sounds_like, seed.title), style = MaterialTheme.typography.labelMedium, color = SonivellTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    forYou.pick?.let { pick ->
        item(key = "fy-pick") {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Title(stringResource(R.string.fy_pick))
                PickCard(pick, forYou.pickNeverPlayed) { actions.onPlay(listOf(pick), 0, labels.forYou) }
            }
        }
    }
    cardRow("fy-hour", R.string.fy_this_hour, forYou.thisHour, labels.forYou, actions)
    cardRow("fy-unfinished", R.string.fy_unfinished, forYou.unfinished.drop(1), labels.forYou, actions)
    cardRow("fy-forgotten", R.string.fy_forgotten, forYou.forgottenFavorites, labels.forYou, actions)
    if (state.recentAlbums.isNotEmpty()) {
        item(key = "home-recent") {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Title(stringResource(R.string.recently_added))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recentAlbums, key = { "ra-" + it.id }) { a ->
                        Card(a.tracks.firstOrNull(), a.title, a.artist) { actions.onOpenAlbum(a.id) }
                    }
                }
            }
        }
    }
    if (forYou.isEmpty && forYou.mixSeeds.isEmpty()) {
        item(key = "fy-hint") { Hint(stringResource(R.string.fy_empty_hint)) }
    }
}

/** Atalhos 2×3: Curtidas em destaque, um mix, a década que mais aparece, nunca tocadas, recentes e um álbum. */
@Composable
private fun QuickGrid(state: LibraryState, favorites: Set<Long>, forYou: ForYouState, decades: List<Int>, actions: LibraryActions) {
    val c = SonivellTheme.colors
    val res = LocalContext.current.resources
    val tiles = remember(state.allTracks, state.recentAlbums, favorites, forYou.mixSeeds, decades, res) {
        val byDecade = state.allTracks.groupBy { t -> t.year?.takeIf { it in 1900..2100 }?.let { it / 10 * 10 } }
        val topDecade = decades.maxByOrNull { byDecade[it]?.size ?: 0 }
        val liked = state.allTracks.count { it.id in favorites }
        val auto = res.getString(R.string.kind_auto)
        buildList {
            add(OndasQuick("liked", res.getString(R.string.source_liked), songsText(res, liked), OndasArt.Symbol(OndasSymbol.Liked), liked = true) { actions.onOpenAuto("curtidas") })
            val seed = forYou.mixSeeds.firstOrNull()
            if (seed != null) {
                add(OndasQuick("mix", res.getString(R.string.fy_mix_of, seed.artist), res.getString(R.string.source_smart), OndasArt.Of(seed)) { actions.onPlayMix(seed) })
            } else {
                add(OndasQuick("most", res.getString(R.string.auto_most), auto, OndasArt.Symbol(OndasSymbol.Auto)) { actions.onOpenAuto("mais-ouvidas") })
            }
            if (topDecade != null) {
                val n = byDecade[topDecade]?.size ?: 0
                add(OndasQuick("dec", decadeName(res, topDecade), songsText(res, n), OndasArt.Of(byDecade[topDecade]?.firstOrNull())) { actions.onOpenAuto("decada-$topDecade") })
            }
            add(OndasQuick("never", res.getString(R.string.auto_never), auto, OndasArt.Symbol(OndasSymbol.Auto)) { actions.onOpenAuto("nunca-tocadas") })
            add(OndasQuick("played", res.getString(R.string.auto_recent_played), auto, OndasArt.Symbol(OndasSymbol.Auto)) { actions.onOpenAuto("ouvidas-recentemente") })
            val album = state.recentAlbums.firstOrNull()
            if (album != null) {
                add(OndasQuick("album", album.title, res.getString(R.string.kind_album_short), OndasArt.Of(album.tracks.firstOrNull())) { actions.onOpenAlbum(album.id) })
            } else {
                add(OndasQuick("added", res.getString(R.string.auto_recent_added), auto, OndasArt.Symbol(OndasSymbol.Auto)) { actions.onOpenAuto("recentes") })
            }
            if (size < 6) add(OndasQuick("least", res.getString(R.string.auto_least), auto, OndasArt.Symbol(OndasSymbol.Auto)) { actions.onOpenAuto("menos-ouvidas") })
        }.take(6)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { q ->
                    val bg = if (q.liked) c.accent else c.surface
                    val fg = if (q.liked) c.onAccent else c.textPrimary
                    val meta = if (q.liked) c.onAccent.copy(alpha = 0.72f) else c.textSecondary
                    Row(
                        Modifier
                            .weight(1f)
                            .height(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .clickable(onClick = q.onClick)
                            .padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(q.label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp), fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(q.meta, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), fontWeight = FontWeight.SemiBold, color = meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (q.liked) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.onAccent), contentAlignment = Alignment.Center) {
                                Icon(SonivellTheme.icons.liked, null, Modifier.size(18.dp), tint = c.accent)
                            }
                        } else {
                            ArtBox(q.art, 40.dp, round = false, corner = 12.dp)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ContinueCard(track: Track, resumeAtMs: Long, onPlay: (Long) -> Unit) {
    val c = SonivellTheme.colors
    val duration = track.durationMs.coerceAtLeast(1L)
    val fraction = (resumeAtMs.toFloat() / duration).coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable { onPlay(resumeAtMs) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlbumArt(track, Modifier.size(52.dp), corner = 14.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.fy_time_left, track.artist, formatDuration((duration - resumeAtMs).coerceAtLeast(0L))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlaySquircle(false, { onPlay(resumeAtMs) }, size = 48.dp, corner = 16.dp, iconSize = 20.dp, contentDescription = stringResource(R.string.cd_continue, track.title))
        }
        Waveform(track.id, fraction, Modifier.fillMaxWidth().height(22.dp), bars = 44)
    }
}

@Composable
private fun PickCard(track: Track, neverPlayed: Boolean, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .clickable(onClick = onPlay)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(track, Modifier.size(52.dp), corner = 14.dp, detailed = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(if (neverPlayed) R.string.fy_pick_never else R.string.fy_pick_long), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.accent)
        }
        PlaySquircle(false, onPlay, size = 48.dp, corner = 16.dp, iconSize = 20.dp)
    }
}

private fun LazyListScope.cardRow(key: String, @StringRes title: Int, tracks: List<Track>, source: String, actions: LibraryActions) {
    if (tracks.isEmpty()) return
    item(key = key) {
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Title(stringResource(title))
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
        Modifier.width(136.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AlbumArt(track, Modifier.size(136.dp), corner = 20.dp, detailed = true)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AnalysisCard(progress: SmartProgress) {
    val c = SonivellTheme.colors
    val fraction = if (progress.total > 0) progress.analysed.toFloat() / progress.total else 0f
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(SonivellTheme.icons.smart, null, Modifier.size(18.dp), tint = c.accent)
            Text(stringResource(R.string.smart_analysing), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.smart_progress, progress.analysed, progress.total), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        }
        Waveform(progress.total.toLong(), fraction, Modifier.fillMaxWidth().height(18.dp), bars = 48)
    }
}

// ---------------------------------------------------------------- Busca

/** Palavras de período ("anos 90", "1990", "90s", "1990-1999") para tirar da busca. */
private val PERIOD_WORDS = Regex("""(?i)\b(?:anos|decada|década|years?|the)?\s*'?\d{2,4}s?(?:\s*[-–]\s*\d{2,4})?\b""")

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.search(
    state: LibraryState,
    currentId: Long?,
    labels: OndasLabels,
    actions: LibraryActions,
    playlists: List<Playlist>,
    semanticResults: List<Track>,
    decades: List<Int>,
    genreOfMonth: Ranked<String>?,
    showAllSongs: Boolean,
    onShowAll: () -> Unit,
) {
    val parsed = Search.parse(state.query)
    if (parsed.isEmpty) {
        genreBrowse(state, decades, genreOfMonth, actions)
        return
    }
    val hasWords = parsed.words.isNotEmpty()
    val artists = if (!hasWords) emptyList() else state.artists.filter { Search.matchesText(parsed, it.name) }.take(5)
    val albums = if (!hasWords) emptyList() else state.albums.filter { Search.matchesText(parsed, it.title) }.take(6)
    val foundPlaylists = if (!hasWords) emptyList() else playlists.filter { Search.matchesText(parsed, it.name) }
    val genreWord = parsed.words.firstOrNull { w -> state.genres.any { g -> Search.matchesText(Search.Query(listOf(w), null), g.name) } }
    val genre = genreWord?.let { w -> state.genres.first { g -> Search.matchesText(Search.Query(listOf(w), null), g.name) } }

    if (parsed.years != null || genre != null) {
        item(key = "s-interp") {
            FlowRow(
                Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.search_understood),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SonivellTheme.colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                genre?.let { g ->
                    Token(stringResource(R.string.search_genre_token, g.name)) {
                        actions.onQuery(state.query.replace(Regex("(?i)\\b" + Regex.escape(genreWord) + "\\w*"), "").trim().replace(Regex("\\s+"), " "))
                    }
                }
                parsed.years?.let { y ->
                    Token(if (y.first == y.last) y.first.toString() else "${y.first}–${y.last}") {
                        actions.onQuery(state.query.replace(PERIOD_WORDS, " ").trim().replace(Regex("\\s+"), " "))
                    }
                }
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
            Box(Modifier.padding(bottom = 16.dp)) {
                when {
                    bestArtist != null -> BestCard(
                        bestArtist.name,
                        res.getString(R.string.kind_artist_albums, res.getQuantityString(R.plurals.albums_count, bestArtist.albumCount, bestArtist.albumCount)),
                        bestArtist.tracks.firstOrNull(), true,
                        onOpen = { actions.onOpenArtist(bestArtist.name) },
                        onPlay = { actions.onPlay(bestArtist.tracks, 0, bestArtist.name) },
                    )
                    bestAlbum != null -> BestCard(
                        bestAlbum.title, res.getString(R.string.kind_album, bestAlbum.artist), bestAlbum.tracks.firstOrNull(), false,
                        onOpen = { actions.onOpenAlbum(bestAlbum.id) },
                        onPlay = { actions.onPlay(bestAlbum.tracks, 0, bestAlbum.title) },
                    )
                    bestTrack != null -> BestCard(
                        bestTrack.title, bestTrack.artist, bestTrack, false,
                        onOpen = { actions.onPlay(tracks, 0, labels.search) },
                        onPlay = { actions.onPlay(tracks, 0, labels.search) },
                    )
                }
            }
        }
    }

    if (tracks.isNotEmpty()) {
        item(key = "s-songs-title") { Title(stringResource(R.string.tab_songs), Modifier.padding(bottom = 4.dp)) }
        val shown = if (showAllSongs) tracks else tracks.take(4)
        itemsIndexed(shown, key = { _, t -> "s-t-${t.id}" }) { i, t ->
            TrackRow(t, onClick = { actions.onPlay(tracks, i, labels.search) }, highlighted = t.id == currentId, onMore = { actions.onTrackMenu(t) })
        }
        if (!showAllSongs && tracks.size > 4) {
            item(key = "s-songs-all") {
                Text(
                    stringResource(R.string.search_show_all, tracks.size),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = SonivellTheme.colors.accent,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onShowAll).padding(vertical = 10.dp),
                )
            }
        }
    }
    if (artists.isNotEmpty()) {
        item(key = "s-artists") {
            val r = LocalContext.current.resources
            Group(stringResource(R.string.tab_artists), artists.map { a ->
                OndasItem("s-ar-${a.name}", a.name, r.getString(R.string.kind_artist), OndasArt.Of(a.tracks.firstOrNull()), round = true, onClick = { actions.onOpenArtist(a.name) })
            })
        }
    }
    if (albums.isNotEmpty()) {
        item(key = "s-albums") {
            val r = LocalContext.current.resources
            Group(stringResource(R.string.tab_albums), albums.map { albumItem(r, it, actions) })
        }
    }
    if (foundPlaylists.isNotEmpty()) {
        item(key = "s-playlists") {
            val byId = remember(state.allTracks) { state.allTracks.associateBy { it.id } }
            val r = LocalContext.current.resources
            Group(stringResource(R.string.tab_playlists), foundPlaylists.map { p ->
                OndasItem("s-pl-${p.id}", p.name, r.getString(R.string.kind_playlist, songsText(r, p.trackIds.count { it in byId })), OndasArt.Of(p.trackIds.firstNotNullOfOrNull(byId::get)), onClick = { actions.onOpenPlaylist(p.id) })
            })
        }
    }
    if (tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && semanticResults.isEmpty()) {
        item(key = "s-none") { Hint(stringResource(R.string.search_none)) }
    }
    // Busca pelo sentido (edição SMART): o que soa parecido com a descrição.
    val extra = semanticResults.filter { r -> tracks.none { it.id == r.id } }.take(12)
    if (extra.isNotEmpty()) {
        item(key = "s-sem") {
            val c = SonivellTheme.colors
            val shape = RoundedCornerShape(22.dp)
            Column(
                Modifier.padding(top = 16.dp).fillMaxWidth().clip(shape).border(1.dp, c.outline, shape).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(SonivellTheme.icons.smart, null, Modifier.size(18.dp), tint = c.accent)
                    Text(stringResource(R.string.search_sounds_like), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.source_smart), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp), fontWeight = FontWeight.ExtraBold, color = c.textSecondary)
                }
                extra.forEachIndexed { i, t ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).clickable { actions.onPlay(extra, i, labels.search) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AlbumArt(t, Modifier.size(40.dp), corner = 12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = if (t.id == currentId) c.accent else c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(t.artist, t.year?.toString()).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Token(text: String, onRemove: () -> Unit) {
    val c = SonivellTheme.colors
    val dash = c.textSecondary
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_remove_filter, text), onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
        Icon(SonivellTheme.icons.close, null, Modifier.size(12.dp), tint = c.textPrimary)
    }
}

@Composable
private fun Group(title: String, items: List<OndasItem>) {
    Column(Modifier.padding(top = 16.dp)) {
        Title(title, Modifier.padding(bottom = 4.dp))
        items.forEach { ItemRow(it) }
    }
}

@Composable
private fun BestCard(title: String, subtitle: String, track: Track?, round: Boolean, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtBox(OndasArt.Of(track), 68.dp, round, corner = 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(R.string.search_best).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.7.sp),
                fontWeight = FontWeight.ExtraBold,
                color = c.accent,
            )
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PlaySquircle(false, onPlay, size = 48.dp, corner = 16.dp, iconSize = 20.dp, contentDescription = stringResource(R.string.cd_play_item, title))
    }
}

/** Campo de busca Ondas: escuro, com contorno na cor de destaque quando ativo. */
@Composable
private fun OndasSearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    var focused by remember { mutableStateOf(false) }
    val searchLabel = stringResource(R.string.cd_search)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(c.surface)
            .border(2.dp, if (focused) c.accent else c.outline, shape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SonivellTheme.icons.search, null, Modifier.size(20.dp), tint = if (focused) c.accent else c.textSecondary)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.semantics { contentDescription = searchLabel },
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button) { onChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SonivellTheme.icons.close, stringResource(R.string.cd_clear_search), Modifier.size(18.dp), tint = c.textSecondary)
            }
        }
    }
}
