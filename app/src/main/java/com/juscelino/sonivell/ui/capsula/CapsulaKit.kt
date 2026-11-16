package com.juscelino.sonivell.ui.capsula

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.ArtBlur
import com.juscelino.sonivell.ui.components.miniPlayerGestures
import com.juscelino.sonivell.ui.sonivell.SvTab
import com.juscelino.sonivell.ui.theme.BarFade
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.glassBar
import com.juscelino.sonivell.ui.theme.glassSurface
import dev.chrisbanes.haze.HazeState

/** Forma de pílula: os cantos são metade da altura. */
internal val Pill = RoundedCornerShape(percent = 50)

/** Vidro do Cápsula: superfície translúcida do tema, sem contorno e com sombra só por fora. */
@Composable
internal fun Modifier.capsulaGlass(
    shape: androidx.compose.ui.graphics.Shape,
    elevation: Dp = 8.dp,
    alpha: Float = 0.62f,
): Modifier {
    val c = SonivellTheme.colors
    return if (SonivellTheme.glass) glassSurface(shape, c.surface, c.outline, elevation = elevation, surfaceAlpha = alpha)
    else clip(shape).background(c.surface)
}

/**
 * A barra de baixo do Cápsula: duas pílulas soltas. Em cima, o mini player com a capa desfocada de
 * fundo, o progresso como um anel em volta do play e o botão de próxima; embaixo, as abas em vidro,
 * a atual numa cápsula na cor de destaque. As pílulas desfocam o que passa por baixo ([glassBar]).
 */
@Composable
fun CapsulaDock(
    track: Track?,
    isPlaying: Boolean,
    progress: Float,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    route: String?,
    tabs: List<SvTab>,
    onSelectTab: (String) -> Unit,
    haze: HazeState,
) {
    val c = SonivellTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            // Um véu bem leve sobe atrás das pílulas, para as listas não cortarem seco na borda.
            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to c.background.copy(alpha = 0.55f)))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (track != null) {
            MiniPill(track, isPlaying, progress, onOpenPlayer, onTogglePlay, onNext, onPrevious)
        }
        NavPill(route, tabs, onSelectTab, haze)
    }
}

@Composable
private fun MiniPill(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val openLabel = stringResource(R.string.cd_open_player)
    // Sobre a capa desfocada o texto é sempre claro, em qualquer tema: o véu escuro garante o contraste.
    val ink = Color.White
    val inkSoft = Color.White.copy(alpha = 0.78f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .glassSurface(Pill, Color(0xFF101312), Color.Transparent, elevation = 12.dp, surfaceAlpha = 1f)
            .border(1.dp, Color.White.copy(alpha = 0.10f), Pill)
            .miniPlayerGestures(onNext = onNext, onPrevious = onPrevious, onOpen = onOpen)
            .clickable(onClickLabel = openLabel, onClick = onOpen),
    ) {
        ArtBlur(track, Modifier.matchParentSize(), fallback = Color(0xFF1F2825))
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.42f)))
        Row(
            Modifier.fillMaxSize().padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlbumArt(track, Modifier.size(48.dp).border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape), corner = 24.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.labelMedium, color = inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RingPlay(isPlaying, progress, onTogglePlay, ink)
            val nextLabel = stringResource(R.string.cd_next)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = nextLabel, onClick = onNext)
                    .semantics { contentDescription = nextLabel },
                contentAlignment = Alignment.Center,
            ) { Icon(SonivellTheme.icons.skipNext, null, Modifier.size(22.dp), tint = ink) }
        }
    }
}

/** Play/pausa com o progresso da faixa num anel em volta. */
@Composable
private fun RingPlay(isPlaying: Boolean, progress: Float, onClick: () -> Unit, ink: Color) {
    val label = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play)
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize().padding(3.dp)) {
            val w = 2.5.dp.toPx()
            drawArc(ink.copy(alpha = 0.25f), 0f, 360f, false, style = Stroke(w))
            drawArc(ink, -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(w, cap = StrokeCap.Round))
        }
        Icon(if (isPlaying) SonivellTheme.icons.pause else SonivellTheme.icons.play, null, Modifier.size(20.dp), tint = ink)
    }
}

