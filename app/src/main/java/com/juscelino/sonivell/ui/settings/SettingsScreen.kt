package com.juscelino.sonivell.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.juscelino.sonivell.system.PlaylistPublisher
import androidx.compose.ui.res.pluralStringResource
import com.juscelino.sonivell.data.MIN_DURATION_STEPS
import com.juscelino.sonivell.data.SPEED_STEPS
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
    val onOpenCarMode: () -> Unit = {},
    val onOpenRetro: () -> Unit = {},
    val onBackup: () -> Unit = {},
    val onRestore: () -> Unit = {},
)

/** Páginas dos ajustes: o índice e uma página por assunto. */
private enum class SettingsPage { Index, Aparencia, Reproducao, Transicoes, Smart, Biblioteca, Historico, Dados, Sobre }

/**
 * Ajustes em páginas, como nos ajustes do Android: o índice mostra cada assunto com o valor
 * atual em uma linha, e cada página tem só as opções daquele assunto. O cabeçalho fica fixo
 * (o conteúdo rola por baixo dele, nunca por baixo da barra de status).
 */
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
    /** Todas as pastas com áudio (inclusive as ignoradas). */
    folders: List<String> = emptyList(),
) {
    val c = SonivellTheme.colors
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.Index.name) }
    val page = SettingsPage.valueOf(pageName)
    fun open(p: SettingsPage) { pageName = p.name }
    BackHandler(enabled = page != SettingsPage.Index) { open(SettingsPage.Index) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBtn(
                SonivellTheme.icons.back,
                stringResource(R.string.cd_back),
                { if (page == SettingsPage.Index) actions.onBack() else open(SettingsPage.Index) },
                background = c.surface,
            )
            Text(
                stringResource(pageTitle(page)),
                style = if (page == SettingsPage.Index) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        key(page) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (page) {
                    SettingsPage.Index -> IndexPage(settings, smartAvailable, versionName, actions, ::open)
                    SettingsPage.Aparencia -> AppearancePage(settings, actions)
                    SettingsPage.Reproducao -> PlaybackPage(settings, actions)
                    SettingsPage.Transicoes -> TransitionsPage(settings, smartAvailable, actions)
                    SettingsPage.Smart -> SmartPage(settings, smartAvailable, tasteStatus, blockedTracks, blockedArtists, actions)
                    SettingsPage.Biblioteca -> LibraryPage(settings, folders, actions)
                    SettingsPage.Historico -> HistoryPage(settings, actions)
                    SettingsPage.Dados -> DataPage(actions)
                    SettingsPage.Sobre -> AboutPage(versionName, smartAvailable, actions)
                }
            }
        }
    }
}

private fun pageTitle(p: SettingsPage): Int = when (p) {
    SettingsPage.Index -> R.string.settings
    SettingsPage.Aparencia -> R.string.settings_appearance
    SettingsPage.Reproducao -> R.string.settings_playback
    SettingsPage.Transicoes -> R.string.settings_transitions
    SettingsPage.Smart -> R.string.settings_smart_page
    SettingsPage.Biblioteca -> R.string.nav_library
    SettingsPage.Historico -> R.string.settings_history_page
    SettingsPage.Dados -> R.string.backup_section
    SettingsPage.Sobre -> R.string.settings_about
}

// ---------------------------------------------------------------- Índice

