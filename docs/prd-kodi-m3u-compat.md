# PRD F1 — Kodi-compatible M3U and per-channel playback properties

Status: **Ready for development, 2026-09-27** · Owner: PO · Platforms: Android (phone + TV), iOS, Desktop
Research (binding): [`research/kodi.md`](research/kodi.md), [`research/playlist-formats.md`](research/playlist-formats.md)
Roadmap: [`roadmap-sources-and-formats.md`](roadmap-sources-and-formats.md) — F1 is the foundation for F2 and F3.

---

## Problem

Most IPTV playlists that users bring are written for Kodi's PVR IPTV Simple Client. TS IPTV reads
only a small part of that dialect, so today:

1. **Channels that need a User-Agent, Referer or token do not play.** `#EXTVLCOPT` and `#KODIPROP`
   lines are skipped, and the `|User-Agent=…` suffix stays inside the URL, so Media3 requests a URL
   containing `|` (research `kodi.md` §1.10, gaps 2–3).
2. **DRM is wrong for everyone.** `MediaPlayerService` attaches a hard-coded Widevine licence URL
   (`https://license.widevine.com/getlicense`) to any URL containing "drm", "encrypted",
   "protected" or "widevine", and ignores the licence the playlist actually declares.
3. **Guides are missing.** Only `url-tvg` is read; `x-tvg-url`, the attribute Kodi documents and
   iptv-org uses, is ignored.
4. **Names and groups break.** Names with commas are cut at the last comma; `group-title="A;B"`
   becomes one group called "A;B"; `#EXTGRP` is not a begin directive; `tvg-ID` casing and
   `x-tvg-url`-style keys (two hyphens) are lost by the attribute regex.
5. **Data is lost.** A channel's attributes are written to `channel_attributes` only on the silent
   daily refresh in `RoomIPTVDatabase.getPlaylistById`, never on the first import
   (`HomeViewModel.parseIptvSource`) or the manual refresh (`refreshIPTVChannel`). Two channels with
   the same `tvg-id` in one playlist overwrite each other (`ChannelDao` uses `REPLACE` on `id`).
6. **Formats are misdetected.** A JSON array (`[ … ]`) is sent to the M3U parser; an HTML page gives
   a confusing "missing #EXTM3U" error; `.strm` files cannot be imported at all.
7. **The landing page promises "from a URL or a local file"**, but the app has no file import.

## Goals

1. Parse the full PVR IPTV Simple M3U dialect listed in scope, except the parts excluded by policy.
2. Play channels with their own HTTP headers and their own DRM (Widevine, PlayReady, ClearKey) on
   Android; fail clearly on devices that cannot.
3. Persist everything needed for playback through a **non-destructive** Room migration.
4. Import `.strm` and playlist files from device storage.
5. Leave catch-up data stored and ready, so catch-up playback (F1b) needs no second migration.

## Non-goals

- **Kodi add-ons of any kind.** No `plugin://` resolution, `addon.xml`, `addons.xml`,
  `repository.*`, zip installs or Python. Entries that need them are skipped with a clear message.
- **`#WEBPROP` / `@url` page scraping.** Refused by policy (`kodi.md` §1.6).
- **Catch-up playback UI** (see "Decision: catch-up" below). Catch-up attributes are parsed and stored.
- **DRM licence wrappers**: `license_key` bodies other than `R{SSM}`, responses other than raw,
  `B{SSM}`/`{HASH}` GET licences, `drm` JSON `req_data`/`wrapper`/`unwrapper`/`server_certificate`,
  custom PSSH. These channels show "DRM setup not supported yet" (phase 2, only if users need it).
- WisePlay, FairPlay. iOS gets no DRM at all in this feature.
- `#EXTVLCOPT:program=` (MPEG-TS program selection), `inputstream.ffmpegdirect.*`, XZ-compressed XMLTV.
- `.nfo` metadata. Digit-key channel entry on TV remotes.
- Shipping, suggesting or linking any playlist. TS IPTV stays a neutral player.

## Decision: catch-up is stored now, played later (F1b)

F1 parses `catchup`/`catchup-type`, `catchup-source`, `catchup-days`/`tvg-rec`, `catchup-correction`,
legacy `timeshift`, and the header-level defaults, and stores them as a typed `CatchupSpec` per
channel. **Playback of past programmes ships as F1b**, after F3, because:

1. **There is no entry point for it yet.** The Programs screen and channel guide show what is on now
   and next; catch-up needs a past-programme list and a "watch from start" action on phone and TV,
   which is its own design task.
2. **It cannot be QC'd with public fixtures.** Each mode (`default`, `append`, `shift`, `flussonic`,
   `fs`, `xc`) needs a real archive server; there are no public test servers. The URL templating can be
   unit-tested, the playback cannot.
3. **Nothing is lost by waiting.** The data is persisted in the F1 migration; F1b is pure code
   (a `CatchupUrlBuilder` + UI) with no schema change.
4. **F2 and F3 are larger user-visible wins** for the same engineering time.

No catch-up badge or button is shown in F1 (a badge that does nothing would read as a bug).

---

## Behaviour

