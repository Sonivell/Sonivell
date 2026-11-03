package com.juscelino.sonivell.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Artista do álbum: a tag do álbum, o artista comum ou "Vários artistas" nas coletâneas. */
class AlbumArtistTest {
    private val various = "Vários artistas"

    @Test
    fun usaATagDeArtistaDoAlbum() {
        assertEquals("Trilha Sonora", albumArtistFrom(listOf(null, " Trilha Sonora "), listOf("A", "B"), various))
    }

    @Test
    fun artistaComumATodasAsFaixas() {
        assertEquals("Pink Floyd", albumArtistFrom(listOf(null, ""), listOf("Pink Floyd", "pink floyd"), various))
    }

    @Test
    fun coletaneaSemTagViraVariosArtistas() {
        assertEquals(various, albumArtistFrom(listOf(null, null), listOf("DJ Diyanna", "Era"), various))
    }

    @Test
    fun semArtistaNenhum() {
        assertEquals("", albumArtistFrom(emptyList(), emptyList(), various))
    }
}
