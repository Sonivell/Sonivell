package com.juscelino.sonivell.ui.sonivell

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
import androidx.compose.ui.graphics.lerp
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
 * Início do tema Sonivell (redesign): capas logo no topo, numa grade de atalhos (coleções e álbuns
 * recentes); o Flow de hoje como destaque, com o mosaico das capas e o único play na cor de destaque;
 * "Continuar ouvindo" só quando não é a faixa que já está tocando; os mixes pelo som e o mapa.
 * A grade "Sua biblioteca" saiu daqui: os mesmos filtros estão na aba Biblioteca.
 */
@Composable
fun SvHomeScreen(
    contentPadding: PaddingValues,
    state: LibraryState,
    favoritesCount: Int,
    /** Quantas playlists o usuário criou (mantido para a chamada; a grade da biblioteca saiu do Início). */
    @Suppress("UNUSED_PARAMETER") playlistCount: Int,
    mixSeeds: List<Track>,
    smartAvailable: Boolean,
    smartProgress: SmartProgress,
    /** Coleção automática por chave ("curtidas", "mais-ouvidas", "recentes"). */
    onOpenAuto: (String) -> Unit,
    /** Biblioteca no filtro dado (mantido para a chamada; os filtros ficam na aba Biblioteca). */
    @Suppress("UNUSED_PARAMETER") onOpenLibrary: (Int) -> Unit,
    onPlayMix: (Track) -> Unit,
    onOpenFlow: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenAnalysis: () -> Unit,
    /** Faixa que ficou pela metade e onde ela parou: o "Continuar ouvindo". */
    continueTrack: Track? = null,
    continueAtMs: Long = 0L,
    onResume: (Track, Long) -> Unit = { _, _ -> },
    /** Faixa tocando agora: o "Continuar ouvindo" some quando seria ela mesma. */
    currentId: Long? = null,
    onOpenAlbum: (Long) -> Unit = {},
) {
    val nc = SonivellTheme.colors
    val icons = SonivellTheme.icons
    // Adicionadas nos últimos 30 dias: a mesma regra da coleção automática "recentes".
    val newCount = remember(state.allTracks) {
        val since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        state.allTracks.count { it.dateAdded * 1000 >= since }
    }
    // Tons dos ícones das coleções: um por coleção, mais escuros no tema claro para o ícone seguir legível.
    val amber = if (nc.isDark) Color(0xFFF5B841) else Color(0xFF8A5A00)
    val blue = if (nc.isDark) Color(0xFF7DB4FF) else Color(0xFF0B5FAA)
    val quick = buildList {
        add(SvQuick(stringResource(R.string.source_liked), songsLabel(favoritesCount), { onOpenAuto("curtidas") }, icon = icons.liked, tint = nc.accent))
        add(SvQuick(stringResource(R.string.auto_most), stringResource(R.string.sv_most_sub), { onOpenAuto("mais-ouvidas") }, icon = icons.trendUp, tint = amber))
        add(SvQuick(stringResource(R.string.sv_new), stringResource(R.string.sv_new_sub, newCount), { onOpenAuto("recentes") }, icon = icons.recent, tint = blue))
        state.recentAlbums.take(3).forEach { album ->
            add(SvQuick(album.title, album.artist, { onOpenAlbum(album.id) }, cover = album.tracks.firstOrNull()))
        }
    }
    // Capas do mosaico do Flow: as dos mixes e dos álbuns recentes, um álbum por capa.
    val flowCovers = remember(mixSeeds, state.recentAlbums) {
        (mixSeeds + state.recentAlbums.mapNotNull { it.tracks.firstOrNull() }).distinctBy { it.albumId }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Cabeçalho: saudação pequena e o título grande.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(greeting()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = nc.textSecondary)
            Text(
                stringResource(R.string.sv_home_title),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.6).sp),
                color = nc.textPrimary,
            )
        }

        // Atalhos 2×N: coleções com ícone próprio e os álbuns recentes com a capa.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { QuickTile(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (!smartAvailable) {
            Text(stringResource(R.string.sv_analysis_lite), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary)
        }

        // Seu Flow de hoje: o destaque da tela. Mosaico das capas, o texto e um play redondo, o único
        // elemento cheio na cor de destaque da tela.
        val flowLabel = stringResource(R.string.sv_flow_play)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(lerp(nc.surface, nc.accent, 0.10f))
                .clickable(role = Role.Button, onClickLabel = flowLabel, onClick = onOpenFlow),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (flowCovers.isNotEmpty()) CoverCollage(flowCovers, Modifier.size(96.dp), corner = 14.dp)
                    else SvIconTile(icons.smart, 96.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.sv_flow_title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp), fontWeight = FontWeight.ExtraBold, color = nc.textPrimary)
                        Text(stringResource(R.string.sv_flow_sub), style = MaterialTheme.typography.bodyMedium, color = nc.textSecondary, maxLines = 2)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.sv_flow_desc),
                        style = MaterialTheme.typography.labelMedium,
                        color = nc.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    PlaySquircle(false, onOpenFlow, size = 56.dp, iconSize = 22.dp, contentDescription = flowLabel)
                }
            }
        }

        // Continuar ouvindo: só quando não é a faixa que já está no mini player.
        continueTrack?.takeIf { it.id != currentId }?.let { t ->
            SvSection(stringResource(R.string.fy_continue), Modifier.padding(top = 4.dp))
            val fraction = if (t.durationMs > 0) (continueAtMs.toFloat() / t.durationMs).coerceIn(0f, 1f) else 0f
            SvCard(Modifier.fillMaxWidth(), onClick = { onResume(t, continueAtMs) }, elevation = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    com.juscelino.sonivell.ui.components.AlbumArt(t, Modifier.size(56.dp), corner = 12.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(nc.track)) {
                            Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(nc.textPrimary))
                        }
                        Text(
                            t.artist + " · " + stringResource(R.string.ess_remaining, com.juscelino.sonivell.data.formatDuration((t.durationMs - continueAtMs).coerceAtLeast(0L))),
                            style = MaterialTheme.typography.labelMedium,
                            color = nc.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Play neutro: o destaque da tela é o Flow.
                    PlaySquircle(false, { onResume(t, continueAtMs) }, size = 44.dp, iconSize = 16.dp, background = nc.surfaceHigh, tint = nc.textPrimary)
                }
            }
        }

        // Mixes pelo som (edição SMART): capas limpas, sem botão por cima; o cartão inteiro toca.
        if (smartAvailable && mixSeeds.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SvSection(stringResource(R.string.fy_mixes), Modifier.padding(top = 4.dp)) { SvSmartTag() }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(mixSeeds, key = { _, seed -> "mix-" + seed.id }) { _, seed ->
                        val label = stringResource(R.string.fy_mix_of, seed.artist)
                        Column(
                            Modifier.width(144.dp)
                                .clickable(role = Role.Button, onClickLabel = label) { onPlayMix(seed) },
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val covers = remember(state.allTracks, seed.artist) {
                                listOf(seed) + state.allTracks.filter { it.artist == seed.artist && it.albumId != seed.albumId }
                            }
                            CoverCollage(covers, Modifier.size(144.dp), corner = 16.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(seed.artist, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            elevation = 0.dp,
            trailing = { Icon(icons.chevron, null, Modifier.size(20.dp), tint = nc.textSecondary) },
        )
        if (smartAvailable && (smartProgress.running || smartProgress.analysed < smartProgress.total)) {
            SvRow(
                title = stringResource(R.string.sv_analysis_title),
                subtitle = stringResource(R.string.sv_analysis_count, smartProgress.analysed, smartProgress.total),
                onClick = onOpenAnalysis,
                art = { SvIconTile(icons.smart) },
                elevation = 0.dp,
                trailing = { Icon(icons.chevron, null, Modifier.size(20.dp), tint = nc.textSecondary) },
            )
        }
    }
}

/** Um atalho do Início: coleção (ícone num quadrado tingido) ou álbum (capa). */
private class SvQuick(
    val label: String,
    val detail: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val tint: Color = Color.Unspecified,
    val cover: Track? = null,
)

/** Atalho compacto: arte quadrada colada à esquerda, nome e detalhe; superfície sem contorno. */
@Composable
private fun QuickTile(item: SvQuick, modifier: Modifier = Modifier) {
    val nc = SonivellTheme.colors
    Row(
        modifier
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(nc.surface)
            .clickable(role = Role.Button, onClick = item.onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(60.dp).then(if (item.icon != null) Modifier.background(item.tint.copy(alpha = 0.16f)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (item.icon != null) Icon(item.icon, null, Modifier.size(22.dp), tint = item.tint)
            else com.juscelino.sonivell.ui.components.AlbumArt(item.cover, Modifier.fillMaxSize(), corner = 0.dp)
        }
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = nc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.detail, style = MaterialTheme.typography.labelMedium, color = nc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
