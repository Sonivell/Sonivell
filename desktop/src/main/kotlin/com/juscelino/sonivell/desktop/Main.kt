package com.juscelino.sonivell.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.juscelino.sonivell.desktop.ui.SonivellApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.awt.Dimension

/** Tamanho da janela no modo compacto (só capa e controles). */
private val COMPACT_SIZE = DpSize(400.dp, 500.dp)
private val FULL_SIZE = DpSize(1360.dp, 860.dp)

fun main() = application {
    val controller = remember { Controller() }
    val settings by controller.settings.collectAsState()
    val compact = settings.compact
    val state = rememberWindowState(
        size = if (controller.settings.value.compact) COMPACT_SIZE else FULL_SIZE,
        position = controller.settings.value.let { s ->
            if (s.compact && s.compactX != null && s.compactY != null) WindowPosition(s.compactX.dp, s.compactY.dp)
            else WindowPosition.PlatformDefault
        },
    )
    // Tamanho do player completo antes de entrar no modo compacto, para voltar igual.
    var fullSize by remember { mutableStateOf(FULL_SIZE) }
    val icon = remember { runCatching { BitmapPainter(useResource("icon.png", ::loadImageBitmap)) }.getOrNull() }
    Window(
        onCloseRequest = { controller.shutdown(); exitApplication() },
        title = str("app_name"),
        state = state,
        icon = icon,
        resizable = !compact,
        onKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) false
            else when {
                e.key == Key.Spacebar -> { controller.togglePlay(); true }
                e.isCtrlPressed && e.key == Key.M -> { controller.toggleCompact(); true }
                e.isCtrlPressed && e.key == Key.DirectionRight -> { controller.next(); true }
                e.isCtrlPressed && e.key == Key.DirectionLeft -> { controller.previous(); true }
                else -> false
            }
        },
    ) {
        LaunchedEffect(compact) {
            if (compact) {
                // O modo compacto só existe com a janela solta (não maximizada).
                if (state.placement != WindowPlacement.Floating) state.placement = WindowPlacement.Floating
                window.minimumSize = Dimension(360, 440)
                state.size = COMPACT_SIZE
                val s = controller.settings.value
                if (s.compactX != null && s.compactY != null) state.position = WindowPosition(s.compactX.dp, s.compactY.dp)
                // Lembra onde a janela compacta ficou.
                snapshotFlow { state.position }.collectLatest { pos ->
                    if (pos is WindowPosition.Absolute) {
                        delay(600)
                        controller.saveCompactPosition(pos.x.value.toInt(), pos.y.value.toInt())
                    }
                }
            } else {
                if (state.size == COMPACT_SIZE) state.size = fullSize
                window.minimumSize = Dimension(980, 640)
                snapshotFlow { state.size to state.placement }.collectLatest { (size, placement) ->
                    if (placement == WindowPlacement.Floating && size.width >= 980.dp) fullSize = size
                }
            }
        }
        // Maximizar (ou tela cheia) pela barra do sistema sai do modo compacto.
        LaunchedEffect(Unit) {
            snapshotFlow { state.placement }.collectLatest { placement ->
                if (placement != WindowPlacement.Floating) controller.setCompact(false)
            }
        }
        SonivellApp(controller)
    }
}
