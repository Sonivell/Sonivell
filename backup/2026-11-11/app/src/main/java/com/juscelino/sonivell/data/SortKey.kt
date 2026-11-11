package com.juscelino.sonivell.data

import java.text.Normalizer

/**
 * Chave de ordem alfabética: ignora símbolos no começo ("!Tchau radar!", "#LaMafia",
 * "(Everything I Do)") e acentos ("Água" junto do A), sem diferenciar maiúsculas.
 * (Uma cópia igual vive no desktop, em Basics.kt.)
 */
fun sortKey(text: String): String {
    val trimmed = text.trimStart { !it.isLetterOrDigit() }.ifEmpty { text }
    return Normalizer.normalize(trimmed, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase()
}

/**
 * [sortKey] com memória: cada texto é normalizado uma vez. `sortedBy { sortKey(it.title) }`
 * normaliza (NFD + regex) a cada comparação, O(n log n) vezes; com isto, uma vez por texto
 * distinto (auditoria PERF-01). Mesmo resultado, por construção.
 */
class SortKeys {
    private val memo = HashMap<String, String>()

    fun of(text: String): String = memo.getOrPut(text) { sortKey(text) }
}

/** Letra do índice A–Z de um texto; números e o resto vão para "#". */
fun indexLetter(text: String): Char {
    val c = sortKey(text).firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

private val DIACRITICS = Regex("\\p{Mn}+")
