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

/** Player do layout Contraste (2.0). */
@Composable
fun NowPlayingScreenContraste(
    track: Track,
    state: PlayerState,
    shuffleMode: ShuffleMode,
    smartAvailable: Boolean,
    isFavorite: Boolean,
    sourceLabel: String,
    actions: NowPlayingActions,
    sleepTimer: SleepTimer = SleepTimer(),
    lyrics: LyricsState = LyricsState.None,
    lyricsOffsetMs: Long = 0L,
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
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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

        // Capa
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AlbumArt(
                track,
                Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f),
                corner = 16.dp,
                detailed = true,
                legacyArt = true,
            )
        }

        // Título + curtir
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(track.title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp), color = OnTint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodyLarge, color = OnTintSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(
                if (isFavorite) icons.liked else icons.like,
                stringResource(if (isFavorite) R.string.cd_unlike else R.string.cd_like),
                actions.onFavorite,
                tint = if (isFavorite) c.accent else OnTint,
                iconSize = 26.dp,
            )
        }

        // Progresso: decorrido, formato e tempo restante.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SeekBar(positionMs = state.positionMs, durationMs = durationMs, onSeek = actions.onSeek)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeText(formatDuration(state.positionMs))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    track.formatLabel?.let { TimeText(it) }
                }
                TimeText("-" + formatDuration((durationMs - state.positionMs).coerceAtLeast(0L)))
            }
        }

        // Controles
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShuffleButton(shuffleMode, smartAvailable, actions.onShuffle)
            IconBtn(icons.skipPrevious, stringResource(R.string.cd_previous), actions.onPrevious, size = 56.dp, iconSize = 34.dp, tint = OnTint)
            IconBtn(
                if (state.isPlaying) icons.pause else icons.play,
                stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                actions.onTogglePlay,
                size = 72.dp,
                iconSize = 32.dp,
                background = OnTint,
                tint = Color(0xFF121212),
            )
            IconBtn(icons.skipNext, stringResource(R.string.cd_next), actions.onNext, size = 56.dp, iconSize = 34.dp, tint = OnTint)
            ActiveDot(active = state.repeat != RepeatMode.OFF) {
                IconBtn(
                    if (state.repeat == RepeatMode.ONE) icons.repeatOne else icons.repeat,
                    stringResource(
                        when (state.repeat) {
                            RepeatMode.OFF -> R.string.cd_repeat_off
                            RepeatMode.ALL -> R.string.cd_repeat_all
                            RepeatMode.ONE -> R.string.cd_repeat_one
                        }
                    ),
                    actions.onRepeat,
                    tint = if (state.repeat == RepeatMode.OFF) OnTintSoft else c.accent,
                )
            }
        }

        // Saída à esquerda; temporizador, letra (quando não há prévia) e fila à direita.
        val loaded = (lyrics as? LyricsState.Loaded)?.lyrics?.takeIf { it.lines.isNotEmpty() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icons.device, null, Modifier.size(18.dp), tint = OnTintSoft)
                Text(stringResource(R.string.this_phone), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OnTintSoft)
            }
            SleepButton(sleepTimer, actions.onSleepTimer)
            if (loaded == null) {
                IconBtn(icons.lyrics, stringResource(R.string.cd_lyrics), actions.onLyrics, tint = OnTintSoft, iconSize = 20.dp)
            }
            IconBtn(icons.queue, stringResource(R.string.cd_open_queue), actions.onQueue, tint = OnTintSoft, iconSize = 20.dp)
        }

        if (loaded != null) {
            val index = loaded.lineAt(state.positionMs - lyricsOffsetMs)
            val first = if (loaded.synced) index.coerceAtLeast(0) else 0
            val current = loaded.lines.getOrNull(first)?.text.orEmpty()
            val next = loaded.lines.getOrNull(first + 1)?.text.orEmpty()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(onClickLabel = stringResource(R.string.cd_lyrics), onClick = actions.onLyrics)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.lyrics_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OnTintSoft)
                Text(
                    current.ifBlank { "♪" },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = OnTint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (next.isNotBlank()) {
                    Text(
                        next,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = OnTint.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
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

/** Ponto verde sob um botão ligado (aleatório, SMART, repetir), como no Spotify. */
@Composable
private fun ActiveDot(active: Boolean, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        content()
        if (active) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-2).dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(SonivellTheme.colors.accent)
            )
        }
    }
}

/** Barra fina (4 dp) com bolinha; aceita toque e arrasto. */
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val seekLabel = stringResource(R.string.cd_seek)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics { contentDescription = seekLabel }
            .pointerInput(durationMs) {
                detectTapGestures { o ->
                    if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragFraction = (o.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val width = maxWidth
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(OnTint.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(OnTint))
        val knob = if (dragFraction != null) 16.dp else 12.dp
        Box(
            Modifier
                .offset(x = (width * fraction) - knob / 2)
                .size(knob)
                .clip(CircleShape)
                .background(OnTint)
        )
    }
}

/** Aleatório em três estados: desligado, comum e SMART (ícone de brilho); ligado ganha o ponto verde. */
@Composable
private fun ShuffleButton(mode: ShuffleMode, smartAvailable: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val label = stringResource(
        when (mode) {
            ShuffleMode.OFF -> R.string.cd_shuffle_on
            ShuffleMode.ON -> if (smartAvailable) R.string.cd_shuffle_smart else R.string.cd_shuffle_off
            ShuffleMode.SMART -> R.string.cd_smart_off
        }
    )
    ActiveDot(active = mode != ShuffleMode.OFF) {
        IconBtn(
            if (mode == ShuffleMode.SMART) SonivellTheme.icons.smart else SonivellTheme.icons.shuffle,
            label,
            onClick,
            tint = if (mode == ShuffleMode.OFF) OnTintSoft else c.accent,
        )
    }
}

/** Temporizador: ícone de lua; quando ligado, mostra a contagem regressiva em verde. */
@Composable
private fun SleepButton(timer: SleepTimer, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    if (!timer.active) {
        IconBtn(SonivellTheme.icons.sleep, stringResource(R.string.cd_sleep_timer), onClick, tint = OnTintSoft, iconSize = 20.dp)
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val label = when {
        timer.endOfTrack -> stringResource(R.string.timer_end_of_track_short)
        timer.endsAtMs != null -> formatDuration((timer.endsAtMs - now).coerceAtLeast(0))
        else -> ""
    }
    Row(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClickLabel = stringResource(R.string.sleep_timer), onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(SonivellTheme.icons.sleep, null, Modifier.size(18.dp), tint = c.accent)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.accent)
    }
}
