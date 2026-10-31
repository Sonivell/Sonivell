package com.juscelino.sonivell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.cardSurface

/**
 * Primeira abertura: conta quantas músicas foram achadas, mostra o SMART analisando e
 * deixa claro que o app já funciona enquanto isso (e que nada sai do aparelho).
 */
@Composable
internal fun WelcomeScreen(
    trackCount: Int,
    loading: Boolean,
    smartAvailable: Boolean,
    progress: SmartProgress,
    onDone: () -> Unit,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    BackHandler(onBack = onDone)
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(icons.musicNote, null, Modifier.size(30.dp), tint = c.onAccent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (loading) R.string.welcome_title_loading else R.string.welcome_title),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 36.sp),
                color = c.textPrimary,
            )
            Text(
                if (loading) stringResource(R.string.welcome_sub_loading)
                else pluralStringResource(if (smartAvailable) R.plurals.welcome_sub_smart else R.plurals.welcome_sub, trackCount, trackCount),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                color = c.textTertiary,
            )
        }

        if (loading) {
            CircularProgressIndicator(color = c.accent)
        } else if (smartAvailable && progress.total > 0) {
            val fraction = (progress.analysed.toFloat() / progress.total).coerceIn(0f, 1f)
            Column(
                Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(24.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.welcome_smart_listening), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = c.accent)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.track)) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(c.accent))
                }
                Text(
                    stringResource(R.string.welcome_smart_count, progress.analysed, progress.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Point(icons.check, true, stringResource(R.string.welcome_point_now), stringResource(if (smartAvailable) R.string.welcome_point_now_text_smart else R.string.welcome_point_now_text))
            if (smartAvailable) Point(icons.smart, false, stringResource(R.string.welcome_point_better), stringResource(R.string.welcome_point_better_text))
            Point(icons.device, false, stringResource(R.string.welcome_point_private), stringResource(R.string.welcome_point_private_text))
        }

        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.accent)
                .clickable(role = Role.Button, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.welcome_start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.onAccent)
        }
    }
}

@Composable
private fun Point(icon: ImageVector, highlighted: Boolean, title: String, text: String) {
    val c = SonivellTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (highlighted) c.accent else c.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (highlighted) c.onAccent else c.textPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
            Text(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp), color = c.textSecondary)
        }
    }
}
