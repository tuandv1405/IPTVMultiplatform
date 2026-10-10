// Billing module (src/billing/): Play verification, RTDN, linking, entitlement, OIDC, HTTP.
// Pure node:test with mocks; needs no node_modules and no network. Run: node --test test/billing.test.js
import { test } from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, createSign, createVerify } from "node:crypto";
import { PassThrough } from "node:stream";
import { accountHash, computeEntitlement, purchaseFromV2, sha256Hex } from "../src/billing/entitlement.js";
import { createBillingService, BillingError, NotificationType } from "../src/billing/service.js";
import { createMemoryBillingStore } from "../src/billing/memoryStore.js";
import { clientAddress, createBillingRoutes } from "../src/billing/http.js";
import { createPushTokenVerifier } from "../src/billing/oidc.js";
import { createGoogleAuth, signAssertion } from "../src/billing/googleAuth.js";
import { createPlayApi, PlayApiError } from "../src/billing/playApi.js";

const PKG = "tss.t.tsiptv";
const NOW = Date.parse("2026-10-10T12:00:00Z");
const DAY = 24 * 3600 * 1000;
const TOKEN_A = "tokenAAAAAAAAAAAAAAAAAAAA.aaaa-bbbb_cccc";
const TOKEN_B = "tokenBBBBBBBBBBBBBBBBBBBB.bbbb-cccc_dddd";
const silent = { info() {}, warn() {}, error() {} };

function v2({ productId = "tsiptv_unlimited", state = "SUBSCRIPTION_STATE_ACTIVE", expiry = NOW + 30 * DAY, ack = "ACKNOWLEDGEMENT_STATE_PENDING", account = null, linked = null, basePlanId = "monthly", autoRenew = true } = {}) {
  return {
    kind: "androidpublisher#subscriptionPurchaseV2",
    subscriptionState: state,
    acknowledgementState: ack,
    lineItems: [{ productId, expiryTime: new Date(expiry).toISOString(), autoRenewingPlan: { autoRenewEnabled: autoRenew }, offerDetails: { basePlanId } }],
    ...(account ? { externalAccountIdentifiers: { obfuscatedExternalAccountId: account } } : {}),
    ...(linked ? { linkedPurchaseToken: linked } : {}),
  };
}

function fakePlay(byToken) {
  const calls = { get: [], ack: [] };
  return {
    calls,
    async getSubscriptionV2(pkg, token) {
      calls.get.push({ pkg, token });
      const value = typeof byToken === "function" ? byToken(token) : byToken[token];
      if (value instanceof Error) throw value;
      if (!value) throw new PlayApiError("not found", 404);
      return value;
    },
    async acknowledge(pkg, productId, token) {
      calls.ack.push({ pkg, productId, token });
      return {};
    },
  };
}

function setup(byToken) {
  const store = createMemoryBillingStore();
  const playApi = fakePlay(byToken);
  const service = createBillingService({ playApi, store, packageName: PKG, now: () => NOW, log: silent });
  return { store, playApi, service };
}

const rtdn = (token, type = NotificationType.RENEWED, subscriptionId = "tsiptv_unlimited", pkg = PKG) => ({
  version: "1.0",
  packageName: pkg,
  eventTimeMillis: String(NOW),
  subscriptionNotification: { version: "1.0", notificationType: type, purchaseToken: token, subscriptionId },
});

// ---- entitlement (pure) ---------------------------------------------------------------

test("purchaseFromV2 maps state, expiry, plan, account and linked token", () => {
  const p = purchaseFromV2(v2({ state: "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", account: "abc", linked: "old" }), { purchaseToken: TOKEN_A, nowMs: NOW });
  assert.equal(p.plan, "unlimited");
  assert.equal(p.state, "IN_GRACE_PERIOD");
  assert.equal(p.expiresAt, NOW + 30 * DAY);
  assert.equal(p.accountHash, "abc");
  assert.equal(p.linkedPurchaseTokenHash, sha256Hex("old"));
  assert.equal(p.tokenHash, sha256Hex(TOKEN_A));
  assert.equal(p.acknowledged, false);
  assert.equal(purchaseFromV2(v2({ productId: "other" }), { purchaseToken: TOKEN_A }), null);
});

