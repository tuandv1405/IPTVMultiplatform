package tss.t.tsiptv.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModelStoreOwner

object LocalAppViewModelStoreOwner {
    private val LocalAppViewModelStoreOwner = compositionLocalOf<ViewModelStoreOwner?> { null }

    /**
     * Returns current composition local value for the owner or `null` if one has not been provided
     * nor is one available via [findViewTreeViewModelStoreOwner] on the current
     * [androidx.compose.ui.platform.LocalView].
     */
    public val current: ViewModelStoreOwner?
        @Composable get() = LocalAppViewModelStoreOwner.current ?: throw IllegalStateException("")

    /**
     * Associates a [LocalViewModelStoreOwner] key to a value in a call to
     * [CompositionLocalProvider].
     */
    public infix fun provides(
        viewModelStoreOwner: ViewModelStoreOwner,
    ): ProvidedValue<ViewModelStoreOwner?> {
        return LocalAppViewModelStoreOwner.provides(viewModelStoreOwner)
    }
}
