package com.juscelino.sonivell.data

import com.juscelino.sonivell.R
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lê as músicas do aparelho direto do MediaStore — sem banco próprio, leve e rápido. */
class MusicRepository(private val context: Context) {

    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            folderColumn,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
            }
        }.toTypedArray()

        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val folderCol = c.getColumnIndexOrThrow(folderColumn)
            val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val genreCol = c.getColumnIndex("genre")
            val albumArtistCol = c.getColumnIndex("album_artist")

            fun text(col: Int): String? =
                if (col < 0) null else c.getString(col)?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }
            fun int(col: Int): Int? = if (col < 0 || c.isNull(col)) null else c.getInt(col)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val rawFolder = c.getString(folderCol).orEmpty()
                val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    rawFolder
                } else {
                    rawFolder.substringBeforeLast('/', rawFolder)
                }
                val rawTitle = text(titleCol)
                val rawArtist = text(artistCol)
                val rawAlbum = text(albumCol)
                // MediaStore guarda disco e faixa juntos: 1003 = disco 1, faixa 3.
                val trackField = int(trackCol)
                tracks += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_title),
                    artist = c.getString(artistCol)
                        ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                        ?: context.getString(R.string.unknown_artist),
                    album = c.getString(albumCol)?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_album),
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durCol),
                    mimeType = c.getString(mimeCol),
                    folder = folder,
                    dateAdded = c.getLong(addedCol),
                    rawTitle = rawTitle,
                    rawArtist = rawArtist,
                    rawAlbum = rawAlbum,
                    albumArtist = text(albumArtistCol),
                    genre = text(genreCol),
                    year = int(yearCol)?.takeIf { it > 0 },
                    trackNumber = trackField?.rem(1000)?.takeIf { it > 0 },
                    discNumber = trackField?.div(1000)?.takeIf { it > 0 },
                    fileName = text(nameCol),
                    sizeBytes = if (c.isNull(sizeCol)) null else c.getLong(sizeCol),
                    dateModified = if (c.isNull(modifiedCol)) null else c.getLong(modifiedCol),
                )
            }
        }
        tracks
    }
}
