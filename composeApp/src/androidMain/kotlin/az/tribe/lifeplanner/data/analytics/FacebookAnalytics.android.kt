package az.tribe.lifeplanner.data.analytics

import android.os.Bundle
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import az.tribe.lifeplanner.MainApplication

actual object FacebookAnalytics {
    private val logger: AppEventsLogger by lazy {
        AppEventsLogger.newLogger(MainApplication.appContext)
    }

    actual fun logCompleteRegistration(method: String) {
        val params = Bundle().apply {
            putString(AppEventsConstants.EVENT_PARAM_REGISTRATION_METHOD, method)
        }
        logger.logEvent(AppEventsConstants.EVENT_NAME_COMPLETED_REGISTRATION, params)
    }

    actual fun logCompleteTutorial() {
        val params = Bundle().apply {
            putString(AppEventsConstants.EVENT_PARAM_SUCCESS, "1")
        }
        logger.logEvent(AppEventsConstants.EVENT_NAME_COMPLETED_TUTORIAL, params)
    }

    actual fun logViewContent(contentId: String, contentType: String) {
        val params = Bundle().apply {
            putString(AppEventsConstants.EVENT_PARAM_CONTENT_ID, contentId)
            putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, contentType)
        }
        logger.logEvent(AppEventsConstants.EVENT_NAME_VIEWED_CONTENT, params)
    }

    actual fun logAchieveLevel(level: Int) {
        val params = Bundle().apply {
            putString(AppEventsConstants.EVENT_PARAM_LEVEL, level.toString())
        }
        logger.logEvent(AppEventsConstants.EVENT_NAME_ACHIEVED_LEVEL, params)
    }

    actual fun logUnlockAchievement(description: String) {
        val params = Bundle().apply {
            putString(AppEventsConstants.EVENT_PARAM_DESCRIPTION, description)
        }
        logger.logEvent(AppEventsConstants.EVENT_NAME_UNLOCKED_ACHIEVEMENT, params)
    }

    actual fun logSearch(query: String, contentType: String) {
        val params = Bundle().apply {
            putString(AppEventsConstants.EVENT_PARAM_SEARCH_STRING, query)
            putString(AppEventsConstants.EVENT_PARAM_CONTENT_TYPE, contentType)
        }
        logger.logEvent(AppEventsConstants.EVENT_NAME_SEARCHED, params)
    }
}
