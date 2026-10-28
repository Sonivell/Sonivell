package com.juscelino.sonivell.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.juscelino.sonivell.data.Track
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.AppSettings
import com.juscelino.sonivell.data.CROSSFADE_STEPS
import com.juscelino.sonivell.data.TasteModel
import com.juscelino.sonivell.data.UiStyle
import com.juscelino.sonivell.ui.components.Chip
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.components.SectionLabel
import com.juscelino.sonivell.ui.theme.AccentChoice
import com.juscelino.sonivell.ui.theme.DarkBackground
import com.juscelino.sonivell.ui.theme.IconStyle
import com.juscelino.sonivell.ui.theme.icons
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.ThemeMode

data class SettingsActions(
    val onBack: () -> Unit,
    val onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    val onOpenEqualizer: () -> Unit,
    val onOpenStats: () -> Unit,
    val onRefreshLibrary: () -> Unit,
    val onClearHistory: () -> Unit,
    val onExportHistory: () -> Unit,
    val onReset: () -> Unit,
    val onPickLyricsFolder: () -> Unit = {},
    val onOpenOrganize: () -> Unit = {},
    val onUnblockTrack: (Long) -> Unit = {},
    val onUnblockArtist: (String) -> Unit = {},
    val onRetrainTaste: () -> Unit = {},
    val onForgetTaste: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    smartAvailable: Boolean,
    versionName: String,
    contentPadding: PaddingValues,
    actions: SettingsActions,
    blockedTracks: List<Track> = emptyList(),
    blockedArtists: List<String> = emptyList(),
    tasteStatus: TasteModel.Status = TasteModel.Status(),
) {
    val c = SonivellTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBtn(SonivellTheme.icons.back, stringResource(R.string.cd_back), actions.onBack, background = c.surface)
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.displaySmall, color = c.textPrimary)
        }

        Section(stringResource(R.string.settings_appearance)) {
            RowLabel(stringResource(R.string.settings_layout))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(UiStyle.ONDAS, R.string.layout_ondas, R.string.layout_ondas_hint),
                    Triple(UiStyle.ESSENCIAL, R.string.layout_essencial, R.string.layout_essencial_hint),
                    Triple(UiStyle.CONTRASTE, R.string.layout_contrast, R.string.layout_contrast_hint),
                    Triple(UiStyle.CLASSICA, R.string.layout_classic, R.string.layout_classic_hint),
                ).forEach { (style, name, hint) ->
                    LayoutOption(stringResource(name), stringResource(hint), settings.uiStyle == style) {
                        actions.onUpdate { it.copy(uiStyle = style) }
                    }
                }
            }
            RowLabel(stringResource(R.string.settings_theme))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.AUTO to R.string.theme_auto,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    Chip(stringResource(label), selected = settings.theme == mode) {
                        actions.onUpdate { it.copy(theme = mode) }
                    }
                }
            }
            RowLabel(stringResource(R.string.settings_dark_background))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    DarkBackground.Grafite to R.string.background_graphite,
                    DarkBackground.Preto to R.string.background_black,
                ).forEach { (bg, label) ->
                    Chip(stringResource(label), selected = settings.darkBackground == bg) {
                        actions.onUpdate { it.copy(darkBackground = bg) }
                    }
                }
            }
            if (!c.isDark) {
                Text(stringResource(R.string.settings_dark_background_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            RowLabel(stringResource(R.string.settings_accent))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentChoice.entries.forEach { choice ->
                    val color = if (c.isDark) choice.dark else choice.light
                    val selected = settings.accent == choice
                    val label = stringResource(accentNames[choice.ordinal])
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(if (selected) Modifier.border(3.dp, c.textPrimary, CircleShape) else Modifier)
                            .clickable(role = Role.RadioButton) { actions.onUpdate { it.copy(accent = choice) } }
                            .semantics { contentDescription = label; this.selected = selected },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(SonivellTheme.icons.check, null, Modifier.size(20.dp), tint = c.onAccent)
                    }
                }
            }
            RowLabel(stringResource(R.string.settings_icon_style))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconStyle.entries.forEach { style ->
                    IconStyleOption(
                        style = style,
                        label = stringResource(iconStyleNames[style.ordinal]),
                        selected = settings.iconStyle == style,
                        modifier = Modifier.weight(1f),
                    ) { actions.onUpdate { it.copy(iconStyle = style) } }
                }
            }
        }

        Section(stringResource(R.string.settings_playback)) {
            SwitchRow(
                stringResource(R.string.settings_pause_unplug),
                stringResource(R.string.settings_pause_unplug_hint),
                settings.pauseOnUnplug,
            ) { on -> actions.onUpdate { it.copy(pauseOnUnplug = on) } }
            SwitchRow(
                stringResource(R.string.settings_normalize),
                stringResource(R.string.settings_normalize_hint),
                settings.normalize,
            ) { on -> actions.onUpdate { it.copy(normalize = on) } }
            SwitchRow(
                stringResource(R.string.settings_crossfade),
                stringResource(R.string.settings_crossfade_hint),
                settings.crossfade,
            ) { on -> actions.onUpdate { it.copy(crossfade = on) } }
            if (settings.crossfade) {
                Text(
                    stringResource(
                        if (smartAvailable && settings.crossfadeSmart) R.string.settings_crossfade_length_smart
                        else R.string.settings_crossfade_length,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CROSSFADE_STEPS.forEach { seconds ->
                        Chip(stringResource(R.string.seconds_short, seconds), selected = settings.crossfadeSeconds == seconds) {
                            actions.onUpdate { it.copy(crossfadeSeconds = seconds) }
                        }
                    }
                }
                if (smartAvailable) {
                    SwitchRow(
                        stringResource(R.string.settings_crossfade_smart),
                        stringResource(R.string.settings_crossfade_smart_hint),
                        settings.crossfadeSmart,
                    ) { on -> actions.onUpdate { it.copy(crossfadeSmart = on) } }
                }
                SwitchRow(
                    stringResource(R.string.settings_crossfade_albums),
                    stringResource(R.string.settings_crossfade_albums_hint),
                    settings.crossfadeKeepAlbums,
                ) { on -> actions.onUpdate { it.copy(crossfadeKeepAlbums = on) } }
            }
            LinkRow(stringResource(R.string.equalizer), stringResource(R.string.equalizer_hint), actions.onOpenEqualizer)
        }

        if (smartAvailable) {
            Section(stringResource(R.string.source_smart)) {
                RowLabel(stringResource(R.string.settings_artist_variety))
                Text(stringResource(R.string.settings_artist_variety_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.variety_0, R.string.variety_1, R.string.variety_2, R.string.variety_3, R.string.variety_4)
                        .forEachIndexed { level, label ->
                            Chip(stringResource(label), selected = settings.artistVariety == level) {
                                actions.onUpdate { it.copy(artistVariety = level) }
                            }
                        }
                }
            }
            Section(stringResource(R.string.taste_section)) {
                SwitchRow(
                    stringResource(R.string.taste_learn),
                    stringResource(R.string.taste_learn_hint),
                    settings.learnTaste,
                ) { on -> actions.onUpdate { it.copy(learnTaste = on) } }
                if (settings.learnTaste) {
                    Text(tasteStatusText(tasteStatus), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    LinkRow(stringResource(R.string.taste_train_now), stringResource(R.string.taste_train_now_hint), actions.onRetrainTaste)
                }
                if (tasteStatus.trained) {
                    LinkRow(stringResource(R.string.taste_forget), stringResource(R.string.taste_forget_hint), actions.onForgetTaste)
                }
            }
        }

        Section(stringResource(R.string.not_recommended)) {
            Text(stringResource(R.string.not_recommended_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            if (blockedTracks.isEmpty() && blockedArtists.isEmpty()) {
                Text(stringResource(R.string.not_recommended_none), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            }
            blockedArtists.forEach { name ->
                RemovableRow(name, stringResource(R.string.tab_artists)) { actions.onUnblockArtist(name) }
            }
            blockedTracks.forEach { t ->
                RemovableRow(t.title, t.artist) { actions.onUnblockTrack(t.id) }
            }
        }

        Section(stringResource(R.string.settings_history)) {
            LinkRow(stringResource(R.string.stats), stringResource(R.string.stats_hint), actions.onOpenStats)
            SwitchRow(
                stringResource(R.string.settings_history_pause),
                stringResource(R.string.settings_history_pause_hint),
                settings.historyPaused,
            ) { on -> actions.onUpdate { it.copy(historyPaused = on) } }
            LinkRow(stringResource(R.string.settings_history_export), null, actions.onExportHistory)
            LinkRow(stringResource(R.string.settings_history_clear), null, actions.onClearHistory)
        }

        Section(stringResource(R.string.nav_library)) {
            LinkRow(stringResource(R.string.cd_refresh), stringResource(R.string.settings_refresh_hint), actions.onRefreshLibrary)
            LinkRow(stringResource(R.string.lyrics_pick_folder), stringResource(R.string.lyrics_folder_hint), actions.onPickLyricsFolder)
            LinkRow(stringResource(R.string.organize), stringResource(R.string.organize_hint), actions.onOpenOrganize)
        }

        Section(stringResource(R.string.settings_about)) {
            Text(stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            if (smartAvailable) {
                Text(stringResource(R.string.settings_credits_smart), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            LinkRow(stringResource(R.string.settings_reset), null, actions.onReset)
        }
    }
}

/** Nomes dos estilos de ícone, na ordem de [IconStyle]. */
private val iconStyleNames = listOf(R.string.icon_style_fine, R.string.icon_style_rounded, R.string.icon_style_line)

/** Uma opção de layout: nome e uma linha do que muda; a escolhida ganha contorno na cor de destaque. */
@Composable
private fun LayoutOption(name: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.surfaceHigh else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        if (selected) Icon(SonivellTheme.icons.check, null, Modifier.size(20.dp), tint = c.accent)
    }
}

/** Cartão com uma amostra dos ícones do estilo, para comparar antes de escolher. */
@Composable
private fun IconStyleOption(
    style: IconStyle,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = SonivellTheme.colors
    val icons = style.icons()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) c.surfaceHigh else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(icons.play, icons.liked, icons.shuffle).forEach {
                Icon(it, null, Modifier.size(20.dp), tint = if (selected) c.accent else c.textPrimary)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = c.textPrimary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Nomes das cores de destaque, na ordem de [AccentChoice]. */
private val accentNames = listOf(
    R.string.accent_lime, R.string.accent_blue, R.string.accent_orange,
    R.string.accent_lilac, R.string.accent_pink, R.string.accent_teal,
    R.string.accent_green,
)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = SonivellTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(title)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun RowLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = SonivellTheme.colors.textPrimary)
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.textSecondary,
                uncheckedTrackColor = c.surfaceHigh,
                uncheckedBorderColor = c.outline,
            ),
        )
    }
}

@Composable
private fun RemovableRow(title: String, subtitle: String, onRemove: () -> Unit) {
    val c = SonivellTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1)
        }
        IconBtn(SonivellTheme.icons.close, stringResource(R.string.allow_again, title), onRemove, tint = c.textSecondary, iconSize = 18.dp)
    }
}

/** "Treinado com 320 escutas há 2 h · acerta 74% dos pulos" (ou o que falta para começar). */
@Composable
private fun tasteStatusText(st: TasteModel.Status): String {
    if (!st.trained) return stringResource(R.string.taste_not_yet)
    val ago = android.text.format.DateUtils.getRelativeTimeSpanString(
        st.trainedAtMs, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    val base = stringResource(R.string.taste_trained, st.examples, ago)
    return when {
        st.profileOnly -> base + " · " + stringResource(R.string.taste_profile_only)
        st.accuracy != null -> base + " · " + stringResource(R.string.taste_accuracy, (st.accuracy * 100).toInt())
        else -> base
    }
}

@Composable
private fun LinkRow(title: String, hint: String?, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        Icon(SonivellTheme.icons.chevron, null, tint = c.textSecondary)
    }
}
