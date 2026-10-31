package com.juscelino.sonivell.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Auditoria PERF-01: as chaves e o índice pré-calculados dão exatamente o mesmo resultado que
 * o cálculo antigo, a cada comparação e a cada tecla.
 */
class SearchIndexTest {
    private val samples = listOf(
        "Água de Beber", "agua de beber", "!Tchau radar!", "#LaMafia", "(Everything I Do)", "Ébano",
        "Zé Ramalho", "ze ramalho", "Metallica", "Ação", "acao", "Çedilha", "Über", "", "123", "  espaços",
    )

    private fun randomTitles(n: Int, seed: Int): List<String> {
        val rnd = Random(seed)
        val alphabet = "aáàãâbcçdeéêfghiíjklmnoóôõpqrstuúüvwxyzAÁÉÍÓÚÇ!#( 0123456789"
        return List(n) {
            if (rnd.nextInt(10) == 0) samples[rnd.nextInt(samples.size)]
            else String(CharArray(rnd.nextInt(1, 18)) { alphabet[rnd.nextInt(alphabet.length)] })
        }
    }

    @Test
    fun memoizedKeyIsTheSameAsSortKey() {
        val keys = SortKeys()
        (samples + randomTitles(2_000, 1)).forEach { assertEquals(sortKey(it), keys.of(it)) }
    }

    @Test
    fun sortingWithMemoizedKeysGivesTheSameOrder() {
        val titles = randomTitles(5_000, 2)
        val keys = SortKeys()
        assertEquals(titles.sortedBy { sortKey(it) }, titles.sortedBy { keys.of(it) })
        // Dois campos, como "Artista" (artista, depois álbum).
        val pairs = titles.zip(titles.shuffled(Random(3)))
        assertEquals(
            pairs.sortedWith(compareBy({ sortKey(it.first) }, { sortKey(it.second) })),
            pairs.sortedWith(compareBy({ keys.of(it.first) }, { keys.of(it.second) })),
        )
    }

    @Test
    fun indexHoldsTheSameNormalizedFieldsAndWeights() {
        val idx = Search.indexOf(1997, "Água de Beber", "Tom Jobim", "Ao Vivo", "Bossa Nova", "agua.mp3")
        assertEquals(
            listOf(
                Search.normalize("Água de Beber") to 6,
                Search.normalize("Tom Jobim") to 4,
                Search.normalize("Ao Vivo") to 3,
                Search.normalize("Bossa Nova") to 2,
                Search.normalize("agua.mp3") to 1,
            ),
            idx.fields,
        )
    }

    @Test
    fun searchBehaviourIsKept() {
        val metallica = Search.indexOf(1991, "Enter Sandman", "Metallica", "Metallica", "Metal", "enter.mp3")
        val jobim = Search.indexOf(1963, "Água de Beber", "Tom Jobim", "The Composer", "Bossa Nova", "agua.mp3")
        assertTrue(Search.score(Search.parse("metalica"), metallica) > 0) // erro de digitação
        assertTrue(Search.score(Search.parse("agua"), jobim) > 0) // sem acento
        assertEquals(0, Search.score(Search.parse("metalica"), jobim))
        assertTrue(Search.score(Search.parse("anos 90"), metallica) > 0) // década
        assertEquals(0, Search.score(Search.parse("anos 90"), jobim))
        // Título pesa mais que artista.
        val byTitle = Search.indexOf(null, "Jobim", "Outro", "", "", "")
        val byArtist = Search.indexOf(null, "Outra", "Jobim", "", "", "")
        assertTrue(Search.score(Search.parse("jobim"), byTitle) > Search.score(Search.parse("jobim"), byArtist))
    }
}
