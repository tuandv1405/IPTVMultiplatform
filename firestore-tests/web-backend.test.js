// End-to-end: the site's real contributor-backend.js (Firestore backend) against
// the Firestore + Auth emulators and the real firestore.rules. Proves that what
// the pages write is exactly what the rules accept. Google sign-in is faked by
// the Auth emulator.
import { test, before, after } from "node:test";
import assert from "node:assert/strict";

globalThis.location = new URL("https://tsiptv-8bdd6.web.app/contributor/");

const { app, auth } = await import("/assets/firebase.js");
const { connectAuthEmulator, GoogleAuthProvider, signInWithCredential, signOut } = await import("firebase/auth");
const { connectFirestoreEmulator, getFirestore } = await import("firebase/firestore");
connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
const [host, port] = process.env.FIRESTORE_EMULATOR_HOST.split(":");
connectFirestoreEmulator(getFirestore(app), host, Number(port));

const { getBackend } = await import("/assets/contributor-backend.js");
const { encryptContact } = await import("/assets/contact-crypto.js");
const { CONTACT_PUBLIC_KEY, POLICY_VERSIONS } = await import("/assets/contributor-config.js");
const backend = await getBackend();
const { deleteApp } = await import("firebase/app");
// Open Firestore/Auth channels would otherwise keep the test process alive.
after(() => deleteApp(app));

const OWNER = "chintk111999@gmail.com";

async function as(email) {
  await signOut(auth);
  const sub = `sub-${email.replace(/\W/g, "")}`;
  await signInWithCredential(auth, GoogleAuthProvider.credential(JSON.stringify({ sub, email, email_verified: true })));
  return auth.currentUser;
}

async function requestPayload(user, overrides = {}) {
  return {
    publicName: "Tuan Lists",
    about: "I maintain a list of Vietnamese public news channels.",
    links: ["https://example.com/sample.m3u"],
    contactEnc: await encryptContact(CONTACT_PUBLIC_KEY, { fullName: "Nguyen Van Tuan", email: user.email, phone: "+84901234567" }),
    consents: { ...POLICY_VERSIONS },
    ...overrides,
  };
}

const playlist = (url = "https://lists.example.com/news.m3u") => ({
  name: "My news list",
  url,
  description: "Public news channels",
  category: "news",
  language: "vi",
  image: { mime: "image/jpeg", data: "x".repeat(300) },
  declarations: { own_work: true, not_copied: true, rights_ok: true },
  policyVersion: POLICY_VERSIONS.policy,
  verification: { ok: null, method: "browser", format: null, channelCount: null, groupCount: null },
});

const expectCode = async (promise, code) => assert.equal((await promise.then(() => null, (e) => e))?.code, code);

before(async () => {
  // Clean slate for each run (the emulator keeps data between exec runs only if exported).
  await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${process.env.GCLOUD_PROJECT}/databases/(default)/documents`, { method: "DELETE" });
});

test("the whole programme through the web backend, as the pages use it", { timeout: 120_000 }, async () => {
  // --- a user applies ---
  const user = await as("alice@gmail.com");
  assert.equal(await backend.getMyRequest(), null);
  assert.equal(await backend.isAdmin(), false);
  await backend.saveRequest(await requestPayload(user));
  const pending = await backend.getMyRequest();
  assert.equal(pending.status, "PENDING");
  assert.equal(typeof pending.createdAt, "number", "timestamps come back as millis");
  assert.ok(!JSON.stringify(pending).includes("+84901234567"), "contact data is stored encrypted");
  await expectCode(backend.saveRequest(await requestPayload(user)), "request_pending");
  await expectCode(backend.listRequests(), "forbidden");
  await expectCode(backend.submitPlaylist(playlist()), "not_contributor");

  // --- the owner rejects, the user applies again, the owner approves ---
  await as(OWNER);
  assert.equal(await backend.isAdmin(), true);
  let [request] = await backend.listRequests("PENDING");
  assert.equal(request.publicName, "Tuan Lists");
  await backend.decideRequest(request, "REJECTED", "Tell us more");

  await as("alice@gmail.com");
  assert.equal((await backend.getMyRequest()).decision.reason, "Tell us more");
  await backend.saveRequest(await requestPayload(auth.currentUser, { about: "More detail about the channels I curate every week." }));
  assert.equal((await backend.getMyRequest()).attempt, 2);

  await as(OWNER);
  [request] = await backend.listRequests("PENDING");
  await backend.decideRequest(request, "APPROVED");

  // --- the contributor uploads ---
  const aliceUser = await as("alice@gmail.com");
  assert.equal((await backend.getMyContributor()).status, "ACTIVE");
  await expectCode(backend.saveRequest(await requestPayload(aliceUser)), "already_contributor");
  const id = await backend.submitPlaylist(playlist());
  const [mine] = await backend.listMySubmissions();
  assert.equal(mine.id, id);
  assert.equal(mine.status, "IN_REVIEW");

  // --- another contributor cannot take the same link ---
  const bob = await as("bob@gmail.com");
  await backend.saveRequest(await requestPayload(bob, { publicName: "Bob" }));
  await as(OWNER);
  await backend.decideRequest((await backend.listRequests("PENDING"))[0], "APPROVED");
  await as("bob@gmail.com");
  await expectCode(backend.submitPlaylist(playlist()), "link_already_submitted");
  assert.deepEqual(await backend.listMySubmissions(), [], "bob sees none of alice's playlists");

  // --- the owner approves: it becomes public ---
  await as(OWNER);
  const [review] = await backend.listSubmissions("IN_REVIEW");
  assert.ok((await backend.getSubmissionImage(review.id)).data);
  await backend.decideSubmission(review, "approve");
  await signOut(auth);
  const [pub] = await backend.listPublicPlaylists();
  assert.equal(pub.name, "My news list");
  assert.equal(pub.publicName, "Tuan Lists");
  assert.ok((await backend.getPublicImage(id)).data, "image is public");

  // --- alice withdraws: it leaves the directory and the link is free again ---
  await as("alice@gmail.com");
  const [approved] = await backend.listMySubmissions();
  await backend.withdrawSubmission(approved);
  await signOut(auth);
  assert.deepEqual(await backend.listPublicPlaylists(), []);
  await as("bob@gmail.com");
  const bobId = await backend.submitPlaylist(playlist());

  // --- rejection as copied keeps the link locked; takedown; suspension ---
  await as(OWNER);
  const bobs = (await backend.listSubmissions("IN_REVIEW")).find((s) => s.id === bobId);
  await backend.decideSubmission(bobs, "reject", { code: "copyright", note: "copied from alice" });
  await as("alice@gmail.com");
  await expectCode(backend.submitPlaylist(playlist()), "link_already_submitted");
  const second = await backend.submitPlaylist(playlist("https://lists.example.com/sports.m3u"));

  await as(OWNER);
  const sports = (await backend.listSubmissions("IN_REVIEW")).find((s) => s.id === second);
  await backend.decideSubmission(sports, "approve");
  await backend.decideSubmission({ ...sports, status: "APPROVED" }, "takedown", { note: "DMCA" });
  assert.deepEqual(await backend.listPublicPlaylists(), []);
  const alice = (await backend.listContributors()).find((c) => c.publicName === "Tuan Lists");
  await backend.setContributorStatus(alice, "SUSPENDED", "stolen lists");

  await as("alice@gmail.com");
  await expectCode(backend.submitPlaylist(playlist("https://lists.example.com/kids.m3u")), "suspended");

  // --- deleting the account removes the request (encrypted contact data) ---
  await backend.purgeMyData();
  assert.equal(await backend.getMyRequest(), null);
});
