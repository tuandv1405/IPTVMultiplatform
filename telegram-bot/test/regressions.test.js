// One test per defect fixed in QC (docs/qc-report-2026-09-27.md), kept for v2.
import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { clientIp } from "../src/http/server.js";
import { renderRequest, renderSubmission, visibleLength } from "../src/telegram/render.js";
import { createFetcher } from "../src/verify/fetcher.js";
import { isPublicAddress } from "../src/verify/netguard.js";
import { MEMBER, REVIEW_CHAT, adminUser, googleUser, requestBody, startStack, submission } from "./helpers.js";

test("D2: X-Forwarded-For is read from the right, so a client cannot pick its own IP", () => {
  const req = { headers: { "x-forwarded-for": "9.9.9.1, 198.51.100.7" }, socket: { remoteAddress: "10.0.0.1" } };
  assert.equal(clientIp(req, true), "198.51.100.7");
  assert.equal(clientIp(req, true, 2), "9.9.9.1");
  assert.equal(clientIp(req, false), "10.0.0.1");
});

test("D3: a withdraw racing an approve never leaves a public copy of a withdrawn playlist", async (t) => {
  for (let round = 0; round < 10; round++) {
    const stack = await startStack();
    t.after(stack.close);
    const identity = await stack.makeContributor("u1");
    const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });
    await Promise.all([
      stack.press(MEMBER, REVIEW_CHAT, `ap:${body.id}`),
      stack.call("POST", `/api/submissions/${body.id}/withdraw`, { identity }),
    ]);
    const final = await stack.store.getSubmission(body.id);
    assert.equal(Boolean(await stack.store.getPublicPlaylist(body.id)), final.status === "APPROVED", `round ${round}`);
  }
});

test("D4: a decision taken while cards are still being posted updates every card", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await stack.makeContributor("u1");
  const original = stack.telegram.sendPhoto;
  let approved = false;
  stack.telegram.sendPhoto = async (...args) => {
    const result = await original(...args);
    if (!approved) {
      approved = true;
      const id = (await stack.store.listSubmissionsByStatus("IN_REVIEW"))[0].id;
      await stack.service.approve(id, { by: "tg:1", name: "@fast" });
    }
    return result;
  };
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  assert.equal((await stack.store.getSubmission(body.id)).telegramMessages.length, 2);
  assert.equal(new Set(stack.telegram.byMethod("editMessageCaption").map((e) => e.chatId)).size, 2);
});

for (const driver of ["memory", "sqlite"]) {
  test(`D5 [${driver}]: parallel submissions cannot exceed the in-review limit`, async (t) => {
    const stack = await startStack({ driver });
    t.after(stack.close);
    const identity = await stack.makeContributor("u1");
    const results = await Promise.all([0, 1, 2, 3, 4].map((i) =>
      stack.call("POST", "/api/submissions", { identity, body: submission({ url: `https://lists.example.com/p${i}.m3u` }) })));
    assert.equal(results.filter((r) => r.status === 200).length, 3);
    assert.equal((await stack.store.listSubmissionsByStatus("IN_REVIEW")).length, 3);
  });

  test(`one request at a time holds under concurrency [${driver}]`, async (t) => {
    const stack = await startStack({ driver });
    t.after(stack.close);
    const identity = googleUser("u1");
    const bodies = await Promise.all([0, 1, 2].map(() => requestBody()));
    const results = await Promise.all(bodies.map((body) => stack.call("POST", "/api/requests", { identity, body })));
    assert.equal(results.filter((r) => r.status === 200).length, 1);
  });
}

test("D6: the worst-case cards fit Telegram's limits", () => {
  const long = (n) => "x".repeat(n);
  for (const status of ["IN_REVIEW", "APPROVED", "REJECTED", "REMOVED"]) {
    const caption = renderSubmission({
      id: "pl0123456789", status, name: long(80), url: `https://${long(2000)}`, category: "documentary", language: "multi",
      publicName: long(40), ownerUid: long(28), description: long(500),
      flags: [1, 2, 3, 4].map((i) => ({ type: "same_content", otherId: `pl${i}`, otherPublicName: long(40), otherStatus: "APPROVED" })),
      verification: { ok: true, channelCount: 99999, groupCount: 999, format: "json" },
      statusReason: { code: "copyright", note: long(300) },
      review: { by: long(128), name: long(129) },
    });
    assert.ok(visibleLength(caption) <= 1024, `${status}: ${visibleLength(caption)}`);
  }
  const request = renderRequest({
    uid: long(128), publicName: long(40), about: long(1000), links: [long(500), long(500), long(500)], attempt: 9,
    status: "REJECTED", decision: { by: long(128), name: long(129), reason: long(500) },
  });
  assert.ok(visibleLength(request) <= 4096, "request cards are text messages (4096 limit)");
});

