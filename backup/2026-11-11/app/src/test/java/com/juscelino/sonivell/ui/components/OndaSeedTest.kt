package com.juscelino.sonivell.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Capa Onda: a cor sai do artista e a onda do álbum, de forma estável. Os valores esperados são do
 * FNV-1a de 32 bits; o teste do desktop (OndaSeedDesktopTest) confere os mesmos números, para o
 * Android e o PC darem a mesma capa.
 */
class OndaSeedTest {
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
        // Normalizado: espaços nas pontas e maiúsculas não mudam a cor.
        assertEquals(a, OndaSeed.cor("  pink floyd "))
        assertEquals(a, OndaSeed.cor("PINK FLOYD"))
        assertEquals(0, a)
        assertEquals(4, OndaSeed.cor("Era"))
        assertEquals(4, OndaSeed.cor("Far East Movement"))
    }

    @Test
    fun ondaSaiDoAlbum() {
        assertEquals(3, OndaSeed.onda("Reborn"))
        assertEquals(0, OndaSeed.onda("The Wall"))
        assertEquals(OndaSeed.onda("The Wall"), OndaSeed.onda(" the wall"))
    }

    @Test
    fun indicesFicamNoIntervalo() {
        listOf("", "a", "Adriana Calcanhotto", "ção", "日本語", "x".repeat(500)).forEach {
            assertTrue(OndaSeed.cor(it) in 0 until OndaSeed.CORES)
            assertTrue(OndaSeed.onda(it) in 0 until OndaSeed.ONDAS)
        }
    }
}
