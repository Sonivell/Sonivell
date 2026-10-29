@file:Suppress("FunctionName") // os nomes dos métodos D-Bus são os da especificação MPRIS

package com.juscelino.sonivell.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.io.File
import javax.swing.SwingUtilities

/** org.mpris.MediaPlayer2: a raiz (mostrar a janela). */
@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

/** org.mpris.MediaPlayer2.Player: os controles. */
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MprisPlayer : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)
}

/**
 * Integração com o sistema (MPRIS, pelo D-Bus da sessão): as teclas de mídia, o controle de
 * música do painel (KDE, GNOME…), a tela de bloqueio e os fones Bluetooth passam a ver e
 * controlar o Sonivell, mesmo com a janela minimizada. Sem D-Bus (ou sem sessão gráfica),
 * simplesmente não liga — o resto do app segue igual.
 */
class Mpris(private val ctl: Controller) : MprisRoot, MprisPlayer, Properties {

    /** Traz a janela para a frente (Raise); ligado pelo Main.kt. */
    @Volatile var onRaise: (() -> Unit)? = null

    private var connection: DBusConnection? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        val conn = runCatching { DBusConnectionBuilder.forSessionBus().build() }.getOrNull() ?: return
        runCatching {
            conn.requestBusName(BUS_NAME)
            conn.exportObject(PATH, this)
        }.onFailure {
            runCatching { conn.close() }
            return
        }
        connection = conn
        // Avisa o sistema quando a faixa, o tocar/pausar ou o volume mudam.
        scope.launch {
            ctl.player
                .map { Triple(it.current?.id, it.playing, it.volume) }
                .distinctUntilChanged()
                .collect { emitPlayerChanged() }
        }
    }

    fun stop() {
        scope.cancel()
        connection?.let { c -> runCatching { c.releaseBusName(BUS_NAME) }; runCatching { c.close() } }
        connection = null
    }

    override fun getObjectPath(): String = PATH
    override fun isRemote(): Boolean = false

    // ---- org.mpris.MediaPlayer2 ----

    override fun Raise() = onUi { onRaise?.invoke() }
    override fun Quit() = Unit // CanQuit = false: fechar é pela janela

    // ---- org.mpris.MediaPlayer2.Player ----

    override fun Next() = onUi { ctl.next() }
    override fun Previous() = onUi { ctl.previous() }
    override fun Pause() = onUi { if (ctl.player.value.playing) ctl.togglePlay() }
    override fun PlayPause() = onUi { ctl.togglePlay() }
    override fun Stop() = Pause()
    override fun Play() = onUi { if (!ctl.player.value.playing) ctl.togglePlay() }
    override fun Seek(offset: Long) = onUi { ctl.seek(ctl.player.value.positionMs + offset / 1000) }
    override fun SetPosition(trackId: DBusPath, position: Long) = onUi {
        val current = ctl.player.value.current ?: return@onUi
        if (trackId.path == trackPath(current)) ctl.seek(position / 1000)
    }
    override fun OpenUri(uri: String) = Unit

    // ---- org.freedesktop.DBus.Properties ----

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
        (GetAll(interfaceName)[propertyName]?.value) as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) {
        if (interfaceName == PLAYER_IFACE && propertyName == "Volume") {
            val v = (value as? Variant<*>)?.value ?: value
            (v as? Number)?.toFloat()?.let { vol -> onUi { ctl.setVolume(vol.coerceIn(0f, 1f)) } }
        }
    }

    override fun GetAll(interfaceName: String): MutableMap<String, Variant<*>> = when (interfaceName) {
        ROOT_IFACE -> linkedMapOf<String, Variant<*>>(
            "CanQuit" to Variant(false),
            "CanRaise" to Variant(true),
            "HasTrackList" to Variant(false),
            "Identity" to Variant("Sonivell"),
            "DesktopEntry" to Variant("sonivell"),
            "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
            "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
        )
        PLAYER_IFACE -> playerProperties()
        else -> linkedMapOf<String, Variant<*>>()
    }

    private fun playerProperties(): MutableMap<String, Variant<*>> {
        val s = ctl.player.value
        val hasTrack = s.current != null
        return linkedMapOf<String, Variant<*>>(
            "PlaybackStatus" to Variant(if (!hasTrack) "Stopped" else if (s.playing) "Playing" else "Paused"),
            "LoopStatus" to Variant(when (s.repeat) { Repeat.Off -> "None"; Repeat.All -> "Playlist"; Repeat.One -> "Track" }),
            "Rate" to Variant(1.0),
            "Shuffle" to Variant(s.shuffle),
            "Metadata" to Variant(metadata(s.current), "a{sv}"),
            "Volume" to Variant(s.volume.toDouble()),
            "Position" to Variant(s.positionMs * 1000),
            "MinimumRate" to Variant(1.0),
            "MaximumRate" to Variant(1.0),
            "CanGoNext" to Variant(hasTrack),
            "CanGoPrevious" to Variant(hasTrack),
            "CanPlay" to Variant(true),
            "CanPause" to Variant(hasTrack),
            "CanSeek" to Variant(hasTrack),
            "CanControl" to Variant(true),
        )
    }

    private fun metadata(t: Track?): Map<String, Variant<*>> {
        if (t == null) return linkedMapOf("mpris:trackid" to Variant(DBusPath(NO_TRACK)))
        val m = linkedMapOf<String, Variant<*>>(
            "mpris:trackid" to Variant(DBusPath(trackPath(t))),
            "mpris:length" to Variant(t.durationMs * 1000),
            "xesam:title" to Variant(t.title),
        )
        if (t.artist.isNotBlank()) m["xesam:artist"] = Variant(listOf(t.artist), "as")
        if (t.album.isNotBlank()) m["xesam:album"] = Variant(t.album)
        if (t.albumArtist.isNotBlank()) m["xesam:albumArtist"] = Variant(listOf(t.albumArtist), "as")
        if (t.genre.isNotBlank()) m["xesam:genre"] = Variant(listOf(t.genre), "as")
        if (t.trackNo > 0) m["xesam:trackNumber"] = Variant(t.trackNo)
        m["xesam:url"] = Variant(File(t.path).toURI().toString())
        coverFile(t)?.let { m["mpris:artUrl"] = Variant(it.toURI().toString()) }
        return m
    }

    /** A capa já fica em cache como .jpg; o painel do sistema lê o arquivo direto. */
    private fun coverFile(t: Track): File? = runCatching {
        ArtLoader.bytesFor(t) ?: return null
        File(File(Dirs.cache, "art-v2"), ArtLoader.key(t) + ".jpg").takeIf { it.exists() }
    }.getOrNull()

    private fun emitPlayerChanged() {
        val conn = connection ?: return
        runCatching {
            val all = playerProperties()
            val changed = linkedMapOf<String, Variant<*>>()
            for (key in listOf("PlaybackStatus", "Metadata", "Volume", "CanGoNext", "CanGoPrevious", "CanPause", "CanSeek", "Shuffle", "LoopStatus")) {
                all[key]?.let { changed[key] = it }
            }
            conn.sendMessage(Properties.PropertiesChanged(PATH, PLAYER_IFACE, changed, emptyList()))
        }
    }

    private fun onUi(block: () -> Unit) = SwingUtilities.invokeLater { runCatching(block) }

    private fun trackPath(t: Track) = "/com/juscelino/sonivell/track/" + t.id.toULong().toString(16)

    private companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.sonivell"
        const val PATH = "/org/mpris/MediaPlayer2"
        const val ROOT_IFACE = "org.mpris.MediaPlayer2"
        const val PLAYER_IFACE = "org.mpris.MediaPlayer2.Player"
        const val NO_TRACK = "/org/mpris/MediaPlayer2/TrackList/NoTrack"
    }
}
