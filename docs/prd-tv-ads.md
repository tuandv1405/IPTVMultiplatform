# PRD: custom ads on Android TV (house, direct-sold and VAST)

Status: **draft for review** · Owner: PO · Date: 2026-10-10 · Code: none yet (plan only)
Related: `docs/prd-admob.md` (phone ads; TV has **no** AdMob), `docs/prd-tv-cast-and-sync.md`
(rewarded ads, entitlements), web support page `/support/` (`web/public/support/`).

## 0. Summary of the recommendations

| Topic | Recommendation |
|---|---|
| Ad source | **Phase 1: our own lightweight campaign system** (house ads + direct-sold brand campaigns), served as one JSON "ad decision" from the existing HTTP config host or Firestore, with creatives on a CDN. A **VAST tag** is one creative type from the start, played through the **Google IMA SDK** (ExoPlayer/Media3 IMA extension). **Phase 2: Google Ad Manager** as one more source behind the same `TvAdSource` interface, only when inventory justifies it. |
| Formats | Home carousel banner (phase 1), in-player corner banner (phase 1), pre-roll video (phase 2, brand campaigns only, skippable after 5 s), pause-screen ad (phase 3, optional). |
| No brand campaign | House fallback. **Play-distributed builds must not show a bank/MoMo/crypto donation QR** (Play Payments policy, §6.1). They show either a **Play Billing "Support the developer" product** (recommended) or a non-payment house card (guides, community playlists, "rate the app"). The bank/donate QR is allowed only in builds that are not distributed through Google Play (e.g. a direct APK), switched by remote config + a build flag. |
| 24 h ad-free start | **Applies to TV too** (same `AdsPolicy.isInAdFreePeriod`, same `ads_first_use_ms`). |
| Live sports | Open question Q3; default: no pre-roll and no in-player banner on channels the user marks "no ads" (none in phase 1 – see Q3). |
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
  (spreadsheet + bank transfer) in phase 1; listed in §9 as out of scope.
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
Compose `dp` on a 1080p TV ≈ 2 px).

### 3.1 Home carousel banner (phase 1)

- **Where:** first row of the TV Home content pane (above the channel grid and above
  `SourceHomeContent` sections), full width of the pane, 16:5 aspect (e.g. 1600×500 px creative,
  shown about 800×250 dp minus the rail). Not shown in Discover, Settings or the addon manager.
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

- **Where:** **top-right corner**, 384×216 dp max (a 16:9 or 16:5 creative, or a QR card 216×216 dp),
  inside the TV title-safe area (5 % margin: 48 dp from the top and right edges at 960×540 dp).
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

- **Where:** before VOD playback (TS IPTV Source movies/episodes, addon streams) and, optionally,
  before a **live** channel when it is opened from Home (not on zap, never on ▲/▼ zapping).
- **Brand campaigns only.** No house video pre-roll (a house message as pre-roll is only friction).
- **Length:** 6–30 s. **Skippable after 5 s** (the "Skip" button appears at 5 s; mandatory for
  ads over 5 s; see the VN rule in §6.2). Non-skippable only for ≤ 6 s bumpers, opt-in per
  campaign.
- **Skip with OK**: the Skip button is the only focusable element and takes focus when it
  appears (this is an expected focus change, not a steal: nothing else is focusable on screen).
  **Back always ends the ad** after 5 s; before 5 s Back leaves the player (cancels the open of
  the content), never traps the user.
- **Frequency caps:** max **1 pre-roll per 30 min** of watching per device, max **4 per day**, max
  per campaign per device per day from the campaign (default 2). No pre-roll in the first **3**
  content opens of a session (the user is still choosing).
- **Timeout:** if the ad is not ready within **2 s** (VAST: 3 s including the wrapper chain), skip
  it and start the content. A failed ad never delays content more than that.
- Playback: a separate Media3 `ExoPlayer` instance for our own MP4/HLS creatives; the **IMA SDK**
  (`media3-exoplayer-ima` `ImaAdsLoader`) for VAST/VMAP tags. The content player is prepared in
  parallel so content starts at once after the ad.

