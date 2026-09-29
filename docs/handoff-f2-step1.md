# Hand-off: F2 step 1, protocol core (Stremio-compatible addons)

Status: done on `feature/sources-and-formats`, 2026-09-28. Not committed.
Scope: PRD [`prd-stremio-addons.md`](prd-stremio-addons.md), implementation plan step 1. This step has no UI and no DB changes.
Research: [`research/stremio-addons.md`](research/stremio-addons.md).

## What exists

| Path | Content |
|---|---|
| `composeApp/src/commonMain/kotlin/tss/t/tsiptv/core/stremio/StremioJson.kt` | `StremioJson` (`ignoreUnknownKeys`, `isLenient`, `coerceInputValues`, `explicitNulls = false`), plus lenient serializers for string, int, boolean and string-list fields |
| `…/StremioModels.kt` | DTOs: `StremioManifest`, `ManifestResource` (string or object), `ManifestCatalog` + `CatalogExtra` (resolved extras), `ManifestBehaviorHints`, `StremioMeta`, `MetaLink`, `MetaBehaviorHints`, `StremioVideo`, `StremioStream`, `StreamBehaviorHints` (keeps unknown hints in `other`), `StremioSubtitle`, `AddonDescriptor`. Also the tolerant list and object serializers |
| `…/StremioManifestParser.kt` | Validates required fields and reports the missing field name |
| `…/StremioUrl.kt` | `StremioUrlEncoding` (`encodeURIComponent` set), `StremioTransport` (transport → base URL, resource URLs), `ManifestUrlNormalizer` (user input → transport, or an error key), `StremioDeepLink` (`stremio:///` page links) |
| `…/ResourceMatcher.kt` | Matching like stremio-core (§2.2): board rows, search fan-out, `extra` building in declaration order, type-tab order |
| `…/CatalogPager.kt` | Paging state machine: `skip` = items received so far, the stop rules, de-duplication |
| `…/StreamClassifier.kt` | `StreamKind` by field presence, `ClassifiedStream` (display fields, badges), `visible()` for hiding unsupported streams, the MIME hint, and `ClassifiedStream.toMediaItem()` |
| `…/StremioCache.kt` | `StremioRequestKind` (timeouts, cache defaults, 5-min stream cap, root key), `CachePolicy.resolve` (header, then body fields, then defaults), `CacheEntry`, `StremioResponseCache` (LRU, 20 MiB) |
| `…/StremioHttp.kt` | `StremioHttpTransport` (the fun interface the client uses), `KtorStremioHttpTransport`, `createStremioHttpClient(base)` |
| `…/StremioClient.kt` | `StremioClient`: manifest, catalog, meta, streams, subtitles and addon_catalog; error mapping; total timeout; cache (fresh / stale-while-revalidate / stale-if-error); 16 global and 6 per-host concurrency limits |
| `…/AddonBlocklist.kt` | `AddonBlocklist` (id and host matching) and `AddonBlocklistFetcher` (our own URL, `isDue()` for the daily check) |
| `composeApp/src/commonTest/kotlin/tss/t/tsiptv/core/stremio/*Test.kt` | 79 tests in 8 classes, plus `StremioTestSupport.kt` (fixture reader, `FakeTransport` including a static-host fake that serves the sampler files) |
| `composeApp/src/commonTest/kotlin/assests/stremio/` | `sampler/*`: byte copies of `web/public/examples/stremio-sampler/`, with `/` in paths replaced by `__`. Hand-written fixtures: `cinemeta-manifest.json`, `cinemeta-series-meta.json`, `mixed-streams.json` (url + infoHash + ytId + externalUrl), `edge-streams.json`, `legacy-manifest.json`, `messy-catalog.json`. No real third-party catalogue was downloaded. |
| `qa/stremio-fixture/server.js` | QC harness (plain Node 18+, no npm dependencies, never shipped). Usage is in the file header. |

