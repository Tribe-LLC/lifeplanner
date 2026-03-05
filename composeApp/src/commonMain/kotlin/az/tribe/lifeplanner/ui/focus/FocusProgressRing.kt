package az.tribe.lifeplanner.ui.focus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FocusProgressRing(
    progress: Float,
    remainingSeconds: Int,
    isRunning: Boolean,
    elapsedSeconds: Int = 0,
    isFreeFlow: Boolean = false,
    gradientColors: List<Color> = listOf(Color(0xFFFF6B35), Color(0xFFFFA726)),
    size: Dp = 240.dp,
    strokeWidth: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress"
    )

    // Breathing glow effect
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Completion scale spring
    val scale by animateFloatAsState(
        targetValue = if (progress >= 1f) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val displaySeconds = if (isFreeFlow) elapsedSeconds else remainingSeconds
    val minutes = displaySeconds / 60
    val seconds = displaySeconds % 60
    val timeText = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size * scale)
    ) {
        Canvas(modifier = Modifier.size(size * scale)) {
            val canvasSize = this.size
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(
                canvasSize.width - strokePx,
                canvasSize.height - strokePx
            )
            val topLeft = Offset(strokePx / 2, strokePx / 2)

            // Background track
            drawArc(
                color = gradientColors.first().copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (isFreeFlow) {
                // Full-ring pulsing glow for free flow
                if (isRunning) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = gradientColors.map { it.copy(alpha = glowAlpha) }
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(topLeft.x - 4.dp.toPx(), topLeft.y - 4.dp.toPx()),
                        size = Size(arcSize.width + 8.dp.toPx(), arcSize.height + 8.dp.toPx()),
                        style = Stroke(width = strokePx + 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            } else {
                // Glow effect when running (timed mode)
                if (isRunning && animatedProgress > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = gradientColors.map { it.copy(alpha = glowAlpha) }
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = Offset(topLeft.x - 4.dp.toPx(), topLeft.y - 4.dp.toPx()),
                        size = Size(arcSize.width + 8.dp.toPx(), arcSize.height + 8.dp.toPx()),
                        style = Stroke(width = strokePx + 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Progress arc (timed mode only)
                if (animatedProgress > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = gradientColors + gradientColors.first()
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Time display centered
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isRunning) {
                Text(
                    text = if (isFreeFlow) "elapsed" else "remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
