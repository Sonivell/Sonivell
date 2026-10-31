package com.juscelino.sonivell.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.ui.theme.AccentChoice
import com.juscelino.sonivell.ui.theme.SonivellColors
import kotlin.math.PI
import kotlin.math.sin

/** As quatro cores das capas geradas, na versão legível para o tema atual (lima, azul, laranja, lilás). */
fun glassTone(index: Int, colors: SonivellColors): Color {
    val tones = listOf(AccentChoice.Lima, AccentChoice.Azul, AccentChoice.Laranja, AccentChoice.Lilas)
    val t = tones[Math.floorMod(index, tones.size)]
    return if (colors.isDark) t.dark else t.light
}

/**
 * Superfície de vidro: sombra, fundo da superfície com um brilho translúcido de [tone]
 * no canto inferior esquerdo, reflexo claro no topo e borda fina.
 */
fun Modifier.glassSurface(tone: Color, shape: Shape, colors: SonivellColors): Modifier = this
    .shadow(6.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(colors.surface)
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                listOf(tone.copy(alpha = if (colors.isDark) 0.20f else 0.28f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height),
                radius = size.maxDimension * 0.9f,
            ),
        )
        drawRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = if (colors.isDark) 0.05f else 0.35f), Color.Transparent),
                endY = size.height * 0.6f,
            ),
        )
    }
    .border(
        1.dp,
        Brush.verticalGradient(
            if (colors.isDark) listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.04f))
            else listOf(Color.White.copy(alpha = 0.9f), colors.outline),
        ),
        shape,
    )

/** Onda curta na cor do bloco, como nos atalhos do tema Sonivell. */
@Composable
fun GlassWave(tone: Color, modifier: Modifier = Modifier, turns: Float = 2f) {
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val mid = size.height / 2f
        val amp = (size.height - stroke) / 2f
        val path = Path()
        var x = stroke / 2f
        path.moveTo(x, mid)
        while (x <= size.width - stroke / 2f) {
            val t = x / size.width
            path.lineTo(x, mid - sin(t * turns * 2 * PI).toFloat() * amp)
            x += 1.5f
        }
        drawPath(path, tone, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
