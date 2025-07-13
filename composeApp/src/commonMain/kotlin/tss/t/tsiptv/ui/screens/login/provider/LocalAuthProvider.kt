package tss.t.tsiptv.ui.screens.login.provider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import org.koin.compose.koinInject
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import tss.t.tsiptv.ui.screens.login.AuthUiState

object LocalAuthProvider {
    private val LocalAuthProvider = compositionLocalOf<AuthUiState?> { null }
    val current: AuthUiState?
        @Composable get() = LocalAuthProvider.current ?: koinInject<AuthRepository>()
            .authState
            .collectAsState(null)
            .value
            .let {
                AuthUiState(
                    isLoading = it?.isLoading ?: true,
                    isAuthenticated = it?.isAuthenticated ?: false,
                    displayName = it?.user?.displayName,
                    user = it?.user,
                    error = it?.error,
                )
            }

    public infix fun provides(value: AuthUiState) = LocalAuthProvider provides value
}