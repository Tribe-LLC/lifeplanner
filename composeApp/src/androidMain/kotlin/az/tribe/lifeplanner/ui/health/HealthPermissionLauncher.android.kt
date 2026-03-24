package az.tribe.lifeplanner.ui.health

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import az.tribe.lifeplanner.data.health.HealthDataManager
import co.touchlab.kermit.Logger

@Composable
actual fun rememberHealthPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val contract = PermissionController.createRequestPermissionResultContract()
    val launcher = rememberLauncherForActivityResult(contract) { granted ->
        Logger.d("HealthPermission") { "Permissions granted: $granted" }
        // Accept if ANY permission was granted — we'll sync whatever we can
        onResult(granted.isNotEmpty())
    }

    return launch@{
        try {
            if (Build.VERSION.SDK_INT < 28) {
                Logger.w("HealthPermission") { "Health Connect requires Android 9+" }
                onResult(false)
                return@launch
            }

            val status = HealthConnectClient.getSdkStatus(context)
            when (status) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    launcher.launch(HealthDataManager.REQUIRED_PERMISSIONS)
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    // Health Connect needs to be installed/updated
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback to browser
                        val webIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(webIntent)
                    }
                }
                else -> {
                    Logger.w("HealthPermission") { "Health Connect not available, status: $status" }
                    onResult(false)
                }
            }
        } catch (e: Exception) {
            Logger.e("HealthPermission") { "Failed to request health permissions: ${e.message}" }
            onResult(false)
        }
    }
}
