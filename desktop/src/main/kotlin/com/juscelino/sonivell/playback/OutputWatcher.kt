package com.juscelino.sonivell.playback

import com.juscelino.sonivell.compat.SharedPreferences
import com.juscelino.sonivell.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Acompanha a saída de áudio do sistema (PulseAudio ou PipeWire, pelo `pactl`), como o
 * OutputWatcher do Android:
 * - "Pausar ao desconectar": fone ou Bluetooth saiu e o som voltou para o alto-falante → pausa;
 * - "Retomar ao conectar": um fone ou Bluetooth entrou → volta a tocar;
 * - "Equalizador por saída": guarda o ajuste da saída anterior e carrega o da nova.
 * Sem `pactl`, não faz nada (e o nome da saída fica "Este computador").
 */
class OutputWatcher(
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val onPause: () -> Unit,
    private val onResume: () -> Unit,
) {
    enum class Output { Fone, Bluetooth, Alto }

    data class Device(val kind: Output, val name: String?)

    private val _device = MutableStateFlow(Device(Output.Alto, null))

    /** A saída atual e o nome dela (ex.: "Fone de ouvido", "WH-1000XM4"), para o rodapé do player. */
    val device: StateFlow<Device> = _device.asStateFlow()

    private var job: Job? = null
    private val available: Boolean =
        System.getenv("PATH").orEmpty().split(':').any { File(it, "pactl").canExecute() }

    fun start() {
        if (!available || job != null) return
        job = scope.launch(Dispatchers.IO) {
            _device.value = detect() ?: _device.value
            while (isActive) {
                delay(POLL_MS)
                val next = detect() ?: continue
                val before = _device.value
                if (next.kind != before.kind) onChanged(before.kind, next.kind)
                _device.value = next
            }
        }
    }

    fun release() { job?.cancel(); job = null }

    private fun onChanged(from: Output, to: Output) {
        if (prefs.getBoolean(SettingsStore.KEY_EQ_PER_OUTPUT, false)) swapEqualizer(from, to)
        if (to == Output.Alto && from != Output.Alto && prefs.getBoolean(SettingsStore.KEY_PAUSE_ON_UNPLUG, true)) onPause()
        if (to != Output.Alto && prefs.getBoolean(SettingsStore.KEY_RESUME_ON_CONNECT, false)) {
            scope.launch {
                // O Bluetooth leva um instante para assumir o áudio; tocar antes sai no alto-falante.
                delay(RESUME_DELAY_MS)
                if (_device.value.kind != Output.Alto) onResume()
            }
        }
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

    private fun key(o: Output, field: String) = "eqout_${o.name.lowercase()}_$field"

    private fun run(vararg cmd: String): String? = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(5, TimeUnit.SECONDS)) { p.destroyForcibly(); null } else text
    }.getOrNull()

    /** A saída padrão do sistema: o tipo (pelo nome e pela porta ativa) e a descrição. */
    private fun detect(): Device? {
        val sink = run("pactl", "get-default-sink")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val list = run("pactl", "list", "sinks") ?: return null
        val block = list.split(Regex("(?m)^Sink #")).firstOrNull { b -> b.lines().any { it.trim() == "Name: $sink" } }.orEmpty()
        val description = block.lines().firstOrNull { it.trim().startsWith("Description:") }?.substringAfter(':')?.trim()
        val port = block.lines().firstOrNull { it.trim().startsWith("Active Port:") }?.substringAfter(':')?.trim().orEmpty().lowercase()
        val kind = when {
            "bluez" in sink.lowercase() -> Output.Bluetooth
            "headphone" in port || "headset" in port || "headphone" in sink.lowercase() -> Output.Fone
            else -> Output.Alto
        }
        return Device(kind, description)
    }

    private companion object {
        const val POLL_MS = 2_000L
        const val RESUME_DELAY_MS = 1_500L
    }
}
