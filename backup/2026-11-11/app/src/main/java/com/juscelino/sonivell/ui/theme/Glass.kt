package com.juscelino.sonivell.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.juscelino.sonivell.data.UiStyle
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Liga os cartões em vidro (ver SonivellTheme, que hoje liga em todos os temas). */
val LocalGlass = staticCompositionLocalOf { false }

/**
 * Superfície em vidro do app: fundo translúcido, contorno fino, sombra suave por fora
 * e, com [tone], um brilho fraco na parte de baixo na cor do cartão.
 *
 * Leve de propósito: não há desfoque do que fica atrás (o fundo do app é liso, então a transparência
 * só clareia o tom), o brilho é um gradiente criado uma vez por tamanho ([drawWithCache]) e nada anima.
 * A sombra é a única parte com custo; [elevation] 0 a desliga (linhas de listas longas).
 */
fun Modifier.glassSurface(
    shape: Shape,
    surface: Color,
    outline: Color,
    tone: Color? = null,
    elevation: Dp = 10.dp,
    surfaceAlpha: Float = 0.72f,
): Modifier = composed {
    // Escuro: como sempre foi. Claro: a sombra do sistema seria desenhada também por baixo do vidro
    // translúcido e apareceria como um bloco cinza dentro do cartão; no claro a sombra é desenhada só
    // por fora da forma ([sombraClara]) e o vidro fica um pouco mais opaco para seguir destacado.
    val dark = SonivellTheme.colors.isDark
    val alpha = if (dark) surfaceAlpha else maxOf(surfaceAlpha, LIGHT_SURFACE_ALPHA)
    Modifier
        .then(
            // A sombra do sistema também é desenhada por baixo da forma e, atrás do vidro translúcido,
            // aparecia como um bloco escuro dentro do cartão (pior sobre o fundo da capa). Nos dois
            // temas a sombra agora é só por fora ([sombraExterna]); no escuro, mais forte.
            when {
                elevation <= 0.dp -> Modifier
                dark -> Modifier.sombraExterna(shape, elevation, DARK_SHADOW_ALPHA)
                else -> Modifier.sombraExterna(shape, elevation, LIGHT_SHADOW_ALPHA)
            },
        )
        .clip(shape)
        .background(surface.copy(alpha = surface.alpha * alpha))
        .then(
            if (tone == null) Modifier else Modifier.drawWithCache {
                val brush = Brush.radialGradient(
                    colors = listOf(tone.copy(alpha = GLOW_ALPHA), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 1.15f),
                    radius = maxOf(size.width, size.height) * 0.75f,
                )
                onDrawBehind { drawRect(brush) }
            },
        )
        .border(1.dp, outline, shape)
}

/**
 * Sombra só do lado de fora de [shape]: camadas de contorno cada vez mais largas e
 * fracas, um pouco deslocadas para baixo, com o miolo da forma recortado. Assim nada escurece o
 * vidro por dentro. Criada uma vez por tamanho ([drawWithCache]).
 */