### 3.4 Pause-screen ad (phase 3, optional)

- When the user pauses VOD (not live) for **≥ 3 s**, a card (max 30 % of the width, right side,
  vertically centred) shows a brand banner or house QR. Disappears on any key. Not focusable.
  Never on live channels (pause on live is rare and usually means "phone call").

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

1. **Phase 1 – own system, banners first.** House ads + direct-sold banner campaigns from our
   system. Creative types: `image`, `qr` (generated from a URL at runtime or a static image),
   `video` (MP4/HLS we host) and **`vast`** (a tag URL). The schema has `vast` from day one even if
   pre-roll ships in phase 2, so brands can be sold "bring your VAST tag".
2. **Phase 2 – pre-roll video** with our MP4 creatives **and** IMA for `vast` creatives; quartile
   tracking. Same decision endpoint.
3. **Phase 3 – Google Ad Manager** only if monthly TV impressions make it worthwhile
   (rule of thumb: > 1 M video starts/month). GAM becomes one `TvAdSource` with a lower priority
   than direct-sold campaigns; house ads stay the final fallback.

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
  offline from cache, no per-request PII. Size cap 256 KB; signed with a version + `expiresAt`.
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

Selection: filter by schedule, targeting (country from the store/locale, never IP geolocation on
device; app language; local hour), caps, slot → highest `priority` wins; within a priority,
weighted random by `weight`. House entries are priority 0.

### 4.5 Creative spec for brands (TV, 1080p)

| Creative | Spec |
|---|---|
| Home carousel image | 1600×500 px (16:5), WebP or PNG/JPEG, ≤ 300 KB, sRGB. Keep text and logos inside the **central 1440×420** (safe area); no text < 28 px; no fake buttons. "Quảng cáo/Ad" label is added by the app (top-left 160×48 px reserved). |
| In-player corner | 768×432 px (16:9) or 768×240 (16:5), WebP/PNG, ≤ 200 KB; or a QR card (we render the QR; brand gives the URL + a 1-line caption ≤ 40 characters). |
| QR target | HTTPS URL; we wrap it in our short link for counting (§7.3). |
| Pre-roll video (our hosting) | MP4 (H.264 High@4.1, AAC-LC 48 kHz stereo) **and** optionally HLS; 1920×1080 or 1280×720, 25/30 fps, CBR/VBR 6–8 Mbps (1080p) / 3–4 Mbps (720p); loudness **−23 LUFS ±1** (EBU R128), true peak ≤ −1 dBTP; 6, 15, 20 or 30 s; file ≤ 30 MB; no letterbox; key content inside the 90 % title-safe area; first 5 s must stand alone (skippable). |
| VAST | VAST 2.0–4.2 or VMAP 1.0; HTTPS only; ≤ 5 wrapper hops; must include an MP4 MediaFile ≤ 1080p; VPAID/SIMID not supported (no interactive JS on TV). |
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

`brand campaign (direct or VAST)` → `house` → `nothing (collapse)`.

House content per build (from remote config `tv_house_mode`, never hard-coded; the QR payload is
the URL in the decision file `house.supportQr.url` or in remote config `support_page_url`):

| Build | House card shown |
|---|---|
| **Google Play** (default) | `play_billing`: "Ủng hộ nhà phát triển" card; OK opens a Play Billing purchase sheet for a one-time **"Support the developer"** product (consumable, several price tiers). Plus non-payment house cards: "Hướng dẫn định dạng", "Danh sách cộng đồng", "Đánh giá ứng dụng". **No bank, MoMo, crypto or donation-page QR.** |
| Not distributed by Play (direct APK, other stores whose rules allow it) | `site_qr`: a QR to `/support/?utm_source=tv…` ("Quét để ủng hộ TS IPTV") or `bank_qr`: the VietQR image URL from remote config. |
| Any build, `tv_house_mode = off` | Nothing (slot collapses). |

Pre-roll has no house fallback.

### 5.2 Rules

