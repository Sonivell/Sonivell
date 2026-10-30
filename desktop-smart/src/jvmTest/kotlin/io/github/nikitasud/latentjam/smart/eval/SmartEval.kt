package io.github.nikitasud.latentjam.smart.eval

import io.github.nikitasud.latentjam.smart.DefaultSimilarityEngine
import io.github.nikitasud.latentjam.smart.EmbeddingBackend
import io.github.nikitasud.latentjam.smart.InMemoryVectorIndex
import io.github.nikitasud.latentjam.smart.NoopIndexStore
import io.github.nikitasud.latentjam.smart.SmartEngineConfig
import io.github.nikitasud.latentjam.smart.SmartHistoryEvent
import io.github.nikitasud.latentjam.smart.TrackDescriptor
import io.github.nikitasud.latentjam.smart.TrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Avaliação offline do SMART com bibliotecas sintéticas e o motor de produção.
 *
 * Roda [DefaultSimilarityEngine] com a mesma configuração do app Android (LatentJamSmartEngine) e sem
 * preditor ONNX — o app não registra preditor, então o caminho "audio-geometry" exercitado aqui é o de
 * produção. O que muda é a origem dos vetores: um backend falso devolve vetores de um espaço latente
 * sintético com gêneros e artistas conhecidos, o que dá uma referência de relevância (o gênero da faixa
 * inicial) que dados reais não têm. Similaridade nesse espaço NÃO é qualidade musical real; serve para
 * detectar regressões de coerência, repetição e diversidade, não para afinar o ouvido.
 *
 * Sem codificador de texto (é ONNX): o canal de metadados por texto não participa destas medições.
 */
internal object SmartEval {

    const val DIM = 960

    /** Mesma configuração do app Android (LatentJamSmartEngine). */
    val APP_CONFIG = SmartEngineConfig(
        embeddingDim = DIM,
        modelVersion = "eval",
        typicalityWeight = 0f,
        continueAfterExhaustion = true,
        judgedScoring = true,
        artistRunPenalty = 0.5f,
    )

    /** Uma faixa sintética: descritor para o motor, vetor latente e o gênero verdadeiro. */
    class SynthTrack(val descriptor: TrackDescriptor, val vector: FloatArray, val genre: Int)

    class Library(val tracks: List<SynthTrack>) {
        val byId: Map<TrackId, SynthTrack> = tracks.associateBy { it.descriptor.id }
        val descriptors: List<TrackDescriptor> = tracks.map { it.descriptor }
    }

    /**
     * Gêneros como centros aleatórios; cada artista um sub-centro do seu gênero; cada faixa o centro do
     * artista mais ruído. [genreSpread] e [trackNoise] controlam o quanto os gêneros se misturam.
     */
    fun library(
        seed: Int,
        genres: Int,
        artistsPerGenre: Int,
        tracksPerArtist: Int,
        genreSpread: Float = 0.6f,
        trackNoise: Float = 0.5f,
        sharedMean: Float = 1.5f,
        describe: (index: Int, genre: Int, artist: Int, n: Int) -> TrackDescriptor = ::defaultDescriptor,
    ): Library {
        val rnd = Random(seed)
        // Um componente comum a tudo, como nos embeddings reais (o motor centraliza para removê-lo).
        val mean = gaussian(rnd, sharedMean / sqrt(DIM.toFloat()))
        val out = ArrayList<SynthTrack>()
        var index = 0
        for (g in 0 until genres) {
            val genreCenter = gaussian(rnd, 1f / sqrt(DIM.toFloat()))
            for (a in 0 until artistsPerGenre) {
                val artistCenter = add(genreCenter, gaussian(rnd, genreSpread / sqrt(DIM.toFloat())))
                for (t in 0 until tracksPerArtist) {
                    val v = add(add(artistCenter, gaussian(rnd, trackNoise / sqrt(DIM.toFloat()))), mean)
                    out += SynthTrack(describe(index, g, g * artistsPerGenre + a, t), v, g)
                    index++
                }
            }
        }
        return Library(out)
    }

