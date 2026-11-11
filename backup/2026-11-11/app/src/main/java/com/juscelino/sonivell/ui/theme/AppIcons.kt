package com.juscelino.sonivell.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Estilo dos ícones escolhido em Aparência: o peso do traço do Phosphor (phosphoricons.com).
 * Os nomes das constantes ficam os de antes para a escolha salva continuar valendo.
 */
enum class IconStyle {
    /** Phosphor Light: traço fino. */
    FINO,

    /** Phosphor Bold: traço grosso. */
    ARREDONDADO,

    /** Phosphor Regular: o peso padrão. */
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
    /** Aba Para você: a onda do Sonivell, igual em todos os estilos. */
    val forYou: ImageVector,
    val forYouActive: ImageVector,
    /** Alternar a Biblioteca entre grade e lista. */
    val grid: ImageVector,
    val list: ImageVector,
    /** Aba Perfil. */
    val profile: ImageVector,
    val profileActive: ImageVector,
    /** Coleções automáticas e atalhos do Para você. */
    val trendUp: ImageVector,
    val trendDown: ImageVector,
    val fresh: ImageVector,
    val recent: ImageVector,
    val star: ImageVector,
    val starFilled: ImageVector,
    /** Menu da faixa e da coleção. */
    val image: ImageVector,
    val imageRemove: ImageVector,
    val ringtone: ImageVector,
    val block: ImageVector,
    val blockArtist: ImageVector,
    val rules: ImageVector,
    val save: ImageVector,
    /** Linhas da tela de Ajustes. */
    val appearance: ImageVector,
    val car: ImageVector,
    val playback: ImageVector,
    val transitions: ImageVector,
    val history: ImageVector,
    val backup: ImageVector,
    val info: ImageVector,
)

/** Peso do Phosphor de cada estilo e a espessura da onda que combina com ele. */
private enum class Weight(val wave: Float, val waveActive: Float) {
    LIGHT(1.3f, 2.1f),
    REGULAR(1.6f, 2.4f),
    BOLD(2.2f, 2.8f),
}

private fun PhosphorPath.at(w: Weight): String = when (w) {
    Weight.LIGHT -> light
    Weight.REGULAR -> regular
    Weight.BOLD -> bold
}

/** Monta um vetor a partir de um caminho do Phosphor (viewBox 0 0 256 256, área cheia). */
private fun vector(name: String, path: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = "Phosphor.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
        autoMirror = autoMirror,
    )
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

/**
 * O conjunto inteiro num peso. Estados ativos (aba atual, curtida, estrela marcada) e os
 * controles de reprodução usam o Fill do Phosphor, como num player: cheios leem melhor.
 */
private fun phosphorIcons(w: Weight): SonivellIcons {
    val P = Phosphor
    fun line(name: String, p: PhosphorPath, autoMirror: Boolean = false) = vector("${w.name}.$name", p.at(w), autoMirror)
    fun solid(name: String, p: PhosphorPath) = vector("Fill.$name", p.fill)
    return SonivellIcons(
        back = line("back", P.ArrowLeft, autoMirror = true),
        close = line("close", P.X),
        check = line("check", P.Check),
        add = line("add", P.Plus),
        remove = line("remove", P.Minus),
        search = line("search", P.MagnifyingGlass),
        searchActive = solid("search", P.MagnifyingGlass),
        settings = line("settings", P.GearSix),
        more = line("more", P.DotsThreeVertical),
        moreHoriz = line("moreHoriz", P.DotsThree),
        collapse = line("collapse", P.CaretDown),
        chevron = line("chevron", P.CaretRight, autoMirror = true),
        play = solid("play", P.Play),
        pause = solid("pause", P.Pause),
        skipPrevious = solid("skipPrevious", P.SkipBack),
        skipNext = solid("skipNext", P.SkipForward),
        shuffle = line("shuffle", P.Shuffle),
        smart = line("smart", P.Sparkle),
        repeat = line("repeat", P.Repeat),
        repeatOne = line("repeatOne", P.RepeatOnce),
        like = line("like", P.Heart),
        liked = solid("liked", P.Heart),
        queue = line("queue", P.Queue),
        playNext = line("playNext", P.RowsPlusTop),
        addToPlaylist = line("addToPlaylist", P.ListPlus),
        removeItem = line("removeItem", P.MinusCircle),
        dragHandle = line("dragHandle", P.DotsSixVertical),
        album = line("album", P.VinylRecord),
        artist = line("artist", P.User),
        genre = line("genre", P.Guitar),
        folder = line("folder", P.Folder),
        folderOpen = line("folderOpen", P.FolderOpen),
        share = line("share", P.ShareNetwork),
        edit = line("edit", P.PencilSimple),
        delete = line("delete", P.Trash),
        export = line("export", P.Export),
        sleep = line("sleep", P.Moon),
        timer = line("timer", P.Timer),
        timerOff = line("timerOff", P.XCircle),
        musicNote = line("musicNote", P.MusicNote),
        lyrics = line("lyrics", P.MicrophoneStage),
        nowPlaying = line("nowPlaying", P.Equalizer),
        device = line("device", P.DeviceMobile),
        volumeUp = line("volumeUp", P.SpeakerHigh),
        volumeDown = line("volumeDown", P.SpeakerLow),
        sort = line("sort", P.ArrowsDownUp),
        home = line("home", P.House),
        homeActive = solid("home", P.House),
        library = line("library", P.MusicNotes),
        libraryActive = solid("library", P.MusicNotes),
        forYou = waveIcon("${w.name}.forYou", w.wave),
        forYouActive = waveIcon("${w.name}.forYouActive", w.waveActive),
        grid = line("grid", P.SquaresFour),
        list = line("list", P.List),
        profile = line("profile", P.UserCircle),
        profileActive = solid("profile", P.UserCircle),
        trendUp = line("trendUp", P.TrendUp),
        trendDown = line("trendDown", P.TrendDown),
        fresh = line("fresh", P.StarFour),
        recent = line("recent", P.Clock),
        star = line("star", P.Star),
        starFilled = solid("star", P.Star),
        image = line("image", P.Image),
        imageRemove = line("imageRemove", P.ImageBroken),
        ringtone = line("ringtone", P.BellRinging),
        block = line("block", P.Prohibit),
        blockArtist = line("blockArtist", P.UserMinus),
        rules = line("rules", P.SlidersHorizontal),
        save = line("save", P.FloppyDisk),
        appearance = line("appearance", P.Palette),
        car = line("car", P.Car),
        playback = line("playback", P.PlayCircle),
        transitions = line("transitions", P.ArrowsLeftRight),
        history = line("history", P.ChartLineUp),
        backup = line("backup", P.CloudArrowUp),
        info = line("info", P.Info),
    )
}

/** Cada peso é montado só quando usado pela primeira vez. */
private val LightIcons: SonivellIcons by lazy { phosphorIcons(Weight.LIGHT) }
private val RegularIcons: SonivellIcons by lazy { phosphorIcons(Weight.REGULAR) }
private val BoldIcons: SonivellIcons by lazy { phosphorIcons(Weight.BOLD) }

fun IconStyle.icons(): SonivellIcons = when (this) {
    IconStyle.FINO -> LightIcons
    IconStyle.ARREDONDADO -> BoldIcons
    IconStyle.TRACO -> RegularIcons
}

val LocalSonivellIcons = staticCompositionLocalOf { IconStyle.TRACO.icons() }
