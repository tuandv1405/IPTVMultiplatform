# PRD: custom ads on Android TV (house, direct-sold and VAST)

Status: **draft for review, rev. 2 (QC)** · Owner: PO · Date: 2026-10-10 · Code: none yet (plan only)
Related: `docs/prd-admob.md` (phone ads; TV has **no** AdMob), `docs/prd-tv-cast-and-sync.md`
(rewarded ads, entitlements), `feature/subscriptions` (no-ads and unlimited plans; defines the
entitlement, T8), web support page `/support/` (`web/public/support/`).

## 0. Summary of the recommendations

| Topic | Recommendation |
|---|---|
| Phases | **Phase 1** (M1–M4): our own lightweight campaign system, house ads, Shopee affiliate offers, Home carousel, in-player corner banner, tracking, campaign admin. **Phase 2** (M5): pre-roll video, our own MP4 creatives and **VAST/VMAP tags through the Google IMA SDK** (Media3 IMA extension). **Phase 3** (M6–M7, each optional): pause-screen card; **Google Ad Manager**, only when inventory justifies it (§4.2). |
| Ad source | One signed JSON "ad decision" file published from Firestore to a CDN; targeting and caps on the device. `vast` is a creative type in the schema from phase 1 (so brands can be sold "bring your VAST tag"), but it is **played** only from phase 2. GAM, if ever, is one more `TvAdSource` behind the same interface. |
| Formats | Home carousel banner (phase 1), in-player corner banner (phase 1, subject to the legal gate in §6.4), pre-roll video (phase 2, brand campaigns only, VOD only by default, skippable at ≤ 5 s), pause-screen card (phase 3, optional). |
| Fallback chain | Brand campaign → **Shopee affiliate offer** → house card → nothing (§5.1). |
| No brand campaign | House fallback. **Play-distributed builds must not show a bank/MoMo/crypto donation QR** (Play Payments policy, §6.1). They show either a **Play Billing "Support the developer" product** (recommended) or a non-payment house card (guides, community playlists, "rate the app"). The bank/donate QR is allowed only in builds that are not distributed through Google Play (the `direct` flavour, §8 M2), switched by remote config + the build flavour. |
| 24 h ad-free start | **Applies to TV too** (same `AdsPolicy.isInAdFreePeriod`, same `ads_first_use_ms`). |
| Live channels and sports | Default (T5): **no pre-roll on any live channel** (VOD only); the in-player corner banner is allowed on live channels with the §3.2 rules. Q3 asks whether to also suppress it on sports groups. |
| Brand safety / copyright | Ads next to **user-supplied streams** are an explicit legal item (§6.4). Until cleared, in-player and pre-roll ads run only on allowlisted content; the Home carousel is not tied to any stream. |
| Admin | Extend the web admin console (`web/public/contributor/admin/` pattern) with a "Campaigns" page; Firestore rules: admin-only writes, public read of the published ad decision only. |
| Measurement | Own beacon endpoint (impression, viewable, quartiles, click/QR scan) with **no PII**, aggregated per campaign/day; QR scans counted by a short-link redirect with UTM. |

---

## 1. Goals and non-goals

Goals
- Earn revenue on Android TV, where AdMob is excluded (`prd-admob.md` §3), with formats that work
  with a remote and a 10-foot screen.
- Let brands buy **video** and **banner** campaigns directly, and later plug in their own ad server
  (VAST/VMAP) or Google Ad Manager.
- When no brand campaign runs, the slots still do something useful: a **house** card (support the
  developer, guides, community playlists).
- Never break playback, focus or Back.

Non-goals (this PRD)
- Ads on phone layouts (AdMob already covers them), iOS, desktop.
- Programmatic real-time bidding, header bidding, mediation.
- Invoices, insertion orders (IO), contracts, payments from brands: handled outside the product
  (spreadsheet + bank transfer) in phase 1; listed in §4.6 as out of scope.
- Personalised (interest-based) targeting.

## 2. Context (what exists today)

- TV layout: `ui/tv/TvHomeScreen.kt` (left `CategoryRail`, right `ChannelGrid` / `SourceHomeContent`
  / `DiscoverContent`), `TvPlayerScreen.kt` (full-screen video; **all D-pad keys are consumed by
  the root key handler**: ▲/▼ zap, OK opens the channel list on the left, ◀/▶/Info show the channel
  banner at the **bottom**, Back closes the list then leaves), `TvSettingsDialog.kt`,
  `TvFocusable.kt` (`TvFocusableSurface`, `tvFocusBorder`).
- Ad gating: `core/ads/AdsGate.kt` → `AdsState(adMob, fallback)`; `AdsPolicy` (24 h,
  `nativeSlots`). TV currently gets `adMob = false, fallback = false`.
- Shopee affiliate offers: `IAdsRepository` (`getAdsList`, `getAdsVideoList`, `getOpenAdsList`)
  reading URLs from remote config keys `ads_url`, `ads_video`, `ads_open_app`;
  `ShopeeAffiliateAds` already carries `bannerImage`, `videoUrl`, `qrCodeLink`.
- Remote config: `HttpRemoteConfigImpl` (a JSON file on a static host, cached in `KeyValueStorage`).
- Rewarded ads (`RewardedAdGateway`) and `Entitlement` (paid phase) from `prd-tv-cast-and-sync.md`.
- Web admin pattern: `web/public/contributor/admin/` + `firestore.rules` (`isAdmin()`), and the
  `telegram-bot/` server as the planned own backend.

## 3. Formats and placements on TV

All TV ads carry a visible **"Quảng cáo" / "Ad"** label (top-left, same as the phone) and, for
brand campaigns, the advertiser name. Sizes are in dp at 1920×1080 (= 960×540 dp at xhdpi TV,
Compose `dp` on a 1080p TV ≈ 2 px). **Safe area:** everything the app draws for an ad stays
inside the TV title-safe area, i.e. **5 % of the screen width from the left/right edges and 5 %
of the height from the top/bottom** (the 90 % × 90 % centre).

