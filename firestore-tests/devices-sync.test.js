// Tests for the device limit, quota and sync rules in ../firestore.rules
// (docs/prd-tv-cast-and-sync.md, AC-C4, AC-D5, AC-D6). Run: npm test
import { test, before, after, beforeEach } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, runTransaction, setDoc, updateDoc, writeBatch } from "firebase/firestore";

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

const device = (id, extra = {}) => ({ id, platform: "android_phone", name: `Phone ${id}`, appVersion: "tsptv.1.1.0001", createdAt: 1, lastSeen: 1, ...extra });
const metaRef = (db, uid) => doc(db, "users", uid, "meta", "devices");
const deviceRef = (db, uid, id) => doc(db, "users", uid, "devices", id);

/** Registers a device the way FirestoreAccountCloud does: device + meta in one batch. */
function addDevice(db, uid, id, before) {
  const batch = writeBatch(db);
  batch.set(deviceRef(db, uid, id), device(id));
  batch.set(metaRef(db, uid), { ids: [...before, id], updatedAt: 1 });
  return batch.commit();
}

function removeDevice(db, uid, id, before) {
  const batch = writeBatch(db);
  batch.delete(deviceRef(db, uid, id));
  batch.set(metaRef(db, uid), { ids: before.filter((x) => x !== id), updatedAt: 2 });
  return batch.commit();
}

async function seedDevices(uid, ids) {
  await seed(async (db) => {
    for (const id of ids) await setDoc(deviceRef(db, uid, id), device(id));
    await setDoc(metaRef(db, uid), { ids, updatedAt: 1 });
  });
}

// ---- devices ------------------------------------------------------------------

test("a user registers up to 4 devices, the 5th is refused", async () => {
  const db = user("u1");
  const ids = [];
  for (const id of ["d1", "d2", "d3", "d4"]) {
    await assertSucceeds(addDevice(db, "u1", id, ids));
    ids.push(id);
  }
  await assertFails(addDevice(db, "u1", "d5", ids));
});

test("a device document cannot be created without its id in meta/devices", async () => {
  const db = user("u1");
  await assertFails(setDoc(deviceRef(db, "u1", "x"), device("x")));
  await seedDevices("u1", ["d1"]);
  await assertFails(setDoc(deviceRef(db, "u1", "x"), device("x")));
});

test("meta/devices cannot grow without the device, nor by two at once, nor hold duplicates", async () => {
  const db = user("u1");
  await assertFails(setDoc(metaRef(db, "u1"), { ids: ["ghost"], updatedAt: 1 }));
  await seedDevices("u1", ["d1"]);
  const batch = writeBatch(db);
  batch.set(deviceRef(db, "u1", "a"), device("a"));
  batch.set(deviceRef(db, "u1", "b"), device("b"));
  batch.set(metaRef(db, "u1"), { ids: ["d1", "a", "b"], updatedAt: 1 });
  await assertFails(batch.commit());
  await assertFails(setDoc(metaRef(db, "u1"), { ids: ["d1", "d1"], updatedAt: 1 }));
  await assertFails(deleteDoc(metaRef(db, "u1")));
});

test("signing a device out remotely frees the slot", async () => {
  await seedDevices("u1", ["d1", "d2", "d3", "d4"]);
  const db = user("u1");
  // Deleting the document alone (id still listed) is refused, and so is unlisting a live device.
  await assertFails(deleteDoc(deviceRef(db, "u1", "d2")));
  await assertFails(setDoc(metaRef(db, "u1"), { ids: ["d1", "d3", "d4"], updatedAt: 2 }));
  await assertSucceeds(removeDevice(db, "u1", "d2", ["d1", "d2", "d3", "d4"]));
  await assertSucceeds(addDevice(db, "u1", "d5", ["d1", "d3", "d4"]));
});

test("lastSeen updates keep createdAt and need a listed device", async () => {
  await seedDevices("u1", ["d1"]);
  const db = user("u1");
  await assertSucceeds(updateDoc(deviceRef(db, "u1", "d1"), { lastSeen: 99, name: "Renamed" }));
  await assertFails(updateDoc(deviceRef(db, "u1", "d1"), { createdAt: 5 }));
  await assertFails(updateDoc(deviceRef(db, "u1", "d1"), { serial: "HW-123" }));
});

test("a transaction like the app's registers and removes devices", async () => {
  const db = user("u1");
  await assertSucceeds(runTransaction(db, async (tx) => {
    const meta = await tx.get(metaRef(db, "u1"));
    const ids = meta.exists() ? meta.data().ids : [];
    tx.set(deviceRef(db, "u1", "d1"), device("d1"));
    tx.set(metaRef(db, "u1"), { ids: [...ids, "d1"], updatedAt: 1 });
  }));
  await assertSucceeds(runTransaction(db, async (tx) => {
    const meta = await tx.get(metaRef(db, "u1"));
    tx.set(metaRef(db, "u1"), { ids: meta.data().ids.filter((x) => x !== "d1"), updatedAt: 2 });
    tx.delete(deviceRef(db, "u1", "d1"));
  }));
});

