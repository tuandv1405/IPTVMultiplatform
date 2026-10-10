# PRD — AdMob ads (Android first)

Status: **approved for implementation** · Owner: PO · Date: 2026-10-10 · Implementation hand-off:
`docs/handoff-admob.md`

## 1. Goal

Earn revenue with Google AdMob on Android phones and tablets without hurting the first experience
or playback:
- Ads stay out of the way while a user is learning the app (first 24 hours).
- Ads never cover running video.
- Every ad slot looks like the content around it, while staying clearly marked as an ad.
- The existing Shopee affiliate offers become the **fallback** in the same slots when AdMob has no
  fill.

Non-goals for this release:
- Rewarded or interstitial ads.
- Ads on Android TV, desktop or iOS (iOS is follow-up work, see §9).
- Mediation.
- In-app "remove ads" purchases.

## 2. Users' rules (verbatim intent) and decisions

| # | Rule | Decision |
|---|---|---|
| R1 | No ads of any kind during the first 24 hours of use. | The first-use time is stored persistently (`KeyValueStorage`, key `ads_first_use_ms`) the first time the ad layer starts. **On Android it is set to `min(now, PackageManager.firstInstallTime)`.** `firstInstallTime` is reliable (it survives app updates and is reset only by an uninstall), so existing users who installed more than 24 h ago are not held back again. iOS and desktop have no equivalent, so the clock starts at the first launch of this version. "No ads of any kind" includes the Shopee fallback. |
| R2 | App open ad on every cold start only. | Requested at process start (`Application.onCreate`) as soon as the consent stored by an earlier session allows ads (UMP reads it in the background; the app re-checks it every 25 ms instead of waiting for the network consent update) and the 24 h period is over. Shown **once per process**, and only if it is ready within **4 s** of the first screen becoming visible (first activity resume) while the splash or first screen is in the foreground; otherwise it is skipped for this launch. Never shown when coming back from background or after a configuration change (the process-level "already handled" flag). Never shown while the player is playing, buffering or about to play (READY). The ad must be **< 4 h** old. No fallback (Shopee has no full-screen format). The "first screen" may be **Login**: after day 1, a signed-out user of a release build can get the app open ad over the Login screen. That is allowed (it is still the cold-start first screen) and kept on purpose. |
| R3 | Banner at the top of the Home channel list. | Anchored adaptive banner as the first row of the "All channels" section, under the category chips. While it loads, a skeleton of the same height shows an "Ad" / "Quảng cáo" label in the **top-left** corner. Google's native ads guidance puts the ad attribution badge top-left, and the native slots use the same corner, so labels are consistent. On failure the Shopee item takes the slot; without one, the slot collapses. **Sticky** (QC round 1): one banner for the screen, drawn as an overlay. It sits on its own list item (a spacer of the banner's height, so it never covers channels at rest) and, once that item scrolls up, stays pinned directly under the sticky category chips. The `AdView` is created the first time its row (or the pinned position) has been above the bottom bar for 0.5 s, so a banner below the fold is never requested, and is then kept for the screen. A swipe that starts on the pinned banner (or its 8 dp gaps) scrolls the list; taps still reach the ad. |
| R4 | Banner on the player screen under the title and description. | The title block and the banner now sit **outside** the scrolling list, so neither scrolls away. Hidden in fullscreen and landscape fullscreen (also on a phone already in landscape before the player switches to fullscreen), under the schedule/details overlay (removed from composition there, so it neither shows nor refreshes), and during the first 24 h. Shopee fallback, else collapse. |
| R5 | Native ads in the Home channel list, the player channel list and the Programs list. | Frequency from `AdsPolicy.nativeSlots`: the first ad after **9** items, then one every **11** items, at most **8** per list. Never at index 0, never adjacent to another ad (or to the banner), and never the last row. That gives 2 ads per 25–30 items. Each ad uses a row template that looks like the list's items. It shows the **"Ad"/"Quảng cáo" badge** and the **AdChoices** icon (placed by the SDK, top-right), registers headline, icon, body and call to action as asset views, and is clickable only through the `NativeAdView`. |
| R6 | Shopee fallback. | In every slot (banner and native): AdMob first. On no fill or error, the Shopee item goes in the **same** slot. A slot never shows both. |

## 3. Platforms

| Platform | AdMob | Shopee fallback | Why |
|---|---|---|---|
| Android phone / tablet (touch UI) | Yes | Yes | Primary target. |
| Android TV, or the TV layout forced on a phone | **No** | **No** | Ads in AdMob formats are built for touch: no D-pad focus handling, and the AdChoices/CTA targets are not reachable by remote. AdMob policy also forbids placing ads where users click them by accident, which D-pad focus jumps cause. Full-screen app open ads on a TV launcher break the "lean back" start-up. |
| Desktop | No | Yes (as before), with the 24 h rule | No AdMob SDK for JVM desktop. |
| iOS | No (no-op implementation) | Yes (as before), with the 24 h rule | iOS AdMob (Google Mobile Ads iOS SDK + UMP iOS) needs a Mac build; follow-up. The common interface is ready. |

