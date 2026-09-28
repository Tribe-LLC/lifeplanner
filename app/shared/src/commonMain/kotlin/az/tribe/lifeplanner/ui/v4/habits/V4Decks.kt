package az.tribe.lifeplanner.ui.v4.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.data.habits.HabitRow
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.HabitLearning
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.X
import com.adamglin.phosphoricons.regular.ArrowUp
import com.adamglin.phosphoricons.regular.Clock
import com.adamglin.phosphoricons.regular.Heart
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import leanlifeplanner.app.shared.generated.resources.Res
import leanlifeplanner.app.shared.generated.resources.v4_card_blue
import leanlifeplanner.app.shared.generated.resources.v4_card_blue_dark
import leanlifeplanner.app.shared.generated.resources.v4_card_green
import leanlifeplanner.app.shared.generated.resources.v4_card_green_dark
import leanlifeplanner.app.shared.generated.resources.v4_card_orange
import leanlifeplanner.app.shared.generated.resources.v4_card_orange_dark
import leanlifeplanner.app.shared.generated.resources.v4_card_pink
import leanlifeplanner.app.shared.generated.resources.v4_card_pink_dark
import leanlifeplanner.app.shared.generated.resources.v4_card_purple
import leanlifeplanner.app.shared.generated.resources.v4_card_purple_dark
import leanlifeplanner.app.shared.generated.resources.v4_card_teal
import leanlifeplanner.app.shared.generated.resources.v4_card_teal_dark
import leanlifeplanner.app.shared.generated.resources.v4_illo_balanced
import leanlifeplanner.app.shared.generated.resources.v4_illo_checkin
import leanlifeplanner.app.shared.generated.resources.v4_illo_done
import leanlifeplanner.app.shared.generated.resources.v4_illo_learn
import leanlifeplanner.app.shared.generated.resources.v4_illo_review
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock

/** Same red as "Stop tracking" on the Habits page; reads on white and on the dark surface. */
private val DangerRed = Color(0xFFD9483B)

// ── Art (ui8: Mind Garden illustrations, Mesh Gradient backgrounds) ────────

enum class DeckArt { CHECKIN, DONE, REVIEW, BALANCED, LEARN }

internal fun deckArt(a: DeckArt): DrawableResource = when (a) {
    DeckArt.CHECKIN -> Res.drawable.v4_illo_checkin
    DeckArt.DONE -> Res.drawable.v4_illo_done
    DeckArt.REVIEW -> Res.drawable.v4_illo_review
    DeckArt.BALANCED -> Res.drawable.v4_illo_balanced
    DeckArt.LEARN -> Res.drawable.v4_illo_learn
}

/** A card's colour: a light and a dark gradient, and a flat base shown while the image loads. */
enum class CardTint(val light: DrawableResource, val dark: DrawableResource, val base: Color) {
    BLUE(Res.drawable.v4_card_blue, Res.drawable.v4_card_blue_dark, Color(0xFF3B5BE5)),
    PURPLE(Res.drawable.v4_card_purple, Res.drawable.v4_card_purple_dark, Color(0xFF7C4DDB)),
    ORANGE(Res.drawable.v4_card_orange, Res.drawable.v4_card_orange_dark, Color(0xFFE26A3A)),
    GREEN(Res.drawable.v4_card_green, Res.drawable.v4_card_green_dark, Color(0xFF5E8F2A)),
    TEAL(Res.drawable.v4_card_teal, Res.drawable.v4_card_teal_dark, Color(0xFF2A9BD8)),
    PINK(Res.drawable.v4_card_pink, Res.drawable.v4_card_pink_dark, Color(0xFFD9557A));

    companion object {
        /** Area habits take their area's colour; plain habits spread over the rest so a deck is not all blue. */
        fun of(r: HabitRow): CardTint = when (V4TodayViewModel.areaOf(r.habit)) {
            PlanArea.FITNESS -> ORANGE
            PlanArea.MIND -> PURPLE
            else -> listOf(BLUE, TEAL, PINK, GREEN, PURPLE)[(r.habit.id.hashCode() and 0x7fffffff) % 5]
        }
    }
}

