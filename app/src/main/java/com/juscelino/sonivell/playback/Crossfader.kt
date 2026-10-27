package com.juscelino.sonivell.playback

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.juscelino.sonivell.data.SettingsStore
import com.juscelino.sonivell.smart.SmartEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Crossfade de verdade: o fim de uma faixa some enquanto a próxima entra, as duas tocando juntas.
 *
 * Como funciona ("passagem de bastão"): perto do fim da faixa, um segundo ExoPlayer — a [tail] —
 * é preparado com a MESMA faixa, já posicionado no ponto onde o crossfade começa. Quando o player
 * principal chega lá, a cauda assume o fim da música e o principal pula para a próxima faixa, em
 * silêncio, e vai subindo de volume enquanto a cauda desce (curvas de mesma potência, sem "buraco"
 * no meio). Assim o player principal continua sendo o único que a MediaSession, a fila, a
 * notificação e o histórico conhecem; a cauda é só um eco do fim da faixa anterior.
 *
 * A cauda usa a mesma sessão de áudio, então o equalizador vale para as duas, e não pede foco de
 * áudio (quem cuida disso é o principal; pausar o principal pausa a cauda).
 *
 * Só há crossfade nas passagens automáticas: pular, voltar ou escolher outra faixa corta na hora.
 *
 * Com o SMART: se a fila é do SMART e a opção estiver ligada, a duração se adapta ao quanto as
 * duas faixas soam parecidas ([SmartEngine.transitionSimilarity]) — mais longa entre vizinhas,
 * mais curta quando a mudança é grande.
 */
