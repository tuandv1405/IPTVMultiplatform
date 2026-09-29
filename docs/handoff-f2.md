# Hand-off: F2 — Stremio-compatible addons (complete)

Status: implemented on `feature/sources-and-formats`, 2026-09-28. Not committed. It builds on the step 1 protocol core ([`handoff-f2-step1.md`](handoff-f2-step1.md)).
Spec: [`prd-stremio-addons.md`](prd-stremio-addons.md). Research: [`research/stremio-addons.md`](research/stremio-addons.md). Roadmap §2 (common and F2 deliverables).

## Results

| Check | Result |
|---|---|
| `.\gradlew.bat :composeApp:desktopTest --console=plain` | 384 tests, 0 failures, 0 errors (see "Final test totals") |
| `.\gradlew.bat :composeApp:assembleDebug` | BUILD SUCCESSFUL |
| `python web/scripts/check_guides.py` | OK (10 guide pages, 21 site pages, 20 example files) |
| Denylist grep (`strem.io`, `strem.fun`, `stremio.net`, `beamup`) in `commonMain`, `androidMain`, `iosMain`, `desktopMain` | 0 hits |
| iOS | **Not compiled.** This is a Windows machine. `SecretCipher.ios.kt` is new cinterop code (Keychain + CommonCrypto) and needs a Mac build before release. |

## What was built

### Data (Room v4 → v5)
- New tables `stremio_addons` and `media_history`, exactly as the PRD lists them (`core/database/entity/StremioEntities.kt`). `media_history` has a unique index on (`sourceKind`, `sourceId`, `itemId`, `videoId`), an index on `updatedAt`, and no foreign key.
- DAOs are in `core/database/dao/StremioDaos.kt`. `RoomStremioStores` implements both stores:
  - Removing an addon deletes its `media_history` rows in the same transaction.
  - A history upsert reuses the existing row's id, because the unique key is not the primary key.
- `AppDatabase` is `version = 5` with `AutoMigration(4, 5)` (new tables only). `composeApp/schemas/.../5.json` is exported.
- `IPTVDatabase.stremioStores` has a Room implementation and an in-memory one (`InMemoryStremioStores`, used by `InMemoryIPTVDatabase`), so the two stay in sync.
- Migration test `AppDatabaseMigrationTest.migrate4To5KeepsF1DataAndAddsAddonTables`:
  - It creates a v4 database holding a playlist, category, channel (headers, VOD flag, sort index), attribute, programme and channel history.
  - It runs `runMigrationsAndValidate(5)`, checks every F1 row and the new columns' defaults, then opens the real Room database.
  - Through the app's own code it then checks the channel and history, the new stores, an in-place upsert, and that removing an addon also deletes its history.
  - The 3→4 test still passes, and it now also runs 4→5 when Room opens the database.
  - `fallbackToDestructiveMigration(true)` is kept only as a last resort, as roadmap §2.3 allows. The 4→5 path never reaches it; the test would fail if it did.

### Security (`core/security/SecretCipher`)
- `expect fun createSecretCipher()`. Tokens are `v1:` + base64url.
- **Android**: AES-256-GCM with a non-exportable Keystore key (alias `tsiptv_addon_secrets`).
- **Desktop**: AES-256-GCM with a 32-byte key in `~/.tsiptv/addon-secrets.key`, restricted to the owner (POSIX `rw-------`, or on Windows an ACL with a single entry for the owner). It is tested in desktopTest: round trip, random IV, a tampered token and a foreign key are both rejected, and the file permissions are checked.
- **iOS**: a 64-byte key in the Keychain (`AfterFirstUnlockThisDeviceOnly`), with AES-256-CBC + HMAC-SHA256 (encrypt-then-MAC). See the deviations section.
- Transport URLs are stored only encrypted (`transportUrlEnc`). The UI shows the host only. `toString()` of every addon, stream and response type is redacted, and nothing in F2 logs.

### Wiring
- `core/stremio/di/StremioModule.kt` (`stremioModule`, added to `getCommonModules()`) provides:
  - the shared addon Ktor client, derived from the platform client. `KtorNetworkClient.httpClient` is newly exposed; no logging plugin is added.
  - `StremioClient`, with the User-Agent `TSIPTV/{AppBuildInfo.VERSION_NAME} (Stremio-addon-client)` and a refresh scope
  - the blocklist fetcher, `SecretCipher`, `AddonSettings` (over `KeyValueStorage`), `AddonRepository`, `MediaHistoryRepository`, `MediaProgressTracker`
  - four ViewModels
- `StremioKoinGraphTest` (desktopTest) resolves this graph.
- **App version**: `composeApp/build.gradle.kts` has a new `generateAppBuildInfo` task that generates `tss.t.tsiptv.AppBuildInfo.VERSION_NAME` from `appVersionName` into the commonMain sources.
- `App.kt` runs `AddonRepository.dailyMaintenance()` at start: a manifest refresh at most once a day per addon, then the blocklist check.

