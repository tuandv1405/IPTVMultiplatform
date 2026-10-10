# Hand-off: Push notifications (Android)

Branch: `feature/push-notifications` (from `main` 555dc63). It is not pushed or merged. Date: 2026-10-10.

- PRD: `docs/prd-push-notifications.md` (R1–R7, AC-P1…P8).
- iOS setup: `docs/setup-push-ios.md`.

## What was built

| Area | Files |
|---|---|
| Common rules: link allowlist `PushLinkPolicy`, topics `PushTopics`, payload limits `PushMessage`, channels, the `PushPlatform` interface and `NoPushPlatform` (iOS and desktop) | `feature/push/PushModels.kt` |
| `PushManager`: settings (off by default), the permission only on request, topic sync that follows the settings and the app language, the token kept locally and written to the device document when signed in, tapped-notification links | `feature/push/PushManager.kt` |
| Android: `AndroidPushPlatform` (FCM token and topics, `POST_NOTIFICATIONS` via an Activity launcher, channels `general`/`updates`), `TsFirebaseMessagingService` (shows foreground and data messages, respects the in-app switch, never logs content) | `androidMain/feature/push/AndroidPush.kt` |
| Manifest: `POST_NOTIFICATIONS`, the service, FCM default channel `general` and icon `ic_stat_notification`, `MainActivity` `launchMode="singleTop"` (taps reach `onNewIntent`) | `AndroidManifest.xml` |
| Profile › Notifications → `NotificationSettingsScreen`: rationale, master switch, General / App updates, a blocked note with Open settings, TV note. Debug builds also show the FCM token and two test buttons (a local notification to Addons, and one with a bad link) | `ui/screens/settings/NotificationSettingsScreen.kt` |
| Deep links: `MainActivity` passes the `link` extra (from our notification or from FCM's background tap, `google.message_id`) to `PushManager`, and `App.kt` applies it after Splash/Login: Home / Addons / Notifications / store page / web page on `tsiptv-8bdd6.web.app` | `MainActivity.kt`, `App.kt` |
| Token in Firestore: `DeviceSessionManager.storePushToken`, only when signed in **and** this installation is registered, through `AccountCloud.updateFcmToken` (field `fcmToken`). Rules: optional `fcmToken` string of 1–4,096 characters on device documents | `DeviceSessionManager.kt`, `AccountCloud.kt`, `firestore.rules` |
| Strings: 11 `notif_*` strings in 7 locales; Android channel names (`strings_push.xml`) in 7 locales | resources |
| Privacy: `play-store/data-safety.md` (FCM token: device ID, app functionality) | — |

**Dependency:** `com.google.firebase:firebase-messaging` from the existing Firebase BoM. The
`google-services` setup is unchanged.

## Decisions

- **No prompt at start.** The permission is asked only when the user turns the switch on.
  "Blocked" is explained only after the prompt was shown once.
- **Off by default.** Nothing is subscribed until the user opts in. Turning it off unsubscribes
  every topic.
- **Topics:** `all` (General), `updates` (App updates), and `lang_<code>` while either one is on.
  The language follows the app language, or the system language when the app follows the system.
- **TV:** channels and messages are handled. Most TV launchers don't show notifications, and the
  screen says so. Checked on my TV AVD: no crash, and the tap intent opened the app. That image has
  no FCM token (no Play services).
- **Links:** only `tsiptv://home|addons|notifications|store` and `https://tsiptv-8bdd6.web.app/…`
  (no user info, no port, no backslash, at most 2,048 characters). Anything else opens Home.

## How to send

**Firebase console:**
1. Messaging › New campaign › Notifications.
2. Set the title and text.
3. Target: **Topic** `all` (or `updates`, `lang_vi`).
4. Additional options:
   - Android notification channel `general`;
   - custom data `link` = `tsiptv://addons`, or a guide URL.

To test one device, use "Send test message" with the FCM token, which a debug build shows in
Profile › Notifications and logs with the tag `TSPush`.

**firebase-admin (Node, trusted server only, e.g. the telegram-bot later):**

```js
import admin from "firebase-admin";
admin.initializeApp({ credential: admin.credential.applicationDefault() }); // service account
await admin.messaging().send({
  topic: "all",
  notification: { title: "New guide", body: "Adding a Stremio-compatible addon" },
  data: { link: "https://tsiptv-8bdd6.web.app/guides/stremio-addons/", channel: "general" },
  android: { notification: { channelId: "general" } },
});
```

Data-only messages (`data: { title, body, link, channel }`) are always built by the app, which
respects the in-app switch.

## Verified

- `desktopTest` passes, including the new `PushTest`: allowlist, topics, payload limits, no
  prompt at start, denied keeps the switch off, topic sync, token written only when signed in.
- Firestore rules tests: **40/40**, including the new `fcmToken` test.
- `compileCommonMainKotlinMetadata`, `assembleDebug` and `assembleRelease` (R8) are in the final
  report.
- **Emulator TSIPTV_ADS_QA (API 37, Play image, headless):**
  - no prompt at start;
  - the switch showed the system prompt, and Allow turned the topics on;
  - a real FCM token was issued and shown;
  - the debug test notification appeared in the shade, and tapping it opened **Addons**;
  - background taps simulated with `am start -f 0x14000000 --es google.message_id … --es link …`:
    `tsiptv://addons` opened Addons, `https://evil.example/x` opened Home only, and the guide URL
    opened Chrome.
- **Not done:** a real FCM send. No service-account credentials are on this machine, and I did
  not send through the production project. Do the console test above: one foreground, one with
  the app in the background.

## Privacy policy note (add to the policy page)

> **Notifications.** If you turn on notifications in TS IPTV, Firebase Cloud Messaging (Google)
> gives your device a registration token. We use it only to send the notifications you chose
> (app news and updates). If you are signed in, the token is stored with your device entry in your
> account, and deleted when the device is signed out. You can turn notifications off at any time
> in Profile › Notifications or in your device settings.
