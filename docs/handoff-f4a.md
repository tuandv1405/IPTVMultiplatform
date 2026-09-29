# Hand-off — F4 wave A + F4-0 fixtures (website format guides)

Date: 2026-09-28 · Branch: `feature/sources-and-formats` (not committed) · Spec: [`prd-web-format-guides.md`](prd-web-format-guides.md)

## What was built

**F4-0 fixtures** (`web/public/`)

| File | Notes |
|---|---|
| `examples/playlist.m3u` | 6 channels, `tvg-chno` 1–6, exactly as PRD: HLS Test Channel (Mux), Example News (`News;HD`, 2× `#EXTVLCOPT`), Example Sports (`\|Referer=` suffix), Example Radio (`radio="true"`), Example Protected (`#KODIPROP` Widevine, `license.example.com`), Example Archive (`catchup="shift" catchup-days="3"`). `x-tvg-url` → `/examples/guide.xml`. |
| `examples/guide.xml` | Channels `HlsTest.example`, `ExampleNews.us`; one "Test pattern" programme per channel `20260101000000 +0000` → `20301231235959 +0000`; two dated one-hour programmes (31 Dec 2025, before the test pattern, so nothing overlaps) with `sub-title`, `category`, `episode-num` (`xmltv_ns` + `onscreen`), `rating`. 4 programmes. No DOCTYPE, one `display-name` per channel (current model is single-valued). |
| `examples/stremio-sampler/**` | The 11 JSON files of research §5.2, byte-for-byte as written there (no `.nojekyll`). |
| `policy/addon-blocklist.json` | `{"ids":[],"hosts":[]}` |

**Remaining wave A examples**: `examples/playlist.xspf` (2 tracks, Mux first, one element per line),
`examples/playlist.json` (generic, 3 channels, Mux first in parse order), `examples/iptv-org-streams.json`
(2 entries), `examples/channel.strm`.

**Pages** (`web/public/guides/`): hub `index.html`, `iptv/`, `m3u/`, `xmltv/`, `xspf/`, `json/`,
`xtream-codes/`, `kodi/`, `monplayer/` (noindex). All bilingual in one document (`<article data-lang-pane>`),
breadcrumb, "On this page" TOC, "no channels" callout (`[data-no-channels]`) in both panes, Related guides.
Stremio and TS IPTV Source cards/rows are **omitted** from the hub (PRD: later-wave cards omitted).

**Shared assets**: `assets/site.css` (appended block only: pre/code, `.toc`, `.breadcrumb`, `.callout`,
`.badge`, `.copy-btn`/`.dl-btn` ≥ 44 px, `.features`, focus ring, nav wrap, 16 px gutters < 400 px);
`assets/guides.js` (new, no deps: copy buttons with Clipboard API + `execCommand` fallback, 2 s "Đã chép /
Copied", labels in pane language; cross-pane anchor mapping `#x` ↔ `#x-en`); `assets/lang.js` (switches
`document.title` from `data-title-vi`/`data-title-en`).

**Site-wide**: Guides link added as first header-nav link and after "TS IPTV ·" in the footer of every page
(`index`, `privacy`, `terms`, `delete-account`, `delete-account/confirm` (gained a nav), `playlists`,
`contributor`, `contributor/request`, `contributor/submit`, `contributor/admin` (gained a footer)). Landing
"Many formats" card links to `/guides/` in both panes. `sitemap.xml`, `robots.txt`. `web/README.md` page table
+ editing notes.

**Check script**: `web/scripts/check_guides.py` (`--fix` rewrites `<pre data-example>` blocks from files).

## Check-script output

```
> python web/scripts/check_guides.py
check_guides: OK — 9 guide pages, 20 site pages, 20 example files, schema and links clean
```

Negative tests (temporary mutations, restored) were all caught: missing anchor, denylisted
`plugin.video.*`, broken cross-page fragment, non-allowlisted outbound link, example file ≠ block,
non-placeholder URL in text, TS source schema error, missing nav Guides link, `get.php?username=<not U>`.

Local serve (`npx http-server web/public -p 5077 -c-1`): every page (with and without `?lang=en`), every
linked asset, every example/policy/schema file, sitemap and robots → **200** (74 URLs). Server stopped.

