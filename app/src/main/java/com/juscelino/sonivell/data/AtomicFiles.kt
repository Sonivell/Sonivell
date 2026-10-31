package com.juscelino.sonivell.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Gravação e leitura seguras dos arquivos do app (playlists, correções de tags, gosto, histórico).
 * Sem dependência do Android, para ser testada no JVM.
 */
object AtomicFiles {

    /**
     * Grava num temporário ao lado, força o disco (fsync) e troca de uma vez. Um crash ou o fim da
     * bateria no meio deixa o arquivo anterior inteiro, nunca um pela metade (auditoria REL-02).
     * Antes: `writeText` direto no arquivo final, ou `renameTo` com o resultado ignorado.
     */
    fun write(file: File, bytes: ByteArray) {
        val target = file.absoluteFile
        val dir = target.parentFile ?: error("Sem pasta para ${target.name}")
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun writeText(file: File, text: String) = write(file, text.toByteArray(Charsets.UTF_8))

    /**
     * Lê com [parse]; se o arquivo existe mas não se lê (truncado, corrompido), guarda uma cópia
     * ao lado (`<nome>.corrompido-<hora>`) antes de devolver null. Assim a próxima gravação, que
     * parte de uma lista vazia, não apaga para sempre o que ainda dava para recuperar à mão.
     */
    fun <T> readOrQuarantine(file: File, parse: (String) -> T): T? {
        if (!file.exists()) return null
        return runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrElse {
            runCatching { file.copyTo(File(file.parentFile, "${file.name}.corrompido-${System.currentTimeMillis()}")) }
            null
        }
    }
}
