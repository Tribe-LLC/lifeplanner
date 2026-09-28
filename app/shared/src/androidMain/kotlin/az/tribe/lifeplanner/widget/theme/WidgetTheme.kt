package az.tribe.lifeplanner.widget.theme

import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// v4 tokens (ui/v4/theme/V4Theme.kt), so the widget looks like the app.
private val WidgetLightColors = lightColorScheme(
    primary = Color(0xFF3B5BE5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EBFF),
    onPrimaryContainer = Color(0xFF1E2F85),
    secondary = Color(0xFF3B5BE5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EBFF),
    onSecondaryContainer = Color(0xFF1E2F85),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15171C),
    surfaceVariant = Color(0xFFF6F5F1),
    onSurfaceVariant = Color(0xFF45484F),
    background = Color(0xFFF6F5F1),
    onBackground = Color(0xFF15171C),
    outline = Color(0xFFE4E2DB)
)

private val WidgetDarkColors = darkColorScheme(
    primary = Color(0xFF6D86F5),
    onPrimary = Color(0xFF0D1330),
    primaryContainer = Color(0xFF1F2744),
    onPrimaryContainer = Color(0xFFE6EBFF),
    secondary = Color(0xFF6D86F5),
    onSecondary = Color(0xFF0D1330),
    secondaryContainer = Color(0xFF1F2744),
    onSecondaryContainer = Color(0xFFE6EBFF),
    surface = Color(0xFF1A1D22),
    onSurface = Color(0xFFF2F1EC),
    surfaceVariant = Color(0xFF111317),
    onSurfaceVariant = Color(0xFFBDBFC6),
    background = Color(0xFF111317),
    onBackground = Color(0xFFF2F1EC),
    outline = Color(0xFF2B2F36)
)

val WidgetColorProviders = ColorProviders(
    light = WidgetLightColors,
    dark = WidgetDarkColors
)

val StreakFireColor = ColorProvider(Color(0xFFF59E0B))

val SuccessColor = ColorProvider(Color(0xFF10B981))

val XpBarBackground = ColorProvider(Color(0xFFE5E7EB))

val XpBarFill = ColorProvider(Color(0xFF6366F1))
