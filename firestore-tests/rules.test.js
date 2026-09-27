// Tests for ../firestore.rules (docs/prd-contributor-requests.md, AC-Q2..Q10).
// Run: npm test   (starts the Firestore emulator through firebase emulators:exec)
import { test, before, after, beforeEach } from "node:test";
import { readFileSync } from "node:fs";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import {
  collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc, where, writeBatch,
} from "firebase/firestore";

const ADMIN_EMAIL = "chintk111999@gmail.com";
// The rules derive the lock key from the (normalised) link: sha256 hex.
const URL_A = "https://lists.example.com/a.m3u";
const sha = (text) => createHash("sha256").update(text).digest("hex");
const HASH = sha(URL_A);
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: process.env.GCLOUD_PROJECT ?? "tsiptv-8bdd6",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const google = (email) => ({ email, email_verified: true, firebase: { sign_in_provider: "google.com" } });
const user = (uid, email = `${uid}@gmail.com`) => env.authenticatedContext(uid, google(email)).firestore();
const admin = () => env.authenticatedContext("owner", google(ADMIN_EMAIL)).firestore();
const anon = () => env.unauthenticatedContext().firestore();

const contactEnc = { v: 1, alg: "RSA-OAEP-256+A256GCM", kid: "9abf8a22e4a7c937", wrappedKey: "w".repeat(512), iv: "i".repeat(16), ct: "c".repeat(200) };

const requestDoc = (uid, extra = {}) => ({
  uid,
  publicName: "Tuan Lists",
  about: "I maintain a list of Vietnamese public news channels.",
  links: ["https://example.com/a.m3u"],
  contactEnc,
  consents: { policy: "2026-09-27", terms: "2026-09-16", privacy: "2026-09-27", acceptedAt: serverTimestamp() },
  status: "PENDING",
  attempt: 1,
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  ...extra,
});

/** Seed data bypassing the rules. */
const seed = (fn) => env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));

const seedContributor = (uid, status = "ACTIVE") =>
  seed((db) => setDoc(doc(db, "contributors", uid), { uid, publicName: "Tuan Lists", status, approvedAt: 1, approvedBy: "owner", updatedAt: 1 }));

function submissionBatch(db, uid, id, overrides = {}, { image = true, lock = true } = {}) {
  const batch = writeBatch(db);
  batch.set(doc(db, "playlist_submissions", id), {
    id,
    ownerUid: uid,
    publicName: "Tuan Lists",
    name: "My news list",
    url: overrides.url ?? URL_A,
    urlHash: sha(overrides.url ?? URL_A),
    description: "",
    category: "news",
    language: "vi",
    declarations: { own_work: true, not_copied: true, rights_ok: true, policyVersion: "2026-09-27" },
    verification: { ok: null, method: "browser", format: null, channelCount: null, groupCount: null },
    status: "IN_REVIEW",
    statusReason: null,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...overrides,
  });
  if (image) batch.set(doc(db, "playlist_submission_images", id), { mime: "image/jpeg", data: "x".repeat(200) });
  if (lock) batch.set(doc(db, "playlist_urls", overrides.urlHash ?? sha(overrides.url ?? URL_A)), { submissionId: id, ownerUid: uid });
  return batch;
}

// ---- requests ---------------------------------------------------------------

test("AC-Q1/Q4: signed-out and other users cannot read a request", async () => {
  await assertSucceeds(setDoc(doc(user("u1"), "contributor_requests", "u1"), requestDoc("u1")));
  await assertFails(getDoc(doc(anon(), "contributor_requests", "u1")));
  await assertFails(getDoc(doc(user("u2"), "contributor_requests", "u1")));
  await assertFails(getDocs(collection(user("u2"), "contributor_requests")));
  await assertSucceeds(getDoc(doc(user("u1"), "contributor_requests", "u1")));
  await assertSucceeds(getDocs(collection(admin(), "contributor_requests")));
});

test("AC-Q2: one request at a time — no second request while PENDING", async () => {
  const db = user("u1");
  await assertSucceeds(setDoc(doc(db, "contributor_requests", "u1"), requestDoc("u1")));
  await assertFails(setDoc(doc(db, "contributor_requests", "u1"), requestDoc("u1", { attempt: 2 })));
  await assertFails(setDoc(doc(db, "contributor_requests", "u1"), requestDoc("u1")));
});

