package com.juscelino.sonivell.data

/**
 * A biblioteca como o app a mostra: correções de tags aplicadas, sem áudios curtos nem pastas
 * ignoradas. Uma regra só, usada pelo app e pelo serviço de reprodução (que abastece a fila
 * SMART com o app fechado), para os dois não divergirem.
 */
fun visibleLibrary(scanned: List<Track>, overrides: TagOverrides, settings: AppSettings): List<Track> {
    val minMs = settings.minDurationSec * 1000L
    return overrides.apply(scanned).filter { t -> (minMs == 0L || t.durationMs >= minMs) && t.folder !in settings.ignoredFolders }
}
