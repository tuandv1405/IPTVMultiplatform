/*
 * Smoke test of the app running inside a TV simulator, driven over the Chrome DevTools Protocol
 * (remote keys are sent to the page; the real mouse and keyboard are not used). Node 22+.
 *
 *   LG webOS TV Simulator (Electron; the app is a page of its own):
 *     <Simulator>.exe <abs path to dist/webos> "{}" --remote-debugging-port=9334 --disable-backgrounding-occluded-windows
 *     node scripts/sim-check.js --port 9334 --page dist/webos/index.html --back 461
 *
 *   Samsung TV Web Simulator (tizen-studio/tools/sec-tv-simulator; the app runs in an iframe):
 *     simulator.exe --file=file:///<abs path>/dist/tizen/index.html --platform=tizentv --tizentvversion=10.0
 *                   --resolution=1920x1080 --remote-debugging-port=9335 --disableInspector
 *     node scripts/sim-check.js --port 9335 --page ripple.html --frame appLauncher --back 10009
 *
 * The test playlist is served on :8790 without CORS headers, like most IPTV servers. Its first channel
 * is a public HLS test stream; the others point at a closed port and must fail quickly.
 * Screenshots go to --out (default dist/sim-check). Exit code 1 when a check fails.
 */
'use strict';
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const opts = { port: '9334', page: 'index.html', frame: '', back: '461', out: path.resolve(__dirname, '..', 'dist', 'sim-check') };
for (let i = 2; i < process.argv.length; i += 2) opts[process.argv[i].replace(/^--/, '')] = process.argv[i + 1];
const KEYS = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, OK: 13, BACK: Number(opts.back) };
fs.mkdirSync(opts.out, { recursive: true });

let m3u = '#EXTM3U\n#EXTINF:-1 tvg-chno="1" group-title="Test",Mux Test HLS\nhttps://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8\n';
for (let i = 2; i <= 40; i++) {
  m3u += `#EXTINF:-1 tvg-chno="${i}" group-title="${i % 3 ? 'News' : 'Sport'}",Kênh thử ${i}\nhttp://127.0.0.1:1/none${i}.m3u8\n`;
}
const server = http.createServer((req, res) => {
  if (req.url.startsWith('/test.m3u')) {
    res.writeHead(200, { 'Content-Type': 'audio/x-mpegurl' });
    return res.end(m3u);
  }
  res.writeHead(404);
  res.end();
}).listen(8790);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let ws;
let lastId = 0;
const pending = {};
const contexts = [];
const logs = [];
let contextId = null;
let failures = 0;

function send(method, params) {
  return new Promise((resolve) => {
    const id = ++lastId;
    pending[id] = resolve;
    ws.send(JSON.stringify({ id, method, params: params || {} }));
  });
}