test("a request must be the user's own, from Google, complete and without plaintext contact", async () => {
  const db = user("u1");
  await assertFails(setDoc(doc(db, "contributor_requests", "u2"), requestDoc("u2")));
  await assertFails(setDoc(doc(db, "contributor_requests", "u1"), requestDoc("u1", { phone: "+84901234567" })));
  await assertFails(setDoc(doc(db, "contributor_requests", "u1"), requestDoc("u1", { status: "APPROVED" })));
  await assertFails(setDoc(doc(db, "contributor_requests", "u1"), requestDoc("u1", { about: "short" })));
  await assertFails(setDoc(doc(db, "contributor_requests", "u1"), requestDoc("u1", { contactEnc: { ...contactEnc, alg: "none" } })));
  await assertFails(setDoc(doc(db, "contributor_requests", "u1"), requestDoc("u1", { links: ["javascript:alert(1)"] })));
  const { consents, ...noConsents } = requestDoc("u1");
  await assertFails(setDoc(doc(db, "contributor_requests", "u1"), noConsents));
  const password = env.authenticatedContext("u1", { email: "u1@x.com", email_verified: true, firebase: { sign_in_provider: "password" } }).firestore();
  await assertFails(setDoc(doc(password, "contributor_requests", "u1"), requestDoc("u1")));
});

