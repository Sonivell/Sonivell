package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.desktop.Controller
import com.juscelino.sonivell.desktop.Library
import com.juscelino.sonivell.desktop.Track
import com.juscelino.sonivell.desktop.str
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Gêneros que não dizem nada ("Genre:", "Music", "Other"…): vão para "Tags para arrumar", não para a grade. */
private val genericGenres = setOf(
    "genre", "genres", "music", "musica", "música", "other", "others", "outro", "outros",
    "unknown", "desconhecido", "misc", "miscellaneous", "various", "none", "n/a", "default",
)

internal fun isGenericGenreName(name: String): Boolean {
    val n = name.trim().trimEnd(':', '.', ' ').lowercase()
    return n.isEmpty() || n in genericGenres
}

private val tones = listOf(Color(0xFFC8F04A), Color(0xFF7CC4FF), Color(0xFFFF9F5A), Color(0xFFE8A6FF))
private const val DAY_MS = 86_400_000L

/** Blocos mostrados antes do "Ver todos": um grande + quatro + duas fileiras de quatro. */
private const val GRID_LIMIT = 13

private class MonthGenre(val name: String, val listenedMs: Long)

/** Gênero mais ouvido nos últimos 30 dias (escutas completas ou de 30 s ou mais). */
private fun genreOfMonth(ctl: Controller, library: Library): MonthGenre? {
    val from = System.currentTimeMillis() - 30 * DAY_MS
    val count = HashMap<String, Int>()
    val time = HashMap<String, Long>()
    ctl.user.history.toList().forEach { l ->
        if (l.atMs < from) return@forEach
        val g = library.byId[l.trackId]?.genre?.takeIf { it.isNotBlank() && !isGenericGenreName(it) } ?: return@forEach
        time[g] = (time[g] ?: 0L) + l.listenedMs
        if (l.completed || l.listenedMs >= 30_000) count[g] = (count[g] ?: 0) + 1
    }
    val best = count.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { time[it.key] ?: 0L }) ?: return null
    return MonthGenre(best.key, time[best.key] ?: 0L)
}

/**
 * Busca vazia (mosaico): o gênero do mês em destaque, os demais em blocos do tamanho do espaço
 * que ocupam, as décadas numa linha do tempo e as tags genéricas à parte.
 */
@Composable
fun GenreBrowse(ctl: Controller, library: Library, scanning: Boolean, playIndex: Int, nav: Nav) {
    if (scanning && library.tracks.isEmpty()) {
        ScanningCard()
        return
    }
    val good = remember(library) { library.genres.filterNot { isGenericGenreName(it.first) }.sortedByDescending { it.second.size } }
    val generic = remember(library) { library.genres.filter { isGenericGenreName(it.first) } }
    val month = remember(library, playIndex) { genreOfMonth(ctl, library) }
    val monthGenre = month?.let { m -> good.firstOrNull { it.first == m.name } }
    val hero = monthGenre ?: good.firstOrNull()
    val rest = good.filter { it !== hero }
    val open = { name: String, list: List<Track> ->
        nav.detail = Detail(name, str("desktop_tracks_count", list.size), list, list.first(), str("kind_genre", name))
    }

    Column(Modifier.widthIn(max = 1180.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        if (hero != null) {
            HeroCard(
                name = hero.first,
                tracks = hero.second,
                isMonth = monthGenre != null,
                listenedMs = if (monthGenre != null) month?.listenedMs ?: 0L else 0L,
                onOpen = { open(hero.first, hero.second) },
                onPlay = { ctl.playShuffled(hero.second, hero.first) },
            )
        } else {
            Txt(str("browse_no_genres"), 15, FontWeight.Medium, SonivellTheme.colors.textSecondary)
        }
        if (rest.isNotEmpty()) Mosaic(rest, open)
        Decades(library, nav)
        if (generic.isNotEmpty()) GenericTags(generic, open, nav)
    }
}

@Composable
private fun ScanningCard() {
    val c = SonivellTheme.colors
    Row(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().cardSurface(RoundedCornerShape(22.dp), elevation = 10.dp).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.5.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(str("browse_loading_title"), 16, FontWeight.ExtraBold)
            Txt(str("browse_loading_sub"), 13, FontWeight.Medium, c.textSecondary, maxLines = 2)
        }
    }
}

