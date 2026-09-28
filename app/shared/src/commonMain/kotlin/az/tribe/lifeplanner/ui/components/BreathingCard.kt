package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import az.tribe.lifeplanner.domain.model.XpRewards
import az.tribe.lifeplanner.domain.enum.HabitCompletionSource
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.usecases.habit.CreditHabitsFromSessionUseCase
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.bouncyClickable
import az.tribe.lifeplanner.ui.theme.modernColors
import com.russhwolf.settings.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Clock

private const val BREATH_GOAL = 3
private const val KEY_SESSIONS_TOTAL = "breath_sessions_total"

/** One phase of a breath: a spoken label, a short orb caption, its length, and the orb's end scale. */
data class BreathPhase(val label: String, val short: String, val durationMs: Int, val targetScale: Float)

/**
 * A breathing technique. Techniques are chosen automatically by the user's mindfulness level (see
 * [BREATH_LEVELS]) and are never named in the UI, the app just guides the breath. Each is [rounds]
 * repeats of [phases]; [boxStyle] techniques render the square-frame tracer instead of the orb.
 * [label] is for code/analytics only and is never shown.
 */
enum class BreathTechnique(
    val id: String,
    val label: String,
    val symbol: String,
    val xp: Int,
    val rounds: Int,
    val phases: List<BreathPhase>,
    val boxStyle: Boolean = false,
) {
    CALM("calm", "Calm Breath", "🫧", XpRewards.MINDFUL_BREATH, 3, listOf(
        BreathPhase("Breathe in", "In", 3800, 1f),
        BreathPhase("Breathe out", "Out", 3800, 0.55f),
    )),
    EXTENDED_EXHALE("ext_exhale", "Extended Exhale", "🌊", XpRewards.MINDFUL_BREATH, 4, listOf(
        BreathPhase("Breathe in", "In", 4000, 1f),
        BreathPhase("Long exhale", "Out", 6000, 0.5f),
    )),
    BOX("box", "Box Breathing", "⬜", XpRewards.MINDFUL_BREATH_BOX, 3, listOf(
        BreathPhase("Breathe in", "In", 4000, 1f),
        BreathPhase("Hold", "Hold", 4000, 1f),
        BreathPhase("Breathe out", "Out", 4000, 0.55f),
        BreathPhase("Hold", "Hold", 4000, 0.55f),
    ), boxStyle = true),
    RELAXING_478("relax478", "4-7-8 Breath", "🌙", XpRewards.MINDFUL_BREATH_BOX, 3, listOf(
        BreathPhase("Breathe in", "In", 4000, 1f),
        BreathPhase("Hold", "Hold", 7000, 1f),
        BreathPhase("Breathe out", "Out", 8000, 0.5f),
    )),
    RESONANT("resonant", "Resonant Breath", "💫", XpRewards.MINDFUL_BREATH_BOX, 5, listOf(
        BreathPhase("Breathe in", "In", 5500, 1f),
        BreathPhase("Breathe out", "Out", 5500, 0.5f),
    )),
}

/** Lifetime completed-session count required for each technique. Order = unlock order. */
private val BREATH_LEVELS: List<Pair<Int, BreathTechnique>> = listOf(
    0 to BreathTechnique.CALM,
    3 to BreathTechnique.EXTENDED_EXHALE,
    8 to BreathTechnique.BOX,
    15 to BreathTechnique.RELAXING_478,
    25 to BreathTechnique.RESONANT,
)

/** The technique the user has grown into at [sessions] lifetime sessions (the highest unlocked). */
private fun techniqueForSessions(sessions: Int): BreathTechnique =
    BREATH_LEVELS.last { sessions >= it.first }.second

/** Lifetime sessions at which the next technique unlocks, or null once every rhythm is unlocked. */
private fun nextUnlockAt(sessions: Int): Int? =
    BREATH_LEVELS.firstOrNull { sessions < it.first }?.first

internal fun todayBreathKey(): String =
    "breaths_" + Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

