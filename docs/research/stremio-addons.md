# Stremio addons: protocol research for TS IPTV

Status: research, 2026-09-27. Audience: TS IPTV engineers who will build Stremio addon support into the KMP client (Android phone/TV, iOS, desktop).
Scope: the HTTP addon protocol as specified by the official SDK docs and as actually implemented by the official client engine (`stremio-core`), plus how to author an addon, what we can play, and legal/policy constraints.

Everything below is from primary sources that were read in full for this document (list at the end). Where the spec text and the reference client disagree, both are stated and the client behaviour is marked **[core]**. Anything that is our own inference rather than a documented rule is marked **[inference]**. Live endpoints quoted here were fetched on 2026-09-27.

---

## 0. TL;DR

- An addon is **a set of static-looking JSON GET endpoints** under a base URL. The user gives us a URL ending in `/manifest.json`. We strip that suffix to get the base, then request `/{resource}/{type}/{id}.json` or `/{resource}/{type}/{id}/{extra}.json`.
- Resources: `catalog` (lists), `meta` (details and episode list), `stream` (playable sources), `subtitles`, `addon_catalog` (lists of other addons).
- Path segments are percent-encoded with the `encodeURIComponent` character set, so `tt0108778:1:1` goes on the wire as `tt0108778%3A1%3A1`. `extra` is `k=v&k=v` with the same encoding: space becomes `%20`, not `+`.
- Pages hold 100 catalog items. Fewer than 100 items means the catalog has ended. The next page is requested with `skip=100`, `skip=200`, and so on.
- Stream kinds are `url` (HTTP(S)/HLS/DASH/RTMP), `ytId` (YouTube), `infoHash` (BitTorrent), `nzbUrl`/archive URLs (Usenet/archives) and `externalUrl` (open in a browser). **TS IPTV should support only `url` (and optionally open `externalUrl` in the browser). No torrents, no Usenet, no YouTube extraction.**
- `behaviorHints.proxyHeaders.request` lists HTTP headers that must be sent to the media server, for example `User-Agent` or `Referer`. A native player should apply them. `notWebReady` only matters for browser playback.
- CORS (`Access-Control-Allow-Origin: *`) is required of addons. Native clients don't need it. It matters only if we ever ship a web/wasm target.
- Legal: Stremio's own ToS separates "Official Addons" from "Community Addons" and disclaims all liability for the latter. Most popular community stream addons scrape torrents of copyrighted works. **TS IPTV should ship with zero pre-installed third-party addon URLs, must not browse or promote the community collection, and should label added addons as user-provided.**

---

## 1. Transport

### 1.1 Transport URL, base URL

The addon is identified by its **transport URL**, the full URL of its manifest ([protocol.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md)):

| URL shape | Transport | TS IPTV |
|---|---|---|
| `https://…/manifest.json` (or `http://`) | HTTP transport | **Support** |
| `https://…/stremio/v1` | Legacy v1/v2 protocol (JSON-RPC era). Still used by the old official OpenSubtitles addon `https://opensubtitles.strem.io/stremio/v1` | Reject: "unsupported legacy addon" |
| `ipfs://…/manifest.json`, `ipns://…/manifest.json` | IPFS | Reject |

