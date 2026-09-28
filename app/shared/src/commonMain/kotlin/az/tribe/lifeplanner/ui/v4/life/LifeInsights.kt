package az.tribe.lifeplanner.ui.v4.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.domain.service.DayFacts
import az.tribe.lifeplanner.domain.service.LifeFactsMath
import az.tribe.lifeplanner.domain.service.MindCheckIns
import az.tribe.lifeplanner.domain.service.Pattern
import az.tribe.lifeplanner.domain.service.WeekSummary
import az.tribe.lifeplanner.ui.v4.areas.AreaSheet
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.travel.TravelField
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** "What moves your days": the strongest pattern big, the next two small. Before there is enough, how long until there is. */
@Composable
fun PatternsCard(patterns: List<Pattern>, daysUntil: Int, modifier: Modifier = Modifier) {
    val c = V4.colors
    V4Card(modifier.fillMaxWidth(), color = c.ink, bordered = false) {
        Text("WHAT MOVES YOUR DAYS", style = V4.type.label.copy(letterSpacing = 0.6.sp), color = c.accentSoft)
        if (patterns.isEmpty()) {
            Text(
                if (daysUntil > 0) "$daysUntil more ${if (daysUntil == 1) "day" else "days"} until your first pattern"
                else "No clear pattern yet",
                style = V4.type.display.copy(fontSize = 22.sp, lineHeight = 26.sp), color = c.background,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Keep ticking habits and checking in. After two weeks the app shows what lifts your days, like sleep, walks or cooking at home.",
                style = V4.type.body, color = c.background.copy(alpha = 0.8f),
            )
        } else {
            Text(patterns.first().text, style = V4.type.display.copy(fontSize = 22.sp, lineHeight = 26.sp), color = c.background, modifier = Modifier.semantics { heading() })
            val rest = patterns.drop(1)
            Text(
                "From ${patterns.first().days}+ days." + if (rest.isNotEmpty()) " Also: " + rest.joinToString(" ") { it.text } else "",
                style = V4.type.caption, color = c.background.copy(alpha = 0.78f),
            )
        }
    }
}

/** The Sunday review: the week in numbers, how it felt, and one thing to change. */
@Composable
fun WeekReviewCard(w: WeekSummary, onSave: (Int?, String) -> Unit, onAskCoach: () -> Unit, modifier: Modifier = Modifier) {
    val c = V4.colors
    var mood by rememberSaveable { mutableStateOf<Int?>(null) }
    var change by rememberSaveable { mutableStateOf("") }
    V4Card(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("YOUR WEEK", style = V4.type.label.copy(letterSpacing = 0.6.sp), color = c.accentInk)
            Text("2 minutes", style = V4.type.caption, color = c.ink3)
        }
        Text(
            w.bestDay?.let { "Best day: ${LifeFactsMath.dayName(it.date.dayOfWeek)}, ${it.habitsKept} of ${it.habitsDue} kept" } ?: "A week to look back on",
            style = V4.type.display.copy(fontSize = 20.sp, lineHeight = 24.sp), color = c.ink, modifier = Modifier.semantics { heading() },
        )
        Text(weekLine(w), style = V4.type.body, color = c.ink2)
        Text("How was the week?", style = V4.type.bodyStrong, color = c.ink)
        Faces(mood) { mood = it }
        TravelField(change, { change = it }, "Less phone after 22:00", "One thing to change next week")
        V4PrimaryButton("Save the week", onClick = { onSave(mood, change) }, modifier = Modifier.fillMaxWidth(), enabled = mood != null || change.isNotBlank())
        V4TextButton("Ask the coach for a note", onClick = onAskCoach, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

private fun weekLine(w: WeekSummary): String = buildList {
    w.keptPct?.let { p ->
        val d = w.keptPctBefore?.let { p - it }
        add("Habits $p%" + when { d == null || d == 0 -> ""; d > 0 -> " (up $d)"; else -> " (down ${-d})" })
    }
    if (w.workouts > 0) add("${w.workouts} ${if (w.workouts == 1) "workout" else "workouts"}")
    if (w.studyMin > 0) add("${LifeFactsMath.minutes(w.studyMin)} of study")
    w.sleepAvg?.let { add("sleep ${LifeFactsMath.hours(it)} on average") }
    w.moodAvg?.let { add("mood ${LifeFactsMath.oneDecimal(it)} of 5") }
}.joinToString(", ").replaceFirstChar { it.uppercase() }.let { if (it.isEmpty()) "Not much recorded this week, and that is fine." else "$it." }

@Composable
private fun Faces(picked: Int?, onPick: (Int) -> Unit) {
    val c = V4.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..5).forEach { score ->
            val on = picked == score
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(c.surface).border(2.dp, if (on) c.accent else c.line, CircleShape)
                    .clickable(role = Role.Button) { onPick(score) }
                    .semantics { contentDescription = MindCheckIns.label(score); selected = on },
                contentAlignment = Alignment.Center,
            ) { Text(listOf("😞", "😕", "😐", "🙂", "😄")[score - 1], fontSize = 24.sp) }
        }
    }
}

