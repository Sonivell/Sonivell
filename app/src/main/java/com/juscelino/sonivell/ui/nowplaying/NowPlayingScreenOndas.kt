package com.juscelino.sonivell.ui.nowplaying

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ShuffleMode
import com.juscelino.sonivell.SleepTimer
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.playback.PlayerState
import com.juscelino.sonivell.playback.RepeatMode
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.PlaySquircle
import com.juscelino.sonivell.ui.components.WaveSeekBar
import com.juscelino.sonivell.ui.lyrics.LyricsState
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlinx.coroutines.delay

/** Por que a faixa atual está tocando (aba "Por que tocou"). */
sealed interface PlayReason {
    /** Escolhida pelo SMART por soar parecido com [seed]. */
    data class Smart(val seed: Track) : PlayReason

    /** É a faixa de onde o mix SMART partiu. */
    data object MixStart : PlayReason
    data object Shuffle : PlayReason
    data object Chosen : PlayReason
}

/** Player do layout Ondas (3.0): progresso em onda, botões de modo com nome e painel embaixo. */
@Composable
fun NowPlayingScreenOndas(
    track: Track,
    state: PlayerState,
    shuffleMode: ShuffleMode,
    smartAvailable: Boolean,
    isFavorite: Boolean,
    sourceLabel: String,
    actions: NowPlayingActions,
    sleepTimer: SleepTimer = SleepTimer(),
    lyrics: LyricsState = LyricsState.None,
    lyricsOffsetMs: Long = 0L,
    upNext: List<Track> = emptyList(),
    reason: PlayReason = PlayReason.Chosen,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val durationMs = state.durationMs.takeIf { it > 0 } ?: track.durationMs

    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Topo
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Square(icons.collapse, stringResource(R.string.cd_close_player), actions.onClose)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.playing_from), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textSecondary)
                    Text(sourceLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Square(icons.moreHoriz, stringResource(R.string.cd_more_options), actions.onMore)
            }

            // Capa
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AlbumArt(track, Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f), corner = 28.dp, detailed = true)
            }

            // Título + curtir
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(track.title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val likeLabel = stringResource(if (isFavorite) R.string.cd_unlike else R.string.cd_like)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isFavorite) c.accent else c.surface)
                        .clickable(role = Role.Button, onClickLabel = likeLabel, onClick = actions.onFavorite)
                        .semantics { contentDescription = likeLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (isFavorite) icons.liked else icons.like, null, Modifier.size(22.dp), tint = if (isFavorite) c.onAccent else c.textPrimary)
                }
            }

            // Progresso em onda
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WaveSeekBar(track.id, state.positionMs, durationMs, actions.onSeek)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeLabel(formatDuration(state.positionMs))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        track.formatLabel?.let { fmt ->
                            val shape = RoundedCornerShape(6.dp)
                            Text(
                                fmt,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = c.textSecondary,
                                modifier = Modifier.border(1.dp, c.outline, shape).padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                    TimeLabel(formatDuration(durationMs))
                }
            }

            // Controles
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(icons.skipPrevious, stringResource(R.string.cd_previous), actions.onPrevious, size = 56.dp, iconSize = 30.dp)
                PlaySquircle(state.isPlaying, actions.onTogglePlay, size = 80.dp, corner = 26.dp, iconSize = 30.dp)
                IconBtn(icons.skipNext, stringResource(R.string.cd_next), actions.onNext, size = 56.dp, iconSize = 30.dp)
            }

            // Modos com nome: ligado = preenchido.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (smartAvailable) {
                    ModeChip(
                        icons.smart, stringResource(R.string.source_smart), shuffleMode == ShuffleMode.SMART, Modifier.weight(1f),
                    ) { actions.onSetShuffle(if (shuffleMode == ShuffleMode.SMART) ShuffleMode.OFF else ShuffleMode.SMART) }
                }
                ModeChip(
                    icons.shuffle, stringResource(R.string.mode_shuffle), shuffleMode == ShuffleMode.ON, Modifier.weight(1f),
                ) { actions.onSetShuffle(if (shuffleMode == ShuffleMode.ON) ShuffleMode.OFF else ShuffleMode.ON) }
                ModeChip(
                    if (state.repeat == RepeatMode.ONE) icons.repeatOne else icons.repeat,
                    stringResource(if (state.repeat == RepeatMode.ONE) R.string.mode_repeat_one else R.string.mode_repeat),
                    state.repeat != RepeatMode.OFF,
                    Modifier.weight(1f),
                    onClick = actions.onRepeat,
                )
            }
        }

        BottomPanel(track, state, lyrics, lyricsOffsetMs, upNext, reason, sourceLabel, sleepTimer, actions)
    }
}

@Composable
private fun Square(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(44.dp).clip(shape).border(1.dp, c.outline, shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(22.dp), tint = c.textPrimary) }
}

@Composable
private fun TimeLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SonivellTheme.colors.textSecondary)
}

@Composable
private fun ModeChip(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .height(42.dp)
            .clip(shape)
            .then(if (on) Modifier.background(c.accent) else Modifier.border(1.dp, c.outline, shape))
            .toggleable(value = on, role = Role.Switch, onValueChange = { onClick() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (on) c.onAccent else c.textTertiary)
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = if (on) c.onAccent else c.textTertiary, maxLines = 1)
    }
}

