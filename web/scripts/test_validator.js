#!/usr/bin/env node
/* Tests the browser validator (web/public/assets/tsiptv-validate.js) in Node, with the vendored
   Ajv bundle and the published schema. Run: node web/scripts/test_validator.js
   - every web/public/examples/*.tsiptv.json is valid, with no warnings;
   - each fixture in web/scripts/validator-fixtures/ reports its expected path (AC-W14).
   check_guides.py runs this when Node is available. */
"use strict";
const fs = require("fs");
const path = require("path");

const web = path.resolve(__dirname, "..");
const pub = path.join(web, "public");
const Ajv = require(path.join(pub, "assets", "vendor", "ajv2020.min.js"));
const { validate } = require(path.join(pub, "assets", "tsiptv-validate.js"));
const schema = JSON.parse(fs.readFileSync(path.join(pub, "schema", "tsiptv-source-v1.json"), "utf8"));

let failures = 0;
function check(ok, label, detail) {
  if (!ok) {
    failures++;
    console.log("FAIL " + label + (detail ? "\n     " + detail : ""));
  }
}

const examples = fs.readdirSync(path.join(pub, "examples")).filter((f) => f.endsWith(".tsiptv.json"));
for (const name of examples) {
  const text = fs.readFileSync(path.join(pub, "examples", name), "utf8");
  for (const lang of ["en", "vi"]) {
    const r = validate(text, schema, Ajv, lang);
    check(r.valid && r.warnings.length === 0, `${name} [${lang}] is valid without warnings`,
      JSON.stringify(r.errors.concat(r.warnings)));
  }
}

// fixture → [kind, expected path, expected code or null]
const EXPECT = {
  "typo.json": [["errors", "channels[0].nmae", null], ["errors", "channels[0].name", null]],
  "duplicate-id.json": [["errors", "movies[0].id", "E_DUPLICATE_ID"]],
  "colon-id.json": [["errors", "channels[0].id", null]],
  "unknown-include.json": [["warnings", "layout.home[0].query.include", "W_QUERY_REF"]],
};
const dir = path.join(__dirname, "validator-fixtures");
for (const [name, expectations] of Object.entries(EXPECT)) {
  const text = fs.readFileSync(path.join(dir, name), "utf8");
  for (const lang of ["en", "vi"]) {
    const r = validate(text, schema, Ajv, lang);
    for (const [kind, p, code] of expectations) {
      const hit = r[kind].some((x) => x.path === p && (!code || x.code === code));
      check(hit, `${name} [${lang}] reports ${kind} at ${p}${code ? " (" + code + ")" : ""}`,
        JSON.stringify(r));
    }
  }
}

// Not JSON / not a source
const nj = validate("{ nope", schema, Ajv, "en");
check(!nj.valid && nj.errors[0].code === "E_NOT_JSON", "invalid JSON reports E_NOT_JSON");
const ns = validate('{"format":"x","version":1,"id":"a","meta":{"name":"n"},"channels":[]}', schema, Ajv, "en");
check(ns.errors.some((e) => e.code === "E_NOT_SOURCE") && ns.errors.some((e) => e.code === "E_EMPTY"),
  "wrong format and no content report E_NOT_SOURCE and E_EMPTY");

// QC round 1 cases (inline documents)
const base = { format: "tsiptv-source", version: 1, id: "org.example.qc", meta: { name: "QC" } };
const ok = { id: "ok", name: "OK", url: "https://cdn.example.com/ok.m3u8" };
function run(doc, lang = "en") { return validate(typeof doc === "string" ? doc : JSON.stringify(doc), schema, Ajv, lang); }
function paths(list) { return list.map((x) => x.path + (x.code ? " " + x.code : "")); }

