package com.juscelino.sonivell.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Teste de ponta a ponta do SMART no PC: ffmpeg decodifica, o ONNX Runtime "ouve" e o motor monta uma fila. */
class SmartEngineTest {

    @Test
    fun analysesAndBuildsQueue() {
        if (!Scanner.toolsAvailable()) return // sem ffmpeg não há o que testar
        val dir = Files.createTempDirectory("sonivell-smart-test").toFile()
        val music = File(dir, "music").apply { mkdirs() }
        val freqs = listOf(220, 247, 262, 294, 330, 349, 392, 440)
        val tracks = freqs.mapIndexed { i, f ->
            val file = File(music, "t$i.flac")
            val p = ProcessBuilder(
                "ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=f=$f:d=40", "-c:a", "flac",
                "-metadata", "title=Faixa $i", "-metadata", "artist=Artista ${i % 3}", "-metadata", "genre=Teste", file.path,
            ).redirectErrorStream(true).start()
            p.inputStream.readBytes(); p.waitFor()
            Track(
                id = trackIdOf(file.path), path = file.path, title = "Faixa $i", artist = "Artista ${i % 3}", albumArtist = "",
                album = "Álbum", genre = "Teste", year = 2024, trackNo = i + 1, durationMs = 40_000, format = "FLAC",
                bitrateKbps = 0, addedMs = System.currentTimeMillis(),
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bridge = SmartBridge(scope, File(dir, "smart"))
        bridge.analyse(tracks)
        val done = runBlocking {
            withTimeout(240_000) {
                var p = bridge.progress.value
                while (!(p.analysed == tracks.size && !p.running) && !p.failed) {
                    kotlinx.coroutines.delay(500); p = bridge.progress.value
                }
                p
            }
        }
        println("SMART progress: $done")
        assertTrue(!done.failed, "a análise falhou: $done")
        assertTrue(done.analysed == tracks.size, "faixas analisadas: ${done.analysed} de ${tracks.size}")
        val byId = tracks.associateBy { it.id }
        val queue = runBlocking { bridge.startQueue(tracks[0], tracks.drop(1), 5, emptyList(), byId::get) }
        println("SMART queue: $queue")
        assertTrue(queue.isNotEmpty(), "o SMART devolveu uma fila vazia")
        assertTrue(queue.all { it in byId && it != tracks[0].id })
    }
}
