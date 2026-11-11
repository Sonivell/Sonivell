package com.juscelino.sonivell.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Auditoria REL-02: gravação de uma vez e leitura que não apaga o que estava corrompido. */
class AtomicFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun writeReplacesTheFileAndLeavesNoTemporary() {
        val f = File(tmp.root, "playlists.json")
        AtomicFiles.writeText(f, "um")
        AtomicFiles.writeText(f, "dois")
        assertEquals("dois", f.readText())
        assertFalse(File(tmp.root, "playlists.json.tmp").exists())
    }

    @Test
    fun failedWriteKeepsThePreviousFileWhole() {
        val f = File(tmp.root, "playlists.json")
        AtomicFiles.writeText(f, "[\"anterior\"]")
        // O temporário não pode ser criado (já existe uma pasta com esse nome): a gravação falha.
        File(tmp.root, "playlists.json.tmp").mkdirs()
        try {
            AtomicFiles.writeText(f, "[\"novo\"]")
            fail("a gravação deveria falhar")
        } catch (e: Exception) {
            // esperado
        }
        assertEquals("[\"anterior\"]", f.readText())
    }

    @Test
    fun missingFileReadsAsNull() {
        assertNull(AtomicFiles.readOrQuarantine(File(tmp.root, "nada.json")) { it })
    }

    @Test
    fun readableFileIsParsed() {
        val f = File(tmp.root, "x.json")
        f.writeText("42")
        assertEquals(42, AtomicFiles.readOrQuarantine(f) { it.trim().toInt() })
    }

    @Test
    fun unreadableFileIsKeptAsideNotLost() {
        val f = File(tmp.root, "playlists.json")
        f.writeText("[{\"id\": 1, \"name\": \"Trunc") // cortado no meio
        // Um leitor que, como o JSON, rejeita o arquivo cortado.
        val parsed = AtomicFiles.readOrQuarantine(f) { text -> text.also { require(it.trim().endsWith("]")) } }
        assertNull(parsed)
        val kept = tmp.root.listFiles()!!.filter { it.name.startsWith("playlists.json.corrompido-") }
        assertEquals(1, kept.size)
        assertEquals(f.readText(), kept.single().readText())
        assertTrue(f.exists())
    }
}
