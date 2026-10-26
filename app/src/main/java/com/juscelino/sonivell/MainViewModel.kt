package com.juscelino.sonivell

import androidx.annotation.StringRes
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juscelino.sonivell.data.Album
import com.juscelino.sonivell.data.Artist
import com.juscelino.sonivell.data.Folder
import com.juscelino.sonivell.data.Genre
import com.juscelino.sonivell.data.M3u
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.PlaylistStore
import com.juscelino.sonivell.data.SavedSession
import com.juscelino.sonivell.data.SessionStore
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.juscelino.sonivell.data.ListenEvent
import com.juscelino.sonivell.data.ListenSessionTracker
import com.juscelino.sonivell.data.ListeningHistory
import com.juscelino.sonivell.data.MusicRepository
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.playback.PlayerConnection
import com.juscelino.sonivell.playback.PlayerState
import com.juscelino.sonivell.playback.toTrack
import com.juscelino.sonivell.smart.SmartEngine
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.smart.createSmartEngine
import com.juscelino.sonivell.data.ForYou
import com.juscelino.sonivell.data.ForYouState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(@StringRes val label: Int) {
    Musicas(R.string.tab_songs),
    Playlists(R.string.tab_playlists),
    Albuns(R.string.tab_albums),
    Artistas(R.string.tab_artists),
    Generos(R.string.tab_genres),
    Pastas(R.string.tab_folders),
}
enum class SortOrder(@StringRes val label: Int) {
    Titulo(R.string.sort_title),
    Artista(R.string.sort_artist),
    Album(R.string.sort_album),
    Duracao(R.string.sort_duration),
    Ano(R.string.sort_year),
    Recentes(R.string.sort_recent),
}

/** Temporizador para parar a música: num horário ([endsAtMs]) ou ao fim da faixa atual. */
data class SleepTimer(val endsAtMs: Long? = null, val endOfTrack: Boolean = false) {
    val active: Boolean get() = endsAtMs != null || endOfTrack
}

/** Estado do botão de aleatório: desligado, aleatório comum ou SMART (só na edição smart). */
enum class ShuffleMode { OFF, ON, SMART }