### 1. Format detection (`IPTVParserFactory.detectFormat`)

Before detection: strip a UTF-8 BOM and leading whitespace; if the bytes start with `1F 8B`,
gunzip first (use `getManualGzipIfNeed` for playlist downloads too, not only EPG).

| Content (after trimming) | Result |
|---|---|
| Starts with `<!DOCTYPE html` or `<html` (case-insensitive) | Error `import_error_html_page` |
| JSON object with `"format": "tsiptv-source"` in the first 64 KiB | `TSIPTV_SOURCE` (F3; until F3 ships: error "needs a newer version") |
| Starts with `#EXTM3U` or `#EXTINF` **and** contains `#EXT-X-TARGETDURATION`, `#EXT-X-STREAM-INF` or `#EXT-X-MEDIA-SEQUENCE` | `HLS_MANIFEST` → dialog "This is a single stream" (7) |
| Starts with `#EXTM3U` or `#EXTINF` | `M3U` |
| XSPF (existing rule) | `XSPF` |
| `<?xml` or `<tv` | `XML` (existing) |
| Starts with `[` and the first element has `channel`, `url` and `title` and no `name` | `JSON_IPTV_ORG` |
| Starts with `{` or `[` | `JSON` (**fix**: arrays were sent to M3U) |
| Only `#KODIPROP:`/`#EXTVLCOPT:`/`#` comment lines and **exactly one** URL line | `STRM` (5) |
| Two or more non-`#` lines that are all URLs, no `#EXTINF` | `M3U_PLAIN`: one channel per URL, named after the last path segment without extension, else "Stream N" |
| Anything else | Error `import_error_unknown_format` (today it silently goes to M3U and fails with "missing #EXTM3U") |

### 2. M3U parsing (`core/parser/iptv/m3u/M3UParser.kt`)

**Header line (`#EXTM3U …`)**

| Attribute | Behaviour |
|---|---|
| `x-tvg-url` | EPG URLs. Comma-separated values are split, trimmed, and **all** are kept (Kodi keeps only the first). |
| `url-tvg` | Read only when `x-tvg-url` is absent. Same splitting. |
| `tvg-shift` | Default guide shift in hours (decimal) for channels without their own. |
| `catchup`, `catchup-type`, `catchup-days`, `catchup-source`, `catchup-correction` | Defaults for channels without their own value. |

**Attributes on `#EXTINF`**

- Syntax: key `[A-Za-z0-9_-]+`, `=`, double-quoted value. Keys are **lower-cased**
  (`tvg-ID` → `tvg-id`). Aliases: `ch-number` → `tvg-chno`, `catchup-type` → `catchup`,
  `tvg-rec` → `catchup-days`.
- **Name**: the text after the first comma that is **outside** a quoted value, trimmed.
  `#EXTINF:-1 tvg-name="A, B" group-title="News",Channel, with comma` → name `Channel, with comma`.
  If empty, fall back to `tvg-name`, then to "Unknown channel". **Behaviour change**: `tvg-name`
  no longer overrides the display name; it is used for guide matching only (Kodi semantics).
- **Groups**: `group-title` is split on `;`, trimmed, empty parts dropped, duplicates removed.
  The first group is the channel's primary group (`categoryId`, unchanged semantics); all groups are
  stored in `groups`. A channel appears under every group it belongs to.
- `tvg-chno` → `number` (integer; non-numeric values ignored).
- `radio="true"` (case-insensitive) → `isRadio`.
- `media="true"` or `#EXT-X-PLAYLIST-TYPE:VOD` in the stanza → `isVod` (stored; plays as today).
- `tvg-shift` → per-channel guide shift (overrides header).
- `user-agent`, `http-user-agent`, `referrer`, `http-referrer` as attributes (non-standard, seen in
  generated lists) → headers, lowest priority.

**`#EXTGRP`**

- Inside a stanza (after `#EXTINF`, before the URL): if the `#EXTINF` has no `group-title`, its
  value (split on `;`) becomes the channel's groups.
- Outside a stanza (before an `#EXTINF`): it becomes the **sticky default group** for every following
  channel that has no `group-title`, until an empty `#EXTGRP:` line or the end of the file.

**`#KODIPROP:key=value`** — key lower-cased, value to end of line (JSON allowed). Stored raw and
interpreted:

| Key | Use |
|---|---|
| `inputstream`, `inputstreamaddon`, `inputstreamclass` | Ignored (Kodi demuxer selection). |
| `mimetype` | MIME hint: `application/dash+xml`, `application/vnd.apple.mpegurl`/`application/x-mpegurl`, `application/vnd.ms-sstr+xml`, `video/mp2t`. |
| `inputstream.adaptive.manifest_type` | `mpd` → DASH, `hls` → HLS, `ism` → SmoothStreaming MIME hint. |
| `inputstream.adaptive.common_headers`, `manifest_headers`, `stream_headers` | Headers (`name=urlencoded&…`, values URL-decoded). All three are merged and applied to every request. |
| `inputstream.adaptive.license_type`, `license_key`, `drm_legacy`, `drm` | DRM (section 4). |
| anything else | Stored raw, no effect. |

