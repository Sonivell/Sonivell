package io.github.nikitasud.latentjam.smart.eval

import io.github.nikitasud.latentjam.smart.InMemoryVectorIndex
import io.github.nikitasud.latentjam.smart.TrackId
import io.github.nikitasud.latentjam.smart.chain.ChainTuning
import io.github.nikitasud.latentjam.smart.chain.JourneySequencer
import io.github.nikitasud.latentjam.smart.chain.PredictorRuntime
import io.github.nikitasud.latentjam.smart.chain.Rerank
import io.github.nikitasud.latentjam.smart.chain.SmartChain
import io.github.nikitasud.latentjam.smart.chain.SmartSnapshot
import io.github.nikitasud.latentjam.smart.chain.SmartTrack
import io.github.nikitasud.latentjam.smart.chain.TrackMeta
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regressões das correções numéricas e de ordenação do SMART. */
class EngineMathTest {

    // ---- Índice vetorial ----

    @Test
    fun indexNormalizesHugeFiniteVectorsInsteadOfZeroingThem() {
        // Finito (o motor aceita: a norma em Double é finita), mas a soma de quadrados em Float estoura.
        val index = InMemoryVectorIndex(4)
        index.upsert(TrackId("big"), floatArrayOf(3e19f, 0f, 4e19f, 0f))
        val v = index.vector(TrackId("big"))!!
        assertEquals(0.6f, v[0], 1e-6f)
        assertEquals(0.8f, v[2], 1e-6f)
        val hit = index.nearest(floatArrayOf(3f, 0f, 4f, 0f), 1, emptySet()).single()
        assertEquals(1f, hit.score, 1e-5f)
    }

    @Test
    fun indexNormalizesTinyVectors() {
        val index = InMemoryVectorIndex(2)
        index.upsert(TrackId("tiny"), floatArrayOf(3e-22f, 4e-22f))
        val v = index.vector(TrackId("tiny"))!!
        assertEquals(1f, sqrt(v[0] * v[0] + v[1] * v[1]), 1e-5f)
    }

    @Test
    fun indexKeepsExactFloatsForOrdinaryVectors() {
        // O caminho Float de sempre: mesma aritmética, bit a bit, para vetores comuns.
        val rnd = Random(3)
        val raw = FloatArray(960) { rnd.nextFloat() - 0.5f }
        var sum = 0f
        for (c in raw) sum += c * c
        val norm = kotlin.math.sqrt(sum)
        val expected = FloatArray(raw.size) { raw[it] / norm }
        val index = InMemoryVectorIndex(960)
        index.upsert(TrackId("a"), raw)
        assertTrue(expected.contentEquals(index.vector(TrackId("a"))!!))
    }

    @Test
    fun indexRejectsInvalidInputAndOrdersTiesStably() {
        val index = InMemoryVectorIndex(2)
        assertTrue(runCatching { index.upsert(TrackId("nan"), floatArrayOf(Float.NaN, 1f)) }.isFailure)
        assertTrue(runCatching { index.upsert(TrackId("dim"), floatArrayOf(1f)) }.isFailure)
        index.upsert(TrackId("z"), floatArrayOf(0f, 0f))
        index.upsert(TrackId("a"), floatArrayOf(1f, 0f))
        index.upsert(TrackId("b"), floatArrayOf(2f, 0f))
        index.upsert(TrackId("c"), floatArrayOf(0f, 1f))
        val hits = index.nearest(floatArrayOf(1f, 0f), 10, setOf(TrackId("c")))
        // a e b empatam (mesma direção): a ordem de inserção decide; o vetor nulo pontua 0; c fica de fora.
        assertEquals(listOf("a", "b", "z"), hits.map { it.trackId.value })
        assertEquals(0f, hits.last().score)
    }

    // ---- Penalidade de hub: a versão em lote é idêntica à de um par por vez ----

    @Test
    fun hubPenaltyMatchesOnePairReferenceBitForBit() {
        for ((n, anchors) in listOf(40 to SmartSnapshot.HUB_ANCHOR_SAMPLE, 37 to 16, 7 to 3, 1 to 1024, 2 to 1024)) {
            val centered = centeredRows(n, seed = n)
            val expected = referenceHubPenalty(centered, n, anchors)
            val actual = SmartSnapshot.computeHubPenalty(centered, n, anchors)
            assertTrue(expected.contentEquals(actual), "n=$n anchors=$anchors")
        }
    }

    // ---- JourneySequencer ----