### 3.1 Home carousel banner (phase 1)

- **Where:** first row of the TV Home content pane (above the channel grid and above
  `SourceHomeContent` sections), full width of the pane, **4:1** creative (1600×400 px). Not
  shown in Discover, Settings or the addon manager.
- **Height guardrail:** the carousel is at most **22 % of the screen height** (≈ 120 dp at
  540 dp) including its dots, and at least **two full rows** of channel cards stay visible below
  it at rest. If the pane is narrower than 4 × that height, the creative is scaled to the pane
  width; if wider, it is centred and letterboxed with the pane background. A test checks the
  guardrail at 720p and 1080p and with font scale 1.3.
- **Slides:** 1–5 creatives from the ad decision (brand campaigns first, then house). Auto-advance
  every **8 s** with a crossfade; dots under the banner show the position.
- **Focus:** the carousel is **one focusable item** (`TvFocusableSurface`). It is **never the
  initial focus**: the existing start focus (selected category / playing channel) is unchanged.
  ▼ from the carousel goes to the first grid row; ▲ from the first grid row goes to the carousel;
  ◀ from the carousel goes to the rail.
- **While focused:** auto-advance **pauses**; ◀/▶ move between slides (◀ on the first slide moves
  focus to the rail, as today); OK opens the slide's **action** (§3.5); a focus border as on
  channel cards.
- Auto-advance also pauses when the app is not resumed or a dialog is open.
- Collapses (no empty space) when there is nothing to show, during the 24 h ad-free start and when
  ads are disabled by remote config.

### 3.2 In-player banner (phase 1)

- **Where:** **top-right corner**, at most **40 % of the screen width × 40 % of the height**
  (384×216 dp at 960×540 dp; a 16:9 or 16:5 creative, or a QR card of 40 % of the height),
  inset **5 % of the width from the right edge and 5 % of the height from the top** (title-safe).
- **Content gate:** only on streams allowed by §6.4 (until the legal item is cleared).
  The channel banner and VOD progress bar are at the **bottom**, the channel list on the **left**,
  subtitles at the bottom: the top-right corner never overlaps them.
- **Not focusable.** The player has no on-screen controls; all keys keep their current meaning.
  The banner therefore has no click; brand banners may carry a QR (scanned with the phone).
- **When:** at most once every **20 min** of watching, first time **≥ 5 min** after playback
  starts, shown **15 s**, then fades out. Never while buffering, during a playback error, while the
  channel list or the options dialog is open, or in the first **60 s** after a zap (zapping users
  are browsing).
- **Hidden at once** when the user presses any key that shows the channel banner (◀/▶/Info), opens
  the channel list or options, or when subtitles move to the top (a subtitle cue with top
  positioning hides the banner for that cue).
- **Back** keeps its meaning (close list, then leave the player); the banner never consumes Back.

### 3.3 Pre-roll video (phase 2)

- **Where:** before **VOD** playback only (TS IPTV Source movies/episodes, addon streams), on
  content allowed by §6.4. **No pre-roll on live channels** (T5); never on zapping.
- **Brand campaigns only.** No house or Shopee video pre-roll (a house message as pre-roll is only friction).
- **Length:** 6–30 s. **Skippable at 5 s at the latest** (the "Skip" button appears at ≤ 5 s;
  see the VN rule in §6.2). Non-skippable only for bumpers of **≤ 6 s**, opt-in per campaign.
- **VAST skip rule:** a VAST linear ad longer than 6 s must carry `skipoffset` ≤ 5 s
  (as a time such as `00:00:05`, or a percentage that resolves to ≤ 5 s). The app checks after the ad loads (IMA
  `Ad.isSkippable()` and `Ad.getSkipTimeOffset()`, or our own parser for own creatives); a
  non-skippable ad > 6 s or an offset > 5 s is **discarded** (counted as `error: not_skippable`),
  and content starts. The creative spec (§4.5) states this, and the admin refuses such tags
  when it can parse them.
- **Skip with OK**: our own Skip button (own creatives) is the only focusable element and takes
  focus when it appears (an expected focus change, not a steal: nothing else is focusable on
  screen). For IMA, set `AdsRenderingSettings.setFocusSkipButtonWhenAvailable(true)` so the IMA
  skip button gets D-pad focus, keep only the attribution and countdown UI, and **suppress
  click-through** (no browser on TV; "Learn more" is not shown) — verify these against the IMA
  version we ship. A key handler maps OK/Enter to `AdsManager.skip()` once `isSkippable`, as a
  fallback when the WebView-based button does not take focus on a box.
- **Back** after the skip offset skips the ad; before it, Back leaves the player (cancels the
  open of the content). Back never traps the user.
- **Frequency caps:** max **1 pre-roll per 30 min** of watching per device, max **4 per day**, max
  per campaign per device per day from the campaign (default 2). No pre-roll in the first **3**
  content opens of a session (the user is still choosing).
- **Timeout:** if the ad is not ready within **2 s** (VAST: 3 s including the wrapper chain), skip
  it and start the content. A failed ad never delays content more than that.
- **Playback, one decoder by default:** many low-end TV boxes have a single hardware video
  decoder instance. Default path: **one** content `ExoPlayer`.
  - VAST/VMAP: the Media3 IMA extension (`ImaAdsLoader`) inserts the ad into the content player
    itself (no second decoder).
  - Own MP4/HLS creatives: played as the first item of the same player (`setMediaItems([ad,
    content])`, ad item not seekable), then content.
  - A parallel second player (pre-buffering content while the ad plays) is used only when
    `MediaCodecInfo.CodecCapabilities.getMaxSupportedInstances()` ≥ 2 for the content codec
    **and** no decoder init failure was seen on this device; on any `DecoderInitializationException`
    the device is marked single-decoder (stored) and the sequential path is used from then on.
  - Test devices: one low-end box (e.g. Amlogic S905-class, 1–2 GB RAM, Android TV 9–11) and one
    current Google TV device; the test plan covers both paths.

