package com.juscelino.sonivell.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Tags corrigidas no Sonivell para uma faixa. Campo null = mantém o que vem do arquivo. */
data class TagEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val year: Int? = null,
) {
    val isEmpty: Boolean get() = title == null && artist == null && album == null && genre == null && year == null
}

/**
 * Correções de tags guardadas pelo app (`filesDir/tag_edits.json`), aplicadas por cima do que
 * o Android leu do arquivo. Valem para a biblioteca, a busca, o SMART e as playlists; o
 * arquivo de música em si não é alterado (nenhum risco de estragar a música).
 */
class TagOverrides(context: Context) {
    private val file = File(context.filesDir, "tag_edits.json")
    private var edits: Map<Long, TagEdit> = read()

    fun get(id: Long): TagEdit? = edits[id]

    fun all(): Map<Long, TagEdit> = edits

    fun put(id: Long, edit: TagEdit) {
        edits = if (edit.isEmpty) edits - id else edits + (id to edit)
        write()
    }

    /** Aplica as correções na lista lida do aparelho. */
    fun apply(tracks: List<Track>): List<Track> {
        if (edits.isEmpty()) return tracks
        return tracks.map { t ->
            val e = edits[t.id] ?: return@map t
            t.copy(
                title = e.title ?: t.title,
                rawTitle = e.title ?: t.rawTitle,
                artist = e.artist ?: t.artist,
                rawArtist = e.artist ?: t.rawArtist,
                album = e.album ?: t.album,
                rawAlbum = e.album ?: t.rawAlbum,
                genre = e.genre ?: t.genre,
                year = e.year ?: t.year,
            )
        }
    }

    private fun read(): Map<Long, TagEdit> = runCatching {
        if (!file.exists()) return emptyMap()
        val root = JSONObject(file.readText())
        root.keys().asSequence().mapNotNull { key ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val o = root.getJSONObject(key)
            id to TagEdit(
                title = o.optString("title").ifEmpty { null },
                artist = o.optString("artist").ifEmpty { null },
                album = o.optString("album").ifEmpty { null },
                genre = o.optString("genre").ifEmpty { null },
                year = if (o.has("year")) o.optInt("year").takeIf { it > 0 } else null,
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun write() {
        val root = JSONObject()
        edits.forEach { (id, e) ->
            root.put(id.toString(), JSONObject().apply {
                e.title?.let { put("title", it) }
                e.artist?.let { put("artist", it) }
                e.album?.let { put("album", it) }
                e.genre?.let { put("genre", it) }
                e.year?.let { put("year", it) }
            })
        }
        runCatching { file.writeText(root.toString()) }
    }
}
