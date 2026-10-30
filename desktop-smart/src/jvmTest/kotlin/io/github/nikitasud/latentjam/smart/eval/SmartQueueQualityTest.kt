package io.github.nikitasud.latentjam.smart.eval

import io.github.nikitasud.latentjam.smart.SmartHistoryEvent
import io.github.nikitasud.latentjam.smart.TrackDescriptor
import io.github.nikitasud.latentjam.smart.TrackId
import io.github.nikitasud.latentjam.smart.eval.SmartEval.SynthTrack
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cenários controlados de qualidade da fila SMART (ver [SmartEval] para o que estas métricas medem e o
 * que não medem). Os limites são portões de regressão com folga sobre o comportamento medido, não metas
 * de qualidade musical: falhar aqui significa que uma mudança piorou algo mensurável.
 */
class SmartQueueQualityTest {

    private val length = 30

    private fun report(name: String, m: SmartEval.Metrics) = println("SMART-EVAL [$name] $m")

    /** Faixas iniciais espalhadas pela biblioteca, sempre as mesmas. */
    private fun seeds(lib: SmartEval.Library, count: Int = 6): List<SynthTrack> =
        List(count) { lib.tracks[(it * 7919L % lib.tracks.size).toInt()] }

    private fun averaged(name: String, lib: SmartEval.Library, history: List<SmartHistoryEvent> = emptyList()): SmartEval.Metrics {
        val engine = SmartEval.engine(lib)
        val all = seeds(lib).map { seed ->
            val (q, ms) = SmartEval.queue(engine, lib, seed, length, history)
            SmartEval.measure(lib, seed, q, history, ms)
        }
        val m = SmartEval.Metrics(
            length = all.minOf { it.length },
            genrePrecision = all.map { it.genrePrecision }.average(),
            seedSim = all.map { it.seedSim }.average(),
            stepSim = all.map { it.stepSim }.average(),
            drift = all.map { it.drift }.average(),
            artistDiversity = all.map { it.artistDiversity }.average(),
            maxArtistShare = all.maxOf { it.maxArtistShare },
            maxArtistRun = all.maxOf { it.maxArtistRun },
            repeatRate = all.maxOf { it.repeatRate },
            titleRepeatRate = all.maxOf { it.titleRepeatRate },
            novelty = all.map { it.novelty }.average(),
            millis = all.map { it.millis }.average(),
        )
        report(name, m)
        return m
    }

    @Test
    fun separatedGenres() {
        val lib = SmartEval.library(seed = 1, genres = 6, artistsPerGenre = 8, tracksPerArtist = 10, genreSpread = 0.5f)
        val m = averaged("1-generos-separados", lib)
        assertEquals(length, m.length)
        assertEquals(0.0, m.repeatRate)
        assertTrue(m.genrePrecision >= 0.9, "precisão de gênero ${m.genrePrecision}")
        assertTrue(m.maxArtistRun <= 3, "sequência de artista ${m.maxArtistRun}")
    }

    /**
     * O ajuste "Variedade de artistas" (0, 0,25, 0,5, 1, 2 — LatentJamSmartEngine.setArtistVariety) precisa
     * mudar a fila de fato. Antes da correção do JourneySequencer, 0, 0,25 e 0,5 davam a mesma sequência
     * de 7 faixas seguidas do artista da inicial.
     */
    @Test
    fun artistVarietySettingIsRespected() {
        val lib = SmartEval.library(seed = 1, genres = 6, artistsPerGenre = 8, tracksPerArtist = 10, genreSpread = 0.5f)
        val runs = listOf(0f, 0.25f, 0.5f, 1f, 2f).map { penalty ->
            val engine = SmartEval.engine(lib)
            engine.setArtistRunPenalty(penalty)
            val longest = seeds(lib).map { seed ->
                val (q, _) = SmartEval.queue(engine, lib, seed, length)
                SmartEval.measure(lib, seed, q).maxArtistRun
            }
            println("SMART-EVAL [variedade=$penalty] maior sequência por inicial: $longest")
            longest.average()
        }
        for (i in 1 until runs.size) assertTrue(runs[i] <= runs[i - 1] + 1e-9, "mais variedade, sequências mais longas: $runs")
        assertTrue(runs[2] < runs[0], "o padrão (0,5) não difere de 'sem variedade': $runs")
    }

