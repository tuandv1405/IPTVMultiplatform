import { randomBytes } from "node:crypto";
import { ApiError, badRequest, forbidden, notFound, tooMany } from "./errors.js";
import { parseImageDataUrl } from "./image.js";
import {
  CATEGORIES,
  CONTRIBUTOR_DECLARATIONS,
  LIMITS,
  POLICY_VERSION,
  REJECT_REASONS,
  STATUS,
  SUBMISSION_DECLARATIONS,
  allDeclared,
} from "./policy.js";
import { createRateLimiter } from "./rateLimit.js";
import { FetchRefused } from "./verify/fetcher.js";
import { urlHash } from "./verify/verifier.js";

export const newSubmissionId = () => `pl${randomBytes(5).toString("hex")}`;

/**
 * Everything the contributor programme does, independent of HTTP and Telegram.
 * The HTTP layer calls the contributor half, the bot calls the reviewer half,
 * and both go through the same status transitions.
 */
export function createContributorService({
  store,
  vault,
  verifyPlaylist,
  notifier,
  clock = Date.now,
  limits = LIMITS,
  idGen = newSubmissionId,
  log = console,
}) {
  const verifyLimiter = createRateLimiter({ limit: limits.verifyPerMinute, windowMs: 60_000, clock });

  // ---- guards -------------------------------------------------------------

  function requireVerified(identity) {
    if (!identity.email || !identity.emailVerified) {
      throw forbidden("email_not_verified", "Verify your email address first.");
    }
    if (!identity.phone) {
      throw forbidden("phone_not_verified", "Verify your phone number first.");
    }
  }

  async function requireContributor(identity) {
    requireVerified(identity);
    const contributor = await store.getContributor(identity.uid);
    if (!contributor) throw forbidden("not_contributor", "Accept the contributor policy first.");
    if (contributor.status === "BANNED") throw forbidden("banned", "This contributor account has been suspended.");
    if (contributor.policyVersion !== POLICY_VERSION) {
      throw forbidden("policy_outdated", "The contributor policy has changed. Review and accept it again.");
    }
    return contributor;
  }

  // ---- contributor side ---------------------------------------------------

  async function me(identity) {
    const contributor = await store.getContributor(identity.uid);
    return {
      uid: identity.uid,
      email: identity.email,
      emailVerified: identity.emailVerified,
      phoneVerified: Boolean(identity.phone),
      policyVersion: POLICY_VERSION,
      contributor: contributor && {
        publicName: contributor.publicName,
        status: contributor.status,
        policyCurrent: contributor.policyVersion === POLICY_VERSION,
        joinedAt: contributor.createdAt,
      },
    };
  }

  async function register(identity, body = {}) {
    requireVerified(identity);
    if (body.policyVersion !== POLICY_VERSION) {
      throw badRequest("policy_outdated", "The contributor policy has changed. Reload the page.");
    }
    if (!allDeclared(body.declarations, CONTRIBUTOR_DECLARATIONS)) {
      throw badRequest("declarations_required", "Tick every declaration to continue.");
    }
    const publicName = validatePublicName(body.publicName);
    const existing = await store.getContributor(identity.uid);
    if (existing?.status === "BANNED") throw forbidden("banned", "This contributor account has been suspended.");
    // A ban follows the person, not the uid: a new account with the same email
    // or phone number stays suspended.
    const emailHash = vault.hash("email", identity.email);
    const phoneHash = vault.hash("phone", identity.phone);
    const sameContact = [
      ...(await store.findContributorsByHash("emailHash", emailHash)),
      ...(await store.findContributorsByHash("phoneHash", phoneHash)),
    ];
    if (sameContact.some((c) => c.status === "BANNED" && c.uid !== identity.uid)) {
      throw forbidden("banned", "This contributor account has been suspended.");
    }

    const now = clock();
    await store.putContributor(identity.uid, {
      uid: identity.uid,
      publicName,
      // Contact data is only ever stored encrypted; the hashes allow lookups
      // (e.g. "is this phone already banned?") without decrypting.
      emailEnc: vault.encrypt(identity.email),
      emailHash,
      phoneEnc: vault.encrypt(identity.phone),
      phoneHash,
      policyVersion: POLICY_VERSION,
      policyAcceptedAt: now,
      status: "ACTIVE",
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    });
    log.info?.("contributor registered", { uid: identity.uid });
    return me(identity);
  }

  async function verifyLink(identity, url) {
    await requireContributor(identity);
    if (!verifyLimiter(identity.uid)) throw tooMany();
    guardUrl(text(url, "invalid_url", "Enter the playlist link."));
    const result = await verifyPlaylist(url);
    const { contentHash, ...visible } = result;
    return visible;
  }

  async function submit(identity, body = {}) {
    const contributor = await requireContributor(identity);
    const fields = validateSubmission(body);
    if (!allDeclared(body.declarations, SUBMISSION_DECLARATIONS)) {
      throw badRequest("declarations_required", "Confirm that the playlist is your own work.");
    }

    // Checked here to fail fast before fetching the link, and again inside the
    // store's create transaction, which is what actually holds under concurrency.
    checkOwnerLimits(await store.listSubmissionsByOwner(identity.uid), limits);

    const image = parseImageDataUrl(body.image);
    const hash = guardUrl(fields.url);

    // Verified again here: the "Check link" result in the browser is advisory.
    const verification = await verifyPlaylist(fields.url);
    if (!verification.ok) {
      throw badRequest("link_check_failed", verification.message, { reason: verification.code });
    }

    const flags = [];
    for (const other of await store.findSubmissionsByContentHash(verification.contentHash)) {
      if (other.ownerUid !== identity.uid && other.status !== STATUS.WITHDRAWN) {
        flags.push({ type: "same_content", otherId: other.id, otherPublicName: other.publicName, otherStatus: other.status });
      }
    }

    const now = clock();
    const { contentHash, ...verificationSummary } = verification;
    const submission = {
      id: idGen(),
      ownerUid: identity.uid,
      publicName: contributor.publicName,
      ...fields,
      urlHash: hash,
      contentHash,
      verification: verificationSummary,
      declarations: { keys: SUBMISSION_DECLARATIONS, policyVersion: POLICY_VERSION, acceptedAt: now },
      flags,
      status: STATUS.IN_REVIEW,
      statusReason: null,
      reviewedBy: null,
      reviewedAt: null,
      telegramMessages: [],
      createdAt: now,
      updatedAt: now,
    };

    await store.createSubmission(submission, (own) => checkOwnerLimits(own, limits));
    await store.putImage("submission", submission.id, { mime: image.mime, data: image.data });
    log.info?.("submission created", { id: submission.id, uid: identity.uid, flags: flags.length });

    try {
      // Each card is recorded as soon as it is posted, so a reviewer who taps
      // the first card while later ones are still sending still has it updated.
      await notifier.announce(submission, image, (ref) => addTelegramMessages(submission.id, [ref]));
      // A decision taken during the announce may have been rendered before the
      // later cards existed; bring every card in line with the final state.
      const current = await store.getSubmission(submission.id);
      if (current && current.status !== STATUS.IN_REVIEW) await refresh(current);
    } catch (error) {
      // The submission stands; /pending in Telegram re-posts anything not announced.
      log.error?.("telegram announce failed", { id: submission.id, error: error.message });
    }
    return ownerView(submission);
  }

  async function listOwn(identity) {
    const own = await store.listSubmissionsByOwner(identity.uid);
    return own.map(ownerView);
  }

  async function withdraw(identity, id) {
    const submission = await store.getSubmission(id);
    if (!submission || submission.ownerUid !== identity.uid) throw notFound();
    const result = await store.transitionSubmission(id, [STATUS.IN_REVIEW, STATUS.APPROVED], {
      status: STATUS.WITHDRAWN,
      statusReason: { code: "withdrawn" },
      updatedAt: clock(),
    });
    if (!result.ok) throw badRequest("cannot_withdraw", "This playlist can no longer be withdrawn.");
    // Unconditional: the status read above may predate an approve that committed
    // just before this transaction. Deleting an absent public doc is harmless.
    await unpublish(id);
    await store.releaseUrl(submission.urlHash, id);
    await refresh(result.current);
    return ownerView(result.current);
  }

  /**
   * Called by /delete-account/ before the Firebase account is deleted. Removes
   * the encrypted contact data and withdraws every live playlist. A suspended
   * contributor keeps a tombstone (status + keyed hashes, no ciphertext), so a
   * ban is not undone by deleting and recreating the account.
   */
  async function deleteMe(identity) {
    return purgeContributor(identity.uid);
  }

  /**
   * Safety net for account deletions whose purge call never reached us (the
   * delete page does not block on it): every contributor whose Firebase account
   * no longer exists is purged. `accountExists(uid)` is answered by Firebase Auth.
   */
  async function sweepDeletedAccounts(accountExists, { maxPerRun = 20 } = {}) {
    let purged = 0;
    for (const uid of await store.listContributorUids()) {
      // Real deletions trickle in; a burst means something is misconfigured.
      if (purged >= maxPerRun) {
        log.warn?.("account sweep stopped at its per-run cap", { maxPerRun });
        break;
      }
      const contributor = await store.getContributor(uid);
      if (!contributor || contributor.deletedAt) continue; // already a tombstone
      if (await accountExists(uid)) continue;
      await purgeContributor(uid);
      purged++;
    }
    if (purged) log.info?.("swept deleted accounts", { purged });
    return purged;
  }

  async function purgeContributor(uid) {
    const identity = { uid };
    const now = clock();
    for (const submission of await store.listSubmissionsByOwner(identity.uid)) {
      const result = await store.transitionSubmission(submission.id, [STATUS.IN_REVIEW, STATUS.APPROVED], {
        status: STATUS.WITHDRAWN,
        statusReason: { code: "account_deleted" },
        updatedAt: now,
      });
      await unpublish(submission.id);
      await store.deleteImage("submission", submission.id);
      if (result.ok) {
        await store.releaseUrl(submission.urlHash, submission.id);
        await refresh(result.current);
      }
    }
    const contributor = await store.getContributor(identity.uid);
    if (contributor) {
      await store.deleteContributor(identity.uid);
      if (contributor.status === "BANNED") {
        await store.putContributor(identity.uid, {
          uid: identity.uid,
          status: "BANNED",
          bannedAt: contributor.bannedAt ?? now,
          emailHash: contributor.emailHash,
          phoneHash: contributor.phoneHash,
          deletedAt: now,
        });
      }
    }
    log.info?.("contributor data deleted", { uid: identity.uid, wasContributor: Boolean(contributor) });
    return { deleted: true };
  }

  // ---- reviewer side (Telegram) ------------------------------------------

  async function approve(id, reviewer) {
    const now = clock();
    const result = await store.transitionSubmission(id, [STATUS.IN_REVIEW], {
      status: STATUS.APPROVED,
      statusReason: null,
      reviewedBy: reviewer,
      reviewedAt: now,
      approvedAt: now,
      updatedAt: now,
    });
    if (!result.ok) return result;
    await publish(result.current);
    await refresh(result.current);
    return result;
  }

  async function reject(id, reasonCode, reviewer) {
    if (!(reasonCode in REJECT_REASONS)) throw badRequest("invalid_reason", "Unknown reason.");
    const now = clock();
    const result = await store.transitionSubmission(id, [STATUS.IN_REVIEW], {
      status: STATUS.REJECTED,
      statusReason: { code: reasonCode },
      reviewedBy: reviewer,
      reviewedAt: now,
      updatedAt: now,
    });
    if (!result.ok) return result;
    // A link rejected as copied stays locked so it cannot simply be resubmitted.
    if (reasonCode !== "copyright") await store.releaseUrl(result.current.urlHash, id);
    await refresh(result.current);
    return result;
  }

  async function takedown(id, reviewer, note = "") {
    const now = clock();
    const result = await store.transitionSubmission(id, [STATUS.APPROVED], {
      status: STATUS.REMOVED,
      statusReason: { code: "removed", note: String(note).slice(0, 300) },
      removedBy: reviewer,
      removedAt: now,
      updatedAt: now,
    });
    if (!result.ok) return result;
    await unpublish(id);
    await refresh(result.current);
    return result;
  }

  async function ban(uid, reviewer, note = "") {
    const contributor = await store.getContributor(uid);
    if (!contributor) return { ok: false, affected: [] };
    const now = clock();
    await store.putContributor(uid, {
      status: "BANNED",
      bannedAt: now,
      bannedBy: reviewer,
      banNote: String(note).slice(0, 300),
      updatedAt: now,
    });
    const affected = [];
    for (const submission of await store.listSubmissionsByOwner(uid)) {
      let result = null;
      let status = submission.status;
      if (status === STATUS.IN_REVIEW) {
        result = await store.transitionSubmission(submission.id, [STATUS.IN_REVIEW], {
          status: STATUS.REJECTED,
          statusReason: { code: "banned" },
          reviewedBy: reviewer,
          reviewedAt: now,
          updatedAt: now,
        });
        // Approved while we were looping: remove it instead.
        if (!result.ok && result.current?.status === STATUS.APPROVED) status = STATUS.APPROVED;
      }
      if (status === STATUS.APPROVED) {
        result = await store.transitionSubmission(submission.id, [STATUS.APPROVED], {
          status: STATUS.REMOVED,
          statusReason: { code: "banned", note: String(note).slice(0, 300) },
          removedBy: reviewer,
          removedAt: now,
          updatedAt: now,
        });
        if (result.ok) await unpublish(submission.id);
      }
      if (result?.ok) {
        affected.push(result.current);
        await refresh(result.current);
      }
    }
    log.info?.("contributor banned", { uid, affected: affected.length });
    return { ok: true, affected };
  }

  /** Decrypted contact data. Only the bot's admin-only, private-chat /contact reaches this. */
  async function contact(id) {
    const submission = await store.getSubmission(id);
    if (!submission) return null;
    const contributor = await store.getContributor(submission.ownerUid);
    if (!contributor) return null;
    return {
      uid: submission.ownerUid,
      publicName: contributor.publicName,
      email: vault.decrypt(contributor.emailEnc),
      phone: vault.decrypt(contributor.phoneEnc),
    };
  }

  async function recheck(id) {
    const submission = await store.getSubmission(id);
    if (!submission) return null;
    const verification = await verifyPlaylist(submission.url);
    const { contentHash, ...summary } = verification;
    await store.patchSubmission(id, { verification: summary, updatedAt: clock() });
    const updated = { ...submission, verification: summary };
    await refresh(updated);
    return updated;
  }

  // ---- helpers -------------------------------------------------------------

  async function publish(submission) {
    await store.putPublicPlaylist(submission.id, publicView(submission));
    const image = await store.getImage("submission", submission.id);
    if (image) await store.putImage("public", submission.id, image);
    // A withdraw, take-down or ban may have committed between the approve
    // transaction and the writes above; the public copy must not outlive it.
    const current = await store.getSubmission(submission.id);
    if (current?.status !== STATUS.APPROVED) await unpublish(submission.id);
  }

  async function addTelegramMessages(id, messages) {
    const submission = await store.getSubmission(id);
    if (!submission) return;
    await store.patchSubmission(id, { telegramMessages: [...(submission.telegramMessages ?? []), ...messages] });
  }

  async function unpublish(id) {
    await store.deletePublicPlaylist(id);
    await store.deleteImage("public", id);
  }

  async function refresh(submission) {
    try {
      await notifier.refresh(submission);
    } catch (error) {
      log.error?.("telegram refresh failed", { id: submission.id, error: error.message });
    }
  }

  return {
    me,
    register,
    verifyLink,
    submit,
    listOwn,
    withdraw,
    deleteMe,
    sweepDeletedAccounts,
    approve,
    reject,
    takedown,
    ban,
    contact,
    recheck,
    getSubmission: (id) => store.getSubmission(id),
    getImage: (id) => store.getImage("submission", id),
    pending: (limit = 10) => store.listSubmissionsByStatus(STATUS.IN_REVIEW, limit),
    stats: () => store.countByStatus(),
    addTelegramMessages,
  };
}