test("computeEntitlement: highest active plan wins; inactive states and past expiry do not count", () => {
  const mk = (plan, state, expiresAt, extra = {}) => ({ plan, state, expiresAt, productId: plan === "unlimited" ? "tsiptv_unlimited" : "tsiptv_noads", basePlanId: "monthly", autoRenewing: true, updatedAt: NOW, ...extra });
  let e = computeEntitlement([mk("no_ads", "ACTIVE", NOW + DAY), mk("unlimited", "CANCELED", NOW + 2 * DAY)], NOW);
  assert.equal(e.plan, "unlimited");
  assert.equal(e.active, true);
  assert.equal(e.state, "CANCELED");
  assert.ok(e.expiresAt instanceof Date && e.expiresAt.getTime() === NOW + 2 * DAY);
  assert.equal(e.v, 1);
  assert.equal(e.source, "google_play");
  e = computeEntitlement([mk("unlimited", "CANCELED", NOW - 1), mk("no_ads", "IN_GRACE_PERIOD", NOW + DAY)], NOW);
  assert.equal(e.plan, "no_ads");
  for (const state of ["ON_HOLD", "PAUSED", "EXPIRED", "PENDING", "PENDING_PURCHASE_CANCELED"]) {
    e = computeEntitlement([mk("unlimited", state, NOW + DAY)], NOW);
    assert.equal(e.plan, "free", state);
    assert.equal(e.active, false);
    assert.equal(e.state, state);
  }
  e = computeEntitlement([mk("unlimited", "ACTIVE", NOW + DAY, { revoked: true })], NOW);
  assert.deepEqual([e.plan, e.state], ["free", "REVOKED"]);
  e = computeEntitlement([], NOW);
  assert.deepEqual([e.plan, e.active, e.state, e.expiresAt], ["free", false, "NONE", null]);
  // Same plan twice: the one that runs longest.
  e = computeEntitlement([mk("unlimited", "ACTIVE", NOW + DAY, { basePlanId: "monthly" }), mk("unlimited", "ACTIVE", NOW + 300 * DAY, { basePlanId: "yearly" })], NOW);
  assert.equal(e.basePlanId, "yearly");
});

test("the document has exactly the PRD §5.3 fields", () => {
  const e = computeEntitlement([], NOW);
  assert.deepEqual(Object.keys(e).sort(), ["active", "autoRenewing", "basePlanId", "expiresAt", "plan", "productId", "source", "state", "updatedAt", "v"]);
  assert.ok(e.updatedAt instanceof Date);
});

// ---- verifyPurchase ----------------------------------------------------------------------

test("verify: happy path links, acknowledges and writes the entitlement", async () => {
  const { store, playApi, service } = setup({ [TOKEN_A]: v2({ account: accountHash("u1") }) });
  const doc = await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });
  assert.equal(doc.plan, "unlimited");
  assert.equal(doc.active, true);
  assert.deepEqual(playApi.calls.get[0], { pkg: PKG, token: TOKEN_A });
  assert.deepEqual(playApi.calls.ack, [{ pkg: PKG, productId: "tsiptv_unlimited", token: TOKEN_A }]);
  assert.equal(store.entitlements.get("u1").plan, "unlimited");
  const stored = store.purchases.get(sha256Hex(TOKEN_A));
  assert.equal(stored.uid, "u1");
  assert.equal(stored.acknowledged, true);
  assert.deepEqual(store.accounts.get(accountHash("u1")), { uid: "u1" });
});

test("verify: already acknowledged or not active is not acknowledged again", async () => {
  const { playApi, service } = setup({
    [TOKEN_A]: v2({ ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" }),
    [TOKEN_B]: v2({ state: "SUBSCRIPTION_STATE_PENDING" }),
  });
  await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });
  const doc = await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_B });
  assert.equal(playApi.calls.ack.length, 0);
  assert.equal(doc.plan, "unlimited"); // TOKEN_A still active; the pending one adds nothing
});

test("verify: input validation", async () => {
  const { service } = setup({});
  await assert.rejects(service.verifyPurchase({ uid: "u1", productId: "nope", purchaseToken: TOKEN_A }), { status: 400, code: "unknown_product" });
  await assert.rejects(service.verifyPurchase({ uid: "u1", productId: "tsiptv_noads", purchaseToken: "short" }), { status: 400, code: "bad_token" });
  await assert.rejects(service.verifyPurchase({ uid: "u1", productId: "tsiptv_noads", purchaseToken: "x".repeat(20) + " <script>" }), { code: "bad_token" });
  await assert.rejects(service.verifyPurchase({ uid: "", productId: "tsiptv_noads", purchaseToken: TOKEN_A }), { status: 401 });
});

test("verify: unknown token (or another package's token) is 404; product mismatch is 400", async () => {
  const { service } = setup({ [TOKEN_A]: v2({ productId: "tsiptv_noads" }) });
  await assert.rejects(service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_B }), { status: 404, code: "purchase_not_found" });
  await assert.rejects(service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A }), { status: 400, code: "product_mismatch" });
});

test("verify: Play outages are 503 (retry later), Play refusals 502", async () => {
  const { service } = setup({ [TOKEN_A]: new PlayApiError("boom", 503), [TOKEN_B]: new PlayApiError("denied", 401) });
  await assert.rejects(service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A }), { status: 503 });
  await assert.rejects(service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_B }), { status: 502 });
});

