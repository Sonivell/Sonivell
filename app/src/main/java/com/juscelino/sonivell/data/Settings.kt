package com.juscelino.sonivell.data

import android.content.SharedPreferences
import com.juscelino.sonivell.ui.theme.AccentChoice
import com.juscelino.sonivell.ui.theme.DarkBackground
import com.juscelino.sonivell.ui.theme.IconStyle
import com.juscelino.sonivell.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Layout das telas principais (Para você, Busca, Biblioteca, player e mini player). */
enum class UiStyle {
    /** 3.0: identidade própria, com a onda, botões com nome e o porquê do SMART. */
    ONDAS,

    /** 2.0: player tingido pela capa e texto branco. */
    CONTRASTE,

    /** 1.0: o visual da primeira versão. */
    CLASSICA,

    /** Enxuto: capas em destaque e só o essencial em cada tela (usa o player do Ondas). */
    ESSENCIAL,
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.AUTO,
    val accent: AccentChoice = AccentChoice.Lima,
    val uiStyle: UiStyle = UiStyle.ONDAS,
    /** Traço (padrão, dos mockups), fino (como o Lumivell) ou arredondado. */
    val iconStyle: IconStyle = IconStyle.TRACO,
    /** Fundo usado no tema escuro. */
    val darkBackground: DarkBackground = DarkBackground.Grafite,
    /** Fundo e superfícies no tom da cor de destaque (vale para todos os layouts). */
    val tintedBackground: Boolean = false,
    /** Usar a cor do sistema (Material You, Android 12+) no lugar da cor de destaque escolhida. */
    val systemColors: Boolean = false,
    /** Pausar quando o fone é desconectado (Media3 "becoming noisy"). */
    val pauseOnUnplug: Boolean = true,
    /** Não registrar novas escutas (o SMART e o Para você param de aprender). */
    val historyPaused: Boolean = false,
    /** Variedade de artistas no SMART: 0 a 4 (2 = padrão do LatentJam). */
    val artistVariety: Int = 2,
    /** Normalizar o volume entre faixas pelo ReplayGain das tags. */
    val normalize: Boolean = false,
    /** O SMART aprende o seu gosto com o histórico (só na edição smart). */
    val learnTaste: Boolean = true,
    /** Crossfade: o fim de uma faixa some enquanto a próxima entra, as duas sobrepostas. */
    val crossfade: Boolean = false,
    /** Duração do crossfade em segundos ([CROSSFADE_STEPS]). Com o SMART adaptando, é o "meio". */
    val crossfadeSeconds: Int = 6,
    /** O SMART alonga a transição entre faixas parecidas e encurta entre as bem diferentes. */
    val crossfadeSmart: Boolean = true,
    /** Não misturar faixas seguidas do mesmo álbum (álbuns ao vivo e sem pausa ficam inteiros). */
    val crossfadeKeepAlbums: Boolean = true,
    /** Voltar a tocar ao conectar fone com fio ou Bluetooth (com o player aberto em segundo plano). */
    val resumeOnConnect: Boolean = false,
    /** Um ajuste de equalizador para cada saída (fone, Bluetooth, alto-falante), trocado sozinho. */
    val eqPerOutput: Boolean = false,
    /** Velocidade de reprodução ([SPEED_STEPS]). */
    val playbackSpeed: Float = 1f,
    /** Cortar trechos de silêncio (podcasts, aulas). */
    val skipSilence: Boolean = false,
    /** Atualizar a biblioteca sozinha quando músicas chegam ou saem do aparelho. */
    val autoRefresh: Boolean = true,
    /** Ignorar áudios mais curtos que isso, em segundos (0 = não ignora). */
    val minDurationSec: Int = 0,
    /** Pastas cujas músicas ficam fora da biblioteca. */
    val ignoredFolders: Set<String> = emptySet(),
    /** Publicar as playlists como .m3u para o Android e outros apps (Android 10+). */
    val publishPlaylists: Boolean = false,
    /** Barras animadas sobre a capa no player (pede a permissão de microfone do Android). */
    val visualizer: Boolean = false,
)

/** Velocidades oferecidas. */
val SPEED_STEPS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** Durações mínimas oferecidas para ignorar áudios curtos, em segundos. */
val MIN_DURATION_STEPS = listOf(0, 15, 30, 60)

/** Durações oferecidas para o crossfade, em segundos. */
val CROSSFADE_STEPS = listOf(2, 4, 6, 8, 10, 12)

