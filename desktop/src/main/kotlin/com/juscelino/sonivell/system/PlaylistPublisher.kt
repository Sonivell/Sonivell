package com.juscelino.sonivell.system

import com.juscelino.sonivell.compat.Context
import com.juscelino.sonivell.data.M3u
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.desktop.Dirs
import java.io.File

/**
 * Publica as playlists do Sonivell como arquivos .m3u em Música/Playlists/Sonivell, onde outros
 * players do Linux (Rhythmbox, Elisa, Strawberry…) as encontram. Só mexe nos arquivos que ele mesmo
 * criou: apaga os de playlists que deixaram de existir.
 */
object PlaylistPublisher {
    private const val PREFS = "sonivell_published"
    private const val KEY_NAMES = "names"

    val supported: Boolean get() = true

    private val folder: File get() = File(Dirs.defaultMusicDir(), "Playlists/Sonivell")

    fun publish(context: Context, playlists: List<Playlist>, tracksOf: (Playlist) -> List<Track>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_NAMES, emptySet()).orEmpty()
        val now = mutableSetOf<String>()
        playlists.forEach { p ->
            val name = fileName(p.name)
            if (!now.add(name)) return@forEach // dois nomes iguais: fica a primeira
            runCatching {
                folder.mkdirs()
                File(folder, name).writeText(M3u.export(tracksOf(p)))
            }
        }
        (before - now).forEach { runCatching { File(folder, it).delete() } }
        prefs.edit().putStringSet(KEY_NAMES, now).apply()
    }

    /** Desligou a opção: tira tudo o que foi publicado. */
    fun unpublishAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_NAMES, emptySet()).orEmpty().forEach { runCatching { File(folder, it).delete() } }
        prefs.edit().remove(KEY_NAMES).apply()
    }

    private fun fileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Playlist" }.take(80) + ".m3u"
}
