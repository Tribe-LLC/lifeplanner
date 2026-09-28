package az.tribe.lifeplanner.ui.v4.illustration

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.ui.v4.theme.V4

/**
 * The spot illustration for a life area. Decorative: the area's name is always written next to it,
 * so it carries no content description.
 */
@Composable
fun AreaIllustration(area: PlanArea, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val colors = V4.colors
    val palette = IlloPalette(
        blob = colors.area(area).soft,
        ink = colors.ink,
        paper = colors.surface,
        highlight = Color.White,
    )
    val vector = remember(area, palette) { buildAreaIllustration(area, palette) }
    Image(imageVector = vector, contentDescription = null, modifier = modifier.size(size))
}