/** An illustration on a light plate, so its dark line work reads in dark mode too. */
@Composable
internal fun ArtPlate(art: DeckArt, size: Dp, modifier: Modifier = Modifier, plate: Boolean = V4.colors.isDark) {
    Box(
        modifier.size(size).let { if (plate) it.clip(RoundedCornerShape(size / 5)).background(Color(0xFFF6F5F1)) else it },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(deckArt(art)), contentDescription = null, modifier = Modifier.size(size * 0.92f))
    }
}

// ── The swipeable card ──────────────────────────────────────────────────────

/**
 * A card that follows the finger and leaves the deck past a threshold. [command] flies it off
 * the same way for the buttons; [labels] are the stamps shown while dragging.
 */
@Composable
private fun SwipeCard(
    key: Any,
    command: Swipe?,
    onGone: (Swipe) -> Unit,
    labels: Map<Swipe, String>,
    actions: Map<Swipe, String>,
    modifier: Modifier = Modifier,
    /** False when "up" opens something instead: the card springs back and stays. */
    flyUp: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember(key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    BoxWithConstraints(modifier) {
        val w = with(LocalDensity.current) { maxWidth.toPx() }
        val h = with(LocalDensity.current) { maxHeight.toPx() }
        suspend fun fly(dir: Swipe) {
            if (dir == Swipe.UP && !flyUp) {
                offset.animateTo(Offset.Zero, tween(200))
                onGone(dir)
                return
            }
            val target = when (dir) {
                Swipe.RIGHT -> Offset(w * 1.4f, offset.value.y + h * 0.05f)
                Swipe.LEFT -> Offset(-w * 1.4f, offset.value.y + h * 0.05f)
                Swipe.UP -> Offset(offset.value.x, -h * 1.3f)
            }
            offset.animateTo(target, tween(220))
            onGone(dir)
        }
        LaunchedEffect(key, command) { command?.let { fly(it) } }
        val x = offset.value.x
        val y = offset.value.y
        val showing = when {
            x > w * 0.08f -> Swipe.RIGHT
            x < -w * 0.08f -> Swipe.LEFT
            y < -h * 0.06f && abs(x) < w * 0.15f -> Swipe.UP
            else -> null
        }
        val strength = when (showing) {
            Swipe.RIGHT, Swipe.LEFT -> (abs(x) / (w * 0.3f)).coerceIn(0f, 1f)
            Swipe.UP -> (-y / (h * 0.2f)).coerceIn(0f, 1f)
            null -> 0f
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = x
                    translationY = y
                    rotationZ = (x / w) * 12f
                }
                .semantics {
                    customActions = actions.map { (dir, label) ->
                        CustomAccessibilityAction(label) { scope.launch { fly(dir) }; true }
                    }
                }
                .pointerInput(key) {
                    detectDragGestures(
                        onDragEnd = {
                            val p = offset.value
                            val dir = when {
                                p.x > w * 0.3f -> Swipe.RIGHT
                                p.x < -w * 0.3f -> Swipe.LEFT
                                p.y < -h * 0.2f && Swipe.UP in actions -> Swipe.UP
                                else -> null
                            }
                            scope.launch { if (dir != null) fly(dir) else offset.animateTo(Offset.Zero, tween(200)) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(Offset.Zero, tween(200)) } },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + drag) }
                    }
                },
        ) {
            content()
            if (showing != null && strength > 0.05f) {
                labels[showing]?.let { text ->
                    Text(
                        text,
                        style = V4.type.title.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = Color.White,
                        modifier = Modifier
                            .align(if (showing == Swipe.LEFT) Alignment.TopEnd else Alignment.TopStart)
                            .padding(top = 76.dp, start = 24.dp, end = 24.dp)
                            .graphicsLayer { alpha = strength }
                            .rotate(if (showing == Swipe.LEFT) 10f else -10f)
                            .border(3.dp, Color.White, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** The coloured face every deck card shares: gradient, a scrim for the text, and the content. */
@Composable
private fun CardFace(tint: CardTint, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(tint.base),
    ) {
        Image(
            painterResource(if (V4.colors.isDark) tint.dark else tint.light),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0x80080A14))
            )
        )
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun LightPill(text: String, color: Color = Color(0xFF15171C)) {
    Text(
        text,
        style = V4.type.label,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE6FFFFFF)).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Two cards peeking out behind the top one, so it reads as a deck. */
@Composable
private fun DeckBacking(left: Int) {
    val c = V4.colors
    if (left > 2) Box(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 22.dp).rotate(3f).clip(RoundedCornerShape(28.dp)).background(c.accentSoft.copy(alpha = 0.7f)))
    if (left > 1) Box(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp).rotate(-2f).clip(RoundedCornerShape(28.dp)).background(c.accentSoft))
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit, big: Boolean, primary: Boolean, tintColor: Color = V4.colors.ink2) {
    val c = V4.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val size = if (big) 64.dp else 52.dp
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (primary) c.accent else c.surface)
                .let { if (!primary) it.border(2.dp, c.line, CircleShape) else it }
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (primary) c.onAccent else tintColor, modifier = Modifier.size(if (big) 26.dp else 22.dp))
        }
        Text(label, style = V4.type.caption.copy(fontWeight = FontWeight.SemiBold), color = if (primary) c.accentInk else c.ink2)
    }
}

