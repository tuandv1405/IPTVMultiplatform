#!/usr/bin/env node
/*
 * TS IPTV — Stremio-compatible addon QC fixture (F2). NEVER SHIPPED. Plain Node >= 18, no npm deps.
 *
 *   node qa/stremio-fixture/server.js            # PORT=7000 by default
 *   PORT=7001 BLOCK_HOSTS=10.0.2.2 node qa/stremio-fixture/server.js
 *
 * Android emulator: http://10.0.2.2:7000/manifest.json   Desktop / iOS simulator: http://127.0.0.1:7000/manifest.json
 * Every request is logged as `METHOD path  UA=…` so QC can verify skip/genre/search requests,
 * cancellation (AC-S12) and the proxy headers on HLS requests (AC-S15).
 *
 * Addons (each is a separate transport URL on this server):
 *   /manifest.json                       Public Domain Sampler (SDK-style): movie catalogue of exactly 250 items
 *                                        (His Girl Friday + 249 generated, all pointing at the same public-domain MP4),
 *                                        search + genre + skip; series with season 0 and a future episode; a tv item
 *                                        whose HLS goes through /hls/ (logged) with proxyHeaders User-Agent; a movie
 *                                        `tspd_mixed` whose streams are url + infoHash + ytId + externalUrl (AC-S16);
 *                                        a movie `tspd_inline` series-like item with inline video.streams (AC-S18).
 *   /multi/manifest.json                 Same data, genre with optionsLimit 2 (AC-S11), id org.tsiptv.fixture.multi
 *   /v2/manifest.json                    Same id as / with version 1.0.1 (replace flow, AC-S6)
 *   /p2p/manifest.json                   behaviorHints.p2p = true (AC-S5)
 *   /adult/manifest.json                 behaviorHints.adult = true (AC-S5)
 *   /configure-required/manifest.json    configurationRequired = true (AC-S4); /configure-required/configure is a page
 *   /no-resources/manifest.json          missing `resources` (AC-S4)
 *   /slow/manifest.json                  catalog/meta/stream answers after 30 s (timeouts, partial failure AC-S12)
 *   /broken/manifest.json                catalog/meta/stream answer 500 / HTML / missing root key
 *   /redirect/…                          307 to the same path without /redirect (manifest and every resource;
 *                                        the SDK `redirect` behaviour). https→http redirects are not tested here
 *                                        (no TLS); the app client never follows them (Ktor allowHttpsDowngrade=false).
 *   /cfg/{config}/manifest.json?x=1      configured addon: {config} is a URL-encoded JSON object (400 if not),
 *                                        e.g. /cfg/%7B%22k%22%3A1%7D/manifest.json?x=1 — the log shows that every
 *                                        resource request keeps the config segment and ends with `.json?x=1`.
 * Bad percent-escapes answer 400 instead of crashing the server.
 *   /policy/addon-blocklist.json         blocklist fixture from env BLOCK_IDS / BLOCK_HOSTS (comma-separated, AC-S22)
 */
'use strict'

const http = require('http')
const https = require('https')

const PORT = parseInt(process.env.PORT || '7000', 10)
const PAGE = 100
const MOVIE_TOTAL = 250
const MP4 = 'https://archive.org/download/his_girl_friday/his_girl_friday_512kb.mp4'
const SUPERMAN_MP4 = 'https://archive.org/download/superman_the_mechanical_monsters/superman_the_mechanical_monsters_512kb.mp4'
const POSTER = 'https://archive.org/services/img/his_girl_friday'
const GENRES = ['Animation', 'Comedy', 'Drama', 'Test']

// ---------------------------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------------------------

function movies () {
  const list = [{
    id: 'tspd_his_girl_friday', type: 'movie', name: 'His Girl Friday', releaseInfo: '1940',
    genres: ['Comedy'], poster: POSTER, description: 'Howard Hawks screwball comedy (public domain).',
    runtime: '92 min', released: '1940-01-18T00:00:00.000Z'
  }, {
    id: 'tspd_mixed', type: 'movie', name: 'Mixed stream kinds (fixture)', releaseInfo: '2026',
    genres: ['Test'], poster: POSTER, description: 'Returns one url, one infoHash, one ytId and one externalUrl stream.'
  }]
  for (let i = list.length + 1; i <= MOVIE_TOTAL; i++) {
    const n = String(i).padStart(3, '0')
    list.push({
      id: `tspd_gen_${n}`, type: 'movie', name: `Generated Movie ${n}`, releaseInfo: String(1900 + (i % 100)),
      genres: [GENRES[i % 3]], poster: POSTER, description: `Generated fixture item ${n} (same public-domain MP4).`
    })
  }
  return list
}

