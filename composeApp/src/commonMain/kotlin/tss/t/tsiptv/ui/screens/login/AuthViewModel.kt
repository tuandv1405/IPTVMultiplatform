package tss.t.tsiptv.ui.screens.login

import androidx.lifecycle.ViewModel
import coil3.PlatformContext
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.firebase.models.DeactivationRequest
import tss.t.tsiptv.core.firebase.models.FirebaseUser
import tss.t.tsiptv.core.network.NetworkConnectivityChecker
import tss.t.tsiptv.core.network.NetworkConnectivityCheckerFactory
import tss.t.tsiptv.core.tracking.UserTrackingService
import tss.t.tsiptv.feature.auth.domain.model.AuthResult
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.ui.screens.profile.ProfileScreenActions

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

            LoginEvents.OnConfirmDeactivation -> {
                createDeactivationRequest()
            }

            is LoginEvents.OnProfileActionEvent -> {
                when (event.action) {
                    ProfileScreenActions.EditProfile -> {
                        _uiState.update {
                            it.copy(
                                showEditUsernameDialog = true,
                                editUsernameValue = it.displayName ?: it.user?.email?.split("@")
                                    ?.firstOrNull() ?: ""
                            )
                        }
                    }

                    ProfileScreenActions.ChangePassword -> {
                        _uiState.update {
                            it.copy(showChangePasswordDialog = true)
                        }
                    }

                    ProfileScreenActions.Subscription -> {
                        _uiState.update {
                            it.copy(showSubscriptionPopup = true)
                        }
                    }

                    ProfileScreenActions.Notification -> {
                        _uiState.update {
                            it.copy(showNotificationDialog = true)
                        }
                    }

                    ProfileScreenActions.Settings -> {
                        // Settings action handled in UI
                    }

                    else -> {
                        // Handle other profile actions
                    }
                }
            }

            LoginEvents.OnShowSettingsBottomSheet,
            LoginEvents.OnDismissSettingsBottomSheet,
            LoginEvents.OnShowDeactivationDialog,
            LoginEvents.OnDismissDeactivationDialog,
                -> {
                // These events are handled in UI state
            }

            // Edit username dialog events
            is LoginEvents.OnEditUsernameChanged -> {
                _uiState.update {
                    it.copy(editUsernameValue = event.username, editUsernameError = null)
                }
            }

            LoginEvents.OnSaveUsername -> {
                saveUsername()
            }

            LoginEvents.OnDismissEditUsernameDialog -> {
                _uiState.update {
                    it.copy(
                        showEditUsernameDialog = false,
                        editUsernameValue = "",
                        editUsernameError = null,
                        isEditUsernameLoading = false
                    )
                }
            }

            // Change password dialog events
            is LoginEvents.OnCurrentPasswordChanged -> {
                _uiState.update {
                    it.copy(currentPasswordValue = event.password, changePasswordError = null)
                }
            }

            is LoginEvents.OnNewPasswordChanged -> {
                _uiState.update {
                    it.copy(newPasswordValue = event.password, changePasswordError = null)
                }
            }

            is LoginEvents.OnConfirmPasswordChanged -> {
                _uiState.update {
                    it.copy(confirmPasswordValue = event.password, changePasswordError = null)
                }
            }

            LoginEvents.OnSavePassword -> {
                changePassword()
            }

            LoginEvents.OnDismissChangePasswordDialog -> {
                _uiState.update {
                    it.copy(
                        showChangePasswordDialog = false,
                        currentPasswordValue = "",
                        newPasswordValue = "",
                        confirmPasswordValue = "",
                        changePasswordError = null,
                        isChangePasswordLoading = false
                    )
                }
            }

            // Subscription popup
            LoginEvents.OnDismissSubscriptionPopup -> {
                _uiState.update {
                    it.copy(showSubscriptionPopup = false)
                }
            }

            // Notification dialog events
            LoginEvents.OnRequestNotificationPermission -> {
                requestNotificationPermission()
            }

            LoginEvents.OnDismissNotificationDialog -> {
                _uiState.update {
                    it.copy(showNotificationDialog = false)
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
                    println(result.message)
                    println(result.exception?.message)
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

    /**
     * Creates a deactivation request for the current user.
     */
    fun createDeactivationRequest(reason: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeactivationLoading = true, deactivationError = null) }

            // Check network connectivity
            if (!networkConnectivityChecker.isNetworkAvailable()) {
                _uiState.update {
                    it.copy(
                        isDeactivationLoading = false,
                        deactivationError = "No internet connection. Please check your network settings and try again."
                    )
                }
                return@launch
            }

            val result = authRepository.createDeactivationRequest(reason)

            when (result) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isDeactivationLoading = false,
                            deactivationError = null
                        )
                    }
                    // Refresh the deactivation request to show the newly created one
                    loadDeactivationRequest()
                }

                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isDeactivationLoading = false,
                            deactivationError = result.message
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
     * Loads the current user's deactivation request.
     */
    fun loadDeactivationRequest() {
        viewModelScope.launch {
            try {
                val deactivationRequest = authRepository.getDeactivationRequest()
                _uiState.update {
                    it.copy(
                        deactivationRequest = deactivationRequest,
                        deactivationError = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        deactivationRequest = null,
                        deactivationError = e.message ?: "Failed to load deactivation request"
                    )
                }
            }
        }
    }

    /**
     * Cancels the current user's deactivation request.
     */
    fun cancelDeactivationRequest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeactivationLoading = true, deactivationError = null) }

            // Check network connectivity
            if (!networkConnectivityChecker.isNetworkAvailable()) {
                _uiState.update {
                    it.copy(
                        isDeactivationLoading = false,
                        deactivationError = "No internet connection. Please check your network settings and try again."
                    )
                }
                return@launch
            }

            val result = authRepository.cancelDeactivationRequest()

            when (result) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isDeactivationLoading = false,
                            deactivationRequest = null,
                            deactivationError = null
                        )
                    }
                }

                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isDeactivationLoading = false,
                            deactivationError = result.message
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
     * Observes the current user's deactivation request for real-time updates.
     */
    fun observeDeactivationRequest() {
        viewModelScope.launch {
            authRepository.observeDeactivationRequest().collect { deactivationRequest ->
                _uiState.update {
                    it.copy(deactivationRequest = deactivationRequest)
                }
            }
        }
    }

    /**
     * Saves the updated username/display name for the current user.
     */
    private fun saveUsername() {
        val currentState = _uiState.value
        val newDisplayName = currentState.editUsernameValue.trim()

        if (newDisplayName.isEmpty()) {
            _uiState.update {
                it.copy(editUsernameError = "Username cannot be empty")
            }
            return
        }

        if (newDisplayName == currentState.displayName) {
            _uiState.update {
                it.copy(showEditUsernameDialog = false, editUsernameValue = "")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isEditUsernameLoading = true, editUsernameError = null) }

            // Check network connectivity
            if (!networkConnectivityChecker.isNetworkAvailable()) {
                _uiState.update {
                    it.copy(
                        isEditUsernameLoading = false,
                        editUsernameError = "No internet connection. Please check your network settings and try again."
                    )
                }
                return@launch
            }

            try {
                val result = authRepository.updateDisplayName(newDisplayName)
                when (result) {
                    is AuthResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isEditUsernameLoading = false,
                                showEditUsernameDialog = false,
                                displayName = newDisplayName,
                                editUsernameValue = "",
                                editUsernameError = null
                            )
                        }
                    }

                    is AuthResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isEditUsernameLoading = false,
                                editUsernameError = result.message
                            )
                        }
                    }

                    AuthResult.Loading -> {
                        // Loading state is already handled
                    }

                    AuthResult.SignedOut -> {
                        // User signed out during operation
                        _uiState.update {
                            it.copy(
                                isEditUsernameLoading = false,
                                editUsernameError = "User session expired. Please sign in again."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isEditUsernameLoading = false,
                        editUsernameError = e.message ?: "Failed to update username"
                    )
                }
            }
        }
    }

    /**
     * Changes the password for the current user.
     */
    private fun changePassword() {
        val currentState = _uiState.value
        val currentPassword = currentState.currentPasswordValue.trim()
        val newPassword = currentState.newPasswordValue.trim()
        val confirmPassword = currentState.confirmPasswordValue.trim()

        // Validate inputs
        if (currentPassword.isEmpty()) {
            _uiState.update {
                it.copy(changePasswordError = "Current password is required")
            }
            return
        }

        if (newPassword.isEmpty()) {
            _uiState.update {
                it.copy(changePasswordError = "New password is required")
            }
            return
        }

        if (newPassword.length < 6) {
            _uiState.update {
                it.copy(changePasswordError = "New password must be at least 6 characters")
            }
            return
        }

        if (newPassword != confirmPassword) {
            _uiState.update {
                it.copy(changePasswordError = "New passwords do not match")
            }
            return
        }

        if (currentPassword == newPassword) {
            _uiState.update {
                it.copy(changePasswordError = "New password must be different from current password")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isChangePasswordLoading = true, changePasswordError = null) }

            // Check network connectivity
            if (!networkConnectivityChecker.isNetworkAvailable()) {
                _uiState.update {
                    it.copy(
                        isChangePasswordLoading = false,
                        changePasswordError = "No internet connection. Please check your network settings and try again."
                    )
                }
                return@launch
            }

            try {
                val result = authRepository.changePassword(currentPassword, newPassword)
                when (result) {
                    is AuthResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isChangePasswordLoading = false,
                                showChangePasswordDialog = false,
                                currentPasswordValue = "",
                                newPasswordValue = "",
                                confirmPasswordValue = "",
                                changePasswordError = null
                            )
                        }
                    }

                    is AuthResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isChangePasswordLoading = false,
                                changePasswordError = result.message
                            )
                        }
                    }

                    AuthResult.Loading -> {
                        // Loading state is already handled
                    }

                    AuthResult.SignedOut -> {
                        // User signed out during operation
                        _uiState.update {
                            it.copy(
                                isChangePasswordLoading = false,
                                changePasswordError = "User session expired. Please sign in again."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isChangePasswordLoading = false,
                        changePasswordError = e.message ?: "Failed to change password"
                    )
                }
            }
        }
    }

    /**
     * Requests notification permission from the user.
     * For now, this is a placeholder that simply toggles the permission state.
     * In a real implementation, this would trigger the system permission dialog.
     */
    private fun requestNotificationPermission() {
        // For now, just toggle the state to simulate permission request
        // In a real implementation, this would check platform-specific permissions
        _uiState.update {
            it.copy(
                notificationPermissionGranted = !it.notificationPermissionGranted,
                showNotificationDialog = false
            )
        }
    }
}

/**
 * Data class representing the UI state for authentication.
 *
 * @property isAuthenticated Whether the user is authenticated
 * @property isLoading Whether an authentication operation is in progress
 * @property error The error message from the last authentication operation, or null if no error
 * @property deactivationRequest The current user's deactivation request, or null if none exists
 * @property isDeactivationLoading Whether a deactivation operation is in progress
 * @property deactivationError The error message from the last deactivation operation, or null if no error
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
    val deactivationRequest: DeactivationRequest? = null,
    val isDeactivationLoading: Boolean = false,
    val deactivationError: String? = null,

    // Profile screen states
    val showEditUsernameDialog: Boolean = false,
    val editUsernameValue: String = "",
    val isEditUsernameLoading: Boolean = false,
    val editUsernameError: String? = null,

    val showChangePasswordDialog: Boolean = false,
    val currentPasswordValue: String = "",
    val newPasswordValue: String = "",
    val confirmPasswordValue: String = "",
    val isChangePasswordLoading: Boolean = false,
    val changePasswordError: String? = null,

    val showSubscriptionPopup: Boolean = false,

    val showNotificationDialog: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
)
