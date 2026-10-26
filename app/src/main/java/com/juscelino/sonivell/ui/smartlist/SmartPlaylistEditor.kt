package com.juscelino.sonivell.ui.smartlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.RuleField
import com.juscelino.sonivell.data.RuleOp
import com.juscelino.sonivell.data.SmartPlaylist
import com.juscelino.sonivell.data.SmartRule
import com.juscelino.sonivell.data.SmartSort
import com.juscelino.sonivell.ui.components.Chip
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.SectionLabel
import com.juscelino.sonivell.ui.theme.SonivellTheme

private enum class Kind { Text, Number, Bool }

private fun RuleField.kind() = when (this) {
    RuleField.Genero, RuleField.Artista, RuleField.Album, RuleField.Titulo, RuleField.Formato -> Kind.Text
    RuleField.Ano, RuleField.DuracaoMin, RuleField.AdicionadaDias, RuleField.Reproducoes -> Kind.Number
    RuleField.NuncaTocada, RuleField.Curtida -> Kind.Bool
}

private fun defaultRule(field: RuleField) = when (field.kind()) {
    Kind.Text -> SmartRule(field, RuleOp.Contem)
    Kind.Number -> SmartRule(field, RuleOp.AoMenos, a = if (field == RuleField.Ano) 2000 else 1)
    Kind.Bool -> SmartRule(field, RuleOp.Sim)
}

private val fieldLabels = mapOf(
    RuleField.Genero to R.string.rule_genre,
    RuleField.Artista to R.string.rule_artist,
    RuleField.Album to R.string.rule_album,
    RuleField.Titulo to R.string.rule_title,
    RuleField.Formato to R.string.rule_format,
    RuleField.Ano to R.string.rule_year,
    RuleField.DuracaoMin to R.string.rule_duration,
    RuleField.AdicionadaDias to R.string.rule_added_days,
    RuleField.Reproducoes to R.string.rule_plays,
    RuleField.NuncaTocada to R.string.rule_never_played,
    RuleField.Curtida to R.string.rule_liked,
)

private val opLabels = mapOf(
    RuleOp.Contem to R.string.op_contains,
    RuleOp.NaoContem to R.string.op_not_contains,
    RuleOp.Igual to R.string.op_equals,
    RuleOp.AoMenos to R.string.op_at_least,
    RuleOp.NoMaximo to R.string.op_at_most,
    RuleOp.Entre to R.string.op_between,
    RuleOp.Sim to R.string.op_yes,
    RuleOp.Nao to R.string.op_no,
)

private val sortLabels = mapOf(
    SmartSort.Aleatoria to R.string.ssort_random,
    SmartSort.MaisOuvidas to R.string.auto_most,
    SmartSort.MenosOuvidas to R.string.auto_least,
    SmartSort.Recentes to R.string.sort_recent,
    SmartSort.Titulo to R.string.sort_title,
    SmartSort.Artista to R.string.sort_artist,
    SmartSort.Ano to R.string.sort_year,
)

data class SmartEditorActions(
    val onBack: () -> Unit,
    val onSave: (SmartPlaylist) -> Unit,
    val onDelete: (() -> Unit)?,
    /** Quantas faixas a playlist teria com estas regras (prévia ao vivo). */
    val preview: (SmartPlaylist) -> Int,
)

