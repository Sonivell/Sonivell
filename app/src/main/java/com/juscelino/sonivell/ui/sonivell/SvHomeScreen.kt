package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.theme.NoraColors
import com.juscelino.sonivell.ui.theme.SonivellTheme

/** Início do tema Sonivell: "Como você quer se sentir agora?", com as sensações em círculos. */
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
    val icons = SonivellTheme.icons
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 16.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SvWordmark()
        SvTitle(stringResource(R.string.sv_mood_title), stringResource(R.string.sv_mood_sub))

        // Três por linha, como no modelo; a última é "Outro" e leva à busca por descrição.
        SvMood.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { mood ->
                    val label = stringResource(mood.label)
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button, onClickLabel = label) {
                                if (mood == SvMood.Outro) onDescribe(text) else onMood(mood)
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (mood == SvMood.Outro) {
                            Box(
                                Modifier.size(76.dp).clip(CircleShape).background(NoraColors.Card).border(1.dp, NoraColors.Line, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text("•••", color = NoraColors.Muted, style = MaterialTheme.typography.titleMedium) }
                        } else {
                            SvOrb(mood.top, mood.bottom, Modifier.size(76.dp), seed = mood.ordinal)
                        }
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = NoraColors.Text)
                    }
                }
            }
        }
        if (!smartAvailable) {
            Text(
                stringResource(R.string.sv_analysis_lite),
                style = MaterialTheme.typography.bodyMedium,
                color = NoraColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Campo "descreva o que sente": vira uma busca pelo sentido (SMART) na aba Explorar.
        val shape = RoundedCornerShape(50)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x30FFFFFF), shape)
                .padding(start = 22.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(stringResource(R.string.sv_describe_hint), style = MaterialTheme.typography.bodyLarge, color = NoraColors.Muted)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = NoraColors.Text),
                    cursorBrush = SolidColor(NoraColors.Cream),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) onDescribe(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(NoraColors.Cream)
                    .clickable(role = Role.Button) { if (text.isNotBlank()) onDescribe(text) },
                contentAlignment = Alignment.Center,
            ) { Icon(icons.search, null, Modifier.size(20.dp), tint = Color(0xFF1A1612)) }
        }

        // Atalhos para as outras telas do tema.
        SvRow(
            title = stringResource(R.string.sv_flow_title),
            subtitle = stringResource(R.string.sv_flow_sub),
            onClick = onOpenFlow,
            art = { SvOrb(Color(0xFFB8956A), Color(0xFF1A1410), Modifier.size(44.dp), seed = 1) },
        )
        SvRow(
            title = stringResource(R.string.sv_map_title),
            subtitle = stringResource(R.string.sv_map_sub),
            onClick = onOpenMap,
            art = { SvOrb(Color(0xFF8A7A6A), Color(0xFF14110F), Modifier.size(44.dp), seed = 2) },
        )
        if (smartAvailable && (smartProgress.running || smartProgress.analysed < smartProgress.total)) {
            SvRow(
                title = stringResource(R.string.sv_analysis_title),
                subtitle = stringResource(R.string.sv_analysis_count, smartProgress.analysed, smartProgress.total),
                onClick = onOpenAnalysis,
                art = { SvOrb(Color(0xFF6A7A8A), Color(0xFF101418), Modifier.size(44.dp), seed = 0) },
            )
        }
    }
}
