# Play Store listing — English (en-US)

Paste each block into Play Console → Grow → Store presence → Main store listing.
Character limits are enforced by the console; the counts below are the actual
lengths of this copy.

---

## App name  *(limit 30)*

```
TS IPTV: Playlist Player
```

*24 characters.*

---

## Short description  *(limit 80)*

```
Play your own M3U, XSPF and JSON IPTV playlists, with EPG, on every device.
```

*74 characters.*

---

## Full description  *(limit 4000)*

```
TS IPTV is a player for the IPTV playlists you already have.

It ships with no channels. You bring the source: an M3U link from your provider,
an XSPF file, a JSON source or a TS IPTV Source. TS IPTV turns it into a clean,
fast, browsable TV experience on every screen you own.

WHAT IT DOES

• Imports M3U, M3U8, XSPF, JSON and iptv-org style catalogues, from a URL or a
  file on your device.
• Reads Kodi-style playlists: per-channel headers, DRM (Widevine, ClearKey and
  PlayReady on Android), catch-up information and .strm files.
• TS IPTV Source format: build your own source with a custom home layout,
  colours, channels, movies and series. The guide and a validator are on our
  website.
• Add your own Stremio-compatible addons to browse catalogues, search and
  continue watching where you left off. No addons are bundled.
• Switch stream or turn on subtitles while you watch.
• XMLTV programme guides (EPG) with correct times in any time zone.
• Automatic channel groups, watch history, background playback with lock-screen
  controls.

BUILT FOR EVERY SCREEN

Phones, tablets and Android TV (fully remote-controlled), plus iOS and desktop
from one Kotlin Multiplatform codebase.

FREE, WITH ADS

TS IPTV is free and shows ads on Android phones and tablets. There are no ads
during your first 24 hours, and no AdMob ads on Android TV. Users in the EU, the
UK and some US states choose how ads are personalised and can change it at any
time under Privacy options.

YOUR DATA

Your playlists, channels, programme guide and watch history are stored on your
device. To improve the app we receive anonymous statistics such as the playlist
format, the channel count and the source's domain, never usernames, passwords
or full links. Signing in is optional. Details are in our privacy policy.

IMPORTANT: THIS APP PROVIDES NO CONTENT

TS IPTV does not bundle, host, index or rebroadcast any television channel. It
is a player. You are responsible for the sources you add and must be legally
entitled to use them. If you do not already have a playlist from a provider you
subscribe to, the app shows an empty library, and that is by design.

SUPPORTED FORMATS

Playlists: M3U, M3U8 (including Kodi properties), XSPF, JSON, iptv-org,
TS IPTV Source, .strm
Guides: XMLTV (.xml and .xml.gz)
Streams: HLS, DASH, progressive HTTP, and other formats your device can decode

Questions, bugs, or a format we should support? Get in touch: the contact
address is on the listing and inside the app.
```

*Roughly 2,000 characters — comfortably inside the 4,000 limit, leaving room to
add a "What's new in this version" style paragraph later.*

---

## Other listing fields

| Field | Value |
| --- | --- |
| App category | Entertainment |
| Tags | Video players & editors, Entertainment |
| Contact email | *(the address set via `web/set-support-email.py`)* |
| Website | `https://tsiptv-8bdd6.web.app/` |
| Privacy policy | `https://tsiptv-8bdd6.web.app/privacy/?lang=en` |
| Contains ads | **Yes** — see `data-safety.md` |
| In-app purchases | **Yes** (from the subscriptions release, `docs/prd-subscriptions.md`): auto-renewing subscriptions "No ads" and "Unlimited". Play shows "In-app purchases" on the listing by itself once the products are active. |

**Description paragraph to use once subscriptions ship** (replaces "FREE, WITH ADS"):

```
FREE, WITH ADS, OR AD-FREE

TS IPTV is free and shows ads on Android phones and tablets. There are no ads
during your first 24 hours, and no AdMob ads on Android TV. Prefer no ads? The
optional "No ads" subscription removes them all, and "Unlimited" also removes
the daily limits on sending playlists to your TV and syncing your devices.
Subscriptions renew automatically; cancel any time in Google Play.
```

---

## Release notes — first release  *(limit 500)*

```
First public release of TS IPTV.

• Import M3U, M3U8, XSPF, JSON and iptv-org playlists from a URL or a file
• XMLTV programme guide with correct handling of every time zone
• Automatic channel grouping and watch history
• Background playback with lock-screen controls
• Android, iOS and desktop from a single codebase
```
