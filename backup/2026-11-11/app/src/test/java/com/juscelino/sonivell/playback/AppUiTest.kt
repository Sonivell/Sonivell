package com.juscelino.sonivell.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quem abastece a fila SMART: o app enquanto alguma tela está viva, o serviço depois. */
class AppUiTest {
    @Test
    fun openUntilEveryScreenCloses() {
        assertFalse(AppUi.isOpen)
        AppUi.opened()
        AppUi.opened()
        AppUi.closed()
        assertTrue(AppUi.isOpen)
        AppUi.closed()
        assertFalse(AppUi.isOpen)
    }

    @Test
    fun extraCloseDoesNotLeaveTheCounterNegative() {
        AppUi.closed()
        AppUi.closed()
        AppUi.opened()
        assertTrue(AppUi.isOpen) // um "fechou" a mais não pode esconder o app aberto (o serviço abasteceria em dobro)
        AppUi.closed()
        assertFalse(AppUi.isOpen)
    }
}
