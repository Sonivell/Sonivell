package com.juscelino.sonivell.data

import android.content.SharedPreferences

/** O que é preciso para continuar de onde parou: fila, faixa atual, posição e modos. */
data class SavedSession(
    val queueIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    /** Player.REPEAT_MODE_* do Media3. */
    val repeatMode: Int,
)

/** Guarda a sessão de reprodução nas preferências do app (só no aparelho). */
class SessionStore(private val prefs: SharedPreferences) {

    fun save(session: SavedSession) {
        prefs.edit()
            .putString(KEY_QUEUE, session.queueIds.joinToString(","))
            .putInt(KEY_INDEX, session.index)
            .putLong(KEY_POSITION, session.positionMs)
            .putBoolean(KEY_SHUFFLE, session.shuffle)
            .putInt(KEY_REPEAT, session.repeatMode)
            .apply()
    }

    fun load(): SavedSession? {
        val ids = prefs.getString(KEY_QUEUE, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        if (ids.isEmpty()) return null
        return SavedSession(
            queueIds = ids,
            index = prefs.getInt(KEY_INDEX, 0),
            positionMs = prefs.getLong(KEY_POSITION, 0),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            repeatMode = prefs.getInt(KEY_REPEAT, 0),
        )
    }

    private companion object {
        const val KEY_QUEUE = "session_queue"
        const val KEY_INDEX = "session_index"
        const val KEY_POSITION = "session_position"
        const val KEY_SHUFFLE = "session_shuffle"
        const val KEY_REPEAT = "session_repeat"
    }
}
