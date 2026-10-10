# QC report: TV cast, send to TV, 4-device limit, device sync

Date: 2026-10-10 · Branch: `feature/tv-cast-and-sync` · Final commit tested: `223b38a`
Scope: PRD `docs/prd-tv-cast-and-sync.md`; hand-off `docs/handoff-tv-cast-and-sync.md`. Five QC rounds
(round 1 at `01d0971`, round 5 at `223b38a`).

## Verdict

**Merge: GO for the LAN features (A: cast; B: receiving on the TV) and for the playlist-data fixes.
The account-backed flows are untested on a device.**

- Every bug found in rounds 1–4 (31 items) is fixed and verified, on a device where that was
  possible, otherwise by code review or unit/rules tests.
- No open Critical, High or Medium issue.
- Features B (send quota, rewarded task), C (4-device limit) and D (sync) are covered only by unit
  tests and the Firestore emulator. **They were never run on a device** (PENDING USER: a signed-in
  account is needed, and production writes were out of scope for QC).
- Before release:
  - deploy `firestore.rules` (until then the old owner-wildcard rule applies, see the hand-off);
  - set `TSIPTV_ADMOB_REWARDED_UNIT`;
  - run the account flows once with a test account.

## Test environment and rules

- Phone `TSIPTV_ADS_QA` (API 37). In round 5 it booted headless.
- TV: `TSIPTV_QA_TV`, my own Android TV AVD on API 36 (round 1; deleted afterwards), then
  `TSIPTV_CAST_TV` (rounds 2–5).
- Both ran the debug build with `-Ptsiptv.debugSkipLogin=true -Ptsiptv.debugAdsNoFirstDay=true`.
- Phone ↔ TV over `adb forward` + `10.0.2.2:<port>`, because mDNS does not cross emulator NATs.
- Host tools (session scratchpad, not in the repo):
  - `fuzz.mjs`: protocol fuzzing, flooding, slowloris, pairing abuse.
  - `lan.mjs`: a fake paired phone that does real ECDH, HMAC signing and offers.
  - `n3.mjs`: large-offer gate, rate and deadline tests.
  - Extra Firestore rules probes (`qc-extra.test.js`, 18 cases).
  - Room DB snapshot and diff scripts, using the debug build's `run-as`.
- **No sign-in and no production Firestore writes** in any round. The user approved a test account
  in round 1, but the Claude Code permission system blocked sign-in; that block was respected.
- Devices not touched: `CL_UIS7862S_REF`/emulator-5554, `8BMY112A8`, `Pixel_10a`, `TSLauncherReferenceTV`.

## Build gate (round 5)

| Check | Result |
|---|---|
| `:composeApp:desktopTest` (forced rerun) | **557/557 pass** |
| `:composeApp:compileCommonMainKotlinMetadata` | pass |
| `:composeApp:assembleDebug` | pass |
| `:composeApp:assembleRelease` | R8 pass; packaging fails only on the known keystore alias `ts_iptv` (environment) |
| `firestore-tests` (rules + devices/sync + web-backend) | **39/39 pass** |
| QC extra rules probes (scratch, round 2 rules) | 18/18 pass |

## Acceptance criteria

