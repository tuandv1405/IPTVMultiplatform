# Web sources: turn a web page into channels with AI

Research date: 2026-10-24. Audience: TS IPTV PO + engineers. Status: **research, nothing built**.

Request: the user pastes the address of a web page that plays video. The app works out the streams on
that page with AI, turns them into a playlist (M3U-like), and plays them with the native player like
any IPTV channel. When the user has added at least one web source, Home gets a **Web** tab.

**Bottom line**

- **Feasible, but "HTML → M3U" alone works on few real pages.** Most video pages build the stream
  URL in JavaScript at play time, sign it with a token that expires in minutes, and check `Referer` /
  `Origin` / cookies. A static M3U made once from the HTML is dead within the hour.
- **Recommended design: AI writes a recipe once; the app resolves streams at play time.**
  1. The app loads the page in a hidden WebView and records the media requests it makes
     (`.m3u8`, `.mpd`, `.mp4`) with their headers. No AI needed for that part.
  2. A server endpoint sends the page structure + the captured requests to Claude, which returns a
     **recipe** (JSON, schema-validated): the list of channels on the page (name, logo, group, the
     link to each channel's page) and how to get each stream (captured-request pattern + headers).
  3. The channels go into the existing channel model, so player, favourites, history, search, Send to
     TV and sync work unchanged. A channel's stream is resolved **when it is played** (re-run the
     page in the hidden WebView, pick the request that matches the recipe), never stored for long.
- **The AI call runs on our server, never in the app** (no API key in the APK), once per source plus a
  re-analysis when a recipe stops working. Estimated **≈ US$0.15–0.50 per analysis** with Claude
  Opus 5.5; a refresh or a play costs nothing.
- **The policy risk is the real blocker, not the tech.** A button that turns any website into TV
  channels reads as a piracy tool to Google Play and App Review. Ship only with the limits in §6
  (user-supplied pages only, no DRM or paywall bypass, blocklist, remote kill switch), behind a remote
  flag, and Android-only first.
- **Next step:** a 2–3 day spike (§8) on ~20 legal sample pages to measure how many pages each layer
  handles before committing to the full build (~3–4 weeks for Android + server).

---

## 1. What real pages look like

| Page type | Example (legal) | Static HTML has the stream? | What works |
|---|---|---|---|
| A. Direct link list | A page listing `.m3u8` / `.mp4` links, an open-data channel list | Yes | Plain fetch + parse (no AI even needed) |
| B. Single player, plain `<video src>` | Public broadcaster live page, a university lecture page | Usually yes | Fetch + parse, or WebView capture |
| C. Player built by JS (hls.js, video.js, Shaka, JW Player) | Most broadcaster "watch live" pages | No: URL comes from an API call | **WebView capture** |
| D. Signed / expiring URLs (`?token=…&expires=…`) | CDN-protected live streams | No, and a saved URL dies in minutes | WebView capture **at play time** |
| E. Player in an `<iframe>` from another host | Embedded players | No | WebView capture (requests from frames are seen too) |
| F. DRM (Widevine / FairPlay / PlayReady) | Paid services | No | **Out of scope**: we must not extract keys or licences |
| G. Login / paywall / anti-bot challenge | Subscription sites | No | **Out of scope**: do not bypass |

So a design must handle C, D and E to be useful, and must deliberately refuse F and G.

## 2. Options compared

| | A. Fetch HTML → AI → M3U | B. WebView capture only | **C. WebView capture + AI recipe + resolve at play (recommended)** |
|---|---|---|---|
| Handles page types | A, B | B, C, E (one stream per page) | A–E, multi-channel pages |
| Expiring URLs (D) | Breaks | Breaks if stored | Works: resolved per play |
| AI cost | Every refresh | None | Once per source (+ when it breaks) |
| Multi-channel pages (a list with 30 channels) | Yes | No: only what auto-plays | Yes: AI finds the channel links, capture resolves each |
| Prompt-injection exposure | AI output used directly | none | AI output is only a schema-checked recipe; never code |
| Effort | Low | Medium | High |