**`#EXTVLCOPT:key=value`** (also `#EXTVLCOPT--key=value`)

| Key | Use |
|---|---|
| `http-user-agent` | `User-Agent` header |
| `http-referrer`, `http-referer` | `Referer` header |
| anything else | Stored raw, no effect |

**URL line**

- `|` suffix: split at the **first** `|`. The left part is the stream URL (stored without the
  suffix). The right part is `name=value&name=value`: values are percent-decoded (a malformed escape
  keeps the raw text), a leading `!` on a name is removed, `cookies` → `Cookie`, `seekable` is ignored.
- `plugin://…` URLs: the entry is **skipped** (reason `KODI_ADDON`).
- URLs starting with `@` or stanzas with `#WEBPROP:`: **skipped** (reason `WEB_SCRAPING`).
- Other schemes pass through unchanged (`http`, `https`, `rtmp`, `rtsp`, `rtp`, `udp`, `srt`,
  `mms`, `mmsh`); playability is the platform player's business, as today.

**Header priority** (highest wins, per header name, case-insensitive): `|` suffix →
`#EXTVLCOPT` → `inputstream.adaptive.*_headers` → non-standard attributes. The JSON parser's
`attributes["user-agent"]`/`["referrer"]` and iptv-org's `user_agent`/`referrer` fields map to the
same headers.

**Channel ids**: `id = tvg-id` if present, else slug of the name (as today). If the id is already
used **in the same playlist**, append `~2`, `~3`, … in file order. `epgId` stays the raw `tvg-id`.
The first occurrence keeps the plain id, so existing history and favourites survive.

### 3. Per-channel headers at playback

The player receives a `MediaItem` carrying `headers: Map<String, String>`.

| Platform | Mechanism | Headers applied |
|---|---|---|
| Android | For each item, build the `MediaSource` with an `OkHttpDataSource.Factory` from `playerHttpDataSourceFactory()` plus `setDefaultRequestProperties(headers)`; a channel `User-Agent` replaces the default Dalvik agent. The `EdgeFailover` interceptors stay in the chain. | All |
| iOS | `AVURLAsset(URL, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])` (undocumented key, best effort) | All, best effort |
| Desktop (VLCJ) | Media options `:http-user-agent=…` and `:http-referrer=…` | `User-Agent`, `Referer` only; others dropped (debug log only) |

Headers apply to manifest, variant, segment and key requests of that channel only. Changing channel
must not leak headers to the next channel.

### 4. Per-channel DRM

**Parsing** into `DrmSpec(system, licenseUrl?, licenseHeaders, clearKeys, unsupportedReason?)`.
If several syntaxes are present, the newest wins: `drm` (JSON) → `drm_legacy` → `license_type` +
`license_key`.

| Input | Rule |
|---|---|
| Key system names | `com.widevine.alpha`/`widevine` → WIDEVINE; `com.microsoft.playready`/`playready` → PLAYREADY; `org.w3.clearkey`/`clearkey` → CLEARKEY; `com.huawei.wiseplay` or unknown → unsupported. |
| `license_key` (4 fields `URL\|headers\|body\|response`) | Field 1 = licence URL (if it contains `{SSM}`, `B{SSM}` or `{HASH}` → unsupported). Field 2 = `k=v&k=v`, values URL-decoded. Field 3 must be empty or `R{SSM}`; field 4 empty or `R`; anything else → unsupported. |
| `license_key` with `license_type=clearkey` (lenient, common in lists) | `kid:key[,kid:key]` → keys; `{"keys":[…]}` JWKS or `data:application/json;base64,…` → keys; URL → licence URL. |
| `drm_legacy` (`system\|urlOrKeys\|headers`, 1–3 fields; more → unsupported) | Field 2: URL/`data:` URI → licence (ClearKey data URI → keys); otherwise `kid:key` pairs. Field 3 = headers. |
| `drm` JSON | Candidate key systems in `priority` order (then document order); take the first supported one whose config uses only `license.server_url`, `license.req_headers`, `license.keyids`; skip systems using `use_http_get_request`, `req_data`, `req_params`, `wrapper`, `unwrapper`, `server_certificate`, `init_data`, `pre_init_data`. None left → unsupported. |
| Key formats | KID and key: 32 hex characters (UUID dashes stripped). Anything not matching `^[0-9a-fA-F]{32}$` is decoded as base64/base64url and must be 16 bytes, else unsupported. |

**Android playback**

- Remove the hard-coded `WIDEVINE_UUID` + `DRM_LICENSE_URL` session manager and the
  `isDrmProtected(url)` heuristic from `MediaPlayerService`. A channel without `DrmSpec` is played
  without DRM configuration; Media3 still handles HLS AES-128 natively.
- WIDEVINE / PLAYREADY / CLEARKEY with URL: `MediaItem.DrmConfiguration.Builder(uuid)
  .setLicenseUri(url).setLicenseRequestHeaders(licenseHeaders).setForceDefaultLicenseUri(true)`,
  through a `DefaultDrmSessionManagerProvider` whose `setDrmHttpDataSourceFactory` uses the
  channel's headers (some licence servers check User-Agent/Referer).