/** This month as a grid, coloured by how much was kept; tap a day to see it. */
@Composable
fun MonthCard(name: String, days: List<DayFacts?>, today: LocalDate?, currency: String?, modifier: Modifier = Modifier) {
    val c = V4.colors
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    V4Card(modifier.fillMaxWidth()) {
        Text(name, style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, style = V4.type.micro, color = c.ink3, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 7).forEach { i ->
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (d != null) {
                            val future = today != null && d.date > today
                            val share = d.keptShare
                            val bg = when {
                                future -> Color.Transparent
                                share == null -> if (d.hasData) c.accentSoft.copy(alpha = 0.5f) else c.surfaceMuted
                                else -> c.accent.copy(alpha = (0.15f + 0.85f * share.toFloat()).coerceIn(0.15f, 1f))
                            }
                            val strong = share != null && share >= 0.6 && !future
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                                    .let {
                                        when {
                                            d.date == today -> it.border(2.dp, c.ink, RoundedCornerShape(10.dp))
                                            future -> it.border(1.dp, c.line, RoundedCornerShape(10.dp))
                                            else -> it
                                        }
                                    }
                                    .clickable(role = Role.Button, enabled = !future) { open = d.date.toString() }
                                    .semantics { contentDescription = "${d.date.day}, " + (share?.let { "${(it * 100).roundToInt()}% kept" } ?: "nothing due") },
                                contentAlignment = Alignment.Center,
                            ) { Text("${d.date.day}", style = V4.type.micro.copy(fontSize = 12.sp), color = when { strong -> Color.White; future -> c.ink3; else -> c.ink }) }
                        }
                    }
                }
            }
        }
        Text("The stronger the colour, the more you kept. Tap a day to see it.", style = V4.type.caption, color = c.ink3)
    }
    open?.let { key ->
        val d = days.firstOrNull { it?.date?.toString() == key }
        if (d != null) DaySheet(d, currency) { open = null }
    }
}

@Composable
private fun DaySheet(d: DayFacts, currency: String?, onDismiss: () -> Unit) {
    val c = V4.colors
    val title = "${LifeFactsMath.dayName(d.date.dayOfWeek)} ${d.date.day} ${d.date.month.name.lowercase().replaceFirstChar { it.uppercase() }}"
    AreaSheet(title, onDismiss) {
        val lines = buildList {
            if (d.habitsDue > 0) add("Habits" to "${d.habitsKept} of ${d.habitsDue}" + if (d.keptNames.isNotEmpty()) ": ${d.keptNames.take(6).joinToString(", ")}" else "")
            if (d.workouts > 0) add("Workouts" to "${d.workouts}")
            d.steps?.let { add("Steps" to "${it.roundToInt()}") }
            if (d.spent > 0) add("Spent" to MoneyFormat.format(d.spent, currency))
            if (d.meals > 0) add("Meals" to "${d.meals}" + if (d.mealsOut > 0) ", ${d.mealsOut} out" else "")
            if (d.studyMin > 0) add("Study" to LifeFactsMath.minutes(d.studyMin))
            d.mood?.let { add("Mood" to MindCheckIns.label(it.roundToInt())) }
            d.sleep?.let { add("Sleep" to LifeFactsMath.hours(it)) }
        }
        if (lines.isEmpty()) Text("Nothing recorded this day.", style = V4.type.body, color = c.ink2)
        lines.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(k, style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.weight(0.3f))
                Text(v, style = V4.type.body, color = c.ink2, modifier = Modifier.weight(0.7f))
            }
        }
    }
}
