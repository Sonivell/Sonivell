package com.juscelino.sonivell.playback

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.MusicRepository
import com.juscelino.sonivell.data.PlaylistStore
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.sortKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Árvore de navegação para o Android Auto (e outros navegadores de mídia):
 * Curtidas, Adicionadas recentemente, Playlists, Álbuns e Artistas.
 *
 * Cada faixa num item navegável leva o id da lista ("pai|faixa"), para que tocar uma faixa
 * no carro toque a lista toda a partir dela ([expand]).
 */
class LibraryBrowser(private val context: Context, private val prefs: SharedPreferences) {

    private val lock = Mutex()
    private var tracks: List<Track>? = null
    private var loadedAt = 0L
    private val playlists = PlaylistStore(context)

    private suspend fun library(): List<Track> = lock.withLock {
        val now = System.currentTimeMillis()
        tracks?.takeIf { now - loadedAt < CACHE_MS } ?: MusicRepository(context).loadTracks().also {
            tracks = it
            loadedAt = now
        }
    }

    fun root(): MediaItem = folder(ROOT, context.getString(R.string.app_name))

    suspend fun children(parentId: String): List<MediaItem> {
        val all = library()
        return when {
            parentId == ROOT -> listOf(
                folder(LIKED, context.getString(R.string.source_liked)),
                folder(RECENT, context.getString(R.string.recently_added)),
                folder(PLAYLISTS, context.getString(R.string.tab_playlists)),
                folder(ALBUMS, context.getString(R.string.tab_albums)),
                folder(ARTISTS, context.getString(R.string.tab_artists)),
            )
            parentId == PLAYLISTS -> {
                playlists.load()
                playlists.playlists.value.map { folder("$PLAYLIST_PREFIX${it.id}", it.name) }
            }
            parentId == ALBUMS -> all.groupBy { it.albumId }.values
                .sortedBy { sortKey(it.first().album) }
                .map { ts -> folder("$ALBUM_PREFIX${ts.first().albumId}", ts.first().album, ts.first().artist, ts.first().artUri) }
            parentId == ARTISTS -> all.groupBy { it.artist }.keys
                .sortedBy(::sortKey)
                .map { folder("$ARTIST_PREFIX$it", it) }
            else -> tracksOf(parentId, all).map { playable(parentId, it) }
        }
    }

    suspend fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT) return root()
        val all = library()
        val trackId = mediaId.substringAfterLast(SEP).toLongOrNull()
        if (trackId != null) all.firstOrNull { it.id == trackId }?.let { return playable(mediaId.substringBeforeLast(SEP, ""), it) }
        return children(ROOT).firstOrNull { it.mediaId == mediaId }
    }

    /**
     * Tocar uma faixa vinda do navegador: troca pela lista inteira, começando nela.
     * Devolve null quando o item não é desse tipo (o pedido segue como veio).
     */
    suspend fun expand(mediaId: String): Pair<List<MediaItem>, Int>? {
        if (SEP !in mediaId) return null
        val parent = mediaId.substringBeforeLast(SEP)
        val id = mediaId.substringAfterLast(SEP).toLongOrNull() ?: return null
        val list = tracksOf(parent, library())
        val index = list.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        return list.map(::queueItem) to index
    }

    private suspend fun tracksOf(parentId: String, all: List<Track>): List<Track> = when {
        parentId == LIKED -> {
            val favs = prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
            all.filter { it.id in favs }
        }
        parentId == RECENT -> all.sortedByDescending { it.dateAdded }.take(100)
        parentId.startsWith(PLAYLIST_PREFIX) -> {
            playlists.load()
            val pl = playlists.playlists.value.firstOrNull { it.id.toString() == parentId.removePrefix(PLAYLIST_PREFIX) }
            val byId = all.associateBy { it.id }
            pl?.trackIds?.mapNotNull(byId::get).orEmpty()
        }
        parentId.startsWith(ALBUM_PREFIX) -> all.filter { it.albumId.toString() == parentId.removePrefix(ALBUM_PREFIX) }
            .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
        parentId.startsWith(ARTIST_PREFIX) -> all.filter { it.artist == parentId.removePrefix(ARTIST_PREFIX) }
            .sortedWith(compareBy({ sortKey(it.album) }, { it.trackNumber ?: 0 }))
        else -> emptyList()
    }

    private fun folder(id: String, title: String, subtitle: String? = null, art: Uri? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(art)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            )
            .build()

    private fun playable(parentId: String, t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(if (parentId.isEmpty()) t.id.toString() else "$parentId$SEP${t.id}")
        .setUri(t.uri)
        .setMediaMetadata(metadata(t))
        .build()

    /** O item que entra na fila usa o id da faixa, como os que o app põe. */
    private fun queueItem(t: Track): MediaItem = MediaItem.Builder()
        .setMediaId(t.id.toString())
        .setUri(t.uri)
        .setMediaMetadata(metadata(t))
        .build()

    private fun metadata(t: Track) = MediaMetadata.Builder()
        .setTitle(t.title)
        .setArtist(t.artist)
        .setAlbumTitle(t.album)
        .setArtworkUri(t.artUri)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .build()

    companion object {
        const val ROOT = "root"
        private const val LIKED = "curtidas"
        private const val RECENT = "recentes"
        private const val PLAYLISTS = "playlists"
        private const val ALBUMS = "albuns"
        private const val ARTISTS = "artistas"
        private const val PLAYLIST_PREFIX = "pl:"
        private const val ALBUM_PREFIX = "al:"
        private const val ARTIST_PREFIX = "ar:"
        private const val SEP = "|"
        private const val CACHE_MS = 5 * 60_000L
    }
}
