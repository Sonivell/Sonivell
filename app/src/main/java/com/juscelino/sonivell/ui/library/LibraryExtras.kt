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
import com.juscelino.sonivell.ui.theme.SonivellTheme

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
