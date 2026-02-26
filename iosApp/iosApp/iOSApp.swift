import SwiftUI
import FirebaseCore
//import FirebasePerformance
import FirebaseMessaging
import ComposeApp
import WidgetKit


class AppDelegate: NSObject, UIApplicationDelegate {

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        FirebaseApp.configure()
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
        }
    }
}
