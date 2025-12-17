package az.tribe.lifeplanner

import android.app.Application
import android.content.Context
import az.tribe.lifeplanner.di.initFileSharer
import az.tribe.lifeplanner.di.initKoin
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import dev.gitlive.firebase.perf.android
import dev.gitlive.firebase.perf.performance
import org.koin.android.ext.koin.androidContext

class MainApplication : Application() {
    companion object {
        lateinit var appContext: Context
    }

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
    }
}