test("verify: a purchase made for another account hash is refused (403)", async () => {
  const { store, service } = setup({ [TOKEN_A]: v2({ account: accountHash("u1") }) });
  await assert.rejects(service.verifyPurchase({ uid: "u2", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A }), { status: 403, code: "account_mismatch" });
  assert.equal(store.entitlements.size, 0);
});

test("verify: first claim wins; a token linked to another uid is 409", async () => {
  const { store, service } = setup({ [TOKEN_A]: v2() }); // bought signed out: no account hash
  await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });
  await assert.rejects(service.verifyPurchase({ uid: "u2", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A }), { status: 409, code: "linked_to_other_account" });
  assert.equal(store.entitlements.has("u2"), false);
  // The owner may verify again (restore) without trouble.
  const again = await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });
  assert.equal(again.plan, "unlimited");
});

test("verify: an upgrade marks the replaced purchase and refreshes it from Play", async () => {
  const { store, service } = setup({
    [TOKEN_A]: v2({ productId: "tsiptv_noads", state: "SUBSCRIPTION_STATE_EXPIRED", expiry: NOW - 1 }),
    [TOKEN_B]: v2({ linked: TOKEN_A }),
  });
  await store.linkPurchase({ ...purchaseFromV2(v2({ productId: "tsiptv_noads" }), { purchaseToken: TOKEN_A, nowMs: NOW }) }, "u1");
  const doc = await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_B });
  const old = store.purchases.get(sha256Hex(TOKEN_A));
  assert.equal(old.replaced, true);
  assert.equal(old.replacedBy, sha256Hex(TOKEN_B));
  assert.equal(old.state, "EXPIRED");
  assert.equal(doc.plan, "unlimited");
});

test("verify: a deferred downgrade keeps the higher plan until it ends", async () => {
  const { store, service } = setup({
    [TOKEN_A]: v2({ productId: "tsiptv_unlimited", state: "SUBSCRIPTION_STATE_ACTIVE", expiry: NOW + 5 * DAY, ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" }),
    [TOKEN_B]: v2({ productId: "tsiptv_noads", linked: TOKEN_A, expiry: NOW + 35 * DAY }),
  });
  await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });
  const doc = await service.verifyPurchase({ uid: "u1", productId: "tsiptv_noads", purchaseToken: TOKEN_B });
  assert.equal(doc.plan, "unlimited");
  assert.equal(store.purchases.get(sha256Hex(TOKEN_A)).replaced, true);
});

// ---- RTDN ------------------------------------------------------------------------------------

test("rtdn: test notification and other packages are acknowledged without work", async () => {
  const { playApi, service } = setup({});
  assert.deepEqual(await service.handleRtdn({ packageName: PKG, testNotification: { version: "1.0" } }), { outcome: "test" });
  assert.deepEqual(await service.handleRtdn(rtdn(TOKEN_A, 2, "tsiptv_unlimited", "com.other")), { outcome: "ignored_package" });
  assert.equal(playApi.calls.get.length, 0);
  await assert.rejects(service.handleRtdn(null), BillingError);
});

test("rtdn: renew / cancel / hold / recover / expire update a linked user's entitlement", async () => {
  let current = v2({ ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" });
  const { store, service } = setup(() => current);
  await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });

  current = v2({ state: "SUBSCRIPTION_STATE_ACTIVE", expiry: NOW + 60 * DAY });
  assert.equal((await service.handleRtdn(rtdn(TOKEN_A, NotificationType.RENEWED))).outcome, "updated");
  assert.equal(store.entitlements.get("u1").expiresAt.getTime(), NOW + 60 * DAY);

  current = v2({ state: "SUBSCRIPTION_STATE_CANCELED", expiry: NOW + 60 * DAY, autoRenew: false });
  await service.handleRtdn(rtdn(TOKEN_A, NotificationType.CANCELED));
  assert.deepEqual([store.entitlements.get("u1").plan, store.entitlements.get("u1").state, store.entitlements.get("u1").autoRenewing], ["unlimited", "CANCELED", false]);

  current = v2({ state: "SUBSCRIPTION_STATE_ON_HOLD", expiry: NOW - DAY });
  await service.handleRtdn(rtdn(TOKEN_A, NotificationType.ON_HOLD));
  assert.deepEqual([store.entitlements.get("u1").plan, store.entitlements.get("u1").state], ["free", "ON_HOLD"]);

  current = v2({ state: "SUBSCRIPTION_STATE_ACTIVE", expiry: NOW + 30 * DAY });
  await service.handleRtdn(rtdn(TOKEN_A, NotificationType.RECOVERED));
  assert.equal(store.entitlements.get("u1").plan, "unlimited");

  current = v2({ state: "SUBSCRIPTION_STATE_EXPIRED", expiry: NOW - 1 });
  await service.handleRtdn(rtdn(TOKEN_A, NotificationType.EXPIRED));
  assert.deepEqual([store.entitlements.get("u1").plan, store.entitlements.get("u1").active], ["free", false]);
});

