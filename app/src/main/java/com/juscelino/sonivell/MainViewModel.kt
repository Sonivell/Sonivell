package com.juscelino.sonivell

import androidx.annotation.StringRes
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.juscelino.sonivell.data.Album
import com.juscelino.sonivell.data.Artist
import com.juscelino.sonivell.data.Folder
import com.juscelino.sonivell.data.AppSettings
import com.juscelino.sonivell.data.SettingsStore
import com.juscelino.sonivell.data.Ranked
import com.juscelino.sonivell.data.Stats
import com.juscelino.sonivell.data.TagEdit
import com.juscelino.sonivell.data.TagOverrides
import com.juscelino.sonivell.data.sortKey
import com.juscelino.sonivell.data.StatsPeriod
import com.juscelino.sonivell.data.StatsState
import com.juscelino.sonivell.data.historyCsv
import com.juscelino.sonivell.playback.AudioEffects
import com.juscelino.sonivell.playback.EqualizerInfo
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
import com.juscelino.sonivell.data.TasteModel
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
import kotlinx.coroutines.flow.collectLatest
import com.juscelino.sonivell.system.PlaylistPublisher
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import android.os.Looper
import android.os.Handler
import android.database.ContentObserver
import kotlinx.coroutines.Job
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import com.juscelino.sonivell.data.DuplicateGroup
import com.juscelino.sonivell.data.MetadataIssues
import com.juscelino.sonivell.data.Organize
import com.juscelino.sonivell.data.PlayStats
import com.juscelino.sonivell.data.RecommendationPrefs
import com.juscelino.sonivell.data.Search
import com.juscelino.sonivell.data.SmartPlaylist
import com.juscelino.sonivell.data.SmartPlaylistStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.transformLatest
import com.juscelino.sonivell.data.LyricsRepository
import com.juscelino.sonivell.ui.lyrics.LyricsState
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

enum class LibraryTab(@StringRes val label: Int) {
    Tudo(R.string.tab_all),
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

/** O que a tela do equalizador mostra. Níveis em milibéis; [preset] null = ajuste próprio. */
data class EqualizerState(
    val info: EqualizerInfo? = null,
    val enabled: Boolean = false,
    val levels: List<Int> = emptyList(),
    val bass: Int = 0,
    val preset: String? = null,
)

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
    val tab: LibraryTab = LibraryTab.Tudo,
    val sort: SortOrder = SortOrder.Titulo,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository(app)
    val player = PlayerConnection(app, viewModelScope)
    val playerState: StateFlow<PlayerState> = player.state

    private val _smartSeeds = MutableStateFlow<Map<Long, Long>>(emptyMap())

    /** Faixa escolhida pelo SMART → a faixa a partir da qual o motor a escolheu ("Por que tocou"). */
    val smartSeeds: StateFlow<Map<Long, Long>> = _smartSeeds

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

    /** O que o SMART e o Para você não devem sugerir; grupos marcados como "não são duplicatas". */
    val recommendations = RecommendationPrefs(prefs)
    val smartPlaylistStore = SmartPlaylistStore(app)

    /** Sessão salva: ao abrir o app, a fila e a posição voltam como estavam. */
    private val sessionStore = SessionStore(prefs)
    private var lastSessionSaveMs = 0L
    private var lastSavedQueue: List<String> = emptyList()
    private var lastSavedPlaying = false

    val playlistStore = PlaylistStore(app)
    val playlists: StateFlow<List<Playlist>> = playlistStore.playlists

    /** Ajustes do app (tema, cor, comportamento). */
    private val settingsStore = SettingsStore(prefs)
    val settings: StateFlow<AppSettings> = settingsStore.settings

    /** O gosto aprendido no celular (edição smart): perfil + previsor de pulo. */
    private val taste = TasteModel(java.io.File(app.filesDir, "gosto.bin"))
    val tasteStatus: StateFlow<TasteModel.Status> = taste.status
    private var trainJob: Job? = null

    private val _tasteBoosts = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /** Faixas que subiram na fila por combinarem com o seu gosto → período do dia (0 madrugada … 3 noite). */
    val tasteBoosts: StateFlow<Map<Long, Int>> = _tasteBoosts

