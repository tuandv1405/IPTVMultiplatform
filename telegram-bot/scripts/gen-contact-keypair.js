// Creates the key pair that protects contributor contact data (full name, email,
// phone). Browsers encrypt with the PUBLIC key; only the PRIVATE key decrypts.
//
//   node scripts/gen-contact-keypair.js <output-dir>
//
// Writes <output-dir>/contact-private-key.json (keep it secret, back it up — it
// is loaded into the admin console to read contact data) and prints the public
// key to paste into web/public/assets/contributor-config.js.
import { mkdirSync, writeFileSync, existsSync } from "node:fs";
import { join, resolve } from "node:path";
import { keyId } from "../src/contactCrypto.js";

const outDir = resolve(process.argv[2] ?? ".");
const privatePath = join(outDir, "contact-private-key.json");
if (existsSync(privatePath)) {
  console.error(`${privatePath} already exists. Refusing to overwrite a key that may be protecting data.`);
  process.exit(1);
}

const { publicKey, privateKey } = await crypto.subtle.generateKey(
  { name: "RSA-OAEP", modulusLength: 3072, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true,
  ["encrypt", "decrypt"],
);
const publicJwk = await crypto.subtle.exportKey("jwk", publicKey);
const privateJwk = await crypto.subtle.exportKey("jwk", privateKey);
const kid = await keyId(publicJwk);

mkdirSync(outDir, { recursive: true });
writeFileSync(privatePath, JSON.stringify({ kid, ...privateJwk }, null, 2), { mode: 0o600 });

const publicForConfig = { kty: publicJwk.kty, n: publicJwk.n, e: publicJwk.e, alg: publicJwk.alg };
console.log(`Private key written to ${privatePath} (key id ${kid}).`);
console.log("Paste this into web/public/assets/contributor-config.js:\n");
console.log(`export const CONTACT_PUBLIC_KEY = ${JSON.stringify(publicForConfig)};`);
