package com.juscelino.sonivell.data

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import java.nio.charset.Charset

/**
 * Lê das tags do arquivo (via Media3) o que o MediaStore não expõe: letras e ganho
 * ReplayGain. Funciona com ID3 (MP3), comentários Vorbis (FLAC, OGG, Opus) e MP4/M4A.
 */
@OptIn(UnstableApi::class)
object TagReader {

    /** Letra embutida (USLT no ID3, LYRICS no Vorbis, ©lyr no MP4), se houver. */
    fun lyrics(metadata: List<Metadata>): String? {
        for (m in metadata) for (i in 0 until m.length()) {
            val entry = m.get(i)
            when {
                entry is TextInformationFrame && entry.id == "USLT" -> entry.values.firstOrNull()?.let { return it }
                entry is BinaryFrame && entry.id == "USLT" -> parseUslt(entry.data)?.let { return it }
                else -> vorbis(entry)?.let { (key, value) ->
                    if (key.equals("LYRICS", true) || key.equals("UNSYNCEDLYRICS", true)) return value
                }
            }
        }
        return null
    }

    /**
     * Ganho da faixa em dB. Prefere REPLAYGAIN_TRACK_GAIN; aceita R128_TRACK_GAIN do Opus
     * (Q7.8, referência −23 LUFS), convertido para a referência do ReplayGain (+5 dB).
     */
    fun trackGainDb(metadata: List<Metadata>): Float? {
        var r128: Float? = null
        for (m in metadata) for (i in 0 until m.length()) {
            val entry = m.get(i)
            if (entry is TextInformationFrame && entry.id == "TXXX" &&
                entry.description.equals("REPLAYGAIN_TRACK_GAIN", true)
            ) {
                parseDb(entry.values.firstOrNull())?.let { return it }
            }
            vorbis(entry)?.let { (key, value) ->
                when {
                    key.equals("REPLAYGAIN_TRACK_GAIN", true) -> parseDb(value)?.let { return it }
                    key.equals("R128_TRACK_GAIN", true) -> value.trim().toIntOrNull()?.let { r128 = it / 256f + 5f }
                }
            }
        }
        return r128
    }

    /** "−7.12 dB" → −7.12 */
    private fun parseDb(raw: String?): Float? =
        raw?.trim()?.replace(',', '.')?.split(' ')?.firstOrNull()?.toFloatOrNull()

    /**
     * Comentário Vorbis como par chave/valor. Lido por reflexão porque a classe mudou de
     * pacote entre versões do Media3; assim o código não quebra numa atualização.
     */
    private fun vorbis(entry: Metadata.Entry): Pair<String, String>? {
        if (entry.javaClass.simpleName != "VorbisComment") return null
        return runCatching {
            val key = entry.javaClass.getField("key").get(entry) as String
            val value = entry.javaClass.getField("value").get(entry) as String
            key to value
        }.getOrNull()
    }

    /** Quadro USLT do ID3: codificação, idioma (3 bytes), descrição terminada em zero e o texto. */
    private fun parseUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val charset: Charset = when (data[0].toInt()) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val wide = data[0].toInt() == 1 || data[0].toInt() == 2
        var i = 4 // depois da codificação e do idioma
        // Pula a descrição (terminador de 1 ou 2 bytes conforme a codificação).
        if (wide) {
            while (i + 1 < data.size && !(data[i].toInt() == 0 && data[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < data.size && data[i].toInt() != 0) i++
            i += 1
        }
        if (i >= data.size) return null
        return String(data, i, data.size - i, charset).trimEnd('\u0000').takeIf { it.isNotBlank() }
    }
}
