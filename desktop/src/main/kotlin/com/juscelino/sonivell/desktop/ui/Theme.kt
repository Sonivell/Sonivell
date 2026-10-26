package com.juscelino.sonivell.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.desktop.Accent
import com.juscelino.sonivell.desktop.DarkBg
import com.juscelino.sonivell.desktop.ThemeMode

/** Paleta Ondas: grafite + um único acento (a mesma do app Android). */
@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceLow: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val track: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

fun accentColor(a: Accent, dark: Boolean): Color = when (a) {
    Accent.Lima -> if (dark) Color(0xFFC8F04A) else Color(0xFF4A7300)
    Accent.Azul -> if (dark) Color(0xFF7CC4FF) else Color(0xFF0B5FAA)
    Accent.Laranja -> if (dark) Color(0xFFFF9F5A) else Color(0xFFAD4A00)
    Accent.Lilas -> if (dark) Color(0xFFE8A6FF) else Color(0xFF8338A8)
    Accent.Rosa -> if (dark) Color(0xFFFF8FB1) else Color(0xFFB0305C)
    Accent.Turquesa -> if (dark) Color(0xFF5FE3D0) else Color(0xFF00756A)
    Accent.Verde -> if (dark) Color(0xFF1DB954) else Color(0xFF117A37)
}

fun paletteOf(accent: Accent, dark: Boolean, bg: DarkBg): Palette {
    val a = accentColor(accent, dark)
    return when {
        !dark -> Palette(
            Color(0xFFF6F6F3), Color(0xFFEBEBE6), Color(0xFFF0F0EC), Color(0xFFE0E0DA), Color(0xFFCFCFC8), Color(0xFFD6D6CF),
            Color(0xFF141518), Color(0xFF5B6068), Color(0xFF3B3F46), a, Color(0xFFFFFFFF), false,
        )
        bg == DarkBg.Preto -> Palette(
            Color(0xFF121212), Color(0xFF1F1B1B), Color(0xFF191414), Color(0xFF2A2626), Color(0xFF3A3535), Color(0xFF3E3E3E),
            Color(0xFFFFFFFF), Color(0xFFB3B3B3), Color(0xFFD9D9D9), a, Color(0xFF121212), true,
        )
        else -> Palette(
            Color(0xFF0E0F11), Color(0xFF1A1C20), Color(0xFF14161A), Color(0xFF24272C), Color(0xFF2E3137), Color(0xFF2A2D33),
            Color(0xFFF2F3F5), Color(0xFF9BA1A9), Color(0xFFC9CDD3), a, Color(0xFF0E0F11), true,
        )
    }
}

val Manrope = FontFamily(
    Font(resource = "fonts/manrope-400.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/manrope-500.ttf", weight = FontWeight.Medium),
    Font(resource = "fonts/manrope-600.ttf", weight = FontWeight.SemiBold),
    Font(resource = "fonts/manrope-700.ttf", weight = FontWeight.Bold),
    Font(resource = "fonts/manrope-800.ttf", weight = FontWeight.ExtraBold),
)

val LocalPalette = staticCompositionLocalOf { paletteOf(Accent.Lima, true, DarkBg.Grafite) }

object SonivellTheme {
    val colors: Palette @Composable get() = LocalPalette.current
}

@Composable
fun SonivellTheme(accent: Accent, mode: ThemeMode, bg: DarkBg, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.AUTO -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val p = paletteOf(accent, dark, bg)
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.accent, onPrimary = p.onAccent, background = p.background, onBackground = p.textPrimary,
        surface = p.background, onSurface = p.textPrimary, surfaceVariant = p.surface, onSurfaceVariant = p.textSecondary, outline = p.outline,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme) { content() }
    }
}

/** Texto no estilo do app: Manrope, com peso e tamanho explícitos. */
@Composable
fun Txt(
    text: String,
    size: Int = 14,
    weight: FontWeight = FontWeight.Medium,
    color: Color = SonivellTheme.colors.textPrimary,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    maxLines: Int = 1,
    letterSpacing: Float = 0f,
    lineHeight: Int? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = Manrope, fontSize = size.sp, fontWeight = weight, letterSpacing = letterSpacing.sp,
            lineHeight = (lineHeight ?: (size * 1.3).toInt()).sp,
        ),
    )
}
