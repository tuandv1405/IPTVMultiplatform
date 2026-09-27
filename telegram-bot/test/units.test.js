import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import zlib from "node:zlib";
import { randomBytes } from "node:crypto";
import { createPiiVault } from "../src/crypto.js";
import { loadConfig } from "../src/config.js";
import { createLogger } from "../src/logger.js";
import { parseImageDataUrl } from "../src/image.js";
import { createSafeLookup, isPublicAddress } from "../src/verify/netguard.js";
import { analysePlaylist } from "../src/verify/playlistParser.js";
import { checkUrl, createFetcher } from "../src/verify/fetcher.js";
import { normaliseUrl } from "../src/verify/verifier.js";
import { renderSubmission } from "../src/telegram/render.js";
import { PNG_DATA_URL } from "./helpers.js";

const key = () => randomBytes(32).toString("base64");

test("vault: round trip, random IV, tamper detection, domain-separated hashes", () => {
  const vault = createPiiVault({ encryptionKey: key(), hashKey: key() });
  const a = vault.encrypt("a@example.com");
  const b = vault.encrypt("a@example.com");
  assert.notEqual(a, b, "same plaintext, different ciphertext");
  assert.equal(vault.decrypt(a), "a@example.com");

  const parts = a.split(":");
  parts[3] = Buffer.from("tampered").toString("base64url");
  assert.throws(() => vault.decrypt(parts.join(":")));

  assert.notEqual(vault.hash("email", "x"), vault.hash("phone", "x"));
  assert.equal(vault.hash("phone", "+84 90-123"), vault.hash("phone", "+8490123"));

  const other = createPiiVault({ encryptionKey: key(), hashKey: key() });
  assert.throws(() => other.decrypt(a), "another key cannot read it");
  assert.throws(() => createPiiVault({ encryptionKey: "short", hashKey: key() }));
});

test("netguard: public vs reserved addresses", () => {
  for (const ip of ["8.8.8.8", "1.1.1.1", "2606:4700:4700::1111"]) assert.ok(isPublicAddress(ip), ip);
  for (const ip of [
    "127.0.0.1",
    "10.1.2.3",
    "172.16.0.1",
    "172.31.255.255",
    "192.168.0.1",
    "169.254.169.254",
    "100.64.0.1",
    "0.0.0.0",
    "224.0.0.1",
    "::1",
    "::",
    "fe80::1",
    "fd00::1",
    "::ffff:127.0.0.1",
    "::ffff:7f00:1",
    "64:ff9b::a00:1",
    "not-an-ip",
  ]) {
    assert.ok(!isPublicAddress(ip), ip);
  }
});

test("safe lookup refuses a hostname that resolves to a private address", async () => {
  const lookup = createSafeLookup((host, opts, cb) =>
    cb(null, [
      { address: "93.184.216.34", family: 4 },
      { address: "10.0.0.1", family: 4 },
    ]),
  );
  await new Promise((resolve) =>
    lookup("rebind.example", {}, (error) => {
      assert.equal(error.code, "EPRIVATEADDRESS");
      resolve();
    }),
  );
  const ok = createSafeLookup((host, opts, cb) => cb(null, [{ address: "93.184.216.34", family: 4 }]));
  await new Promise((resolve) =>
    ok("fine.example", {}, (error, address, family) => {
      assert.equal(error, null);
      assert.equal(address, "93.184.216.34");
      assert.equal(family, 4);
      resolve();
    }),
  );
});

test("checkUrl and normaliseUrl", () => {
  assert.throws(() => checkUrl("javascript:alert(1)"), { code: "unsupported_scheme" });
  assert.throws(() => checkUrl("not a url"), { code: "invalid_url" });
  assert.throws(() => checkUrl("http://printer.local/a.m3u"), { code: "private_address" });
  assert.equal(normaliseUrl("HTTPS://Example.COM:443/A.m3u?x=1#top"), "https://example.com/A.m3u?x=1");
});

