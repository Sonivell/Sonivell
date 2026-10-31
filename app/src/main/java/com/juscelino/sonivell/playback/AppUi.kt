package com.juscelino.sonivell.playback

import java.util.concurrent.atomic.AtomicInteger

/**
 * Se a tela do app está viva (o MainViewModel existe). Com ela viva, é o app que abastece a fila
 * SMART (com o gosto aprendido e o "Por que tocou"); sem ela, o serviço de reprodução assume.
 */
object AppUi {
    private val open = AtomicInteger(0)

    val isOpen: Boolean get() = open.get() > 0

    fun opened() {
        open.incrementAndGet()
    }

    fun closed() {
        open.updateAndGet { (it - 1).coerceAtLeast(0) }
    }
}