const MOVIES = movies()

const SERIES = [{
  id: 'tspd_superman', type: 'series', name: 'Fleischer Superman (1941-43)', releaseInfo: '1941-1943',
  genres: ['Animation'], poster: 'https://archive.org/services/img/superman_the_mechanical_monsters',
  description: 'Fleischer Studios Superman cartoons, public domain.',
  videos: [
    { id: 'tspd_superman_s0e1', title: 'Behind the scenes (special)', season: 0, episode: 1, released: '1941-01-01T00:00:00.000Z' },
    { id: 'tspd_superman_s1e2', title: 'Second fixture episode', season: 1, episode: 2, released: '1941-12-01T00:00:00.000Z' },
    { id: 'tspd_superman_s1e1', title: 'The Mechanical Monsters', season: 1, episode: 1, released: '1941-11-28T00:00:00.000Z',
      thumbnail: 'https://archive.org/services/img/superman_the_mechanical_monsters' },
    { id: 'tspd_superman_s2e1', name: 'Upcoming fixture episode', season: 2, episode: 1, released: '2099-01-01T00:00:00.000Z' }
  ]
}, {
  id: 'tspd_inline', type: 'series', name: 'Inline streams (fixture)', genres: ['Test'], poster: POSTER,
  description: 'Its video carries inline streams: no /stream/ request may be made (AC-S18).',
  videos: [{
    id: 'tspd_inline_s1e1', title: 'Inline episode', season: 1, episode: 1, released: '1941-11-28T00:00:00.000Z',
    streams: [{ name: 'inline', description: 'Inline MP4', url: SUPERMAN_MP4 }]
  }]
}]

function tvItems (host) {
  return [{
    id: 'tspd_test_hls', type: 'tv', name: 'HLS Test Channel (Mux test stream)', genres: ['Test'],
    posterShape: 'square', poster: POSTER, behaviorHints: { isLive: true },
    description: 'Public HLS test stream from Mux, proxied through this fixture so headers are logged.',
    streamUrl: `http://${host}/hls/x36xhzz/x36xhzz.m3u8`
  }]
}

function streamsFor (type, id, host) {
  if (type === 'movie' && id === 'tspd_mixed') {
    return [
      { name: 'Direct', description: 'Plays in TS IPTV', url: MP4, behaviorHints: { filename: 'his_girl_friday_512kb.mp4' } },
      { name: 'Fixture A', description: 'Must be hidden by default', infoHash: '0000000000000000000000000000000000000000', fileIdx: 0 },
      { name: 'Fixture B', description: 'Must be hidden by default', ytId: 'aqz-KE-bpKQ' },
      { name: 'Website', description: 'Opens the browser', externalUrl: 'https://archive.org/details/his_girl_friday' }
    ]
  }
  if (type === 'movie' && MOVIES.some(m => m.id === id)) {
    return [{ name: 'archive.org', description: 'MP4 · 512kb', url: MP4,
      behaviorHints: { filename: 'his_girl_friday_512kb.mp4', bingeGroup: 'tspd-archive-512kb' } }]
  }
  if (type === 'series') {
    for (const s of SERIES) {
      if (s.videos.some(v => v.id === id)) {
        return [{ name: 'archive.org', description: 'MP4 · 512kb', url: SUPERMAN_MP4,
          behaviorHints: { filename: 'superman_512kb.mp4', bingeGroup: 'tspd-archive-512kb' } }]
      }
    }
  }
  if (type === 'tv') {
    const item = tvItems(host).find(t => t.id === id)
    if (item) {
      return [{ name: 'HLS', description: 'Mux public test stream (Big Buck Bunny, CC-BY Blender Foundation)',
        url: item.streamUrl,
        behaviorHints: { notWebReady: true, proxyHeaders: { request: { 'User-Agent': 'TSIPTV-Example/1.0' } } } }]
    }
  }
  return []
}

// ---------------------------------------------------------------------------------------------
// Manifests
// ---------------------------------------------------------------------------------------------

