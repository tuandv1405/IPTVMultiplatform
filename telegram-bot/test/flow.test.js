import { test } from "node:test";
import assert from "node:assert/strict";
import {
  ADMIN,
  MEMBER,
  REVIEW_CHAT,
  STRANGER,
  okVerification,
  registration,
  startStack,
  submission,
  verifiedUser,
} from "./helpers.js";

const PRIVATE = (user) => ({ id: user.id, type: "private" });

async function contributor(stack, uid = "u1", name = "Tuan Lists") {
  const identity = verifiedUser(uid);
  const res = await stack.call("POST", "/api/contributor/register", { identity, body: registration(name) });
  assert.equal(res.status, 200, JSON.stringify(res.body));
  return identity;
}

test("onboarding enforces email, phone, policy and declarations (AC-C1..C4)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);

  assert.equal((await stack.call("GET", "/api/me")).status, 401, "no token");

  const unverifiedEmail = verifiedUser("u1", { emailVerified: false });
  let res = await stack.call("POST", "/api/contributor/register", { identity: unverifiedEmail, body: registration() });
  assert.equal(res.status, 403);
  assert.equal(res.body.error, "email_not_verified");

  const noPhone = verifiedUser("u1", { phone: null });
  res = await stack.call("POST", "/api/contributor/register", { identity: noPhone, body: registration() });
  assert.equal(res.body.error, "phone_not_verified");

  const identity = verifiedUser("u1");
  res = await stack.call("POST", "/api/contributor/register", {
    identity,
    body: { ...registration(), policyVersion: "2000-01-01" },
  });
  assert.equal(res.body.error, "policy_outdated");

  res = await stack.call("POST", "/api/contributor/register", {
    identity,
    body: { ...registration(), declarations: { contact_accurate: true, accept_policy: true } },
  });
  assert.equal(res.body.error, "declarations_required");

  res = await stack.call("POST", "/api/contributor/register", {
    identity,
    body: registration("call me 0901 234 567"),
  });
  assert.equal(res.body.error, "invalid_public_name");

  res = await stack.call("POST", "/api/contributor/register", { identity, body: registration() });
  assert.equal(res.status, 200);
  assert.equal(res.body.contributor.publicName, "Tuan Lists");
  assert.equal(res.body.contributor.status, "ACTIVE");
});

test("contact data is stored only encrypted (AC-C5)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  await stack.call("POST", "/api/submissions", { identity, body: submission() });

  const everything = JSON.stringify(stack.store.dump());
  assert.ok(!everything.includes(identity.email), "plaintext email found in a document");
  assert.ok(!everything.includes(identity.phone), "plaintext phone found in a document");
  assert.ok(!everything.includes("901234567"), "phone digits found in a document");

  const doc = stack.store.dump().contributors.u1;
  assert.match(doc.emailEnc, /^v1:/);
  assert.match(doc.phoneEnc, /^v1:/);
  assert.equal(stack.vault.decrypt(doc.emailEnc), identity.email);
  assert.equal(doc.emailHash, stack.vault.hash("email", identity.email.toUpperCase()), "hash is case-insensitive");

  const me = await stack.call("GET", "/api/me", { identity });
  assert.ok(!JSON.stringify(me.body.contributor).includes("+84"), "profile must not echo the phone");
});

test("submit → IN_REVIEW → posted to admin DMs and the review group with the image (AC-S4)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);

  const res = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  assert.equal(res.status, 200, JSON.stringify(res.body));
  assert.equal(res.body.status, "IN_REVIEW");
  assert.equal(res.body.channelCount, 12);

  const photos = stack.telegram.byMethod("sendPhoto");
  assert.deepEqual(photos.map((p) => p.chatId).sort(), ["-1001", "999"].sort());
  assert.match(photos[0].caption, /IN REVIEW/);
  assert.match(photos[0].caption, /My Vietnam news/);
  assert.ok(!photos[0].caption.includes("u1@example.com"), "reviewers must not see the email");
  assert.equal(photos[0].reply_markup.inline_keyboard[0][0].callback_data, `ap:${res.body.id}`);

  const list = await stack.call("GET", "/api/submissions", { identity });
  assert.equal(list.body.submissions.length, 1);
});

