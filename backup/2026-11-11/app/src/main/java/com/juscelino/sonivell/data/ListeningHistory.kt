package com.juscelino.sonivell.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Uma escuta: do momento em que a faixa começou até a reprodução sair dela.
 * Mesmas regras do LatentJam: completa = chegou a 85% da duração;
 * pulada = não completou e foi ouvida por menos de 30 s.
 */
data class ListenEvent(
    val trackId: Long,
    val startedAtMs: Long,
    val playedMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val skipped: Boolean,
) {
    val playedFraction: Float
        get() = when {
            durationMs > 0 -> (playedMs.toFloat() / durationMs).coerceIn(0f, 1f)
            completed -> 1f
            skipped -> 0f
            else -> 0.5f
        }

    fun toLine() = "$trackId\t$startedAtMs\t$playedMs\t$durationMs\t${if (completed) 1 else 0}\t${if (skipped) 1 else 0}"

    companion object {
        const val COMPLETION_THRESHOLD = 0.85
        const val SKIP_THRESHOLD_MS = 30_000L

        fun fromLine(line: String): ListenEvent? {
            val p = line.split('\t')
            if (p.size < 6) return null
            return ListenEvent(
                trackId = p[0].toLongOrNull() ?: return null,
                startedAtMs = p[1].toLongOrNull() ?: return null,
                playedMs = p[2].toLongOrNull() ?: 0,
                durationMs = p[3].toLongOrNull() ?: 0,
                completed = p[4] == "1",
                skipped = p[5] == "1",
            )
        }
    }
}

/**
 * Histórico de escuta, só no aparelho: um arquivo de texto simples, uma escuta por linha.
 * Alimenta o SMART (o que você pula ou termina) e a tela Para você.
 *
 * Uma instância por processo ([get]): quem registra é o serviço de reprodução (funciona com a
 * tela fechada) e quem lê é o app; os dois precisam do mesmo cache e da mesma [revision].
 */
class ListeningHistory private constructor(context: Context) {

    private val file = File(context.filesDir, "historico.tsv")
    private val lock = Mutex()
    private var cache: MutableList<ListenEvent>? = null

    /** Muda a cada escuta registrada, para quem quiser recalcular algo. */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _finished = MutableSharedFlow<ListenEvent>(extraBufferCapacity = 16)

    /** Cada escuta encerrada, gravada ou não (com o histórico pausado também): o app reage a ela. */
    val finished: SharedFlow<ListenEvent> = _finished.asSharedFlow()

    fun publish(event: ListenEvent) {
        _finished.tryEmit(event)
    }

    private suspend fun events(): MutableList<ListenEvent> {
        cache?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            if (file.exists()) file.readLines().mapNotNullTo(mutableListOf(), ListenEvent::fromLine)
            else mutableListOf()
        }
        cache = loaded
        return loaded
    }

    suspend fun record(event: ListenEvent) = lock.withLock {
        val list = events()
        list += event
        withContext(Dispatchers.IO) {
            if (list.size > MAX_EVENTS + COMPACT_SLACK) {
                // Mantém só as mais recentes; reescreve o arquivo de uma vez.
                val keep = list.takeLast(MAX_EVENTS)
                list.clear()
                list += keep
                // De uma vez: um crash no meio não deixa o histórico pela metade.
                AtomicFiles.writeText(file, keep.joinToString("\n", postfix = "\n") { it.toLine() })
            } else {
                file.appendText(event.toLine() + "\n")
            }
        }
        _revision.value++
    }

    /** Apaga todo o histórico de escuta. */
    suspend fun clear() = lock.withLock {
        cache = mutableListOf()
        withContext(Dispatchers.IO) { file.delete() }
        _revision.value++
    }

    /** As [limit] escutas mais recentes, da mais antiga para a mais nova. */
    suspend fun recent(limit: Int = MAX_EVENTS): List<ListenEvent> = lock.withLock {
        events().takeLast(limit).toList()
    }

    companion object {
        const val MAX_EVENTS = 10_000
        private const val COMPACT_SLACK = 2_000

        @Volatile private var instance: ListeningHistory? = null

        fun get(context: Context): ListeningHistory =
            instance ?: synchronized(this) {
                instance ?: ListeningHistory(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * Transforma os estados do player em [ListenEvent]s: chame [observe] a cada atualização;
 * quando a reprodução sai de uma faixa, devolve a escuta dela.
 */
class ListenSessionTracker {
    private var trackId: Long? = null
    private var startedAtMs = 0L
    private var maxPositionMs = 0L
    private var lastPositionMs = 0L
    private var listenedMs = 0L
    private var durationMs = 0L

    fun observe(currentId: Long?, positionMs: Long, durationMs: Long, isPlaying: Boolean, nowMs: Long): ListenEvent? {
        if (currentId != trackId) {
            val finished = finish()
            if (currentId != null) {
                trackId = currentId
                startedAtMs = nowMs
                maxPositionMs = positionMs
                lastPositionMs = positionMs
                listenedMs = 0
                this.durationMs = durationMs
            }
            return finished
        }
        if (durationMs > 0) this.durationMs = durationMs
        // Conta só o tempo realmente ouvido: avanços pequenos e contínuos (um arrasto não conta).
        val delta = positionMs - lastPositionMs
        if (isPlaying && delta in 1..MAX_TICK_MS) listenedMs += delta
        lastPositionMs = positionMs
        if (positionMs > maxPositionMs) maxPositionMs = positionMs
        return null
    }

    fun finish(): ListenEvent? {
        val id = trackId ?: return null
        trackId = null
        val completed = durationMs > 0 && maxPositionMs >= (durationMs * ListenEvent.COMPLETION_THRESHOLD).toLong()
        return ListenEvent(
            trackId = id,
            startedAtMs = startedAtMs,
            playedMs = maxPositionMs,
            durationMs = durationMs,
            completed = completed,
            skipped = !completed && listenedMs < ListenEvent.SKIP_THRESHOLD_MS,
        )
    }

    private companion object {
        const val MAX_TICK_MS = 2_500L
    }
}
