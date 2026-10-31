package com.juscelino.sonivell.ui.components

import androidx.compose.ui.graphics.Color
import com.juscelino.sonivell.ui.theme.AccentChoice
import com.juscelino.sonivell.ui.theme.SonivellColors

/** As quatro cores das capas geradas, na versão legível para o tema atual (lima, azul, laranja, lilás). */
fun glassTone(index: Int, colors: SonivellColors): Color {
    val tones = listOf(AccentChoice.Lima, AccentChoice.Azul, AccentChoice.Laranja, AccentChoice.Lilas)
    val t = tones[Math.floorMod(index, tones.size)]
    return if (colors.isDark) t.dark else t.light
}
