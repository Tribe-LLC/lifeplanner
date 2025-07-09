package az.tribe.lifeplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.ui.theme.modernColors


@Composable
fun StreakCounter(
    streak: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RectangleShape
            )
            .background(MaterialTheme.modernColors.accent.copy(alpha = 0.1f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Current Streak",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.modernColors.textPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "$streak",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.modernColors.accent,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "day${if (streak != 1) "s" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.modernColors.textSecondary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Progress indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Mid-term indicator (7 days)
            MilestoneIndicator(
                current = streak.coerceAtMost(7),
                max = 7,
                label = "Mid-Term"
            )
            
            // Long-term indicator (30 days)
            MilestoneIndicator(
                current = streak.coerceAtMost(30),
                max = 30,
                label = "Long-Term"
            )
        }
    }
}

@Composable
fun MilestoneIndicator(
    current: Int,
    max: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.modernColors.textSecondary
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "$current/$max",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.modernColors.textPrimary
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = { current.toFloat() / max },
            modifier = Modifier.width(80.dp),
            color = MaterialTheme.modernColors.accent,
            trackColor = MaterialTheme.modernColors.accent.copy(alpha = 0.2f)
        )
    }
}