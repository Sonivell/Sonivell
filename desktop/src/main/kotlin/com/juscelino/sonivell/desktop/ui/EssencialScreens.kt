package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.desktop.Album
import com.juscelino.sonivell.desktop.Controller
import com.juscelino.sonivell.desktop.Track
import com.juscelino.sonivell.desktop.formatTime
import com.juscelino.sonivell.desktop.str
import java.util.Calendar

/*
 * Layout Essencial no desktop: Para você, Buscar e Biblioteca com o mínimo de elementos.
 * A capa é o centro visual; o destaque fica só no que toca e no que está selecionado.
 */

private const val MAX_W = 1180
private val CELL = 176.dp
private val GAP = 16.dp

private fun sortKey(s: String): String = s.trimStart { !it.isLetterOrDigit() }.lowercase()

private fun greeting(): String = str(
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "greeting_morning"
        in 12..17 -> "greeting_afternoon"
        else -> "greeting_evening"
    }
)

@Composable
private fun EssTitle(text: String, modifier: Modifier = Modifier) {
    Txt(text, 22, FontWeight.ExtraBold, modifier = modifier, letterSpacing = -0.3f)
}

@Composable
private fun EssChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier.height(36.dp)
            .hoverClick(shape, hover = if (selected) c.accent else c.surface, base = if (selected) c.accent else c.surfaceLow, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, 13, if (selected) FontWeight.ExtraBold else FontWeight.Bold, if (selected) c.onAccent else c.textTertiary)
    }
}

/** Até quatro capas diferentes em mosaico 2×2; com menos, uma capa só. */
@Composable
private fun Collage(tracks: List<Track>, size: Dp, corner: Dp) {
    val covers = remember(tracks) { tracks.distinctBy { it.albumKey }.take(4) }
    if (covers.size < 4) {
        AlbumArt(covers.firstOrNull(), size, corner)
        return
    }
    val half = (size - 2.dp) / 2
    Column(Modifier.size(size).clip(RoundedCornerShape(corner)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        covers.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { pair.forEach { AlbumArt(it, half, 0.dp) } }
        }
    }
}

/** Capa com título e subtítulo embaixo. */
@Composable
private fun Cover(title: String, subtitle: String?, width: Dp, round: Boolean = false, onClick: () -> Unit, art: @Composable (Dp) -> Unit) {
    val c = SonivellTheme.colors
    Column(
        Modifier.width(width).hoverClick(RoundedCornerShape(18.dp), hover = c.surface, onClick = onClick).padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        art(width)
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start) {
            Txt(title, 14, FontWeight.ExtraBold)
            if (subtitle != null) Txt(subtitle, 12, FontWeight.Medium, c.textSecondary)
        }
    }
}

/** Grade de capas que se ajusta à largura da janela. */
@Composable
private fun <T> CoverGrid(list: List<T>, cell: @Composable (T, Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = ((maxWidth + GAP) / (CELL + GAP)).toInt().coerceAtLeast(2)
        val w = (maxWidth - GAP * (cols - 1)) / cols
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            list.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) { row.forEach { cell(it, w) } }
            }
        }
    }
}

@Composable
private fun EssScaffold(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(30.dp), content = content)
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

// ---------------------------------------------------------------- Para você

