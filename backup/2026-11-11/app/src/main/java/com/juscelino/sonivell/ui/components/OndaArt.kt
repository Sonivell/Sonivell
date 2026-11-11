package com.juscelino.sonivell.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.util.Locale

/**
 * Escolha estável da capa Onda: a cor sai do artista e o formato da onda do álbum.
 * FNV-1a de 32 bits sobre o texto em UTF-8 (sem String.hashCode), para o Android e o PC
 * darem o mesmo resultado; o desktop tem a mesma conta em desktop/.../ui/Components.kt.
 */
object OndaSeed {
    const val CORES = 5
    const val ONDAS = 4

    fun fnv1a(texto: String): Long {
        var h = 0x811c9dc5L
        for (b in texto.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xff)
            h = (h * 0x01000193L) and 0xffffffffL
        }
        return h
    }

    fun normalizar(texto: String): String = texto.trim().lowercase(Locale.ROOT)

    /** Índice da cor (0..4) para o artista do álbum. */
    fun cor(artista: String): Int = (fnv1a(normalizar(artista)) % CORES).toInt()

    /** Índice do formato da onda (0..3) para o álbum. */
    fun onda(album: String): Int = (fnv1a(normalizar(album)) % ONDAS).toInt()
}

/** Limão, Turquesa, Coral, Lavanda e Âmbar: uma por artista. */
private val OndaCores = listOf(
    Color(0xFFD4EF55), Color(0xFF4FE0C8), Color(0xFFFF8A6B), Color(0xFFB79BFF), Color(0xFFFFC24D),
)

/** Os quatro formatos (A, B, C, D) num quadro de 200×200: início, depois duas cúbicas de 3 pontos. */
private val OndaFormas = listOf(
    floatArrayOf(26f, 106f, 46f, 50f, 78f, 50f, 100f, 100f, 122f, 150f, 154f, 150f, 174f, 94f),
    floatArrayOf(20f, 100f, 44f, 66f, 76f, 66f, 100f, 100f, 124f, 134f, 156f, 134f, 180f, 100f),
    floatArrayOf(34f, 112f, 50f, 30f, 80f, 30f, 100f, 100f, 120f, 170f, 150f, 170f, 166f, 88f),
    floatArrayOf(26f, 94f, 46f, 150f, 78f, 150f, 100f, 100f, 122f, 50f, 154f, 50f, 174f, 106f),
)

private val SimplesMax = 110.dp
private val CompletoMin = 160.dp

/**
 * Capa "Onda" para faixas sem arte: fundo escuro (ou claro, no tema claro) com brilho na cor do
 * artista e uma onda. Três versões, pelo tamanho e pelo texto:
 * - Simples (lado < 110dp ou capa redonda): só a onda grande.
 * - Com nome (lado ≥ 110dp e com [title]): onda em cima e faixa de vidro com álbum e artista.
 * - Completo (lado ≥ 160dp sem título): dois cartões de vidro, como o ícone do app.
 * [albumKey] escolhe a onda; [artistKey] escolhe a cor. Com [placeholder], só o fundo (enquanto
 * a capa real carrega, para não piscar a onda).
 */
@Composable
fun OndaArt(
    albumKey: String,
    artistKey: String,
    title: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 0.dp,
    placeholder: Boolean = false,
) {
    val dark = SonivellTheme.colors.isDark
    val tom = OndaCores[OndaSeed.cor(artistKey)]
    val forma = OndaFormas[OndaSeed.onda(albumKey)]
    val onda = if (dark) tom else lerp(tom, Color.Black, 0.35f)
    BoxWithConstraints(modifier) {
        val lado = minOf(maxWidth, maxHeight)
        val redonda = corner * 2 >= lado * 0.9f
        val versao = when {
            placeholder -> Versao.Fundo
            lado < SimplesMax || redonda -> Versao.Simples
            !title.isNullOrBlank() -> Versao.ComNome
            lado >= CompletoMin -> Versao.Completo
            else -> Versao.Simples
        }
        val brilho = if (versao == Versao.Completo) 0.18f else 0.30f
        Canvas(Modifier.fillMaxSize()) { drawOndaFundo(dark, tom, brilho) }
        Box(Modifier.size(lado).align(Alignment.Center)) {
            when (versao) {
                Versao.Fundo -> Unit
                Versao.Simples -> Canvas(Modifier.fillMaxSize()) {
                    drawOnda(forma, center, size.minDimension * 0.78f, onda)
                }
                Versao.ComNome -> ComNome(lado, forma, onda, dark, title.orEmpty(), subtitle)
                Versao.Completo -> Completo(lado, forma, tom, onda, dark)
            }
        }
    }
}

private enum class Versao { Fundo, Simples, ComNome, Completo }

