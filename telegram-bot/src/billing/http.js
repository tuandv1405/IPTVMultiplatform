// HTTP handlers for plain node:http: POST /billing/verify (the app) and POST /billing/rtdn
// (Pub/Sub push). Tokens are never logged.
import { BillingError } from "./service.js";

const MAX_BODY = 64 * 1024;

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY) {
        reject(new BillingError(413, "too_large"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

async function readJson(req) {
  const text = await readBody(req);
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    throw new BillingError(400, "bad_json");
  }
}

function send(res, status, body) {
  if (body === undefined) {
    res.writeHead(status, { "cache-control": "no-store" });
    res.end();
    return;
  }
  const json = JSON.stringify(body);
  res.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  res.end(json);
}

/** The entitlement document as JSON: dates as epoch milliseconds. */
export function entitlementToJson(doc) {
  return {
    plan: doc.plan,
    active: doc.active,
    state: doc.state,
    productId: doc.productId ?? null,
    basePlanId: doc.basePlanId ?? null,
    expiresAtMs: doc.expiresAt instanceof Date ? doc.expiresAt.getTime() : null,
    autoRenewing: Boolean(doc.autoRenewing),
    source: doc.source,
  };
}

/**
 * @param {object} deps
 * @param {ReturnType<import("./service.js").createBillingService>} deps.service
 * @param {(idToken: string) => Promise<{ uid: string }>} deps.verifyIdToken Firebase Auth ID token check
 * @param {(authorization: string | undefined) => Promise<object>} deps.verifyPushToken OIDC check (oidc.js)
 * @returns {{ verify(req, res): Promise<void>, rtdn(req, res): Promise<void>, handle(req, res): Promise<boolean> }}
 */
export function createBillingRoutes({ service, verifyIdToken, verifyPushToken, log = console }) {
  async function verify(req, res) {
    try {
      const match = /^Bearer\s+(\S+)$/.exec(String(req.headers.authorization || ""));
      if (!match) throw new BillingError(401, "unauthenticated");
      let uid;
      try {
        uid = (await verifyIdToken(match[1]))?.uid;
      } catch {
        throw new BillingError(401, "unauthenticated");
      }
      if (!uid) throw new BillingError(401, "unauthenticated");
      const body = await readJson(req);
      const doc = await service.verifyPurchase({ uid, productId: body.productId, purchaseToken: body.purchaseToken });
      send(res, 200, { entitlement: entitlementToJson(doc) });
    } catch (error) {
      fail(res, error, "verify");
    }
  }

  async function rtdn(req, res) {
    try {
      try {
        await verifyPushToken(req.headers.authorization);
      } catch (error) {
        log.warn?.("billing: RTDN push refused", { reason: error.message });
        send(res, 401, { error: "unauthenticated" });
        return;
      }
      const body = await readJson(req);
      const data = body?.message?.data;
      if (typeof data !== "string" || !data) throw new BillingError(400, "bad_message");
      let message;
      try {
        message = JSON.parse(Buffer.from(data, "base64").toString("utf8"));
      } catch {
        throw new BillingError(400, "bad_message");
      }
      const result = await service.handleRtdn(message);
      log.info?.("billing: RTDN handled", { outcome: result.outcome, messageId: body.message.messageId ?? null });
      send(res, 204);
    } catch (error) {
      // 4xx: Pub/Sub would redeliver a bad message forever, so malformed input is answered 400
      // (configure a dead-letter topic); transient failures get 500 so Pub/Sub retries.
      fail(res, error, "rtdn");
    }
  }

  function fail(res, error, route) {
    if (error instanceof BillingError) {
      if (error.status >= 500) log.warn?.(`billing: ${route} failed`, { code: error.code });
      send(res, error.status >= 500 && route === "rtdn" ? 500 : error.status, { error: error.code });
      return;
    }
    log.error?.(`billing: ${route} error`, { error: error?.message });
    send(res, 500, { error: "internal" });
  }

  return {
    verify,
    rtdn,
    /** Handles the request if it is a billing route; returns false otherwise (for mounting). */
    async handle(req, res) {
      const path = new URL(req.url, "http://localhost").pathname;
      if (path === "/billing/verify" || path === "/billing/rtdn") {
        if (req.method !== "POST") {
          send(res, 405, { error: "method_not_allowed" });
          return true;
        }
        await (path === "/billing/verify" ? verify(req, res) : rtdn(req, res));
        return true;
      }
      return false;
    },
  };
}
