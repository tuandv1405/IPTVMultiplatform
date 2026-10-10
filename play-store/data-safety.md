# Data safety form — answers

Play Console → Policy → App content → **Data safety**.

Every answer below is traced to code in this repository, so it can be re-checked
when the app changes. **If you change what the app collects, change this file in
the same commit.**

---

## Section 1 — Data collection and security

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** — all Firebase SDK traffic is HTTPS. *(See the caveat on cleartext traffic at the bottom.)* |
| Do you provide a way for users to request that their data be deleted? | **Yes** — `https://tsiptv-8bdd6.web.app/delete-account/` |

---

## Section 2 — Data types

### Personal info → Email address

| Field | Answer |
| --- | --- |
| Collected | Yes |
| Shared | No |
| Processed ephemerally | No |
| Required or optional | **Optional** — the app works without an account |
| Purposes | App functionality; Account management |

*Source: `feature/auth/data/repository/AuthRepositoryImpl.kt` (Firebase
Authentication), `core/tracking/UserTrackingService.kt` (email used as the
Analytics user ID **only** when tracking permission is granted).*

### Personal info → Name

| Field | Answer |
| --- | --- |
| Collected | Yes |
| Shared | No |
| Required or optional | Optional |
| Purposes | App functionality; Account management |

*Source: `AuthRepository.updateDisplayName`, Firebase `FirebaseUser.displayName`.*

### Personal info → User IDs

| Field | Answer |
| --- | --- |
| Collected | Yes |
| Shared | No |
| Required or optional | Optional |
| Purposes | App functionality; Account management; Analytics |

*Source: Firebase UID; `DeactivationRequest.userId` written to Firestore.*

### Photos and videos → Photos

| Field | Answer |
| --- | --- |
| Collected | Yes |
| Shared | No |
| Required or optional | Optional |
| Purposes | App functionality |

*Only the profile photo URL supplied by Google or Apple sign-in. The app does
not read the device photo library. If you would rather not declare this, strip
`photoUrl` from the user model first.*

### App activity → App interactions

| Field | Answer |
| --- | --- |
| Collected | Yes |
| Shared | No |
| Required or optional | **Required** — events are sent whether or not tracking is allowed; only the user ID depends on it |
| Purposes | Analytics |

*Source: Firebase Analytics. `UserTrackingService` gates only the Analytics
**user ID** (the email, set when tracking permission is granted); the events
themselves are not gated. They carry the detected playlist format and channel
count (`add_iptv_playlist`, `HomeViewModel`) and the hour of play
(`play_iptv_channel`, `PlayerViewModel`). Since 1.1, `add_iptv_playlist` also
carries `source_kind` (link/file), `url_scheme`, `has_epg`, `include_count`, and
for links added by URL the host (`link_host`) and a **sanitized** link (`link`):
scheme, host, port and path only, built by `AnalyticsLinks`. Usernames and
passwords (`user:pass@`), the query string (e.g. Xtream `?username=&password=`),
the fragment, `|Authorization=`-style header suffixes and secret-looking path
segments are removed before sending, and the value is capped at 100 characters.
`add_addon` carries only the addon's host and scheme (addon paths hold the user's
configuration). Stream URLs, the name typed for a playlist and channel names are
never sent. Imported files are read on the device only; for them only
`source_kind=file` is sent.

Play Console: because a host or sanitized link can point to a private server,
declare **App activity › Other user-generated content** (or "Other actions") as
collected for Analytics, not shared, and mention it in the privacy policy.*

### App info and performance → Crash logs

| Field | Answer |
| --- | --- |
| Collected | Yes |
| Shared | No |
| Required or optional | **Required** |
| Purposes | Analytics (crash diagnostics) |

*Source: Firebase Crashlytics (`libs.firebase.crashlytics`).*

### App info and performance → Diagnostics

| Field | Answer |
| --- | --- |
| Collected | Yes |
| Shared | No |
| Required or optional | Required |
| Purposes | Analytics |

*Device model, OS version, app version — collected by Crashlytics and
Analytics.*

### Device or other IDs

| Field | Answer |
| --- | --- |
| Collected | Yes |
| Shared | **Yes** (advertising, through Google AdMob) |
| Processed ephemerally | No |
| Required or optional | Required |
| Purposes | Analytics; **Advertising or marketing** |

