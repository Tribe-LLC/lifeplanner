package az.tribe.lifeplanner

import android.app.Application
import android.content.Context
import az.tribe.lifeplanner.di.initFileSharer
import az.tribe.lifeplanner.di.initKoin
import az.tribe.lifeplanner.domain.repository.BackupRepository
import az.tribe.lifeplanner.domain.repository.ReminderRepository
import az.tribe.lifeplanner.notification.AndroidNotificationScheduler
import az.tribe.lifeplanner.worker.BackupScheduler
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.gitlive.firebase.Firebase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import dev.gitlive.firebase.initialize
import dev.gitlive.firebase.perf.android
import dev.gitlive.firebase.perf.performance
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import org.koin.android.ext.koin.androidContext

open class MainApplication : Application(), KoinComponent {
    companion object {
        lateinit var appContext: Context
    }

    private val backupRepository: BackupRepository by inject()
    private val reminderRepository: ReminderRepository by inject()

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Initialize FileSharer for backup sharing
        initFileSharer(applicationContext)

        AppInitializer.onApplicationStart()

        try {
            Firebase.initialize(appContext)
            Firebase.performance.android.isPerformanceCollectionEnabled = true
        } catch (e: Exception) {
            Logger.w("MainApplication") { "Firebase init skipped (no Play Services?): ${e.message}" }
        }

        // PostHog product analytics
        Logger.i("PostHog") { "API key present: ${BuildKonfig.POSTHOG_API_KEY.isNotBlank()}, host: ${BuildKonfig.POSTHOG_HOST}" }
        if (BuildKonfig.POSTHOG_API_KEY.isNotBlank()) {
            PostHogAnalytics.setup(BuildKonfig.POSTHOG_API_KEY, BuildKonfig.POSTHOG_HOST)
            Logger.i("PostHog") { "PostHog initialized with session replay" }
        } else {
            Logger.w("PostHog") { "PostHog API key is empty, skipping init" }
        }

        // RevenueCat. Without a key nothing is configured, PremiumGate stays open and the Plus row
        // on You hides itself, so a build without billing keys behaves exactly like a free app.
        if (BuildKonfig.REVENUECAT_ANDROID_API_KEY.isNotBlank() && !Purchases.isConfigured) {
            Purchases.logLevel = if (BuildKonfig.isDebug) LogLevel.DEBUG else LogLevel.INFO
            Purchases.configure(PurchasesConfiguration(apiKey = BuildKonfig.REVENUECAT_ANDROID_API_KEY))
            Logger.i("RevenueCat") { "Purchases configured (Android)" }
        } else {
            Logger.w("RevenueCat") { "No RevenueCat key, billing off" }
        }

        initKoin {
            androidContext(this@MainApplication)
        }

        // Initialize notification scheduler with context
        AndroidNotificationScheduler.init(applicationContext)

        // Reschedule all enabled reminders (recovers from force-close)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = reminderRepository.getEnabledReminders()
                reminders.forEach { AndroidNotificationScheduler.schedule(it) }
                Logger.i("MainApplication") { "Rescheduled ${reminders.size} reminders on app start" }
            } catch (e: Exception) {
                Logger.e("MainApplication") { "Failed to reschedule reminders: ${e.message}" }
            }
        }

        // Initialize BackupScheduler with context
        BackupScheduler.init(applicationContext)

        // Schedule daily backup only if enabled in settings
        if (backupRepository.isAutoBackupEnabled()) {
            BackupScheduler.scheduleDailyBackup()
        }
    }
}