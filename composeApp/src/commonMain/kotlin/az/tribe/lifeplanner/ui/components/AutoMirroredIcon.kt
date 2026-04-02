package az.tribe.lifeplanner.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun AutoMirroredIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val layoutDirection = LocalLayoutDirection.current
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.then(
            if (layoutDirection == LayoutDirection.Rtl) Modifier.graphicsLayer { scaleX = -1f }
            else Modifier
        ),
        tint = tint
    )
}
