package az.tribe.lifeplanner.ui.v4.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.regular.CaretLeft

val V4CardShape = RoundedCornerShape(20.dp)
val V4RowShape = RoundedCornerShape(18.dp)

/** A white card with a hairline border: the basic container on every v4 screen. */
@Composable
fun V4Card(
    modifier: Modifier = Modifier,
    color: Color = V4.colors.surface,
    bordered: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalSpacing: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .clip(V4CardShape)
        .background(color)
        .let { if (bordered) it.border(1.dp, V4.colors.line, V4CardShape) else it }
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(contentPadding)
    Column(base, verticalArrangement = Arrangement.spacedBy(verticalSpacing), content = content)
}

/** The area label pill: the area's tint with its ink. */
@Composable
fun AreaChip(area: PlanArea, text: String = areaName(area), modifier: Modifier = Modifier) {
    val c = V4.colors.area(area)
    Text(
        text = text,
        style = V4.type.micro,
        color = c.ink,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.soft)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        maxLines = 1,
    )
}

/** A neutral pill for things that are not an area, like "Calendar" or "From Health". */
@Composable
fun NeutralChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = V4.type.micro,
        color = V4.colors.ink2,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(V4.colors.surfaceMuted)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        maxLines = 1,
    )
}

/**
 * The round tick used for every "done" in v4. 44dp, so it is a real touch target; announces its
 * state rather than relying on the fill colour.
 */
@Composable
fun CheckCircleButton(
    done: Boolean,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = V4.colors.accent,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(if (done) color else V4.colors.surface)
            .border(2.dp, color, shape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics {
                contentDescription = label
                stateDescription = if (done) "Done" else "Not done"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(PhosphorIcons.Bold.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun V4PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = V4.colors.accent,
    content: Color = V4.colors.onAccent,
) {
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (enabled) container else V4.colors.trackOff)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = V4.type.headline, color = if (enabled) content else V4.colors.ink2, textAlign = TextAlign.Center)
    }
}

@Composable
fun V4TextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = V4.colors.accentInk) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = V4.type.bodyStrong, color = color)
    }
}

/** A smaller pill button used inside cards ("Keep it", "Add to Calendar"). */
@Composable
fun V4PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    container: Color = V4.colors.accent,
    contentColor: Color = if (filled) V4.colors.onAccent else V4.colors.accentInk,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (filled) container else Color.Transparent)
            .let { if (!filled) it.border(1.5.dp, V4.colors.line, RoundedCornerShape(22.dp)) else it }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = V4.type.bodyStrong, color = contentColor, maxLines = 1)
    }
}

/** A 44dp round icon button on a card-coloured disc (avatar, back, add). */
@Composable
fun V4IconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(V4.colors.surface)
            .border(1.dp, V4.colors.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = V4.colors.ink, modifier = Modifier.size(22.dp))
    }
}

/** "‹ Life" style back link at the top of area pages. */
@Composable
fun V4BackLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Back to $label" }
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(PhosphorIcons.Regular.CaretLeft, contentDescription = null, tint = V4.colors.ink, modifier = Modifier.size(20.dp))
        Text(label, style = V4.type.bodyStrong, color = V4.colors.ink)
    }
}

@Composable
fun V4Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = V4.colors.accent,
            checkedBorderColor = V4.colors.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = V4.colors.trackOff,
            uncheckedBorderColor = V4.colors.trackOff,
        ),
    )
}

@Composable
fun V4SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = V4.type.headline, color = V4.colors.ink)
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun V4ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(V4.colors.surfaceMuted),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}

/** A tiny trend line, drawn from whatever values it is given, scaled to its own min and max. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier.width(64.dp).height(28.dp)) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - 2.dp.toPx() - (v - min) / span * (size.height - 4.dp.toPx())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 2.5.dp.toPx(), center = Offset((values.size - 1) * stepX, size.height - 2.dp.toPx() - (values.last() - min) / span * (size.height - 4.dp.toPx())))
    }
}

/** The small round brand mark (blue tile with a tick). */
@Composable
fun LifePlannerMark(size: Dp = 36.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(V4.colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PhosphorIcons.Bold.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun V4Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(V4.colors.lineSoft))
}

@Composable
fun V4Spacer(height: Dp) = Spacer(Modifier.height(height))

/** Singleline text that ellipsizes: most list rows use it. */
@Composable
fun OneLine(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

fun areaName(area: PlanArea): String = when (area) {
    PlanArea.HABITS -> "Habits"
    PlanArea.FITNESS -> "Fitness"
    PlanArea.MONEY -> "Money"
    PlanArea.TRAVEL -> "Travel"
    PlanArea.STUDY -> "Study"
    PlanArea.MEALS -> "Meals"
    PlanArea.MIND -> "Sleep and mind"
    PlanArea.CAREER -> "Career"
}

fun areaBlurb(area: PlanArea): String = when (area) {
    PlanArea.HABITS -> "Small things, every day"
    PlanArea.FITNESS -> "Workouts, steps, runs"
    PlanArea.MONEY -> "Budget and spending"
    PlanArea.TRAVEL -> "Trips, plans, packing"
    PlanArea.STUDY -> "Courses, exams, focus time"
    PlanArea.MEALS -> "What you eat and cook"
    PlanArea.MIND -> "Sleep, mood, journal"
    PlanArea.CAREER -> "Skills and next moves"
}
