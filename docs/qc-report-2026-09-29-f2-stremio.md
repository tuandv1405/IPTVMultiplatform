# QC report: F2, Stremio-compatible addons (2026-09-29)

**Scope.** F2 as specified in [`prd-stremio-addons.md`](prd-stremio-addons.md) (AC-S1 to AC-S25), built on the step 1 protocol core ([`handoff-f2-step1.md`](handoff-f2-step1.md)) and handed over in [`handoff-f2.md`](handoff-f2.md). That hand-off includes the fix tables for QC rounds 1 to 4. This report also covers F1 regressions caused by the F2 player and navigation changes.

**Branch.** `feature/sources-and-formats`. Nothing has been committed or deployed.

**Process.** Five QC rounds, each followed by dev fixes:

| Round | Date | Result | Main findings |
|---|---|---|---|
| 1 | 2026-09-28 | FAIL | 2 Blockers, 9 Majors, 21 numbered bugs in total, plus 2 rejected deviations. The Blockers: a resume crash ("Player is accessed on the wrong thread") and a LazyList duplicate-key crash. The Majors included: the F1 live channel showing a stale VOD seek bar; the browser never opening (phone package visibility, TV `BrowserStub`); an orphan Discover screen in the back stack; and TV focus. |
| 2 | 2026-09-28 | FAIL | All 21 round-1 bugs and both deviations fixed. New Major N1: a failed or slow stream saved duration 0 and dropped the title from Continue watching. N2 to N9: Minor or Nit. |
| 3 | 2026-09-28 | FAIL | N1 to N9 fixed. New Major N-S1: the TV search screen trapped D-pad focus in the field. R1 to R7: Minor or Nit. |
| 4 | 2026-09-28 | PASS (TV) | N-S1 and R1 to R7 fixed. L1 to L3: Low. The phone was not testable because its login had expired. |
| 5 | 2026-09-29 | **PASS** | L1 to L3, the R6 Nit and the dead-code clean-up were verified. Phone S17 and N8 were re-run (the phone was logged in again). |

## Verdict

**F2 passes QC for Android (phone and TV). No Blocker, Major or Minor is open.**

Release still depends on:
- the iOS build (never compiled, see "Not testable");
- committing the Room schema files (O1);
- publishing the production blocklist and the hosted sampler (O2).

## How it was tested

- **Automated checks (G1)**, re-run fresh in every round (final run, round 5):
  - `.\gradlew.bat :composeApp:desktopTest --rerun` passes: **444 tests in 61 classes, 0 failures, 0 errors, 0 skipped** (round 1 had 411).
  - `.\gradlew.bat :composeApp:assembleDebug -Ptsiptv.debugAddonBlocklistUrl=http://10.0.2.2:7000/policy/addon-blocklist.json` builds.
  - `python web/scripts/check_guides.py` passes.
  - The denylist grep (`strem.io`, `strem.fun`, `stremio.net`, `beamup`) over `commonMain`, `androidMain`, `iosMain` and `desktopMain` returns **0 hits**.
- **Devices (G3).** Emulators `Pixel_10a` (API 37, phone, Play image) and `TSLauncherReferenceTV` (android-36 android-tv, no DocumentsUI), driven with adb, uiautomator dumps, screenshots and D-pad key events only on TV. The two emulators were run one at a time.
- **Fixtures:**
  - `qa/stremio-fixture/server.js`, the local harness with request and UA logging and `BLOCK_IDS`/`BLOCK_HOSTS`.
  - Three QC-only helpers, kept in the QC scratch area and not in the repository:
    - an addon with duplicate ids;
    - a "dead-stream" addon with Good, Dead (404), Slow (never answers) and Delayed (3 × 9 s redirects, then the good MP4) streams;
    - an HTTP server with a sliding-window live HLS channel, VOD channels, a preflight-refused DRM channel and a 404 logo carrying a token.
