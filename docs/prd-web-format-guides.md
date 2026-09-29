# PRD F4 — Website format guides

Status: **Ready for development, 2026-09-27** · Owner: PO · Surface: `web/public/` (Firebase Hosting, `tsiptv-8bdd6.web.app`)
Research (content source, binding): [`research/playlist-formats.md`](research/playlist-formats.md),
[`research/kodi.md`](research/kodi.md), [`research/stremio-addons.md`](research/stremio-addons.md)
Format spec: [`tsiptv-source-format.md`](tsiptv-source-format.md). Roadmap: [`roadmap-sources-and-formats.md`](roadmap-sources-and-formats.md)

---

## Problem

TS IPTV ships with no content, so every new user's first question is "what do I put in it?" The
honest answer is "a playlist or guide in one of these formats, that you make or that your provider
gives you". Today the site lists format names on the landing page and nothing else, users ask for
"links", and the support answer has to be written each time. The formats themselves are scattered
across RFCs, a Kodi add-on README, a DTD, SDK docs and forum posts, and TS IPTV's own format (F3)
has no public home at all.

## Goals

1. One bilingual (Vietnamese / English) guide per format, each showing the **real format**: syntax,
   a complete example, how to write it by hand, how to host it, how to add it in TS IPTV, and
   common mistakes.
2. A complete public home for the TS IPTV Source format: guide, JSON Schema, examples, a
   step-by-step "create your own", and a browser validator.
3. Every example on the site is a real file that our parsers read and our schema accepts, checked
   automatically.
4. The pages strengthen, not weaken, TS IPTV's position as a neutral player.

## Non-goals

- **Listing, linking or recommending any source**: no playlists, "free IPTV" lists, playlist
  indexes, providers, Xtream resellers, Stremio community addons or collections, Kodi add-ons or
  repositories, MonPlayer sources or the sites that distribute them. Guide content does not link to
  the community directory `/playlists/` either.
- A playlist or source editor/builder, hosting for users' files, uploading.
- Languages other than Vietnamese and English on the website.
- Changing the existing design system (colours, typography, header/footer look).

## Content rules (binding for every page and example)

1. Say plainly, near the top of every page: TS IPTV plays playlists and sources **you** provide and
   includes no channels.
2. Hosts in examples are `example.com` / `*.example.com` placeholders, **except** these real,
   playable media, all public test streams or public domain:
   - `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` (Mux test stream; Big Buck Bunny, © Blender Foundation, CC BY 3.0 — attribute it);
   - `https://archive.org/download/his_girl_friday/his_girl_friday_512kb.mp4` and
     `https://archive.org/download/superman_the_mechanical_monsters/superman_the_mechanical_monsters_512kb.mp4`
     (public domain) and their `https://archive.org/services/img/<item>` images;
   - TS IPTV's own files under `https://tsiptv-8bdd6.web.app/examples/`, `/schema/` and `/policy/`.
3. **Outbound links allowlist** (format specifications and official documentation only):
   `rfc-editor.org` (RFC 8216), `github.com/XMLTV/xmltv` (DTD), `xspf.org`,
   `github.com/kodi-pvr/pvr.iptvsimple` (README), `github.com/xbmc/inputstream.adaptive/wiki`,
   `github.com/Stremio/stremio-addon-sdk` (docs), `json-schema.org`, `developer.android.com`
   (Media3 DRM), `w3.org` (EME Clear Key). Anything else needs PO approval.
4. Denylist (automated check, section "Checks"; case-insensitive): `strem.io`, `strem.fun`,
   `stremio.net`, `beamup`, `addonscollection`, `iptv-org.github.io`, `monplayer.org`,
   `org.monplayer`, `xoilac`, `xôi lạc`, the regexes `repository\.[a-z0-9]` and
   `plugin\.video\.[a-z0-9]` (real Kodi add-on ids), and `get.php?username=` followed by anything
   other than the `U` placeholder.