- CLEARKEY with keys: convert to a W3C JWKS (`kty:"oct"`, `kid`/`k` base64url without padding,
  `type:"temporary"`) and play through a `DefaultDrmSessionManager` built with
  `LocalMediaDrmCallback(jwks)` for that item only. ClearKey is DASH-only on Media3: a ClearKey
  HLS channel shows `drm_not_supported_config`.
- `unsupportedReason != null`: do not start playback; show `drm_not_supported_config`.

**iOS and desktop**: any channel with a `DrmSpec` shows `drm_not_supported_device` before any
network request is made, and TV/zapping continues to work.

**Errors**: Media3 `ERROR_CODE_DRM_*` → `drm_license_failed` (licence refused / provisioning
failed) or `drm_not_supported_device` (`ERROR_CODE_DRM_SCHEME_UNSUPPORTED`).

### 5. `.strm` and file import

- New action **Import from file** next to "Add by link".
  - Android phone/tablet: Storage Access Framework `ACTION_OPEN_DOCUMENT`, MIME `*/*`.
  - Android TV: shown only if an activity resolves `ACTION_OPEN_DOCUMENT` (most TVs have none);
    otherwise hidden, and the import screen shows `import_file_unavailable_tv` under "Add by link".
  - iOS: `UIDocumentPickerViewController` (types `public.data`, `public.plain-text`).
  - Desktop: native file dialog filtered to `*.m3u, *.m3u8, *.strm, *.json, *.xspf, *.xml`.
- Max file size **20 MiB**; larger → `import_error_file_too_large`.
- The file is read once. Its content is parsed like downloaded content (same detection); the file
  itself is not kept. The playlist is stored with `sourceType = FILE`, `url = "file:" + display name`,
  id = `"file:" + hash(display name)`. Importing a file with the same display name asks
  `import_replace_existing`. Refresh on a FILE playlist shows `playlist_file_refresh_hint` instead
  of a network call; the daily auto-refresh skips FILE playlists.
- `.strm`: `#KODIPROP`/`#EXTVLCOPT` lines + one URL (with optional `|headers`) → a one-channel
  playlist; channel and playlist name = file name without extension. A `plugin://` URL →
  `strm_error_kodi_addon`, nothing imported.

### 6. Import summary

After a successful import or manual refresh, the existing success message
(`iptv_import_success_msg`) is followed, when anything was skipped, by
`import_summary_skipped` and a **Details** action listing counts per reason
(`KODI_ADDON`, `WEB_SCRAPING`, `NO_URL`, `DRM_UNSUPPORTED` is **not** a skip reason: such channels
are imported and fail at play time with a clear message).

### 7. Single HLS stream detection

When the content is an HLS manifest, show a dialog (`import_hls_single_title`/`_message`) with
**Add as channel** and **Cancel**. "Add as channel" creates a one-channel playlist named after the
name the user typed, pointing at the URL.

### 8. EPG

- All `x-tvg-url`/`url-tvg` URLs are fetched (max 5, sequentially), each with its own error
  handling; programmes are merged and de-duplicated by (channel id, start time).
- `tvg-shift` (channel, else header) shifts that channel's programme times by the given hours when
  programmes are matched and displayed.

### 9. Privacy fix bundled with F1

`HomeViewModel.parseIptvSource` sends the playlist **URL** to Firebase Analytics
(`PARAMS_IPTV_URL`). With F1, URLs may carry tokens and `|Authorization=` suffixes. Stop sending the
URL and the user-typed name; send only the detected format and channel count. Check
`play-store/data-safety.md` still matches.

---

## Data model

### Parser models (`core/parser/model/`)

- `IPTVChannel` gains: `groups: List<String>`, `number: Int?`, `isRadio: Boolean`, `isVod: Boolean`,
  `headers: Map<String, String>`, `mimeType: String?`, `drm: DrmSpec?`, `catchup: CatchupSpec?`,
  `epgShiftHours: Double?`. `attributes` keeps all raw attributes plus `kodiprop:<key>` and
  `vlcopt:<key>` entries.
- `IPTVPlaylist` gains `epgUrls: List<String>` (keep `epgUrl` = first, for existing callers) and
  `skipped: List<SkippedEntry(reason, lineNumber)>`.
- New `core/parser/model/playback/`: `DrmSpec`, `DrmSystem` (WIDEVINE, PLAYREADY, CLEARKEY),
  `CatchupSpec(mode, source, days, correctionHours)`, all `@Serializable`.
- `IPTVFormat` gains `STRM`, `M3U_PLAIN`, `HLS_MANIFEST`, `TSIPTV_SOURCE` (reserved for F3).

### App models

- `core/model/Channel` gains `number`, `groups`, `isRadio`, `isVod`, `headers`, `mimeType`, `drm`,
  `catchup`, `epgShiftHours`, `sortIndex` (all with defaults so existing constructors compile).
- `player/models/MediaItem` gains `headers: Map<String, String> = emptyMap()`,
  `drm: DrmSpec? = null`, `isRadio: Boolean = false`. `Channel.toMediaItem()` copies them.
