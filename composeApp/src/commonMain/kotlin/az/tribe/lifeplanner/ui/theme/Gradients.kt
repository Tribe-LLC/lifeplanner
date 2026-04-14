package az.tribe.lifeplanner.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import az.tribe.lifeplanner.domain.enum.GoalCategory

/**
 * Life Planner Gradient System
 *
 * Beautiful gradient definitions for a modern, premium feel.
 */
object LifePlannerGradients {

    // ==================== PRIMARY GRADIENTS ====================

    /**
     * Primary brand gradient - Blue to Purple
     * Use for: Hero sections, primary CTAs, main headers
     */
    val primary: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF667EEA),  // Soft blue
                Color(0xFF764BA2)   // Rich purple
            )
        )

    /**
     * Primary gradient with angle (diagonal)
     */
    val primaryDiagonal: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF667EEA),
                Color(0xFF764BA2)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )

    // ==================== SEMANTIC GRADIENTS ====================

    /**
     * Success gradient - Teal to Green
     * Use for: Completion states, achievements, positive feedback
     */
    val success: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF11998E),  // Teal
                Color(0xFF38EF7D)   // Bright green
            )
        )

    /**
     * Warm gradient - Pink to Coral
     * Use for: Streaks, motivation, engagement features
     */
    val warm: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFFF093FB),  // Soft pink
                Color(0xFFF5576C)   // Coral red
            )
        )

    /**
     * Sunset gradient - Orange to Pink
     * Use for: Highlights, special features
     */
    val sunset: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF9A9E),  // Peach
                Color(0xFFFECFEF)   // Light pink
            )
        )

    /**
     * Ocean gradient - Deep blue to cyan
     * Use for: Calm sections, analytics
     */
    val ocean: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF2193B0),  // Ocean blue
                Color(0xFF6DD5ED)   // Light cyan
            )
        )

    /**
     * Night gradient - Dark purple to indigo
     * Use for: Dark mode accents, premium features
     */
    val night: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF0F2027),  // Dark
                Color(0xFF203A43),  // Mid dark
                Color(0xFF2C5364)   // Deep blue
            )
        )

    // ==================== CATEGORY GRADIENTS ====================

    /**
     * Career gradient - Professional blue
     */
    val career: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF2196F3),  // Blue
                Color(0xFF21CBF3)   // Cyan
            )
        )

    /**
     * Financial gradient - Money green
     */
    val financial: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF11998E),  // Teal
                Color(0xFF38EF7D)   // Green
            )
        )

    /**
     * Physical gradient - Energy orange
     */
    val physical: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF8008),  // Orange
                Color(0xFFFFC837)   // Yellow
            )
        )

    /**
     * Social gradient - Vibrant purple
     */
    val social: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF9C27B0),  // Purple
                Color(0xFFE040FB)   // Light purple
            )
        )

    /**
     * Emotional gradient - Calm teal
     */
    val emotional: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF009688),  // Teal
                Color(0xFF4DB6AC)   // Light teal
            )
        )

    /**
     * Spiritual gradient - Deep rose
     */
    val spiritual: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFFE91E63),  // Pink
                Color(0xFFFF6090)   // Light pink
            )
        )

    /**
     * Family gradient - Warm indigo
     */
    val family: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color(0xFF3F51B5),  // Indigo
                Color(0xFF7986CB)   // Light indigo
            )
        )

    // ==================== UTILITY FUNCTIONS ====================

    /**
     * Get gradient brush for a goal category
     */
    @Composable
    fun forCategory(category: GoalCategory): Brush {
        return when (category) {
            GoalCategory.CAREER -> career
            GoalCategory.FINANCIAL -> financial
            GoalCategory.PHYSICAL -> physical
            GoalCategory.SOCIAL -> social
            GoalCategory.EMOTIONAL -> emotional
            GoalCategory.SPIRITUAL -> spiritual
            GoalCategory.FAMILY -> family
        }
    }

    /**
     * Get gradient colors for a goal category (for custom use)
     */
    fun colorsForCategory(category: GoalCategory): List<Color> {
        return when (category) {
            GoalCategory.CAREER -> listOf(Color(0xFF2196F3), Color(0xFF21CBF3))
            GoalCategory.FINANCIAL -> listOf(Color(0xFF11998E), Color(0xFF38EF7D))
            GoalCategory.PHYSICAL -> listOf(Color(0xFFFF8008), Color(0xFFFFC837))
            GoalCategory.SOCIAL -> listOf(Color(0xFF9C27B0), Color(0xFFE040FB))
            GoalCategory.EMOTIONAL -> listOf(Color(0xFF009688), Color(0xFF4DB6AC))
            GoalCategory.SPIRITUAL -> listOf(Color(0xFFE91E63), Color(0xFFFF6090))
            GoalCategory.FAMILY -> listOf(Color(0xFF3F51B5), Color(0xFF7986CB))
        }
    }

    // ==================== GLASS EFFECT GRADIENTS ====================

    /**
     * Subtle glass overlay gradient
     * Use for: Glass card backgrounds
     */
    val glassOverlay: Brush
        @Composable get() = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    /**
     * Subtle glass overlay gradient
     * Use for: Glass card backgrounds
     */
    val glassOverlayHigh: Brush
        @Composable get() = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.75f)
            )
        )

    /**
     * Border gradient for glass cards
     */
    val glassBorder: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.1f)
            )
        )

    // ==================== ACCENT GRADIENTS ====================

    /**
     * Subtle accent for stat cards
     */
    @Composable
    fun accentBar(color: Color): Brush {
        return Brush.horizontalGradient(
            colors = listOf(
                color,
                color.copy(alpha = 0.6f)
            )
        )
    }

    /**
     * Radial gradient for badge backgrounds
     */
    @Composable
    fun radialGlow(color: Color): Brush {
        return Brush.radialGradient(
            colors = listOf(
                color,
                color.copy(alpha = 0.7f),
                color.copy(alpha = 0.3f)
            )
        )
    }
}

/**
 * Extension function to get gradient brush for GoalCategory
 */
@Composable
fun GoalCategory.gradient(): Brush = LifePlannerGradients.forCategory(this)
