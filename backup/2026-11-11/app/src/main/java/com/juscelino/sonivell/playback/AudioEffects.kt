package com.juscelino.sonivell.playback

import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.compose.runtime.Immutable

/**
 * Equalizador e reforço de graves do Android, presos à sessão de áudio do player.
 *
 * O serviço de reprodução é o dono dos efeitos; a tela do equalizador só escreve nas
 * preferências, e este objeto aplica as mudanças na hora (mesmo processo).
 */
class AudioEffects(private val prefs: SharedPreferences) {

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("eq_") && key != KEY_INFO) apply()
    }

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId <= 0) return
        eq = runCatching { Equalizer(0, audioSessionId) }
            .onFailure { Log.w(TAG, "Equalizador indisponível neste aparelho", it) }
            .getOrNull()
        bass = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        eq?.let(::publishInfo)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        apply()
    }

    fun release() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        eq = null
        bass = null
    }

    private fun apply() {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        eq?.let { e ->
            runCatching {
                val levels = parseLevels(prefs.getString(KEY_LEVELS, null))
                for (band in 0 until e.numberOfBands) {
                    val level = levels.getOrNull(band) ?: 0
                    e.setBandLevel(band.toShort(), level.toShort())
                }
                e.enabled = enabled
            }.onFailure { Log.w(TAG, "Falha ao aplicar o equalizador", it) }
        }
        bass?.let { b ->
            runCatching {
                val strength = prefs.getInt(KEY_BASS, 0).coerceIn(0, 1000)
                if (b.strengthSupported) b.setStrength(strength.toShort())
                b.enabled = enabled && strength > 0
            }
        }
    }

    /** Descreve o equalizador do aparelho (bandas, faixa e predefinições) para a tela de ajuste. */
    private fun publishInfo(e: Equalizer) {
        runCatching {
            val range = e.bandLevelRange
            val bands = (0 until e.numberOfBands).map { e.getCenterFreq(it.toShort()) / 1000 } // mHz → Hz
            val presets = (0 until e.numberOfPresets).map { i ->
                e.usePreset(i.toShort())
                val levels = (0 until e.numberOfBands).map { e.getBandLevel(it.toShort()).toInt() }
                e.getPresetName(i.toShort()) + "=" + levels.joinToString(",")
            }
            val info = listOf(
                range[0].toString(),
                range[1].toString(),
                bands.joinToString(","),
                presets.joinToString("|"),
                if (bass?.strengthSupported == true) "1" else "0",
            ).joinToString(";")
            prefs.edit().putString(KEY_INFO, info).apply()
        }
    }

    companion object {
        private const val TAG = "SonivellEq"
        const val KEY_ENABLED = "eq_enabled"
        const val KEY_LEVELS = "eq_levels"
        const val KEY_BASS = "eq_bass"
        const val KEY_PRESET = "eq_preset"
        const val KEY_INFO = "eq_info"

        fun parseLevels(raw: String?): List<Int> =
            raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

        fun readInfo(prefs: SharedPreferences): EqualizerInfo? {
            val raw = prefs.getString(KEY_INFO, null) ?: return null
            val parts = raw.split(';')
            if (parts.size < 5) return null
            return runCatching {
                EqualizerInfo(
                    minLevel = parts[0].toInt(),
                    maxLevel = parts[1].toInt(),
                    bandFrequencies = parts[2].split(',').filter { it.isNotBlank() }.map { it.toInt() },
                    presets = parts[3].split('|').filter { '=' in it }.map { p ->
                        EqPreset(p.substringBefore('='), parseLevels(p.substringAfter('=')))
                    },
                    bassSupported = parts[4] == "1",
                )
            }.getOrNull()
        }
    }
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