@Composable
private fun ComNome(lado: Dp, forma: FloatArray, onda: Color, dark: Boolean, title: String, subtitle: String?) {
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize()) {
        drawOnda(forma, Offset(size.width / 2f, size.height * 0.38f), size.minDimension * 0.51f, onda)
    }
    val margem = lado * 0.055f
    val forma1 = RoundedCornerShape(lado * 0.086f)
    val texto = if (dark) Color.White else Color(0xFF1A1E16)
    Box(Modifier.fillMaxSize().padding(start = margem, end = margem, bottom = margem)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .size(width = lado - margem * 2, height = lado * 0.30f)
                .drawBehind { sombraSuave(lado.toPx() * 0.086f, dark) }
                .clip(forma1)
                .background(Color.White.copy(alpha = if (dark) 0.10f else 0.45f))
                .drawBehind { brilhoTopo() }
                .border(1.dp, Color.White.copy(alpha = if (dark) 0.28f else 0.80f), forma1)
                .padding(horizontal = lado * 0.06f),
            verticalArrangement = Arrangement.spacedBy(lado * 0.008f, Alignment.CenterVertically),
        ) {
            with(density) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = (lado * 0.09f).toSp(), lineHeight = (lado * 0.11f).toSp()),
                    fontWeight = FontWeight.ExtraBold,
                    color = texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = (lado * 0.063f).toSp(), lineHeight = (lado * 0.08f).toSp()),
                        fontWeight = FontWeight.Medium,
                        color = texto.copy(alpha = if (dark) 0.68f else 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Completo(lado: Dp, forma: FloatArray, tom: Color, onda: Color, dark: Boolean) {
    val atrasX = lado * 0.27f
    val atrasY = lado * 0.1875f
    val atrasL = lado * 0.46f
    val atrasA = lado * 0.51f
    val atrasForma = RoundedCornerShape(lado * 0.125f)
    val frenteX = lado * 0.219f
    val frenteY = lado * 0.25f
    val frenteL = lado * 0.5625f
    val frenteForma = RoundedCornerShape(lado * 0.148f)
    val cartaoAtras = Modifier
        .size(atrasL, atrasA)
        .clip(atrasForma)
        .background(tom.copy(alpha = 0.70f))
        .border(1.dp, Color.White.copy(alpha = 0.40f), atrasForma)

    Box(Modifier.offset(atrasX, atrasY).then(cartaoAtras))
    Box(
        Modifier
            .offset(frenteX, frenteY)
            .size(frenteL)
            .drawBehind { sombraSuave(lado.toPx() * 0.148f, dark) }
            .clip(frenteForma),
    ) {
        // Vidro de verdade (Android 12+): o que está atrás do cartão (fundo e cartão de trás) desfocado.
        // Abaixo disso, só a translucidez, que já deixa ver as camadas de trás.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(Modifier.fillMaxSize().blur(lado * 0.05f, BlurredEdgeTreatment.Rectangle)) {
                Canvas(Modifier.fillMaxSize()) {
                    val lp = lado.toPx()
                    translate(-frenteX.toPx(), -frenteY.toPx()) {
                        drawOndaFundo(dark, tom, 0.18f, Size(lp, lp))
                    }
                }
                Box(Modifier.offset(atrasX - frenteX, atrasY - frenteY).then(cartaoAtras))
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = if (dark) 0.10f else 0.45f))
                .drawBehind { brilhoTopo() }
                .border(1.dp, Color.White.copy(alpha = if (dark) 0.32f else 0.80f), frenteForma),
        )
        Canvas(Modifier.fillMaxSize()) { drawOnda(forma, center, size.minDimension * 0.78f, onda) }
    }
}

/** Fundo em diagonal (160°) com brilho radial da cor do artista no canto superior esquerdo. */
private fun DrawScope.drawOndaFundo(dark: Boolean, tom: Color, brilho: Float, area: Size = size) {
    val cores = if (dark) {
        listOf(Color(0xFF22262A), Color(0xFF15181A), Color(0xFF0B0D0E))
    } else {
        listOf(Color(0xFFF4F6EE), Color(0xFFEBEFE4), Color(0xFFE3E8DA))
    }
    // 160°: de cima (levemente à esquerda) para baixo (levemente à direita).
    val dx = area.width * 0.17f
    val dy = area.height * 0.47f
    val c = Offset(area.width / 2f, area.height / 2f)
    drawRect(Brush.linearGradient(cores, start = Offset(c.x - dx, c.y - dy), end = Offset(c.x + dx, c.y + dy)), size = area)
    drawRect(
        Brush.radialGradient(
            listOf(tom.copy(alpha = brilho), Color.Transparent),
            center = Offset(area.width * 0.12f, area.height * 0.10f),
            radius = maxOf(area.width, area.height) * 0.80f,
        ),
        size = area,
    )
}

/** Onda de um dos quatro formatos, num quadro de lado [quadro] centrado em [centro]. */
private fun DrawScope.drawOnda(forma: FloatArray, centro: Offset, quadro: Float, cor: Color) {
    val s = quadro / 200f
    val x0 = centro.x - quadro / 2f
    val y0 = centro.y - quadro / 2f
    fun x(i: Int) = x0 + forma[i] * s
    fun y(i: Int) = y0 + forma[i + 1] * s
    val path = Path().apply {
        moveTo(x(0), y(0))
        cubicTo(x(2), y(2), x(4), y(4), x(6), y(6))
        cubicTo(x(8), y(8), x(10), y(10), x(12), y(12))
    }
    drawPath(path, cor, style = Stroke(width = 26f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Sombra suave por fora do vidro: camadas crescentes de preto bem transparente. No claro bem mais
 * fraca: o vidro claro é translúcido e deixaria a sombra de baixo acinzentar a faixa.
 */
private fun DrawScope.sombraSuave(raio: Float, dark: Boolean) {
    val passo = size.minDimension * 0.012f
    for (k in 1..6) {
        val g = k * passo
        drawRoundRect(
            Color.Black.copy(alpha = if (dark) 0.05f else 0.015f),
            topLeft = Offset(-g * 0.5f, g * 0.9f),
            size = Size(size.width + g, size.height),
            cornerRadius = CornerRadius(raio + g),
        )
    }
}

/** Reflexo de 2dp no topo do vidro. */
private fun DrawScope.brilhoTopo() {
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), endY = 2.dp.toPx()),
        size = Size(size.width, 2.dp.toPx()),
    )
}