@Composable
private fun IndexPage(
    settings: AppSettings,
    smartAvailable: Boolean,
    versionName: String,
    actions: SettingsActions,
    open: (SettingsPage) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    SearchField(query) { query = it }

    if (query.isNotBlank()) {
        val entries = searchEntries(smartAvailable)
        val q = fold(query)
        val hits = entries.filter { fold(it.title).contains(q) || fold(it.hint).contains(q) }
        if (hits.isEmpty()) {
            Text(stringResource(R.string.settings_search_none), style = MaterialTheme.typography.bodyLarge, color = SonivellTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = 8.dp))
        } else {
            Card {
                hits.forEach { e ->
                    NavRow(null, e.title, if (e.page == SettingsPage.Index) e.hint else stringResource(pageTitle(e.page))) {
                        when (e.page) {
                            SettingsPage.Index -> e.direct?.invoke(actions)
                            else -> open(e.page)
                        }
                    }
                }
            }
        }
        return
    }

    val layout = stringResource(
        when (settings.uiStyle) {
            UiStyle.ONDAS -> R.string.layout_ondas
            UiStyle.ESSENCIAL -> R.string.layout_essencial
            UiStyle.CONTRASTE -> R.string.layout_contrast
            UiStyle.CLASSICA -> R.string.layout_classic
            UiStyle.SONIVELL -> R.string.layout_sonivell
            UiStyle.DESENVOLVIMENTO -> R.string.layout_dev
        }
    )
    val theme = stringResource(
        when (settings.theme) {
            ThemeMode.AUTO -> R.string.theme_auto
            ThemeMode.LIGHT -> R.string.theme_light
            ThemeMode.DARK -> R.string.theme_dark
        }
    )
    val color = if (settings.systemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) stringResource(R.string.settings_system_colors)
    else stringResource(accentNames[settings.accent.ordinal])

    Card {
        NavRow(Icons.Rounded.Palette, stringResource(R.string.settings_appearance), "$layout · $theme · $color", accent = true) { open(SettingsPage.Aparencia) }
        NavRow(Icons.Rounded.PlayCircle, stringResource(R.string.settings_playback), stringResource(R.string.sum_playback, speedLabel(settings.playbackSpeed))) { open(SettingsPage.Reproducao) }
        NavRow(
            Icons.Rounded.SwapHoriz,
            stringResource(R.string.settings_transitions),
            when {
                !settings.crossfade -> stringResource(R.string.sum_crossfade_off)
                smartAvailable && settings.crossfadeSmart -> stringResource(R.string.sum_crossfade_smart, settings.crossfadeSeconds)
                else -> stringResource(R.string.sum_crossfade, settings.crossfadeSeconds)
            },
        ) { open(SettingsPage.Transicoes) }
        NavRow(Icons.Rounded.GraphicEq, stringResource(R.string.equalizer), stringResource(R.string.equalizer_hint), onClick = actions.onOpenEqualizer)
    }
    Card {
        NavRow(
            Icons.Rounded.AutoAwesome,
            stringResource(R.string.settings_smart_page),
            if (smartAvailable) stringResource(
                R.string.sum_smart,
                stringResource(varietyNames[settings.artistVariety.coerceIn(0, varietyNames.lastIndex)]),
                stringResource(if (settings.learnTaste) R.string.sum_taste_on else R.string.sum_taste_off),
            ) else stringResource(R.string.not_recommended),
            accent = true,
        ) { open(SettingsPage.Smart) }
        NavRow(
            Icons.Rounded.LibraryMusic,
            stringResource(R.string.nav_library),
            stringResource(if (settings.autoRefresh) R.string.sum_library_auto else R.string.sum_library),
        ) { open(SettingsPage.Biblioteca) }
        NavRow(Icons.Rounded.Insights, stringResource(R.string.settings_history_page), stringResource(R.string.sum_history)) { open(SettingsPage.Historico) }
    }
    Card {
        NavRow(Icons.Rounded.DirectionsCar, stringResource(R.string.car_mode), stringResource(R.string.sum_car), onClick = actions.onOpenCarMode)
        NavRow(Icons.Rounded.Backup, stringResource(R.string.backup_section), stringResource(R.string.sum_data)) { open(SettingsPage.Dados) }
        NavRow(Icons.Rounded.Info, stringResource(R.string.settings_about), stringResource(R.string.sum_about, versionName)) { open(SettingsPage.Sobre) }
    }
}

/** Um ajuste que a busca encontra: abre a página dele (ou a tela, quando é um atalho). */
private class SearchEntry(val title: String, val hint: String, val page: SettingsPage, val direct: ((SettingsActions) -> Unit)? = null)