/** Painel de baixo: Letra, A seguir e Por que tocou, mais o temporizador. */
@Composable
private fun BottomPanel(
    track: Track,
    state: PlayerState,
    lyrics: LyricsState,
    lyricsOffsetMs: Long,
    upNext: List<Track>,
    reason: PlayReason,
    sourceLabel: String,
    sleepTimer: SleepTimer,
    actions: NowPlayingActions,
) {
    val c = SonivellTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Começa minimizado: só a alça e as abas. Tocar numa aba (ou arrastar para cima) abre;
    // tocar na aba aberta, na alça ou arrastar para baixo fecha.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val toggleLabel = stringResource(if (expanded) R.string.cd_collapse_panel else R.string.cd_expand_panel)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(c.surface)
            .pointerInput(Unit) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { _, dy -> drag += dy },
                    onDragEnd = {
                        if (drag < -24f) expanded = true
                        if (drag > 24f) expanded = false
                    },
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .animateContentSize(tween(220))
            .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = if (expanded) 16.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 64.dp, height = 20.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = toggleLabel) { expanded = !expanded }
                .semantics { contentDescription = toggleLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.outline))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(R.string.lyrics_label, R.string.tab_up_next, R.string.tab_why).forEachIndexed { i, label ->
                    val on = expanded && tab == i
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) c.surfaceHigh else Color.Transparent)
                            .clickable(role = Role.Tab) {
                                if (on) expanded = false else {
                                    tab = i
                                    expanded = true
                                }
                            }
                            .semantics { selected = on }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (on) c.textPrimary else c.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            SleepButton(sleepTimer, actions.onSleepTimer)
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(top = 4.dp, end = 8.dp)) {
                when (tab) {
                    0 -> LyricsPeek(state, lyrics, lyricsOffsetMs, actions.onLyrics)
                    1 -> UpNextPeek(upNext, actions.onQueue)
                    else -> WhyPeek(reason, sourceLabel)
                }
            }
        }
    }
}

@Composable
private fun LyricsPeek(state: PlayerState, lyrics: LyricsState, offsetMs: Long, onOpen: () -> Unit) {
    val c = SonivellTheme.colors
    val loaded = (lyrics as? LyricsState.Loaded)?.lyrics?.takeIf { it.lines.isNotEmpty() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = stringResource(R.string.open_full_lyrics), onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (loaded == null) {
            Text(
                stringResource(if (lyrics is LyricsState.Loading) R.string.lyrics_label else R.string.lyrics_none),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary,
            )
            Text(stringResource(R.string.open_full_lyrics), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.accent)
        } else {
            val index = loaded.lineAt(state.positionMs - offsetMs)
            val first = if (loaded.synced) index.coerceAtLeast(0) else 0
            val current = loaded.lines.getOrNull(first)?.text.orEmpty()
            val next = loaded.lines.getOrNull(first + 1)?.text.orEmpty()
            Text(
                current.ifBlank { "♪" },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp),
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (next.isNotBlank()) {
                Text(next, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun UpNextPeek(upNext: List<Track>, onOpenQueue: () -> Unit) {
    val c = SonivellTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (upNext.isEmpty()) {
            Text(stringResource(R.string.up_next_empty), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        upNext.take(2).forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AlbumArt(t, Modifier.size(32.dp), corner = 9.dp)
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(
            stringResource(R.string.open_full_queue),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            color = c.accent,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onOpenQueue).padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun WhyPeek(reason: PlayReason, sourceLabel: String) {
    val c = SonivellTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(
                when (reason) {
                    is PlayReason.Smart, PlayReason.MixStart -> SonivellTheme.icons.smart
                    PlayReason.Shuffle -> SonivellTheme.icons.shuffle
                    PlayReason.Chosen -> SonivellTheme.icons.musicNote
                },
                null,
                Modifier.size(18.dp),
                tint = c.accent,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when (reason) {
                    is PlayReason.Smart -> stringResource(R.string.why_smart, reason.seed.title, reason.seed.artist)
                    PlayReason.MixStart -> stringResource(R.string.why_seed)
                    PlayReason.Shuffle -> stringResource(R.string.why_shuffle, sourceLabel)
                    PlayReason.Chosen -> stringResource(R.string.why_source, sourceLabel)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary,
            )
            if (reason is PlayReason.Smart) {
                Text(stringResource(R.string.why_smart_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
    }
}

/** Temporizador: lua; ligado, mostra a contagem regressiva na cor de destaque. */
@Composable
private fun SleepButton(timer: SleepTimer, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    if (!timer.active) {
        IconBtn(SonivellTheme.icons.sleep, stringResource(R.string.cd_sleep_timer), onClick, tint = c.textSecondary, iconSize = 19.dp)
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val label = when {
        timer.endOfTrack -> stringResource(R.string.timer_end_of_track_short)
        timer.endsAtMs != null -> formatDuration((timer.endsAtMs - now).coerceAtLeast(0))
        else -> ""
    }
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = stringResource(R.string.sleep_timer), onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(SonivellTheme.icons.sleep, null, Modifier.size(16.dp), tint = c.accent)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = c.accent)
    }
}