test("an admin who pressed Start in a DM receives cards too", async (t) => {
  const stack = await startStack({ configOverrides: { telegram: { adminChatIds: [] } } });
  t.after(stack.close);
  await stack.say(ADMIN, PRIVATE(ADMIN), "/start");
  const identity = await contributor(stack);
  await stack.call("POST", "/api/submissions", { identity, body: submission() });
  const chats = stack.telegram.byMethod("sendPhoto").map((p) => p.chatId).sort();
  assert.deepEqual(chats, [String(ADMIN.id), "-1001"].sort());
});

test("any member of the review group approves; every copy is updated; public doc appears (AC-V1, AC-V4)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });

  await stack.press(MEMBER, REVIEW_CHAT, `ap:${body.id}`);

  const edits = stack.telegram.byMethod("editMessageCaption");
  assert.equal(edits.length, 2, "both the DM and the group card are edited");
  for (const edit of edits) {
    assert.match(edit.caption, /APPROVED/);
    assert.match(edit.caption, /@reviewer_a/);
  }
  const published = await stack.store.getPublicPlaylist(body.id);
  assert.equal(published.name, "My Vietnam news");
  assert.equal(published.publicName, "Tuan Lists");
  assert.ok(!("ownerUid" in published), "the public doc has no uid");
  assert.ok(await stack.store.getImage("public", body.id), "the image is published too");

  const mine = await stack.call("GET", "/api/submissions", { identity });
  assert.equal(mine.body.submissions[0].status, "APPROVED");
});

test("two concurrent decisions produce one transition (AC-V2)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });

  await Promise.all([
    stack.press(MEMBER, REVIEW_CHAT, `ap:${body.id}`),
    stack.press(ADMIN, PRIVATE(ADMIN), `rr:${body.id}:broken`),
  ]);
  const final = await stack.store.getSubmission(body.id);
  assert.ok(["APPROVED", "REJECTED"].includes(final.status));
  const answers = stack.telegram.byMethod("answerCallbackQuery");
  assert.equal(answers.filter((a) => /already/i.test(a.text ?? "")).length, 1, "the loser is told it was already decided");
});

test("reject needs a reason, which the contributor sees (AC-V3)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });

  await stack.press(MEMBER, REVIEW_CHAT, `rj:${body.id}`);
  const menu = stack.telegram.byMethod("editMessageReplyMarkup").at(-1);
  assert.ok(menu.reply_markup.inline_keyboard.some((row) => row[0].callback_data === `rr:${body.id}:copyright`));
  assert.equal((await stack.store.getSubmission(body.id)).status, "IN_REVIEW", "opening the menu decides nothing");

  await stack.press(MEMBER, REVIEW_CHAT, `rr:${body.id}:copyright`);
  const mine = await stack.call("GET", "/api/submissions", { identity });
  assert.equal(mine.body.submissions[0].status, "REJECTED");
  assert.equal(mine.body.submissions[0].statusReason.code, "copyright");

  // Rejected as copied: the link stays locked.
  const again = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  assert.equal(again.status, 409);
});

test("a link rejected for another reason can be resubmitted", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  await stack.press(MEMBER, REVIEW_CHAT, `rr:${body.id}:incomplete`);
  const again = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  assert.equal(again.status, 200);
});

test("take down: admins only; removes the public doc (AC-V5, AC-V6)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  await stack.press(MEMBER, REVIEW_CHAT, `ap:${body.id}`);

  await stack.press(MEMBER, REVIEW_CHAT, `td:${body.id}`);
  assert.equal((await stack.store.getSubmission(body.id)).status, "APPROVED", "a non-admin member cannot take down");
  assert.match(stack.telegram.byMethod("answerCallbackQuery").at(-1).text, /Only admins/);

  await stack.say(MEMBER, REVIEW_CHAT, `/takedown ${body.id} dmca`);
  assert.equal((await stack.store.getSubmission(body.id)).status, "APPROVED");

  await stack.say(ADMIN, PRIVATE(ADMIN), `/takedown ${body.id} DMCA notice from the rights holder`);
  const removed = await stack.store.getSubmission(body.id);
  assert.equal(removed.status, "REMOVED");
  assert.equal(removed.statusReason.note, "DMCA notice from the rights holder");
  assert.equal(await stack.store.getPublicPlaylist(body.id), null);
  assert.equal(await stack.store.getImage("public", body.id), null);

  const mine = await stack.call("GET", "/api/submissions", { identity });
  assert.equal(mine.body.submissions[0].status, "REMOVED");
});