    @Test
    fun mixedGenres() {
        val lib = SmartEval.library(seed = 2, genres = 6, artistsPerGenre = 8, tracksPerArtist = 10, genreSpread = 1.4f, trackNoise = 0.9f)
        val m = averaged("2-generos-misturados", lib)
        assertEquals(length, m.length)
        assertEquals(0.0, m.repeatRate)
        // Aleatório daria ~1/6 = 0.17.
        assertTrue(m.genrePrecision >= 0.4, "precisão de gênero ${m.genrePrecision}")
    }

    @Test
    fun dominatedBySingleArtist() {
        // 60% da biblioteca é um artista só (gênero 0); o resto se divide entre 20 artistas.
        val lib = SmartEval.library(seed = 3, genres = 4, artistsPerGenre = 6, tracksPerArtist = 10) { i, g, a, n ->
            val artist = if (i % 10 < 6) "Big Band" else "Artist $a"
            SmartEval.defaultDescriptor(i, g, a, n).copy(artist = artist)
        }
        val m = averaged("3-um-artista-domina", lib)
        assertEquals(length, m.length)
        assertEquals(0.0, m.repeatRate)
        // Teto de 6 faixas por artista na fila (CHAIN_ARTIST_QUEUE_CAP), a inicial fora da conta.
        assertTrue(m.maxArtistShare <= 6.0 / length + 1e-9, "fatia máxima de artista ${m.maxArtistShare}")
    }

    @Test
    fun fewCandidates() {
        val lib = SmartEval.library(seed = 4, genres = 2, artistsPerGenre = 3, tracksPerArtist = 5)
        val engine = SmartEval.engine(lib)
        val seed = lib.tracks[0]
        val (q, ms) = SmartEval.queue(engine, lib, seed, length)
        val m = SmartEval.measure(lib, seed, q, millis = ms)
        report("4-poucos-candidatos", m)
        assertTrue(q.isNotEmpty())
        assertTrue(seed.descriptor.id !in q, "a inicial não volta na fila")
        assertEquals(q.size, q.distinct().size, "sem repetição de faixa")
    }

    @Test
    fun incompleteMetadata() {
        // Metade sem artista, gênero ou título: não pode agrupar faixas sem tag como um "artista vazio".
        val lib = SmartEval.library(seed = 5, genres = 5, artistsPerGenre = 6, tracksPerArtist = 10) { i, g, a, n ->
            val d = SmartEval.defaultDescriptor(i, g, a, n)
            if (i % 2 == 0) d.copy(title = null, artist = null, album = null, genre = null, year = null) else d
        }
        val m = averaged("5-metadados-incompletos", lib)
        assertEquals(length, m.length)
        assertEquals(0.0, m.repeatRate)
        assertTrue(m.genrePrecision >= 0.8, "precisão de gênero ${m.genrePrecision}")
    }

    @Test
    fun invalidVectorsAreRejected() {
        val lib = SmartEval.library(seed = 6, genres = 4, artistsPerGenre = 5, tracksPerArtist = 10)
        val bad = HashMap<TrackId, FloatArray>()
        lib.tracks.forEachIndexed { i, t ->
            bad[t.descriptor.id] = when (i % 10) {
                1 -> t.vector.copyOf().also { it[3] = Float.NaN }
                2 -> FloatArray(SmartEval.DIM - 1) { 0.1f }
                3 -> FloatArray(SmartEval.DIM)
                4 -> t.vector.copyOf().also { it[5] = Float.POSITIVE_INFINITY }
                else -> t.vector
            }
        }
        val engine = SmartEval.engine(lib, bad)
        val invalid = lib.tracks.filterIndexed { i, _ -> i % 10 in 1..4 }.map { it.descriptor.id }.toSet()
        val seed = lib.tracks[0]
        val (q, ms) = SmartEval.queue(engine, lib, seed, length)
        report("6-vetores-invalidos", SmartEval.measure(lib, seed, q, millis = ms))
        assertEquals(length, q.size)
        assertTrue(q.none { it in invalid }, "faixa com vetor inválido entrou na fila: ${q.filter { it in invalid }}")
        runBlocking {
            for (id in invalid) assertEquals(null, engine.embedding(id), "vetor inválido indexado: ${id.value}")
        }
    }

