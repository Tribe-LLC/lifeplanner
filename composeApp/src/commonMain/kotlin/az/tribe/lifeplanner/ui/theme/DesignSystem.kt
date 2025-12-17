package az.tribe.lifeplanner.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Life Planner Design System
 *
 * Centralized design tokens for consistent UI across the app.
 * All spacing follows an 8dp grid system.
 */
object LifePlannerDesign {

    /**
     * Elevation values for cards and surfaces.
     * Use sparingly - flat design is preferred.
     */
    object Elevation {
        val none: Dp = 0.dp
        val low: Dp = 2.dp
        val medium: Dp = 4.dp
        val high: Dp = 8.dp
    }

    /**
     * Corner radius values for rounded shapes.
     */
    object CornerRadius {
        val extraSmall: Dp = 8.dp
        val small: Dp = 12.dp
        val medium: Dp = 16.dp
        val large: Dp = 20.dp
        val extraLarge: Dp = 24.dp
        val full: Dp = 100.dp // For circular shapes
    }

    /**
     * Padding values for content areas.
     */
    object Padding {
        val none: Dp = 0.dp
        val extraSmall: Dp = 4.dp
        val small: Dp = 8.dp
        val medium: Dp = 12.dp
        val standard: Dp = 16.dp
        val large: Dp = 20.dp
        val extraLarge: Dp = 24.dp

        // Semantic padding
        val screenHorizontal: Dp = 16.dp
        val screenVertical: Dp = 16.dp
        val cardContent: Dp = 16.dp
        val cardContentLarge: Dp = 20.dp
    }

    /**
     * Spacing values between elements (based on 8dp grid).
     */
    object Spacing {
        val none: Dp = 0.dp
        val xxs: Dp = 4.dp
        val xs: Dp = 8.dp
        val sm: Dp = 12.dp
        val md: Dp = 16.dp
        val lg: Dp = 20.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp

        // Common spacing for lists
        val listItemGap: Dp = 12.dp
        val sectionGap: Dp = 24.dp
    }

    /**
     * Icon sizes for different contexts.
     */
    object IconSize {
        val extraSmall: Dp = 16.dp
        val small: Dp = 20.dp
        val medium: Dp = 24.dp
        val large: Dp = 32.dp
        val extraLarge: Dp = 48.dp

        // Specific contexts
        val emptyState: Dp = 64.dp
        val avatar: Dp = 72.dp
        val categoryIndicator: Dp = 8.dp
    }

    /**
     * Standard component heights.
     */
    object ComponentSize {
        val buttonHeight: Dp = 48.dp
        val chipHeight: Dp = 32.dp
        val inputFieldHeight: Dp = 56.dp
        val progressBarHeight: Dp = 8.dp
        val dividerHeight: Dp = 1.dp
        val fabSize: Dp = 56.dp
        val smallFabSize: Dp = 40.dp
    }

    /**
     * Opacity values for transparency effects.
     */
    object Alpha {
        const val disabled: Float = 0.38f
        const val medium: Float = 0.6f
        const val high: Float = 0.87f
        const val overlay: Float = 0.5f
        const val containerLight: Float = 0.1f
        const val containerMedium: Float = 0.2f
    }
}
