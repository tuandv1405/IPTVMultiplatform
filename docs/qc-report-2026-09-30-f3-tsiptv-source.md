# QC report: F3, TS IPTV Source format (2026-09-30)

**Scope.** F3 as specified in [`prd-tsiptv-source-format.md`](prd-tsiptv-source-format.md) (AC-T1 to AC-T28, plus T22b and T22c) and the normative spec [`tsiptv-source-format.md`](tsiptv-source-format.md). Two hand-offs:
- step 1, parser and validation: [`handoff-f3-step1.md`](handoff-f3-step1.md);
- step 2 onwards, runtime, storage, UI: [`handoff-f3.md`](handoff-f3.md), including the "QC round 1 fixes", "QC round 2 fixes" and "QC round 3 fixes" tables. The lead's M-R4-1 fix (`GetCurrentProgramChannelList`) was checked in round 5.

**Branch.** `feature/sources-and-formats`. Nothing has been committed or deployed.

## Verdict

**F3 passes QC for Android (phone and TV). No Blocker, Major or Minor is open** (after step 1 rounds 1–4 and step 2+ rounds 1–5).

Every finding is fixed and verified on the phone and TV emulators, including M-R4-1 (verified in round 5). The rejected deviations 3, 4 and 6 are fixed too.

Release still depends on:
- the iOS and desktop checks listed under "Not testable";
- committing the untracked files (O2).

## Process

| Part | Round | Date | Result | Main findings |
|---|---|---|---|---|
| Step 1 (parser) | 1 | 2026-09-28 | FAIL | 5 Minor and 5 Nit, plus spec ambiguities. Examples: the Stremio manifest check accepted an empty path; a catch-up `append` with a nested URL was rejected; localized keys were echoed into messages; lengths were counted in UTF-16 units; the planner could not enforce the budget. |
| Step 1 | 2 | 2026-09-28 | FAIL | Round-1 items fixed and PO decisions (a)–(k) implemented. New Minors: a rejected vs fetch-failed include was mis-classified; the parser rejected C1 characters but the schema allowed them; the trim set differed from the schema's ECMA `\s`. |
| Step 1 | 3 | 2026-09-28 | FAIL | ECMA whitespace inside URLs was accepted, and C0 characters in short strings were kept silently. |
| Step 1 | 4 | 2026-09-28 | **PASS** | Differential test: 7,398 mutations against the ECMA-semantics schema, 0 disagreements left. |
| Step 2+ | 1 | 2026-09-29 | FAIL | 7 Major, 13 Minor, Nits. Majors: Android subtitles never offered; `x-tvg-url` guides lost on refresh; stale guide wiped; budget counted compressed bytes; no locking between refresh and remove; parsing on Main; failed root refresh silent. 3 deviations rejected. |
| Step 2+ | 2 | 2026-09-29 | FAIL | All round-1 items fixed. 4 Minor: the Home/Channels switch started scrolled away; TV ▶ opened an empty options dialog on F1 channels; duplicate programmes across guides; a partial refresh left Home stale. |
| Step 2+ | 3 | 2026-09-30 | FAIL (1 Minor) | All 4 Minors fixed except one path of the duplicate-programmes fix (M-R3-1). All the round-2 nits are fixed. |
| Step 2+ | 4 | 2026-09-30 | FAIL (1 Minor) | M-R3-1: the Programs tab list and counts are fixed ("8 Programs" for the doubled channel). O1: source channels show their names, including the name-matched QA Multi. The channel detail list still shows every show twice (M-R4-1). |
| Step 2+ | 5 | 2026-09-30 | **PASS** | M-R4-1 verified: the channel detail list shows each show once (8 of 8), while the DB still holds 16 rows from the two guides. F1 Programs regression OK. |

## How it was tested

- **Automated checks (G1), re-run fresh each round.**
  - Rounds 3 to 5: `desktopTest --rerun` gives **66 suites, 483 tests, 0 failures**.
  - `assembleDebug` (with `-Ptsiptv.debugAddonBlocklistUrl=…`) is green.
  - `check_guides.py` is OK.
  - The denylist grep (`strem.io`, `strem.fun`, `stremio.net`, `beamup`) gives 0 hits.
