package az.tribe.lifeplanner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import leanlifeplanner.composeapp.generated.resources.*
import leanlifeplanner.composeapp.generated.resources.Res

@Composable
fun AppFontFamily(): FontFamily = FontFamily(
    Font(Res.font.Satoshi_Regular, weight = FontWeight.Normal, style = FontStyle.Normal),
    Font(Res.font.Satoshi_Medium, weight = FontWeight.Medium, style = FontStyle.Normal),
    Font(Res.font.Satoshi_Bold, weight = FontWeight.Bold, style = FontStyle.Normal),
    Font(Res.font.Satoshi_Black, weight = FontWeight.Black, style = FontStyle.Normal)
)

@Composable
fun LifePlannerTypography(): Typography {
    val fontFamily = AppFontFamily()
    return Typography().run {
        val colors = LocalModernColors.current
        copy(
            displayLarge = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
                color = colors.textPrimary
            ),
            displayMedium = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
                color = colors.textPrimary
            ),
            displaySmall = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
                color = colors.textPrimary
            ),

            // Headline styles
            headlineLarge = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
                color = colors.textPrimary
            ),
            headlineMedium = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
                color = colors.textPrimary
            ),
            headlineSmall = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
                color = colors.textPrimary
            ),

            // Title styles
            titleLarge = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
                color = colors.textPrimary
            ),
            titleMedium = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
                color = colors.textPrimary
            ),
            titleSmall = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
                color = colors.textPrimary
            ),

            // Body styles
            bodyLarge = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
                color = colors.textPrimary
            ),
            bodyMedium = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
                color = colors.textSecondary
            ),
            bodySmall = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
                color = colors.textTertiary
            ),

            // Label styles
            labelLarge = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
                color = colors.textPrimary
            ),
            labelMedium = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
                color = colors.textSecondary
            ),
            labelSmall = TextStyle(
                fontFamily=fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
                color = colors.textTertiary
            )
        )
    }
}

