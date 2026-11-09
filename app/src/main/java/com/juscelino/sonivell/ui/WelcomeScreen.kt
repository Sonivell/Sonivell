package com.juscelino.sonivell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.components.AppIcon
import com.juscelino.sonivell.ui.sonivell.SvCard
import com.juscelino.sonivell.ui.sonivell.SvIconTile
import com.juscelino.sonivell.ui.sonivell.SvPill
import com.juscelino.sonivell.ui.theme.SonivellTheme

/*
 * As duas telas da primeira abertura (pedir acesso e boas-vindas), no mesmo desenho do resto do app:
 * o ícone do Sonivell, títulos pesados, cartões de vidro ([SvCard]), ícones em quadrados claros na cor
 * de destaque ([SvIconTile]) e o botão principal do app ([SvPill]). Uma luz fraca na cor de destaque
 * no alto da tela, como a luz da capa no player.
 */

/** Fundo da abertura: o fundo do app com uma luz fraca na cor de destaque vinda do alto. */
@Composable
private fun Modifier.openingBackground(): Modifier {
    val c = SonivellTheme.colors
    return this
        .background(c.background)
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(c.accent.copy(alpha = if (c.isDark) 0.12f else 0.18f), Color.Transparent),
                    center = Offset(size.width / 2f, 0f),
                    radius = size.width * 1.1f,
                ),
            )
        }
}

/** Primeira tela antes do acesso aos arquivos: o que o Sonivell lê e que nada sai do aparelho. */
@Composable
internal fun PermissionScreen(onRequest: () -> Unit) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    // Centrado na tela quando cabe; rola nas telas baixas (a altura mínima é a da tela, e os
    // espaços com peso dividem o que sobra).
    BoxWithConstraints(Modifier.fillMaxSize().openingBackground().safeDrawingPadding()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.heightIn(min = 48.dp).weight(1f))
            AppIcon(104.dp, glow = true)
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp, letterSpacing = (-0.9).sp),
                color = c.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp),
            )
            Text(
                stringResource(R.string.permission_message),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, start = 8.dp, end = 8.dp),
            )
            SvCard(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Point(icons.musicNote, stringResource(R.string.permission_point_audio), stringResource(R.string.permission_point_audio_text))
                    Point(icons.device, stringResource(R.string.welcome_point_private), stringResource(R.string.welcome_point_private_text))
                }
            }
            Spacer(Modifier.heightIn(min = 32.dp).weight(1f))
            SvPill(stringResource(R.string.permission_allow), onRequest, Modifier.fillMaxWidth())
        }
    }
}

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
            .openingBackground()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AppIcon(64.dp, Modifier.padding(top = 16.dp), glow = true)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (loading) R.string.welcome_title_loading else R.string.welcome_title),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.9).sp),
                color = c.textPrimary,
            )
            Text(
                if (loading) stringResource(R.string.welcome_sub_loading)
                else pluralStringResource(if (smartAvailable) R.plurals.welcome_sub_smart else R.plurals.welcome_sub, trackCount, trackCount),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                color = c.textSecondary,
            )
        }

        if (loading) {
            CircularProgressIndicator(color = c.accent)
        } else if (smartAvailable && progress.total > 0) {
            val fraction = (progress.analysed.toFloat() / progress.total).coerceIn(0f, 1f)
            SvCard(Modifier.fillMaxWidth(), tone = c.accent) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SvIconTile(icons.smart, 40.dp)
                        Text(stringResource(R.string.welcome_smart_listening), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.accent)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.track)) {
                        Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.accent))
                    }
                    Text(
                        stringResource(R.string.welcome_smart_count, progress.analysed, progress.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }
            }
        }

        SvCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Point(icons.check, stringResource(R.string.welcome_point_now), stringResource(if (smartAvailable) R.string.welcome_point_now_text_smart else R.string.welcome_point_now_text))
                if (smartAvailable) Point(icons.smart, stringResource(R.string.welcome_point_better), stringResource(R.string.welcome_point_better_text))
                Point(icons.device, stringResource(R.string.welcome_point_private), stringResource(R.string.welcome_point_private_text))
            }
        }

        SvPill(stringResource(R.string.welcome_start), onDone, Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

/** Um item da abertura: ícone num quadrado claro na cor de destaque, título e uma linha de apoio. */
@Composable
private fun Point(icon: ImageVector, title: String, text: String) {
    val c = SonivellTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        SvIconTile(icon, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
            Text(text, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp), color = c.textSecondary)
        }
    }
}
