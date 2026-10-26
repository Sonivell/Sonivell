package com.juscelino.sonivell.ui.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Lyrics
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.util.Locale
import kotlin.math.abs

/** Estado da letra da faixa atual. */
sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data class Loaded(val lyrics: Lyrics) : LyricsState
}

data class LyricsActions(
    val onClose: () -> Unit,
    val onSeek: (Long) -> Unit,
    /** Muda o ajuste de sincronia em [deltaMs]. */
    val onOffset: (deltaMs: Long) -> Unit,
    val onPickFolder: () -> Unit,
)

@Composable
fun LyricsScreen(
    track: Track,
    state: LyricsState,
    positionMs: Long,
    offsetMs: Long,
    hasFolder: Boolean,
    actions: LyricsActions,
) {
    val c = SonivellTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.background).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBtn(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.cd_close_lyrics), actions.onClose, background = c.surface)
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(Icons.Rounded.FolderOpen, stringResource(R.string.lyrics_pick_folder), actions.onPickFolder, background = c.surface, iconSize = 20.dp)
        }

        when (state) {
            LyricsState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
            LyricsState.None -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.lyrics_none), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(
                    stringResource(if (hasFolder) R.string.lyrics_none_hint_folder else R.string.lyrics_none_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                )
            }
            is LyricsState.Loaded -> {
                val lyrics = state.lyrics
                if (lyrics.synced) {
                    SyncedLyrics(lyrics, positionMs - offsetMs, Modifier.weight(1f)) { lineTime ->
                        actions.onSeek((lineTime + offsetMs).coerceAtLeast(0))
                    }
                    OffsetBar(offsetMs, actions.onOffset)
                } else {
                    PlainLyrics(lyrics, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics, positionMs: Long, modifier: Modifier, onLine: (Long) -> Unit) {
    val c = SonivellTheme.colors
    val list = rememberLazyListState()
    val current = lyrics.lineAt(positionMs)
    val topGap = with(LocalDensity.current) { 160.dp.roundToPx() }
    // Mantém a linha atual perto do terço superior da tela.
    LaunchedEffect(current) {
        if (current >= 0) list.animateScrollToItem(current, scrollOffset = -topGap)
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = list,
        contentPadding = PaddingValues(top = 24.dp, bottom = 240.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val active = index == current
            val color by animateColorAsState(
                when {
                    active -> c.textPrimary
                    index < current -> c.textSecondary.copy(alpha = 0.6f)
                    else -> c.textSecondary
                },
                label = "lyricColor",
            )
            Text(
                line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = if (active) 26.sp else 22.sp,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                ),
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onLine(line.timeMs) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PlainLyrics(lyrics: Lyrics, modifier: Modifier) {
    val c = SonivellTheme.colors
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text(
                lyrics.lines.joinToString("\n") { it.text },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 30.sp),
                color = c.textPrimary,
            )
        }
        item {
            Text(
                stringResource(R.string.lyrics_unsynced_note),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

/** Ajuste manual: letra adiantada ou atrasada em passos de meio segundo. */
@Composable
private fun OffsetBar(offsetMs: Long, onOffset: (Long) -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.Rounded.Remove, stringResource(R.string.lyrics_earlier), { onOffset(-500) }, tint = c.textPrimary)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.lyrics_sync), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            val seconds = String.format(Locale.getDefault(), "%+.1f s", offsetMs / 1000f)
            Text(
                if (offsetMs == 0L) stringResource(R.string.lyrics_sync_none) else seconds,
                style = MaterialTheme.typography.titleSmall,
                color = if (abs(offsetMs) > 0) c.accent else c.textPrimary,
                modifier = Modifier.clickable { if (offsetMs != 0L) onOffset(-offsetMs) },
            )
        }
        IconBtn(Icons.Rounded.Add, stringResource(R.string.lyrics_later), { onOffset(500) }, tint = c.textPrimary)
    }
}