## 4. Placements and frequency

| Placement | Format | Unit | Fallback | Where |
|---|---|---|---|---|
| App start | App open | `app_open` | none | Over the splash or first screen, cold start only |
| Home | Anchored adaptive banner | `banner` | Shopee item | First row of the channel section |
| Home channel list | Native | `native` | Shopee item | `AdsPolicy.nativeSlots(count)` |
| Player | Anchored adaptive banner | `banner` | Shopee item | Pinned under title/description (portrait, non-fullscreen) |
| Player channel list | Native | `native` | Shopee item | `AdsPolicy.nativeSlots(count)` |
| Programs (TV schedule) list | Native | `native` | Shopee item | `AdsPolicy.nativeSlots(count)` |

The History tab's Shopee item is unchanged (it is not an AdMob placement) and follows the 24 h rule.

## 5. Consent and privacy

- **Google UMP:**
  - Each start, `requestConsentInfoUpdate`, then `loadAndShowConsentFormIfRequired` (shown in the
    EEA, the UK and Switzerland, or wherever the AdMob privacy message requires it).
  - The Mobile Ads SDK is initialised and ads are requested **only when `canRequestAds()`**.
  - Consent stored from the previous session lets the app open ad load at process start.
- **Privacy options:** Profile › "Privacy options" / "Tùy chọn quyền riêng tư" is shown only when
  `privacyOptionsRequirementStatus == REQUIRED`, and opens `showPrivacyOptionsForm`.
- **No PII in ad requests:** plain `AdRequest`. No keywords, content URL or neighbouring content
  URLs; no email or user ID.
- **Analytics rules unchanged:** no URLs, no channel names; ad events are not logged by the app.
- **Permissions:** `com.google.android.gms.permission.AD_ID` (declared explicitly; also merged from
  the SDK).
- **Play Console:**
  - App content › Ads: **Yes, contains ads**.
  - Data safety: Device or other IDs (advertising ID), App activity and App info/performance
    collected **and shared** for Advertising (see `play-store/data-safety.md`).
- **app-ads.txt:** before release, host
  `https://<developer website>/app-ads.txt` with the AdMob publisher line
  (`google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0`). The developer website must be the
  one set on the Play listing.

## 6. Ad unit IDs

- **Debug:** Google's public test IDs (app
  `ca-app-pub-3940256099942544~3347511713`, app open `…/9257395921`, adaptive banner
  `…/9214589741`, native `…/2247696110`).
- **Release:** read from Gradle properties, `local.properties` or the environment, in that order:
  `TSIPTV_ADMOB_APP_ID`, `TSIPTV_ADMOB_APP_OPEN_UNIT`, `TSIPTV_ADMOB_BANNER_UNIT`,
  `TSIPTV_ADMOB_NATIVE_UNIT`. A missing value falls back to the test ID **with a build warning**.
- The App ID reaches the manifest through a manifest placeholder. Real IDs are never committed.

## 7. Edge cases

| Case | Behaviour |
|---|---|
| Within 24 h of first use | No AdMob request at all, no Shopee item, no skeleton. Slots collapse. |
| Consent unknown or denied (`canRequestAds() == false`) | No AdMob request. Shopee fallback only. |
| Offline | AdMob fails, so the Shopee fallback shows if cached; otherwise the slot collapses. The skeleton never stays longer than the load attempt. A failed slot retries when the network comes back, or when the screen resumes at least 60 s after the failure. |
| App open still loading after 4 s | Skipped for this launch. A late result is discarded. |
| App open while a stream is playing (e.g. the process was recreated) | Not shown. |
| Rotation or configuration change | MainActivity handles configuration changes itself. A new screen width creates a new banner `AdView` (the old one is destroyed); the request is made only once the banner is attached and drawn. The app open ad is not re-shown (once per process). |
| Fullscreen player | Player banner hidden. Native ads are not in fullscreen. |
| Very short lists (< 10 items) | No native ad. |
| Search or filter changes the list | Slots are recomputed from the new count. Ads are cached per slot index, so slots don't flash. |
| Leaving a screen | Banner `AdView`s are destroyed. Native ads go back to a small LRU cache (3) and are destroyed when evicted or older than 1 h. |
| TV layout toggled on a phone | All ad slots collapse immediately. |

## 8. Lifecycle and performance

- One `AdView` per banner slot and screen width, created when the slot first composes, requested
  once it is attached and drawn, and destroyed on dispose.
