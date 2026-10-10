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
- **Off by default, no token before opt-in.** FCM auto-init is off in the manifest. Nothing is
  subscribed and no token exists until the user opts in. Turning it off unsubscribes every topic,
  deletes the token and removes `fcmToken` from the device document.
- **Topics:** `all` (General), `updates` (App updates), and `lang_<code>` while either one is on.
  The language follows the app language, or the system language when the app follows the system.
- **TV:** channels and messages are handled. TV has no entry to Notifications, so push stays off
  there (no prompt, no token). Checked on my TV AVD: no crash, and the tap intent opened the app. That image has
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

## Privacy policy

The notifications paragraph is on the policy page now: `web/public/privacy/index.html`, section 1.7
in vi and en, plus the permission row and Cloud Messaging under processors. It is **not
deployed**. Deploy it with the web hosting before this ships.

## QC round 1 fixes

`main` was merged in first. The 7 `strings.xml` conflicts were resolved by keeping both blocks.

| # | Fix | Files |
|---|---|---|
| B1 | Reopening from Recents no longer replays the notification link: `handlePushIntent` returns when `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` is set | `MainActivity.kt` |
| B2 | **No token before opt-in.**<br>• Manifest sets `firebase_messaging_auto_init_enabled=false`.<br>• `PushManager` calls `refreshToken()` (auto-init on, then fetch) only while the switch is on.<br>• Opt-out unsubscribes every topic first (a topic call would create a token again), then `deleteToken()` (auto-init off and FCM `deleteToken`). It then clears the local token and removes `fcmToken` from the device document when signed in (`AccountCloud.clearFcmToken`, `FieldValue.delete`).<br>• `data-safety.md`, the PRD and the policy page now say this. | `AndroidManifest.xml`, `AndroidPush.kt`, `PushManager.kt`, `PushModels.kt`, `AccountCloud.kt` |
| B3 | **Token write after registration.**<br>• `DeviceSessionManager.registration` is a "registered uid" flow: it is set only after `check()` confirms the device or `register()` succeeds. Each full `registerDevice` write is a new generation, which clears the `push_token_stored` marker, so the token is written again after the `set`.<br>• `PushManager` combines token, switch and registration. It skips the write when `uid\|token` is unchanged.<br>• `storePushToken(registration, token?)` refuses a stale registration.<br>• The in-memory cloud now drops `fcmToken` on `registerDevice`, like Firestore's `set`. | `DeviceSessionManager.kt`, `PushManager.kt`, `AccountCloud.kt` |
| B4 | A Home link also selects the Home bottom tab (`PushManager.homeTabRequested`, handled in `HomeBottomNavigationScreen`) | `App.kt`, `HomeBottomNavigationScreen.kt`, `PushManager.kt` |
| B5 | Notifications paragraph in the policy, vi and en (section 1.7). Also a permission row, Cloud Messaging under processors, and the date. **Not deployed.** | `web/public/privacy/index.html` |
| B6 | The messaging service reads the stored switch (`showMessagesStored()`), so a data message on a cold process is not dropped | `AndroidPush.kt`, `PushManager.kt` |
| B7 | `SwitchRow` is one `Modifier.toggleable(value, role = Role.Switch)` node with the same TV focus look | `NotificationSettingsScreen.kt` |
| B8 | iOS doc:<br>• APNs `.p8` vs dev/prod `.p12`;<br>• `FirebaseMessagingAutoInitEnabled=NO` and fetch/delete through the bridge;<br>• with swizzling off, `appDidReceiveMessage` in `willPresent`, `didReceive` and `didReceiveRemoteNotification`;<br>• a `content-available` data message builds a local notification;<br>• stored-switch read;<br>• migration note and plan from registration tokens to FIDs, for both platforms (the exact API names still need checking against the release notes).<br>Topics are subscribed again when a new token arrives, on Android too (`tokenEpoch`). Channel names follow an in-app language change (`refreshChannelNames`; the service creates channels only when missing). PRD R5 is corrected: TV has no Notifications entry, so push stays off on TV. | `docs/setup-push-ios.md`, `PushManager.kt`, `AndroidPush.kt`, `docs/prd-push-notifications.md` |

### Verified (round 1 fixes)

**Builds and tests:**
- `desktopTest`: all pass, 79 suites. `PushTest` has 6 tests:
  - no token before opt-in, and the token is removed on opt-out;
  - the first sign-in race, reproduced with registration delayed by 300 ms after the uid is known;
  - no rewrite of an unchanged token;
  - a rewrite after a full re-registration;
  - topics subscribed again for a new token.
- `compileCommonMainKotlinMetadata` and `assembleDebug` pass.
- `assembleRelease`: R8 passes. Packaging fails only on the local keystore alias `ts_iptv`, as before.
- `firestore-tests`: 41/41 (+1 for `deleteField` of `fcmToken`; another account is refused).
- `check_guides.py`: OK.

**Emulator TSIPTV_ADS_QA (headless, debug build with `-Ptsiptv.debugSkipLogin=true`, app data cleared):**
- **B2, before opt-in:** no token. There is no `com.google.android.gms.appid.xml` and no token log.
- **B2, opt-in:** turning the switch on showed the system prompt. After Allow, a token was issued
  (`appid` holds a `|T|` entry).
- **B2, opt-out:** turning it off logged "FCM token deleted". The `|T|` entry is gone, `auto_init`
  is false and the topic queue is empty. After a cold restart there is still no token.
- **B1:** a background tap opened Addons. I then backed out of the app and reopened it from Recents
  (intent flags `0x14100000`). It opened Home, and no "notification opened" was logged.
- **B4:** a `tsiptv://home` link from the Profile tab, and from Notifications on the Profile tab,
  landed on the Home tab.
- **Regression:** the `qc-push/links.ps1` attack list gave the same results as QC. Only
  addons, notifications and home navigate; everything else opens Home.

**Not done:**
- The signed-in Firestore removal on a device: no sign-in was allowed. It is covered by the unit
  and rules tests.
- A real FCM send.
