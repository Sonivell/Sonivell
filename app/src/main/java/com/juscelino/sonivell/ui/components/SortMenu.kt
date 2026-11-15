package com.juscelino.sonivell.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ui.theme.SonivellTheme

/**
 * Botão "ordenar" em cápsula neutra (ícone + ordem atual); abre um menu com as opções da lista,
 * a atual marcada, e a opção de inverter a ordem.
 */
@Composable
fun <T> SortMenuButton(
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    reversed: Boolean,
    onSelect: (T) -> Unit,
    onToggleReversed: () -> Unit,
) {
    val nc = SonivellTheme.colors
    val icons = SonivellTheme.icons
    var open by remember { mutableStateOf(false) }
    val current = stringResource(label(selected))
    Box {
        Row(
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(nc.surface)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_sort_by, current)) { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icons.sort, null, Modifier.size(16.dp), tint = nc.textSecondary)
            Text(current, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = nc.textPrimary, maxLines = 1)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = nc.surface,
        ) {
            Text(
                stringResource(R.string.sort_menu_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = nc.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            options.forEach { o ->
                val on = o == selected
                DropdownMenuItem(
                    text = { Text(stringResource(label(o)), fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, color = nc.textPrimary) },
                    trailingIcon = { if (on) Icon(icons.check, null, Modifier.size(18.dp), tint = nc.accent) },
                    onClick = { open = false; if (!on) onSelect(o) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = nc.outline)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_reverse), fontWeight = if (reversed) FontWeight.ExtraBold else FontWeight.Medium, color = nc.textPrimary) },
                trailingIcon = { if (reversed) Icon(icons.check, null, Modifier.size(18.dp), tint = nc.accent) },
                onClick = { open = false; onToggleReversed() },
            )
        }
    }
}