@Composable
private fun DeckTopBar(center: @Composable () -> Unit, onClose: () -> Unit, canUndo: Boolean, onUndo: () -> Unit) {
    val c = V4.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape)
                .clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "Close" },
            contentAlignment = Alignment.Center,
        ) { Icon(PhosphorIcons.Bold.X, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center() }
        Box(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(22.dp))
                .clickable(enabled = canUndo, role = Role.Button, onClick = onUndo).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Undo", style = V4.type.bodyStrong, color = if (canUndo) c.accentInk else c.ink3) }
    }
}

@Composable
private fun Progress(done: Int, total: Int) {
    val c = V4.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${(done + 1).coerceAtMost(total)} of $total", style = V4.type.label, color = c.ink2)
        Box(Modifier.width(160.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.line)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (total == 0) 0f else done.toFloat() / total).clip(RoundedCornerShape(3.dp)).background(c.accent))
        }
    }
}

@Composable
private fun EndScreen(art: DeckArt, title: String, body: String, button: String, onDone: () -> Unit, extra: @Composable ColumnScope.() -> Unit = {}) {
    val c = V4.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            ArtPlate(art, 220.dp)
            Text(title, style = V4.type.display.copy(fontSize = 30.sp), color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(body, style = V4.type.body, color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
        }
        extra()
        V4PrimaryButton(button, onDone, Modifier.fillMaxWidth())
    }
}

// ── Quick check-in ──────────────────────────────────────────────────────────

