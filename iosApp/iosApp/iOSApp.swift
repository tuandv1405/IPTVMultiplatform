import SwiftUI
import Firebase
import GoogleSignIn
import FirebaseCore
import FirebaseAnalytics
import ComposeApp

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
        // Clear MPEG-DASH plays on VLCKit; AVPlayer keeps HLS and files (see VLCPlaybackEngine.swift).
        IosPlaybackEngines.shared.dashFactory = VLCPlaybackEngineFactory()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
    
    
    func application(_ app: UIApplication,
                     open url: URL,
                     options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
      return GIDSignIn.sharedInstance.handle(url)
    }
    
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        FirebaseApp.configure()
        return true
    }
}
