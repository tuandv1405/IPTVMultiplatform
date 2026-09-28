# TS IPTV public site

Static pages required by the Google Play Console listing, served from Firebase
Hosting on the existing project `tsiptv-8bdd6`.

| Page | URL | Used for |
| --- | --- | --- |
| Landing | `https://tsiptv-8bdd6.web.app/` | Store listing "Website" field |
| Privacy policy | `https://tsiptv-8bdd6.web.app/privacy/` | **Required** — Play Console → App content → Privacy policy |
| Account deletion | `https://tsiptv-8bdd6.web.app/delete-account/` | **Required** — Play Console → App content → Data deletion |
| Terms of use | `https://tsiptv-8bdd6.web.app/terms/` | Optional, linked from the listing |
| Contributor programme | `https://tsiptv-8bdd6.web.app/contributor/` | Policy; opened from the app's Profile tab |
| Apply as contributor | `https://tsiptv-8bdd6.web.app/contributor/request/` | Google sign-in, one request at a time |
| Upload a playlist | `https://tsiptv-8bdd6.web.app/contributor/submit/` | Approved contributors only |
| Contributor admin | `https://tsiptv-8bdd6.web.app/contributor/admin/` | Firebase project owner (or `admin` claim) |
| Community playlists | `https://tsiptv-8bdd6.web.app/playlists/` | Approved playlists, read from Firestore |
| Format guides hub | `https://tsiptv-8bdd6.web.app/guides/` | Entry point to every format guide; linked from nav and footer |
| What is IPTV | `https://tsiptv-8bdd6.web.app/guides/iptv/` | Basics: stream, playlist, guide, glossary |
| M3U / M3U8 | `https://tsiptv-8bdd6.web.app/guides/m3u/` | Full M3U dialect incl. Kodi attributes (F1 behaviour) |
| XMLTV | `https://tsiptv-8bdd6.web.app/guides/xmltv/` | Programme guide format |
| XSPF | `https://tsiptv-8bdd6.web.app/guides/xspf/` | XSPF / VLC playlists |
| JSON | `https://tsiptv-8bdd6.web.app/guides/json/` | Generic JSON and iptv-org `streams.json` shape |
| Xtream Codes | `https://tsiptv-8bdd6.web.app/guides/xtream-codes/` | Provider API URL shapes (facts only) |
| Kodi formats | `https://tsiptv-8bdd6.web.app/guides/kodi/` | `#KODIPROP`, DRM, catch-up, `.strm`; what cannot run |
| Stremio-compatible addons | `https://tsiptv-8bdd6.web.app/guides/stremio-addons/` | Addon protocol, static and SDK addons, sample addon (wave B, publish with F2) |
| TS IPTV Source | `https://tsiptv-8bdd6.web.app/guides/tsiptv-source/` | Full format reference, examples, create/host/add (wave C, publish with F3) |
| TS IPTV Source validator | `https://tsiptv-8bdd6.web.app/guides/tsiptv-source/validate/` | `noindex`; in-browser validator (vendored Ajv in `assets/vendor/`, MIT), not in the sitemap |
| MonPlayer note | `https://tsiptv-8bdd6.web.app/guides/monplayer/` | `noindex`, linked from the hub only, not in the sitemap |
| Example files | `https://tsiptv-8bdd6.web.app/examples/…` | Guide examples and QA fixtures (incl. `stremio-sampler/`); never suggested in the app |
| Addon blocklist | `https://tsiptv-8bdd6.web.app/policy/addon-blocklist.json` | F2 kill switch, `{"ids":[],"hosts":[]}` |
| Sitemap / robots | `/sitemap.xml`, `/robots.txt` | Indexable pages only |

### Format guides: editing and checks

**Workflow: edit the sources, run `build_guides.py`, run `check_guides.py`.** Never edit
`public/guides/**` by hand: those pages are generated.

