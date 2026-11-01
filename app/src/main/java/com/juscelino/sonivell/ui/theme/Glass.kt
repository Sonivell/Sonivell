package com.juscelino.sonivell.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Liga os cartões em vidro (ver SonivellTheme, que hoje liga em todos os temas). */
val LocalGlass = staticCompositionLocalOf { false }

/**
 * Superfície em vidro do app: fundo translúcido, contorno fino, sombra suave por fora
 * e, com [tone], um brilho fraco na parte de baixo na cor do cartão.
 *
 * Leve de propósito: não há desfoque do que fica atrás (o fundo do app é liso, então a transparência
 * só clareia o tom), o brilho é um gradiente criado uma vez por tamanho ([drawWithCache]) e nada anima.
 * A sombra é a única parte com custo; [elevation] 0 a desliga (linhas de listas longas).
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

/** Item selecionado em vidro (aba atual, botão principal): translúcido na cor de destaque, com contorno. */
fun Modifier.glassAccent(shape: Shape, accent: Color): Modifier = this
    .clip(shape)
    .background(accent.copy(alpha = 0.13f))
    .border(1.dp, accent.copy(alpha = 0.55f), shape)

private const val GLOW_ALPHA = 0.18f

/**
 * Fundo de cartão que segue o tema: em vidro quando ligado ([glassSurface], com sombra leve)
 * e, desligado, o fundo liso de sempre. Usado nos cartões das telas comuns (ajustes, equalizador,
 * estatísticas, fila…), para o vidro valer no app inteiro sem mudar os outros temas.
 */
@Composable
fun Modifier.cardSurface(
    shape: Shape,
    color: Color = SonivellTheme.colors.surface,
    elevation: Dp = 6.dp,
): Modifier =
    if (SonivellTheme.glass) glassSurface(shape, color, SonivellTheme.colors.outline, elevation = elevation)
    else clip(shape).background(color)