### Repository (`core/stremio/AddonRepository.kt`, `MediaHistoryRepository.kt`, `MediaProgressTracker.kt`, `MediaRules.kt`)
- **Add**: normalise → blocklist (host) → fetch and validate → blocklist (id) → `configurationRequired`. The preview carries the warnings and the "replace" info. Install encrypts the URL; replacing keeps the position and the enabled state.
- **Manage**: enable/disable (a blocked addon cannot be re-enabled), move up/down, reorder, remove (with its history), and "Update now" (with the version change). After 3 consecutive failures an addon shows as unreachable but keeps working from the cached manifest. If the key is lost, the addon shows `secret_lost` and is inactive.
- **Blocklist**: fetched at most once a day, and again when the addon manager opens if a day has passed. Matching addons are blocked and disabled.
- **Fan-out**:
  - Board rows: enabled, non-adult addons, in addon order.
  - Search: progressive, cancellable; enabled, non-adult addons only.
  - Meta: all matching addons in parallel; the first non-empty answer in addon order wins and later answers fill in missing fields. With no answer, the catalogue item is used; a 404 is not counted as a failure.
  - Streams: parallel and progressive, grouped in addon order. Inline `video.streams` are used exclusively, with no request.
- **History**: per video. Finished means ≥ 95 %. A finished episode is replaced in Continue watching by the next released episode, at position 0. Resume only if the saved position is between 60 s and 95 %. Stream URLs are never stored.
- `MediaProgressTracker` saves every 10 s, on pause, stop and back, and when another item takes over. It resumes with a seek once the player plays.

### Player (F1 path, kept intact for channels)
- `PlayerViewModel.playStream(item, context)` prepares the item directly, since it is not in the channel database. It uses `MediaItem.headers` from `proxyHeaders.request`, so Android/iOS apply them to every request and desktop applies UA and Referer.
- Addon item ids are `stremio:{addonId}:{videoId}`, and `verifyPlayingMediaItem` skips them.
- `resumeMediaItem` re-prepares an addon item from the item itself, for the mini player.
- New `PlayerEvent.SeekTo` / `SeekBy`.
- The analytics event `play_addon_stream` sends only the hour and a known content type. `add_addon` has no parameters.
- **Android `AndroidMediaPlayer`**: `duration` used to be read only in `prepare()`, so it stayed 0 for every VOD. It is now updated in the 500 ms loop. Live items stay 0, even with a DVR window, so the F1 LIVE chip is unchanged.
- **Phone/desktop controls**: for VOD (duration > 0) the progress line is now a real seek slider with `mm:ss / mm:ss` (or `h:mm:ss`). The old `PlayerProgress` always rendered full. Live still shows LIVE.
- **Phone `PlayerScreen`**: for addon items, related channels and the EPG are hidden.
- **TV `TvPlayerScreen`**: for addon items there is no zapping and no channel list. ◀/▶ (and rewind/fast-forward) seek ±10 s, OK and play/pause toggle playback, and the banner shows the episode label plus a position/duration bar.

### UI (phone, TV, desktop, iOS: one code base, adapted with `LocalIsTvMode`)
- **Addon manager** (`ui/screens/addons/`):
  - Entry points: Profile → **Addons** (new `ic_addons` drawable) and TV Settings → **Addons**.
  - Rows show logo, name, version · host, "Added by you", a status line and an enabled switch. The phone overflow has Move up/down, Update now, Configure (if `configurable`) and Remove (with confirmation).
  - TV: the ◀/▶ hint is shown; ◀/▶ on a focused row toggles enabled, and OK opens the row menu dialog.
  - Empty state: `addons_empty` plus the Add button.
  - Add dialog: URL field (a Paste button on phone), Continue, Cancel. TV focus order is field → Continue → Cancel, and ▼ leaves the field. Inline errors cover every error key, with the missing field named.
  - Configure flow: a Configure button opens `{base}/configure` in the browser. If there is no browser (TV), the URL is shown as selectable text; the user then pastes the configured link.
  - Preview: logo, name, version · host, description, localized types, catalogue count, provides streams, the http/p2p/adult warnings, `addon_notice`, and the 18+ checkbox (Add stays disabled until it is ticked). If the id is already installed, it shows "Replace …?" and the button reads Replace.
- **Discover** (`ui/screens/discover/DiscoverScreen.kt`, `DiscoverViewModel`, app-scoped):
  - Phone: a bottom-nav item between Home and History, present only while an addon is enabled. It disappears (and returns to Home) when the last one is removed or disabled.
  - TV: a **Discover** item above "All channels" in the rail. OK moves focus to the first Continue watching card, else the first row. ◀ returns to the rail; Back returns to the channels.
  - Content: offline banner, then the partial-failure banner with Retry, then Continue watching (with a progress line), then type tabs (All plus types in PRD order), then one row per board catalogue ("{catalog} · {addon}", See all).
  - Rows load lazily as they enter composition and show at most 20 cards.
  - Search: on phone an inline field (≥ 2 characters, 400 ms debounce, typing again cancels the previous requests, results grouped as they arrive). On TV a search button opens `DiscoverSearch` with the field focused (on-screen keyboard); the same screen serves `stremio:///search` links.
- **Catalogue** (`CatalogScreen`):
  - Genre chips from `extra.options`: single select, or multi-select up to `optionsLimit`. A required genre starts on its first option and cannot be cleared.
  - Grid cells follow `posterShape`.
  - Paging uses `CatalogPager`. A post-short-page probe failure (4xx, 500, HTML, missing root key) ends quietly; only real failures show Retry.