- `Playlist` gains `sourceType` (`URL`, `FILE`) and `epgUrls`.

### Room: version 3 → 4 (**non-destructive**)

`getRoomDatabase` uses `fallbackToDestructiveMigration(true)`: a missing or failing migration
**silently deletes every user's playlists and history**. The migration is therefore a release gate.

| Table | Column | Type | Default |
|---|---|---|---|
| `channel` | `channelNumber` | INTEGER NULL | NULL |
| `channel` | `groupsJson` | TEXT NULL | NULL (JSON array of strings) |
| `channel` | `isRadio` | INTEGER NOT NULL | 0 |
| `channel` | `isVod` | INTEGER NOT NULL | 0 |
| `channel` | `headersJson` | TEXT NULL | NULL (JSON object) |
| `channel` | `mimeType` | TEXT NULL | NULL |
| `channel` | `drmJson` | TEXT NULL | NULL (serialized `DrmSpec`) |
| `channel` | `catchupJson` | TEXT NULL | NULL (serialized `CatchupSpec`) |
| `channel` | `epgShiftHours` | REAL NULL | NULL |
| `channel` | `sortIndex` | INTEGER NOT NULL | 0 (file order; list queries `ORDER BY sortIndex`) |
| `playlists` | `sourceType` | TEXT NOT NULL | `'URL'` |
| `playlists` | `epgUrlsJson` | TEXT NULL | NULL |

- `@Database(version = 4, autoMigrations = [AutoMigration(2, 3), AutoMigration(3, 4, spec = Migration3To4::class)])`,
  export `composeApp/schemas/.../4.json`.
- `Migration3To4.onPostMigrate`: `UPDATE playlists SET lastUpdated = 0 WHERE sourceType = 'URL'`
  so every URL playlist re-parses (and gains headers/DRM) the next time it is opened.
- `ChannelWithHistory` (a partial projection) must not be used to start playback: every path to the
  player loads the full `Channel` by id first (`PlayerViewModel.verifyPlayingMediaItem`,
  `HomeEvent.OnResumeMediaItem`, history screens).
- `InMemoryIPTVDatabase` gets the same fields.

### One import pipeline

The three call sites that parse and store playlists today (`HomeViewModel.parseIptvSource`,
`HomeViewModel.refreshIPTVChannel`, `RoomIPTVDatabase.getPlaylistById`) are replaced by one
`PlaylistImporter` use case: download (or read file) → detect → parse → map → store playlist,
categories, channels **and** attributes in one transaction → fetch EPGs. This removes the current
divergence (attributes only on one path) and is where F3 plugs in.

**Known risk, not fixed in F1 (R1)**: `channel.id` and `categories.id` are global primary keys.
The same `tvg-id` (or group name) in two different playlists makes the second import take the row
over from the first. Fixing it needs an id-rewrite migration that also rewrites
`channel_history` and favourites; it is scheduled separately (roadmap). F2 and F3 store their own
content in their own tables and namespace ids, so they do not add to the problem.

---

## UI

### Phone

- **Import screen** (`ui/screens/addiptv/ImportIPTVScreen.kt`): below the URL field, a secondary
  button **Import from file** (`import_from_file_title`). Loading state and cancel as today.
- **Import result**: success dialog as today; when entries were skipped, an extra line
  `import_summary_skipped` and a **Details** button opening a sheet `import_details_title` with one
  row per reason (`import_skipped_kodi_addon`, `import_skipped_web_scraping`,
  `import_skipped_no_url`).
- **Channel rows/cards** (`HomeChannelItem`, `homeItemList`): if `number` is set, show it before the
  name (`101  Example News`); radio channels show a radio icon and `channel_badge_radio`.
- **Groups**: the category chips list every group; a channel with two groups is shown under both.
- **Player**: radio channels show the logo centred on the background instead of a black video area.
  DRM errors use the existing error overlay with the new messages and a **Back** action.

### TV (D-pad)

- Import dialog: **Import from file** is a focusable button after the URL field, in the D-pad order
  "back → name → link → add → import from file" (extend the existing explicit `FocusRequester`
  chain). Hidden when no document picker exists; then `import_file_unavailable_tv` is shown as text.
- Import result dialog: **OK** has initial focus; **Details** is reachable with ▶. Back closes.
- Channel grid (`TvHomeScreen.ChannelCard`): number badge top-left; radio icon for radio channels.
  The category rail lists every group.
- Player (`TvPlayerScreen`): the zapping overlay shows the channel number. On a DRM error, the
  message is shown for the current channel and ▲/▼ still zap to the next/previous channel; Back
  returns Home with focus restored as today.

### iOS and desktop

Same screens as phone. File import uses the platform picker. DRM channels show
`drm_not_supported_device`.

## Error states

