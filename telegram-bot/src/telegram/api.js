export class TelegramError extends Error {
  constructor(method, description, code) {
    super(`${method}: ${description}`);
    this.method = method;
    this.description = description;
    this.errorCode = code;
  }
}

/** Minimal Telegram Bot API client over fetch. No SDK: the surface used here is small. */
export function createTelegramApi({ token, fetchImpl = fetch, baseUrl = "https://api.telegram.org" }) {
  const endpoint = (method) => `${baseUrl}/bot${token}/${method}`;

  async function parse(method, response) {
    let body;
    try {
      body = await response.json();
    } catch {
      throw new TelegramError(method, `HTTP ${response.status}`, response.status);
    }
    if (!body.ok) throw new TelegramError(method, body.description ?? "unknown error", body.error_code);
    return body.result;
  }

  async function call(method, params = {}, { signal } = {}) {
    const response = await fetchImpl(endpoint(method), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(params),
      signal,
    });
    return parse(method, response);
  }

  async function sendPhoto(chatId, photo, { mime = "image/jpeg", ...params } = {}) {
    const form = new FormData();
    form.set("chat_id", String(chatId));
    for (const [key, value] of Object.entries(params)) {
      if (value === undefined) continue;
      form.set(key, typeof value === "string" ? value : JSON.stringify(value));
    }
    const extension = mime.split("/")[1] ?? "jpg";
    form.set("photo", new Blob([photo], { type: mime }), `playlist.${extension}`);
    const response = await fetchImpl(endpoint("sendPhoto"), { method: "POST", body: form });
    return parse("sendPhoto", response);
  }

  return {
    call,
    sendPhoto,
    getMe: () => call("getMe"),
    sendMessage: (chatId, text, extra = {}) => call("sendMessage", { chat_id: chatId, text, ...extra }),
    editMessageText: (chatId, messageId, text, extra = {}) =>
      call("editMessageText", { chat_id: chatId, message_id: messageId, text, ...extra }),
    editMessageCaption: (chatId, messageId, caption, extra = {}) =>
      call("editMessageCaption", { chat_id: chatId, message_id: messageId, caption, ...extra }),
    editMessageReplyMarkup: (chatId, messageId, replyMarkup) =>
      call("editMessageReplyMarkup", { chat_id: chatId, message_id: messageId, reply_markup: replyMarkup }),
    answerCallbackQuery: (id, text, showAlert = false) =>
      call("answerCallbackQuery", { callback_query_id: id, text, show_alert: showAlert }),
    getUpdates: (params, options) => call("getUpdates", params, options),
    setWebhook: (params) => call("setWebhook", params),
    deleteWebhook: () => call("deleteWebhook", { drop_pending_updates: false }),
    setMyCommands: (commands, scope) => call("setMyCommands", { commands, ...(scope ? { scope } : {}) }),
  };
}
