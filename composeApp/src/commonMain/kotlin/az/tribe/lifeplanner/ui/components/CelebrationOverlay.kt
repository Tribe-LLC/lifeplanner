package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class CelebrationType {
    GOAL_COMPLETED,
    BADGE_UNLOCKED,
    STREAK_MILESTONE,
    LEVEL_UP
}

private data class Particle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var size: Float,
    var color: Color,
    var rotation: Float,
    var rotationSpeed: Float,
    var alpha: Float = 1f
)

@Composable
fun CelebrationOverlay(
    type: CelebrationType,
    isVisible: Boolean,
    message: String = "",
    onDismiss: () -> Unit
) {
    // Play celebration sound when overlay becomes visible
    val soundPlayer = rememberCelebrationSoundPlayer()
    LaunchedEffect(isVisible) {
        if (isVisible) {
            soundPlayer.play(type)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Confetti particles on top layer
            ConfettiCanvas(type = type)

            // Bottom card that slides up
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                )
            ) {
                val (emoji, gradientColors, subtitle) = remember(type) {
                    when (type) {
                        CelebrationType.GOAL_COMPLETED -> Triple(
                            "\uD83C\uDF89",
                            listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                            "You crushed it!"
                        )
                        CelebrationType.BADGE_UNLOCKED -> Triple(
                            "\uD83C\uDFC6",
                            listOf(Color(0xFFFF8F00), Color(0xFFFFD700)),
                            "New achievement unlocked!"
                        )
                        CelebrationType.STREAK_MILESTONE -> Triple(
                            "\uD83D\uDD25",
                            listOf(Color(0xFFFF6B35), Color(0xFFFF4500)),
                            "Keep the fire burning!"
                        )
                        CelebrationType.LEVEL_UP -> Triple(
                            "\u2B50",
                            listOf(Color(0xFF667EEA), Color(0xFFC780FA)),
                            "You're getting stronger!"
                        )
                    }
                }

                val displayMessage = message.ifEmpty {
                    when (type) {
                        CelebrationType.GOAL_COMPLETED -> "Goal Complete!"
                        CelebrationType.BADGE_UNLOCKED -> "Badge Unlocked!"
                        CelebrationType.STREAK_MILESTONE -> "Streak Milestone!"
                        CelebrationType.LEVEL_UP -> "Level Up!"
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Gradient accent bar at top
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Brush.horizontalGradient(gradientColors))
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Emoji with gradient circle background
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(gradientColors.map { it.copy(alpha = 0.15f) })),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 32.sp
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(
                                text = displayMessage,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(16.dp))

                            // Tap to dismiss hint
                            Text(
                                text = "Tap to dismiss",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // Auto-dismiss after delay
        LaunchedEffect(Unit) {
            delay(3000)
            onDismiss()
        }
    }
}

@Composable
private fun ConfettiCanvas(type: CelebrationType) {
    val colors = when (type) {
        CelebrationType.GOAL_COMPLETED -> listOf(
            Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77),
            Color(0xFF4D96FF), Color(0xFFFF6B9D), Color(0xFFC780FA)
        )
        CelebrationType.BADGE_UNLOCKED -> listOf(
            Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFFEB3B),
            Color(0xFFFF8F00), Color(0xFFFFC107)
        )
        CelebrationType.STREAK_MILESTONE -> listOf(
            Color(0xFFFF6B35), Color(0xFFFF4500), Color(0xFFFF8C00),
            Color(0xFFFFD700), Color(0xFFFF6347)
        )
        CelebrationType.LEVEL_UP -> listOf(
            Color(0xFF667EEA), Color(0xFF764BA2), Color(0xFFF093FB),
            Color(0xFFFFD700), Color(0xFF4D96FF), Color(0xFFC780FA)
        )
    }

    val particleCount = when (type) {
        CelebrationType.GOAL_COMPLETED -> 60
        CelebrationType.BADGE_UNLOCKED -> 40
        CelebrationType.STREAK_MILESTONE -> 50
        CelebrationType.LEVEL_UP -> 70
    }

    var particles by remember { mutableStateOf<List<Particle>>(emptyList()) }
    var animationProgress by remember { mutableStateOf(0f) }

    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Initialize particles — burst upward from bottom center
        particles = List(particleCount) {
            val angle = Random.nextFloat() * 180f // spread upward (0-180 degrees)
            val speed = Random.nextFloat() * 8f + 3f
            Particle(
                x = 0.4f + Random.nextFloat() * 0.2f, // near center horizontally
                y = 0.85f + Random.nextFloat() * 0.1f, // near bottom
                velocityX = cos(angle.toDouble() * kotlin.math.PI / 180.0).toFloat() * speed * 0.004f,
                velocityY = -sin(angle.toDouble() * kotlin.math.PI / 180.0).toFloat() * speed * 0.006f,
                size = Random.nextFloat() * 8f + 4f,
                color = colors.random(),
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 10f - 5f
            )
        }

        progress.animateTo(
            1f,
            animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
        )
    }

    // Re-trigger particle updates ~60fps
    LaunchedEffect(Unit) {
        while (progress.value < 1f) {
            delay(16)
            particles = particles.map { p ->
                p.copy(
                    x = p.x + p.velocityX,
                    y = p.y + p.velocityY + 0.0005f, // gentle gravity
                    rotation = p.rotation + p.rotationSpeed,
                    alpha = (1f - progress.value * 0.7f).coerceIn(0f, 1f)
                )
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        particles.forEach { particle ->
            val px = particle.x * canvasWidth
            val py = particle.y * canvasHeight

            if (px in -50f..canvasWidth + 50f && py in -50f..canvasHeight + 50f) {
                rotate(
                    degrees = particle.rotation,
                    pivot = Offset(px, py)
                ) {
                    drawRect(
                        color = particle.color.copy(alpha = particle.alpha),
                        topLeft = Offset(px - particle.size / 2, py - particle.size / 2),
                        size = Size(particle.size, particle.size * 0.6f)
                    )
                }
            }
        }
    }
}
