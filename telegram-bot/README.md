# TS IPTV contributor server (own-server phase) + Telegram review bot

Spec: [`docs/prd-contributor-requests.md`](../docs/prd-contributor-requests.md)
(v2: Google sign-in → request → admin approval → playlist upload).

The contributor programme runs in two phases with the **same web pages**:

| Phase | `BACKEND` in `web/public/assets/contributor-config.js` | Where data lives | Who enforces the rules |
| --- | --- | --- | --- |
| **Now** | `"firestore"` | Cloud Firestore | `firestore.rules` (tested in `firestore-tests/`) |
| **Later** | `"http"` | this server — **SQLite** on a VPS / physical machine (or Firestore on Cloud Run) | this server |

This folder is the later phase, ready now: an HTTP API with exactly the
operations the pages use, plus an optional Telegram bot that posts every new
request and playlist to the publisher (`@tuandv1405`) and a review group.
Identity stays with Firebase Auth (Google sign-in) in both phases; only the data
moves.

```
browser pages ──(Firebase ID token)──► this server ──► SQLite file (./data/contributors.sqlite)
                                            └──► Telegram: admin DMs + review group (optional)
```

Node.js ≥ 22.13 (`node:sqlite` is built in). The only dependency is `firebase-admin`.

---

## Contact data never exists in the clear on the server

Full name, email and phone are encrypted **in the browser** with the publisher's
public key (RSA-OAEP-3072 + AES-256-GCM, `src/contactCrypto.js` = the site's
`assets/contact-crypto.js`, kept identical by a test). The server stores the
envelope as-is and refuses a request whose contact data is not encrypted. Admins
decrypt in the web console by loading the private key file; set
`CONTACT_PRIVATE_KEY_FILE` only if you also want Telegram `/contact`.

```bash
npm run gen-contact-keypair -- <dir-outside-the-repo>
```

