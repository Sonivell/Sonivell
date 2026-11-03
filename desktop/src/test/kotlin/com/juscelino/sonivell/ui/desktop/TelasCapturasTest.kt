package com.juscelino.sonivell.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.LibraryState
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ShuffleMode
import com.juscelino.sonivell.compat.Uri
import com.juscelino.sonivell.data.Album
import com.juscelino.sonivell.data.Artist
import com.juscelino.sonivell.data.Folder
import com.juscelino.sonivell.data.ForYouState
import com.juscelino.sonivell.data.Genre
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.UiStyle
import com.juscelino.sonivell.data.iconStyle
import com.juscelino.sonivell.playback.PlayerState
import com.juscelino.sonivell.playback.toMediaItem
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.library.LibraryActions
import com.juscelino.sonivell.ui.library.LibraryMode
import com.juscelino.sonivell.ui.library.LibraryScreenClassica
import com.juscelino.sonivell.ui.library.LibraryScreenContraste
import com.juscelino.sonivell.ui.library.LibraryScreenEssencial
import com.juscelino.sonivell.ui.library.LibraryScreenOndas
import com.juscelino.sonivell.ui.nowplaying.NowPlayingActions
import com.juscelino.sonivell.ui.nowplaying.NowPlayingScreenClassica
import com.juscelino.sonivell.ui.nowplaying.NowPlayingScreenContraste
import com.juscelino.sonivell.ui.nowplaying.NowPlayingScreenOndas
import com.juscelino.sonivell.ui.sonivell.SvHomeScreen
import com.juscelino.sonivell.ui.sonivell.SvProfileScreen
import com.juscelino.sonivell.ui.theme.AccentChoice
import com.juscelino.sonivell.ui.theme.SonivellTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Capturas das telas do PC em cada estilo, desenhadas sem janela (para conferir o visual no CI):
 * Para você, Biblioteca, Perfil e o player, com a barra lateral e a barra do player.
 */
@OptIn(ExperimentalComposeUiApi::class)
class TelasCapturasTest {
    private val out = File("build/capturas-onda").apply { mkdirs() }

    private val tracks: List<Track> = listOf(
        Triple("Maré Alta", "Lua Norte", "Horizonte"), Triple("Âncora", "Lua Norte", "Horizonte"),
        Triple("Madrugada Azul", "Lua Norte", "Horizonte"), Triple("Casa de Vento", "Rio Acima", "Rio Acima"),
        Triple("Pedra e Pó", "Rio Acima", "Rio Acima"), Triple("Farol", "Sal Grosso", "Sal Grosso"),
        Triple("Brasa Lenta", "Sal Grosso", "Sal Grosso"), Triple("Chuva de Sal", "Sal Grosso", "Sal Grosso"),
        Triple("Noites de Vidro", "Marina Ruído", "Vidro e Sal"), Triple("Vidro e Sal", "Marina Ruído", "Vidro e Sal"),
        Triple("Janela Molhada", "Marina Ruído", "Vidro e Sal"), Triple("Live from Boiler Room in Cologne", "MÉTARAPH", "Boiler Room"),
    ).mapIndexed { i, (title, artist, album) ->
        Track(
            id = 1000L + i, uri = Uri.parse("/nao/existe/$i.flac"), title = title, artist = artist, album = album,
            albumId = album.hashCode().toLong(), durationMs = 180_000L + i * 17_000L, mimeType = if (i % 2 == 0) "audio/flac" else "audio/mpeg",
            folder = "/musica/$artist", dateAdded = 1_700_000_000L + i * 3600, rawTitle = title, rawArtist = artist, rawAlbum = album,
            genre = listOf("Indie", "Folk", "Eletrônica", "Pop")[i % 4], year = 2018 + i % 6, trackNumber = i % 3 + 1,
            fileName = "$i.flac", sizeBytes = 8_000_000L, dateModified = 1_700_000_000L,
        )
    }

    private val library = LibraryState(
        loading = false,
        allTracks = tracks,
        tracks = tracks,
        albums = tracks.groupBy { it.albumId }.map { (id, ts) -> Album(id, ts.first().album, ts.first().artist, ts) },
        artists = tracks.groupBy { it.artist }.map { (n, ts) -> Artist(n, ts) },
        folders = tracks.groupBy { it.folder }.map { (p, ts) -> Folder(p, ts) },
        genres = tracks.groupBy { it.genre!! }.map { (n, ts) -> Genre(n, ts) },
        recentAlbums = tracks.groupBy { it.albumId }.map { (id, ts) -> Album(id, ts.first().album, ts.first().artist, ts) },
    )

    private val forYou = ForYouState(
        pick = tracks[4], pickNeverPlayed = true, unfinished = listOf(tracks[8]), forgottenFavorites = tracks.take(4),
        thisHour = tracks.drop(3).take(6), mixSeeds = listOf(tracks[0], tracks[3], tracks[5], tracks[8]), resumeAtMs = mapOf(tracks[8].id to 90_000L),
    )

    private val current = tracks.last()
    private val player = PlayerState(queue = tracks.map { it.toMediaItem() }, currentIndex = tracks.lastIndex, isPlaying = true, positionMs = 97_000, durationMs = current.durationMs)
    private val actions = LibraryActions(onQuery = {}, onTab = {}, onSort = {}, onRefresh = {}, onPlay = { _, _, _ -> }, onShuffleAll = {}, onPlayMood = {})
    private val np = NowPlayingActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val smart = SmartProgress(running = true, analysed = 640, total = 1990, metadataReady = true)

