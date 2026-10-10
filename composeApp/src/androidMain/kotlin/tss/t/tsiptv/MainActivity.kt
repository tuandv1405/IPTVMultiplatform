package tss.t.tsiptv

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import org.koin.core.component.KoinComponent
import tss.t.tsiptv.core.ads.AndroidAdsPlatform
import tss.t.tsiptv.core.ads.AppOpenAdController
import tss.t.tsiptv.core.language.LocalAppLocale
import tss.t.tsiptv.utils.PlatformUtils
import tss.t.tsiptv.core.network.NetworkConnectivityCheckerFactory
import tss.t.tsiptv.core.permission.PermissionCheckerFactory
import tss.t.tsiptv.ui.provider.LocalMultiPermissionProvider
import tss.t.tsiptv.ui.provider.LocalPermissionProvider

class MainActivity : ComponentActivity(), KoinComponent {
    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        PermissionCheckerFactory.onSinglePermissionResult(it)
    }

    private val multiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        PermissionCheckerFactory.onMultiplePermissionsResult(it)
    }

    // POST_NOTIFICATIONS, asked only from Profile › Notifications (docs/prd-push-notifications.md R1).
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> tss.t.tsiptv.feature.push.PushPermissionRequests.onResult(granted) }

    /** A tapped notification: its `link` extra (from our notification or FCM's data) goes through the allowlist. */
    private fun handlePushIntent(intent: android.content.Intent?) {
        val extras = intent?.extras ?: return
        val fromFcm = extras.containsKey("google.message_id")
        val link = extras.getString(tss.t.tsiptv.feature.push.AndroidPushPlatform.EXTRA_LINK)
        if (!fromFcm && link == null) return
        intent.removeExtra(tss.t.tsiptv.feature.push.AndroidPushPlatform.EXTRA_LINK)
        intent.removeExtra("google.message_id")
        runCatching { getKoin().get<tss.t.tsiptv.feature.push.PushManager>().onNotificationOpened(link) }
            .onFailure { android.util.Log.w("TSPush", "open failed: ${it::class.simpleName}") }
        if (tss.t.tsiptv.feature.push.AndroidPushPlatform.isDebuggable(this)) android.util.Log.d("TSPush", "notification opened (fcm=$fromFcm, link=${link != null})")
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePushIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        AndroidPlatformUtils.appContext = this

        PermissionCheckerFactory.create()
        PermissionCheckerFactory.initialize(
            activity = this,
            singlePermissionLauncher = permission,
            multiplePermissionsLauncher = multiplePermissions
        )

        NetworkConnectivityCheckerFactory.initialize(applicationContext as Application)

        tss.t.tsiptv.feature.push.PushPermissionRequests.launcher = notificationPermission
        // Not on a configuration change: the link was handled already.
        if (savedInstanceState == null) handlePushIntent(intent)

        // Google UMP consent (form where required), then the ads SDK if allowed. Not on TV.
        if (!PlatformUtils.platform.isTv) AndroidAdsPlatform.gatherConsent(this)

        setContent {
            val language = LocalAppLocale.current

            CompositionLocalProvider(
                LocalPermissionProvider provides permission,
                LocalMultiPermissionProvider provides multiplePermissions,
            ) {
                App()
            }
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
    }

    override fun onResume() {
        super.onResume()
        // The cold-start app open ad shows here at most once per process (PRD R2).
        AppOpenAdController.onActivityResumed(this)
    }

    override fun onPause() {
        AppOpenAdController.onActivityPaused()
        super.onPause()
    }

    override fun onDestroy() {
        if (tss.t.tsiptv.feature.push.PushPermissionRequests.launcher === notificationPermission) {
            tss.t.tsiptv.feature.push.PushPermissionRequests.launcher = null
        }
        super.onDestroy()
        LocalPermissionProvider.provides(null)
    }
}
