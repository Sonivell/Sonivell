package com.juscelino.sonivell.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BasicsTest {
    private fun track(title: String, artist: String, album: String = "", path: String = "/m/$title.mp3") = Track(
        id = trackIdOf(path), path = path, title = title, artist = artist, albumArtist = "", album = album, genre = "",
        year = 0, trackNo = 0, durationMs = 180_000, format = "MP3", bitrateKbps = 320, addedMs = 0,
    )

    @Test fun formatsTime() {
        assertEquals("1:05", formatTime(65_000))
        assertEquals("1:01:01", formatTime(3_661_000))
        assertEquals("0:00", formatTime(-5))
    }

    @Test fun foldRemovesAccents() = assertEquals("musica sao joao", fold("Música São João"))

    @Test fun trackIdIsStable() {
        assertEquals(trackIdOf("/a/b.mp3"), trackIdOf("/a/b.mp3"))
        assertNotEquals(trackIdOf("/a/b.mp3"), trackIdOf("/a/c.mp3"))
        assertTrue(trackIdOf("/a/b.mp3") >= 0)
    }

    @Test fun waveShapeIsStableAndInRange() {
        val a = waveShape(42, 30)
        assertEquals(a.toList(), waveShape(42, 30).toList())
        assertTrue(a.all { it in 0.15f..1.0f })
    }

    @Test fun searchIgnoresAccentsAndCase() {
        val lib = Library(listOf(track("Maré Alta", "Lua Norte"), track("Farol", "Sal Grosso")))
        assertEquals(listOf("Maré Alta"), lib.search("mare").map { it.title })
        assertEquals(listOf("Farol"), lib.search("SAL gros").map { it.title })
        assertTrue(lib.search("zzz").isEmpty())
    }

    @Test fun groupsAlbumsAndArtists() {
        val lib = Library(listOf(track("A", "X", "Alb"), track("B", "X", "Alb"), track("C", "Y", "Other")))
        assertEquals(2, lib.albums.size)
        assertEquals(2, lib.artists.size)
    }

    @Test fun stringsExistInBothLanguages() {
        val en = java.util.Properties().also { p -> Str::class.java.getResourceAsStream("/strings_en.properties")!!.reader(Charsets.UTF_8).use { p.load(it) } }
        val pt = java.util.Properties().also { p -> Str::class.java.getResourceAsStream("/strings_pt.properties")!!.reader(Charsets.UTF_8).use { p.load(it) } }
        assertTrue(en.size > 100, "strings do app devem ser lidas")
        val desktopKeys = en.stringPropertyNames().filter { it.startsWith("desktop_") }
        assertTrue(desktopKeys.isNotEmpty())
        for (k in desktopKeys) assertTrue(pt.containsKey(k), "faltou em pt: $k")
        assertEquals("Para você", pt.getProperty("nav_home"))
    }

    @Test fun formatsStringsWithArguments() {
        assertTrue(str("desktop_scanning", 3, 10).contains("3"))
    }
}
