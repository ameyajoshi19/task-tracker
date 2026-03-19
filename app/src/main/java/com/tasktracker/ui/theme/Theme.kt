package com.tasktracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SortdDarkColorScheme = darkColorScheme(
    primary = SortdColors.accent,
    onPrimary = Color.White,
    primaryContainer = SortdColors.Dark.elevated,
    onPrimaryContainer = SortdColors.accentLight,
    secondary = SortdColors.accentLight,
    background = SortdColors.Dark.background,
    onBackground = SortdColors.Dark.textPrimary,
    surface = SortdColors.Dark.surface,
    onSurface = SortdColors.Dark.textPrimary,
    surfaceVariant = SortdColors.Dark.card,
    onSurfaceVariant = SortdColors.Dark.textSecondary,
    surfaceContainerHigh = SortdColors.Dark.elevated,
    surfaceContainerHighest = SortdColors.Dark.elevated,
    outline = SortdColors.Dark.border,
    outlineVariant = SortdColors.Dark.border,
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFF3B1111),
    onErrorContainer = Color(0xFFFCA5A5),
)

private val SortdLightColorScheme = lightColorScheme(
    primary = SortdColors.accent,
    onPrimary = Color.White,
    primaryContainer = SortdColors.Light.elevated,
    onPrimaryContainer = SortdColors.accent,
    secondary = SortdColors.accentLight,
    background = SortdColors.Light.background,
    onBackground = SortdColors.Light.textPrimary,
    surface = SortdColors.Light.surface,
    onSurface = SortdColors.Light.textPrimary,
    surfaceVariant = SortdColors.Light.card,
    onSurfaceVariant = SortdColors.Light.textSecondary,
    surfaceContainerHigh = SortdColors.Light.elevated,
    surfaceContainerHighest = SortdColors.Light.elevated,
    outline = SortdColors.Light.border,
    outlineVariant = SortdColors.Light.border,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

@Composable
fun SortdTheme(
    themeMode: String = "auto",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) SortdDarkColorScheme else SortdLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TaskTrackerTypography,
        content = content,
    )
}