@Composable
fun V4CheckInScreen(onClose: () -> Unit, viewModel: V4CheckInViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    var command by remember { mutableStateOf<Swipe?>(null) }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckTopBar(center = { if (!s.finished && s.cards.isNotEmpty()) Progress(s.index, s.cards.size) }, onClose = onClose, canUndo = s.canUndo, onUndo = viewModel::undo)
        when {
            !s.loaded -> Spacer(Modifier.weight(1f))
            s.cards.isEmpty() -> EndScreen(DeckArt.DONE, "Nothing left for now", "Everything due today is done or set for another day.", "Back to Today", onClose)
            s.finished -> EndScreen(
                DeckArt.DONE,
                "All checked in",
                buildString {
                    append("${s.done} done")
                    if (s.notToday > 0) append(", ${s.notToday} not today")
                    if (s.later > 0) append(", ${s.later} still open for later")
                    append(". Not-today days never count against a streak.")
                },
                "Back to Today",
                onClose,
            )
            else -> {
                val row = s.current!!
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    DeckBacking(s.cards.size - s.index)
                    SwipeCard(
                        key = row.habit.id + s.index,
                        command = command,
                        onGone = { dir -> command = null; viewModel.swipe(dir) },
                        labels = mapOf(Swipe.RIGHT to "DONE", Swipe.LEFT to "NOT TODAY", Swipe.UP to "LATER"),
                        actions = mapOf(Swipe.RIGHT to "Done", Swipe.LEFT to "Not today", Swipe.UP to "Later"),
                        modifier = Modifier.fillMaxSize().padding(bottom = 12.dp),
                    ) {
                        HabitCardFace(row, s.counts[row.habit.id] ?: 0, onMinus = { viewModel.count(-1) }, onPlus = { viewModel.count(1) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Top) {
                    RoundAction(PhosphorIcons.Bold.X, "Not today", { if (command == null) command = Swipe.LEFT }, big = true, primary = false)
                    Box(Modifier.padding(top = 6.dp)) {
                        RoundAction(PhosphorIcons.Regular.Clock, "Later", { if (command == null) command = Swipe.UP }, big = false, primary = false)
                    }
                    RoundAction(PhosphorIcons.Bold.Check, "Done", { if (command == null) command = Swipe.RIGHT }, big = true, primary = true)
                }
                Text(
                    "Swipe right: done. Left: not today, it never breaks a streak. Up: later.",
                    style = V4.type.caption, color = c.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HabitCardFace(r: HabitRow, count: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val tint = CardTint.of(r)
    val area = V4TodayViewModel.areaOf(r.habit)
    CardFace(tint) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LightPill(az.tribe.lifeplanner.ui.v4.components.areaName(area), V4.colors.area(area).ink)
            val whenText = when {
                HabitLearning.reminderIsOff(r.reminderMinute, r.usualMinute) -> "Usually ${HabitLearning.roughly(r.usualMinute!!)}"
                r.habit.reminderTime != null -> "At ${r.habit.reminderTime}"
                r.usualMinute != null -> "Usually ${HabitLearning.roughly(r.usualMinute)}"
                else -> null
            }
            whenText?.let { LightPill(it) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(150.dp).clip(RoundedCornerShape(40.dp)).background(Color(0xE6FFFFFF)), contentAlignment = Alignment.Center) {
                AreaIllustration(area, size = 110.dp)
            }
        }
        Text(r.habit.title, style = V4.type.display.copy(fontSize = 32.sp, lineHeight = 34.sp), color = Color.White, maxLines = 3)
        Text(
            when {
                r.habit.type == HabitType.QUIT -> if (r.stats.streak >= 2) "To break. ${r.stats.streak} days strong" else "A habit to break"
                r.stats.streak >= 2 -> "${r.stats.streak} ${if (r.stats.streakInWeeks) "weeks" else "days"} in a row. Keep it going"
                r.stats.score != null -> "${(r.stats.score * 100).roundToInt()}% in the last 30 days"
                else -> "New this week"
            },
            style = V4.type.bodyStrong, color = Color.White,
        )
        if (r.habit.targetCount > 1) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepButton("−", "One less", onMinus)
                Text("$count of ${r.habit.targetCount}${r.habit.unit?.let { " $it" } ?: ""}", style = V4.type.headline, color = Color.White)
                StepButton("+", "One more", onPlus)
            }
        }
    }
}

@Composable
private fun StepButton(text: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color(0xEBFFFFFF)).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = V4.type.headline, color = Color(0xFF15171C)) }
}

