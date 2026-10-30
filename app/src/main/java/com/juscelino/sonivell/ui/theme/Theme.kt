package com.juscelino.sonivell.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R

/** Paleta do mockup: grafite + um único acento. */
@Immutable
data class SonivellColors(
    val background: Color = Color(0xFF0E0F11),
    val surface: Color = Color(0xFF1A1C20),
    val surfaceLow: Color = Color(0xFF16181B),
    val surfaceHigh: Color = Color(0xFF24272C),
    val outline: Color = Color(0xFF2E3137),
    val track: Color = Color(0xFF2A2D33),
    val textPrimary: Color = Color(0xFFF2F3F5),
    val textSecondary: Color = Color(0xFF9BA1A9),
    val textTertiary: Color = Color(0xFFC9CDD3),
    val accent: Color = Accents.Lima,
    val onAccent: Color = Color(0xFF0E0F11),
    /** Fundo do mini player, um degrau acima da superfície. */
    val miniPlayer: Color = Color(0xFF1E2125),
    val isDark: Boolean = true,
)

/** Paleta clara: mesmos papéis, contraste invertido. Textos passam 4,5:1 sobre o fundo. */
fun lightColors(accent: Color) = SonivellColors(
    background = Color(0xFFF6F6F3),
    surface = Color(0xFFEBEBE6),
    surfaceLow = Color(0xFFF0F0EC),
    surfaceHigh = Color(0xFFE0E0DA),
    outline = Color(0xFFCFCFC8),
    track = Color(0xFFD6D6CF),
    textPrimary = Color(0xFF141518),
    textSecondary = Color(0xFF5B6068),
    textTertiary = Color(0xFF3B3F46),
    accent = accent,
    onAccent = Color(0xFFFFFFFF),
    miniPlayer = Color(0xFFE4E4DE),
    isDark = false,
)

enum class ThemeMode { AUTO, LIGHT, DARK }

/**
 * Cores de destaque. Cada uma tem a versão para o tema escuro (clara, vibrante) e para o
 * claro (mais escura), para que texto e ícones na cor de destaque sigam legíveis.
 */
enum class AccentChoice(val dark: Color, val light: Color) {
    Lima(Color(0xFFC8F04A), Color(0xFF4A7300)),
    Azul(Color(0xFF7CC4FF), Color(0xFF0B5FAA)),
    Laranja(Color(0xFFFF9F5A), Color(0xFFAD4A00)),
    Lilas(Color(0xFFE8A6FF), Color(0xFF8338A8)),
    Rosa(Color(0xFFFF8FB1), Color(0xFFB0305C)),
    Turquesa(Color(0xFF5FE3D0), Color(0xFF00756A)),
    /** Verde #1DB954 (RGB 29, 185, 84); no tema claro, um tom mais escuro para o texto passar 4,5:1. */
    Verde(Color(0xFF1DB954), Color(0xFF117A37)),
    /** Creme do tema "Sonivell"; no claro, um dourado escuro para o texto passar 4,5:1. */
    Creme(Color(0xFFEFE3CC), Color(0xFF7A5A1E)),
}

/** Fundo do tema escuro: grafite (padrão do Sonivell) ou preto. */
enum class DarkBackground { Grafite, Preto }

/** Paleta escura em preto: fundo #121212, superfícies a partir de #191414 e texto secundário #B3B3B3. */
fun blackColors(accent: Color) = SonivellColors(
    background = Color(0xFF121212),
    surface = Color(0xFF1F1B1B),
    surfaceLow = Color(0xFF191414),
    surfaceHigh = Color(0xFF2A2626),
    outline = Color(0xFF3A3535),
    track = Color(0xFF3E3E3E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB3B3B3),
    textTertiary = Color(0xFFD9D9D9),
    accent = accent,
    onAccent = Color(0xFF121212),
    miniPlayer = Color(0xFF282424),
    isDark = true,
)

/**
 * Paleta "fundo colorido": o fundo, as superfícies e os textos de apoio ganham o tom da cor de
 * destaque (como no mockup "fundo na cor do tema"). No escuro, tons bem fundos da cor; no
 * claro, tons pastel. Vale para qualquer layout.
 */
fun tintedColors(accent: Color, dark: Boolean): SonivellColors {
    val (h, sat, _) = accent.toHsl()
    fun tone(s: Float, l: Float) = Color.hsl(h, (sat * s).coerceIn(0f, 1f), l)
    return if (dark) SonivellColors(
        background = tone(0.55f, 0.10f),
        surface = tone(0.45f, 0.15f),
        surfaceLow = tone(0.50f, 0.125f),
        surfaceHigh = tone(0.42f, 0.19f),
        outline = tone(0.38f, 0.25f),
        track = tone(0.38f, 0.24f),
        textPrimary = Color(0xFFF6F4F0),
        textSecondary = tone(0.35f, 0.78f),
        textTertiary = tone(0.25f, 0.88f),
        accent = accent,
        onAccent = tone(0.60f, 0.09f),
        miniPlayer = tone(0.44f, 0.17f),
        isDark = true,
    ) else SonivellColors(
        background = tone(0.55f, 0.955f),
        surface = tone(0.45f, 0.91f),
        surfaceLow = tone(0.50f, 0.935f),
        surfaceHigh = tone(0.42f, 0.87f),
        outline = tone(0.35f, 0.80f),
        track = tone(0.35f, 0.84f),
        textPrimary = Color(0xFF141518),
        textSecondary = tone(0.30f, 0.33f),
        textTertiary = tone(0.25f, 0.22f),
        accent = accent,
        onAccent = Color(0xFFFFFFFF),
        miniPlayer = tone(0.44f, 0.89f),
        isDark = false,
    )
}

