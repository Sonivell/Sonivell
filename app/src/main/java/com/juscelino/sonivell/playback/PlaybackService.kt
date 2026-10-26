package com.juscelino.sonivell.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import com.juscelino.sonivell.data.SettingsStore
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.juscelino.sonivell.MainActivity

/**
 * Serviço de reprodução: mantém o ExoPlayer vivo em segundo plano e publica a
 * MediaSession (notificação, tela de bloqueio, fones Bluetooth, Android Auto).
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var effects: AudioEffects

    /** "Pausar ao desconectar o fone" muda na hora, sem reiniciar o player. */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == SettingsStore.KEY_PAUSE_ON_UNPLUG) {
            (session?.player as? ExoPlayer)?.setHandleAudioBecomingNoisy(p.getBoolean(key, true))
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(SettingsStore.PREFS, MODE_PRIVATE)
        effects = AudioEffects(prefs)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(prefs.getBoolean(SettingsStore.KEY_PAUSE_ON_UNPLUG, true))
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Sessão de áudio fixa: o equalizador se prende a ela uma vez só.
        val audioSession = getSystemService(AudioManager::class.java).generateAudioSessionId()
        player.setAudioSessionId(audioSession)
        effects.attach(audioSession)
        prefs.registerOnSharedPreferenceChangeListener(settingsListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    /**
     * Garante que todo item adicionado tenha URI tocável. Itens vindos de
     * controladores externos (ex.: Android Auto) podem chegar só com o mediaId.
     */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { item ->
                when {
                    item.localConfiguration != null -> item
                    else -> item.mediaId.toLongOrNull()?.let { id ->
                        item.buildUpon()
                            .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                            .build()
                    }
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        effects.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
