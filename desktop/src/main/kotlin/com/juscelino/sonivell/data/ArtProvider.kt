package com.juscelino.sonivell.data

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.juscelino.sonivell.compat.AppContext
import com.juscelino.sonivell.compat.Context
import com.juscelino.sonivell.compat.Uri
import com.juscelino.sonivell.desktop.ArtLoader
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import javax.imageio.ImageIO

/**
 * Capas no PC: a escolhida pela pessoa (ver [CustomCovers]) e, se não houver, a da faixa (embutida
 * no arquivo ou a imagem "cover/folder" da pasta, lida pelo [ArtLoader] com cache em disco).
 */
object ArtProvider {
    private const val MEMORY_ITEMS = 300

    private val memory: MutableMap<String, ImageBitmap?> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > MEMORY_ITEMS
        },
    )

    /** Chave da capa de [track] (muda quando a capa própria do álbum muda). */
    fun keyFor(track: Track): String = track.uri.path + "\u0001" + (CustomCovers.version(track.albumId) ?: "")

    fun cached(track: Track): ImageBitmap? = memory[keyFor(track)]

    fun isCached(track: Track): Boolean = memory.containsKey(keyFor(track))

    /** Bytes da capa (JPEG/PNG), ou null se a faixa não tem. Lê do disco: chamar fora da thread da tela. */
    fun bytesFor(track: Track): ByteArray? =
        CustomCovers.file(track.albumId)?.let { runCatching { it.readBytes() }.getOrNull() }
            ?: ArtLoader.bytesFor(track.uri.path)

    /** Capa decodificada, com cache em memória. Chamar fora da thread da tela. */
    fun load(track: Track): ImageBitmap? {
        val key = keyFor(track)
        if (memory.containsKey(key)) return memory[key]
        val image = bytesFor(track)?.let(::decode)
        memory[key] = image
        return image
    }

    fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()

}

/**
 * Capas escolhidas pela pessoa para um álbum, em `files/covers/<albumId>.jpg` (o mesmo lugar do
 * app Android, para o backup servir nos dois). As versões ficam num mapa observável: trocar a capa
 * redesenha na hora as telas que a mostram.
 */
object CustomCovers {
    private val versions = mutableStateMapOf<Long, Long>()
    private val dir: File get() = File(AppContext.filesDir, "covers").apply { mkdirs() }

    init {
        dir.listFiles()?.forEach { f -> f.nameWithoutExtension.toLongOrNull()?.let { versions[it] = f.lastModified() } }
    }

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) = Unit

    fun has(albumId: Long): Boolean = albumId in versions

    fun version(albumId: Long): String? = versions[albumId]?.let { "c$it" }

    fun file(albumId: Long): File? = if (albumId in versions) File(dir, "$albumId.jpg").takeIf(File::exists) else null

    /** Copia a imagem escolhida, reduzida a no máximo 1024 px, como capa do álbum. */
    fun set(@Suppress("UNUSED_PARAMETER") context: Context, albumId: Long, image: Uri): Boolean = runCatching {
        val src = ImageIO.read(File(image.path)) ?: return false
        val side = maxOf(src.width, src.height)
        val scale = if (side > 1024) 1024.0 / side else 1.0
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            drawImage(src, 0, 0, w, h, null)
            dispose()
        }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(out, "jpg", it) }.toByteArray()
        val file = File(dir, "$albumId.jpg")
        file.writeBytes(bytes)
        versions[albumId] = file.lastModified()
        true
    }.getOrDefault(false)

    fun clear(albumId: Long) {
        File(dir, "$albumId.jpg").delete()
        versions.remove(albumId)
    }
}
