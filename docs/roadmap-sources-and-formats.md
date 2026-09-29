# Roadmap — Sources and formats (F1 → F4)

Status: **Approved plan, 2026-09-27** · Owner: PO · Branch: `feature/sources-and-formats`

| Id | Feature | Spec |
|---|---|---|
| F1 | Kodi-compatible M3U and per-channel playback properties (headers, DRM, `.strm`, file import, Room v4) | [`prd-kodi-m3u-compat.md`](prd-kodi-m3u-compat.md) |
| F2 | Stremio-compatible addons (add, manage, browse, detail, streams, history, Room v5) | [`prd-stremio-addons.md`](prd-stremio-addons.md) |
| F3 | TS IPTV Source format (import, validation, includes, custom layout, Room v6) | [`prd-tsiptv-source-format.md`](prd-tsiptv-source-format.md) + normative [`tsiptv-source-format.md`](tsiptv-source-format.md) |
| F4 | Website format guides, examples, schema, validator | [`prd-web-format-guides.md`](prd-web-format-guides.md) |
| F1b | Catch-up playback (later) | to be written after F3 |

Binding for all of them: the app stays a **neutral player**. Protocols and formats only; no default
sources, addons or repositories; no torrent, Usenet or YouTube extraction; no Kodi add-on
execution; MonPlayer only with a user-supplied sample (`monplayer-parser-plan.md`, "Scope boundary").

---

## 1. Order and dependencies

```
F4-0 fixtures ──────────────┐ (sampler addon, example M3U/XMLTV, blocklist JSON, hosting headers)
                            │
F1 ──► F2 ──► F3 ──► F1b    │
 │      │      │            │
 │      │      └──► F4 wave C (TS IPTV Source page, validator)   publish with F3
 │      └──► F4 wave B (Stremio page)                           publish with F2
 └──► F4 wave A (hub, IPTV, M3U, XMLTV, XSPF, JSON, Xtream, Kodi, MonPlayer)  publish with F1
```

| Step | Needs | Why |
|---|---|---|
| **F1** first | — | Every later feature plays streams through F1's `MediaItem.headers`/`drm`, per-item data sources and error mapping; F3 reuses the M3U parser for `m3u` includes, the file picker and `PlaylistImporter`; the v4 migration establishes the non-destructive migration discipline. |
| **F2** second | F1 playback path | F3's `stremio` includes use F2's client and repository; F3's movie/series detail pages and `media_history` come from F2. |
| **F3** third | F1, F2 | Composes everything. |
| **F4** in waves | F4-0 before F2 QC; each wave published with its feature | Pages describe shipped behaviour only. |
| **F1b** after F3 | F1 data (already stored) | Needs a past-programme UI; no schema change. |

**Allowed overlap** (to shorten the calendar without breaking the order):

- F2 step 1 (protocol core: DTOs, URL encoding, matcher, client — no UI, no DB) may start while F1
  is in development; it only needs `MediaItem.headers`, which is F1 step 1.
- F3 step 1 (parser and validation) may start once F1's models (F1 step 1) are merged.
- F4 wave A content can be written during F1; F4-0 fixtures can be deployed immediately.
- **Room versions are strictly sequential**: v4 (F1) → v5 (F2) → v6 (F3). No feature may merge a
  schema change out of order, and no two branches may both claim the same version.

## 2. What each Dev agent must deliver

Common to every app feature (F1, F2, F3):

1. Code following the PRD's implementation plan, on a branch off `feature/sources-and-formats`.
2. Unit tests in `commonTest`/`desktopTest` for every parser, mapper, URL builder and rule the PRD
   marks as testable; all existing tests still green (`./gradlew :composeApp:desktopTest`).
3. **Room**: bumped version, exported schema JSON committed under `composeApp/schemas/…`, an
   AutoMigration (or explicit Migration) plus a migration test from the previous version, and
   `InMemoryIPTVDatabase` kept in sync. `fallbackToDestructiveMigration(true)` remains only as a
   last resort; a migration that falls back destroys user data and is a release blocker.