/**
 * A tiny in-app "grow" moment: take a breath or two a day. Finishing a session earns mindfulness XP
 * and counts toward a small daily goal. The breathing pattern deepens on its own as the user builds
 * a practice (see [BREATH_LEVELS]), no technique picker, no names, just a fresh rhythm now and then.
 */
@Composable
fun BreathingCard(
    /**
     * Why this is on screen today. The card no longer appears unprompted, so it owes the user an
     * explanation for interrupting — see [az.tribe.lifeplanner.domain.service.BreathMoment].
     */
    reason: String? = null,
) {
    val settings: Settings = koinInject()
    val gamification: GamificationRepository = koinInject()
    val creditHabits: CreditHabitsFromSessionUseCase = koinInject()
    val haptic = rememberHapticManager()
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.modernColors

    val dateKey = remember { todayBreathKey() }
    var count by remember { mutableStateOf(settings.getInt(dateKey, 0)) }
    var sessionsTotal by remember { mutableStateOf(settings.getInt(KEY_SESSIONS_TOTAL, 0)) }
    var active by remember { mutableStateOf(false) }
    var justUnlocked by remember { mutableStateOf(false) }

    val technique = techniqueForSessions(sessionsTotal)

    // With a reason supplied the feed has already decided this is a moment worth mentioning, and
    // hides the card again as soon as one breath is done.
    AnimatedVisibility(visible = if (reason != null) count == 0 else count < BREATH_GOAL) {
        Surface(Modifier.fillMaxWidth(), color = c.cardBackground, shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.large)) {
            Column(
                Modifier.fillMaxWidth().padding(LifePlannerDesign.Padding.cardContent),
                verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(technique.symbol, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Take a breath", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
                        Text(
                            reason
                                ?: if (count == 0) "A moment to reset before you dive in."
                                else "$count of $BREATH_GOAL today. One more?",
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        )
                    }
                    AppButton(text = "Breathe", onClick = { active = true }, variant = AppButtonVariant.SECONDARY)
                }

                // A quiet sense of progression: a fresh rhythm on level-up, or how far to the next.
                val remaining = nextUnlockAt(sessionsTotal)?.minus(sessionsTotal)
                when {
                    justUnlocked -> Text(
                        "🎉 A new breath unlocked — a fresh rhythm to try.",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = c.primary,
                    )
                    remaining != null -> Text(
                        if (remaining == 1) "1 more breath to your next rhythm" else "$remaining more breaths to your next rhythm",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                    )
                }
            }
        }
    }

    // The breath itself never shares the screen: it takes the whole of it (see [ImmersiveBreath]).
    if (active) {
        ImmersiveBreath(
            technique = technique,
            onDone = {
                val next = (count + 1).coerceAtMost(BREATH_GOAL)
                count = next
                settings.putInt(dateKey, next)
                val newTotal = sessionsTotal + 1
                // Crossing into a higher level reveals a fresh rhythm (still unnamed).
                if (techniqueForSessions(newTotal) != techniqueForSessions(sessionsTotal)) justUnlocked = true
                settings.putInt(KEY_SESSIONS_TOTAL, newTotal)
                sessionsTotal = newTotal
                scope.launch {
                    runCatching { gamification.awardXp(technique.xp.toLong()) }
                    // A finished breath also counts toward any habit linked to breathing,
                    // so "three breaths a day" is tracked by doing it, not by ticking it.
                    runCatching { creditHabits(HabitCompletionSource.BREATHING) }
                }
                haptic.success()
                active = false
            },
            onCancel = { active = false },
        )
    }
}

/**
 * A self-contained guided breath for use outside the feed card (e.g. the "take a breath" nudge).
 * Auto-selects the level-appropriate technique, awards XP and advances the practice on completion,
 * then calls [onClose]. Cancelling just closes without counting. Like every breath in the app it
 * takes over the whole screen (see [ImmersiveBreath]), so callers need no scrim of their own.
 *
 * [onFinished] runs only when the session is breathed to the end, before [onClose].
 */
