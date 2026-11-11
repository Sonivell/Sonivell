package com.juscelino.sonivell.playback

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.juscelino.sonivell.data.ListenSessionTracker
import com.juscelino.sonivell.data.ListeningHistory
import com.juscelino.sonivell.data.SavedSession
import com.juscelino.sonivell.data.SessionStore
import com.juscelino.sonivell.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * O que precisa continuar com a tela fechada, preso ao serviço de reprodução: o temporizador,
 * o registro das escutas e o "onde parei". Antes isso vivia no ViewModel, que some quando a
 * activity fecha (voltar no Início, fechar pelos recentes) enquanto a música segue no serviço:
 * o temporizador nunca pausava e as escutas deixavam de ser registradas (auditoria REL-01).
 */
class PlaybackKeeper(
    context: Context,
    private val player: Player,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val history = ListeningHistory.get(context)
    private val tracker = ListenSessionTracker()
    private val sessionStore = SessionStore(prefs)
    private var lastSessionSaveMs = 0L

    /** Enquanto toca: acompanha a posição (para medir o que foi ouvido) e salva a sessão de tempos em tempos. */
    private val tick = object : Runnable {
        override fun run() {
            observe()
            saveSession(force = false)
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val sleepAtTime = Runnable {
        if (SleepTimerPrefs.read(prefs).endsAtMs != null) {
            player.pause()
            SleepTimerPrefs.clear(prefs)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            observe() // fecha a escuta da faixa que acabou de sair
            pauseIfArmedTrackEnded(mediaItem)
            saveSession(force = true)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            observe()
            saveSession(force = true)
            handler.removeCallbacks(tick)
            if (isPlaying) handler.postDelayed(tick, TICK_MS)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                observe()
                // A fila acabou com "ao fim desta música" ligado: a música já parou sozinha.
                if (prefs.getString(SleepTimerPrefs.KEY_TRACK, null) != null) SleepTimerPrefs.clear(prefs)
            }
        }
    }

    /** O app grava o temporizador nas preferências; aqui ele é agendado (referência forte: o Android guarda fraca). */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == SleepTimerPrefs.KEY_ENDS_AT || key == null) scheduleSleep()
    }

    fun start() {
        player.addListener(listener)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        scheduleSleep()
        if (player.isPlaying) handler.postDelayed(tick, TICK_MS)
    }

    fun release() {
        saveSession(force = true)
        handler.removeCallbacksAndMessages(null)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        player.removeListener(listener)
    }

    private fun scheduleSleep() {
        handler.removeCallbacks(sleepAtTime)
        val endsAt = SleepTimerPrefs.read(prefs).endsAtMs ?: return
        val wait = endsAt - System.currentTimeMillis()
        if (wait <= 0) sleepAtTime.run() else handler.postDelayed(sleepAtTime, wait)
    }

    /** "Ao fim desta música": a faixa marcada saiu (acabou ou foi pulada) → pausa no começo da próxima. */
    private fun pauseIfArmedTrackEnded(current: MediaItem?) {
        val armed = prefs.getString(SleepTimerPrefs.KEY_TRACK, null) ?: return
        if (current?.mediaId == armed) return
        player.pause()
        if (current != null) player.seekTo(0)
        SleepTimerPrefs.clear(prefs)
    }

    private fun observe() {
        val event = tracker.observe(
            currentId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            isPlaying = player.isPlaying,
            nowMs = System.currentTimeMillis(),
        ) ?: return
        // O app (se aberto) reage à escuta (treino do gosto, replanejar o SMART depois de um pulo).
        history.publish(event)
        if (!prefs.getBoolean(SettingsStore.KEY_HISTORY_PAUSED, false)) {
            scope.launch { history.record(event) }
        }
    }

    private fun saveSession(force: Boolean) {
        val count = player.mediaItemCount
        if (count == 0) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSessionSaveMs < SESSION_SAVE_EVERY_MS) return
        lastSessionSaveMs = now
        sessionStore.save(
            SavedSession(
                queueIds = List(count) { player.getMediaItemAt(it).mediaId.toLongOrNull() }.filterNotNull(),
                index = player.currentMediaItemIndex,
                positionMs = player.currentPosition.coerceAtLeast(0),
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            ),
        )
    }

    private companion object {
        /** Abaixo do limite de 2,5 s entre leituras do [ListenSessionTracker]. */
        const val TICK_MS = 1_000L
        const val SESSION_SAVE_EVERY_MS = 5_000L
    }
}
