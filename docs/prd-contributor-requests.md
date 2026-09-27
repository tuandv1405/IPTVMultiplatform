# PRD v2 — Contributor requests, admin console, playlist upload

Status: **Implemented 2026-09-27** · Owner: PO · Supersedes the onboarding part of
[`prd-contributor-program.md`](prd-contributor-program.md) (self-registration after
email + SMS verification). Review rules, anti-copy checks, the public directory
and the Telegram bot design stay as described there.

---

## What changes

| Before (v1) | Now (v2) |
| --- | --- |
| Email/password + SMS verification, then self-registration | **Google sign-in**, then a **request** reviewed by an admin |
| Needs the contributor API (VPS / Cloud Run) to work at all | Works **today on Firestore only**; the same pages switch to the own-server API later with one config value |
| Reviewing only in Telegram | **Admin web console**, signed in as the Firebase project owner; Telegram joins when the server runs |

## Pages

| Page | Who | Purpose |
| --- | --- | --- |
| `/contributor/` | everyone | Programme overview + contributor policy |
| `/contributor/request/` | Google-signed-in users | Request to become a contributor; see the request's status |
| `/contributor/submit/` | **contributors only** | Upload a playlist; see own playlists; withdraw |
| `/contributor/admin/` | **admin only** | Review requests, contributors and playlists |
| `/playlists/` | everyone | Approved playlists |

`/contributor/app/` (v1) redirects to `/contributor/request/`.

## Accounts and roles

- **User**: anyone signed in with Google. Google accounts arrive with a verified email.
- **Contributor**: a user whose request an admin approved (`contributors/{uid}.status == ACTIVE`).
- **Admin**: the Firebase project owner, `chintk111999@gmail.com`, plus any account
  given the `admin: true` custom claim (`telegram-bot/scripts/set-admin-claim.js`).
  Admin rights are enforced by Firestore rules, not by hiding buttons.

## Request to become a contributor

Form fields:

| Field | Required | Stored as | Visible to |
| --- | --- | --- | --- |
| Public display name (2–40) | yes | plain | everyone once approved |
| Full name | yes | **encrypted** | admin with the private key |
| Phone number | yes | **encrypted** | admin with the private key |
| Google email | automatic | **encrypted** | admin with the private key |
| About you / what you maintain (20–1000) | yes | plain | the user, admins |
| Up to 3 sample links | no | plain | the user, admins |
| Accept contributor policy, Terms of use, Privacy policy | yes, all three | version strings + time | the user, admins |

Rules:

- **One request at a time.** A user can submit again only when their last request
  was **REJECTED**. PENDING blocks a new request; APPROVED means they are a
  contributor already. Enforced by Firestore rules: the request document ID is
  the user's UID, so there can never be two.
- A user reads **only their own request**. Admins read all.
- The user may **cancel** (delete) their own request, and deleting the account
  deletes it.

### Encryption of contact data (Firestore phase)

Browsers write to Firestore directly, so there is no server to hold a key. Contact
data is therefore **encrypted in the browser** with the publisher's **public key**
(RSA-OAEP-3072 wrapping a per-request AES-256-GCM key). Only the admin, holding the
**private key**, can decrypt it — in the admin console, where the key is loaded
from a file and kept in memory only. Firestore, Google and anyone who can read
the document see ciphertext. The key pair is created with
`node telegram-bot/scripts/gen-contact-keypair.js`; the public half goes in
`web/public/assets/contributor-config.js`, the private half never goes anywhere
near the repository.

If the public key is not configured, the request page refuses to send
("programme not open yet") rather than store contact data in the clear.

## Playlist upload (`/contributor/submit/`)

- Non-contributors see why the page is disabled and a link to the request page;
  the form is not rendered, and Firestore rules refuse their writes anyway.
- Suspended contributors see the same, with "suspended".
- Fields and declarations as in v1: name, link, image, description, category,
  language, and the three ownership declarations.
- **Link check.** Firestore phase: the browser validates the URL and tries to
  fetch it; many playlist hosts block cross-origin reads, so a failed fetch is
  shown as "could not be checked from the browser — the admin will check it"
  and does not block the upload. Own-server phase: the server's SSRF-safe
  verifier runs and **does** block a failing link, as in v1.