test("D7: a slow-drip server is cut off at the absolute deadline", async (t) => {
  const server = http.createServer((req, res) => {
    res.writeHead(200);
    const timer = setInterval(() => res.write("#"), 200);
    req.on("close", () => clearInterval(timer));
  });
  const listening = await new Promise((resolve) => {
    server.once("error", () => resolve(false));
    server.listen(8080, "127.0.0.1", () => resolve(true));
  });
  if (!listening) return t.skip("port 8080 is busy");
  t.after(() => server.close());
  const lookup = (host, opts, cb) => (opts.all ? cb(null, [{ address: "127.0.0.1", family: 4 }]) : cb(null, "127.0.0.1", 4));
  const started = Date.now();
  await assert.rejects(createFetcher({ lookup, timeoutMs: 1000 })("http://slow.test:8080/"), { code: "timeout" });
  assert.ok(Date.now() - started < 2000, `took ${Date.now() - started} ms`);
});

test("D9: IPv4-compatible and IPv4-translated IPv6 addresses are not public", () => {
  for (const ip of ["::7f00:1", "::127.0.0.1", "::ffff:0:7f00:1"]) assert.ok(!isPublicAddress(ip), ip);
  assert.ok(isPublicAddress("2001:4860:4860::8888"));
});

test("D10: non-string fields are a 400, not a 500", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await stack.makeContributor("u1");
  for (const override of [{ name: { toString: 1 } }, { name: {} }, { category: ["general"] }, { url: 42 }]) {
    assert.equal((await stack.call("POST", "/api/submissions", { identity, body: submission(override) })).status, 400, JSON.stringify(override));
  }
  assert.equal((await stack.call("POST", "/api/playlists/verify", { identity, body: { url: { toString: 1 } } })).status, 400);
  const req = await stack.call("POST", "/api/requests", { identity: googleUser("u3"), body: await requestBody({ publicName: ["Tuan"] }) });
  assert.equal(req.body.error, "invalid_public_name");
  const links = await stack.call("POST", "/api/requests", { identity: googleUser("u4"), body: await requestBody({ links: "https://x" }) });
  assert.equal(links.body.error, "invalid_links");
});

test("D11/D12: oversize bodies get a JSON 413; application/jsonp is not JSON", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await stack.makeContributor("u1");
  const big = await stack.call("POST", "/api/submissions", { identity, body: submission({ description: "x".repeat(2 * 1024 * 1024) }) });
  assert.equal(big.status, 413);
  const jsonp = await stack.call("POST", "/api/submissions", { identity, body: submission(), headers: { "Content-Type": "application/jsonp" } });
  assert.equal(jsonp.body.error, "invalid_content_type");
});

test("N2: the sweep purges users whose Firebase account is gone — capped per run, nobody else", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const gone = await stack.makeContributor("u1");
  await stack.makeContributor("u2");
  await stack.call("POST", "/api/requests", { identity: googleUser("u3"), body: await requestBody() });
  const { body } = await stack.call("POST", "/api/submissions", { identity: gone, body: submission() });
  await stack.call("POST", `/api/admin/submissions/${body.id}/decide`, { identity: adminUser(), body: { action: "approve" } });

  assert.equal(await stack.service.sweepDeletedAccounts(async (uid) => uid !== "u1"), 1);
  assert.equal(await stack.store.getContributor("u1"), null);
  assert.equal(await stack.store.getRequest("u1"), null);
  assert.ok(await stack.store.getContributor("u2"));
  assert.equal(await stack.store.getPublicPlaylist(body.id), null);
  assert.equal(await stack.service.sweepDeletedAccounts(async () => false, { maxPerRun: 1 }), 1);
  assert.equal(await stack.service.sweepDeletedAccounts(async () => false, { maxPerRun: 5 }), 1);
});
