# Hand-off: AdMob (Android first)

Status: **implemented, not committed** · Branch: `main` · Date: 2026-10-10 · PRD:
`docs/prd-admob.md` (AC-A1 … AC-A13)

Your uncommitted work was left as it was:
- Programs avatars (`ChannelInProgramItem`, `DetailProgramItem`, `ProgramDao`,
  `InMemoryIPTVDatabase`, `XMLTVEPGParser`/`XMLTVProgramme` and its test)
- Profile icons
- `IconTitleActionItem`

`ProfileScreen.kt` got one additive change only: a `PrivacyOptions` action and entry.

## Results

| Check | Result |
|---|---|
| `:composeApp:desktopTest` | **504 tests, 0 failures** (new `AdsPolicyTest`: 11) |
| `:composeApp:compileCommonMainKotlinMetadata` | green |
| `:composeApp:assembleDebug` | green |
| `:composeApp:assembleRelease` | **R8 (`minifyReleaseWithR8`) green.** Packaging then fails on this machine's signing setup: `composeApp/keystore.properties` names key alias `ts_iptv`, which is not in `ts_iptv_android_key.jks`. That is a local keystore issue unrelated to ads. No extra keep rules were needed: `play-services-ads` and UMP ship their own consumer rules. |
| Emulator | App open, Home banner, skeleton, native ads in Home, Player and Programs, the pinned player banner, the Shopee fallback and the 24 h gate were all checked with test ads. Screenshots are in `docs/admob-screenshots/`. |

## What was built

**Common (`commonMain`)**
- `core/ads/AdsPolicy.kt` holds the pure rules:
  - the 24 h gate, `firstUseTime(stored, now, installTime)`, `remainingAdFreeMs`;
  - native slots (`nativeSlots`, `interleave`: first after 9 items, then every 11, at most 8,
    never index 0, never adjacent, never last);
  - app open freshness (< 4 h) and `canShowAppOpen`.
- `core/ads/AdsPlatform.kt` is the SDK interface, plus `NoAdMobPlatform` for desktop and iOS, and
  `expect fun platformAdsPlatform()`.
- `core/ads/AdsGate.kt` combines the 24 h clock (KeyValueStorage `ads_first_use_ms`), UMP consent,
  the platform and the TV layout into `StateFlow<AdsState(adMob, fallback)>`. It starts as NONE, so
  nothing flashes.
- `ui/ads/AdSlots.kt`:
  - `BannerAdSlot` and `NativeAdSlot` show AdMob first, a same-size skeleton with the "Ad" badge
    while loading, and on failure the Shopee fallback in the same slot, or nothing. A slot never
    shows both.
  - `NativeAdStyle` lets a native ad match its list.
  - `expect` composables `PlatformBannerAd`, `PlatformNativeAd` and `rememberBannerHeight`.
- `ui/screens/ads/ShopeeFallback.kt` picks the Shopee offer for a slot (rotating offer for
  banners, `offers[slot % n]` for native slots).

**Android (`androidMain`)**
- `core/ads/AndroidAdsPlatform.kt`:
  - **UMP:** cached consent at `Application.onCreate`; `requestConsentInfoUpdate` and
    `loadAndShowConsentFormIfRequired` in `MainActivity.onCreate`; privacy options form.
  - `MobileAds.initialize` once, on a background thread, only when `canRequestAds()`.
  - Unit IDs come from resources. Nothing runs on Android TV devices.
- `core/ads/AppOpenAdController.kt`: the app open ad loads at process start (when consent from an
  earlier session allows it). It is shown once per process, only within 4 s of process start,
  only when MainActivity is resumed, never while the player is playing, and never when older than
  4 h. A late result is discarded. Returning from background or a configuration change never
  shows it again (process flag).
- `core/ads/NativeAdCache.kt`: native ads load only for composed slots and are kept per
  `placement:slot` (3 idle at most). Evicted ones, and ones older than 1 h, are destroyed; an ad
  on screen is never destroyed.
