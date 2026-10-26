package com.juscelino.sonivell.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    /** Ids das faixas, na ordem da playlist. Pode repetir. */
    val trackIds: List<Long>,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Playlists do usuário num arquivo JSON simples, no armazenamento do app.
 * Leve: sem banco de dados, e cabe tranquilamente milhares de faixas.
 */
class PlaylistStore(context: Context) {

    private val file = File(context.filesDir, "playlists.json")
    private val lock = Mutex()
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()
    private var loaded = false

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        _playlists.value = withContext(Dispatchers.IO) { read() }
        loaded = true
    }

    suspend fun create(name: String, trackIds: List<Long> = emptyList()): Long = edit { list ->
        val now = System.currentTimeMillis()
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1
        list + Playlist(id, name.trim(), trackIds, now, now) to id
    }

    suspend fun rename(id: Long, name: String) = update(id) { it.copy(name = name.trim()) }

    suspend fun delete(id: Long) = edit { list -> list.filterNot { it.id == id } to Unit }

    suspend fun addTracks(id: Long, trackIds: List<Long>) = update(id) { it.copy(trackIds = it.trackIds + trackIds) }

    suspend fun removeAt(id: Long, index: Int) = update(id) { p ->
        if (index !in p.trackIds.indices) p else p.copy(trackIds = p.trackIds.toMutableList().apply { removeAt(index) })
    }

    suspend fun move(id: Long, from: Int, to: Int) = update(id) { p ->
        if (from !in p.trackIds.indices || to !in p.trackIds.indices) p
        else p.copy(trackIds = p.trackIds.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Tira das playlists faixas que não existem mais no aparelho. */
    suspend fun prune(existing: Set<Long>) = edit { list ->
        list.map { p ->
            val kept = p.trackIds.filter { it in existing }
            if (kept.size == p.trackIds.size) p else p.copy(trackIds = kept)
        } to Unit
    }

    private suspend fun update(id: Long, change: (Playlist) -> Playlist) = edit { list ->
        list.map { if (it.id == id) change(it).copy(updatedAt = System.currentTimeMillis()) else it } to Unit
    }

    private suspend fun <R> edit(change: (List<Playlist>) -> Pair<List<Playlist>, R>): R = lock.withLock {
        if (!loaded) {
            _playlists.value = withContext(Dispatchers.IO) { read() }
            loaded = true
        }
        val (next, result) = change(_playlists.value)
        _playlists.value = next
        withContext(Dispatchers.IO) { write(next) }
        result
    }

    private fun read(): List<Playlist> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("tracks")
                Playlist(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    trackIds = List(ids.length()) { ids.getLong(it) },
                    createdAt = o.optLong("created"),
                    updatedAt = o.optLong("updated"),
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun write(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("created", p.createdAt)
                    .put("updated", p.updatedAt)
                    .put("tracks", JSONArray(p.trackIds))
            )
        }
        // Grava num temporário e troca: um corte de energia não corrompe as playlists.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}

/** Formato M3U estendido, entendido pela maioria dos players. */
object M3u {
    fun export(tracks: List<Track>): String = buildString {
        append("#EXTM3U\n")
        tracks.forEach { t ->
            append("#EXTINF:").append(t.durationMs / 1000).append(',')
                .append(t.artist).append(" - ").append(t.title).append('\n')
            val name = t.fileName ?: "${t.title}.mp3"
            append(t.folder.trimEnd('/').let { if (it.isEmpty()) name else "$it/$name" }).append('\n')
        }
    }

    /**
     * Lê um M3U e acha as faixas na biblioteca: primeiro pelo nome do arquivo, depois por
     * "artista - título" da linha #EXTINF. Faixas que não estão no aparelho são ignoradas.
     */
    fun import(text: String, library: List<Track>): List<Long> {
        val byFile = library.filter { it.fileName != null }.groupBy { it.fileName!!.lowercase() }
        val byTag = library.groupBy { "${it.artist} - ${it.title}".lowercase() }
        val ids = mutableListOf<Long>()
        var pendingTag: String? = null
        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingTag = line.substringAfter(',', "").trim()
                line.startsWith("#") -> Unit
                else -> {
                    val name = line.replace('\\', '/').substringAfterLast('/').lowercase()
                    val match = byFile[name]?.firstOrNull()
                        ?: pendingTag?.lowercase()?.let { byTag[it]?.firstOrNull() }
                    match?.let { ids += it.id }
                    pendingTag = null
                }
            }
        }
        return ids
    }
}
