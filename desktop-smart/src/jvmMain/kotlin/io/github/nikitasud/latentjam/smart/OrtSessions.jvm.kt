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
        smartTrace("createOrtSession: ${model.size} bytes, operators=$operators")
        options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
        options.setIntraOpNumThreads(1)
        options.setInterOpNumThreads(1)
        // Variantes só para diagnóstico (SONIVELL_ORT_VARIANT); o padrão é o mesmo do Android.
        val variant = System.getenv("SONIVELL_ORT_VARIANT").orEmpty()
        if (variant != "arena") options.setCPUArenaAllocator(false)
        if (variant == "noopt") options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        if (variant == "nopattern") options.setMemoryPatternOptimization(false)
        if (variant == "threads") { options.setIntraOpNumThreads(0); options.setInterOpNumThreads(0) }
        if (operators) OrtOperators.register(options)
        smartTrace("createSession...")
        offlineOrtEnvironment().createSession(model, options).also { smartTrace("createSession ok") }
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

    /**
     * O ONNX Runtime faz dlopen da biblioteca e dlclose quando a SessionOptions é fechada. Sem esta
     * carga pela JVM (o System.loadLibrary do Android faz o mesmo papel), o operador sumiria da memória
     * e a primeira inferência leria memória já liberada.
     */
    private val pinned: Boolean by lazy { System.load(file.absolutePath); true }

    fun register(options: OrtSession.SessionOptions) {
        check(pinned)
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

/** Diagnóstico: com SONIVELL_SMART_TRACE=arquivo, cada etapa nativa é anotada (e gravada) antes de rodar. */
internal fun smartTrace(message: String) {
    val path = System.getenv("SONIVELL_SMART_TRACE") ?: return
    runCatching { java.io.FileOutputStream(path, true).use { it.write((message + "\n").toByteArray()); it.fd.sync() } }
}
