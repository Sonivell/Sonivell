package com.juscelino.sonivell

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) { Musicas("Músicas"), Albuns("Álbuns"), Artistas("Artistas"), Pastas("Pastas") }
enum class SortOrder(val label: String) { Titulo("Título"), Artista("Artista"), Recentes("Adicionadas") }

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
        all?.firstOrNull { it.id == id } ?: item.toTrack()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPermissionGranted() {
        player.connect()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { allTracks.value = repository.loadTracks() }
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
        if (all.isNotEmpty()) player.play(all, all.indices.random(), shuffle = true)
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
