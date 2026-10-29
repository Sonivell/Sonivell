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

/** Letra do índice A–Z de um texto; números e o resto vão para "#". */
fun indexLetter(text: String): Char {
    val c = sortKey(text).firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

private val DIACRITICS = Regex("\\p{Mn}+")
