package com.juscelino.sonivell.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ShuffleMode
import com.juscelino.sonivell.compat.stringResource
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.UiStyle
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.playback.PlayerState
import com.juscelino.sonivell.playback.RepeatMode
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.Waveform
import com.juscelino.sonivell.ui.nowplaying.LineSeekBar
import com.juscelino.sonivell.ui.nowplaying.PlayCircle
import com.juscelino.sonivell.ui.nowplaying.artTint
import com.juscelino.sonivell.ui.nowplaying.remainingLabel
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.cardSurface

/** Uma aba da barra lateral: Para você · Explorar · Biblioteca · Perfil. */
data class DesktopTab(val route: String, val label: Int, val icon: ImageVector, val activeIcon: ImageVector)

/** O ícone do app ("Onda plana"): uma onda lima sobre grafite. */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width
        drawRoundRect(Color(0xFF0E0F12), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.25f, s * 0.25f))
        val k = s / 96f
        val p = Path().apply {
            moveTo(20 * k, 48 * k)
            cubicTo(32 * k, 22 * k, 44 * k, 22 * k, 48 * k, 48 * k)
            cubicTo(52 * k, 74 * k, 64 * k, 74 * k, 76 * k, 48 * k)
        }
        drawPath(p, Color(0xFFC6F24E), style = Stroke(width = 9 * k, cap = StrokeCap.Round))
    }
}

/**
 * Barra lateral do PC: as mesmas quatro abas do app, na mesma ordem, com a aba ativa no jeito de
 * cada estilo (fundo suave na cor de destaque no Sonivell, Ondas e Essencial; branco e mais peso no
 * Contraste; cor de destaque no Clássico). Embaixo, a leitura das pastas e a análise do som.
 */
@Composable
fun DesktopSidebar(
    tabs: List<DesktopTab>,
    active: String?,
    onSelect: (String) -> Unit,
    scanning: Pair<Int, Int>?,
    smartAvailable: Boolean,
    smartProgress: SmartProgress,
    onOpenAnalysis: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SonivellTheme.colors
    val style = SonivellTheme.style
    Column(
        modifier
            .width(232.dp)
            .fillMaxHeight()
            .cardSurface(RoundedCornerShape(22.dp), c.surfaceLow, elevation = 8.dp)
            .padding(horizontal = 10.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.padding(start = 10.dp, top = 2.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppLogo(30.dp)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 1.05f), color = c.textPrimary)
        }
        tabs.forEach { tab ->
            val selected = tab.route == active
            val (bg, fg) = when (style) {
                UiStyle.CONTRASTE -> (if (selected) c.surfaceHigh else Color.Transparent) to (if (selected) c.textPrimary else c.textSecondary)
                UiStyle.CLASSICA -> (if (selected) c.accent.copy(alpha = 0.10f) else Color.Transparent) to (if (selected) c.accent else c.textSecondary)
                else -> (if (selected) c.accent.copy(alpha = 0.14f) else Color.Transparent) to (if (selected) c.accent else c.textSecondary)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.route) })
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(if (selected) tab.activeIcon else tab.icon, null, Modifier.size(20.dp), tint = fg)
                Text(
                    stringResource(tab.label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = fg,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (scanning != null) {
            val (done, total) = scanning
            Text(
                if (total > 0) stringResource(R.string.desktop_scanning, done, total) else stringResource(R.string.desktop_scanning_plain),
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                maxLines = 2,
            )
        }
        if (smartAvailable && smartProgress.running && smartProgress.total > 0) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onOpenAnalysis)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.sv_analysis_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
                Text(
                    stringResource(R.string.desktop_analysis_count, smartProgress.analysed, smartProgress.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                )
                val f = (smartProgress.analysed.toFloat() / smartProgress.total).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.track)) {
                    Box(Modifier.fillMaxWidth(f).height(3.dp).background(c.accent))
                }
            }
        }
    }
}

/** O que a barra do player pode fazer. */
data class PlayerBarActions(
    val onOpenPlayer: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onSetShuffle: (ShuffleMode) -> Unit,
    val onLike: () -> Unit,
    val onVolume: (Float) -> Unit,
    val onOutput: () -> Unit,
    val onLyrics: () -> Unit,
    val onQueue: () -> Unit,
    val onCompact: () -> Unit,
)