| AC | Result | Evidence / reason |
|---|---|---|
| A1 TV icon in the phone player | **PASS** | Device: "Play on TV" in the top bar |
| A2 TV found by mDNS within 5 s | **NOT TESTED** | mDNS does not cross emulators; needs real devices on one router |
| A3 6-digit pairing; wrong code; 3rd wrong code ends the session | **PASS** | Device (rounds 1 and 2): after the 3rd wrong code the phone now says "The code expired. Start again." |
| A4 TV plays with headers/DRM; VOD at the phone's position (±5 s) | **PASS** (headers/DRM by code review) | Phone paused at 01:39 → TV session at 99.3 s; casts at 120 s and 90 s confirmed |
| A5 `UNPAIRED`, `BAD_SIGNATURE`, `REPLAY`, `EXPIRED` refused | **PASS** | Unit tests + device (fake phone: replay → `REPLAY`; unknown sender → `UNPAIRED`; bad MAC → `BAD_SIGNATURE`) |
| A6 over cap → `TOO_LARGE`; non-http(s) → `BAD_URL` | **PASS** | Device fuzz (64 KiB pre-auth, 1 MiB file, 1.5 MiB cap); `BAD_URL` by unit test |
| A7 receiver stops when the TV app leaves the foreground | **PASS** | Port closed after HOME; also after 5 fast HOME/resume cycles |
| A8 connect by IP works without mDNS | **PASS** | All device runs; port is stable across restarts |
| A9 no URL/header/key/code in logcat | **PASS** | Both logcats grepped in every round; only system or AdMob lines |
| B1 "Send to TV" in Add playlist / rows; signed out asks to sign in | **PASS** (signed-out path) | One sign-in line plus a Sign in button; nothing is sent |
| B2 TV offer dialog; Receive imports, Decline imports nothing | **PASS** | Device via the paired fake phone: URL, 1 MB file, error and TS IPTV Source offers; background import with a result dialog |
| B3 4th send refused; a rewarded ad gives exactly 1 more | **NOT TESTED (PENDING USER)** | `QuotaPolicyTest`, `RewardTasksTest` + rules tests pass |
| B4 quota survives reinstall; resets at local midnight | **NOT TESTED (PENDING USER)** | Rules + unit tests pass |
| B5 file > 1 MiB refused; imported file playlist explains | **PARTIAL** | TV refuses > 1 MiB content (device); phone side needs sign-in |
| C1 5th device shows the limit dialog with 4 devices | **NOT TESTED (PENDING USER)** | `DeviceLimitTest` + rules |
| C2 remote sign-out frees the slot | **NOT TESTED (PENDING USER)** | Rules test (including "ghost" ids) |
| C3 removed device signs out at next start | **NOT TESTED (PENDING USER)** | Unit tests |
| C4 rules: 5th id / unlisted device refused; other user cannot read | **PASS** | Emulator rules tests + QC probes (swap, collection-group, other user) |
| D1 push stores definitions only, skips files | **NOT TESTED (PENDING USER)** | `SyncPlannerTest` |
| D2 other device shows "Có bản đồng bộ mới từ …" | **NOT TESTED (PENDING USER)** | — |
| D3 merge: union, newer wins | **PASS (unit)** | `SyncPlannerTest` |
| D4 replace lists removals first | **PASS (unit)**, UI NOT TESTED | `SyncPlannerTest` |
| D5 2nd push needs 2 rewarded ads; rules need `syncs + 1` in the same batch | **PASS (rules)**, device NOT TESTED | Emulator rules + QC probe X12 |
| D6 `sync/current` unreadable by others | **PASS** | Emulator rules |
| X1 strings in 7 locales | **PASS** | `StringResourcesLocaleTest`; same key count in every locale |
| X2 builds and tests | **PASS** | Above |
| X3 no Room schema change | **PASS** | Still v6; upgrade checked on a device (round 4) |

## Bugs found and their status (all closed)

| Round | # | Severity | Issue | Status |
|---|---|---|---|---|
| 1 | 1 | Medium | VOD cast ignored the phone's position (seek before the async source swap) | Fixed, verified on device |
| 1 | 2 | Medium | Slowloris: 4 trickling sockets blocked the receiver indefinitely | Fixed (10 s total deadline, per-IP and total caps, 64 KiB pre-auth); verified |
| 1 | 3 | Low/Med | Receiver accepted any peer address | Fixed (LAN/loopback peers only); code review |
| 1 | 4 | Low | Phone kept asking for a code after the TV ended the session | Fixed, verified |
| 1 | 5 | Low | Sender names not sanitised (bidi/newline); code prompt over playback | Fixed (`cleanName`, cooldown, later pairing mode); verified |
| 1 | 6 | Low | Start/stop race of the receiver | Fixed (ordered worker); verified |
| 1 | 7 | Low | `NOT_ACCEPTING` mapped to "not supported"; `acceptingOffers` never set | Fixed; verified |
| 1 | 8 | Low | Failed `recordSend` silently ignored | Fixed (retry + message); code review |
| 1 | 9 | Low | Device count from docs vs rules from `meta.ids` | Fixed (ids are the source of truth, ghost removal); rules test |
| 1 | 10 | Low | UI: duplicate sign-in line, spinner + "none found", keyboard covering Connect, unstable port, credentials in URL-derived names | Fixed; verified |
| 1 | 11 | Low | Rules: unbounded `quota.networks`; ±14 h window allowed 2 extra days | Fixed; probes X10/X17/X18 |
| 2 | N1 | Low/Med | Accepted URL offer gave no feedback (one-shot event lost) | Fixed (state-driven, background import); verified |
| 2 | N2 | Low | LAN client could keep the code screen up | Fixed (pairing only on the TV & devices screen, escalating cooldown); verified |
| 2 | N3 | Low | Large-request gate by loose regex | Hardened (exact v1 header, ±120 s, 3 per sender per minute, size-scaled deadline ≤ 30 s); verified |
| 2 | N4 | Info | Per-IP cap is per address | Accepted (bounded by the deadline) |
| 3 | N5 | Medium | 2nd accepted offer cancelled the running import, leaving a 0-channel playlist | Fixed (one LAN import at a time); verified |
| 3 | N6 | Low | User left on an empty import screen; offers refused | Fixed; verified (error and Source offers) |
| 3 | N7 | Low | Pairing-open race | Fixed (ordered worker); code review |
| 3 | N8 | Low | Unpair without confirmation, focus lost | Fixed; verified |
| 3 | — | High (data) | Playlists with the same entries took each other's channels (shared channel-id primary key) | Fixed by `ChannelIdNamespace`; verified, including upgrade from an old build |
| 4 | N9 | Medium | EPG programmes moved between playlists sharing a guide | Fixed (`p:<playlistId>|` ids, per-playlist queries); verified incl. legacy rows |
| 4 | N10 | Low | Related channels mixed playlists | Fixed; verified |
| 4 | N11 | Low | Namespaced `@tag` ids shown to users | Fixed; verified |

