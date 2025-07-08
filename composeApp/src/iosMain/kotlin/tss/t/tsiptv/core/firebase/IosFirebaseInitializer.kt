package tss.t.tsiptv.core.firebase

/**
 * iOS Firebase initializer.
 * 
 * Note: Firebase is initialized in the Swift code (iOSApp.swift) using:
 * ```
 * import Firebase
 * 
 * @main
 * struct iOSApp: App {
 *     init() {
 *         FirebaseApp.configure()
 *     }
 *     
 *     var body: some Scene {
 *         WindowGroup {
 *             ContentView()
 *         }
 *     }
 * }
 * ```
 * 
 * This class provides a way to access Firebase services from Kotlin code.
 * The implementation now uses the firebase-kotlin-sdk, which provides a Kotlin Multiplatform
 * interface to Firebase services.
 */
class IosFirebaseInitializer {
    companion object {
        /**
         * Provides an implementation of IFirebaseAuth using firebase-kotlin-sdk.
         * This implementation works across platforms (iOS, Android) using the same API.
         */
        fun provideFirebaseAuth(): IFirebaseAuth {
            return IosFirebaseAuthImplementation()
        }
    }
}