- **Step 1.**
  - A Java harness calls the compiled parser directly.
  - A Python differential compares its verdicts with `tsiptv-source-v1.json`: 4,895 mutations in round 1, 7,398 in round 4. The schema's `pattern` is applied with ECMA-262 semantics.
  - Targeted cases covered gzip, the 5 MiB boundary and bombs, and self-include.
  - A planner driver used an in-memory server.
- **Step 2+ on devices.**
  - Emulators `Pixel_10a` (phone) and `TSLauncherReferenceTV` (TV), one at a time, driven with adb, uiautomator and D-pad key events.
  - `qa/tsiptv-fixture` and `qa/stremio-fixture` were used.
  - QC-only helpers were kept in the scratch area, not in the repository:
    - a round-3 copy of the fixture with a movie autoplay banner, a second guide covering one channel, an M3U channel on the logged `/hls` path, and a root failure switch;
    - a server that delays a nested include by 25 s (remove-during-refresh);
    - a generated maximum document (10,000 channels, 5,000 movies, 1,000 series, 20,000 episodes; 3.8 MB).
- **Code review.** A read-only reviewer covered every round's diff. Its findings were confirmed on device where possible.
- **Upgrade (AC-T27, G4).**
  - Before each install, the app database was snapshotted and diffed row by row.
  - In round 2 the Room v6 schema had changed after the round-1 install. Both devices were restored to their pre-upgrade v5 snapshot (checkpointed single file, pushed with `run-as`) and upgraded again.

## AC-T matrix (final state)

| AC | Final | Evidence (round) |
|---|---|---|
| T1 | PASS | Hosted examples by link and `minimal-live` from file. Minimal: 2 TV + 1 radio. VOD: 2 movies, 1 series. Composed: 6 channels, 2 movies, 1 series (r1). Parser: 0 issues on the three examples (step 1). |
| T2 | PASS | All six `/bad/*` documents show the right message and nothing is stored (r1). Details show `path — CODE: explanation` (r2). |
| T3 | PASS | "3 items will be skipped" and Details lists every path (r1). |
| T4–T6 | PASS | Step-1 parser tests and the differential test. |
| T7 | PASS | Switching the UI to Vietnamese changes Source Home titles live (r1). |
| T8 | PASS | `composed-includes`: M3U row by number with guide data, addon catalogue, "Picked by hand" = Big Buck Bunny then His Girl Friday (r1). |
| T9 | PASS | M3U include 500 on refresh: channels stay, "Couldn't refresh — showing the copy from …" (r1, r2). |
| T10 | PASS | Cycle gives `E_INCLUDE_CYCLE` on device. Depth 4 and the 21st include are covered by step-1 tests. |
| T11 | PASS | `If-None-Match` and 304 in the log. An all-304 refresh leaves every content table unchanged in the DB diff (r2). |
| T12 | PASS | Include headers appear only on `/live.m3u` (r2). The M3U channel's stream request `/hls/master.m3u8` has the player UA, not the include UA (r3). |
| T13 | PASS | Addon under "From your sources", no Remove, not on Discover, removed with its source (r1). Ref-counting for shared addons: unit test and review (r2). |
| T14 | PASS | `vod-catalog`: hero with 2 banners, rows, TV focus ring in the accent (r1). Phone chips and the switch use the accent (r3). |
| T15 | PASS | Default layout (r1). |
| T16 | PASS | Skips fixture: app colours and `W_CONTRAST` (r1). |
| T17 | PASS | TV: focus on hero, ◀/▶ skip the decorative banner, detail → Play focused, Back restores the card (r1–r3). |
| T18 | PASS | Chips filter on phone; TV chip row is focusable (r1). |
| T19 | PASS | Channel headers in the log; TV ▲/▼ zap within the section (r1–r3). |
| T20 | PASS | In this source's Continue watching only, not in Discover (r1). |
| T21 | PASS (Android) / **NOT TESTABLE (desktop)** | Options dialog: Off / English / Vietnamese (label fallback r3). VTT and SRT load on selection and start Off, on phone and TV (r2, r3). |
| T22 / T22b / T22c | PASS | Host list, 18+ gate, include-18+ Cancel stores nothing, refresh hold-back and Confirm (r1). Fetch failure → `E_INCLUDE` and a stale copy (r1, r2). |
| T23 | PASS | Replace prompt; history kept (r1). |
| T24 | PASS | DB back to baseline after Remove (r1–r3). After an app restart, Remove returns to the previous playlist (r3). |
| T25 | PASS (import) / **NOT MEASURABLE (home ≤ 1 s)** | Maximum document: preview plus import 8.8 s on the phone (r2), 11.9 s on TV (r1); target < 15 s. Time to first sections could not be resolved: one uiautomator dump alone takes about 2 s. |
| T26 | PASS (debug logcat, review) / **NOT TESTABLE (release, analytics)** | No URLs in the app's logcat (r1). |
| T27 | PASS | Phone: 2 playlists / 23 channels / 11 history / 2 media history / 5 addons. TV: 3 playlists / 12,360 channels / 51,252 attributes / 30 history. All kept v5 → v6 (r1, and again r2), and kept across the r3 install. |
| T28 | PASS | `StringResourcesLocaleTest`; Vietnamese strings seen on device. |

