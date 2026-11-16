package com.juscelino.sonivell.ui.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ShuffleMode
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.playback.PlayerState
import com.juscelino.sonivell.playback.RepeatMode
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.theme.LocalGlassMaterial
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.glassSurface

/**
 * Player padrão do Sonivell, em todos os estilos e temas ("Vidro"):
 * fundo no tom da capa, botões e painel em vidro, capa grande com sombra, barra de progresso lisa
 * (sem onda), selos de som espacial e formato junto do tempo, play na cor de destaque e, embaixo,
 * um painel de vidro com "Fila pelo som · Smart" e os atalhos Este telefone · Letra · Fila.
 *
 * As cores vêm todas do tema (Grafite, Preto, Fundo colorido, Material You, Claro), então o mesmo
 * desenho se ajusta a cada um; o tom do fundo vem da própria capa.
 */
@Composable
fun NowPlayingScreenVidro(
    track: Track,
    state: PlayerState,
    shuffleMode: ShuffleMode,
    smartAvailable: Boolean,
    isFavorite: Boolean,
    sourceLabel: String,
    actions: NowPlayingActions,
    spatialLabel: String? = null,
    reason: PlayReason = PlayReason.Chosen,
    visualizer: Boolean = false,
    /** Cápsula: painel "Modo de reprodução" sob os controles e a pílula "A seguir" no rodapé. */
    capsula: Boolean = false,
    /** A próxima faixa da fila, para a pílula "A seguir" do Cápsula. */
    nextTrack: Track? = null,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val durationMs = state.durationMs.takeIf { it > 0 } ?: track.durationMs
    val tone by animateColorAsState(artTint(track, ::glowTone), tween(600), label = "tomVidro")
    // Com o fundo da capa ligado, a capa desfocada já está atrás; senão, o fundo liso do tema.
    val backdrop = LocalGlassMaterial.current.backdrop
    // Redesign: o tom da capa mais presente no fundo.
    val glowStrong = if (c.isDark) 0.48f else 0.26f
    val glowSoft = if (c.isDark) 0.12f else 0.08f

    Column(
        Modifier
            .fillMaxSize()
            .then(if (backdrop) Modifier else Modifier.background(c.background))
            // Luz da capa no alto e um toque da cor de destaque embaixo, como no mockup.
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(tone.copy(alpha = glowStrong), Color.Transparent),
                        center = Offset(size.width * 0.45f, size.height * 0.22f),
                        radius = size.width * 1.05f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(c.accent.copy(alpha = glowSoft), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.66f),
                        radius = size.width * 0.7f,
                    ),
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Topo: fechar · Tocando de · origem · mais opções.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassRound(icons.collapse, stringResource(R.string.cd_close_player), actions.onClose)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.playing_from).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = c.textSecondary,
                )
                Text(sourceLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GlassRound(icons.moreHoriz, stringResource(R.string.cd_more_options), actions.onMore)
        }

        // Capa: quadrada no maior tamanho que cabe, com sombra funda.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight, 400.dp)
            val shape = RoundedCornerShape(22.dp)
            Box(Modifier.size(side).shadow(if (c.isDark) 28.dp else 18.dp, shape, clip = false)) {
                AlbumArt(
                    track,
                    Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.08f), shape),
                    corner = 22.dp,
                    detailed = true,
                )
                if (visualizer) {
                    AudioBars(
                        playing = state.isPlaying,
                        color = c.accent,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.32f).padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        // Título + artista; curtir num círculo de vidro. Pelo Smart, a linha "por que".
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlayerTitle(track, titleSize = 26.sp)
                if (reason is PlayReason.Smart) WhyLine(stringResource(R.string.why_line_smart, reason.seed.title))
            }
            com.juscelino.sonivell.ui.components.IconBtn(
                if (isFavorite) icons.liked else icons.like,
                stringResource(if (isFavorite) R.string.cd_unlike else R.string.cd_like),
                actions.onFavorite,
                tint = if (isFavorite) c.accent else c.textPrimary,
                iconSize = 26.dp,
            )
        }

        // Progresso: barra lisa na cor de destaque, botão claro; selos entre os tempos.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Barra neutra: a cor de destaque fica só no play e no que está ligado.
            LineSeekBar(
                state.positionMs, durationMs, actions.onSeek,
                thickness = 5.dp,
                fill = c.textPrimary,
                track = c.textPrimary.copy(alpha = 0.18f),
                knobColor = if (c.isDark) Color.White else c.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeText(formatDuration(state.positionMs))
                Box(Modifier.weight(1f))
                TimeText(remainingLabel(state.positionMs, durationMs))
            }
            // Selos à esquerda; "Fila pelo som" (Smart) vira um chip liga/desliga à direita.
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                spatialLabel?.let { Chip(it, c.textTertiary, c.textPrimary.copy(alpha = 0.08f)) }
                track.formatLabel?.let { Chip(it, c.textTertiary, c.textPrimary.copy(alpha = 0.08f)) }
                Box(Modifier.weight(1f))
                if (smartAvailable && !capsula) {
                    val on = shuffleMode == ShuffleMode.SMART
                    SmartChip(on) { actions.onSetShuffle(if (on) ShuffleMode.OFF else ShuffleMode.SMART) }
                }
            }
        }

        // Controles: embaralhar · anterior · play · próxima · repetir.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PlayerModeToggle(icons.shuffle, stringResource(R.string.mode_shuffle), shuffleMode == ShuffleMode.ON, onClick = {
                actions.onSetShuffle(if (shuffleMode == ShuffleMode.ON) ShuffleMode.OFF else ShuffleMode.ON)
            })
            com.juscelino.sonivell.ui.components.IconBtn(icons.skipPrevious, stringResource(R.string.cd_previous), actions.onPrevious, size = 56.dp, iconSize = 30.dp)
            Box(Modifier.shadow(18.dp, CircleShape, clip = false, ambientColor = c.accent, spotColor = c.accent)) {
                PlayCircle(state.isPlaying, actions.onTogglePlay, size = 76.dp, iconSize = 30.dp)
            }
            com.juscelino.sonivell.ui.components.IconBtn(icons.skipNext, stringResource(R.string.cd_next), actions.onNext, size = 56.dp, iconSize = 30.dp)
            PlayerModeToggle(
                if (state.repeat == RepeatMode.ONE) icons.repeatOne else icons.repeat,
                stringResource(if (state.repeat == RepeatMode.ONE) R.string.mode_repeat_one else R.string.mode_repeat),
                state.repeat != RepeatMode.OFF,
                onClick = actions.onRepeat,
            )
        }

        val context = LocalContext.current
        if (capsula) {
            PlayModePanel(shuffleMode, smartAvailable, actions.onSetShuffle)
            // Rodapé do Cápsula: a próxima faixa numa pílula que abre a fila, a letra e a saída de som.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UpNextPill(nextTrack, Modifier.weight(1f), actions.onQueue)
                GlassRound(icons.lyrics, stringResource(R.string.lyrics_label), actions.onLyrics)
                GlassRound(icons.device, stringResource(R.string.this_phone), { openOutputSwitcher(context) })
            }
        } else {
            // Rodapé simples: Este telefone · Letra · Fila, sem painel (o Smart subiu para os selos).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GlassPill(icons.device, stringResource(R.string.this_phone), Modifier.weight(1f)) { openOutputSwitcher(context) }
                GlassPill(icons.lyrics, stringResource(R.string.lyrics_label), Modifier.weight(1f), actions.onLyrics)
                GlassPill(icons.queue, stringResource(R.string.np_queue), Modifier.weight(1f), actions.onQueue)
            }
        }
    }
}

