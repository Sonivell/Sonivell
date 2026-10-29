package com.juscelino.sonivell.ui.library

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.LibraryState
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Genre
import com.juscelino.sonivell.data.Ranked
import com.juscelino.sonivell.data.isGenericGenre
import com.juscelino.sonivell.ui.components.PlaySquircle
import com.juscelino.sonivell.ui.theme.ArtTones
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Gêneros que não dizem nada ("Genre:", "Music", "Other"…): vão para "Tags para arrumar", não para a grade. */
internal object GenreTags {
    fun isGeneric(name: String): Boolean = isGenericGenre(name)
}

/** Quantos blocos de gênero aparecem antes do "Ver todos". */
private const val GRID_LIMIT = 9

/**
 * Busca vazia no layout Ondas (mosaico): o gênero do mês em destaque, os demais em blocos
 * do tamanho do espaço que ocupam, as décadas numa linha do tempo e as tags genéricas à parte.
 * Tudo sai da leitura da biblioteca; enquanto ela não termina, mostra só o aviso de leitura.
 */
internal fun LazyListScope.genreBrowse(
    state: LibraryState,
    decades: List<Int>,
    genreOfMonth: Ranked<String>?,
    actions: LibraryActions,
) {
    if (state.loading && state.allTracks.isEmpty()) {
        item(key = "browse-loading") { LoadingCard() }
        return
    }
    item(key = "browse") {
        val good = remember(state.genres) {
            state.genres.filterNot { GenreTags.isGeneric(it.name) }.sortedByDescending { it.tracks.size }
        }
        val generic = remember(state.genres) { state.genres.filter { GenreTags.isGeneric(it.name) } }
        val monthGenre = genreOfMonth?.let { r -> good.firstOrNull { it.name.equals(r.item, ignoreCase = true) } }
        val hero = monthGenre ?: good.firstOrNull()
        val rest = good.filter { it !== hero }

        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            if (hero != null) {
                HeroCard(
                    genre = hero,
                    isMonth = monthGenre != null,
                    listenedMs = if (monthGenre != null) genreOfMonth?.listenedMs ?: 0L else 0L,
                    onOpen = { actions.onOpenGenre(hero.name) },
                    onPlay = { actions.onPlay(hero.tracks.shuffled(), 0, hero.name) },
                )
            } else {
                Text(
                    stringResource(R.string.browse_no_genres),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SonivellTheme.colors.textSecondary,
                )
            }
            if (rest.isNotEmpty()) GenreMosaic(rest, actions)
            if (decades.isNotEmpty()) DecadeTimeline(state, decades, actions)
            if (generic.isNotEmpty()) GenericTagsCard(generic, actions)
        }
    }
}

@Composable
private fun Heading(text: String, trailing: String? = null) {
    val c = SonivellTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, letterSpacing = (-0.2).sp),
            fontWeight = FontWeight.ExtraBold,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c.textSecondary)
        }
    }
}

@Composable
private fun LoadingCard() {
    val c = SonivellTheme.colors
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.5.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.browse_loading_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
            Text(stringResource(R.string.browse_loading_sub), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}

// ---------------------------------------------------------------- Destaque

@Composable
private fun HeroCard(genre: Genre, isMonth: Boolean, listenedMs: Long, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = SonivellTheme.colors
    val ink = c.onAccent
    val songs = pluralStringResource(R.plurals.songs_count, genre.tracks.size, genre.tracks.size)
    val listened = listenedText(listenedMs)
    val subtitle = if (listened != null) "$songs · ${stringResource(R.string.browse_listened, listened)}" else songs
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(c.accent)
            .clickable(onClick = onOpen)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(if (isMonth) R.string.browse_month_genre else R.string.browse_top_genre).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.7.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = ink.copy(alpha = 0.72f),
                )
                Text(
                    genre.name,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, letterSpacing = (-0.6).sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = 0.72f))
            }
            PlaySquircle(
                isPlaying = false,
                onClick = onPlay,
                size = 52.dp,
                corner = 18.dp,
                iconSize = 20.dp,
                contentDescription = stringResource(R.string.browse_play_genre, genre.name),
                background = ink,
                tint = c.accent,
            )
        }
        SignatureBars(genre.name, ink, Modifier.fillMaxWidth().height(44.dp))
    }
}

@Composable
private fun listenedText(ms: Long): String? {
    if (ms <= 0L) return null
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    return if (hours >= 1) stringResource(R.string.browse_hours, hours.toInt())
    else stringResource(R.string.browse_minutes, TimeUnit.MILLISECONDS.toMinutes(ms).coerceAtLeast(1).toInt())
}