test("parser: M3U with attributes, commas in quotes and groups", () => {
  const summary = analysePlaylist(
    [
      "#EXTM3U",
      '#EXTINF:-1 tvg-id="a" group-title="News, Local",VTV1 HD',
      "https://cdn.example.com/vtv1.m3u8",
      '#EXTINF:-1 group-title="Sports",Channel 2',
      "http://cdn.example.com/2.ts",
      "#EXTINF:-1,Broken entry without url",
      "",
    ].join("\n"),
  );
  assert.equal(summary.format, "m3u");
  assert.equal(summary.channelCount, 2);
  assert.equal(summary.groupCount, 2);
  assert.deepEqual(summary.sampleNames, ["VTV1 HD", "Channel 2"]);
});

test("parser: content hash ignores order and names", () => {
  const a = analysePlaylist("#EXTM3U\n#EXTINF:-1,A\nhttp://x.example/1\n#EXTINF:-1,B\nhttp://x.example/2\n");
  const b = analysePlaylist("#EXTM3U\n#EXTINF:-1,Two\nhttp://x.example/2\n#EXTINF:-1,One\nhttp://x.example/1\n");
  assert.equal(a.contentHash, b.contentHash);
});

test("parser: XSPF and JSON (plain array, wrapped, iptv-org style)", () => {
  const xspf = analysePlaylist(
    '<?xml version="1.0"?><playlist version="1" xmlns="http://xspf.org/ns/0/"><trackList>' +
      "<track><title>One</title><location>http://s.example/1?a=1&amp;b=2</location></track>" +
      "<track><title>Two</title><location>http://s.example/2</location></track></trackList></playlist>",
  );
  assert.equal(xspf.format, "xspf");
  assert.equal(xspf.channelCount, 2);

  assert.equal(analysePlaylist('[{"name":"A","url":"http://s.example/a"}]').channelCount, 1);
  assert.equal(analysePlaylist('{"channels":[{"title":"A","stream_url":"http://s.example/a"}]}').channelCount, 1);
  assert.equal(
    analysePlaylist('[{"channel":"a.vn","url":"https://s.example/a.m3u8","categories":["news"]}]').groupCount,
    1,
  );
});

test("parser: refuses HTML, empty lists and non-playlists", () => {
  assert.throws(() => analysePlaylist("<!DOCTYPE html><html><body>hi</body></html>"), { code: "not_a_playlist" });
  assert.throws(() => analysePlaylist("#EXTM3U\n"), { code: "no_channels" });
  assert.throws(() => analysePlaylist('{"foo":1}'), { code: "not_a_playlist" });
  assert.throws(() => analysePlaylist("hello world"), { code: "not_a_playlist" });
  assert.throws(() => analysePlaylist("#EXTM3U\n#EXTINF:-1,A\njavascript:alert(1)\n"), { code: "no_channels" });
});