- `ui/ads/AdSlots.android.kt`:
  - Banners are anchored adaptive `AdView`s, paused and resumed with the lifecycle and destroyed
    on dispose.
  - Native ads are `NativeAdView`s built in code: icon, "Ad" badge, headline, body and CTA
    registered as assets; AdChoices is placed by the SDK (`ADCHOICES_TOP_RIGHT`). Clicks go only
    through the AdMob view.
- `TSAndroidApplication` and `MainActivity` are wired up.
- Manifest: `AD_ID` permission, and `APPLICATION_ID` meta-data from the `${admobAppId}`
  placeholder.

**Placements**
- **Home (`homeItemList`):** the banner is the first row of the channel section, replacing the
  old Shopee "Ads" item, and the channels are interleaved with native slots.
- **Player (`PlayerScreen`):** the title/description block and the banner now sit **outside** the
  `LazyColumn`, pinned, and the list scrolls under them. The sticky-title workaround is no longer
  needed. The channel list and the details overlay use native slots, replacing the Shopee item
  every 4 channels. The fullscreen branch has no banner.
- **Programs (`ChannelXProgramListScreen`):** native slots replace the Shopee item every 5 rows,
  with a flat style (60 dp tile).
- **History:** the Shopee item is kept, but only after the 24 h period.
- **Profile:** "Privacy options" appears only when UMP says it is required.

**Desktop and iOS:** a no-op platform. AdMob slots report failure, so the Shopee fallback still
works there, with the 24 h rule. iOS was not compiled here; the new iOS files are trivial `actual`s.

**Strings (7 locales):** `ad_label` ("Ad", "Quảng cáo", …) and `privacy_options_title`.

## Decisions (see the PRD for why)

- **24 h clock:** stored first-use time, otherwise `min(now, firstInstallTime)` on Android, so
  upgraders who installed more than 24 h ago see ads at once. iOS and desktop start at the first
  launch of this version. The Shopee fallback is gated too ("no ads of any kind").
- **"Ad" label:** top-left on the skeleton and before the native headline. That matches Google's
  native guidance, which puts the attribution badge top-left, so all labels sit in one corner.
- **Sticky Home banner:** pinned under the sticky category chips when scrolled (QC round 1, see
  below).
- **TV (device or forced TV layout):** no ads and no SDK on TV devices (PRD §3).

## Where to put the real IDs (release)

The app ID and unit IDs are read from, in order:
1. a Gradle property;
2. `local.properties` (root) or `composeApp/local.properties`, both git-ignored;
3. the environment.

The keys are:

```properties
TSIPTV_ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
TSIPTV_ADMOB_APP_OPEN_UNIT=ca-app-pub-XXXXXXXXXXXXXXXX/1111111111
TSIPTV_ADMOB_BANNER_UNIT=ca-app-pub-XXXXXXXXXXXXXXXX/2222222222
TSIPTV_ADMOB_NATIVE_UNIT=ca-app-pub-XXXXXXXXXXXXXXXX/3333333333
```

A missing key falls back to Google's test ID, with a `composeApp: … is not set` warning during
release builds. **Never commit real IDs.** Debug builds always use the test IDs.

## QA switches (debug builds only; release defines them as false or 0)

- `-Ptsiptv.debugAdsNoFirstDay=true` skips the 24 h ad-free period.
- `-Ptsiptv.debugAppOpenTimeoutMs=9000` raises the app open timeout on slow x86 emulators, which
  take about 5 s to load a test ad. Real devices use the 4 s rule.
- `-Ptsiptv.debugSkipLogin=true` opens the phone layout signed out, as the TV layout already does,
  so ads can be checked without a real account.

## Emulator notes (please read)

- **I made a mistake with `emulator-5554`.** You told me to use it, and I did, but it is the AVD
  **`CL_UIS7862S_REF`**, which an earlier rule said never to touch. I noticed after installing the
  app and changing rotation, then:
  - uninstalled the app (it was not installed before);
  - restored `accelerometer_rotation=1` and `user_rotation=0`;
  - deleted `/sdcard/ui.xml`.

  Nothing else was changed there. Screenshots 09 and 10 come from that run.
