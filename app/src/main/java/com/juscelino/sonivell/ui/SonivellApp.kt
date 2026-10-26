package com.juscelino.sonivell.ui

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.juscelino.sonivell.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.ui.res.pluralStringResource
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.collection.CollectionActions
import android.os.Build
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Tune
import com.juscelino.sonivell.data.RuleField
import com.juscelino.sonivell.data.RuleOp
import com.juscelino.sonivell.data.SmartPlaylist
import com.juscelino.sonivell.data.SmartPlaylists
import com.juscelino.sonivell.data.SmartRule
import com.juscelino.sonivell.ui.organize.OrganizeActions
import com.juscelino.sonivell.ui.organize.OrganizeScreen
import com.juscelino.sonivell.ui.smartlist.SmartEditorActions
import com.juscelino.sonivell.ui.smartlist.SmartPlaylistEditor
import com.juscelino.sonivell.ui.lyrics.LyricsActions
import com.juscelino.sonivell.ui.lyrics.LyricsScreen
import com.juscelino.sonivell.ui.settings.EqualizerActions
import com.juscelino.sonivell.ui.settings.EqualizerScreen
import com.juscelino.sonivell.ui.settings.SettingsActions
import com.juscelino.sonivell.ui.settings.SettingsScreen
import com.juscelino.sonivell.ui.settings.StatsScreen
import com.juscelino.sonivell.ui.collection.CollectionScreen
import com.juscelino.sonivell.ui.components.AddToPlaylistSheet
import com.juscelino.sonivell.ui.components.ConfirmDialog
import com.juscelino.sonivell.ui.components.MenuOption
import com.juscelino.sonivell.ui.components.NameDialog
import com.juscelino.sonivell.ui.components.OptionsSheet
import com.juscelino.sonivell.ui.components.TrackHeader
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.juscelino.sonivell.MainViewModel
import com.juscelino.sonivell.ShuffleMode
import com.juscelino.sonivell.playback.toTrack
import com.juscelino.sonivell.ui.components.MiniPlayer
import com.juscelino.sonivell.ui.library.LibraryActions
import com.juscelino.sonivell.ui.library.LibraryMode
import com.juscelino.sonivell.ui.library.LibraryScreen
import com.juscelino.sonivell.ui.nowplaying.NowPlayingActions
import com.juscelino.sonivell.ui.nowplaying.NowPlayingScreen
import com.juscelino.sonivell.ui.queue.QueueActions
import com.juscelino.sonivell.ui.queue.QueueScreen
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "inicio"
    const val SEARCH = "buscar"
    const val LIBRARY = "biblioteca"
    const val PLAYER = "player"
    const val QUEUE = "fila"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artista/{name}"
    const val GENRE = "genero/{name}"
    const val FOLDER = "pasta/{path}"
    const val PLAYLIST = "playlist/{id}"
    const val SETTINGS = "ajustes"
    const val EQUALIZER = "equalizador"
    const val STATS = "estatisticas"
    const val LYRICS = "letra"
    const val AUTO = "auto/{key}"
    const val SMARTLIST = "inteligente/{id}"
    const val SMART_EDIT = "inteligente-editar/{id}"
    const val ORGANIZE = "organizar"
    fun auto(key: String) = "auto/$key"
    fun smartlist(id: Long) = "inteligente/$id"
    fun smartEdit(id: Long) = "inteligente-editar/$id"
    /** Telas de coleção: mostram o mini player e a barra de navegação. */
    val COLLECTIONS = setOf(ALBUM, ARTIST, GENRE, FOLDER, PLAYLIST, AUTO, SMARTLIST, SMART_EDIT, ORGANIZE)

    fun album(id: Long) = "album/$id"
    fun artist(name: String) = "artista/" + Uri.encode(name)
    fun genre(name: String) = "genero/" + Uri.encode(name)
    fun folder(path: String) = "pasta/" + Uri.encode(path)
    fun playlist(id: Long) = "playlist/$id"
}

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.nav_home, Icons.Rounded.Home),
    Tab(Routes.SEARCH, R.string.nav_search, Icons.Rounded.Search),
    Tab(Routes.LIBRARY, R.string.nav_library, Icons.Rounded.LibraryMusic),
)

