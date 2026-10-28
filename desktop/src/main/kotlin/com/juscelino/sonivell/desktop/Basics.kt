package com.juscelino.sonivell.desktop

import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.Properties

/** Pastas do usuário seguindo o padrão XDG (config, dados e cache). */
object Dirs {
    private val home: String = System.getProperty("user.home")

    private fun xdg(env: String, fallback: String): File {
        val base = System.getenv(env)?.takeIf { it.isNotBlank() } ?: "$home/$fallback"
        return File(base, "sonivell").also { it.mkdirs() }
    }

    val config: File by lazy { xdg("XDG_CONFIG_HOME", ".config") }
    val data: File by lazy { xdg("XDG_DATA_HOME", ".local/share") }
    val cache: File by lazy { xdg("XDG_CACHE_HOME", ".cache") }

    /** Pasta de músicas do usuário (XDG_MUSIC_DIR), ou ~/Music. */
    fun defaultMusicDir(): File {
        val file = File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.config", "user-dirs.dirs")
        val found = runCatching {
            file.readLines().firstNotNullOfOrNull { line ->
                Regex("""^XDG_MUSIC_DIR="(.*)"""").find(line.trim())?.groupValues?.get(1)
            }
        }.getOrNull()
        val path = found?.replace("\$HOME", home)
        return File(path ?: "$home/Music")
    }
}

/** Textos da interface, lidos dos mesmos strings.xml do app (convertidos em tempo de build). */
object Str {
    private fun load(name: String): Properties = Properties().also { props ->
        Str::class.java.getResourceAsStream("/$name")?.reader(Charsets.UTF_8)?.use { props.load(it) }
    }

    private val en: Properties by lazy { load("strings_en.properties") }
    private val pt: Properties by lazy { load("strings_pt.properties") }
    private val current: Properties by lazy { if (Locale.getDefault().language == "pt") pt else en }

    operator fun get(key: String): String = current.getProperty(key) ?: en.getProperty(key) ?: key

    fun format(key: String, vararg args: Any?): String =
        runCatching { String.format(Locale.getDefault(), get(key), *args) }.getOrDefault(get(key))
}

fun str(key: String): String = Str[key]
fun str(key: String, vararg args: Any?): String = Str.format(key, *args)

data class Track(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val genre: String,
    val year: Int,
    val trackNo: Int,
    val durationMs: Long,
    val format: String,
    val bitrateKbps: Int,
    val addedMs: Long,
) {
    val folder: String get() = File(path).parent ?: ""
    val albumKey: String get() = (albumArtist.ifBlank { artist } + "\u0001" + album).lowercase()
}

data class Album(val key: String, val title: String, val artist: String, val year: Int, val tracks: List<Track>)

/** Identificador estável de uma faixa a partir do caminho do arquivo (FNV-1a de 64 bits). */
fun trackIdOf(path: String): Long {
    var h = -0x340d631b7bdddcdbL
    for (b in path.toByteArray(Charsets.UTF_8)) {
        h = h xor (b.toLong() and 0xff)
        h *= 0x100000001b3L
    }
    return h and Long.MAX_VALUE
}

/** Minúsculas e sem acentos, para buscar "musica" e achar "Música". */
fun fold(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Alturas (0..1) da onda de uma faixa: a mesma fórmula do app Android. */
fun waveShape(seed: Long, bars: Int): FloatArray {
    val a = (seed % 997).toFloat() * 0.37f
    val b = (seed % 389).toFloat() * 0.11f
    return FloatArray(bars) { i ->
        kotlin.math.abs(kotlin.math.sin(i * 1.7f + a) * kotlin.math.cos(i * 0.31f + b)) * 0.85f + 0.15f
    }
}

// ---- Tags com a codificação errada ----

private val mojibakeMarker = Regex(
    "[ÂÃâ][\u0080-\u00BF\u0152\u0153\u0160\u0161\u0178\u017D\u017E\u0192\u02C6\u02DC" +
        "\u2013\u2014\u2018-\u201E\u2020-\u2022\u2026\u2030\u2039\u203A\u20AC\u2122]"
)

/** Caractere → byte do Windows-1252; os 5 bytes sem caractere voltam como eles mesmos. */
private val cp1252Bytes: Map<Char, Byte> by lazy {
    val cp1252 = java.nio.charset.Charset.forName("windows-1252")
    (0..255).associate { b ->
        val ch = String(byteArrayOf(b.toByte()), cp1252).single()
        (if (ch == '\uFFFD') b.toChar() else ch) to b.toByte()
    }
}

/**
 * Conserta tags gravadas com a codificação errada ("Whatâ€™s" → "What’s", "SÃ£o" → "São"):
 * texto UTF-8 que algum programa leu como Windows-1252. Só mexe quando há a marca típica do
 * erro e a volta para UTF-8 é válida. (Mesma lógica do TextFix do Android.)
 */
fun repairEncoding(text: String): String {
    var current = text
    repeat(2) {
        if (!mojibakeMarker.containsMatchIn(current)) return current
        val bytes = ByteArray(current.length)
        for (i in current.indices) bytes[i] = cp1252Bytes[current[i]] ?: return current
        current = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            return current
        }
    }
    return current
}