test("a stranger pressing a button in a private chat is refused (AC-V7)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });

  await stack.press(STRANGER, PRIVATE(STRANGER), `ap:${body.id}`);
  assert.equal((await stack.store.getSubmission(body.id)).status, "IN_REVIEW");
  assert.equal(stack.telegram.byMethod("answerCallbackQuery").at(-1).showAlert, true);
});

test("/contact: admins only, private chat only (AC-V6)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });

  await stack.say(ADMIN, REVIEW_CHAT, `/contact ${body.id}`);
  let last = stack.telegram.byMethod("sendMessage").at(-1);
  assert.ok(!last.text.includes(identity.email), "never in a group");

  await stack.say(MEMBER, PRIVATE(MEMBER), `/contact ${body.id}`);
  last = stack.telegram.byMethod("sendMessage").at(-1);
  assert.ok(!last.text.includes(identity.email), "never to a non-admin");

  await stack.say(ADMIN, PRIVATE(ADMIN), `/contact ${body.id}`);
  last = stack.telegram.byMethod("sendMessage").at(-1);
  assert.ok(last.text.includes(identity.email));
  assert.ok(last.text.includes(identity.phone));
});

test("the same link by another contributor is refused (AC-S5)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const a = await contributor(stack, "u1", "Alice");
  const b = await contributor(stack, "u2", "Bob");
  assert.equal((await stack.call("POST", "/api/submissions", { identity: a, body: submission() })).status, 200);
  const res = await stack.call("POST", "/api/submissions", {
    identity: b,
    // Same list, different spelling of the host and a fragment.
    body: submission({ url: "https://LISTS.example.com:443/a.m3u#x" }),
  });
  assert.equal(res.status, 409);
  assert.equal(res.body.error, "link_already_submitted");
});

test("identical content from another contributor is flagged to reviewers (AC-S6)", async (t) => {
  const stack = await startStack({ verifyPlaylist: async () => okVerification({ contentHash: "same" }) });
  t.after(stack.close);
  const a = await contributor(stack, "u1", "Alice");
  const b = await contributor(stack, "u2", "Bob");
  const first = await stack.call("POST", "/api/submissions", { identity: a, body: submission() });
  const second = await stack.call("POST", "/api/submissions", {
    identity: b,
    body: submission({ url: "https://mirror.example.net/copy.m3u" }),
  });
  assert.equal(second.status, 200);
  const card = stack.telegram.byMethod("sendPhoto").at(-1);
  assert.match(card.caption, /Identical channel list/);
  assert.ok(card.caption.includes(first.body.id));
});

test("at most 3 in review at a time (AC-S7)", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  for (let i = 0; i < 3; i++) {
    const res = await stack.call("POST", "/api/submissions", {
      identity,
      body: submission({ url: `https://lists.example.com/${i}.m3u` }),
    });
    assert.equal(res.status, 200);
  }
  const res = await stack.call("POST", "/api/submissions", {
    identity,
    body: submission({ url: "https://lists.example.com/4.m3u" }),
  });
  assert.equal(res.status, 429);
  assert.equal(res.body.error, "too_many_in_review");
});

test("a failing link check blocks the submission (AC-S3)", async (t) => {
  const stack = await startStack({
    verifyPlaylist: async () => ({ ok: false, code: "not_a_playlist", message: "The link returns a web page." }),
  });
  t.after(stack.close);
  const identity = await contributor(stack);
  const res = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  assert.equal(res.status, 400);
  assert.equal(res.body.error, "link_check_failed");
  assert.equal(res.body.details.reason, "not_a_playlist");
  assert.equal(stack.telegram.byMethod("sendPhoto").length, 0, "nothing reaches reviewers");
});

