package com.juscelino.sonivell.system

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.juscelino.sonivell.data.M3u
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.Track

/**
 * Publica as playlists do Sonivell como arquivos .m3u em Música/Playlists/Sonivell, onde o
 * Android e outros apps (carro, relógio, outros players) as encontram. Só mexe nos arquivos
 * que ele mesmo criou: apaga os de playlists que deixaram de existir.
 */
object PlaylistPublisher {
    private const val PREFS = "sonivell_published"
    private const val KEY_NAMES = "names"
    private val RELATIVE_PATH = "Music/Playlists/Sonivell/"

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun publish(context: Context, playlists: List<Playlist>, tracksOf: (Playlist) -> List<Track>) {
        if (!supported) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY_NAMES, emptySet()).orEmpty()
        val now = mutableSetOf<String>()
        playlists.forEach { p ->
            val name = fileName(p.name)
            if (!now.add(name)) return@forEach // dois nomes iguais: fica a primeira
            runCatching { write(context, name, M3u.export(tracksOf(p))) }
        }
        (before - now).forEach { runCatching { delete(context, it) } }
        prefs.edit().putStringSet(KEY_NAMES, now).apply()
    }

    /** Desligou a opção: tira do sistema tudo o que foi publicado. */
    fun unpublishAll(context: Context) {
        if (!supported) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_NAMES, emptySet()).orEmpty().forEach { runCatching { delete(context, it) } }
        prefs.edit().remove(KEY_NAMES).apply()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun write(context: Context, name: String, text: String) {
        val resolver = context.contentResolver
        val uri = find(context, name) ?: resolver.insert(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-mpegurl")
                put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            },
        ) ?: return
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun delete(context: Context, name: String) {
        find(context, name)?.let { context.contentResolver.delete(it, null, null) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun find(context: Context, name: String): Uri? {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(name, RELATIVE_PATH),
            null,
        )?.use { c -> if (c.moveToFirst()) return android.content.ContentUris.withAppendedId(collection, c.getLong(0)) }
        return null
    }

    private fun fileName(playlist: String): String =
        playlist.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Playlist" }.take(80) + ".m3u"
}
