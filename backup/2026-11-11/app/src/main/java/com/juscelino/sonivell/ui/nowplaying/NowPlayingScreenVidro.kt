package com.juscelino.sonivell.ui.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val durationMs = state.durationMs.takeIf { it > 0 } ?: track.durationMs
    val tone by animateColorAsState(artTint(track, ::glowTone), tween(600), label = "tomVidro")
    // Com o fundo da capa ligado, a capa desfocada já está atrás; senão, o fundo liso do tema.
    val backdrop = LocalGlassMaterial.current.backdrop
    val glowStrong = if (c.isDark) 0.34f else 0.22f
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
            GlassRound(
                if (isFavorite) icons.liked else icons.like,
                stringResource(if (isFavorite) R.string.cd_unlike else R.string.cd_like),
                actions.onFavorite,
                tint = if (isFavorite) c.accent else c.textPrimary,
                iconSize = 22.dp,
            )
        }

        // Progresso: barra lisa na cor de destaque, botão claro; selos entre os tempos.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LineSeekBar(
                state.positionMs, durationMs, actions.onSeek,
                thickness = 6.dp,
                fill = c.accent,
                track = c.textPrimary.copy(alpha = 0.18f),
                knobColor = if (c.isDark) Color.White else c.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeText(formatDuration(state.positionMs))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    spatialLabel?.let { Chip(it, c.accent, c.accent.copy(alpha = 0.14f)) }
                    track.formatLabel?.let { Chip(it, c.textTertiary, c.textPrimary.copy(alpha = 0.08f)) }
                }
                TimeText(remainingLabel(state.positionMs, durationMs))
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

        // Painel de vidro: Smart em cima, atalhos embaixo.
        Column(
            Modifier
                .fillMaxWidth()
                .glassSurface(RoundedCornerShape(24.dp), c.surface, c.outline, tone = tone, elevation = 8.dp)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (smartAvailable) {
                SmartQueueSwitch(
                    on = shuffleMode == ShuffleMode.SMART,
                    onToggle = { on -> actions.onSetShuffle(if (on) ShuffleMode.SMART else ShuffleMode.OFF) },
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            val context = LocalContext.current
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GlassPill(icons.device, stringResource(R.string.this_phone), Modifier.weight(1f)) { openOutputSwitcher(context) }
                GlassPill(icons.lyrics, stringResource(R.string.lyrics_label), Modifier.weight(1f), actions.onLyrics)
                GlassPill(icons.queue, stringResource(R.string.np_queue), Modifier.weight(1f), actions.onQueue)
            }
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
            .background(c.textPrimary.copy(alpha = 0.06f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = c.textPrimary)
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