    private val vectorLock = kotlinx.coroutines.sync.Mutex()
    private val vectorCache = object : LinkedHashMap<Long, FloatArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, FloatArray>?) = size > 1500
    }

    /** Vetores de som, com cache (o motor só devolve os das faixas já analisadas). */
    private suspend fun vectorsFor(ids: List<Long>): Map<Long, FloatArray> {
        val missing = vectorLock.withLock { ids.filter { it !in vectorCache } }
        val fetched = if (missing.isEmpty()) emptyMap() else smart.embeddings(missing)
        return vectorLock.withLock {
            vectorCache.putAll(fetched)
            ids.mapNotNull { id -> vectorCache[id]?.let { id to it } }.toMap()
        }
    }

    /** Treina de novo quando há escutas novas o bastante (ou quando [force]). Roda em segundo plano. */
    fun maybeTrainTaste(force: Boolean = false) {
        if (!smart.isAvailable || !settings.value.learnTaste) return
        if (trainJob?.isActive == true) return
        trainJob = viewModelScope.launch(Dispatchers.Default) {
            val events = history.recent()
            val fresh = events.size - taste.examplesSeen
            val due = force || (!taste.status.value.trained && events.size >= 10) || fresh >= RETRAIN_EVERY ||
                System.currentTimeMillis() - taste.status.value.trainedAtMs > RETRAIN_AFTER_MS && fresh > 0
            if (due) runCatching { taste.train(events, _favorites.value, ::vectorsFor) }
        }
    }

    fun forgetTaste() {
        trainJob?.cancel()
        taste.forget()
        _tasteBoosts.value = emptyMap()
    }

    /** Reordena uma fila do SMART pelo gosto aprendido (sem mexer se ainda não há modelo). */
    private suspend fun personalize(planned: List<Track>, prev: Track?): List<Track> {
        if (planned.size < 2 || !settings.value.learnTaste || !taste.status.value.trained) return planned
        val vectors = vectorsFor(planned.map { it.id } + listOfNotNull(prev?.id))
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val (ordered, boosted) = taste.rerank(planned, vectors, prev?.let { vectors[it.id] }, hour)
        if (boosted.isNotEmpty()) {
            val part = TasteModel.daypart(hour)
            _tasteBoosts.update { old ->
                val next = LinkedHashMap(old)
                boosted.forEach { id -> if (vectors[id]?.let { taste.matchesDaypart(it, hour) } == true) next[id] = part else next[id] = -1 }
                if (next.size > 600) next.keys.take(next.size - 600).forEach(next::remove)
                next
            }
        }
        return ordered
    }

    private val _sleepTimer = MutableStateFlow(SleepTimer())
    val sleepTimer: StateFlow<SleepTimer> = _sleepTimer.asStateFlow()
    private var sleepJob: Job? = null
    private var sleepTrackId: String? = null

    private val allTracks = MutableStateFlow<List<Track>?>(null)
    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(LibraryTab.Tudo)
    private val sort = MutableStateFlow(
        SortOrder.entries.getOrElse(prefs.getInt("sort", 0)) { SortOrder.Titulo }
    )

    private val _favorites = MutableStateFlow(
        prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    )
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    val library: StateFlow<LibraryState> = combine(allTracks, query, tab, sort) { all, q, t, s ->
        if (all == null) return@combine LibraryState(loading = true, query = q, tab = t, sort = s)
        // Busca tolerante (sem acentos, fora de ordem, pequenos erros, anos/décadas).
        val parsed = Search.parse(q)
        val scored = if (parsed.isEmpty) null else all.mapNotNull { t -> Search.score(parsed, t).takeIf { it > 0 }?.let { t to it } }
        val filtered = scored?.map { it.first } ?: all
        val sorted = if (scored != null) scored.sortedByDescending { it.second }.map { it.first } else when (s) {
            SortOrder.Titulo -> filtered.sortedBy { sortKey(it.title) }
            SortOrder.Artista -> filtered.sortedWith(compareBy({ sortKey(it.artist) }, { sortKey(it.album) }))
            SortOrder.Album -> filtered.sortedWith(
                compareBy({ sortKey(it.album) }, { it.discNumber ?: 0 }, { it.trackNumber ?: 0 }, { sortKey(it.title) })
            )
            SortOrder.Duracao -> filtered.sortedByDescending { it.durationMs }
            SortOrder.Ano -> filtered.sortedWith(compareByDescending<Track> { it.year ?: 0 }.thenBy { it.album.lowercase() })
            SortOrder.Recentes -> filtered.sortedByDescending { it.dateAdded }
        }
        val albums = filtered.groupBy { it.albumId }.map { (id, ts) ->
            Album(id, ts.first().album, ts.first().artist, ts)
        }.sortedBy { sortKey(it.title) }
        LibraryState(
            loading = false,
            allTracks = all,
            tracks = sorted,
            albums = albums,
            artists = filtered.groupBy { it.artist }.map { (n, ts) -> Artist(n, ts) }.sortedBy { sortKey(it.name) },
            folders = filtered.groupBy { it.folder }.map { (p, ts) -> Folder(p, ts) }.sortedBy { sortKey(it.name) },
            // Uma faixa pode ter vários gêneros na mesma tag ("Rock; Indie").
            genres = filtered
                .flatMap { t -> t.genre.orEmpty().split(';', '/', ',').map { it.trim() }.filter { it.isNotEmpty() }.map { it to t } }
                .groupBy({ it.first.lowercase() }, { it })
                .map { (_, pairs) -> Genre(pairs.first().first, pairs.map { it.second }.distinctBy { it.id }) }
                .sortedBy { sortKey(it.name) },
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
    val forYou: StateFlow<ForYouState> = combine(
        allTracks, _favorites, history.revision, recommendations.blockedTracks, recommendations.blockedArtists,
    ) { all, favs, _, _, _ ->
        if (all == null) ForYouState()
        else ForYou.compute(all.filterNot(recommendations::isBlocked), history.recent(), favs, withMixes = smart.isAvailable)
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
        viewModelScope.launch { smartPlaylistStore.load() }
        smart.setArtistVariety(settings.value.artistVariety)

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

    // ---- Ajustes ----

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val before = settings.value.artistVariety
        settingsStore.update(change)
        if (settings.value.artistVariety != before) smart.setArtistVariety(settings.value.artistVariety)
    }

    fun resetSettings() {
        settingsStore.reset()
        smart.setArtistVariety(settings.value.artistVariety)
    }

    fun clearHistory() { viewModelScope.launch { history.clear() } }

    /** Exporta o histórico de escuta como CSV. */
    suspend fun exportHistory(target: Uri): Boolean {
        val csv = historyCsv(history.recent(), allTracks.value.orEmpty())
        return withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(target, "wt")!!.use { it.write(csv.toByteArray()) }
            }.isSuccess
        }
    }

    // ---- Equalizador (aplicado pelo serviço de reprodução) ----

    private val _eq = MutableStateFlow(readEq())
    val equalizer: StateFlow<EqualizerState> = _eq.asStateFlow()

    private fun readEq() = EqualizerState(
        info = AudioEffects.readInfo(prefs),
        enabled = prefs.getBoolean(AudioEffects.KEY_ENABLED, false),
        levels = AudioEffects.parseLevels(prefs.getString(AudioEffects.KEY_LEVELS, null)),
        bass = prefs.getInt(AudioEffects.KEY_BASS, 0),
        preset = prefs.getString(AudioEffects.KEY_PRESET, null),
    )

    fun refreshEqualizer() { _eq.value = readEq() }

    fun setEqualizer(enabled: Boolean? = null, levels: List<Int>? = null, bass: Int? = null, preset: String? = null, customLevels: Boolean = false) {
        val e = prefs.edit()
        enabled?.let { e.putBoolean(AudioEffects.KEY_ENABLED, it) }
        levels?.let { e.putString(AudioEffects.KEY_LEVELS, it.joinToString(",")) }
        bass?.let { e.putInt(AudioEffects.KEY_BASS, it) }
        when {
            preset != null -> e.putString(AudioEffects.KEY_PRESET, preset)
            customLevels -> e.remove(AudioEffects.KEY_PRESET)
        }
        e.apply()
        _eq.value = readEq()
    }

    // ---- Letras ----

    val lyricsRepo = LyricsRepository(app, prefs)
    private val lyricsVersion = MutableStateFlow(0)
    private val offsetVersion = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val lyrics: StateFlow<LyricsState> = combine(currentTrack, lyricsVersion) { t, _ -> t }
        .transformLatest { t ->
            if (t == null) { emit(LyricsState.None); return@transformLatest }
            emit(LyricsState.Loading)
            emit(lyricsRepo.load(t)?.let { LyricsState.Loaded(it) } ?: LyricsState.None)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsState.None)

    val lyricsOffset: StateFlow<Long> = combine(currentTrack, offsetVersion) { t, _ ->
        t?.let { lyricsRepo.offsetFor(it.id) } ?: 0L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val hasLyricsFolder: Boolean get() = lyricsRepo.lrcFolder != null

    fun setLyricsFolder(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        lyricsRepo.setLrcFolder(uri)
        lyricsVersion.value++
    }

    fun shiftLyrics(deltaMs: Long) {
        val id = currentTrack.value?.id ?: return
        lyricsRepo.setOffset(id, lyricsRepo.offsetFor(id) + deltaMs)
        offsetVersion.value++
    }

    // ---- Estatísticas ----

    private val statsPeriod = MutableStateFlow(StatsPeriod.Mes)
    val stats: StateFlow<StatsState> = combine(allTracks, history.revision, statsPeriod) { all, _, period ->
        Stats.compute(all.orEmpty(), history.recent(), period)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun setStatsPeriod(period: StatsPeriod) { statsPeriod.value = period }

    /** Gênero mais ouvido nos últimos 30 dias: o destaque da busca vazia. Null sem histórico. */
    val genreOfMonth: StateFlow<Ranked<String>?> = combine(allTracks, history.revision) { all, _ ->
        if (all.isNullOrEmpty()) null
        else Stats.compute(all, history.recent(), StatsPeriod.Mes).topGenres.firstOrNull()
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Retrospectiva: sempre os últimos 30 dias, independente do período escolhido nas estatísticas. */
    val monthStats: StateFlow<StatsState> = combine(allTracks, history.revision) { all, _ ->
        Stats.compute(all.orEmpty(), history.recent(), StatsPeriod.Mes)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    // ---- Backup ----

    suspend fun exportBackup(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        prefs.edit().commit()
        com.juscelino.sonivell.system.Backup.export(getApplication<Application>(), uri)
    }

    suspend fun restoreBackup(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        com.juscelino.sonivell.system.Backup.restore(getApplication<Application>(), uri)
    }

    // ---- Fila ----

    /** Toca uma coleção (álbum, playlist…) em ordem aleatória. */
    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (smartMode.value) setSmartMode(false)
        player.play(tracks, tracks.indices.random(), shuffle = true)
    }

    /**
     * Toca um mix "Para o momento" sem mexer no modo escolhido: a lista já vem ordenada pelo
     * SMART (mais parecidas primeiro). Só embaralha se o aleatório comum já estiver ligado.
     */
    fun playMood(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val shuffle = shuffleMode.value == ShuffleMode.ON
        planningJob?.cancel()
        player.play(tracks, if (shuffle) tracks.indices.random() else 0, shuffle = shuffle)
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

    fun refresh() = reload(train = true)

    /** Relê o MediaStore; [train] = treinar o gosto depois (não a cada atualização automática). */
    private fun reload(train: Boolean) {
        viewModelScope.launch {
            scanned = repository.loadTracks()
            _scannedFolders.value = scanned.map { it.folder }.distinct().sortedBy { sortKey(it.substringAfterLast('/')) }
            val tracks = applyLibraryFilter()
            if (smart.isAvailable) {
                smart.analyse(tracks)
                if (train) {
                    // Dá tempo de o motor carregar os vetores já salvos antes de treinar.
                    delay(8_000)
                    maybeTrainTaste()
                }
            }
        }
    }

    // ---- Filtro da biblioteca: áudios curtos e pastas ignoradas ----

    private var scanned: List<Track> = emptyList()
    private val tagOverrides = TagOverrides(app)

    /** Tags como vieram do arquivo (para o editor mostrar e restaurar). */
    fun originalTrack(id: Long): Track? = scanned.firstOrNull { it.id == id }

    fun tagEditOf(id: Long): TagEdit? = tagOverrides.get(id)

    /** Salva a correção de tags (vazia = volta ao original) e atualiza a biblioteca na hora. */
    fun saveTags(id: Long, edit: TagEdit) {
        tagOverrides.put(id, edit)
        applyLibraryFilter()
    }

    // ---- Notas (estrelas) ----

    private val _ratings = MutableStateFlow(
        prefs.getString(KEY_RATINGS, null).orEmpty().split(',').mapNotNull { p ->
            val (id, stars) = p.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            (id.toLongOrNull() ?: return@mapNotNull null) to (stars.toIntOrNull()?.coerceIn(1, 5) ?: return@mapNotNull null)
        }.toMap()
    )
    val ratings: StateFlow<Map<Long, Int>> = _ratings

    /** 0 apaga a nota. Tocar na mesma estrela de novo também apaga. */
    fun rate(id: Long, stars: Int) {
        val current = _ratings.value[id]
        val next = if (stars <= 0 || stars == current) _ratings.value - id else _ratings.value + (id to stars.coerceIn(1, 5))
        _ratings.value = next
        prefs.edit().putString(KEY_RATINGS, next.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }
    private val _scannedFolders = MutableStateFlow<List<String>>(emptyList())

    /** Todas as pastas com áudio, inclusive as ignoradas (para a lista dos ajustes). */
    val scannedFolders: StateFlow<List<String>> = _scannedFolders

    private fun applyLibraryFilter(): List<Track> {
        val s = settings.value
        val minMs = s.minDurationSec * 1000L
        val tracks = tagOverrides.apply(scanned).filter { t -> (minMs == 0L || t.durationMs >= minMs) && t.folder !in s.ignoredFolders }
        allTracks.value = tracks
        return tracks
    }

    // ---- Atualização automática: o MediaStore avisa quando algo muda ----

    private var refreshJob: Job? = null
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!settings.value.autoRefresh || allTracks.value == null) return
            refreshJob?.cancel()
            // Um download ou cópia gera vários avisos seguidos: espera acalmar.
            refreshJob = viewModelScope.launch {
                delay(AUTO_REFRESH_DEBOUNCE_MS)
                reload(train = false)
            }
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
    fun playFrom(tracks: List<Track>, index: Int, startPositionMs: Long = 0L) {
        if (!smartMode.value) {
            player.play(tracks, index, startPositionMs = startPositionMs)
            return
        }
        val seed = tracks.getOrNull(index) ?: return
        player.play(listOf(seed), 0, startPositionMs = startPositionMs)
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

    /** Botões separados do layout Ondas: liga/desliga o SMART ou o aleatório comum. */
    fun setShuffleMode(mode: ShuffleMode) {
        if (mode == shuffleMode.value) return
        when (mode) {
            ShuffleMode.OFF -> {
                player.setShuffle(false)
                setSmartMode(false)
            }
            ShuffleMode.ON -> {
                setSmartMode(false)
                player.setShuffle(true)
            }
            ShuffleMode.SMART -> if (smart.isAvailable) {
                player.setShuffle(false)
                setSmartMode(true)
                replanFromCurrent()
            }
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
        if (!settings.value.historyPaused) viewModelScope.launch {
            history.record(event)
            maybeTrainTaste()
        }
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
                library = recommendable().filter { it.id !in playedOrCurrent },
                length = SMART_PLAN_LENGTH,
                preceding = queueIds.take(currentIndex).takeLast(SMART_PLAN_LENGTH),
                history = history.recent(SMART_HISTORY_LIMIT),
            )
            val planned = personalize(ids.mapNotNull(byId::get), seed)
            noteSmartSeed(planned, seed)
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
                library = recommendable().filter { it.id !in queued },
                length = SMART_PLAN_LENGTH,
                preceding = queueIds.dropLast(1).takeLast(SMART_PLAN_LENGTH),
                history = history.recent(SMART_HISTORY_LIMIT),
            )
            val more = personalize(ids.mapNotNull(byId::get), seed)
            noteSmartSeed(more, seed)
            if (more.isEmpty()) exhaustedSeedId = lastId
            player.append(more)
        }
    }

    /** Biblioteca sem o que o usuário pediu para não recomendar. */
    private fun recommendable(): List<Track> = allTracks.value.orEmpty().filterNot(recommendations::isBlocked)

    // ---- Playlists inteligentes ----

    val playStats: StateFlow<PlayStats> = history.revision
        .map { PlayStats.from(history.recent()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayStats(emptyMap(), emptyMap()))

    val smartPlaylists: StateFlow<List<SmartPlaylist>> = smartPlaylistStore.items

    fun saveSmartPlaylist(p: SmartPlaylist, then: (Long) -> Unit = {}) {
        viewModelScope.launch { then(smartPlaylistStore.save(p)) }
    }

    fun deleteSmartPlaylist(id: Long) { viewModelScope.launch { smartPlaylistStore.delete(id) } }

    // ---- Busca pelo estilo (SMART) ----

    @OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalCoroutinesApi::class)
    val semanticResults: StateFlow<List<Track>> = query
        .debounce(350)
        .mapLatest { q ->
            if (!smart.isAvailable || q.trim().length < 3) emptyList()
            else {
                val byId = allTracks.value.orEmpty().associateBy { it.id }
                smart.semanticSearch(q.trim(), 30).mapNotNull(byId::get)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Faixas de um mix por clima (busca por descrição no SMART), já sem as bloqueadas. */
    suspend fun moodTracks(mood: com.juscelino.sonivell.data.Mood): List<Track> {
        if (!smart.isAvailable) return emptyList()
        val byId = allTracks.value.orEmpty().associateBy { it.id }
        val blocked = recommendations.blockedTracks.value
        return smart.semanticSearch(mood.query, 60).filter { it !in blocked }.mapNotNull(byId::get).take(40)
    }

    /** Busca nas letras (pasta de letras e letras embutidas já abertas). */
    @OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalCoroutinesApi::class)
    val lyricHits: StateFlow<List<com.juscelino.sonivell.data.LyricHit>> = query
        .debounce(400)
        .mapLatest { q ->
            if (q.trim().length < 3) emptyList()
            else lyricsRepo.search(q, allTracks.value.orEmpty())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Organizar: duplicatas e metadados ----

    val duplicates: StateFlow<List<DuplicateGroup>> = combine(allTracks, recommendations.notDuplicates) { all, ignored ->
        Organize.duplicates(all.orEmpty(), ignored)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val metadataIssues: StateFlow<MetadataIssues> = allTracks
        .map { Organize.issues(it.orEmpty()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetadataIssues())

    // ---- Aviso "tags para arrumar" na Biblioteca ----

    /** Quantas faixas tinham problema quando o aviso foi dispensado; ele volta se aumentar. */
    private val tagBannerDismissedAt = MutableStateFlow(prefs.getInt(KEY_TAG_BANNER_DISMISSED, 0))

    /** Faixas com tags para arrumar, ou 0 quando o aviso não deve aparecer. */
    val tagBannerCount: StateFlow<Int> = combine(metadataIssues, tagBannerDismissedAt) { issues, dismissed ->
        issues.total.takeIf { it > dismissed } ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun dismissTagBanner() {
        val total = metadataIssues.value.total
        tagBannerDismissedAt.value = total
        prefs.edit().putInt(KEY_TAG_BANNER_DISMISSED, total).apply()
    }

    // ---- Boas-vindas (primeiro uso) ----

    private val _showWelcome = MutableStateFlow(false)

    /** Mostra a tela de boas-vindas uma vez, na primeira abertura (quem já ouviu algo não vê). */
    val showWelcome: StateFlow<Boolean> = _showWelcome

    init {
        if (!prefs.getBoolean(KEY_WELCOME_SEEN, false)) {
            viewModelScope.launch(Dispatchers.IO) {
                if (history.recent(1).isNotEmpty()) dismissWelcome() else _showWelcome.value = true
            }
        }
    }

    init {
        runCatching {
            getApplication<Application>().contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver,
            )
        }
        // Mudou o mínimo de duração ou as pastas ignoradas: refaz o filtro sem reler o aparelho.
        viewModelScope.launch {
            settings.map { it.minDurationSec to it.ignoredFolders }.distinctUntilChanged().drop(1).collect {
                if (allTracks.value != null) {
                    val tracks = applyLibraryFilter()
                    if (smart.isAvailable) smart.analyse(tracks)
                }
            }
        }
    }

    // ---- Playlists publicadas para outros apps ----

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(settings.map { it.publishPlaylists }.distinctUntilChanged(), playlists, allTracks) { on, lists, all -> Triple(on, lists, all) }
                .collectLatest { (on, lists, all) ->
                    if (all == null) return@collectLatest
                    delay(PUBLISH_DEBOUNCE_MS)
                    val app = getApplication<Application>()
                    if (on) PlaylistPublisher.publish(app, lists, ::tracksOf) else PlaylistPublisher.unpublishAll(app)
                }
        }
    }

    fun dismissWelcome() {
        _showWelcome.value = false
        prefs.edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
    }

    // ---- Atalhos do ícone ----

    private val _pendingShortcut = MutableStateFlow<String?>(null)

    /** Ação pedida por um atalho do ícone, esperando a biblioteca carregar para ser feita. */
    val pendingShortcut: StateFlow<String?> = _pendingShortcut

    fun requestShortcut(action: String) { _pendingShortcut.value = action }
    fun consumeShortcut() { _pendingShortcut.value = null }

    // ---- Buscas recentes ----

    private val _recentSearches = MutableStateFlow(
        prefs.getString(KEY_RECENT_SEARCHES, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    )
    val recentSearches: StateFlow<List<String>> = _recentSearches

    /** Guarda a busca quando ela levou a algo (tocar, abrir um álbum, um artista…). */
    fun addRecentSearch(query: String) {
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (q.length < 2) return
        val next = (listOf(q) + _recentSearches.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
        _recentSearches.value = next
        prefs.edit().putString(KEY_RECENT_SEARCHES, next.joinToString("\n")).apply()
    }

    fun removeRecentSearch(query: String) {
        val next = _recentSearches.value.filterNot { it == query }
        _recentSearches.value = next
        prefs.edit().putString(KEY_RECENT_SEARCHES, next.joinToString("\n")).apply()
    }

    /**
     * Pede ao Android para apagar os arquivos (o sistema mostra a confirmação).
     * Devolve null quando o aparelho (Android 10 ou anterior) não oferece esse pedido.
     */
    fun deleteRequest(tracks: List<Track>): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || tracks.isEmpty()) return null
        return MediaStore.createDeleteRequest(getApplication<Application>().contentResolver, tracks.map { it.uri }).intentSender
    }

    private suspend fun planSmart(seed: Track, excluding: Set<Long>): List<Track> {
        val all = allTracks.value.orEmpty()
        val byId = all.associateBy { it.id }
        return smart.startQueue(seed, recommendable(), SMART_PLAN_LENGTH, history.recent(SMART_HISTORY_LIMIT))
            .filter { it !in excluding }
            .mapNotNull(byId::get)
            .let { personalize(it, seed) }
            .also { noteSmartSeed(it, seed) }
    }

    private fun noteSmartSeed(planned: List<Track>, seed: Track) {
        if (planned.isEmpty()) return
        _smartSeeds.update { old ->
            val next = LinkedHashMap(old)
            planned.forEach { next.remove(it.id); next[it.id] = seed.id }
            // Guarda só as mais recentes: o bastante para a fila atual.
            if (next.size > 600) next.keys.take(next.size - 600).forEach(next::remove)
            next
        }
    }

    private companion object {
        const val SMART_PLAN_LENGTH = 25
        const val RETRAIN_EVERY = 15
        const val RETRAIN_AFTER_MS = 24L * 60 * 60 * 1000
        const val SMART_TOP_UP_BELOW = 5
        const val SMART_HISTORY_LIMIT = 2_000
        const val SESSION_SAVE_EVERY_MS = 5_000L
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val KEY_TAG_BANNER_DISMISSED = "tag_banner_dismissed"
        const val MAX_RECENT_SEARCHES = 8
        const val KEY_WELCOME_SEEN = "welcome_seen"
        const val AUTO_REFRESH_DEBOUNCE_MS = 3_000L
        const val PUBLISH_DEBOUNCE_MS = 2_000L
        const val KEY_RATINGS = "ratings"
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver) }
        player.release()
        super.onCleared()
    }
}
