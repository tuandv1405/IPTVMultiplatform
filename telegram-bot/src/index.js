import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { createDevAuthVerifier, createFirebaseAuthVerifier } from "./auth.js";
import { ConfigError, loadConfig } from "./config.js";
import { createPiiVault } from "./crypto.js";
import { createHttpServer } from "./http/server.js";
import { createLogger } from "./logger.js";
import { createContributorService } from "./service.js";
import { createMemoryStore } from "./store/memoryStore.js";
import { createFirestoreStore } from "./store/firestoreStore.js";
import { createTelegramApi } from "./telegram/api.js";
import { createBot, runPolling } from "./telegram/bot.js";
import { createNotifier } from "./telegram/notifier.js";
import { createPlaylistVerifier } from "./verify/verifier.js";

const log = createLogger({ level: process.env.LOG_LEVEL || "info" });

// .env next to package.json for local and VPS runs. Cloud Run sets real env vars.
const envFile = fileURLToPath(new URL("../.env", import.meta.url));
if (existsSync(envFile)) process.loadEnvFile(envFile);

let config;
try {
  config = loadConfig();
} catch (error) {
  if (error instanceof ConfigError) {
    console.error(error.message);
    console.error("\nCopy .env.example to .env and fill it in. See README.md.");
    process.exit(1);
  }
  throw error;
}

let store;
let verifyToken;
let firebaseAuth = null;
if (config.storeDriver === "firestore" || !config.allowDevAuth) {
  // Credentials come from GOOGLE_APPLICATION_CREDENTIALS locally / on a VPS, and
  // from the service's own identity on Cloud Run.
  const { initializeApp, applicationDefault } = await import("firebase-admin/app");
  const { getFirestore } = await import("firebase-admin/firestore");
  const { getAuth } = await import("firebase-admin/auth");
  const app = initializeApp({ credential: applicationDefault(), projectId: config.firebaseProjectId });
  if (config.storeDriver === "firestore") {
    const db = getFirestore(app);
    db.settings({ ignoreUndefinedProperties: true });
    store = createFirestoreStore(db);
  }
  if (!config.allowDevAuth) {
    firebaseAuth = getAuth(app);
    verifyToken = createFirebaseAuthVerifier(firebaseAuth);
  }
}
if (!store) store = createMemoryStore();
if (!verifyToken) {
  log.warn("ALLOW_DEV_AUTH is on: any 'dev:' token is accepted. Never use this outside your machine.");
  verifyToken = createDevAuthVerifier();
}

const api = createTelegramApi({ token: config.telegram.token });
const notifier = createNotifier({ api, config, store, log });
const service = createContributorService({
  store,
  vault: createPiiVault(config.pii),
  verifyPlaylist: createPlaylistVerifier(),
  notifier,
  log,
});
const bot = createBot({ api, service, notifier, config, store, log });

const server = createHttpServer({ service, verifyToken, config, bot, log });
server.listen(config.port, config.host, () => {
  log.info("contributor api listening", { port: config.port, store: store.kind, telegram: config.telegram.mode });
});

const shutdown = new AbortController();

// Accounts deleted while this service was unreachable still get their
// contributor data purged. Only "user-not-found" counts as deleted; any other
// error is treated as "still exists" so nothing is purged on a lookup failure.
// Against the Auth emulator every production uid looks deleted: never sweep then.
if (firebaseAuth && config.storeDriver === "firestore" && !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
  const accountExists = async (uid) => {
    try {
      await firebaseAuth.getUser(uid);
      return true;
    } catch (error) {
      return error?.code !== "auth/user-not-found";
    }
  };
  const sweep = () =>
    service.sweepDeletedAccounts(accountExists).catch((error) => log.error("account sweep failed", { error: error.message }));
  setTimeout(sweep, 60_000).unref();
  setInterval(sweep, 60 * 60_000).unref();
}

if (config.telegram.mode !== "off") {
  try {
    const me = await api.getMe();
    bot.setBotUsername(me.username);
    log.info("telegram bot ready", { username: me.username });
    await api.setMyCommands([
      { command: "pending", description: "Playlists waiting for review" },
      { command: "stats", description: "Counts per status" },
      { command: "whoami", description: "Show chat and user IDs" },
      { command: "help", description: "How the review bot works" },
    ]);
  } catch (error) {
    log.error("telegram getMe failed — check TELEGRAM_BOT_TOKEN", { error: error.message });
  }

  if (config.telegram.mode === "polling") {
    runPolling({ api, bot, log, signal: shutdown.signal });
  } else {
    try {
      await api.setWebhook({
        url: `${config.telegram.webhookUrl}/telegram/webhook`,
        secret_token: config.telegram.webhookSecret,
        allowed_updates: ["message", "callback_query"],
      });
      log.info("telegram webhook registered", { url: `${config.telegram.webhookUrl}/telegram/webhook` });
    } catch (error) {
      // The API keeps serving; Telegram simply will not deliver updates yet.
      log.error("telegram setWebhook failed", { error: error.message });
    }
  }
}

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    log.info("shutting down", { signal });
    shutdown.abort();
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 5000).unref();
  });
}
