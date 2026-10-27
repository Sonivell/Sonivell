package io.github.nikitasud.latentjam.smart

import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer

/** Diagnóstico: carrega o modelo de áudio e roda uma janela de sinal sintético. Devolve a norma do vetor. */
public fun smartSmokeRun(): String {
    smartTrace("smoke: start")
    val model = JvmAssets.open("ml/mnv4_audio.onnx").use { it.readBytes() }
    val session = createOrtSession(model, operators = true)
    val wave = FloatArray(320_000) { i -> 0.3f * kotlin.math.sin(i * 2.0 * Math.PI * 440.0 / 32000.0).toFloat() }
    smartTrace("smoke: tensor")
    OnnxTensor.createTensor(offlineOrtEnvironment(), FloatBuffer.wrap(wave), longArrayOf(1, 320_000)).use { t ->
        smartTrace("smoke: run")
        session.run(mapOf("waveform" to t)).use { out ->
            @Suppress("UNCHECKED_CAST")
            val v = (out[0].value as Array<FloatArray>)[0]
            smartTrace("smoke: ok")
            return "dim=${v.size} norm=${kotlin.math.sqrt(v.sumOf { (it * it).toDouble() })}"
        }
    }
}