@Composable
private fun searchEntries(smartAvailable: Boolean): List<SearchEntry> {
    @Composable fun e(title: Int, hint: Int?, page: SettingsPage) = SearchEntry(stringResource(title), hint?.let { stringResource(it) }.orEmpty(), page)
    return buildList {
        add(e(R.string.settings_layout, null, SettingsPage.Aparencia))
        add(e(R.string.settings_theme, null, SettingsPage.Aparencia))
        add(e(R.string.settings_accent, null, SettingsPage.Aparencia))
        add(e(R.string.settings_tinted_background, R.string.settings_tinted_background_hint, SettingsPage.Aparencia))
        add(e(R.string.settings_black_background, null, SettingsPage.Aparencia))
        add(e(R.string.settings_icon_style, null, SettingsPage.Aparencia))
        add(e(R.string.settings_pause_unplug, R.string.settings_pause_unplug_hint, SettingsPage.Reproducao))
        add(e(R.string.settings_resume_connect, R.string.settings_resume_connect_hint, SettingsPage.Reproducao))
        add(e(R.string.settings_eq_per_output, R.string.settings_eq_per_output_hint, SettingsPage.Reproducao))
        add(e(R.string.settings_normalize, R.string.settings_normalize_hint, SettingsPage.Reproducao))
        add(e(R.string.settings_speed, null, SettingsPage.Reproducao))
        add(e(R.string.settings_skip_silence, R.string.settings_skip_silence_hint, SettingsPage.Reproducao))
        add(e(R.string.settings_visualizer, R.string.settings_visualizer_hint, SettingsPage.Reproducao))
        add(e(R.string.settings_crossfade, R.string.settings_crossfade_hint, SettingsPage.Transicoes))
        add(e(R.string.settings_crossfade_albums, R.string.settings_crossfade_albums_hint, SettingsPage.Transicoes))
        if (smartAvailable) {
            add(e(R.string.settings_crossfade_smart, R.string.settings_crossfade_smart_hint, SettingsPage.Transicoes))
            add(e(R.string.settings_artist_variety, R.string.settings_artist_variety_hint, SettingsPage.Smart))
            add(e(R.string.taste_learn, R.string.taste_learn_hint, SettingsPage.Smart))
        }
        add(e(R.string.not_recommended, R.string.not_recommended_hint, SettingsPage.Smart))
        add(e(R.string.settings_auto_refresh, R.string.settings_auto_refresh_hint, SettingsPage.Biblioteca))
        add(e(R.string.settings_min_duration, R.string.settings_min_duration_hint, SettingsPage.Biblioteca))
        add(e(R.string.settings_ignored_folders, null, SettingsPage.Biblioteca))
        add(e(R.string.settings_publish_playlists, R.string.settings_publish_playlists_hint, SettingsPage.Biblioteca))
        add(e(R.string.lyrics_pick_folder, R.string.lyrics_folder_hint, SettingsPage.Biblioteca))
        add(e(R.string.organize, R.string.organize_hint, SettingsPage.Biblioteca))
        add(e(R.string.stats, R.string.stats_hint, SettingsPage.Historico))
        add(e(R.string.retro_title, R.string.retro_hint, SettingsPage.Historico))
        add(e(R.string.settings_history_pause, R.string.settings_history_pause_hint, SettingsPage.Historico))
        add(e(R.string.settings_history_export, null, SettingsPage.Historico))
        add(e(R.string.settings_history_clear, null, SettingsPage.Historico))
        add(e(R.string.backup_export, R.string.backup_hint, SettingsPage.Dados))
        add(e(R.string.backup_restore, R.string.backup_restore_hint, SettingsPage.Dados))
        add(e(R.string.thanks_title, null, SettingsPage.Sobre))
        add(e(R.string.settings_reset, null, SettingsPage.Sobre))
        add(SearchEntry(stringResource(R.string.equalizer), stringResource(R.string.equalizer_hint), SettingsPage.Index) { it.onOpenEqualizer() })
        add(SearchEntry(stringResource(R.string.car_mode), stringResource(R.string.car_mode_hint), SettingsPage.Index) { it.onOpenCarMode() })
    }
}

private val DIACRITICS = Regex("\\p{Mn}+")
private fun fold(s: String) = java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD).replace(DIACRITICS, "")

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = SonivellTheme.colors
    val label = stringResource(R.string.settings_search)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(999.dp)).background(c.surface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SonivellTheme.icons.search, null, Modifier.size(18.dp), tint = c.textSecondary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = c.textPrimary, fontWeight = FontWeight.SemiBold)),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
        if (value.isNotEmpty()) IconBtn(SonivellTheme.icons.close, stringResource(R.string.cd_clear_search), { onChange("") }, size = 36.dp, iconSize = 16.dp, tint = c.textSecondary)
    }
}

