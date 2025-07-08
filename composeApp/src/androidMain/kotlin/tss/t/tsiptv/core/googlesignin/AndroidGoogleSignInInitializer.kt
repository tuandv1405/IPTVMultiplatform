package tss.t.tsiptv.core.googlesignin

/**
 * Android GoogleSignIn initializer.
 * 
 * This class provides a way to access GoogleSignIn services from Kotlin code.
 */
class AndroidGoogleSignInInitializer {
    companion object {
        /**
         * Provides an implementation of GoogleSignIn for Android.
         */
        fun provideGoogleSignIn(): GoogleSignIn {
            return AndroidGoogleSignInImplementation()
        }
    }
}