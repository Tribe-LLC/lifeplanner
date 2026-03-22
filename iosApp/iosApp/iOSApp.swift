import SwiftUI
import FirebaseCore
//import FirebasePerformance
import FirebaseMessaging
import FBSDKCoreKit
import AppTrackingTransparency
import ComposeApp
import PostHog
import WidgetKit


class SwiftFacebookAnalyticsBridge: FacebookAnalyticsBridge {
    func logEvent(eventName: String, parameters: [String: String]) {
        var fbParams: [AppEvents.ParameterName: Any] = [:]
        for (key, value) in parameters {
            fbParams[AppEvents.ParameterName(key)] = value
        }
        AppEvents.shared.logEvent(AppEvents.Name(eventName), parameters: fbParams)
    }
}

class SwiftPostHogBridge: PostHogBridge {
    func setup(apiKey: String, host: String) {
        let config = PostHogConfig(apiKey: apiKey, host: host)
        config.personProfiles = .always
        config.captureApplicationLifecycleEvents = true
        config.captureScreenViews = true
        config.sessionReplay = true
        config.sessionReplayConfig.maskAllTextInputs = true
        config.sessionReplayConfig.maskAllImages = false
        config.sessionReplayConfig.screenshotMode = true
        PostHogSDK.shared.setup(config)
    }
    func identify(userId: String, properties: [String: Any]) {
        PostHogSDK.shared.identify(userId, userProperties: properties)
    }
    func reset() {
        PostHogSDK.shared.reset()
    }
    func capture(event: String, properties: [String: Any]) {
        PostHogSDK.shared.capture(event, properties: properties)
    }
    func screen(screenName: String, properties: [String: Any]) {
        PostHogSDK.shared.screen(screenName, properties: properties)
    }
    func isFeatureEnabled(flag: String) -> Bool {
        return PostHogSDK.shared.isFeatureEnabled(flag)
    }
    func getFeatureFlag(flag: String) -> Any? {
        return PostHogSDK.shared.getFeatureFlagPayload(flag)
    }
    func reloadFeatureFlags() {
        PostHogSDK.shared.reloadFeatureFlags()
    }
    func group(type: String, key: String, properties: [String: Any]) {
        PostHogSDK.shared.group(type: type, key: key, groupProperties: properties)
    }
    func flush() {
        PostHogSDK.shared.flush()
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        FirebaseApp.configure()
        Settings.shared.isAutoLogAppEventsEnabled = true
        Settings.shared.isAdvertiserIDCollectionEnabled = true
        ApplicationDelegate.shared.application(application, didFinishLaunchingWithOptions: launchOptions)

        // Bridge Facebook events from KMP to iOS SDK
        FacebookAnalytics.shared.bridge = SwiftFacebookAnalyticsBridge()

        // Bridge PostHog events from KMP to iOS SDK
        PostHogAnalytics.shared.bridge = SwiftPostHogBridge()

        AppInitializer.shared.onApplicationStart()
//        let _ = Performance.sharedInstance()

        // Register background backup task
        IOSBackupScheduler.shared.registerBackgroundTask()

        // Observe widget refresh notifications from KMP
        NotificationCenter.default.addObserver(
            forName: NSNotification.Name("az.tribe.lifeplanner.refreshWidgets"),
            object: nil,
            queue: .main
        ) { _ in
            if #available(iOS 14.0, *) {
                WidgetCenter.shared.reloadAllTimelines()
            }
        }

        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // Schedule daily backup when app goes to background (only if enabled)
        if IOSBackupScheduler.shared.isAutoBackupEnabled() {
            IOSBackupScheduler.shared.scheduleDailyBackup()
        }
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }


    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable : Any]) async -> UIBackgroundFetchResult {
        NotifierManager.shared.onApplicationDidReceiveRemoteNotification(userInfo: userInfo)
        return UIBackgroundFetchResult.newData
    }

}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
                    if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
                        ATTrackingManager.requestTrackingAuthorization { _ in }
                    }
                }
                .onOpenURL { url in
                    if url.scheme == "lifeplanner" && url.host == "promo" {
                        // Marketing deep links handled by ComposeApp navigation
                    } else {
                        // Dispatch async to let the app finish launching before handling auth deep links
                        DispatchQueue.main.async {
                            DeepLinkHandler().handle(url: url)
                        }
                    }
                }
        }
    }
}
