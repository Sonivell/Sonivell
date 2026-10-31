package com.juscelino.sonivell.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Leitura com tamanho máximo para arquivos que a pessoa escolhe (playlist .m3u, letras .lrc,
 * imagem de capa). Antes eram lidos inteiros: um arquivo de centenas de MB levava o app à falta
 * de memória (auditoria REL-05). Sem dependência do Android (readNBytes só existe no Android 13+).
 */
object BoundedRead {
    const val PLAYLIST_MAX = 5 * 1024 * 1024
    const val LYRICS_MAX = 2 * 1024 * 1024
    const val IMAGE_MAX = 30 * 1024 * 1024

    /** Até [max] bytes; maior que isso, null (o chamador trata como arquivo que não abriu). */
    fun bytes(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) return out.toByteArray()
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
    }
}
