package az.tribe.lifeplanner.ui.v4.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.ui.theme.AppFontFamily
import az.tribe.lifeplanner.ui.theme.LocalIsDarkTheme

/**
 * v4's design tokens, taken from the approved canvas ("LifePlanner 4 redesign"). A toned warm
 * neutral ground, one accent (LifePlanner blue), and one colour per life area used only for that
 * area's tints, chips and illustrations.
 */
@Immutable
data class V4Colors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val line: Color,
    val lineSoft: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val accent: Color,
    val onAccent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val success: Color,
    val successSoft: Color,
    val trackOff: Color,
    val scrim: Color,
    val inverse: Color,
    val onInverse: Color,
    private val areas: Map<PlanArea, AreaColors>,
) {
    fun area(area: PlanArea): AreaColors = areas.getValue(area)
}

/** [color] is the strong fill, [ink] is text on [soft], [soft] is the area's tint. */
@Immutable
data class AreaColors(val color: Color, val ink: Color, val soft: Color)

private val LightAreas = mapOf(
    PlanArea.HABITS to AreaColors(Color(0xFF3B5BE5), Color(0xFF2C42B0), Color(0xFFE6EBFF)),
    PlanArea.FITNESS to AreaColors(Color(0xFFEA580C), Color(0xFF9A3412), Color(0xFFFFE9DC)),
    PlanArea.MONEY to AreaColors(Color(0xFF16A34A), Color(0xFF166534), Color(0xFFDDF3E4)),
    PlanArea.TRAVEL to AreaColors(Color(0xFF0891B2), Color(0xFF155E75), Color(0xFFD9F1F5)),
    PlanArea.STUDY to AreaColors(Color(0xFF7C3AED), Color(0xFF5B21B6), Color(0xFFEEE7FD)),
    PlanArea.MEALS to AreaColors(Color(0xFFF59E0B), Color(0xFF92400E), Color(0xFFFDF0D5)),
    PlanArea.MIND to AreaColors(Color(0xFFC026D3), Color(0xFF86198F), Color(0xFFF9E5FB)),
    PlanArea.CAREER to AreaColors(Color(0xFF475569), Color(0xFF334155), Color(0xFFE8EBF0)),
)

private val DarkAreas = mapOf(
    PlanArea.HABITS to AreaColors(Color(0xFF6D86F5), Color(0xFFB4C1FA), Color(0xFF1F2744)),
    PlanArea.FITNESS to AreaColors(Color(0xFFF97316), Color(0xFFFDBA8C), Color(0xFF3A2216)),
    PlanArea.MONEY to AreaColors(Color(0xFF22C55E), Color(0xFF86EFAC), Color(0xFF142C1D)),
    PlanArea.TRAVEL to AreaColors(Color(0xFF22B8D9), Color(0xFF8ADCEC), Color(0xFF0F2A31)),
    PlanArea.STUDY to AreaColors(Color(0xFF9F67FA), Color(0xFFCDBEFD), Color(0xFF261A40)),
    PlanArea.MEALS to AreaColors(Color(0xFFF59E0B), Color(0xFFFCD34D), Color(0xFF33260C)),
    PlanArea.MIND to AreaColors(Color(0xFFD946EF), Color(0xFFF0ABFC), Color(0xFF33163A)),
    PlanArea.CAREER to AreaColors(Color(0xFF8B9BB4), Color(0xFFCBD5E1), Color(0xFF232A33)),
)

val V4LightColors = V4Colors(
    isDark = false,
    background = Color(0xFFF6F5F1),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEFEEE9),
    line = Color(0xFFE4E2DB),
    lineSoft = Color(0xFFEFEEE9),
    ink = Color(0xFF15171C),
    ink2 = Color(0xFF45484F),
    ink3 = Color(0xFF5A5D66),
    accent = Color(0xFF3B5BE5),
    onAccent = Color(0xFFFFFFFF),
    accentInk = Color(0xFF2C42B0),
    accentSoft = Color(0xFFE6EBFF),
    success = Color(0xFF166534),
    successSoft = Color(0xFFDDF3E4),
    trackOff = Color(0xFFCFCCC3),
    scrim = Color(0x7315171C),
    inverse = Color(0xFF15171C),
    onInverse = Color(0xFFFFFFFF),
    areas = LightAreas,
)

val V4DarkColors = V4Colors(
    isDark = true,
    background = Color(0xFF111317),
    surface = Color(0xFF1A1D22),
    surfaceMuted = Color(0xFF23262C),
    line = Color(0xFF2B2F36),
    lineSoft = Color(0xFF23262C),
    ink = Color(0xFFF2F1EC),
    ink2 = Color(0xFFBDBFC6),
    ink3 = Color(0xFF9EA1A9),
    accent = Color(0xFF6D86F5),
    onAccent = Color(0xFF0D1330),
    accentInk = Color(0xFFB4C1FA),
    accentSoft = Color(0xFF1F2744),
    success = Color(0xFF86EFAC),
    successSoft = Color(0xFF142C1D),
    trackOff = Color(0xFF3A3E46),
    scrim = Color(0x99000000),
    inverse = Color(0xFFF2F1EC),
    onInverse = Color(0xFF15171C),
    areas = DarkAreas,
)

/** Text styles. Satoshi throughout: it is already the app's brand face and ships in the bundle. */
@Immutable
data class V4Type(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val micro: TextStyle,
    val number: TextStyle,
)

@Composable
private fun v4Type(family: FontFamily) = V4Type(
    display = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.02).em),
    title = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em),
    headline = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyStrong = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    label = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp),
    caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    micro = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp),
    number = TextStyle(fontFamily = family, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em),
)

val LocalV4Colors = staticCompositionLocalOf { V4LightColors }
val LocalV4Type = staticCompositionLocalOf<V4Type> { error("V4Theme not provided") }

object V4 {
    val colors: V4Colors
        @Composable @ReadOnlyComposable get() = LocalV4Colors.current
    val type: V4Type
        @Composable @ReadOnlyComposable get() = LocalV4Type.current
}

/**
 * Wraps v4 screens. It follows the app's own Light / Dark / System preference (through
 * [LocalIsDarkTheme], which the root theme provides) rather than the system setting, and maps the
 * tokens onto Material's colour scheme so stock components (switches, text fields, sheets) match.
 */
@Composable
fun V4Theme(content: @Composable () -> Unit) {
    val dark = LocalIsDarkTheme.current
    val colors = if (dark) V4DarkColors else V4LightColors
    val type = v4Type(AppFontFamily())
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft, onPrimaryContainer = colors.accentInk,
            background = colors.background, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceMuted, onSurfaceVariant = colors.ink2,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceMuted,
            outline = colors.line, outlineVariant = colors.lineSoft,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft, onPrimaryContainer = colors.accentInk,
            background = colors.background, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceMuted, onSurfaceVariant = colors.ink2,
            surfaceContainerLow = colors.surface, surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceMuted,
            outline = colors.line, outlineVariant = colors.lineSoft,
        )
    }
    CompositionLocalProvider(LocalV4Colors provides colors, LocalV4Type provides type) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            content()
        }
    }
}
