// Prints a random TELEGRAM_WEBHOOK_SECRET for .env (webhook mode only).
// The contact-data key pair is made by scripts/gen-contact-keypair.js.
import { randomBytes } from "node:crypto";

console.log(`TELEGRAM_WEBHOOK_SECRET=${randomBytes(24).toString("base64url")}`);
