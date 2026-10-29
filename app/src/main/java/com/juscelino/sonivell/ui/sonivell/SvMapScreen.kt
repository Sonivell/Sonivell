package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Genre
import com.juscelino.sonivell.ui.theme.NoraColors
import com.juscelino.sonivell.ui.theme.SonivellTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mapa musical do tema: o centro é você e cada "mundo" é um gênero da sua biblioteca, do maior para o menor.
 * Esta primeira versão usa os gêneros das tags; o mapa por semelhança de som (core/smart) fica para depois.
 */
@Composable
fun SvMapScreen(
    genres: List<Genre>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenGenre: (String) -> Unit,
) {
    val icons = SonivellTheme.icons
    val worlds = genres.sortedByDescending { it.tracks.size }.take(6)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.cd_back), onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(icons.back, null, Modifier.size(22.dp), tint = NoraColors.Text) }
        SvTitle(stringResource(R.string.sv_map_title), stringResource(R.string.sv_map_sub))

        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val radius = maxWidth * 0.36f
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val r = size.minDimension / 2f
                listOf(0.42f, 0.72f, 1f).forEach { f ->
                    drawCircle(NoraColors.Line, radius = r * f * 0.98f, center = center, style = Stroke(1.dp.toPx()))
                }
            }
            SvPlanet(Modifier.align(Alignment.Center).size(maxWidth * 0.26f))
            worlds.forEachIndexed { i, genre ->
                val a = Math.toRadians(-90.0 + i * (360.0 / worlds.size))
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = radius * cos(a).toFloat(), y = radius * sin(a).toFloat())
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { onOpenGenre(genre.name) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SvCircleArt(genre.tracks.firstOrNull(), 52.dp)
                    Text(genre.name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = NoraColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.size(width = 72.dp, height = 16.dp))
                }
            }
        }

        SvCard(Modifier.fillMaxWidth(), onClick = { worlds.firstOrNull()?.let { onOpenGenre(it.name) } }) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.sv_map_explore), style = MaterialTheme.typography.titleMedium, color = NoraColors.Text)
                    Text(stringResource(R.string.sv_map_hint), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = NoraColors.Muted)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(NoraColors.Cream), contentAlignment = Alignment.Center) {
                    Icon(icons.chevron, null, Modifier.size(22.dp), tint = Color(0xFF1A1612))
                }
            }
        }
    }
}