| # | Rule | Decision |
|---|---|---|
| T1 | 24 h ad-free start | **Applies to TV** (same clock, `ads_first_use_ms`, `PackageManager.firstInstallTime`). Includes house cards. |
| T2 | No ads while navigating settings | No ad UI while `TvSettingsDialog`, language, login, import, addon manager, device-limit or receive screens are open; the carousel pauses and is not drawn behind modal dialogs. |
| T3 | Session caps | In-player banner ≤ 3 per session; pre-roll ≤ 2 per session; carousel slide impressions are not capped (it is a page element) but each **brand creative** counts against its campaign caps once per Home visit. |
| T4 | Daily caps | Pre-roll ≤ 4/day/device; player banner ≤ 8/day; per campaign `perDevicePerDay`. Stored in `KeyValueStorage` (counters per local date). |
| T5 | Live sports | **Open question Q3.** Proposal: no pre-roll on live channels in phase 2 at all (only VOD); in-player banner on live allowed with the rules of §3.2. |
| T6 | Kids / sensitive content | No targeting on content. Campaign categories restricted by §6.2 never run. |
| T7 | Kill switch | `tv_ads_enabled=false` hides everything within one config refresh. |
| T8 | Entitlement | A paid entitlement (`prd-tv-cast-and-sync.md` §8) can remove ads (`Entitlement.adFree`); the Play Billing "Support" product **does not** remove ads unless we decide so (Q6). |

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
- **United States only:** since 29 Oct 2025 (Epic v. Google injunction) developers may link to
  external payment for US users through Google's external content links / alternative billing
  programmes, with reporting and service fees from 1 Oct 2026. This does **not** cover Vietnam or
  other countries, which are our main audience.

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
| Non-Play builds (direct APK) | Play policy does not apply; bank/donate QR allowed. Keep the code path behind a build flag `distribution != play` **and** remote config. |

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

## 7. Measurement

### 7.1 Events (beacon `POST /ads/events`, batched every 60 s or on pause, max 50 per batch)

`{v, cid, crid, slot, ev, t, appVer, country, lang, dayId}` where `ev` ∈ `served`, `impression`,
`viewable`, `click` (OK on a carousel action), `qr_open` (QR dialog shown), `start`, `q1`, `mid`,
`q3`, `complete`, `skip`, `error`. Queued offline (max 500, 7 days).

### 7.2 Viewability on TV

- Banner: **impression** when the creative is drawn ≥ 50 % in the visible window; **viewable**
  after **2 s continuous** (carousel slide must stay ≥ 2 s; in-player banner after 2 s).