test("private and local links are refused before anything is fetched (AC-S2)", async (t) => {
  let fetched = 0;
  const stack = await startStack({ verifyPlaylist: async () => (fetched++, okVerification()) });
  t.after(stack.close);
  const identity = await contributor(stack);
  for (const url of [
    "http://127.0.0.1/a.m3u",
    "http://localhost:8080/a.m3u",
    "http://10.0.0.5/a.m3u",
    "http://192.168.1.1/a.m3u",
    "http://169.254.169.254/latest/meta-data",
    "http://[::1]/a.m3u",
    "ftp://example.com/a.m3u",
    "https://user:pass@example.com/a.m3u",
    "http://example.com:22/a.m3u",
  ]) {
    const res = await stack.call("POST", "/api/playlists/verify", { identity, body: { url } });
    assert.equal(res.status, 400, url);
  }
  assert.equal(fetched, 0);
});

test("submission requires image, declarations and valid fields", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const cases = [
    [{ image: undefined }, "invalid_image"],
    [{ image: "data:image/png;base64,AAAA" }, "invalid_image"],
    [{ declarations: { own_work: true, not_copied: true } }, "declarations_required"],
    [{ name: "ab" }, "invalid_name"],
    [{ category: "warez" }, "invalid_category"],
    [{ language: "vietnamese" }, "invalid_language"],
  ];
  for (const [override, code] of cases) {
    const res = await stack.call("POST", "/api/submissions", { identity, body: submission(override) });
    assert.equal(res.body.error, code, JSON.stringify(override));
  }
});

test("withdraw an approved playlist unpublishes it and frees the link", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  await stack.press(MEMBER, REVIEW_CHAT, `ap:${body.id}`);

  const other = verifiedUser("u9");
  assert.equal((await stack.call("POST", `/api/submissions/${body.id}/withdraw`, { identity: other })).status, 404);

  const res = await stack.call("POST", `/api/submissions/${body.id}/withdraw`, { identity });
  assert.equal(res.body.status, "WITHDRAWN");
  assert.equal(await stack.store.getPublicPlaylist(body.id), null);
  assert.match(stack.telegram.byMethod("editMessageCaption").at(-1).caption, /WITHDRAWN/);
  assert.equal((await stack.call("POST", "/api/submissions", { identity, body: submission() })).status, 200);
});

test("/ban removes every live playlist and blocks further submissions", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const a = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  const b = await stack.call("POST", "/api/submissions", {
    identity,
    body: submission({ url: "https://lists.example.com/b.m3u" }),
  });
  await stack.press(MEMBER, REVIEW_CHAT, `ap:${a.body.id}`);

  await stack.say(ADMIN, PRIVATE(ADMIN), "/ban u1 stolen lists");
  assert.equal((await stack.store.getSubmission(a.body.id)).status, "REMOVED");
  assert.equal((await stack.store.getSubmission(b.body.id)).status, "REJECTED");
  const res = await stack.call("POST", "/api/submissions", {
    identity,
    body: submission({ url: "https://lists.example.com/c.m3u" }),
  });
  assert.equal(res.body.error, "banned");
});

test("CORS only for the configured origin", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const good = await stack.call("OPTIONS", "/api/me", { headers: { Origin: "https://tsiptv-8bdd6.web.app" } });
  assert.equal(good.status, 204);
  assert.equal(good.headers.get("access-control-allow-origin"), "https://tsiptv-8bdd6.web.app");
  const bad = await stack.call("OPTIONS", "/api/me", { headers: { Origin: "https://evil.example" } });
  assert.equal(bad.status, 403);
  assert.equal(bad.headers.get("access-control-allow-origin"), null);
});

test("/pending re-posts the queue into the asking chat and tracks the new message", async (t) => {
  const stack = await startStack();
  t.after(stack.close);
  const identity = await contributor(stack);
  const { body } = await stack.call("POST", "/api/submissions", { identity, body: submission() });
  const before = stack.telegram.byMethod("sendPhoto").length;
  await stack.say(MEMBER, REVIEW_CHAT, "/pending");
  assert.equal(stack.telegram.byMethod("sendPhoto").length, before + 1);
  assert.equal((await stack.store.getSubmission(body.id)).telegramMessages.length, 3);

  await stack.say(STRANGER, PRIVATE(STRANGER), "/pending");
  assert.match(stack.telegram.byMethod("sendMessage").at(-1).text, /not allowed/);
});
