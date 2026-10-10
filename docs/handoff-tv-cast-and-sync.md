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
  - **Before release: set `TSIPTV_ADMOB_REWARDED_UNIT`** (Gradle property, `local.properties` or
    the environment, never committed) to the real rewarded unit. A release built without it shows
    Google's test ads in the quota tasks; the build only warns
    (`composeApp: TSIPTV_ADMOB_REWARDED_UNIT is not set ...`).
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

## QC round 1 fixes (2026-10-10)

The user's decisions are applied as given: rewarded ads (not ad clicks), the caps, v1 security
level, a server later. Existing Firestore docs are kept; ghost ids are handled (see 9).

| # | Fix | Where | Verified |
|---|---|---|---|
| 1 | **VOD cast position.** `playCast` no longer seeks right after `prepare`, because the async source swap dropped that seek. It waits until this item is current with `duration > 0` (like `MediaProgressTracker`'s resume, up to 60 s), then seeks, clamped to 5 s before the end. | `PlayerViewModel.playCast` | Emulators: phone paused at **05:23**, cast, and the TV player showed **05:44** about 20 s later (it had been playing since). Before the fix the TV started at 0. |
| 2 | **Slowloris and pre-auth limits.** <br>• Each connection has a total deadline of 10 s from accept: a watchdog closes the socket, and reads use the remaining time.<br>• At most **2 connections per LAN address**, 8 in total.<br>• **64 KiB pre-auth cap.** Only a `signed` request whose `senderId` is a paired phone may go on to the 1.5 MiB offer limit. This is decided once, on the first 64 KiB (`LanReceiverEngine.allowsLargeRequest`); the MAC is still checked. | `NsdSocketLanServer` (`AndroidLan.kt`) | QC's `fuzz.mjs` against the TV AVD:<br>• **slowloris** (4 sockets × 1 byte every 4 s, for 45 s): the legit `hello` was OK at every 5 s probe, and the server closed all 4 attacker sockets.<br>• F6 100 KB hello → `TOO_LARGE`; F7b 1.4 MiB signed from an unknown sender → `TOO_LARGE`; F11 idle connection → closed at 10.0 s.<br>• The per-IP cap cannot be shown through `adb forward`: everything arrives from 127.0.0.1, which is exempt from the per-IP cap (see 3). Through the forward only the total cap of 8 applies, so QC's 10-socket **flood** from one host still holds the receiver for up to 10 s. On a real LAN one host gets 2 slots and the other 6 stay free. |
| 3 | **Only LAN peers.** Connections are closed at accept unless the address is loopback, site-local (10/8, 172.16/12, 192.168/16), link-local or IPv6 ULA (fc00::/7); IPv4-mapped IPv6 is judged as IPv4. Binding stays on all interfaces, because Wi-Fi and Ethernet TVs both need it and so does NSD; the address check is the filter. Loopback is this device (`adb forward` in development). | `NsdSocketLanServer.isLocalPeer` | code; emulator traffic (loopback) is still served |
| 4 | **Wrong code or expired.** After the 3rd `WRONG_CODE`, or `EXPIRED` from confirm, the phone goes back to the TV list with "The code expired. Start again." | `TvSendViewModel.submitCode` | Emulators: after 3 wrong codes the phone showed the TV list with the message (`cast/q4_third_wrong_code_expired.png`). Starting again showed a new code on the TV, and pairing with it worked. |
| 5 | **Sender names.** `LanValidation.cleanName` removes control, format (bidi, zero-width), line/paragraph separator, unassigned and private-use characters, collapses whitespace, and caps at 40 characters without splitting a surrogate pair. A blank result is `BAD_REQUEST`. It is applied on the TV (pair_start, stored names, playlist offer names) and on the phone (TV names).<br>**Pairing cooldown:** after the TV user cancels a code screen, or it expires, new `pair_start`s get `BUSY` for 30 s. Stopping the receiver (app paused) clears the screen without a cooldown. | `LanValidation`, `LanReceiverEngine`, `PairingStore`, `LanSender` | QC's `pair1.mjs` name `"Living room remote \u202Eevil\nline2"` showed as "Living room remote evil line2" (`cast/q5_tv_clean_name.png`). A second `pair_start` right after Cancel → `BUSY`. Unit tests. |
| 6 | **start/stop race.** The host only calls `setForeground(true/false)`. One coroutine applies the latest wish in order (conflated StateFlow), and `start()` returns early when the app is no longer in the foreground. | `LanReceiverController`, `LanReceiverHost` | TV: after the Home button the port was closed (checked in `/proc/net/tcp`) |
| 7 | **`acceptingOffers` wired.** It is off while the TV's import screen is open or 5 offers are queued. Offers then get `NOT_ACCEPTING`, which the phone shows as "The TV is busy. Open TS IPTV home on the TV and try again." | `App.kt`, `LanReceiverHost`, `TvSendViewModel` | unit test (`NOT_ACCEPTING`) |
| 8 | **`recordSend` result.** After the TV accepted a playlist, the send is recorded with up to 3 tries (1 s and 2 s back-off). If it still fails, the Done step says the send was made but today's count could not be saved. | `TvSendViewModel` | code |
| 9 | **Device list = `meta/devices.ids`.** <br>• `listDevices` returns one entry per listed id, which is what the rules count.<br>• A listed id without its document is a **ghost** ("Unknown device (no details)") and can be removed like any other device (remote sign-out).<br>• Documents whose id is not listed no longer count. `registerDevice` deletes such a leftover before registering, because the rules forbid re-creating or updating it otherwise.<br>• `isDeviceRegistered` reads the ids. | `AccountCloud`, `DeviceLimit`, `ConnectScreen`, `AccountHost` | rules test "a ghost id ... can be removed" |
| 10 | **UI.** <br>• Signed-out send sheet: the sign-in text appears once, plus a **Sign in** button that opens Login.<br>• The spinner and "No TV found" never show together: after 6 s the spinner is replaced.<br>• Dialogs use `imePadding` and scroll, so the IP field and Connect stay above the keyboard (`cast/q10_ip_field_above_keyboard.png`).<br>• **Stable port:** the receiver listens on the port saved for this install. It is random the first time, then saved; a new random one is picked if the saved one is busy. Same port after an app restart (checked).<br>• Playlist names derived from a URL drop the query, the fragment and `user:password@` (`LanValidation.nameFromUrl`). | `TvSendDialog`, `ConnectWidgets`, `LanReceiverController`, `ImportIPTVScreen` | emulators (keyboard, port); unit tests (names) |
| 11 | **Rules.** <br>• `quota.networks`: a list of at most 10 strings of 1–16 characters; each index is checked.<br>• Day window: the day at `request.time - 11 h` or at `+ 13 h`, two adjacent days 24 h apart. At any moment at most two days are acceptable and days only go forward, so a client can open **at most one extra day** (the old ±14 h allowed two). Zones beyond UTC+13 fail to write, and the phone shows the "not counted" message.<br>• The rules document that `syncRewards` without `syncAds` (and `sendRewards`) cannot be prevented without server verification; the caps bound it. | `firestore.rules`, `devices-sync.test.js` | 4 new rules tests; **39/39** pass (rules, devices/sync and web-backend) |
| 12 | Handoff: `TSIPTV_ADMOB_REWARDED_UNIT` must be set before release (see above). | this file | — |

New strings in all 7 locales: `send_tv_sign_in_button`, `send_tv_not_counted`,
`lan_pair_code_expired`, `lan_tv_busy`, `devices_unknown`.

Test devices: `TSIPTV_ADS_QA` (phone) and a new AVD of mine, **`TSIPTV_CAST_TV`** (Android TV,
API 36), joined with `adb forward` (`10.0.2.2:<port>` on the phone). No sign-in and no Firestore
writes were made. Screenshots are in the session scratchpad `cast/`.

## QC round 2 fixes (2026-10-10)

| # | Fix | Where | Verified (TSIPTV_CAST_TV + QC's `lan.mjs` as a paired phone) |
|---|---|---|---|
| N1 | **Accepted playlists import in the background on the TV.** The TV no longer opens *Add IPTV Source*. It imports from wherever it is, shows "Adding "<name>"…", and shows the result dialog over any screen. The dialog is driven by `importSummary.fromLan` **state**, not the one-shot event. The import screen opens only when the user has to act: a single stream, a TS IPTV Source preview, or an error. New offers get `NOT_ACCEPTING` while that import runs. On the phone, the import screen's success dialog now also comes from state: it shows a summary produced after the screen opened, so an import that finishes before the screen is resumed is no longer lost. | `App.kt`, `HomeViewModel` (`importFromLan`, `ImportSummary.fromLan`), `LanReceiverHost` | `lan.mjs playlist "QC R2 URL"` while on TV Home, then Receive: "Adding "QC R2 URL"…", then "Congratulation … Found 6 channels" over Home; the playlist became current and the TV stayed on Home. |
| N2 | **Pairing mode.** `pair_start` is accepted only while the TV's *TV & devices* screen is shown and resumed (`LanReceiverController.setPairingOpen`). Everywhere else the TV answers `PAIRING_CLOSED`, and the phone shows "To pair, open TV & devices on the TV, then try again." Leaving the screen drops an open code. Paired phones keep casting and sending from anywhere. On top of that, codes that expire unused escalate the cooldown: 30 s, 60 s, 2 min … up to 10 min, reset by a successful pairing. The TV screen says "Pairing is open while this screen is shown". The PRD (§2.4, §3.1) is updated. | `LanReceiverEngine`, `LanReceiverController`, `ConnectScreen`, `TvSendViewModel`/`TvSendDialog`, `LanModels` | `lan.mjs start` on Home → `PAIRING_CLOSED`. On *TV & devices*: the code shows, and `confirm` pairs. After Back → `PAIRING_CLOSED` again. Unit tests cover the closed state and the escalation. |
| N3 | **Large-request gate.** <br>• **No regex search in the body.** The line must start with the exact v1 envelope `{"type":"signed","v":1,"senderId":"…","ts":…,"nonce":"…",`.<br>• **Paired senders only.** The senderId must belong to a paired phone, with `ts` inside the ±120 s window.<br>• **Rate limit.** At most 3 large grants per sender per minute.<br>• **Longer deadline for large offers.** It is raised to 10 s + 1 s per 100 KB of the offer cap, about 25.7 s, at most 30 s. A watchdog follows the moved deadline.<br>Still not closed: the MAC sits after the body, so a header MAC or a signed size declaration needs a protocol v2. A spoofed but paired senderId can therefore still make the TV read up to 1.5 MiB, at most 3 times a minute. | `LanReceiverEngine.allowsLargeRequest`, `NsdSocketLanServer` | `lan.mjs playlist … 900` (928 KB) four times in a row: three `ok`, the 4th `TOO_LARGE`. `fuzz.mjs basic`: 100 KB pre-auth and 1.4 MiB from an unknown sender → `TOO_LARGE`. |
| N4 | **The per-IP cap is per address.** It is 2 connections per remote IP address (IPv4-mapped IPv6 counted as IPv4), 8 in total. Two devices behind one address share 2 slots, e.g. a guest network that NATs into the LAN, which is rare at home. Loopback (`adb forward`) only has the total cap. | `NsdSocketLanServer` | — |

New strings (7 locales): `lan_pair_open_tv_screen`, `lan_tv_pairing_open`, `lan_tv_importing`.
Protocol: new error code `PAIRING_CLOSED`. Older phones show it as the generic "The TV refused".

## QC round 3 fixes (2026-10-10)

| # | Fix | Where | Verified (TSIPTV_CAST_TV + `lan.mjs`) |
|---|---|---|---|
| N5 | **One phone import at a time.** While a phone import runs (`importFromLan && isLoading`), the next queued offer is not shown. It appears when the import has finished, so accepting it can no longer cancel the running import (`runImport` has one job). Empty rows: a playlist row is only written together with its channels, in one transaction. The "0 Channels" rows QC saw come from the channel-id collision below (another playlist took the rows), not from the cancel. | `LanReceiverHost` (`importRunning`), `App.kt` | 928 KB "R3 Big" then "R3 Alpha" queued: "Adding "R3 Big"…", and no second dialog until it finished (13,964 channels). Then the "R3 Alpha" offer → Receive → 6 channels. Both lists complete. |
| N6 | **The import screen opened for a phone import closes again.** When nothing waits for the user any more (no single-stream, Source or error dialog, no import running), the route the LAN flow opened is popped, so later offers aren't refused as busy. An error now names the playlist: "“R3 Broken”: Network error: …". | `App.kt` (`lanOpenedImport`), `HomeViewModel.importName`, `ImportIPTVScreen` | Offer with an unreachable URL → import screen with "“R3 Broken”: Network error: network" → OK → back on Home. The next offer was accepted (`ok`), not "TV is busy". |
| N7 | `setPairingOpen` no longer launches on two scopes. It sets a StateFlow that one worker of the controller applies in order, like `setForeground`. | `LanReceiverController`, `ConnectScreen` | code |
| N8 | **Unpair on the TV asks first.** "Unpair “<name>”? It will need a new code …" with Cancel focused. After unpairing, focus moves to the next row (or the previous one; with none left, to Back). New string `lan_tv_unpair_confirm` (7 locales). | `ConnectScreen` | code |

### Channel ids shared between playlists (QC info item): fixed without a schema change

**Cause.** `channel.id` is the primary key **on its own** (schema v6). Ids come from the playlist
content (`tvg-id`, or a slug of the name), and `insertChannels` uses `REPLACE`. Importing a second
playlist with the same entries therefore *replaced* the first playlist's rows: same id, new
`playlistId`. That moved them to the new playlist (QC: "R3 big 2" lost 9,310 channels; also the
"0 Channels" rows). The carry-over (`matchPreviousChannels`) is not the cause: it only matches
within one playlist. `categories.id` had the same problem (slugged group titles).

**Fix (no Room change, still v6).** `ChannelIdNamespace`, applied in `replacePlaylistContentLocked`
(Room) and in `InMemoryIPTVDatabase`:
- An incoming channel id that another playlist already owns is stored as `<id>@<8-hex tag of the
  playlist id>`.
- Its guide id stays in `epgId`, so the EPG still matches.
- Attributes and legacy ids follow the renamed ids.
- Category ids get the same treatment.
- New DAO queries `idsOwnedElsewhere` (in chunks of 900) are reads only; the schema is unchanged.

The result is deterministic, so a refresh keeps the same ids (favourites, history, attributes
continue). Tests: `ChannelIdNamespaceTest` (in-memory) and
`RoomImportPipelineTest.twoPlaylistsWithTheSameEntriesKeepTheirChannels` (real Room, attributes and
categories included).

**Not repaired:** playlists already damaged on users' devices (rows taken earlier). A refresh of the
damaged playlist restores its channels, now namespaced. The QC TV AVD still shows the old
"R3 Alpha 0 Channels" / "R3 big 2" rows from before the fix.

**Proper fix later (Room v7).** Make the keys `channel(playlistId, id)` and
`categories(playlistId, id)` composite. The `channel_attributes` and `programs` lookups by
`channelId` then need `playlistId`, and so do `getChannelById` callers (player routes use the
channel id alone). The migration: create the new tables, copy the rows, and drop the namespacing
suffix where it is no longer needed. That is a larger change across DAOs and navigation, so it was
not done here.
