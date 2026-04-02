package az.tribe.lifeplanner.data.review

import android.app.Activity
import az.tribe.lifeplanner.MainApplication
import az.tribe.lifeplanner.data.analytics.Analytics
import co.touchlab.kermit.Logger
import com.google.android.play.core.review.ReviewManagerFactory

actual class InAppReviewManager {

    actual fun requestReview(trigger: String) {
        val context = MainApplication.appContext
        val activity = findActivity(context)
        if (activity == null) {
            Logger.w("InAppReview") { "No activity found — skipping review prompt" }
            return
        }

        Analytics.appReviewPrompted(trigger)

        val manager = ReviewManagerFactory.create(context)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    Analytics.appReviewCompleted(trigger)
                    Logger.i("InAppReview") { "Review flow completed for trigger: $trigger" }
                }
            } else {
                Logger.e("InAppReview") { "Review request failed: ${task.exception?.message}" }
            }
        }
    }

    private fun findActivity(context: android.content.Context): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
