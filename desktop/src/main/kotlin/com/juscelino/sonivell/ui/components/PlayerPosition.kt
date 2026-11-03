package com.juscelino.sonivell.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import com.juscelino.sonivell.playback.PlayerState

/**
 * Posição ao vivo do player (ver `PlayerConnection.position`). Fica fora do [PlayerState] para que só
 * quem mostra o progresso se redesenhe a cada atualização, e não o app inteiro.
 */
val LocalPlayerPosition = staticCompositionLocalOf<State<Long>?> { null }

/** Posição atual da faixa: a ao vivo quando há uma, senão a do último estado publicado. */
val PlayerState.livePositionMs: Long
    @Composable @ReadOnlyComposable get() = LocalPlayerPosition.current?.value ?: positionMs
