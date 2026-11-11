package com.juscelino.sonivell.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Calendar
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * O que o SMART aprendeu do seu gosto, treinado no próprio celular com o seu histórico.
 *
 * Duas peças, sobre as "impressões digitais" de som que o motor do LatentJam já calcula:
 * - **Perfil de gosto**: a média do som do que você ouve até o fim (e curte), menos o que
 *   você pula. Um perfil geral e um por período do dia (madrugada, manhã, tarde, noite).
 * - **Previsor de pulo**: uma regressão logística que estima a chance de você ouvir (e não
 *   pular) uma faixa, dado o som dela, o perfil e a faixa anterior.
 *
 * Nada sai do aparelho. O modelo é pequeno (alguns KB) e treina em segundos.
 */
class TasteModel(private val file: File) {

    data class Status(
        val trained: Boolean = false,
        /** Escutas usadas no último treino. */
        val examples: Int = 0,
        val positives: Int = 0,
        val negatives: Int = 0,
        val trainedAtMs: Long = 0L,
        /** Acerto do previsor nas escutas mais recentes, que ficaram de fora do treino (0..1); null sem dados para medir. */
        val accuracy: Float? = null,
        /** Só o perfil de gosto (ainda sem pulos suficientes para o previsor). */
        val profileOnly: Boolean = false,
    )

    private class Model(
        val dim: Int,
        val centroid: FloatArray,
        val dayparts: Array<FloatArray?>,
        /** Pesos do previsor: [dim] do som + [EXTRA] de contexto; null = só perfil. */
        val weights: FloatArray?,
        val bias: Float,
        val status: Status,
    )

