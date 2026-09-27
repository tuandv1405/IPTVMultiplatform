import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createDevAuthVerifier, devToken } from "../src/auth.js";
import { encryptContact, importPrivateKey } from "../src/contactCrypto.js";
import { createHttpServer } from "../src/http/server.js";
import { POLICY_VERSIONS } from "../src/policy.js";
import { createContributorService } from "../src/service.js";
import { createMemoryStore } from "../src/store/memoryStore.js";
import { createSqliteStore } from "../src/store/sqliteStore.js";
import { createBot } from "../src/telegram/bot.js";
import { createNotifier } from "../src/telegram/notifier.js";

export const silentLog = { info() {}, warn() {}, error() {}, debug() {} };

/** 1×1 PNG. */
export const PNG_DATA_URL =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

export const ADMIN_EMAIL = "chintk111999@gmail.com";
export const TG_ADMIN = { id: 111, username: "tuandv1405", first_name: "Tuan" };
export const MEMBER = { id: 222, username: "reviewer_a", first_name: "Rev" };
export const STRANGER = { id: 333, username: "someone", first_name: "Some" };
export const REVIEW_CHAT = { id: -1001, type: "supergroup" };
export const PRIVATE = (user) => ({ id: user.id, type: "private" });

export const DRIVERS = ["memory", "sqlite"];

