package com.juscelino.sonivell.ui.nowplaying

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ShuffleMode
import com.juscelino.sonivell.SleepTimer
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.playback.PlayerState
import com.juscelino.sonivell.playback.RepeatMode
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.lyrics.LyricsState
import com.juscelino.sonivell.ui.theme.ArtTones
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import android.graphics.Color as AColor


/** Texto do player 2.0: sempre claro, sobre o fundo tingido pela capa. */
private val OnTint = Color.White
private val OnTintSoft = Color.White.copy(alpha = 0.78f)

/**
 * Player do estilo Contraste: o tom da capa tinge a tela em dois tons (claro no topo, escuro na base).
 * Branco é ação (play, progresso, controles); a cor de destaque marca só estado (embaralhar, repetir
 * e Smart ligados). Estrutura comum: Tocando de · origem, controles padrão, Smart como interruptor e
 * rodapé Este telefone · Letra · Fila em superfície translúcida.
 */
@Composable
fun NowPlayingScreenContraste(
    track: Track,
    state: PlayerState,
    shuffleMode: ShuffleMode,
    smartAvailable: Boolean,
    isFavorite: Boolean,
    sourceLabel: String,
    actions: NowPlayingActions,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val tint by animateColorAsState(artTint(track), tween(600), label = "tint")
    LightSystemBarIcons()

    val durationMs = state.durationMs.takeIf { it > 0 } ?: track.durationMs
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tint, lerp(tint, Color(0xFF121212), 0.7f))))
            .safeDrawingPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Topo
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(icons.collapse, stringResource(R.string.cd_close_player), actions.onClose, tint = OnTint)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.playing_from).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.9.sp),
                    color = OnTintSoft,
                )
                Text(sourceLabel, style = MaterialTheme.typography.titleSmall, color = OnTint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(icons.moreHoriz, stringResource(R.string.cd_more_options), actions.onMore, tint = OnTint)
        }

        // Capa maior, com o espaço equilibrado acima e abaixo.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight, 440.dp)
            AlbumArt(track, Modifier.size(side), corner = 12.dp, detailed = true)
        }

        // Título (até duas linhas) + artista, tipografia pesada
        Row(verticalAlignment = Alignment.Bottom) {
            PlayerTitle(track, Modifier.weight(1f), titleSize = 24.sp, color = OnTint, secondary = OnTintSoft)
            IconBtn(
                if (isFavorite) icons.liked else icons.like,
                stringResource(if (isFavorite) R.string.cd_unlike else R.string.cd_like),
                actions.onFavorite,
                tint = if (isFavorite) c.accent else OnTint,
                iconSize = 26.dp,
            )
        }

        // Progresso branco; "MP3" como texto; tempo restante com sinal de menos.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LineSeekBar(state.positionMs, durationMs, actions.onSeek, fill = OnTint, track = OnTint.copy(alpha = 0.25f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeText(formatDuration(state.positionMs))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { FormatText(track, OnTintSoft) }
                TimeText(remainingLabel(state.positionMs, durationMs))
            }
        }

        // Controles padrão; pausa branca de 74, sem anel.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerModeToggle(icons.shuffle, stringResource(R.string.mode_shuffle), shuffleMode == ShuffleMode.ON, offColor = OnTintSoft, onClick = {
                actions.onSetShuffle(if (shuffleMode == ShuffleMode.ON) ShuffleMode.OFF else ShuffleMode.ON)
            })
            IconBtn(icons.skipPrevious, stringResource(R.string.cd_previous), actions.onPrevious, size = 56.dp, iconSize = 34.dp, tint = OnTint)
            PlayCircle(state.isPlaying, actions.onTogglePlay, size = 74.dp, background = OnTint, tint = Color(0xFF121212), iconSize = 32.dp)
            IconBtn(icons.skipNext, stringResource(R.string.cd_next), actions.onNext, size = 56.dp, iconSize = 34.dp, tint = OnTint)
            PlayerModeToggle(
                if (state.repeat == RepeatMode.ONE) icons.repeatOne else icons.repeat,
                stringResource(
                    when (state.repeat) {
                        RepeatMode.OFF -> R.string.cd_repeat_off
                        RepeatMode.ALL -> R.string.cd_repeat_all
                        RepeatMode.ONE -> R.string.cd_repeat_one
                    }
                ),
                state.repeat != RepeatMode.OFF,
                offColor = OnTintSoft,
                onClick = actions.onRepeat,
            )
        }

        if (smartAvailable) {
            SmartQueueSwitch(
                on = shuffleMode == ShuffleMode.SMART,
                onToggle = { on -> actions.onSetShuffle(if (on) ShuffleMode.SMART else ShuffleMode.OFF) },
                textColor = OnTint,
                secondary = OnTintSoft,
            )
        }

        // Rodapé em superfície translúcida sobre o tom da capa.
        PlayerFooter(
            onLyrics = actions.onLyrics,
            onQueue = actions.onQueue,
            color = OnTint,
            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.22f)).padding(horizontal = 4.dp),
        )
    }
}

