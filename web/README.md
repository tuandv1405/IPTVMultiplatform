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