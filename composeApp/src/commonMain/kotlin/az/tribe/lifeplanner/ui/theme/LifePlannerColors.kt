// Color.kt
package az.tribe.lifeplanner.ui.theme

import androidx.compose.ui.graphics.Color

// Modern color palette
object ModernColors {
    // Primary colors - vibrant blue with purple undertones
    val primary = Color(0xFF4A6FFF)
    val primaryVariant = Color(0xFF3B5BE5)
    val primaryDark = Color(0xFF2C42B0)
    val primaryContainer = Color(0xFFECF0FF)
    val onPrimaryContainer = Color(0xFF0C2379)

    // Secondary colors - complementary purple
    val secondary = Color(0xFF7A5AF8)
    val secondaryVariant = Color(0xFF6346E0)
    val secondaryContainer = Color(0xFFF1ECFF)
    val onSecondaryContainer = Color(0xFF330C79)

    // Tertiary/accent - coral for warmth and contrast
    val accent = Color(0xFFF86E5A)
    val accentVariant = Color(0xFFE05A46)
    val tertiaryContainer = Color(0xFFFFECEA)
    val onTertiaryContainer = Color(0xFF79160C)

    // Success, error, warning
    val success = Color(0xFF28C76F)
    val successContainer = Color(0xFFE0F7EA)
    val onSuccessContainer = Color(0xFF0B542C)

    val error = Color(0xFFEA5455)
    val errorContainer = Color(0xFFFFEDED)
    val onErrorContainer = Color(0xFF7A1214)

    val warning = Color(0xFFFF9F43)
    val warningContainer = Color(0xFFFFF4E6)
    val onWarningContainer = Color(0xFF663D0F)

    // Neutrals - clean with subtle undertones
    val background = Color(0xFFF8F9FC)
    val surface = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFF0F2FA)

    // Text colors
    val textPrimary = Color(0xFF2C3345)
    val textSecondary = Color(0xFF6E7A94)
    val textTertiary = Color(0xFF9AA6BC)
    val textDisabled = Color(0xFFCBD0DD)

    // Component specific
    val divider = Color(0xFFE8ECF4)
    val outline = Color(0xFFCBD0DD)
    val outlineVariant = Color(0xFFE8ECF4)
    val scrim = Color(0x80000000)

    // Dark theme colors
    object Dark {
        val primary = Color(0xFF6A87FF)
        val primaryVariant = Color(0xFF4A6FFF)
        val primaryDark = Color(0xFF3B5BE5)
        val primaryContainer = Color(0xFF1E2746)
        val onPrimaryContainer = Color(0xFFD7E0FF)

        val secondary = Color(0xFF9578FF)
        val secondaryVariant = Color(0xFF7A5AF8)
        val secondaryContainer = Color(0xFF262146)
        val onSecondaryContainer = Color(0xFFE9DFFF)

        val accent = Color(0xFFFF8A7A)
        val accentVariant = Color(0xFFF86E5A)
        val tertiaryContainer = Color(0xFF462421)
        val onTertiaryContainer = Color(0xFFFFDDD9)

        val success = Color(0xFF3DD98B)
        val successContainer = Color(0xFF1A3B2A)
        val onSuccessContainer = Color(0xFFCFF2DE)

        val error = Color(0xFFFF7273)
        val errorContainer = Color(0xFF3E1A1B)
        val onErrorContainer = Color(0xFFFFDADA)

        val warning = Color(0xFFFFBF75)
        val warningContainer = Color(0xFF3E2F1B)
        val onWarningContainer = Color(0xFFFFEBD4)

        val background = Color(0xFF121826)
        val surface = Color(0xFF1B2437)
        val surfaceVariant = Color(0xFF252E42)

        val textPrimary = Color(0xFFF5F6FA)
        val textSecondary = Color(0xFFB0B7C9)
        val textTertiary = Color(0xFF8792AB)
        val textDisabled = Color(0xFF5E6A84)

        val divider = Color(0xFF2E3850)
        val outline = Color(0xFF5E6A84)
        val outlineVariant = Color(0xFF2E3850)
        val scrim = Color(0xB3000000)
    }
}

// Modern color scheme data class that replaces the brutalist one
data class ModernColorScheme(
    // Base Colors
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,

    // UI Elements
    val primary: Color,
    val primaryVariant: Color,
    val primaryDark: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,

    val secondary: Color,
    val secondaryVariant: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,

    val accent: Color,
    val accentVariant: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,

    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,

    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,

    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,

    val divider: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,

    // Component Colors
    val cardBackground: Color,
    val chipBackground: Color,
    val chipText: Color,

    // States
    val disabledBackground: Color,
    val disabledContent: Color,

    // Gradient start/end colors for categories
    val gradientPrimary: List<Color>,
    val gradientSecondary: List<Color>,
    val gradientAccent: List<Color>,
    val gradientSuccess: List<Color>,
    val gradientWarning: List<Color>,
    val gradientError: List<Color>
)

