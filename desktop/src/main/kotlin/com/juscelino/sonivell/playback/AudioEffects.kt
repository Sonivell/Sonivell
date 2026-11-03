package com.juscelino.sonivell.playback

import androidx.compose.runtime.Immutable
import com.juscelino.sonivell.compat.SharedPreferences
import com.juscelino.sonivell.data.SettingsStore
import com.juscelino.sonivell.desktop.AudioFilters

/**
 * Equalizador e reforço de graves do PC: as mesmas chaves do app Android, aplicadas pelo ffmpeg
 * (ver [AudioFilters]). Cinco bandas e as predefinições padrão do equalizador do Android, para a
 * tela e os ajustes salvos serem os mesmos nos dois.
 */
object AudioEffects {
    const val KEY_ENABLED = "eq_enabled"
    const val KEY_LEVELS = "eq_levels"
    const val KEY_BASS = "eq_bass"
    const val KEY_PRESET = "eq_preset"
    const val KEY_INFO = "eq_info"

    private val BANDS = listOf(60, 230, 910, 3600, 14000)

    val info = EqualizerInfo(
        minLevel = -1500,
        maxLevel = 1500,
        bandFrequencies = BANDS,
        presets = listOf(
            EqPreset("Normal", listOf(300, 0, 0, 0, 300)),
            EqPreset("Classical", listOf(500, 300, -200, 400, 400)),
            EqPreset("Dance", listOf(600, 0, 200, 400, 100)),
            EqPreset("Flat", listOf(0, 0, 0, 0, 0)),
            EqPreset("Folk", listOf(300, 0, 0, 200, -100)),
            EqPreset("Heavy Metal", listOf(400, 100, 900, 300, 0)),
            EqPreset("Hip Hop", listOf(500, 300, 0, 100, 300)),
            EqPreset("Jazz", listOf(400, 200, -200, 200, 500)),
            EqPreset("Pop", listOf(-100, 200, 500, 100, -200)),
            EqPreset("Rock", listOf(500, 300, -100, 300, 500)),
        ),
        bassSupported = true,
    )

    fun parseLevels(raw: String?): List<Int> =
        raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

    @Suppress("UNUSED_PARAMETER")
    fun readInfo(prefs: SharedPreferences): EqualizerInfo = info

    /** Os efeitos atuais (equalizador, graves, velocidade e silêncio) como filtros do ffmpeg. */
    fun filtersFrom(prefs: SharedPreferences): AudioFilters = AudioFilters(
        eqEnabled = prefs.getBoolean(KEY_ENABLED, false),
        bandFrequencies = BANDS,
        levels = parseLevels(prefs.getString(KEY_LEVELS, null)),
        bass = prefs.getInt(KEY_BASS, 0).coerceIn(0, 1000),
        speed = prefs.getFloat(SettingsStore.KEY_PLAYBACK_SPEED, 1f),
        skipSilence = prefs.getBoolean(SettingsStore.KEY_SKIP_SILENCE, false),
    )
}

@Immutable
data class EqPreset(val name: String, val levels: List<Int>)

/** Níveis em milibéis (mB); 100 mB = 1 dB. */
@Immutable
data class EqualizerInfo(
    val minLevel: Int,
    val maxLevel: Int,
    val bandFrequencies: List<Int>,
    val presets: List<EqPreset>,
    val bassSupported: Boolean,
)
