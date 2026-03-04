package az.tribe.lifeplanner

import az.tribe.lifeplanner.di.onApplicationStartPlatformSpecific
import co.touchlab.kermit.Logger
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.PayloadData

object AppInitializer {
    fun onApplicationStart() {
        onApplicationStartPlatformSpecific()


        NotifierManager.addListener(object : NotifierManager.Listener {
            override fun onNewToken(token: String) {
                Logger.d("AppInitializer") { "Push Notification onNewToken: $token" }
            }

            override fun onPushNotification(title: String?, body: String?) {
                super.onPushNotification(title, body)
                Logger.d("AppInitializer") { "Push Notification notification type message is received: Title: $title and Body: $body" }
            }

            override fun onPayloadData(data: PayloadData) {
                super.onPayloadData(data)
                Logger.d("AppInitializer") { "Push Notification payloadData: $data" }
            }

            override fun onNotificationClicked(data: PayloadData) {
                super.onNotificationClicked(data)
                Logger.d("AppInitializer") { "Notification clicked, Notification payloadData: $data" }
            }
        })
    }
}