    fun defaultDescriptor(index: Int, genre: Int, artist: Int, n: Int): TrackDescriptor = TrackDescriptor(
        id = TrackId("$index"),
        title = "Song $index",
        artist = "Artist $artist",
        album = "Album $artist-${n / 10}",
        genre = GENRE_NAMES[genre % GENRE_NAMES.size],
        durationMs = 200_000L + (index % 7) * 10_000L,
        audioUri = "synthetic://$index",
        year = 1980 + (index % 40),
        sourceRevision = "r1",
    )

    val GENRE_NAMES = listOf("Rock", "Jazz", "Hip-Hop", "Classical", "Electronic", "Country", "Reggae", "Metal")

    class FakeBackend(private val vectors: Map<TrackId, FloatArray>) : EmbeddingBackend {
        override suspend fun loadModel(): Result<Unit> = Result.success(Unit)
        override suspend fun embed(descriptor: TrackDescriptor): Result<FloatArray> =
            vectors[descriptor.id]?.let { Result.success(it.copyOf()) }
                ?: Result.failure(IllegalStateException("sem vetor para ${descriptor.id.value}"))
        override fun close() {}
    }

    /** Motor de produção já inicializado e com a biblioteca toda analisada. */
    fun engine(lib: Library, vectors: Map<TrackId, FloatArray> = lib.tracks.associate { it.descriptor.id to it.vector }): DefaultSimilarityEngine =
        runBlocking {
            val engine = DefaultSimilarityEngine(
                backend = FakeBackend(vectors),
                index = InMemoryVectorIndex(DIM),
                store = NoopIndexStore(),
                config = APP_CONFIG,
                dispatcher = Dispatchers.Default,
            )
            engine.initialize().getOrThrow()
            engine.synchronizeLibrary(lib.descriptors, pruneMissing = true)
            for (chunk in lib.descriptors.chunked(256)) engine.indexLibrary(chunk)
            engine
        }

    class Metrics(
        val length: Int,
        /** Fração da fila no gênero da faixa inicial (precisão de relevância sintética). */
        val genrePrecision: Double,
        /** Média do cosseno centrado (espaço latente) entre a inicial e cada faixa da fila. */
        val seedSim: Double,
        /** Média do cosseno centrado entre faixas consecutivas, a inicial incluída. */
        val stepSim: Double,
        /** seedSim do último terço menos o do primeiro terço (negativo = deriva). */
        val drift: Double,
        /** Artistas distintos / tamanho da fila. */
        val artistDiversity: Double,
        /** Maior fatia da fila de um mesmo artista. */
        val maxArtistShare: Double,
        /** Maior sequência seguida do mesmo artista (a inicial conta). */
        val maxArtistRun: Int,
        /** Faixas repetidas (mesmo id) / tamanho. */
        val repeatRate: Double,
        /** Títulos normalizados repetidos do mesmo artista (a inicial conta) / tamanho. */
        val titleRepeatRate: Double,
        /** Fração da fila que não aparece no histórico. */
        val novelty: Double,
        val millis: Double,
    ) {
        override fun toString(): String =
            "len=%d prec=%.2f seed=%.3f step=%.3f drift=%+.3f artDiv=%.2f maxArt=%.2f run=%d rep=%.2f titleRep=%.2f nov=%.2f t=%.0fms"
                .format(length, genrePrecision, seedSim, stepSim, drift, artistDiversity, maxArtistShare, maxArtistRun,
                    repeatRate, titleRepeatRate, novelty, millis)
    }