/**
 * Painel "Modo de reprodução" do Cápsula: Normal (na ordem) ou Aleatório (só as suas músicas), com
 * a escolha marcada, e o "Aleatório inteligente" (o SMART, que mistura faixas parecidas pelo som)
 * num interruptor à parte, só na edição SMART.
 */
@Composable
private fun PlayModePanel(mode: ShuffleMode, smartAvailable: Boolean, onSet: (ShuffleMode) -> Unit) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    Column(
        Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(24.dp), c.surface, c.outline, elevation = 8.dp, surfaceAlpha = 0.55f)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.cap_play_mode).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = c.textSecondary,
            modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
        )
        ModeRow(icons.list, stringResource(R.string.cap_mode_normal), mode == ShuffleMode.OFF) { onSet(ShuffleMode.OFF) }
        ModeRow(icons.shuffle, stringResource(R.string.cap_mode_shuffle), mode == ShuffleMode.ON) { onSet(ShuffleMode.ON) }
        if (smartAvailable) {
            val on = mode == ShuffleMode.SMART
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .toggleable(value = on, role = Role.Switch, onValueChange = { onSet(if (on) ShuffleMode.OFF else ShuffleMode.SMART) })
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icons.smart, null, Modifier.size(20.dp), tint = if (on) c.accent else c.textPrimary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.cap_mode_smart), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
                    Text(stringResource(R.string.cap_mode_smart_hint), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
                com.juscelino.sonivell.ui.capsula.CapsulaSwitch(on)
            }
        }
    }
}

