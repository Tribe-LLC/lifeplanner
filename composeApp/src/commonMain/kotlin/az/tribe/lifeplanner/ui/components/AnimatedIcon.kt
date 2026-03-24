package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An icon that plays a bounce + rotate animation when [animate] becomes true.
 * Useful for bottom nav selected state, sync status changes, etc.
 */
@Composable
fun AnimatedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    effect: IconAnimationEffect = IconAnimationEffect.BOUNCE
) {
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(animate, imageVector) {
        if (animate) {
            when (effect) {
                IconAnimationEffect.BOUNCE -> {
                    scale.snapTo(0.6f)
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
                IconAnimationEffect.BOUNCE_ROTATE -> {
                    scale.snapTo(0.6f)
                    rotation.snapTo(-15f)
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    rotation.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                IconAnimationEffect.POP -> {
                    scale.snapTo(0f)
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                IconAnimationEffect.PULSE -> {
                    scale.animateTo(1.3f, animationSpec = tween(150))
                    scale.animateTo(1f, animationSpec = tween(150))
                }
            }
        } else {
            scale.snapTo(1f)
            rotation.snapTo(0f)
        }
    }

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation.value
            },
        tint = tint
    )
}

enum class IconAnimationEffect {
    BOUNCE,
    BOUNCE_ROTATE,
    POP,
    PULSE
}
