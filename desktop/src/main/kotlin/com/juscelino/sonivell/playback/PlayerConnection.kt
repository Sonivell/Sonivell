package com.juscelino.sonivell.playback

import androidx.compose.runtime.Immutable
import com.juscelino.sonivell.compat.Context
import com.juscelino.sonivell.compat.SharedPreferences
import com.juscelino.sonivell.data.ListenSessionTracker
import com.juscelino.sonivell.data.ListeningHistory
import com.juscelino.sonivell.data.SavedSession
import com.juscelino.sonivell.data.SessionStore
import com.juscelino.sonivell.data.SettingsStore
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.desktop.AudioEngine
import com.juscelino.sonivell.desktop.Scanner
import com.juscelino.sonivell.smart.SmartEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.pow

/** Item da fila (no Android, o MediaItem do Media3). */
@Immutable
data class MediaItem(val mediaId: String, val track: Track)

fun Track.toMediaItem(): MediaItem = MediaItem(id.toString(), this)

@Suppress("UNUSED_PARAMETER")
fun MediaItem.toTrack(context: Context): Track = track

enum class RepeatMode { OFF, ALL, ONE }

@Immutable
data class PlayerState(
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
) {
    val current: MediaItem? get() = queue.getOrNull(currentIndex)
    val hasMedia: Boolean get() = current != null
    val upNext: List<IndexedValue<MediaItem>>
        get() = queue.withIndex().filter { it.index > currentIndex }
}

/** Volume do player (0–1), guardado nas preferências. */
object PlayerVolume {
    private val prefs: SharedPreferences get() = SharedPreferences.named("sonivell")
    private val _flow = MutableStateFlow(prefs.getFloat("volume", 0.8f).coerceIn(0f, 1f))
    val flow: StateFlow<Float> = _flow.asStateFlow()
    val value: Float get() = _flow.value

    fun set(v: Float) {
        _flow.value = v.coerceIn(0f, 1f)
        prefs.edit().putFloat("volume", _flow.value).apply()
    }
}

/**
 * O player do PC, com a mesma interface do PlayerConnection do Android. Aqui ele também faz o
 * papel do serviço de reprodução: registra as escutas, guarda a sessão, cuida do temporizador,
 * dos efeitos, do crossfade e da saída de áudio.
 */
class PlayerConnection(context: Context, private val scope: CoroutineScope) {