5. Describe features **as shipped**. A page (or a paragraph) about F1, F2 or F3 behaviour is
   published only in the same release as that feature (see "Release waves"). Catch-up playback is
   described as "stored, playback coming later" until F1b ships.
6. Tone: the existing pages' tone — short sentences, active voice, no hype, no emoji.

---

## Site structure

### URLs

| URL | Page | Wave |
|---|---|---|
| `/guides/` | Guides hub | A |
| `/guides/iptv/` | What is IPTV | A |
| `/guides/m3u/` | M3U / M3U8 playlists (with Kodi attributes) | A (Kodi parts with F1) |
| `/guides/xmltv/` | XMLTV programme guides | A |
| `/guides/xspf/` | XSPF playlists | A |
| `/guides/json/` | JSON playlists (generic and iptv-org) | A (array support with F1) |
| `/guides/xtream-codes/` | Xtream Codes style provider links | A |
| `/guides/kodi/` | Kodi formats in TS IPTV | A, with F1 |
| `/guides/stremio-addons/` | Stremio-compatible addons | B, with F2 |
| `/guides/tsiptv-source/` | TS IPTV Source format | C, with F3 |
| `/guides/tsiptv-source/validate/` | Browser validator | C |
| `/guides/monplayer/` | Note about MonPlayer's format | A |
| `/schema/tsiptv-source-v1.json` | JSON Schema (already written) | C (may go live earlier) |
| `/examples/…` | Example files (below) | A for A-files, B/C for theirs; fixtures early (see waves) |
| `/policy/addon-blocklist.json` | Addon kill-switch list for F2, `{"ids":[],"hosts":[]}` | B (live before F2 QC) |
| `/sitemap.xml`, `/robots.txt` | SEO | A |

Firebase `cleanUrls` + `trailingSlash` stay; each page is `web/public/guides/<slug>/index.html`.

### Example files under `web/public/examples/`

| File | Content | Parsed by |
|---|---|---|
| `playlist.m3u` | `#EXTM3U x-tvg-url="https://tsiptv-8bdd6.web.app/examples/guide.xml"`; 6 channels with `tvg-chno` 1–6: **HLS Test Channel** (Mux, `tvg-id="HlsTest.example"`, group `Test`); Example News (placeholder, `group-title="News;HD"`, `#EXTVLCOPT` UA + referrer, `tvg-id="ExampleNews.us"`); Example Sports (placeholder, `|Referer=` suffix); Example Radio (placeholder, `radio="true"`); Example Protected (placeholder, `#KODIPROP` Widevine with an `example.com` licence URL, group `DRM demo`); Example Archive (placeholder, `catchup="shift" catchup-days="3"`). | F1 `M3UParser` |
| `guide.xml` | XMLTV for `HlsTest.example` and `ExampleNews.us`: one programme per channel running `20260101000000 +0000` → `20301231235959 +0000` ("Test pattern" / always on now), plus two dated one-hour programmes showing `sub-title`, `category`, `episode-num` (`xmltv_ns` and `onscreen`), `rating`. | `XMLTVEPGParser` |
| `playlist.xspf` | The research §3.2 example, one element per line (the current `XSPFParser` is line-based), first track = Mux stream. | `XSPFParser` |
| `playlist.json` | Generic JSON (research §4.2 shape): object with `name`, `epgUrl`, `groups`, `channels`, first channel = Mux stream. | `JSONParser` |
| `iptv-org-streams.json` | Array in iptv-org `streams.json` shape with 2 entries (Mux + placeholder with `referrer`/`user_agent`). | `IptvOrgParser` (via F1 detection) |
| `channel.strm` | `#KODIPROP:mimetype=application/vnd.apple.mpegurl` + the Mux URL. | F1 `StrmParser` |
| `minimal-live.tsiptv.json`, `vod-catalog.tsiptv.json`, `composed-includes.tsiptv.json` | Already written. | F3 parser + schema |
| `stremio-sampler/…` | The static **Public Domain Sampler** addon exactly as research §5.2 (file tree, `manifest.json`, catalog/meta/stream files, `.nojekyll` not needed on Firebase). Manifest URL `https://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json`. | F2 client |

