package az.tribe.lifeplanner.data.review

import az.tribe.lifeplanner.data.analytics.Analytics
import co.touchlab.kermit.Logger
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindowScene

actual class InAppReviewManager {

    actual fun requestReview(trigger: String) {
        Analytics.appReviewPrompted(trigger)

        try {
            val windowScene = UIApplication.sharedApplication.connectedScenes
                .filterIsInstance<UIWindowScene>()
                .firstOrNull()

            if (windowScene != null) {
                SKStoreReviewController.requestReviewInScene(windowScene)
                Analytics.appReviewCompleted(trigger)
                Logger.i("InAppReview") { "Review requested via SKStoreReviewController for trigger: $trigger" }
            } else {
                Logger.w("InAppReview") { "No UIWindowScene found — skipping review prompt" }
            }
        } catch (e: Exception) {
            Logger.e("InAppReview") { "Failed to request review: ${e.message}" }
        }
    }
}
