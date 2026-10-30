package com.juscelino.sonivell.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Estilo dos ícones escolhido em Aparência. */
enum class IconStyle {
    /** Traço fino (Material Symbols Outlined 300), o mesmo do Lumivell. Preenchido só no estado ativo. */
    FINO,

    /** Material Icons Rounded, cheio e arredondado. */
    ARREDONDADO,

    /** Os ícones dos mockups 2.0 e 3.0: traço redondo de 1,6, com a onda do Sonivell. */
    TRACO,
}

/**
 * Ícones do app por função, não por desenho: as telas pedem `icons.pause`, e o estilo
 * escolhido decide qual vetor aparece. Os `*Active` são a versão do estado selecionado.
 */
@Immutable
class SonivellIcons(
    val back: ImageVector,
    val close: ImageVector,
    val check: ImageVector,
    val add: ImageVector,
    val remove: ImageVector,
    val search: ImageVector,
    val searchActive: ImageVector,
    val settings: ImageVector,
    val more: ImageVector,
    val moreHoriz: ImageVector,
    val collapse: ImageVector,
    val chevron: ImageVector,
    val play: ImageVector,
    val pause: ImageVector,
    val skipPrevious: ImageVector,
    val skipNext: ImageVector,
    val shuffle: ImageVector,
    val smart: ImageVector,
    val repeat: ImageVector,
    val repeatOne: ImageVector,
    val like: ImageVector,
    val liked: ImageVector,
    val queue: ImageVector,
    val playNext: ImageVector,
    val addToPlaylist: ImageVector,
    val removeItem: ImageVector,
    val dragHandle: ImageVector,
    val album: ImageVector,
    val artist: ImageVector,
    val genre: ImageVector,
    val folder: ImageVector,
    val folderOpen: ImageVector,
    val share: ImageVector,
    val edit: ImageVector,
    val delete: ImageVector,
    val export: ImageVector,
    val sleep: ImageVector,
    val timer: ImageVector,
    val timerOff: ImageVector,
    val musicNote: ImageVector,
    val lyrics: ImageVector,
    val nowPlaying: ImageVector,
    val device: ImageVector,
    val volumeUp: ImageVector,
    val volumeDown: ImageVector,
    val sort: ImageVector,
    val home: ImageVector,
    val homeActive: ImageVector,
    val library: ImageVector,
    val libraryActive: ImageVector,
    /** Aba Para você do layout Ondas. */
    val forYou: ImageVector,
    val forYouActive: ImageVector,
    /** Alternar a Biblioteca entre grade e lista. */
    val grid: ImageVector,
    val list: ImageVector,
)

val RoundedIcons = SonivellIcons(
    back = Icons.AutoMirrored.Rounded.ArrowBack,
    close = Icons.Rounded.Close,
    check = Icons.Rounded.Check,
    add = Icons.Rounded.Add,
    remove = Icons.Rounded.Remove,
    search = Icons.Rounded.Search,
    searchActive = Icons.Rounded.Search,
    settings = Icons.Rounded.Settings,
    more = Icons.Rounded.MoreVert,
    moreHoriz = Icons.Rounded.MoreHoriz,
    collapse = Icons.Rounded.KeyboardArrowDown,
    chevron = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    play = Icons.Rounded.PlayArrow,
    pause = Icons.Rounded.Pause,
    skipPrevious = Icons.Rounded.SkipPrevious,
    skipNext = Icons.Rounded.SkipNext,
    shuffle = Icons.Rounded.Shuffle,
    smart = Icons.Rounded.AutoAwesome,
    repeat = Icons.Rounded.Repeat,
    repeatOne = Icons.Rounded.RepeatOne,
    like = Icons.Rounded.FavoriteBorder,
    liked = Icons.Rounded.Favorite,
    queue = Icons.AutoMirrored.Rounded.QueueMusic,
    playNext = Icons.Rounded.QueuePlayNext,
    addToPlaylist = Icons.AutoMirrored.Rounded.PlaylistAdd,
    removeItem = Icons.Rounded.RemoveCircleOutline,
    dragHandle = Icons.Rounded.DragHandle,
    album = Icons.Rounded.Album,
    artist = Icons.Rounded.Person,
    genre = Icons.Rounded.Category,
    folder = Icons.Rounded.Folder,
    folderOpen = Icons.Rounded.FolderOpen,
    share = Icons.Rounded.Share,
    edit = Icons.Rounded.Edit,
    delete = Icons.Rounded.DeleteOutline,
    export = Icons.Rounded.FileUpload,
    sleep = Icons.Rounded.Bedtime,
    timer = Icons.Rounded.Timer,
    timerOff = Icons.Rounded.TimerOff,
    musicNote = Icons.Rounded.MusicNote,
    lyrics = Icons.Rounded.Lyrics,
    nowPlaying = Icons.Rounded.GraphicEq,
    device = Icons.Rounded.PhoneAndroid,
    volumeUp = Icons.AutoMirrored.Rounded.VolumeUp,
    volumeDown = Icons.AutoMirrored.Rounded.VolumeDown,
    sort = Icons.Rounded.SwapVert,
    home = Icons.Rounded.Home,
    homeActive = Icons.Rounded.Home,
    library = Icons.Rounded.LibraryMusic,
    libraryActive = Icons.Rounded.LibraryMusic,
    // A onda do Sonivell em todos os estilos (traço mais grosso, como os ícones cheios daqui).
    forYou = waveIcon("Rounded.forYou", 2.2f),
    forYouActive = waveIcon("Rounded.forYouActive", 2.8f),
    grid = Icons.Rounded.GridView,
    list = Icons.AutoMirrored.Rounded.List,
)

