package az.tribe.lifeplanner.di

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import az.tribe.lifeplanner.R
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
import androidx.core.net.toUri

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()


actual fun onApplicationStartPlatformSpecific() {
    val customNotificationSound =
        (ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + "az.tribe.lifeplanner" + "/" + R.raw.custom_notification_sound).toUri()
    NotifierManager.initialize(
        configuration = NotificationPlatformConfiguration.Android(
            notificationIconResId = R.drawable.ic_launcher_foreground,
            showPushNotification = true,
            notificationChannelData = NotificationPlatformConfiguration.Android.NotificationChannelData(
                soundUri = customNotificationSound.toString()
            )
        )
    )
}