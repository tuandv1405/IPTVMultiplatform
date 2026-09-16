package tss.t.tsiptv.feature.auth.presentation.viewmodel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import tss.t.tsiptv.core.firebase.models.DeactivationRequest
import tss.t.tsiptv.core.firebase.models.FirebaseUser
import tss.t.tsiptv.core.network.NetworkConnectivityCheckerFactory
import tss.t.tsiptv.core.permission.PermissionCheckerFactory
import tss.t.tsiptv.core.tracking.DefaultUserTrackingService
import tss.t.tsiptv.feature.auth.domain.model.AuthResult
import tss.t.tsiptv.feature.auth.domain.model.AuthState
import tss.t.tsiptv.feature.auth.domain.model.AuthToken
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import tss.t.tsiptv.ui.screens.login.AuthViewModel
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fake [AuthRepository] that records password reset requests.
 *
 * Only the members the tests exercise are implemented; the rest throw, so a test
 * that reaches them fails loudly instead of passing on a silent default.
 */
private class FakeAuthRepository : AuthRepository {
    val sentResetEmails = mutableListOf<String>()
    var sendPasswordResetResult: AuthResult = AuthResult.PasswordResetEmailSent

    override val authState = MutableStateFlow(
        AuthState(isAuthenticated = false, isLoading = false, error = null)
    )

    override suspend fun sendPasswordResetEmail(email: String): AuthResult {
        sentResetEmails += email
        return sendPasswordResetResult
    }

    override suspend fun signInWithEmailAndPassword(email: String, password: String) =
        AuthResult.Error("Not implemented for test")

    override suspend fun createUserWithEmailAndPassword(email: String, password: String) =
        AuthResult.Error("Not implemented for test")

    override suspend fun signInWithGoogle() = AuthResult.Error("Not implemented for test")

    override suspend fun signInWithApple() = AuthResult.Error("Not implemented for test")

    override suspend fun signOut() = AuthResult.Error("Not implemented for test")

    override suspend fun isAuthenticated() = false

    override suspend fun isTokenExpired() = true

    override suspend fun createDeactivationRequest(reason: String?) =
        AuthResult.Error("Not implemented for test")

    override suspend fun getDeactivationRequest(): DeactivationRequest? = null

    override fun observeDeactivationRequest(): Flow<DeactivationRequest?> = flowOf(null)

    override suspend fun cancelDeactivationRequest() = AuthResult.Error("Not implemented for test")

    override suspend fun updateDisplayName(displayName: String) =
        AuthResult.Error("Not implemented for test")

    override suspend fun changePassword(currentPassword: String, newPassword: String) =
        AuthResult.Error("Not implemented for test")

    override suspend fun refreshTokenIfNeeded() = AuthResult.Error("Not implemented for test")

    override suspend fun getAuthToken(): AuthToken? = null

    override suspend fun getCurrentUser(): FirebaseUser? = null
}

class AuthViewModelTest {

    private fun viewModelWith(repository: AuthRepository) = AuthViewModel(
        authRepository = repository,
        userTrackingService = DefaultUserTrackingService(PermissionCheckerFactory.create()),
        networkConnectivityChecker = NetworkConnectivityCheckerFactory.create()
    )

    @Test
    fun testEmailValidation() {
        val viewModel = viewModelWith(FakeAuthRepository())

        // Test valid email addresses
        viewModel.onEvent(LoginEvents.EmailChanged("user@example.com"))
        assertTrue(viewModel.uiState.value.isEmailValid)
        assertFalse(viewModel.uiState.value.isEmailEmpty)

        viewModel.onEvent(LoginEvents.EmailChanged("user.name@example.co.uk"))
        assertTrue(viewModel.uiState.value.isEmailValid)

        viewModel.onEvent(LoginEvents.EmailChanged("user-name@example.org"))
        assertTrue(viewModel.uiState.value.isEmailValid)

        viewModel.onEvent(LoginEvents.EmailChanged("user_name@example.io"))
        assertTrue(viewModel.uiState.value.isEmailValid)

        // Test invalid email addresses
        viewModel.onEvent(LoginEvents.EmailChanged(""))
        assertFalse(viewModel.uiState.value.isEmailValid)
        assertTrue(viewModel.uiState.value.isEmailEmpty)

        viewModel.onEvent(LoginEvents.EmailChanged("user@"))
        assertFalse(viewModel.uiState.value.isEmailValid)

        viewModel.onEvent(LoginEvents.EmailChanged("user@example"))
        assertFalse(viewModel.uiState.value.isEmailValid)

        viewModel.onEvent(LoginEvents.EmailChanged("user@.com"))
        assertFalse(viewModel.uiState.value.isEmailValid)

        viewModel.onEvent(LoginEvents.EmailChanged("@example.com"))
        assertFalse(viewModel.uiState.value.isEmailValid)

        viewModel.onEvent(LoginEvents.EmailChanged("user@example."))
        assertFalse(viewModel.uiState.value.isEmailValid)
    }