    @Test
    fun versionsAndSimilarTitles() {
        // Cada música em 3 versões do mesmo artista: original, ao vivo e remaster (vetores quase iguais).
        val base = SmartEval.library(seed = 7, genres = 3, artistsPerGenre = 5, tracksPerArtist = 8)
        val suffix = listOf("", " (Live)", " - Remastered 2011")
        val tracks = base.tracks.flatMapIndexed { i, t ->
            suffix.mapIndexed { v, s ->
                SynthTrack(
                    t.descriptor.copy(id = TrackId("${i}v$v"), title = "Song $i$s", audioUri = "synthetic://${i}v$v"),
                    SmartEval.add(t.vector, SmartEval.gaussian(kotlin.random.Random(i * 3 + v), 0.002f)),
                    t.genre,
                )
            }
        }
        val lib = SmartEval.Library(tracks)
        val m = averaged("7-versoes-e-titulos", lib)
        assertEquals(length, m.length)
        assertEquals(0.0, m.titleRepeatRate, "a mesma música em outra versão voltou na fila")
    }

    @Test
    fun emptyAndBiasedHistory() {
        val lib = SmartEval.library(seed = 8, genres = 5, artistsPerGenre = 8, tracksPerArtist = 10)
        val engine = SmartEval.engine(lib)
        val seed = lib.tracks[17]
        val (cold, _) = SmartEval.queue(engine, lib, seed, length)
        val (again, _) = SmartEval.queue(engine, lib, seed, length)
        assertEquals(cold, again, "mesma entrada, mesma fila")
        report("8-historico-vazio", SmartEval.measure(lib, seed, cold))

        // Histórico enviesado: uma faixa vizinha ouvida até o fim 40 vezes ao longo de 40 dias.
        val now = 1_800_000_000_000L
        val favourite = TrackId(cold.first().value)
        val biased = List(40) { k ->
            SmartHistoryEvent(favourite, now - (40 - k) * 86_400_000L, 1f, completed = true, skipped = false)
        } + SmartHistoryEvent(seed.descriptor.id, now, 1f, completed = true, skipped = false)
        val (withHistory, _) = SmartEval.queue(engine, lib, seed, length, biased)
        val m = SmartEval.measure(lib, seed, withHistory, biased)
        report("9-historico-enviesado", m)
        assertEquals(length, withHistory.size)
        assertEquals(withHistory.size, withHistory.distinct().size)
        // O histórico não pode transformar a fila numa bolha da faixa repetida nem tirar a relevância.
        assertTrue(m.novelty >= (length - 1).toDouble() / length - 1e-9, "novidade ${m.novelty}")
        assertTrue(m.genrePrecision >= 0.8, "precisão de gênero ${m.genrePrecision}")
    }

    @Test
    fun atypicalSeed() {
        val lib = SmartEval.library(seed = 9, genres = 5, artistsPerGenre = 6, tracksPerArtist = 10)
        // Uma faixa longe de todos os gêneros: o motor ainda devolve uma fila completa e sem repetições.
        val odd = SynthTrack(
            TrackDescriptor(TrackId("odd"), title = "Odd", artist = "Loner", genre = "Noise", audioUri = "synthetic://odd", sourceRevision = "r1"),
            SmartEval.add(SmartEval.gaussian(kotlin.random.Random(99), 3f / kotlin.math.sqrt(SmartEval.DIM.toFloat())), SmartEval.libraryMean(lib)),
            genre = -1,
        )
        val all = SmartEval.Library(lib.tracks + odd)
        val engine = SmartEval.engine(all)
        val (q, ms) = SmartEval.queue(engine, all, odd, length)
        val m = SmartEval.measure(all, odd, q, millis = ms)
        report("10-inicial-atipica", m)
        assertEquals(length, q.size)
        assertEquals(q.size, q.distinct().size)
        // Sem gênero próprio, a fila deve ao menos se manter coerente de uma faixa para a outra.
        assertTrue(m.stepSim > m.seedSim, "coerência local ${m.stepSim} vs à inicial ${m.seedSim}")
    }

    @Test
    fun longQueueKeepsCoherence() {
        val lib = SmartEval.library(seed = 10, genres = 6, artistsPerGenre = 10, tracksPerArtist = 12, genreSpread = 0.8f)
        val engine = SmartEval.engine(lib)
        val seed = lib.tracks[5]
        val (q, ms) = SmartEval.queue(engine, lib, seed, 60)
        val m = SmartEval.measure(lib, seed, q, millis = ms)
        report("11-fila-longa-60", m)
        assertEquals(60, q.size)
        assertEquals(0.0, m.repeatRate)
        // 120 faixas por gênero: 60 cabem no gênero da inicial.
        assertTrue(m.genrePrecision >= 0.85, "precisão de gênero ${m.genrePrecision}")
    }
}