- That image has no Google Play services, so native ads failed there ("Click actions were not
  properly specified"). That run confirmed the Shopee fallback.
- For everything else I created a **new AVD of my own, `TSIPTV_ADS_QA`** (Pixel 6, Android 37.1
  Google APIs Play Store image, 2 GB). It is shut down now; delete it whenever you like. Neither
  Pixel_10a nor TSLauncherReferenceTV was used.

## Screenshots (`docs/admob-screenshots/`)

| File | Shows |
|---|---|
| `01_app_open.png` | App open test ad on a cold start, over the first screen. Returning from background did not show it again (logged). |
| `02_home_banner.png` | Home banner as the first row of the channel section |
| `03_native_validator_ok.png` | AdMob native validator: "No implementation issues found" |
| `04_home_native.png` | Native ad after channel 9 in the Home list (Ad badge, icon, headline, body, INSTALL) |
| `05_player_banner.png` | Player banner pinned under title and description |
| `06_player_native_banner_pinned.png` | Player list scrolled: banner still pinned, native ad after 9 related channels |
| `07_programs_native.png` | Native ad in the Programs list (flat style) |
| `08_first_day_no_ads.png` | Fresh install without the override: no banner, no native slots, and no ad request in logcat |
| `09_home_skeleton_and_native_skeleton.png` | Skeletons with the top-left "Ad" label while loading (CL_UIS7862S_REF run) |
| `10_native_no_fill_shopee_fallback.png` | Native no-fill, so the Shopee offer took the same slot (CL_UIS7862S_REF run) |

## Open points and risks

- **AdChoices:** see QC round 1, item 4.
- **Consent:** a first run in the EEA shows the UMP form, which normally pushes the app open ad
  past 4 s, so it is skipped on that launch (by design). Set up the AdMob "Privacy & messaging"
  GDPR message before release, or UMP has nothing to show.
- **Release signing:** fix the local keystore alias to produce a signed `assembleRelease`; R8
  already passes.
- **Play Console:**
  - Ads = Yes.
  - Data safety updated (`play-store/data-safety.md`): advertising ID and device/app data
    collected and shared for advertising; approximate location from IP.
  - Host `app-ads.txt` on the developer website with the AdMob publisher line.
- **iOS:** not implemented (PRD §9). The no-op layer keeps iOS compiling in principle; it can't be
  built here.

## QC round 1 fixes

All on `TSIPTV_ADS_QA` (the AVD name was checked before every install). Screenshots are in
`docs/admob-screenshots/qc1/`.

| # | Fix | Verified |
|---|---|---|
| 1 | **Sticky Home banner, one `AdView`.** The banner is one overlay in `HomeFeedScreen`. It sits on its own list item (`HOME_BANNER_ITEM_KEY`, a spacer of the overlay's measured height + 16 dp, so it never covers channels at rest) and, once that row scrolls up, stays pinned directly under the sticky chips (or the top bar). It is placed with `Modifier.offset` from `LazyListState.layoutInfo`, so it is never re-created while scrolling. The skeleton with the "Ad" label stays while it loads. | `01`, `02`, `03`: after scrolling down and back several times, logcat shows **one** `Banner HOME_BANNER: new AdView` |
| 2 | **Player banner after rotation, no leak.** `PlatformBannerAd` creates the `AdView` inside `key(placement, widthDp)`, so a new width gets a new `AdView` in a new `AndroidView`. The old one is detached, its listener cleared and `destroy()`ed. The slot resets to "loading" (`onResult(null)`). The request is made only once the view is attached, one frame later, so a banner composed for a single frame never requests. A phone already in landscape (about to go fullscreen) composes no banner. | `04` → `05` (landscape fullscreen, no banner, **no request**) → `06`: banner back after rotation, one request at 411 dp |
| 3 | **No banner under the schedule/details overlay.** `PlayerScreen` removes `BannerAdSlot` from composition while `showDetailsScreen` (or the sticky variant) is open, so the `AdView` is destroyed and stops refreshing. | `07`: `dumpsys activity top` shows **0** `AdView`s while the overlay is open |
| 4 | **AdChoices.** The explicit `AdChoicesView` (0×0) is removed; the SDK places AdChoices itself (`NativeAdOptions.ADCHOICES_TOP_RIGHT`), and the CTA's top margin keeps that corner free. **With Google's test native units the icon still does not appear, and that is not our layout:** the SDK's own overlay containers inside the `NativeAdView` stay empty (checked with `dumpsys activity top`), the explicit `AdChoicesView` stayed empty too, `NativeAd.adChoicesInfo` is null for both test units (`/2247696110`, video `/1044960115`), and it did not change when loading with an Activity context or registering the ad after attach. Test creatives seem to carry no AdChoices. The native validator reports "No implementation issues found". **To confirm before release:** a live ad with a real unit ID on a device registered as a test device in the AdMob console (live creatives, no charge). | `13` (validator OK); icon **not** visible with test ads, see the left column |
| 5 | **Own slots for the details overlay.** New placement `PLAYER_DETAILS_NATIVE` (same native unit); `playerChannelList` takes the placement, so the main list and the overlay never share a `NativeAd`. | `08`: logcat `Native ad request: PLAYER_NATIVE:0` and later `PLAYER_DETAILS_NATIVE:0` |
| 6 | **Debug UMP geography.** `-Ptsiptv.debugUmpGeography=EEA` (also `REGULATED_US_STATE`, `OTHER`), `-Ptsiptv.debugUmpTestDevice=<hash>` and `-Ptsiptv.debugUmpReset=true` (forget stored consent). Set through `ConsentDebugSettings` only in debug builds; release defines them empty or false. Emulators are test devices automatically. **On a real phone:** run once with the geography set, then find the hash in logcat (`adb logcat -s UserMessagingPlatform`, line "Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("…")") and rebuild with `-Ptsiptv.debugUmpTestDevice=…`. | `11`: the EEA consent form over Home (Shopee fallback in the banner slot until consent); after "Consent", the AdMob banner loads; `12`: Profile shows "Privacy options" |
| 7 | **`NativeAdCache` by identity.** Entries are tracked by `NativeAd` identity (`IdentityHashMap`). An expired or replaced ad still on screen is taken out of reuse and destroyed on its last `release(ad)`, which no longer touches the newer entry. The row's `NativeAdView.destroy()` is called in `AndroidView(onRelease = …)`. | code review; native ads in all lists still load and rebind |
| 8 | `play-store/data-safety.md`: the orphan table fragment ("Shared: No", Firebase) after the location section is deleted. | — |
| 9 | Programs: the divider is drawn by `NativeAdSlot(divider = …)` only when the slot shows something, so a collapsed slot leaves no double divider. | code |
| 10 | **Robustness:** <br>• A failed banner/native slot retries when the network comes back (`AdsPlatform.networkEpoch`, from a `ConnectivityManager` default-network callback), or when the screen resumes ≥ 60 s after the failure.<br>• The app open ad treats PLAYING, BUFFERING and READY (or `isPlaying`) as playback.<br>• `MobileAds.initialize` (and so the app open load) now runs only when `AdsGate` opens AdMob (`AdsPlatform.startAdMob()`): not during the first 24 h, not without consent, never on TV. A unit test checks that the SDK is not started on a fresh install. | `09` (offline: slot collapsed) → `10` (back online: banner requested again and shown) |

**Also fixed while testing:** `AdsGate` could read the debug flag and install time before
`AndroidAdsPlatform` was initialised (a race that could keep ads off for the session). The platform
is now initialised first in `TSAndroidApplication`, and `app` is `@Volatile`.

**PRD:** R2 now notes that after day 1, a signed-out release user can get the app open ad over
Login (allowed, kept). R3, R4, edge cases, lifecycle and AC-A3/AC-A4 describe the new behaviour.

**Checks:**
- `desktopTest`: **504 tests, 0 failures** (`AdsPolicyTest` also checks `startAdMob`).
- `compileCommonMainKotlinMetadata` and `assembleDebug`: green.
- `assembleRelease`: R8 green; packaging still stops on the local keystore alias, as before.

**Debug logs (tag `TSAds`):** banner creation and request, native request and load by slot key, and
consent changes. They contain no URLs or ad content.

## QC round 2 fixes

On `TSIPTV_ADS_QA` only (the AVD name was checked before every install). Screenshots are in
`docs/admob-screenshots/qc2/`.

| # | Fix | Verified |
|---|---|---|
| N1 | **No banner request below the fold.** The Home banner overlay is composed (and so requested) only after its row, or the pinned position, has been above the bottom bar for 0.5 s; from then on the same `AdView` is kept. The 0.5 s matters: on a cold start, Now Playing and Continue watching load a little after the channels and push the row down again. | Screen shortened to 1080×1500 (`wm size`, reset afterwards): **no** `Banner HOME_BANNER` request while the row was below the fold (`01`); one request after scrolling it into view (`02`) |
| N2 | **App open request without waiting for the network.** UMP reads stored consent on a background thread right after `getConsentInformation`, so `canRequestAds()` is false for the first ~130 ms even with stored consent. Previously the app only saw `true` after `requestConsentInfoUpdate` returned, about 4 s later. Now:<br>• the ads platform is set up first in `Application.onCreate`, before Firebase and Koin;<br>• a background thread re-checks stored consent every 25 ms (for up to 3 s);<br>• `AdsGate` then starts the SDK and the request is posted to the front of the main queue (the 300 ms gate wait is gone).<br>The 4 s window now starts at the **first activity resume** ("First screen visible"), not at `Application.onCreate`. | Three cold starts with stored consent, logcat `TSAds` (times since process start; `Application.onCreate` itself starts at about +1.19 s on this emulator):<br>• stored consent ready at +1316 / +1322 / +1342 ms<br>• first screen visible at +1363 / +1365 / +1388 ms<br>• **requested at +1385 / +1379 / +1409 ms** (about 0.2 s after `onCreate`; it was +5.2 s)<br>• loaded about 4.4–5.5 s later. This emulator's network is that slow, so with the strict 4 s window the ad was skipped there; on a device that loads it in 1–3 s it shows. |
| N3 | **Swipes on the pinned banner scroll the list.** The overlay has 8 dp gaps above and below the banner, and `Modifier.scrollListOnDrag`: a vertical drag past the touch slop that starts anywhere on the overlay scrolls the list (with fling). Only drag events are consumed, in the `Initial` pass, so the ad's `AndroidView` gets a cancel for drags (no accidental click) and untouched taps. | `03` → `04`: a swipe starting on the banner text scrolled the list by two rows, and the app stayed in front (no click). A tap on "OPEN" still opened the ad (Chrome came to the front) |
| N4 | **No ads logs in release.** All `TSAds` logging goes through `AdsLog` (inline, message built lazily), which is on only when the app is debuggable (`AdsLog.enabled`, set in `Application.onCreate`). | code; release builds log nothing |

Also: the Home banner overlay is now drawn **below** the mini player (it lives in the same `Box`
as the list, before `HomeMiniPlayer`), so it can never cover it.

**Checks:**
- `desktopTest`: **504 tests, 0 failures**.
- `compileCommonMainKotlinMetadata` and `assembleDebug`: green.
- `assembleRelease`: R8 (`minifyReleaseWithR8`) green; packaging still stops on the local keystore
  alias `ts_iptv`, unrelated.

**Note for a real phone:** UMP logs the device hash even without debug settings
(`Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("…")`), which is the value for
`-Ptsiptv.debugUmpTestDevice`.
