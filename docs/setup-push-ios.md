# Push notifications on iOS: setup (not done yet)

The common code is in `feature/push`, and the iOS build uses `NoPushPlatform` (no-op): the
Notifications screen says "not available on this device". To turn it on, do the steps below
on a Mac, then bind an iOS `PushPlatform`. Product rules: `docs/prd-push-notifications.md`.

## 1. Apple and Firebase

1. Apple Developer › Keys › **+** › enable **Apple Push Notifications service (APNs)** › download
   the `.p8` file. Note the **Key ID** and the **Team ID**.
2. Firebase console › Project settings › **Cloud Messaging** › Apple app configuration › **APNs
   Authentication Key** › upload the `.p8` with its Key ID and Team ID. One key covers development
   and production.
3. Make sure `GoogleService-Info.plist` of the iOS app is in `iosApp/iosApp` (it already is if
   Firebase Auth works on iOS).

## 2. Xcode

1. Target `iosApp` › **Signing & Capabilities**:
   - **+ Push Notifications**;
   - **+ Background Modes** › tick **Remote notifications**.
2. Swift Package Manager: add `https://github.com/firebase/firebase-ios-sdk` (same version line
   as the other Firebase products) and add **FirebaseMessaging** to the target.
3. `Info.plist`: `FirebaseAppDelegateProxyEnabled` = `NO`, so the token is handled explicitly
   below. If you leave swizzling on, skip the `apnsToken` line.

## 3. AppDelegate (Swift)

```swift
import UIKit
import FirebaseCore
import FirebaseMessaging
import UserNotifications
import ComposeApp   // the KMP framework

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        FirebaseApp.configure()
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        IosPushBridge.shared.attach(
            requestPermission: { done in
                UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
                    if granted { DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() } }
                    done(KotlinBoolean(value: granted))
                }
            },
            subscribe: { topic, done in Messaging.messaging().subscribe(toTopic: topic) { done(KotlinBoolean(value: $0 == nil)) } },
            unsubscribe: { topic, done in Messaging.messaging().unsubscribe(fromTopic: topic) { done(KotlinBoolean(value: $0 == nil)) } },
            openSettings: { UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!) }
        )
        // Already authorized earlier: register again at every start (the token can change).
        UNUserNotificationCenter.current().getNotificationSettings { s in
            if s.authorizationStatus == .authorized {
                DispatchQueue.main.async { application.registerForRemoteNotifications() }
            }
        }
        return true
    }

    // APNs token -> FCM (needed with swizzling off).
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // FCM token -> Kotlin (PushManager stores it, and on the device document when signed in).
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        IosPushBridge.shared.onToken(token: fcmToken)
    }

    // Foreground: show the banner, unless the user switched notifications off in the app (R7).
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler(IosPushBridge.shared.showMessages ? [.banner, .sound, .list] : [])
    }

    // Tap: pass the payload "link" to Kotlin; it goes through PushLinkPolicy (the allowlist).
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let link = response.notification.request.content.userInfo["link"] as? String
        IosPushBridge.shared.onOpened(link: link)
        completionHandler()
    }
}
```

In `iOSApp.swift`, add `@UIApplicationDelegateAdaptor(AppDelegate.self) var delegate`.

## 4. Kotlin bridge (`iosMain`)

```kotlin
package tss.t.tsiptv.feature.push

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.context.GlobalContext
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/** Called from Swift (AppDelegate). */
object IosPushBridge {
    private var request: ((done: (Boolean) -> Unit) -> Unit)? = null
    private var sub: ((String, (Boolean) -> Unit) -> Unit)? = null
    private var unsub: ((String, (Boolean) -> Unit) -> Unit)? = null
    private var settings: (() -> Unit)? = null
    internal val token = MutableStateFlow<String?>(null)
    internal var granted: Boolean? = null

    fun attach(
        requestPermission: (done: (Boolean) -> Unit) -> Unit,
        subscribe: (String, (Boolean) -> Unit) -> Unit,
        unsubscribe: (String, (Boolean) -> Unit) -> Unit,
        openSettings: () -> Unit,
    ) { request = requestPermission; sub = subscribe; unsub = unsubscribe; settings = openSettings }

    fun onToken(token: String?) { this.token.value = token }
    val showMessages: Boolean get() = GlobalContext.get().get<PushManager>().showMessages
    fun onOpened(link: String?) = GlobalContext.get().get<PushManager>().onNotificationOpened(link)

    internal suspend fun requestPermission(): Boolean {
        val r = request ?: return false
        val d = CompletableDeferred<Boolean>()
        r { d.complete(it) }
        return d.await().also { granted = it }
    }
    internal suspend fun call(f: ((String, (Boolean) -> Unit) -> Unit)?, topic: String): Boolean {
        f ?: return false
        val d = CompletableDeferred<Boolean>()
        f(topic) { d.complete(it) }
        return d.await()
    }
    internal fun openSettings() = settings?.invoke()
    internal val subscribeFn get() = sub
    internal val unsubscribeFn get() = unsub
}

class IosPushPlatform : PushPlatform {
    override val isSupported = true
    override val showDebugToken = false
    override val token: StateFlow<String?> = IosPushBridge.token
    override fun permission() = when (IosPushBridge.granted) {
        true -> PushPermission.GRANTED
        false -> PushPermission.DENIED
        null -> PushPermission.UNKNOWN
    }
    override suspend fun requestPermission() =
        if (IosPushBridge.requestPermission()) PushPermission.GRANTED else PushPermission.DENIED
    override fun openSystemSettings() { IosPushBridge.openSettings() }
    override suspend fun subscribe(topic: String) = IosPushBridge.call(IosPushBridge.subscribeFn, topic)
    override suspend fun unsubscribe(topic: String) = IosPushBridge.call(IosPushBridge.unsubscribeFn, topic)
    override suspend fun refreshToken() = Unit // the token arrives through MessagingDelegate
    override fun systemLanguage(): String = NSLocale.currentLocale.languageCode
}
```

Bind it in the iOS Koin module, which loads after `castSyncModule`:
`single<PushPlatform> { IosPushPlatform() }`. Start the manager once at app start, with
`GlobalContext.get().get<PushManager>().start()`, e.g. from `MainViewController`.

Note: `permission()` above only knows the answer from this run. To be exact, read
`UNUserNotificationCenter.getNotificationSettings` in `attach` and store it in `granted`.

## 5. Test

- Run on a **real device**: the simulator receives remote pushes only on recent Xcode with an
  Apple silicon Mac.
- Profile › Notifications › turn on › allow.
- Firebase console › Messaging › **Send test message** to the token (a debug log or temporary
  UI can show it), or target topic `all`.
- Check the banner in the foreground, and the tap with `link` = `tsiptv://addons`.
