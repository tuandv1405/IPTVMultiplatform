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
import org.koin.core.context.GlobalContext
import tss.t.tsiptv.core.ads.AdsGate
import tss.t.tsiptv.core.ads.AndroidAdsPlatform
import tss.t.tsiptv.core.ads.AppOpenAdController
import tss.t.tsiptv.core.network.NetworkClientFactory
import tss.t.tsiptv.utils.PlatformUtils
import tss.t.tsiptv.di.getAndroidModules
import tss.t.tsiptv.di.getCommonModules

class TSAndroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        tss.t.tsiptv.core.ads.AdsLog.enabled = debuggable
        if (!PlatformUtils.platform.isTv) {
            // First thing: UMP starts reading the consent stored by the previous session in the
            // background while the rest of the app initialises (PRD R2: app open ad in time).
            AppOpenAdController.onProcessStart(
                debugTimeoutMs = if (debuggable) resources.getInteger(R.integer.debug_app_open_timeout_ms).toLong() else 0L
            )
            AndroidAdsPlatform.onApplicationCreate(this)
        }

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

        if (isDebuggable) {
            tss.t.tsiptv.utils.DebugFlags.skipLogin = resources.getBoolean(R.bool.debug_skip_login)
        }

        // Ads (docs/prd-admob.md). Not on Android TV (PRD §3). The platform was set up first
        // (above); AdsGate reads the install time and debug flags from it.
        if (!PlatformUtils.platform.isTv) {
            GlobalContext.get().get<AdsGate>() // starts the 24 h clock
        }

        // Push notifications (docs/prd-push-notifications.md): channels first, then the manager
        // (settings, topics, token). Also on TV: messages are handled there without being shown.
        tss.t.tsiptv.feature.push.AndroidPushPlatform.createChannels(this)
        GlobalContext.get().get<tss.t.tsiptv.feature.push.PushManager>().start()
    }

    companion object {
        lateinit var instance: TSAndroidApplication
    }
}
