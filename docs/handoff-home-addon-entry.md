# Hand-off: Home addon entry and addon help

Branch: `feature/home-addon-entry` (from `main` 555dc63). It is not pushed or merged. Date: 2026-10-10.

## What changed

1. **Addon help in the app** (`AddonsScreen`, phone and TV):
   - A "What are addons?" card (`AddonsHelpCard`), with neutral wording from
     `docs/prd-stremio-addons.md`: what an addon is, how to add one (the manifest link), and that
     TS IPTV ships and recommends no addons.
   - A **Full guide** button opens `https://tsiptv-8bdd6.web.app/guides/stremio-addons/` (Vietnamese
     UI) or the same URL with `?lang=en` (every other UI language) through `UrlOpener`. When no
     browser opens it (TV), the existing dialog shows the URL as text.
   - Empty state: the card shows under the "No addons yet" text, expanded.
   - With addons: the card is the first list item, collapsed.
   - On TV the card is D-pad focusable (OK expands it).
   - New strings `addons_help_title`, `addons_help_body`, `addons_help_guide` in 7 locales.
2. **Home settings sheet (phone):**
   - New **Addons** item (`Icons.Rounded.Extension`) opens `NavRoutes.Addons`.
   - It sits after Refresh, the same place and icon as in the TV settings dialog.
3. **Home header icon:**
   - The gear is now `Icons.Rounded.Tune`, with the content description "Sources and options" /
     "Nguồn & tuỳ chọn" (`home_options_title`, 7 locales).
   - The Profile tab keeps the gear for app settings.
   - **TV:** the rail item stays "Settings" with a gear. It is labelled and there is no second gear
     on TV, so it is consistent there.

## Verified

- `desktopTest` passes, including the new `AddonsGuideUrlTest` and `StringResourcesLocaleTest`.
- `compileCommonMainKotlinMetadata` and `assembleDebug` pass.
- **On the TSIPTV_ADS_QA emulator** (headless, `-Ptsiptv.debugSkipLogin=true`):
  - the header shows the tune icon, with content-desc "Sources and options";
  - the sheet lists Addons after Refresh;
  - Addons opens the screen with the empty state and the open help card;
  - Full guide opened Chrome.
  - Screenshots are in the session scratchpad `addonqa/` (`a1_home_header`, `a2_sheet`,
    `a3_addons`).
