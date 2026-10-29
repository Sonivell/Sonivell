package com.juscelino.sonivell.ui.sonivell

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.ui.theme.NoraColors
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlin.math.PI
import kotlin.math.sin

/** Respira: um planeta que "respira" e um som calmo para desacelerar. Deslizar para baixo pausa. */
@Composable
fun SvBreatheScreen(
    isPlaying: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onTogglePlay: () -> Unit,
    onTimer: () -> Unit,
) {
    val icons = SonivellTheme.icons
    val transition = rememberInfiniteTransition(label = "respira")
    val scale by transition.animateFloat(
        initialValue = 0.95f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "escala",
    )
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "onda",
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(NoraColors.Bg)
            .pointerInput(isPlaying) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = { if (total > 120f && isPlaying) onTogglePlay() },
                )
            }
            .padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_back), onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(icons.close, null, Modifier.size(22.dp), tint = NoraColors.Text) }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            SvPlanet(Modifier.fillMaxWidth(0.82f).aspectRatio(1f).scale(scale))
        }
        Text(stringResource(R.string.sv_breathe_title), style = MaterialTheme.typography.titleLarge, color = NoraColors.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.sv_breathe_sub), style = MaterialTheme.typography.bodyMedium, color = NoraColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val path = Path()
            val mid = size.height / 2f
            for (x in 0..size.width.toInt() step 6) {
                val xf = x.toFloat()
                val y = mid + sin(xf / size.width * 4f * PI.toFloat() + phase) * (size.height * 0.28f)
                if (x == 0) path.moveTo(xf, y) else path.lineTo(xf, y)
            }
            drawPath(path, Brush.horizontalGradient(listOf(Color.Transparent, NoraColors.Gold, Color.Transparent)), style = Stroke(1.6.dp.toPx()))
        }

        if (isPlaying) {
            Text(stringResource(R.string.sv_breathe_swipe), style = MaterialTheme.typography.bodyMedium.copy(), color = NoraColors.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            SvPill(stringResource(R.string.sv_breathe_start), onStart, Modifier.fillMaxWidth())
        }
        SvPill(stringResource(R.string.sv_breathe_timer), onTimer, Modifier.fillMaxWidth(), filled = false)
    }
}
