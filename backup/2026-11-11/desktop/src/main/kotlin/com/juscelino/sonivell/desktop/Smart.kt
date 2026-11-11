package com.juscelino.sonivell.desktop

import io.github.nikitasud.latentjam.smart.EngineState
import io.github.nikitasud.latentjam.smart.JvmSmartPaths
import io.github.nikitasud.latentjam.smart.SimilarityEngine
import io.github.nikitasud.latentjam.smart.SmartEngineConfig
import io.github.nikitasud.latentjam.smart.SmartHistoryEvent
import io.github.nikitasud.latentjam.smart.TrackDescriptor
import io.github.nikitasud.latentjam.smart.TrackId
import io.github.nikitasud.latentjam.smart.di.smartEngineModule
import io.github.nikitasud.latentjam.smart.smartChainInputsModule
import io.github.nikitasud.latentjam.smart.smartEngineBackendModule
import io.github.nikitasud.latentjam.smart.text.smartTextEncoderModule
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Progresso da análise da biblioteca: [analysed] faixas já "ouvidas" pelo modelo, de [total]. */
data class SmartProgress(
    val running: Boolean = false,
    val analysed: Int = 0,
    val total: Int = 0,
    /** Vetores de metadados prontos: o SMART já funciona, mesmo antes de ouvir todo o áudio. */
    val metadataReady: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Ponte entre o Sonivell para PC e o motor SMART do LatentJam (módulo :desktop-smart).
 * A configuração e a ordem da análise são as mesmas da versão Android, para as filas saírem iguais.
 */
class SmartBridge(
    private val scope: CoroutineScope,
    private val dataDir: File = File(Dirs.data, "smart"),
) {

    private val initLock = Mutex()

    private val engine: SimilarityEngine by lazy {
        koinApplication {
            modules(
                module { single { JvmSmartPaths(dataDir) } },
                smartEngineModule,
                smartEngineBackendModule(),
                smartTextEncoderModule(),
                smartChainInputsModule(),
                module {
                    single {
                        SmartEngineConfig(
                            embeddingDim = 960,
                            modelLocator = "ml/mnv4_audio.onnx",
                            // Precisa bater com ml/embedding_version.txt.
                            modelVersion = "mnv4-960-retrieval-distill-v1-p50q4aq",
                            typicalityWeight = 0f,
                            continueAfterExhaustion = true,
                            judgedScoring = true,
                            artistRunPenalty = 0.5f,
                        )
                    }
                },
            )
        }.koin.get()
    }

    private val _progress = MutableStateFlow(SmartProgress())
    val progress: StateFlow<SmartProgress> = _progress.asStateFlow()

    /** Falta de ffmpeg ou da biblioteca nativa deixa o SMART indisponível (o resto do app segue). */
    val available: Boolean = Scanner.toolsAvailable()

    private var analysisJob: Job? = null
    private var analysedKey: Int? = null

    fun analyse(library: List<Track>) {
        if (!available) return
        val key = library.fold(library.size) { acc, t -> 31 * acc + t.id.hashCode() }
        if (key == analysedKey && (analysisJob?.isActive == true || _progress.value.analysed > 0)) return
        analysedKey = key
        analysisJob?.cancel()
        analysisJob = scope.launch(Dispatchers.Default) { runAnalysis(library.map { it.toDescriptor() }) }
    }

    private suspend fun ensureInitialized() = initLock.withLock {
        when (engine.state.value) {
            is EngineState.Ready -> Unit
            else -> engine.initialize().getOrThrow()
        }
    }

    private suspend fun runAnalysis(tracks: List<TrackDescriptor>) {
        val ids = tracks.map(TrackDescriptor::id)
        val total = ids.size
        try {
            _progress.value = SmartProgress(running = true, total = total)
            ensureInitialized()
            engine.synchronizeLibrary(library = tracks, pruneMissing = true)

            fun analysedNow(missing: Int) = (total - missing).coerceIn(0, total)
            _progress.update { it.copy(analysed = analysedNow(engine.missingFromIndex(ids))) }

            // 1) Metadados (gênero, artista, ano, idioma): rápido, e já deixa o SMART funcionando.
            tracks.chunked(METADATA_CHUNK).forEach { chunk ->
                currentCoroutineContext().ensureActive()
                engine.stageMetadataVectors(chunk)
            }
            engine.persistPendingAnalysis()
            _progress.update { it.copy(metadataReady = true) }

            // 2) Áudio: o modelo "ouve" cada faixa, espalhado pela biblioteca (ordem do LatentJam).
            var batches = 0
            analysisOrder(tracks).chunked(AUDIO_CHUNK).forEach { chunk ->
                currentCoroutineContext().ensureActive()
                if (engine.missingFromIndex(chunk.map(TrackDescriptor::id)) == 0) return@forEach
                val report = engine.stageLibraryIndex(chunk)
                if (++batches % PERSIST_EVERY == 0) engine.persistPendingAnalysis()
                _progress.update { it.copy(analysed = analysedNow(engine.missingFromIndex(ids))) }
                if (report.indexed > 0 || report.failed > 0) delay(YIELD_MS)
            }
            engine.persistPendingAnalysis()
            _progress.update { it.copy(running = false, analysed = analysedNow(engine.missingFromIndex(ids))) }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { runCatching { engine.persistPendingAnalysis() } }
            throw cancelled
        } catch (failure: Throwable) {
            System.err.println("Análise SMART interrompida: $failure")
            withContext(NonCancellable) { runCatching { engine.persistPendingAnalysis() } }
            _progress.update { it.copy(running = false, failed = true) }
        }
    }

    fun setArtistVariety(level: Int) {
        val penalties = listOf(0f, 0.25f, 0.5f, 1f, 2f)
        runCatching { engine.setArtistRunPenalty(penalties[level.coerceIn(0, penalties.lastIndex)]) }
    }

    suspend fun startQueue(seed: Track, library: List<Track>, length: Int, history: List<Listen>, byId: (Long) -> Track?): List<Long> = plan {
        engine.smartQueue(
            seed = seed.toDescriptor(),
            library = library.map { it.toDescriptor() },
            length = length,
            history = smartHistory(history, seed, byId),
        )
    }

    suspend fun continueQueue(
        seed: Track,
        library: List<Track>,
        length: Int,
        preceding: List<Long>,
        history: List<Listen>,
        byId: (Long) -> Track?,
    ): List<Long> = plan {
        engine.continueSmartQueue(
            seed = seed.toDescriptor(),
            library = library.map { it.toDescriptor() },
            length = length,
            history = smartHistory(history, seed, byId),
            precedingTrackIds = preceding.map { TrackId(it.toString()) },
        )
    }

    /** Histórico no formato do motor; a faixa tocando agora entra no fim como a intenção positiva mais recente. */
    private fun smartHistory(history: List<Listen>, current: Track, byId: (Long) -> Track?): List<SmartHistoryEvent> {
        val observed = history.map {
            val duration = byId(it.trackId)?.durationMs?.takeIf { d -> d > 0 }
            val fraction = if (duration != null) (it.listenedMs.toFloat() / duration).coerceIn(0f, 1f) else if (it.completed) 1f else 0.5f
            SmartHistoryEvent(
                trackId = TrackId(it.trackId.toString()),
                startedAtMs = it.atMs,
                playedFraction = fraction,
                completed = it.completed,
                skipped = !it.completed && fraction < 0.5f,
            )
        }
        val now = maxOf(System.currentTimeMillis(), (observed.lastOrNull()?.startedAtMs ?: Long.MIN_VALUE) + 1)
        return observed + SmartHistoryEvent(TrackId(current.id.toString()), now, 1f, completed = true, skipped = false)
    }

    private suspend fun plan(block: suspend () -> List<TrackId>): List<Long> = withContext(Dispatchers.Default) {
        try {
            ensureInitialized()
            block().mapNotNull { it.value.toLongOrNull() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            System.err.println("SMART não conseguiu montar a fila: $failure")
            emptyList()
        }
    }

    private companion object {
        const val METADATA_CHUNK = 32
        const val AUDIO_CHUNK = 8
        const val PERSIST_EVERY = 4
        const val YIELD_MS = 75L
    }
}

/** O que o Sonivell sabe de uma faixa, no formato do motor. Tags ausentes ficam null. */
internal fun Track.toDescriptor(): TrackDescriptor = TrackDescriptor(
    id = TrackId(id.toString()),
    title = title.ifBlank { null },
    artist = artist.ifBlank { null },
    album = album.ifBlank { null },
    genre = genre.ifBlank { null },
    durationMs = durationMs.takeIf { it > 0 },
    audioUri = path,
    addedAtMs = addedMs,
    folderPath = folder,
    year = year.takeIf { it > 0 },
    sourceRevision = File(path).let { "${it.length()}:${it.lastModified()}" },
    sizeBytes = File(path).length().takeIf { it > 0 },
    fileName = File(path).name,
    trackNumber = trackNo.takeIf { it > 0 },
    albumArtist = albumArtist.ifBlank { null },
)

/** Ordem da análise de áudio: embaralhamento determinístico por id (o mesmo do LatentJam). */
internal fun analysisOrder(tracks: List<TrackDescriptor>): List<TrackDescriptor> =
    tracks.sortedWith(compareBy<TrackDescriptor> { analysisRank(it.id) }.thenBy { it.id.value })

private fun analysisRank(id: TrackId): ULong {
    var hash = 0xcbf29ce484222325uL
    for (byte in ("latentjam-analysis-order-v1\u0000" + id.value).encodeToByteArray()) {
        hash = (hash xor (byte.toULong() and 0xFFuL)) * 0x100000001b3uL
    }
    hash = (hash xor (hash shr 33)) * 0xFF51AFD7ED558CCDuL
    hash = (hash xor (hash shr 33)) * 0xC4CEB9FE1A85EC53uL
    return hash xor (hash shr 33)
}