*Firebase Analytics App Instance ID (analytics, not shared). Since the AdMob
release (`docs/prd-admob.md`): the **Android advertising ID** (`AD_ID`
permission) is collected by the Google Mobile Ads SDK and shared with Google for
advertising, including ad personalisation where the user consents through Google
UMP. AdMob is Android phone/tablet only; no ads, and no AdMob SDK calls, on
Android TV.*

### App activity → Other actions (ads) / App info and performance (ads)

| Field | Answer |
| --- | --- |
| Collected | Yes |
| Shared | **Yes** (Google AdMob) |
| Required or optional | Required |
| Purposes | **Advertising or marketing**; Analytics; Fraud prevention, security and compliance |

*The Google Mobile Ads SDK collects ad interactions (impressions, taps), device
information (model, OS, screen, language, network type), app performance and
diagnostics, and an approximate location derived from the IP address, and shares
them with Google for serving, measuring and protecting ads. See Google's
"Data disclosure" page for the Mobile Ads SDK and copy its current answers. The
app itself adds **no** user data to ad requests (no keywords, no content URLs,
no email or user ID).*

### Financial info → Purchase history (subscriptions)

| Field | Answer |
| --- | --- |
| Collected | **Yes** (from the subscriptions release, `docs/prd-subscriptions.md`) |
| Shared | No |
| Processed ephemerally | No |
| Required or optional | **Optional** — only when the user buys a subscription |
| Purposes | App functionality; Account management; Fraud prevention, security and compliance |

*Google Play processes the payment; the app and our server never see card or
bank details. What we keep: the Play purchase token, product and base plan,
subscription state, expiry and auto-renew flag, linked to the signed-in account
(`telegram-bot/src/billing`, Firestore `billing_purchases`, `billing_accounts`
and `users/{uid}/entitlements/current`), so the plan unlocks features on the
user's other devices and cannot be claimed by another account. The purchase is
tagged in Play with a SHA-256 hash of the account id (`obfuscatedAccountId`),
never the email. The data is deleted with the account. Until the billing server
is deployed, the purchase state stays on the device and in Google Play only;
declare it anyway, because the server is part of the design.*

**Do not** declare "Financial info › Payment info": no payment details are
collected.

### Location → Approximate location

Declare **Collected and shared for Advertising** (derived from the IP address by
the Mobile Ads SDK), unless Google's current SDK disclosure says otherwise.

---

## Data types you should answer **No** to

Confirmed absent from the codebase:

- Precise location — no location permission, no location API. (Approximate
  location: see AdMob above.)
- Financial info › Payment info, Credit score, Other financial info — Google
  Play handles payments. (Purchase history: **Yes** since the subscriptions
  release, see above.)
- Health and fitness, Messages, Contacts, Calendar — not touched.
- Files and docs — the app reads a playlist file the user explicitly picks; it
  does not enumerate storage. Declare **No**.
- Audio (voice or sound recordings) — the app plays audio, it never records it.
- Web browsing history — not collected.
- Installed apps — not collected.

---

## Ads declaration

Play Console → App content → **Ads**: answer **Yes, my app contains ads**, and
keep the "Contains ads" badge on the store listing.

Since the AdMob release the app shows Google AdMob ads (app open, banner and
native; `docs/prd-admob.md`), with the Shopee affiliate offers
(`core/model/ShopeeAffiliateAds.kt`, `ui/screens/ads/AdsViewModel.kt`) as the
fallback when AdMob has no fill. Consent is collected with Google's User
Messaging Platform (EEA, UK, Switzerland). Declare **Advertising or marketing**
as a purpose for the data above, and set up the AdMob privacy & messaging
(GDPR and US state regulations) messages in the AdMob console before release.

**app-ads.txt**: host `app-ads.txt` at the root of the developer website listed
on the Play Store page, with the line AdMob shows under *Apps › app-ads.txt*
(`google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0`).

---

## Caveat to resolve before submitting

`composeApp/src/androidMain/AndroidManifest.xml` sets
`android:usesCleartextTraffic="true"`, which lets the player reach `http://`
streams. This does not make the *Firebase* answer above untrue — the user data
listed here always goes over HTTPS — but reviewers do look at it.

Recommended: replace the blanket flag with a network security config that allows
cleartext only for playback, and keep it off for everything else. If you keep the
blanket flag, be ready to explain in the review notes that it exists because
user-supplied IPTV sources are frequently plain HTTP.
