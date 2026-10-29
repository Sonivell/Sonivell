package com.juscelino.sonivell.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlinx.coroutines.launch

/**
 * Índice A–Z na lateral de uma lista longa: tocar ou arrastar sobre as letras pula para a
 * primeira linha daquela letra, com um balão grande mostrando a letra atual.
 *
 * [letters] traz a letra de cada linha do fim da lista (as linhas da lista ficam depois do
 * cabeçalho, filtros etc.), na ordem em que aparecem.
 */
@Composable
fun FastScroller(listState: LazyListState, letters: List<Char>, modifier: Modifier = Modifier) {
    if (letters.size < MIN_ROWS) return
    val c = SonivellTheme.colors
    val index = remember(letters) {
        val first = LinkedHashMap<Char, Int>()
        letters.forEachIndexed { i, l -> if (l !in first) first[l] = i }
        first
    }
    val shown = remember(index) { index.keys.toList() }
    if (shown.size < 3) return
    var active by remember { mutableStateOf<Char?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun jump(letter: Char) {
        if (letter == active) return
        active = letter
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val offsetInList = index[letter] ?: return
        val firstRow = (listState.layoutInfo.totalItemsCount - letters.size).coerceAtLeast(0)
        scope.launch { listState.scrollToItem(firstRow + offsetInList) }
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val current = active
        if (current != null) {
            Box(
                Modifier.padding(end = 8.dp).size(64.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(current.toString(), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = c.onAccent)
            }
        }
        Column(
            Modifier
                .fillMaxHeight()
                .width(24.dp)
                .clearAndSetSemantics { }
                .pointerInput(shown) {
                    fun pick(y: Float) {
                        val i = (y / size.height * shown.size).toInt().coerceIn(0, shown.lastIndex)
                        jump(shown[i])
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pick(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()
                            pick(change.position.y)
                        }
                        active = null
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            shown.forEach { l ->
                Text(
                    l.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (l == active) c.accent else c.textSecondary,
                )
            }
        }
    }
}

/** Abaixo disso a lista cabe em poucas rolagens e o índice só atrapalha. */
private const val MIN_ROWS = 40