// V1: a channel written as a plain string is "not an object", never "both url and streams"
{
  const r = run({ ...base, channels: ["https://cdn.example.com/a.m3u8", ok] });
  const at = r.errors.filter((e) => e.path === "channels[0]");
  check(at.length > 0 && at.every((e) => !/url and streams/.test(e.msg)) && at.some((e) => /object/.test(e.msg)),
    "V1 string channel reports 'must be an object'", JSON.stringify(r.errors));
}
// V2: impossible calendar dates are W_FIELD warnings (the app drops the field), real ones pass
{
  const r = run({ ...base, meta: { name: "QC", updatedAt: "2026-02-30T10:00:00Z" },
    movies: [{ ...ok, id: "m", releaseDate: "2026-02-31" }, { ...ok, id: "leap", releaseDate: "2024-02-29" }] });
  check(r.valid, "V2 impossible dates are not errors", JSON.stringify(r.errors));
  const w = paths(r.warnings);
  check(w.includes("meta.updatedAt W_FIELD") && w.includes("movies[0].releaseDate W_FIELD") && !w.some((p) => p.startsWith("movies[1]")),
    "V2 impossible dates warn W_FIELD; 2024-02-29 is fine", JSON.stringify(r.warnings));
}
// V3: a dropped item does not claim its id (TsiptvItemErrorsTest.aDroppedItemDoesNotClaimItsId)
{
  const r = run({ ...base, channels: [{ id: "x", name: "X", url: "rtmp://live.example.com/x" }, { id: "x", name: "X2", url: "https://cdn.example.com/x.m3u8" }] });
  check(!r.errors.some((e) => e.code === "E_DUPLICATE_ID"), "V3 dropped channel does not claim its id", JSON.stringify(r.errors));
  check(r.errors.some((e) => e.path === "channels[0].url"), "V3 the rtmp URL itself is reported", JSON.stringify(r.errors));
}
// V4: no duplicate noise
{
  const epgOnly = run({ ...base, epg: ["https://example.com/guide.xml"] });
  check(epgOnly.errors.length === 1 && epgOnly.errors[0].code === "E_EMPTY", "V4 epg-only file reports only E_EMPTY", JSON.stringify(epgOnly.errors));
  const space = run({ ...base, channels: [{ ...ok, url: "https://cdn.example.com/a b.m3u8" }, { ...ok, id: "ok2" }] });
  check(space.errors.length === 1 && space.errors[0].path === "channels[0].url", "V4 URL with a space: one error", JSON.stringify(space.errors));
  const epgId = run({ ...base, channels: [ok], includes: [{ id: "epg-1", type: "m3u", url: "https://example.com/a.m3u" }] });
  check(epgId.errors.length === 1 && epgId.errors[0].code === "E_INCLUDE", "V4 epg- include id: one E_INCLUDE", JSON.stringify(epgId.errors));
  const xkey = run({ ...base, meta: { name: { en: "QC", "x-note": "n" } }, channels: [ok] });
  check(xkey.valid && paths(xkey.warnings).join() === 'meta.name.x-note W_TEXT', "V4 x- key in a text is one W_TEXT warning",
    JSON.stringify(xkey));
}
// V6: localized JSON error with position, gzip detection
{
  const r = run('{\n  "format": "tsiptv-source",\n  oops\n}', "vi");
  check(r.errors[0].code === "E_NOT_JSON" && /^Không phải JSON hợp lệ/.test(r.errors[0].msg), "V6 localized JSON error", JSON.stringify(r.errors));
  const gz = run("\u001f\uFFFD\u0008\u0000");
  check(gz.errors[0].code === "E_NOT_JSON" && /gzip/.test(gz.errors[0].msg), "V6 gzip is detected", JSON.stringify(gz.errors));
}
// V7: epg-<index> counts as an xmltv include in queries
{
  const r = run({ ...base, channels: [ok], epg: ["https://example.com/a.xml", "https://example.com/b.xml"],
    layout: { home: [{ type: "row", title: "G", query: { from: "channels", include: "epg-1" } }] } });
  check(r.warnings.some((w) => w.path === "layout.home[0].query.include" && w.code === "W_QUERY_REF" && /xmltv/.test(w.msg)),
    "V7 query on epg-1 says it is an xmltv guide", JSON.stringify(r.warnings));
}