test("rtdn: revoked and voided purchases end the entitlement", async () => {
  const current = v2({ ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" });
  const { store, service } = setup(() => current);
  await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });
  await service.handleRtdn(rtdn(TOKEN_A, NotificationType.REVOKED));
  assert.deepEqual([store.entitlements.get("u1").plan, store.entitlements.get("u1").state], ["free", "REVOKED"]);

  const s2 = setup(() => current);
  await s2.service.verifyPurchase({ uid: "u2", productId: "tsiptv_unlimited", purchaseToken: TOKEN_B });
  const out = await s2.service.handleRtdn({ packageName: PKG, voidedPurchaseNotification: { purchaseToken: TOKEN_B, orderId: "GPA.1", productType: 1, refundType: 1 } });
  assert.equal(out.outcome, "voided");
  assert.equal(s2.store.entitlements.get("u2").plan, "free");
  assert.equal((await s2.service.handleRtdn({ packageName: PKG, voidedPurchaseNotification: { purchaseToken: TOKEN_A } })).outcome, "voided_unknown");
});

test("rtdn: an unverified purchase is linked through its account hash", async () => {
  const { store, service } = setup({ [TOKEN_A]: v2({ account: accountHash("u1") }) });
  await store.putAccount(accountHash("u1"), "u1"); // the app verified an earlier purchase of u1
  const out = await service.handleRtdn(rtdn(TOKEN_A, NotificationType.PURCHASED));
  assert.deepEqual([out.outcome, out.uid], ["updated", "u1"]);
  assert.equal(store.entitlements.get("u1").plan, "unlimited");
});

test("rtdn: an unlinked token is stored unlinked and acked; a later verify claims it", async () => {
  const { store, service } = setup({ [TOKEN_A]: v2() });
  assert.equal((await service.handleRtdn(rtdn(TOKEN_A, NotificationType.PURCHASED))).outcome, "unlinked");
  assert.equal(store.entitlements.size, 0);
  assert.equal(store.purchases.get(sha256Hex(TOKEN_A)).uid, null);
  const doc = await service.verifyPurchase({ uid: "u9", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });
  assert.equal(doc.plan, "unlimited");
});

test("rtdn: transient Play errors throw (Pub/Sub retries); unknown tokens are acked", async () => {
  const { service } = setup({ [TOKEN_A]: new PlayApiError("x", 500) });
  await assert.rejects(service.handleRtdn(rtdn(TOKEN_A)), { status: 503 });
  assert.equal((await service.handleRtdn(rtdn(TOKEN_B))).outcome, "not_found");
});

test("entitlement is the max over all purchases linked to the user", async () => {
  const { store, service } = setup({
    [TOKEN_A]: v2({ productId: "tsiptv_noads", expiry: NOW + 300 * DAY }),
    [TOKEN_B]: v2({ productId: "tsiptv_unlimited", expiry: NOW + 3 * DAY }),
  });
  await service.verifyPurchase({ uid: "u1", productId: "tsiptv_noads", purchaseToken: TOKEN_A });
  const doc = await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_B });
  assert.equal(doc.plan, "unlimited");
  assert.equal(doc.expiresAt.getTime(), NOW + 3 * DAY);
  assert.equal(store.entitlements.get("u1").plan, "unlimited");
});

// ---- OIDC ------------------------------------------------------------------------------------

const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: "jwk" }), kid: "k1", alg: "RS256", use: "sig" };
const PUSH_SA = "rtdn-push@tsiptv-8bdd6.iam.gserviceaccount.com";
const AUD = "https://billing.example.com/billing/rtdn";

function jwt(claims, { kid = "k1", key = privateKey } = {}) {
  const enc = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const unsigned = `${enc({ alg: "RS256", kid, typ: "JWT" })}.${enc(claims)}`;
  return `${unsigned}.${createSign("RSA-SHA256").update(unsigned).sign(key).toString("base64url")}`;
}

const goodClaims = (extra = {}) => ({ iss: "https://accounts.google.com", aud: AUD, email: PUSH_SA, email_verified: true, iat: NOW / 1000 - 10, exp: NOW / 1000 + 3600, ...extra });

function verifier() {
  let jwksCalls = 0;
  const fetch = async () => {
    jwksCalls++;
    return { ok: true, json: async () => ({ keys: [jwk] }) };
  };
  const verify = createPushTokenVerifier({ audience: AUD, serviceAccountEmail: PUSH_SA, fetch, now: () => NOW });
  return { verify, calls: () => jwksCalls };
}

