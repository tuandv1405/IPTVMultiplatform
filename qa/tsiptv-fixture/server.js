#!/usr/bin/env node
/*
 * TS IPTV — TS IPTV Source QC fixture (F3). NEVER SHIPPED. Plain Node >= 18, no npm deps.
 *
 *   node qa/tsiptv-fixture/server.js                 # PORT=7100 by default
 *   PORT=7101 STREMIO=http://10.0.2.2:7000 node qa/tsiptv-fixture/server.js
 *
 * Android emulator: http://10.0.2.2:7100/root.tsiptv.json   Desktop / iOS simulator: http://127.0.0.1:7100/root.tsiptv.json
 * BASE (default http://10.0.2.2:<PORT>) is the host written into the documents; set BASE=http://127.0.0.1:7100 for desktop.
 * STREMIO (optional) is the base of qa/stremio-fixture; without it the stremio include points at a closed port
 * (a failing include, which is also a useful case).
 *
 * Every request is logged as `METHOD path  status  [If-None-Match] UA=… Referer=…` so QC can verify conditional
 * GET (304), the 50 MiB budget, header delivery and which includes a refresh fetched.
 *
 * Documents
 *   /root.tsiptv.json          appearance + layout (hero, rows, grid with group chips, catalog row, include rows),
 *                              channels (one with headers + epgId, one radio), a movie with 2 streams + subtitles,
 *                              a series (2 seasons, a future episode), includes: m3u `live`, xmltv `guide`,
 *                              stremio `addon`, tsiptv-source `nested`; ETag/Last-Modified (answers 304)
 *   /root.tsiptv.json.gz       the same document gzip-compressed, served WITHOUT Content-Encoding (spec: gzip files)
 *   /live.m3u                  4 channels (tvg-id, groups, x-tvg-url → /guide2.xml), ETag
 *   /guide.xml, /guide2.xml    XMLTV programmes around "now" for every channel (epgId / tvg-id), ETag
 *   /nested.tsiptv.json        a nested source with 2 channels and 1 movie; `meta.adult` follows /admin/adult
 *   /adult-root.tsiptv.json    root with meta.adult = true (preview 18+ checkbox)
 *   /adult-include.tsiptv.json root whose nested include is adult (include 18+ dialog)
 *   /cycle.tsiptv.json         includes itself (E_INCLUDE_CYCLE) and has one channel
 *   /only-includes.tsiptv.json no items; its only include fails (E_INCLUDE; "nothing could be loaded")
 *   /bad/not-json, /bad/not-source, /bad/no-id, /bad/version, /bad/too-large, /bad/empty   document errors
 *   /skips.tsiptv.json         item errors: E_ITEM_ID, E_DUPLICATE_ID, E_NO_STREAM (counted) + E_URL, E_HEADER_FORBIDDEN,
 *                              E_DRM (Details only, not counted) + warnings (W_QUERY_REF, W_CONTRAST, W_FIELD)
 *   /subs/en.vtt, /subs/vi.srt subtitles
 *   /hls/master.m3u8           logs the request headers, then 302 to a public test stream (header check)
 *
 * Switches (GET, answer the new state as text)
 *   /admin/adult?on=1|0        nested source meta.adult (refresh rule: withheld until confirmed in About)
 *   /admin/fail?name=live|guide|nested&on=1|0   make an include answer 500 (stale copy kept)
 *   /admin/revision            bump the root revision / ETag (a real change on the next refresh)
 *   /admin/reset
 */
'use strict'

const http = require('http')
const zlib = require('zlib')
const crypto = require('crypto')

const PORT = Number(process.env.PORT || 7100)
const BASE = (process.env.BASE || `http://10.0.2.2:${PORT}`).replace(/\/$/, '')
const STREMIO = (process.env.STREMIO || 'http://10.0.2.2:1').replace(/\/$/, '')
const TEST_HLS = 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8'
const TEST_MP4 = 'https://archive.org/download/his_girl_friday/his_girl_friday_512kb.mp4'

