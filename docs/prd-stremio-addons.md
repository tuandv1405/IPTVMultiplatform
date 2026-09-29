# PRD F2 — Stremio addon support

Status: **Ready for development after F1, 2026-09-27** · Owner: PO · Platforms: Android (phone + TV), iOS, Desktop
Research (binding): [`research/stremio-addons.md`](research/stremio-addons.md) — section numbers below ("§1.3") refer to it.
Depends on: F1 (`MediaItem.headers`, per-item data sources, `PlaylistImporter` patterns).
Roadmap: [`roadmap-sources-and-formats.md`](roadmap-sources-and-formats.md)

---

## Problem

Stremio's addon protocol is the most widely used open way to publish movie, series and live-TV
catalogues as plain JSON over HTTP. Anyone can host an addon — even as static files on GitHub
Pages — and many people run their own for home media servers, licensed services or public-domain
archives. TS IPTV can play the HTTP/HLS/DASH streams these addons return, but it has no way to add
an addon, browse its catalogues, open a title or pick a stream.

At the same time, most *community* addons scrape torrents of copyrighted works. The feature must
support the **protocol** without ever pointing users at those addons.

## Goals

1. A user can add an addon they already know by pasting its manifest URL (or a `stremio://` link),
   and manage it (enable, disable, reorder, update, remove).
2. Browse the addon's catalogues by type, filter by genre, search, and page through them.
3. Open a movie, a series (seasons and episodes) or a live `tv` item, and pick a playable stream.
4. Play `url` streams with the addon's request headers; open `externalUrl` streams in the browser.
5. Remember progress and offer "Continue watching" for addon titles.

## Non-goals

- **No default, suggested, featured or recommended addons.** Nothing pre-installed, no "popular
  addons" list, no browsing of `api.strem.io/addonscollection.json` or Cinemeta's `community`
  addon catalog, no addon names in screenshots, store listing or help pages (other than TS IPTV's own
  public-domain sample).
- **Torrent (`infoHash`), Usenet (`nzbUrl`), archive (`rarUrls`, `zipUrls`, …), YouTube (`ytId`)
  and `playerFrameUrl` streams.** Never played, never counted, never named in the UI.
- Registering the `stremio://` URL scheme or intent filter (it would compete with the real Stremio app).
- Legacy `/stremio/v1` addons, `ipfs://`/`ipns://` transports, Stremio's local streaming server
  (`127.0.0.1:11470`) and the "Local Files" addon.
- Subtitles (`subtitles` resource) — phase F2b. Native EPG (`epgProvider`) — later. `addon_catalog`
  browsing — later, and only for addon lists the user adds themselves. Stremio account sync.