test("oidc: a valid Pub/Sub token passes; keys are cached", async () => {
  const { verify, calls } = verifier();
  assert.equal((await verify(`Bearer ${jwt(goodClaims())}`)).email, PUSH_SA);
  await verify(`Bearer ${jwt(goodClaims({ iss: "accounts.google.com" }))}`);
  assert.equal(calls(), 1);
});

test("oidc: every wrong token is rejected", async () => {
  const { verify } = verifier();
  const other = generateKeyPairSync("rsa", { modulusLength: 2048 }).privateKey;
  const cases = [
    undefined,
    "Basic abc",
    "Bearer not.a.jwt!",
    `Bearer ${jwt(goodClaims(), { key: other })}`,
    `Bearer ${jwt(goodClaims(), { kid: "unknown" })}`,
    `Bearer ${jwt(goodClaims({ iss: "https://evil.example" }))}`,
    `Bearer ${jwt(goodClaims({ aud: "https://other" }))}`,
    `Bearer ${jwt(goodClaims({ email: "someone@else.com" }))}`,
    `Bearer ${jwt(goodClaims({ email_verified: false }))}`,
    `Bearer ${jwt(goodClaims({ exp: NOW / 1000 - 3600 }))}`,
    `Bearer ${jwt(goodClaims({ iat: NOW / 1000 + 3600 }))}`,
  ];
  for (const header of cases) await assert.rejects(verify(header), { status: 401 }, String(header).slice(0, 40));
});

// ---- Google auth and Play API ------------------------------------------------------------------

test("googleAuth: signs a JWT assertion with the key and caches the token", async () => {
  const key = { client_email: "play@p.iam.gserviceaccount.com", private_key: privateKey.export({ format: "pem", type: "pkcs8" }), private_key_id: "kid9" };
  const assertion = signAssertion(key, { nowMs: NOW });
  const [h, p, s] = assertion.split(".");
  assert.ok(createVerify("RSA-SHA256").update(`${h}.${p}`).verify(publicKey, Buffer.from(s, "base64url")));
  const claims = JSON.parse(Buffer.from(p, "base64url").toString());
  assert.equal(claims.scope, "https://www.googleapis.com/auth/androidpublisher");
  assert.equal(claims.iss, key.client_email);

  let t = NOW;
  const requests = [];
  const fetch = async (url, init) => {
    requests.push({ url, body: init.body });
    return { ok: true, json: async () => ({ access_token: `tok${requests.length}`, expires_in: 3600 }) };
  };
  const auth = createGoogleAuth({ serviceAccountKey: key, fetch, now: () => t });
  assert.equal(await auth.getAccessToken(), "tok1");
  assert.equal(await auth.getAccessToken(), "tok1");
  assert.match(requests[0].body, /grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer/);
  t += 3600 * 1000 - 30_000; // within the refresh margin
  assert.equal(await auth.getAccessToken(), "tok2");
});

