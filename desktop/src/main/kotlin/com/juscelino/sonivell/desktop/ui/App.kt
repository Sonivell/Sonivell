package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.desktop.Controller
import com.juscelino.sonivell.desktop.str

@Composable
fun SonivellApp(ctl: Controller) {
    val settings by ctl.settings.collectAsState()
    SonivellTheme(settings.accent, settings.theme, settings.darkBg) {
        val c = SonivellTheme.colors
        val nav = remember { Nav() }
        val actions = remember { Actions(ctl, nav) }
        val player by ctl.player.collectAsState()
        BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
            val wide = maxWidth >= 1180.dp
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Sidebar(nav, ctl)
                    Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp)) {
                        val d = nav.detail
                        if (d != null) DetailScreen(d, ctl, nav, actions)
                        else when (nav.route) {
                            Route.ForYou -> ForYouScreen(ctl, nav, actions)
                            Route.Search -> SearchScreen(ctl, nav, actions)
                            Route.Library -> LibraryScreen(ctl, nav, actions)
                            Route.Settings -> SettingsScreen(ctl)
                            Route.NowPlaying -> NowPlayingScreen(ctl, nav, actions)
                        }
                        player.error?.let { msg ->
                            Box(Modifier.align(Alignment.BottomCenter).padding(12.dp).background(c.surfaceHigh, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Txt(str("desktop_audio_error", msg), 13, FontWeight.SemiBold, maxLines = 2)
                            }
                        }
                    }
                    if (wide && nav.showQueue && nav.route != Route.NowPlaying) QueuePanel(ctl, Modifier.width(340.dp))
                }
                PlayerBar(ctl, nav)
            }
        }
    }
}
