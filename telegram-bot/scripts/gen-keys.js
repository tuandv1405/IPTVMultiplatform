// Prints fresh keys for .env. Run once per deployment and keep them safe:
// losing PII_ENCRYPTION_KEY makes every stored email and phone unreadable.
import { randomBytes } from "node:crypto";

console.log(`PII_ENCRYPTION_KEY=${randomBytes(32).toString("base64")}`);
console.log(`PII_HASH_KEY=${randomBytes(32).toString("base64")}`);
console.log(`TELEGRAM_WEBHOOK_SECRET=${randomBytes(24).toString("base64url")}`);