- **Code review.** Every round's diff was read by a read-only reviewer. Key findings were confirmed on device where possible.
- **Upgrade (G4).** The F2 build was installed with `adb install -r` over the Pixel's real F1 (Room v4) data: 2 playlists, 19 channels, 7 favourites and 9 history rows. The result was user_version 5, every row kept, and the new tables empty. `AppDatabaseMigrationTest` (4→5 with real data) passes.

## AC-S matrix (final state)

| AC | Final | Evidence (round) |
|---|---|---|
| S1 | PASS | No Discover tab or rail item with no addons, on phone and TV (r1). Denylist grep: 0 hits (every round). |
| S2 | PASS (local) / **NOT TESTABLE (hosted)** | Local harness: preview → Add → "Addon added" → the Discover tab appears (r1). The hosted sampler `https://tsiptv-8bdd6.web.app/examples/stremio-sampler/manifest.json` returns 404 (F4 is not deployed), so the `stremio://` hosted variant was not run. |
| S3 | PASS | Not-manifest, legacy and local-server messages (r1). |
| S4 | PASS | "Missing or invalid field: resources" (r1). Configure opens Chrome on `/configure-required/configure` on the phone (r2). TV shows the URL as text (r2). The addon is not added. |
| S5 | PASS | p2p warning. Adult: Add is gated on the 18+ box; no adult rows and no `/adult/` search requests (r1). |
| S6 | PASS | "Replace …?" → a single entry in the same position, now 1.0.1 (r1). |
| S7 | PASS | Disable, move up and remove (with its history) all work (r1). After removing the last addon, Back never lands on Discover (r2). |
| S8, S9 | PASS | Unit tests. |
| S10 | PASS | Requests: no skip, then `skip=100`, `skip=200`, `skip=250` (the lenient probe, deviation 6). Up to "Generated Movie 250", no Retry (r1). |
| S11 | PASS | `genre=Comedy`, then `genre=Comedy&genre=Drama` (r1). |
| S12 | PASS | "his" returns His Girl Friday; retyping cancels (`CANCELLED … after 1066ms`); `/slow` gives "1 addon(s) did not respond" after 15 s (r1). |
| S13 | PASS | Season 1 "The Mechanical Monsters", Specials last, the 2099 episode shows Upcoming and cannot be selected (r1). |
| S14 | PASS (Android) | His Girl Friday plays on phone and TV (r1, r5, including from TV search). iOS and desktop were not run. |
| S15 | PASS | `/hls/` playlist, variants and segments all carry `UA=TSIPTV-Example/1.0` (r1). |
| S16 | PASS | Two rows by default. The toggle adds two greyed rows reading "Not playable in TS IPTV" (no kind word, no count) that cannot be selected (r1, r2 on TV). The group header is kept by lead decision. |
| S17 | PASS | Phone: Chrome opens (r2, re-run r5). TV: the URL is shown as text (r2). |
| S18 | PASS | Inline streams: no `/stream/` request (r1). |
| S19 | PASS | No repeat catalogue or stream request within max-age or 5 min (r1 logs). The 5-minute stream cap is covered by unit tests. |
| S20 | PASS | Resume within ±5 s: phone 214,400 vs 214,379 ms saved (r2); TV 120,640 vs 120,619 (r3). A stream that loads after about 27 s resumes exactly at the saved point (70,082, r5). Dead or slow attempts never overwrite a row (r3). Finishing S1E1 queues S1E2 in Continue watching (r2). |
| S21 | PASS (DB and logs) / **NOT TESTABLE (DebugView, release logcat)** | `transportUrlEnc = v1:…`; the plain URL is absent from the DB, WAL and SHM (r1). The app-PID logcat has no addon, stream or token URL (r2, r3: a 404 logo `?token=SECRETLOGO` appears 0 times). |
| S22 | PASS | Debug-only override. An installed addon on the blocklist becomes Blocked and disabled, with the switch removed (r2, r3, r5). Adding a blocked host shows "This addon has been blocked in TS IPTV." (r2). The release build cannot read the override (`resValue` exists only in the debug build type; the reader checks FLAG_DEBUGGABLE). |
| S23 | PASS | TV, D-pad only (r2 to r5): add dialog, ◀/▶ toggle, row menu; Discover rail → first card; Back returns to the opener; TV search (▼ / IME Search to the results, ▲ to the field); the picker panel traps ◀ and Back closes it; the last-watched episode is focused after Back from the player; ◀ from the grid lands on the selected rail item. Minor oddities in "Open items". |
| S24 | PASS | Migration test plus the manual G4 upgrade above. |
| S25 | PASS | `StringResourcesLocaleTest`. No escaped quotes on device. |

