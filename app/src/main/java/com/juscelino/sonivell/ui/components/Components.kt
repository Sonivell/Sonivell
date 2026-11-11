package com.juscelino.sonivell.ui.components

import androidx.compose.ui.res.stringResource
import com.juscelino.sonivell.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.formatDuration
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.glassAccent
import com.juscelino.sonivell.ui.theme.glassSurface
import com.juscelino.sonivell.ui.theme.LocalInDock
import com.juscelino.sonivell.ui.theme.cardSurface

/** Botão de ícone com área de toque de no mínimo 44 dp. */
@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = SonivellTheme.colors.textPrimary,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .then(
                // Com fundo, no vidro o botão redondo também fica em vidro (voltar, etc.). Na cor de destaque
                // (play, ação principal) fica cheio e sem anel: o vidro deixava a cor apagada, verde-oliva.
                if (SonivellTheme.glass && background != Color.Transparent && background != SonivellTheme.colors.accent) {
                    Modifier.glassSurface(CircleShape, background, SonivellTheme.colors.outline, elevation = 4.dp)
                } else {
                    Modifier.clip(CircleShape).background(background)
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = SonivellTheme.colors.textSecondary,
    )
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val glass = SonivellTheme.glass
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .height(36.dp)
            .then(
                when {
                    // Vidro: o escolhido fica translúcido na cor de destaque, como a aba atual.
                    glass && selected -> Modifier.glassAccent(shape, c.accent)
                    glass -> Modifier.glassSurface(shape, c.surface, c.outline, elevation = 0.dp)
                    selected -> Modifier.clip(shape).background(c.accent)
                    else -> Modifier.clip(shape).background(c.surface)
                }
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) (if (glass) c.accent else c.onAccent) else c.textTertiary,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    /** Abre o menu da faixa (botão ⋮ e toque longo). */
    onMore: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = SonivellTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AlbumArt(track, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (highlighted) c.accent else c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) trailing() else Text(
            formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
        )
        if (onMore != null) {
            IconBtn(
                SonivellTheme.icons.more,
                stringResource(R.string.cd_track_options, track.title),
                onMore,
                tint = c.textSecondary,
                iconSize = 20.dp,
                size = 40.dp,
            )
        }
    }
}

/**
 * Mini player 2.0: capa, título, curtir e play, com uma linha de progresso fina na base
 * do cartão. Fica acima da barra de navegação em todas as telas principais.
 */
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    liked: Boolean = false,
    onLike: (() -> Unit)? = null,
    /** Contraste: o cartão no tom da capa, com texto, coração e pausa brancos. */
    tone: Color? = null,
) {
    val c = if (tone == null) SonivellTheme.colors else tonedColors(SonivellTheme.colors, tone)
    val icons = SonivellTheme.icons
    val openPlayer = stringResource(R.string.cd_open_player)
    // No dock a superfície é do dock; o tom da capa (Contraste) continua como fundo da faixa do mini player.
    val docked = LocalInDock.current
    Box(
        if (docked) modifier.then(if (tone != null) Modifier.background(tone) else Modifier)
        else modifier.padding(horizontal = 8.dp).cardSurface(RoundedCornerShape(12.dp), tone ?: c.miniPlayer, elevation = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = openPlayer, onClick = onOpen)
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlbumArt(track, Modifier.size(40.dp), corner = 6.dp)
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onLike != null) {
                IconBtn(
                    icon = if (liked) icons.liked else icons.like,
                    contentDescription = stringResource(if (liked) R.string.cd_unlike else R.string.cd_like),
                    onClick = onLike,
                    tint = if (liked && tone == null) c.accent else c.textPrimary,
                    iconSize = 22.dp,
                )
            }
            IconBtn(
                icon = if (isPlaying) icons.pause else icons.play,
                contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                onClick = onTogglePlay,
                tint = c.textPrimary,
                iconSize = 26.dp,
            )
        }
        // Progresso: linha de 2 dp colada na base do cartão.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(c.textPrimary.copy(alpha = 0.18f))
        ) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(c.textPrimary))
        }
    }
}

/** Título de seção grande (Para você, Busca): mais legível que o rótulo em maiúsculas. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            color = SonivellTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Mini player do Clássico: capa, título, artista, play no lima e a barra de progresso dentro do cartão. */
@Composable
fun MiniPlayerClassica(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = SonivellTheme.colors
    val openPlayer = stringResource(R.string.cd_open_player)
    val docked = LocalInDock.current
    Column(
        modifier
            .padding(horizontal = if (docked) 0.dp else 12.dp)
            .fillMaxWidth()
            .then(if (docked) Modifier else Modifier.cardSurface(RoundedCornerShape(16.dp), c.miniPlayer, elevation = 12.dp))
            .clickable(onClickLabel = openPlayer, onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AlbumArt(track, Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(
                icon = if (isPlaying) SonivellTheme.icons.pause else SonivellTheme.icons.play,
                contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                onClick = onTogglePlay,
                // No dock o play fica neutro: o destaque é da ação principal de cada tela.
                background = if (docked) c.textPrimary else c.accent,
                tint = if (docked) c.background else c.onAccent,
                iconSize = 20.dp,
            )
        }
        Box(
            Modifier
                .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.track)
        ) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(c.accent))
        }
    }
}

/**
 * Botão em pílula com ícone e rótulo (Aleatório, + Playlist): a proposta visual pede nome nos botões
 * principais. [primary] = cheio na cor de destaque; senão, contorno discreto.
 */
@Composable
fun LabeledPill(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .height(40.dp)
            .clip(shape)
            .then(if (primary) Modifier.background(c.accent) else Modifier.background(c.surface))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (primary) c.onAccent else c.textPrimary)
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (primary) c.onAccent else c.textPrimary, maxLines = 1)
    }
}

/**
 * Cores sobre o tom da capa (Contraste): superfície no tom, texto e ícones brancos, linhas translúcidas.
 * O tom vem escurecido da capa, então o branco fica sempre legível.
 */
fun tonedColors(base: com.juscelino.sonivell.ui.theme.SonivellColors, tone: Color) = base.copy(
    background = tone,
    surface = tone,
    surfaceLow = tone,
    surfaceHigh = Color.White.copy(alpha = 0.12f),
    miniPlayer = tone,
    outline = Color.White.copy(alpha = 0.16f),
    track = Color.White.copy(alpha = 0.25f),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.72f),
    textTertiary = Color.White.copy(alpha = 0.86f),
    isDark = true,
)
