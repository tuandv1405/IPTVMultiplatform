/*
 * M3U / M3U8 playlist parser for the TV app. ES2015, no dependencies; also loaded by Node for the
 * tests (test/m3u.test.js).
 *
 * Supported: #EXTINF attributes (tvg-id, tvg-name, tvg-logo, tvg-chno, group-title, radio),
 * #EXTGRP, #EXTVLCOPT http-user-agent / http-referrer, Kodi-style "url|Header=value" suffixes,
 * #KODIPROP DRM lines (the channel is kept but marked drm: the TV players cannot play it), and
 * relative stream URLs (resolved against the playlist URL).
 */
(function (root) {
  'use strict';

  var ATTR_RE = /([A-Za-z0-9_-]+)="([^"]*)"/g;

  function parseAttributes(text) {
    var attrs = {};
    var m;
    ATTR_RE.lastIndex = 0;
    while ((m = ATTR_RE.exec(text)) !== null) attrs[m[1].toLowerCase()] = m[2];
    return attrs;
  }

  /** "#EXTINF:-1 a="b" c="d",Name" -> { attrs, name }. The name follows the first comma outside quotes. */
  function parseExtinf(line) {
    var body = line.slice(line.indexOf(':') + 1);
    var inQuotes = false;
    var comma = -1;
    for (var i = 0; i < body.length; i++) {
      var c = body.charAt(i);
      if (c === '"') inQuotes = !inQuotes;
      else if (c === ',' && !inQuotes) { comma = i; break; }
    }
    var head = comma >= 0 ? body.slice(0, comma) : body;
    var name = comma >= 0 ? body.slice(comma + 1).trim() : '';
    return { attrs: parseAttributes(head), name: name };
  }

  function resolveUrl(url, base) {
    if (/^[a-z][a-z0-9+.-]*:/i.test(url) || !base) return url;
    try { return new URL(url, base).toString(); } catch (e) { return url; }
  }

  /** "url|User-Agent=x&Referer=y" -> { url, headers }. Values may be percent-encoded. */
  function splitPipeHeaders(raw) {
    var bar = raw.indexOf('|');
    if (bar < 0) return { url: raw, headers: {} };
    var headers = {};
    raw.slice(bar + 1).split('&').forEach(function (pair) {
      var eq = pair.indexOf('=');
      if (eq <= 0) return;
      var key = pair.slice(0, eq).trim().toLowerCase();
      var value = pair.slice(eq + 1);
      try { value = decodeURIComponent(value); } catch (e) { /* keep as is */ }
      headers[key] = value;
    });
    return { url: raw.slice(0, bar), headers: headers };
  }

  /**
   * Parses playlist text. Returns { channels, epgUrl }. Each channel:
   * { name, url, logo, group, tvgId, number, radio, drm, userAgent, referrer }.
   */
  function parse(text, playlistUrl) {
    var lines = String(text || '').replace(/^﻿/, '').split(/\r?\n/);
    var channels = [];
    var epgUrl = null;
    var pending = null;

    function fresh() {
      return { name: '', logo: '', group: '', tvgId: '', number: null, radio: false, drm: false, userAgent: '', referrer: '' };
    }

    for (var i = 0; i < lines.length; i++) {
      var line = lines[i].trim();
      if (!line) continue;

      if (line.charAt(0) === '#') {
        var upper = line.slice(0, 12).toUpperCase();
        if (upper.indexOf('#EXTM3U') === 0) {
          var head = parseAttributes(line);
          epgUrl = head['x-tvg-url'] || head['url-tvg'] || epgUrl;
        } else if (upper.indexOf('#EXTINF') === 0) {
          var info = parseExtinf(line);
          pending = fresh();
          pending.name = info.name || info.attrs['tvg-name'] || '';
          pending.logo = info.attrs['tvg-logo'] || '';
          pending.group = (info.attrs['group-title'] || '').split(';')[0].trim();
          pending.tvgId = info.attrs['tvg-id'] || '';
          var chno = parseInt(info.attrs['tvg-chno'], 10);
          pending.number = isNaN(chno) ? null : chno;
          pending.radio = info.attrs.radio === 'true';
        } else if (upper.indexOf('#EXTGRP') === 0) {
          if (!pending) pending = fresh();
          if (!pending.group) pending.group = line.slice(line.indexOf(':') + 1).trim();
        } else if (upper.indexOf('#EXTVLCOPT') === 0) {
          if (!pending) pending = fresh();
          var opt = line.slice(line.indexOf(':') + 1);
          var eq = opt.indexOf('=');
          var key = opt.slice(0, eq).trim().toLowerCase();
          var value = opt.slice(eq + 1).trim();
          if (key === 'http-user-agent') pending.userAgent = value;
          else if (key === 'http-referrer' || key === 'http-referer') pending.referrer = value;
        } else if (upper.indexOf('#KODIPROP') === 0) {
          if (!pending) pending = fresh();
          if (/license_type|license_key|drm/i.test(line)) pending.drm = true;
        }
        continue;
      }

      // A stream URL line.
      var split = splitPipeHeaders(line);
      var ch = pending || fresh();
      ch.url = resolveUrl(split.url.trim(), playlistUrl);
      if (split.headers['user-agent']) ch.userAgent = split.headers['user-agent'];
      if (split.headers.referer || split.headers.referrer) ch.referrer = split.headers.referer || split.headers.referrer;
      if (!ch.name) ch.name = ch.url.replace(/[?#].*$/, '').split('/').pop() || ch.url;
      if (/^(https?|rtmp|rtsp|udp|rtp):/i.test(ch.url)) channels.push(ch);
      pending = null;
    }
    return { channels: channels, epgUrl: epgUrl };
  }

  /** True when the text looks like an M3U playlist (used to give a clear error for HTML pages etc.). */
  function looksLikeM3u(text) {
    var head = String(text || '').replace(/^﻿/, '').slice(0, 2048);
    return /#EXTM3U|#EXTINF/i.test(head);
  }

  var api = { parse: parse, looksLikeM3u: looksLikeM3u, resolveUrl: resolveUrl };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.M3U = api;
})(this);