@Composable
fun GuidedBreathSession(onClose: () -> Unit, onFinished: () -> Unit = {}) {
    val settings: Settings = koinInject()
    val gamification: GamificationRepository = koinInject()
    val creditHabits: CreditHabitsFromSessionUseCase = koinInject()
    val haptic = rememberHapticManager()
    val scope = rememberCoroutineScope()

    val sessionsTotal = remember { settings.getInt(KEY_SESSIONS_TOTAL, 0) }
    val technique = techniqueForSessions(sessionsTotal)

    ImmersiveBreath(
        technique = technique,
        onDone = {
            settings.putInt(KEY_SESSIONS_TOTAL, sessionsTotal + 1)
            val dateKey = todayBreathKey()
            settings.putInt(dateKey, settings.getInt(dateKey, 0) + 1)
            scope.launch {
                runCatching { gamification.awardXp(technique.xp.toLong()) }
                runCatching { creditHabits(HabitCompletionSource.BREATHING) }
            }
            haptic.success()
            onFinished()
            onClose()
        },
        onCancel = onClose,
    )
}

// ── The immersive session ────────────────────────────────────────────────────

/** The night the breath happens in: fixed in both themes, so the screen always reads as "off". */
private val BREATH_NIGHT = listOf(Color(0xFF070B18), Color(0xFF121A33), Color(0xFF070B18))
private val BREATH_GLOW = Color(0xFF7DA2FF)
private val BREATH_TEXT = Color(0xFFEAF0FF)

/**
 * The breath, alone on the screen. Everything else in the app goes away (a full-screen dialog over
 * a fixed night backdrop), the orb sits dead centre, and the only words are the phase you're in.
 * The screen is kept awake for the duration, and each phase change lands as a soft haptic so the
 * rhythm can be followed with your eyes shut.
 *
 * Leaving is deliberate but never trapped: a quiet "End" at the bottom, or the system back gesture.
 */
@Composable
private fun ImmersiveBreath(technique: BreathTechnique, onDone: () -> Unit, onCancel: () -> Unit) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        KeepScreenOn(enabled = true)
        // The whole thing fades up rather than cutting in, so entering the breath is itself calming.
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(BREATH_NIGHT))
                .graphicsLayer { alpha = enter.value },
            contentAlignment = Alignment.Center,
        ) {
            if (technique.boxStyle) BoxBreathVisual(technique, onDone, onCancel)
            else PacedBreathVisual(technique, onDone, onCancel)
        }
    }
}

/** The one way out of a session, kept quiet at the bottom of the screen. */
@Composable
private fun EndBreathLink(onCancel: () -> Unit) {
    Text(
        "End",
        style = MaterialTheme.typography.labelLarge,
        color = BREATH_TEXT.copy(alpha = 0.35f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .bouncyClickable(onClick = onCancel)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** Round progress as dots rather than a counter: information without arithmetic. */
@Composable
private fun BreathRounds(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(BREATH_GLOW.copy(alpha = if (i <= current) 0.9f else 0.22f)),
            )
        }
    }
}

/**
 * The "grow" orb: it swells and settles through the technique's phases. Timing is driven by explicit
 * per-phase delays (so hold phases actually hold), while the orb scale eases toward each phase's
 * target over that phase's duration.
 */
