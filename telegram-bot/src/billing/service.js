// Billing service: verifies Google Play subscription purchases, links them to Firebase users and
// keeps users/{uid}/entitlements/current up to date (docs/prd-subscriptions.md §5.3, §7).
//
// Store interface (see memoryStore.js / firestoreStore.js):
//   getPurchase(tokenHash) -> record | null
//   putPurchase(record)                       upsert by record.tokenHash
//   linkPurchase(record, uid) -> { ok: true } | { ok: false, uid }   atomic first-claim-wins
//   listPurchasesByUid(uid) -> record[]
//   getAccount(accountHash) -> { uid } | null
//   putAccount(accountHash, uid)
//   writeEntitlement(uid, doc)
import { DEFAULT_PRODUCT_PLANS, accountHash, computeEntitlement, purchaseFromV2, sha256Hex } from "./entitlement.js";

export class BillingError extends Error {
  constructor(status, code, message = code) {
    super(message);
    this.name = "BillingError";
    this.status = status;
    this.code = code;
  }
}

// Play purchase tokens are long URL-safe strings (letters, digits, '.', '_', '-').
const TOKEN_RE = /^[A-Za-z0-9._-]{16,4096}$/;
const UID_RE = /^[A-Za-z0-9_-]{1,128}$/;

/** RTDN notificationType values (https://developer.android.com/google/play/billing/rtdn-reference). */
export const NotificationType = Object.freeze({
  RECOVERED: 1, RENEWED: 2, CANCELED: 3, PURCHASED: 4, ON_HOLD: 5, IN_GRACE_PERIOD: 6, RESTARTED: 7,
  PRICE_CHANGE_CONFIRMED: 8, DEFERRED: 9, PAUSED: 10, PAUSE_SCHEDULE_CHANGED: 11, REVOKED: 12, EXPIRED: 13,
  PENDING_PURCHASE_CANCELED: 20, PRICE_CHANGE_UPDATED: 19,
});

/**
 * @param {object} deps
 * @param {{ getSubscriptionV2(pkg: string, token: string): Promise<object>, acknowledge(pkg: string, productId: string, token: string): Promise<object> }} deps.playApi
 * @param {object} deps.store
 * @param {string} deps.packageName
 * @param {Record<string, string>} [deps.productPlans] productId -> plan
 * @param {() => number} [deps.now]
 * @param {object} [deps.log]
 */
