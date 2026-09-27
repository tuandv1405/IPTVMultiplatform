# PRD — Contributor programme: publish your own IPTV playlist

Status: **Implemented 2026-09-27; onboarding superseded by [v2](prd-contributor-requests.md)** · Owner: PO

> **v2 (same day):** contributors now sign in with **Google**, send a **request** that an admin approves, and only then upload playlists; the programme runs on **Firestore now** and on the own server (SQLite) later. Onboarding, pages and storage below are replaced by `docs/prd-contributor-requests.md`; review rules, anti-copy checks, SSRF-safe verification and the Telegram bot still apply.
Components: `web/public/contributor/`, `web/public/playlists/`, `telegram-bot/`, `firestore.rules`

---

## Problem

People who build and maintain their own playlists have no way to share them
through TS IPTV, and the publisher has no controlled way to accept them. Any
public list must be (a) traceable to a real, reachable person, (b) declared to
be the contributor's own work, and (c) reviewed by a human before anyone else
sees it.

## Goals

1. A contributor signs in **on the web**, verifies **email and phone number**,
   accepts the contributor policy, and can then submit playlists.
2. Every submission is **verified automatically** (the link must work and parse
   as a playlist) before it reaches a reviewer.
3. Every submission lands in **IN REVIEW** and is pushed to Telegram — to the
   publisher's own account (`@tuandv1405`) and to a review group — where any
   member of that group can approve or reject it.
4. Approved playlists are published in a **public directory** on the website.
5. The publisher can take any playlist down, and ban a contributor, at any time.
6. Email and phone number are **mandatory, encrypted at rest, and never shown
   publicly**.

## Non-goals (this release)

- **No in-app catalogue.** Approved lists are published on the website, not
  bundled into, or browsable from, the Android/iOS app. The app's store listing,
  landing page and Terms all say *"TS IPTV provides no content"*, and
  `docs/monplayer-parser-plan.md` explains why a built-in source list is the
  fastest route to a Play removal for an IPTV app. A person can still copy a
  directory link into the app's normal *Import playlist* screen. Surfacing the
  directory inside the app is a separate product decision that needs its own
  policy review.
- Email notifications to contributors. Status is shown on the contributor
  dashboard.
- Payments, ranking, comments or ratings on playlists.

---

## Roles

| Role | Who | Can |
| --- | --- | --- |
| Visitor | anyone | Browse approved playlists, report one |
| Contributor | verified account that accepted the policy | Submit, see own submissions, withdraw them |
| Reviewer | every member of the Telegram review group | Approve / reject IN REVIEW submissions |
| Admin (publisher) | IDs / usernames in `.env`, default `@tuandv1405` | Everything a reviewer can, plus take down, ban, list pending |

---

## User journey

```
Web /contributor/            policy + "Become a contributor"
   │
   ▼
Web /contributor/app/
 1. Sign in / create account (Firebase Auth, email + password)
 2. Verify email       → Firebase sends a link; page re-checks on return
 3. Verify phone       → Firebase Phone Auth (SMS code, reCAPTCHA), linked to the account
 4. Accept policy      → public display name + 3 declarations ticked
      └─ POST /api/contributor/register   (server re-checks 2 and 3 from the ID token)
 5. Dashboard
      ├─ New playlist form: name, link, image, description, category, language
      │     "Check link" → POST /api/playlists/verify   (must pass before Submit is enabled)
      │     ownership declarations (3 boxes)            (all required)
      │     Submit → POST /api/submissions              (server verifies the link again)
      └─ My submissions: status, reason when rejected/removed, Withdraw
   │
   ▼  status = IN_REVIEW
Telegram bot → admin DM(s) + review group
   message: image, name, link, channel count, format, contributor public name,
            anti-theft warnings, buttons [✅ Approve] [❌ Reject] [🔄 Re-check link]
   ├─ Approve  → APPROVED, copied to public_playlists, every copy of the message updated
   └─ Reject   → pick a reason → REJECTED, reason shown on the dashboard
Approved message keeps [🗑 Take down] (admins only) → REMOVED
Web /playlists/  lists APPROVED playlists (reads Firestore directly)
```

### Status machine

```
            submit               approve
   (none) ─────────► IN_REVIEW ─────────► APPROVED ───take down──► REMOVED
                        │   ▲                 │
                 reject │   │ (never)          │ withdraw
                        ▼                     ▼
                     REJECTED             WITHDRAWN
                        ▲
     withdraw from IN_REVIEW ──► WITHDRAWN
```

Every transition is conditional on the current status (a transaction), so two
reviewers tapping at once produce one decision; the second sees *"already
decided by …"*.

---

## Verification rules

### Contributor

| Check | Where | Rule |
| --- | --- | --- |
| Signed in | server | Firebase ID token verified with revocation check |
| Email verified | server | token claim `email_verified == true` |
| Phone verified | server | token claim `phone_number` present (only set by Firebase after SMS verification) |
| Policy accepted | server | body carries the current `policyVersion` and all declarations `true` |
| Display name | server | 2–40 chars, letters/digits/space/`._-`, not an email or phone number |
| Not banned | server | `contributors/{uid}.status != BANNED` |