test("fetcher: non-standard ports are refused before connecting", async () => {
  const fetcher = createFetcher({ timeoutMs: 1000 });
  await assert.rejects(fetcher("http://lists.example.com:6379/a.m3u"), { code: "port_not_allowed" });
});
test("fetcher: end-to-end against a server on an allowed port", async (t) => {
  const server = http.createServer((req, res) => {
    if (req.url === "/redirect") return res.writeHead(302, { Location: "/list.m3u" }).end();
    if (req.url === "/to-private") return res.writeHead(302, { Location: "http://127.0.0.1/x" }).end();
    if (req.url === "/loop") return res.writeHead(302, { Location: "/loop" }).end();
    if (req.url === "/gzip") {
      res.writeHead(200, { "Content-Encoding": "gzip" });
      return res.end(zlib.gzipSync("#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n"));
    }
    if (req.url === "/bomb") {
      res.writeHead(200, { "Content-Encoding": "gzip" });
      return res.end(zlib.gzipSync(Buffer.alloc(64 * 1024, 65)));
    }
    if (req.url === "/missing") return res.writeHead(404).end();
    res.writeHead(200);
    res.end("#EXTM3U\n#EXTINF:-1,A\nhttp://s.example/a\n");
  });
  const listening = await new Promise((resolve) => {
    server.once("error", () => resolve(false));
    server.listen(8000, "127.0.0.1", () => resolve(true));
  });
  if (!listening) return t.skip("port 8000 is busy on this machine");
  t.after(() => server.close());

  const lookup = (host, opts, cb) =>
    host === "lists.test" ? (opts.all ? cb(null, [{ address: "127.0.0.1", family: 4 }]) : cb(null, "127.0.0.1", 4)) : cb(new Error("nx"));
  const fetcher = createFetcher({ lookup, maxBytes: 1024, timeoutMs: 3000 });
  const base = "http://lists.test:8000";

  const direct = await fetcher(`${base}/list.m3u`);
  assert.match(direct.body.toString(), /#EXTM3U/);

  const redirected = await fetcher(`${base}/redirect`);
  assert.equal(redirected.finalUrl, `${base}/list.m3u`);

  assert.match((await fetcher(`${base}/gzip`)).body.toString(), /#EXTINF/);
  await assert.rejects(fetcher(`${base}/to-private`), { code: "private_address" });
  await assert.rejects(fetcher(`${base}/loop`), { code: "too_many_redirects" });
  await assert.rejects(fetcher(`${base}/bomb`), { code: "too_large" });
  await assert.rejects(fetcher(`${base}/missing`), { code: "http_error" });

  // The real safe lookup refuses loopback outright.
  const guarded = createFetcher({ timeoutMs: 3000 });
  await assert.rejects(guarded("http://localtest.me:8000/list.m3u"), (error) =>
    ["private_address", "dns_failed"].includes(error.code),
  );
});

test("image: checks magic bytes, not the declared type", () => {
  assert.equal(parseImageDataUrl(PNG_DATA_URL).mime, "image/png");
  const pngAsJpeg = PNG_DATA_URL.replace("image/png", "image/jpeg");
  assert.throws(() => parseImageDataUrl(pngAsJpeg), { code: "invalid_image" });
  assert.throws(() => parseImageDataUrl("data:image/svg+xml;base64,PHN2Zz4="), { code: "invalid_image" });
  const huge = `data:image/png;base64,${Buffer.alloc(400 * 1024).toString("base64")}`;
  assert.throws(() => parseImageDataUrl(huge), { code: "image_too_large" });
});

test("config: lists every problem, refuses dev auth in production (AC-D4)", () => {
  assert.throws(() => loadConfig({}), (error) => {
    assert.match(error.message, /TELEGRAM_BOT_TOKEN is required/);
    assert.match(error.message, /PII_ENCRYPTION_KEY is required/);
    assert.match(error.message, /at least one of TELEGRAM_ADMIN_CHAT_IDS/);
    return true;
  });
  const base = {
    TELEGRAM_BOT_TOKEN: "123:abc",
    TELEGRAM_ADMIN_USERNAMES: "@TuanDV1405",
    PII_ENCRYPTION_KEY: key(),
    PII_HASH_KEY: key(),
  };
  const config = loadConfig(base);
  assert.deepEqual(config.telegram.adminUsernames, ["tuandv1405"]);
  assert.equal(config.telegram.mode, "polling");

  assert.throws(() => loadConfig({ ...base, NODE_ENV: "production", ALLOW_DEV_AUTH: "true" }), /ALLOW_DEV_AUTH/);
  assert.throws(() => loadConfig({ ...base, TELEGRAM_MODE: "webhook" }), /TELEGRAM_WEBHOOK_URL/);
  assert.throws(() => loadConfig({ ...base, TELEGRAM_REVIEW_CHAT_ID: "@group" }), /must be numeric/);
  assert.throws(() => loadConfig({ ...base, PII_HASH_KEY: base.PII_ENCRYPTION_KEY }), /different keys/);
});

test("logger redacts personal data", () => {
  const lines = [];
  const log = createLogger({ write: (line) => lines.push(line) });
  log.info("x", { email: "a@b.c", phone: "+84", uid: "u1" });
  assert.ok(!lines[0].includes("a@b.c"));
  assert.ok(!lines[0].includes("+84"));
  assert.ok(lines[0].includes("u1"));
});

test("review card escapes HTML from contributor input", () => {
  const caption = renderSubmission({
    id: "pl1",
    status: "IN_REVIEW",
    name: "<b>evil</b>",
    url: "https://x.example/?a=<script>",
    category: "news",
    language: "vi",
    publicName: "A&B",
    ownerUid: "u1",
    description: "</code><a href='x'>",
    verification: { ok: true, channelCount: 1, groupCount: 0, format: "m3u" },
  });
  assert.ok(!caption.includes("<script>"));
  assert.ok(!caption.includes("<b>evil"));
  assert.ok(caption.includes("A&amp;B"));
});
