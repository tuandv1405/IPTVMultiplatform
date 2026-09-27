import { randomBytes } from "node:crypto";
import { ApiError, badRequest, forbidden, notFound, tooMany, conflict } from "./errors.js";
import { parseImageDataUrl } from "./image.js";
import {
  CATEGORIES,
  CONTRIBUTOR_STATUS,
  LIMITS,
  POLICY_VERSION,
  REJECT_REASONS,
  REQUEST_STATUS,
  STATUS,
  SUBMISSION_DECLARATIONS,
  allDeclared,
} from "./policy.js";
import { createRateLimiter } from "./rateLimit.js";
import { FetchRefused } from "./verify/fetcher.js";
import { normaliseUrl, urlHash } from "./verify/verifier.js";
import { CONTACT_ALG, decryptContact } from "./contactCrypto.js";

export const newSubmissionId = () => `pl${randomBytes(5).toString("hex")}`;

/**
 * The contributor programme on the own server (docs/prd-contributor-requests.md).
 * Same operations and document shapes as the Firestore backend of the web pages
 * (web/public/assets/contributor-backend.js), so data can move between them.
 *
 * Who is who:
 *   user         — any caller with a verified email (Google sign-in)
 *   contributor  — contributors/{uid}.status == ACTIVE, created only when an admin approves a request
 *   admin        — email in ADMIN_EMAILS, or the `admin` custom claim; or a Telegram reviewer
 */
