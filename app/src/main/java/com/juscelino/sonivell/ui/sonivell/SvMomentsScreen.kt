package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.theme.SonivellTheme

/**
 * Momentos: as fases da sua biblioteca em uma linha do tempo. Cada fase é uma década (pelo ano das
 * tags) e o último ponto, "Agora", é o que você ouviu há pouco.
 */
@Composable
fun SvMomentsScreen(
    tracks: List<Track>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenAuto: (String) -> Unit,
) {
    val nc = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val phases = remember(tracks) {
        tracks.filter { (it.year ?: 0) in 1900..2100 }
            .groupBy { (it.year ?: 0) / 10 * 10 }
            .toSortedMap()
            .map { (decade, list) -> decade to list }
    }
    val recall = remember(phases) { phases.randomOrNull() }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SvSquare(icons.back, stringResource(R.string.cd_back), onBack)
        SvTitle(stringResource(R.string.sv_moments_title), stringResource(R.string.sv_moments_sub), Modifier.padding(bottom = 10.dp))

        phases.forEachIndexed { i, (decade, list) ->
            val label = if (decade in 1900..1999) (decade % 100).toString() else decade.toString()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Timeline(first = i == 0, last = false)
                SvRow(
                    resources.getString(R.string.decade_label, label),
                    resources.getString(R.string.sv_songs_count, list.size),
                    { onOpenAuto("decada-$decade") },
                    Modifier.weight(1f),
                    art = { SvCircleArt(list.firstOrNull(), 48.dp) },
                )
            }
        }
        if (tracks.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Timeline(first = phases.isEmpty(), last = true)
                SvRow(
                    stringResource(R.string.sv_moments_now),
                    stringResource(R.string.auto_recent_played),
                    { onOpenAuto("ouvidas-recentemente") },
                    Modifier.weight(1f),
                    art = { SvCircleArt(tracks.firstOrNull(), 48.dp) },
                )
            }
        }
        recall?.let { (decade, _) ->
            SvCard(Modifier.fillMaxWidth().padding(top = 8.dp), onClick = { onOpenAuto("decada-$decade") }) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.sv_moments_recall), style = MaterialTheme.typography.titleMedium, color = nc.textPrimary)
                    Text(stringResource(R.string.sv_moments_recall_hint), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = nc.textSecondary)
                }
            }
        }
    }
}

/** A linha vertical com um ponto, na lateral de cada fase. */
@Composable
private fun Timeline(first: Boolean, last: Boolean) {
    val nc = SonivellTheme.colors
    Box(
        Modifier
            .width(14.dp)
            .fillMaxHeight()
            .drawBehind {
                val x = size.width / 2f
                val top = if (first) size.height / 2f else 0f
                val bottom = if (last) size.height / 2f else size.height
                drawLine(nc.outline, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
                drawCircle(if (last) nc.accent else nc.textSecondary, radius = 4.dp.toPx(), center = Offset(x, size.height / 2f))
            },
    )
}
