package com.juscelino.sonivell.system

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Auditoria REL-04: as preferências do backup são lidas para serem aplicadas pelo SharedPreferences. */
class PrefsXmlTest {
    @Test
    fun readsEveryTypeTheAndroidWrites() {
        val xml = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <string name="ui_style">SONIVELL</string>
                <string name="vazio"></string>
                <int name="artist_variety" value="2" />
                <long name="session_position" value="123456789012" />
                <float name="playback_speed" value="1.25" />
                <boolean name="smart_mode" value="true" />
                <set name="favorites">
                    <string>10</string>
                    <string>42</string>
                </set>
                <string name="com acento">Água &amp; &lt;som&gt;</string>
            </map>
        """.trimIndent()
        assertEquals(
            mapOf(
                "ui_style" to "SONIVELL",
                "vazio" to "",
                "artist_variety" to 2,
                "session_position" to 123456789012L,
                "playback_speed" to 1.25f,
                "smart_mode" to true,
                "favorites" to setOf("10", "42"),
                "com acento" to "Água & <som>",
            ),
            PrefsXml.parse(xml.toByteArray()),
        )
    }

    @Test
    fun somethingElseIsNotAPrefsFile() {
        try {
            PrefsXml.parse("<html><body/></html>".toByteArray())
            fail("deveria recusar")
        } catch (e: IllegalArgumentException) {
            // esperado
        }
    }

    @Test
    fun externalEntitiesAreNotResolved() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE map [<!ENTITY x SYSTEM "file:///etc/passwd">]><map><string name="a">&x;</string></map>"""
        val result = runCatching { PrefsXml.parse(xxe.toByteArray()) }
        // Ou recusa o DTD, ou não traz o conteúdo do arquivo: nunca o lê.
        result.getOrNull()?.let { assertEquals(false, (it["a"] as? String).orEmpty().contains("root:")) }
    }
}
