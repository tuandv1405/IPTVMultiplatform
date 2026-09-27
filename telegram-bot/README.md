# TS IPTV contributor bot

One Node.js process that is both:

- the **contributor API** the website calls (`/api/*`) — verifies the signed-in
  user, checks playlist links, stores submissions, encrypts contact data; and
- the **Telegram review bot** — posts every new playlist to the publisher
  (`@tuandv1405`) and to the review group, where any member can approve or
  reject it.

Product spec: [`docs/prd-contributor-program.md`](../docs/prd-contributor-program.md).

```
browser (web/public/contributor/app)  ──HTTPS──►  this service  ──Admin SDK──►  Firestore
          │ Firebase Auth ID token                     │
          ▼                                            └──Bot API──►  Telegram: admin DMs + review group
   Firebase Auth (email + phone)
web/public/playlists  ──reads public_playlists──►  Firestore
```

Requires Node.js ≥ 20.12. The only dependency is `firebase-admin`.

---

## 1. One-off setup

### Telegram

1. Talk to [@BotFather](https://t.me/BotFather) → `/newbot` → copy the token
   into `TELEGRAM_BOT_TOKEN`.
2. Create the review group, add the bot, and (so the buttons work for everyone)
   leave it as a normal member.
3. Start the bot once (section 3 below), then:
   - in the group send `/whoami` → copy the negative chat ID into
     `TELEGRAM_REVIEW_CHAT_ID`;
   - `@tuandv1405` opens a private chat with the bot and presses **Start**.
     Because `TELEGRAM_ADMIN_USERNAMES=tuandv1405`, the bot remembers that chat
     and sends every new playlist there. `/whoami` also prints the numeric user
     ID; putting it in `TELEGRAM_ADMIN_CHAT_IDS` as well is more robust (usernames
     can change).

### Keys

```bash
npm run gen-keys
```

prints `PII_ENCRYPTION_KEY`, `PII_HASH_KEY` and `TELEGRAM_WEBHOOK_SECRET`.
**Back up the two PII keys** in a password manager: without
`PII_ENCRYPTION_KEY` the stored emails and phone numbers cannot be read again.

### Firebase

- Console → Authentication → Sign-in method → enable **Phone**. SMS needs the
  project on the **Blaze** plan. For testing, add a fictional number under
  *Phone numbers for testing* — no SMS is sent and no quota is used.
- Deploy the rules from the repository root: `firebase deploy --only firestore:rules`.
- Local/VPS only: IAM → Service accounts → create one with **Cloud Datastore
  User** and **Firebase Authentication Viewer**, download a JSON key as
  `telegram-bot/service-account.json` (gitignored).

### Web

Set the API's public URL in
[`web/public/assets/contributor-config.js`](../web/public/assets/contributor-config.js)
and redeploy hosting (`firebase deploy --only hosting`). Until then the
contributor page says the programme is not open yet.

---

## 2. Configuration

Copy `.env.example` to `.env`. Every variable is documented there. The process
refuses to start and lists every problem when something required is missing.

| Variable | Required | Notes |
| --- | --- | --- |
| `TELEGRAM_BOT_TOKEN` | yes | From BotFather |
| `TELEGRAM_MODE` | – | `polling` (default), `webhook`, `off` |
| `TELEGRAM_ADMIN_USERNAMES` | one of these three | `tuandv1405` |
| `TELEGRAM_ADMIN_CHAT_IDS` | ″ | Numeric user IDs, comma-separated |
| `TELEGRAM_REVIEW_CHAT_ID` | ″ | Negative group ID |
| `TELEGRAM_WEBHOOK_URL`, `TELEGRAM_WEBHOOK_SECRET` | webhook mode | Public https base URL + random secret |
| `PII_ENCRYPTION_KEY`, `PII_HASH_KEY` | yes | Two different 32-byte base64 keys |
| `GOOGLE_APPLICATION_CREDENTIALS` | local/VPS | Path to the service-account JSON |
| `CORS_ORIGINS` | – | Defaults to the two Firebase Hosting domains |
| `TRUST_PROXY` | – | `true` behind Cloud Run / a reverse proxy |

---

## 3. Run it

### A. Local machine

```bash
cd telegram-bot
npm ci
cp .env.example .env        # fill it in
npm start
```

Polling mode needs no public URL. To try the API with a local copy of the site,
add `http://localhost:5000` to `CORS_ORIGINS`, set `API_BASE` to
`http://localhost:8080`, and run `firebase emulators:start --only hosting` from
the repository root.

Dry run without Firestore or real accounts (never in production — the config
refuses it):

```bash
STORE_DRIVER=memory ALLOW_DEV_AUTH=true NODE_ENV=development npm start
```

Dev tokens are `dev:` + base64url JSON; `test/helpers.js` shows how they are
built.

### B. Google Cloud Run (webhook)

```bash
cd telegram-bot
gcloud run deploy tsiptv-contributor-bot \
  --project tsiptv-8bdd6 --region asia-southeast1 --source . \
  --allow-unauthenticated --min-instances 0 --max-instances 2 \
  --set-env-vars TELEGRAM_MODE=webhook,TRUST_PROXY=true,NODE_ENV=production \
  --set-env-vars TELEGRAM_ADMIN_USERNAMES=tuandv1405,TELEGRAM_REVIEW_CHAT_ID=-100xxxxxxxxxx \
  --set-secrets TELEGRAM_BOT_TOKEN=tsiptv-bot-token:latest,PII_ENCRYPTION_KEY=tsiptv-pii-key:latest,PII_HASH_KEY=tsiptv-pii-hash:latest,TELEGRAM_WEBHOOK_SECRET=tsiptv-webhook-secret:latest
```

- Create the four secrets in Secret Manager first and grant the service's
  runtime service account **Secret Manager Secret Accessor**, **Cloud Datastore
  User** and **Firebase Authentication Viewer**. No key file is needed.
- After the first deploy, set `TELEGRAM_WEBHOOK_URL` to the service URL
  (`gcloud run services update tsiptv-contributor-bot --update-env-vars TELEGRAM_WEBHOOK_URL=https://…run.app`).
  The service registers the webhook itself at start-up.
- Webhook mode means no always-on instance: Cloud Run scales to zero between
  updates. Do **not** use polling on Cloud Run.
- Optional: serve the API from the site's own domain by adding a Hosting
  rewrite to `firebase.json` and setting `API_BASE = ""`:
  `{ "source": "/api/**", "run": { "serviceId": "tsiptv-contributor-bot", "region": "asia-southeast1" } }`

### C. VPS

With Docker:

```bash
cd telegram-bot
cp .env.example .env               # TELEGRAM_MODE=polling
cp ~/service-account.json .        # the key from step 1
docker compose up -d --build
docker compose logs -f
```

The container listens on `127.0.0.1:8080` only. Put a TLS reverse proxy in front
of it — `deploy/Caddyfile.example` is a two-line Caddy config with automatic
HTTPS — and use that URL as `API_BASE`.

Without Docker: `deploy/tsiptv-bot.service` is a hardened systemd unit; the
install steps are in its header.

---

## 4. Reviewing

Each new playlist arrives as a card with its image, link, channel count and
contributor's public name:

| Button / command | Who | Effect |
| --- | --- | --- |
| ✅ Approve | review group members, admins | Published on `/playlists/` |
| ❌ Reject → reason | review group members, admins | Contributor sees the reason |
| 🔄 Re-check link | review group members, admins | Fetches the link again |
| 🗑 Take down | admins | Removed from `/playlists/` |
| `/pending` | reviewers | Re-posts the queue here |
| `/stats` | reviewers | Counts per status |
| `/takedown <id> [reason]` | admins | Same as the button, with a reason |
| `/ban <uid> [reason]` | admins | Suspends the contributor, removes all their playlists |
| `/contact <id>` | admins, private chat only | Decrypts the contributor's email and phone |

A decision made anywhere updates every copy of the card. Two reviewers pressing
at once cannot both win: transitions are transactional.

🚩 on a card means the channel list is identical to another contributor's
playlist — a strong hint of copying.

---

## 5. Account deletion

`/delete-account/confirm/` calls `POST /api/me/delete` before deleting the
Firebase account: the contributor document (encrypted email/phone) is deleted
and live playlists are withdrawn. That call never blocks deletion, so as a
safety net the service sweeps every hour for contributors whose Firebase
account no longer exists and purges them (only `auth/user-not-found` counts as
deleted). On Cloud Run the sweep runs only while an instance is up; to make it
reliable there, add `--min-instances 1` or a Cloud Scheduler job that hits the
service hourly. A suspended contributor leaves a tombstone of keyed hashes, and
registering again with the same email or phone number is refused.

## 6. Tests

```bash
npm test
```

48 tests: the SSRF guard (private ranges, redirects to private hosts, DNS
answers mixing public and private addresses), the parsers, encryption, and
the whole contributor → Telegram → public directory flow over real HTTP with an
in-memory store and a recording fake of the Bot API.