// Define the light and dark modern themes
object ModernThemeColors {
    val Light = ModernColorScheme(
        background = ModernColors.background,
        surface = ModernColors.surface,
        surfaceVariant = ModernColors.surfaceVariant,
        textPrimary = ModernColors.textPrimary,
        textSecondary = ModernColors.textSecondary,
        textTertiary = ModernColors.textTertiary,
        textDisabled = ModernColors.textDisabled,

        primary = ModernColors.primary,
        primaryVariant = ModernColors.primaryVariant,
        primaryDark = ModernColors.primaryDark,
        primaryContainer = ModernColors.primaryContainer,
        onPrimaryContainer = ModernColors.onPrimaryContainer,

        secondary = ModernColors.secondary,
        secondaryVariant = ModernColors.secondaryVariant,
        secondaryContainer = ModernColors.secondaryContainer,
        onSecondaryContainer = ModernColors.onSecondaryContainer,

        accent = ModernColors.accent,
        accentVariant = ModernColors.accentVariant,
        tertiaryContainer = ModernColors.tertiaryContainer,
        onTertiaryContainer = ModernColors.onTertiaryContainer,

        success = ModernColors.success,
        successContainer = ModernColors.successContainer,
        onSuccessContainer = ModernColors.onSuccessContainer,

        error = ModernColors.error,
        errorContainer = ModernColors.errorContainer,
        onErrorContainer = ModernColors.onErrorContainer,

        warning = ModernColors.warning,
        warningContainer = ModernColors.warningContainer,
        onWarningContainer = ModernColors.onWarningContainer,

        divider = ModernColors.divider,
        outline = ModernColors.outline,
        outlineVariant = ModernColors.outlineVariant,
        scrim = ModernColors.scrim,

        cardBackground = ModernColors.surface,
        chipBackground = ModernColors.surfaceVariant,
        chipText = ModernColors.textSecondary,

        disabledBackground = ModernColors.surfaceVariant,
        disabledContent = ModernColors.textDisabled,

        // Gradient definitions
        gradientPrimary = listOf(ModernColors.primary, ModernColors.primaryVariant),
        gradientSecondary = listOf(ModernColors.secondary, ModernColors.secondaryVariant),
        gradientAccent = listOf(ModernColors.accent, ModernColors.accentVariant),
        gradientSuccess = listOf(ModernColors.success, Color(0xFF1AAC59)),
        gradientWarning = listOf(ModernColors.warning, Color(0xFFE88B2E)),
        gradientError = listOf(ModernColors.error, Color(0xFFD03A3B))
    )

    val Dark = ModernColorScheme(
        background = ModernColors.Dark.background,
        surface = ModernColors.Dark.surface,
        surfaceVariant = ModernColors.Dark.surfaceVariant,
        textPrimary = ModernColors.Dark.textPrimary,
        textSecondary = ModernColors.Dark.textSecondary,
        textTertiary = ModernColors.Dark.textTertiary,
        textDisabled = ModernColors.Dark.textDisabled,

        primary = ModernColors.Dark.primary,
        primaryVariant = ModernColors.Dark.primaryVariant,
        primaryDark = ModernColors.Dark.primaryDark,
        primaryContainer = ModernColors.Dark.primaryContainer,
        onPrimaryContainer = ModernColors.Dark.onPrimaryContainer,

        secondary = ModernColors.Dark.secondary,
        secondaryVariant = ModernColors.Dark.secondaryVariant,
        secondaryContainer = ModernColors.Dark.secondaryContainer,
        onSecondaryContainer = ModernColors.Dark.onSecondaryContainer,

        accent = ModernColors.Dark.accent,
        accentVariant = ModernColors.Dark.accentVariant,
        tertiaryContainer = ModernColors.Dark.tertiaryContainer,
        onTertiaryContainer = ModernColors.Dark.onTertiaryContainer,

        success = ModernColors.Dark.success,
        successContainer = ModernColors.Dark.successContainer,
        onSuccessContainer = ModernColors.Dark.onSuccessContainer,

        error = ModernColors.Dark.error,
        errorContainer = ModernColors.Dark.errorContainer,
        onErrorContainer = ModernColors.Dark.onErrorContainer,

        warning = ModernColors.Dark.warning,
        warningContainer = ModernColors.Dark.warningContainer,
        onWarningContainer = ModernColors.Dark.onWarningContainer,

        divider = ModernColors.Dark.divider,
        outline = ModernColors.Dark.outline,
        outlineVariant = ModernColors.Dark.outlineVariant,
        scrim = ModernColors.Dark.scrim,

        cardBackground = ModernColors.Dark.surface,
        chipBackground = ModernColors.Dark.surfaceVariant,
        chipText = ModernColors.Dark.textSecondary,

        disabledBackground = ModernColors.Dark.surfaceVariant,
        disabledContent = ModernColors.Dark.textDisabled,

        // Gradient definitions
        gradientPrimary = listOf(ModernColors.Dark.primary, ModernColors.Dark.primaryVariant),
        gradientSecondary = listOf(ModernColors.Dark.secondary, ModernColors.Dark.secondaryVariant),
        gradientAccent = listOf(ModernColors.Dark.accent, ModernColors.Dark.accentVariant),
        gradientSuccess = listOf(ModernColors.Dark.success, Color(0xFF2BC07C)),
        gradientWarning = listOf(ModernColors.Dark.warning, Color(0xFFE9A555)),
        gradientError = listOf(ModernColors.Dark.error, Color(0xFFE85657))
    )
}