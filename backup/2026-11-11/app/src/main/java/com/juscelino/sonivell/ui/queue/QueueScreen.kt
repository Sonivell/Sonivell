package com.juscelino.sonivell.ui.queue

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.juscelino.sonivell.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.SectionLabel
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlin.math.roundToInt
import com.juscelino.sonivell.ui.theme.cardSurface

data class QueueActions(
    val onClose: () -> Unit,
    val onClear: () -> Unit,
    val onPlayAt: (Int) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onShuffle: () -> Unit,
    val onSavePlaylist: () -> Unit,
    /** Menu de uma faixa da fila (remover, tocar a seguir…): índice real na fila. */
    val onItemMenu: (track: Track, queueIndex: Int) -> Unit = { _, _ -> },
)

/** [upNext]: faixas seguintes com o índice real delas na fila. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    current: Track?,
    upNext: List<IndexedValue<Track>>,
    shuffle: Boolean,
    actions: QueueActions,
) {
    val c = SonivellTheme.colors
    val rowHeight = 64.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    var dragging by remember { mutableStateOf<Int?>(null) } // índice real na fila
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(SonivellTheme.icons.collapse, stringResource(R.string.cd_back_to_player), actions.onClose, background = c.surface)
            Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(enabled = upNext.isNotEmpty(), onClick = actions.onClear)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.queue_clear), style = MaterialTheme.typography.labelLarge, color = if (upNext.isNotEmpty()) c.accent else c.textSecondary)
            }
        }

        if (current != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.now_playing))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .cardSurface(RoundedCornerShape(18.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(52.dp)) {
                        AlbumArt(current, Modifier.fillMaxSize(), corner = 12.dp)
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(c.background.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(SonivellTheme.icons.nowPlaying, null, tint = c.accent) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(current.title, style = MaterialTheme.typography.titleMedium, color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current.artist, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatDuration(current.durationMs), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.up_next), Modifier.weight(1f))
                val total = upNext.sumOf { it.value.durationMs }
                Text(
                    stringResource(R.string.queue_summary, pluralStringResource(R.plurals.queue_tracks, upNext.size, upNext.size), (total / 60_000).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                )
            }
            if (upNext.isEmpty()) {
                Text(
                    stringResource(R.string.queue_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            // Chave estável por faixa (com contador para repetidas), para o arrasto não reiniciar a cada troca.
            val keyed = remember(upNext) {
                val seen = HashMap<Long, Int>()
                upNext.map { iv ->
                    val n = (seen[iv.value.id] ?: 0) + 1
                    seen[iv.value.id] = n
                    "${iv.value.id}#$n" to iv
                }
            }
            val first by rememberUpdatedState(upNext.firstOrNull()?.index ?: 0)
            val last by rememberUpdatedState(upNext.lastOrNull()?.index ?: 0)
            LazyColumn(Modifier.fillMaxWidth()) {
                items(keyed, key = { it.first }) { (itemKey, iv) ->
                    val (queueIndex, track) = iv
                    val latestIndex by rememberUpdatedState(queueIndex)
                    val isDragging = dragging == queueIndex
                    val dragLabel = stringResource(R.string.cd_drag_reorder, track.title)
                    Row(
                        Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset { IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0) }
                            .fillMaxWidth()
                            .height(rowHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDragging) c.surface else c.background)
                            .combinedClickable(
                                onClick = { actions.onPlayAt(queueIndex) },
                                onLongClick = { actions.onItemMenu(track, queueIndex) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AlbumArt(track, Modifier.size(44.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${track.artist} · ${formatDuration(track.durationMs)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .semantics { contentDescription = dragLabel }
                                .pointerInput(itemKey) {
                                    detectVerticalDragGestures(
                                        onDragStart = { dragging = latestIndex; dragOffset = 0f },
                                        onVerticalDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            val from = dragging ?: return@detectVerticalDragGestures
                                            if (dragOffset > rowPx * 0.6f && from < last) {
                                                actions.onMove(from, from + 1)
                                                dragging = from + 1
                                                dragOffset -= rowPx
                                            } else if (dragOffset < -rowPx * 0.6f && from > first) {
                                                actions.onMove(from, from - 1)
                                                dragging = from - 1
                                                dragOffset += rowPx
                                            }
                                        },
                                        onDragEnd = { dragging = null; dragOffset = 0f },
                                        onDragCancel = { dragging = null; dragOffset = 0f },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(SonivellTheme.icons.dragHandle, null, tint = c.textSecondary)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideButton(
                stringResource(R.string.shuffle), SonivellTheme.icons.shuffle, actions.onShuffle, filled = false,
                active = shuffle, modifier = Modifier.weight(1f),
            )
            WideButton(stringResource(R.string.save_playlist), SonivellTheme.icons.add, actions.onSavePlaylist, filled = true, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WideButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .height(52.dp)
            .clip(shape)
            .then(
                if (filled) Modifier.background(c.accent)
                else Modifier.border(1.dp, if (active) c.accent else c.outline, shape)
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = when {
            filled -> c.onAccent
            active -> c.accent
            else -> c.textPrimary
        }
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint, modifier = Modifier.padding(start = 8.dp))
    }
}
