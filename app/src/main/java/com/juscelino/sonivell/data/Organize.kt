package com.juscelino.sonivell.data

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Um grupo de cópias da mesma gravação; [keep] é a sugestão de qual manter. */
@Immutable
data class DuplicateGroup(val key: String, val tracks: List<Track>, val keep: Track)

/** Contagem de faixas com metadados faltando. */
@Immutable
data class MetadataIssues(
    val noArtist: List<Track> = emptyList(),
    val noAlbum: List<Track> = emptyList(),
    val noGenre: List<Track> = emptyList(),
    val noYear: List<Track> = emptyList(),
    val noTitle: List<Track> = emptyList(),
) {
    val total: Int get() = (noArtist + noAlbum + noGenre + noYear + noTitle).distinctBy { it.id }.size
}

object Organize {

    /**
     * Duplicatas: mesmo artista e título (sem acentos, sem "(Remastered)" e afins) e duração
     * a até 3 s de diferença. Versões ao vivo/remix/acústica ficam de fora de propósito.
     */
    fun duplicates(library: List<Track>, ignored: Set<String>): List<DuplicateGroup> {
        val groups = library.groupBy { key(it) }
        return groups.flatMap { (key, tracks) ->
            if (tracks.size < 2 || key in ignored) return@flatMap emptyList<DuplicateGroup>()
            // Dentro do mesmo título, separa por duração parecida.
            val clusters = mutableListOf<MutableList<Track>>()
            tracks.sortedBy { it.durationMs }.forEach { t ->
                val last = clusters.lastOrNull()
                if (last != null && abs(last.last().durationMs - t.durationMs) <= 3_000) last += t else clusters += mutableListOf(t)
            }
            clusters.filter { it.size >= 2 }.map { DuplicateGroup(key, it, best(it)) }
        }.sortedBy { it.keep.artist.lowercase() }
    }

    /** Qual cópia manter: formato sem perdas, depois o arquivo maior, depois o mais antigo na biblioteca. */
    private fun best(copies: List<Track>): Track = copies.maxWith(
        compareBy<Track>({ formatRank(it.formatLabel) }, { it.sizeBytes ?: 0L }, { -it.dateAdded })
    )

    private fun formatRank(f: String?) = when (f) {
        "FLAC", "ALAC", "WAV" -> 3
        "OPUS", "AAC", "OGG" -> 2
        "MP3" -> 1
        else -> 0
    }

    private val NOISE = Regex("""\s*[(\[](?:\d{4}\s*)?(?:remaster(?:ed)?|remasterizad[ao]|version|versão|explicit|clean|deluxe|mono|stereo)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
    private val KEEP_APART = Regex("""\b(live|ao vivo|remix|acoustic|acústic[ao]|instrumental|demo|edit)\b""", RegexOption.IGNORE_CASE)

    fun key(t: Track): String {
        val title = Search.normalize(t.title.replace(NOISE, ""))
        // Versões diferentes (ao vivo, remix…) ganham uma marca para não se juntarem à original.
        val variant = KEEP_APART.find(t.title)?.value?.lowercase().orEmpty()
        return Search.normalize(t.artist) + "|" + title + "|" + variant
    }

    fun issues(library: List<Track>) = MetadataIssues(
        noArtist = library.filter { it.rawArtist == null },
        noAlbum = library.filter { it.rawAlbum == null },
        // Sem gênero ou com um gênero que não diz nada ("Genre:", "Music", "Other"…).
        noGenre = library.filter { it.genre == null || isGenericGenre(it.genre) },
        noYear = library.filter { it.year == null },
        noTitle = library.filter { it.rawTitle == null },
    )
}

/**
 * Preferências de recomendação: faixas e artistas que o SMART e o Para você não devem
 * sugerir, e grupos marcados como "não são duplicatas".
 */
class RecommendationPrefs(private val prefs: SharedPreferences) {

    private val _blockedTracks = MutableStateFlow(prefs.getStringSet(KEY_TRACKS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet())
    val blockedTracks: StateFlow<Set<Long>> = _blockedTracks.asStateFlow()

    private val _blockedArtists = MutableStateFlow(prefs.getStringSet(KEY_ARTISTS, emptySet()).orEmpty())
    val blockedArtists: StateFlow<Set<String>> = _blockedArtists.asStateFlow()

    private val _notDuplicates = MutableStateFlow(prefs.getStringSet(KEY_NOT_DUP, emptySet()).orEmpty())
    val notDuplicates: StateFlow<Set<String>> = _notDuplicates.asStateFlow()

    fun blockTrack(id: Long, blocked: Boolean) {
        _blockedTracks.value = if (blocked) _blockedTracks.value + id else _blockedTracks.value - id
        prefs.edit().putStringSet(KEY_TRACKS, _blockedTracks.value.map { it.toString() }.toSet()).apply()
    }

    fun blockArtist(name: String, blocked: Boolean) {
        _blockedArtists.value = if (blocked) _blockedArtists.value + name else _blockedArtists.value - name
        prefs.edit().putStringSet(KEY_ARTISTS, _blockedArtists.value).apply()
    }

    fun markNotDuplicate(key: String) {
        _notDuplicates.value = _notDuplicates.value + key
        prefs.edit().putStringSet(KEY_NOT_DUP, _notDuplicates.value).apply()
    }

    fun clearNotDuplicates() {
        _notDuplicates.value = emptySet()
        prefs.edit().remove(KEY_NOT_DUP).apply()
    }

    fun isBlocked(t: Track) = t.id in _blockedTracks.value || t.artist in _blockedArtists.value

    private companion object {
        const val KEY_TRACKS = "blocked_tracks"
        const val KEY_ARTISTS = "blocked_artists"
        const val KEY_NOT_DUP = "not_duplicates"
    }
}

private val GENERIC_GENRES = setOf(
    "genre", "genres", "music", "musica", "música", "other", "others", "outro", "outros",
    "unknown", "desconhecido", "misc", "miscellaneous", "various", "none", "n/a", "default",
)

/** Gênero que não diz nada ("Genre:", "Music", "Other"…): conta como gênero para arrumar. */
fun isGenericGenre(name: String): Boolean {
    val n = name.trim().trimEnd(':', '.', ' ').lowercase()
    return n.isEmpty() || n in GENERIC_GENRES
}