| Situation | Message key | Where |
|---|---|---|
| Link returns an HTML page | `import_error_html_page` | Import dialog |
| Content not recognised | `import_error_unknown_format` | Import dialog |
| File > 20 MiB | `import_error_file_too_large` | Import dialog |
| `.strm` with `plugin://` | `strm_error_kodi_addon` | Import dialog |
| Entries skipped | `import_summary_skipped` + details | Import result |
| HLS manifest imported as list | `import_hls_single_title` / `_message` | Dialog with **Add as channel** |
| Refresh a file playlist | `playlist_file_refresh_hint` | Toast/snackbar (phone), dialog (TV) |
| DRM on iOS/desktop, or ClearKey on HLS | `drm_not_supported_device` | Player overlay |
| DRM syntax not supported | `drm_not_supported_config` | Player overlay |
| Licence server refused | `drm_license_failed` | Player overlay |
| HTTP 401/403 on a channel with headers | `stream_error_forbidden_headers` | Player overlay |
| TS IPTV Source detected before F3 ships | `import_error_needs_update` | Import dialog |

## Strings

New keys, in `composeResources/values*/strings.xml` for **all seven locales** (en, vi, de, es, fr,
ja, zh-CN). English and Vietnamese are given; the Dev agent translates the other five and QC checks
every key exists in every file.