These files are TS IPTV's own examples and QA fixtures. They are **never** pre-installed or
suggested inside the app; the app may link to the guides, not to example files.

### Navigation and footer (all pages, including existing ones)

- Header `nav`: add **Hướng dẫn / Guides** as the first link (`/guides/`). On guide pages the link
  has `aria-current="page"`. Existing links unchanged.
- Footer: add `Hướng dẫn / Guides` after "TS IPTV ·".
- Landing page (`index.html`): the "Many formats" card (both panes) links to `/guides/`; the formats
  sentence is updated when F1/F2/F3 ship.
- Guide pages: breadcrumb `Hướng dẫn › <page>` / `Guides › <page>` above the H1; an **On this page**
  list (anchor links) after the summary; a **Related guides** list at the end.

### Shared assets

- `assets/site.css` additions (no change to existing rules): `pre`, `code` (monospace, navy-700
  background, `overflow-x: auto`, `tab-size: 2`), `.toc`, `.breadcrumb`, `.callout` (info variant
  of `.card`), `.badge` (support status: "Supported", "Not supported", "Android only"), `.copy-btn`,
  and `overflow-wrap: anywhere` for inline code and URLs in text.
- `assets/guides.js` (new, no dependencies): adds a **Sao chép / Copy** button to every
  `pre[data-copy]`, shows "Đã chép / Copied" for 2 s; uses the Clipboard API with a selection fallback.
- `assets/lang.js`: also switch `document.title` from `data-title-vi` / `data-title-en` on `<html>`.
- `firebase.json` headers: for `/examples/**`, `/schema/**`, `/policy/**` add
  `Access-Control-Allow-Origin: *` and `Cache-Control: public, max-age=600`; `Content-Type` for
  `**/*.m3u` / `*.m3u8` = `audio/x-mpegurl; charset=utf-8`, `*.strm` = `text/plain; charset=utf-8`,
  `*.xspf` = `application/xspf+xml`. QC confirms `.json` files are served without a trailing-slash
  redirect.

---

## Page template (format pages)

Every format page (M3U, XMLTV, XSPF, JSON, Xtream, Kodi, Stremio, TS IPTV Source) has these
sections, in both language panes, with these **anchor ids** (the check script requires them):

| Anchor | Section | Content |
|---|---|---|
| `#what` | What it is | 2–4 sentences; where the format comes from; what it is used for. |
| `#syntax` | Syntax | The structure, then tables of required and optional fields/attributes with meaning. |
| `#example` | Complete example | A full file in a `<pre data-copy data-example="/examples/<file>">`, a **Download** link to that file, and a line-by-line explanation. |
| `#create` | Create one by hand | Numbered steps with the tools (plain-text editor, encoding, extension). |
| `#host` | Host it | Options with exact URL shapes (GitHub raw, GitHub Pages, any static host, Dropbox `dl=1`, local file) and what does **not** work (share pages, `blob/` URLs). |
| `#add` | Add it in TS IPTV | Steps on phone and TV (**Add by link**, **Import from file**, or **Add addon** for Stremio), what the user sees, and what the import summary means. |
| `#mistakes` | Common mistakes | Table: mistake · symptom · fix. |
| `#support` | What TS IPTV supports | Table of features with `.badge` status per platform where it differs (Android / iOS / desktop). |

Pages that are not format pages (hub, What is IPTV, MonPlayer, validator) follow their own outline.

---

## Page outlines

### `/guides/` — hub

- H1 "Hướng dẫn định dạng / Format guides". Summary: TS IPTV plays what you provide; pick your format.
- Callout: "TS IPTV includes no channels."
- Card grid (reuse `.features` style), four groups:
  1. **Start here**: What is IPTV.
  2. **Playlists**: M3U/M3U8, XSPF, JSON, TS IPTV Source.
  3. **Programme guides**: XMLTV.
  4. **Other apps and services**: Xtream Codes links, Stremio-compatible addons, Kodi formats, MonPlayer.
