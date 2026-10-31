package com.juscelino.sonivell.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Mood
import com.juscelino.sonivell.data.LyricHit
import com.juscelino.sonivell.ui.components.AlbumArt
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.cardSurface

/** Cartão da Biblioteca: "N músicas com tags para arrumar", leva ao Organizar. */
@Composable
internal fun TagsBanner(count: Int, onReview: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val dash = c.outline
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onReview)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(SonivellTheme.icons.edit, null, Modifier.size(18.dp), tint = c.accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                pluralStringResource(R.plurals.lib_tags_banner, count, count),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(R.string.lib_tags_banner_sub), style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(R.string.browse_review), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = c.accent)
        val hide = stringResource(R.string.lib_tags_banner_hide)
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = hide, onClick = onDismiss).semantics { contentDescription = hide },
            contentAlignment = Alignment.Center,
        ) {
            Icon(SonivellTheme.icons.close, null, Modifier.size(14.dp), tint = c.textSecondary)
        }
    }
}

/** Buscas recentes da busca vazia: tocar repete a busca; o X apaga. */
@Composable
internal fun RecentSearches(items: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val c = SonivellTheme.colors
    Column(modifier) {
        Text(
            stringResource(R.string.search_recent),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, letterSpacing = (-0.2).sp),
            fontWeight = FontWeight.ExtraBold,
            color = c.textPrimary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        items.forEach { q ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(q) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(SonivellTheme.icons.search, null, Modifier.size(18.dp), tint = c.textSecondary)
                Text(q, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val remove = stringResource(R.string.cd_remove_recent, q)
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = remove) { onRemove(q) }.semantics { contentDescription = remove },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SonivellTheme.icons.close, null, Modifier.size(14.dp), tint = c.textSecondary)
                }
            }
        }
    }
}

/** "Para o momento": mixes por clima, montados pelo SMART a partir de uma descrição. */
@Composable
internal fun MoodRow(onPick: (Mood) -> Unit, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Mood.entries.forEach { mood ->
            val label = stringResource(mood.label)
            Box(
                Modifier
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(mood.color))
                    .clickable(role = Role.Button, onClickLabel = label) { onPick(mood) }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF2F2EE))
            }
        }
    }
}

/** Resultado da busca na letra: a faixa e a linha, com o trecho buscado destacado. */
@Composable
internal fun LyricHitCard(hit: LyricHit, query: String, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val range = highlightRange(hit.line, query)
    Column(
        modifier
            .fillMaxWidth()
            // Um cartão por resultado da busca nas letras: sem sombra, para a lista rolar leve.
            .cardSurface(RoundedCornerShape(20.dp), elevation = 0.dp)
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlbumArt(hit.track, Modifier.size(44.dp), corner = 12.dp)
            Column(Modifier.weight(1f)) {
                Text(hit.track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(hit.track.artist, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            hit.timeMs?.let { ms ->
                Text("%d:%02d".format(ms / 60_000, (ms / 1000) % 60), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = c.accent)
            }
        }
        Text(
            buildAnnotatedString {
                if (range == null) append(hit.line)
                else {
                    append(hit.line.substring(0, range.first))
                    withStyle(SpanStyle(color = c.onAccent, background = c.accent, fontWeight = FontWeight.ExtraBold)) {
                        append(hit.line.substring(range.first, range.last + 1))
                    }
                    append(hit.line.substring(range.last + 1))
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.textTertiary,
        )
        if (hit.timeMs != null) {
            Text(stringResource(R.string.lyrics_play_from_here), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textSecondary)
        }
    }
}

/** Onde [query] aparece em [line], ignorando maiúsculas e acentos (índices da linha original). */
private fun highlightRange(line: String, query: String): IntRange? {
    fun fold(ch: Char): String =
        java.text.Normalizer.normalize(ch.lowercase(), java.text.Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    val q = query.trim().map(::fold).joinToString("")
    if (q.isEmpty()) return null
    val folded = StringBuilder()
    val origin = ArrayList<Int>()
    line.forEachIndexed { i, ch -> fold(ch).forEach { f -> folded.append(f); origin += i } }
    val at = folded.indexOf(q)
    if (at < 0) return null
    return origin[at]..origin[at + q.length - 1]
}
