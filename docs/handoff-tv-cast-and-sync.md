# Hand-off: TV cast, send to TV, device limit, device sync

Branch: `feature/tv-cast-and-sync` (not pushed, not merged) · Date: 2026-10-10
PRD: `docs/prd-tv-cast-and-sync.md` (A cast, B send playlist, C 4-device limit, D device sync).

Build status: `:composeApp:desktopTest` **529 tests, 0 failures**; `:composeApp:assembleDebug` green;
`:composeApp:compileCommonMainKotlinMetadata` green; Firestore rules tests (`firestore-tests`,
`rules.test.js` + new `devices-sync.test.js`) **34/34 pass**. iOS not compiled (not possible here).
No Room schema change (still v6); local state is in `KeyValueStorage`.

## What was built

### Common core (`commonMain`, no I/O)
| File | Content |
|---|---|
| `feature/lan/LanModels.kt` | Protocol v1: `_tsiptv._tcp`, one JSON line per request/answer, messages (`hello`, `pair_start`, `pair_confirm`, `signed`), commands (`ping`, `cast`, `playlist`), `CastStream`, `SharedPlaylist`, error codes, size caps (64 KiB / 1.5 MiB with a ≤ 1 MiB file). Every `toString()` redacts URLs, headers, keys, codes. |
| `feature/lan/LanCrypto.kt` | HMAC-SHA256 / SHA-256 (okio), secure random (UUID v4 bytes), unbiased 6-digit code, constant-time compare, pair-key derivation, signing input. `LanKeyAgreement` (ECDH P-256) interface. |
| `feature/lan/LanValidation.kt` | http(s)-only URLs, header token/CRLF checks, cast and playlist offer checks, `host:port` parsing, hashed /24 network id. |
| `feature/lan/LanReceiverEngine.kt` | TV side, pure: pairing sessions (2 min code, 3 wrong codes, one at a time → `BUSY`, 5 failed sessions in 10 min → `LOCKED` 10 min), signed request checks (unknown sender, ±120 s, MAC, nonce replay cache of 512), events for UI. |
| `feature/lan/LanSender.kt` | Phone side: `hello` (connect by IP), pairing, signed send; drops the key on `UNPAIRED`. |
| `feature/lan/PairingStore.kt` | Paired peers per role; keys wrapped with `SecretCipher` (Keystore AES-GCM). |
| `feature/lan/LanReceiverController.kt` | Starts/stops server + mDNS around the engine. |
| `feature/lan/TvSendViewModel.kt` | Phone flow: discover, connect by IP, pair, send, playlist quota + rewarded task. |
| `feature/account/QuotaPolicy.kt` | Pure quota rules: 3 sends/day (+1 per rewarded ad, ≤ 5), 1 sync/day (+1 per 2 rewarded ads, ≤ 2), local-midnight day key, `Entitlement` hook. |
| `feature/account/SyncPlanner.kt` | Pure push build (definitions only, file playlists skipped), merge (union by id/URL, newer wins, nothing removed), replace (exact removal list). |
| `feature/account/DeviceLimit.kt`, `DeviceSessionManager.kt` | Pure 4-device decision; register / touch (6 h) / limit dialog / remote sign-out / "removed elsewhere" → sign out. Frees its own slot on sign-out via `SignOutHooks` (new, called by `AuthRepositoryImpl.signOut`). |
| `feature/account/AccountCloud.kt` | Firestore (gitlive) implementation with transactions matching the rules, plus in-memory. |
| `feature/account/QuotaService.kt`, `SyncService.kt` | Server-side quotas, rewarded ads, push / incoming / plan / apply (through `PlaylistImporter`, sources through `TsiptvSourceService`). |
| `feature/account/RewardedAdGateway.kt` | Interface, `RewardAvailability`, `UnavailableRewardedAdGateway` (AdMob implementation: see "Rewarded ads" below). |
| `feature/account/di/CastSyncModule.kt` | Koin; "not supported" defaults for iOS/desktop. |

### Android (`androidMain/feature/lan/`)
`NsdLanDiscovery` (NsdManager discover + resolve), `NsdSocketLanServer` (random-port `ServerSocket`, ≤ 4 connections, 10 s timeout, capped line reader, NSD registration with TXT `id`/`v`), `SocketLanTransport`, `JvmLanKeyAgreement` (JCA ECDH P-256, curve check), `AndroidDeviceNameProvider`, `androidLanModule` (overrides the defaults; added to `getAndroidModules()`).

