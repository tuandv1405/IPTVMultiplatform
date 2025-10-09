package tss.t.tsiptv.ui.screens.login

import androidx.lifecycle.ViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.firebase.models.FirebaseUser
import tss.t.tsiptv.core.network.NetworkConnectivityChecker
import tss.t.tsiptv.core.network.NetworkConnectivityCheckerFactory
import tss.t.tsiptv.core.tracking.UserTrackingService
import tss.t.tsiptv.feature.auth.domain.model.AuthResult
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import tss.t.tsiptv.ui.screens.login.models.LoginEvents

/**
 * ViewModel for authentication operations.
 * This ViewModel handles authentication operations and exposes the authentication state to the UI.
 *
 * @property authRepository The use case for authentication operations
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val userTrackingService: UserTrackingService,
    private val networkConnectivityChecker: NetworkConnectivityChecker = NetworkConnectivityCheckerFactory.create(),
) : ViewModel() {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)

    // UI state
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Email and password for login
    private var email: String = ""
    private var password: String = ""

    init {
        // Observe auth state
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isAuthenticated = authState.isAuthenticated,
                        isLoading = authState.isLoading,
                        error = authState.error,
                        user = authState.user,
                        displayName = authState.user
                            ?.displayName.takeIf {
                                !it.isNullOrEmpty()
                            }
                            ?: authState.user
                                ?.email
                                ?.firstOrNull()
                                ?.uppercase()
                            ?: "Anonymous"
                    )
                }
                if (authState.isAuthenticated) {
                    // Use UserTrackingService to set user ID only when ATT permission is granted
                    viewModelScope.launch {
                        userTrackingService.updateAnalyticsUserIdIfAllowed(authState.user)
                    }
                }
            }
        }

        // Observe network status
        viewModelScope.launch {
            // Set initial network status
//            _uiState.update { it.copy(isNetworkAvailable = networkConnectivityChecker.isNetworkAvailable()) }

            // Observe network status changes
//            networkConnectivityChecker.observeNetworkStatus().collect { isAvailable ->
//                _uiState.update { it.copy(isNetworkAvailable = isAvailable) }
//            }
        }

        // Check if token needs refresh
        viewModelScope.launch {
            if (authRepository.isAuthenticated() && authRepository.isTokenExpired()) {
                refreshToken()
            }
        }
    }

    /**
     * Handles login events from the UI.
     *
     * @param event The login event
     */
    fun onEvent(event: LoginEvents) {
        when (event) {
            is LoginEvents.EmailChanged -> {
                validateEmail(event.email)
                email = event.email
            }

            is LoginEvents.PasswordChanged -> {
                password = event.password
            }

            is LoginEvents.OnSignInPressed -> {
                signInWithEmailAndPassword()
            }

            is LoginEvents.OnSignUpPressed -> {
                createUserWithEmailAndPassword()
            }

            is LoginEvents.OnSignInWithGooglePressed -> {
                signInWithGoogle()
            }

            is LoginEvents.OnSignInWithApplePressed -> {
                signInWithApple()
            }

            is LoginEvents.OnDismissErrorDialog -> {
                _uiState.update {
                    it.copy(
                        isAuthenticated = false,
                        error = null
                    )
                }
            }

            LoginEvents.OnLogoutPressed -> {
                _uiState.update {
                    it.copy(
                        isAuthenticated = false
                    )
                }
                viewModelScope.launch {
                    authRepository.signOut()
                }
            }

            else -> {
                // Handle other events if needed
            }
        }
    }

    private fun validateEmail(email: String) {
        if (this.email == email) return
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
        val isValid = email.isNotEmpty() && emailRegex.matches(email)
        _uiState.update {
            it.copy(
                isEmailValid = isValid,
                isEmailEmpty = email.isEmpty()
            )
        }
    }

    /**
     * Signs in with email and password.
     */
    private fun signInWithEmailAndPassword() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Check network connectivity
            if (!networkConnectivityChecker.isNetworkAvailable()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "No internet connection. Please check your network settings and try again."
                    )
                }
                return@launch
            }

            val result = authRepository.signInWithEmailAndPassword(email, password)

            when (result) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            isLoading = false,
                            error = null
                        )
                    }
                }

                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }

                else -> {
                    // Handle other result types if needed
                }
            }
        }
    }

    /**
     * Creates a new user with email and password.
     */
    private fun createUserWithEmailAndPassword() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Check network connectivity
            if (!networkConnectivityChecker.isNetworkAvailable()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "No internet connection. Please check your network settings and try again."
                    )
                }
                return@launch
            }

            val result = authRepository.createUserWithEmailAndPassword(email, password)

            when (result) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            isLoading = false,
                            error = null
                        )
                    }
                }

                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }

                else -> {
                    // Handle other result types if needed
                }
            }
        }
    }

    /**
     * Signs in with Google.
     */
    private fun signInWithGoogle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Check network connectivity
            if (!networkConnectivityChecker.isNetworkAvailable()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "No internet connection. Please check your network settings and try again."
                    )
                }
                return@launch
            }

            val result = authRepository.signInWithGoogle()

            when (result) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            isLoading = false,
                            error = null
                        )
                    }
                }

                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }

                else -> {
                    // Handle other result types if needed
                }
            }
        }
    }

    /**
     * Signs in with Apple.
     */
    private fun signInWithApple() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Check network connectivity
            if (!networkConnectivityChecker.isNetworkAvailable()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "No internet connection. Please check your network settings and try again."
                    )
                }
                return@launch
            }

            val result = authRepository.signInWithApple()

            when (result) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            isLoading = false,
                            error = null
                        )
                    }
                }

                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }

                else -> {
                    // Handle other result types if needed
                }
            }
        }
    }

    /**
     * Signs out the current user.
     */
    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Check network connectivity
            if (!networkConnectivityChecker.isNetworkAvailable()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "No internet connection. Please check your network settings and try again."
                    )
                }
                return@launch
            }

            val result = authRepository.signOut()

            when (result) {
                is AuthResult.SignedOut -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticated = false,
                            isLoading = false,
                            error = null
                        )
                    }
                }

                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }

                else -> {
                    // Handle other result types if needed
                }
            }
        }
    }

    /**
     * Refreshes the access token if it's expired or about to expire.
     */
    private fun refreshToken() {
        viewModelScope.launch {
            // Check network connectivity
            if (!networkConnectivityChecker.isNetworkAvailable()) {
                _uiState.update {
                    it.copy(
                        error = "No internet connection. Please check your network settings and try again."
                    )
                }
                return@launch
            }

            val result = authRepository.refreshTokenIfNeeded()

            if (result is AuthResult.Error) {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }
}

/**
 * Data class representing the UI state for authentication.
 *
 * @property isAuthenticated Whether the user is authenticated
 * @property isLoading Whether an authentication operation is in progress
 * @property error The error message from the last authentication operation, or null if no error
 */
data class AuthUiState(
    val user: FirebaseUser? = null,
    val displayName: String? = null,
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isEmailValid: Boolean = true,
    val isEmailEmpty: Boolean = false,
    val isPasswordValid: Boolean = true,
    val isNetworkAvailable: Boolean = true,
)
