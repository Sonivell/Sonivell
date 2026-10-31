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
    private lateinit var prefs: SharedPreferences
    private lateinit var effects: AudioEffects
    private var crossfader: Crossfader? = null
    private var outputs: OutputWatcher? = null
    private var keeper: PlaybackKeeper? = null
    private var smartFiller: SmartQueueFiller? = null

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
        // Temporizador, escutas e "onde parei" seguem o serviço, não a tela (funcionam com o app fechado).
        keeper = PlaybackKeeper(this, player, prefs, scope).also { it.start() }
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

        browser = LibraryBrowser(this, getSharedPreferences(SettingsStore.PREFS, MODE_PRIVATE))
        // Fila SMART "infinita" com o app fechado (com ele aberto, quem abastece é o app).
        smartFiller = SmartQueueFiller(this, player, prefs, scope, createSmartEngine(this)) { browser.tracks() }
            .also { it.start() }
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
     * Sessão com navegação (Android Auto e afins).
     * Garante também que todo item adicionado tenha URI tocável: itens vindos de
     * controladores externos podem chegar só com o mediaId.
     */
    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            if (!canBrowse(browser)) denied<MediaItem>()
            else Futures.immediateFuture(LibraryResult.ofItem(this@PlaybackService.browser.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = if (!canBrowse(browser)) denied<ImmutableList<MediaItem>>() else scope.future {
            val all = this@PlaybackService.browser.children(parentId)
            // Em Long: page e pageSize vêm do controlador; em Int, o produto podia estourar e ficar negativo.
            val from = (page.coerceAtLeast(0).toLong() * pageSize.coerceAtLeast(1)).coerceAtMost(all.size.toLong()).toInt()
            val to = (from.toLong() + pageSize.coerceAtLeast(1)).coerceAtMost(all.size.toLong()).toInt()
            LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = if (!canBrowse(browser)) denied<MediaItem>() else scope.future {
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
                MediaSession.MediaItemsWithStartPosition(resolve(mediaItems, controller), startIndex, startPositionMs)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(resolve(mediaItems, controller).toMutableList())
    }

    /**
     * Quem pode navegar pela biblioteca (que inclui curtidas e playlists): o próprio app, apps do
     * sistema e com MEDIA_CONTENT_CONTROL (isTrusted) e os de carro, relógio e assistente. Os
     * controles de tocar/pausar seguem abertos a todos, como em qualquer player; a navegação não:
     * antes qualquer app instalado, mesmo sem permissão de mídia, listava a biblioteca (auditoria SEC-02).
     */
    private fun canBrowse(controller: MediaSession.ControllerInfo): Boolean =
        controller.packageName == packageName || controller.isTrusted || controller.packageName in BROWSE_ALLOWED

    private fun <T : Any> denied(): ListenableFuture<LibraryResult<T>> =
        Futures.immediateFuture(LibraryResult.ofError<T>(SessionError.ERROR_PERMISSION_DENIED))

    /**
     * Garante uma URI tocável para cada item. Só o próprio app manda itens com URI pronta; os de
     * outros apps são sempre refeitos pelo mediaId (uma faixa do MediaStore). Antes um app qualquer
     * podia mandar o Sonivell abrir URIs arbitrárias, inclusive arquivos do próprio app.
     */
    private fun resolve(mediaItems: List<MediaItem>, controller: MediaSession.ControllerInfo): List<MediaItem> = mediaItems.mapNotNull { item ->
        when {
            item.localConfiguration != null && controller.packageName == packageName -> item
            else -> item.mediaId.substringAfterLast('|').toLongOrNull()?.let { id ->
                item.buildUpon()
                    .setMediaId(id.toString())
                    .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                    .build()
            }
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
        keeper?.release()
        keeper = null
        smartFiller?.release()
        smartFiller = null
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

        /** Android Auto, Wear OS e Google Assistente: navegam pela biblioteca mesmo sem serem do sistema. */
        private val BROWSE_ALLOWED = setOf(
            "com.google.android.projection.gearhead",
            "com.google.android.wearable.app",
            "com.google.android.googlequicksearchbox",
            "com.google.android.carassistant",
        )

        /** Sessão de áudio do player (o visualizador do app se liga nela). 0 = serviço parado. */
        @Volatile
        var currentAudioSession: Int = 0
            private set
    }
}
