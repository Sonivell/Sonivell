package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.desktop.Accent
import com.juscelino.sonivell.desktop.DarkBg
import com.juscelino.sonivell.desktop.ThemeMode
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Capturas dos cartões em vidro nos modos claro e escuro (grupo de ajustes, campo de busca, cartões
 * com brilho e botão redondo). No claro a sombra fica só por fora do cartão; o escuro não muda.
 * Grava em desktop/build/capturas-onda, que o desktop.yml junta às capturas do PC.
 */
class VidroCapturasTest {
    @Composable
    private fun Tela(dark: Boolean) {
        SonivellTheme(Accent.Lima, if (dark) ThemeMode.DARK else ThemeMode.LIGHT, DarkBg.Grafite) {
            val c = SonivellTheme.colors
            Row(Modifier.fillMaxSize().background(c.background).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Txt("Ajustes", 18, FontWeight.ExtraBold)
                    Column(Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf("Aparência", "Reprodução", "Biblioteca", "Sobre").forEach { Txt(it, 14, FontWeight.Bold) }
                    }
                    Box(
                        Modifier.fillMaxWidth().height(48.dp)
                            .glassSurface(RoundedCornerShape(24.dp), c.surface, c.outline, elevation = 6.dp)
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Txt("Buscar músicas, artistas…", 14, FontWeight.Medium, c.textSecondary) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Txt("Cartões", 18, FontWeight.ExtraBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf("Rock" to c.accent, "Jazz" to c.textSecondary, "Pop" to c.accent).forEach { (nome, tom) ->
                            Box(
                                Modifier.size(140.dp, 100.dp)
                                    .glassSurface(RoundedCornerShape(18.dp), c.surface, c.outline, tone = tom, elevation = 10.dp)
                                    .padding(14.dp),
                            ) { Txt(nome, 15, FontWeight.ExtraBold) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).glassSurface(CircleShape, c.surface, c.outline, elevation = 6.dp))
                        Box(
                            Modifier.size(300.dp, 64.dp)
                                .glassSurface(RoundedCornerShape(22.dp), c.surface, c.outline, tone = c.accent, elevation = 12.dp)
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { Txt("Mini player", 14, FontWeight.Bold) }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun gerarCapturas() {
        val pasta = File(System.getProperty("user.dir"), "build/capturas-onda").apply { mkdirs() }
        listOf(true to "vidro-escuro.png", false to "vidro-claro.png").forEach { (dark, nome) ->
            val escala = 2f
            val scene = ImageComposeScene(width = (900 * escala).toInt(), height = (330 * escala).toInt(), density = Density(escala)) {
                Tela(dark)
            }
            try {
                val png = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
                assertTrue(png != null && png.isNotEmpty(), "captura vazia: $nome")
                File(pasta, nome).writeBytes(png)
            } finally {
                scene.close()
            }
        }
    }
}
