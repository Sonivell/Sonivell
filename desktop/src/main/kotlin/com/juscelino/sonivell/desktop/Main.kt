package com.juscelino.sonivell.desktop

import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.juscelino.sonivell.desktop.ui.SonivellApp
import java.awt.Dimension

fun main() = application {
    val controller = remember { Controller() }
    val state = rememberWindowState(size = DpSize(1360.dp, 860.dp))
    val icon = remember { runCatching { BitmapPainter(useResource("icon.png", ::loadImageBitmap)) }.getOrNull() }
    Window(
        onCloseRequest = { controller.shutdown(); exitApplication() },
        title = str("app_name"),
        state = state,
        icon = icon,
        onKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) false
            else when {
                e.key == Key.Spacebar -> { controller.togglePlay(); true }
                e.isCtrlPressed && e.key == Key.DirectionRight -> { controller.next(); true }
                e.isCtrlPressed && e.key == Key.DirectionLeft -> { controller.previous(); true }
                else -> false
            }
        },
    ) {
        window.minimumSize = Dimension(980, 640)
        SonivellApp(controller)
    }
}