/** Ajustes do app, nas preferências locais. O serviço de reprodução lê as mesmas chaves. */
class SettingsStore(private val prefs: SharedPreferences) {

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs.edit()
            .putInt(KEY_THEME, next.theme.ordinal)
            .putInt(KEY_ACCENT, next.accent.ordinal)
            .putString(KEY_ICON_STYLE, next.iconStyle.name)
            .putString(KEY_UI_STYLE, next.uiStyle.name)
            .putString(KEY_DARK_BACKGROUND, next.darkBackground.name)
            .putBoolean(KEY_TINTED_BACKGROUND, next.tintedBackground)
            .putBoolean(KEY_SYSTEM_COLORS, next.systemColors)
            .putBoolean(KEY_PAUSE_ON_UNPLUG, next.pauseOnUnplug)
            .putBoolean(KEY_HISTORY_PAUSED, next.historyPaused)
            .putInt(KEY_ARTIST_VARIETY, next.artistVariety)
            .putBoolean(KEY_NORMALIZE, next.normalize)
            .putBoolean(KEY_LEARN_TASTE, next.learnTaste)
            .putBoolean(KEY_CROSSFADE, next.crossfade)
            .putInt(KEY_CROSSFADE_SECONDS, next.crossfadeSeconds)
            .putBoolean(KEY_CROSSFADE_SMART, next.crossfadeSmart)
            .putBoolean(KEY_CROSSFADE_KEEP_ALBUMS, next.crossfadeKeepAlbums)
            .putBoolean(KEY_RESUME_ON_CONNECT, next.resumeOnConnect)
            .putBoolean(KEY_EQ_PER_OUTPUT, next.eqPerOutput)
            .putFloat(KEY_PLAYBACK_SPEED, next.playbackSpeed)
            .putBoolean(KEY_SKIP_SILENCE, next.skipSilence)
            .putBoolean(KEY_AUTO_REFRESH, next.autoRefresh)
            .putInt(KEY_MIN_DURATION, next.minDurationSec)
            .putStringSet(KEY_IGNORED_FOLDERS, next.ignoredFolders)
            .putBoolean(KEY_PUBLISH_PLAYLISTS, next.publishPlaylists)
            .putBoolean(KEY_VISUALIZER, next.visualizer)
            .apply()
    }

    fun reset() = update { AppSettings() }

    private fun read() = AppSettings(
        theme = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, 0)) { ThemeMode.AUTO },
        accent = AccentChoice.entries.getOrElse(prefs.getInt(KEY_ACCENT, AccentChoice.Lima.ordinal)) { AccentChoice.Lima },
        uiStyle = UiStyle.entries.firstOrNull { it.name == prefs.getString(KEY_UI_STYLE, null) } ?: UiStyle.ONDAS,
        iconStyle = IconStyle.entries.firstOrNull { it.name == prefs.getString(KEY_ICON_STYLE, null) } ?: IconStyle.TRACO,
        darkBackground = DarkBackground.entries.firstOrNull { it.name == prefs.getString(KEY_DARK_BACKGROUND, null) } ?: DarkBackground.Grafite,
        tintedBackground = prefs.getBoolean(KEY_TINTED_BACKGROUND, false),
        systemColors = prefs.getBoolean(KEY_SYSTEM_COLORS, false),
        pauseOnUnplug = prefs.getBoolean(KEY_PAUSE_ON_UNPLUG, true),
        historyPaused = prefs.getBoolean(KEY_HISTORY_PAUSED, false),
        artistVariety = prefs.getInt(KEY_ARTIST_VARIETY, 2),
        normalize = prefs.getBoolean(KEY_NORMALIZE, false),
        learnTaste = prefs.getBoolean(KEY_LEARN_TASTE, true),
        crossfade = prefs.getBoolean(KEY_CROSSFADE, false),
        crossfadeSeconds = prefs.getInt(KEY_CROSSFADE_SECONDS, 6),
        crossfadeSmart = prefs.getBoolean(KEY_CROSSFADE_SMART, true),
        crossfadeKeepAlbums = prefs.getBoolean(KEY_CROSSFADE_KEEP_ALBUMS, true),
        resumeOnConnect = prefs.getBoolean(KEY_RESUME_ON_CONNECT, false),
        eqPerOutput = prefs.getBoolean(KEY_EQ_PER_OUTPUT, false),
        playbackSpeed = prefs.getFloat(KEY_PLAYBACK_SPEED, 1f),
        skipSilence = prefs.getBoolean(KEY_SKIP_SILENCE, false),
        autoRefresh = prefs.getBoolean(KEY_AUTO_REFRESH, true),
        minDurationSec = prefs.getInt(KEY_MIN_DURATION, 0),
        ignoredFolders = prefs.getStringSet(KEY_IGNORED_FOLDERS, null)?.toSet().orEmpty(),
        publishPlaylists = prefs.getBoolean(KEY_PUBLISH_PLAYLISTS, false),
        visualizer = prefs.getBoolean(KEY_VISUALIZER, false),
    )

    companion object {
        const val PREFS = "sonivell"
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
        const val KEY_ICON_STYLE = "icon_style"
        const val KEY_UI_STYLE = "ui_style"
        const val KEY_DARK_BACKGROUND = "dark_background"
        const val KEY_TINTED_BACKGROUND = "tinted_background"
        const val KEY_SYSTEM_COLORS = "system_colors"
        const val KEY_PAUSE_ON_UNPLUG = "pause_on_unplug"
        const val KEY_HISTORY_PAUSED = "history_paused"
        const val KEY_ARTIST_VARIETY = "artist_variety"
        const val KEY_NORMALIZE = "normalize"
        const val KEY_LEARN_TASTE = "learn_taste"
        const val KEY_CROSSFADE = "crossfade"
        const val KEY_CROSSFADE_SECONDS = "crossfade_seconds"
        const val KEY_CROSSFADE_SMART = "crossfade_smart"
        const val KEY_CROSSFADE_KEEP_ALBUMS = "crossfade_keep_albums"
        const val KEY_RESUME_ON_CONNECT = "resume_on_connect"
        const val KEY_EQ_PER_OUTPUT = "per_output_eq"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_AUTO_REFRESH = "auto_refresh"
        const val KEY_MIN_DURATION = "min_duration"
        const val KEY_IGNORED_FOLDERS = "ignored_folders"
        const val KEY_PUBLISH_PLAYLISTS = "publish_playlists"
        const val KEY_VISUALIZER = "visualizer"
        /** Escrita pelo MainViewModel: a fila atual é do SMART. */
        const val KEY_SMART_MODE = "smart_mode"
    }
}
