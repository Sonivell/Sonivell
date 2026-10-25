package com.juscelino.sonivell

import androidx.annotation.StringRes
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juscelino.sonivell.data.Album
import com.juscelino.sonivell.data.Artist
import com.juscelino.sonivell.data.Folder
import com.juscelino.sonivell.data.MusicRepository
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.playback.PlayerConnection
import com.juscelino.sonivell.playback.PlayerState
import com.juscelino.sonivell.playback.toTrack
import com.juscelino.sonivell.smart.SmartEngine
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.smart.createSmartEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(@StringRes val label: Int) {
    Musicas(R.string.tab_songs), Albuns(R.string.tab_albums), Artistas(R.string.tab_artists), Pastas(R.string.tab_folders)
}
enum class SortOrder(@StringRes val label: Int) {
    Titulo(R.string.sort_title), Artista(R.string.sort_artist), Recentes(R.string.sort_recent)
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
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
        val sorted = when (s) {
            SortOrder.Titulo -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.Artista -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }))
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

    val shuffleMode: StateFlow<ShuffleMode> = combine(playerState, smartMode) { st, isSmart ->
        when {
            isSmart -> ShuffleMode.SMART
            st.shuffle -> ShuffleMode.ON
            else -> ShuffleMode.OFF
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ShuffleMode.OFF)

    init {
        // Fila SMART "infinita": quando faltam poucas faixas, o motor planeja mais.
        viewModelScope.launch {
            playerState.collect { st ->
                if (smartMode.value && st.hasMedia && st.upNext.size < SMART_TOP_UP_BELOW &&
                    planningJob?.isActive != true
                ) {
                    topUpSmartQueue(st)
                }
            }
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
            )
            val more = ids.mapNotNull(byId::get)
            if (more.isEmpty()) exhaustedSeedId = lastId
            player.append(more)
        }
    }

    private suspend fun planSmart(seed: Track, excluding: Set<Long>): List<Track> {
        val all = allTracks.value.orEmpty()
        val byId = all.associateBy { it.id }
        return smart.startQueue(seed, all, SMART_PLAN_LENGTH)
            .filter { it !in excluding }
            .mapNotNull(byId::get)
    }

    private companion object {
        const val SMART_PLAN_LENGTH = 25
        const val SMART_TOP_UP_BELOW = 5
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
