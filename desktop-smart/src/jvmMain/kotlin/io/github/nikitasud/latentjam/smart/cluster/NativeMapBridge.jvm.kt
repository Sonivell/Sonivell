package io.github.nikitasud.latentjam.smart.cluster

/** No PC, o mapa 2D da biblioteca (biblioteca nativa libljmap) não é usado: 3 = "indisponível". */
internal actual fun nativeMapEmbed(
    rows: FloatArray, n: Int, dim: Int, initial: FloatArray?, output: FloatArray, cancellation: MapCancellation,
): Int = 3