@Composable
private fun NavPill(route: String?, tabs: List<SvTab>, onSelect: (String) -> Unit, haze: HazeState) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(if (SonivellTheme.glass) Modifier.glassSurface(Pill, Color.Transparent, Color.Transparent, elevation = 12.dp, surfaceAlpha = 1f) else Modifier)
            .clip(Pill)
            .glassBar(haze, BarFade.None)
            .border(1.dp, c.textPrimary.copy(alpha = 0.08f), Pill)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == route
            val color by animateColorAsState(if (selected) c.accent else c.textSecondary, label = "corAba")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(Pill)
                    .background(if (selected) c.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.route) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Icon(if (selected) tab.activeIcon else tab.icon, null, Modifier.size(22.dp), tint = color)
                Text(
                    stringResource(tab.label),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Botão redondo em vidro (voltar, fechar, buscar). */
@Composable
internal fun GlassCircle(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = SonivellTheme.colors.textPrimary) {
    Box(
        modifier
            .size(44.dp)
            .capsulaGlass(CircleShape, elevation = 6.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = tint) }
}

/** Interruptor arredondado do Cápsula (o trilho na cor de destaque quando ligado). */
@Composable
internal fun CapsulaSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val knob by animateDpAsState(if (checked) 23.dp else 3.dp, label = "botaoChave")
    val trackColor by animateColorAsState(if (checked) c.accent else c.textPrimary.copy(alpha = 0.18f), label = "trilhoChave")
    Box(modifier.width(50.dp).height(30.dp).clip(Pill).background(trackColor)) {
        Box(
            Modifier
                .padding(start = knob, top = 3.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Linha com rótulo, apoio opcional e um interruptor; a linha inteira liga e desliga. */
@Composable
internal fun SwitchLine(
    icon: ImageVector?,
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconBubble(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        }
        CapsulaSwitch(checked)
    }
}

/** Ícone num círculo translúcido (linhas das configurações e atalhos). */
@Composable
internal fun IconBubble(icon: ImageVector, tint: Color = SonivellTheme.colors.textTertiary, background: Color = SonivellTheme.colors.textPrimary.copy(alpha = 0.07f), size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = tint)
    }
}

/** Título de seção com "Ver tudo" opcional à direita. */
@Composable
internal fun CapTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = SonivellTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.ExtraBold, color = c.textPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = c.accent,
                modifier = Modifier
                    .clip(Pill)
                    .clickable(role = Role.Button, onClick = onAction)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            )
        }
    }
}

/** Botão de pílula: cheio na cor de destaque (principal) ou em vidro. */
@Composable
internal fun PillButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val c = SonivellTheme.colors
    val bg = when {
        danger -> Color(0xFFDC2626)
        filled -> c.accent
        else -> c.textPrimary.copy(alpha = 0.08f)
    }
    val fg = when {
        danger -> Color.White
        filled -> c.onAccent
        else -> c.textPrimary
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(Pill)
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.5f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Faixa de abas em cápsula (Atual · Sugeridas), num trilho de vidro. */
@Composable
internal fun SegmentedPill(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    Row(modifier.fillMaxWidth().capsulaGlass(Pill, elevation = 4.dp).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(Pill)
                    .background(if (on) c.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = if (on) c.accent else c.textSecondary)
            }
        }
    }
}

/** Filtro em cápsula: o ativo cheio na cor de destaque. */
@Composable
internal fun FilterChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Box(
        Modifier
            .height(38.dp)
            .clip(Pill)
            .background(if (selected) c.accent else c.textPrimary.copy(alpha = 0.07f))
            .then(if (selected) Modifier else Modifier.border(1.dp, c.textPrimary.copy(alpha = 0.10f), Pill))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = if (selected) c.onAccent else c.textTertiary, maxLines = 1)
    }
}

/** Aviso de "Desfazer" em pílula clara, por cima do conteúdo. */
@Composable
internal fun UndoPill(text: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = SonivellTheme.colors
    val ink = if (c.isDark) Color(0xFF0A0D0C) else Color.White
    val bg = if (c.isDark) Color(0xFFF2F5F3) else Color(0xFF1B1F1D)
    Row(
        modifier
            .fillMaxWidth()
            .glassSurface(Pill, bg, Color.Transparent, elevation = 10.dp, surfaceAlpha = 0.96f)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.cap_undo),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = if (c.isDark) Color(0xFF0B6B3A) else c.accent,
            modifier = Modifier
                .clip(Pill)
                .clickable(role = Role.Button, onClick = onUndo)
                .heightIn(min = 44.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** Uma linha com o conteúdo dentro de um cartão de vidro de cantos grandes. */
@Composable
internal fun GlassGroup(modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    val c = SonivellTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .capsulaGlass(RoundedCornerShape(26.dp), elevation = 6.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        content()
    }
}

/** Cabeçalho comum das telas do Cápsula: botão de voltar opcional, título e ações à direita. */
@Composable
internal fun CapsulaHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = SonivellTheme.icons.back,
    backLabel: String = stringResource(R.string.cd_back),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = SonivellTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) GlassCircle(backIcon, backLabel, onBack)
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
            fontWeight = FontWeight.ExtraBold,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}
