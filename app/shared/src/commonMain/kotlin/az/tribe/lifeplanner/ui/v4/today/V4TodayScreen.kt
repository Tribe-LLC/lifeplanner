package az.tribe.lifeplanner.ui.v4.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import az.tribe.lifeplanner.domain.service.HabitLearning
import az.tribe.lifeplanner.ui.v4.habits.CheckInHero
import az.tribe.lifeplanner.ui.v4.habits.LearnedTipCard
import az.tribe.lifeplanner.ui.v4.habits.SlippedCard
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.CaretRight
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import az.tribe.lifeplanner.ui.v4.components.AreaChip
import az.tribe.lifeplanner.ui.v4.components.CheckCircleButton
import az.tribe.lifeplanner.ui.v4.components.NeutralChip
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4IconButton
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4RowShape
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.ChatCircle
import com.adamglin.phosphoricons.regular.CalendarBlank
import com.adamglin.phosphoricons.regular.User
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun V4TodayScreen(
    onOpenYou: () -> Unit,
    onAskCoach: (String) -> Unit,
    onOpenItem: (DayItem) -> Unit,
    onCheckIn: () -> Unit,
    onReview: () -> Unit,
    bottomInset: PaddingValues,
    viewModel: V4TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var toggled by rememberSaveable { mutableStateOf(listOf<String>()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var first = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!first) viewModel.refresh()
                first = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(V4.colors.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = bottomInset.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(longDate(state.date), style = V4.type.label.copy(fontSize = V4.type.body.fontSize * 0.93f), color = V4.colors.ink3)
                    Text("Today", style = V4.type.display, color = V4.colors.ink, modifier = Modifier.semantics { heading() })
                }
                V4IconButton(PhosphorIcons.Regular.User, "You and connected apps", onOpenYou)
            }
        }
        if (state.chips.isNotEmpty()) {
            item(key = "chips") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.chips.forEach { TopChip(it) }
                }
            }
        }
        state.nudge?.let { nudge ->
            item(key = "coach") {
                CoachCard(
                    nudge = nudge,
                    onPrimary = { viewModel.onNudgePrimary(nudge, onAskCoach) },
                    onSecondary = { viewModel.onNudgeSecondary(nudge) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (state.busy && state.habitsLeft >= V4TodayViewModel.CHECK_IN_AT) {
            item(key = "checkin") { CheckInHero(state.habitsLeft, onCheckIn, Modifier.padding(top = 8.dp)) }
        }
        if (state.slipped > 0) {
            item(key = "slipped") { SlippedCard(state.slipped, onReview, Modifier.padding(top = 4.dp)) }
        }
        state.tip?.let { tip ->
            item(key = "tip") {
                LearnedTipCard(tip.text, "Move it", "Keep it", onYes = { viewModel.acceptTip(tip) }, onNo = { viewModel.declineTip(tip) }, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item(key = "your_day") {
            Text(
                "Your day",
                style = V4.type.headline,
                color = V4.colors.ink,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp).semantics { heading() },
            )
        }
        if (state.loaded && state.items.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nothing here yet. Add a habit or a plan with the box below, and it shows up on the day it is due.",
                    style = V4.type.body,
                    color = V4.colors.ink2,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        if (state.busy) {
            state.groups.forEach { g ->
                val open = (g.id in toggled) != g.openByDefault
                item(key = "group_${g.id}") {
                    GroupHeader(g, open, onToggle = { toggled = if (g.id in toggled) toggled - g.id else toggled + g.id }, modifier = Modifier.animateItem())
                }
                if (open) {
                    items(g.items, key = { it.key }) { item ->
                        SwipeableDayRow(item, viewModel, onOpenItem, Modifier.animateItem())
                    }
                }
            }
        } else {
            items(state.items, key = { it.key }) { item ->
                SwipeableDayRow(item, viewModel, onOpenItem, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun GroupHeader(g: DayGroup, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = V4.colors
    val count = if (g.id == "done") g.items.size else g.items.count { !it.done }
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = if (open) "Collapse" else "Expand", onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(g.title, style = V4.type.headline, color = c.ink)
            g.sub?.let { Text(it, style = V4.type.caption, color = c.ink3) }
        }
        Text(
            "$count",
            style = V4.type.label,
            color = c.ink2,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c.surfaceMuted).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Icon(
            if (open) PhosphorIcons.Regular.CaretDown else PhosphorIcons.Regular.CaretRight,
            contentDescription = null, tint = c.ink2, modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * A day row that can be swiped: right ticks it (or unticks a done one), left on a habit means
 * "not today", a skip that never breaks the streak. The tick button still works for anyone who
 * does not swipe, and both are also offered as accessibility actions.
 */
@Composable
private fun SwipeableDayRow(item: DayItem, viewModel: V4TodayViewModel, onOpenItem: (DayItem) -> Unit, modifier: Modifier = Modifier) {
    val c = V4.colors
    if (!item.checkable) {
        DayRow(item, onToggle = {}, onOpen = { onOpenItem(item) }, modifier = modifier)
        return
    }
    val canSkip = item.type == DayItemType.HABIT && !item.done
    val scope = rememberCoroutineScope()
    val offset = remember(item.key, item.done) { Animatable(0f) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = constraints.maxWidth.toFloat()
        val x = offset.value
        if (x != 0f) {
            val right = x > 0
            Box(
                Modifier.matchParentSize().clip(V4RowShape).background(if (right) c.success else c.surfaceMuted).padding(horizontal = 20.dp),
                contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Text(
                    if (right) (if (item.done) "Undo" else "Done") else "Not today",
                    style = V4.type.bodyStrong,
                    color = if (right) Color.White else c.ink,
                )
            }
        }
        DayRow(
            item,
            onToggle = { viewModel.toggle(item) },
            onOpen = { onOpenItem(item) },
            modifier = Modifier
                .graphicsLayer { translationX = x }
                .semantics {
                    if (canSkip) customActions = listOf(CustomAccessibilityAction("Not today") { viewModel.notToday(item); true })
                }
                .pointerInput(item.key, item.done) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val v = offset.value
                            scope.launch {
                                when {
                                    v > w * 0.3f -> { offset.animateTo(w, tween(160)); viewModel.toggle(item); offset.snapTo(0f) }
                                    v < -w * 0.3f && canSkip -> { offset.animateTo(-w, tween(160)); viewModel.notToday(item) }
                                    else -> offset.animateTo(0f, tween(180))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, tween(180)) } },
                    ) { change, dx ->
                        change.consume()
                        val next = offset.value + dx
                        scope.launch { offset.snapTo(if (canSkip) next else next.coerceAtLeast(0f)) }
                    }
                },
        )
    }
}

@Composable
private fun TopChip(chip: TodayChip) {
    val (bg, fg) = if (chip.area == null) V4.colors.inverse to V4.colors.onInverse
    else V4.colors.area(chip.area).soft to V4.colors.area(chip.area).ink
    Text(
        chip.text,
        style = V4.type.label,
        color = fg,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun CoachCard(nudge: CoachNudge, onPrimary: () -> Unit, onSecondary: () -> Unit, modifier: Modifier = Modifier) {
    val c = V4.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.accentSoft).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Fill.ChatCircle, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Coach", style = V4.type.label, color = c.accentInk)
                Text(nudge.text, style = V4.type.body, color = c.ink)
            }
        }
        if (nudge.primary != null || nudge.secondary != null) {
            Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                nudge.primary?.let { V4PillButton(it, onPrimary) }
                nudge.secondary?.let { V4PillButton(it, onSecondary, filled = false) }
            }
        }
    }
}

@Composable
private fun DayRow(item: DayItem, onToggle: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = V4.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(V4RowShape)
            .background(c.surface)
            .border(1.dp, c.line, V4RowShape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 6.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            when {
                item.allDay -> "All day"
                item.time != null -> "${item.time.hour.toString().padStart(2, '0')}:${item.time.minute.toString().padStart(2, '0')}"
                item.usualMinute != null -> "~" + HabitLearning.roughly(item.usualMinute)
                else -> "Any"
            },
            style = V4.type.label,
            color = c.ink2,
            textAlign = TextAlign.Center,
            maxLines = 1,
            // Wide enough for a learned "~08:00" on one line.
            modifier = Modifier.width(54.dp),
        )
        if (item.area == null) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceMuted), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Regular.CalendarBlank, contentDescription = null, tint = c.ink2, modifier = Modifier.size(20.dp))
            }
        } else {
            AreaIllustration(item.area, size = 40.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OneLine(item.title, V4.type.bodyStrong.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), if (item.done) c.ink3 else c.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.area != null) AreaChip(item.area) else NeutralChip("Calendar")
                OneLine(item.meta, V4.type.caption.copy(fontSize = V4.type.micro.fontSize), c.ink3, Modifier.weight(1f, fill = false))
            }
        }
        if (item.checkable) {
            CheckCircleButton(
                done = item.done,
                label = (if (item.done) "Undo: " else "Mark done: ") + item.title,
                onToggle = onToggle,
            )
        } else {
            Spacer(Modifier.size(44.dp))
        }
    }
}

private val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

internal fun longDate(d: LocalDate) = "${weekdays[d.dayOfWeek.ordinal]}, ${d.day} ${months[d.month.ordinal]}"