/**
 * Barra do player na base da janela, com a estrutura comum da proposta: capa, título e artista;
 * embaralhar · anterior · play · próxima · repetir; o progresso com o tempo restante ("−3:12"); o
 * Smart como interruptor ("Fila pelo som"); e Este computador · Letra · Fila. O que muda por estilo
 * é a assinatura: a onda no Sonivell e no Ondas, a linha fina no Essencial, a linha com botão no
 * Clássico e, no Contraste, a barra inteira no tom da capa com o branco marcando as ações.
 */
@Composable
fun DesktopPlayerBar(
    track: Track?,
    state: PlayerState,
    shuffleMode: ShuffleMode,
    smartAvailable: Boolean,
    liked: Boolean,
    volume: Float,
    queueOpen: Boolean,
    actions: PlayerBarActions,
    modifier: Modifier = Modifier,
) {
    val base = SonivellTheme.colors
    val style = SonivellTheme.style
    val contraste = style == UiStyle.CONTRASTE
    // Contraste: o player toma a cor da capa e o branco marca as ações.
    val tone = if (contraste && track != null) artTint(track) else null
    val primary = if (tone != null) Color.White else base.textPrimary
    val secondary = if (tone != null) Color.White.copy(alpha = 0.72f) else base.textSecondary
    val accent = base.accent
    val playBg = if (contraste) Color.White else accent
    val playFg = if (contraste) (tone ?: Color.Black) else base.onAccent
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(104.dp)
            .then(if (tone != null) Modifier.clip(shape).background(tone) else Modifier.cardSurface(shape, base.surface, elevation = 10.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Capa, título e artista (tocar abre o player completo).
        Row(
            Modifier.width(300.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = track != null, role = Role.Button, onClickLabel = stringResource(R.string.desktop_open_player), onClick = actions.onOpenPlayer)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                AlbumArt(track, Modifier.size(64.dp), corner = 14.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(track?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track?.artist.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (track != null) {
                IconBtn(
                    if (liked) SonivellTheme.icons.liked else SonivellTheme.icons.like,
                    stringResource(if (liked) R.string.cd_unlike else R.string.cd_like),
                    actions.onLike,
                    iconSize = 20.dp,
                    tint = if (liked) (if (contraste) Color.White else accent) else secondary,
                )
            }
        }

        // Controles e progresso.
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val shuffleOn = shuffleMode == ShuffleMode.ON
                ModeButton(SonivellTheme.icons.shuffle, stringResource(if (shuffleOn) R.string.cd_shuffle_on else R.string.cd_shuffle_off), shuffleOn, if (contraste) Color.White else accent, secondary) {
                    actions.onSetShuffle(if (shuffleOn) ShuffleMode.OFF else ShuffleMode.ON)
                }
                IconBtn(SonivellTheme.icons.skipPrevious, stringResource(R.string.cd_previous), actions.onPrevious, iconSize = 24.dp, tint = primary)
                PlayCircle(state.isPlaying, actions.onTogglePlay, size = 50.dp, background = playBg, tint = playFg, iconSize = 22.dp)
                IconBtn(SonivellTheme.icons.skipNext, stringResource(R.string.cd_next), actions.onNext, iconSize = 24.dp, tint = primary)
                val repeatOn = state.repeat != RepeatMode.OFF
                ModeButton(
                    if (state.repeat == RepeatMode.ONE) SonivellTheme.icons.repeatOne else SonivellTheme.icons.repeat,
                    stringResource(when (state.repeat) { RepeatMode.OFF -> R.string.cd_repeat_off; RepeatMode.ALL -> R.string.cd_repeat_all; RepeatMode.ONE -> R.string.cd_repeat_one }),
                    repeatOn, if (contraste) Color.White else accent, secondary, actions.onRepeat,
                )
            }
            Row(
                Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val duration = track?.durationMs ?: 0L
                Text(formatDuration(state.positionMs), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = secondary)
                Box(Modifier.weight(1f)) {
                    if (track != null) {
                        when (style) {
                            UiStyle.SONIVELL, UiStyle.ONDAS -> BarWave(track.id, state.positionMs, duration, actions.onSeek)
                            UiStyle.ESSENCIAL -> LineSeekBar(state.positionMs, duration, actions.onSeek, thickness = 3.dp, fill = accent, knob = false)
                            UiStyle.CLASSICA -> LineSeekBar(state.positionMs, duration, actions.onSeek, thickness = 4.dp, fill = accent, knob = true)
                            UiStyle.CONTRASTE -> LineSeekBar(state.positionMs, duration, actions.onSeek, thickness = 4.dp, fill = Color.White, track = Color.White.copy(alpha = 0.25f), knob = true)
                        }
                    }
                }
                Text(if (track != null) remainingLabel(state.positionMs, duration) else "", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = secondary)
            }
        }

        // Smart, volume e o rodapé (Este computador · Letra · Fila).
        Column(
            Modifier.width(330.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            if (smartAvailable) {
                val on = shuffleMode == ShuffleMode.SMART
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .toggleable(value = on, role = Role.Switch) { actions.onSetShuffle(if (it) ShuffleMode.SMART else ShuffleMode.OFF) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(SonivellTheme.icons.smart, null, Modifier.size(16.dp), tint = if (on) (if (contraste) Color.White else accent) else secondary)
                    Text(stringResource(R.string.np_queue_by_sound), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = primary, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(stringResource(R.string.np_smart), style = MaterialTheme.typography.bodyMedium, color = secondary)
                    Switch(
                        checked = on,
                        onCheckedChange = null,
                        modifier = Modifier.height(28.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = if (contraste) (tone ?: Color.Black) else base.onAccent,
                            checkedTrackColor = if (contraste) Color.White else accent,
                            uncheckedThumbColor = secondary,
                            uncheckedTrackColor = Color.Transparent,
                            uncheckedBorderColor = if (contraste) Color.White.copy(alpha = 0.4f) else base.outline,
                        ),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FooterButton(SonivellTheme.icons.device, stringResource(R.string.this_phone), secondary, actions.onOutput)
                FooterButton(SonivellTheme.icons.lyrics, stringResource(R.string.lyrics_label), secondary, actions.onLyrics)
                FooterButton(SonivellTheme.icons.queue, stringResource(R.string.np_queue), if (queueOpen) (if (contraste) Color.White else accent) else secondary, actions.onQueue)
                Spacer(Modifier.weight(1f))
                IconBtn(SonivellTheme.icons.volumeUp, stringResource(R.string.cd_volume), { actions.onVolume(if (volume > 0f) 0f else 0.8f) }, size = 34.dp, iconSize = 18.dp, tint = secondary)
                VolumeLine(volume, actions.onVolume, Modifier.width(72.dp), fill = if (contraste) Color.White else accent, track = if (tone != null) Color.White.copy(alpha = 0.25f) else base.track)
                IconBtn(SonivellTheme.icons.collapse, stringResource(R.string.desktop_compact_mode), actions.onCompact, size = 34.dp, iconSize = 18.dp, tint = secondary)
            }
        }
    }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, on: Boolean, accent: Color, off: Color, onClick: () -> Unit) {
    // Ligado: na cor de destaque com um ponto embaixo (como no player do app).
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(20.dp), tint = if (on) accent else off)
        if (on) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
    }
}

@Composable
private fun FooterButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/** A onda da faixa como barra de progresso, com toque e arrasto (Sonivell e Ondas). */
@Composable
private fun BarWave(seed: Long, positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(durationMs) { detectTapGestures { o -> if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong()) } }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures { change, _ ->
                    if (durationMs > 0) onSeek(((change.position.x / size.width).coerceIn(0f, 1f) * durationMs).toLong())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Waveform(seed, fraction, Modifier.fillMaxWidth().height(24.dp), bars = 90)
    }
}

/** Volume: trilha fina e a parte cheia; toque ou arrasto. */
@Composable
fun VolumeLine(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, fill: Color = SonivellTheme.colors.accent, track: Color = SonivellTheme.colors.track) {
    Box(
        modifier
            .height(24.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(track))
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(fill))
    }
}
