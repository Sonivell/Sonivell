package com.juscelino.sonivell.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Campo que uma regra examina. */
enum class RuleField { Genero, Artista, Album, Titulo, Ano, DuracaoMin, AdicionadaDias, Reproducoes, NuncaTocada, Curtida, Formato }

/** Comparação: texto usa Contem/NaoContem/Igual; números usam AoMenos/NoMaximo/Entre. */
enum class RuleOp { Contem, NaoContem, Igual, AoMenos, NoMaximo, Entre, Sim, Nao }

@Immutable
data class SmartRule(val field: RuleField, val op: RuleOp, val text: String = "", val a: Int = 0, val b: Int = 0)

enum class SmartSort { Aleatoria, MaisOuvidas, MenosOuvidas, Recentes, Titulo, Artista, Ano }

/**
 * Playlist inteligente: regras que se reavaliam sozinhas quando a biblioteca ou o
 * histórico mudam. [limitCount]/[limitMinutes] = 0 significa sem limite.
 */
@Immutable
data class SmartPlaylist(
    val id: Long,
    val name: String,
    val rules: List<SmartRule>,
    val matchAll: Boolean = true,
    val sort: SmartSort = SmartSort.Aleatoria,
    val limitCount: Int = 0,
    val limitMinutes: Int = 0,
)

/** Dados de escuta por faixa usados nas regras e nas playlists automáticas. */
@Immutable
data class PlayStats(val plays: Map<Long, Int>, val lastPlayed: Map<Long, Long>) {
    companion object {
        fun from(events: List<ListenEvent>): PlayStats {
            val plays = HashMap<Long, Int>()
            val last = HashMap<Long, Long>()
            events.forEach { e ->
                if (!e.skipped) plays[e.trackId] = (plays[e.trackId] ?: 0) + 1
                if (e.startedAtMs > (last[e.trackId] ?: 0L)) last[e.trackId] = e.startedAtMs
            }
            return PlayStats(plays, last)
        }
    }
}

object SmartPlaylists {
    private val DAY = TimeUnit.DAYS.toMillis(1)

    fun evaluate(
        p: SmartPlaylist,
        library: List<Track>,
        stats: PlayStats,
        favorites: Set<Long>,
        nowMs: Long = System.currentTimeMillis(),
        shuffleSeed: Long = p.id,
    ): List<Track> {
        val matching = library.filter { t ->
            if (p.rules.isEmpty()) true
            else if (p.matchAll) p.rules.all { matches(it, t, stats, favorites, nowMs) }
            else p.rules.any { matches(it, t, stats, favorites, nowMs) }
        }
        val sorted = when (p.sort) {
            // Aleatória estável: muda só quando a playlist é reaberta noutro dia.
            SmartSort.Aleatoria -> matching.sortedBy { (it.id * 2654435761L + shuffleSeed * 97 + nowMs / DAY) % 1_000_003L }
            SmartSort.MaisOuvidas -> matching.sortedByDescending { stats.plays[it.id] ?: 0 }
            SmartSort.MenosOuvidas -> matching.sortedBy { stats.plays[it.id] ?: 0 }
            SmartSort.Recentes -> matching.sortedByDescending { it.dateAdded }
            SmartSort.Titulo -> matching.sortedBy { it.title.lowercase() }
            SmartSort.Artista -> matching.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber ?: 0 }))
            SmartSort.Ano -> matching.sortedWith(compareByDescending<Track> { it.year ?: 0 }.thenBy { it.album.lowercase() })
        }
        var out = if (p.limitCount > 0) sorted.take(p.limitCount) else sorted
        if (p.limitMinutes > 0) {
            // Playlist "com duração": soma faixas até chegar perto do tempo pedido.
            var total = 0L
            val budget = p.limitMinutes * 60_000L
            out = out.takeWhile { t -> (total + t.durationMs <= budget || total == 0L).also { if (it) total += t.durationMs } }
        }
        return out
    }

    private fun matches(r: SmartRule, t: Track, stats: PlayStats, favorites: Set<Long>, nowMs: Long): Boolean {
        fun text(value: String?): Boolean {
            val v = Search.normalize(value.orEmpty())
            val q = Search.normalize(r.text)
            return when (r.op) {
                RuleOp.Contem -> v.contains(q)
                RuleOp.NaoContem -> !v.contains(q)
                RuleOp.Igual -> v == q
                else -> false
            }
        }
        fun number(value: Int?): Boolean {
            val v = value ?: return false
            return when (r.op) {
                RuleOp.AoMenos -> v >= r.a
                RuleOp.NoMaximo -> v <= r.a
                RuleOp.Entre -> v in minOf(r.a, r.b)..maxOf(r.a, r.b)
                RuleOp.Igual -> v == r.a
                else -> false
            }
        }
        return when (r.field) {
            RuleField.Genero -> text(t.genre)
            RuleField.Artista -> text(t.artist)
            RuleField.Album -> text(t.album)
            RuleField.Titulo -> text(t.title)
            RuleField.Formato -> text(t.formatLabel)
            RuleField.Ano -> number(t.year)
            RuleField.DuracaoMin -> number((t.durationMs / 60_000).toInt())
            RuleField.AdicionadaDias -> number(((nowMs - t.dateAdded * 1000) / DAY).toInt())
            RuleField.Reproducoes -> number(stats.plays[t.id] ?: 0)
            RuleField.NuncaTocada -> (t.id !in stats.lastPlayed) == (r.op != RuleOp.Nao)
            RuleField.Curtida -> (t.id in favorites) == (r.op != RuleOp.Nao)
        }
    }

    /** Playlists automáticas, prontas, que se atualizam sozinhas. */
    enum class Builtin { MaisOuvidas, MenosOuvidas, NuncaTocadas, Recentes, OuvidasRecentemente, Curtidas }

    fun builtin(kind: Builtin, library: List<Track>, stats: PlayStats, favorites: Set<Long>, nowMs: Long = System.currentTimeMillis()): List<Track> =
        when (kind) {
            Builtin.MaisOuvidas -> library.filter { (stats.plays[it.id] ?: 0) > 0 }
                .sortedByDescending { stats.plays[it.id] ?: 0 }.take(100)
            Builtin.MenosOuvidas -> library.filter { (stats.plays[it.id] ?: 0) > 0 }
                .sortedWith(compareBy<Track> { stats.plays[it.id] ?: 0 }.thenBy { stats.lastPlayed[it.id] ?: 0L }).take(100)
            Builtin.NuncaTocadas -> library.filter { it.id !in stats.lastPlayed }.sortedByDescending { it.dateAdded }
            Builtin.Recentes -> library.filter { nowMs - it.dateAdded * 1000 <= 30 * DAY }.sortedByDescending { it.dateAdded }
            Builtin.OuvidasRecentemente -> library.filter { it.id in stats.lastPlayed }
                .sortedByDescending { stats.lastPlayed[it.id] ?: 0L }.take(100)
            Builtin.Curtidas -> library.filter { it.id in favorites }
        }

    /** Décadas presentes na biblioteca (1980, 1990…), da mais recente para a mais antiga. */
    fun decades(library: List<Track>): List<Int> =
        library.mapNotNull { it.year?.takeIf { y -> y in 1900..2100 }?.let { y -> y / 10 * 10 } }.distinct().sortedDescending()

    fun decade(library: List<Track>, start: Int): List<Track> =
        library.filter { (it.year ?: -1) in start until start + 10 }.sortedWith(compareBy({ it.year ?: 0 }, { it.album.lowercase() }, { it.trackNumber ?: 0 }))
}