@Composable
private fun ModeRow(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.textPrimary.copy(alpha = 0.09f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = c.textPrimary)
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = c.textPrimary, modifier = Modifier.weight(1f))
        if (selected) Icon(SonivellTheme.icons.check, null, Modifier.size(18.dp), tint = c.accent)
    }
}

/** "A seguir": a próxima faixa numa pílula de vidro; tocar abre a fila. */
@Composable
private fun UpNextPill(next: Track?, modifier: Modifier, onOpenQueue: () -> Unit) {
    val c = SonivellTheme.colors
    val label = stringResource(R.string.np_queue)
    Row(
        modifier
            .heightIn(min = 56.dp)
            .glassSurface(RoundedCornerShape(percent = 50), c.surface, c.outline, elevation = 6.dp, surfaceAlpha = 0.55f)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onOpenQueue)
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (next != null) AlbumArt(next, Modifier.size(42.dp), corner = 21.dp)
        Column(Modifier.weight(1f).padding(start = if (next == null) 12.dp else 0.dp)) {
            Text(stringResource(R.string.up_next), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            Text(
                next?.let { it.title + " · " + it.artist } ?: stringResource(R.string.cap_queue_end),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(percent = 50)).background(c.textPrimary.copy(alpha = 0.10f)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(SonivellTheme.icons.queue, null, Modifier.size(16.dp), tint = c.textPrimary)
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
        }
    }
}

/** Botão redondo em vidro (fechar, mais opções, curtir). */
@Composable
private fun GlassRound(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = SonivellTheme.colors.textPrimary,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val c = SonivellTheme.colors
    Box(
        Modifier
            .size(44.dp)
            .glassSurface(CircleShape, c.surface, c.outline, elevation = 6.dp, surfaceAlpha = 0.55f)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(iconSize), tint = tint) }
}

/** Atalho do painel de baixo: ícone e rótulo numa pílula translúcida. */
@Composable
private fun GlassPill(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = c.textSecondary)
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Fila pelo som" como chip: aceso na cor de destaque quando ligado, neutro quando desligado. */
@Composable
private fun SmartChip(on: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val label = stringResource(R.string.np_queue_by_sound)
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) c.accent.copy(alpha = 0.18f) else c.textPrimary.copy(alpha = 0.08f))
            .toggleable(value = on, role = Role.Switch, onValueChange = { onClick() })
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SonivellTheme.icons.smart, null, Modifier.size(14.dp), tint = if (on) c.accent else c.textSecondary)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (on) c.textPrimary else c.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Chip(text: String, color: Color, background: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SonivellTheme.colors.textSecondary)
}
