package com.juscelino.sonivell.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.juscelino.sonivell.data.ListeningHistory
import com.juscelino.sonivell.data.RecommendationPrefs
import com.juscelino.sonivell.data.SettingsStore
import com.juscelino.sonivell.data.TagOverrides
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.visibleLibrary
import com.juscelino.sonivell.smart.SmartEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Fila SMART "infinita" com o app fechado. Com a tela aberta, quem abastece é o app ([AppUi]);
 * fechada, o serviço continua: quando faltam poucas faixas, pede mais ao mesmo motor, com a
 * mesma biblioteca e os mesmos filtros do app. Sem o reordenamento pelo gosto aprendido, que
 * vive no app (auditoria REL-01).
 */
class SmartQueueFiller(
    private val context: Context,
    private val player: Player,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val engine: SmartEngine,
    private val tracks: suspend () -> List<Track>,
) {
    private val history = ListeningHistory.get(context)
    private var job: Job? = null

    /** Última faixa a partir da qual o motor não achou continuação: não pede de novo em loop. */
    private var exhaustedSeedId: Long? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = check()
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = check()
    }

    fun start() = player.addListener(listener)

    fun release() {
        player.removeListener(listener)
        job?.cancel()
    }

    private fun check() {
        if (!engine.isAvailable || AppUi.isOpen || job?.isActive == true) return
        if (!prefs.getBoolean(KEY_SMART_MODE, false) || player.shuffleModeEnabled) return
        val count = player.mediaItemCount
        if (count == 0 || count - player.currentMediaItemIndex - 1 >= TOP_UP_BELOW) return
        val queueIds = List(count) { player.getMediaItemAt(it).mediaId.toLongOrNull() }.filterNotNull()
        val lastId = queueIds.lastOrNull() ?: return
        if (lastId == exhaustedSeedId) return
        job = scope.launch {
            val settings = SettingsStore(prefs).settings.value
            val blocked = RecommendationPrefs(prefs)
            val library = visibleLibrary(tracks(), TagOverrides(context), settings).filterNot(blocked::isBlocked)
            val byId = library.associateBy { it.id }
            val seed = byId[lastId] ?: return@launch
            val queued = queueIds.toSet()
            val ids = runCatching {
                engine.continueQueue(
                    seed = seed,
                    library = library.filter { it.id !in queued },
                    length = PLAN_LENGTH,
                    preceding = queueIds.dropLast(1).takeLast(PLAN_LENGTH),
                    history = history.recent(HISTORY_LIMIT),
                )
            }.getOrDefault(emptyList())
            val more = ids.mapNotNull(byId::get)
            // O app pode ter aberto enquanto o motor pensava: aí quem abastece é ele.
            if (AppUi.isOpen) return@launch
            if (more.isEmpty()) {
                exhaustedSeedId = lastId
                return@launch
            }
            player.addMediaItems(more.map { it.toMediaItem() })
        }
    }

    private companion object {
        const val KEY_SMART_MODE = "smart_mode"

        // Os mesmos números do app (MainViewModel): abastece quando faltam menos de 5, planeja 25.
        const val TOP_UP_BELOW = 5
        const val PLAN_LENGTH = 25
        const val HISTORY_LIMIT = 2_000
    }
}
