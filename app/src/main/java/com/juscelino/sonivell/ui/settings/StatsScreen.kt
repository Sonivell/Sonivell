package com.juscelino.sonivell.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Ranked
import com.juscelino.sonivell.data.StatsPeriod
import com.juscelino.sonivell.data.StatsState
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.Chip
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.SectionLabel
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.text.DateFormatSymbols

@Composable
fun StatsScreen(
    stats: StatsState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPeriod: (StatsPeriod) -> Unit,
    onPlayTrack: (Track) -> Unit,
) {
    val c = SonivellTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back), onBack, background = c.surface)
            Text(stringResource(R.string.stats), style = MaterialTheme.typography.displaySmall, color = c.textPrimary)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                StatsPeriod.Semana to R.string.period_week,
                StatsPeriod.Mes to R.string.period_month,
                StatsPeriod.Ano to R.string.period_year,
                StatsPeriod.Tudo to R.string.period_all,
            ).forEach { (p, label) -> Chip(stringResource(label), selected = stats.period == p) { onPeriod(p) } }
        }

        if (stats.isEmpty) {
            Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            return@Column
        }

        // Números principais
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigNumber(formatListening(stats.listenedMs), stringResource(R.string.stats_listened), Modifier.weight(1f), comparison(stats))
            BigNumber(stats.plays.toString(), stringResource(R.string.stats_plays), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigNumber(stats.distinctTracks.toString(), stringResource(R.string.stats_distinct), Modifier.weight(1f))
            BigNumber(stats.firstListens.toString(), stringResource(R.string.stats_first), Modifier.weight(1f))
        }

        if (stats.topTracks.isNotEmpty()) {
            SectionLabel(stringResource(R.string.stats_top_tracks))
            stats.topTracks.forEachIndexed { i, r -> TrackLine(i + 1, r, onPlayTrack) }
        }
        RankList(stringResource(R.string.stats_top_artists), stats.topArtists.map { it.item to it.plays })
        if (stats.topAlbums.isNotEmpty()) {
            RankList(stringResource(R.string.stats_top_albums), stats.topAlbums.map { it.item.album to it.plays })
        }
        RankList(stringResource(R.string.stats_top_genres), stats.topGenres.map { it.item to it.plays })

        SectionLabel(stringResource(R.string.stats_by_hour))
        Bars(stats.byHour, labels = (0 until 24).map { if (it % 6 == 0) "${it}h" else "" })
        SectionLabel(stringResource(R.string.stats_by_weekday))
        val days = DateFormatSymbols.getInstance().shortWeekdays.drop(1) // índice 1 = domingo
        Bars(stats.byWeekday, labels = days)
    }
}

/** "Mais/menos que no período anterior", quando dá para comparar. */
@Composable
private fun comparison(stats: StatsState): String? {
    val prev = stats.previousListenedMs ?: return null
    if (prev <= 0) return null
    val change = ((stats.listenedMs - prev) * 100 / prev).toInt()
    return if (change >= 0) stringResource(R.string.stats_more, change) else stringResource(R.string.stats_less, -change)
}

@Composable
private fun formatListening(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes >= 60) pluralStringResource(R.plurals.hours_short, minutes / 60, minutes / 60)
    else pluralStringResource(R.plurals.minutes_short, minutes, minutes)
}

@Composable
private fun BigNumber(value: String, label: String, modifier: Modifier = Modifier, note: String? = null) {
    val c = SonivellTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = c.accent)
    }
}

@Composable
private fun TrackLine(position: Int, ranked: Ranked<Track>, onPlay: (Track) -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPlay(ranked.item) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("$position", style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.width(20.dp))
        AlbumArt(ranked.item, Modifier.size(44.dp))
        Column(Modifier.weight(1f)) {
            Text(ranked.item.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ranked.item.artist, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(pluralStringResource(R.plurals.plays_count, ranked.plays, ranked.plays), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
    }
}

@Composable
private fun RankList(title: String, items: List<Pair<String, Int>>) {
    if (items.isEmpty()) return
    val c = SonivellTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        val top = items.first().second.coerceAtLeast(1)
        items.forEachIndexed { i, (name, plays) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text("${i + 1}. $name", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(pluralStringResource(R.plurals.plays_count, plays, plays), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.track)) {
                    Box(Modifier.fillMaxWidth(plays.toFloat() / top).height(4.dp).background(c.accent))
                }
            }
        }
    }
}

/** Gráfico de barras simples (minutos ouvidos), com o total de cada barra para leitores de tela. */
@Composable
private fun Bars(values: List<Long>, labels: List<String>) {
    val c = SonivellTheme.colors
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val minutesLabel = stringResource(R.string.stats_minutes_label)
    Row(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp)).background(c.surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "${labels.getOrElse(i) { "" }} $v $minutesLabel" },
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((v.toFloat() / max).coerceIn(0.02f, 1f) * 0.8f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (v == max) c.accent else c.textSecondary.copy(alpha = 0.5f))
                )
                Text(labels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1)
            }
        }
    }
}