const state = { adult: false, fail: new Set(), revision: 1 }

function rootDoc() {
  return {
    $schema: 'https://tsiptv-8bdd6.web.app/schema/tsiptv-source-v1.json',
    format: 'tsiptv-source',
    version: 1,
    revision: state.revision,
    id: 'qa.tsiptv.fixture',
    meta: {
      name: { en: 'QA Fixture Source', vi: 'Nguồn kiểm thử QA' },
      description: { en: 'Local QC fixture for TS IPTV Sources.', vi: 'Nguồn kiểm thử nội bộ.' },
      author: { name: 'TS IPTV QA' },
      homepage: `${BASE}/about.html`,
      language: 'en',
      languages: ['en', 'vi'],
      updatedAt: '2026-09-29T10:00:00Z',
      logo: `${BASE}/logo.png`,
    },
    appearance: {
      accent: '#FFB300',
      background: { gradient: ['#101820', '#1E2A38'] },
      card: { style: 'auto', corner: 'large' },
    },
    layout: {
      home: [
        { type: 'hero', id: 'featured', items: [
          { image: 'https://archive.org/services/img/his_girl_friday', title: 'Movie hero', subtitle: 'Opens the movie', target: 'movie1' },
          { image: 'https://archive.org/services/img/superman_the_mechanical_monsters', title: 'Channel hero', target: 'news', autoplay: true },
          { image: 'https://archive.org/services/img/his_girl_friday', title: 'Decorative banner' },
        ] },
        { type: 'row', id: 'continue', title: { en: 'Continue watching', vi: 'Xem tiếp' }, query: { from: 'continueWatching' } },
        { type: 'row', id: 'news', title: 'News (own channels)', query: { from: 'channels', groups: ['News'] }, card: { style: 'logo' } },
        { type: 'row', id: 'live', title: 'From the M3U include', subtitle: 'with the XMLTV guide', query: { from: 'channels', include: 'live', sort: 'number' }, card: { style: 'list' } },
        { type: 'row', id: 'movies', title: { en: 'Movies', vi: 'Phim lẻ' }, query: { from: 'movies', sort: 'yearDesc' } },
        { type: 'row', id: 'series', title: { en: 'Series', vi: 'Phim bộ' }, query: { from: 'series' } },
        { type: 'row', id: 'nested', title: 'From the nested source', query: { from: 'channels', include: 'nested' } },
        { type: 'row', id: 'catalog', title: 'Addon catalogue', query: { from: 'catalog', include: 'addon', catalog: { type: 'movie', id: 'tspd-movie' }, limit: 20 } },
        // Not declared by the addon: skipped with W_QUERY_REF in About › Details (QC #12).
        { type: 'row', id: 'catalog-missing', title: 'Missing catalogue', query: { from: 'catalog', include: 'addon', catalog: { type: 'movie', id: 'no-such-catalog' } } },
        { type: 'row', id: 'radio', title: 'Radio', query: { from: 'radio' } },
        { type: 'grid', id: 'all', title: { en: 'All channels', vi: 'Tất cả kênh' }, query: { from: 'channels', sort: 'number' }, groupChips: true },
      ],
    },
    channels: [
      { id: 'news', name: { en: 'QA News', vi: 'Tin tức QA' }, number: 1, groups: ['News'], epgId: 'qa.news', url: TEST_HLS },
      // Several streams (spec §8.4): a DRM one first, so iOS/desktop fall back to the next one (§8.5);
      // no epgId: matched to the guide by its name "QA Multi" (§8.1).
      { id: 'multi', name: 'QA Multi', number: 4, groups: ['News'], streams: [
        { url: 'https://cdn.example.com/drm/manifest.mpd', name: 'DRM (Android)', drm: { system: 'widevine', licenseUrl: 'https://license.example.com/wv' } },
        { url: TEST_HLS, name: 'HLS' },
        { url: TEST_MP4, name: 'MP4' },
      ] },
      { id: 'headers', name: 'QA Headers', number: 2, groups: ['News'], epgId: 'qa.headers',
        url: `${BASE}/hls/master.m3u8`, headers: { 'User-Agent': 'TSIPTV-QA/1.0', Referer: 'https://qa.example/' } },
      { id: 'radio1', type: 'radio', name: 'QA Radio', number: 3, url: 'https://radio.example.com/stream.aac' },
    ],
    movies: [
      { id: 'movie1', name: 'His Girl Friday (QA)', year: 1940, genres: ['Comedy'], poster: 'https://archive.org/services/img/his_girl_friday',
        backdrop: 'https://archive.org/services/img/his_girl_friday', description: 'Two streams and two subtitle tracks.',
        streams: [ { url: TEST_MP4, name: 'SD' }, { url: TEST_HLS, name: 'HLS test' } ],
        subtitles: [ { url: `${BASE}/subs/en.vtt`, language: 'en', label: 'English' }, { url: `${BASE}/subs/vi.srt`, language: 'vi', format: 'srt' } ] },
    ],
    series: [
      { id: 'show1', name: 'QA Show', year: 2024, poster: 'https://archive.org/services/img/superman_the_mechanical_monsters',
        seasons: [
          { number: 1, episodes: [ { id: 's1e1', number: 1, name: 'Pilot', url: TEST_HLS }, { id: 's1e2', number: 2, name: 'Second', url: TEST_MP4 } ] },
          { number: 2, episodes: [ { id: 's2e1', number: 1, name: 'Upcoming', url: TEST_HLS, releaseDate: '2099-01-01' } ] },
        ] },
    ],
    epg: [ `${BASE}/guide.xml` ],
    includes: [
      // Include headers go to the include request only (AC-T12: see the log for /live.m3u vs the streams).
      { id: 'live', type: 'm3u', url: `${BASE}/live.m3u`, name: 'QA M3U', refreshHours: 1, headers: { 'User-Agent': 'TSIPTV-QA-Include/1.0', Referer: 'https://include.qa.example/' } },
      { id: 'guide', type: 'xmltv', url: `${BASE}/guide2.xml`, name: 'QA XMLTV', refreshHours: 12 },
      { id: 'addon', type: 'stremio', url: `${STREMIO}/manifest.json`, name: 'QA addon' },
      { id: 'nested', type: 'tsiptv-source', url: `${BASE}/nested.tsiptv.json`, name: 'QA nested source', refreshHours: 1 },
    ],
  }
}