### UI
- Phone player: TV icon in `MediaPlayerView`'s top bar through `LocalCastAction` (set only on the phone Player route, only when LAN is supported) → `TvSendDialog` (TV list, conditions, connect by IP, 6-digit code entry, done). `PlayerScreen.kt` is untouched (ads branch edits it).
- Send to TV: icon per row in *Chọn danh sách phát*, rows on the Connect screen, and a "Gửi tới TV" button in *Thêm danh sách phát* (typed link, or a picked file decoded to text, ≤ 1 MiB).
- TV: `LanReceiverHost` at the app root in TV mode: receiver runs only while resumed; pairing code dialog (Cancel focused), "Nhận danh sách phát … từ …?" dialog (Nhận focused, one offer at a time, 2-min TTL), notices. A cast plays via `PlayerViewModel.playCast` (ids `cast:` live / `castvod:` VOD with position); an accepted playlist opens *Thêm danh sách phát* and runs the normal importer.
- `ConnectScreen` (route `NavRoutes.Connect`; phone: Settings sheet → "TV & thiết bị"; TV: Settings → "TV & thiết bị"): receive status + `IP:port` + paired phones (TV); send + paired TVs (phone); sync (incoming Gộp/Thay thế/Để sau, push with remaining, rewarded task, current sync) and devices (remote sign-out with confirmation). Built from `TvMenuItem`, so it is D-pad navigable.
- `AccountHost` on Home: device-limit dialog, "signed out from another device", "Có bản đồng bộ mới từ …" (once per app session).
- Strings: `strings_cast_sync.xml` in all 7 locales (separate file to avoid conflicts with the ads branch). `StringResourcesLocaleTest` now reads every `strings*.xml` and has `castSyncKeysArePresent`.

### Firestore rules (`firestore.rules`)
- `users/{uid}` and its free subcollections keep owner read/write; `devices`, `meta`, `quota`, `sync` are excluded from the wildcard and have their own rules.
- `meta/devices.ids` (≤ 4, distinct) changes by exactly one id together with that device doc; device docs need their id listed; `createdAt` immutable.
- `quota/daily`: plausible day (UTC ±14 h), counters monotonic +≤1 per write within a day, caps, fresh day restarts, no delete.
- `sync/current`: owner only, ≤ 256 KiB payload, `fromDeviceId` registered, and the same write must raise `quota.syncs` by exactly 1.

## How to verify each AC
| AC | How |
|---|---|
| A1 | Phone: play any channel → TV icon top-right of the player. |
| A2 | Real devices on one router: TV app open, phone sheet lists the TV by name. (Not possible between emulators, see below.) |
| A3, A5 | `LanProtocolTest` (wrong code ×3, busy, lock, replay, tamper, unknown sender, expired) and `JvmLanPairingTest` (real ECDH). Manually done on emulators (below). |
| A4 | Done on emulators: TV opened its player with the phone's stream. VOD position: play an addon/source movie, cast, TV resumes at the position. |
| A6 | `LanProtocolTest.badInputIsRefused`, `playlistOfferWithUrlOrFile`. |
| A7 | TV: Home → press Home button; `adb shell ss -ltn` no longer lists the port. |
| A8 | Done on emulators (connect by IP). |
| A9 | New code has no logging; `LanValidationTest.toStringNeverShowsSecrets`. |
| B1–B5 | Phone (signed in): playlist row TV icon → TV → code (first time) → TV dialog → Nhận. 4th send → "Bạn đã dùng hết lượt gửi hôm nay" + rewarded task (AdMob test ad in debug). Quota unit tests in `QuotaPolicyTest`; rules in `devices-sync.test.js`. |
| C1–C4 | Sign in on a 5th device → limit dialog; remote sign-out; the removed device signs out at next start. `DeviceLimitTest` + rules tests. |
| D1–D6 | Connect screen → Đồng bộ… on device 1; device 2 shows the prompt; Gộp / Thay thế (removal list). `SyncPlannerTest` + rules tests. |
| X1–X3 | `StringResourcesLocaleTest`; builds above; no schema change. |

### End-to-end run done here (2026-10-10)
`Pixel_10a` (phone, API 37) → `TSLauncherReferenceTV` (API 36), both with `adb install -r` of this
debug build (no wipe; started with `-no-snapshot-save`, stopped afterwards). mDNS between the two
AVDs does not work (separate 10.0.2.x NATs), as expected. Used the fallback:
`adb -s <tv> forward tcp:<port> tcp:<port>` (port from `adb shell ss -ltn`; the TV's Connect screen
also shows it) and on the phone *Kết nối bằng địa chỉ IP* `10.0.2.2:<port>`. Result: TV showed the
6-digit code, the phone accepted it, "Đang phát trên …", the TV switched to its player with the
stream. Koin overrides and NSD registration verified in logcat (`_tsiptv._tcp`, TXT id/v).
Playlist send, sync and device flows were **not** run on devices (see side effect below).