test("googleAuth: without a key it uses the metadata server", async () => {
  let seen;
  const fetch = async (url, init) => {
    seen = { url, headers: init.headers };
    return { ok: true, json: async () => ({ access_token: "meta", expires_in: 100 }) };
  };
  assert.equal(await createGoogleAuth({ fetch, now: () => NOW }).getAccessToken(), "meta");
  assert.match(seen.url, /^http:\/\/metadata\.google\.internal\//);
  assert.equal(seen.headers["Metadata-Flavor"], "Google");
});

test("playApi: URLs, auth header and error mapping", async () => {
  const calls = [];
  const responses = [
    { ok: true, status: 200, text: async () => JSON.stringify(v2()) },
    { ok: true, status: 204, text: async () => "" },
    { ok: false, status: 410, text: async () => "" },
    { ok: false, status: 503, text: async () => "" },
  ];
  const api = createPlayApi({ auth: { getAccessToken: async () => "AT" }, fetch: async (url, init) => { calls.push({ url, init }); return responses.shift(); } });
  const got = await api.getSubscriptionV2(PKG, TOKEN_A);
  assert.equal(got.subscriptionState, "SUBSCRIPTION_STATE_ACTIVE");
  assert.equal(calls[0].url, `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PKG}/purchases/subscriptionsv2/tokens/${encodeURIComponent(TOKEN_A)}`);
  assert.equal(calls[0].init.headers.authorization, "Bearer AT");
  await api.acknowledge(PKG, "tsiptv_unlimited", TOKEN_A);
  assert.equal(calls[1].init.method, "POST");
  assert.match(calls[1].url, /\/purchases\/subscriptions\/tsiptv_unlimited\/tokens\/.+:acknowledge$/);
  await assert.rejects(api.getSubscriptionV2(PKG, TOKEN_A), (e) => e.notFound && !e.transient);
  await assert.rejects(api.getSubscriptionV2(PKG, TOKEN_A), (e) => !e.notFound && e.transient);
});

// ---- HTTP --------------------------------------------------------------------------------------

function request({ method = "POST", url, headers = {}, body }) {
  const req = new PassThrough();
  Object.assign(req, { method, url, headers });
  process.nextTick(() => req.end(body === undefined ? "" : typeof body === "string" ? body : JSON.stringify(body)));
  return req;
}

function response() {
  const res = { status: 0, headers: {}, body: "", headersSent: false };
  res.writeHead = (status, headers = {}) => { res.status = status; res.headers = headers; res.headersSent = true; };
  res.end = (chunk) => { if (chunk) res.body += chunk; res.done = true; };
  return res;
}

function routes(byToken = { [TOKEN_A]: v2() }) {
  const ctx = setup(byToken);
  const logs = [];
  const log = { info: (...a) => logs.push(JSON.stringify(a)), warn: (...a) => logs.push(JSON.stringify(a)), error: (...a) => logs.push(JSON.stringify(a)) };
  const r = createBillingRoutes({
    service: ctx.service,
    verifyIdToken: async (t) => { if (t !== "ID-u1") throw new Error("bad"); return { uid: "u1" }; },
    verifyPushToken: async (h) => { if (h !== "Bearer PUSH") throw new Error("bad push"); return {}; },
    log,
  });
  return { ...ctx, r, logs };
}

test("http verify: 200 with the entitlement; 401 without a valid ID token; errors are {error: code}", async () => {
  const { r, logs } = routes();
  let res = response();
  await r.handle(request({ url: "/billing/verify", headers: { authorization: "Bearer ID-u1" }, body: { productId: "tsiptv_unlimited", purchaseToken: TOKEN_A } }), res);
  assert.equal(res.status, 200);
  const body = JSON.parse(res.body);
  assert.equal(body.entitlement.plan, "unlimited");
  assert.equal(body.entitlement.active, true);
  assert.equal(body.entitlement.expiresAtMs, NOW + 30 * DAY);
  assert.equal(body.error, undefined);

  res = response();
  await r.handle(request({ url: "/billing/verify", headers: { authorization: "Bearer nope" }, body: {} }), res);
  assert.deepEqual([res.status, JSON.parse(res.body)], [401, { error: "unauthenticated" }]);

  res = response();
  await r.handle(request({ url: "/billing/verify", headers: { authorization: "Bearer ID-u1" }, body: "{oops" }), res);
  assert.deepEqual([res.status, JSON.parse(res.body)], [400, { error: "bad_json" }]);

  res = response();
  await r.handle(request({ url: "/billing/verify", headers: { authorization: "Bearer ID-u1" }, body: { productId: "tsiptv_unlimited", purchaseToken: TOKEN_B } }), res);
  assert.deepEqual([res.status, JSON.parse(res.body)], [404, { error: "purchase_not_found" }]);

  res = response();
  await r.handle(request({ method: "GET", url: "/billing/verify" }), res);
  assert.equal(res.status, 405);
  assert.equal(await r.handle(request({ url: "/other" }), response()), false);
  assert.ok(logs.every((line) => !line.includes(TOKEN_A) && !line.includes("ID-u1")), "tokens never logged");
});

test("http rtdn: OIDC required; 204 on success, 400 malformed, 500 transient", async () => {
  const { r, store, logs } = routes({ [TOKEN_A]: v2(), [TOKEN_B]: new PlayApiError("down", 503) });
  await store.linkPurchase(purchaseFromV2(v2(), { purchaseToken: TOKEN_A, nowMs: NOW }), "u1");
  const push = (data, auth = "Bearer PUSH") => request({ url: "/billing/rtdn", headers: { authorization: auth }, body: { message: { data: Buffer.from(JSON.stringify(data)).toString("base64"), messageId: "m1" }, subscription: "s" } });

  let res = response();
  await r.handle(push(rtdn(TOKEN_A), "Bearer wrong"), res);
  assert.equal(res.status, 401);
  assert.equal(store.entitlements.size, 0);

  res = response();
  await r.handle(push(rtdn(TOKEN_A)), res);
  assert.equal(res.status, 204);
  assert.equal(store.entitlements.get("u1").plan, "unlimited");

  res = response();
  await r.handle(request({ url: "/billing/rtdn", headers: { authorization: "Bearer PUSH" }, body: { message: { data: "!!!" } } }), res);
  assert.equal(res.status, 400);

  res = response();
  await r.handle(push(rtdn(TOKEN_B)), res);
  assert.equal(res.status, 500);
  assert.ok(logs.every((line) => !line.includes(TOKEN_A) && !line.includes(TOKEN_B)), "tokens never logged");
});

// ---- QC round 1 (S1-S4) ----------------------------------------------------------------------

test("S1: a revoked purchase stays revoked when a later RTDN or verify re-reads it as active", async () => {
  const current = v2({ ack: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" });
  const { store, service } = setup(() => current);
  await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });
  await service.handleRtdn(rtdn(TOKEN_A, NotificationType.REVOKED));
  // Play still reports the line item ACTIVE for a while; the record must not come back to life.
  await service.handleRtdn(rtdn(TOKEN_A, NotificationType.RENEWED));
  assert.equal(store.purchases.get(sha256Hex(TOKEN_A)).revoked, true);
  assert.equal(store.entitlements.get("u1").plan, "free");
  const doc = await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });
  assert.equal(doc.plan, "free");

  // Voided, then re-read: still revoked.
  const s2 = setup(() => current);
  await s2.service.verifyPurchase({ uid: "u2", productId: "tsiptv_unlimited", purchaseToken: TOKEN_B });
  await s2.service.handleRtdn({ packageName: PKG, voidedPurchaseNotification: { purchaseToken: TOKEN_B } });
  await s2.service.handleRtdn(rtdn(TOKEN_B, NotificationType.RECOVERED));
  assert.equal(s2.store.entitlements.get("u2").plan, "free");
});

