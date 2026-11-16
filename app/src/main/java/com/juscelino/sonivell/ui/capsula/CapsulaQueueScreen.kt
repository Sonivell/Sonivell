package com.juscelino.sonivell.ui.capsula

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.ArtBlur
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.screenBackground
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class CapsulaQueueActions(
    val onClose: () -> Unit,
    val onPlayAt: (Int) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    /** Tira da fila a faixa no índice real [Int]. */
    val onRemove: (Int) -> Unit,
    /** Devolve a faixa ao índice real em que estava (o "Desfazer"). */
    val onRestore: (Int, Track) -> Unit,
    val onClear: () -> Unit,
    /** Devolve ao fim da fila o que "Limpar fila" tirou. */
    val onRestoreAll: (List<Track>) -> Unit,
    val onSavePlaylist: () -> Unit,
    /** Põe uma sugestão no fim da fila. */
    val onAddSuggestion: (Track) -> Unit,
    val onItemMenu: (Track, Int) -> Unit,
)

/**
 * Fila do Cápsula: Atual e Sugeridas numa faixa em cápsula, a faixa tocando com a capa desfocada,
 * cada faixa com "tirar da fila" e a alça de arrastar, e os botões numa pílula embaixo. Tirar uma
 * faixa (ou limpar a fila) deixa alguns segundos para desfazer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CapsulaQueueScreen(
    current: Track?,
    upNext: List<IndexedValue<Track>>,
    suggestions: List<Track>,
    smartAvailable: Boolean,
    actions: CapsulaQueueActions,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var removed by remember { mutableStateOf<Pair<Int, Track>?>(null) }
    var cleared by remember { mutableStateOf<List<Track>?>(null) }
    var added by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val rowHeight = 64.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(removed, cleared) {
        if (removed != null || cleared != null) {
            delay(5_000L)
            removed = null
            cleared = null
        }
    }

    Box(Modifier.fillMaxSize().screenBackground()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CapsulaHeader(
                stringResource(R.string.queue_title),
                onBack = actions.onClose,
                backIcon = icons.collapse,
                backLabel = stringResource(R.string.cd_back_to_player),
            )
            SegmentedPill(
                listOf(stringResource(R.string.cap_queue_current), stringResource(R.string.cap_queue_suggested)),
                tab,
                { tab = it },
            )

            if (current != null) NowPlayingPill(current)

            if (tab == 0) {
                val total = upNext.sumOf { it.value.durationMs }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.queue_summary, pluralStringResource(R.plurals.queue_tracks, upNext.size, upNext.size), (total / 60_000).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (upNext.size > 1) Text(stringResource(R.string.cap_drag_hint), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
                if (upNext.isEmpty()) {
                    Text(stringResource(R.string.queue_empty), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp).weight(1f))
                } else {
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
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        items(keyed, key = { it.first }) { (itemKey, iv) ->
                            val (queueIndex, track) = iv
                            val latestIndex by rememberUpdatedState(queueIndex)
                            val isDragging = dragging == queueIndex
                            val removeLabel = stringResource(R.string.cap_remove_from_queue, track.title)
                            val dragLabel = stringResource(R.string.cd_drag_reorder, track.title)
                            Row(
                                Modifier
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .offset { IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0) }
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDragging) c.surfaceHigh else Color.Transparent)
                                    .combinedClickable(
                                        onClick = { actions.onPlayAt(queueIndex) },
                                        onLongClick = { actions.onItemMenu(track, queueIndex) },
                                    )
                                    .padding(start = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AlbumArt(track, Modifier.size(46.dp), corner = 12.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${track.artist} · ${formatDuration(track.durationMs)}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .clickable(role = Role.Button, onClickLabel = removeLabel) {
                                            actions.onRemove(queueIndex)
                                            cleared = null
                                            removed = queueIndex to track
                                        }
                                        .semantics { contentDescription = removeLabel },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(icons.remove, null, Modifier.size(20.dp), tint = c.textSecondary) }
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
                                ) { Icon(icons.dragHandle, null, tint = c.textSecondary) }
                            }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(if (smartAvailable) R.string.cap_suggested_smart else R.string.cap_suggested_lite),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                if (suggestions.isEmpty()) {
                    Text(stringResource(R.string.cap_suggested_empty), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp).weight(1f))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        items(suggestions, key = { "s" + it.id }) { track ->
                            val isAdded = track.id in added
                            val addLabel = stringResource(R.string.cap_add_to_queue, track.title)
                            Row(
                                Modifier.fillMaxWidth().height(rowHeight).padding(start = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AlbumArt(track, Modifier.size(46.dp), corner = 12.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(if (isAdded) c.accent.copy(alpha = 0.16f) else c.textPrimary.copy(alpha = 0.07f))
                                        .clickable(enabled = !isAdded, role = Role.Button, onClickLabel = addLabel) {
                                            actions.onAddSuggestion(track)
                                            added = added + track.id
                                        }
                                        .semantics { contentDescription = addLabel },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(if (isAdded) icons.check else icons.add, null, Modifier.size(20.dp), tint = if (isAdded) c.accent else c.textPrimary) }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().capsulaGlass(Pill, elevation = 10.dp, alpha = 0.8f).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PillButton(
                    stringResource(R.string.queue_clear),
                    icons.delete,
                    {
                        val tracks = upNext.map { it.value }
                        actions.onClear()
                        removed = null
                        cleared = tracks
                    },
                    Modifier.weight(1f),
                    enabled = upNext.isNotEmpty(),
                )
                PillButton(stringResource(R.string.save_playlist), icons.add, actions.onSavePlaylist, Modifier.weight(1f), filled = true)
            }
        }

        val undoText = when {
            removed != null -> stringResource(R.string.cap_left_queue, removed!!.second.title)
            cleared != null -> stringResource(R.string.cap_queue_cleared)
            else -> null
        }
        if (undoText != null) {
            UndoPill(
                undoText,
                onUndo = {
                    removed?.let { (i, t) -> actions.onRestore(i, t) }
                    cleared?.let { actions.onRestoreAll(it) }
                    removed = null
                    cleared = null
                },
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
            )
        }
    }
}

/** A faixa tocando: capa desfocada de fundo, o nome e as barras do "tocando agora". */
@Composable
private fun NowPlayingPill(track: Track) {
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth().clip(shape).border(1.dp, Color.White.copy(alpha = 0.12f), shape)) {
        ArtBlur(track, Modifier.matchParentSize(), fallback = Color(0xFF1F2825))
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.40f)))
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AlbumArt(track, Modifier.size(52.dp), corner = 12.dp)
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist + " · " + stringResource(R.string.now_playing).lowercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(SonivellTheme.icons.nowPlaying, null, Modifier.padding(end = 6.dp).size(22.dp), tint = Color.White)
        }
    }
}
