package com.juscelino.sonivell.ui

import androidx.compose.material.icons.Icons
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.juscelino.sonivell.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.juscelino.sonivell.data.UiStyle
import com.juscelino.sonivell.ui.components.MiniPlayer
import com.juscelino.sonivell.ui.components.MiniPlayerClassica
import com.juscelino.sonivell.ui.components.MiniPlayerOndas
import com.juscelino.sonivell.ui.library.LibraryActions
import com.juscelino.sonivell.ui.library.LibraryMode
import com.juscelino.sonivell.ui.library.LibraryScreenClassica
import com.juscelino.sonivell.ui.library.LibraryScreenContraste
import com.juscelino.sonivell.system.AppShortcuts
import androidx.compose.material.icons.rounded.NotificationsActive
import com.juscelino.sonivell.ui.extras.CarModeScreen
import com.juscelino.sonivell.ui.extras.AlarmScreen
import com.juscelino.sonivell.ui.extras.RetroScreen
import com.juscelino.sonivell.ui.extras.TagEditorScreen
import com.juscelino.sonivell.system.Backup
import com.juscelino.sonivell.data.CustomCovers
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.juscelino.sonivell.system.Ringtones
import com.juscelino.sonivell.ui.components.miniPlayerGestures
import com.juscelino.sonivell.ui.library.LibraryScreenEssencial
import com.juscelino.sonivell.ui.library.LibraryScreenOndas
import com.juscelino.sonivell.ui.nowplaying.NowPlayingActions
import com.juscelino.sonivell.ui.nowplaying.NowPlayingScreenClassica
import com.juscelino.sonivell.ui.nowplaying.NowPlayingScreenContraste
import com.juscelino.sonivell.ui.nowplaying.NowPlayingScreenOndas
import com.juscelino.sonivell.ui.nowplaying.PlayReason
import com.juscelino.sonivell.ui.queue.QueueActions
import com.juscelino.sonivell.ui.queue.QueueScreen
import com.juscelino.sonivell.ui.theme.SonivellIcons
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
    const val CAR = "carro"
    const val ALARM = "despertador"
    const val TAGS = "tags/{id}"
    const val RETRO = "retrospectiva"
    fun tags(id: Long) = "tags/$id"
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

