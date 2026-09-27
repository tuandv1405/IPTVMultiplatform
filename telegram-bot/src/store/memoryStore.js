import { conflict } from "../errors.js";

/**
 * In-process store with the same contract as firestoreStore. Used by the tests
 * and by `STORE_DRIVER=memory` for a local dry run; everything is lost on exit.
 */
export function createMemoryStore() {
  const contributors = new Map();
  const submissions = new Map();
  const urlLocks = new Map();
  const images = { submission: new Map(), public: new Map() };
  const publicPlaylists = new Map();
  const state = new Map();
  const clone = (value) => (value == null ? value : structuredClone(value));

  return {
    kind: "memory",

    async getContributor(uid) {
      return clone(contributors.get(uid) ?? null);
    },
    async putContributor(uid, data) {
      contributors.set(uid, clone({ ...(contributors.get(uid) ?? {}), ...data }));
    },
    async deleteContributor(uid) {
      contributors.delete(uid);
    },
    async findContributorsByHash(field, hash) {
      return [...contributors.values()].filter((c) => c[field] === hash).map(clone);
    },
    async listContributorUids() {
      return [...contributors.keys()];
    },

    // No await between the checks and the writes, so this is atomic on the event loop.
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
      return clone(submissions.get(id) ?? null);
    },
    async transitionSubmission(id, fromStatuses, patch) {
      const current = submissions.get(id);
      if (!current) return { ok: false, current: null };
      if (!fromStatuses.includes(current.status)) return { ok: false, current: clone(current) };
      const next = { ...current, ...patch };
      submissions.set(id, next);
      return { ok: true, current: clone(next) };
    },
    async patchSubmission(id, patch) {
      const current = submissions.get(id);
      if (current) submissions.set(id, { ...current, ...clone(patch) });
    },
    async listSubmissionsByOwner(uid) {
      return [...submissions.values()]
        .filter((s) => s.ownerUid === uid)
        .sort((a, b) => b.createdAt - a.createdAt)
        .map(clone);
    },
    async listSubmissionsByStatus(status, limit = 50) {
      return [...submissions.values()]
        .filter((s) => s.status === status)
        .sort((a, b) => a.createdAt - b.createdAt)
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

    async putImage(kind, id, image) {
      images[kind].set(id, clone(image));
    },
    async getImage(kind, id) {
      return clone(images[kind].get(id) ?? null);
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
      return clone(publicPlaylists.get(id) ?? null);
    },

    async getState(key) {
      return clone(state.get(key) ?? null);
    },
    async setState(key, value) {
      state.set(key, clone(value));
    },

    /** Test hook: every document, to assert nothing holds plaintext PII. */
    dump() {
      return clone({
        contributors: Object.fromEntries(contributors),
        submissions: Object.fromEntries(submissions),
        publicPlaylists: Object.fromEntries(publicPlaylists),
      });
    },
  };
}
