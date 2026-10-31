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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.MiniPlayerOndas
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.glassAccent
import com.juscelino.sonivell.ui.theme.glassSurface
import kotlin.math.PI
import kotlin.math.sin

/** Sensações da tela Início do tema Sonivell. Cada uma é uma descrição que o SMART compara com o som. */
enum class SvMood(@StringRes val label: Int, val query: String, val top: Color, val bottom: Color) {
    Calmo(R.string.sv_mood_calm, "calm relaxing soft acoustic music", Color(0xFF8D949C), Color(0xFF2A2D31)),
    // Azul cobalto: no tom escurecido (#1F3E66) contrasta com o verde-lima e não some no fundo.
    Foco(R.string.sv_mood_focus, "instrumental ambient music for concentration", Color(0xFF458AE3), Color(0xFF0B1A2E)),
    Energia(R.string.sv_mood_energy, "happy upbeat energetic dance music", Color(0xFFE08A4A), Color(0xFF3A1F4A)),
    Criativo(R.string.sv_mood_creative, "creative experimental inventive instrumental music", Color(0xFF6E8E90), Color(0xFF16262A)),
    Nostalgia(R.string.sv_mood_nostalgia, "nostalgic retro classic songs", Color(0xFFD9A867), Color(0xFF3A2A1A)),
    Sonho(R.string.sv_mood_dream, "dreamy ethereal atmospheric ambient music", Color(0xFF5A4AD8), Color(0xFF14102E)),
    Equilibrio(R.string.sv_mood_balance, "balanced mellow smooth easygoing music", Color(0xFF6FAE96), Color(0xFF1A2E2A)),
    Liberdade(R.string.sv_mood_freedom, "free spirited open road uplifting music", Color(0xFF7FB0A0), Color(0xFF3A3420)),
    Outro(R.string.sv_mood_other, "", Color(0xFF1A1815), Color(0xFF1A1815)),
}

/** Aba da barra de navegação do tema Sonivell (ícone normal e o preenchido, da aba atual). */
data class SvTab(val route: String, @StringRes val label: Int, val icon: ImageVector, val activeIcon: ImageVector = icon)

/** Fundo das sensações: o tom da cor, escurecido para o texto claro passar 4,5:1 (como no "Para o momento" do Ondas). */
fun SvMood.tone(): Color = lerp(top, Color.Black, 0.55f)

/**
 * Arte gerada no estilo do Ondas: quadrado arredondado no tom escuro, com duas ondas na cor clara
 * (a mesma ideia da capa dos mixes).
 */
@Composable
fun SvOrb(top: Color, bottom: Color, modifier: Modifier = Modifier, seed: Int = 0) {
    Canvas(modifier.clip(RoundedCornerShape(30))) {
        drawRect(lerp(bottom, top, 0.18f))
        val stroke = size.minDimension * 0.05f
        fun line(yFrac: Float, amp: Float, alpha: Float, width: Float) {
            val path = Path()
            val y0 = size.height * yFrac
            val left = size.width * 0.14f
            val right = size.width * 0.86f
            var x = left
            path.moveTo(x, y0)
            while (x <= right) {
                val t = (x - left) / (right - left)
                path.lineTo(x, y0 + sin(t * 4 * PI + seed).toFloat() * amp)
                x += 1.5f
            }
            drawPath(path, top.copy(alpha = alpha), style = Stroke(width, cap = StrokeCap.Round))
        }
        line(0.56f, size.height * 0.09f, 1f, stroke)
        line(0.70f, size.height * 0.06f, 0.45f, stroke * 0.8f)
    }
}

/** Onda curta dos cartões de Gêneros do Ondas: altura e frequência saem do texto, para cada um ter a sua. */
@Composable
fun SvMiniWave(seedText: String, color: Color, modifier: Modifier = Modifier) {
    val h = kotlin.math.abs(seedText.lowercase().hashCode())
    val amp = 0.18f + (h % 5) * 0.05f
    val turns = 1.5f + (h / 5 % 4) * 0.5f
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val path = Path()
        val mid = size.height / 2f
        var x = stroke
        path.moveTo(x, mid)
        while (x <= size.width - stroke) {
            val t = x / size.width
            path.lineTo(x, mid + sin(t * turns * 2 * PI).toFloat() * size.height * amp)
            x += 1.5f
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Centro do mapa e da tela Respira: anéis no tom do contorno e um núcleo na cor de destaque. */
@Composable
fun SvPlanet(modifier: Modifier = Modifier) {
    val nc = SonivellTheme.colors
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(nc.accent.copy(alpha = 0.10f), radius = r, center = center)
        drawCircle(nc.outline, radius = r * 0.98f, center = center, style = Stroke(1.dp.toPx()))
        drawCircle(nc.outline, radius = r * 0.70f, center = center, style = Stroke(1.dp.toPx()))
        drawCircle(nc.surfaceHigh, radius = r * 0.46f, center = center)
        drawCircle(nc.accent, radius = r * 0.24f, center = center)
    }
}

/**
 * Cartão do Ondas: superfície, cantos de 20 dp, sem contorno. No vidro vira
 * ([glassSurface]), com o brilho em [tone] quando dado; [elevation] 0 tira a sombra (listas longas).
 */
@Composable
fun SvCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tone: Color? = null,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val nc = SonivellTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .then(
                if (SonivellTheme.glass) Modifier.glassSurface(shape, nc.surface, nc.outline, tone, elevation)
                else Modifier.clip(shape).background(nc.surface),
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        content = content,
    )
}

