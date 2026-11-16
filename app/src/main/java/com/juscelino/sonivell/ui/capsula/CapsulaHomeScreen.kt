package com.juscelino.sonivell.ui.capsula

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.LibraryState
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.CapsulaSection
import com.juscelino.sonivell.data.ForYouState
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.ArtBlur
import com.juscelino.sonivell.ui.components.CoverCollage
import com.juscelino.sonivell.ui.library.LibraryActions
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.util.Calendar

/**
 * Início do Cápsula: saudação, quatro atalhos em vidro, o cartão "Retomar" com a capa desfocada e as
 * seções que o usuário deixou ligadas em "Mostrar no início" (favoritos, playlists, álbuns, artistas,
 * mixes pelo som). Tudo vem da biblioteca local e do histórico; nada de streaming.
 */
@Composable
fun CapsulaHomeScreen(
    state: LibraryState,
    favoritesCount: Int,
    playlists: List<Playlist>,
    forYou: ForYouState,
    sections: Set<String>,
    smartAvailable: Boolean,
    currentId: Long?,
    contentPadding: PaddingValues,
    actions: LibraryActions,
    playlistTracks: (Playlist) -> List<Track>,
    onOpenLibraryPlaylists: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenContent: () -> Unit,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val resume = forYou.unfinished.firstOrNull { it.id != currentId }
    val resumeAt = resume?.let { forYou.resumeAtMs[it.id] } ?: 0L
    val recentPlaylists = remember(playlists) { playlists.sortedByDescending { it.updatedAt }.take(6) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Saudação e o botão de "Mostrar no início".
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(greeting()),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, letterSpacing = (-0.6).sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = c.textPrimary,
                    )
                    Text(stringResource(R.string.cap_home_sub), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                }
                GlassCircle(icons.settings, stringResource(R.string.cap_content_title), onOpenContent)
            }
        }

        // Atalhos: continuar · curtidas · playlists · explorar.
        item(key = "shortcuts") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Shortcut(icons.recent, stringResource(R.string.cap_short_continue), Modifier.weight(1f)) {
                    if (resume != null) actions.onResume(resume, resumeAt) else actions.onOpenAuto("recentes")
                }
                Shortcut(icons.liked, stringResource(R.string.source_liked), Modifier.weight(1f)) { actions.onOpenAuto("curtidas") }
                Shortcut(icons.addToPlaylist, stringResource(R.string.cap_short_playlists), Modifier.weight(1f), onOpenLibraryPlaylists)
                Shortcut(icons.search, stringResource(R.string.nav_explore), Modifier.weight(1f), actions.onOpenSearch)
            }
        }

        if (CapsulaSection.CONTINUE in sections && resume != null) {
            item(key = "resume") {
                ResumeCard(resume, resumeAt, Modifier.padding(horizontal = 20.dp)) { actions.onResume(resume, resumeAt) }
            }
        }

        if (CapsulaSection.FAVORITES in sections) {
            item(key = "fav-title") { CapTitle(stringResource(R.string.cap_favorites), Modifier.padding(horizontal = 20.dp)) }
            item(key = "fav") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallTile(
                        title = stringResource(R.string.source_liked),
                        subtitle = pluralStringResource(R.plurals.songs_count, favoritesCount, favoritesCount),
                        modifier = Modifier.weight(1f),
                        art = { IconBubble(icons.liked, tint = Color.White, background = c.accent.copy(alpha = 0.85f), size = 46.dp) },
                    ) { actions.onOpenAuto("curtidas") }
                    SmallTile(
                        title = stringResource(R.string.auto_most),
                        subtitle = stringResource(R.string.sv_most_sub),
                        modifier = Modifier.weight(1f),
                        art = { IconBubble(icons.trendUp, tint = c.accent, background = c.accent.copy(alpha = 0.16f), size = 46.dp) },
                    ) { actions.onOpenAuto("mais-ouvidas") }
                }
            }
        }

        if (CapsulaSection.PLAYLISTS in sections) {
            item(key = "pl-title") {
                CapTitle(
                    stringResource(R.string.cap_recent_playlists),
                    Modifier.padding(horizontal = 20.dp),
                    action = if (playlists.isNotEmpty()) stringResource(R.string.cap_see_all) else null,
                    onAction = onOpenLibraryPlaylists,
                )
            }
            item(key = "pl") {
                if (recentPlaylists.isEmpty()) {
                    PillButton(
                        stringResource(R.string.action_playlist),
                        icons.add,
                        actions.onNewPlaylist,
                        Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    )
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentPlaylists, key = { it.id }) { p ->
                            PlaylistCard(p, playlistTracks(p)) { actions.onOpenPlaylist(p.id) }
                        }
                    }
                }
            }
        }

        if (CapsulaSection.MIXES in sections && smartAvailable && forYou.mixSeeds.isNotEmpty()) {
            item(key = "mix-title") { CapTitle(stringResource(R.string.cap_mixes), Modifier.padding(horizontal = 20.dp)) }
            item(key = "mix") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(forYou.mixSeeds.take(10), key = { "m" + it.id }) { seed ->
                        CoverCard(seed, stringResource(R.string.fy_mix_of, seed.artist), seed.title) { actions.onPlayMix(seed) }
                    }
                }
            }
        }

        if (CapsulaSection.ALBUMS in sections && state.recentAlbums.isNotEmpty()) {
            item(key = "al-title") {
                CapTitle(stringResource(R.string.cap_recent_albums), Modifier.padding(horizontal = 20.dp), action = stringResource(R.string.cap_see_all), onAction = onOpenLibrary)
            }
            item(key = "al") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recentAlbums.take(12), key = { "a" + it.id }) { album ->
                        CoverCard(album.tracks.firstOrNull(), album.title, album.artist) { actions.onOpenAlbum(album.id) }
                    }
                }
            }
        }

        if (CapsulaSection.ARTISTS in sections && state.artists.isNotEmpty()) {
            item(key = "ar-title") { CapTitle(stringResource(R.string.sv_artists), Modifier.padding(horizontal = 20.dp)) }
            item(key = "ar") {
                // Os artistas com mais faixas primeiro: os que a pessoa mais tem.
                val top = remember(state.artists) { state.artists.sortedByDescending { it.tracks.size }.take(16) }
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(top, key = { "r" + it.name }) { artist ->
                        Column(
                            Modifier.width(84.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button) { actions.onOpenArtist(artist.name) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AlbumArt(artist.tracks.firstOrNull(), Modifier.size(76.dp), corner = 38.dp)
                            Text(artist.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Column(
        modifier
            .heightIn(min = 76.dp)
            .capsulaGlass(RoundedCornerShape(20.dp), elevation = 4.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = c.accent)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Retomar": a faixa que ficou pela metade, com a capa desfocada de fundo e o progresso. */
@Composable
private fun ResumeCard(track: Track, atMs: Long, modifier: Modifier, onResume: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val fraction = if (track.durationMs > 0) (atMs.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f
    val label = stringResource(R.string.cap_resume)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onResume),
    ) {
        ArtBlur(track, Modifier.matchParentSize(), fallback = Color(0xFF1F2825))
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.40f)))
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AlbumArt(track, Modifier.size(68.dp), corner = 14.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.8f))
                    Text(track.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(50.dp).clip(CircleShape).background(Color.White).semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { Icon(SonivellTheme.icons.play, null, Modifier.size(22.dp), tint = Color(0xFF0A0D0C)) }
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(Pill).background(Color.White.copy(alpha = 0.22f))) {
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(Pill).background(Color.White))
            }
        }
    }
}