### 3.4 Pause-screen ad (phase 3, optional)

- When the user pauses VOD (not live) for **≥ 3 s**, a card (max 30 % of the width, right side,
  vertically centred) shows a brand banner or house QR. Disappears on any key. Not focusable.
  Never on live channels (pause on live is rare and usually means "phone call"). Only on content
  allowed by §6.4; inside the title-safe area.

### 3.5 Actions (what OK does on a carousel slide)

On a TV there is no browser to finish a purchase. Actions are:
- `qr`: show the slide's QR full-size in a dialog ("Scan with your phone"); Back closes.
- `deeplink`: open an in-app destination (a TS IPTV Source, a community playlist, the guides).
- `app`: open a Play Store page of a **brand's** app (`market://details?id=…`), brand campaigns only.
- `none`.
No WebView, no arbitrary URL opening on TV.

### 3.6 10-foot UX rules (all formats)

- Focus order: rail → (carousel) → grid; the carousel is reachable but never steals focus.
- **Back always dismisses** the topmost ad UI (QR dialog, pre-roll after 5 s, pause card); never
  leaves the user stuck.
- No ad UI appears while a dialog, Settings, the import flow, the addon manager, login, the device
  limit or the "receive from phone" screen is open.
- Text ≥ 18 sp; contrast ≥ 4.5:1; no flashing animations; motion off with "remove animations".
- The ad label is always visible; brand creatives must not imitate app UI (no fake buttons).

## 4. Ad source architecture

### 4.1 Options

| | (a) Own campaign system | (b) VAST/VMAP via Google IMA | (c) Google Ad Manager (GAM) |
|---|---|---|---|
| What | Campaign docs + creatives we host; the app picks an ad from a JSON decision | The app plays any VAST 2/3/4 / VMAP tag the brand (or its agency ad server) gives us | GAM is the ad server; we put GAM tags (VAST for video; native/banner via the GMA SDK) in the app |
| Banners on TV | Yes (our own Compose UI, D-pad ready) | Video only (companion banners are possible but rarely used) | Video yes via IMA; banners would need GMA SDK views (same TV problems as AdMob) |
| Brand video | Our MP4/HLS creatives | Yes, with the brand's own tracking (third-party verification) | Yes |
| Cost / effort | Low–medium; no SDK; we write targeting, caps, tracking | Medium; IMA SDK + Media3 extension; tracking done by IMA | High setup, GAM account approval, needs traffic; revenue share for AdX |
| Measurement trusted by brands | Our own numbers (brands may not accept them for big buys) | Brand's ad server counts (trusted) | GAM counts (trusted), AdX demand |
| Works with no brand | Yes (house ads) | No | Partly (AdX backfill, if approved for CTV app inventory) |

### 4.2 Recommendation: phased

1. **Phase 1 (M1–M4) – own system, banners first.** House ads, Shopee affiliate offers and
   direct-sold banner campaigns from our system; Home carousel and in-player corner banner;
   tracking; campaign admin. Creative types: `image`, `qr` (rendered from a URL at runtime or a
   static image), `video` (MP4/HLS we host) and **`vast`** (a tag URL). The schema has `video` and
   `vast` from day one, but they are **played only from phase 2**; in phase 1 the app ignores them.
2. **Phase 2 (M5) – pre-roll video** with our MP4 creatives **and** IMA for `vast` creatives;
   quartile tracking. Same decision file.
3. **Phase 3 (M6, M7) – optional:** the pause-screen card (M6), and **Google Ad Manager** (M7)
   only if monthly TV impressions make it worthwhile (rule of thumb: > 1 M video starts/month).
   GAM becomes one `TvAdSource` with a lower priority than direct-sold campaigns; Shopee and
   house stay the final fallbacks.

Why: the TV audience is small and mostly in Vietnam today; direct deals with local brands, our
house messages and our own UI (D-pad, QR) matter more than programmatic demand. VAST support
makes us compatible with agency ad servers without running one.

### 4.3 Components (phase 1)

```
            Admin console (web)  ──writes──►  Firestore: ad_campaigns/{id}, ad_creatives/{id}
                                                │  (admin-only; Cloud Function / bot job)
                                                ▼
                        publish job ──► static JSON  /ads/tv-decision-v1.json  (CDN, cache 5 min)
                                                │
TV app: TvAdRepository ──GET (If-None-Match)────┘
        TvAdPolicy (targeting, caps, schedule, weight)  ──►  TvAdSlot UI (carousel / corner / pre-roll)
        TvAdTracker ──batched beacons──► POST /ads/events  (telegram-bot server or Cloud Function)
```

- **Decision file, not a per-request ad server.** One JSON with every active campaign and its
  rules; the app does targeting and frequency capping **on device**. Cheap (static CDN), works
  offline from cache, no per-request PII. Size cap 256 KB; carries a version and `expiresAt`.
- **Signing:** the publish job signs the file as a **detached JWS with ES256** (ECDSA P-256 +
  SHA-256; `tv-decision-v1.json.sig`, header `{"alg":"ES256","kid":"…"}`). The app pins the
  public keys (current + next, for rotation) and rejects an unsigned, badly signed, expired or
  oversize file, keeping the last good one. ES256 is verifiable with `java.security` on every
  Android version we support (Ed25519 needs API 33+). The private key lives only in the
  publisher's secret store (Cloud Function / bot server), never in Firestore or the repo.
- **Source of the URL:** remote config key `tv_ads_decision_url` (and kill switch `tv_ads_enabled`,
  per-format switches `tv_ads_carousel`, `tv_ads_player_banner`, `tv_ads_preroll`).
- **Creatives:** Firebase Storage (or the CDN in front of the bot server), immutable URLs with a
  content hash. Images ≤ 300 KB, video per §5.
- **Store choice:** Firestore for campaign editing (we already have admin auth and rules);
  the **published JSON** is what the app reads, so moving to the bot server later changes only the
  publisher.

### 4.4 Decision JSON (v1, sketch)

