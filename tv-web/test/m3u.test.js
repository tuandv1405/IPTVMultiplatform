// node --test tv-web/test
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const M3U = require('../src/js/m3u.js');

const example = fs.readFileSync(path.join(__dirname, '../../web/public/examples/playlist.m3u'), 'utf8');

test('the site example playlist: names, groups, logos, numbers, EPG url', () => {
  const { channels, epgUrl } = M3U.parse(example, 'https://tsiptv-8bdd6.web.app/examples/playlist.m3u');
  assert.strictEqual(epgUrl, 'https://tsiptv-8bdd6.web.app/examples/guide.xml');
  const first = channels[0];
  assert.strictEqual(first.name, 'HLS Test Channel');
  assert.strictEqual(first.group, 'Test');
  assert.strictEqual(first.number, 1);
  assert.strictEqual(first.url, 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8');
  const news = channels.find((c) => c.name === 'Example News');
  assert.strictEqual(news.group, 'News', 'only the first of "News;HD"');
  assert.strictEqual(news.logo, 'https://example.com/logos/news.png');
  assert.strictEqual(news.userAgent, 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)');
  assert.strictEqual(news.referrer, 'https://example.com/');
});

test('pipe headers are removed from the URL and kept as headers', () => {
  const { channels } = M3U.parse(example);
  const sports = channels.find((c) => c.name === 'Example Sports');
  assert.strictEqual(sports.url, 'https://cdn.example.com/sports/index.m3u8');
  assert.strictEqual(sports.referrer, 'https://example.com/');
});

test('KODIPROP DRM marks the channel, radio attribute is read', () => {
  const { channels } = M3U.parse(example);
  assert.strictEqual(channels.find((c) => c.name === 'Example Protected').drm, true);
  assert.strictEqual(channels.find((c) => c.name === 'Example Radio').radio, true);
  assert.strictEqual(channels.find((c) => c.name === 'HLS Test Channel').drm, false);
});

test('commas inside quoted attributes do not end the attributes', () => {
  const text = '#EXTM3U\n#EXTINF:-1 tvg-name="A, B" group-title="X, Y",Real Name\nhttp://h/1.ts\n';
  const [ch] = M3U.parse(text).channels;
  assert.strictEqual(ch.name, 'Real Name');
  assert.strictEqual(ch.group, 'X, Y');
});

test('relative URLs, EXTGRP, BOM, CRLF and missing names', () => {
  const text = '﻿#EXTM3U\r\n#EXTINF:-1,\r\n#EXTGRP:Movies\r\nlive/one.m3u8\r\nhttp://h/path/two.ts?token=1\r\n';
  const { channels } = M3U.parse(text, 'http://host/lists/main.m3u');
  assert.strictEqual(channels.length, 2);
  assert.strictEqual(channels[0].url, 'http://host/lists/live/one.m3u8');
  assert.strictEqual(channels[0].group, 'Movies');
  assert.strictEqual(channels[0].name, 'one.m3u8');
  assert.strictEqual(channels[1].name, 'two.ts');
  assert.strictEqual(channels[1].group, '');
});

test('non-stream lines are skipped; looksLikeM3u spots HTML', () => {
  const { channels } = M3U.parse('#EXTM3U\n#EXTINF:-1,Bad\nnot a url at all\n#EXTINF:-1,Good\nhttps://x/y.m3u8\n');
  assert.deepStrictEqual(channels.map((c) => c.name), ['Good']);
  assert.strictEqual(M3U.looksLikeM3u('<!doctype html><html>'), false);
  assert.strictEqual(M3U.looksLikeM3u('#EXTM3U\n'), true);
});
