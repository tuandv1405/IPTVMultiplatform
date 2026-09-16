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
| Required or optional | Optional — user can decline tracking |
| Purposes | Analytics |

*Source: Firebase Analytics, gated by `UserTrackingService`.*

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
| Shared | No |
| Required or optional | Required |
| Purposes | Analytics |

*Firebase Analytics App Instance ID.*

---

## Data types you should answer **No** to

Confirmed absent from the codebase:

- Location (approximate or precise) — no location permission, no location API.
- Financial info — no payments, no in-app purchases.
- Health and fitness, Messages, Contacts, Calendar — not touched.
- Files and docs — the app reads a playlist file the user explicitly picks; it
  does not enumerate storage. Declare **No**.
- Audio (voice or sound recordings) — the app plays audio, it never records it.
- Web browsing history — not collected.
- Installed apps / Purchase history — not collected.

---

## Ads declaration

Play Console → App content → **Ads**: answer **Yes, my app contains ads**, and
tick the "Contains ads" badge on the store listing.

The app fetches and displays Shopee affiliate product offers
(`core/model/ShopeeAffiliateAds.kt`, `ui/screens/ads/AdsViewModel.kt`). Even
though there is no ad-network SDK and no behavioural targeting, Play counts
monetised third-party promotional content as ads. The offers are not
personalised to a user profile, so you do **not** need to declare advertising or
marketing as a data-sharing purpose.

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
