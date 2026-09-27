import { renderSubmission, reviewKeyboard } from "./render.js";

export const ADMIN_CHATS_STATE = "admin_chats";

/**
 * Posts review cards and keeps every copy in sync. A submission is posted to
 * each admin's private chat and to the review group; the message IDs are stored
 * on the submission so a decision taken in one place updates all of them.
 */
export function createNotifier({ api, config, store, log = console }) {
  async function targets() {
    const learned = (await store.getState(ADMIN_CHATS_STATE)) ?? [];
    const all = [...config.telegram.adminChatIds, ...learned, config.telegram.reviewChatId]
      .filter((id) => id !== undefined && id !== null && String(id).trim() !== "")
      .map(String);
    return [...new Set(all)];
  }

  async function postCard(chatId, submission, image) {
    const caption = renderSubmission(submission);
    const reply_markup = reviewKeyboard(submission);
    if (image) {
      const message = await api.sendPhoto(chatId, image.bytes ?? Buffer.from(image.data, "base64"), {
        mime: image.mime,
        caption,
        parse_mode: "HTML",
        reply_markup,
      });
      return { chatId: String(chatId), messageId: message.message_id, kind: "photo" };
    }
    const message = await api.sendMessage(chatId, caption, {
      parse_mode: "HTML",
      reply_markup,
      link_preview_options: { is_disabled: true },
    });
    return { chatId: String(chatId), messageId: message.message_id, kind: "text" };
  }

  async function announce(submission, image, onPosted = async () => {}) {
    const refs = [];
    for (const chatId of await targets()) {
      try {
        const ref = await postCard(chatId, submission, image);
        refs.push(ref);
        await onPosted(ref);
      } catch (error) {
        // Typically "chat not found": the admin never pressed Start, or the bot
        // was removed from the group. The other chats still get the card.
        log.error?.("telegram post failed", { chatId, id: submission.id, error: error.message });
      }
    }
    return refs;
  }

  async function refresh(submission) {
    const caption = renderSubmission(submission);
    const reply_markup = reviewKeyboard(submission);
    for (const ref of submission.telegramMessages ?? []) {
      try {
        if (ref.kind === "photo") {
          await api.editMessageCaption(ref.chatId, ref.messageId, caption, { parse_mode: "HTML", reply_markup });
        } else {
          await api.editMessageText(ref.chatId, ref.messageId, caption, {
            parse_mode: "HTML",
            reply_markup,
            link_preview_options: { is_disabled: true },
          });
        }
      } catch (error) {
        if (/message is not modified/i.test(error.description ?? error.message)) continue;
        log.error?.("telegram edit failed", { chatId: ref.chatId, id: submission.id, error: error.message });
      }
    }
  }

  return { targets, postCard, announce, refresh };
}