    private val prefs = context.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE)
    private val history = ListeningHistory.get(context)
    private val tracker = ListenSessionTracker()
    private val sessionStore = SessionStore(prefs)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)

    /** Problema de áudio (sem saída de som, ffmpeg falhou), para avisar na tela. */
    val error: StateFlow<String?> = _error.asStateFlow()
    fun dismissError() { _error.value = null }

    /** Motor SMART: deixa o crossfade adaptar a duração pela semelhança das faixas. */
    @Volatile var smart: SmartEngine? = null

    private val engine = AudioEngine(
        onEnded = { onEngineEnded() },
        onError = { msg -> _error.value = msg; synchronized(this) { playing = false; publish() } },
    )

    val output = OutputWatcher(prefs, scope, onPause = { if (playing) togglePlay() }, onResume = { if (!playing && current != null) togglePlay() })

    // ---- Fila e modos (sob o monitor deste objeto) ----
    private var queue: MutableList<MediaItem> = ArrayList()
    private var index = -1
    private var playing = false
    private var shuffle = false
    private var repeat = RepeatMode.OFF
    /** Ordem de antes do aleatório, para voltar a ela ao desligar. */
    private var baseOrder: List<MediaItem> = emptyList()
    /** A faixa atual já está no motor? Sem isso, tocar começa de [pendingMs]. */
    private var loaded = false
    private var pendingMs = 0L
    private var lastSessionSave = 0L
    private var tickJob: Job? = null

    // Crossfade planejado para a faixa atual.
    private var plannedFor: String? = null
    private var fadeMsPlanned: Long = 0L
    private var planJob: Job? = null

    private val current: MediaItem? get() = queue.getOrNull(index)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when {
            key == null || key.startsWith("eq_") || key == SettingsStore.KEY_PLAYBACK_SPEED || key == SettingsStore.KEY_SKIP_SILENCE ->
                engine.setFilters(AudioEffects.filtersFrom(p))
            key == SettingsStore.KEY_NORMALIZE -> synchronized(this) { current?.let { engine.setTrackGain(gainOf(it.track)) } }
            key.startsWith("crossfade") -> synchronized(this) { plannedFor = null }
        }
    }

    init {
        engine.volume = PlayerVolume.value
        engine.setFilters(AudioEffects.filtersFrom(prefs))
        scope.launch { PlayerVolume.flow.collect { engine.volume = it } }
    }

    fun connect() {
        if (_connected.value) return
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        output.start()
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
        _connected.value = true
    }

    fun release() {
        synchronized(this) {
            observe()
            saveSession(force = true)
        }
        tickJob?.cancel()
        output.release()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        engine.close()
        _connected.value = false
    }

    // ---- Estado publicado ----

    private fun publish() {
        val cur = current
        _state.value = PlayerState(
            queue = queue.toList(),
            currentIndex = index,
            isPlaying = playing,
            positionMs = positionNow(),
            durationMs = cur?.track?.durationMs ?: 0L,
            shuffle = shuffle,
            repeat = repeat,
        )
    }

    private fun positionNow(): Long = if (loaded) engine.positionMs() else pendingMs

    /** Ganho do ReplayGain da faixa (só atenua, como o volume do player do Android), ou 1. */
    private fun gainOf(track: Track): Float {
        if (!prefs.getBoolean(SettingsStore.KEY_NORMALIZE, false)) return 1f
        val db = Scanner.extras[track.path]?.gainDb ?: return 1f
        return 10.0.pow(db / 20.0).toFloat().coerceIn(0.05f, 1f)
    }

    // ---- Comandos ----

    @Synchronized
    fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean = false, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        observe()
        val items = tracks.map { it.toMediaItem() }
        val start = startIndex.coerceIn(0, items.lastIndex)
        baseOrder = items
        this.shuffle = shuffle
        if (shuffle) {
            val first = items[start]
            queue = (listOf(first) + items.filterIndexed { i, _ -> i != start }.shuffled()).toMutableList()
            index = 0
        } else {
            queue = items.toMutableList()
            index = start
        }
        startCurrent(startPositionMs, immediate = true)
    }

    private fun startCurrent(fromMs: Long, immediate: Boolean) {
        val cur = current ?: return
        plannedFor = null
        loaded = true
        playing = true
        pendingMs = fromMs
        engine.play(cur.track.path, fromMs, gainOf(cur.track), immediate)
        observe()
        publish()
        saveSession(force = true)
    }

    /** Volta a fila salva, sem tocar (o play continua de [positionMs]). */
    @Synchronized
    fun restore(tracks: List<Track>, index: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        if (tracks.isEmpty() || current != null) return
        queue = tracks.map { it.toMediaItem() }.toMutableList()
        baseOrder = queue.toList()
        this.index = index.coerceIn(0, queue.lastIndex)
        this.shuffle = shuffle
        repeat = when (repeatMode) { 1 -> RepeatMode.ONE; 2 -> RepeatMode.ALL; else -> RepeatMode.OFF }
        loaded = false
        playing = false
        pendingMs = positionMs
        publish()
    }

    @Synchronized
    fun togglePlay() {
        val cur = current ?: return
        if (playing) {
            pendingMs = positionNow()
            engine.pause()
            playing = false
        } else {
            if (loaded && engine.isActive) engine.resume() else {
                loaded = true
                engine.play(cur.track.path, pendingMs, gainOf(cur.track), immediate = true)
            }
            playing = true
        }
        observe()
        publish()
        saveSession(force = true)
    }

    @Synchronized
    fun pause() { if (playing) togglePlay() }

    @Synchronized
    fun resume() { if (!playing) togglePlay() }

    @Synchronized
    fun next() {
        if (current == null) return
        observe()
        when {
            index + 1 < queue.size -> { index++; startCurrent(0, immediate = true) }
            repeat == RepeatMode.ALL && queue.isNotEmpty() -> { index = 0; startCurrent(0, immediate = true) }
        }
    }

    /** Como o seekToPrevious do Media3: depois de 3 s volta ao começo; antes, vai à anterior. */
    @Synchronized
    fun previous() {
        if (current == null) return
        if (positionNow() > 3_000 || index == 0) { seekTo(0L); return }
        observe()
        index--
        startCurrent(0, immediate = true)
    }

    @Synchronized
    fun seekTo(positionMs: Long) {
        val cur = current ?: return
        val to = positionMs.coerceIn(0L, (cur.track.durationMs - 500).coerceAtLeast(0L))
        plannedFor = null
        pendingMs = to
        if (playing) {
            loaded = true
            engine.play(cur.track.path, to, gainOf(cur.track), immediate = true)
        } else {
            engine.stop()
            loaded = false
        }
        observe()
        publish()
    }

    @Synchronized
    fun playAt(index: Int) {
        if (index !in queue.indices) return
        observe()
        this.index = index
        startCurrent(0, immediate = true)
    }

    @Synchronized
    fun toggleShuffle() = setShuffle(!shuffle)

    @Synchronized
    fun setShuffle(enabled: Boolean) {
        if (enabled == shuffle) return
        shuffle = enabled
        val cur = current
        if (cur != null) {
            if (enabled) {
                baseOrder = queue.toList()
                queue = (listOf(cur) + queue.filterIndexed { i, _ -> i != index }.shuffled()).toMutableList()
                index = 0
            } else {
                val base = baseOrder.filter { b -> queue.any { it.mediaId == b.mediaId } }
                if (base.any { it.mediaId == cur.mediaId }) {
                    queue = base.toMutableList()
                    index = queue.indexOfFirst { it.mediaId == cur.mediaId }
                }
            }
        }
        plannedFor = null
        publish()
        saveSession(force = true)
    }

    @Synchronized
    fun cycleRepeat() {
        repeat = when (repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }
        plannedFor = null
        publish()
        saveSession(force = true)
    }

    @Synchronized
    fun moveItem(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val item = queue.removeAt(from)
        queue.add(to, item)
        index = when {
            from == index -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        plannedFor = null
        publish()
    }

    @Synchronized
    fun removeItem(index: Int) {
        if (index !in queue.indices || index == this.index) return
        queue.removeAt(index)
        if (index < this.index) this.index--
        plannedFor = null
        publish()
    }

    /** Tira tudo o que vem depois da faixa atual. */
    @Synchronized
    fun clearUpNext() {
        if (index < 0) return
        while (queue.size > index + 1) queue.removeAt(queue.lastIndex)
        plannedFor = null
        publish()
    }

    @Synchronized
    fun playNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (current == null) { play(tracks, 0); return }
        queue.addAll(index + 1, tracks.map { it.toMediaItem() })
        plannedFor = null
        publish()
    }

    @Synchronized
    fun addNext(track: Track) = playNext(listOf(track))

    @Synchronized
    fun append(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (current == null) { play(tracks, 0); return }
        queue.addAll(tracks.map { it.toMediaItem() })
        publish()
    }

    /** Troca tudo o que vem depois da faixa atual por [tracks] (SMART replanejando). */
    @Synchronized
    fun replaceUpNext(tracks: List<Track>) {
        if (index < 0) return
        while (queue.size > index + 1) queue.removeAt(queue.lastIndex)
        queue.addAll(tracks.map { it.toMediaItem() })
        plannedFor = null
        publish()
    }

    // ---- Fim de faixa, crossfade, escutas, temporizador ----

    /** O motor terminou de ler a faixa atual: a próxima entra logo atrás (sem pausa). */
    @Synchronized
    private fun onEngineEnded() {
        if (current == null) return
        val armed = prefs.getString(SleepTimerPrefs.KEY_TRACK, null)
        when {
            repeat == RepeatMode.ONE -> startCurrent(0, immediate = false)
            index + 1 < queue.size -> { index++; startCurrent(0, immediate = false) }
            repeat == RepeatMode.ALL && queue.isNotEmpty() -> { index = 0; startCurrent(0, immediate = false) }
            else -> {
                // A fila acabou: fica parada no começo da última faixa.
                playing = false
                loaded = false
                pendingMs = 0L
                if (armed != null) SleepTimerPrefs.clear(prefs)
                observe()
                publish()
                saveSession(force = true)
                return
            }
        }
        pauseIfArmedTrackEnded(armed)
    }

    /** "Ao fim desta música": a faixa marcada saiu → pausa no começo da próxima. */
    private fun pauseIfArmedTrackEnded(armed: String?) {
        if (armed == null || current?.mediaId == armed) return
        SleepTimerPrefs.clear(prefs)
        engine.stop()
        loaded = false
        pendingMs = 0L
        playing = false
        observe()
        publish()
    }

    private fun tick() = synchronized(this) {
        if (current == null) return@synchronized
        // Temporizador por horário.
        SleepTimerPrefs.read(prefs).endsAtMs?.let { endsAt ->
            if (System.currentTimeMillis() >= endsAt) {
                SleepTimerPrefs.clear(prefs)
                if (playing) togglePlay()
            }
        }
        if (playing) {
            maybeCrossfade()
            observe()
            saveSession(force = false)
        }
        publish()
    }

    /** Fecha a escuta da faixa que saiu (e avisa o app) quando a reprodução muda de faixa. */
    private fun observe() {
        val cur = current
        val event = tracker.observe(
            currentId = cur?.mediaId?.toLongOrNull(),
            positionMs = positionNow().coerceAtLeast(0),
            durationMs = cur?.track?.durationMs ?: 0,
            isPlaying = playing,
            nowMs = System.currentTimeMillis(),
        ) ?: return
        history.publish(event)
        if (!prefs.getBoolean(SettingsStore.KEY_HISTORY_PAUSED, false)) scope.launch { history.record(event) }
    }

    private fun saveSession(force: Boolean) {
        if (queue.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSessionSave < SESSION_SAVE_EVERY_MS) return
        lastSessionSave = now
        sessionStore.save(
            SavedSession(
                queueIds = queue.mapNotNull { it.mediaId.toLongOrNull() },
                index = index,
                positionMs = positionNow().coerceAtLeast(0),
                shuffle = shuffle,
                repeatMode = when (repeat) { RepeatMode.OFF -> 0; RepeatMode.ONE -> 1; RepeatMode.ALL -> 2 },
            ),
        )
    }

    /**
     * Crossfade de verdade (as duas faixas tocando juntas), só nas passagens automáticas, com as
     * mesmas regras do Crossfader do Android: duração escolhida (ou adaptada pelo SMART), nunca mais
     * que 13% da faixa, e sem misturar faixas seguidas do mesmo álbum se o ajuste pedir.
     */
    private fun maybeCrossfade() {
        if (!prefs.getBoolean(SettingsStore.KEY_CROSSFADE, false) || repeat == RepeatMode.ONE) return
        val from = current ?: return
        val toIndex = when {
            index + 1 < queue.size -> index + 1
            repeat == RepeatMode.ALL && queue.size > 1 -> 0
            else -> return
        }
        val to = queue[toIndex]
        val key = "${from.mediaId}>${to.mediaId}@$index"
        if (plannedFor != key) {
            plannedFor = key
            fadeMsPlanned = 0L
            planJob?.cancel()
            if (prefs.getBoolean(SettingsStore.KEY_CROSSFADE_KEEP_ALBUMS, true) && from.track.albumId == to.track.albumId) return
            val baseMs = prefs.getInt(SettingsStore.KEY_CROSSFADE_SECONDS, 6).coerceIn(1, 12) * 1000L
            val engineSmart = smart
            val adaptive = engineSmart?.isAvailable == true &&
                prefs.getBoolean(SettingsStore.KEY_CROSSFADE_SMART, true) &&
                prefs.getBoolean(SettingsStore.KEY_SMART_MODE, false)
            val duration = from.track.durationMs
            planJob = scope.launch {
                val similarity = if (adaptive) {
                    withTimeoutOrNull(2_000L) { engineSmart?.transitionSimilarity(from.track.id, to.track.id) }
                } else null
                val wanted = if (adaptive) adaptiveFadeMs(baseMs, similarity) else baseMs
                val fadeMs = min(wanted, (duration * MAX_FADE_FRACTION).toLong())
                synchronized(this@PlayerConnection) { if (plannedFor == key && fadeMs >= MIN_FADE_MS) fadeMsPlanned = fadeMs }
            }
            return
        }
        val fadeMs = fadeMsPlanned
        if (fadeMs <= 0) return
        val position = positionNow()
        if (position < from.track.durationMs - fadeMs) return
        // Começa o crossfade: a próxima entra agora, a atual some por cima.
        observe()
        index = toIndex
        plannedFor = null
        fadeMsPlanned = 0L
        loaded = true
        engine.crossfadeTo(to.track.path, 0L, gainOf(to.track), fadeMs)
        observe()
        publish()
        saveSession(force = true)
    }

    private companion object {
        const val TICK_MS = 250L
        const val SESSION_SAVE_EVERY_MS = 5_000L
        const val MIN_FADE_MS = 1_000L
        const val MAX_FADE_MS = 12_000L
        const val MAX_FADE_FRACTION = 0.13
        const val SIMILARITY_DIFFERENT = -0.05f
        const val SIMILARITY_ALIKE = 0.2f
        const val SHORTEST_FACTOR = 0.4f
        const val LONGEST_FACTOR = 1.6f

        fun adaptiveFadeMs(baseMs: Long, similarity: Float?): Long {
            if (similarity == null || similarity.isNaN()) return baseMs
            val t = ((similarity - SIMILARITY_DIFFERENT) / (SIMILARITY_ALIKE - SIMILARITY_DIFFERENT)).coerceIn(0f, 1f)
            val factor = SHORTEST_FACTOR + t * (LONGEST_FACTOR - SHORTEST_FACTOR)
            return (baseMs * factor).toLong().coerceIn(MIN_FADE_MS, MAX_FADE_MS)
        }
    }
}
