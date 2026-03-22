package az.tribe.lifeplanner.di

import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import co.touchlab.kermit.Logger
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()


actual fun onApplicationStartPlatformSpecific() {
    NotifierManager.initialize(
        NotificationPlatformConfiguration.Ios(
            showPushNotification = true,
            askNotificationPermissionOnStart = false,
            notificationSoundName = "custom_notification_sound.wav"
        )
    )

    // PostHog product analytics
    if (BuildKonfig.POSTHOG_API_KEY.isNotBlank()) {
        PostHogAnalytics.setup(BuildKonfig.POSTHOG_API_KEY, BuildKonfig.POSTHOG_HOST)
        Logger.i("PostHog") { "PostHog initialized on iOS with session replay" }
    } else {
        Logger.w("PostHog") { "PostHog API key is empty — skipping init on iOS" }
    }
}