// ---------------------------------------------------------------- Páginas

@Composable
private fun AppearancePage(settings: AppSettings, actions: SettingsActions) {
    val c = SonivellTheme.colors
    // Prévia ao vivo: já usa o tema aplicado, então mostra o resultado de cada escolha.
    AppearancePreview()
    Group(stringResource(R.string.settings_layout)) {
        val legacy = settings.uiStyle == UiStyle.CONTRASTE || settings.uiStyle == UiStyle.CLASSICA
        var showLegacy by rememberSaveable { mutableStateOf(legacy) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(UiStyle.ONDAS, R.string.layout_ondas, R.string.layout_ondas_hint),
                Triple(UiStyle.ESSENCIAL, R.string.layout_essencial, R.string.layout_essencial_hint),
                Triple(UiStyle.SONIVELL, R.string.layout_sonivell, R.string.layout_sonivell_hint),
                Triple(UiStyle.DESENVOLVIMENTO, R.string.layout_dev, R.string.layout_dev_hint),
            ).forEach { (style, name, hint) ->
                LayoutOption(stringResource(name), stringResource(hint), settings.uiStyle == style) {
                    actions.onUpdate { it.copy(uiStyle = style) }
                }
            }
            Text(
                stringResource(if (showLegacy) R.string.settings_legacy_hide else R.string.settings_legacy_show),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = c.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { showLegacy = !showLegacy }
                    .heightIn(min = 44.dp)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
            )
            if (showLegacy) {
                listOf(
                    Triple(UiStyle.CONTRASTE, R.string.layout_contrast, R.string.layout_contrast_hint),
                    Triple(UiStyle.CLASSICA, R.string.layout_classic, R.string.layout_classic_hint),
                ).forEach { (style, name, hint) ->
                    LayoutOption(stringResource(name), stringResource(hint), settings.uiStyle == style) {
                        actions.onUpdate { it.copy(uiStyle = style) }
                    }
                }
            }
        }
    }
    Group(stringResource(R.string.settings_accent)) {
        val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val usingSystem = dynamicAvailable && settings.systemColors
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (dynamicAvailable) {
                val label = stringResource(R.string.settings_system_colors)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.sweepGradient(listOf(AccentChoice.Lima.dark, AccentChoice.Azul.dark, AccentChoice.Lilas.dark, AccentChoice.Laranja.dark, AccentChoice.Lima.dark)))
                        .then(if (usingSystem) Modifier.border(3.dp, c.textPrimary, CircleShape) else Modifier)
                        .clickable(role = Role.RadioButton) { actions.onUpdate { it.copy(systemColors = true) } }
                        .semantics { contentDescription = label; this.selected = usingSystem },
                    contentAlignment = Alignment.Center,
                ) {
                    if (usingSystem) Icon(SonivellTheme.icons.check, null, Modifier.size(20.dp), tint = Color(0xFF121314))
                    else Text("A", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF121314))
                }
            }
            AccentChoice.entries.forEach { choice ->
                val color = if (c.isDark) choice.dark else choice.light
                val selected = !usingSystem && settings.accent == choice
                val label = stringResource(accentNames[choice.ordinal])
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(if (selected) Modifier.border(3.dp, c.textPrimary, CircleShape) else Modifier)
                        .clickable(role = Role.RadioButton) { actions.onUpdate { it.copy(accent = choice, systemColors = false) } }
                        .semantics { contentDescription = label; this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(SonivellTheme.icons.check, null, Modifier.size(20.dp), tint = c.onAccent)
                }
            }
        }
        if (usingSystem) {
            Text(stringResource(R.string.settings_system_colors_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
    Card {
        val themes = listOf(ThemeMode.AUTO to R.string.theme_auto, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
        ChoiceRow(
            stringResource(R.string.settings_theme),
            null,
            themes.map { stringResource(it.second) },
            themes.indexOfFirst { it.first == settings.theme },
        ) { i -> actions.onUpdate { it.copy(theme = themes[i].first) } }
        SwitchRow(stringResource(R.string.settings_tinted_background), stringResource(R.string.settings_tinted_background_hint), settings.tintedBackground) { on ->
            actions.onUpdate { it.copy(tintedBackground = on) }
        }
        if (!settings.tintedBackground) {
            SwitchRow(
                stringResource(R.string.settings_black_background),
                stringResource(if (c.isDark) R.string.settings_black_background_hint else R.string.settings_dark_background_hint),
                settings.darkBackground == DarkBackground.Preto,
            ) { on -> actions.onUpdate { it.copy(darkBackground = if (on) DarkBackground.Preto else DarkBackground.Grafite) } }
        }
    }
    Group(stringResource(R.string.settings_icon_style)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconStyle.entries.forEach { style ->
                IconStyleOption(
                    style = style,
                    label = stringResource(iconStyleNames[style.ordinal]),
                    selected = settings.iconStyle == style,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { actions.onUpdate { it.copy(iconStyle = style) } }
            }
        }
    }
}

@Composable
private fun PlaybackPage(settings: AppSettings, actions: SettingsActions) {
    Group(stringResource(R.string.group_headset)) {
        Card {
            SwitchRow(stringResource(R.string.settings_pause_unplug), stringResource(R.string.settings_pause_unplug_hint), settings.pauseOnUnplug) { on ->
                actions.onUpdate { it.copy(pauseOnUnplug = on) }
            }
            SwitchRow(stringResource(R.string.settings_resume_connect), stringResource(R.string.settings_resume_connect_hint), settings.resumeOnConnect) { on ->
                actions.onUpdate { it.copy(resumeOnConnect = on) }
            }
            SwitchRow(stringResource(R.string.settings_eq_per_output), stringResource(R.string.settings_eq_per_output_hint), settings.eqPerOutput) { on ->
                actions.onUpdate { it.copy(eqPerOutput = on) }
            }
        }
    }
    Group(stringResource(R.string.group_sound)) {
        Card {
            SwitchRow(stringResource(R.string.settings_normalize), stringResource(R.string.settings_normalize_hint), settings.normalize) { on ->
                actions.onUpdate { it.copy(normalize = on) }
            }
            ChoiceRow(
                stringResource(R.string.settings_speed),
                stringResource(R.string.settings_speed_hint),
                SPEED_STEPS.map(::speedLabel),
                SPEED_STEPS.indexOfFirst { kotlin.math.abs(settings.playbackSpeed - it) < 0.01f },
            ) { i -> actions.onUpdate { it.copy(playbackSpeed = SPEED_STEPS[i]) } }
            SwitchRow(stringResource(R.string.settings_skip_silence), stringResource(R.string.settings_skip_silence_hint), settings.skipSilence) { on ->
                actions.onUpdate { it.copy(skipSilence = on) }
            }
        }
    }
    Group(stringResource(R.string.group_screen)) {
        Card { VisualizerSwitch(settings.visualizer) { on -> actions.onUpdate { it.copy(visualizer = on) } } }
    }
}

@Composable
private fun TransitionsPage(settings: AppSettings, smartAvailable: Boolean, actions: SettingsActions) {
    Card {
        SwitchRow(stringResource(R.string.settings_crossfade), stringResource(R.string.settings_crossfade_hint), settings.crossfade) { on ->
            actions.onUpdate { it.copy(crossfade = on) }
        }
        if (settings.crossfade) {
            ChoiceRow(
                stringResource(R.string.settings_crossfade_duration),
                stringResource(
                    if (smartAvailable && settings.crossfadeSmart) R.string.settings_crossfade_length_smart else R.string.settings_crossfade_length,
                ),
                CROSSFADE_STEPS.map { stringResource(R.string.seconds_short, it) },
                CROSSFADE_STEPS.indexOf(settings.crossfadeSeconds),
            ) { i -> actions.onUpdate { it.copy(crossfadeSeconds = CROSSFADE_STEPS[i]) } }
            if (smartAvailable) {
                SwitchRow(stringResource(R.string.settings_crossfade_smart), stringResource(R.string.settings_crossfade_smart_hint), settings.crossfadeSmart) { on ->
                    actions.onUpdate { it.copy(crossfadeSmart = on) }
                }
            }
            SwitchRow(stringResource(R.string.settings_crossfade_albums), stringResource(R.string.settings_crossfade_albums_hint), settings.crossfadeKeepAlbums) { on ->
                actions.onUpdate { it.copy(crossfadeKeepAlbums = on) }
            }
        }
    }
}

@Composable
private fun SmartPage(
    settings: AppSettings,
    smartAvailable: Boolean,
    tasteStatus: TasteModel.Status,
    blockedTracks: List<Track>,
    blockedArtists: List<String>,
    actions: SettingsActions,
) {
    val c = SonivellTheme.colors
    if (smartAvailable) {
        Card {
            ChoiceRow(
                stringResource(R.string.settings_artist_variety),
                stringResource(R.string.settings_artist_variety_hint),
                varietyNames.map { stringResource(it) },
                settings.artistVariety.coerceIn(0, varietyNames.lastIndex),
            ) { i -> actions.onUpdate { it.copy(artistVariety = i) } }
        }
        Group(stringResource(R.string.taste_section)) {
            Card {
                SwitchRow(stringResource(R.string.taste_learn), stringResource(R.string.taste_learn_hint), settings.learnTaste) { on ->
                    actions.onUpdate { it.copy(learnTaste = on) }
                }
                if (settings.learnTaste) {
                    InfoRow(tasteStatusText(tasteStatus))
                    NavRow(null, stringResource(R.string.taste_train_now), stringResource(R.string.taste_train_now_hint), onClick = actions.onRetrainTaste)
                }
                if (tasteStatus.trained) {
                    DangerRow(stringResource(R.string.taste_forget), stringResource(R.string.taste_forget_hint), actions.onForgetTaste)
                }
            }
        }
    }
    Group(stringResource(R.string.not_recommended)) {
        Card {
            InfoRow(stringResource(R.string.not_recommended_hint))
            if (blockedTracks.isEmpty() && blockedArtists.isEmpty()) {
                InfoRow(stringResource(R.string.not_recommended_none), primary = true)
            }
            blockedArtists.forEach { name ->
                Box(Modifier.padding(horizontal = 16.dp)) { RemovableRow(name, stringResource(R.string.tab_artists)) { actions.onUnblockArtist(name) } }
            }
            blockedTracks.forEach { t ->
                Box(Modifier.padding(horizontal = 16.dp)) { RemovableRow(t.title, t.artist) { actions.onUnblockTrack(t.id) } }
            }
        }
    }
}

@Composable
private fun LibraryPage(settings: AppSettings, folders: List<String>, actions: SettingsActions) {
    Card {
        SwitchRow(stringResource(R.string.settings_auto_refresh), stringResource(R.string.settings_auto_refresh_hint), settings.autoRefresh) { on ->
            actions.onUpdate { it.copy(autoRefresh = on) }
        }
        ChoiceRow(
            stringResource(R.string.settings_min_duration),
            stringResource(R.string.settings_min_duration_hint),
            MIN_DURATION_STEPS.map { if (it == 0) stringResource(R.string.settings_min_duration_off) else stringResource(R.string.seconds_short, it) },
            MIN_DURATION_STEPS.indexOf(settings.minDurationSec),
        ) { i -> actions.onUpdate { it.copy(minDurationSec = MIN_DURATION_STEPS[i]) } }
        IgnoredFolders(folders, settings.ignoredFolders) { folder, ignore ->
            actions.onUpdate { s -> s.copy(ignoredFolders = if (ignore) s.ignoredFolders + folder else s.ignoredFolders - folder) }
        }
        if (PlaylistPublisher.supported) {
            SwitchRow(stringResource(R.string.settings_publish_playlists), stringResource(R.string.settings_publish_playlists_hint), settings.publishPlaylists) { on ->
                actions.onUpdate { it.copy(publishPlaylists = on) }
            }
        }
    }
    Card {
        NavRow(null, stringResource(R.string.lyrics_pick_folder), stringResource(R.string.lyrics_folder_hint), onClick = actions.onPickLyricsFolder)
        NavRow(null, stringResource(R.string.organize), stringResource(R.string.organize_hint), onClick = actions.onOpenOrganize)
        NavRow(null, stringResource(R.string.cd_refresh), stringResource(R.string.settings_refresh_hint), onClick = actions.onRefreshLibrary)
    }
}

@Composable
private fun HistoryPage(settings: AppSettings, actions: SettingsActions) {
    Card {
        NavRow(null, stringResource(R.string.stats), stringResource(R.string.stats_hint), onClick = actions.onOpenStats)
        NavRow(null, stringResource(R.string.retro_title), stringResource(R.string.retro_hint), onClick = actions.onOpenRetro)
    }
    Card {
        SwitchRow(stringResource(R.string.settings_history_pause), stringResource(R.string.settings_history_pause_hint), settings.historyPaused) { on ->
            actions.onUpdate { it.copy(historyPaused = on) }
        }
        NavRow(null, stringResource(R.string.settings_history_export), null, onClick = actions.onExportHistory)
        DangerRow(stringResource(R.string.settings_history_clear), null, actions.onClearHistory)
    }
}

@Composable
private fun DataPage(actions: SettingsActions) {
    Card {
        InfoRow(stringResource(R.string.backup_hint))
        NavRow(null, stringResource(R.string.backup_export), null, onClick = actions.onBackup)
        DangerRow(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_hint), actions.onRestore)
    }
}

@Composable
private fun AboutPage(versionName: String, smartAvailable: Boolean, actions: SettingsActions) {
    Card {
        InfoRow(stringResource(R.string.settings_version, versionName), primary = true)
        InfoRow(stringResource(R.string.settings_privacy))
        if (smartAvailable) InfoRow(stringResource(R.string.settings_credits_smart))
    }
    Group(stringResource(R.string.thanks_title)) {
        Card {
            val uriHandler = LocalUriHandler.current
            val url = stringResource(R.string.thanks_url)
            InfoRow(stringResource(R.string.thanks_message), primary = true)
            NavRow(null, stringResource(R.string.thanks_link), url) { runCatching { uriHandler.openUri(url) } }
        }
    }
    Card { DangerRow(stringResource(R.string.settings_reset), null, actions.onReset) }
}

private val varietyNames = listOf(R.string.variety_0, R.string.variety_1, R.string.variety_2, R.string.variety_3, R.string.variety_4)

private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0').replace('.', ',')) + "×"

/** Pastas ignoradas: recolhida mostra quantas; aberta lista todas com um interruptor cada. */
@Composable
private fun IgnoredFolders(folders: List<String>, ignored: Set<String>, onToggle: (String, Boolean) -> Unit) {
    val c = SonivellTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    NavRow(
        null,
        stringResource(R.string.settings_ignored_folders),
        if (ignored.isEmpty()) stringResource(R.string.settings_ignored_folders_none)
        else pluralStringResource(R.plurals.settings_ignored_folders_count, ignored.size, ignored.size),
    ) { open = !open }
    if (open) {
        if (folders.isEmpty()) {
            Text(stringResource(R.string.settings_ignored_folders_empty), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(horizontal = 16.dp))
        }
        folders.forEach { folder ->
            val name = folder.trimEnd('/').substringAfterLast('/').ifBlank { folder }
            // O interruptor diz "incluir": ligado = a pasta entra na biblioteca.
            SwitchRow(name, folder, checked = folder !in ignored) { include -> onToggle(folder, !include) }
        }
    }
}

/** Prévia da aparência: uma faixa tocando, com capa, progresso, botão e abas no tema atual. */
@Composable
private fun AppearancePreview() {
    val c = SonivellTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.background).border(1.dp, c.outline, RoundedCornerShape(24.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_preview).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.7.sp), color = c.textSecondary)
        Row(Modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                Icon(SonivellTheme.icons.musicNote, null, Modifier.size(22.dp), tint = c.textSecondary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_preview_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1)
                Text(stringResource(R.string.settings_preview_artist), style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.track)) {
                    Box(Modifier.fillMaxWidth(0.4f).height(4.dp).background(c.accent))
                }
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(SonivellTheme.icons.play, null, Modifier.size(18.dp), tint = c.onAccent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.height(30.dp).clip(RoundedCornerShape(999.dp)).background(c.accent).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.nav_home), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = c.onAccent)
            }
            Box(Modifier.height(30.dp).clip(RoundedCornerShape(999.dp)).background(c.surface).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.nav_search), style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
            }
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
        // Uma linha só: em telas estreitas a fonte diminui em vez de quebrar a palavra.
        val labelStyle = MaterialTheme.typography.titleSmall
        BasicText(
            label,
            style = labelStyle.copy(color = c.textPrimary),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = labelStyle.fontSize),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Nomes das cores de destaque, na ordem de [AccentChoice]. */
