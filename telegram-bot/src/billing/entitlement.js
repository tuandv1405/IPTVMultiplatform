// Pure rules: Google Play SubscriptionPurchaseV2 -> purchase record -> the user's entitlement
// document (docs/prd-subscriptions.md §2, §3.4, §5.3). No I/O here.
import { createHash } from "node:crypto";

export const DEFAULT_PRODUCT_PLANS = Object.freeze({ tsiptv_noads: "no_ads", tsiptv_unlimited: "unlimited" });

const PLAN_RANK = { free: 0, no_ads: 1, unlimited: 2 };

/** States that keep access while the expiry time is in the future (§3.4). */
export const ACTIVE_STATES = new Set(["ACTIVE", "IN_GRACE_PERIOD", "CANCELED"]);

export const sha256Hex = (value) => createHash("sha256").update(String(value), "utf8").digest("hex");

/** The account id the app passes as `obfuscatedAccountId`: SHA-256 hex of the Firebase uid. */
export const accountHash = (uid) => sha256Hex(uid);

/** "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> "IN_GRACE_PERIOD". */
export function shortState(state) {
  const s = String(state || "SUBSCRIPTION_STATE_UNSPECIFIED").replace(/^SUBSCRIPTION_STATE_/, "");
  return s === "UNSPECIFIED" ? "NONE" : s;
}

const toMs = (rfc3339) => {
  const ms = rfc3339 ? Date.parse(rfc3339) : NaN;
  return Number.isFinite(ms) ? ms : null;
};

/**
 * The line item of a known product. A subscription purchase has one line item per product; the app
 * sells one product per purchase, so the first known one is used.
 */
export function pickLineItem(v2, productPlans = DEFAULT_PRODUCT_PLANS, productId = null) {
  const items = Array.isArray(v2?.lineItems) ? v2.lineItems : [];
  if (productId) return items.find((item) => item?.productId === productId) ?? null;
  return items.find((item) => productPlans[item?.productId]) ?? null;
}

/**
 * Maps a SubscriptionPurchaseV2 to the stored purchase record (without uid / linking fields).
 * @returns {null | object} null when no line item is one of our products
 */
export function purchaseFromV2(v2, { purchaseToken, productPlans = DEFAULT_PRODUCT_PLANS, productId = null, nowMs = Date.now() }) {
  const item = pickLineItem(v2, productPlans, productId);
  if (!item || !productPlans[item.productId]) return null;
  return {
    tokenHash: sha256Hex(purchaseToken),
    purchaseToken,
    productId: item.productId,
    basePlanId: item.offerDetails?.basePlanId ?? null,
    plan: productPlans[item.productId],
    state: shortState(v2.subscriptionState),
    expiresAt: toMs(item.expiryTime),
    autoRenewing: item.autoRenewingPlan?.autoRenewEnabled === true,
    acknowledged: v2.acknowledgementState === "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    accountHash: v2.externalAccountIdentifiers?.obfuscatedExternalAccountId ?? null,
    linkedPurchaseTokenHash: v2.linkedPurchaseToken ? sha256Hex(v2.linkedPurchaseToken) : null,
    testPurchase: Boolean(v2.testPurchase),
    revoked: false,
    updatedAt: nowMs,
  };
}

/** The purchase gives access now: an active state, not revoked, expiry in the future. */
export function isActive(purchase, nowMs) {
  return Boolean(purchase) &&
    !purchase.revoked &&
    ACTIVE_STATES.has(purchase.state) &&
    typeof purchase.expiresAt === "number" &&
    purchase.expiresAt > nowMs;
}

/**
 * The entitlement document (`users/{uid}/entitlements/current`) from all purchases linked to a user:
 * the highest active plan (unlimited > no_ads > free); for that plan the purchase that runs longest.
 * With nothing active the plan is `free`, `active: false`, and the fields describe the most recently
 * updated purchase (so the app can say "payment on hold" or "paused").
 */
export function computeEntitlement(purchases, nowMs = Date.now()) {
  const list = (purchases || []).filter(Boolean);
  const active = list.filter((p) => isActive(p, nowMs));
  let winner = null;
  for (const p of active) {
    if (!winner) winner = p;
    else {
      const rank = PLAN_RANK[p.plan] ?? 0;
      const best = PLAN_RANK[winner.plan] ?? 0;
      if (rank > best || (rank === best && p.expiresAt > winner.expiresAt)) winner = p;
    }
  }
  const updatedAt = new Date(nowMs);
  if (winner) {
    return {
      v: 1,
      plan: winner.plan,
      active: true,
      state: winner.state,
      productId: winner.productId,
      basePlanId: winner.basePlanId ?? null,
      expiresAt: new Date(winner.expiresAt),
      autoRenewing: Boolean(winner.autoRenewing),
      source: "google_play",
      updatedAt,
    };
  }
  const latest = list.reduce((a, b) => (!a || (b.updatedAt ?? 0) > (a.updatedAt ?? 0) ? b : a), null);
  return {
    v: 1,
    plan: "free",
    active: false,
    state: latest ? (latest.revoked ? "REVOKED" : latest.state) : "NONE",
    productId: latest?.productId ?? null,
    basePlanId: latest?.basePlanId ?? null,
    expiresAt: typeof latest?.expiresAt === "number" ? new Date(latest.expiresAt) : null,
    autoRenewing: false,
    source: "google_play",
    updatedAt,
  };
}
