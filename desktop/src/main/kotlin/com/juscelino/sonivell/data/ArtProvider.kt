package com.juscelino.sonivell.data

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * Capas no PC: a escolhida pela pessoa (ver [CustomCovers]) e, se não houver, a da faixa (embutida
 * no arquivo ou a imagem "cover/folder" da pasta, lida pelo [ArtLoader] com cache em disco).
 */
object ArtProvider {
    // Capas pequenas (listas, fila, grades) e grandes (player, cabeçalhos). Decodificar a capa no
    // tamanho original (3000 px é comum) pesa na memória e em cada quadro desenhado.
    private const val SMALL_SIDE = 192
    private const val LARGE_SIDE = 1024
    private const val MEMORY_ITEMS = 400

    private val memory: MutableMap<String, ImageBitmap?> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > MEMORY_ITEMS
        },
    )

    // Uma leitura por álbum por vez (as faixas do mesmo álbum esperam a primeira) e no máximo duas
    // extrações do ffmpeg ao mesmo tempo: rolar uma lista grande não abre dezenas de processos.
    private val albumLocks = ConcurrentHashMap<Long, Any>()
    private val extractions = Semaphore(2)

    /** Chave da capa de [track]: uma por álbum (muda quando a capa própria do álbum muda). */
    fun keyFor(track: Track, large: Boolean = false): String =
        "a" + track.albumId + "\u0001" + (CustomCovers.version(track.albumId) ?: "") + if (large) "L" else "S"

    fun cached(track: Track, large: Boolean = false): ImageBitmap? = memory[keyFor(track, large)]

    fun isCached(track: Track, large: Boolean = false): Boolean = memory.containsKey(keyFor(track, large))

    /** Bytes da capa (JPEG/PNG), ou null se a faixa não tem. Lê do disco: chamar fora da thread da tela. */
    fun bytesFor(track: Track): ByteArray? =
        CustomCovers.file(track.albumId)?.let { runCatching { it.readBytes() }.getOrNull() }
            ?: extractions.withPermit { ArtLoader.bytesFor(track.uri.path) }

    /** Capa decodificada no tamanho certo, com cache em memória. Chamar fora da thread da tela. */
    fun load(track: Track, large: Boolean = false): ImageBitmap? {
        val key = keyFor(track, large)
        if (memory.containsKey(key)) return memory[key]
        return synchronized(albumLocks.getOrPut(track.albumId) { Any() }) {
            if (memory.containsKey(key)) return@synchronized memory[key]
            // A outra versão já sabe que não há capa: não precisa ler de novo.
            val other = keyFor(track, !large)
            if (memory.containsKey(other) && memory[other] == null) {
                memory[key] = null
                return@synchronized null
            }
            val image = bytesFor(track)?.let { decode(it, if (large) LARGE_SIDE else SMALL_SIDE) }
            memory[key] = image
            image
        }
    }

    fun decode(bytes: ByteArray): ImageBitmap? = decode(bytes, Int.MAX_VALUE)

    /** Decodifica e reduz para no máximo [maxSide] px no lado maior. */
    fun decode(bytes: ByteArray, maxSide: Int): ImageBitmap? = runCatching {
        val img = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        val side = maxOf(img.width, img.height)
        if (side <= maxSide) return@runCatching img.toComposeImageBitmap()
        val scale = maxSide.toFloat() / side
        val w = (img.width * scale).roundToInt().coerceAtLeast(1)
        val h = (img.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = org.jetbrains.skia.Bitmap()
        bitmap.allocPixels(org.jetbrains.skia.ImageInfo.makeN32Premul(w, h))
        val pixels = bitmap.peekPixels() ?: return@runCatching img.toComposeImageBitmap()
        val sampling = org.jetbrains.skia.FilterMipmap(org.jetbrains.skia.FilterMode.LINEAR, org.jetbrains.skia.MipmapMode.LINEAR)
        if (!img.scalePixels(pixels, sampling, false)) return@runCatching img.toComposeImageBitmap()
        bitmap.setImmutable()
        bitmap.asComposeImageBitmap()
    }.getOrNull()

    private inline fun <T> Semaphore.withPermit(block: () -> T): T {
        acquire()
        try { return block() } finally { release() }
    }
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