data class LibraryState(
    val loading: Boolean = true,
    val allTracks: List<Track> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val recentAlbums: List<Album> = emptyList(),
    val query: String = "",
    val tab: LibraryTab = LibraryTab.Musicas,
    val sort: SortOrder = SortOrder.Titulo,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository(app)
    val player = PlayerConnection(app, viewModelScope)
    val playerState: StateFlow<PlayerState> = player.state

    private val prefs = app.getSharedPreferences("sonivell", Context.MODE_PRIVATE)

    /** Motor SMART: o do LatentJam na edição smart, um "nada" na lite. */
    val smart: SmartEngine = createSmartEngine(app)
    val smartProgress: StateFlow<SmartProgress> = smart.progress
    private val smartMode = MutableStateFlow(smart.isAvailable && prefs.getBoolean("smart_mode", false))
    private var planningJob: Job? = null
    /** Última faixa a partir da qual o SMART não achou continuação: evita pedir de novo em loop. */
    private var exhaustedSeedId: Long? = null

    /** Histórico de escuta local: alimenta o SMART e a tela Para você. */
    val history = ListeningHistory(app)
    private val session = ListenSessionTracker()

    /** Sessão salva: ao abrir o app, a fila e a posição voltam como estavam. */
    private val sessionStore = SessionStore(prefs)
    private var lastSessionSaveMs = 0L
    private var lastSavedQueue: List<String> = emptyList()
    private var lastSavedPlaying = false

    val playlistStore = PlaylistStore(app)
    val playlists: StateFlow<List<Playlist>> = playlistStore.playlists

    private val _sleepTimer = MutableStateFlow(SleepTimer())
    val sleepTimer: StateFlow<SleepTimer> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null
    private var sleepTrackId: String? = null

    private val allTracks = MutableStateFlow<List<Track>?>(null)
    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(LibraryTab.Musicas)
    private val sort = MutableStateFlow(
        SortOrder.entries.getOrElse(prefs.getInt("sort", 0)) { SortOrder.Titulo }
    )

    private val _favorites = MutableStateFlow(
        prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    )
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    val library: StateFlow<LibraryState> = combine(allTracks, query, tab, sort) { all, q, t, s ->
        if (all == null) return@combine LibraryState(loading = true, query = q, tab = t, sort = s)
        val filtered = if (q.isBlank()) all else all.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) ||
                it.genre?.contains(q, true) == true || it.fileName?.contains(q, true) == true
        }
        val sorted = when (s) {
            SortOrder.Titulo -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.Artista -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }))
            SortOrder.Album -> filtered.sortedWith(
                compareBy({ it.album.lowercase() }, { it.discNumber ?: 0 }, { it.trackNumber ?: 0 }, { it.title.lowercase() })
            )
            SortOrder.Duracao -> filtered.sortedByDescending { it.durationMs }
            SortOrder.Ano -> filtered.sortedWith(compareByDescending<Track> { it.year ?: 0 }.thenBy { it.album.lowercase() })
            SortOrder.Recentes -> filtered.sortedByDescending { it.dateAdded }
        }
        val albums = filtered.groupBy { it.albumId }.map { (id, ts) ->
            Album(id, ts.first().album, ts.first().artist, ts)
        }.sortedBy { it.title.lowercase() }
        LibraryState(
            loading = false,
            allTracks = all,
            tracks = sorted,
            albums = albums,
            artists = filtered.groupBy { it.artist }.map { (n, ts) -> Artist(n, ts) }.sortedBy { it.name.lowercase() },
            folders = filtered.groupBy { it.folder }.map { (p, ts) -> Folder(p, ts) }.sortedBy { it.name.lowercase() },
            // Uma faixa pode ter vários gêneros na mesma tag ("Rock; Indie").
            genres = filtered
                .flatMap { t -> t.genre.orEmpty().split(';', '/', ',').map { it.trim() }.filter { it.isNotEmpty() }.map { it to t } }
                .groupBy({ it.first.lowercase() }, { it })
                .map { (_, pairs) -> Genre(pairs.first().first, pairs.map { it.second }.distinctBy { it.id }) }
                .sortedBy { it.name.lowercase() },
            recentAlbums = all.sortedByDescending { it.dateAdded }
                .distinctBy { it.albumId }
                .take(6)
                .map { t -> Album(t.albumId, t.album, t.artist, all.filter { it.albumId == t.albumId }) },
            query = q,
            tab = t,
            sort = s,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    /** Faixa atual com dados completos (da biblioteca, quando disponível). */
    val currentTrack: StateFlow<Track?> = combine(playerState, allTracks) { st, all ->
        val item = st.current ?: return@combine null
        val id = item.mediaId.toLongOrNull()
        all?.firstOrNull { it.id == id } ?: item.toTrack(getApplication<Application>())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Tela Para você: recalculada a cada escuta registrada, curtida ou mudança na biblioteca. */
    val forYou: StateFlow<ForYouState> = combine(allTracks, _favorites, history.revision) { all, favs, _ ->
        if (all == null) ForYouState()
        else ForYou.compute(all, history.recent(), favs, withMixes = smart.isAvailable)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForYouState())

    val shuffleMode: StateFlow<ShuffleMode> = combine(playerState, smartMode) { st, isSmart ->
        when {
            isSmart -> ShuffleMode.SMART
            st.shuffle -> ShuffleMode.ON
            else -> ShuffleMode.OFF
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShuffleMode.OFF)

    init {
        viewModelScope.launch { playlistStore.load() }

        // Restaura a sessão quando o player conectar e a biblioteca carregar (uma vez só).
        viewModelScope.launch {
            combine(player.connected, allTracks) { connected, all -> if (connected) all else null }
                .first { it != null }
                ?.let(::restoreSession)
        }

        // Fila SMART "infinita": quando faltam poucas faixas, o motor planeja mais.
        viewModelScope.launch {
            playerState.collect { st ->
                // Registra cada escuta quando a reprodução sai da faixa.
                session.observe(
                    currentId = st.current?.mediaId?.toLongOrNull(),
                    positionMs = st.positionMs,
                    durationMs = st.durationMs,
                    isPlaying = st.isPlaying,
                    nowMs = System.currentTimeMillis(),
                )?.let { event -> onListenFinished(event, st) }

                maybeSaveSession(st)
                checkSleepAtEndOfTrack(st)

                if (smartMode.value && st.hasMedia && st.upNext.size < SMART_TOP_UP_BELOW &&
                    planningJob?.isActive != true
                ) {
                    topUpSmartQueue(st)
                }
            }
        }
    }

    // ---- Sessão ----

    private fun restoreSession(all: List<Track>) {
        val saved = sessionStore.load() ?: return
        val byId = all.associateBy { it.id }
        val tracks = saved.queueIds.mapNotNull(byId::get)
        if (tracks.isEmpty()) return
        // Se alguma faixa sumiu do aparelho, a atual pode ter mudado de posição.
        val currentId = saved.queueIds.getOrNull(saved.index)
        val index = tracks.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
        player.restore(tracks, index, if (index == saved.index) saved.positionMs else 0L, saved.shuffle, saved.repeatMode)
    }

    private fun maybeSaveSession(st: PlayerState) {
        if (!st.hasMedia) return
        val now = System.currentTimeMillis()
        val ids = st.queue.map { it.mediaId }
        val changed = ids != lastSavedQueue || st.isPlaying != lastSavedPlaying
        if (!changed && now - lastSessionSaveMs < SESSION_SAVE_EVERY_MS) return
        lastSessionSaveMs = now
        lastSavedQueue = ids
        lastSavedPlaying = st.isPlaying
        sessionStore.save(
            SavedSession(
                queueIds = ids.mapNotNull { it.toLongOrNull() },
                index = st.currentIndex,
                positionMs = st.positionMs,
                shuffle = st.shuffle,
                repeatMode = player.rawRepeatMode,
            )
        )
    }

    // ---- Fila ----

    /** Toca uma coleção (álbum, playlist…) em ordem aleatória. */
    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (smartMode.value) setSmartMode(false)
        player.play(tracks, tracks.indices.random(), shuffle = true)
    }

    fun playNext(tracks: List<Track>) = player.playNext(tracks)
    fun addToQueue(tracks: List<Track>) = player.append(tracks)
    fun removeFromQueue(index: Int) = player.removeItem(index)

    // ---- Playlists ----

    fun tracksOf(playlist: Playlist): List<Track> {
        val byId = allTracks.value.orEmpty().associateBy { it.id }
        return playlist.trackIds.mapNotNull(byId::get)
    }

    fun createPlaylist(name: String, tracks: List<Track> = emptyList(), then: (Long) -> Unit = {}) {
        viewModelScope.launch { then(playlistStore.create(name, tracks.map { it.id })) }
    }

    fun renamePlaylist(id: Long, name: String) { viewModelScope.launch { playlistStore.rename(id, name) } }
    fun deletePlaylist(id: Long) { viewModelScope.launch { playlistStore.delete(id) } }
    fun addToPlaylist(id: Long, tracks: List<Track>) {
        viewModelScope.launch { playlistStore.addTracks(id, tracks.map { it.id }) }
    }
    fun removeFromPlaylist(id: Long, index: Int) { viewModelScope.launch { playlistStore.removeAt(id, index) } }
    fun movePlaylistItem(id: Long, from: Int, to: Int) { viewModelScope.launch { playlistStore.move(id, from, to) } }

    /** A fila atual (inclusive a faixa que está tocando) vira uma playlist. */
    fun saveQueueAsPlaylist(name: String) {
        val byId = allTracks.value.orEmpty().associateBy { it.id }
        val tracks = playerState.value.queue.mapNotNull { it.mediaId.toLongOrNull()?.let(byId::get) }
        createPlaylist(name, tracks)
    }

    /** Exporta como M3U para o arquivo escolhido pelo usuário. Devolve false se não deu. */
    suspend fun exportPlaylist(id: Long, target: Uri): Boolean {
        val playlist = playlists.value.firstOrNull { it.id == id } ?: return false
        val text = M3u.export(tracksOf(playlist))
        return withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(target, "wt")!!.use {
                    it.write(text.toByteArray())
                }
            }.isSuccess
        }
    }

    /** Importa um M3U; devolve quantas faixas foram encontradas no aparelho (-1 se o arquivo não abriu). */
    suspend fun importPlaylist(source: Uri, name: String): Int {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(source)!!.use { it.readBytes().decodeToString() }
            }.getOrNull()
        } ?: return -1
        val ids = M3u.import(text, allTracks.value.orEmpty())
        if (ids.isNotEmpty()) playlistStore.create(name, ids)
        return ids.size
    }

    // ---- Temporizador ----

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepTrackId = null
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimer.value = SleepTimer(endsAtMs = endsAt)
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            player.pause()
            _sleepTimer.value = SleepTimer()
        }
    }

    fun sleepAtEndOfTrack() {
        sleepJob?.cancel()
        sleepTrackId = playerState.value.current?.mediaId
        _sleepTimer.value = SleepTimer(endOfTrack = true)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepTrackId = null
        _sleepTimer.value = SleepTimer()
    }

    private fun checkSleepAtEndOfTrack(st: PlayerState) {
        val armed = sleepTrackId ?: return
        if (!_sleepTimer.value.endOfTrack) return
        val currentId = st.current?.mediaId
        // A faixa marcada acabou (passou para outra, ou a fila terminou): pausa no começo da próxima.
        if (currentId != armed || (!st.isPlaying && st.durationMs > 0 && st.positionMs >= st.durationMs - 500)) {
            player.pause()
            if (currentId != armed) player.seekTo(0)
            cancelSleepTimer()
        }
    }

    fun onPermissionGranted() {
        player.connect()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val tracks = repository.loadTracks()
            allTracks.value = tracks
            if (smart.isAvailable) smart.analyse(tracks)
        }
    }

    fun setQuery(q: String) { query.value = q }
    fun setTab(t: LibraryTab) { tab.value = t }
    fun cycleSort() {
        sort.update { SortOrder.entries[(it.ordinal + 1) % SortOrder.entries.size] }
        prefs.edit().putInt("sort", sort.value.ordinal).apply()
    }

    fun toggleFavorite(id: Long) {
        _favorites.update { if (id in it) it - id else it + id }
        prefs.edit().putStringSet("favorites", _favorites.value.map { it.toString() }.toSet()).apply()
    }

    fun playAllShuffled() {
        val all = allTracks.value.orEmpty()
        if (all.isEmpty()) return
        if (smartMode.value) playFrom(all, all.indices.random())
        else player.play(all, all.indices.random(), shuffle = true)
    }

    /**
     * Toca [tracks] a partir de [index]. No modo SMART, só a faixa escolhida vem da lista:
     * o resto da fila é montado pelo motor, com músicas que soam parecidas.
     */
    fun playFrom(tracks: List<Track>, index: Int) {
        if (!smartMode.value) {
            player.play(tracks, index)
            return
        }
        val seed = tracks.getOrNull(index) ?: return
        player.play(listOf(seed), 0)
        planningJob?.cancel()
        planningJob = viewModelScope.launch {
            val planned = planSmart(seed, excluding = setOf(seed.id))
            // Se o SMART ainda não tiver dados, segue a lista normal em vez de parar a música.
            player.append(planned.ifEmpty { tracks.drop(index + 1) })
        }
    }

    /** Mix pelo som: fila SMART a partir de [seed], mesmo com o aleatório SMART desligado. */
    fun playMix(seed: Track) {
        player.play(listOf(seed), 0)
        planningJob?.cancel()
        planningJob = viewModelScope.launch {
            player.append(planSmart(seed, excluding = setOf(seed.id)))
        }
    }

    /** Desligado → aleatório → SMART (edição smart) → desligado. */
    fun cycleShuffle() {
        when (shuffleMode.value) {
            ShuffleMode.OFF -> player.setShuffle(true)
            ShuffleMode.ON -> if (smart.isAvailable) {
                player.setShuffle(false)
                setSmartMode(true)
                replanFromCurrent()
            } else {
                player.setShuffle(false)
            }
            ShuffleMode.SMART -> setSmartMode(false)
        }
    }

    private fun setSmartMode(on: Boolean) {
        smartMode.value = on
        prefs.edit().putBoolean("smart_mode", on).apply()
        if (!on) planningJob?.cancel()
    }

    /** Ao ligar o SMART no meio da música, refaz o "a seguir" a partir da faixa atual. */
    private fun replanFromCurrent() {
        val current = currentTrack.value ?: return
        planningJob?.cancel()
        planningJob = viewModelScope.launch {
            val planned = planSmart(current, excluding = setOf(current.id))
            if (planned.isNotEmpty()) player.replaceUpNext(planned)
        }
    }

    private fun onListenFinished(event: ListenEvent, st: PlayerState) {
        viewModelScope.launch { history.record(event) }
        // Pulou no modo SMART: o motor refaz o "a seguir" levando o pulo em conta, como no
        // LatentJam. (Se uma fila nova já está sendo montada, ela ganha.)
        if (smartMode.value && event.skipped && planningJob?.isActive != true) replanAfterSkip(st)
    }

    private fun replanAfterSkip(st: PlayerState) {
        val queueIds = st.queue.mapNotNull { it.mediaId.toLongOrNull() }
        val currentIndex = st.currentIndex.takeIf { it in queueIds.indices } ?: return
        val byId = allTracks.value.orEmpty().associateBy { it.id }
        val seed = byId[queueIds[currentIndex]] ?: return
        val playedOrCurrent = queueIds.take(currentIndex + 1).toSet()
        planningJob = viewModelScope.launch {
            val ids = smart.continueQueue(
                seed = seed,
                library = allTracks.value.orEmpty().filter { it.id !in playedOrCurrent },
                length = SMART_PLAN_LENGTH,
                preceding = queueIds.take(currentIndex).takeLast(SMART_PLAN_LENGTH),
                history = history.recent(SMART_HISTORY_LIMIT),
            )
            val planned = ids.mapNotNull(byId::get)
            if (planned.isNotEmpty()) player.replaceUpNext(planned)
        }
    }

    private fun topUpSmartQueue(st: PlayerState) {
        val queueIds = st.queue.mapNotNull { it.mediaId.toLongOrNull() }
        val lastId = queueIds.lastOrNull() ?: return
        val byId = allTracks.value.orEmpty().associateBy { it.id }
        val seed = byId[lastId] ?: return
        if (lastId == exhaustedSeedId) return
        planningJob = viewModelScope.launch {
            val queued = queueIds.toSet()
            val ids = smart.continueQueue(
                seed = seed,
                library = allTracks.value.orEmpty().filter { it.id !in queued },
                length = SMART_PLAN_LENGTH,
                preceding = queueIds.dropLast(1).takeLast(SMART_PLAN_LENGTH),
                history = history.recent(SMART_HISTORY_LIMIT),
            )
            val more = ids.mapNotNull(byId::get)
            if (more.isEmpty()) exhaustedSeedId = lastId
            player.append(more)
        }
    }

    private suspend fun planSmart(seed: Track, excluding: Set<Long>): List<Track> {
        val all = allTracks.value.orEmpty()
        val byId = all.associateBy { it.id }
        return smart.startQueue(seed, all, SMART_PLAN_LENGTH, history.recent(SMART_HISTORY_LIMIT))
            .filter { it !in excluding }
            .mapNotNull(byId::get)
    }

    private companion object {
        const val SMART_PLAN_LENGTH = 25
        const val SMART_TOP_UP_BELOW = 5
        const val SMART_HISTORY_LIMIT = 2_000
        const val SESSION_SAVE_EVERY_MS = 5_000L
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