How the base URL is derived **[core]** (`AddonHTTPTransport::resource` in [stremio-core http_transport.rs](https://github.com/Stremio/stremio-core/blob/development/src/addon_transport/http_transport/http_transport.rs)):

- The transport URL's *path* must end with `/manifest.json`, otherwise the request fails with "addon http transport url must ends with /manifest.json".
- The resource URL is built by string replacement: `transportUrl.replace("/manifest.json", "/{resource}/{type}/{id}.json")`. Any **query string on the manifest URL is therefore kept** and ends up after the `.json` of every resource URL.
- Everything before `/manifest.json` is kept verbatim. Configured addons put user settings there (see 1.7), e.g. `https://host/eyJ0b2tlbiI6IngifQ/manifest.json` gives the base `https://host/eyJ0b2tlbiI6IngifQ`.

Recommended implementation: parse the URL, require `path.endsWith("/manifest.json")`, set `basePath = path.removeSuffix("/manifest.json")`, and build `scheme://host[:port]{basePath}/{resource}/{type}/{id}[/{extra}].json[?{originalQuery}]`. Only replace the *last* occurrence. Core replaces all occurrences, which is a latent bug that doesn't matter in practice.

The SDK README says: "Addon URLs must be served over HTTPS with CORS enabled, except for `127.0.0.1`" ([SDK README](https://github.com/Stremio/stremio-addon-sdk/blob/master/README.md)). The official "Local Files" addon is `http://127.0.0.1:11470/local-addon/manifest.json` and depends on Stremio's local streaming server. We should skip it.

### 1.2 Resource URL patterns

From [protocol.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md) and the SDK router ([getRouter.js](https://github.com/Stremio/stremio-addon-sdk/blob/master/src/getRouter.js)):

```
GET {base}/manifest.json
GET {base}/{resource}/{type}/{id}.json
GET {base}/{resource}/{type}/{id}/{extra}.json
```

| Resource | `type` | `id` | Response root |
|---|---|---|---|
| `catalog` | catalog's `type` | catalog `id` from manifest | `{ "metas": [MetaPreview] }` (EPG: `{ "metasDetailed": [Meta] }`) |
| `meta` | item type | meta item id (from catalog) | `{ "meta": Meta }` |
| `stream` | item type | **video id** (for a movie this equals the meta id; for series `tt…:S:E`; for `tv` the **channel** id) | `{ "streams": [Stream] }` |
| `subtitles` | item type | video id (see 3.6 for the history) | `{ "subtitles": [Subtitle] }` |
| `addon_catalog` | addonCatalog `type` | addonCatalog `id` | `{ "addons": [AddonDescriptor] }` |

The SDK router only answers resource names that appear in the manifest (regex `(catalog|meta|…)`). Anything else returns 404 `{"err":"not found"}`. A handler exception returns **500** `{"err":"handler error"}`. A handler may also return `{redirect: url}`, which gives a **307** redirect, so our HTTP client must follow redirects.

### 1.3 Percent-encoding of path segments and `extra` **[core]**

Encoding set (`URI_COMPONENT_ENCODE_SET` in [stremio-core constants.rs](https://github.com/Stremio/stremio-core/blob/development/src/constants.rs)): every byte except ASCII alphanumerics and `- _ . ! ~ * ' ( )` is percent-encoded as UTF-8. This is exactly JavaScript `encodeURIComponent`.

- `resource`, `type` and `id` are each encoded with that set. So `yt_id:UCrDkAvwZum-UTjHmzDI2iIw` goes out as `yt_id%3AUCrDkAvwZum-UTjHmzDI2iIw`, and `tt0108778:1:1` as `tt0108778%3A1%3A1`. Servers decode the path before routing, so both forms work against SDK/Express servers.
- `extra` is built by `query_params_encode`: `enc(name) + "=" + enc(value)`, joined with `&`, **in the order the client holds them**, with no sorting. Example from protocol.md: `search=game%20of%20thrones&skip=100`.
  - Space is `%20`. Never use `+`. Node's `querystring.parse` on the server would decode `+` as a space, but static hosts would look for a literal `+`.
  - A literal `&` or `=` inside a value is encoded (`%26`, `%3D`). The SDK router deliberately parses `extra` from the *raw* `req.url` so that an encoded `%26` doesn't split the parameter.
  - **Multi-value** extras (`optionsLimit > 1`) repeat the key: `genre=Action&genre=Comedy` **[core]** (`ExtraExt::extend_one`). The server gets an array.
  - Cinemeta's `lastVideosIds`/`calendarVideosIds` pass many ids as one comma-joined value. Core sorts them first "to improve caching".
- Worked examples (verified live against Cinemeta):
  - `https://v3-cinemeta.strem.io/catalog/movie/top/genre=Comedy&skip=100.json`
  - `https://v3-cinemeta.strem.io/catalog/movie/top/search=big%20buck%20bunny.json`

Recommendation: put extras in a stable order, e.g. the order they are declared in the manifest's `extra[]`, so HTTP/CDN caches get hits. Omit `extra` entirely (no trailing `/`) when there are no values.

### 1.4 Caching headers

- Handlers can return `cacheMaxAge`, `staleRevalidate` and `staleError` (integers, **seconds**) next to the payload. The SDK turns them into `Cache-Control: max-age=N, stale-while-revalidate=N, stale-if-error=N, public` and **also leaves the fields in the JSON body** ([getRouter.js](https://github.com/Stremio/stremio-addon-sdk/blob/master/src/getRouter.js); request docs such as [defineStreamHandler.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/defineStreamHandler.md)). Observed on the Public Domain Movies stream response: `{"streams":[…],"cacheMaxAge":604800,"staleRevalidate":7776000,"staleError":31104000}` together with the matching `Cache-Control` header.
- `serveHTTP(…, { cacheMaxAge })` sets a global default ([serveHTTP.js](https://github.com/Stremio/stremio-addon-sdk/blob/master/src/serveHTTP.js)).
- Observed: Cinemeta manifest `public, max-age=10800`, Cinemeta search `max-age=86400`, GitHub Pages `max-age=600`, `api.strem.io/addonscollection.json` `max-age=1200`.
- EPG guidance ([epg.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/epg.md)): programme data `cacheMaxAge: 300`, `staleRevalidate: 1800`, `staleError: 604800`.
- Client rule for TS IPTV: honour `Cache-Control` (or the body fields when the header is missing). Serve stale data on network error within `stale-if-error`. **Don't cache stream responses for long** when the stream URLs look signed or tokenised [inference]. Cap stream-response caching at a few minutes whatever the addon says.

### 1.5 CORS

"For the HTTP transport, each route, including `/manifest.json`, must serve CORS headers that allow all origins" ([protocol.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md)). The SDK adds the `cors()` middleware to every route, with the comment "CORS is mandatory for the addon protocol". The static example sets `Access-Control-Allow-Origin: *` via host config ([now.json](https://github.com/Stremio/stremio-static-addon-example/blob/master/now.json)). GitHub Pages sends `Access-Control-Allow-Origin: *` by default, verified on `https://stremio.github.io/stremio-static-addon-example/manifest.json`.

For TS IPTV: native Ktor/OkHttp/NSURLSession clients ignore CORS, so a missing header must **not** be treated as an error. The requirement matters only for addon *authors* whose addon should also work in Stremio Web.

### 1.6 `stremio://` links

From [deep-links.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/deep-links.md) and [meta.links.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/meta.links.md):

- **Install link:** `stremio://host/path/manifest.json` is the manifest URL with `https://` replaced by `stremio://`. Addon install pages link their "Install" button to this. TS IPTV should accept pasted `stremio://…` input and rewrite the scheme to `https://`, which covers every documented case (use `http://` only for `127.0.0.1`/LAN hosts). We may also register an intent filter or URL scheme for `stremio` [inference]. That would compete with the real Stremio app, so it is probably better not to.
- **Page links** (three slashes, no host), used inside `meta.links[].url` and `externalUrl`:
  - `stremio:///search?search={query}`
  - `stremio:///discover/{urlEncodedTransportUrl}/{type}/{catalogId}?{extra}`. Cinemeta genre links use exactly this, e.g. `stremio:///discover/https%3A%2F%2Fv3-cinemeta.strem.io%2Fmanifest.json/series/top?genre=Comedy`.
  - `stremio:///detail/{type}/{id}` and `stremio:///detail/{type}/{id}/{videoId}` (optional `?autoPlay=true`)
  - `stremio:///board`, `stremio:///library`, `stremio:///discover`
  - TS IPTV: map `discover` links to our catalog screen for that addon, catalog and extra, `detail` links to our detail screen, and `search` to our search. Ignore the rest.

### 1.7 User configuration: `configurable`, `configurationRequired`, `config`

From [manifest.md → User data](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md#user-data), [advanced.md → Using User Data](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/advanced.md#using-user-data-in-addons) and [getRouter.js](https://github.com/Stremio/stremio-addon-sdk/blob/master/src/getRouter.js):

- User data lives **in the transport URL path**, before `/manifest.json`, e.g. `https://www.mydomain.com/c9y2kz0c26c3w4csaqne71eu4jqko7e1/manifest.json`. Every resource request carries it automatically because it is part of the base.
- With the SDK, the segment is a URL-encoded **JSON object** (`/:config?/manifest.json`, parsed with `JSON.parse`). Hand-written addons may use any opaque token.
- `behaviorHints.configurable: true`: Stremio shows a "Configure" button that opens `{base}/configure` in a browser.
- `behaviorHints.configurationRequired: true`: Stremio hides "Install" and shows only "Configure". The addon doesn't work without user data.
- When the SDK serves the manifest *under a config prefix*, it **deletes** `configurable` and `configurationRequired` from the served manifest, so the configured URL is installable.
- `manifest.config[]` (SDK-generated config page): `{ key (req), type: "text"|"number"|"password"|"checkbox"|"select" (req), default?, title?, options? (for select), required? }`.

TS IPTV behaviour: if a fetched manifest has `configurationRequired: true`, don't install it. Show "This addon must be configured first", open `{base}/configure` in the system browser, and ask the user to paste the resulting manifest URL. The configure page normally shows a `stremio://` link or a copyable URL. If `configurable: true`, show an optional "Configure" action with the same flow. Configured URLs often embed secrets (API keys, debrid tokens), so store them encrypted and never log them [inference].

---

## 2. Manifest

Source: [manifest.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md), the client-side parsing in [stremio-core manifest.rs](https://github.com/Stremio/stremio-core/blob/development/src/types/addon/manifest.rs), and TypeScript types in [types.d.ts](https://github.com/Stremio/stremio-addon-sdk/blob/master/src/types.d.ts).

| Field | Required | Type | Notes |
|---|---|---|---|
| `id` | yes | string | Dot-separated reverse-DNS, e.g. `com.stremio.filmon`. Use as the dedupe key; installing the same `id` again means an update. |
| `version` | yes | string (semver) | Core parses it with `semver` and rejects the manifest if it's invalid. We should be lenient and keep it as a string. |
| `name` | yes | string | |
| `description` | yes (spec) / optional (core) | string | |
| `types` | yes | string[] | Supported content types (section 4). Core: an empty list means no types are supported. |
| `resources` | yes | (string \| object)[] | `"catalog"`, `"meta"`, `"stream"`, `"subtitles"`, `"addon_catalog"`, or `{ "name": "stream", "types": ["movie"], "idPrefixes": ["tt"] }`. |
| `idPrefixes` | optional | string[] | Only ids starting with one of these are sent to the addon, e.g. `["tt"]`, `["yt_id:"]`. |
| `catalogs` | yes (may be `[]`) | Catalog[] | Core defaults to `[]` if the field is missing. Duplicate `(id,type)` pairs are dropped. |
| `addonCatalogs` | optional | Catalog[] (`type`, `id`, `name`) | For addons that list other addons. |
| `behaviorHints` | optional | object | `adult`, `p2p`, `configurable`, `configurationRequired`, `epgProvider`, all booleans defaulting to `false`. Cinemeta also sends a non-spec `newEpisodeNotifications`. |
| `config` | optional | Config[] | See 1.7. |
| `logo` | optional | URL | Monochrome PNG, 256×256. Core ignores an invalid or empty value. |
| `background` | optional | URL | PNG/JPG, at least 1024×786. |
| `contactEmail` | optional | string | Used for the "Report" button. |

The SDK refuses manifests larger than **8 KiB** ("incompatible with addonCollection API", [builder.js](https://github.com/Stremio/stremio-addon-sdk/blob/master/src/builder.js)). Hand-written manifests (Cinemeta is about 6.4 KB) can be larger, so don't enforce this limit as a client.

### 2.1 Catalog object

| Field | Required | Notes |
|---|---|---|
| `type` | yes | Content type of the items |
| `id` | yes | Unique per addon *per type*. The same `id` may appear with different `type`s, e.g. Cinemeta `top` for `movie` and for `series`. Key catalogs by `(type,id)`. |
| `name` | yes (spec), optional (core) | Display name. Fall back to `id`. |
| `extra` | optional | `[{ name, isRequired?, options?, optionsLimit? }]` |

Extra property:

- `name` (required). Well-known names are `search`, `genre`, `skip` and `date` (EPG). Addons may define their own, such as Cinemeta's `lastVideosIds` and `calendarVideosIds`.
- `isRequired` (default `false`). If `true`, the catalog must never be requested without it. `search` with `isRequired: true` means a **search-only** catalog, never shown on the home board.
- `options` (string[]). The preset values, e.g. the list of genres.
- `optionsLimit` (default **1**). The maximum number of values that can be selected at the same time.

**Legacy "short" form** (old docs [manifest.md@b11bd51](https://github.com/Stremio/stremio-addon-sdk/blob/b11bd517f8ce3b24a843de320ec8ac193611e9a0/docs/api/responses/manifest.md#catalog-format)): `extraSupported: string[]`, `extraRequired: string[]`, where `extraRequired` must be a subset of `extraSupported`. Options came from a separate `genres: string[]` field. Core deserialises catalogs as an untagged enum: if `extra` is present it wins, otherwise it falls back to `extraSupported`/`extraRequired` with `options=[]` and `optionsLimit=1`. **Cinemeta still sends both**, e.g. `"extra":[…], "extraSupported":["search","genre","skip"], "genres":[…]`. Implement the same precedence: `extra` → else `extraSupported`/`extraRequired` (+ `genres` as options for `genre`, our own fallback [inference]).

Core normalises every `skip` extra to `{name:"skip", isRequired:false, optionsLimit:1}` and ignores any `options` on it ([manifest.rs](https://github.com/Stremio/stremio-core/blob/development/src/types/addon/manifest.rs) `ExtraPropValid`).

### 2.2 Resource matching: which addon gets which request **[core]**

From `Manifest::is_resource_supported` ([manifest.rs](https://github.com/Stremio/stremio-core/blob/development/src/types/addon/manifest.rs)) and [docs/api/README.md → Filtering](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/README.md#filtering):

- **catalog / addon_catalog**: there must be an entry in `catalogs` / `addonCatalogs` with the same `type` and `id`. Every extra we send must be declared, and every `isRequired` extra must be present. `resources`/`idPrefixes` do **not** gate catalogs. The doc says "`idPrefixes` filtering does not matter for the `catalog` resource". Many manifests omit `"catalog"` from `resources` and still get catalog requests. Treat a non-empty `catalogs` as implying the catalog resource, like the SDK does.
- **meta / stream / subtitles**: find the resource entry by name.
  - String form: use `manifest.types` and `manifest.idPrefixes`.
  - Object form: use *its* `types` (if absent, **no type is supported**) and *its* `idPrefixes` (if absent, any id). Core doesn't fall back to the manifest-level `idPrefixes` for the object form, although the doc comment says it should be a subset.
  - `idPrefixes` missing or empty means every id matches. Otherwise `id.startsWith(anyPrefix)`.
- **Aggregation**: Stremio asks **every** installed addon that matches and merges the results, e.g. metadata from Cinemeta plus streams from N stream addons for the same `tt` id. TS IPTV should do the same: stream requests fan out in parallel to every matching addon and the results are grouped by addon.
- **Home/board**: load catalogs that have no required extras. If a required extra has `options`, core uses the **first option** as the default (`default_required_extra`). That's how Cinemeta's "New" catalog, which requires `genre` from a list of years, appears on the board. **Search**: query every catalog that declares a `search` extra (required or not).

### 2.3 Manifest example (official Cinemeta, abridged, live)

```json
{
  "id": "com.linvo.cinemeta", "version": "3.0.14", "name": "Cinemeta",
  "description": "The official addon for movie and series catalogs",
  "resources": ["catalog", "meta", "addon_catalog"],
  "types": ["movie", "series"], "idPrefixes": ["tt"],
  "catalogs": [
    { "type": "movie", "id": "top", "name": "Popular",
      "extra": [ {"name":"genre","options":["Action","Comedy","…"]}, {"name":"search"}, {"name":"skip"} ],
      "extraSupported": ["search","genre","skip"] },
    { "type": "movie", "id": "year", "name": "New",
      "extra": [ {"name":"genre","options":["2026","2025","…"],"isRequired":true}, {"name":"skip"} ] },
    { "type": "series", "id": "last-videos", "name": "Last videos",
      "extra": [ {"name":"lastVideosIds","isRequired":true,"optionsLimit":100} ] }
  ],
  "addonCatalogs": [ {"type":"all","id":"official","name":"Official"}, {"type":"all","id":"community","name":"Community"} ],
  "behaviorHints": { "newEpisodeNotifications": true }
}
```

---

## 3. Responses

### 3.1 Catalog: `{ "metas": [MetaPreview] }`

Sources: [defineCatalogHandler.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/defineCatalogHandler.md), [advanced.md → Understanding Catalogs](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/advanced.md#understanding-catalogs).

Extras:

- `search`: the free-text query string.
- `genre`: filters the feed or the search results. Values come from `options`.
- `skip`: pagination. "the standard page size in Stremio is 100, so the `skip` value will be a multiple of 100; if you return less than 100 items, Stremio will consider this to be the end of the catalog". Core constant `CATALOG_PAGE_SIZE = 100`. The first page is requested **without** `skip`. Following pages use `skip=100`, `skip=200`, …. Only send `skip` if the catalog declares it. Note that the SDK's own advanced.md example slices pages of 20, which contradicts the rule. With such an addon, a strict client stops after the first page. Be lenient: stop only on an **empty** page or a page that repeats ids already seen, and use `skip = itemsReceivedSoFar` [inference, matches how EPG paging is described].
- `date`: EPG only (3.8).

**MetaPreview** ([meta.md → Meta Preview Object](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/meta.md#meta-preview-object)):

| Field | Req. | Notes |
|---|---|---|
| `id` | yes | Addon-prefixed or IMDb id |
| `type` | yes | |
| `name` | yes | |
| `poster` | yes (spec) | Often missing in the wild. Use a placeholder. |
| `posterShape` | opt | `poster` (default, 1:0.675, i.e. portrait 2:3-ish), `square`, `landscape` (16:9). The static example uses the non-spec `"regular"`. Map unknown values to `poster`. |
| `genres`, `imdbRating` (string), `releaseInfo` (string, `"2000-2014"`, `"2000-"`), `director`, `cast`, `links`, `description`, `trailers` | opt | Shown in the discover sidebar |

Also seen in the wild: `year` as a number in the static example but a string in Cinemeta, `banner`, `imdb_id`, `behaviorHints.isLive`, `background` and `logo`, and search responses with extra top-level keys (`query`, `rank`). Parse with `ignoreUnknownKeys` and accept both numbers and strings for number-like fields.

### 3.2 Meta: `{ "meta": Meta }`

Source: [meta.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/meta.md), [defineMetaHandler.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/defineMetaHandler.md).

An unknown id may produce `{ "meta": {} }`, `{ "meta": null }`, a 404, or a 500. Treat all of these as "not found here" and try the next addon.

| Field | Notes |
|---|---|
| `id`, `type`, `name` | Required |
| `poster`, `posterShape`, `background` (≤500 KB), `logo` | Images |
| `description`, `releaseInfo`, `released` (ISO 8601), `runtime` (human text, e.g. `"120m"`), `language`, `country`, `awards`, `website`, `imdbRating` (string) | Text |
| `genres`, `director`, `cast` | string[] (deprecated in favour of `links`) |
| `trailers` | `[{ "source": "<YouTube id>", "type": "Trailer"\|"Clip" }]`. Cinemeta also sends `trailerStreams: [{title, ytId}]`. |
| `links` | `[{ name, category, url }]`. Categories group links, e.g. `actor`, `director`, `writer`, `Genres`. `imdb`, `share` and `similar` are reserved: Cinemeta uses `imdb` for rating→IMDb and `share` for the strem.io share link. `url` is an external URL or a `stremio:///…` link. |
| `videos` | Video[]. Used for `series`, `channel` and EPG `tv`. **If absent, the item has exactly one video whose id equals the meta id** (movies, and `tv` without EPG). |
| `behaviorHints.defaultVideoId` | Open the detail page directly on this video's streams. Cinemeta sends `null`. |
| `behaviorHints.isLive` | A live channel. `type: "tv"` implies live. |
| `behaviorHints.hasScheduledVideos` | `videos` is an EPG schedule, not an episode list. Cinemeta sets it `true` on series too, so don't rely on it alone. |

**Video object**:

| Field | Notes |
|---|---|
| `id` (req) | Video id, used as `{id}` in `/stream/{type}/{id}.json` |
| `title` (req) | Cinemeta sends **`name`** instead of `title` for episodes. Accept both. |
| `released` (req) | ISO 8601. For episodes, the first air date. May be in the future (upcoming episode). |
| `season`, `episode` | Numbers. **Season `0` = specials** (Cinemeta). Cinemeta also sends a legacy `number` equal to `episode`. |
| `thumbnail` | Image in the video's aspect ratio |
| `overview` | Synopsis |
| `streams` | Stream[]. **Exclusive**: if present, Stremio doesn't ask other addons for streams for this video. |
| `available` | Boolean hint that this addon has streams |
| `trailers` | Stream[] |
| `startTime`, `endTime` | ISO 8601. Both present with `endTime > startTime` makes the video an EPG programme. |
| `runtime`, `releaseInfo`, `genres`, `cast`, `directors`, `links`, `ratings` (`[{value, system?, icon?}]`) | EPG programme extras |

Series example (Cinemeta `GET /meta/series/tt0108778.json`, 243 videos):

```json
{ "id": "tt0108778:0:1", "name": "Friends: The Stuff You've Never Seen", "season": 0, "episode": 1, "number": 1,
  "firstAired": "2001-02-15T00:00:00.000Z", "released": "2001-02-15T00:00:00.000Z", "tvdb_id": 303991, "rating": "7.4",
  "thumbnail": "https://episodes.metahub.space/tt0108778/0/1/w780.jpg" }
```
(This is the first entry verbatim: a season-0 special. Regular episodes follow as `tt0108778:1:1`, and so on.)

YouTube channel video ids take the form `yt_id:{channelId}:{videoId}`, e.g. `yt_id:UCq-Fj5jknLsUf-MWSy4_brA:b9V_q3Wvo8U`.

UI grouping for series: group by `season` (put 0 last as "Specials"), sort by `episode`, and hide or mark videos whose `released` is in the future.

### 3.3 Stream: `{ "streams": [Stream] }`

Source: [stream.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md), [defineStreamHandler.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/defineStreamHandler.md), client model [stremio-core stream.rs](https://github.com/Stremio/stremio-core/blob/development/src/types/resource/stream.rs). "The streams should be ordered from highest to lowest quality."

**Exactly one source** per stream (core models it as an untagged enum `StreamSource`):

| Source fields | Meaning | TS IPTV |
|---|---|---|
| `url` | Direct `http(s)`, `ftp(s)` or `rtmp` link. May be progressive MP4/MKV, HLS `.m3u8` or DASH `.mpd`. "protocol support can vary depending on client app capabilities". | **Supported** |
| `ytId` | YouTube video id, "plays using the built-in YouTube player" | Not supported in v1 (section 6) |
| `infoHash` (+ `fileIdx`, `sources`/`announce`, `fileMustInclude`) | BitTorrent. With no `fileIdx`, the largest file is used. `sources` are `tracker:<proto>://host:port` or `dht:<hash>`. | **Never** |
| `nzbUrl` / `nzbUrls` + `servers` (`nntps://user:pass@host:563/4`) (+ `fileIdx`, `fileMustInclude`) | Usenet | **Never** |
| `rarUrls`, `zipUrls`, `7zipUrls`, `tgzUrls`, `tarUrls`: `[{url, bytes?}]` (+ `fileIdx`, `fileMustInclude`) | Archives streamed through Stremio's server | Not supported |
| `externalUrl` | Open in a browser, e.g. a Netflix page or a `stremio:///` deep link. Core also knows `androidTvUrl`, `tizenUrl` and `webosUrl` as platform-specific variants. | Supported as "Open in browser". Handle `stremio:///…` internally (1.6). |
| `playerFrameUrl` **[core only]** | An iframe player page. Not in the SDK docs. | Ignore |

Informational fields:

- `name`: short label, usually the quality or the addon name ("1080p").
- `description`: longer, multi-line label, often with emoji and sizes. The old name is **`title`**, which core accepts as an alias. Read `description ?: title`.
- `thumbnail` **[core only]**.
- `subtitles`: `Subtitle[]` attached to this stream (3.6).
- `behaviorHints`:
  - `notWebReady` (bool): "needs to be set to `true` if the URL does not support https or is not an MP4 file".
  - `bingeGroup` (string): streams with the same `bingeGroup` on the next episode are chosen automatically when binge watching, e.g. `"myAddon-720p"`.
  - `proxyHeaders`: `{ "request": {Header: Value}, "response": {Header: Value} }`. Only for `url` streams, and it **requires `notWebReady: true`**. Example `{ "request": { "User-Agent": "Stremio" } }`.
  - `countryWhitelist`: lowercase ISO 3166-1 **alpha-3** codes where the stream works.
  - `videoHash` (OpenSubtitles hash), `videoSize` (bytes) and `filename` are passed on to subtitle addons. The SDK warns when `url` streams lack `filename`.
  - Core keeps any unknown hints in `other`. Preserve them and pass them through.

Real-world examples:

```json
{"streams":[{"url":"https://example.com/live/news.m3u8","name":"News","description":"Live",
             "behaviorHints":{"notWebReady":true,"proxyHeaders":{"request":{"Referer":"https://example.com/","User-Agent":"Mozilla/5.0"}}}}]}
```

```json
{"streams":[{"infoHash":"5d640678eae57c72c0d096904fd7d7405ece6653","fileIdx":1,"name":"1080p","title":"💾 859.37 MB"}],
 "cacheMaxAge":604800,"staleRevalidate":7776000,"staleError":31104000}
```

The second is the official "Public Domain Movies" addon. **It returns torrents only**, so TS IPTV can't play it.

### 3.4 Cache fields in any response

`cacheMaxAge`, `staleRevalidate`, `staleError`: integers in seconds, allowed on catalog, meta, stream, subtitles and addon_catalog responses (1.4). Ignore them when parsing the payload itself.

### 3.5 Addon catalog: `{ "addons": [ { transportName, transportUrl, manifest } ] }`

From [addon_catalog.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/addon_catalog.md) and [defineResourceHandler.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/defineResourceHandler.md):

- `transportName`: `"http"` is the only value officially supported.
- `transportUrl`: the manifest URL.
- `manifest`: the full manifest object.
- Requested at `/addon_catalog/{type}/{id}.json`, where `(type,id)` come from `manifest.addonCatalogs`.
- The official and community listings Stremio uses (live):
  - `https://v3-cinemeta.strem.io/addon_catalog/all/official.json`: Cinemeta, YouTube, WatchHub, Public Domain Movies, OpenSubtitles v3, OpenSubtitles (legacy), Local Files.
  - `https://v3-cinemeta.strem.io/addon_catalog/all/community.json`: the community list. Core hard-codes this URL and allows overriding it through `CINEMETA_ADDONS_CATALOG_URL`.
  - `https://api.strem.io/addonscollection.json`: a bare JSON **array** of the same `{transportUrl, transportName, manifest}` objects (95 entries on 2026-09-27). It is the "public addon collection" that `publishToCentral` and [the publish web form](https://stremio.github.io/stremio-publish-addon/index.html) submit to.
- TS IPTV should support the `addon_catalog` *format*, so that a user who adds their own "addon list" addon can pick from it. **It should not surface the Stremio community collection** (section 7).

### 3.6 Subtitles: `{ "subtitles": [Subtitle] }`

From [subtitles.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/subtitles.md) and [defineSubtitlesHandler.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/requests/defineSubtitlesHandler.md):

- Request: `/subtitles/{type}/{videoId}.json` or `/subtitles/{type}/{videoId}/videoHash={h}&videoSize={bytes}&filename={name}.json`. The current handler docs say `id` is the **video id**, with `videoHash`, `videoSize` and `filename` in `extra`. protocol.md's older text says the `id` is the OpenSubtitles hash with the video id in extra. **Follow the handler docs.** Send the extras only when we know them, e.g. from the stream's `behaviorHints`.
- Subtitle object: `id` (req, unique, distinguishes several subtitles in one language), `url` (req), `lang` (req, ISO 639-2 such as `eng`, otherwise shown verbatim), `label` (opt, picker text such as `"English [CC]"`).
- Formats: SRT and VTT, with ASS/SSA supported. URLs of the form `http://127.0.0.1:11470/subtitles.vtt?from=…` or `http://127.0.0.1:11470/{infoHash}/{fileIdx}` point at Stremio's local streaming server. **Drop any `127.0.0.1:11470` URL.**
- Official subtitle addon: OpenSubtitles v3, `https://opensubtitles-v3.strem.io/manifest.json` (`resources:["subtitles"]`, types movie/series, `idPrefixes:["tt"]`). It works for `tt…` ids only.

### 3.7 Errors (what we'll actually see)

- SDK servers: 404 `{"err":"not found"}` for an unknown resource or no handler, 500 `{"err":"handler error"}` for a handler exception. Some handlers reject with an error, which also gives a 500 (the advanced.md examples reject on "no results").
- Static hosts: 404 HTML for anything not pre-generated, e.g. searches, unknown ids or unsupported extras.
- Many free-hosted community addons (Render, Heroku, BeamUp) cold-start slowly and time out.
- Treat any non-2xx response, non-JSON body or missing root key as **an empty result from that addon**. Don't abort the aggregated screen, and log each addon's error separately.

### 3.8 Native EPG (for `tv` addons)

From [epg.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/epg.md). This is relevant because TS IPTV is live-TV first.

- Opt-in via `manifest.behaviorHints.epgProvider: true` plus a `tv` catalog declaring `{ "name": "date" }` (optional extra).
- `GET /catalog/tv/{catalogId}/date=YYYY-MM-DD.json` (**UTC** day) returns `{ "metasDetailed": [full Meta with that day's programmes in videos] }`. Paging: advance `skip` by the number of channels received so far, and stop on an **empty** page (a short page is not the end).
- Programmes are `videos[]` with `startTime`/`endTime`. Streams are always requested with the **channel** id: `/stream/tv/{channelId}.json`.
- Without `epgProvider`, a `tv` catalog is simply a channel list, which maps directly onto our existing channel UI.

---

## 4. Types and ID conventions

[content.types.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/content.types.md):

| Type | Meaning | Videos |
|---|---|---|
| `movie` | Single video. Video id = meta id. | none (implicit) |
| `series` | Episodes in `videos[]` with `season`/`episode` | many |
| `channel` | A YouTube-style channel of uploads | many (`yt_id:CH:VID`) |
| `tv` | Live TV, streams without duration. EPG capable. | none, or a programme schedule |

Types are free-form strings in practice (core: "`movie`, `series`, `anime`, `other`, `tv`, etc."). Cinemeta's addon catalog lists `Podcasts` and `other`, and Anime Kitsu uses `anime`. **Don't use an enum.** Keep the raw string, with known values for UI hints.

ID conventions:

- **IMDb**: `tt\d+` for a movie or series meta id (`tt0111161`). A series episode video id is `{imdbId}:{season}:{episode}` (`tt0108778:1:1`, `tt0898266:9:17`) ([advanced.md → Understanding Cinemeta](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/advanced.md#understanding-cinemeta)). Stream addons that set `idPrefixes:["tt"]` get **stream** requests for Cinemeta items without providing any meta themselves. That is how the ecosystem composes: Cinemeta supplies metadata and other addons supply streams for the same ids.
- **YouTube**: `yt_id:{channelId}` for a channel meta, `yt_id:{channelId}:{videoId}` for a video. The official YouTube addon has **no `stream` resource**. Core synthesises a `{ytId}` stream from the third `:` segment (`Stream::youtube`).
- **Custom prefixes**: `{addonprefix}{localId}`, e.g. `exampletv:news`, `kitsu:…`. Set `idPrefixes` accordingly. EPG programme ids follow `{channelId}:epg:{startTime}`.
- Ids are opaque. Don't parse them, except for the `tt…:S:E` convention if we ever want an episode title fallback.

---

## 5. How to create an addon

### 5.1 With the official SDK (Node.js)

Setup ([SDK README](https://github.com/Stremio/stremio-addon-sdk/blob/master/README.md)): Node 12+, `npm install stremio-addon-sdk`, or scaffold with `npm i -g stremio-addon-sdk && addon-bootstrap hello-world`. `serveHTTP` handles CORS, caching headers and a landing page at `/`. `getRouter(addonInterface)` mounts the addon into an existing Express app.

`package.json`:

```json
{
  "name": "tsiptv-publicdomain-addon",
  "version": "1.0.0",
  "main": "addon.js",
  "scripts": { "start": "node addon.js" },
  "dependencies": { "stremio-addon-sdk": "^1.6.10" }
}
```

`addon.js`: a small public-domain addon with a paginated, searchable and genre-filterable catalog, a meta handler including a series, and `url` streams:

```javascript
const { addonBuilder, serveHTTP } = require('stremio-addon-sdk')

// ---- data (all items are public domain on archive.org; the live entry is a public test stream) ----
const ITEMS = [
  {
    id: 'tspd_his_girl_friday', type: 'movie', name: 'His Girl Friday',
    releaseInfo: '1940', genres: ['Comedy'],
    poster: 'https://archive.org/services/img/his_girl_friday',
    description: 'Howard Hawks screwball comedy (public domain).',
    streams: [{
      name: 'archive.org', description: 'MP4 512kb',
      url: 'https://archive.org/download/his_girl_friday/his_girl_friday_512kb.mp4',
      behaviorHints: { filename: 'his_girl_friday_512kb.mp4' }
    }]
  },
  {
    id: 'tspd_superman', type: 'series', name: 'Fleischer Superman (1941-43)',
    releaseInfo: '1941-1943', genres: ['Animation'],
    poster: 'https://archive.org/services/img/superman_the_mechanical_monsters',
    videos: [{
      id: 'tspd_superman_s1e1', title: 'The Mechanical Monsters', season: 1, episode: 1,
      released: '1941-11-28T00:00:00.000Z',
      streams: [{
        name: 'archive.org', description: 'MP4 512kb',
        url: 'https://archive.org/download/superman_the_mechanical_monsters/superman_the_mechanical_monsters_512kb.mp4'
      }]
    }]
  },
  {
    id: 'tspd_test_hls', type: 'tv', name: 'HLS Test Channel (Mux test stream)',
    genres: ['Test'], posterShape: 'square',
    poster: 'https://archive.org/services/img/his_girl_friday',
    streams: [{
      name: 'HLS', description: 'Mux public test stream (Big Buck Bunny, CC-BY Blender Foundation)',
      url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
      behaviorHints: { notWebReady: true, proxyHeaders: { request: { 'User-Agent': 'TSIPTV-Example/1.0' } } }
    }]
  }
]
const PAGE = 100
const GENRES = ['Animation', 'Comedy', 'Test']

const manifest = {
  id: 'org.tsiptv.publicdomain',
  version: '1.0.0',
  name: 'Public Domain Sampler',
  description: 'Public-domain films and cartoons from archive.org, plus a public HLS test channel.',
  resources: ['catalog', 'meta', 'stream'],
  types: ['movie', 'series', 'tv'],
  idPrefixes: ['tspd_'],
  catalogs: ['movie', 'series', 'tv'].map(type => ({
    type, id: 'tspd-' + type, name: 'Public Domain ' + type,
    extra: [
      { name: 'search', isRequired: false },
      { name: 'genre', isRequired: false, options: GENRES },
      { name: 'skip', isRequired: false }
    ]
  })),
  behaviorHints: { adult: false, p2p: false }
}

const builder = new addonBuilder(manifest)

const preview = i => ({ id: i.id, type: i.type, name: i.name, poster: i.poster,
  posterShape: i.posterShape, genres: i.genres, releaseInfo: i.releaseInfo, description: i.description })

builder.defineCatalogHandler(({ type, id, extra }) => {
  if (id !== 'tspd-' + type) return Promise.resolve({ metas: [] })
  let list = ITEMS.filter(i => i.type === type)
  if (extra.genre) list = list.filter(i => (i.genres || []).includes(extra.genre))
  if (extra.search) {
    const q = extra.search.toLowerCase()
    list = list.filter(i => i.name.toLowerCase().includes(q))
  }
  const skip = parseInt(extra.skip || '0', 10) || 0
  return Promise.resolve({ metas: list.slice(skip, skip + PAGE).map(preview), cacheMaxAge: 3600 })
})

builder.defineMetaHandler(({ type, id }) => {
  const i = ITEMS.find(x => x.id === id && x.type === type)
  if (!i) return Promise.resolve({ meta: null })
  const meta = preview(i)
  if (i.videos) meta.videos = i.videos.map(({ streams, ...v }) => v) // don't inline streams (exclusive!)
  return Promise.resolve({ meta, cacheMaxAge: 3600 })
})

builder.defineStreamHandler(({ type, id }) => {
  const direct = ITEMS.find(x => x.id === id && x.type === type && x.streams)
  if (direct) return Promise.resolve({ streams: direct.streams, cacheMaxAge: 600 })
  for (const i of ITEMS) {
    const v = (i.videos || []).find(v => v.id === id)
    if (v) return Promise.resolve({ streams: v.streams, cacheMaxAge: 600 })
  }
  return Promise.resolve({ streams: [] })
})

serveHTTP(builder.getInterface(), { port: process.env.PORT || 7000, cacheMaxAge: 600 })
// prints: HTTP addon accessible at: http://127.0.0.1:7000/manifest.json
```

Test locally with `npm start` and add `http://127.0.0.1:7000/manifest.json`. On an Android emulator use `http://10.0.2.2:7000/manifest.json`. `npm start -- --launch` opens Stremio Web with the addon, and `--install` opens `stremio://127.0.0.1:7000/manifest.json` in the desktop app ([serveHTTP.js](https://github.com/Stremio/stremio-addon-sdk/blob/master/src/serveHTTP.js), [testing.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/testing.md)).

Hosting ([deploying/README.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/deploying/README.md)): it is a normal Node app. Stremio runs BeamUp (free) for addons, and Heroku, Glitch, Fleek, cloudno.de or Evennode also work. HTTPS is up to the host. Remote devices need a public HTTPS URL; for a quick demo, `localtunnel` works. Other languages without the SDK: Express, PHP, Python, Go, Ruby, C#, Java hello-worlds ([protocol.md → Next steps](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md#next-steps)).

### 5.2 Without the SDK: static JSON files (GitHub Pages or any static host)

"This addon is so simple that it can actually be hosted statically on GitHub pages!" ([protocol.md](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md), [stremio-static-addon-example](https://github.com/Stremio/stremio-static-addon-example), live at `https://stremio.github.io/stremio-static-addon-example/manifest.json`).

Constraints of static hosting:

- Every URL must exist as a file, so **no free-text `search`**, because queries can't be enumerated. `genre` works if you pre-generate one file per option. `skip` works if you pre-generate `skip=100.json` etc.
- **Avoid `:` in ids**. The client sends `%3A`, the host decodes it and looks for a file named with `:`. That's illegal on Windows checkouts and fragile on some hosts. Use `_` or `-` separators (`tspd_superman_s1e1`), not the Cinemeta-style `id:1:1`.
- Keep `extra` values URL-safe single words (`genre=Comedy`). A value with spaces is requested as `genre=Science%20Fiction.json`, which the host decodes to a file literally named `genre=Science Fiction.json`. That works on GitHub Pages [inference] but is error-prone.
- CORS: GitHub Pages sends `Access-Control-Allow-Origin: *` (verified). Netlify, Vercel and S3 need a header rule, e.g. the example's Vercel/now `now.json`: `{"static":{"headers":[{"source":"**","headers":[{"key":"Access-Control-Allow-Origin","value":"*"}]}]}}`. For local testing: `http-server dist --cors -c-1`, where `-c-1` disables caching (example README).
- GitHub Pages: add an empty `.nojekyll` at the root so Jekyll doesn't drop or transform files. Files starting with `_` are ignored otherwise. It serves `.json` as `application/json` with `Cache-Control: max-age=600`.

File tree for the same "Public Domain Sampler" (repo root = site root; manifest URL `https://<user>.github.io/<repo>/manifest.json`):

```
.nojekyll
manifest.json
catalog/movie/tspd-movie.json
catalog/movie/tspd-movie/genre=Comedy.json
catalog/series/tspd-series.json
catalog/tv/tspd-tv.json
meta/movie/tspd_his_girl_friday.json
meta/series/tspd_superman.json
meta/tv/tspd_test_hls.json
stream/movie/tspd_his_girl_friday.json
stream/series/tspd_superman_s1e1.json
stream/tv/tspd_test_hls.json
```

`manifest.json`:

```json
{
  "id": "org.tsiptv.publicdomain.static",
  "version": "1.0.0",
  "name": "Public Domain Sampler (static)",
  "description": "Public-domain films and cartoons from archive.org, plus a public HLS test channel. Static JSON on GitHub Pages.",
  "resources": ["catalog", "meta", "stream"],
  "types": ["movie", "series", "tv"],
  "idPrefixes": ["tspd_"],
  "catalogs": [
    { "type": "movie",  "id": "tspd-movie",  "name": "Public Domain Movies",
      "extra": [ { "name": "genre", "isRequired": false, "options": ["Comedy"] } ] },
    { "type": "series", "id": "tspd-series", "name": "Public Domain Cartoons" },
    { "type": "tv",     "id": "tspd-tv",     "name": "Test Channels" }
  ],
  "behaviorHints": { "adult": false, "p2p": false }
}
```

`catalog/movie/tspd-movie.json`, with an identical copy at `catalog/movie/tspd-movie/genre=Comedy.json`:

```json
{
  "metas": [
    { "id": "tspd_his_girl_friday", "type": "movie", "name": "His Girl Friday",
      "poster": "https://archive.org/services/img/his_girl_friday",
      "releaseInfo": "1940", "genres": ["Comedy"] }
  ]
}
```

`catalog/series/tspd-series.json`:

```json
{
  "metas": [
    { "id": "tspd_superman", "type": "series", "name": "Fleischer Superman (1941-43)",
      "poster": "https://archive.org/services/img/superman_the_mechanical_monsters",
      "releaseInfo": "1941-1943", "genres": ["Animation"] }
  ]
}
```

`catalog/tv/tspd-tv.json`:

```json
{
  "metas": [
    { "id": "tspd_test_hls", "type": "tv", "name": "HLS Test Channel (Mux test stream)",
      "poster": "https://archive.org/services/img/his_girl_friday", "posterShape": "square",
      "behaviorHints": { "isLive": true } }
  ]
}
```

`meta/movie/tspd_his_girl_friday.json`:

```json
{
  "meta": {
    "id": "tspd_his_girl_friday", "type": "movie", "name": "His Girl Friday",
    "poster": "https://archive.org/services/img/his_girl_friday",
    "releaseInfo": "1940", "released": "1940-01-18T00:00:00.000Z", "runtime": "92 min",
    "genres": ["Comedy"],
    "description": "Howard Hawks screwball comedy. Public domain (archive.org item his_girl_friday).",
    "links": [ { "name": "archive.org", "category": "source", "url": "https://archive.org/details/his_girl_friday" } ]
  }
}
```

`meta/series/tspd_superman.json`:

```json
{
  "meta": {
    "id": "tspd_superman", "type": "series", "name": "Fleischer Superman (1941-43)",
    "poster": "https://archive.org/services/img/superman_the_mechanical_monsters",
    "releaseInfo": "1941-1943", "genres": ["Animation"],
    "description": "Fleischer Studios Superman cartoons, public domain.",
    "videos": [
      { "id": "tspd_superman_s1e1", "title": "The Mechanical Monsters",
        "season": 1, "episode": 1, "released": "1941-11-28T00:00:00.000Z",
        "thumbnail": "https://archive.org/services/img/superman_the_mechanical_monsters" }
    ]
  }
}
```

`meta/tv/tspd_test_hls.json`:

```json
{
  "meta": {
    "id": "tspd_test_hls", "type": "tv", "name": "HLS Test Channel (Mux test stream)",
    "poster": "https://archive.org/services/img/his_girl_friday", "posterShape": "square",
    "description": "Public HLS test stream from Mux (Big Buck Bunny, CC-BY Blender Foundation).",
    "behaviorHints": { "isLive": true }
  }
}
```

`stream/movie/tspd_his_girl_friday.json`:

```json
{
  "streams": [
    { "name": "archive.org", "description": "MP4 · 512kb",
      "url": "https://archive.org/download/his_girl_friday/his_girl_friday_512kb.mp4",
      "behaviorHints": { "filename": "his_girl_friday_512kb.mp4" } }
  ]
}
```

`stream/series/tspd_superman_s1e1.json`:

```json
{
  "streams": [
    { "name": "archive.org", "description": "MP4 · 512kb",
      "url": "https://archive.org/download/superman_the_mechanical_monsters/superman_the_mechanical_monsters_512kb.mp4",
      "behaviorHints": { "filename": "superman_the_mechanical_monsters_512kb.mp4", "bingeGroup": "tspd-archive-512kb" } }
  ]
}
```

`stream/tv/tspd_test_hls.json`:

```json
{
  "streams": [
    { "name": "HLS", "description": "Mux public test stream",
      "url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
      "behaviorHints": { "notWebReady": true,
                         "proxyHeaders": { "request": { "User-Agent": "TSIPTV-Example/1.0" } } } }
  ]
}
```

All media URLs above returned HTTP 200 on 2026-09-27 (`video/mp4`, `audio/mpegurl`, `image/jpeg`). archive.org lists `his_girl_friday` with licence `creativecommons.org/licenses/publicdomain`. Note that the Big Buck Bunny URL used throughout the SDK docs (`distribution.bbb3d.renderfarming.net`) now returns **403**. Don't use it in tests.

Publishing to Stremio's community list (optional, not needed for TS IPTV): `publishToCentral('https://…/manifest.json')` from the SDK, or [the web form](https://stremio.github.io/stremio-publish-addon/index.html).

---

## 6. What a non-torrent native player can play

TS IPTV plays through native players. Our HLS/MP4 stack already handles `url` streams.

| Stream | Playable? | Handling |
|---|---|---|
| `url` https/http: progressive MP4, MKV, WebM, TS | **Yes** | Feed to the player. Container support depends on the platform player (MKV is fine on ExoPlayer and VLC, not on AVPlayer) [inference]. |
| `url` HLS (`.m3u8`) | **Yes** | Same path as our M3U channels |
| `url` DASH (`.mpd`) | Yes on ExoPlayer/Media3. Not on AVPlayer (iOS). | Detect by extension or `Content-Type`, and mark the stream unsupported on iOS if needed [inference]. |
| `url` rtmp / ftp | Depends on player (VLC/mpv yes, ExoPlayer needs an extension) | Treat as "may not play" |
| `ytId`, `yt_id:` videos, trailers | Needs YouTube handling | **Out of scope for v1.** Extracting raw YouTube streams violates YouTube's ToS. The only compliant route is the official embedded IFrame/Android player [inference]. Hide `ytId` streams, or show "Open in YouTube" via `https://www.youtube.com/watch?v={ytId}`. |
| `infoHash` (+`fileIdx`, `sources`) | Needs a BitTorrent engine | **We will NOT support torrents.** Hide them, and don't show counts like "12 torrent streams hidden" that would advertise them. |
| `nzbUrl`/`servers`, `rarUrls`/`zipUrls`/`7zipUrls`/`tgzUrls`/`tarUrls` | Need Stremio's streaming server | Not supported. Hide. |
| `externalUrl` | Not playable in-app | "Open in browser" (or internal navigation for `stremio:///`) |
| `playerFrameUrl` | Web iframe | Ignore |

`notWebReady` and `proxyHeaders`:

- `notWebReady: true` means a *browser* `<video>` can't play the URL directly: it is plain http, not MP4, or needs custom headers. Stremio then routes it through its local streaming server (`127.0.0.1:11470`), which proxies and remuxes it. **A native player can ignore `notWebReady`**. It is a hint that the stream is HLS/MKV/http or needs headers, not a prohibition [inference from stream.md wording and the `127.0.0.1:11470` references in subtitles.md].
- `proxyHeaders.request`: headers that must be sent on **every** HTTP request for the media, including HLS playlist, variant and segment requests. The usual ones are `User-Agent`, `Referer`, `Origin` and `Cookie`. This maps onto our existing per-channel header support (M3U `#EXTVLCOPT:http-user-agent`/`http-referrer`). On Media3 use `DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)` plus `setUserAgent`. On AVPlayer use `AVURLAsset(url:, options: ["AVURLAssetHTTPHeaderFieldsKey": headers])`, which is undocumented but widely used [inference]. Headers apply to every request of that stream.
- `proxyHeaders.response`: headers the Stremio proxy adds to *responses* it gives the web player, e.g. a forced `Content-Type`. That has no native equivalent, so ignore it, or use `Content-Type` as a MIME hint for the player [inference].
- `countryWhitelist`: purely informational. We could show a "may be geo-restricted" badge.
- `bingeGroup`: when auto-playing the next episode, prefer the stream whose `bingeGroup` equals the one just played.
- `filename`/`videoSize`/`videoHash`: pass them on to subtitle addons (3.6). `filename` also helps guess the container.

---

## 7. Legal and Google Play policy

What the sources say:

- Stremio's [Terms of Service](https://www.stremio.com/tos): "Official Addons" are "developed by the Provider and are automatically accessible for the Users of Stremio". "Community Addons" are "developed by independent third-party developers through the use of an open-source SDK / protocol". "The Provider is not liable for hosting, storing, developing, maintaining or monitoring the content available for the Users through the Community Addons." Community addon developers must "own all intellectual property rights or the rights to use the Streamable Content".
- The official addon list (live `addon_catalog/all/official.json`) contains Cinemeta (metadata), YouTube (catalog/meta of YouTube channels), WatchHub (links to legal services via `externalUrl`), Public Domain Movies (torrents), OpenSubtitles v3 and legacy OpenSubtitles (subtitles), and Local Files. **None of the official addons except Public Domain Movies provides playable `url` streams, and that one uses torrents.** In Stremio, streams come almost entirely from community addons. Many of the most popular ones (torrent scrapers and debrid resolvers) index copyrighted films and series without authorisation. General knowledge, consistent with the ToS separation above and with third-party coverage, e.g. [RapidSeedbox, "Is Stremio legal?"](https://www.rapidseedbox.com/blog/is-stremio-legal).
- [Google Play Intellectual Property policy](https://support.google.com/googleplay/android-developer/answer/9888072): "We don't allow apps that induce or encourage copyright infringement". Violations include "Apps that encourage users to stream and download copyrighted works, including music and video, in violation of applicable copyright law" and store listings or descriptions that prompt users to obtain copyrighted material without authorisation. Apple's App Review Guidelines contain equivalent IP rules [not re-read for this document].

Recommendations for TS IPTV:

1. **Ship zero third-party addon URLs by default.** No pre-installed addons, no "recommended addons" list, no in-app browser of `api.strem.io/addonscollection.json` or Cinemeta's `community` addon catalog. The user adds an addon only by typing or pasting a URL, or by opening a link.
2. **Never name, screenshot or promote** torrent or debrid addons in the app, the store listing, help pages or marketing. Store screenshots should use our own public-domain sample addon (section 5).
3. **Don't implement torrent, Usenet or archive sources.** That removes most of the infringing use of the ecosystem and the P2P IP exposure. Hide `infoHash`/`nzbUrl` streams silently. Refuse or warn on manifests whose `behaviorHints.p2p` is `true`. Honour `behaviorHints.adult` with a warning, and hide adult addons in kids or Play-family contexts.
4. Show a clear notice when an addon is added: "Addons are third-party services not operated by TS IPTV. You are responsible for having the rights to content you access." Keep an addon removal UI and a remote kill-switch list (blocked addon ids or hosts) for DMCA or Play takedown requests [inference, operational best practice].
5. Clearly legitimate addons, with manifests fetched and verified on 2026-09-27:

| Addon | Manifest URL | What it gives us |
|---|---|---|
| Cinemeta (official, Stremio) | `https://v3-cinemeta.strem.io/manifest.json` | Metadata and catalogs for IMDb ids, no streams. Legal, but alone it gives nothing playable. |
| OpenSubtitles v3 (official) | `https://opensubtitles-v3.strem.io/manifest.json` | Subtitles for `tt…` ids |
| WatchHub (official) | `https://watchhub.strem.io/manifest.json` | `externalUrl` links to Netflix, Hulu, etc. |
| YouTube (official) | `https://v3-channels.strem.io/manifest.json` | Channel catalogs/meta. Streams need YouTube playback, so not usable in v1. |
| Stremio static example | `https://stremio.github.io/stremio-static-addon-example/manifest.json` | Protocol smoke test only. Its stream URL is dead (403). |
| Public Domain Movies (official) | `https://caching.stremio.net/publicdomainmovies.now.sh/manifest.json` | Legal content but **torrent-only**, so it doesn't work in TS IPTV |

   Even these should **not** be pre-installed (point 1). They are fine as QA fixtures. Best option for QA and store screenshots: host our own static "Public Domain Sampler" (section 5.2) on GitHub Pages.

---

## 8. Implementation notes for TS IPTV

### 8.1 Scope, phase 1

1. **Add addon by URL**. Accept `https://…/manifest.json`, `http://` (warn), `stremio://…` (rewrite to `https://`). Reject `/stremio/v1`, `ipfs://` and `ipns://`. Fetch the manifest, validate that `id`, `name`, `version`, `types` and `resources` are present, show name, description, logo, types, catalogs and warnings (`adult`, `p2p`), then confirm. Handle `configurationRequired` as in 1.7. Store `{transportUrl, manifest, addedAt, enabled, order}`. Refresh the manifest periodically (daily) and on pull-to-refresh, and treat a changed `version` as an update.
2. **Catalogs**:
   - Home rows: every catalog with no required extra, plus required extras that have `options`, filled with the first option.
   - Paging: `skip` in steps of the number of items received. The page size is normally 100. Stop on an empty page or a short page. Fall back to "empty page" if the addon returns fewer than 100 but keeps giving new items [inference].
   - `genre` filter chips from `extra[].options`, with multi-select only if `optionsLimit > 1`.
   - **Search**: fan out to every catalog that declares a `search` extra, and show the results grouped by addon and catalog.
3. **Meta**: `GET /meta/{type}/{id}.json` to every addon whose `meta` resource matches (type + idPrefix). Use the first non-empty result in addon order, and optionally merge missing fields from later ones. If no addon provides meta, build a minimal page from the MetaPreview. For series, list seasons and episodes from `videos` (season 0 = Specials, future `released` = upcoming). Honour `behaviorHints.defaultVideoId`. If `videos` is absent, the single video id is the meta id.
4. **Streams**: `GET /stream/{type}/{videoId}.json` to every matching addon in parallel. If the chosen video has inline `video.streams`, use those exclusively and skip the requests. Keep `url` and `externalUrl` streams, drop everything else. Display `name` + `description ?: title` grouped by addon, in the addon's order. Pass `proxyHeaders.request` to the player data source. `tv`-type items map straight onto our live-channel player.
5. **Subtitles** (optional, phase 1b): `GET /subtitles/{type}/{videoId}[/filename=…&videoSize=…&videoHash=…].json` to matching addons, plus `stream.subtitles`. Drop `127.0.0.1:11470` URLs. Show `label ?: lang`.
6. Later: `addon_catalog` (user-provided addon lists only), Native EPG (`epgProvider` + `date` extra → our EPG grid), `stremio:///` link routing, `bingeGroup` auto-next.

### 8.2 Networking

- One shared Ktor client. `followRedirects = true` (SDK `redirect` gives a 307). Accept gzip. Send `Accept: application/json` and a descriptive `User-Agent`, e.g. `TSIPTV/{version} (Stremio-addon-client)` [inference].
- Timeouts [inference, not specified by Stremio]: manifest connect 10 s / total 15 s; catalog, meta and subtitles 15 s total; streams 20 s total (resolver addons are slow). Search: show results progressively as each addon answers.
- Concurrency: limit to about 6 parallel requests per host and about 16 overall. Cancel in-flight requests when the screen closes.
- Errors: on non-2xx, a timeout, invalid JSON or a missing root key (`metas`, `meta`, `streams`, `subtitles`, `addons`), treat that addon's result as empty and record `lastError` per addon for a diagnostics screen. Put a small "1 addon failed" affordance in the UI rather than failing the whole screen. After repeated manifest failures, mark the addon "unreachable" but never auto-delete it.
- Caching: an HTTP cache keyed by full URL that honours `Cache-Control` `max-age`/`stale-while-revalidate`/`stale-if-error`, falling back to the body fields `cacheMaxAge` etc. Defaults when nothing is given: manifest 6 h, catalog 1 h, meta 6 h, streams ≤ 5 min (cap streams at 5 min even if the addon asks for more), subtitles 24 h [inference].
- Security: configured transport URLs may contain API keys, so store them encrypted, redact paths in logs and crash reports, and never send them to our backend or analytics. Show `http://` addons with a warning, and block cleartext on Android unless the user opts in [inference].

### 8.3 Parsing (kotlinx.serialization)

- `Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }`.
- `resources`: a custom serializer for `String | {name, types?, idPrefixes?}`.
- Catalog extras: `extra` → else `extraSupported`/`extraRequired` (+ `genres`). `optionsLimit` defaults to 1. `isRequired` defaults to false.
- Fields that may be a number or a string (`year`, `imdbRating`, `season`, `episode`): lenient serializers.
- Video: `title ?: name`. Stream: `description ?: title`.
- Types, posterShape and link categories stay as raw strings, with known values only as UI hints.
- Stream source discriminator by field presence: `url` > `externalUrl` > `ytId` > `infoHash` > `nzbUrl`/`*Urls`. Keep unknown streams as `Unsupported(rawKind)` so they can be counted for diagnostics.
- URL building: encode `resource`, `type`, `id` and each extra key and value with the `encodeURIComponent` set (1.3). Ktor's `encodeURLParameter()` also encodes `!*'()`, which servers still decode correctly, so that's fine. Make sure spaces become `%20`, never `+`.

### 8.4 Suggested module layout [inference]

`shared/…/stremio/`: `StremioModels.kt` (manifest, meta, stream, subtitle DTOs), `StremioUrl.kt` (transport → base, path and extra encoding), `StremioClient.kt` (fetch, cache, timeouts, error mapping), `AddonRepository.kt` (installed addons, resource matching per 2.2, fan-out and aggregation), `StremioStreamMapper.kt` (Stream → our playable `MediaSource` with headers, dropping unsupported kinds). Unit-test the URL encoder against the examples in 1.3, the matcher against the Cinemeta manifest, and the parser against recorded fixtures (Cinemeta series meta, Public Domain Movies torrent stream, the static example).

---

## Sources actually read

Official SDK (Stremio/stremio-addon-sdk, `master`):
- README: https://github.com/Stremio/stremio-addon-sdk/blob/master/README.md
- Protocol: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md
- Resources overview and filtering: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/README.md
- Manifest: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md
- Legacy manifest (extraSupported/extraRequired): https://github.com/Stremio/stremio-addon-sdk/blob/b11bd517f8ce3b24a843de320ec8ac193611e9a0/docs/api/responses/manifest.md
- Meta: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/meta.md
- Meta links: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/meta.links.md
- Stream: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md
- Subtitles: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/subtitles.md
- Addon catalog: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/addon_catalog.md
- Content types: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/content.types.md
- Request handlers: https://github.com/Stremio/stremio-addon-sdk/tree/master/docs/api/requests (defineCatalogHandler, defineMetaHandler, defineStreamHandler, defineSubtitlesHandler, defineResourceHandler)
- Advanced usage: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/advanced.md
- Deep links: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/deep-links.md
- Native EPG: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/epg.md
- Deploying: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/deploying/README.md
- Examples list: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/examples.md
- SDK source: `src/getRouter.js`, `src/serveHTTP.js`, `src/builder.js`, `src/types.d.ts`

Official static example: https://github.com/Stremio/stremio-static-addon-example (README, manifest.json, now.json, catalog/meta/stream JSON)

Official client engine (Stremio/stremio-core, `development`):
- HTTP transport: https://github.com/Stremio/stremio-core/blob/development/src/addon_transport/http_transport/http_transport.rs
- Extra encoding: https://github.com/Stremio/stremio-core/blob/development/src/types/query_params_encode.rs
- Encode set, page size: https://github.com/Stremio/stremio-core/blob/development/src/constants.rs
- Request/ResourcePath/aggregation: https://github.com/Stremio/stremio-core/blob/development/src/types/addon/request.rs
- Manifest model and matching: https://github.com/Stremio/stremio-core/blob/development/src/types/addon/manifest.rs
- Stream model: https://github.com/Stremio/stremio-core/blob/development/src/types/resource/stream.rs

Live endpoints fetched 2026-09-27: Cinemeta manifest, catalog search/genre+skip and series meta (`v3-cinemeta.strem.io`); `addon_catalog/all/official.json`; `api.strem.io/addonscollection.json`; manifests of OpenSubtitles v3, WatchHub, YouTube channels, Public Domain Movies (plus one catalog and stream response), and the static example on GitHub Pages; archive.org metadata API for `his_girl_friday` and `superman_the_mechanical_monsters`; Mux test stream.

Policy:
- Stremio Terms of Service: https://www.stremio.com/tos
- Google Play Intellectual Property policy: https://support.google.com/googleplay/android-developer/answer/9888072
- Secondary (context only): https://www.rapidseedbox.com/blog/is-stremio-legal
