package com.juscelino.sonivell.ui.extras

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.theme.SonivellTheme

/**
 * Modo carro/academia: fundo preto, capa grande e botões enormes, para usar de relance.
 * A tela fica acesa enquanto ele está aberto.
 */
@Composable
fun CarModeScreen(
    track: Track?,
    isPlaying: Boolean,
    liked: Boolean,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit,
    onMixNow: () -> Unit,
    onExit: () -> Unit,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.car_mode).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = c.accent, modifier = Modifier.weight(1f))
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xFF1A1C1F)).clickable(role = Role.Button, onClick = onExit).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.car_mode_exit), style = MaterialTheme.typography.titleSmall, color = Color.White)
            }
        }
        AlbumArt(track, Modifier.weight(1f, fill = false).widthIn(max = 420.dp).aspectRatio(1f), corner = 28.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track?.title ?: stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(track?.artist.orEmpty(), fontSize = 18.sp, color = Color(0xFF9BA1A9), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Big(icons.skipPrevious, stringResource(R.string.cd_previous), false, Modifier.weight(1f), onPrevious)
            Big(if (isPlaying) icons.pause else icons.play, stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play), true, Modifier.weight(1f), onTogglePlay)
            Big(icons.skipNext, stringResource(R.string.cd_next), false, Modifier.weight(1f), onNext)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Small(stringResource(if (liked) R.string.cd_unlike else R.string.cd_like), Modifier.weight(1f), onLike)
            Small(stringResource(R.string.shortcut_mix_now), Modifier.weight(1f), onMixNow)
        }
    }
}

@Composable
private fun Big(icon: ImageVector, label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Box(
        modifier
            .height(110.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (primary) c.accent else Color(0xFF1A1C1F))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(44.dp), tint = if (primary) c.onAccent else Color.White)
    }
}

@Composable
private fun Small(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(64.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1A1C1F)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}