- "Which one do I need?" table: *I have a link from my provider* → M3U or Xtream; *I have a guide
  URL* → XMLTV; *I want to build my own list* → M3U (simple) or TS IPTV Source (with movies, series,
  layout); *I run a Stremio-style addon* → Stremio page; *I used Kodi PVR IPTV Simple* → Kodi page.
- Cards for pages of later waves are omitted until their wave ships.

### `/guides/iptv/` — What is IPTV

- What IPTV means (TV delivered over IP networks) and the three pieces: stream, playlist, guide.
- Glossary table: playlist, stream, HLS, DASH, MPEG-TS, EPG/XMLTV, DRM, catch-up, VOD, User-Agent/Referer.
- Where legitimate streams come from, in general terms only: your TV or internet provider, a
  broadcaster that offers its channels online, your own home media server, public-domain archives.
  No names, no links.
- What TS IPTV is and is not: a player; no channels; you are responsible for your sources (same
  wording as the landing page).
- "Next steps" links to the M3U and TS IPTV Source guides.

### `/guides/m3u/` — M3U / M3U8

- `#what`: M3U vs extended M3U vs M3U8 (UTF-8); **HLS media playlist vs IPTV channel list** table
  (research §1.1), and what TS IPTV does when you give it an HLS link (single-stream dialog, F1).
- `#syntax`: structure block; header attribute table (`x-tvg-url` with several URLs, `url-tvg`,
  `tvg-shift`, `catchup-*`); channel attribute table (research §1.2 plus Kodi extras: multi-group
  `;`, `tvg-chno`/`ch-number`, `radio`, `media`); `#EXTGRP` (in-stanza and sticky rule);
  `#EXTVLCOPT` keys; `|Header=Value` suffix (URL-encode values); name after the first comma outside
  quotes; `#KODIPROP` summarised with a link to `/guides/kodi/`.
- `#example`: `/examples/playlist.m3u`, plus the 3-line minimal playlist inline (no file).
- `#create`, `#host`, `#add`: research §1.4–1.5; mention `.strm` single-stream files with a link to
  the Kodi guide.
- `#mistakes`: research §1.6 table, plus "headers written as attributes" and "plugin:// entries".
- `#support`: headers (Android all / iOS best effort / desktop UA+Referer only), DRM (Android only),
  radio, groups, numbers, catch-up (stored; playback later).

### `/guides/xmltv/` — XMLTV

- `#syntax`: element tree from the DTD; tables for `<tv>`, `<channel>`, `<programme>` children;
  time format with offsets (UTC assumed when missing); `episode-num` systems (`xmltv_ns` zero-based).
- `#example`: `/examples/guide.xml`; explain the long "Test pattern" programme trick.
- How it links to a playlist: `tvg-id` = `<channel id>`, then name matching; `x-tvg-url`; several
  guides merged (F1); `tvg-shift`.
- `#create`: small hand-made guide; gzip (`gzip -k`, 7-Zip); never `.zip`.
- `#mistakes`: research §2.5.

### `/guides/xspf/` — XSPF

- `#syntax`: playlist/trackList/track, namespace `http://xspf.org/ns/0/` with `version="1"`, VLC
  extension (`vlc:id`, `vlc:node`).
- `#example`: `/examples/playlist.xspf`. Note: "keep one element per line" (current parser).
- `#create`: VLC *Save Playlist to File → XSPF*, or by hand.
- `#mistakes`: research §3.3.

### `/guides/json/` — JSON

- Two formats on one page, each with its own `#syntax`/`#example` subsection:
  - **Generic JSON accepted by TS IPTV** (research §4.2): accepted top-level shapes (object with
    channels/groups; array of channels; array of groups), field table with aliases, attributes map
    (`user-agent`, `referrer`). Example `/examples/playlist.json`.
  - **iptv-org `streams.json` shape** (research §4.1): field table; stream id `channel@feed`;
    headers from `referrer`/`user_agent`. Example `/examples/iptv-org-streams.json`. Describe the
    format only; no link to iptv-org playlists or API files.
