# TS IPTV public site

Static pages required by the Google Play Console listing, served from Firebase
Hosting on the existing project `tsiptv-76d8f`.

| Page | URL | Used for |
| --- | --- | --- |
| Landing | `https://tsiptv-76d8f.web.app/` | Store listing "Website" field |
| Privacy policy | `https://tsiptv-76d8f.web.app/privacy/` | **Required** — Play Console → App content → Privacy policy |
| Account deletion | `https://tsiptv-76d8f.web.app/delete-account/` | **Required** — Play Console → App content → Data deletion |
| Terms of use | `https://tsiptv-76d8f.web.app/terms/` | Optional, linked from the listing |

Each page carries both Vietnamese and English in one document, toggled client
side. `?lang=en` forces English, so the English listing can deep-link to
`https://tsiptv-76d8f.web.app/privacy/?lang=en`.

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
`tsiptv-76d8f`. If you later move to a custom domain, add it under
Hosting → Custom domains and update the URLs in the Play Console listing.

## Editing

- Shared styling lives in `public/assets/site.css`; the palette mirrors
  `composeApp/src/commonMain/kotlin/tss/t/tsiptv/ui/themes/TSColors.kt`.
- The language toggle is `public/assets/lang.js`; panes are marked with
  `data-lang-pane="vi|en"`.
- Images in `public/assets/` are produced by `brand/generate_assets.py`.
- When you change the substance of a policy, bump the `<time>` element and the
  visible date in **both** language panes.
