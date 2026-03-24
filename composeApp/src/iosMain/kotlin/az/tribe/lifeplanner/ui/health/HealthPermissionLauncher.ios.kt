package az.tribe.lifeplanner.ui.health

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import az.tribe.lifeplanner.data.health.HealthDataManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
actual fun rememberHealthPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
    val healthDataManager = koinInject<HealthDataManager>()
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            // On iOS, hasPermissions() calls requestAuthorization() which shows the system dialog
            val granted = healthDataManager.hasPermissions()
            onResult(granted)
        }
    }
}
