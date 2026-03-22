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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.FocusTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─── Theme ring palettes ─────────────────────────────────────────────

private data class RingPalette(
    val ringColors: List<Color>,
    val trackColor: Color,
    val glowColor: Color,
    val textColor: Color,
    val subtitleColor: Color,
    val particleColor: Color,
    val hasParticles: Boolean = false
)

private fun themeRingPalette(theme: FocusTheme, fallbackColors: List<Color>): RingPalette = when (theme) {
    FocusTheme.DEFAULT -> RingPalette(
        ringColors = fallbackColors,
        trackColor = fallbackColors.first().copy(alpha = 0.12f),
        glowColor = fallbackColors.first(),
        textColor = Color.White,
        subtitleColor = Color.White.copy(alpha = 0.5f),
        particleColor = fallbackColors.first()
    )
    FocusTheme.RAIN -> RingPalette(
        ringColors = listOf(Color(0xFF5DADE2), Color(0xFF2980B9), Color(0xFF85C1E9)),
        trackColor = Color(0xFF2980B9).copy(alpha = 0.15f),
        glowColor = Color(0xFF5DADE2),
        textColor = Color(0xFFD6EAF8),
        subtitleColor = Color(0xFF85C1E9).copy(alpha = 0.6f),
        particleColor = Color(0xFFAED6F1),
        hasParticles = true
    )
    FocusTheme.FOREST -> RingPalette(
        ringColors = listOf(Color(0xFF52BE80), Color(0xFF27AE60), Color(0xFF82E0AA)),
        trackColor = Color(0xFF27AE60).copy(alpha = 0.15f),
        glowColor = Color(0xFF52BE80),
        textColor = Color(0xFFD5F5E3),
        subtitleColor = Color(0xFF82E0AA).copy(alpha = 0.6f),
        particleColor = Color(0xFFA9DFBF),
        hasParticles = true
    )
    FocusTheme.FIREPLACE -> RingPalette(
        ringColors = listOf(Color(0xFFE74C3C), Color(0xFFF39C12), Color(0xFFF5B041)),
        trackColor = Color(0xFFE74C3C).copy(alpha = 0.15f),
        glowColor = Color(0xFFF39C12),
        textColor = Color(0xFFFDEDEC),
        subtitleColor = Color(0xFFF5B041).copy(alpha = 0.6f),
        particleColor = Color(0xFFF5B041),
        hasParticles = true
    )
    FocusTheme.OCEAN -> RingPalette(
        ringColors = listOf(Color(0xFF1ABC9C), Color(0xFF16A085), Color(0xFF48C9B0)),
        trackColor = Color(0xFF1ABC9C).copy(alpha = 0.15f),
        glowColor = Color(0xFF1ABC9C),
        textColor = Color(0xFFD1F2EB),
        subtitleColor = Color(0xFF48C9B0).copy(alpha = 0.6f),
        particleColor = Color(0xFF76D7C4),
        hasParticles = true
    )
    FocusTheme.NIGHT_SKY -> RingPalette(
        ringColors = listOf(Color(0xFFAB9DF2), Color(0xFF8E7CC3), Color(0xFFD2B4DE)),
        trackColor = Color(0xFF8E7CC3).copy(alpha = 0.15f),
        glowColor = Color(0xFFAB9DF2),
        textColor = Color(0xFFEBDEF0),
        subtitleColor = Color(0xFFD2B4DE).copy(alpha = 0.6f),
        particleColor = Color.White,
        hasParticles = true
    )
}

// ─── Particles ───────────────────────────────────────────────────────

private data class RingParticle(
    var angle: Float,     // radians, position on the ring
    var radius: Float,    // offset from ring center line
    var size: Float,
    var alpha: Float,
    var speed: Float      // angular speed
)

@Composable
fun FocusProgressRing(
    progress: Float,
    remainingSeconds: Int,
    isRunning: Boolean,
    elapsedSeconds: Int = 0,
    isFreeFlow: Boolean = false,
    gradientColors: List<Color> = listOf(Color(0xFFFF6B35), Color(0xFFFFA726)),
    theme: FocusTheme = FocusTheme.DEFAULT,
    size: Dp = 240.dp,
    strokeWidth: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val palette = remember(theme, gradientColors) { themeRingPalette(theme, gradientColors) }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "progress"
    )

    // Breathing glow effect
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Slow rotation for sweep gradient start angle
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
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

    // Particles that float around the ring
    var particles by remember { mutableStateOf(emptyList<RingParticle>()) }
    if (palette.hasParticles && isRunning) {
        LaunchedEffect(theme) {
            particles = List(12) {
                RingParticle(
                    angle = Random.nextFloat() * 6.28f,
                    radius = Random.nextFloat() * 16f - 8f,
                    size = Random.nextFloat() * 3f + 1.5f,
                    alpha = Random.nextFloat() * 0.5f + 0.2f,
                    speed = (Random.nextFloat() * 0.008f + 0.003f) * if (Random.nextBoolean()) 1 else -1
                )
            }
            while (true) {
                delay(40L)
                particles = particles.map { p ->
                    p.copy(
                        angle = p.angle + p.speed,
                        alpha = (p.alpha + (Random.nextFloat() - 0.5f) * 0.02f).coerceIn(0.1f, 0.7f)
                    )
                }
            }
        }
    }

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
            val ringRadius = (canvasSize.width - strokePx) / 2f
            val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

            // Background track
            drawArc(
                color = palette.trackColor,
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
                            colors = palette.ringColors.map { it.copy(alpha = glowAlpha) }
                        ),
                        startAngle = -90f + rotationAngle,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(topLeft.x - 4.dp.toPx(), topLeft.y - 4.dp.toPx()),
                        size = Size(arcSize.width + 8.dp.toPx(), arcSize.height + 8.dp.toPx()),
                        style = Stroke(width = strokePx + 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            } else {
                // Glow behind progress arc
                if (isRunning && animatedProgress > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = palette.ringColors.map { it.copy(alpha = glowAlpha) }
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = Offset(topLeft.x - 4.dp.toPx(), topLeft.y - 4.dp.toPx()),
                        size = Size(arcSize.width + 8.dp.toPx(), arcSize.height + 8.dp.toPx()),
                        style = Stroke(width = strokePx + 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Progress arc
                if (animatedProgress > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = palette.ringColors + palette.ringColors.first()
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

            // Theme particles floating along the ring
            if (palette.hasParticles && isRunning) {
                particles.forEach { p ->
                    val px = center.x + (ringRadius + p.radius) * cos(p.angle)
                    val py = center.y + (ringRadius + p.radius) * sin(p.angle)
                    drawCircle(
                        color = palette.particleColor.copy(alpha = p.alpha),
                        radius = p.size,
                        center = Offset(px, py)
                    )
                }
            }
        }

        // Time display centered
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeText,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                color = palette.textColor,
                letterSpacing = 2.sp
            )
            if (isRunning) {
                Text(
                    text = if (isFreeFlow) "elapsed" else "remaining",
                    fontSize = 12.sp,
                    color = palette.subtitleColor
                )
            }
        }
    }
}
