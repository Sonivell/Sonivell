package com.juscelino.sonivell.desktop.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Capa Onda no PC: os mesmos números do teste do app Android (OndaSeedTest), para os dois darem a
 * mesma cor e a mesma onda para cada artista e álbum.
 */
class OndaSeedDesktopTest {
    @Test
    fun fnv1aConfereComValoresDeReferencia() {
        assertEquals(0x811c9dc5L, OndaSeed.fnv1a(""))
        assertEquals(0x7f979115L, OndaSeed.fnv1a("era"))
        assertEquals(0x82a2d59bL, OndaSeed.fnv1a("pink floyd"))
        assertEquals(0x0b2cde09L, OndaSeed.fnv1a("ação"))
    }

    @Test
    fun mesmoArtistaDaSempreAMesmaCor() {
        val a = OndaSeed.cor("Pink Floyd")
        repeat(50) { assertEquals(a, OndaSeed.cor("Pink Floyd")) }
        assertEquals(a, OndaSeed.cor("  pink floyd "))
        assertEquals(0, a)
        assertEquals(4, OndaSeed.cor("Era"))
        assertEquals(4, OndaSeed.cor("Far East Movement"))
    }

    @Test
    fun ondaSaiDoAlbum() {
        assertEquals(3, OndaSeed.onda("Reborn"))
        assertEquals(0, OndaSeed.onda("The Wall"))
    }

    @Test
    fun indicesFicamNoIntervalo() {
        listOf("", "a", "Adriana Calcanhotto", "日本語", "x".repeat(500)).forEach {
            assertTrue(OndaSeed.cor(it) in 0 until OndaSeed.CORES)
            assertTrue(OndaSeed.onda(it) in 0 until OndaSeed.ONDAS)
        }
    }
}
