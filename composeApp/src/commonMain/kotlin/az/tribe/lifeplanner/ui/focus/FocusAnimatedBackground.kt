package az.tribe.lifeplanner.ui.focus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import az.tribe.lifeplanner.domain.enum.FocusTheme
import az.tribe.lifeplanner.domain.enum.Mood
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun FocusAnimatedBackground(
    theme: FocusTheme,
    mood: Mood?,
    modifier: Modifier = Modifier
) {
    when (theme) {
        FocusTheme.DEFAULT -> DefaultGradientBackground(mood, modifier)
        FocusTheme.RAIN -> RainBackground(mood, modifier)
        FocusTheme.FOREST -> ForestBackground(mood, modifier)
        FocusTheme.FIREPLACE -> FireplaceBackground(mood, modifier)
        FocusTheme.OCEAN -> OceanBackground(mood, modifier)
        FocusTheme.NIGHT_SKY -> NightSkyBackground(mood, modifier)
    }
}

// Mood adjusts gradient warmth/brightness
private fun moodColorShift(mood: Mood?): Float = when (mood) {
    Mood.VERY_HAPPY -> 0.15f
    Mood.HAPPY -> 0.08f
    Mood.NEUTRAL, null -> 0f
    Mood.SAD -> -0.08f
    Mood.VERY_SAD -> -0.15f
}

private fun Color.warmShift(amount: Float): Color {
    return Color(
        red = (red + amount).coerceIn(0f, 1f),
        green = green,
        blue = (blue - amount * 0.5f).coerceIn(0f, 1f),
        alpha = alpha
    )
}

// ============================================
// DEFAULT — Mood-based animated gradient
// ============================================

@Composable
private fun DefaultGradientBackground(mood: Mood?, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "defaultBg")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientShift"
    )
    val moodShift = moodColorShift(mood)

    Canvas(modifier = modifier.fillMaxSize()) {
        val topColor = Color(0xFF1A1A2E).warmShift(moodShift)
        val midColor = Color(0xFF16213E).warmShift(moodShift * 0.5f)
        val bottomColor = Color(0xFF0F3460).warmShift(moodShift * 0.3f)

        val adjustedMid = Color(
            red = midColor.red + shift * 0.05f,
            green = midColor.green,
            blue = midColor.blue + (1f - shift) * 0.05f,
            alpha = 1f
        )

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(topColor, adjustedMid, bottomColor)
            )
        )
    }
}

// ============================================
// RAIN — Blue-gray gradient + falling droplets
// ============================================

private data class RainDrop(
    var x: Float,
    var y: Float,
    val speed: Float,
    val length: Float,
    val alpha: Float
)

