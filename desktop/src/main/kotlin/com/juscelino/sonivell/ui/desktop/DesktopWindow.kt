package com.juscelino.sonivell.ui.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.juscelino.sonivell.R
import com.juscelino.sonivell.compat.SharedPreferences
import com.juscelino.sonivell.desktop.AudioRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado da janela do PC que não existe no app Android: modo compacto, painel da fila, atalhos de
 * teclado que mudam de tela e o aviso de som fora do servidor de som.
 */
object DesktopWindow {
    private val prefs: SharedPreferences get() = SharedPreferences.named("desktop")

    /** Versão única do Sonivell (version.properties), gravada pelo build. */
    val versionName: String by lazy {
        DesktopWindow::class.java.getResourceAsStream("/version.txt")?.bufferedReader()?.readText()?.trim().orEmpty()
    }

    private val _compact = MutableStateFlow(prefs.getBoolean("compact", false))
    val compact: StateFlow<Boolean> = _compact.asStateFlow()

    fun setCompact(on: Boolean) {
        if (_compact.value == on) return
        _compact.value = on
        prefs.edit().putBoolean("compact", on).apply()
    }

    fun toggleCompact() = setCompact(!_compact.value)

    var compactPosition: Pair<Int, Int>?
        get() = if (prefs.contains("compact_x")) prefs.getInt("compact_x", 0) to prefs.getInt("compact_y", 0) else null
        set(value) {
            val e = prefs.edit()
            if (value == null) e.remove("compact_x").remove("compact_y") else e.putInt("compact_x", value.first).putInt("compact_y", value.second)
            e.apply()
        }

    var queueOpen: Boolean
        get() = prefs.getBoolean("queue_open", true)
        set(value) { prefs.edit().putBoolean("queue_open", value).apply() }

    /** Voltar quando nenhuma tela tratou o Esc: a navegação volta uma tela. */
    @Volatile var backFallback: (() -> Boolean)? = null

    /** Aviso de que o som não passa pelo PipeWire/PulseAudio (ver [AudioRoute]); null quando está tudo certo. */
    var audioRouteHint by mutableStateOf(
        when (AudioRoute.missingBridge()) {
            AudioRoute.Server.PipeWire -> R.string.desktop_audio_route_pipewire
            AudioRoute.Server.PulseAudio -> R.string.desktop_audio_route_pulse
            null -> null
        },
    )

    private val _navigation = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val navigation: SharedFlow<Int> = _navigation.asSharedFlow()
    fun navigate(action: Int) { _navigation.tryEmit(action) }

    const val NAV_SEARCH = 1
    const val NAV_LYRICS = 2
    const val NAV_PLAYER = 3
    const val NAV_QUEUE = 4
    const val NAV_SETTINGS = 5

    // Ações dos atalhos do ícone (no Android, AppShortcuts); no PC, pelo menu do lançador (.desktop).
    const val ACTION_MIX_NOW = "mix_now"
    const val ACTION_SHUFFLE = "shuffle"
    const val ACTION_LIKED = "liked"
    const val ACTION_SEARCH = "search"
    const val ACTION_CAR = "car"
}
