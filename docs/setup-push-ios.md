# Push notifications on iOS: setup (not done yet)

The common code is in `feature/push`, and the iOS build uses `NoPushPlatform` (no-op): the
Notifications screen says "not available on this device". To turn it on, do the steps below
on a Mac, then bind an iOS `PushPlatform`. Product rules: `docs/prd-push-notifications.md`.

Same privacy rule as Android: **no FCM token before the user opts in**, and opt-out deletes it.

## 1. Apple and Firebase

1. Apple Developer › Keys › **+** › enable **Apple Push Notifications service (APNs)** › download
   the `.p8` file. Note the **Key ID** and the **Team ID**.
2. Firebase console › Project settings › **Cloud Messaging** › Apple app configuration › **APNs
   Authentication Key** › upload the `.p8` with its Key ID and Team ID.
   - **Dev or prod key:** an APNs *authentication key* (`.p8`) is not tied to an environment. The
     same key signs sandbox and production pushes, so upload it once (Firebase shows both slots;
     the one key is enough). Which APNs server is used depends on the device token. Debug builds
     signed with a development profile (`aps-environment = development`) get sandbox tokens.
     TestFlight and App Store builds get production tokens. FCM reads the type from the
     provisioning profile when you set `apnsToken` (below).
   - Only if you use the older *certificates* (`.p12`) instead of a key, upload **two**: the
     development certificate for debug builds and the production certificate for TestFlight and
     the App Store. A development-only certificate is the usual reason "TestFlight gets nothing".
3. Make sure `GoogleService-Info.plist` of the iOS app is in `iosApp/iosApp` (it already is if
   Firebase Auth works on iOS).

## 2. Xcode

1. Target `iosApp` › **Signing & Capabilities**:
   - **+ Push Notifications**;
   - **+ Background Modes** › tick **Remote notifications** (needed for `content-available`
     data messages, section 3).
2. Swift Package Manager: add `https://github.com/firebase/firebase-ios-sdk` (same version line
   as the other Firebase products) and add **FirebaseMessaging** to the target.
3. `Info.plist`:
   - `FirebaseAppDelegateProxyEnabled` = `NO` (swizzling off): the app passes the APNs token and
     every received message to FCM explicitly (section 3);
   - `FirebaseMessagingAutoInitEnabled` = `NO`: no token is created at start. The bridge turns
     auto-init on only while the user has notifications switched on (same as Android's
     `firebase_messaging_auto_init_enabled=false`).

## 3. AppDelegate (Swift)