test("S2: an upgrade without an account id inherits the replaced purchase's uid; the old uid is recomputed", async () => {
  const { store, service } = setup({
    [TOKEN_A]: v2({ productId: "tsiptv_noads", state: "SUBSCRIPTION_STATE_EXPIRED", expiry: NOW - 1 }),
    [TOKEN_B]: v2({ linked: TOKEN_A }),
  });
  await store.linkPurchase(purchaseFromV2(v2({ productId: "tsiptv_noads" }), { purchaseToken: TOKEN_A, nowMs: NOW }), "u1");
  const out = await service.handleRtdn(rtdn(TOKEN_B, NotificationType.PURCHASED));
  assert.deepEqual([out.outcome, out.uid], ["updated", "u1"]);
  assert.equal(store.purchases.get(sha256Hex(TOKEN_B)).uid, "u1");
  assert.equal(store.entitlements.get("u1").plan, "unlimited");

  // The replacement verified by another account: the old owner's entitlement is recomputed (now free).
  const s2 = setup({
    [TOKEN_A]: v2({ productId: "tsiptv_noads", state: "SUBSCRIPTION_STATE_EXPIRED", expiry: NOW - 1 }),
    [TOKEN_B]: v2({ linked: TOKEN_A }),
  });
  await s2.store.linkPurchase(purchaseFromV2(v2({ productId: "tsiptv_noads" }), { purchaseToken: TOKEN_A, nowMs: NOW }), "old");
  await s2.store.writeEntitlement("old", { plan: "no_ads", active: true });
  await s2.service.verifyPurchase({ uid: "new", productId: "tsiptv_unlimited", purchaseToken: TOKEN_B });
  assert.equal(s2.store.entitlements.get("old").plan, "free");
  assert.equal(s2.store.entitlements.get("new").plan, "unlimited");
});

test("S3: /billing/verify is rate limited per uid and per address", async () => {
  let t = NOW;
  const ctx = setup({ [TOKEN_A]: v2() });
  const r = createBillingRoutes({
    service: ctx.service,
    verifyIdToken: async (tok) => ({ uid: tok.slice(3) }),
    verifyPushToken: async () => ({}),
    log: silent,
    rateLimit: { perUid: 2, perIp: 3, windowMs: 60_000 },
    now: () => t,
  });
  const call = async (uid, ip = "10.0.0.1") => {
    const req = request({ url: "/billing/verify", headers: { authorization: `Bearer ID-${uid}` }, body: { productId: "tsiptv_unlimited", purchaseToken: TOKEN_A } });
    req.socket = { remoteAddress: ip };
    const res = response();
    await r.handle(req, res);
    return res.status;
  };
  assert.equal(await call("u1"), 200);
  assert.equal(await call("u1"), 200);
  assert.equal(await call("u1", "10.0.0.2"), 429); // 3rd for u1
  assert.equal(await call("u2"), 409); // 3rd for the address, passes the limit (token is u1's)
  assert.equal(await call("u3"), 429); // 4th for the address
  t += 60_000;
  assert.equal(await call("u1"), 200);
  const res = response();
  const req = request({ url: "/billing/verify", headers: {}, body: {} });
  req.socket = { remoteAddress: "10.0.0.9" };
  await r.handle(req, res);
  assert.equal(res.status, 401);
});

