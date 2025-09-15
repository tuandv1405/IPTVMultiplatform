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
import tss.t.tsiptv.core.language.LocalAppLocale
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

    override fun onDestroy() {
        super.onDestroy()
        LocalPermissionProvider.provides(null)
    }
}