function manifest (variant) {
  const genreExtra = { name: 'genre', isRequired: false, options: GENRES }
  if (variant === 'multi') genreExtra.optionsLimit = 2
  const m = {
    id: 'org.tsiptv.publicdomain',
    version: variant === 'v2' ? '1.0.1' : '1.0.0',
    name: 'Public Domain Sampler (local fixture)',
    description: 'QC fixture: public-domain films and cartoons from archive.org, plus a public HLS test channel.',
    resources: ['catalog', 'meta', 'stream'],
    types: ['movie', 'series', 'tv'],
    idPrefixes: ['tspd_'],
    catalogs: ['movie', 'series', 'tv'].map(type => ({
      type, id: 'tspd-' + type, name: 'Public Domain ' + type,
      extra: [{ name: 'search', isRequired: false }, genreExtra, { name: 'skip', isRequired: false }]
    })),
    behaviorHints: { adult: false, p2p: false }
  }
  switch (variant) {
    case 'multi': m.id = 'org.tsiptv.fixture.multi'; m.name = 'Fixture: multi-genre'; break
    case 'p2p': m.id = 'org.tsiptv.fixture.p2p'; m.name = 'Fixture: p2p'; m.behaviorHints.p2p = true; break
    case 'adult': m.id = 'org.tsiptv.fixture.adult'; m.name = 'Fixture: adult flag'; m.behaviorHints.adult = true; break
    case 'configure-required':
      m.id = 'org.tsiptv.fixture.configure'; m.name = 'Fixture: configuration required'
      m.behaviorHints.configurable = true; m.behaviorHints.configurationRequired = true; break
    case 'no-resources': m.id = 'org.tsiptv.fixture.noresources'; delete m.resources; break
    case 'slow': m.id = 'org.tsiptv.fixture.slow'; m.name = 'Fixture: slow (30 s)'; break
    case 'broken': m.id = 'org.tsiptv.fixture.broken'; m.name = 'Fixture: broken responses'; break
    case 'cfg': m.id = 'org.tsiptv.fixture.cfg'; m.name = 'Fixture: configured (config segment + query)'; break
  }
  return m
}

const VARIANTS = new Set(['multi', 'v2', 'p2p', 'adult', 'configure-required', 'no-resources', 'slow', 'broken', 'redirect'])

// ---------------------------------------------------------------------------------------------
// Handlers (SDK semantics: extra parsed from the raw URL, `+` is NOT a space in paths)
// ---------------------------------------------------------------------------------------------

function parseExtra (raw) {
  const out = {}
  if (!raw) return out
  for (const part of raw.split('&')) {
    if (!part) continue
    const eq = part.indexOf('=')
    const k = decodeURIComponent(eq < 0 ? part : part.slice(0, eq))
    const v = decodeURIComponent(eq < 0 ? '' : part.slice(eq + 1))
    if (k in out) out[k] = [].concat(out[k], v); else out[k] = v
  }
  return out
}

const preview = i => ({ id: i.id, type: i.type, name: i.name, poster: i.poster, posterShape: i.posterShape,
  genres: i.genres, releaseInfo: i.releaseInfo, description: i.description, behaviorHints: i.behaviorHints })

function catalog (type, id, extra, host) {
  if (id !== 'tspd-' + type) return { metas: [] }
  let list = type === 'movie' ? MOVIES : type === 'series' ? SERIES : type === 'tv' ? tvItems(host) : []
  if (extra.genre) {
    const wanted = [].concat(extra.genre)
    list = list.filter(i => (i.genres || []).some(g => wanted.includes(g)))
  }
  if (extra.search) {
    const q = String(extra.search).toLowerCase()
    list = list.filter(i => i.name.toLowerCase().includes(q))
  }
  const skip = parseInt(extra.skip || '0', 10) || 0
  return { metas: list.slice(skip, skip + PAGE).map(preview), cacheMaxAge: 3600 }
}

function meta (type, id, host) {
  const all = [...MOVIES, ...SERIES, ...tvItems(host)]
  const i = all.find(x => x.id === id && x.type === type)
  if (!i) return { meta: null }
  const m = { ...preview(i), runtime: i.runtime, released: i.released }
  if (i.videos) m.videos = i.videos
  return { meta: m, cacheMaxAge: 3600 }
}

// ---------------------------------------------------------------------------------------------
// HTTP
// ---------------------------------------------------------------------------------------------

function send (res, status, body, cacheMaxAge) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*' }
  if (cacheMaxAge) headers['Cache-Control'] = `max-age=${cacheMaxAge}, public`
  res.writeHead(status, headers)
  res.end(typeof body === 'string' ? body : JSON.stringify(body))
}