- Video: impression at the first frame; viewable at 2 s continuous play with sound on (MRC-style
  video standard); quartiles from the player position (own creatives) or IMA events (VAST, which
  also fires the brand's own trackers).
- No impression when the app is in the background or the screen saver is on.

### 7.3 QR scans

- Every QR encodes **our short link** `https://<site>/r/<code>` (Firebase Hosting rewrite to a
  function, or the bot server) that logs `scan` (no IP kept) and redirects 302 to the brand URL
  with `utm_source=tsiptv_tv&utm_medium=<slot>&utm_campaign=<cid>&utm_content=<crid>`.
- House QR (non-Play builds) points to `/support/?utm_source=tv&utm_medium=house`.

### 7.4 Brand report

Per campaign/day/slot: served, impressions, viewable rate, quartiles, VCR, skips, QR scans, scan
rate per 1 000 impressions, estimated unique devices. CSV export and an expiring share link.

## 8. Implementation plan

| Milestone | Scope | Estimate |
|---|---|---|
| **M0 – Decisions** | Answer the open questions (§10); legal review of §6.2; decide Play Billing tiers. | 2–3 days (non-dev) |
| **M1 – Core + house carousel** | `TvAdsGate` (24 h, kill switches, build distribution flag); `TvAdRepository` (decision JSON, ETag cache); `TvAdPolicy` (targeting, schedule, caps, weighted pick) with unit tests; Home carousel UI with focus rules; house cards (guides, playlists, rate app); strings in 7 locales. | 6–8 dev days |
| **M2 – Support product (Play Billing)** | Play Billing Library in Android only, `SupportPurchase` expect/actual (no-op elsewhere), 3 consumable products, thank-you toast; house card OK opens it. Non-Play flavour: site/bank QR card. | 3–4 days |
| **M3 – In-player corner banner + tracking** | Corner banner rules (§3.2), `TvAdTracker` beacons + offline queue, `/ads/events` endpoint (bot server or Cloud Function), short-link redirect `/r/<code>`. | 5–7 days |
| **M4 – Campaign admin** | Admin page, Storage upload, preview with safe areas, publish job writing the JSON, Firestore rules + emulator tests, daily aggregation, brand CSV. | 6–8 days |
| **M5 – Pre-roll (own MP4 + IMA VAST)** | Ad player before content, skip at 5 s, caps, timeouts, IMA `ImaAdsLoader` for `vast`, quartiles, consent macros. | 7–10 days |
| **M6 – Pause card (optional)** | §3.4. | 1–2 days |
| **M7 – GAM (phase 3)** | Only if the traffic threshold is met. | 5+ days |

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
- **AC-TA7** Fallback: brand → house → collapse. In a Play build, no bank/MoMo/crypto/donation
  QR or link is ever shown (test: a decision with `bank_qr` is ignored in the Play flavour).
- **AC-TA8** The house QR URL and mode come from remote config/the decision file; no payment
  detail is compiled into the app.
- **AC-TA9** Pre-roll: skippable at 5 s with OK; content starts within 2 s (3 s VAST) if the ad
  fails; caps of T3/T4 hold; quartile events fire once each.
- **AC-TA10** Beacons contain no account id, email, advertising id, channel name or stream URL;
  the endpoint stores no IP.
- **AC-TA11** `tv_ads_enabled=false` hides all TV ads after the next config refresh.
- **AC-TA12** Admin: only admins can read/write campaigns; publishing is blocked for restricted
  categories without a certificate; the public decision JSON contains no internal notes.
- **AC-TA13** Strings ("Quảng cáo", "Quét bằng điện thoại", "Bỏ qua sau %d giây", "Ủng hộ nhà phát
  triển") exist in all 7 locales.

## 10. Test plan

- **Unit (`commonTest`):** `TvAdPolicy` (targeting, schedule edges, caps per session/day, date
  rollover, weighted pick with a seeded RNG, priority), `TvAdsGate` (24 h, kill switches, Play vs
  non-Play house mode), decision JSON parsing (unknown fields, oversize, expired), tracker queue
  (batching, offline, max size).
- **UI (Compose tests on desktop where possible):** carousel focus order and pause-on-focus;
  Back handling; banner hidden when the channel banner is visible.
- **Manual on an Android TV emulator (1080p) and one real TV box:** D-pad walkthrough, safe
  areas, subtitle overlap, pre-roll skip, VAST sample tags (Google IMA sample tags), slow network
  and offline, process death during an ad, "remove animations" setting.
- **Rules:** Firestore emulator tests for `ad_campaigns` and `ad_stats`.
- **Policy check before release:** the Play build contains no donation copy or payment QR (grep the
  strings and the decision used in QA).

## 11. Open questions for the user

1. **Q1 – Play Billing "Support" product:** OK to add Play Billing on Android (phone and TV) with
   3 tip tiers? Which prices? Does supporting remove ads (Q6)?
2. **Q2 – Non-Play distribution:** do you distribute a direct APK (website/Telegram)? If yes, the
   bank/MoMo QR house card can ship there.
3. **Q3 – Live sports:** no pre-roll on live channels at all, or allowed when opening from Home?
   Should the in-player banner skip channels in "Sports" groups?
4. **Q4 – Brand sales:** who sells and signs brands (you, an agency)? Minimum campaign size and
   pricing model (CPM vs flat per week)?
5. **Q5 – Backend:** host the events endpoint and short links on the telegram-bot server or on
   Firebase Cloud Functions (Blaze plan)?
6. **Q6 – Ad-free:** should a paid entitlement or a support purchase remove TV ads (and for how
   long)?
7. **Q7 – 24 h start on TV:** confirm it applies to TV (recommended yes).
8. **Q8 – Legal:** who reviews the VN advertising compliance and the brand IO template?
