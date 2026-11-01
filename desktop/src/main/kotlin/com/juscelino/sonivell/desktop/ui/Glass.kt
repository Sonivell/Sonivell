package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cartões em vidro, a mesma receita do app Android (`ui/theme/Glass.kt`): fundo translúcido,
 * contorno fino, sombra suave por fora e, com [tone], um brilho fraco embaixo na cor do cartão.
 *
 * Leve de propósito: sem desfoque (o fundo do app é liso, então a transparência só clareia o tom),
 * o brilho é criado uma vez por tamanho ([drawWithCache]) e nada anima. A sombra é a única parte
 * com custo; [elevation] 0 a desliga (linhas de listas longas).
 */
fun Modifier.glassSurface(
    shape: Shape,
    surface: Color,
    outline: Color,
    tone: Color? = null,
    elevation: Dp = 10.dp,
    surfaceAlpha: Float = 0.72f,
): Modifier = composed {
    // Escuro: como sempre foi. Claro: a sombra do sistema seria desenhada também por baixo do vidro
    // translúcido e apareceria como um bloco cinza dentro do cartão; no claro a sombra é desenhada só
    // por fora da forma ([sombraClara]) e o vidro fica um pouco mais opaco para seguir destacado.
    val dark = SonivellTheme.colors.isDark
    val alpha = if (dark) surfaceAlpha else maxOf(surfaceAlpha, LIGHT_SURFACE_ALPHA)
    Modifier
        .then(
            when {
                elevation <= 0.dp -> Modifier
                dark -> Modifier.shadow(elevation, shape, clip = false)
                else -> Modifier.sombraClara(shape, elevation)
            },
        )
        .clip(shape)
        .background(surface.copy(alpha = surface.alpha * alpha))
        .then(
            if (tone == null) Modifier else Modifier.drawWithCache {
                val brush = Brush.radialGradient(
                    colors = listOf(tone.copy(alpha = GLOW_ALPHA), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 1.15f),
                    radius = maxOf(size.width, size.height) * 0.75f,
                )
                onDrawBehind { drawRect(brush) }
            },
        )
        .border(1.dp, outline, shape)
}

/**
 * Sombra do modo claro, só do lado de fora de [shape]: camadas de contorno cada vez mais largas e
 * fracas, um pouco deslocadas para baixo, com o miolo da forma recortado. Assim nada escurece o
 * vidro por dentro. Criada uma vez por tamanho ([drawWithCache]).
 */
private fun Modifier.sombraClara(shape: Shape, elevation: Dp): Modifier = drawWithCache {
    val corpo = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val alcance = elevation.toPx() * 0.9f
    val queda = elevation.toPx() * 0.25f
    onDrawBehind {
        clipPath(corpo, ClipOp.Difference) {
            translate(top = queda) {
                for (i in SHADOW_LAYERS downTo 1) {
                    drawPath(
                        corpo,
                        Color.Black.copy(alpha = LIGHT_SHADOW_ALPHA / SHADOW_LAYERS),
                        style = Stroke(width = alcance * 2f * i / SHADOW_LAYERS, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

private const val LIGHT_SURFACE_ALPHA = 0.86f
private const val LIGHT_SHADOW_ALPHA = 0.07f
private const val SHADOW_LAYERS = 16

/** Item selecionado em vidro (aba atual, filtro ativo): translúcido na cor de destaque, com contorno. */
fun Modifier.glassAccent(shape: Shape, accent: Color): Modifier = this
    .clip(shape)
    .background(accent.copy(alpha = 0.13f))
    .border(1.dp, accent.copy(alpha = 0.55f), shape)

private const val GLOW_ALPHA = 0.18f

/** Painel ou cartão em vidro nas cores do tema (barra lateral, fila, player, cartões das telas). */
@Composable
fun Modifier.cardSurface(
    shape: Shape,
    color: Color = SonivellTheme.colors.surface,
    elevation: Dp = 6.dp,
    tone: Color? = null,
): Modifier = glassSurface(shape, color, SonivellTheme.colors.outline, tone = tone, elevation = elevation)

/**
 * Cartão em vidro clicável: como [hoverClick], mas o fundo é sempre vidro e fica um tom mais claro
 * ao passar o mouse. Para os cartões que antes tinham fundo liso (atalhos, gêneros, playlists…).
 */
@Composable
fun Modifier.glassClick(
    shape: Shape,
    base: Color = SonivellTheme.colors.surface,
    hover: Color = SonivellTheme.colors.surfaceHigh,
    elevation: Dp = 6.dp,
    label: String? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return this
        .glassSurface(shape, if (hovered) hover else base, SonivellTheme.colors.outline, elevation = elevation)
        .hoverable(source)
        .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
        .pointerHoverIcon(PointerIcon.Hand)
        .let { if (label != null) it.semantics { contentDescription = label } else it }
}

/** Logo do app: o mesmo ícone da janela e do pacote (o escuro, do SMART). */
@Composable
fun AppLogo(size: Dp) {
    val logo = remember { runCatching { BitmapPainter(useResource("icon.png", ::loadImageBitmap)) }.getOrNull() }
    if (logo != null) Image(logo, null, Modifier.size(size))
    else Box(Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(SonivellTheme.colors.accent))
}
