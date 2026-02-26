package az.tribe.lifeplanner.widget.theme

import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val WidgetLightColors = lightColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF818CF8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF312E81),
    surface = Color.White,
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF6B7280),
    background = Color.White,
    onBackground = Color(0xFF1F2937),
    outline = Color(0xFFE5E7EB)
)

private val WidgetDarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFA5B4FC),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF3730A3),
    onSecondaryContainer = Color(0xFFEEF2FF),
    surface = Color(0xFF1F2937),
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFF9CA3AF),
    background = Color(0xFF111827),
    onBackground = Color(0xFFF9FAFB),
    outline = Color(0xFF4B5563)
)

val WidgetColorProviders = ColorProviders(
    light = WidgetLightColors,
    dark = WidgetDarkColors
)

val StreakFireColor = ColorProvider(Color(0xFFF59E0B))

val SuccessColor = ColorProvider(Color(0xFF10B981))

val XpBarBackground = ColorProvider(Color(0xFFE5E7EB))

val XpBarFill = ColorProvider(Color(0xFF6366F1))
