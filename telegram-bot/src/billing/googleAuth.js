// OAuth 2.0 access tokens for the Google Play Developer API (androidpublisher scope).
//
// Two sources, no extra dependencies:
//  - a service-account JSON key: an RS256-signed JWT assertion exchanged at
//    https://oauth2.googleapis.com/token (RFC 7523);
//  - otherwise the GCE / Cloud Run metadata server (the service's own identity).
// Tokens are cached until about a minute before they expire.
import { createSign } from "node:crypto";

export const ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const METADATA_URL =
  "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token";
const REFRESH_MARGIN_MS = 60_000;

const b64url = (input) => Buffer.from(input).toString("base64url");

/** Builds the signed JWT assertion for a service-account key (exported for tests). */
export function signAssertion(key, { scope = ANDROID_PUBLISHER_SCOPE, nowMs = Date.now() } = {}) {
  if (!key?.client_email || !key?.private_key) throw new Error("service account key needs client_email and private_key");
  const iat = Math.floor(nowMs / 1000);
  const header = { alg: "RS256", typ: "JWT", ...(key.private_key_id ? { kid: key.private_key_id } : {}) };
  const claims = { iss: key.client_email, scope, aud: key.token_uri || TOKEN_URL, iat, exp: iat + 3600 };
  const unsigned = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(claims))}`;
  const signature = createSign("RSA-SHA256").update(unsigned).sign(key.private_key).toString("base64url");
  return `${unsigned}.${signature}`;
}

/**
 * @param {object} options
 * @param {object} [options.serviceAccountKey] parsed JSON key; when absent the metadata server is used
 * @param {typeof fetch} [options.fetch]
 * @param {() => number} [options.now] clock in ms
 * @returns {{ getAccessToken(): Promise<string> }}
 */
export function createGoogleAuth({ serviceAccountKey = null, scope = ANDROID_PUBLISHER_SCOPE, fetch = globalThis.fetch, now = Date.now } = {}) {
  let cached = null; // { token, expiresAt }
  let inflight = null;

  async function fetchToken() {
    let response;
    if (serviceAccountKey) {
      const assertion = signAssertion(serviceAccountKey, { scope, nowMs: now() });
      response = await fetch(serviceAccountKey.token_uri || TOKEN_URL, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }).toString(),
      });
    } else {
      response = await fetch(`${METADATA_URL}?scopes=${encodeURIComponent(scope)}`, {
        headers: { "Metadata-Flavor": "Google" },
      });
    }
    if (!response.ok) throw new Error(`google token request failed: HTTP ${response.status}`);
    const body = await response.json();
    if (!body.access_token) throw new Error("google token response has no access_token");
    const ttlMs = Number(body.expires_in || 3600) * 1000;
    return { token: body.access_token, expiresAt: now() + ttlMs };
  }

  return {
    async getAccessToken() {
      if (cached && now() < cached.expiresAt - REFRESH_MARGIN_MS) return cached.token;
      if (!inflight) {
        inflight = fetchToken()
          .then((result) => {
            cached = result;
            return result.token;
          })
          .finally(() => {
            inflight = null;
          });
      }
      return inflight;
    },
  };
}