test("AC-Q10: a user cannot approve their own request or make themselves a contributor", async () => {
  const db = user("u1");
  await setDoc(doc(db, "contributor_requests", "u1"), requestDoc("u1"));
  await assertFails(updateDoc(doc(db, "contributor_requests", "u1"), {
    status: "APPROVED", decision: { by: "u1", name: "x", at: serverTimestamp(), reason: "" }, updatedAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(db, "contributors", "u1"), { uid: "u1", publicName: "Tuan Lists", status: "ACTIVE", approvedAt: serverTimestamp(), approvedBy: "u1", updatedAt: serverTimestamp() }));
});

test("AC-Q7: admin approves (with the contributor doc in the same batch) or rejects", async () => {
  await setDoc(doc(user("u1"), "contributor_requests", "u1"), requestDoc("u1"));
  const db = admin();
  const decision = (status) => ({ status, decision: { by: "owner", name: ADMIN_EMAIL, at: serverTimestamp(), reason: "" }, updatedAt: serverTimestamp() });

  // Approval without creating the contributor is refused.
  await assertFails(updateDoc(doc(db, "contributor_requests", "u1"), decision("APPROVED")));

  const batch = writeBatch(db);
  batch.update(doc(db, "contributor_requests", "u1"), decision("APPROVED"));
  batch.set(doc(db, "contributors", "u1"), { uid: "u1", publicName: "Tuan Lists", status: "ACTIVE", approvedAt: serverTimestamp(), approvedBy: "owner", updatedAt: serverTimestamp() });
  await assertSucceeds(batch.commit());
  await assertSucceeds(getDoc(doc(user("u1"), "contributors", "u1")));
  await assertFails(getDoc(doc(user("u2"), "contributors", "u1")));

  // A non-admin with a similar-looking email is not an admin.
  const fake = env.authenticatedContext("x", google("chintk111999@gmail.com.evil.com")).firestore();
  await assertFails(getDocs(collection(fake, "contributor_requests")));
});

test("AC-Q3: after REJECTED the user may apply again (attempt + 1); after APPROVED never", async () => {
  await setDoc(doc(user("u1"), "contributor_requests", "u1"), requestDoc("u1"));
  await updateDoc(doc(admin(), "contributor_requests", "u1"), {
    status: "REJECTED", decision: { by: "owner", name: ADMIN_EMAIL, at: serverTimestamp(), reason: "Tell us more" }, updatedAt: serverTimestamp(),
  });
  await assertFails(setDoc(doc(user("u1"), "contributor_requests", "u1"), requestDoc("u1", { attempt: 1 })));
  await assertSucceeds(setDoc(doc(user("u1"), "contributor_requests", "u1"), requestDoc("u1", { attempt: 2 })));

  await seed((db) => setDoc(doc(db, "contributor_requests", "u2"), { ...requestDoc("u2"), status: "APPROVED", consents: {}, createdAt: 1, updatedAt: 1 }));
  await assertFails(setDoc(doc(user("u2"), "contributor_requests", "u2"), requestDoc("u2", { attempt: 2 })));
});

test("the owner may cancel (delete) their request", async () => {
  await setDoc(doc(user("u1"), "contributor_requests", "u1"), requestDoc("u1"));
  await assertFails(deleteDoc(doc(user("u2"), "contributor_requests", "u1")));
  await assertSucceeds(deleteDoc(doc(user("u1"), "contributor_requests", "u1")));
});

// ---- submissions --------------------------------------------------------------

test("AC-Q8: non-contributors and suspended contributors cannot upload", async () => {
  await assertFails(submissionBatch(user("u1"), "u1", "s1").commit());
  await seedContributor("u2", "SUSPENDED");
  await assertFails(submissionBatch(user("u2"), "u2", "s2").commit());
});

test("an active contributor uploads with image and link lock; incomplete batches fail", async () => {
  await seedContributor("u1");
  const db = user("u1");
  await assertFails(submissionBatch(db, "u1", "s0", {}, { image: false }).commit());
  await assertFails(submissionBatch(db, "u1", "s0", {}, { lock: false }).commit());
  await assertFails(submissionBatch(db, "u1", "s0", { publicName: "Someone Else" }).commit());
  await assertFails(submissionBatch(db, "u1", "s0", { status: "APPROVED" }).commit());
  await assertFails(submissionBatch(db, "u1", "s0", { declarations: { own_work: true, not_copied: false, rights_ok: true, policyVersion: "x1234" } }).commit());
  await assertSucceeds(submissionBatch(db, "u1", "s1").commit());

  // Owner sees it; others do not.
  await assertSucceeds(getDocs(query(collection(db, "playlist_submissions"), where("ownerUid", "==", "u1"))));
  await assertFails(getDoc(doc(user("u9"), "playlist_submissions", "s1")));
  await assertFails(getDocs(collection(db, "playlist_submissions")));
  await assertFails(getDoc(doc(user("u9"), "playlist_submission_images", "s1")));
});

test("a link already taken by another contributor cannot be submitted again", async () => {
  await seedContributor("u1");
  await seedContributor("u2");
  await assertSucceeds(submissionBatch(user("u1"), "u1", "s1").commit());
  await assertFails(submissionBatch(user("u2"), "u2", "s2").commit());
});

test("AC-Q9/Q10: only admins publish; owners withdraw and must unpublish in the same batch", async () => {
  await seedContributor("u1");
  await submissionBatch(user("u1"), "u1", "s1").commit();
  const review = () => ({ by: "owner", name: ADMIN_EMAIL, at: serverTimestamp() });

  // The owner cannot approve or publish.
  await assertFails(updateDoc(doc(user("u1"), "playlist_submissions", "s1"), { status: "APPROVED", statusReason: null, review: { by: "u1", name: "x", at: serverTimestamp() }, updatedAt: serverTimestamp() }));
  await assertFails(setDoc(doc(user("u1"), "public_playlists", "s1"), { id: "s1", name: "x" }));

  // Approval must publish in the same batch.
  const db = admin();
  await assertFails(updateDoc(doc(db, "playlist_submissions", "s1"), { status: "APPROVED", statusReason: null, review: review(), updatedAt: serverTimestamp() }));
  const approve = writeBatch(db);
  approve.update(doc(db, "playlist_submissions", "s1"), { status: "APPROVED", statusReason: null, review: review(), updatedAt: serverTimestamp() });
  approve.set(doc(db, "public_playlists", "s1"), { id: "s1", name: "My news list", url: "https://lists.example.com/a.m3u", description: "", category: "news", language: "vi", publicName: "Tuan Lists", channelCount: null, groupCount: null, format: null, approvedAt: serverTimestamp() });
  approve.set(doc(db, "public_playlist_images", "s1"), { mime: "image/jpeg", data: "x".repeat(200) });
  await assertSucceeds(approve.commit());
  await assertSucceeds(getDoc(doc(anon(), "public_playlists", "s1")));
  await assertSucceeds(getDoc(doc(anon(), "public_playlist_images", "s1")));

  // Withdrawing an approved playlist without removing it from the directory is refused.
  const owner = user("u1");
  const withdraw = { status: "WITHDRAWN", statusReason: { code: "withdrawn" }, updatedAt: serverTimestamp() };
  await assertFails(updateDoc(doc(owner, "playlist_submissions", "s1"), withdraw));
  const batch = writeBatch(owner);
  batch.update(doc(owner, "playlist_submissions", "s1"), withdraw);
  batch.delete(doc(owner, "public_playlists", "s1"));
  batch.delete(doc(owner, "public_playlist_images", "s1"));
  batch.delete(doc(owner, "playlist_urls", HASH));
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(doc(anon(), "public_playlists", "s1"))).exists(), false);
});