// ── Review what slipped ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V4ReviewScreen(onClose: () -> Unit, viewModel: V4ReviewViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    var command by remember { mutableStateOf<Swipe?>(null) }
    var sheet by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckTopBar(
            center = { if (s.started && !s.finished) Progress(s.index, s.cards.size) else Text("Review", style = V4.type.label, color = c.ink2) },
            onClose = onClose, canUndo = s.canUndo, onUndo = viewModel::undo,
        )
        when {
            !s.loaded -> Spacer(Modifier.weight(1f))
            s.cards.isEmpty() -> EndScreen(DeckArt.BALANCED, "Nothing has slipped", "When a habit is missed ${HabitLearning.MISSES} times in a row, it comes up here so you can keep it, lighten it, or let it go.", "Back", onClose)
            !s.started -> {
                val n = s.cards.size
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                    ArtPlate(DeckArt.REVIEW, 230.dp)
                    Text("$n ${if (n == 1) "habit has" else "habits have"} slipped", style = V4.type.display.copy(fontSize = 30.sp), color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                    Text(
                        "Each one was missed ${HabitLearning.MISSES} times in a row. That usually means too much at once, not too little willpower. Keep what matters, make some easier, and let the rest go. Their history stays.",
                        style = V4.type.body, color = c.ink2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 330.dp),
                    )
                }
                V4PrimaryButton("Start, about a minute", viewModel::start, Modifier.fillMaxWidth())
                V4TextButton("Not now", onClose, Modifier.fillMaxWidth())
            }
            s.finished -> EndScreen(
                DeckArt.BALANCED,
                "Lighter now",
                listOfNotNull(
                    s.kept.takeIf { it > 0 }?.let { "kept $it" },
                    s.easier.takeIf { it > 0 }?.let { "made $it easier" },
                    s.letGo.takeIf { it > 0 }?.let { "let $it go" },
                ).joinToString(", ").replaceFirstChar { it.uppercase() } +
                    ". You now have ${s.remaining} habits." + if (s.letGo > 0) " Let-go habits keep their history." else "",
                "Back to Today",
                onClose,
            )
            else -> {
                val row = s.current!!
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    DeckBacking(s.cards.size - s.index)
                    SwipeCard(
                        key = row.habit.id + s.index,
                        command = command,
                        onGone = { dir ->
                            command = null
                            when (dir) {
                                Swipe.RIGHT -> viewModel.keep()
                                Swipe.LEFT -> viewModel.letGo()
                                Swipe.UP -> sheet = true
                            }
                        },
                        labels = mapOf(Swipe.RIGHT to "KEEP", Swipe.LEFT to "LET GO", Swipe.UP to "EASIER"),
                        actions = mapOf(Swipe.RIGHT to "Keep", Swipe.LEFT to "Let go", Swipe.UP to "Make it easier"),
                        modifier = Modifier.fillMaxSize().padding(bottom = 12.dp),
                        // Up opens the options; the card springs back and waits until one is picked.
                        flyUp = false,
                    ) {
                        ReviewCardFace(row)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Top) {
                    RoundAction(PhosphorIcons.Bold.X, "Let go", { if (command == null) command = Swipe.LEFT }, big = true, primary = false, tintColor = DangerRed)
                    Box(Modifier.padding(top = 6.dp)) {
                        RoundAction(PhosphorIcons.Regular.ArrowUp, "Easier", { sheet = true }, big = false, primary = false)
                    }
                    RoundAction(PhosphorIcons.Regular.Heart, "Keep", { if (command == null) command = Swipe.RIGHT }, big = true, primary = true)
                }
                Text("Swipe right to keep, left to let go, up to make it easier.", style = V4.type.caption, color = c.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                if (sheet) {
                    val options = s.options[row.habit.id].orEmpty()
                    ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
                        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Make ${row.habit.title} easier", style = V4.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
                            options.forEach { o ->
                                Column(
                                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).border(1.5.dp, c.line, RoundedCornerShape(16.dp))
                                        .clickable(role = Role.Button) { sheet = false; viewModel.easier(o) }.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(o.title, style = V4.type.bodyStrong, color = c.ink)
                                    Text(o.detail, style = V4.type.caption, color = c.ink3)
                                }
                            }
                            V4TextButton("Back", { sheet = false }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCardFace(r: HabitRow) {
    val tint = CardTint.of(r)
    val area = V4TodayViewModel.areaOf(r.habit)
    val slip = r.slip
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    CardFace(tint) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            slip?.let { LightPill(HabitLearning.slipText(it)) }
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xE6FFFFFF)), contentAlignment = Alignment.Center) {
                AreaIllustration(area, size = 44.dp)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(r.habit.title, style = V4.type.display.copy(fontSize = 32.sp, lineHeight = 34.sp), color = Color.White, maxLines = 3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            V4ReviewViewModel.strip(r, today).forEach { v ->
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(
                        when {
                            v >= 1f -> Color.White
                            v >= 0.5f -> Color.White.copy(alpha = 0.45f)
                            v >= 0f -> Color.White.copy(alpha = 0.16f)
                            else -> Color.Transparent
                        }
                    )
                )
            }
        }
        slip?.let { Text(HabitLearning.historyText(it), style = V4.type.bodyStrong, color = Color.White) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xEBFFFFFF)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("What I noticed", style = V4.type.caption.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2C42B0))
            Text(V4ReviewViewModel.noticed(r), style = V4.type.body, color = Color(0xFF15171C))
        }
    }
}