@Composable
private fun SmallTile(title: String, subtitle: String, modifier: Modifier, art: @Composable () -> Unit, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        modifier
            .capsulaGlass(RoundedCornerShape(18.dp), elevation = 4.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        art()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Playlist em cartão quadrado: o mosaico das capas e o nome por cima, num degradê escuro. */
@Composable
private fun PlaylistCard(playlist: Playlist, tracks: List<Track>, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .size(148.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        val covers = remember(tracks) { tracks.distinctBy { it.albumId }.take(4) }
        if (covers.isNotEmpty()) CoverCollage(covers, Modifier.fillMaxSize(), corner = 0.dp)
        else Box(Modifier.fillMaxSize().background(SonivellTheme.colors.surfaceHigh))
        Box(
            Modifier.matchParentSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f)),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.songs_count, playlist.trackIds.size, playlist.trackIds.size),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}

/** Capa com título e apoio embaixo (álbuns e mixes). */
@Composable
private fun CoverCard(track: Track?, title: String, subtitle: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Column(
        Modifier.width(132.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AlbumArt(track, Modifier.fillMaxWidth().aspectRatio(1f), corner = 18.dp)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
    }
}

private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> R.string.greeting_morning
    in 12..17 -> R.string.greeting_afternoon
    else -> R.string.greeting_evening
}