## AC coverage

| AC | Status | How QC checks it |
|---|---|---|
| W1 | Local: all 200. Prod pending deploy | `curl -I` every URL in the PRD table (wave A) with and without `?lang=en` after `firebase deploy --only hosting`. |
| W2 | Done | Toggle on each page: content, `<html lang>`, tab title change; `?lang=en` opens English; reload remembers choice. |
| W3 | Done (script) | `check_guides.py` (anchors `what…support` in vi, `…-en` in en). XMLTV also has `#link`, JSON has generic/iptv-org sub-anchors. |
| W4 | Done (script) | `check_guides.py` compares every `data-example` block to its file (entity-decoded, LF-normalised, trailing newline ignored). |
| W5 | TS Source examples: validate (script). Others: **not run** | `WebExamplesParseTest.kt` is due after F1 merges (not written here: no Kotlin changes allowed). See risks below. |
| W6 | Automated part done | Script: denylist over all of `web/public`, allowlist for guide links, permitted hosts for every URL in guide text and examples, no `/playlists/` links from guides. PO manual review still required. |
| W7 | CSS done; device test pending | Chrome device toolbar 360 px / iPhone SE / Pixel: no horizontal page scroll; tables in `.scroll-x`; `pre` scrolls; copy buttons on Android Chrome + iOS Safari (needs HTTPS for Clipboard API; fallback otherwise). |
| W8 | Pending | Lighthouse mobile on `/guides/`, `/guides/m3u/`. |
| W9 | Done (script) | Script asserts `/guides/` in header nav and footer of every non-redirect page. |
| W10 | Headers in `firebase.json` (added by the lead); pending deploy | After `firebase deploy --only hosting`: `curl -I` `/schema/tsiptv-source-v1.json`, `/examples/*.json` (`application/json`, `Access-Control-Allow-Origin: *`, `Cache-Control: public, max-age=600`), `/examples/playlist.m3u` (`audio/x-mpegurl`), `.strm`, `.xspf`; no trailing-slash redirect on any file. |
| W11 | n/a to wave A | — |
| W12 | Fixtures in place | Sampler manifest URL in the F2 build; example URLs in F1 build (after deploy). |
| W13 | Done (script + manual) | `noindex` meta, no http(s) links (only `/guides/m3u/` and a `mailto:`), absent from sitemap; text is the four approved statements. |
| W14 | n/a (wave C) | — |
| W15 | Done (script) | `[data-no-channels]` callout in both panes of every guide page. |
| W16 | Pending | Native Vietnamese reviewer + PO sign-off. |

## Deviations and decisions

1. **`firebase.json`**: not edited by me (outside `web/`); the lead has since added these headers
   to `hosting.headers`:
   ```json
   { "source": "/@(examples|schema|policy)/**", "headers": [
       { "key": "Access-Control-Allow-Origin", "value": "*" },
       { "key": "Cache-Control", "value": "public, max-age=600" } ] },
   { "source": "**/*.@(m3u|m3u8)", "headers": [ { "key": "Content-Type", "value": "audio/x-mpegurl; charset=utf-8" } ] },
   { "source": "**/*.strm", "headers": [ { "key": "Content-Type", "value": "text/plain; charset=utf-8" } ] },
   { "source": "**/*.xspf", "headers": [ { "key": "Content-Type", "value": "application/xspf+xml" } ] }
   ```
   Note `"ignore": ["**/.*"]` would drop a `.nojekyll`; none is shipped. Deploy was not run, so AC-W10
   still needs the `curl -I` pass after deploy.
2. **Anchor ids in the English pane carry `-en`** (`#syntax-en`) because ids must be unique per document;
   `guides.js` maps `#syntax` ↔ `#syntax-en` to the visible pane. The check script requires both forms.
3. **Sampler `manifest.json` description** still says "Static JSON on GitHub Pages." (kept verbatim from
   research §5.2 as the PRD says "exactly"); PO may want "Static JSON files." instead.
4. **No link to the TS IPTV Source page** from the JSON guide (PRD asks for one): the page ships in wave C
   and content rule 5 forbids describing F3 early. Replaced with "need headers/DRM/catch-up → use M3U".
   Add the link in wave C.