```json
{
  "v": 1, "generatedAt": "2026-11-01T00:00:00Z", "expiresAt": "2026-11-02T00:00:00Z",
  "house": {
    "supportQr": { "mode": "play_billing | site_qr | bank_qr | off", "url": "https://<site>/support/?utm_source=tv&utm_medium=house&utm_campaign=support" }
  },
  "campaigns": [{
    "id": "c_2026_11_brandx", "advertiser": "Brand X", "priority": 10, "weight": 3,
    "start": "2026-11-01T00:00:00+07:00", "end": "2026-11-30T23:59:59+07:00",
    "target": { "countries": ["VN"], "langs": ["vi"], "hours": [[18, 23]], "minAppVersion": 120 },
    "caps": { "perDevicePerDay": 6, "perSession": 2 },
    "slots": ["home_carousel", "player_corner", "preroll"],
    "creatives": [
      { "id": "cr1", "type": "image", "slot": "home_carousel", "url": "https://cdn…/cr1.webp", "w": 1600, "h": 500,
        "action": { "type": "qr", "url": "https://s.tsiptv…/x1" } },
      { "id": "cr2", "type": "vast", "slot": "preroll", "tag": "https://ad.brand-agency…/vast?…" }
    ],
    "category": "fmcg", "label": "Quảng cáo"
  }]
}
```

Selection: filter by schedule, targeting, caps, slot → highest `priority` wins; within a
priority, weighted random by `weight`. Shopee offers rank below every brand campaign, house
entries are priority 0.

**How the country is obtained (no IP geolocation, no location permission):** TV boxes have no
SIM, so (1) the Play Billing country (`BillingClient.getBillingConfigAsync` → `countryCode`)
once Play Billing ships (M2) in the `play` flavour; else (2) the region of the device locale
(`Locale.getDefault().country`); else (3) unknown, which matches only campaigns without a
country filter. Language = the app language; hour = the device's local time. The decision file
is a static CDN file, so no server sees a per-device request with targeting data.

### 4.5 Creative spec for brands (TV, 1080p)

| Creative | Spec |
|---|---|
| Home carousel image | 1600×400 px (4:1), WebP or PNG/JPEG, ≤ 300 KB, sRGB. Keep text and logos inside the **central 90 % of the width × 80 % of the height**; keep the **top-left 10 % × 15 %** free for the app's "Quảng cáo/Ad" label; no text smaller than 7 % of the creative height; no fake buttons. |
| In-player corner | 768×432 px (16:9) or 768×240 (16:5), WebP/PNG, ≤ 200 KB, key content inside the central 90 % × 90 %; or a QR card (we render the QR; brand gives the URL + a 1-line caption ≤ 40 characters). |
| QR target | HTTPS URL; we wrap it in our short link for counting (§7.3). |
| Pre-roll video (our hosting) | MP4 (H.264 High@4.1, AAC-LC 48 kHz stereo) **and** optionally HLS; 1920×1080 or 1280×720, 25/30 fps, CBR/VBR 6–8 Mbps (1080p) / 3–4 Mbps (720p); loudness **−23 LUFS ±1** (EBU R128), true peak ≤ −1 dBTP; 6, 15, 20 or 30 s; file ≤ 30 MB; no letterbox; key content inside the central 90 % × 90 % (title-safe); first 5 s must stand alone (skippable). |
| VAST | VAST 2.0–4.2 or VMAP 1.0; HTTPS only; ≤ 5 wrapper hops; must include an MP4 MediaFile ≤ 1080p; **linear ads > 6 s must set `skipoffset` ≤ 5 s** (non-skippable only ≤ 6 s), otherwise the app discards the ad; VPAID/SIMID not supported (no interactive JS on TV); click-through is not opened on TV. |
| Pause card | 640×640 px PNG/WebP ≤ 200 KB. |

### 4.6 Campaign admin (web)

- New page `web/public/contributor/admin/campaigns/` (same Google sign-in, `isAdmin()` claim).
  Create/edit campaign, upload creatives (Storage path `ads/creatives/{campaignId}/…`, admin
  write only), preview at 1920×1080 with safe-area overlay, schedule, targeting, caps, priority,
  weight, pause/resume, **Publish** (writes the decision JSON).
- Firestore: `ad_campaigns/{id}`, `ad_stats/{campaignId_yyyymmdd}` (aggregates only). Rules:
  read/write admin only; the app never reads Firestore for ads (it reads the published JSON).
- Brand report: per campaign and day, impressions, viewable impressions, quartiles, completion
  rate, skips, QR scans, unique-device estimate (HyperLogLog on a daily-rotating id, §6.3). Export
  CSV; a read-only share link (signed, expiring) for the brand.
- Out of scope: invoicing, IO signing, brand self-serve accounts, credit-card payments.

## 5. Fallback chain and rules

### 5.1 Fallback per slot

`brand campaign (direct or VAST)` → `Shopee affiliate offer` → `house` → `nothing (collapse)`.

**Shopee affiliate offers on TV (decision):** they are the second tier, in the **Home carousel
and the in-player corner banner only** (never pre-roll or the pause card).
- Source: the existing `IAdsRepository.getAdsList()` (remote config `ads_url`), no new backend.
- Rendering: `bannerImage` when it is landscape; otherwise a composed card (product image,
  title, price/sale price, and a QR of `qrCodeLink` or `productLink` wrapped in our short link,
  §7.3). OK opens the QR dialog; nothing opens a browser on TV.
- Labelled "Quảng cáo / Ad" like every other ad; same 24 h rule and caps as brand banners.
- Policy: an affiliate offer is advertising of a third party's goods, not payment for our app,
  so it is allowed in Play builds (§6.1).
- At most 2 Shopee slides in the carousel at a time, so house cards keep a place.
- Remote config `tv_shopee_enabled` switches the tier off.

House content per build (from remote config `tv_house_mode`, never hard-coded; the QR payload is
the URL in the decision file `house.supportQr.url` or in remote config `support_page_url`):

