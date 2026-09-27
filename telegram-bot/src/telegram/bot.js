import { REJECT_REASONS, REQUEST_REJECT_REASONS, REQUEST_STATUS, STATUS } from "../policy.js";
import { ADMIN_CHATS_STATE } from "./notifier.js";
import {
  escapeHtml, rejectReasonKeyboard, requestKeyboard, requestRejectKeyboard, reviewKeyboard, reviewerLabel,
} from "./render.js";

const HELP = [
  "<b>TS IPTV review bot</b>",
  "",
  "Contributor requests and new playlists arrive here as cards. Anyone in the review group can use the buttons.",
  "",
  "/pending — post every request and playlist waiting for review",
  "/stats — counts per status",
  "/whoami — this chat's ID and your user ID (for .env)",
  "",
  "<i>Admins only</i>",
  "/takedown &lt;id&gt; [reason] — remove a published playlist",
  "/ban &lt;uid&gt; [reason] — suspend a contributor and remove their playlists",
  "/contact &lt;playlist id or uid&gt; — contributor's name, email and phone (private chat only; needs CONTACT_PRIVATE_KEY_FILE)",
].join("\n");

/**
 * Handles Telegram updates: slash commands and the review buttons.
 * Transport-agnostic — polling and the webhook both feed handleUpdate().
 */