5. **XMLTV guide URL in TS IPTV**: the app has no separate guide-URL field, so the XMLTV and Xtream pages
   say the guide comes via the playlist's `x-tvg-url`/`url-tvg` (or JSON `epgUrl`), with a download-edit-import
   workaround. The PRD's Xtream line "add the `xmltv.php` link as the guide" is not possible as shipped.
6. **XMLTV matching** is described as `tvg-id` = `<channel id>` only; I could not confirm name-based
   matching in the app code, so the page does not promise it.
7. **ClearKey-on-HLS message**: F1 PRD is inconsistent (`drm_not_supported_config` in §4 vs
   `drm_not_supported_device` in the error table); the Kodi page says only "a DRM message is shown".
8. Nav label is "Hướng dẫn" on Vietnamese-only navs and "Hướng dẫn / Guides" on bilingual navs and all footers.
9. Sitemap includes existing indexable pages, including `/playlists/` (not noindex today; see roadmap R3).
10. `/examples/*.tsiptv.json` and `/schema/` existed already and were not changed.

## Risks for F1 / QC (app side, not fixed here)

- `XMLTVEPGParser` uses xmlutil with default policy; unknown children (`sub-title`, `episode-num`, `rating`)
  may throw. `guide.xml` contains them as the PRD requires, and the XMLTV page says they are "accepted, not
  shown". Verify in `WebExamplesParseTest`; fix with an `ignoreUnknownChildren()` policy if needed.
- `IptvOrgParser` decodes with default `Json` (no `ignoreUnknownKeys`) and `IptvOrgRawDTO` is not
  `@Serializable`; `iptv-org-streams.json` includes `labels`. F1 must fix this for AC-W5.
- `XSPFParser` assigns groups from the last-seen `vlc:node`; the XSPF page states this ("Partial").
- Guide pages describe **F1 behaviour** (file import, `.strm`, headers, DRM, HLS single-stream dialog,
  skipped-entry details) and must only be published with the F1 release.

## Files

- New: `web/public/guides/**/index.html` (9), `web/public/assets/guides.js`, `web/public/examples/{playlist.m3u,guide.xml,playlist.xspf,playlist.json,iptv-org-streams.json,channel.strm}`, `web/public/examples/stremio-sampler/**` (11), `web/public/policy/addon-blocklist.json`, `web/public/sitemap.xml`, `web/public/robots.txt`, `web/scripts/check_guides.py`, `docs/handoff-f4a.md`.
- Changed: `web/public/assets/site.css` (appended), `web/public/assets/lang.js`, nav/footer of the 10 existing pages, `web/public/index.html` (card link), `web/README.md`.

## QC round 1 (2026-09-28) — changes

1. XMLTV: `<channel>` is "zero or more" (DTD `channel*, programme*`).
2. XMLTV: offset-less times — the DTD reads them as UTC, but TS IPTV currently reads them in the device's time zone; always write the offset (time-format paragraph and mistakes row, both panes).
3. JSON: numeric `id` — "TS IPTV still reads it, but other apps may reject it"; advice unchanged.
4. JSON: iptv-org file with `name` → read as generic JSON, which requires `id`, so the whole file cannot be read.
5. Xtream: no longer claims `get.php` usually carries `url-tvg`; "some providers add `url-tvg`/`x-tvg-url`; if yours does not…".
6. XSPF: detection needs the `<?xml …?>` first line and the namespace (add section); new mistakes row for a missing `<?xml` line.
7. Tab title switches on every page: `data-title-vi`/`data-title-en` added to `<html>` of index, privacy, terms, delete-account, delete-account/confirm, playlists, contributor, contributor/request, contributor/submit, contributor/admin; `contributor-ui.js` `applyLanguage()` now sets `document.title` from them (called on load and on toggle). `contributor/app` is a redirect stub, unchanged.
8. M3U and Kodi callouts mention TS IPTV's own example files.
9. M3U mistakes: "something other than `#EXTM3U`/`#EXTINF` at the start".
10. Kodi catch-up: `shift` uses `&utc=` when the URL already has `?`; `{Y}…{S}` use the device's local time.
11. JSON (vi): "Bảng chuỗi" → "Tập cặp khoá–giá trị (chuỗi)".

`check_guides.py`: OK after the changes.