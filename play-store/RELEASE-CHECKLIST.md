# TS IPTV — Play Console release checklist

Status as of 2026-09-16. Tick items as they are done.

---

## 🔴 Blockers — the app should not ship until these are resolved

### 1. The Settings tab is a developer sample screen

`ui/screens/home/HomeBottomNavigationNavHost.kt:136` routes
`NavRoutes.HomeScreens.SETTINGS` to `PermissionExample(...)` with
`Permission.CAMERA`. A user who taps **Settings** gets a camera-permission demo,
not settings.

This also explains blocker 2. Replace the route with the real settings screen
(`ui/screens/settings/LanguageSettingsScreen.kt` already exists and is a
reasonable starting point).

### 2. `CAMERA` permission is declared but the app has no camera feature

`AndroidManifest.xml` declares `android.permission.CAMERA` and
`<uses-feature android:name="android.hardware.camera">`. The only consumer is
the sample screen above. Shipping a camera permission with no user-facing camera
feature is a common cause of policy review failure, and it will show on the
store listing.

Once blocker 1 is fixed, delete from the manifest:

```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-permission android:name="android.permission.CAMERA" />
```

…and drop `Permission.CAMERA` from `core/permission/` if nothing else uses it.

### 3. No upload key exists

The build now reads signing credentials, but there is no key yet. Create one:

```bash
keytool -genkeypair -v \
  -keystore ts_iptv_android_key.jks \
  -alias ts_iptv \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then copy `composeApp/keystore.properties.template` to
`composeApp/keystore.properties` and fill it in. Both the `.jks` and the
`.properties` file are gitignored. **Back the keystore up somewhere you will
still have it in ten years** — losing it means you can never update the app
under the same listing without Play's key-reset process.

Verify:

```bash
./gradlew :composeApp:bundleRelease
# The "no upload key configured" warning must be gone.
```

### 4. Support email is still a placeholder

Run `python web/set-support-email.py you@example.com`, then confirm:

```bash
grep -r "{{SUPPORT_EMAIL}}" web/public && echo "STILL UNSET" || echo "ok"
```

---

## 🟡 Should fix before the first public track

- [ ] **`usesCleartextTraffic="true"`** is applied app-wide. Narrow it to a
      network security config scoped to playback. See `data-safety.md`.
- [ ] **`versionName` is `ts.iptv.v25401`** — a build identifier, not a version.
      Users see this string on the listing. Change it to something like `1.0.0`
      and keep `versionCode` as the monotonic integer.
- [ ] **`targetSdk = 36`** is current; no action needed, just confirm at
      submission time that Play has not raised the floor again.
- [ ] Run the release build on a physical device once. R8 is enabled
      (`isMinifyEnabled = true`) and reflection-heavy libraries — Room,
      kotlinx.serialization, the gitlive Firebase wrappers, VLCJ — are exactly
      the kind that break only in a minified build.
- [ ] Decide whether the Shopee affiliate offers stay in v1. They are fine
      policy-wise once declared, but they raise the content-rating and data
      questions for a first submission.

---

## ✅ Done

- [x] Release signing wired into `composeApp/build.gradle.kts`, reading
      `composeApp/keystore.properties` or `TSIPTV_*` environment variables, and
      leaving the build **unsigned** rather than debug-signed when absent.
- [x] App icon replaced. The launcher previously shipped the default Android
      Studio green-robot icon. Now generated from `brand/generate_assets.py`:
      adaptive foreground + background, legacy square and round icons, and an
      Android 13+ monochrome themed layer.
- [x] `brand/play-icon-512.png` — Play Store icon, 512×512.
- [x] `brand/feature-graphic-1024x500.png` — feature graphic.
- [x] Privacy policy page — `https://tsiptv-76d8f.web.app/privacy/`
- [x] Account deletion page — `https://tsiptv-76d8f.web.app/delete-account/`
- [x] Terms of use page — `https://tsiptv-76d8f.web.app/terms/`
- [x] Store listing copy, English and Vietnamese.
- [x] Data safety answers mapped to the code.
- [x] `desktopTest` green: 66/66.
- [x] `:composeApp:bundleRelease` produces
      `composeApp/build/outputs/bundle/release/composeApp-release.aab` (22 MB).

---

## Assets to produce before submission

| Asset | Spec | Status |
| --- | --- | --- |
| App icon | 512×512 PNG, 32-bit | ✅ `brand/play-icon-512.png` |
| Feature graphic | 1024×500 PNG/JPG, no alpha | ✅ `brand/feature-graphic-1024x500.png` |
| Phone screenshots | 2–8, min 1080px on the short side, 16:9 or 9:16 | ❌ **you must capture these** |
| 7" tablet screenshots | optional, up to 8 | ❌ |
| 10" tablet screenshots | optional, up to 8 | ❌ |
| Promo video | optional YouTube URL | ❌ |

Screenshots cannot be generated from here — they need the app running with real
playlist data. Suggested set of five, in this order:

1. Home with a populated channel grid
2. A channel playing, with the programme guide visible
3. The playlist import screen (shows the app's actual purpose)
4. Programme guide / EPG timeline
5. Profile or settings

Do **not** screenshot a playlist of recognisable premium channels — that is the
single fastest way to attract an IP complaint on an IPTV listing. Use a public
test playlist such as iptv-org's free channels.

---

## Console sections to complete

| Section | Answer / source |
| --- | --- |
| App access | "All functionality is available without special access" — the app works without an account. Provide test credentials only if you gate anything. |
| Ads | **Yes, contains ads** — see `data-safety.md` |
| Content rating | See `content-rating.md` |
| Target audience | 13+ (do **not** opt into the Families programme) |
| News app | No |
| COVID-19 apps | No |
| Data safety | See `data-safety.md` |
| Government apps | No |
| Financial features | None |
| Health | None |

---

## Submission-day sequence

1. Fix the blockers above.
2. `./gradlew :composeApp:bundleRelease` → confirm the AAB is signed.
3. `firebase deploy --only hosting` → confirm all four URLs load.
4. Play Console → **Internal testing** first, never straight to production.
5. Install from the internal track on a real device and walk every screen.
6. Complete every App content section; Play blocks the production rollout until
      all of them are green.
7. Promote to closed → open → production.

Expect the first review of an IPTV app to take longer than average, and expect
questions. The "provides no content" framing in the listing copy and on the
landing page is there to answer them before they are asked.