With swizzling off, FCM sees a message only when the app calls
`Messaging.messaging().appDidReceiveMessage(userInfo)`. Do it in **all three** places below
(`willPresent`, `didReceive`, `didReceiveRemoteNotification`), or delivery analytics and some
SDK behaviour are lost.

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
            // Opt-in: allow a token and fetch it (it also arrives through the delegate below).
            fetchToken: { done in
                Messaging.messaging().isAutoInitEnabled = true
                Messaging.messaging().token { token, _ in done(token) }
            },
            // Opt-out: no new token, and the current one is deleted at FCM.
            deleteToken: { done in
                Messaging.messaging().isAutoInitEnabled = false
                Messaging.messaging().deleteToken { _ in done() }
            },
            openSettings: { UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!) }
        )
        // Already authorized earlier: register again at every start (the APNs token can change).
        UNUserNotificationCenter.current().getNotificationSettings { s in
            IosPushBridge.shared.onAuthorization(granted: s.authorizationStatus == .authorized ? KotlinBoolean(value: true)
                : s.authorizationStatus == .denied ? KotlinBoolean(value: false) : nil)
            if s.authorizationStatus == .authorized {
                DispatchQueue.main.async { application.registerForRemoteNotifications() }
            }
        }
        return true
    }

    // APNs token -> FCM (needed with swizzling off). FCM picks sandbox/production from the profile.
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // FCM token -> Kotlin. PushManager stores it, writes it to the device document when signed in,
    // and subscribes the topics again (a topic call made before the APNs token existed fails on iOS).
    // See section 6 about the deprecation of this callback.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        IosPushBridge.shared.onToken(token: fcmToken)
    }

    // Foreground: show the banner, unless the user switched notifications off in the app (R7).
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let userInfo = notification.request.content.userInfo
        Messaging.messaging().appDidReceiveMessage(userInfo)
        completionHandler(IosPushBridge.shared.showMessages() ? [.banner, .sound, .list] : [])
    }

    // Tap: pass the payload "link" to Kotlin; it goes through PushLinkPolicy (the allowlist).
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        Messaging.messaging().appDidReceiveMessage(userInfo)
        IosPushBridge.shared.onOpened(link: userInfo["link"] as? String)
        completionHandler()
    }

    // Data-only / background messages (`content-available: 1`, no alert): iOS shows nothing by
    // itself, so the app builds a local notification from the data (same fields and limits as
    // Android: title, body, link, channel), unless the user switched notifications off.
    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                     fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        Messaging.messaging().appDidReceiveMessage(userInfo)
        let aps = userInfo["aps"] as? [String: Any]
        let hasAlert = aps?["alert"] != nil
        guard !hasAlert, IosPushBridge.shared.showMessages(),
              let msg = IosPushBridge.shared.dataMessage(title: userInfo["title"] as? String,
                                                         body: userInfo["body"] as? String,
                                                         link: userInfo["link"] as? String,
                                                         channel: userInfo["channel"] as? String)
        else { completionHandler(.noData); return }
        let content = UNMutableNotificationContent()
        content.title = msg.title
        content.body = msg.body
        content.sound = .default
        if let link = msg.link { content.userInfo = ["link": link] }   // checked again on tap
        content.threadIdentifier = msg.channel                         // groups like Android channels
        let id = (userInfo["gcm.message_id"] as? String) ?? UUID().uuidString
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: nil)) { _ in
            completionHandler(.newData)
        }
    }
}
```

In `iOSApp.swift`, add `@UIApplicationDelegateAdaptor(AppDelegate.self) var delegate`.

Notes on data-only messages:
- iOS throttles background (`content-available`) pushes and drops them when the app was
  force-quit. They are not guaranteed. For anything the user must see, send a `notification`
  message (with `apns.payload.aps.alert`); keep data-only for silent extras.
- From `firebase-admin`, a data-only iOS message needs
  `apns: { headers: { "apns-push-type": "background", "apns-priority": "5" }, payload: { aps: { "content-available": 1 } } }`.

## 4. Kotlin bridge (`iosMain`)

```kotlin
package tss.t.tsiptv.feature.push

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

/** Called from Swift (AppDelegate). */
object IosPushBridge {
    private var request: ((done: (Boolean) -> Unit) -> Unit)? = null
    private var sub: ((String, (Boolean) -> Unit) -> Unit)? = null
    private var unsub: ((String, (Boolean) -> Unit) -> Unit)? = null
    private var fetch: (((String?) -> Unit) -> Unit)? = null
    private var delete: ((() -> Unit) -> Unit)? = null
    private var settings: (() -> Unit)? = null
    internal val token = MutableStateFlow<String?>(null)
    internal var granted: Boolean? = null

    fun attach(
        requestPermission: (done: (Boolean) -> Unit) -> Unit,
        subscribe: (String, (Boolean) -> Unit) -> Unit,
        unsubscribe: (String, (Boolean) -> Unit) -> Unit,
        fetchToken: ((String?) -> Unit) -> Unit,
        deleteToken: (() -> Unit) -> Unit,
        openSettings: () -> Unit,
    ) {
        request = requestPermission; sub = subscribe; unsub = unsubscribe
        fetch = fetchToken; delete = deleteToken; settings = openSettings
    }

    fun onAuthorization(granted: Boolean?) { this.granted = granted }
    fun onToken(token: String?) { this.token.value = token }
    fun onOpened(link: String?) = manager().onNotificationOpened(link)

    /**
     * Reads the stored choice: a background message can start the process before
     * PushManager.start() loaded the settings (same as Android's messaging service).
     */
    fun showMessages(): Boolean = runCatching { runBlocking { manager().showMessagesStored() } }.getOrDefault(false)

    /** A data message, with the same limits and channel fallback as Android (PushMessage.from). */
    fun dataMessage(title: String?, body: String?, link: String?, channel: String?): PushMessage? =
        PushMessage.from(null, null, buildMap {
            title?.let { put("title", it) }; body?.let { put("body", it) }
            link?.let { put("link", it) }; channel?.let { put("channel", it) }
        })

