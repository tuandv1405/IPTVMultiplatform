import { unauthorized } from "./errors.js";

/**
 * Turns a bearer token into the caller's identity:
 * { uid, email, emailVerified, provider, claims: { admin } }.
 * Identity stays with Firebase Auth (Google sign-in) in both phases; only the
 * data moves to the own server.
 */
export function createFirebaseAuthVerifier(auth) {
  return async function verify(token) {
    try {
      // checkRevoked: a disabled or signed-out-everywhere account stops working now,
      // not when its hour-long ID token expires.
      const claims = await auth.verifyIdToken(token, true);
      return {
        uid: claims.uid,
        email: claims.email ?? null,
        emailVerified: claims.email_verified === true,
        provider: claims.firebase?.sign_in_provider ?? null,
        claims: { admin: claims.admin === true },
      };
    } catch {
      throw unauthorized();
    }
  };
}

/**
 * Local testing without Firebase: `dev:<base64url JSON identity>`.
 * Only wired in when ALLOW_DEV_AUTH=true, which config refuses in production.
 */
export function createDevAuthVerifier() {
  return async function verify(token) {
    if (!token.startsWith("dev:")) throw unauthorized();
    try {
      const identity = JSON.parse(Buffer.from(token.slice(4), "base64url").toString("utf8"));
      if (!identity.uid) throw new Error("uid");
      return {
        uid: String(identity.uid),
        email: identity.email ?? null,
        emailVerified: identity.emailVerified === true,
        provider: identity.provider ?? "google.com",
        claims: { admin: identity.admin === true },
      };
    } catch {
      throw unauthorized();
    }
  };
}

export function devToken(identity) {
  return `dev:${Buffer.from(JSON.stringify(identity)).toString("base64url")}`;
}
