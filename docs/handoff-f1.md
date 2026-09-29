# Hand-off F1 — Kodi-compatible M3U and per-channel playback

Status: **Dev complete, 2026-09-28; ready for QC (G1–G4).** Branch `feature/sources-and-formats`, not committed.
Spec: [`prd-kodi-m3u-compat.md`](prd-kodi-m3u-compat.md). Plan rules: [`roadmap-sources-and-formats.md`](roadmap-sources-and-formats.md) §2.

## Results

| Check | Result |
|---|---|
| `.\gradlew.bat :composeApp:desktopTest --console=plain` | After QC round 1: **269 tests, 0 failures, 0 errors, 0 skipped** (41 result files; this total also contains F2's tests from the parallel branch work). Round 0: 169 tests; baseline before F1: 86 |
| `python web/scripts/check_guides.py` | **OK** (QC fixtures kept out of `web/public/`) |
| `.\gradlew.bat :composeApp:assembleDebug --console=plain` | **BUILD SUCCESSFUL** (`composeApp-debug.apk`) |
| iOS | **Not compiled** (Windows machine, no Kotlin/Native Apple toolchain). iOS changes kept minimal; compile on a Mac before G3. |
| Room | v4, exported `composeApp/schemas/tss.t.tsiptv.core.database.AppDatabase/4.json`, 3 → 4 migration test green |
| Locale key sets | `StringResourcesLocaleTest` green: all 7 files have identical keys |
| Denylist | No third-party source URL in production source sets (the old hard-coded Widevine URL and a CDN host named in a comment are gone). Test assets that predate F1 (`assests/epg.xml`, `XMLTVEPGParserTest`) still contain a third-party logo host; test-only, untouched. |

## What was built

**Parsing** (`core/parser/`)
- `iptv/m3u/M3UParser.kt` rewritten: `x-tvg-url` (all comma-separated URLs) then `url-tvg`; header `tvg-shift` and catch-up defaults; keys `[A-Za-z0-9_-]+`, lower-cased, aliases `ch-number`/`catchup-type`/`tvg-rec`; name after the first unquoted comma, `tvg-name` only as fallback, then "Unknown channel"; `group-title` split on `;`; `#EXTGRP` inside a stanza and sticky before one (reset by empty `#EXTGRP:`); `tvg-chno`, `radio`, `media`, `#EXT-X-PLAYLIST-TYPE:VOD`, `tvg-shift`; `#KODIPROP` / `#EXTVLCOPT` (also `--`) stored as `kodiprop:*` / `vlcopt:*` attributes and interpreted; `|` suffix; skips with reasons (`plugin://` → `KODI_ADDON`, `@`/`#WEBPROP` → `WEB_SCRAPING`, no URL → `NO_URL`) and line numbers; id de-duplication `id`, `id~2`, …
- `iptv/m3u/M3UStanza.kt`: stanza model and channel builder shared by M3U, plain URL lists and `.strm`; header priority `|` > `#EXTVLCOPT` > `inputstream.adaptive.*_headers` > non-standard attributes.
- `iptv/m3u/HeaderSuffixParser.kt`: `|name=value&…` (percent-decoding, malformed escape keeps raw text, `!` dropped, `cookies` → `Cookie`, `seekable` ignored).
- `iptv/m3u/KodiDrmParser.kt`: `drm` JSON → `drm_legacy` → `license_type`+`license_key`, exactly per PRD §4 (key systems, 4-field `license_key`, lenient ClearKey `kid:key`/JWKS/`data:` URI, UUID dashes, base64 keys).
- `model/playback/{DrmSpec, CatchupSpec, StreamMimeTypes}.kt`; `DrmSpec.clearKeyJwks()` builds the W3C JWKS.
- `strm/StrmParser.kt`; `PlainUrlListParser` (in `M3UParser.kt`) for `M3U_PLAIN`.
- `IPTVParserFactory.detectFormat`: the PRD §1 table (HTML, TS IPTV Source, HLS manifest, M3U, XSPF, XML, iptv-org, JSON arrays, STRM, plain list, unknown); `decode(bytes)` gunzips and strips the BOM.
- JSON `attributes["user-agent"/"referrer"]` and iptv-org `user_agent`/`referrer` → headers. `IptvOrgRawDTO` is now `@Serializable` (it was not, so iptv-org import could never work) and channels are named by `title`.
- Lead request: `XMLTVEPGParser` ignores unknown XMLTV elements/attributes (`ignoreUnknownChildren`), `display-name`/`title`/`desc` are lists (first, or the one matching an optional `preferredLanguage`); XSPF is detected without an `<?xml` line when the document starts at `<playlist` with the XSPF namespace.

**Persistence** (`core/database/`)
- Room **v4**: the 12 columns of the PRD table, all nullable or with defaults; `AutoMigration(3, 4, spec = Migration3To4::class)`; `Migration3To4.onPostMigrate` sets `lastUpdated = 0` for `URL` playlists. JSON columns mapped in `entity/Mappers.kt` (lenient decode: a damaged value loses that field, not the channel).
- List queries `ORDER BY sortIndex, rowid`; `getChannelsByCategory` also matches secondary groups.
- `IPTVDatabase.replacePlaylistContent(...)` — one write transaction (`useWriterConnection { immediateTransaction { … } }`): upsert playlist, replace categories, channels and `channel_attributes`, carrying favourites and last-watched over. `PlaylistDao.upsertPlaylist`; `insertPlaylist` now upserts too (REPLACE deleted the row and cascaded into history).
- `InMemoryIPTVDatabase` kept in sync (same ordering, multi-group filter, transaction method, attributes). It also no longer hangs in `getCategoriesByPlaylist`/`countValidPrograms` (they collected a StateFlow).

**One import pipeline** — `usecase/playlist/PlaylistImporter.kt`: `importFromUrl`, `importFromFile` (20 MiB limit, `sourceType = FILE`, `url = file:<name>`, id `file:<hash(name)>`), `importSingleStream`, `refresh` (FILE → `FILE_REFRESH` without a network call), `refreshIfStale` (daily, skips FILE, failures keep the stored copy), `fetchEpg` (≤ 5 URLs, sequential, each failing alone, merged and de-duplicated by channel + start). Typed `PlaylistImportException(ImportError)`. `HomeViewModel.parseIptvSource`, `refreshIPTVChannel` and the daily refresh all go through it.

**Privacy** — the add-playlist event sends only `iptv_format` and `channel_count`; the play event no longer sends the stream URL; `PARAMS_IPTV_URL` removed. Ktor `Logging` set to `NONE` on Android (was `ALL`: URLs, headers and whole playlists in logcat), desktop and iOS (was `INFO`: every URL). `play-store/data-safety.md` re-checked and annotated; answers unchanged.

**Playback**
- `player/models/MediaItem` gains `headers`, `drm`, `isRadio`; `Channel.toMediaItem()` copies them. `MediaPlayer.playbackError` + `PlaybackError` + `PlaybackPreflight` (common, tested).
- Android: `MediaPlayerService` receives the whole serialized item; per-item `MediaSource` from `playerHttpDataSourceFactory(headers)` (one shared OkHttp client, so `EdgeFailover` state and the pool survive; channel UA replaces the Dalvik one); DRM through `DrmConfiguration` + `DefaultDrmSessionManagerProvider.setDrmHttpDataSourceFactory(channel headers)`, ClearKey keys through `LocalMediaDrmCallback`; the hard-coded Widevine manager and `isDrmProtected` are removed. `AndroidMediaPlayer` refuses unsupported specs before starting the service and maps `ERROR_CODE_DRM_*` and 401/403 on channels with headers.
- Desktop: `:http-user-agent=` / `:http-referrer=` VLC options, other header names logged (never values); any DRM → `drm_not_supported_device`.
- iOS: `AVURLAsset` with `AVURLAssetHTTPHeaderFieldsKey`; any DRM → `drm_not_supported_device`, current item cleared. **Not compiled.**
- `PlayerViewModel.playIptv` loads the full channel by id before playing, so History / continue watching / mini player (partial `ChannelWithHistory`) use stored headers and DRM. `verifyPlayingMediaItem` fixed: it used to call `playMedia`, which called it back and never started anything (resume from the mini player did nothing).

**File import** — `platform/PlaylistFilePicker.kt` (expect/actual): Android SAF `OpenDocument("*/*")`, availability = something resolves `ACTION_OPEN_DOCUMENT` (manifest `<queries>` added for API 30+), size checked from `OpenableColumns.SIZE` and while reading; desktop native `FileDialog` filtered to `m3u, m3u8, strm, json, xspf, xml`; iOS `UIDocumentPickerViewController` (`public.data`, `public.plain-text`).

**UI**
- Import screen (phone and TV use the same screen): **Import from file** after **Add**, D-pad chain back → name → link → add → import from file; hidden with `import_file_unavailable_tv` when no picker exists; HLS single-stream dialog; replace-existing dialog; typed error messages.
- Import result dialog (`ImportDialogs.kt`): success message + `import_summary_skipped`; **OK** has initial focus, **Details** to its right (▶), Back dismisses; details dialog lists one row per reason.
- Channel rows: number before the name, radio badge; TV cards: number badge top-left, radio icon; TV zapping banner shows the number. Categories list every group and a channel shows under each of them.
- Player: radio logo layout (under the controls); error overlay with message and **Back** on phone/desktop/iOS; on TV the overlay is text only, so ▲/▼ keep zapping and Back leaves.
- `GrayButton` now shows the TV focus border (dialog secondary buttons had no visible focus).
- 25 strings in all seven locales (the 23 PRD keys + `import_replace_action`, `player_back`).

## Files

New: `core/parser/iptv/m3u/{HeaderSuffixParser, KodiDrmParser, M3UStanza}.kt`, `core/parser/model/playback/{DrmSpec, CatchupSpec, StreamMimeTypes}.kt`, `core/parser/strm/StrmParser.kt`, `core/database/Migration3To4.kt`, `usecase/playlist/PlaylistImporter.kt`, `platform/PlaylistFilePicker.kt` (+ `.android.kt`, `.desktop.kt`, `.ios.kt`), `player/models/PlaybackError.kt`, `player/ui/PlayerOverlays.kt`, `ui/screens/addiptv/ImportDialogs.kt`, `ui/widgets/channels/ChannelBadges.kt`, `composeApp/schemas/…/4.json`.

Changed (main): `M3UParser.kt`, `IPTVParserFactory.kt`, `IPTVChannel.kt`, `IPTVPlaylist.kt`, `IPTVFormat.kt`, `JSONModels.kt`, `IptvOrgParser.kt`, `IptvOrgRawDTO.kt`, `IPTVParserException.kt` (open), `epg/XMLTVEPGParser.kt`, `epg/model/{XMLTVChannel, XMLTVProgramme, XMLTVCredits}.kt`, `AppDatabase.kt`, `RoomEntities.kt`, `Mappers.kt`, `ChannelDao.kt`, `ChannelAttributeDao.kt`, `PlaylistDao.kt`, `IPTVDatabase.kt`, `RoomIPTVDatabase.kt` (no network client any more), `InMemoryIPTVDatabase.kt`, the three `*DatabaseFactory.kt`, `core/model/{Channel, Playlist}.kt`, `MediaItem.kt`, `MediaPlayer.kt`, `AnalyticsConstants.kt`, `HomeViewModel.kt`, `PlayerViewModel.kt`, `App.kt`, `ImportIPTVScreen.kt`, `HomeBottomNavigationScreen.kt`, `HomeChannelItem.kt`, `MediaPlayerView.kt`, `TvHomeScreen.kt`, `TvPlayerScreen.kt`, `TSButtonDefaults.kt`, `usecase/di/DI.kt`; Android `MediaPlayerService.kt`, `AndroidMediaPlayer.kt`, `PlayerHttpDataSource.kt`, `EdgeFailover.kt` (comment), `OkHttpKtorNetworkClient.kt`, `AndroidManifest.xml`; desktop `DesktopMediaPlayer.kt`, `DesktopKtorNetworkClient.kt`; iOS `IOSMediaPlayer.kt`, `NetworkClientProvider.kt`; 7 × `strings.xml`; `composeApp/build.gradle.kts` + `gradle/libs.versions.toml` (`room-testing` for `desktopTest`); `play-store/data-safety.md`.

Tests: `commonTest/.../core/parser/{KodiM3UParserTest, KodiDrmParserTest, HeaderSuffixParserTest, StrmParserTest, IPTVParserFactoryTest}.kt`, `core/database/ChannelEntityMapperTest.kt`, `usecase/playlist/PlaylistImporterTest.kt`, `player/PlaybackPreflightTest.kt`, `core/language/StringResourcesLocaleTest.kt`; `desktopTest/.../core/database/{AppDatabaseMigrationTest, RoomImportPipelineTest}.kt`, `desktopTest/.../{WebExamplesParseTest, QaKodiFixturesTest}.kt`; `XMLTVEPGParserTest.kt` adapted to list fields. Fixtures `commonTest/kotlin/assests/kodi/` (`reference.m3u` = README example with example.com hosts, `headers.m3u`, `drm.m3u`, `skips.m3u`, `groups.m3u`, `channel.strm`, `addon.strm`, `hls-master.m3u8`, `plain-list.txt`, `page.html`, `array.json`, `iptv-org-streams.json`).

QC fixtures (never deployed; only Mux / example.com URLs): `qa/kodi/qc-playback.m3u`, `qc-channel.strm`, `qc-addon.strm`, `qc-array.json`, checked by `desktopTest/.../QaKodiFixturesTest.kt`. They live outside `web/public/` on purpose: they contain a `plugin://` entry and placeholder hosts that the site policy check (`web/scripts/check_guides.py`) rejects. `WebExamplesParseTest` parses the published F4 examples (`playlist.m3u`, `.xspf`, `.json`, `iptv-org-streams.json`, `channel.strm`, `guide.xml`). The other `web/` changes in the working tree belong to F4, not F1.

## Deviations from the PRD (with reasons)

1. **Daily refresh moved out of `RoomIPTVDatabase.getPlaylistById`.** It is now `PlaylistImporter.refreshIfStale`, called where a playlist is opened (Home start-up and switching playlist). A database that calls the importer that writes to the database is a cycle; the trigger point is the same, and `getPlaylistById` is a plain read everywhere else.
2. **`IPTVFormat.HTML` added** (not in the PRD's list) so detection stays a pure function; the importer maps it to `import_error_html_page`.
3. **Two extra strings**: `import_replace_action` ("Replace") and `player_back` ("Back") — the specified dialogs need them.
4. **ClearKey on HLS → `drm_not_supported_config`.** §4 says config, the error table says device; followed §4 (the device is fine, the playlist asks for a combination Media3 does not offer).
5. **Details is a dialog, not a sheet**, on phone as on TV (one D-pad-safe component). After a **manual refresh** the result dialog appears on Home only when something was skipped (a refresh showed nothing before).
6. **`playlist_file_refresh_hint` is a dialog on phone too**: phone Home has no snackbar host; the same dialog serves TV.
7. **An HLS manifest in a picked file** gives `import_error_unknown_format`: there is no URL to "add as channel".
8. **Guide matching for `id~2` channels** uses `Channel.guideId` (the id without the `~N` suffix) instead of a new `epgId` column, keeping the v4 schema exactly as specified.
9. **`tvg-shift` is applied on Home / player** (now-playing programme and the programme list in `HomeViewModel.loadProgramForChannel`). The Programs tab (`ProgramViewModel`, `GetChannelsWithValidProgramCounts`) still shows unshifted times — see gaps.
10. **Header decoding keeps `+`** (not turned into a space): tokens are often base64.
11. **`drm` JSON is strict about `license`**: a key system whose `license` has any member other than `server_url`, `req_headers`, `keyids` is skipped (PRD lists the forbidden ones; unknown ones are treated the same way to avoid half-applied setups).
12. **Also fixed on the way** (needed for ACs): play analytics URL, Ktor URL/body logging, resume-from-history never starting, favourites lost on every refresh, playlist REPLACE cascading into history, duplicate channel collectors, iptv-org DTO not serializable.
13. The EPG request still sends the `Content-Encoding: gzip` request header it sent before (unchanged behaviour; odd but harmless so far).

## Known gaps

- **iOS not compiled**: `IOSMediaPlayer.kt` (headers via asset options, DRM refusal) and `PlaylistFilePicker.ios.kt` need a Mac build before G3. `AVURLAssetHTTPHeaderFieldsKey` is undocumented (R6).
- **401/403 → `stream_error_forbidden_headers`** is mapped on Android only; iOS and desktop keep their generic failure.
- **Programs tab ignores `tvg-shift`** (see deviation 9); XMLTV `preferredLanguage` is supported by the parser but not yet fed from the app locale (defaults to the first title).
- Catch-up is stored, not played (F1b by design). R1 (global channel/category ids) unchanged.
- `web/public/index.html` formats line not updated: PRD step 11 says only after QC passes.
- Pre-existing, not F1: the desktop database lives in `java.io.tmpdir`; `DesktopMediaPlayer.setVolume/setMuted` are `TODO()`.

## How QC reproduces each AC

Serve the fixtures locally: `cd qa/kodi && python -m http.server 8000`, then use `http://10.0.2.2:8000/qc-playback.m3u` (and the other file names) from the Android emulator, `http://localhost:8000/...` on desktop; for file import, copy the files to the device (`adb push qa/kodi/qc-channel.strm /sdcard/Download/`). Proxy: `mitmproxy -p 8080`, emulator `adb shell settings put global http_proxy 10.0.2.2:8080` (install the mitm CA; the player trusts any certificate).

| AC | How |
|---|---|
| K1–K10 | Unit tests `KodiM3UParserTest` (fixtures `reference.m3u`, `headers.m3u`, `groups.m3u`, `skips.m3u`). In the app: import `qc-playback.m3u` → 9 channels, groups QC/Headers/Radio/DRM, numbers 1–9. |
| K9 (summary) | Import `qc-playback.m3u` → success dialog says 3 entries could not be imported; **Details**: 1 add-on, 1 web scraping, 1 no link. |
| K11–K15 | `KodiDrmParserTest` (`drm.m3u`). |
| K16 | Play "QC with User-Agent" (Mux) through mitmproxy: master, variant and `.ts` requests carry `User-Agent: TSIPTV-QC/1.0` and the Referer. Then ▼ / pick "QC without headers": default Dalvik UA, no Referer. |
| K17 | Play "QC clear stream, drm in URL": plays, no licence request at all (no `license.widevine.com`). Also `PlaybackPreflightTest`. |
| K18 | Play "QC Widevine licence request" on a device with Widevine: the proxy shows a POST to `https://license.example.com/widevine` with `User-Agent: TSIPTV-QC-LIC/1.0` and `X-QC: 1` (the licence then fails → `drm_license_failed`, expected with a placeholder server). "QC unsupported DRM setup" → `drm_not_supported_config` with no request; "QC ClearKey on HLS" → `drm_not_supported_config`. |
| K19 | Desktop / iOS: play "QC Widevine licence request" → `drm_not_supported_device`, no request in the proxy; ▼ / next channel plays. |
| K20 | Desktop, VLC verbosity 2 (`-vv` / VLC log): "QC with EXTVLCOPT" sends `TSIPTV-QC-VLC/1.0` and Referer `https://example.com/vlc`; console shows only the names of dropped headers. |
| K21 | "QC radio layout": radio badge in the list/card, logo in the player; lock the phone, audio continues (foreground service). |
| K22 | Play "QC with User-Agent", go Home, start it from History / continue watching / mini player: same UA in the proxy. |
| K23 | Import `qc-array.json` → JSON, one channel. Also `PlaylistImporterTest.jsonArrayImports`. |
| K24 | Link `https://example.com/` → `import_error_html_page`. Gzip: `gzip -k qc-playback.m3u` and import the `.gz` link → imports. BOM: save the M3U as "UTF-8 with BOM" and import → imports. Also `IPTVParserFactoryTest`. |
| K25 | Link `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` → "This is a single stream" → **Add as channel** → one-channel playlist named as typed, plays. |
| K26 | Import from file `qc-channel.strm` → one channel "qc-channel", plays with the QC UA. `qc-addon.strm` → `strm_error_kodi_addon`, nothing imported. |
| K27 | Phone: Import from file opens DocumentsUI; desktop: native dialog filtered by extension; iOS: document picker. Android TV emulator without DocumentsUI (`adb shell pm disable-user com.android.documentsui` if the image has it): button hidden, hint shown. 25 MB file: `fsutil file createnew big.m3u 26214400` → `import_error_file_too_large`. |
| K28 | Refresh a playlist imported from a file (Settings → refresh channels) → `playlist_file_refresh_hint`, proxy shows no request. |
| K29 | Install the current release, import `qc-playback.m3u` by link, favourite a channel, play two; install the F1 debug build over it: playlists, favourite and history are there; opening Home re-parses (proxy shows the playlist download) and the channels now carry headers. CI: `AppDatabaseMigrationTest`. |
| K30 | `RoomImportPipelineTest` (real Room) and `PlaylistImporterTest.importRefreshAndAutoRefreshStoreTheSameRows`. On device: compare `channel` / `channel_attributes` in App Inspection after import, manual refresh, and a refresh after setting the device date +2 days. |
| K31 | `adb shell setprop debug.firebase.analytics.app tss.t.tsiptv`; DebugView `add_iptv_playlist` shows only `iptv_format`, `channel_count`; `play_iptv_channel` has no URL. |
| K32 | `StringResourcesLocaleTest`. TV: D-pad only — back → name → link → Add → Import from file; result dialog: OK focused, ▶ Details, Back closes. |

## QC round 1 fixes (2026-09-28)

All 19 items from the round-1 report. Results after the fixes: desktopTest **269 / 0 failures** (includes F2's tests), `assembleDebug` **BUILD SUCCESSFUL**, `check_guides.py` **OK**. iOS still not compiled.

| # | Fix | Where / test |
|---|---|---|
| 1 | All `\'` / `\"` removed from the 7 `strings.xml` (Compose resources show them literally): plain `'` / `"`, typographic `’` in French. New test fails on any `\'` or `\"`. | `StringResourcesLocaleTest.noBackslashEscapedQuotes` |
| 2 | Added `media3-exoplayer-smoothstreaming`, so `mimetype=application/vnd.ms-sstr+xml` / `manifest_type=ism` play. `createMediaSource` is wrapped: a failure stops and clears the player and reports `STREAM_FAILED` through `MediaPlayerService.sourceFailures`, never crashing `onStartCommand`. | `MediaPlayerService`, `AndroidMediaPlayer` |
| 3 | Ids keep the pre-F1 rule `tvg-id ?: slug(tvg-name ?: name)`. For names with commas the pre-F1 id (cut at the last comma) is kept as `IPTVChannel.legacyId`. `replacePlaylistContent` matches each new channel to the stored row it continues (same id and URL → same URL → same id → legacy id, each row used once), carries favourite / last-watched over, and moves `channel_history.channelId` (two-step `UPDATE OR IGNORE`, so rename chains cannot merge). This covers duplicate `tvg-id`s, where the old REPLACE had left the *last* occurrence under the plain id. | `ChannelCarryOver.kt`, `ChannelHistoryDao.moveHistory`; `QcRound1ImporterTest`, `RoomImportPipelineTest.renamedChannelsKeepFavouriteAndHistory` |
| 4 | A licence `User-Agent` is removed from the licence request headers and set on the DRM data-source factory instead, so it is sent once. | `MediaPlayerService.createMediaSource` / `drmConfiguration` |
| 5, 6 | Desktop and iOS: `play()` / `pause()` do nothing while a playback error is set, so the previous channel does not restart. VLC `stopped` no longer overwrites ERROR with IDLE. Android does the same (and ignores STATE_IDLE while in error, and unloads the previous item on refusal). | `DesktopMediaPlayer`, `IOSMediaPlayer`, `AndroidMediaPlayer` |
| 7 | Every `PlaylistImporter` entry point runs on `Dispatchers.Default`. | `PlaylistImporter` |
| 8 | `getManualGzipIfNeed` throws `HttpStatusException` on non-2xx (the URL is not in the message). A refresh that parses to 0 channels never replaces a non-empty playlist. | `KtorNetworkClient`; `QcRound1ImporterTest.anEmptyOrFailedRefreshKeepsTheChannels` |
| 9 | `license_key` field 1 must be empty or an http(s) URL. `kid : key` and `a:b, c:d` are accepted. Plain (non-base64) `data:` URIs are read. `clearkey` with an empty `license_key` is unsupported. | `KodiDrmParser`; `QcRound1ParserTest` |
| 10 | A non-primitive `priority` sorts last instead of throwing. `KodiDrmParser.parse` also turns any exception into an unsupported spec, so one channel cannot abort an import. | `QcRound1ParserTest.nonPrimitivePriorityDoesNotAbort` |
| 11 | The BOM is stripped once in `PlaylistImporter.parse` for every format. | `QcRound1ImporterTest.byteOrderMarkBeforeJsonIsAccepted` |
| 12 | Content that starts with `#` and has a line starting `#EXTM3U` / `#EXTINF` is M3U. The parser accepts directives and comments before the first entry. | `QcRound1ParserTest.headerlessKodiListsAreM3u` |
| 13 | HLS tags count only at the start of a line. | `QcRound1ParserTest.hlsTagsCountOnlyAtLineStart` |
| 14 | OK on the import error dialog also sends `OnDismissErrorDialog`. | `ImportIPTVScreen` |
| 15 | A channel refused by preflight gets no history row. Before a switch, the tracker closes the previous channel while the player still holds its position, and it never writes a position that belongs to another item. Players reset position / duration on each `prepare`. | `PlayerViewModel.playIptv`, `ChannelHistoryTracker` |
| 16 | Headers with a non-token name, CR/LF, control characters or non-ASCII values are dropped: in the parser, in JSON / iptv-org mapping and again before OkHttp, which also covers old rows. Invalid UTF-8 escapes keep the raw text. | `HeaderSuffixParser.isValidHeader/sanitize`, `PlayerHttpDataSource`; `QcRound1ParserTest.unsendableHeadersAreDropped` |
| 17 | New `PlaybackError.STREAM_FAILED` / `stream_error_generic` (7 locales) for every other failure (DNS, 404, unbuildable source) on Android, desktop and iOS. | `PlayerOverlays`, the three players |
| 18 | Picker read failures give `import_error_file_read` (new `PickedPlaylistFile.ReadFailed`). iOS checks the size before reading and reads off the main thread. Counts use `<plurals>` (`import_summary_skipped`, `import_skipped_*`; one/other where the language has them). The play event no longer sends the channel name, only the hour. `data-safety.md` is corrected: only the Analytics user ID is gated by `UserTrackingService`, events are not. | `ImportDialogs`, pickers, `PlayerViewModel`, `play-store/data-safety.md` |
| 19 | Removed the `println`s in `KtorNetworkClient` (gzip) and `AuthRepositoryImpl` (user and token). `getChannelsByCategory` escapes LIKE wildcards (`ESCAPE '\'`, JSON escaping included). An unknown channel `catchup` (`disabled`, `none`) switches catch-up off instead of using the header default. XHTML is detected as a web page. A truncated `.gz` file gives `import_error_file_read`. Groups are de-duplicated case-insensitively and filtered ignoring case. The replace dialog title is now `import_replace_title` ("Replace playlist?"), and the button stays "Replace". | `QcRound1ParserTest`, `RoomImportPipelineTest.categoryFilterTreatsLikeWildcardsLiterally`, `QcRound1ImporterTest.truncatedGzipFileIsAReadError` |

New strings (7 locales): `stream_error_generic`, `import_error_file_read`, `import_replace_title`. Four count strings became plurals. `StringResourcesLocaleTest` now compares `<string>` and `<plurals>` keys and checks every plural has an `other` form.

Changes to earlier sections:
- Deviation 8 still holds (guide matching via `guideId`).
- The id rule now follows pre-F1 behaviour for `tvg-name` rather than the PRD's "slug of the name". The display name still follows the PRD, so ids stay stable for existing users.
- SmoothStreaming is no longer a known gap.
- A refresh that returns an empty list reports `import_error_unknown_format` and keeps the stored channels (no dedicated string).
- Channels that fail at runtime (not refused by preflight) still get a history row for the attempt; its position and duration are no longer the previous channel's.

## QC round 2 fixes (2026-09-28)

Results: desktopTest **287 tests / 0 failures / 0 errors / 0 skipped** (42 result files, F2's tests included), `assembleDebug` **BUILD SUCCESSFUL**, `check_guides.py` **OK**. iOS still not compiled.

| # | Fix | Where / test |
|---|---|---|
| 20 | **Carry-over matching is id-first** (this replaces the order given in round 1). Passes, each stored row used once: (1) same id + same URL; (2) same URL where the new id is a `~N` sibling of the stored id (reordered duplicate `tvg-id`s); (3) same id; (4) same URL only when the stored id is gone from the new list **and** the URL is unique among both the stored and the new channels (renames by the new parser rules, e.g. a mis-cased `tvg-ID`); (5) pre-F1 legacy id. A rotated token link can no longer hand a favourite or history to another channel, and shared placeholder links never match by URL. | `ChannelCarryOver.kt`; `QcRound1ImporterTest.rotatedTokenLinkDoesNotMoveFavouriteToAnotherChannel`, `.sharedPlaceholderUrlsNeverMatchByUrl`, `.k29UpgradeKeepsEveryFavouriteOnItsOwnChannel` (QC's `qc-k29.m3u`, hosts changed to example.com, as fixture `commonTest/kotlin/assests/kodi/qc-k29.m3u`) |
| 21 | The Android picker check uses `queryIntentActivities` and ignores `com.android.tv.frameworkpackagestubs` (the Android TV `DocumentsStub`), so on such TVs the button is hidden and `import_file_unavailable_tv` shows. | `PlaylistFilePicker.android.kt` |
| 22 | ▲ / ▼ on the name and link fields move focus explicitly (name ▲ → top bar, name ▼ → link, link ▲ → name, link ▼ → Add), even with the IME hidden. | `ImportIPTVScreen.dpadVertical` |
| 23 | `ChannelHistoryTracker` follows `MediaPlayer.isPlaying`: watch time runs only while the player reports playing. A channel behind the error overlay, or one still buffering, accrues nothing. | `ChannelHistoryTrackerTest` |
| 24 | History moves no longer leave a temporary row. When the target id already has history (for example an orphan row), the moved row's play count and watch time are merged in and the temporary row is deleted. The temporary prefix is plain ASCII (`::tsiptv-moving::`), not NUL. PII/URL printlns redacted: `AuthRepositoryImpl.sendPasswordResetEmail` (no email or message), `UserTrackingService` (no email or message), `IPTVParserService` (exception type only), `HomeChannelItem` (logo-URL error logging removed). | `ChannelHistoryDao.mergeHistoryInto/deleteHistoryRow`; `RoomImportPipelineTest.historyMoveMergesAndLeavesNoTemporaryRows` (also covers two `~N` feeds swapping places) |
