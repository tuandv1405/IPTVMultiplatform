package tss.t.tsiptv

import android.app.Application
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import tss.t.tsiptv.core.network.NetworkClientFactory
import tss.t.tsiptv.di.getAndroidModules
import tss.t.tsiptv.di.getCommonModules

class TSAndroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize Firebase App Check
        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        // Use Debug provider for development, PlayIntegrity for production
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            // Debug provider allows testing without valid tokens
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            // PlayIntegrity provider for production
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

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