function nestedDoc() {
  return {
    format: 'tsiptv-source', version: 1, id: 'qa.tsiptv.nested',
    meta: { name: 'QA nested', adult: state.adult },
    channels: [
      { id: 'n1', name: state.adult ? 'Nested (after adult switch)' : 'Nested One', url: TEST_HLS },
      { id: 'n2', name: 'Nested Two', url: TEST_HLS },
    ],
    movies: [ { id: 'nm1', name: 'Nested movie', url: TEST_MP4 } ],
  }
}

const docs = {
  '/adult-root.tsiptv.json': () => ({ format: 'tsiptv-source', version: 1, id: 'qa.tsiptv.adult', meta: { name: 'QA adult root', adult: true },
    channels: [ { id: 'a', name: 'Adult root channel', url: TEST_HLS } ] }),
  '/adult-include.tsiptv.json': () => ({ format: 'tsiptv-source', version: 1, id: 'qa.tsiptv.adultinc', meta: { name: 'QA adult include' },
    channels: [ { id: 'own', name: 'Own channel', url: TEST_HLS } ],
    includes: [ { id: 'x', type: 'tsiptv-source', url: `${BASE}/adult-root.tsiptv.json` } ] }),
  '/cycle.tsiptv.json': () => ({ format: 'tsiptv-source', version: 1, id: 'qa.tsiptv.cycle', meta: { name: 'QA cycle' },
    channels: [ { id: 'c', name: 'Cycle channel', url: TEST_HLS } ],
    includes: [ { id: 'self', type: 'tsiptv-source', url: `${BASE}/cycle.tsiptv.json` } ] }),
  '/only-includes.tsiptv.json': () => ({ format: 'tsiptv-source', version: 1, id: 'qa.tsiptv.onlyinc', meta: { name: 'QA only includes' },
    includes: [ { id: 'gone', type: 'm3u', url: `${BASE}/missing.m3u` } ] }),
  '/skips.tsiptv.json': () => ({ format: 'tsiptv-source', version: 1, id: 'qa.tsiptv.skips', meta: { name: 'QA skips' },
    appearance: { accent: '#111111', background: { color: '#FFFFFF' } },
    layout: { home: [ { type: 'row', query: { from: 'channels', include: 'nope' } }, { type: 'grid', query: { from: 'channels' } } ] },
    channels: [
      { id: 'ok', name: 'OK', url: TEST_HLS, logo: 'ftp://bad.example/logo.png', headers: { Host: 'x', 'X-Fine': '1' } },
      { id: 'bad id!', name: 'Bad id', url: TEST_HLS },
      { id: 'ok', name: 'Duplicate', url: TEST_HLS },
      { id: 'nostream', name: 'No stream', url: 'rtmp://nope.example/live' },
      { id: 'drm', name: 'DRM dropped stream', streams: [ { url: TEST_HLS, drm: { system: 'widevine' } }, { url: TEST_HLS } ] },
      { id: 'field', name: 'Wrong field', url: TEST_HLS, number: 'three' },
    ] }),
  // Detection is by content (spec section 3): these carry the "format" marker, so they reach the source parser.
  '/bad/not-json': () => '{"format": "tsiptv-source", "version": 1, "id": "broken", ',
  '/bad/not-source': () => ({ note: { format: 'tsiptv-source' }, version: 1, id: 'x', meta: { name: 'x' } }),
  '/bad/no-id': () => ({ format: 'tsiptv-source', version: 1, meta: { name: 'No id' }, channels: [ { id: 'a', name: 'A', url: TEST_HLS } ] }),
  '/bad/version': () => ({ format: 'tsiptv-source', version: 99, id: 'qa.v99', meta: { name: 'Future' } }),
  '/bad/empty': () => ({ format: 'tsiptv-source', version: 1, id: 'qa.empty', meta: { name: 'Empty' } }),
}