@Composable
fun ForYouEssencial(ctl: Controller, nav: Nav, actions: Actions) {
    val lib by ctl.library.collectAsState()
    val favorites by ctl.favorites.collectAsState()
    val player by ctl.player.collectAsState()
    val resume by ctl.resume.collectAsState()
    val library = lib.library
    if (library.tracks.isEmpty()) {
        EmptyLibrary(ctl)
        return
    }
    val liked = remember(library, favorites) { library.tracks.filter { it.id in favorites } }
    val mostPlayed = remember(library, player.index) {
        library.tracks.map { it to ctl.user.playCount(it.id) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
    val again = remember(library) {
        val now = System.currentTimeMillis()
        library.tracks.filter { ctl.user.lastPlayed(it.id).let { last -> last > 0L && now - last > 30L * 86_400_000L } }.shuffled().take(10)
    }
    val recentAlbums = remember(library) { library.albums.sortedByDescending { a -> a.tracks.maxOf { it.addedMs } }.take(12) }

    EssScaffold {
        item {
            Row(Modifier.widthIn(max = MAX_W.dp).fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt(greeting(), 36, FontWeight.ExtraBold, letterSpacing = -1f, modifier = Modifier.weight(1f))
                SearchBox(nav.query, { nav.query = it; nav.go(Route.Search) }, Modifier.width(320.dp))
            }
        }
        val r = resume
        val heroTrack = r?.first ?: again.firstOrNull() ?: library.recentlyAdded.firstOrNull()
        if (heroTrack != null) item {
            EssHero(
                track = heroTrack,
                tag = str(if (r != null) "ess_continue" else "fy_pick"),
                progress = r?.let { (t, pos) -> (pos.toFloat() / t.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                meta = r?.let { (t, pos) -> str("ess_remaining", formatTime((t.durationMs - pos).coerceAtLeast(0L))) },
                onPlay = { if (r != null) ctl.playFrom(listOf(r.first), 0, str("nav_home"), r.second) else ctl.playFrom(listOf(heroTrack), 0, str("nav_home")) },
            )
        }
        val mixes = buildList {
            if (liked.isNotEmpty()) add(Triple(str("source_liked"), str("desktop_tracks_count", liked.size), liked))
            if (mostPlayed.isNotEmpty()) add(Triple(str("auto_most"), str("desktop_tracks_count", mostPlayed.size), mostPlayed.take(100)))
            val recent = library.recentlyAdded.take(100)
            if (recent.isNotEmpty()) add(Triple(str("recently_added"), str("desktop_tracks_count", recent.size), recent))
        }
        if (mixes.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EssTitle(str("ess_mixes"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    items(mixes) { (title, sub, list) ->
                        Cover(title, sub, 196.dp, onClick = { nav.detail = Detail(title, sub, list, list.first(), title) }) { w -> Collage(list, w, 20.dp) }
                    }
                }
            }
        }
        if (again.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EssTitle(str("ess_listen_again"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    itemsIndexed(again) { i, t ->
                        Cover(t.title, t.artist, 160.dp, onClick = { ctl.playFrom(again, i, str("nav_home")) }) { w -> AlbumArt(t, w, 18.dp) }
                    }
                }
            }
        }
        if (recentAlbums.isNotEmpty()) item {
            Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EssTitle(str("recently_added"))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    items(recentAlbums) { a ->
                        Cover(a.title, a.artist, 160.dp, onClick = { actions.openAlbum(a) }) { w -> AlbumArt(a.tracks.first(), w, 18.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EssHero(track: Track, tag: String, progress: Float?, meta: String?, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier.widthIn(max = MAX_W.dp).fillMaxWidth().hoverClick(RoundedCornerShape(28.dp), hover = c.surfaceHigh, base = c.surface, onClick = onPlay).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        AlbumArt(track, 220.dp, 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.height(28.dp).clip(RoundedCornerShape(999.dp)).background(c.background).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Txt(tag, 12, FontWeight.ExtraBold)
            }
            Txt(track.title, 34, FontWeight.ExtraBold, letterSpacing = -0.8f, maxLines = 2)
            Txt(track.artist, 16, FontWeight.Medium, c.textSecondary)
            if (progress != null) {
                Row(Modifier.widthIn(max = 520.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.outline)) {
                        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(c.accent))
                    }
                    if (meta != null) Txt(meta, 12, FontWeight.SemiBold, c.textSecondary)
                }
            }
        }
        RoundPlay(64.dp, str("cd_play"), onPlay)
    }
}

@Composable
private fun RoundPlay(size: Dp, label: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Box(Modifier.size(size).hoverClick(CircleShape, hover = c.accent.copy(alpha = 0.85f), base = c.accent, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Traco.play, null, Modifier.size(size * 0.38f), tint = c.onAccent)
    }
}

// ---------------------------------------------------------------- Buscar

private enum class EssFilter { All, Songs, Albums, Artists }

@Composable
fun SearchEssencial(ctl: Controller, nav: Nav, actions: Actions) {
    val c = SonivellTheme.colors
    val lib by ctl.library.collectAsState()
    val favorites by ctl.favorites.collectAsState()
    val player by ctl.player.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = nav.query
    val library = lib.library
    val tracks = remember(q, library) { library.search(q) }
    val albums = remember(q, library) { library.searchAlbums(q).take(14) }
    val artists = remember(q, library) { library.searchArtists(q).take(14) }
    var filter by remember(q) { mutableStateOf(EssFilter.All) }

    EssScaffold {
        item {
            Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Txt(str("nav_search"), 36, FontWeight.ExtraBold, letterSpacing = -1f)
                Row(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(999.dp)).background(c.textPrimary).padding(start = 20.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Traco.search, null, Modifier.size(20.dp), tint = c.background.copy(alpha = 0.75f))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Txt(str("ess_search_hint"), 15, FontWeight.SemiBold, c.background.copy(alpha = 0.6f))
                        BasicTextField(
                            value = q, onValueChange = { nav.query = it }, singleLine = true,
                            textStyle = TextStyle(fontFamily = Manrope, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.background),
                            cursorBrush = SolidColor(c.background),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (q.isNotEmpty()) IconBtn(Traco.close, str("cd_clear_search"), { nav.query = "" }, size = 40.dp, iconSize = 16.dp, tint = c.background)
                }
                if (q.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(EssFilter.All to "tab_all", EssFilter.Songs to "tab_songs", EssFilter.Albums to "tab_albums", EssFilter.Artists to "tab_artists").forEach { (f, key) ->
                            EssChip(str(key), filter == f) { filter = f }
                        }
                    }
                }
            }
        }
        if (q.isBlank()) {
            essBrowse(library, nav)
        } else if (tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
            item { Txt(str("search_none"), 15, FontWeight.Medium, c.textSecondary) }
        } else {
            if (filter == EssFilter.All) {
                val a = artists.firstOrNull()
                val al = albums.firstOrNull()
                val t = tracks.firstOrNull()
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        EssTitle(str("search_best"))
                        when {
                            a != null -> BestCard(a.first, str("kind_artist"), a.second.first(), true, { actions.openArtist(a.first) }) { ctl.playFrom(a.second, 0, a.first) }
                            al != null -> BestCard(al.title, str("kind_album", al.artist), al.tracks.first(), false, { actions.openAlbum(al) }) { ctl.playFrom(al.tracks, 0, al.title) }
                            t != null -> BestCard(t.title, t.artist, t, false, { ctl.playFrom(tracks, 0, str("search_hint")) }) { ctl.playFrom(tracks, 0, str("search_hint")) }
                        }
                    }
                }
            }
            if ((filter == EssFilter.All || filter == EssFilter.Songs) && tracks.isNotEmpty()) {
                val shown = if (filter == EssFilter.Songs) tracks.take(200) else tracks.take(5)
                item { EssTitle(str("tab_songs")) }
                itemsIndexed(shown) { i, t ->
                    TrackRow(t, ctl, { ctl.playFrom(tracks, i, str("search_hint")) }, t.id in favorites, player.current?.id == t.id, actions::openAlbum, actions::openArtist)
                }
            }
            if ((filter == EssFilter.All || filter == EssFilter.Albums) && albums.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EssTitle(str("tab_albums"))
                    if (filter == EssFilter.Albums) {
                        CoverGrid(albums) { a, w -> Cover(a.title, a.artist, w, onClick = { actions.openAlbum(a) }) { s -> AlbumArt(a.tracks.first(), s, 16.dp) } }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                            items(albums) { a -> Cover(a.title, a.artist, 160.dp, onClick = { actions.openAlbum(a) }) { s -> AlbumArt(a.tracks.first(), s, 18.dp) } }
                        }
                    }
                }
            }
            if ((filter == EssFilter.All || filter == EssFilter.Artists) && artists.isNotEmpty()) item {
                Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EssTitle(str("tab_artists"))
                    if (filter == EssFilter.Artists) {
                        CoverGrid(artists) { (name, list), w -> Cover(name, null, w, round = true, onClick = { actions.openArtist(name) }) { s -> AlbumArt(list.first(), s, s / 2) } }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                            items(artists) { (name, list) -> Cover(name, null, 150.dp, round = true, onClick = { actions.openArtist(name) }) { s -> AlbumArt(list.first(), s, s / 2) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BestCard(title: String, subtitle: String, track: Track, round: Boolean, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().hoverClick(RoundedCornerShape(24.dp), hover = c.surfaceHigh, base = c.surface, onClick = onOpen).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AlbumArt(track, 112.dp, if (round) 56.dp else 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(title, 26, FontWeight.ExtraBold, letterSpacing = -0.5f, maxLines = 2)
            Txt(subtitle, 14, FontWeight.Medium, c.textSecondary)
        }
        RoundPlay(56.dp, str("cd_play"), onPlay)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.essBrowse(library: com.juscelino.sonivell.desktop.Library, nav: Nav) {
    val genres = library.genres.filterNot { isGenericGenreName(it.first) }.sortedByDescending { it.second.size }
    if (genres.isNotEmpty()) item {
        var all by remember { mutableStateOf(false) }
        val shown = if (all) genres else genres.take(8)
        Column(Modifier.widthIn(max = MAX_W.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EssTitle(str("browse_genres"))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cols = ((maxWidth + GAP) / (260.dp + GAP)).toInt().coerceIn(2, 4)
                val w = (maxWidth - GAP * (cols - 1)) / cols
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    shown.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                            row.forEach { (name, list) -> GenreCover(name, list, w) { nav.detail = Detail(name, str("desktop_tracks_count", list.size), list, list.first(), str("kind_genre", name)) } }
                        }
                    }
                }
            }
            if (genres.size > 8) {
                val c = SonivellTheme.colors
                Box(Modifier.hoverClick(RoundedCornerShape(12.dp), hover = c.surface) { all = !all }.padding(10.dp)) {
                    Txt(if (all) str("browse_show_less") else str("browse_show_all", genres.size), 14, FontWeight.ExtraBold, c.accent)
                }
            }
        }
    }
    val decades = library.tracks.filter { it.year in 1900..2100 }.groupBy { it.year / 10 * 10 }.toSortedMap()
    if (decades.isNotEmpty()) item {
        Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EssTitle(str("browse_decades"))
            DecadeRow(decades, nav)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecadeRow(decades: Map<Int, List<Track>>, nav: Nav) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        decades.forEach { (d, list) ->
            val name = str("decade_label", if (d in 1900..1999) (d % 100).toString() else d.toString())
            val sorted = list.sortedWith(compareBy({ it.year }, { it.album.lowercase() }, { it.trackNo }))
            EssChip(name, false) { nav.detail = Detail(name, str("desktop_tracks_count", list.size), sorted, sorted.first(), name) }
        }
    }
}

@Composable
private fun GenreCover(name: String, tracks: List<Track>, width: Dp, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val covers = remember(tracks) { tracks.distinctBy { it.albumKey }.take(3) }
    val h = 110.dp
    Column(Modifier.width(width).hoverClick(RoundedCornerShape(18.dp), hover = c.surface, onClick = onClick).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.width(width).height(h).clip(RoundedCornerShape(18.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (covers.size < 3) {
                Box(Modifier.fillMaxSize()) { AlbumArt(covers.firstOrNull(), width, 0.dp) }
            } else {
                val w = (width - 4.dp) / 3
                covers.forEach { Box(Modifier.width(w).height(h)) { AlbumArt(it, maxOf(w, h), 0.dp) } }
            }
        }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(name, 15, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Txt(tracks.size.toString(), 12, FontWeight.SemiBold, c.textSecondary)
        }
    }
}

// ---------------------------------------------------------------- Biblioteca

private enum class EssTab(val key: String) { Albums("tab_albums"), Artists("tab_artists"), Songs("tab_songs"), Genres("tab_genres"), Folders("tab_folders") }

private object EssLibState {
    var tab by mutableStateOf(EssTab.Albums)
    var byAdded by mutableStateOf(false)
}

@Composable
fun LibraryEssencial(ctl: Controller, nav: Nav, actions: Actions) {
    val c = SonivellTheme.colors
    val lib by ctl.library.collectAsState()
    val favorites by ctl.favorites.collectAsState()
    val player by ctl.player.collectAsState()
    val library = lib.library
    if (library.tracks.isEmpty()) {
        EmptyLibrary(ctl)
        return
    }
    val tab = EssLibState.tab
    val liked = remember(library, favorites) { library.tracks.filter { it.id in favorites } }

    EssScaffold {
        item {
            Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Txt(str("nav_library"), 36, FontWeight.ExtraBold, letterSpacing = -1f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EssTab.entries.forEach { t -> EssChip(str(t.key), tab == t) { EssLibState.tab = t } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pin(str("source_liked"), str("desktop_tracks_count", liked.size), { if (liked.isNotEmpty()) nav.detail = Detail(str("source_liked"), str("desktop_tracks_count", liked.size), liked, liked.first(), str("source_liked")) }) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                            Icon(Traco.liked, null, Modifier.size(20.dp), tint = c.onAccent)
                        }
                    }
                    val recent = library.recentlyAdded.take(100)
                    Pin(str("ess_recent"), str("ess_recent_sub"), { if (recent.isNotEmpty()) nav.detail = Detail(str("recently_added"), str("desktop_tracks_count", recent.size), recent, recent.first(), str("recently_added")) }) {
                        Collage(recent, 48.dp, 12.dp)
                    }
                }
            }
        }
        when (tab) {
            EssTab.Albums -> {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.hoverClick(RoundedCornerShape(10.dp), hover = c.surface) { EssLibState.byAdded = !EssLibState.byAdded }.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Traco.sort, null, Modifier.size(16.dp), tint = c.textTertiary)
                                Txt(str(if (EssLibState.byAdded) "ess_sort_added" else "ess_sort_az"), 13, FontWeight.Bold, c.textTertiary)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Txt(str("desktop_albums_count", library.albums.size), 12, FontWeight.SemiBold, c.textSecondary)
                    }
                }
                item {
                    val sorted = remember(library, EssLibState.byAdded) {
                        if (EssLibState.byAdded) library.albums.sortedByDescending { a -> a.tracks.maxOf { it.addedMs } } else library.albums.sortedBy { sortKey(it.title) }
                    }
                    Box(Modifier.padding(bottom = 24.dp)) {
                        CoverGrid(sorted) { a: Album, w -> Cover(a.title, a.artist, w, onClick = { actions.openAlbum(a) }) { s -> AlbumArt(a.tracks.first(), s, 16.dp) } }
                    }
                }
            }
            EssTab.Artists -> item {
                val sorted = remember(library) { library.artists.sortedBy { sortKey(it.first) } }
                Box(Modifier.padding(bottom = 24.dp)) {
                    CoverGrid(sorted) { (name, list), w -> Cover(name, null, w, round = true, onClick = { actions.openArtist(name) }) { s -> AlbumArt(list.first(), s, s / 2) } }
                }
            }
            EssTab.Genres -> item {
                val sorted = remember(library) { library.genres.sortedByDescending { it.second.size } }
                Box(Modifier.padding(bottom = 24.dp)) {
                    CoverGrid(sorted) { (name, list), w ->
                        Cover(name, str("desktop_tracks_count", list.size), w, onClick = { nav.detail = Detail(name, str("desktop_tracks_count", list.size), list, list.first(), str("kind_genre", name)) }) { s -> Collage(list, s, 16.dp) }
                    }
                }
            }
            EssTab.Folders -> items(library.folders) { (path, list) ->
                Row(
                    Modifier.fillMaxWidth().hoverClick(RoundedCornerShape(14.dp), hover = c.surface) {
                        nav.detail = Detail(path.substringAfterLast('/'), path, list, list.first(), str("kind_folder", path.substringAfterLast('/')))
                    }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Collage(list, 52.dp, 12.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(path.substringAfterLast('/'), 15, FontWeight.ExtraBold)
                        Txt(str("desktop_tracks_count", list.size), 12, FontWeight.Medium, c.textSecondary)
                    }
                }
            }
            EssTab.Songs -> {
                val sorted = library.tracks.sortedBy { sortKey(it.title) }
                itemsIndexed(sorted) { i, t ->
                    TrackRow(t, ctl, { ctl.playFrom(sorted, i, str("nav_library")) }, t.id in favorites, player.current?.id == t.id, actions::openAlbum, actions::openArtist)
                }
            }
        }
    }
}

@Composable
private fun Pin(title: String, subtitle: String, onClick: () -> Unit, art: @Composable () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier.width(260.dp).height(64.dp).hoverClick(RoundedCornerShape(18.dp), hover = c.surfaceHigh, base = c.surface, onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        art()
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Txt(title, 14, FontWeight.ExtraBold)
            Txt(subtitle, 12, FontWeight.Medium, c.textSecondary)
        }
    }
}
