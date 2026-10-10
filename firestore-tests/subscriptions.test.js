// Tests for the subscription rules in ../firestore.rules (docs/prd-subscriptions.md §6,
// AC-SUB7): the entitlement document is read-only for clients, and only a verified,
// active, unexpired Unlimited plan lifts the daily send / sync caps. Run: npm test
import { test, before, after, beforeEach } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { Timestamp, deleteDoc, doc, getDoc, setDoc, updateDoc, writeBatch } from "firebase/firestore";

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: process.env.GCLOUD_PROJECT ?? "tsiptv-8bdd6",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const user = (uid) => env.authenticatedContext(uid, { email: `${uid}@example.com`, firebase: { sign_in_provider: "password" } }).firestore();
const seed = (fn) => env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));

const DAY_MS = 24 * 3600 * 1000;
const entRef = (db, uid) => doc(db, "users", uid, "entitlements", "current");
const entitlement = (extra = {}) => ({
  v: 1,
  plan: "unlimited",
  active: true,
  state: "ACTIVE",
  productId: "tsiptv_unlimited",
  basePlanId: "monthly",
  expiresAt: Timestamp.fromMillis(Date.now() + 30 * DAY_MS),
  autoRenewing: true,
  source: "google_play",
  updatedAt: Timestamp.now(),
  ...extra,
});
const seedEntitlement = (uid, extra) => seed((s) => setDoc(entRef(s, uid), entitlement(extra)));

// Same shapes as devices-sync.test.js.
const device = (id) => ({ id, platform: "android_phone", name: `Phone ${id}`, appVersion: "tsptv.1.1.0001", createdAt: 1, lastSeen: 1 });
const metaRef = (db, uid) => doc(db, "users", uid, "meta", "devices");
const deviceRef = (db, uid, id) => doc(db, "users", uid, "devices", id);
async function seedDevices(uid, ids) {
  await seed(async (db) => {
    for (const id of ids) await setDoc(deviceRef(db, uid, id), device(id));
    await setDoc(metaRef(db, uid), { ids, updatedAt: 1 });
  });
}

const today = () => { const t = new Date(); return t.getUTCFullYear() * 10000 + (t.getUTCMonth() + 1) * 100 + t.getUTCDate(); };
const quotaRef = (db, uid) => doc(db, "users", uid, "quota", "daily");
const quota = (extra = {}) => ({ day: today(), sends: 0, sendRewards: 0, syncs: 0, syncRewards: 0, syncAds: 0, networks: [], updatedAt: 1, ...extra });
const syncRef = (db, uid) => doc(db, "users", uid, "sync", "current");
const syncDoc = (extra = {}) => ({ v: 1, fromDeviceId: "d1", fromDeviceName: "Phone", createdAt: 5, playlistCount: 1, payload: '{"v":1,"playlists":[]}', ...extra });
function pushSync(db, uid, quotaAfter) {
  const batch = writeBatch(db);
  batch.set(quotaRef(db, uid), quotaAfter);
  batch.set(syncRef(db, uid), syncDoc());
  return batch.commit();
}

// ---- the entitlement document ------------------------------------------------------

test("a client can neither create, update nor delete its own entitlement", async () => {
  const db = user("u1");
  await assertFails(setDoc(entRef(db, "u1"), entitlement()));
  await assertFails(setDoc(doc(db, "users", "u1", "entitlements", "other"), entitlement()));
  await seedEntitlement("u1", { plan: "no_ads", productId: "tsiptv_noads" });
  await assertFails(updateDoc(entRef(db, "u1"), { plan: "unlimited" }));
  await assertFails(setDoc(entRef(db, "u1"), entitlement()));
  await assertFails(deleteDoc(entRef(db, "u1")));
});

test("the owner reads the entitlement, another user cannot", async () => {
  await seedEntitlement("u1");
  await assertSucceeds(getDoc(entRef(user("u1"), "u1")));
  await assertFails(getDoc(entRef(user("u2"), "u1")));
  await assertFails(setDoc(entRef(user("u2"), "u1"), entitlement()));
});

// ---- quotas with a verified Unlimited plan --------------------------------------------

test("verified Unlimited sends past 3 without rewards, up to 200 a day", async () => {
  await seedEntitlement("u1");
  const db = user("u1");
  await seed((s) => setDoc(quotaRef(s, "u1"), quota({ sends: 3 })));
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ sends: 4 })));
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ sends: 5 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sends: 7 })));       // still +1 per write
  await seed((s) => setDoc(quotaRef(s, "u1"), quota({ sends: 199 })));
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ sends: 200 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sends: 201 })));     // fair-use cap
  await assertFails(deleteDoc(quotaRef(db, "u1")));                         // still no reset
});

test("verified Unlimited pushes a 2nd sync without rewards, up to 50 a day", async () => {
  await seedEntitlement("u1");
  await seedDevices("u1", ["d1"]);
  const db = user("u1");
  await assertSucceeds(pushSync(db, "u1", quota({ syncs: 1 })));
  await assertSucceeds(pushSync(db, "u1", quota({ syncs: 2 })));
  await assertSucceeds(pushSync(db, "u1", quota({ syncs: 3 })));
  await seed((s) => setDoc(quotaRef(s, "u1"), quota({ syncs: 49 })));
  await assertSucceeds(pushSync(db, "u1", quota({ syncs: 50 })));
  await assertFails(pushSync(db, "u1", quota({ syncs: 51 })));              // fair-use cap
});

test("reward caps still apply to Unlimited", async () => {
  await seedEntitlement("u1");
  const db = user("u1");
  await seed((s) => setDoc(quotaRef(s, "u1"), quota({ sendRewards: 5, syncRewards: 2 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sendRewards: 6, syncRewards: 2 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sendRewards: 5, syncRewards: 3 })));
});

// ---- plans that do not lift the caps -------------------------------------------------

for (const [name, extra] of [
  ["an expired Unlimited", { expiresAt: Timestamp.fromMillis(Date.now() - 60 * 1000), state: "EXPIRED" }],
  ["an inactive Unlimited (on hold)", { active: false, state: "ON_HOLD" }],
  ["a no-ads plan", { plan: "no_ads", productId: "tsiptv_noads" }],
  ["an Unlimited without expiresAt", { expiresAt: null }],
  ["an Unlimited whose expiresAt is a number", { expiresAt: Date.now() + DAY_MS }],
]) {
  test(`${name} keeps the free caps (3 + rewards, 1 + rewards)`, async () => {
    await seedEntitlement("u1", extra);
    await seedDevices("u1", ["d1"]);
    const db = user("u1");
    await seed((s) => setDoc(quotaRef(s, "u1"), quota({ sends: 3 })));
    await assertFails(setDoc(quotaRef(db, "u1"), quota({ sends: 4 })));
    await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ sends: 4, sendRewards: 1 })));
    await assertSucceeds(pushSync(db, "u1", quota({ sends: 4, sendRewards: 1, syncs: 1 })));
    await assertFails(pushSync(db, "u1", quota({ sends: 4, sendRewards: 1, syncs: 2 })));
  });
}

test("without any entitlement document the free caps hold", async () => {
  const db = user("u1");
  await seed((s) => setDoc(quotaRef(s, "u1"), quota({ sends: 3 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sends: 4 })));
});

test("another user's Unlimited does not lift my caps", async () => {
  await seedEntitlement("u2");
  const db = user("u1");
  await seed((s) => setDoc(quotaRef(s, "u1"), quota({ sends: 3 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sends: 4 })));
});
