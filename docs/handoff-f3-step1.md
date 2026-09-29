# Hand-off — F3 step 1: TS IPTV Source parser and validation

Status: **done, not committed** · Branch: `feature/sources-and-formats` · Date: 2026-09-28
Scope: PRD `prd-tsiptv-source-format.md` implementation plan step 1 (roadmap §1 "F3 step 1 may start
once F1's models are merged"). Normative spec: `tsiptv-source-format.md` ("§" below refers to it).

No file outside `core/parser/tsiptv/**`, its tests, `assests/tsiptv/**` and this note was touched.
No network, DB, DI or UI code. Room is untouched (v6 remains F3 step 3's).

## What was built

| File (`composeApp/src/commonMain/kotlin/tss/t/tsiptv/core/parser/tsiptv/`) | Content |
|---|---|
| `TsiptvSourceModels.kt` | Typed, validated document tree (all `@Serializable`): `TsiptvSourceDocument`, `TsiptvMeta`, `TsiptvAppearance`/`Background`/`CardStyle`, `TsiptvLayout`/`Section`/`Query`/`HeroItem`/`CatalogRef`, `TsiptvChannel`, `TsiptvMovie`, `TsiptvSeries`/`Season`/`Episode`, `TsiptvStream`, `TsiptvSubtitle`, `TsiptvEpgLink`, `TsiptvInclude`, enums. Reuses F1's `DrmSpec`, `ClearKey`, `CatchupSpec`, `StreamMimeTypes`. `TsiptvChannel.toIPTVChannel()` maps to the app's `IPTVChannel`. |
| `TsiptvSourceParser.kt` | `TsiptvSourceParser.parse(bytes|text, documentUrl?, maxBytes)` → `TsiptvParseResult.Success(document, report)` / `Failure(report)`. Bounded gunzip, BOM, 5 MiB, strict RFC 8259, every §10 rule. |
| `ValidationReport.kt` | `TsiptvIssueCode` (all 22 codes of §10, each with its level), `TsiptvIssueLevel` (DOCUMENT / ITEM / WARNING), `TsiptvIssue(code, path, message, droppedItems)`, `TsiptvValidationReport` (`documentErrors`, `itemErrors`, `warnings`, `isRejected`, `primaryDocumentError`, `skippedItemCount`, `details(100)`, `detailLine()` = `path — message`). |
| `LocalizedTextResolver.kt` | `LocalizedText(values, defaultLanguage)` + `resolve(uiLanguage)`; resolver with the §5.2 order; accepts `zh-rCN` / `zh_CN`. |
| `TsiptvRules.kt` | `TsiptvLimits` (every size/count constant of §2/§4/§7/§8/§9 and the PRD per-type include sizes), `TsiptvRules` (Id, item ref, HttpUrl, language tag, colour, header name/value, forbidden headers, MIME syntax, hex32, dates, Stremio manifest URL), `TsiptvIds` (`namespaced`, `sanitizeForeignId` for M3U ids). |
| `TsiptvIncludeGraph.kt` | `TsiptvIncludeGuard` (depth ≤ 3, cycles, ≤ 20 includes in the tree, 50 MiB per refresh, merged 20,000/5,000/1,000 items), `TsiptvIncludeContext` (depth, URL path, chained id path, `namespaced()`, issue path prefix), `TsiptvIncludeTreePlanner.plan(root, rootUrl, load)` (depth-first walk over an injected suspend loader), `TsiptvUrlKey`, `TsiptvHosts.contacted()` (preview host list, AC-T22). |
| `TsiptvAppearanceResolver.kt` | §6 legibility (`W_CONTRAST`, WCAG 2.1 ratio), `TsiptvCardResolver` (§6.1 per-field inheritance + `auto` per item kind), `TsiptvDefaultLayout.build(poolSummary)` (§7.5, titles as `TsiptvBuiltInTitle`). |

### Model conventions (what step 2+ can rely on)

- Shorthands are expanded: `url` → one `TsiptvStream`; item `headers` are merged under each stream's
  (stream wins per name, case-insensitive); item `mimeType` is the fallback of each stream; item `drm`
  applies to streams without their own (a stream's `drm` replaces it entirely). `streams` is never empty.
- Invalid optional fields are `null`/empty; defaults are filled in (`type=tv`, `refreshHours=24`,
  `catchup.days=7`, `imageDim=0.6`, `sort` = `recent` for `continueWatching` else `source`, row `seeAll=true`).
- Unknown enum values already have their §11 fallback. Unknown `drm.system` → `DrmSpec(system=null,
  unsupportedReason=…)`, i.e. `isSupported == false`: the player must refuse it (never plays in clear).
- Plain-string Text values are stored under `meta.language`; `LocalizedText.defaultLanguage` travels with
  every value so included items keep their own document's fallback language after merging.
- Ids are the document's own. Pool ids: `context.namespaced(itemId)` (`cinema:big-buck-bunny`, chained
  for nested includes). M3U include items: `context.namespaced(TsiptvIds.sanitizeForeignId(tvgIdOrSlug))`.
- `layout == null` means "use `TsiptvDefaultLayout`" (absent, invalid, or every section skipped).
- Messages never contain URLs, header values or keys from the document (§12).

## Public API for step 2 (resolver)

```kotlin
val result = TsiptvSourceParser.parse(bytes, documentUrl = url)   // or parse(text, url)
when (result) {
    is TsiptvParseResult.Failure -> result.report.primaryDocumentError   // → source_error_* key
    is TsiptvParseResult.Success -> { result.document; result.report }  // preview + Details
}

// Include tree (step 2 supplies the fetch; only tsiptv-source includes are loaded here):
val tree = TsiptvIncludeTreePlanner.plan(document, rootUrl, rootBytes) { include, context ->
    fetchBytes(include.url, include.headers)?.let { TsiptvFetchedDocument(TsiptvSourceParser.parse(it, include.url), it.size.toLong()) }
    // null = fetch failed → E_INCLUDE at includes[i]
}
tree.root       // root after the merged item limits
tree.flatten()  // nodes: include, context (depth, includePath, namespaced()), status, document (already trimmed)
tree.report     // tree-level issues: E_INCLUDE(_CYCLE), W_LIMIT, nested item issues prefixed "[cinema] …"
tree.adultCheck(stremioAdultIncludePaths)   // TsiptvAdultCheck(rootIsAdult, adultIncludePaths).requiresConfirmation
tree.guard      // keep using it for m3u / xmltv / epg / Stremio manifest fetches of the same refresh

// Guard API (the planner uses the same calls; entries are includeEntry(i) / epgEntry(i)):
guard.admit(parentContext, include, index)               // Accepted(context) | Refused(issue)
guard.recordRootFetched(bytes)                           // root counts towards 50 MiB (304 = 0)
guard.canFetch / guard.remainingBudget                   // check before a download
guard.recordFetched(parent, entry, bytes)                // false → over budget, E_INCLUDE recorded, keep last good copy
guard.recordFetchFailed(parent, entry)                   // network/timeout/non-2xx/size cap/gzip → E_INCLUDE
guard.recordBudgetSpent(parent, entry)                   // not fetched, budget spent → E_INCLUDE
guard.admitItems(TsiptvPoolKind.CHANNEL, n, context)     // how many of n may be merged (W_LIMIT beyond)
guard.admitDocumentItems(document, context)              // same for a whole nested document
guard.recordParse(parent, index, context, result)        // nested report (meta/appearance/layout filtered) / rejection → E_INCLUDE
```

Per-type download caps are in `TsiptvLimits` (`MAX_DOCUMENT_BYTES`, `MAX_M3U_INCLUDE_BYTES`,
`MAX_XMLTV_INCLUDE_BYTES`); `parse(bytes, maxBytes = …)` accepts a custom cap.

## Required changes outside this folder (not done, by instruction)

| Where | Change | Step |
|---|---|---|
| `usecase/playlist/PlaylistImporter.kt:253` | Replace `TSIPTV_SOURCE -> throw NEEDS_UPDATE` with the source path: `TsiptvSourceParser.parse(content, url)`; `Failure` → error dialog from `report.primaryDocumentError` (`E_ID`/`E_META` → `source_error_missing_fields`); `Success` → preview. Pass the **URL** so a self-include is caught. | 4 |
| Download path (`KtorNetworkClient` / importer) | Today the whole body is downloaded before the parser can apply the 5 MiB limit. Stop reading at `TsiptvLimits.MAX_DOCUMENT_BYTES + 1` (and at the per-type caps for includes), or pass raw bytes to `parse(bytes)`, which gunzips with a bound (`IPTVParserFactory.decode` inflates without a bound). Add `ETag`/`Last-Modified`. | 2 |
| `core/parser/IPTVParserFactory.kt` | **No change needed**: F1 already detects `TSIPTV_SOURCE` with the §3 regex on the first 64 KiB, before the JSON branch (verified by `TsiptvExamplesTest`). `createParser(TSIPTV_SOURCE)` keeps throwing — a source is not an `IPTVParser`; route by format instead. Optional: call `TsiptvSourceParser.looksLikeSource()` there to keep a single copy of the regex. | — |
| Koin (`di/AppModule.kt`) | Nothing required: parser, guard factory and resolvers are stateless objects / per-refresh instances. If a seam is wanted for tests: `single { TsiptvSourceParser }`. | 2 |
| Room v6 (step 3) | Store `meta`/`appearance`/`layout`/`streams`/`subtitles` with `Json.encodeToString` of the model classes (round-trip tested for streams). `reportJson` = `report.details(100)`. Channel ids `ts:{playlistId}:{context.namespaced(id)}`; `channel.name` = `name.resolve(defaultLanguage)`, `nameJson` = `name`. | 3 |
| Strings (step 10) | The PRD says "17 `source_issue_*` keys", but §10 defines **22** codes (7 document, 9 item, 6 warnings). One key per `TsiptvIssueCode` means 22 keys. | 10 |
| Player | Header values may contain non-ASCII (the spec allows it); Android `PlayerHttpDataSource` already applies `HeaderSuffixParser.sanitize`, so nothing to do there. Desktop/iOS use only UA/Referer. | — |

## Deviations and interpretations (please confirm with the PO)

1. **Spec conflict — unknown `include.type`:** §10 lists it under `W_UNKNOWN_TYPE`, §11 says "skip the
   include (`E_INCLUDE`)". Implemented as **`E_INCLUDE`** (include dropped, counted as skipped), matching
   §11, the `E_INCLUDE` row of §10 ("type") and AC-T4.
2. **`W_UNKNOWN_TYPE` for every §11 fallback**, not only the four named in §10: also `query.sort`,
   `card.corner`, `channel.type`, `stream.mimeType` (valid syntax, not recognised), `drm.system`,
   `catchup.mode`. Behaviour is exactly §11; these are warnings only.
3. **Codes where §10 has none**: season/episode `number` missing or out of range → `E_ITEM_ID`; duplicate
   season or episode number → `E_DUPLICATE_ID`; series without seasons, season without episodes, or none
   left → `E_NO_STREAM`; an item that is not an object → `E_ITEM_ID`; a stream that is not an object →
   `E_URL`; a malformed `licenseUrl` → `E_URL` (stream dropped), a missing one → `E_DRM`; an invalid EPG
   URL → `E_URL`; invalid **header value** (CR/LF/control chars, > 4,096, not a string) → `W_FIELD`
   (`E_HEADER_FORBIDDEN` is kept for names, as §10 says); structurally broken sections (no type, no
   query, no `from`, wrong shapes) → `W_FIELD`, section skipped; a banner with an invalid image → `E_URL`,
   banner dropped; localized entries that are not non-empty strings, or past 30 → `W_TEXT`; line breaks in
   a (non-Long) Text → replaced by spaces + `W_TEXT`.
4. **Uniqueness order** is fixed: channels → movies → series (with their episodes) → includes, not raw
   JSON member order. Dropped items do not claim their id; a dropped series releases its episode ids.
5. **`epg-<index>`**: index = 0-based position in `epg` as written. Implicit EPG ids are not in the
   uniqueness set; a query naming one gets `W_QUERY_REF` (it is xmltv). EPG links do not count towards the
   20-include tree limit (they have their own limit of 10 per document).
6. **Depth counts every include type**: the includes of a depth-3 document (any type) are refused with
   `E_INCLUDE_CYCLE` (AC-T10 "depth 4 is refused").
7. **Cycle key** ignores scheme (`http` ≡ `https`), user info, default port, fragment and host case.
8. **A nested document that is rejected** becomes one `E_INCLUDE` on the including document in the tree
   report, so the tree report never contains a document-level issue (§9.3 "never fails the root").
9. **`meta.adult` present but not a boolean** → treated as `true` (fail-safe: ask for confirmation) + `W_FIELD`.
10. **Legibility**: when the creator background is rejected the background **image** (under its scrim) is
    kept; `accentSecondary` alone is not contrast-checked (it is dropped together with a rejected accent).
11. `imageDim` below 0.3 is raised silently (§6); above 1.0, negative or not a number → `W_FIELD`, 0.6.
12. Text lengths are counted in Unicode code points (truncation never splits a surrogate pair); values are
    trimmed and an empty value is invalid. `3.0` counts as an integer (JSON Schema semantics).
13. Enum matching is case-insensitive for section type, include type, DRM system, channel type, card
    style/corner and subtitle format; `query.from` and `query.sort` are case-sensitive (camelCase names).
14. A hero with both `query` and `items` uses `items` (`W_FIELD`); `sort: "recent"` outside
    `continueWatching` → `source` (`W_FIELD`); `catalog` on a non-catalog query is ignored (`W_FIELD`);
    `headers` on a `stremio` include are ignored (`W_FIELD`).
15. MIME hints are normalised: both HLS spellings → `StreamMimeTypes.HLS` (`application/x-mpegURL`), DASH
    and MPEG-TS → F1 constants, the other recognised types as written in lower case.
16. `toIPTVChannel()` uses the first stream; alternative streams exist only in `TsiptvChannel.streams`.

## QC round 1 fixes (2026-09-28)

QC round 1 found no Blocker/Major. Fixed items (QC numbering) and the PO's follow-up decisions
(PRD "Follow-up from QC", letters) that supersede some of them. The **Deviations** list above is
kept for history; where it disagrees with this section, this section and the updated spec win.

| # | Finding / decision | Fix |
|---|---|---|
| 1 | Stremio manifest check accepted an empty path (`https://host?x=/manifest.json`, `#/manifest.json`) | `TsiptvRules.isStremioManifestUrl`: the first of `/ ? #` after `://` must be `/`; the path up to `?`/`#` must end with `/manifest.json`. Otherwise `E_INCLUDE`. |
| 2 + (j) | Catch-up `source` scheme | Now §8.6: every mode — no whitespace or control characters, 1–2,048 code points (`TsiptvRules.isCatchupSource`). `default` must start with `http(s)://` (the substituted URL is checked at play time); `append` is query text, nested URLs of any scheme allowed; `shift`/`flussonic`/`flussonic-ts`/`xc` ignore `source` silently. Missing/invalid where required → catch-up dropped with **`W_FIELD`** (no longer `E_URL`); the channel plays live. |
| 3 | Localized keys echoed into messages/paths | Keys that are not language tags (and invalid header names) are never echoed: the path names the entry by position (`meta.name[1]`, `channels[0].headers[4]`), the message by number. Fixed messages no longer contain `://`. Test: hostile keys/values (URLs, `SECRET…` tokens) never appear in any issue path or message. |
| 4 + (h) | Lengths in UTF-16 units | `TsiptvRules.codePointCount`/`lengthIn` everywhere: `optString`, string arrays, catalog `type`/`id`, `author.name`, header values, HttpUrl length, catch-up source (Text already counted code points). |
| 6 + (e) (f) | Planner did not enforce budget / merged limits; failed fetch unreported | Loader returns `TsiptvFetchedDocument(result, byteCount)`. The planner checks `canFetch` before each load, counts bytes after it, applies merged item limits (root first, then nested in walk order; returned documents are trimmed) and exposes `tree.guard` for step 2. **Every fetch failure is `E_INCLUDE`** at `includes[i]`/`epg[i]` of the declaring document (`TsiptvIncludeGuard.FETCH_FAILED_CODE`), including over-budget and budget-spent; `droppedItems = 0` (the last good copy is kept, stale). The root counts towards 50 MiB (`plan(rootBytes = …)`, `recordRootFetched`). |
| 7 | Nested meta/appearance/layout warnings in the tree report | Filtered in `recordParse` (§9.3 ignores those parts). |
| 8 + (i) | 20-header cap after merge | Effective headers = stream's own first, then the item's in document order, distinct case-insensitive names, max 20; excess → **`W_LIMIT`** at `…streams[i].headers`. Per-map limit unchanged (`W_LIMIT`); `licenseHeaders` separate. |
| 9 | Unchecked `background.color` returned next to a legible gradient | The colour is used only if it passes on its own, else the first gradient stop. |
| 10 | Hero with `items: []` and a query | `items: []` counts as absent; the query is used. |
| 11 + (k) | `E_VERSION` message for huge integers | Integers beyond a Long (`1e20`, 25 digits) say "needs a newer app"; negative ones "does not exist". `0`, negative, `null`, non-integers → `E_VERSION` (unchanged). |
| (a) | `null` values | Already absent-silently; now tested for members, required members (`E_ITEM_NAME`, `E_META`, `E_VERSION`), array entries (not an object) and map values (header `W_FIELD`, text `W_TEXT`, DRM key `E_DRM`). |
| (b) | `x-` keys inside maps are data | Localized objects: an `x-…` key is an invalid language key (`W_TEXT`); `drm.keys`: not 32 hex digits (`E_DRM`). Headers named `x-…` keep working (unchanged). |
| (c) | Reserved `epg-` include ids | An explicit include id starting with `epg-` (case-sensitive) → `E_INCLUDE`, include dropped. Item ids may start with `epg-`. |
| (d) | Adult content through includes | `tree.adultCheck(stremioAdultIncludePaths)` → `TsiptvAdultCheck(rootIsAdult, adultIncludePaths)`; `requiresConfirmation` if the root, any accepted nested TS IPTV Source at any depth (`meta.adult` true or non-boolean, already read as true), or any `stremio` include whose manifest step 2 found adult. `tree.requiresAdultConfirmation` = without manifest information. |
| (g) | Non-object entries | Already as decided (items `E_ITEM_ID`, includes `E_INCLUDE`, streams/epg `E_URL`, subtitles/hero items/layout.home/string arrays `W_FIELD`); now tested with `null` entries. |
| schema | "Schema-valid ⇒ accepted" | Test: the three examples plus two schema-valid edge documents (every limit at its maximum, 198 emoji names, `x-…` headers, `epg-…` item ids, 20+20 headers, ClearKey, catalog query, hero items, season 0, append source with a nested URL) parse with no document or item errors; the only warning is the merged-header `W_LIMIT` the spec mentions. |

Step 2 API change: the planner's loader type, `TsiptvIncludeTree` (now a class with `guard`), and the
guard's fetch methods (`recordFetched(parent, entry, bytes)` etc.) — see "Public API" above.

## QC round 3 fixes (2026-09-28)

| # | Finding / decision | Fix |
|---|---|---|
| 1 | An include over 5 MiB or with corrupt gzip was recorded as "rejected" (dropped) | `TsiptvParseResult.Failure.cause: TsiptvFailureCause` — `TOO_LARGE`, `CORRUPT_COMPRESSION` (both `isFetchFailure`, codes still `E_TOO_LARGE` / `E_NOT_JSON`) or `INVALID_DOCUMENT`. The planner (and `guard.recordParse`) turn fetch-failure causes into `recordFetchFailed` → `E_INCLUDE`, `droppedItems 0`. Node statuses split: **`REJECTED`** (drop the include and any stored copy) vs **`FETCH_FAILED`** (keep the last good copy, stale). |
| 3 | Trim set vs the schema's `\S` | `TsiptvRules.isEcmaWhitespace` / `trimEcma`: ECMA-262 WhiteSpace + LineTerminator (TAB, VT, FF, SP, NBSP, U+FEFF, Zs, LF, CR, U+2028, U+2029); U+001C–U+001F are **not** whitespace. Used for every trimmed string in the parser. C0 controls in Text (tab included) and in LongText (everything but line breaks) are replaced by spaces with `W_TEXT`. C1 (U+0080–U+009F) stays rejected in URLs, header values and catch-up sources (`isISOControl`). |
| addendum | LongText line breaks | CRLF and a lone CR are normalised to LF, silently. |
| 4 | Catch-up `source` trimming | `source` is **not** trimmed: any whitespace (leading/trailing included, ECMA set or Kotlin's) or control character → `W_FIELD`, catch-up dropped, channel plays live. |

## QC round 4 fixes (2026-09-28)

| # | Finding / decision | Fix |
|---|---|---|
| 1 | `HTTP_URL`'s `\s` is ASCII-only | `TsiptvRules.isHttpUrl` also rejects every ECMA whitespace (NBSP, U+FEFF, U+2028/2029, U+3000, Zs) and every control character inside a URL, in every URL field → `E_URL` (or `E_INCLUDE` for include URLs). Leading/trailing whitespace is still trimmed first. |
| 2 | C0 controls in short strings | `shortText()`: groups, genres, tags, cast, directors, countries, languages, query filters and ids, `epgId`, `quality`, `license`, `author.name`/`email`, `originalName`, `ageRating`, `updatedAt`, catalog `type`/`id`/`genre` — C0 controls (tab, CR, LF included) become spaces, then the value is trimmed, with `W_FIELD`; the value stays if it is still valid. |

Tests: `TsiptvQcRound4Test`. `--rerun --tests "tss.t.tsiptv.core.parser.tsiptv.*"` → 12 classes, 114 tests, 0 failures.

**Loader contract (step 2, `TsiptvIncludeTreePlanner.plan`)**: for an accepted `tsiptv-source` include, `load(include, context)`:
- fetches `include.url` with `include.headers`, conditional GET where possible, reading at most
  `min(TsiptvLimits.MAX_DOCUMENT_BYTES + 1, guard.remainingBudget + 1)` decompressed bytes;
- returns `null` for network error, timeout, non-2xx other than 304, or a body cut at the cap
  (→ `FETCH_FAILED`, `E_INCLUDE`, keep stale);
- on `304` returns the stored copy's parse result with `byteCount = 0`;
- otherwise returns `TsiptvFetchedDocument(TsiptvSourceParser.parse(bytes, include.url), decompressedSize)`
  with the **raw** (possibly gzip) bytes, so the parser's bounded gunzip classifies `TOO_LARGE` /
  `CORRUPT_COMPRESSION` as fetch failures and every other document error as `REJECTED`.

## Known gaps (for step 2+)

- `W_QUERY_REF` for "a catalog not in the addon's manifest" needs the manifest → step 2 (after fetching it).
- `hero.target` and `query.ids` are checked for syntax only; whether the item exists (own or from an
  include) is known only after merging → resolve at render time.
- The fetch/compose runtime, conditional GET, M3U/XMLTV include parsing, Stremio registration: step 2/4.
- iOS: not compiled (common code only; uses kotlinx.serialization, okio, `kotlin.time`).
- AC-T25 on the reference emulators is not measured (no emulator per instructions). Desktop JVM: the
  generated maximum document (10,000 channels, 5,000 movies, 1,000 series, 20,000 episodes, 2.5 MiB)
  parses in ~200 ms.

## Tests

`composeApp/src/commonTest/kotlin/tss/t/tsiptv/core/parser/tsiptv/` — 11 classes, **112 tests**
(after QC round 3; `TsiptvQcRound1Test` / `TsiptvQcRound3Test` hold the regression tests for every
finding and PO decision). Last run: `--rerun --tests "tss.t.tsiptv.core.parser.tsiptv.*"`,
2026-09-28 09:27 → 112 tests, 0 failures, 0 errors, 0 skipped (this also confirms the round-1
message rewording that had not been re-run). Earlier classes:

| Class | Covers |
|---|---|
| `TsiptvExamplesTest` | AC-T1: the three `web/public/examples/*.tsiptv.json` files (read from the repo) detect as `TSIPTV_SOURCE` and parse with **zero issues**; counts (2 TV + 1 radio; 2 movies, 1 series, 1 episode; 4 includes, 6 sections); AC-T22 host list; the composed example's include tree with the nested vod-catalog (`cinema:…` ids). |
| `TsiptvDocumentErrorsTest` | AC-T2: `E_NOT_JSON` (comments, trailing comma, non-object root), `E_NOT_SOURCE`, `E_VERSION` (2, "1", 1.5, 0, missing; 1.0 accepted), `E_ID`, `E_META`, `E_EMPTY` (none / all dropped / EPG only), BOM. |
| `TsiptvItemErrorsTest` | AC-T3 fixture with one item per item code and exact JSON paths; AC-T4 forward compatibility (`foo`/`x-` everywhere, every §11 fallback); AC-T6 non-http schemes in every URL field; uniqueness; DRM; header rules; include validation. |
| `TsiptvContentTest` | AC-T5 shorthands and header/DRM/MIME merging (incl. JSON round trip); channel/movie/series/season/episode fields; catch-up; subtitles; Text rules (W_TEXT); meta; `W_FIELD` drops. |
| `TsiptvLayoutTest` | Sections, hero, limits and clamps, `seeAll`/`groupChips`, sorts, `W_QUERY_REF`, catalog queries, ids, default-layout fallback, 30-section limit. |
| `TsiptvAppearanceTest` | AC-T16 contrast (`#DDDDDD`), gradients, accents, WCAG ratio, invalid fields, card resolution, default layout. |
| `LocalizedTextResolverTest` | AC-T7 resolution order, `zh-TW` → `zh-CN`, platform tag spellings. |
| `TsiptvIncludeGraphTest` | AC-T10 cycle A→B→A, depth 4, 21st include; failed/rejected nested documents; merged item limits; 50 MiB budget; URL keys; id sanitising; hosts. |
| `TsiptvLimitsTest` | Generated fixtures (nothing large committed): the maximum document, one past each count limit, 20,000-episode limit, per-series/season/stream/subtitle/include/EPG limits, 6 MiB → `E_TOO_LARGE`, exactly 5 MiB accepted, UTF-8 byte counting, gzip and gzip bomb, corrupt gzip. |

Fixtures in `composeApp/src/commonTest/kotlin/assests/tsiptv/`: `item-errors.tsiptv.json`,
`forward-compat.tsiptv.json`, `bad-urls.tsiptv.json`, `doc-not-json.tsiptv.json`, `doc-not-source.json`,
`doc-version-2.tsiptv.json`, `doc-missing-id-and-name.tsiptv.json`, `doc-all-items-invalid.tsiptv.json`,
`cycle-a.tsiptv.json`, `cycle-b.tsiptv.json`.

Run: `.\gradlew.bat :composeApp:desktopTest --console=plain`. Round 0: 58 suites, 411 tests, 0 failures.
QC round 1 (last run, 2026-09-28 06:54): 59 suites, **426 tests, 1 failure** —
`TsiptvQcRound1Test.noUrlSecretOrHeaderValueInAnyMessageOrPath`, which found the fixed wording
"http:// or https://" in the parser's own URL message. The two messages were reworded without
`://`, and nothing else in the test can trip. **This last change was not re-run** because this
round was limited to two gradle runs; QC should confirm with the command above. Only this package:
`--tests "tss.t.tsiptv.core.parser.tsiptv.*"`.
