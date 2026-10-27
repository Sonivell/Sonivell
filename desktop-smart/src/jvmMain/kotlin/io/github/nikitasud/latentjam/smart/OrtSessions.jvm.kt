package io.github.nikitasud.latentjam.smart

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.InputStream

internal fun offlineOrtEnvironment(): OrtEnvironment = OrtEnvironment.getEnvironment().also {
    it.setTelemetry(false)
}

/** Uma thread só: o ONNX Runtime não deve ocupar vários núcleos e travar a interface durante a análise. */
internal fun createOrtSession(model: ByteArray, operators: Boolean = false): OrtSession =
    OrtSession.SessionOptions().use { options ->
        options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        options.setIntraOpNumThreads(1)
        options.setInterOpNumThreads(1)
        options.setCPUArenaAllocator(false)
        if (operators) OrtOperators.register(options)
        offlineOrtEnvironment().createSession(model, options)
    }

/** Os operadores de 4 bits do LatentJam (libljq4.so), extraídos do pacote para uma pasta temporária. */
internal object OrtOperators {
    private val file: File by lazy {
        val stream = JvmAssets.openOrNull("native/linux-x86_64/libljq4.so")
            ?: error("libljq4.so não está neste pacote (só existe para Linux x86_64)")
        val out = File.createTempFile("sonivell-ljq4-", ".so").apply { deleteOnExit() }
        stream.use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    }

    fun register(options: OrtSession.SessionOptions) {
        options.registerCustomOpLibrary(file.absolutePath)
    }
}

/** Arquivos do modelo, empacotados como recursos em `ml/`. */
public object JvmAssets {
    internal fun openOrNull(name: String): InputStream? =
        JvmAssets::class.java.classLoader.getResourceAsStream(name)

    internal fun open(name: String): InputStream =
        openOrNull(name) ?: error("Recurso ausente: $name")
}

/** Onde o motor guarda os índices (por exemplo ~/.local/share/sonivell/smart). */
public class JvmSmartPaths(public val directory: File) {
    init { directory.mkdirs() }
}
