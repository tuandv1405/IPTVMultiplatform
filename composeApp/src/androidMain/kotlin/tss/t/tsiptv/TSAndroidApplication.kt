package tss.t.tsiptv

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import tss.t.tsiptv.di.getAndroidModules
import tss.t.tsiptv.di.getCommonModules

class TSAndroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Koin
        startKoin {
            androidLogger()
            androidContext(this@TSAndroidApplication)
            modules(getCommonModules() + getAndroidModules())
        }
    }

    companion object {
        lateinit var instance: TSAndroidApplication
    }
}