test("another user can neither read nor change my devices", async () => {
  await seedDevices("u1", ["d1"]);
  const other = user("u2");
  await assertFails(getDoc(deviceRef(other, "u1", "d1")));
  await assertFails(getDoc(metaRef(other, "u1")));
  await assertFails(removeDevice(other, "u1", "d1", ["d1"]));
  await assertSucceeds(getDoc(deviceRef(user("u1"), "u1", "d1")));
});

// ---- quota ----------------------------------------------------------------------

const today = () => { const t = new Date(); return t.getUTCFullYear() * 10000 + (t.getUTCMonth() + 1) * 100 + t.getUTCDate(); };
const quotaRef = (db, uid) => doc(db, "users", uid, "quota", "daily");
const quota = (extra = {}) => ({ day: today(), sends: 0, sendRewards: 0, syncs: 0, syncRewards: 0, syncAds: 0, networks: [], updatedAt: 1, ...extra });

test("quota counters grow by one at a time and stay under the caps", async () => {
  const db = user("u1");
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ sends: 1, networks: ["abc"] })));
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ sends: 2 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sends: 1 })));            // goes down
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sends: 4, sendRewards: 1 }))); // +2
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ sends: 3 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sends: 4 })));            // over 3 without a reward
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ sends: 3, sendRewards: 1 })));
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ sends: 4, sendRewards: 1 })));
  await assertFails(deleteDoc(quotaRef(db, "u1")));
});

test("quota caps on rewards and syncs, and a fresh document starts small", async () => {
  const db = user("u1");
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sendRewards: 5, sends: 8 })));
  await seed((s) => setDoc(quotaRef(s, "u1"), quota({ sendRewards: 5, sends: 8, syncRewards: 2, syncs: 3 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sendRewards: 6, sends: 8, syncRewards: 2, syncs: 3 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sendRewards: 5, sends: 8, syncRewards: 2, syncs: 4 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ sendRewards: 5, sends: 8, syncRewards: 2, syncs: 3, syncAds: 2 })));
});

test("a new day resets the counters; an implausible day is refused", async () => {
  const db = user("u1");
  await seed((s) => setDoc(quotaRef(s, "u1"), quota({ day: 20000101, sends: 3 })));
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ sends: 1 })));
  await assertFails(setDoc(quotaRef(db, "u1"), quota({ day: 20000102 })));       // back in time
  await assertFails(setDoc(quotaRef(user("u3"), "u3"), quota({ day: 20991231 }))); // far future
});

// ---- sync -----------------------------------------------------------------------

const syncRef = (db, uid) => doc(db, "users", uid, "sync", "current");
const syncDoc = (extra = {}) => ({ v: 1, fromDeviceId: "d1", fromDeviceName: "Phone", createdAt: 5, playlistCount: 1, payload: '{"v":1,"playlists":[]}', ...extra });

function pushSync(db, uid, quotaAfter, docExtra = {}) {
  const batch = writeBatch(db);
  batch.set(quotaRef(db, uid), quotaAfter);
  batch.set(syncRef(db, uid), syncDoc(docExtra));
  return batch.commit();
}

test("a sync push needs a registered device and one more counted sync", async () => {
  await seedDevices("u1", ["d1"]);
  const db = user("u1");
  await assertFails(setDoc(syncRef(db, "u1"), syncDoc()));                       // no quota change
  await assertFails(pushSync(db, "u1", quota({ syncs: 1 }), { fromDeviceId: "not-mine" }));
  await assertSucceeds(pushSync(db, "u1", quota({ syncs: 1 })));
  await assertFails(pushSync(db, "u1", quota({ syncs: 2 })));                     // 2nd needs a reward
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ syncs: 1, syncAds: 1 })));
  await assertSucceeds(setDoc(quotaRef(db, "u1"), quota({ syncs: 1, syncRewards: 1 })));
  await assertSucceeds(pushSync(db, "u1", quota({ syncs: 2, syncRewards: 1 })));
});

test("the sync slot is owner-only and size-capped", async () => {
  await seedDevices("u1", ["d1"]);
  await assertSucceeds(pushSync(user("u1"), "u1", quota({ syncs: 1 })));
  await assertFails(getDoc(syncRef(user("u2"), "u1")));
  await assertSucceeds(getDoc(syncRef(user("u1"), "u1")));
  await seed((s) => setDoc(quotaRef(s, "u1"), quota({ day: 20000101 })));
  await assertFails(pushSync(user("u1"), "u1", quota({ syncs: 1 }), { payload: "x".repeat(262145) }));
});

// ---- regression: free per-user data ---------------------------------------------

test("the owner still writes users/{uid}/deactiveRequest, others cannot", async () => {
  await assertSucceeds(setDoc(doc(user("u1"), "users", "u1", "deactiveRequest", "u1"), { userId: "u1", status: "PENDING" }));
  await assertFails(setDoc(doc(user("u2"), "users", "u1", "deactiveRequest", "u1"), { userId: "u1" }));
  await assertSucceeds(setDoc(doc(user("u1"), "users", "u1"), { any: 1 }));
});
