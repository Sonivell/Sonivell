package com.juscelino.sonivell.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.DuplicateGroup
import com.juscelino.sonivell.data.MetadataIssues
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.Chip
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.SectionLabel
import com.juscelino.sonivell.ui.components.TrackRow
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.util.Locale
import com.juscelino.sonivell.ui.theme.cardSurface

data class OrganizeActions(
    val onBack: () -> Unit,
    /** Apaga as outras cópias do grupo (o Android pede confirmação). */
    val onDeleteCopies: (List<Track>) -> Unit,
    val onNotDuplicate: (DuplicateGroup) -> Unit,
    val onPlay: (Track) -> Unit,
    val onResetIgnored: () -> Unit,
)

@Composable
fun OrganizeScreen(
    duplicates: List<DuplicateGroup>,
    issues: MetadataIssues,
    canDelete: Boolean,
    contentPadding: PaddingValues,
    actions: OrganizeActions,
) {
    val c = SonivellTheme.colors
    var tab by rememberSaveable { mutableStateOf(0) }
    var openIssue by rememberSaveable { mutableStateOf(-1) }
    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBtn(SonivellTheme.icons.back, stringResource(R.string.cd_back), actions.onBack, background = c.surface)
                Text(stringResource(R.string.organize), style = MaterialTheme.typography.displaySmall, color = c.textPrimary)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.duplicates) + " · ${duplicates.size}", selected = tab == 0) { tab = 0 }
                Chip(stringResource(R.string.missing_tags) + " · ${issues.total}", selected = tab == 1) { tab = 1 }
            }
        }
        if (tab == 0) {
            item {
                Text(stringResource(R.string.duplicates_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            if (duplicates.isEmpty()) {
                item { Text(stringResource(R.string.duplicates_none), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary) }
            }
            items(duplicates, key = { it.key + it.tracks.first().id }) { g -> DuplicateCard(g, canDelete, actions) }
            item {
                Text(
                    stringResource(R.string.duplicates_reset),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = actions.onResetIgnored).padding(vertical = 8.dp),
                )
            }
        } else {
            item { Text(stringResource(R.string.missing_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
            val groups = listOf(
                R.string.missing_title to issues.noTitle,
                R.string.missing_artist to issues.noArtist,
                R.string.missing_album to issues.noAlbum,
                R.string.missing_genre to issues.noGenre,
                R.string.missing_year to issues.noYear,
            )
            groups.forEachIndexed { i, (label, tracks) ->
                item(key = "issue-$i") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .cardSurface(RoundedCornerShape(14.dp))
                            .clickable(enabled = tracks.isNotEmpty()) { openIssue = if (openIssue == i) -1 else i }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text(pluralStringResource(R.plurals.songs_count, tracks.size, tracks.size), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    }
                }
                if (openIssue == i) {
                    items(tracks.take(100), key = { "issue-$i-${it.id}" }) { t ->
                        TrackRow(t, onClick = { actions.onPlay(t) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateCard(g: DuplicateGroup, canDelete: Boolean, actions: OrganizeActions) {
    val c = SonivellTheme.colors
    Column(
        Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(g.keep.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(g.keep.artist, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        g.tracks.forEach { t ->
            val keep = t.id == g.keep.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(if (keep) Modifier.border(1.dp, c.accent, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { actions.onPlay(t) }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AlbumArt(t, Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    val size = t.sizeBytes?.let { String.format(Locale.getDefault(), "%.1f MB", it / 1_048_576f) }
                    Text(
                        listOfNotNull(t.formatLabel, size, formatDuration(t.durationMs)).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textPrimary,
                    )
                    Text(t.folder.ifBlank { t.fileName.orEmpty() }, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (keep) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.accent).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(stringResource(R.string.keep), style = MaterialTheme.typography.labelMedium, color = c.onAccent)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canDelete) {
                Chip(stringResource(R.string.delete_others), selected = true) { actions.onDeleteCopies(g.tracks.filter { it.id != g.keep.id }) }
            }
            Chip(stringResource(R.string.not_duplicates), selected = false) { actions.onNotDuplicate(g) }
        }
    }
}