async function ev(expression) {
  const params = { expression, returnByValue: true };
  if (contextId) params.contextId = contextId;
  const r = await send('Runtime.evaluate', params);
  if (!r.result || r.result.exceptionDetails) throw new Error('evaluate failed: ' + expression + ' ' + JSON.stringify(r.result || r.error));
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

async function shot(name) {
  const r = await Promise.race([send('Page.captureScreenshot', { format: 'png' }), sleep(5000)]);
  if (r && r.result) fs.writeFileSync(path.join(opts.out, name + '.png'), Buffer.from(r.result.data, 'base64'));
  else console.log(`  (no screenshot ${name}: is the simulator window minimized?)`);
}

function check(name, ok, detail) {
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${detail === undefined ? '' : ' - ' + detail}`);
}

async function waitFor(expression, ms) {
  for (let t = 0; t < ms; t += 250) {
    if (await ev(expression)) return true;
    await sleep(250);
  }
  return false;
}

/** The Samsung simulator runs the app in an iframe: evaluate in that frame's context. */
async function useAppFrame() {
  if (!opts.frame) return;
  contextId = null;
  for (let i = 0; i < 60; i++) {
    const frames = [];
    (function walk(node) { frames.push(node.frame); (node.childFrames || []).forEach(walk); })((await send('Page.getFrameTree')).result.frameTree);
    const frame = frames.find((f) => f.url.includes(opts.frame));
    const context = frame && contexts.filter((c) => c.auxData && c.auxData.frameId === frame.id && c.auxData.isDefault).pop();
    if (context && (await send('Runtime.evaluate', { expression: 'typeof Player', contextId: context.id, returnByValue: true })).result.result.value === 'object') {
      contextId = context.id;
      await ev('window.focus(); 1'); // remote keys go to the focused document
      return;
    }
    await sleep(250);
  }
  throw new Error('app frame not found: ' + opts.frame);
}

const FOCUSED = "(function(){var f=document.querySelector('#home .focused');return f?(f.id||f.querySelector('.name')&&f.querySelector('.name').textContent||f.textContent):null})()";
const STATUS = "(function(){var s=document.getElementById('osd-status');return s.hidden?'':s.textContent})()";
const SCREEN = "document.getElementById('home').hidden ? 'player' : 'home'";
const DIALOG = "(document.querySelector('.dialog')||{}).innerText||''";

(async () => {
  const targets = await (await fetch(`http://127.0.0.1:${opts.port}/json`)).json();
  const target = targets.find((t) => t.url.includes(opts.page));
  if (!target) throw new Error(`no page matching ${opts.page} on port ${opts.port}`);
  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  ws.onmessage = (message) => {
    const d = JSON.parse(message.data);
    if (d.id && pending[d.id]) { pending[d.id](d); delete pending[d.id]; }
    if (d.method === 'Runtime.executionContextCreated') contexts.push(d.params.context);
    if (d.method === 'Runtime.exceptionThrown') logs.push('exception ' + JSON.stringify(d.params.exceptionDetails).slice(0, 300));
  };
  await send('Runtime.enable');
  await send('Page.enable');

  // Start from a fresh install: no playlists, disclaimer not accepted.
  await useAppFrame();
  await ev('localStorage.clear(); location.reload(); 1');
  await sleep(2500);
  await useAppFrame();
  const env = await ev("JSON.stringify({ua: navigator.userAgent.replace(/.*(Chrome\\/[\\d.]+).*/, '$1'), screen: innerWidth + 'x' + innerHeight, engine: Player.engineName(), hlsjs: !!(window.Hls && Hls.isSupported())})");
  console.log('env ' + env);
  const engine = JSON.parse(env).engine;

  await shot('01-disclaimer');
  check('disclaimer shown on first start', /TS IPTV/.test(await ev(DIALOG)));
  await key('OK');
  check('focus on "Add playlist" with no playlist', (await ev(FOCUSED)) === 'btn-add');

  await key('OK');
  await key('UP'); await key('OK');
  await send('Input.insertText', { text: 'Test list' });
  await key('OK'); await key('OK');
  await send('Input.insertText', { text: 'http://127.0.0.1:8790/test.m3u' });
  await key('OK');
  await shot('02-add-dialog');
  await key('OK'); // Save
  const loaded = await waitFor("document.querySelectorAll('.card').length > 0 && !document.querySelector('.dialog')", 15000);
  // Group names follow the TV language; their channel counts do not.
  const groups = await ev("Array.prototype.map.call(document.querySelectorAll('.group'), function(g){return g.textContent.replace(/\\D+/, '')}).join(',')");
  check('playlist without CORS headers loads', loaded, groups);
  check('groups: favorites, recent, all, Test, News, Sport', groups === '0,0,40,1,26,13', groups);
  await shot('03-home');

  await key('RIGHT', 2); await key('DOWN', 2);
  check('grid navigation (right 2, down 2 = channel 13)', /Kênh thử 13$/.test(await ev(FOCUSED)), await ev(FOCUSED));
  await shot('04-grid');
  await key('UP', 2); await key('LEFT', 2);
  await key('OK');
  const playing = engine === 'avplay'
    ? "webapis.avplay.getState() === 'PLAYING'"
    : "document.getElementById('video').currentTime > 1";
  const played = await waitFor(playing, 30000);
  const failed = /could not|Không phát/.test(await ev(STATUS));
  if (!played && failed && engine === 'avplay') console.log('SKIP HLS playback - this AVPlay cannot play the test stream (the Samsung web simulator has no H.264)');
  else check(`HLS test stream plays (${engine})`, played, await ev(STATUS));
  await shot('05-player');

  await key('UP'); // channel 2: closed port
  check('dead channel reports an error within 10 s', await waitFor("/could not|Không phát/.test(" + STATUS + ")", 10000), await ev("document.getElementById('osd-name').textContent"));
  await shot('06-dead-channel');

  for (let i = 0; i < 3 && (await ev(SCREEN)) === 'player'; i++) await key('BACK');
  check('Back returns to the channel list', (await ev(SCREEN)) === 'home');
  check('focus on the last channel played', /Kênh thử 2$/.test(await ev(FOCUSED) || ''), await ev(FOCUSED));
  check('"Recently watched" has both channels', /\D2$/.test(await ev("document.querySelectorAll('.group')[1].textContent")));

  await key('BACK');
  check('Back on the channel list asks before exiting', /TS IPTV\?/.test(await ev(DIALOG)));
  await shot('07-exit');
  await key('BACK');
  check('Back closes the exit question', (await ev(DIALOG)) === '');

  if (logs.length) console.log(logs.join('\n'));
  check('no uncaught exceptions', logs.length === 0, logs.length);
  console.log(failures ? `${failures} check(s) failed` : 'all checks passed');
  ws.close();
  server.close();
  process.exit(failures ? 1 : 0);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
