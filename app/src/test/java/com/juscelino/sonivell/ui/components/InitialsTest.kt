package com.juscelino.sonivell.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Iniciais das capas vazias. */
class InitialsTest {
    @Test
    fun duasPrimeirasPalavras() {
        assertEquals("LN", initialsOf("Lariss Neo-Soul"))
        assertEquals("R", initialsOf("reborn"))
        assertEquals("DB", initialsOf("  Dirty   Bass  "))
    }

    @Test
    fun ignoraSimbolosSoltos() {
        assertEquals("AV", initialsOf("(Ao vivo) · Mix"))
        assertEquals("M", initialsOf("· Mix"))
        assertEquals("", initialsOf(""))
        assertEquals("", initialsOf(" - "))
    }
}
