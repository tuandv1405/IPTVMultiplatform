# TS IPTV Source format, version 1 (normative specification)

Status: **Draft for implementation, 2026-09-27** · Owner: PO · Product spec: [`prd-tsiptv-source-format.md`](prd-tsiptv-source-format.md)
JSON Schema: `web/public/schema/tsiptv-source-v1.json`, published at
`https://tsiptv-8bdd6.web.app/schema/tsiptv-source-v1.json`
Examples: `web/public/examples/*.tsiptv.json`

The key words **MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT** and **MAY** are used as in RFC 2119.
"Reader" means any app that imports the format (TS IPTV in the first place). "Creator" means the
person who writes the file.

---

## 1. Purpose and design principles

A **TS IPTV Source** is one JSON document that describes a complete, self-branded collection:

- **metadata**: who made it, what it is, which languages it speaks;
- **appearance**: accent colours, background, card style;
- **home layout**: an ordered list of sections (hero banner, rows, grids) and what each one shows;
- **content**: live TV channels, radio stations, movies, and series with seasons and episodes, each
  with one or more streams (with HTTP headers, DRM and catch-up where needed);
- **remote includes**: M3U playlists, XMLTV guides, Stremio addons and other TS IPTV Sources,
  referenced by URL and placed into sections;
- **localization** of every user-visible title.

Design principles, in priority order:

1. **Data, never code.** The format has no scripts, expressions, templates that run, HTML or
   markup. Every text field is plain text. A reader never executes anything from a source.
2. **Protocols, not content.** The format carries links the creator supplies. The format itself,
   the schema and the reader ship no content and no default sources.
3. **Easy by hand.** The smallest valid file is ten lines (see §4). Shorthands exist for the common case
   (`url` instead of `streams`, a plain string instead of a localized object).
4. **Predictable forward compatibility.** Unknown fields are ignored; unknown enum values degrade
   in a defined way (section 11). Only a breaking change bumps `version`.
5. **Same semantics as the formats it composes.** Headers, DRM and catch-up use the same meaning
   as the Kodi M3U dialect already supported by TS IPTV (see `prd-kodi-m3u-compat.md`), so an M3U
   channel and a TS IPTV Source channel play identically.

## 2. File

| Property | Rule |
|---|---|
| Encoding | UTF-8. A leading BOM MUST be tolerated by readers and SHOULD NOT be written. |
| Syntax | JSON (RFC 8259). No comments, no trailing commas. |
| Root | A JSON object. |
| Suggested extension | `.tsiptv.json` (for example `my-channels.tsiptv.json`). Readers MUST NOT depend on the extension. |
| Media type | Serve as `application/json` (preferred) or `text/plain`. Readers detect the format by content (section 3), not by media type. |
| Compression | Servers MAY use HTTP `Content-Encoding: gzip`. A gzip file body (magic bytes `1F 8B`) MUST be decompressed by readers before parsing. |
| Maximum size | **5 MiB** after decompression for any single TS IPTV Source document (root or included). Larger documents MUST be rejected. |

## 3. Detection

A document is a TS IPTV Source if and only if, after stripping a BOM and leading whitespace, it is
a JSON object whose top-level `format` member equals the string `"tsiptv-source"`.

