package com.savyor.app.ui.theme

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
    primary = SavyorDarkAccent, // #74B9A2 (lifted sage)
    onPrimary = SavyorDarkBackground, // #111312
    primaryContainer = SavyorDarkButtonAccent, // #2F5D50
    onPrimaryContainer = SavyorDarkTextPrimary, // #F2F1EC
    secondary = SavyorDarkAccent,
    onSecondary = SavyorDarkBackground,
    secondaryContainer = SavyorDarkSubtle,
    onSecondaryContainer = SavyorDarkTextPrimary,
    background = SavyorDarkBackground, // #111312
    surface = SavyorDarkCard, // #191C1B
    surfaceVariant = SavyorDarkSubtle, // #222625
    onBackground = SavyorDarkTextPrimary, // #F2F1EC
    onSurface = SavyorDarkTextPrimary,
    onSurfaceVariant = SavyorDarkTextSecondary, // #9BA19E
    outline = SavyorDarkBorder, // #2C312F
    outlineVariant = SavyorDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = SavyorLightAccent, // #2F5D50
    onPrimary = Color.White,
    primaryContainer = SavyorLightSubtle, // #F2F0EB
    onPrimaryContainer = SavyorLightAccent,
    secondary = SavyorLightAccent,
    onSecondary = Color.White,
    secondaryContainer = SavyorLightSubtle,
    onSecondaryContainer = SavyorLightTextPrimary,
    background = SavyorLightBackground, // #FAF9F6
    surface = SavyorLightCard, // #FFFFFF
    surfaceVariant = SavyorLightSubtle, // #F2F0EB
    onBackground = SavyorLightTextPrimary, // #1C1E1D
    onSurface = SavyorLightTextPrimary,
    onSurfaceVariant = SavyorLightTextSecondary, // #666A67
    outline = SavyorLightBorder, // #E6E2DA
    outlineVariant = SavyorLightBorder
)

@Composable
fun SavyorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SavyorTypography,
        content = content
    )
}
