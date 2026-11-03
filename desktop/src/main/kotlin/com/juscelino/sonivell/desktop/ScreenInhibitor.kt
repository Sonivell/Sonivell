package com.juscelino.sonivell.desktop

import java.io.File

/**
 * Mantém a tela acesa (modo carro): pede ao systemd que não suspenda nem desligue a tela enquanto
 * o processo "segurando" existir. Sem systemd-inhibit, não faz nada.
 */
object ScreenInhibitor {
    class Hold internal constructor(private val process: Process?) {
        fun release() { runCatching { process?.destroy() } }
    }

    fun hold(): Hold {
        val available = System.getenv("PATH").orEmpty().split(':').any { File(it, "systemd-inhibit").canExecute() }
        if (!available) return Hold(null)
        val p = runCatching {
            ProcessBuilder("systemd-inhibit", "--what=idle:sleep", "--who=Sonivell", "--why=Modo carro", "--mode=block", "sleep", "infinity")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        }.getOrNull()
        return Hold(p)
    }
}
