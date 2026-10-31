package com.juscelino.sonivell.data

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * Capas POR FAIXA, lidas do próprio arquivo.
 *
 * Antes o app usava `content://media/external/audio/albumart/<albumId>`: o Android guarda uma
 * única imagem por ALBUM_ID, tirada do primeiro arquivo que encontrou. Faixas sem tag de álbum,
 * ou álbuns diferentes com o mesmo nome, dividem o ALBUM_ID — e todas mostravam a mesma capa
 * errada. Agora cada faixa tem a sua URI:
 *
 *     content://<pacote>.art/track/<id do MediaStore>/<versão>
 *
 * A versão muda quando o arquivo muda (data de modificação + tamanho), então uma capa
 * trocada no arquivo nunca fica presa no cache (nem no do Coil, que guarda pela URI).
 *
 * Ordem de busca: arte embutida no arquivo → imagem da pasta (cover.jpg, folder.jpg…, pelo
 * próprio Android 10+) → no Android 8/9, a capa do álbum do MediaStore (o comportamento antigo).
 *
 * A mesma URI serve ao Coil (telas) e ao Media3 (notificação, tela de bloqueio).
 */
class ArtProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let {
            authority = it.packageName + ".art"
            CustomCovers.init(it)
        }
        return true
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Somente leitura")
        val ctx = context ?: throw FileNotFoundException(uri.toString())
        val segments = uri.pathSegments
        if (segments.size < 3 || segments[0] != "track") throw FileNotFoundException(uri.toString())
        val id = segments[1].toLongOrNull() ?: throw FileNotFoundException(uri.toString())
        val version = segments[2].filter { it.isLetterOrDigit() || it == '-' }.take(40)
        val albumId = uri.getQueryParameter("album")?.toLongOrNull() ?: 0L
        // Capa escolhida pela pessoa para o álbum vem antes da do arquivo.
        CustomCovers.file(albumId)?.let { return ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
        val file = ArtCache.file(ctx, id, version, albumId) ?: throw FileNotFoundException("Sem capa: $uri")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        @Volatile var authority: String = "com.juscelino.sonivell.art"
            private set

        fun uriFor(track: Track): Uri = Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendPath("track")
            .appendPath(track.id.toString())
            .appendPath(CustomCovers.version(track.albumId) ?: ArtCache.version(track.dateModified, track.sizeBytes))
            .appendQueryParameter("album", track.albumId.toString())
            .build()
    }
}

/** Cache em disco das capas por faixa: `cacheDir/art/<id>_<versão>.jpg` (ou `.none` = sem capa). */
internal object ArtCache {
    private const val TAG = "SonivellArt"
    /** Lado máximo guardado. Cobre a capa grande do player; as listas reduzem ao decodificar. */
    private const val MAX_SIDE = 1024
    private val locks = ConcurrentHashMap<String, Any>()

    fun version(dateModified: Long?, sizeBytes: Long?) = "${dateModified ?: 0}-${sizeBytes ?: 0}"

    fun file(context: Context, id: Long, version: String, albumId: Long): File? {
        val dir = File(context.cacheDir, "art").apply { mkdirs() }
        val key = "${id}_$version"
        val jpg = File(dir, "$key.jpg")
        val none = File(dir, "$key.none")
        if (jpg.exists()) return jpg
        if (none.exists()) return null
        val lock = locks.getOrPut(key) { Any() }
        try {
            synchronized(lock) {
                if (jpg.exists()) return jpg
                if (none.exists()) return null
                val bitmap = runCatching { extract(context, id, albumId) }
                    .onFailure { Log.w(TAG, "Falha ao ler a capa da faixa $id", it) }
                    .getOrNull()
                // Versões antigas desta faixa (arquivo editado) não servem mais.
                dir.listFiles { f -> f.name.startsWith("${id}_") }?.forEach { it.delete() }
                if (bitmap == null) {
                    runCatching { none.createNewFile() }
                    return null
                }
                val tmp = File(dir, "$key.tmp")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bitmap.recycle()
                return if (tmp.renameTo(jpg)) jpg else null
            }
        } finally {
            locks.remove(key)
        }
    }

    private fun extract(context: Context, id: Long, albumId: Long): Bitmap? {
        val audio = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        embedded(context, audio)?.let { return it }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Miniatura da PRÓPRIA faixa: o Android procura cover.jpg/folder.jpg na pasta do arquivo.
            runCatching { context.contentResolver.loadThumbnail(audio, Size(MAX_SIDE, MAX_SIDE), null) }.getOrNull()
        } else if (albumId > 0) {
            val legacy = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
            runCatching { context.contentResolver.openInputStream(legacy)?.use { decode(it.readBytes()) } }.getOrNull()
        } else null
    }

    /** A arte embutida no arquivo (APIC no ID3, METADATA_BLOCK_PICTURE no FLAC/Ogg, covr no MP4). */
    private fun embedded(context: Context, audio: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, audio)
            retriever.embeddedPicture?.let(::decode)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val side = maxOf(decoded.width, decoded.height)
        if (side <= MAX_SIDE) return decoded
        val scale = MAX_SIDE.toFloat() / side
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            .also { if (it !== decoded) decoded.recycle() }
    }
}

/**
 * Capas escolhidas pela pessoa para um álbum (galeria), em `filesDir/covers/<albumId>.jpg`.
 * As versões ficam num mapa observável: trocar a capa redesenha na hora as telas que a mostram.
 */
object CustomCovers {
    private var dir: File? = null
    private val versions = androidx.compose.runtime.mutableStateMapOf<Long, Long>()

    fun init(context: Context) {
        val d = File(context.filesDir, "covers").apply { mkdirs() }
        dir = d
        d.listFiles()?.forEach { f -> f.nameWithoutExtension.toLongOrNull()?.let { versions[it] = f.lastModified() } }
    }

    fun has(albumId: Long): Boolean = albumId in versions

    fun version(albumId: Long): String? = versions[albumId]?.let { "c$it" }

    fun file(albumId: Long): File? = if (albumId in versions) dir?.let { File(it, "$albumId.jpg").takeIf(File::exists) } else null

    /** Copia a imagem escolhida, reduzida a no máximo 1024 px, como capa do álbum. */
    fun set(context: Context, albumId: Long, image: Uri): Boolean = runCatching {
        val d = dir ?: File(context.filesDir, "covers").apply { mkdirs() }
        val bytes = context.contentResolver.openInputStream(image)?.use { BoundedRead.bytes(it, BoundedRead.IMAGE_MAX) } ?: return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1024 && bounds.outHeight / (sample * 2) >= 1024) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val out = File(d, "$albumId.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        versions[albumId] = out.lastModified()
        true
    }.getOrDefault(false)

    fun clear(albumId: Long) {
        dir?.let { File(it, "$albumId.jpg").delete() }
        versions.remove(albumId)
    }
}