- Duplicate link: refused when another contributor already holds the link
  (`playlist_urls/{sha256(normalised url)}`). The rules compute that hash from the stored (normalised) link themselves, so a client cannot fake or squat a lock.
- The contributor sees their playlists and statuses and can withdraw any that is
  IN_REVIEW or APPROVED (withdrawing an approved one removes it from `/playlists/`).

## Admin console (`/contributor/admin/`)

- Google sign-in. A non-admin sees "not authorised" and no data (rules deny it).
- **Requests** tab: filter by status; each card shows public name, about, sample
  links, consents and dates. **Load private key** decrypts full name, email, phone.
  Approve (creates `contributors/{uid}`) or Reject with a reason. Decisions are recorded as uid + the label "Admin", never the admin's email, because users can read decisions on their own records.
- **Contributors** tab: list; Suspend / Reinstate. Suspending also rejects the contributor's playlists in review and takes down the published ones (both backends). A suspended — or any existing — contributor can never send a new request, so a new approval cannot lift a suspension.
- **Playlists** tab: filter by status; open link, copy link; Approve (publishes to
  `public_playlists`), Reject with reason, Take down an approved one.

## Firestore data model (temporary)

| Collection | Doc ID | Written by | Read by |
| --- | --- | --- | --- |
| `contributor_requests` | uid | owner (create / resubmit after reject / delete), admin (decide) | owner, admin |
| `contributors` | uid | admin | owner, admin |
| `playlist_submissions` | random | contributor (create, withdraw), admin (decide) | owner, admin |
| `playlist_submission_images` | submission id | contributor (with the submission) | owner, admin |
| `playlist_urls` | sha256 of link | contributor (with the submission), owner/admin (release) | any signed-in user (get only) |
| `public_playlists`, `public_playlist_images` | submission id | admin (approve), owner (withdraw) | everyone |

Timestamps are Firestore server timestamps; rules pin them to `request.time`.

## Own-server phase (VPS / physical server) — prepared now

- Web: every page talks to a **backend interface** (`assets/contributor-backend.js`)
  with two implementations, `firestore` (active) and `http` (own server).
  Switch with `BACKEND` in `contributor-config.js`; nothing else changes.
- Server: `telegram-bot/` serves the same operations over HTTP, with the same
  document shapes, backed by **SQLite** (`STORE_DRIVER=sqlite`, built into Node,
  a single file to back up). Admins are the `ADMIN_EMAILS` list or the custom claim.
  Telegram notifications and buttons work for requests and playlists. Deciding a **request** in Telegram is admin-only; any review-group member can decide **playlists**.
- Migration: `node telegram-bot/scripts/migrate-firestore-to-sqlite.js` copies
  every Firestore-phase collection into the SQLite file; then flip `BACKEND`.

## Acceptance criteria

- **AC-Q1** Signed out, request/submit/admin pages show only "Sign in with Google".
- **AC-Q2** A user can create one request. While it is PENDING, a second request is
  refused by the UI and by the rules.
- **AC-Q3** After REJECTED, the user can submit again; after APPROVED they cannot.
- **AC-Q4** A user cannot read another user's request, contributor doc or submissions (rules).
- **AC-Q5** Full name, email and phone are never stored in plaintext; the admin
  console shows them only after the private key is loaded.
- **AC-Q6** All three consents are required to send; their versions are stored.
- **AC-Q7** Only an admin can approve/reject requests, create contributors,
  decide playlists or write public playlists (rules).
- **AC-Q8** The submit page is disabled for non-contributors and suspended
  contributors, and their writes are refused by the rules.
- **AC-Q9** An approved playlist appears on `/playlists/`; withdrawing or taking it
  down removes it.
- **AC-Q10** A user cannot approve their own request or playlist by writing to Firestore directly.
- **AC-Q11** `BACKEND = "http"` drives the same pages against `telegram-bot/` with
  `STORE_DRIVER=sqlite`, covered by the bot's automated tests.
- **AC-Q12** Firestore rules have automated tests (emulator).