Option A alone is a demo, not a feature. Option B alone (a "video sniffer") is simple and useful for
single-player pages, and can ship first as the fallback inside C.

## 3. Recommended architecture

```
 App (Android first)                           Server (telegram-bot service, Node)        Claude API
 ───────────────────                           ───────────────────────────────────        ──────────
 Add source › Web page › URL
   │
   ├─ HiddenPageLoader: load URL in an
   │  off-screen WebView for ≤ 20 s,
   │  record media requests + headers,
   │  DOM snapshot (links, titles, imgs)
   │
   ├─ POST /web-source/analyze ───────────────▶ auth (Firebase ID token), rate limit,
   │  {url, dom, requests}                     blocklist check, size cap
   │                                           │
   │                                           ├─ messages.create(structured output) ──▶ recipe JSON
   │                                           │
   │◀────────────── recipe (validated) ────────┘
   │
   ├─ Preview: "Found 24 channels" → Save
   ├─ WebSource saved (url, recipe, channels)
   └─ Play channel → HiddenPageLoader on the
      channel page → pick request matching the
      recipe → native player with its headers
```

### 3.1 Capture on each platform

| Platform | How to see the page's media requests | Notes |
|---|---|---|
| Android | `WebViewClient.shouldInterceptRequest` sees every sub-resource request (URL + request headers) from the page and its frames. Plus an injected script hooking `fetch`, `XMLHttpRequest.open`, `HTMLMediaElement.src` / `<source>` and `MediaSource` for anything the hook misses. | **Verify in the spike** that native `<video src=…m3u8>` requests reach `shouldInterceptRequest` on current WebView; the JS hook covers it otherwise. Cookies: read with `CookieManager` for the stream host and pass them as a header. |
| iOS | `WKWebView` has no interception for http(s). Use the injected script (`WKUserScript`, all frames) for fetch / XHR / media element URLs, posted back through a `WKScriptMessageHandler`. | Same JS as Android. Request headers of native `<video>` loads are not visible; send `Referer` = page URL and the WebView's user agent, which is what most CDNs check. |
| Desktop | `WebViewInApp.desktop.kt` opens the system browser today, so there is nothing to capture. | Out of scope for v1. Later: an embedded Chromium (JCEF / KCEF) with a request handler. |
| Android TV | Same as Android. | Loading heavy pages on cheap TV boxes is slow; prefer adding sources on the phone and **sending to TV** (existing playlist send) once the format is supported there. |

Guard rails for the hidden WebView: no navigation outside the page's site and its frames' hosts, no
pop-ups, no downloads, no file access, no `addJavascriptInterface` exposure beyond the capture
callback, autoplay muted, hard timeout, and the user can watch the page in a visible WebView if
they want ("Show page").

### 3.2 The recipe (AI output)

The model never returns code. It returns JSON validated against a schema (structured outputs) and
then re-validated on the server:

```json
{
  "kind": "multi",                     // "single" | "multi" | "unsupported"
  "unsupportedReason": null,           // "drm" | "login" | "no_video" | "blocked" when unsupported
  "title": "Example Channels",
  "channels": [
    {
      "name": "Channel 1",
      "logo": "https://example.com/logo1.png",
      "group": "News",
      "pageUrl": "https://example.com/live/1",   // must be on the same site as the source URL
      "isLive": true
    }
  ],
  "stream": {
    "match": "\\.m3u8(\\?|$)",          // regex over captured request URLs, applied in the app
    "prefer": "highest-variant-playlist",
    "headers": { "Referer": "{pageUrl}", "Origin": "{pageOrigin}" },
    "resolveAtPlay": true               // false only for stable direct links (page types A/B)
  }
}
```

