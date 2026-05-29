package com.secura9.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF00FFE5)
val Green = Color(0xFF00FF88)
val Red = Color(0xFFFF003C)
val Yellow = Color(0xFFFFE600)
val Bg = Color(0xFF030810)
val Panel = Color(0xFF070F1C)
val Panel2 = Color(0xFF0A1520)
val Dim = Color(0xFF0A1828)
val Text = Color(0xFFB8DDF0)
val Muted = Color(0xFF3A6080)

private val DarkColorScheme = darkColorScheme(
    primary = Cyan,
    secondary = Green,
    tertiary = Red,
    background = Bg,
    surface = Panel,
    surfaceVariant = Panel2,
    onPrimary = Bg,
    onSecondary = Bg,
    onTertiary = Color.White,
    onBackground = Text,
    onSurface = Text,
    outline = Muted,
)

@Composable
fun Secura9Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