## Channel / EPG data fixes (rounds 4–5)

- **Upgrade.**
  - Built the pre-fix commit (df7c779) in a scratch worktree and created data with it on the TV, which reproduced the old channel loss.
  - Installed the new build over it: all 27,937 channel rows were byte-identical before and after.
  - The first owner's ids did not change on refresh.
  - Damaged URL playlists recover on their next refresh. A damaged file playlist cannot recover, so it has to be re-imported (covered in `play-store/release-notes/next-draft.md`).
- **Overlap.** Two or three playlists with the same entries each keep their full channel count. Later owners get `<id>@<8-hex>`, with the guide id kept in `epgId`.
- **History and Continue watching.** They survive refresh on namespaced ids.
- **Favourites.** Code review only: no UI emits `OnFavouriteIPTVChannelPressed` (existing behaviour).
- **EPG (round 5, TV and phone).**
  - Legacy unscoped rows from the round-4 build are read correctly after upgrade: no crash, no duplicate slots, and the EPG line shows.
  - Parsing UP A, then UP B, leaves both with their own 4 scoped programmes, in either order.
  - The Programs tab is correct for both playlists. The player EPG line is correct.
  - A TS IPTV Source guide (`composed-includes`) coexists, with `tsg:` ids and an "On air" row.
- **Zapping, search, history, categories.** Zapping in a namespaced playlist works. Search and History work. Categories stay per playlist.

## Security findings

What the device tests confirmed:
- **Protocol robustness.** Invalid UTF-8, deep nesting (30k/700k levels), garbage, wrong roots, EOF without newline and huge lines are all answered cleanly; the receiver survives.
- **Limits.**
  - Pre-auth requests over 64 KiB → `TOO_LARGE`.
  - Each connection has a 10 s total deadline; large offers get 10 s + 1 s per 100 KB, at most 30 s.
  - At most 8 connections in total and 2 per LAN address.
  - Slowloris no longer blocks legitimate clients.
- **Pairing.**
  - ECDH P-256 with a curve check and a 6-digit code.
  - The code is valid 2 minutes; 3 wrong codes end the session; 5 failed sessions in 10 minutes lock pairing for 10 minutes.
  - Pairing is accepted only while the TV & devices screen is shown (`PAIRING_CLOSED` otherwise), with an escalating cooldown.
  - Pair keys are stored with Keystore AES-GCM.
- **Signed commands.** HMAC-SHA256 over a ±120 s window, with a 512-entry nonce cache (replay refused). Names are sanitised. URLs and headers are validated (http(s) only).
- **Logs.** Nothing secret in logcat.
- **Firestore rules.**
  - Owner-only access to devices, meta, quota and sync; collection-group reads denied.
  - At most 4 device ids, changed one at a time together with the device doc.
  - Quota counters move up by at most 1 per write, within the caps, cannot be deleted, and use a one-extra-day window.
  - A sync write needs `syncs + 1` in the same batch and a registered device.
- **Rewarded ads.** A reward is granted only on `onUserEarnedReward`. They are gated by TV layout, the 24 h ad-free start and consent.

Accepted residuals (v1, documented in the PRD/hand-off):
1. An active MITM present during the 2-minute pairing can brute-force the 6-digit code offline. The fix is a PAKE (SPAKE2) or a fingerprint check.
2. LAN commands are signed, not encrypted: stream URLs and headers are visible to someone sniffing the same Wi-Fi.
3. Someone who has sniffed a paired phone's `senderId` can make the TV read up to about 1.5 MiB before `BAD_SIGNATURE`. That uses up the phone's 3 large-offer grants per minute, so its file sends can be blocked for a minute at a time. A header MAC is planned for protocol v2.
4. Quotas and rewards are client-claimed within the rule caps, with no AdMob server-side verification yet. Remote sign-out is advisory: the Firebase token stays valid.
5. The sync payload is owner-only but not end-to-end encrypted.
6. A UTC+13:45/+14 device cannot write quota (the send is reported as not counted).

## Open items (none blocking the merge)

- **PENDING USER:** run B3–B5, C1–C3 and D1–D5 on devices with a test account once sign-in is allowed. Also run the phone "TV is busy" and "not counted" messages, and the 18+ Source path in sync.
- Deploy `firestore.rules` and set `TSIPTV_ADMOB_REWARDED_UNIT` before release.
- Real-device test of mDNS discovery (A2), and of the guest-network "not found" case.
- Cosmetic: a queued offer dialog can stack on top of the previous import's success dialog on the TV.
- Existing behaviour, not this branch: favourites have no UI entry point; Unpair confirmation focuses Cancel (intended).
- Room v7 with composite keys `(playlistId, id)` for channels and programmes would remove the namespacing workaround (hand-off follow-up).
