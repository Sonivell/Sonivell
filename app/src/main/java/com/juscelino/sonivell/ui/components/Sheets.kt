package com.juscelino.sonivell.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.theme.SonivellTheme

/** Uma opção de menu: ícone, texto e ação. */
data class MenuOption(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Menu em folha inferior (bottom sheet). [header] aparece no topo, por exemplo a faixa
 * escolhida. Cada opção fecha o menu antes de agir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(
    options: List<MenuOption>,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val c = SonivellTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surface,
        contentColor = c.textPrimary,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            if (header != null) {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) { header() }
                HorizontalDivider(color = c.outline, modifier = Modifier.padding(vertical = 8.dp))
            }
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button) {
                            onDismiss()
                            option.onClick()
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(option.icon, null, Modifier.size(22.dp), tint = c.textTertiary)
                    Text(option.label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                }
            }
        }
    }
}

/** Cabeçalho do menu de uma faixa: capa, título e artista. */
@Composable
fun TrackHeader(track: Track) {
    val c = SonivellTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AlbumArt(track, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Escolher a playlist de destino (ou criar uma nova) para [count] faixa(s). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SonivellTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.textPrimary,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.add_to_playlist),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    SheetRow(Icons.Rounded.Add, stringResource(R.string.new_playlist), null) {
                        onDismiss(); onNew()
                    }
                }
                items(playlists, key = { it.id }) { p ->
                    SheetRow(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        p.name,
                        pluralStringResource(R.plurals.songs_count, p.trackIds.size, p.trackIds.size),
                    ) { onDismiss(); onPick(p) }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(20.dp), tint = c.textTertiary) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}

/** Caixa para digitar um nome (nova playlist, renomear). */
@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String = stringResource(R.string.save),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SonivellTheme.colors
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val valid = text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, color = c.textPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.name)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) { onConfirm(text.trim()); onDismiss() } }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    cursorColor = c.accent,
                    focusedLabelColor = c.accent,
                    focusedTextColor = c.textPrimary,
                    unfocusedTextColor = c.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(text.trim()); onDismiss() }) {
                Text(confirmLabel, color = if (valid) c.accent else c.textSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.textTertiary) }
        },
    )
}

/** Confirmação para ações que não têm volta (ex.: excluir playlist). */
@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = SonivellTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, color = c.textPrimary) },
        text = { Text(message, color = c.textSecondary) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = c.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.textTertiary) }
        },
    )
}