creates `contact-private-key.json` and prints the public key for
`contributor-config.js`. **Back the private key up** — without it, stored contact
data cannot be read. (The current key was generated on 2026-09-27 into
`C:\Users\Admin\.tsiptv-keys\`.)

---

## Moving from Firestore to the own server

1. On the server: `npm ci`, copy `.env.example` → `.env`, keep `STORE_DRIVER=sqlite`.
2. Copy the data (safe to re-run; do a dry run first, then again right before the switch):
   ```bash
   GOOGLE_APPLICATION_CREDENTIALS=./service-account.json npm run migrate -- ./data/contributors.sqlite
   ```
3. Start the server (below) and put it behind HTTPS.
4. In `web/public/assets/contributor-config.js` set `BACKEND = "http"` and
   `API_BASE = "https://<your server>"`, then `firebase deploy --only hosting`.
5. Optionally lock Firestore down afterwards (deny the contributor collections in
   `firestore.rules`) so nothing writes to the old store.

---

## Run it

### Local machine

```bash
cd telegram-bot
npm ci
cp .env.example .env     # fill it in
npm start
```

Dry run without Firebase or real accounts (refused in production):

```bash
STORE_DRIVER=memory ALLOW_DEV_AUTH=true NODE_ENV=development TELEGRAM_MODE=off npm start
```

### VPS / physical machine (recommended target)

With Docker:

```bash
cp .env.example .env               # STORE_DRIVER=sqlite, TELEGRAM_MODE=polling
cp ~/service-account.json .        # Firebase Authentication Viewer
docker compose up -d --build       # database in ./data — back that directory up
```

The container listens on `127.0.0.1:8080`; put `deploy/Caddyfile.example` (or
nginx) in front for HTTPS. Without Docker, `deploy/tsiptv-bot.service` is a
hardened systemd unit (its header has the install steps; the database goes to
`/opt/tsiptv-bot/data`).

Backups: SQLite in WAL mode — copy the file with `sqlite3 contributors.sqlite ".backup backup.sqlite"`,
or stop the service and copy `data/`.

### Google Cloud Run (alternative)

`STORE_DRIVER=firestore`, `TELEGRAM_MODE=webhook`, `TRUST_PROXY=true`; the
runtime service account needs **Cloud Datastore User** and **Firebase
Authentication Viewer**. It uses the same Firestore collections as the
Firestore phase, so no migration is needed.

```bash
gcloud run deploy tsiptv-contributor-bot --project tsiptv-8bdd6 --region asia-southeast1 --source . \
  --allow-unauthenticated --min-instances 0 --max-instances 2 \
  --set-env-vars STORE_DRIVER=firestore,TELEGRAM_MODE=webhook,TRUST_PROXY=true,NODE_ENV=production \
  --set-env-vars ADMIN_EMAILS=chintk111999@gmail.com,TELEGRAM_ADMIN_USERNAMES=tuandv1405,TELEGRAM_REVIEW_CHAT_ID=-100xxxxxxxxxx \
  --set-secrets TELEGRAM_BOT_TOKEN=tsiptv-bot-token:latest,TELEGRAM_WEBHOOK_SECRET=tsiptv-webhook-secret:latest
# then: --update-env-vars TELEGRAM_WEBHOOK_URL=https://<service>.run.app
```

---

## Telegram (optional)

1. [@BotFather](https://t.me/BotFather) → `/newbot` → `TELEGRAM_BOT_TOKEN`.
2. Add the bot to the review group; send `/whoami` there → `TELEGRAM_REVIEW_CHAT_ID`.
3. `@tuandv1405` opens the bot and presses **Start** (or add the numeric ID from
   `/whoami` to `TELEGRAM_ADMIN_CHAT_IDS`).

| Button / command | Who | Effect |
| --- | --- | --- |
| Request card ✅ / ❌ (reason) | review group, admins | Approve → contributor created; reject → reason shown to the user |
| Playlist card ✅ / ❌ (reason) / 🔄 | review group, admins | Publish / reject / re-check the link |
| 🗑 Take down, `/takedown <id> [reason]` | admins | Remove from `/playlists/` |
| `/ban <uid> [reason]`, `/reinstate <uid>` | admins | Suspend (removes live playlists) / lift |
| `/contact <playlist id or uid>` | admins, private chat only | Decrypted name, email, phone (needs `CONTACT_PRIVATE_KEY_FILE`) |
| `/pending`, `/stats`, `/whoami` | reviewers / anyone | Queue, counts, IDs for `.env` |

Decisions made in Telegram and in the web admin console are the same
transitions and update every card.

---

## API (what `contributor-backend.js` calls in `http` mode)

| Method | Path | Who |
| --- | --- | --- |
| GET | `/api/public/playlists`, `/api/public/playlists/:id/image` | anyone |
| GET | `/api/me` | signed in |
| GET / POST | `/api/requests/me`, `/api/requests`, `/api/requests/me/cancel` | signed in (Google) |
| POST | `/api/me/delete` | signed in |
| POST | `/api/playlists/verify` | contributor |
| GET / POST | `/api/submissions`, `/api/submissions/:id/withdraw` | contributor |
| GET / POST | `/api/admin/requests`, `/api/admin/requests/:uid/decide` | admin |
| GET / POST | `/api/admin/contributors`, `/api/admin/contributors/:uid/status` | admin |
| GET / POST | `/api/admin/submissions`, `…/:id/image`, `…/:id/decide` | admin |

On the own server links are verified for real (SSRF-safe fetch, playlist
parsing) and a failing link blocks the upload; in the Firestore phase the
browser can only try.

---

## Tests

```bash
npm test                     # 69 tests: every flow on the memory AND the SQLite store,
                             # SSRF guard, parsers, crypto, migration, QC regressions
cd ../firestore-tests && npm test
                             # firestore.rules (emulator) + the site's real backend code
                             # in both modes: Firestore, and HTTP against this server
```

## Account deletion

`/delete-account/confirm/` asks the backend to delete the user's request (the
encrypted contact data) and withdraw their playlists before deleting the
Firebase account. On the own server an hourly sweep also purges users whose
Firebase account no longer exists (only `auth/user-not-found` counts; at most
20 per run; skipped against the Auth emulator).