export function createBillingService({ playApi, store, packageName, productPlans = DEFAULT_PRODUCT_PLANS, now = Date.now, log = console }) {
  if (!packageName) throw new Error("packageName is required");

  async function fetchV2(purchaseToken) {
    try {
      return await playApi.getSubscriptionV2(packageName, purchaseToken);
    } catch (error) {
      if (error?.notFound) return null;
      if (error?.transient === false) throw new BillingError(502, "play_rejected", error.message);
      throw new BillingError(503, "play_unavailable", error?.message ?? "play api error");
    }
  }

  /**
   * Rebuilds users/{uid}/entitlements/current from the uid's purchases. Stores that support it
   * (firestoreStore.js) read the purchases and write the document in one transaction, so a verify
   * and an RTDN for the same user cannot leave a stale entitlement behind (S4).
   */
  async function recompute(uid) {
    if (typeof store.recomputeEntitlement === "function") {
      return store.recomputeEntitlement(uid, (purchases) => computeEntitlement(purchases, now()));
    }
    const purchases = await store.listPurchasesByUid(uid);
    const doc = computeEntitlement(purchases, now());
    await store.writeEntitlement(uid, doc);
    return doc;
  }

  /** A revoked / voided purchase stays revoked, whatever a later Play re-read says (S1). */
  const merged = (existing, record, extra = {}) => ({
    ...existing,
    ...record,
    ...extra,
    revoked: Boolean(existing?.revoked || record.revoked),
  });

  /**
   * An upgrade / downgrade / re-subscribe carries the token it replaces. Mark the old purchase and
   * refresh its state from Play (an immediate replacement expires it; a deferred one keeps it active
   * until renewal, so it is not simply dropped). When the old purchase belonged to another uid than
   * [uid], that user's entitlement is recomputed too (S2).
   */
  async function handleReplaced(record, uid) {
    if (!record.linkedPurchaseTokenHash) return;
    const old = await store.getPurchase(record.linkedPurchaseTokenHash);
    if (!old) return;
    try {
      await refreshReplaced(old, record);
    } finally {
      if (old.uid && old.uid !== uid) await recompute(old.uid);
    }
  }

  async function refreshReplaced(old, record) {
    let refreshed = old;
    if (old.purchaseToken) {
      try {
        const v2 = await playApi.getSubscriptionV2(packageName, old.purchaseToken);
        const mapped = v2 && purchaseFromV2(v2, { purchaseToken: old.purchaseToken, productPlans, productId: old.productId, nowMs: now() });
        if (mapped) refreshed = { ...old, state: mapped.state, expiresAt: mapped.expiresAt, autoRenewing: mapped.autoRenewing, updatedAt: now() };
      } catch (error) {
        if (error?.notFound) refreshed = { ...old, state: "EXPIRED", updatedAt: now() };
        else log.warn?.("billing: could not refresh a replaced purchase", { error: error?.message });
      }
    }
    await store.putPurchase({ ...refreshed, replaced: true, replacedBy: record.tokenHash });
  }

  async function acknowledgeIfNeeded(record) {
    if (record.acknowledged || !["ACTIVE", "IN_GRACE_PERIOD"].includes(record.state)) return record;
    try {
      await playApi.acknowledge(packageName, record.productId, record.purchaseToken);
      return { ...record, acknowledged: true };
    } catch (error) {
      // The app acknowledges too; Play only refunds after 3 days without one.
      log.warn?.("billing: acknowledge failed", { productId: record.productId, error: error?.message });
      return record;
    }
  }

  return {
    /**
     * A signed-in app reports a purchase. Verifies it with Google Play, links it to [uid]
     * (first claim wins), acknowledges it and returns the new entitlement document.
     */
    async verifyPurchase({ uid, productId, purchaseToken }) {
      if (typeof uid !== "string" || !UID_RE.test(uid)) throw new BillingError(401, "unauthenticated");
      if (typeof productId !== "string" || !productPlans[productId]) throw new BillingError(400, "unknown_product");
      if (typeof purchaseToken !== "string" || !TOKEN_RE.test(purchaseToken)) throw new BillingError(400, "bad_token");

      const v2 = await fetchV2(purchaseToken);
      if (!v2) throw new BillingError(404, "purchase_not_found");
      let record = purchaseFromV2(v2, { purchaseToken, productPlans, productId, nowMs: now() });
      if (!record) throw new BillingError(400, "product_mismatch");

      const hash = accountHash(uid);
      if (record.accountHash && record.accountHash !== hash) throw new BillingError(403, "account_mismatch");

      const existing = await store.getPurchase(record.tokenHash);
      if (existing?.uid && existing.uid !== uid) throw new BillingError(409, "linked_to_other_account");

      record = await acknowledgeIfNeeded(record);
      const linked = await store.linkPurchase(merged(existing, record, { replaced: existing?.replaced ?? false }), uid);
      if (!linked.ok) throw new BillingError(409, "linked_to_other_account");
      await store.putAccount(hash, uid);
      await handleReplaced(record, uid);
      return recompute(uid);
    },

    /**
     * One decoded RTDN message (the JSON inside the Pub/Sub message's base64 `data`).
     * Returns a short outcome for logs and tests. Throws BillingError(503) on transient failures so
     * Pub/Sub retries.
     */
    async handleRtdn(message) {
      if (!message || typeof message !== "object") throw new BillingError(400, "bad_message");
      if (message.packageName !== packageName) return { outcome: "ignored_package" };
      if (message.testNotification) {
        log.info?.("billing: RTDN test notification received");
        return { outcome: "test" };
      }

      if (message.voidedPurchaseNotification) {
        const token = message.voidedPurchaseNotification.purchaseToken;
        if (typeof token !== "string" || !token) return { outcome: "ignored_bad_voided" };
        const existing = await store.getPurchase(sha256Hex(token));
        if (!existing) return { outcome: "voided_unknown" };
        await store.putPurchase({ ...existing, revoked: true, updatedAt: now() });
        if (existing.uid) await recompute(existing.uid);
        return { outcome: "voided", uid: existing.uid ?? null };
      }

      const n = message.subscriptionNotification;
      if (!n) return { outcome: "ignored_type" };
      if (typeof n.purchaseToken !== "string" || !n.purchaseToken) throw new BillingError(400, "bad_message");
      const tokenHash = sha256Hex(n.purchaseToken);
      const existing = await store.getPurchase(tokenHash);

      const v2 = await fetchV2(n.purchaseToken);
      if (!v2) {
        // Unknown to Play (e.g. long expired): end it locally if we know it.
        if (!existing) return { outcome: "not_found" };
        await store.putPurchase({ ...existing, state: "EXPIRED", updatedAt: now() });
        if (existing.uid) await recompute(existing.uid);
        return { outcome: "expired_not_found", uid: existing.uid ?? null };
      }
      let record = purchaseFromV2(v2, { purchaseToken: n.purchaseToken, productPlans, productId: n.subscriptionId || null, nowMs: now() })
        ?? purchaseFromV2(v2, { purchaseToken: n.purchaseToken, productPlans, nowMs: now() });
      if (!record) return { outcome: "ignored_product" };
      if (n.notificationType === NotificationType.REVOKED) record = { ...record, revoked: true };

      let uid = existing?.uid ?? null;
      if (!uid && record.accountHash) uid = (await store.getAccount(record.accountHash))?.uid ?? null;
      // An upgrade / downgrade made without an account id inherits the replaced purchase's account (S2).
      if (!uid && record.linkedPurchaseTokenHash) uid = (await store.getPurchase(record.linkedPurchaseTokenHash))?.uid ?? null;

      if (!uid) {
        // Kept unlinked; the app links it when a signed-in user verifies it (first claim wins).
        await store.putPurchase(merged(existing, record, { uid: null }));
        log.info?.("billing: RTDN for a purchase not linked to any account", { type: n.notificationType, productId: record.productId });
        return { outcome: "unlinked" };
      }
      if (record.accountHash && existing?.uid && record.accountHash !== accountHash(existing.uid)) {
        log.warn?.("billing: RTDN account hash differs from the linked account; keeping the link", { productId: record.productId });
      }
      await store.putPurchase(merged(existing, record, { uid, replaced: existing?.replaced ?? false }));
      await handleReplaced(record, uid);
      await recompute(uid);
      return { outcome: "updated", uid, state: record.state };
    },

    recompute,
  };
}