- When to use TS IPTV Source instead (movies, series, layout, DRM) → link.
- `#mistakes`: research §4.3.

### `/guides/xtream-codes/` — Xtream Codes style links

- `#what`: a **provider API**, not a file format; panel software used by many providers; no public
  specification; whether a provider is licensed is a question about that provider. No history
  narrative, no provider names.
- `#syntax`: URL table from research §5 with `U`/`P` placeholders and `provider.example.com`
  (playlist `get.php`, `player_api.php` account info, live/VOD/series stream URLs, `xmltv.php`,
  catch-up shape).
- `#example`: an example `get.php` URL (no file download).
- `#add`: "Paste the `get.php?…&type=m3u_plus&output=ts` link as a playlist link"; add the
  `xmltv.php` link as the guide if the playlist has no `x-tvg-url`. TS IPTV has no separate Xtream
  login screen (state it).
- Security callout: the link contains your username and password; do not share it or post it.
- `#create`, `#host`: "not applicable — your provider hosts it" (sections present, one sentence).

### `/guides/kodi/` — Kodi formats in TS IPTV

- `#what`: Kodi and PVR IPTV Simple Client in one paragraph; TS IPTV reuses **formats**, not add-ons.
- `#syntax` → **What TS IPTV understands**: the dialect (link to the M3U page for basics);
  `#KODIPROP` table (mimetype, manifest_type, `*_headers`, license_type/license_key/drm_legacy/drm)
  with what TS IPTV does with each; DRM key-system table with platform badges (Widevine/PlayReady:
  Android; ClearKey: Android DASH; iOS/desktop: not supported); supported `license_key` subset
  (`R{SSM}`/`R`) and what shows "not supported yet"; catch-up modes and placeholders (stored;
  playback later); `.strm` files.
- **What TS IPTV cannot run** (its own H2): video add-ons (`plugin://`), repositories and
  `addons.xml`, zip installs, `#WEBPROP` page scraping — with the one-paragraph reason (Python
  programs that only run inside Kodi; security; store rules). No add-on or repository names.
- `#example`: `/examples/playlist.m3u` (the DRM and headers channels) and `/examples/channel.strm`.
- `#add`: moving from Kodi PVR IPTV Simple: copy the playlist URL/file and the EPG URL from the
  add-on settings; add them in TS IPTV.
- `#mistakes`: pipe headers on manifests (accepted), `license_key` wrappers, ClearKey on HLS,
  WisePlay, `plugin://` entries skipped.

### `/guides/stremio-addons/` — Stremio-compatible addons (wave B)

- `#what`: the open addon protocol: an addon is JSON over HTTP; TS IPTV can use addons that follow
  it. "Addons are third-party services; TS IPTV has no built-in addons and does not recommend any."
- `#syntax`: manifest fields table; resources and URL patterns
  (`/{resource}/{type}/{id}[/{extra}].json`); percent-encoding rules (`%20`, never `+`; `:` →
  `%3A`); extras (`search`, `genre`, `skip`) and paging by 100; meta, video, stream objects;
  `proxyHeaders.request`; caching fields.
- `#support` → **What TS IPTV plays**: `url` streams (HTTP(S), HLS, DASH on Android/desktop, MP4,
  MKV not on iOS) — yes; `externalUrl` — opens the browser; torrent, Usenet, archive and YouTube
  streams — not supported (stated once, in one row, no elaboration); `configurationRequired` flow;
  what is not supported yet (subtitles, EPG addons).
- `#create` → **Create your own addon**, two tracks:
  1. **Static files** (no server): the sampler file tree and files (research §5.2) — link to
     `/examples/stremio-sampler/manifest.json` as the live version; host on GitHub Pages
     (`.nojekyll`), Firebase, Netlify/Vercel (CORS header rule); limits (no free-text search; no `:`
     in ids).
  2. **Node.js SDK**: `package.json` and `addon.js` from research §5.1; run locally
     (`npm start`), test on an Android emulator with `http://10.0.2.2:7000/manifest.json`; deploy to
     any Node host with HTTPS (generic, no host recommendations beyond "any HTTPS Node host").