- **Detail and stream picker** (`ui/screens/mediadetail/`):
  - Header: background (else poster) with a scrim, poster, name, `releaseInfo`/`year`, runtime, genres, `meta_rating`, and an expandable description. Only `stremio:///` links are shown, and only when routable (the chips go to catalogue, detail or search).
  - Movie/tv: a Play button. `behaviorHints.defaultVideoId` opens that video's streams directly.
  - Series: season chips (Specials last), and episodes sorted with thumbnail, number, title, date and overview. Upcoming episodes show `meta_upcoming` and cannot be selected. The last watched episode is highlighted and, on TV, focused and scrolled into view.
  - Picker: a bottom sheet on phone, a right side panel on TV, grouped by addon with a spinner per loading addon. Rows show `name`, `description ?: title` (3 lines), the region badge, and on iOS the "may not play" badge for DASH/MKV. External links get an "opens browser" icon.
  - Unsupported streams are hidden. The overflow (phone: ⋮; TV: a focusable **More** button) has "Show unsupported streams", off by default and not remembered. Those rows show only `stream_unsupported`, greyed and not selectable, with no name, description, badge, kind or count.
  - TV focus starts on the stream from the same addon and `bingeGroup` as last time, else the first playable one.
  - With nothing playable: `stream_none_playable` and Back.
  - `externalUrl` http(s): a confirmation showing the host, then the system browser. If there is no browser, the URL is shown as text.
  - `stremio:///` external links navigate internally.
- **History** (phone History tab): a new **Movies & series** section under the channel history, one row per title. Tap opens the detail with the streams for that video; long-press or ✕ removes the title; the header ✕ clears the section.
- **Strings**: 56 PRD keys plus 19 UI keys (75) in all seven locales. There are no backslash-escaped quotes, and French uses typographic apostrophes. `StringResourcesLocaleTest.f2KeysArePresent` pins the PRD keys, and the existing test checks the key sets are equal.

### Tests added (commonTest / desktopTest)
- **commonTest**:
  - `AddonRepositoryTest` (AC-S2, S3/S4, S5, S6, S7, S18, S21 at the fake-cipher level, S22, refresh/failures, search, lost key)
  - `MediaHistoryRepositoryTest` (AC-S20 rules)
  - `MediaProgressTrackerTest` (resume seek, periodic and pause saves, hand-over)
  - `MediaRulesTest` (AC-S13 seasons, specials, upcoming, next episode, resume, labels, stream preference)
  - `DeepLinkRouterTest`
  - the step 1 core tests (see [`handoff-f2-step1.md`](handoff-f2-step1.md))
- **desktopTest**: the migration test (AC-S24), `FileKeySecretCipherTest` (AC-S21), `StremioKoinGraphTest`.

## Deviations and known gaps

