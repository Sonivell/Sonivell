package com.juscelino.sonivell.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Auditoria SEC-03: o restaurar só aceita o formato do backup, dentro de limites. */
class BackupArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, data) ->
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val marker = BackupArchive.MARKER to "Sonivell backup v1\n".toByteArray()

    private fun extract(bytes: ByteArray, limits: BackupArchive.Limits = BackupArchive.Limits()): Pair<Boolean, File> {
        val staging = tmp.newFolder()
        return BackupArchive.extract(ByteArrayInputStream(bytes), staging, limits) to staging
    }

    @Test
    fun validBackupIsExtractedAndInstalled() {
        val (valid, staging) = extract(
            zip(
                marker,
                "shared_prefs/sonivell.xml" to "<map/>".toByteArray(),
                "files/playlists.json" to "[]".toByteArray(),
                "files/covers/123.jpg" to byteArrayOf(1, 2, 3),
            ),
        )
        assertTrue(valid)
        assertEquals(mapOf("sonivell" to emptyMap<String, Any>()), BackupArchive.stagedPrefs(staging))
        val files = tmp.newFolder("files")
        BackupArchive.installFiles(staging, files)
        assertEquals("[]", File(files, "playlists.json").readText())
        assertEquals(3, File(files, "covers/123.jpg").length())
    }

    @Test
    fun zipWithoutMarkerIsNotABackup() {
        val (valid, _) = extract(zip("files/playlists.json" to "[]".toByteArray()))
        assertFalse(valid)
    }

    @Test
    fun namesOutsideTheBackupFormatAreIgnored() {
        val (valid, staging) = extract(
            zip(
                marker,
                "files/../fora.txt" to "x".toByteArray(),
                "files/sub/../../fora2.txt" to "x".toByteArray(),
                "../fora3.txt" to "x".toByteArray(),
                "/etc/fora4" to "x".toByteArray(),
                "files/sub/indice.bin" to "x".toByteArray(),
                "files/playlists.json.tmp" to "x".toByteArray(),
                "shared_prefs/../x.xml" to "x".toByteArray(),
                "files/covers/capa.png" to "x".toByteArray(),
            ),
        )
        assertTrue(valid)
        // Nada foi extraído, nem dentro nem fora da pasta temporária.
        assertEquals(emptyList<File>(), staging.walkTopDown().filter { it.isFile }.toList())
        assertFalse(File(staging.parentFile, "fora.txt").exists())
        assertFalse(File(staging.parentFile, "fora2.txt").exists())
    }

    @Test
    fun allowedNamesMatchWhatTheExportWrites() {
        assertTrue(BackupArchive.isAllowed("shared_prefs/sonivell.xml"))
        assertTrue(BackupArchive.isAllowed("files/historico.tsv"))
        assertTrue(BackupArchive.isAllowed("files/gosto.bin"))
        assertTrue(BackupArchive.isAllowed("files/covers/-42.jpg"))
        assertFalse(BackupArchive.isAllowed("files/.oculto"))
        assertFalse(BackupArchive.isAllowed("files/covers/../gosto.bin"))
    }

    @Test
    fun entryOverTheLimitIsRefused() {
        val limits = BackupArchive.Limits(maxEntryBytes = 1_000, maxTotalBytes = 10_000, maxEntries = 100)
        try {
            extract(zip(marker, "files/grande.bin" to ByteArray(1_001)), limits)
            fail("deveria recusar")
        } catch (e: BackupArchive.TooLarge) {
            // esperado
        }
    }

    @Test
    fun totalOverTheLimitIsRefused() {
        val limits = BackupArchive.Limits(maxEntryBytes = 1_000, maxTotalBytes = 2_500, maxEntries = 100)
        val parts = (1..3).map { "files/p$it.bin" to ByteArray(900) }.toTypedArray()
        try {
            extract(zip(marker, *parts), limits)
            fail("deveria recusar")
        } catch (e: BackupArchive.TooLarge) {
            // esperado
        }
    }

    @Test
    fun tooManyEntriesAreRefused() {
        val limits = BackupArchive.Limits(maxEntries = 10)
        val parts = (1..11).map { "files/p$it.bin" to ByteArray(1) }.toTypedArray()
        try {
            extract(zip(*parts), limits)
            fail("deveria recusar")
        } catch (e: BackupArchive.TooLarge) {
            // esperado
        }
    }

    @Test
    fun zipBombStopsAtTheLimitWithoutFillingTheDisk() {
        // 50 MB de zeros viram ~50 KB comprimidos; com o limite padrão de 20 MB por arquivo, para.
        val bomb = zip(marker, "files/bomba.bin" to ByteArray(50 * 1024 * 1024))
        assertTrue(bomb.size < 1024 * 1024)
        val staging = tmp.newFolder()
        try {
            BackupArchive.extract(ByteArrayInputStream(bomb), staging)
            fail("deveria recusar")
        } catch (e: BackupArchive.TooLarge) {
            // esperado
        }
        val written = staging.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertTrue("escreveu $written bytes", written <= 20L * 1024 * 1024)
    }
}