/** Guarda as playlists inteligentes do usuário (JSON no armazenamento do app). */
class SmartPlaylistStore(context: Context) {
    private val file = File(context.filesDir, "playlists-inteligentes.json")
    private val lock = Mutex()
    private val _items = MutableStateFlow<List<SmartPlaylist>>(emptyList())
    val items: StateFlow<List<SmartPlaylist>> = _items.asStateFlow()

    suspend fun load() = lock.withLock { _items.value = withContext(Dispatchers.IO) { read() } }

    /** Cria ou atualiza; devolve o id. */
    suspend fun save(p: SmartPlaylist): Long = lock.withLock {
        val list = _items.value
        val id = if (p.id > 0) p.id else (list.maxOfOrNull { it.id } ?: 0L) + 1
        val item = p.copy(id = id)
        val next = if (list.any { it.id == id }) list.map { if (it.id == id) item else it } else list + item
        _items.value = next
        withContext(Dispatchers.IO) { write(next) }
        id
    }

    suspend fun delete(id: Long) = lock.withLock {
        val next = _items.value.filterNot { it.id == id }
        _items.value = next
        withContext(Dispatchers.IO) { write(next) }
    }

    private fun read(): List<SmartPlaylist> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val rules = o.getJSONArray("rules")
                SmartPlaylist(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    matchAll = o.optBoolean("all", true),
                    sort = SmartSort.entries.getOrElse(o.optInt("sort")) { SmartSort.Aleatoria },
                    limitCount = o.optInt("limit"),
                    limitMinutes = o.optInt("minutes"),
                    rules = List(rules.length()) { j ->
                        val r = rules.getJSONObject(j)
                        SmartRule(
                            field = RuleField.entries.getOrElse(r.getInt("f")) { RuleField.Genero },
                            op = RuleOp.entries.getOrElse(r.getInt("o")) { RuleOp.Contem },
                            text = r.optString("t"),
                            a = r.optInt("a"),
                            b = r.optInt("b"),
                        )
                    },
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun write(list: List<SmartPlaylist>) {
        val arr = JSONArray()
        list.forEach { p ->
            val rules = JSONArray()
            p.rules.forEach { r ->
                rules.put(JSONObject().put("f", r.field.ordinal).put("o", r.op.ordinal).put("t", r.text).put("a", r.a).put("b", r.b))
            }
            arr.put(
                JSONObject().put("id", p.id).put("name", p.name).put("all", p.matchAll).put("sort", p.sort.ordinal)
                    .put("limit", p.limitCount).put("minutes", p.limitMinutes).put("rules", rules)
            )
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