**Side effect to clean up:** the Pixel_10a emulator is signed in to the user's real account, so at
app start `DeviceSessionManager` registered it in **production** Firestore:
`users/<uid>/devices/<installation id>` and `users/<uid>/meta/devices`. Delete both in the Firebase
console if unwanted (the new rules are not deployed, so the old owner-wildcard rule allowed it).

## Deploy needed (lead)
1. `firebase deploy --only firestore:rules` after review (run `cd firestore-tests && npm test` first).
   Until then the device limit, quotas and sync are not protected server-side (old rule = owner may
   write anything under `users/{uid}`).
2. Existing users: nothing to migrate; devices register on next start.

## Merge of `main` (AdMob, 0ff2dc1) — 2026-10-10
Merge commit `b5b97c4`. Git reported **no conflicts**: the strings are in `strings_cast_sync.xml`
(ads added `ad_label` / `privacy_options_title` to `strings.xml`), the cast icon lives in
`MediaPlayerView`'s top bar through `LocalCastAction`, and the new `PlayerScreen` layout (title out of
the list, pinned banner) still hosts `MediaPlayerView`, so the icon shows there unchanged. `App.kt`,
`AppModule.kt`, `AndroidModule.kt`, `build.gradle.kts`, `ProfileScreen`, `HomeFeedScreen` merged
automatically; compile and tests checked afterwards.

## Rewarded ads (AdMob), wired after the merge
- `core/ads/AdMobRewardedAdGateway.kt` (androidMain), bound in `androidLanModule`:
  - AdMob `RewardedAd`; unit `admob_rewarded_unit` = Google test unit
    `ca-app-pub-3940256099942544/5224354917` in debug, `TSIPTV_ADMOB_REWARDED_UNIT` in release (read by
    `adMobSetting` like the other units; falls back to the test unit with a warning).
  - Reward only from `OnUserEarnedRewardListener`; dismissed early → no reward; load/show failure or
    no fill (15 s timeout) → "Chưa có quảng cáo. Hãy thử lại sau."
  - Gated by the ads layer: `RewardAvailability.of(...)` = unsupported / TV layout / 24 h ad-free
    start / no UMP consent / available. `AdsGate` got two read-only properties for this
    (`tvLayout`, `adFreePeriodOver`). When not available the task button is replaced by a one-line
    reason (`reward_unavailable_*`, 7 locales).
  - One ad preloaded when a quota screen opens (send sheet for a playlist; TV & thiết bị screen when
    signed in); cached with the application context only, single use, dropped after 55 min; the
    full-screen callback is cleared after show (no Activity kept).
- `UnavailableRewardedAdGateway` is the common default (iOS / desktop). `FakeRewardedAdGateway` now
  exists only in `commonTest` (`RewardTasksTest`).
- Caps unchanged and still pending the user's decision: 1 rewarded ad = 1 send (≤ 5/day),
  2 rewarded ads = 1 sync (≤ 2/day). To change them edit `QuotaPolicy` constants **and** the matching
  numbers in `firestore.rules` (`validQuota`) and `devices-sync.test.js`.
- Not verified on a device: every rewarded flow needs a signed-in account (quota in Firestore), and
  no device flows may write to production. Covered by `RewardTasksTest` + `QuotaPolicyTest` + rules
  tests.
- Later: AdMob server-side verification to a Cloud Function that alone may raise `*Rewards`.

Build after the wiring: `desktopTest` 545 tests, 0 failures (one run hit a 5 s timeout in the ads
branch's `AdsPolicyTest.gateOnAFreshInstall…` under load; it passed on rerun and in the full rerun);
`compileCommonMainKotlinMetadata`, `assembleDebug`, `assembleRelease` (R8, unsigned) green; Firestore
tests: rules + devices/sync + web-backend 35/35; `web-http-backend.test.js` cannot run in this
worktree (needs `telegram-bot/node_modules`, not installed; unrelated to these rules).

## Known limits / not done
- Active MITM during the 2-minute pairing can brute-force the code offline (documented; fix: SPAKE2). LAN commands are signed, not encrypted.
- Quotas are client-enforced within rule caps (max 8 sends, 3 syncs a day; +14 h trick); remote sign-out is advisory (token stays valid). Server path in PRD §6/§8.
- Sync payload is owner-only but not end-to-end encrypted (PRD §5.4 decision).
- No "auto-trust same account" pairing; no iOS/desktop sender or receiver (desktop has only the ECDH class for tests).
- Synced playlists are re-downloaded without EPG fetch until the playlist is opened; sources needing 18+ confirmation are reported as failed.
- File playlists already imported cannot be sent or synced (content is never kept).
- Android 17 local-network permission not handled yet (PRD §2.3).
- Merge conflicts expected with the ads branch in `App.kt`, `AppModule.kt` (one line), `AndroidModule.kt` (one line).
