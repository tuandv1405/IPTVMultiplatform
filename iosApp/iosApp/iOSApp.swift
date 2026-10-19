import SwiftUI
import Firebase
import GoogleSignIn
import FirebaseCore
import FirebaseAnalytics
import ComposeApp

/// UIKit callbacks a SwiftUI `App` has no other way to answer.
final class AppDelegate: NSObject, UIApplicationDelegate {
    /// Full screen and the TV layout turn the phone to landscape (`IOSScreenOrientationUtils`).
    func application(_ application: UIApplication,
                     supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        UIInterfaceOrientationMask(rawValue: UInt(IosOrientationLock.shared.mask))
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        FirebaseApp.configure()
        // Clear MPEG-DASH plays on VLCKit; AVPlayer keeps HLS and files (see VLCPlaybackEngine.swift).
        IosPlaybackEngines.shared.dashFactory = VLCPlaybackEngineFactory()
        // Cast / send to TV over the LAN (see LanBridge.swift).
        IosLan.shared.bridge = LanBridge()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // A SwiftUI App gets URL callbacks here, not through the delegate.
                .onOpenURL { url in
                    _ = GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