| Key | English | Vietnamese |
|---|---|---|
| `import_from_file_title` | Import from file | Nhập từ tệp |
| `import_file_unavailable_tv` | This TV has no file picker. Add the playlist by link instead. | TV này không có trình chọn tệp. Hãy thêm danh sách phát bằng đường dẫn. |
| `import_error_file_too_large` | This file is larger than 20 MB. | Tệp này lớn hơn 20 MB. |
| `import_error_html_page` | This link opens a web page, not a playlist. Use the raw or direct-download link. | Đường dẫn này mở một trang web, không phải danh sách phát. Hãy dùng đường dẫn tệp gốc (raw) hoặc tải trực tiếp. |
| `import_error_unknown_format` | This content is not a playlist format TS IPTV can read. | Nội dung này không phải định dạng danh sách phát mà TS IPTV đọc được. |
| `import_error_needs_update` | This playlist needs a newer version of TS IPTV. | Danh sách phát này cần phiên bản TS IPTV mới hơn. |
| `import_replace_existing` | A playlist named "%1$s" already exists. Replace it? | Đã có danh sách phát tên "%1$s". Thay thế? |
| `import_summary_skipped` | %1$d entries could not be imported. | Không thể nhập %1$d mục. |
| `import_details_title` | Import details | Chi tiết nhập |
| `import_details_action` | Details | Chi tiết |
| `import_skipped_kodi_addon` | %1$d need a Kodi add-on (plugin://) and cannot play in TS IPTV | %1$d mục cần tiện ích Kodi (plugin://) và không phát được trong TS IPTV |
| `import_skipped_web_scraping` | %1$d use web-page scraping, which TS IPTV does not support | %1$d mục dùng cách lấy luồng từ trang web, TS IPTV không hỗ trợ |
| `import_skipped_no_url` | %1$d have no stream link | %1$d mục không có đường dẫn luồng |
| `import_hls_single_title` | This is a single stream | Đây là một luồng đơn |
| `import_hls_single_message` | The link is one video stream, not a list of channels. Add it as one channel? | Đường dẫn là một luồng video, không phải danh sách kênh. Thêm nó thành một kênh? |
| `import_hls_single_action` | Add as channel | Thêm thành kênh |
| `strm_error_kodi_addon` | This .strm file points to a Kodi add-on (plugin://) and cannot be played in TS IPTV. | Tệp .strm này trỏ tới tiện ích Kodi (plugin://) và không phát được trong TS IPTV. |
| `playlist_file_refresh_hint` | This playlist was imported from a file. Import the file again to update it. | Danh sách phát này được nhập từ tệp. Hãy nhập lại tệp để cập nhật. |
| `channel_badge_radio` | Radio | Radio |
| `drm_not_supported_device` | This channel is DRM-protected. DRM is not supported on this device. | Kênh này được bảo vệ bằng DRM. Thiết bị này không hỗ trợ DRM. |
| `drm_not_supported_config` | This channel's DRM setup is not supported yet. | Cấu hình DRM của kênh này chưa được hỗ trợ. |
| `drm_license_failed` | The DRM licence server refused this device. Check that your subscription is active. | Máy chủ giấy phép DRM từ chối thiết bị này. Hãy kiểm tra gói thuê bao của bạn còn hiệu lực. |
| `stream_error_forbidden_headers` | The server refused this stream. The playlist's headers or token may have expired. | Máy chủ từ chối luồng này. Header hoặc mã truy cập trong danh sách phát có thể đã hết hạn. |

---

## Acceptance criteria

Fixtures live in `composeApp/src/commonTest/kotlin/assests/kodi/` and use `example.com` hosts
(plus the Mux test stream `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` for real playback).

**Parsing**

- **AC-K1** The README reference playlist (`kodi.md` §1.9) parses to 9 channels; `Channel X` and
  `Channel X HD` both exist (ids `channel-x` and `channel-x~2`), `Channel X HD` is in groups
  `Entertainment` and `HD Channels`, and `Channel X` has `isRadio = true` and `number = 10`.
- **AC-K2** `#EXTM3U x-tvg-url="https://a.example/1.xml, https://a.example/2.xml.gz"` yields two EPG
  URLs; `url-tvg` is used only when `x-tvg-url` is absent.
- **AC-K3** `#EXTINF:-1 tvg-name="A, B" group-title="News",Channel, with comma` → name
  `Channel, with comma`, group `News`, `tvg-name` kept as an attribute.
- **AC-K4** `tvg-ID="X"` is stored as `tvg-id` and used as `epgId`; `ch-number="5"` gives `number = 5`.
- **AC-K5** `#EXTGRP:Movies` before two stanzas without `group-title` puts both in `Movies`; a third
  stanza after an empty `#EXTGRP:` has no group; a stanza with `group-title` ignores the sticky group.
- **AC-K6** `https://cdn.example.com/a.m3u8|User-Agent=Mozilla%2F5.0&Referer=https%3A%2F%2Fexample.com%2F`
  is stored as URL `https://cdn.example.com/a.m3u8` with headers `User-Agent: Mozilla/5.0` and
  `Referer: https://example.com/`. `|user-agent=Mozilla/5.0 (Windows NT 10.0)` (unencoded) is accepted.
- **AC-K7** `#EXTVLCOPT:http-user-agent=UA1` plus `|User-Agent=UA2` → header `UA2`;
  `#EXTVLCOPT:http-referrer=R` → `Referer: R`.
- **AC-K8** `#KODIPROP:inputstream.adaptive.stream_headers=X-Token=abc%3D` → header `X-Token: abc=`;
  `#KODIPROP:mimetype=application/dash+xml` → MIME hint DASH.
- **AC-K9** A stanza with a `plugin://` URL and one with `#WEBPROP` are skipped and counted in the
  import summary under the right reasons; the rest of the playlist imports.
- **AC-K10** `catchup="append" catchup-source="&cutv={Y}-{m}-{d}T{H}:{M}:{S}" catchup-days="3"`
  is stored as `CatchupSpec(APPEND, "&cutv=…", 3, 0)`; a header `catchup="shift"` applies to a
  channel with none of its own; `timeshift="2"` → `SHIFT`, 2 days.

**DRM parsing**

- **AC-K11** `license_type=com.widevine.alpha` + `license_key=https://lic.example.com/wv|User-Agent=A&Content-Type=application%2Foctet-stream|R{SSM}|R`
  → WIDEVINE, URL `https://lic.example.com/wv`, headers `User-Agent: A`, `Content-Type: application/octet-stream`.
- **AC-K12** `drm_legacy=org.w3.clearkey|000102030405060708090a0b0c0d0e0f:00112233445566778899aabbccddeeff`
  → CLEARKEY; the generated JWKS is exactly
  `{"keys":[{"kty":"oct","kid":"AAECAwQFBgcICQoLDA0ODw","k":"ABEiM0RVZneImaq7zN3u_w"}],"type":"temporary"}`.
- **AC-K13** `license_type=clearkey` + `license_key=00010203-0405-0607-0809-0a0b0c0d0e0f:00112233445566778899aabbccddeeff`
  → same keys as AC-K12 (dashes stripped).
- **AC-K14** `license_key` with body `b{SSM}` or response `JBlicense`, and `drm` JSON using
  `unwrapper`, produce `unsupportedReason != null`; `license_type=com.huawei.wiseplay` too.
- **AC-K15** `drm={"com.widevine.alpha":{"license":{"server_url":"https://lic.example.com/wv","req_headers":"X-A=1"}}}`
  → WIDEVINE with header `X-A: 1`; when both `drm` and `license_type` are present, `drm` wins.

**Playback**

- **AC-K16** (Android) A channel whose headers include `User-Agent: TSIPTV-QC/1.0` plays the Mux
  stream and a proxy/`mitmproxy` log shows that UA on the master playlist, a variant playlist and a
  segment request. The next channel without headers is requested with the default UA.
- **AC-K17** (Android) A channel URL containing "drm" but no DRM properties plays **without** any
  licence request (no request to `license.widevine.com`).
- **AC-K18** (Android) A Widevine channel sends its licence request to the playlist's URL with its
  licence headers (verified against a test licence endpoint or proxy log).
- **AC-K19** (iOS, desktop) A channel with any `DrmSpec` shows `drm_not_supported_device` without
  requesting the stream URL; the next channel plays.
- **AC-K20** (desktop) A channel with `User-Agent` and `Referer` headers sends both (VLC log at
  verbosity 2); other headers are not sent and nothing crashes.
- **AC-K21** A radio channel shows the radio badge in the list and the logo layout in the player,
  and keeps playing in the background with the screen off (Android).
- **AC-K22** Playing a channel from History or "continue watching" uses its stored headers/DRM
  (same proxy check as AC-K16).

**Import, detection, persistence**

- **AC-K23** A JSON array playlist (`[{"id":"a","name":"A","url":"https://cdn.example.com/a.m3u8"}]`)
  imports as JSON with one channel.
- **AC-K24** A URL that returns an HTML page shows `import_error_html_page`; a gzip-compressed M3U
  imports normally; a BOM before `#EXTM3U` imports normally.
- **AC-K25** An HLS master playlist URL shows the single-stream dialog; **Add as channel** creates a
  one-channel playlist that plays.
- **AC-K26** A `.strm` file with two `#KODIPROP` lines and an `https` URL imports as one channel named
  after the file; a `.strm` with `plugin://` shows `strm_error_kodi_addon` and imports nothing.
- **AC-K27** File import works on Android phone, iOS and desktop; on an Android TV emulator without
  DocumentsUI the button is hidden and the hint is shown; a 25 MB file is refused.
- **AC-K28** Refreshing a FILE playlist shows `playlist_file_refresh_hint` and makes no network call.
- **AC-K29** **Upgrade test**: install the current release, import a playlist, favourite a channel,
  play two channels; install the F1 build over it. Playlists, favourites and history are all still
  there; opening the playlist re-parses it (headers present in DB). A Room migration test 3 → 4 runs
  in CI with exported schemas.
- **AC-K30** Import, manual refresh and daily auto-refresh all store the same data for the same
  playlist (compare `channel` and `channel_attributes` rows).
- **AC-K31** Firebase Analytics debug view shows no playlist URL or name for the add-playlist event.
- **AC-K32** All new strings exist in all seven locale files; the TV import dialog is fully usable
  with D-pad only (focus order as specified, visible focus, Back always closes).

## Implementation plan (ordered)

1. **Models** — `core/parser/model/IPTVChannel.kt`, `IPTVPlaylist.kt`, `IPTVFormat.kt`; new
   `core/parser/model/playback/{DrmSpec,CatchupSpec}.kt`; `core/model/Channel.kt`, `Playlist.kt`;
   `player/models/MediaItem.kt` (+ `toMediaItem`).
2. **Parsers** — rewrite the line loop in `core/parser/iptv/m3u/M3UParser.kt` (header, quote-aware
   name, attribute regex, groups, sticky `#EXTGRP`, KODIPROP/EXTVLCOPT, `|` suffix, skips, id
   de-duplication); new `core/parser/iptv/m3u/KodiDrmParser.kt` and `HeaderSuffixParser.kt`; new
   `core/parser/strm/StrmParser.kt`; JSON/iptv-org header mapping in `JSONModels.kt` and
   `iptv/iptvorg/`. Tests: extend `commonTest/.../M3UParserTest.kt`, add `KodiDrmParserTest.kt`,
   `StrmParserTest.kt`, fixtures under `commonTest/kotlin/assests/kodi/`.
3. **Detection** — `core/parser/IPTVParserFactory.kt` (BOM, gzip, HTML, JSON arrays, iptv-org,
   STRM, plain M3U, HLS manifest, TS IPTV Source placeholder) + `IPTVParserFactoryTest.kt`.
4. **Room v4** — `core/database/entity/RoomEntities.kt`, `AppDatabase.kt` (version 4, AutoMigration
   3→4 with `Migration3To4` spec), `Converter.kt`, `entity/Mappers.kt`, `dao/ChannelDao.kt`
   (`ORDER BY sortIndex`), `InMemoryIPTVDatabase.kt`; commit `composeApp/schemas/.../4.json`;
   migration test in `desktopTest` (Room `MigrationTestHelper`).
5. **Import pipeline** — new `usecase/playlist/PlaylistImporter.kt`; switch
   `HomeViewModel.parseIptvSource`/`refreshIPTVChannel` and `RoomIPTVDatabase.getPlaylistById` to it;
   multi-EPG in `HomeViewModel.parsePlaylistEpg`; analytics fix; skipped-entry summary in `HomeUiState`.
6. **Android playback** — `player/service/MediaPlayerService.kt` (pass the full serialized
   `MediaItem` in the intent, per-item `MediaSource` with headers, remove fixed Widevine manager and
   `isDrmProtected`, DRM configuration and ClearKey local callback, error mapping),
   `player/network/PlayerHttpDataSource.kt` (factory accepting headers/UA), `AndroidMediaPlayer.kt`.
7. **iOS / desktop playback** — `iosMain/.../IOSMediaPlayer.kt` (AVURLAsset headers, DRM refusal),
   `desktopMain/.../DesktopMediaPlayer.kt` (VLC options, DRM refusal).
8. **File import** — `expect/actual` file picker (or FileKit if the team prefers a dependency) in
   `commonMain/.../platform/`, Android SAF + TV capability check, iOS document picker, desktop
   dialog; wire into `ui/screens/addiptv/ImportIPTVScreen.kt` and the TV import dialog.
9. **UI** — number/radio badges (`ui/screens/home/widget/HomeChannelItem.kt`, `ui/tv/TvHomeScreen.kt`),
   multi-group filtering in `HomeViewModel.searchWithFilter`, radio layout in the player views, import
   summary sheet/dialog, HLS single-stream dialog, player error messages (`ui/screens/player/`,
   `ui/tv/TvPlayerScreen.kt`).
10. **Strings** — seven `strings.xml` files; a unit test that compares key sets across locales.
11. **Docs** — update the formats line on `web/public/index.html` (both panes) only after QC passes.
