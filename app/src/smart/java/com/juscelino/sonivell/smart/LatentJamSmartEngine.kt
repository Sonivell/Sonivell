package com.juscelino.sonivell.smart

import android.content.Context
import android.util.Log
import com.juscelino.sonivell.data.Track
import io.github.nikitasud.latentjam.smart.EngineState
import io.github.nikitasud.latentjam.smart.SimilarityEngine
import io.github.nikitasud.latentjam.smart.SmartEngineConfig
import io.github.nikitasud.latentjam.smart.TrackDescriptor
import io.github.nikitasud.latentjam.smart.TrackId
import io.github.nikitasud.latentjam.smart.di.smartEngineModule
import io.github.nikitasud.latentjam.smart.smartChainInputsModule
import io.github.nikitasud.latentjam.smart.smartEngineBackendModule
import io.github.nikitasud.latentjam.smart.text.smartTextEncoderModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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

/**
 * Ponte entre o Sonivell e o motor SMART do LatentJam (core/smart).
 *
 * A configuração e a ordem da análise seguem as do próprio LatentJam (AppGraph.kt), para
 * que as filas saiam iguais às dele.
 */
internal class LatentJamSmartEngine private constructor(context: Context) : SmartEngine {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val initLock = Mutex()

    // Koin "escopado": não usa o startKoin global, então nada mais do app depende de Koin.
    private val engine: SimilarityEngine by lazy {
        koinApplication {
            modules(
                module { single<Context> { appContext } },
                smartEngineModule,
                smartEngineBackendModule(),
                smartTextEncoderModule(),
                smartChainInputsModule(),
                // Listado por último: substitui o SmartEngineConfig padrão do smartEngineModule.
                module {
                    single {
                        SmartEngineConfig(
                            embeddingDim = 960,
                            modelLocator = "ml/mnv4_audio.onnx",
                            // Precisa bater com assets/ml/embedding_version.txt.
                            modelVersion = "mnv4-960-retrieval-distill-v1-p50q4aq",
                            typicalityWeight = 0f,
                            continueAfterExhaustion = true,
                            judgedScoring = true,
                            // Variedade de artistas "média", o padrão do LatentJam.
                            artistRunPenalty = 0.5f,
                        )
                    }
                },
            )
        }.koin.get()
    }

    private val _progress = MutableStateFlow(SmartProgress())
    override val progress: StateFlow<SmartProgress> = _progress.asStateFlow()
    override val isAvailable = true

    private var analysisJob: Job? = null
    private var analysedKey: Int? = null

    override fun analyse(library: List<Track>) {
        val key = library.fold(library.size) { acc, t -> 31 * acc + (t.id.hashCode() xor (t.dateModified ?: 0).hashCode()) }
        if (key == analysedKey && (analysisJob?.isActive == true || _progress.value.analysed > 0)) return
        analysedKey = key
        analysisJob?.cancel()
        analysisJob = scope.launch { runAnalysis(library.map { it.toDescriptor() }) }
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

            // 2) Áudio: o modelo "ouve" cada faixa. Espalhado pela biblioteca, para o SMART ficar
            //    representativo bem antes de terminar uma biblioteca grande.
            var batches = 0
            analysisOrder(tracks).chunked(AUDIO_CHUNK).forEach { chunk ->
                currentCoroutineContext().ensureActive()
                if (engine.missingFromIndex(chunk.map(TrackDescriptor::id)) == 0) return@forEach
                val report = engine.stageLibraryIndex(chunk)
                if (++batches % PERSIST_EVERY == 0) engine.persistPendingAnalysis()
                _progress.update { it.copy(analysed = analysedNow(engine.missingFromIndex(ids))) }
                // Pequena pausa: deixa a interface fluida e o celular esfriar entre lotes.
                if (report.indexed > 0 || report.failed > 0) delay(YIELD_MS)
            }
            engine.persistPendingAnalysis()
            _progress.update { it.copy(running = false, analysed = analysedNow(engine.missingFromIndex(ids))) }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { runCatching { engine.persistPendingAnalysis() } }
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Análise SMART interrompida", failure)
            withContext(NonCancellable) { runCatching { engine.persistPendingAnalysis() } }
            _progress.update { it.copy(running = false, failed = true) }
        }
    }

    override suspend fun startQueue(seed: Track, library: List<Track>, length: Int): List<Long> =
        plan {
            engine.smartQueue(
                seed = seed.toDescriptor(),
                library = library.map { it.toDescriptor() },
                length = length,
            )
        }

    override suspend fun continueQueue(
        seed: Track,
        library: List<Track>,
        length: Int,
        preceding: List<Long>,
    ): List<Long> = plan {
        engine.continueSmartQueue(
            seed = seed.toDescriptor(),
            library = library.map { it.toDescriptor() },
            length = length,
            precedingTrackIds = preceding.map { TrackId(it.toString()) },
        )
    }

    private suspend fun plan(block: suspend () -> List<TrackId>): List<Long> = withContext(Dispatchers.Default) {
        try {
            ensureInitialized()
            block().mapNotNull { it.value.toLongOrNull() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "SMART não conseguiu montar a fila", failure)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "SonivellSmart"
        private const val METADATA_CHUNK = 32
        private const val AUDIO_CHUNK = 8
        private const val PERSIST_EVERY = 4
        private const val YIELD_MS = 75L

        @Volatile private var instance: LatentJamSmartEngine? = null

        fun get(context: Context): LatentJamSmartEngine =
            instance ?: synchronized(this) {
                instance ?: LatentJamSmartEngine(context).also { instance = it }
            }
    }
}

/** O que o Sonivell sabe de uma faixa, no formato do motor. Tags ausentes ficam null (nunca "Desconhecido"). */
internal fun Track.toDescriptor(): TrackDescriptor = TrackDescriptor(
    id = TrackId(id.toString()),
    title = rawTitle,
    artist = rawArtist,
    album = rawAlbum,
    genre = genre,
    durationMs = durationMs.takeIf { it > 0 },
    audioUri = uri.toString(),
    artworkUri = artUri.toString(),
    addedAtMs = dateAdded * 1000,
    folderPath = folder,
    year = year,
    sourceRevision = if (sizeBytes != null || dateModified != null) "${sizeBytes ?: 0}:${dateModified ?: 0}" else null,
    sizeBytes = sizeBytes,
    fileName = fileName,
    trackNumber = trackNumber,
    discNumber = discNumber,
    albumArtist = albumArtist,
)

/**
 * Ordem da análise de áudio: um embaralhamento determinístico por id (o mesmo do LatentJam),
 * para que uma análise pela metade já cubra a biblioteca toda de forma representativa.
 */
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
