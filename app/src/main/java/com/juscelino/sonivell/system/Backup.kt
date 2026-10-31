package com.juscelino.sonivell.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/**
 * Backup dos dados do Sonivell num único .zip: playlists, curtidas, histórico, notas,
 * correções de tags, capas escolhidas, ajustes e o modelo de gosto. As músicas não entram
 * (continuam no aparelho), nem os índices do SMART, que são refeitos sozinhos.
 */
object Backup {
    private const val PREFS_DIR = BackupArchive.PREFS_DIR
    private const val FILES_DIR = BackupArchive.FILES_DIR
    private const val MARKER = BackupArchive.MARKER
    /** Pastas dentro de filesDir que entram no backup (o resto é índice/cache). */
    private val KEEP_DIRS = setOf("covers")
    private const val MAX_FILE = 20L * 1024 * 1024

    private fun prefsDir(context: Context) = File(context.applicationInfo.dataDir, "shared_prefs")

    fun export(context: Context, target: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(target)?.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MARKER))
                zip.write("Sonivell backup v1\n".toByteArray())
                zip.closeEntry()
                prefsDir(context).listFiles()?.filter { it.isFile && it.name.endsWith(".xml") }?.forEach { f ->
                    zip.add(PREFS_DIR + f.name, f)
                }
                context.filesDir.listFiles()?.forEach { f ->
                    when {
                        f.isFile && f.length() <= MAX_FILE -> zip.add(FILES_DIR + f.name, f)
                        f.isDirectory && f.name in KEEP_DIRS -> f.listFiles()?.filter { it.isFile }?.forEach { c ->
                            zip.add(FILES_DIR + f.name + "/" + c.name, c)
                        }
                    }
                }
            }
        } != null
    }.getOrDefault(false)

    private fun ZipOutputStream.add(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    /**
     * Restaura um backup. Extrai tudo para uma pasta temporária com limites de tamanho e de
     * quantidade e só com os nomes que o próprio backup gera ([BackupArchive]); só depois de o .zip
     * se mostrar do Sonivell e caber nos limites, troca os arquivos, um a um e de uma vez cada.
     * Depois é preciso reiniciar o app ([restart]).
     */
    fun restore(context: Context, source: Uri): Boolean = runCatching {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            val valid = context.contentResolver.openInputStream(source)?.use { BackupArchive.extract(it, staging) }
            if (valid != true) return false
            BackupArchive.install(staging, prefsDir(context), context.filesDir)
            true
        } finally {
            staging.deleteRecursively()
        }
    }.getOrDefault(false)

    /** Reabre o app do zero para que tudo seja lido do backup restaurado. */
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        exitProcess(0)
    }
}