- `#host`: HTTPS; CORS `*` needed only for web clients; cache headers.
- `#add`: Discover/Profile → Addons → Add addon → paste manifest link (or a `stremio://` link) →
  preview → accept notice.
- `#mistakes`: missing `/manifest.json`; `+` instead of `%20`; `:` in static ids; short pages
  treated as the end; `configurationRequired`; `127.0.0.1:11470` URLs; torrent-only addons showing
  "No playable stream".

### `/guides/tsiptv-source/` — TS IPTV Source (wave C)

- `#what`: one JSON file for a complete branded collection; open and documented; links to the
  schema and the normative spec (rendered from `docs/tsiptv-source-format.md` into this page's
  appendix or a sub-page).
- **Quick start**: the 8-line minimal file inline.
- `#syntax`: guided tour with one short example per part: top level; `meta`; localized text;
  `appearance` (with the contrast rule explained); `layout` (hero/row/grid, `query` with `from`,
  `include`, `ids`, filters, `sort`, `limit`, `groupChips`, `seeAll`); `channels` (headers, DRM,
  catch-up, radio); `movies`; `series` with seasons/episodes; `epg`; `includes` and how they map into
  pools; ids and namespacing (`include:item`); limits table; validation codes table; versioning and
  `x-` members.
- `#example`: the three examples, each with **Download** and a picture of how it renders on phone
  and TV (screenshots taken after F3 ships, using the examples only).
- `#create` → **Create your own, step by step**:
  1. Copy `minimal-live.tsiptv.json`; change `id` (reverse-DNS) and `meta.name`.
  2. Add channels (`url`, `groups`, `number`, `logo`, `headers`).
  3. Add movies and a series.
  4. Add `appearance` and a `layout`.
  5. Validate: in VS Code the `$schema` line gives autocompletion and errors; or paste into the
     validator page.
  6. Host it (see `#host`).
  7. Add it in TS IPTV and check the import summary.
- `#host`: same options as M3U; gzip allowed; keep the URL stable; bump `revision`.
- `#add`: link or file; preview screen; "This source contacts these servers".
- `#mistakes`: `url` and `streams` together; `:` in ids; `rtmp://` streams (use M3U); duplicate
  ids; hero with both `items` and `query`; `groupChips` on movies; colours with low contrast; http
  images blocked by some hosts.

### `/guides/tsiptv-source/validate/` — validator (wave C)

- A textarea and a file input; **Validate** runs entirely in the browser (no upload, no network
  except loading `/schema/tsiptv-source-v1.json`).
- Implementation: a vendored JSON Schema 2020-12 validator bundle (for example the Ajv 2020
  browser bundle, MIT) in `assets/vendor/` with its licence file; no CDN. Plus a small hand-written
  pass for rules the schema cannot express: duplicate ids, `query.include`/`ids`/`target`
  references to unknown ids or includes, `xmltv` includes used in queries, `endYear < year`.
- Output: "Valid" or a list of `path — message` lines, grouped as errors/warnings, in the pane
  language. The validator never fetches includes.
- If no suitable bundle can be vendored under 200 KB, this page is **cut** from wave C (the
  VS Code `$schema` path remains) and the PO is told; it is not replaced by a CDN script.

### `/guides/monplayer/` — MonPlayer note

Short page, exactly these facts and nothing else:

- MonPlayer is a third-party app. The format of its "JSON sources" is **not publicly documented**
  (no specification on its store listing or developer website, checked September 2026).
- TS IPTV therefore cannot import that JSON format today. If what you have is an ordinary M3U
  playlist, add it as an M3U (link to `/guides/m3u/`).
- To add support, TS IPTV needs **one sample file that you created yourself or are allowed to
  share**, with every stream link replaced by an `example.com` placeholder, and whether it came from
  a link or a file. Send it to the support address.
- TS IPTV does not connect to MonPlayer's services or catalogues and will not add them.

