package tv.meowfilm.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MeowFilmDark: ColorScheme =
    darkColorScheme(
        background = Color(0xFF0B0F14),
        surface = Color(0xFF0F1720),
        onSurface = Color(0xFFE6EDF3),
        onBackground = Color(0xFFE6EDF3),
        primary = Color(0xFF7DD3FC),
        onPrimary = Color(0xFF00121B),
        secondary = Color(0xFFA7F3D0),
        onSecondary = Color(0xFF00140C),
        tertiary = Color(0xFFC7D2FE),
        onTertiary = Color(0xFF0B1024),
    )

@Composable
fun MeowFilmTvTheme(
    themeMode: String = "dark",
    content: @Composable () -> Unit,
) {
    val light =
        when (themeMode.trim().lowercase()) {
            "light" -> true
            else -> false
        }
    val scheme = if (light) MeowFilmLight else MeowFilmDark
    MaterialTheme(
        colorScheme = scheme,
        typography = MeowFilmTypography(),
        content = content,
    )
}

private val MeowFilmLight: ColorScheme =
    lightColorScheme(
        background = Color(0xFFF6F8FC),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0B1220),
        onBackground = Color(0xFF0B1220),
        primary = Color(0xFF0EA5E9),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF10B981),
        onSecondary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF6366F1),
        onTertiary = Color(0xFFFFFFFF),
    )
