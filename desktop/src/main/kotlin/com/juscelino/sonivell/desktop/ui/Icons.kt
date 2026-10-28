package com.juscelino.sonivell.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Estilo "Traço": os ícones desenhados nos mockups 2.0 e 3.0 (viewBox 24, traço arredondado).
 * Cada ícone é uma lista de partes: traços ([s]) e áreas cheias ([f]).
 */
private sealed interface Part {
    val d: String
}

private data class S(override val d: String, val w: Float = 1.6f) : Part
private data class F(override val d: String) : Part

private fun s(d: String, w: Float = 1.6f) = S(d, w)
private fun f(d: String) = F(d)

/** Círculo como caminho SVG (o PathParser não tem `circle`). */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0z"

/** Retângulo de cantos arredondados como caminho SVG. */
private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r},$y h${w - 2 * r} a$r,$r 0 0 1 $r,$r v${h - 2 * r} a$r,$r 0 0 1 ${-r},$r " +
        "h${-(w - 2 * r)} a$r,$r 0 0 1 ${-r},${-r} v${-(h - 2 * r)} a$r,$r 0 0 1 $r,${-r}z"

private fun icon(name: String, vararg parts: Part, autoMirror: Boolean = false): ImageVector {
    val b = ImageVector.Builder(
        name = "Traco.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    )
    for (p in parts) when (p) {
        is S -> b.addPath(
            pathData = addPathNodes(p.d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = p.w,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        is F -> b.addPath(pathData = addPathNodes(p.d), fill = SolidColor(Color.Black))
    }
    return b.build()
}

private const val HEART = "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"
private const val WAVE = "M3 12c2-5 4-5 6 0s4 5 6 0 4-5 6 0"
private const val HOUSE = "M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"
private const val REPEAT = "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3"
private const val SPEAKER = "M4 9v6h4l5 4V5L8 9z"
private const val FOLDER = "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"


/** Ícones do estilo Traço (os mesmos do app Android). */
object Traco {
    private val searchParts = arrayOf(s(circle(11f, 11f, 7f)), s("M20 20l-3.5-3.5"))
    private val libraryShape = rrect(4f, 4f, 16f, 16f, 4f)
    val back: ImageVector by lazy { icon("back", s("M19 12H5M11 6l-6 6 6 6"), autoMirror = true) }
    val close: ImageVector by lazy { icon("close", s("M6 6l12 12M18 6L6 18")) }
    val check: ImageVector by lazy { icon("check", s("M5 12.5l4.5 4.5L19 7.5")) }
    val add: ImageVector by lazy { icon("add", s("M12 5v14M5 12h14")) }
    val remove: ImageVector by lazy { icon("remove", s("M5 12h14")) }
    val search: ImageVector by lazy { icon("search", *searchParts) }
    val searchActive: ImageVector by lazy { icon("searchActive", s(circle(11f, 11f, 7f), 2.4f), s("M20 20l-3.5-3.5", 2.4f)) }
    val settings: ImageVector by lazy { icon("settings", s("M4 7h10M18 7h2M4 17h4M12 17h8"), s(circle(16f, 7f, 2f)), s(circle(10f, 17f, 2f))) }
    val more: ImageVector by lazy { icon("more", f(circle(12f, 5f, 1.6f)), f(circle(12f, 12f, 1.6f)), f(circle(12f, 19f, 1.6f))) }
    val moreHoriz: ImageVector by lazy { icon("moreHoriz", f(circle(5f, 12f, 1.6f)), f(circle(12f, 12f, 1.6f)), f(circle(19f, 12f, 1.6f))) }
    val collapse: ImageVector by lazy { icon("collapse", s("M6 9l6 6 6-6")) }
    val chevron: ImageVector by lazy { icon("chevron", s("M9 6l6 6-6 6"), autoMirror = true) }
    val play: ImageVector by lazy { icon("play", f("M8 5.5v13a1 1 0 0 0 1.5.9l10-6.5a1 1 0 0 0 0-1.8l-10-6.5A1 1 0 0 0 8 5.5z")) }
    val pause: ImageVector by lazy { icon("pause", f(rrect(6f, 5f, 4.2f, 14f, 1.5f)), f(rrect(13.8f, 5f, 4.2f, 14f, 1.5f))) }
    val skipPrevious: ImageVector by lazy { icon(
            "skipPrevious",
            f("M19 6.2v11.6a1 1 0 0 1-1.5.8L9 12.8a1 1 0 0 1 0-1.6l8.5-5.8a1 1 0 0 1 1.5.8z"),
            f(rrect(5f, 5f, 2.2f, 14f, 1.1f)),
        ) }
    val skipNext: ImageVector by lazy { icon(
            "skipNext",
            f("M5 6.2v11.6a1 1 0 0 0 1.5.8L15 12.8a1 1 0 0 0 0-1.6L6.5 5.4A1 1 0 0 0 5 6.2z"),
            f(rrect(16.8f, 5f, 2.2f, 14f, 1.1f)),
        ) }
    val shuffle: ImageVector by lazy { icon("shuffle", s("M16 3h5v5M4 20L21 3M21 16v5h-5M15 15l6 6M4 4l5 5")) }
    val smart: ImageVector by lazy { icon("smart", s(WAVE, 1.8f)) }
    val repeat: ImageVector by lazy { icon("repeat", s(REPEAT)) }
    val repeatOne: ImageVector by lazy { icon("repeatOne", s(REPEAT), s("M11 10.5l1.5-1v5", 1.4f)) }
    val like: ImageVector by lazy { icon("like", s(HEART)) }
    val liked: ImageVector by lazy { icon("liked", f(HEART)) }
    val queue: ImageVector by lazy { icon("queue", s("M3 6h13M3 12h13M3 18h8"), f("M17 15v6l4-3z"), autoMirror = true) }
    val playNext: ImageVector by lazy { icon("playNext", s("M10 6h11M10 12h11M4 18h17"), f("M3.5 4.5v9l6-4.5z")) }
    val addToPlaylist: ImageVector by lazy { icon("addToPlaylist", s("M3 6h12M3 12h12M3 18h7M18 14v7M14.5 17.5h7"), autoMirror = true) }
    val removeItem: ImageVector by lazy { icon("removeItem", s(circle(12f, 12f, 8f)), s("M8.5 12h7")) }
    val dragHandle: ImageVector by lazy { icon("dragHandle", s("M5 9h14M5 15h14")) }
    val album: ImageVector by lazy { icon("album", s(circle(12f, 12f, 8f)), s(circle(12f, 12f, 2.5f))) }
    val artist: ImageVector by lazy { icon("artist", s(circle(12f, 8f, 3.5f)), s("M5 20c1-4 4-6 7-6s6 2 7 6")) }
    val genre: ImageVector by lazy { icon("genre", s(rrect(4f, 4f, 7f, 7f, 2f)), s(circle(16.5f, 7.5f, 3.5f)), s("M7.5 13.5l4 6.5h-8z"), s(rrect(13f, 13f, 7f, 7f, 2f))) }
    val folder: ImageVector by lazy { icon("folder", s(FOLDER)) }
    val folderOpen: ImageVector by lazy { icon("folderOpen", s("M3 17V7a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v1M3 19l2.5-8h16L19 19z")) }
    val share: ImageVector by lazy { icon(
            "share",
            s(circle(18f, 5f, 2.5f)), s(circle(6f, 12f, 2.5f)), s(circle(18f, 19f, 2.5f)),
            s("M8.2 10.8l7.6-4.5M8.2 13.2l7.6 4.5"),
        ) }
    val edit: ImageVector by lazy { icon("edit", s("M4 20h4L19 9l-4-4L4 16z M13.5 6.5l4 4")) }
    val delete: ImageVector by lazy { icon("delete", s("M4 7h16M10 3h4M6 7l1 13h10l1-13M10 11v6M14 11v6")) }
    val export: ImageVector by lazy { icon("export", s("M12 15V4M7 9l5-5 5 5M5 15v4a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-4")) }
    val sleep: ImageVector by lazy { icon("sleep", s("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")) }
    val timer: ImageVector by lazy { icon("timer", s(circle(12f, 13f, 8f)), s("M12 9v4l2.5 2.5M9.5 2.5h5")) }
    val timerOff: ImageVector by lazy { icon("timerOff", s(circle(12f, 13f, 8f)), s("M12 9v4M9.5 2.5h5M3 3l18 18")) }
    val musicNote: ImageVector by lazy { icon("musicNote", s("M9 18V5l11-2v13"), s(circle(6.5f, 18f, 2.5f)), s(circle(17.5f, 16f, 2.5f))) }
    val lyrics: ImageVector by lazy { icon("lyrics", s("M4 5h16v11H9l-5 4z"), s("M8 9h8M8 12h5")) }
    val nowPlaying: ImageVector by lazy { icon("nowPlaying", s("M4 10v4M8 6v12M12 9v6M16 4v16M20 10v4")) }
    val device: ImageVector by lazy { icon("device", s(rrect(6f, 2f, 12f, 20f, 2f)), s("M11 18h2")) }
    val volumeUp: ImageVector by lazy { icon("volumeUp", s(SPEAKER), s("M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12"), autoMirror = true) }
    val volumeOff: ImageVector by lazy { icon("volumeOff", s(SPEAKER), s("M16 9.5l5 5M21 9.5l-5 5"), autoMirror = true) }
    val compact: ImageVector by lazy { icon("compact", s(rrect(3f, 4f, 18f, 16f, 3f)), f(rrect(11f, 11f, 7f, 6f, 1.5f))) }
    val expand: ImageVector by lazy { icon("expand", s("M14 4h6v6M20 4l-7 7M10 20H4v-6M4 20l7-7")) }
    val volumeDown: ImageVector by lazy { icon("volumeDown", s(SPEAKER), s("M16.5 8.5a5 5 0 0 1 0 7"), autoMirror = true) }
    val sort: ImageVector by lazy { icon("sort", s("M7 4v16M3 16l4 4 4-4M17 20V4M13 8l4-4 4 4")) }
    val home: ImageVector by lazy { icon("home", s(HOUSE)) }
    val homeActive: ImageVector by lazy { icon("homeActive", f(HOUSE)) }
    val library: ImageVector by lazy { icon("library", s(libraryShape), s("M9 9v6M12 8v8M15 10v4")) }
    val libraryActive: ImageVector by lazy { icon("libraryActive", s(libraryShape, 2.2f), s("M9 9v6M12 8v8M15 10v4", 2.2f)) }
    val forYou: ImageVector by lazy { icon("forYou", s(WAVE)) }
    val forYouActive: ImageVector by lazy { icon("forYouActive", s(WAVE, 2.4f)) }
    val grid: ImageVector by lazy { icon("grid", s(rrect(4f, 4f, 7f, 7f, 2.5f)), s(rrect(13f, 4f, 7f, 7f, 2.5f)), s(rrect(4f, 13f, 7f, 7f, 2.5f)), s(rrect(13f, 13f, 7f, 7f, 2.5f))) }
    val list: ImageVector by lazy { icon("list", s("M4 6h16M4 12h16M4 18h16")) }
}