| Build | House card shown |
|---|---|
| **Google Play** (`play` flavour, default) | `play_billing`: "Ủng hộ nhà phát triển" card; OK opens a Play Billing purchase sheet for a one-time **"Support the developer"** product (consumable, several price tiers). Plus non-payment house cards: "Hướng dẫn định dạng", "Danh sách cộng đồng", "Đánh giá ứng dụng". **No bank, MoMo, crypto or donation-page QR.** |
| Not distributed by Play (`direct` flavour: direct APK, other stores whose rules allow it) | `site_qr`: a QR to `/support/?utm_source=tv…` ("Quét để ủng hộ TS IPTV") or `bank_qr`: the VietQR image URL from remote config. |
| Any build, `tv_house_mode = off` | Nothing (slot collapses). |

Pre-roll has no house fallback.

### 5.2 Rules

| # | Rule | Decision |
|---|---|---|
| T1 | 24 h ad-free start | **Applies to TV** (same clock, `ads_first_use_ms`, `PackageManager.firstInstallTime`). Includes house cards. |
| T2 | No ads while navigating settings | No ad UI while `TvSettingsDialog`, language, login, import, addon manager, device-limit or receive screens are open; the carousel pauses and is not drawn behind modal dialogs. |
| T3 | Session caps | In-player banner ≤ 3 per session; pre-roll ≤ 2 per session; carousel slide impressions are not capped (it is a page element) but each **brand creative** counts against its campaign caps once per Home visit. |
| T4 | Daily caps | Pre-roll ≤ 4/day/device; player banner ≤ 8/day; per campaign `perDevicePerDay`. Stored in `KeyValueStorage` (counters per local date). |
| T5 | Live channels and sports | **Decided default:** no pre-roll on any live channel (pre-roll is VOD only, §3.3); the in-player corner banner is allowed on live channels with the §3.2 rules and the §6.4 content gate. **Q3** only asks whether to also suppress the corner banner on sports groups. |
| T6 | Kids / sensitive content | No targeting on content. Campaign categories restricted by §6.2 never run. |
| T7 | Kill switch | `tv_ads_enabled=false` hides everything within one config refresh; per-format switches and `tv_shopee_enabled` as in §4.3/§5.1. |
| T8 | Entitlement | **Defined by `feature/subscriptions`** (`docs/prd-subscriptions.md`). Source of truth: `users/{uid}/entitlements/current` (written only by the billing server after Play verification, read-only for clients), merged with this device's Play purchases and a short local cache by `EntitlementRepository` (common code). `TvAdsGate` reads `EntitlementRepository.state` (`StateFlow<Entitlement?>`, null until known) and shows **no TV ads at all** while `entitlement.noAds` is true (the "No ads" and "Unlimited" plans), and nothing while the value is still null, like the phone `AdsGate`. There is no separate `adFree` field. The one-time "Support" product **does not** remove ads unless Q6 decides otherwise. **Billing note:** `PlayBillingClient` handles only subscriptions today (`ProductType.SUBS`, acknowledge). A one-time consumable "Support" product needs its own handling there: `ProductType.INAPP` product details, `queryPurchasesAsync(INAPP)`, and `consumeAsync` (not acknowledge) after the thank-you, so a tip can be bought again; it must never map to an entitlement. |

## 6. Policy and legal

### 6.1 Google Play Payments policy (checked 2026-10-10)

Source: Play Console Help, "Payments" policy (answer 9858738), and the US external-links
programme (answer 15582165).

What the policy says (paraphrased, quotes in quotation marks):
- Play-distributed apps "requiring or accepting payment for access to in-app features or services,
  including any app functionality, digital content or goods must use Google Play's billing system."
- Play Billing must **not** be used for physical goods/services, and the exceptions include
  "peer-to-peer payments, online auctions, and **tax exempt donations**" (i.e. donations to
  registered charities).
- Anti-steering: apps "may not lead users to a payment method other than Google Play's billing
  system" through the store listing, in-app promotions, or "**in-app webviews, buttons, links,
  messaging, advertisements, or other calls to action**".
- Google has enforced this against **donation buttons to the developer** (e.g. the WireGuard app
  was removed in 2020 until it removed its donation link); donating to the developer is treated
  as paying for the app, not as a tax-exempt donation.
- **United States only (verify):** reportedly, since 29 Oct 2025 (Epic v. Google injunction)
  developers may link to external payment for US users through Google's external content links /
  alternative billing programmes, with reporting and service fees from 1 Oct 2026 (Play Console
  Help answer 15582165 and press reports; dates, scope and fees to be verified before relying on
  them). Either way it does **not** cover Vietnam or other countries, our main audience.

Conclusions for TS IPTV (Play builds, phone and TV):

| Item | Verdict |
|---|---|
| In-app QR or button "Donate via bank/MoMo/ZaloPay/crypto/PayPal/Ko-fi" | **Not allowed** (steering to non-Play payment for the developer). High risk: app removal. |
| In-app QR to our website `/support/` page labelled "Support us" | **Not allowed in substance** – it is a call to action that leads to non-Play payment; the indirection through a web page does not change that. |
| In-app QR/link to the website for **non-payment** content (guides, privacy, community playlists), where the site happens to have a support page in its footer | Low risk. Keep the in-app copy non-transactional ("Xem hướng dẫn trên điện thoại"), never "donate". |
| Play Billing in-app product "Support the developer" (tip) | **Allowed** (it *is* Play Billing). Google takes 15 % (first US$1 M/year under the 15 % tier). |
| Donations to a registered **charity** via its own page | Allowed (tax-exempt donation exception) – not our case. |
| The website `/support/` page itself, outside the app (linked from the site, social, email) | Allowed: the policy governs the app and its listing, not our website. Do **not** put the support URL in the Play listing text. |
| Brand campaigns whose QR goes to a brand's own shop | Allowed (it is advertising of a third party's goods, not payment for our app); standard ads policy applies. |
| Non-Play builds (direct APK) | Play policy does not apply; bank/donate QR allowed. Keep the code path in the `direct` build flavour only (§8 M2) **and** behind remote config; the `play` flavour does not contain it. |

