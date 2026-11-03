package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.glassSurface

/**
 * Aba Perfil, a mesma em todos os estilos: três números neutros, "Descobrir" (mapa musical, Momentos,
 * Respira, Seu perfil sonoro e a Análise do som com progresso) e Ajustes por último, mostrando o estilo atual.
 */
@Composable
fun SvProfileScreen(
    trackCount: Int,
    artistCount: Int,
    monthListenedMs: Long,
    smartAvailable: Boolean,
    /** Músicas já analisadas pelo motor e o total da biblioteca. */
    analysed: Int,
    analysisTotal: Int,
    /** Nome do estilo atual ("Clássico"), mostrado no item Ajustes. */
    styleName: String,
    contentPadding: PaddingValues,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onMap: () -> Unit,
    onMoments: () -> Unit,
    onBreathe: () -> Unit,
    onAnalysis: () -> Unit,
) {
    val nc = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val hours = (monthListenedMs / 3_600_000L).toInt()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 16.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SvTitle(stringResource(R.string.nav_profile), stringResource(R.string.sv_profile_sub))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(count(trackCount), stringResource(R.string.sv_stat_songs), Modifier.weight(1f))
            Stat(count(artistCount), stringResource(R.string.sv_stat_artists), Modifier.weight(1f))
            Stat(count(hours) + " h", stringResource(R.string.sv_stat_hours), Modifier.weight(1f))
        }
        SvSection(stringResource(R.string.sv_discover), Modifier.padding(top = 4.dp))
        // O mesmo ícone de mapa usado no Para você.
        Item(icons.genre, stringResource(R.string.sv_map_title), stringResource(R.string.sv_map_sub), onMap)
        Item(icons.album, stringResource(R.string.sv_moments_title), stringResource(R.string.sv_moments_sub), onMoments)
        Item(icons.sleep, stringResource(R.string.sv_breathe_title), stringResource(R.string.sv_breathe_sub), onBreathe)
        // Seu perfil sonoro reúne estatísticas, histórico e "seu mês em música".
        Item(icons.musicNote, stringResource(R.string.sv_row_profile), stringResource(R.string.sv_row_profile_hint), onStats)
        if (smartAvailable) AnalysisItem(analysed, analysisTotal, onAnalysis)

        // Ajustes por último, com o mesmo nome da tela que abre e o estilo atual.
        Item(icons.settings, stringResource(R.string.sv_row_settings), styleName, onSettings, Modifier.padding(top = 10.dp))

    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val highlight = false
    val nc = SonivellTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .then(
                // Vidro: o destaque ("este mês") ganha o brilho na cor de destaque.
                if (SonivellTheme.glass) Modifier.glassSurface(shape, nc.surface, nc.outline, if (highlight) nc.accent else null, elevation = 8.dp)
                else Modifier.clip(shape).background(nc.surface),
            )
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = if (highlight) nc.accent else nc.textPrimary, textAlign = TextAlign.Center, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = nc.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val nc = SonivellTheme.colors
    SvRow(
        title, subtitle, onClick, modifier,
        art = { SvIconTile(icon) },
        trailing = { Icon(SonivellTheme.icons.chevron, null, Modifier.size(20.dp), tint = nc.textSecondary) },
    )
}

/** Análise do som com barra de progresso e contagem ("[N] de 1.990 músicas analisadas"). */
@Composable
private fun AnalysisItem(analysed: Int, total: Int, onClick: () -> Unit) {
    val nc = SonivellTheme.colors
    val fraction = if (total > 0) (analysed.toFloat() / total).coerceIn(0f, 1f) else 0f
    SvCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SvIconTile(SonivellTheme.icons.smart)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.sv_analysis_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(nc.track)) {
                    Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(nc.accent))
                }
                Text(stringResource(R.string.sv_analysis_count, analysed, total), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(SonivellTheme.icons.chevron, null, Modifier.size(20.dp), tint = nc.textSecondary)
        }
    }
}

/** Contagem com separador de milhar ("1.990"). */
private fun count(n: Int): String = String.format(java.util.Locale.getDefault(), "%,d", n)