**F1 regressions checked in every round.** TV ▲/▼ zapping works, including onto a preflight-refused channel with a fast key sequence (r3). Live after VOD shows LIVE on phone and TV, with no seek bar (r2). TV channel banners have no progress bar for live.

## Deviations (final judgement)

| # | Deviation | Verdict |
|---|---|---|
| 1 | iOS cipher uses AES-CBC + HMAC-SHA256 | Accepted, on condition that it builds on a Mac. The Keychain error handling was fixed in r2 (M11). |
| 2 | No drag-to-reorder on phone | Accepted: AC-S7 only needs Move up/down. |
| 3 | No TV history screen, no TV remove | Rejected in r1. The Menu key on a Continue watching card was implemented and verified (r2). |
| 4 | Separate "clear" for Movies & series | Accepted. |
| 5 | TV search is a separate screen | Accepted. Its D-pad trap (N-S1) was fixed in r4 and hardened in r5. |
| 6 | Extra `skip=250` probe | Accepted: 4 requests appear in the log. |
| 7 | Production-only blocklist URL | Rejected in r1. A debug-only override was added and verified (r2), and it is absent from release. |
| 8 | Seek bar for F1 VOD channels | Accepted once the live-after-VOD regression was fixed (r2). |
| 9 | `fallbackToDestructiveMigration(true)` kept | Accepted: 4→5 is a real, tested AutoMigration. |
| 10 | `previewJson` in routes | Accepted: trimmed in r2 (no videos or links; description ≤ 300 characters). |

## Not testable on this machine

- **iOS.** Never compiled (Windows). This affects AC-S14/S15/S20 on iOS, the Keychain cipher (deviation 1) and the document picker.
- **Desktop app.** Not run (S14, S15 on desktop).
- **Firebase DebugView and a release-build logcat** for S21.
- **The hosted sampler** (S2 `stremio://` variant), because F4 is not deployed.

## Open items (none blocks F2)

- **O1 (release housekeeping).** `composeApp/schemas/tss.t.tsiptv.core.database.AppDatabase/4.json` and `5.json` are still untracked. They must be committed, or the CI migration test fails. `qa/stremio-fixture/` and `docs/handoff-f2.md` are untracked as well.
- **O2 (release housekeeping).** `https://tsiptv-8bdd6.web.app/policy/addon-blocklist.json` and the hosted sampler return 404 until F4 is deployed. A missing blocklist keeps the last cached list, and addons still work.
- **O3 (Nit, code).** `AddonRepository.keyRetryJob` is written from the retry coroutine and read by the collector without `@Volatile`. With the r5 reordering this is benign in practice. Also, after 8 failed key reads, `hasActiveAddons` stays unknown for the rest of the process (documented).
- **O4 (Nit, TV).**
  - After scrolling a search result row, ▼ from the field lands on the nearest visible card, not the first one. There is no trap.
  - Moving focus through the channel rail changes the selected group as it goes. This predates F2 and was seen with a 100+ group playlist.
- **O5 (observation, outside F2).** On the TV AVD, a user playlist ("Channel By lang", 1,384 channels) shows "0 Channels" in many language groups while All Channels lists them. This is worth checking under F1 (group and category ids, R1); it was not investigated here.

## Test data hygiene

**Phone.** The QC Sampler was added only for the r5 checks and then removed; the user's own addons were left untouched. By r5 both emulators held other users' data.

**TV.** The QC addons were added only for the r5 checks and then removed; the user's playlists were left untouched.

Neither emulator was left running, and all QC servers were stopped. `CL_UIS7862S_REF` was never touched.
