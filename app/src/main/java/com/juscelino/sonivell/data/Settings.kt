package com.juscelino.sonivell.data

import android.content.SharedPreferences
import com.juscelino.sonivell.ui.theme.AccentChoice
import com.juscelino.sonivell.ui.theme.IconStyle
import com.juscelino.sonivell.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val theme: ThemeMode = ThemeMode.AUTO,
    val accent: AccentChoice = AccentChoice.Lima,
    /** Traço fino (padrão, como o Lumivell) ou arredondado. */
    val iconStyle: IconStyle = IconStyle.FINO,
    /** Pausar quando o fone é desconectado (Media3 "becoming noisy"). */
    val pauseOnUnplug: Boolean = true,
    /** Não registrar novas escutas (o SMART e o Para você param de aprender). */
    val historyPaused: Boolean = false,
    /** Variedade de artistas no SMART: 0 a 4 (2 = padrão do LatentJam). */
    val artistVariety: Int = 2,
    /** Normalizar o volume entre faixas pelo ReplayGain das tags. */
    val normalize: Boolean = false,
)

/** Ajustes do app, nas preferências locais. O serviço de reprodução lê as mesmas chaves. */
class SettingsStore(private val prefs: SharedPreferences) {

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs.edit()
            .putInt(KEY_THEME, next.theme.ordinal)
            .putInt(KEY_ACCENT, next.accent.ordinal)
            .putString(KEY_ICON_STYLE, next.iconStyle.name)
            .putBoolean(KEY_PAUSE_ON_UNPLUG, next.pauseOnUnplug)
            .putBoolean(KEY_HISTORY_PAUSED, next.historyPaused)
            .putInt(KEY_ARTIST_VARIETY, next.artistVariety)
            .putBoolean(KEY_NORMALIZE, next.normalize)
            .apply()
    }

    fun reset() = update { AppSettings() }

    private fun read() = AppSettings(
        theme = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, 0)) { ThemeMode.AUTO },
        accent = AccentChoice.entries.getOrElse(prefs.getInt(KEY_ACCENT, 0)) { AccentChoice.Lima },
        iconStyle = IconStyle.entries.firstOrNull { it.name == prefs.getString(KEY_ICON_STYLE, null) } ?: IconStyle.FINO,
        pauseOnUnplug = prefs.getBoolean(KEY_PAUSE_ON_UNPLUG, true),
        historyPaused = prefs.getBoolean(KEY_HISTORY_PAUSED, false),
        artistVariety = prefs.getInt(KEY_ARTIST_VARIETY, 2),
        normalize = prefs.getBoolean(KEY_NORMALIZE, false),
    )

    companion object {
        const val PREFS = "sonivell"
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
        const val KEY_ICON_STYLE = "icon_style"
        const val KEY_PAUSE_ON_UNPLUG = "pause_on_unplug"
        const val KEY_HISTORY_PAUSED = "history_paused"
        const val KEY_ARTIST_VARIETY = "artist_variety"
        const val KEY_NORMALIZE = "normalize"
    }
}
