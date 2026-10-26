package com.juscelino.sonivell.ui.nowplaying

/** Ações do player, as mesmas nos três layouts. */
data class NowPlayingActions(
    val onClose: () -> Unit,
    val onMore: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onFavorite: () -> Unit,
    val onLyrics: () -> Unit,
    val onQueue: () -> Unit,
    val onSleepTimer: () -> Unit = {},
    /** Layout Ondas: SMART e aleatório em botões separados. */
    val onSetShuffle: (com.juscelino.sonivell.ShuffleMode) -> Unit = {},
)
