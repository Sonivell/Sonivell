package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Perfil do tema: "Sua jornada", com números da biblioteca, atalhos e os ajustes do app. */
@Composable
fun SvProfileScreen(
    trackCount: Int,
    artistCount: Int,
    monthListenedMs: Long,
    smartAvailable: Boolean,
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
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SvTitle(stringResource(R.string.sv_profile_title), stringResource(R.string.sv_profile_sub))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(trackCount.toString(), stringResource(R.string.sv_stat_songs), Modifier.weight(1f))
            Stat(artistCount.toString(), stringResource(R.string.sv_stat_artists), Modifier.weight(1f))
            Stat("$hours h", stringResource(R.string.sv_stat_hours), Modifier.weight(1f), highlight = true)
        }
        SvSection(stringResource(R.string.sv_discover), Modifier.padding(top = 4.dp))
        Item(icons.artist, stringResource(R.string.sv_map_title), stringResource(R.string.sv_map_sub), onMap)
        Item(icons.album, stringResource(R.string.sv_moments_title), stringResource(R.string.sv_moments_sub), onMoments)
        Item(icons.sleep, stringResource(R.string.sv_breathe_title), stringResource(R.string.sv_breathe_sub), onBreathe)
        if (smartAvailable) Item(icons.smart, stringResource(R.string.sv_analysis_title), stringResource(R.string.sv_analysis_sub), onAnalysis)

        SvSection(stringResource(R.string.sv_profile_title), Modifier.padding(top = 8.dp))
        Item(icons.musicNote, stringResource(R.string.sv_row_profile), stringResource(R.string.sv_row_profile_hint), onStats)
        Item(icons.smart, stringResource(R.string.sv_row_prefs), stringResource(R.string.sv_row_prefs_hint), onSettings)
        Item(icons.grid, stringResource(R.string.sv_row_visual), stringResource(R.string.sv_row_visual_hint), onSettings)
        Item(icons.settings, stringResource(R.string.sv_row_settings), stringResource(R.string.sv_row_settings_hint), onSettings)

        SvCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.sv_tagline), style = MaterialTheme.typography.titleLarge, color = nc.textPrimary)
                Text(stringResource(R.string.sv_tagline_sub), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = nc.textSecondary)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val nc = SonivellTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(nc.surface).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = if (highlight) nc.accent else nc.textPrimary, textAlign = TextAlign.Center, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = nc.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val nc = SonivellTheme.colors
    SvRow(
        title, subtitle, onClick,
        art = { SvIconTile(icon) },
        trailing = { Icon(SonivellTheme.icons.chevron, null, Modifier.size(20.dp), tint = nc.textSecondary) },
    )
}