    @Composable
    private fun Window(style: UiStyle, tab: String, content: @Composable () -> Unit) {
        SonivellTheme(accent = AccentChoice.Lima, darkTheme = true, iconStyle = style.iconStyle, style = style) {
            val icons = SonivellTheme.icons
            val tabs = listOf(
                DesktopTab("inicio", R.string.nav_home, icons.forYou, icons.forYouActive),
                DesktopTab("buscar", R.string.nav_explore, icons.search, icons.searchActive),
                DesktopTab("biblioteca", R.string.nav_library, icons.library, icons.libraryActive),
                DesktopTab("perfil", R.string.nav_profile, Icons.Outlined.Person, Icons.Filled.Person),
            )
            Column(Modifier.fillMaxSize().background(SonivellTheme.colors.background).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DesktopSidebar(tabs, tab, {}, null, true, smart, {})
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp))) { content() }
                }
                DesktopPlayerBar(
                    current, player, ShuffleMode.SMART, true, true, 0.7f, false,
                    PlayerBarActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                )
            }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(1400, 880, Density(1f)) { content() }
        try {
            scene.render(0)
            scene.render(500_000_000)
            val img = scene.render(1_000_000_000)
            File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun Home(style: UiStyle) {
        val pad = PaddingValues(top = 8.dp, bottom = 16.dp)
        when (style) {
            UiStyle.SONIVELL -> SvHomeScreen(pad, library, 12, 0, forYou.mixSeeds, true, smart, {}, {}, {}, {}, {}, {}, tracks[8], 90_000L)
            UiStyle.ONDAS -> LibraryScreenOndas(mode = LibraryMode.Inicio, state = library, favorites = setOf(tracks[0].id), currentId = current.id, contentPadding = pad, actions = actions, smartProgress = smart, forYou = forYou)
            UiStyle.ESSENCIAL -> LibraryScreenEssencial(mode = LibraryMode.Inicio, state = library, favorites = setOf(tracks[0].id), currentId = current.id, contentPadding = pad, actions = actions, forYou = forYou)
            UiStyle.CONTRASTE -> LibraryScreenContraste(mode = LibraryMode.Inicio, state = library, favorites = setOf(tracks[0].id), currentId = current.id, contentPadding = pad, actions = actions, smartProgress = smart, forYou = forYou)
            UiStyle.CLASSICA -> LibraryScreenClassica(mode = LibraryMode.Inicio, state = library, favorites = setOf(tracks[0].id), currentId = current.id, contentPadding = pad, actions = actions, smartProgress = smart, forYou = forYou)
        }
    }

    @Composable
    private fun Library(style: UiStyle) {
        val pad = PaddingValues(top = 8.dp, bottom = 16.dp)
        when (style) {
            UiStyle.SONIVELL, UiStyle.ONDAS -> LibraryScreenOndas(mode = LibraryMode.Biblioteca, state = library, favorites = emptySet(), currentId = current.id, contentPadding = pad, actions = actions)
            UiStyle.ESSENCIAL -> LibraryScreenEssencial(mode = LibraryMode.Biblioteca, state = library, favorites = emptySet(), currentId = current.id, contentPadding = pad, actions = actions)
            UiStyle.CONTRASTE -> LibraryScreenContraste(mode = LibraryMode.Biblioteca, state = library, favorites = emptySet(), currentId = current.id, contentPadding = pad, actions = actions)
            UiStyle.CLASSICA -> LibraryScreenClassica(mode = LibraryMode.Biblioteca, state = library, favorites = emptySet(), currentId = current.id, contentPadding = pad, actions = actions)
        }
    }

    @Composable
    private fun Player(style: UiStyle) {
        when (style) {
            UiStyle.CONTRASTE -> NowPlayingScreenContraste(current, player, ShuffleMode.SMART, true, true, "Seu Flow de hoje", np)
            UiStyle.CLASSICA -> NowPlayingScreenClassica(current, player, ShuffleMode.SMART, true, true, "Seu Flow de hoje", np)
            else -> NowPlayingScreenOndas(current, player, ShuffleMode.SMART, true, true, "Seu Flow de hoje", np, style = style)
        }
    }

    @Test
    fun capturasDasTelas() {
        // As capturas saem em português, como o app na máquina do usuário.
        val antes = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"))
        try { capturas() } finally { java.util.Locale.setDefault(antes) }
    }

    private fun capturas() {
        UiStyle.entries.forEach { style ->
            val n = style.name.lowercase()
            runCatching { shot("tela-$n-1-para-voce") { Window(style, "inicio") { Home(style) } } }.onFailure { println("FALHOU $n para você: $it") }
            runCatching { shot("tela-$n-2-biblioteca") { Window(style, "biblioteca") { Library(style) } } }.onFailure { println("FALHOU $n biblioteca: $it") }
            runCatching { shot("tela-$n-3-player") { Window(style, "inicio") { Player(style) } } }.onFailure { println("FALHOU $n player: $it") }
        }
        runCatching {
            shot("tela-perfil") {
                Window(UiStyle.SONIVELL, "perfil") {
                    SvProfileScreen(1990, 1053, 43_200_000L, true, 640, 1990, "Sonivell", PaddingValues(top = 8.dp), {}, {}, {}, {}, {}, {})
                }
            }
        }.onFailure { println("FALHOU perfil: $it") }
    }
}
