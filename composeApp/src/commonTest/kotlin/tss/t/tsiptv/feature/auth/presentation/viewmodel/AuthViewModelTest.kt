package tss.t.tsiptv.feature.auth.presentation.viewmodel

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import tss.t.tsiptv.ui.screens.login.models.LoginEvents
import tss.t.tsiptv.core.firebase.models.FirebaseUser
import tss.t.tsiptv.feature.auth.domain.model.AuthResult
import tss.t.tsiptv.feature.auth.domain.model.AuthToken
import tss.t.tsiptv.ui.screens.login.AuthViewModel

class AuthViewModelTest {

    @Test
    fun testEmailValidation() {
        // Create a mock repository
        val mockRepository = object : AuthRepository {
            override val authState = kotlinx.coroutines.flow.MutableStateFlow(
                tss.t.tsiptv.feature.auth.domain.model.AuthState(
                    isAuthenticated = false,
                    isLoading = false,
                    error = null
                )
            )

            override suspend fun signInWithEmailAndPassword(email: String, password: String) = 
                tss.t.tsiptv.feature.auth.domain.model.AuthResult.Error("Not implemented for test")

            override suspend fun createUserWithEmailAndPassword(
                email: String,
                password: String,
            ): AuthResult {
                TODO("Not yet implemented")
            }

            override suspend fun signInWithGoogle() = 
                tss.t.tsiptv.feature.auth.domain.model.AuthResult.Error("Not implemented for test")

            override suspend fun signInWithApple() = 
                tss.t.tsiptv.feature.auth.domain.model.AuthResult.Error("Not implemented for test")

            override suspend fun signOut() = 
                tss.t.tsiptv.feature.auth.domain.model.AuthResult.Error("Not implemented for test")

            override suspend fun isAuthenticated() = false

            override suspend fun isTokenExpired() = true

            override suspend fun refreshTokenIfNeeded() = 
                tss.t.tsiptv.feature.auth.domain.model.AuthResult.Error("Not implemented for test")

            override suspend fun getAuthToken(): AuthToken? = null

            override suspend fun getCurrentUser(): FirebaseUser? = null
        }

        // Create the view model with the mock repository
        val viewModel = AuthViewModel(mockRepository)

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
}