private fun Modifier.sombraExterna(shape: Shape, elevation: Dp, alpha: Float): Modifier = drawWithCache {
    val corpo = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val alcance = elevation.toPx() * 0.9f
    val queda = elevation.toPx() * 0.25f
    onDrawBehind {
        clipPath(corpo, ClipOp.Difference) {
            translate(top = queda) {
                for (i in SHADOW_LAYERS downTo 1) {
                    drawPath(
                        corpo,
                        Color.Black.copy(alpha = alpha / SHADOW_LAYERS),
                        style = Stroke(width = alcance * 2f * i / SHADOW_LAYERS, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

private const val LIGHT_SURFACE_ALPHA = 0.86f
private const val LIGHT_SHADOW_ALPHA = 0.07f
private const val DARK_SHADOW_ALPHA = 0.30f
private const val SHADOW_LAYERS = 16

/** Item selecionado em vidro (aba atual, botão principal): translúcido na cor de destaque, com contorno. */
fun Modifier.glassAccent(shape: Shape, accent: Color): Modifier = this
    .clip(shape)
    .background(accent.copy(alpha = 0.13f))
    .border(1.dp, accent.copy(alpha = 0.55f), shape)

private const val GLOW_ALPHA = 0.18f

/**
 * Fundo de cartão que segue o tema: em vidro quando ligado ([glassSurface], com sombra leve)
 * e, desligado, o fundo liso de sempre. Usado nos cartões das telas comuns (ajustes, equalizador,
 * estatísticas, fila…), para o vidro valer no app inteiro sem mudar os outros temas.
 */
@Composable
fun Modifier.cardSurface(
    shape: Shape,
    color: Color = SonivellTheme.colors.surface,
    elevation: Dp = 6.dp,
): Modifier {
    // Essencial e Contraste: cards sem borda, o contraste vem da superfície. O Ondas tem contorno em tudo.
    val style = SonivellTheme.style
    val outline = if (style == UiStyle.ESSENCIAL || style == UiStyle.CONTRASTE) Color.Transparent else SonivellTheme.colors.outline
    return if (SonivellTheme.glass) glassSurface(shape, color, outline, elevation = elevation)
    else clip(shape).background(color)
}

/**
 * Como o vidro do app está se comportando agora, decidido uma vez em [SonivellTheme] a partir
 * dos ajustes, da versão do Android e da economia de bateria. As telas só consultam.
 *
 * - [backdrop]: as telas ficam transparentes sobre o fundo com a capa desfocada ([screenBackground]).
 * - [blur]: as barras (abas, mini player, faixa da barra de status) desfocam o que passa por baixo
 *   ([glassBar]). Desligado, elas ficam sólidas no fundo do tema, como antes.
 * - [scrim]: quanto do fundo do tema cobre a capa. Alto o bastante para o texto de apoio passar
 *   4,5:1 mesmo sobre uma capa branca (no escuro) ou preta (no claro).
 */
@Immutable
data class GlassMaterial(
    val backdrop: Boolean = false,
    val blur: Boolean = false,
    val scrim: Float = SCRIM_DARK,
)

val LocalGlassMaterial = staticCompositionLocalOf { GlassMaterial() }

internal const val SCRIM_DARK = 0.80f
internal const val SCRIM_BLACK = 0.88f
internal const val SCRIM_TINTED = 0.74f
// Claro: quase branco puro, como no iOS; a capa só tinge de leve.
internal const val SCRIM_LIGHT = 0.90f

/**
 * Fundo da raiz de uma tela comum. Com o fundo da capa ligado fica transparente, para a capa
 * desfocada aparecer atrás; senão é o fundo liso do tema de sempre.
 */
@Composable
fun Modifier.screenBackground(): Modifier =
    if (LocalGlassMaterial.current.backdrop) this else background(SonivellTheme.colors.background)

/**
 * Vidro das barras fixas. O que passa por baixo é desfocado pelo Haze a um terço da resolução
 * (nove vezes menos pixels; o desfoque esconde a diferença) e coberto pelo fundo do tema a meia
 * opacidade, então cada tema tinge as barras com a própria cor. Sem desfoque (Reduzir
 * transparência, economia de bateria, Android abaixo do 12) a barra é sólida, como era.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.glassBar(state: HazeState, fade: BarFade = BarFade.None): Modifier {
    val c = SonivellTheme.colors
    // As barras de baixo são pílulas soltas; um retângulo de vidro com borda reta atrás delas
    // aparecia como uma faixa. Com [fade] a borda livre se dissolve no fundo.
    val mask = when (fade) {
        BarFade.None -> null
        BarFade.Top -> Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black)
        BarFade.Bottom -> Brush.verticalGradient(0.65f to Color.Black, 1f to Color.Transparent)
    }
    if (!LocalGlassMaterial.current.blur) {
        return when (fade) {
            BarFade.None -> background(c.background)
            BarFade.Top -> background(Brush.verticalGradient(0f to Color.Transparent, 0.12f to c.background))
            BarFade.Bottom -> background(Brush.verticalGradient(0.65f to c.background, 1f to Color.Transparent))
        }
    }
    val style = HazeStyle(
        backgroundColor = c.background,
        tints = listOf(HazeTint(c.background.copy(alpha = if (c.isDark) BAR_TINT_DARK else BAR_TINT_LIGHT))),
        blurRadius = 24.dp,
        noiseFactor = 0f,
        fallbackTint = HazeTint(c.background),
    )
    return hazeEffect(state, style) {
        inputScale = HazeInputScale.Fixed(BAR_INPUT_SCALE)
        this.mask = mask
    }
}

/** Qual borda de uma barra de vidro se dissolve no fundo ([glassBar]). */
enum class BarFade { None, Top, Bottom }

private const val BAR_TINT_DARK = 0.58f
private const val BAR_TINT_LIGHT = 0.66f
private const val BAR_INPUT_SCALE = 0.33f

/** Economia de bateria do Android, acompanhada ao vivo: com ela ligada o desfoque das barras para. */
@Composable
fun rememberPowerSaveMode(): State<Boolean> {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val state = remember(power) { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                state.value = power?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