## Deviations (final judgement)

| # (handoff) | Deviation | Verdict |
|---|---|---|
| r1-3 | Channels keep only their first stream | **Rejected (r1).** Fixed in r2: `channel.streamsJson`, first supported stream, options dialog (verified with "QA Multi" on phone and TV). |
| r1-4 | Guide matching without the name fallback | **Rejected (r1).** Fixed in r2 (a channel with no `epgId` gets its guide by name); spaces ignored (r3). |
| r1-6 | Hero `autoplay` on a movie opened the picker | **Rejected (r1).** Fixed: the movie plays at once, and Back returns to the detail page (verified r3). |
| 1 | Includes fetched one after another | Accepted. |
| 2 | Preview is a dialog on phone | Accepted. |
| 3 | Include-18+ Cancel aborts the import | Accepted (AC-T22b). |
| 4 | Refresh routing (Home vs About) | Accepted. |
| 5 | Shared addons are ref-counted; the user's own install stays theirs | Accepted. |
| 6 | Catch-up stored, not played | Accepted (PRD). |
| 7 | About in the overflow menu on phone, a rail item on TV | Accepted. |
| 8 | Subtitles start Off and never get the stream headers | Accepted. An embedded *forced* text track may show again on a new item until Off is chosen; the spec has no rule for forced subtitles. |
| 9 | Movie autoplay passes through the detail route | Accepted. |
| 10 | TV options on ▶ (channels) / ▲ (VOD) / MENU, only when there is something to choose | Accepted. F1 channels keep the info banner and the old hint (r3). |

## Not testable on this machine

- **iOS.** Never compiled (Windows): the `NSLocale` language names, no subtitles, and DRM fallback.
- **Desktop app.** Not run: VLC subtitles and the header subset.
- **Release-build logcat and Firebase DebugView** (AC-T26).
- **AC-T25 "first sections within 1 s".** The tooling latency is higher than the target.

## Round 4 (2026-09-30)

- **G1.**
  - `desktopTest --rerun`: 66 suites, 483 tests, 0 failures.
  - `assembleDebug` green; `check_guides.py` OK; denylist grep 0.
  - `6.json` `identityHash` 47f17b7b… equals the one on both devices.
- **Programs tab.**
  - Setup: the scratch fixture has two EPG links, both covering `qa.news`.
  - The channel list shows "8 Programs" for QA News (it showed 16 in r3).
  - Source channels show their names: QA News, QA Headers, M3U One/Two/Three, and the name-matched **QA Multi**. Logos could not be checked, because the fixture channels have none.
  - F1: my own F1 M3U with `x-tvg-url` still lists M3U One/Two/Three with 8 programmes each. The user's K29 shows the same "No programme guide" screen before and after the install (UI dumps identical).
