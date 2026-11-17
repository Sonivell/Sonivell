package com.juscelino.sonivell.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cantos do app. Os estilos usam raios generosos; o Fluent troca todos pelos cantos discretos do
 * Fluent Design: 8 dp em cartões e painéis, 4 dp em botões e capas pequenas. Linhas finas (até
 * 3 dp, como barras de progresso), pílulas e círculos ficam como estão.
 *
 * É um estado global (e não um CompositionLocal) para valer também onde a forma é criada fora de
 * uma função @Composable, como em modificadores e valores padrão de parâmetros. [SonivellTheme]
 * atualiza o valor quando o estilo muda, e as telas recompõem com os cantos novos.
 */
object SvShapes {
    var fluent by mutableStateOf(false)
        internal set
}

/** Raio de canto no estilo atual; use no lugar de `RoundedCornerShape(x.dp)`. */
fun rounded(radius: Dp): RoundedCornerShape = RoundedCornerShape(cornerRadius(radius))

/** O raio [radius] traduzido para o estilo atual. */
fun cornerRadius(radius: Dp): Dp = when {
    !SvShapes.fluent -> radius
    // Linhas finas e formas que pedem círculo ou pílula (avatares, chips, 999 dp) ficam como estão.
    radius <= 3.dp || radius >= 40.dp -> radius
    radius >= 12.dp -> 8.dp
    else -> 4.dp
}
