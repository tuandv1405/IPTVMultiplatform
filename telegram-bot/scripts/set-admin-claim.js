// Grants (or revokes) the `admin` custom claim, which firestore.rules and the
// server accept in addition to the owner email hard-wired there.
//
//   GOOGLE_APPLICATION_CREDENTIALS=./service-account.json node scripts/set-admin-claim.js someone@gmail.com
//   ... node scripts/set-admin-claim.js someone@gmail.com --revoke
//
// The person must have signed in once (so the account exists), then sign out and
// in again for the new claim to reach their ID token.
import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";

const [email, flag] = process.argv.slice(2);
if (!email) {
  console.error("Usage: node scripts/set-admin-claim.js <email> [--revoke]");
  process.exit(1);
}
const auth = getAuth(initializeApp({ credential: applicationDefault(), projectId: process.env.FIREBASE_PROJECT_ID || "tsiptv-8bdd6" }));
const user = await auth.getUserByEmail(email);
const claims = { ...(user.customClaims ?? {}) };
if (flag === "--revoke") delete claims.admin;
else claims.admin = true;
await auth.setCustomUserClaims(user.uid, claims);
console.log(`${flag === "--revoke" ? "Revoked" : "Granted"} admin for ${email} (uid ${user.uid}). They must sign in again.`);