- **Quick regression.**
  - Source Home opens with the switch visible.
  - The channel player shows the EPG line ("qa.multi.byname show 3").
  - F2 Discover and Continue watching load.
  - TV: after the install, the home UI dump is identical to the one before, and the DB is unchanged.
- **User data.**
  - Phone restored to the pre-round snapshot (identical), K29 active.
  - TV unchanged ("Channel By lang" active).
  - Emulators and servers stopped.

## Round 5 (2026-09-30, final)

- **G1.**
  - `desktopTest --rerun`: 66 suites, 483 tests, 0 failures.
  - `assembleDebug` green; `check_guides.py` OK; denylist grep 0.
  - `6.json` `identityHash` unchanged (47f17b7b…).
  - `programDao.` is now referenced only inside `RoomIPTVDatabase`.
- **M-R4-1 verified on the phone.**
  - Setup: duplicate-guide scratch fixture.
  - Programs › QA News reads "8 Programs" and lists "qa.news show 1" to "show 8", each once (`r5p02`). The DB still holds 16 rows for `qa.news` (two guides).
- **F1 regression.** My F1 M3U with `x-tvg-url` shows M3U One/Two/Three with 8 programmes each, and each show appears once (`r5p01`).
- **User data.** The phone was restored to the pre-round snapshot (every table SAME), and K29 is active. The TV was not used in round 5; its data is unchanged since round 4.

## Open items

**No Blocker, Major or Minor is open.** What remains:
- **M-R4-1, M-R3-1 and O1: closed** (r5, r4, r4).
- **Not testable here** (see above):
  - iOS build and behaviour;
  - the desktop app (VLC subtitles, header subset);
  - release logcat and Firebase DebugView (AC-T26);
  - AC-T25 "first sections within 1 s".
- **Accepted behaviours to keep in mind:**
  - an embedded *forced* text track may reappear on a new item until the viewer picks Off (deviation 8; the spec has no rule for forced subtitles);
  - movie hero autoplay goes through the detail route, so Back returns to the detail page (deviation 9).
- **O2 (release housekeeping).** The F3 files, `composeApp/schemas/…/6.json`, `qa/tsiptv-fixture/` and the hand-offs are untracked. They must be committed; the CI migration test needs `6.json`.
- **O3 (QC environment).** Both AVDs showed an emulator crash-consent dialog after being killed. The pending dumps were moved to the QC scratch area; boot with `-no-snapshot`.

## Test data hygiene

- **Phone:** after round 3, every user row matches the pre-install snapshot, and K29 is the active playlist again. F2's own background manifest refresh updated `version`, `manifestJson` and `lastFetchedAt` of some user addons during the rounds. This is normal app behaviour.
- **TV:** the QC playlists were cleaned up by restoring the v5 snapshot and letting the app migrate again. The final diff is SAME, and "Channel By lang" is active again.
- Nothing was uninstalled and no data was cleared. `CL_UIS7862S_REF` was never touched. Both emulators and all QC servers are stopped.

## Lead follow-ups (2026-09-30, from the web C1 closure check)

- **F3-FU1 (iOS/desktop, untestable here):** spec §8.5 says readers without DRM support try the next stream without DRM. Today only channels do this; the movie/episode stream picker and hero autoplay do not skip DRM streams (they show the DRM-protected message). Fix when iOS/desktop can be tested.
- **F3-FU2 (desktop):** `DesktopMediaPlayer.kt:141-148` adds subtitles with `addSlave` (URL only), so tracks without a `label` don't show the language name, and VLC media options for stream headers (`:185-186`) may be inherited by the subtitle input. Verify and fix when the desktop app is run. The KDoc at `:137-139` says subtitles auto-select, but the code starts them Off; correct the comment.
- The web guide now describes the current behaviour for both (Android verified, desktop/iOS marked not verified).