@Composable
private fun RainBackground(mood: Mood?, modifier: Modifier) {
    val moodShift = moodColorShift(mood)
    var drops by remember { mutableStateOf(emptyList<RainDrop>()) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            if (!initialized) {
                drops = List(80) {
                    RainDrop(
                        x = Random.nextFloat(),
                        y = Random.nextFloat(),
                        speed = Random.nextFloat() * 0.015f + 0.008f,
                        length = Random.nextFloat() * 0.04f + 0.02f,
                        alpha = Random.nextFloat() * 0.4f + 0.1f
                    )
                }
                initialized = true
            }
            delay(32L)
            drops = drops.map { drop ->
                val newY = drop.y + drop.speed
                if (newY > 1.1f) {
                    drop.copy(y = -0.1f, x = Random.nextFloat())
                } else {
                    drop.copy(y = newY)
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val topColor = Color(0xFF2C3E50).warmShift(moodShift)
        val bottomColor = Color(0xFF3498DB).warmShift(moodShift * 0.5f)

        drawRect(
            brush = Brush.verticalGradient(listOf(topColor, bottomColor))
        )

        val dropColor = Color.White
        drops.forEach { drop ->
            drawLine(
                color = dropColor.copy(alpha = drop.alpha),
                start = Offset(drop.x * size.width, drop.y * size.height),
                end = Offset(
                    drop.x * size.width - 1f,
                    (drop.y + drop.length) * size.height
                ),
                strokeWidth = 1.5f
            )
        }
    }
}

// ============================================
// FOREST — Green gradient + floating leaf particles
// ============================================

private data class Leaf(
    var x: Float,
    var y: Float,
    val speed: Float,
    val drift: Float,
    val size: Float,
    val alpha: Float,
    var phase: Float
)

@Composable
private fun ForestBackground(mood: Mood?, modifier: Modifier) {
    val moodShift = moodColorShift(mood)
    var leaves by remember { mutableStateOf(emptyList<Leaf>()) }
    var initialized by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            if (!initialized) {
                leaves = List(25) {
                    Leaf(
                        x = Random.nextFloat(),
                        y = Random.nextFloat(),
                        speed = Random.nextFloat() * 0.003f + 0.001f,
                        drift = Random.nextFloat() * 0.002f - 0.001f,
                        size = Random.nextFloat() * 6f + 3f,
                        alpha = Random.nextFloat() * 0.4f + 0.15f,
                        phase = Random.nextFloat() * 6.28f
                    )
                }
                initialized = true
            }
            delay(50L)
            frame += 0.05f
            leaves = leaves.map { leaf ->
                val newY = leaf.y + leaf.speed
                val newX = leaf.x + leaf.drift + sin((frame + leaf.phase).toDouble()).toFloat() * 0.001f
                if (newY > 1.1f) {
                    leaf.copy(y = -0.05f, x = Random.nextFloat())
                } else {
                    leaf.copy(y = newY, x = newX.coerceIn(0f, 1f))
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val topColor = Color(0xFF1B4332).warmShift(moodShift)
        val midColor = Color(0xFF2D6A4F).warmShift(moodShift * 0.5f)
        val bottomColor = Color(0xFF40916C).warmShift(moodShift * 0.3f)

        drawRect(
            brush = Brush.verticalGradient(listOf(topColor, midColor, bottomColor))
        )

        // Light rays
        val rayAlpha = 0.04f + sin(frame.toDouble()).toFloat() * 0.02f
        drawLine(
            color = Color(0xFFD4E09B).copy(alpha = rayAlpha),
            start = Offset(size.width * 0.3f, 0f),
            end = Offset(size.width * 0.5f, size.height),
            strokeWidth = size.width * 0.15f
        )

        // Leaves
        val leafColor = Color(0xFF95D5B2)
        leaves.forEach { leaf ->
            drawCircle(
                color = leafColor.copy(alpha = leaf.alpha),
                radius = leaf.size,
                center = Offset(leaf.x * size.width, leaf.y * size.height)
            )
        }
    }
}

// ============================================
// FIREPLACE — Warm gradient + rising ember particles
// ============================================

private data class Ember(
    var x: Float,
    var y: Float,
    val speed: Float,
    val drift: Float,
    val size: Float,
    val alpha: Float,
    var phase: Float
)

@Composable
private fun FireplaceBackground(mood: Mood?, modifier: Modifier) {
    val moodShift = moodColorShift(mood)
    var embers by remember { mutableStateOf(emptyList<Ember>()) }
    var initialized by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf(0f) }

    val transition = rememberInfiniteTransition(label = "fireBg")
    val flicker by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker"
    )

    LaunchedEffect(Unit) {
        while (true) {
            if (!initialized) {
                embers = List(30) {
                    Ember(
                        x = Random.nextFloat() * 0.6f + 0.2f,
                        y = Random.nextFloat(),
                        speed = Random.nextFloat() * 0.005f + 0.002f,
                        drift = Random.nextFloat() * 0.003f - 0.0015f,
                        size = Random.nextFloat() * 4f + 1.5f,
                        alpha = Random.nextFloat() * 0.6f + 0.2f,
                        phase = Random.nextFloat() * 6.28f
                    )
                }
                initialized = true
            }
            delay(40L)
            frame += 0.08f
            embers = embers.map { ember ->
                val newY = ember.y - ember.speed
                val newX = ember.x + ember.drift + sin((frame + ember.phase).toDouble()).toFloat() * 0.002f
                if (newY < -0.05f) {
                    ember.copy(
                        y = 1.05f,
                        x = Random.nextFloat() * 0.6f + 0.2f,
                        alpha = Random.nextFloat() * 0.6f + 0.2f
                    )
                } else {
                    ember.copy(y = newY, x = newX.coerceIn(0f, 1f))
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val flickerAmount = flicker * 0.03f
        val topColor = Color(0xFF1A0A00).warmShift(moodShift + flickerAmount)
        val midColor = Color(0xFF4A1500).warmShift(moodShift * 0.5f + flickerAmount)
        val bottomColor = Color(0xFFE25822).warmShift(moodShift * 0.3f)

        drawRect(
            brush = Brush.verticalGradient(listOf(topColor, midColor, bottomColor))
        )

        // Embers
        embers.forEach { ember ->
            val emberColor = Color(
                red = 1f,
                green = 0.5f + Random.nextFloat() * 0.3f,
                blue = 0.1f,
                alpha = ember.alpha * (0.7f + flicker * 0.3f)
            )
            drawCircle(
                color = emberColor,
                radius = ember.size,
                center = Offset(ember.x * size.width, ember.y * size.height)
            )
        }
    }
}

// ============================================
// OCEAN — Deep blue gradient + wave motion
// ============================================

@Composable
private fun OceanBackground(mood: Mood?, modifier: Modifier) {
    val moodShift = moodColorShift(mood)
    var frame by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(40L)
            frame += 0.03f
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val topColor = Color(0xFF0A1628).warmShift(moodShift)
        val bottomColor = Color(0xFF1A5276).warmShift(moodShift * 0.5f)

        drawRect(
            brush = Brush.verticalGradient(listOf(topColor, bottomColor))
        )

        // Draw wave layers
        val waveColors = listOf(
            Color(0xFF2980B9).copy(alpha = 0.15f),
            Color(0xFF3498DB).copy(alpha = 0.12f),
            Color(0xFF5DADE2).copy(alpha = 0.1f)
        )

        waveColors.forEachIndexed { index, color ->
            val baseY = size.height * (0.4f + index * 0.15f)
            val amplitude = size.height * 0.03f
            val frequency = 0.008f + index * 0.002f
            val phaseOffset = frame * (1.5f + index * 0.5f)

            for (x in 0..size.width.toInt() step 3) {
                val waveY = baseY + sin((x * frequency + phaseOffset).toDouble()).toFloat() * amplitude
                drawCircle(
                    color = color,
                    radius = 4f,
                    center = Offset(x.toFloat(), waveY)
                )
            }
        }
    }
}

// ============================================
// NIGHT SKY — Dark gradient + twinkling stars
// ============================================

private data class Star(
    val x: Float,
    val y: Float,
    val size: Float,
    val baseAlpha: Float,
    val twinkleSpeed: Float,
    val phase: Float
)

@Composable
private fun NightSkyBackground(mood: Mood?, modifier: Modifier) {
    val moodShift = moodColorShift(mood)
    val stars = remember {
        List(60) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat() * 0.8f,
                size = Random.nextFloat() * 2.5f + 0.5f,
                baseAlpha = Random.nextFloat() * 0.5f + 0.3f,
                twinkleSpeed = Random.nextFloat() * 2f + 1f,
                phase = Random.nextFloat() * 6.28f
            )
        }
    }

    var frame by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(50L)
            frame += 0.05f
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val topColor = Color(0xFF0C0C1D).warmShift(moodShift)
        val midColor = Color(0xFF1A1A3E).warmShift(moodShift * 0.5f)
        val bottomColor = Color(0xFF2D2D5E).warmShift(moodShift * 0.3f)

        drawRect(
            brush = Brush.verticalGradient(listOf(topColor, midColor, bottomColor))
        )

        // Stars
        stars.forEach { star ->
            val twinkle = sin((frame * star.twinkleSpeed + star.phase).toDouble()).toFloat()
            val alpha = (star.baseAlpha + twinkle * 0.25f).coerceIn(0.05f, 0.9f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = star.size,
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }
    }
}