### Test results

`.\gradlew.bat :composeApp:desktopTest --console=plain` passes. Totals from `composeApp/build/test-results/desktopTest/*.xml` after the QC round 2 fixes: **281 tests, 0 failures, 0 errors, 0 skipped**. The suite total includes tests F1 added in parallel; round 0 had 248. Of these, 91 are in `core.stremio`: StremioModelsTest 17, StremioClientTest 16, StremioUrlTest 15, ResourceMatcherTest 13, CatalogPagerTest 10, StreamClassifierTest 8, StremioCacheTest 7, AddonBlocklistTest 5. No tests outside `core/stremio` failed. `:composeApp:compileDebugKotlinAndroid` also succeeds. iOS was not compiled because this was a Windows machine; the code is common-only and uses no JVM APIs.

Acceptance criteria covered by unit tests at protocol level: AC-S3, S4 and S8 exactly; S9; S10 (the pager logic; the harness serves the real 250 items); S11 (extra building); S16 (classification and visibility); S18 (`StremioVideo.hasInlineStreams` and the streams); S19 (cache); S22 (blocklist matching and fetch). Policy grep: `core/stremio` in `commonMain` contains no `strem.io`, `strem.fun`, `stremio.net` or `beamup`. Test sources contain `v3-cinemeta.strem.io` only in the AC-S8 URL assertions.

## Public API for step 2 onwards

