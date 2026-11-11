package com.juscelino.sonivell.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ui.theme.SonivellTheme

/** Fundo grafite do ícone do app (o mesmo da camada de fundo do ícone adaptativo). */
private val IconBackground = Color(0xFF0E0F12)

/**
 * O ícone do Sonivell como no lançador: a onda lima sobre o quadrado grafite. Usa o mesmo desenho da
 * abertura do sistema ([R.drawable.ic_splash]), que segue a curva do ícone adaptativo; a camada tem
 * 108 de lado e o ícone visível é o miolo de 72, por isso o desenho entra com 1,5× o tamanho.
 * [glow] acende uma luz lima fraca atrás do ícone, como a luz da capa no player.
 */
@Composable
fun AppIcon(size: Dp, modifier: Modifier = Modifier, glow: Boolean = false) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(size * 0.24f)
    val label = stringResource(R.string.app_name)
    Box(
        modifier
            .size(size)
            .then(
                if (!glow) Modifier else Modifier.drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(c.accent.copy(alpha = 0.28f), Color.Transparent),
                            center = Offset(this.size.width / 2f, this.size.height / 2f),
                            radius = this.size.minDimension * 1.1f,
                        ),
                        radius = this.size.minDimension * 1.1f,
                    )
                },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(IconBackground)
                .border(if (size < 40.dp) 0.5.dp else 1.dp, Color.White.copy(alpha = 0.08f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_splash), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
        }
    }
}