No links to MonPlayer, its developer, store listings, press articles or any site that distributes
its links. The page carries `<meta name="robots" content="noindex">` — decision: the page exists
for users who ask support, and the PO does not want TS IPTV to rank for searches that lead people to
an app associated in the press with unlicensed redistribution. It is linked from the hub only.

---

## SEO and page basics

- Each page: unique `<title>` and `meta description` in Vietnamese (default pane) with
  `data-title-en` for the English title; `link rel="canonical"` to the clean URL;
  `link rel="alternate" hreflang="vi"` (clean URL), `hreflang="en"` (`?lang=en`) and
  `hreflang="x-default"`; Open Graph title/description/image (existing `og-image.png`).
- Title pattern: `M3U và M3U8 — Hướng dẫn TS IPTV` / `M3U and M3U8 — TS IPTV guides`.
- One H1 per pane; headings in order; `lang` attribute switched by `lang.js`.
- `/sitemap.xml` lists every indexable page (MonPlayer and validator excluded); `/robots.txt`
  allows all and points to the sitemap.
- Optional: JSON-LD `TechArticle` per format page (`inLanguage` for both), only if it adds no script.
- Performance: no new third-party scripts; pages work without JavaScript except the copy buttons,
  language toggle (both panes remain in the DOM) and validator.

## Accessibility and mobile

- Works at 320–360 px width with **no horizontal page scroll**: tables wrapped in `.scroll-x`,
  `pre` scrolls inside itself, long URLs wrap.
- Copy and download controls are real `<button>`/`<a>` elements, ≥ 44×44 px touch target, visible
  focus, labelled in the pane language.
- Colour contrast of new styles ≥ 4.5:1 for text.

## Checks (automation)

1. `web/scripts/check_guides.py` (Python 3 + `jsonschema`, run in CI and before deploy):
   - every `web/public/examples/*.tsiptv.json` validates against the schema (draft 2020-12);
   - every `<pre data-example="…">` in both panes equals the referenced file after HTML-entity
     decoding and line-ending normalisation;
   - every format page has all eight anchors in both panes, and both panes are non-empty;
   - denylist terms absent; every outbound link host is on the allowlist;
   - every internal link and `/examples/…` link resolves to a file;
   - `<title>`, description, canonical and hreflang present.
2. `composeApp/src/desktopTest/.../WebExamplesParseTest.kt`: parses every non-TS-source example file
   from `web/public/examples/` with the app's parsers (after F1) and asserts the expected counts
   (`playlist.m3u` → 6 channels, 1 radio; `guide.xml` → 2 channels, ≥ 4 programmes; `playlist.xspf`
   → 2; `playlist.json` → as written; `iptv-org-streams.json` → 2; `channel.strm` → 1), and the three
   TS IPTV Source examples with the F3 parser (0 errors).

## Release waves

| Wave | Contents | Can be built | Can be published |
|---|---|---|---|
| **F4-0 fixtures** | `examples/stremio-sampler/**`, `examples/playlist.m3u`, `guide.xml`, `policy/addon-blocklist.json`, `firebase.json` headers | now | before F2 QC starts (they are QA fixtures) |
| **A** | hub, IPTV, M3U, XMLTV, XSPF, JSON, Xtream, Kodi, MonPlayer, nav/footer, CSS/JS, sitemap/robots, remaining A example files | now | with the F1 release (the pages describe F1 behaviour) |
| **B** | Stremio page (+ hub card) | after F2 spec freeze | with the F2 release |
| **C** | TS IPTV Source page, validator, schema/examples announcement (+ hub card) | after F3 spec freeze | with the F3 release |

## Acceptance criteria

- **AC-W1** Every URL in the Site structure table for the shipped wave returns 200 on
  `tsiptv-8bdd6.web.app`, with no redirect loop, both with and without `?lang=en`.
- **AC-W2** Every page has a complete Vietnamese and English pane; the toggle switches content,
  `lang` and `<title>`; `?lang=en` opens English; the choice is remembered.
