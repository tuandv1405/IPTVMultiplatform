package tss.t.tsiptv.feature.auth.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import tss.t.tsiptv.feature.auth.data.repository.AuthRepositoryImpl
import tss.t.tsiptv.feature.auth.domain.repository.AuthRepository
import tss.t.tsiptv.ui.screens.login.AuthViewModel
import tss.t.tsiptv.feature.auth.presentation.viewmodel.GoogleSignInViewModel

/**
 * Koin module for authentication components.
 *
 * Note: IFirebaseAuth and IFirestore are provided by platform-specific modules:
 * - AndroidFirebaseAuth and AndroidFirestore for Android
 * - IosFirebaseAuth and IosFirestore for iOS
 */
val authModule = module {
    // Repository
    single<AuthRepository> {
        AuthRepositoryImpl(
            firebaseAuth = get(),
            firestore = get(),
            keyValueStorage = get()
        )
    }

    factory {
        GoogleSignInViewModel(
            googleSignIn = get()
        )
    }

    // ViewModels
    viewModelOf(::AuthViewModel)
}
