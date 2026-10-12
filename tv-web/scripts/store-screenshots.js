/*
 * Store screenshots: runs src/ in headless Edge/Chrome at 1920x1080 with the reviewers' demo playlist
 * (PLAYLIST_URL, legal test streams only) and saves PNGs to store/screenshots/<lang>/. Then run
 * `python tv-web/scripts/make_store_assets.py` to make the JPG sizes each store wants. Node 22+.
 *
 * Usage: node scripts/store-screenshots.js [langs, default "en,vi"]
 *   BROWSER  path to Chrome or Edge (default: Edge on Windows)
 */
'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const src = path.join(root, 'src');
const PLAYLIST_URL = 'https://tsiptv-download.web.app/tv/review.m3u';
const browser = process.env.BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const langs = (process.argv[2] || 'en,vi').split(',');
const PORT = 8793;
const CDP = 9337;
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png' };
const KEYS = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, OK: 13, BACK: 27 };

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let ws;
let lastId = 0;
const pending = {};

function send(method, params) {
  return new Promise((resolve) => {
    const id = ++lastId;
    pending[id] = resolve;
    ws.send(JSON.stringify({ id, method, params: params || {} }));
  });
}

async function ev(expression) {
  const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (r.result.exceptionDetails) throw new Error(expression + ': ' + r.result.exceptionDetails.text);
  return r.result.result.value;
}

async function key(name, times) {
  for (let i = 0; i < (times || 1); i++) {
    const code = KEYS[name];
    await send('Input.dispatchKeyEvent', { type: 'rawKeyDown', windowsVirtualKeyCode: code, nativeVirtualKeyCode: code, key: name === 'OK' ? 'Enter' : undefined });
    await send('Input.dispatchKeyEvent', { type: 'keyUp', windowsVirtualKeyCode: code, nativeVirtualKeyCode: code });
    await sleep(200);
  }
}

async function shot(dir, name) {
  // Let focus animations finish.
  await sleep(500);
  const r = await send('Page.captureScreenshot', { format: 'png' });
  fs.writeFileSync(path.join(dir, name + '.png'), Buffer.from(r.result.data, 'base64'));
  console.log('  ' + name);
}

async function waitFor(expression, ms) {
  for (let t = 0; t < ms; t += 250) {
    if (await ev(expression)) return true;
    await sleep(250);
  }
  throw new Error('timed out: ' + expression);
}

/** Plays the focused card and waits for picture; VOD jumps to `seekTo` seconds for a better frame. */
async function playFocused(seekTo) {
  await key('OK');
  await waitFor("document.getElementById('video').currentTime > 1", 30000);
  if (seekTo) {
    await ev(`new Promise(function(res){var v=document.getElementById('video');v.addEventListener('seeked',function(){res(1)},{once:true});v.currentTime=${seekTo}})`);
    await waitFor(`document.getElementById('video').currentTime > ${seekTo + 2}`, 30000);
  } else {
    await sleep(3000);
  }
  await key('OK'); // on-screen controls
}

async function capture(lang) {
  const dir = path.join(root, 'store', 'screenshots', lang);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  console.log(lang);

  // The app picks its language from the TV's (navigator.language).
  const script = await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `Object.defineProperty(navigator, 'language', { get: function () { return '${lang}'; } });`,
  });
  await send('Page.navigate', { url: `http://127.0.0.1:${PORT}/` });
  await sleep(1500);
  await ev('localStorage.clear(); location.reload(); 1');
  await sleep(1500);

  await key('OK'); // disclaimer
  await key('OK'); // Add playlist
  await key('UP'); await key('OK');
  await send('Input.insertText', { text: 'Demo' });
  await key('OK'); await key('OK');
  await send('Input.insertText', { text: PLAYLIST_URL });
  await key('OK');
  await shot(dir, '04-add-playlist');
  await key('OK');
  await waitFor("document.querySelectorAll('.card').length === 4 && !document.querySelector('.dialog')", 15000);
  await shot(dir, '01-channels');

  await playFocused(95);
  await shot(dir, '02-player');
  await key('OK'); // ★ on the on-screen controls
  await key('BACK'); await key('BACK');

  // The other demo streams show their owners' logos and debug text, so the third shot is Favorites.
  await key('LEFT'); await key('UP', 2); await key('RIGHT');
  await shot(dir, '03-favorites');

  await key('UP', 6); // header
  await key('LEFT', 3); await key('OK'); // Playlists
  await shot(dir, '05-playlists');
  await key('BACK');
  await send('Page.removeScriptToEvaluateOnNewDocument', { identifier: script.result.identifier });
}

(async () => {
  const server = http.createServer((req, res) => {
    const file = req.url.split('?')[0] === '/' ? 'index.html' : req.url.split('?')[0];
    fs.readFile(path.join(src, file), (e, data) => {
      if (e) { res.writeHead(404); return res.end(); }
      res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
      res.end(data);
    });
  }).listen(PORT);
  const profile = path.join(root, 'dist', 'screenshot-profile');
  fs.rmSync(profile, { recursive: true, force: true });
  const chrome = spawn(browser, ['--headless=new', '--disable-gpu', '--hide-scrollbars', `--remote-debugging-port=${CDP}`,
    '--window-size=1920,1080', '--autoplay-policy=no-user-gesture-required', '--mute-audio', `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' });
  try {
    let target;
    for (let i = 0; i < 150 && !target; i++) {
      try { target = (await (await fetch(`http://127.0.0.1:${CDP}/json`)).json()).find((t) => t.type === 'page'); } catch (e) { /* starting */ }
      await sleep(200);
    }
    ws = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((r) => (ws.onopen = r));
    ws.onmessage = (message) => {
      const d = JSON.parse(message.data);
      if (d.id && pending[d.id]) { pending[d.id](d); delete pending[d.id]; }
    };
    await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
    for (const lang of langs) await capture(lang);
  } finally {
    chrome.kill();
    server.close();
  }
  process.exit(0);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
