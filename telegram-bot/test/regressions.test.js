// One test per defect from the QC pass of 2026-09-27 (docs/qc-report-2026-09-27.md).
import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { clientIp } from "../src/http/server.js";
import { renderSubmission, visibleLength } from "../src/telegram/render.js";
import { createFetcher } from "../src/verify/fetcher.js";
import { isPublicAddress } from "../src/verify/netguard.js";
import { MEMBER, REVIEW_CHAT, registration, startStack, submission, verifiedUser } from "./helpers.js";

async function contributor(stack, uid = "u1") {
  const identity = verifiedUser(uid);
  await stack.call("POST", "/api/contributor/register", { identity, body: registration() });
  return identity;
}

test("D2: X-Forwarded-For is read from the right, so a client cannot pick its own IP", () => {
  const req = { headers: { "x-forwarded-for": "9.9.9.1, 198.51.100.7" }, socket: { remoteAddress: "10.0.0.1" } };
  assert.equal(clientIp(req, true), "198.51.100.7");
  assert.equal(clientIp(req, true, 2), "9.9.9.1");
  assert.equal(clientIp(req, false), "10.0.0.1");
});

test("D3: a withdraw racing an approve never leaves a public copy of a withdrawn playlist", async (t) => {
  for (let round = 0; round < 20; round++) {
    const stack = await startStack();
    t.after(stack.close);
    const identity = await contributor(stack);
    const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });
    await Promise.all([
      stack.press(MEMBER, REVIEW_CHAT, `ap:${body.id}`),
      stack.call("POST", `/api/submissions/${body.id}/withdraw`, { identity }),
    ]);
    const final = await stack.store.getSubmission(body.id);
    const published = await stack.store.getPublicPlaylist(body.id);
    assert.equal(Boolean(published), final.status === "APPROVED", `round ${round}: ${final.status}`);
  }
});

test("D4: a decision taken while cards are still being posted updates every card", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  // Approve as soon as the first card lands, before the second is sent.
  const original = stack.telegram.sendPhoto;
  let approved = false;
  stack.telegram.sendPhoto = async (...args) => {
    const result = await original(...args);
    if (!approved) {
      approved = true;
      const id = (await stack.store.listSubmissionsByStatus("IN_REVIEW"))[0].id;
      await stack.service.approve(id, { id: 1, username: "fast" });
    }
    return result;
  };
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  const final = await stack.store.getSubmission(body.id);
  assert.equal(final.telegramMessages.length, 2);
  const edited = new Set(stack.telegram.byMethod("editMessageCaption").map((e) => e.chatId));
  assert.equal(edited.size, 2, "both cards show the decision");
});

test("D5: parallel submissions cannot exceed the in-review limit", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const results = await Promise.all(
    [0, 1, 2, 3, 4].map((i) =>
      stack.call("POST", "/api/submissions", { identity, body: submission({ url: `https://lists.example.com/p${i}.m3u` }) }),
    ),
  );
  assert.equal(results.filter((r) => r.status === 200).length, 3);
  assert.equal((await stack.store.listSubmissionsByStatus("IN_REVIEW")).length, 3);
});

test("D6: the worst-case card fits Telegram's caption limit", () => {
  const long = (n) => "x".repeat(n);
  for (const status of ["IN_REVIEW", "APPROVED", "REJECTED", "REMOVED"]) {
    const caption = renderSubmission({
      id: "pl0123456789",
      status,
      name: long(80),
      url: `https://${long(2000)}`,
      category: "documentary",
      language: "multi",
      publicName: long(40),
      ownerUid: long(28),
      description: long(500),
      flags: [1, 2, 3, 4].map((i) => ({ type: "same_content", otherId: `pl${i}`, otherPublicName: long(40), otherStatus: "APPROVED" })),
      verification: { ok: true, channelCount: 99999, groupCount: 999, format: "json" },
      statusReason: { code: "copyright", note: long(300) },
      reviewedBy: { id: 1, name: long(129) },
      removedBy: { id: 1, name: long(129) },
    });
    assert.ok(visibleLength(caption) <= 1024, `${status}: ${visibleLength(caption)}`);
  }
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
  const fetcher = createFetcher({ lookup, timeoutMs: 1000 });
  const started = Date.now();
  await assert.rejects(fetcher("http://slow.test:8080/"), { code: "timeout" });
  assert.ok(Date.now() - started < 2000, `took ${Date.now() - started} ms`);
});