function proxyHls (req, res, path) {
  const target = 'https://test-streams.mux.dev/' + path
  https.get(target, upstream => {
    res.writeHead(upstream.statusCode || 502, {
      'Content-Type': upstream.headers['content-type'] || 'application/octet-stream',
      'Access-Control-Allow-Origin': '*'
    })
    upstream.pipe(res)
  }).on('error', () => { res.writeHead(502); res.end() })
}

const server = http.createServer((req, res) => {
  try {
    handle(req, res)
  } catch (e) {
    if (e instanceof URIError) return send(res, 400, { err: 'bad percent-encoding' })
    console.error(e)
    if (!res.headersSent) send(res, 500, { err: 'handler error' })
  }
})

function handle (req, res) {
  const started = Date.now()
  const rawUrl = req.url || '/'
  const host = req.headers.host || `127.0.0.1:${PORT}`
  res.on('finish', () => console.log(`${req.method} ${rawUrl} -> ${res.statusCode} ${Date.now() - started}ms UA=${req.headers['user-agent'] || '-'}`))
  res.on('close', () => { if (!res.writableFinished) console.log(`CANCELLED ${rawUrl} after ${Date.now() - started}ms`) })

  const pathOnly = rawUrl.split('?')[0]

  if (pathOnly.startsWith('/hls/')) return proxyHls(req, res, pathOnly.slice(5))

  if (pathOnly === '/policy/addon-blocklist.json') {
    const list = s => (s || '').split(',').map(x => x.trim()).filter(Boolean)
    return send(res, 200, { ids: list(process.env.BLOCK_IDS), hosts: list(process.env.BLOCK_HOSTS) })
  }

  // Optional variant prefix.
  const segs = pathOnly.split('/').filter(Boolean)
  let variant = null
  if (segs.length && VARIANTS.has(segs[0])) variant = segs.shift()
  if (variant === 'redirect') {
    res.writeHead(307, { Location: rawUrl.replace(/^\/redirect/, '') || '/' })
    return res.end()
  }
  if (segs[0] === 'cfg' && segs.length >= 2) {
    segs.shift()
    const config = segs.shift()
    try {
      const parsed = JSON.parse(decodeURIComponent(config))
      if (typeof parsed !== 'object' || parsed === null) throw new SyntaxError('not an object')
    } catch (e) {
      return send(res, 400, { err: 'bad config segment' })
    }
    variant = 'cfg'
  }

  if (segs.length === 1 && segs[0] === 'configure') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
    return res.end(`<html><body><p>Fixture configure page. Configured link:</p><pre>stremio://${host}/manifest.json</pre></body></html>`)
  }
  if (segs.length === 1 && segs[0] === 'manifest.json') return send(res, 200, manifest(variant), 600)

  if (segs.length < 3 || !segs[segs.length - 1].endsWith('.json')) return send(res, 404, { err: 'not found' })
  segs[segs.length - 1] = segs[segs.length - 1].slice(0, -5)
  const [resource, type, id, extraRaw] = [segs[0], decodeURIComponent(segs[1]), decodeURIComponent(segs[2]), segs[3]]
  const extra = parseExtra(extraRaw)

  const answer = () => {
    if (variant === 'broken') {
      if (resource === 'catalog') return send(res, 500, { err: 'handler error' })
      if (resource === 'meta') { res.writeHead(200, { 'Content-Type': 'text/html' }); return res.end('<html>oops</html>') }
      return send(res, 200, { err: 'missing root key' })
    }
    switch (resource) {
      case 'catalog': { const b = catalog(type, id, extra, host); return send(res, 200, b, b.cacheMaxAge) }
      case 'meta': { const b = meta(type, id, host); return send(res, 200, b, b.cacheMaxAge) }
      case 'stream': {
        // SDK-like caching that the client must cap at 5 minutes (AC-S19).
        const b = { streams: streamsFor(type, id, host), cacheMaxAge: 604800, staleRevalidate: 7776000, staleError: 31104000 }
        res.setHeader('Cache-Control', 'max-age=604800, stale-while-revalidate=7776000, stale-if-error=31104000, public')
        return send(res, 200, b)
      }
      default: return send(res, 404, { err: 'not found' })
    }
  }
  if (variant === 'slow') setTimeout(answer, 30000); else answer()
}

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Stremio fixture addon: http://127.0.0.1:${PORT}/manifest.json (emulator: http://10.0.2.2:${PORT}/manifest.json)`)
  console.log(`Movie catalogue: ${MOVIES.length} items. Variants: ${[...VARIANTS].map(v => '/' + v + '/manifest.json').join(' ')}`)
})
