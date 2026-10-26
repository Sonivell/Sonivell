package com.juscelino.sonivell.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.EqualizerState
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ui.components.Chip
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.SectionLabel
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlin.math.roundToInt

data class EqualizerActions(
    val onBack: () -> Unit,
    val onEnabled: (Boolean) -> Unit,
    /** Novos níveis; [preset] é o nome da predefinição escolhida, ou null para ajuste próprio. */
    val onLevels: (levels: List<Int>, preset: String?) -> Unit,
    val onBass: (Int) -> Unit,
)

/** Predefinições do Sonivell, além das do aparelho: pensadas para o tipo de saída. */
private enum class OutputPreset { Fone, AltoFalante }

@Composable
fun EqualizerScreen(state: EqualizerState, contentPadding: PaddingValues, actions: EqualizerActions) {
    val c = SonivellTheme.colors
    val info = state.info
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back), actions.onBack, background = c.surface)
            Text(stringResource(R.string.equalizer), style = MaterialTheme.typography.displaySmall, color = c.textPrimary, modifier = Modifier.weight(1f))
        }

        if (info == null || info.bandFrequencies.isEmpty()) {
            Text(stringResource(R.string.equalizer_unavailable), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            return@Column
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface)
                .clickable(role = Role.Switch) { actions.onEnabled(!state.enabled) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.equalizer_on), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = state.enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onAccent,
                    checkedTrackColor = c.accent,
                    uncheckedThumbColor = c.textSecondary,
                    uncheckedTrackColor = c.surfaceHigh,
                    uncheckedBorderColor = c.outline,
                ),
            )
        }

        val levels = info.bandFrequencies.indices.map { state.levels.getOrNull(it) ?: 0 }
        val flatName = stringResource(R.string.eq_flat)
        val headphonesName = stringResource(R.string.eq_headphones)
        val speakerName = stringResource(R.string.eq_speaker)

        SectionLabel(stringResource(R.string.eq_presets))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(flatName, selected = state.preset == flatName) {
                actions.onLevels(List(levels.size) { 0 }, flatName)
            }
            Chip(headphonesName, selected = state.preset == headphonesName) {
                actions.onLevels(outputCurve(OutputPreset.Fone, info.bandFrequencies, info.minLevel, info.maxLevel), headphonesName)
            }
            Chip(speakerName, selected = state.preset == speakerName) {
                actions.onLevels(outputCurve(OutputPreset.AltoFalante, info.bandFrequencies, info.minLevel, info.maxLevel), speakerName)
            }
            info.presets.forEach { p ->
                Chip(p.name, selected = state.preset == p.name) { actions.onLevels(p.levels, p.name) }
            }
        }

        SectionLabel(stringResource(R.string.eq_bands))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            info.bandFrequencies.forEachIndexed { band, hz ->
                BandSlider(
                    label = formatHz(hz),
                    valueMb = levels[band],
                    min = info.minLevel,
                    max = info.maxLevel,
                ) { newLevel ->
                    actions.onLevels(levels.toMutableList().also { it[band] = newLevel }, null)
                }
            }
        }

        if (info.bassSupported) {
            SectionLabel(stringResource(R.string.eq_bass))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(16.dp)) {
                var bass by remember(state.bass) { mutableStateOf(state.bass.toFloat()) }
                Text("${(bass / 10).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                val bassLabel = stringResource(R.string.eq_bass)
                Slider(
                    value = bass,
                    onValueChange = { bass = it },
                    onValueChangeFinished = { actions.onBass(bass.roundToInt()) },
                    valueRange = 0f..1000f,
                    modifier = Modifier.semantics { contentDescription = bassLabel },
                    colors = sliderColors(),
                )
            }
        }
        Text(stringResource(R.string.equalizer_note), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}

@Composable
private fun BandSlider(label: String, valueMb: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val c = SonivellTheme.colors
    var value by remember(valueMb) { mutableStateOf(valueMb.toFloat()) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textTertiary, modifier = Modifier.width(56.dp))
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            colors = sliderColors(),
        )
        val db = value / 100f
        Text(
            (if (db > 0) "+" else "") + "%.1f dB".format(db),
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
private fun sliderColors() = SonivellTheme.colors.let { c ->
    SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.track)
}

private fun formatHz(hz: Int) = if (hz >= 1000) "%.1f kHz".format(hz / 1000f).replace(".0 ", " ") else "$hz Hz"

/**
 * Curvas simples por tipo de saída, a partir da frequência de cada banda:
 * fone = graves e agudos um pouco realçados; alto-falante = sem subgraves, médios mais presentes.
 */
private fun outputCurve(kind: OutputPreset, freqs: List<Int>, min: Int, max: Int): List<Int> = freqs.map { hz ->
    val mb = when (kind) {
        OutputPreset.Fone -> when {
            hz < 120 -> 300
            hz < 500 -> 100
            hz < 2_000 -> 0
            hz < 8_000 -> 150
            else -> 250
        }
        OutputPreset.AltoFalante -> when {
            hz < 120 -> -400
            hz < 500 -> -100
            hz < 4_000 -> 250
            else -> 100
        }
    }
    mb.coerceIn(min, max)
}
