package com.juscelino.sonivell.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.juscelino.sonivell.data.AccentChoice
import com.juscelino.sonivell.data.UiStyle
import com.juscelino.sonivell.data.iconStyle
import com.juscelino.sonivell.smart.SmartProgress
import com.juscelino.sonivell.ui.PermissionScreen
import com.juscelino.sonivell.ui.WelcomeScreen
import com.juscelino.sonivell.ui.theme.SonivellTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.Locale
import kotlin.test.Test

/** Prévia das telas de abertura do Android (o mesmo código Compose) num tamanho de celular. */
@OptIn(ExperimentalComposeUiApi::class)
class PreviaAberturaTest {
    private val out = File("build/capturas-onda").apply { mkdirs() }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        // 412 × 915 dp, densidade 2,5 (um celular comum).
        val scene = ImageComposeScene(1030, 2288, Density(2.5f)) {
            SonivellTheme(accent = AccentChoice.Lima, darkTheme = dark, iconStyle = UiStyle.SONIVELL.iconStyle, style = UiStyle.SONIVELL) { content() }
        }
        try {
            scene.render(0)
            scene.render(500_000_000)
            val img = scene.render(1_000_000_000)
            File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Test
    fun previa() {
        val antes = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        try {
            runCatching { shot("abertura-1-acesso", true) { PermissionScreen {} } }.onFailure { println("FALHOU acesso: $it") }
            runCatching {
                shot("abertura-2-boas-vindas", true) { WelcomeScreen(2012, false, true, SmartProgress(running = true, analysed = 0, total = 2012, metadataReady = true)) {} }
            }.onFailure { println("FALHOU boas-vindas: $it") }
            runCatching {
                shot("abertura-3-boas-vindas-analisando", true) { WelcomeScreen(2012, false, true, SmartProgress(running = true, analysed = 744, total = 2012, metadataReady = true)) {} }
            }.onFailure { println("FALHOU boas-vindas 2: $it") }
            runCatching { shot("abertura-4-acesso-claro", false) { PermissionScreen {} } }.onFailure { println("FALHOU acesso claro: $it") }
        } finally {
            Locale.setDefault(antes)
        }
    }
}
