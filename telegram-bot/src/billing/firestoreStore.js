// Billing store on Firestore through firebase-admin (bypasses the security rules).
//   billing_purchases/{sha256(token)}   server only (catch-all rule denies clients)
//   billing_accounts/{sha256(uid)}      server only
//   users/{uid}/entitlements/current    owner may read, nobody may write (firestore.rules)

/** @param {import("firebase-admin/firestore").Firestore} db */
export function createFirestoreBillingStore(db) {
  const purchaseRef = (hash) => db.collection("billing_purchases").doc(hash);
  const accountRef = (hash) => db.collection("billing_accounts").doc(hash);
  const entitlementRef = (uid) => db.collection("users").doc(uid).collection("entitlements").doc("current");

  // undefined is not a valid Firestore value.
  const clean = (record) => Object.fromEntries(Object.entries(record).filter(([, v]) => v !== undefined));

  return {
    async getPurchase(tokenHash) {
      const snap = await purchaseRef(tokenHash).get();
      return snap.exists ? snap.data() : null;
    },
    async putPurchase(record) {
      await purchaseRef(record.tokenHash).set(clean(record), { merge: true });
    },
    async linkPurchase(record, uid) {
      return db.runTransaction(async (tx) => {
        const ref = purchaseRef(record.tokenHash);
        const snap = await tx.get(ref);
        const current = snap.exists ? snap.data() : null;
        if (current?.uid && current.uid !== uid) return { ok: false, uid: current.uid };
        tx.set(ref, clean({ ...record, uid, linkedAt: current?.linkedAt ?? Date.now() }), { merge: true });
        return { ok: true };
      });
    },
    async listPurchasesByUid(uid) {
      const snap = await db.collection("billing_purchases").where("uid", "==", uid).get();
      return snap.docs.map((d) => d.data());
    },
    async getAccount(accountHash) {
      const snap = await accountRef(accountHash).get();
      return snap.exists ? snap.data() : null;
    },
    async putAccount(accountHash, uid) {
      await accountRef(accountHash).set({ uid, updatedAt: Date.now() }, { merge: true });
    },
    async writeEntitlement(uid, doc) {
      // Date values become Firestore timestamps, which the quota rules compare with request.time.
      await entitlementRef(uid).set(doc);
    },
  };
}
