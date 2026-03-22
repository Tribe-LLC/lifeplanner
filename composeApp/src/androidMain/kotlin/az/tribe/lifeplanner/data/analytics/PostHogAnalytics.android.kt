package az.tribe.lifeplanner.data.analytics

import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.posthog.android.replay.PostHogSessionReplayConfig
import az.tribe.lifeplanner.MainApplication

actual object PostHogAnalytics {

    actual fun setup(apiKey: String, host: String) {
        val config = PostHogAndroidConfig(
            apiKey = apiKey,
            host = host,
            captureApplicationLifecycleEvents = true,
            captureDeepLinks = true,
            captureScreenViews = true,
            sessionReplayConfig = PostHogSessionReplayConfig(
                maskAllTextInputs = true,
                maskAllImages = false,
                captureLogcat = false,
                screenshot = true
            )
        ).apply {
            personProfiles = PersonProfiles.ALWAYS
        }
        PostHogAndroid.setup(MainApplication.appContext, config)
    }

    actual fun identify(userId: String, properties: Map<String, Any>) {
        PostHog.identify(userId, properties)
    }

    actual fun reset() {
        PostHog.reset()
    }

    actual fun capture(event: String, properties: Map<String, Any>) {
        PostHog.capture(event, properties = properties)
    }

    actual fun setUserProperties(properties: Map<String, Any>) {
        PostHog.identify(PostHog.distinctId(), userProperties = properties)
    }

    actual fun screen(screenName: String, properties: Map<String, Any>) {
        PostHog.screen(screenName, properties)
    }

    actual fun isFeatureEnabled(flag: String): Boolean {
        return PostHog.isFeatureEnabled(flag)
    }

    actual fun getFeatureFlag(flag: String): Any? {
        return PostHog.getFeatureFlagPayload(flag)
    }

    actual fun reloadFeatureFlags() {
        PostHog.reloadFeatureFlags()
    }

    actual fun group(type: String, key: String, properties: Map<String, Any>) {
        PostHog.group(type, key, properties)
    }

    actual fun flush() {
        PostHog.flush()
    }
}
