package com.juscelino.sonivell.playback

import android.content.SharedPreferences
import com.juscelino.sonivell.SleepTimer

/**
 * O temporizador mora nas preferências do app, e não no ViewModel: quem pausa a música é o
 * serviço de reprodução ([PlaybackKeeper]), que segue vivo com a tela fechada. O app só grava o
 * pedido e mostra o estado.
 */
object SleepTimerPrefs {
    /** Hora (System.currentTimeMillis) em que a música pausa; ausente = sem temporizador por tempo. */
    const val KEY_ENDS_AT = "sleep_ends_at"

    /** mediaId da faixa que, ao acabar, pausa a música ("ao fim desta música"). */
    const val KEY_TRACK = "sleep_track"

    fun read(prefs: SharedPreferences): SleepTimer = SleepTimer(
        endsAtMs = prefs.getLong(KEY_ENDS_AT, 0L).takeIf { it > 0L },
        endOfTrack = prefs.getString(KEY_TRACK, null) != null,
    )

    fun setEndsAt(prefs: SharedPreferences, endsAtMs: Long) {
        prefs.edit().putLong(KEY_ENDS_AT, endsAtMs).remove(KEY_TRACK).apply()
    }

    fun setEndOfTrack(prefs: SharedPreferences, mediaId: String) {
        prefs.edit().remove(KEY_ENDS_AT).putString(KEY_TRACK, mediaId).apply()
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit().remove(KEY_ENDS_AT).remove(KEY_TRACK).apply()
    }
}