1. **iOS cipher algorithm**: AES-CBC + HMAC-SHA256 instead of AES-GCM. Kotlin/Native has no public GCM (CommonCrypto's is SPI and CryptoKit is Swift-only). Both are authenticated encryption, tokens never leave the device, and the `v1:` prefix allows a later switch. **Not compiled**; build it on a Mac.
2. **Phone drag-to-reorder** is not implemented. Move up/down covers AC-S7.
3. **TV history**: TV has no media history screen of its own. Continue watching lives in TV Discover. Since QC round 1, the TV **menu key** on a Continue watching card removes the title.
4. **History "clear all"**: the channel History tab had no clear action to extend, so the Movies & series section has its own clear icon.
5. **Discover search on TV** is a separate screen (`DiscoverSearch` route), opened from the focusable search button, because a text field inside the D-pad rows traps ▲/▼.
6. **AC-S10 request log**: the lenient paging rule sends one more `skip=250` probe, which returns empty; that request is expected. On static hosts the probe's 404 ends paging silently.
7. ~~Blocklist on a device~~: replaced in QC round 1 by a **debug-only** override (`-Ptsiptv.debugAddonBlocklistUrl=…`, see "QC round 1 fixes"). Release builds always use the production URL.
8. **Seek slider for F1 VOD channels**: F1 channels with a known duration now get the same seek bar and `pos / dur` time. Live channels are unchanged.
9. `fallbackToDestructiveMigration(true)` is unchanged (roadmap: last resort only). 4→5 is a real AutoMigration and is tested.
10. The `AddonCatalog`/`MediaDetail` routes carry the catalogue item as JSON (`previewJson`), so a detail page can be built when no addon provides meta. It contains no URLs of the addon itself, only public poster links.

## QC recipe

### Fixture setup
- **Hosted sampler**: `https://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json` (F4 static files).
- **Local harness**: `node qa/stremio-fixture/server.js` on the host (Node ≥ 18, no npm install). It listens on 0.0.0.0:7000 and logs every request with its User-Agent, plus `CANCELLED` lines.
  - Android emulator (phone and TV): `http://10.0.2.2:7000/manifest.json`. `10.0.2.2` is the emulator's alias for the host.
  - Desktop, or the iOS simulator on the same Mac: `http://127.0.0.1:7000/manifest.json`.
  - A physical device on the LAN: `http://<host LAN IP>:7000/manifest.json`, with the host firewall allowing port 7000.
  - The variants are listed in the `server.js` header.
- Android allows cleartext (`usesCleartextTraffic="true"`), so `http://` fixtures work; the preview shows `addon_warn_http` for them.
- **Blocklist fixture** (AC-S22): `BLOCK_HOSTS=10.0.2.2 node qa/stremio-fixture/server.js`, or `BLOCK_IDS=org.tsiptv.publicdomain`, serves `/policy/addon-blocklist.json`. Point the app's blocklist URL at it with a proxy map-remote rule: in mitmproxy, `--map-remote "|https://tsiptv-8bdd6.web.app/policy/addon-blocklist.json|http://10.0.2.2:7000/policy/addon-blocklist.json"`. The check runs at most once a day: clear app data, or move the device clock forward a day, to trigger it again.

| AC | Steps / evidence |
|---|---|
| S1 | Fresh install: no Discover tab or rail item. Run the denylist grep over the production source sets (0 hits). |
| S2 | Profile → Addons → Add. Paste `stremio://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json` → Continue. The preview shows the https host. Add → the "Addon added" snackbar; Discover appears (phone tab / TV rail). |
| S3 | Enter `https://example.com/foo`, `https://x.example.org/stremio/v1` and `http://127.0.0.1:11470/local-addon/manifest.json`. Expect the not-manifest, legacy and local-server errors, in that order. |
| S4 | `http://10.0.2.2:7000/no-resources/manifest.json` → invalid manifest with "Missing or invalid field: resources". `/configure-required/manifest.json` → the configure flow: Configure opens `…/configure-required/configure`, or on TV shows the URL; the addon is not added. |
| S5 | `/p2p/manifest.json` → the p2p warning. `/adult/manifest.json` → Add is disabled until "I am 18 or older" is ticked. Afterwards none of its catalogues appear on Discover or in search. |
| S6 | Add `/v2/manifest.json` (same id as `/manifest.json`, version 1.0.1) → "Replace …?" and a Replace button. Afterwards there is one entry in the same position with the same enabled state. |
| S7 | Disable an addon → its rows leave Discover and its streams leave the picker. Re-enable. Move up/down → the row order on Discover and the group order in the picker change. Remove → it is gone, together with its Movies & series history rows. Removing the last addon hides Discover. |
| S8, S9 | Unit tests (`StremioUrlTest`, `ResourceMatcherTest`). |
| S10 | Local addon → See all on "Public Domain movie". Scroll to the end. The harness log shows no skip, then `skip=100`, `skip=200`, `skip=250` (empty). 250 cards, no duplicates. |
| S11 | Add `/multi/manifest.json` → See all → genre chips. Selecting Comedy requests `genre=Comedy`. Two chips can be selected at once (`optionsLimit: 2`) and the request repeats the key. |
| S12 | Search "his" → His Girl Friday. Type more quickly → `CANCELLED` lines in the harness log. Add `/slow/manifest.json` as well → after 15 s, "1 addon(s) did not respond" while the other results stay. |
| S13 | Local addon → Fleischer Superman: Season 1 lists "The Mechanical Monsters"; Specials (season 0) is last; season 2's 2099 episode shows Upcoming and cannot be selected. Unit tests cover the same rules. |
| S14 | His Girl Friday → Play → archive.org → plays on Android, iOS and desktop. |
| S15 | Local addon → Live TV → HLS Test Channel → Play. The harness log shows `GET /hls/... UA=TSIPTV-Example/1.0` for the playlist and segments. On desktop only the UA and Referer headers are applied (F1 rule). |
| S16 | Local addon → "Mixed stream kinds (fixture)" → Play. Exactly two rows (Direct, Website), with no count and no kind word. More / ⋮ → "Show unsupported streams" → two greyed, non-selectable rows showing only "Not playable in TS IPTV". **PO review.** |
| S17 | Select "Website" → "Open archive.org in your browser?" → the browser opens (phone). On the TV emulator without a browser, the URL is shown as text. |
| S18 | Local addon → "Inline streams (fixture)" → episode → the picker shows the "inline" stream. The harness log has **no** `/stream/` request. |
| S19 | Open the same catalogue twice within an hour → the second open makes no catalogue request (log). Streams: reopen the picker after 5 minutes → a new `/stream/` request, although the response says `cacheMaxAge: 604800`. |
| S20 | Play a movie for 2+ minutes and leave → it appears in Continue watching (Discover) and in History → Movies & series. Select it → the picker opens → the same stream → playback resumes within ±5 s. Seek to the end (≥ 95 %) → it leaves Continue watching. Finishing an episode offers the next one. |
| S21 | Release build logcat while adding and browsing: no transport URL. Firebase DebugView: only `add_addon` (no parameters) and `play_addon_stream` (hour, type). The Room `stremio_addons.transportUrlEnc` column holds `v1:…` ciphertext (`adb shell run-as tss.t.tsiptv sqlite3 databases/iptv_list.db "select transportUrlEnc from stremio_addons"`). `FileKeySecretCipherTest` covers desktop. |
| S22 | Build the debug APK with `-Ptsiptv.debugAddonBlocklistUrl=http://10.0.2.2:7000/policy/addon-blocklist.json` (or use the proxy mapping above), then: adding the blocked host → `addon_blocked`. For an installed addon: add its id or host to `BLOCK_IDS`/`BLOCK_HOSTS`, restart the harness, and trigger the daily check → the addon shows Blocked, is disabled, and its switch cannot be turned on. |
| S23 | TV emulator with the D-pad only: Settings → Addons (◀/▶ toggles, OK opens the menu). Add dialog order: field → Continue → Cancel. Discover rail, then rows (▲/▼ between rows; ◀ from the first card goes to the rail). Detail: initial focus on Play or the last episode; the season chips are above the episodes. Picker: a right panel with More at the bottom; Back closes it, then leaves the detail. |
| S24 | `AppDatabaseMigrationTest` (CI). Manual: install the F1 build, create data, then install this build → everything is kept. |
| S25 | `StringResourcesLocaleTest` (key sets equal, F2 keys present, no escaped quotes). |

## QC round 1 fixes

No emulator or device was started or touched for these fixes. Verification below uses unit tests where possible, otherwise the steps QC should run.

| # | Fix | How to verify |
|---|---|---|
| **B1** Resume crash / lost resume point | `AndroidMediaPlayer`: every control method (`prepare`, `play`, `pause`, `stop`, `seekTo`, speed, volume, mute, release) runs on `Dispatchers.Main.immediate`, whatever the caller's thread. `MediaProgressTracker` does the resume seek only once **this** item is loaded (current media id matches and its duration is known), on Main. Nothing is saved before that seek has been applied. | `MediaProgressTrackerTest.resumesSavesPeriodicallyAndOnPause` (no seek or save before the duration is known). Device: Continue watching → same stream → resumes within ±5 s, no crash. |
| **B2** Duplicate ids in a row or search | `StremioClient.catalog()` removes duplicate ids (search results come from the same call). | `StremioClientTest.duplicateCatalogIdsAreDroppedBecauseTheyAreListKeys` |
| **M3** Stale VOD seek bar on a live channel | `prepare()` no longer reads the old item's duration. The 500 ms loop accepts position and duration only while `currentMediaItem.mediaId` equals the current id; duration is 0 if live or ≤ 0. `MediaPlayerView` treats `duration <= 0` as live. | Device: play an addon movie, then a live channel → LIVE chip, no "00:11 / 30:00". |
| **M4** Phone browser never opened | Manifest `<queries>` gained VIEW + BROWSABLE for http and https. `AndroidUrlOpener` adds CATEGORY_BROWSABLE and calls `startActivity` directly (falling back on `ActivityNotFoundException`). Callers no longer pre-check `canHandleUrl`. | Device: Website → Open; Configure → the browser opens. |
| **M5** TV browser stub | `com.android.tv.frameworkpackagestubs` counts as "no browser" (as in F1's file picker): `openUrl` returns false when only the stub resolves, and the URL is shown as text. The same applies to Configure. | TV emulator: Website → Open, and Configure → the URL is shown as text. |
| **M6** Orphan Discover in the back stack | Tabs use `navigate { popUpTo(HOME_FEED) { saveState = true }; launchSingleTop = true; restoreState = true }`. When Discover disappears, the app navigates to Home with `popUpTo(HOME_FEED)`; that pop is what removes the Discover entries. `clearBackStack(DISCOVER)` only discards Discover's **saved tab state** (from `saveState`), so `restoreState` cannot bring it back later; it does not remove live back-stack entries. Since round 2 this runs only when "no active addon" is known (N8). | Phone: open Discover → Profile → Addons → remove the last addon → Back → Home, never Discover. |
| **M7** Duplicate episode and stream keys | `MediaRules.groupSeasons` drops blank and duplicate video ids. Picker keys are `"s|addon|index"` and `"g|addon"`. | `MediaRulesTest.duplicateVideoIdsAreDroppedBeforeTheyBecomeListKeys` |
| **M8** TV focus restore | Discover remembers the last focused card (`rememberSaveable`) and restores it after Back from a detail page. Pressing OK on the Discover rail item moves focus into the content, retrying while rows load and falling back to the search button. Back from Addons focuses the Settings rail item. | TV: card → detail → Back (same card); Settings → Addons → Back (Settings); Discover rail → OK (first card). |
| **M9** Series preview treated as a movie | The loading state uses `isSeries = type == "series"`, so there is no Play flash. On TV, a series without a last-watched episode focuses the selected season chip; if there are no seasons, Play. The focus effect re-runs when loading ends. | Phone: open a series from a row → no Play flash. TV: initial focus is on the season chip. |
| **M10** Re-prepare restarted at the first start point | `MediaProgressTracker.restartContext(id)` resumes from the last known position, and `PlayerViewModel.resumeMediaItem` uses it. | `MediaProgressTrackerTest.restartResumesFromTheLastPositionAndStopIsTiedToTheItem` |
| **M11** iOS Keychain | A new key is created only on `errSecItemNotFound`. `SecItemAdd` status is checked. Any other Keychain error, or a wrong-size item, throws (never an in-memory-only key). A failed install shows a message instead of crashing (#15). | Mac build + code review (not compiled here). |
| Dev. 3 rejected | TV: the **menu key** on a Continue watching card removes the title (`DiscoverViewModel.removeFromHistory`). | TV: Discover → Continue watching card → Menu. |
| Dev. 7 rejected | **Debug-only** blocklist override: build with `.\gradlew.bat :composeApp:assembleDebug -Ptsiptv.debugAddonBlocklistUrl=http://10.0.2.2:7000/policy/addon-blocklist.json`. The value lives in a `resValue` of the **debug** build type only (empty by default) and is read only when the app is debuggable (`debugAddonBlocklistUrlOverride()`; desktop and iOS always return null). Release builds cannot have it. | AC-S22 with the harness (`BLOCK_HOSTS=10.0.2.2 node qa/stremio-fixture/server.js`). The check still runs at most once a day, so clear app data or move the clock forward a day to re-trigger it. |
| m12 Lost key | New `AddonStatus.SECRET_LOST`: red status "The saved link can no longer be read. Add the addon again.", the switch is disabled, and the row menu has **Add addon** (adding the same id replaces the row). | `AddonRepositoryTest.undecryptableAddonIsSecretLostAndInactive` |
| m13 History keyed by the meta addon | History is keyed by title + video (`findVideo`). The first row's `sourceId` is kept, and removing a title deletes its rows from every addon. | `MediaHistoryRepositoryTest.historyIsKeyedByTitleNotByTheMetaAddon` |
| m14 Series dropped from Continue watching | If the next episode already has a row, finishing the current one bumps that row's `updatedAt`. | `MediaHistoryRepositoryTest.finishingAnEpisodeWhoseNextAlreadyHasARowKeepsTheSeriesInContinueWatching` |
| m15 Robustness | Catalogue and detail read installed addons from the database (`repository.current()`), so they work after process death. The decrypt cache is an immutable map in an `AtomicReference` (CAS updates). `install()` failures show a message. Desktop key file: temp file restricted to the owner **before** the bytes are written, then an atomic rename. An existing key file is never overwritten; a wrong size throws. | `FileKeySecretCipherTest.aWrongSizeKeyFileIsNeverOverwritten` |
| m16 Player UX | TV banner for addon items: "◀ ▶ seek 10 s · OK play/pause · BACK exit". The mini player shows the episode label, not the old programme or raw id (channels unchanged). The tracker samples the position continuously, so hand-over and Back save the latest one. `stop()` is tied to the item id. Rows without a known duration (live `tv`, never loaded) stay out of Continue watching; queued next episodes are marked `durationMs = -1`. | `MediaHistoryRepositoryTest` (live, never-loaded) and `MediaProgressTrackerTest` |
| m17 TV focus details | The TV stream picker is a focusable `Popup`: ◀ cannot leave it, and Back closes it. "No playable stream" appears only after the first answer. The catalogue's first-card focus happens once, so focus stays on a genre chip after a change. After Remove, focus returns to the list or the Add button. On phone the last-watched episode is scrolled into view too. | TV walkthrough. |
| m18 History empty card | Not shown when only Movies & series history exists. | Phone: clear channel history, keep one media item. |
| m19 Shared query | Separate `inlineSearch` (phone field) and `screenSearch` (search screen / `stremio:///search`). | Phone: type in Discover, open a search link → separate fields. |
| m20 Privacy | `MediaPlayerService` no longer logs artwork URLs; a load failure logs the host only. `UrlOpener.ios.kt` no longer prints URLs. | grep `Log.` in `MediaPlayerService.kt`. |
| Nits | "Off" status for disabled addons. An unroutable `stremio:///` stream is shown greyed as "Not playable in TS IPTV". `previewJson` is trimmed (no videos, links, cast or director; description ≤ 300 characters). Greyed-only groups keep their addon header (lead decision). `fallbackToDestructiveMigration` is unchanged. `schemas/4.json` and `5.json` are current (regenerated by the build) and left for the lead to commit. | — |

Test run after the fixes (`.\gradlew.bat :composeApp:desktopTest :composeApp:assembleDebug`): BUILD SUCCESSFUL, **439 tests in 61 classes, 0 failures, 0 errors, 0 skipped**. 127 of them are F2 (`core.stremio`, `core.security`). The total includes tests added in parallel by others.

## QC round 2 fixes

No emulator or device was used.

| # | Fix | How to verify |
|---|---|---|
| **N1** Unknown duration overwrote known progress | `MediaHistoryRepository.saveProgress` never writes a non-live save whose duration is ≤ 0: a failed or never-loaded stream, or one that gave up after 20 s. So it can neither create an unresumable row nor overwrite a known position or duration. Continue watching now filters resumable rows (duration > 0 or queued next) **before** picking the latest row per title. | `MediaHistoryRepositoryTest.aFailedResumeNeverOverwritesTheKnownProgress` (movie repro), `aDeadNextEpisodeDoesNotHideTheSeries` (S1E3 / dead S1E4 repro), `aNewerUnresumableRowNeverHidesAnOlderResumableOne`. Device: Continue watching → a dead stream → Back → the title stays with its position. |
| **N2** Resume seek on the old source | `AndroidMediaPlayer.pendingSource` is set in `prepare()` and cleared on `onTimelineChanged(PLAYLIST_CHANGED)`, `onMediaItemTransition`, a player error or a source failure. There is also a fallback: the player's `currentMediaItem` object differs from the one captured at `prepare()`. While it is set, the 500 ms loop takes no position or duration, so the tracker cannot see a duration and seek, even when the new item has the same id. | Device: replay the same title from Continue watching → resumes at the saved point, not at the old source's. |
| **N3** Catalogue Back focus | `CatalogScreen` remembers the last focused card or genre chip (`rememberSaveable` key + restore requester), as Discover does. The one-time initial focus is per visit (`remember`), so after Back the card is focused, and after a genre change focus stays on the chip. | TV: See all → card → detail → Back → the same card. |
| **N4** Stale start intent while zapping | Each `startService` intent carries a sequence number; `onStartCommand` ignores all but the latest. A refused item (preflight error) calls `invalidatePendingStarts()`, so an in-flight channel A cannot start under B's refusal overlay. | Device: zap quickly from a playable channel onto a refused one (e.g. a DRM channel on a device without DRM) → only the overlay, no audio or video of A. |
| **N5** Tracker outliving its item | The tracker compares `currentMedia` right after subscribing (no `drop(1)`), so a change before tracking started ends it at once. | `MediaProgressTrackerTest.trackingEndsWhenAnotherItemIsAlreadyCurrent` |
| **N6** Full URLs printed | `MediaPlayerService.loadBitmapFromUrl` logs only the exception type (it used to print `FileNotFoundException(<url>)`). Same for `AdsViewModel` and `IAdsRepositoryImpl` (their network and parse errors carry URLs). No `printStackTrace` is left in `androidMain`/`commonMain`. | grep `printStackTrace` in `composeApp/src/androidMain` and `commonMain` → 0. |
| **N7** A transient key failure became permanent SECRET_LOST | `SecretCipher.decryptResult()` returns `Ok`, `Invalid` (malformed, authentication/MAC failure, permanently invalidated key: lost) or `KeyUnavailable` (key store not usable now: transient). The repository caches only `Ok` and `Invalid`. `KeyUnavailable` is retried on the next read, shown as "not responding", and a refresh records `key_unavailable`. Android, desktop and iOS implement it. | `AddonRepositoryTest.anUnavailableKeyIsTransientAndRetried`, `undecryptableAddonIsSecretLostAndInactive` |
| **N8** Restored Discover popped before Room emitted | `AddonRepository.hasActiveAddons` is `null` until the database has emitted once. The phone keeps the Discover tab while it is unknown and pops Discover only on a known `false`. TV keeps a restored Discover selection while unknown. | Phone: open Discover, kill the process (`adb shell am kill tss.t.tsiptv` while in the background) → reopen → Discover is restored. |
| **N9** Nits | TV banner hint: channels keep the zapping hint, addon VOD shows the seek hint, and addon live items show none (there is no zapping or seeking for them). The `clearBackStack(DISCOVER)` wording in round 1 is corrected. | — |

Test run after the fixes (`.\gradlew.bat :composeApp:desktopTest :composeApp:assembleDebug`): BUILD SUCCESSFUL, **444 tests in 61 classes, 0 failures, 0 errors, 0 skipped**. 132 of them are F2.

## QC round 3 fixes

No emulator or device was used.

| # | Fix | How to verify |
|---|---|---|
| **N-S1** TV search trapped focus | `DiscoverSearchScreen`: ▼ in the field moves focus to the first result (a `FocusRequester` on the first card; if there are no results yet, `moveFocus(Down)`). The IME Search action (`KeyboardActions(onSearch)`) does the same. ▲ on any card of the first result row returns to the field. | TV: Discover → search button → type "his" → Search/▼ → first card; ▲ → field. |
| **R1** Slow stream lost the resume point | No more 20 s timeout: the tracker waits while the item is current and settles only after the resume seek was applied or the item stopped being current. Nothing is saved before that; an abandoned attempt is not written (N1). | `MediaProgressTrackerTest.resumesSavesPeriodicallyAndOnPause` (no seek or save before the duration is known). Device: a stream that takes > 20 s to start still resumes. |
| **R2** Unavailable key never retried / known false | Enabled addons whose key is temporarily unavailable are retried with bounded backoff (1 s, 2 s, 4 s … capped at 30 s, at most 8 attempts, reset once they decrypt). While only such addons exist, `hasActiveAddons` stays unknown (`null`), never `false`. A disabled addon shows OFF whatever its key state. | `AddonRepositoryTest.anUnavailableKeyIsTransientAndRetried` (unknown state, OFF ordering, recovery). |
| **R3** stop() and pending starts | `stop()` invalidates start intents in flight and clears the pending-source flag. `startService` is wrapped so an exception never leaves the flag set. | Code review; device: play → stop quickly after zapping → nothing restarts. |
| **R4** TV Discover selection | `discoverSelected` is reset when "no active addon" becomes known. | TV: select Discover, remove every addon → the channels show; add one again → the channels stay selected. |
| **R5** Phone Discover tab flicker | While unknown, the Discover tab shows only if Discover is already in the tab back stack (a restored screen). Users without addons see no flicker. | Phone without addons: cold start → no Discover tab at any point. |
| **R6** TV focus after the player | The detail page re-reads the last watched video on every (re)entry (`refreshLastWatched`, which also selects its season). The focus effect is keyed on it, so Back from the player focuses the episode just watched. Choosing a season chip does not pull focus into the episodes. | TV: series → episode → play → Back → that episode is focused. |
| **R7** ◀ from the channel grid | The rail list redirects focus-enter to the selected item (Discover, All channels or the selected group) when it is on screen (`focusProperties.enter` + `focusGroup`); otherwise the default search applies. | TV: select a group → ▶ into the grid → ◀ → the selected group, not Discover. |

Test run after the fixes (`.\gradlew.bat :composeApp:desktopTest :composeApp:assembleDebug`): BUILD SUCCESSFUL, **444 tests in 61 classes, 0 failures, 0 errors, 0 skipped** (132 F2).

## QC round 4 fixes

No emulator or device was used.

| # | Fix | How to verify |
|---|---|---|
| **L1** Search focus fallback never fired | `DiscoverSearchScreen` uses the Boolean overload: `firstResult.requestFocus(FocusDirection.Enter) \|\| focusManager.moveFocus(FocusDirection.Down)`. ▼ is consumed only when focus actually moved. ▲ from the first row likewise returns `field.requestFocus(FocusDirection.Enter)`. | TV: type fast and press Search before results appear (focus stays in the field, nothing breaks); scroll the first row so its first card is off screen, then ▲/▼ from the field. |
| **L2** Rail enter redirect | Applies only to `FocusDirection.Left` (and only when the selected item is on screen); every other direction uses `FocusRequester.Default`. | TV: ◀ from the grid → the selected rail item; ▲/▼ into the rail behave as before. |
| **L3** Key retry chain | The retry job clears its own reference **before** bumping the tick (the re-mapping runs synchronously and must see no active job). A comment documents that after 8 failed attempts `hasActiveAddons` stays unknown for the process until the addons change. | Code review; `AddonRepositoryTest.anUnavailableKeyIsTransientAndRetried`. |
| Nit R6 | `MediaDetailViewModel.refreshLastWatched` prefers the tracker's current item when it belongs to this title, so Back from the player focuses the just-watched episode even before the first 10 s save. | TV: play an episode, Back within 5 s → that episode is focused. |
| Nit | Unused `resumeWaitMs` / `RESUME_WAIT_MS` / `withTimeoutOrNull` removed from `MediaProgressTracker`. | — |

Test run after the fixes (`.\gradlew.bat :composeApp:desktopTest :composeApp:assembleDebug`): BUILD SUCCESSFUL, **444 tests in 61 classes, 0 failures, 0 errors, 0 skipped** (132 F2).

## Files changed outside `core/stremio` (for review)
- **Build**: `composeApp/build.gradle.kts` (`generateAppBuildInfo`).
- **Database**:
  - `core/database/AppDatabase.kt`, `IPTVDatabase.kt`, `RoomIPTVDatabase.kt`, `InMemoryIPTVDatabase.kt`
  - new `RoomStremioStores.kt`, `entity/StremioEntities.kt`, `dao/StremioDaos.kt`
  - `composeApp/schemas/.../5.json`
- **Security**: `core/security/SecretCipher.kt` plus `androidMain`/`desktopMain`/`iosMain` actuals.
- **Network, DI, analytics**: `core/network/KtorNetworkClient.kt` (`httpClient`), `di/AppModule.kt`, `core/firebase/analystics/AnalyticsConstants.kt`.
- **Player**:
  - `ui/screens/player/PlayerViewModel.kt`, `PlayerScreen.kt`
  - `player/ui/MediaPlayerView.kt`
  - `androidMain/.../player/AndroidMediaPlayer.kt`
  - `ui/tv/TvPlayerScreen.kt`
- **App shell and navigation**:
  - `App.kt`, `navigation/NavRoutes.kt`
  - `ui/tv/TvHomeScreen.kt`, `ui/tv/TvSettingsDialog.kt`
  - `ui/screens/home/HomeBottomNavigationScreen.kt`, `HomeBottomNavigationNavHost.kt`
  - `ui/screens/profile/ProfileScreen.kt`, `ui/screens/history/HistoryScreen.kt`
- **New UI**: `ui/screens/addons/`, `ui/screens/discover/`, `ui/screens/mediadetail/`, `ui/screens/history/MediaHistorySection.kt`, drawable `ic_addons.xml`.
- **Strings**: the seven `strings.xml` files, and `StringResourcesLocaleTest.kt`.
- **Tests**: `AppDatabaseMigrationTest.kt`, and in desktopTest the new `FileKeySecretCipherTest.kt` and `StremioKoinGraphTest.kt`.

## Final test totals

The final run was `.\gradlew.bat :composeApp:desktopTest :composeApp:assembleDebug` (2026-09-28 03:36 local). **BUILD SUCCESSFUL.**

From `composeApp/build/test-results/desktopTest/*.xml`: **384 tests in 55 classes, 0 failures, 0 errors, 0 skipped.**

- F2 accounts for 120 of them (`core.stremio.*` and `core.security.*`), plus `AppDatabaseMigrationTest` (2, including 4→5) and `StringResourcesLocaleTest` (5, including the F2 keys).
- The suite total also contains tests other agents were adding in parallel: an earlier run the same night had 333 tests in 50 classes. `composeApp/build.gradle.kts` was also changed by someone else between runs.
- Nothing failed outside F2.
