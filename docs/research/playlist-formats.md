# Playlist and guide formats: reference for user documentation

Research date: 2026-09-27. This file is source material for TS IPTV's user-facing help pages
("How do I make a playlist?"). Each section gives the exact syntax, required and optional fields,
a complete small example, how to write one by hand, how to host it, and common mistakes.
The Kodi-specific M3U extensions (`#KODIPROP`, catch-up, DRM) are covered in depth in
[`kodi.md`](./kodi.md). This file summarises them only where users need them.

All example hosts are placeholders (`example.com`, `cdn.example.com`). Help pages must keep it that
way. See [Wording rules for the help pages](#8-wording-rules-for-the-help-pages).

---

## 1. M3U, extended M3U and M3U8

### 1.1 What these names mean

| Name | What it is |
|---|---|
| **M3U** | A plain text list of media locations, one per line. It started with WinPlay3/Winamp (Wikipedia). A plain M3U has no metadata. |
| **Extended M3U** | M3U with a `#EXTM3U` first line and `#EXTINF:<duration>,<title>` before each entry. |
| **M3U8** | The same thing encoded as **UTF-8**. The `.m3u8` extension signals UTF-8 ("mandatory in M3U playlists with the M3U8 file extension", Wikipedia). |
| **HLS playlist (RFC 8216)** | Apple's HTTP Live Streaming *manifest*. It also uses `#EXTM3U`/`#EXTINF` and `.m3u8`, but the entries are **media segments of one stream**, not channels. |
| **IPTV channel list** | An extended M3U where each entry is a **whole channel** (usually a URL to an HLS manifest, an MPEG-TS stream, and so on), with extra `key="value"` attributes (`tvg-id`, `tvg-logo`, `group-title`...). This is a community convention, not a standard. |

**HLS media playlist vs IPTV channel list.** Both start with `#EXTM3U` and both can be named `.m3u8`.
A player must not confuse them:

| | HLS media playlist (RFC 8216) | IPTV channel list |
|---|---|---|
| An entry is | a 2-10 s media segment of **one** stream | a **channel** (a URL that is itself a stream or an HLS manifest) |
| `#EXTINF:<n>` | segment duration in seconds (REQUIRED, decimal) | `-1` or `0` (ignored) |
| Required tags | `#EXT-X-TARGETDURATION` (REQUIRED), usually `#EXT-X-MEDIA-SEQUENCE`, `#EXT-X-VERSION` | only `#EXTM3U` by convention |
| Multi-variant manifests use | `#EXT-X-STREAM-INF` (a master playlist) | never |
| Attributes on `#EXTINF` | none (title after the comma only) | `tvg-id="..." group-title="..."` and others |
| Who opens it | the video player, internally | the user, via "Add playlist" |
| Encoding | UTF-8, **no BOM**, NFC, no control characters (RFC 8216 §4.1) | UTF-8 in practice. Some old files are ANSI. |

Detection rule for TS IPTV: if the text contains `#EXT-X-TARGETDURATION`, `#EXT-X-STREAM-INF` or
`#EXT-X-MEDIA-SEQUENCE`, it is an HLS manifest. Offer to play it as a single stream instead of
importing it as a channel list.

RFC 8216 facts worth quoting:
- `#EXTM3U` "MUST be the first line of every Media Playlist and every Master Playlist".
- `#EXTINF:<duration>,[<title>]` "specifies the duration of a Media Segment".
- Identification is by the `.m3u8`/`.m3u` extension or by the HTTP Content-Type
  `application/vnd.apple.mpegurl` (or `audio/mpegurl`).
- Lines end with LF or CRLF. Lines that start with `#` are tags when they start with `#EXT`;
  all other `#` lines are comments.

### 1.2 IPTV M3U syntax

```
#EXTM3U [header attributes]
#EXTINF:-1 [attr="value" ...],<Channel name>
[#EXTGRP:<group>]
[#EXTVLCOPT:<option>=<value>]
[#KODIPROP:<property>=<value>]
<stream URL>[|Header=Value&Header=Value]
```

**Header attributes (`#EXTM3U` line)**

| Attribute | Required | Meaning |
|---|---|---|
| `x-tvg-url` | optional | XMLTV guide URL. Several URLs may be separated by commas. |
| `url-tvg` | optional | Older alias of `x-tvg-url`. |
| `tvg-shift` | optional | Default guide time shift in hours (for example `+2`, `-4.5`). |
| `catchup-correction` | optional | Default catch-up time correction in hours. |

**Channel attributes (`#EXTINF` line, before the comma)**

| Attribute | Required | Meaning |
|---|---|---|
| (name after the comma) | **required** | Display name of the channel. |
| `tvg-id` | recommended | ID that links the channel to the XMLTV guide (`<channel id>`). iptv-org uses `Channel.cc` or `Channel.cc@Feed`, for example `ExampleTV.us@HD`. |
| `tvg-name` | optional | Alternative name for guide matching. |
| `tvg-logo` | optional | Logo image URL (PNG, JPG or SVG). |
| `group-title` | optional | Category or folder. Kodi allows several, separated by `;`. |
| `tvg-chno` | optional | Channel number. |
| `tvg-shift` | optional | Guide time shift for this channel, in hours. |
| `tvg-country`, `tvg-language` | optional | Metadata used by some generators (iptv-org historically; Wikipedia lists them). |
| `radio` | optional | `"true"` for radio stations. |
| `catchup`, `catchup-days`, `catchup-source`, `catchup-correction` | optional | Archive / catch-up (see `kodi.md` §1.7). |
| `user-agent`, `referrer` / `http-referrer` | non-standard | Some generators put headers here. Accept them, but document `#EXTVLCOPT` instead. |

**Per-channel option lines** (between `#EXTINF` and the URL)

| Line | Meaning |
|---|---|
| `#EXTGRP:News` | Group name (an alternative to `group-title`). |
| `#EXTVLCOPT:http-user-agent=Mozilla/5.0 ...` | User-Agent for this stream (VLC convention, used by iptv-org). |
| `#EXTVLCOPT:http-referrer=https://example.com/` | Referer for this stream. Note the spelling `referrer`. |
| `#KODIPROP:inputstream.adaptive.license_type=...` and so on | DRM and adaptive-stream properties (see `kodi.md` §4). |

**URL line**

- Supported schemes (iptv-org's list): `http`, `https`, `rtmp`, `rtsp`, `mms`, `mmsh`, `srt`, `rtp`, `udp`.
  What actually plays depends on the platform player.
- Optional header suffix, Kodi style: `https://cdn.example.com/live.m3u8|User-Agent=Mozilla%2F5.0&Referer=https%3A%2F%2Fexample.com%2F`.
  Values should be URL-encoded.

### 1.3 Complete example

```
#EXTM3U x-tvg-url="https://example.com/guide.xml.gz"
#EXTINF:-1 tvg-id="ExampleNews.us" tvg-logo="https://example.com/logos/news.png" group-title="News",Example News
https://cdn.example.com/news/index.m3u8
#EXTINF:-1 tvg-id="ExampleSports.us@HD" tvg-chno="7" tvg-logo="https://example.com/logos/sports.png" group-title="Sports",Example Sports HD (1080p)
#EXTVLCOPT:http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64)
#EXTVLCOPT:http-referrer=https://example.com/
https://cdn.example.com/sports/index.m3u8
#EXTINF:-1 tvg-id="ExampleRadio.us" radio="true" group-title="Radio",Example Radio
https://cdn.example.com/radio/stream.aac
```

The minimum valid playlist:

```
#EXTM3U
#EXTINF:-1,My Channel
https://cdn.example.com/live.m3u8
```

### 1.4 How to create one by hand

1. Open a plain-text editor (Notepad, TextEdit in *plain text* mode, VS Code). Do not use Word or
   Google Docs.
2. Type `#EXTM3U` on the first line.
3. For each channel, add two lines: `#EXTINF:-1 <attributes>,<Name>` and then the stream URL.
4. Use straight double quotes `"` around attribute values.
5. Save as **UTF-8** (in Notepad: *Save as > Encoding: UTF-8*; "UTF-8 without BOM" is preferred)
   with the extension `.m3u` or `.m3u8`.
6. Test the stream URLs one by one (for example in VLC: *Media > Open Network Stream*) before sharing.

### 1.5 How to host it

- Any HTTPS URL that returns the **raw file** works. Content-Type can be `audio/x-mpegurl`,
  `application/vnd.apple.mpegurl`, `application/x-mpegurl` or `text/plain`; TS IPTV sniffs the content.
- **GitHub:** commit the file and use the *Raw* URL (`https://raw.githubusercontent.com/<user>/<repo>/<branch>/list.m3u`).
  A GitHub Gist's *Raw* link also works, but it contains the revision hash. Drop the hash from the
  URL so it always points to the latest version.
- **GitHub Pages / any static host** (Netlify, Cloudflare Pages, your own nginx): upload the file and
  use its URL. This is how iptv-org publishes `https://iptv-org.github.io/iptv/index.m3u`.
- **Dropbox:** change `?dl=0` to `?dl=1` (or `raw=1`) so the link returns the file, not a preview page.
- **Google Drive / OneDrive share links** return an HTML page. Avoid them, or use a direct-download form.
- **Local file:** import it from the device storage (Android SAF / iOS Files / desktop file picker).
- Remote playlists are re-downloaded on refresh. Keep the URL stable and edit the file in place.

### 1.6 Common mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| Missing `#EXTM3U` first line, or text before it (HTML, a BOM, blank lines) | "Not a playlist" | Make `#EXTM3U` the very first bytes. Save without BOM. |
| Link is a web page (Drive/Dropbox preview, GitHub `blob/` URL) | "Not a playlist", HTML error | Use the raw or direct-download URL. |
| Curly quotes `“ ”` from a word processor (even iptv-org's own docs page shows mojibake quotes `â€`) | Attributes ignored, logos and groups missing | Retype them as straight `"`. |
| No comma before the name, or attributes placed after the comma | Name shows as `tvg-id="..."`, or no name | `#EXTINF:-1 attrs,Name`. Nothing after the name. |
| Comma inside an attribute value placed after the name | Name cut off | Put all attributes before the name. |
| URL on the same line as `#EXTINF` | Channel has no stream | The URL goes on its own line. |
| Space or pipe in the URL without encoding | 404 or 400 | Encode spaces as `%20`. Use `\|` only for the header suffix. |
| Saved as ANSI/Windows-1252 with accented names | Garbled names (`Ã©`) | Save as UTF-8. |
| `tvg-id` does not match any XMLTV `<channel id>` | No guide data | Copy IDs exactly (they are case-sensitive in most players). |
| HLS segment playlist imported as a channel list | Hundreds of "channels" named after segment numbers | Import the *channel list*. Play the `.m3u8` manifest as a single stream. |
| Tokenized URLs (`?token=...`, `?e=<unix time>`) | Works today, fails tomorrow | These expire by design. Get a fresh list from the provider. |
| `http://` stream on Android | Cleartext blocked (on some builds) | Prefer `https://`. The app must allow cleartext for user-supplied streams. |

---

## 2. XMLTV (electronic programme guide)

### 2.1 Structure (from the XMLTV DTD)

```
<!ELEMENT tv (channel*, programme*)>
<!ELEMENT channel (display-name+, icon*, url*)>           id REQUIRED
<!ELEMENT programme (title+, sub-title*, desc*, credits?, date?, category*, keyword*,
                     language?, orig-language?, length?, icon*, url*, country*, episode-num*,
                     video?, audio?, previously-shown?, premiere?, last-chance?, new?,
                     subtitles*, rating*, star-rating*, review*, image*)>
                     start REQUIRED, channel REQUIRED, stop IMPLIED
```

| Element / attribute | Required | Notes |
|---|---|---|
| `<tv>` | root | Optional attributes: `date`, `source-info-url`, `source-info-name`, `source-data-url`, `generator-info-name`, `generator-info-url`. |
| `<channel id="...">` | `id` required | The ID matched against M3U `tvg-id`. Channels come **before** programmes. |
| `<display-name lang="..">` | at least 1 | Several allowed (for example per language). Also used as a fallback match against `tvg-name` or the channel name. |
| `<icon src=".." width height>` | optional | Channel logo. |
| `<programme start=".." stop=".." channel="..">` | `start`, `channel` required | `channel` must equal a `<channel id>`. `stop` is optional but should be present; players otherwise use the next programme's start. |
| `<title lang>` | at least 1 | |
| `<sub-title>` | optional | Episode title. |
| `<desc lang>` | optional | Description. Newlines are allowed here only. |
| `<category lang>` | optional, repeatable | Genre text. |
| `<episode-num system="xmltv_ns" \| "onscreen">` | optional | See below. |
| `<rating system="..."><value>..</value></rating>` | optional | Age rating, for example `system="MPAA"` / `NC-17`. |
| `<star-rating><value>N / M</value></star-rating>` | optional | Score. |
| `<icon src>` / `<image type="poster">` | optional | Programme artwork. |
| `catchup-id="..."` on `<programme>` | non-standard | Kodi extension used in catch-up URLs (`{catchup-id}`). |

**Times.** Format `YYYYMMDDhhmmss` followed by an optional timezone, for example
`20260927183000 +0700`. The DTD says: "if no explicit timezone is given, UTC is assumed". Initial
substrings (`YYYYMMDDhhmm`) are allowed. Always write the offset explicitly. Intervals are
half-open: a programme is "broadcasting at its start time but ... stop[s] just before its stop time".

**`episode-num` systems.**
- `xmltv_ns`: `season.episode.part`, **zero-based**, each part optionally `X/Y`. For example
  `1.0.0/1` = season 2, episode 1. `0 . 12/13 . 0/3` = season 1, episode 13 of 13, part 1 of 3.
  Unknown parts may be empty (`0..`). Spaces are allowed.
- `onscreen`: free text as shown on screen (`S01E02`, `#FFEE`). This is the default when `system`
  is omitted.
- Other systems are allowed: `themoviedb.org` (`movie/1234`), `thetvdb.com` (`series/123456`),
  `imdb.com` (`title/tt123455`), or a URL.

### 2.2 Complete example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE tv SYSTEM "xmltv.dtd">
<tv generator-info-name="hand-made">
  <channel id="ExampleNews.us">
    <display-name lang="en">Example News</display-name>
    <icon src="https://example.com/logos/news.png"/>
  </channel>
  <programme start="20260927180000 +0000" stop="20260927190000 +0000" channel="ExampleNews.us">
    <title lang="en">Evening Bulletin</title>
    <desc lang="en">The day's headlines.</desc>
    <category lang="en">News</category>
  </programme>
  <programme start="20260927190000 +0000" stop="20260927200000 +0000" channel="ExampleNews.us">
    <title lang="en">Weekly Magazine</title>
    <sub-title lang="en">Harvest Season</sub-title>
    <category lang="en">Documentary</category>
    <episode-num system="xmltv_ns">2.4.</episode-num>
    <episode-num system="onscreen">S03E05</episode-num>
    <rating system="MPAA"><value>PG</value></rating>
  </programme>
</tv>
```

It links to an M3U through `tvg-id="ExampleNews.us"` and `#EXTM3U x-tvg-url="https://example.com/guide.xml"`.

### 2.3 Compressed `.xml.gz`

- Real guides are often tens of megabytes, so publish them gzip-compressed as `guide.xml.gz`.
  Create one with `gzip -k guide.xml` (macOS/Linux) or 7-Zip *Add to archive > gzip* (Windows).
  Kodi IPTV Simple also accepts XZ (`.xml.xz`).
- TS IPTV detects gzip by its magic bytes, so it works whether the server sends
  `Content-Type: application/gzip` or `Content-Encoding: gzip`.
- Do **not** zip it (`.zip`). That is a different format.

### 2.4 Creating and hosting

- Most users do not write XMLTV by hand. They use a grabber (for example the iptv-org/epg tools) or
  their provider's guide URL. For a help page, a hand-written file like 2.2 is enough for a small
  personal list.
- Host it like an M3U (raw HTTPS URL), then either put it in `x-tvg-url` or add it in the app's EPG
  settings.

### 2.5 Common mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| No timezone offset on `start`/`stop` | Guide shifted by N hours | Add `+hhmm`. Otherwise UTC is assumed. |
| `channel` attribute does not equal `<channel id>` or M3U `tvg-id` | Empty guide | Use identical strings. |
| Unescaped `&` in titles or URLs | XML parse error, whole guide lost | Write `&amp;`. |
| `<programme>` elements before `<channel>` | Some parsers skip the channels | Put all `<channel>` elements first (DTD order). |
| Overlapping or duplicate programmes | Flickering or duplicate rows | Make `stop` equal the next `start`. |
| Gzip file served as `.xml` with no gzip header, or a double-gzipped file | "Invalid guide" | Compress exactly once. |
| Guide only covers past days | "No information" | Regenerate daily. |

---

## 3. XSPF (XML Shareable Playlist Format)

### 3.1 Syntax (xspf.org spec, version 1)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<playlist version="1" xmlns="http://xspf.org/ns/0/">
  <title>...</title>            <!-- 0..1 -->
  <trackList>                   <!-- exactly 1, may be empty -->
    <track>
      <location>URI</location>  <!-- 0..n, a player renders at most one -->
      <title>..</title>         <!-- 0..1 -->
      <image>URI</image>        <!-- 0..1 -->
      <extension application="URI">...</extension>  <!-- 0..n -->
    </track>
  </trackList>
</playlist>
```

- The namespace is `http://xspf.org/ns/0/` and `version="1"` ("the namespace is 0 but the version is 1").
- Playlist-level optional elements: `title`, `creator`, `annotation`, `info`, `location`,
  `identifier`, `image`, `date` (xsd:dateTime), `license`, `attribution`, `link rel`, `meta rel`,
  `extension application`.
- Track-level optional elements: `location` (0..n), `identifier` (0..n), `title`, `creator`,
  `annotation`, `info`, `image`, `album`, `trackNum`, `duration` (milliseconds, a hint only), `link`,
  `meta`, `extension`.
- Players "MUST skip to the next xspf:track" when one cannot be rendered.
- Relative paths are resolved against the document base URI (XML Base / RFC 2396).
- `<extension application="...">` carries non-XSPF XML. **VLC** saves IPTV lists with
  `application="http://www.videolan.org/vlc/playlist/0"` and the namespace
  `xmlns:vlc="http://www.videolan.org/vlc/playlist/ns/0/"`. Track IDs (`<vlc:id>`) and options
  (`<vlc:option>`) sit inside each track's extension. Groups sit in a playlist-level extension as
  `<vlc:node title="...">` containing `<vlc:item tid="N"/>`. The VLC details come from VLC's
  XSPF writer (`modules/misc/playlist/xspf.c`), not from xspf.org. TS IPTV's `XSPFParser` already
  reads `vlc:id` and `vlc:node title`.

### 3.2 Complete example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<playlist version="1" xmlns="http://xspf.org/ns/0/" xmlns:vlc="http://www.videolan.org/vlc/playlist/ns/0/">
  <title>My Channels</title>
  <trackList>
    <track>
      <location>https://cdn.example.com/news/index.m3u8</location>
      <title>Example News</title>
      <image>https://example.com/logos/news.png</image>
      <extension application="http://www.videolan.org/vlc/playlist/0">
        <vlc:id>0</vlc:id>
      </extension>
    </track>
    <track>
      <location>https://cdn.example.com/sports/index.m3u8</location>
      <title>Example Sports</title>
      <extension application="http://www.videolan.org/vlc/playlist/0">
        <vlc:id>1</vlc:id>
      </extension>
    </track>
  </trackList>
  <extension application="http://www.videolan.org/vlc/playlist/0">
    <vlc:node title="News"><vlc:item tid="0"/></vlc:node>
    <vlc:node title="Sports"><vlc:item tid="1"/></vlc:node>
  </extension>
</playlist>
```

### 3.3 Create, host, mistakes

- **Create:** easiest is VLC. Open the streams, then *Media > Save Playlist to File... > XSPF*. By hand,
  copy 3.2 and edit it in a text editor, saved as UTF-8 with the extension `.xspf`.
- **Host:** same as M3U (raw HTTPS URL, or import the local file).
- **Mistakes:** a missing or wrong `xmlns` (TS IPTV detects XSPF by the XSPF or VLC namespace); `&` in
  URLs not written as `&amp;`; putting the URL in `<title>` or `<info>` instead of `<location>`;
  treating `<duration>` as meaningful for live streams.

TS IPTV parser note: the current `XSPFParser` reads line by line and expects each element on one
line. It assigns the most recently seen `vlc:node` title as the group rather than resolving
`vlc:item tid`. Keep the help-page example formatted like 3.2 until the parser uses a real XML reader.

---

## 4. JSON channel lists

### 4.1 iptv-org API (`iptv-org/api`)

The data is published as static JSON arrays under `https://iptv-org.github.io/api/`. The files
relevant to a player:

**`streams.json`** (source: iptv-org/iptv)

| Field | Type | Meaning |
|---|---|---|
| `channel` | string or null | Channel ID, for example `France3.fr` |
| `feed` | string or null | Feed ID, for example `NordPasdeCalaisHD` |
| `title` | string | Stream title |
| `url` | string | Stream URL |
| `referrer` | string or null | `Referer` request header |
| `user_agent` | string or null | `User-Agent` request header |
| `quality` | string or null | Maximum quality, for example `720p` |
| `labels` | array | `Geo-blocked`, `Not 24/7` |

**`channels.json`** (source: iptv-org/database): `id`, `name`, `alt_names[]`, `network`,
`owners[]`, `country` (ISO 3166-1 alpha-2), `categories[]`, `is_nsfw`, `launched`, `closed`,
`replaced_by`, `website`.

**`logos.json`**: `channel`, `feed`, `in_use`, `tags[]`, `width`, `height`, `format`
(`PNG`/`JPEG`/`SVG`/`GIF`/`WebP`/`AVIF`/`APNG`), `url`. Logos were moved out of `channels.json`,
so a player that wants logos must join on `channel` (+ `feed`).

**`feeds.json`**: `channel`, `id`, `name`, `alt_names[]`, `is_main`, `broadcast_area[]`
(`r/`, `c/`, `s/`, `ct/` prefixes), `timezones[]`, `languages[]` (ISO 639-3), `format` (`576i`...).

**`guides.json`**: `channel`, `feed`, `site`, `site_id`, `site_name`, `lang`, `sources[]{host,url,format}`.

Also: `categories.json`, `languages.json`, `countries.json`, `subdivisions.json`, `cities.json`,
`regions.json`, `timezones.json`, and **`blocklist.json`** (`channel`, `reason` = `dmca` | `nsfw`, `ref`).

Example `streams.json` element (from the API README):

```json
{
  "channel": "France3.fr",
  "feed": "NordPasdeCalaisHD",
  "title": "France 3 Nord Pas-de-Calais HD",
  "url": "http://1111296894.rsc.cdn77.org/LS-ATL-54548-6/index.m3u8",
  "referrer": "http://example.com/",
  "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
  "quality": "720p",
  "labels": ["Geo-blocked"]
}
```

Join rules for a player: the stream ID is `channel@feed` (the M3U `tvg-id`). Name comes from
`channels.json`, logo from `logos.json`, and the group from `channels.categories`. Hide `is_nsfw`
by default. Honour `blocklist.json`.

iptv-org's M3U equivalent is the public playlist `https://iptv-org.github.io/iptv/index.m3u`
(plus `countries/<cc>.m3u`, categories and so on, listed in PLAYLISTS.md). It uses
`#EXTM3U x-tvg-url="..."`, `#EXTINF:-1 tvg-id="<channel>@<feed>" tvg-logo="..." group-title="...",Name (720p) [Geo-blocked]`,
and `#EXTVLCOPT:http-referrer=` / `http-user-agent=` for headers.

TS IPTV note: `IptvOrgRawDTO` maps `channel`, `feed`, `quality`, `referrer`, `title`, `url` and
`user_agent`. `labels` is not mapped.

### 4.2 Generic JSON format accepted by TS IPTV

This is not an external standard. It is the shape the existing `JSONParser`/`JSONModels.kt`
accepts, documented here so the help page can publish it. Unknown keys are ignored.

Accepted top-level shapes:
1. an object `{ "name", "epgUrl", "channels": [...], "groups": [...] }` (`channel`/`group` also accepted),
2. an object with only `channels` (or only `groups`),
3. an array of channel objects,
4. an array of group objects.

| Channel field | Required | Aliases | Meaning |
|---|---|---|---|
| `id` | **yes** | | Unique ID |
| `name` | **yes** | | Display name |
| `url` | yes (or `remote_data.url`) | `remote_data: {"url": "..."}` | Stream URL |
| `logoUrl` | no | `logo`, `image: {"url": "..."}` | Logo |
| `groupTitle` | no | `group` | Category name |
| `groupId` | no | | Category ID (derived from the title if missing) |
| `epgId` | no | `epg` | XMLTV channel ID |
| `description` | no | | |
| `attributes` | no | | String map, same keys as M3U attributes (for example `"user-agent"`) |

Group object: `{ "id"?, "name", "channels": [ ... ] }`.

Example:

```json
{
  "name": "My Channels",
  "epgUrl": "https://example.com/guide.xml.gz",
  "groups": [
    {
      "name": "News",
      "channels": [
        { "id": "ExampleNews.us", "name": "Example News",
          "url": "https://cdn.example.com/news/index.m3u8",
          "logo": "https://example.com/logos/news.png", "epgId": "ExampleNews.us" }
      ]
    }
  ],
  "channels": [
    { "id": "radio1", "name": "Example Radio", "url": "https://cdn.example.com/radio.aac", "group": "Radio" }
  ]
}
```

**Parser gaps to fix before publishing this page:**
- `IPTVParserFactory.detectFormat` sends content that starts with `[` (a JSON array) to the M3U
  branch. Only `{` is detected as JSON.
- A leading BOM or whitespace before `#EXTINF` is not handled consistently.
- `JSON_IPTV_ORG` is never auto-detected.

### 4.3 Common JSON mistakes

Trailing commas; comments (`//`) in `.json` files (the iptv-org README uses `jsonc` for display
only, the real files have none); numbers used for `id` instead of strings; smart quotes; a file saved
with a BOM; a GitHub page URL instead of the raw URL.

---

## 5. Xtream Codes style provider URLs

**What it is.** "Xtream Codes" was commercial IPTV *panel* software, used by providers to manage
subscribers and streams. It is **a provider API, not a file format**. Many panels still expose the
same URL shape. Background: in September 2019 Italy's Guardia di Finanza led raids that shut the platform down. In 2021 the Naples Court of Appeals found "no evidence to show that Xtream Codes Ltd acted
illegally", and Italy's Supreme Court dismissed the prosecutor's appeal (TorrentFreak, 2022).
Whether a particular *provider* using such a panel is licensed is a separate question.
iptv-org refuses Xtream links because they are unstable.

There is no official public specification. The shape below is the one used by open-source
clients (py-xtream-codes, iptv-proxy) and by Kodi IPTV Simple's `catchup="xc"` logic. Some GitHub
"documentation" for these panels is based on decoded panel source code. It was **not** used here.

| Purpose | URL |
|---|---|
| M3U playlist | `http(s)://host:port/get.php?username=U&password=P&type=m3u_plus&output=ts` (`type=m3u` = without extra attributes; `output=ts` or `m3u8`/`hls`) |
| Account + server info (JSON) | `.../player_api.php?username=U&password=P`, which returns `user_info` (`auth`, `status`, `exp_date`, `is_trial`, `active_cons`, `max_connections`, `allowed_output_formats`, ...) and `server_info` (`url`, `port`, `https_port`, `server_protocol`, `timezone`, `timestamp_now`, ...) |
| Live categories / streams | `...&action=get_live_categories`, `...&action=get_live_streams[&category_id=X]`. A stream item has `num`, `name`, `stream_type`, `stream_id`, `stream_icon`, `epg_channel_id`, `added`, `category_id`, `tv_archive`, `tv_archive_duration`, `direct_source` |
| VOD / series | `get_vod_categories`, `get_vod_streams`, `get_vod_info&vod_id=`, `get_series_categories`, `get_series`, `get_series_info&series_id=` |
| EPG | `.../xmltv.php?username=U&password=P` (full XMLTV); `...&action=get_short_epg&stream_id=X[&limit=N]`; `...&action=get_simple_data_table&stream_id=X` |
| Live stream | `http(s)://host:port/live/U/P/<stream_id>.ts` (or `.m3u8`). The older form omits `/live` |
| VOD / episode | `.../movie/U/P/<id>.<container_extension>`, `.../series/U/P/<id>.<ext>` |
| Catch-up (as built by Kodi `catchup="xc"`) | `.../timeshift/U/P/<duration minutes>/<YYYY-MM-DD:HH-MM>/<stream_id>.ts` |

For the help page: "If your provider gave you a server, username and password, use *Add Xtream
account* (or paste the `get.php` link as a playlist URL)." Credentials are in the URL, so the app
must store them securely and never log them. The page should not name or recommend providers.

---

## 6. MonPlayer ("Monster Player", `org.monplayer.mpapp`)

**Scope:** only public sources were used. The app was not decompiled, its backend was not contacted,
and no leaked or private material was used.

### 6.1 Public documentation of its playlist or source format

**None was found.**

- The Google Play listing (developer **Monoverse Co., Ltd**, last updated Jan 23 2026, 100K+
  downloads as seen on 2026-09-27) only says the player streams "from sources such as M3U playlist,
  IPTV link or JSON source" and is "a sources manager where you can add M3U/JSON playlist from an
  URL as an provider". It gives no schema.
- The developer website linked from the listing (`monplayer.org`, including `/privacy-policy` and
  `/preview`) is currently a **parked domain** (Sedo parking page). It has no documentation.
- The iOS App Store listing (id 6590610364) returns 404, and the iTunes lookup API returns 0 results
  (checked 2026-09-27), so the app appears to be removed from the App Store.
- Searches for a public spec, a GitHub schema or a sample found only APK mirror pages and press articles.
- The press articles (6.2) mention "M3U, IPTV links or JSON sources" and say users paste links taken
  from the network's sites. They describe no schema either.

**Conclusion.** The "JSON source" format is undocumented publicly. M3U sources are presumably
ordinary M3U, and TS IPTV already imports those. To support the JSON variant we would need **a
user-supplied sample file** that the user created or is entitled to share, containing no pirated
catalogue URLs (they can be replaced with `example.com` placeholders). Specifically:
1. one complete JSON document, as saved or served, with its top-level structure intact;
2. whether it is served from a URL (and the Content-Type) or saved as a file;
3. a second sample, if possible, to tell required fields from optional ones.

This matches `docs/monplayer-parser-plan.md` ("Blocked on one input"). The existing `JSONParser`
already accepts nested group arrays, `remote_data.url` and `image.url`. Whether that matches
MonPlayer's JSON cannot be confirmed without a sample, and it should not be assumed.

### 6.2 What public sources say about legality (reported facts, not our findings)

- **Znews** (7 March 2026, citing a VTV report; English syndication on vietnam.vn) describes
  MonPlayer as "a product of the Xôi Lạc TV network", an unlicensed sports and TV redistribution
  network. It says the app receives streams via "M3U playlist links, IPTV links, or JSON sources",
  that it "bypassed the censorship layers of Google and Apple", and that it was still on Google Play
  and the App Store at the time of writing.
- **VnReview** (7 March 2026), "Giải mã ứng dụng Monplayer: Cách mạng lưới Xôi Lạc TV qua mặt Google
  và Apple" ("Decoding the Monplayer app: how the Xôi Lạc TV network got past Google and Apple"),
  reports that the app launched in June 2025 and had "tens of thousands" of monthly downloads
  (AppFigure data). It says Xôi Lạc satellite sites hand out encoded links that users paste into the
  app, and that the app is presented as an ordinary media player.
- Both reports say that on **5 March 2026** Vietnam's Cybersecurity and High-Tech Crime Prevention
  Department (A05, Ministry of Public Security), with Hưng Yên provincial police, prosecuted
  **30 people** for copyright infringement and gambling offences connected to the network.
- Note: an APK index snippet says the Play listing has existed since October 2024, while VnReview
  says June 2025. This was not reconciled; cite the press date only as "reported".

**Framing for TS IPTV:** importing a playlist *file format* is neutral, and the user's content decides
legality. TS IPTV must not fetch, bundle or advertise MonPlayer's catalogue or the sites that
distribute its links, and must not describe the feature as "watch MonPlayer channels". See also the
scope boundary in `docs/monplayer-parser-plan.md` and the Google Play IP policy notes in `kodi.md` §6.

---

## 7. Quick detection table (for the importer)

| First meaningful bytes / markers | Format |
|---|---|
| `#EXTM3U` + `#EXTINF` with attributes, no `#EXT-X-TARGETDURATION` | IPTV M3U |
| `#EXTM3U` + `#EXT-X-TARGETDURATION` / `#EXT-X-STREAM-INF` | HLS manifest: play it, do not import it |
| `<?xml` ... `<tv` | XMLTV guide |
| `<playlist` with `xmlns="http://xspf.org/ns/0/"` | XSPF |
| `[` or `{` | JSON (iptv-org if elements have `channel`+`url`+`title`; otherwise generic) |
| `1F 8B` (gzip magic) | decompress, then detect again |
| `<!DOCTYPE html` / `<html` | Not a playlist. The link is a web page (help text: "use the raw/direct link") |
| a single URL line (optionally `#KODIPROP` lines) | `.strm`-style single stream |
| `get.php?username=` in the URL | Xtream-style provider playlist (M3U) |

Strip a UTF-8 BOM (`EF BB BF`) and leading whitespace before detecting.

## 8. Wording rules for the help pages

- Use `example.com` hosts only. Do not include real stream URLs other than clearly public and
  licensed ones (iptv-org playlists are acceptable because they document a takedown process and a
  DMCA blocklist).
- Say "TS IPTV plays playlists you provide. It does not include any channels."
- Do not name or link Kodi add-ons, add-on repositories, "free IPTV" lists, Xtream resellers,
  or MonPlayer sources.

---

## Sources

All of these were read on 2026-09-27.

- RFC 8216, HTTP Live Streaming: https://www.rfc-editor.org/rfc/rfc8216.txt
- M3U (Wikipedia; history, extended M3U directives, M3U8 = UTF-8, MIME types): https://en.wikipedia.org/wiki/M3U
- Kodi PVR IPTV Simple README (IPTV M3U attributes, XMLTV usage): https://github.com/kodi-pvr/pvr.iptvsimple/blob/Piers/README.md
- XMLTV DTD: https://github.com/XMLTV/xmltv/blob/master/xmltv.dtd
- XSPF version 1 specification: https://www.xspf.org/spec
- iptv-org API README (all JSON schemas): https://github.com/iptv-org/api/blob/master/README.md
- iptv-org/iptv README, PLAYLISTS.md, CONTRIBUTING.md, FAQ.md: https://github.com/iptv-org/iptv
- iptv-org docs, playlists, stream description scheme, stream ID, Xtream Codes, tokenized links:
  https://github.com/iptv-org/iptv/blob/master/docs/playlists.md ,
  https://github.com/iptv-org/iptv/blob/master/docs/stream-description-scheme.md ,
  https://github.com/iptv-org/iptv/blob/master/docs/stream-id.md ,
  https://github.com/iptv-org/iptv/blob/master/docs/xtream-codes.md ,
  https://github.com/iptv-org/iptv/blob/master/docs/tokenized-links.md
- Xtream-style API shape (open-source clients): https://github.com/chazlarson/py-xtream-codes/blob/master/xtream.py ,
  https://github.com/pierre-emmanuelJ/iptv-proxy/blob/master/README.md ,
  https://github.com/AndreyPavlenko/Fermata/discussions/434 ;
  Kodi `xc` catch-up regex: https://github.com/kodi-pvr/pvr.iptvsimple/blob/Piers/src/iptvsimple/data/Channel.cpp
- VLC XSPF writer (VLC extension namespace): https://github.com/videolan/vlc/blob/master/modules/misc/playlist/xspf.c
- Xtream Codes legal history: https://torrentfreak.com/xtream-codes-iptv-company-declared-lawful-assets-seized-in-raid-returned-220616/
- MonPlayer Google Play listing: https://play.google.com/store/apps/details?id=org.monplayer.mpapp
- MonPlayer developer site (parked): https://monplayer.org
- Apple lookup (0 results): https://itunes.apple.com/lookup?id=6590610364&country=us
- Znews via vietnam.vn (English): https://www.vietnam.vn/en/xoi-lac-tv-tu-lam-app-de-mo-rong-mang-luoi-xem-lau
  (original: https://znews.vn/xoi-lac-tv-tu-lam-app-de-mo-rong-mang-luoi-xem-lau-post1633056.html ; returned 404 when fetched)
- VnReview: https://vnreview.vn/threads/giai-ma-ung-dung-monplayer-cach-mang-luoi-xoi-lac-tv-qua-mat-google-va-apple.80196/
- W3C EME Clear Key (for DRM playlists; details in kodi.md): https://www.w3.org/TR/encrypted-media/#clear-key-license-format