// ---- validation ------------------------------------------------------------

function guardUrl(url) {
  try {
    return urlHash(url);
  } catch (error) {
    if (error instanceof FetchRefused) throw badRequest(error.code, error.message);
    throw error;
  }
}

export function validatePublicName(raw) {
  const name = text(raw, "invalid_public_name", "Enter a public name.").trim().replace(/\s+/g, " ");
  if (name.length < 2 || name.length > 40) {
    throw badRequest("invalid_public_name", "The public name must be 2–40 characters.");
  }
  if (!/^[\p{L}\p{N} ._-]+$/u.test(name)) {
    throw badRequest("invalid_public_name", "Use letters, numbers, spaces, dots, dashes or underscores.");
  }
  // A public name must not become a way to publish contact details.
  if (/\d[\d .-]{6,}\d/.test(name)) {
    throw badRequest("invalid_public_name", "The public name must not contain a phone number.");
  }
  return name;
}

function checkOwnerLimits(own, limits) {
  const inReview = own.filter((s) => s.status === STATUS.IN_REVIEW).length;
  const live = own.filter((s) => s.status === STATUS.IN_REVIEW || s.status === STATUS.APPROVED).length;
  if (inReview >= limits.maxInReview) {
    throw tooMany("too_many_in_review", `You can have ${limits.maxInReview} playlists in review at a time.`);
  }
  if (live >= limits.maxLive) {
    throw tooMany("too_many_live", `You can have ${limits.maxLive} playlists published or in review.`);
  }
}