    fun measure(
        lib: Library,
        seed: SynthTrack,
        queue: List<TrackId>,
        history: List<SmartHistoryEvent> = emptyList(),
        millis: Double = 0.0,
    ): Metrics {
        val center = libraryMean(lib)
        fun cos(a: FloatArray, b: FloatArray) = centeredCosine(a, b, center)
        val tracks = queue.mapNotNull(lib.byId::get)
        val n = tracks.size
        if (n == 0) return Metrics(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, millis)
        val seedSims = tracks.map { cos(seed.vector, it.vector) }
        val chain = listOf(seed) + tracks
        val steps = chain.zipWithNext { a, b -> cos(a.vector, b.vector) }
        val third = maxOf(1, n / 3)
        // Faixa sem artista não forma grupo com outra sem artista (o motor também não as agrupa).
        val artists = tracks.map { it.descriptor.artist ?: "?${it.descriptor.id.value}" }
        var run = 1
        var maxRun = 1
        val withSeed = listOf(seed.descriptor.artist ?: "") + tracks.map { it.descriptor.artist ?: "" }
        for (i in 1 until withSeed.size) {
            run = if (withSeed[i].isNotEmpty() && withSeed[i] == withSeed[i - 1]) run + 1 else 1
            maxRun = maxOf(maxRun, run)
        }
        val keys = chain.mapNotNull { t ->
            val artist = t.descriptor.artist ?: return@mapNotNull null
            val title = normalizeTitle(t.descriptor.title).ifEmpty { return@mapNotNull null }
            artist to title
        }
        val historyIds = history.map { it.trackId }.toSet()
        return Metrics(
            length = n,
            genrePrecision = tracks.count { it.genre == seed.genre }.toDouble() / n,
            seedSim = seedSims.average(),
            stepSim = steps.average(),
            drift = seedSims.takeLast(third).average() - seedSims.take(third).average(),
            artistDiversity = artists.distinct().size.toDouble() / n,
            maxArtistShare = artists.groupingBy { it }.eachCount().values.max().toDouble() / n,
            maxArtistRun = maxRun,
            repeatRate = (n - queue.distinct().size).toDouble() / n,
            titleRepeatRate = (keys.size - keys.distinct().size).toDouble() / n,
            novelty = tracks.count { it.descriptor.id !in historyIds }.toDouble() / n,
            millis = millis,
        )
    }

    /** Título sem versão: "Song (Live)", "Song - Remastered 2011" → "song". */
    fun normalizeTitle(title: String?): String =
        (title ?: "").lowercase().replace(Regex("\\s*[(\\[].*?[)\\]]"), "").replace(Regex("\\s+-\\s+.*$"), "").trim()

    fun libraryMean(lib: Library): FloatArray {
        val mean = FloatArray(DIM)
        for (t in lib.tracks) {
            val v = unit(t.vector)
            for (d in 0 until DIM) mean[d] += v[d] / lib.tracks.size
        }
        return mean
    }

    fun centeredCosine(a: FloatArray, b: FloatArray, mean: FloatArray): Double {
        val ua = unit(a)
        val ub = unit(b)
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (d in 0 until DIM) {
            val x = (ua[d] - mean[d]).toDouble()
            val y = (ub[d] - mean[d]).toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / sqrt(na * nb)
    }

    fun unit(v: FloatArray): FloatArray {
        var s = 0.0
        for (x in v) s += x.toDouble() * x
        val n = sqrt(s).toFloat()
        return if (n == 0f) v.copyOf() else FloatArray(v.size) { v[it] / n }
    }

    fun gaussian(rnd: Random, sigma: Float): FloatArray = FloatArray(DIM) {
        // Box–Muller: determinístico pela semente, igual em toda JVM.
        val u1 = rnd.nextDouble().coerceAtLeast(1e-12)
        val u2 = rnd.nextDouble()
        (sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * Math.PI * u2)).toFloat() * sigma
    }

    fun add(a: FloatArray, b: FloatArray): FloatArray = FloatArray(a.size) { a[it] + b[it] }

    fun <T> timed(block: () -> T): Pair<T, Double> {
        val t0 = System.nanoTime()
        val r = block()
        return r to (System.nanoTime() - t0) / 1e6
    }

    /** Fila nova (smartQueue) e o tempo que levou. */
    fun queue(
        engine: DefaultSimilarityEngine,
        lib: Library,
        seed: SynthTrack,
        length: Int,
        history: List<SmartHistoryEvent> = emptyList(),
    ): Pair<List<TrackId>, Double> = timed {
        runBlocking { engine.smartQueue(seed.descriptor, lib.descriptors.filter { it.id != seed.descriptor.id }, length, history) }
    }
}