If the policy version changes, contributors must accept it again before their
next submission.

### Playlist link (`/api/playlists/verify` and again on submit)

- `http` or `https` only; no credentials in the URL; port 80/443/8080/8000/8443 or default.
- Host must resolve to **public** addresses only — loopback, private, link-local,
  CGNAT, multicast and metadata addresses are refused, and every redirect hop
  (max 3) is re-checked. This stops the verifier being used to probe the
  server's own network (SSRF).
- 15 s timeout, 15 MB cap.
- Must parse as **M3U/M3U8**, **XSPF** or **JSON** (plain channel array or
  iptv-org style) with at least **1 channel with a stream URL**.
- Returns format, channel count, group count and the first five channel names,
  which the contributor sees before submitting.

### Anti-copying signals (shown to reviewers)

- **Same link already submitted** by another contributor → refused outright
  (409), whatever its status, unless the earlier one was withdrawn or rejected
  for a non-copyright reason.
- **Same content** (SHA-256 of the normalised channel URL list) as a playlist of
  another contributor → accepted, but the Telegram message carries a red
  *"⚠️ Identical channel list to #… by …"* line. Content can legitimately be
  mirrored; a human decides.

### Image

PNG, JPEG or WebP, resized in the browser to at most 512×512 and ≤ 300 KB
decoded; the server checks the magic bytes and size. Stored in Firestore (no
Cloud Storage bucket is needed, so it works on the Spark plan).

### Limits

- 3 submissions IN REVIEW per contributor at a time; 20 live (IN_REVIEW + APPROVED).
- 30 API requests / minute / IP, 10 link checks / minute / user.

---

## Privacy and security of personal data

| Data | Stored as | Visible to |
| --- | --- | --- |
| Email | AES-256-GCM ciphertext + HMAC-SHA256 lookup hash | Nobody publicly. Admins can decrypt with `/contact <id>` in a private chat with the bot, for copyright disputes only |
| Phone | AES-256-GCM ciphertext + HMAC-SHA256 lookup hash | Same as email |
| Public name | plain | Everyone (directory, Telegram) |
| Firebase UID | plain | Reviewers (Telegram), never on the public site |

- Key material lives only in the bot's `.env` (`PII_ENCRYPTION_KEY`,
  `PII_HASH_KEY`). Firestore never holds a key. Ciphertext carries a key ID
  (`v1:`) so the key can be rotated.
- The `contributors`, `submissions` and image collections are **denied to every
  client** by `firestore.rules`; only the bot (Admin SDK) reads or writes them.
  Only `public_playlists` and `public_playlist_images` are world-readable, and
  they hold no personal data.
- Log lines never include email, phone or ID tokens.
- The review group sees the public name and the UID, not contact data.
- Deleting the account on `/delete-account/` first calls `/api/me/delete`: the
  contributor document (ciphertext and hashes) is deleted and every live playlist
  is withdrawn. A suspended contributor keeps a tombstone (status + keyed
  hashes, no ciphertext) so deleting the account does not lift a ban.
- Firebase Auth itself also holds the email and phone (Google-encrypted, not
  exposed); deleting the account on `/delete-account/` removes them there.

The contributor policy page states all of the above in Vietnamese and English.

---

## Components

### Web (Firebase Hosting, static)

| Path | Content |
| --- | --- |
| `/contributor/` | Programme overview + full contributor policy (vi/en) |
| `/contributor/app/` | Sign-in, verification, policy acceptance, submit form, dashboard |
| `/playlists/` | Public directory of approved playlists, with a *Report* link |
| `/privacy/` | New section: contributor data |

The web app talks to the bot's HTTP API. Its base URL lives in
`web/public/assets/contributor-config.js` (placeholder until deployed; the page
says *"not open yet"* instead of failing).

### Telegram bot + API (`telegram-bot/`, Node.js ≥ 20)

One process, two faces:

- **HTTP API** for the web app (`/api/*`, CORS-restricted).
- **Telegram bot** — long polling (local, VPS) or webhook (Cloud Run).

Deployable three ways, all from the same code: local (`npm start`), Google
Cloud Run (`Dockerfile`, webhook mode), VPS (`docker compose` or `systemd`).
Tokens, chat IDs and keys are read from `.env`; `.env.example` lists them all.

