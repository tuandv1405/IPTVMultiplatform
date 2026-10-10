# Hand-off: device QC 1 fixes

Branch: `fix/device-qc-1`, from `main` 0df2b20. Not pushed or merged. Date: 2026-10-11.
The evidence from the real device is in the QC scratchpad (`qc-device\`).

| # | Issue | Fix | Files |
|---|---|---|---|
| 1 | AdChoices missing on native ads (AdMob policy) | Each native row now has an explicit `AdChoicesView`:<br>• fixed 20×20 dp, top-right, in a `FrameLayout` over the row;<br>• assigned to `adView.adChoicesView`.<br>The "Ad" badge stays top-left, before the headline, and the CTA's top margin keeps the corner free. | `androidMain/ui/ads/AdSlots.android.kt` |
| 2 | App open ad ready only about 5.5 s after the first screen (4 s window) | The timeout is not raised. Instead:<br>• a late or unshown ad is **kept** (< 4 h);<br>• one is **preloaded** when the app goes to background;<br>• both are used by the **next launch in the same process** (a fresh `MainActivity.onCreate` after Back), where the ad shows at once;<br>• at most one app open ad per 30 min across launches.<br>Returning to a live screen and rotation are never a launch. PRD R2 is updated. | `AppOpenAdController.kt`, `MainActivity.kt`, `AdsPolicy.kt`, `docs/prd-admob.md` |
| 3 | Home banner requested while nearly hidden behind the bottom nav | The banner is started only when at least half of its expected height (`rememberBannerHeight()`) is above `viewportEnd - bottomBar`. | `HomeFeedScreen.kt` |
| 4 | Playlist picker stays open after a pick | Picking a playlist hides the sheet (animated), like the close button. | `HomeChangeIPTVSourceBottomSheet.kt` |
| 5 | Name typed on import ignored for TS IPTV Sources | **Decision:** the typed name is kept as the playlist's **local display name**. The form cannot know beforehand that a link is a Source, so a disabled field is not possible.<br>• Stored in `SourceDisplayNames` (key-value `source_display_name:<playlistId>`) and kept over refreshes.<br>• Typing the source's own name clears it; an import without a name keeps the earlier choice; removing the source forgets it.<br>• A LAN import whose name was generated from the link is not treated as typed.<br>• The source's home header and About page still show the source's own name (its identity). The picker and the playlist lists show the local name. | `TsiptvSourceService.kt`, `TsiptvModule.kt`, `HomeViewModel.kt` |
| 6 | Active playlist reverted once (unconfirmed) | Not reproduced on the emulator: importing B, then player → back → Programs → Home, kept B. I did find a race that matches the report. `HomeViewModel.init` reads the current playlist (A), then awaits `refreshIfStale(A)`, which for a Source can be a slow network refresh, and finally sets Home to A unconditionally. An import of B in between was reverted on Home only; Programs reads the stored id, so it showed B. Fixed: the start-up load is skipped when the stored current playlist or Home's playlist changed meanwhile. | `HomeViewModel.kt` |
| 7 | 11 WebViews after a short session | No leak found (details below). The banners are destroyed in `onDispose`, native views in `onRelease`, and native ads by the cache. | — |

## Verification

**Builds:**
- `desktopTest` passes (83 result files), with these new tests:
  - `AdsPolicyTest.appOpenAtMostOncePerIntervalAcrossLaunches`;
  - `TsiptvSourceServiceTest.typedNameIsKeptAsTheLocalNameOverRefreshes`.
- `compileCommonMainKotlinMetadata` and `assembleDebug` pass.
- `assembleRelease`: R8 passes, and there is no mapping upload task. Packaging fails only on the local keystore alias (known).

**Emulators** (TSIPTV_ADS_QA and TSIPTV_SUBS_QA, headless, names checked; debug build with `-Ptsiptv.debugSkipLogin=true -Ptsiptv.debugAdsNoFirstDay=true`):
- **#2, cold start:** "App open ad skipped: timeout", then "loaded at +8630 ms, kept for the next launch".
- **#2, relaunch after Back (same process):** "New launch in a running process (kept ad: true)", and the ad was on screen at once (AdActivity on top).
- **#2, second relaunch within 30 min:** "skipped: shown recently (kept)".
- **#4:** picking PlaylistB closed the sheet.
- **#5:** a Source imported with the name "QA Composed" is listed as "QA Composed" in the picker.
- **#6:** not reproduced, as described above.
- **#7:** WebView counts with a GC forced before each read (`kill -10`):

  | Step | WebViews |
  |---|---|
  | Start | 4 |
  | 9 Home ↔ History switches | 4 |
  | First player round trip | 6 |
  | 3 more player round trips | 6 |
  | 2 rotations | 6 |

  The count stays bounded:
  - the extra WebViews are the cached player native ad (kept ≤ 1 h by `NativeAdCache`) and the kept app open ad;
  - the device's 11 most likely included WebViews that were destroyed but not yet collected.
- **#1 (AdChoices view tree): not verified on an emulator.** Native test ads fail to load on both QA emulators today. The SDK logs "Incorrect native ad response. Click actions were not properly specified", error 0. This happens for the native and the native-video test units, before any view is bound, and the native code path is unchanged on `main`.

  The `AdChoicesView` has fixed 20 dp layout params, so it cannot be 0×0. Please confirm on the device re-test, using the view tree or a screenshot of the top-right icon.
