// AC-Q11: the site's real contributor-backend.js in BACKEND="http" mode, against
// the real own-server (telegram-bot/) on SQLite, with real Firebase ID tokens from
// the Auth emulator (the Admin SDK verifies emulator tokens when
// FIREBASE_AUTH_EMULATOR_HOST is set). Nothing touches production.
import { test, after } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { initializeApp as initAdmin } from "../telegram-bot/node_modules/firebase-admin/lib/app/index.js";
import { getAuth as getAdminAuth } from "../telegram-bot/node_modules/firebase-admin/lib/auth/index.js";
import { createFirebaseAuthVerifier } from "../telegram-bot/src/auth.js";
import { createHttpServer } from "../telegram-bot/src/http/server.js";
import { createContributorService } from "../telegram-bot/src/service.js";
import { createSqliteStore } from "../telegram-bot/src/store/sqliteStore.js";

// ---- the own server, in-process ----
const dir = mkdtempSync(join(tmpdir(), "tsiptv-http-"));
const store = createSqliteStore(join(dir, "db.sqlite"));
const adminApp = initAdmin({ projectId: process.env.GCLOUD_PROJECT }, "http-test");
const service = createContributorService({
  store,
  verifyPlaylist: async () => ({ ok: true, format: "m3u", channelCount: 7, groupCount: 2, sampleNames: ["A"], contentHash: "c1" }),
  notifier: { announce: async () => {}, refresh: async () => {}, announceRequest: async () => {}, refreshRequest: async () => {} },
  adminEmails: ["chintk111999@gmail.com"],
  log: { info() {}, warn() {}, error() {} },
});
const server = createHttpServer({
  service,
  verifyToken: createFirebaseAuthVerifier(getAdminAuth(adminApp)),
  config: { corsOrigins: [], rateLimitPerMinute: 10_000, trustProxy: false, telegram: { mode: "off" } },
  log: { info() {}, warn() {}, error() {} },
});
await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
globalThis.TSIPTV_TEST_API_BASE = `http://127.0.0.1:${server.address().port}`;

// ---- the site's code ----
globalThis.location = new URL("https://tsiptv-8bdd6.web.app/contributor/");
const { app, auth } = await import("/assets/firebase.js");
const { connectAuthEmulator, GoogleAuthProvider, signInWithCredential, signOut } = await import("firebase/auth");
const { deleteApp } = await import("firebase/app");
connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
const { getBackend } = await import("/assets/contributor-backend.js");
const { encryptContact } = await import("/assets/contact-crypto.js");
const { CONTACT_PUBLIC_KEY, POLICY_VERSIONS } = await import("/assets/contributor-config.js");
const backend = await getBackend();

after(async () => {
  server.close();
  store.close();
  await deleteApp(app);
  rmSync(dir, { recursive: true, force: true });
});

async function as(email) {
  await signOut(auth);
  await signInWithCredential(auth, GoogleAuthProvider.credential(JSON.stringify({ sub: `http-${email.replace(/\W/g, "")}`, email, email_verified: true })));
  return auth.currentUser;
}

const expectCode = async (promise, code) => assert.equal((await promise.then(() => null, (e) => e))?.code, code);

test("the pages drive the own server through the HTTP backend", { timeout: 120_000 }, async () => {
  assert.equal(backend.kind, "http");

  const alice = await as("alice.http@gmail.com");
  assert.equal(await backend.getMyRequest(), null);
  await backend.saveRequest({
    publicName: "Alice Lists",
    about: "I maintain Vietnamese public news channels for my family.",
    links: [],
    contactEnc: await encryptContact(CONTACT_PUBLIC_KEY, { fullName: "Alice", email: alice.email, phone: "+84900000001" }),
    consents: { ...POLICY_VERSIONS },
  });
  assert.equal((await backend.getMyRequest()).status, "PENDING");
  await expectCode(backend.listRequests(), "forbidden");
  await expectCode(backend.submitPlaylist({ name: "x", url: "https://x.example/a.m3u", category: "news", language: "vi", image: { mime: "image/png", data: "" }, declarations: {} }), "not_contributor");

  await as("chintk111999@gmail.com");
  const [request] = await backend.listRequests("PENDING");
  assert.equal(request.publicName, "Alice Lists");
  await backend.decideRequest(request, "APPROVED");
  const [contributor] = await backend.listContributors();
  assert.equal(contributor.status, "ACTIVE");

  await as("alice.http@gmail.com");
  assert.equal((await backend.getMyContributor()).status, "ACTIVE");
  const check = await backend.checkLink("https://lists.example.com/a.m3u");
  assert.equal(check.checked, true);
  assert.equal(check.method, "server");
  const png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
  const id = await backend.submitPlaylist({
    name: "Alice news", url: "https://lists.example.com/a.m3u", description: "", category: "news", language: "vi",
    image: { mime: "image/png", data: png }, declarations: { own_work: true, not_copied: true, rights_ok: true },
  });
  const [mine] = await backend.listMySubmissions();
  assert.equal(mine.id, id);
  assert.equal(mine.verification.channelCount, 7, "the submit page reads verification.channelCount");

  await as("chintk111999@gmail.com");
  const [review] = await backend.listSubmissions("IN_REVIEW");
  assert.equal((await backend.getSubmissionImage(review.id)).mime, "image/png");
  await backend.decideSubmission(review, "approve");

  await signOut(auth);
  const [pub] = await backend.listPublicPlaylists();
  assert.equal(pub.name, "Alice news");
  assert.equal((await backend.getPublicImage(id)).mime, "image/png");

  await as("alice.http@gmail.com");
  await backend.withdrawSubmission((await backend.listMySubmissions())[0]);
  assert.deepEqual(await backend.listPublicPlaylists(), []);

  await as("chintk111999@gmail.com");
  await backend.setContributorStatus(contributor, "SUSPENDED", "test");
  await as("alice.http@gmail.com");
  await expectCode(backend.submitPlaylist({
    name: "Another", url: "https://lists.example.com/b.m3u", description: "", category: "news", language: "vi",
    image: { mime: "image/png", data: png }, declarations: { own_work: true, not_copied: true, rights_ok: true },
  }), "suspended");

  await backend.purgeMyData();
  assert.equal(await backend.getMyRequest(), null);
});
