package com.juscelino.sonivell.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BasicsTest {
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

    @Test fun pluralsFollowAndroidRules() {
        // "songs_count" vem do strings.xml do app; os plurais viram "nome#one"/"nome#other".
        val one = com.juscelino.sonivell.compat.pluralStringResource(com.juscelino.sonivell.R.plurals.songs_count, 1, 1)
        val many = com.juscelino.sonivell.compat.pluralStringResource(com.juscelino.sonivell.R.plurals.songs_count, 5, 5)
        assertTrue(one.contains("1") && many.contains("5") && one != many, "$one / $many")
    }

    @Test fun generatedIdsGoBackToNames() {
        assertEquals("app_name", com.juscelino.sonivell.compat.Texts.name(com.juscelino.sonivell.R.string.app_name))
        assertEquals("Sonivell", com.juscelino.sonivell.compat.stringResource(com.juscelino.sonivell.R.string.app_name))
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