@Composable
fun SmartPlaylistEditor(initial: SmartPlaylist, contentPadding: PaddingValues, actions: SmartEditorActions) {
    val c = SonivellTheme.colors
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val count = actions.preview(draft)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBtn(SonivellTheme.icons.back, stringResource(R.string.cd_back), actions.onBack, background = c.surface)
            Text(
                stringResource(if (initial.id > 0) R.string.edit_smart_playlist else R.string.new_smart_playlist),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            actions.onDelete?.let { IconBtn(SonivellTheme.icons.delete, stringResource(R.string.delete), it, background = c.surface) }
        }

        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text(stringResource(R.string.name)) },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        SectionLabel(stringResource(R.string.rules))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.match_all), selected = draft.matchAll) { draft = draft.copy(matchAll = true) }
            Chip(stringResource(R.string.match_any), selected = !draft.matchAll) { draft = draft.copy(matchAll = false) }
        }
        draft.rules.forEachIndexed { index, rule ->
            RuleEditor(
                rule = rule,
                onChange = { new -> draft = draft.copy(rules = draft.rules.toMutableList().also { it[index] = new }) },
                onRemove = { draft = draft.copy(rules = draft.rules.toMutableList().also { it.removeAt(index) }) },
            )
        }
        Chip("+ " + stringResource(R.string.add_rule), selected = true) {
            draft = draft.copy(rules = draft.rules + defaultRule(RuleField.Genero))
        }

        SectionLabel(stringResource(R.string.order))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmartSort.entries.forEach { s ->
                Chip(stringResource(sortLabels.getValue(s)), selected = draft.sort == s) { draft = draft.copy(sort = s) }
            }
        }

        SectionLabel(stringResource(R.string.limit))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(stringResource(R.string.limit_songs), draft.limitCount, Modifier.weight(1f)) { draft = draft.copy(limitCount = it) }
            NumberField(stringResource(R.string.limit_minutes), draft.limitMinutes, Modifier.weight(1f)) { draft = draft.copy(limitMinutes = it) }
        }
        Text(stringResource(R.string.limit_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)

        Text(
            pluralStringResource(R.plurals.songs_count, count, count),
            style = MaterialTheme.typography.titleMedium,
            color = c.accent,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (draft.name.isNotBlank()) c.accent else c.surfaceHigh)
                .clickable(enabled = draft.name.isNotBlank(), role = Role.Button) {
                    actions.onSave(draft.copy(name = draft.name.trim()))
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge, color = if (draft.name.isNotBlank()) c.onAccent else c.textSecondary)
        }
    }
}

@Composable
private fun RuleEditor(rule: SmartRule, onChange: (SmartRule) -> Unit, onRemove: () -> Unit) {
    val c = SonivellTheme.colors
    var fieldMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Chip(stringResource(fieldLabels.getValue(rule.field)) + "  ▾", selected = false) { fieldMenu = true }
                DropdownMenu(expanded = fieldMenu, onDismissRequest = { fieldMenu = false }) {
                    RuleField.entries.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(stringResource(fieldLabels.getValue(f))) },
                            onClick = { fieldMenu = false; if (f != rule.field) onChange(defaultRule(f)) },
                        )
                    }
                }
            }
            IconBtn(SonivellTheme.icons.close, stringResource(R.string.remove_rule), onRemove, tint = c.textSecondary, iconSize = 18.dp)
        }
        val ops = when (rule.field.kind()) {
            Kind.Text -> listOf(RuleOp.Contem, RuleOp.NaoContem, RuleOp.Igual)
            Kind.Number -> listOf(RuleOp.AoMenos, RuleOp.NoMaximo, RuleOp.Entre, RuleOp.Igual)
            Kind.Bool -> listOf(RuleOp.Sim, RuleOp.Nao)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ops.forEach { op -> Chip(stringResource(opLabels.getValue(op)), selected = rule.op == op) { onChange(rule.copy(op = op)) } }
        }
        when (rule.field.kind()) {
            Kind.Text -> OutlinedTextField(
                value = rule.text,
                onValueChange = { onChange(rule.copy(text = it)) },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Kind.Number -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("", rule.a, Modifier.width(120.dp)) { onChange(rule.copy(a = it)) }
                if (rule.op == RuleOp.Entre) NumberField("", rule.b, Modifier.width(120.dp)) { onChange(rule.copy(b = it)) }
            }
            Kind.Bool -> Unit
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { raw -> onChange(raw.filter(Char::isDigit).take(6).toIntOrNull() ?: 0) },
        label = { if (label.isNotEmpty()) Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = fieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun fieldColors() = SonivellTheme.colors.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent,
        cursorColor = c.accent,
        focusedLabelColor = c.accent,
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
    )
}
