/* Contributor programme configuration. One file, so switching from the
 * temporary Firestore backend to the own server (VPS / physical) is one edit.
 * See docs/prd-contributor-requests.md.
 */

/** "firestore": browsers read and write Firestore directly (today).
 *  "http": every operation goes to the contributor server in telegram-bot/. */
export const BACKEND = "firestore";

/** http backend only. The server's public URL, e.g. "https://api.your-domain.example",
 *  or "" when a Hosting rewrite serves /api/** on this origin. */
export const API_BASE = "{{CONTRIBUTOR_API_BASE}}";

/** Who the admin console treats as admin. Display only: Firestore rules (and
 *  the server) enforce the same list plus the admin custom claim. */
export const ADMIN_EMAILS = ["chintk111999@gmail.com"];

/** Contributors' full name, email and phone are encrypted with this key in the
 *  browser. The matching private key stays with the admin (never in the repo).
 *  Regenerate with: node telegram-bot/scripts/gen-contact-keypair.js <dir> */
export const CONTACT_PUBLIC_KEY = {"kty":"RSA","n":"znlGXFkgnAIDH5QWZGs2E-yNlG61HnpOuwcIy60ohVK7Ck6JLgNLySBmmGGFnoigb4aLPi5sESp1eTnkTa1yemYqEd3dNHDjsXkHHvDXlyaBA221Fj2mtSTwD3B2yUKieKCQvrtfRkOQU9PyvxSMmgVBzWXyU_5yn2AdsA4XrpCjv8LRFbHT9PtEDue0YCIha3AHuQRcFSnpoEfvGLUvbvqt2owbFnXMjzXI5324O1oEu_zg1Bv4kb4s4xRQViWcOIZ5ERsdWLSTT1T2NYPpGs3WhI7jSRSBYDdpyAFftb-bw82NJIwNe13fE5TsAZ_IwGPKwoqKx1AdiP22ZC3dVr1UXy9pgpz0PRx68ff_3TSGvjqEKRvqUIjD9dj1OoK9t1yBgT9oqtynVAUQjhfR8O--w4vSUbfEjsH6V77u_gshPwxaWtzSl81rda1nfJzPM9RhN1Wg4BqFdSWmOEv94WgNbgwFq2UpcjpC0unvGgzGIGy_lEiMrCjKyrr_KxZ3","e":"AQAB","alg":"RSA-OAEP-256"};

/** Bump when the text of the page changes; stored with every consent. */
export const POLICY_VERSIONS = { policy: "2026-09-27", terms: "2026-09-16", privacy: "2026-09-27" };

export const API_CONFIGURED = BACKEND === "firestore" || !API_BASE.includes("{{");