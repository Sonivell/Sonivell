package com.juscelino.sonivell.system

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.juscelino.sonivell.MainActivity
import com.juscelino.sonivell.R
import com.juscelino.sonivell.playback.PlaybackService

/**
 * Bloco das Configurações Rápidas: mostra a música atual e toca/pausa com um toque.
 * O estado vem do mesmo lugar dos widgets (atualizado pelo serviço de reprodução).
 */
class PlayTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE)
        if (prefs.getString("title", null) == null) {
            openApp()
            return
        }
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            if (controller.mediaItemCount == 0) openApp()
            else if (controller.isPlaying) controller.pause() else controller.play()
            controller.release()
        }, MoreExecutors.directExecutor())
    }

    private fun render() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE)
        val title = prefs.getString("title", null)
        val playing = prefs.getBoolean("playing", false)
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = title ?: getString(R.string.widget_idle_sub)
        tile.icon = Icon.createWithResource(this, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Mesmas preferências que os widgets usam. */
        const val STATE_PREFS = "sonivell_widget"

        /** Pede ao sistema para redesenhar o bloco (o serviço chama quando a música muda). */
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, PlayTileService::class.java)) }
        }
    }
}
