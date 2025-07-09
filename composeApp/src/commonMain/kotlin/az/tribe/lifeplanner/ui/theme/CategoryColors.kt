package az.tribe.lifeplanner.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import az.tribe.lifeplanner.domain.enum.GoalCategory

// Modern category colors and gradients
object CategoryColors {
    // Vibrant base colors
    val CAREER = Color(0xFF4A6FFF)
    val FINANCIAL = Color(0xFF28C76F) 
    val PHYSICAL = Color(0xFFFF9F43)
    val SOCIAL = Color(0xFF7A5AF8)
    val EMOTIONAL = Color(0xFF00CFE8)
    val SPIRITUAL = Color(0xFFEA5455)
    val FAMILY = Color(0xFF6236FF)
    val LEARNING = Color(0xFF28C3D7)
    val OTHER = Color(0xFF9E9FA3)
    
    // Secondary/gradient colors
    val CAREER_GRADIENT = listOf(Color(0xFF4A6FFF), Color(0xFF2C42B0))
    val FINANCIAL_GRADIENT = listOf(Color(0xFF28C76F), Color(0xFF1AAC59))
    val PHYSICAL_GRADIENT = listOf(Color(0xFFFF9F43), Color(0xFFE88B2E))
    val SOCIAL_GRADIENT = listOf(Color(0xFF7A5AF8), Color(0xFF6346E0))
    val EMOTIONAL_GRADIENT = listOf(Color(0xFF00CFE8), Color(0xFF00A1B5))
    val SPIRITUAL_GRADIENT = listOf(Color(0xFFEA5455), Color(0xFFD03A3B))
    val FAMILY_GRADIENT = listOf(Color(0xFF6236FF), Color(0xFF4A2BC8))
    val LEARNING_GRADIENT = listOf(Color(0xFF28C3D7), Color(0xFF1A9AAB))
    val OTHER_GRADIENT = listOf(Color(0xFF9E9FA3), Color(0xFF75767A))
    
    // Container/light backgrounds for cards
    val CAREER_CONTAINER = Color(0xFFECF0FF)
    val FINANCIAL_CONTAINER = Color(0xFFE0F7EA)
    val PHYSICAL_CONTAINER = Color(0xFFFFF4E6)
    val SOCIAL_CONTAINER = Color(0xFFF1ECFF) 
    val EMOTIONAL_CONTAINER = Color(0xFFE0F9FC)
    val SPIRITUAL_CONTAINER = Color(0xFFFFEDED)
    val FAMILY_CONTAINER = Color(0xFFEDE6FF)
    val LEARNING_CONTAINER = Color(0xFFDFF7FB)
    val OTHER_CONTAINER = Color(0xFFF0F0F0)
}

// Extension function to get category gradient
fun GoalCategory.gradientColors(): List<Color> {
    return when (this) {
        GoalCategory.CAREER -> CategoryColors.CAREER_GRADIENT
        GoalCategory.FINANCIAL -> CategoryColors.FINANCIAL_GRADIENT
        GoalCategory.PHYSICAL -> CategoryColors.PHYSICAL_GRADIENT
        GoalCategory.SOCIAL -> CategoryColors.SOCIAL_GRADIENT
        GoalCategory.EMOTIONAL -> CategoryColors.EMOTIONAL_GRADIENT
        GoalCategory.SPIRITUAL -> CategoryColors.SPIRITUAL_GRADIENT
        GoalCategory.FAMILY -> CategoryColors.FAMILY_GRADIENT
        else -> CategoryColors.OTHER_GRADIENT
    }
}

// Extension function to get category container color
fun GoalCategory.containerColor(): Color {
    return when (this) {
        GoalCategory.CAREER -> CategoryColors.CAREER_CONTAINER
        GoalCategory.FINANCIAL -> CategoryColors.FINANCIAL_CONTAINER
        GoalCategory.PHYSICAL -> CategoryColors.PHYSICAL_CONTAINER
        GoalCategory.SOCIAL -> CategoryColors.SOCIAL_CONTAINER
        GoalCategory.EMOTIONAL -> CategoryColors.EMOTIONAL_CONTAINER
        GoalCategory.SPIRITUAL -> CategoryColors.SPIRITUAL_CONTAINER
        GoalCategory.FAMILY -> CategoryColors.FAMILY_CONTAINER
        else -> CategoryColors.OTHER_CONTAINER
    }
}

// Extension function to get main category color
fun GoalCategory.backgroundColor(): Color {
    return when (this) {
        GoalCategory.CAREER -> CategoryColors.CAREER
        GoalCategory.FINANCIAL -> CategoryColors.FINANCIAL
        GoalCategory.PHYSICAL -> CategoryColors.PHYSICAL
        GoalCategory.SOCIAL -> CategoryColors.SOCIAL
        GoalCategory.EMOTIONAL -> CategoryColors.EMOTIONAL
        GoalCategory.SPIRITUAL -> CategoryColors.SPIRITUAL
        GoalCategory.FAMILY -> CategoryColors.FAMILY
        else -> CategoryColors.OTHER
    }
}

// Create a horizontal gradient brush for the category
@Composable
fun GoalCategory.horizontalGradient(): Brush {
    return Brush.horizontalGradient(
        colors = this.gradientColors()
    )
}

// Create a vertical gradient brush for the category
@Composable
fun GoalCategory.verticalGradient(): Brush {
    return Brush.verticalGradient(
        colors = this.gradientColors()
    )
}

// Get a category by its color (useful for analytics)
fun getCategoryByColor(color: Int): GoalCategory {
    return when (color) {
        CategoryColors.CAREER.toArgb() -> GoalCategory.CAREER
        CategoryColors.FINANCIAL.toArgb() -> GoalCategory.FINANCIAL
        CategoryColors.PHYSICAL.toArgb() -> GoalCategory.PHYSICAL
        CategoryColors.SOCIAL.toArgb() -> GoalCategory.SOCIAL
        CategoryColors.EMOTIONAL.toArgb() -> GoalCategory.EMOTIONAL
        CategoryColors.SPIRITUAL.toArgb() -> GoalCategory.SPIRITUAL
        CategoryColors.FAMILY.toArgb() -> GoalCategory.FAMILY
        else -> GoalCategory.CAREER
    }
}