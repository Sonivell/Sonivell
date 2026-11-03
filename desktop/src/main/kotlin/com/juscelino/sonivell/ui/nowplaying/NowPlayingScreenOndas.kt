package com.juscelino.sonivell.ui.nowplaying

import com.juscelino.sonivell.ui.components.livePositionMs
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.juscelino.sonivell.compat.stringResource
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
import com.juscelino.sonivell.data.UiStyle
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.playback.PlayerState
import com.juscelino.sonivell.playback.RepeatMode
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.WaveSeekBar
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.glassSurface

/** Por que a faixa atual está tocando ("Por que tocou", no menu de opções). */
sealed interface PlayReason {
    /** Escolhida pelo Smart por soar parecido com [seed]. */
    data class Smart(
        val seed: Track,
        /** Subiu na fila pelo gosto aprendido: período do dia (0 madrugada … 3 noite), -1 = gosto geral, null = não. */
        val taste: Int? = null,
    ) : PlayReason

    /** É a faixa de onde o mix Smart partiu. */
    data object MixStart : PlayReason
    data object Shuffle : PlayReason
    data object Chosen : PlayReason
}

/**
 * Player dos estilos Sonivell, Ondas e Essencial. A estrutura é a mesma de todos os estilos
 * (Tocando de · origem; título em até duas linhas e artista; embaralhar · anterior · play · próxima ·
 * repetir; Smart como interruptor; rodapé Este telefone · Letra · Fila). Muda só a assinatura:
 * - Sonivell: onda como progresso, capa com brilho na cor dela.
 * - Ondas: onda como progresso, Aleatório e Repetir com nome, e a linha "por que" do Smart.
 * - Essencial: capa grande em fundo liso, linha fina de 3 dp, rodapé sem caixa.
 */
@Composable
fun NowPlayingScreenOndas(
    track: Track,
    state: PlayerState,
    shuffleMode: ShuffleMode,
    smartAvailable: Boolean,
    isFavorite: Boolean,
    sourceLabel: String,
    actions: NowPlayingActions,
    reason: PlayReason = PlayReason.Chosen,
    visualizer: Boolean = false,
    style: UiStyle = UiStyle.ONDAS,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val durationMs = state.durationMs.takeIf { it > 0 } ?: track.durationMs
    val essencial = style == UiStyle.ESSENCIAL
    val ondas = style == UiStyle.ONDAS
    val tone by animateColorAsState(artTint(track, ::glowTone), tween(600), label = "tone")

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            // Luz ambiente da capa, bem fraca, no alto da tela (o Essencial fica no fundo liso).
            .then(
                if (essencial) Modifier else Modifier.drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(tone.copy(alpha = 0.20f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.24f),
                            radius = size.width * 1.1f,
                        ),
                    )
                },
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Topo: Tocando de · origem real
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TopRound(icons.collapse, stringResource(R.string.cd_close_player), actions.onClose)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.playing_from).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = c.textSecondary,
                )
                Text(sourceLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TopRound(icons.moreHoriz, stringResource(R.string.cd_more_options), actions.onMore)
        }

        // Capa: quadrada no maior tamanho que cabe.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight, if (essencial) 420.dp else 400.dp)
            Box(
                Modifier
                    .size(side)
                    .then(
                        if (essencial) Modifier else Modifier.drawBehind {
                            drawRect(
                                Brush.radialGradient(
                                    listOf(tone.copy(alpha = 0.42f), Color.Transparent),
                                    center = Offset(size.width / 2f, size.height * 0.62f),
                                    radius = size.width * 0.78f,
                                ),
                                topLeft = Offset(-size.width * 0.3f, -size.height * 0.2f),
                                size = Size(size.width * 1.6f, size.height * 1.5f),
                            )
                        },
                    ),
            ) {
                val corner = when (style) {
                    UiStyle.ESSENCIAL -> 18.dp
                    UiStyle.ONDAS -> 22.dp
                    else -> 24.dp
                }
                AlbumArt(
                    track,
                    Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(corner)),
                    corner = corner,
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

        // Título (até duas linhas) + artista; no Ondas, a linha "por que" do Smart.
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlayerTitle(track, titleSize = if (essencial) 26.sp else 24.sp)
                if (ondas && reason is PlayReason.Smart) WhyLine(stringResource(R.string.why_line_smart, reason.seed.title))
            }
            IconBtn(
                if (isFavorite) icons.liked else icons.like,
                stringResource(if (isFavorite) R.string.cd_unlike else R.string.cd_like),
                actions.onFavorite,
                iconSize = 24.dp,
                tint = if (isFavorite) c.accent else c.textPrimary,
            )
        }

        // Progresso: onda (Sonivell e Ondas) ou linha fina (Essencial); tempo restante com "−".
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (essencial) {
                LineSeekBar(state.livePositionMs, durationMs, actions.onSeek, thickness = 3.dp, fill = c.accent, knob = false)
            } else {
                WaveSeekBar(track.id, state.livePositionMs, durationMs, actions.onSeek)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeLabel(formatDuration(state.livePositionMs))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { FormatText(track) }
                TimeLabel(remainingLabel(state.livePositionMs, durationMs))
            }
        }

        // Controles padrão: embaralhar · anterior · play · próxima · repetir.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PlayerModeToggle(icons.shuffle, stringResource(R.string.mode_shuffle), shuffleMode == ShuffleMode.ON, showLabel = ondas, onClick = {
                actions.onSetShuffle(if (shuffleMode == ShuffleMode.ON) ShuffleMode.OFF else ShuffleMode.ON)
            })
            IconBtn(icons.skipPrevious, stringResource(R.string.cd_previous), actions.onPrevious, size = 52.dp, iconSize = 30.dp)
            PlayCircle(state.isPlaying, actions.onTogglePlay, size = if (essencial) 72.dp else if (ondas) 74.dp else 76.dp, iconSize = 30.dp)
            IconBtn(icons.skipNext, stringResource(R.string.cd_next), actions.onNext, size = 52.dp, iconSize = 30.dp)
            PlayerModeToggle(
                if (state.repeat == RepeatMode.ONE) icons.repeatOne else icons.repeat,
                stringResource(if (state.repeat == RepeatMode.ONE) R.string.mode_repeat_one else R.string.mode_repeat),
                state.repeat != RepeatMode.OFF,
                showLabel = ondas,
                onClick = actions.onRepeat,
            )
        }

        // Smart como interruptor (no Essencial, entre duas linhas finas).
        if (smartAvailable) {
            if (essencial) HorizontalDivider(color = c.outline)
            SmartQueueSwitch(
                on = shuffleMode == ShuffleMode.SMART,
                onToggle = { on -> actions.onSetShuffle(if (on) ShuffleMode.SMART else ShuffleMode.OFF) },
            )
            if (essencial) HorizontalDivider(color = c.outline)
        }

        // Rodapé: Este telefone · Letra · Fila. Em caixa de vidro no Sonivell e no Ondas; solto no Essencial.
        PlayerFooter(
            onLyrics = actions.onLyrics,
            onQueue = actions.onQueue,
            modifier = if (essencial) Modifier else Modifier
                .glassSurface(RoundedCornerShape(20.dp), c.surface, c.outline, tone = tone, elevation = 8.dp)
                .padding(horizontal = 4.dp),
        )
    }
}