// QC round 2 cases
function has(list, p, code) { return list.some((x) => x.path === p && (!code || x.code === code)); }
// R2-1: texts whose only entries have invalid language keys are unusable, like in the app
{
  const only = run({ ...base, channels: [{ id: "news", name: { EN: "News" }, url: "https://cdn.example.com/n.m3u8" }] });
  check(!only.valid && has(only.errors, "channels[0].name", "E_ITEM_NAME") && only.errors.some((e) => e.code === "E_EMPTY"),
    "R2-1 channel name {EN} → E_ITEM_NAME and E_EMPTY", JSON.stringify(only));
  const movie = run({ ...base, channels: [ok], movies: [{ id: "m", name: { vi_VN: "Phim" }, url: "https://cdn.example.com/m.mp4" }] });
  check(!movie.valid && has(movie.errors, "movies[0].name", "E_ITEM_NAME"), "R2-1 movie name {vi_VN} → E_ITEM_NAME", JSON.stringify(movie.errors));
  const meta = run({ ...base, meta: { name: { English: "Name", EN: "Name" } }, channels: [ok] });
  check(!meta.valid && has(meta.errors, "meta.name", "E_META"), "R2-1 meta.name with only invalid keys → E_META", JSON.stringify(meta.errors));
  const opt = run({ ...base, meta: { name: "QC", description: { EN: "x" } }, channels: [ok] });
  check(opt.valid && has(opt.warnings, "meta.description", "W_TEXT"), "R2-1 optional text with only invalid keys → W_TEXT, field ignored", JSON.stringify(opt));
  const mixed = run({ ...base, meta: { name: { en: "QC", EN: "QC" } }, channels: [ok] });
  check(mixed.valid && !has(mixed.errors, "meta.name"), "R2-1 a usable entry keeps the text valid", JSON.stringify(mixed));
  // the dropped item does not claim its id
  const dup = run({ ...base, channels: [{ id: "x", name: { EN: "X" }, url: "https://cdn.example.com/a.m3u8" }, { ...ok, id: "x" }] });
  check(!dup.errors.some((e) => e.code === "E_DUPLICATE_ID"), "R2-1 item with unusable name does not claim its id", JSON.stringify(dup.errors));
}
// R2-2: 10,000 channels with key issues stay fast
let timing = null;
{
  const channels = [];
  for (let i = 0; i < 10000; i++) channels.push({ id: "c" + i, name: { en: "C" + i, "x-note": "n" }, url: "https://cdn.example.com/" + i + ".m3u8" });
  const started = Date.now();
  const r = run({ ...base, channels });
  const ms = Date.now() - started;
  timing = ms;
  check(r.valid && r.warnings.length === 10000 && ms < 2000, `R2-2 10,000 channels validate in < 2 s (took ${ms} ms)`, `${r.warnings.length} warnings`);
}
// R2-3: "constructor" is just an unknown include id
{
  const r = run({ ...base, channels: [ok], layout: { home: [{ type: "row", title: "X", query: { from: "channels", include: "constructor" } }] } });
  check(has(r.warnings, "layout.home[0].query.include", "W_QUERY_REF"), "R2-3 include 'constructor' is flagged", JSON.stringify(r.warnings));
}
// R2-4: "$&" in a value is not expanded
{
  const r = run({ ...base, channels: [ok], layout: { home: [{ type: "row", title: "X", query: { from: "channels", include: "a$&b" } }] } });
  check(r.warnings.some((w) => w.msg.includes('"a$&b"')), "R2-4 '$&' is kept literally in messages", JSON.stringify(r.warnings));
}
// R2-5: invalid DRM drops the only stream, so the item does not claim its id
{
  const r = run({ ...base, channels: [{ id: "x", name: "X", url: "https://cdn.example.com/x.mpd", drm: { system: "widevine" } }, { ...ok, id: "x" }] });
  check(!r.errors.some((e) => e.code === "E_DUPLICATE_ID") && has(r.errors, "channels[0].drm.licenseUrl"),
    "R2-5 invalid drm: item dropped, no duplicate id", JSON.stringify(r.errors));
}
// R2-6: a date-time with a space is a shape problem, not an impossible date
{
  const r = run({ ...base, meta: { name: "QC", updatedAt: "2026-09-27 10:00:00Z" }, channels: [ok] });
  check(r.warnings.some((w) => w.path === "meta.updatedAt" && /2026-09-27T10:00:00Z/.test(w.msg)), "R2-6 date-time shape message", JSON.stringify(r.warnings));
}
// R2-7: not UTF-8
{
  const r = run("\uFFFD\uFFFD{\u0000");
  check(r.errors[0].code === "E_NOT_JSON" && /UTF-8/.test(r.errors[0].msg), "R2-7 UTF-16 / not UTF-8 message", JSON.stringify(r.errors));
}
// R2-8: invisible characters in a key are escaped in the displayed path
{
  const r = run({ ...base, meta: { name: { en: "QC", "e\u200Bn": "Q" } }, channels: [ok] });
  check(r.warnings.some((w) => w.path.includes("\\u200B")), "R2-8 zero-width space is shown as \\u200B", JSON.stringify(r.warnings));
}
// R2-9: no url and no streams is one line
{
  const r = run({ ...base, channels: [{ id: "a", name: "A" }] });
  check(r.errors.filter((e) => e.path.startsWith("channels[0]")).length === 1, "R2-9 missing url/streams is one message", JSON.stringify(r.errors));
}

