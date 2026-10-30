package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.theme.SonivellTheme

/** Análise do som: o progresso real do SMART ouvindo a biblioteca, tudo no aparelho. */
@Composable
fun SvAnalysisScreen(
    progress: SmartProgress,
    smartAvailable: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val nc = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val fraction = if (progress.total > 0) (progress.analysed.toFloat() / progress.total).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_back), onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(icons.back, null, Modifier.size(22.dp), tint = nc.textPrimary) }
        SvTitle(stringResource(R.string.sv_analysis_title), stringResource(R.string.sv_analysis_sub))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(180.dp)) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2f + 8.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawCircle(nc.surface, radius = size.minDimension / 2f)
                drawArc(nc.outline, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(nc.accent, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp), color = nc.textPrimary)
                Text(stringResource(R.string.sv_analysis_done), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary)
            }
        }

        SvCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.sv_analysis_tracks), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary)
                Text(
                    if (smartAvailable) stringResource(R.string.sv_analysis_count, progress.analysed, progress.total) else stringResource(R.string.sv_analysis_lite),
                    style = MaterialTheme.typography.titleLarge,
                    color = nc.textPrimary,
                )
                Box(Modifier.fillMaxWidth().height(3.dp).background(nc.outline)) {
                    Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(nc.accent))
                }
                Text(stringResource(R.string.sv_analysis_note), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = nc.textSecondary)
            }
        }
        SvCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.sv_analysis_private),
                style = MaterialTheme.typography.titleSmall,
                color = nc.textPrimary,
                modifier = Modifier.padding(18.dp),
            )
        }
    }
}