/** Monta um vetor a partir de um caminho do Material Symbols (viewBox "0 -960 960 960"). */
private fun symbol(name: String, path: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = "Fino.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = autoMirror,
    )
        .addGroup(translationY = 960f)
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()

/** Montado só quando usado pela primeira vez. */
val FinoIcons: SonivellIcons by lazy {
    val p = FinoIconPaths
    SonivellIcons(
        back = symbol("back", p.ARROW_BACK, autoMirror = true),
        close = symbol("close", p.CLOSE),
        check = symbol("check", p.CHECK),
        add = symbol("add", p.ADD),
        remove = symbol("remove", p.REMOVE),
        search = symbol("search", p.SEARCH),
        searchActive = symbol("searchActive", p.SEARCH_FILL),
        settings = symbol("settings", p.SETTINGS),
        more = symbol("more", p.MORE_VERT),
        moreHoriz = symbol("moreHoriz", p.MORE_HORIZ),
        collapse = symbol("collapse", p.ARROW_DOWN),
        chevron = symbol("chevron", p.ARROW_RIGHT, autoMirror = true),
        play = symbol("play", p.PLAY_FILL),
        pause = symbol("pause", p.PAUSE_FILL),
        skipPrevious = symbol("skipPrevious", p.SKIP_PREVIOUS),
        skipNext = symbol("skipNext", p.SKIP_NEXT),
        shuffle = symbol("shuffle", p.SHUFFLE),
        smart = symbol("smart", p.SMART),
        repeat = symbol("repeat", p.REPEAT),
        repeatOne = symbol("repeatOne", p.REPEAT_ONE),
        like = symbol("like", p.FAVORITE),
        liked = symbol("liked", p.FAVORITE_FILL),
        queue = symbol("queue", p.QUEUE_MUSIC, autoMirror = true),
        playNext = symbol("playNext", p.QUEUE_PLAY_NEXT),
        addToPlaylist = symbol("addToPlaylist", p.PLAYLIST_ADD, autoMirror = true),
        removeItem = symbol("removeItem", p.REMOVE_CIRCLE),
        dragHandle = symbol("dragHandle", p.DRAG_HANDLE),
        album = symbol("album", p.ALBUM),
        artist = symbol("artist", p.PERSON),
        genre = symbol("genre", p.CATEGORY),
        folder = symbol("folder", p.FOLDER),
        folderOpen = symbol("folderOpen", p.FOLDER_OPEN),
        share = symbol("share", p.SHARE),
        edit = symbol("edit", p.EDIT),
        delete = symbol("delete", p.DELETE),
        export = symbol("export", p.UPLOAD),
        sleep = symbol("sleep", p.BEDTIME),
        timer = symbol("timer", p.TIMER),
        timerOff = symbol("timerOff", p.TIMER_OFF),
        musicNote = symbol("musicNote", p.MUSIC_NOTE),
        lyrics = symbol("lyrics", p.LYRICS),
        nowPlaying = symbol("nowPlaying", p.GRAPHIC_EQ),
        device = symbol("device", p.PHONE),
        volumeUp = symbol("volumeUp", p.VOLUME_UP, autoMirror = true),
        volumeDown = symbol("volumeDown", p.VOLUME_DOWN, autoMirror = true),
        sort = symbol("sort", p.SWAP_VERT),
        home = symbol("home", p.HOME),
        homeActive = symbol("homeActive", p.HOME_FILL),
        library = symbol("library", p.LIBRARY),
        libraryActive = symbol("libraryActive", p.LIBRARY_FILL),
        // A onda do Sonivell também no Fino (traço leve; mais firme na aba atual).
        forYou = waveIcon("Fino.forYou", 1.4f),
        forYouActive = waveIcon("Fino.forYouActive", 2.2f),
        grid = TracoIcons.grid,
        list = TracoIcons.list,
    )
}

fun IconStyle.icons(): SonivellIcons = when (this) {
    IconStyle.FINO -> FinoIcons
    IconStyle.ARREDONDADO -> RoundedIcons
    IconStyle.TRACO -> TracoIcons
}

val LocalSonivellIcons = staticCompositionLocalOf { RoundedIcons }