function m3u() {
  return [
    `#EXTM3U x-tvg-url="${BASE}/guide2.xml"`,
    '#EXTINF:-1 tvg-id="qa.m3u.one" tvg-chno="10" group-title="Movies",M3U One',
    TEST_HLS,
    '#EXTINF:-1 tvg-id="qa.m3u.two" tvg-chno="11" group-title="Sport",M3U Two',
    TEST_HLS,
    '#EXTINF:-1 tvg-id="QA@HD" tvg-chno="12" group-title="Sport",M3U Three (id with @)',
    TEST_HLS,
    '#EXTINF:-1 radio="true" group-title="Radio",M3U Radio',
    'https://radio.example.com/m3u.aac',
    '',
  ].join('\n')
}

function xmltv(ids) {
  const now = Date.now()
  const fmt = (t) => new Date(t).toISOString().replace(/[-:T]/g, '').slice(0, 14) + ' +0000'
  const hour = 3600000
  const start = Math.floor(now / hour) * hour
  let out = '<?xml version="1.0" encoding="UTF-8"?>\n<tv>\n'
  const names = { 'qa.multi.byname': 'QA Multi' }
  for (const id of ids) out += `  <channel id="${id}"><display-name>${names[id] || id}</display-name></channel>\n`
  for (const id of ids) {
    for (let i = -2; i < 6; i++) {
      out += `  <programme start="${fmt(start + i * hour)}" stop="${fmt(start + (i + 1) * hour)}" channel="${id}"><title>${id} show ${i + 3}</title></programme>\n`
    }
  }
  return out + '</tv>\n'
}

