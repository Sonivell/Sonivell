package com.juscelino.sonivell.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import com.juscelino.sonivell.data.SettingsStore
import com.juscelino.sonivell.data.TagReader
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import kotlin.math.pow
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.juscelino.sonivell.widget.SonivellWidgets
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.juscelino.sonivell.MainActivity
import com.juscelino.sonivell.R
import com.juscelino.sonivell.smart.createSmartEngine

/**
 * Serviço de reprodução: mantém o ExoPlayer vivo em segundo plano e publica a
 * MediaSession (notificação, tela de bloqueio, fones Bluetooth, Android Auto).
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var effects: AudioEffects
    private var crossfader: Crossfader? = null
    private var outputs: OutputWatcher? = null

    /** "Pausar ao desconectar o fone" muda na hora, sem reiniciar o player. */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        val player = session?.player as? ExoPlayer ?: return@OnSharedPreferenceChangeListener
        when (key) {
            SettingsStore.KEY_PAUSE_ON_UNPLUG -> player.setHandleAudioBecomingNoisy(p.getBoolean(key, true))
            SettingsStore.KEY_NORMALIZE -> applyNormalization(player)
            SettingsStore.KEY_PLAYBACK_SPEED, SettingsStore.KEY_SKIP_SILENCE -> applySpeed(player)
        }
    }

    @OptIn(UnstableApi::class) // DefaultMediaNotificationProvider
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
        crossfader = Crossfader(this, player, audioSession, prefs, createSmartEngine(this)).also { it.start() }
        prefs.registerOnSharedPreferenceChangeListener(settingsListener)
        applySpeed(player)
        outputs = OutputWatcher(getSystemService(AudioManager::class.java), prefs, player).also { it.start() }
        // A cada faixa nova, ajusta o volume pelo ReplayGain dela (se a opção estiver ligada).
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) = applyNormalization(player)
        })
        // Widgets da tela inicial acompanham a faixa e o tocar/pausar.
        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = publishWidget(player)
            override fun onIsPlayingChanged(isPlaying: Boolean) = publishWidget(player)
        })

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

        // Notificação do player com a silhueta das ondas do ícone.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )
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

    /**
     * Normalização: volume = 10^(ganho/20), sem passar de 100%. Faixas sem ReplayGain tocam
     * com −6 dB, perto do nível típico das que têm a tag, para não "saltarem" mais altas.
     */
    @OptIn(UnstableApi::class)
    private fun applyNormalization(player: ExoPlayer) {
        if (!prefs.getBoolean(SettingsStore.KEY_NORMALIZE, false)) {
            setGain(player, 1f)
            return
        }
        val metadata = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { g -> (0 until g.length).mapNotNull { g.getTrackFormat(it).metadata } }
        val gainDb = TagReader.trackGainDb(metadata) ?: UNTAGGED_GAIN_DB
        setGain(player, 10f.pow(gainDb / 20f).coerceIn(0.05f, 1f))
    }

    /** Velocidade e "pular silêncio" (ajustes de reprodução). */
    @OptIn(UnstableApi::class)
    private fun applySpeed(player: ExoPlayer) {
        player.setPlaybackSpeed(prefs.getFloat(SettingsStore.KEY_PLAYBACK_SPEED, 1f).coerceIn(0.5f, 2f))
        player.skipSilenceEnabled = prefs.getBoolean(SettingsStore.KEY_SKIP_SILENCE, false)
    }

    /** O crossfade multiplica o volume dele por cima do da normalização. */
    private fun setGain(player: ExoPlayer, gain: Float) {
        crossfader?.setMainGain(gain) ?: run { player.volume = gain }
    }

    private fun publishWidget(player: Player) {
        val md = player.mediaMetadata
        SonivellWidgets.publish(this, md.title?.toString(), md.artist?.toString(), md.artworkUri, player.isPlaying)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.player?.let { p ->
            val md = p.mediaMetadata
            SonivellWidgets.publish(this, md.title?.toString(), md.artist?.toString(), md.artworkUri, playing = false)
        }
        prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        outputs?.release()
        outputs = null
        effects.release()
        crossfader?.release()
        crossfader = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val UNTAGGED_GAIN_DB = -6f
    }
}