export function createContributorService({
  store,
  verifyPlaylist,
  notifier,
  adminEmails = [],
  contactPrivateKey = null, // CryptoKey; enables Telegram /contact
  clock = Date.now,
  limits = LIMITS,
  idGen = newSubmissionId,
  log = console,
}) {
  const verifyLimiter = createRateLimiter({ limit: limits.verifyPerMinute, windowMs: 60_000, clock });
  const admins = new Set(adminEmails.map((e) => e.toLowerCase()));

  // ---- guards -------------------------------------------------------------

  // Same as isAdmin() in firestore.rules: the claim, or a listed email signed in with Google.
  const isAdmin = (identity) =>
    identity?.claims?.admin === true ||
    (identity?.emailVerified && identity.provider === "google.com" && admins.has(String(identity.email).toLowerCase()));

  function requireUser(identity) {
    if (!identity?.email || !identity.emailVerified) {
      throw forbidden("email_not_verified", "Sign in with a verified Google account.");
    }
  }

  function requireAdmin(identity) {
    if (!isAdmin(identity)) throw forbidden("forbidden", "Admins only.");
  }

  async function requireContributor(identity) {
    requireUser(identity);
    const contributor = await store.getContributor(identity.uid);
    if (!contributor) throw forbidden("not_contributor", "Only approved contributors can upload playlists.");
    if (contributor.status !== CONTRIBUTOR_STATUS.ACTIVE) throw forbidden("suspended", "This contributor account is suspended.");
    return contributor;
  }

  // Users can read decisions on their own records: record the admin by uid with a
  // neutral label, never by personal email.
  const adminReviewer = (identity) => ({ by: identity.uid, name: "Admin" });

  // ---- me --------------------------------------------------------------------

  async function me(identity) {
    return {
      uid: identity.uid,
      email: identity.email,
      emailVerified: identity.emailVerified,
      isAdmin: isAdmin(identity),
      contributor: await store.getContributor(identity.uid),
      request: requestView(await store.getRequest(identity.uid)),
    };
  }

  // ---- contributor requests ---------------------------------------------------

  async function createRequest(identity, body = {}) {
    requireUser(identity);
    // Same as isGoogle() in firestore.rules: contributors sign in with Google.
    if (identity.provider !== "google.com") throw forbidden("google_required", "Sign in with Google to apply.");
    const fields = validateRequest(body);
    // An approved (or suspended) contributor never applies again — otherwise a new
    // approval would quietly lift a suspension.
    if (await store.getContributor(identity.uid)) throw conflict("already_contributor", "You are already a contributor.");
    const now = clock();
    const saved = await store.saveRequest(identity.uid, (existing) => {
      // One request at a time: a new one only after the last was rejected.
      if (existing?.status === REQUEST_STATUS.PENDING) throw conflict("request_pending", "You already have a request waiting for review.");
      if (existing?.status === REQUEST_STATUS.APPROVED) throw conflict("already_contributor", "You are already a contributor.");
      return {
        uid: identity.uid,
        ...fields,
        consents: { ...fields.consents, acceptedAt: now },
        status: REQUEST_STATUS.PENDING,
        attempt: (existing?.attempt ?? 0) + 1,
        telegramMessages: [],
        createdAt: now,
        updatedAt: now,
      };
    });
    log.info?.("contributor request", { uid: identity.uid, attempt: saved.attempt });
    try {
      await notifier.announceRequest?.(saved, (ref) => addRequestMessages(identity.uid, [ref]));
    } catch (error) {
      log.error?.("telegram request announce failed", { uid: identity.uid, error: error.message });
    }
    return requestView(saved);
  }

  async function getMyRequest(identity) {
    return requestView(await store.getRequest(identity.uid));
  }

  async function cancelRequest(identity) {
    const existing = await store.getRequest(identity.uid);
    if (!existing) throw notFound();
    await store.deleteRequest(identity.uid);
    await refreshRequest({ ...existing, status: "CANCELLED" });
    return { cancelled: true };
  }

  async function listRequests(identity, status) {
    requireAdmin(identity);
    return (await store.listRequests(status || null)).map(requestView);
  }

  /** decision: "APPROVED" | "REJECTED". `reviewer` = { by, name }. */
  async function decideRequest(uid, decision, reason, reviewer) {
    if (![REQUEST_STATUS.APPROVED, REQUEST_STATUS.REJECTED].includes(decision)) throw badRequest("invalid_decision", "Unknown decision.");
    if (decision === REQUEST_STATUS.APPROVED && (await store.getContributor(uid))) {
      throw conflict("already_contributor", "This user is already a contributor (possibly suspended).");
    }
    const now = clock();
    const result = await store.transitionRequest(uid, [REQUEST_STATUS.PENDING], {
      status: decision,
      decision: { ...reviewer, at: now, reason: String(reason ?? "").slice(0, 500) },
      updatedAt: now,
    });
    if (!result.ok) return result;
    if (decision === REQUEST_STATUS.APPROVED) {
      await store.putContributor(uid, {
        uid,
        publicName: result.current.publicName,
        status: CONTRIBUTOR_STATUS.ACTIVE,
        approvedAt: now,
        approvedBy: reviewer.by,
        updatedAt: now,
      });
    }
    log.info?.("contributor request decided", { uid, decision });
    await refreshRequest(result.current);
    return result;
  }

  async function adminDecideRequest(identity, uid, body = {}) {
    requireAdmin(identity);
    const result = await decideRequest(uid, body.decision, body.reason, adminReviewer(identity));
    if (!result.ok) throw conflict("already_decided", `This request is ${result.current?.status ?? "gone"}.`);
    return requestView(result.current);
  }

  async function listContributors(identity) {
    requireAdmin(identity);
    return store.listContributors();
  }

  async function setContributorStatus(uid, status, note, reviewer) {
    if (!Object.values(CONTRIBUTOR_STATUS).includes(status)) throw badRequest("invalid_status", "Unknown status.");
    const contributor = await store.getContributor(uid);
    if (!contributor) throw notFound();
    const now = clock();
    await store.putContributor(uid, { status, statusNote: String(note ?? "").slice(0, 300), updatedAt: now });
    const affected = [];
    if (status === CONTRIBUTOR_STATUS.SUSPENDED) {
      // Suspension withdraws the contributor's work from review and from the directory.
      for (const submission of await store.listSubmissionsByOwner(uid)) {
        let result = null;
        if (submission.status === STATUS.IN_REVIEW) {
          result = await store.transitionSubmission(submission.id, [STATUS.IN_REVIEW], {
            status: STATUS.REJECTED, statusReason: { code: "banned" }, review: { ...reviewer, at: now }, updatedAt: now,
          });
          if (!result.ok && result.current?.status === STATUS.APPROVED) submission.status = STATUS.APPROVED;
        }
        if (submission.status === STATUS.APPROVED) {
          result = await store.transitionSubmission(submission.id, [STATUS.APPROVED], {
            status: STATUS.REMOVED, statusReason: { code: "banned", note: String(note ?? "").slice(0, 300) }, review: { ...reviewer, at: now }, updatedAt: now,
          });
          if (result.ok) await unpublish(submission.id);
        }
        if (result?.ok) {
          affected.push(result.current);
          await refresh(result.current);
        }
      }
    }
    log.info?.("contributor status", { uid, status, affected: affected.length });
    return { ok: true, contributor: await store.getContributor(uid), affected };
  }

  async function adminSetContributorStatus(identity, uid, body = {}) {
    requireAdmin(identity);
    return (await setContributorStatus(uid, body.status, body.note, adminReviewer(identity))).contributor;
  }

  // ---- playlists: contributor side -------------------------------------------

  async function verifyLink(identity, url) {
    await requireContributor(identity);
    if (!verifyLimiter(identity.uid)) throw tooMany();
    guardUrl(text(url, "invalid_url", "Enter the playlist link."));
    const { contentHash, ...visible } = await verifyPlaylist(url);
    return visible;
  }

  async function submit(identity, body = {}) {
    const contributor = await requireContributor(identity);
    const fields = validateSubmission(body);
    if (!allDeclared(body.declarations, SUBMISSION_DECLARATIONS)) {
      throw badRequest("declarations_required", "Confirm that the playlist is your own work.");
    }
    // Fail fast here; the store's create transaction re-checks under concurrency.
    checkOwnerLimits(await store.listSubmissionsByOwner(identity.uid), limits);

    const image = parseImageDataUrl(body.image);
    const hash = guardUrl(fields.url);
    // Stored normalised, like the Firestore backend (whose rules hash this exact string).
    fields.url = normaliseUrl(fields.url);
    // On the own server the link is verified for real, and a failure blocks the upload.
    const verification = await verifyPlaylist(fields.url);
    if (!verification.ok) throw badRequest("link_check_failed", verification.message, { reason: verification.code });

    const flags = [];
    for (const other of await store.findSubmissionsByContentHash(verification.contentHash)) {
      if (other.ownerUid !== identity.uid && other.status !== STATUS.WITHDRAWN) {
        flags.push({ type: "same_content", otherId: other.id, otherPublicName: other.publicName, otherStatus: other.status });
      }
    }

    const now = clock();
    const { contentHash, ...summary } = verification;
    const submission = {
      id: idGen(),
      ownerUid: identity.uid,
      publicName: contributor.publicName,
      ...fields,
      urlHash: hash,
      contentHash,
      verification: { ...summary, method: "server" },
      declarations: { own_work: true, not_copied: true, rights_ok: true, policyVersion: POLICY_VERSION },
      flags,
      status: STATUS.IN_REVIEW,
      statusReason: null,
      review: null,
      telegramMessages: [],
      createdAt: now,
      updatedAt: now,
    };
    await store.createSubmission(submission, (own) => checkOwnerLimits(own, limits));
    await store.putImage("submission", submission.id, { mime: image.mime, data: image.data });
    log.info?.("submission created", { id: submission.id, uid: identity.uid, flags: flags.length });

    try {
      // Each card is recorded as it is posted, so an early decision updates it.
      await notifier.announce(submission, image, (ref) => addTelegramMessages(submission.id, [ref]));
      const current = await store.getSubmission(submission.id);
      if (current && current.status !== STATUS.IN_REVIEW) await refresh(current);
    } catch (error) {
      log.error?.("telegram announce failed", { id: submission.id, error: error.message });
    }
    return ownerView(submission);
  }

  async function listOwn(identity) {
    return (await store.listSubmissionsByOwner(identity.uid)).map(ownerView);
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
    await unpublish(id); // unconditional: an approve may have committed just before
    await store.releaseUrl(submission.urlHash, id);
    await refresh(result.current);
    return ownerView(result.current);
  }

  // ---- playlists: reviewer side (admin console + Telegram) ------------------

  async function approve(id, reviewer) {
    const now = clock();
    const result = await store.transitionSubmission(id, [STATUS.IN_REVIEW], {
      status: STATUS.APPROVED, statusReason: null, review: { ...reviewer, at: now }, approvedAt: now, updatedAt: now,
    });
    if (!result.ok) return result;
    await publish(result.current);
    await refresh(result.current);
    return result;
  }

  async function reject(id, reasonCode, reviewer, note = "") {
    if (!(reasonCode in REJECT_REASONS)) throw badRequest("invalid_reason", "Unknown reason.");
    const now = clock();
    const result = await store.transitionSubmission(id, [STATUS.IN_REVIEW], {
      status: STATUS.REJECTED, statusReason: { code: reasonCode, note: String(note).slice(0, 300) }, review: { ...reviewer, at: now }, updatedAt: now,
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
      status: STATUS.REMOVED, statusReason: { code: "removed", note: String(note).slice(0, 300) }, review: { ...reviewer, at: now }, updatedAt: now,
    });
    if (!result.ok) return result;
    await unpublish(id);
    await refresh(result.current);
    return result;
  }

  async function listSubmissions(identity, status) {
    requireAdmin(identity);
    // Newest first, like the Firestore backend of the admin console.
    return (await store.listSubmissionsByStatus(status || null, 500, { newestFirst: true })).map(adminView);
  }

  async function adminDecideSubmission(identity, id, body = {}) {
    requireAdmin(identity);
    const reviewer = adminReviewer(identity);
    const result =
      body.action === "approve" ? await approve(id, reviewer)
      : body.action === "reject" ? await reject(id, text(body.code, "invalid_reason", "Unknown reason."), reviewer, text(body.note, "invalid_note", "Bad note."))
      : body.action === "takedown" ? await takedown(id, reviewer, text(body.note, "invalid_note", "Bad note."))
      : null;
    if (!result) throw badRequest("invalid_action", "Unknown action.");
    if (!result.ok) throw conflict("already_decided", `This playlist is ${result.current?.status ?? "gone"}.`);
    return adminView(result.current);
  }

  async function adminImage(identity, id) {
    requireAdmin(identity);
    const image = await store.getImage("submission", id);
    if (!image) throw notFound();
    return image;
  }

  /** Decrypted contact data of a submission's (or a request's) owner, for Telegram /contact. */
  async function contact(idOrUid) {
    if (!contactPrivateKey) return { unavailable: true };
    const submission = await store.getSubmission(idOrUid);
    const uid = submission?.ownerUid ?? idOrUid;
    const request = await store.getRequest(uid);
    if (!request?.contactEnc) return null;
    const info = await decryptContact(contactPrivateKey, request.contactEnc);
    return { uid, publicName: request.publicName, ...info };
  }

  async function recheck(id) {
    const submission = await store.getSubmission(id);
    if (!submission) return null;
    const { contentHash, ...summary } = await verifyPlaylist(submission.url);
    const verification = { ...summary, method: "server" };
    await store.patchSubmission(id, { verification, updatedAt: clock() });
    const updated = { ...submission, verification };
    await refresh(updated);
    return updated;
  }

  // ---- account deletion ------------------------------------------------------

  /** The request holds the (encrypted) personal data; playlists are withdrawn. */
  async function purgeUser(uid) {
    const now = clock();
    for (const submission of await store.listSubmissionsByOwner(uid)) {
      const result = await store.transitionSubmission(submission.id, [STATUS.IN_REVIEW, STATUS.APPROVED], {
        status: STATUS.WITHDRAWN, statusReason: { code: "account_deleted" }, updatedAt: now,
      });
      await unpublish(submission.id);
      await store.deleteImage("submission", submission.id);
      if (result.ok) {
        await store.releaseUrl(submission.urlHash, submission.id);
        await refresh(result.current);
      }
    }
    await store.deleteRequest(uid);
    const contributor = await store.getContributor(uid);
    // A suspension is kept as a bare marker (no personal data) for the admin's record.
    if (contributor?.status === CONTRIBUTOR_STATUS.SUSPENDED) {
      await store.putContributor(uid, { publicName: "(deleted account)", deletedAt: now, updatedAt: now });
    } else if (contributor) {
      await store.deleteContributor(uid);
    }
    log.info?.("user data deleted", { uid });
    return { deleted: true };
  }

  const deleteMe = (identity) => purgeUser(identity.uid);

  /** Purges users whose Firebase account no longer exists (see index.js). */
  async function sweepDeletedAccounts(accountExists, { maxPerRun = 20 } = {}) {
    let purged = 0;
    const uids = new Set([...(await store.listContributorUids()), ...(await store.listRequestUids())]);
    for (const uid of uids) {
      if (purged >= maxPerRun) {
        log.warn?.("account sweep stopped at its per-run cap", { maxPerRun });
        break;
      }
      const contributor = await store.getContributor(uid);
      if (contributor?.deletedAt && !(await store.getRequest(uid))) continue;
      if (await accountExists(uid)) continue;
      await purgeUser(uid);
      purged++;
    }
    if (purged) log.info?.("swept deleted accounts", { purged });
    return purged;
  }

  // ---- helpers -------------------------------------------------------------

  async function publish(submission) {
    await store.putPublicPlaylist(submission.id, publicView(submission));
    const image = await store.getImage("submission", submission.id);
    if (image) await store.putImage("public", submission.id, image);
    // A withdraw / take-down may have committed meanwhile; never outlive it.
    const current = await store.getSubmission(submission.id);
    if (current?.status !== STATUS.APPROVED) await unpublish(submission.id);
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

  async function refreshRequest(request) {
    try {
      await notifier.refreshRequest?.(request);
    } catch (error) {
      log.error?.("telegram request refresh failed", { uid: request.uid, error: error.message });
    }
  }

  async function addTelegramMessages(id, messages) {
    const submission = await store.getSubmission(id);
    if (!submission) return;
    await store.patchSubmission(id, { telegramMessages: [...(submission.telegramMessages ?? []), ...messages] });
  }

  async function addRequestMessages(uid, messages) {
    const request = await store.getRequest(uid);
    if (!request) return;
    await store.patchRequest(uid, { telegramMessages: [...(request.telegramMessages ?? []), ...messages] });
  }

  async function listPublic() {
    return store.listPublicPlaylists();
  }

  async function publicImage(id) {
    const image = await store.getImage("public", id);
    if (!image) throw notFound();
    return image;
  }

  return {
    isAdmin,
    me,
    listPublic,
    publicImage,
    createRequest,
    getMyRequest,
    cancelRequest,
    listRequests,
    decideRequest,
    adminDecideRequest,
    listContributors,
    setContributorStatus,
    adminSetContributorStatus,
    verifyLink,
    submit,
    listOwn,
    withdraw,
    approve,
    reject,
    takedown,
    listSubmissions,
    adminDecideSubmission,
    adminImage,
    contact,
    recheck,
    deleteMe,
    purgeUser,
    sweepDeletedAccounts,
    getSubmission: (id) => store.getSubmission(id),
    getRequest: (uid) => store.getRequest(uid),
    getImage: (id) => store.getImage("submission", id),
    pending: (limit = 10) => store.listSubmissionsByStatus(STATUS.IN_REVIEW, limit),
    pendingRequests: async (limit = 10) => (await store.listRequests(REQUEST_STATUS.PENDING)).slice(0, limit),
    stats: () => store.countByStatus(),
    addTelegramMessages,
    addRequestMessages,
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

/** Strings only: String() on a crafted object can throw, or turn into "[object Object]". */
function text(value, code, message) {
  if (value === undefined || value === null) return "";
  if (typeof value !== "string") throw badRequest(code, message);
  return value;
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

export function validatePublicName(raw) {
  const name = text(raw, "invalid_public_name", "Enter a public name.").trim().replace(/\s+/g, " ");
  if (name.length < 2 || name.length > 40) {
    throw badRequest("invalid_public_name", "The public name must be 2–40 characters.");
  }
  if (!/^[\p{L}\p{N} ._-]+$/u.test(name)) {
    throw badRequest("invalid_public_name", "Use letters, numbers, spaces, dots, dashes or underscores.");
  }
  if (/\d[\d .-]{6,}\d/.test(name)) {
    throw badRequest("invalid_public_name", "The public name must not contain a phone number.");
  }
  return name;
}

/** Same limits as validRequest() in firestore.rules. */
function validateRequest(body) {
  const publicName = validatePublicName(body.publicName);
  const about = text(body.about, "invalid_about", "Tell us about yourself.").trim();
  if (about.length < 20 || about.length > 1000) throw badRequest("invalid_about", "The introduction needs 20–1000 characters.");
  if (!Array.isArray(body.links ?? []) || (body.links ?? []).length > 3) throw badRequest("invalid_links", "Up to 3 sample links.");
  const links = (body.links ?? []).map((link) => text(link, "invalid_links", "Links must be text.").trim()).filter(Boolean);
  if (links.some((l) => l.length > 500 || !/^https?:\/\/.{3,}/i.test(l))) throw badRequest("invalid_links", "Sample links must start with http:// or https://.");
  const c = body.contactEnc;
  const okEnvelope =
    c && typeof c === "object" && c.v === 1 && c.alg === CONTACT_ALG &&
    ["kid", "wrappedKey", "iv", "ct"].every((k) => typeof c[k] === "string") &&
    // Same bounds as validContact() in firestore.rules.
    c.kid.length >= 8 && c.kid.length <= 64 && c.wrappedKey.length >= 100 && c.wrappedKey.length <= 2048 &&
    c.iv.length >= 12 && c.iv.length <= 32 && c.ct.length >= 20 && c.ct.length <= 4096;
  // The server never sees contact data in the clear: the browser encrypts it
  // with the admin's public key.
  if (!okEnvelope) throw badRequest("invalid_contact", "Contact details must be encrypted.");
  const consents = body.consents ?? {};
  for (const key of ["policy", "terms", "privacy"]) {
    if (typeof consents[key] !== "string" || consents[key].length < 4 || consents[key].length > 20) {
      throw badRequest("consents_required", "Accept the contributor policy, the terms of use and the privacy policy.");
    }
  }
  return {
    publicName,
    about,
    links,
    contactEnc: { v: 1, alg: c.alg, kid: c.kid, wrappedKey: c.wrappedKey, iv: c.iv, ct: c.ct },
    consents: { policy: consents.policy, terms: consents.terms, privacy: consents.privacy },
  };
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

/** A request as its owner / an admin sees it (Telegram bookkeeping stripped). */
export function requestView(request) {
  if (!request) return null;
  const { telegramMessages, ...view } = request;
  return view;
}

/** What the contributor sees of their own submission. Same field names as the Firestore backend. */
export function ownerView(submission) {
  return {
    id: submission.id,
    name: submission.name,
    url: submission.url,
    urlHash: submission.urlHash,
    description: submission.description,
    category: submission.category,
    language: submission.language,
    status: submission.status,
    statusReason: submission.statusReason,
    verification: submission.verification ?? null,
    createdAt: submission.createdAt,
    updatedAt: submission.updatedAt ?? null,
  };
}

/** Admin console: everything but the Telegram bookkeeping and the content fingerprint. */
export function adminView(submission) {
  const { telegramMessages, contentHash, ...view } = submission;
  return view;
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
    approvedAt: submission.approvedAt ?? submission.review?.at ?? null,
  };
}

export { ApiError };
