export class ConfigError extends Error {}

const list = (value) =>
  String(value ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

const DEFAULT_ORIGINS = ["https://tsiptv-8bdd6.web.app", "https://tsiptv-8bdd6.firebaseapp.com"];

/**
 * Reads and validates the environment. Fails with every problem listed at once,
 * so a first deploy does not turn into a loop of one-variable-at-a-time fixes.
 */
export function loadConfig(env = process.env) {
  const problems = [];
  const required = (key) => {
    const value = String(env[key] ?? "").trim();
    if (!value) problems.push(`${key} is required`);
    return value;
  };

  const nodeEnv = env.NODE_ENV || "development";
  const mode = (env.TELEGRAM_MODE || "polling").toLowerCase();
  const storeDriver = (env.STORE_DRIVER || "firestore").toLowerCase();

  const config = {
    nodeEnv,
    port: Number(env.PORT || 8080),
    host: env.HOST || "0.0.0.0",
    trustProxy: env.TRUST_PROXY === "true",
    // How many proxies append to X-Forwarded-For in front of this service.
    trustedProxyHops: Number(env.TRUSTED_PROXY_HOPS || 1),
    corsOrigins: list(env.CORS_ORIGINS).length ? list(env.CORS_ORIGINS) : DEFAULT_ORIGINS,
    rateLimitPerMinute: Number(env.RATE_LIMIT_PER_MINUTE || 30),
    storeDriver,
    allowDevAuth: env.ALLOW_DEV_AUTH === "true",
    firebaseProjectId: env.FIREBASE_PROJECT_ID || "tsiptv-8bdd6",
    telegram: {
      token: required("TELEGRAM_BOT_TOKEN"),
      mode,
      webhookUrl: (env.TELEGRAM_WEBHOOK_URL || "").replace(/\/+$/, ""),
      webhookSecret: env.TELEGRAM_WEBHOOK_SECRET || "",
      adminChatIds: list(env.TELEGRAM_ADMIN_CHAT_IDS),
      adminUsernames: list(env.TELEGRAM_ADMIN_USERNAMES).map((u) => u.replace(/^@/, "").toLowerCase()),
      reviewChatId: (env.TELEGRAM_REVIEW_CHAT_ID || "").trim() || null,
    },
    pii: {
      encryptionKey: required("PII_ENCRYPTION_KEY"),
      hashKey: required("PII_HASH_KEY"),
    },
  };

  for (const [key, value] of [
    ["PII_ENCRYPTION_KEY", config.pii.encryptionKey],
    ["PII_HASH_KEY", config.pii.hashKey],
  ]) {
    if (value && Buffer.from(value, "base64").length !== 32) {
      problems.push(`${key} must be 32 bytes, base64-encoded (npm run gen-keys)`);
    }
  }
  if (config.pii.encryptionKey && config.pii.encryptionKey === config.pii.hashKey) {
    problems.push("PII_ENCRYPTION_KEY and PII_HASH_KEY must be different keys");
  }

  const t = config.telegram;
  if (t.adminChatIds.length === 0 && t.adminUsernames.length === 0 && !t.reviewChatId) {
    problems.push("Set at least one of TELEGRAM_ADMIN_CHAT_IDS, TELEGRAM_ADMIN_USERNAMES or TELEGRAM_REVIEW_CHAT_ID");
  }
  for (const id of [...t.adminChatIds, ...(t.reviewChatId ? [t.reviewChatId] : [])]) {
    if (!/^-?\d+$/.test(id)) problems.push(`Telegram chat ID "${id}" must be numeric (send /whoami to the bot)`);
  }
  if (!["polling", "webhook", "off"].includes(mode)) problems.push("TELEGRAM_MODE must be polling, webhook or off");
  if (mode === "webhook") {
    if (!/^https:\/\//.test(t.webhookUrl)) problems.push("TELEGRAM_WEBHOOK_URL must be the public https:// base URL");
    if (!/^[A-Za-z0-9_-]{16,256}$/.test(t.webhookSecret)) {
      problems.push("TELEGRAM_WEBHOOK_SECRET must be 16-256 characters of A-Z a-z 0-9 _ -");
    }
  }
  if (!["firestore", "memory"].includes(storeDriver)) problems.push("STORE_DRIVER must be firestore or memory");
  if (config.allowDevAuth && nodeEnv === "production") {
    problems.push("ALLOW_DEV_AUTH=true is refused when NODE_ENV=production");
  }
  if (storeDriver === "memory" && nodeEnv === "production") {
    problems.push("STORE_DRIVER=memory is refused when NODE_ENV=production (data would be lost on restart)");
  }
  if (!Number.isInteger(config.port) || config.port <= 0) problems.push("PORT must be a positive integer");

  if (problems.length) {
    throw new ConfigError(`Invalid configuration:\n  - ${problems.join("\n  - ")}`);
  }
  return config;
}