private fun audioPermission() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun SonivellApp(vm: MainViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[audioPermission()] == true
    }
    val requestPermissions = {
        val perms = buildList {
            add(audioPermission())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(perms.toTypedArray())
    }

    LaunchedEffect(granted) { if (granted) vm.onPermissionGranted() }

    if (!granted) {
        PermissionScreen(onRequest = requestPermissions)
    } else {
        MainContent(vm)
    }
}

/** De onde o menu de uma faixa foi aberto: muda as opções (remover da fila, da playlist…). */
private sealed interface MenuContext {
    data object None : MenuContext
    data class Queue(val index: Int) : MenuContext
    data class InPlaylist(val playlistId: Long, val storeIndex: Int) : MenuContext
}

/** O que a tela de uma coleção mostra. */
private data class CollectionData(
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
    /** Só playlists: índice de cada faixa exibida na lista salva (faixas sumidas ficam de fora). */
    val storeIndices: List<Int>? = null,
    val playlist: Playlist? = null,
    /** Playlist inteligente (regras), quando for o caso. */
    val smart: SmartPlaylist? = null,
    /** Automática (sem edição): oferece "Salvar como playlist". */
    val automatic: Boolean = false,
)

@Composable
private fun MainContent(vm: MainViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val library by vm.library.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    val current by vm.currentTrack.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val shuffleMode by vm.shuffleMode.collectAsStateWithLifecycle()
    val smartProgress by vm.smartProgress.collectAsStateWithLifecycle()
    val forYou by vm.forYou.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val sleepTimer by vm.sleepTimer.collectAsStateWithLifecycle()
    val smartSource = stringResource(R.string.source_smart)

    val defaultSource = stringResource(R.string.nav_library)
    var source by rememberSaveable { mutableStateOf(defaultSource) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val res = context.resources
    fun toast(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    val comingSoon = stringResource(R.string.coming_soon)
    val soon: () -> Unit = { toast(comingSoon) }
    val shuffleSource = stringResource(R.string.source_shuffle)

    // ---- Estado dos menus e diálogos ----
    var menuFor by remember { mutableStateOf<Pair<Track, MenuContext>?>(null) }
    var addToPlaylist by remember { mutableStateOf<List<Track>?>(null) }
    var newPlaylistWith by remember { mutableStateOf<List<Track>?>(null) }
    var collectionMenu by remember { mutableStateOf<CollectionData?>(null) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    var playerMenu by remember { mutableStateOf(false) }
    var timerSheet by remember { mutableStateOf(false) }
    var saveQueue by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importName by remember { mutableStateOf("") }
    var exportId by remember { mutableStateOf<Long?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val equalizer by vm.equalizer.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val historyExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            toast(res.getString(if (vm.exportHistory(uri)) R.string.history_exported else R.string.export_failed))
        }
    }
    val smartPlaylists by vm.smartPlaylists.collectAsStateWithLifecycle()
    val semanticResults by vm.semanticResults.collectAsStateWithLifecycle()
    val duplicates by vm.duplicates.collectAsStateWithLifecycle()
    val metadataIssues by vm.metadataIssues.collectAsStateWithLifecycle()
    val playStats by vm.playStats.collectAsStateWithLifecycle()
    val blockedTracks by vm.recommendations.blockedTracks.collectAsStateWithLifecycle()
    val blockedArtists by vm.recommendations.blockedArtists.collectAsStateWithLifecycle()
    var deletingSmart by remember { mutableStateOf<SmartPlaylist?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            vm.refresh()
            toast(res.getString(R.string.copies_deleted))
        }
    }
    val lyricsState by vm.lyrics.collectAsStateWithLifecycle()
    val lyricsOffset by vm.lyricsOffset.collectAsStateWithLifecycle()
    val lyricsFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setLyricsFolder(uri)
    }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val id = exportId
        if (uri != null && id != null) scope.launch {
            toast(res.getString(if (vm.exportPlaylist(id, uri)) R.string.exported else R.string.export_failed))
        }
        exportId = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importName = displayName(context, uri)?.substringBeforeLast('.').orEmpty()
            importUri = uri
        }
    }

    fun openAlbum(id: Long) = nav.navigate(Routes.album(id))
    fun openArtist(name: String) = nav.navigate(Routes.artist(name))
    fun share(track: Track) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(track.mimeType ?: "audio/*")
            .putExtra(Intent.EXTRA_STREAM, track.uri)
            .putExtra(Intent.EXTRA_TITLE, "${track.artist} - ${track.title}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, res.getString(R.string.share_chooser))) }
    }

    val libraryActions = LibraryActions(
        onQuery = vm::setQuery,
        onTab = vm::setTab,
        onSort = vm::cycleSort,
        onRefresh = vm::refresh,
        onPlay = { tracks, index, src ->
            source = if (shuffleMode == ShuffleMode.SMART) smartSource else src
            vm.playFrom(tracks, index)
        },
        onPlayMix = { seed ->
            source = smartSource
            vm.playMix(seed)
        },
        onShuffleAll = {
            source = if (shuffleMode == ShuffleMode.SMART) smartSource else shuffleSource
            vm.playAllShuffled()
        },
        onTrackMenu = { menuFor = it to MenuContext.None },
        onOpenAlbum = ::openAlbum,
        onOpenArtist = ::openArtist,
        onOpenGenre = { nav.navigate(Routes.genre(it)) },
        onOpenFolder = { nav.navigate(Routes.folder(it)) },
        onOpenPlaylist = { nav.navigate(Routes.playlist(it)) },
        onNewPlaylist = { newPlaylistWith = emptyList() },
        onImportPlaylist = { importLauncher.launch(arrayOf("*/*")) },
        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
        onOpenAuto = { nav.navigate(Routes.auto(it)) },
        onOpenSmartPlaylist = { nav.navigate(Routes.smartlist(it)) },
        onNewSmartPlaylist = { nav.navigate(Routes.smartEdit(0)) },
    )

    val chromeRoutes = remember { tabs.map { it.route }.toSet() + Routes.COLLECTIONS + setOf(Routes.SETTINGS, Routes.EQUALIZER, Routes.STATS) }
    val showChrome = route in chromeRoutes

    Scaffold(
        containerColor = SonivellTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            if (showChrome) {
                Column(Modifier.background(SonivellTheme.colors.background).windowInsetsPadding(WindowInsets.navigationBars)) {
                    current?.let { t ->
                        MiniPlayer(
                            track = t,
                            isPlaying = player.isPlaying,
                            progress = if (player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f,
                            onOpen = { nav.navigate(Routes.PLAYER) },
                            onTogglePlay = vm.player::togglePlay,
                        )
                    }
                    BottomNav(route) { r ->
                        nav.navigate(r) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        },
    ) { padding ->
        val listPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())

        @Composable
        fun Collection(data: CollectionData?) {
            if (data == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
                return
            }
            val playlist = data.playlist
            CollectionScreen(
                title = data.title,
                subtitle = data.subtitle,
                tracks = data.tracks,
                currentId = current?.id,
                contentPadding = listPadding,
                emptyText = if (playlist != null) stringResource(R.string.playlist_empty) else null,
                actions = CollectionActions(
                    onBack = { nav.popBackStack() },
                    onPlay = { index ->
                        source = if (shuffleMode == ShuffleMode.SMART) smartSource else data.title
                        vm.playFrom(data.tracks, index)
                    },
                    onShuffle = {
                        source = data.title
                        vm.playShuffled(data.tracks)
                    },
                    onTrackMenu = { track, index ->
                        val ctx = if (playlist != null && data.storeIndices != null) {
                            MenuContext.InPlaylist(playlist.id, data.storeIndices[index])
                        } else MenuContext.None
                        menuFor = track to ctx
                    },
                    onMore = { collectionMenu = data },
                    onMove = if (playlist == null || data.storeIndices == null) null else { from, to ->
                        vm.movePlaylistItem(playlist.id, data.storeIndices[from], data.storeIndices[to])
                    },
                ),
            )
        }

        NavHost(
            navController = nav,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
        ) {
            listOf(
                Routes.HOME to LibraryMode.Inicio,
                Routes.SEARCH to LibraryMode.Buscar,
                Routes.LIBRARY to LibraryMode.Biblioteca,
            ).forEach { (r, mode) ->
                composable(r) {
                    LibraryScreen(
                        mode = mode,
                        state = library,
                        favorites = favorites,
                        currentId = current?.id,
                        contentPadding = listPadding,
                        actions = libraryActions,
                        smartProgress = smartProgress,
                        forYou = forYou,
                        playlists = playlists,
                        smartPlaylists = smartPlaylists,
                        semanticResults = semanticResults,
                    )
                }
            }

            composable(Routes.ALBUM, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id")
                val tracks = library.allTracks.filter { it.albumId == id }
                    .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }, { it.title.lowercase() }))
                Collection(tracks.firstOrNull()?.let { CollectionData(it.album, it.albumArtist ?: it.artist, tracks) })
            }
            composable(Routes.ARTIST) { entry ->
                val name = entry.arguments?.getString("name")
                val tracks = library.allTracks.filter { it.artist == name }
                    .sortedWith(compareBy({ it.album.lowercase() }, { it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
                val albums = tracks.map { it.albumId }.distinct().size
                Collection(if (name == null || tracks.isEmpty()) null else CollectionData(name, pluralStringResource(R.plurals.albums_count, albums, albums), tracks))
            }
            composable(Routes.GENRE) { entry ->
                val name = entry.arguments?.getString("name")
                val genre = library.genres.firstOrNull { it.name.equals(name, ignoreCase = true) }
                Collection(genre?.let { CollectionData(it.name, stringResource(R.string.tab_genres), it.tracks) })
            }
            composable(Routes.FOLDER) { entry ->
                val path = entry.arguments?.getString("path")
                val folder = library.folders.firstOrNull { it.path == path }
                Collection(folder?.let { CollectionData(it.name, it.path, it.tracks) })
            }
            composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id")
                val playlist = playlists.firstOrNull { it.id == id }
                val byId = remember(library.allTracks) { library.allTracks.associateBy { it.id } }
                val data = playlist?.let { p ->
                    val shown = p.trackIds.withIndex().mapNotNull { (i, tid) -> byId[tid]?.let { i to it } }
                    CollectionData(
                        title = p.name,
                        subtitle = stringResource(R.string.tab_playlists),
                        tracks = shown.map { it.second },
                        storeIndices = shown.map { it.first },
                        playlist = p,
                    )
                }
                // Enquanto as playlists carregam, não volta da tela.
                if (data == null && playlists.isEmpty()) return@composable
                Collection(data)
            }

            composable(Routes.AUTO) { entry ->
                val key = entry.arguments?.getString("key").orEmpty()
                val all = library.allTracks
                val (title, tracks) = when {
                    key.startsWith("decada-") -> {
                        val start = key.removePrefix("decada-").toIntOrNull() ?: 0
                        val label = if (start in 1900..1999) (start % 100).toString() else start.toString()
                        stringResource(R.string.decade_label, label) to SmartPlaylists.decade(all, start)
                    }
                    else -> {
                        val kind = when (key) {
                            "mais-ouvidas" -> SmartPlaylists.Builtin.MaisOuvidas to R.string.auto_most
                            "menos-ouvidas" -> SmartPlaylists.Builtin.MenosOuvidas to R.string.auto_least
                            "nunca-tocadas" -> SmartPlaylists.Builtin.NuncaTocadas to R.string.auto_never
                            "recentes" -> SmartPlaylists.Builtin.Recentes to R.string.auto_recent_added
                            "ouvidas-recentemente" -> SmartPlaylists.Builtin.OuvidasRecentemente to R.string.auto_recent_played
                            else -> SmartPlaylists.Builtin.Curtidas to R.string.source_liked
                        }
                        stringResource(kind.second) to SmartPlaylists.builtin(kind.first, all, playStats, favorites)
                    }
                }
                Collection(CollectionData(title, stringResource(R.string.auto_playlists), tracks, automatic = true))
            }
            composable(Routes.SMARTLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id")
                val sp = smartPlaylists.firstOrNull { it.id == id }
                if (sp == null && smartPlaylists.isEmpty()) return@composable
                Collection(sp?.let {
                    CollectionData(it.name, stringResource(R.string.smart_playlists), SmartPlaylists.evaluate(it, library.allTracks, playStats, favorites), smart = it)
                })
            }
            composable(Routes.SMART_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val existing = smartPlaylists.firstOrNull { it.id == id }
                val initial = existing ?: SmartPlaylist(0, "", listOf(SmartRule(RuleField.Genero, RuleOp.Contem)))
                SmartPlaylistEditor(
                    initial = initial,
                    contentPadding = listPadding,
                    actions = SmartEditorActions(
                        onBack = { nav.popBackStack() },
                        onSave = { p ->
                            vm.saveSmartPlaylist(p) { newId ->
                                nav.popBackStack()
                                if (existing == null) nav.navigate(Routes.smartlist(newId))
                            }
                        },
                        onDelete = existing?.let { e -> { deletingSmart = e } },
                        preview = { p -> SmartPlaylists.evaluate(p, library.allTracks, playStats, favorites).size },
                    ),
                )
            }
            composable(Routes.ORGANIZE) {
                OrganizeScreen(
                    duplicates = duplicates,
                    issues = metadataIssues,
                    canDelete = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    contentPadding = listPadding,
                    actions = OrganizeActions(
                        onBack = { nav.popBackStack() },
                        onDeleteCopies = { copies ->
                            vm.deleteRequest(copies)?.let { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                        },
                        onNotDuplicate = { vm.recommendations.markNotDuplicate(it.key) },
                        onPlay = { t -> vm.playFrom(listOf(t), 0) },
                        onResetIgnored = vm.recommendations::clearNotDuplicates,
                    ),
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    smartAvailable = vm.smart.isAvailable,
                    versionName = versionName,
                    contentPadding = listPadding,
                    actions = SettingsActions(
                        onBack = { nav.popBackStack() },
                        onUpdate = vm::updateSettings,
                        onOpenEqualizer = { nav.navigate(Routes.EQUALIZER) },
                        onOpenStats = { nav.navigate(Routes.STATS) },
                        onRefreshLibrary = vm::refresh,
                        onClearHistory = { confirmClearHistory = true },
                        onExportHistory = { historyExportLauncher.launch("sonivell-historico.csv") },
                        onReset = { confirmReset = true },
                        onPickLyricsFolder = { lyricsFolderLauncher.launch(null) },
                        onOpenOrganize = { nav.navigate(Routes.ORGANIZE) },
                        onUnblockTrack = { vm.recommendations.blockTrack(it, false) },
                        onUnblockArtist = { vm.recommendations.blockArtist(it, false) },
                    ),
                    blockedTracks = library.allTracks.filter { it.id in blockedTracks },
                    blockedArtists = blockedArtists.sorted(),
                )
            }
            composable(Routes.EQUALIZER) {
                LaunchedEffect(Unit) { vm.refreshEqualizer() }
                EqualizerScreen(
                    state = equalizer,
                    contentPadding = listPadding,
                    actions = EqualizerActions(
                        onBack = { nav.popBackStack() },
                        onEnabled = { vm.setEqualizer(enabled = it) },
                        onLevels = { levels, preset ->
                            // Mexer numa banda liga o equalizador: o efeito aparece na hora.
                            vm.setEqualizer(enabled = true, levels = levels, preset = preset, customLevels = preset == null)
                        },
                        onBass = { vm.setEqualizer(enabled = true, bass = it) },
                    ),
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    stats = stats,
                    contentPadding = listPadding,
                    onBack = { nav.popBackStack() },
                    onPeriod = vm::setStatsPeriod,
                    onPlayTrack = { t ->
                        source = res.getString(R.string.stats)
                        vm.playFrom(listOf(t), 0)
                    },
                )
            }

            composable(
                Routes.PLAYER,
                enterTransition = { slideInVertically(tween(280)) { it } },
                popExitTransition = { slideOutVertically(tween(250)) { it } },
            ) {
                val t = current
                if (t == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                } else {
                    NowPlayingScreen(
                        track = t,
                        state = player,
                        shuffleMode = shuffleMode,
                        smartAvailable = vm.smart.isAvailable,
                        isFavorite = t.id in favorites,
                        sourceLabel = source,
                        sleepTimer = sleepTimer,
                        actions = NowPlayingActions(
                            onClose = { nav.popBackStack() },
                            onMore = { playerMenu = true },
                            onTogglePlay = vm.player::togglePlay,
                            onNext = { vm.player.next() },
                            onPrevious = { vm.player.previous() },
                            onSeek = vm.player::seekTo,
                            onShuffle = vm::cycleShuffle,
                            onRepeat = vm.player::cycleRepeat,
                            onFavorite = { vm.toggleFavorite(t.id) },
                            onLyrics = { nav.navigate(Routes.LYRICS) },
                            onQueue = { nav.navigate(Routes.QUEUE) },
                            onSleepTimer = { timerSheet = true },
                        ),
                    )
                }
            }

            composable(
                Routes.LYRICS,
                enterTransition = { slideInVertically(tween(280)) { it } },
                popExitTransition = { slideOutVertically(tween(250)) { it } },
            ) {
                val t = current
                if (t == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                } else {
                    LyricsScreen(
                        track = t,
                        state = lyricsState,
                        positionMs = player.positionMs,
                        offsetMs = lyricsOffset,
                        hasFolder = vm.hasLyricsFolder,
                        actions = LyricsActions(
                            onClose = { nav.popBackStack() },
                            onSeek = vm.player::seekTo,
                            onOffset = vm::shiftLyrics,
                            onPickFolder = { lyricsFolderLauncher.launch(null) },
                        ),
                    )
                }
            }

            composable(
                Routes.QUEUE,
                enterTransition = { slideInVertically(tween(280)) { it } },
                popExitTransition = { slideOutVertically(tween(250)) { it } },
            ) {
                val byId = remember(library.allTracks) { library.allTracks.associateBy { it.id } }
                val upNext = player.upNext.map { iv ->
                    IndexedValue(iv.index, byId[iv.value.mediaId.toLongOrNull()] ?: iv.value.toTrack(context))
                }
                QueueScreen(
                    current = current,
                    upNext = upNext,
                    shuffle = shuffleMode != ShuffleMode.OFF,
                    actions = QueueActions(
                        onClose = { nav.popBackStack() },
                        onClear = vm.player::clearUpNext,
                        onPlayAt = vm.player::playAt,
                        onMove = vm.player::moveItem,
                        onShuffle = vm::cycleShuffle,
                        onSavePlaylist = { saveQueue = true },
                        onItemMenu = { track, index -> menuFor = track to MenuContext.Queue(index) },
                    ),
                )
            }
        }
    }

    // ---- Menus ----

    menuFor?.let { (track, ctx) ->
        val liked = track.id in favorites
        val options = buildList {
            add(MenuOption(Icons.Rounded.QueuePlayNext, stringResource(R.string.play_next)) {
                vm.playNext(listOf(track)); toast(res.getString(R.string.added_next))
            })
            add(MenuOption(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.add_to_queue)) {
                vm.addToQueue(listOf(track)); toast(res.getString(R.string.added_to_queue))
            })
            add(MenuOption(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist)) {
                addToPlaylist = listOf(track)
            })
            when (ctx) {
                is MenuContext.Queue -> add(MenuOption(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove_from_queue)) {
                    vm.removeFromQueue(ctx.index)
                })
                is MenuContext.InPlaylist -> add(MenuOption(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove_from_playlist)) {
                    vm.removeFromPlaylist(ctx.playlistId, ctx.storeIndex)
                })
                MenuContext.None -> Unit
            }
            add(MenuOption(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(if (liked) R.string.cd_unlike else R.string.cd_like)) {
                vm.toggleFavorite(track.id)
            })
            add(MenuOption(Icons.Rounded.Album, stringResource(R.string.go_to_album)) { openAlbum(track.albumId) })
            add(MenuOption(Icons.Rounded.Person, stringResource(R.string.go_to_artist)) { openArtist(track.artist) })
            add(MenuOption(Icons.Rounded.Share, stringResource(R.string.share)) { share(track) })
            val trackBlocked = track.id in blockedTracks
            add(MenuOption(Icons.Rounded.Block, stringResource(if (trackBlocked) R.string.recommend_again else R.string.dont_recommend)) {
                vm.recommendations.blockTrack(track.id, !trackBlocked)
            })
            if (track.rawArtist != null && track.artist !in blockedArtists) {
                add(MenuOption(Icons.Rounded.PersonOff, stringResource(R.string.dont_recommend_artist, track.artist)) {
                    vm.recommendations.blockArtist(track.artist, true)
                })
            }
        }
        OptionsSheet(options, onDismiss = { menuFor = null }, header = { TrackHeader(track) })
    }

    collectionMenu?.let { data ->
        val playlist = data.playlist
        val options = buildList {
            add(MenuOption(Icons.Rounded.QueuePlayNext, stringResource(R.string.play_next)) {
                vm.playNext(data.tracks); toast(res.getString(R.string.added_next))
            })
            add(MenuOption(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.add_to_queue)) {
                vm.addToQueue(data.tracks); toast(res.getString(R.string.added_to_queue))
            })
            val smart = data.smart
            if (smart != null) {
                add(MenuOption(Icons.Rounded.Tune, stringResource(R.string.edit_rules)) { nav.navigate(Routes.smartEdit(smart.id)) })
            }
            if (smart != null || data.automatic) {
                add(MenuOption(Icons.Rounded.SaveAlt, stringResource(R.string.save_as_playlist)) { newPlaylistWith = data.tracks })
            }
            if (smart != null) {
                add(MenuOption(Icons.Rounded.DeleteOutline, stringResource(R.string.delete)) { deletingSmart = smart })
            }
            if (playlist == null) {
                add(MenuOption(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist)) {
                    addToPlaylist = data.tracks
                })
            } else {
                add(MenuOption(Icons.Rounded.Edit, stringResource(R.string.rename)) { renaming = playlist })
                add(MenuOption(Icons.Rounded.FileUpload, stringResource(R.string.export_playlist)) {
                    exportId = playlist.id
                    exportLauncher.launch("${playlist.name}.m3u")
                })
                add(MenuOption(Icons.Rounded.DeleteOutline, stringResource(R.string.delete)) { deleting = playlist })
            }
        }
        OptionsSheet(options, onDismiss = { collectionMenu = null })
    }

    if (playerMenu) {
        val playing = current
        if (playing == null) LaunchedEffect(Unit) { playerMenu = false }
        playing?.let { track ->
            val options = listOf(
                MenuOption(Icons.Rounded.Bedtime, stringResource(R.string.sleep_timer)) { timerSheet = true },
                MenuOption(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist)) { addToPlaylist = listOf(track) },
                MenuOption(Icons.Rounded.Album, stringResource(R.string.go_to_album)) { openAlbum(track.albumId) },
                MenuOption(Icons.Rounded.Person, stringResource(R.string.go_to_artist)) { openArtist(track.artist) },
                MenuOption(Icons.Rounded.Share, stringResource(R.string.share)) { share(track) },
            )
            OptionsSheet(options, onDismiss = { playerMenu = false }, header = { TrackHeader(track) })
        }
    }

    if (timerSheet) {
        val options = buildList {
            listOf(5, 15, 30, 45, 60).forEach { m ->
                add(MenuOption(Icons.Rounded.Timer, pluralStringResource(R.plurals.timer_minutes, m, m)) { vm.setSleepTimer(m) })
            }
            add(MenuOption(Icons.Rounded.MusicNote, stringResource(R.string.timer_end_of_track)) { vm.sleepAtEndOfTrack() })
            if (sleepTimer.active) add(MenuOption(Icons.Rounded.TimerOff, stringResource(R.string.timer_off)) { vm.cancelSleepTimer() })
        }
        OptionsSheet(options, onDismiss = { timerSheet = false }, header = {
            Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.titleLarge, color = SonivellTheme.colors.textPrimary)
        })
    }

    addToPlaylist?.let { tracks ->
        AddToPlaylistSheet(
            playlists = playlists,
            onPick = { p ->
                vm.addToPlaylist(p.id, tracks)
                toast(res.getString(R.string.added_to_playlist, p.name))
            },
            onNew = { newPlaylistWith = tracks },
            onDismiss = { addToPlaylist = null },
        )
    }

    // ---- Diálogos ----

    newPlaylistWith?.let { tracks ->
        NameDialog(
            title = stringResource(R.string.new_playlist),
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                vm.createPlaylist(name, tracks)
                toast(res.getString(R.string.playlist_created, name))
            },
            onDismiss = { newPlaylistWith = null },
        )
    }

    if (saveQueue) {
        NameDialog(
            title = stringResource(R.string.save_queue_title),
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                vm.saveQueueAsPlaylist(name)
                toast(res.getString(R.string.playlist_created, name))
            },
            onDismiss = { saveQueue = false },
        )
    }

    renaming?.let { p ->
        NameDialog(
            title = stringResource(R.string.rename),
            initial = p.name,
            onConfirm = { vm.renamePlaylist(p.id, it) },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.delete_playlist_title),
            message = stringResource(R.string.delete_playlist_message, p.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                vm.deletePlaylist(p.id)
                if (route == Routes.PLAYLIST) nav.popBackStack()
            },
            onDismiss = { deleting = null },
        )
    }

    deletingSmart?.let { sp ->
        ConfirmDialog(
            title = stringResource(R.string.delete_playlist_title),
            message = stringResource(R.string.delete_playlist_message, sp.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                vm.deleteSmartPlaylist(sp.id)
                if (route == Routes.SMARTLIST || route == Routes.SMART_EDIT) nav.popBackStack()
            },
            onDismiss = { deletingSmart = null },
        )
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history_title),
            message = stringResource(R.string.clear_history_message),
            confirmLabel = stringResource(R.string.clear),
            onConfirm = { vm.clearHistory(); toast(res.getString(R.string.history_cleared)) },
            onDismiss = { confirmClearHistory = false },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_title),
            message = stringResource(R.string.reset_message),
            confirmLabel = stringResource(R.string.restore),
            onConfirm = vm::resetSettings,
            onDismiss = { confirmReset = false },
        )
    }

    importUri?.let { uri ->
        NameDialog(
            title = stringResource(R.string.import_playlist),
            initial = importName,
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                scope.launch {
                    val count = vm.importPlaylist(uri, name)
                    toast(
                        when {
                            count < 0 -> res.getString(R.string.import_failed)
                            count == 0 -> res.getString(R.string.import_none)
                            else -> res.getQuantityString(R.plurals.imported_count, count, count)
                        }
                    )
                }
            },
            onDismiss = { importUri = null },
        )
    }
}

/** Nome do arquivo escolhido pelo usuário (para sugerir o nome da playlist importada). */
private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

@Composable
private fun BottomNav(route: String?, onSelect: (String) -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == route
            val color = if (selected) c.accent else c.textSecondary
            Column(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab, onClick = { onSelect(tab.route) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, null, Modifier.size(22.dp), tint = color)
                Text(
                    stringResource(tab.label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = color,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    val c = SonivellTheme.colors
    Box(
        Modifier.fillMaxSize().background(c.background).safeDrawingPadding().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            com.juscelino.sonivell.ui.components.GeneratedArt(
                seed = 0,
                modifier = Modifier.size(160.dp).clip(RoundedCornerShape(28.dp)),
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = c.textPrimary)
            Text(
                stringResource(R.string.permission_message),
                style = MaterialTheme.typography.bodyLarge,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.accent)
                    .clickable(role = Role.Button, onClick = onRequest)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.permission_allow), style = MaterialTheme.typography.labelLarge, color = c.onAccent)
            }
        }
    }
}
