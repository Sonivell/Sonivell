package com.juscelino.sonivell

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juscelino.sonivell.system.AppShortcuts
import com.juscelino.sonivell.ui.SonivellApp
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppShortcuts.publish(this)
        // Reagenda o despertador (o Android apaga alarmes agendados em algumas atualizações).
        com.juscelino.sonivell.system.MusicAlarm.schedule(this)
        if (savedInstanceState == null) handleShortcut(intent)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.AUTO -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Ícones da barra de status/navegação acompanham o tema escolhido.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SonivellTheme(
                accent = settings.accent,
                darkTheme = dark,
                iconStyle = settings.iconStyle,
                darkBackground = settings.darkBackground,
                tintedBackground = settings.tintedBackground,
                systemColors = settings.systemColors,
            ) {
                SonivellApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    /** Atalhos do ícone e do bloco rápido chegam como ações no Intent. */
    private fun handleShortcut(intent: Intent?) {
        val action = intent?.action ?: return
        if (action in AppShortcuts.ALL) vm.requestShortcut(action)
    }
}
