package com.juscelino.sonivell.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Conserta tags gravadas com a codificação errada ("Whatâ€™s" → "What’s", "SÃ£o" → "São"):
 * texto UTF-8 que algum programa leu como Windows-1252. Só mexe quando o texto tem a marca
 * típica do erro e a volta para UTF-8 é válida; senão devolve o texto como veio.
 * (Uma cópia igual vive no desktop, em Basics.kt.)
 */
object TextFix {
    private val marker = Regex(
        "[ÂÃâ][\u0080-¿ŒœŠšŸŽžƒˆ˜" +
            "–—‘-„†-•…‰‹›€™]"
    )

    /** Caractere → byte do Windows-1252; os 5 bytes sem caractere voltam como eles mesmos. */
    private val toByte: Map<Char, Byte> by lazy {
        val cp1252 = Charset.forName("windows-1252")
        (0..255).associate { b ->
            val ch = String(byteArrayOf(b.toByte()), cp1252).single()
            (if (ch == '�') b.toChar() else ch) to b.toByte()
        }
    }

    fun repair(text: String?): String? {
        var current = text ?: return null
        // Às vezes o erro aconteceu duas vezes ("Ã¢â‚¬â„¢"); duas voltas resolvem.
        repeat(2) {
            if (!marker.containsMatchIn(current)) return current
            current = decodeOnce(current) ?: return current
        }
        return current
    }

    private fun decodeOnce(s: String): String? {
        val bytes = ByteArray(s.length)
        for (i in s.indices) bytes[i] = toByte[s[i]] ?: return null
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
