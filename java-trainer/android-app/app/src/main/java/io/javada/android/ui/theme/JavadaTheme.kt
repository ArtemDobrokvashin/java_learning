package io.javada.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val JavadaOrange = Color(0xFFF97316)
val JavadaEmerald = Color(0xFF10B981)
val JavadaSky = Color(0xFF38BDF8)
val JavadaDarkBg = Color(0xFF0F172A)
val JavadaDarkSurface = Color(0xFF1E293B)

private val DarkColors = darkColorScheme(
    primary = JavadaOrange,
    onPrimary = Color.White,
    secondary = JavadaEmerald,
    onSecondary = Color.White,
    tertiary = JavadaSky,
    background = JavadaDarkBg,
    onBackground = Color(0xFFF8FAFC),
    surface = JavadaDarkSurface,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFEA580C),
    onPrimary = Color.White,
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun JavadaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