Readers that sniff content before a full parse (TS IPTV's `IPTVParserFactory.detectFormat`)
SHOULD look for the regular expression `"format"\s*:\s*"tsiptv-source"` in the first 64 KiB, and
MUST check it before the generic JSON branch.

## 4. Top-level object

```json
{
  "$schema": "https://tsiptv-8bdd6.web.app/schema/tsiptv-source-v1.json",
  "format": "tsiptv-source",
  "version": 1,
  "id": "com.example.my-channels",
  "meta": { "name": "My Channels" },
  "channels": [
    { "id": "test-hls", "name": "HLS Test Channel", "url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" }
  ]
}
```

| Field | Type | Req. | Default | Rules |
|---|---|---|---|---|
| `$schema` | string (URI) | no | | Editors use it for validation and autocompletion. Readers MUST NOT fetch it. |
| `format` | string | **yes** | | Exactly `"tsiptv-source"`. |
| `version` | integer | **yes** | | Major version of this specification. This document defines `1`. Valid versions are integers ≥ 1; `0`, negative numbers, non-integers and strings are `E_VERSION`. See section 11. |
| `id` | string | **yes** | | Stable identifier of the source. Pattern `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$`. Reverse-DNS (`com.example.news`) is RECOMMENDED. Readers use it to recognise an update of a source they already have. |
| `revision` | integer ≥ 0 | no | `0` | Creator-maintained counter. Readers MAY show "updated" when it increases. It has no other meaning. |
| `meta` | [Meta](#51-meta) | **yes** | | |
| `appearance` | [Appearance](#6-appearance) | no | defaults of section 6 | |
| `layout` | [Layout](#7-layout) | no | default layout of 7.5 | |
| `channels` | [Channel](#81-channel)[] | no | `[]` | Live TV and radio. Max **10,000** items. |
| `movies` | [Movie](#82-movie)[] | no | `[]` | Max **5,000** items. |
| `series` | [Series](#83-series)[] | no | `[]` | Max **1,000** items. |
| `epg` | (string \| [EpgLink](#91-epglink))[] | no | `[]` | XMLTV guide URLs. Max **10**. Shorthand for `includes` of type `xmltv` (9.1). |
| `includes` | [Include](#92-include)[] | no | `[]` | Remote documents. Max **20** per document. |

Extension members: any member whose name starts with `x-` (at any level) is reserved for
creators and tools. Readers MUST ignore it. This specification will never define an `x-` member.

**Members vs map keys.** The `x-` rule applies only to *members* of objects whose member names this
specification defines. It does **not** apply to the keys of the three **maps**, whose keys are data:
[Headers](#55-headers) (so a header named `X-Api-Key` or `x-forwarded-for` is a normal header and
MUST keep working), localized [Text](#52-text) objects (keys are language tags; an `x-…` key is an
invalid language key, `W_TEXT`) and `drm.keys` (keys are key IDs).

**`null` values.** A member whose value is JSON `null` is treated exactly as if it were **absent**,
silently (no warning). For a required member this means "missing", with that member's usual code
(for example `"name": null` on a channel → `E_ITEM_NAME`, `"meta": null` → `E_META`). Inside
arrays, a `null` entry is an entry that is not an object (see section 10). In maps, a `null` value
is an invalid value (header → `W_FIELD`; localized text → `W_TEXT`; DRM key → `E_DRM`).

**Lengths.** Every length limit in this specification (Text, LongText, Id, HttpUrl, header names
and values, every other string) counts **Unicode code points**, after trimming where trimming
applies (section 10). Size limits in bytes (MiB) count bytes after decompression.

**Blank strings.** Readers trim leading and trailing whitespace from every free-text string (Text
and LongText values, `author.name`, `license`, `originalName`, `ageRating`, `epgId`, `quality`,
group, genre, tag, cast and director names, `catalog` fields). A string that is empty after
trimming is invalid for that field. The schema enforces this with a "contains a non-whitespace
character" pattern. **Whitespace**, for trimming and blank checks everywhere in this
specification, means the ECMA-262 *WhiteSpace* and *LineTerminator* code points (tab, vertical
tab, form feed, space, U+00A0, U+FEFF, the Unicode `Zs` category, LF, CR, U+2028, U+2029).

**Control characters.** "Control character" means C0 (U+0000–U+001F), DEL (U+007F) and C1
(U+0080–U+009F).
- Text, LongText and every other free-text string MUST NOT contain C0 controls, except LF
  (U+000A) and CR (U+000D) in LongText. (Readers are lenient: see 5.2 and section 10.)
  **Short strings** (every free-text string that is not Text or LongText: group, genre, tag, cast
  and director names, `epgId`, `quality`, `license`, `author.name`, `originalName`, `ageRating`,
  `catalog` fields, …): readers replace every C0 control (including tab, CR and LF) with a space,
  then trim, and report `W_FIELD`; a value that is empty afterwards is invalid for that field.
- HttpUrl values and catch-up `source` MUST NOT contain any control character (C0, DEL, C1).
- Header values MUST NOT contain any control character except horizontal tab (U+0009).

A document MUST contain at least one of: a non-empty `channels`, `movies`, `series` or
`includes`. A document with none of them is invalid (error `E_EMPTY`).

## 5. Common types

### 5.1 Meta

| Field | Type | Req. | Default | Rules |
|---|---|---|---|---|
| `name` | [Text](#52-text) | **yes** | | Display name of the source. |
| `description` | [LongText](#52-text) | no | | |
| `author` | object | no | | `{ "name": string (req, 1–100), "url"?: HttpUrl, "email"?: string (≤ 254) }` |
| `logo` | HttpUrl | no | | Square image, PNG/JPG/WebP, at least 256×256 recommended. |
| `homepage` | HttpUrl | no | | Readers show it as a link in "About this source" and MUST ask before opening it. |
| `language` | LanguageTag | no | `"en"` | Default language of Text values given as plain strings, and the fallback language of localized objects. |
| `languages` | LanguageTag[] | no | | Languages the content is in (informational, for display). Max 20. |
| `updatedAt` | string (RFC 3339 date-time) | no | | For example `"2026-09-27T10:00:00Z"`. Informational. |
| `adult` | boolean | no | `false` | `true` if the source contains adult content. Readers MUST warn and ask for confirmation before import. A present non-boolean value is treated as `true` (`W_FIELD`, section 10). |
| `license` | string (≤ 200) | no | | Free text or SPDX identifier describing the licence of the *listing* (not of the streams). |

### 5.2 Text

Every user-visible string is a **Text** value, in one of two forms:

- a **plain string**, in the language given by `meta.language`; or
- a **localized object** mapping language tags to strings, for example
  `{ "en": "Movies", "vi": "Phim", "zh-CN": "电影" }`. At least one entry. Max 30 entries.

Length limits: **Text** 1–200 characters per value; **LongText** (descriptions) 1–5,000
characters (code points) per value, after trimming; a value that is empty after trimming is
invalid. Values are plain text: readers MUST NOT interpret HTML, Markdown or escape sequences
other than JSON's own. Line breaks are allowed in LongText only, as LF, CR or CRLF (files
edited on Windows stay valid); readers normalise CRLF and lone CR to LF. The schema rejects every
other C0 control in LongText, and every C0 control (including tab and line breaks) in Text. Readers are lenient: line breaks in a Text value are replaced by
spaces (`W_TEXT`). Short strings that are not Text (section 4) get the same tolerance: C0
controls replaced by spaces, then trimmed, reported as `W_FIELD`.

**LanguageTag**: BCP 47 subset, pattern `^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$` (`en`, `vi`,
`zh-CN`, `pt-BR`).

**Resolution** (reader behaviour, in order):

1. exact match of the app's UI language (`zh-CN`);
2. match of its primary subtag (`zh`), then any key with the same primary subtag (`zh-TW`);
3. `meta.language`;
4. `en`;
5. the first entry in document order.

### 5.3 HttpUrl

A string of at most 2,048 characters that is an absolute URL with scheme `http` or `https`
(case-insensitive). Every URL anywhere in the document — streams, images, includes, licence
servers, links — MUST be an HttpUrl. Any other scheme (`file:`, `content:`, `data:`,
`javascript:`, `intent:`, `plugin:`, `rtmp:`, `rtsp:`, `udp:`, `magnet:` …) is invalid.

An HttpUrl contains no whitespace and no control character (C0, DEL, C1; section 4); such a URL
is invalid (`E_URL`).

Creators SHOULD use `https`. Readers MAY show a "not encrypted" indicator for `http` URLs.
Relative URLs are not allowed (there is no base URL).

### 5.4 Id

Pattern `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$`. Case-sensitive. The colon `:` is **not** allowed
because readers use it to namespace items that come from includes (`<includeId>:<itemId>`, 9.3).

**Uniqueness:** within one document, every `id` of a channel, movie, series, episode and include
MUST be unique across all of those collections together. The first occurrence wins; later
duplicates are item errors (`E_DUPLICATE_ID`).

### 5.5 Headers

An object mapping HTTP header names to string values: `{ "User-Agent": "Mozilla/5.0", "Referer": "https://example.com/" }`.

- Names: RFC 9110 token characters, pattern `^[!#$%&'*+.^_`|~0-9A-Za-z-]+$`, compared
  case-insensitively. Values: 0–4,096 code points (not trimmed), no control characters other than
  horizontal tab (U+0000–U+0008, U+000A–U+001F, U+007F and C1 U+0080–U+009F are invalid →
  `W_FIELD`, header dropped).
  Non-ASCII characters are allowed.
- Max **20** headers per map. The limit also applies to the **effective** headers of a stream
  after merging the item's `headers` under the stream's (8.0), counted by distinct
  case-insensitive name: the stream's own headers are kept first, then the item's in document
  order, until 20; the rest are dropped with `W_LIMIT`. `drm.licenseHeaders` is a separate map
  with its own limit of 20.
- Forbidden names (item error `E_HEADER_FORBIDDEN`, header dropped): `Host`, `Content-Length`,
  `Connection`, `Transfer-Encoding`.
- Values are sent as written. They are **not** URL-decoded (unlike the Kodi `|` suffix).
- Headers apply to every HTTP request for that stream: HLS/DASH manifests, variant playlists,
  segments and keys. They do not apply to the DRM licence request (use `drm.licenseHeaders`).

### 5.6 Color

`#RRGGBB` hexadecimal, pattern `^#[0-9A-Fa-f]{6}$`.

## 6. Appearance

```json
"appearance": {
  "accent": "#FFB300",
  "accentSecondary": "#FF7043",
  "background": { "color": "#101014", "gradient": ["#101014", "#1C1C24"], "image": "https://example.com/bg.jpg", "imageDim": 0.7 },
  "card": { "style": "poster", "corner": "medium", "showTitles": true }
}
```

| Field | Type | Default | Rules |
|---|---|---|---|
| `accent` | Color | app accent (`#00F5A0`) | Focus rings, selected chips, primary buttons, progress bars. |
| `accentSecondary` | Color | app secondary accent (`#00D9E9`) | Gradients on buttons, badges. |
| `background.color` | Color | app background (`#03041D`) | Solid background of the source's home. |
| `background.gradient` | Color[2..3] | none | Top-to-bottom gradient; overrides `color` when present. |
| `background.image` | HttpUrl | none | Drawn behind the home, cropped to fill, under a dark scrim. |
| `background.imageDim` | number 0.3–1.0 | `0.6` | Opacity of the scrim over the image. Values below 0.3 are raised to 0.3. |
| `card` | [CardStyle](#61-cardstyle) | `{ "style": "auto" }` | Default card style for every section. |

**Legibility rules (MUST, reader side).** The app keeps its own text colour (`#F3F4F6`).
- If the contrast ratio (WCAG 2.1) between the text colour and `background.color` (or each
  gradient stop) is below **4.5:1**, the reader ignores the creator's background colour/gradient and
  uses its own. A `background.image` is kept, under its scrim, on top of the app's colour.
- If the contrast between `accent` and the effective background is below **3:1**, the reader
  ignores `accent` and `accentSecondary` together and uses its own. `accentSecondary` is not
  contrast-checked on its own.
- Appearance applies only inside the source's home and its detail screens. The player, system
  dialogs, settings and error messages keep the app's own theme.

### 6.1 CardStyle

| Field | Type | Default | Values |
|---|---|---|---|
| `style` | string | `"auto"` | `auto`, `poster` (2:3 portrait), `landscape` (16:9), `square` (1:1), `logo` (channel logo tile with name under it), `list` (one line per item: logo, name, now-playing programme) |
| `corner` | string | `"medium"` | `none` (0), `small` (4 dp), `medium` (12 dp), `large` (20 dp) |
| `showTitles` | boolean | `true` | Show the item name under or on the card. Channel and `list` cards always show names. |

`auto` resolves per item kind: movies and series → `poster`; TV channels → `logo`; radio →
`square`; episodes → `landscape`; Stremio catalog items → their `posterShape`.

## 7. Layout

```json
"layout": {
  "home": [
    { "type": "hero", "query": { "from": "movies", "limit": 5 } },
    { "type": "row", "title": { "en": "Continue watching", "vi": "Xem tiếp" }, "query": { "from": "continueWatching" } },
    { "type": "row", "title": "News", "query": { "from": "channels", "groups": ["News"] }, "card": { "style": "logo" } },
    { "type": "grid", "title": "All channels", "query": { "from": "channels", "sort": "number" }, "groupChips": true }
  ]
}
```

| Field | Type | Req. | Rules |
|---|---|---|---|
| `home` | [Section](#71-section)[] | **yes** (if `layout` present) | 1–**30** sections, rendered top to bottom in order. |

### 7.1 Section

| Field | Type | Req. | Default | Rules |
|---|---|---|---|---|
| `type` | string | **yes** | | `hero`, `row` or `grid`. |
| `id` | Id | no | | Lets readers remember scroll/focus position per section. |
| `title` | Text | no (`row`, `grid`: recommended) | none | Heading above the section. Hero sections show no heading. |
| `subtitle` | Text | no | | Smaller line under the title. |
| `query` | [Query](#72-query) | **yes**, except `hero` with `items` | | What the section shows. |
| `items` | [HeroItem](#73-heroitem)[] | `hero` only | | Hand-made banners, 1–**10**. A hero has either `query` or `items`, not both. |
| `card` | CardStyle | no | `appearance.card` | Overrides the default card style for this section. Ignored by `hero`. |
| `seeAll` | boolean | no | `true` for `row` | `row` only: show a "See all" action that opens the same query as a full grid without `limit`. |
| `groupChips` | boolean | no | `false` | `grid` only, and only with `from` = `channels` or `radio`: show group filter chips (TV: a group rail) above the grid. |

Section behaviour:

- **hero**: a full-width banner carousel of 1–10 items. With `query`, each item uses its
  `backdrop`, else `poster`, else `logo`; items with no image are skipped. At most 10 items even if
  `limit` is higher.
- **row**: a single horizontal line of cards. Default `limit` 20, max 100.
- **grid**: a vertical grid of cards. Default `limit` none (all matching items, paged by the reader).
- A section whose query yields **no items** is not rendered (no empty heading).
- `continueWatching` and `favorites` sections are hidden until they have items.

### 7.2 Query

A query selects items from the source's **pools**. Pools are built from the document's own
`channels`, `movies` and `series` plus the content of its includes (9.3).

| Field | Type | Req. | Default | Rules |
|---|---|---|---|---|
| `from` | string | **yes** | | `channels` (TV channels), `radio` (radio channels), `movies`, `series`, `catalog` (a Stremio include catalog), `continueWatching`, `favorites`. |
| `include` | Id | required for `catalog` | none | Restricts the pool to items from the include with this `id`. For `catalog`, names the `stremio` include. |
| `catalog` | object | required for `catalog` | | `{ "type": string (req), "id": string (req), "genre"?: string }`. Must name a catalog declared in that addon's manifest. |
| `ids` | Id[] | no | | Only these items, **in this order** (then `sort` is ignored). 1–500 entries. Include items are referenced as `includeId:itemId`. |
| `groups` | string[] | no | | Channels whose `groups` contain any of these (case-insensitive exact match). |
| `genres` | string[] | no | | Movies/series whose `genres` contain any of these (case-insensitive). |
| `tags` | string[] | no | | Items whose `tags` contain any of these. |
| `sort` | string | no | `source` | `source` (document order; includes after own items, in include order), `name` (A→Z by resolved name, locale-aware), `number` (channel `number` ascending, missing numbers last), `year` (ascending), `yearDesc` (newest first), `recent` (`continueWatching` only, and its default). |
| `limit` | integer 1–500 | no | section default | Max items. |

Filters combine with **AND** between fields and **OR** within a field. Filters that do not apply
to the pool (for example `genres` on `channels`) are ignored. For `catalog`, only `genre` inside
`catalog` filters; `groups`/`genres`/`tags`/`sort` are ignored and the addon's own order is kept.

`continueWatching` returns the source's partly watched movies and episodes (and recently played
channels), most recent first. `favorites` returns channels of this source the user marked as
favourite. Both are per-user state kept by the reader, never stored in the document.

### 7.3 HeroItem

| Field | Type | Req. | Rules |
|---|---|---|---|
| `image` | HttpUrl | **yes** | 16:9 image, at least 1280×720 recommended. |
| `title` | Text | no | Drawn over the image. |
| `subtitle` | Text | no | Second line. |
| `target` | Id | no | Item opened on select (channel, movie, series or episode; include items as `includeId:itemId`). A banner without `target` is decorative and not focusable on TV. |
| `autoplay` | boolean | no | `false`. `true` starts playback of the target instead of opening its detail page (channels, movies and episodes only). |

Banners cannot link outside the app. There is no URL action.

### 7.4 What readers do with sections they cannot render

See section 11: an unknown `type` or `from` skips the section and records a warning.

### 7.5 Default layout

When `layout` is absent (or every section was skipped), readers build this layout:

1. `row` "Continue watching" (`from: continueWatching`);
2. `hero` from `movies` (limit 5), only if at least one movie has a `backdrop`;
3. `row` "Movies" (`from: movies`), if any;
4. `row` "Series" (`from: series`), if any;
5. `row` "Radio" (`from: radio`), if any;
6. `grid` "Channels" (`from: channels`, `sort: number`, `groupChips: true`), if any.

The default titles are the reader's own localized strings.

## 8. Content items

### 8.0 Playable fields (shared by channels, movies and episodes)

A playable item has **exactly one** of `url` or `streams`.

| Field | Type | Rules |
|---|---|---|
| `url` | HttpUrl | Shorthand for `"streams": [{ "url": … }]`. |
| `streams` | [Stream](#84-stream)[] | 1–**10** alternatives, best first. Readers play the first one that is supported on the device and let the user pick another. |
| `headers` | Headers | Default headers for every stream of this item. A stream's own `headers` are merged over these, key by key (stream wins). |
| `mimeType` | string | Default MIME type hint for every stream (8.4). |
| `drm` | [Drm](#85-drm) | Default DRM for every stream. A stream's own `drm` replaces it entirely. |

### 8.1 Channel

```json
{
  "id": "example-news",
  "name": { "en": "Example News", "vi": "Tin tức Ví dụ" },
  "number": 101,
  "type": "tv",
  "logo": "https://example.com/logos/news.png",
  "groups": ["News", "HD"],
  "epgId": "ExampleNews.us",
  "url": "https://cdn.example.com/news/index.m3u8",
  "headers": { "Referer": "https://example.com/" },
  "catchup": { "mode": "shift", "days": 3 }
}
```

| Field | Type | Req. | Default | Rules |
|---|---|---|---|---|
| `id` | Id | **yes** | | |
| `name` | Text | **yes** | | |
| `type` | string | no | `tv` | `tv` or `radio`. |
| `number` | integer 0–99,999 | no | | Channel number shown on cards and used by `sort: number`. |
| `logo` | HttpUrl | no | | Square or wide logo, transparent PNG recommended. |
| `groups` | string[] | no | `[]` | 0–10 group names, each 1–100 characters. Used by `groups` filters and group chips. Group names are matched case-insensitively and are **not** localized (they are keys); display them as written. |
| `epgId` | string (1–200) | no | | Matches XMLTV `<channel id>`. If absent, readers match by resolved name (Kodi rule, `prd-kodi-m3u-compat.md`). |
| `epgShiftHours` | number −12 … +14 | no | `0` | Shift applied to this channel's guide times. |
| `description` | LongText | no | | |
| `tags` | string[] | no | | 0–20 free tags, each 1–50 characters. |
| `catchup` | [Catchup](#86-catchup) | no | | Archive/catch-up description. |
| playable fields | | **yes** | | Section 8.0. |

### 8.2 Movie

| Field | Type | Req. | Rules |
|---|---|---|---|
| `id` | Id | **yes** | |
| `name` | Text | **yes** | |
| `originalName` | string (1–200) | no | Title in the original language, shown under `name` if different. |
| `poster` | HttpUrl | no | 2:3 portrait. |
| `backdrop` | HttpUrl | no | 16:9 landscape, used by hero and detail header. |
| `logo` | HttpUrl | no | Title logo with transparency, drawn over the backdrop. |
| `description` | LongText | no | |
| `year` | integer 1870–2100 | no | |
| `releaseDate` | string (RFC 3339 full-date, `YYYY-MM-DD`) | no | |
| `runtimeMinutes` | integer 1–1,440 | no | |
| `genres` | string[] | no | 0–10, each 1–50 characters. |
| `cast` | string[] | no | 0–50 names. |
| `directors` | string[] | no | 0–20 names. |
| `countries` | string[] | no | ISO 3166-1 alpha-2 codes, pattern `^[A-Z]{2}$`. |
| `languages` | LanguageTag[] | no | Audio languages. |
| `ageRating` | string (1–16) | no | As printed, e.g. `"PG-13"`, `"T16"`. Informational. |
| `tags` | string[] | no | As for channels. |
| `subtitles` | [Subtitle](#87-subtitle)[] | no | 0–30. |
| playable fields | | **yes** | Section 8.0. |

### 8.3 Series

| Field | Type | Req. | Rules |
|---|---|---|---|
| `id`, `name`, `originalName`, `poster`, `backdrop`, `logo`, `description`, `genres`, `cast`, `countries`, `languages`, `ageRating`, `tags` | | as Movie | `id` and `name` required. |
| `year` | integer 1870–2100 | no | First year. |
| `endYear` | integer 1870–2100 | no | Last year; absent = ongoing or unknown. MUST be ≥ `year`. |
| `seasons` | [Season](#season)[] | **yes** | 1–100 seasons. |

<a id="season"></a>**Season**

| Field | Type | Req. | Rules |
|---|---|---|---|
| `number` | integer 0–999 | **yes** | `0` = Specials. Unique within the series. Readers list seasons ascending with 0 last. |
| `name` | Text | no | Default "Season N" / "Specials" (reader strings). |
| `poster` | HttpUrl | no | |
| `description` | LongText | no | |
| `episodes` | [Episode](#episode)[] | **yes** | 1–500 episodes. |

<a id="episode"></a>**Episode**

| Field | Type | Req. | Rules |
|---|---|---|---|
| `id` | Id | **yes** | Unique in the document (5.4). Used for watch progress. |
| `number` | integer 0–9,999 | **yes** | Unique within the season. Listed ascending. |
| `name` | Text | **yes** | |
| `description` | LongText | no | |
| `thumbnail` | HttpUrl | no | 16:9 still. |
| `releaseDate` | full-date | no | A date in the future marks the episode "upcoming": listed, not playable. |
| `runtimeMinutes` | integer 1–1,440 | no | |
| `subtitles` | Subtitle[] | no | 0–30. |
| playable fields | | **yes** | Section 8.0. |

Limit: at most **20,000** episodes in one document.

### 8.4 Stream

| Field | Type | Req. | Rules |
|---|---|---|---|
| `url` | HttpUrl | **yes** | HLS (`.m3u8`), DASH (`.mpd`), progressive MP4/WebM/MKV, MPEG-TS, or audio (AAC/MP3/Opus). |
| `name` | Text | no | Label in the stream picker, e.g. `"1080p"`, `"Backup"`. Default "Source N". |
| `headers` | Headers | no | Merged over the item's `headers`. |
| `mimeType` | string | no | Hint when the URL has no telling extension. Pattern `^[a-z]+/[A-Za-z0-9.+-]+$`. Recognised: `application/x-mpegURL`, `application/vnd.apple.mpegurl` (HLS), `application/dash+xml` (DASH), `video/mp4`, `video/mp2t`, `video/webm`, `video/x-matroska`, `audio/aac`, `audio/mpeg`, `audio/ogg`. Others are ignored. |
| `drm` | Drm | no | Replaces the item's `drm`. |
| `quality` | string (1–32) | no | Informational badge (`"HD"`, `"720p"`). |
| `language` | LanguageTag | no | Audio language of this stream, shown as a badge. |

### 8.5 Drm

| Field | Type | Req. | Rules |
|---|---|---|---|
| `system` | string | **yes** | `widevine`, `playready` or `clearkey`. |
| `licenseUrl` | HttpUrl | `widevine`/`playready`: **yes**; `clearkey`: one of `licenseUrl` or `keys` | Licence server. The reader POSTs the raw CDM challenge and expects the raw licence in the body (the default behaviour of Media3's `HttpMediaDrmCallback`). Wrapped or JSON licence protocols are not part of v1. |
| `licenseHeaders` | Headers | no | Sent with licence requests only. |
| `keys` | object | `clearkey` only | Map of key ID → key, both 32 hexadecimal characters (`"000102030405060708090a0b0c0d0e0f": "00112233445566778899aabbccddeeff"`). 1–20 pairs. Readers convert them to a W3C Clear Key JSON Web Key Set locally. |

Platform note (informative): Widevine and PlayReady play on Android (PlayReady mostly on Android
TV). ClearKey plays on Android for DASH only. iOS and desktop readers show "DRM is not supported on
this device" for any stream with `drm`, and try the next stream of the item if it has none.

Creators MUST only publish keys and licence endpoints they are entitled to share.

### 8.6 Catchup

Same semantics as the Kodi IPTV Simple catch-up attributes (`docs/research/kodi.md` §1.7).

| Field | Type | Req. | Rules |
|---|---|---|---|
| `mode` | string | **yes** | `default`, `append`, `shift`, `flussonic`, `flussonic-ts`, `xc`. |
| `source` | string (1–2,048) | `default`: **yes**; `append`: **yes**; others: no | `default`: full URL template (MUST start with `http://` or `https://`). `append`: the string appended to the live URL (for example `"?utc={utc}&lutc={lutc}"`). May contain the placeholders `{utc}`, `{utcend}`, `{lutc}`, `{Y}`, `{m}`, `{d}`, `{H}`, `{M}`, `{S}`, `{duration}`, `{duration:N}`, `{offset:N}`, `{utc:FMT}`, `{utcend:FMT}`, `${start}`, `${end}`, `${now}`, `${timestamp}`, `${duration}`, `${start:FMT}`, `${end:FMT}`. |
| `days` | integer 1–60 | no | Length of the archive. Default `7`. |
| `correctionHours` | number −24 … +24 | no | Hours added to all times before substitution. Default `0`. |

**`source` scheme rules.** In every mode `source` is **not trimmed**: any whitespace (section 4
definition, including leading or trailing) or control character (C0, DEL, C1) anywhere makes it
invalid (`W_FIELD`, `catchup` dropped, channel plays live), exactly as the schema checks.
- **Template mode (`default`)**: `source` is a URL template and MUST start with `http://` or
  `https://` (case-insensitive). It is not checked as an HttpUrl before substitution (placeholders
  such as `{utc}` are not valid URL characters), but the URL produced by substitution MUST be an
  HttpUrl or catch-up is unavailable for that programme.
- **Append mode (`append`)**: `source` is query text appended to the live URL, which is already an
  HttpUrl. It MAY contain nested URLs of any scheme as parameter data (for example
  `&back=https://example.com/x`); they are never opened by the reader.
- **Other modes** (`shift`, `flussonic`, `flussonic-ts`, `xc`) build the URL from the live URL;
  a `source` is ignored silently.
- A missing or invalid `source` where it is required drops the `catchup` object (`W_FIELD`); the
  channel stays and plays live.

Readers that do not implement catch-up playback MUST still import channels with `catchup` and play
them live.

### 8.7 Subtitle

| Field | Type | Req. | Rules |
|---|---|---|---|
| `url` | HttpUrl | **yes** | WebVTT (`.vtt`) or SubRip (`.srt`). |
| `language` | LanguageTag | **yes** | |
| `label` | Text | no | Picker label. Default: the language name. |
| `format` | string | no | `vtt` or `srt`. Default: from the URL extension, else `vtt`. |

## 9. EPG links and includes

### 9.1 EpgLink

`epg` entries are either a plain HttpUrl string or `{ "url": HttpUrl (req), "refreshHours"?: 1–720 (default 24) }`.
Each entry is equivalent to an include `{ "type": "xmltv", "id": "epg-<index>", "url": … }`
(`<index>` = 0-based position in `epg` as written). **The prefix `epg-` is reserved** for these
implicit ids: an explicit include whose `id` starts with `epg-` (case-sensitive) is invalid
(`E_INCLUDE`, include dropped). Channels, movies, series and episodes may still use ids starting
with `epg-` (queries reference includes and items in separate namespaces), and implicit EPG ids
remain outside the uniqueness set of 5.4.
XMLTV documents may be gzip-compressed (`.xml.gz`). Guide data from all EPG links and `xmltv`
includes is merged; programmes are matched to channels by `epgId`, then by channel name.

### 9.2 Include

```json
"includes": [
  { "id": "news", "type": "m3u", "url": "https://example.com/playlists/news.m3u", "name": "Partner news" },
  { "id": "guide", "type": "xmltv", "url": "https://example.com/epg/guide.xml.gz", "refreshHours": 12 },
  { "id": "sampler", "type": "stremio", "url": "https://addon.example.com/manifest.json" },
  { "id": "archive", "type": "tsiptv-source", "url": "https://example.com/archive.tsiptv.json" }
]
```

| Field | Type | Req. | Default | Rules |
|---|---|---|---|---|
| `id` | Id | **yes** | | Unique (5.4). Namespaces the included items. |
| `type` | string | **yes** | | `m3u`, `xmltv`, `stremio`, `tsiptv-source`. |
| `url` | HttpUrl | **yes** | | `stremio`: MUST be a manifest URL whose path ends with `/manifest.json`. |
| `name` | Text | no | from the document | Shown in "About this source" and in error messages. |
| `refreshHours` | integer 1–720 | no | `24` | Minimum time between two automatic fetches. |
| `headers` | Headers | no | | Sent when **fetching the include document** (`m3u`, `xmltv`, `tsiptv-source`). Not sent to addons, not applied to streams. |

### 9.3 How includes map into pools

| Include type | Contributes to | Item ids | Notes |
|---|---|---|---|
| `m3u` | `channels` (entries with `radio="true"` go to `radio`) | `includeId:` + the channel's M3U id (`tvg-id`, else the reader's name slug) with every character outside the Id set replaced by `_` (`ExampleTV.us@HD` → `live:ExampleTV.us_HD`) | Parsed with the reader's M3U parser, including Kodi properties, headers and DRM. `x-tvg-url` in the M3U header is added to the EPG set. VOD entries (`media="true"`) are imported as channels. |
| `xmltv` | guide data only | — | Referencing an `xmltv` include in `query.include` is an error (section skipped). |
| `stremio` | nothing in the pools; used through `from: "catalog"` | Stremio ids, used as is inside that include | Catalog, meta and stream requests follow the Stremio addon protocol (`prd-stremio-addons.md`). Only `url` streams play. |
| `tsiptv-source` | `channels`, `radio`, `movies`, `series` of the included document, and its EPG links | `includeId:` + the included item's id | The included document's `meta`, `appearance` and `layout` are **ignored**. Its own includes are followed (depth rule below). |

**Depth and cycles.** A root document may include TS IPTV Sources that include further documents,
to a maximum **depth of 3** (root = depth 1). **Depth counts every include type**: an include of
any type (`m3u`, `xmltv`, `stremio`, `tsiptv-source`) declared by a depth-3 document would sit at
depth 4 and is refused with `E_INCLUDE_CYCLE`. Readers keep the set of normalised URLs on the
current include path; an include whose URL is already on the path is a cycle and is skipped
(`E_INCLUDE_CYCLE`). URL normalisation for this check ignores the scheme (`http` ≡ `https`),
user info, a default port, the fragment and host letter case. Across the whole tree: at most **20**
includes (EPG links of 9.1 do not count; they have their own limit of 10 per document), at most
**50 MiB** fetched per refresh — counted in bytes after decompression over **every** document
fetched in that refresh, **including the root** and EPG links (a `304 Not Modified` counts 0; a
Stremio addon's manifest counts, its catalogue/meta/stream requests made while browsing do not) —
and at most **20,000** channels, 5,000 movies and 1,000 series after
merging. Items beyond a limit are dropped with warning `W_LIMIT`. Namespaced ids from nested
includes chain (`archive:partner:item`) and may exceed 128 characters.

**Failure.** A failing include never fails the root document. A **fetch failure** — network
error, timeout, non-2xx status (other than 304), a body over the include type's size cap or over
the remaining 50 MiB budget, or a body that cannot be decompressed — is reported as **`E_INCLUDE`**
(item level) at the path of the `includes[i]` (or `epg[i]`) entry of the including document. Readers
keep the last successfully fetched copy of each include and show it (the include is then *stale*,
not dropped); without one, sections that use the include are hidden.

**Adult content through includes.** An included document's `meta` is otherwise ignored, but its
adult flag is not. The **root requires the adult confirmation** of 5.1 and section 12 when **any**
of these holds: the root's `meta.adult` is `true` (or present and not a boolean); **any** included
TS IPTV Source, at any depth, has `meta.adult` `true` (or present and not a boolean); **any**
`stremio` include's manifest has `behaviorHints.adult: true`. The rule is evaluated after the
include tree is fetched and before anything is stored. If a refresh makes an unconfirmed source
adult, the reader MUST NOT merge the new content of the include(s) that caused it (the previous
copy stays; with none, the include is treated as failed) until the user confirms.

**Rejected nested documents.** If an included TS IPTV Source is rejected (any document error of
section 10), readers record **one `E_INCLUDE`** on the *including* document, at the path of the
`includes[i]` entry, and drop that include. Document-level codes are never reported for a nested
document, so a tree report contains document errors only for the root. Item errors and warnings
inside an accepted nested document are reported with the include path as a prefix
(`[cinema] movies[0].poster`).

## 10. Validation

Readers validate in three levels. **Document errors** reject the whole file. **Item errors**
drop the offending item (or field) and import the rest. **Warnings** change nothing but are
reported.

| Code | Level | Condition |
|---|---|---|
| `E_NOT_JSON` | document | Not parseable as JSON, or root is not an object. |
| `E_NOT_SOURCE` | document | `format` missing or not `"tsiptv-source"`. |
| `E_VERSION` | document | `version` missing, `null`, not an integer, `0` or negative, or greater than the highest version the reader supports. |
| `E_TOO_LARGE` | document | Larger than 5 MiB after decompression. |
| `E_ID` | document | `id` missing or invalid. |
| `E_META` | document | `meta` or `meta.name` missing or invalid. |
| `E_EMPTY` | document | No items and no includes, or every item and include was dropped. |
| `E_ITEM_ID` | item | Missing or invalid `id`; a season or episode `number` missing or out of range; an entry of `channels`, `movies`, `series`, `seasons` or `episodes` that is not an object (including `null`). |
| `E_DUPLICATE_ID` | item | `id` already used in the document; a season `number` already used in the series; an episode `number` already used in the season. |
| `E_ITEM_NAME` | item | Missing or invalid `name`. |
| `E_NO_STREAM` | item | No `url`/`streams`, both present, or no valid stream left; a series with no (valid) season; a season with no (valid) episode. |
| `E_URL` | field / item | URL invalid, too long or not http(s), including a malformed `drm.licenseUrl` and an invalid EPG link. An invalid stream URL drops the stream; an invalid image URL drops the image field; an invalid hero `image` drops the banner; a stream entry that is not an object is dropped with this code. |
| `E_HEADER_FORBIDDEN` | field | Forbidden or invalid header **name**; the header is dropped. (Invalid header **values** are `W_FIELD`.) |
| `E_DRM` | stream | DRM object invalid (missing `licenseUrl`, bad key format). The stream is dropped. |
| `E_INCLUDE` | include | Invalid include (unknown or missing `type`, invalid `url`, invalid `id`, an `id` starting with the reserved prefix `epg-`, an entry that is not an object), an included TS IPTV Source that was rejected, or a **fetch failure** of an include or EPG link (9.3). An invalid include is dropped; a failed fetch keeps the last good copy if there is one. |
| `E_INCLUDE_CYCLE` | include | Cycle, or depth > 3 for an include of any type (9.3). |
| `W_UNKNOWN_TYPE` | warning | An unknown enum value was replaced by its section 11 fallback: section `type`, query `from`, query `sort`, card `style`, card `corner`, channel `type`, stream `mimeType` (valid syntax, not recognised), `drm.system`, `catchup.mode`. (An unknown include `type` is `E_INCLUDE`, because the include is dropped.) |
| `W_QUERY_REF` | warning | Query references an unknown include, an `xmltv` include, or a catalog not in the addon's manifest; the section is skipped. |
| `W_LIMIT` | warning | A count limit was reached; extra items were dropped. Also: more than 20 effective headers after merging item and stream headers (5.5). |
| `W_CONTRAST` | warning | A creator colour was ignored for legibility (section 6). |
| `W_TEXT` | warning | A Text value was too long and was truncated, or a localized object had an invalid language key (the key is ignored). |
| `W_FIELD` | warning | An optional field had the wrong type or was out of range (for example `number: -5`, `year: 3000`), or a header value was invalid (a control character other than tab — C0, DEL or C1 —, longer than 4,096 code points, not a string), or a catch-up `source` was missing or invalid where required (8.6; not trimmed, any whitespace or control character invalidates it). The field or header is dropped; the item stays. Also used for a structurally broken section (no `type`, no `query`, no `from`, wrong shapes), which is skipped, and for the tolerated misuses listed below. |

**Code assignment rules** (so that every reader reports the same code):

- The 22 codes above (7 document, 9 item, 6 warning) are the complete set; readers MUST NOT
  invent others. Conditions not named in the table map as listed in the table's extended
  conditions and in the rules below.
- **Uniqueness order** (5.4 "first occurrence wins") is evaluated in this fixed order, not raw JSON
  member order: `channels` → `movies` → `series` (each series followed by its episodes) → `includes`.
  A dropped item does not claim its id; a dropped series releases its episodes' ids. The implicit ids
  of EPG links (`epg-<index>`, index 0-based as written) are not part of the uniqueness set.
- **Entries that are not objects**: in item arrays (`channels`, `movies`, `series`, `seasons`,
  `episodes`) → `E_ITEM_ID`; in `includes` → `E_INCLUDE`; in `streams` → `E_URL`; in `epg` (neither
  a string nor an object) → `E_URL`; in `subtitles`, hero `items`, `layout.home` and string arrays
  (`groups`, `genres`, `tags`, `cast`, `directors`, `countries`, `languages`, query filters) →
  `W_FIELD`, entry dropped. `null` entries count as "not an object" (or "not a string").
- **`null` members** are absent, silently (section 4).
- **`meta.adult`** present but not a boolean is treated as `true` (fail-safe: the reader asks for
  confirmation) and reported as `W_FIELD`.
- Tolerated misuses (reported as `W_FIELD`, never rejected): a hero with both `query` and `items`
  uses `items`; `sort: "recent"` outside `continueWatching` falls back to `source`; `catalog` on a
  query whose `from` is not `catalog` is ignored; `headers` on a `stremio` include are ignored;
  `imageDim` above 1.0, negative or not a number falls back to `0.6` (below 0.3 is raised to 0.3
  silently, section 6).
- Text: localized entries that are not non-empty strings, entries beyond 30, and line breaks in a
  non-Long Text (replaced by spaces) are `W_TEXT`. **All** string lengths in this specification are
  counted in Unicode code points (section 4); free-text values are trimmed and a value that is
  empty after trimming is invalid. In short strings (section 4), C0 controls including tab, CR and
  LF are replaced by spaces before trimming and reported as `W_FIELD`. A JSON number with a zero fraction (`3.0`) counts
  as an integer.
- Enum matching is case-insensitive for section `type`, include `type`, `drm.system`, channel
  `type`, card `style`/`corner` and subtitle `format`; `query.from` and `query.sort` are
  case-sensitive.
- **Checks that need merged content** are done after includes are resolved, not at parse time:
  `W_QUERY_REF` for a catalog not declared in the addon's manifest, and whether a `hero.target` or a
  `query.ids` entry names an existing item (own or included). A missing target makes the banner
  decorative; missing `ids` entries are skipped silently.

The published JSON Schema is **stricter** than readers: it also flags unknown members (except `x-`
members) so that creators catch typos, rejects blank (whitespace-only) strings, C0 controls in
text (LF and CR allowed in LongText only), control characters (C0, DEL, C1) in URLs, header values and
catch-up sources, whitespace in catch-up sources, and include ids starting with
`epg-`. A file that passes the schema is always accepted by a reader supporting its version
(it may still produce warnings, for example `W_LIMIT` for merged headers, and rules the schema
cannot express — id uniqueness, references — are checked by the reader); a reader may accept files
that fail the schema.

## 11. Versioning and forward compatibility

1. `version` is the **major** version. Readers MUST reject documents with a `version` they do not
   support (`E_VERSION`), with a message telling the user to update the app.
2. **Additive changes do not bump `version`.** New optional members, new enum values and new
   section types are added to version 1 over time, and the schema at the same URL is updated.
3. Readers MUST **ignore unknown members** at every level.
4. Readers MUST handle **unknown enum values** as follows:

   | Where | Unknown value → reader behaviour | Code |
   |---|---|---|
   | `section.type` | Skip the section. | `W_UNKNOWN_TYPE` |
   | `query.from` | Skip the section. | `W_UNKNOWN_TYPE` |
   | `query.sort` | Use `source`. | `W_UNKNOWN_TYPE` |
   | `card.style` / `card.corner` | Use `auto` / `medium`. | `W_UNKNOWN_TYPE` |
   | `channel.type` | Treat as `tv`. | `W_UNKNOWN_TYPE` |
   | `include.type` | Skip the include. | `E_INCLUDE` |
   | `stream.mimeType` | Ignore the hint. | `W_UNKNOWN_TYPE` |
   | `drm.system` | Treat the stream as unplayable on this device (never play it without DRM). | `W_UNKNOWN_TYPE` |
   | `catchup.mode` | No catch-up for that channel; live playback unaffected. | `W_UNKNOWN_TYPE` |

5. A change that would make an existing valid document mean something different, or make a
   reader that follows these rules misbehave, is breaking and requires `version: 2` and a new
   schema URL (`tsiptv-source-v2.json`). The v1 schema stays published.

## 12. Security and privacy rules

| Rule | Requirement |
|---|---|
| No code | Readers MUST NOT execute, evaluate or render as markup any content of the document. |
| Schemes | Only `http` and `https` URLs (5.3). Readers MUST refuse to open any other scheme even if a future member contains one. |
| No outbound actions | The document cannot trigger navigation outside the app, installs, downloads to storage, or requests other than: fetching images, includes, guides, licence servers and streams when the user browses or plays. |
| No automatic network on import beyond the tree | Import fetches only the root document and its includes. Images load lazily when displayed. Streams load only when the user presses play. |
| Size and count limits | Sections 2, 4, 8 and 9. Readers MUST enforce them before building UI. |
| Image safety | Readers decode images downsampled to display size and cap each image download at 10 MiB. |
| Credentials | URLs and headers may carry tokens. Readers MUST NOT send document URLs, stream URLs or header values to analytics, crash reports or the app's own backend, and MUST NOT log them in release builds. |
| Local network | Allowed (users run home servers), no special treatment. |
| Adult flag | `meta.adult: true` — on the root or on any included TS IPTV Source — or `behaviorHints.adult: true` on any `stremio` include requires an explicit confirmation before import (9.3). |

## 13. Complete examples

- `web/public/examples/minimal-live.tsiptv.json`: minimal live list (one public test stream, one placeholder channel with headers, one placeholder radio station).
- `web/public/examples/vod-catalog.tsiptv.json`: public-domain movies and a series, with a custom appearance and layout, localized in English and Vietnamese.
- `web/public/examples/composed-includes.tsiptv.json`: a source composed of an M3U include, an XMLTV include, a Stremio addon include and a nested TS IPTV Source include, each placed in a section.

All streams in the examples are public test streams (Mux) or public-domain films on archive.org;
all other hosts are `example.com` placeholders or TS IPTV's own example files.
