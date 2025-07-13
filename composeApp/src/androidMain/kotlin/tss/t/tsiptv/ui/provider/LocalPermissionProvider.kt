package tss.t.tsiptv.ui.provider

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

object LocalPermissionProvider {
    private val localActivityResultLauncher = compositionLocalOf<ActivityResultLauncher<String>?> {
        null
    }

    val current: ActivityResultLauncher<String>?
        @Composable
        get() = localActivityResultLauncher.current

    infix fun provides(
        permissionLauncher: ActivityResultLauncher<String>?,
    ) = localActivityResultLauncher provides permissionLauncher
}
