package com.savyor.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SavyorForestGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = SavyorForestGreenDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = SavyorForestGreen,
    background = SavyorDarkBackground,
    surface = SavyorDarkSurface,
    onBackground = SavyorTextPrimaryDark,
    onSurface = SavyorTextPrimaryDark,
    outline = SavyorDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = SavyorForestGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = SavyorLightBorder,
    onPrimaryContainer = SavyorForestGreen,
    secondary = SavyorForestGreen,
    background = SavyorLightBackground,
    surface = SavyorLightSurface,
    onBackground = SavyorTextPrimaryLight,
    onSurface = SavyorTextPrimaryLight,
    outline = SavyorLightBorder
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
        content = content
    )
}
