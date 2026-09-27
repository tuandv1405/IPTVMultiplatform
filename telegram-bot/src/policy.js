/**
 * The contributor policy the web app shows and the server enforces.
 * Bump POLICY_VERSION whenever the text on /contributor/ changes in substance:
 * every contributor must then accept it again before their next submission.
 */
export const POLICY_VERSION = "2026-09-27";

/** Accepted with every contributor request. Same values as POLICY_VERSIONS in
 *  web/public/assets/contributor-config.js. */
export const POLICY_VERSIONS = { policy: POLICY_VERSION, terms: "2026-09-16", privacy: "2026-09-27" };

export const REQUEST_STATUS = Object.freeze({ PENDING: "PENDING", APPROVED: "APPROVED", REJECTED: "REJECTED" });
export const CONTRIBUTOR_STATUS = Object.freeze({ ACTIVE: "ACTIVE", SUSPENDED: "SUSPENDED" });

/** Quick reasons a Telegram reviewer can pick when rejecting a request. */
export const REQUEST_REJECT_REASONS = {
  incomplete: "📄 Not enough information",
  not_eligible: "🚫 Not eligible",
  other: "❔ Other",
};

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
