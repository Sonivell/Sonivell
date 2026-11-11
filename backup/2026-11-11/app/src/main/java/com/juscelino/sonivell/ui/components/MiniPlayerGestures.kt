package com.juscelino.sonivell.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Gestos do mini player: arrastar para a esquerda passa para a próxima, para a direita volta,
 * para cima abre o player. O mini player acompanha o dedo e volta para o lugar ao soltar.
 * Leitores de tela ganham as mesmas ações como ações personalizadas.
 */
@Composable
fun Modifier.miniPlayerGestures(onNext: () -> Unit, onPrevious: () -> Unit, onOpen: () -> Unit): Modifier {
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val open by rememberUpdatedState(onOpen)
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val nextLabel = stringResource(R.string.cd_next)
    val previousLabel = stringResource(R.string.cd_previous)
    return this
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(nextLabel) { next(); true },
                CustomAccessibilityAction(previousLabel) { previous(); true },
            )
        }
        .offset { IntOffset(offset.value.roundToInt(), 0) }
        .pointerInput(Unit) {
            var vertical = 0f
            detectDragGestures(
                onDragStart = { vertical = 0f },
                onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                onDragEnd = {
                    val dx = offset.value
                    val width = size.width.toFloat()
                    scope.launch {
                        when {
                            vertical < -threshold && abs(vertical) > abs(dx) -> {
                                offset.animateTo(0f, spring())
                                open()
                            }
                            dx <= -threshold -> {
                                offset.animateTo(-width, tween(140))
                                next()
                                offset.snapTo(width * 0.35f)
                                offset.animateTo(0f, spring())
                            }
                            dx >= threshold -> {
                                offset.animateTo(width, tween(140))
                                previous()
                                offset.snapTo(-width * 0.35f)
                                offset.animateTo(0f, spring())
                            }
                            else -> offset.animateTo(0f, spring())
                        }
                    }
                },
            ) { change, drag ->
                change.consume()
                vertical += drag.y
                // Na vertical o mini player fica parado: só conta o gesto de abrir.
                scope.launch { offset.snapTo(offset.value + drag.x) }
            }
        }
}