    @Test
    fun sequencerNoLongerRegroupsWhatTheChainAlternated() {
        // Biblioteca em que o próprio artista é o vizinho mais próximo: o caso que reagrupava a fila.
        val lib = SmartEval.library(seed = 1, genres = 6, artistsPerGenre = 8, tracksPerArtist = 10, genreSpread = 0.5f)
        val snapshot = SmartSnapshot.build(lib.tracks.map {
            SmartTrack(it.descriptor.id, it.vector, meta = TrackMeta(it.descriptor.title, it.descriptor.artist, it.descriptor.album, it.descriptor.genre, it.descriptor.year))
        })!!
        val artistOf = { row: Int -> snapshot.tracks[row].meta.artistKey }
        for (seedIndex in listOf(0, 77, 222, 401)) {
            val seed = lib.tracks[seedIndex].descriptor.id
            val seedRow = snapshot.rowOf(seed)
            val chain = SmartChain(snapshot, null, tuning = judged(0.5f)).build(seed, 30, PredictorRuntime.timeFeatures(20, 3))
            val before = JourneySequencer.longestArtistRun(listOf(seedRow) + chain.rows, artistOf)
            val ordered = JourneySequencer.order(snapshot, chain.rows, sameArtistCost = 0.5f, before = seedRow)
            assertEquals(chain.rows.sorted(), ordered.sorted(), "o reordenamento só permuta")
            assertEquals(chain.rows.first(), ordered.first(), "a primeira recomendação fica")
            val after = JourneySequencer.longestArtistRun(listOf(seedRow) + ordered, artistOf)
            assertTrue(after <= before, "seed=$seedIndex: sequência $before -> $after")
            // Continuação a partir da faixa em reprodução: a mesma garantia.
            val continued = JourneySequencer.order(snapshot, chain.rows, from = seedRow, sameArtistCost = 0.5f)
            assertTrue(JourneySequencer.longestArtistRun(listOf(seedRow) + continued, artistOf) <= before)
        }
    }

    @Test
    fun sequencerWithoutArtistCostIsUnchangedByTheGuard() {
        // Com custo 0 (ajuste "sem variedade") o caminho é o original: sem a guarda e sem `before`.
        val lib = SmartEval.library(seed = 2, genres = 3, artistsPerGenre = 4, tracksPerArtist = 10)
        val snapshot = SmartSnapshot.build(lib.tracks.map {
            SmartTrack(it.descriptor.id, it.vector, meta = TrackMeta(it.descriptor.title, it.descriptor.artist, null, null, null))
        })!!
        val rows = (0 until 30).map { (it * 37) % snapshot.size }.distinct()
        assertEquals(
            JourneySequencer.order(snapshot, rows),
            JourneySequencer.order(snapshot, rows, before = 5),
        )
    }

    @Test
    fun longestRunIgnoresUntaggedArtists() {
        val keys = listOf("a", "a", "", "", "", "b", "a", "a", "a")
        assertEquals(3, JourneySequencer.longestArtistRun(keys.indices.toList()) { keys[it] })
    }

    private fun judged(penalty: Float) = ChainTuning(
        continueAfterExhaustion = true,
        neighbourhoodBonus = 0f,
        rerankWeights = Rerank.JUDGED_WEIGHTS,
        soundFloor = Rerank.SOUND_FLOOR,
        artistRunPenalty = penalty,
        companionPoints = Rerank.COMPANION_POINTS,
    )

    private fun centeredRows(n: Int, seed: Int): FloatArray {
        val rnd = Random(seed)
        val dim = SmartSnapshot.AUDIO_DIM
        val out = FloatArray(n * dim) { rnd.nextFloat() - 0.5f }
        for (i in 0 until n) {
            var s = 0.0
            for (d in 0 until dim) s += out[i * dim + d].toDouble() * out[i * dim + d]
            val scale = (1.0 / sqrt(s)).toFloat()
            for (d in 0 until dim) out[i * dim + d] *= scale
        }
        return out
    }

    /** A implementação anterior, um par por vez, copiada como referência. */
    private fun referenceHubPenalty(centered: FloatArray, n: Int, anchorLimit: Int): FloatArray {
        val dim = SmartSnapshot.AUDIO_DIM
        val penalty = FloatArray(n)
        val anchors: IntArray? = if (n > anchorLimit) {
            val perm = IntArray(n) { it }
            val rng = Random(0x1A7E47L)
            for (i in n - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp
            }
            perm.copyOf(anchorLimit)
        } else null
        val candidates = anchors?.size ?: n
        val k = SmartSnapshot.HUB_TOPK.coerceAtMost(candidates - 1)
        if (k <= 0) return penalty
        val top = FloatArray(k)
        for (i in 0 until n) {
            top.fill(Float.NEGATIVE_INFINITY)
            val baseI = i * dim
            for (c in 0 until candidates) {
                val j = anchors?.get(c) ?: c
                if (j == i) continue
                var dot = 0f
                val baseJ = j * dim
                for (d in 0 until dim) dot += centered[baseI + d] * centered[baseJ + d]
                var minIdx = 0
                for (t in 1 until k) if (top[t] < top[minIdx]) minIdx = t
                if (dot > top[minIdx]) top[minIdx] = dot
            }
            var sum = 0f
            for (t in 0 until k) sum += top[t]
            penalty[i] = sum / k
        }
        var mean = 0f
        for (v in penalty) mean += v
        mean /= n
        for (i in 0 until n) penalty[i] -= mean
        return penalty
    }
}
