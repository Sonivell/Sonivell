package com.juscelino.sonivell.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.data.UiStyle
import com.juscelino.sonivell.ui.theme.SonivellTheme

/**
 * Interruptor do app: o do Material nos estilos de sempre e, no Fluent, o interruptor fino do
 * Fluent Design (trilho de 40 × 20 dp com bolinha de 12 dp). Mesma assinatura do [Switch], para
 * trocar sem mexer no resto da linha.
 */
@Composable
fun SvSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: SwitchColors = SwitchDefaults.colors(),
) {
    if (SonivellTheme.style != UiStyle.FLUENT) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, colors = colors)
        return
    }
    val c = SonivellTheme.colors
    val track by animateColorAsState(if (checked) c.accent else androidx.compose.ui.graphics.Color.Transparent, label = "trilho")
    val thumb by animateColorAsState(if (checked) c.onAccent else c.textSecondary, label = "bolinha")
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, label = "posição")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    } else Modifier
    // A área de toque segue com 48 dp, como a do Material; só o desenho é menor.
    Box(modifier.then(toggle).size(width = 52.dp, height = 48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 40.dp, height = 20.dp)
                .background(track, CircleShape)
                .then(if (checked) Modifier else Modifier.border(1.dp, c.textSecondary, CircleShape)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(start = 4.dp + offset)
                    .size(12.dp)
                    .background(thumb, CircleShape),
            )
        }
    }
}
