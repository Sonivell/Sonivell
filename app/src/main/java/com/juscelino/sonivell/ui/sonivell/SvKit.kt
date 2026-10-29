package com.juscelino.sonivell.ui.sonivell

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.theme.NoraColors
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Sensações da tela Início do tema Sonivell. Cada uma é uma descrição que o SMART compara com o som. */
enum class SvMood(@StringRes val label: Int, val query: String, val top: Color, val bottom: Color) {
    Calmo(R.string.sv_mood_calm, "calm relaxing soft acoustic music", Color(0xFF8D949C), Color(0xFF2A2D31)),
    Foco(R.string.sv_mood_focus, "instrumental ambient music for concentration", Color(0xFF1A1F2A), Color(0xFF05060A)),
    Energia(R.string.sv_mood_energy, "happy upbeat energetic dance music", Color(0xFFE08A4A), Color(0xFF3A1F4A)),
    Criativo(R.string.sv_mood_creative, "creative experimental inventive instrumental music", Color(0xFF6E8E90), Color(0xFF16262A)),
    Nostalgia(R.string.sv_mood_nostalgia, "nostalgic retro classic songs", Color(0xFFD9A867), Color(0xFF3A2A1A)),
    Sonho(R.string.sv_mood_dream, "dreamy ethereal atmospheric ambient music", Color(0xFF5A4AD8), Color(0xFF14102E)),
    Equilibrio(R.string.sv_mood_balance, "balanced mellow smooth easygoing music", Color(0xFF6FAE96), Color(0xFF1A2E2A)),
    Liberdade(R.string.sv_mood_freedom, "free spirited open road uplifting music", Color(0xFF7FB0A0), Color(0xFF3A3420)),
    Outro(R.string.sv_mood_other, "", Color(0xFF1A1815), Color(0xFF1A1815)),
}

/** Aba da barra de navegação do tema Sonivell. */
data class SvTab(val route: String, @StringRes val label: Int, val icon: ImageVector)

/** Arte circular gerada: céu em degradê, serra escura e um ponto de luz. */
@Composable
fun SvOrb(top: Color, bottom: Color, modifier: Modifier = Modifier, seed: Int = 0) {
    Canvas(modifier.clip(CircleShape)) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(top, bottom)))
        val ridge = Path().apply {
            moveTo(0f, h * 0.72f)
            lineTo(w * 0.28f, h * (0.46f + (seed % 3) * 0.03f))
            lineTo(w * 0.52f, h * 0.62f)
            lineTo(w * 0.76f, h * 0.40f)
            lineTo(w, h * 0.64f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(ridge, Color(0xFF0B0A09).copy(alpha = 0.72f))
        drawCircle(Color.White.copy(alpha = 0.6f), radius = w * 0.07f, center = Offset(w * 0.62f, h * 0.28f))
        drawCircle(Color.White.copy(alpha = 0.14f), radius = w / 2f, style = Stroke(1.dp.toPx()))
    }
}

/** Planeta rochoso iluminado de um lado, usado no mapa e na tela Respira. */
@Composable
fun SvPlanet(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        val light = Offset(c.x - r * 0.35f, c.y - r * 0.4f)
        drawCircle(
            Brush.radialGradient(listOf(NoraColors.Gold.copy(alpha = 0.28f), Color.Transparent), center = c, radius = r * 1.25f),
            radius = r * 1.25f, center = c,
        )
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFE9D2AE), 0.55f to Color(0xFF9A7B52), 1f to Color(0xFF1A130C),
                center = light, radius = r * 1.5f,
            ),
            radius = r, center = c,
        )
        val rnd = Random(7)
        repeat(26) {
            val a = rnd.nextFloat() * 6.2832f
            val d = sqrt(rnd.nextFloat()) * r * 0.85f
            val cr = r * (0.03f + rnd.nextFloat() * 0.09f)
            val p = Offset(c.x + d * cos(a), c.y + d * sin(a))
            drawCircle(Color.Black.copy(alpha = 0.14f), cr, p)
            drawCircle(Color(0xFFFFE9C4).copy(alpha = 0.10f), cr * 0.8f, Offset(p.x - cr * 0.2f, p.y - cr * 0.2f))
        }
        drawCircle(
            Brush.radialGradient(
                0f to Color.Transparent, 0.55f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.92f),
                center = light, radius = r * 1.5f,
            ),
            radius = r, center = c,
        )
        drawCircle(NoraColors.Gold.copy(alpha = 0.5f), radius = r, center = c, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
fun SvCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(NoraColors.Card)
            .border(1.dp, NoraColors.Line, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        content = content,
    )
}

/** Botão em cápsula: creme cheio (principal) ou só com contorno. */
@Composable
fun SvPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = 54.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(NoraColors.Cream) else Modifier.border(1.dp, NoraColors.Line, shape))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (filled) Color(0xFF1A1612) else NoraColors.Text)
    }
}

@Composable
fun SvWordmark(modifier: Modifier = Modifier) {
    Text(
        "S O N I V E L L",
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, letterSpacing = 3.sp),
        color = NoraColors.Text,
        modifier = modifier,
    )
}

@Composable
fun SvTitle(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = NoraColors.Text)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = NoraColors.Muted)
    }
}

/** Linha de lista: arte circular, título, subtítulo e um complemento opcional à direita. */
@Composable
fun SvRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    art: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    SvCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            art()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = NoraColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = NoraColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

/** Capa circular de uma faixa (ou a arte gerada, se não houver). */
@Composable
fun SvCircleArt(track: Track?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    AlbumArt(track, modifier.size(size), corner = size / 2, detailed = false)
}

/** Barra de navegação do tema: ícone e nome, só o creme marca a aba atual. */
@Composable
fun BottomNavSonivell(route: String?, tabs: List<SvTab>, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(NoraColors.Bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(NoraColors.Line))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            tabs.forEach { tab ->
                val selected = tab.route == route
                val color = if (selected) NoraColors.Cream else NoraColors.Muted
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.route) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Icon(tab.icon, null, Modifier.size(22.dp), tint = color)
                    Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = color, maxLines = 1)
                }
            }
        }
    }
}

/** Mini player do tema: capa circular, título e botão de tocar em creme, com um fio de progresso. */
@Composable
fun MiniPlayerSonivell(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icons = SonivellTheme.icons
    val openPlayer = stringResource(R.string.cd_open_player)
    SvCard(modifier.padding(horizontal = 12.dp).fillMaxWidth(), onClick = onOpen) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SvCircleArt(track, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, color = NoraColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = NoraColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(NoraColors.Cream).clickable(role = Role.Button, onClickLabel = openPlayer, onClick = onTogglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (isPlaying) icons.pause else icons.play, null, Modifier.size(22.dp), tint = Color(0xFF1A1612))
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(NoraColors.Line)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(NoraColors.Gold))
            }
        }
    }
}