test("D8: deleting the account removes contact data and unpublishes playlists; a ban survives", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  await stack.press(MEMBER, REVIEW_CHAT, `ap:${body.id}`);

  const res = await stack.call("POST", "/api/me/delete", { identity });
  assert.equal(res.status, 200);
  assert.equal(await stack.store.getContributor("u1"), null);
  assert.equal(await stack.store.getPublicPlaylist(body.id), null);
  assert.equal((await stack.store.getSubmission(body.id)).status, "WITHDRAWN");

  const banned = await contributor(stack, "u2");
  await stack.service.ban("u2", { id: 1 }, "stolen");
  await stack.call("POST", "/api/me/delete", { identity: banned });
  const tombstone = await stack.store.getContributor("u2");
  assert.equal(tombstone.status, "BANNED");
  assert.equal(tombstone.emailEnc, undefined, "no ciphertext is kept");
  const again = await stack.call("POST", "/api/contributor/register", { identity: banned, body: registration() });
  assert.equal(again.body.error, "banned");
});

test("D9: IPv4-compatible and IPv4-translated IPv6 addresses are not public", () => {
  assert.ok(!isPublicAddress("::7f00:1"));
  assert.ok(!isPublicAddress("::127.0.0.1"));
  assert.ok(!isPublicAddress("::ffff:0:7f00:1"));
  assert.ok(isPublicAddress("2001:4860:4860::8888"));
});

test("D10: non-string fields are a 400, not a 500", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  for (const override of [{ name: { toString: 1 } }, { name: {} }, { category: ["general"] }, { url: 42 }]) {
    const res = await stack.call("POST", "/api/submissions", { identity, body: submission(override) });
    assert.equal(res.status, 400, JSON.stringify(override));
  }
  const verify = await stack.call("POST", "/api/playlists/verify", { identity, body: { url: { toString: 1 } } });
  assert.equal(verify.status, 400);
  const reg = await stack.call("POST", "/api/contributor/register", {
    identity: verifiedUser("u3"),
    body: { ...registration(), publicName: ["Tuan"] },
  });
  assert.equal(reg.body.error, "invalid_public_name");
});

test("D11/D12: oversize bodies get a JSON 413; application/jsonp is not JSON", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const big = await stack.call("POST", "/api/submissions", {
    identity,
    body: submission({ description: "x".repeat(2 * 1024 * 1024) }),
  });
  assert.equal(big.status, 413);
  assert.equal(big.body.error, "payload_too_large");
  const jsonp = await stack.call("POST", "/api/submissions", {
    identity,
    body: submission(),
    headers: { "Content-Type": "application/jsonp" },
  });
  assert.equal(jsonp.body.error, "invalid_content_type");
});

test("N1: a ban follows the email/phone to a new account", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const old = await contributor(stack, "u1");
  await stack.service.ban("u1", { id: 1 }, "stolen");
  await stack.call("POST", "/api/me/delete", { identity: old });
  // New Firebase account, same phone number.
  const fresh = verifiedUser("u7", { email: "other@example.com", phone: old.phone });
  const res = await stack.call("POST", "/api/contributor/register", { identity: fresh, body: registration() });
  assert.equal(res.body.error, "banned");
  const unrelated = verifiedUser("u8", { email: "new@example.com", phone: "+84900000001" });
  assert.equal((await stack.call("POST", "/api/contributor/register", { identity: unrelated, body: registration() })).status, 200);
});

test("N2: the sweep purges contributors whose Firebase account is gone, and nobody else", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const gone = await contributor(stack, "u1");
  await contributor(stack, "u2");
  const { body } = await stack.call("POST", "/api/submissions", { identity: gone, body: submission() });
  await stack.press(MEMBER, REVIEW_CHAT, `ap:${body.id}`);

  const purged = await stack.service.sweepDeletedAccounts(async (uid) => uid !== "u1");
  assert.equal(purged, 1);
  assert.equal(await stack.store.getContributor("u1"), null);
  assert.ok(await stack.store.getContributor("u2"));
  assert.equal(await stack.store.getPublicPlaylist(body.id), null);
  assert.equal(await stack.service.sweepDeletedAccounts(async () => false), 1, "u2 now, u1 not twice");
});
test("N2 guard: the sweep purges at most maxPerRun accounts per run", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  for (const uid of ["a1", "a2", "a3"]) await contributor(stack, uid);
  assert.equal(await stack.service.sweepDeletedAccounts(async () => false, { maxPerRun: 2 }), 2);
  assert.equal(await stack.service.sweepDeletedAccounts(async () => false, { maxPerRun: 2 }), 1);
});