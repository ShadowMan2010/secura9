package com.secura9.dashboard.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = BgDark,
    primaryContainer = CyanDim,
    secondary = Purple,
    onSecondary = Color.White,
    secondaryContainer = PurpleDim,
    tertiary = Green,
    onTertiary = BgDark,
    background = BgDark,
    onBackground = TextPrimary,
    surface = BgPanel,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = TextMuted,
    error = Red,
    onError = Color.White,
    errorContainer = RedDim,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    secondary = CyanDim,
    onSecondary = Color.White,
    tertiary = GreenDim,
    onTertiary = Color.White,
    background = BgLight,
    onBackground = TextOnLight,
    surface = SurfaceLight,
    onSurface = TextOnLight,
    surfaceVariant = Color(0xFFE8E0F0),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFC0B8D0),
    error = Red,
    onError = Color.White,
)

@Composable
fun SECURA9Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
