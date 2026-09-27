import { createCipheriv, createDecipheriv, createHmac, randomBytes } from "node:crypto";

/**
 * Encrypts contributor contact data at rest.
 *
 * AES-256-GCM with a random 96-bit IV per value; the output carries the key ID
 * so the key can be rotated later (decrypt old values with the old key, write
 * new ones with the new one). The HMAC is a keyed hash for equality lookups —
 * a plain SHA-256 of a phone number is trivially brute-forced.
 */
export function createPiiVault({ encryptionKey, hashKey, keyId = "v1" }) {
  const key = decodeKey(encryptionKey, "PII_ENCRYPTION_KEY");
  const macKey = decodeKey(hashKey, "PII_HASH_KEY");

  return {
    encrypt(plaintext) {
      const iv = randomBytes(12);
      const cipher = createCipheriv("aes-256-gcm", key, iv);
      const body = Buffer.concat([cipher.update(String(plaintext), "utf8"), cipher.final()]);
      const tag = cipher.getAuthTag();
      return [keyId, iv.toString("base64url"), tag.toString("base64url"), body.toString("base64url")].join(":");
    },

    decrypt(token) {
      const parts = String(token).split(":");
      if (parts.length !== 4) throw new Error("Malformed ciphertext");
      const [id, iv, tag, body] = parts;
      if (id !== keyId) throw new Error(`Unknown key id ${id}`);
      const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(iv, "base64url"));
      decipher.setAuthTag(Buffer.from(tag, "base64url"));
      return Buffer.concat([decipher.update(Buffer.from(body, "base64url")), decipher.final()]).toString("utf8");
    },

    /** Domain-separated so an email hash can never collide with a phone hash. */
    hash(kind, value) {
      return createHmac("sha256", macKey).update(`${kind}:${normalise(kind, value)}`).digest("hex");
    },
  };
}

function normalise(kind, value) {
  const text = String(value ?? "").trim();
  return kind === "email" ? text.toLowerCase() : text.replace(/[\s-]/g, "");
}

function decodeKey(value, name) {
  const buffer = Buffer.from(String(value ?? ""), "base64");
  if (buffer.length !== 32) {
    throw new Error(`${name} must be 32 random bytes, base64-encoded (run: npm run gen-keys)`);
  }
  return buffer;
}