1. Edit the sources in `guides-src/`: `<slug>.vi.html` and `<slug>.en.html` hold the body of each
   language pane (`hub.*` is `/guides/`). Titles, descriptions, breadcrumb labels and `noindex`
   are in the `PAGES` table at the top of `scripts/build_guides.py`. The shared head, header,
   footer and scripts are its `TEMPLATE`. For a new page, add a `PAGES` row, its two source
   files, a hub card, and a sitemap entry.
2. `python web/scripts/build_guides.py` writes `public/guides/<slug>/index.html`. It fills
   every `<pre data-example="/examples/<file>">` block from that file, so to change an example,
   edit the file in `public/examples/`, not the block. `--check` only reports out-of-date pages.
3. `python web/scripts/check_guides.py` before every deploy (needs `pip install jsonschema`).
   It fails if any generated page differs from what the sources produce. It also checks example
   blocks, anchors, the denylist, the outbound-link allowlist, internal links, head tags, the
   sitemap, and the Guides link in every page's nav and footer. `--fix` runs the build first.

- Validator: `public/assets/tsiptv-validate.js` (core + UI) uses `public/assets/vendor/ajv2020.min.js`
  (Ajv 8.17.1 from `ajv-dist`, MIT, licence next to it; no CDN). `node web/scripts/test_validator.js`
  checks it against the examples and `scripts/validator-fixtures/`; `check_guides.py` runs it.
  When the schema changes, re-run the test; when upgrading Ajv, keep the bundle under 200 KB.
- Anchor ids: Vietnamese pane `what`, `syntax`, …; English pane `what-en`, `syntax-en`, …
  (`assets/guides.js` maps `#syntax` to the visible pane and adds the copy buttons).
- Content rules (placeholders only, allowlisted spec links, no source listing):
  `docs/prd-web-format-guides.md`.

Each page carries both Vietnamese and English in one document, toggled client
side. `?lang=en` forces English, so the English listing can deep-link to
`https://tsiptv-8bdd6.web.app/privacy/?lang=en`.

## 1. Fill in the support email (required before the first deploy)

Every page ships with the literal placeholder `{{SUPPORT_EMAIL}}` so it cannot be
published by accident with the wrong address.

```bash
python web/set-support-email.py you@example.com
```

Verify nothing is left over:

```bash
grep -r "{{SUPPORT_EMAIL}}" web/public && echo "STILL UNSET" || echo "ok"
```

## 2. Preview locally

```bash
firebase emulators:start --only hosting     # http://localhost:5000
```

## 3. Deploy

```bash
firebase login
firebase deploy --only hosting
```

`firebase.json` and `.firebaserc` at the repository root already point at
`tsiptv-8bdd6`. If you later move to a custom domain, add it under
Hosting → Custom domains and update the URLs in the Play Console listing.

## Editing

- Shared styling lives in `public/assets/site.css`; the palette mirrors
  `composeApp/src/commonMain/kotlin/tss/t/tsiptv/ui/themes/TSColors.kt`.
- The language toggle is `public/assets/lang.js`; panes are marked with
  `data-lang-pane="vi|en"`.
- Images in `public/assets/` are produced by `brand/generate_assets.py`.
- When you change the substance of a policy, bump the `<time>` element and the
  visible date in **both** language panes.

## Contributor pages

All data access goes through `public/assets/contributor-backend.js`, selected by
`BACKEND` in `public/assets/contributor-config.js`:

- `"firestore"` (now): browsers read and write Firestore; `firestore.rules` is
  the backend. Tests: `firestore-tests/` (`npm test`, uses the emulators).
- `"http"` (own server later): the same pages call `telegram-bot/`. Switch
  procedure: `telegram-bot/README.md` → "Moving from Firestore to the own server".

`CONTACT_PUBLIC_KEY` in the same file encrypts contributors' contact data in the
browser; the private key stays with the admin (never in this repository).
Spec: `docs/prd-contributor-requests.md`.