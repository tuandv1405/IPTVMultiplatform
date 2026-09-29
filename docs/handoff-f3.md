# Hand-off: F3 step 2 onwards (TS IPTV Source runtime, storage, UI)

Status: **done, not committed** · Branch: `feature/sources-and-formats` · Date: 2026-09-29
Scope: PRD `prd-tsiptv-source-format.md` "Implementation plan (ordered)" from step 2 onwards, on top
of F3 step 1 (`handoff-f3-step1.md`) and F2 (`handoff-f2.md`, Room v5 final). Normative spec:
`tsiptv-source-format.md` ("§" below refers to it).

Build status at hand-off (after QC round 1 fixes): `:composeApp:desktopTest` **481 tests, 0 failures**, and
`:composeApp:assembleDebug` is **green**. iOS was not compiled (it can't be built here). All new code
is in `commonMain`, except Android subtitles (`MediaPlayerService`) and desktop subtitles
(`DesktopMediaPlayer`).

## What was built

### Runtime (`core/tsiptv/`)

| File | Content |
|---|---|
| `SourceHttp.kt` | `SourceFetchRequest` (cap, sniff callback that lowers the cap to 5 MiB once the first 64 KiB look like a source, `If-None-Match` / `If-Modified-Since`, 15 s connect / 60 s total), `SourceFetchResult` Ok / NotModified / Failed(code), `KtorSourceHttpTransport` on F2's `createStremioHttpClient` (no https to http redirect, gzip `Content-Encoding`, no status exceptions, no logging). |
| `TsiptvSourceResolver.kt` | Depth-first include walk using `TsiptvIncludeGuard`: depth 3, 20 includes, 50 MiB budget including the root (304 counts 0), merged limits 20k/5k/1k. Per type: `m3u` (F1 `M3UParser`, `x-tvg-url` guides join the guide set), `xmltv`/`epg` (F1 EPG parsers), `stremio` (F2 probe + blocklist through `SourceAddonBridge`, manifest kept for `W_QUERY_REF` and catalogue rows), nested `tsiptv-source`. Conditional GET for every include. `refreshHours` scheduling (not-due includes reuse the stored copy). A failed include keeps the stale copy (`STALE`) and has no copy on first import (`FAILED`, its sections are hidden). Adult rule: on import, the adult include paths are returned to the UI. On refresh of an unconfirmed source, new adult content is withheld (`adultWithheld`) and the previous non-adult copy stays. `TsiptvSourceIds`: `ts:{playlistId}:{poolId}` channel ids, `tsiptv:{sourceId}` playlist ids. |
| `TsiptvSourceService.kt` | `fetchLink` (a sniffed source becomes a preview; any other format goes back to F1 as bytes; E_TOO_LARGE is reported as a document error), `preview`, `resolve(progress)`, `store` (one transaction: playlist + categories + channels with F1 carry-over of favourites/history, source row, includes, VOD, episodes; addons installed as owned; programmes replaced), `isRefreshDue` / `refreshIfDue` (root older than 24 h, or include `refreshHours`), `refresh(force)`, `confirmAdult`, `remove` (owned addons, `media_history` rows, programmes, playlist with cascade). |
| `TsiptvStore.kt`, `InMemoryTsiptvStore.kt`, `core/database/RoomTsiptvStore.kt` | Records and store for the four new tables. Room implementation swaps everything in one `immediateTransaction`. |
| `TsiptvVodMapping.kt`, `TsiptvDetailProvider.kt` | VOD rows ↔ models. The detail provider turns a source movie/series into F2's `StremioMeta` with inline streams, so F2's detail page, stream picker and history are reused with no addon requests. At play time, streams map back to headers, DRM, MIME type and subtitles. |
| `SourceHomeBuilder.kt` | Layout (stored or the §7.5 default), query engine (include restriction incl. nested, `ids` order, groups/genres/tags, sort, limit), hero banners (items or query, target resolution, decorative banners), catalogue refs, Continue watching, local accent-insensitive search. |
| `di/TsiptvModule.kt` | Koin wiring. |

### Storage (Room v6)

- `AppDatabase` version 6, `AutoMigration(5 → 6)`, schema exported as
  `composeApp/schemas/tss.t.tsiptv.core.database.AppDatabase/6.json`.
- New tables: `tsiptv_sources`, `tsiptv_includes`, `vod_items`, `vod_episodes`, each with a FK to
  `playlists` and `ON DELETE CASCADE`.
- New nullable `channel` columns: `epgId`, `nameJson`, `originIncludePath`, `tagsJson`,
  `descriptionJson`. `Channel.guideId` uses `epgId` first.
- Test: `AppDatabaseMigrationTest.migrate5To6KeepsEveryV5RowAndAddsSourceTables`. It checks every
  v5 table, then stores and removes a source on the migrated DB.

### Import path and UI

- `PlaylistImporter`: `importFromUrl` goes through `TsiptvSourceService.fetchLink`, and
  `importFileOutcome` handles picked files. Both return `ImportOutcome.SourcePreview`. Refresh and
  the daily refresh route `TSIPTV_SOURCE` playlists to the service.
- `HomeViewModel`: `SourceImportState` (Preview → Fetching "Loading i of n…" → AdultInclude →
  stored, or Rejected), a source import summary with Details, `zapChannels`, `isSourcePlaylist`,
  and events for import, adult confirmation, remove and play-from-section.
- `ui/screens/source/`:
  - `SourceDialogs.kt`: preview, Details (`path — translated explanation`), error, fetching and
    include-18+ dialogs, plus the `source_issue_*` mapping.
  - `SourceHomeScreen.kt`: header with search, About, Refresh and Settings; hero (phone pager with
    6 s auto-advance, TV banner with ◀/▶); rows with See all; grids with group chips and 60-item
    paging; catalogue rows; See all screen; Home/Channels switch.
  - `SourceTheme.kt`, `SourceCards.kt`: appearance after the contrast rules, card styles, accent
    focus ring.
  - `SourceAboutScreen.kt`: logo, name, author, description, facts, homepage behind a
    confirmation, the 18+ banner with Confirm, Refresh, Remove source with a confirmation, includes
    with status (OK / stale since date / failed / held back), and issues count with Details.
  - `SourceUiModule.kt`: `tsiptvUiModule`.
- Phone Home tab: when the active playlist is a source, Source Home shows with a Home/Channels
  switch. The switch is hidden when there are no channels. The overflow menu opens the usual
  settings sheet, so playlists can still be switched.
- TV (`TvHomeScreen`): the rail gets "Home" at the top, selected initially, and "About this source"
  above Settings. Focus goes to the hero on entry. `focusRestorer` is set per row, and the last
  focused card is restored after Back.
- Routes: `NavRoutes.SourceSeeAll`, `NavRoutes.SourceAbout`, and `MediaDetail.sourcePlaylistId`.
- Playback: a channel card plays through the Home flow, and the section is the TV zap list
  (`TvPlayerScreen` uses `zapChannels ?: listChannels`). Movies and episodes play through F2 detail
  with the `TSIPTV` history kind. Continue watching comes from `continueWatchingForSource(playlistId)`
  and is not shared between sources or with Discover.
- Subtitles:
  - Android: Media3 `SubtitleConfiguration` (VTT/SRT), loaded through the item's data source.
  - Desktop: VLC `addSlave(SUBTITLE)`. The track in the system language is selected.
  - iOS: not shown, as the guide states.
- Addon manager: addons owned by a source are listed under "From your sources". They can be switched
  on or off and updated, but not removed or configured. They are already excluded from
  Discover/search (F2 changes).
- Playlist switchers (phone sheet, TV dialog): sources get a Dashboard icon.
- Strings: 84 F3 keys (the 22 `source_issue_*` plus the other PRD strings) in all 7 locales.
  `StringResourcesLocaleTest.f3KeysArePresent` checks them.
- Parser follow-up (PRD Follow-up #3 row 5): `TsiptvRules.isDateTime` now checks calendar and
  clock ranges. An invalid value gives `W_FIELD` (`TsiptvDateTimeTest`).

### Tests added

| Test | Covers |
|---|---|
| `commonTest/.../core/tsiptv/TsiptvSourceServiceTest.kt` (12) | Import with include, preview before storing, stale include, conditional GET/304, adult include on import, adult withheld on refresh then Confirm, cycle, nothing loaded, rejected document, other formats pass through, remove, same-link update vs replace. |
| `commonTest/.../core/tsiptv/SourceHomeBuilderTest.kt` (7) | `vod-catalog` layout, Continue watching, default layout, `ids`, include restriction incl. nested, search, hero refs. |
| `commonTest/.../usecase/playlist/PlaylistImporterSourceTest.kt` (4) | Link/file routing, F1 formats unchanged, refresh through the service. |
| `commonTest/.../core/parser/tsiptv/TsiptvDateTimeTest.kt` | DATE_TIME ranges. |
| `desktopTest/.../AppDatabaseMigrationTest` (5 → 6) | AC-T27. |
| `StringResourcesLocaleTest.f3KeysArePresent` | AC-T28. |

The whole pipeline was also run once against the QA fixture server with the real Ktor transport
(temporary test, removed afterwards):
- root and `.gz` root: 9 channels, 2 movies, 1 series, 5 channels with guide data.
- Every document error gave the right code.
- `skips` gave `skipped=3`, with E_URL, E_HEADER_FORBIDDEN and E_DRM listed but not counted.
- Cycle, adult include, nothing loaded, stale include on 500, and adult withheld until confirmed all
  behaved as specified.

## QA fixture server

`qa/tsiptv-fixture/server.js` (Node ≥ 18, no dependencies, never shipped):

```
node qa/tsiptv-fixture/server.js                               # emulator: http://10.0.2.2:7100/root.tsiptv.json
PORT=7100 BASE=http://127.0.0.1:7100 node qa/tsiptv-fixture/server.js   # desktop
STREMIO=http://10.0.2.2:7000 …                                 # point the stremio include at qa/stremio-fixture
```

- Documents: `/root.tsiptv.json` (+ `.gz`), `/skips.tsiptv.json`, `/cycle.tsiptv.json`,
  `/adult-root…`, `/adult-include…`, `/only-includes…`, and `/bad/{not-json,not-source,no-id,version,too-large,empty}`.
- Switches: `/admin/fail?name=live|guide|nested&on=1`, `/admin/adult?on=1`, `/admin/revision`,
  `/admin/reset`.
- The log shows `If-None-Match`, User-Agent and Referer for each request.

## How to verify each AC-T

| AC | How |
|---|---|
| T1 | Import the three `web/public/examples` files by link (`https://tsiptv-8bdd6.web.app/examples/…`). Counts show in the preview. The `TsiptvExamplesTest` suite (step 1) checks they parse with zero errors. |
| T2 | Fixture `/bad/*`: error dialog "Can't add this source" with the matching message, and nothing stored. Automated: `rejectedDocumentStoresNothing`, plus step-1 tests. |
| T3 | Fixture `/skips.tsiptv.json`: preview says "3 items will be skipped", and Details lists every path. |
| T4–T6 | Parser (step 1 tests). |
| T7 | Switch the app language (Settings > Language). Source Home titles change without re-import: texts stay `LocalizedText` and resolve with `LocalAppLocale`. |
| T8 | Import `composed-includes`. Rows: M3U channels by number with guide data, addon catalogue, and the picked-by-hand row. |
| T9 / T22c | Fixture: import root, `/admin/fail?name=live&on=1`, then Refresh in About. Channels stay, the include reads "Couldn't refresh — showing the copy from …", and the report has `E_INCLUDE`. On first import, `/only-includes` gives "None of this source's content could be loaded". |
| T10 | Fixture `/cycle.tsiptv.json` gives `E_INCLUDE_CYCLE`. Depth and 21st-include limits come from `TsiptvIncludeGuard` (step-1 tests). |
| T11 | Refresh twice: the fixture log shows `INM=` and `304`. Automated: `refreshSendsConditionalHeadersAndReusesNotModifiedIncludes`. |
| T12 | Give an include `headers`. The fixture log shows them on the include request only. Stream requests carry only the channel's own headers (`/hls/master.m3u8` logs them). |
| T13 | With `STREMIO=` set, Addon manager shows the addon under "From your sources", with no Remove, not in Discover. Removing the source removes it. |
| T14–T16 | `vod-catalog` on phone and TV. Fixture `/skips` has a white background and dark accent, so the app colours are used and Details shows `W_CONTRAST`. |
| T17 | TV: Home rail item, focus on hero, ◀/▶ between targeted banners (decorative skipped), ▼/▲ keep the card per row, Back from detail returns to the card. |
| T18 | Fixture root "All channels" grid: chips filter it. |
| T19 | TV: open a channel from the "News (own channels)" row. ▲/▼ zap only through that row's channels. The fixture `QA Headers` channel shows UA/Referer in the log. |
| T20 | Play the fixture movie for 2 minutes. It appears in this source's Continue watching and not in Discover or another source. |
| T21 | Fixture movie has VTT + SRT. Android and desktop players offer the tracks. |
| T22 / T22b | Preview host list. `/adult-root` needs the checkbox. `/adult-include` shows the include-18+ dialog after loading, and Cancel stores nothing. Refresh rule: `/admin/adult?on=1`, then Refresh: About shows "Held back until 18+…" and Confirm merges the content. |
| T23 | Import `/root.tsiptv.json` from `127.0.0.1` and then from `10.0.2.2` (same id, other link). The preview asks "Replace the source …?". Favourites carry over via F1 `replacePlaylistContent`. |
| T24 | About > Remove source. Automated: `removeDeletesThePlaylistItsRowsHistoryAndOwnedAddons` and the migration test cascade. |
| T25 | Not measured (see "Not done"). |
| T26 | New code logs nothing with URLs. Every `toString()` of records and requests redacts URLs, and analytics carry format + count only. |
| T27 | `migrate5To6KeepsEveryV5RowAndAddsSourceTables`. A manual upgrade on a device was not done. |
| T28 | `f3KeysArePresent`. |

## Deviations and interpretations

(Updated after QC round 1: the old deviations 2, 3, 4 and 6 are gone, see "QC round 1 fixes".)

1. **Includes are fetched one after another**, not 4 in parallel. This keeps budget and limit
   accounting deterministic. Progress shows "Loading i of n…".
2. **The preview is a dialog on phone too**, not a bottom sheet. Import has initial focus on TV.
3. **Include-18+ Cancel aborts the whole import** (AC-T22b: "Cancel stores nothing").
4. **Refresh** from phone Source Home and Settings uses the Home flow (`PlaylistImporter.refresh`
   → service, forced). About's Refresh calls the service directly and shows its own status.
5. **Source-owned addons**: an addon the user installed stays the user's. An addon several sources
   include is owned by all of them (ref-counted) and removed with the last one.
6. **Catch-up** is stored (channel `catchupJson`) and never played, as the PRD says.
7. **About entry on phone** is in the Source Home overflow menu. On TV it's a rail item.
8. **Subtitles are Off by default**: the spec has no preselection rule. They are **never sent the
   stream's headers** (spec §12 says headers may carry tokens; a subtitle file is often on another
   host).
9. **Hero `autoplay` on a movie or episode** opens the detail route with `autoplay = true`: the page
   loads and the first playable stream starts at once (Back returns to the detail page).
10. **TV player options** open with ▶ on channels, ▲ on movies/episodes, and MENU / CAPTIONS keys.
    The banner hint says so.

## QC round 1 fixes (2026-09-29)

| # | Fix |
|---|---|
| 1, 19 | Player options dialog on phone (settings button) and TV (▶ / ▲ / MENU), D-pad items. It lists **Stream** (source channels with several streams) and **Subtitles** (Off + each track: Media3 `TrackSelectionParameters` on Android, VLC tracks on desktop). Side-loaded subtitles are not preselected (`selectionFlags = 0`), and the text choice resets per item. Subtitle requests go through `SubtitleAwareDataSourceFactory`, **without** the stream's headers. New `MediaPlayer.textTracks()/selectTextTrack()/supportsDrm` (defaults: none / no-op / false; iOS unchanged). |
| 2, 3, 21 | Programmes are stored **per guide**. Source programme ids are `tsg:{playlistId}#{guidePath}#{id}`. `TsiptvGuidePlan` (replace / drop) is written **inside** the source transaction. Kept guides (not due, 304, stale) keep their rows. An M3U that isn't re-parsed (not due / 304 / failed) still runs its stored `x-tvg-url` guides. The same guide URL is fetched and charged once per refresh; a second path mirrors the first one's status and stores nothing. |
| 4 | Every include body is gunzipped (bounded by its type cap) **before** it is charged to the 50 MiB budget. The root is charged by its decompressed size. |
| 5 | A per-source refresh lock (one refresh at a time) and write lock (store / confirm / remove). A store re-checks that the source row still exists, so a remove during a refresh wins (`Failed("missing")`). |
| 6 | `fetchLink/preview/resolve/store/refresh/confirmAdult/remove` run on `Dispatchers.Default`. F1 parsing already ran in `PlaylistImporter` (`withContext(Default)`) and `ParseProgramListUseCase` (IO). No other Main-thread parse path was found. |
| 7 | A failed root refresh keeps `fetchedAt` and records `lastErrorCode/lastErrorAt` (new `tsiptv_sources` columns). Includes are still refreshed from the stored root and the result is `Stored(rootError)` / `Failed`. Home shows `source_refresh_failed`, and About shows it plus "Last attempt: date". Retry is on the same 24 h schedule. |
| 17 | A refresh where the root is 304 / not due and every include is unchanged returns `Unchanged`. Only `fetchedAt` and the include rows' times are updated: no reparse and no content rewrite. |
| 18 | Programmes are written in the source transaction. Addon installs are undone (owners restored) if the source transaction fails. `CancellationException` is never swallowed (service, HomeViewModel). |
| 20 | `stremio_addons.ownerSourceId` holds every owning source (newline-separated). `removeOwnedBy` drops one owner, and the addon is removed only when none is left. |
| 23 | Non-source links stop after the 64 KiB sniff: a body that fits is parsed, and a larger one goes to F1's normal download (`LinkFetch.NotSource`), so nothing buffers up to 512 MiB. `store()` refuses (`TsiptvAdultConfirmationRequiredException`) when the root or an include is adult and 18+ was not confirmed. |
| Dev 3 | Channels keep **all** streams (`channel.streamsJson`, new column). `PlayerViewModel.playIptv` plays the first stream the platform supports (DRM needs Android: iOS/desktop fall back to the next one), and the options dialog lets the viewer pick another. |
| Dev 4 | Guide name matching: a channel without `epgId` (or an M3U channel without tvg-id) gets the guide channel whose `display-name` equals one of its names. The match ignores case, accents and spaces. Display names are kept per guide (xmltv include row `documentJson`), so kept guides still match. |
| Dev 6 | Hero `autoplay` plays the first playable stream directly (`MediaDetail.autoplay`). |
| 8, 9 | Phone Source Home uses `statusBarsPadding` (as Discover does), and the See all screen too. |
| 10 | Select Playlist rows: URL on one line with an ellipsis; date on one line. |
| 11 | `SourceBackground` provides the source accent to buttons, chips and TV focus rings (`LocalAddonAccent`, `LocalFocusRingColor`). Detail pages of source items take the source background and accent (`SourceAppearanceScope`). The Channels-side Home switch uses the accent too. Contrast rules are unchanged. |
| 12 | A `catalog` query whose catalogue the addon manifest doesn't declare adds `W_QUERY_REF` at `layout.home[i].query.catalog` (About › Details). |
| 13, 22 | Preview and About show `updatedAt` formatted (RFC 3339 in the local time zone; a plain date as written). |
| 14 | Details lines are `path — CODE: explanation`. |
| 15 | About › Homepage: when no browser opens (TV), the link is shown as text (F2's fallback). |
| 16 | Catalogue rows have See all (F2 catalogue screen). |
| 22 | Friendly include names in About ("Guide 1", "QA M3U · Guide 1"). Refresh Details are titled "Problems found". The success message uses plurals ("1 movie", "N items were skipped"). Placeholder initials use the first letters of the first two words that start with a letter ("Movie 10" → "M"). The phone search field gets focus. The name under a card opens the item. Remove source returns to the previously active playlist. |
| Fixture | `qa/tsiptv-fixture/server.js`: the catalogue row uses `tspd-movie` (declared by `qa/stremio-fixture`). A second row asks for `no-such-catalog` (#12). The `live` include sends headers (AC-T12: `UA=TSIPTV-QA-Include/1.0` on `/live.m3u` only). A `QA Multi` channel has 3 streams (DRM first) and no epgId, and is matched to the guide by name. |

**Room v6 (unshipped, so no v7):** `6.json` was regenerated with `channel.streamsJson`,
`tsiptv_sources.lastErrorCode` and `tsiptv_sources.lastErrorAt`. `AppDatabaseMigrationTest` (5 → 6)
checks the new columns and the per-guide programme ids, and still checks every v5 row.

**New strings** (7 locales): `player_options_title`, `player_stream`, `player_subtitles`,
`player_subtitles_off`, `player_no_options`, `tv_player_hint_options`, `tv_player_hint_options_vod`,
`source_details_title`, `source_added`, `source_last_refresh_failed`, `source_include_guide`, and the
plurals `source_n_channels`, `source_n_movies`, `source_n_series`, `source_items_were_skipped`.

**New tests:**
- `TsiptvRefreshRulesTest` (8 tests):
  - #2: tvg guides kept on a 304 / failing M3U
  - #3: a stale guide keeps its programmes while another changes
  - #4: the budget counts decompressed bytes
  - #5: remove during refresh
  - #7: a failed root refresh
  - #17: an all-304 refresh returns Unchanged, and a real change is still stored
  - streams list and first-playable choice
  - EPG name fallback for source and M3U channels
- `AddonRepositoryTest.sourceOwnedAddonsAreRefCounted` (#20).
- The migration test was extended.

## QC round 2 fixes (2026-09-30)

No schema change: `6.json` is untouched.

| # | Fix |
|---|---|
| 1 | The phone Source Home always emits the `switch` item (empty while there are no channels), so the list no longer opens anchored below it. |
| 2 | TV player: the options dialog and the "streams and subtitles" hint appear only when the item has more than one stream or at least one text track (checked each second while playing). Otherwise ▶ / ▲ / MENU keep showing the banner, with the old hint. |
| 3 | Programmes are de-duplicated by (channel, start, title) on read (`distinctProgrammes()` in the Room and in-memory `getProgramsForChannel`, `…InTimeRange` and `getCurrentAndUpcoming…`). Tests: `TsiptvRefreshRulesTest.programmesFromTwoGuidesAreNotListedTwice` and the migration test (Room). |
| 4 | After a partial refresh (root failed, includes stored), Home reloads the playlist name, categories, top-3 history and the channel list before it shows the notice. |
| nit | A subtitle without `label` shows the language name in the UI language (new `expect fun languageDisplayName`: `java.util.Locale` on Android/desktop, `NSLocale` on iOS; iOS not compiled here). |
| nit | The guide name key drops every space (`guideNameKey`: "BBCOne" == "BBC One"). Test: `nameMatchingIgnoresSpaces`. |
| nit | `previousPlaylistId` is saved in `KeyValueStorage` (`home_previous_playlist_id`), so Remove source goes back to it after a restart. |
| nit | "Off" already disables the whole text track type (`setTrackTypeDisabled(TEXT, true)` on Android, `deselect(TEXT)` on desktop), which includes embedded default and forced tracks. On a new item the text type is enabled again with no override, so the player's defaults apply: an embedded *forced* track may show until the viewer picks Off. The spec says nothing about forced subtitles. |
| nit | Catalogue See all opened near the end of the first page because the grid stayed anchored on the keyed footer item while the first page was inserted above it. The footer now has no stable key, so a fresh catalogue opens at the top. Back from a detail page still restores the saved grid position of the same screen. |

Build: `desktopTest` 483 tests, 0 failures; `assembleDebug` green.

## QC round 3 fixes (2026-09-30)

No schema change: `6.json` is untouched; only queries changed.

| # | Fix |
|---|---|
| M-R3-1 | The Programs tab queries now use the same (channel, start, title) de-duplication. `countValidPrograms` counts distinct rows and `getValidPrograms` groups by those three columns. `getChannelsWithValidProgramCounts` counts `COUNT(DISTINCT startTime\|title)` per guide channel. In-memory is the same, through the new `IPTVDatabase.getChannelsWithValidProgramCounts`, which the use case now calls instead of the DAO. |
| O1 | The Programs tab joins a programme to the channel **of the same playlist** whose `epgId` equals the guide id: this covers source channels, including name-fallback matches, which store the matched id in `epgId`. Channels without `epgId` (all F1 channels) still join by `id`, so F1 is unchanged. Source channels now show their name and logo. When several channels share a guide id, the first name and logo are used. The row's `channelId` stays the guide id, so the detail screen works as before. |

Tests: `TsiptvRefreshRulesTest.programmesFromTwoGuidesAreNotListedTwice` (in-memory) and `AppDatabaseMigrationTest` (Room) cover the count, the valid-programmes page, the per-channel count and the name for a source channel, plus the F1 join. Build: `desktopTest` 483 tests, 0 failures; `assembleDebug` green.

## Web guide (`web/public/guides/tsiptv-source/`): differences

These match: the preview contents and "N items will be skipped" + Details (E_URL,
E_HEADER_FORBIDDEN and E_DRM listed but not counted), "Loading i of n…", the Home/Channels switch,
the TV rail starting with Home, About contents (author, homepage behind a confirmation, include
status, last refresh, Details, Refresh, Remove source), adult include content held back until
confirmed in About, the support table (headers: Android all, iOS best effort, desktop UA + Referer;
DRM Android only; subtitles Android + desktop), the error messages table, the 304/50 MiB caching
text, the default layout, EPG matching by `epgId` then by name, and the next-stream fallback without
DRM.

These still differ:
- `autoplay: true` "plays the target": for movies and episodes the detail page opens and then plays
  at once; Back returns to the detail page.
- Success message: the guide says "the count in the success message". The app now says "Source
  added: 3 channels, 1 movie, 0 series" and "N items were skipped".
- Subtitles: the guide doesn't say they start Off. The app has no preselection and uses a player
  "Subtitles" menu.
- F1 labels quoted in the guide ("Add IPTV Source", "Source Link", "Import from file") were not
  re-checked; they are F1 strings that were not changed.

## Not done / for QC

- No manual device run by the developer in this round either (QC ran round 1 on the AVDs). The
  fixture server above has the new cases.
- AC-T25 performance with a large fixture was not measured.
- iOS compile not possible here. iOS gets only the new interface defaults (no track menu, no DRM).
- Web guide not edited (per instructions).
