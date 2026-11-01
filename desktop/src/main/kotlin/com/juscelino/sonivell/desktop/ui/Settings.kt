package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.desktop.Accent
import com.juscelino.sonivell.desktop.Controller
import com.juscelino.sonivell.desktop.DarkBg
import com.juscelino.sonivell.desktop.ScreenLayout
import com.juscelino.sonivell.desktop.ThemeMode
import com.juscelino.sonivell.desktop.str
import java.io.File
import javax.swing.JFileChooser

@Composable
fun SettingsScreen(ctl: Controller) {
    val c = SonivellTheme.colors
    val settings by ctl.settings.collectAsState()
    val lib by ctl.library.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Txt(str("settings"), 36, FontWeight.ExtraBold, letterSpacing = -1f, modifier = Modifier.padding(top = 22.dp))

        Group(str("settings_appearance")) {
            Label(str("settings_layout"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(str("layout_ondas"), settings.layout == ScreenLayout.ONDAS, { ctl.updateSettings { it.copy(layout = ScreenLayout.ONDAS) } })
                Pill(str("layout_essencial"), settings.layout == ScreenLayout.ESSENCIAL, { ctl.updateSettings { it.copy(layout = ScreenLayout.ESSENCIAL) } })
            }
            Label(str("settings_theme"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(str("theme_auto"), settings.theme == ThemeMode.AUTO, { ctl.updateSettings { it.copy(theme = ThemeMode.AUTO) } })
                Pill(str("theme_light"), settings.theme == ThemeMode.LIGHT, { ctl.updateSettings { it.copy(theme = ThemeMode.LIGHT) } })
                Pill(str("theme_dark"), settings.theme == ThemeMode.DARK, { ctl.updateSettings { it.copy(theme = ThemeMode.DARK) } })
            }
            Label(str("settings_accent"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(4.dp)) {
                Accent.values().forEach { a ->
                    val selected = settings.accent == a
                    val color = accentColor(a, c.isDark)
                    Box(
                        Modifier.size(40.dp).let { if (selected) it.border(2.dp, c.textPrimary, RoundedCornerShape(14.dp)).padding(3.dp) else it }
                            .clip(RoundedCornerShape(14.dp)).background(color)
                            .hoverClick(RoundedCornerShape(14.dp), hover = color.copy(alpha = 0.8f), base = color, label = str(accentKey(a))) { ctl.updateSettings { it.copy(accent = a) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Traco.check, null, Modifier.size(20.dp), tint = c.onAccent)
                    }
                }
            }
            Label(str("settings_tinted_background"))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill(str(if (settings.tinted) "desktop_on" else "desktop_off"), settings.tinted, { ctl.updateSettings { it.copy(tinted = !it.tinted) } })
                Txt(str("settings_tinted_background_hint"), 13, FontWeight.Medium, c.textSecondary, maxLines = 2)
            }
            if (!settings.tinted) {
                Label(str("settings_dark_background"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(str("background_graphite"), settings.darkBg == DarkBg.Grafite, { ctl.updateSettings { it.copy(darkBg = DarkBg.Grafite) } })
                    Pill(str("background_black"), settings.darkBg == DarkBg.Preto, { ctl.updateSettings { it.copy(darkBg = DarkBg.Preto) } })
                }
            }
        }

        Group(str("desktop_music_folders")) {
            Txt(str("desktop_music_folders_hint"), 13, FontWeight.Medium, c.textSecondary, maxLines = 3)
            settings.musicDirs.forEach { dir ->
                Row(
                    Modifier.fillMaxWidth().widthIn(max = 720.dp).height(52.dp).glassSurface(RoundedCornerShape(14.dp), c.surfaceHigh, c.outline, elevation = 0.dp).padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Traco.folder, null, Modifier.size(20.dp), tint = c.textTertiary)
                    Txt(dir, 14, FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (settings.musicDirs.size > 1) IconBtn(Traco.close, str("desktop_remove_folder"), { ctl.updateSettings { s -> s.copy(musicDirs = s.musicDirs - dir) } }, size = 38.dp, iconSize = 18.dp, tint = c.textSecondary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(str("desktop_add_folder"), false, {
                    val chooser = JFileChooser(File(System.getProperty("user.home"))).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val picked = chooser.selectedFile.path
                        ctl.updateSettings { s -> if (picked in s.musicDirs) s else s.copy(musicDirs = s.musicDirs + picked) }
                    }
                }, Traco.add)
                Pill(if (lib.scanning) str("desktop_scanning_plain") else str("desktop_rescan"), false, { ctl.refresh() })
            }
        }

        if (ctl.smart.available) {
            val progress by ctl.smart.progress.collectAsState()
            Group(str("desktop_smart_label")) {
                Txt(str("smart_on_hint"), 13, FontWeight.Medium, c.textSecondary, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(str("desktop_smart_label"), settings.smart, { ctl.toggleSmart() }, Traco.shuffle)
                }
                if (progress.total > 0) {
                    Label(str("smart_analysing"))
                    Txt(
                        if (progress.failed) str("desktop_smart_failed") else str("smart_progress", progress.analysed, progress.total),
                        14, FontWeight.SemiBold, c.textSecondary,
                    )
                }
                Label(str("settings_artist_variety"))
                Txt(str("settings_artist_variety_hint"), 13, FontWeight.Medium, c.textSecondary, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..4).forEach { level ->
                        Pill(str("variety_$level"), settings.artistVariety == level, { ctl.updateSettings { it.copy(artistVariety = level) } })
                    }
                }
                Txt(str("settings_credits_smart"), 12, FontWeight.Medium, c.textTertiary, maxLines = 2)
            }
        }

        Group(str("thanks_title")) {
            val url = "https://github.com/Nikita-sud/latentjam"
            Txt(str("thanks_message"), 14, FontWeight.Medium, maxLines = 6, lineHeight = 21)
            Box(Modifier.hoverClick(RoundedCornerShape(12.dp), hover = c.surface, label = str("thanks_link")) { openInBrowser(url) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Txt(str("thanks_link"), 14, FontWeight.ExtraBold, c.accent)
            }
        }

        Group(str("settings_about")) {
            Txt(str("settings_version", "0.1.0"), 14, FontWeight.SemiBold)
            Txt(str("desktop_shortcuts"), 13, FontWeight.Medium, c.textSecondary, maxLines = 3)
        }
    }
}

private fun accentKey(a: Accent) = when (a) {
    Accent.Lima -> "accent_lime"; Accent.Azul -> "accent_blue"; Accent.Laranja -> "accent_orange"; Accent.Lilas -> "accent_lilac"
    Accent.Rosa -> "accent_pink"; Accent.Turquesa -> "accent_teal"; Accent.Verde -> "accent_green"
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    // Cada grupo é um cartão em vidro, como nas páginas de ajustes do app Android.
    Column(
        Modifier.widthIn(max = 820.dp).fillMaxWidth().cardSurface(RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Txt(title, 18, FontWeight.ExtraBold)
        content()
    }
}

@Composable
private fun Label(text: String) {
    Txt(text, 13, FontWeight.Bold, SonivellTheme.colors.textSecondary)
}

/** Abre um endereço no navegador padrão; no Linux, cai para o xdg-open quando o AWT não consegue. */
private fun openInBrowser(url: String) {
    val viaAwt = runCatching {
        val desktop = java.awt.Desktop.getDesktop()
        if (!desktop.isSupported(java.awt.Desktop.Action.BROWSE)) error("sem suporte")
        desktop.browse(java.net.URI(url))
    }.isSuccess
    if (!viaAwt) runCatching { ProcessBuilder("xdg-open", url).start() }
}
