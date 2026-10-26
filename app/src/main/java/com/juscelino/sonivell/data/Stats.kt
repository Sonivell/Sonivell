package com.juscelino.sonivell.data

import androidx.compose.runtime.Immutable
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class StatsPeriod(val days: Int?) { Semana(7), Mes(30), Ano(365), Tudo(null) }

@Immutable
data class Ranked<T>(val item: T, val plays: Int, val listenedMs: Long)

/** Estatísticas de audição de um período, calculadas só com o histórico local. */
@Immutable
data class StatsState(
    val period: StatsPeriod = StatsPeriod.Mes,
    val listenedMs: Long = 0,
    /** Tempo ouvido no período anterior de mesmo tamanho (para comparar); null em "Tudo". */
    val previousListenedMs: Long? = null,
    val plays: Int = 0,
    val skips: Int = 0,
    val distinctTracks: Int = 0,
    /** Faixas ouvidas pela primeira vez neste período. */
    val firstListens: Int = 0,
    val topTracks: List<Ranked<Track>> = emptyList(),
    val topArtists: List<Ranked<String>> = emptyList(),
    val topAlbums: List<Ranked<Track>> = emptyList(),
    val topGenres: List<Ranked<String>> = emptyList(),
    /** Minutos ouvidos por hora do dia (0–23). */
    val byHour: List<Long> = List(24) { 0L },
    /** Minutos ouvidos por dia da semana (0 = domingo). */
    val byWeekday: List<Long> = List(7) { 0L },
) {
    val isEmpty: Boolean get() = plays == 0 && skips == 0
}

object Stats {
    private val DAY = TimeUnit.DAYS.toMillis(1)

    fun compute(
        library: List<Track>,
        events: List<ListenEvent>,
        period: StatsPeriod,
        nowMs: Long = System.currentTimeMillis(),
    ): StatsState {
        val byId = library.associateBy { it.id }
        val from = period.days?.let { nowMs - it * DAY } ?: Long.MIN_VALUE
        val inPeriod = events.filter { it.startedAtMs >= from }
        val previous = period.days?.let { d ->
            events.filter { it.startedAtMs in (from - d * DAY) until from }.sumOf { listened(it) }
        }

        val firstSeen = HashMap<Long, Long>()
        events.forEach { e -> if (e.startedAtMs < (firstSeen[e.trackId] ?: Long.MAX_VALUE)) firstSeen[e.trackId] = e.startedAtMs }

        val plays = inPeriod.filterNot { it.skipped }
        val trackCount = HashMap<Long, Int>()
        val trackTime = HashMap<Long, Long>()
        val hours = LongArray(24)
        val weekdays = LongArray(7)
        val cal = Calendar.getInstance()
        inPeriod.forEach { e ->
            val ms = listened(e)
            trackTime[e.trackId] = (trackTime[e.trackId] ?: 0L) + ms
            if (!e.skipped) trackCount[e.trackId] = (trackCount[e.trackId] ?: 0) + 1
            cal.timeInMillis = e.startedAtMs
            hours[cal.get(Calendar.HOUR_OF_DAY)] += ms
            weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1] += ms
        }

        fun <K> rank(key: (Track) -> K?): List<Ranked<K>> {
            val count = HashMap<K, Int>()
            val time = HashMap<K, Long>()
            trackCount.forEach { (id, n) -> byId[id]?.let(key)?.let { k -> count[k] = (count[k] ?: 0) + n } }
            trackTime.forEach { (id, ms) -> byId[id]?.let(key)?.let { k -> time[k] = (time[k] ?: 0L) + ms } }
            return count.entries
                .sortedWith(compareByDescending<Map.Entry<K, Int>> { it.value }.thenByDescending { time[it.key] ?: 0L })
                .take(TOP)
                .map { Ranked(it.key, it.value, time[it.key] ?: 0L) }
        }

        val topTracks = rank { it.id }.mapNotNull { r -> byId[r.item]?.let { Ranked(it, r.plays, r.listenedMs) } }
        val albumReps = HashMap<Long, Track>()
        library.forEach { albumReps.putIfAbsent(it.albumId, it) }
        val topAlbums = rank { it.albumId }.mapNotNull { r -> albumReps[r.item]?.let { Ranked(it, r.plays, r.listenedMs) } }

        return StatsState(
            period = period,
            listenedMs = inPeriod.sumOf { listened(it) },
            previousListenedMs = previous,
            plays = plays.size,
            skips = inPeriod.count { it.skipped },
            distinctTracks = inPeriod.map { it.trackId }.distinct().size,
            firstListens = firstSeen.count { (_, t) -> t >= from },
            topTracks = topTracks,
            topArtists = rank { it.artist },
            topAlbums = topAlbums,
            topGenres = rank { it.genre?.split(';', '/', ',')?.firstOrNull()?.trim()?.takeIf { g -> g.isNotEmpty() } },
            byHour = hours.map { it / 60_000 },
            byWeekday = weekdays.map { it / 60_000 },
        )
    }

    /** Tempo realmente ouvido de uma escuta: até onde chegou na faixa. */
    private fun listened(e: ListenEvent): Long =
        if (e.durationMs > 0) e.playedMs.coerceAtMost(e.durationMs) else e.playedMs

    private const val TOP = 10
}

/** O histórico inteiro como CSV (para abrir numa planilha). */
fun historyCsv(events: List<ListenEvent>, library: List<Track>): String {
    val byId = library.associateBy { it.id }
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    return buildString {
        append("inicio_ms,titulo,artista,album,ouvido_ms,duracao_ms,completa,pulada\n")
        events.forEach { e ->
            val t = byId[e.trackId]
            append(e.startedAtMs).append(',')
                .append(q(t?.title.orEmpty())).append(',')
                .append(q(t?.artist.orEmpty())).append(',')
                .append(q(t?.album.orEmpty())).append(',')
                .append(e.playedMs).append(',')
                .append(e.durationMs).append(',')
                .append(if (e.completed) 1 else 0).append(',')
                .append(if (e.skipped) 1 else 0).append('\n')
        }
    }
}
