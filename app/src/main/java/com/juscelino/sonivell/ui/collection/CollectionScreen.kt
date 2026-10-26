package com.juscelino.sonivell.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.TrackRow
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlin.math.roundToInt

data class CollectionActions(
    val onBack: () -> Unit,
    val onPlay: (index: Int) -> Unit,
    val onShuffle: () -> Unit,
    val onTrackMenu: (track: Track, index: Int) -> Unit,
    /** Menu da coleção (ex.: renomear/exportar/excluir playlist); null esconde o botão. */
    val onMore: (() -> Unit)? = null,
    /** Reordenar faixas (só playlists); null desliga o arrasto. */
    val onMove: ((from: Int, to: Int) -> Unit)? = null,
)

/**
 * Tela de uma coleção de faixas: álbum, artista, gênero, pasta ou playlist.
 * Cabeçalho com capa, nome e total; botões Tocar e Aleatório; a lista de faixas.
 */
@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    currentId: Long?,
    contentPadding: PaddingValues,
    actions: CollectionActions,
    emptyText: String? = null,
) {
    val c = SonivellTheme.colors
    val rowHeight = 72.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val lastIndex by rememberUpdatedState(tracks.lastIndex)
    // Chave estável por faixa (com contador para repetidas): o arrasto não reinicia a cada troca.
    val keyed = remember(tracks) {
        val seen = HashMap<Long, Int>()
        tracks.map { t ->
            val n = (seen[t.id] ?: 0) + 1
            seen[t.id] = n
            "${t.id}#$n" to t
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(SonivellTheme.icons.back, stringResource(R.string.cd_back), actions.onBack, background = c.surface)
                Box(Modifier.weight(1f))
                actions.onMore?.let {
                    IconBtn(SonivellTheme.icons.more, stringResource(R.string.cd_more_options), it, background = c.surface)
                }
            }
        }
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AlbumArt(tracks.firstOrNull(), Modifier.size(120.dp), corner = 20.dp, detailed = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val total = tracks.sumOf { it.durationMs }
                    Text(
                        pluralStringResource(R.plurals.songs_count, tracks.size, tracks.size) + " · " + formatDuration(total),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary,
                    )
                }
            }
        }
        item(key = "buttons") {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.cd_play), SonivellTheme.icons.play, filled = true, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    actions.onPlay(0)
                }
                PillButton(stringResource(R.string.shuffle), SonivellTheme.icons.shuffle, filled = false, enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f), onClick = actions.onShuffle)
            }
        }
        if (tracks.isEmpty() && emptyText != null) {
            item(key = "empty") {
                Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(vertical = 24.dp))
            }
        }
        itemsIndexed(keyed, key = { _, k -> k.first }) { index, (itemKey, track) ->
            val latestIndex by rememberUpdatedState(index)
            val isDragging = dragging == index
            val reorder = actions.onMove
            Box(
                Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset { IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0) }
                    .height(rowHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDragging) c.surface else c.background),
                contentAlignment = Alignment.Center,
            ) {
                TrackRow(
                    track,
                    onClick = { actions.onPlay(index) },
                    highlighted = track.id == currentId,
                    onMore = { actions.onTrackMenu(track, index) },
                    trailing = if (reorder == null) null else {
                        {
                            val dragLabel = stringResource(R.string.cd_drag_reorder, track.title)
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .semantics { contentDescription = dragLabel }
                                    .pointerInput(itemKey) {
                                        detectVerticalDragGestures(
                                            onDragStart = { dragging = latestIndex; dragOffset = 0f },
                                            onVerticalDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount
                                                val from = dragging ?: return@detectVerticalDragGestures
                                                if (dragOffset > rowPx * 0.6f && from < lastIndex) {
                                                    reorder(from, from + 1); dragging = from + 1; dragOffset -= rowPx
                                                } else if (dragOffset < -rowPx * 0.6f && from > 0) {
                                                    reorder(from, from - 1); dragging = from - 1; dragOffset += rowPx
                                                }
                                            },
                                            onDragEnd = { dragging = null; dragOffset = 0f },
                                            onDragCancel = { dragging = null; dragOffset = 0f },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(SonivellTheme.icons.dragHandle, null, tint = c.textSecondary) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    icon: ImageVector,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(if (enabled) c.accent else c.surfaceHigh) else Modifier.border(1.dp, c.outline, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (filled) c.onAccent else c.textPrimary
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint, modifier = Modifier.padding(start = 8.dp))
    }
}
