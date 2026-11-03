package com.juscelino.sonivell.desktop

import java.io.File

/**
 * O Java Sound toca pela saída "default" do ALSA. Em sistemas com PipeWire ou PulseAudio, ela só
 * chega ao servidor de som (e daí ao fone Bluetooth, à saída escolhida no painel de volume etc.)
 * se a ponte ALSA estiver instalada: pipewire-alsa ou libasound2-plugins. Sem ela, o som vai
 * direto para a placa (muitas vezes a HDMI) e o app "toca" em silêncio.
 */
object AudioRoute {
    enum class Server { PipeWire, PulseAudio }

    /** O servidor de som rodando sem a ponte ALSA, ou null se está tudo certo (ou não há servidor). */
    fun missingBridge(): Server? = runCatching {
        val runtime = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() } ?: return null
        val server = when {
            File(runtime, "pipewire-0").exists() -> Server.PipeWire
            File(runtime, "pulse/native").exists() -> Server.PulseAudio
            else -> return null // ALSA puro: tocar direto na placa é o certo
        }
        if (bridged()) null else server
    }.getOrNull()

    private fun mentionsServer(text: String) = text.contains("pipewire", true) || text.contains("pulse", true)

    private fun bridged(): Boolean {
        val dirs = listOf("/usr/share/alsa/alsa.conf.d", "/etc/alsa/conf.d")
        val confs = dirs.flatMap { File(it).listFiles()?.toList().orEmpty() }
            .filter { it.name.endsWith(".conf") && mentionsServer(it.name) }
        if (confs.isNotEmpty()) return true
        val home = System.getProperty("user.home")
        return listOf(File("/etc/asound.conf"), File(home, ".asoundrc")).any { f ->
            f.isFile && runCatching { mentionsServer(f.readText()) }.getOrDefault(false)
        }
    }
}

/**
 * Capas POR FAIXA: a arte embutida no arquivo (via ffmpeg) e, se não houver, uma imagem
 * "cover/folder" na pasta. Cache de disco pela faixa (caminho + data de modificação + tamanho):
 * antes a chave era o nome do álbum, e álbuns diferentes com o mesmo nome (ou faixas sem tag)
 * dividiam a mesma capa — muitas vezes errada.
 */
object ArtLoader {
    private const val ART_TIMEOUT_S = 15L
    private const val MAX_ART_BYTES = 10 * 1024 * 1024

    private val dir get() = File(Dirs.cache, "art-v2").also { it.mkdirs() }

    fun key(path: String): String {
        val f = File(path)
        return trackIdOf(path + "\u0001" + f.lastModified() + "\u0001" + f.length()).toULong().toString(16)
    }

    fun bytesFor(path: String): ByteArray? {
        val key = key(path)
        val cached = File(dir, "$key.jpg")
        val none = File(dir, "$key.none")
        if (cached.exists()) return cached.readBytes()
        if (none.exists()) return null
        val embedded = runCatching {
            val p = ProcessBuilder(
                "ffmpeg", "-nostdin", "-loglevel", "error", "-i", path, "-an", "-map", "0:v:0", "-frames:v", "1",
                "-vf", "scale='min(1024,iw)':-2", "-f", "image2", "-c:v", "mjpeg", "-",
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            // Com prazo e tamanho máximo: a capa é uma imagem, não pode virar gigabytes.
            val data = withDeadline(p, ART_TIMEOUT_S) { p.inputStream.readNBytes(MAX_ART_BYTES + 1) }
            p.destroy()
            data.takeIf { it.size in 101..MAX_ART_BYTES }
        }.getOrNull()
        val bytes = embedded ?: File(path).parentFile?.listFiles { f ->
            f.isFile && f.nameWithoutExtension.lowercase() in setOf("cover", "folder", "front", "album", "artwork") &&
                f.extension.lowercase() in setOf("jpg", "jpeg", "png")
        }?.minByOrNull { it.name.lowercase() }?.readBytes()
        if (bytes == null) runCatching { none.createNewFile() } else runCatching { cached.writeBytes(bytes) }
        return bytes
    }
}
