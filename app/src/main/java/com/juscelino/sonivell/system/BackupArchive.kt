package com.juscelino.sonivell.system

import com.juscelino.sonivell.data.AtomicFiles
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * O formato do backup e a sua leitura segura, sem Android (testado no JVM). [Backup] cuida dos
 * caminhos do app e do reinício.
 *
 * Um backup é um .zip com o marcador, as preferências (`shared_prefs/<nome>.xml`), os arquivos
 * do topo de `files/` e as capas escolhidas (`files/covers/<álbum>.jpg`).
 */
object BackupArchive {
    const val MARKER = "sonivell-backup.txt"
    const val PREFS_DIR = "shared_prefs/"
    const val FILES_DIR = "files/"

    /** Limites do restaurar (auditoria SEC-03): um .zip pequeno pode descompactar em gigabytes. */
    data class Limits(
        val maxEntryBytes: Long = 20L * 1024 * 1024,
        val maxTotalBytes: Long = 200L * 1024 * 1024,
        val maxEntries: Int = 2_000,
    )

    private val PREFS_NAME = Regex("""shared_prefs/[A-Za-z0-9._-]+\.xml""")
    private val TOP_FILE = Regex("""files/[A-Za-z0-9][A-Za-z0-9._-]*""")
    private val COVER = Regex("""files/covers/-?[0-9]+\.jpg""")

    /** Só os nomes que o próprio backup gera; temporários (.tmp) nunca. */
    fun isAllowed(name: String): Boolean =
        !name.endsWith(".tmp") && (PREFS_NAME.matches(name) || TOP_FILE.matches(name) || COVER.matches(name))

    class TooLarge(message: String) : IOException(message)

    /**
     * Extrai [input] para [staging] (que deve estar vazia), aplicando [limits] e a lista de nomes.
     * Conta os bytes de verdade: o tamanho declarado no .zip pode mentir. Devolve se o marcador do
     * Sonivell estava presente; lança [TooLarge] ao passar de um limite (o chamador descarta a staging).
     */
    fun extract(input: InputStream, staging: File, limits: Limits = Limits()): Boolean {
        var valid = false
        var entries = 0
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (++entries > limits.maxEntries) throw TooLarge("Backup com entradas demais")
                if (e.isDirectory) continue
                val name = e.name
                if (name == MARKER) {
                    valid = true
                    continue
                }
                if (!isAllowed(name)) continue
                val out = File(staging, name)
                out.parentFile?.mkdirs()
                out.outputStream().use { o ->
                    total += copyLimited(zip, o, limits.maxEntryBytes, limits.maxTotalBytes - total)
                }
            }
        }
        return valid
    }

    /** Copia até o limite da entrada e o que resta do total; passou, lança [TooLarge]. */
    private fun copyLimited(input: InputStream, out: OutputStream, maxEntry: Long, remainingTotal: Long): Long {
        val buf = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return copied
            copied += n
            if (copied > maxEntry) throw TooLarge("Arquivo grande demais no backup")
            if (copied > remainingTotal) throw TooLarge("Backup grande demais")
            out.write(buf, 0, n)
        }
    }

    /**
     * Instala o que foi extraído: cada arquivo é trocado de uma vez ([AtomicFiles]), então nenhum
     * fica pela metade. Só roda depois de [extract] terminar e o marcador ser confirmado.
     */
    fun install(staging: File, prefsDir: File, filesDir: File) {
        File(staging, PREFS_DIR).listFiles()?.filter { it.isFile }?.forEach { f ->
            AtomicFiles.write(File(prefsDir, f.name), f.readBytes())
        }
        val stagedFiles = File(staging, FILES_DIR)
        stagedFiles.walkTopDown().filter { it.isFile }.forEach { f ->
            AtomicFiles.write(File(filesDir, f.relativeTo(stagedFiles).path), f.readBytes())
        }
    }
}
