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
| Support / donate | `https://tsiptv-8bdd6.web.app/support/` | Ways to support the developer; linked from the home footer and the guides hub. **Never link it from the Play build of the app** (see below) |
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

- **Header navigation** is generated: `python web/scripts/site_nav.py` writes the same
  `<nav class="site-nav">` (icon pills, bilingual labels, `aria-current` on the current section)
  and the inline `<script type="speculationrules">` into every hand-written page; guide pages get
  them from `build_guides.py`. Edit the nav only in `scripts/site_nav.py`, then run it.
  `check_guides.py` fails if a page's nav differs. Sticky header, smooth scrolling and
  cross-page view transitions are CSS in `site.css` (disabled under `prefers-reduced-motion`).
- Shared styling lives in `public/assets/site.css`; the palette mirrors
  `composeApp/src/commonMain/kotlin/tss/t/tsiptv/ui/themes/TSColors.kt`.
- The language toggle is `public/assets/lang.js`; panes are marked with
  `data-lang-pane="vi|en"`.
- Images in `public/assets/` are produced by `brand/generate_assets.py`.
- When you change the substance of a policy, bump the `<time>` element and the
  visible date in **both** language panes.

## Support page (`/support/`)

Files: `public/support/index.html` (text, hand-written, nav from `site_nav.py`),
`public/assets/support.js` (draws the cards), `public/assets/support.css`, and **the only file
you edit for payment details: `public/assets/support-config.js`**. QR images go in
`public/assets/support/`.

How it behaves
- Every value that is still `CHANGE_ME` (or empty) counts as not filled in. A method appears
  only when `enabled: true` **and** its required fields are filled in. Each crypto address
  appears only when its own `address` is filled in. With nothing filled in, visitors see
  "Ways to support will be listed here soon".
- Preview: open `/support/?preview=1` (or any `localhost` URL, e.g. the hosting emulator) to see
  every method, with a red "Not configured" badge and a "QR coming soon" placeholder for what is
  missing. Visitors never see that state.
- Links must be `https://`; "Open app" only accepts `momo://` and `zalopay://` and falls back to
  the web link after 1.5 s. QR paths must be files under `/assets/support/`. No third-party
  scripts or images; nothing is generated in the browser.

### Fill in your details

Edit `public/assets/support-config.js`, replace each `CHANGE_ME`, then preview with
`?preview=1` and scan every QR with a phone before deploying. Never commit made-up values.

| Method | Fields | Where to get them |
|---|---|---|
| VietQR / Napas 247 | `bankName`, `accountNumber`, `accountName` (capitals without accents, exactly as the bank shows), optional `branch`; `qr: "/assets/support/vietqr.png"` | Generate the QR image in your banking app ("Mã QR nhận tiền"), or on vietqr.io (choose the bank, enter the account number and name, leave the amount empty, download the PNG). Save it as `public/assets/support/vietqr.png`. |
| MoMo | `phone` and/or `link` (your MoMo receive link, `https://…`), optional `accountName`; `qr: "/assets/support/momo.png"` | MoMo app › "Nhận tiền" / "Mã QR của tôi": save the QR image; copy the share link if offered. |
| Zypage | `url` (`https://…` of your creator page) | Your Zypage profile. |
| Crypto | for each entry: `address`, optional `memo`, optional `qr` image | Your wallet or exchange "Deposit/Receive" screen. **Check the network**: USDT TRC20 addresses start with `T`, BEP20 and ERC20 with `0x`, BTC with `bc1`, `1` or `3`. Delete entries you do not use. Send a small test amount first. |
| `transferNote` | Optional note shown with bank and wallet methods | ASCII only (e.g. `TSIPTV ung ho`). |

Suggested extra methods are in the same file with `enabled: false`; set `enabled: true` after
filling them in:

| Method | Reach | Typical cost to you (check current rates) | Notes |
|---|---|---|---|
| VietQR / Napas 247 | VN, every bank app | Free for personal accounts | Best default in Vietnam. |
| MoMo, ZaloPay, ShopeePay | VN wallets | Free person-to-person; merchant QR has fees | Personal QR is enough for donations. |
| VNPAY-QR | VN, most bank apps | Merchant fee (~1 %) | Needs a VNPay merchant QR; only if you already have one. |
| Zypage | VN creators | Platform fee on payouts | Vietnamese "buy me a coffee" style page. |
| PayPal.me | International | ~3–5 % + fixed fee, plus FX; VN accounts can receive but withdrawing to a VN bank costs extra | Widest international reach. |
| Ko-fi | International | 0 % platform fee on one-off donations (PayPal/Stripe fees apply) | Payouts via PayPal or Stripe. |
| Buy Me a Coffee | International | 5 % + processor fees | Payouts via Stripe/Payoneer; check VN support. |
| GitHub Sponsors | Developers worldwide | 0 % for personal sponsorships | Needs a supported payout country (Stripe Connect); check VN eligibility. |
| Stripe Payment Link | International cards, Apple/Google Pay | ~3–4 % + fixed fee | Stripe is not available to VN businesses directly; needs an entity in a supported country. |
| Binance Pay ID | Crypto users | Free between Binance accounts | Simpler than on-chain addresses for Binance users. |
| Crypto addresses | Global | Network fee paid by the sender | Irreversible; wrong network = lost funds (the page warns). |

**Google Play policy.** Do not show this page, a donate QR, a bank account or any "donate"
button or link inside the Play build of the app (phone or TV): Play's Payments policy forbids
leading users to non-Play payment for the developer. Inside the Play app use a Play Billing
"Support the developer" product instead. Details: `docs/prd-tv-ads.md` §6.1.

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