Server checks before returning it: every URL is http(s) and on the source's site (or a host seen in
the captured requests), the regex compiles and is bounded, header names are on an allowlist
(`Referer`, `Origin`, `User-Agent`, `Cookie` only from the device), at most 500 channels.

### 3.3 How it plugs into the app

- **Storage:** a new source type next to M3U / TS IPTV Source / addons, e.g. `WebSourceEntity`
  (`url`, `recipeJson`, `recipeVersion`, `analyzedAt`, `lastOkAt`) whose channels go into the
  existing channel tables with `streamUrl` = a marker such as `tsiptv-web://<sourceId>/<index>`.
- **Playback:** the player's URL resolution step recognises the marker, runs the capture on the
  channel's `pageUrl`, and hands the real URL + headers to the native player (Media3 / AVPlayer).
  The same hook is where `|headers` and `#EXTVLCOPT` already end up, so DRM-free HLS/DASH paths are
  unchanged. Expect 2–6 s extra start time; show "Opening page…" on the loader.
- **Export:** "Export as M3U" can write the channels with their page URLs, labelled as needing TS IPTV.
  A plain M3U with resolved URLs is not offered (the URLs expire).
- **Self-healing:** if a play finds no matching request twice in a row, mark the recipe stale and
  offer "Analyse again" (one server call).
- **Parsers:** nothing changes in `core/parser/*`. The recipe is not a playlist format; it lives
  beside them.

### 3.4 The Home "Web" tab

There is an exact precedent: **Discover** (`HomeBottomNavigationScreen.kt`, `discoverNavItem`) is in
the bottom bar only while an addon is enabled (`hasActiveAddons`), including the null-state and
back-stack handling. Add `NavRoutes.HomeScreens.WEB` and a `webNavItem` shown while
`hasWebSources == true`, same rules. Content: one section per web source (title, channel grid,
"Analyse again", "Show page", "Remove"). TV layout: a rail item like Discover in `TvHomeScreen`.

Tab order: Home, Discover, **Web**, History, Programs, Profile would make six items on a phone; when
both Discover and Web exist, consider merging them as two segments of one "Discover" tab.

## 4. AI call (server side)

- **Where:** a new route in the existing Node service (`telegram-bot/src`, which already has Firebase
  ID token auth, rate limiting and the billing routes), e.g. `POST /web-source/analyze`.
- **Model:** Claude Opus 5.5 (`claude-opus-5-5`), structured outputs (`output_config.format` with the
  recipe JSON schema), adaptive thinking with effort set explicitly (start at `medium`, measure),
  and server-side refusal fallbacks enabled. Use the official Node SDK (`@anthropic-ai/sdk`).
- **Input size:** do not send raw HTML. Send a reduced DOM (links with text, `img` alt/src,
  headings, `video`/`iframe` attributes, JSON-LD) + the captured request list. Typical 15–60 k tokens.
- **Prompt injection:** the page is untrusted text. It can only influence the recipe, which is
  schema-constrained and validated (§3.2): same-site URLs, bounded regex, allowlisted headers. The
  model has no tools and its output is never executed.
- **Cost estimate** (list price of Opus 5.5: US$4 / M input tokens, US$20 / M output tokens):

  | | Input | Output (incl. thinking) | Cost |
  |---|---|---|---|
  | Small page | 15 k | 4 k | ≈ US$0.14 |
  | Typical | 40 k | 8 k | ≈ US$0.32 |
  | Large list page | 60 k | 12 k | ≈ US$0.48 |

  The system prompt + schema (a few thousand tokens) is cached across calls. 1 000 sources a month at
  ~1.3 analyses each ≈ US$400. Cheaper models (Claude Sonnet 5.5, Claude Haiku 5.5) are a measured
  decision for after the spike, on the same sample set, if quality holds.
- **Abuse / budget:** per-user daily limit (e.g. Free 2 analyses/day, Unlimited 20), global monthly
  cap, cache recipes by normalised URL so the second user adding the same page costs nothing (only
  the recipe is shared, never cookies or captured tokens).

