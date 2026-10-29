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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import com.juscelino.sonivell.MainActivity
import com.juscelino.sonivell.R
import com.juscelino.sonivell.smart.createSmartEngine

/**
 * Serviço de reprodução: mantém o ExoPlayer vivo em segundo plano e publica a
 * MediaSession (notificação, tela de bloqueio, fones Bluetooth, Android Auto).
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private lateinit var browser: LibraryBrowser
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    /** Multiplicador do volume durante o despertador (sobe de ~0 a 1). */
    private var rampFactor = 1f
    private var baseGain = 1f
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
        currentAudioSession = audioSession
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
        // Pausou o despertador: o som volta a ser de música.
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && alarmAudio) useAlarmAudio(player, false)
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        browser = LibraryBrowser(this, getSharedPreferences(SettingsStore.PREFS, MODE_PRIVATE))
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)
            .build()

        // Notificação do player com a silhueta das ondas do ícone.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )
    }

    /**
     * Sessão com navegação (Android Auto e afins) e o comando do despertador.
     * Garante também que todo item adicionado tenha URI tocável: itens vindos de
     * controladores externos podem chegar só com o mediaId.
     */
    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(ALARM_COMMAND, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != ALARM_COMMAND) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            val rampSec = args.getInt(EXTRA_RAMP_SEC, 0)
            val stopMin = args.getInt(EXTRA_STOP_MIN, 0)
            val config = com.juscelino.sonivell.system.AlarmConfig(
                source = com.juscelino.sonivell.system.AlarmSource.entries.firstOrNull { it.name == args.getString(EXTRA_SOURCE) }
                    ?: com.juscelino.sonivell.system.AlarmSource.Curtidas,
                playlistId = args.getLong(EXTRA_PLAYLIST, 0L),
            )
            return scope.future {
                val ids = args.getLongArray(EXTRA_IDS)?.toList()
                    ?: com.juscelino.sonivell.system.MusicAlarm.trackIds(this@PlaybackService, config)
                val items = browser.queueItems(ids)
                val player = session.player
                if (items.isEmpty() && player.mediaItemCount == 0) return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                if (items.isNotEmpty()) player.setMediaItems(items, 0, 0L)
                (player as? ExoPlayer)?.let { useAlarmAudio(it, true) }
                player.prepare()
                startAlarmRamp(rampSec)
                player.play()
                handler.removeCallbacks(alarmStop)
                if (stopMin > 0) handler.postDelayed(alarmStop, stopMin * 60_000L)
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(this@PlaybackService.browser.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val all = this@PlaybackService.browser.children(parentId)
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            this@PlaybackService.browser.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        /** Tocar uma faixa pelo carro toca a lista dela inteira, a partir dali. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val single = mediaItems.singleOrNull()
            val expanded = single?.let { browser.expand(it.mediaId) }
            if (expanded != null) {
                MediaSession.MediaItemsWithStartPosition(expanded.first, expanded.second, C.TIME_UNSET)
            } else {
                MediaSession.MediaItemsWithStartPosition(resolve(mediaItems), startIndex, startPositionMs)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(resolve(mediaItems).toMutableList())
    }

    private fun resolve(mediaItems: List<MediaItem>): List<MediaItem> = mediaItems.mapNotNull { item ->
        when {
            item.localConfiguration != null -> item
            else -> item.mediaId.substringAfterLast('|').toLongOrNull()?.let { id ->
                item.buildUpon()
                    .setMediaId(id.toString())
                    .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                    .build()
            }
        }
    }

    // ---- Despertador: o volume sobe aos poucos e a música para sozinha depois de um tempo ----

    private val alarmStop = Runnable { session?.player?.pause() }

    /**
     * Enquanto o despertador toca, o som sai como alarme: toca mesmo com o "Não perturbe"
     * ligado e usa o volume de alarme. Ao pausar, volta a ser música normal.
     */
    private var alarmAudio = false

    private fun useAlarmAudio(player: ExoPlayer, on: Boolean) {
        if (alarmAudio == on) return
        alarmAudio = on
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (on) C.USAGE_ALARM else C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
    }
    private var rampStep: Runnable? = null

    private fun startAlarmRamp(seconds: Int) {
        rampStep?.let(handler::removeCallbacks)
        val player = session?.player as? ExoPlayer ?: return
        if (seconds <= 0) {
            rampFactor = 1f
            setGain(player, baseGain)
            return
        }
        val steps = (seconds * 1000 / RAMP_TICK_MS).toInt().coerceAtLeast(1)
        var step = 0
        rampFactor = RAMP_START
        setGain(player, baseGain)
        val tick = object : Runnable {
            override fun run() {
                step++
                rampFactor = (RAMP_START + (1f - RAMP_START) * step / steps).coerceAtMost(1f)
                setGain(player, baseGain)
                if (step < steps) handler.postDelayed(this, RAMP_TICK_MS)
            }
        }
        rampStep = tick
        handler.postDelayed(tick, RAMP_TICK_MS)
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
        baseGain = gain
        val actual = gain * rampFactor
        crossfader?.setMainGain(actual) ?: run { player.volume = actual }
    }

    private fun publishWidget(player: Player) {
        val md = player.mediaMetadata
        SonivellWidgets.publish(this, md.title?.toString(), md.artist?.toString(), md.artworkUri, player.isPlaying)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

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
        currentAudioSession = 0
        prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        outputs?.release()
        outputs = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
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

    companion object {
        private const val UNTAGGED_GAIN_DB = -6f
        private const val RAMP_TICK_MS = 500L
        private const val RAMP_START = 0.05f

        /** Comando do despertador: toca [EXTRA_IDS] com o volume subindo em [EXTRA_RAMP_SEC]. */
        const val ALARM_COMMAND = "com.juscelino.sonivell.ALARM"
        const val EXTRA_IDS = "ids"
        const val EXTRA_RAMP_SEC = "ramp_sec"
        const val EXTRA_STOP_MIN = "stop_min"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_PLAYLIST = "playlist"

        /** Sessão de áudio do player (o visualizador do app se liga nela). 0 = serviço parado. */
        @Volatile
        var currentAudioSession: Int = 0
            private set
    }
}