export function createBot({ api, service, notifier, config, store, log = console }) {
  const adminIds = new Set(config.telegram.adminChatIds.map(String));
  const adminUsernames = new Set(config.telegram.adminUsernames);
  const reviewChatId = config.telegram.reviewChatId ? String(config.telegram.reviewChatId) : null;
  let botUsername = config.telegram.botUsername ?? null;

  const isAdmin = (user) =>
    Boolean(user) &&
    (adminIds.has(String(user.id)) || (user.username && adminUsernames.has(user.username.toLowerCase())));

  // Anyone who can see a message in the review group is a member of it, which
  // is exactly who may review. Anywhere else (a private chat, a forwarded card)
  // only admins may press the buttons.
  const canReview = (user, chat) => (reviewChatId && String(chat?.id) === reviewChatId) || isAdmin(user);

  // Same { by, name } shape as an admin in the web console.
  const reviewerOf = (user) => ({
    by: `tg:${user.id}`,
    name: user.username ? `@${user.username}` : [user.first_name, user.last_name].filter(Boolean).join(" ") || `tg:${user.id}`,
  });

  const reply = (chatId, text, extra = {}) =>
    api.sendMessage(chatId, text, { parse_mode: "HTML", link_preview_options: { is_disabled: true }, ...extra });

  async function handleUpdate(update) {
    try {
      if (update.callback_query) return await onCallback(update.callback_query);
      if (update.message?.text) return await onMessage(update.message);
    } catch (error) {
      log.error?.("telegram update failed", { updateId: update.update_id, error: error.message });
      if (update.callback_query) {
        await api.answerCallbackQuery(update.callback_query.id, "Something went wrong. Try again.", true).catch(() => {});
      }
    }
  }

  // ---- commands ------------------------------------------------------------

  async function onMessage(message) {
    const match = /^\/([a-z_]+)(?:@(\w+))?(?:\s+([\s\S]*))?$/i.exec(message.text.trim());
    if (!match) return;
    const [, rawCommand, addressedTo, rawArgs = ""] = match;
    // In a group, "/pending@other_bot" is not for us.
    if (addressedTo && botUsername && addressedTo.toLowerCase() !== botUsername.toLowerCase()) return;

    const command = rawCommand.toLowerCase();
    const args = rawArgs.trim();
    const chat = message.chat;
    const user = message.from;

    switch (command) {
      case "start":
      case "whoami":
        return whoami(chat, user);
      case "help":
        return reply(chat.id, HELP);
      case "pending":
        if (!canReview(user, chat)) return denied(chat);
        return pending(chat);
      case "stats":
        if (!canReview(user, chat)) return denied(chat);
        return stats(chat);
      case "takedown":
        if (!isAdmin(user)) return denied(chat);
        return takedown(chat, user, args);
      case "ban":
        if (!isAdmin(user)) return denied(chat);
        return ban(chat, user, args);
      case "reinstate":
        if (!isAdmin(user)) return denied(chat);
        return reinstate(chat, user, args);
      case "contact":
        if (!isAdmin(user)) return denied(chat);
        if (chat.type !== "private") {
          return reply(chat.id, "🔒 /contact only works in a private chat with the bot, never in a group.");
        }
        return contact(chat, args);
      default:
        if (chat.type === "private") return reply(chat.id, HELP);
    }
  }

  async function whoami(chat, user) {
    const lines = [
      `Chat ID: <code>${escapeHtml(chat.id)}</code> (${escapeHtml(chat.type)})`,
      `Your user ID: <code>${escapeHtml(user?.id)}</code>${user?.username ? ` · @${escapeHtml(user.username)}` : ""}`,
    ];
    if (chat.type === "private" && isAdmin(user)) {
      const learned = (await store.getState(ADMIN_CHATS_STATE)) ?? [];
      if (!learned.includes(String(chat.id)) && !adminIds.has(String(chat.id))) {
        await store.setState(ADMIN_CHATS_STATE, [...learned, String(chat.id)]);
      }
      lines.push("", "✅ You are an admin. New playlists will be sent to this chat.");
    } else if (reviewChatId && String(chat.id) === reviewChatId) {
      lines.push("", "✅ This is the review group. New playlists will be posted here.");
    }
    return reply(chat.id, lines.join("\n"));
  }

  async function pending(chat) {
    const requests = await service.pendingRequests(10);
    const items = await service.pending(10);
    if (items.length === 0 && requests.length === 0) return reply(chat.id, "Nothing is waiting for review. 🎉");
    if (requests.length) await reply(chat.id, `🟡 ${requests.length} contributor request(s):`);
    for (const request of requests) {
      const ref = await notifier.postRequestCard(chat.id, request);
      await service.addRequestMessages(request.uid, [ref]);
    }
    if (items.length === 0) return;
    await reply(chat.id, `🟡 ${items.length} playlist(s) in review:`);
    for (const submission of items) {
      const image = await service.getImage(submission.id);
      const ref = await notifier.postCard(chat.id, submission, image);
      await service.addTelegramMessages(submission.id, [ref]);
    }
  }

  async function stats(chat) {
    const counts = await service.stats();
    const lines = Object.values(STATUS).map((s) => `${s}: <b>${counts[s] ?? 0}</b>`);
    return reply(chat.id, lines.join("\n"));
  }

  async function takedown(chat, user, args) {
    const [id, ...rest] = args.split(/\s+/);
    if (!id) return reply(chat.id, "Usage: /takedown &lt;id&gt; [reason]");
    const result = await service.takedown(id, reviewerOf(user), rest.join(" ") || "Removed by the publisher");
    if (result.ok) return reply(chat.id, `🗑 <code>${escapeHtml(id)}</code> taken down.`);
    return reply(chat.id, notApplied(id, result.current, "published"));
  }

  async function ban(chat, user, args) {
    const [uid, ...rest] = args.split(/\s+/);
    if (!uid) return reply(chat.id, "Usage: /ban &lt;uid&gt; [reason]");
    try {
      const result = await service.setContributorStatus(uid, "SUSPENDED", rest.join(" ") || "Contributor suspended", reviewerOf(user));
      return reply(
        chat.id,
        `⛔ Contributor <code>${escapeHtml(uid)}</code> suspended. ${result.affected.length} playlist(s) rejected or removed.`,
      );
    } catch {
      return reply(chat.id, `No contributor with uid <code>${escapeHtml(uid)}</code>.`);
    }
  }

  async function reinstate(chat, user, args) {
    const uid = args.split(/\s+/)[0];
    if (!uid) return reply(chat.id, "Usage: /reinstate &lt;uid&gt;");
    try {
      await service.setContributorStatus(uid, "ACTIVE", "", reviewerOf(user));
      return reply(chat.id, `✅ Contributor <code>${escapeHtml(uid)}</code> reinstated.`);
    } catch {
      return reply(chat.id, `No contributor with uid <code>${escapeHtml(uid)}</code>.`);
    }
  }

  async function contact(chat, args) {
    const id = args.split(/\s+/)[0];
    if (!id) return reply(chat.id, "Usage: /contact &lt;submission id&gt;");
    const info = await service.contact(id);
    if (info?.unavailable) return reply(chat.id, "🔒 CONTACT_PRIVATE_KEY_FILE is not configured on this server. Use the admin console instead.");
    if (!info) return reply(chat.id, `No request or playlist <code>${escapeHtml(id)}</code>.`);
    log.info?.("contact data disclosed to admin", { submission: id, chat: chat.id });
    return reply(
      chat.id,
      [
        `👤 ${escapeHtml(info.publicName)} · <code>${escapeHtml(info.uid)}</code>`,
        `🪪 ${escapeHtml(info.fullName)}`,
        `✉️ ${escapeHtml(info.email)}`,
        `📱 ${escapeHtml(info.phone)}`,
        "",
        "<i>Confidential. Use only to resolve a copyright or abuse report.</i>",
      ].join("\n"),
    );
  }

  const denied = (chat) => reply(chat.id, "⛔ You are not allowed to do that.");

  // ---- buttons ---------------------------------------------------------------

  async function onCallback(query) {
    const [action, id, code] = String(query.data ?? "").split(":");
    const message = query.message;
    const chat = message?.chat;
    const user = query.from;

    if (!canReview(user, chat)) {
      return api.answerCallbackQuery(query.id, "Only members of the review group can review.", true);
    }

    // Deciding who becomes a contributor is the admins' call (the review group
    // reviews playlists).
    if (action.startsWith("q") && !isAdmin(user)) {
      return api.answerCallbackQuery(query.id, "Only admins can decide contributor requests.", true);
    }

    switch (action) {
      // ---- contributor requests ----
      case "qa": {
        try {
          const result = await service.decideRequest(id, REQUEST_STATUS.APPROVED, "", reviewerOf(user));
          return answerResult(query, result, "✅ Contributor approved");
        } catch (error) {
          if (error.code !== "already_contributor") throw error;
          return api.answerCallbackQuery(query.id, "This user is already a contributor (possibly suspended). Use /reinstate instead.", true);
        }
      }
      case "qr": {
        const request = await service.getRequest(id);
        if (request?.status !== REQUEST_STATUS.PENDING) return answerResult(query, { ok: false, current: request });
        await editKeyboard(message, requestRejectKeyboard(id));
        return api.answerCallbackQuery(query.id, "Pick a reason");
      }
      case "qb": {
        const request = await service.getRequest(id);
        if (request) await editKeyboard(message, requestKeyboard(request));
        return api.answerCallbackQuery(query.id);
      }
      case "qx": {
        if (!(code in REQUEST_REJECT_REASONS)) return api.answerCallbackQuery(query.id, "Unknown reason", true);
        const reason = REQUEST_REJECT_REASONS[code].replace(/^\S+\s/, "");
        const result = await service.decideRequest(id, REQUEST_STATUS.REJECTED, reason, reviewerOf(user));
        return answerResult(query, result, "❌ Request rejected");
      }
      // ---- playlists ----
      case "ap": {
        const result = await service.approve(id, reviewerOf(user));
        return answerResult(query, result, "✅ Approved and published");
      }
      case "rj": {
        const submission = await service.getSubmission(id);
        if (submission?.status !== STATUS.IN_REVIEW) return answerResult(query, { ok: false, current: submission });
        await editKeyboard(message, rejectReasonKeyboard(id));
        return api.answerCallbackQuery(query.id, "Pick a reason");
      }
      case "bk": {
        const submission = await service.getSubmission(id);
        if (submission) await editKeyboard(message, reviewKeyboard(submission));
        return api.answerCallbackQuery(query.id);
      }
      case "rr": {
        if (!(code in REJECT_REASONS)) return api.answerCallbackQuery(query.id, "Unknown reason", true);
        const result = await service.reject(id, code, reviewerOf(user));
        return answerResult(query, result, "❌ Rejected");
      }
      case "rc": {
        const updated = await service.recheck(id);
        if (!updated) return api.answerCallbackQuery(query.id, "Not found", true);
        const v = updated.verification;
        return api.answerCallbackQuery(
          query.id,
          v.ok ? `Link OK: ${v.channelCount} channels (${v.format})` : `Link check failed: ${v.message}`,
          !v.ok,
        );
      }
      case "td": {
        if (!isAdmin(user)) return api.answerCallbackQuery(query.id, "Only admins can take a playlist down.", true);
        const result = await service.takedown(id, reviewerOf(user), "Taken down from Telegram");
        return answerResult(query, result, "🗑 Taken down");
      }
      default:
        return api.answerCallbackQuery(query.id);
    }
  }

  function answerResult(query, result, successText) {
    if (result.ok) return api.answerCallbackQuery(query.id, successText);
    return api.answerCallbackQuery(query.id, stripTags(notApplied(null, result.current, "in review")), true);
  }

  async function editKeyboard(message, keyboard) {
    try {
      await api.editMessageReplyMarkup(message.chat.id, message.message_id, keyboard);
    } catch (error) {
      if (!/message is not modified/i.test(error.description ?? error.message)) throw error;
    }
  }

  function notApplied(id, current, expected) {
    if (!current) return `Nothing ${id ? `with ID <code>${escapeHtml(id)}</code>` : "with that ID"}.`;
    const by = current.review || current.decision;
    return `Not ${expected}: already ${current.status}${by ? ` by ${escapeHtml(reviewerLabel(by))}` : ""}.`;
  }

  return {
    handleUpdate,
    isAdmin,
    canReview,
    setBotUsername: (name) => {
      botUsername = name;
    },
  };
}

const stripTags = (html) => html.replace(/<[^>]+>/g, "").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");

/** Long-polling loop for local and VPS runs. Stops when `signal` aborts. */
export async function runPolling({ api, bot, log = console, signal }) {
  await api.deleteWebhook();
  let offset = 0;
  log.info?.("telegram polling started");
  while (!signal.aborted) {
    try {
      const updates = await api.getUpdates(
        { offset, timeout: 30, allowed_updates: ["message", "callback_query"] },
        { signal },
      );
      for (const update of updates) {
        offset = update.update_id + 1;
        await bot.handleUpdate(update);
      }
    } catch (error) {
      if (signal.aborted) break;
      log.error?.("telegram polling error", { error: error.message });
      await new Promise((resolve) => setTimeout(resolve, 3000));
    }
  }
  log.info?.("telegram polling stopped");
}