/** Linha "por que" do estilo Ondas: ícone do Smart e uma frase curta. */
@Composable
fun WhyLine(text: String, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(SonivellTheme.icons.smart, null, Modifier.size(14.dp), tint = c.accent)
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botão redondo do topo do player (fechar, mais opções). */
@Composable
private fun TopRound(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Box(
        Modifier
            .size(44.dp)
            .then(if (SonivellTheme.glass) Modifier.glassSurface(CircleShape, c.surface, c.outline, elevation = 6.dp) else Modifier.clip(CircleShape).border(1.dp, c.outline, CircleShape))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = c.textPrimary) }
}

@Composable
private fun TimeLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SonivellTheme.colors.textSecondary)
}

/**
 * Explicação completa de "Por que tocou", aberta pelo menu de opções do player (em todos os estilos).
 */
@Composable
fun WhyContent(reason: PlayReason, sourceLabel: String, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(
                when (reason) {
                    is PlayReason.Smart, PlayReason.MixStart -> SonivellTheme.icons.smart
                    PlayReason.Shuffle -> SonivellTheme.icons.shuffle
                    PlayReason.Chosen -> SonivellTheme.icons.musicNote
                },
                null,
                Modifier.size(18.dp),
                tint = c.accent,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when (reason) {
                    is PlayReason.Smart -> stringResource(R.string.why_smart, reason.seed.title, reason.seed.artist)
                    PlayReason.MixStart -> stringResource(R.string.why_seed)
                    PlayReason.Shuffle -> stringResource(R.string.why_shuffle, sourceLabel)
                    PlayReason.Chosen -> stringResource(R.string.why_source, sourceLabel)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary,
            )
            if (reason is PlayReason.Smart) {
                reason.taste?.let { part ->
                    Text(
                        stringResource(
                            when (part) {
                                0 -> R.string.why_taste_0
                                1 -> R.string.why_taste_1
                                2 -> R.string.why_taste_2
                                3 -> R.string.why_taste_3
                                else -> R.string.why_taste_any
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.textTertiary,
                    )
                }
                Text(stringResource(R.string.why_smart_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
    }
}
