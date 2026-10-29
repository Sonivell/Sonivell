package com.juscelino.sonivell.ui.sonivell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.ForYouState
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.theme.NoraColors
import com.juscelino.sonivell.ui.theme.SonivellTheme

/** Seu Flow de hoje: uma sequência do "Para você" (o que você ouve neste horário), com um botão só. */
@Composable
fun SvFlowScreen(
    forYou: ForYouState,
    allTracks: List<Track>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onResume: (Track, Long) -> Unit,
    onCustomize: () -> Unit,
) {
    val icons = SonivellTheme.icons
    val flow = forYou.thisHour.ifEmpty { listOfNotNull(forYou.pick) }.ifEmpty { allTracks.take(30) }
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
        SvTitle(stringResource(R.string.nav_home), stringResource(R.string.sv_flow_sub))

        Box(Modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
            AlbumArt(flow.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1.6f), corner = 24.dp, detailed = true)
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color(0x99000000)).clickable(role = Role.Button) { if (flow.isNotEmpty()) onPlay(flow, 0) },
                contentAlignment = Alignment.Center,
            ) { Icon(icons.play, stringResource(R.string.cd_play), Modifier.size(30.dp), tint = Color.White) }
        }
        Text(stringResource(R.string.sv_flow_title), style = MaterialTheme.typography.titleLarge, color = NoraColors.Text)
        Text(stringResource(R.string.sv_flow_desc), style = MaterialTheme.typography.bodyMedium, color = NoraColors.Muted)
        SvPill(stringResource(R.string.sv_play_now), { if (flow.isNotEmpty()) onPlay(flow, 0) }, Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.sv_customize),
            style = MaterialTheme.typography.labelLarge,
            color = NoraColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable(role = Role.Button, onClick = onCustomize).padding(14.dp),
        )

        if (forYou.unfinished.isNotEmpty()) {
            Text(stringResource(R.string.fy_continue), style = MaterialTheme.typography.titleLarge, color = NoraColors.Text)
            forYou.unfinished.take(4).forEach { t ->
                SvRow(t.title, t.artist, { onResume(t, forYou.resumeAtMs[t.id] ?: 0L) }, art = { SvCircleArt(t, 48.dp) })
            }
        }
        if (forYou.forgottenFavorites.isNotEmpty()) {
            Text(stringResource(R.string.fy_forgotten), style = MaterialTheme.typography.titleLarge, color = NoraColors.Text)
            forYou.forgottenFavorites.take(4).forEachIndexed { i, t ->
                SvRow(t.title, t.artist, { onPlay(forYou.forgottenFavorites, i) }, art = { SvCircleArt(t, 48.dp) })
            }
        }
    }
}