- **AC-W3** Every format page contains the eight anchored sections in both panes (check script).
- **AC-W4** Every example block matches its downloadable file byte-for-byte (check script).
- **AC-W5** Every TS IPTV Source example validates against `/schema/tsiptv-source-v1.json`; every
  other example parses with the app's parsers with the expected counts (desktop test).
- **AC-W6** Denylist and outbound-link allowlist checks pass; manual review confirms no page names
  or links a playlist, provider, addon, add-on, repository or MonPlayer source, and guide content
  does not link to `/playlists/`.
- **AC-W7** At 360 px (Chrome device toolbar, iPhone SE and Pixel presets) no page scrolls
  horizontally; code blocks scroll inside themselves; copy buttons work on Android Chrome and iOS
  Safari.
- **AC-W8** Lighthouse (mobile) for the hub, M3U and TS IPTV Source pages: Accessibility ≥ 90,
  SEO ≥ 90, Best practices ≥ 90.
- **AC-W9** Header nav and footer contain the Guides link on **every** page of the site, including
  privacy, terms, delete-account and contributor pages; existing links are unchanged.
- **AC-W10** `/schema/tsiptv-source-v1.json` and `/examples/*.json` are served as
  `application/json` with `Access-Control-Allow-Origin: *`; `.m3u` as `audio/x-mpegurl`; no
  trailing-slash redirect on any file (`curl -I`).
- **AC-W11** A VS Code user opening `minimal-live.tsiptv.json` gets autocompletion and an error for
  a misspelt field through `$schema` (manual).
- **AC-W12** Adding `https://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json` in the F2
  build lists the sampler catalogues and plays His Girl Friday; importing each example URL in the F1/F3
  builds shows the expected counts.
- **AC-W13** The MonPlayer page has `noindex`, no outbound links, only the approved statements, and
  is absent from the sitemap.
- **AC-W14** The validator (if not cut) marks the three examples valid, and reports the expected
  paths for fixtures with a typo, a duplicate id, a `:` in an id and an unknown include reference —
  with the network tab showing no request other than page assets and the schema.
- **AC-W15** Every page states that TS IPTV includes no channels, in both languages.
- **AC-W16** A native Vietnamese reviewer and the PO sign off each page's text before its wave is
  published.

## Implementation plan (ordered)

1. **Fixtures (F4-0)**: `web/public/examples/stremio-sampler/**` from research §5.2,
   `examples/playlist.m3u`, `examples/guide.xml`, `web/public/policy/addon-blocklist.json`,
   `firebase.json` headers; deploy (`firebase deploy --only hosting`) and verify with `curl -I`.
2. **Design additions**: `web/public/assets/site.css` (pre/code/toc/breadcrumb/callout/badge/copy),
   `assets/guides.js`, `assets/lang.js` title switching.
3. **Template**: one guide page skeleton (`web/public/guides/m3u/index.html`) with both panes,
   breadcrumb, TOC, anchors, example block, related guides; reviewed by the PO before the other pages
   are written.
4. **Wave A pages** (`web/public/guides/{,iptv,m3u,xmltv,xspf,json,xtream-codes,kodi,monplayer}/index.html`)
   and remaining A example files; nav/footer on all existing pages (`index.html`, `privacy/`, `terms/`,
   `delete-account/**`, `contributor/**`, `playlists/`); `sitemap.xml`, `robots.txt`.
5. **Checks**: `web/scripts/check_guides.py`; `WebExamplesParseTest.kt` (after F1 is merged).
6. **Wave B**: `guides/stremio-addons/index.html`, hub card.
7. **Wave C**: `guides/tsiptv-source/index.html` (+ spec appendix), `guides/tsiptv-source/validate/`
   with `assets/vendor/` bundle and licence, hub card, screenshots.
8. Update `web/README.md` page table for every new page.

## Decision (2026-09-30): shared site navigation

AC-W6 applies to guide **content** (the language panes). The site-wide header nav, generated by `web/scripts/site_nav.py` and identical on every page, is site chrome and may link to `/playlists/`. `check_guides.py` checks the panes for that rule and fails on any page whose nav differs from the shared block.