/** Cores do tema "Sonivell": preto quente, creme e dourado. */
object NoraColors {
    val Bg = Color(0xFF080706)
    val Card = Color(0xFF14110F)
    val Line = Color(0xFF2B2621)
    val Cream = Color(0xFFEFE3CC)
    val Text = Color(0xFFEFE8DC)
    val Muted = Color(0xFF8F877B)
    val Gold = Color(0xFFC9A56A)
}

/** Paleta do tema "Sonivell": sempre escura, com a cor de destaque escolhida (creme por padrão). */
fun noraColors(accent: Color = NoraColors.Cream) = SonivellColors(
    background = NoraColors.Bg,
    surface = NoraColors.Card,
    surfaceLow = Color(0xFF0E0C0B),
    surfaceHigh = Color(0xFF1C1814),
    outline = NoraColors.Line,
    track = Color(0xFF2B2621),
    textPrimary = NoraColors.Text,
    textSecondary = NoraColors.Muted,
    textTertiary = Color(0xFFB8B0A2),
    accent = accent,
    onAccent = Color(0xFF1A1612),
    miniPlayer = NoraColors.Card,
    isDark = true,
)

/** Matiz (0–360), saturação e luminosidade (0–1) de uma cor. */
private fun Color.toHsl(): Triple<Float, Float, Float> {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val l = (max + min) / 2f
    val d = max - min
    if (d == 0f) return Triple(0f, 0f, l)
    val s = d / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when (max) {
        red -> 60f * (((green - blue) / d) % 6f)
        green -> 60f * ((blue - red) / d + 2f)
        else -> 60f * ((red - green) / d + 4f)
    }
    return Triple((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
}

object Accents {
    val Lima = Color(0xFFC8F04A)
    val Azul = Color(0xFF7CC4FF)
    val Laranja = Color(0xFFFF9F5A)
    val Lilas = Color(0xFFE8A6FF)
}

/** Tons das capas geradas quando a faixa não tem arte embutida. */
object ArtTones {
    val backgrounds = listOf(
        Color(0xFF1B2A26), Color(0xFF1D2433), Color(0xFF2E221A), Color(0xFF2A1D30),
    )
    val rings = listOf(
        Color(0xFF3B6457), Color(0xFF3A4A6B), Color(0xFF6B4A33), Color(0xFF5A3D66),
    )
    val cores = listOf(Accents.Lima, Accents.Azul, Accents.Laranja, Accents.Lilas)
}

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(
    manrope(400), manrope(500), manrope(600), manrope(700), manrope(800),
)

/**
 * Manrope mais fina para o tema "Sonivell". Sem os pesos 700 e 800: textos que pedem negrito
 * pesado caem no 600, o que mantém a tipografia leve em todas as telas.
 */
val ManropeFina = FontFamily(
    manrope(200), manrope(300), manrope(400), manrope(500), manrope(600),
)

private val NoraTypography = Typography(
    displaySmall = TextStyle(fontFamily = ManropeFina, fontWeight = FontWeight.Light, fontSize = 30.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = ManropeFina, fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = ManropeFina, fontWeight = FontWeight.Normal, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = ManropeFina, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    titleSmall = TextStyle(fontFamily = ManropeFina, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = ManropeFina, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = ManropeFina, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = ManropeFina, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = ManropeFina, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = ManropeFina, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 1.4.sp),
)

private val SonivellTypography = Typography(
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.4.sp),
)

val LocalSonivellColors = staticCompositionLocalOf { SonivellColors() }

object SonivellTheme {
    val colors: SonivellColors
        @Composable get() = LocalSonivellColors.current

    val icons: SonivellIcons
        @Composable get() = LocalSonivellIcons.current
}

@Composable
fun SonivellTheme(
    accent: AccentChoice = AccentChoice.Lima,
    darkTheme: Boolean = true,
    iconStyle: IconStyle = IconStyle.FINO,
    darkBackground: DarkBackground = DarkBackground.Grafite,
    /** Fundo e superfícies no tom da cor de destaque, em qualquer layout. */
    tintedBackground: Boolean = false,
    /** Cor de destaque tirada do papel de parede (Material You, Android 12+). */
    systemColors: Boolean = false,
    /**
     * Tema "Sonivell": tipografia própria e sempre escuro. A cor de destaque, as cores do sistema,
     * o fundo colorido e o fundo preto valem aqui também; sem eles, fica o preto quente.
     */
    noraStyle: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamic = systemColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val accentColor = when {
        dynamic && darkTheme -> dynamicDarkColorScheme(context).primary
        dynamic -> dynamicLightColorScheme(context).primary
        darkTheme -> accent.dark
        else -> accent.light
    }
    val base = when {
        noraStyle && tintedBackground -> tintedColors(accentColor, dark = true)
        noraStyle && darkBackground == DarkBackground.Preto -> blackColors(accentColor)
        noraStyle -> noraColors(accentColor)
        tintedBackground -> tintedColors(accentColor, darkTheme)
        !darkTheme -> lightColors(accentColor)
        darkBackground == DarkBackground.Preto -> blackColors(accentColor)
        else -> SonivellColors(accent = accentColor)
    }
    // A cor do sistema pode ser clara ou escura: o texto sobre ela segue a escolha do sistema.
    val colors = if (dynamic) {
        base.copy(onAccent = if (darkTheme) dynamicDarkColorScheme(context).onPrimary else dynamicLightColorScheme(context).onPrimary)
    } else base
    val scheme = (if (darkTheme || noraStyle) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.background,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surface,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.outline,
    )
    androidx.compose.runtime.CompositionLocalProvider(
        LocalSonivellColors provides colors,
        LocalSonivellIcons provides iconStyle.icons(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = if (noraStyle) NoraTypography else SonivellTypography, content = content)
    }
}
