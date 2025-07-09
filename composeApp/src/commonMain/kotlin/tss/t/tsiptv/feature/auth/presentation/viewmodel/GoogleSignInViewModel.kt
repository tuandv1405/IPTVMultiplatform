package tss.t.tsiptv.feature.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tss.t.tsiptv.core.googlesignin.GoogleSignIn
import tss.t.tsiptv.core.googlesignin.GoogleSignInException
import tss.t.tsiptv.core.googlesignin.GoogleSignInUser

/**
 * ViewModel for Google Sign-In functionality.
 */
class GoogleSignInViewModel(
    private val googleSignIn: GoogleSignIn
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoogleSignInUiState())
    val uiState: StateFlow<GoogleSignInUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            googleSignIn.currentUser.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    /**
     * Sign in with Google.
     *
     * @param clientId The client ID from the Google API Console
     */
    fun signIn(clientId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                googleSignIn.signIn(clientId)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: GoogleSignInException) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Sign out.
     */
    fun signOut() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                googleSignIn.signOut()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Check if a user is signed in.
     */
    fun checkSignInStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val isSignedIn = googleSignIn.isSignedIn()
                _uiState.update { it.copy(isLoading = false, isSignedIn = isSignedIn) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Restore a previous sign-in.
     */
    fun restorePreviousSignIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                googleSignIn.restorePreviousSignIn()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

/**
 * UI state for Google Sign-In.
 */
data class GoogleSignInUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val currentUser: GoogleSignInUser? = null,
    val error: String? = null
)