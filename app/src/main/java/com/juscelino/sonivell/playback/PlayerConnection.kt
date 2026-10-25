package com.juscelino.sonivell.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.juscelino.sonivell.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

@Immutable
data class PlayerState(
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
) {
    val current: MediaItem? get() = queue.getOrNull(currentIndex)
    val hasMedia: Boolean get() = current != null
    val upNext: List<IndexedValue<MediaItem>>
        get() = queue.withIndex().filter { it.index > currentIndex }
}

/** Ponte entre a UI e o [PlaybackService] via MediaController. */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var progressJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
            if (player.isPlaying) startProgress() else stopProgress()
        }
    }

    fun connect() {
        if (controller != null) return
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = MediaController.Builder(context, token).buildAsync().await()
            controller = c
            c.addListener(listener)
            publish(c)
            if (c.isPlaying) startProgress()
        }
    }

    fun release() {
        stopProgress()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    // ---- Ações ----

    fun play(tracks: List<Track>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceAtLeast(0), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() = controller?.seekToNextMediaItem()

    /** Como nos players comuns: volta ao início se já passou de 3 s. */
    fun previous() = controller?.seekToPrevious()

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun moveItem(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
    }

    fun removeItem(index: Int) {
        controller?.removeMediaItem(index)
    }

    /** "Limpar": remove tudo que vem depois da faixa atual. */
    fun clearUpNext() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
    }

    fun addNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(track), 0)
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    // ---- Estado ----

    private fun publish(p: Player) {
        val items = List(p.mediaItemCount) { p.getMediaItemAt(it) }
        _state.value = PlayerState(
            queue = items,
            currentIndex = if (items.isEmpty()) -1 else p.currentMediaItemIndex,
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = p.duration.takeIf { it > 0 } ?: 0,
            shuffle = p.shuffleModeEnabled,
            repeat = when (p.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
        )
    }

    private fun startProgress() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _state.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0),
                            durationMs = c.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
    }
}

// ---- Conversão Track <-> MediaItem ----

private const val EXTRA_ALBUM_ID = "albumId"
private const val EXTRA_MIME = "mime"
private const val EXTRA_DURATION = "duration"

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(Bundle().apply {
                putLong(EXTRA_ALBUM_ID, albumId)
                putString(EXTRA_MIME, mimeType)
                putLong(EXTRA_DURATION, durationMs)
            })
            .build()
    )
    .build()

/** Reconstrói uma Track a partir do item da fila (o MediaController preserva os metadados). */
fun MediaItem.toTrack(): Track {
    val md = mediaMetadata
    val extras = md.extras
    return Track(
        id = mediaId.toLongOrNull() ?: 0L,
        uri = localConfiguration?.uri ?: "".toUri(),
        title = md.title?.toString() ?: "Sem título",
        artist = md.artist?.toString() ?: "Artista desconhecido",
        album = md.albumTitle?.toString() ?: "",
        albumId = extras?.getLong(EXTRA_ALBUM_ID) ?: 0L,
        durationMs = extras?.getLong(EXTRA_DURATION) ?: 0L,
        mimeType = extras?.getString(EXTRA_MIME),
        folder = "",
        dateAdded = 0L,
    )
}
