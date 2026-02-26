package az.tribe.lifeplanner

import android.app.Application
import android.content.Context
import az.tribe.lifeplanner.di.initFileSharer
import az.tribe.lifeplanner.di.initKoin
import az.tribe.lifeplanner.domain.repository.BackupRepository
import az.tribe.lifeplanner.worker.BackupScheduler
import dev.gitlive.firebase.Firebase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import dev.gitlive.firebase.initialize
import dev.gitlive.firebase.perf.android
import dev.gitlive.firebase.perf.performance
import org.koin.android.ext.koin.androidContext

class MainApplication : Application(), KoinComponent {
    companion object {
        lateinit var appContext: Context
    }

    private val backupRepository: BackupRepository by inject()

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Initialize FileSharer for backup sharing
        initFileSharer(applicationContext)

        AppInitializer.onApplicationStart()

        Firebase.initialize(appContext)
        Firebase.performance.android.isPerformanceCollectionEnabled = true

        initKoin {
            androidContext(this@MainApplication)
        }

        // Initialize BackupScheduler with context
        BackupScheduler.init(applicationContext)

        // Schedule daily backup only if enabled in settings
        if (backupRepository.isAutoBackupEnabled()) {
            BackupScheduler.scheduleDailyBackup()
        }
    }
}