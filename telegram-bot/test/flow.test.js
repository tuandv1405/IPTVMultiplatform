// End-to-end flows of docs/prd-contributor-requests.md over real HTTP, run once
// on the memory store and once on the SQLite store (the own-server backend).
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  ADMIN_EMAIL, DRIVERS, MEMBER, PRIVATE, REVIEW_CHAT, STRANGER, TG_ADMIN,
  adminUser, googleUser, okVerification, requestBody, startStack, submission,
} from "./helpers.js";

for (const driver of DRIVERS) {
  const it = (name, fn) =>
    test(`[${driver}] ${name}`, async (t) => {
      const stack = await startStack({ driver, ...(fn.options ?? {}) });
      t.after(stack.close);
      await fn(stack);
    });
  const itWith = (options, name, fn) => it(name, Object.assign(fn, { options }));

  // ---- requests ------------------------------------------------------------

  it("AC-Q1: every user endpoint needs a sign-in", async ({ call }) => {
    for (const [method, path] of [["GET", "/api/me"], ["POST", "/api/requests"], ["GET", "/api/admin/requests"], ["POST", "/api/submissions"]]) {
      assert.equal((await call(method, path)).status, 401, path);
    }
  });

  it("AC-Q2/Q6: one request at a time; consents and encrypted contact required", async ({ call }) => {
    const identity = googleUser("u1");
    let res = await call("POST", "/api/requests", { identity, body: await requestBody({ consents: { policy: "2026-09-27" } }) });
    assert.equal(res.body.error, "consents_required");

    const plaintext = { ...(await requestBody()), contactEnc: { fullName: "A", phone: "+84901234567" } };
    res = await call("POST", "/api/requests", { identity, body: plaintext });
    assert.equal(res.body.error, "invalid_contact", "contact data must arrive encrypted");

    const password = googleUser("u1", { provider: "password" });
    res = await call("POST", "/api/requests", { identity: password, body: await requestBody() });
    assert.equal(res.body.error, "google_required");

    res = await call("POST", "/api/requests", { identity, body: await requestBody() });
    assert.equal(res.status, 200, JSON.stringify(res.body));
    assert.equal(res.body.status, "PENDING");
    assert.equal(res.body.attempt, 1);

    res = await call("POST", "/api/requests", { identity, body: await requestBody() });
    assert.equal(res.status, 409);
    assert.equal(res.body.error, "request_pending");
  });

  it("AC-Q3: after a rejection the user may apply again; after approval never", async ({ call }) => {
    const identity = googleUser("u1");
    await call("POST", "/api/requests", { identity, body: await requestBody() });
    await call("POST", "/api/admin/requests/u1/decide", { identity: adminUser(), body: { decision: "REJECTED", reason: "Tell us more" } });
    const mine = await call("GET", "/api/requests/me", { identity });
    assert.equal(mine.body.request.status, "REJECTED");
    assert.equal(mine.body.request.decision.reason, "Tell us more");

    const again = await call("POST", "/api/requests", { identity, body: await requestBody() });
    assert.equal(again.body.attempt, 2);
    await call("POST", "/api/admin/requests/u1/decide", { identity: adminUser(), body: { decision: "APPROVED" } });
    const third = await call("POST", "/api/requests", { identity, body: await requestBody() });
    assert.equal(third.body.error, "already_contributor");
    const me = await call("GET", "/api/me", { identity });
    assert.equal(me.body.contributor.status, "ACTIVE");
    assert.equal(me.body.isAdmin, false);
  });

  it("AC-Q4/Q7: only admins list and decide; users see only their own request", async ({ call }) => {
    await call("POST", "/api/requests", { identity: googleUser("u1"), body: await requestBody() });
    assert.equal((await call("GET", "/api/admin/requests", { identity: googleUser("u2") })).status, 403);
    assert.equal((await call("POST", "/api/admin/requests/u1/decide", { identity: googleUser("u1"), body: { decision: "APPROVED" } })).status, 403);
    assert.equal((await call("GET", "/api/requests/me", { identity: googleUser("u2") })).body.request, null);
    // A look-alike address is not the owner.
    const fake = googleUser("x", { email: `${ADMIN_EMAIL}.evil.com` });
    assert.equal((await call("GET", "/api/admin/requests", { identity: fake })).status, 403);
    // The custom claim works.
    const claimed = googleUser("y", { email: "helper@gmail.com", admin: true });
    const list = await call("GET", "/api/admin/requests?status=PENDING", { identity: claimed });
    assert.equal(list.body.requests.length, 1);
    assert.equal(list.body.requests[0].telegramMessages, undefined, "bookkeeping is not exposed");
  });

  it("AC-Q5: the server stores contact data only as ciphertext; /contact decrypts for an admin DM", async ({ call, store, say, telegram }) => {
    const identity = googleUser("u1");
    await call("POST", "/api/requests", { identity, body: await requestBody({ phone: "+84901234567", fullName: "Nguyen Van Tuan", email: identity.email }) });
    const raw = JSON.stringify(await store.getRequest("u1"));
    for (const secret of ["+84901234567", "Nguyen Van Tuan", identity.email]) assert.ok(!raw.includes(secret), secret);

    await say(TG_ADMIN, REVIEW_CHAT, "/contact u1");
    assert.ok(!telegram.byMethod("sendMessage").at(-1).text.includes("+84901234567"), "never in a group");
    await say(MEMBER, PRIVATE(MEMBER), "/contact u1");
    assert.ok(!telegram.byMethod("sendMessage").at(-1).text.includes("+84901234567"), "never to a non-admin");
    await say(TG_ADMIN, PRIVATE(TG_ADMIN), "/contact u1");
    const text = telegram.byMethod("sendMessage").at(-1).text;
    assert.ok(text.includes("+84901234567") && text.includes("Nguyen Van Tuan"));
  });

  it("requests reach Telegram; only an admin can approve them there", async ({ call, telegram, press, store }) => {
    await call("POST", "/api/requests", { identity: googleUser("u1"), body: await requestBody() });
    const cards = telegram.byMethod("sendMessage").filter((m) => /CONTRIBUTOR REQUEST/.test(m.text));
    assert.deepEqual(cards.map((c) => c.chatId).sort(), ["-1001", "999"].sort());
    assert.ok(!cards[0].text.includes("+849"), "no contact data on cards");
    assert.equal(cards[0].reply_markup.inline_keyboard[0][0].callback_data, "qa:u1");

    await press(STRANGER, PRIVATE(STRANGER), "qa:u1");
    assert.equal((await store.getRequest("u1")).status, "PENDING", "strangers cannot decide");
    await press(MEMBER, REVIEW_CHAT, "qa:u1");
    assert.equal((await store.getRequest("u1")).status, "PENDING", "review-group members review playlists, not contributors");
    assert.match(telegram.byMethod("answerCallbackQuery").at(-1).text, /Only admins/);
    await press(TG_ADMIN, REVIEW_CHAT, "qa:u1");
    assert.equal((await store.getRequest("u1")).status, "APPROVED");
    assert.equal((await store.getContributor("u1")).status, "ACTIVE");
    const edits = telegram.byMethod("editMessageText").filter((e) => /CONTRIBUTOR APPROVED/.test(e.text));
    assert.equal(edits.length, 2);
    assert.match(edits[0].text, /@tuandv1405/);
  });

  it("a request rejected in Telegram carries the reason to the user", async ({ call, press }) => {
    const identity = googleUser("u1");
    await call("POST", "/api/requests", { identity, body: await requestBody() });
    await press(TG_ADMIN, PRIVATE(TG_ADMIN), "qx:u1:incomplete");
    const mine = await call("GET", "/api/requests/me", { identity });
    assert.equal(mine.body.request.status, "REJECTED");
    assert.equal(mine.body.request.decision.reason, "Not enough information");
  });

  it("QC-1: a suspended contributor cannot come back by cancelling and re-applying", async ({ call, makeContributor }) => {
    const identity = await makeContributor("u1");
    await call("POST", "/api/admin/contributors/u1/status", { identity: adminUser(), body: { status: "SUSPENDED", note: "x" } });
    await call("POST", "/api/requests/me/cancel", { identity });
    const again = await call("POST", "/api/requests", { identity, body: await requestBody({ publicName: "Second Name" }) });
    assert.equal(again.body.error, "already_contributor");
    const me = await call("GET", "/api/me", { identity });
    assert.equal(me.body.contributor.status, "SUSPENDED");
  });

  it("QC-7/13: empty contact envelopes are refused; the owner email needs Google sign-in to be admin", async ({ call }) => {
    const body = await requestBody();
    const empty = { ...body, contactEnc: { v: 1, alg: body.contactEnc.alg, kid: "", wrappedKey: "", iv: "", ct: "" } };
    assert.equal((await call("POST", "/api/requests", { identity: googleUser("u1"), body: empty })).body.error, "invalid_contact");
    const passwordOwner = googleUser("x", { email: ADMIN_EMAIL, provider: "password" });
    assert.equal((await call("GET", "/api/admin/requests", { identity: passwordOwner })).status, 403);
  });

  it("the user can cancel a pending request and apply again", async ({ call }) => {
    const identity = googleUser("u1");
    await call("POST", "/api/requests", { identity, body: await requestBody() });
    assert.equal((await call("POST", "/api/requests/me/cancel", { identity })).status, 200);
    assert.equal((await call("GET", "/api/requests/me", { identity })).body.request, null);
    assert.equal((await call("POST", "/api/requests", { identity, body: await requestBody() })).status, 200);
  });

  // ---- playlists -------------------------------------------------------------

  it("AC-Q8: non-contributors and suspended contributors cannot upload", async ({ call, makeContributor }) => {
    const outsider = googleUser("u9");
    assert.equal((await call("POST", "/api/submissions", { identity: outsider, body: submission() })).body.error, "not_contributor");
    assert.equal((await call("POST", "/api/playlists/verify", { identity: outsider, body: { url: "https://x.example/a.m3u" } })).body.error, "not_contributor");

    const identity = await makeContributor("u1");
    await call("POST", "/api/admin/contributors/u1/status", { identity: adminUser(), body: { status: "SUSPENDED", note: "stolen lists" } });
    assert.equal((await call("POST", "/api/submissions", { identity, body: submission() })).body.error, "suspended");
    await call("POST", "/api/admin/contributors/u1/status", { identity: adminUser(), body: { status: "ACTIVE" } });
    assert.equal((await call("POST", "/api/submissions", { identity, body: submission() })).status, 200);
  });

  it("AC-Q9: submit → admin console approves → public directory → withdraw removes it", async ({ call, makeContributor, telegram }) => {
    const identity = await makeContributor("u1");
    const { body } = await call("POST", "/api/submissions", { identity, body: submission() });
    assert.equal(body.status, "IN_REVIEW");
    assert.equal(body.verification.method, "server");
    assert.equal(telegram.byMethod("sendPhoto").length, 2, "posted to the admin DM and the review group");

    assert.equal((await call("POST", `/api/admin/submissions/${body.id}/decide`, { identity, body: { action: "approve" } })).status, 403);
    const list = await call("GET", "/api/admin/submissions?status=IN_REVIEW", { identity: adminUser() });
    assert.equal(list.body.submissions.length, 1);
    const image = await call("GET", `/api/admin/submissions/${body.id}/image`, { identity: adminUser() });
    assert.equal(image.body.mime, "image/png");

    const approved = await call("POST", `/api/admin/submissions/${body.id}/decide`, { identity: adminUser(), body: { action: "approve" } });
    assert.equal(approved.body.status, "APPROVED");
    assert.equal(approved.body.review.name, "Admin", "never the owner's personal email");

    const pub = await call("GET", "/api/public/playlists");
    assert.equal(pub.body.playlists.length, 1);
    assert.equal(pub.body.playlists[0].publicName, "Tuan Lists");
    assert.ok(!("ownerUid" in pub.body.playlists[0]));
    assert.equal((await call("GET", `/api/public/playlists/${body.id}/image`)).status, 200);

    const again = await call("POST", `/api/admin/submissions/${body.id}/decide`, { identity: adminUser(), body: { action: "approve" } });
    assert.equal(again.status, 409);

    await call("POST", `/api/submissions/${body.id}/withdraw`, { identity });
    assert.equal((await call("GET", "/api/public/playlists")).body.playlists.length, 0);
  });

  it("admin rejects with a reason; takes down an approved playlist", async ({ call, makeContributor }) => {
    const identity = await makeContributor("u1");
    const a = await call("POST", "/api/submissions", { identity, body: submission() });
    const b = await call("POST", "/api/submissions", { identity, body: submission({ url: "https://lists.example.com/b.m3u" }) });
    const admin = adminUser();
    await call("POST", `/api/admin/submissions/${a.body.id}/decide`, { identity: admin, body: { action: "reject", code: "broken", note: "404" } });
    await call("POST", `/api/admin/submissions/${b.body.id}/decide`, { identity: admin, body: { action: "approve" } });
    await call("POST", `/api/admin/submissions/${b.body.id}/decide`, { identity: admin, body: { action: "takedown", note: "DMCA" } });
    const mine = (await call("GET", "/api/submissions", { identity })).body.submissions;
    const byId = Object.fromEntries(mine.map((s) => [s.id, s]));
    assert.deepEqual(byId[a.body.id].statusReason, { code: "broken", note: "404" });
    assert.equal(byId[b.body.id].status, "REMOVED");
    assert.equal(byId[b.body.id].statusReason.note, "DMCA");
    assert.equal((await call("GET", "/api/public/playlists")).body.playlists.length, 0);
    const bad = await call("POST", `/api/admin/submissions/${a.body.id}/decide`, { identity: admin, body: { action: "explode" } });
    assert.equal(bad.body.error, "invalid_action");
  });

  it("suspending a contributor rejects/removes all their live playlists", async ({ call, makeContributor, store }) => {
    const identity = await makeContributor("u1");
    const a = await call("POST", "/api/submissions", { identity, body: submission() });
    const b = await call("POST", "/api/submissions", { identity, body: submission({ url: "https://lists.example.com/b.m3u" }) });
    await call("POST", `/api/admin/submissions/${a.body.id}/decide`, { identity: adminUser(), body: { action: "approve" } });
    const res = await call("POST", "/api/admin/contributors/u1/status", { identity: adminUser(), body: { status: "SUSPENDED", note: "copied" } });
    assert.equal(res.body.status, "SUSPENDED");
    assert.equal((await store.getSubmission(a.body.id)).status, "REMOVED");
    assert.equal((await store.getSubmission(b.body.id)).status, "REJECTED");
    assert.equal(await store.getPublicPlaylist(a.body.id), null);
    const contributors = await call("GET", "/api/admin/contributors", { identity: adminUser() });
    assert.equal(contributors.body.contributors[0].statusNote, "copied");
  });

  it("the Telegram review flow still works for playlists (approve, reject reason, take down)", async ({ call, makeContributor, press, say, store, telegram }) => {
    const identity = await makeContributor("u1");
    const a = await call("POST", "/api/submissions", { identity, body: submission() });
    await press(MEMBER, REVIEW_CHAT, `ap:${a.body.id}`);
    assert.equal((await store.getSubmission(a.body.id)).review.name, "@reviewer_a");
    await press(MEMBER, REVIEW_CHAT, `td:${a.body.id}`);
    assert.equal((await store.getSubmission(a.body.id)).status, "APPROVED", "members cannot take down");
    await say(TG_ADMIN, PRIVATE(TG_ADMIN), `/takedown ${a.body.id} DMCA`);
    assert.equal((await store.getSubmission(a.body.id)).status, "REMOVED");

    const b = await call("POST", "/api/submissions", { identity, body: submission({ url: "https://lists.example.com/b.m3u" }) });
    await press(MEMBER, REVIEW_CHAT, `rr:${b.body.id}:copyright`);
    assert.equal((await store.getSubmission(b.body.id)).statusReason.code, "copyright");
    // Rejected as copied: the link stays locked.
    assert.equal((await call("POST", "/api/submissions", { identity, body: submission({ url: "https://lists.example.com/b.m3u" }) })).status, 409);
    assert.ok(telegram.byMethod("editMessageCaption").length >= 3);
  });

  it("/pending re-posts waiting requests and playlists; strangers are refused", async ({ call, makeContributor, say, telegram }) => {
    const identity = await makeContributor("u1");
    await call("POST", "/api/submissions", { identity, body: submission() });
    await call("POST", "/api/requests", { identity: googleUser("u2"), body: await requestBody() });
    const before = telegram.calls.length;
    await say(MEMBER, REVIEW_CHAT, "/pending");
    const posted = telegram.calls.slice(before);
    assert.ok(posted.some((c) => c.method === "sendPhoto"));
    assert.ok(posted.some((c) => c.method === "sendMessage" && /CONTRIBUTOR REQUEST/.test(c.text)));
    await say(STRANGER, PRIVATE(STRANGER), "/pending");
    assert.match(telegram.byMethod("sendMessage").at(-1).text, /not allowed/);
  });

  it("the same link by another contributor is refused; identical content is flagged", async ({ call, makeContributor, telegram }) => {
    const a = await makeContributor("u1", "Alice");
    const b = await makeContributor("u2", "Bob");
    assert.equal((await call("POST", "/api/submissions", { identity: a, body: submission() })).status, 200);
    const dup = await call("POST", "/api/submissions", { identity: b, body: submission({ url: "https://LISTS.example.com:443/a.m3u#x" }) });
    assert.equal(dup.body.error, "link_already_submitted");
    const mirror = await call("POST", "/api/submissions", { identity: b, body: submission({ url: "https://mirror.example.net/copy.m3u" }) });
    assert.equal(mirror.status, 200);
    assert.match(telegram.byMethod("sendPhoto").at(-1).caption, /Identical channel list/);
  });

  itWith({ verifyPlaylist: async () => ({ ok: false, code: "not_a_playlist", message: "Web page" }) },
    "on the own server a failing link check blocks the upload", async ({ call, makeContributor }) => {
      const identity = await makeContributor("u1");
      const res = await call("POST", "/api/submissions", { identity, body: submission() });
      assert.equal(res.body.error, "link_check_failed");
      assert.equal(res.body.details.reason, "not_a_playlist");
    });

  it("private and local links are refused before anything is fetched", async ({ call, makeContributor }) => {
    const identity = await makeContributor("u1");
    for (const url of ["http://127.0.0.1/a.m3u", "http://localhost:8080/a", "http://169.254.169.254/", "http://[::1]/a", "ftp://x.example/a"]) {
      assert.equal((await call("POST", "/api/playlists/verify", { identity, body: { url } })).status, 400, url);
    }
  });

  it("account deletion removes the request (encrypted contact) and withdraws playlists", async ({ call, makeContributor, store }) => {
    const identity = await makeContributor("u1");
    const { body } = await call("POST", "/api/submissions", { identity, body: submission() });
    await call("POST", `/api/admin/submissions/${body.id}/decide`, { identity: adminUser(), body: { action: "approve" } });
    assert.equal((await call("POST", "/api/me/delete", { identity })).status, 200);
    assert.equal(await store.getRequest("u1"), null);
    assert.equal(await store.getContributor("u1"), null);
    assert.equal((await store.getSubmission(body.id)).status, "WITHDRAWN");
    assert.equal(await store.getPublicPlaylist(body.id), null);
  });

  it("CORS only for the configured origin", async ({ call }) => {
    const good = await call("OPTIONS", "/api/me", { headers: { Origin: "https://tsiptv-8bdd6.web.app" } });
    assert.equal(good.status, 204);
    const bad = await call("OPTIONS", "/api/me", { headers: { Origin: "https://evil.example" } });
    assert.equal(bad.status, 403);
  });
}