4. Strings in all seven locales (en, vi, de, es, fr, ja, zh-CN) and a test that compares key sets.
5. Phone and TV UI, with D-pad focus order as specified; iOS and desktop behaviour as specified
   (iOS compile verified on a Mac, or flagged as "not compiled" in the hand-off).
6. A hand-off note in the PR: what was built, deviations from the PRD with reasons, known gaps,
   and how QC can reproduce each AC (fixture URLs, emulator setup).
7. No third-party source, addon or repository URL in production source sets; no playlist/addon/
   source URL, header value or token in logs, analytics or crash reports.

Feature-specific deliverables:

| Feature | Also deliver |
|---|---|
| **F1** | `PlaylistImporter` replacing the three import paths; M3U/DRM/`.strm` fixtures under `commonTest/kotlin/assests/kodi/`; platform file pickers; removal of the hard-coded Widevine licence URL; analytics URL removal and a check of `play-store/data-safety.md`. |
| **F2** | `core/stremio/**` with recorded fixtures under `commonTest/kotlin/assests/stremio/`; `SecretCipher` expect/actual; the QC fixture harness in `qa/stremio-fixture/` (local SDK addon with a 250-item catalogue and a mixed-kind stream response), never shipped; blocklist fetch. |
| **F3** | `core/parser/tsiptv/**`; invalid and generated large fixtures under `commonTest/kotlin/assests/tsiptv/`; a desktop test that imports the three published examples from `web/public/examples/`; conditional-GET support in `KtorNetworkClient`; performance measurement on the reference emulators. |
| **F4** | Pages and assets per the PRD; `web/scripts/check_guides.py`; `WebExamplesParseTest.kt` (after F1); F4-0 fixtures deployed and verified with `curl -I`; `web/README.md` page table updated. |

## 3. QC gates

Each gate must pass before the next step starts or the feature is released. QC writes
`docs/qc-report-<date>-<feature>.md` in the style of `docs/qc-report-2026-09-27.md`.

