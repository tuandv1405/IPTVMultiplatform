# PRD — Rate TS IPTV on the Play Store, from inside the app

Status: **Implemented 2026-09-27** · Owner: PO · Platforms: Android (phone + TV), iOS, Desktop

---

## Problem

The listing has no ratings yet. People who enjoy the app never get a nudge to
say so, and there is no place in the app to leave a review even when they want
to. Ratings drive Play ranking and conversion, especially for a first release.

## Goals

1. Ask satisfied users for a rating at a natural moment, **without** breaking a
   task and without nagging.
2. Give anyone who wants to rate a permanent, findable entry point.
3. Stay inside Google Play's policy for review prompts.

## Non-goals

- Incentivised or gated ratings ("rate 5★ to unlock…") — forbidden by Play policy.
- Asking "do you like the app?" first and sending only happy users to the store
  (review gating) — also forbidden.
- Reading the rating back. The Play API never tells the app whether a review was
  left, or what it said.

---

## Policy constraints (Google Play In-App Review API)

- Use the official API: `com.google.android.play:review`. The card is drawn by
  Play, over the app, and the user never leaves.
- Play enforces its own quota. `launchReviewFlow` may complete **without showing
  anything**; the app must carry on the same way in both cases.
- Do not trigger the in-app review card from a button. A button must lead to the
  store listing, because the card may silently not appear.
- Do not ask during a task (mid-playback, mid-import).

---

## Behaviour

### A. Automatic prompt

Shown when **all** of these hold:

| Rule | Value | Why |
| --- | --- | --- |
| Days since first launch | ≥ 3 | The person has formed an opinion |
| Successful plays | ≥ 5 | A channel reached *Playing* five times — they actually use it |
| Days since last prompt | ≥ 120 | Well above Play's own quota; never feels like nagging |
| Prompts ever | ≤ 3 | Stop asking eventually |
| Days since the user tapped **Rate** manually | ≥ 120 | They already went to the store |

**When:** on returning to Home from the player (phone and TV). Never on the
player screen, never on cold start, never during import.

**Counting a play:** once per media item, the first time its playback state
becomes `PLAYING`. Zapping through ten channels in a minute is ten plays, which
is fine — the day threshold is what prevents an early prompt.

**Recording:** a prompt is recorded as soon as the flow is *requested*, whether
or not Play actually displayed it. Otherwise a quota-suppressed prompt would be
retried on every return to Home.

| Platform | Mechanism |
| --- | --- |
| Android | Play In-App Review API (`ReviewManager`) |
| iOS | `SKStoreReviewController.requestReviewInScene` (Apple's own 3-per-year cap applies on top) |
| Desktop | None — not distributed through a store |

### B. Manual entry point

| Surface | Item | Action |
| --- | --- | --- |
| Phone — Profile → Preferences | **Rate TS IPTV** | Opens the Play listing |
| TV — Settings menu | **Rate TS IPTV** | Opens the Play listing (Play Store TV app) |
| iOS | hidden | Needs the App Store ID, which does not exist yet |
| Desktop | hidden | No store |

Android opens `market://details?id=tss.t.tsiptv` and falls back to
`https://play.google.com/store/apps/details?id=tss.t.tsiptv` when no Play Store
is installed. Tapping the item records a "manual rate" timestamp (rule 5).

### Strings

New keys, translated into all seven locales (en, vi, de, es, fr, ja, zh-CN):
`rate_app_title`, `rate_app_desc`, `open_link_failed` (shown if neither the Play Store nor a browser can open the link), and `contributor_entry_title` for the Profile entry that opens the contributor programme on the web.

---

## Acceptance criteria

- **AC-R1** A fresh install never prompts on day 0, whatever the play count.
- **AC-R2** Day ≥ 3 and 5 plays → the next return from the player to Home
  requests the review flow exactly once.
- **AC-R3** After a prompt, no second prompt for 120 days, even with more plays.
- **AC-R4** After three prompts, never again.
- **AC-R5** Tapping **Rate TS IPTV** opens the Play listing and suppresses the
  automatic prompt for 120 days.
- **AC-R6** The prompt is never requested while the player screen is showing.
- **AC-R7** No crash, no visible error, when Play Services / Play Store are
  missing (emulator images without Play, Huawei, sideloaded desktop).
- **AC-R8** The Rate item is visible on Android phone and TV, hidden on iOS and
  desktop.
- **AC-R9** The eligibility rules are unit-tested with an injected clock.

## Metrics to watch after release

- Ratings per week in Play Console, before vs. after.
- Average rating trend — a drop suggests the timing is wrong, not the app.