/**
 * Cor de fundo do player: o tom dominante da capa, escurecido para o texto branco ficar legível.
 * Sem capa (ou enquanto carrega), usa o tom da capa gerada da mesma faixa. [adjust] troca o
 * escurecimento por outro ajuste (o brilho do vidro usa um tom vivo).
 */
@Composable
internal fun artTint(track: Track, adjust: (Color) -> Color = ::darken): Color {
    val context = LocalContext.current
    val fallback = remember(track.albumId) {
        adjust(ArtTones.rings[(abs(track.albumId) % ArtTones.rings.size).toInt()])
    }
    var tint by remember(track.albumId) { mutableStateOf(fallback) }
    LaunchedEffect(track.artUri) {
        val request = ImageRequest.Builder(context)
            .data(track.artUri)
            .size(48)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@LaunchedEffect
        val color = withContext(Dispatchers.Default) {
            runCatching { dominant(result.drawable.toBitmap(48, 48, Bitmap.Config.ARGB_8888)) }.getOrNull()
        }
        if (color != null) tint = adjust(color)
    }
    return tint
}

/** Média dos pixels com peso maior para os mais saturados e claros (evita um cinza lavado). */
private fun dominant(bitmap: Bitmap): Color {
    val hsv = FloatArray(3)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var total = 0.0
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    for (p in pixels) {
        if (AColor.alpha(p) < 128) continue
        AColor.colorToHSV(p, hsv)
        val w = 0.05 + hsv[1] * hsv[2]
        r += AColor.red(p) * w
        g += AColor.green(p) * w
        b += AColor.blue(p) * w
        total += w
    }
    if (total == 0.0) return Color(0xFF2A2626)
    return Color((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
}

/** O tom da capa vivo e médio, para brilhar sobre o fundo escuro sem estourar (brilho do vidro). */
internal fun glowTone(color: Color): Color {
    val hsv = FloatArray(3)
    AColor.RGBToHSV((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt(), hsv)
    hsv[1] = hsv[1].coerceIn(0.35f, 0.8f)
    hsv[2] = hsv[2].coerceIn(0.6f, 0.85f)
    return Color(AColor.HSVToColor(hsv))
}

private fun darken(color: Color): Color {
    val hsv = FloatArray(3)
    AColor.RGBToHSV((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt(), hsv)
    hsv[1] = hsv[1].coerceAtMost(0.62f)
    hsv[2] = hsv[2].coerceIn(0.16f, 0.36f)
    return Color(AColor.HSVToColor(hsv))
}

/** O fundo do player é sempre escuro: ícones da barra de status claros enquanto ele está aberto. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val nav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (controller != null) {
                status?.let { controller.isAppearanceLightStatusBars = it }
                nav?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun TimeText(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnTintSoft)
}
