package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.core.preferences.ThemeMode

private val LightColors = lightColorScheme(
    primary = JadePrimary,
    onPrimary = Color.White,
    primaryContainer = JadeContainerLight,
    onPrimaryContainer = Color(0xFF042F2C),
    secondary = SlateSecondary,
    onSecondary = Color.White,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = GoldAccent,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainerLight,
    onTertiaryContainer = Color(0xFF451A03),
    background = LightBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = SemanticError,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = JadePrimaryDark,
    onPrimary = Color(0xFF032A27),
    primaryContainer = JadeContainerDark,
    onPrimaryContainer = Color(0xFF99F6E4),
    secondary = SlateSecondaryDark,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = GoldAccentDark,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = GoldContainerDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = DarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

@Composable
fun MiniMartProTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = MiniMartTypography,
        content = content,
    )
}