test("N3: behind a trusted proxy the address is counted from the right of X-Forwarded-For", async () => {
  const req = (xff, socket = "10.9.9.9") => ({ headers: xff === undefined ? {} : { "x-forwarded-for": xff }, socket: { remoteAddress: socket } });
  // Not trusted: the header is ignored.
  assert.equal(clientAddress(req("1.1.1.1"), false), "10.9.9.9");
  // One trusted hop: the rightmost entry; spoofed leftmost entries are ignored.
  assert.equal(clientAddress(req("6.6.6.6, 7.7.7.7, 203.0.113.5"), true), "203.0.113.5");
  assert.equal(clientAddress(req("203.0.113.5"), true, 1), "203.0.113.5");
  // Two trusted hops (load balancer + Cloud Run): second from the right.
  assert.equal(clientAddress(req("6.6.6.6, 203.0.113.5, 130.211.0.1"), true, 2), "203.0.113.5");
  // Fewer entries than trusted hops, empty or missing header: the socket address.
  assert.equal(clientAddress(req("203.0.113.5"), true, 2), "10.9.9.9");
  assert.equal(clientAddress(req(" , "), true), "10.9.9.9");
  assert.equal(clientAddress(req(undefined), true), "10.9.9.9");
  // Bad hop counts mean 1.
  assert.equal(clientAddress(req("6.6.6.6, 203.0.113.5"), true, 0), "203.0.113.5");

  // End to end: a client rotating spoofed left entries still shares one counter.
  const ctx = setup({ [TOKEN_A]: v2() });
  const r = createBillingRoutes({
    service: ctx.service,
    verifyIdToken: async () => {
      throw new Error("bad token");
    },
    verifyPushToken: async () => ({}),
    log: silent,
    rateLimit: { perIp: 2, trustProxy: true, trustedHops: 1 },
  });
  const statuses = [];
  for (let i = 0; i < 3; i++) {
    const rq = request({ url: "/billing/verify", headers: { "x-forwarded-for": `9.9.9.${i}, 203.0.113.5` }, body: {} });
    rq.socket = { remoteAddress: "10.0.0.1" };
    const res = response();
    await r.handle(rq, res);
    statuses.push(res.status);
  }
  assert.deepEqual(statuses, [401, 401, 429]);
});

test("S3: unknown key ids refetch the JWKS at most once per cooldown", async () => {
  let t = NOW;
  let jwksCalls = 0;
  const fetch = async () => {
    jwksCalls++;
    return { ok: true, json: async () => ({ keys: [jwk] }) };
  };
  const verify = createPushTokenVerifier({ audience: AUD, serviceAccountEmail: PUSH_SA, fetch, now: () => t });
  await verify(`Bearer ${jwt(goodClaims())}`);
  assert.equal(jwksCalls, 1);
  for (let i = 0; i < 5; i++) await assert.rejects(verify(`Bearer ${jwt(goodClaims(), { kid: `bogus${i}` })}`), { status: 401 });
  assert.equal(jwksCalls, 2, "one forced refetch, then the cooldown");
  t += 61_000;
  await assert.rejects(verify(`Bearer ${jwt(goodClaims(), { kid: "bogus" })}`), { status: 401 });
  assert.equal(jwksCalls, 3);
  // The normal cache expiry still refetches.
  t += 2 * 60 * 60 * 1000;
  await verify(`Bearer ${jwt(goodClaims({ iat: t / 1000 - 10, exp: t / 1000 + 3600 }))}`);
  assert.equal(jwksCalls, 4);
});

test("S4: the entitlement is recomputed through the store's atomic recomputeEntitlement", async () => {
  const { store, service } = setup({ [TOKEN_A]: v2() });
  let atomic = 0;
  const original = store.recomputeEntitlement;
  store.recomputeEntitlement = async (uid, compute) => {
    atomic++;
    return original(uid, compute);
  };
  store.writeEntitlement = async () => assert.fail("non-atomic write used");
  await service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A });
  await service.handleRtdn(rtdn(TOKEN_A, NotificationType.RENEWED));
  assert.equal(atomic, 2);
  assert.equal(store.entitlements.get("u1").plan, "unlimited");

  // Concurrent verify + RTDN for the same user end with the document matching the stored purchases.
  const s2 = setup({ [TOKEN_A]: v2(), [TOKEN_B]: v2({ productId: "tsiptv_noads" }) });
  await Promise.all([
    s2.service.verifyPurchase({ uid: "u1", productId: "tsiptv_unlimited", purchaseToken: TOKEN_A }),
    s2.service.verifyPurchase({ uid: "u1", productId: "tsiptv_noads", purchaseToken: TOKEN_B }),
    s2.service.handleRtdn(rtdn(TOKEN_A, NotificationType.RENEWED)),
  ]);
  const expected = computeEntitlement(await s2.store.listPurchasesByUid("u1"), NOW);
  assert.equal(s2.store.entitlements.get("u1").plan, expected.plan);
  assert.equal(expected.plan, "unlimited");
});
