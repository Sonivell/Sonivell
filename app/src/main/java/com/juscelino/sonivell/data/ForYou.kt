package com.juscelino.sonivell.data

import androidx.compose.runtime.Immutable
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Tela Para você: redescoberta, não descoberta — tudo aqui já é seu.
 * Inspirada no "For You" do LatentJam, calculada só com o histórico local.
 */
@Immutable
data class ForYouState(
    /** Uma sugestão de destaque: algo que você nunca ouviu, ou que não ouve há tempo. */
    val pick: Track? = null,
    val pickNeverPlayed: Boolean = false,
    /** Faixas que você começou e largou no meio (sem pular). */
    val unfinished: List<Track> = emptyList(),
    /** Curtidas que andam sumidas. */
    val forgottenFavorites: List<Track> = emptyList(),
    /** O que você costuma ouvir neste horário. */
    val thisHour: List<Track> = emptyList(),
    /** Sementes dos mixes pelo som (edição SMART): uma por artista que você ouve. */
    val mixSeeds: List<Track> = emptyList(),
) {
    val isEmpty: Boolean
        get() = unfinished.isEmpty() && forgottenFavorites.isEmpty() && thisHour.isEmpty()
}

object ForYou {
    private val DAY = TimeUnit.DAYS.toMillis(1)

    fun compute(
        library: List<Track>,
        events: List<ListenEvent>,
        favorites: Set<Long>,
        withMixes: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): ForYouState {
        if (library.isEmpty()) return ForYouState()
        val byId = library.associateBy { it.id }
        val latest = HashMap<Long, ListenEvent>()
        val completions = HashMap<Long, Int>()
        for (e in events) {
            if (e.trackId !in byId) continue
            val prev = latest[e.trackId]
            if (prev == null || e.startedAtMs >= prev.startedAtMs) latest[e.trackId] = e
            if (e.completed) completions[e.trackId] = (completions[e.trackId] ?: 0) + 1
        }

        // Ficou pela metade: a escuta mais recente parou entre 30 s e 85%, nos últimos 30 dias.
        val unfinished = latest.values
            .filter { !it.completed && !it.skipped && nowMs - it.startedAtMs < 30 * DAY }
            .sortedByDescending { it.startedAtMs }
            .mapNotNull { byId[it.trackId] }
            .take(ROW)

        // Favoritas esquecidas: curtidas sem tocar há 3 semanas (ou nunca).
        val forgotten = favorites.mapNotNull(byId::get)
            .filter { t -> latest[t.id]?.let { nowMs - it.startedAtMs > 21 * DAY } ?: true }
            .sortedBy { latest[it.id]?.startedAtMs ?: 0L }
            .take(ROW)

        // Neste horário: escutas completas a até 1 h da hora atual, nos últimos 60 dias.
        val cal = Calendar.getInstance()
        val hourNow = cal.get(Calendar.HOUR_OF_DAY)
        val hourCounts = HashMap<Long, Int>()
        for (e in events) {
            if (!e.completed || nowMs - e.startedAtMs > 60 * DAY) continue
            cal.timeInMillis = e.startedAtMs
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val distance = minOf((h - hourNow + 24) % 24, (hourNow - h + 24) % 24)
            if (distance <= 1) hourCounts[e.trackId] = (hourCounts[e.trackId] ?: 0) + 1
        }
        val thisHour = hourCounts.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .mapNotNull { byId[it.key] }
            .take(ROW)

        // Destaque do dia: estável ao longo do dia, muda no seguinte.
        val daySeed = nowMs / DAY
        val never = library.filter { it.id !in latest }
        val pick: Track?
        val pickNever: Boolean
        if (never.isNotEmpty()) {
            pick = never[(daySeed % never.size).toInt()]
            pickNever = true
        } else {
            pick = library.minByOrNull { latest[it.id]?.startedAtMs ?: 0L }
            pickNever = false
        }

        // Mixes: um por artista entre os que você mais ouve até o fim; sem histórico, artistas variados.
        val mixSeeds = if (!withMixes) emptyList() else {
            val fromHistory = completions.entries
                .sortedByDescending { it.value }
                .mapNotNull { byId[it.key] }
                .distinctBy { it.artist.lowercase() }
            val filler = library
                .sortedBy { (it.id * 2654435761L + daySeed) % 1_000_003L }
                .distinctBy { it.artist.lowercase() }
            (fromHistory + filler).distinctBy { it.artist.lowercase() }.take(MIXES)
        }

        return ForYouState(
            pick = pick,
            pickNeverPlayed = pickNever,
            unfinished = unfinished,
            forgottenFavorites = forgotten,
            thisHour = thisHour,
            mixSeeds = mixSeeds,
        )
    }

    private const val ROW = 12
    private const val MIXES = 6
}
