# Hand-off — F4 wave C (TS IPTV Source guide + validator)

Date: 2026-09-28 · Branch: `feature/sources-and-formats` (not committed, not deployed) · Spec: [`prd-web-format-guides.md`](prd-web-format-guides.md) (`/guides/tsiptv-source/`, `/guides/tsiptv-source/validate/`), content from the normative [`tsiptv-source-format.md`](tsiptv-source-format.md), [`prd-tsiptv-source-format.md`](prd-tsiptv-source-format.md), `web/public/schema/tsiptv-source-v1.json` and the three examples. Earlier waves: [`handoff-f4a.md`](handoff-f4a.md), [`handoff-f4b.md`](handoff-f4b.md).

## What was built

**`/guides/tsiptv-source/`** (sources `web/guides-src/tsiptv-source.{vi,en}.html`), bilingual. It is a complete creator's guide and the public reference of version 1:

- **Opening**: the "no channels / no sources" callout, and links to the schema, the 3 examples and the validator.
- **`#what`**: purpose and the five design principles (data never code, links not content, easy by hand, forward compatible, same meaning as M3U).
- **`#quick`**: the minimal file (spec §4).
- **`#syntax`** covers the whole format:
  - the file itself (encoding, JSON, detection, 5 MiB, gzip) and the top-level table;
  - general rules (`x-` members vs map keys, `null`, unknown fields, trimming, code points, control characters);
  - `meta`; texts and translations (Text/LongText lengths, language tags, resolution order); ids, URLs, headers and colours;
  - `appearance` with card styles and the legibility (contrast) rules;
  - `layout`: section table, hero, queries (fields, AND/OR, sorts), default layout;
  - playable fields; channel, movie, series/season/episode, stream, DRM (with platforms), catch-up ("stored, playback coming later"), subtitles (Android and desktop, not iOS);
  - `epg` and `includes`: fields, the what-each-type-adds and item-id table, depth/cycles, per-refresh totals and per-type size caps, failures, adult content through includes;
  - a limits table, security and privacy rules, and the §11 fallbacks table with a note on version 2.
- **`#validation`**: all 22 codes (document, item, warning), each with when it happens and what the viewer sees. The friendly messages come from the PRD's string table; the "N items will be skipped" / Details / About-this-source behaviour follows PRD §3.
- **`#example`**: the three examples as `data-example` blocks with Download links, each with an explanation.
- **`#create`**: 7 steps, from the minimal file to a custom layout, with snippets, translation, validation and publishing (keep link and `id`, increase `revision`). Also a tip to add existing M3U playlists as an `m3u` include.
- **`#host`**: static HTTPS hosts and URL shapes, content type, gzip, caching (root refresh rule, include `refreshHours`, `If-None-Match`/`If-Modified-Since` with `ETag`/`Last-Modified`, `304` counts 0 towards the budget), CORS not needed, keep the link stable.
- **`#add`**: link or file, the preview contents (counts, "This source contacts these servers", skipped items, notice, 18+), the include loading line, Source Home (phone Home/Channels switch, TV rail), About this source, and the error dialog and nothing-loaded messages.
- **`#mistakes`**: 14 rows, covering the PRD list plus typos, comments, `epg-` ids, stremio URLs and catch-up spaces.
- **`#support`**: platform table.

**`/guides/tsiptv-source/validate/`** (`noindex`, not in the sitemap). The PRD condition is met, so the validator is **not cut**: Ajv 8.17.1 `ajv2020.min.js` from the npm package `ajv-dist` is **132 KB**, MIT, vendored at `web/public/assets/vendor/` with `ajv2020.LICENSE.txt`, and no CDN is used. The only change to the bundle is that its source-map comment was removed.

