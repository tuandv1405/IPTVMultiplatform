# QC report: F4 wave C, TS IPTV Source guide and validator (2026-09-28)

**Scope.** The TS IPTV Source guide (`/guides/tsiptv-source/`, source `web/guides-src/tsiptv-source.{vi,en}.html`) and its browser validator (`/guides/tsiptv-source/validate/`, `web/public/assets/tsiptv-validate.js`, which uses the vendored Ajv 8.17.1 `web/public/assets/vendor/ajv2020.min.js`). The work also added a hub card, related links, a sitemap entry, `site.css` additions and a README row.

**Branch.** `feature/sources-and-formats`. Nothing has been committed or deployed.

**Sources of truth.**
- [`prd-web-format-guides.md`](prd-web-format-guides.md): the acceptance criteria AC-W1 to AC-W16.
- [`tsiptv-source-format.md`](tsiptv-source-format.md): the normative spec.
- [`prd-tsiptv-source-format.md`](prd-tsiptv-source-format.md): the product spec, including the Follow-up #3 lead decisions.
- `web/public/schema/tsiptv-source-v1.json`: the JSON Schema.
- The F3 parser in `composeApp/src/commonMain/kotlin/tss/t/tsiptv/core/parser/tsiptv/`.
- Hand-off: [`handoff-f4c.md`](handoff-f4c.md).

**Process.** Five rounds: dev hand-off, then QC, then dev fixes, then QC again. Each QC round covered the scripts, the validator in real headless Chrome and the site.

## Verdict

The work is **release-ready apart from one Nit (R5-1) and the C1 release gate.** No Blocker, Major or Minor is open. The lead's round-5 rule, "PASS = zero open bugs including Nits", is **not met because of R5-1**. R5-1 is a one-line fix. Publication is gated on F3 in any case (C1).

## How it was tested

- **Automated checks.**
  - `python web/scripts/check_guides.py` passes: 12 guide pages, 23 site pages, 20 example files. It also checks that the generated pages are current and runs the Node validator test.
  - `node web/scripts/test_validator.js` passes: 3 examples, 4 fixtures in vi and en, all QC regression cases V1 to R4-2, and 10,000 channels in about 560 ms.
- **Real browser.** Chrome 64-bit, headless, driven by puppeteer-core, against `python -m http.server` serving `web/public`. The final suite has 272 cases, with vi-pane repeats.
  - **Content cases:**
    - the examples and all fixtures;
    - empty, whitespace-only, non-JSON, BOM, gzip, UTF-16 and Latin-1 input;
    - a file 1 byte over 5 MiB (and exactly 5 MiB), counted in UTF-8 bytes;
    - nesting 200,000 levels deep;
    - 1,000 to 10,000 findings, 15,000 references and 20,000 episodes;
    - prototype names (`constructor`, `__proto__`);
    - `$` replacement patterns;
    - zero-width, bidi, lone-surrogate and emoji text;
    - dates, times and offsets.
  - **XSS:** attacks in every echoed value: keys, language keys, `query.include`, `query.ids`, hero targets, include references and JSON error text.
  - **Network:** in every round, only the page assets plus one request for `/schema/tsiptv-source-v1.json`. Includes are never fetched.
  - **Safety:** no XSS (`window.__xss` stayed undefined, no dialog, no injected element) and no console errors.
  - **Other:** the file input, a failed schema load, the vi/en switch, and labels on the form controls.
- **Validator vs parser.** The validator was run on the 10 Kotlin fixtures in `composeApp/src/commonTest/kotlin/assests/tsiptv/` and the parser tests' inline cases. The results were compared with what the parser tests assert and with the parser code.
- **Content.** The page was compared section by section with the spec, both PRDs, the schema and the parser.
  - All 26 JSON snippets (13 per language) validate against the schema or the matching `$defs`.
  - vi and en differ only in translation.
  - Every host on the page is `example.com`/`.org`, one of the site's own URLs, or an allowlisted demo host (`test-streams.mux.dev`, `archive.org`).
- **Site.**
  - A crawl found 57 internal URLs, all 200.
  - There is no horizontal scroll at 320 or 360 px on the hub, both new pages (vi and en), and the JSON, M3U and Stremio pages.
  - Headings are in order, every table has `<th>` and is wrapped in `.scroll-x`, and there are no duplicate ids.
  - The validator page has `noindex` and is not in the sitemap.
  - Lighthouse (mobile) on the guide: Performance 97, Accessibility 100, Best practices 100, SEO 100.
