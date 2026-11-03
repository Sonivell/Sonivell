package com.juscelino.sonivell.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
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
import com.juscelino.sonivell.MainViewModel
import com.juscelino.sonivell.R
import com.juscelino.sonivell.compat.BackDispatcher
import com.juscelino.sonivell.compat.Texts
import com.juscelino.sonivell.data.iconStyle
import com.juscelino.sonivell.playback.PlayerVolume
import com.juscelino.sonivell.system.DesktopSystem
import com.juscelino.sonivell.ui.SonivellApp
import com.juscelino.sonivell.ui.components.LocalPlayerPosition
import com.juscelino.sonivell.ui.desktop.CompactPlayer
import com.juscelino.sonivell.ui.desktop.DesktopWindow
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Dimension

/** Tamanho da janela no modo compacto (só capa e controles). */
private val COMPACT_SIZE = DpSize(400.dp, 500.dp)
private val FULL_SIZE = DpSize(1400.dp, 880.dp)

fun main(args: Array<String>) {
    // Dados da versão anterior do PC (ajustes, curtidas, histórico) passam para o formato do app.
    LegacyMigration.run()
    application {
        val vm = remember { MainViewModel() }
        // No PC não há permissão a pedir: as músicas vêm das pastas escolhidas. Liga o player e lê a biblioteca.
        LaunchedEffect(Unit) { vm.onPermissionGranted() }
        // Atalhos do lançador: sonivell --action=mix_now (mix_now, shuffle, liked, search, car).
        LaunchedEffect(Unit) {
            args.firstOrNull { it.startsWith("--action=") }?.substringAfter('=')?.let(vm::requestShortcut)
        }
        // Teclas de mídia e controle de música do sistema (MPRIS); sem D-Bus, não faz nada.
        val mpris = remember { Mpris(vm).also { m -> Thread({ m.start() }, "mpris").apply { isDaemon = true }.start() } }
        val compact by DesktopWindow.compact.collectAsState()
        val settings by vm.settings.collectAsState()
        val state = rememberWindowState(
            size = if (DesktopWindow.compact.value) COMPACT_SIZE else FULL_SIZE,
            position = DesktopWindow.compactPosition?.takeIf { DesktopWindow.compact.value }
                ?.let { (x, y) -> WindowPosition(x.dp, y.dp) } ?: WindowPosition.PlatformDefault,
        )
        // Tamanho do player completo antes de entrar no modo compacto, para voltar igual.
        var fullSize by remember { mutableStateOf(FULL_SIZE) }
        // Só trava o tamanho depois que a janela já encolheu (o gerenciador de janelas precisa deixar encolher).
        var sizeLocked by remember { mutableStateOf(false) }
        val icon = remember { runCatching { BitmapPainter(useResource("icon.png", ::loadImageBitmap)) }.getOrNull() }
        Window(
            onCloseRequest = { mpris.stop(); vm.shutdown(); exitApplication() },
            title = Texts.get(R.string.app_name),
            state = state,
            icon = icon,
            resizable = !sizeLocked,
            onKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) false
                else when {
                    e.key == Key.Spacebar -> { vm.player.togglePlay(); true }
                    e.key == Key.Escape -> BackDispatcher.dispatch() || DesktopWindow.backFallback?.invoke() == true
                    e.key == Key.MediaPlayPause -> { vm.player.togglePlay(); true }
                    e.key == Key.MediaNext -> { vm.player.next(); true }
                    e.key == Key.MediaPrevious -> { vm.player.previous(); true }
                    !e.isCtrlPressed -> false
                    e.key == Key.M -> { DesktopWindow.toggleCompact(); true }
                    e.key == Key.DirectionRight -> { vm.player.next(); true }
                    e.key == Key.DirectionLeft -> { vm.player.previous(); true }
                    e.key == Key.DirectionUp -> { PlayerVolume.set(PlayerVolume.value + 0.05f); true }
                    e.key == Key.DirectionDown -> { PlayerVolume.set(PlayerVolume.value - 0.05f); true }
                    e.key == Key.F -> { DesktopWindow.navigate(DesktopWindow.NAV_SEARCH); true }
                    e.key == Key.L -> { DesktopWindow.navigate(DesktopWindow.NAV_LYRICS); true }
                    e.key == Key.P -> { DesktopWindow.navigate(DesktopWindow.NAV_PLAYER); true }
                    e.key == Key.Q -> { DesktopWindow.navigate(DesktopWindow.NAV_QUEUE); true }
                    e.key == Key.Comma -> { DesktopWindow.navigate(DesktopWindow.NAV_SETTINGS); true }
                    e.key == Key.S -> { vm.cycleShuffle(); true }
                    e.key == Key.R -> { vm.player.cycleRepeat(); true }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) {
                DesktopSystem.window = window
                mpris.onRaise = {
                    state.isMinimized = false
                    window.toFront()
                    window.requestFocus()
                }
            }
            LaunchedEffect(compact) {
                if (compact) {
                    sizeLocked = false
                    // O modo compacto só existe com a janela solta: o gerenciador de janelas desmaximiza
                    // sem esperar e devolve o tamanho antigo logo depois. Espera a janela soltar antes de encolher.
                    if (state.placement != WindowPlacement.Floating) {
                        state.placement = WindowPlacement.Floating
                        withTimeoutOrNull(1500) { snapshotFlow { state.placement }.first { it == WindowPlacement.Floating } }
                        delay(300)
                    }
                    window.minimumSize = Dimension(360, 440)
                    state.size = COMPACT_SIZE
                    DesktopWindow.compactPosition?.let { (x, y) -> state.position = WindowPosition(x.dp, y.dp) }
                    repeat(4) {
                        delay(200)
                        if (state.size.width > COMPACT_SIZE.width + 24.dp || state.size.height > COMPACT_SIZE.height + 24.dp) state.size = COMPACT_SIZE
                    }
                    sizeLocked = true
                    // Lembra onde a janela compacta ficou.
                    snapshotFlow { state.position }.collectLatest { pos ->
                        if (pos is WindowPosition.Absolute) {
                            delay(600)
                            DesktopWindow.compactPosition = pos.x.value.toInt() to pos.y.value.toInt()
                        }
                    }
                } else {
                    sizeLocked = false
                    if (state.size == COMPACT_SIZE) state.size = fullSize
                    window.minimumSize = Dimension(1080, 700)
                    snapshotFlow { state.size to state.placement }.collectLatest { (size, placement) ->
                        if (placement == WindowPlacement.Floating && size.width >= 1080.dp) fullSize = size
                    }
                }
            }
            // Maximizar (ou tela cheia) pela barra do sistema sai do modo compacto.
            LaunchedEffect(Unit) {
                snapshotFlow { state.placement }.collectLatest { placement ->
                    if (placement != WindowPlacement.Floating) DesktopWindow.setCompact(false)
                }
            }
            val dark = when (settings.theme) {
                ThemeMode.AUTO -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SonivellTheme(
                accent = settings.accent,
                darkTheme = dark,
                iconStyle = settings.uiStyle.iconStyle,
                darkBackground = settings.darkBackground,
                tintedBackground = settings.tintedBackground,
                systemColors = false,
                style = settings.uiStyle,
            ) {
                // A posição ao vivo vai só para quem mostra o progresso (ver LocalPlayerPosition).
                val position = vm.player.position.collectAsState()
                CompositionLocalProvider(LocalPlayerPosition provides position) {
                    if (compact) CompactPlayer(vm) else SonivellApp(vm)
                }
            }
        }
    }
}