**Recommendation:** in Play builds, the TV house slot uses the **Play Billing "Support the
developer" product** (with 3 tiers, e.g. 22 000 / 55 000 / 110 000 VND) plus non-payment house
cards. Show bank/MoMo/crypto QRs only on the website and in non-Play builds. Reconsider external
links for US users only if US traffic grows (needs enrolment and fees; not worth it now).

### 6.2 Vietnamese advertising law (high level – **needs legal review**)

- Framework: Law on Advertising 16/2012/QH13 and its 2025 amendment (effective 1 Jan 2026, verify
  the exact text), Decree 181/2013/ND-CP, Decree 70/2021/ND-CP (cross-border advertising on the
  internet, foreign advertisers through a VN advertising service provider).
- Ads must be **clearly identifiable** as advertising: our "Quảng cáo" label on every creative.
- Online video ads: viewers must be able to skip/close; the amended law is reported to require a
  skip option within **5 s** for video ads (verify). Our pre-roll is skippable at 5 s.
- **Banned** categories (do not accept): tobacco; alcohol ≥ 15 % ABV; prescription medicines;
  breast-milk substitutes for children under 24 months; weapons; gambling/betting (also a Play
  policy issue); anything illegal in VN.
- **Content confirmation/licence needed before running** (brand must provide the certificate):
  medicines (non-prescription), cosmetics, functional/health supplements, medical equipment and
  services, pesticides, veterinary products, fertilisers, financial/securities/insurance in some
  cases. The admin console stores the certificate number and expiry per campaign and blocks
  publishing without it.
- Comparative claims, "best/number one" claims need proof; prices must include tax; Vietnamese
  language required (foreign language may accompany).
- Keep IO terms that the brand warrants its creative is lawful and indemnifies us.

### 6.3 Privacy and consent

- **No PII** in decisions or beacons: no account id, no email, no Advertising ID, no IP stored
  (the endpoint drops it after rate limiting), no channel names or stream URLs (same rule as
  analytics).
- Frequency counting is local on the device. For brand reach estimates, beacons carry a
  **daily-rotating random id** (regenerated every local midnight, never linked to the account),
  aggregated into a HyperLogLog sketch; raw ids are not stored.
- Targeting uses only country (store/locale), app language and local hour.
- Consent: contextual, non-personalised ads with no tracking id do not need GDPR consent for
  ad personalisation, but **IMA/VAST tags from third parties may set cookies or collect data**:
  pass `gdpr`/`gdpr_consent`/`us_privacy`/`gpp` macros from UMP where available and do not run
  third-party VAST for users in the EEA/UK without consent. Privacy policy (`/privacy/`) gets a
  "TV ads" section; Play Data safety: "App interactions" collected for advertising, not shared
  (own tracking) – shared if VAST tags run.

### 6.4 Brand safety and copyright around user-supplied streams (**legal item**)

TS IPTV plays streams that **users add**; we do not know whether a given channel or film is
licensed. Showing ads **next to or before** that content means we (and the brand) earn money
from it. Risks:
- **Copyright:** rights holders may argue that monetising unlicensed streams is contributory or
  secondary infringement, or a reason for takedowns of the app (Play's IP policy, Vietnamese IP
  law and Decree 70/2021 on cross-border content). This is stronger for ads tied to playback
  (pre-roll, in-player) than for the Home screen.
- **Brand safety:** a brand's ad could run before pirated sport, adult or extremist content
  that a user added; brands and agencies will ask for guarantees we cannot give for user
  content.

Decision for this PRD, **pending legal review (Q8)**:
1. The **Home carousel** is not tied to any stream and can launch in phase 1.
2. **In-player banner and pre-roll** run only on an **allowlist** of content: TS IPTV Sources and
   community playlists whose publisher is approved in the contributor programme and declared
   the rights (`public_playlists` with an approved, rights-declared flag), plus channels
   explicitly allowlisted by the admin. On any other stream these slots stay empty. Legal may
   widen or narrow this.
3. No contextual targeting by channel or title; brands buy placements, not content.
4. Brand IOs state that ads may appear in an app that plays user-supplied content, and that we
   offer the allowlist as the only "in-content" inventory.

### 6.5 Creative and QR-target moderation, takedown

- **Review before publish:** every creative and every QR/action target is approved by an admin
  in the console (checklist: label, banned categories §6.2, licence certificate where needed,
  no fake UI, safe areas, landing page matches the advertiser, HTTPS). The publish job refuses
  unapproved items. Edits to an approved creative reset it to "pending".
- **QR targets** always go through our short link (§7.3), so a target can be changed or killed
  without republishing the app: a disabled link answers a neutral "This offer has ended" page.
  A daily job checks each live target with the Google Safe Browsing API and for redirects to a
  different domain; a hit disables the link and pauses the campaign, and alerts the admin.
- **Reports and takedown:** a "Report an ad" link on the website (`/support/` footer area or the
  privacy page) and the support email; target **24 h** to review, immediate kill via the
  per-creative switch (republish of the decision file, picked up within one config refresh,
  ≤ 5 min CDN cache) or the global kill switch. Takedowns and their reasons are logged in
  Firestore (`ad_moderation_log`, admin only).

## 7. Measurement

### 7.1 Events (beacon `POST /ads/events`, batched every 60 s or on pause, max 50 per batch)

`{v, cid, crid, slot, ev, t, appVer, country, lang, dayId}` where `ev` ∈ `served`, `impression`,
`viewable`, `click` (OK on a carousel action), `qr_open` (QR dialog shown), `start`, `q1`, `mid`,
`q3`, `complete`, `skip`, `error`. Queued offline (max 500, 7 days).

### 7.2 Viewability on TV

- Banner: **impression** when the creative is drawn ≥ 50 % in the visible window; **viewable**
  after **2 s continuous** (carousel slide must stay ≥ 2 s; in-player banner after 2 s).
