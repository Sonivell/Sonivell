package com.juscelino.sonivell.data

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val mimeType: String?,
    val folder: String,
    val dateAdded: Long,
    // Metadados extras: alimentam o motor SMART (gênero, ano, artista do álbum…).
    // "raw*" guardam a tag original; null quando o arquivo não tem a tag.
    val rawTitle: String? = null,
    val rawArtist: String? = null,
    val rawAlbum: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val fileName: String? = null,
    val sizeBytes: Long? = null,
    val dateModified: Long? = null,
) {
    /** Arte do álbum guardada pelo MediaStore (pode não existir). */
    val artUri: Uri get() = Uri.parse("content://media/external/audio/albumart/$albumId")

    /** Rótulo curto do formato, ex.: "FLAC", "MP3". */
    val formatLabel: String?
        get() = when (val mime = mimeType?.lowercase()) {
            null -> null
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/mpeg", "audio/mp3" -> "MP3"
            "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac" -> "AAC"
            "audio/ogg", "application/ogg" -> "OGG"
            "audio/opus" -> "OPUS"
            "audio/wav", "audio/x-wav" -> "WAV"
            "audio/alac" -> "ALAC"
            else -> mime.substringAfter('/').uppercase().takeIf { it.length <= 5 }
        }
}

@Immutable
data class Album(val id: Long, val title: String, val artist: String, val tracks: List<Track>)

@Immutable
data class Artist(val name: String, val tracks: List<Track>) {
    val albumCount: Int get() = tracks.map { it.albumId }.distinct().size
}

@Immutable
data class Folder(val path: String, val tracks: List<Track>) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifBlank { path }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
