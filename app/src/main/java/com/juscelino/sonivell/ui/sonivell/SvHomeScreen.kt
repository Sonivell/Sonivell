package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.LibraryState
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.components.Waveform
import com.juscelino.sonivell.ui.theme.ArtTones
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.glassSurface
import java.util.Calendar

/**
 * Início do tema Sonivell, no visual do Ondas: atalhos em cartões pequenos (Coleções e Sua biblioteca),
 * o Flow de hoje, os mixes pelo som (edição SMART) e o mapa. A biblioteca deixou de ser uma aba: os
 * cartões abrem as coleções automáticas e a biblioteca já no filtro escolhido.
 */
@Composable
fun SvHomeScreen(
    contentPadding: PaddingValues,
    state: LibraryState,
    favoritesCount: Int,
    mixSeeds: List<Track>,
    smartAvailable: Boolean,
    smartProgress: SmartProgress,
    /** Coleção automática por chave ("curtidas", "mais-ouvidas", "recentes"). */
    onOpenAuto: (String) -> Unit,
    /** Biblioteca no filtro dado: 0 Todas, 1 Músicas, 2 Álbuns, 3 Artistas, 4 Gêneros, 5 Pastas. */
    onOpenLibrary: (Int) -> Unit,
    onPlayMix: (Track) -> Unit,
    onOpenFlow: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenAnalysis: () -> Unit,
) {
    val nc = SonivellTheme.colors
    val icons = SonivellTheme.icons
    // Adicionadas nos últimos 30 dias: a mesma regra da coleção automática "recentes".
    val newCount = remember(state.allTracks) {
        val since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        state.allTracks.count { it.dateAdded * 1000 >= since }
    }
    val forYou = listOf(
        SvShortcut(stringResource(R.string.source_liked), favoritesCount.toString()) { onOpenAuto("curtidas") },
        SvShortcut(stringResource(R.string.auto_most), stringResource(R.string.sv_most_sub)) { onOpenAuto("mais-ouvidas") },
        SvShortcut(stringResource(R.string.sv_new), stringResource(R.string.sv_new_sub, newCount)) { onOpenAuto("recentes") },
    )
    val library = listOf(
        SvShortcut(stringResource(R.string.tab_songs), state.allTracks.size.toString()) { onOpenLibrary(1) },
        SvShortcut(stringResource(R.string.sv_albums), state.albums.size.toString()) { onOpenLibrary(2) },
        SvShortcut(stringResource(R.string.sv_artists), state.artists.size.toString()) { onOpenLibrary(3) },
        SvShortcut(stringResource(R.string.sv_genres), state.genres.size.toString()) { onOpenLibrary(4) },
        SvShortcut(stringResource(R.string.tab_folders), state.folders.size.toString()) { onOpenLibrary(5) },
        // As playlists moravam na aba Biblioteca; sem ela, entram por aqui ("Todas": coleções e playlists).
        SvShortcut(stringResource(R.string.tab_playlists), "") { onOpenLibrary(0) },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Cabeçalho do Ondas: saudação pequena e o título grande.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(greeting()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = nc.textSecondary)
            Text(
                stringResource(R.string.sv_home_title),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.6).sp),
                color = nc.textPrimary,
            )
        }

        ShortcutGroup(stringResource(R.string.sv_collections), forYou, toneOffset = 0)
        ShortcutGroup(stringResource(R.string.sv_library_title), library, toneOffset = 3)
        if (!smartAvailable) {
            Text(stringResource(R.string.sv_analysis_lite), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary)
        }

        // Seu Flow de hoje: cartão do Ondas com a onda embaixo.
        SvCard(Modifier.fillMaxWidth(), onClick = onOpenFlow, tone = nc.accent) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SvIconTile(icons.smart, 52.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.sv_flow_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary)
                        Text(stringResource(R.string.sv_flow_sub), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary, maxLines = 2)
                    }
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(nc.accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icons.play, null, Modifier.size(20.dp), tint = nc.onAccent) }
                }
                Waveform(seed = 7L, progress = 0f, modifier = Modifier.fillMaxWidth().height(22.dp), bars = 44)
            }
        }

        // Mixes pelo som (edição SMART): os mesmos do Para você do Ondas, um por artista que você ouve.
        if (smartAvailable && mixSeeds.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SvSection(stringResource(R.string.fy_mixes), Modifier.padding(top = 4.dp)) { SvSmartTag() }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(mixSeeds, key = { _, seed -> "mix-" + seed.id }) { i, seed ->
                        val label = stringResource(R.string.fy_mix_of, seed.artist)
                        Column(
                            Modifier.width(136.dp)
                                // No vidro não corta: o recorte comeria a sombra da capa.
                                .then(if (SonivellTheme.glass) Modifier else Modifier.clip(RoundedCornerShape(20.dp)))
                                .clickable(role = Role.Button, onClickLabel = label) { onPlayMix(seed) },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val tone = ArtTones.cores[i % ArtTones.cores.size]
                            if (SonivellTheme.glass) {
                                GlassMixCover(seed.artist, tone, Modifier.size(width = 136.dp, height = 120.dp))
                            } else {
                                SvOrb(
                                    top = tone,
                                    bottom = ArtTones.backgrounds[i % ArtTones.backgrounds.size],
                                    modifier = Modifier.size(width = 136.dp, height = 120.dp),
                                    seed = 0,
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.fy_sounds_like, seed.title), style = MaterialTheme.typography.labelMedium, color = nc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        SvRow(
            title = stringResource(R.string.sv_map_title),
            subtitle = stringResource(R.string.sv_map_sub),
            onClick = onOpenMap,
            art = { SvIconTile(icons.genre) },
            tone = ArtTones.cores[1 % ArtTones.cores.size],
            trailing = { Icon(icons.chevron, null, Modifier.size(20.dp), tint = nc.textSecondary) },
        )
        if (smartAvailable && (smartProgress.running || smartProgress.analysed < smartProgress.total)) {
            SvRow(
                title = stringResource(R.string.sv_analysis_title),
                subtitle = stringResource(R.string.sv_analysis_count, smartProgress.analysed, smartProgress.total),
                onClick = onOpenAnalysis,
                art = { SvIconTile(icons.smart) },
                trailing = { Icon(icons.chevron, null, Modifier.size(20.dp), tint = nc.textSecondary) },
            )
        }
    }
}

/** Um atalho do Início: nome, um número ou dica curta embaixo e para onde leva. */
private class SvShortcut(val label: String, val detail: String, val onClick: () -> Unit)

/**
 * Grupo de atalhos com rótulo pequeno, em cartões de três por linha no modelo das antigas sensações:
 * nome, ponto colorido e onda curta, com o número à direita da onda.
 */
@Composable
private fun ShortcutGroup(title: String, items: List<SvShortcut>, toneOffset: Int) {
    val nc = SonivellTheme.colors
    val glass = SonivellTheme.glass
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
            fontWeight = FontWeight.ExtraBold,
            color = nc.textSecondary,
            modifier = Modifier.padding(start = 2.dp),
        )
        items.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { col, item ->
                    val tone = ArtTones.cores[(toneOffset + r * 3 + col) % ArtTones.cores.size]
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .height(68.dp)
                            .then(
                                if (glass) Modifier.glassSurface(shape, nc.surface, nc.outline, tone, elevation = 8.dp)
                                else Modifier.clip(shape).background(nc.surface).border(1.dp, nc.outline, shape),
                            )
                            .clickable(role = Role.Button, onClickLabel = item.label, onClick = item.onClick)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Encolhe até caber ("Mais ouvidas" cortava em telas estreitas) em vez de virar "Mais ouvid…".
                            BasicText(
                                item.label,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, color = nc.textPrimary),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                            )
                            Box(Modifier.size(7.dp).clip(CircleShape).background(tone))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SvMiniWave(item.label, tone, Modifier.size(width = 40.dp, height = 14.dp))
                            if (item.detail.isNotEmpty()) {
                                Text(
                                    item.detail,
                                    // Sem o espaçamento entre letras do labelSmall, que separava os dígitos ("1 9 9 0").
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = nc.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Capa de mix no tema Desenvolvimento: o cartão dos atalhos (vidro, ponto colorido no canto e a onda
 * curta embaixo), no mesmo tamanho da capa do Sonivell.
 */
@Composable
private fun GlassMixCover(seedText: String, tone: Color, modifier: Modifier = Modifier) {
    val nc = SonivellTheme.colors
    Column(
        modifier
            .glassSurface(RoundedCornerShape(20.dp), nc.surface, nc.outline, tone, elevation = 10.dp)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tone))
        SvMiniWave(seedText, tone, Modifier.size(width = 64.dp, height = 22.dp))
    }
}

/** Selo "SMART" com contorno na cor de destaque, como no Para você do Ondas. */
@Composable
private fun SvSmartTag() {
    val nc = SonivellTheme.colors
    val shape = RoundedCornerShape(7.dp)
    Box(Modifier.height(22.dp).clip(shape).border(1.dp, nc.accent, shape).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.source_smart), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.8.sp), fontWeight = FontWeight.ExtraBold, color = nc.accent)
    }
}

private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}