@OptIn(UnstableApi::class)
class Crossfader(
    private val context: Context,
    private val main: ExoPlayer,
    private val audioSessionId: Int,
    private val prefs: SharedPreferences,
    private val smart: SmartEngine,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Volume "de base" da faixa atual (normalização); o crossfade multiplica por cima. */
    private var mainGain = 1f
    /** Fator do crossfade sobre o principal: 0 no começo da entrada, 1 fora do crossfade. */
    private var fadeIn = 1f

    /** Faixa que está saindo, tocando o próprio fim durante o crossfade. */
    private var tail: ExoPlayer? = null
    private var tailGain = 1f

    private var plan: Plan? = null
    private var fade: Fade? = null
    private var planningJob: Job? = null
    /** Faixa (posição na fila + id) para a qual já se decidiu fazer ou não o crossfade. */
    private var decidedFor: Pair<Int, String>? = null

    private data class Plan(
        val fromIndex: Int,
        val fromId: String,
        val toIndex: Int,
        val toId: String,
        val fadeStartMs: Long,
        val fadeMs: Long,
    )

    private data class Fade(val toIndex: Int, val toId: String, val startMs: Long, val lengthMs: Long)

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            tail?.playWhenReady = playWhenReady && fade != null
            schedule(0)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val f = fade
            // A passagem que o próprio crossfade pediu: segue o crossfade.
            if (f != null && main.currentMediaItemIndex == f.toIndex && mediaItem?.mediaId == f.toId) return
            // Qualquer outra (pulou, escolheu outra faixa, a fila mudou): corta e recomeça.
            if (f != null) finishFade()
            dropPlan()
            schedule(0)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK && fade == null) {
                dropPlan()
                schedule(0)
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // Só mudanças na fila; as de duração/metadados (pré-carga da próxima) não contam.
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && fade == null) {
                dropPlan()
                schedule(0)
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) = replan()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = replan()
    }

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("crossfade")) replan()
    }

    private val tick = Runnable { onTick() }

    fun start() {
        main.addListener(listener)
        prefs.registerOnSharedPreferenceChangeListener(settingsListener)
        schedule(0)
    }

    fun release() {
        prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        main.removeListener(listener)
        handler.removeCallbacks(tick)
        scope.cancel()
        releaseTail()
    }

    /** Volume da faixa atual pela normalização (1 sem ela). O crossfade aplica o dele por cima. */
    fun setMainGain(gain: Float) {
        mainGain = gain
        applyMainVolume()
    }

    // ---- Laço ----

    private fun schedule(delayMs: Long) {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delayMs)
    }

    private fun onTick() {
        fade?.let {
            stepFade(it)
            if (fade != null) schedule(FADE_TICK_MS)
            return
        }
        if (!enabled() || !main.playWhenReady) {
            dropPlan()
            return // volta quando tocar de novo ou o ajuste mudar
        }
        val duration = main.duration
        val position = main.currentPosition
        if (duration == C.TIME_UNSET || duration <= 0) return schedule(IDLE_TICK_MS)
        val remaining = duration - position

        plan?.let { p ->
            if (!isStillValid(p)) dropPlan()
        }
        val current = main.currentMediaItemIndex to (main.currentMediaItem?.mediaId ?: "")
        if (plan == null && planningJob?.isActive != true && decidedFor != current &&
            remaining <= MAX_FADE_MS + PREPARE_LEAD_MS
        ) {
            decidedFor = current
            planFor(duration)
        }

        val p = plan
        when {
            p != null && position >= p.fadeStartMs -> {
                startFade(p)
                schedule(FADE_TICK_MS)
            }
            // Perto do ponto de entrada, olha bem de perto para a passagem não repetir nem pular nada.
            p != null && p.fadeStartMs - position < 400 -> schedule(PRECISE_TICK_MS)
            p != null -> schedule(min(p.fadeStartMs - position - 300, IDLE_TICK_MS))
            remaining > MAX_FADE_MS + PREPARE_LEAD_MS -> schedule(IDLE_TICK_MS)
            else -> schedule(NEAR_TICK_MS)
        }
    }

    // ---- Planejamento ----

    private fun planFor(duration: Long) {
        if (main.repeatMode == Player.REPEAT_MODE_ONE) return
        val fromIndex = main.currentMediaItemIndex
        val toIndex = main.nextMediaItemIndex
        if (toIndex == C.INDEX_UNSET || toIndex == fromIndex) return
        val from = main.currentMediaItem ?: return
        val to = main.getMediaItemAt(toIndex)
        if (prefs.getBoolean(SettingsStore.KEY_CROSSFADE_KEEP_ALBUMS, true) && sameAlbum(from, to)) return

        val baseMs = prefs.getInt(SettingsStore.KEY_CROSSFADE_SECONDS, 6).coerceIn(1, 12) * 1000L
        val adaptive = smart.isAvailable &&
            prefs.getBoolean(SettingsStore.KEY_CROSSFADE_SMART, true) &&
            prefs.getBoolean(SettingsStore.KEY_SMART_MODE, false)

        planningJob = scope.launch {
            val similarity = if (adaptive) {
                val a = from.mediaId.toLongOrNull()
                val b = to.mediaId.toLongOrNull()
                if (a != null && b != null) {
                    withTimeoutOrNull(SIMILARITY_TIMEOUT_MS) { smart.transitionSimilarity(a, b) }
                } else null
            } else null
            val wanted = if (adaptive) adaptiveFadeMs(baseMs, similarity) else baseMs
            // Nunca começa antes de 87% da faixa: assim ela ainda conta como ouvida até o fim.
            val fadeMs = min(wanted, (duration * MAX_FADE_FRACTION).toLong())
            if (fadeMs < MIN_FADE_MS) return@launch

            // A fila pode ter mudado enquanto o SMART pensava.
            if (main.currentMediaItemIndex != fromIndex || main.currentMediaItem?.mediaId != from.mediaId) return@launch
            if (main.nextMediaItemIndex != toIndex) return@launch
            val fadeStart = duration - fadeMs
            if (main.currentPosition > fadeStart - LATE_MARGIN_MS) return@launch

            Log.d(TAG, "Crossfade de ${fadeMs} ms (semelhança ${similarity ?: "—"})")
            prepareTail(from, fadeStart)
            plan = Plan(fromIndex, from.mediaId, toIndex, to.mediaId, fadeStart, fadeMs)
            schedule(0)
        }
    }

    private fun prepareTail(item: MediaItem, positionMs: Long) {
        releaseTail()
        tail = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false, // o foco é do player principal
            )
            .build()
            .apply {
                setAudioSessionId(audioSessionId) // o equalizador vale para as duas
                setMediaItem(item, positionMs)
                volume = mainGain
                playWhenReady = false
                prepare()
            }
    }

    private fun isStillValid(p: Plan): Boolean =
        main.currentMediaItemIndex == p.fromIndex &&
            main.currentMediaItem?.mediaId == p.fromId &&
            main.nextMediaItemIndex == p.toIndex &&
            main.getMediaItemAt(p.toIndex).mediaId == p.toId &&
            main.repeatMode != Player.REPEAT_MODE_ONE

    private fun replan() {
        if (fade != null) return // deixa o crossfade em curso terminar
        dropPlan()
        decidedFor = null
        schedule(0)
    }

    private fun dropPlan() {
        planningJob?.cancel()
        planningJob = null
        plan = null
        if (fade == null) {
            releaseTail()
            decidedFor = null
        }
    }

    // ---- Crossfade ----

    private fun startFade(p: Plan) {
        val t = tail
        // A cauda não ficou pronta a tempo: passagem normal, sem crossfade.
        if (t == null || t.playbackState != Player.STATE_READY) {
            plan = null
            releaseTail()
            return
        }
        plan = null
        tailGain = mainGain
        t.volume = tailGain
        t.playWhenReady = main.playWhenReady
        fade = Fade(p.toIndex, p.toId, p.fadeStartMs, p.fadeMs)
        fadeIn = 0f
        applyMainVolume()
        main.seekTo(p.toIndex, 0L)
        fadeStartedAt = SystemClock.elapsedRealtime()
    }

    private var fadeStartedAt = 0L

    private fun stepFade(f: Fade) {
        val t = tail ?: return finishFade()
        if (t.playbackState == Player.STATE_ENDED || t.playbackState == Player.STATE_IDLE) return finishFade()
        // O progresso segue o que a cauda já tocou (pausar no meio congela o crossfade).
        val progress = ((t.currentPosition - f.startMs).toFloat() / f.lengthMs).coerceIn(0f, 1f)
        // Segurança: se a cauda travar (arquivo sumiu, erro), não deixa o principal mudo.
        val stuck = SystemClock.elapsedRealtime() - fadeStartedAt > f.lengthMs + STUCK_GRACE_MS && main.playWhenReady
        if (progress >= 1f || stuck) return finishFade()
        val (fadeOut, fadeInNow) = equalPower(progress)
        t.volume = tailGain * fadeOut
        fadeIn = fadeInNow
        applyMainVolume()
    }

    private fun finishFade() {
        fade = null
        fadeIn = 1f
        applyMainVolume()
        releaseTail()
        decidedFor = null
        schedule(0)
    }

    private fun applyMainVolume() {
        main.volume = (mainGain * fadeIn).coerceIn(0f, 1f)
    }

    private fun releaseTail() {
        tail?.let { t -> runCatching { t.stop(); t.release() } }
        tail = null
    }

    private fun enabled() = prefs.getBoolean(SettingsStore.KEY_CROSSFADE, false)

    private fun sameAlbum(a: MediaItem, b: MediaItem): Boolean {
        val x = a.mediaMetadata.extras?.getLong("albumId") ?: 0L
        val y = b.mediaMetadata.extras?.getLong("albumId") ?: 0L
        return x != 0L && x == y
    }

    companion object {
        private const val TAG = "SonivellCrossfade"

        const val MIN_FADE_MS = 1_000L
        const val MAX_FADE_MS = 12_000L
        /** Fração máxima da faixa usada no crossfade (a escuta "completa" pede 85%). */
        const val MAX_FADE_FRACTION = 0.13

        /** Semelhança de um par qualquer de faixas e a das vizinhas mais próximas (as do SMART). */
        const val SIMILARITY_DIFFERENT = -0.05f
        const val SIMILARITY_ALIKE = 0.2f
        /** Quanto a duração escolhida encolhe (faixas bem diferentes) ou cresce (muito parecidas). */
        const val SHORTEST_FACTOR = 0.4f
        const val LONGEST_FACTOR = 1.6f

        private const val PREPARE_LEAD_MS = 4_000L
        private const val LATE_MARGIN_MS = 300L
        private const val SIMILARITY_TIMEOUT_MS = 2_000L
        private const val STUCK_GRACE_MS = 4_000L
        private const val IDLE_TICK_MS = 2_000L
        private const val NEAR_TICK_MS = 250L
        private const val PRECISE_TICK_MS = 15L
        private const val FADE_TICK_MS = 40L

        /**
         * Duração adaptada pelo SMART: [baseMs] é o "meio". Faixas muito parecidas ganham até
         * [LONGEST_FACTOR] vezes; mudanças grandes ficam com [SHORTEST_FACTOR]. Sem semelhança
         * conhecida (faixa ainda não analisada), usa [baseMs].
         */
        fun adaptiveFadeMs(baseMs: Long, similarity: Float?): Long {
            if (similarity == null || similarity.isNaN()) return baseMs
            val t = ((similarity - SIMILARITY_DIFFERENT) / (SIMILARITY_ALIKE - SIMILARITY_DIFFERENT)).coerceIn(0f, 1f)
            val factor = SHORTEST_FACTOR + t * (LONGEST_FACTOR - SHORTEST_FACTOR)
            return (baseMs * factor).toLong().coerceIn(MIN_FADE_MS, MAX_FADE_MS)
        }

        /** Curvas de mesma potência: (saída, entrada). O volume somado não "afunda" no meio. */
        fun equalPower(progress: Float): Pair<Float, Float> {
            val angle = progress.coerceIn(0f, 1f) * (PI / 2).toFloat()
            return cos(angle) to sin(angle)
        }
    }
}
