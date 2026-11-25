package com.savyor.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SavyorThemeState {
    private val _themeMode = MutableStateFlow(SavyorThemeMode.SYSTEM)
    val themeMode: StateFlow<SavyorThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: SavyorThemeMode) {
        _themeMode.value = mode
    }
}

private val ObsidianColorScheme = darkColorScheme(
    primary = ObsidianSavyorColors.ctaBg,
    onPrimary = ObsidianSavyorColors.ctaFg,
    primaryContainer = ObsidianSavyorColors.surfaceElevated,
    onPrimaryContainer = ObsidianSavyorColors.inkPrimary,
    secondary = ObsidianSavyorColors.inkSecondary,
    onSecondary = ObsidianSavyorColors.bgCanvas,
    secondaryContainer = ObsidianSavyorColors.surfaceRecessed,
    onSecondaryContainer = ObsidianSavyorColors.inkPrimary,
    background = ObsidianSavyorColors.bgCanvas,
    surface = ObsidianSavyorColors.surfaceCard,
    surfaceVariant = ObsidianSavyorColors.surfaceRecessed,
    surfaceTint = Color.Transparent,
    onBackground = ObsidianSavyorColors.inkPrimary,
    onSurface = ObsidianSavyorColors.inkPrimary,
    onSurfaceVariant = ObsidianSavyorColors.inkSecondary,
    outline = ObsidianSavyorColors.borderSubtle,
    outlineVariant = ObsidianSavyorColors.borderSubtle,
    error = ObsidianSavyorColors.errorFg
)

private val PorcelainColorScheme = lightColorScheme(
    primary = PorcelainSavyorColors.ctaBg,
    onPrimary = PorcelainSavyorColors.ctaFg,
    primaryContainer = PorcelainSavyorColors.surfaceRecessed,
    onPrimaryContainer = PorcelainSavyorColors.inkPrimary,
    secondary = PorcelainSavyorColors.inkSecondary,
    onSecondary = PorcelainSavyorColors.ctaFg,
    secondaryContainer = PorcelainSavyorColors.surfaceRecessed,
    onSecondaryContainer = PorcelainSavyorColors.inkPrimary,
    background = PorcelainSavyorColors.bgCanvas,
    surface = PorcelainSavyorColors.surfaceCard,
    surfaceVariant = PorcelainSavyorColors.surfaceRecessed,
    surfaceTint = Color.Transparent,
    onBackground = PorcelainSavyorColors.inkPrimary,
    onSurface = PorcelainSavyorColors.inkPrimary,
    onSurfaceVariant = PorcelainSavyorColors.inkSecondary,
    outline = PorcelainSavyorColors.borderSubtle,
    outlineVariant = PorcelainSavyorColors.borderSubtle,
    error = PorcelainSavyorColors.errorFg
)

@Composable
fun SavyorAppTheme(
    themeMode: SavyorThemeMode = SavyorThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        SavyorThemeMode.SYSTEM -> systemDark
        SavyorThemeMode.OBSIDIAN -> true
        SavyorThemeMode.PORCELAIN -> false
    }

    val savyorColors = if (darkTheme) ObsidianSavyorColors else PorcelainSavyorColors
    val colorScheme = if (darkTheme) ObsidianColorScheme else PorcelainColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSavyorColors provides savyorColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SavyorTypography,
            content = content
        )
    }
}

@Composable
fun SavyorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    SavyorAppTheme(
        themeMode = if (darkTheme) SavyorThemeMode.OBSIDIAN else SavyorThemeMode.PORCELAIN,
        content = content
    )
}
