# PRD F3 — TS IPTV Source: our own open source format

Status: **Ready for development after F2, 2026-09-27** · Owner: PO · Platforms: Android (phone + TV), iOS, Desktop
Normative format spec: [`tsiptv-source-format.md`](tsiptv-source-format.md) ("the spec"; section numbers "§7.2" refer to it)
Schema: `web/public/schema/tsiptv-source-v1.json` · Examples: `web/public/examples/*.tsiptv.json`
Depends on: F1 (headers, DRM, catch-up models, file import, `PlaylistImporter`), F2 (Stremio client,
media detail screens, `media_history`). Roadmap: [`roadmap-sources-and-formats.md`](roadmap-sources-and-formats.md)

---

## Problem

People who curate streams for others — a family sharing their home server, a school or community
channel, a small licensed operator, a public-domain film archive — can only hand TS IPTV users an
M3U file. M3U cannot describe movies versus series versus episodes, cannot carry a home-screen
layout or branding, cannot localize titles, and expresses headers, DRM and catch-up through a
patchwork of Kodi and VLC conventions. Combining sources (a live list, a guide, an addon, a VOD
catalogue) means asking users to add four things separately.

TS IPTV also lacks a format of its own that is **fully documented and open**, which matters for
the store review story ("we define and document formats; users bring content") and contrasts with
undocumented app-specific formats.

## Goals

1. Creators can describe a complete branded collection — metadata, appearance, home layout, live TV,
   radio, movies, series, EPG and remote includes — in one hand-editable JSON file (spec).
2. Users add it by link or file, see exactly what it contains and which servers it contacts, and
   get a home screen laid out the way the creator intended, on phone and TV.
3. Invalid content never crashes or blanks the app: document errors are explained, item errors are
   skipped and listed.
4. Includes stay fresh without re-importing, and keep working from the last good copy when a
   remote is down.
5. The format is public: spec, JSON Schema and examples are published by F4.

## Non-goals

- **No directory, gallery or search of TS IPTV Sources**, in the app or on the website. No default
  sources. The website shows only TS IPTV's own examples, which contain public test streams and
  public-domain films.
- An in-app editor or a hosted builder. (The website gets a schema-based validator, F4.)
- Scripts, expressions, HTML, Markdown, custom fonts, light theme, per-item colours.
- Banners or links that leave the app (no URL actions). Ads, monetisation or tracking fields.
- Sign-in flows inside a source; per-user entitlements. (Tokens in URLs and headers work as in M3U.)
- Catch-up playback (F1b), subtitles for TS IPTV Source movies beyond side-loaded VTT/SRT (below),
  digit-key channel entry.
- Global search across sources and addons.

---

## Behaviour

### 1. Import

- **By link**: the existing "Add by link" field. Detection (F1 §1) recognises a TS IPTV Source by
  content; the URL extension is irrelevant.
- **By file**: F1's **Import from file**, same detection.
- **Deep links** (`tsiptv://`) are not part of this release.

Import steps:

1. Download (max 5 MiB after decompression; gzip accepted) or read the file.
2. Parse and validate the root document (spec §10). A document error stops here (section 3).
3. **Preview** screen (nothing is stored yet):
   - logo, name, author, description (resolved to the UI language), `updatedAt`;
   - counts: TV channels, radio, movies, series, episodes, includes;
   - **"This source contacts these servers"**: the distinct hosts of the root URL and every include
     URL (streams and images are not listed — too many);
   - warnings count with **Details** (item errors and warnings from step 2);
   - root `meta.adult: true` (or present and not a boolean) → `source_warn_adult` and a required
     "I am 18 or older" checkbox;
   - the notice `source_notice`;
   - **Import** / **Cancel**.
4. On **Import**: fetch includes (section 5) with a progress line (`source_fetching_includes`
   "Loading 2 of 4…"), build the pools, store everything in one transaction, make it the active
   playlist and open its home.
   **Adult content through includes** (spec §9.3): after the include tree is fetched and before
   anything is stored, if any included TS IPTV Source has `meta.adult: true` (or a non-boolean
   value) or any `stremio` include's manifest has `behaviorHints.adult: true`, and the user has not
   already confirmed in step 3, show `source_warn_adult_include` with the "I am 18 or older"
   checkbox and **Import** / **Cancel**. Cancel stores nothing. On a later refresh that makes an
   unconfirmed source adult, the new content of the responsible include(s) is not merged (the
   previous copy stays; with none they count as failed) and "About this source" shows the warning
   with a **Confirm** action; confirming merges it on the next refresh.
5. If every include failed **and** the root has no items of its own, nothing is stored and
   `source_error_nothing_loaded` is shown.

**Updates**: a document whose `id` matches an installed source from the **same URL** is a refresh.
From a **different URL** or file: `source_replace_existing` ("Replace 'Name'?"). Replacing keeps the
playlist id, favourites and watch history of items whose ids still exist.

### 2. Storage model (summary; details in Data model)