- Video: impression at the first frame; **viewable** when the ad plays for **2 s continuously**
  while it fills the screen (MRC-style video rule: ≥ 50 % of pixels for 2 continuous seconds,
  always met for a full-screen pre-roll in a resumed app). The audio state is **not** a
  condition; a muted device is recorded as a dimension (`muted=true`) for brand reports.
  Quartiles from the player position (own creatives) or IMA events (VAST, which also fires the
  brand's own trackers).
- No impression when the app is in the background or the screen saver is on.

### 7.3 QR scans

- Every QR encodes **our short link** `https://<site>/r/<code>` (Firebase Hosting rewrite to a
  function, or the bot server) that logs `scan` (no IP kept) and redirects 302 to the brand URL
  with `utm_source=tsiptv_tv&utm_medium=<slot>&utm_campaign=<cid>&utm_content=<crid>`.
- House QR (non-Play builds) points to `/support/?utm_source=tv&utm_medium=house`.

### 7.4 Brand report

Per campaign/day/slot: served, impressions, viewable rate, quartiles, VCR, skips, QR scans, scan
rate per 1 000 impressions, estimated unique devices. CSV export and an expiring share link.

### 7.5 Success metrics, guardrails, rollout and A/B

Success metrics (TV only, per week):
- Ad revenue (direct-sold + Shopee commissions) and revenue per 1 000 TV sessions.
- Fill rate per slot (brand / Shopee / house), viewable rate, video completion rate.
- Support purchases (Play Billing tips) or support-page visits from TV (`utm_source=tv`).

Guardrails (compared with the holdout; a regression beyond the threshold pauses the rollout):
- TV D1 / D7 retention: no drop > **2 %** relative.
- Watch time per TV session and plays per session: no drop > **3 %**.
- Playback start time (p50/p90): no increase > **200 ms** outside pre-roll; content start after a
  failed ad ≤ 2 s (3 s VAST).
- Crash-free and ANR-free sessions on TV: no drop > **0.1 pp**.
- Uninstalls within 7 days and 1–2★ ratings mentioning ads: no increase > **10 %** relative.

Staged rollout and A/B:
- Bucketing on the device: `bucket = sha256(installationId + salt) mod 100`, with the salt and the
  percentage in remote config (`tv_ads_rollout_pct`, `tv_ads_salt`); no server needed.
- Steps: internal testers → **5 %** → 25 % → 50 % → 100 %, each step ≥ 7 days and only when the
  guardrails hold.
- A **10 % holdout** without TV ads for at least 4 weeks after 100 %, to measure the guardrails.
- A/B tests through the same buckets (e.g. carousel auto-advance 8 s vs 12 s; in-player banner
  every 20 vs 30 min). The bucket and variant are attached to the existing analytics as user
  properties (no PII).

## 8. Implementation plan

| Milestone | Scope | Estimate |
|---|---|---|
| **M0 – Decisions** | Answer the open questions (§11); legal review of §6.2 and §6.4; decide Play Billing tiers; align the entitlement with `feature/subscriptions` (T8). | 2–3 days (non-dev) |
| **M1 – Core + house/Shopee carousel** (phase 1) | `TvAdsGate` (24 h, kill switches, flavour, entitlement, rollout bucket); `TvAdRepository` (decision JSON, ES256 signature check, ETag cache); `TvAdPolicy` (targeting, schedule, caps, weighted pick) with unit tests; Home carousel UI with focus rules and the height guardrail; Shopee tier (§5.1); house cards (guides, playlists, rate app); strings in 7 locales. | 7–9 dev days |
| **M2 – Build flavours + Support product** (phase 1) | Gradle product flavours **`play`** and **`direct`** (dimension `distribution`; the donate/bank QR code path compiled only into `direct`); CI: build, test and archive both flavours, a check that the `play` APK/AAB contains no donation strings or QR assets; **signing:** `play` keeps Play App Signing (upload key in CI secrets as today); `direct` is signed by CI with our own release key using **APK Signature Scheme v2 + v3** (`apksigner`; v3 allows key rotation later), key in CI secrets, never in the repo. The `direct` APK has the same `applicationId`, so it cannot update over a Play install (different signer); the download page says so. Play Billing Library in `play` only, `SupportPurchase` expect/actual (no-op elsewhere), 3 consumable products, thank-you message; house card OK opens it. `direct`: site/bank QR card from remote config. | **6–9 days** (was 3–4) |
| **M3 – In-player corner banner + tracking** (phase 1) | Corner banner rules (§3.2) with the §6.4 content allowlist, `TvAdTracker` beacons + offline queue, `/ads/events` endpoint (bot server or Cloud Function), short-link redirect `/r/<code>` with Safe Browsing checks. | 6–8 days |
| **M4 – Campaign admin + moderation** (phase 1) | Admin page, Storage upload, preview with safe areas, approval workflow and moderation log (§6.5), publish job writing and signing the JSON, Firestore rules + emulator tests, daily aggregation, brand CSV. | 7–9 days |
| **M5 – Pre-roll (own MP4 + IMA VAST)** (phase 2) | Single-player ad insertion with the sequential fallback (§3.3), skip at ≤ 5 s, VAST `skipoffset` check, IMA focus and Back handling, caps, timeouts, quartiles, consent macros; low-end box test. | 8–11 days |
| **M6 – Pause card** (phase 3, optional) | §3.4. | 1–2 days |
| **M7 – GAM** (phase 3, optional) | Only if the traffic threshold of §4.2 is met. | 5+ days |

Rollout of every milestone follows §7.5 (staged, with holdout).

## 9. Acceptance criteria

- **AC-TA1** On TV, a fresh install shows no ad of any kind (brand or house) and fetches no ad
  decision for 24 h after first use; an install older than 24 h shows ads at once.
- **AC-TA2** The Home carousel is the first row of the content pane, never takes the initial
  focus, is reachable with ▲ from the first grid row, pauses auto-advance while focused, moves
  slides with ◀/▶, and OK runs only the actions of §3.5.
- **AC-TA3** Back closes any ad dialog (QR dialog, pause card) and, after 5 s, any pre-roll;
  before 5 s Back leaves the player. No ad state ever ignores Back.
- **AC-TA4** The in-player banner is never focusable, sits top-right inside the 5 % safe area, is
  hidden while the channel banner, channel list, options dialog, buffering spinner or an error is
  visible, and respects the timing and caps of §3.2.
- **AC-TA5** No ad UI is drawn while Settings or any modal screen of T2 is open.
- **AC-TA6** Selection follows schedule, targeting, caps, priority and weight; a unit test covers
  ties, expired campaigns, caps at the limit, time zones and an empty decision.
- **AC-TA7** Fallback: brand → Shopee (carousel and corner banner only) → house → collapse. In a Play build, no bank/MoMo/crypto/donation
  QR or link is ever shown (test: a decision with `bank_qr` is ignored in the Play flavour).
- **AC-TA8** The house QR URL and mode come from remote config/the decision file; no payment
  detail is compiled into the app.
- **AC-TA9** Pre-roll runs only before VOD (never live) and only on §6.4 allowlisted content;
  skippable at ≤ 5 s with OK; content starts within 2 s (3 s VAST) if the ad fails; caps of
  T3/T4 hold; quartile events fire once each.
- **AC-TA10** Beacons contain no account id, email, advertising id, channel name or stream URL;
  the endpoint stores no IP.
- **AC-TA11** `tv_ads_enabled=false` hides all TV ads after the next config refresh.
- **AC-TA12** Admin: only admins can read/write campaigns; publishing is blocked for restricted
  categories without a certificate; the public decision JSON contains no internal notes.
- **AC-TA13** Strings ("Quảng cáo", "Quét bằng điện thoại", "Bỏ qua sau %d giây", "Ủng hộ nhà phát
  triển") exist in all 7 locales.
- **AC-TA14** A VAST linear ad > 6 s without `skipoffset` ≤ 5 s is discarded and content starts;
  the IMA skip button takes D-pad focus (or OK skips through the fallback handler); no
  click-through opens on TV.
- **AC-TA15** On a device with one decoder instance (or after a decoder init failure) the ad and
  content play through one player, sequentially, without a playback error.
- **AC-TA16** The carousel is ≤ 22 % of the screen height and two full rows of channel cards stay
  visible below it at 720p and 1080p, also with font scale 1.3.
- **AC-TA17** The app ignores an unsigned, wrongly signed, expired or oversize decision file and
  keeps the last good one.
- **AC-TA18** In-player and pre-roll slots stay empty on streams outside the §6.4 allowlist.
- **AC-TA19** Rollout: only devices in the configured bucket percentage see TV ads; the holdout
  sees none; the bucket of a device is stable across launches.
- **AC-TA20** The `play` flavour contains no donation strings, payment QR assets or bank/wallet
  code path (CI check); the `direct` flavour is signed with APK Signature Scheme v2 + v3.
- **AC-TA21** A disabled short link stops redirecting within 5 minutes; a Safe Browsing hit
  disables the link and pauses the campaign.

## 10. Test plan

- **Unit (`commonTest`):** `TvAdPolicy` (targeting, schedule edges, caps per session/day, date
  rollover, weighted pick with a seeded RNG, priority), `TvAdsGate` (24 h, kill switches, Play vs
  non-Play house mode, rollout bucket, entitlement), decision JSON parsing and ES256 signature
  (unknown fields, oversize, expired, bad signature, key rotation), VAST skip-offset rule, tracker queue
  (batching, offline, max size).
- **UI (Compose tests on desktop where possible):** carousel focus order and pause-on-focus;
  Back handling; banner hidden when the channel banner is visible.
- **Manual on an Android TV emulator (720p and 1080p), one low-end box (single decoder, e.g.
  Amlogic S905-class, 1–2 GB RAM) and one current Google TV device:** D-pad walkthrough, safe
  areas, subtitle overlap, pre-roll skip, VAST sample tags (Google IMA sample tags), slow network
  and offline, process death during an ad, "remove animations" setting, sequential vs parallel
  pre-roll path, IMA skip-button focus, carousel height guardrail, Shopee slides.
- **Rules:** Firestore emulator tests for `ad_campaigns`, `ad_stats` and `ad_moderation_log`.
- **Policy check before release:** the `play` build contains no donation copy or payment QR (CI
  check of the AAB, plus the decision used in QA).
- **Rollout:** verify bucketing, holdout and the guardrail dashboard (§7.5) before the 5 % step.

## 11. Open questions for the user

1. **Q1 – Play Billing "Support" product:** OK to add Play Billing on Android (phone and TV) with
   3 tip tiers? Which prices? Does supporting remove ads (Q6)?
2. **Q2 – Non-Play distribution:** do you distribute a direct APK (website/Telegram)? If yes, the
   bank/MoMo QR house card can ship there.
3. **Q3 – Live sports:** pre-roll is never on live channels (decided, T5). Should the in-player
   corner banner also skip channels in "Sports" groups?
4. **Q4 – Brand sales:** who sells and signs brands (you, an agency)? Minimum campaign size and
   pricing model (CPM vs flat per week)?
5. **Q5 – Backend:** host the events endpoint and short links on the telegram-bot server or on
   Firebase Cloud Functions (Blaze plan)?
6. **Q6 – Ad-free:** the no-ads plan comes from `feature/subscriptions` (T8). Should the one-time
   support purchase also remove TV ads (and for how long), or only the subscription?
7. **Q7 – 24 h start on TV:** confirm it applies to TV (recommended yes).
8. **Q8 – Legal:** who reviews the VN advertising compliance (§6.2), the brand-safety and
   copyright item (§6.4: ads around user-supplied streams, the allowlist) and the brand IO template?
9. **Q9 – Direct APK signing:** OK to sign the `direct` APK with our own key (users cannot switch
   between the Play and direct installs without uninstalling)?
