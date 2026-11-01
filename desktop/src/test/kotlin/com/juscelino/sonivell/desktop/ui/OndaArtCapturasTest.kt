package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import com.juscelino.sonivell.desktop.Accent
import com.juscelino.sonivell.desktop.DarkBg
import com.juscelino.sonivell.desktop.ThemeMode
import kotlin.test.assertTrue

/**
 * Capturas da capa Onda nos modos claro e escuro: lista de faixas (versão Simples), grade de álbuns
 * (Com nome) e um cabeçalho grande sem título (Completo). Renderiza fora da tela, sem janela, e grava
 * em desktop/build/capturas-onda (o desktop.yml junta essas imagens às capturas do PC).
 */
class OndaArtCapturasTest {
    private val faixas = listOf(
        Triple("Maré Alta", "Lua Norte", "Horizonte"),
        Triple("Casa de Vento", "Rio Acima", "Rio Acima"),
        Triple("Farol", "Sal Grosso", "Sal Grosso"),
        Triple("Noites de Vidro", "Marina Ruído", "Vidro e Sal"),
        Triple("Reborn", "Era", "Reborn"),
    )
    private val albuns = listOf(
        "Horizonte" to "Lua Norte", "Rio Acima" to "Rio Acima", "Sal Grosso" to "Sal Grosso",
        "Vidro e Sal" to "Marina Ruído", "Dirty Bass (Tour Edition)" to "Far East Movement", "The Wall" to "Pink Floyd",
    )

    @Composable
    private fun Capa(album: String, artista: String, title: String?, lado: Dp, canto: Dp) {
        Box(Modifier.size(lado).clip(RoundedCornerShape(canto))) {
            OndaArt(album, artista, title = title, subtitle = artista.takeIf { title != null }, size = lado, corner = canto)
        }
    }

    @Composable
    private fun Tela(dark: Boolean) {
        SonivellTheme(Accent.Lima, if (dark) ThemeMode.DARK else ThemeMode.LIGHT, DarkBg.Grafite) {
            val c = SonivellTheme.colors
            Row(Modifier.fillMaxSize().background(c.background).padding(32.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Txt("Lista de faixas", 18, FontWeight.ExtraBold)
                    faixas.forEach { (titulo, artista, album) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Capa(album, artista, null, 48.dp, 12.dp)
                            Column {
                                Txt(titulo, 14, FontWeight.Bold)
                                Txt(artista, 12, FontWeight.Medium, c.textSecondary)
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Txt("Grade de álbuns", 18, FontWeight.ExtraBold)
                    albuns.chunked(3).forEach { linha ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            linha.forEach { (album, artista) -> Capa(album, artista, album, 150.dp, 18.dp) }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Txt("Cabeçalho (sem título)", 18, FontWeight.ExtraBold)
                    Capa("Horizonte", "Lua Norte", null, 220.dp, 28.dp)
                    Txt("Horizonte", 22, FontWeight.ExtraBold)
                    Txt("Lua Norte", 14, FontWeight.Medium, c.textSecondary)
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun gerarCapturas() {
        val pasta = File(System.getProperty("user.dir"), "build/capturas-onda").apply { mkdirs() }
        listOf(true to "onda-escuro.png", false to "onda-claro.png").forEach { (dark, nome) ->
            val escala = 2f
            val scene = ImageComposeScene(width = (1200 * escala).toInt(), height = (560 * escala).toInt(), density = Density(escala)) {
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
