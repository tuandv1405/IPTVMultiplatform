package tss.t.tsiptv.ui.provider

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

object LocalMultiPermissionProvider {
    private val localActivityResultLauncher =
        compositionLocalOf<ActivityResultLauncher<Array<String>>?> {
            null
        }

    val current: ActivityResultLauncher<Array<String>>?
        @Composable
        get() = localActivityResultLauncher.current

    infix fun provides(
        permissionLauncher: ActivityResultLauncher<Array<String>>?,
    ) = localActivityResultLauncher provides permissionLauncher
}
