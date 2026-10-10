# PRD: Push notifications (Android first)

Status: draft for implementation · Branch: `feature/push-notifications` · Date: 2026-10-10

## 1. Goal

Let TS IPTV tell its users about app news: new versions, new guides and feature tips. Users choose
whether they receive these and which kinds. Notifications are **app news only**: never channels,
playlists, sources or addons. The app keeps its neutral-player stance (it ships no content and
recommends none).

Non-goals (v1):
- per-user or targeted marketing;
- notifications about a user's own playlists or EPG;
- rich media (images, actions);
- in-app inbox;
- iOS implementation (setup documented, code is a no-op).

## 2. Users and rules

| # | Rule | Decision |
|---|---|---|
| R1 | Opt-in, never surprising. | Android 13+ needs `POST_NOTIFICATIONS`. The app **never asks at start**. It asks only when the user opens *Profile › Notifications* and turns notifications on, with a one-line rationale first ("Get news about TS IPTV updates and new guides. No ads, no content suggestions."). Denied → the screen explains how to allow it in system settings and offers an "Open settings" button. |
| R2 | The user decides the kinds. | *Notifications* screen: master switch; **General** (`all`) and **App updates** (`updates`) topic switches. Language topic `lang_<code>` follows the app language automatically (not a switch). Switching off = unsubscribe from every topic, then delete the FCM token (on the device and at FCM) and remove `fcmToken` from the device document when signed in. |
| R3 | Deep links only to known places. | A payload `link` may open: `tsiptv://home`, `tsiptv://addons`, `tsiptv://notifications`, `tsiptv://store` (the store page), or an `https` page on `tsiptv-8bdd6.web.app` (guides, policy pages). Anything else is ignored and the app just opens Home. Never an arbitrary intent, scheme, host or file. |
| R4 | Privacy. | **No token before opt-in:** FCM auto-init is off (`firebase_messaging_auto_init_enabled=false` in the manifest); the app enables it and fetches the token only while notifications are switched on, and deletes it on opt-out (R2). While on, the token is kept on the device. If the user is **signed in and the device is registered** (4-device registry of the cast/sync feature), the token is also stored as `users/{uid}/devices/{installationId}.fcmToken`, for a later "notify my devices" feature: written after the device is registered (again after a re-registration rewrote the document), not rewritten when unchanged. No token is written when signed out. The token is never logged in release, never sent to analytics. |
| R5 | Android TV. | Same APK. Channels are created and messages are handled without crashing; most TV launchers do not show standard notifications, so TV users may not see them. The TV layout has no entry to *Notifications* (TV Settings has none, and Profile is phone-only), so push stays **off** on TV: no prompt, no topics, no token. The screen's TV note applies only if an entry is added later. A `tsiptv://notifications` link opens Home on TV. |
| R6 | Foreground and background. | Foreground: the app builds the notification itself (FCM does not show one). Background: FCM shows `notification` messages on the default channel `general`; `data`-only messages are always built by the app. Tapping opens the app and applies the `link` (R3). |
| R7 | Respect the in-app switch. | If the user switched notifications off in the app, a message that still arrives (e.g. sent to the token directly) is not shown. |

## 3. Channels

| Channel id | Name (localized) | Importance | Used for |
|---|---|---|---|
| `general` | General | default | Everything without a channel; FCM default |
| `updates` | App updates | low | `"channel": "updates"` in data |

## 4. Payload

```json
{
  "message": {
    "topic": "all",
    "notification": { "title": "New guide", "body": "How to add a Stremio-compatible addon" },
    "data": { "channel": "general", "link": "https://tsiptv-8bdd6.web.app/guides/stremio-addons/" },
    "android": { "notification": { "channel_id": "general" } }
  }
}
```

Data-only messages use `data.title` and `data.body`. Titles are cut at 100 characters and bodies at 500.

## 5. Topics

- `all`: everyone who has notifications on and **General** on.
- `updates`: **App updates** on.
- `lang_vi`, `lang_en`, `lang_de`, …: the current app language, swapped when the language changes.
- Later per-feature topics: `feature_<name>`, added as switches when needed.

## 6. Edge cases

| Case | Behaviour |
|---|---|
| Permission denied | The master switch stays off and the rationale shows the system settings shortcut. |
| Permission revoked in system settings | The screen shows "Blocked in system settings" with the shortcut. Topics stay subscribed, but nothing is shown. |
| No Google Play services | `isSupported = false`. The screen says notifications are not available on this device. |
| Token refresh | The new token is stored locally, the topics are subscribed again for it, and, when signed in, it is written to the device document. |
| Opt-out | The token is deleted (device and FCM), which drops its topic subscriptions, so there are no per-topic calls. `fcmToken` is removed from the device document. |
| Opt-out offline | The delete fails fast and stays pending (the token is not forgotten locally). It is retried when the network is validated (with spaced retries) and at every start, until it succeeds. A token that appears while switched off is deleted too. |
| Message on a cold process | The service reads the stored switch (the settings may not be loaded yet). |
| Sign-out | The `fcmToken` field leaves with the device document (it is deleted when the device frees its slot). |
| Link not allowed | It is ignored and Home opens. |
| Store link without a store app | The store page opens in the browser. |

## 7. Sending

From the Firebase console (Messaging › New campaign › topic `all`), or with `firebase-admin` from a
trusted server (e.g. the telegram-bot backend later). See `docs/handoff-push-notifications.md`.

## 8. Acceptance criteria

- **AC-P1** No permission prompt at app start. The prompt appears only after turning notifications
  on in *Notifications*, preceded by the rationale.
- **AC-P2** With the permission granted and notifications on, a message to topic `all` shows in the
  foreground and the background.
- **AC-P3** Topic switches subscribe and unsubscribe. The language topic follows the app language.
- **AC-P4** Deep links open Home, Addons, Notifications, the store page, or an allowed web page.
  Anything else opens Home only (unit-tested allowlist).
- **AC-P5** Signed out, no Firestore write happens. Signed in with a registered device, `fcmToken`
  is written to that device's document. The rules allow only a string up to 4,096 characters.
- **AC-P6** TV does not crash on receiving a message.
- **AC-P7** iOS compiles (no-op). The setup steps are in `docs/setup-push-ios.md`.
- **AC-P8** `play-store/data-safety.md` declares the FCM token. Strings are in 7 locales.