// QC round 3 cases: the kept-item model follows TsiptvSourceParser (trimJs, DRM order, stremio URLs)
function empty(r) { return r.errors.some((e) => e.code === "E_EMPTY"); }
{
  const pad = run({ ...base, channels: [{ id: "a", name: "A", url: "  https://cdn.example.com/a.m3u8 " }] });
  check(!empty(pad), "R3-1 a padded stream url is trimmed like the app (item kept)", JSON.stringify(pad.errors));
  const padDup = run({ ...base, channels: [{ id: "a", name: "A", url: " https://cdn.example.com/a.m3u8" }, { ...ok, id: "a" }] });
  check(padDup.errors.some((e) => e.code === "E_DUPLICATE_ID"), "R3-1 a kept padded item claims its id", JSON.stringify(padDup.errors));
  const lic = run({ ...base, channels: [{ id: "a", name: "A", url: "https://cdn.example.com/a.mpd",
    drm: { system: " Widevine ", licenseUrl: " https://license.example.com/wv " } }] });
  check(!empty(lic), "R3-1 padded drm system and licenseUrl are trimmed (item kept)", JSON.stringify(lic.errors));
  const blank = run({ ...base, channels: [{ id: "a", name: "A", url: "https://cdn.example.com/a.mpd", drm: { system: "  " } }] });
  check(empty(blank), "R3-1 a blank drm system drops the stream (E_EMPTY)", JSON.stringify(blank.errors));
  const wvKeys = run({ ...base, channels: [{ id: "a", name: "A", url: "https://cdn.example.com/a.mpd",
    drm: { system: "widevine", licenseUrl: "https://license.example.com/wv", keys: { zz: "1" } } }] });
  check(!empty(wvKeys), "R3-1 keys are ignored for widevine in the kept decision", JSON.stringify(wvKeys.errors));
  // R3-4: only the "keys are only for clearkey" message under drm.keys
  const under = wvKeys.errors.concat(wvKeys.warnings).filter((x) => x.path.startsWith("channels[0].drm.keys"));
  check(under.length === 1 && /clearkey/.test(under[0].msg), "R3-4 widevine with malformed keys: one message", JSON.stringify(under));
  const type = run({ ...base, includes: [{ id: "live", type: " M3U ", url: "https://example.com/list.m3u" }] });
  check(!empty(type), "R3-1 a padded include type is trimmed (include kept)", JSON.stringify(type.errors));
  const stremio = run({ ...base, includes: [{ id: "s", type: "stremio", url: "https://addon.example.com/addon" }] });
  check(empty(stremio), "R3-1 a stremio include without /manifest.json is dropped (E_EMPTY)", JSON.stringify(stremio.errors));
  const stremioOk = run({ ...base, includes: [{ id: "s", type: "stremio", url: "https://addon.example.com/x/manifest.json?a=1" }] });
  check(stremioOk.valid, "R3-1 a stremio manifest URL with a query is kept", JSON.stringify(stremioOk.errors));
}
// R3-2: lowercase t/z is accepted by the app: never "ignored"
{
  const lower = run({ ...base, meta: { name: "QC", updatedAt: "2026-09-28t10:00:00z" }, channels: [ok] });
  check(!lower.warnings.some((w) => w.path === "meta.updatedAt" && w.code === "W_FIELD") && !lower.errors.length,
    "R3-2 lowercase t/z: no 'ignored' warning", JSON.stringify(lower));
  check(lower.warnings.some((w) => w.path === "meta.updatedAt" && /TS IPTV accepts/.test(w.msg)), "R3-2 schema-only note is shown", JSON.stringify(lower.warnings));
  const z = run({ ...base, meta: { name: "QC", updatedAt: "2026-09-28T10:00:00z" }, channels: [ok] });
  check(z.valid && !z.warnings.length, "R3-2 lowercase z alone passes the schema: no message", JSON.stringify(z));
}
// R3-3: updatedAt longer than 64 characters is ignored (parser optString maxLength 64)
{
  const long = "2026-09-28T10:00:00." + "1".repeat(45) + "Z";
  const r = run({ ...base, meta: { name: "QC", updatedAt: long }, channels: [ok] });
  const at = r.warnings.concat(r.errors).filter((x) => x.path === "meta.updatedAt");
  check(long.length > 64 && at.length === 1 && at[0].code === "W_FIELD" && /64/.test(at[0].msg), "R3-3 updatedAt > 64 characters: one W_FIELD", JSON.stringify(at));
}

