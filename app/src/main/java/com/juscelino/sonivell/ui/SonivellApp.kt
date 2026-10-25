package com.juscelino.sonivell.ui

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.juscelino.sonivell.R
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
    val smartSource = stringResource(R.string.source_smart)

    val defaultSource = stringResource(R.string.nav_library)
    var source by rememberSaveable { mutableStateOf(defaultSource) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.coming_soon)
    val shuffleSource = stringResource(R.string.source_shuffle)
    val context = LocalContext.current
    val soon: () -> Unit = { scope.launch { snackbar.showSnackbar(comingSoon) } }

    val libraryActions = LibraryActions(
        onQuery = vm::setQuery,
        onTab = vm::setTab,
        onSort = vm::cycleSort,
        onRefresh = vm::refresh,
        onPlay = { tracks, index, src ->
            source = if (shuffleMode == ShuffleMode.SMART) smartSource else src
            vm.playFrom(tracks, index)
        },
        onShuffleAll = {
            source = if (shuffleMode == ShuffleMode.SMART) smartSource else shuffleSource
            vm.playAllShuffled()
        },
    )

    val showChrome = route in tabs.map { it.route }

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
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding(),
                        ),
                        actions = libraryActions,
                        smartProgress = smartProgress,
                    )
                }
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
                        actions = NowPlayingActions(
                            onClose = { nav.popBackStack() },
                            onMore = soon,
                            onTogglePlay = vm.player::togglePlay,
                            onNext = { vm.player.next() },
                            onPrevious = { vm.player.previous() },
                            onSeek = vm.player::seekTo,
                            onShuffle = vm::cycleShuffle,
                            onRepeat = vm.player::cycleRepeat,
                            onFavorite = { vm.toggleFavorite(t.id) },
                            onLyrics = soon,
                            onQueue = { nav.navigate(Routes.QUEUE) },
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
                        onSavePlaylist = soon,
                    ),
                )
            }
        }
    }
}

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