private val accentNames = listOf(
    R.string.accent_lime, R.string.accent_blue, R.string.accent_orange,
    R.string.accent_lilac, R.string.accent_pink, R.string.accent_teal,
    R.string.accent_green, R.string.accent_cream,
)

/** Rótulo pequeno acima de um bloco (FONE E BLUETOOTH, SOM…). */
@Composable
private fun Group(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(label, Modifier.padding(horizontal = 8.dp))
        content()
    }
}

/** Cartão com linhas de ajuste encostadas umas nas outras. */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SonivellTheme.colors.surface)
            .padding(vertical = 4.dp),
        content = content,
    )
}

/**
 * Descrição de uma linha; quando não cabe, tocar nela mostra o texto inteiro.
 * Assim a tela fica limpa sem esconder a explicação.
 */
@Composable
private fun Hint(text: String) {
    val c = SonivellTheme.colors
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = c.textSecondary,
        maxLines = if (expanded) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        modifier = if (overflows || expanded) Modifier.clickable(onClickLabel = text) { expanded = !expanded } else Modifier,
    )
}

/** Linha que abre uma página ou tela: ícone opcional, nome, resumo do valor atual e seta. */
@Composable
private fun NavRow(icon: ImageVector?, title: String, summary: String?, accent: Boolean = false, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (accent) c.accent.copy(alpha = 0.18f) else c.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (accent) c.accent else c.textTertiary)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(SonivellTheme.icons.chevron, null, Modifier.size(20.dp), tint = c.textSecondary)
    }
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = SonivellTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = c.textPrimary,
                modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
            )
            if (hint != null) Hint(hint)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = title },
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

