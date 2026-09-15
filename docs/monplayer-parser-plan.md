# Plan — MonPlayer playlist format support

**Goal:** let a user import a playlist that is in MonPlayer's format, the same
way they can already import M3U, XSPF, JSON and iptv-org playlists.

**Explicitly out of scope:** reverse-engineering the MonPlayer app or calling its
backend to pull its channel catalogue. See "Scope boundary" at the bottom — this
matters for the Play Console submission, so please read it before implementing.

---

## Blocked on one input

I need **one real MonPlayer playlist file** (or the format's documentation)
before this can be implemented. Everything below is designed around the codebase
as it stands; only the schema mapping in step 2 depends on the sample.

Please drop a sample at:

```
composeApp/src/commonTest/kotlin/assests/monplayer-sample.json
```

and say which of these it is:

- a JSON document with a channel array,
- an M3U file with MonPlayer-specific `#EXT` directives,
- or something else entirely.

If the playlist is served from a URL rather than saved as a file, the URL and one
captured response body work equally well.

---

## How the existing parser layer works

Understanding this makes the change small. Three pieces:

| File | Role |
| --- | --- |
| `core/parser/IPTVParser.kt` | The interface: `parse(content): IPTVPlaylist` + `getSupportedFormat()` |
| `core/parser/IPTVParserFactory.kt` | `detectFormat(content)` sniffs the text, `createParser(format)` builds the right parser |
| `core/parser/model/IPTVFormat.kt` | The format enum |

Callers never name a parser directly. They call
`IPTVParserFactory.createParserForContent(content)`, which sniffs and dispatches.
Two call sites matter:

- `ui/screens/home/HomeViewModel.kt:130` and `:268` — playlist import
- `core/database/RoomIPTVDatabase.kt:153` — refresh of a stored playlist

So a new parser becomes available everywhere as soon as `detectFormat` can
recognise it. **No UI change is needed.**

---

## Implementation steps

### 1. Add the format

`core/parser/model/IPTVFormat.kt`:

```kotlin
enum class IPTVFormat {
    M3U,
    XML,
    JSON,
    JSON_IPTV_ORG,
    JSON_MONPLAYER,   // new
    XSPF,
    UNKNOWN
}
```

### 2. Add the parser — *needs the sample*

New file `core/parser/iptv/monplayer/MonPlayerParser.kt`, following the shape of
`iptv/iptvorg/IptvOrgParser.kt`:

```kotlin
class MonPlayerParser : IPTVParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override fun parse(content: String): IPTVPlaylist {
        val dto = try {
            json.decodeFromString<MonPlayerPlaylistDTO>(content)
        } catch (e: Exception) {
            throw IPTVParserException("Failed to parse MonPlayer playlist: ${e.message}")
        }
        return dto.toIPTVPlaylist()
    }

    override fun getSupportedFormat() = IPTVFormat.JSON_MONPLAYER
}
```

with DTOs in `core/parser/iptv/monplayer/models/MonPlayerDTO.kt` mapping onto the
existing `IPTVChannel` / `IPTVGroup` / `IPTVPlaylist` models.

Two lessons from the bugs just fixed in the JSON and XMLTV parsers — apply both
here:

1. **`ignoreUnknownKeys = true` hides schema mismatches.** A document whose
   channel array is under an unexpected key decodes "successfully" into an empty
   playlist. Use `@JsonNames` for every plausible key spelling, and make the
   parser throw rather than return an empty playlist when the document is
   recognisably MonPlayer but yields zero channels.
2. **Never assume one time zone.** If MonPlayer carries schedule data, reuse
   `XMLTVEPGParser.parseXMLTVDateTime` or write an equivalent that honours the
   offset present in the data.

### 3. Teach `detectFormat` to recognise it

`IPTVParserFactory.detectFormat` currently sniffs on prefixes. The MonPlayer
check must run **before** the generic `startsWith("{") -> JSON` branch, or JSON
will swallow it:

```kotlin
content.trimStart().startsWith("{") &&
    MON_PLAYER_MARKERS.any { it in content } -> IPTVFormat.JSON_MONPLAYER

content.trimStart().startsWith("{") -> IPTVFormat.JSON
```

`MON_PLAYER_MARKERS` should be the smallest set of strings that appear in every
MonPlayer playlist and in nothing else — a schema version field, a namespace, a
distinctive key name. The sample will show which.

Then add the branch to `createParser`:

```kotlin
IPTVFormat.JSON_MONPLAYER -> MonPlayerParser()
```

### 4. Tests

New `commonTest/.../core/parser/MonPlayerParserTest.kt`, mirroring
`JSONParserTest.kt`. Load the sample through the `TestAssets` helper added
alongside the recent parser fixes:

```kotlin
val content = TestAssets.read("monplayer-sample.json")
```

Cover:

- a real sample parses to the expected channel and group counts;
- `IPTVParserFactory.detectFormat(sample)` returns `JSON_MONPLAYER` — this is
  the regression test that stops the generic JSON branch stealing it back;
- a MonPlayer document with an empty channel list throws, rather than silently
  producing an empty playlist;
- a plain JSON playlist is still detected as `JSON`, not MonPlayer.

### 5. Documentation

Add the format to the supported-formats lists in `README.md`, in both store
listing files under `play-store/`, and on the landing page in
`web/public/index.html` (both language panes).

---

## Estimate

| Step | Effort |
| --- | --- |
| 1. Format enum | trivial |
| 2. Parser + DTOs | ~1–2 h once the sample exists |
| 3. Detection | ~30 min |
| 4. Tests | ~1 h |
| 5. Docs | ~15 min |

No new dependencies; kotlinx.serialization is already in `commonMain`.

---

## Scope boundary

MonPlayer (`org.monplayer.mpapp`) is described by Vietnamese press — Znews and
VnReview among others — as the app arm of the Xôi Lạc TV network, which
redistributes sports and television broadcasts without rights.

That has a direct consequence for this repository:

- **Parsing the file format is fine.** A format is not content. Reading someone
  else's playlist file is the same kind of work as reading M3U or XSPF, and it is
  what the user brings to the app that determines legality.
- **Pulling MonPlayer's catalogue into TS IPTV is not.** Shipping an app that
  reaches a known infringing catalogue would breach the Play Store's
  Intellectual Property policy. The realistic outcome is not a rejected release;
  it is a removed app and a terminated developer account, and it would take the
  whole Play submission being prepared in `play-store/` down with it.

So: implement step 2 against a file the user supplies, and do not add a built-in
source list, a default MonPlayer URL, or a catalogue fetcher. That keeps TS IPTV
in the same position as VLC — a player, with content responsibility resting on
the person who adds the source. The store listing copy and the Terms of Use page
already say exactly that, which is the line to hold if a reviewer asks.
