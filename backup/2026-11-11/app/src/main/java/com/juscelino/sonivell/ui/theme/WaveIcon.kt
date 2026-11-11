package com.juscelino.sonivell.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val WAVE = "M3 12c2-5 4-5 6 0s4 5 6 0 4-5 6 0"

/**
 * A onda do "Para você", o único ícone que não vem do Phosphor: é a marca do Sonivell. O mesmo
 * desenho em todos os pesos, só com a espessura de cada um (viewBox 24, traço arredondado).
 */
internal fun waveIcon(name: String, width: Float): ImageVector =
    ImageVector.Builder(
        name = "Onda.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes(WAVE),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
