package az.tribe.lifeplanner.domain.enum

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

enum class GoalFilter(val displayName: String, val icon: ImageVector) {
    ALL("All", Icons.Rounded.List),
    ACTIVE("Active", Icons.Rounded.PlayArrow),
    COMPLETED("Completed", Icons.Rounded.CheckCircle)
}