/** Strings only: String() on a crafted object can throw, or turn into "[object Object]". */
function text(value, code, message) {
  if (value === undefined || value === null) return "";
  if (typeof value !== "string") throw badRequest(code, message);
  return value;
}

function validateSubmission(body) {
  const name = text(body.name, "invalid_name", "Enter the playlist name.").trim().replace(/\s+/g, " ");
  if (name.length < 3 || name.length > 80) throw badRequest("invalid_name", "The playlist name must be 3–80 characters.");
  const url = text(body.url, "invalid_url", "Enter the playlist link.").trim();
  if (!url || url.length > 2048) throw badRequest("invalid_url", "Enter the playlist link.");
  const description = text(body.description, "invalid_description", "The description must be text.").trim();
  if (description.length > 500) throw badRequest("invalid_description", "The description must be 500 characters or fewer.");
  const category = text(body.category, "invalid_category", "Choose a category.");
  if (!CATEGORIES.includes(category)) throw badRequest("invalid_category", "Choose a category.");
  const language = text(body.language, "invalid_language", "Choose a language.").toLowerCase();
  if (!/^([a-z]{2,3}|multi)$/.test(language)) throw badRequest("invalid_language", "Choose a language.");
  return { name, url, description, category, language };
}

/** What the contributor sees of their own submission. */
export function ownerView(submission) {
  return {
    id: submission.id,
    name: submission.name,
    url: submission.url,
    description: submission.description,
    category: submission.category,
    language: submission.language,
    status: submission.status,
    statusReason: submission.statusReason,
    channelCount: submission.verification?.channelCount ?? null,
    format: submission.verification?.format ?? null,
    createdAt: submission.createdAt,
    reviewedAt: submission.reviewedAt ?? null,
  };
}

/** What everybody sees once approved. No UID, no contact data, no review metadata. */
export function publicView(submission) {
  return {
    id: submission.id,
    name: submission.name,
    url: submission.url,
    description: submission.description,
    category: submission.category,
    language: submission.language,
    publicName: submission.publicName,
    channelCount: submission.verification?.channelCount ?? null,
    groupCount: submission.verification?.groupCount ?? null,
    format: submission.verification?.format ?? null,
    approvedAt: submission.approvedAt ?? submission.reviewedAt ?? null,
  };
}

export { ApiError };
