package com.juscelino.sonivell.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Estilo dos ícones: o peso do traço (no teste do Iconsax, a espessura do Linear).
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

/**
 * Espessura do traço do Iconsax Linear em cada estilo (o original é 1,5) e a da onda que combina.
 * TESTE (branch android/icones-iconsax): o Iconsax no lugar do Phosphor.
 */
private enum class Weight(val stroke: Float, val wave: Float, val waveActive: Float) {
    LIGHT(1.25f, 1.3f, 2.1f),
    REGULAR(1.5f, 1.6f, 2.4f),
    BOLD(2f, 2.2f, 2.8f),
}

/** Ícones que o Iconsax gratuito não tem, desenhados no mesmo traço (24×24, 1,5, pontas redondas). */
private object IxExtra {
    private fun stroke(vararg d: String) = d.map { IxPart(it, false, 1.5f, 'r', 'r', 1f) }

    val Check = stroke("M5 12.5L9.5 17L19 7.5").let { IxIcon(it, it) }
    val Shuffle = stroke(
        "M3 17H5.6C7 17 8.3 16.25 9 15.05L12.9 8.95C13.6 7.75 14.9 7 16.3 7H21.5",
        "M19 4.5L21.5 7L19 9.5",
        "M3 7H5.6C7 7 8.3 7.75 9 8.95L9.75 10.25",
        "M14.25 13.75L15 15.05C15.7 16.25 17 17 18.4 17H21.5",
        "M19 14.5L21.5 17L19 19.5",
    ).let { IxIcon(it, it) }
}

/**
 * Monta um vetor com as partes de um ícone do Iconsax (viewBox 0 0 24 24). [strokeScale] engrossa
 * ou afina os traços; [rotate] gira o desenho em torno do centro (X a partir do +, ⋮ a partir do ⋯).
 */
