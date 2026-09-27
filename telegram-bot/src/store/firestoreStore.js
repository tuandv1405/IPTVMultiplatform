import { conflict } from "../errors.js";

/**
 * Firestore-backed store for running the server on Cloud Run (Admin SDK, so
 * security rules do not apply here). It uses the same collections and document
 * shapes as the browser's Firestore backend, so switching the web from
 * BACKEND="firestore" to "http" on Cloud Run needs no data migration.
 *
 * Single-field filters only: no composite index is needed.
 */
export function createFirestoreStore(db) {
  const col = {
    requests: db.collection("contributor_requests"),
    contributors: db.collection("contributors"),
    submissions: db.collection("playlist_submissions"),
    urlLocks: db.collection("playlist_urls"),
    images: { submission: db.collection("playlist_submission_images"), public: db.collection("public_playlist_images") },
    publicPlaylists: db.collection("public_playlists"),
    state: db.collection("bot_state"),
  };
  // Documents written by browsers carry Firestore Timestamps; the service works in millis.
  const plain = (value) => {
    if (value && typeof value.toMillis === "function") return value.toMillis();
    if (Array.isArray(value)) return value.map(plain);
    if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, plain(v)]));
    return value;
  };
  const data = (snap) => (snap.exists ? plain(snap.data()) : null);
  const newestFirst = (a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0);

  async function transition(ref, fromStatuses, patch) {
    return db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) return { ok: false, current: null };
      const current = plain(snap.data());
      if (!fromStatuses.includes(current.status)) return { ok: false, current };
      tx.update(ref, patch);
      return { ok: true, current: { ...current, ...patch } };
    });
  }

  return {
    kind: "firestore",

    // ---- requests ----
    async getRequest(uid) {
      return data(await col.requests.doc(uid).get());
    },
    async saveRequest(uid, build) {
      const ref = col.requests.doc(uid);
      return db.runTransaction(async (tx) => {
        const next = build(data(await tx.get(ref)));
        tx.set(ref, next);
        return next;
      });
    },
    transitionRequest: (uid, fromStatuses, patch) => transition(col.requests.doc(uid), fromStatuses, patch),
    async patchRequest(uid, patch) {
      await col.requests.doc(uid).set(patch, { merge: true });
    },
    async deleteRequest(uid) {
      await col.requests.doc(uid).delete();
    },
    async listRequests(status) {
      const snap = await (status ? col.requests.where("status", "==", status) : col.requests).get();
      return snap.docs.map((d) => plain(d.data())).sort(newestFirst);
    },
    async listRequestUids() {
      return (await col.requests.listDocuments()).map((ref) => ref.id);
    },

    // ---- contributors ----
    async getContributor(uid) {
      return data(await col.contributors.doc(uid).get());
    },
    async putContributor(uid, value) {
      await col.contributors.doc(uid).set(value, { merge: true });
    },
    async deleteContributor(uid) {
      await col.contributors.doc(uid).delete();
    },
    async listContributors() {
      return (await col.contributors.get()).docs.map((d) => plain(d.data())).sort((a, b) => (b.approvedAt ?? 0) - (a.approvedAt ?? 0));
    },
    async listContributorUids() {
      return (await col.contributors.listDocuments()).map((ref) => ref.id);
    },

    // ---- submissions ----
    async createSubmission(submission, checkOwner = () => {}) {
      const lockRef = col.urlLocks.doc(submission.urlHash);
      const subRef = col.submissions.doc(submission.id);
      const ownQuery = col.submissions.where("ownerUid", "==", submission.ownerUid);
      await db.runTransaction(async (tx) => {
        // Read inside the transaction so parallel submits conflict and retry.
        checkOwner((await tx.get(ownQuery)).docs.map((d) => plain(d.data())));
        const lock = await tx.get(lockRef);
        if (lock.exists) {
          throw conflict("link_already_submitted", "This link has already been submitted.", {
            sameOwner: lock.data().ownerUid === submission.ownerUid,
          });
        }
        tx.create(lockRef, { submissionId: submission.id, ownerUid: submission.ownerUid });
        tx.create(subRef, submission);
      });
    },
    async getSubmission(id) {
      return data(await col.submissions.doc(id).get());
    },
    transitionSubmission: (id, fromStatuses, patch) => transition(col.submissions.doc(id), fromStatuses, patch),
    async patchSubmission(id, patch) {
      await col.submissions.doc(id).set(patch, { merge: true });
    },
    async listSubmissionsByOwner(uid) {
      return (await col.submissions.where("ownerUid", "==", uid).get()).docs.map((d) => plain(d.data())).sort(newestFirst);
    },
    async listSubmissionsByStatus(status, limit = 50, { newestFirst = false } = {}) {
      // No composite index: filter server-side, order here.
      const snap = await (status ? col.submissions.where("status", "==", status) : col.submissions).get();
      return snap.docs.map((d) => plain(d.data()))
        .sort((a, b) => (newestFirst ? b.createdAt - a.createdAt : a.createdAt - b.createdAt)).slice(0, limit);
    },
    async findSubmissionsByContentHash(hash) {
      return (await col.submissions.where("contentHash", "==", hash).limit(50).get()).docs.map((d) => plain(d.data()));
    },
    async countByStatus() {
      const counts = {};
      for (const status of ["IN_REVIEW", "APPROVED", "REJECTED", "WITHDRAWN", "REMOVED"]) {
        counts[status] = (await col.submissions.where("status", "==", status).count().get()).data().count;
      }
      return counts;
    },
    async releaseUrl(urlHash, submissionId) {
      const ref = col.urlLocks.doc(urlHash);
      await db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        if (snap.exists && snap.data().submissionId === submissionId) tx.delete(ref);
      });
    },

    // ---- images, public directory, bot state ----
    async putImage(kind, id, image) {
      await col.images[kind].doc(id).set(image);
    },
    async getImage(kind, id) {
      return data(await col.images[kind].doc(id).get());
    },
    async deleteImage(kind, id) {
      await col.images[kind].doc(id).delete();
    },
    async putPublicPlaylist(id, doc) {
      await col.publicPlaylists.doc(id).set(doc);
    },
    async deletePublicPlaylist(id) {
      await col.publicPlaylists.doc(id).delete();
    },
    async getPublicPlaylist(id) {
      return data(await col.publicPlaylists.doc(id).get());
    },
    async listPublicPlaylists() {
      // Sorted here: browser-written docs carry Timestamps, server-written ones millis.
      return (await col.publicPlaylists.get()).docs.map((d) => plain(d.data()))
        .sort((a, b) => (b.approvedAt ?? 0) - (a.approvedAt ?? 0)).slice(0, 300);
    },
    async getState(key) {
      const value = data(await col.state.doc(key).get());
      return value ? value.value : null;
    },
    async setState(key, value) {
      await col.state.doc(key).set({ value });
    },
  };
}
