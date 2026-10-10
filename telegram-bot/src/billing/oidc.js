// Verifies the OIDC token Pub/Sub attaches to authenticated push requests
// (https://cloud.google.com/pubsub/docs/authenticate-push-subscriptions): RS256 signature against
// Google's JWKS, issuer, audience, the push service account's email, expiry.
import { createPublicKey, createVerify } from "node:crypto";

const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const ISSUERS = new Set(["accounts.google.com", "https://accounts.google.com"]);
const SKEW_S = 60;
const JWKS_TTL_MS = 60 * 60 * 1000;

export class PushAuthError extends Error {
  constructor(message) {
    super(message);
    this.name = "PushAuthError";
    this.status = 401;
  }
}

const decodePart = (part) => JSON.parse(Buffer.from(part, "base64url").toString("utf8"));

/**
 * @param {object} options
 * @param {string} options.audience the audience configured on the push subscription
 * @param {string} options.serviceAccountEmail the service account the push subscription signs as
 * @param {typeof fetch} [options.fetch]
 * @param {() => number} [options.now] ms
 * @returns {(authorizationHeader: string | undefined) => Promise<object>} resolves to the claims
 */
export function createPushTokenVerifier({ audience, serviceAccountEmail, fetch = globalThis.fetch, now = Date.now, jwksUrl = GOOGLE_JWKS_URL }) {
  if (!audience || !serviceAccountEmail) throw new Error("push audience and service account email are required");
  let keys = null; // Map kid -> KeyObject
  let fetchedAt = 0;

  async function loadKeys(force) {
    if (keys && !force && now() - fetchedAt < JWKS_TTL_MS) return keys;
    const response = await fetch(jwksUrl);
    if (!response.ok) throw new Error(`JWKS fetch failed: HTTP ${response.status}`);
    const body = await response.json();
    const map = new Map();
    for (const jwk of body.keys || []) {
      if (jwk.kty === "RSA" && jwk.kid) map.set(jwk.kid, createPublicKey({ key: jwk, format: "jwk" }));
    }
    keys = map;
    fetchedAt = now();
    return keys;
  }

  return async function verify(authorization) {
    const match = /^Bearer\s+([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)$/.exec(String(authorization || "").trim());
    if (!match) throw new PushAuthError("missing or malformed bearer token");
    const [, h, p, s] = match;
    let header;
    let claims;
    try {
      header = decodePart(h);
      claims = decodePart(p);
    } catch {
      throw new PushAuthError("malformed token");
    }
    if (header.alg !== "RS256" || !header.kid) throw new PushAuthError("unsupported token algorithm");
    let key = (await loadKeys(false)).get(header.kid);
    if (!key) key = (await loadKeys(true)).get(header.kid); // keys rotate
    if (!key) throw new PushAuthError("unknown signing key");
    const ok = createVerify("RSA-SHA256").update(`${h}.${p}`).verify(key, Buffer.from(s, "base64url"));
    if (!ok) throw new PushAuthError("bad signature");

    const t = Math.floor(now() / 1000);
    if (!ISSUERS.has(claims.iss)) throw new PushAuthError("wrong issuer");
    const aud = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
    if (!aud.includes(audience)) throw new PushAuthError("wrong audience");
    if (claims.email !== serviceAccountEmail || claims.email_verified !== true) throw new PushAuthError("wrong service account");
    if (typeof claims.exp !== "number" || claims.exp + SKEW_S < t) throw new PushAuthError("token expired");
    if (typeof claims.iat === "number" && claims.iat - SKEW_S > t) throw new PushAuthError("token from the future");
    return claims;
  };
}