- **Invisible characters.** No literal invisible characters remain in `web/public/assets` (JS, CSS and TXT), `web/scripts`, the guide sources, the generated pages, the schema or the examples.

## Rounds

| Round | Found | Result |
|---|---|---|
| 1 | V1–V8 (validator messages, dates, dropped-item ids, noise, gzip, `epg-N`, order), S1 (code wrapping at 360 px), C1–C18 (content; C1 = F3 not shipped) | All fixed except C1 (release gate) |
| 2 | **R2-1 Major**: a text with only invalid language keys was reported as Valid, although the app gives `E_META`/`E_ITEM_NAME` (a regression from the V4 fix). **R2-2 Minor**: slowdown that grew with the square of the findings (20 s for 10,000 channels). R2-3 to R2-10 Nits (prototype names, `$` patterns, DRM id claims, date wording, UTF-16, invisible characters in paths, triple messages, PRD wording) | All fixed |
| 3 | R3-1 to R3-5 Nits: `E_EMPTY` model without the parser's trimming/DRM/stremio rules; lowercase `t`/`z`; `updatedAt` over 64 characters; duplicate `keys` messages; literal invisible characters | All fixed |
| 4 | R4-1 Nit: padded dates reported as ignored. R4-2 Nit: time out of range explained as a letter-case problem | Fixed, plus a lead decision on RFC 3339 ranges (PRD Follow-up #3 row 5) |
| 5 | R5-1 Nit (below) | **Open** |

## Round 5 checks

- **R4-1 is verified.** Padded `updatedAt`, `releaseDate` (movie and episode), NBSP/U+3000 padding and a padded 70-character `updatedAt` each give the code-less note "the schema expects no surrounding spaces; TS IPTV accepts this value". A padded impossible date still gives `W_FIELD`.
- **R4-2 and the lead decision are verified.**
  - Each of the following gives `W_FIELD` "not a valid time…": `T24:00:00Z`, `T25:00:00Z`, `T10:99:00Z`, `T10:00:99Z`, `T23:59:60Z`, `T25:99:99Z`, `t25:00:00Z`, offset `+24:00` and offset `+07:60`.
  - Each of the following is valid with no message: `-23:59`, `+23:59`, `-00:00`, `23:59:59.999+07:00` and `T10:00:00z`.
  - Lowercase `t` together with `z` gets only the case note.
  - Each of the following gives `W_FIELD` for its shape: `+0700`, `+7:00`, a missing offset, `T10:00Z`, `.Z`, a space separator and a fullwidth digit.
  - `2023-02-29` gives "not a real calendar date"; `2024-02-29` is valid.
  - The vi messages are correct.
- **Round 4 vs round 5 diff.** Of 227 cases, exactly one result changed: a padded `updatedAt` went from `W_FIELD` to the "accepts" note, which is the R4-1 fix. The 45 new round-5 cases behave as described above.
- **Fixtures.** The validator's results on the Kotlin fixtures are identical to round 4, and they match the parser.
- **Invisible characters.** The scan is clean. **Crawl and 360 px layout:** clean.

## Open bug

**R5-1 (Nit): control characters in `meta.updatedAt` are not reported as `W_FIELD`, but the app reports them.**
- **Repro:** `"updatedAt": "\t2026-09-28T10:00:00Z\t"` or `"2026-09-28T10:00:00Z\n"`.
- **Validator:** only the code-less note "the schema expects no surrounding spaces; TS IPTV accepts this value".
- **App:** `optString` calls `shortText` (`TsiptvSourceParser.kt`, `shortText`). The value contains C0 characters, so the app replaces them with spaces, **adds `W_FIELD` "Control characters are not allowed here; they were replaced by spaces."**, then trims and accepts the value. The creator sees a `W_FIELD` in the app's Details that the validator did not predict.
- `releaseDate` is not affected: `optDate` only trims, and tab and LF are ECMA whitespace.
- **Fix:** in `dateStatus`, for `updatedAt` only, when the raw value contains a C0 character, emit `W_FIELD` "control characters are replaced by spaces" in place of the code-less note (the value is still accepted).

## AC-W matrix (wave C)

| AC | Result | Evidence and notes |
|---|---|---|
| W1 URLs return 200 | PASS locally | Both new URLs, with and without `?lang=en`. Repeat with `curl -I` after deploy. |
| W2 vi/en panes, toggle, `?lang=en`, remembered | PASS | The title and `lang` switch; the choice carries over between pages. |
| W3 eight anchors | PASS | `check_guides.py`. |
| W4 example blocks equal the files | PASS | `check_guides.py`. |
| W5 TS Source examples validate | PASS | Python `jsonschema` and Ajv. The desktop parser test is outside this wave. |
| W6 denylist, allowlist, manual review | PASS | Only allowlisted hosts; the page says the app ships no sources. |
| W7 360 px, no horizontal scroll | PASS at 320 and 360 px | Copy buttons were not tried on real Android or iOS devices (manual item). |
| W8 Lighthouse mobile ≥ 90 | PASS | TS Source page: Accessibility, Best practices and SEO all 100. |
| W9 Guides link in header and footer | PASS | `check_guides.py`. |
| W10 content types and CORS | PASS by config | `firebase.json` and local check. Repeat with `curl -I` after deploy. |
| W11 VS Code `$schema` | NOT TESTED | Manual item. |
| W12 import in the F1/F2/F3 builds | BLOCKED | F3 not shipped: `PlaylistImporter.kt:253` still throws `NEEDS_UPDATE` for `TSIPTV_SOURCE`. |
| W13 `noindex` pages absent from the sitemap | PASS | Validator (MonPlayer was covered in wave A). |
| W14 validator examples, fixtures, network | PASS | Real Chrome: examples valid, the expected paths for typo, duplicate id, `:` in an id and unknown include (`W_QUERY_REF`); only the schema is requested. |
| W15 "no channels" statement | PASS | Both pages, both panes. |
| W16 Vietnamese review and PO sign-off | PENDING | Manual item. |

## Deviations (from `handoff-f4c.md`)

| # | Deviation | Verdict |
|---|---|---|
| 1 | No separate rendered copy of the spec; the page is the public reference of version 1 | Accept. Every normative section is covered, and the exceptions to "passes the schema" are listed. |
| 2 | No screenshots of the examples | Accept. Add them once F3 ships (open item). |
| 3 | The validator uses its own messages, not the app's `source_issue_*` strings; schema findings have no code | Accept. |
| 4 | Only uncompressed JSON can be validated | Accept. gzip and UTF-16 input get clear messages. |
| 5 | Unknown `ids` and banner targets are warnings | Accept. |
| 6 | App UI labels come from the PRD strings | Accept. The labels exist in `strings.xml`; re-check against the F3 build. |
| 7 | Publish only with F3 | Accept. This is the release gate C1. |

## Open items at close

1. **C1, release gate.** The guide describes F3 behaviour that does not exist yet: preview, Source Home, About, refresh with ETag/304, include fetching, and the `E_INCLUDE`/`E_INCLUDE_CYCLE`/merged `W_LIMIT` fetch paths. The app rejects every TS IPTV Source today. Publish only with F3, then re-check the #add, #host, "what the viewer sees" and support-table text and the UI labels against the F3 build.
2. **R5-1 (Nit)**, above.
3. **Manual acceptance checks:**
   - AC-W11: VS Code `$schema` autocompletion and a typo error.
   - AC-W16: Vietnamese reviewer and PO sign-off.
   - AC-W7: copy buttons on Android Chrome and iOS Safari.
   - After deploy, `curl -I` for AC-W1 and AC-W10.
4. **Example screenshots** (PRD `#example`) after F3 ships.
5. **F3 follow-ups (parser; not web bugs):**
   - **F3 step 2:** `TsiptvRules` `DATE_TIME` must range-check hours, minutes, seconds and offsets per RFC 3339 (PRD Follow-up #3 row 5). Until then the validator is intentionally stricter than today's parser for values such as `T25:00:00Z`.
   - **PRD wording:** Follow-up #3 row 4 says `E_HEADER_FORBIDDEN` drops the stream; the parser drops only that header. The page is correct.
   - **Spec §1:** it now says the minimal file is ten lines (lead decision); confirm with the PO.
6. **Outside wave C (FYI):** `web/public/assets/contributor-backend.js:111` had a literal BOM. The lead escaped it (`/^\uFEFF/`) in round 4.

Evidence (logs, test cases, screenshots) is in the QC session scratchpad folder `qc-f4c/`. The main files are `browser_validator_r5.log`, `r5_view.txt`, `node_fixtures_r5.log`, `browser_site_r5.log` and `cases_r*.js`.

## Lead closure (after round 5)

- **R5-1 fixed by the lead** (round limit reached): `tsiptv-validate.js` `dateStatus` returns `controls` when a date-time contains a C0 character, shown as one `W_FIELD` (vi/en `controlsReplaced`), matching the parser's `shortText`. `test_validator.js` covers a trailing tab (vi) and LF (en); `test_validator.js` and `check_guides.py` pass.
- PRD Follow-up #3 row 4 already says `E_HEADER_FORBIDDEN` drops only the header.
- Wave C closes with zero open bugs; remaining items are the C1 release gate and the manual/F3 follow-ups listed above.

## C1 closure (2026-09-30)

**Scope.** The "F3 alignment" changes in `web/guides-src/tsiptv-source.{en,vi}.html` (listed in `handoff-f4c.md`). They were checked against:
- `handoff-f3.md`;
- `qc-report-2026-09-30-f3-tsiptv-source.md`;
- the shipped code;
- `values{,-vi}/strings.xml`.

The vi file has the same line numbers as en.

**Verdict: FAIL.** Most of the alignment is correct, but the guide still states 4 things the app does not do or that have not been verified (M1–M4 below). There are also 5 Nits. C1 can close once these guide fixes are made; they are text-only.

### Checks that pass
- **Scripts:** `check_guides.py` OK (12 guide pages, 23 site pages, 20 example files, generated pages up to date). `test_validator.js` OK (10,000 channels in 547 ms).
- **Site:** a crawl found 57 URLs, all 200. No horizontal scroll at 320 or 360 px on either page in either language. Headings, tables and ids are fine. I stopped the local server and headless Chrome.
- **UI labels, character by character:** every quoted label in both languages matches `strings.xml` exactly, including the typographic apostrophes, “Hủy” and the arrows.
  - The count-based strings, with numbers in place of N, match their format strings: `source_items_skipped`, `source_fetching_includes`, `source_open_homepage`, `source_include_stale`, `source_issues_count` and `source_last_refresh_failed`.
  - "Source added: 3 channels, 1 movie, 0 series" (vi “Đã thêm nguồn: 3 kênh, 1 phim lẻ, 0 phim bộ”) matches how `ImportDialogs.kt:107-116` builds it: `source_added` plus the plurals joined with ", ". It is followed by the `source_items_were_skipped` plural.
  - The only quoted phrases not in `strings.xml` are prose rather than UI labels: “needs a newer TS IPTV”, “upcoming”, the example section title “On air”, and the table value “Not yet verified”.
- **vi/en parity:** 577 lines in each file, with the same structure, codes and numbers. The count of quoted labels, `<strong>`, cells and ▶/▲/MENU is the same on every line. All 26 snippets validate.
- **These topics match the code:**
  - the import flow: preview, “Import details”, the 18+ checkbox, the include-18+ dialog where Cancel stores nothing, and the success message;
  - the Details format `path — CODE: explanation` (`SourceDialogs.kt:134-139`);
  - the About screen: phone menu, TV rail, Homepage, the TV fallback when there is no browser, include statuses, the problem count with “Problems found”, Refresh and Remove source;
  - the item-error cell of the validation table;
  - subtitles on Android: they start Off, a missing label shows the language name, and they are fetched without the stream's headers;
  - several streams **for channels** (`PlayerViewModel.kt:148-153`);
  - EPG matching (`TsiptvSourceResolver.kt:883`);
  - the unchanged items: “Loading i of n…”, the Home/Channels switch, the TV rail starting with Home, and caching/304.

### Mismatches (fix in both languages)
1. **M1: several streams for movies and episodes** (line 230, and the support row at line 555).
   - The guide says TS IPTV plays the first supported stream and that the viewer picks another under “Stream” in “Playback options”, for every item.
   - That is true only for channels. For a movie or episode, `playStream` clears the stream choice (`PlayerViewModel.kt:190`), so “Playback options” shows only subtitles. The streams are chosen on the detail page instead (`MediaDetailViewModel.kt:213`, `TsiptvDetailProvider.kt:80-90`).
   - On TV, ▲, MENU and captions on a movie therefore open subtitles only. Settings also opens the options, and the keys do this only when there is something to choose (`TvPlayerScreen.kt:225-250`).
   - **Fix:** limit the sentence and the table row to channels, and add "for a movie or episode, the detail page lists its streams".
2. **M2: DRM fallback on iPhone, iPad and desktop** (line 318).
   - "Skips streams with DRM and plays the item's next stream without DRM" is true only for channels (`firstPlayable` plus the playback preflight).
   - For movies and episodes, the detail picker and hero autoplay do not check DRM, so a DRM stream gives the “DRM-protected” error instead of falling back.
   - **Fix:** say this for channels only; for movies and episodes, tell the viewer to pick a stream without DRM on the detail page. Alternatively, file an F3 bug to make the picker and autoplay skip DRM streams when the device has no DRM.
3. **M3: desktop subtitles** (line 326; desktop cell in the support table at line 557).
   - `DesktopMediaPlayer.kt:141-148` passes only the subtitle URL to VLC (`addSlave`). Neither `label` nor `language` is passed, so the menu shows VLC's own track name. The statement "a subtitle without label shows the language name" is therefore false on desktop.
   - The stream headers are set as VLC media options (`:185-186`), which the subtitle input normally inherits, so "requested without the stream's headers" is unverified and probably false on desktop.
   - The F3 QC report says the desktop app was not run (T21 NOT TESTABLE on desktop; "VLC subtitles" not tested).
   - **Fix:** limit the label and no-headers sentences to Android, and mark desktop subtitles "Not yet verified".
4. **M4: the support table overstates desktop** (lines 554-558, note at line 565).
   - The desktop cells say "Supported" (import/layout, streams, subtitles) and "User-Agent and Referer only" (headers). The F3 QC report says the desktop app was not run (lines 109 and 153); only the shared logic ran, in `desktopTest`.
   - The code exists, so these cells should state that desktop has not been tested in the app.
   - **Fix:** use "Supported (not yet tested in the app)", or add a desktop sentence to the note at line 565.
   - The iOS cells are honest: "Not yet verified", DRM "Not supported", subtitles "Not yet".

### Nits
- **N1, EPG (line 329):** "matched by `epgId`, then by name" reads as a fallback after a failed `epgId`. In the code, name matching is used only when a channel has no `epgId` (`TsiptvSourceResolver.kt:398, 752`). Suggested: "…by `epgId`; a channel without `epgId` is matched by name…".
- **N2, hero autoplay (line 201):** an episode opens the **series** detail page. "Its detail page" is loose.
- **N3, failed root refresh (line 522):** Home shows “Couldn’t refresh this source. The saved copy is shown.” only after a Refresh the user starts. The automatic refresh does not show it (`PlaylistImporter.kt:271-277`); About shows the failure after any refresh.
- **N4, wording:** vi line 318 is missing "chỉ" (ClearKey is DASH only). en line 565 says "built" where vi says "compiled" ("biên dịch"); use "compiled" in en.
- **N5, Android DRM:** PlayReady plays only on devices that have a PlayReady CDM, which most phones don't; suggest "where the device supports it". The F3 QC report lists no DRM playback test.
- **FYI (code, not web):** the KDoc at `DesktopMediaPlayer.kt:137-139` says the system-language track is selected, which contradicts the code (subtitles start Off).

Evidence is in the QC scratchpad `qc-f4c/`: `labels.py` (label matching), `qparity.py`, `browser_site_c1.log`, `c1-shot-*.png`.


## C1 closed (lead, 2026-09-30)

M1–M4 and N1–N5 from the C1 closure check are fixed in both languages (see `docs/handoff-f4c.md` → "C1 closure fixes"). Lead re-checked lines 230, 318, 326, 554–558 and 565 and ran `check_guides.py` and `test_validator.js` (both OK). The guide now matches the shipped F3 on Android; desktop and iOS behaviour is marked not verified. The page may be published together with the F3 release. F3 code follow-ups FU1/FU2 are in `docs/qc-report-2026-09-30-f3-tsiptv-source.md`.