A TS IPTV Source is stored as a **playlist** (`format = TSIPTV_SOURCE`) so that switching,
refreshing, deleting, EPG, favourites and channel history keep working. Live and radio channels go
into the existing `channel` table (F1 columns) with namespaced ids. Movies, series and episodes go
into new VOD tables. The document's `meta`, `appearance` and `layout` are stored as JSON and
resolved at display time (the UI language can change at runtime).

### 3. Validation errors shown to users

| Level (spec §10) | What the user sees |
|---|---|
| Document error | Dialog `source_error_title` with a friendly message per code (table below) and a **Details** action showing the code and JSON path (for creators). Nothing is stored. |
| Item errors / warnings at import | Preview shows "N items will be skipped" with **Details**; after import, the success message includes the count. |
| Item errors / warnings on refresh | Silent, but "About this source" shows the count and the details of the last refresh. |

| Code | Message key | English text |
|---|---|---|
| `E_NOT_JSON` | `source_error_not_json` | This file is not valid JSON. |
| `E_NOT_SOURCE` | `source_error_not_source` | This is not a TS IPTV Source file. |
| `E_VERSION` | `source_error_version` | This source needs a newer version of TS IPTV. |
| `E_TOO_LARGE` | `source_error_too_large` | This source is larger than 5 MB. |
| `E_ID`, `E_META` | `source_error_missing_fields` | This source is missing its id or name. |
| `E_EMPTY` | `source_error_empty` | This source has nothing that TS IPTV can show. |

The **Details** list shows at most 100 entries, each as `path — message`, for example
`channels[3].url — must start with http:// or https://` (message keys `source_issue_*`, one per
code in spec §10). It is selectable/copyable on phone and desktop.

### 4. Home rendering

When the active playlist is a TS IPTV Source, **Source Home** replaces the Home feed:

- Phone: the Home tab shows a two-segment switch at the top — **Home** (the source layout) and
  **Channels** (the existing channel feed with group chips, for this source's channels). Default:
  Home. The switch is hidden when the source has no channels.
- TV: the category rail gets a **Home** item at the top (the layout, initial selection), followed
  by the existing "All channels" and groups.

**Layout** = `layout.home` or the default layout (spec §7.5). Sections that resolve to no items
are not rendered. Sections with unknown `type`/`from` were already dropped at parse time.

| Section | Phone | TV (D-pad) |
|---|---|---|
| `hero` | Full-width 16:9 pager, page dots, auto-advance every 6 s, paused while touched and when system animations are off. Title/subtitle over a bottom scrim. Tap → target detail (or play if `autoplay`). | 16:9 banner, height ≤ 45 % of the screen; ◀/▶ change banner; OK opens/plays the target; auto-advance only while not focused. Decorative banners (no target) are skipped by focus. |
| `row` | Title, optional subtitle, **See all** (unless `seeAll: false`), horizontal list of cards (`limit`, default 20). | TV row; focused card scales 1.08 with an accent focus ring; ◀ at the first card goes to the rail; the row remembers its horizontal position. **See all** is the last focusable card. |
| `grid` | Adaptive columns: poster ≥ 110 dp, landscape ≥ 170 dp, square/logo ≥ 96 dp, `list` one column. Paged loading of 60 items. `groupChips` → chip row above. | Columns: poster 6, landscape 4, square/logo 6, list 2. `groupChips` → a focusable chip row above the grid (the rail is already used). |

**Cards** (style from section `card` → `appearance.card` → `auto` rules, spec §6.1):
`poster` 2:3 image + title; `landscape` 16:9 + title (+ progress bar for Continue watching);
`square` 1:1; `logo` channel logo on a tile + name + channel number; `list` logo, name and the
current programme from the guide. Missing image → placeholder tile with the name's initials.
`corner` sets the corner radius; `showTitles: false` hides titles except for channels and lists.

**Selecting an item**: TV/radio channel → player (as today, with zapping across the section's
channels); movie → detail page; series → detail page with seasons and episodes; episode (hero
target) → series detail with that episode selected (or play if `autoplay`); Stremio catalog item →
F2 detail page backed by that include's addon only.

**Detail pages** reuse F2's media detail and stream picker screens with a TS IPTV Source data
provider: `streams` in order, `name`/`quality`/`language` as the row labels, first playable stream
preselected. Subtitles (`vtt`/`srt`) are offered as side-loaded tracks on Android (Media3
`SubtitleConfiguration`) and desktop; on iOS they are not shown in this release.

**Appearance** applies to Source Home, See all grids and detail pages only: background (colour,
gradient, image with scrim), accent (focus rings, chips, buttons, progress), corner radius. The
contrast rules of spec §6 are applied (`W_CONTRAST`). The player, dialogs and settings keep the app
theme.

**Search**: a search action on Source Home searches names of this source's channels, radio,
movies and series (local, case- and accent-insensitive). Stremio catalogues are not searched.

**Continue watching / favourites**: `continueWatching` combines `media_history` rows of this source
(movies, episodes) and `channel_history` for its channels, most recent first. `favorites` shows its
favourite channels.

### 5. Includes: fetch and refresh

| Aspect | Rule |
|---|---|
| When | At import (all), then each include when older than its `refreshHours` at the moment its source becomes active, plus **Refresh** (all, ignoring `refreshHours`). The root document refreshes like playlists (older than 24 h when opened, or Refresh). |
| How | Up to 4 includes in parallel; conditional GET with `If-None-Match`/`If-Modified-Since` when an `ETag`/`Last-Modified` was received; `headers` of the include sent on the request. Timeouts 15 s connect, 60 s total (M3U and XMLTV can be large). |
| Limits | Spec §9.3: depth 3, 20 includes, 50 MiB per refresh; M3U include ≤ 20 MiB, XMLTV ≤ 100 MiB decompressed, TS IPTV Source ≤ 5 MiB. |
| `m3u` | Parsed with the F1 M3U parser (headers, DRM, catch-up, groups). Its `x-tvg-url` guides join the EPG set. |
| `xmltv` | Joined to the EPG set; guide data refreshed on the include's schedule. |
| `stremio` | Registered in `stremio_addons` with `ownerSourceId` = this playlist (enabled). Not shown on Discover; listed in the Addon manager under `addons_from_sources` (read-only: can be disabled, not removed individually). Removed with the source. Catalogue and stream requests follow F2 (cache, timeouts, error handling). The F2 notice is covered by the source's import notice. |
| `tsiptv-source` | Parsed and validated like the root; contributes content only; its own includes are followed within the depth limit; cycles skipped. |
| Failure | Keep the last good copy and show it. First fetch failed → sections using the include are hidden. "About this source" lists each include with status (`source_include_ok`, `source_include_stale` + last success date, `source_include_failed`). |
| Refresh atomicity | A refresh replaces an include's items only when the new copy parsed; the swap happens in one transaction so the UI never sees a half-updated source. |

### 6. About this source

Reachable from Source Home (phone: overflow menu; TV: last rail item "About"): logo, name, author,
description, homepage link (confirm `source_open_homepage` before opening the browser; TV shows the
URL text), languages, `updatedAt`, `revision`, root URL host, include list with status, last refresh
time, validation details of the last refresh, **Refresh**, **Remove source**.

### 7. Performance

A document with 10,000 channels, 5,000 movies and 1,000 series (20,000 episodes) — generated test
fixture — imports in under **15 s** on the reference device (Pixel 10a emulator / Android TV
emulator) and Source Home shows its first sections within **1 s** of opening. Parsing uses streaming
or incremental decoding if needed to stay within memory on 2 GB TV devices.

---

## Data model

### Parser layer (`core/parser/tsiptv/`)

- `TsiptvSourceParser`: JSON → `TsiptvSourceDocument` (typed tree mirroring the spec; localized
  values kept as `Map<String, String>`), plus `ValidationReport(errors, warnings)` with spec codes and
  JSON paths. Hand-written validation per spec §10 (the JSON Schema is not used at runtime).
- `TsiptvSourceResolver`: fetches includes (tree, limits, cycles, conditional GET), parses each
  with the right parser, namespaces ids (`includeId:itemId`, chained), and produces the pools.
- `LocalizedTextResolver`: spec §5.2 resolution order; shared with F2 where useful.
- `IPTVFormat.TSIPTV_SOURCE` (reserved in F1) and detection before the generic JSON branch.

### Room: version 5 → 6

AutoMigration 5 → 6 (new tables + nullable columns). Export `6.json`; migration test; update
`InMemoryIPTVDatabase`.

**`tsiptv_sources`** — one row per imported source

| Column | Type | Notes |
|---|---|---|
| `playlistId` | TEXT PK, FK → `playlists.id` ON DELETE CASCADE | |
| `sourceId` | TEXT NOT NULL | document `id` (unique index) |
| `revision` | INTEGER NOT NULL DEFAULT 0 | |
| `rootUrl` | TEXT NULL | NULL for file imports |
| `metaJson`, `appearanceJson`, `layoutJson` | TEXT | `layoutJson` NULL = default layout |
| `etag`, `lastModified` | TEXT NULL | root conditional GET |
| `fetchedAt` | INTEGER NOT NULL | |
| `reportJson` | TEXT NULL | last validation report (max 100 entries) |
| `adultConfirmed` | INTEGER NOT NULL DEFAULT 0 | |

**`tsiptv_includes`**

| Column | Type | Notes |
|---|---|---|
| `playlistId` | TEXT, FK cascade | |
| `includePath` | TEXT | chained id, e.g. `cinema` or `cinema:partner` |
| PK | (`playlistId`, `includePath`) | |
| `type`, `url` | TEXT NOT NULL | |
| `refreshHours` | INTEGER NOT NULL | |
| `etag`, `lastModified` | TEXT NULL | |
| `lastSuccessAt`, `lastAttemptAt` | INTEGER NULL | |
| `status` | TEXT NOT NULL | `OK`, `STALE`, `FAILED` |
| `lastError` | TEXT NULL | code only, no URL |

**`vod_items`** — movies and series

| Column | Type | Notes |
|---|---|---|
| `rowId` | TEXT PK | `{playlistId}|{itemId}` |
| `playlistId` | TEXT NOT NULL, FK cascade | index (`playlistId`, `kind`, `sortIndex`) |
| `itemId` | TEXT NOT NULL | namespaced id |
| `originIncludePath` | TEXT NULL | NULL = root document |
| `kind` | TEXT NOT NULL | `MOVIE`, `SERIES` |
| `sortIndex` | INTEGER NOT NULL | document order (includes after own items) |
| `nameJson`, `descriptionJson` | TEXT | localized objects |
| `originalName`, `posterUrl`, `backdropUrl`, `logoUrl`, `ageRating`, `releaseDate` | TEXT NULL | |
| `year`, `endYear`, `runtimeMinutes` | INTEGER NULL | |
| `genresJson`, `castJson`, `directorsJson`, `countriesJson`, `languagesJson`, `tagsJson` | TEXT NULL | |
| `streamsJson`, `subtitlesJson` | TEXT NULL | movies only; streams with merged headers/DRM defaults |

**`vod_episodes`**

| Column | Type | Notes |
|---|---|---|
| `rowId` | TEXT PK | `{playlistId}|{episodeId}` |
| `playlistId` | TEXT NOT NULL, FK cascade | index (`playlistId`, `seriesItemId`, `seasonNumber`, `episodeNumber`) |
| `seriesItemId`, `episodeId` | TEXT NOT NULL | namespaced |
| `seasonNumber` | INTEGER NOT NULL | |
| `seasonNameJson`, `seasonPosterUrl`, `seasonDescriptionJson` | TEXT NULL | |
| `episodeNumber` | INTEGER NOT NULL | |
| `nameJson`, `descriptionJson`, `thumbnailUrl`, `releaseDate` | TEXT | |
| `runtimeMinutes` | INTEGER NULL | |
| `streamsJson`, `subtitlesJson` | TEXT | |

**`channel`** (new nullable columns): `nameJson` (localized name; `name` keeps the resolved default
for search and existing code), `originIncludePath`, `tagsJson`, `descriptionJson`. Source channel
ids are stored as `ts:{playlistId}:{itemId}` so they never collide with other playlists (roadmap
risk R1). `epgId` is the document's `epgId`.

**`media_history`** (from F2): `sourceKind = TSIPTV`, `sourceId = playlistId`, `itemId` = movie or
series id, `videoId` = movie id or episode id.

---

## UI summary per platform

| Screen | Phone | TV | iOS / desktop |
|---|---|---|---|
| Import preview | Full-screen sheet; **Import** primary, **Cancel** secondary; Details opens a list | Full-screen dialog; initial focus **Import**; ▼ reaches Details; Back = Cancel | As phone |
| Source Home | Section 4 | Section 4 (rail **Home** item) | As phone; desktop arrow keys follow TV focus rules |
| See all | Grid screen with the section title | Grid with focus restore | As phone |
| Movie/series detail | F2 detail with appearance applied | F2 TV detail | As phone |
| About this source | Screen from overflow | Rail item "About" | As phone |
| Switch playlist sheet | Sources listed with a distinct icon and their logo | TV settings playlist list, same | As phone |

## Strings

All seven locales (en, vi, de, es, fr, ja, zh-CN). English and Vietnamese given. `source_issue_*`
(one per spec code of §10: **22 keys** — 7 document, 9 item, 6 warning) are short technical
sentences; the English text is the "Condition" column of spec §10, rewritten as a sentence. The
`source_error_*` keys above are the friendly document-error messages; `E_ID` and `E_META` share
`source_error_missing_fields`.

| Key | English | Vietnamese |
|---|---|---|
| `source_preview_title` | Add this source? | Thêm nguồn này? |
| `source_by_author` | by %1$s | của %1$s |
| `source_counts` | %1$d channels · %2$d radio · %3$d movies · %4$d series | %1$d kênh · %2$d radio · %3$d phim lẻ · %4$d phim bộ |
| `source_hosts_title` | This source contacts these servers | Nguồn này kết nối tới các máy chủ sau |
| `source_notice` | This source was made by a third party, not by TS IPTV. You are responsible for having the rights to what you watch. | Nguồn này do bên thứ ba tạo, không phải TS IPTV. Bạn chịu trách nhiệm về quyền sử dụng nội dung bạn xem. |
| `source_warn_adult` | This source says it contains adult content. | Nguồn này cho biết có nội dung người lớn. |
| `source_warn_adult_include` | Part of this source's content comes from a source or addon that says it contains adult content. | Một phần nội dung của nguồn này đến từ nguồn hoặc addon có nội dung người lớn. |
| `source_import` | Import | Nhập |
| `source_items_skipped` | %1$d items will be skipped | %1$d mục sẽ bị bỏ qua |
| `source_fetching_includes` | Loading %1$d of %2$d… | Đang tải %1$d trên %2$d… |
| `source_error_title` | Can't add this source | Không thể thêm nguồn này |
| `source_error_not_json` | This file is not valid JSON. | Tệp này không phải JSON hợp lệ. |
| `source_error_not_source` | This is not a TS IPTV Source file. | Đây không phải tệp TS IPTV Source. |
| `source_error_version` | This source needs a newer version of TS IPTV. | Nguồn này cần phiên bản TS IPTV mới hơn. |
| `source_error_too_large` | This source is larger than 5 MB. | Nguồn này lớn hơn 5 MB. |
| `source_error_missing_fields` | This source is missing its id or name. | Nguồn này thiếu id hoặc tên. |
| `source_error_empty` | This source has nothing that TS IPTV can show. | Nguồn này không có nội dung nào TS IPTV hiển thị được. |
| `source_error_nothing_loaded` | None of this source's content could be loaded. Try again later. | Không tải được nội dung nào của nguồn này. Hãy thử lại sau. |
| `source_replace_existing` | Replace the source "%1$s"? | Thay thế nguồn "%1$s"? |
| `source_home_tab` / `source_channels_tab` | Home / Channels | Trang chủ / Kênh |
| `source_about` | About this source | Giới thiệu nguồn |
| `source_refresh` | Refresh | Làm mới |
| `source_remove` | Remove source | Gỡ nguồn |
| `source_open_homepage` | Open %1$s in your browser? | Mở %1$s trong trình duyệt? |
| `source_include_ok` | Up to date | Đã cập nhật |
| `source_include_stale` | Couldn't refresh — showing the copy from %1$s | Không làm mới được — đang hiện bản từ %1$s |
| `source_include_failed` | Couldn't load | Không tải được |
| `source_section_continue` / `_movies` / `_series` / `_radio` / `_channels` | Continue watching / Movies / Series / Radio / Channels | Xem tiếp / Phim lẻ / Phim bộ / Radio / Kênh |
| `source_search_hint` | Search this source | Tìm trong nguồn này |
| `addons_from_sources` | From your sources | Từ các nguồn của bạn |
| `source_season_default` | Season %1$d | Mùa %1$d |

## Acceptance criteria

Fixtures: the three published examples plus generated invalid and large fixtures in
`commonTest/kotlin/assests/tsiptv/`.

**Parsing and validation**

- **AC-T1** Each of the three examples validates against `tsiptv-source-v1.json` (CI script) and
  imports with zero errors. `minimal-live` → 2 TV channels, 1 radio; `vod-catalog` → 2 movies,
  1 series, 1 episode; `composed-includes` → the counts of its includes after fetching.
- **AC-T2** For each document error code (`E_NOT_JSON`, `E_NOT_SOURCE`, `E_VERSION` with
  `version: 2`, `E_TOO_LARGE` with a 6 MiB file, `E_ID`, `E_META`, `E_EMPTY`) the matching message
  is shown and nothing is stored.
- **AC-T3** A fixture with one item per item error code imports the valid items and lists every
  skipped item with its JSON path in Details (for example `channels[1].url`).
- **AC-T4** Unknown members (`"foo": 1` at every level) are ignored; `x-` members are ignored;
  unknown `section.type`, `query.from`, `card.style`, `include.type`, `drm.system` behave exactly as
  spec §11 (section skipped, default style, include skipped, stream unplayable).
- **AC-T5** `url` and `streams` both present → item error; item-level `headers` are merged under a
  stream's `headers` (stream wins per key) — verified in the stored `streamsJson`.
- **AC-T6** A URL with `rtmp://`, `file:`, `data:` or `javascript:` in any field is rejected per
  spec §10 (stream dropped, image field dropped, include dropped).
- **AC-T7** Localization: with the UI in Vietnamese, `{ "en": "Movies", "vi": "Phim" }` shows
  "Phim"; in German it shows `meta.language`'s value; `zh-TW` UI matches a `zh-CN` key when no
  `zh-TW` key exists. Changing the UI language updates Source Home without re-import.

**Includes**

- **AC-T8** `composed-includes` fetches 4 includes; its "On air" row shows the M3U channels in
  number order with guide data from the XMLTV include; the addon row shows the sampler's movies; the
  "Picked by hand" row shows exactly Big Buck Bunny then His Girl Friday.
- **AC-T9** Include failure: with the M3U include returning 500 on refresh, the previous channels
  stay visible and About shows `source_include_stale`; on first import, sections that use it are hidden
  and the other sections render.
- **AC-T10** A cycle (A includes B includes A) is skipped with `E_INCLUDE_CYCLE`; depth 4 is refused;
  a 21st include is dropped with `W_LIMIT`.
- **AC-T11** Conditional GET: a second refresh against a server returning `304` reuses the stored
  copy (no reparse; server log shows `If-None-Match`).
- **AC-T12** An include's `headers` are sent on the include request only (server log), not on
  stream requests of its channels.
- **AC-T13** The Stremio include appears in Addon manager under `addons_from_sources`, cannot be
  removed individually, is not on Discover, and is deleted when the source is removed.

**Rendering (phone and TV)**

- **AC-T14** `vod-catalog` renders: hero with 2 banners, Continue watching hidden until something is
  watched, Movies row newest first (Big Buck Bunny, His Girl Friday), Series row, Comedy grid (both
  movies, A→Z) with landscape cards and small corners; accent `#FFB300` on focus rings and chips.
- **AC-T15** A source without `layout` shows the default layout of spec §7.5 with the app's own
  section titles.
- **AC-T16** A background colour with contrast below 4.5:1 against the text (e.g. `#DDDDDD`) is
  replaced by the app background (`W_CONTRAST`); the player keeps the app theme.
- **AC-T17** TV: initial focus on the hero; ◀/▶ change banners; ▼ moves to the next section and
  back ▲ restores the previous card; OK on a movie opens detail with focus on Play; Back returns to
  the same card. Decorative banners are not focusable. Every element is reachable by D-pad.
- **AC-T18** `groupChips: true` on a channel grid shows chips (phone) / a chip row (TV) with the
  groups of the grid's channels; selecting one filters the grid.
- **AC-T19** Selecting a channel card plays it with its headers; ▲/▼ in the TV player zap through the
  channels of that section.
- **AC-T20** Playing His Girl Friday for 2 minutes adds it to the source's Continue watching row with
  a progress bar; it does not appear in another source.
- **AC-T21** A movie with a `vtt` subtitle offers it in the Android and desktop player track menu.

**Lifecycle and safety**

- **AC-T22** Preview lists the distinct hosts of the root and include URLs (for
  `composed-includes`: `tsiptv-8bdd6.web.app` only). `meta.adult: true` requires the 18+ checkbox.
- **AC-T22b** A root without `meta.adult` that includes a TS IPTV Source with `meta.adult: true`
  (or a Stremio fixture addon with `behaviorHints.adult: true`) shows `source_warn_adult_include`
  after includes load; Cancel stores nothing; on refresh, an include that turns adult is not merged
  until confirmed.
- **AC-T22c** An include fetch that fails (500, timeout, over the size cap) is reported as
  `E_INCLUDE` at `includes[i]`; with a previous copy the include shows `source_include_stale`.
- **AC-T23** Re-importing the same `id` from a different URL asks to replace; after replacing,
  favourites of channels whose ids still exist are kept.
- **AC-T24** Removing the source deletes its playlist, channels, VOD rows, include rows, owned addons
  and its `media_history` rows; other playlists are untouched.
- **AC-T25** Performance targets of section 7 are met with the generated large fixture.
- **AC-T26** No source, include or stream URL appears in release logs, analytics or crash reports.
- **AC-T27** Upgrade from the F2 build keeps all data (migration 5 → 6 test + manual upgrade).
- **AC-T28** All new strings exist in all seven locales.

## Validation decisions (2026-09-28)

Answers to the F3 step 1 hand-off (`docs/handoff-f3-step1.md`, "Deviations and interpretations").
All confirm the implementation; the spec was changed to match.

| # | Question | Decision | Spec |
|---|---|---|---|
| 1 | Unknown `include.type`: §10 said `W_UNKNOWN_TYPE`, §11 said `E_INCLUDE` | **`E_INCLUDE`** (the include is dropped, so it is an item-level error). §10's `W_UNKNOWN_TYPE` row no longer lists it. | §10, §11 |
| 2 | `W_UNKNOWN_TYPE` for every §11 fallback | **Yes**: section type, query from, sort, card style, card corner, channel type, MIME hint, DRM system, catch-up mode. Warnings only; behaviour unchanged. §11 table now has a Code column. | §10, §11 |
| 3 | Codes the spec did not assign | **Accepted as implemented**: season/episode number missing or out of range, non-object entries → `E_ITEM_ID`; duplicate season/episode number → `E_DUPLICATE_ID`; series without seasons, season without episodes → `E_NO_STREAM`; non-object stream, malformed `licenseUrl`, invalid EPG link, invalid banner image → `E_URL`; missing `licenseUrl` → `E_DRM`; invalid header value, broken section, tolerated misuses → `W_FIELD`; bad localized entries / line breaks in Text → `W_TEXT`. Fixed uniqueness order, enum case rules and code-point length counting are now normative. (No precedence order between codes was added, to avoid contradicting the implementation.) | §10 "Code assignment rules" |
| 4 | Depth counts every include type | **Yes**: any include declared by a depth-3 document is refused with `E_INCLUDE_CYCLE`. EPG links do not count toward the 20-include tree limit. Cycle-key normalisation (scheme, user info, default port, fragment, host case) is normative. | §9.3 |
| 5 | Rejected nested document | **One `E_INCLUDE`** on the including document at `includes[i]`; nested document errors are never reported as document errors; nested item issues carry an `[includePath]` prefix. | §9.3 |
| 6 | Non-boolean `meta.adult` | **Treated as adult** (`true`, confirmation required) + `W_FIELD`. The schema keeps `boolean` (creators still get an error). | §5.1, §10 |
| 7 | Issue string keys | **22 `source_issue_*` keys**, one per code (the "17" was wrong). | Strings |
| — | Other interpretations (legibility keeps the background image; `accentSecondary` dropped with a rejected accent; `imageDim` fallback; hero with both `query` and `items` uses `items`) | Accepted. | §6, §10 |
| — | `W_QUERY_REF` for undeclared catalogs; existence of `hero.target` / `query.ids` | Checked in **step 2, after merging**, not by the step 1 parser. | §10, plan step 2 |

No schema change was needed for these: every decision concerns reader tolerance (readers stay
more lenient than the schema, spec §10).

### Follow-up from QC (2026-09-28)

| # | Question | Decision | Spec / schema |
|---|---|---|---|
| 5 (schema bug) | Whitespace-only strings passed the schema but readers reject them | Schema tightened (the spec already forbade them): non-whitespace pattern `\S` on every free-text string, LongText and string-array items; Text additionally forbids CR/LF; header values forbid control characters except tab; catch-up `source` forbids whitespace and control characters. The three examples still validate; `check_guides.py` passes. | §4 "Blank strings", §5.2, §5.5; schema |
| a | `null` values | **`null` = absent, silently.** A required member set to `null` is missing (its usual code). `null` array entries are "not an object"; `null` map values are invalid values. | §4 |
| b | `x-` keys inside maps | Map keys are **data**, not members: the `x-` rule does not apply to headers, localized objects or `drm.keys`. `X-Api-Key` and other `x-…` headers keep working. | §4 |
| c | `epg-N` vs explicit include ids | **Prefix `epg-` reserved** for implicit EPG include ids: an explicit include `id` starting with `epg-` → `E_INCLUDE` (include dropped). Item ids may still start with `epg-`. Schema: `not: {pattern: "^epg-"}` on include ids. | §9.1, §10; schema |
| d | Adult content via includes | **The root needs the 18+ confirmation if the root, any included TS IPTV Source at any depth (`meta.adult` true or non-boolean), or any `stremio` include (`behaviorHints.adult: true`) is adult.** Evaluated after fetching includes, before storing; on refresh the responsible include's new content is withheld until confirmed. | §9.3, §12; PRD §1 step 4 |
| e | Failed include fetch | **`E_INCLUDE`** (item level) at `includes[i]` / `epg[i]` of the including document, for network error, timeout, non-2xx (except 304), size cap, budget or decompression failure. The last good copy is kept (stale), not dropped. | §9.3, §10 |
| f | Root in the 50 MiB budget | **Yes.** Decompressed bytes of every document fetched in the refresh count: root, includes, EPG links, Stremio manifests; `304` counts 0; browsing requests to addons do not count. | §9.3 |
| g | Non-object entries | Item arrays (`channels`, `movies`, `series`, `seasons`, `episodes`) → `E_ITEM_ID`; `includes` → `E_INCLUDE`; `streams`, `epg` → `E_URL`; `subtitles`, hero `items`, `layout.home`, string arrays → `W_FIELD` (matches the parser). | §10 |
| h | Length units | **Unicode code points** for every string length; MiB limits count bytes after decompression. | §4, §10 |
| i | 20-header limit | Applies per map **and** to the effective headers after merging item + stream (distinct case-insensitive names; stream's first, then item's in order); excess → `W_LIMIT`. `licenseHeaders` separate. | §5.5, §10 |
| j | Catch-up `source` scheme | `default`: MUST start with `http(s)://`; the substituted URL must be an HttpUrl. `append`: query text, may contain nested URLs of any scheme as data. Other modes ignore `source` silently. No whitespace/control characters in any mode. Invalid where required → `catchup` dropped (`W_FIELD`), channel plays live. | §8.6; schema |
| k | `version: 0` or negative | **`E_VERSION`**. Valid versions are integers ≥ 1. | §4, §10 |

### Follow-up #3 (2026-09-28, lead decisions)

| # | Decision | Spec / schema |
|---|---|---|
| 1 | "Control character" = C0 (U+0000–U+001F), DEL (U+007F) **and C1 (U+0080–U+009F)**. None allowed in HttpUrl values or catch-up `source`; none except tab in header values (matches the parser). | §4, §5.3, §5.5, §8.6, §10; schema `httpUrl`, `headers`, `catchup.source` |
| 2 | Text, LongText and short strings contain no C0 controls (LongText may contain LF and CR — CRLF from Windows editors stays schema-valid; readers normalise CRLF/CR to LF; lead decision 2026-09-28). A name consisting of U+001F alone is schema-invalid. Whitespace for trimming and blank checks = ECMA-262 *WhiteSpace* + *LineTerminator*. | §4, §5.2; schema `text`, `longText`, all short strings |
| 2b | Short strings (groups, genres, tags, cast, directors, `epgId`, `quality`, `license`, `author.name`, `originalName`, `ageRating`, catalog fields, …): readers replace every C0 control (incl. tab, CR, LF) with a space, trim, and report `W_FIELD` — same tolerance as Text (lead decision 2026-09-28). The schema still rejects them. | §4, §5.2, §10 |
| 3 | Catch-up `source` is **not trimmed**: any whitespace or control character anywhere → `W_FIELD`, `catchup` dropped, channel plays live (same as the schema). | §8.6, §10 |
| 5 | Date-times follow RFC 3339 ranges: hour 00–23, minute and second 00–59 (no leap second `:60`), offset hours 00–23 / minutes 00–59. Out-of-range values → `W_FIELD`, field dropped. **F3 step 2 must fix `TsiptvRules` DATE_TIME** (currently checks shape only). Surrounding whitespace is trimmed before the check, lowercase `t`/`z` stay accepted (lead decision 2026-09-28, from F4 wave C QC R4-2). | §5, §10 |
| 4 | "N items will be skipped" (`source_items_skipped`) counts **dropped items** only. `E_URL` and `E_DRM` on one stream drop that stream, and `E_HEADER_FORBIDDEN` drops only that header (the stream stays) — none of them drops the item, so they are listed in **Details** but not counted — unless the item is left with no stream, which is counted once as `E_NO_STREAM` (lead decision 2026-09-28, from F4 wave C QC). | §10, Behaviour table |

Verified: the three examples still validate (Python `jsonschema`, 54 schema cases pass) and
`web/scripts/check_guides.py` is clean.

## Implementation plan (ordered)

1. **Parser + validation** — `core/parser/tsiptv/TsiptvSourceModels.kt`, `TsiptvSourceParser.kt`,
   `ValidationReport.kt`, `LocalizedTextResolver.kt`; detection branch in `IPTVParserFactory`; tests
   with the three examples (read from `web/public/examples/` in `desktopTest`) and invalid fixtures.
   *Done 2026-09-28* (`docs/handoff-f3-step1.md`). PO decisions on the hand-off's questions are
   recorded in "Validation decisions (2026-09-28)" below and folded into spec §9.3, §10, §11.
2. **Resolver** — `core/parser/tsiptv/TsiptvSourceResolver.kt` (include tree, limits, cycles,
   conditional GET via `NetworkClient` — add `ETag`/`Last-Modified` support to
   `core/network/KtorNetworkClient.kt`), M3U/XMLTV/nested parsing, id namespacing. Downloads stop
   reading at the size cap (`TsiptvLimits`) instead of buffering the whole body. **After merging
   the pools**, step 2 also performs the checks that need merged content (spec §10, "Checks that
   need merged content"): `W_QUERY_REF` for a Stremio catalog not declared in the fetched manifest,
   and existence of every `hero.target` and `query.ids` entry (own or included item); a missing
   target makes the banner decorative, missing `ids` entries are skipped. Step 1 checks their
   syntax only.
3. **Persistence** — entities and DAOs for `tsiptv_sources`, `tsiptv_includes`, `vod_items`,
   `vod_episodes`, new `channel` columns; `AppDatabase` version 6 with AutoMigration 5 → 6; `6.json`;
   migration test; `InMemoryIPTVDatabase`.
4. **Import** — extend F1's `usecase/playlist/PlaylistImporter.kt` with the source path (preview →
   confirm → resolve → transactional store); refresh scheduling per include; Stremio include
   registration via F2's `AddonRepository` (`ownerSourceId`).
5. **Preview and errors UI** — `ui/screens/source/SourcePreviewScreen.kt`, details list, error dialog;
   TV variants.
6. **Source Home** — `ui/screens/source/SourceHomeScreen.kt` (section renderers: hero, row, grid;
   card styles; appearance theming with contrast checks; query engine over pools with `include`,
   filters, sort, limit), phone Home/Channels switch in `ui/screens/home/homeiptvlist/HomeFeedScreen.kt`,
   TV rail item and TV renderers in `ui/tv/` (`TvSourceHome.kt`), See all screen, search.
7. **Detail integration** — TS IPTV Source data provider for F2's media detail and stream picker;
   side-loaded subtitles (Android `SubtitleConfiguration`, desktop VLC `:sub-file` / slave API).
8. **About this source** — screen + TV rail item; refresh and remove.
9. **History** — `media_history` writes for source VOD; combined Continue watching query.
10. **Strings** — seven locales; key-set test.
11. **Performance** — generated large fixture test and a measured import on the reference emulators.
