package az.tribe.lifeplanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Static composition local for providing modern colors throughout the app
val LocalModernColors = staticCompositionLocalOf {
    ModernColorScheme(
        // Initialize with default color
        background = Color.Unspecified,
        surface = Color.Unspecified,
        surfaceVariant = Color.Unspecified,
        textPrimary = Color.Unspecified,
        textSecondary = Color.Unspecified,
        textTertiary = Color.Unspecified,
        textDisabled = Color.Unspecified,

        primary = Color.Unspecified,
        primaryVariant = Color.Unspecified,
        primaryDark = Color.Unspecified,
        primaryContainer = Color.Unspecified,
        onPrimaryContainer = Color.Unspecified,

        secondary = Color.Unspecified,
        secondaryVariant = Color.Unspecified,
        secondaryContainer = Color.Unspecified,
        onSecondaryContainer = Color.Unspecified,

        accent = Color.Unspecified,
        accentVariant = Color.Unspecified,
        tertiaryContainer = Color.Unspecified,
        onTertiaryContainer = Color.Unspecified,

        success = Color.Unspecified,
        successContainer = Color.Unspecified,
        onSuccessContainer = Color.Unspecified,

        error = Color.Unspecified,
        errorContainer = Color.Unspecified,
        onErrorContainer = Color.Unspecified,

        warning = Color.Unspecified,
        warningContainer = Color.Unspecified,
        onWarningContainer = Color.Unspecified,

        divider = Color.Unspecified,
        outline = Color.Unspecified,
        outlineVariant = Color.Unspecified,
        scrim = Color.Unspecified,

        cardBackground = Color.Unspecified,
        chipBackground = Color.Unspecified,
        chipText = Color.Unspecified,

        disabledBackground = Color.Unspecified,
        disabledContent = Color.Unspecified,

        gradientPrimary = listOf(Color.Unspecified, Color.Unspecified),
        gradientSecondary = listOf(Color.Unspecified, Color.Unspecified),
        gradientAccent = listOf(Color.Unspecified, Color.Unspecified),
        gradientSuccess = listOf(Color.Unspecified, Color.Unspecified),
        gradientWarning = listOf(Color.Unspecified, Color.Unspecified),
        gradientError = listOf(Color.Unspecified, Color.Unspecified)
    )
}

@Composable
fun LifePlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Create Material3 color scheme from our modern colors
    val colorScheme = createColorScheme(darkTheme)

    // Select the appropriate modern color scheme based on theme
    val modernColorScheme = if (darkTheme) {
        ModernThemeColors.Dark
    } else {
        ModernThemeColors.Light
    }

    // Provide the modern colors to the composition
    CompositionLocalProvider(
        LocalModernColors provides modernColorScheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LifePlannerTypography(),
            shapes = ModernShapes,
            content = content
        )
    }
}

// Extension property to easily access modern colors
val MaterialTheme.modernColors: ModernColorScheme
    @Composable
    get() = LocalModernColors.current

// Create Material3 ColorScheme from our modern colors
private fun createColorScheme(darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        darkColorScheme(
            primary = ModernColors.Dark.primary,
            onPrimary = ModernColors.Dark.textPrimary,
            primaryContainer = ModernColors.Dark.primaryContainer,
            onPrimaryContainer = ModernColors.Dark.onPrimaryContainer,

            secondary = ModernColors.Dark.secondary,
            onSecondary = ModernColors.Dark.textPrimary,
            secondaryContainer = ModernColors.Dark.secondaryContainer,
            onSecondaryContainer = ModernColors.Dark.onSecondaryContainer,

            tertiary = ModernColors.Dark.accent,
            onTertiary = ModernColors.Dark.textPrimary,
            tertiaryContainer = ModernColors.Dark.tertiaryContainer,
            onTertiaryContainer = ModernColors.Dark.onTertiaryContainer,

            error = ModernColors.Dark.error,
            onError = ModernColors.Dark.textPrimary,
            errorContainer = ModernColors.Dark.errorContainer,
            onErrorContainer = ModernColors.Dark.onErrorContainer,

            background = ModernColors.Dark.background,
            onBackground = ModernColors.Dark.textPrimary,

            surface = ModernColors.Dark.surface,
            onSurface = ModernColors.Dark.textPrimary,
            surfaceVariant = ModernColors.Dark.surfaceVariant,
            onSurfaceVariant = ModernColors.Dark.textSecondary,

            outline = ModernColors.Dark.outline,
            outlineVariant = ModernColors.Dark.outlineVariant,

            scrim = ModernColors.Dark.scrim
        )
    } else {
        lightColorScheme(
            primary = ModernColors.primary,
            onPrimary = Color.White,
            primaryContainer = ModernColors.primaryContainer,
            onPrimaryContainer = ModernColors.onPrimaryContainer,

            secondary = ModernColors.secondary,
            onSecondary = Color.White,
            secondaryContainer = ModernColors.secondaryContainer,
            onSecondaryContainer = ModernColors.onSecondaryContainer,

            tertiary = ModernColors.accent,
            onTertiary = Color.White,
            tertiaryContainer = ModernColors.tertiaryContainer,
            onTertiaryContainer = ModernColors.onTertiaryContainer,

            error = ModernColors.error,
            onError = Color.White,
            errorContainer = ModernColors.errorContainer,
            onErrorContainer = ModernColors.onErrorContainer,

            background = ModernColors.background,
            onBackground = ModernColors.textPrimary,

            surface = ModernColors.surface,
            onSurface = ModernColors.textPrimary,
            surfaceVariant = ModernColors.surfaceVariant,
            onSurfaceVariant = ModernColors.textSecondary,

            outline = ModernColors.outline,
            outlineVariant = ModernColors.outlineVariant,

            scrim = ModernColors.scrim
        )
    }
}