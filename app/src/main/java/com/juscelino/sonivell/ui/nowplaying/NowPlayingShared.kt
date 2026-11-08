package com.juscelino.sonivell.ui.nowplaying

import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.ui.theme.SonivellTheme

/** Ações do player, as mesmas em todos os estilos. */
data class NowPlayingActions(
    val onClose: () -> Unit,
    val onMore: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onFavorite: () -> Unit,
    val onLyrics: () -> Unit,
    val onQueue: () -> Unit,
    val onSleepTimer: () -> Unit = {},
    /** Aleatório e Smart são independentes: o aleatório é um botão, o Smart um interruptor. */
    val onSetShuffle: (com.juscelino.sonivell.ShuffleMode) -> Unit = {},
)

// ---- Estrutura comum do player (proposta visual: igual em todos os estilos) ----

/**
 * Linha de baixo do título no player e nos menus: o artista e, quando diz algo a mais, o álbum.
 * Single com o mesmo nome da faixa não repete o título; "Nome - Single" vira "Nome (Single)".
 */
fun playerSubtitle(track: Track): String {
    val album = track.album.trim().let { a ->
        val m = Regex("""\s+-\s+(Single|EP)$""", RegexOption.IGNORE_CASE).find(a)
        if (m != null) a.removeRange(m.range) + " (" + m.groupValues[1] + ")" else a
    }
    val bare = album.removeSuffix(" (Single)").removeSuffix(" (EP)")
    val showAlbum = album.isNotBlank() && !bare.equals(track.title.trim(), ignoreCase = true) && !album.equals(track.artist, ignoreCase = true)
    return listOfNotNull(track.artist.takeIf { it.isNotBlank() }, album.takeIf { showAlbum }).joinToString(" · ")
}

/** Tempo restante com sinal de menos de verdade ("−32:21"): mais útil que o total em sets longos. */
fun remainingLabel(positionMs: Long, durationMs: Long): String =
    "−" + formatDuration((durationMs - positionMs).coerceAtLeast(0L))

/**
 * Abre a escolha de saída de áudio do sistema (fone, Bluetooth, alto-falante). No Android 14+ é o
 * seletor do próprio sistema; no 11–13, o painel de saída de mídia; antes disso, os ajustes de Bluetooth.
 */
fun openOutputSwitcher(context: Context) {
    runCatching {
        if (Build.VERSION.SDK_INT >= 34) {
            MediaRouter2.getInstance(context).showSystemOutputSwitcher()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.startActivity(
                Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                    .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Título do player (até duas linhas) e o artista embaixo. */
@Composable
fun PlayerTitle(track: Track, modifier: Modifier = Modifier, titleSize: androidx.compose.ui.unit.TextUnit = MaterialTheme.typography.headlineSmall.fontSize, color: Color = SonivellTheme.colors.textPrimary, secondary: Color = SonivellTheme.colors.textSecondary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            track.title,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = titleSize, lineHeight = titleSize * 1.18f),
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(playerSubtitle(track), style = MaterialTheme.typography.bodyLarge, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Embaralhar ou repetir: ícone discreto; ligado, na cor de destaque com um ponto embaixo.
 * Com [showLabel] (estilo Ondas) o nome aparece embaixo do ícone.
 */
@Composable
fun PlayerModeToggle(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    showLabel: Boolean = false,
    offColor: Color = SonivellTheme.colors.textSecondary,
    onColor: Color = SonivellTheme.colors.accent,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = on, role = Role.Switch, onValueChange = { onClick() })
            .semantics { contentDescription = label }
            .heightIn(min = 44.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (on) onColor else offColor)
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (on) onColor else offColor, maxLines = 1)
        }
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) onColor else Color.Transparent))
    }
}

/** "Fila pelo som · Smart" como interruptor de verdade (não um botão que parece selo). */
@Composable
fun SmartQueueSwitch(
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = SonivellTheme.colors.textPrimary,
    secondary: Color = SonivellTheme.colors.textSecondary,
    accent: Color = SonivellTheme.colors.accent,
    onAccent: Color = SonivellTheme.colors.onAccent,
) {
    val c = SonivellTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(value = on, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SonivellTheme.icons.smart, null, Modifier.size(18.dp), tint = if (on) accent else secondary)
        Text(stringResource(R.string.np_queue_by_sound), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.np_smart), style = MaterialTheme.typography.bodyMedium, color = secondary)
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = onAccent,
                checkedTrackColor = accent,
                uncheckedThumbColor = secondary,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = c.outline,
            ),
        )
    }
}

/**
 * Rodapé do player, igual em todos os estilos: Este telefone · Letra · Fila, cada um com ícone e
 * rótulo. "Por que tocou" mora no menu de opções; o temporizador também (a lua saiu daqui).
 */