test("admin takedown must remove the public copy; other users cannot delete it", async () => {
  await seedContributor("u1");
  await submissionBatch(user("u1"), "u1", "s1").commit();
  await seed(async (db) => {
    await updateDoc(doc(db, "playlist_submissions", "s1"), { status: "APPROVED" });
    await setDoc(doc(db, "public_playlists", "s1"), { id: "s1", name: "x" });
  });
  await assertFails(deleteDoc(doc(user("u9"), "public_playlists", "s1")));
  const db = admin();
  const takedown = { status: "REMOVED", statusReason: { code: "removed", note: "DMCA" }, review: { by: "owner", name: ADMIN_EMAIL, at: serverTimestamp() }, updatedAt: serverTimestamp() };
  await assertFails(updateDoc(doc(db, "playlist_submissions", "s1"), takedown));
  const batch = writeBatch(db);
  batch.update(doc(db, "playlist_submissions", "s1"), takedown);
  batch.delete(doc(db, "public_playlists", "s1"));
  await assertSucceeds(batch.commit());
});

test("admin suspends and reinstates a contributor; the contributor cannot", async () => {
  await seedContributor("u1");
  await assertFails(updateDoc(doc(user("u1"), "contributors", "u1"), { status: "ACTIVE", statusNote: "", updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(admin(), "contributors", "u1"), { status: "SUSPENDED", statusNote: "stolen lists", updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(admin(), "contributors", "u1"), { status: "ACTIVE", statusNote: "", updatedAt: serverTimestamp() }));
});

test("app user data stays private and unknown collections are closed", async () => {
  await assertSucceeds(setDoc(doc(user("u1"), "users", "u1", "prefs", "p"), { a: 1 }));
  await assertFails(getDoc(doc(user("u2"), "users", "u1", "prefs", "p")));
  await assertFails(getDoc(doc(user("u1"), "bot_state", "x")));
  await assertFails(setDoc(doc(admin(), "anything", "x"), { a: 1 }));
});

// ---- regressions from the v2 QC pass -------------------------------------------

const approveBatch = (db, uid, publicName = "Tuan Lists") => {
  const batch = writeBatch(db);
  batch.update(doc(db, "contributor_requests", uid), { status: "APPROVED", decision: { by: "owner", name: "Admin", at: serverTimestamp(), reason: "" }, updatedAt: serverTimestamp() });
  batch.set(doc(db, "contributors", uid), { uid, publicName, status: "ACTIVE", approvedAt: serverTimestamp(), approvedBy: "owner", updatedAt: serverTimestamp() });
  return batch;
};

test("QC-1: an approved or suspended contributor cannot apply again, and approval never overwrites a contributor", async () => {
  await setDoc(doc(user("u1"), "contributor_requests", "u1"), requestDoc("u1"));
  await approveBatch(admin(), "u1").commit();
  await updateDoc(doc(admin(), "contributors", "u1"), { status: "SUSPENDED", statusNote: "x", updatedAt: serverTimestamp() });
  // Delete the request and apply again: refused.
  await assertSucceeds(deleteDoc(doc(user("u1"), "contributor_requests", "u1")));
  await assertFails(setDoc(doc(user("u1"), "contributor_requests", "u1"), requestDoc("u1", { publicName: "Second Name" })));
  // Even if a pending request exists, approving it cannot overwrite the suspended contributor.
  await seed((db) => setDoc(doc(db, "contributor_requests", "u1"), { ...requestDoc("u1"), consents: {}, createdAt: 1, updatedAt: 1 }));
  await assertFails(approveBatch(admin(), "u1").commit());
});

test("QC-2: the link lock key is derived from the link, not trusted from the client", async () => {
  await seedContributor("u1");
  await seedContributor("u2");
  await submissionBatch(user("u1"), "u1", "s1").commit();
  // Same link with a made-up hash.
  await assertFails(submissionBatch(user("u2"), "u2", "s2", { urlHash: "b".repeat(64) }).commit());
  // Own link, squatting the hash of someone else's link.
  const victim = sha("https://victim.example/list.m3u");
  await assertFails(submissionBatch(user("u2"), "u2", "s3", { url: "https://mine.example/x.m3u", urlHash: victim }).commit());
});

test("QC-4: browser link checks are bounded and must say they come from the browser", async () => {
  await seedContributor("u1");
  const db = user("u1");
  await assertFails(submissionBatch(db, "u1", "s1", { verification: { ok: true, method: "server", format: "m3u", channelCount: 5, groupCount: 1 } }).commit());
  await assertFails(submissionBatch(db, "u1", "s1", { verification: { ok: true, method: "browser", format: "m3u", channelCount: 99999999, groupCount: 1 } }).commit());
  await assertFails(submissionBatch(db, "u1", "s1", { verification: { ok: true, method: "browser", format: "m3u", channelCount: 5, groupCount: 1, junk: "x".repeat(700000) } }).commit());
  await assertSucceeds(submissionBatch(db, "u1", "s1", { verification: { ok: true, method: "browser", format: "m3u", channelCount: 5, groupCount: null } }).commit());
});

test("QC-10: approval must use the public name the user asked for", async () => {
  await setDoc(doc(user("u1"), "contributor_requests", "u1"), requestDoc("u1"));
  await assertFails(approveBatch(admin(), "u1", "Someone Else").commit());
  await assertSucceeds(approveBatch(admin(), "u1").commit());
});

test("QC-11: withdrawing an approved playlist must also remove its public image", async () => {
  await seedContributor("u1");
  await submissionBatch(user("u1"), "u1", "s1").commit();
  await seed(async (db) => {
    await updateDoc(doc(db, "playlist_submissions", "s1"), { status: "APPROVED" });
    await setDoc(doc(db, "public_playlists", "s1"), { id: "s1", name: "x" });
    await setDoc(doc(db, "public_playlist_images", "s1"), { mime: "image/jpeg", data: "x" });
  });
  const owner = user("u1");
  const partial = writeBatch(owner);
  partial.update(doc(owner, "playlist_submissions", "s1"), { status: "WITHDRAWN", statusReason: { code: "withdrawn" }, updatedAt: serverTimestamp() });
  partial.delete(doc(owner, "public_playlists", "s1"));
  await assertFails(partial.commit());
});

test("QC-13/14: the owner email counts only with Google sign-in; link locks are not readable by everyone", async () => {
  const password = env.authenticatedContext("x", { email: ADMIN_EMAIL, email_verified: true, firebase: { sign_in_provider: "password" } }).firestore();
  await assertFails(getDocs(collection(password, "contributor_requests")));
  await seedContributor("u1");
  await submissionBatch(user("u1"), "u1", "s1").commit();
  await assertFails(getDoc(doc(user("u9"), "playlist_urls", HASH)));
  const anonymous = env.authenticatedContext("anon", { firebase: { sign_in_provider: "anonymous" } }).firestore();
  await assertFails(getDoc(doc(anonymous, "playlist_urls", HASH)));
  await assertSucceeds(getDoc(doc(user("u1"), "playlist_urls", HASH)));
  await assertSucceeds(getDoc(doc(admin(), "playlist_urls", HASH)));
});
test("QC-2b: one link has one spelling — non-normalised links are refused", async () => {
  await seedContributor("u2");
  const db = user("u2");
  for (const url of [
    "https://LISTS.example.com/a.m3u",
    "https://lists.example.com:443/a.m3u",
    "http://lists.example.com:80/a.m3u",
    "https://lists.example.com/a.m3u#x",
    "https://lists.example.com/x/../a.m3u",
    "https://lists.example.com/./a.m3u",
    "https://user@lists.example.com/a.m3u",
    "https://lists.example.com",
    "https://lists.example.com:0443/a.m3u",
    "https://lists.example.com/%2e/a.m3u",
    "https://lists.example.com./a.m3u",
    "https://lists.example.com//a.m3u",
    "https://lists.example.com/a.m3u?",
  ]) {
    await assertFails(submissionBatch(db, "u2", `s-${sha(url).slice(0, 8)}`, { url }).commit()).catch(() => {
      throw new Error(`accepted a non-normalised link: ${url}`);
    });
  }
  await assertSucceeds(submissionBatch(db, "u2", "s-ok", { url: "https://lists.example.com:8080/a.m3u?x=1" }).commit());
});