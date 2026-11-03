package com.juscelino.sonivell.ui.components

import androidx.compose.ui.graphics.Color
import com.juscelino.sonivell.ui.theme.SonivellColors

/**
 * Tom dos cartões de atalho e de navegação. Era uma de quatro cores por posição, sem significado;
 * a proposta visual deixa a cor só para ação e estado, então o tom é neutro em todos os cartões.
 * [index] fica para os chamadores não mudarem.
 */
@Suppress("UNUSED_PARAMETER")
fun glassTone(index: Int, colors: SonivellColors): Color = colors.textSecondary
