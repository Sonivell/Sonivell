package com.juscelino.sonivell.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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
    content: @Composable () -> Unit,
) {
    val colors = when {
        !darkTheme -> lightColors(accent.light)
        darkBackground == DarkBackground.Preto -> blackColors(accent.dark)
        else -> SonivellColors(accent = accent.dark)
    }
    val scheme = (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
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
        MaterialTheme(colorScheme = scheme, typography = SonivellTypography, content = content)
    }
}
