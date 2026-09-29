# Kodi formats: what TS IPTV can reuse

Research date: 2026-09-27. Audience: TS IPTV engineers (Android Media3, iOS AVPlayer, desktop VLCJ).
Everything below comes from primary sources listed in [Sources](#sources). Where this document states
an implementation fact about Kodi (for example, which aliases the parser accepts), it was read from the
C++ source code on the current development branch (`Piers`, Kodi 22), not from memory.

**Bottom line**

- **Reusable (data formats):** the M3U dialect accepted by PVR IPTV Simple Client (attributes, `#EXTGRP`,
  `#KODIPROP`, `#EXTVLCOPT`, `|header=value` URL suffix, catch-up modes and placeholders, `x-tvg-url`),
  `.strm` files, and the inputstream.adaptive DRM properties (`license_type`, `license_key`,
  `drm_legacy`, `drm`). They are all plain text and map cleanly onto Media3 / AVPlayer / VLC.
- **Not reusable (code):** Kodi video add-ons (`plugin.video.*`), repositories (`repository.*`,
  `addons.xml`) and `plugin://` URLs. These are Python programs that run only inside Kodi's embedded
  interpreter, against Kodi's `xbmc*` modules. An external player cannot run them, and should not try.
- **Legal:** support the *formats* and never ship, bundle, link to, or recommend third-party add-ons
  or repositories. Most third-party video add-ons are infringing (see [Legal and Play notes](#6-legal-and-google-play-notes)).

---

## 1. PVR IPTV Simple Client (`pvr.iptvsimple`) M3U dialect

PVR IPTV Simple Client is Kodi's official binary (C++) PVR add-on for M3U + XMLTV. Its README
"Supported M3U and XMLTV elements" appendix is the most widely copied reference for IPTV M3U
attributes. The README is identical on the `Omega` (Kodi 21) and `Piers` (Kodi 22) branches.

### 1.1 Overall structure

```
#EXTM3U <header attributes>
#EXTINF:<duration> <attr>="<value>" <attr>="<value>" ...,<Channel name>
#EXTGRP:<group;group>            (optional, see 1.4)
#KODIPROP:<key>=<value>          (optional, zero or more)
#EXTVLCOPT:<key>=<value>         (optional, zero or more)
<stream URL>[|<header>=<value>&<header>=<value>]
```

- The minimum channel is an `#EXTINF` line with a name after the comma, followed by a URL line.
- `<duration>` is `0` or `-1` for live channels (both appear in the README examples). It is not used.
- Directive lines between `#EXTINF` and the URL apply to that one channel.
- A missing `#EXTM3U` first line is logged as an error but parsing continues
  (`PlaylistLoader.cpp`: "missing #EXTM3U descriptor on line 1, attempting to parse it anyway").

### 1.2 `#EXTM3U` header attributes

| Attribute | Meaning |
|---|---|
| `x-tvg-url` | URL of the XMLTV guide. Used only if the add-on settings have no EPG location. |
| `url-tvg` | Alias of `x-tvg-url` (source: `TVG_URL_OTHER_MARKER = "url-tvg="`). Read only if `x-tvg-url` is absent. |
| `tvg-shift` | Default EPG time shift in hours (decimal allowed, for example `-4.5`) for channels without their own `tvg-shift`. |
| `catchup-correction` | Default catch-up time correction in hours for channels without their own value. |
| `catchup`, `catchup-type`, `catchup-days`, `catchup-source` | Header-level defaults for the channel catch-up attributes (source code; not in the README table). `catchup="xc"` in the header also switches on XEEV-specific behaviour. |

If the `x-tvg-url` value is a comma-separated list, Kodi takes only the first URL
(source: "The tvgUrl might be a comma separated list. If it is just take the first one").
TS IPTV can do better and merge all of them.

### 1.3 `#EXTINF` attributes (complete list from README and `PlaylistLoader.h`)

Syntax: `#EXTINF:<duration> key="value" key="value",<Channel name>`. Values are double-quoted.

| Attribute | Meaning | Notes |
|---|---|---|
| `tvg-id` | Unique ID used to match the channel to XMLTV `<channel id>`. | The parser also accepts `tvg-ID` ("some provider incorrectly use an uppercase ID"). |
| `tvg-name` | Name used to match XMLTV `<display-name>` (with or without spaces replaced by `_`). | |
| `tvg-logo` | Logo URL. | For relative values `.png` is appended if missing; absolute URLs are unchanged. |
| `tvg-chno` | Channel number. | Alias accepted: `ch-number`. |
| `tvg-shift` | Per-channel EPG time shift in hours. | Overrides the header value. |
| `group-title` | **Semicolon-separated** list of groups. | `group-title="Entertainment;HD Channels"` puts the channel in two groups. |
| `radio` | `"true"` (case-insensitive) marks a radio channel. | |
| `catchup` | Catch-up mode (see 1.7). | Alias: `catchup-type`. |
| `catchup-source` | Full catch-up URL template (`default` mode) or the query string to append (`append` mode). | |
| `catchup-days` | Catch-up window in days. | Alias: `tvg-rec` (treated like SIPTV days). |
| `catchup-correction` | Hours added to the times used when building catch-up URLs. | For geo-mismatched servers. |
| `timeshift` | Legacy SIPTV attribute: days of catch-up in `shift` mode, for example `timeshift="3"`. | |
| `provider` | Provider name, used for provider mappings. Other `provider-*` tags are ignored without it. | |
| `provider-type` | One of `addon`, `satellite`, `cable`, `aerial`, `iptv`, `unknown`. | |
| `provider-logo` | Provider icon path. | |
| `provider-countries` | ISO 3166 codes, comma-separated (for example `IE,GB`). | |
| `provider-languages` | RFC 5646 codes, comma-separated (for example `en_GB,en_IE`). | |
| `media` | `"true"` marks a VOD/media entry instead of a live channel. | Same as `#EXT-X-PLAYLIST-TYPE:VOD`. |
| `media-dir` | Virtual folder path for the media entry, `/`-separated. | |
| `media-size` | Size in bytes. | |
| `realtime` | `"false"` stops the entry from being treated as a live stream. | |

The **channel name** is the text after the comma that ends the attribute list. Names may contain
commas, so a parser should find the comma that follows the last closing quote, not the last comma
on the line.

### 1.4 `#EXTGRP`

`#EXTGRP:<group;group>` is a *begin* directive: every following channel gets these groups until an empty
`#EXTGRP` line, and any `group-title` attribute on an `#EXTINF` resets it.

Note: in the README example `#EXTGRP` appears *after* `#EXTINF` and before the URL, so it can be written
either way. The TS IPTV parser currently treats `#EXTGRP` as a per-channel value only.

### 1.5 `#KODIPROP`

`#KODIPROP:key=value`, one property per line, placed between `#EXTINF` and the URL. Kodi passes it
unchanged to the player or inputstream as a stream property. The key is lower-cased. `inputstreamaddon`
and `inputstreamclass` are rewritten to `inputstream`. Values are read up to the end of the line
(delimiters are not checked for KODIPROP), so JSON values are allowed.

Properties seen in IPTV lists and what they mean:

| Property | Values | Meaning |
|---|---|---|
| `inputstream` | `inputstream.adaptive`, `inputstream.ffmpegdirect`, `inputstream.ffmpeg` | Which demuxer add-on plays the stream. |
| `mimetype` | `application/dash+xml`, `application/vnd.apple.mpegurl`, `application/vnd.ms-sstr+xml`, `video/mp2t` | MIME type, so Kodi can skip content sniffing. |
| `inputstream.adaptive.manifest_type` | `mpd`, `hls`, `ism` | Mandatory until Kodi 20, deprecated in 21, **removed in Kodi 22** (auto-detected). |
| `inputstream.adaptive.license_type` | `com.widevine.alpha`, `com.microsoft.playready`, `com.huawei.wiseplay`, `org.w3.clearkey` | DRM key system (old method, deprecated in Kodi 22). |
| `inputstream.adaptive.license_key` | `URL\|headers\|body\|response` | License request template (old method). See section 4. |
| `inputstream.adaptive.drm_legacy` | `keysystem\|license URL or kid:key list\|headers` | Simple DRM config, Kodi 21+. |
| `inputstream.adaptive.drm` | JSON | Advanced DRM config, Kodi 22+. |
| `inputstream.adaptive.common_headers` | `name=urlenc&name=urlenc` | Headers for every request, Kodi 22+. |
| `inputstream.adaptive.manifest_headers` | same | Headers for manifest requests, Kodi 20+. |
| `inputstream.adaptive.stream_headers` | same | Headers for segment requests (Kodi 21+: segments only; before 21 also manifests). |
| `inputstream.adaptive.manifest_params`, `stream_params` | `k=v&k=v` | Query parameters added to manifest or segment URLs. |
| `inputstream.adaptive.play_timeshift_buffer` | `true`/`false` | Start live at the start of the DVR window. |
| `inputstream.adaptive.manifest_config` | JSON, for example `{"timeshift_bufferlimit":14400,"hls_ignore_endlist":true}` | Manifest quirks, Kodi 21+. |
| `inputstream.adaptive.config` | JSON, for example `{"ssl_verify_peer":false}` | Add-on behaviour, Kodi 21+. |
| `inputstream.ffmpegdirect.stream_mode` | `timeshift`, `catchup` | Enables local timeshift or catch-up mode in ffmpegdirect. |
| `inputstream.ffmpegdirect.is_realtime_stream` | `true` | Marks a live stream. |
| `inputstream.ffmpegdirect.manifest_type`, `open_mode`, `program_number`, `default_url`, `playback_as_live` | | ffmpegdirect options (listed in the README). |
| `http-reconnect` | `true` | ffmpeg HTTP reconnect for that stream. |
| `rtsp_transport` | `tcp` | RTSP transport, Kodi 20+ (Kodi wiki, `.strm` page). |

inputstream.adaptive warns in bold: **never** add headers to a manifest URL with the `|` pipe. Use
`*_headers` properties instead. IPTV lists do it anyway, so TS IPTV must accept both.

### 1.6 `#EXTVLCOPT` and the `|` URL suffix

`#EXTVLCOPT:key=value` is VLC's per-item option syntax. Kodi keeps only three keys
(source: `ParseSinglePropertyIntoChannel`):

- `http-user-agent` is turned into a `user-agent` header.
- `http-referrer` is turned into a `referer` header (the README says "referrer"; the key read is
  `http-referrer`, double r).
- `program` selects the MPEG-TS program number.

`#EXTVLCOPT--http-reconnect=true` (with a double dash) is also accepted.
Headers from `#EXTVLCOPT` are added to the URL only if the URL does not already set them.

**URL suffix syntax.** `<url>|name1=val1&name2=val2`. Kodi's special fields are `cookie`, `cookies`,
`seekable` and `user-agent`. It also accepts the standard headers `accept`, `accept-language`,
`authorization`, `origin`, `referer`, `x-forwarded-for` and others (the full list is in the README
appendix). Header names are case-insensitive, and values should be URL-encoded. Kodi does not send
non-standard headers unless they are prefixed with `!`: `|!X-Custom=1`.

Examples found in real lists (all of them should be accepted):

```
http://host/live.m3u8|User-Agent=Mozilla%2F5.0&Referer=https%3A%2F%2Fexample.com%2F
http://host/live.m3u8|user-agent=Mozilla/5.0 (Windows NT 10.0)
```

**Other markers**
- `#EXT-X-PLAYLIST-TYPE:VOD` inside a channel stanza marks it as VOD/media.
- `#WEBPROP:web-regex=...` / `#WEBPROP:web-headers=name:value&name:value`, together with a URL that
  starts with `@`, make Kodi scrape a web page for the real stream URL. **TS IPTV should not implement
  this.** It is a scraping feature and is often used to extract streams from pirate pages.

### 1.7 Catch-up modes and URL placeholders

Catch-up is enabled for a channel when it has a `catchup` (or `catchup-type`) attribute with a
valid mode, or when a `timeshift`/`tvg-rec` value is present. The source-code mode values
(case-insensitive) are listed below, and `ConfigureCatchupMode()` builds the template as follows:

| `catchup=` | How the catch-up URL template is built |
|---|---|
| `default` | Use `catchup-source` as the full URL. If it is empty, fall back to `append` behaviour. |
| `append` | `<channel URL>` + `catchup-source`. If `catchup-source` is empty, append the "Query format string" setting. |
| `shift` (SIPTV) | `<url>?utc={utc}&lutc={lutc}`, or `&utc=...` if the URL already has `?`. |
| legacy `timeshift="N"` | Same as `shift`, with N catch-up days. |
| `flussonic`, `flussonic-hls` | From `http(s)://host/<id>/<name>.m3u8?q`: `index.m3u8` gives `host/<id>/timeshift_rel-{offset:1}.m3u8?q`, otherwise `host/<id>/<name>-timeshift_rel-{offset:1}.m3u8?q` (for example `mono.m3u8` gives `mono-timeshift_rel-...`). |
| `flussonic-ts`, `fs` | From `http(s)://host/<id>/mpegts?q`: `host/<id>/timeshift_abs-${start}.ts?q`. |
| `xc` (Xtream Codes) | URL must match `^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/([^/.]+)(\.m3u8?)?$` (host, user, pass, id). Result: `host/timeshift/<user>/<pass>/{duration:60}/{Y}-{m}-{d}:{H}-{M}/<id>.ts` (or `.m3u8` if the live URL ended with `.m3u8`). |
| `vod` | Plays only `catchup-source`. If that is empty, the source is `{catchup-id}` (taken from XMLTV `catchup-id`). Days are ignored. |

If a `catchup-source` contains `|`, the headers after the pipe are kept on the generated URL.
Header or protocol options from the live URL are appended to the catch-up URL.

**Placeholders** (README "Catchup format specifiers"):

| Placeholder | Value |
|---|---|
| `{utc}` = `${start}` | Programme start, Unix seconds UTC. |
| `{utcend}` = `${end}` | Programme start + `{duration}`, Unix seconds. |
| `{lutc}` = `${now}` = `${timestamp}` | Current time, Unix seconds. These three are also allowed in the **live** URL. |
| `{Y}` `{m}` `{d}` `{H}` `{M}` `{S}` | Start time parts: 4-digit year, 01-12, 01-31, 00-23, 00-59, 00-59. |
| `{duration}` = `${duration}` | Programme duration in seconds, including configured start/end buffers. |
| `{duration:X}` | Duration divided by X (positive integer), for example `{duration:60}` gives minutes. |
| `{offset:X}` | (now - start) divided by X seconds. |
| `{catchup-id}` | Per-programme ID from the non-standard XMLTV attribute `<programme catchup-id="...">`. |
| `{utc:FMT}`, `${start:FMT}`, `${end:FMT}`, `{lutc:FMT}` ... | Formatted time. `FMT` is made of `Y m d H M S` plus literal characters, for example `{utc:YmdHM}` or `{utc:Ymd-H-M}`. |

Examples from the README: `&cutv={Y}-{m}-{d}T{H}:{M}:{S}` gives `&cutv=2019-11-26T22:00:32`;
`?start={utc:YmdHM}&end=${end:YmdHM}`; `?offset={offset:60}`.

`catchup-correction` (hours, decimal) is multiplied by 3600 and added to all times used for
substitution. The README says the `{Y}...{S}` and format-string values are rendered "in the current
locale". `CatchupController.cpp` confirms this: it builds them with `SafeLocaltime(...)`, which is
the **device's local time zone**, while `{utc}`, `{lutc}` and the others are Unix epoch seconds.
The source also accepts an undocumented `${offset}` (now - start, in seconds). **Decision for TS
IPTV:** match Kodi and use device local time for the calendar fields, because playlists are written
and tested against Kodi. Users can fix geo-mismatched servers with `catchup-correction`.

### 1.8 How XMLTV is linked and matched

- Guide URL: add-on setting, otherwise `x-tvg-url`, otherwise `url-tvg` in `#EXTM3U`.
- Gzip (`.xml.gz`) and XZ (`.xml.xz`) compressed XMLTV are both supported.
- Kodi matches each XMLTV `<channel>` to an M3U channel in three passes:
  1. XMLTV `<channel id>` == M3U `tvg-id` (case-insensitivity is a setting).
  2. Each `<display-name>` == `tvg-name`, as written or with spaces replaced by `_`.
  3. Each `<display-name>` == the M3U channel name.
- Programme elements read by Kodi: `title`, `desc`, `category` (the first one sets the genre),
  `sub-title`, `date`, `star-rating`, `episode-num` (`xmltv_ns` preferred, `onscreen` only as `S01E02`),
  `credits` (director/writer/actor), `icon`, plus the non-standard `catchup-id` attribute.
- EPG time shift = global setting + per-channel `tvg-shift` (hours).

### 1.9 Full reference example (from the README, trimmed)

```
#EXTM3U tvg-shift="-4.5" x-tvg-url="http://path-to-xmltv/guide.xml" catchup-correction="-2.5"
#EXTINF:0 tvg-id="channel-x" tvg-name="Channel_X" group-title="Entertainment" tvg-chno="10" tvg-logo="http://path-to-icons/channel-x.png" radio="true" tvg-shift="-3.5",Channel X
#EXTVLCOPT:program=745
#KODIPROP:key=val
http://path-to-stream/live/channel-x.ts
#EXTINF:0 tvg-id="channel-x" tvg-name="Channel-X-HD" group-title="Entertainment;HD Channels",Channel X HD
http://path-to-stream/live/channel-x-hd.ts
#EXTINF:0 tvg-id="channel-y" tvg-name="Channel_Y",Channel Y
#EXTGRP:Entertainment
http://path-to-stream/live/channel-y.ts
#EXTINF:0 catchup="default" catchup-source="http://path-to-stream/live/catchup-b.ts&cutv={Y}-{m}-{d}T{H}:{M}:{S}" catchup-days="3",Channel B
http://path-to-stream/live/channel-b.ts
#EXTINF:0 catchup="append" catchup-source="&cutv={Y}-{m}-{d}T{H}:{M}:{S}" catchup-days="3" catchup-correction="-4.0",Channel C
http://path-to-stream/live/channel-c.ts
#EXTINF:0 tvg-id="channel-d" catchup="shift" catchup-days="3",Channel D
http://path-to-stream/live/channel-d.ts
#EXTINF:-1 catchup="fs",Channel G
http://list.tv:8888/325/mono.m3u8?token=secret
#EXTINF:-1 catchup="xc",Channel I
http://list.tv:8080/live/my@account.xc/my_password/1477.m3u8
#EXTINF:-1 media="true",Channel M
http://path-to-stream/live/channel-m.mkv
```

A DRM example in the same dialect (from the inputstream.adaptive wiki):

```
#EXTINF:-1 tvg-id="demo",Demo DASH Widevine
#KODIPROP:inputstream=inputstream.adaptive
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=https://license.example.com/wv|User-Agent=Mozilla%2F5.0&Content-Type=application%2Foctet-stream|R{SSM}|R
#KODIPROP:mimetype=application/dash+xml
https://cdn.example.com/manifest.mpd
```

### 1.10 Gaps in the current TS IPTV M3U parser (`core/parser/iptv/m3u/M3UParser.kt`)

These were found while reading the parser against the spec above. They are listed here for planning,
not fixed.

1. The header reads only `url-tvg`. **`x-tvg-url` is ignored**, even though it is the attribute Kodi
   documents and the one iptv-org's public playlists use.
2. `#KODIPROP`, `#EXTVLCOPT` and `#EXT-X-PLAYLIST-TYPE` lines are skipped. They are not stored in
   `IPTVChannel.attributes`, so UA/Referer/DRM never reach the player.
3. The `|headers` suffix stays in `IPTVChannel.url`. Media3 would then request a URL containing `|`.
4. The attribute regex `(\w+(?:-\w+)?)` allows only one hyphen. `x-tvg-url`-style keys lose their prefix.
   `tvg-ID` is kept with different casing, so keys should be lower-cased.
5. `group-title` is not split on `;`. `#EXTGRP` is per-channel, not a begin directive.
6. The channel name is taken after the **last** comma, which breaks names that contain commas.
7. The `tvg-chno`, `radio` and `catchup*` attributes are captured in `attributes` but not used.
8. `MediaPlayerService.kt` applies a fixed `WIDEVINE_UUID` + `DRM_LICENSE_URL` whenever
   `isDrmProtected(url)` is true. Per-channel DRM from `#KODIPROP` would replace this (section 4.4).

---

## 2. `.strm` files and `.nfo` files

### 2.1 `.strm`

A `.strm` file is a plain text file with a `.strm` extension whose content is **one playable URL**
(`http://`, `https://`, `rtsp://`, `mms://`, and also `plugin://...`), optionally preceded by
`#KODIPROP:key=value` lines:

```
#KODIPROP:inputstream=inputstream.adaptive
#KODIPROP:mimetype=application/dash+xml
https://www.videoservice.com/manifest.mpd
```

- Kodi plays it like a local video file. When it is placed in a library source, the file name is
  used for scraping like any other video (for example `Movie Title (2020).strm`), and it can have
  local artwork and an `.nfo`.
- The inputstream.adaptive wiki uses the same `#KODIPROP` lines for "STRM playlist file" examples,
  including DRM.
- **What TS IPTV can do:** import `.strm` as a one-item playlist. Parse it with the M3U stanza logic
  (KODIPROP lines + URL + optional `|headers`), use the file name without extension as the title, and
  reject `plugin://` URLs with a clear message (see 3.4).

### 2.2 `.nfo` (brief)

`.nfo` is Kodi's local metadata file. It is **XML**, always UTF-8, named `<VideoFileName>.nfo`
(or `movie.nfo`/`tvshow.nfo`), with a root element such as `<movie>`, `<tvshow>` or
`<episodedetails>` and tags such as `<title>`, `<plot>`, `<year>`, `<uniqueid type="imdb">`, `<thumb>`.
Kodi reads it before any online scraper. It is a VOD-library concept. It has little value for a live
IPTV player, although it could later supply a title and poster for `.strm` VOD items. **Not a
priority.**

---

## 3. Kodi add-ons, repositories and `plugin://`

### 3.1 `addon.xml`

Every add-on is a folder (or zip) named after its ID with an `addon.xml` manifest at the root:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<addon id="plugin.video.example" name="Example" version="1.2.3" provider-name="Author">
  <requires>
    <import addon="xbmc.python" version="3.0.1"/>
  </requires>
  <extension point="xbmc.python.pluginsource" library="addon.py">
    <provides>video</provides>
  </extension>
  <extension point="xbmc.addon.metadata">
    <summary lang="en_GB">...</summary>
    <description lang="en_GB">...</description>
    <license>GPL-2.0-or-later</license>
    <platform>all</platform>
    <assets><icon>resources/icon.png</icon><fanart>resources/fanart.jpg</fanart></assets>
  </extension>
</addon>
```

- `<addon>` has four required attributes: `id` (lowercase, `.`, `_`, `-` and digits; also the folder
  name), `version` (`x.y.z`, `~beta` suffixes allowed), `name` and `provider-name`.
- `<requires>/<import addon version [optional]>` lists dependencies. `xbmc.python` 3.0.x means
  Kodi 19+ (Python 3).
- `<extension point="...">` declares what the add-on is. Examples: `xbmc.python.pluginsource`
  (a video, audio or image plugin; `<provides>` is a whitespace-separated list of
  `video audio image executable`), `xbmc.python.script`, `xbmc.addon.repository`, `xbmc.gui.skin`,
  `xbmc.metadata.scraper.*`, and binary points such as PVR clients and inputstreams.

### 3.2 Repositories: `repository.*`, `addons.xml`, checksum

A repository is itself an add-on (conventionally `repository.<name>`) that extends
`xbmc.addon.repository`:

```xml
<extension point="xbmc.addon.repository" name="Example Repo">
  <dir minversion="19.0.0">
    <info compressed="false">https://example.com/matrix/addons.xml</info>
    <checksum>https://example.com/matrix/addons.xml.md5</checksum>
    <datadir zip="true">https://example.com/matrix/</datadir>
    <hashes>sha256</hashes>
  </dir>
</extension>
```

- `addons.xml` is every add-on's `addon.xml` concatenated inside one `<addons>` root. It may be served
  gzip-encoded or pre-gzipped as `.gz`.
- `addons.xml.md5` is fetched first. If it changed, `addons.xml` is fetched again. The Kodi wiki says
  it is "not verified and not required to be a checksum", it only has to change when `addons.xml` changes.
- Zips live at `<datadir>/<addon.id>/<addon.id>-<x.y.z>.zip`. `<hashes>` (`sha256`/`sha512`/`sha1`/`md5`)
  makes Kodi compare a `content-<hash>` response header before downloading.
- `<dir minversion/maxversion>` serves different trees per Kodi version. Root-level `<info>` without
  `<dir>` was removed in Kodi 20.
- Installing a non-official repository requires the user to enable **Settings > System > Add-ons >
  Unknown sources**. The official repositories are compiled into Kodi.

### 3.3 `plugin://` URLs

`plugin://<addon.id>/<path>?<query>`, for example `plugin://plugin.video.myaddon/?mode=folder&foldername=Folder+One`.
When Kodi resolves the URL it **executes the add-on's Python entry point**
(`library="addon.py"`) with `sys.argv[0]` = the base URL, `sys.argv[1]` = an integer handle, and
`sys.argv[2]` = the query string. The script then calls Kodi-only APIs
(`xbmcplugin.addDirectoryItem`, `xbmcplugin.setResolvedUrl`, `xbmcgui.ListItem.setProperty(...)`,
`xbmcaddon.Addon().getSetting`) to return a directory listing or a playable URL with properties.
The plugin process ends after each listing. Its state lives in the URLs and in add-on settings.

### 3.4 Why add-ons cannot run outside Kodi

- They are Python programs, not data. "Kodi includes a built-in Python interpreter". Add-ons are
  "typically written in python" and depend on the `xbmc`, `xbmcgui`, `xbmcplugin`, `xbmcaddon` and
  `xbmcvfs` modules, which exist only inside Kodi's embedded interpreter (C++ bindings).
- Running them in TS IPTV would mean shipping a Python runtime plus a reimplementation of the Kodi
  Python API on Android, iOS and desktop. iOS App Store rules also forbid downloading and running
  code that changes app behaviour.
- The output of an add-on (the stream URL and its `ListItem` properties) is often computed at play
  time from a scraped web page or a signed token, so it cannot be pre-exported to a static list.
- Distributing add-ons or repositories would also make TS IPTV a distributor of whatever the add-on
  links to (see section 6).

### 3.5 What an external player can and cannot reuse

| Kodi artifact | Reuse in TS IPTV? | How |
|---|---|---|
| M3U with `tvg-*`, `group-title`, `catchup*`, `radio`, `tvg-chno` | **Yes** | Extend `M3UParser` (1.10). |
| `#KODIPROP` (inputstream.adaptive headers, DRM, mimetype) | **Yes** | Map to Media3 (section 4). Ignore `inputstream=` itself; it only selects Kodi's demuxer. |
| `#KODIPROP` ffmpegdirect timeshift properties | Partly | Treat as "live, timeshift allowed". Media3 has its own DVR window handling. |
| `#EXTVLCOPT` UA/Referer, `\|header` suffix | **Yes** | HTTP headers per channel. On desktop VLCJ, pass `:http-user-agent=` and `:http-referrer=` media options. |
| Catch-up modes + placeholders | **Yes** | Pure string templating plus the XMLTV programme times. |
| XMLTV incl. `catchup-id`, `.gz`/`.xz` | **Yes** (gz already supported) | Add XZ only if users need it. |
| `.strm` | **Yes** | One-item playlist. |
| `.nfo` | Low value | Optional metadata for `.strm` VOD. |
| `#WEBPROP` / `@url` page scraping | **No (by policy)** | Scraping tool, commonly used against pirate pages. |
| `addon.xml`, `addons.xml`, `repository.*`, zips | **No** | Python code, Kodi-only runtime, legal exposure. |
| `plugin://` URLs (in M3U, `.strm` or `catchup-source`) | **No** | Show "This entry needs a Kodi add-on and cannot be played in TS IPTV". |

---

## 4. inputstream.adaptive DRM and its mapping to Android Media3

### 4.1 Key systems

| DRM | Key system string | Media3 UUID constant | Media3 support (Android DRM page) |
|---|---|---|---|
| Widevine | `com.widevine.alpha` | `C.WIDEVINE_UUID` | "cenc" API 19+, "cbcs" API 25+; DASH, HLS (fMP4 only) |
| PlayReady | `com.microsoft.playready` (Android only in ISA) | `C.PLAYREADY_UUID` | SL2000, Android TV; DASH, SmoothStreaming, HLS (fMP4) |
| ClearKey | `org.w3.clearkey` (ISA Kodi 21+) | `C.CLEARKEY_UUID` | "cenc" API 21+; **DASH only** |
| WisePlay | `com.huawei.wiseplay` | none | not supported by Media3 |
| none (HLS AES-128) | `none` (Kodi 22) | not needed | Media3 handles `#EXT-X-KEY` AES-128 natively |

Some lists use informal values such as `license_type=clearkey` or `widevine`. The ISA source rejects
unknown key systems (`IsValidKeySystem`). TS IPTV can be lenient and map these aliases.

### 4.2 The three configuration syntaxes

**A. Old properties (Kodi 18-22, deprecated in 22, to be removed in 23)**

```
#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
#KODIPROP:inputstream.adaptive.license_key=<URL>|<Headers>|<Post-Data>|<Response-Data>
```

`license_key` has four fields separated by `|`, with no spaces:

1. **URL**: license server. It may contain `B{SSM}` (challenge, base64 + URL-encoded) or `{HASH}`
   (MD5 of the challenge) for GET-style servers.
2. **Headers**: `name=value&name=value`. ISA URL-encodes them automatically. Leave empty if none.
3. **Post-Data**: if present, a POST is made. It must contain `{SSM}` with a prefix: `R` raw,
   `b` base64, `B` base64 + URL-encoded, `D` decimal list. Optional `{SID}` (session ID) and
   `{KID}` (`R`/`H` hex), and `{PSSH}` (`b`/`B`). Most servers want `R{SSM}`.
4. **Response-Data**: `R` (or empty) raw bytes; `B` base64; `J<token>[;<hdcpToken>]` a JSON field
   holding the license; `JB<token>` a JSON field holding base64; `BJ<token>` base64-wrapped JSON.

Defaults when `license_key` is absent: Widevine `URL|Content-Type=application/octet-stream|R{SSM}|R`,
PlayReady uses `text/xml` + `SOAPAction`, WisePlay `application/json`.
`license_key` **cannot** configure ClearKey. The source logs an error and tells you to use
`drm_legacy` or `drm`. For HLS AES-128 the fields mean `<params to append to key URL>|<key headers>||`.

Related old properties: `license_data` (custom PSSH, base64), `server_certificate` (base64),
`license_flags` (`persistent_storage`, `force_secure_decoder`), and
`license_url` + `license_url_append` (Kodi 20-21 workaround for 1024-character truncation).

**B. `drm_legacy` (Kodi 21+)**

```
#KODIPROP:inputstream.adaptive.drm_legacy=<KeySystem>|<License URL or KID:KEY list>|<License headers>
```

- 1 to 3 fields. More than 3 is rejected as "Malformed value".
- Field 2 is treated as a license URL if it parses as a URL/URI (including
  `data:application/json;base64,...` for ClearKey). Otherwise it is parsed as ClearKey
  `kid1:key1,kid2:key2` pairs in **hex**.
- Field 3 is URL-encoded headers `a=b&c=d`.
- Examples:
  `com.widevine.alpha|https://lic.example.com/wv|User-Agent=Mozilla%2F5.0`,
  `org.w3.clearkey|000102030405060708090a0b0c0d0e0f:00112233445566778899aabbccddeeff`.

**C. `drm` JSON (Kodi 22+)**

```
#KODIPROP:inputstream.adaptive.drm={"com.widevine.alpha":{"license":{"server_url":"https://lic.example.com/wv","req_headers":"User-Agent=Mozilla%2F5.0"}}}
```

The top-level keys are key systems (or `none`). Per key system: `priority` (1 = highest),
`license.{server_url, server_certificate, use_http_get_request, req_headers, req_params, req_data,
wrapper, unwrapper, unwrapper_params, keyids}`, `init_data`, `pre_init_data`, `persistent_storage`,
`secure_decoder`, `force_single_session`, `optional_key_req_params`.
ClearKey: `"org.w3.clearkey":{"license":{"keyids":{"<KID hex>":"<KEY hex>"}}}` or
`{"license":{"server_url":"..."}}`.

### 4.3 Mapping to Media3

Media3 source read: `MediaItem.java`, `DefaultDrmSessionManagerProvider.java`,
`HttpMediaDrmCallback.java` and `LocalMediaDrmCallback.java` from `androidx/media` (`release` branch).

Relevant `MediaItem.DrmConfiguration.Builder` API:
`Builder(UUID scheme)`, `setLicenseUri(String/Uri)`, `setLicenseRequestHeaders(Map<String,String>)`,
`setMultiSession(bool)`, `setForceDefaultLicenseUri(bool)` ("always use the default DRM license
server URI even if the media specifies its own"), `setPlayClearContentWithoutKey(bool)` (default
true), `setForceSessionsForAudioAndVideoTracks(bool)`, `setKeySetId(byte[])`.

How the default path works: `DefaultDrmSessionManagerProvider` builds an `HttpMediaDrmCallback`
from `licenseUri` + `forceDefaultLicenseUri`, copies `licenseRequestHeaders` onto every key request,
and creates a `DefaultDrmSessionManager` for `scheme` with `FrameworkMediaDrm`.
`HttpMediaDrmCallback.executeKeyRequest` **always POSTs** the raw challenge (`request.getData()`)
with `Content-Type` = `application/octet-stream` (Widevine), `application/json` (ClearKey) or
`text/xml` + `SOAPAction` (PlayReady), and returns the raw response body. If the manifest carries
a license URL and `forceDefaultLicenseUri` is false, the manifest URL is used instead.

Mapping table:

| Kodi input | Media3 |
|---|---|
| `license_type` / `drm_legacy` field 1 / `drm` top-level key | `DrmConfiguration.Builder(uuid)` using 4.1 |
| `license_key` field 1, `drm_legacy` field 2 (URL), `drm.license.server_url` | `setLicenseUri(url)` + `setForceDefaultLicenseUri(true)` (Kodi's property overrides the manifest) |
| `license_key` field 2, `drm_legacy` field 3, `drm.license.req_headers` | URL-decode each `k=v` and pass to `setLicenseRequestHeaders(map)` |
| `license_key` field 3 = `R{SSM}` or empty, field 4 = `R` or empty | Default `HttpMediaDrmCallback`, no extra work |
| `license_key` field 3 other than `R{SSM}` (b/B/D prefixes, JSON bodies), field 4 = `B`/`J...`/`JB...`, `drm.license.req_data`/`wrapper`/`unwrapper`, `use_http_get_request`, `B{SSM}` in URL | **Custom `MediaDrmCallback`** that wraps the challenge and unwraps the response. Set it with `DefaultDrmSessionManager.Builder().setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER).build(callback)` and `mediaSourceFactory.setDrmSessionManagerProvider { manager }`. Phase 2. |
| `drm.force_single_session=false` / multi-key content | `setMultiSession(true)` |
| `server_certificate`, `license_data`/`init_data` (custom PSSH), `pre_init_data` | Not supported by the default path. Needs a custom `ExoMediaDrm` or session manager. Out of scope. |
| `license_flags=force_secure_decoder`, `drm.secure_decoder` | Nothing to set per item. Media3 picks a secure decoder when the CDM requires it. |
| ClearKey `kid:key` hex pairs (`drm_legacy` field 2, `drm.license.keyids`) | Convert to a W3C ClearKey JSON Web Key Set and use `LocalMediaDrmCallback(jsonBytes)` with `C.CLEARKEY_UUID` (see 4.4). |
| ClearKey `data:application/json;base64,<JWKS>` | Base64-decode the JWKS and use `LocalMediaDrmCallback`. |
| ClearKey license **URL** | `DrmConfiguration.Builder(C.CLEARKEY_UUID).setLicenseUri(url)` |
| `stream_headers` / `manifest_headers` / `common_headers` / `\|` URL suffix / `#EXTVLCOPT` | Not DRM. They become HTTP headers on the media `DataSource` (4.5). |
| `manifest_type` / `mimetype` | `MediaItem.Builder.setMimeType(MimeTypes.APPLICATION_MPD / APPLICATION_M3U8 / APPLICATION_SS)`, which is useful when the URL has no `.mpd`/`.m3u8` extension. |

### 4.4 ClearKey: concrete conversion

`LocalMediaDrmCallback` is documented as "A MediaDrmCallback that provides a fixed response to key
requests ... primarily useful for providing locally stored keys to decrypt ClearKey protected
content". The response it returns must be a W3C Clear Key license: a JSON Web Key Set with
`kty:"oct"`, and `kid` and `k` in **base64url without padding**. Media3's `ClearKeyUtil` already
converts between base64url and base64 for old CDMs.

Conversion from ISA hex pairs:

```
input : 000102030405060708090a0b0c0d0e0f:00112233445566778899aabbccddeeff
kid   = base64url(hexToBytes("000102030405060708090a0b0c0d0e0f"))  -> "AAECAwQFBgcICQoLDA0ODw"
k     = base64url(hexToBytes("00112233445566778899aabbccddeeff"))  -> "ABEiM0RVZneImaq7zN3u_w"
output: {"keys":[{"kty":"oct","kid":"AAECAwQFBgcICQoLDA0ODw","k":"ABEiM0RVZneImaq7zN3u_w"}],"type":"temporary"}
```

Sketch for the implementer (not repository code):

```kotlin
val drmManager = DefaultDrmSessionManager.Builder()
    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
    .build(LocalMediaDrmCallback(jwksJson.toByteArray(Charsets.UTF_8)))
val source = DashMediaSource.Factory(httpDataSourceFactory)
    .setDrmSessionManagerProvider { drmManager }
    .createMediaSource(MediaItem.fromUri(url))
```

Some IPTV lists put the KID in UUID form (`00010203-0405-...`). Strip the dashes before converting.
Keys are sometimes given as base64 already. Detect this with the regex `^[0-9a-fA-F]{32}$` and treat
anything that does not match as base64.

### 4.5 HTTP headers in Media3 (non-DRM)

There is no per-`MediaItem` header field in Media3. Options, in order of preference:

1. Build one `DefaultHttpDataSource.Factory().setUserAgent(ua).setDefaultRequestProperties(headers)`
   **per channel** when creating its `MediaSource`. This fits TS IPTV, which plays one item at a time.
2. A `ResolvingDataSource.Factory` that adds headers based on `DataSpec.uri`, for playlists that mix
   channels.

For the DRM license request, the default provider uses its own `DefaultHttpDataSource.Factory` with
the player user agent. Set `DefaultDrmSessionManagerProvider.setDrmHttpDataSourceFactory(...)` if the
license server also needs the channel's User-Agent or Referer.

### 4.6 Other platforms

- **iOS (AVPlayer):** Widevine, PlayReady and ClearKey-CENC are not available; only FairPlay is.
  Channels with `#KODIPROP` DRM should show "Not supported on this device" on iOS. HTTP headers can
  be passed with `AVURLAsset(url:options:["AVURLAssetHTTPHeaderFieldsKey": ...])`. This key is
  widely used but not documented by Apple; treat it as best effort.
- **Desktop (VLCJ):** there is no Widevine. Headers use the `:http-user-agent=` and `:http-referrer=`
  media options, the same keys as `#EXTVLCOPT`.

---

## 5. Recommended scope for TS IPTV

1. **M3U:** fully support the section 1 dialect except `#WEBPROP`/`@` and `plugin://`. Store the raw
   `#KODIPROP`/`#EXTVLCOPT` pairs and parsed headers in `IPTVChannel.attributes` (or new typed fields),
   and strip the `|` suffix from `url`.
2. **Headers:** a per-channel UA/Referer/header map, from (in priority order) the `|` suffix, then
   `#EXTVLCOPT`, then `inputstream.adaptive.*_headers`, then iptv-org `user_agent`/`referrer`.
3. **DRM phase 1:** Widevine and PlayReady with URL + headers (the default callback), and ClearKey
   with hex keys, data: URI or URL. Replace the fixed `DRM_LICENSE_URL` in `MediaPlayerService.kt`.
4. **DRM phase 2 (only if users need it):** custom callback for `license_key` wrappers and `drm` JSON
   `req_data`/`unwrapper`.
5. **Catch-up:** implement `default`, `append`, `shift`, `flussonic`/`fs`, `xc` and `vod` templating
   against XMLTV programme times.
6. **`.strm` import:** one-item playlist.
7. **Never:** add-on execution, repository browsing, bundled source lists, `#WEBPROP` scraping.

---

## 6. Legal and Google Play notes

- **Kodi's own position.** Kodi is legal open-source software. Team Kodi's forum *Piracy Policy*
  bans discussion of add-ons "that link directly to pirated content", and its "banned add-ons" page
  lists many third-party repositories, builds and wizards. It says the list is "only an example.
  Due to the dynamic nature of Piracy streams ... it is impossible to list all". Its rule of thumb:
  "if the Add-on is offering something for free that you would normally expect to pay for by any
  other means, then it'll most likely be using pirate feeds". Add-ons in the official repository
  have been checked against these rules; third-party repositories have not.
- **Google Play Intellectual Property policy.** Play does not allow apps that "induce or encourage
  copyright infringement". The listed violations include "Apps that encourage users to stream and
  download copyrighted works, including music and video, in violation of applicable copyright law".
  A player that ships, installs, links to or recommends third-party Kodi add-ons or repositories, or
  that ships a default channel catalogue from an unlicensed source, falls into this category.
- **Position for TS IPTV.**
  - Support **formats only**: M3U attributes, `#KODIPROP`/`#EXTVLCOPT`, `.strm`, DRM properties and
    XMLTV. Users bring their own playlists.
  - Do **not** ship, bundle, download, index, link to, or name third-party Kodi add-ons or
    repositories, in the app, the store listing, the docs or support replies.
  - Do not implement `plugin://` execution, add-on installation, repository parsing (`addons.xml`)
    or `#WEBPROP` page scraping.
  - Documentation examples must use placeholder hosts (`example.com`) or clearly licensed or public
    sources (for example iptv-org playlists, which take down channels on rights-holder request).
  - DRM support means playing streams the user is licensed to watch (for example an operator
    playlist). The app must not ship keys, or tools to obtain keys.

---

## Sources

All of these were read on 2026-09-27.

- PVR IPTV Simple Client README (Piers / Kodi 22; Omega identical):
  https://github.com/kodi-pvr/pvr.iptvsimple/blob/Piers/README.md
- pvr.iptvsimple source (marker aliases, catch-up generation):
  https://github.com/kodi-pvr/pvr.iptvsimple/blob/Piers/src/iptvsimple/PlaylistLoader.h ,
  https://github.com/kodi-pvr/pvr.iptvsimple/blob/Piers/src/iptvsimple/PlaylistLoader.cpp ,
  https://github.com/kodi-pvr/pvr.iptvsimple/blob/Piers/src/iptvsimple/data/Channel.cpp ,
  https://github.com/kodi-pvr/pvr.iptvsimple/blob/Piers/src/iptvsimple/CatchupController.cpp
- inputstream.adaptive wiki, Integration (properties, headers, pipe warning, STRM example):
  https://github.com/xbmc/inputstream.adaptive/wiki/Integration
- inputstream.adaptive wiki, Integration DRM (`drm_legacy`, `drm`, key systems, ClearKey):
  https://github.com/xbmc/inputstream.adaptive/wiki/Integration-DRM
- inputstream.adaptive wiki, Integration DRM (old) (`license_type`, `license_key` 4-field template):
  https://github.com/xbmc/inputstream.adaptive/wiki/Integration-DRM-(old)
- inputstream.adaptive source, property parsing (`drm_legacy` split, ClearKey vs `license_key`):
  https://github.com/xbmc/inputstream.adaptive/blob/Piers/src/CompKodiProps.cpp
- Kodi wiki (read through the MediaWiki API because the HTML is behind a bot wall):
  Internet video and audio streams (`.strm`): https://kodi.wiki/view/Internet_video_and_audio_streams ;
  Addon.xml: https://kodi.wiki/view/Addon.xml ;
  Add-on repositories: https://kodi.wiki/view/Add-on_repositories ;
  Plugin sources: https://kodi.wiki/view/Plugin_sources ;
  Audio-video add-on tutorial (`sys.argv`, `plugin://`): https://kodi.wiki/view/Audio-video_add-on_tutorial ;
  About Add-ons: https://kodi.wiki/view/Python_development ;
  NFO files: https://kodi.wiki/view/NFO_files and https://kodi.wiki/view/NFO_files/Movies ;
  Official add-on repository: https://kodi.wiki/view/Official_add-on_repository ;
  Forum rules (Piracy Policy): https://kodi.wiki/view/Official:Forum_rules ;
  Banned add-ons: https://kodi.wiki/view/Official:Forum_rules/Banned_add-ons
- Android Media3 DRM guide: https://developer.android.com/media/media3/exoplayer/drm
- Media3 source (`release` branch):
  https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/MediaItem.java ,
  https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/drm/DefaultDrmSessionManagerProvider.java ,
  https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/drm/HttpMediaDrmCallback.java ,
  https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/drm/LocalMediaDrmCallback.java ,
  https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/drm/ClearKeyUtil.java
- W3C Encrypted Media Extensions, Clear Key license format:
  https://www.w3.org/TR/encrypted-media/#clear-key-license-format
- Google Play Intellectual Property policy:
  https://support.google.com/googleplay/android-developer/answer/9888072