#### API

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/healthz` | – | Liveness |
| GET | `/api/policy` | – | Current policy version + declaration keys |
| GET | `/api/me` | ID token | Verification state, contributor profile (no PII) |
| POST | `/api/contributor/register` | ID token | Accept policy, become contributor |
| POST | `/api/playlists/verify` | contributor | Check a link |
| POST | `/api/submissions` | contributor | Submit → IN_REVIEW → Telegram |
| GET | `/api/submissions` | contributor | Own submissions |
| POST | `/api/submissions/:id/withdraw` | contributor | Withdraw own |
| POST | `/api/me/delete` | ID token | Called by `/delete-account/` before the Firebase account is deleted: removes contact data, withdraws playlists |
| POST | `/telegram/webhook` | `X-Telegram-Bot-Api-Secret-Token` header | Webhook mode only |

#### Bot commands

| Command | Who | Effect |
| --- | --- | --- |
| `/start`, `/whoami` | anyone | Replies with the chat ID and user ID (to fill `.env`); registers an admin's DM when their username is in `TELEGRAM_ADMIN_USERNAMES` |
| `/help` | anyone | Command list |
| `/pending` | reviewers | IN REVIEW queue |
| `/takedown <id> [reason]` | admins | APPROVED → REMOVED |
| `/ban <uid> [reason]` | admins | Ban contributor, remove all their live playlists |
| `/contact <id>` | admins, private chat only | Decrypted email/phone of a submission's contributor |
| `/stats` | reviewers | Counts per status |

### Firestore collections

| Collection | Doc | Client access |
| --- | --- | --- |
| `contributors/{uid}` | publicName, emailEnc, emailHash, phoneEnc, phoneHash, policyVersion, acceptedAt, status | none |
| `submissions/{id}` | ownerUid, publicName, name, url, urlHash, description, category, language, verification{…}, contentHash, status, statusReason, reviewedBy, telegramMessages[], timestamps | none |
| `submission_urls/{urlHash}` | submissionId, ownerUid | none |
| `submission_images/{id}` | mime, data (base64) | none |
| `public_playlists/{id}` | name, url, description, category, language, publicName, channelCount, format, approvedAt | read |
| `public_playlist_images/{id}` | mime, data | read |
| `bot_state/{key}` | polling offset, learned admin chats | none |

---

## Ops prerequisites (console, one-off)

1. **Firebase Auth → Sign-in method → Phone** — enable. SMS verification needs
   the project on the **Blaze** plan; add a test number under *Phone numbers
   for testing* for QA.
2. **Authorized domains** — must include `tsiptv-8bdd6.web.app` (already there)
   and the domain the API is served from if different.
3. **Service account** for the bot (Firestore + Auth token verification):
   Cloud Run uses its runtime identity; local/VPS use a key file named in
   `GOOGLE_APPLICATION_CREDENTIALS`.
4. **Telegram:** create the bot with @BotFather, add it to the review group,
   send `/whoami` in the group and in a DM from `@tuandv1405`, and copy the IDs
   into `.env`.

---

## Acceptance criteria

Contributor onboarding

- **AC-C1** Without signing in, `/contributor/app/` shows only the sign-in form.
- **AC-C2** A signed-in user with an unverified email cannot reach the phone
  step; the server rejects `register` with `email_not_verified`.
- **AC-C3** Without a verified phone, `register` is rejected with `phone_not_verified`.
- **AC-C4** `register` fails unless every declaration is `true` and the
  `policyVersion` is current.
- **AC-C5** After `register`, Firestore holds email and phone only as `v1:` ciphertext
  plus hashes; no plaintext email or phone appears in any document or log line.
- **AC-C6** The policy page states that email and phone are mandatory,
  encrypted, and never shown publicly, and that copied/stolen playlists are
  removed at any time.

Submission

- **AC-S1** *Submit* is disabled until *Check link* has passed for the current link.
- **AC-S2** Links to `localhost`, `127.0.0.1`, `10.x`, `192.168.x`, `169.254.169.254`,
  `[::1]`, or a public host redirecting to one, are refused.
- **AC-S3** A non-playlist page (HTML, empty M3U) is refused with a clear reason.
- **AC-S4** A valid submission is stored as IN_REVIEW and posted to every admin
  chat and to the review group, with the image.
- **AC-S5** A link already submitted by someone else is refused with 409.
- **AC-S6** Identical content to another contributor's playlist is flagged in the
  Telegram message.
- **AC-S7** The 4th simultaneous IN_REVIEW submission is refused with 429.

Review

- **AC-V1** Any member of the review group can approve or reject; the message in
  every chat is updated with the decision and the reviewer's name.
- **AC-V2** Two concurrent decisions produce exactly one transition.
- **AC-V3** A reject requires a reason, which the contributor sees on the dashboard.
- **AC-V4** Approve creates `public_playlists/{id}` and its image; the playlist
  then appears on `/playlists/`.
- **AC-V5** Take down (button or `/takedown`) removes it from `/playlists/` and
  shows *Removed by the publisher* with the reason on the dashboard.
- **AC-V6** Only admins can take down, ban, or read contact data; `/contact`
  works only in a private chat.
- **AC-V7** A non-admin pressing a button in a private chat (e.g. a forwarded
  message) is refused.

Deployment

- **AC-D1** `npm start` runs locally in polling mode with only `.env` filled.
- **AC-D2** `docker build` produces an image that runs in webhook mode on Cloud
  Run with `TELEGRAM_MODE=webhook`.
- **AC-D3** `docker compose up -d` runs on a VPS in polling mode.
- **AC-D4** The process refuses to start when a required variable is missing,
  and names it.
