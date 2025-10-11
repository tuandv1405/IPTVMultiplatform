package tss.t.tsiptv.ui.screens.login.models

import androidx.compose.runtime.Immutable
import tss.t.tsiptv.ui.screens.profile.ProfileScreenActions

@Immutable
sealed interface LoginEvents {
    data class EmailChanged(val email: String) : LoginEvents {
    }

    data class PasswordChanged(val password: String) : LoginEvents

    object LoginSuccess : LoginEvents
    object LoginFailure : LoginEvents
    object RegisterSuccess : LoginEvents
    object RegisterFailure : LoginEvents
    object ShowRegisterScreen : LoginEvents
    object ShowLoginScreen : LoginEvents
    object ShowHomeScreen : LoginEvents
    object ShowErrorScreen : LoginEvents
    object ShowLoadingScreen : LoginEvents

    object OnBackPressed : LoginEvents
    object OnRetryPressed : LoginEvents
    object OnCloseDialog : LoginEvents
    object OnRetryRegisterPressed : LoginEvents
    object OnRetryLoginPressed : LoginEvents
    object OnSignInWithGooglePressed : LoginEvents
    object OnSignInWithApplePressed : LoginEvents
    object OnSignInWithFacebookPressed : LoginEvents
    object OnSignInPressed : LoginEvents
    object OnSignUpPressed : LoginEvents

    object OnLogoutPressed : LoginEvents

    object OnDismissErrorDialog : LoginEvents

    data class OnProfileActionEvent(val action: ProfileScreenActions) : LoginEvents
    
    object OnShowSettingsBottomSheet : LoginEvents
    object OnDismissSettingsBottomSheet : LoginEvents
    object OnShowDeactivationDialog : LoginEvents
    object OnDismissDeactivationDialog : LoginEvents
    object OnConfirmDeactivation : LoginEvents
    
    // Profile dialog events
    data class OnEditUsernameChanged(val username: String) : LoginEvents
    object OnSaveUsername : LoginEvents
    object OnDismissEditUsernameDialog : LoginEvents
    
    data class OnCurrentPasswordChanged(val password: String) : LoginEvents
    data class OnNewPasswordChanged(val password: String) : LoginEvents
    data class OnConfirmPasswordChanged(val password: String) : LoginEvents
    object OnSavePassword : LoginEvents
    object OnDismissChangePasswordDialog : LoginEvents
    
    object OnDismissSubscriptionPopup : LoginEvents
    
    object OnRequestNotificationPermission : LoginEvents
    object OnDismissNotificationDialog : LoginEvents
}