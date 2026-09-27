import { conflict } from "../errors.js";

/**
 * In-process store with the same contract as sqliteStore and firestoreStore.
 * Used by the tests and by STORE_DRIVER=memory for a local dry run; everything
 * is lost on exit. No await between a check and its write, so every method is
 * atomic on the event loop.
 */
export function createMemoryStore() {
  const requests = new Map();
  const contributors = new Map();
  const submissions = new Map();
  const urlLocks = new Map();
  const images = { submission: new Map(), public: new Map() };
  const publicPlaylists = new Map();
  const state = new Map();
  const clone = (value) => (value == null ? value ?? null : structuredClone(value));
  const newestFirst = (a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0);

  return {
    kind: "memory",

    // ---- requests ----
    async getRequest(uid) {
      return clone(requests.get(uid));
    },
    /** `build(existing)` returns the new document or throws to refuse. */
    async saveRequest(uid, build) {
      const next = build(clone(requests.get(uid)));
      requests.set(uid, clone(next));
      return clone(next);
    },
    async transitionRequest(uid, fromStatuses, patch) {
      const current = requests.get(uid);
      if (!current || !fromStatuses.includes(current.status)) return { ok: false, current: clone(current) };
      const next = { ...current, ...clone(patch) };
      requests.set(uid, next);
      return { ok: true, current: clone(next) };
    },
    async patchRequest(uid, patch) {
      const current = requests.get(uid);
      if (current) requests.set(uid, { ...current, ...clone(patch) });
    },
    async deleteRequest(uid) {
      requests.delete(uid);
    },
    async listRequests(status) {
      return [...requests.values()].filter((r) => !status || r.status === status).sort(newestFirst).map(clone);
    },
    async listRequestUids() {
      return [...requests.keys()];
    },

    // ---- contributors ----
    async getContributor(uid) {
      return clone(contributors.get(uid));
    },
    async putContributor(uid, data) {
      contributors.set(uid, clone({ ...(contributors.get(uid) ?? {}), ...data }));
    },
    async deleteContributor(uid) {
      contributors.delete(uid);
    },
    async listContributors() {
      return [...contributors.values()].sort((a, b) => (b.approvedAt ?? 0) - (a.approvedAt ?? 0)).map(clone);
    },
    async listContributorUids() {
      return [...contributors.keys()];
    },

    // ---- submissions ----
    async createSubmission(submission, checkOwner = () => {}) {
      checkOwner([...submissions.values()].filter((s) => s.ownerUid === submission.ownerUid));
      const lock = urlLocks.get(submission.urlHash);
      if (lock) {
        throw conflict("link_already_submitted", "This link has already been submitted.", {
          sameOwner: lock.ownerUid === submission.ownerUid,
        });
      }
      urlLocks.set(submission.urlHash, { submissionId: submission.id, ownerUid: submission.ownerUid });
      submissions.set(submission.id, clone(submission));
    },
    async getSubmission(id) {
      return clone(submissions.get(id));
    },
    async transitionSubmission(id, fromStatuses, patch) {
      const current = submissions.get(id);
      if (!current || !fromStatuses.includes(current.status)) return { ok: false, current: clone(current) };
      const next = { ...current, ...clone(patch) };
      submissions.set(id, next);
      return { ok: true, current: clone(next) };
    },
    async patchSubmission(id, patch) {
      const current = submissions.get(id);
      if (current) submissions.set(id, { ...current, ...clone(patch) });
    },
    async listSubmissionsByOwner(uid) {
      return [...submissions.values()].filter((s) => s.ownerUid === uid).sort(newestFirst).map(clone);
    },
    /** `status` null = every status. Oldest first (review queue order). */
    async listSubmissionsByStatus(status, limit = 50, { newestFirst = false } = {}) {
      return [...submissions.values()]
        .filter((s) => !status || s.status === status)
        .sort((a, b) => (newestFirst ? b.createdAt - a.createdAt : a.createdAt - b.createdAt))
        .slice(0, limit)
        .map(clone);
    },
    async findSubmissionsByContentHash(hash) {
      return [...submissions.values()].filter((s) => s.contentHash === hash).map(clone);
    },
    async countByStatus() {
      const counts = {};
      for (const s of submissions.values()) counts[s.status] = (counts[s.status] ?? 0) + 1;
      return counts;
    },
    async releaseUrl(urlHash, submissionId) {
      if (urlLocks.get(urlHash)?.submissionId === submissionId) urlLocks.delete(urlHash);
    },

    // ---- images, public directory, bot state ----
    async putImage(kind, id, image) {
      images[kind].set(id, clone(image));
    },
    async getImage(kind, id) {
      return clone(images[kind].get(id));
    },
    async deleteImage(kind, id) {
      images[kind].delete(id);
    },
    async putPublicPlaylist(id, doc) {
      publicPlaylists.set(id, clone(doc));
    },
    async deletePublicPlaylist(id) {
      publicPlaylists.delete(id);
    },
    async getPublicPlaylist(id) {
      return clone(publicPlaylists.get(id));
    },
    async listPublicPlaylists() {
      return [...publicPlaylists.values()].sort((a, b) => (b.approvedAt ?? 0) - (a.approvedAt ?? 0)).slice(0, 300).map(clone);
    },
    async getState(key) {
      return clone(state.get(key));
    },
    async setState(key, value) {
      state.set(key, clone(value));
    },

    /** Test hook: every document, to assert nothing holds plaintext PII. */
    dump() {
      return clone({
        requests: Object.fromEntries(requests),
        contributors: Object.fromEntries(contributors),
        submissions: Object.fromEntries(submissions),
        publicPlaylists: Object.fromEntries(publicPlaylists),
      });
    },
  };
}
