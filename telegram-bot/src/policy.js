/**
 * The contributor policy the web app shows and the server enforces.
 * Bump POLICY_VERSION whenever the text on /contributor/ changes in substance:
 * every contributor must then accept it again before their next submission.
 */
export const POLICY_VERSION = "2026-09-27";

/** Ticked once, when becoming a contributor. */
export const CONTRIBUTOR_DECLARATIONS = [
  "contact_accurate", // the email and phone are mine and reachable
  "accept_policy", // I have read and accept the contributor policy
  "accept_takedown", // the publisher may remove my playlists and my access at any time
];

/** Ticked for every playlist. */
export const SUBMISSION_DECLARATIONS = [
  "own_work", // I created and maintain this playlist myself
  "not_copied", // it is not copied, scraped or taken from another person or service
  "rights_ok", // I have the right to share every link in it; infringing lists may be removed without notice
];

export const CATEGORIES = [
  "general",
  "news",
  "sports",
  "movies",
  "kids",
  "music",
  "documentary",
  "education",
  "religious",
  "regional",
  "other",
];

/** Reasons a reviewer can pick when rejecting. The web app has the matching text. */
export const REJECT_REASONS = {
  copyright: "©️ Copyright / copied",
  broken: "🔗 Broken or unsafe link",
  inappropriate: "🔞 Inappropriate content",
  incomplete: "📄 Incomplete or misleading info",
  duplicate: "♻️ Duplicate",
  other: "❔ Other",
};

export const STATUS = Object.freeze({
  IN_REVIEW: "IN_REVIEW",
  APPROVED: "APPROVED",
  REJECTED: "REJECTED",
  WITHDRAWN: "WITHDRAWN",
  REMOVED: "REMOVED",
});

export const LIMITS = Object.freeze({
  maxInReview: 3,
  maxLive: 20,
  verifyPerMinute: 10,
});

export function allDeclared(declarations, keys) {
  return keys.every((key) => declarations && declarations[key] === true);
}