// One RSA key pair for the whole test run (3072-bit generation is slow).
const keyPair = await crypto.subtle.generateKey(
  { name: "RSA-OAEP", modulusLength: 3072, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true,
  ["encrypt", "decrypt"],
);
export const PUBLIC_JWK = await crypto.subtle.exportKey("jwk", keyPair.publicKey);
export const PRIVATE_KEY = await importPrivateKey(await crypto.subtle.exportKey("jwk", keyPair.privateKey));

export function testConfig(overrides = {}) {
  return {
    corsOrigins: ["https://tsiptv-8bdd6.web.app"],
    rateLimitPerMinute: 1000,
    trustProxy: false,
    adminEmails: [ADMIN_EMAIL],
    ...overrides,
    telegram: {
      mode: "polling",
      adminChatIds: ["999"],
      adminUsernames: ["tuandv1405"],
      reviewChatId: String(REVIEW_CHAT.id),
      webhookSecret: "x".repeat(32),
      ...overrides.telegram,
    },
  };
}

/** Records every Bot API call and answers like Telegram would. */
export function createFakeTelegram() {
  let nextId = 1;
  const calls = [];
  const record = (method, args) => {
    calls.push({ method, ...args });
    return { message_id: nextId++ };
  };
  return {
    calls,
    byMethod: (method) => calls.filter((c) => c.method === method),
    sendPhoto: async (chatId, photo, params) => record("sendPhoto", { chatId: String(chatId), photo, ...params }),
    sendMessage: async (chatId, text, extra) => record("sendMessage", { chatId: String(chatId), text, ...extra }),
    editMessageCaption: async (chatId, messageId, caption, extra) =>
      record("editMessageCaption", { chatId: String(chatId), messageId, caption, ...extra }),
    editMessageText: async (chatId, messageId, text, extra) =>
      record("editMessageText", { chatId: String(chatId), messageId, text, ...extra }),
    editMessageReplyMarkup: async (chatId, messageId, reply_markup) =>
      record("editMessageReplyMarkup", { chatId: String(chatId), messageId, reply_markup }),
    answerCallbackQuery: async (id, text, showAlert) => record("answerCallbackQuery", { id, text, showAlert }),
  };
}

export const okVerification = (overrides = {}) => ({
  ok: true,
  format: "m3u",
  channelCount: 12,
  groupCount: 3,
  sampleNames: ["One", "Two"],
  contentHash: "hash-default",
  finalUrl: "https://lists.example.com/a.m3u",
  bytes: 1234,
  checkedAt: 1,
  ...overrides,
});

function openStore(driver) {
  if (driver === "sqlite") {
    const dir = mkdtempSync(join(tmpdir(), "tsiptv-"));
    const store = createSqliteStore(join(dir, "test.sqlite"));
    return { store, cleanup: () => { store.close(); rmSync(dir, { recursive: true, force: true }); } };
  }
  return { store: createMemoryStore(), cleanup: () => {} };
}

/** A whole running stack on an ephemeral port: store, fake Telegram, dev auth. */
export async function startStack({ driver = "memory", verifyPlaylist, configOverrides, limits } = {}) {
  const { store, cleanup } = openStore(driver);
  const telegram = createFakeTelegram();
  const config = testConfig(configOverrides);
  const notifier = createNotifier({ api: telegram, config, store, log: silentLog });
  const service = createContributorService({
    store,
    verifyPlaylist: verifyPlaylist ?? (async () => okVerification()),
    notifier,
    adminEmails: config.adminEmails,
    contactPrivateKey: PRIVATE_KEY,
    log: silentLog,
    ...(limits ? { limits } : {}),
  });
  const bot = createBot({ api: telegram, service, notifier, config, store, log: silentLog });
  const server = createHttpServer({ service, verifyToken: createDevAuthVerifier(), config, bot, log: silentLog });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;

  async function call(method, path, { identity, body, headers = {} } = {}) {
    const response = await fetch(base + path, {
      method,
      headers: {
        ...(identity ? { Authorization: `Bearer ${devToken(identity)}` } : {}),
        ...(body ? { "Content-Type": "application/json" } : {}),
        ...headers,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await response.text();
    return { status: response.status, body: text ? JSON.parse(text) : null, headers: response.headers };
  }

  let updateId = 1;
  const press = (user, chat, data, message = { message_id: 1 }) =>
    bot.handleUpdate({ update_id: updateId++, callback_query: { id: `cq${updateId}`, from: user, data, message: { ...message, chat } } });
  const say = (user, chat, text) =>
    bot.handleUpdate({ update_id: updateId++, message: { message_id: 50, from: user, chat, text } });

  /** request → admin approves: the only way to become a contributor. */
  async function makeContributor(uid = "u1", publicName = "Tuan Lists") {
    const identity = googleUser(uid);
    const created = await call("POST", "/api/requests", { identity, body: await requestBody({ publicName, email: identity.email }) });
    if (created.status !== 200) throw new Error(`request failed: ${JSON.stringify(created.body)}`);
    const decided = await call("POST", `/api/admin/requests/${uid}/decide`, { identity: adminUser(), body: { decision: "APPROVED" } });
    if (decided.status !== 200) throw new Error(`approve failed: ${JSON.stringify(decided.body)}`);
    return identity;
  }

  return {
    store, telegram, service, bot, call, press, say, makeContributor,
    close: () => { server.close(); cleanup(); },
  };
}

export const googleUser = (uid = "u1", extra = {}) => ({
  uid,
  email: `${uid}@gmail.com`,
  emailVerified: true,
  provider: "google.com",
  ...extra,
});

export const adminUser = () => googleUser("owner", { email: ADMIN_EMAIL });

export async function requestBody({ publicName = "Tuan Lists", email = "u1@gmail.com", phone = "+84901234567", fullName = "Nguyen Van Tuan", ...rest } = {}) {
  return {
    publicName,
    about: "I maintain a list of Vietnamese public news channels.",
    links: ["https://example.com/a.m3u"],
    contactEnc: await encryptContact(PUBLIC_JWK, { fullName, email, phone }),
    consents: { ...POLICY_VERSIONS },
    ...rest,
  };
}

export const submission = (overrides = {}) => ({
  name: "My Vietnam news",
  url: "https://lists.example.com/a.m3u",
  description: "News channels I maintain",
  category: "news",
  language: "vi",
  image: PNG_DATA_URL,
  declarations: { own_work: true, not_copied: true, rights_ok: true },
  ...overrides,
});
