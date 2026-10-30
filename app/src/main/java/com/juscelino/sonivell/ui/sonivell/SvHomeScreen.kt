package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.components.Waveform
import com.juscelino.sonivell.ui.theme.ArtTones
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.util.Calendar

/**
 * Início do tema Sonivell, no visual do Ondas: a pergunta "Como você quer se sentir agora?" com as
 * sensações em botões, o campo para descrever e os atalhos para Flow, Mapa e Análise.
 */
@Composable
fun SvHomeScreen(
    contentPadding: PaddingValues,
    smartAvailable: Boolean,
    smartProgress: SmartProgress,
    onMood: (SvMood) -> Unit,
    onDescribe: (String) -> Unit,
    onOpenFlow: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenAnalysis: () -> Unit,
) {
    val nc = SonivellTheme.colors
    val icons = SonivellTheme.icons
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Cabeçalho do Ondas: saudação pequena e o título grande.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(greeting()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = nc.textSecondary)
            Text(
                stringResource(R.string.sv_mood_title),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.6).sp),
                color = nc.textPrimary,
            )
        }

        // Sensações em cartões pequenos no modelo dos Gêneros do Ondas: nome, ponto colorido e uma
        // onda curta. "Outro" é uma ação (leva à busca por descrição): contorno e tudo na cor de destaque.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SvMood.entries.chunked(3).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { col, mood ->
                        val label = stringResource(mood.label)
                        val other = mood == SvMood.Outro
                        val tone = if (other) nc.accent else ArtTones.cores[(r * 3 + col) % ArtTones.cores.size]
                        val shape = RoundedCornerShape(16.dp)
                        Column(
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(shape)
                                .then(if (other) Modifier else Modifier.background(nc.surface))
                                .border(if (other) 1.5.dp else 1.dp, if (other) nc.accent else nc.outline, shape)
                                .clickable(role = Role.Button, onClickLabel = label) {
                                    if (other) onDescribe(text) else onMood(mood)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (other) nc.accent else nc.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Box(Modifier.size(7.dp).clip(CircleShape).background(tone))
                            }
                            SvMiniWave(label, tone, Modifier.size(width = 44.dp, height = 14.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (!smartAvailable) {
            Text(stringResource(R.string.sv_analysis_lite), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary)
        }

        // Campo "descreva o que sente": vira uma busca pelo sentido (SMART) na aba Explorar.
        val fieldShape = RoundedCornerShape(16.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(fieldShape)
                .background(nc.surface)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(stringResource(R.string.sv_describe_hint), style = MaterialTheme.typography.bodyLarge, color = nc.textSecondary, maxLines = 1)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = nc.textPrimary),
                    cursorBrush = SolidColor(nc.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) onDescribe(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(nc.accent)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_search)) { if (text.isNotBlank()) onDescribe(text) },
                contentAlignment = Alignment.Center,
            ) { Icon(icons.search, null, Modifier.size(18.dp), tint = nc.onAccent) }
        }

        // Seu Flow de hoje: cartão do Ondas com a onda embaixo.
        SvCard(Modifier.fillMaxWidth(), onClick = onOpenFlow) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SvIconTile(icons.smart, 52.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.sv_flow_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary)
                        Text(stringResource(R.string.sv_flow_sub), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary, maxLines = 2)
                    }
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(nc.accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icons.play, null, Modifier.size(20.dp), tint = nc.onAccent) }
                }
                Waveform(seed = 7L, progress = 0f, modifier = Modifier.fillMaxWidth().height(22.dp), bars = 44)
            }
        }
        SvRow(
            title = stringResource(R.string.sv_map_title),
            subtitle = stringResource(R.string.sv_map_sub),
            onClick = onOpenMap,
            art = { SvIconTile(icons.genre) },
            trailing = { Icon(icons.chevron, null, Modifier.size(20.dp), tint = nc.textSecondary) },
        )
        if (smartAvailable && (smartProgress.running || smartProgress.analysed < smartProgress.total)) {
            SvRow(
                title = stringResource(R.string.sv_analysis_title),
                subtitle = stringResource(R.string.sv_analysis_count, smartProgress.analysed, smartProgress.total),
                onClick = onOpenAnalysis,
                art = { SvIconTile(icons.smart) },
                trailing = { Icon(icons.chevron, null, Modifier.size(20.dp), tint = nc.textSecondary) },
            )
        }
    }
}

private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}