// QC round 4: surrounding spaces are trimmed by the app; out-of-range times are invalid
function at(r, p) { return r.errors.concat(r.warnings).filter((x) => x.path === p); }
{
  const sp = run({ ...base, meta: { name: "QC", updatedAt: " 2026-09-28T10:00:00Z " },
    movies: [{ ...ok, id: "m", releaseDate: " 2026-09-28 " }] });
  const u = at(sp, "meta.updatedAt"), d = at(sp, "movies[0].releaseDate");
  check(sp.valid && u.length === 1 && !u[0].code && /spaces/.test(u[0].msg), "R4-1 padded updatedAt: code-less 'spaces' note", JSON.stringify(u));
  check(d.length === 1 && !d[0].code && /spaces/.test(d[0].msg), "R4-1 padded releaseDate: code-less 'spaces' note", JSON.stringify(d));
  const vi = run({ ...base, meta: { name: "QC", updatedAt: "2026-09-28T10:00:00Z\t" }, channels: [ok] }, "vi");
  // R5-1: the app accepts the value but reports W_FIELD for the replaced control character (shortText).
  const viU = at(vi, "meta.updatedAt");
  check(vi.valid && viU.length === 1 && viU[0].code === "W_FIELD" && /điều khiển/.test(viU[0].msg), "R5-1 a trailing tab in updatedAt → one W_FIELD (controls replaced)", JSON.stringify(viU));
  const lf = at(run({ ...base, meta: { name: "QC", updatedAt: "2026-09-28T10:00:00Z\n" }, channels: [ok] }), "meta.updatedAt");
  check(lf.length === 1 && lf[0].code === "W_FIELD" && /control/.test(lf[0].msg), "R5-1 a trailing LF in updatedAt → one W_FIELD (en)", JSON.stringify(lf));
  const padBad = run({ ...base, meta: { name: "QC", updatedAt: " 2026-02-30T10:00:00Z " }, channels: [ok] });
  check(at(padBad, "meta.updatedAt").some((x) => x.code === "W_FIELD"), "R4-1 padding does not hide an impossible date", JSON.stringify(at(padBad, "meta.updatedAt")));
}
{
  for (const v of ["2026-09-28T25:00:00Z", "2026-09-28T10:99:00Z", "2026-09-28T10:00:99Z", "2026-09-28T23:59:60Z", "2026-09-28T25:99:99Z"]) {
    const r = run({ ...base, meta: { name: "QC", updatedAt: v }, channels: [ok] });
    const f = at(r, "meta.updatedAt");
    check(f.length === 1 && f[0].code === "W_FIELD" && /time/.test(f[0].msg), `R4-2 ${v} → W_FIELD not a valid time`, JSON.stringify(f));
  }
  const lower = run({ ...base, meta: { name: "QC", updatedAt: "2026-09-28t10:00:00z" }, channels: [ok] });
  check(at(lower, "meta.updatedAt").length === 1 && !at(lower, "meta.updatedAt")[0].code, "R4-2 lowercase t/z keeps the case-only note", JSON.stringify(at(lower, "meta.updatedAt")));
  for (const v of ["2026-09-28T10:00:00+24:00", "2026-09-28T10:00:00+07:60"]) {
    const r = run({ ...base, meta: { name: "QC", updatedAt: v }, channels: [ok] });
    const f = at(r, "meta.updatedAt");
    check(f.length === 1 && f[0].code === "W_FIELD" && /time/.test(f[0].msg), `R4-2 offset ${v} → W_FIELD not a valid time`, JSON.stringify(f));
  }
  const west = run({ ...base, meta: { name: "QC", updatedAt: "2026-09-28T10:00:00-23:59" }, channels: [ok] });
  check(west.valid && !at(west, "meta.updatedAt").length, "R4-2 offset -23:59 is valid", JSON.stringify(at(west, "meta.updatedAt")));
  const edge = run({ ...base, meta: { name: "QC", updatedAt: "2026-09-28T23:59:59.999+07:00" }, channels: [ok] });
  check(edge.valid && !at(edge, "meta.updatedAt").length, "R4-2 23:59:59.999 with an offset is valid", JSON.stringify(at(edge, "meta.updatedAt")));
}

if (failures) {
  console.log(`test_validator: ${failures} failure(s)`);
  process.exit(1);
}
console.log(`test_validator: OK — ${examples.length} examples valid, ${Object.keys(EXPECT).length} fixtures report the expected paths (vi and en), QC cases V1–V7, R2-1…R2-9, R3-1…R3-4 and R4-1…R4-2 pass (10,000 channels: ${timing} ms)`);