/** Escolha entre poucas opções: a linha mostra o valor; tocar abre uma folha com a lista. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(title: String, hint: String?, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val c = SonivellTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val value = options.getOrNull(selected).orEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClickLabel = title) { open = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.accent)
        Icon(SonivellTheme.icons.chevron, null, Modifier.size(20.dp), tint = c.textSecondary)
    }
    if (open) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = state, containerColor = c.surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = c.textPrimary, modifier = Modifier.padding(horizontal = 8.dp))
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp))
                }
                options.forEachIndexed { i, label ->
                    val on = i == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) c.accent.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable(role = Role.RadioButton) { onPick(i); open = false }
                            .semantics { this.selected = on }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, color = c.textPrimary, modifier = Modifier.weight(1f))
                        if (on) Icon(SonivellTheme.icons.check, null, Modifier.size(20.dp), tint = c.accent)
                    }
                }
            }
        }
    }
}

/** Texto informativo dentro de um cartão. */
@Composable
private fun InfoRow(text: String, primary: Boolean = false) {
    val c = SonivellTheme.colors
    Text(
        text,
        style = if (primary) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
        color = if (primary) c.textPrimary else c.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Ação que apaga ou troca dados: fica por último e em vermelho. */
@Composable
private fun DangerRow(title: String, hint: String?, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val danger = if (c.isDark) Color(0xFFFF8A80) else Color(0xFFB3261E)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = danger)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Liga o visualizador; antes pede a permissão de microfone, que o Android exige para ler o som. */
@Composable
private fun VisualizerSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onChange(true) }
    SwitchRow(
        stringResource(R.string.settings_visualizer),
        stringResource(R.string.settings_visualizer_hint),
        on,
    ) { enable ->
        if (!enable) onChange(false)
        else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) onChange(true)
        else launcher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
}