@Composable
private fun Heading(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt(text, 21, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        if (trailing != null) Txt(trailing, 13, FontWeight.SemiBold, SonivellTheme.colors.textSecondary)
    }
}

// ---------------------------------------------------------------- Destaque

@Composable
private fun HeroCard(name: String, tracks: List<Track>, isMonth: Boolean, listenedMs: Long, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    val ink = c.onAccent
    val listened = when {
        listenedMs <= 0L -> null
        listenedMs >= 3_600_000L -> str("browse_hours", (listenedMs / 3_600_000L).toInt())
        else -> str("browse_minutes", (listenedMs / 60_000L).coerceAtLeast(1).toInt())
    }
    val songs = str("desktop_tracks_count", tracks.size)
    val subtitle = if (listened != null) "$songs · ${str("browse_listened", listened)}" else songs
    Row(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .hoverClick(RoundedCornerShape(26.dp), hover = c.accent.copy(alpha = 0.92f), base = c.accent, onClick = onOpen)
            .padding(horizontal = 26.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(Modifier.widthIn(min = 200.dp, max = 340.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(str(if (isMonth) "browse_month_genre" else "browse_top_genre").uppercase(), 11, FontWeight.ExtraBold, ink.copy(alpha = 0.72f), letterSpacing = 0.7f)
            Txt(name, 38, FontWeight.ExtraBold, ink, letterSpacing = -1f)
            Txt(subtitle, 13, FontWeight.SemiBold, ink.copy(alpha = 0.72f))
        }
        SignatureBars(name, ink, Modifier.weight(1f).height(64.dp))
        Box(
            Modifier.size(60.dp).hoverClick(RoundedCornerShape(20.dp), hover = ink.copy(alpha = 0.85f), base = ink, label = str("browse_play_genre", name), onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Traco.play, null, Modifier.size(24.dp), tint = c.accent)
        }
    }
}

/** Barras verticais com um desenho próprio de cada gênero (sempre o mesmo para o mesmo nome). */
@Composable
private fun SignatureBars(seedText: String, color: Color, modifier: Modifier = Modifier) {
    val seed = remember(seedText) { (abs(seedText.lowercase().hashCode()) % 1000) / 160f }
    Canvas(modifier) {
        val barW = 4.dp.toPx()
        val gap = 4.dp.toPx()
        val count = ((size.width + gap) / (barW + gap)).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val v = abs(sin(i * 0.45 + seed) * kotlin.math.cos(i * 0.19 + seed * 1.3)).toFloat()
            val h = size.height * (0.12f + v * 0.88f)
            drawRoundRect(color, Offset(i * (barW + gap), (size.height - h) / 2f), Size(barW, h), CornerRadius(barW / 2f))
        }
    }
}

// ---------------------------------------------------------------- Mosaico

@Composable
private fun Mosaic(genres: List<Pair<String, List<Track>>>, open: (String, List<Track>) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    val shown = if (showAll) genres else genres.take(GRID_LIMIT)
    val gap = 12.dp
    val unit = 104.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Heading(str("browse_genres"), str("browse_by_size"))
        var start = 0
        if (shown.size >= 5) {
            Row(Modifier.fillMaxWidth().height(unit * 2 + gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(shown[0], 0, big = true, open, Modifier.weight(2f).fillMaxHeight())
                Column(Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (r in 0..1) {
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (k in 0..1) {
                                val i = 1 + r * 2 + k
                                Tile(shown[i], i, big = false, open, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
            start = 5
        }
        shown.drop(start).chunked(4).forEachIndexed { row, group ->
            Row(Modifier.fillMaxWidth().height(unit), horizontalArrangement = Arrangement.spacedBy(gap)) {
                group.forEachIndexed { k, g -> Tile(g, start + row * 4 + k, big = false, open, Modifier.weight(1f).fillMaxHeight()) }
                repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (genres.size > GRID_LIMIT) {
            val c = SonivellTheme.colors
            Box(Modifier.hoverClick(RoundedCornerShape(12.dp), hover = c.surface) { showAll = !showAll }.padding(horizontal = 10.dp, vertical = 10.dp)) {
                Txt(if (showAll) str("browse_show_less") else str("browse_show_all", genres.size), 14, FontWeight.ExtraBold, c.accent)
            }
        }
    }
}

@Composable
private fun Tile(genre: Pair<String, List<Track>>, index: Int, big: Boolean, open: (String, List<Track>) -> Unit, modifier: Modifier) {
    val c = SonivellTheme.colors
    val tone = tones[index % tones.size]
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .glassClick(shape, elevation = 8.dp) { open(genre.first, genre.second) }
            .padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(genre.first, if (big) 30 else 18, FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = if (big) 3 else 1, letterSpacing = if (big) -0.6f else -0.2f)
            Box(Modifier.padding(top = 7.dp).size(9.dp).clip(CircleShape).background(tone))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(str("desktop_tracks_count", genre.second.size), 13, FontWeight.SemiBold, c.textSecondary, modifier = Modifier.weight(1f))
            MiniWave(genre.first, tone, Modifier.size(width = if (big) 120.dp else 64.dp, height = if (big) 26.dp else 18.dp))
        }
    }
}

/** Uma onda curta; amplitude e frequência saem do nome, para cada gênero ter a sua. */
@Composable
private fun MiniWave(seedText: String, color: Color, modifier: Modifier = Modifier) {
    val h = remember(seedText) { abs(seedText.lowercase().hashCode()) }
    val amp = 0.18f + (h % 5) * 0.05f
    val turns = 1.5f + (h / 5 % 4) * 0.5f
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val path = Path()
        val mid = size.height / 2f
        var x = stroke
        path.moveTo(x, mid)
        while (x <= size.width - stroke) {
            path.lineTo(x, mid + sin(x / size.width * turns * 2 * PI).toFloat() * size.height * amp)
            x += 1.5f
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------- Décadas

@Composable
private fun Decades(library: Library, nav: Nav) {
    val byDecade = remember(library) {
        library.tracks.filter { it.year in 1900..2100 }.groupBy { it.year / 10 * 10 }.toSortedMap()
    }
    if (byDecade.isEmpty()) return
    val c = SonivellTheme.colors
    val top = byDecade.maxByOrNull { it.value.size }?.key
    val line = c.outline
    val circle = 52.dp
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading(str("browse_decades"))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .drawBehind {
                    val y = 4.dp.toPx() + circle.toPx() / 2f
                    val inset = 44.dp.toPx()
                    if (size.width > inset * 2) drawLine(line, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 2.dp.toPx())
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            byDecade.forEach { (d, list) ->
                val name = str("decade_label", if (d in 1900..1999) (d % 100).toString() else d.toString())
                val sorted = list.sortedWith(compareBy({ it.year }, { it.album.lowercase() }, { it.trackNo }))
                Column(
                    Modifier
                        .width(88.dp)
                        .hoverClick(RoundedCornerShape(16.dp), hover = c.surface, label = name) {
                            nav.detail = Detail(name, str("desktop_tracks_count", list.size), sorted, sorted.first(), name)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(circle).clip(CircleShape).background(c.background).border(2.dp, if (d == top) c.accent else c.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt("%02d".format(d % 100), 15, FontWeight.ExtraBold)
                    }
                    Txt(name, 12, FontWeight.ExtraBold)
                    Txt(str("desktop_tracks_count", list.size), 11, FontWeight.SemiBold, c.textSecondary)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Tags genéricas

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenericTags(generic: List<Pair<String, List<Track>>>, open: (String, List<Track>) -> Unit, nav: Nav) {
    val c = SonivellTheme.colors
    val dash = c.outline
    val all = remember(generic) { generic.flatMap { it.second }.distinctBy { it.id } }
    Column(
        Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(str("browse_tags_title"), 16, FontWeight.ExtraBold)
                Txt(str("desktop_browse_tags_sub", all.size), 13, FontWeight.Medium, c.textSecondary)
            }
            Box(
                Modifier.height(40.dp).hoverClick(RoundedCornerShape(12.dp), hover = c.textTertiary, base = c.textPrimary) {
                    if (all.isNotEmpty()) nav.detail = Detail(str("browse_tags_title"), str("desktop_tracks_count", all.size), all, all.first(), str("browse_tags_title"))
                }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Txt(str("browse_review"), 13, FontWeight.ExtraBold, c.background)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            generic.forEach { (name, list) ->
                Box(
                    Modifier.height(32.dp).hoverClick(RoundedCornerShape(999.dp), hover = c.outline, base = c.surfaceHigh) { open(name, list) }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(name.trim(), 12, FontWeight.SemiBold, c.textTertiary)
                }
            }
        }
    }
}