    @Test
    fun forgotPasswordDialogOpensPrefilledWithTheTypedEmail() {
        val viewModel = viewModelWith(FakeAuthRepository())

        viewModel.onEvent(LoginEvents.EmailChanged("user@example.com"))
        viewModel.onEvent(LoginEvents.OnForgotPasswordPressed)

        val state = viewModel.uiState.value
        assertTrue(state.showForgotPasswordDialog)
        assertEquals("user@example.com", state.forgotPasswordEmail)
        assertFalse(state.isForgotPasswordEmailSent)
        assertFalse(state.isForgotPasswordLoading)
        assertNull(state.forgotPasswordError)
    }

    @Test
    fun forgotPasswordRejectsAnEmptyEmailWithoutCallingTheRepository() {
        val repository = FakeAuthRepository()
        val viewModel = viewModelWith(repository)

        viewModel.onEvent(LoginEvents.OnForgotPasswordPressed)
        viewModel.onEvent(LoginEvents.OnForgotPasswordEmailChanged("   "))
        viewModel.onEvent(LoginEvents.OnSendPasswordResetEmail)

        assertNotNull(viewModel.uiState.value.forgotPasswordError)
        assertFalse(viewModel.uiState.value.isForgotPasswordEmailSent)
        assertTrue(repository.sentResetEmails.isEmpty())
    }

    @Test
    fun forgotPasswordRejectsAMalformedEmailWithoutCallingTheRepository() {
        val repository = FakeAuthRepository()
        val viewModel = viewModelWith(repository)

        viewModel.onEvent(LoginEvents.OnForgotPasswordPressed)

        for (malformed in listOf("user@", "user@example", "@example.com", "user@.com")) {
            viewModel.onEvent(LoginEvents.OnForgotPasswordEmailChanged(malformed))
            viewModel.onEvent(LoginEvents.OnSendPasswordResetEmail)

            assertNotNull(
                viewModel.uiState.value.forgotPasswordError,
                "expected \"$malformed\" to be rejected"
            )
            assertFalse(viewModel.uiState.value.isForgotPasswordEmailSent)
        }

        assertTrue(repository.sentResetEmails.isEmpty())
    }

    @Test
    fun typingInTheForgotPasswordDialogClearsThePreviousError() {
        val viewModel = viewModelWith(FakeAuthRepository())

        viewModel.onEvent(LoginEvents.OnForgotPasswordPressed)
        viewModel.onEvent(LoginEvents.OnForgotPasswordEmailChanged("nope"))
        viewModel.onEvent(LoginEvents.OnSendPasswordResetEmail)
        assertNotNull(viewModel.uiState.value.forgotPasswordError)

        viewModel.onEvent(LoginEvents.OnForgotPasswordEmailChanged("user@example.com"))

        assertNull(viewModel.uiState.value.forgotPasswordError)
        assertEquals("user@example.com", viewModel.uiState.value.forgotPasswordEmail)
    }

    @Test
    fun dismissingTheForgotPasswordDialogResetsItsState() {
        val viewModel = viewModelWith(FakeAuthRepository())

        viewModel.onEvent(LoginEvents.OnForgotPasswordPressed)
        viewModel.onEvent(LoginEvents.OnForgotPasswordEmailChanged("bad"))
        viewModel.onEvent(LoginEvents.OnSendPasswordResetEmail)
        viewModel.onEvent(LoginEvents.OnDismissForgotPasswordDialog)

        val state = viewModel.uiState.value
        assertFalse(state.showForgotPasswordDialog)
        assertEquals("", state.forgotPasswordEmail)
        assertNull(state.forgotPasswordError)
        assertFalse(state.isForgotPasswordLoading)
        assertFalse(state.isForgotPasswordEmailSent)
    }
}
