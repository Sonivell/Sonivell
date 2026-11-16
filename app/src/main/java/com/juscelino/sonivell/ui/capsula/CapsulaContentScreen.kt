package com.juscelino.sonivell.ui.capsula

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.AppSettings
import com.juscelino.sonivell.data.CapsulaSection
import com.juscelino.sonivell.ui.theme.SonivellTheme

/**
 * "Mostrar no início" do Cápsula: liga e desliga cada seção do Início, e leva às recomendações (o que
 * o SMART aprende e o que fica oculto) e às opções de áudio. Tudo local: no lugar de podcasts e vídeos
 * do mockup, as seções vêm da própria biblioteca.
 */
@Composable
fun CapsulaContentScreen(
    settings: AppSettings,
    smartAvailable: Boolean,
    hiddenCount: Int,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenHidden: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    val c = SonivellTheme.colors
    val icons = SonivellTheme.icons
    fun toggle(key: String, on: Boolean) = onUpdate { s -> s.copy(capsulaHome = if (on) s.capsulaHome + key else s.capsulaHome - key) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CapsulaHeader(stringResource(R.string.cap_content_title), onBack = onBack)

        GlassGroup(title = stringResource(R.string.cap_show_on_home)) {
            val rows = buildList {
                add(Triple(CapsulaSection.CONTINUE, icons.recent, R.string.cap_resume))
                add(Triple(CapsulaSection.FAVORITES, icons.liked, R.string.cap_favorites))
                add(Triple(CapsulaSection.PLAYLISTS, icons.addToPlaylist, R.string.cap_short_playlists))
                if (smartAvailable) add(Triple(CapsulaSection.MIXES, icons.smart, R.string.cap_mixes))
                add(Triple(CapsulaSection.ALBUMS, icons.album, R.string.sv_albums))
                add(Triple(CapsulaSection.ARTISTS, icons.artist, R.string.sv_artists))
            }
            rows.forEachIndexed { i, (key, icon, label) ->
                if (i > 0) Divider()
                SwitchLine(icon, stringResource(label), key in settings.capsulaHome, { on -> toggle(key, on) })
            }
        }

        GlassGroup(title = stringResource(R.string.cap_recommendations)) {
            if (smartAvailable) {
                SwitchLine(
                    icons.smart,
                    stringResource(R.string.cap_personalize),
                    settings.learnTaste,
                    { on -> onUpdate { it.copy(learnTaste = on) } },
                    hint = stringResource(R.string.cap_personalize_hint),
                )
                Divider()
            }
            NavLine(icons.block, stringResource(R.string.cap_hidden), stringResource(R.string.cap_hidden_hint, hiddenCount), onOpenHidden)
        }

        GlassGroup(title = stringResource(R.string.cap_audio)) {
            NavLine(icons.volumeUp, stringResource(R.string.settings_playback), stringResource(R.string.cap_audio_hint), onOpenAudio)
        }

        GlassGroup(title = stringResource(R.string.settings_appearance)) {
            NavLine(icons.appearance, stringResource(R.string.settings_layout), stringResource(R.string.layout_capsula), onOpenAppearance)
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = SonivellTheme.colors.textPrimary.copy(alpha = 0.06f))
}

@Composable
private fun NavLine(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBubble(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
            Text(hint, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        }
        Icon(SonivellTheme.icons.chevron, null, Modifier.size(18.dp), tint = c.textSecondary)
    }
}