- **`web/public/assets/tsiptv-validate.js`**. The core `TsiptvValidator.validate(text, schema, Ajv, lang)` is a pure function that also runs in Node. The UI works in both panes and in the pane's language.
  - **Document checks**: size ≤ 5 MiB, JSON, root object, `format`, `version`, `E_EMPTY`.
  - **Schema**: Ajv with `allErrors`. Errors are mapped to our own vi/en messages per keyword and per `$defs` (found through Ajv's `verbose` parent schema), with branch noise removed.
  - **Hand pass**: duplicate ids (in the spec's order), duplicate season/episode numbers, `epg-` include ids, `query.include` pointing to an unknown, `xmltv` or non-stremio include (`W_QUERY_REF`), unknown `ids`/banner targets, `endYear < year`.
  - Output is "Valid" or "Not valid" plus Errors and Warnings lists of `path — message (CODE)`.
  - Network: only `/schema/tsiptv-source-v1.json`. Includes are never fetched. Files are read with `FileReader`; nothing is uploaded.
- **`web/scripts/test_validator.js`** runs in Node. It checks the 3 examples in both languages (valid, no warnings) and 4 fixtures in `web/scripts/validator-fixtures/` (typo, duplicate id, `:` in an id, unknown include reference), each reporting its expected path (AC-W14), plus the `E_NOT_JSON`, `E_NOT_SOURCE` and `E_EMPTY` checks. `check_guides.py` runs it when Node is installed; a negative test proved the hook works.
- **UI exercised in jsdom** (temporary install, outside the repo): the English pane is visible with `?lang=en` and the title switches; the vod-catalog example gives "Valid… No warnings"; `typo.json` gives 2 errors with the right paths; empty input gives the hint; the only fetch is the schema. It has **not** been tried in a real browser.

**Also**:
- **Hub**: TS IPTV Source card under Playlists; the "build my own list" row now offers M3U or TS IPTV Source.
- **JSON guide**: the TS IPTV Source link deferred in wave A.
- **Related links**: on the JSON, M3U and Stremio pages.
- **Sitemap**: `/guides/tsiptv-source/` added (the validator is excluded).
- **`site.css`**: validator styles and `h4` (additions only).
- **`build_guides.py`**: two PAGES rows. A slug with `/` maps to a source file named with `-` (`tsiptv-source-validate.*.html`).
- **README**: page rows and validator notes.

## Results

```
> python web/scripts/check_guides.py
check_guides: OK — 12 guide pages, 23 site pages, 20 example files, schema and links clean
> node web/scripts/test_validator.js
test_validator: OK — 3 examples valid, 4 fixtures report the expected paths (vi and en)
```

Local serve (`npx http-server web/public -p 5077 -c-1`): 84 URLs, all 200. The crawler also reported one 404, the known false positive: its regex matches escaped `src="…"` text in an XMLTV code block, not a real link. `/assets/vendor/ajv2020.min.js`, `/assets/tsiptv-validate.js`, the licence and both new pages were checked individually. Server stopped.

## AC notes (wave C)

| AC | How QC checks it |
|---|---|
| W1 | After deploy, `curl -I` both new URLs, with and without `?lang=en` → 200. |
| W3, W4 | `check_guides.py`: the 8 anchors, and the 3 example blocks equal the files. |
| W5 | The TS Source examples validate against the schema (Python `jsonschema` in `check_guides.py`, and Ajv in `test_validator.js`). |
| W8 | Lighthouse (mobile) on `/guides/tsiptv-source/` (a large page, about 140 KB of HTML). |
| W11 | Unchanged: `$schema` in the examples. |
| W13 | Validator page: `noindex` and absent from the sitemap. It is not a MonPlayer-style page, so it has normal links. |
| W14 | `node web/scripts/test_validator.js`. In a browser, paste each fixture from `web/scripts/validator-fixtures/` and watch the network tab: only page assets plus `/schema/tsiptv-source-v1.json`. |
| W16 | Vietnamese review and PO sign-off. |

## Deviations and decisions

1. **No separate rendered copy of the spec**. The PRD allows "an appendix or a sub-page"; instead, this page *is* the public reference (every normative section is covered). It says "This page is the public reference of version 1" and links the schema. The spec's reader-side details, such as code assignment rules and uniqueness order, are summarised rather than copied.
2. **No screenshots** of how the examples render (PRD: "taken after F3 ships"). This should be added once the F3 UI exists.
3. **Validator error messages** are our own sentences keyed on the Ajv keyword and `$defs` name, not the app's `source_issue_*` strings, which are not in the repo yet. Codes are shown only where the validator's own pass assigns one; schema findings have no code, because the schema is stricter than the app.
4. **The validator reads only uncompressed JSON** (a `.gz` file must be unzipped first). This is stated on the page.
5. **Unknown `ids`/banner targets are warnings**, although the app skips them silently (spec §10). They help creators and are clearly worded ("will be skipped", "will be decorative").
6. **App UI labels** for import ("Add IPTV Source", "Import from file", preview texts) come from F1/F3 PRD strings. Re-check them against the shipped F3 strings before publishing.
7. These pages describe **F3 behaviour** and must be published only with the F3 release.

## QC round 1 fixes (2026-09-28)

**Validator** (`web/public/assets/tsiptv-validate.js`; the core between `fmt` and the browser UI was rewritten, the UI and string tables were kept):

| # | Fix |
|---|---|
| V1 | Failed `oneOf`/`anyOf` branch `type` errors are dropped only when they do not fit the value's actual type. A channel written as a string now says "must be an object"; the playable `oneOf` is ignored for non-objects. When a value fits no branch (for example a number for a Text), the result is one merged message: "must be a string / an object". |
| V2 | `date` and `date-time` formats use a real calendar check with leap years (same as `TsiptvRules.isFullDate`/`isDateTime`). Impossible dates are **warnings `W_FIELD`** ("TS IPTV ignores this field"), not errors. |
| V3 | Uniqueness mirrors what the app keeps. An item claims its id only if it would be kept: valid id, a name, and exactly one of `url`/`streams` with a valid URL (`streams`: at least one valid entry). Episodes also need a number in range. A series needs a valid season with a kept episode; a dropped series releases its episodes' ids. Duplicate season and episode numbers are checked only among kept entries. |
| V4 | `E_EMPTY` suppresses the root `anyOf` branch errors. A URL `format` error is dropped where the URL-pattern message exists. The `epg-` include check keeps only the hand-written `E_INCLUDE`. An invalid key in a text object (`x-note`) is one warning `W_TEXT` ("ignored"), with no parent "does not match" message when only key problems exist. Explained clearkey `anyOf` branches are dropped. |
| V5 | Specific vi/en messages for: a stremio include URL not ending in `/manifest.json`; headers on a stremio include; `keys` with widevine/playready; clearkey with neither `licenseUrl` nor `keys`. |
| V6 | `E_NOT_JSON` gives a localized message with the line and column (derived from the engine's position), never the browser's English text. A gzip body (`1F 8B`, or a leading U+FFFD after text decoding) gives "looks compressed (gzip), unzip it first". |
| V7 | `epg-<index>` ids are registered as `xmltv` includes, so `query.include: "epg-1"` gets the same `W_QUERY_REF` message as the app. |
| V8 | Findings are sorted by document order (member position, then array index; a missing required field sorts after its siblings). |
| — | The "Valid" text no longer promises acceptance: "TS IPTV checks its includes when you import it." |

`web/scripts/test_validator.js` now also covers V1, V2 (including 2024-02-29 staying valid), V3 (rtmp channel then a valid channel with the same id: no `E_DUPLICATE_ID`), V4 (epg-only file, URL with a space, `epg-` include id, `x-note` key), V6 (localized message, gzip) and V7.

**Site.** S1: `td code { white-space: nowrap; overflow-wrap: normal; }` (tables stay inside `.scroll-x`).

**Content** (`web/guides-src/tsiptv-source.{en,vi}.html`, both languages identically; the validator page and the JSON guide too):

- **C2**: detection looks for `format` in the first 64 KiB (put it near the top). "Passes the schema ⇒ always accepted" is softened to "almost always", with the exceptions listed: over 5 MiB, `format` after 64 KiB, impossible dates, duplicate ids and references, failing or cyclic includes (`E_INCLUDE`, `E_INCLUDE_CYCLE`, `E_EMPTY`). The validator page links to that list.
- **C3**: the series fields are listed explicitly; no `directors`, `releaseDate`, `runtimeMinutes` or `subtitles`.
- **C4**: step 3 adds the episode `name`.
- **C5**: the rtmp row says the stream is dropped (`E_URL`) and a single-url item is skipped (`E_NO_STREAM`); after moving to an m3u include, playback depends on each platform's player.
- **C6**: conditional GET applies to the file itself and to each include.
- **C7**: a non-boolean `meta.adult` counts as adult (meta table and includes bullet). Refresh rule added: new content that would make an unconfirmed source adult is held back until the user confirms in "About this source".
- **C8**: the control-characters rule no longer reads as if a line break in Text were an error. `W_TEXT` also covers replaced line breaks and control characters in one-line texts and more than 30 languages.
- **C9**: `W_QUERY_REF` also covers a `catalog` query whose include is not `stremio`.
- **C10**, all done:
  - `E_ITEM_ID` now says "missing or out of range";
  - the example message is marked illustrative;
  - catch-up `source` is required, and the whitespace rule applies, only for `default`/`append` (also in the mistakes row);
  - "eight lines" is corrected to "ten lines";
  - `:` in an include id gives `E_INCLUDE`;
  - the validation table is regrouped: `E_URL`, `E_HEADER_FORBIDDEN` and `E_DRM` have their own cell saying they are not counted in "N items will be skipped";
  - the example text now says "His Girl Friday has…";
  - the `auto` card rule for Stremio `posterShape`, the hero ignoring `card`, `autoplay` limited to channels, movies and episodes, and the first-one-wins order (channels → movies → series with episodes → includes) are added;
  - the JSON guide now says "M3U or TS IPTV Source" for headers, DRM and catch-up.

**Spec inconsistency found (not a parser disagreement; please confirm with the PO):**
- Spec §1 says "The smallest valid file is **eight** lines", but its own §4 example (and the page's quick start) is **ten** lines. The page now says ten.
- The `E_URL`/`E_HEADER_FORBIDDEN`/`E_DRM` grouping follows QC's statement that they are not counted in "N items will be skipped"; the F3 PRD §3 does not say this explicitly.

Results after the fixes: `check_guides: OK — 12 guide pages, 23 site pages, 20 example files, schema and links clean`; `test_validator: OK — 3 examples valid, 4 fixtures report the expected paths (vi and en), QC cases V1–V7 pass`; the jsdom UI run is unchanged (valid, typo and empty cases correct; the only fetch is the schema). The release gate C1 is unchanged: the pages stay unpublished until F3 ships.

## QC round 2 fixes (2026-09-28)

| # | Fix (`web/public/assets/tsiptv-validate.js`) |
|---|---|
| R2-1 (Major) | A text object whose only entries have invalid language keys is no longer "Valid". The parent message is removed only when an entry with a valid tag and a non-blank value remains (`usableEntry`). Otherwise: `meta.name` → error `E_META`; a required `name` (channel, movie, series, episode) → error `E_ITEM_NAME` (the item does not claim its id); an optional text → warning `W_TEXT` "TS IPTV ignores this field". New: when the file has items or includes but the app would drop every one of them, the validator adds `E_EMPTY` ("Every channel, movie, series and include would be dropped…"), as the app does. |
| R2-2 | The "only key problems below?" dedupe is linear. Each finding's ancestor paths are indexed once (`keyBelow`/`realBelow`), replacing the per-finding scans. Node timing guard: 10,000 channels with an `x-note` key each validate in about 0.6 s (the limit is 2 s). |
| R2-3 | The id, include, season-number and episode-number maps use `Object.create(null)`; include lookups are own-property. `query.include: "constructor"` is now flagged. |
| R2-4 | `fmt` uses replacer functions, so `$&`/`$'` in values stay literal. |
| R2-5 | `playable()` also checks DRM (`drmOk`). A stream's own `drm`, else the item's, must be valid: `system`; widevine/playready need `licenseUrl`; clearkey needs `licenseUrl` or 1–20 hex keys; a malformed `licenseUrl` or `keys` is invalid; an unknown system is kept. A single-url item with invalid DRM is dropped and does not claim its id. |
| R2-6 | Date fields: "not a real calendar date" appears only when the shape is right but the day does not exist. Otherwise the message is "must be a date-time such as 2026-09-27T10:00:00Z" or "must be a date YYYY-MM-DD". Both are `W_FIELD` warnings, including the schema's `…T` pattern on `updatedAt`, because the app drops the field. |
| R2-7 | A leading U+FFFD gives "looks compressed, or it is not UTF-8 (for example saved as UTF-16)". `1F` still gives the gzip message. |
| R2-8 | Displayed paths escape C1 controls, soft hyphen, bidi marks and embeddings, zero-width characters, word joiners and BOM as `\uXXXX` (JSON-style, so ordering still works). |
| R2-9 | "needs url or streams" replaces the two branch `required` lines: one message per item. |
| R2-10 | Page wording: `E_HEADER_FORBIDDEN` now says "only that header is dropped" (vi: "chỉ header đó bị bỏ"). |
| — | Literal U+FEFF/U+FFFD characters that an editing tool had written into the source are now `\u` escapes; the file has no invisible characters. |

`test_validator.js` adds:
- R2-1: channel `{"EN":…}` as the only item gives `E_ITEM_NAME` plus `E_EMPTY`; movie `{"vi_VN":…}` gives `E_ITEM_NAME`; `meta.name` `{"English","EN"}` gives `E_META`; an optional description gives `W_TEXT`; a mixed valid-plus-invalid text stays valid; an item with an unusable name does not claim its id.
- R2-2 timing, and cases for R2-3 through R2-9.
- The V4 URL-with-space case now keeps a second valid channel, because a single dropped channel now correctly also gives `E_EMPTY`.

Results: `test_validator: OK — 3 examples valid, 4 fixtures report the expected paths (vi and en), QC cases V1–V7 and R2-1…R2-9 pass (10,000 channels: 591 ms)`; `check_guides: OK — 12 guide pages, 23 site pages, 20 example files, schema and links clean`. In jsdom the typo fixture now also shows `E_EMPTY` (its only channel is dropped), which matches the app.

## QC round 3 nits (2026-09-28)

| # | Fix |
|---|---|
| R3-1 | The kept-item model now does what `TsiptvSourceParser` does. URLs (item, stream, `licenseUrl`, include), the include `type` and `drm.system` are trimmed with ECMA-262 whitespace (`String.prototype.trim`, the same set as `trimJs`). A blank or missing `system` makes the DRM invalid. An unknown system is kept. Checks run in the parser's order: system, then `licenseUrl` (E_URL), then widevine/playready need `licenseUrl` and their `keys` are ignored (W_FIELD in the app, so it does not count for keeping), then clearkey `keys` (1–20 hex pairs) or `licenseUrl`. A stremio include whose URL fails `isStremioManifestUrl` (ported: the path before `?`/`#` must end with `/manifest.json`) is dropped and counts towards `E_EMPTY`. |
| R3-2 | Date-times the app accepts (its `DATE_TIME` regex, lowercase `t`/`z` allowed, plus the calendar check) are never reported as "ignored". If the schema's uppercase-`T` pattern fails, the message is a warning without a code: "the schema expects the form 2026-09-27T10:00:00Z (uppercase T and Z); TS IPTV accepts this value". `…T10:00:00z` passes the schema and gets no message. |
| R3-3 | `meta.updatedAt` longer than 64 characters (trimmed, in code points) gives one warning `W_FIELD` "longer than 64 characters; TS IPTV ignores this field", like the parser's `optString(maxLength = 64)`. Other findings at that path are dropped. The schema is unchanged. |
| R3-4 | Widevine or PlayReady with `keys`: only "keys are only for clearkey…" is shown; the key-pattern errors under that `keys` object are dropped. |
| R3-5 | `web/scripts/test_validator.js` writes U+FFFD, U+200B and control characters as `\u` escapes. Neither `test_validator.js` nor `tsiptv-validate.js` contains literal invisible characters (checked by script). |

New tests:
- R3-1: padded stream URL kept; a padded kept item claims its id; padded `system`/`licenseUrl` kept; blank `system` gives `E_EMPTY`; widevine with `keys` kept; padded include `type` kept; stremio include without `/manifest.json` gives `E_EMPTY`; a manifest URL with a query is kept.
- R3-2: lowercase `t`/`z` gives no W_FIELD and shows the schema-only note; lowercase `z` alone gives no message.
- R3-3: an `updatedAt` over 64 characters gives exactly one W_FIELD.
- R3-4: one message under `drm.keys`.

Results: `test_validator: OK — 3 examples valid, 4 fixtures report the expected paths (vi and en), QC cases V1–V7, R2-1…R2-9 and R3-1…R3-4 pass (10,000 channels: 608 ms)`; `check_guides: OK — 12 guide pages, 23 site pages, 20 example files, schema and links clean`; `build_guides.py --check` clean; jsdom UI unchanged. `contributor-backend.js` was not touched.

## QC round 4 nits (2026-09-28)

| # | Fix |
|---|---|
| R4-1 | A date value is decided the way the app reads it. It is trimmed with ECMA-262 whitespace (for `updatedAt`, C0 controls become spaces first, as in `optString`/`shortText`; `optDate` trims). A value that is valid after trimming gets a code-less note: "the schema expects no surrounding spaces; TS IPTV accepts this value". An invalid value is still `W_FIELD`: padding does not hide an impossible date. The R3-3 length check uses the same normalised value. |
| R4-2 | Lead decision: date-times follow spec §5 (RFC 3339). Hours above 23, minutes or seconds above 59, and the leap second `:60` give `W_FIELD` "not a valid time (hours 00–23, minutes and seconds 00–59); TS IPTV ignores this field", matching the parser fix planned in F3 step 2. Lowercase `t` still gets only the case note; `…T…z` passes with no message. Offsets are range-checked too (PRD follow-up #3 row 5): offset hours above 23 or minutes above 59 give the same `W_FIELD` message. |

Implementation: one `dateStatus(kind, value)` returns `ok`, `case`, `spaces`, `shape`, `day` or `time`. It drives the Ajv `date`/`date-time` formats (strict: valid exactly as written; RFC 3339 allows a lowercase `z`) and the messages for both the `format` and the `pattern` errors, so a value gets exactly one message. The old `appAcceptsDateTime`/`DATE_TIME_SHAPE` code is gone.

New tests:
- R4-1: padded `updatedAt` and `releaseDate` each give one code-less note; a trailing tab is accepted; a padded impossible date still gives `W_FIELD`.
- R4-2: `T25:00:00Z`, `T10:99:00Z`, `T10:00:99Z`, `T23:59:60Z` and `T25:99:99Z` each give one `W_FIELD` "not a valid time"; lowercase `t`/`z` keeps the case note; `23:59:59.999+07:00` is valid.

Results: `test_validator: OK — … QC cases V1–V7, R2-1…R2-9, R3-1…R3-4 and R4-1…R4-2 pass (10,000 channels: 567 ms)`; `check_guides: OK — 12 guide pages, 23 site pages, 20 example files, schema and links clean`; `build_guides.py --check` clean; jsdom UI unchanged; no literal invisible characters in either JS file.

**Offsets (lead confirmation, PRD follow-up #3 row 5):** the date-time offset is range-checked too. Offset hours above 23 or minutes above 59 give the same `W_FIELD` "not a valid time" (the message now mentions "offset up to ±23:59"). Tests: `+24:00` and `+07:60` give `W_FIELD`; `-23:59` is valid.

## F3 alignment (2026-09-30)

F3 passed QC, so release gate C1 is closed. I re-checked the guide (`web/guides-src/tsiptv-source.{en,vi}.html`) against `docs/handoff-f3.md` (its "Web guide differences" and every QC round section), `docs/qc-report-2026-09-30-f3-tsiptv-source.md`, and the F3 strings in `values{,-vi}/strings.xml`. UI labels are quoted exactly from those strings (en/vi), including their typographic apostrophes (“Can’t add this source”, vi “Hủy”). The validator page needed no change.

| Topic | Guide now says |
|---|---|
| Several streams | The first supported stream plays. The viewer picks another under “Stream” / “Luồng” in “Playback options” / “Tuỳ chọn phát”: on phone the player's settings button; on TV ▶ on channels, ▲ on movies and episodes, or the MENU or captions key. The support table row mentions “Playback options”. |
| Subtitles | They start “Off” / “Tắt”; the viewer picks one under “Subtitles” / “Phụ đề”. A subtitle without `label` shows the language name. Subtitle files are requested without the stream's headers (also added to the Headers rule). Android and desktop only. |
| EPG | Matched by `epgId`, then by name, ignoring case, accents and spaces (`epgId` row and the `epg` paragraph). |
| Hero `autoplay` | A channel plays at once. A movie or episode opens its detail page and starts playing, so Back returns to the detail page. |
| DRM on iOS/desktop | Streams with DRM are skipped for the next stream without DRM. Only if there is none does the app show “This channel is DRM-protected. DRM is not supported on this device.” |
| Import | The preview dialog “Add this source?” / “Thêm nguồn này?”. **Details** opens “Import details” / “Chi tiết nhập”. The adult checkbox is “I am 18 or older” / “Tôi đủ 18 tuổi trở lên”. The include-18+ dialog text is quoted; **Cancel** stores nothing. The success message is quoted (“Source added: 3 channels, 1 movie, 0 series”, then “N items were skipped” / “Đã bỏ qua N mục”). |
| Details format | `path — CODE: explanation` (replaces `path — message`). |
| About | Phone: the Source Home menu; TV: a rail item. It shows **Homepage** / **Trang web** with “Open … in your browser?” (a TV without a browser shows the link as text), include statuses quoted (“Up to date”, “Couldn’t refresh — showing the copy from …”, “Couldn’t load”, “Held back until 18+ is confirmed” with **Confirm**), “Problems found in the last refresh: N” with Details titled “Problems found” / “Các vấn đề tìm thấy”, and Refresh / Remove source. |
| Failed root refresh | New paragraph: Home shows “Couldn’t refresh this source. The saved copy is shown.” and About adds “Last attempt: …”; retry on the normal schedule. |
| Validation table | The item-error cell describes the post-import success message and the refresh count and “Problems found”. |
| Support table | The iOS cells for import/layout, streams and headers now say “Not yet verified” / “Chưa kiểm chứng” (iOS was never compiled or tested). DRM stays “Not supported” (by design) and subtitles “Not yet”. A note under the table explains this. |

Unchanged, because they already matched F3: the preview contents and “N items will be skipped” (with E_URL / E_HEADER_FORBIDDEN / E_DRM listed but not counted), “Loading i of n…”, the Home/Channels switch, the TV rail starting with Home, adult content held back until confirmed, the error messages table, the caching and 304 text, the default layout, and the F1 labels (“Add IPTV Source”, “Source Link”, “Add IPTV”, “Import from file”, re-checked in `strings.xml`). No screenshots were added.

Results: `build_guides.py` rebuilt `guides/tsiptv-source/`; `check_guides: OK — 12 guide pages, 23 site pages, 20 example files, schema and links clean`; `test_validator: OK — … (10,000 channels: 542 ms)`.

**C1 closure fixes (2026-09-30), en and vi:**
- **M1 (several streams):** "first supported stream + Playback options" now applies to channels only. Movies and episodes pick their stream on the detail page, and their options offer subtitles only. On TV the options open only when there is something to choose (▶, MENU, captions or Settings). The support row is updated to match.
- **M2 (DRM):** the skip-to-next-stream fallback applies to channels only. A DRM stream picked for a movie or episode shows the DRM-protected message, and creators are advised to add a stream without DRM.
- **M3 (subtitles):** "language name when there is no label" and "no stream headers" now apply to Android only. Desktop subtitles load, but their labels and header behaviour are not yet verified; the desktop subtitle cell says "Not yet verified".
- **M4 (desktop):** the note under the table gains a desktop sentence (the features are built but the desktop app is untested).
- **N1:** name matching applies only to channels without `epgId`.
- **N2:** an episode opens its series' detail page.
- **N3:** the Home notice appears after a Refresh the user starts; About shows "Last attempt" in every case.
- **N4:** vi "chỉ" added (ClearKey); "built" / "build" used consistently in the note.
- **N5:** PlayReady plays only on Android devices with a PlayReady CDM.

Results: `check_guides` OK; `test_validator` OK; both source files have 576 lines, so vi and en line numbers still match.