```kotlin
// Input → transport (AC-S2/S3). Error.error.messageKey is the string key.
ManifestUrlNormalizer.normalize(input): ManifestUrlResult  // Ok(transport) | Error(NOT_MANIFEST | LEGACY | LOCAL_SERVER)
StremioTransport.parse(storedManifestUrl): StremioTransport?  // for URLs decrypted from Room
transport.manifestUrl / displayHost / host / port / isHttps / configureUrl
transport.resourceUrl(resource, type, id, extra: List<Pair<String,String>>)
// toString() prints only the host; never log manifestUrl.

// Client (one instance, Koin single)
StremioClient(transport: StremioHttpTransport, userAgent = StremioClient.userAgentFor(appVersion),
              cache = StremioResponseCache(), nowMs = …, refreshScope: CoroutineScope? = null,
              maxConcurrentRequests = 16, maxRequestsPerHost = 6, totalTimeoutMs = { it.totalTimeoutMs })
suspend fetchManifest(t): ManifestFetchResult      // Success(manifest, rawJson) | Invalid(field?) | Unreachable(AddonError)
suspend catalog(t, CatalogRequest): AddonResult<List<StremioMeta>>
suspend meta(t, type, id): AddonResult<StremioMeta?>          // null = "not found here" (meta null / {})
suspend streams(t, type, videoId): AddonResult<List<StremioStream>>
suspend subtitles(t, type, videoId, extra): AddonResult<List<StremioSubtitle>>   // F2b
suspend addonCatalog(t, type, id): AddonResult<List<AddonDescriptor>>             // format only
suspend invalidate(t)                                          // drop that addon's cached responses
// AddonResult = Success(value, source: NETWORK|CACHE|STALE_REVALIDATING|STALE_ON_ERROR) | Failure(AddonError)
// AddonError.code ("http_404", "timeout", "network", "invalid_json", "missing_streams", …) goes in lastError; no URL.
// AddonError.isNotFound: a 404 (for meta, the repository may choose not to count it as a partial failure).

// Manifest
StremioManifestParser.parse(body) / parseOrNull(body)   // re-parse manifestJson from Room with parseOrNull
manifest.hints.isAdult / isP2p / isConfigurable / isConfigurationRequired, manifest.logoUrl, catalogList, typeList

// Matching (§3)
ResourceMatcher.supportsMeta / supportsStream / supportsSubtitles(manifest, type, id)
ResourceMatcher.supportsCatalog(manifest, type, catalogId, extra)
ResourceMatcher.declaresResource(manifest, "stream")     // preview: "provides streams"
ResourceMatcher.boardCatalogs(manifest): List<CatalogRequest>
ResourceMatcher.searchCatalogs(manifest, query): List<CatalogRequest>
ResourceMatcher.buildExtra(catalog, selections, skip)    // genre chips → ordered extra, optionsLimit applied
ResourceMatcher.defaultRequiredSelections(catalog)       // a required genre's first option
ResourceMatcher.orderTypes(types)                        // type tabs

// Paging (§5): one pager per catalogue screen and selection set; one call per "load more"
val pager = CatalogPager(catalog, selections)
suspend fun loadMore() {
    val req = pager.nextRequest() ?: return                    // null = end of catalogue
    when (val r = client.catalog(t, req)) {
        is AddonResult.Success -> show(pager.accept(r.value))  // only the new, de-duplicated items
        is AddonResult.Failure -> when (pager.onFailure(r.error)) {
            CatalogPager.PageFailure.END_OF_CATALOG -> Unit    // silent: no Retry, not counted in addon_partial_failure
            CatalogPager.PageFailure.FAILED -> showRetry()     // Retry calls loadMore() again (same request)
        }
    }
}
// Never call accept(emptyList()) for a failure: that would end paging as an empty page.

// Meta helpers
meta.effectiveVideos (a movie or tv item → one video with the meta id), meta.isLive, meta.resolvedPosterShape,
meta.fillMissingFrom(later), video.displayTitle (title ?: name), video.episodeNumber, video.hasInlineStreams,
meta.behaviorHints?.defaultVideoId, MetaLink.isInternal, StremioDeepLink.parse(url)

// Streams (§7)
val rows = StreamClassifier.classifyAll(streams)          // ClassifiedStream(stream, kind)
StreamClassifier.visible(rows, showUnsupported)           // hidden by default
row.name, row.description, row.isSelectable, row.bingeGroup, row.isRegionLimited, row.mayNotPlayOnIos
row.kind: Playable(url, headers, mimeType) | ExternalBrowser(url, host) | Internal(url, link: StremioDeepLink) | Unsupported(rawKind)
// Unsupported rows: name == "" and description == null (show only stream_unsupported).
row.toMediaItem(id = "stremio:$addonId:$videoId", title, subtitle, artworkUri): MediaItem?   // F1 headers path

// Blocklist (policy)
AddonBlocklistFetcher(transport, userAgent).fetch(): AddonBlocklist?   // null = keep the previous list
AddonBlocklistFetcher.isDue(lastCheckedAtMs, nowMs)
blocklist.isBlocked(addonId, transport)
```

## Required changes outside my folders (not done)

1. **Koin** (step 3, next to the existing modules). Register one addon HTTP client and the client:
   ```kotlin
   single { createStremioHttpClient(<platform HttpClient>) }             // shares the engine
   single<StremioHttpTransport> { KtorStremioHttpTransport(get()) }
   single { StremioClient(get(), StremioClient.userAgentFor(<appVersionName>), refreshScope = <app-scoped CoroutineScope>) }
   single { AddonBlocklistFetcher(get(), StremioClient.userAgentFor(<appVersionName>)) }
   ```
   `<platform HttpClient>` is `(NetworkClientFactory.get().getNetworkClient() as KtorNetworkClient).client` after `client` is made public or internal, or a new `HttpClient(OkHttp | Darwin | CIO)`. The existing platform clients install `Logging` at `LogLevel.NONE`. Keep it that way, because addon URLs carry secrets. The desktop CIO client's `endpoint { connectTimeout = 5000 }` applies to derived clients. The addon client's per-request `timeout {}` sets connect to 10 s, but CIO may still use the engine's 5 s. That is acceptable. If exact behaviour matters, build a dedicated `HttpClient(CIO)` for addons.
