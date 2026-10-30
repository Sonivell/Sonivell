/*
 * Copyright (c) 2026 LatentJam Project
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nikitasud.latentjam.smart.chain

import kotlin.math.sqrt

/**
 * Orders already selected recommendations into smoother local journeys. Selection still belongs
 * to [SmartChain]; this pass cannot add a track, repeat one, or shorten the queue.
 *
 * Work in short windows, retaining each window's first pick and destination. This preserves the
 * immediate recommendation and the tail from which playback plans its next batch. A plan that
 * continues a walk (`from`) has no immediate recommendation to keep: its first window starts from
 * the track already playing, so the step into the plan is smoothed like any other. Squared
 * distance makes one abrupt transition more expensive than several moderate ones. Descriptor
 * similarity helps keep musical themes together, while audio supplies most of the distance.
 *
 * The 0.35 descriptor weight was selected from 0/0.35/0.65 on separate discovery seeds, then
 * checked on 180 unseen seeds in three libraries. It improves adjacent audio/descriptor cosine;
 * those are continuity proxies, not a claim that listeners always prefer the reordered journey.
 */
internal object JourneySequencer {
    private const val WINDOW_SIZE = 12
    private const val SEMANTIC_WEIGHT = 0.35f
    private const val MIN_GAIN = 1e-6f

    /**
     * @param from the row playing right before [rows] when they continue a walk (the queue's last
     *   track), never one of [rows]; null keeps every window's first pick
     * @param sameArtistCost what two neighbours by one artist add to the journey's cost, in standard
     *   deviations of the window's step costs ([ChainTuning.artistRunPenalty]); 0 leaves the order alone
     * @param companions the listener's marked playlists: two neighbours sharing one pay [sameArtistCost] only
     *   times (1 - the playlist's specificity) and save [togetherCost] times its specificity, in the same unit
     *   ([CompanionPoints.together]); null leaves the order as without marked playlists
     * @param before the row that plays right before [rows] when [from] is null (a new plan's seed); it is
     *   never moved or returned, and only counts toward the run of one artist the order may not lengthen
     *
     * With [sameArtistCost] above 0 the order never makes a run of one artist longer than the longest
     * run [rows] already had (after [from] or [before]). The selection sized its runs with the same
     * penalty, growing with each track in a row; the flat cost per same-artist neighbour here does
     * not grow, and on its own regrouped what the selection had alternated — at the default 0.5, the
     * seed's artist could come back six times in a row (SmartQueueQualityTest, EngineMathTest).
     * At 0 the order is exactly as before.
     */
    fun order(
        snapshot: SmartSnapshot,
        rows: List<Int>,
        from: Int? = null,
        sameArtistCost: Float = 0f,
        companions: CompanionMembership? = null,
        togetherCost: Float = 0f,
        before: Int? = null,
    ): List<Int> {
        val windows = rows.chunked(WINDOW_SIZE)
        if (sameArtistCost <= 0f) {
            return windows.flatMapIndexed { index, window ->
                if (index == 0 && from != null) {
                    orderWindow(snapshot, listOf(from) + window, sameArtistCost, companions, togetherCost).drop(1)
                } else {
                    orderWindow(snapshot, window, sameArtistCost, companions, togetherCost)
                }
            }
        }
        val artists = { row: Int -> snapshot.tracks[row].meta.artistKey }
        val lead = listOfNotNull(from ?: before)
        val limit = longestArtistRun(lead + rows, artists)
        val ordered = ArrayList<Int>(rows.size)
        var start = 0
        for ((index, window) in windows.withIndex()) {
            val withFrom = index == 0 && from != null
            val prefix = if (withFrom) emptyList() else lead + ordered
            val rest = rows.subList(start + window.size, rows.size)
            val route = orderWindow(
                snapshot, if (withFrom) listOf(from!!) + window else window,
                sameArtistCost, companions, togetherCost,
            ) { candidate -> longestArtistRun(prefix + candidate + rest, artists) <= limit }
            ordered += if (withFrom) route.drop(1) else route
            start += window.size
        }
        return ordered
    }

