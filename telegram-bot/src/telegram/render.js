import { REJECT_REASONS, STATUS } from "../policy.js";

export const escapeHtml = (value) =>
  String(value ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

const STATUS_LINE = {
  [STATUS.IN_REVIEW]: "🟡 <b>IN REVIEW</b>",
  [STATUS.APPROVED]: "✅ <b>APPROVED</b>",
  [STATUS.REJECTED]: "❌ <b>REJECTED</b>",
  [STATUS.WITHDRAWN]: "↩️ <b>WITHDRAWN</b> by the contributor",
  [STATUS.REMOVED]: "🗑 <b>REMOVED</b> by the publisher",
};

// Telegram names run to 129 characters; capped so the card stays under the caption limit.
export const reviewerLabel = (reviewer) =>
  reviewer
    ? truncate(reviewer.username ? `@${reviewer.username}` : reviewer.name || `id ${reviewer.id}`, 40)
    : "?";

/**
 * The review card. Kept under Telegram's 1024-character caption limit: the
 * same text is used as a photo caption and, without an image, as a message.
 */
export function renderSubmission(submission) {
  const v = submission.verification ?? {};
  const lines = [
    STATUS_LINE[submission.status] ?? submission.status,
    `<b>${escapeHtml(truncate(submission.name, 80))}</b>`,
    `🔗 <code>${escapeHtml(truncate(submission.url, 180))}</code>`,
  ];
  if (v.ok) {
    lines.push(`📺 ${v.channelCount} channels · ${v.groupCount} groups · ${String(v.format ?? "").toUpperCase()}`);
  } else if (v.code) {
    lines.push(`⚠️ Link check failed: ${escapeHtml(v.code)}`);
  }
  lines.push(`🏷 ${escapeHtml(submission.category)} · ${escapeHtml(submission.language)}`);
  lines.push(`👤 ${escapeHtml(submission.publicName)} · uid <code>${escapeHtml(submission.ownerUid)}</code>`);
  if (submission.description) lines.push(`📝 ${escapeHtml(truncate(submission.description, 200))}`);
  const flags = (submission.flags ?? []).filter((flag) => flag.type === "same_content");
  for (const flag of flags.slice(0, 2)) {
    lines.push(
      `🚩 <b>Identical channel list</b> to <code>${escapeHtml(flag.otherId)}</code> by ${escapeHtml(truncate(flag.otherPublicName, 40))} (${escapeHtml(flag.otherStatus)}) — possible copy`,
    );
  }
  if (flags.length > 2) lines.push(`🚩 …and ${flags.length - 2} more identical lists`);
  lines.push(`🆔 <code>${escapeHtml(submission.id)}</code>`);

  if (submission.status === STATUS.APPROVED || submission.status === STATUS.REJECTED) {
    const reason = submission.statusReason?.code
      ? ` — ${escapeHtml(REJECT_REASONS[submission.statusReason.code] ?? submission.statusReason.code)}`
      : "";
    lines.push(`🧑‍⚖️ ${escapeHtml(reviewerLabel(submission.reviewedBy))}${reason}`);
  }
  if (submission.status === STATUS.REMOVED) {
    const note = submission.statusReason?.note ? ` — ${escapeHtml(truncate(submission.statusReason.note, 120))}` : "";
    lines.push(`🧑‍⚖️ ${escapeHtml(reviewerLabel(submission.removedBy))}${note}`);
  }
  // Fields are length-capped, but a card can still combine every long field.
  // Drop whole optional lines (never cut HTML, which could split a tag) until
  // the visible text fits Telegram's 1024-character caption limit.
  const optional = (line) => line.startsWith("📝") || line.startsWith("🚩");
  while (visibleLength(lines.join("\n")) > CAPTION_LIMIT) {
    const index = lines.findIndex(optional);
    if (index < 0) break;
    lines.splice(index, 1);
  }
  return lines.join("\n");
}

const CAPTION_LIMIT = 1024;

export const visibleLength = (html) =>
  html.replace(/<[^>]+>/g, "").replace(/&(lt|gt|amp);/g, "_").length;

export function reviewKeyboard(submission) {
  if (submission.status === STATUS.IN_REVIEW) {
    return {
      inline_keyboard: [
        [
          { text: "✅ Approve", callback_data: `ap:${submission.id}` },
          { text: "❌ Reject", callback_data: `rj:${submission.id}` },
        ],
        [{ text: "🔄 Re-check link", callback_data: `rc:${submission.id}` }],
      ],
    };
  }
  if (submission.status === STATUS.APPROVED) {
    return { inline_keyboard: [[{ text: "🗑 Take down (admin)", callback_data: `td:${submission.id}` }]] };
  }
  return { inline_keyboard: [] };
}

export function rejectReasonKeyboard(submissionId) {
  const rows = Object.entries(REJECT_REASONS).map(([code, label]) => [
    { text: label, callback_data: `rr:${submissionId}:${code}` },
  ]);
  rows.push([{ text: "⬅️ Back", callback_data: `bk:${submissionId}` }]);
  return { inline_keyboard: rows };
}

function truncate(text, max) {
  const value = String(text ?? "");
  return value.length <= max ? value : `${value.slice(0, max - 1)}…`;
}