2. **App version for the User-Agent**: pass the existing version name (`appVersionName`, e.g. `tsptv.1.0.0001`) to `userAgentFor`. It is not currently exposed to common code. It needs a BuildKonfig-style constant or an injected value.
3. **`core/security/SecretCipher`** (step 2). It is `expect/actual` platform code, so step 1 does not implement it. Specification:
   ```kotlin
   interface SecretCipher { fun encrypt(plain: String): String; fun decrypt(token: String): String? }  // null on tamper/key loss
   ```
   - AES-256-GCM, 12-byte random IV per call, 128-bit tag. Token = `v1:` + base64url(iv ‖ ciphertext‖tag). The version prefix allows key rotation.
   - Android: key in the Android Keystore (`KeyGenParameterSpec`, `PURPOSE_ENCRYPT|DECRYPT`, `BLOCK_MODE_GCM`, no user auth), alias `tsiptv_addon_secrets`.
   - iOS: 32-byte key in the Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`), AES-GCM via CryptoKit through a small Swift shim or a cinterop.
   - Desktop: 32-byte random key file in the app data directory with owner-only permissions (POSIX `rw-------`; on Windows an ACL restricted to the current user), then `javax.crypto` AES/GCM.
   - If `decrypt` returns null, the addon's URL is lost. Mark the addon `lastError = "secret_lost"` and ask the user to re-add it. Never fall back to storing the URL in plain text.
   - AC-S21 test: `transportUrlEnc` never contains the plain URL, and `encrypt` twice gives different tokens.
4. **Room v5, entities and DAOs** (step 2), as in the PRD. Store `manifestJson` as the raw body from `ManifestFetchResult.Success.rawJson` and re-parse it with `StremioManifestParser.parseOrNull`.
5. **Optional test dependency**: `io.ktor:ktor-client-mock` in `commonTest` would let `KtorStremioHttpTransport` itself be unit-tested (redirects, gzip, header lower-casing). It is not added, because `build.gradle.kts` is out of scope. Every client behaviour is tested through the `StremioHttpTransport` fake. The Ktor adapter is covered only by the compile and by QC against the harness.
6. `StreamClassifier` uses F1's `core/parser/model/playback/StreamMimeTypes` constants (read-only). If F1 renames them, update the imports.

## Deviations and clarifications

- **AC-S10 request log**: with the lenient rule (PRD §5, research §3.1), the 50-item page does not end paging on its own. The client sends one more request, `skip=250`, which returns empty, and that ends paging. QC will therefore see 4 requests (no skip, 100, 200, 250) and 250 cards. If the PO wants the strict "short page ends paging" rule instead, it is a one-line change in `CatalogPager.accept`. The drawback is that addons paging by 20 would stop after the first page.
- **Order of input checks**: the legacy `/stremio/v1` check and the `:11470` local-server check run **before** the `/manifest.json` suffix check. Otherwise a `/stremio/v1` URL could never produce `addon_error_legacy` (AC-S3). `stremio:///…` page links pasted into Add addon give `addon_error_not_manifest`.
- **Manifest `version`**: a bare JSON number is accepted and kept as text (`2` becomes `"2"`). `id`, `name` and `version` must be non-blank primitives.
- **Tolerance**: malformed catalogs, videos, streams, links and subtitles are dropped one by one, never the whole response. Catalog items without an `id` are dropped. Duplicate `(type, id)` catalogs keep the first. Resource entries that are not strings or named objects are dropped.
- **Meta outcomes**: `{"meta":null}` and `{"meta":{}}` are `Success(null)` (not found, not a failure). A 404 is a `Failure` with `isNotFound = true`, so the repository decides whether it counts as a partial failure.
- **MIME hint**: only HLS, DASH, Smooth Streaming and MPEG-TS get a `mimeType`. Progressive MP4 and MKV get `null`, so the platform player sniffs them. This matches how F1 channels without hints behave. `proxyHeaders.response` `Content-Type` is used only as a last-resort hint.
- **Headers from `proxyHeaders.request`**: header names that are not valid tokens and values containing CR/LF/NUL are dropped (header-injection guard). Numeric values become strings.
- **Streams**: an empty `url` falls through to `externalUrl`. `androidTvUrl` is treated like `externalUrl`. `url` streams with a non-http(s) scheme (rtmp, ftp, …) and local-server URLs are `Unsupported`. `Unsupported.rawKind` exists for tests and diagnostics only and must never reach the UI.
- **Cache**: manifests and the blocklist are never cached by the client (manifests are persisted by the repository). For streams, fresh plus stale time is capped at 5 minutes in total. Stale-while-revalidate is served only when a `refreshScope` is given; otherwise the response is refetched synchronously and stale-if-error still applies. `no-store` disables caching, and `no-cache` means max-age 0.
- **Timeouts**: the client applies `withTimeout(total)` (manifest, catalog and meta 15 s; streams 20 s) as a backstop over the engine's `HttpTimeout` (connect 10 s). Waiting for a concurrency permit does not count against the budget.
- **Blocklist host matching**: an entry matches that host and its subdomains (`evil.example` also blocks `cdn.evil.example`). `host:port` matches only that port. Ids match exactly. Persisting the last check time is left to the repository.
- **Extras beyond step 1**: DTOs and client calls for subtitles (F2b) and addon_catalog (format only), plus the `stremio:///` deep-link parser, are included because they are pure protocol and small. None of them browse any Stremio collection.