private data class Tab(
    val route: String,
    @StringRes val label: Int,
    val icon: (SonivellIcons) -> ImageVector,
    val activeIcon: (SonivellIcons) -> ImageVector,
    /** Ícones no layout Ondas (o Para você usa a onda). */
    val ondasIcon: (SonivellIcons) -> ImageVector = icon,
    val ondasActiveIcon: (SonivellIcons) -> ImageVector = activeIcon,
)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.nav_home, { it.home }, { it.homeActive }, { it.forYou }, { it.forYouActive }),
    Tab(Routes.SEARCH, R.string.nav_search, { it.search }, { it.searchActive }),
    Tab(Routes.LIBRARY, R.string.nav_library, { it.library }, { it.libraryActive }),
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
        val showWelcome by vm.showWelcome.collectAsStateWithLifecycle()
        Box(Modifier.fillMaxSize()) {
            MainContent(vm)
            if (showWelcome) {
                val library by vm.library.collectAsStateWithLifecycle()
                val smartProgress by vm.smartProgress.collectAsStateWithLifecycle()
                WelcomeScreen(
                    trackCount = library.allTracks.size,
                    loading = library.loading,
                    smartAvailable = vm.smart.isAvailable,
                    progress = smartProgress,
                    onDone = vm::dismissWelcome,
                )
            }
        }
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
    val genreOfMonth by vm.genreOfMonth.collectAsStateWithLifecycle()
    val recentSearches by vm.recentSearches.collectAsStateWithLifecycle()
    val scannedFolders by vm.scannedFolders.collectAsStateWithLifecycle()
    val tagBannerCount by vm.tagBannerCount.collectAsStateWithLifecycle()
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
    val smartSeeds by vm.smartSeeds.collectAsStateWithLifecycle()
    val tasteStatus by vm.tasteStatus.collectAsStateWithLifecycle()
    val tasteBoosts by vm.tasteBoosts.collectAsStateWithLifecycle()
    val uiStyle = settings.uiStyle
    val equalizer by vm.equalizer.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val monthStats by vm.monthStats.collectAsStateWithLifecycle()
    val ratings by vm.ratings.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            toast(res.getString(if (vm.exportBackup(uri)) R.string.backup_done else R.string.backup_failed))
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }
    var coverFor by remember { mutableStateOf<Long?>(null) }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val albumId = coverFor
        if (uri != null && albumId != null) scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CustomCovers.set(context, albumId, uri) }
            toast(res.getString(if (ok) R.string.cover_changed else R.string.cover_failed))
        }
        coverFor = null
    }
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

    // Uma busca que levou a algo (tocar, abrir) vira "busca recente".
    val commitSearch: () -> Unit = { if (route == Routes.SEARCH && library.query.isNotBlank()) vm.addRecentSearch(library.query) }
    val libraryActions = LibraryActions(
        onQuery = vm::setQuery,
        onTab = vm::setTab,
        onSort = vm::cycleSort,
        onRefresh = vm::refresh,
        onPlay = { tracks, index, src ->
            commitSearch()
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
        onOpenAlbum = { commitSearch(); openAlbum(it) },
        onOpenArtist = { commitSearch(); openArtist(it) },
        onOpenGenre = { commitSearch(); nav.navigate(Routes.genre(it)) },
        onOpenFolder = { nav.navigate(Routes.folder(it)) },
        onOpenPlaylist = { commitSearch(); nav.navigate(Routes.playlist(it)) },
        onNewPlaylist = { newPlaylistWith = emptyList() },
        onImportPlaylist = { importLauncher.launch(arrayOf("*/*")) },
        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
        onOpenAuto = { nav.navigate(Routes.auto(it)) },
        onOpenSmartPlaylist = { nav.navigate(Routes.smartlist(it)) },
        onNewSmartPlaylist = { nav.navigate(Routes.smartEdit(0)) },
        onResume = { t, pos ->
            source = res.getString(R.string.nav_home)
            vm.playFrom(listOf(t), 0, startPositionMs = pos)
        },
        onOpenSearch = {
            nav.navigate(Routes.SEARCH) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        onOpenOrganize = { nav.navigate(Routes.ORGANIZE) },
        onRemoveRecentSearch = vm::removeRecentSearch,
        onDismissTagBanner = vm::dismissTagBanner,
    )

    // Atalhos do ícone: esperam a biblioteca carregar e então fazem a ação.
    val pendingShortcut by vm.pendingShortcut.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShortcut, library.loading) {
        val action = pendingShortcut ?: return@LaunchedEffect
        if (library.loading) return@LaunchedEffect
        when (action) {
            AppShortcuts.ACTION_MIX_NOW -> {
                val mix = forYou.thisHour
                if (mix.isNotEmpty()) {
                    source = res.getString(R.string.shortcut_mix_now)
                    vm.playFrom(mix, 0)
                } else libraryActions.onShuffleAll()
            }
            AppShortcuts.ACTION_SHUFFLE -> libraryActions.onShuffleAll()
            AppShortcuts.ACTION_LIKED -> nav.navigate(Routes.auto("curtidas"))
            AppShortcuts.ACTION_SEARCH -> libraryActions.onOpenSearch()
            AppShortcuts.ACTION_CAR -> nav.navigate(Routes.CAR)
        }
        vm.consumeShortcut()
    }

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
                        val progress = if (player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f
                        Box(Modifier.miniPlayerGestures(onNext = { vm.player.next() }, onPrevious = { vm.player.previous() }, onOpen = { nav.navigate(Routes.PLAYER) })) {
                        when (uiStyle) {
                            UiStyle.ONDAS, UiStyle.ESSENCIAL -> MiniPlayerOndas(
                                track = t,
                                isPlaying = player.isPlaying,
                                progress = progress,
                                onOpen = { nav.navigate(Routes.PLAYER) },
                                onTogglePlay = vm.player::togglePlay,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            UiStyle.CONTRASTE -> MiniPlayer(
                                track = t,
                                isPlaying = player.isPlaying,
                                progress = progress,
                                onOpen = { nav.navigate(Routes.PLAYER) },
                                onTogglePlay = vm.player::togglePlay,
                                liked = t.id in favorites,
                                onLike = { vm.toggleFavorite(t.id) },
                            )
                            UiStyle.CLASSICA -> MiniPlayerClassica(
                                track = t,
                                isPlaying = player.isPlaying,
                                progress = progress,
                                onOpen = { nav.navigate(Routes.PLAYER) },
                                onTogglePlay = vm.player::togglePlay,
                            )
                        }
                        }
                    }
                    val onTab: (String) -> Unit = { r ->
                        nav.navigate(r) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    when (uiStyle) {
                        UiStyle.ONDAS, UiStyle.ESSENCIAL -> BottomNavOndas(route, onTab)
                        UiStyle.CONTRASTE -> BottomNav(route, onTab)
                        UiStyle.CLASSICA -> BottomNavClassica(route, onTab)
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
                    when (uiStyle) {
                        UiStyle.ONDAS -> LibraryScreenOndas(
                            mode = mode, state = library, favorites = favorites, currentId = current?.id,
                            contentPadding = listPadding, actions = libraryActions, smartProgress = smartProgress,
                            forYou = forYou, playlists = playlists, smartPlaylists = smartPlaylists, semanticResults = semanticResults,
                            genreOfMonth = genreOfMonth, recentSearches = recentSearches, tagBannerCount = tagBannerCount,
                        )
                        UiStyle.CONTRASTE -> LibraryScreenContraste(
                            mode = mode, state = library, favorites = favorites, currentId = current?.id,
                            contentPadding = listPadding, actions = libraryActions, smartProgress = smartProgress,
                            forYou = forYou, playlists = playlists, smartPlaylists = smartPlaylists, semanticResults = semanticResults,
                        )
                        UiStyle.ESSENCIAL -> LibraryScreenEssencial(
                            mode = mode, state = library, favorites = favorites, currentId = current?.id,
                            contentPadding = listPadding, actions = libraryActions,
                            forYou = forYou, playlists = playlists, smartPlaylists = smartPlaylists, semanticResults = semanticResults,
                            recentSearches = recentSearches, tagBannerCount = tagBannerCount,
                        )
                        UiStyle.CLASSICA -> LibraryScreenClassica(
                            mode = mode, state = library, favorites = favorites, currentId = current?.id,
                            contentPadding = listPadding, actions = libraryActions, smartProgress = smartProgress,
                            forYou = forYou, playlists = playlists, smartPlaylists = smartPlaylists, semanticResults = semanticResults,
                        )
                    }
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
                    key == "bem-avaliadas" -> stringResource(R.string.auto_top_rated) to all
                        .filter { (ratings[it.id] ?: 0) >= 4 }
                        .sortedByDescending { ratings[it.id] ?: 0 }
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
            composable(Routes.CAR) {
                val t = current
                CarModeScreen(
                    track = t,
                    isPlaying = player.isPlaying,
                    liked = t != null && t.id in favorites,
                    onPrevious = { vm.player.previous() },
                    onTogglePlay = vm.player::togglePlay,
                    onNext = { vm.player.next() },
                    onLike = { t?.let { vm.toggleFavorite(it.id) } },
                    onMixNow = { vm.requestShortcut(AppShortcuts.ACTION_MIX_NOW) },
                    onExit = { nav.popBackStack() },
                )
            }
            composable(Routes.TAGS, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val original = remember(id) { vm.originalTrack(id) }
                if (original == null) {
                    LaunchedEffect(Unit) { nav.popBackStack() }
                } else {
                    TagEditorScreen(
                        original = original,
                        current = remember(id) { vm.tagEditOf(id) },
                        contentPadding = listPadding,
                        onSave = { edit ->
                            vm.saveTags(id, edit)
                            toast(res.getString(if (edit.isEmpty) R.string.tags_restored else R.string.tags_saved))
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable(Routes.RETRO) {
                RetroScreen(
                    stats = monthStats,
                    contentPadding = listPadding,
                    onBack = { nav.popBackStack() },
                    onPlayTop = {
                        val top = monthStats.topTracks.map { it.item }
                        if (top.isNotEmpty()) {
                            source = res.getString(R.string.retro_title)
                            vm.playFrom(top, 0)
                        }
                    },
                )
            }
            composable(Routes.ALARM) {
                AlarmScreen(playlists = playlists, contentPadding = listPadding, onBack = { nav.popBackStack() })
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
                        onOpenAlarm = { nav.navigate(Routes.ALARM) },
                        onOpenCarMode = { nav.navigate(Routes.CAR) },
                        onOpenRetro = { nav.navigate(Routes.RETRO) },
                        onBackup = { backupLauncher.launch("sonivell-backup.zip") },
                        onRestore = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                        onUnblockTrack = { vm.recommendations.blockTrack(it, false) },
                        onUnblockArtist = { vm.recommendations.blockArtist(it, false) },
                        onRetrainTaste = { vm.maybeTrainTaste(force = true) },
                        onForgetTaste = vm::forgetTaste,
                    ),
                    tasteStatus = tasteStatus,
                    blockedTracks = library.allTracks.filter { it.id in blockedTracks },
                    blockedArtists = blockedArtists.sorted(),
                    folders = scannedFolders,
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
                    onRetro = { nav.navigate(Routes.RETRO) },
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
                    val npActions = NowPlayingActions(
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
                        onSetShuffle = vm::setShuffleMode,
                    )
                    when (uiStyle) {
                        UiStyle.ONDAS, UiStyle.ESSENCIAL -> {
                            val byId = remember(library.allTracks) { library.allTracks.associateBy { it.id } }
                            val upNext = player.upNext.take(3).mapNotNull { iv -> byId[iv.value.mediaId.toLongOrNull()] }
                            val seed = smartSeeds[t.id]?.let(byId::get)
                            val reason = when {
                                seed != null && seed.id != t.id -> PlayReason.Smart(seed, tasteBoosts[t.id])
                                smartSeeds.values.contains(t.id) -> PlayReason.MixStart
                                shuffleMode == ShuffleMode.ON -> PlayReason.Shuffle
                                else -> PlayReason.Chosen
                            }
                            NowPlayingScreenOndas(
                                track = t, state = player, shuffleMode = shuffleMode, smartAvailable = vm.smart.isAvailable,
                                isFavorite = t.id in favorites, sourceLabel = source, actions = npActions, sleepTimer = sleepTimer,
                                lyrics = lyricsState, lyricsOffsetMs = lyricsOffset, upNext = upNext, reason = reason,
                            )
                        }
                        UiStyle.CONTRASTE -> NowPlayingScreenContraste(
                            track = t, state = player, shuffleMode = shuffleMode, smartAvailable = vm.smart.isAvailable,
                            isFavorite = t.id in favorites, sourceLabel = source, actions = npActions, sleepTimer = sleepTimer,
                            lyrics = lyricsState, lyricsOffsetMs = lyricsOffset,
                        )
                        UiStyle.CLASSICA -> NowPlayingScreenClassica(
                            track = t, state = player, shuffleMode = shuffleMode, smartAvailable = vm.smart.isAvailable,
                            isFavorite = t.id in favorites, sourceLabel = source, actions = npActions, sleepTimer = sleepTimer,
                        )
                    }
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
            add(MenuOption(SonivellTheme.icons.playNext, stringResource(R.string.play_next)) {
                vm.playNext(listOf(track)); toast(res.getString(R.string.added_next))
            })
            add(MenuOption(SonivellTheme.icons.queue, stringResource(R.string.add_to_queue)) {
                vm.addToQueue(listOf(track)); toast(res.getString(R.string.added_to_queue))
            })
            add(MenuOption(SonivellTheme.icons.addToPlaylist, stringResource(R.string.add_to_playlist)) {
                addToPlaylist = listOf(track)
            })
            when (ctx) {
                is MenuContext.Queue -> add(MenuOption(SonivellTheme.icons.removeItem, stringResource(R.string.remove_from_queue)) {
                    vm.removeFromQueue(ctx.index)
                })
                is MenuContext.InPlaylist -> add(MenuOption(SonivellTheme.icons.removeItem, stringResource(R.string.remove_from_playlist)) {
                    vm.removeFromPlaylist(ctx.playlistId, ctx.storeIndex)
                })
                MenuContext.None -> Unit
            }
            add(MenuOption(if (liked) SonivellTheme.icons.liked else SonivellTheme.icons.like, stringResource(if (liked) R.string.cd_unlike else R.string.cd_like)) {
                vm.toggleFavorite(track.id)
            })
            add(MenuOption(SonivellTheme.icons.album, stringResource(R.string.go_to_album)) { openAlbum(track.albumId) })
            add(MenuOption(SonivellTheme.icons.artist, stringResource(R.string.go_to_artist)) { openArtist(track.artist) })
            add(MenuOption(SonivellTheme.icons.share, stringResource(R.string.share)) { share(track) })
            add(MenuOption(Icons.Rounded.Edit, stringResource(R.string.tags_edit)) { nav.navigate(Routes.tags(track.id)) })
            add(MenuOption(Icons.Rounded.Image, stringResource(R.string.cover_change)) {
                coverFor = track.albumId
                coverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            if (CustomCovers.has(track.albumId)) {
                add(MenuOption(Icons.Rounded.HideImage, stringResource(R.string.cover_remove)) { CustomCovers.clear(track.albumId) })
            }
            add(MenuOption(Icons.Rounded.NotificationsActive, stringResource(R.string.set_ringtone)) {
                if (Ringtones.canWrite(context)) {
                    toast(res.getString(if (Ringtones.setAsRingtone(context, track)) R.string.ringtone_set else R.string.ringtone_failed))
                } else {
                    toast(res.getString(R.string.ringtone_permission))
                    runCatching { context.startActivity(Ringtones.permissionIntent(context)) }
                }
            })
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
        OptionsSheet(options, onDismiss = { menuFor = null }, header = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackHeader(track)
                StarRow(ratings[track.id] ?: 0) { vm.rate(track.id, it) }
            }
        })
    }

    collectionMenu?.let { data ->
        val playlist = data.playlist
        val options = buildList {
            add(MenuOption(SonivellTheme.icons.playNext, stringResource(R.string.play_next)) {
                vm.playNext(data.tracks); toast(res.getString(R.string.added_next))
            })
            add(MenuOption(SonivellTheme.icons.queue, stringResource(R.string.add_to_queue)) {
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
                add(MenuOption(SonivellTheme.icons.delete, stringResource(R.string.delete)) { deletingSmart = smart })
            }
            if (playlist == null) {
                add(MenuOption(SonivellTheme.icons.addToPlaylist, stringResource(R.string.add_to_playlist)) {
                    addToPlaylist = data.tracks
                })
            } else {
                add(MenuOption(SonivellTheme.icons.edit, stringResource(R.string.rename)) { renaming = playlist })
                add(MenuOption(SonivellTheme.icons.export, stringResource(R.string.export_playlist)) {
                    exportId = playlist.id
                    exportLauncher.launch("${playlist.name}.m3u")
                })
                add(MenuOption(SonivellTheme.icons.delete, stringResource(R.string.delete)) { deleting = playlist })
            }
        }
        OptionsSheet(options, onDismiss = { collectionMenu = null })
    }

    if (playerMenu) {
        val playing = current
        if (playing == null) LaunchedEffect(Unit) { playerMenu = false }
        playing?.let { track ->
            val options = listOf(
                MenuOption(SonivellTheme.icons.sleep, stringResource(R.string.sleep_timer)) { timerSheet = true },
                MenuOption(SonivellTheme.icons.addToPlaylist, stringResource(R.string.add_to_playlist)) { addToPlaylist = listOf(track) },
                MenuOption(SonivellTheme.icons.album, stringResource(R.string.go_to_album)) { openAlbum(track.albumId) },
                MenuOption(SonivellTheme.icons.artist, stringResource(R.string.go_to_artist)) { openArtist(track.artist) },
                MenuOption(SonivellTheme.icons.share, stringResource(R.string.share)) { share(track) },
            )
            OptionsSheet(options, onDismiss = { playerMenu = false }, header = { TrackHeader(track) })
        }
    }

    if (timerSheet) {
        val options = buildList {
            listOf(5, 15, 30, 45, 60).forEach { m ->
                add(MenuOption(SonivellTheme.icons.timer, pluralStringResource(R.plurals.timer_minutes, m, m)) { vm.setSleepTimer(m) })
            }
            add(MenuOption(SonivellTheme.icons.musicNote, stringResource(R.string.timer_end_of_track)) { vm.sleepAtEndOfTrack() })
            if (sleepTimer.active) add(MenuOption(SonivellTheme.icons.timerOff, stringResource(R.string.timer_off)) { vm.cancelSleepTimer() })
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

    confirmRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.backup_restore),
            message = stringResource(R.string.backup_restore_confirm),
            confirmLabel = stringResource(R.string.restore),
            onConfirm = {
                scope.launch {
                    if (vm.restoreBackup(uri)) Backup.restart(context)
                    else toast(res.getString(R.string.backup_invalid))
                }
            },
            onDismiss = { confirmRestore = null },
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
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == route
            // 2.0: aba ativa em branco com ícone preenchido; inativas em cinza.
            val color = if (selected) c.textPrimary else c.textSecondary
            Column(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab, onClick = { onSelect(tab.route) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val icons = SonivellTheme.icons
                Icon(if (selected) tab.activeIcon(icons) else tab.icon(icons), null, Modifier.size(24.dp), tint = color)
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

/** Navegação do layout Clássica (1.0): aba ativa na cor de destaque. */
@Composable
private fun BottomNavClassica(route: String?, onSelect: (String) -> Unit) {
    val c = SonivellTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
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
                val icons = SonivellTheme.icons
                Icon(if (selected) tab.activeIcon(icons) else tab.icon(icons), null, Modifier.size(22.dp), tint = color)
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

/** Navegação do layout Ondas: barra flutuante, a aba ativa preenchida na cor de destaque. */
@Composable
private fun BottomNavOndas(route: String?, onSelect: (String) -> Unit) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == route
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) c.accent else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.route) }),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (selected) c.onAccent else c.textSecondary
                Icon(if (selected) tab.ondasActiveIcon(icons) else tab.ondasIcon(icons), null, Modifier.size(20.dp), tint = color)
                Text(
                    stringResource(tab.label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = color,
                    maxLines = 1,
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

/** Nota de 1 a 5 estrelas; tocar na estrela marcada apaga a nota. */
@Composable
private fun StarRow(stars: Int, onRate: (Int) -> Unit) {
    val c = SonivellTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { n ->
            val on = n <= stars
            val label = pluralStringResource(R.plurals.rating_stars, n, n)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = label) { onRate(n) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, Modifier.size(26.dp), tint = if (on) c.accent else c.textSecondary)
            }
        }
    }
}
