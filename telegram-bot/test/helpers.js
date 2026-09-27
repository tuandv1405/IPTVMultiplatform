import { randomBytes } from "node:crypto";
import { devToken } from "../src/auth.js";
import { createDevAuthVerifier } from "../src/auth.js";
import { createPiiVault } from "../src/crypto.js";
import { createHttpServer } from "../src/http/server.js";
import { createContributorService } from "../src/service.js";
import { createMemoryStore } from "../src/store/memoryStore.js";
import { createBot } from "../src/telegram/bot.js";
import { createNotifier } from "../src/telegram/notifier.js";
import { POLICY_VERSION } from "../src/policy.js";

export const silentLog = { info() {}, warn() {}, error() {}, debug() {} };

/** 1×1 PNG. */
export const PNG_DATA_URL =
  "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

export const ADMIN = { id: 111, username: "tuandv1405", first_name: "Tuan" };
export const MEMBER = { id: 222, username: "reviewer_a", first_name: "Rev" };
export const STRANGER = { id: 333, username: "someone", first_name: "Some" };
export const REVIEW_CHAT = { id: -1001, type: "supergroup" };

export function testConfig(overrides = {}) {
  return {
    corsOrigins: ["https://tsiptv-8bdd6.web.app"],
    rateLimitPerMinute: 1000,
    trustProxy: false,
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

/** A whole running stack on an ephemeral port: memory store, fake Telegram, dev auth. */
export async function startStack({ verifyPlaylist, configOverrides, limits } = {}) {
  const store = createMemoryStore();
  const telegram = createFakeTelegram();
  const config = testConfig(configOverrides);
  const vault = createPiiVault({
    encryptionKey: randomBytes(32).toString("base64"),
    hashKey: randomBytes(32).toString("base64"),
  });
  const notifier = createNotifier({ api: telegram, config, store, log: silentLog });
  const service = createContributorService({
    store,
    vault,
    verifyPlaylist: verifyPlaylist ?? (async () => okVerification()),
    notifier,
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
    bot.handleUpdate({
      update_id: updateId++,
      callback_query: { id: `cq${updateId}`, from: user, data, message: { ...message, chat } },
    });
  const say = (user, chat, text) =>
    bot.handleUpdate({ update_id: updateId++, message: { message_id: 50, from: user, chat, text } });

  return { store, telegram, service, bot, vault, call, press, say, close: () => server.close() };
}

export const verifiedUser = (uid = "u1", extra = {}) => ({
  uid,
  email: `${uid}@example.com`,
  emailVerified: true,
  phone: "+84901234567",
  ...extra,
});

export const registration = (publicName = "Tuan Lists") => ({
  publicName,
  policyVersion: POLICY_VERSION,
  declarations: { contact_accurate: true, accept_policy: true, accept_takedown: true },
});

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