## QC round 1 fixes

| # | Fix |
|---|---|
| 1 | `StremioClient.withLimits` takes the **per-host permit first, then the global one**, always in that order. Requests queued behind one slow host no longer hold global permits. Test: `aSlowHostDoesNotStarveOtherHosts`. |
| 2 | `CatalogPager.onFailure(error): PageFailure`. After a short page, a 4xx, invalid-JSON or missing-root answer to the lenient probe request (a static host's 404 for `skip=250.json`) returns `END_OF_CATALOG` and ends paging silently. **The UI must not show Retry or count it in `addon_partial_failure`.** Anything else returns `FAILED`, and the same request can be retried. The paging snippet above is fixed (a failure never calls `accept(emptyList())`). |
| 3 | `ResourceMatcher.buildExtra` always puts `skip` **last** (core `extend_one`). Other extras keep declaration order. |
| 4 | `StremioVideo.hasInlineStreams` is `!streams.isNullOrEmpty()`, so `streams: []` still asks the stream addons. |
| 5 | Unsupported rows: `ClassifiedStream.name` is `""` and `description` is `null`. Addon text never acts as a kind label. |
| 6 | A `stremio:///` `externalUrl` the app cannot route (board, library, …) is `Unsupported("internal:unrouted")`, so it is not selectable. `StreamKind.Internal.link` is now non-null. |
| 7 | Header values allow only tab and 0x20–0x7E. `Host`, `Content-Length`, `Transfer-Encoding`, `Connection` and `Range` are dropped from `proxyHeaders.request`. The denylist does not apply to response hints. |
| 8 | A leading UTF-8 BOM is stripped before parsing, in the manifest parser and the client. |
| 9 | Videos without an `id` are dropped. |
| 10 | `KtorStremioHttpTransport` rejects `Content-Length` > `MAX_BODY_BYTES` (8 MiB) and reads the decoded body with the same limit (`bodyAsChannel().readRemaining(limit + 1)`). It throws `StremioBodyTooLargeException`, which maps to the new error code **`too_large`** (`AddonError.Kind.TOO_LARGE`). The limit is a constructor parameter. |
| 11 | Blocklist host entries are normalised: scheme, userinfo, path, query and fragment stripped, a leading `*.` and trailing `.` removed, case-folded, IPv6 brackets removed. A port in an entry is compared with the transport's effective port, so `host:443` matches `https://host/`. `isHostBlocked(host, port, scheme)`. |
| 12 | Harness: `/redirect/…` answers 307 for the manifest and every resource. `/cfg/{url-encoded JSON}/manifest.json?x=1` is a configured addon, and the log shows the wire URLs (config segment kept, `.json?x=1`). **https→http redirects are never followed** (`install(HttpRedirect) { allowHttpsDowngrade = false }` in `createStremioHttpClient`). The harness has no TLS, so QC cannot exercise this against it. |
| 13 | Harness: bad percent-escapes (and a non-JSON config segment) answer 400 instead of killing the process. |
| 14 | No stale-if-error for 4xx answers (RFC 5861). 5xx, timeouts and network errors still use the stale copy. `AddonError.isClientError`. |
| 15 | Pager test aligned with production: the client drops id-less items before the pager, so `skip` counts the valid items received. |
| 17 | `toString()` of `StreamKind.Playable` (host, header names), `ExternalBrowser` (host), `ClassifiedStream` (kind only) and `StremioDeepLink.Discover` (transport host) never contains URLs, paths or header values. |
| 18 | `isUsableSubtitleUrl` also drops `[::1]:11470`. `.m3u` no longer maps to HLS. |

## QC round 2 fixes

| # | Fix |
|---|---|
| N1 | After a short page, a **500** answer to the probe request (the SDK's `{"err":"handler error"}` when a handler rejects on no results) also returns `END_OF_CATALOG`. Timeouts, network errors and other 5xx (502, 503, …) stay `FAILED`. A 500 on the first page is still `FAILED`. |
| N2 | `StreamKind.Internal.toString()` is `Internal(link=$link)`, and the link is itself redacted (Discover shows the transport host only). `StremioStream`, `StremioSubtitle` and `StremioHttpResponse` have redacted `toString()`: hosts, header names and body length only, never URLs, header values or bodies. |
| N3 | For unsupported rows, `ClassifiedStream.bingeGroup` is `null` and `isRegionLimited` is `false`. Nothing addon-provided is exposed for them. |
| Nit | The BOM is written as the escape `"U+FEFF"` in `withoutBom()` (no invisible literal). `AddonBlocklistFetcher.fetch()` strips a leading BOM too. |

## QA harness (`qa/stremio-fixture/server.js`)

Run `node qa/stremio-fixture/server.js`. It listens on port 7000 on all interfaces and logs every request with its User-Agent, and logs `CANCELLED` when a client aborts. The emulator URL is `http://10.0.2.2:7000/manifest.json`.

- `/manifest.json`: SDK-style sampler (`org.tsiptv.publicdomain`). The movie catalogue has exactly 250 items (100/100/50/0 by skip). Search "his" finds His Girl Friday. Genre and skip work.
- The series `tspd_superman` has a season 0 special, "The Mechanical Monsters" and a 2099 upcoming episode. `tspd_inline` carries inline `video.streams`.
- The tv item `tspd_test_hls` streams through `/hls/…`, which proxies Mux and logs the UA (AC-S15). `tspd_mixed` returns url, infoHash, ytId and externalUrl streams (AC-S16).
- Stream responses send `cacheMaxAge: 604800` and the matching `Cache-Control` header (AC-S19).
- Variants: `/multi/` (genre `optionsLimit: 2`), `/v2/` (same id, version 1.0.1), `/p2p/`, `/adult/`, `/configure-required/` (with a `/configure` page), `/no-resources/`, `/slow/` (answers after 30 s), `/broken/` (500, HTML, missing root key).
- `/policy/addon-blocklist.json` is built from the `BLOCK_IDS` and `BLOCK_HOSTS` env vars (AC-S22). `AddonBlocklistFetcher` takes a `url` constructor parameter (production by default), so step 3 only needs a debug-only way to pass the harness URL.
- Smoke-tested locally with Node 24.