private fun ixVector(
    name: String,
    parts: List<IxPart>,
    shift: Pair<Float, Float>,
    strokeScale: Float = 1f,
    rotate: Float = 0f,
    autoMirror: Boolean = false,
): ImageVector {
    val b = ImageVector.Builder(
        name = "Iconsax.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    )
    b.addGroup(rotate = rotate, pivotX = 12f, pivotY = 12f, translationX = shift.first, translationY = shift.second)
    parts.forEach { p ->
        b.addPath(
            pathData = addPathNodes(p.d),
            fill = if (p.fill) SolidColor(Color.Black) else null,
            fillAlpha = p.alpha,
            stroke = if (p.stroke > 0f) SolidColor(Color.Black) else null,
            strokeAlpha = p.alpha,
            strokeLineWidth = p.stroke * strokeScale,
            strokeLineCap = when (p.cap) { 'r' -> StrokeCap.Round; 's' -> StrokeCap.Square; else -> StrokeCap.Butt },
            strokeLineJoin = when (p.join) { 'r' -> StrokeJoin.Round; 'b' -> StrokeJoin.Bevel; else -> StrokeJoin.Miter },
            strokeLineMiter = 10f,
        )
    }
    b.clearGroup()
    return b.build()
}

/**
 * O conjunto inteiro num peso. Linear (traço) no geral; estados ativos (aba atual, curtida, estrela
 * marcada) e os controles de reprodução usam o Bold do Iconsax, como num player: cheios leem melhor.
 */
private fun iconsaxIcons(w: Weight): SonivellIcons {
    val X = Iconsax
    val scale = w.stroke / 1.5f
    fun line(name: String, i: IxIcon, rotate: Float = 0f, autoMirror: Boolean = false) =
        ixVector("${w.name}.$name", i.linear, i.linearShift, scale, rotate, autoMirror)
    fun solid(name: String, i: IxIcon) = ixVector("Bold.$name", i.bold, i.boldShift)
    return SonivellIcons(
        back = line("back", X.ArrowLeft, autoMirror = true),
        close = line("close", X.Add, rotate = 45f),
        check = line("check", IxExtra.Check),
        add = line("add", X.Add),
        remove = line("remove", X.Minus),
        search = line("search", X.SearchNormal1),
        searchActive = solid("search", X.SearchNormal1),
        settings = line("settings", X.Setting2),
        more = line("more", X.More, rotate = 90f),
        moreHoriz = line("moreHoriz", X.More),
        collapse = line("collapse", X.ArrowLeft1, rotate = -90f),
        chevron = line("chevron", X.ArrowRight1, autoMirror = true),
        play = solid("play", X.Play),
        pause = solid("pause", X.Pause),
        skipPrevious = solid("skipPrevious", X.Previous),
        skipNext = solid("skipNext", X.Next),
        shuffle = line("shuffle", IxExtra.Shuffle),
        smart = line("smart", X.MagicStar),
        repeat = line("repeat", X.RepeateMusic),
        repeatOne = line("repeatOne", X.RepeateOne),
        like = line("like", X.Heart),
        liked = solid("liked", X.Heart),
        queue = line("queue", X.MusicFilter),
        playNext = line("playNext", X.PlayAdd),
        addToPlaylist = line("addToPlaylist", X.MusicSquareAdd),
        removeItem = line("removeItem", X.MinusCirlce),
        dragHandle = line("dragHandle", X.Menu),
        album = line("album", X.Cd),
        artist = line("artist", X.User),
        genre = line("genre", X.MusicSquare),
        folder = line("folder", X.Folder),
        folderOpen = line("folderOpen", X.FolderOpen),
        share = line("share", X.Share),
        edit = line("edit", X.Edit2),
        delete = line("delete", X.Trash),
        export = line("export", X.Export),
        sleep = line("sleep", X.Moon),
        timer = line("timer", X.Timer1),
        timerOff = line("timerOff", X.CloseCircle),
        musicNote = line("musicNote", X.Musicnote),
        lyrics = line("lyrics", X.Microphone2),
        nowPlaying = line("nowPlaying", X.Sound),
        device = line("device", X.Mobile),
        volumeUp = line("volumeUp", X.VolumeHigh),
        volumeDown = line("volumeDown", X.VolumeLow1),
        sort = line("sort", X.Sort),
        home = line("home", X.Home2),
        homeActive = solid("home", X.Home2),
        library = line("library", X.MusicLibrary2),
        libraryActive = solid("library", X.MusicLibrary2),
        forYou = waveIcon("${w.name}.forYou", w.wave),
        forYouActive = waveIcon("${w.name}.forYouActive", w.waveActive),
        grid = line("grid", X.Category),
        list = line("list", X.TextalignJustifyleft),
        profile = line("profile", X.ProfileCircle),
        profileActive = solid("profile", X.ProfileCircle),
        trendUp = line("trendUp", X.TrendUp),
        trendDown = line("trendDown", X.TrendDown),
        fresh = line("fresh", X.Star),
        recent = line("recent", X.Clock),
        star = line("star", X.Star1),
        starFilled = solid("star", X.Star1),
        image = line("image", X.Gallery),
        imageRemove = line("imageRemove", X.GallerySlash),
        ringtone = line("ringtone", X.NotificationBing),
        block = line("block", X.Slash),
        blockArtist = line("blockArtist", X.UserMinus),
        rules = line("rules", X.Setting4),
        save = line("save", X.SaveAdd),
        appearance = line("appearance", X.Colorfilter),
        car = line("car", X.Car),
        playback = line("playback", X.PlayCircle),
        transitions = line("transitions", X.ArrowSwapHorizontal),
        history = line("history", X.Chart),
        backup = line("backup", X.CloudChange),
        info = line("info", X.InfoCircle),
    )
}

/** Cada peso é montado só quando usado pela primeira vez. */
private val LightIcons: SonivellIcons by lazy { iconsaxIcons(Weight.LIGHT) }
private val RegularIcons: SonivellIcons by lazy { iconsaxIcons(Weight.REGULAR) }
private val BoldIcons: SonivellIcons by lazy { iconsaxIcons(Weight.BOLD) }

fun IconStyle.icons(): SonivellIcons = when (this) {
    IconStyle.FINO -> LightIcons
    IconStyle.ARREDONDADO -> BoldIcons
    IconStyle.TRACO -> RegularIcons
}

val LocalSonivellIcons = staticCompositionLocalOf { IconStyle.TRACO.icons() }
