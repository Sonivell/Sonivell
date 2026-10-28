package com.juscelino.sonivell.desktop

import java.io.File
import java.util.Properties

enum class ThemeMode { AUTO, LIGHT, DARK }
enum class Accent { Lima, Azul, Laranja, Lilas, Rosa, Turquesa, Verde }
enum class DarkBg { Grafite, Preto }

/** Layout das telas: Ondas (padrão) ou Essencial (enxuto, capas em destaque). */
enum class ScreenLayout { ONDAS, ESSENCIAL }

data class Settings(
    val musicDirs: List<String> = emptyList(),
    val theme: ThemeMode = ThemeMode.AUTO,
    val accent: Accent = Accent.Lima,
    val darkBg: DarkBg = DarkBg.Grafite,
    val layout: ScreenLayout = ScreenLayout.ONDAS,
    val volume: Float = 0.8f,
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val smart: Boolean = false,
    val artistVariety: Int = 2,
)

/** Ajustes do usuário, num arquivo .properties em ~/.config/sonivell. */
object SettingsStore {
    private val file get() = File(Dirs.config, "settings.properties")

    fun load(): Settings {
        val p = Properties()
        runCatching { if (file.exists()) file.reader(Charsets.UTF_8).use { p.load(it) } }
        fun <T : Enum<T>> enumOf(key: String, values: Array<T>, def: T) = values.firstOrNull { it.name == p.getProperty(key) } ?: def
        val dirs = p.getProperty("musicDirs")?.split('\u0001')?.filter { it.isNotBlank() }.orEmpty()
        return Settings(
            musicDirs = dirs.ifEmpty { listOf(Dirs.defaultMusicDir().path) },
            theme = enumOf("theme", ThemeMode.values(), ThemeMode.AUTO),
            accent = enumOf("accent", Accent.values(), Accent.Lima),
            darkBg = enumOf("darkBg", DarkBg.values(), DarkBg.Grafite),
            layout = enumOf("layout", ScreenLayout.values(), ScreenLayout.ONDAS),
            volume = p.getProperty("volume")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.8f,
            shuffle = p.getProperty("shuffle") == "true",
            repeat = p.getProperty("repeat")?.toIntOrNull()?.coerceIn(0, 2) ?: 0,
            smart = p.getProperty("smart") == "true",
            artistVariety = p.getProperty("artistVariety")?.toIntOrNull()?.coerceIn(0, 4) ?: 2,
        )
    }

    fun save(s: Settings) {
        val p = Properties()
        p.setProperty("musicDirs", s.musicDirs.joinToString("\u0001"))
        p.setProperty("theme", s.theme.name)
        p.setProperty("accent", s.accent.name)
        p.setProperty("darkBg", s.darkBg.name)
        p.setProperty("layout", s.layout.name)
        p.setProperty("volume", s.volume.toString())
        p.setProperty("shuffle", s.shuffle.toString())
        p.setProperty("repeat", s.repeat.toString())
        p.setProperty("smart", s.smart.toString())
        p.setProperty("artistVariety", s.artistVariety.toString())
        runCatching { file.writer(Charsets.UTF_8).use { p.store(it, null) } }
    }
}

/** Uma escuta: quando começou, quanto foi ouvido e se foi até o fim. */
data class Listen(val trackId: Long, val atMs: Long, val listenedMs: Long, val completed: Boolean)

/** Curtidas, histórico e "onde parei", em arquivos de texto simples em ~/.local/share/sonivell. */
class UserData {
    private val favoritesFile get() = File(Dirs.data, "favorites.txt")
    private val historyFile get() = File(Dirs.data, "history.tsv")
    private val resumeFile get() = File(Dirs.data, "resume.txt")

    var favorites: Set<Long> = runCatching {
        if (favoritesFile.exists()) favoritesFile.readLines().mapNotNull { it.trim().toLongOrNull() }.toSet() else emptySet()
    }.getOrDefault(emptySet())
        private set

    val history: MutableList<Listen> = runCatching {
        if (historyFile.exists()) historyFile.readLines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 4) null else Listen(f[0].toLong(), f[1].toLong(), f[2].toLong(), f[3] == "1")
        }.toMutableList() else mutableListOf()
    }.getOrDefault(mutableListOf())

    fun setFavorite(id: Long, on: Boolean) {
        favorites = if (on) favorites + id else favorites - id
        runCatching { favoritesFile.writeText(favorites.joinToString("\n")) }
    }

    fun record(listen: Listen) {
        if (listen.listenedMs < 3_000) return
        history.add(listen)
        runCatching { historyFile.appendText("${listen.trackId}\t${listen.atMs}\t${listen.listenedMs}\t${if (listen.completed) 1 else 0}\n") }
    }

    fun playCount(id: Long): Int = history.count { it.trackId == id && (it.completed || it.listenedMs >= 30_000) }

    fun lastPlayed(id: Long): Long = history.lastOrNull { it.trackId == id }?.atMs ?: 0L

    fun saveResume(trackId: Long, positionMs: Long) {
        runCatching { resumeFile.writeText("$trackId\n$positionMs") }
    }

    fun loadResume(): Pair<Long, Long>? = runCatching {
        val l = resumeFile.readLines()
        l[0].trim().toLong() to l[1].trim().toLong()
    }.getOrNull()
}