    /** The longest stretch of consecutive rows by one artist; rows without an artist never form one. */
    internal fun longestArtistRun(rows: List<Int>, artistOf: (Int) -> String): Int {
        var longest = 0
        var run = 0
        var previous = ""
        for (row in rows) {
            val artist = artistOf(row)
            run = if (artist.isNotEmpty() && artist == previous) run + 1 else if (artist.isEmpty()) 0 else 1
            previous = artist
            if (run > longest) longest = run
        }
        return longest
    }

    private fun orderWindow(
        snapshot: SmartSnapshot,
        rows: List<Int>,
        sameArtistCost: Float,
        companions: CompanionMembership?,
        togetherCost: Float,
        accepts: ((List<Int>) -> Boolean)? = null,
    ): List<Int> {
        if (rows.size < 4) return rows
        val n = rows.size
        val costs = Array(n) { FloatArray(n) }
        for (a in 0 until n) {
            for (b in 0 until a) {
                val audio = snapshot.centeredCosine(rows[a], rows[b]).coerceIn(-1f, 1f)
                val semantic = snapshot.descriptorCosine(rows[a], rows[b])
                val similarity = if (semantic == null) audio else
                    audio * (1f - SEMANTIC_WEIGHT) +
                        semantic.coerceIn(-1f, 1f) * SEMANTIC_WEIGHT
                val distance = 1f - similarity
                costs[a][b] = distance * distance
                costs[b][a] = costs[a][b]
            }
        }
        if (sameArtistCost > 0f || (companions != null && togetherCost > 0f)) {
            adjust(snapshot, rows, costs, sameArtistCost, companions, togetherCost)
        }
        val route = rows.indices.toMutableList()
        // Symmetric costs make a reversal's gain depend only on its two boundary edges.
        // Bound the work even for unusual inputs; equal-cost alternatives retain original order.
        repeat(n * 2) {
            var gain = MIN_GAIN
            var left = -1
            var right = -1
            for (i in 1 until n - 2) {
                for (j in i + 1 until n - 1) {
                    val before = costs[route[i - 1]][route[i]] + costs[route[j]][route[j + 1]]
                    val after = costs[route[i - 1]][route[j]] + costs[route[i]][route[j + 1]]
                    if (before - after > gain && (accepts == null || accepts(reversed(route, i, j).map { rows[it] }))) {
                        gain = before - after
                        left = i
                        right = j
                    }
                }
            }
            if (left < 0) return route.map { rows[it] }
            while (left < right) {
                val previous = route[left]
                route[left] = route[right]
                route[right] = previous
                left++
                right--
            }
        }
        return route.map { rows[it] }
    }

    /** [route] with positions [left]..[right] reversed, as the 2-opt move applies it. */
    private fun reversed(route: List<Int>, left: Int, right: Int): List<Int> {
        val out = route.toMutableList()
        var l = left
        var r = right
        while (l < r) {
            val previous = out[l]
            out[l] = out[r]
            out[r] = previous
            l++
            r--
        }
        return out
    }

    /** Same-artist neighbours cost more and neighbours from one marked playlist less, in spreads of the costs. */
    private fun adjust(
        snapshot: SmartSnapshot,
        rows: List<Int>,
        costs: Array<FloatArray>,
        weight: Float,
        companions: CompanionMembership?,
        togetherCost: Float,
    ) {
        val n = rows.size
        var sum = 0.0
        var squares = 0.0
        var count = 0
        for (a in 0 until n) for (b in 0 until a) {
            sum += costs[a][b]
            squares += costs[a][b].toDouble() * costs[a][b]
            count++
        }
        val mean = sum / count
        val spread = sqrt((squares / count - mean * mean).coerceAtLeast(0.0)).toFloat()
        if (spread <= 0f) return
        for (a in 0 until n) for (b in 0 until a) {
            val together = companions?.takeIf { it.sharesGroup(rows[a], rows[b]) }?.weight(rows[a], rows[b]) ?: 0f
            val artist = snapshot.tracks[rows[a]].meta.artistKey
            if (weight > 0f && artist.isNotEmpty() && artist == snapshot.tracks[rows[b]].meta.artistKey) {
                costs[a][b] += if (together == 0f) weight * spread else weight * spread * (1f - together)
            }
            if (togetherCost > 0f) costs[a][b] -= togetherCost * together * spread
            costs[b][a] = costs[a][b]
        }
    }
}