/** Botão largo do Ondas: cheio na cor de destaque (principal) ou só com contorno. */
@Composable
fun SvPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val nc = SonivellTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(nc.accent) else Modifier.border(1.dp, nc.outline, shape))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = if (filled) nc.onAccent else nc.textPrimary)
    }
}

/** Botão quadrado com contorno do Ondas (voltar, fechar, buscar…). No vidro, o cheio vira translúcido na cor de destaque. */
@Composable
fun SvSquare(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    val nc = SonivellTheme.colors
    val glass = SonivellTheme.glass
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .size(44.dp)
            .then(
                when {
                    glass && filled -> Modifier.glassAccent(shape, nc.accent)
                    glass -> Modifier.glassSurface(shape, nc.surface, nc.outline, elevation = 6.dp)
                    filled -> Modifier.clip(shape).background(nc.accent)
                    else -> Modifier.clip(shape).border(1.dp, nc.outline, shape)
                },
            )
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = when {
            glass && filled -> nc.accent
            filled -> nc.onAccent
            else -> nc.textPrimary
        }
        Icon(icon, label, Modifier.size(22.dp), tint = tint)
    }
}

/** Título de tela do Ondas: grande e pesado, com uma linha de apoio opcional. */
@Composable
fun SvTitle(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    val nc = SonivellTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, letterSpacing = (-0.9).sp), color = nc.textPrimary)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = nc.textSecondary)
    }
}

/** Título de seção do Ondas (19 sp, ExtraBold), com um complemento opcional à direita. */
@Composable
fun SvSection(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, letterSpacing = (-0.2).sp),
            fontWeight = FontWeight.ExtraBold,
            color = SonivellTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Ícone num quadrado com fundo na cor de destaque bem clara (como nos ajustes). */
@Composable
fun SvIconTile(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val nc = SonivellTheme.colors
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(nc.accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.46f), tint = nc.accent) }
}

/** Linha de lista do Ondas: arte, título, subtítulo e um complemento opcional à direita. */
@Composable
fun SvRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    art: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
    /** Brilho do cartão no vidro. */
    tone: Color? = null,
    /** Sombra no vidro; 0 nas listas longas (álbuns, artistas, pastas). */
    elevation: Dp = 10.dp,
) {
    val nc = SonivellTheme.colors
    SvCard(modifier.fillMaxWidth(), onClick = onClick, tone = tone, elevation = elevation) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            art()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing()
        }
    }
}

/** Capa de uma faixa (ou a arte gerada) em quadrado arredondado, como no Ondas. */
@Composable
fun SvCircleArt(track: Track?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    AlbumArt(track, modifier.size(size), corner = size * 0.28f, detailed = false)
}

/** Barra de navegação do Ondas com as abas do tema: a aba atual fica preenchida na cor de destaque. */
@Composable
fun BottomNavSonivell(route: String?, tabs: List<SvTab>, onSelect: (String) -> Unit) {
    val nc = SonivellTheme.colors
    val glass = SonivellTheme.glass
    val barShape = RoundedCornerShape(22.dp)
    val tabShape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
            .height(60.dp)
            .then(
                if (glass) Modifier.glassSurface(barShape, nc.surface, nc.outline, elevation = 12.dp)
                else Modifier.clip(barShape).background(nc.surface),
            )
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == route
            // No vidro a aba atual é translúcida, com ícone e nome na cor de destaque.
            val color = when {
                !selected -> nc.textSecondary
                glass -> nc.accent
                else -> nc.onAccent
            }
            // Só a aba atual mostra o nome.
            Row(
                Modifier
                    .weight(if (selected) 1.6f else 1f)
                    .fillMaxHeight()
                    .then(
                        when {
                            !selected -> Modifier.clip(tabShape)
                            glass -> Modifier.glassAccent(tabShape, nc.accent)
                            else -> Modifier.clip(tabShape).background(nc.accent)
                        },
                    )
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.route) }),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = stringResource(tab.label)
                Icon(if (selected) tab.activeIcon else tab.icon, if (selected) null else label, Modifier.size(20.dp), tint = color)
                if (selected) {
                    Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
                }
            }
        }
    }
}

/** Mini player do tema: o mesmo do Ondas. */
@Composable
fun MiniPlayerSonivell(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiniPlayerOndas(track, isPlaying, progress, onOpen, onTogglePlay, modifier)
}