const VTT = 'WEBVTT\n\n00:00:01.000 --> 00:00:05.000\nQA English subtitle\n'
const SRT = '1\n00:00:01,000 --> 00:00:05,000\nPhụ đề tiếng Việt QA\n'

function etagOf(body) {
  return '"' + crypto.createHash('sha1').update(body).digest('hex').slice(0, 16) + '"'
}

function send(req, res, status, body, type, extra = {}) {
  res.writeHead(status, { 'Content-Type': type, ...extra })
  res.end(body)
  log(req, status)
}

function sendConditional(req, res, body, type) {
  const etag = etagOf(body)
  if (req.headers['if-none-match'] === etag) {
    res.writeHead(304, { ETag: etag })
    res.end()
    return log(req, 304)
  }
  send(req, res, 200, body, type, { ETag: etag, 'Last-Modified': new Date(Date.UTC(2026, 8, 1)).toUTCString() })
}

function log(req, status) {
  const inm = req.headers['if-none-match'] ? ` INM=${req.headers['if-none-match']}` : ''
  console.log(`${req.method} ${req.url}  ${status}${inm}  UA=${req.headers['user-agent'] || '-'}  Referer=${req.headers.referer || '-'}`)
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x')
  const path = url.pathname
  const json = (value) => typeof value === 'string' ? value : JSON.stringify(value, null, 2)

  if (path.startsWith('/admin/')) {
    if (path === '/admin/adult') state.adult = url.searchParams.get('on') === '1'
    if (path === '/admin/fail') {
      const name = url.searchParams.get('name')
      if (url.searchParams.get('on') === '1') state.fail.add(name); else state.fail.delete(name)
    }
    if (path === '/admin/revision') state.revision++
    if (path === '/admin/reset') { state.adult = false; state.fail.clear(); state.revision = 1 }
    return send(req, res, 200, JSON.stringify({ adult: state.adult, fail: [...state.fail], revision: state.revision }), 'application/json')
  }

  const failing = { '/live.m3u': 'live', '/guide2.xml': 'guide', '/nested.tsiptv.json': 'nested' }[path]
  if (failing && state.fail.has(failing)) return send(req, res, 500, 'fixture failure', 'text/plain')

  switch (path) {
    case '/root.tsiptv.json': return sendConditional(req, res, json(rootDoc()), 'application/json')
    case '/root.tsiptv.json.gz': return send(req, res, 200, zlib.gzipSync(json(rootDoc())), 'application/octet-stream')
    case '/nested.tsiptv.json': return sendConditional(req, res, json(nestedDoc()), 'application/json')
    case '/live.m3u': return sendConditional(req, res, m3u(), 'audio/x-mpegurl')
    case '/guide.xml': return sendConditional(req, res, xmltv(['qa.news', 'qa.headers', 'qa.multi.byname']), 'application/xml')
    case '/guide2.xml': return sendConditional(req, res, xmltv(['qa.m3u.one', 'qa.m3u.two', 'QA@HD']), 'application/xml')
    case '/subs/en.vtt': return send(req, res, 200, VTT, 'text/vtt')
    case '/subs/vi.srt': return send(req, res, 200, SRT, 'application/x-subrip')
    case '/hls/master.m3u8':
      res.writeHead(302, { Location: TEST_HLS })
      res.end()
      return log(req, 302)
    case '/bad/too-large': {
      // 6 MiB of valid JSON: rejected with E_TOO_LARGE (5 MiB cap after decompression).
      const filler = 'x'.repeat(6 * 1024 * 1024)
      return send(req, res, 200, `{"format":"tsiptv-source","version":1,"id":"qa.big","meta":{"name":"Big","x-pad":"${filler}"}}`, 'application/json')
    }
  }
  if (docs[path]) return send(req, res, 200, json(docs[path]()), 'application/json')
  send(req, res, 404, 'not found', 'text/plain')
})

server.listen(PORT, () => {
  console.log(`TS IPTV Source fixture on :${PORT}  BASE=${BASE}  STREMIO=${STREMIO}`)
  console.log(`  root: ${BASE}/root.tsiptv.json`)
})
