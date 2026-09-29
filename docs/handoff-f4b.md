# Hand-off — F4 wave B (Stremio-compatible addons guide)

Date: 2026-09-28 · Branch: `feature/sources-and-formats` (not committed, not deployed) · Spec: [`prd-web-format-guides.md`](prd-web-format-guides.md) (`/guides/stremio-addons/`), behaviour from [`prd-stremio-addons.md`](prd-stremio-addons.md) and [`handoff-f2-step1.md`](handoff-f2-step1.md). Wave A: [`handoff-f4a.md`](handoff-f4a.md).

## What was built

- **`web/public/guides/stremio-addons/index.html`**, bilingual (vi/en), same template as wave A. It has the eight anchors (`what` … `support`, English `-en`), plus sub-anchors for URLs, encoding, manifest, paging, meta, stream, subtitles, caching, ids and each example.
  - **Protocol**: base URL and resource URL patterns, with the response root for each of catalog, meta, stream, subtitles and addon_catalog. Also covers the query string and config path segment, SDK error and redirect answers, and `encodeURIComponent` encoding (`%20`, never `+`; `:` becomes `%3A`; repeated keys; TS IPTV's extra order with `skip` last).
  - **Manifest and objects**: manifest field table, catalogue and extra table (with the legacy `extraSupported` fallback), and paging (100 per page, TS IPTV's lenient rule). Meta and video fields, stream fields including `proxyHeaders.request`, the subtitles format ("not used yet"), cache fields (TS IPTV defaults, 5-minute stream cap), and types and id prefixes.
  - **Example**: the full `stremio-sampler` file tree. Five sampler files are shown as `data-example` blocks with Download links (manifest, movie catalogue, series meta, movie stream, tv stream); the other six (series and tv catalogues, film and channel meta, episode stream, `genre=Comedy.json`) are linked, so all 11 are reachable. Each is explained with the URL it is requested at.
  - **Create**: (1) static JSON, step by step, with its limits (no search, one file per genre and page, no `:` in ids) and local testing with `npx http-server . --cors -c-1`, `127.0.0.1` and `10.0.2.2`. (2) Node.js SDK: `package.json` and a condensed `addon.js` from research §5.1, then `npm start` and deploying to "any Node.js host with HTTPS".
  - **Host**: HTTPS (TS IPTV accepts http with a warning), CORS (required by the spec, not needed by TS IPTV), cache and content-type rules, and a table with the exact config for GitHub Pages (`.nojekyll`), Firebase, Netlify `_headers`, Vercel `vercel.json`, nginx and SDK hosts. The hosting services are named but not linked.
  - **Add**: Profile → Addons → Add addon (phone, iOS, desktop), Settings → Addons (TV); `stremio://` input accepted; preview contents; notice; Discover appears; `configurationRequired` flow; links stored encrypted.
  - **Mistakes**: 14 rows, using the app's actual message texts.
  - **What TS IPTV plays**: platform badges for `url` MP4/HLS, DASH/MKV (iOS "may not play") and `proxyHeaders`; `externalUrl` opens the browser (as text on TV); torrent, Usenet, archive and YouTube "Not supported" in one row, with no elaboration; configure, adult/p2p, continue watching; "Not yet" for subtitles, EPG addons, `addon_catalog` and auto-next.
- **Policy statement** in the callout of both panes: no channels, no addons, nothing pre-installed or recommended; addons are third-party and the user is responsible. It also says the protocol was created for the Stremio app and published as the open `stremio-addon-sdk` specification, and that TS IPTV is not affiliated with or endorsed by Stremio ("Stremio-compatible" is descriptive only). No logo, no community addon or collection named or linked. External links go only to `github.com/Stremio/stremio-addon-sdk` (repo, protocol.md, docs/api, README).
- **Hub**: a card under "Other apps and services" (after Xtream, before Kodi), and a "Which one do I need?" row. **Related links** added on the JSON and M3U pages. **Sitemap**: `/guides/stremio-addons/` added. **`web/README.md`**: page row.
- **`check_guides.py`**: `127.0.0.1`, `localhost` and `10.0.2.2` are now permitted in guide text (only as local-testing addresses). The page is covered by all existing checks (it was already listed in `FORMAT_PAGES`).

## Results

```
> python web/scripts/check_guides.py
check_guides: OK — 10 guide pages, 21 site pages, 20 example files, schema and links clean
```

Local serve (`npx http-server web/public -p 5077 -c-1`): all 77 page, asset, example and policy URLs return 200 (pages with and without `?lang=en`). My crawler also reported one 404, `/guides/xmltv/…`, but that is its own regex matching escaped `src="…"` text inside a code block, not a real link. Server stopped.

The `web/public/examples/kodi/qc-*` files that failed the check in wave A QC round 2 are no longer present, so the check is clean again.

## AC notes (wave B)

| AC | How QC checks it |
|---|---|
| W1 | After deploy: `curl -I https://tsiptv-8bdd6.web.app/guides/stremio-addons/` and `?lang=en` → 200. |
| W2, W3, W4, W6, W9, W15 | `check_guides.py` plus the manual toggle check. For W6, the PO confirms no addon other than TS IPTV's own sample is named or linked, and that the non-affiliation sentence is present. |
| W7, W8 | 360 px check (the long `addon.js` block scrolls inside itself); Lighthouse on this page is optional (W8 names hub, M3U and TS Source). |
| W12 | The sampler manifest link given on the page is the F2 fixture URL. |
| W16 | Vietnamese review and PO sign-off. |

## Deviations and decisions

1. `addon.js` is the research §5.1 addon **condensed**: same data and handlers, a shorter manifest literal, no `description` per item. It is not a downloadable file, so it has no `data-example`.
2. Firebase snippet on the page uses `**/*.json` as a generic example; TS IPTV's own `firebase.json` rule (added by the lead) is scoped to `/examples`, `/schema`, `/policy`.
3. UI labels use the F2 PRD string table (addon strings are not yet in `values-vi/strings.xml`). "Continue" and "Add" button labels (vi "Tiếp tục", "Thêm") are from the PRD flow text, not from string keys. Re-check them against the shipped F2 strings before publishing.
4. The page describes F2 behaviour (Discover, preview, configure flow, header platforms) and must be published only with the F2 release.
5. The kill switch is mentioned once and neutrally ("disabled remotely only when a takedown request requires it"). The blocklist URL is not shown.

## Generator moved into the repo (2026-09-28)

- `web/scripts/build_guides.py` (the page generator, previously in a temporary scratch folder) and `web/guides-src/*.html` (20 source files, the body of each language pane for 10 pages) are now in the repo.
- Byte-for-byte check: `build_guides.py --check` returned 0 against the existing pages, a full build wrote no file, and `git diff --no-index` between a copy of `web/public/guides/` taken before the build and the result was empty.
- `check_guides.py` now fails with "out of date vs web/guides-src" when a generated page differs from its sources (tested by editing a source without rebuilding: 1 problem, exit 1; restored: OK). `--fix` now runs the build. The old in-place example rewriter was removed from `check_guides.py`; `build_guides.py` fills the example blocks.
- Workflow documented in `web/README.md`: edit sources, run `build_guides.py`, run `check_guides.py`.
