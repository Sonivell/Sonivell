package com.juscelino.sonivell.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/**
 * Backup dos dados do Sonivell num único .zip: playlists, curtidas, histórico, notas,
 * correções de tags, capas escolhidas, ajustes e o modelo de gosto. As músicas não entram
 * (continuam no aparelho), nem os índices do SMART, que são refeitos sozinhos.
 */
object Backup {
    private const val PREFS_DIR = "shared_prefs/"
    private const val FILES_DIR = "files/"
    private const val MARKER = "sonivell-backup.txt"
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
     * Restaura um backup. Valida o arquivo antes de tocar em qualquer coisa: só substitui os
     * dados se o .zip for mesmo do Sonivell. Depois é preciso reiniciar o app ([restart]).
     */
    fun restore(context: Context, source: Uri): Boolean = runCatching {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var valid = false
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { e ->
                    if (e.isDirectory) return@forEach
                    val name = e.name
                    if (name == MARKER) { valid = true; return@forEach }
                    // Só aceita caminhos conhecidos e sem "..".
                    if (".." in name || !(name.startsWith(PREFS_DIR) || name.startsWith(FILES_DIR))) return@forEach
                    val out = File(staging, name)
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
        } ?: return false
        if (!valid) {
            staging.deleteRecursively()
            return false
        }
        File(staging, PREFS_DIR).listFiles()?.forEach { f -> f.copyTo(File(prefsDir(context), f.name), overwrite = true) }
        File(staging, FILES_DIR).walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(File(staging, FILES_DIR)).path
            File(context.filesDir, rel).also { it.parentFile?.mkdirs() }.let { f.copyTo(it, overwrite = true) }
        }
        staging.deleteRecursively()
        true
    }.getOrDefault(false)

    /** Reabre o app do zero para que tudo seja lido do backup restaurado. */
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        exitProcess(0)
    }
}
