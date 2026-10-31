package com.juscelino.sonivell.data

import java.text.Normalizer

/**
 * Busca local tolerante: ignora acentos e maiúsculas, aceita palavras fora de ordem,
 * pequenos erros de digitação e anos/décadas ("1997", "anos 80", "80s", "90's").
 */
object Search {
    private val MARKS = Regex("\\p{Mn}+")
    private val DECADE = Regex("""^(?:anos\s*)?(\d{2}|\d{4})(?:'?s)?$""")

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "").trim()

    /** Interpretação da busca: palavras e um filtro de período opcional. */
    data class Query(val words: List<String>, val years: IntRange?) {
        val isEmpty: Boolean get() = words.isEmpty() && years == null
    }

    fun parse(raw: String): Query {
        val norm = normalize(raw)
        // "anos 80" vira uma palavra só para o reconhecimento de década.
        val joined = norm.replace(Regex("""\banos\s+(\d{2})\b"""), "anos$1")
        var years: IntRange? = null
        val words = mutableListOf<String>()
        joined.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { w ->
            val m = DECADE.find(w.removePrefix("anos"))
            val isDecadeWord = w.startsWith("anos") || w.endsWith("s") || w.endsWith("'s")
            if (m != null && years == null) {
                val n = m.groupValues[1].toInt()
                when {
                    m.groupValues[1].length == 4 && n in 1900..2100 && !isDecadeWord -> { years = n..n; return@forEach }
                    m.groupValues[1].length == 4 && n in 1900..2100 -> { years = (n / 10 * 10)..(n / 10 * 10 + 9); return@forEach }
                    m.groupValues[1].length == 2 && isDecadeWord -> {
                        val start = if (n >= 30) 1900 + n / 10 * 10 else 2000 + n / 10 * 10
                        years = start..start + 9; return@forEach
                    }
                }
            }
            words += w
        }
        return Query(words, years)
    }

    /**
     * Pontua uma faixa (0 = não combina). Cada palavra precisa aparecer em algum campo:
     * inteira vale mais, início de palavra vale bem, erro pequeno ainda vale.
     */
    fun score(q: Query, t: Track): Int = score(q, index(t))

    /**
     * Os campos de uma faixa já normalizados, com o peso de cada um. Montado uma vez por faixa
     * (quando a biblioteca muda) em vez de a cada tecla digitada (auditoria PERF-01).
     */
    class Indexed(val year: Int?, val fields: List<Pair<String, Int>>)

    fun index(t: Track): Indexed = indexOf(t.year, t.title, t.artist, t.album, t.genre.orEmpty(), t.fileName.orEmpty())

    fun indexOf(year: Int?, title: String, artist: String, album: String, genre: String, fileName: String): Indexed =
        Indexed(
            year,
            listOf(
                normalize(title) to 6,
                normalize(artist) to 4,
                normalize(album) to 3,
                normalize(genre) to 2,
                normalize(fileName) to 1,
            ),
        )

    fun score(q: Query, t: Indexed): Int {
        if (q.years != null && (t.year ?: -1) !in q.years) return 0
        if (q.words.isEmpty()) return 1
        val fields = t.fields
        var total = 0
        for (w in q.words) {
            var best = 0
            for ((text, weight) in fields) {
                if (text.isEmpty()) continue
                val s = wordMatch(w, text)
                if (s > 0) best = maxOf(best, s * weight)
            }
            if (best == 0) return 0
            total += best
        }
        return total
    }

    /** Busca simples por nome (playlists, artistas): mesma tolerância, um texto só. */
    fun matchesText(q: Query, text: String): Boolean =
        q.words.isNotEmpty() && q.words.all { wordMatch(it, normalize(text)) > 0 }

    /** 3 = contém a palavra exata; 2 = começa uma palavra; 1 = parecida (erro de digitação). */
    private fun wordMatch(w: String, text: String): Int {
        if (text.contains(w)) {
            return if (text.split(' ', '-', '_', '.', '(', ')').any { it == w }) 3 else 2
        }
        if (w.length < 4) return 0
        val maxEdits = if (w.length >= 7) 2 else 1
        return if (text.split(' ', '-', '_', '.', '(', ')').any { part ->
                part.length >= w.length - maxEdits && distance(w, part.take(w.length + maxEdits)) <= maxEdits
            }) 1 else 0
    }

    /** Distância de edição (Damerau simplificada: troca de vizinhos conta 1). */
    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        // Prefixo: "metalica" bate com "metallica" mesmo se o resto do texto for maior.
        return (0..b.length).minOf { d[a.length][it] + (b.length - it).coerceAtMost(0) }.coerceAtMost(d[a.length][b.length])
    }
}
