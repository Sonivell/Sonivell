package com.juscelino.sonivell.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Repeat { Off, All, One }

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.Off,
    val volume: Float = 0.8f,
    val sourceLabel: String = "",
    val error: String? = null,
    val smart: Boolean = false,
) {
    val current: Track? get() = queue.getOrNull(index)
    val upNext: List<Track> get() = if (index in queue.indices) queue.drop(index + 1) else emptyList()
}

data class LibraryState(
    val library: Library = Library(emptyList()),
    val scanning: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val toolsMissing: Boolean = false,
)

/** Une biblioteca, fila, reprodução, curtidas e histórico. Toda a interface lê daqui. */
private const val SMART_BATCH = 30
private const val SMART_REFILL_AT = 3

class Controller {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val user = UserData()

    private val _settings = MutableStateFlow(SettingsStore.load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _player = MutableStateFlow(PlayerState(shuffle = _settings.value.shuffle, repeat = Repeat.values()[_settings.value.repeat], volume = _settings.value.volume, smart = _settings.value.smart))
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    private val _favorites = MutableStateFlow(user.favorites)
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    /** Faixa a retomar ao abrir o app (a última que estava tocando) e a posição. */
    private val _resume = MutableStateFlow<Pair<Track, Long>?>(null)
    val resume: StateFlow<Pair<Track, Long>?> = _resume.asStateFlow()

    private var baseOrder: List<Track> = emptyList()
    private var trackStartedAt = 0L

    private val engine = AudioEngine(
        onEnded = { scope.launch { onTrackEnded() } },
        onError = { msg -> _player.update { it.copy(playing = false, error = msg) } },
    )

    val audioAvailable: Boolean = AudioEngine.available()

    /** Aviso quando o som não passa pelo servidor de som do sistema (ver [AudioRoute]). */
    private val _audioRoute = MutableStateFlow(AudioRoute.missingBridge())
    val audioRoute: StateFlow<AudioRoute.Server?> = _audioRoute.asStateFlow()
    fun dismissAudioRoute() { _audioRoute.value = null }

    /** Aleatório inteligente (motor do LatentJam): análise em segundo plano e filas pelo som. */
    val smart = SmartBridge(scope)
    private var smartJob: Job? = null

    init {
        engine.volume = _settings.value.volume
        smart.setArtistVariety(_settings.value.artistVariety)
        scope.launch {
            while (true) {
                delay(250)
                val s = _player.value
                if (s.playing && s.current != null) _player.update { it.copy(positionMs = engine.positionMs()) }
            }
        }
        scope.launch {
            while (true) {
                delay(10_000)
                val s = _player.value
                s.current?.let { user.saveResume(it.id, s.positionMs) }
            }
        }
        refresh()
    }

    // ---- biblioteca -----------------------------------------------------------------------------

    fun refresh() {
        if (_library.value.scanning) return
        scope.launch {
            if (!Scanner.toolsAvailable()) {
                _library.update { it.copy(toolsMissing = true); }
                return@launch
            }
            _library.update { it.copy(scanning = true, progress = null, toolsMissing = false) }
            val tracks = withContext(Dispatchers.IO) {
                Scanner.scan(_settings.value.musicDirs.map(::File)) { done, total ->
                    _library.update { it.copy(progress = done to total) }
                }
            }
            val lib = Library(tracks)
            _library.update { LibraryState(lib, scanning = false, progress = null) }
            smart.analyse(lib.tracks)
            if (_resume.value == null && _player.value.current == null) {
                user.loadResume()?.let { (id, pos) -> lib.byId[id]?.let { _resume.value = it to pos } }
            }
        }
    }

    fun updateSettings(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        val dirsChanged = next.musicDirs != _settings.value.musicDirs
        if (next.artistVariety != _settings.value.artistVariety) smart.setArtistVariety(next.artistVariety)
        _settings.value = next
        SettingsStore.save(next)
        if (dirsChanged) refresh()
    }

    fun setCompact(on: Boolean) {
        if (_settings.value.compact != on) updateSettings { it.copy(compact = on) }
    }

    fun toggleCompact() = setCompact(!_settings.value.compact)

    fun saveCompactPosition(x: Int, y: Int) {
        val s = _settings.value
        if (s.compactX != x || s.compactY != y) updateSettings { it.copy(compactX = x, compactY = y) }
    }

    // ---- reprodução -----------------------------------------------------------------------------

    fun playFrom(tracks: List<Track>, index: Int, label: String = "", startMs: Long = 0L) {
        if (tracks.isEmpty() || index !in tracks.indices) return
        recordCurrent(completed = false)
        baseOrder = tracks
        if (_player.value.smart && smart.available) {
            val seed = tracks[index]
            startTrack(listOf(seed), 0, label.ifEmpty { "SMART" }, startMs)
            smartFill(seed, replace = true)
            return
        }
        val shuffle = _player.value.shuffle
        val queue = if (shuffle) listOf(tracks[index]) + tracks.filterIndexed { i, _ -> i != index }.shuffled() else tracks
        val idx = if (shuffle) 0 else index
        startTrack(queue, idx, label, startMs)
    }

    fun playShuffled(tracks: List<Track>, label: String = "") {
        if (tracks.isEmpty()) return
        _player.update { it.copy(shuffle = true) }
        persistModes()
        playFrom(tracks, tracks.indices.random(), label)
    }

    private fun startTrack(queue: List<Track>, index: Int, label: String, startMs: Long = 0L) {
        val track = queue[index]
        trackStartedAt = System.currentTimeMillis()
        _resume.value = null
        _player.update { it.copy(queue = queue, index = index, playing = true, positionMs = startMs, sourceLabel = label.ifEmpty { it.sourceLabel }, error = null) }
        engine.play(track.path, startMs)
    }

    private fun recordCurrent(completed: Boolean) {
        val s = _player.value
        val t = s.current ?: return
        val listened = if (completed) t.durationMs else s.positionMs
        user.record(Listen(t.id, trackStartedAt.takeIf { it > 0 } ?: System.currentTimeMillis(), listened, completed))
    }

    private fun onTrackEnded() {
        recordCurrent(completed = true)
        val s = _player.value
        smartTopUp()
        when {
            s.repeat == Repeat.One && s.current != null -> startTrack(s.queue, s.index, "")
            s.index + 1 < s.queue.size -> startTrack(s.queue, s.index + 1, "")
            s.repeat == Repeat.All && s.queue.isNotEmpty() -> {
                val q = if (s.shuffle) baseOrder.shuffled() else baseOrder.ifEmpty { s.queue }
                startTrack(q, 0, "")
            }
            else -> {
                engine.stop()
                _player.update { it.copy(playing = false, positionMs = 0L) }
            }
        }
    }

    fun togglePlay() {
        val s = _player.value
        if (s.current == null) {
            _resume.value?.let { (t, pos) -> playFrom(listOf(t), 0, "", pos) }
            return
        }
        if (s.playing) {
            engine.pause()
            _player.update { it.copy(playing = false, positionMs = engine.positionMs()) }
            s.current?.let { user.saveResume(it.id, engine.positionMs()) }
        } else {
            if (engine.isActive) engine.resume() else engine.play(s.current!!.path, s.positionMs)
            _player.update { it.copy(playing = true) }
        }
    }

    fun next() {
        val s = _player.value
        if (s.current == null) return
        recordCurrent(completed = false)
        smartTopUp()
        if (s.index + 1 < s.queue.size) startTrack(s.queue, s.index + 1, "")
        else if (s.repeat == Repeat.All) startTrack(if (s.shuffle) baseOrder.shuffled() else baseOrder.ifEmpty { s.queue }, 0, "")
    }

    fun previous() {
        val s = _player.value
        if (s.current == null) return
        if (s.positionMs > 3_000 || s.index == 0) { seek(0L); return }
        recordCurrent(completed = false)
        startTrack(s.queue, s.index - 1, "")
    }

    fun seek(ms: Long) {
        val s = _player.value
        val t = s.current ?: return
        val to = ms.coerceIn(0L, (t.durationMs - 500).coerceAtLeast(0L))
        _player.update { it.copy(positionMs = to) }
        if (s.playing) engine.play(t.path, to)
        else engine.stop()
    }

    fun setVolume(v: Float) {
        engine.volume = v
        _player.update { it.copy(volume = v.coerceIn(0f, 1f)) }
        persistModes()
    }

    fun toggleSmart() {
        val on = !_player.value.smart
        _player.update { it.copy(smart = on) }
        updateSettings { it.copy(smart = on) }
        if (on) _player.value.current?.let { smartFill(it, replace = true) }
    }

    /** Monta (replace) ou estende a fila a partir de [seed], escolhendo faixas que soam parecidas. */
    private fun smartFill(seed: Track, replace: Boolean) {
        if (!smart.available) return
        smartJob?.cancel()
        smartJob = scope.launch {
            val lib = _library.value.library
            val s0 = _player.value
            val inQueue = s0.queue.map { it.id }.toSet()
            val pool = lib.tracks.filter { it.id != seed.id && (replace || it.id !in inQueue) }
            val history = user.history.takeLast(300)
            val ids = if (replace) {
                smart.startQueue(seed, pool, SMART_BATCH, history, lib.byId::get)
            } else {
                val preceding = s0.queue.dropLast(1).takeLast(40).map { it.id }
                smart.continueQueue(seed, pool, SMART_BATCH, preceding, history, lib.byId::get)
            }
            val picked = ids.mapNotNull { lib.byId[it] }
            if (picked.isEmpty()) return@launch
            _player.update { st ->
                if (replace) {
                    if (st.current?.id != seed.id) st else st.copy(queue = st.queue.take(st.index + 1) + picked)
                } else {
                    if (st.queue.lastOrNull()?.id != seed.id) st else st.copy(queue = st.queue + picked)
                }
            }
        }
    }

    /** Perto do fim da fila SMART, pede mais faixas a partir da última planejada. */
    private fun smartTopUp() {
        val s = _player.value
        if (!s.smart || !smart.available || s.queue.isEmpty()) return
        if (s.upNext.size <= SMART_REFILL_AT) smartFill(s.queue.last(), replace = false)
    }

    fun toggleShuffle() {
        val s = _player.value
        val on = !s.shuffle
        val cur = s.current
        val queue = when {
            cur == null -> s.queue
            on -> listOf(cur) + s.queue.filter { it.id != cur.id }.shuffled()
            else -> {
                val base = baseOrder.ifEmpty { s.queue }
                if (base.any { it.id == cur.id }) base else s.queue
            }
        }
        val idx = if (cur == null) -1 else queue.indexOfFirst { it.id == cur.id }
        _player.update { it.copy(shuffle = on, queue = queue, index = idx) }
        persistModes()
    }

    fun cycleRepeat() {
        _player.update { it.copy(repeat = Repeat.values()[(it.repeat.ordinal + 1) % 3]) }
        persistModes()
    }

    private fun persistModes() {
        val s = _player.value
        updateSettings { it.copy(shuffle = s.shuffle, repeat = s.repeat.ordinal, volume = s.volume) }
    }

    fun playNext(track: Track) {
        _player.update { s ->
            if (s.current == null) s else s.copy(queue = s.queue.toMutableList().also { it.add(s.index + 1, track) })
        }
    }

    fun addToQueue(track: Track) {
        _player.update { s -> if (s.current == null) s else s.copy(queue = s.queue + track) }
    }

    fun removeFromQueue(position: Int) {
        _player.update { s ->
            if (position <= s.index || position !in s.queue.indices) s
            else s.copy(queue = s.queue.toMutableList().also { it.removeAt(position) })
        }
    }

    fun playQueueAt(position: Int) {
        val s = _player.value
        if (position !in s.queue.indices) return
        recordCurrent(completed = false)
        startTrack(s.queue, position, "")
    }

    fun toggleFavorite(track: Track) {
        val on = track.id !in _favorites.value
        user.setFavorite(track.id, on)
        _favorites.value = user.favorites
    }

    fun shutdown() {
        recordCurrent(completed = false)
        _player.value.current?.let { user.saveResume(it.id, _player.value.positionMs) }
        engine.close()
    }
}
