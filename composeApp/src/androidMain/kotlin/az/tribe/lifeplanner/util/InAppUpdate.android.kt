package az.tribe.lifeplanner.util

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

@Composable
actual fun InAppUpdateEffect(enabled: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val activity = context as? Activity ?: return@LaunchedEffect
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(context)
            val appUpdateInfoTask = appUpdateManager.appUpdateInfo
            appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    Logger.d("InAppUpdate") { "Flexible update available, starting flow" }
                    appUpdateManager.startUpdateFlow(
                        appUpdateInfo,
                        activity,
                        AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)
                    )
                } else {
                    Logger.d("InAppUpdate") { "No update available or not allowed: availability=${appUpdateInfo.updateAvailability()}" }
                }
            }.addOnFailureListener { e ->
                Logger.e("InAppUpdate", e) { "Failed to check for updates: ${e.message}" }
            }
        } catch (e: Exception) {
            Logger.e("InAppUpdate", e) { "In-app update error: ${e.message}" }
        }
    }
}
