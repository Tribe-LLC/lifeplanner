package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.util.getStoreUrl
import az.tribe.lifeplanner.util.isVersionBelow
import co.touchlab.kermit.Logger

enum class UpdateMode { FORCE, SOFT }

sealed class UpdateState {
    data object UpToDate : UpdateState()
    data class UpdateRequired(
        val storeUrl: String,
        val mode: UpdateMode,
        val minVersion: String
    ) : UpdateState()
}

object ForceUpdateChecker {

    private const val FLAG_KEY = "force_update_min_version"

    /**
     * Check the PostHog feature flag to determine if the app needs updating.
     *
     * Expected flag payload (JSON): {"min_version": "2.2", "mode": "force"}
     * - mode: "force" (blocking) or "soft" (dismissible)
     */
    fun check(): UpdateState {
        return try {
            if (!PostHogAnalytics.isFeatureEnabled(FLAG_KEY)) {
                return UpdateState.UpToDate
            }

            val payload = PostHogAnalytics.getFeatureFlag(FLAG_KEY)
            Logger.d("ForceUpdateChecker") { "Flag payload: $payload" }

            val map = when (payload) {
                is Map<*, *> -> payload
                else -> {
                    Logger.w("ForceUpdateChecker") { "Unexpected payload type: ${payload?.let { it::class }}" }
                    return UpdateState.UpToDate
                }
            }

            val minVersion = map["min_version"]?.toString() ?: return UpdateState.UpToDate
            val modeStr = map["mode"]?.toString() ?: "soft"
            val mode = if (modeStr.equals("force", ignoreCase = true)) UpdateMode.FORCE else UpdateMode.SOFT
            val currentVersion = BuildKonfig.APP_VERSION

            Logger.d("ForceUpdateChecker") { "currentVersion=$currentVersion, minVersion=$minVersion, mode=$mode" }

            if (isVersionBelow(currentVersion, minVersion)) {
                UpdateState.UpdateRequired(
                    storeUrl = getStoreUrl(),
                    mode = mode,
                    minVersion = minVersion
                )
            } else {
                UpdateState.UpToDate
            }
        } catch (e: Exception) {
            Logger.e("ForceUpdateChecker", e) { "Failed to check force update: ${e.message}" }
            UpdateState.UpToDate
        }
    }
}