@Composable
private fun PacedBreathVisual(technique: BreathTechnique, onDone: () -> Unit, onCancel: () -> Unit) {
    val haptic = rememberHapticManager()
    val phases = technique.phases
    val rounds = technique.rounds
    var round by remember { mutableStateOf(0) }
    var phaseIdx by remember { mutableStateOf(0) }
    val current = phases[phaseIdx]
    val scale by animateFloatAsState(
        targetValue = current.targetScale,
        animationSpec = tween(durationMillis = current.durationMs, easing = FastOutSlowInEasing),
        label = "pacedScale",
    )
    LaunchedEffect(Unit) {
        for (r in 0 until rounds) {
            round = r
            for (i in phases.indices) {
                phaseIdx = i
                // A soft tick at each turn of the breath, so the rhythm carries with eyes closed.
                haptic.click()
                delay(phases[i].durationMs.toLong())
            }
        }
        onDone()
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            // A wide, soft halo that breathes with the orb, so the light in the room changes too.
            Box(
                Modifier.size(300.dp)
                    .graphicsLayer { scaleX = scale * 1.25f; scaleY = scale * 1.25f }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(BREATH_GLOW.copy(alpha = 0.18f), Color.Transparent))),
            )
            Box(
                Modifier.size(220.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(BREATH_GLOW.copy(alpha = 0.62f), BREATH_GLOW.copy(alpha = 0.10f)))),
            )
            Text(
                current.short,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, letterSpacing = 2.sp),
                color = BREATH_TEXT,
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            current.label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, letterSpacing = 1.sp),
            color = BREATH_TEXT.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        BreathRounds(current = round, total = rounds)
        Spacer(Modifier.weight(1f))
        EndBreathLink(onCancel)
    }
}

/**
 * Box breathing: inhale, hold, exhale, hold, four equal phases. A square frame traces one side per
 * phase so the passing time reads as motion around the box, not a number ticking down. Phase count,
 * durations, and labels come from [technique].
 */
@Composable
private fun BoxBreathVisual(technique: BreathTechnique, onDone: () -> Unit, onCancel: () -> Unit) {
    val haptic = rememberHapticManager()
    val rounds = technique.rounds
    val phases = technique.phases
    var currentSide by remember { mutableStateOf(0) }
    var round by remember { mutableStateOf(0) }
    val sideProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        for (r in 0 until rounds) {
            round = r
            for (side in 0..3) {
                currentSide = side
                haptic.click()
                sideProgress.snapTo(0f)
                sideProgress.animateTo(1f, animationSpec = tween(phases[side].durationMs, easing = LinearEasing))
            }
        }
        onDone()
    }

    val p = sideProgress.value
    val phaseLabel = phases[currentSide].label
    // Inner square breathes with the phase: grows on inhale, holds, shrinks on exhale, holds.
    val innerScale = when (currentSide) {
        0 -> 0.55f + 0.45f * p
        1 -> 1f
        2 -> 1f - 0.4f * p
        else -> 0.6f
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val strokePx = 10.dp.toPx()
                val s = size.minDimension - strokePx
                val o = strokePx / 2f
                // Corners clockwise from top-left; side i runs corners[i] -> corners[i+1].
                val corners = listOf(
                    Offset(o, o),
                    Offset(o + s, o),
                    Offset(o + s, o + s),
                    Offset(o, o + s),
                    Offset(o, o),
                )
                val faint = BREATH_GLOW.copy(alpha = 0.18f)
                val bright = BREATH_GLOW

                // Faint full frame.
                for (i in 0..3) drawLine(faint, corners[i], corners[i + 1], strokePx, cap = StrokeCap.Round)

                // Inner square that breathes.
                val inner = s * innerScale
                val center = Offset(size.width / 2f, size.height / 2f)
                drawRoundRect(
                    color = BREATH_GLOW.copy(alpha = 0.12f),
                    topLeft = Offset(center.x - inner / 2f, center.y - inner / 2f),
                    size = Size(inner, inner),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )

                // Bright trace: completed sides this round, plus the partial current side + a head dot.
                for (i in 0..3) {
                    when {
                        i < currentSide -> drawLine(bright, corners[i], corners[i + 1], strokePx, cap = StrokeCap.Round)
                        i == currentSide -> {
                            val head = lerp(corners[i], corners[i + 1], p)
                            drawLine(bright, corners[i], head, strokePx, cap = StrokeCap.Round)
                            drawCircle(bright, radius = strokePx * 0.85f, center = head)
                        }
                    }
                }
            }
            Text(
                phaseLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, letterSpacing = 1.sp),
                color = BREATH_TEXT,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(40.dp))
        BreathRounds(current = round, total = rounds)
        Spacer(Modifier.weight(1f))
        EndBreathLink(onCancel)
    }
}
