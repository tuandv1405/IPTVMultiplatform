/* Contact-data encryption for the contributor programme.
 *
 * Hybrid public-key encryption with WebCrypto, identical in the browser and in
 * Node (telegram-bot/src/contactCrypto.js is a byte-for-byte copy; a test keeps
 * them in sync):
 *   - a fresh AES-256-GCM key encrypts the JSON payload;
 *   - that key is wrapped with the publisher's RSA-OAEP-3072 (SHA-256) public key.
 * Anyone can encrypt with the public key; only the holder of the private key can
 * decrypt. The private key never reaches the repository, Firestore or the site.
 */

export const CONTACT_ALG = "RSA-OAEP-256+A256GCM";

const RSA = { name: "RSA-OAEP", hash: "SHA-256" };
const subtle = () => globalThis.crypto.subtle;

const toB64 = (buffer) => {
  let binary = "";
  for (const byte of new Uint8Array(buffer)) binary += String.fromCharCode(byte);
  return btoa(binary);
};
const fromB64 = (text) => Uint8Array.from(atob(text), (c) => c.charCodeAt(0));

/** Short, stable identifier of a public key, stored with each ciphertext. */
export async function keyId(publicJwk) {
  const digest = await subtle().digest("SHA-256", new TextEncoder().encode(publicJwk.n));
  return [...new Uint8Array(digest).slice(0, 8)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export async function encryptContact(publicJwk, payload) {
  const publicKey = await subtle().importKey("jwk", publicJwk, RSA, false, ["encrypt"]);
  const aesKey = await subtle().generateKey({ name: "AES-GCM", length: 256 }, true, ["encrypt"]);
  const iv = globalThis.crypto.getRandomValues(new Uint8Array(12));
  const ct = await subtle().encrypt({ name: "AES-GCM", iv }, aesKey, new TextEncoder().encode(JSON.stringify(payload)));
  const wrappedKey = await subtle().encrypt(RSA, publicKey, await subtle().exportKey("raw", aesKey));
  return { v: 1, alg: CONTACT_ALG, kid: await keyId(publicJwk), wrappedKey: toB64(wrappedKey), iv: toB64(iv), ct: toB64(ct) };
}

export async function importPrivateKey(privateJwk) {
  return subtle().importKey("jwk", privateJwk, RSA, false, ["decrypt"]);
}

/** `privateKey` is a CryptoKey from importPrivateKey. Throws if the key does not match. */
export async function decryptContact(privateKey, envelope) {
  if (!envelope || envelope.v !== 1 || envelope.alg !== CONTACT_ALG) throw new Error("Unsupported contact envelope");
  const raw = await subtle().decrypt(RSA, privateKey, fromB64(envelope.wrappedKey));
  const aesKey = await subtle().importKey("raw", raw, { name: "AES-GCM" }, false, ["decrypt"]);
  const plain = await subtle().decrypt({ name: "AES-GCM", iv: fromB64(envelope.iv) }, aesKey, fromB64(envelope.ct));
  return JSON.parse(new TextDecoder().decode(plain));
}