/** Barras verticais com um desenho próprio de cada gênero (sempre o mesmo para o mesmo nome). */
@Composable
private fun SignatureBars(seedText: String, color: Color, modifier: Modifier = Modifier) {
    val seed = remember(seedText) { (abs(seedText.lowercase().hashCode()) % 1000) / 160f }
    Canvas(modifier) {
        val barW = 4.dp.toPx()
        val gap = 3.dp.toPx()
        val count = ((size.width + gap) / (barW + gap)).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val v = abs(sin(i * 0.45 + seed) * cos(i * 0.19 + seed * 1.3)).toFloat()
            val h = size.height * (0.14f + v * 0.86f)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

// ---------------------------------------------------------------- Mosaico

@Composable
private fun GenreMosaic(genres: List<Genre>, actions: LibraryActions) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shown = if (showAll) genres else genres.take(GRID_LIMIT)
    val gap = 10.dp
    val unit = 92.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Heading(stringResource(R.string.browse_genres), stringResource(R.string.browse_by_size))
        var index = 0
        if (shown.size >= 3) {
            Row(Modifier.fillMaxWidth().height(unit * 2 + gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
                GenreTile(shown[0], 0, tall = true, actions, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    GenreTile(shown[1], 1, tall = false, actions, Modifier.fillMaxWidth().weight(1f))
                    GenreTile(shown[2], 2, tall = false, actions, Modifier.fillMaxWidth().weight(1f))
                }
            }
            index = 3
        }
        shown.drop(index).chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().height(unit), horizontalArrangement = Arrangement.spacedBy(gap)) {
                pair.forEachIndexed { col, g ->
                    GenreTile(g, index + row * 2 + col, tall = false, actions, Modifier.weight(1f).fillMaxHeight())
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (genres.size > GRID_LIMIT) {
            val c = SonivellTheme.colors
            Text(
                if (showAll) stringResource(R.string.browse_show_less) else stringResource(R.string.browse_show_all, genres.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = c.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { showAll = !showAll }
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun GenreTile(genre: Genre, index: Int, tall: Boolean, actions: LibraryActions, modifier: Modifier) {
    val c = SonivellTheme.colors
    val tone = ArtTones.cores[index % ArtTones.cores.size]
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.outline, shape)
            .clickable { actions.onOpenGenre(genre.name) }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                genre.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = if (tall) 24.sp else 17.sp, letterSpacing = (-0.2).sp),
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary,
                maxLines = if (tall) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(tone))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                pluralStringResource(R.plurals.songs_count, genre.tracks.size, genre.tracks.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            MiniWave(genre.name, tone, Modifier.size(width = 56.dp, height = 18.dp))
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
            val t = x / size.width
            path.lineTo(x, mid + sin(t * turns * 2 * PI).toFloat() * size.height * amp)
            x += 1.5f
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------- Décadas

@Composable
private fun DecadeTimeline(state: LibraryState, decades: List<Int>, actions: LibraryActions) {
    val c = SonivellTheme.colors
    val res = LocalContext.current.resources
    val ordered = remember(decades) { decades.sorted() }
    val counts = remember(state.allTracks) {
        state.allTracks.mapNotNull { it.year?.takeIf { y -> y in 1900..2100 }?.let { y -> y / 10 * 10 } }.groupingBy { it }.eachCount()
    }
    val top = ordered.maxByOrNull { counts[it] ?: 0 }
    val line = c.outline
    val circle = 44.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading(stringResource(R.string.browse_decades))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .drawBehind {
                    val y = 2.dp.toPx() + circle.toPx() / 2f
                    val inset = 36.dp.toPx()
                    if (size.width > inset * 2) {
                        drawLine(line, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 2.dp.toPx())
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ordered.forEach { d ->
                val name = res.getString(R.string.decade_label, if (d in 1900..1999) (d % 100).toString() else d.toString())
                DecadeStop(
                    short = "%02d".format(d % 100),
                    count = counts[d] ?: 0,
                    highlighted = d == top,
                    description = name,
                    circle = circle,
                    onClick = { actions.onOpenAuto("decada-$d") },
                )
            }
        }
    }
}

@Composable
private fun DecadeStop(short: String, count: Int, highlighted: Boolean, description: String, circle: Dp, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Column(
        Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(circle)
                .clip(CircleShape)
                .background(c.background)
                .border(2.dp, if (highlighted) c.accent else c.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(short, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
        }
        Text(
            pluralStringResource(R.plurals.songs_count, count, count),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = c.textSecondary,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- Tags genéricas

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenericTagsCard(generic: List<Genre>, actions: LibraryActions) {
    val c = SonivellTheme.colors
    val dash = c.outline
    val total = remember(generic) { generic.flatMap { it.tracks }.distinctBy { it.id }.size }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.browse_tags_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
                Text(pluralStringResource(R.plurals.browse_tags_sub, total, total), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.textPrimary)
                    .clickable(role = Role.Button, onClick = actions.onOpenOrganize)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.browse_review), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = c.background)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            generic.forEach { g ->
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.surfaceHigh)
                        .clickable { actions.onOpenGenre(g.name) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(g.name.trim(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c.textTertiary)
                }
            }
        }
    }
}
