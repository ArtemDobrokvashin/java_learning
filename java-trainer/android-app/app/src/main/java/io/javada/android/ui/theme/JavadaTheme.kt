package io.javada.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Точная цветовая палитра веб-тренажёра JavaДа (`frontend/css/main.css` и `components.css`).
 */
@Immutable
data class JavadaPalette(
    val isDark: Boolean,
    val green: Color,
    val greenDeep: Color,
    val greenBright: Color,
    val greenLip: Color,
    val greenSoft: Color,
    val yellow: Color,
    val yellowSoft: Color,
    val yellowText: Color,
    val red: Color,
    val redSoft: Color,
    val redText: Color,
    val blue: Color,
    val reviewPurple: Color,
    val reviewPurpleSoft: Color,
    val xpGold: Color,
    val streakOrange: Color,
    val bg: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val text: Color,
    val muted: Color,
    val mutedStrong: Color,
    val line: Color,
    val topbarBg: Color,
    // Цвета встроенного редактора кода и блоков теории (.theory-code / .editor-body)
    val codeBg: Color = Color(0xFF17241E),
    val codeHeaderBg: Color = Color(0xFF1B2922),
    val codeBorder: Color = Color(0xFF31453A),
    val codeText: Color = Color(0xFFDFEDDE),
    val codeMuted: Color = Color(0xFF9FB6A4),
    val blankInputBg: Color = Color(0xFF284331),
    val blankInputBorder: Color = Color(0xFF669871)
)

val LightJavadaPalette = JavadaPalette(
    isDark = false,
    green = Color(0xFF58CC02),
    greenDeep = Color(0xFF419D09),
    greenBright = Color(0xFF68D727),
    greenLip = Color(0xFF419D09),
    greenSoft = Color(0xFFEAF8E4),
    yellow = Color(0xFFFFC800),
    yellowSoft = Color(0xFFFFF6D7),
    yellowText = Color(0xFF8A6617),
    red = Color(0xFFFF4B4B),
    redSoft = Color(0xFFFFF0EC),
    redText = Color(0xFFAD4141),
    blue = Color(0xFF438DC1),
    reviewPurple = Color(0xFF8763D3),
    reviewPurpleSoft = Color(0xFFF3EDFF),
    xpGold = Color(0xFFE5A800),
    streakOrange = Color(0xFFFF8637),
    bg = Color(0xFFF7F8F5),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFFAFBF8),
    text = Color(0xFF303C36),
    muted = Color(0xFF82918A),
    mutedStrong = Color(0xFF596C61),
    line = Color(0xFFE8EEE8),
    topbarBg = Color(0xFFF7F8F5)
)

val DarkJavadaPalette = JavadaPalette(
    isDark = true,
    green = Color(0xFF58CC02),
    greenDeep = Color(0xFF87D967),
    greenBright = Color(0xFF68D727),
    greenLip = Color(0xFF419D09),
    greenSoft = Color(0xFF253D29),
    yellow = Color(0xFFFFC800),
    yellowSoft = Color(0xFF463D23),
    yellowText = Color(0xFFF5D06F),
    red = Color(0xFFFF4B4B),
    redSoft = Color(0xFF432C2C),
    redText = Color(0xFFFF9E9E),
    blue = Color(0xFF5CA8DC),
    reviewPurple = Color(0xFFB793F4),
    reviewPurpleSoft = Color(0xFF2E2540),
    xpGold = Color(0xFFFFC800),
    streakOrange = Color(0xFFFF8637),
    bg = Color(0xFF121B17),
    surface = Color(0xFF1B2921),
    surfaceSoft = Color(0xFF213027),
    text = Color(0xFFE9F2E9),
    muted = Color(0xFFA1B3A4),
    mutedStrong = Color(0xFFC5D4C6),
    line = Color(0xFF304337),
    topbarBg = Color(0xFF121B17)
)

val LocalJavadaColors = staticCompositionLocalOf { LightJavadaPalette }

private val DarkColorScheme = darkColorScheme(
    primary = DarkJavadaPalette.green,
    onPrimary = Color.White,
    primaryContainer = DarkJavadaPalette.greenSoft,
    onPrimaryContainer = DarkJavadaPalette.greenDeep,
    secondary = DarkJavadaPalette.yellow,
    onSecondary = Color(0xFF3A2D00),
    secondaryContainer = DarkJavadaPalette.yellowSoft,
    onSecondaryContainer = DarkJavadaPalette.yellowText,
    tertiary = DarkJavadaPalette.blue,
    background = DarkJavadaPalette.bg,
    onBackground = DarkJavadaPalette.text,
    surface = DarkJavadaPalette.surface,
    onSurface = DarkJavadaPalette.text,
    surfaceVariant = DarkJavadaPalette.surfaceSoft,
    onSurfaceVariant = DarkJavadaPalette.mutedStrong,
    outline = DarkJavadaPalette.line,
    error = DarkJavadaPalette.red,
    errorContainer = DarkJavadaPalette.redSoft,
    onErrorContainer = DarkJavadaPalette.redText
)

private val LightColorScheme = lightColorScheme(
    primary = LightJavadaPalette.green,
    onPrimary = Color.White,
    primaryContainer = LightJavadaPalette.greenSoft,
    onPrimaryContainer = LightJavadaPalette.greenDeep,
    secondary = LightJavadaPalette.yellow,
    onSecondary = Color(0xFF3A2D00),
    secondaryContainer = LightJavadaPalette.yellowSoft,
    onSecondaryContainer = LightJavadaPalette.yellowText,
    tertiary = LightJavadaPalette.blue,
    background = LightJavadaPalette.bg,
    onBackground = LightJavadaPalette.text,
    surface = LightJavadaPalette.surface,
    onSurface = LightJavadaPalette.text,
    surfaceVariant = LightJavadaPalette.surfaceSoft,
    onSurfaceVariant = LightJavadaPalette.mutedStrong,
    outline = LightJavadaPalette.line,
    error = LightJavadaPalette.red,
    errorContainer = LightJavadaPalette.redSoft,
    onErrorContainer = LightJavadaPalette.redText
)

@Composable
fun JavadaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkJavadaPalette else LightJavadaPalette
    CompositionLocalProvider(LocalJavadaColors provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            content = content
        )
    }
}