// ── Entry cards for Today and Habits ────────────────────────────────────────

/** The big "N habits left" card that opens the check-in deck. */
@Composable
fun CheckInHero(left: Int, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val tint = CardTint.BLUE
    Box(modifier.fillMaxWidth().heightIn(min = 190.dp).clip(RoundedCornerShape(24.dp)).background(tint.base)) {
        Image(
            painterResource(if (V4.colors.isDark) tint.dark else tint.light),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color(0x66080A14))))
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (hourNow() >= 17) "Evening check-in" else "Quick check-in", style = V4.type.label, color = Color.White)
                Text("$left habits left", style = V4.type.display.copy(fontSize = 30.sp, lineHeight = 32.sp), color = Color.White)
                Text("One card at a time. Swipe right if you did it, left if not today.", style = V4.type.caption, color = Color.White)
                Box(
                    Modifier.padding(top = 6.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)
                        .clickable(role = Role.Button, onClick = onStart).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Start check-in", style = V4.type.bodyStrong, color = Color(0xFF1E2F85)) }
            }
            ArtPlate(DeckArt.CHECKIN, 112.dp, plate = true)
        }
    }
}

/** "N habits have slipped" with the way into the review deck. */
@Composable
fun SlippedCard(count: Int, onReview: () -> Unit, modifier: Modifier = Modifier) {
    val c = V4.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onReview).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtPlate(DeckArt.REVIEW, 72.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$count ${if (count == 1) "habit has" else "habits have"} slipped", style = V4.type.bodyStrong, color = c.ink)
            Text("Missed ${HabitLearning.MISSES} times in a row. Keep, make easier, or let go.", style = V4.type.caption, color = c.ink2)
            Text("Review ${if (count == 1) "it" else "them"}", style = V4.type.bodyStrong, color = c.accentInk)
        }
    }
}

/** A suggestion learned from the user's ticks, with a yes and a no. */
@Composable
fun LearnedTipCard(text: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit, modifier: Modifier = Modifier) {
    val c = V4.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtPlate(DeckArt.LEARN, 64.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Learned from you", style = V4.type.label, color = c.accentInk)
            Text(text, style = V4.type.body, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                az.tribe.lifeplanner.ui.v4.components.V4PillButton(yes, onYes)
                az.tribe.lifeplanner.ui.v4.components.V4PillButton(no, onNo, filled = false)
            }
        }
    }
}

private fun hourNow(): Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
