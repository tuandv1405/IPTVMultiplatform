# TS IPTV — Play Console release checklist

Status as of 2026-09-16. Tick items as they are done.

---

## 🔴 Blockers — the app should not ship until these are resolved

### 0. Firebase Authentication is not initialised on tsiptv-8bdd6

Probing Identity Toolkit with the project's own API key returns
`CONFIGURATION_NOT_FOUND`, which means Authentication has never been turned on
for this project. **Nothing that needs an account works right now** — not
sign-up, not login, not password reset, not account deletion. The app builds and
runs, it just cannot authenticate anybody.

This cannot be done from the CLI; enabling Auth for the first time provisions the
Identity Platform config and is a console action. Three steps:

1. [Authentication](https://console.firebase.google.com/project/tsiptv-8bdd6/authentication)
   → **Get started**.
2. **Sign-in method** → **Email/Password** → enable it, and in the same panel
   enable **Email link (passwordless sign-in)**. The second switch is what the
   deletion flow uses; without it the confirm page reports that email-link
   sign-in is not enabled.
3. **Settings → Authorized domains** → confirm `tsiptv-8bdd6.web.app` is listed.
   Firebase normally adds the Hosting domain automatically.

Verify without sending yourself anything:

```bash
curl -s -X POST "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=AIzaSyAink_cGRkOZe6PcxJ7y5DCL7JIwrebCH8"   -H "Content-Type: application/json"   -d '{"requestType":"EMAIL_SIGNIN","email":"probe@example.com","continueUrl":"https://tsiptv-8bdd6.web.app/delete-account/confirm/","canHandleCodeInApp":true}'
```

`{}` means it works. `CONFIGURATION_NOT_FOUND` means step 1 is still missing,
`OPERATION_NOT_ALLOWED` means step 2 is.

### 0b. iOS still points at the retired Firebase project

`iosApp/iosApp/GoogleService-Info.plist` carries `tsiptv-76d8f`. Download the
replacement from the new project's console and overwrite it, or the iOS build
talks to a project the app no longer uses.


### ~~1. The Settings tab is a developer sample screen~~ — WRONG, and now fixed

**This entry was mistaken.** Testing on an emulator showed the gear icon on Home
opens a real bottom sheet (Select Playlist / Import Playlist / Refresh Channels /
Change Language), and the bottom navigation has no Settings tab at all
(`defNavItems` = Home, History, Programs, Profile).

`NavRoutes.HomeScreens.SETTINGS` was registered as a destination but nothing
navigated to it, so `PermissionExample` was unreachable dead code. The
destination has been deleted.

### ~~2. `CAMERA` permission is declared but the app has no camera feature~~ — fixed

Still a real problem, for a different reason than stated above: the permission
existed only to serve dead code. With that destination gone, both
`android.permission.CAMERA` and the `android.hardware.camera` `uses-feature`
have been removed from the manifest.

### 1. No upload key exists

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

### 2. Support email is still a placeholder

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

## ✅ Verified on an emulator (API 37, 2026-09-16)

Full pass on a running device, not just a build:

- [x] App launches in 2.4s, no crash.
- [x] Playlist import end to end: `iptv-org/countries/vn.m3u` → "Found 82
      channels", matching what the parser produces in `desktopTest`.
- [x] Live playback works (ANTV, Can Tho TV 2), including the media
      notification, background service and landscape fullscreen.
- [x] Watch history records and resumes.
- [x] Forgot password: dialog opens, validates, and Firebase accepts the
      request — logcat shows `[PasswordReset] accepted by Firebase`.
- [x] Sign-up, log-in and log-out.
- [x] **Fixed during this pass:** the Programs tab rendered a completely blank
      page whenever the active playlist had no EPG source. It now shows a
      "No programme guide" empty state, in all seven locales.

### Known issues found, not yet fixed

- **Fullscreen control collision.** In landscape, the elapsed-time label sits
  behind the LIVE badge and is partly hidden. (An earlier note here also claimed
  the volume slider covered the programme title; that text turned out to be
  burned into the broadcast, not drawn by the app.)
- **Affiliate offers sit above the content** on Home, History and the player,
  pushing channels below the fold. Policy-wise fine once declared; worth a
  product decision before the first screenshot-driven impression.
- **Tablet layout is a stretched phone layout.** It does not break, but a 10"
  screen shows one very wide row per channel. Play may flag the listing as not
  tablet-optimised.
- **App name is inconsistent:** `TSIPTV` in-app vs `TS IPTV` on the launcher.
- **Overlay backgrounds are transparent.** Two places draw an overlay with no
  opaque background, so whatever is underneath bleeds through: the floating
  mini-player on Home (the channel list and the group chips show through it),
  and the affiliate card on the player screen while scrolling (the channel
  title shows through it). Same class of bug in both.
- **A test account was created** during this pass, `qa.emulator@tsiptv.test`.
  Delete it in Firebase Console → Authentication when you no longer need it.

## ✅ Deployed (2026-09-17)

- [x] `firebase deploy --only hosting` → **https://tsiptv-8bdd6.web.app**
      All five pages return 200: `/`, `/privacy/`, `/delete-account/`,
      `/delete-account/confirm/`, `/terms/`.
- [x] `firebase deploy --only firestore:rules` — this also created the project's
      Firestore database, which did not exist yet.
- [x] Support email set to chintk111999@gmail.com across all pages.

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
- [x] Privacy policy page — `https://tsiptv-8bdd6.web.app/privacy/`
- [x] Account deletion page — `https://tsiptv-8bdd6.web.app/delete-account/`
- [x] Terms of use page — `https://tsiptv-8bdd6.web.app/terms/`
- [x] Store listing copy, English and Vietnamese.
- [x] Data safety answers mapped to the code.
- [x] `desktopTest` green: 71/71.
- [x] `:composeApp:bundleRelease` produces
      `composeApp/build/outputs/bundle/release/composeApp-release.aab` (22 MB).

---

## Assets to produce before submission

| Asset | Spec | Status |
| --- | --- | --- |
| App icon | 512×512 PNG, 32-bit | ✅ `brand/play-icon-512.png` |
| Feature graphic | 1024×500 PNG/JPG, no alpha | ✅ `brand/feature-graphic-1024x500.png` |
| Phone screenshots | 2–8, min 1080px on the short side, 16:9 or 9:16 | ✅ `play-store/screenshots/phone/` (5) |
| 7" tablet screenshots | optional, up to 8 | ✅ `play-store/screenshots/tablet-7in/` (2) |
| 10" tablet screenshots | optional, up to 8 | ✅ `play-store/screenshots/tablet-10in/` (2) |
| Promo video | optional YouTube URL | ❌ |

Captured from a running emulator against a **generated demo playlist**, not a
real one: invented channel names, original logos and video frames stamped
"SAMPLE CONTENT — NOT A BROADCAST", all produced by
`play-store/screenshots/make-demo-assets.py`. Screenshots taken against a real
playlist would have published third-party broadcast frames, station logos and
channel marks — the fastest route to an IP complaint on an IPTV listing.

No affiliate offer appears in any image either: every shot is a screen or scroll
position where none is rendered. See `play-store/screenshots/README.md`.

## Console sections to complete

| Section | Answer / source |
| --- | --- |
| App access | **Login required.** Emulator testing confirmed there is no guest path: logging out lands on the login screen with only Login / Sign Up. You must tick "All or some functionality is restricted" and supply working test credentials, or Play cannot review the app. |
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
