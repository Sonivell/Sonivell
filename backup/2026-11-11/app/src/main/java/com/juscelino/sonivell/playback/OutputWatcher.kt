package com.juscelino.sonivell.playback

import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.juscelino.sonivell.data.SettingsStore

/**
 * Acompanha a saída de áudio (fone com fio, Bluetooth, alto-falante):
 * - "Retomar ao conectar": volta a tocar quando um fone ou Bluetooth conecta;
 * - "Equalizador por saída": guarda o ajuste da saída anterior e carrega o da nova.
 */
class OutputWatcher(
    private val audio: AudioManager,
    private val prefs: SharedPreferences,
    private val player: Player,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var current: Output = detect()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            val connectedHeadset = added.any { it.isSink && kindOf(it) != Output.Alto }
            onOutputMaybeChanged()
            if (connectedHeadset && prefs.getBoolean(SettingsStore.KEY_RESUME_ON_CONNECT, false)) {
                // O Bluetooth leva um instante para assumir o áudio; tocar antes sai no alto-falante.
                handler.postDelayed({
                    if (!player.isPlaying && player.mediaItemCount > 0 && detect() != Output.Alto) player.play()
                }, RESUME_DELAY_MS)
            }
        }

        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = onOutputMaybeChanged()
    }

    fun start() = audio.registerAudioDeviceCallback(callback, handler)

    fun release() {
        audio.unregisterAudioDeviceCallback(callback)
        handler.removeCallbacksAndMessages(null)
    }

    private fun onOutputMaybeChanged() {
        val next = detect()
        if (next == current) return
        if (prefs.getBoolean(SettingsStore.KEY_EQ_PER_OUTPUT, false)) swapEqualizer(current, next)
        current = next
    }

    /** Salva o equalizador atual na saída que saiu e carrega o da que entrou (se já houver um). */
    private fun swapEqualizer(from: Output, to: Output) {
        val e = prefs.edit()
        e.putBoolean(key(from, "enabled"), prefs.getBoolean(AudioEffects.KEY_ENABLED, false))
        e.putString(key(from, "levels"), prefs.getString(AudioEffects.KEY_LEVELS, null))
        e.putInt(key(from, "bass"), prefs.getInt(AudioEffects.KEY_BASS, 0))
        e.putString(key(from, "preset"), prefs.getString(AudioEffects.KEY_PRESET, null))
        if (prefs.contains(key(to, "levels")) || prefs.contains(key(to, "enabled"))) {
            e.putBoolean(AudioEffects.KEY_ENABLED, prefs.getBoolean(key(to, "enabled"), false))
            e.putString(AudioEffects.KEY_LEVELS, prefs.getString(key(to, "levels"), null))
            e.putInt(AudioEffects.KEY_BASS, prefs.getInt(key(to, "bass"), 0))
            e.putString(AudioEffects.KEY_PRESET, prefs.getString(key(to, "preset"), null))
        }
        e.apply()
    }

    private fun detect(): Output {
        val outs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return when {
            outs.any { kindOf(it) == Output.Bluetooth } -> Output.Bluetooth
            outs.any { kindOf(it) == Output.Fone } -> Output.Fone
            else -> Output.Alto
        }
    }

    private fun kindOf(d: AudioDeviceInfo): Output = when (d.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> Output.Bluetooth
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> Output.Fone
        else -> Output.Alto
    }

    private fun key(o: Output, field: String) = "eqout_${o.name.lowercase()}_$field"

    enum class Output { Fone, Bluetooth, Alto }

    private companion object {
        const val RESUME_DELAY_MS = 1_500L
    }
}