| Gate | When | Pass criteria |
|---|---|---|
| **G0 Spec** | before development | PO sign-off of the PRD; open questions answered; for F3, spec + schema + examples frozen (schema changes after G0 must be additive). |
| **G1 Unit** | every PR | All unit and migration tests green in CI; new tests cover the PRD's parser/URL/rule ACs; locale key-set test green. |
| **G2 Policy** | every PR touching the app or site | Denylist grep clean (production source sets and `web/public`); no default sources/addons; analytics debug view shows no URLs; unsupported stream kinds hidden without counts (F2); guide content links only to the allowlist (F4). |
| **G3 Device** | before merge to `feature/sources-and-formats` | Every AC of the PRD executed on: Android phone emulator (API 37, Pixel_10a), Android TV emulator (`android-36;android-tv`) with D-pad only, desktop (Windows), and iOS simulator for iOS-specific ACs (or explicitly deferred with the PO's agreement). Proxy logs attached for header/DRM ACs. |
| **G4 Upgrade** | before release | Install the previous release build, create data (playlists, favourites, history, and for F3 an addon), upgrade to the new build: nothing lost; migration path exercised on phone and TV. |
| **G5 Release** | before a Play track upload | `play-store/RELEASE-CHECKLIST.md` updated (new permissions: none expected; data safety unchanged or updated); store listing text reviewed: formats named, no source/addon names, no "Stremio" in listing or screenshots; screenshots use only TS IPTV's own examples; the matching F4 wave deployed and its checks green. |

Additional gate content per feature:

- **F1**: AC-K16–K18 with a proxy (`mitmproxy`) showing headers and licence requests; AC-K29 upgrade.
- **F2**: AC-S16 reviewed by the PO personally (the "no torrent advertising" rule); AC-S21 secrets.
- **F3**: AC-T25 performance numbers recorded; AC-T22 host list; the three examples imported from
  their live URLs.
- **F4**: AC-W6 manual review by the PO; AC-W16 Vietnamese review.

## 4. Scope cuts and decisions (summary)

| Decision | Reason |
|---|---|
| Catch-up **stored in F1, played in F1b** | No past-programme UI exists; modes can't be QC'd without real archive servers; data is already persisted so F1b needs no migration. |
| DRM licence wrappers, custom PSSH, server certificates, WisePlay, FairPlay **out** | Need custom `MediaDrmCallback`/CDM work for rare setups; unsupported configurations fail with a clear message instead of silently. |
| iOS and desktop: **no DRM** | AVPlayer offers only FairPlay; VLCJ has no Widevine. Clear "not supported on this device" message. |
| Desktop headers: **User-Agent and Referer only** | VLC exposes only those media options. |
| Stremio subtitles, EPG addons, `addon_catalog`, auto-next **later** | Keep F2 to the browse → detail → stream core; none is needed to play. |
| Unsupported Stremio streams **hidden by default, no counts, no kind labels** | Satisfies "shown as unsupported" on request without advertising torrents. |
| No `stremio://` scheme registration; no "Stremio" in the store listing | Avoids competing with / implying affiliation with the Stremio app. |
| Discover appears **only after the user adds an addon** | A fresh install shows nothing addon-related, so nothing invites users to look for addons. |
| TS IPTV Source: **http(s) URLs only**, even for streams | Predictable cross-platform playback and a simple security rule; RTMP/RTSP/UDP remain available through M3U. |
| TS IPTV Source: **no outbound links from banners**, homepage link only with confirmation | A third-party file cannot push users to websites. |
| TS IPTV Source: nested includes **ignore the included document's layout and appearance** | One layout per source, no conflicting themes; includes contribute content only. |
| TS IPTV Source: forward compatibility by **ignoring unknown members and defined fallbacks for unknown enum values**; `version` bumps only on breaking change | Old apps keep reading new files; the schema grows additively at the same URL. |
| Website validator **cut rather than loaded from a CDN** if no small vendorable bundle exists | The site loads no third-party scripts for this feature. |
| MonPlayer page **noindex**, facts only | Supports users who ask, without ranking for searches associated with unlicensed redistribution. |

## 5. Risks and follow-ups

| Id | Risk | Mitigation / owner |
|---|---|---|
| **R1** | `channel.id` and `categories.id` are global primary keys: the same `tvg-id` or group name in two playlists makes the second import take over the first's rows (existing bug). | F2/F3 namespace their own ids. A dedicated id-rewrite migration (rewriting `channel_history` and favourites) is scheduled after F3, or earlier if QC reproduces it with real user playlists. |
| R2 | `fallbackToDestructiveMigration(true)` hides migration bugs by wiping data. | Migration tests (G1) and upgrade tests (G4) for v4, v5, v6. Consider removing the fallback after v6 ships. |
| R3 | The community directory `/playlists/` (contributor programme) is itself a list of sources, which sits uneasily with the "no source listing" rule applied to guides. | Guides do not link to it. The PO raises a separate policy review of the directory with the owner before the F4 wave A release. |
| R4 | Adult addons / sources in a 13+ app. | 18+ confirmation, exclusion from Discover and search; PO to re-check content rating answers (`play-store/content-rating.md`) before F2 release. |
| R5 | Large TS IPTV Sources on low-memory TV devices. | F3 performance AC with the generated fixture on the TV emulator; streaming parse if needed. |
| R6 | `AVURLAssetHTTPHeaderFieldsKey` is undocumented on iOS. | Best effort, stated in the M3U and Kodi guides' support tables. |
| R7 | Static example files under `/examples/` are QA fixtures and public at the same time. | They contain only public test streams, public-domain films and `example.com` placeholders; changes go through the F4 check script. |