- `MobileAds.initialize` runs only when the 24 h gate opens with consent (not at every start).
- Each list placement has its own slot keys (`PLAYER_NATIVE` and `PLAYER_DETAILS_NATIVE` are
  separate), so one native ad is never bound to two views.
- Native ads load lazily: only for slots that are composed (on screen or in the lazy list's
  prefetch window). There is no pre-loading for far-away slots.
- Native ads are cached by `(placement, slot)` in a small cache and destroyed when evicted or after
  60 minutes.
- The app open ad is loaded once per process and dropped after it is shown, skipped or 4 h old.

## 9. iOS follow-up (not in this release)

- Add Google Mobile Ads iOS SDK + UMP via SPM.
- Implement the same `AdsPlatform` with `GADBannerView`, `GADNativeAdView` and `GADAppOpenAd` in
  Swift, registered from `iOSApp.init()`.
- Add `GADApplicationIdentifier` and `SKAdNetworkItems` to `Info.plist`, and an ATT prompt before
  personalised ads.

## 10. Test plan

- **Unit (`commonTest`):** `AdsPolicy` covers the 24 h gate (the edge exactly at 24 h, clock moved
  backwards, debug override), `firstUseTime` with and without an install time, native slot
  positions (counts 0/9/10/30/1000, never index 0, never adjacent, never last, maximum per list),
  app open freshness (< 4 h), and app open eligibility (cold start, timeout, playing, TV, consent).
- **Build:** `desktopTest`, `assembleDebug`, `assembleRelease` (R8),
  `compileCommonMainKotlinMetadata`.
- **Manual on an emulator, with test ads and the debug override `-Ptsiptv.debugAdsNoFirstDay=true`:**
  - Home banner and its skeleton.
  - Player banner, which stays put while the list scrolls.
  - Native ads in Home, Player and Programs, with badge and AdChoices.
  - App open on a cold start, and not on return from background.
  - TV layout shows no ads.
  - Without the override, a fresh install shows no ads at all.
  - Fallback: in airplane mode with a cached Shopee list, the Shopee item takes the slot.

## 11. Acceptance criteria

- **AC-A1** A fresh install shows **no** ad (AdMob or Shopee) and makes no AdMob ad request for 24 h
  after first use. An upgrade from an install older than 24 h shows ads at once (Android
  `firstInstallTime`).
- **AC-A2** App open ad: requested within 1 s of `Application.onCreate` on a cold start with stored
  consent; shown at most once per process, only on a cold start, only if loaded within 4 s of the
  first screen appearing while the splash or first screen is in the foreground, never over a playing stream, never
  older than 4 h, and never after returning from background or rotating.
- **AC-A3** Home: a banner (or the skeleton with a top-left "Ad"/"Quảng cáo" label while loading)
  sits as the first row of the channel section and, when scrolled, stays pinned under the sticky
  category chips without covering channels at rest. One `AdView` (one request) for the screen while
  scrolling. On failure the Shopee item takes the slot, and without one the slot collapses.
- **AC-A4** Player: the banner sits right under the title and description, does not scroll with the
  channel list, shows again after a rotation, and is hidden (removed) in fullscreen, in landscape
  fullscreen, under the schedule/details overlay and during the first 24 h.
- **AC-A5** Native ads appear in the Home channel list, the player channel list and the Programs list
  at `AdsPolicy` positions: first after 9 items, then every 11, at most 8, never at index 0, never
  adjacent, never last. They look like the surrounding rows and show the "Ad" badge, AdChoices,
  headline, icon (when provided) and CTA, clickable only through the AdMob view.
- **AC-A6** Wherever AdMob fails or has no fill, the Shopee item takes the same slot. No slot ever
  shows both.
- **AC-A7** UMP: consent is requested at start, the form shows where required, no ad is requested
  unless `canRequestAds()`, and "Privacy options" appears in Profile when required and opens the
  form.
- **AC-A8** Android TV (device or forced TV layout), desktop and iOS make no AdMob request and show
  no AdMob UI. iOS and desktop compile with the no-op layer.
- **AC-A9** Debug builds use only Google test IDs. Release reads the real IDs from properties or the
  environment, warns at build time when they are missing, and never commits them.
- **AC-A10** No leaks: banners are destroyed on dispose, native ads are destroyed when evicted or
  expired, and the app open ad is released after use.
- **AC-A11** `AD_ID` is declared, `play-store/data-safety.md` declares the advertising ID and data
  shared for advertising, and app-ads.txt is documented.
- **AC-A12** The "Ad" and "Privacy options" strings exist in all 7 locales.
- **AC-A13** `assembleRelease` with R8 passes and test ads show in the release build (with test
  IDs).
