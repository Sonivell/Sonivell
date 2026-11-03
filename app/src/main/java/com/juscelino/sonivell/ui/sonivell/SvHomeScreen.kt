package com.juscelino.sonivell.ui.sonivell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.Spacer
import com.juscelino.sonivell.ui.components.LabeledPill
import com.juscelino.sonivell.ui.components.CoverCollage
import com.juscelino.sonivell.ui.components.PlaySquircle
import com.juscelino.sonivell.data.realGenreCount
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
import androidx.compose.foundation.layout.fillMaxSize
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
import com.juscelino.sonivell.ui.components.OndaArt
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
    /** Quantas playlists o usuário criou; com zero, o atalho diz "Criar". */
    playlistCount: Int,
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
        // Cada coleção com o seu ícone de traço (coração, tendência, relógio) e uma meta clara.
        SvShortcut(stringResource(R.string.source_liked), songsLabel(favoritesCount), icons.like) { onOpenAuto("curtidas") },
        SvShortcut(stringResource(R.string.auto_most), stringResource(R.string.sv_most_sub), Icons.AutoMirrored.Rounded.TrendingUp) { onOpenAuto("mais-ouvidas") },
        SvShortcut(stringResource(R.string.sv_new), stringResource(R.string.sv_new_sub, newCount), Icons.Rounded.Schedule) { onOpenAuto("recentes") },
    )
    val library = listOf(
        SvShortcut(stringResource(R.string.tab_songs), count(state.allTracks.size)) { onOpenLibrary(1) },
        SvShortcut(stringResource(R.string.sv_albums), count(state.albums.size)) { onOpenLibrary(2) },
        SvShortcut(stringResource(R.string.sv_artists), count(state.artists.size)) { onOpenLibrary(3) },
        // Mesma contagem do Explorar: sem os gêneros genéricos.
        SvShortcut(stringResource(R.string.sv_genres), count(realGenreCount(state.genres))) { onOpenLibrary(4) },
        SvShortcut(stringResource(R.string.tab_folders), count(state.folders.size)) { onOpenLibrary(5) },
        SvShortcut(
            stringResource(R.string.tab_playlists),
            if (playlistCount == 0) stringResource(R.string.create) else count(playlistCount),
        ) { onOpenLibrary(0) },
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

        CollectionCards(stringResource(R.string.sv_collections), forYou)
        LibraryGrid(stringResource(R.string.sv_library_title), library)
        if (!smartAvailable) {
            Text(stringResource(R.string.sv_analysis_lite), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary)
        }

        // Seu Flow de hoje: o destaque da tela, em superfície neutra, com o ícone do Smart e um único
        // botão primário. Sem onda decorativa (a onda só aparece onde mostra progresso).
        SvCard(Modifier.fillMaxWidth(), onClick = onOpenFlow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SvIconTile(icons.smart, 48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.sv_flow_title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), fontWeight = FontWeight.ExtraBold, color = nc.textPrimary)
                        Text(stringResource(R.string.sv_flow_sub), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary, maxLines = 2)
                    }
                }
                LabeledPill(icons.play, stringResource(R.string.sv_flow_play), onOpenFlow, Modifier.height(52.dp).fillMaxWidth(), primary = true)
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
                                .clickable(role = Role.Button, onClickLabel = label) { onPlayMix(seed) },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Colagem 2×2 das capas reais do artista, com o play lima no canto.
                            val covers = remember(state.allTracks, seed.artist) {
                                listOf(seed) + state.allTracks.filter { it.artist == seed.artist && it.albumId != seed.albumId }
                            }
                            Box(Modifier.size(136.dp)) {
                                CoverCollage(covers, Modifier.fillMaxSize())
                                PlaySquircle(false, { onPlayMix(seed) }, Modifier.align(Alignment.BottomEnd).padding(8.dp), size = 36.dp, iconSize = 16.dp, contentDescription = label)
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
            tone = nc.textSecondary,
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
private class SvShortcut(val label: String, val detail: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/** Coleções: três cartões iguais, cada um com o ícone de traço próprio, o nome e a meta. */
@Composable
private fun CollectionCards(title: String, items: List<SvShortcut>) {
    val nc = SonivellTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SvSection(title)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                SvCard(Modifier.weight(1f).height(108.dp), onClick = item.onClick, elevation = 6.dp) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        item.icon?.let { Icon(it, null, Modifier.size(22.dp), tint = nc.textPrimary) }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            BasicText(
                                item.label,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, color = nc.textPrimary),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                            )
                            Text(item.detail, style = MaterialTheme.typography.labelMedium, color = nc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Biblioteca: grade 2×3 de linhas compactas, com a contagem à direita em números tabulares. */
@Composable
private fun LibraryGrid(title: String, items: List<SvShortcut>) {
    val nc = SonivellTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SvSection(title)
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { item ->
                    SvCard(Modifier.weight(1f).height(52.dp), onClick = item.onClick, elevation = 0.dp) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(item.detail, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = nc.textSecondary, maxLines = 1)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** "SMART" como rótulo cinza em caixa-alta, igual aos outros rótulos: nada de selo com borda que pareça botão. */
@Composable
private fun SvSmartTag() {
    val nc = SonivellTheme.colors
    Text(stringResource(R.string.source_smart), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.8.sp), fontWeight = FontWeight.ExtraBold, color = nc.textSecondary)
}

private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}

/** Contagem com separador de milhar no idioma do aparelho ("1.990"). */
private fun count(n: Int): String = String.format(java.util.Locale.getDefault(), "%,d", n)

/** "12 músicas", com separador de milhar. */
@Composable
private fun songsLabel(n: Int): String = pluralStringResource(R.plurals.songs_count, n, n)
