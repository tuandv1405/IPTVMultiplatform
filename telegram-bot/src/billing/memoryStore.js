// In-memory billing store for tests and dry runs. Same semantics as firestoreStore.js.

const clone = (value) => (value == null ? value : structuredClone(value));

export function createMemoryBillingStore() {
  const purchases = new Map(); // tokenHash -> record
  const accounts = new Map(); // accountHash -> { uid }
  const entitlements = new Map(); // uid -> doc

  return {
    purchases,
    accounts,
    entitlements,

    async getPurchase(tokenHash) {
      return clone(purchases.get(tokenHash) ?? null);
    },
    async putPurchase(record) {
      purchases.set(record.tokenHash, clone({ ...(purchases.get(record.tokenHash) ?? {}), ...record }));
    },
    async linkPurchase(record, uid) {
      const current = purchases.get(record.tokenHash);
      if (current?.uid && current.uid !== uid) return { ok: false, uid: current.uid };
      purchases.set(record.tokenHash, clone({ ...(current ?? {}), ...record, uid }));
      return { ok: true };
    },
    async listPurchasesByUid(uid) {
      return [...purchases.values()].filter((p) => p.uid === uid).map(clone);
    },
    async getAccount(accountHash) {
      return clone(accounts.get(accountHash) ?? null);
    },
    async putAccount(accountHash, uid) {
      accounts.set(accountHash, { uid });
    },
    /** Read and write in one synchronous step (no await in between), like the Firestore transaction. */
    async recomputeEntitlement(uid, compute) {
      const doc = compute([...purchases.values()].filter((p) => p.uid === uid).map(clone));
      entitlements.set(uid, clone(doc));
      return clone(doc);
    },
    async writeEntitlement(uid, doc) {
      entitlements.set(uid, clone(doc));
    },
  };
}
