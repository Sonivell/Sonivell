package com.juscelino.sonivell.system

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.juscelino.sonivell.MainActivity
import com.juscelino.sonivell.R

/** Atalhos do ícone (segurar o ícone do app): Mix de agora, Aleatório, Curtidas e Buscar. */
object AppShortcuts {
    const val ACTION_MIX_NOW = "com.juscelino.sonivell.action.MIX_NOW"
    const val ACTION_SHUFFLE = "com.juscelino.sonivell.action.SHUFFLE"
    const val ACTION_LIKED = "com.juscelino.sonivell.action.LIKED"
    const val ACTION_SEARCH = "com.juscelino.sonivell.action.SEARCH"
    const val ACTION_CAR = "com.juscelino.sonivell.action.CAR"

    val ALL = setOf(ACTION_MIX_NOW, ACTION_SHUFFLE, ACTION_LIKED, ACTION_SEARCH, ACTION_CAR)

    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, "mix", ACTION_MIX_NOW, R.string.shortcut_mix_now, R.drawable.ic_w_play),
            shortcut(context, "shuffle", ACTION_SHUFFLE, R.string.shuffle_all, R.drawable.ic_s_shuffle),
            shortcut(context, "liked", ACTION_LIKED, R.string.source_liked, R.drawable.ic_s_heart),
            shortcut(context, "search", ACTION_SEARCH, R.string.nav_search, R.drawable.ic_s_search),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(context: Context, id: String, action: String, label: Int, icon: Int) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            .build()
}