- Auto-play of the next episode (`bingeGroup`) — later.
- The word "Stremio" in the store listing, store screenshots or app name. In the app and on the
  website the feature is described as "Stremio-compatible addons" (descriptive use of the
  protocol's name only, no logo).

## Policy guardrails (binding)

| Rule | Implementation |
|---|---|
| Zero default addons | The addon table is empty on install; no URL constants for any third-party addon in the codebase (QC greps for `strem.io`, `strem.fun`, `stremio.net`, `beamup`). |
| User-provided label | Every addon screen shows "Added by you" and the addon's host. |
| Notice on add | `addon_notice` must be accepted once per addon before it is saved. |
| `p2p: true` | Warning `addon_warn_p2p`; the user may still add it. |
| `adult: true` | Warning `addon_warn_adult` plus an explicit "I am 18 or older" confirmation; adult addons are excluded from the Discover home rows and global search unless opened directly. |
| Kill switch | The app fetches `https://tsiptv-8bdd6.web.app/policy/addon-blocklist.json` (`{"ids":[],"hosts":[]}`, empty at launch) at most once a day. Matching addons are disabled with `addon_blocked`. Used only for takedown requests. |
| No torrent advertising | Unsupported streams are hidden by default, with no count and no kind label (see Stream picker). |

---

## Behaviour

### 1. Add an addon

Entry points: **Profile → Addons → Add addon** (phone), **Settings → Addons → Add addon** (TV),
**Discover → Add addon** when Discover is visible, and the empty state of the Addons screen.

Input normalisation (in order):

1. Trim. `stremio://host/path` → `https://host/path` (use `http://` only when the host is
   `localhost`, `127.0.0.1`, `10.x`, `192.168.x` or `172.16–31.x`).
2. Scheme must be `http` or `https`; the path must end with `/manifest.json` (query string kept).
   Otherwise → `addon_error_not_manifest`.
3. `/stremio/v1` paths → `addon_error_legacy`. `ipfs://`/`ipns://` → `addon_error_not_manifest`.
4. Hosts `127.0.0.1:11470` / `localhost:11470` → `addon_error_local_server`.

Fetch the manifest (timeouts: connect 10 s, total 15 s; follow redirects; `Accept: application/json`;
`User-Agent: TSIPTV/{version} (Stremio-addon-client)`). Validate: `id`, `name`, `version` (string,
not parsed as semver), `types` (array), `resources` (array) present; `catalogs` defaults to `[]`.
Failures → `addon_error_invalid_manifest` (with the missing field name in details).

- `behaviorHints.configurationRequired: true` → do not add; show `addon_needs_configuration` with
  **Configure** (opens `{base}/configure` in the browser; on TV shows the URL as text) and a field to
  paste the configured manifest URL.
- Blocklisted id or host → `addon_blocked`, not added.
- Same manifest `id` already installed → `addon_replace_existing` ("Replace the existing
  'Name'?"). Replacing keeps its position and enabled state.

**Preview sheet** before saving: logo (placeholder if missing), name, version, host, description,
types (localized labels for `movie`, `series`, `tv`, `channel`, `anime`, other raw strings as is),
number of catalogues, whether it provides streams, warnings (`http` → `addon_warn_http`, `p2p`,
`adult`), the `addon_notice`, and **Add** / **Cancel**. `configurable: true` adds an optional
**Configure** action (same flow as above).

### 2. Addon manager

List of installed addons in the user's order: logo, name, version, host, **Added by you**, an
enabled switch, and a status line (`addon_status_ok`, `addon_status_unreachable`,
`addon_status_blocked`). Actions per addon: **Move up / Move down** (phone also supports drag),
**Update now** (refetch manifest; a changed `version` shows `addon_updated`), **Configure** (if
`configurable`), **Remove** (confirm `addon_remove_confirm`).

- Manifests refresh automatically once a day when the app starts, and on **Update now**.
- After 3 consecutive failed refreshes the addon is marked unreachable; it is never removed
  automatically and keeps working from the cached manifest.
- Order matters: it is the order of rows on Discover, of meta lookups (first non-empty wins) and of
  stream groups in the picker.

### 3. Resource matching (§2.2)

- **catalog**: the addon's `catalogs` contains the `(type, id)`; every sent extra is declared; every
  `isRequired` extra is present. A non-empty `catalogs` implies the catalog resource.
- **meta / stream**: find the resource by name; string form uses manifest `types`/`idPrefixes`,
  object form uses its own `types` (absent = none) and `idPrefixes` (absent/empty = any id).
- Catalog extras: `extra` wins; else legacy `extraSupported`/`extraRequired` with `genres` as
  options for `genre`. `optionsLimit` defaults to 1, `isRequired` to false; `skip` is normalised.

### 4. URL building (§1.1, §1.3)

`scheme://host[:port]{basePath}/{resource}/{type}/{id}[/{extra}].json[?{originalQuery}]`, where
`basePath` is the manifest path without the **last** `/manifest.json`. `resource`, `type`, `id` and
every extra key and value are encoded with the `encodeURIComponent` set (space = `%20`, never `+`).
Extras are written in the order they are declared in the manifest; multi-value extras repeat the
key; no `/extra` segment when there are no values.

### 5. Discover (browse)

**Visibility**: the Discover destination exists only while at least one addon is enabled. With no
addons, nothing about addons appears on Home.

- Phone: a new bottom-navigation item **Discover** between Home and History.
- TV: a **Discover** item at the top of the Home category rail, above "All channels".

**Discover home**

1. **Continue watching** row (from `media_history`, section 9), if not empty.
2. **Type tabs**: All, then one tab per type present in enabled addons' board catalogues, in the
   order movie, series, tv, channel, then others alphabetically.
3. One **row per board catalogue**, grouped by addon in addon order: catalogues with no required
   extra, plus catalogues whose required extras have `options` (filled with the first option, §2.2).
   Row title: `{catalog name} · {addon name}`. Adult addons are skipped.
4. Rows load lazily as they scroll into view (first page only, `limit` 20 cards shown, "See all" to
   open the catalogue).

**Catalogue screen** (from "See all" or a `stremio:///discover/…` link)

- Genre chips from the `genre` extra's `options` (single select, or multi-select when
  `optionsLimit > 1`); a required genre starts on its first option and cannot be cleared.
- Grid of cards; card shape from `posterShape` (`poster` 2:3, `square`, `landscape` 16:9; unknown →
  poster). Missing poster → placeholder with the name.
- **Paging**: first request without `skip`; next pages `skip = number of items received so far`.
  Stop on an empty page, a page whose ids are all already shown, or a page with fewer than 100 items
  **and** no new ids on the following request (lenient rule, §3.1). Only send `skip` if declared.

**Search**

- A search field on Discover (TV: a focusable search button that opens the on-screen keyboard).
- Minimum 2 characters, 400 ms debounce. Fans out to every catalogue of every enabled, non-adult
  addon that declares a `search` extra (required or not). Results appear progressively, grouped by
  `{catalog name} · {addon name}`. At most 6 parallel requests per host and 16 overall; typing again
  cancels in-flight requests.

### 6. Meta detail

Opened from any card. Request `/meta/{type}/{id}.json` from every addon whose `meta` resource matches,
in addon order; the first non-empty `meta` wins; missing fields may be filled from later answers.
If no addon provides meta, the page is built from the catalogue preview.

- Header: background (else poster) with scrim, poster, name, `releaseInfo`, `runtime`, genres,
  rating as text (`meta_rating` "Rating %1$s", never a brand name), description (expandable).
- **Movie** (or any meta without `videos`): **Play** opens the stream picker for `id` = meta id.
  `behaviorHints.defaultVideoId` opens that video's streams directly.
- **Series**: season selector (chips on phone, a horizontal focusable row on TV); seasons ascending,
  season 0 last as `meta_specials`. Episode list sorted by `episode`: thumbnail, number, title
  (`title ?: name`), released date, overview. Episodes with `released` in the future show
  `meta_upcoming` and are not selectable. Selecting an episode opens the stream picker for its video id.
  The last watched episode is highlighted and scrolled into view.
- **tv** / `behaviorHints.isLive`: **Play** opens the stream picker; playback uses the live player UI.
- `links`: only `stremio:///…` links are handled (genre/discover → catalogue screen, `detail` →
  detail, `search` → search); all other links are not shown. Trailers are not shown.

### 7. Stream picker

Request `/stream/{type}/{videoId}.json` from every addon whose `stream` resource matches, **in
parallel** (total timeout 20 s each). If the chosen video has inline `streams`, use only those and
make no requests. Results appear progressively, grouped by addon in addon order.

| Stream kind (by field presence: `url` > `externalUrl` > others) | Shown | Action |
|---|---|---|
| `url` with `http`/`https` | Yes | Play in TS IPTV |
| `externalUrl` `stremio:///…` | Yes | Internal navigation (6) |
| `externalUrl` `http(s)` | Yes, with an "opens browser" icon | Confirm `stream_open_external` (shows host) → system browser. TV without a browser: show the URL as text. |
| everything else (`infoHash`, `nzbUrl`, archives, `ytId`, `playerFrameUrl`, `url` with another scheme, local-server URLs) | **Hidden by default** | Overflow → **Show unsupported streams** (off by default, not remembered): rows appear greyed with `stream_unsupported`, not selectable, with **no** kind label and no count. |

Row content: `name` (bold, one line), `description ?: title` (up to 3 lines), badges for
`behaviorHints.countryWhitelist` (`stream_badge_region`) and, on iOS, `stream_badge_may_not_play`
for `.mpd`/`.mkv` URLs. Focus/selection starts on the stream from the same addon and
`bingeGroup` as the last one the user played for this title, else the first playable stream.
With no playable stream at all: `stream_none_playable` with **Back**.

**Playback mapping**: `MediaItem(uri = url, headers = behaviorHints.proxyHeaders.request,
mimeType from filename/extension, title, artwork = poster/thumbnail)`. `notWebReady` and
`proxyHeaders.response` are ignored. Movies and episodes use the VOD player UI (seek bar, resume);
`tv` uses the live UI.

### 8. Networking, caching, errors

- One shared Ktor client for addons: redirects followed, gzip accepted, headers as in 1.
- Timeouts: manifest 10 s connect / 15 s total; catalogue, meta 15 s; streams 20 s.
- Cache (in memory, per URL, LRU 20 MiB) honouring `Cache-Control` `max-age`,
  `stale-while-revalidate` and `stale-if-error`, falling back to the body fields `cacheMaxAge`,
  `staleRevalidate`, `staleError`. Defaults when absent: catalogue 1 h, meta 6 h, streams 5 min.
  **Streams are never cached longer than 5 minutes.** Manifests are persisted in Room.
- Any non-2xx, timeout, non-JSON body or missing root key (`metas`, `meta`, `streams`) is **an empty
  result from that addon**, never a screen failure. The screen shows a compact
  `addon_partial_failure` ("%1$d addon(s) did not respond · Retry") when at least one failed.
- Offline: Discover shows `discover_offline` and the Continue watching row from local data.
- Security: transport URLs can contain API keys in the path. They are stored encrypted
  (`SecretCipher`, below), shown only as host in the UI, and never logged, sent to analytics, crash
  reports or TS IPTV's backend. Release builds log addon errors with the host only.

### 9. Watch history and resume

- A play of an addon movie/episode/tv item creates or updates a `media_history` row: source
  (addon id), meta type/id, video id, name, poster, episode label (`S1 · E3 Title`), last
  addon id + `bingeGroup` used, position, duration, updated time. **Stream URLs are not stored**
  (they expire).
- Position is saved every 10 s and on pause/stop. An item counts as finished at ≥ 95 % of duration;
  finished movies leave Continue watching; a finished episode is replaced by the next episode of the
  series (if released) with position 0.
- Continue watching (Discover and History): selecting an item opens its detail page with the episode
  selected and the stream picker open; after a stream is chosen, playback resumes from the saved
  position if it is between 60 s and 95 %.
- History tab: a new section **Movies & series** under the existing channel history, same card style.
  Remove one item (long-press / TV menu key) or clear all (existing clear action covers both).

---

## Data model

### Room: version 4 → 5 (F2)

AutoMigration 4 → 5 (new tables only). Export `5.json`, migration test, update `InMemoryIPTVDatabase`.

**`stremio_addons`**

| Column | Type | Notes |
|---|---|---|
| `addonId` | TEXT PK | manifest `id` |
| `transportUrlEnc` | TEXT NOT NULL | encrypted manifest URL (`SecretCipher`) |
| `transportHost` | TEXT NOT NULL | host (and port) for display |
| `manifestJson` | TEXT NOT NULL | last good manifest |
| `name`, `version` | TEXT NOT NULL | denormalised for lists |
| `logoUrl` | TEXT NULL | |
| `enabled` | INTEGER NOT NULL DEFAULT 1 | |
| `sortOrder` | INTEGER NOT NULL | |
| `isAdult`, `isP2p` | INTEGER NOT NULL DEFAULT 0 | from `behaviorHints` |
| `ownerSourceId` | TEXT NULL | set by F3 for addons that come from a TS IPTV Source include; NULL = added by the user |
| `addedAt`, `lastFetchedAt` | INTEGER NOT NULL | epoch ms |
| `failCount` | INTEGER NOT NULL DEFAULT 0 | consecutive refresh failures |
| `lastError` | TEXT NULL | short code, no URL |
| `blocked` | INTEGER NOT NULL DEFAULT 0 | set by the blocklist |

**`media_history`** (shared with F3)

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK autoincrement | |
| `sourceKind` | TEXT NOT NULL | `STREMIO` (F2), `TSIPTV` (F3) |
| `sourceId` | TEXT NOT NULL | addon id, or TS IPTV Source playlist id |
| `itemType` | TEXT NOT NULL | raw type (`movie`, `series`, `tv`, …) |
| `itemId` | TEXT NOT NULL | meta id |
| `videoId` | TEXT NOT NULL | video id (= item id for movies) |
| `title`, `subtitle`, `posterUrl` | TEXT | display snapshot |
| `season`, `episode` | INTEGER NULL | |
| `lastAddonId`, `lastBingeGroup` | TEXT NULL | stream preference |
| `positionMs`, `durationMs` | INTEGER NOT NULL DEFAULT 0 | |
| `finished` | INTEGER NOT NULL DEFAULT 0 | |
| `updatedAt` | INTEGER NOT NULL | |
| unique index | | (`sourceKind`, `sourceId`, `itemId`, `videoId`) |

No foreign key to `playlists` (addon content is not a playlist). Deleting an addon deletes its
`media_history` rows.

### Other

- `core/security/SecretCipher` (`expect/actual`): AES-GCM with a key in Android Keystore / iOS
  Keychain; desktop stores the key in the app data directory with owner-only file permissions.
- DTOs (`kotlinx.serialization`, `ignoreUnknownKeys`, `isLenient`, `coerceInputValues`,
  `explicitNulls = false`): manifest, catalog, extra (with legacy fallback), meta, video
  (`title ?: name`), stream (`description ?: title`, source kind by field presence, unknown kinds
  kept as `Unsupported`), behaviour hints; custom serializers for `resources` (string | object) and
  number-or-string fields (`year`, `imdbRating`, `season`, `episode`).
- Types, `posterShape` and link categories are raw strings (no enums).

---

## UI

### Phone

- **Profile → Addons** list (section 2) with a floating **Add addon** button. Empty state:
  `addons_empty` + **Add addon**. No suggestions of any kind.
- **Add addon** sheet: URL field (paste button), **Continue** → preview sheet → notice → saved,
  snackbar `addon_added`.
- **Discover** tab (section 5), catalogue screen, search, detail, stream picker as a bottom sheet.
- Player: existing player; VOD items show the seek bar; the top bar shows `name · S1E3`.

### TV (D-pad)

- **Settings → Addons**: vertical list; each row focusable; ◀/▶ on a focused row toggles enabled;
  **OK** opens a row menu (Move up, Move down, Update now, Configure, Remove). Add addon opens an
  input dialog with the on-screen keyboard; focus order: field → Continue → Cancel.
- **Discover** in the rail: content area shows type tabs (a focusable row), then catalogue rows as
  TV rows (cards scale 1.08 and show the accent focus ring). ▲/▼ moves between rows and keeps the
  horizontal position per row; ◀ from the first card returns to the rail. Initial focus: first card
  of Continue watching, else the first row.
- **Detail**: initial focus on **Play** (movie/tv) or on the last watched episode (series). Season
  row above the episode list; ▼ from the season row enters the episodes. Back returns to the card
  that opened the detail.
- **Stream picker**: full-height side panel on the right; initial focus as in section 7; the
  overflow is a focusable **More** button at the bottom containing "Show unsupported streams".
- `externalUrl` on TV: if no browser can handle `ACTION_VIEW`, show `stream_external_tv` with the
  URL as selectable text.
- Every screen: Back always goes back; no dead ends; visible focus on every focusable element.

### iOS and desktop

As phone. Desktop uses mouse and keyboard (arrow keys follow the TV focus rules). iOS shows
`stream_badge_may_not_play` for DASH and MKV.

## Error states

| Situation | Message key |
|---|---|
| Not a manifest URL | `addon_error_not_manifest` |
| Legacy `/stremio/v1` addon | `addon_error_legacy` |
| Stremio local server address | `addon_error_local_server` |
| Manifest missing required fields / not JSON | `addon_error_invalid_manifest` |
| Manifest unreachable (on add) | `addon_error_unreachable` + Retry |
| Needs configuration | `addon_needs_configuration` |
| Blocklisted | `addon_blocked` |
| Some addons failed on a screen | `addon_partial_failure` + Retry |
| All addons failed / empty catalogue | `discover_empty_catalog` |
| No search results | `discover_no_results` |
| Offline | `discover_offline` |
| Meta not found anywhere | detail built from preview; if no preview, `meta_not_found` |
| No playable stream | `stream_none_playable` |
| Playback 401/403 | F1's `stream_error_forbidden_headers` |

## Strings

All seven locales (en, vi, de, es, fr, ja, zh-CN). English and Vietnamese given.

| Key | English | Vietnamese |
|---|---|---|
| `addons_title` | Addons | Addon |
| `addons_empty` | No addons yet. If you run or use a Stremio-compatible addon, add it with its manifest link. | Chưa có addon nào. Nếu bạn tự chạy hoặc đang dùng một addon tương thích Stremio, hãy thêm nó bằng đường dẫn manifest. |
| `addon_add` | Add addon | Thêm addon |
| `addon_url_label` | Manifest link (…/manifest.json) | Đường dẫn manifest (…/manifest.json) |
| `addon_notice` | Addons are third-party services, not operated by TS IPTV. You are responsible for having the rights to anything you watch through them. | Addon là dịch vụ của bên thứ ba, không do TS IPTV vận hành. Bạn chịu trách nhiệm về quyền sử dụng mọi nội dung bạn xem qua chúng. |
| `addon_added_by_you` | Added by you | Do bạn thêm |
| `addon_added` | Addon added | Đã thêm addon |
| `addon_warn_http` | This addon does not use an encrypted connection. | Addon này không dùng kết nối mã hoá. |
| `addon_warn_p2p` | This addon says it uses peer-to-peer streams. TS IPTV does not play those, so much of it may not work. | Addon này cho biết nó dùng luồng ngang hàng (P2P). TS IPTV không phát loại luồng này nên phần lớn nội dung có thể không hoạt động. |
| `addon_warn_adult` | This addon contains adult content. | Addon này có nội dung người lớn. |
| `addon_confirm_adult` | I am 18 or older | Tôi đủ 18 tuổi trở lên |
| `addon_error_not_manifest` | Enter the addon's manifest link. It ends with /manifest.json. | Hãy nhập đường dẫn manifest của addon. Đường dẫn kết thúc bằng /manifest.json. |
| `addon_error_legacy` | This is an old-style addon that TS IPTV cannot use. | Đây là addon kiểu cũ, TS IPTV không dùng được. |
| `addon_error_local_server` | This address belongs to the Stremio app's own local server and cannot be used in TS IPTV. | Địa chỉ này thuộc máy chủ cục bộ của ứng dụng Stremio, không dùng được trong TS IPTV. |
| `addon_error_invalid_manifest` | This link did not return a valid addon manifest. | Đường dẫn này không trả về manifest addon hợp lệ. |
| `addon_error_unreachable` | Could not reach this addon. Check the link and your connection. | Không kết nối được addon này. Hãy kiểm tra đường dẫn và kết nối mạng. |
| `addon_needs_configuration` | This addon must be configured first. Open its configuration page, then paste the link it gives you. | Addon này cần được cấu hình trước. Hãy mở trang cấu hình rồi dán đường dẫn nó cung cấp. |
| `addon_configure` | Configure | Cấu hình |
| `addon_replace_existing` | Replace the existing addon "%1$s"? | Thay thế addon "%1$s" hiện có? |
| `addon_blocked` | This addon has been blocked in TS IPTV. | Addon này đã bị chặn trong TS IPTV. |
| `addon_updated` | Updated to version %1$s | Đã cập nhật lên phiên bản %1$s |
| `addon_update_now` | Update now | Cập nhật ngay |
| `addon_remove_confirm` | Remove "%1$s"? Its watch history will also be removed. | Gỡ "%1$s"? Lịch sử xem của addon cũng sẽ bị xoá. |
| `addon_move_up` / `addon_move_down` | Move up / Move down | Chuyển lên / Chuyển xuống |
| `addon_status_ok` | Working | Đang hoạt động |
| `addon_status_unreachable` | Not responding — using the saved copy | Không phản hồi — đang dùng bản đã lưu |
| `addon_status_blocked` | Blocked | Đã bị chặn |
| `addon_partial_failure` | %1$d addon(s) did not respond | %1$d addon không phản hồi |
| `discover_title` | Discover | Khám phá |
| `discover_search_hint` | Search your addons | Tìm trong addon của bạn |
| `discover_no_results` | No results | Không có kết quả |
| `discover_empty_catalog` | Nothing to show here | Không có gì để hiển thị |
| `discover_offline` | You are offline | Bạn đang ngoại tuyến |
| `discover_see_all` | See all | Xem tất cả |
| `discover_type_all` | All | Tất cả |
| `type_movie` / `type_series` / `type_tv` / `type_channel` | Movies / Series / Live TV / Channels | Phim lẻ / Phim bộ / Truyền hình / Kênh |
| `continue_watching_media` | Continue watching | Xem tiếp |
| `history_media_section` | Movies & series | Phim lẻ và phim bộ |
| `meta_play` | Play | Phát |
| `meta_rating` | Rating %1$s | Đánh giá %1$s |
| `meta_season` | Season %1$d | Mùa %1$d |
| `meta_specials` | Specials | Tập đặc biệt |
| `meta_upcoming` | Upcoming | Sắp ra mắt |
| `meta_not_found` | This title could not be loaded. | Không tải được nội dung này. |
| `stream_picker_title` | Choose a stream | Chọn luồng phát |
| `stream_show_unsupported` | Show unsupported streams | Hiện luồng không hỗ trợ |
| `stream_unsupported` | Not playable in TS IPTV | Không phát được trong TS IPTV |
| `stream_none_playable` | No playable stream was found for this title. | Không tìm thấy luồng phát được cho nội dung này. |
| `stream_open_external` | Open %1$s in your browser? | Mở %1$s trong trình duyệt? |
| `stream_external_tv` | Open this link on another device: | Hãy mở đường dẫn này trên thiết bị khác: |
| `stream_badge_region` | Region-limited | Giới hạn khu vực |
| `stream_badge_may_not_play` | May not play on this device | Có thể không phát được trên thiết bị này |

## Acceptance criteria

Fixture: TS IPTV's own static **Public Domain Sampler** addon (research §5.2), hosted by F4 at
`https://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json`, plus a local SDK instance of
the same addon (research §5.1, `http://10.0.2.2:7000/manifest.json` on the Android emulator) whose
movie catalogue is padded with 250 generated items pointing at the same public-domain MP4, for
search and paging. The QC harness lives in `qa/stremio-fixture/` and is never shipped.
Official addons may be used by QC as protocol fixtures only; they are never referenced in code.

- **AC-S1** A fresh install has no addons, no Discover tab/rail item, and the production source sets
  (`commonMain`, `androidMain`, `iosMain`, `desktopMain`) contain no third-party addon URL (grep for
  `strem.io`, `strem.fun`, `stremio.net`, `beamup`). Test fixtures may contain them.
- **AC-S2** Pasting `stremio://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json` opens the
  preview for the `https://` URL; confirming the notice adds it and Discover appears.
- **AC-S3** `https://example.com/foo`, a `/stremio/v1` URL and `http://127.0.0.1:11470/local-addon/manifest.json`
  show `addon_error_not_manifest`, `addon_error_legacy` and `addon_error_local_server` respectively.
- **AC-S4** A manifest missing `resources` shows `addon_error_invalid_manifest`; a manifest with
  `configurationRequired: true` is not added and shows the configure flow.
- **AC-S5** A manifest with `p2p: true` shows `addon_warn_p2p`; with `adult: true` it cannot be added
  without the 18+ confirmation, and its catalogues do not appear in Discover rows or search.
- **AC-S6** Adding a second manifest with the same `id` asks to replace; after replacing there is one
  entry in the same position.
- **AC-S7** Disable hides the addon's rows from Discover and its streams from the picker; re-enable
  restores them. Move up/down changes row order on Discover and group order in the picker. Remove
  deletes the addon and its `media_history` rows. Removing the last addon hides Discover.
- **AC-S8** URL encoding: a unit test reproduces the research examples exactly —
  `…/catalog/movie/top/genre=Comedy&skip=100.json`, `…/search=big%20buck%20bunny.json`, and
  `tt0108778:1:1` → `tt0108778%3A1%3A1`; a manifest URL with a query string keeps it after `.json`.
- **AC-S9** Matching unit tests against the Cinemeta manifest fixture (research §2.3): home rows
  include `top` (movie, series) and `year` with `genre` = first option; `last-videos` is excluded;
  search goes to catalogues declaring `search`; the object-form `resources` with no `types` matches
  nothing.
- **AC-S10** Catalogue paging against the local SDK addon with 250 items: first request has no `skip`,
  then `skip=100`, `skip=200`, and paging stops after the 50-item page; no duplicate cards.
- **AC-S11** Genre chips come from `extra.options`; selecting "Comedy" requests `genre=Comedy`;
  with `optionsLimit: 2` two chips can be selected and the request repeats the key.
- **AC-S12** Search "his" returns His Girl Friday from the local addon; typing more cancels the
  previous request (verified in logs); a timeout of one addon shows `addon_partial_failure` while
  results from others are shown.
- **AC-S13** Series detail for `tspd_superman` shows Season 1 with "The Mechanical Monsters"; a
  fixture with season 0 lists it last as Specials; a future `released` episode shows Upcoming and
  cannot be selected.
- **AC-S14** Stream picker for His Girl Friday plays the archive.org MP4 on Android, iOS and desktop.
- **AC-S15** The `tv` sampler item plays the Mux HLS stream and the proxy log shows
  `User-Agent: TSIPTV-Example/1.0` from `proxyHeaders.request` on playlist and segment requests.
- **AC-S16** A fixture addon returning one `url`, one `infoHash`, one `ytId` and one `externalUrl`
  stream shows exactly two rows by default (url, externalUrl) and no count or word like "torrent";
  **Show unsupported streams** adds two greyed, non-selectable rows labelled `stream_unsupported`.
- **AC-S17** Selecting the `externalUrl` stream asks for confirmation showing the host and opens the
  browser (phone); on the TV emulator it shows the URL text.
- **AC-S18** Inline `video.streams` are used without any `/stream/` request.
- **AC-S19** Caching: a second open of the same catalogue within `max-age` makes no request; stream
  responses are re-requested after 5 minutes even if the addon says `cacheMaxAge: 604800`.
- **AC-S20** Watching 2 minutes of a movie, leaving and returning shows it in Continue watching
  (Discover and History); selecting it and the same stream resumes within ±5 s of the saved position.
  Watching ≥ 95 % removes it; finishing an episode offers the next episode.
- **AC-S21** No transport URL appears in logcat (release build), Firebase Analytics debug view or
  crash reports; the Room `stremio_addons.transportUrlEnc` column does not contain the plain URL.
- **AC-S22** Adding a blocklisted host (blocklist fixture served locally) shows `addon_blocked`; an
  installed addon added to the blocklist is disabled on the next daily check.
- **AC-S23** TV: every Discover, detail, picker and addon-manager element is reachable by D-pad, has
  visible focus, and Back returns to the element that opened the screen.
- **AC-S24** Upgrade from the F1 build keeps all playlists, history and favourites (migration 4 → 5
  test in CI plus a manual upgrade).
- **AC-S25** All new strings exist in all seven locales.

## Implementation plan (ordered)

1. **Protocol core** (commonMain, no UI) — `core/stremio/StremioModels.kt` (DTOs + serializers),
   `StremioUrl.kt` (transport → base, encoding), `ResourceMatcher.kt`, `StremioClient.kt` (Ktor,
   timeouts, cache, error mapping), `StreamClassifier.kt` (kind by field presence). Tests with
   recorded fixtures in `commonTest/kotlin/assests/stremio/` (Cinemeta manifest and series meta,
   sampler files, a mixed-kind stream response).
2. **Persistence** — `core/database/entity/` (`StremioAddonEntity`, `MediaHistoryEntity`), DAOs,
   `AppDatabase` version 5 with AutoMigration 4 → 5, `5.json`, migration test;
   `core/security/SecretCipher` expect/actual; `InMemoryIPTVDatabase`.
3. **Repository** — `core/stremio/AddonRepository.kt` (add/normalise/validate, order, enable,
   refresh daily, blocklist check, fan-out aggregation for catalog/meta/stream, per-addon errors).
   Koin module registration next to the existing modules.
4. **Addon manager UI** — phone `ui/screens/addons/` (list, add sheet, preview), Profile entry in
   `ui/screens/profile/ProfileScreen.kt`; TV entry in `ui/tv/TvSettingsDialog.kt` and a TV addon screen.
5. **Discover** — `ui/screens/discover/` (home rows, type tabs, catalogue grid with paging, search);
   phone bottom nav item (conditional) in `ui/screens/home/HomeBottomNavigationScreen.kt` /
   `models/BottomNavItem.kt`; TV rail item in `ui/tv/TvHomeScreen.kt`; routes in
   `navigation/NavRoutes.kt` (`Discover`, `AddonCatalog`, `MediaDetail`, `Addons`).
6. **Detail and stream picker** — `ui/screens/mediadetail/` (movie/series/tv, seasons, episodes),
   stream picker sheet/panel, external URL handling, `stremio:///` routing.
7. **Playback** — map stream → `MediaItem` with headers (F1 path); VOD player UI for movies/episodes
   in `ui/screens/player/PlayerScreen.kt` and `ui/tv/TvPlayerScreen.kt`; progress saving to
   `media_history` from `PlayerViewModel`.
8. **History** — Continue watching row, History tab section in `ui/screens/history/HistoryScreen.kt`.
9. **Blocklist** — F4 publishes `web/public/policy/addon-blocklist.json` (`{"ids":[],"hosts":[]}`);
   app fetch + daily check.
10. **Strings** in seven locales; key-set test.