## 5. Privacy

- What leaves the device: the page URL, the reduced DOM and the captured request URLs (query strings
  can contain tokens, so strip known token parameters, or hash them, before sending). Cookies and
  request headers stay on the device.
- The data goes to our server and to Anthropic. Add to the privacy policy (`web/public/privacy/`)
  and the Play Data safety form ("App activity › Other user-generated content", processed, not shared
  for ads); update `play-store/data-safety.md`.
- Analysis needs sign-in (for the rate limit), the same as Send to TV.

## 6. Store policy and legal risk

This is the part that decides whether the feature can ship.

- **Google Play** removes apps that facilitate unauthorised streaming (Intellectual Property policy)
  and treats IPTV players with extra scrutiny. **App Review 5.2.3** forbids facilitating illegal
  file sharing or saving/converting third-party media without authorisation. A generic "extract the
  video from any website" button is exactly what reviewers look for.
- Limits to build in, and to describe that way in the listing and to reviewers:
  1. **User-supplied pages only.** No directory, no suggestions, no examples of third-party sites
     (same rule as the guides: `docs/prd-web-format-guides.md`).
  2. **No DRM, no login, no paywall, no anti-bot bypass.** Encrypted streams and protected pages get
     `unsupported`, and the app says why. Never forward stolen cookies to the server.
  3. **Blocklist + kill switch:** reuse the addon blocklist mechanism (`policy/addon-blocklist.json`)
     for hosts, and a Remote Config flag that hides the feature per platform or country.
  4. **Terms:** the user confirms they have the right to watch the page's content; takedown contact.
  5. Respect the page's own playback (watching in our player is equivalent to watching on the page),
     do not offer download or recording of web-source streams.
- Rollout: Android only, behind the remote flag, **off in the first store build**; on in internal /
  closed testing first. Decide with the store review experience of the first Android TV release.
- Ads: some pages show ads around the player; replacing their player removes those. Legally sensitive
  for broadcaster pages; the terms and the "user-supplied" framing are the mitigation, not a fix.

## 7. Effort (rough)

| Work | Estimate |
|---|---|
| Spike (§8) | 2–3 days |
| Android hidden-page loader + capture + JS hook | 4–5 days |
| Server route, prompt, schema, validation, rate limit, recipe cache, tests | 4–5 days |
| Web source storage, resolve-at-play in the player, self-healing | 4–5 days |
| UI: add flow + preview, Web tab (phone + TV rail), settings, strings × 7 locales | 4–5 days |
| Privacy/terms/data-safety texts, remote flag, blocklist | 1–2 days |
| iOS capture (JS-only) + AVPlayer headers | 4–6 days (later) |

## 8. Spike plan (do this before deciding)

1. Pick ~20 **legal** pages across types A–E (public broadcasters' free live pages, open-licence video
   sites, our own test page on `tsiptv-8bdd6.web.app` with hls.js and a signed-URL mock).
2. Android debug-only screen: load a URL in the hidden WebView, list captured media requests and
   headers, play the chosen one with the existing player.
3. A script (server side) that sends the reduced DOM + captures to Claude Opus 5.5 with the recipe
   schema; save recipes.
4. Measure per page: capture found a playable stream? recipe found all channels? replay at play time
   works after 1 h? start-up delay? tokens and cost per analysis?
5. Go / no-go: ship if ≥ 70 % of type B–E pages play via capture and multi-channel recipes are right on
   most list pages; otherwise ship option B (single-page sniffer, no AI) only.

## 9. Open questions

- Q1 Is the policy risk (§6) acceptable at all for the Play listing, or should web sources be a
  desktop / sideload-only feature?
- Q2 Free vs Unlimited: is "Analyse a web page" a paid-plan feature (it has a per-use cost)?
- Q3 Merge Discover and Web into one tab when both exist?
- Q4 Allow sharing a recipe between users (cheaper, but it publishes which sites users add)?
