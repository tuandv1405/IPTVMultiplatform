// Contact encryption, the SQLite store and the Firestore → SQLite migration.
import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { decryptContact, encryptContact, importPrivateKey } from "../src/contactCrypto.js";
import { migrate } from "../src/migrate.js";
import { createContributorService } from "../src/service.js";
import { createSqliteStore } from "../src/store/sqliteStore.js";
import { PRIVATE_KEY, PUBLIC_JWK, okVerification, silentLog } from "./helpers.js";

test("contact encryption: round trip, fresh key per message, wrong key fails", async () => {
  const payload = { fullName: "Nguyen Van Tuan", email: "a@gmail.com", phone: "+84901234567" };
  const a = await encryptContact(PUBLIC_JWK, payload);
  const b = await encryptContact(PUBLIC_JWK, payload);
  assert.notEqual(a.ct, b.ct);
  assert.ok(!JSON.stringify(a).includes("+84901234567"));
  assert.deepEqual(await decryptContact(PRIVATE_KEY, a), payload);

  const other = await crypto.subtle.generateKey(
    { name: "RSA-OAEP", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["encrypt", "decrypt"]);
  const wrong = await importPrivateKey(await crypto.subtle.exportKey("jwk", other.privateKey));
  await assert.rejects(decryptContact(wrong, a));
  await assert.rejects(decryptContact(PRIVATE_KEY, { ...a, ct: a.ct.slice(0, -4) + "AAAA" }), "tampering is detected");
});

test("the browser and the server use the same contact-crypto code", (t) => {
  const web = new URL("../../web/public/assets/contact-crypto.js", import.meta.url);
  if (!existsSync(web)) return t.skip("web/ is not next to telegram-bot/ here (e.g. inside the Docker image)");
  assert.equal(readFileSync(new URL("../src/contactCrypto.js", import.meta.url), "utf8"), readFileSync(web, "utf8"),
    "copy web/public/assets/contact-crypto.js to telegram-bot/src/contactCrypto.js");
});

test("the server's firestore.rules admin list matches the web config and the server default", (t) => {
  const rules = new URL("../../firestore.rules", import.meta.url);
  const web = new URL("../../web/public/assets/contributor-config.js", import.meta.url);
  if (!existsSync(rules) || !existsSync(web)) return t.skip("repository layout not available");
  const inRules = /email', ''\) in \[([^\]]*)\]/.exec(readFileSync(rules, "utf8"))[1];
  const inWeb = /ADMIN_EMAILS = \[([^\]]*)\]/.exec(readFileSync(web, "utf8"))[1];
  const norm = (list) => list.replace(/["'\s]/g, "").split(",").sort().join(",");
  assert.equal(norm(inRules), norm(inWeb));
  assert.equal(norm(inRules), "chintk111999@gmail.com");
});

function tempDb() {
  const dir = mkdtempSync(join(tmpdir(), "tsiptv-"));
  return { path: join(dir, "db.sqlite"), cleanup: () => rmSync(dir, { recursive: true, force: true }) };
}

test("sqlite: data survives closing and reopening the file", async (t) => {
  const { path, cleanup } = tempDb();
  let store = createSqliteStore(path);
  await store.saveRequest("u1", () => ({ uid: "u1", status: "PENDING", attempt: 1, createdAt: 1 }));
  await store.putContributor("u2", { uid: "u2", status: "ACTIVE", publicName: "B" });
  await store.createSubmission({ id: "s1", ownerUid: "u2", urlHash: "h1", status: "IN_REVIEW", createdAt: 5, contentHash: "c" });
  await store.setState("admin_chats", ["1"]);
  store.close();

  store = createSqliteStore(path);
  // Close before deleting: Windows will not remove an open database file.
  t.after(() => { store.close(); cleanup(); });
  assert.equal((await store.getRequest("u1")).status, "PENDING");
  assert.equal((await store.getContributor("u2")).publicName, "B");
  assert.equal((await store.listSubmissionsByStatus("IN_REVIEW")).length, 1);
  assert.equal((await store.findSubmissionsByContentHash("c")).length, 1);
  assert.deepEqual(await store.getState("admin_chats"), ["1"]);
  await assert.rejects(
    store.createSubmission({ id: "s2", ownerUid: "u3", urlHash: "h1", status: "IN_REVIEW", createdAt: 6 }),
    { code: "link_already_submitted" },
  );
  assert.equal(await store.getSubmission("s2"), null, "the failed transaction left nothing behind");
});

/** A stand-in for the Admin SDK: collection(name).get() → { size, docs: [{ id, data() }] }. */
function fakeFirestore(collections) {
  const ts = (ms) => ({ toMillis: () => ms });
  const withTimestamps = (doc) => JSON.parse(JSON.stringify(doc), (key, value) =>
    typeof value === "number" && /At$/.test(key) ? ts(value) : value);
  return {
    collection: (name) => ({
      get: async () => {
        const docs = Object.entries(collections[name] ?? {}).map(([id, data]) => ({ id, data: () => withTimestamps(data) }));
        return { size: docs.length, docs };
      },
    }),
  };
}

test("migration: Firestore-phase data moves to SQLite and the server works on it", async (t) => {
  const { path, cleanup } = tempDb();
  const contactEnc = await encryptContact(PUBLIC_JWK, { fullName: "A B", email: "u1@gmail.com", phone: "+84900000000" });
  const firestore = fakeFirestore({
    contributor_requests: {
      u1: { uid: "u1", publicName: "Pending Person", about: "x".repeat(30), links: [], contactEnc, consents: { policy: "2026-09-27", terms: "2026-09-16", privacy: "2026-09-27", acceptedAt: 10 }, status: "PENDING", attempt: 1, createdAt: 10, updatedAt: 10 },
      u2: { uid: "u2", publicName: "Approved Person", about: "y".repeat(30), links: [], contactEnc, consents: {}, status: "APPROVED", attempt: 1, createdAt: 5, updatedAt: 6, decision: { by: "owner", name: "o", at: 6, reason: "" } },
    },
    contributors: { u2: { uid: "u2", publicName: "Approved Person", status: "ACTIVE", approvedAt: 6, approvedBy: "owner", updatedAt: 6 } },
    playlist_submissions: {
      s1: { id: "s1", ownerUid: "u2", publicName: "Approved Person", name: "List", url: "https://x.example/a.m3u", urlHash: "h".repeat(64), description: "", category: "news", language: "vi", declarations: {}, verification: null, status: "IN_REVIEW", statusReason: null, createdAt: 7, updatedAt: 7 },
    },
    playlist_urls: { ["h".repeat(64)]: { submissionId: "s1", ownerUid: "u2" } },
    playlist_submission_images: { s1: { mime: "image/jpeg", data: "abc" } },
  });
  const store = createSqliteStore(path);
  t.after(() => { store.close(); cleanup(); });
  const counts = await migrate(firestore, store, () => {});
  assert.equal(counts.contributor_requests, 2);

  const request = await store.getRequest("u1");
  assert.equal(request.createdAt, 10, "Timestamps became millis");
  assert.equal(request.consents.acceptedAt, 10);
  assert.deepEqual(request.telegramMessages, []);

  const service = createContributorService({
    store, verifyPlaylist: async () => okVerification(), notifier: { announce: async () => {}, refresh: async () => {} },
    adminEmails: ["o@gmail.com"], contactPrivateKey: PRIVATE_KEY, log: silentLog,
  });
  const admin = { uid: "o", email: "o@gmail.com", emailVerified: true, provider: "google.com", claims: {} };
  assert.equal((await service.listRequests(admin, "PENDING")).length, 1);
  assert.equal((await service.adminDecideRequest(admin, "u1", { decision: "APPROVED" })).status, "APPROVED");
  assert.equal((await store.getContributor("u1")).status, "ACTIVE");
  assert.equal((await service.adminDecideSubmission(admin, "s1", { action: "approve" })).status, "APPROVED");
  assert.equal((await store.getPublicPlaylist("s1")).name, "List");
  assert.equal((await store.getImage("public", "s1")).data, "abc");
  assert.equal((await service.contact("u1")).phone, "+84900000000", "encrypted contact data survives the move");
});
