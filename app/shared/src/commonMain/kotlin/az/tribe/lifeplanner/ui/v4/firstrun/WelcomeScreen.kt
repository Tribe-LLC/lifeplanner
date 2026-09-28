package az.tribe.lifeplanner.ui.v4.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.ui.v4.components.LifePlannerMark
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4

/**
 * v4's front door. "Get started" starts a guest session and goes to the area picker, so a new user
 * is planning inside a minute with no account; "I already use LifePlanner" goes to sign-in.
 */
@Composable
fun WelcomeScreen(
    busy: Boolean,
    onGetStarted: () -> Unit,
    onHaveAccount: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(V4.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LifePlannerMark()
            Text("LifePlanner", style = V4.type.headline.copy(fontSize = V4.type.title.fontSize * 0.77f), color = V4.colors.ink)
        }
        AreaCollage()
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("One planner for every part of your life.", style = V4.type.display.copy(fontSize = V4.type.display.fontSize * 1.15f, lineHeight = V4.type.display.lineHeight * 1.1f), color = V4.colors.ink)
            Text(
                "Habits, workouts, money, trips, study and more. Pick what matters to you. The rest stays out of your way.",
                style = V4.type.body.copy(fontSize = V4.type.headline.fontSize, lineHeight = V4.type.body.lineHeight * 1.15f),
                color = V4.colors.ink2,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = V4.colors.accent)
                }
            } else {
                V4PrimaryButton("Get started", onClick = onGetStarted, modifier = Modifier.fillMaxWidth())
            }
            V4TextButton("I already use LifePlanner", onClick = onHaveAccount, modifier = Modifier.fillMaxWidth())
            Text(
                "No account needed to start.",
                style = V4.type.caption,
                color = V4.colors.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AreaCollage() {
    val areas = PlanArea.entries
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        areas.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { area ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(78.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(V4.colors.surface)
                            .border(1.dp, V4.colors.line, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AreaIllustration(area, size = 62.dp)
                    }
                }
            }
        }
    }
}