    private val lock = Mutex()
    @Volatile private var model: Model? = runCatching { load() }.getOrNull()
    private val _status = MutableStateFlow(model?.status ?: Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Quantas escutas o último treino viu; o app retreina quando chegam novas o bastante. */
    val examplesSeen: Int get() = model?.status?.examples ?: 0

    fun forget() {
        model = null
        file.delete()
        _status.value = Status()
    }

    /**
     * Treina de novo com [events] (da mais antiga para a mais nova). [vectors] devolve a
     * impressão de som de cada faixa (as que ainda não foram ouvidas pelo modelo ficam de fora).
     */
    suspend fun train(
        events: List<ListenEvent>,
        favorites: Set<Long>,
        vectors: suspend (List<Long>) -> Map<Long, FloatArray>,
        nowMs: Long = System.currentTimeMillis(),
    ) = lock.withLock {
        val ids = (events.map { it.trackId } + favorites).distinct()
        val vecs = vectors(ids).mapValues { normalized(it.value) }.filterValues { it.isNotEmpty() }
        if (vecs.isEmpty()) return@withLock
        val dim = vecs.values.first().size

        // Exemplos rotulados: ouviu até o fim = gostou; pulou cedo = não gostou.
        data class Ex(val v: FloatArray, val prev: FloatArray?, val hour: Int, val label: Int, val weight: Float, val at: Long)
        val sorted = events.sortedBy { it.startedAtMs }
        val cal = Calendar.getInstance()
        val examples = ArrayList<Ex>()
        var prevEvent: ListenEvent? = null
        for (e in sorted) {
            val v = vecs[e.trackId]
            val label = when {
                e.skipped && e.playedFraction < 0.5f -> 0
                e.completed && !e.skipped -> 1
                e.playedFraction >= 0.8f -> 1
                else -> -1
            }
            if (v != null && v.size == dim && label >= 0) {
                cal.timeInMillis = e.startedAtMs
                val prev = prevEvent?.takeIf { e.startedAtMs - it.startedAtMs < 30 * MINUTE }?.let { vecs[it.trackId] }
                val ageDays = (nowMs - e.startedAtMs) / DAY.toFloat()
                val recency = max(0.25f, exp(-ageDays / 90f))
                examples += Ex(v, prev, cal.get(Calendar.HOUR_OF_DAY), label, recency, e.startedAtMs)
            }
            prevEvent = e
        }
        // Curtidas contam como gostar, mesmo sem escuta recente.
        favorites.forEach { id -> vecs[id]?.takeIf { it.size == dim }?.let { examples += Ex(it, null, -1, 1, 0.6f, 0L) } }

        val pos = examples.filter { it.label == 1 }
        val neg = examples.filter { it.label == 0 }
        if (pos.size < MIN_POSITIVES) return@withLock

        // Perfil: média ponderada do que você gosta, menos metade da média do que pula.
        fun mean(list: List<Ex>): FloatArray {
            val m = FloatArray(dim)
            var total = 0f
            list.forEach { ex -> total += ex.weight; for (i in 0 until dim) m[i] += ex.v[i] * ex.weight }
            if (total > 0f) for (i in 0 until dim) m[i] /= total
            return m
        }
        fun profile(p: List<Ex>, n: List<Ex>): FloatArray {
            val a = mean(p)
            if (n.size >= 3) {
                val b = mean(n)
                for (i in 0 until dim) a[i] -= 0.5f * b[i]
            }
            return normalized(a)
        }
        val centroid = profile(pos, neg)
        val dayparts = Array(4) { part ->
            val p = pos.filter { it.hour >= 0 && daypart(it.hour) == part }
            if (p.size < MIN_DAYPART) null else profile(p, neg.filter { it.hour >= 0 && daypart(it.hour) == part })
        }

        // Previsor: só com pulos e escutas completas suficientes.
        var weights: FloatArray? = null
        var bias = 0f
        var accuracy: Float? = null
        if (pos.size >= MIN_CLASS && neg.size >= MIN_CLASS) {
            fun features(ex: Ex): FloatArray {
                val x = FloatArray(dim + EXTRA)
                System.arraycopy(ex.v, 0, x, 0, dim)
                x[dim] = ex.prev?.let { dot(it, ex.v) } ?: 0f
                x[dim + 1] = if (ex.prev != null) 1f else 0f
                x[dim + 2] = dot(centroid, ex.v)
                x[dim + 3] = if (ex.hour >= 0) dayparts[daypart(ex.hour)]?.let { dot(it, ex.v) } ?: x[dim + 2] else x[dim + 2]
                return x
            }
            // As escutas mais recentes ficam de fora para medir o acerto honestamente.
            val timed = examples.filter { it.at > 0 }.sortedBy { it.at }
            val holdout = if (timed.size >= 40) timed.takeLast(timed.size / 5).toHashSet() else emptySet()
            val trainSet = examples.filter { it !in holdout }
            val xs = trainSet.map(::features)
            val ys = trainSet.map { it.label }
            val nPos = ys.count { it == 1 }.coerceAtLeast(1)
            val nNeg = ys.count { it == 0 }.coerceAtLeast(1)
            // Classes equilibradas: um pulo pesa tanto quanto uma escuta completa.
            val ws = trainSet.map { it.weight * if (it.label == 1) ys.size / (2f * nPos) else ys.size / (2f * nNeg) }
            val w = FloatArray(dim + EXTRA)
            var b = 0f
            val order = xs.indices.toMutableList()
            val rng = java.util.Random(42)
            repeat(EPOCHS) { epoch ->
                order.shuffle(rng)
                val lr = LEARNING_RATE / (1f + epoch * 0.5f)
                for (i in order) {
                    val x = xs[i]
                    val p = sigmoid(dot(w, x) + b)
                    val g = (p - ys[i]) * ws[i]
                    for (j in w.indices) w[j] -= lr * (g * x[j] + L2 * w[j])
                    b -= lr * g
                }
            }
            if (holdout.isNotEmpty()) {
                val hits = holdout.count { ex -> (sigmoid(dot(w, features(ex)) + b) >= 0.5f) == (ex.label == 1) }
                accuracy = hits / holdout.size.toFloat()
            }
            // Se não acerta mais que o acaso, fica só com o perfil (que não atrapalha).
            if (accuracy == null || accuracy >= 0.55f) {
                weights = w
                bias = b
            }
        }

        val st = Status(
            trained = true,
            examples = events.size,
            positives = pos.size,
            negatives = neg.size,
            trainedAtMs = nowMs,
            accuracy = accuracy,
            profileOnly = weights == null,
        )
        val m = Model(dim, centroid, dayparts, weights, bias, st)
        model = m
        _status.value = st
        runCatching { save(m) }
    }

    /** Chance (0..1) de você ouvir esta faixa até o fim, agora; null se o modelo não serve para ela. */
    fun likeProbability(v: FloatArray, prev: FloatArray?, hour: Int): Float? {
        val m = model ?: return null
        if (v.size != m.dim) return null
        val x = normalized(v)
        if (x.size != m.dim) return null
        val p = prev?.takeIf { it.size == m.dim }?.let(::normalized)
        val simCentroid = dot(m.centroid, x)
        val simPart = m.dayparts[daypart(hour)]?.let { dot(it, x) } ?: simCentroid
        val w = m.weights ?: return sigmoid(SIM_SCALE * simPart)
        var z = m.bias
        for (i in 0 until m.dim) z += w[i] * x[i]
        z += w[m.dim] * (p?.let { dot(it, x) } ?: 0f)
        z += w[m.dim + 1] * (if (p != null) 1f else 0f)
        z += w[m.dim + 2] * simCentroid
        z += w[m.dim + 3] * simPart
        return sigmoid(z)
    }

    /** A faixa combina com o que você costuma ouvir neste período do dia? */
    fun matchesDaypart(v: FloatArray, hour: Int): Boolean {
        val m = model ?: return false
        val part = m.dayparts[daypart(hour)] ?: return false
        return v.size == m.dim && dot(part, normalized(v)) >= DAYPART_MATCH
    }

    /**
     * Reordena uma fila do SMART pelo seu gosto, sem desmontar a ordem do motor: a posição
     * original continua valendo, e o gosto pesa mais quanto mais o modelo já aprendeu.
     * Devolve a fila e as faixas que subiram por causa do gosto.
     */
    fun rerank(
        candidates: List<Track>,
        vectors: Map<Long, FloatArray>,
        prev: FloatArray?,
        hour: Int,
    ): Pair<List<Track>, Set<Long>> {
        val m = model ?: return candidates to emptySet()
        if (candidates.size < 2) return candidates to emptySet()
        val alpha = if (m.weights != null) min(0.45f, 0.15f + m.status.examples / 1500f) else 0.12f
        val n = candidates.size
        val probs = candidates.map { t -> vectors[t.id]?.let { likeProbability(it, prev, hour) } }
        val scored = candidates.mapIndexed { i, t ->
            val base = 1f - i / n.toFloat()
            val p = probs[i] ?: 0.5f
            Triple(t, i, (1f - alpha) * base + alpha * p)
        }.sortedByDescending { it.third }
        val boosted = scored.mapIndexedNotNull { newIndex, (t, oldIndex, _) ->
            t.id.takeIf { oldIndex - newIndex >= 2 && (probs[oldIndex] ?: 0f) >= 0.6f }
        }.toSet()
        return scored.map { it.first } to boosted
    }

    // ---- persistência: um arquivo binário pequeno

    private fun save(m: Model) {
        val bytes = java.io.ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT)
            out.writeInt(m.dim)
            m.centroid.forEach(out::writeFloat)
            m.dayparts.forEach { part ->
                out.writeBoolean(part != null)
                part?.forEach(out::writeFloat)
            }
            out.writeBoolean(m.weights != null)
            m.weights?.forEach(out::writeFloat)
            out.writeFloat(m.bias)
            val s = m.status
            out.writeInt(s.examples); out.writeInt(s.positives); out.writeInt(s.negatives)
            out.writeLong(s.trainedAtMs); out.writeFloat(s.accuracy ?: -1f)
        }
        AtomicFiles.write(file, bytes.toByteArray())
    }

    private fun load(): Model? {
        if (!file.exists()) return null
        DataInputStream(file.inputStream().buffered()).use { inp ->
            if (inp.readInt() != FORMAT) return null
            val dim = inp.readInt()
            val centroid = FloatArray(dim) { inp.readFloat() }
            val dayparts = Array(4) { if (inp.readBoolean()) FloatArray(dim) { inp.readFloat() } else null }
            val weights = if (inp.readBoolean()) FloatArray(dim + EXTRA) { inp.readFloat() } else null
            val bias = inp.readFloat()
            val examples = inp.readInt(); val positives = inp.readInt(); val negatives = inp.readInt()
            val at = inp.readLong(); val acc = inp.readFloat()
            val st = Status(true, examples, positives, negatives, at, acc.takeIf { it >= 0f }, weights == null)
            return Model(dim, centroid, dayparts, weights, bias, st)
        }
    }

    companion object {
        private const val FORMAT = 1
        private const val EXTRA = 4
        private const val EPOCHS = 8
        private const val LEARNING_RATE = 0.05f
        private const val L2 = 1e-3f
        private const val MIN_POSITIVES = 5
        private const val MIN_DAYPART = 6
        private const val MIN_CLASS = 10
        private const val SIM_SCALE = 4f
        private const val DAYPART_MATCH = 0.35f
        private const val MINUTE = 60_000L
        private const val DAY = 86_400_000L

        /** 0 madrugada, 1 manhã, 2 tarde, 3 noite. */
        fun daypart(hour: Int): Int = when (hour) {
            in 0..5 -> 0
            in 6..11 -> 1
            in 12..17 -> 2
            else -> 3
        }

        private fun sigmoid(z: Float): Float = 1f / (1f + exp(-z.coerceIn(-30f, 30f)))

        private fun dot(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in 0 until min(a.size, b.size)) s += a[i] * b[i]
            return s
        }

        private fun normalized(v: FloatArray): FloatArray {
            var n = 0f
            for (x in v) n += x * x
            if (n <= 0f || n.isNaN()) return FloatArray(0)
            val inv = 1f / sqrt(n)
            return FloatArray(v.size) { v[it] * inv }
        }
    }
}