@Composable
fun PlayerFooter(
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SonivellTheme.colors.textTertiary,
) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FooterItem(SonivellTheme.icons.device, stringResource(R.string.this_phone), color, Modifier.weight(1.4f), Alignment.Start) {
            openOutputSwitcher(context)
        }
        FooterItem(SonivellTheme.icons.lyrics, stringResource(R.string.lyrics_label), color, Modifier.weight(1f), Alignment.CenterHorizontally, onLyrics)
        FooterItem(SonivellTheme.icons.queue, stringResource(R.string.np_queue), color, Modifier.weight(1f), Alignment.End, onQueue)
    }
}

@Composable
private fun FooterItem(icon: ImageVector, label: String, color: Color, modifier: Modifier, align: Alignment.Horizontal, onClick: () -> Unit) {
    Box(modifier, contentAlignment = when (align) {
        Alignment.Start -> Alignment.CenterStart
        Alignment.End -> Alignment.CenterEnd
        else -> Alignment.Center
    }) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = 44.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Formato do arquivo como texto simples entre os tempos ("MP3"), sem borda de botão. */
@Composable
fun FormatText(track: Track, color: Color = SonivellTheme.colors.textSecondary) {
    track.formatLabel?.let { fmt ->
        Text(fmt, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Botão de play circular, sem brilho nem anel; mostra pausa enquanto toca. */
@Composable
fun PlayCircle(
    isPlaying: Boolean,
    onClick: () -> Unit,
    size: Dp,
    background: Color = SonivellTheme.colors.accent,
    tint: Color = SonivellTheme.colors.onAccent,
    iconSize: Dp = size * 0.4f,
) {
    val label = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (isPlaying) SonivellTheme.icons.pause else SonivellTheme.icons.play, null, Modifier.size(iconSize), tint = tint)
    }
}

/**
 * Linha de progresso fina com toque e arrasto para buscar: a barra com botão do Clássico
 * ([knob]) e a linha de 3 dp do Essencial (sem botão). [fill] é a cor da parte tocada.
 */
@Composable
fun LineSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    fill: Color = SonivellTheme.colors.textPrimary,
    track: Color = SonivellTheme.colors.track,
    knob: Boolean = true,
    /** Rótulo para leitores de tela; por padrão, o da busca na faixa. */
    label: String? = null,
) {
    val seekLabel = label ?: stringResource(R.string.cd_seek)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        modifier
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
        val bar = RoundedCornerShape(thickness / 2)
        Box(Modifier.fillMaxWidth().height(thickness).clip(bar).background(track))
        Box(Modifier.fillMaxWidth(fraction).height(thickness).clip(bar).background(fill))
        if (knob) {
            val size = if (dragFraction != null) 18.dp else 14.dp
            Box(
                Modifier
                    .offset(x = (width * fraction) - size / 2)
                    .size(size)
                    .clip(CircleShape)
                    .background(fill),
            )
        }
    }
}


/**
 * Selo discreto no topo do player quando o som está espacial: Dolby Atmos, multicanal no áudio espacial
 * do aparelho, ou o Som espacial do Sonivell na música estéreo. [tint] troca a cor (o Contraste usa branco).
 */
@Composable
fun SpatialBadge(label: String?, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = SonivellTheme.colors.accent) {
    if (label == null) return
    Box(
        modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = tint, maxLines = 1)
    }
}

/** Texto do [SpatialBadge] para o áudio tocando agora, ou null quando o som é estéreo comum. */
@Composable
fun rememberSpatialLabel(audio: com.juscelino.sonivell.playback.AudioInfo, sonivellSpatial: Boolean, systemSpatial: Boolean): String? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val system = androidx.compose.runtime.remember(audio, systemSpatial) {
        systemSpatial && com.juscelino.sonivell.playback.SpatialAudio.systemStatus(context).let {
            it == com.juscelino.sonivell.playback.SpatialAudio.SystemStatus.On ||
                it == com.juscelino.sonivell.playback.SpatialAudio.SystemStatus.OnHeadTracking
        }
    }
    val layout = when (audio.channels) {
        6 -> "5.1"
        8 -> "7.1"
        else -> audio.channels.toString()
    }
    return when {
        audio.isAtmos -> stringResource(R.string.spatial_badge_atmos)
        audio.isMultichannel -> if (system) stringResource(R.string.spatial_badge_system, layout) else stringResource(R.string.spatial_badge_channels, layout)
        audio.channels == 2 && sonivellSpatial -> stringResource(R.string.spatial_badge_sonivell)
        else -> null
    }
}