    private fun manager() = GlobalContext.get().get<PushManager>()

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
    internal suspend fun fetchToken() {
        val f = fetch ?: return
        val d = CompletableDeferred<String?>()
        f { d.complete(it) }
        d.await()?.let { token.value = it }
    }
    internal suspend fun deleteToken() {
        val f = delete ?: return
        val d = CompletableDeferred<Unit>()
        f { d.complete(Unit) }
        d.await()
        token.value = null
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
    override suspend fun refreshToken() = IosPushBridge.fetchToken()   // only called while opted in
    override suspend fun deleteToken() = IosPushBridge.deleteToken()   // opt-out
    override fun systemLanguage(): String = NSLocale.currentLocale.languageCode
}
```

Bind it in the iOS Koin module, which loads after `castSyncModule`:
`single<PushPlatform> { IosPushPlatform() }`. Start the manager once at app start, with
`GlobalContext.get().get<PushManager>().start()`, e.g. from `MainViewController`.

What the common `PushManager` already does for iOS (nothing extra to write):
- it calls `refreshToken()` only while notifications are on, and on opt-out it unsubscribes every
  topic **first**, then calls `deleteToken()`, then removes `fcmToken` from the device document;
- when a new token arrives it subscribes the topics again. On iOS this is what makes the first
  opt-in work, because `subscribe(toTopic:)` fails until the APNs token has reached FCM. Android
  does the same, for a token that FCM rotated;
- it writes the token to the device document once per token and registration.

There are no notification channels on iOS. `refreshChannelNames` stays the default no-op, and the
`channel` value only sets `threadIdentifier`.

## 5. Test

- Run on a **real device**: the simulator receives remote pushes only on recent Xcode with an
  Apple silicon Mac.
- Before opting in: no token in the log, and `Messaging.messaging().isAutoInitEnabled == false`.
- Profile › Notifications › turn on › allow → a token arrives and the topics are subscribed.
- Firebase console › Messaging › **Send test message** to the token (a debug log or temporary
  UI can show it), or target topic `all`.
- Check the banner in the foreground, and the tap with `link` = `tsiptv://addons`.
- A data-only message with `content-available` while the app is in the background shows a local
  notification. It shows nothing after opt-out.
- Turn notifications off: the token is deleted, so a "Send test message" to the old token fails.
- Debug builds get sandbox tokens. Check a TestFlight build once, because it uses production APNs.

## 6. FCM registration tokens → Firebase Installation IDs (migration note)

Firebase has announced that the registration-token API is deprecated in favour of a registration
keyed by the **Firebase Installation ID (FID)**. The deprecated parts are
`messaging(_:didReceiveRegistrationToken:)`/`token` on iOS, and `onNewToken`/`getToken` on
Android. The new parts are a registration callback (`didReceiveRegistration` on iOS, `onRegistered`
on Android) and sending addressed by FID. The exact names and the first SDK versions that have
them were not checked from this machine. Read the Firebase release notes for the BoM or SPM
version you adopt before wiring them.

**Plan (both platforms):**
1. Nothing changes for topics. Our sends go to topics (`all`, `updates`, `lang_*`), so they are
   unaffected. Per-device sends are not used yet: `fcmToken` is stored only for a later "notify my
   devices" feature.
2. `PushPlatform.token` stays an opaque "push address" string. On the new API:
   - iOS: the bridge fills it from `didReceiveRegistration` instead of
     `didReceiveRegistrationToken`;
   - Android: `AndroidPushPlatform` fills it from `onRegistered` instead of
     `onNewToken`/`getToken`.

   `PushManager`, the rules (`fcmToken` is a string of 1–4,096 characters) and the opt-in and
   opt-out flow stay as they are.
3. If the server-side address becomes the FID plus project, write it under a **new** field (for
   example `fcmInstallation`) next to `fcmToken`:
   - add it to `validDevice` in `firestore.rules`, with the same string limits;
   - keep both fields during the overlap;
   - drop `fcmToken` once no supported app version writes it.
4. Opt-out must still delete the registration (the new API's unregister call, or deleting the
   Firebase installation for messaging), so the "no address before opt-in, none after opt-out"
   rule and `play-store/data-safety.md` stay true.
5. Android: do it in the same release as iOS, when the Firebase BoM in `gradle/libs.versions.toml`
   ships the new API. Until then the token API keeps working, and no app change is needed.
